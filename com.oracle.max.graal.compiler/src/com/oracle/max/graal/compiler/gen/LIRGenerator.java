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
package com.oracle.max.graal.compiler.gen;

import static com.oracle.max.cri.intrinsics.MemoryBarriers.*;
import static com.sun.cri.bytecode.Bytecodes.*;
import static com.sun.cri.ci.CiCallingConvention.Type.*;
import static com.sun.cri.ci.CiValue.*;

import java.util.*;

import com.oracle.max.asm.*;
import com.oracle.max.cri.intrinsics.*;
import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.alloc.*;
import com.oracle.max.graal.compiler.alloc.OperandPool.VariableFlag;
import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.stub.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.DeoptimizeNode.DeoptAction;
import com.oracle.max.graal.nodes.FrameState.ValueProcedure;
import com.oracle.max.graal.nodes.PhiNode.PhiType;
import com.oracle.max.graal.nodes.calc.*;
import com.oracle.max.graal.nodes.extended.*;
import com.oracle.max.graal.nodes.java.*;
import com.oracle.max.graal.nodes.spi.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;
import com.sun.cri.ri.RiType.Representation;
import com.sun.cri.xir.CiXirAssembler.XirConstant;
import com.sun.cri.xir.CiXirAssembler.XirInstruction;
import com.sun.cri.xir.CiXirAssembler.XirOperand;
import com.sun.cri.xir.CiXirAssembler.XirParameter;
import com.sun.cri.xir.CiXirAssembler.XirRegister;
import com.sun.cri.xir.CiXirAssembler.XirTemp;
import com.sun.cri.xir.*;

/**
 * This class traverses the HIR instructions and generates LIR instructions from them.
 */
public abstract class LIRGenerator extends LIRGeneratorTool {

    /**
     * Helper class for inserting memory barriers as necessary to implement the Java Memory Model
     * with respect to volatile field accesses.
     *
     * @see MemoryBarriers
     */
    class VolatileMemoryAccess {
        /**
         * Inserts any necessary memory barriers before a volatile write as required by the JMM.
         */
        void preVolatileWrite() {
            int barriers = compilation.compiler.target.arch.requiredBarriers(JMM_PRE_VOLATILE_WRITE);
            if (compilation.compiler.target.isMP && barriers != 0) {
                lir.membar(barriers);
            }
        }

        /**
         * Inserts any necessary memory barriers after a volatile write as required by the JMM.
         */
        void postVolatileWrite() {
            int barriers = compilation.compiler.target.arch.requiredBarriers(JMM_POST_VOLATILE_WRITE);
            if (compilation.compiler.target.isMP && barriers != 0) {
                lir.membar(barriers);
            }
        }

        /**
         * Inserts any necessary memory barriers before a volatile read as required by the JMM.
         */
        void preVolatileRead() {
            int barriers = compilation.compiler.target.arch.requiredBarriers(JMM_PRE_VOLATILE_READ);
            if (compilation.compiler.target.isMP && barriers != 0) {
                lir.membar(barriers);
            }
        }

        /**
         * Inserts any necessary memory barriers after a volatile read as required by the JMM.
         */
        void postVolatileRead() {
            // Ensure field's data is loaded before any subsequent loads or stores.
            int barriers = compilation.compiler.target.arch.requiredBarriers(LOAD_LOAD | LOAD_STORE);
            if (compilation.compiler.target.isMP && barriers != 0) {
                lir.membar(barriers);
            }
        }
    }

    /**
     * Forces the result of a given instruction to be available in a given register,
     * inserting move instructions if necessary.
     *
     * @param instruction an instruction that produces a {@linkplain ValueNode#operand() result}
     * @param register the {@linkplain CiRegister} in which the result of {@code instruction} must be available
     * @return {@code register} as an operand
     */
    protected CiValue force(ValueNode instruction, CiRegister register) {
        return force(instruction, register.asValue(instruction.kind));
    }

    /**
     * Forces the result of a given instruction to be available in a given operand,
     * inserting move instructions if necessary.
     *
     * @param instruction an instruction that produces a {@linkplain ValueNode#operand() result}
     * @param operand the operand in which the result of {@code instruction} must be available
     * @return {@code operand}
     */
    protected CiValue force(ValueNode instruction, CiValue operand) {
        CiValue result = makeOperand(instruction);
        if (result != operand) {
            assert result.kind != CiKind.Illegal;
            if (!Util.archKindsEqual(result.kind, operand.kind)) {
                // moves between different types need an intervening spill slot
                CiValue tmp = forceToSpill(result, operand.kind, false);
                lir.move(tmp, operand);
            } else {
                lir.move(result, operand);
            }
        }
        return operand;
    }

    @Override
    public CiVariable load(ValueNode val) {
        CiValue result = makeOperand(val);
        if (!result.isVariable()) {
            CiVariable operand = newVariable(val.kind);
            lir.move(result, operand);
            return operand;
        }
        return (CiVariable) result;
    }

    public CiValue loadNonconstant(ValueNode val) {
        CiValue result = makeOperand(val);
        assert result.isVariable() || result.isConstant();
        return result;
    }

    // the range of values in a lookupswitch or tableswitch statement
    private static final class SwitchRange {
        final int lowKey;
        int highKey;
        final LIRBlock sux;

        SwitchRange(int lowKey, LIRBlock sux) {
            this.lowKey = lowKey;
            this.highKey = lowKey;
            this.sux = sux;
        }
    }

    protected final GraalContext context;
    protected final GraalCompilation compilation;
    protected final LIR ir;
    protected final XirSupport xirSupport;
    protected final RiXirGenerator xir;
    protected final boolean isTwoOperand;

    private LIRBlock currentBlock;

    public final OperandPool operands;

    private ValueNode currentInstruction;
    private ValueNode lastInstructionPrinted; // Debugging only

    protected LIRList lir;
    final VolatileMemoryAccess vma;
    private ArrayList<DeoptimizationStub> deoptimizationStubs;
    private FrameState lastState;

    public LIRGenerator(GraalCompilation compilation) {
        this.context = compilation.context;
        this.compilation = compilation;
        this.ir = compilation.lir();
        this.xir = compilation.compiler.xir;
        this.xirSupport = new XirSupport();
        this.isTwoOperand = compilation.compiler.target.arch.twoOperandMode();
        this.vma = new VolatileMemoryAccess();

        this.operands = new OperandPool(compilation.compiler.target);
    }

    @Override
    public CiTarget target() {
        return compilation.compiler.target;
    }

    public LIRList lir() {
        return lir;
    }

    public ArrayList<DeoptimizationStub> deoptimizationStubs() {
        return deoptimizationStubs;
    }

    private void addDeoptimizationStub(DeoptimizationStub stub) {
        if (deoptimizationStubs == null) {
            deoptimizationStubs = new ArrayList<LIRGenerator.DeoptimizationStub>();
        }
        deoptimizationStubs.add(stub);
    }

    public static class DeoptimizationStub {
        public final Label label = new Label();
        public final LIRDebugInfo info;
        public final DeoptAction action;
        public final Object deoptInfo;

        public DeoptimizationStub(DeoptAction action, FrameState state, Object deoptInfo) {
            this.action = action;
            this.info = new LIRDebugInfo(state);
            this.deoptInfo = deoptInfo;
        }
    }

