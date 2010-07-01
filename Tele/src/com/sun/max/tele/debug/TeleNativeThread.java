/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.max.tele.debug;

import static com.sun.max.tele.MaxThreadState.*;

import java.util.*;
import java.util.logging.*;

import com.sun.max.asm.*;
import com.sun.max.jdwp.vm.data.*;
import com.sun.max.jdwp.vm.proxy.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;
import com.sun.max.tele.*;
import com.sun.max.tele.memory.*;
import com.sun.max.tele.method.*;
import com.sun.max.tele.method.CodeLocation.*;
import com.sun.max.tele.object.*;
import com.sun.max.tele.util.*;
import com.sun.max.tele.value.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.bytecode.BytecodeLocation;
import com.sun.max.vm.classfile.*;
import com.sun.max.vm.classfile.LocalVariableTable.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.compiler.target.TargetLocation.*;
import com.sun.max.vm.cps.target.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.stack.*;
import com.sun.max.vm.thread.*;
import com.sun.max.vm.type.*;
import com.sun.max.vm.value.*;

/**
 * Represents a thread executing in a {@linkplain TeleProcess tele process}.
 *
 * @author Bernd Mathiske
 * @author Aritra Bandyopadhyay
 * @author Doug Simon
 * @author Michael Van De Vanter
 */
public abstract class TeleNativeThread extends AbstractTeleVMHolder implements TeleVMCache, Comparable<TeleNativeThread>, MaxThread, ThreadProvider {

    @Override
    protected String  tracePrefix() {
        return "[TeleNativeThread: " + Thread.currentThread().getName() + "] ";
    }

    private static final int TRACE_VALUE = 1;

    private final TimedTrace updateTracer;

    private static final Logger LOGGER = Logger.getLogger(TeleNativeThread.class.getName());

    private final TeleProcess teleProcess;
    private int suspendCount;

    private final TeleRegisterSet teleRegisterSet;

    private final TeleStack teleStack;

    /**
     * A cached stack trace for this thread, never null.
     */
    private List<StackFrame> frames = Collections.emptyList();

    /**
     * Only if this value is less than the {@linkplain TeleProcess#epoch() epoch} of this thread's tele process, does
     * the {@link #refreshFrames(boolean)} method do anything.
     */
    private long framesRefreshedEpoch;

    /**
     * The last epoch at which the structure of the stack changed, even if the contents of the top frame may have.
     */
    private long framesLastChangedEpoch;

    /**
     * Access to information about and contained in this thread's local storage area.
     * Holds a dummy if no thread local information is available:  i.e. either this is a non-Java thread, or
     * the thread is very early in its creation sequence.
     */
    private final TeleThreadLocalsBlock threadLocalsBlock;

    private MaxThreadState state = SUSPENDED;
    private TeleTargetBreakpoint breakpoint;
    private FrameProvider[] frameCache;

    /**
     * This thread's {@linkplain VmThread#id() identifier}.
     */
    private final int id;

    private final long localHandle;
    private final long handle;
    private final String entityName;
    private final String entityDescription;

    /**
     * The parameters accepted by {@link TeleNativeThread#TeleNativeThread(TeleProcess, Params)}.
     */
    public static class Params {
        public int id;
        public long localHandle;
        public long handle;
        public TeleFixedMemoryRegion stackRegion;
        public TeleFixedMemoryRegion threadLocalsRegion;

        @Override
        public String toString() {
            return String.format("id=%d, localHandle=0x%08x, handle=%d, stackRegion=%s, threadLocalsAreasRegion=%s", id, localHandle, handle, MaxMemoryRegion.Util.asString(stackRegion), MaxMemoryRegion.Util.asString(threadLocalsRegion));
        }
    }

