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

import java.util.*;

import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.spi.*;
import com.sun.cri.ci.*;

/**
 * Returns -1, 0, or 1 if either x > y, x == y, or x < y.
 */
// TODO(cwi): Since the machine code representation of this node uses branches and individual constant assignemnts anyway,
// it should be lowered to explicit control flow nodes.  This removes AMD64CompareToIntOpcode.
// For code generated by javac, the opcodes are followed by a branch instruction immediately, so in all cases that matter
// this node is canonicalized away.
// Consider moving this node and the code that does the lowering / canonicalization in a Java-specific project.
public final class NormalizeCompareNode extends BinaryNode implements LIRLowerable {
    @Data public final boolean isUnorderedLess;

    /**
     * Creates a new compare operation.
     * @param x the first input
     * @param y the second input
     * @param isUnorderedLess true when an unordered floating point comparison is interpreted as less, false when greater.
     */
    public NormalizeCompareNode(ValueNode x, ValueNode y, boolean isUnorderedLess) {
        super(CiKind.Int, x, y);
        this.isUnorderedLess = isUnorderedLess;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.visitNormalizeCompare(this);
    }

    @Override
    public Map<Object, Object> getDebugProperties() {
        Map<Object, Object> properties = super.getDebugProperties();
        properties.put("isUnorderedLess", isUnorderedLess);
        return properties;
    }
}