    public void doBlock(LIRBlock block) {
        blockDoProlog(block);
        this.currentBlock = block;

        if (GraalOptions.TraceLIRGeneratorLevel >= 1) {
            TTY.println("BEGIN Generating LIR for block B" + block.blockID());
        }

        if (block == ir.startBlock()) {
            XirSnippet prologue = xir.genPrologue(null, compilation.method);
            if (prologue != null) {
                emitXir(prologue, null, null, null, false);
            }
            setOperandsForLocals();
        } else if (block.blockPredecessors().size() > 0) {
            FrameState fs = null;
            for (LIRBlock pred : block.blockPredecessors()) {
                if (fs == null) {
                    fs = pred.lastState();
                } else if (fs != pred.lastState()) {
                    fs = null;
                    break;
                }
            }
            if (GraalOptions.TraceLIRGeneratorLevel >= 2) {
                if (fs == null) {
                    TTY.println("STATE RESET");
                } else {
                    TTY.println("STATE CHANGE (singlePred)");
                    if (GraalOptions.TraceLIRGeneratorLevel >= 3) {
                        TTY.println(fs.toDetailedString());
                    }
                }
            }
            lastState = fs;
        }

        for (int i = 0; i < block.getInstructions().size(); ++i) {
            Node instr = block.getInstructions().get(i);

            if (GraalOptions.OptImplicitNullChecks) {
                Node nextInstr = null;
                if (i < block.getInstructions().size() - 1) {
                    nextInstr = block.getInstructions().get(i + 1);
                }

                if (instr instanceof GuardNode) {
                    GuardNode guardNode = (GuardNode) instr;
                    if (guardNode.condition() instanceof IsNonNullNode) {
                        IsNonNullNode isNonNullNode = (IsNonNullNode) guardNode.condition();
                        if (nextInstr instanceof AccessNode) {
                            AccessNode accessNode = (AccessNode) nextInstr;
                            if (isNonNullNode.object() == accessNode.object() && canBeNullCheck(accessNode.location())) {
                                //TTY.println("implicit null check");
                                accessNode.setNullCheck(true);
                                continue;
                            }
                        }
                    }
                }
            }
            if (GraalOptions.TraceLIRGeneratorLevel >= 3) {
                TTY.println("LIRGen for " + instr);
            }
            FrameState stateAfter = null;
            if (instr instanceof StateSplit) {
                stateAfter = ((StateSplit) instr).stateAfter();
            }
            if (instr != instr.graph().start()) {
                walkState(instr, stateAfter);
                doRoot((ValueNode) instr);
            }
            if (stateAfter != null) {
                lastState = stateAfter;
                if (instr == instr.graph().start()) {
                    checkOperands(lastState);
                }
                if (GraalOptions.TraceLIRGeneratorLevel >= 2) {
                    TTY.println("STATE CHANGE");
                    if (GraalOptions.TraceLIRGeneratorLevel >= 3) {
                        TTY.println(stateAfter.toDetailedString());
                    }
                }
            }
        }
        if (block.blockSuccessors().size() >= 1 && !block.endsWithJump()) {
            NodeSuccessorsIterable successors = block.lastInstruction().successors();
            assert successors.explicitCount() >= 1 : "should have at least one successor : " + block.lastInstruction();
            block.lir().jump(getLIRBlock((FixedNode) successors.first()));
        }

        if (GraalOptions.TraceLIRGeneratorLevel >= 1) {
            TTY.println("END Generating LIR for block B" + block.blockID());
        }

        block.setLastState(lastState);
        this.currentBlock = null;
        blockDoEpilog();
    }

    private boolean canBeNullCheck(LocationNode location) {
        // TODO: Make this part of CiTarget
        return !(location instanceof IndexedLocationNode) && location.displacement() < 4096;
    }

    @Override
    public void visitMerge(MergeNode x) {
        if (x.next() instanceof LoopBeginNode) {
            moveToPhi((LoopBeginNode) x.next(), x);
        }
    }

    @Override
    public void visitArrayLength(ArrayLengthNode x) {
        emitArrayLength(x);
    }

    public CiValue emitArrayLength(ArrayLengthNode x) {
        XirArgument array = toXirArgument(x.array());
        XirSnippet snippet = xir.genArrayLength(site(x), array);
        emitXir(snippet, x, stateFor(x), null, true);
        return x.operand();
    }

    private void setOperandsForLocals() {
        CiCallingConvention args = compilation.frameMap().incomingArguments();
        for (LocalNode local : compilation.graph.start().locals()) {
            int i = local.index();

            CiValue src = args.locations[i];
            assert src.isLegal() : "check";

            CiVariable dest = newVariable(src.kind.stackKind());
            lir.move(src, dest, src.kind, null);

            assert src.kind.stackKind() == local.kind.stackKind() : "local type check failed";
            setResult(local, dest);
        }
    }

    private boolean checkOperands(FrameState fs) {
        CiKind[] arguments = CiUtil.signatureToKinds(compilation.method);
        int slot = 0;
        for (CiKind kind : arguments) {
            LocalNode local = (LocalNode) fs.localAt(slot);
            assert local != null && local.kind == kind.stackKind() : "No valid local in framestate for slot #" + slot + " (" + local + ")";
            slot++;
            if (slot < fs.localsSize() && fs.localAt(slot) == null) {
                slot++;
            }
        }
        return true;
    }

    @Override
    public void visitCheckCast(CheckCastNode x) {
        XirArgument obj = toXirArgument(x.object());
        XirSnippet snippet = xir.genCheckCast(site(x), obj, toXirArgument(x.targetClassInstruction()), x.targetClass());
        emitXir(snippet, x, stateFor(x), null, true);
    }

    @Override
    public void visitMonitorEnter(MonitorEnterNode x) {
        XirArgument obj = toXirArgument(x.object());
        XirArgument lockAddress = toXirArgument(createMonitorAddress(x.monitorIndex()));
        XirSnippet snippet = xir.genMonitorEnter(site(x), obj, lockAddress);
        emitXir(snippet, x, stateFor(x), stateFor(x, x.stateAfter()), null, true, null);
    }

    @Override
    public void visitMonitorExit(MonitorExitNode x) {
        XirArgument obj = toXirArgument(x.object());
        XirArgument lockAddress = toXirArgument(createMonitorAddress(x.monitorIndex()));
        XirSnippet snippet = xir.genMonitorExit(site(x), obj, lockAddress);
        emitXir(snippet, x, stateFor(x), null, true);
    }

    @Override
    public void visitStoreIndexed(StoreIndexedNode x) {
        XirArgument array = toXirArgument(x.array());
        XirArgument index = toXirArgument(x.index());
        XirArgument value = toXirArgument(x.value());
        XirSnippet snippet = xir.genArrayStore(site(x), array, index, value, x.elementKind(), null);
        emitXir(snippet, x, stateFor(x), null, true);
    }

    @Override
    public void visitNewInstance(NewInstanceNode x) {
        XirSnippet snippet = xir.genNewInstance(site(x), x.instanceClass());
        emitXir(snippet, x, stateFor(x), null, true);
    }

    @Override
    public void visitNewTypeArray(NewTypeArrayNode x) {
        XirArgument length = toXirArgument(x.length());
        XirSnippet snippet = xir.genNewArray(site(x), length, x.elementType().kind(true), null, null);
        emitXir(snippet, x, stateFor(x), null, true);
    }

    @Override
    public void visitNewObjectArray(NewObjectArrayNode x) {
        XirArgument length = toXirArgument(x.length());
        XirSnippet snippet = xir.genNewArray(site(x), length, CiKind.Object, x.elementType(), x.exactType());
        emitXir(snippet, x, stateFor(x), null, true);
    }

    @Override
    public void visitNewMultiArray(NewMultiArrayNode x) {
        XirArgument[] dims = new XirArgument[x.dimensionCount()];

        for (int i = 0; i < dims.length; i++) {
            dims[i] = toXirArgument(x.dimension(i));
        }

        XirSnippet snippet = xir.genNewMultiArray(site(x), dims, x.elementType);
        emitXir(snippet, x, stateFor(x), null, true);
    }


