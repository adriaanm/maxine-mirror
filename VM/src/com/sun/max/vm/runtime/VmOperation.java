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
package com.sun.max.vm.runtime;

import static com.sun.max.vm.runtime.VmOperationThread.*;
import static com.sun.max.vm.thread.VmThreadLocal.*;

import com.sun.cri.bytecode.Bytecodes.MemoryBarriers;
import com.sun.max.unsafe.*;
import com.sun.max.unsafe.Pointer.Predicate;
import com.sun.max.vm.*;
import com.sun.max.vm.compiler.builtin.*;
import com.sun.max.vm.compiler.snippet.*;
import com.sun.max.vm.compiler.snippet.NativeStubSnippet.NativeCallEpilogue;
import com.sun.max.vm.compiler.snippet.NativeStubSnippet.NativeCallPrologue;
import com.sun.max.vm.object.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.stack.*;
import com.sun.max.vm.thread.*;

/**
 * A VM operation that can be {@linkplain VmOperationThread#submit(VmOperation) executed}
 * on the {@linkplain VmOperationThread VM operation thread}. VM operations typically operate
 * one or more mutator threads frozen at a safepoint.
 * A thread is frozen at a safepoint when it is blocked in native
 * code (typically on an OS-level lock) and cannot (re)enter compiled/interpreted
 * Java code without being {@linkplain ThawThread thawed} by the VM operation thread.
 * Every frame of a compiled/interpreted method on a frozen thread's stack is guaranteed to be
 * at an execution point where the complete frame state of the method is available.
 * <p>
 * Freezing a thread is a co-operative action between the VM operation thread and the thread(s) being frozen.
 * There are two alternative implementations of this mechanism provided. The first uses atomic instructions
 * and the second uses memory fences. They are named "CAS" and "FENCE" and are described further below.
 * <dl>
 * <dt>CAS</dt>
 * <dd>Atomic compare-and-swap (CAS) instructions are used to enforce transitions through the following state
 * machine:
 *
 * <pre>
 *     +------+                            +--------+                               +---------+
 *     |      |--- M:JNI-Prolog{STORE} --->|        |--- VM:WaitUntilFrozen{CAS} --->|         |
 *     | JAVA |                            | NATIVE |                               | FROZEN  |
 *     |      |<--- M:JNI-Epilog{CAS} -----|        |<----- VM:ThawThread{STORE} ----|         |
 *     +------+                            +--------+                               +---------+
 * </pre>
 * The syntax for each transition operation is:
 * <pre>
 *       thread ':' code '{' update-instruction '}'
 * </pre>
 *
 * The state pertains to the mutator thread and is recorded in the
 * {@link VmThreadLocal#MUTATOR_STATE} thread local variable of the mutator thread.
 * Each transition describes which thread makes the transition ({@code M} == mutator thread, {@code VM} == VM operation
 * thread), the VM code implementing the transition ({@linkplain NativeCallPrologue#nativeCallPrologue() JNI-Prolog},
 * {@linkplain NativeCallEpilogue#nativeCallEpilogue() JNI-Epilog}, {@linkplain WaitUntilFrozen
 * WaitUntilFrozen} and {@linkplain ThawThread ThawThread}) and the instruction used to update the state
 * variable ({@code CAS} == atomic compare-and-swap, {@code STORE} == normal memory store).</dd>
 *
 * <dt>FENCE</dt>
 * <dd>Memory fences are used to implement Dekkers algorithm to ensure that a thread is never
 * mutating during a GC. This mechanism uses both the {@link VmThreadLocal#MUTATOR_STATE} and
 * {@link VmThreadLocal#FROZEN} thread local variables of the mutator thread. The operations
 * that access these variables are in {@link NativeCallPrologue#nativeCallPrologue()},
 * {@link NativeCallEpilogue#nativeCallEpilogue()}, {@link WaitUntilFrozen} and
 * {@link ThawThread}.
 * </dd>
 * </dl>
 * The choice of which synchronization mechanism to use is specified by the {@link #UseCASBasedThreadFreezing} variable.
 * TODO: Make the choice for this value based on the mechanism proven to runs best on each platform.
 * <p>
 * Freezing a thread requires making it enter native code. For threads already in native code, this is trivial (i.e.
 * there's nothing to do except to transition them to the frozen state). For threads executing in Java code,
 * {@linkplain Safepoint safepoints} are employed.
 * Safepoints are small polling code sequences injected by the compiler at prudently chosen execution points.
 * The effect of executing a triggered safepoint is for the thread to trap. The trap handler will then call
 * a specified {@linkplain AtSafepoint} procedure. This procedure synchronizes
 * on the global GC and thread lock. Since the VM operation thread holds this lock, a trapped thread will
 * eventually enter native code to block on the native monitor associated with the lock.
 * <p>
 * This mechanism is similar to but not exactly the same as the {@code VM_Operation} facility in HotSpot
 * except that {@link VmOperation}s can freeze a partial set of the running threads as Maxine implements
 * per-thread safepoints (HotSpot doesn't).</li>
 * <p>
 *
 * Implementation note:
 * It is simplest for a mutator thread to be blocked this way. Only under this condition can the
 * GC find every reference on a slave thread's stack.
 * If the mutator thread blocked in a spin loop instead, finding the references in the frame of
 * the spinning method is hard (what refmap would be used?). Even if the VM operation
 * is not a GC, it may want to walk the stack of the mutator thread. Doing
 * so requires the VM operation thread to be able to find the starting point for the stack walk
 * and this can only reliably be done (through use of the Java frame anchors) when the mutator
 * thread is blocked in native code.
 *
 * @author Mick Jordan
 * @author Doug Simon
 */
