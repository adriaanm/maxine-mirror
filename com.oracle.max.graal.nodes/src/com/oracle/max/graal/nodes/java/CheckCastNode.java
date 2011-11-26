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
package com.oracle.max.graal.nodes.java;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.extended.*;
import com.oracle.max.graal.nodes.spi.*;
import com.oracle.max.graal.nodes.type.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code CheckCastNode} represents a {@link Bytecodes#CHECKCAST}.
 */
public final class CheckCastNode extends TypeCheckNode implements Canonicalizable, LIRLowerable {

    @Input private final AnchorNode anchor;

    /**
     * Creates a new CheckCast instruction.
     *
     * @param targetClassInstruction the instruction which produces the class which is being cast to
     * @param targetClass the class being cast to
     * @param object the instruction producing the object
     */
    public CheckCastNode(AnchorNode anchor, ValueNode targetClassInstruction, RiResolvedType targetClass, ValueNode object) {
        super(targetClassInstruction, targetClass, object, targetClass == null ? StampFactory.forKind(CiKind.Object) : StampFactory.declared(targetClass));
        this.anchor = anchor;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.visitCheckCast(this);
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        RiResolvedType objectExactType = object().exactType();
        RiResolvedType classExactType = exactType();
        if (objectExactType != null && classExactType != null && objectExactType.isSubtypeOf(classExactType)) {
            freeAnchor();
            return object();
        }
        CiConstant constant = object().asConstant();
        if (constant != null) {
            assert constant.kind == CiKind.Object;
            if (constant.isNull()) {
                freeAnchor();
                return object();
            }
        }
        return this;
    }

    // TODO(tw): Find a better way to handle anchors.
    private void freeAnchor() {
        for (Node n : usages()) {
            if (n instanceof ValueAnchorNode) {
                n.replaceFirstInput(this, null);
                return;
            }
        }
    }

    @Override
    public BooleanNode negate() {
        throw new Error("A CheckCast does not produce a boolean value, so it should actually not be a subclass of BooleanNode");
    }
}