    @Override
    public void visitGuardNode(GuardNode x) {
        emitGuardComp(x.condition());
    }


    @Override
    public void visitConstant(ConstantNode x) {
        if (!canInlineAsConstant(x)) {
            CiValue res = x.operand();
            if (!(res.isLegal())) {
                res = x.asConstant();
            }
            if (res.isConstant()) {
                CiVariable reg = createResultVariable(x);
                lir.move(res, reg);
            } else {
                setResult(x, (CiVariable) res);
            }
        }
    }

    @Override
    public void visitExceptionObject(ExceptionObjectNode x) {
        XirSnippet snippet = xir.genExceptionObject(site(x));
        LIRDebugInfo info = stateFor(x);
        emitXir(snippet, x, info, null, true);
    }

    @Override
    public void visitAnchor(AnchorNode x) {
        setNoResult(x);
    }

    @Override
    public void visitIf(IfNode x) {
        assert x.defaultSuccessor() == x.falseSuccessor() : "wrong destination";
        emitBooleanBranch(x.compare(), getLIRBlock(x.trueSuccessor()),  getLIRBlock(x.falseSuccessor()), null, null, null, null);
    }

    public void emitBranch(BooleanNode n, Condition cond, LIRBlock trueSuccessor, LIRBlock falseSucc) {
        if (n instanceof CompareNode) {
            CompareNode compare = (CompareNode) n;
            if (compare.x().kind.isFloat() || compare.x().kind.isDouble()) {
                LIRBlock unorderedSuccBlock = falseSucc;
                if (compare.unorderedIsTrue()) {
                    unorderedSuccBlock = trueSuccessor;
                }
                lir.branch(cond, trueSuccessor, unorderedSuccBlock);
                return;
            }
        }
        lir.branch(cond, trueSuccessor);
    }

    public void emitBooleanBranch(BooleanNode node, LIRBlock trueSuccessor, LIRBlock falseSuccessor, CiValue trueValue, CiValue falseValue, CiValue result, LIRDebugInfo info) {
        if (node instanceof NegateBooleanNode) {
            emitBooleanBranch(((NegateBooleanNode) node).value(), falseSuccessor, trueSuccessor, falseValue, trueValue, result, info);
        } else if (node instanceof IsNonNullNode) {
            emitIsNonNullBranch((IsNonNullNode) node, trueSuccessor, falseSuccessor, trueValue, falseValue, result);
        } else if (node instanceof CompareNode) {
            emitCompare((CompareNode) node, trueSuccessor, falseSuccessor, trueValue, falseValue, result);
        } else if (node instanceof InstanceOfNode) {
            emitInstanceOf((TypeCheckNode) node, trueSuccessor, falseSuccessor, trueValue, falseValue, result, info);
        } else if (node instanceof ConstantNode) {
            emitConstantBranch(((ConstantNode) node).asConstant().asBoolean(), trueSuccessor, falseSuccessor, trueValue, falseValue, result, info);
        } else {
            throw Util.unimplemented(node.toString());
        }
    }

    private void emitIsNonNullBranch(IsNonNullNode node, LIRBlock trueSuccessor, LIRBlock falseSuccessor, CiValue trueValue, CiValue falseValue, CiValue result) {
        Condition cond = Condition.NE;
        if (trueSuccessor == null && falseSuccessor != null) {
            cond = cond.negate();
            trueSuccessor = falseSuccessor;
            falseSuccessor = null;
        }

        LIRItem xitem = new LIRItem(node.object(), this);
        xitem.loadItem();

        lir.cmp(cond, xitem.result(), CiConstant.NULL_OBJECT);
        if (trueValue != null) {
            lir.cmove(cond, trueValue, falseValue, result);
        } else {
            lir.branch(cond, trueSuccessor);

            if (falseSuccessor != null) {
                lir.jump(falseSuccessor);
            }
        }
    }

    private void emitInstanceOf(TypeCheckNode x, LIRBlock trueSuccessor, LIRBlock falseSuccessor, CiValue trueValue, CiValue falseValue, CiValue result, LIRDebugInfo info) {
        if (result != null) {
            XirArgument obj = toXirArgument(x.object());
            assert trueValue instanceof CiConstant && trueValue.kind == CiKind.Int;
            assert falseValue instanceof CiConstant && falseValue.kind == CiKind.Int;
            XirSnippet snippet = xir.genMaterializeInstanceOf(site(x), obj, toXirArgument(x.targetClassInstruction()), toXirArgument(trueValue), toXirArgument(falseValue), x.targetClass());
            CiValue xirResult = emitXir(snippet, null, info, null, false);
            lir.move(xirResult, result);
        } else {
            XirArgument obj = toXirArgument(x.object());
            XirSnippet snippet = xir.genInstanceOf(site(x), obj, toXirArgument(x.targetClassInstruction()), x.targetClass());
            emitXir(snippet, x, info, null, false);
            LIRXirInstruction instr = (LIRXirInstruction) lir.instructionsList().get(lir.instructionsList().size() - 1);
            instr.setTrueSuccessor(trueSuccessor);
            instr.setFalseSuccessor(falseSuccessor);
        }
    }


    public void emitConstantBranch(boolean value, LIRBlock trueSuccessorBlock, LIRBlock falseSuccessorBlock, CiValue trueValue, CiValue falseValue, CiValue result, LIRDebugInfo info) {
        if (value) {
            emitConstantBranch(trueSuccessorBlock, trueValue, result, info);
        } else {
            emitConstantBranch(falseSuccessorBlock, falseValue, result, info);
        }
    }

    private void emitConstantBranch(LIRBlock block, CiValue value, CiValue result, LIRDebugInfo info) {
        if (result != null) {
            lir.move(value, result);
        } else {
            if (block != null) {
                lir.jump(block);
            }
        }
    }

    public void emitCompare(CompareNode compare, LIRBlock trueSuccessorBlock, LIRBlock falseSuccessorBlock, CiValue trueValue, CiValue falseValue, CiValue result) {
        CiKind kind = compare.x().kind;

        Condition cond = compare.condition();
        boolean unorderedIsTrue = compare.unorderedIsTrue();

        if (trueSuccessorBlock == null && falseSuccessorBlock != null) {
            cond = cond.negate();
            unorderedIsTrue = !unorderedIsTrue;
            trueSuccessorBlock = falseSuccessorBlock;
            falseSuccessorBlock = null;
        }

        LIRItem xitem = new LIRItem(compare.x(), this);
        LIRItem yitem = new LIRItem(compare.y(), this);
        LIRItem xin = xitem;
        LIRItem yin = yitem;

        if (kind.isFloat() || kind.isDouble()) {
            cond = floatingPointCondition(cond);
        }

        xin.loadItem();

        CiValue left = xin.result();
        CiValue right = yin.result();
        lir.cmp(cond, left, right);

        if (compare.x().kind.isFloat() || compare.x().kind.isDouble()) {
            LIRBlock unorderedSuccBlock = falseSuccessorBlock;
            if (unorderedIsTrue) {
                unorderedSuccBlock = trueSuccessorBlock;
            }

            if (result != null) {
                lir.fcmove(cond, trueValue, falseValue, result, !unorderedIsTrue);
            } else {
                lir.branch(cond, trueSuccessorBlock, unorderedSuccBlock);
            }
        } else {
            if (result != null) {
                lir.cmove(cond, trueValue, falseValue, result);
            } else {
                lir.branch(cond, trueSuccessorBlock);
            }
        }

        if (falseSuccessorBlock != null) {
            lir.jump(falseSuccessorBlock);
        }
    }

    protected FrameState stateBeforeCallReturn(AbstractCallNode call, int bci) {
        return call.stateAfter().duplicateModified(bci, call.stateAfter().rethrowException(), call.kind);
    }

