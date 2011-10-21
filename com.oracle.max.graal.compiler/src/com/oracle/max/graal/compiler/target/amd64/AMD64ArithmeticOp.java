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
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.util.*;
import com.sun.cri.ci.*;

public enum AMD64ArithmeticOp implements LIROpcode<AMD64LIRAssembler, LIRInstruction>, LIROpcode.SecondOperandCanBeMemory {
    IADD, ISUB, IAND, IOR, IXOR,
    LADD, LSUB, LAND, LOR, LXOR,
    FADD, FSUB, FMUL, FDIV,
    DADD, DSUB, DMUL, DDIV;

    public LIRInstruction create(CiVariable leftAndResult, CiValue right) {
        assert (name().startsWith("I") && leftAndResult.kind == CiKind.Int && right.kind == CiKind.Int)
            || (name().startsWith("L") && leftAndResult.kind == CiKind.Long && right.kind == CiKind.Long)
            || (name().startsWith("F") && leftAndResult.kind == CiKind.Float && right.kind == CiKind.Float)
            || (name().startsWith("D") && leftAndResult.kind == CiKind.Double && right.kind == CiKind.Double);

        return new LIRInstruction(this, leftAndResult, null, leftAndResult, right);
    }

    @Override
    public void emitCode(AMD64LIRAssembler lasm, LIRInstruction op) {
        assert op.info == null;
        assert op.operand(0).kind == op.operand(1).kind || (op.operand(0).kind == lasm.target.wordKind && op.operand(1).kind == CiKind.Int);
        assert op.operand(0).equals(op.result());


        CiRegister dst = op.result().asRegister();
        CiValue right = op.operand(1);

        AMD64MacroAssembler masm = lasm.masm;
        if (right.isRegister()) {
            CiRegister rreg = right.asRegister();
            switch (this) {
                case IADD: masm.addl(dst,  rreg); break;
                case ISUB: masm.subl(dst,  rreg); break;
                case IAND: masm.andl(dst,  rreg); break;
                case IOR:  masm.orl(dst,   rreg); break;
                case IXOR: masm.xorl(dst,  rreg); break;
                case LADD: masm.addq(dst,  rreg); break;
                case LSUB: masm.subq(dst,  rreg); break;
                case LAND: masm.andq(dst,  rreg); break;
                case LOR:  masm.orq(dst,   rreg); break;
                case LXOR: masm.xorq(dst,  rreg); break;
                case FADD: masm.addss(dst, rreg); break;
                case FSUB: masm.subss(dst, rreg); break;
                case FMUL: masm.mulss(dst, rreg); break;
                case FDIV: masm.divss(dst, rreg); break;
                case DADD: masm.addsd(dst, rreg); break;
                case DSUB: masm.subsd(dst, rreg); break;
                case DMUL: masm.mulsd(dst, rreg); break;
                case DDIV: masm.divsd(dst, rreg); break;
                default:   throw Util.shouldNotReachHere();
            }
        } else if (right.isConstant()) {
            switch (this) {
                case IADD: masm.incrementl(dst, lasm.asIntConst(right)); break;
                case ISUB: masm.decrementl(dst, lasm.asIntConst(right)); break;
                case IAND: masm.andl(dst,  lasm.asIntConst(right)); break;
                case IOR:  masm.orl(dst,   lasm.asIntConst(right)); break;
                case IXOR: masm.xorl(dst,  lasm.asIntConst(right)); break;
                case LADD: masm.addq(dst,  lasm.asLongConst(right)); break;
                case LSUB: masm.subq(dst,  lasm.asLongConst(right)); break;
                case LAND: masm.andq(dst,  lasm.asLongConst(right)); break;
                case LOR:  masm.orq(dst,   lasm.asLongConst(right)); break;
// TODO: need to implement this instruction in Assembler
//                case LXOR: masm.xorq(dst,  lasm.asLongConst(right)); break;
                case FADD: masm.addss(dst, lasm.asFloatConst(right)); break;
                case FSUB: masm.subss(dst, lasm.asFloatConst(right)); break;
                case FMUL: masm.mulss(dst, lasm.asFloatConst(right)); break;
                case FDIV: masm.divss(dst, lasm.asFloatConst(right)); break;
                case DADD: masm.addsd(dst, lasm.asDoubleConst(right)); break;
                case DSUB: masm.subsd(dst, lasm.asDoubleConst(right)); break;
                case DMUL: masm.mulsd(dst, lasm.asDoubleConst(right)); break;
                case DDIV: masm.divsd(dst, lasm.asDoubleConst(right)); break;
                default:   throw Util.shouldNotReachHere();
            }
        } else {
            CiAddress raddr = lasm.asAddress(right);
            switch (this) {
                case IADD: masm.addl(dst,  raddr); break;
                case ISUB: masm.subl(dst,  raddr); break;
                case IAND: masm.andl(dst,  raddr); break;
                case IOR:  masm.orl(dst,   raddr); break;
                case IXOR: masm.xorl(dst,  raddr); break;
                case LADD: masm.addq(dst,  raddr); break;
                case LSUB: masm.subq(dst,  raddr); break;
                case LAND: masm.andq(dst,  raddr); break;
                case LOR:  masm.orq(dst,   raddr); break;
                case LXOR: masm.xorq(dst,  raddr); break;
                case FADD: masm.addss(dst, raddr); break;
                case FSUB: masm.subss(dst, raddr); break;
                case FMUL: masm.mulss(dst, raddr); break;
                case FDIV: masm.divss(dst, raddr); break;
                case DADD: masm.addsd(dst, raddr); break;
                case DSUB: masm.subsd(dst, raddr); break;
                case DMUL: masm.mulsd(dst, raddr); break;
                case DDIV: masm.divsd(dst, raddr); break;
                default:  throw Util.shouldNotReachHere();
            }
        }
    }
}

