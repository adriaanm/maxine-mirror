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
package com.oracle.max.graal.nodes.extended;

import java.util.*;

import com.oracle.max.graal.cri.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.PhiNode.PhiType;
import com.oracle.max.graal.nodes.calc.*;
import com.oracle.max.graal.nodes.spi.*;
import com.sun.cri.ci.*;

public final class CreateVectorNode extends AbstractVectorNode implements Lowerable {
    @Input private ValueNode length;

    public ValueNode length() {
        return length;
    }

    private boolean reversed;

    public boolean reversed() {
        return reversed;
    }

    public CreateVectorNode(boolean reversed, ValueNode length) {
        super(CiKind.Illegal, null);
        this.length = length;
        this.reversed = reversed;
    }

    @Override
    public Map<Object, Object> getDebugProperties() {
        Map<Object, Object> debugProperties = super.getDebugProperties();
        debugProperties.put("reversed", reversed);
        return debugProperties;
    }

    private LoopBeginNode createLoop(Map<AbstractVectorNode, ValueNode> map) {
        EndNode end = graph().add(new EndNode());
        LoopBeginNode loopBegin = graph().add(new LoopBeginNode());
        loopBegin.addEnd(end);
        PhiNode loopVariable = graph().unique(new PhiNode(CiKind.Int, loopBegin, PhiType.Value));

        if (reversed) {
            IntegerSubNode add = graph().unique(new IntegerSubNode(CiKind.Int, loopVariable, ConstantNode.forInt(1, graph())));
            loopVariable.addInput(graph().unique(new IntegerSubNode(CiKind.Int, length(), ConstantNode.forInt(1, graph()))));
            loopVariable.addInput(add);
        } else {
            IntegerAddNode add = graph().unique(new IntegerAddNode(CiKind.Int, loopVariable, ConstantNode.forInt(1, graph())));
            loopVariable.addInput(ConstantNode.forInt(0, graph()));
            loopVariable.addInput(add);
        }

        LoopEndNode loopEnd = graph().add(new LoopEndNode());
        loopEnd.setLoopBegin(loopBegin);
        loopBegin.setStateAfter(stateAfter());
        CompareNode condition;
        if (reversed) {
            condition = graph().unique(new CompareNode(loopVariable, Condition.GE, ConstantNode.forInt(0, graph())));
        } else {
            condition = graph().unique(new CompareNode(loopVariable, Condition.LT, length()));
        }
        int expectedLength = 100; // TODO(ls) get a more accurate estimate using expected loop counts
        if (length().isConstant()) {
            expectedLength = length().asConstant().asInt();
        }
        IfNode ifNode = graph().add(new IfNode(condition, 1.0 / expectedLength));
        loopBegin.setNext(ifNode);
        ifNode.setTrueSuccessor(BeginNode.begin(loopEnd));
        this.replaceAtPredecessors(end);
        ifNode.setFalseSuccessor(BeginNode.begin(this));
        map.put(this, loopVariable);
        return loopBegin;
    }

    private static void processUse(LoopBeginNode loop, Node use, IdentityHashMap<AbstractVectorNode, ValueNode> nodes) {
        AbstractVectorNode vectorNode = (AbstractVectorNode) use;
        if (nodes.containsKey(vectorNode)) {
            return;
        }
        nodes.put(vectorNode, null);

        // Make sure inputs are evaluated.
        for (Node input : use.inputs()) {
            if (input instanceof AbstractVectorNode) {
                AbstractVectorNode abstractVectorNodeInput = (AbstractVectorNode) input;
                processUse(loop, abstractVectorNodeInput, nodes);
            }
        }

        vectorNode.addToLoop(loop, nodes);

        // Go on to usages.
        for (Node usage : use.usages()) {
            processUse(loop, usage, nodes);
        }
    }

    @Override
    public void lower(CiLoweringTool tool) {
        IdentityHashMap<AbstractVectorNode, ValueNode> nodes = new IdentityHashMap<AbstractVectorNode, ValueNode>();
        LoopBeginNode begin = createLoop(nodes);
        for (Node use : usages()) {
            processUse(begin, use, nodes);
        }
    }
}
