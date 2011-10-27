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

import static com.sun.cri.ci.CiCallingConvention.Type.*;
import static com.sun.cri.ci.CiValue.*;

import java.util.*;

import com.oracle.max.asm.*;
import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.asm.target.amd64.AMD64Assembler.ConditionFlag;
import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.nodes.calc.*;
import com.sun.cri.ci.*;
import com.sun.cri.ci.CiTargetMethod.Mark;
import com.sun.cri.ri.*;
import com.sun.cri.xir.*;
import com.sun.cri.xir.CiXirAssembler.RuntimeCallInformation;
import com.sun.cri.xir.CiXirAssembler.XirInstruction;
import com.sun.cri.xir.CiXirAssembler.XirLabel;
import com.sun.cri.xir.CiXirAssembler.XirMark;

public enum AMD64XirOp implements StandardOp.XirOpcode<AMD64LIRAssembler, LIRXirInstruction> {
    XIR;

    public LIRInstruction create(XirSnippet snippet, CiValue[] operands, CiValue outputOperand, int tempInputCount, int tempCount, CiValue[] inputOperands, int[] operandIndices, int outputOperandIndex,
                        LIRDebugInfo info, LIRDebugInfo infoAfter, RiMethod method, List<CiValue> pointerSlots) {
        return new LIRXirInstruction(this, snippet, operands, outputOperand, tempInputCount, tempCount, inputOperands, operandIndices, outputOperandIndex, info, infoAfter, method, pointerSlots);
    }

    @Override
    public void emitCode(AMD64LIRAssembler lasm, LIRXirInstruction op) {
        XirSnippet snippet = op.snippet;
        Label endLabel = null;
        Label[] labels = new Label[snippet.template.labels.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = new Label();
            if (snippet.template.labels[i].name == XirLabel.TrueSuccessor) {
                if (op.trueSuccessor() == null) {
                    assert endLabel == null;
                    endLabel = new Label();
                    labels[i] = endLabel;
                } else {
                    labels[i] = op.trueSuccessor().label;
                }
            } else if (snippet.template.labels[i].name == XirLabel.FalseSuccessor) {
                if (op.falseSuccessor() == null) {
                    assert endLabel == null;
                    endLabel = new Label();
                    labels[i] = endLabel;
                } else {
                    labels[i] = op.falseSuccessor().label;
                }
            }
        }
        emitXirInstructions(lasm, op, snippet.template.fastPath, labels, op.getOperands(), snippet.marks);
        if (endLabel != null) {
            lasm.masm.bind(endLabel);
        }

        if (snippet.template.slowPath != null) {
            lasm.compilation.lir().slowPaths.add(new SlowPath(op, labels, snippet.marks));
        }
    }

    private static class SlowPath implements LIR.SlowPath {
        public final LIRXirInstruction instruction;
        public final Label[] labels;
        public final Map<XirMark, Mark> marks;

        public SlowPath(LIRXirInstruction instruction, Label[] labels, Map<XirMark, Mark> marks) {
            this.instruction = instruction;
            this.labels = labels;
            this.marks = marks;
        }

        public void emitCode(LIRAssembler lasm) {
            emitSlowPath((AMD64LIRAssembler) lasm, this);
        }
    }


    private static void emitSlowPath(AMD64LIRAssembler lasm, SlowPath sp) {
        int start = -1;
        if (GraalOptions.TraceAssembler) {
            TTY.println("Emitting slow path for XIR instruction " + sp.instruction.snippet.template.name);
            start = lasm.masm.codeBuffer.position();
        }
        emitXirInstructions(lasm, sp.instruction, sp.instruction.snippet.template.slowPath, sp.labels, sp.instruction.getOperands(), sp.marks);
        lasm.masm.nop();
        if (GraalOptions.TraceAssembler) {
            TTY.println("From " + start + " to " + lasm.masm.codeBuffer.position());
        }
    }