    protected TeleNativeThread(TeleProcess teleProcess, Params params) {
        super(teleProcess.vm());
        final TimedTrace tracer = new TimedTrace(TRACE_VALUE, tracePrefix() + " creating");
        tracer.begin();

        this.teleProcess = teleProcess;
        this.id = params.id;
        this.localHandle = params.localHandle;
        this.handle = params.handle;
        this.entityName = "Thread-" + this.localHandle;
        this.entityDescription = "The thread named " + this.entityName + " in the " + teleProcess.vm().entityName();
        final VMConfiguration vmConfiguration = teleProcess.vm().vmConfiguration();
        this.teleRegisterSet = new TeleRegisterSet(teleProcess.vm(), this);
        if (params.threadLocalsRegion == null) {
            final String name = this.entityName + " Locals (NULL, not allocated)";
            this.threadLocalsBlock = new TeleThreadLocalsBlock(this, name);
        } else {
            final String name = this.entityName + " Locals";
            this.threadLocalsBlock = new TeleThreadLocalsBlock(this, name, params.threadLocalsRegion.start(), params.threadLocalsRegion.size());
        }
        this.breakpointIsAtInstructionPointer = vmConfiguration.platform().processorKind.instructionSet == InstructionSet.SPARC;
        final String stackName = this.entityName + " Stack";
        this.teleStack = new TeleStack(teleProcess.vm(), this, stackName, params.stackRegion.start(), params.stackRegion.size());
        this.updateTracer = new TimedTrace(TRACE_VALUE, tracePrefix() + " updating");

        tracer.end(null);
    }

    public void updateCache() {
        Trace.line(TRACE_VALUE + 1, tracePrefix() + "refresh thread=" + this);
        if (state.allowsDataAccess()) {
            refreshBreakpoint();
            threadLocalsBlock.updateCache();
        }
    }

    public final String entityName() {
        return entityName;
    }

    public String entityDescription() {
        return entityDescription;
    }

    public final MaxEntityMemoryRegion<MaxThread> memoryRegion() {
        // The thread has no VM memory allocated for itself; it allocates stack and locals spaces from the OS.
        return null;
    }

    public boolean contains(Address address) {
        return teleStack.contains(address) || (threadLocalsBlock != null && threadLocalsBlock.contains(address));

    }

    public final int id() {
        return id;
    }

    public final long handle() {
        return handle;
    }

    public final String handleString() {
        return "0x" + Long.toHexString(handle);
    }

    public final long localHandle() {
        return localHandle;
    }

    public final boolean isPrimordial() {
        return id() == 0;
    }

    /**
     * Determines if this thread is associated with a {@link VmThread} instance. Note that even if this method returns
     * {@code true}, the {@link #maxVMThread()} method will return {@code null} if the thread has not reached the
     * execution point in {@link VmThread#run} where the {@linkplain VmThreadLocal#VM_THREAD reference} to the
     * {@link VmThread} object has been initialized.
     */
    public final boolean isJava() {
        return id > 0;
    }

    public final boolean isLive() {
        return state != DEAD;
    }

    public final MaxThreadState state() {
        return state;
    }

    public final TeleTargetBreakpoint breakpoint() {
        return breakpoint;
    }

    public final TeleThreadLocalsBlock localsBlock() {
        return threadLocalsBlock;
    }

    public final TeleRegisterSet registers() {
        return teleRegisterSet;
    }

    public final TeleStack stack() {
        return teleStack;
    }

    public final MachineCodeLocation ipLocation() {
        if (!isLive()) {
            return null;
        }
        // No need to refresh registers: the instruction pointer is updated by updateAfterGather() which
        // ensures that it is always in sync.
        return codeManager().createMachineCodeLocation(teleRegisterSet.instructionPointer(), "Instruction pointer");
    }

    public final TeleVmThread teleVmThread() {
        return threadLocalsBlock.teleVmThread();
    }

    public final String vmThreadName() {
        if (teleVmThread() != null) {
            return teleVmThread().name();
        }
        return null;
    }

    /**
     * @return a printable version of the thread's internal state that only shows key aspects
     */
    public final String toShortString() {
        final StringBuilder sb = new StringBuilder(100);
        sb.append(isPrimordial() ? "primordial" : (teleVmThread() == null ? "native" : teleVmThread().name()));
        sb.append("[id=").append(id());
        sb.append(",handle=").append(handleString());
        sb.append(",local handle=").append(localHandle());
        sb.append(",type=").append(isPrimordial() ? "primordial" : (isJava() ? "Java" : "native"));
        sb.append(",stat=").append(state.toString());
        sb.append("]");
        return sb.toString();
    }

    /**
     * Imposes a total ordering between threads based on their {@linkplain #id() identifiers}.
     */
    public final int compareTo(TeleNativeThread other) {
        return Longs.compare(handle(), other.handle());
    }

    /**
     * Immutable; thread-safe.
     *
     * @return the process in which this thread is running.
     */
    public TeleProcess teleProcess() {
        return teleProcess;
    }

