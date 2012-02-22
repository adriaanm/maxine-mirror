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
package jtt.optimize;

/*
 * Tests constant folding of integer operations.
 * @Harness: java
 * @Runs: 0=18; 1=18; 2=!java.lang.NullPointerException
 */
public class VN_Cast01 {
    static final Object object = new VN_Cast01();

    int field = 9;

    public static int test(int arg) {
        if (arg == 0) {
            return test1();
        }
        if (arg == 1) {
            return test2();
        }
        if (arg == 2) {
            return test3();
        }
        return 0;
    }

    private static int test1() {
        Object o = object;
        VN_Cast01 a = (VN_Cast01) o;
        VN_Cast01 b = (VN_Cast01) o;
        return a.field + b.field;
    }

    private static int test2() {
        Object obj = new VN_Cast01();
        VN_Cast01 a = (VN_Cast01) obj;
        VN_Cast01 b = (VN_Cast01) obj;
        return a.field + b.field;
    }

    @SuppressWarnings("all")
    private static int test3() {
        Object o = null;
        VN_Cast01 a = (VN_Cast01) o;
        VN_Cast01 b = (VN_Cast01) o;
        return a.field + b.field;
    }
}
