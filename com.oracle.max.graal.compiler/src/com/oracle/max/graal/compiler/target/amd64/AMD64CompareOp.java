/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.target.amd64;

import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.graal.compiler.asm.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.util.*;
import com.sun.cri.ci.*;

public enum AMD64CompareOp implements LIROpcode<AMD64MacroAssembler, LIRInstruction>, LIROpcode.SecondOperandCanBeMemory {
    ICMP, LCMP, ACMP, FCMP, DCMP;

    public LIRInstruction create(CiVariable left, CiValue right) {
        assert (name().startsWith("I") && left.kind == CiKind.Int && right.kind.stackKind() == CiKind.Int)
            || (name().startsWith("I") && left.kind == CiKind.Jsr && right.kind == CiKind.Jsr)
            || (name().startsWith("L") && left.kind == CiKind.Long && right.kind == CiKind.Long)
            || (name().startsWith("A") && left.kind == CiKind.Object && right.kind == CiKind.Object)
            || (name().startsWith("F") && left.kind == CiKind.Float && right.kind == CiKind.Float)
            || (name().startsWith("D") && left.kind == CiKind.Double && right.kind == CiKind.Double);

        return new LIRInstruction(this, CiValue.IllegalValue, null, left, right);
    }

    @Override
    public void emitCode(TargetMethodAssembler<AMD64MacroAssembler> tasm, LIRInstruction op) {
        assert op.info == null;
        assert op.operand(0).kind == op.operand(1).kind.stackKind();

        CiRegister left = tasm.asRegister(op.operand(0));
        CiValue right = op.operand(1);

        AMD64MacroAssembler masm = tasm.masm;
        if (right.isRegister()) {
            CiRegister rreg = tasm.asRegister(right);
            switch (this) {
                case ICMP: masm.cmpl(left, rreg); break;
                case LCMP: masm.cmpq(left, rreg); break;
                case ACMP: masm.cmpptr(left, rreg); break;
                case FCMP: masm.ucomiss(left, rreg); break;
                case DCMP: masm.ucomisd(left, rreg); break;
                default:   throw Util.shouldNotReachHere();
            }
        } else if (right.isConstant()) {
            switch (this) {
                case ICMP: masm.cmpl(left, tasm.asIntConst(right)); break;
                case LCMP: masm.cmpq(left, tasm.asIntConst(right)); break;
                case ACMP:
                    if (((CiConstant) right).isNull()) {
                        masm.cmpq(left, 0); break;
                    } else {
                        throw Util.shouldNotReachHere("Only null object constants are allowed in comparisons");
                    }
                case FCMP: masm.ucomiss(left, tasm.asFloatConstRef(right)); break;
                case DCMP: masm.ucomisd(left, tasm.asDoubleConstRef(right)); break;
                default:   throw Util.shouldNotReachHere();
            }
        } else {
            CiAddress raddr = tasm.asAddress(right);
            switch (this) {
                case ICMP: masm.cmpl(left, raddr); break;
                case LCMP: masm.cmpq(left, raddr); break;
                case ACMP: masm.cmpptr(left, raddr); break;
                case FCMP: masm.ucomiss(left, raddr); break;
                case DCMP: masm.ucomisd(left, raddr); break;
                default:  throw Util.shouldNotReachHere();
            }
        }
    }
}