public class VmOperation {

    /**
     * Flag indicating which mechanism is to be used for freezing the mutator threads.
     *
     * @see VmOperation
     */
    public static final boolean UseCASBasedThreadFreezing = true;

    /**
     * Constant denoting a mutator thread is executing/blocked in native code and is
     * allowed to transition back to {@link #THREAD_IN_JAVA}. A thread enters this
     * state on every JNI transition to native code.
     */
    public static final Word THREAD_IN_NATIVE = Address.fromInt(0);

    /**
     * Constant denoting a mutator thread is executing Java code.
     */
    public static final Word THREAD_IN_JAVA = Address.fromInt(1);

    /**
     * Constant denoting a mutator thread is executing/blocked in native code and it
     * cannot transition back to {@link #THREAD_IN_JAVA} without the controlling
     * thread allowing it to do so by setting its state to {@link #THREAD_IN_NATIVE}.
     */
    public static final Word THREAD_IS_FROZEN = Address.fromInt(2);

    /**
     * Link to next node in list of VM operations.
     */
    VmOperation next;

    /**
     * Link to next node in list of VM operations.
     */
    VmOperation previous;

    /**
     * The operation (if any) in which this operation is nested.
     */
    VmOperation enclosing;

    public final Mode mode;

    /**
     * The thread that {@linkplain VmOperationThread#submit(VmOperation) submitted} this operation for execution.
     */
    private VmThread callingThread;

    /**
     * Constants denoting the conditions under which a VM operation must be run.
     */
    public enum Mode {
        Safepoint,
        NoSafepoint,
        Concurrent,
        AsyncSafepoint;

        public boolean requiresSafepoint() {
            return this == Safepoint || this == AsyncSafepoint;
        }

        public boolean isBlocking() {
            return this == Safepoint || this == NoSafepoint;
        }
    }

    /**
     * Gets the thread that {@linkplain VmOperationThread#submit(VmOperation) submitted} this
     * operation for execution.
     *
     * @return the thread that submitted this operation for execution
     */
    public VmThread callingThread() {
        if (enclosing != null) {
            return enclosing.callingThread();
        }
        return callingThread;
    }

    /**
     * Sets the thread that {@linkplain VmOperationThread#submit(VmOperation) submitted} this
     * operation for execution.
     *
     * @param thread the thread that submitted this operation for execution
     */
    public void setCallingThread(VmThread thread) {
        FatalError.check(next == null && previous == null, "Cannot change calling thread of operation already in the queue");
        callingThread = thread;
    }

