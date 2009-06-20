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
package com.sun.max.vm.compiler.ir.igv;

import java.io.*;
import java.util.*;

import com.sun.max.collect.*;
import com.sun.max.io.*;

/**
 * Utility class for writing a graph document as an XML stream.
 *
 * @author Thomas Wuerthinger
 */
public class GraphWriter {

    public static final String TOP_ELEMENT = "graphDocument";
    public static final String GROUP_ELEMENT = "group";
    public static final String GRAPH_ELEMENT = "graph";
    public static final String ROOT_ELEMENT = "graphDocument";
    public static final String PROPERTIES_ELEMENT = "properties";
    public static final String EDGES_ELEMENT = "edges";
    public static final String PROPERTY_ELEMENT = "p";
    public static final String EDGE_ELEMENT = "edge";
    public static final String NODE_ELEMENT = "node";
    public static final String NODES_ELEMENT = "nodes";
    public static final String METHOD_NAME_PROPERTY = "name";
    public static final String GROUP_NAME_PROPERTY = "name";
    public static final String TRUE_VALUE = "true";
    public static final String NODE_NAME_PROPERTY = "name";
    public static final String EDGE_NAME_PROPERTY = "name";
    public static final String NODE_ID_PROPERTY = "id";
    public static final String FROM_PROPERTY = "from";
    public static final String TO_PROPERTY = "to";
    public static final String PROPERTY_NAME_PROPERTY = "name";
    public static final String GRAPH_NAME_PROPERTY = "name";
    public static final String TO_INDEX_PROPERTY = "index";
    public static final String FROM_INDEX_PROPERTY = "fromIndex";
    public static final String METHOD_ELEMENT = "method";
    public static final String INLINE_ELEMENT = "inline";
    public static final String BYTECODES_ELEMENT = "bytecodes";
    public static final String METHOD_BCI_PROPERTY = "bci";
    public static final String METHOD_SHORT_NAME_PROPERTY = "shortName";
    public static final String CONTROL_FLOW_ELEMENT = "controlFlow";
    public static final String BLOCK_NAME_PROPERTY = "name";
    public static final String BLOCK_ELEMENT = "block";
    public static final String SUCCESSORS_ELEMENT = "successors";
    public static final String SUCCESSOR_ELEMENT = "successor";
    public static final String ASSEMBLY_ELEMENT = "assembly";
    public static final String DIFFERENCE_PROPERTY = "difference";
    public static final String REMOVE_EDGE_ELEMENT = "removeEdge";
    public static final String REMOVE_NODE_ELEMENT = "removeNode";

    private static class PropertyObject {

        private Properties _properties;

        public PropertyObject() {
            _properties = new Properties();
        }

