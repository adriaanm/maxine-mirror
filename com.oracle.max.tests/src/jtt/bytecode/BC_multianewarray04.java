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
 * @Runs: 1 = 41; 2 = 81
 */
public class BC_multianewarray04 {
    public static int test(int a) {
        int i = 1;

        i += test_byte(a);
        i += test_boolean(a);
        i += test_char(a);
        i += test_short(a);
        i += test_int(a);
        i += test_float(a);
        i += test_long(a);
        i += test_double(a);

        return i;
    }

    private static int test_double(int a) {
        double[][] b2 = new double[a][a];
        double[][][] b3 = new double[a][a][a];
        double[][][][] b4 = new double[a][a][a][a];
        double[][][][][] b5 = new double[a][a][a][a][a];
        double[][][][][][] b6 = new double[a][a][a][a][a][a];
        return b2.length + b3.length + b4.length + b5.length + b6.length;
    }

    private static int test_long(int a) {
        long[][] b2 = new long[a][a];
        long[][][] b3 = new long[a][a][a];
        long[][][][] b4 = new long[a][a][a][a];
        long[][][][][] b5 = new long[a][a][a][a][a];
        long[][][][][][] b6 = new long[a][a][a][a][a][a];
        return b2.length + b3.length + b4.length + b5.length + b6.length;
    }

    private static int test_float(int a) {
        float[][] b2 = new float[a][a];
        float[][][] b3 = new float[a][a][a];
        float[][][][] b4 = new float[a][a][a][a];
        float[][][][][] b5 = new float[a][a][a][a][a];
        float[][][][][][] b6 = new float[a][a][a][a][a][a];
        return b2.length + b3.length + b4.length + b5.length + b6.length;
    }

    private static int test_int(int a) {
        int[][] b2 = new int[a][a];
        int[][][] b3 = new int[a][a][a];
        int[][][][] b4 = new int[a][a][a][a];
        int[][][][][] b5 = new int[a][a][a][a][a];
        int[][][][][][] b6 = new int[a][a][a][a][a][a];
        return b2.length + b3.length + b4.length + b5.length + b6.length;
    }

    private static int test_short(int a) {
        short[][] b2 = new short[a][a];
        short[][][] b3 = new short[a][a][a];
        short[][][][] b4 = new short[a][a][a][a];
        short[][][][][] b5 = new short[a][a][a][a][a];
        short[][][][][][] b6 = new short[a][a][a][a][a][a];
        return b2.length + b3.length + b4.length + b5.length + b6.length;
    }

    private static int test_char(int a) {
        char[][] b2 = new char[a][a];
        char[][][] b3 = new char[a][a][a];
        char[][][][] b4 = new char[a][a][a][a];
        char[][][][][] b5 = new char[a][a][a][a][a];
        char[][][][][][] b6 = new char[a][a][a][a][a][a];
        return b2.length + b3.length + b4.length + b5.length + b6.length;
    }

    private static int test_boolean(int a) {
        boolean[][] b2 = new boolean[a][a];
        boolean[][][] b3 = new boolean[a][a][a];
        boolean[][][][] b4 = new boolean[a][a][a][a];
        boolean[][][][][] b5 = new boolean[a][a][a][a][a];
        boolean[][][][][][] b6 = new boolean[a][a][a][a][a][a];
        return b2.length + b3.length + b4.length + b5.length + b6.length;
    }

    private static int test_byte(int a) {
        byte[][] b2 = new byte[a][a];
        byte[][][] b3 = new byte[a][a][a];
        byte[][][][] b4 = new byte[a][a][a][a];
        byte[][][][][] b5 = new byte[a][a][a][a][a];
        byte[][][][][][] b6 = new byte[a][a][a][a][a][a];
        return b2.length + b3.length + b4.length + b5.length + b6.length;
    }
}