    /**
     * @return the most currently refreshed frames on the thread's stack, never null.
     */
    final List<StackFrame> frames() {
        final long currentProcessEpoch = teleProcess().epoch();
        if (framesRefreshedEpoch < currentProcessEpoch) {
            Trace.line(TRACE_VALUE + 1, tracePrefix() + "refreshFrames (epoch=" + currentProcessEpoch + ") for " + this);
            threadLocalsBlock.updateCache();
            final List<StackFrame> newFrames = new TeleStackFrameWalker(teleProcess.vm(), this).frames();
            assert !newFrames.isEmpty();
            // See if the new stack is structurally equivalent to its predecessor, even if the contents of the top
            // frame may have changed.
            if (newFrames.size() != this.frames.size()) {
                // Clear structural change; lengths are different
                framesLastChangedEpoch = currentProcessEpoch;
            } else {
                // Lengths are the same; see if any frames differ.
                final Iterator<StackFrame> oldFramesIterator = this.frames.iterator();
                final Iterator<StackFrame> newFramesIterator = newFrames.iterator();
                while (oldFramesIterator.hasNext()) {
                    final StackFrame oldFrame = oldFramesIterator.next();
                    final StackFrame newFrame = newFramesIterator.next();
                    if (!oldFrame.isSameFrame(newFrame)) {
                        framesLastChangedEpoch = currentProcessEpoch;
                        break;
                    }
                }
            }
            this.frames = newFrames;
            framesRefreshedEpoch = currentProcessEpoch;
        }
        return frames;
    }

    /**
     * Track when the structure of the stack changes, in any respect other than
     * the contents of the top frame.
     *
     * @return the last process epoch at which the structure of the stack changed.
     */
    final Long framesLastChangedEpoch() {
        return framesLastChangedEpoch;
    }

    /**
     * Updates this thread with the information information made available while
     * {@linkplain TeleProcess#gatherThreads(List) gathering} threads. This information is made available
     * by the native tele layer as threads are discovered. Subsequent refreshing of cached thread state (such a
     * {@linkplain TeleRegisterSet#updateCache() registers}, {@linkplain #refreshFrames(boolean) stack frames} and
     * {@linkplain #refreshThreadLocals() VM thread locals}) depends on this information being available and up to date.
     *
     * @param state the state of the thread
     * @param instructionPointer the current value of the instruction pointer for the thread
     * @param threadLocalsRegion the memory region reported to be holding the thread local block; null if not available
     * @param tlaSize the size of each Thread Locals Area in the thread local block.
     */
    final void updateAfterGather(MaxThreadState state, Pointer instructionPointer, TeleFixedMemoryRegion threadLocalsRegion, int tlaSize) {
        this.state = state;
        teleRegisterSet.setInstructionPointer(instructionPointer);
        threadLocalsBlock.updateAfterGather(threadLocalsRegion, tlaSize);
    }

    /**
     * Marks the thread as having died in the process; flushes all state accordingly.
     */
    final void setDead() {
        state = DEAD;
        clearFrames();
        breakpoint = null;
        frameCache = null;
        threadLocalsBlock.clear();
    }

    /**
     * If this thread is currently at a {@linkplain #breakpoint() breakpoint} it is single stepped to the next
     * instruction.
     */
    void evadeBreakpoint() throws OSExecutionRequestException {
        if (breakpoint != null && !breakpoint.isTransient()) {
            assert !breakpoint.isActive() : "Cannot single step at an activated breakpoint";
            Trace.line(TRACE_VALUE + 1, tracePrefix() + "single step to evade breakpoint=" + breakpoint);
            teleProcess().singleStep(this, true);
        }
    }

