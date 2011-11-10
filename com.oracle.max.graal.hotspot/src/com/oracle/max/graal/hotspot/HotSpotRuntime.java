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
package com.oracle.max.graal.hotspot;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.cri.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.hotspot.nodes.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.PhiNode.PhiType;
import com.oracle.max.graal.nodes.calc.*;
import com.oracle.max.graal.nodes.calc.ConditionalNode.ConditionalStructure;
import com.oracle.max.graal.nodes.extended.*;
import com.oracle.max.graal.nodes.java.*;
import com.oracle.max.graal.nodes.java.MethodCallTargetNode.InvokeKind;
import com.oracle.max.graal.snippets.nodes.*;
import com.oracle.max.graal.snippets.nodes.MathIntrinsicNode.Operation;
import com.sun.cri.ci.*;
import com.sun.cri.ci.CiTargetMethod.DataPatch;
import com.sun.cri.ci.CiTargetMethod.Safepoint;
import com.sun.cri.ri.*;
import com.sun.cri.ri.RiType.Representation;
import com.sun.max.asm.dis.*;
import com.sun.max.lang.*;

/**
 * CRI runtime implementation for the HotSpot VM.
 */
public class HotSpotRuntime implements GraalRuntime {
    private static final long DOUBLENAN_RAW_LONG_BITS = Double.doubleToRawLongBits(Double.NaN);
    private static final int FLOATNAN_RAW_INT_BITS = Float.floatToRawIntBits(Float.NaN);

    final GraalContext context;
    final HotSpotVMConfig config;
    final HotSpotRegisterConfig regConfig;
    final HotSpotRegisterConfig globalStubRegConfig;
    private final Compiler compiler;
    private IdentityHashMap<RiMethod, StructuredGraph> intrinsicGraphs = new IdentityHashMap<RiMethod, StructuredGraph>();

    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<Runnable>();

    public HotSpotRuntime(GraalContext context, HotSpotVMConfig config, Compiler compiler) {
        this.context = context;
        this.config = config;
        this.compiler = compiler;
        regConfig = new HotSpotRegisterConfig(config, false);
        globalStubRegConfig = new HotSpotRegisterConfig(config, true);
    }

    @Override
    public int codeOffset() {
        return 0;
    }


    public Compiler getCompiler() {
        return compiler;
    }

    @Override
    public String disassemble(byte[] code, long address) {
        return disassemble(code, new DisassemblyPrinter(false), address);
    }

    private String disassemble(byte[] code, DisassemblyPrinter disassemblyPrinter, long address) {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final ISA instructionSet = ISA.AMD64;
        Disassembler.disassemble(byteArrayOutputStream, code, instructionSet, WordWidth.BITS_64, address, null, disassemblyPrinter);
        return byteArrayOutputStream.toString();
    }

    @Override
    public String disassemble(final CiTargetMethod targetMethod) {

        final DisassemblyPrinter disassemblyPrinter = new DisassemblyPrinter(false) {

            private String siteInfo(int pcOffset) {
                for (Safepoint site : targetMethod.safepoints) {
                    if (site.pcOffset == pcOffset) {
                        return "{safepoint}";
                    }
                }
                for (DataPatch site : targetMethod.dataReferences) {
                    if (site.pcOffset == pcOffset) {
                        return "{" + site.constant + "}";
                    }
                }
                return null;
            }

            @Override
            protected String disassembledObjectString(Disassembler disassembler, DisassembledObject disassembledObject) {
                final String string = super.disassembledObjectString(disassembler, disassembledObject);

                String site = siteInfo(disassembledObject.startPosition());
                if (site != null) {
                    return string + " " + site;
                }
                return string;
            }
        };
        final byte[] code = Arrays.copyOf(targetMethod.targetCode(), targetMethod.targetCodeSize());
        return disassemble(code, disassemblyPrinter, 0L);
    }

    @Override
    public String disassemble(RiResolvedMethod method) {
        return "No disassembler available";
    }

    @Override
    public RiConstantPool getConstantPool(RiResolvedMethod method) {
        return ((HotSpotTypeResolved) method.holder()).constantPool();
    }

    public Class<?> getJavaClass(CiConstant c) {
        return null;
    }

    @Override
    public RiResolvedType asRiType(CiKind kind) {
        return (RiResolvedType) compiler.getVMEntries().getType(kind.toJavaClass());
    }

    @Override
    public RiResolvedType getTypeOf(CiConstant constant) {
        return (RiResolvedType) compiler.getVMEntries().getRiType(constant);
    }

    @Override
    public boolean isExceptionType(RiResolvedType type) {
        return type.isSubtypeOf((RiResolvedType) compiler.getVMEntries().getType(Throwable.class));
    }

    @Override
    public boolean mustInline(RiResolvedMethod method) {
        return false;
    }

    @Override
    public boolean mustNotCompile(RiResolvedMethod method) {
        return false;
    }

    @Override
    public boolean mustNotInline(RiResolvedMethod method) {
        return Modifier.isNative(method.accessFlags());
    }

    @Override
    public Object registerCompilerStub(CiTargetMethod targetMethod, String name) {
        return HotSpotTargetMethod.installStub(compiler, targetMethod, name);
    }

