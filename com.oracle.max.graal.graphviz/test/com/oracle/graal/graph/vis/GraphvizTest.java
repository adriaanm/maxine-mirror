/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.graph.vis;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.junit.Test;

import com.oracle.max.graal.graph.Graph;
import com.oracle.max.graal.graph.Node;
import com.oracle.max.graal.graph.NodeInputList;
import com.oracle.max.graal.graph.NodeSuccessorList;
import com.oracle.max.graal.graphviz.GraphvizPrinter;
import com.oracle.max.graal.graphviz.GraphvizRunner;

/**
 * Tests for the Graphviz graph generator. Needs Graphviz (more specifically, dot) installed to verify produced output.
 */
public class GraphvizTest {

    @Test
    public void testSimpleGraph() throws IOException {
        Graph<DummyNode> g = new Graph<DummyNode>(new DummyNode("start", 0, 1));

        DummyNode start = g.start();

        DummyNode ifnode = g.add(new DummyNode("if", 2, 2));
        start.setSuccessor(0, ifnode);

        // branch 1
        DummyNode nop = g.add(new DummyNode("nop", 0, 1));
        ifnode.setSuccessor(0, nop);

        // branch 2
        DummyNode a = g.add(new DummyNode("a", 0, 1));
        DummyNode b = g.add(new DummyNode("b", 0, 1));
        DummyNode plus = g.add(new DummyNode("+", 2, 1));
        plus.setInput(0, a);
        plus.setInput(1, b);
        ifnode.setSuccessor(1, plus);

        DummyNode end = g.add(new DummyNode("end", 0, 1));
        plus.setSuccessor(0, end);
        nop.setSuccessor(0, end);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GraphvizPrinter printer = new GraphvizPrinter(out);
        printer.begin("Simple test");
        printer.print(g, false);
        printer.end();

        int exitCode = GraphvizRunner.process(GraphvizRunner.DOT_LAYOUT, new ByteArrayInputStream(out.toByteArray()), new NullOutputStream(), "xdot");
        assertEquals(0, exitCode);
    }

    private static class DummyNode extends Node {

        @Input private final NodeInputList<Node> inputs;

        @Successor private final NodeSuccessorList<Node> successors;

        public DummyNode(String name, int inputCount, int successorCount) {
            super();
            this.name = name;
            inputs = new NodeInputList<Node>(this, inputCount);
            successors = new NodeSuccessorList<Node>(this, successorCount);
        }

        public void setInput(int idx, Node n) {
            inputs.set(idx, n);
        }

        public void setSuccessor(int idx, Node n) {
            successors.set(idx, n);
        }

        private final String name;

        @Override
        public String toString(Verbosity verbosity) {
            if (verbosity == Verbosity.Long) {
                return name;
            } else {
                return super.toString(verbosity);
            }
        }
    }

    private static class NullOutputStream extends OutputStream {

        @Override
        public void write(int b) throws IOException {
        }
    }

}
