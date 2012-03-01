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

package jtt.micro;

/*
 * @Harness: java
 * @Runs: (0, 1L, 2L, 3L, 4L, 5L, 6L, 7L, -8L, -9L) = 1L;
 * @Runs: (1, 1L, 2L, 3L, 4L, 5L, 6L, 7L, -8L, -9L) = 2L;
 * @Runs: (2, 1L, 2L, 3L, 4L, 5L, 6L, 7L, -8L, -9L) = 3L;
 * @Runs: (3, 1L, 2L, 3L, 4L, 5L, 6L, 7L, -8L, -9L) = 4L;
 * @Runs: (4, 1L, 2L, 3L, 4L, 5L, 6L, 7L, -8L, -9L) = 5L;
 * @Runs: (5, 1L, 2L, 3L, 4L, 5L, 6L, 7L, -8L, -9L) = 6L;
 * @Runs: (6, 1L, 2L, 3L, 4L, 5L, 6L, 7L, -8L, -9L) = 7L;
 * @Runs: (7, 1L, 2L, 3L, 4L, 5L, 6L, 7L, -8L, -9L) = -8L;
 * @Runs: (8, 1L, 2L, 3L, 4L, 5L, 6L, 7L, -8L, -9L) = -9L
 */
public class BigLongParams02 {

    public static long test(int choice, long p0, long p1, long p2, long p3, long p4, long p5, long p6, long p7, long p8) {
        switch (choice) {
            case 0:
                return p0;
            case 1:
                return p1;
            case 2:
                return p2;
            case 3:
                return p3;
            case 4:
                return p4;
            case 5:
                return p5;
            case 6:
                return p6;
            case 7:
                return p7;
            case 8:
                return p8;
        }
        return 42;
    }
}