    protected FrameState stateBeforeCallWithArguments(AbstractCallNode call, int bci) {
        return call.stateAfter().duplicateModified(bci, call.stateAfter().rethrowException(), call.kind, call.arguments().toArray(new ValueNode[0]));
    }

    @Override
    public void visitInvoke(InvokeNode x) {
        RiMethod target = x.target();
        LIRDebugInfo info = stateFor(x, stateBeforeCallWithArguments(x, x.bci));
        LIRDebugInfo info2 = stateFor(x, stateBeforeCallReturn(x, x.bci));
        if (x.exceptionEdge() != null) {
            info2.setExceptionEdge(getLIRBlock(x.exceptionEdge()));
        }

        XirSnippet snippet = null;

        int opcode = x.opcode();
        XirArgument receiver;
        switch (opcode) {
            case INVOKESTATIC:
                snippet = xir.genInvokeStatic(site(x), target);
                break;
            case INVOKESPECIAL:
                receiver = toXirArgument(x.receiver());
                snippet = xir.genInvokeSpecial(site(x), receiver, target);
                break;
            case INVOKEVIRTUAL:
                receiver = toXirArgument(x.receiver());
                snippet = xir.genInvokeVirtual(site(x), receiver, target);
                break;
            case INVOKEINTERFACE:
                receiver = toXirArgument(x.receiver());
                snippet = xir.genInvokeInterface(site(x), receiver, target);
                break;
        }

        CiValue resultOperand = resultOperandFor(x.kind);
        CiCallingConvention cc = compilation.frameMap().getCallingConvention(getSignature(x), JavaCall);
        List<CiValue> pointerSlots = new ArrayList<CiValue>(2);
        List<CiValue> argList = visitInvokeArguments(cc, x.arguments(), pointerSlots);

        // emitting the template earlier can ease pressure on register allocation, but the argument loading can destroy an
        // implicit calling convention between the XirSnippet and the call.
        CiValue destinationAddress = emitXir(snippet, x, info.copy(), null, x.target(), false, pointerSlots);

        // emit direct or indirect call to the destination address
        if (destinationAddress instanceof CiConstant) {
            // Direct call
            assert ((CiConstant) destinationAddress).isDefaultValue() : "destination address should be zero";
            lir.callDirect(target, resultOperand, argList, info2, snippet.marks, pointerSlots);
        } else {
            // Indirect call
            argList.add(destinationAddress);
            lir.callIndirect(target, resultOperand, argList, info2, snippet.marks, pointerSlots);
        }

        if (resultOperand.isLegal()) {
            CiValue result = createResultVariable(x);
            lir.move(resultOperand, result);
        }
    }

    private CiKind[] getSignature(InvokeNode x) {
        CiKind receiver = x.isStatic() ? null : x.target.holder().kind(true);
        return CiUtil.signatureToKinds(x.target.signature(), receiver);
    }

    public CiValue createMonitorAddress(int monitorIndex) {
        CiValue result = newVariable(target().wordKind);
        lir.monitorAddress(monitorIndex, result);
        return result;
    }

    /**
     * For note on volatile fields, see {@link #visitStoreField(StoreField)}.
     */
    @Override
    public void visitLoadField(LoadFieldNode x) {
        RiField field = x.field();
        LIRDebugInfo info = stateFor(x);
        XirArgument receiver = toXirArgument(x.object());
        XirSnippet snippet = x.isStatic() ? xir.genGetStatic(site(x), receiver, field) : xir.genGetField(site(x), receiver, field);
        emitXir(snippet, x, info, null, true);

        if (x.isVolatile()) {
            vma.postVolatileRead();
        }
    }

    @Override
    public void visitLoadIndexed(LoadIndexedNode x) {
        XirArgument array = toXirArgument(x.array());
        XirArgument index = toXirArgument(x.index());
        XirSnippet snippet = xir.genArrayLoad(site(x), array, index, x.elementKind(), null);
        emitXir(snippet, x, stateFor(x), null, true);
    }

    protected CompilerStub stubFor(CiRuntimeCall runtimeCall) {
        CompilerStub stub = compilation.compiler.lookupStub(runtimeCall);
        compilation.frameMap().usesStub(stub);
        return stub;
    }

    protected CompilerStub stubFor(CompilerStub.Id id) {
        CompilerStub stub = compilation.compiler.lookupStub(id);
        compilation.frameMap().usesStub(stub);
        return stub;
    }

    protected CompilerStub stubFor(XirTemplate template) {
        CompilerStub stub = compilation.compiler.lookupStub(template);
        compilation.frameMap().usesStub(stub);
        return stub;
    }

    @Override
    public void visitLocal(LocalNode x) {
        if (x.operand().isIllegal()) {
            createResultVariable(x);
        }
    }

    @Override
    public void visitLookupSwitch(LookupSwitchNode x) {
        CiValue tag = load(x.value());
        setNoResult(x);

        if (x.numberOfCases() == 0 || x.numberOfCases() < GraalOptions.SequentialSwitchLimit) {
            int len = x.numberOfCases();
            for (int i = 0; i < len; i++) {
                lir.cmp(Condition.EQ, tag, x.keyAt(i));
                lir.branch(Condition.EQ, getLIRBlock(x.blockSuccessor(i)));
            }
            lir.jump(getLIRBlock(x.defaultSuccessor()));
        } else {
            visitSwitchRanges(createLookupRanges(x), tag, getLIRBlock(x.defaultSuccessor()));
        }
    }

    protected LIRBlock getLIRBlock(FixedNode b) {
        if (b == null) {
            return null;
        }
        LIRBlock result = ir.valueToBlock().get(b);
        if (result == null) {
            TTY.println("instruction without lir block: " + b);
        }
        return result;
    }

    @Override
    public void visitFixedGuard(FixedGuardNode fixedGuard) {
        for (BooleanNode condition : fixedGuard.conditions()) {
            emitGuardComp(condition);
        }
    }

    public void emitGuardComp(BooleanNode comp) {
        if (comp instanceof IsNonNullNode) {
            IsNonNullNode x = (IsNonNullNode) comp;
            CiValue value = load(x.object());
            LIRDebugInfo info = stateFor(x);
            lir.nullCheck(value, info);
        } else if (comp instanceof IsTypeNode) {
            IsTypeNode x = (IsTypeNode) comp;
            load(x.object());
            LIRDebugInfo info = stateFor(x);
            XirArgument clazz = toXirArgument(x.type().getEncoding(Representation.ObjectHub));
            XirSnippet typeCheck = xir.genTypeCheck(site(x), toXirArgument(x.object()), clazz, x.type());
            emitXir(typeCheck, x, info, compilation.method, false);
        } else {
            if (comp instanceof ConstantNode && comp.asConstant().asBoolean()) {
                // Nothing to emit.
            } else {
                DeoptimizationStub stub = createDeoptStub("emitGuardComp " + comp);
                emitBooleanBranch(comp, null, new LIRBlock(stub.label, stub.info), null, null, null, stub.info);
            }
        }
    }

    private DeoptimizationStub createDeoptStub(Object deoptInfo) {
        if (deoptimizationStubs == null) {
            deoptimizationStubs = new ArrayList<DeoptimizationStub>();
        }

        FrameState state = lastState;
        assert state != null : "deoptimize instruction always needs a state";
        DeoptimizationStub stub = new DeoptimizationStub(DeoptAction.InvalidateReprofile, state, deoptInfo);
        deoptimizationStubs.add(stub);
        return stub;
    }

    @Override
    public void deoptimizeOn(Condition cond) {
        DeoptimizationStub stub = createDeoptStub("deoptimizeOn " + cond);
        lir.branch(cond, stub.label, stub.info);
    }


