/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
package jtt.exbytecode;

//register -> memory

/*
 * @Harness: java
 * @Runs: `java.lang.Double.NaN = 0x7ff8000000000000L; 1.0d = 0x3ff0000000000000L; -1.0d = -4616189618054758400L; 473729.5945321d = 4691882224927966680L
*/
public class EBC_movd2l_03 {
    static class L {
        long l;
    }

    public static long test(double arg) {
        return doTest(new L(), arg);
    }

    private static long doTest(L l, double arg) {
        l.l = Double.doubleToRawLongBits(arg);
        return l.l;
    }
}
