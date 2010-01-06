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
package com.sun.max.vm.stack.sparc;

import com.sun.max.lang.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.cps.jit.sparc.*;
import com.sun.max.vm.stack.*;

/**
 * Mechanism for accessing values on a stack frame for a method produced by the
 * {@linkplain SPARCJitCompiler SPARC JIT compiler}.
 *
 * @author Laurent Daynes
 * @author Doug Simon
 * @author Bernd Mathiske
 */
public class SPARCJitStackFrame extends JitStackFrame {

    private final Pointer localVariablesBase;

    public SPARCJitStackFrame(StackFrame callee, TargetMethod targetMethod,
                    Pointer instructionPointer,
                    Pointer stackPointer,
                    Pointer framePointer,
                    Pointer localVariablesBase) {
        super(callee, new SPARCJitStackFrameLayout(targetMethod), targetMethod, instructionPointer, framePointer, stackPointer);
        this.localVariablesBase = localVariablesBase;
    }

    @Override
    public Pointer localsPointer(int index) {
        return localVariablesBase.plus(layout.localVariableOffset(index));
    }

    @Override
    public Pointer operandStackPointer(int index) {
        return localVariablesBase.plus(layout.operandStackOffset(index));
    }

    @Override
    public int operandStackDepth() {
        final Pointer operandStackBase = operandStackPointer(0);
        return Unsigned.idiv(sp.minus(operandStackBase).toInt(), JitStackFrameLayout.JIT_SLOT_SIZE);
    }

    @Override
    public boolean isSameFrame(StackFrame stackFrame) {
        if (stackFrame instanceof SPARCJitStackFrame) {
            final SPARCJitStackFrame jitStackFrame = (SPARCJitStackFrame) stackFrame;
            return targetMethod().equals(stackFrame.targetMethod()) && localVariablesBase.equals(jitStackFrame.localVariablesBase);
        }
        return false;
    }

    @Override
    public Offset biasedFPOffset(Offset offset) {
        // Stack slot offset are positive relative to the stack pointer. To convert them as offset to the frame pointer, we need the frame size.
        return SPARCStackFrameLayout.slotOffsetFromFrame(targetMethod().frameSize(), offset);
    }

    @Override
    public StackBias bias() {
        return StackBias.JIT_SPARC_V9;
    }

}