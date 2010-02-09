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
package com.sun.max.vm.cps.jit.amd64;

import com.sun.max.annotate.*;
import com.sun.max.lang.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.bytecode.*;
import com.sun.max.vm.classfile.*;
import com.sun.max.vm.classfile.constant.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.cps.jit.*;
import com.sun.max.vm.cps.target.*;
import com.sun.max.vm.cps.target.amd64.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.stack.*;
import com.sun.max.vm.stack.StackFrameWalker.*;
import com.sun.max.vm.stack.amd64.*;
import com.sun.max.vm.type.*;

/**
 * @author Bernd Mathiske
 * @author Laurent Daynes
 * @author Doug Simon
 * @author Paul Caprioli
 */
public class AMD64JitTargetMethod extends JitTargetMethod {

    private static final byte ENTER = (byte) 0xC8;
    private static final byte LEAVE = (byte) 0xC9;
    private static final byte POP_RBP = (byte) 0x5D;

    private static final byte RET = (byte) 0xC3;
    private static final byte RET2 = (byte) 0xC2;


    public AMD64JitTargetMethod(ClassMethodActor classMethodActor) {
        super(classMethodActor);
    }

    @Override
    protected CPSTargetMethod createDuplicate() {
        return new AMD64JitTargetMethod(classMethodActor);
    }

    @Override
    public int callerInstructionPointerAdjustment() {
        return -1;
    }

    @Override
    public int bytecodePositionForCallSite(Pointer returnInstructionPointer) {
        // The instruction pointer is now just beyond the call machine instruction.
        // In case the call happens to be the last machine instruction for the invoke bytecode we are interested in, we subtract one byte.
        // Thus we always look up what bytecode we were in during the call.
        return bytecodePositionFor(returnInstructionPointer.minus(1));
    }

    @Override
    public final int registerReferenceMapSize() {
        return AMD64TargetMethod.registerReferenceMapSize();
    }

    @Override
    public final void patchCallSite(int callOffset, Word callEntryPoint) {
        AMD64TargetMethod.patchCall32Site(this, callOffset, callEntryPoint);
    }

    @Override
    public void forwardTo(TargetMethod newTargetMethod) {
        AMD64TargetMethod.forwardTo(this, newTargetMethod);
    }

    /**
     * Various execution states in a JIT method that can only be observed in
     * the context of the Inspector.
     *
     * @author Laurent Daynes
     */
    @HOSTED_ONLY
    enum FramePointerState {
        /**
         * RBP holds the frame pointer of the current method activation. caller's RIP is at [RBP + FrameSize], caller's
         * frame pointer is at [RBP + FrameSize -1]
         */
        IN_RBP {

            @Override
            Pointer localVariablesBase(Cursor current) {
                return current.fp();
            }

            @Override
            Pointer returnIP(Cursor current) {
                JitTargetMethod targetMethod = (JitTargetMethod) current.targetMethod();
                int dispToRip = targetMethod.frameSize() - targetMethod.sizeOfNonParameterLocals();
                return current.fp().plus(dispToRip);
            }

            @Override
            Pointer callerFP(Cursor current) {
                return current.stackFrameWalker().readWord(returnIP(current), -Word.size()).asPointer();
            }
        },

        /**
         * RBP holds the frame pointer of the caller, caller's RIP is at [RSP] This state occurs when entering the
         * method or exiting it.
         */
        CALLER_FRAME_IN_RBP {

            @Override
            Pointer localVariablesBase(Cursor current) {
                int offsetToSaveArea = current.targetMethod().frameSize();
                return current.sp().minus(offsetToSaveArea);
            }

            @Override
            Pointer returnIP(Cursor current) {
                return current.sp();
            }

            @Override
            Pointer callerFP(Cursor current) {
                return current.fp();
            }
        },

        /**
         * RBP points at the bottom of the "saving area". Caller's frame pointer is at [RBP], caller's RIP is at [RBP +
         * WordSize].
         */
        CALLER_FRAME_AT_RBP {

            @Override
            Pointer localVariablesBase(Cursor current) {
                JitTargetMethod targetMethod = (JitTargetMethod) current.targetMethod();
                int dispToFrameStart = targetMethod.frameSize() - (targetMethod.sizeOfNonParameterLocals() + Word.size());
                return current.fp().minus(dispToFrameStart);
            }

            @Override
            Pointer returnIP(Cursor current) {
                return current.fp().plus(Word.size());
            }

            @Override
            Pointer callerFP(Cursor current) {
                return current.stackFrameWalker().readWord(current.fp(), 0).asPointer();
            }
        },

        /**
         * Returning from a runtime call (or actually in a runtime call). RBP may have been clobbered by the runtime.
         * The frame pointer for the current activation record is 'RSP + stack slot size'.
         */
        RETURNING_FROM_RUNTIME {

            @Override
            Pointer localVariablesBase(Cursor current) {
                return current.stackFrameWalker().readWord(current.sp(), 0).asPointer();
            }

            @Override
            Pointer returnIP(Cursor current) {
                JitTargetMethod targetMethod = (JitTargetMethod) current.targetMethod();
                int dispToRip = targetMethod.frameSize() - targetMethod.sizeOfNonParameterLocals();
                return localVariablesBase(current).plus(dispToRip);
            }

            @Override
            Pointer callerFP(Cursor current) {
                return current.stackFrameWalker().readWord(returnIP(current), -Word.size()).asPointer();
            }
        };

