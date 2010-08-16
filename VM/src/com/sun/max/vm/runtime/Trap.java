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

import static com.sun.max.vm.VMOptions.*;
import static com.sun.max.vm.runtime.Trap.Number.*;
import static com.sun.max.vm.runtime.VmOperation.*;
import static com.sun.max.vm.thread.VmThreadLocal.*;

import com.sun.max.annotate.*;
import com.sun.max.lang.*;
import com.sun.max.memory.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.code.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.stack.*;
import com.sun.max.vm.thread.*;

/**
 * This class handles operating systems traps that can arise from implicit null pointer checks, integer divide by zero,
 * safepoint triggering, etc. It contains a number of very low-level functions to directly handle operating system
 * signals (e.g. SIGSEGV on POSIX) and dispatch to the correct handler (e.g. construct and throw a null pointer
 * exception object or {@linkplain VmOperation freeze} the current thread).
 *
 * A small amount of native code supports this class by connecting to the OS-specific signal handling mechanisms.
 *
 * @author Ben L. Titzer
 * @author Doug Simon
 */
public abstract class Trap {

    /**
     * The numeric identifiers for the traps that can be handled by the VM. Note that these do not correspond with the
     * native signals.
     *
     * The values defined here (except for {@link #NULL_POINTER_EXCEPTION} and {@link #SAFEPOINT}) must correspond to
     * those of the same name defined in Native/substrate/trap.c.
     *
     * The {@link #NULL_POINTER_EXCEPTION} and {@link #SAFEPOINT} values are used in
     * {@link Trap#handleMemoryFault(Pointer, TargetMethod, Pointer, Pointer, Pointer, Address)} to disambiguate a memory fault.
     */
    public static final class Number {
        public static final int MEMORY_FAULT = 0;
        public static final int STACK_FAULT = 1;
        public static final int STACK_FATAL = 2;
        public static final int ILLEGAL_INSTRUCTION = 3;
        public static final int ARITHMETIC_EXCEPTION = 4;
        public static final int ASYNC_INTERRUPT = 5;
        public static final int NULL_POINTER_EXCEPTION = 6;
        public static final int SAFEPOINT = 7;

        public static String toExceptionName(int trapNumber) {
            switch (trapNumber) {
                case MEMORY_FAULT:
                    return "MEMORY_FAULT";
                case STACK_FAULT:
                    return "STACK_FAULT";
                case ILLEGAL_INSTRUCTION:
                    return "ILLEGAL_INSTRUCTION";
                case ARITHMETIC_EXCEPTION:
                    return "ARITHMETIC_EXCEPTION";
                case ASYNC_INTERRUPT:
                    return "ASYNC_INTERRUPT";
                case NULL_POINTER_EXCEPTION:
                    return "NULL_POINTER_EXCEPTION";
                case SAFEPOINT:
                    return "SAFEPOINT";
                default:
                    return "unknown";
            }
        }

        public static boolean isImplicitException(int trapNumber) {
            return trapNumber == ARITHMETIC_EXCEPTION || trapNumber == NULL_POINTER_EXCEPTION || trapNumber == STACK_FAULT  || trapNumber == STACK_FATAL;
        }

        public static Class<? extends Throwable> toImplicitExceptionClass(int trapNumber) {
            if (trapNumber == ARITHMETIC_EXCEPTION) {
                return ArithmeticException.class;
            } else if (trapNumber == NULL_POINTER_EXCEPTION) {
                return NullPointerException.class;
            } else if (trapNumber == STACK_FAULT || trapNumber == STACK_FATAL) {
                return StackOverflowError.class;
            }
            return null;
        }

        private Number() {
        }

        public static boolean isStackOverflow(Pointer trapState) {
            return TrapStateAccess.instance().getTrapNumber(trapState) == STACK_FAULT;
        }
    }

    private static boolean DumpStackOnTrap;
    static {
        VMOptions.addFieldOption("-XX:", "DumpStackOnTrap", Trap.class, "Reports a stack trace for every trap, regardless of the cause.", MaxineVM.Phase.PRISTINE);
    }

    /**
     * Whether to bang on the stack in the method prologue.
     */
    public static final boolean STACK_BANGING = true;

    /** The number of bytes reserved in the stack as a guard area.
     *  Note that SPARC code is more efficient if this is set below 6K.  Specifically, set to (6K - 1 - typical_frame_size).
     */
    public static final int stackGuardSize = 12 * Ints.K;

    /**
     * This method is {@linkplain #isTrapStub(MethodActor) known} by the compilation system. In particular, no adapter
     * frame code generated for it. As such, it's entry point is at it's first compiled instruction which corresponds
     * with its entry point it it were to be called from C code.
     */
    private static final CriticalMethod trapStub = new CriticalMethod(Trap.class, "trapStub", null, CallEntryPoint.C_ENTRY_POINT);