//public class AMD64ArithmeticOp {
//    private abstract static class DefaultOp2 implements LIROpcode<AMD64LIRAssembler, AMD64MacroAssembler, LIRInstruction> {
//        public LIRInstruction create(CiVariable leftAndResult, CiValue right) {
//            return new LIRInstruction(this, leftAndResult, null, leftAndResult, right);
//        }
//    }
//
//    public static LIROpcode IADD = new DefaultOp2() {
//        @Override
//        public void emitCode(AMD64LIRAssembler lasm, AMD64MacroAssembler masm, LIRInstruction op) {
//            if (op.operand(1).isRegister()) {
//                masm.addl(lasm.intRegOpd(op, 0), lasm.intRegOpd(op, 1));
//            } else if (op.operand(1).isConstant()) {
//                masm.incrementl(lasm.intRegOpd(op, 0), lasm.intConstOpd(op, 1));
//            } else {
//                masm.addl(lasm.intRegOpd(op, 0), lasm.intAddrOpd(op, 1));
//            }
//        }
//    };
//
//    public static LIROpcode LADD = new DefaultOp2() {
//        @Override
//        public void emitCode(AMD64LIRAssembler lasm, AMD64MacroAssembler masm, LIRInstruction op) {
//            if (op.operand(1).isRegister()) {
//                masm.addq(lasm.longRegOpd(op, 0), lasm.longRegOpd(op, 1));
//            } else if (op.operand(1).isConstant()) {
//                masm.addq(lasm.longRegOpd(op, 0), lasm.longConstOpd(op, 1));
//            } else {
//                masm.addq(lasm.longRegOpd(op, 0), lasm.longAddrOpd(op, 1));
//            }
//        }
//    };
//
//    public static LIROpcode FADD = new DefaultOp2() {
//        @Override
//        public void emitCode(AMD64LIRAssembler lasm, AMD64MacroAssembler masm, LIRInstruction op) {
//            if (op.operand(1).isRegister()) {
//                masm.addss(lasm.floatRegOpd(op, 0), lasm.floatRegOpd(op, 1));
//            } else if (op.operand(1).isConstant()) {
//                masm.addss(lasm.floatRegOpd(op, 0), lasm.floatConstOpd(op, 1));
//            } else {
//                masm.addss(lasm.floatRegOpd(op, 0), lasm.floatAddrOpd(op, 1));
//            }
//        }
//    };
//
//    public static LIROpcode DADD = new DefaultOp2() {
//        @Override
//        public void emitCode(AMD64LIRAssembler lasm, AMD64MacroAssembler masm, LIRInstruction op) {
//            if (op.operand(1).isRegister()) {
//                masm.addsd(lasm.floatRegOpd(op, 0), lasm.doubleRegOpd(op, 1));
//            } else if (op.operand(1).isConstant()) {
//                masm.addsd(lasm.floatRegOpd(op, 0), lasm.doubleConstOpd(op, 1));
//            } else {
//                masm.addsd(lasm.floatRegOpd(op, 0), lasm.doubleAddrOpd(op, 1));
//            }
//        }
//    };
//}