        public Properties getProperties() {
            return _properties;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof PropertyObject) {
                final PropertyObject other = (PropertyObject) obj;
                return other.getProperties().equals(getProperties());
            }
            return false;
        }
    }

    public static final class Node extends PropertyObject {

        private int id;
        private Graph graph;
        private Block block;

        private Node(Graph graph, int id) {
            this.graph = graph;
            this.id = id;
        }

        public Graph getGraph() {
            return graph;
        }

        public int getId() {
            return id;
        }

        @Override
        public int hashCode() {
            return id;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Node) {
                final Node other = (Node) obj;
                if (other.id != id) {
                    return false;
                }
                return super.equals(obj);
            }
            return false;
        }
    }

    public static final class Edge {

        private int from;
        private int fromIndex;
        private int to;
        private int toIndex;
        private Graph graph;

        private Edge(Graph graph, int from, int fromIndex, int to, int toIndex) {
            this.graph = graph;
            this.from = from;
            this.fromIndex = fromIndex;
            this.to = to;
            this.toIndex = toIndex;
        }

        public Graph getGraph() {
            return graph;
        }

        public int getFrom() {
            return from;
        }

        public int getFromIndex() {
            return fromIndex;
        }

        public int getTo() {
            return to;
        }

        public int getToIndex() {
            return toIndex;
        }

        @Override
        public int hashCode() {
            return fromIndex << 28 + toIndex << 24 + from << 12 + to;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Edge) {
                final Edge other = (Edge) obj;
                if (other.from == from && other.fromIndex == fromIndex && other.to == to && other.toIndex == toIndex) {
                    return true;
                }
            }
            return false;
        }
    }

    public static final class Block {

        private Graph graph;
        private String name;
        private AppendableSequence<Block> _successors;
        private AppendableSequence<Block> predecessors;
        private AppendableSequence<Node> nodes;

        private Block(Graph graph, String name) {
            this.graph = graph;
            this.name = name;
            _successors = new LinkSequence<Block>();
            predecessors = new LinkSequence<Block>();
            nodes = new LinkSequence<Node>();
        }

        public Sequence<Node> getNodes() {
            return nodes;
        }

        public Graph getGraph() {
            return graph;
        }

        public Sequence<Block> getSuccessors() {
            return _successors;
        }

        public Sequence<Block> getPredecessors() {
            return predecessors;
        }

        public void addNode(Node n) {
            assert n.getGraph() == getGraph();
            assert n.block == null;
            n.block = this;
            nodes.append(n);
        }

        public void addSuccessor(Block b) {
            assert !Sequence.Static.containsIdentical(_successors, b);
            assert b.getGraph() == graph;
            _successors.append(b);
        }

        public void addPredecessor(Block b) {
            assert !Sequence.Static.containsIdentical(predecessors, b);
            assert b.getGraph() == graph;
            predecessors.append(b);
        }

        public String getName() {
            return name;
        }
    }

    public static final class Graph extends PropertyObject {

        private Set<Node> _nodes;
        private Set<Edge> edges;
        private AppendableSequence<Block> blocks;
        private GrowableMapping<Integer, Node> nodesMapping;

        private Graph(String name) {
            this.getProperties().setProperty(GRAPH_NAME_PROPERTY, name);
            _nodes = new HashSet<Node>();
            edges = new HashSet<Edge>();
            blocks = new LinkSequence<Block>();
            nodesMapping = new ChainedHashMapping<Integer, Node>();
        }

        public Collection<Node> getNodes() {
            return _nodes;
        }

        public Node createNode(int id) {
            assert !nodesMapping.containsKey(id);
            final Node n = new Node(this, id);
            _nodes.add(n);
            nodesMapping.put(id, n);
            return n;
        }

        public Node getNode(int id) {
            return nodesMapping.get(id);
        }

        public Collection<Edge> getEdges() {
            return edges;
        }

        public Edge createEdge(int from, int to) {
            return createEdge(from, 0, to, 0);
        }

        public Edge createEdge(int from, int fromIndex, int to, int toIndex) {
            final Edge e = new Edge(this, from, fromIndex, to, toIndex);
            edges.add(e);
            return e;
        }

        public Block createBlock(String name) {
            final Block b = new Block(this, name);
            blocks.append(b);
            return b;
        }

        public Sequence<Block> getBlocks() {
            return blocks;
        }
    }

    public static class Group extends PropertyObject {

        private AppendableSequence<Graph> _graphs;
        private Method method;

        public Group(String name) {
            this.getProperties().setProperty(METHOD_NAME_PROPERTY, name);
            _graphs = new LinkSequence<Graph>();
        }

        public Graph createGraph(String name) {
            final Graph g = new Graph(name);
            _graphs.append(g);
            return g;
        }

        public Sequence<Graph> getGraphs() {
            return _graphs;
        }

        public Method getMethod() {
            return method;
        }

        public Method createMethod(int bci, String name, String shortName, String bytecodes) {
            assert method == null : "Only one method per group may be created.";
            final Method m = new Method(bci, name, shortName, bytecodes);
            method = m;
            return m;
        }
    }

    public static class Method {

        private int bci;
        private String name;
        private String shortName;
        private String bytecodes;
        private AppendableSequence<Method> inlinedMethods;

        public Method(int bci, String name, String shortName, String bytecodes) {
            this.bci = bci;
            this.name = name;
            this.shortName = shortName;
            this.bytecodes = bytecodes;
            inlinedMethods = new LinkSequence<Method>();
        }

        public Sequence<Method> getInlinedMethods() {
            return inlinedMethods;
        }

        public Method createInlined(int methodBci, String methodName, String methodShortName, String methodBytecodes) {
            final Method m = new Method(methodBci, methodName, methodShortName, methodBytecodes);
            inlinedMethods.append(m);
            return m;
        }

        public String getBytecodes() {
            return bytecodes;
        }

        public int getBci() {
            return bci;
        }

        public String getName() {
            return name;
        }

        public String getShortName() {
            return shortName;
        }
    }

    public static final class Document extends PropertyObject {

        private VariableSequence<Group> groups;

        private Document() {
            groups = new ArrayListSequence<Group>();
        }

        public Sequence<Group> getGroups() {
            return groups;
        }

        public void removeGroup(Group g) {
            assert Sequence.Static.containsIdentical(groups, g);
            groups.remove(Sequence.Static.indexOfIdentical(groups, g));
        }

        public Group createGroup(String name) {
            final Group g = new Group(name);
            groups.append(g);
            return g;
        }
    }

    private XMLWriter _out;

    public GraphWriter(Writer out) {
        this._out = new XMLWriter(out);
    }

    public static Document createDocument() {
        return new Document();
    }

    public void write(Document d) throws IOException {
        _out.begin(TOP_ELEMENT);
        writeProperties(d.getProperties());
        for (Group g : d.getGroups()) {
            writeGroup(g);
        }
        _out.end(TOP_ELEMENT);
    }

    public void close() throws IOException {
        this._out.close();
    }

    private void writeMethod(Method m) throws IOException {
        final Properties p = new Properties();
        p.setProperty(METHOD_BCI_PROPERTY, Integer.toString(m.getBci()));
        p.setProperty(METHOD_NAME_PROPERTY, m.getName());
        p.setProperty(METHOD_SHORT_NAME_PROPERTY, m.getShortName());
        _out.begin(METHOD_ELEMENT, p);

        if (m.getInlinedMethods().length() > 0) {
            _out.begin(INLINE_ELEMENT);
            for (Method im : m.getInlinedMethods()) {
                writeMethod(im);
            }
            _out.end(INLINE_ELEMENT);
        }

        _out.begin(BYTECODES_ELEMENT);
        _out.writeData(m.getBytecodes());
        _out.end(BYTECODES_ELEMENT);

        _out.end(METHOD_ELEMENT);
    }

    private void writeGroup(Group g) throws IOException {
        _out.begin(GROUP_ELEMENT, DIFFERENCE_PROPERTY, TRUE_VALUE);
        writeProperties(g.getProperties());

        Graph previous = null;
        for (Graph graph : g.getGraphs()) {
            writeGraph(graph, previous);
            previous = graph;
        }

        if (g.getMethod() != null) {
            writeMethod(g.getMethod());
        }

        _out.end(GROUP_ELEMENT);
    }

    private void writeNode(Node n) throws IOException {
        _out.begin(NODE_ELEMENT, NODE_ID_PROPERTY, Integer.toString(n.getId()));
        writeProperties(n.getProperties());
        _out.end(NODE_ELEMENT);
    }

    private void writeEdge(Edge e, String elementName) throws IOException {
        final Properties p = new Properties();
        p.setProperty(FROM_PROPERTY, Integer.toString(e.getFrom()));
        p.setProperty(FROM_INDEX_PROPERTY, Integer.toString(e.getFromIndex()));
        p.setProperty(TO_PROPERTY, Integer.toString(e.getTo()));
        p.setProperty(TO_INDEX_PROPERTY, Integer.toString(e.getToIndex()));
        _out.simple(elementName, p);
    }

    private void writeNodeRemoval(Node n) throws IOException {
        _out.simple(REMOVE_NODE_ELEMENT, NODE_ID_PROPERTY, Integer.toString(n.getId()));
    }

    private void writeGraph(Graph g, Graph previous) throws IOException {
        _out.begin(GRAPH_ELEMENT);
        writeProperties(g.getProperties());

        _out.begin(NODES_ELEMENT);
        for (Node n : g.getNodes()) {
            Node prev = null;
            if (previous != null) {
                prev = previous.getNode(n.getId());
            }
            if (prev == null || !prev.equals(n)) {
                writeNode(n);
            }
        }

        if (previous != null) {
            for (Node n : previous.getNodes()) {
                if (g.getNode(n.getId()) == null) {
                    writeNodeRemoval(n);
                }
            }
        }

        _out.end(NODES_ELEMENT);
        _out.begin(EDGES_ELEMENT);

        for (Edge e : g.getEdges()) {
            if (previous == null || !previous.getEdges().contains(e)) {
                writeEdge(e, EDGE_ELEMENT);
            }
        }

        if (previous != null) {
            for (Edge e : previous.getEdges()) {
                if (!g.getEdges().contains(e)) {
                    writeEdge(e, REMOVE_EDGE_ELEMENT);
                }
            }
        }

        _out.end(EDGES_ELEMENT);

        _out.begin(CONTROL_FLOW_ELEMENT);
        for (Block b : g.getBlocks()) {
            writeBlock(b);
        }
        _out.end(CONTROL_FLOW_ELEMENT);

        _out.end(GRAPH_ELEMENT);
    }

    private void writeBlock(Block b) throws IOException {
        _out.begin(BLOCK_ELEMENT, BLOCK_NAME_PROPERTY, b.getName());
        _out.begin(SUCCESSORS_ELEMENT);
        for (Block s : b.getSuccessors()) {
            _out.simple(SUCCESSORS_ELEMENT, BLOCK_NAME_PROPERTY, s.getName());
        }
        _out.end(SUCCESSORS_ELEMENT);
        _out.begin(NODES_ELEMENT);
        for (Node n : b.getNodes()) {
            _out.simple(NODE_ELEMENT, NODE_ID_PROPERTY, Integer.toString(n.getId()));
        }
        _out.end(NODES_ELEMENT);
        _out.end(BLOCK_ELEMENT);
    }

    private void writeProperty(String name, String value) throws IOException {
        _out.begin(PROPERTY_ELEMENT, PROPERTY_NAME_PROPERTY, name);
        _out.write(value);
        _out.end(PROPERTY_ELEMENT);

    }

    private void writeProperties(Properties properties) throws IOException {

        if (properties.size() == 0) {
            return;
        }

        _out.begin(PROPERTIES_ELEMENT);
        final Enumeration e = properties.propertyNames();
        while (e.hasMoreElements()) {
            final String propertyName = (String) e.nextElement();
            writeProperty(propertyName, properties.getProperty(propertyName));
        }
        _out.end(PROPERTIES_ELEMENT);
    }
}
