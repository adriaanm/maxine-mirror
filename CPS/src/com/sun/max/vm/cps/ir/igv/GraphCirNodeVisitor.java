/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.max.vm.cps.ir.igv;

import com.sun.max.vm.cps.cir.*;
import com.sun.max.vm.cps.cir.transform.*;
import com.sun.max.vm.cps.cir.variable.*;

/**
 * Creates nodes and edges for CirNode objects.
 *
 * @author Thomas Wuerthinger
 */
class GraphCirNodeVisitor extends CirTraversal {

    private GraphWriter.Graph graph;

    GraphCirNodeVisitor(GraphWriter.Graph graph, CirNode node) {
        super(node);
        this.graph = graph;
    }

    @Override
    public void visitNode(CirNode n) {
        if (graph.getNode(n.id()) == null) {
            final GraphWriter.Node inputNode = graph.createNode(n.id());
            inputNode.getProperties().setProperty("name", n.toString());
            inputNode.getProperties().setProperty("class", n.getClass().getName());
            inputNode.getProperties().setProperty("dump_spec", n.toString());
            inputNode.getProperties().setProperty("short_name", n.toString());
        }

        assert graph.getNode(n.id()) != null;
        graph.getNode(n.id()).getProperties().setProperty("type", "Node");
    }

    @Override
    public void visitBlock(CirBlock block) {
        super.visitBlock(block);
        visitNode(block);
        final CirClosure closure = block.closure();
        graph.createEdge(block.id(), closure.id());
        graph.getNode(block.id()).getProperties().setProperty("type", "Block");
    }

    @Override
    public void visitCall(CirCall call) {
        super.visitCall(call);
        visitNode(call);
        graph.createEdge(call.id(), call.procedure().id());
        int z = 1;
        for (CirValue v : call.arguments()) {
            graph.createEdge(call.id(), z, v.id(), 0);
            z++;
        }
        graph.getNode(call.id()).getProperties().setProperty("name", "call");
        graph.getNode(call.id()).getProperties().setProperty("type", "Call");
    }

    @Override
    public void visitClosure(CirClosure closure) {
        super.visitClosure(closure);
        visitNode(closure);

        int z = 0;
        for (CirVariable v : closure.parameters()) {
            graph.createEdge(closure.id(), z, v.id(), 0);
            z++;
        }

        graph.createEdge(closure.id(), z, closure.body().id(), 0);
        graph.getNode(closure.id()).getProperties().setProperty("name", "proc");
        graph.getNode(closure.id()).getProperties().setProperty("type", "Closure");
    }

    @Override
    public void visitContinuation(CirContinuation continuation) {
        super.visitContinuation(continuation);
        graph.getNode(continuation.id()).getProperties().setProperty("name", "cont");
        graph.getNode(continuation.id()).getProperties().setProperty("type", "Continuation");
    }

    @Override
    public void visitLocalVariable(CirLocalVariable variable) {
        super.visitLocalVariable(variable);
        graph.getNode(variable.id()).getProperties().setProperty("type", "LocalVariable");
    }

    @Override
    public void visitMethod(CirMethod method) {
        super.visitMethod(method);
        graph.getNode(method.id()).getProperties().setProperty("type", "Method");
    }

    @Override
    public void visitMethodParameter(CirMethodParameter parameter) {
        super.visitMethodParameter(parameter);
        graph.getNode(parameter.id()).getProperties().setProperty("type", "Parameter");
    }
}
