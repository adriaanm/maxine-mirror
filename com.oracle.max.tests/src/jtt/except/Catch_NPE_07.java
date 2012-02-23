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

package jtt.except;

/*
 * @Harness: java
 * @Runs: 0 = 0; 1 = 1
 */

public class Catch_NPE_07 {
    public static class MyThrowable extends Throwable {
    }

    public static int foo(Throwable t) {
        try {
            throw t;
        } catch (Throwable t1) {
            if (t1 == null) {
                return -1;
            }
            if (t1 instanceof NullPointerException) {
                return 0;
            }
            if (t1 instanceof MyThrowable) {
                return 1;
            }
            return -2;
        }
    }

    public static int test(int i) {
        Throwable t = (i == 0) ? null : new MyThrowable();
        try {
            return foo(t);
        } catch (Throwable t1) {
            return -3;
        }
    }
}
