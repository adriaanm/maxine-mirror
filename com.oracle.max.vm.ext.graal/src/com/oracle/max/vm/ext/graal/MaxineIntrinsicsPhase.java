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
package com.oracle.max.vm.ext.graal;

import com.oracle.max.cri.intrinsics.*;
import com.oracle.max.graal.compiler.phases.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.java.*;
import com.oracle.max.vm.ext.maxri.*;
import com.sun.cri.ri.*;
import com.sun.max.vm.actor.member.*;


public class MaxineIntrinsicsPhase extends Phase {

    private final MaxRuntime runtime;

    public MaxineIntrinsicsPhase(MaxRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    protected void run(StructuredGraph graph) {
        for (Invoke invoke : graph.getInvokes()) {
            RiResolvedMethod method = invoke.callTarget().targetMethod();
            MethodActor methodActor = (MethodActor) method;
            if (methodActor.intrinsic() != null) {
                intrinsify(invoke, methodActor);
            }
        }
    }

    public void intrinsify(Invoke invoke, MethodActor method) {
        IntrinsicImpl impl = runtime.getIntrinsicRegistry().get(method);
        assert impl != null : method.intrinsic();
        if (impl != null) {
            ValueNode node = ((GraalIntrinsicImpl) impl).createGraph(invoke.callTarget().graph(), method, runtime, invoke.callTarget().arguments());
            invoke.intrinsify(node);
        }
    }
}
