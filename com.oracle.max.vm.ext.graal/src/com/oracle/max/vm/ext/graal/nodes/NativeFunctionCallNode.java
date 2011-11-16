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
package com.oracle.max.vm.ext.graal.nodes;

import com.oracle.max.graal.cri.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.extended.*;
import com.oracle.max.graal.nodes.spi.*;
import com.sun.cri.ri.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.compiler.*;

/**
 * Represents a call to a native function from within a native method stub.
 */
public final class NativeFunctionCallNode extends AbstractCallNode implements LIRLowerable, Lowerable {

    /**
     * The instruction that produces the native function address for this native call.
     */
    @Input private ValueNode address;

    /**
     * The native method for this native call.
     */
    public final RiResolvedMethod nativeMethod;

    public NativeFunctionCallNode(ValueNode address, ClassMethodActor nativeMethod) {
        super(WordUtil.ciKind(nativeMethod.descriptor().resultKind(), true), new ValueNode[0]);
        this.address = address;
        this.nativeMethod = nativeMethod;
    }

    @Override
    public void generate(LIRGeneratorTool generator) {
        // (ds) not sure what to put here...
    }

    @NodeIntrinsic
    public static Object call(@ConstantNodeParameter Address address, @ConstantNodeParameter ClassMethodActor nativeMethod) {
        throw new UnsupportedOperationException("This method may only be compiled with the Graal compiler");
    }

    @Override
    public void lower(CiLoweringTool tool) {
        // TODO Auto-generated method stub

    }
}
