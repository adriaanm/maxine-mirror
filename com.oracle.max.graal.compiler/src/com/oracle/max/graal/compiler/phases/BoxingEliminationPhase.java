/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.phases;

import java.util.*;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.PhiNode.*;
import com.oracle.max.graal.nodes.extended.*;
import com.oracle.max.graal.nodes.virtual.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

public class BoxingEliminationPhase extends Phase {

    private RiRuntime runtime;

    public BoxingEliminationPhase(RiRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    protected void run(StructuredGraph graph) {
        if (graph.getNodes(UnboxNode.class).iterator().hasNext()) {

            Map<PhiNode, PhiNode> phiReplacements = new HashMap<PhiNode, PhiNode>();
            for (UnboxNode unboxNode : graph.getNodes(UnboxNode.class)) {
                tryEliminate(unboxNode, graph, phiReplacements);
            }

            new DeadCodeEliminationPhase().apply(graph);

            for (BoxNode boxNode : graph.getNodes(BoxNode.class)) {
                tryEliminate(boxNode, graph);
            }
        }
    }

    private void tryEliminate(UnboxNode unboxNode, StructuredGraph graph, Map<PhiNode, PhiNode> phiReplacements) {
        ValueNode unboxedValue = unboxedValue(unboxNode.source(), unboxNode.destinationKind(), phiReplacements);
        if (unboxedValue != null) {
            assert unboxedValue.kind() == unboxNode.destinationKind();
            unboxNode.replaceAndUnlink(unboxedValue);
        }
    }

    private PhiNode getReplacementPhi(PhiNode phiNode, CiKind kind, Map<PhiNode, PhiNode> phiReplacements) {
        if (!phiReplacements.containsKey(phiNode)) {
            PhiNode result = null;
            if (phiNode.stamp().nonNull()) {
                RiResolvedType exactType = phiNode.stamp().exactType();
                if (exactType != null && exactType.toJava() == kind.toUnboxedJavaClass()) {
                    result = phiNode.graph().add(new PhiNode(kind, phiNode.merge(), PhiType.Value));
                    phiReplacements.put(phiNode, result);
                    virtualizeUsages(phiNode, result, exactType);
                    int i = 0;
                    for (ValueNode n : phiNode.values()) {
                        ValueNode unboxedValue = unboxedValue(n, kind, phiReplacements);
                        if (unboxedValue != null) {
                            assert unboxedValue.kind() == kind;
                            result.addInput(unboxedValue);
                        } else {
                            UnboxNode unboxNode = phiNode.graph().add(new UnboxNode(kind, n));
                            FixedNode pred = phiNode.merge().phiPredecessorAt(i);
                            pred.replaceAtPredecessors(unboxNode);
                            unboxNode.setNext(pred);
                            result.addInput(unboxNode);
                        }
                        ++i;
                    }
                }
            }
        }
        return phiReplacements.get(phiNode);
    }

    private ValueNode unboxedValue(ValueNode n, CiKind kind, Map<PhiNode, PhiNode> phiReplacements) {
        if (n instanceof BoxNode) {
            BoxNode boxNode = (BoxNode) n;
            return boxNode.source();
        } else if (n instanceof PhiNode) {
            PhiNode phiNode = (PhiNode) n;
            return getReplacementPhi(phiNode, kind, phiReplacements);
        } else {
            return null;
        }
    }

    private void tryEliminate(BoxNode boxNode, StructuredGraph graph) {

        virtualizeUsages(boxNode, boxNode.source(), boxNode.exactType());

        for (Node n : boxNode.usages()) {
            if (!(n instanceof FrameState) && !(n instanceof VirtualObjectFieldNode)) {
                // Elimination failed, because boxing object escapes.
                return;
            }
        }

        FrameState stateAfter = boxNode.stateAfter();
        boxNode.setStateAfter(null);
        stateAfter.safeDelete();
        FixedNode next = boxNode.next();
        boxNode.setNext(null);
        boxNode.replaceAtPredecessors(next);
        boxNode.safeDelete();
    }

    private void virtualizeUsages(ValueNode boxNode, ValueNode replacement, RiResolvedType exactType) {
        ValueNode virtualValueNode = null;
        VirtualObjectNode virtualObjectNode = null;
        for (Node n : boxNode.usages().snapshot()) {
            if (n instanceof FrameState || n instanceof VirtualObjectFieldNode) {
                if (virtualValueNode == null) {
                    virtualObjectNode = n.graph().unique(new BoxedVirtualObjectNode(exactType, replacement));
                }
                n.replaceFirstInput(boxNode, virtualObjectNode);
            }
        }
    }
}
