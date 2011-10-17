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

import java.util.*;

import com.oracle.max.asm.*;
import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.asm.*;
import com.oracle.max.graal.compiler.gen.*;
import com.oracle.max.graal.compiler.lir.FrameMap.StackBlock;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.nodes.calc.*;
import com.sun.cri.ci.*;
import com.sun.cri.ci.CiTargetMethod.Mark;
import com.sun.cri.ri.*;
import com.sun.cri.xir.CiXirAssembler.XirMark;

/**
 * The {@code LIRAssembler} class definition.
 */
public abstract class LIRAssembler {

    public final GraalCompilation compilation;
    public final TargetMethodAssembler tasm;
    public final AbstractAssembler asm;
    public final FrameMap frameMap;
    public int registerRestoreEpilogueOffset = -1;

    protected final List<SlowPath> xirSlowPath;

    private int lastDecodeStart;

    protected static class SlowPath {
        public final LIRXirInstruction instruction;
        public final Label[] labels;
        public final Map<XirMark, Mark> marks;

        public SlowPath(LIRXirInstruction instruction, Label[] labels, Map<XirMark, Mark> marks) {
            this.instruction = instruction;
            this.labels = labels;
            this.marks = marks;
        }
    }

    public LIRAssembler(GraalCompilation compilation, TargetMethodAssembler tasm) {
        this.compilation = compilation;
        this.tasm = tasm;
        this.asm = tasm.asm;
        this.frameMap = compilation.frameMap();
        this.xirSlowPath = new ArrayList<SlowPath>();
    }

    protected RiMethod method() {
        return compilation.method;
    }

    protected void addSlowPath(SlowPath sp) {
        xirSlowPath.add(sp);
    }

    public void emitLocalStubs() {
        for (SlowPath sp : xirSlowPath) {
            emitSlowPath(sp);
        }

        // No more code may be emitted after this point
    }

    protected int codePos() {
        return asm.codeBuffer.position();
    }

    public abstract void emitTraps();

    public void emitCode(List<LIRBlock> hir) {
        if (GraalOptions.PrintLIR && !TTY.isSuppressed()) {
            LIRList.printLIR(hir);
        }

        for (LIRBlock b : hir) {
            emitBlock(b);
        }

        assert checkNoUnboundLabels();
    }

    void emitBlock(LIRBlock block) {

        if (block.align()) {
            emitAlignment();
        }

        block.setBlockEntryPco(codePos());

        if (GraalOptions.PrintLIRWithAssembly) {
            block.printWithoutPhis(TTY.out());
        }

        assert block.lir() != null : "must have LIR";
        if (GraalOptions.CommentedAssembly) {
            String st = String.format(" block B%d", block.blockID());
            tasm.blockComment(st);
        }

        emitLirList(block.lir());
    }

    void emitLirList(LIRList list) {
        doPeephole(list);

        for (LIRInstruction op : list.instructionsList()) {
            if (GraalOptions.CommentedAssembly) {
                // Only print out branches
                if (op.code == LIROpcode.Branch) {
                    tasm.blockComment(op.toStringWithIdPrefix());
                }
            }
            if (GraalOptions.PrintLIRWithAssembly && !TTY.isSuppressed()) {
                // print out the LIR operation followed by the resulting assembly
                TTY.println(op.toStringWithIdPrefix());
                TTY.println();
            }

            op.emitCode(this);

            if (GraalOptions.PrintLIRWithAssembly) {
                printAssembly(asm);
            }
        }
    }

    private void printAssembly(AbstractAssembler asm) {
        byte[] currentBytes = asm.codeBuffer.copyData(lastDecodeStart, asm.codeBuffer.position());
        if (currentBytes.length > 0) {
            String disasm = compilation.compiler.runtime.disassemble(currentBytes, lastDecodeStart);
            if (disasm.length() != 0) {
                TTY.println(disasm);
            } else {
                TTY.println("Code [+%d]: %d bytes", lastDecodeStart, currentBytes.length);
                Util.printBytes(lastDecodeStart, currentBytes, GraalOptions.PrintAssemblyBytesPerLine);
            }
        }
        lastDecodeStart = asm.codeBuffer.position();
    }

    boolean checkNoUnboundLabels() {
//        for (int i = 0; i < branchTargetBlocks.size() - 1; i++) {
//            if (!branchTargetBlocks.get(i).label().isBound()) {
//                TTY.println(String.format("label of block B%d is not bound", branchTargetBlocks.get(i).blockID()));
//                assert false : "unbound label";
//            }
//        }

        return true;
    }

    void emitCall(LIRCall op) {
        verifyOopMap(op.info);

        switch (op.code) {
            case DirectCall:
                emitCallAlignment(op.code);
                // fall through
                if (op.marks != null) {
                    op.marks.put(XirMark.CALLSITE, tasm.recordMark(null, new Mark[0]));
                }
                emitDirectCall(op.target, op.info);
                break;
            case IndirectCall:
                emitCallAlignment(op.code);
                if (op.marks != null) {
                    op.marks.put(XirMark.CALLSITE, tasm.recordMark(null, new Mark[0]));
                }
                emitIndirectCall(op.target, op.info, op.targetAddress());
                break;
            case NativeCall: {
                emitNativeCall((String) op.target, op.info, op.targetAddress());
                break;
            }
            default:
                throw Util.shouldNotReachHere();
        }
    }