    @Override
    public void visitPhi(PhiNode i) {
        Util.shouldNotReachHere();
    }

    @Override
    public void visitReturn(ReturnNode x) {
        if (x.kind.isVoid()) {
            XirSnippet epilogue = xir.genEpilogue(site(x), compilation.method);
            if (epilogue != null) {
                emitXir(epilogue, x, null, compilation.method, false);
                lir.returnOp(IllegalValue);
            }
        } else {
            CiValue operand = resultOperandFor(x.kind);
            CiValue result = force(x.result(), operand);
            XirSnippet epilogue = xir.genEpilogue(site(x), compilation.method);
            if (epilogue != null) {
                emitXir(epilogue, x, null, compilation.method, false);
                lir.returnOp(result);
            }
        }
        setNoResult(x);
    }

    protected XirArgument toXirArgument(CiValue v) {
        if (v == null) {
            return null;
        }

        return XirArgument.forInternalObject(v);
    }

    protected XirArgument toXirArgument(ValueNode i) {
        if (i == null) {
            return null;
        }

        return XirArgument.forInternalObject(new LIRItem(i, this));
    }

    private CiValue allocateOperand(XirSnippet snippet, XirOperand op) {
        if (op instanceof XirParameter)  {
            XirParameter param = (XirParameter) op;
            return allocateOperand(snippet.arguments[param.parameterIndex], op, param.canBeConstant);
        } else if (op instanceof XirRegister) {
            XirRegister reg = (XirRegister) op;
            return reg.register;
        } else if (op instanceof XirTemp) {
            return newVariable(op.kind);
        } else {
            Util.shouldNotReachHere();
            return null;
        }
    }

    private CiValue allocateOperand(XirArgument arg, XirOperand var, boolean canBeConstant) {
        if (arg.constant != null) {
            return arg.constant;
        } else {
            assert arg.object != null;
            if (arg.object instanceof CiValue) {
                return (CiValue) arg.object;
            }
            assert arg.object instanceof LIRItem;
            LIRItem item = (LIRItem) arg.object;
            if (canBeConstant) {
                return item.instruction.operand();
            } else {
                CiKind kind = var.kind;
                if (kind == CiKind.Byte || kind == CiKind.Boolean) {
                    item.loadByteItem();
                } else {
                    item.loadItem();
                }
                return item.result();
            }
        }
    }

    protected CiValue emitXir(XirSnippet snippet, ValueNode x, LIRDebugInfo info, RiMethod method, boolean setInstructionResult) {
        return emitXir(snippet, x, info, null, method, setInstructionResult, null);
    }

    protected CiValue emitXir(XirSnippet snippet, ValueNode instruction, LIRDebugInfo info, LIRDebugInfo infoAfter, RiMethod method, boolean setInstructionResult, List<CiValue> pointerSlots) {
        if (GraalOptions.PrintXirTemplates) {
            TTY.println("Emit XIR template " + snippet.template.name);
        }

        final CiValue[] operands = new CiValue[snippet.template.variableCount];

        compilation.frameMap().reserveOutgoing(snippet.template.outgoingStackSize);

        XirOperand resultOperand = snippet.template.resultOperand;

        if (snippet.template.allocateResultOperand) {
            CiValue outputOperand = IllegalValue;
            // This snippet has a result that must be separately allocated
            // Otherwise it is assumed that the result is part of the inputs
            if (resultOperand.kind != CiKind.Void && resultOperand.kind != CiKind.Illegal) {
                if (setInstructionResult) {
                    outputOperand = newVariable(instruction.kind);
                } else {
                    outputOperand = newVariable(resultOperand.kind);
                }
                assert operands[resultOperand.index] == null;
            }
            operands[resultOperand.index] = outputOperand;
            if (GraalOptions.PrintXirTemplates) {
                TTY.println("Output operand: " + outputOperand);
            }
        }

        for (XirTemp t : snippet.template.temps) {
            if (t instanceof XirRegister) {
                XirRegister reg = (XirRegister) t;
                if (!t.reserve) {
                    operands[t.index] = reg.register;
                }
            }
        }

        for (XirTemplate calleeTemplate : snippet.template.calleeTemplates) {
            // TODO Save these for use in AMD64LIRAssembler
            stubFor(calleeTemplate);
        }

        for (XirConstant c : snippet.template.constants) {
            assert operands[c.index] == null;
            operands[c.index] = c.value;
        }

        XirOperand[] inputOperands = snippet.template.inputOperands;
        XirOperand[] inputTempOperands = snippet.template.inputTempOperands;
        XirOperand[] tempOperands = snippet.template.tempOperands;

        CiValue[] operandArray = new CiValue[inputOperands.length + inputTempOperands.length + tempOperands.length];
        int[] operandIndicesArray = new int[inputOperands.length + inputTempOperands.length + tempOperands.length];
        for (int i = 0; i < inputOperands.length; i++) {
            XirOperand x = inputOperands[i];
            CiValue op = allocateOperand(snippet, x);
            operands[x.index] = op;
            operandArray[i] = op;
            operandIndicesArray[i] = x.index;
            if (GraalOptions.PrintXirTemplates) {
                TTY.println("Input operand: " + x);
            }
        }

        for (int i = 0; i < inputTempOperands.length; i++) {
            XirOperand x = inputTempOperands[i];
            CiValue op = allocateOperand(snippet, x);
            CiValue newOp = newVariable(op.kind);
            lir.move(op, newOp);
            operands[x.index] = newOp;
            operandArray[i + inputOperands.length] = newOp;
            operandIndicesArray[i + inputOperands.length] = x.index;
            if (GraalOptions.PrintXirTemplates) {
                TTY.println("InputTemp operand: " + x);
            }
        }

        for (int i = 0; i < tempOperands.length; i++) {
            XirOperand x = tempOperands[i];
            CiValue op = allocateOperand(snippet, x);
            operands[x.index] = op;
            operandArray[i + inputOperands.length + inputTempOperands.length] = op;
            operandIndicesArray[i + inputOperands.length + inputTempOperands.length] = x.index;
            if (GraalOptions.PrintXirTemplates) {
                TTY.println("Temp operand: " + x);
            }
        }

        for (CiValue operand : operands) {
            assert operand != null;
        }

        CiValue allocatedResultOperand = operands[resultOperand.index];
        if (!allocatedResultOperand.isVariableOrRegister()) {
            allocatedResultOperand = IllegalValue;
        }

        if (setInstructionResult && allocatedResultOperand.isLegal()) {
            if (instruction.operand().isIllegal()) {
                setResult(instruction, (CiVariable) allocatedResultOperand);
            } else {
                assert instruction.operand() == allocatedResultOperand;
            }
        }


        XirInstruction[] slowPath = snippet.template.slowPath;
        if (!operands[resultOperand.index].isConstant() || snippet.template.fastPath.length != 0 || (slowPath != null && slowPath.length > 0)) {
            // XIR instruction is only needed when the operand is not a constant!
            lir.xir(snippet, operands, allocatedResultOperand, inputTempOperands.length, tempOperands.length,
                    operandArray, operandIndicesArray,
                    (operands[resultOperand.index] == IllegalValue) ? -1 : resultOperand.index,
                    info, infoAfter, method, pointerSlots);
            if (GraalOptions.Meter) {
                context.metrics.LIRXIRInstructions++;
            }
        }

        return operands[resultOperand.index];
    }