    /**
     * Determines if this operation disables heap allocation.
     */
    protected boolean disablesHeapAllocation() {
        return false;
    }

    /**
     * Determines if this operation allows a nested operation to be performed.
     *
     * @param operation the nested operation in question (which may be {@code null})
     */
    protected boolean allowsNestedOperations(VmOperation operation) {
        return false;
    }

    /**
     * Called by the {@linkplain Trap trap} handler on a thread that hit a safepoint.
     * This is always called with safepoints {@linkplain Safepoint#disable() disabled}
     * for the current thread.
     *
     * @param trapState the register and other thread state at the safepoint
     */
    final void doAtSafepoint(Pointer trapState) {
        // note that this procedure always runs with safepoints disabled
        final Pointer vmThreadLocals = Safepoint.getLatchRegister();
        if (!VmThreadLocal.inJava(vmThreadLocals)) {
            FatalError.unexpected("Freezing thread trapped while in native code");
        }

        // This thread must only transition to native code as a result of
        // the synchronization below.
        // Such a transition will be interpreted by the VM operation thread to
        // mean that this thread is now stopped at a safepoint.
        // This invariant is enforced by disabling the ability to call native methods
        // within doAtSafepointBeforeBlocking().
        NativeStubSnippet.disableNativeCallsForCurrentThread();

        doAtSafepointBeforeBlocking(trapState);

        // Now re-enable the ability to call native code
        NativeStubSnippet.enableNativeCallsForCurrentThread();

        synchronized (VmThreadMap.THREAD_LOCK) {
            // block on the thread lock which is held by VM operation thread
        }

        doAtSafepointAfterBlocking(trapState);

        if (TraceVmOperations) {
            boolean lockDisabledSafepoints = Log.lock();
            Log.printCurrentThread(false);
            Log.println(": Thawed from safepoint");
            Log.unlock(lockDisabledSafepoints);
        }
    }

    /**
     * Called on the current thread (which just hit safepoint) before it is frozen.
     *
     * @param trapState the register and other thread state at the safepoint
     */
    protected void doAtSafepointBeforeBlocking(Pointer trapState) {
    }

    /**
     * Called on a mutator thread after it is thawed before it returns to the trap handler.
     *
     * @param trapState the register and other thread state at the safepoint
     */
    protected void doAtSafepointAfterBlocking(Pointer trapState) {
    }

    /**
     * Encapsulates the procedure run by the VM operation thread to thaw a frozen thread.
     */
    static final class ThawThread implements Pointer.Procedure {

        public static final VmOperation.ThawThread DEFAULT = new VmOperation.ThawThread();

        /**
         * Thaws a frozen thread.
         *
         * @param vmThreadLocals thread locals of the thread about to be thawed
         */
        public void run(Pointer vmThreadLocals) {

            /*
             * Set the value of the safepoint latch in the safepoints-enabled VM
             * thread locals to point to itself. This means that subsequent executions
             * of a safepoint instruction will not cause a trap until safepoints
             * are once again triggered.
             */
            SAFEPOINT_LATCH.setVariableWord(vmThreadLocals, SAFEPOINTS_ENABLED_THREAD_LOCALS.getConstantWord(vmThreadLocals));

            VM_OPERATION.setVariableReference(vmThreadLocals, null);

            if (UseCASBasedThreadFreezing) {
                MUTATOR_STATE.setVariableWord(vmThreadLocals, THREAD_IN_NATIVE);
            } else {
                // This must be last so that a frozen thread trying to return out of native code stays
                // frozen until its safepoint related state has been completely reset
                FROZEN.setVariableWord(vmThreadLocals, Address.zero());
            }
        }
    }

    /**
     * Performs the operation represented by this object. This is called once all targeted
     * threads have been frozen.
     *
     * The implementation of this method in {@link VmOperation} simply applies {@link #doThread}
     * to each frozen thread by calling {@link doAllThreads}.
     */
    protected void doIt() {
        doAllThreads();
    }

