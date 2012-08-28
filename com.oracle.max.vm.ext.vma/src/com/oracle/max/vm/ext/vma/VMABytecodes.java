/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.vm.ext.vma;

import com.sun.cri.bytecode.*;

/**
 * Bytecodes defined as enum with useful supporting methods for automatic generation.
 */
public enum VMABytecodes {

// START GENERATED CODE
// EDIT AND RUN VMABytecodesGenerator.main() TO MODIFY

    NOP(Bytecodes.NOP, "Bytecode"),
    ACONST_NULL(Bytecodes.ACONST_NULL, "ConstLoad"),
    ICONST_M1(Bytecodes.ICONST_M1, "ConstLoad"),
    ICONST_0(Bytecodes.ICONST_0, "ConstLoad"),
    ICONST_1(Bytecodes.ICONST_1, "ConstLoad"),
    ICONST_2(Bytecodes.ICONST_2, "ConstLoad"),
    ICONST_3(Bytecodes.ICONST_3, "ConstLoad"),
    ICONST_4(Bytecodes.ICONST_4, "ConstLoad"),
    ICONST_5(Bytecodes.ICONST_5, "ConstLoad"),
    LCONST_0(Bytecodes.LCONST_0, "ConstLoad"),
    LCONST_1(Bytecodes.LCONST_1, "ConstLoad"),
    FCONST_0(Bytecodes.FCONST_0, "ConstLoad"),
    FCONST_1(Bytecodes.FCONST_1, "ConstLoad"),
    FCONST_2(Bytecodes.FCONST_2, "ConstLoad"),
    DCONST_0(Bytecodes.DCONST_0, "ConstLoad"),
    DCONST_1(Bytecodes.DCONST_1, "ConstLoad"),
    BIPUSH(Bytecodes.BIPUSH, "ConstLoad"),
    SIPUSH(Bytecodes.SIPUSH, "ConstLoad"),
    LDC(Bytecodes.LDC, "ConstLoad"),
    LDC_W(Bytecodes.LDC_W, "ConstLoad"),
    LDC2_W(Bytecodes.LDC2_W, "ConstLoad"),
    ILOAD(Bytecodes.ILOAD, "Load"),
    LLOAD(Bytecodes.LLOAD, "Load"),
    FLOAD(Bytecodes.FLOAD, "Load"),
    DLOAD(Bytecodes.DLOAD, "Load"),
    ALOAD(Bytecodes.ALOAD, "Load"),
    ILOAD_0(Bytecodes.ILOAD_0, "Load"),
    ILOAD_1(Bytecodes.ILOAD_1, "Load"),
    ILOAD_2(Bytecodes.ILOAD_2, "Load"),
    ILOAD_3(Bytecodes.ILOAD_3, "Load"),
    LLOAD_0(Bytecodes.LLOAD_0, "Load"),
    LLOAD_1(Bytecodes.LLOAD_1, "Load"),
    LLOAD_2(Bytecodes.LLOAD_2, "Load"),
    LLOAD_3(Bytecodes.LLOAD_3, "Load"),
    FLOAD_0(Bytecodes.FLOAD_0, "Load"),
    FLOAD_1(Bytecodes.FLOAD_1, "Load"),
    FLOAD_2(Bytecodes.FLOAD_2, "Load"),
    FLOAD_3(Bytecodes.FLOAD_3, "Load"),
    DLOAD_0(Bytecodes.DLOAD_0, "Load"),
    DLOAD_1(Bytecodes.DLOAD_1, "Load"),
    DLOAD_2(Bytecodes.DLOAD_2, "Load"),
    DLOAD_3(Bytecodes.DLOAD_3, "Load"),
    ALOAD_0(Bytecodes.ALOAD_0, "Load"),
    ALOAD_1(Bytecodes.ALOAD_1, "Load"),
    ALOAD_2(Bytecodes.ALOAD_2, "Load"),
    ALOAD_3(Bytecodes.ALOAD_3, "Load"),
    IALOAD(Bytecodes.IALOAD, "ArrayLoad"),
    LALOAD(Bytecodes.LALOAD, "ArrayLoad"),
    FALOAD(Bytecodes.FALOAD, "ArrayLoad"),
    DALOAD(Bytecodes.DALOAD, "ArrayLoad"),
    AALOAD(Bytecodes.AALOAD, "ArrayLoad"),
    BALOAD(Bytecodes.BALOAD, "ArrayLoad"),
    CALOAD(Bytecodes.CALOAD, "ArrayLoad"),
    SALOAD(Bytecodes.SALOAD, "ArrayLoad"),
    ISTORE(Bytecodes.ISTORE, "Store"),
    LSTORE(Bytecodes.LSTORE, "Store"),
    FSTORE(Bytecodes.FSTORE, "Store"),
    DSTORE(Bytecodes.DSTORE, "Store"),
    ASTORE(Bytecodes.ASTORE, "Store"),
    ISTORE_0(Bytecodes.ISTORE_0, "Store"),
    ISTORE_1(Bytecodes.ISTORE_1, "Store"),
    ISTORE_2(Bytecodes.ISTORE_2, "Store"),
    ISTORE_3(Bytecodes.ISTORE_3, "Store"),
    LSTORE_0(Bytecodes.LSTORE_0, "Store"),
    LSTORE_1(Bytecodes.LSTORE_1, "Store"),
    LSTORE_2(Bytecodes.LSTORE_2, "Store"),
    LSTORE_3(Bytecodes.LSTORE_3, "Store"),
    FSTORE_0(Bytecodes.FSTORE_0, "Store"),
    FSTORE_1(Bytecodes.FSTORE_1, "Store"),
    FSTORE_2(Bytecodes.FSTORE_2, "Store"),
    FSTORE_3(Bytecodes.FSTORE_3, "Store"),
    DSTORE_0(Bytecodes.DSTORE_0, "Store"),
    DSTORE_1(Bytecodes.DSTORE_1, "Store"),
    DSTORE_2(Bytecodes.DSTORE_2, "Store"),
    DSTORE_3(Bytecodes.DSTORE_3, "Store"),
    ASTORE_0(Bytecodes.ASTORE_0, "Store"),
    ASTORE_1(Bytecodes.ASTORE_1, "Store"),
    ASTORE_2(Bytecodes.ASTORE_2, "Store"),
    ASTORE_3(Bytecodes.ASTORE_3, "Store"),
    IASTORE(Bytecodes.IASTORE, "ArrayStore"),
    LASTORE(Bytecodes.LASTORE, "ArrayStore"),
    FASTORE(Bytecodes.FASTORE, "ArrayStore"),
    DASTORE(Bytecodes.DASTORE, "ArrayStore"),
    AASTORE(Bytecodes.AASTORE, "ArrayStore"),
    BASTORE(Bytecodes.BASTORE, "ArrayStore"),
    CASTORE(Bytecodes.CASTORE, "ArrayStore"),
    SASTORE(Bytecodes.SASTORE, "ArrayStore"),
    POP(Bytecodes.POP, "StackAdjust"),
    POP2(Bytecodes.POP2, "StackAdjust"),
    DUP(Bytecodes.DUP, "StackAdjust"),
    DUP_X1(Bytecodes.DUP_X1, "StackAdjust"),
    DUP_X2(Bytecodes.DUP_X2, "StackAdjust"),
    DUP2(Bytecodes.DUP2, "StackAdjust"),
    DUP2_X1(Bytecodes.DUP2_X1, "StackAdjust"),
    DUP2_X2(Bytecodes.DUP2_X2, "StackAdjust"),
    SWAP(Bytecodes.SWAP, "StackAdjust"),
    IADD(Bytecodes.IADD, "Operation"),
    LADD(Bytecodes.LADD, "Operation"),
    FADD(Bytecodes.FADD, "Operation"),
    DADD(Bytecodes.DADD, "Operation"),
    ISUB(Bytecodes.ISUB, "Operation"),
    LSUB(Bytecodes.LSUB, "Operation"),
    FSUB(Bytecodes.FSUB, "Operation"),
    DSUB(Bytecodes.DSUB, "Operation"),
    IMUL(Bytecodes.IMUL, "Operation"),
    LMUL(Bytecodes.LMUL, "Operation"),
    FMUL(Bytecodes.FMUL, "Operation"),
    DMUL(Bytecodes.DMUL, "Operation"),
    IDIV(Bytecodes.IDIV, "Operation"),
    LDIV(Bytecodes.LDIV, "Operation"),
    FDIV(Bytecodes.FDIV, "Operation"),
    DDIV(Bytecodes.DDIV, "Operation"),
    IREM(Bytecodes.IREM, "Operation"),
    LREM(Bytecodes.LREM, "Operation"),
    FREM(Bytecodes.FREM, "Operation"),
    DREM(Bytecodes.DREM, "Operation"),
    INEG(Bytecodes.INEG, "Operation"),
    LNEG(Bytecodes.LNEG, "Operation"),
    FNEG(Bytecodes.FNEG, "Operation"),
    DNEG(Bytecodes.DNEG, "Operation"),
    ISHL(Bytecodes.ISHL, "Operation"),
    LSHL(Bytecodes.LSHL, "Operation"),
    ISHR(Bytecodes.ISHR, "Operation"),
    LSHR(Bytecodes.LSHR, "Operation"),
    IUSHR(Bytecodes.IUSHR, "Operation"),
    LUSHR(Bytecodes.LUSHR, "Operation"),
    IAND(Bytecodes.IAND, "Operation"),
    LAND(Bytecodes.LAND, "Operation"),
    IOR(Bytecodes.IOR, "Operation"),
    LOR(Bytecodes.LOR, "Operation"),
    IXOR(Bytecodes.IXOR, "Operation"),
    LXOR(Bytecodes.LXOR, "Operation"),
    IINC(Bytecodes.IINC, "Operation"),
    I2L(Bytecodes.I2L, "Conversion"),
    I2F(Bytecodes.I2F, "Conversion"),
    I2D(Bytecodes.I2D, "Conversion"),
    L2I(Bytecodes.L2I, "Conversion"),
    L2F(Bytecodes.L2F, "Conversion"),
    L2D(Bytecodes.L2D, "Conversion"),
    F2I(Bytecodes.F2I, "Conversion"),
    F2L(Bytecodes.F2L, "Conversion"),
    F2D(Bytecodes.F2D, "Conversion"),
    D2I(Bytecodes.D2I, "Conversion"),
    D2L(Bytecodes.D2L, "Conversion"),
    D2F(Bytecodes.D2F, "Conversion"),
    I2B(Bytecodes.I2B, "Conversion"),
    I2C(Bytecodes.I2C, "Conversion"),
    I2S(Bytecodes.I2S, "Conversion"),
    LCMP(Bytecodes.LCMP, "Operation"),
    FCMPL(Bytecodes.FCMPL, "Operation"),
    FCMPG(Bytecodes.FCMPG, "Operation"),
    DCMPL(Bytecodes.DCMPL, "Operation"),
    DCMPG(Bytecodes.DCMPG, "Operation"),
    IFEQ(Bytecodes.IFEQ, "If"),
    IFNE(Bytecodes.IFNE, "If"),
    IFLT(Bytecodes.IFLT, "If"),
    IFGE(Bytecodes.IFGE, "If"),
    IFGT(Bytecodes.IFGT, "If"),
    IFLE(Bytecodes.IFLE, "If"),
    IF_ICMPEQ(Bytecodes.IF_ICMPEQ, "If"),
    IF_ICMPNE(Bytecodes.IF_ICMPNE, "If"),
    IF_ICMPLT(Bytecodes.IF_ICMPLT, "If"),
    IF_ICMPGE(Bytecodes.IF_ICMPGE, "If"),
    IF_ICMPGT(Bytecodes.IF_ICMPGT, "If"),
    IF_ICMPLE(Bytecodes.IF_ICMPLE, "If"),
    IF_ACMPEQ(Bytecodes.IF_ACMPEQ, "If"),
    IF_ACMPNE(Bytecodes.IF_ACMPNE, "If"),
    GOTO(Bytecodes.GOTO, "Goto"),
    JSR(Bytecodes.JSR, "Bytecode"),
    RET(Bytecodes.RET, "Bytecode"),
    TABLESWITCH(Bytecodes.TABLESWITCH, "Bytecode"),
    LOOKUPSWITCH(Bytecodes.LOOKUPSWITCH, "Bytecode"),
    IRETURN(Bytecodes.IRETURN, "Return"),
    LRETURN(Bytecodes.LRETURN, "Return"),
    FRETURN(Bytecodes.FRETURN, "Return"),
    DRETURN(Bytecodes.DRETURN, "Return"),
    ARETURN(Bytecodes.ARETURN, "Return"),
    RETURN(Bytecodes.RETURN, "Return"),
    GETSTATIC(Bytecodes.GETSTATIC, "GetStatic"),
    PUTSTATIC(Bytecodes.PUTSTATIC, "PutStatic"),
    GETFIELD(Bytecodes.GETFIELD, "GetField"),
    PUTFIELD(Bytecodes.PUTFIELD, "PutField"),
    INVOKEVIRTUAL(Bytecodes.INVOKEVIRTUAL, "InvokeVirtual"),
    INVOKESPECIAL(Bytecodes.INVOKESPECIAL, "InvokeSpecial"),
    INVOKESTATIC(Bytecodes.INVOKESTATIC, "InvokeStatic"),
    INVOKEINTERFACE(Bytecodes.INVOKEINTERFACE, "InvokeInterface"),
    XXXUNUSEDXXX(Bytecodes.XXXUNUSEDXXX, "Bytecode"),
    NEW(Bytecodes.NEW, "New"),
    NEWARRAY(Bytecodes.NEWARRAY, "NewArray"),
    ANEWARRAY(Bytecodes.ANEWARRAY, "NewArray"),
    ARRAYLENGTH(Bytecodes.ARRAYLENGTH, "ArrayLength"),
    ATHROW(Bytecodes.ATHROW, "Throw"),
    CHECKCAST(Bytecodes.CHECKCAST, "CheckCast"),
    INSTANCEOF(Bytecodes.INSTANCEOF, "InstanceOf"),
    MONITORENTER(Bytecodes.MONITORENTER, "MonitorEnter"),
    MONITOREXIT(Bytecodes.MONITOREXIT, "MonitorExit"),
    WIDE(Bytecodes.WIDE, "Bytecode"),
    MULTIANEWARRAY(Bytecodes.MULTIANEWARRAY, "MultiNewArray"),
    IFNULL(Bytecodes.IFNULL, "If"),
    IFNONNULL(Bytecodes.IFNONNULL, "If"),
    GOTO_W(Bytecodes.GOTO_W, "Goto"),
    JSR_W(Bytecodes.JSR_W, "Bytecode"),
    BREAKPOINT(Bytecodes.BREAKPOINT, "Bytecode"),
    JNICALL(Bytecodes.JNICALL, "Bytecode"),
    ILLEGAL(Bytecodes.ILLEGAL, "Bytecode"),
    END(Bytecodes.END, "Bytecode"),
    LAST_JVM_OPCODE(Bytecodes.LAST_JVM_OPCODE, "Bytecode"),
    MENTRY(-1, "MethodEntry");
// END GENERATED CODE

    public final int code;
    public final String methodName;

    public static final VMABytecodes[] VALUES = values();

    private VMABytecodes(int code, String methodName) {
        this.code = code;
        this.methodName = methodName;
    }

    public static void main(String[] args) {
        for (VMABytecodes b : VMABytecodes.values()) {
            System.out.println(b + ", ord=" + b.ordinal() + ", code=" + b.code);
        }
    }

}