    protected static void emitXirInstructions(AMD64LIRAssembler lasm, LIRXirInstruction xir, XirInstruction[] instructions, Label[] labels, CiValue[] operands, Map<XirMark, Mark> marks) {
        LIRDebugInfo info = xir == null ? null : xir.info;
        LIRDebugInfo infoAfter = xir == null ? null : xir.infoAfter;

        for (XirInstruction inst : instructions) {
            switch (inst.op) {
                case Add:
                    emitXirViaLir(lasm, AMD64ArithmeticOp.IADD, AMD64ArithmeticOp.LADD, AMD64ArithmeticOp.FADD, AMD64ArithmeticOp.DADD, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case Sub:
                    emitXirViaLir(lasm, AMD64ArithmeticOp.ISUB, AMD64ArithmeticOp.LSUB, AMD64ArithmeticOp.FSUB, AMD64ArithmeticOp.DSUB, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case Div:
                    emitXirViaLir(lasm, AMD64DivOp.IDIV, AMD64DivOp.LDIV, AMD64ArithmeticOp.FDIV, AMD64ArithmeticOp.DDIV, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case Mul:
                    emitXirViaLir(lasm, AMD64MulOp.IMUL, AMD64MulOp.LMUL, AMD64ArithmeticOp.FMUL, AMD64ArithmeticOp.DMUL, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case Mod:
                    emitXirViaLir(lasm, AMD64DivOp.IREM, AMD64DivOp.LREM, null, null, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case Shl:
                    emitXirViaLir(lasm, AMD64ShiftOp.ISHL, AMD64ShiftOp.LSHL, null, null, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case Sar:
                    emitXirViaLir(lasm, AMD64ShiftOp.ISHR, AMD64ShiftOp.LSHR, null, null, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case Shr:
                    emitXirViaLir(lasm, AMD64ShiftOp.UISHR, AMD64ShiftOp.ULSHR, null, null, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case And:
                    emitXirViaLir(lasm, AMD64ArithmeticOp.IAND, AMD64ArithmeticOp.LAND, null, null, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case Or:
                    emitXirViaLir(lasm, AMD64ArithmeticOp.IOR, AMD64ArithmeticOp.LOR, null, null, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case Xor:
                    emitXirViaLir(lasm, AMD64ArithmeticOp.IXOR, AMD64ArithmeticOp.LXOR, null, null, operands[inst.x().index], operands[inst.y().index], operands[inst.result.index]);
                    break;

                case Mov: {
                    CiValue result = operands[inst.result.index];
                    CiValue source = operands[inst.x().index];
                    AMD64MoveOp.move(lasm, result, source);
                    break;
                }

                case PointerLoad: {
                    CiValue result = operands[inst.result.index];
                    CiValue pointer = operands[inst.x().index];
                    CiRegisterValue register = assureInRegister(lasm, pointer);

                    AMD64MoveOp.load(lasm, result, new CiAddress(inst.kind, register, 0), inst.kind, (Boolean) inst.extra ? info : null);
                    break;
                }

                case PointerStore: {
                    CiValue value = operands[inst.y().index];
                    CiValue pointer = operands[inst.x().index];
                    assert pointer.isVariableOrRegister();

                    AMD64MoveOp.store(lasm, new CiAddress(inst.kind, pointer, 0), value, inst.kind, (Boolean) inst.extra ? info : null);
                    break;
                }

                case PointerLoadDisp: {
                    CiXirAssembler.AddressAccessInformation addressInformation = (CiXirAssembler.AddressAccessInformation) inst.extra;
                    boolean canTrap = addressInformation.canTrap;

                    CiAddress.Scale scale = addressInformation.scale;
                    int displacement = addressInformation.disp;

                    CiValue result = operands[inst.result.index];
                    CiValue pointer = operands[inst.x().index];
                    CiValue index = operands[inst.y().index];

                    pointer = assureInRegister(lasm, pointer);
                    assert pointer.isVariableOrRegister();

                    CiAddress src;
                    if (index.isConstant()) {
                        assert index.kind == CiKind.Int;
                        CiConstant constantIndex = (CiConstant) index;
                        src = new CiAddress(inst.kind, pointer, constantIndex.asInt() * scale.value + displacement);
                    } else {
                        src = new CiAddress(inst.kind, pointer, index, scale, displacement);
                    }

                    AMD64MoveOp.load(lasm, result, src, inst.kind, canTrap ? info : null);
                    break;
                }

                case LoadEffectiveAddress: {
                    CiXirAssembler.AddressAccessInformation addressInformation = (CiXirAssembler.AddressAccessInformation) inst.extra;

                    CiAddress.Scale scale = addressInformation.scale;
                    int displacement = addressInformation.disp;

                    CiValue result = operands[inst.result.index];
                    CiValue pointer = operands[inst.x().index];
                    CiValue index = operands[inst.y().index];

                    pointer = assureInRegister(lasm, pointer);
                    assert pointer.isVariableOrRegister();
                    CiAddress src = new CiAddress(CiKind.Illegal, pointer, index, scale, displacement);
                    lasm.masm.leaq(result.asRegister(), src);
                    break;
                }

                case PointerStoreDisp: {
                    CiXirAssembler.AddressAccessInformation addressInformation = (CiXirAssembler.AddressAccessInformation) inst.extra;
                    boolean canTrap = addressInformation.canTrap;

                    CiAddress.Scale scale = addressInformation.scale;
                    int displacement = addressInformation.disp;

                    CiValue value = operands[inst.z().index];
                    CiValue pointer = operands[inst.x().index];
                    CiValue index = operands[inst.y().index];

                    pointer = assureInRegister(lasm, pointer);
                    assert pointer.isVariableOrRegister();

                    CiAddress dst;
                    if (index.isConstant()) {
                        assert index.kind == CiKind.Int;
                        CiConstant constantIndex = (CiConstant) index;
                        dst = new CiAddress(inst.kind, pointer, IllegalValue, scale, constantIndex.asInt() * scale.value + displacement);
                    } else {
                        dst = new CiAddress(inst.kind, pointer, index, scale, displacement);
                    }

                    AMD64MoveOp.store(lasm, dst, value, inst.kind, canTrap ? info : null);
                    break;
                }

                case RepeatMoveBytes:
                    assert operands[inst.x().index].asRegister().equals(AMD64.rsi) : "wrong input x: " + operands[inst.x().index];
                    assert operands[inst.y().index].asRegister().equals(AMD64.rdi) : "wrong input y: " + operands[inst.y().index];
                    assert operands[inst.z().index].asRegister().equals(AMD64.rcx) : "wrong input z: " + operands[inst.z().index];
                    lasm.masm.repeatMoveBytes();
                    break;

                case RepeatMoveWords:
                    assert operands[inst.x().index].asRegister().equals(AMD64.rsi) : "wrong input x: " + operands[inst.x().index];
                    assert operands[inst.y().index].asRegister().equals(AMD64.rdi) : "wrong input y: " + operands[inst.y().index];
                    assert operands[inst.z().index].asRegister().equals(AMD64.rcx) : "wrong input z: " + operands[inst.z().index];
                    lasm.masm.repeatMoveWords();
                    break;

                case PointerCAS:
                    assert operands[inst.x().index].asRegister().equals(AMD64.rax) : "wrong input x: " + operands[inst.x().index];

                    CiValue exchangedVal = operands[inst.y().index];
                    CiValue exchangedAddress = operands[inst.x().index];
                    CiRegisterValue pointerRegister = assureInRegister(lasm, exchangedAddress);
                    CiAddress addr = new CiAddress(lasm.target.wordKind, pointerRegister);

                    if ((Boolean) inst.extra && info != null) {
                        lasm.tasm.recordImplicitException(lasm.masm.codeBuffer.position(), info);
                    }
                    lasm.masm.cmpxchgq(exchangedVal.asRegister(), addr);

                    break;

                case CallStub: {
                    XirTemplate stubId = (XirTemplate) inst.extra;
                    CiValue result = CiValue.IllegalValue;
                    if (inst.result != null) {
                        result = operands[inst.result.index];
                    }
                    CiValue[] args = new CiValue[inst.arguments.length];
                    for (int i = 0; i < args.length; i++) {
                        args[i] = operands[inst.arguments[i].index];
                    }
                    AMD64CallOp.callStub(lasm, lasm.compilation.compiler.lookupStub(stubId), stubId.resultOperand.kind, info, result, args);
                    break;
                }
                case CallRuntime: {
                    CiKind[] signature = new CiKind[inst.arguments.length];
                    for (int i = 0; i < signature.length; i++) {
                        signature[i] = inst.arguments[i].kind;
                    }

                    CiCallingConvention cc = lasm.compilation.registerConfig.getCallingConvention(RuntimeCall, signature, lasm.target, false);
                    lasm.compilation.frameMap().adjustOutgoingStackSize(cc, RuntimeCall);
                    for (int i = 0; i < inst.arguments.length; i++) {
                        CiValue argumentLocation = cc.locations[i];
                        CiValue argumentSourceLocation = operands[inst.arguments[i].index];
                        if (argumentLocation != argumentSourceLocation) {
                            AMD64MoveOp.move(lasm, argumentLocation, argumentSourceLocation);
                        }
                    }

                    RuntimeCallInformation runtimeCallInformation = (RuntimeCallInformation) inst.extra;
                    AMD64CallOp.directCall(lasm, runtimeCallInformation.target, (runtimeCallInformation.useInfoAfter) ? infoAfter : info);

                    if (inst.result != null && inst.result.kind != CiKind.Illegal && inst.result.kind != CiKind.Void) {
                        CiRegister returnRegister = lasm.compilation.registerConfig.getReturnRegister(inst.result.kind);
                        CiValue resultLocation = returnRegister.asValue(inst.result.kind.stackKind());
                        AMD64MoveOp.move(lasm, operands[inst.result.index], resultLocation);
                    }
                    break;
                }
                case Jmp: {
                    if (inst.extra instanceof XirLabel) {
                        Label label = labels[((XirLabel) inst.extra).index];
                        lasm.masm.jmp(label);
                    } else {
                        AMD64CallOp.directJmp(lasm, inst.extra);
                    }
                    break;
                }
                case DecAndJumpNotZero: {
                    Label label = labels[((XirLabel) inst.extra).index];
                    CiValue value = operands[inst.x().index];
                    if (value.kind == CiKind.Long) {
                        lasm.masm.decq(value.asRegister());
                    } else {
                        assert value.kind == CiKind.Int;
                        lasm.masm.decl(value.asRegister());
                    }
                    lasm.masm.jcc(ConditionFlag.notZero, label);
                    break;
                }
                case Jeq: {
                    Label label = labels[((XirLabel) inst.extra).index];
                    emitXirCompare(lasm, inst, Condition.EQ, ConditionFlag.equal, operands, label);
                    break;
                }
                case Jneq: {
                    Label label = labels[((XirLabel) inst.extra).index];
                    emitXirCompare(lasm, inst, Condition.NE, ConditionFlag.notEqual, operands, label);
                    break;
                }

                case Jgt: {
                    Label label = labels[((XirLabel) inst.extra).index];
                    emitXirCompare(lasm, inst, Condition.GT, ConditionFlag.greater, operands, label);
                    break;
                }

                case Jgteq: {
                    Label label = labels[((XirLabel) inst.extra).index];
                    emitXirCompare(lasm, inst, Condition.GE, ConditionFlag.greaterEqual, operands, label);
                    break;
                }

                case Jugteq: {
                    Label label = labels[((XirLabel) inst.extra).index];
                    emitXirCompare(lasm, inst, Condition.AE, ConditionFlag.aboveEqual, operands, label);
                    break;
                }

                case Jlt: {
                    Label label = labels[((XirLabel) inst.extra).index];
                    emitXirCompare(lasm, inst, Condition.LT, ConditionFlag.less, operands, label);
                    break;
                }

                case Jlteq: {
                    Label label = labels[((XirLabel) inst.extra).index];
                    emitXirCompare(lasm, inst, Condition.LE, ConditionFlag.lessEqual, operands, label);
                    break;
                }

                case Jbset: {
                    Label label = labels[((XirLabel) inst.extra).index];
                    CiValue pointer = operands[inst.x().index];
                    CiValue offset = operands[inst.y().index];
                    CiValue bit = operands[inst.z().index];
                    assert offset.isConstant() && bit.isConstant();
                    CiConstant constantOffset = (CiConstant) offset;
                    CiConstant constantBit = (CiConstant) bit;
                    CiAddress src = new CiAddress(inst.kind, pointer, constantOffset.asInt());
                    lasm.masm.btli(src, constantBit.asInt());
                    lasm.masm.jcc(ConditionFlag.aboveEqual, label);
                    break;
                }

                case Bind: {
                    XirLabel l = (XirLabel) inst.extra;
                    Label label = labels[l.index];
                    lasm.masm.bind(label);
                    break;
                }
                case Safepoint: {
                    assert info != null : "Must have debug info in order to create a safepoint.";
                    lasm.tasm.recordSafepoint(lasm.masm.codeBuffer.position(), info);
                    break;
                }
                case NullCheck: {
                    lasm.tasm.recordImplicitException(lasm.masm.codeBuffer.position(), info);
                    CiValue pointer = operands[inst.x().index];
                    lasm.masm.nullCheck(pointer.asRegister());
                    break;
                }
                case Align: {
                    lasm.masm.align((Integer) inst.extra);
                    break;
                }
                case StackOverflowCheck: {
                    int frameSize = lasm.frameMap.frameSize();
                    int lastFramePage = frameSize / lasm.target.pageSize;
                    // emit multiple stack bangs for methods with frames larger than a page
                    for (int i = 0; i <= lastFramePage; i++) {
                        int offset = (i + GraalOptions.StackShadowPages) * lasm.target.pageSize;
                        // Deduct 'frameSize' to handle frames larger than the shadow
                        bangStackWithOffset(lasm, offset - frameSize);
                    }
                    break;
                }
                case PushFrame: {
                    int frameSize = lasm.frameMap.frameSize();
                    lasm.masm.decrementq(AMD64.rsp, frameSize); // does not emit code for frameSize == 0
                    if (GraalOptions.ZapStackOnMethodEntry) {
                        final int intSize = 4;
                        for (int i = 0; i < frameSize / intSize; ++i) {
                            lasm.masm.movl(new CiAddress(CiKind.Int, AMD64.rsp.asValue(), i * intSize), 0xC1C1C1C1);
                        }
                    }
                    CiCalleeSaveLayout csl = lasm.compilation.registerConfig.getCalleeSaveLayout();
                    if (csl != null && csl.size != 0) {
                        int frameToCSA = lasm.frameMap.offsetToCalleeSaveAreaStart();
                        assert frameToCSA >= 0;
                        lasm.masm.save(csl, frameToCSA);
                    }
                    break;
                }
                case PopFrame: {
                    int frameSize = lasm.frameMap.frameSize();

                    CiCalleeSaveLayout csl = lasm.compilation.registerConfig.getCalleeSaveLayout();
                    if (csl != null && csl.size != 0) {
                        lasm.tasm.targetMethod.setRegisterRestoreEpilogueOffset(lasm.masm.codeBuffer.position());
                        // saved all registers, restore all registers
                        int frameToCSA = lasm.frameMap.offsetToCalleeSaveAreaStart();
                        lasm.masm.restore(csl, frameToCSA);
                    }

                    lasm.masm.incrementq(AMD64.rsp, frameSize);
                    break;
                }
                case Push: {
                    CiRegisterValue value = assureInRegister(lasm, operands[inst.x().index]);
                    lasm.masm.push(value.asRegister());
                    break;
                }
                case Pop: {
                    CiValue result = operands[inst.result.index];
                    if (result.isRegister()) {
                        lasm.masm.pop(result.asRegister());
                    } else {
                        CiRegister rscratch = lasm.compilation.registerConfig.getScratchRegister();
                        lasm.masm.pop(rscratch);
                        AMD64MoveOp.move(lasm, result, rscratch.asValue());
                    }
                    break;
                }
                case Mark: {
                    XirMark xmark = (XirMark) inst.extra;
                    Mark[] references = new Mark[xmark.references.length];
                    for (int i = 0; i < references.length; i++) {
                        references[i] = marks.get(xmark.references[i]);
                        assert references[i] != null;
                    }
                    Mark mark = lasm.tasm.recordMark(xmark.id, references);
                    marks.put(xmark, mark);
                    break;
                }
                case Nop: {
                    for (int i = 0; i < (Integer) inst.extra; i++) {
                        lasm.masm.nop();
                    }
                    break;
                }
                case RawBytes: {
                    for (byte b : (byte[]) inst.extra) {
                        lasm.masm.codeBuffer.emitByte(b & 0xff);
                    }
                    break;
                }
                case ShouldNotReachHere: {
                    AMD64CallOp.shouldNotReachHere(lasm);
                    break;
                }
                default:
                    throw Util.shouldNotReachHere("Unknown XIR operation " + inst.op);
            }
        }
    }

    private static void emitXirViaLir(AMD64LIRAssembler lasm, LIROpcode<AMD64LIRAssembler, LIRInstruction> intOp, LIROpcode<AMD64LIRAssembler, LIRInstruction> longOp,
                    LIROpcode<AMD64LIRAssembler, LIRInstruction> floatOp, LIROpcode<AMD64LIRAssembler, LIRInstruction> doubleOp, CiValue left, CiValue right, CiValue result) {

        LIROpcode<AMD64LIRAssembler, LIRInstruction> code;
        switch (result.kind) {
            case Int: code = intOp; break;
            case Long: code = longOp; break;
            case Float: code = floatOp; break;
            case Double: code = doubleOp; break;
            default: throw Util.shouldNotReachHere();
        }
        LIRInstruction op = new LIRInstruction(code, result, null, left, right);
        code.emitCode(lasm, op);
    }

    private static void emitXirCompare(AMD64LIRAssembler lasm, XirInstruction inst, Condition condition, ConditionFlag cflag, CiValue[] ops, Label label) {
        CiValue x = ops[inst.x().index];
        CiValue y = ops[inst.y().index];
        LIROpcode<AMD64LIRAssembler, LIRInstruction> code;
        switch (x.kind) {
            case Int: code = AMD64CompareOp.ICMP; break;
            case Long: code = AMD64CompareOp.LCMP; break;
            case Object: code = AMD64CompareOp.ACMP; break;
            case Float: code = AMD64CompareOp.FCMP; break;
            case Double: code = AMD64CompareOp.DCMP; break;
            default: throw Util.shouldNotReachHere();
        }
        LIRInstruction op = new LIRInstruction(code, CiValue.IllegalValue, null, x, y);
        code.emitCode(lasm, op);

        lasm.masm.jcc(cflag, label);
    }

    /**
     * @param offset the offset RSP at which to bang. Note that this offset is relative to RSP after RSP has been
     *            adjusted to allocated the frame for the method. It denotes an offset "down" the stack.
     *            For very large frames, this means that the offset may actually be negative (i.e. denoting
     *            a slot "up" the stack above RSP).
     */
    private static void bangStackWithOffset(AMD64LIRAssembler lasm, int offset) {
        lasm.masm.movq(new CiAddress(lasm.target.wordKind, AMD64.RSP, -offset), AMD64.rax);
    }

    private static CiRegisterValue assureInRegister(AMD64LIRAssembler lasm, CiValue pointer) {
        if (pointer.isConstant()) {
            CiRegisterValue register = lasm.compilation.registerConfig.getScratchRegister().asValue(pointer.kind);
            AMD64MoveOp.move(lasm, register, pointer);
            return register;
        }

        assert pointer.isRegister() : "should be register, but is: " + pointer;
        return (CiRegisterValue) pointer;
    }
}