        abstract Pointer localVariablesBase(Cursor current);

        abstract Pointer returnIP(Cursor current);

        abstract Pointer callerFP(Cursor current);
    }

    @Override
    public void prepareReferenceMap(Cursor current, Cursor callee, StackReferenceMapPreparer preparer) {
        finalizeReferenceMaps();

        if (callee.calleeKind() == StackFrameWalker.CalleeKind.TRAMPOLINE) {
            prepareTrampolineRefMap(current, preparer);
        }

        int stopIndex = findClosestStopIndex(current.ip());
        int frameReferenceMapSize = frameReferenceMapSize();

        // prepare the map for this stack frame
        Pointer slotPointer = current.fp().plus(frameReferenceMapOffset);
        preparer.tracePrepareReferenceMap(this, stopIndex, slotPointer, "JIT frame");
        int byteIndex = stopIndex * frameReferenceMapSize;
        for (int i = 0; i < frameReferenceMapSize; i++) {
            preparer.setReferenceMapBits(current, slotPointer, referenceMaps[byteIndex] & 0xff, Bytes.WIDTH);
            slotPointer = slotPointer.plusWords(Bytes.WIDTH);
            byteIndex++;
        }
    }

    @Override
    public void catchException(Cursor current, Cursor callee, Throwable throwable) {
        StackFrameWalker stackFrameWalker = current.stackFrameWalker();
        Address throwAddress = current.ip();
        Address catchAddress = throwAddressToCatchAddress(current.isTopFrame(), throwAddress, throwable.getClass());

        if (!catchAddress.isZero()) {
            if (StackFrameWalker.TRACE_STACK_WALK.getValue()) {
                Log.print("StackFrameWalk: Handler position for exception at position ");
                Log.print(throwAddress.minus(codeStart()).toInt());
                Log.print(" is ");
                Log.println(catchAddress.minus(codeStart()).toInt());
            }

            Pointer localVariablesBase = current.fp();
            // The Java operand stack of the method that handles the exception is always cleared.
            // A null object is then pushed to ensure the depth of the stack is as expected upon
            // entry to an exception handler. However, the handler must have a prologue that loads
            // the exception from VmThreadLocal.EXCEPTION_OBJECT which is indeed guaranteed by
            // ExceptionDispatcher.
            // Compute the offset to the first stack slot of the Java Stack: frame size - (space for locals + saved RBP
            // + space of the first slot itself).
            Pointer catcherStackPointer = localVariablesBase.minus(sizeOfNonParameterLocals() + JitStackFrameLayout.JIT_SLOT_SIZE);
            // Push the null object on top of the stack first
            catcherStackPointer.writeReference(0, null);

            // found an exception handler, and thus we are done with the stack walker
            stackFrameWalker.reset();

            // Completes the exception handling protocol (with respect to the garbage collector) initiated in
            // Throwing.raise()
            Safepoint.enable();

            AMD64JitCompiler.unwind(throwable, catchAddress, catcherStackPointer, localVariablesBase);
            // We should never reach here
        }
    }

    @Override
    public boolean acceptStackFrameVisitor(Cursor current, StackFrameVisitor visitor) {
        StackFrameWalker stackFrameWalker = current.stackFrameWalker();
        Pointer localVariablesBase = current.fp();
        if (MaxineVM.isHosted()) {
            // Inspector context only
            Pointer startOfPrologue;
            AdapterGenerator generator = AdapterGenerator.forCallee(this);
            if (generator != null) {
                startOfPrologue = codeStart.plus(generator.prologueSizeForCallee(classMethodActor));
            } else {
                startOfPrologue = codeStart;
            }
            Pointer lastPrologueInstruction = startOfPrologue.plus(AMD64JitCompiler.OFFSET_TO_LAST_PROLOGUE_INSTRUCTION);
            FramePointerState framePointerState = computeFramePointerState(current, stackFrameWalker, lastPrologueInstruction);
            localVariablesBase = framePointerState.localVariablesBase(current);
        }
        StackFrame stackFrame = new AMD64JitStackFrame(stackFrameWalker.calleeStackFrame(), current.targetMethod(), current.ip(), current.sp(), localVariablesBase, localVariablesBase);
        return visitor.visitFrame(stackFrame);
    }

