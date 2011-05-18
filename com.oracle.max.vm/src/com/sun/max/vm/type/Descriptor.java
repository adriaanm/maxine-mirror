/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.vm.type;

import com.sun.max.annotate.*;

/**
 * String descriptions of Java types and signatures, see #4.3.
 */
public abstract class Descriptor implements Comparable<Descriptor> {

    @INSPECTED
    public final String string;

    public static String dottified(String className) {
        return className.replace('/', '.');
    }

    public static String slashified(String className) {
        return className.replace('.', '/');
    }

    protected Descriptor(String value) {
        string = slashified(value);
    }

    @Override
    public final String toString() {
        return string;
    }

    @Override
    public final boolean equals(Object other) {
        return other == this;
    }

    @Override
    public final int hashCode() {
        return string.hashCode();
    }

    public final int compareTo(Descriptor other) {
        return string.compareTo(other.string);
    }

}
