/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package jtt.loop;


/*
 * This test is meaningful only if you run it with 'forced' inlinning because running it in the harness with -Xcomp will not trigger any normal inlining
 * @Harness: java
 * @Runs: 0 = 0; 10 = 402;
 */
public class LoopInline {

    public static int test(int arg) {
        int count = 0;
        for (int i = 0; i < arg; i++) {
            count += foo(i);
            if (count > 15) {
                count -= foo(3);
                break;
            }
        }
        return count;
    }

    public static int foo(int t) {
        int sum = 0;
        for (int i = 0; i < t; i++) {
            sum += i;
            if (i == 4) {
                sum += foo2(sum);
                break;
            }
        }
        return sum;
    }

    public static int foo2(int j) {
        int sum = 0;
        while (j > 0) {
            sum += j*j;
            j--;
        }
        return sum;
    }
}
