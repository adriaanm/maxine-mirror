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
package com.oracle.max.graal.nodes.java;

import java.util.*;

import com.oracle.max.graal.nodes.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

public class MethodCallTargetNode extends CallTargetNode {
    public enum InvokeKind {
        Interface,
        Special,
        Static,
        Virtual
    }

    @Data private final RiType returnType;
    @Data private final RiResolvedMethod targetMethod;
    @Data private final InvokeKind invokeKind;
    /**
     * @param arguments
     */
    public MethodCallTargetNode(InvokeKind invokeKind, RiResolvedMethod targetMethod, ValueNode[] arguments, RiType returnType) {
        super(arguments);
        this.invokeKind = invokeKind;
        this.returnType = returnType;
        this.targetMethod = targetMethod;
    }

    @Override
    public RiType returnType() {
        return returnType;
    }

    /**
     * Gets the target method for this invocation instruction.
     * @return the target method
     */
    public RiResolvedMethod targetMethod() {
        return targetMethod;
    }

    public InvokeKind invokeKind() {
        return invokeKind;
    }

    /**
     * Gets the instruction that produces the receiver object for this invocation, if any.
     * @return the instruction that produces the receiver object for this invocation if any, {@code null} if this
     *         invocation does not take a receiver object
     */
    public ValueNode receiver() {
        assert !isStatic();
        return arguments().get(0);
    }

    /**
     * Checks whether this is an invocation of a static method.
     * @return {@code true} if the invocation is a static invocation
     */
    public boolean isStatic() {
        return invokeKind() == InvokeKind.Static;
    }

    @Override
    public CiKind returnKind() {
        return targetMethod().signature().returnKind(false);
    }

    public Collection<InvokeNode> invokes() {
        return ValueUtil.filter(this.usages(), InvokeNode.class);
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Long) {
            return super.toString(Verbosity.Short) + "(" + targetMethod() + ")";
        } else {
            return super.toString(verbosity);
        }
    }
}
