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
package com.oracle.max.graal.examples.safeadd;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.DeoptimizeNode.*;
import com.oracle.max.graal.nodes.calc.*;
import com.oracle.max.graal.nodes.spi.*;
import com.sun.cri.ci.*;

@NodeInfo(shortName = "[+]")
public final class SafeAddNode extends FloatingNode implements LIRLowerable {
    @Input private ValueNode x;
    @Input private ValueNode y;

    public SafeAddNode(ValueNode x, ValueNode y) {
        super(CiKind.Int);
        this.x = x;
        this.y = y;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.setResult(this, gen.emitAdd(gen.operand(x), gen.operand(y)));
        gen.emitDeoptimizeOn(Condition.OF, DeoptAction.InvalidateReprofile, "SafeAdd");
    }
}
