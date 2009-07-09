/*
 * Copyright (c) 2009 Sun Microsystems, Inc.  All rights reserved.
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
package com.sun.c1x.lir;

import java.util.*;

import com.sun.c1x.*;
import com.sun.c1x.asm.*;
import com.sun.c1x.ci.*;
import com.sun.c1x.ir.*;
import com.sun.c1x.util.*;
import com.sun.c1x.stub.*;
import com.sun.c1x.value.*;

/**
 *
 * @author Marcelo Cintra
 * @author Thomas Wuerthinger
 *
 */
public class LIRList {

    private List<LIRInstruction> operations;
    private final C1XCompilation compilation;
    private final BlockBegin block;

    public LIRList(C1XCompilation compilation, BlockBegin block) {
        this.compilation = compilation;
        this.block = block;
        this.operations = new ArrayList<LIRInstruction>(8);
    }

    private void append(LIRInstruction op) {
        if (op.source() == null) {
            op.setSource(compilation.currentInstruction());
        }

        if (C1XOptions.PrintIRWithLIR) {
            compilation.maybePrintCurrentInstruction();
            op.printOn(TTY.out);
            TTY.println();
        }

        operations.add(op);
        assert verifyInstruction(op);
    }

    private boolean verifyInstruction(LIRInstruction op) {
        op.verify();
        return true;
    }

    public List<LIRInstruction> instructionsList() {
        return operations;
    }

    public int length() {
        return operations.size();
    }

    public LIRInstruction at(int i) {
        return operations.get(i);
    }

    public BlockBegin block() {
        return block;
    }

    public void callOptVirtual(CiMethod method, LIROperand receiver, LIROperand result, Address dest, List<LIROperand> arguments, CodeEmitInfo info) {
        append(new LIRJavaCall(LIROpcode.VirtualCall, method, receiver, result, dest, arguments, info));
    }

    public void callStatic(CiMethod method, LIROperand result, Address dest, List<LIROperand> arguments, CodeEmitInfo info) {
        append(new LIRJavaCall(LIROpcode.StaticCall, method, LIROperandFactory.illegalOperand, result, dest, arguments, info));
    }

    public void callIcvirtual(CiMethod method, LIROperand receiver, LIROperand result, Address dest, List<LIROperand> arguments, CodeEmitInfo info) {
        append(new LIRJavaCall(LIROpcode.IcVirtualCall, method, receiver, result, dest, arguments, info));
    }

    public void callVirtual(CiMethod method, LIROperand receiver, LIROperand result, int vtableOffset, List<LIROperand> arguments, CodeEmitInfo info) {
        append(new LIRJavaCall(LIROpcode.VirtualCall, method, receiver, result, new Address(vtableOffset), arguments, info));
    }

    public void getThread(LIROperand result) {
        append(new LIROp0(LIROpcode.GetThread, result));
    }

    public void wordAlign() {
        append(new LIROp0(LIROpcode.WordAlign));
    }

    public void membar() {
        append(new LIROp0(LIROpcode.Membar));
    }

    public void membarAcquire() {
        append(new LIROp0(LIROpcode.MembarAcquire));
    }

    public void membarRelease() {
        append(new LIROp0(LIROpcode.MembarRelease));
    }

    public void nop() {
        append(new LIROp0(LIROpcode.Nop));
    }

    public void buildFrame() {
        append(new LIROp0(LIROpcode.BuildFrame));
    }

    public void stdEntry(LIROperand receiver) {
        append(new LIROp0(LIROpcode.StdEntry, receiver));
    }

    public void osrEntry(LIROperand osrPointer) {
        append(new LIROp0(LIROpcode.OsrEntry, osrPointer));
    }

    public void branchDestination(Label lbl) {
        append(new LIRLabel(lbl));
    }

    public void negate(LIROperand from, LIROperand to) {
        append(new LIROp1(LIROpcode.Neg, from, to));
    }

    public void leal(LIROperand from, LIROperand resultReg) {
        append(new LIROp1(LIROpcode.Leal, from, resultReg));
    }

    public void roundfp(LIROperand reg, LIROperand stackLocTemp, LIROperand result) {
        append(new LIRRoundFP(reg, stackLocTemp, result));
    }

    public void unalignedMove(LIRAddress src, LIROperand dst) {
        append(new LIROp1(LIROpcode.Move, LIROperandFactory.address(src), dst, dst.type(), LIRPatchCode.PatchNone, null, LIRInstruction.LIRMoveKind.Unaligned));
    }

