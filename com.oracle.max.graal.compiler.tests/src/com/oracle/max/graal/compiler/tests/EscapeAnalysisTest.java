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

import junit.framework.Assert;

import org.junit.Test;

import com.oracle.max.graal.compiler.phases.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.java.*;
import com.sun.cri.ci.*;

/**
 * In these test cases the probability of all invokes is set to a high value, such that an InliningPhase should inline them all.
 * After that, the EscapeAnalysisPhase is expected to remove all allocations and return the correct values.
 */
public class EscapeAnalysisTest extends GraphTest {

    @Test
    public void test1() {
        test("test1Snippet", CiConstant.forInt(101));
    }

    public static int test1Snippet(int a) {
        Integer x = new Integer(101);
        return x.intValue();
    }

    @Test
    public void test2() {
        test("test2Snippet", CiConstant.forInt(0));
    }

    public static int test2Snippet(int a) {
        Integer[] x = new Integer[0];
        return x.length;
    }

    @Test
    public void test3() {
        test("test3Snippet", CiConstant.forObject(null));
    }

    public static Object test3Snippet(int a) {
        Integer[] x = new Integer[1];
        return x[0];
    }

    @SuppressWarnings("unused")
    private void test(String snippet, CiConstant expectedResult) {
        StructuredGraph graph = parse(snippet);
        for (Invoke n : graph.getInvokes()) {
            n.node().setProbability(100000);
        }

        new InliningPhase(null, runtime(), null, null, PhasePlan.DEFAULT).apply(graph);
        new DeadCodeEliminationPhase().apply(graph);
        print(graph);
        new EscapeAnalysisPhase(null, runtime(), null, PhasePlan.DEFAULT).apply(graph);
        int retCount = 0;
        for (ReturnNode ret : graph.getNodes(ReturnNode.class)) {
            Assert.assertTrue(ret.result().isConstant());
            Assert.assertEquals(ret.result().asConstant(), expectedResult);
            retCount++;
        }
        Assert.assertEquals(1, retCount);
        int newInstanceCount = 0;
        for (NewInstanceNode n : graph.getNodes(NewInstanceNode.class)) {
            newInstanceCount++;
        }
        for (NewObjectArrayNode n : graph.getNodes(NewObjectArrayNode.class)) {
            newInstanceCount++;
        }
        Assert.assertEquals(0, newInstanceCount);
    }
}
