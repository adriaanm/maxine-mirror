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
/*
 * @Harness: java
 * @Runs: 0 = 0; 1 = 2
 */
package jtt.except;

public class Except_Synchronized05 {

    Object field;

    public static int test(int arg) {
        Except_Synchronized05 obj = new Except_Synchronized05();
        int a = obj.bar(arg) != null ? 1 : 0;
        int b = obj.baz(arg) != null ? 1 : 0;
        return a + b;
    }

    public synchronized Object bar(int arg) {
        try {
            String f = foo1(arg);
            if (f == null) {
                field = new Object();
            }
        } catch (NullPointerException e) {
            // do nothing
        }
        return field;
    }

    public Object baz(int arg) {
        synchronized (this) {
            try {
                String f = foo1(arg);
                if (f == null) {
                    field = new Object();
                }
            } catch (NullPointerException e) {
                // do nothing
            }
            return field;
        }
    }

    private String foo1(int arg) {
        if (arg == 0) {
            throw null;
        }
        return null;
    }
}
