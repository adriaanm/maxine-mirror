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
 * Tests constant folding of array length operations.
 * @Harness: java
 * @Runs: 0=5; 1=6; 2=7; 3=8; 4=4
 */
public class ArrayLength01 {
    public static final int SIZE = 8;
    public static final byte[] arr = new byte[5];
    public static int test(int arg) {
        if (arg == 0) {
            return arr.length;
        }
        if (arg == 1) {
            return new byte[6].length;
        }
        if (arg == 2) {
            return new Object[7].length;
        }
        if (arg == 3) {
            return new Class[SIZE][].length;
        }
        if (arg == 4) {
            return new int[arg].length;
        }
        return 0;
    }
}
