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
package jtt.except;

/*
 * @Harness: java
 * @Runs: 0 = 0; 2 = -100; 3 = -100; 4 = 4
 */
public class StackTrace_CCE_00 {
    static Object object2 = new Object();
    static Object object3 = "";
    static Object object4 = new StackTrace_CCE_00();

    public static int test(int arg) {
        Object obj = null;
        if (arg == 2) {
            obj = object2;
        }
        if (arg == 3) {
            obj = object3;
        }
        if (arg == 4) {
            obj = object4;
        }
        try {
            final StackTrace_CCE_00 bc = (StackTrace_CCE_00) obj;
            if (bc == null) {
                return arg;
            }
            return arg;
        } catch (ClassCastException npe) {
            for (StackTraceElement e : npe.getStackTrace()) {
                if (e.getClassName().equals(StackTrace_CCE_00.class.getName()) && e.getMethodName().equals("test")) {
                    return -100;
                }
            }
            return -200;
        }
    }
}