    /**
     * Called by {@link VmOperationThread#submit(VmOperation)} prior to scheduling this operation. This is called on the
     * thread trying to schedule a VM operation. It enables the operation scheduling to be canceled. It also enables the
     * operation to perform any action on the scheduling thread (such as taking locks) before the operation is run on
     * the VM operation thread. Unless this method returns {@code false}, then {@link #doItEpilogue(boolean)} will be
     * called on the current thread once the operation has completed.
     *
     * @param nested denotes if this is being called for a nested operation (which implies the current thread is the VM
     *            operation thread)
     * @return {@code true} if this operation should be scheduled. The {@link #doItEpilogue(boolean)} method will only
     *         be called if the operation is executed.
     */
    protected boolean doItPrologue(boolean nested) {
        return true;
    }

    /**
     * Called by {@link VmOperationThread#submit(VmOperation)} once this operation has completed execution or if this
     * operation's {@link #mode} denotes a {@linkplain Mode#isBlocking() non-blocking} operation, once this operation
     * has been {@linkplain #submit() submitted}.
     *
     * @param nested denotes if this is being called for a nested operation (which implies the current thread is the VM
     *            operation thread)
     */
    protected void doItEpilogue(boolean nested) {
    }

    /**
     * Predicate used with {@linkplain VmThreadMap#forAllThreadLocals(Predicate, com.sun.max.unsafe.Pointer.Procedure)}
     * to filter out the VM operation thread and all threads for which {@link #operateOnThread(VmThread)} returns
     * {@code false}.
     */
    private final Pointer.Predicate threadPredicate = new Pointer.Predicate() {
        @Override
        public boolean evaluate(Pointer vmThreadLocals) {
            VmThread vmThread = VmThread.fromVmThreadLocals(vmThreadLocals);
            return !vmThread.isVmOperationThread() && operateOnThread(vmThread);
        }
    };

    /**
     * Traverses over all frozen threads, applying {@link #doThread(Pointer, Pointer, Pointer, Pointer)} to each one.
     */
    protected final void doAllThreads() {
        if (singleThread == null) {
            VmThreadMap.ACTIVE.forAllThreadLocals(threadPredicate, doThreadAdapter);
        } else {
            Pointer vmThreadLocals = singleThread.vmThreadLocals();
            Pointer instructionPointer;
            Pointer stackPointer;
            Pointer framePointer;
            Pointer frameAnchor = JavaFrameAnchor.from(vmThreadLocals);
            assert !frameAnchor.isZero();
            instructionPointer = JavaFrameAnchor.PC.get(frameAnchor);
            stackPointer = JavaFrameAnchor.SP.get(frameAnchor);
            framePointer = JavaFrameAnchor.FP.get(frameAnchor);
            doThread(vmThreadLocals, instructionPointer, stackPointer, framePointer);
        }
    }

    /**
     * Performs an operation on a frozen thread.
     *
     * The definition of this method in {@link VmOperation} simply returns.
     *
     * @param threadLocals denotes the thread on which the operation is to be performed
     * @param ip instruction pointer at which the thread was frozen
     * @param sp stack pointer at which the thread was frozen
     * @param fp frame pointer at which the thread was frozen
     */
    protected void doThread(Pointer threadLocals, Pointer ip, Pointer sp, Pointer fp) {
    }

    /**
     * A descriptive name of the {@linkplain #doIt() operation} represented by this object. This value is only used for tracing.
     */
    public final String name;

    /**
     * The single thread operated on by this operation.
     */
    private final VmThread singleThread;

    /**
     * Adapter from {@link Pointer.Procedure#run(Pointer)} to {@linkplain #doThread(Pointer, Pointer, Pointer, Pointer)}.
     */
    private final Pointer.Procedure doThreadAdapter;

