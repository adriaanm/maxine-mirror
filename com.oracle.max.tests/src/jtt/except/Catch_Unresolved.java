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
 * @Runs: 0 = 0; 1 = 1; 2 = 2
 */
public class Catch_Unresolved {

    public static boolean executed;

    public static int test(int arg) {
        executed = false;
        try {
            helper1(arg);
            helper2(arg);
        } catch(Catch_Unresolved_Exception1 e) {
            return 1;
        } catch (Catch_Unresolved_Exception2 e) {
            return 2;
        }
        return 0;
    }

    private static void helper1(int arg) {
        if (executed) {
            throw new IllegalStateException("helper1 may only be called once");
        }
        executed = true;
        if (arg == 1) {
            throw new Catch_Unresolved_Exception1();
        } else if (arg == 2) {
            throw new Catch_Unresolved_Exception2();
        }
    }

    private static void helper2(int arg) {
        if (arg != 0) {
            throw new IllegalStateException("helper2 can only be called if arg==0");
        }
    }
}

class Catch_Unresolved_Exception1 extends RuntimeException {
}

class Catch_Unresolved_Exception2 extends RuntimeException {
}
