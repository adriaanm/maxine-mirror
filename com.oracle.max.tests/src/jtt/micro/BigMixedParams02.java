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
/*
 * Copyright (c) 2007 Sun Microsystems, Inc. All rights reserved. Use is subject to license terms.
 */
package jtt.micro;

/*
 * @Harness: java
 * @Runs: (0, -1, -1, -1, -1, 1f, 2f, 3f, 4f, -1, -1, 5f, 6f, 7f, 8f, 9f) = 1f;
 * @Runs: (1, -1, -1, -1, -1, 1f, 2f, 3f, 4f, -1, -1, 5f, 6f, 7f, 8f, 9f) = 2f;
 * @Runs: (2, -1, -1, -1, -1, 1f, 2f, 3f, 4f, -1, -1, 5f, 6f, 7f, 8f, 9f) = 3f;
 * @Runs: (3, -1, -1, -1, -1, 1f, 2f, 3f, 4f, -1, -1, 5f, 6f, 7f, 8f, 9f) = 4f;
 * @Runs: (4, -1, -1, -1, -1, 1f, 2f, 3f, 4f, -1, -1, 5f, 6f, 7f, 8f, 9f) = 5f;
 * @Runs: (5, -1, -1, -1, -1, 1f, 2f, 3f, 4f, -1, -1, 5f, 6f, 7f, 8f, 9f) = 6f;
 * @Runs: (6, -1, -1, -1, -1, 1f, 2f, 3f, 4f, -1, -1, 5f, 6f, 7f, 8f, 9f) = 7f;
 * @Runs: (7, -1, -1, -1, -1, 1f, 2f, 3f, 4f, -1, -1, 5f, 6f, 7f, 8f, 9f) = 8f;
 * @Runs: (8, -1, -1, -1, -1, 1f, 2f, 3f, 4f, -1, -1, 5f, 6f, 7f, 8f, 9f) = 9f;
 */
public class BigMixedParams02 {

    public static float test(int choice, int i0, int i1, int i2, int i3, float p0, float p1, float p2, float p3, int i4, int i5, float p4, float p5, float p6, float p7, float p8) {
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
