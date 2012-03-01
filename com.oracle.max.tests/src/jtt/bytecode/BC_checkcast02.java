/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package jtt.bytecode;

/*
 * @Harness: java
 * @Runs: 0 = -1; 1 = -1; 2 = -1; 3 = -1
 */
public class BC_checkcast02 {

    static Object[] o1 = {new Object()};
    static String[] o2 = {""};
    static BC_checkcast02[] o3 = {new BC_checkcast02()};

    public static int test(int arg) {
        Object obj = null;
        if (arg == 0) {
            obj = o1;
        }
        if (arg == 1) {
            obj = o2;
        }
        if (arg == 2) {
            obj = o3;
        }
        Object[] r = (Object[]) obj;
        return r == null ? -1 : -1;
    }
}
