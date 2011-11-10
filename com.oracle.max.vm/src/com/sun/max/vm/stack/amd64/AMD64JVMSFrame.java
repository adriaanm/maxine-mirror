/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.sun.max.vm.stack.amd64;

import com.sun.max.annotate.*;
import com.oracle.max.cri.intrinsics.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.stack.*;

/**
 * Mechanism for accessing values on a stack frame conforming to {@link JVMSFrameLayout}.
 *
 * @see AMD64JVMSFrameLayout
 */
@HOSTED_ONLY
public class AMD64JVMSFrame extends JVMSFrame {

    private final Pointer localVariablesBase;

    public AMD64JVMSFrame(StackFrame callee, TargetMethod targetMethod, Pointer instructionPointer,
                    Pointer stackPointer,
                    Pointer framePointer,
                    Pointer localVariablesBase) {
        super(callee, targetMethod, instructionPointer, framePointer, stackPointer);
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
        return UnsignedMath.divide(sp.minus(operandStackBase).toInt(), JVMSFrameLayout.JVMS_SLOT_SIZE);
    }

    @Override
    public boolean isSameFrame(StackFrame stackFrame) {
        if (stackFrame instanceof AMD64JVMSFrame) {
            final AMD64JVMSFrame jvmsStackFrame = (AMD64JVMSFrame) stackFrame;
            return targetMethod().equals(stackFrame.targetMethod()) && localVariablesBase.equals(jvmsStackFrame.localVariablesBase);
        }
        return false;
    }
}