    @Override
    public int sizeOfBasicObjectLock() {
        // TODO shouldn't be hard coded
        return 2 * 8;
    }

    @Override
    public int basicObjectLockOffsetInBytes() {
        return 8;
    }

    public boolean isFoldable(RiResolvedMethod method) {
        return false;
    }

    @Override
    public CiConstant fold(RiResolvedMethod method, CiConstant[] args) {
        return null;
    }

    @Override
    public boolean areConstantObjectsEqual(CiConstant x, CiConstant y) {
        return compiler.getVMEntries().compareConstantObjects(x, y);
    }

    @Override
    public RiRegisterConfig getRegisterConfig(RiMethod method) {
        return regConfig;
    }

    /**
     * HotSpots needs an area suitable for storing a program counter for temporary use during the deoptimization process.
     */
    @Override
    public int getCustomStackAreaSize() {
        return 8;
    }

    @Override
    public boolean supportsArrayIntrinsics() {
        return true;
    }

    @Override
    public int getArrayLength(CiConstant array) {
        return compiler.getVMEntries().getArrayLength(array);
    }

    @Override
    public Class<?> asJavaClass(CiConstant c) {
        return null;
    }

    @Override
    public Object asJavaObject(CiConstant c) {
        return null;
    }

    @Override
    public void lower(Node n, CiLoweringTool tool) {
        if (!GraalOptions.Lower) {
            return;
        }

        if (n instanceof ArrayLengthNode) {
            ArrayLengthNode arrayLengthNode = (ArrayLengthNode) n;
            SafeReadNode safeReadArrayLength = safeReadArrayLength(arrayLengthNode.graph(), arrayLengthNode.array());
            FixedNode nextNode = arrayLengthNode.next();
            arrayLengthNode.clearSuccessors();
            safeReadArrayLength.setNext(nextNode);
            arrayLengthNode.replaceAndDelete(safeReadArrayLength);
            safeReadArrayLength.lower(tool);
        } else if (n instanceof LoadFieldNode) {
            LoadFieldNode field = (LoadFieldNode) n;
            if (field.isVolatile()) {
                return;
            }
            StructuredGraph graph = field.graph();
            int displacement = ((HotSpotField) field.field()).offset();
            assert field.kind() != CiKind.Illegal;
            ReadNode memoryRead = graph.unique(new ReadNode(field.field().kind(true).stackKind(), field.object(), LocationNode.create(field.field(), field.field().kind(true), displacement, graph)));
            memoryRead.setGuard((GuardNode) tool.createGuard(graph.unique(new NullCheckNode(field.object(), false))));
            FixedNode next = field.next();
            field.setNext(null);
            memoryRead.setNext(next);
            field.replaceAndDelete(memoryRead);
        } else if (n instanceof StoreFieldNode) {
            StoreFieldNode field = (StoreFieldNode) n;
            if (field.isVolatile()) {
                return;
            }
            StructuredGraph graph = field.graph();
            int displacement = ((HotSpotField) field.field()).offset();
            WriteNode memoryWrite = graph.add(new WriteNode(field.object(), field.value(), LocationNode.create(field.field(), field.field().kind(true), displacement, graph)));
            memoryWrite.setGuard((GuardNode) tool.createGuard(graph.unique(new NullCheckNode(field.object(), false))));
            memoryWrite.setStateAfter(field.stateAfter());
            FixedNode next = field.next();
            field.setNext(null);
            if (field.field().kind(true) == CiKind.Object && !field.value().isNullConstant()) {
                FieldWriteBarrier writeBarrier = graph.add(new FieldWriteBarrier(field.object()));
                memoryWrite.setNext(writeBarrier);
                writeBarrier.setNext(next);
            } else {
                memoryWrite.setNext(next);
            }
            field.replaceAndDelete(memoryWrite);
        } else if (n instanceof LoadIndexedNode) {
            LoadIndexedNode loadIndexed = (LoadIndexedNode) n;
            StructuredGraph graph = loadIndexed.graph();
            GuardNode boundsCheck = createBoundsCheck(loadIndexed, tool);

            CiKind elementKind = loadIndexed.elementKind();
            LocationNode arrayLocation = createArrayLocation(graph, elementKind, loadIndexed.index());
            ReadNode memoryRead = graph.unique(new ReadNode(elementKind.stackKind(), loadIndexed.array(), arrayLocation));
            memoryRead.setGuard(boundsCheck);
            FixedNode next = loadIndexed.next();
            loadIndexed.setNext(null);
            memoryRead.setNext(next);
            loadIndexed.replaceAndDelete(memoryRead);
        } else if (n instanceof StoreIndexedNode) {
            StoreIndexedNode storeIndexed = (StoreIndexedNode) n;
            StructuredGraph graph = storeIndexed.graph();
            AnchorNode anchor = graph.add(new AnchorNode());
            GuardNode boundsCheck = createBoundsCheck(storeIndexed, tool);

            FixedWithNextNode append = anchor;

            CiKind elementKind = storeIndexed.elementKind();
            LocationNode arrayLocation = createArrayLocation(graph, elementKind, storeIndexed.index());
            ValueNode value = storeIndexed.value();
            ValueNode array = storeIndexed.array();
            if (elementKind == CiKind.Object && !value.isNullConstant()) {
                // Store check!
                if (array.exactType() != null) {
                    RiResolvedType elementType = array.exactType().componentType();
                    if (elementType.superType() != null) {
                        ConstantNode type = graph.unique(ConstantNode.forCiConstant(elementType.getEncoding(Representation.ObjectHub), this, graph));
                        value = graph.unique(new CheckCastNode(anchor, type, elementType, value));
                    } else {
                        assert elementType.name().equals("Ljava/lang/Object;") : elementType.name();
                    }
                } else {
                    GuardNode guard = (GuardNode) tool.createGuard(graph.unique(new NullCheckNode(array, false)));
                    ReadNode arrayClass = graph.unique(new ReadNode(CiKind.Object, array, LocationNode.create(LocationNode.FINAL_LOCATION, CiKind.Object, config.hubOffset, graph)));
                    arrayClass.setGuard(guard);
                    append.setNext(arrayClass);
                    append = arrayClass;
                    ReadNode arrayElementKlass = graph.unique(new ReadNode(CiKind.Object, arrayClass, LocationNode.create(LocationNode.FINAL_LOCATION, CiKind.Object, config.arrayClassElementOffset, graph)));
                    value = graph.unique(new CheckCastNode(anchor, arrayElementKlass, null, value));
                }
            }
            WriteNode memoryWrite = graph.add(new WriteNode(array, value, arrayLocation));
            memoryWrite.setGuard(boundsCheck);
            memoryWrite.setStateAfter(storeIndexed.stateAfter());
            FixedNode next = storeIndexed.next();
            storeIndexed.setNext(null);
            append.setNext(memoryWrite);
            if (elementKind == CiKind.Object && !value.isNullConstant()) {
                ArrayWriteBarrier writeBarrier = graph.add(new ArrayWriteBarrier(array, arrayLocation));
                memoryWrite.setNext(writeBarrier);
                writeBarrier.setNext(next);
            } else {
                memoryWrite.setNext(next);
            }
            storeIndexed.replaceAtPredecessors(anchor);
            storeIndexed.delete();
        } else if (n instanceof UnsafeLoadNode) {
            UnsafeLoadNode load = (UnsafeLoadNode) n;
            StructuredGraph graph = load.graph();
            assert load.kind() != CiKind.Illegal;
            IndexedLocationNode location = IndexedLocationNode.create(LocationNode.UNSAFE_ACCESS_LOCATION, load.loadKind(), load.displacement(), load.offset(), graph);
            location.setIndexScalingEnabled(false);
            ReadNode memoryRead = graph.unique(new ReadNode(load.kind(), load.object(), location));
            memoryRead.setGuard((GuardNode) tool.createGuard(graph.unique(new NullCheckNode(load.object(), false))));
            FixedNode next = load.next();
            load.setNext(null);
            memoryRead.setNext(next);
            load.replaceAndDelete(memoryRead);
        } else if (n instanceof UnsafeStoreNode) {
            UnsafeStoreNode store = (UnsafeStoreNode) n;
            StructuredGraph graph = store.graph();
            IndexedLocationNode location = IndexedLocationNode.create(LocationNode.UNSAFE_ACCESS_LOCATION, store.storeKind(), store.displacement(), store.offset(), graph);
            location.setIndexScalingEnabled(false);
            WriteNode write = graph.add(new WriteNode(store.object(), store.value(), location));
            FieldWriteBarrier barrier = graph.add(new FieldWriteBarrier(store.object()));
            FixedNode next = store.next();
            store.setNext(null);
            barrier.setNext(next);
            write.setNext(barrier);
            write.setStateAfter(store.stateAfter());
            store.replaceAtPredecessors(write);
            store.delete();
        }
    }