    /**
     * Determines if a given method actor denotes the method used to handle runtime traps.
     *
     * @param methodActor the method actor to test
     * @return true if {@code classMethodActor} is the actor for {@link #trapStub(int, Pointer, Address)}
     */
    public static boolean isTrapStub(MethodActor methodActor) {
        return methodActor == trapStub.classMethodActor;
    }

    @HOSTED_ONLY
    protected Trap() {
    }

    /**
     * The address of the 'traceTrap' static variable in 'trap.c'.
     */
    static Pointer nativeTraceTrapVariable = Pointer.zero();

    /**
     * A VM option to enable tracing of traps, both in the C and Java parts of trap handling.
     */
    private static VMBooleanXXOption traceTrap = register(new VMBooleanXXOption("-XX:-TraceTraps", "Trace traps.") {
        @Override
        public boolean parseValue(Pointer optionValue) {
            if (getValue() && !nativeTraceTrapVariable.isZero()) {
                nativeTraceTrapVariable.writeBoolean(0, true);
            }
            return true;
        }
    }, MaxineVM.Phase.PRISTINE);

    /**
     * Initializes the native side of trap handling by informing the C code of the address of {@link #trapStub(int, Pointer, Address)}.
     *
     * @param the entry point of {@link #trapStub(int, Pointer, Address)}
     * @return the address of the 'traceTrap' static variable in 'trap.c'
     */
    @C_FUNCTION
    private static native Pointer nativeInitialize(Word trapHandler);

    /**
     * Installs the trap handlers using the operating system's API.
     */
    public static void initialize() {
        nativeTraceTrapVariable = nativeInitialize(trapStub.address());
    }

    /**
     * This method handles traps that occurred during execution. This method has a special ABI produced by the compiler
     * that saves the entire register state onto the stack before beginning execution. When a trap occurs, the native
     * trap handler (see trap.c) saves a small amount of state in the disabled thread locals
     * for the thread (the trap number, the instruction pointer, and the fault address) and then returns to this stub.
     * This trap stub saves all of the registers onto the stack which are available in the {@code trapState}
     * pointer.
     *
     * @param trapNumber the trap (>= 0) or signal (< 0) number that occurred
     * @param trapState a pointer to the stack location where trap state is stored
     * @param faultAddress the faulting address that caused this trap (memory faults only)
     */
    @VM_ENTRY_POINT
    private static void trapStub(int trapNumber, Pointer trapState, Address faultAddress) {
        // From this point on until we return from the trap stub,
        // this variable is used to communicate to the VM operation thread
        // whether a thread was stopped at a safepoint or
        // in native code
        TRAP_INSTRUCTION_POINTER.setVariableWord(Pointer.zero());

        if (trapNumber == ASYNC_INTERRUPT) {
            VmThread.current().setInterrupted();
            return;
        }

        if (trapNumber < 0) {
            int signal = -trapNumber;
            if (!VmThread.current().isVmOperationThread()) {
                boolean lockDisabledSafepoints = Log.lock();
                Log.print("Asyncronous signal ");
                Log.print(signal);
                Log.print(" should be masked for non-VM operation thread ");
                Log.printThread(VmThread.current(), true);
                Log.unlock(lockDisabledSafepoints);
                FatalError.unexpected("Asynchronous signal delivered to non VM operation thread");
            }

            if (!UseCASBasedThreadFreezing && !FROZEN.getVariableWord().isZero()) {
                FatalError.unexpected("VM operation thread should never have non-zero value for FROZEN");
            }

            // The VM operation thread may be either in Java code (executing a VM operation) or in
            // native code (waiting on the VM operation queue). In both cases, it's imperative that
            // the MUTATOR_STATE variable is preserved across this trap handling.
            Word savedState = MUTATOR_STATE.getVariableWord();
            MUTATOR_STATE.setVariableWord(THREAD_IN_JAVA);
            SignalDispatcher.postSignal(signal);
            MUTATOR_STATE.setVariableWord(savedState);
            return;
        }

        final TrapStateAccess trapStateAccess = TrapStateAccess.instance();
        final Pointer instructionPointer = trapStateAccess.getInstructionPointer(trapState);
        final Object origin = checkTrapOrigin(trapNumber, trapState, faultAddress);
        if (origin instanceof TargetMethod) {
            // the trap occurred in Java
            final TargetMethod targetMethod = (TargetMethod) origin;
            final Pointer stackPointer = trapStateAccess.getStackPointer(trapState, targetMethod);
            final Pointer framePointer = trapStateAccess.getFramePointer(trapState, targetMethod);

            switch (trapNumber) {
                case MEMORY_FAULT:
                    handleMemoryFault(instructionPointer, targetMethod, stackPointer, framePointer, trapState, faultAddress);
                    break;
                case STACK_FAULT:
                    // stack overflow
                    raiseImplicitException(trapState, targetMethod, new StackOverflowError(), stackPointer, framePointer, instructionPointer);
                    break; // unreachable, except when returning to a local exception handler
                case ILLEGAL_INSTRUCTION:
                    // deoptimization
                    // TODO: deoptimization
                    FatalError.unexpected("illegal instruction", false, null, trapState);
                    break;
                case ARITHMETIC_EXCEPTION:
                    // integer divide by zero
                    raiseImplicitException(trapState, targetMethod, new ArithmeticException(), stackPointer, framePointer, instructionPointer);
                    break; // unreachable
                case STACK_FATAL:
                    // fatal stack overflow
                    FatalError.unexpected("fatal stack fault in red zone", false, null, trapState);
                    break; // unreachable
                default:
                    FatalError.unexpected("unknown trap number", false, null, trapState);

            }
        } else {
            // the fault occurred in native code
            Log.print("Trap in native code (or a runtime stub) @ ");
            Log.print(instructionPointer);
            Log.println(", exiting.");
            FatalError.unexpected("Trap in native code or a runtime stub", true, null, trapState);
        }
    }

