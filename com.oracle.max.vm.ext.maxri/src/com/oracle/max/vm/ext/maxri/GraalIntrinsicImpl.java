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
package com.oracle.max.vm.ext.maxri;

import java.lang.reflect.*;

import com.oracle.max.cri.intrinsics.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;
import com.sun.max.annotate.*;

/**
 * Base class for intrinsic implementations targeting Graal. The intrinsic has access to the graph so that
 * it can append new instructions to the instruction stream.
 */
public class GraalIntrinsicImpl implements IntrinsicImpl {
    /**
     * The name of the method called to actually create the graph.  This method is found using reflection.
     */
    public final String CREATE_NAME = "create";

    private Method createMethod;

    /**
     * Creates the graph nodes necessary for the implementation of the intrinsic and appends them to the supplied {@link StructuredGraph}.
     * <br>
     * This default implementation searches for a method with the name "create" and the following parameter list:
     * <pre>
     *     ValueNode create([StructureGraph], [RiResolvedMethod], [RiRuntime], { ValueNode | any_type })
     * </pre>
     * This means that that the graph, method, and runtime parameters are optionally given to the called method.  The args parameter
     * is flattened from the NodeList to individual ValueNode parameters.  If the parameter has any other type, then the
     * node must be a constant, and the constant value is passed instead of the node.
     * <br>
     * Subclasses can also override this method if they want to avoid the reflective method invocation done in this implementation.
     *
     * @param graph The graph that the intrinsic will be created into.
     * @param method The intrinsic method, i.e., the method that has the {@link INTRINSIC} annotation.
     * @param runtime The RiRuntime, used to get information about types, etc.
     * @param args The arguments of the intrinsic methods, to be used as the parameters of the intrinsic instruction.
     * @return The instruction that should substitute the original method call that is intrinsified.
     */
    public ValueNode createGraph(StructuredGraph graph, RiResolvedMethod method, RiRuntime runtime, NodeList<ValueNode> args) {
        if (createMethod == null) {
            initialize();
        }

        Class[] formalParams = createMethod.getParameterTypes();
        Object[] actualParams = new Object[formalParams.length];

        int offset = 0;
        offset = assignParam(offset, formalParams, actualParams, StructuredGraph.class, graph);
        offset = assignParam(offset, formalParams, actualParams, RiResolvedMethod.class, method);
        offset = assignParam(offset, formalParams, actualParams, RiRuntime.class, runtime);

        if (offset + args.size() != actualParams.length) {
            throw new CiBailout("intrinsic has wrong number of parameters for invoke " + method);
        }

        for (int i = offset; i < actualParams.length; i++) {
            ValueNode node = args.get(i - offset);
            if (formalParams[i] != ValueNode.class) {
                if (!node.isConstant()) {
                    throw new CiBailout("intrinsic parameter " + (i - offset) + " must be compile time constant for invoke " + method);
                }
                actualParams[i] = node.asConstant().boxedValue();
            } else {
                actualParams[i] = node;
            }
        }

        try {
            return (ValueNode) createMethod.invoke(this, actualParams);
        } catch (Exception ex) {
            throw new CiBailout("intrinsic exception for invoke " + method, ex);
        }
    }

    private int assignParam(int offset, Class[] formalParams, Object[] actualParams, Class paramClass, Object paramValue) {
        if (offset < formalParams.length && formalParams[offset] == paramClass) {
            actualParams[offset] = paramValue;
            offset++;
        }
        return offset;
    }

    private void initialize() {
        int count = 0;
        for (Method m : getClass().getMethods()) {
            if (CREATE_NAME.equals(m.getName()) && m.getReturnType() == ValueNode.class) {
                createMethod = m;
                count++;
            }
        }
        if (count != 1) {
            throw new CiBailout("Expected one create method, but found " + count);
        }
    }
}