    void emitOpLabel(LIRLabel op) {
        asm.bind(op.label());
    }

    void emitOp1(LIROp1 op) {
        switch (op.code) {
            case Move:
                assert !op.operand().isIllegal();
                if (op.moveKind() == LIROp1.LIRMoveKind.Volatile) {
                    emitVolatileMove(op.operand(), op.result(), op.kind, op.info);
                } else {
                    moveOp(op.operand(), op.result(), op.kind, op.info, op.moveKind() == LIROp1.LIRMoveKind.Unaligned);
                }
                break;
            case Prefetchr:
                emitReadPrefetch(op.operand());
                break;
            case Prefetchw:
                emitReadPrefetch(op.operand());
                break;
            case Return:
                emitReturn(op.operand());
                break;
            case Neg:
                emitNegate((LIRNegate) op);
                break;
            case Lea:
                emitLea(op.operand(), op.result());
                break;
            case NullCheck:
                emitNullCheck(op.operand(), op.info);
                break;
            case Lsb:
                emitSignificantBitOp(false,  op.operand(), op.result());
                break;
            case Msb:
                emitSignificantBitOp(true,  op.operand(), op.result());
                break;
            default:
                throw Util.shouldNotReachHere();
        }
    }

    public void emitOp0(LIROp0 op) {
        switch (op.code) {
            case Label:
                throw Util.shouldNotReachHere();
            case Breakpoint:
                emitBreakpoint();
                break;
            default:
                throw Util.shouldNotReachHere();
        }
    }

    protected void emitOp2(LIROp2 op) {
        switch (op.code) {
            case Cmp:
                emitCompare(op.condition(), op.operand1(), op.operand2(), op);
                break;

            case Cmpl2i:
            case Cmpfd2i:
            case Ucmpfd2i:
                emitCompare2Int(op.code, op.operand1(), op.operand2(), op.result(), op);
                break;

            case FCmove:
                emitConditionalMove(op.condition(), op.operand1(), op.operand2(), op.result(), true, false);
                break;
            case UFCmove:
                emitConditionalMove(op.condition(), op.operand1(), op.operand2(), op.result(), true, true);
                break;
            case Cmove:
                emitConditionalMove(op.condition(), op.operand1(), op.operand2(), op.result(), false, false);
                break;

            case Shl:
            case Shr:
            case Ushr:
                if (op.operand2().isConstant()) {
                    emitShiftOp(op.code, op.operand1(), ((CiConstant) op.operand2()).asInt(), op.result());
                } else {
                    emitShiftOp(op.code, op.operand1(), op.operand2(), op.result(), op.tmp());
                }
                break;

            case Add:
            case Sub:
            case Mul:
            case Div:
            case Rem:
                emitArithOp(op.code, op.operand1(), op.operand2(), op.result(), op.info);
                break;

            case Abs:
            case Sqrt:
            case Sin:
            case Tan:
            case Cos:
            case Log:
            case Log10:
                emitIntrinsicOp(op.code, op.operand1(), op.operand2(), op.result(), op);
                break;

            case LogicAnd:
            case LogicOr:
            case LogicXor:
                emitLogicOp(op.code, op.operand1(), op.operand2(), op.result());
                break;

            default:
                throw Util.shouldNotReachHere();
        }
    }

    public void moveOp(CiValue src, CiValue dest, CiKind kind, LIRDebugInfo info, boolean unaligned) {
        if (src.isRegister()) {
            if (dest.isRegister()) {
                assert info == null : "no patching and info allowed here";
                reg2reg(src, dest);
            } else if (dest.isStackSlot()) {
                assert info == null : "no patching and info allowed here";
                reg2stack(src, dest, kind);
            } else if (dest.isAddress()) {
                reg2mem(src, dest, kind, info, unaligned);
            } else {
                throw Util.shouldNotReachHere();
            }

        } else if (src.isStackSlot()) {
            assert info == null : "no patching and info allowed here";
            if (dest.isRegister()) {
                stack2reg(src, dest, kind);
            } else if (dest.isStackSlot()) {
                stack2stack(src, dest, kind);
            } else {
                throw Util.shouldNotReachHere();
            }

        } else if (src.isConstant()) {
            if (dest.isRegister()) {
                const2reg(src, dest, info); // patching is possible
            } else if (dest.isStackSlot()) {
                assert info == null : "no patching and info allowed here";
                const2stack(src, dest);
            } else if (dest.isAddress()) {
                const2mem(src, dest, kind, info);
            } else {
                throw Util.shouldNotReachHere();
            }

        } else if (src.isAddress()) {
            if (dest.isStackSlot()) {
                assert info == null && !unaligned;
                mem2stack(src, dest, kind);
            } else if (dest.isAddress()) {
                assert info == null && !unaligned;
                mem2mem(src, dest, kind);
            } else {
                mem2reg(src, dest, kind, info, unaligned);
            }

        } else {
            throw Util.shouldNotReachHere(src.toString() + ", dest=" + dest.toString() + ", " + kind);
        }
    }

