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
import com.oracle.max.graal.compiler.gen.LIRGenerator.*;
import com.oracle.max.graal.graph.*;

/**
 * This class implements the overall container for the LIR graph
 * and directs its construction, optimization, and finalization.
 */
public class LIR {

    /**
     * The start block of this LIR.
     */
    private final LIRBlock startBlock;

    /**
     * The linear-scan ordered list of blocks.
     */
    private final List<LIRBlock> linearScanOrder;

    /**
     * The order in which the code is emitted.
     */
    private final List<LIRBlock> codeEmittingOrder;

    private final NodeMap<LIRBlock> valueToBlock;
    private ArrayList<DeoptimizationStub> deoptimizationStubs;

    /**
     * Creates a new LIR instance for the specified compilation.
     * @param compilation the compilation
     */
    public LIR(LIRBlock startBlock, List<LIRBlock> linearScanOrder, List<LIRBlock> codeEmittingOrder, NodeMap<LIRBlock> valueToBlock) {
        this.codeEmittingOrder = codeEmittingOrder;
        this.linearScanOrder = linearScanOrder;
        this.startBlock = startBlock;
        this.valueToBlock = valueToBlock;
    }

    /**
     * Gets the linear scan ordering of blocks as a list.
     * @return the blocks in linear scan order
     */
    public List<LIRBlock> linearScanOrder() {
        return linearScanOrder;
    }

    public List<LIRBlock> codeEmittingOrder() {
        return codeEmittingOrder;
    }

    public LIRBlock startBlock() {
        return startBlock;
    }

    public NodeMap<LIRBlock> valueToBlock() {
        return valueToBlock;
    }

    public void setDeoptimizationStubs(ArrayList<DeoptimizationStub> deoptimizationStubs) {
        this.deoptimizationStubs = deoptimizationStubs;
    }


    public ArrayList<DeoptimizationStub> deoptimizationStubs() {
        return deoptimizationStubs;
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
            for (LIRInstruction op : bb.lir()) {
                TTY.println(op.toStringWithIdPrefix());
                TTY.println();
            }
            TTY.println();
        }
    }
}