    @Override
    public void visitStoreField(StoreFieldNode x) {
        RiField field = x.field();
        LIRDebugInfo info = stateFor(x);

        if (x.isVolatile()) {
            vma.preVolatileWrite();
        }

        XirArgument receiver = toXirArgument(x.object());
        XirArgument value = toXirArgument(x.value());
        XirSnippet snippet = x.isStatic() ? xir.genPutStatic(site(x), receiver, field, value) : xir.genPutField(site(x), receiver, field, value);
        emitXir(snippet, x, info, null, true);

        if (x.isVolatile()) {
            vma.postVolatileWrite();
        }
    }

    @Override
    public void visitTableSwitch(TableSwitchNode x) {

        LIRItem value = new LIRItem(x.value(), this);
        // Making a copy of the switch value is necessary when generating a jump table
        value.setDestroysRegister();
        value.loadItem();

        CiValue tag = value.result();
        setNoResult(x);

        // TODO: tune the defaults for the controls used to determine what kind of translation to use
        if (x.numberOfCases() == 0 || x.numberOfCases() <= GraalOptions.SequentialSwitchLimit) {
            int loKey = x.lowKey();
            int len = x.numberOfCases();
            for (int i = 0; i < len; i++) {
                lir.cmp(Condition.EQ, tag, i + loKey);
                lir.branch(Condition.EQ, getLIRBlock(x.blockSuccessor(i)));
            }
            lir.jump(getLIRBlock(x.defaultSuccessor()));
        } else {
            SwitchRange[] switchRanges = createLookupRanges(x);
            int rangeDensity = x.numberOfCases() / switchRanges.length;
            if (rangeDensity >= GraalOptions.RangeTestsSwitchDensity) {
                visitSwitchRanges(switchRanges, tag, getLIRBlock(x.defaultSuccessor()));
            } else {
                LIRBlock[] targets = new LIRBlock[x.numberOfCases()];
                for (int i = 0; i < x.numberOfCases(); ++i) {
                    targets[i] = getLIRBlock(x.blockSuccessor(i));
                }
                lir.tableswitch(tag, x.lowKey(), getLIRBlock(x.defaultSuccessor()), targets);
            }
        }
    }

    @Override
    public void visitDeoptimize(DeoptimizeNode deoptimize) {
        assert lastState != null : "deoptimize always needs a state";
        assert lastState.bci != FixedWithNextNode.SYNCHRONIZATION_ENTRY_BCI : "bci must not be -1 for deopt framestate";
        DeoptimizationStub stub = new DeoptimizationStub(deoptimize.action(), lastState, "DeoptimizeNode " + deoptimize);
        addDeoptimizationStub(stub);
        lir.branch(Condition.TRUE, stub.label, stub.info);
    }

    private void blockDoEpilog() {
        if (GraalOptions.PrintIRWithLIR) {
            TTY.println();
        }
    }

    private void blockDoProlog(LIRBlock block) {
        if (GraalOptions.PrintIRWithLIR) {
            TTY.print(block.toString());
        }
        // set up the list of LIR instructions
        assert block.lir() == null : "LIR list already computed for this block";
        lir = new LIRList(this);
        block.setLir(lir);

        lir.branchDestination(block.label());
    }

    /**
     * Copies a given value into an operand that is forced to be a stack location.
     *
     * @param value a value to be forced onto the stack
     * @param kind the kind of new operand
     * @param mustStayOnStack specifies if the new operand must never be allocated to a register
     * @return the operand that is guaranteed to be a stack location when it is
     *         initially defined a by move from {@code value}
     */
    @Override
    public CiValue forceToSpill(CiValue value, CiKind kind, boolean mustStayOnStack) {
        assert value.isLegal() : "value should not be illegal";
        if (!value.isVariableOrRegister()) {
            // force into a variable that must start in memory
            CiValue operand = operands.newVariable(value.kind, mustStayOnStack ? VariableFlag.MustStayInMemory : VariableFlag.MustStartInMemory);
            lir.move(value, operand);
            return operand;
        }

        // create a spill location
        CiValue operand = operands.newVariable(kind, mustStayOnStack ? VariableFlag.MustStayInMemory : VariableFlag.MustStartInMemory);
        // move from register to spill
        lir.move(value, operand);
        return operand;
    }

    /**
     * Allocates a variable operand to hold the result of a given instruction.
     * This can only be performed once for any given instruction.
     *
     * @param x an instruction that produces a result
     * @return the variable assigned to hold the result produced by {@code x}
     */
    @Override
    public CiVariable createResultVariable(ValueNode x) {
        CiVariable operand = newVariable(x.kind);
        setResult(x, operand);
        return operand;
    }

    @Override
    public void visitRegisterFinalizer(RegisterFinalizerNode x) {
        CiValue receiver = load(x.object());
        LIRDebugInfo info = stateFor(x);
        callRuntime(CiRuntimeCall.RegisterFinalizer, info, receiver);
        setNoResult(x);
    }

    private void visitSwitchRanges(SwitchRange[] x, CiValue value, LIRBlock defaultSux) {
        for (int i = 0; i < x.length; i++) {
            SwitchRange oneRange = x[i];
            int lowKey = oneRange.lowKey;
            int highKey = oneRange.highKey;
            LIRBlock dest = oneRange.sux;
            if (lowKey == highKey) {
                lir.cmp(Condition.EQ, value, lowKey);
                lir.branch(Condition.EQ, dest);
            } else if (highKey - lowKey == 1) {
                lir.cmp(Condition.EQ, value, lowKey);
                lir.branch(Condition.EQ, dest);
                lir.cmp(Condition.EQ, value, highKey);
                lir.branch(Condition.EQ, dest);
            } else {
                Label l = new Label();
                lir.cmp(Condition.LT, value, lowKey);
                lir.branch(Condition.LT, l);
                lir.cmp(Condition.LE, value, highKey);
                lir.branch(Condition.LE, dest);
                lir.branchDestination(l);
            }
        }
        lir.jump(defaultSux);
    }

    protected final CiValue callRuntime(CiRuntimeCall runtimeCall, LIRDebugInfo info, CiValue... args) {
        // get a result register
        CiKind result = runtimeCall.resultKind;
        CiKind[] arguments = runtimeCall.arguments;

        CiValue physReg = result.isVoid() ? IllegalValue : resultOperandFor(result);

        List<CiValue> argumentList;
        if (arguments.length > 0) {
            // move the arguments into the correct location
            CiCallingConvention cc = compilation.frameMap().getCallingConvention(arguments, RuntimeCall);
            assert cc.locations.length == args.length : "argument count mismatch";
            for (int i = 0; i < args.length; i++) {
                CiValue arg = args[i];
                CiValue loc = cc.locations[i];
                if (loc.isRegister()) {
                    lir.move(arg, loc);
                } else {
                    assert loc.isStackSlot();
                    CiStackSlot slot = (CiStackSlot) loc;
                    lir.move(arg, slot);
                }
            }
            argumentList = Arrays.asList(cc.locations);
        } else {
            // no arguments
            assert args == null || args.length == 0;
            argumentList = Util.uncheckedCast(Collections.emptyList());
        }

        lir.callRuntime(runtimeCall, physReg, argumentList, info);

        return physReg;
    }

    protected final CiVariable callRuntimeWithResult(CiRuntimeCall runtimeCall, LIRDebugInfo info, CiValue... args) {
        CiVariable result = newVariable(runtimeCall.resultKind);
        CiValue location = callRuntime(runtimeCall, info, args);
        lir.move(location, result);
        return result;
    }