    public void unalignedMove(LIROperand src, LIRAddress dst) {
        append(new LIROp1(LIROpcode.Move, src, LIROperandFactory.address(dst), src.type(), LIRPatchCode.PatchNone, null, LIRInstruction.LIRMoveKind.Unaligned));
    }

    public void unalignedMove(LIROperand src, LIROperand dst) {
        append(new LIROp1(LIROpcode.Move, src, dst, dst.type(), LIRPatchCode.PatchNone, null, LIRInstruction.LIRMoveKind.Unaligned));
    }

    public void move(LIROperand src, LIROperand dst, CodeEmitInfo info) {
        append(new LIROp1(LIROpcode.Move, src, dst, dst.type(), LIRPatchCode.PatchNone, info));
    }

    void move(LIRAddress src, LIROperand dst, CodeEmitInfo info) {
        append(new LIROp1(LIROpcode.Move, LIROperandFactory.address(src), dst, src.type(), LIRPatchCode.PatchNone, info));
    }

    void move(LIROperand src, LIRAddress dst, CodeEmitInfo info) {
        append(new LIROp1(LIROpcode.Move, src, LIROperandFactory.address(dst), dst.type(), LIRPatchCode.PatchNone, info));
    }

    public void move(LIROperand src, LIROperand dst) {
        move(src, dst, null);
    }

    void move(LIRAddress src, LIROperand dst) {
        move(src, dst, null);
    }

    void move(LIROperand src, LIRAddress dst) {
        move(src, dst, null);
    }

    public void volatileMove(LIROperand src, LIROperand dst, BasicType type, CodeEmitInfo info, LIRPatchCode patchCode) {
        append(new LIROp1(LIROpcode.Move, src, dst, type, patchCode, info, LIRInstruction.LIRMoveKind.Volatile));
    }

    public void volatileMove(LIROperand src, LIROperand dst, BasicType type) {
        volatileMove(src, dst, type, null, LIRPatchCode.PatchNone);

    }

    public void volatileMove(LIROperand src, LIROperand dst, BasicType type, CodeEmitInfo info) {
        volatileMove(src, dst, type, info, LIRPatchCode.PatchNone);

    }

    public void oop2reg(Object o, LIROperand reg) {
        append(new LIROp1(LIROpcode.Move, LIROperandFactory.oopConst(o), reg));
    }

    public void returnOp(LIROperand result) {
        append(new LIROp1(LIROpcode.Return, result));
    }

    public void safepoint(LIROperand tmp, CodeEmitInfo info) {
        append(new LIROp1(LIROpcode.Safepoint, tmp, info));
    }

    public void convert(int code, LIROperand left, LIROperand dst, ConversionStub stub) {
        append(new LIRConvert(code, left, dst, stub));
    }

    public void convert(int code, LIROperand left, LIROperand dst) {
        convert(code, left, dst, null);
    }

    public void logicalAnd(LIROperand left, LIROperand right, LIROperand dst) {
        append(new LIROp2(LIROpcode.LogicAnd, left, right, dst));
    }

    public void logicalOr(LIROperand left, LIROperand right, LIROperand dst) {
        append(new LIROp2(LIROpcode.LogicOr, left, right, dst));
    }

    public void logicalXor(LIROperand left, LIROperand right, LIROperand dst) {
        append(new LIROp2(LIROpcode.LogicXor, left, right, dst));
    }

    public void nullCheck(LIROperand opr, CodeEmitInfo info) {
        append(new LIROp1(LIROpcode.NullCheck, opr, info));
    }

    public void throwException(LIROperand exceptionPC, LIROperand exceptionOop, CodeEmitInfo info) {
        append(new LIROp2(LIROpcode.Throw, exceptionPC, exceptionOop, LIROperandFactory.illegalOperand, info));
    }

    public void unwindException(LIROperand exceptionPC, LIROperand exceptionOop, CodeEmitInfo info) {
        append(new LIROp2(LIROpcode.Unwind, exceptionPC, exceptionOop, LIROperandFactory.illegalOperand, info));
    }

    public void compareTo(LIROperand left, LIROperand right, LIROperand dst) {
        append(new LIROp2(LIROpcode.CompareTo, left, right, dst));
    }