    /**
     * Creates a VM operation.
     *
     * @param name descriptive name of the {@linkplain #doIt() operation}. This value is only used for tracing.
     * @param singleThread the single thread operated on by this operation. If {@code null}, then
     *            {@link #operateOnThread(VmThread)} is called to determine which threads are to be operated on.
     */
    public VmOperation(String name, VmThread singleThread, Mode mode) {
        this.name = name;
        this.mode = mode;
        this.singleThread = singleThread;
        assert singleThread == null || !singleThread.isVmOperationThread();
        doThreadAdapter = new Pointer.Procedure() {
            public void run(Pointer vmThreadLocals) {
                Pointer instructionPointer;
                Pointer stackPointer;
                Pointer framePointer;
                Pointer frameAnchor = JavaFrameAnchor.from(vmThreadLocals);
                assert !frameAnchor.isZero();
                instructionPointer = JavaFrameAnchor.PC.get(frameAnchor);
                stackPointer = JavaFrameAnchor.SP.get(frameAnchor);
                framePointer = JavaFrameAnchor.FP.get(frameAnchor);
                doThread(vmThreadLocals, instructionPointer, stackPointer, framePointer);
            }
        };
    }

    /**
     * Convenience method equivalent to calling {@link VmOperationThread#submit(VmOperation)} with this operation.
     */
    public void submit() {
        VmOperationThread.submit(this);
    }

    /**
     * Called on the VM operation thread to perform this operation. This method does all the necessary
     * thread freezing and thawing around a call to {@link #doIt()}.
     */
    final void run() {
        assert VmThread.current().isVmOperationThread();
        assert singleThread == null || !singleThread.isVmOperationThread();

        if (mode.requiresSafepoint()) {
            Throwable error = null;
            synchronized (VmThreadMap.THREAD_LOCK) {

                tracePhase("-- Begin --");

                freeze();

                // Ensures updates to safepoint-related control variables are visible to all threads
                // before the VM operation thread reads them
                MemoryBarriers.storeLoad();

                waitUntilFrozen();

                try {
                    run0();
                } catch (Throwable t) {
                    if (TraceVmOperations) {
                        boolean lockDisabledSafepoints = Log.lock();
                        Log.print(name);
                        Log.print(": error while running operation: ");
                        Log.println(ObjectAccess.readClassActor(t).name.string);
                        Log.unlock(lockDisabledSafepoints);
                    }

                    // Errors are propagated once the remaining phases of the operation are complete
                    // otherwise frozen threads will never be unfrozen
                    error = t;
                }

                thaw();

                tracePhase("-- End --");
            }

            if (error != null) {
                if (error instanceof RuntimeException) {
                    throw (RuntimeException) error;
                } else if (error instanceof Error) {
                    throw (Error) error;
                } else {
                    throw (InternalError) new InternalError().initCause(error);
                }
            }
        } else {
            run0();
        }
    }

    private void run0() {
        tracePhase("Running operation");
        doIt();
    }

    private final Pointer.Procedure freezeThreadProcedure = new Pointer.Procedure() {
        @Override
        public void run(Pointer vmThreadLocals) {
            freezeThread(VmThread.fromVmThreadLocals(vmThreadLocals));
        }
    };

    private void freeze() {
        tracePhase("Freezing thread(s)");

        if (singleThread == null) {
            VmThreadMap.ACTIVE.forAllThreadLocals(threadPredicate, freezeThreadProcedure);
        } else {
            freezeThread(singleThread);
        }
    }

    final void freezeThread(VmThread thread) {

        if (frozenByEnclosing(thread)) {
            return;
        }

        Pointer vmThreadLocals = thread.vmThreadLocals();

        // Freeze a thread already in native code
        if (!UseCASBasedThreadFreezing) {
            FROZEN.setVariableWord(vmThreadLocals, Address.fromInt(1));
        }

        // spin until the VM_OPERATION variable is null
        final Pointer enabledVmThreadLocals = SAFEPOINTS_ENABLED_THREAD_LOCALS.getConstantWord(vmThreadLocals).asPointer();
        while (true) {
            if (enabledVmThreadLocals.getReference(VM_OPERATION.index).isZero()) {
                if (enabledVmThreadLocals.compareAndSwapReference(VM_OPERATION.offset, null, Reference.fromJava(this)).isZero()) {
                    /*
                     * Set the value of the safepoint latch in the safepoints-enabled VM
                     * thread locals to point to the safepoints-triggered VM thread locals.
                     * This will cause a safepoint trap the next time a safepoint
                     * instruction is executed while safepoints are enabled.
                     */
                    SAFEPOINT_LATCH.setVariableWord(vmThreadLocals, SAFEPOINTS_TRIGGERED_THREAD_LOCALS.getConstantWord(vmThreadLocals));
                    return;
                }
            }
            Thread.yield();
        }
    }

