/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.tests;

import org.junit.*;

import com.oracle.max.graal.compiler.loop.*;
import com.oracle.max.graal.nodes.*;

/**
 * In the following tests, the usages of local variable "a" are replaced with the integer constant 0.
 * Then canonicalization is applied and it is verified that the resulting graph is equal to the
 * graph of the method that just has a "return 1" statement in it.
 */
public class NestedLoopTest extends GraphTest {

    @Test
    public void test1() {
        test("test1Snippet");
    }

    public static void test1Snippet(int a) {
        while (test()) {
            m1: while (test()) {
                while (test()) {
                    if (test()) {
                        break m1;
                    }
                }
            }
        }
    }

    private static boolean test() {
        return false;
    }

    private void test(String snippet) {
        StructuredGraph graph = parse(snippet);
        print(graph);

        LoopInfo loopInfo = LoopUtil.computeLoopInfo(graph);
        loopInfo.print();

        print(graph);
    }
}