    /**
     * Checks the origin of a trap by looking for a target method or runtime stub in the code regions. If found, this
     * method will return a reference to the {@code TargetMethod} that produced the trap. If a runtime stub produced
     * the trap, this method will return a reference to that runtime stub. Otherwise, this method returns {@code null},
     * indicating the trap occurred in native code.
     *
     * @param trapNumber the trap number
     * @param trapState the trap state area on the stack
     * @param faultAddress the faulting address that caused the trap (memory faults only)
     * @return a reference to the {@code TargetMethod} or {@link RuntimeStub} containing the instruction pointer that
     *         caused the trap or {@code null} if trap occurred in native code
     */
    private static Object checkTrapOrigin(int trapNumber, Pointer trapState, Address faultAddress) {
        final TrapStateAccess trapStateAccess = TrapStateAccess.instance();
        final Pointer instructionPointer = trapStateAccess.getInstructionPointer(trapState);

        // check to see if this fault originated in a target method
        final TargetMethod targetMethod = Code.codePointerToTargetMethod(instructionPointer);

        if (traceTrap.getValue() || DumpStackOnTrap) {
            final boolean lockDisabledSafepoints = Log.lock();
            Log.printCurrentThread(false);
            if (targetMethod != null) {
                Log.print(": Trapped in ");
                Log.printMethod(targetMethod, true);
            } else {
                Log.println(": Trapped in <unknown>");
            }
            Log.print("  Trap number=");
            Log.println(trapNumber);
            Log.print("  Instruction pointer=");
            Log.println(instructionPointer);
            Log.print("  Fault address=");
            Log.println(faultAddress);
            trapStateAccess.logTrapState(trapState);
            if (DumpStackOnTrap) {
                Throw.stackDump("Stack trace:", instructionPointer, trapStateAccess.getStackPointer(trapState, null), trapStateAccess.getFramePointer(trapState, null));
            }
            Log.unlock(lockDisabledSafepoints);
        }

        if (targetMethod != null) {
            return targetMethod;
        }

        // this fault occurred in native code
        return null;
    }

