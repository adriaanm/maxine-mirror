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
package com.oracle.max.graal.nodes;

import java.util.*;

import com.oracle.max.graal.nodes.calc.*;
import com.oracle.max.graal.nodes.spi.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code Local} instruction is a placeholder for an incoming argument
 * to a function call.
 */

public final class LocalNode extends FloatingNode {

    @Input private EntryPointNode entryPoint;

    public EntryPointNode entryPoint() {
        return entryPoint;
    }

    @Data private final int index;
    @Data private RiType declaredType;
    @Data private boolean canBeNull;


    public LocalNode(CiKind kind, int javaIndex, EntryPointNode entryPoint) {
        this(kind, javaIndex, entryPoint, true);
    }

    public LocalNode(CiKind kind, int javaIndex, EntryPointNode entryPoint, boolean canBeNull) {
        super(kind);
        this.index = javaIndex;
        this.entryPoint = entryPoint;
        this.canBeNull = canBeNull;
    }

    /**
     * Gets the index of this local in the array of parameters. This is NOT the JVM local index.
     * @return the index
     */
    public int index() {
        return index;
    }

    /**
     * Sets the declared type of this local, e.g. derived from the signature of the method.
     * @param declaredType the declared type of the local variable
     */
    public void setDeclaredType(RiType declaredType) {
        this.declaredType = declaredType;
    }

    @Override
    public RiType declaredType() {
        return declaredType;
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitLocal(this);
    }

    @Override
    public Map<Object, Object> getDebugProperties() {
        Map<Object, Object> properties = super.getDebugProperties();
        properties.put("index", index());
        return properties;
    }

    public boolean canBeNull() {
        return canBeNull;
    }

    @Override
    public String toString() {
        return super.toString() + " (local " + index() + ")";
    }
}