    public void push(LIROperand opr) {
        append(new LIROp1(LIROpcode.Push, opr));
    }

    public void pop(LIROperand reg) {
        append(new LIROp1(LIROpcode.Pop, reg));
    }

    public void cmp(LIRCondition condition, LIROperand left, LIROperand right, CodeEmitInfo info) {
        append(new LIROp2(LIROpcode.Cmp, condition, left, right, info));
    }

    public void cmp(LIRCondition condition, LIROperand left, LIROperand right) {
        cmp(condition, left, right, null);
    }

    public void cmp(LIRCondition condition, LIROperand left, int right, CodeEmitInfo info) {
        cmp(condition, left, LIROperandFactory.intConst(right), info);
    }

    public void cmp(LIRCondition condition, LIROperand left, int right) {
        cmp(condition, left, right, null);
    }

    public void cmove(LIRCondition condition, LIROperand src1, LIROperand src2, LIROperand dst) {
        append(new LIROp2(LIROpcode.Cmove, condition, src1, src2, dst));
    }

    public void abs(LIROperand from, LIROperand to, LIROperand tmp) {
        append(new LIROp2(LIROpcode.Abs, from, tmp, to));
    }

    public void sqrt(LIROperand from, LIROperand to, LIROperand tmp) {
        append(new LIROp2(LIROpcode.Sqrt, from, tmp, to));
    }

    public void log(LIROperand from, LIROperand to, LIROperand tmp) {
        append(new LIROp2(LIROpcode.Log, from, tmp, to));
    }

    public void log10(LIROperand from, LIROperand to, LIROperand tmp) {
        append(new LIROp2(LIROpcode.Log10, from, tmp, to));
    }

    public void sin(LIROperand from, LIROperand to, LIROperand tmp1, LIROperand tmp2) {
        append(new LIROp2(LIROpcode.Sin, from, tmp1, to, tmp2));
    }

    public void cos(LIROperand from, LIROperand to, LIROperand tmp1, LIROperand tmp2) {
        append(new LIROp2(LIROpcode.Cos, from, tmp1, to, tmp2));
    }

    public void tan(LIROperand from, LIROperand to, LIROperand tmp1, LIROperand tmp2) {
        append(new LIROp2(LIROpcode.Tan, from, tmp1, to, tmp2));
    }

    public void add(LIROperand left, LIROperand right, LIROperand res) {
        append(new LIROp2(LIROpcode.Add, left, right, res));
    }

    public void sub(LIROperand left, LIROperand right, LIROperand res, CodeEmitInfo info) {
        append(new LIROp2(LIROpcode.Sub, left, right, res, info));
    }

    public void mul(LIROperand left, LIROperand right, LIROperand res) {
        append(new LIROp2(LIROpcode.Mul, left, right, res));
    }

    public void mulStrictfp(LIROperand left, LIROperand right, LIROperand res, LIROperand tmp) {
        append(new LIROp2(LIROpcode.MulStrictFp, left, right, res, tmp));
    }

    public void div(LIROperand left, LIROperand right, LIROperand res, CodeEmitInfo info) {
        append(new LIROp2(LIROpcode.Div, left, right, res, info));
    }

    public void divStrictfp(LIROperand left, LIROperand right, LIROperand res, LIROperand tmp) {
        append(new LIROp2(LIROpcode.DivStrictFp, left, right, res, tmp));
    }

    public void rem(LIROperand left, LIROperand right, LIROperand res, CodeEmitInfo info) {
        append(new LIROp2(LIROpcode.Rem, left, right, res, info));
    }

    public void jump(BlockBegin block) {
        append(new LIRBranch(LIRCondition.Always, BasicType.Illegal, block));
    }

    public void jump(CodeStub stub) {
        append(new LIRBranch(LIRCondition.Always, BasicType.Illegal, stub));
    }

    public void branch(LIRCondition cond, Label lbl) {
        append(new LIRBranch(cond, lbl));
    }

    public void branch(LIRCondition cond, BasicType type, BlockBegin block) {
        assert type != BasicType.Float && type != BasicType.Double : "no fp comparisons";
        append(new LIRBranch(cond, type, block));
    }

    public void branch(LIRCondition cond, BasicType type, CodeStub stub) {
        assert type != BasicType.Float && type != BasicType.Double : "no fp comparisons";
        append(new LIRBranch(cond, type, stub));
    }

