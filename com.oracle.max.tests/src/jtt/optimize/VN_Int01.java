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
 * Tests value numbering of integer operations.
 * @Harness: java
 * @Runs: 0=6; 1=0; 2=36; 3=1; 4=0; 5=5; 6=7; 7=0
 */
public class VN_Int01 {
    public static int test(int arg) {
        if (arg == 0) {
            return add(arg);
        }
        if (arg == 1) {
            return sub(arg);
        }
        if (arg == 2) {
            return mul(arg);
        }
        if (arg == 3) {
            return div(arg);
        }
        if (arg == 4) {
            return mod(arg);
        }
        if (arg == 5) {
            return and(arg);
        }
        if (arg == 6) {
            return or(arg);
        }
        if (arg == 7) {
            return xor(arg);
        }
        return 0;
    }
    public static int add(int x) {
        int c = 3;
        int t = x + c;
        int u = x + c;
        return t + u;
    }
    public static int sub(int x) {
        int c = 3;
        int t = x - c;
        int u = x - c;
        return t - u;
    }
    public static int mul(int x) {
        int i = 3;
        int t = x * i;
        int u = x * i;
        return t * u;
    }
    public static int div(int x) {
        int i = 9;
        int t = i / x;
        int u = i / x;
        return t / u;
    }
    public static int mod(int x) {
        int i = 7;
        int t = i % x;
        int u = i % x;
        return t % u;
    }
    public static int and(int x) {
        int i = 7;
        int t = i & x;
        int u = i & x;
        return t & u;
    }
    public static int or(int x) {
        int i = 7;
        int t = i | x;
        int u = i | x;
        return t | u;
    }
    public static int xor(int x) {
        int i = 7;
        int t = i ^ x;
        int u = i ^ x;
        return t ^ u;
    }
}