    /**
     * Refreshes the information about the {@linkplain #breakpoint() breakpoint} this thread is currently stopped at (if
     * any). If this thread is stopped at a breakpoint, its instruction pointer is adjusted so that it is at the
     * instruction on which the breakpoint was set.
     */
    private void refreshBreakpoint() {
        final TeleTargetBreakpoint.TargetBreakpointManager breakpointManager = teleProcess().targetBreakpointManager();
        TeleTargetBreakpoint breakpoint = null;

        try {
            final Pointer breakpointAddress = breakpointAddressFromInstructionPointer();
            breakpoint = breakpointManager.getTargetBreakpointAt(breakpointAddress);
        } catch (DataIOError dataIOError) {
            // This is a catch for problems getting accurate state for threads that are not at breakpoints
        }

        if (breakpoint != null) {

            Trace.line(TRACE_VALUE + 1, tracePrefix() + "refreshingBreakpoint (epoch=" + teleProcess().epoch() + ") for " + this);

            state = BREAKPOINT;
            this.breakpoint = breakpoint;
            final Address address = this.breakpoint.codeLocation().address();
            if (updateInstructionPointer(address)) {
                teleRegisterSet.setInstructionPointer(address);
                Trace.line(TRACE_VALUE + 1, tracePrefix() + "refreshingBreakpoint (epoc)h=" + teleProcess().epoch() + ") IP updated for " + this);
            } else {
                ProgramError.unexpected("Error updating instruction pointer to adjust thread after breakpoint at " + address + " was hit: " + this);
            }
        } else {
            this.breakpoint = null;
            assert state != BREAKPOINT;
        }
    }

    /**
     * Clears the current list of frames.
     */
    private synchronized void clearFrames() {
        frames = Collections.emptyList();
        framesLastChangedEpoch = teleProcess().epoch();
    }

    /**
     * Update the current list of frames, and notice if the structure of the stack has changed.
     */
    private synchronized void refreshFrames() {
        final long processEpoch = teleProcess().epoch();
        if (framesRefreshedEpoch < processEpoch) {
            Trace.line(TRACE_VALUE + 1, tracePrefix() + "refreshFrames (epoch=" + processEpoch + ") for " + this);
            threadLocalsBlock.updateCache();
            final TeleVM teleVM = teleProcess.vm();
            final List<StackFrame> newFrames = new TeleStackFrameWalker(teleVM, this).frames();
            assert !newFrames.isEmpty();
            // See if the new stack is structurally equivalent to its predecessor, even if the contents of the top
            // frame may have changed.
            if (newFrames.size() != this.frames.size()) {
                // Clear structural change; lengths are different
                framesLastChangedEpoch = processEpoch;
            } else {
                final Iterator<StackFrame> oldFramesIterator = this.frames.iterator();
                final Iterator<StackFrame> newFramesIterator = newFrames.iterator();
                while (oldFramesIterator.hasNext()) {
                    final StackFrame oldFrame = oldFramesIterator.next();
                    final StackFrame newFrame = newFramesIterator.next();
                    if (!oldFrame.isSameFrame(newFrame)) {
                        framesLastChangedEpoch = processEpoch;
                        break;
                    }
                }
            }
            this.frames = newFrames;
            framesRefreshedEpoch = processEpoch;
        }
    }

    /**
     * Specifies whether or not the instruction pointer needs to be adjusted when this thread hits a breakpoint to
     * denote the instruction pointer for which the breakpoint was set. For example, on x86 architectures, the
     * instruction pointer is at the instruction following the breakpoint instruction whereas on SPARC, it's
     * at the instruction pointer for which the breakpoint was set.
     */
    private boolean breakpointIsAtInstructionPointer;

    /**
     * Updates the current value of the instruction pointer for this thread.
     *
     * @param address the address to which the instruction should be set
     * @return true if the instruction pointer was successfully updated, false otherwise
     */
    protected abstract boolean updateInstructionPointer(Address address);

    protected abstract boolean readRegisters(byte[] integerRegisters, byte[] floatingPointRegisters, byte[] stateRegisters);

    /**
     * Advances this thread to the next instruction. That is, makes this thread execute a single machine instruction.
     * Note that this method does not block waiting for the tele process to complete the step.
     *
     * @return true if the single step was issued successfully, false otherwise
     */
    protected abstract boolean singleStep();

    protected abstract boolean threadResume();

    protected abstract boolean threadSuspend();

    /**
     * Gets the address of the breakpoint instruction derived from the current instruction pointer. The current
     * instruction pointer is assumed to be at the architecture dependent location immediately after a breakpoint
     * instruction was executed.
     *
     * The implementation of this method in {@link TeleNativeThread} uses the convention for x86 architectures where the
     * the instruction pointer is at the instruction following the breakpoint instruction.
     */
    private Pointer breakpointAddressFromInstructionPointer() {
        final Pointer instructionPointer = teleRegisterSet.instructionPointer();
        if (breakpointIsAtInstructionPointer) {
            return instructionPointer;
        }
        return instructionPointer.minus(teleProcess().targetBreakpointManager().codeSize());
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof TeleNativeThread) {
            final TeleNativeThread teleNativeThread = (TeleNativeThread) other;
            return localHandle() == teleNativeThread.localHandle() && id() == teleNativeThread.id();
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (int) localHandle();
    }