    /**
     * Determines if a given thread is in the scope of this operation. This method is only called
     * if this operation is not {@linkplain #VmOperation(String, Object, Mode) created} with a single thread.
     *
     * @param thread a thread in the global thread list
     * @return true if {@code thread} is operated on by this operation
     */
    protected boolean operateOnThread(VmThread thread) {
        return true;
    }

    final class CountThreadProcedure implements Pointer.Procedure {
        private int count;
        @Override
        public void run(Pointer vmThreadLocals) {
            count++;
        }
        public int count() {
            count = 0;
            VmThreadMap.ACTIVE.forAllThreadLocals(threadPredicate, this);
            return count;
        }
    }

    private final CountThreadProcedure countThreadProcedure = new CountThreadProcedure();

    /**
     * Gets the number of threads targeted by this operation.
     */
    public int countThreads() {
        return countThreadProcedure.count();
    }

    private void waitUntilFrozen() {
        tracePhase("Waiting for thread(s) to freeze");

        if (singleThread == null) {
            VmThreadMap.ACTIVE.forAllThreadLocals(threadPredicate, waitUntilFrozenProcedure);
        } else {
            waitForThreadFreeze(singleThread);
        }
    }

    private final Pointer.Procedure waitUntilFrozenProcedure = new Pointer.Procedure() {
        @Override
        public void run(Pointer vmThreadLocals) {
            waitForThreadFreeze(VmThread.fromVmThreadLocals(vmThreadLocals));
        }
    };

    /**
     * Called by {@link #waitForThreadFreeze(VmThread)}. Subclasses can use this to perform extra actions
     * on a thread once it is frozen.
     *
     * @param thread thread that is now frozen
     */
    protected void doAfterFrozen(VmThread thread) {
    }

    /**
     * Determines if this is a nested operation whose enclosing operation already froze a given thread.
     *
     * @param thread a thread to test
     */
    private boolean frozenByEnclosing(VmThread thread) {
        if (enclosing != null && enclosing.operateOnThread(thread)) {
            // This is a nested operation that operates on 'thread' -> the enclosing operation must have 'thread'
            if (UseCASBasedThreadFreezing) {
                FatalError.check(MUTATOR_STATE.getVariableWord(thread.vmThreadLocals()).equals(THREAD_IS_FROZEN), "Parent operation did not freeze thread");
            } else {
                FatalError.check(!MUTATOR_STATE.getVariableWord(thread.vmThreadLocals()).equals(THREAD_IN_JAVA), "Parent operation did not freeze thread");
            }
            return true;
        }
        return false;
    }

    static int SafepointSpinBeforeYield = 2000;
    static {
        VMOptions.addFieldOption("-XX:", "SafepointSpinBeforeYield",
            "Number of iterations in VM operation thread while waiting for a thread to freeze before falling back to yield or sleep");
    }

    /**
     * Pauses/yields/sleeps the VM operation thread while waiting for another thread to freeze.
     *
     * @param thread the thread we are waiting for
     * @param steps the number of times this has been called while waiting for {@code thread} to freeze
     */
    private static void waitForThreadFreezePause(VmThread thread, int steps) {
        if (steps < SafepointSpinBeforeYield) {
            SpecialBuiltin.pause();
        } else {
            int attempts = steps - SafepointSpinBeforeYield;
            if (attempts < 25) {
                VmThread.nonJniSleep(1);
            } else {
                VmThread.nonJniSleep(10);
            }
        }
    }