    SwitchRange[] createLookupRanges(LookupSwitchNode x) {
        // we expect the keys to be sorted by increasing value
        List<SwitchRange> res = new ArrayList<SwitchRange>(x.numberOfCases());
        int len = x.numberOfCases();
        if (len > 0) {
            LIRBlock defaultSux = getLIRBlock(x.defaultSuccessor());
            int key = x.keyAt(0);
            LIRBlock sux = getLIRBlock(x.blockSuccessor(0));
            SwitchRange range = new SwitchRange(key, sux);
            for (int i = 1; i < len; i++) {
                int newKey = x.keyAt(i);
                LIRBlock newSux = getLIRBlock(x.blockSuccessor(i));
                if (key + 1 == newKey && sux == newSux) {
                    // still in same range
                    range.highKey = newKey;
                } else {
                    // skip tests which explicitly dispatch to the default
                    if (range.sux != defaultSux) {
                        res.add(range);
                    }
                    range = new SwitchRange(newKey, newSux);
                }
                key = newKey;
                sux = newSux;
            }
            if (res.size() == 0 || res.get(res.size() - 1) != range) {
                res.add(range);
            }
        }
        return res.toArray(new SwitchRange[res.size()]);
    }

    SwitchRange[] createLookupRanges(TableSwitchNode x) {
        // TODO: try to merge this with the code for LookupSwitch
        List<SwitchRange> res = new ArrayList<SwitchRange>(x.numberOfCases());
        int len = x.numberOfCases();
        if (len > 0) {
            LIRBlock sux = getLIRBlock(x.blockSuccessor(0));
            int key = x.lowKey();
            LIRBlock defaultSux = getLIRBlock(x.defaultSuccessor());
            SwitchRange range = new SwitchRange(key, sux);
            for (int i = 0; i < len; i++, key++) {
                LIRBlock newSux = getLIRBlock(x.blockSuccessor(i));
                if (sux == newSux) {
                    // still in same range
                    range.highKey = key;
                } else {
                    // skip tests which explicitly dispatch to the default
                    if (sux != defaultSux) {
                        res.add(range);
                    }
                    range = new SwitchRange(key, newSux);
                }
                sux = newSux;
            }
            if (res.size() == 0 || res.get(res.size() - 1) != range) {
                res.add(range);
            }
        }
        return res.toArray(new SwitchRange[res.size()]);
    }

    void doRoot(ValueNode instr) {
        if (GraalOptions.TraceLIRGeneratorLevel >= 2) {
            TTY.println("Emitting LIR for instruction " + instr);
        }
        currentInstruction = instr;

        if (GraalOptions.TraceLIRVisit) {
            TTY.println("Visiting    " + instr);
        }

        if (instr instanceof LIRLowerable) {
            ((LIRLowerable) instr).generate(this);
        } else {
            instr.accept(this);
        }

        if (GraalOptions.TraceLIRVisit) {
            TTY.println("Operand for " + instr + " = " + instr.operand());
        }
    }

    protected void logicOp(int code, CiValue resultOp, CiValue leftOp, CiValue rightOp) {
        if (isTwoOperand && leftOp != resultOp) {
            assert rightOp != resultOp : "malformed";
            lir.move(leftOp, resultOp);
            leftOp = resultOp;
        }

        switch (code) {
            case IAND:
            case LAND:
                lir.logicalAnd(leftOp, rightOp, resultOp);
                break;

            case IOR:
            case LOR:
                lir.logicalOr(leftOp, rightOp, resultOp);
                break;

            case IXOR:
            case LXOR:
                lir.logicalXor(leftOp, rightOp, resultOp);
                break;

            default:
                Util.shouldNotReachHere();
        }
    }

    @Override
    public void visitEndNode(EndNode end) {
        setNoResult(end);
        assert end.merge() != null;
        moveToPhi(end.merge(), end);
        LIRBlock lirBlock = getLIRBlock(end.merge());
        assert lirBlock != null : end;
        lir.jump(lirBlock);
    }

    @Override
    public void visitMemoryRead(ReadNode memRead) {
        lir.move(memRead.location().createAddress(this, memRead.object()), createResultVariable(memRead), memRead.location().getValueKind(), genDebugInfo(memRead));
    }

    @Override
    public void visitVolatileMemoryRead(VolatileReadNode memRead) {
        CiValue readValue = load(memRead.getReadNode());
        vma.postVolatileRead();
        CiValue result = createResultVariable(memRead);
        lir.move(readValue, result);
    }

    private LIRDebugInfo genDebugInfo(AccessNode access) {
        if (access.getNullCheck()) {
            return stateFor(access);
        } else {
            return null;
        }
    }


    @Override
    public void visitMemoryWrite(WriteNode memWrite) {
        lir.move(load(memWrite.value()), memWrite.location().createAddress(this, memWrite.object()), memWrite.location().getValueKind(), genDebugInfo(memWrite));
    }


    @Override
    public void visitLoopEnd(LoopEndNode x) {
        setNoResult(x);
        moveToPhi(x.loopBegin(), x);
        if (GraalOptions.GenLoopSafepoints) {
            XirSnippet snippet = xir.genSafepointPoll(site(x));
            emitXir(snippet, x, stateFor(x), null, false);
        }
        lir.jump(getLIRBlock(x.loopBegin()));
    }

    private void moveToPhi(MergeNode merge, Node pred) {
        if (GraalOptions.TraceLIRGeneratorLevel >= 1) {
            TTY.println("MOVE TO PHI from " + pred + " to " + merge);
        }
        int nextSuccIndex = merge.phiPredecessorIndex(pred);
        PhiResolver resolver = new PhiResolver(this);
        for (PhiNode phi : merge.phis()) {
            if (phi.type() == PhiType.Value) {
                ValueNode curVal = phi.valueAt(nextSuccIndex);
                if (curVal != null && curVal != phi) {
                    if (curVal instanceof PhiNode) {
                        operandForPhi((PhiNode) curVal);
                    }
                    CiValue operand = curVal.operand();
                    if (operand.isIllegal()) {
                        assert curVal instanceof ConstantNode || curVal instanceof LocalNode : "these can be produced lazily" + curVal + "/" + phi;
                        operand = operandForInstruction(curVal);
                    }
                    resolver.move(operand, operandForPhi(phi));
                }
            }
        }
        resolver.dispose();
    }

    /**
     * Creates a new {@linkplain CiVariable variable}.
     *
     * @param kind the kind of the variable
     * @return a new variable
     */
    @Override
    public CiVariable newVariable(CiKind kind) {
        return operands.newVariable(kind);
    }

    CiValue operandForInstruction(ValueNode x) {
        CiValue operand = x.operand();
        if (operand.isIllegal()) {
            if (x instanceof ConstantNode) {
                x.setOperand(x.asConstant());
            } else {
                assert x instanceof PhiNode || x instanceof LocalNode : "only for Phi and Local : " + x;
                // allocate a variable for this local or phi
                createResultVariable(x);
            }
        }
        return x.operand();
    }

    private CiValue operandForPhi(PhiNode phi) {
        assert phi.type() == PhiType.Value : "wrong phi type: " + phi.id();
        if (phi.operand().isIllegal()) {
            // allocate a variable for this phi
            createResultVariable(phi);
        }
        return phi.operand();
    }

    protected void postGCWriteBarrier(CiValue addr, CiValue newVal) {
       XirSnippet writeBarrier = xir.genWriteBarrier(toXirArgument(addr));
       if (writeBarrier != null) {
           emitXir(writeBarrier, null, null, null, false);
       }
    }

    protected void preGCWriteBarrier(CiValue addrOpr, boolean patch, LIRDebugInfo info) {
    }

    protected void setNoResult(ValueNode x) {
        x.clearOperand();
    }

    public CiValue setResult(ValueNode x, CiVariable operand) {
        x.setOperand(operand);
        if (GraalOptions.DetailedAsserts) {
            operands.recordResult(operand, x);
        }
        return operand;
    }