    /**
     * Handle a memory fault for this thread. A memory fault can be caused by an implicit null pointer check,
     * a safepoint being triggered, or a segmentation fault in native code.
     *
     * @param instructionPointer the instruction pointer that caused the fault
     * @param targetMethod the TargetMethod containing {@code instructionPointer}
     * @param stackPointer the stack pointer at the time of the fault
     * @param framePointer the frame pointer at the time of the fault
     * @param trapState a pointer to the trap state at the time of the fault
     * @param faultAddress the address that caused the fault
     */
    private static void handleMemoryFault(Pointer instructionPointer, TargetMethod targetMethod, Pointer stackPointer, Pointer framePointer, Pointer trapState, Address faultAddress) {
        final Pointer disabledVmThreadLocals = VmThread.currentVmThreadLocals();

        final Safepoint safepoint = VMConfiguration.hostOrTarget().safepoint;
        final TrapStateAccess trapStateAccess = TrapStateAccess.instance();
        final Pointer triggeredVmThreadLocals = SAFEPOINTS_TRIGGERED_THREAD_LOCALS.getConstantWord(disabledVmThreadLocals).asPointer();
        final Pointer safepointLatch = trapStateAccess.getSafepointLatch(trapState);

        if (VmThread.current().isVmOperationThread()) {
            FatalError.unexpected("Memory fault on a GC thread", false, null, trapState);
        }

        // check to see if a safepoint has been triggered for this thread
        if (safepointLatch.equals(triggeredVmThreadLocals) && safepoint.isAt(instructionPointer)) {
            // a safepoint has been triggered for this thread
            final Reference reference = VM_OPERATION.getVariableReference(triggeredVmThreadLocals);
            final VmOperation vmOperation = (VmOperation) reference.toJava();
            trapStateAccess.setTrapNumber(trapState, Number.SAFEPOINT);
            if (vmOperation != null) {
                TRAP_INSTRUCTION_POINTER.setVariableWord(instructionPointer);
                vmOperation.doAtSafepoint(trapState);
                TRAP_INSTRUCTION_POINTER.setVariableWord(Pointer.zero());
            } else {
                /*
                 * The interleaving of a mutator thread and a freezer thread below demonstrates
                 * one case where this can occur:
                 *
                 *    Mutator thread        |  VmOperationThread
                 *  ------------------------+-----------------------------------------------------------------
                 *                          |  set VM_OPERATION and trigger safepoints for mutator thread
                 *  loop: safepoint         |
                 *        enter native      |
                 *                          |  complete operation (e.g. GC)
                 *                          |  reset safepoints and clear VM_OPERATION for mutator thread
                 *        return from native|
                 *  loop: safepoint         |
                 *
                 * The first safepoint instruction above loads the address of triggered VM thread locals
                 * into the latch register. The second safepoint instruction dereferences the latch
                 * register causing the trap. That is, the VM operation thread triggered safepoints in the
                 * mutator to freeze but actually froze it as a result of the mutator making a
                 * native call between 2 safepoint instructions (it takes 2 executions of a safepoint
                 * instruction to cause the trap).
                 *
                 * The second safepoint instruction on the mutator thread will cause a trap when
                 * VM_OPERATION for the mutator is null.
                 */
            }
            // The state of the safepoint latch was TRIGGERED when the trap happened. It must be reset back to ENABLED
            // here otherwise another trap will occur as soon as the trap stub returns and re-executes the
            // safepoint instruction.
            final Pointer enabledVmThreadLocals = SAFEPOINTS_ENABLED_THREAD_LOCALS.getConstantWord(disabledVmThreadLocals).asPointer();
            trapStateAccess.setSafepointLatch(trapState, enabledVmThreadLocals);

        } else if (inJava(disabledVmThreadLocals)) {
            trapStateAccess.setTrapNumber(trapState, Number.NULL_POINTER_EXCEPTION);
            // null pointer exception
            raiseImplicitException(trapState, targetMethod, new NullPointerException(), stackPointer, framePointer, instructionPointer);
        } else {
            // segmentation fault happened in native code somewhere, die.
            FatalError.unexpected("Trap in native code", true, null, trapState);
        }
    }

    /**
     * Raises an implicit exception.
     *
     * If there is a local handler for the exception (i.e. a handler in the same frame in which the exception occurred)
     * and the method in which the exception occurred was compiled by the opto compiler, then the trap state is altered
     * so that the exception object is placed into the register typically used for an integer return value (e.g. RAX on
     * AMD64) and the return address for the trap frame is set to be the exception handler entry address. This means
     * that the register allocator in the opto compiler can assume that registers are not modified in the control flow
     * from an implicit exception to the exception handler (apart from the register now holding the exception object).
     *
     * Otherwise, the {@linkplain Throw#raise(Throwable, Pointer, Pointer, Pointer) standard mechanism} for throwing an
     * exception is used.
     *
     * @param trapState a pointer to the buffer on the stack containing the trap state
     * @param targetMethod the target method containing the trap address
     * @param throwable the throwable to raise
     * @param sp the stack pointer at the time of the trap
     * @param fp the frame pointer at the time of the trap
     * @param ip the instruction pointer which caused the trap
     */
    private static void raiseImplicitException(Pointer trapState, TargetMethod targetMethod, Throwable throwable, Pointer sp, Pointer fp, Pointer ip) {
        StackReferenceMapPreparer.verifyReferenceMapsForThisThread();
        if (!targetMethod.isJitCompiled()) {
            final Address catchAddress = targetMethod.throwAddressToCatchAddress(true, ip, throwable.getClass());
            if (!catchAddress.isZero()) {
                final TrapStateAccess trapStateAccess = TrapStateAccess.instance();
                trapStateAccess.setInstructionPointer(trapState, catchAddress.asPointer());
                EXCEPTION_OBJECT.setConstantReference(Reference.fromJava(throwable));

                if (throwable instanceof StackOverflowError) {
                    // This complete call-chain must be inlined down to the native call
                    // so that no further stack banging instructions
                    // are executed before execution jumps to the catch handler.
                    VirtualMemory.protectPages(VmThread.current().stackYellowZone(), VmThread.STACK_YELLOW_ZONE_PAGES);
                }
                return;
            }
        }
        VmThread.current().unwindingOrReferenceMapPreparingStackFrameWalker().unwind(ip, sp, fp, throwable);
    }
}
