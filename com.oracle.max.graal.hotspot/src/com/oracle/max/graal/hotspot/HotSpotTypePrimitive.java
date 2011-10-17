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
package com.oracle.max.graal.hotspot;

import com.oracle.max.graal.compiler.util.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * Implementation of RiType for primitive HotSpot types.
 */
public final class HotSpotTypePrimitive extends HotSpotType {

    private CiKind kind;


    HotSpotTypePrimitive(Compiler compiler, CiKind kind) {
        super(compiler);
        this.kind = kind;
        this.name = kind.toString();
    }

    @Override
    public int accessFlags() {
        return kind.toJavaClass().getModifiers();
    }

    @Override
    public RiType arrayOf() {
        return compiler.getVMEntries().getPrimitiveArrayType(kind);
    }

    @Override
    public RiType componentType() {
        return null;
    }

    @Override
    public RiType exactType() {
        return this;
    }

    @Override
    public RiType superType() {
        return null;
    }

    @Override
    public CiConstant getEncoding(Representation r) {
        throw Util.unimplemented("HotSpotTypePrimitive.getEncoding");
    }

    @Override
    public CiKind getRepresentationKind(Representation r) {
        return kind;
    }

    @Override
    public boolean hasFinalizableSubclass() {
        return false;
    }

    @Override
    public boolean hasFinalizer() {
        return false;
    }

    @Override
    public boolean hasSubclass() {
        return false;
    }

    @Override
    public boolean isArrayClass() {
        return false;
    }

    @Override
    public boolean isInitialized() {
        return true;
    }

    @Override
    public boolean isInstance(CiConstant obj) {
        return false;
    }

    @Override
    public boolean isInstanceClass() {
        return false;
    }

    @Override
    public boolean isInterface() {
        return false;
    }

    @Override
    public boolean isResolved() {
        return true;
    }

    @Override
    public boolean isSubtypeOf(RiType other) {
        return false;
    }

    @Override
    public CiKind kind(boolean architecture) {
        return kind;
    }

    @Override
    public RiMethod resolveMethodImpl(RiMethod method) {
        return null;
    }

    @Override
    public String toString() {
        return "HotSpotTypePrimitive<" + kind + ">";
    }

    @Override
    public RiType uniqueConcreteSubtype() {
        return this;
    }

    @Override
    public RiMethod uniqueConcreteMethod(RiMethod method) {
        return null;
    }

    @Override
    public RiField[] declaredFields() {
        return null;
    }

    @Override
    public Class< ? > toJava() {
        return kind.toJavaClass();
    }
}
