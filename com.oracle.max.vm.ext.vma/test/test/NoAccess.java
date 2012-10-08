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
package test;

/**
 * A test that creates objects and passes them around, compare them but never reads or writes
 * any part of the object (user fields or meta-data).
 *
 */
public class NoAccess {

    private static class A {

    }

    public static void main(String[] args) {
        A[] aa = new A[100];
        for (int a = 0; a < aa.length; a++) {
            aa[a] = new A();
        }

        for (int n = 0; n < 100; n++) {
            for (int i = 0; i < aa.length - 1; i++) {
                @SuppressWarnings("unused")
                A r = choose(aa[i], aa[i + 1]);
            }
        }
    }

    private static A choose(A a1, A a2) {
        return doChoose(a2, a1);
    }

    private static A doChoose(A a1, A a2) {
        if (a1 == a2) {
            return a1;
        } else {
            return a2;
        }
    }

}