    protected void shiftOp(int code, CiValue resultOp, CiValue value, CiValue count, CiValue tmp) {
        if (isTwoOperand && value != resultOp) {
            assert count != resultOp : "malformed";
            lir.move(value, resultOp);
            value = resultOp;
        }

        assert count.isConstant() || count.isVariableOrRegister();
        switch (code) {
            case ISHL:
            case LSHL:
                lir.shiftLeft(value, count, resultOp, tmp);
                break;
            case ISHR:
            case LSHR:
                lir.shiftRight(value, count, resultOp, tmp);
                break;
            case IUSHR:
            case LUSHR:
                lir.unsignedShiftRight(value, count, resultOp, tmp);
                break;
            default:
                Util.shouldNotReachHere();
        }
    }

    protected void walkState(final Node x, FrameState state) {
        if (state == null) {
            return;
        }

        state.forEachLiveStateValue(new ValueProcedure() {
            public void doValue(ValueNode value) {
                if (value == x) {
                    // nothing to do, will be visited shortly
                } else if (value instanceof PhiNode && ((PhiNode) value).type() == PhiType.Value) {
                    // phi's are special
                    operandForPhi((PhiNode) value);
                } else if (value.operand().isIllegal()) {
                    // instruction doesn't have an operand yet
                    CiValue operand = makeOperand(value);
                    assert operand.isLegal() : "must be evaluated now";
                }
            }
        });
    }

    protected LIRDebugInfo stateFor(ValueNode x) {
        assert lastState != null : "must have state before instruction for " + x;
        return stateFor(x, lastState);
    }

    protected LIRDebugInfo stateFor(ValueNode x, FrameState state) {
        if (compilation.placeholderState != null) {
            state = compilation.placeholderState;
        }
        return new LIRDebugInfo(state);
    }

    List<CiValue> visitInvokeArguments(CiCallingConvention cc, Iterable<ValueNode> arguments, List<CiValue> pointerSlots) {
        // for each argument, load it into the correct location
        List<CiValue> argList = new ArrayList<CiValue>();
        int j = 0;
        for (ValueNode arg : arguments) {
            if (arg != null) {
                CiValue operand = cc.locations[j++];
                if (operand.isRegister()) {
                    force(arg, operand);
                } else {
                    LIRItem param = new LIRItem(arg, this);
                    assert operand.isStackSlot();
                    CiStackSlot slot = (CiStackSlot) operand;
                    assert !slot.inCallerFrame();
                    param.loadForStore(slot.kind);
                    lir.move(param.result(), slot);

                    if (arg.kind == CiKind.Object && pointerSlots != null) {
                        // This slot must be marked explicitly in the pointer map.
                        pointerSlots.add(slot);
                    }
                }
                argList.add(operand);
            }
        }
        return argList;
    }

    /**
     * Ensures that an operand has been {@linkplain ValueNode#setOperand(CiValue) initialized}
     * for storing the result of an instruction.
     *
     * @param instruction an instruction that produces a result value
     */
    @Override
    public CiValue makeOperand(ValueNode instruction) {
        if (instruction == null) {
            return CiValue.IllegalValue;
        }
        CiValue operand = instruction.operand();
        if (operand.isIllegal()) {
            if (instruction instanceof PhiNode) {
                // a phi may not have an operand yet if it is for an exception block
                operand = operandForPhi((PhiNode) instruction);
            } else if (instruction instanceof ConstantNode) {
                operand = operandForInstruction(instruction);
            }
        }
        // the value must be a constant or have a valid operand
        assert operand.isLegal() : "this root has not been visited yet; instruction=" + instruction + " currentBlock=" + currentBlock;
        return operand;
    }

    /**
     * Gets the ABI specific operand used to return a value of a given kind from a method.
     *
     * @param kind the kind of value being returned
     * @return the operand representing the ABI defined location used return a value of kind {@code kind}
     */
    protected CiValue resultOperandFor(CiKind kind) {
        if (kind == CiKind.Void) {
            return IllegalValue;
        }
        CiRegister returnRegister = compilation.registerConfig.getReturnRegister(kind);
        return returnRegister.asValue(kind);
    }

    protected XirSupport site(ValueNode x) {
        return xirSupport.site(x);
    }

    public void maybePrintCurrentInstruction() {
        if (currentInstruction != null && lastInstructionPrinted != currentInstruction) {
            lastInstructionPrinted = currentInstruction;
            InstructionPrinter ip = new InstructionPrinter(TTY.out());
            ip.printInstructionListing(currentInstruction);
        }
    }

    public abstract boolean canInlineAsConstant(ValueNode i);

    protected abstract boolean canStoreAsConstant(ValueNode i, CiKind kind);

    protected abstract void genCmpMemInt(Condition condition, CiValue base, int disp, int c, LIRDebugInfo info);

    protected abstract void genCmpRegMem(Condition condition, CiValue reg, CiValue base, int disp, CiKind kind, LIRDebugInfo info);

    /**
     * Implements site-specific information for the XIR interface.
     */
    static class XirSupport implements XirSite {
        ValueNode current;

        XirSupport() {
        }

        public CiCodePos getCodePos() {
            // TODO: get the code position of the current instruction if possible
            return null;
        }

        public boolean isNonNull(XirArgument argument) {
            return false;
        }

        public boolean requiresNullCheck() {
            return current == null || true;
        }

        public boolean requiresBoundsCheck() {
            return true;
        }

        public boolean requiresReadBarrier() {
            return current == null || true;
        }

        public boolean requiresWriteBarrier() {
            return current == null || true;
        }

        public boolean requiresArrayStoreCheck() {
            return true;
        }

        public RiType getApproximateType(XirArgument argument) {
            return current == null ? null : current.declaredType();
        }

        public RiType getExactType(XirArgument argument) {
            return current == null ? null : current.exactType();
        }

        XirSupport site(ValueNode v) {
            current = v;
            return this;
        }

        @Override
        public String toString() {
            return "XirSupport<" + current + ">";
        }
    }

    @Override
    public void visitFrameState(FrameState i) {
        // nothing to do for now
    }

    @Override
    public void visitUnwind(UnwindNode x) {
        // move exception oop into fixed register
        CiCallingConvention callingConvention = compilation.frameMap().getCallingConvention(new CiKind[]{CiKind.Object}, RuntimeCall);
        CiValue argumentOperand = callingConvention.locations[0];
        lir.move(makeOperand(x.exception()), argumentOperand);
        List<CiValue> args = new ArrayList<CiValue>(1);
        lir.callRuntime(CiRuntimeCall.UnwindException, CiValue.IllegalValue, args, null);
        setNoResult(x);
    }

    public abstract Condition floatingPointCondition(Condition cond);

    @Override
    public void visitConditional(ConditionalNode conditional) {
        CiVariable result = createResultVariable(conditional);
        CiValue tVal = makeOperand(conditional.trueValue());
        CiValue fVal = makeOperand(conditional.falseValue());
        emitBooleanBranch(conditional.condition(), null, null, tVal, fVal, result, null);
    }

    @Override
    public void visitRuntimeCall(RuntimeCallNode x) {
        LIRDebugInfo info = null;
        if (x.stateAfter() != null) {
            info = stateFor(x, stateBeforeCallReturn(x, x.stateAfter().bci));
        }
        CiValue resultOperand = resultOperandFor(x.kind);
        CiCallingConvention cc = compilation.frameMap().getCallingConvention(x.call().arguments, RuntimeCall);
        List<CiValue> pointerSlots = new ArrayList<CiValue>(2);
        List<CiValue> argList = visitInvokeArguments(cc, x.arguments(), pointerSlots);

        lir.callRuntime(x.call(), resultOperand, argList, info);

        if (resultOperand.isLegal()) {
            CiValue result = createResultVariable(x);
            lir.move(resultOperand, result);
        }
    }
}