    /**
     * Blocks the current thread (i.e. the VM operation thread) until a given mutator thread is frozen.
     *
     * @param thread thread to wait for
     */
    final void waitForThreadFreeze(VmThread thread) {
        Pointer vmThreadLocals = thread.vmThreadLocals();

        int steps = 0;
        if (!frozenByEnclosing(thread)) {

            if (UseCASBasedThreadFreezing) {
                final Pointer enabledVmThreadLocals = SAFEPOINTS_ENABLED_THREAD_LOCALS.getConstantWord(vmThreadLocals).asPointer();
                while (true) {
                    if (enabledVmThreadLocals.getWord(MUTATOR_STATE.index).equals(THREAD_IN_NATIVE)) {
                        if (enabledVmThreadLocals.compareAndSwapWord(MUTATOR_STATE.offset, THREAD_IN_NATIVE, THREAD_IS_FROZEN).equals(THREAD_IN_NATIVE)) {
                            // Transitioned thread into frozen state
                            break;
                        }
                    } else if (enabledVmThreadLocals.getWord(MUTATOR_STATE.index).equals(THREAD_IS_FROZEN)) {
                        FatalError.unexpected("VM operation thread found an already frozen thread");
                    }
                    waitForThreadFreezePause(thread, steps);
                    steps++;
                }
            } else {
                while (MUTATOR_STATE.getVariableWord(vmThreadLocals).equals(THREAD_IN_JAVA)) {
                    // Wait for thread to be in native code, either as a result of a safepoint or because
                    // that's where it was when its FROZEN variable was set to true.
                    waitForThreadFreezePause(thread, steps);
                    steps++;
                }
            }
        }
        doAfterFrozen(thread);

        if (TraceVmOperations) {
            boolean lockDisabledSafepoints = Log.lock();
            Log.print("VmOperation[");
            Log.print(name);
            Log.print("]: Froze ");
            Log.printThread(thread, false);
            Log.println(TRAP_INSTRUCTION_POINTER.getVariableWord(vmThreadLocals).isZero() ? " in native code" : " at safepoint");
            Log.unlock(lockDisabledSafepoints);
        }
    }

    /**
     * Called just before a mutator thread is thawed by the VM operation thread.
     * Subclasses can use this to perform extra actions
     * on a thread before it is thawed.
     *
     * @param thread thread about to be thawed
     */
    protected void doBeforeThawingThread(VmThread thread) {
    }

    private final Pointer.Procedure thawThreadProcedure = new Pointer.Procedure() {
        public void run(Pointer vmThreadLocals) {
            thawThread(VmThread.fromVmThreadLocals(vmThreadLocals));
        }
    };

    /**
     * Thaws a frozen thread.
     *
     * @param vmThreadLocals thread locals of the thread about to be thawed
     */
    public final void thawThread(VmThread thread) {

        Pointer vmThreadLocals = thread.vmThreadLocals();

        doBeforeThawingThread(thread);

        if (frozenByEnclosing(thread)) {
            return;
        }

        /*
         * Set the value of the safepoint latch in the safepoints-enabled VM
         * thread locals to point to itself. This means that subsequent executions
         * of a safepoint instruction will not cause a trap until safepoints
         * are once again triggered.
         */
        SAFEPOINT_LATCH.setVariableWord(vmThreadLocals, SAFEPOINTS_ENABLED_THREAD_LOCALS.getConstantWord(vmThreadLocals));

        VM_OPERATION.setVariableReference(vmThreadLocals, null);

        if (UseCASBasedThreadFreezing) {
            MUTATOR_STATE.setVariableWord(vmThreadLocals, THREAD_IN_NATIVE);
        } else {
            // This must be last so that a frozen thread trying to return out of native code stays
            // frozen until its safepoint related state has been completely reset
            FROZEN.setVariableWord(vmThreadLocals, Address.zero());
        }
    }

    private void performOperation() {
        tracePhase("Running operation");
        doIt();
    }

    private void thaw() {
        tracePhase("Thawing thread(s)");

        if (singleThread == null) {
            VmThreadMap.ACTIVE.forAllThreadLocals(threadPredicate, thawThreadProcedure);
        } else {
            thawThread(singleThread);
        }
    }

    private void tracePhase(String phaseMsg) {
        if (TraceVmOperations) {
            boolean lockDisabledSafepoints = Log.lock();
            Log.print("VmOperation[");
            Log.print(name);
            Log.print("]: ");
            Log.println(phaseMsg);
            Log.unlock(lockDisabledSafepoints);
        }
    }
}