    public void verifyOopMap(LIRDebugInfo info) {
        if (GraalOptions.VerifyPointerMaps) {
            // TODO: verify oops
            Util.shouldNotReachHere();
        }
    }

    protected final Object asCallTarget(Object o) {
        return compilation.compiler.runtime.asCallTarget(o);
    }

    protected abstract int initialFrameSizeInBytes();

    protected abstract void doPeephole(LIRList list);

    protected abstract void emitSlowPath(SlowPath sp);

    public abstract void emitDeoptizationStub(LIRGenerator.DeoptimizationStub stub);

    protected abstract void emitAlignment();

    protected abstract void emitBreakpoint();

    protected abstract void emitLea(CiValue src, CiValue dst);

    protected abstract void emitNullCheck(CiValue src, LIRDebugInfo info);

    protected abstract void emitNegate(LIRNegate negate);

    protected abstract void emitMonitorAddress(int monitor, CiValue dst);

    protected abstract void emitStackAllocate(StackBlock src, CiValue dst);

    protected abstract void emitReturn(CiValue inOpr);

    protected abstract void emitReadPrefetch(CiValue inOpr);

    protected abstract void emitVolatileMove(CiValue inOpr, CiValue result, CiKind kind, LIRDebugInfo info);

    protected abstract void emitLogicOp(LIROpcode code, CiValue inOpr1, CiValue inOpr2, CiValue dst);

    protected abstract void emitIntrinsicOp(LIROpcode code, CiValue inOpr1, CiValue inOpr2, CiValue dst, LIROp2 op);

    protected abstract void emitArithOp(LIROpcode code, CiValue inOpr1, CiValue inOpr2, CiValue dst, LIRDebugInfo info);

    protected abstract void emitShiftOp(LIROpcode code, CiValue inOpr1, CiValue inOpr2, CiValue dst, CiValue tmpOpr);

    protected abstract void emitShiftOp(LIROpcode code, CiValue inOpr1, int asJint, CiValue dst);

    protected abstract void emitSignificantBitOp(boolean most, CiValue inOpr1, CiValue dst);

    protected abstract void emitConditionalMove(Condition condition, CiValue inOpr1, CiValue inOpr2, CiValue dst, boolean mayBeUnordered, boolean unorderedcmovOpr1);

    protected abstract void emitCompare2Int(LIROpcode code, CiValue inOpr1, CiValue inOpr2, CiValue dst, LIROp2 op);

    protected abstract void emitCompare(Condition condition, CiValue inOpr1, CiValue inOpr2, LIROp2 op);

    protected abstract void emitBranch(LIRBranch branch);

    protected abstract void emitTableSwitch(LIRTableSwitch tableSwitch);

    protected abstract void emitConvert(LIRConvert convert);

    protected abstract void emitOp3(LIROp3 op3);

    protected abstract void emitCompareAndSwap(LIRCompareAndSwap compareAndSwap);

    protected abstract void emitXir(LIRXirInstruction xirInstruction);

    protected abstract void emitIndirectCall(Object target, LIRDebugInfo info, CiValue callAddress);

    protected abstract void emitDirectCall(Object target, LIRDebugInfo info);

    protected abstract void emitNativeCall(String symbol, LIRDebugInfo info, CiValue callAddress);

    protected abstract void emitCallAlignment(LIROpcode code);

    protected abstract void emitMemoryBarriers(int barriers);

    protected abstract void reg2stack(CiValue src, CiValue dest, CiKind kind);

    protected abstract void reg2mem(CiValue src, CiValue dest, CiKind kind, LIRDebugInfo info, boolean unaligned);

    protected abstract void mem2reg(CiValue src, CiValue dest, CiKind kind, LIRDebugInfo info, boolean unaligned);

    protected abstract void const2mem(CiValue src, CiValue dest, CiKind kind, LIRDebugInfo info);

    protected abstract void const2stack(CiValue src, CiValue dest);

    protected abstract void const2reg(CiValue src, CiValue dest, LIRDebugInfo info);

    protected abstract void mem2stack(CiValue src, CiValue dest, CiKind kind);

    protected abstract void mem2mem(CiValue src, CiValue dest, CiKind kind);

    protected abstract void stack2stack(CiValue src, CiValue dest, CiKind kind);

    protected abstract void stack2reg(CiValue src, CiValue dest, CiKind kind);

    protected abstract void reg2reg(CiValue src, CiValue dest);

    protected abstract boolean trueOnUnordered(Condition condition);

    protected abstract boolean falseOnUnordered(Condition condition);

    protected boolean mayBeTrueOnUnordered(Condition condition) {
        return trueOnUnordered(condition) || !falseOnUnordered(condition);
    }

    protected boolean mayBeFalseOnUnordered(Condition condition) {
        return falseOnUnordered(condition) || !trueOnUnordered(condition);
    }
}