    private IndexedLocationNode createArrayLocation(Graph graph, CiKind elementKind, ValueNode index) {
        return IndexedLocationNode.create(LocationNode.getArrayLocation(elementKind), elementKind, config.getArrayOffset(elementKind), index, graph);
    }

    private GuardNode createBoundsCheck(AccessIndexedNode n, CiLoweringTool tool) {
        return (GuardNode) tool.createGuard(n.graph().unique(new CompareNode(n.index(), Condition.BT, n.length())));
    }

    @Override
    public StructuredGraph intrinsicGraph(RiResolvedMethod caller, int bci, RiResolvedMethod method, List<? extends Node> parameters) {

        if (method.holder().name().equals("Ljava/lang/Object;")) {
            String fullName = method.name() + method.signature().asString();
            if (fullName.equals("getClass()Ljava/lang/Class;")) {
                ValueNode obj = (ValueNode) parameters.get(0);
                if (obj.isConstant() && obj.asConstant().isNonNull()) {
                    StructuredGraph graph = new StructuredGraph();
                    ValueNode result;
                    if (GraalOptions.Meter) {
                        context.metrics.GetClassForConstant++;
                    }
                    result = ConstantNode.forObject(obj.asConstant().asObject().getClass(), this, graph);
                    ReturnNode ret = graph.add(new ReturnNode(result));
                    graph.start().setNext(ret);
                    return graph;
                }
            }
        } else if (method.holder().name().equals("Lcom/oracle/max/graal/graph/NodeClass;")) {
            String fullName = method.name() + method.signature().asString();
            if (fullName.equals("get()Lcom/oracle/max/graal/graph/NodeClass;")) {
                ValueNode obj = (ValueNode) parameters.get(0);
                if (obj.isConstant()) {
                    assert obj.asConstant().asObject() instanceof Class;
                    StructuredGraph graph = new StructuredGraph();
                    ValueNode result;
                    if (GraalOptions.Meter) {
                        context.metrics.GetClassForConstant++;
                    }
                    result = ConstantNode.forObject(NodeClass.get((Class< ? >) obj.asConstant().asObject()), this, graph);
                    ReturnNode ret = graph.add(new ReturnNode(result));
                    graph.start().setNext(ret);
                    return graph;
                }
            }
        }
        if (!intrinsicGraphs.containsKey(method)) {
            RiType holder = method.holder();
            String fullName = method.name() + method.signature().asString();
            String holderName = holder.name();
            if (holderName.equals("Ljava/lang/Object;")) {
                if (fullName.equals("getClass()Ljava/lang/Class;")) {
                    StructuredGraph graph = new StructuredGraph();
                    LocalNode receiver = graph.unique(new LocalNode(CiKind.Object, 0));
                    SafeReadNode klassOop = safeReadHub(graph, receiver);
                    SafeReadNode result = graph.add(new SafeReadNode(CiKind.Object, klassOop, LocationNode.create(LocationNode.FINAL_LOCATION, CiKind.Object, config.classMirrorOffset, graph)));
                    ReturnNode ret = graph.add(new ReturnNode(result));
                    graph.start().setNext(klassOop);
                    klassOop.setNext(result);
                    result.setNext(ret);
                    intrinsicGraphs.put(method, graph);
                }
            } else if (method.holder().name().equals("Ljava/lang/Class;")) {
                if (fullName.equals("getModifiers()I")) {
                    StructuredGraph graph = new StructuredGraph();
                    LocalNode receiver = graph.unique(new LocalNode(CiKind.Object, 0));
                    SafeReadNode klassOop = safeRead(graph, CiKind.Object, receiver, config.klassOopOffset);
                    graph.start().setNext(klassOop);
                    // TODO(tw): Care about primitive classes!
                    ReadNode result = graph.unique(new ReadNode(CiKind.Int, klassOop, LocationNode.create(LocationNode.FINAL_LOCATION, CiKind.Int, config.klassModifierFlagsOffset, graph)));
                    ReturnNode ret = graph.add(new ReturnNode(result));
                    klassOop.setNext(ret);
                    intrinsicGraphs.put(method, graph);
                } else if (fullName.equals("isInstance(Ljava/lang/Object;)Z")) {
                    StructuredGraph graph = new StructuredGraph();
                    LocalNode receiver = graph.unique(new LocalNode(CiKind.Object, 0));
                    LocalNode argument = graph.unique(new LocalNode(CiKind.Object, 1));
                    SafeReadNode klassOop = safeRead(graph, CiKind.Object, receiver, config.klassOopOffset);
                    graph.start().setNext(klassOop);
                    // TODO(tw): Care about primitive classes!
                    MaterializeNode result = MaterializeNode.create(graph.unique(new InstanceOfNode(klassOop, null, argument, false)), graph);
                    ReturnNode ret = graph.add(new ReturnNode(result));
                    klassOop.setNext(ret);
                    intrinsicGraphs.put(method, graph);
                }
            } else if (holderName.equals("Ljava/lang/System;")) {
                if (fullName.equals("arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V")) {
                    StructuredGraph graph = new StructuredGraph();
                    LocalNode src = graph.unique(new LocalNode(CiKind.Object, 0));
                    LocalNode srcPos = graph.unique(new LocalNode(CiKind.Int, 1));
                    LocalNode dest = graph.unique(new LocalNode(CiKind.Object, 2));
                    LocalNode destPos = graph.unique(new LocalNode(CiKind.Int, 3));
                    ValueNode length = graph.unique(new LocalNode(CiKind.Int, 4));
                    src.setDeclaredType(((ValueNode) parameters.get(0)).declaredType());
                    dest.setDeclaredType(((ValueNode) parameters.get(2)).declaredType());

                    if (src.declaredType() == null || dest.declaredType() == null) {
                        return null;
                    }

                    if (src.declaredType() != dest.declaredType()) {
                        return null;
                    }

                    if (!src.declaredType().isArrayClass()) {
                        return null;
                    }

                    CiKind componentType = src.declaredType().componentType().kind(true);
                    if (componentType == CiKind.Object) {
                        return null;
                    }

                    FrameState stateBefore = graph.add(new FrameState(method, FrameState.BEFORE_BCI, 0, 0, 0, false));
                    FrameState stateAfter = graph.add(new FrameState(method, FrameState.AFTER_BCI, 0, 0, 0, false));

                    // Add preconditions.
                    ArrayLengthNode srcLength = graph.add(new ArrayLengthNode(src));
                    graph.start().setNext(srcLength);
                    ArrayLengthNode destLength = graph.add(new ArrayLengthNode(dest));
                    srcLength.setNext(destLength);
                    FixedGuardNode guard = graph.add(new FixedGuardNode());
                    destLength.setNext(guard);

                    IntegerAddNode upperLimitSrc = graph.unique(new IntegerAddNode(CiKind.Int, srcPos, length));
                    IntegerAddNode upperLimitDest = graph.unique(new IntegerAddNode(CiKind.Int, destPos, length));
                    guard.addCondition(graph.unique(new CompareNode(srcPos, Condition.BE, srcLength)));
                    guard.addCondition(graph.unique(new CompareNode(destPos, Condition.BE, destLength)));
                    guard.addCondition(graph.unique(new CompareNode(length, Condition.GE, ConstantNode.forInt(0, graph))));
                    guard.addCondition(graph.unique(new CompareNode(upperLimitSrc, Condition.LE, srcLength)));
                    guard.addCondition(graph.unique(new CompareNode(upperLimitDest, Condition.LE, destLength)));


                    LocationNode location = LocationNode.create(LocationNode.FINAL_LOCATION, componentType, config.getArrayOffset(componentType), graph);

                    // Build normal vector instruction.
                    CreateVectorNode normalVector = graph.add(new CreateVectorNode(false, length));
                    ReadVectorNode values = graph.add(new ReadVectorNode(graph.add(new IntegerAddVectorNode(normalVector, srcPos)), src, location));
                    graph.add(new WriteVectorNode(graph.add(new IntegerAddVectorNode(normalVector, destPos)), dest, location, values));
                    normalVector.setStateAfter(stateAfter);

                    // Build reverse vector instruction.
                    CreateVectorNode reverseVector = graph.add(new CreateVectorNode(true, length));
                    ReadVectorNode reverseValues = graph.add(new ReadVectorNode(graph.add(new IntegerAddVectorNode(reverseVector, srcPos)), src, location));
                    graph.add(new WriteVectorNode(graph.add(new IntegerAddVectorNode(reverseVector, destPos)), dest, location, reverseValues));
                    reverseVector.setStateAfter(stateAfter);

                    IfNode ifNode = graph.add(new IfNode(graph.unique(new CompareNode(src, Condition.EQ, dest)), 0.5));
                    guard.setNext(ifNode);

                    IfNode secondIf = graph.add(new IfNode(graph.unique(new CompareNode(srcPos, Condition.LT, destPos)), 0.5));
                    ifNode.setTrueSuccessor(BeginNode.begin(secondIf));

                    secondIf.setTrueSuccessor(BeginNode.begin(reverseVector));

                    MergeNode merge1 = graph.add(new MergeNode());
                    merge1.addEnd(graph.add(new EndNode()));
                    merge1.addEnd(graph.add(new EndNode()));
                    merge1.setStateAfter(stateBefore);


                    InvokeNode newInvoke = null;
                    if (componentType == CiKind.Object) {
                        AnchorNode anchor = graph.add(new AnchorNode());
                        SafeReadNode srcClass = graph.add(new SafeReadNode(CiKind.Object, src, LocationNode.create(LocationNode.FINAL_LOCATION, CiKind.Object, config.hubOffset, graph)));
                        anchor.setNext(srcClass);
                        SafeReadNode destClass = graph.add(new SafeReadNode(CiKind.Object, dest, LocationNode.create(LocationNode.FINAL_LOCATION, CiKind.Object, config.hubOffset, graph)));
                        srcClass.setNext(destClass);
                        IfNode elementClassIf = graph.add(new IfNode(graph.unique(new CompareNode(srcClass, Condition.EQ, destClass)), 0.5));
                        destClass.setNext(elementClassIf);
                        ifNode.setFalseSuccessor(BeginNode.begin(anchor));
                        MethodCallTargetNode target = graph.add(new MethodCallTargetNode(InvokeKind.Static, method, new ValueNode[]{src, srcPos, dest, destPos, length}, method.signature().returnType(holder)));
                        newInvoke = graph.add(new InvokeNode(target, FrameState.BEFORE_BCI));
                        newInvoke.setCanInline(false);
                        newInvoke.setStateAfter(stateAfter);
                        elementClassIf.setFalseSuccessor(BeginNode.begin(newInvoke));
                        elementClassIf.setTrueSuccessor(BeginNode.begin(merge1.endAt(0)));
                    } else {
                        ifNode.setFalseSuccessor(BeginNode.begin(merge1.endAt(0)));
                    }

                    secondIf.setFalseSuccessor(BeginNode.begin(merge1.endAt(1)));
                    merge1.setNext(normalVector);

                    MergeNode merge2 = graph.add(new MergeNode());
                    merge2.addEnd(graph.add(new EndNode()));
                    merge2.addEnd(graph.add(new EndNode()));
                    merge2.setStateAfter(stateAfter);

                    normalVector.setNext(merge2.endAt(0));
                    reverseVector.setNext(merge2.endAt(1));

                    if (newInvoke != null) {
                        merge2.addEnd(graph.add(new EndNode()));
                        newInvoke.setNext(merge2.endAt(2));
                    }

                    ReturnNode ret = graph.add(new ReturnNode(null));
                    merge2.setNext(ret);
                    return graph;
                } else if (fullName.equals("currentTimeMillis()J")) {
                    StructuredGraph graph = new StructuredGraph();
                    RuntimeCallNode call = graph.add(new RuntimeCallNode(CiRuntimeCall.JavaTimeMillis));
                    ReturnNode ret = graph.add(new ReturnNode(call));
                    call.setNext(ret);
                    graph.start().setNext(call);
                    intrinsicGraphs.put(method, graph);
                } else if (fullName.equals("nanoTime()J")) {
                    StructuredGraph graph = new StructuredGraph();
                    RuntimeCallNode call = graph.add(new RuntimeCallNode(CiRuntimeCall.JavaTimeNanos));
                    ReturnNode ret = graph.add(new ReturnNode(call));
                    call.setNext(ret);
                    graph.start().setNext(call);
                    intrinsicGraphs.put(method, graph);
                }
            } else if (holderName.equals("Ljava/lang/Float;")) {
                if (fullName.equals("floatToRawIntBits(F)I")) {
                    StructuredGraph graph = new StructuredGraph();
                    ReturnNode ret = graph.add(new ReturnNode(graph.unique(new ConvertNode(ConvertNode.Op.MOV_F2I, graph.unique(new LocalNode(CiKind.Float, 0))))));
                    graph.start().setNext(ret);
                    intrinsicGraphs.put(method, graph);
                } else if (fullName.equals("floatToIntBits(F)I")) {
                    StructuredGraph graph = new StructuredGraph();
                    LocalNode arg = graph.unique(new LocalNode(CiKind.Float, 0));
                    CompareNode isNan = graph.unique(new CompareNode(arg, Condition.NE, true, arg));
                    ConvertNode fpConv = graph.unique(new ConvertNode(ConvertNode.Op.MOV_F2I, arg));
                    ConditionalStructure conditionalStructure = ConditionalNode.createConditionalStructure(isNan, ConstantNode.forInt(FLOATNAN_RAW_INT_BITS, graph), fpConv, 0.1);
                    ReturnNode ret = graph.add(new ReturnNode(conditionalStructure.phi));
                    graph.start().setNext(conditionalStructure.ifNode);
                    conditionalStructure.merge.setNext(ret);
                    intrinsicGraphs.put(method, graph);
                } else if (fullName.equals("intBitsToFloat(I)F")) {
                    StructuredGraph graph = new StructuredGraph();
                    ReturnNode ret = graph.add(new ReturnNode(graph.unique(new ConvertNode(ConvertNode.Op.MOV_I2F, graph.unique(new LocalNode(CiKind.Int, 0))))));
                    graph.start().setNext(ret);
                    intrinsicGraphs.put(method, graph);
                }
            } else if (holderName.equals("Ljava/lang/Double;")) {
                if (fullName.equals("doubleToRawLongBits(D)J")) {
                    StructuredGraph graph = new StructuredGraph();
                    ReturnNode ret = graph.add(new ReturnNode(graph.unique(new ConvertNode(ConvertNode.Op.MOV_D2L, graph.unique(new LocalNode(CiKind.Double, 0))))));
                    graph.start().setNext(ret);
                    intrinsicGraphs.put(method, graph);
                } else if (fullName.equals("doubleToLongBits(D)J")) {
                    StructuredGraph graph = new StructuredGraph();
                    LocalNode arg = graph.unique(new LocalNode(CiKind.Double, 0));
                    CompareNode isNan = graph.unique(new CompareNode(arg, Condition.NE, true, arg));
                    ConvertNode fpConv = graph.unique(new ConvertNode(ConvertNode.Op.MOV_D2L, arg));
                    ConditionalStructure conditionalStructure = ConditionalNode.createConditionalStructure(isNan, ConstantNode.forLong(DOUBLENAN_RAW_LONG_BITS, graph), fpConv, 0.1);
                    ReturnNode ret = graph.add(new ReturnNode(conditionalStructure.phi));
                    graph.start().setNext(conditionalStructure.ifNode);
                    conditionalStructure.merge.setNext(ret);
                    intrinsicGraphs.put(method, graph);
                } else if (fullName.equals("longBitsToDouble(J)D")) {
                    StructuredGraph graph = new StructuredGraph();
                    ReturnNode ret = graph.add(new ReturnNode(graph.unique(new ConvertNode(ConvertNode.Op.MOV_L2D, graph.unique(new LocalNode(CiKind.Long, 0))))));
                    graph.start().setNext(ret);
                    intrinsicGraphs.put(method, graph);
                }
            } else if (holderName.equals("Lsun/misc/Unsafe;")) {
                if (fullName.startsWith("compareAndSwap")) {
                    CiKind kind = null;
                    if (fullName.equals("compareAndSwapObject(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z")) {
                        kind = CiKind.Object;
                    } else if (fullName.equals("compareAndSwapInt(Ljava/lang/Object;JII)Z")) {
                        kind = CiKind.Int;
                    } else if (fullName.equals("compareAndSwapLong(Ljava/lang/Object;JJJ)Z")) {
                        kind = CiKind.Long;
                    }
                    if (kind != null) {
                        StructuredGraph graph = new StructuredGraph();
                        LocalNode object = graph.unique(new LocalNode(CiKind.Object, 1));
                        LocalNode offset = graph.unique(new LocalNode(CiKind.Long, 2));
                        LocalNode expected = graph.unique(new LocalNode(kind, 3));
                        LocalNode value = graph.unique(new LocalNode(kind, 4));
                        CompareAndSwapNode cas = graph.add(new CompareAndSwapNode(object, offset, expected, value, false));
                        FrameState frameState = graph.add(new FrameState(method, FrameState.AFTER_BCI, 0, 0, 0, false));
                        cas.setStateAfter(frameState);
                        ReturnNode ret = graph.add(new ReturnNode(cas));
                        cas.setNext(ret);
                        graph.start().setNext(cas);
                        intrinsicGraphs.put(method, graph);
                    }
                } else if (fullName.equals("getObject(Ljava/lang/Object;J)Ljava/lang/Object;")) {
                    StructuredGraph graph = new StructuredGraph();
                    LocalNode object = graph.unique(new LocalNode(CiKind.Object, 1));
                    LocalNode offset = graph.unique(new LocalNode(CiKind.Long, 2));
                    UnsafeLoadNode load = graph.unique(new UnsafeLoadNode(object, offset, CiKind.Object));
                    ReturnNode ret = graph.add(new ReturnNode(load));
                    load.setNext(ret);
                    graph.start().setNext(load);
                    intrinsicGraphs.put(method, graph);
// TODO disabled for now.  The old VolatileReadNode was not safe and is deleted now.  We need a MemoryBarrierNode before and after the
// actual read, and it must be guaranteed that the memory barriers stay next to the read during scheduling.
//                } else if (fullName.equals("getObjectVolatile(Ljava/lang/Object;J)Ljava/lang/Object;")) {
//                    StructuredGraph graph = new StructuredGraph();
//                    LocalNode object = graph.unique(new LocalNode(CiKind.Object, 1));
//                    LocalNode offset = graph.unique(new LocalNode(CiKind.Long, 2));
//                    IndexedLocationNode location = IndexedLocationNode.create(LocationNode.UNSAFE_ACCESS_LOCATION, CiKind.Object, 0, offset, graph);
//                    location.setIndexScalingEnabled(false);
//                    SafeReadNode safeRead = graph.add(new SafeReadNode(CiKind.Object, object, location));
//                    VolatileReadNode volatileRead = graph.add(new VolatileReadNode(safeRead));
//                    ReturnNode ret = graph.add(new ReturnNode(volatileRead));
//                    graph.start().setNext(safeRead);
//                    safeRead.setNext(volatileRead);
//                    volatileRead.setNext(ret);
//                    intrinsicGraphs.put(method, graph);
                } else if (fullName.equals("getInt(Ljava/lang/Object;J)I")) {
                    StructuredGraph graph = new StructuredGraph();
                    LocalNode object = graph.unique(new LocalNode(CiKind.Object, 1));
                    LocalNode offset = graph.unique(new LocalNode(CiKind.Long, 2));
                    UnsafeLoadNode load = graph.unique(new UnsafeLoadNode(object, offset, CiKind.Int));
                    graph.start().setNext(load);
                    ReturnNode ret = graph.add(new ReturnNode(load));
                    load.setNext(ret);
                    intrinsicGraphs.put(method, graph);
                } else if (fullName.equals("putObject(Ljava/lang/Object;JLjava/lang/Object;)V")) {
                    StructuredGraph graph = new StructuredGraph();
                    LocalNode object = graph.unique(new LocalNode(CiKind.Object, 1));
                    LocalNode offset = graph.unique(new LocalNode(CiKind.Long, 2));
                    LocalNode value = graph.unique(new LocalNode(CiKind.Object, 3));
                    UnsafeStoreNode store = graph.add(new UnsafeStoreNode(object, offset, value, CiKind.Object));
                    FrameState frameState = graph.add(new FrameState(method, FrameState.AFTER_BCI, 0, 0, 0, false));
                    graph.start().setNext(store);
                    store.setStateAfter(frameState);
                    ReturnNode ret = graph.add(new ReturnNode(null));
                    store.setNext(ret);
                    intrinsicGraphs.put(method, graph);
                }
            } else if (holderName.equals("Ljava/lang/Math;") && compiler.getCompiler().target.arch.isX86()) {
                Operation op = null;
                if (fullName.equals("abs(D)D")) {
                    op = Operation.ABS;
                } else if (fullName.equals("sqrt(D)D")) {
                    op = Operation.SQRT;
                } else if (fullName.equals("log(D)D")) {
                    op = Operation.LOG;
                } else if (fullName.equals("log10(D)D")) {
                    op = Operation.LOG10;
                } else if (fullName.equals("sin(D)D")) {
                    op = Operation.SIN;
                } else if (fullName.equals("cos(D)D")) {
                    op = Operation.COS;
                } else if (fullName.equals("tan(D)D")) {
                    op = Operation.TAN;
                }
                if (op != null) {
                    StructuredGraph graph = new StructuredGraph();
                    LocalNode value = graph.unique(new LocalNode(CiKind.Double, 0));
                    if (op == Operation.SIN || op == Operation.COS || op == Operation.TAN) {
                        // Math.sin(), .cos() and .tan() guarantee a value within 1 ULP of the
                        // exact result, but x87 trigonometric FPU instructions are only that
                        // accurate within [-pi/4, pi/4]. Examine the passed value and provide
                        // a slow path for inputs outside of that interval.
                        MathIntrinsicNode abs = graph.unique(new MathIntrinsicNode(value, MathIntrinsicNode.Operation.ABS));
                        ConstantNode pi4 = graph.unique(ConstantNode.forDouble(0.7853981633974483, graph));
                        CompareNode cmp = graph.unique(new CompareNode(abs, Condition.LT, pi4));
                        MathIntrinsicNode fast = graph.unique(new MathIntrinsicNode(value, op));
                        EndNode fastend = graph.add(new EndNode());
                        CiRuntimeCall slowtarget;
                        switch (op) {
                            case SIN: slowtarget = CiRuntimeCall.ArithmeticSin; break;
                            case COS: slowtarget = CiRuntimeCall.ArithmeticCos; break;
                            case TAN: slowtarget = CiRuntimeCall.ArithmeticTan; break;
                            default:
                                throw Util.shouldNotReachHere();
                        }
                        RuntimeCallNode slow = graph.add(new RuntimeCallNode(slowtarget, new ValueNode[]{value}));
                        EndNode slowend = graph.add(new EndNode());
                        slow.setNext(slowend);
                        IfNode branch = graph.add(new IfNode(cmp, fastend, slow, 0.5));
                        graph.start().setNext(branch);
                        MergeNode merge = graph.add(new MergeNode());
                        merge.addEnd(fastend);
                        merge.addEnd(slowend);
                        FrameState state = graph.add(new FrameState(null, FrameState.AFTER_BCI, 0, 0, 0, false));
                        merge.setStateAfter(state);
                        PhiNode phi = graph.unique(new PhiNode(CiKind.Double, merge, PhiType.Value));
                        phi.addInput(fast);
                        phi.addInput(slow);
                        ReturnNode ret = graph.add(new ReturnNode(phi));
                        merge.setNext(ret);
                    } else {
                        MathIntrinsicNode result = graph.unique(new MathIntrinsicNode(value, op));
                        ReturnNode ret = graph.add(new ReturnNode(result));
                        graph.start().setNext(ret);
                    }
                    intrinsicGraphs.put(method, graph);
                }
            }

            if (!intrinsicGraphs.containsKey(method)) {
                intrinsicGraphs.put(method, null);
            }
        }
        return intrinsicGraphs.get(method);
    }