    public void branch(LIRCondition cond, BasicType type, BlockBegin block, BlockBegin unordered) {
        assert type == BasicType.Float || type == BasicType.Double : "fp comparisons only";
        append(new LIRBranch(cond, type, block, unordered));
    }

    public void shiftLeft(LIROperand value, int count, LIROperand dst) {
        shiftLeft(value, LIROperandFactory.intConst(count), dst, LIROperandFactory.illegalOperand);
    }

    public void shiftRight(LIROperand value, int count, LIROperand dst) {
        shiftRight(value, LIROperandFactory.intConst(count), dst, LIROperandFactory.illegalOperand);
    }

    public void unsignedShiftRight(LIROperand value, int count, LIROperand dst) {
        unsignedShiftRight(value, LIROperandFactory.intConst(count), dst, LIROperandFactory.illegalOperand);
    }

    public void lcmp2int(LIROperand left, LIROperand right, LIROperand dst) {
        append(new LIROp2(LIROpcode.Cmpl2i, left, right, dst));
    }

    public void callRuntimeLeaf(Address routine, LIROperand tmp, LIROperand result, List<LIROperand> arguments) {
        append(new LIRRTCall(routine, tmp, result, arguments));
    }

    public void callRuntime(Address routine, LIROperand tmp, LIROperand result, List<LIROperand> arguments, CodeEmitInfo info) {
        append(new LIRRTCall(routine, tmp, result, arguments, info));
    }

    public void loadStackAddressMonitor(int monitorIx, LIROperand dst) {
        append(new LIROp1(LIROpcode.Monaddr, LIROperandFactory.intConst(monitorIx), dst));
    }

    public void set_24bitFpu() {
        append(new LIROp0(LIROpcode.Op24bitFPU));
    }

    public void restoreFpu() {
        append(new LIROp0(LIROpcode.ResetFPU));
    }

    public void breakpoint() {
        append(new LIROp0(LIROpcode.Breakpoint));
    }

    public void arraycopy(LIROperand src, LIROperand srcPos, LIROperand dst, LIROperand dstPos, LIROperand length, LIROperand tmp, CiType expectedType, int flags, CodeEmitInfo info) {
        append(new LIRArrayCopy(src, srcPos, dst, dstPos, length, tmp, expectedType, flags, info));
    }

    public void fpopRaw() {
        append(new LIROp0(LIROpcode.FpopRaw));
    }

    public void profileCall(CiMethod method, int bci, LIROperand mdo, LIROperand recv, LIROperand t1, CiType chaKlass) {
        append(new LIRProfileCall(LIROpcode.ProfileCall, method, bci, mdo, recv, t1, chaKlass));
    }

    public void oop2regPatch(Object o, LIROperand reg, CodeEmitInfo info) {
        append(new LIROp1(LIROpcode.Move, LIROperandFactory.oopConst(o), reg, BasicType.Object, LIRPatchCode.PatchNormal, info));
    }

    public void load(LIRAddress addr, LIROperand src, CodeEmitInfo info, LIRPatchCode patchCode) {
        append(new LIROp1(LIROpcode.Move, LIROperandFactory.address(addr), src, addr.type(), patchCode, info));
    }

    public void volatileLoadMemReg(LIRAddress address, LIROperand dst, CodeEmitInfo info, LIRPatchCode patchCode) {
        append(new LIROp1(LIROpcode.Move, LIROperandFactory.address(address), dst, address.type(), patchCode, info, LIRInstruction.LIRMoveKind.Volatile));
    }

    public void volatileLoadUnsafeReg(LIROperand base, LIROperand offset, LIROperand dst, BasicType type, CodeEmitInfo info, LIRPatchCode patchCode) {
        append(new LIROp1(LIROpcode.Move, LIROperandFactory.address(new LIRAddress(base, offset, type)), dst, type, patchCode, info, LIRInstruction.LIRMoveKind.Volatile));
    }

    public void prefetch(LIRAddress addr, boolean isStore) {
        append(new LIROp1(isStore ? LIROpcode.Prefetchw : LIROpcode.Prefetchr, LIROperandFactory.address(addr)));
    }

    public void storeMemInt(int v, LIROperand base, int offsetInBytes, BasicType type, CodeEmitInfo info, LIRPatchCode patchCode) {
        append(new LIROp1(LIROpcode.Move, LIROperandFactory.intConst(v), LIROperandFactory.address(new LIRAddress(base, offsetInBytes, type)), type, patchCode, info));
    }