    @Override
    public final String toString() {
        final StringBuilder sb = new StringBuilder(100);
        sb.append(isPrimordial() ? "primordial" : (teleVmThread() == null ? "native" : teleVmThread().name()));
        sb.append("[id=").append(id());
        sb.append(",handle=").append(handleString());
        sb.append(",local handle=").append(localHandle());
        sb.append(",state=").append(state);
        sb.append(",type=").append(isPrimordial() ? "primordial" : (isJava() ? "Java" : "native"));
        if (isLive()) {
            sb.append(",ip=0x").append(teleRegisterSet.instructionPointer().toHexString());
            if (isJava()) {
                sb.append(",stack_start=0x").append(stack().memoryRegion().start().toHexString());
                sb.append(",stack_size=").append(stack().memoryRegion().size().toLong());
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Class representing a Java-level frame. It implements the interface the JDWP protocol is programmed against.
     */
    private class FrameProviderImpl implements FrameProvider {

        private BytecodeLocation bytecodeLocation;
        private ClassMethodActor classMethodActor;
        private int position;
        private StackFrame stackFrame;
        private TeleTargetMethod targetMethod;
        private long[] rawValues;
        private VMValue[] vmValues;
        private boolean isTopFrame;

        public FrameProviderImpl(boolean isTopFrame, TeleTargetMethod targetMethod, StackFrame stackFrame, BytecodeLocation bytecodeLocation) {
            this(isTopFrame, targetMethod, stackFrame, bytecodeLocation, bytecodeLocation.classMethodActor, 0); //descriptor.bytecodeLocation().());
        }

        public FrameProviderImpl(boolean isTopFrame, TeleTargetMethod targetMethod, StackFrame stackFrame, BytecodeLocation bytecodeLocation, ClassMethodActor classMethodActor, int position) {
            this.stackFrame = stackFrame;
            this.bytecodeLocation = bytecodeLocation;
            this.classMethodActor = classMethodActor;
            this.position = position;
            this.targetMethod = targetMethod;
            this.isTopFrame = isTopFrame;

            if (classMethodActor.codeAttribute().lineNumberTable().entries().length > 0) {
                this.position = classMethodActor.codeAttribute().lineNumberTable().entries()[0].position();
            } else {
                LOGGER.warning("No line number table information for method " + classMethodActor.name.toString());
                this.position = -1;
            }
        }

        private void initValues() {
            final int length = classMethodActor.codeAttribute().localVariableTable().entries().length;
            final long[] values = new long[length];
            final VMValue[] vmValues = new VMValue[length];

            for (LocalVariableTable.Entry entry : classMethodActor.codeAttribute().localVariableTable().entries()) {
                final Value curValue = getValueImpl(entry.slot());
                vmValues[entry.slot()] = vm().maxineValueToJDWPValue(curValue);

                if (curValue.kind().isReference) {
                    values[entry.slot()] = curValue.asReference().toOrigin().toLong();
                } else if (curValue.kind().isWord) {
                    values[entry.slot()] = curValue.asWord().asPointer().toLong();
                }
            }

            this.vmValues = vmValues;
            rawValues = values;
        }

        private Value getValueImpl(int slot) {
            TargetLocation l = null;

            if (!(bytecodeLocation instanceof TargetJavaFrameDescriptor)) {
                final TargetLocation[] targetLocations = stackFrame.targetMethod().abi().getParameterTargetLocations(stackFrame.targetMethod().classMethodActor().getParameterKinds());
                if (slot >= targetLocations.length) {
                    return IntValue.from(0xbadbabe);
                }
                l = targetLocations[slot];
            } else {
                TargetJavaFrameDescriptor descriptor = (TargetJavaFrameDescriptor) bytecodeLocation;
                if (slot >= descriptor.locals.length) {
                    return IntValue.from(0xbadbabe);
                }
                l = descriptor.locals[slot];
            }

            // System.out.println("STACKFRAME ACCESS at " + slot + ", target=" + l);

            final Entry entry = classMethodActor.codeAttribute().localVariableTable().findLocalVariable(slot, position);
            if (entry == null) {
                return LongValue.from(0xbadbabe);
            }
            final TypeDescriptor descriptor = entry.descriptor(classMethodActor.codeAttribute().constantPool);
            final Kind kind = descriptor.toKind();

            if (l instanceof LocalStackSlot) {
                final LocalStackSlot localStackSlot = (LocalStackSlot) l;
                final int index = localStackSlot.index();
                final Pointer slotBase = stackFrame.slotBase();
                final int offset = index * Word.size();

                return vm().readValue(kind, slotBase, offset);

            } else if (l instanceof ParameterStackSlot) {

                final ParameterStackSlot parameterStackSlot = (ParameterStackSlot) l;
                final int index = parameterStackSlot.index();
                final Pointer slotBase = stackFrame.slotBase();

                // TODO: Resolve this hack that uses a special function in the Java stack frame layout.

                final CompiledStackFrame javaStackFrame = (CompiledStackFrame) stackFrame;
                int offset = index * Word.size() + javaStackFrame.layout.frameSize();
                offset += javaStackFrame.layout.isReturnAddressPushedByCall() ? Word.size() : 0;

                return vm().readValue(kind, slotBase, offset);

            } else if (l instanceof IntegerRegister) {
                final IntegerRegister integerRegister = (IntegerRegister) l;
                final int integerRegisterIndex = integerRegister.index();
                final Address address = teleRegisterSet.teleIntegerRegisters().getValueAt(integerRegisterIndex);

                if (kind.isReference) {
                    return TeleReferenceValue.from(vm(), Reference.fromOrigin(address.asPointer()));
                }
                return LongValue.from(address.toLong());
            }

            return IntValue.from(5);
        }

        public TargetMethodAccess getTargetMethodProvider() {
            return targetMethod;
        }

        public JdwpCodeLocation getLocation() {
            return vm().vmAccess().createCodeLocation(vm().findTeleMethodActor(TeleClassMethodActor.class, classMethodActor), position, false);
        }

        public long getInstructionPointer() {
            // On top frame, the instruction pointer is incorrect, so take it from the thread!
            if (isTopFrame) {
                return teleRegisterSet.instructionPointer().asAddress().toLong();
            }
            return stackFrame.ip.asAddress().toLong();
        }

        public long getFramePointer() {
            return stackFrame.fp.asAddress().toLong();
        }

        public long getStackPointer() {
            return stackFrame.sp.asAddress().toLong();
        }

        public ThreadProvider getThread() {
            return TeleNativeThread.this;
        }

        public VMValue getValue(int slot) {

            if (vmValues == null) {
                initValues();
            }

            return vmValues[slot];
        }

        public void setValue(int slot, VMValue value) {
            final TargetLocation targetLocation = bytecodeLocation instanceof TargetJavaFrameDescriptor ? ((TargetJavaFrameDescriptor) bytecodeLocation).locals[slot] : null;

            // TODO: Implement writing to stack frames.
            LOGGER.warning("Stackframe write at " + slot + ", targetLocation=" + targetLocation + ", doing nothing");
        }

        public ObjectProvider thisObject() {
            // TODO: Add a way to access the "this" object.
            LOGGER.warning("Trying to access THIS object, returning null");
            return null;
        }

        public long[] getRawValues() {
            if (rawValues == null) {
                initValues();
            }
            return rawValues;
        }
    }

    public FrameProvider getFrame(int depth) {
        return getFrames()[depth];
    }

    private List<StackFrame> oldFrames;

    public synchronized FrameProvider[] getFrames() {

        synchronized (teleProcess()) {

            if (oldFrames != frames) {
                oldFrames = frames;
            } else {
                return frameCache;
            }

            final List<FrameProvider> result = new LinkedList<FrameProvider>();
            int z = 0;
            for (final StackFrame stackFrame : frames()) {
                z++;

                final Address address = stackFrame.ip;
                TeleCompiledCode compiledCode = vm().codeCache().findCompiledCode(address);
                if (compiledCode == null) {
                    if (stackFrame.targetMethod() == null) {
                        LOGGER.warning("Target method of stack frame (" + stackFrame + ") was null!");
                        continue;
                    }
                    final TargetMethod targetMethod = stackFrame.targetMethod();
                    final ClassMethodActor classMethodActor = targetMethod.classMethodActor();
                    final TeleClassMethodActor teleClassMethodActor = vm().findTeleMethodActor(TeleClassMethodActor.class, classMethodActor);
                    if (teleClassMethodActor == null) {
                        ProgramWarning.message("Could not find tele class method actor for " + classMethodActor);
                        continue;
                    }
                    compiledCode = vm().codeCache().findCompiledCode(targetMethod.codeStart().asAddress());
                    if (compiledCode == null) {
                        ProgramWarning.message("Could not find tele target method actor for " + classMethodActor);
                        continue;
                    }
                }

                LOGGER.info("Processing stackframe " + stackFrame);

                int index = -1;
                if (stackFrame.targetMethod() != null && stackFrame.targetMethod() instanceof CPSTargetMethod) {
                    index = ((CPSTargetMethod) stackFrame.targetMethod()).findClosestStopIndex(stackFrame.ip);
                }
                if (index != -1) {
                    final int stopIndex = index;
                    BytecodeLocation descriptor = compiledCode.teleTargetMethod().getBytecodeLocation(stopIndex);

                    if (descriptor == null) {
                        LOGGER.info("WARNING: No Java frame descriptor found for Java stop " + stopIndex);

                        if (vm().findTeleMethodActor(TeleClassMethodActor.class, compiledCode.classMethodActor()) == null) {
                            LOGGER.warning("Could not find tele method!");
                        } else {
                            result.add(new FrameProviderImpl(z == 1, compiledCode.teleTargetMethod(), stackFrame, null, compiledCode.classMethodActor(), 0));
                        }
                    } else {

                        while (descriptor != null) {
                            final TeleClassMethodActor curTma = vm().findTeleMethodActor(TeleClassMethodActor.class, descriptor.classMethodActor);

                            LOGGER.info("Found part frame " + descriptor + " tele method actor: " + curTma);
                            result.add(new FrameProviderImpl(z == 1, compiledCode.teleTargetMethod(), stackFrame, descriptor));
                            descriptor = descriptor.parent();
                        }
                    }
                } else {
                    LOGGER.info("Not at Java stop!");
                    if (vm().findTeleMethodActor(TeleClassMethodActor.class, compiledCode.classMethodActor()) == null) {
                        LOGGER.warning("Could not find tele method!");
                    } else {
                        result.add(new FrameProviderImpl(z == 1, compiledCode.teleTargetMethod(), stackFrame, null, compiledCode.classMethodActor(), 0));
                    }
                }
            }

            frameCache = result.toArray(new FrameProvider[result.size()]);
            return frameCache;
        }
    }

    public String getName() {
        return toString();
    }

    public void interrupt() {
        // TODO: Implement the possibility to interrupt threads.
        LOGGER.warning("Thread " + this + " was asked to interrupt, doing nothing");
        assert false : "Not implemented.";
    }

    public final void resume() {
        if (suspendCount > 0) {
            suspendCount--;
        }
        if (suspendCount == 0) {
            LOGGER.info("Asked to RESUME THREAD " + this + " we are resuming silently the whole VM for now");
            vm().vmAccess().resume();
        }
    }

    public void stop(ObjectProvider exception) {
        // TODO: Consider implementing stopping a thread by throwing an exception.
        LOGGER.warning("A thread was asked to stop over JDWP with the exception " + exception + ", doing nothing.");
    }

    public final void suspend() {
        suspendCount++;
    }

    public int suspendCount() {
        // TODO: Implement the suspend count according to the JDWP rules. The current very simple implementation seems to work however fine with NetBeans.
        if (teleProcess().processState() == ProcessState.STOPPED) {
            return 1;
        }
        return suspendCount;
    }

    public ReferenceTypeProvider getReferenceType() {
        return vm().vmAccess().getReferenceType(getClass());
    }

    public ThreadGroupProvider getThreadGroup() {
        return isJava() ? vm().javaThreadGroupProvider() : vm().nativeThreadGroupProvider();
    }

    public void doSingleStep() {
        LOGGER.info("Asked to do a single step!");
        vm().registerSingleStepThread(this);
    }

    public void doStepOut() {
        LOGGER.info("Asked to do a step out!");
        vm().registerStepOutThread(this);
    }

    public VMAccess getVM() {
        return vm().vmAccess();
    }

    public RegistersGroup getRegistersGroup() {
        final Registers[] registers = new Registers[]{teleRegisterSet.teleIntegerRegisters().getRegisters("Integer Registers"),
                        teleRegisterSet.teleStateRegisters().getRegisters("State Registers"),
                        teleRegisterSet.teleFloatingPointRegisters().getRegisters("Floating Point Registers")};
        return new RegistersGroup(registers);
    }
}