    private SafeReadNode safeReadHub(Graph graph, ValueNode value) {
        return safeRead(graph, CiKind.Object, value, config.hubOffset);
    }

    private SafeReadNode safeReadArrayLength(Graph graph, ValueNode value) {
        return safeRead(graph, CiKind.Int, value, config.arrayLengthOffset);
    }

    private SafeReadNode safeRead(Graph graph, CiKind kind, ValueNode value, int offset) {
        return graph.add(new SafeReadNode(kind, value, LocationNode.create(LocationNode.FINAL_LOCATION, kind, offset, graph)));
    }

    public RiResolvedType getType(Class<?> clazz) {
        return (RiResolvedType) compiler.getVMEntries().getType(clazz);
    }

    public Object asCallTarget(Object target) {
        return target;
    }

    public long getMaxCallTargetOffset(CiRuntimeCall rtcall) {
        return compiler.getVMEntries().getMaxCallTargetOffset(rtcall);
    }

    public RiResolvedMethod getRiMethod(Method reflectionMethod) {
        return (RiResolvedMethod) compiler.getVMEntries().getRiMethod(reflectionMethod);
    }

    public HotSpotCompiledMethod installMethod(RiMethod method, CiTargetMethod code) {
        Compiler compilerInstance = CompilerImpl.getInstance();
        long nmethod = HotSpotTargetMethod.installMethod(compilerInstance, (HotSpotMethodResolved) method, code, true);
        return new HotSpotCompiledMethod(compilerInstance, (HotSpotMethodResolved) method, nmethod);
    }

    @Override
    public void executeOnCompilerThread(Runnable r) {
        tasks.add(r);
        compiler.getVMEntries().notifyJavaQueue();
    }

    public void pollJavaQueue() {
        Runnable r = tasks.poll();
        while (r != null) {
            try {
                r.run();
            } catch (Throwable t) {
                t.printStackTrace();
            }
            r = tasks.poll();
        }
    }

    @Override
    public RiCompiledMethod addMethod(RiResolvedMethod method, CiTargetMethod code) {
        Compiler compilerInstance = CompilerImpl.getInstance();
        long nmethod = HotSpotTargetMethod.installMethod(compilerInstance, (HotSpotMethodResolved) method, code, false);
        return new HotSpotCompiledMethod(compilerInstance, method, nmethod);
    }
}
