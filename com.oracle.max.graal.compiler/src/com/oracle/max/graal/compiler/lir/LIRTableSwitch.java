/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.lir;

import com.sun.cri.ci.*;
import com.sun.cri.ci.CiValue.Formatter;

public class LIRTableSwitch extends LIRInstruction {

    public LIRBlock defaultTarget;

    public final LIRBlock[] targets;

    public final int lowKey;

    public LIRTableSwitch(LIROpcode opcode, int lowKey, LIRBlock defaultTarget, LIRBlock[] targets, CiValue[] inputs, CiValue[] temps) {
        super(opcode, CiValue.IllegalValue, null, false, inputs, temps);
        this.lowKey = lowKey;
        this.targets = targets;
        this.defaultTarget = defaultTarget;
    }

    @Override
    public String operationString(Formatter operandFmt) {
        StringBuilder buf = new StringBuilder(super.operationString(operandFmt));
        buf.append("\ndefault: [B").append(defaultTarget.blockID()).append(']');
        int key = lowKey;
        for (LIRBlock b : targets) {
            buf.append("\ncase ").append(key).append(": [B").append(b.blockID()).append(']');
            key++;
        }
        return buf.toString();
    }


    private LIRBlock substitute(LIRBlock block, LIRBlock oldBlock, LIRBlock newBlock) {
        if (block == oldBlock) {
            LIRInstruction instr = newBlock.lir().get(0);
            assert instr instanceof LIRLabel : "first instruction of block must be label";
            return newBlock;
        }
        return oldBlock;
    }

    public void substitute(LIRBlock oldBlock, LIRBlock newBlock) {
        if (substitute(defaultTarget, oldBlock, newBlock) == newBlock) {
            defaultTarget = newBlock;
        }
        for (int i = 0; i < targets.length; i++) {
            if (substitute(targets[i], oldBlock, newBlock) == newBlock) {
                targets[i] = newBlock;
            }
        }
    }
}
