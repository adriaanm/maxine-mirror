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
package com.oracle.max.graal.nodes.calc;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.java.*;
import com.oracle.max.graal.nodes.spi.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

public final class NullCheckNode extends BooleanNode implements Canonicalizable, LIRLowerable {

    @Input private ValueNode object;
    @Data public final boolean expectedNull;

    public ValueNode object() {
        return object;
    }

    /**
     * Constructs a new NullCheck instruction.
     *
     * @param object the instruction producing the object to check against null
     * @param expectedNull True when this node checks that the value is null, false when this node checks for non-null
     */
    public NullCheckNode(ValueNode object, boolean expectedNull) {
        super(CiKind.Object);
        assert object.kind() == CiKind.Object : object.kind();
        this.object = object;
        this.expectedNull = expectedNull;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        // Nothing to do.
    }

    @Override
    public RiResolvedType declaredType() {
        // null check does not alter the type of the object
        return object().declaredType();
    }

    @Override
    public RiResolvedType exactType() {
        // null check does not alter the type of the object
        return object().exactType();
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (object instanceof NewInstanceNode || object instanceof NewArrayNode) {
            return ConstantNode.forBoolean(!expectedNull, graph());
        }
        CiConstant constant = object().asConstant();
        if (constant != null) {
            assert constant.kind == CiKind.Object;
            return ConstantNode.forBoolean(constant.isNull() == expectedNull, graph());
        }

        if (object instanceof LocalNode) {
            LocalNode localNode = (LocalNode) object;
            if (!localNode.canBeNull()) {
                return ConstantNode.forBoolean(!expectedNull, graph());
            }
        }
        return this;
    }

    @Override
    public BooleanNode negate() {
        return graph().unique(new NullCheckNode(object(), !expectedNull));
    }
}
