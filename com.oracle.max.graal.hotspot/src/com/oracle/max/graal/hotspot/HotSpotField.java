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

import java.lang.annotation.*;
import java.lang.reflect.*;

import com.oracle.max.graal.compiler.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * Represents a field in a HotSpot type.
 */
public class HotSpotField extends CompilerObject implements RiResolvedField {

    private final RiResolvedType holder;
    private final String name;
    private final RiType type;
    private final int offset;
    private final int accessFlags;
    private CiConstant constant;

    public HotSpotField(Compiler compiler, RiResolvedType holder, String name, RiType type, int offset, int accessFlags) {
        super(compiler);
        this.holder = holder;
        this.name = name;
        this.type = type;
        assert offset != -1;
        this.offset = offset;
        this.accessFlags = accessFlags;
    }

    @Override
    public int accessFlags() {
        return accessFlags;
    }

    @Override
    public CiConstant constantValue(CiConstant receiver) {
        if (receiver == null) {
            if (constant == null && holder.isSubtypeOf((RiResolvedType) compiler.getVMEntries().getType(GraalOptions.class))) {
                Field f;
                try {
                    f = GraalOptions.class.getField(name);
                    f.setAccessible(true);
                    if (Modifier.isStatic(f.getModifiers())) {
                        if (f.getType() == int.class) {
                            constant = CiConstant.forInt(f.getInt(null));
                        } else if (f.getType() == float.class) {
                            constant = CiConstant.forFloat(f.getFloat(null));
                        } else if (f.getType() == boolean.class) {
                            constant = CiConstant.forBoolean(f.getBoolean(null));
                        } else if (!f.getType().isPrimitive()) {
                            constant = CiConstant.forObject(f.get(null));
                        } else {
                            assert false : "unsupported type";
                        }
                    }
                } catch (Exception ex) {
                    return null;
                }
            }

            // Constant part only valid for static fields.
            return constant;
        }
        return null;
    }

    @Override
    public RiResolvedType holder() {
        return holder;
    }

    @Override
    public CiKind kind(boolean architecture) {
        return type().kind(architecture);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public RiType type() {
        return type;
    }

    public int offset() {
        return offset;
    }

    @Override
    public String toString() {
        return "HotSpotField<" + CiUtil.format("%h.%n", this, false) + ":" + offset + ">";
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        Field javaField = toJava();
        if (javaField != null) {
            return javaField.getAnnotation(annotationClass);
        }
        return null;
    }

    private Field toJava() {
        try {
            return holder.toJava().getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }
}
