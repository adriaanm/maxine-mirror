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

import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.alloc.*;
import com.oracle.max.graal.compiler.gen.*;
import com.sun.cri.ci.*;
import com.sun.cri.ci.CiTargetMethod.Mark;
import com.sun.cri.ri.*;
import com.sun.cri.xir.CiXirAssembler.XirMark;
import com.sun.cri.xir.*;

/**
 * This class represents a list of LIR instructions and contains factory methods for creating and appending LIR
 * instructions to this list.
 */
public final class LIRList {

    private List<LIRInstruction> operations;
    private final LIRGenerator generator;

    public LIRList(LIRGenerator generator) {
        this.generator = generator;
        this.operations = new ArrayList<LIRInstruction>(8);
    }

    public void append(LIRInstruction op) {
        if (GraalOptions.PrintIRWithLIR && !TTY.isSuppressed()) {
            generator.maybePrintCurrentInstruction();
            TTY.println(op.toStringWithIdPrefix());
            TTY.println();
        }
        operations.add(op);
        assert op.verify();
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

    public void callDirect(RiMethod method, CiValue result, List<CiValue> arguments, LIRDebugInfo info, Map<XirMark, Mark> marks, List<CiValue> pointerSlots) {
        append(new LIRCall(LegacyOpcode.DirectCall, method, result, arguments, info, marks, false, pointerSlots));
    }

    public void callIndirect(RiMethod method, CiValue result, List<CiValue> arguments, LIRDebugInfo info, Map<XirMark, Mark> marks, List<CiValue> pointerSlots) {
        append(new LIRCall(LegacyOpcode.IndirectCall, method, result, arguments, info, marks, false, pointerSlots));
    }

    public void callNative(String symbol, CiValue result, List<CiValue> arguments, LIRDebugInfo info, Map<XirMark, Mark> marks) {
        append(new LIRCall(LegacyOpcode.NativeCall, symbol, result, arguments, info, marks, false, null));
    }

    public void membar(int barriers) {
        append(new LIRInstruction(LegacyOpcode.Membar, CiValue.IllegalValue, null, CiConstant.forInt(barriers)));
    }

    public void lea(CiValue src, CiValue dst) {
        append(new LIRInstruction(LegacyOpcode.Lea, dst, null, src));
    }

    public void move(CiAddress src, CiValue dst, LIRDebugInfo info) {
        append(new LIRMove(LegacyOpcode.Move, src, dst, src.kind, info));
    }

    public void move(CiValue src, CiAddress dst, LIRDebugInfo info) {
        append(new LIRMove(LegacyOpcode.Move, src, dst, dst.kind, info));
    }

    public void move(CiValue src, CiValue dst, CiKind kind, LIRDebugInfo info) {
        append(new LIRMove(LegacyOpcode.Move, src, dst, kind, info));
    }

    public void move(CiValue src, CiValue dst) {
        append(new LIRMove(LegacyOpcode.Move, src, dst, dst.kind, null));
    }

    public void oop2reg(Object o, CiValue dst) {
        append(new LIRMove(LegacyOpcode.Move, CiConstant.forObject(o), dst, dst.kind, null));
    }

    public void returnOp(CiValue result) {
        append(new LIRInstruction(LegacyOpcode.Return, CiValue.IllegalValue, null, result));
    }

    public void monitorAddress(int monitor, CiValue dst) {
        append(new LIRInstruction(LegacyOpcode.MonitorAddress, dst, null, CiConstant.forInt(monitor)));
    }

    public void nullCheck(CiValue opr, LIRDebugInfo info) {
        append(new LIRInstruction(LegacyOpcode.NullCheck, CiValue.IllegalValue, info, opr));
    }

    public void tableswitch(CiValue index, int lowKey, LIRBlock defaultTargets, LIRBlock[] targets) {
        append(new LIRTableSwitch(index, lowKey, defaultTargets, targets));
    }

    public void lcmp2int(CiValue left, CiValue right, CiValue dst) {
        append(new LIRInstruction(LegacyOpcode.Cmpl2i, dst, null, left, right));
    }

    public void callRuntime(CiRuntimeCall rtCall, CiValue result, List<CiValue> arguments, LIRDebugInfo info) {
        append(new LIRCall(LegacyOpcode.DirectCall, rtCall, result, arguments, info, null, false, null));
    }

    public void breakpoint() {
        append(new LIRInstruction(LegacyOpcode.Breakpoint, CiValue.IllegalValue, null));
    }

    public void prefetch(CiAddress addr, boolean isStore) {
        append(new LIRInstruction(isStore ? LegacyOpcode.Prefetchw : LegacyOpcode.Prefetchr, CiValue.IllegalValue, null, addr));
    }

    public void lsb(CiValue src, CiValue dst) {
        append(new LIRInstruction(LegacyOpcode.Lsb, dst, null, false, 1, 0, src));
    }

    public void msb(CiValue src, CiValue dst) {
        append(new LIRInstruction(LegacyOpcode.Msb, dst, null, false, 1, 0, src));
    }

    public void fcmp2int(CiValue left, CiValue right, CiValue dst, boolean isUnorderedLess) {
        append(new LIRInstruction(isUnorderedLess ? LegacyOpcode.Ucmpfd2i : LegacyOpcode.Cmpfd2i, dst, null, left, right));
    }

    public void cas(CiValue addr, CiValue cmpValue, CiValue newValue, CiValue dst) {
        // Compare and swap produces condition code "zero" if contentsOf(addr) == cmpValue,
        // implying successful swap of newValue into addr
        append(new LIRInstruction(LegacyOpcode.Cas, dst, null, addr, cmpValue, newValue));
    }

    public void store(CiValue src, CiAddress dst, LIRDebugInfo info) {
        append(new LIRMove(LegacyOpcode.Move, src, dst, dst.kind, info));
    }

    public void load(CiAddress src, CiValue dst, LIRDebugInfo info) {
        append(new LIRMove(LegacyOpcode.Move, src, dst, src.kind, info));
    }

    public static void printBlock(LIRBlock x) {
        // print block id
        TTY.print("B%d ", x.blockID());

        // print flags
        if (x.isLinearScanLoopHeader()) {
            TTY.print("lh ");
        }
        if (x.isLinearScanLoopEnd()) {
            TTY.print("le ");
        }

        // print block bci range
        TTY.print("[%d, %d] ", -1, -1);

        // print predecessors and successors
        if (x.numberOfPreds() > 0) {
            TTY.print("preds: ");
            for (int i = 0; i < x.numberOfPreds(); i++) {
                TTY.print("B%d ", x.predAt(i).blockID());
            }
        }

        if (x.numberOfSux() > 0) {
            TTY.print("sux: ");
            for (int i = 0; i < x.numberOfSux(); i++) {
                TTY.print("B%d ", x.suxAt(i).blockID());
            }
        }

        TTY.println();
    }

    public static void printLIR(List<LIRBlock> blocks) {
        if (TTY.isSuppressed()) {
            return;
        }
        TTY.println("LIR:");
        int i;
        for (i = 0; i < blocks.size(); i++) {
            LIRBlock bb = blocks.get(i);
            printBlock(bb);
            TTY.println("__id_Instruction___________________________________________");
            bb.lir().printInstructions();
        }
    }

    private void printInstructions() {
        for (int i = 0; i < operations.size(); i++) {
            TTY.println(operations.get(i).toStringWithIdPrefix());
            TTY.println();
        }
        TTY.println();
    }

    public void append(LIRInsertionBuffer buffer) {
        assert this == buffer.lirList() : "wrong lir list";
        int n = operations.size();

        if (buffer.numberOfOps() > 0) {
            // increase size of instructions list
            for (int i = 0; i < buffer.numberOfOps(); i++) {
                operations.add(null);
            }
            // insert ops from buffer into instructions list
            int opIndex = buffer.numberOfOps() - 1;
            int ipIndex = buffer.numberOfInsertionPoints() - 1;
            int fromIndex = n - 1;
            int toIndex = operations.size() - 1;
            for (; ipIndex >= 0; ipIndex--) {
                int index = buffer.indexAt(ipIndex);
                // make room after insertion point
                while (index < fromIndex) {
                    operations.set(toIndex--, operations.get(fromIndex--));
                }
                // insert ops from buffer
                for (int i = buffer.countAt(ipIndex); i > 0; i--) {
                    operations.set(toIndex--, buffer.opAt(opIndex--));
                }
            }
        }

        buffer.finish();
    }

    public void insertBefore(int i, LIRInstruction op) {
        operations.add(i, op);
    }

    public void xir(XirSnippet snippet, CiValue[] operands, CiValue outputOperand, int tempInputCount, int tempCount, CiValue[] inputOperands, int[] operandIndices, int outputOperandIndex,
                    LIRDebugInfo info, LIRDebugInfo infoAfter, RiMethod method, List<CiValue> pointerSlots) {
        append(new LIRXirInstruction(snippet, operands, outputOperand, tempInputCount, tempCount, inputOperands, operandIndices, outputOperandIndex, info, infoAfter, method, pointerSlots));
    }
}
