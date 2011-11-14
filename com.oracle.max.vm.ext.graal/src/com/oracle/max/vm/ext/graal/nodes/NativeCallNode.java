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

import static com.sun.cri.ci.CiCallingConvention.Type.*;

import java.util.*;

import com.oracle.max.graal.compiler.gen.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.extended.*;
import com.oracle.max.graal.nodes.spi.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * Represents a call to a native function from within a native method stub.
 */
public final class NativeCallNode extends AbstractCallNode implements LIRLowerable {

    /**
     * The instruction that produces the native function address for this native call.
     */
    @Input private ValueNode address;

    /**
     * The native method for this native call.
     */
    public final RiResolvedMethod nativeMethod;

    public final CiKind[] signature;

    public NativeCallNode(ValueNode address, ValueNode[] arguments, CiKind returnKind, RiResolvedMethod nativeMethod) {
        super(returnKind, arguments);
        this.nativeMethod = nativeMethod;
        this.address = address;
        this.signature = new CiKind[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            signature[i] = arguments[i].kind();
        }
    }

    public ValueNode address() {
        return address;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        LIRGenerator lir = (LIRGenerator) gen;
        LIRDebugInfo info = new LIRDebugInfo(this.stateAfter());
        CiValue resultOperand = lir.resultOperandFor(this.kind());
        CiValue callAddress = lir.operand(this.address());
        CiCallingConvention cc = lir.compilation.registerConfig.getCallingConvention(NativeCall, signature, lir.target(), false);
        lir.compilation.frameMap().adjustOutgoingStackSize(cc, NativeCall);

        List<CiValue> argList = lir.visitInvokeArguments(cc, this.arguments(), null);
        argList.add(callAddress);

            // Indirect call
        String target = this.nativeMethod.jniSymbol();
        lir.append(StandardOpcode.INDIRECT_CALL.create(target, resultOperand, argList, callAddress, info, null, null));

        if (resultOperand.isLegal()) {
            lir.setResult(this, lir.emitMove(resultOperand));
        }
    }
}
