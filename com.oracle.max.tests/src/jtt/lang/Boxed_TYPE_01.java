/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
package jtt.lang;

/*
 * @Harness: java
 * @Runs: 0 = "boolean"; 1 = "byte"; 2 = "char"; 3 = "double"; 4 = "float"; 5 = "int"; 6 = "long"; 7 = "short"; 8 = "void";
 */
public class Boxed_TYPE_01 {
    public static String test(int i) {
        if (i == 0) {
            return Boolean.TYPE.getName();
        }
        if (i == 1) {
            return Byte.TYPE.getName();
        }
        if (i == 2) {
            return Character.TYPE.getName();
        }
        if (i == 3) {
            return Double.TYPE.getName();
        }
        if (i == 4) {
            return Float.TYPE.getName();
        }
        if (i == 5) {
            return Integer.TYPE.getName();
        }
        if (i == 6) {
            return Long.TYPE.getName();
        }
        if (i == 7) {
            return Short.TYPE.getName();
        }
        if (i == 8) {
            return Void.TYPE.getName();
        }
        return null;
    }
}