    @Override
    public void advance(Cursor current) {
        StackFrameWalker stackFrameWalker = current.stackFrameWalker();
        int dispToRip = frameSize() - sizeOfNonParameterLocals();
        Pointer returnRIP = current.fp().plus(dispToRip);
        Pointer callerFP = stackFrameWalker.readWord(returnRIP, -Word.size()).asPointer();
        if (MaxineVM.isHosted()) {
            // Inspector context only
            Pointer startOfPrologue;
            AdapterGenerator generator = AdapterGenerator.forCallee(this);
            if (generator != null) {
                if (generator.advanceIfInPrologue(current)) {
                    return;
                }
                startOfPrologue = codeStart.plus(generator.prologueSizeForCallee(classMethodActor));
            } else {
                startOfPrologue = codeStart;
            }

            Pointer lastPrologueInstruction = startOfPrologue.plus(AMD64JitCompiler.OFFSET_TO_LAST_PROLOGUE_INSTRUCTION);
            FramePointerState framePointerState = computeFramePointerState(current, stackFrameWalker, lastPrologueInstruction);
            returnRIP = framePointerState.returnIP(current);
            callerFP = framePointerState.callerFP(current);

        }
        Pointer callerIP = stackFrameWalker.readWord(returnRIP, 0).asPointer();
        Pointer callerSP = returnRIP.plus(Word.size()); // Skip the rip

        int stackAmountInBytes = classMethodActor.numberOfParameterSlots() * JitStackFrameLayout.JIT_SLOT_SIZE;
        if (stackAmountInBytes != 0) {
            callerSP = callerSP.plus(stackAmountInBytes);
        }

        stackFrameWalker.advance(callerIP, callerSP, callerFP);
    }

    /**
     * Prepares the reference map to cover the reference parameters on the stack at a call from a JIT compiled method
     * into a trampoline. These slots are normally ignored when computing the reference maps for a JIT'ed method as they
     * are covered by a reference map in the callee if necessary. They <b>cannot</b> be covered by a reference map in
     * the JIT'ed method as these slots are seen as local variables in a JIT callee and as such can be overwritten with
     * non-reference values.
     *
     * However, in the case where a JIT'ed method calls into a trampoline, the reference parameters of the call are not
     * covered by any reference map. In this situation, we need to analyze the invokeXXX bytecode at the call site to
     * derive the signature of the call which in turn allows us to mark the parameter stack slots that contain
     * references.
     *
     * @param caller the JIT compiled method's frame cursor
     */
    private void prepareTrampolineRefMap(Cursor caller, StackReferenceMapPreparer preparer) {
        // prepare the reference map for the parameters passed by the current (caller) frame.
        // the call was unresolved and hit a trampoline, so compute the refmap from the signature of
        // the called method by looking at the bytecode of the caller method--how ugly...
        final int bytecodePosition = bytecodePositionForCallSite(caller.ip());
        final CodeAttribute codeAttribute = classMethodActor().codeAttribute();
        final ConstantPool constantPool = codeAttribute.constantPool;
        final byte[] code = codeAttribute.code();
        final MethodRefConstant methodConstant = constantPool.methodAt(getInvokeConstantPoolIndexOperand(code, bytecodePosition));
        final boolean isInvokestatic = (code[bytecodePosition] & 0xFF) == Bytecode.INVOKESTATIC.ordinal();
        final SignatureDescriptor signature = methodConstant.signature(constantPool);

        int slotSize = JitStackFrameLayout.JIT_SLOT_SIZE;
        final int numberOfSlots = signature.computeNumberOfSlots() + (isInvokestatic ? 0 : 1);

        if (numberOfSlots != 0) {
            // Handle the parameters in reverse order as caller.sp() is currently
            // pointing at the last parameter.
            Pointer slotPointer = caller.sp();
            for (int i = signature.numberOfParameters() - 1; i >= 0; --i) {
                final TypeDescriptor parameter = signature.parameterDescriptorAt(i);
                final Kind parameterKind = parameter.toKind();
                if (parameterKind == Kind.REFERENCE) {
                    preparer.setReferenceMapBits(caller, slotPointer, 1, 1);
                }
                int parameterSlots = parameterKind.isCategory2() ? 2 : 1;
                slotPointer = slotPointer.plus(slotSize * parameterSlots);
            }

            // Finally deal with the receiver (if any)
            if (!isInvokestatic) {
                // Mark the slot for the receiver as it is not covered by the method signature:
                preparer.setReferenceMapBits(caller, slotPointer, 1, 1);
            }
        }
    }

    @HOSTED_ONLY
    private FramePointerState computeFramePointerState(Cursor current, StackFrameWalker stackFrameWalker, Pointer lastPrologueInstr) {
        Pointer instructionPointer = current.ip();
        byte byteAtInstructionPointer = stackFrameWalker.readByte(instructionPointer, 0);
        if (instructionPointer.lessThan(lastPrologueInstr) || byteAtInstructionPointer == ENTER || byteAtInstructionPointer == RET || byteAtInstructionPointer == RET2) {
            return FramePointerState.CALLER_FRAME_IN_RBP;
        }
        if (instructionPointer.equals(lastPrologueInstr) || byteAtInstructionPointer == LEAVE) {
            return FramePointerState.CALLER_FRAME_AT_RBP;
        }
        if (byteAtInstructionPointer == POP_RBP) {
            return FramePointerState.RETURNING_FROM_RUNTIME;
        }
        return FramePointerState.IN_RBP;
    }

    private static int getInvokeConstantPoolIndexOperand(byte[] code, int invokeOpcodePosition) {
        return ((code[invokeOpcodePosition + 1] & 0xff) << 8) | (code[invokeOpcodePosition + 2] & 0xff);
    }

}
