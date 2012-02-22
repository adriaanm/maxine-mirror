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
 * @Runs: 7 = 7
 */
package jtt.lang;

import java.util.*;

public final class ProcessEnvironment_init {

    private ProcessEnvironment_init() {
    }

    private static HashMap<Object, Object> theEnvironment;
    private static Map<Object, Object> theUnmodifiableEnvironment;

    public static int test(int v) {

        byte[][] environ = environ();
        theEnvironment = new HashMap<Object, Object>(environ.length / 2 + 3);

        for (int i = environ.length - 1; i > 0; i -= 2) {
            theEnvironment.put(Variable.valueOf(environ[i - 1]), Value.valueOf(environ[i]));
        }

        theUnmodifiableEnvironment = Collections.unmodifiableMap(new StringEnvironment(theEnvironment));

        return v;
    }

    private static final class StringEnvironment extends HashMap<Object, Object> {

        public StringEnvironment(HashMap<Object, Object> theenvironment) {
        }
    }

    private static final class Variable {

        public static Object valueOf(byte[] bs) {
            return new Object();
        }
    }

    private static final class Value {

        public static Object valueOf(byte[] bs) {
            return new Object();
        }
    }

    private static byte[][] environ() {
        return new byte[3][3];
    }
}
