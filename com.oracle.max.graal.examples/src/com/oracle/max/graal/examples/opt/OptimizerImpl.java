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
package com.oracle.max.graal.examples.opt;

import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.examples.intrinsics.*;
import com.oracle.max.graal.extensions.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.calc.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;


public class OptimizerImpl implements Optimizer {

    @Override
    public void optimize(RiRuntime runtime, Graph graph) {
        // iterate over all instanceof of SafeAddNode in the graph
        for (SafeAddNode safeAdd : graph.getNodes(SafeAddNode.class)) {
            if (!canOverflow(safeAdd)) {
                // if an overflow is impossible: replace with normal add
                IntegerAddNode add = graph.unique(new IntegerAddNode(CiKind.Int, safeAdd.x(), safeAdd.y()));
                safeAdd.replaceAndDelete(add);
            }
        }
    }

    private boolean canOverflow(SafeAddNode safeAdd) {
        // if this SafeAddNode always adds 1 ...
        if (safeAdd.y().isConstant() && safeAdd.y().asConstant().asLong() == 1) {
            // ... to a phi ...
            if (safeAdd.x() instanceof PhiNode) {
                PhiNode phi = (PhiNode) safeAdd.x();
                // ... that belongs to a loop and merges into itself ...
                if (phi.merge() instanceof LoopBeginNode && phi.valueAt(1) == safeAdd) {
                    LoopBeginNode loopBegin = (LoopBeginNode) phi.merge();
                    // ... then do the heavy analysis.
                    return canOverflow(phi, loopBegin);
                }
            }
        }
        return true;
    }

    private boolean canOverflow(PhiNode phi, LoopBeginNode loopBegin) {
        NodeBitMap nodes = LoopUtil.markUpCFG(loopBegin);
        NodeBitMap exits = LoopUtil.computeLoopExits(loopBegin, nodes);
        // look at all loop exits:
        for (Node exit : exits) {
            TTY.println("exit: " + exit);
            Node pred = exit.predecessor();
            // if this exit is an If node ...
            if (pred instanceof IfNode) {
                IfNode ifNode = (IfNode) pred;
                // ... which compares ...
                if (ifNode.compare() instanceof CompareNode) {
                    CompareNode compare = (CompareNode) ifNode.compare();
                    Condition cond = compare.condition();
                    ValueNode x = compare.x();
                    ValueNode y = compare.y();
                    if (ifNode.trueSuccessor() == pred) {
                        cond = cond.negate();
                    }
                    // ... the phi against a value, then this phi cannot overflow.
                    if (cond == Condition.LT && x == phi) {
                        return false;
                    }
                    if (cond == Condition.GT && y == phi) {
                        return false;
                    }
                }
            }
        }
        TTY.println("can overflow");
        return true;
    }
}
