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
package jtt.reflect;

import sun.reflect.*;

/*
 * @Harness: java
 * @Runs: 0 = "sun.reflect.Reflection"; 1 = "jtt.reflect.Reflection_getCallerClass01$Caller1"; 2 = "jtt.reflect.Reflection_getCallerClass01$Caller2"
 */
public final class Reflection_getCallerClass01 {
    private Reflection_getCallerClass01() {
    }

    public static final class Caller1 {
        private Caller1() {
        }

        static String caller1(int depth) {
            return Reflection.getCallerClass(depth).getName();
        }
    }

    public static final class Caller2 {
        private Caller2() {
        }

        static String caller2(int depth) {
            return Caller1.caller1(depth);
        }
    }

    public static String test(int depth) {
        return Caller2.caller2(depth);
    }
}