    public void storeMemOop(Object o, LIROperand base, int offsetInBytes, BasicType type, CodeEmitInfo info, LIRPatchCode patchCode) {
        append(new LIROp1(LIROpcode.Move, LIROperandFactory.oopConst(o), LIROperandFactory.address(new LIRAddress(base, offsetInBytes, type)), type, patchCode, info));
    }

    public void store(LIROperand src, LIRAddress addr, CodeEmitInfo info, LIRPatchCode patchCode) {
        append(new LIROp1(LIROpcode.Move, src, LIROperandFactory.address(addr), addr.type(), patchCode, info));
    }

    public void volatileStoreMemReg(LIROperand src, LIRAddress addr, CodeEmitInfo info, LIRPatchCode patchCode) {
        append(new LIROp1(LIROpcode.Move, src, LIROperandFactory.address(addr), addr.type(), patchCode, info, LIRInstruction.LIRMoveKind.Volatile));
    }

    public void volatileStoreUnsafeReg(LIROperand src, LIROperand base, LIROperand offset, BasicType type, CodeEmitInfo info, LIRPatchCode patchCode) {
        append(new LIROp1(LIROpcode.Move, src, LIROperandFactory.address(new LIRAddress(base, offset, type)), type, patchCode, info, LIRInstruction.LIRMoveKind.Volatile));
    }

    public void idiv(LIROperand left, LIROperand right, LIROperand res, LIROperand tmp, CodeEmitInfo info) {
        append(new LIROp3(LIROpcode.Idiv, left, right, tmp, res, info));
    }

    public void idiv(LIROperand left, int right, LIROperand res, LIROperand tmp, CodeEmitInfo info) {
        append(new LIROp3(LIROpcode.Idiv, left, LIROperandFactory.intConst(right), tmp, res, info));
    }

    public void irem(LIROperand left, LIROperand right, LIROperand res, LIROperand tmp, CodeEmitInfo info) {
        append(new LIROp3(LIROpcode.Irem, left, right, tmp, res, info));
    }

    public void irem(LIROperand left, int right, LIROperand res, LIROperand tmp, CodeEmitInfo info) {
        append(new LIROp3(LIROpcode.Irem, left, LIROperandFactory.intConst(right), tmp, res, info));
    }

    public void cmpMemInt(LIRCondition condition, LIROperand base, int disp, int c, CodeEmitInfo info) {
        append(new LIROp2(LIROpcode.Cmp, condition, LIROperandFactory.address(new LIRAddress(base, disp, BasicType.Int)), LIROperandFactory.intConst(c), info));
    }

    public void cmpRegMem(LIRCondition condition, LIROperand reg, LIRAddress addr, CodeEmitInfo info) {
        append(new LIROp2(LIROpcode.Cmp, condition, reg, LIROperandFactory.address(addr), info));
    }

    public void allocateObject(LIROperand dst, LIROperand t1, LIROperand t2, LIROperand t3, LIROperand t4, int headerSize, int objectSize, LIROperand klass, boolean initCheck, CodeStub stub) {
        append(new LIRAllocObj(klass, dst, t1, t2, t3, t4, headerSize, objectSize, initCheck, stub));
    }

    public void allocateArray(LIROperand dst, LIROperand len, LIROperand t1, LIROperand t2, LIROperand t3, LIROperand t4, BasicType type, LIROperand klass, CodeStub stub) {
        append(new LIRAllocArray(klass, len, dst, t1, t2, t3, t4, type, stub));
    }

    public void shiftLeft(LIROperand value, LIROperand count, LIROperand dst, LIROperand tmp) {
        append(new LIROp2(LIROpcode.Shl, value, count, dst, tmp));
    }

    public void shiftRight(LIROperand value, LIROperand count, LIROperand dst, LIROperand tmp) {
        append(new LIROp2(LIROpcode.Shr, value, count, dst, tmp));
    }

    public void unsignedShiftRight(LIROperand value, LIROperand count, LIROperand dst, LIROperand tmp) {
        append(new LIROp2(LIROpcode.Ushr, value, count, dst, tmp));
    }

    public void fcmp2int(LIROperand left, LIROperand right, LIROperand dst, boolean isUnorderedLess) {
        append(new LIROp2(isUnorderedLess ? LIROpcode.Ucmpfd2i : LIROpcode.Cmpfd2i, left, right, dst));
    }

    public void lockObject(LIROperand hdr, LIROperand obj, LIROperand lock, LIROperand scratch, CodeStub stub, CodeEmitInfo info) {
        append(new LIRLock(LIROpcode.Lock, hdr, obj, lock, scratch, stub, info));
    }

    public void unlockObject(LIROperand hdr, LIROperand obj, LIROperand lock, CodeStub stub) {
        append(new LIRLock(LIROpcode.Unlock, hdr, obj, lock, LIROperandFactory.illegalOperand, stub, null));
    }

    public void check_LIR() {
        // cannot do the proper checking as PRODUCT and other modes return different results
        // guarantee(sizeof(LIR_OprDesc) == wordSize, "may not have a v-table");
    }

    public void checkcast(LIROperand result, LIROperand object, CiType klass, LIROperand tmp1, LIROperand tmp2, LIROperand tmp3, boolean fastCheck, CodeEmitInfo infoForException,
                    CodeEmitInfo infoForPatch, CodeStub stub, CiMethod profiledMethod, int profiledBci) {
        append(new LIRTypeCheck(LIROpcode.CheckCast, result, object, klass, tmp1, tmp2, tmp3, fastCheck, infoForException, infoForPatch, stub, profiledMethod, profiledBci));
    }

    public void genInstanceof(LIROperand result, LIROperand object, CiType klass, LIROperand tmp1, LIROperand tmp2, LIROperand tmp3, boolean fastCheck, CodeEmitInfo infoForPatch) {
        append(new LIRTypeCheck(LIROpcode.InstanceOf, result, object, klass, tmp1, tmp2, tmp3, fastCheck, null, infoForPatch, null, null, 0));
    }

    public void storeCheck(LIROperand object, LIROperand array, LIROperand tmp1, LIROperand tmp2, LIROperand tmp3, CodeEmitInfo infoForException) {
        append(new LIRTypeCheck(LIROpcode.StoreCheck, object, array, tmp1, tmp2, tmp3, infoForException, null, 0));
    }

    public void casLong(LIROperand addr, LIROperand cmpValue, LIROperand newValue, LIROperand t1, LIROperand t2) {
        // Compare and swap produces condition code "zero" if contentsOf(addr) == cmpValue,
        // implying successful swap of newValue into addr
        append(new LIRCompareAndSwap(LIROpcode.CasLong, addr, cmpValue, newValue, t1, t2));
    }

    public void casObj(LIROperand addr, LIROperand cmpValue, LIROperand newValue, LIROperand t1, LIROperand t2) {
        // Compare and swap produces condition code "zero" if contentsOf(addr) == cmpValue,
        // implying successful swap of newValue into addr
        append(new LIRCompareAndSwap(LIROpcode.CasObj, addr, cmpValue, newValue, t1, t2));
    }

    public void casInt(LIROperand addr, LIROperand cmpValue, LIROperand newValue, LIROperand t1, LIROperand t2) {
        // Compare and swap produces condition code "zero" if contentsOf(addr) == cmpValue,
        // implying successful swap of newValue into addr
        append(new LIRCompareAndSwap(LIROpcode.CasInt, addr, cmpValue, newValue, t1, t2));
    }

    public void setFileAndLine(String file, int line) {
        // TODO: implement
    }

    public void load(LIRAddress counter, LIROperand result) {
        load(counter, result, null);
    }

    public void store(LIROperand result, LIRAddress counter) {
        store(result, counter, null);
    }

    public void rem(LIROperand leftOp, LIROperand rightOp, LIROperand resultOp) {
        rem(leftOp, rightOp, resultOp, null);
    }

    public void div(LIROperand leftOp, LIROperand rightOp, LIROperand resultOp) {
        div(leftOp, rightOp, resultOp);
    }

    public void sub(LIROperand leftOp, LIROperand rightOp, LIROperand resultOp) {
        sub(leftOp, rightOp, resultOp, null);
    }

    public void store(LIROperand value, LIRAddress address, CodeEmitInfo info) {
        store(value, address, info, LIRPatchCode.PatchNone);
    }

    public void load(LIRAddress address, LIROperand result, CodeEmitInfo info) {
        load(address, result, info, LIRPatchCode.PatchNone);
    }

    public static void printLIR(List<BlockBegin> hir) {
        // TODO Auto-generated method stub

    }
}