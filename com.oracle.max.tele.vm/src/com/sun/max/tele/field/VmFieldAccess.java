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
package com.sun.max.tele.field;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import com.sun.cri.ci.*;
import com.sun.max.annotate.*;
import com.sun.max.atomic.*;
import com.sun.max.config.*;
import com.sun.max.ide.*;
import com.sun.max.io.*;
import com.sun.max.lang.*;
import com.sun.max.memory.*;
import com.sun.max.program.*;
import com.sun.max.tele.*;
import com.sun.max.tele.util.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.classfile.*;
import com.sun.max.vm.classfile.constant.*;
import com.sun.max.vm.code.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.ext.jvmti.*;
import com.sun.max.vm.heap.*;
import com.sun.max.vm.heap.gcx.*;
import com.sun.max.vm.heap.gcx.gen.mse.*;
import com.sun.max.vm.heap.gcx.ms.*;
import com.sun.max.vm.heap.gcx.rset.ctbl.*;
import com.sun.max.vm.heap.sequential.gen.semiSpace.*;
import com.sun.max.vm.heap.sequential.semiSpace.*;
import com.sun.max.vm.jni.*;
import com.sun.max.vm.jni.DynamicLinker.LibInfo;
import com.sun.max.vm.layout.*;
import com.sun.max.vm.log.*;
import com.sun.max.vm.log.VMLog.Record;
import com.sun.max.vm.log.java.*;
import com.sun.max.vm.log.nat.*;
import com.sun.max.vm.monitor.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.run.*;
import com.sun.max.vm.tele.*;
import com.sun.max.vm.thread.*;
import com.sun.max.vm.type.*;
import com.sun.max.vm.value.*;

/**
 * Singleton collection of access to specific object fields in VM memory.
 * <p>
 * The {@link INSPECTED} annotation is employed to denote fields that will be read remotely.
 * A field of the appropriate {@link TeleFieldAccess} subtype is generated into this file
 * by executing the {@link #main(String[])} method in this class (ensuring that the VM.
 * class path contains all the {@code com.sun.max} classes).
 */
public class VmFieldAccess extends AbstractVmHolder {

    private static final int TRACE_VALUE = 1;

    private static VmFieldAccess vmFieldsAccess;

    public static VmFieldAccess make(TeleVM vm) {
        if (vmFieldsAccess == null) {
            vmFieldsAccess = new VmFieldAccess(vm);
        }
        return vmFieldsAccess;
    }

    private VmFieldAccess(TeleVM vm) {
        super(vm);
        // Uncomment to enable verifying that the generated content in this class is up to date when running the inspector
        // updateSource(true);
    }

    // Checkstyle: stop field name check

    // VM fields:

    // START GENERATED CONTENT
    public final TeleInstanceIntFieldAccess Actor_flags = new TeleInstanceIntFieldAccess(Actor.class, "flags");
    public final TeleInstanceReferenceFieldAccess Actor_name = new TeleInstanceReferenceFieldAccess(Actor.class, "name", Utf8Constant.class);
    public final TeleInstanceReferenceFieldAccess Adapter_generator = new TeleInstanceReferenceFieldAccess(Adapter.class, "generator", AdapterGenerator.class);
    public final TeleInstanceWordFieldAccess BaseAtomicBumpPointerAllocator_end = new TeleInstanceWordFieldAccess(BaseAtomicBumpPointerAllocator.class, "end");
    public final TeleInstanceWordFieldAccess BaseAtomicBumpPointerAllocator_start = new TeleInstanceWordFieldAccess(BaseAtomicBumpPointerAllocator.class, "start");
    public final TeleInstanceWordFieldAccess BaseAtomicBumpPointerAllocator_top = new TeleInstanceWordFieldAccess(BaseAtomicBumpPointerAllocator.class, "top");
    public final TeleInstanceReferenceFieldAccess BootHeapRegion_referenceMapBytes = new TeleInstanceReferenceFieldAccess(BootHeapRegion.class, "referenceMapBytes", byte[].class);
    public final TeleInstanceReferenceFieldAccess CardTableRSet_cardTable = new TeleInstanceReferenceFieldAccess(CardTableRSet.class, "cardTable", CardTable.class);
    public final TeleInstanceReferenceFieldAccess CardTableRSet_cfoTable = new TeleInstanceReferenceFieldAccess(CardTableRSet.class, "cfoTable", CardFirstObjectTable.class);
    public final TeleInstanceReferenceFieldAccess CardTableRSet_tablesMemory = new TeleInstanceReferenceFieldAccess(CardTableRSet.class, "tablesMemory", MemoryRegion.class);
    public final TeleInstanceReferenceFieldAccess ClassActor_classLoader = new TeleInstanceReferenceFieldAccess(ClassActor.class, "classLoader", ClassLoader.class);
    public final TeleInstanceReferenceFieldAccess ClassActor_classfile = new TeleInstanceReferenceFieldAccess(ClassActor.class, "classfile", byte[].class);
    public final TeleInstanceReferenceFieldAccess ClassActor_componentClassActor = new TeleInstanceReferenceFieldAccess(ClassActor.class, "componentClassActor", ClassActor.class);
    public final TeleInstanceReferenceFieldAccess ClassActor_dynamicHub = new TeleInstanceReferenceFieldAccess(ClassActor.class, "dynamicHub", DynamicHub.class);
    public final TeleInstanceIntFieldAccess ClassActor_id = new TeleInstanceIntFieldAccess(ClassActor.class, "id");
    public final TeleInstanceReferenceFieldAccess ClassActor_javaClass = new TeleInstanceReferenceFieldAccess(ClassActor.class, "javaClass", Class.class);
    public final TeleInstanceReferenceFieldAccess ClassActor_localInstanceFieldActors = new TeleInstanceReferenceFieldAccess(ClassActor.class, "localInstanceFieldActors", FieldActor[].class);
    public final TeleInstanceReferenceFieldAccess ClassActor_localInterfaceMethodActors = new TeleInstanceReferenceFieldAccess(ClassActor.class, "localInterfaceMethodActors", InterfaceMethodActor[].class);
    public final TeleInstanceReferenceFieldAccess ClassActor_localStaticFieldActors = new TeleInstanceReferenceFieldAccess(ClassActor.class, "localStaticFieldActors", FieldActor[].class);
    public final TeleInstanceReferenceFieldAccess ClassActor_localStaticMethodActors = new TeleInstanceReferenceFieldAccess(ClassActor.class, "localStaticMethodActors", StaticMethodActor[].class);
    public final TeleInstanceReferenceFieldAccess ClassActor_localVirtualMethodActors = new TeleInstanceReferenceFieldAccess(ClassActor.class, "localVirtualMethodActors", VirtualMethodActor[].class);
    public final TeleInstanceReferenceFieldAccess ClassActor_staticHub = new TeleInstanceReferenceFieldAccess(ClassActor.class, "staticHub", StaticHub.class);
    public final TeleInstanceReferenceFieldAccess ClassActor_staticTuple = new TeleInstanceReferenceFieldAccess(ClassActor.class, "staticTuple", Object.class);
    public final TeleInstanceReferenceFieldAccess ClassActor_typeDescriptor = new TeleInstanceReferenceFieldAccess(ClassActor.class, "typeDescriptor", TypeDescriptor.class);
    public final TeleInstanceReferenceFieldAccess ClassMethodActor_codeAttribute = new TeleInstanceReferenceFieldAccess(ClassMethodActor.class, "codeAttribute", CodeAttribute.class);
    public final TeleInstanceReferenceFieldAccess ClassMethodActor_compiledState = new TeleInstanceReferenceFieldAccess(ClassMethodActor.class, "compiledState", Object.class);
    public final TeleInstanceReferenceFieldAccess ClassMethodActor_compilee = new TeleInstanceReferenceFieldAccess(ClassMethodActor.class, "compilee", ClassMethodActor.class);
    public final TeleInstanceReferenceFieldAccess ClassRegistry_bootClassRegistry = new TeleInstanceReferenceFieldAccess(ClassRegistry.class, "bootClassRegistry", ClassRegistry.class);
    public final TeleInstanceReferenceFieldAccess ClassRegistry_typeDescriptorToClassActor = new TeleInstanceReferenceFieldAccess(ClassRegistry.class, "typeDescriptorToClassActor", ConcurrentHashMap.class);
    public final TeleStaticReferenceFieldAccess Code_CODE_BOOT_NAME = new TeleStaticReferenceFieldAccess(Code.class, "CODE_BOOT_NAME", String.class);
    public final TeleStaticReferenceFieldAccess Code_bootCodeRegion = new TeleStaticReferenceFieldAccess(Code.class, "bootCodeRegion", CodeRegion.class);
    public final TeleStaticReferenceFieldAccess Code_codeManager = new TeleStaticReferenceFieldAccess(Code.class, "codeManager", CodeManager.class);
    public final TeleInstanceReferenceFieldAccess CodeAttribute_code = new TeleInstanceReferenceFieldAccess(CodeAttribute.class, "code", byte[].class);
    public final TeleInstanceReferenceFieldAccess CodeAttribute_cp = new TeleInstanceReferenceFieldAccess(CodeAttribute.class, "cp", ConstantPool.class);
    public final TeleStaticReferenceFieldAccess CodeManager_runtimeBaselineCodeRegion = new TeleStaticReferenceFieldAccess(CodeManager.class, "runtimeBaselineCodeRegion", SemiSpaceCodeRegion.class);
    public final TeleStaticReferenceFieldAccess CodeManager_runtimeOptCodeRegion = new TeleStaticReferenceFieldAccess(CodeManager.class, "runtimeOptCodeRegion", CodeRegion.class);
    public final TeleInstanceIntFieldAccess CodeRegion_additionCompletedCount = new TeleInstanceIntFieldAccess(CodeRegion.class, "additionCompletedCount");
    public final TeleInstanceIntFieldAccess CodeRegion_additionStartedCount = new TeleInstanceIntFieldAccess(CodeRegion.class, "additionStartedCount");
    public final TeleInstanceLongFieldAccess CodeRegion_evictionCompletedCount = new TeleInstanceLongFieldAccess(CodeRegion.class, "evictionCompletedCount");
    public final TeleInstanceLongFieldAccess CodeRegion_evictionStartedCount = new TeleInstanceLongFieldAccess(CodeRegion.class, "evictionStartedCount");
    public final TeleInstanceIntFieldAccess CodeRegion_length = new TeleInstanceIntFieldAccess(CodeRegion.class, "length");
    public final TeleInstanceReferenceFieldAccess CodeRegion_targetMethods = new TeleInstanceReferenceFieldAccess(CodeRegion.class, "targetMethods", TargetMethod[].class);
    public final TeleInstanceReferenceFieldAccess Compilation_prevCompilations = new TeleInstanceReferenceFieldAccess(Compilation.class, "prevCompilations", Compilations.class);
    public final TeleInstanceReferenceFieldAccess Compilations_baseline = new TeleInstanceReferenceFieldAccess(Compilations.class, "baseline", TargetMethod.class);
    public final TeleInstanceReferenceFieldAccess Compilations_optimized = new TeleInstanceReferenceFieldAccess(Compilations.class, "optimized", TargetMethod.class);
    public final TeleInstanceReferenceFieldAccess ConstantPool_constants = new TeleInstanceReferenceFieldAccess(ConstantPool.class, "constants", PoolConstant[].class);
    public final TeleInstanceReferenceFieldAccess ConstantPool_holder = new TeleInstanceReferenceFieldAccess(ConstantPool.class, "holder", ClassActor.class);
    public final TeleInstanceReferenceFieldAccess ContiguousAllocatingSpace_allocator = new TeleInstanceReferenceFieldAccess(ContiguousAllocatingSpace.class, "allocator", BaseAtomicBumpPointerAllocator.class);
    public final TeleInstanceReferenceFieldAccess ContiguousAllocatingSpace_space = new TeleInstanceReferenceFieldAccess(ContiguousAllocatingSpace.class, "space", ContiguousHeapSpace.class);
    public final TeleInstanceWordFieldAccess ContiguousHeapSpace_committedEnd = new TeleInstanceWordFieldAccess(ContiguousHeapSpace.class, "committedEnd");
    public final TeleInstanceReferenceFieldAccess ContiguousSemiSpace_fromSpace = new TeleInstanceReferenceFieldAccess(ContiguousSemiSpace.class, "fromSpace", ContiguousHeapSpace.class);
    public final TeleStaticReferenceFieldAccess DarkMatter_DARK_MATTER_ARRAY = new TeleStaticReferenceFieldAccess(DarkMatter.class, "DARK_MATTER_ARRAY", ArrayClassActor.class);
    public final TeleStaticReferenceFieldAccess DarkMatter_DARK_MATTER_CLASS_NAME = new TeleStaticReferenceFieldAccess(DarkMatter.class, "DARK_MATTER_CLASS_NAME", String.class);
    public final TeleInstanceReferenceFieldAccess Descriptor_string = new TeleInstanceReferenceFieldAccess(Descriptor.class, "string", String.class);
    public final TeleStaticReferenceFieldAccess DynamicLinker_libInfoArray = new TeleStaticReferenceFieldAccess(DynamicLinker.class, "libInfoArray", LibInfo[].class);
    public final TeleStaticIntFieldAccess DynamicLinker_libInfoIndex = new TeleStaticIntFieldAccess(DynamicLinker.class, "libInfoIndex");
    public final TeleInstanceWordFieldAccess EvacuatorToCardSpace_allocatedRangeStart = new TeleInstanceWordFieldAccess(EvacuatorToCardSpace.class, "allocatedRangeStart");
    public final TeleInstanceWordFieldAccess EvacuatorToCardSpace_initialEvacuationMark = new TeleInstanceWordFieldAccess(EvacuatorToCardSpace.class, "initialEvacuationMark");
    public final TeleInstanceWordFieldAccess EvacuatorToCardSpace_pend = new TeleInstanceWordFieldAccess(EvacuatorToCardSpace.class, "pend");
    public final TeleInstanceWordFieldAccess EvacuatorToCardSpace_ptop = new TeleInstanceWordFieldAccess(EvacuatorToCardSpace.class, "ptop");
    public final TeleInstanceReferenceFieldAccess FreeHeapSpaceManager_committedHeapSpace = new TeleInstanceReferenceFieldAccess(FreeHeapSpaceManager.class, "committedHeapSpace", ContiguousHeapSpace.class);
    public final TeleInstanceReferenceFieldAccess GenMSEHeapScheme_cardTableRSet = new TeleInstanceReferenceFieldAccess(GenMSEHeapScheme.class, "cardTableRSet", CardTableRSet.class);
    public final TeleInstanceReferenceFieldAccess GenMSEHeapScheme_oldSpace = new TeleInstanceReferenceFieldAccess(GenMSEHeapScheme.class, "oldSpace", FirstFitMarkSweepSpace.class);
    public final TeleInstanceReferenceFieldAccess GenMSEHeapScheme_youngSpace = new TeleInstanceReferenceFieldAccess(GenMSEHeapScheme.class, "youngSpace", NoAgingRegionalizedNursery.class);
    public final TeleInstanceReferenceFieldAccess GenSSHeapScheme_cardTableRSet = new TeleInstanceReferenceFieldAccess(GenSSHeapScheme.class, "cardTableRSet", CardTableRSet.class);
    public final TeleInstanceIntFieldAccess GenSSHeapScheme_fullCollectionCount = new TeleInstanceIntFieldAccess(GenSSHeapScheme.class, "fullCollectionCount");
    public final TeleInstanceWordFieldAccess GenSSHeapScheme_oldOverflowEvacuationMark = new TeleInstanceWordFieldAccess(GenSSHeapScheme.class, "oldOverflowEvacuationMark");
    public final TeleInstanceReferenceFieldAccess GenSSHeapScheme_oldSpace = new TeleInstanceReferenceFieldAccess(GenSSHeapScheme.class, "oldSpace", ContiguousSemiSpace.class);
    public final TeleInstanceReferenceFieldAccess GenSSHeapScheme_oldSpaceEvacuator = new TeleInstanceReferenceFieldAccess(GenSSHeapScheme.class, "oldSpaceEvacuator", EvacuatorToCardSpace.class);
    public final TeleInstanceReferenceFieldAccess GenSSHeapScheme_overflowedArea = new TeleInstanceReferenceFieldAccess(GenSSHeapScheme.class, "overflowedArea", MemoryRegion.class);
    public final TeleInstanceBooleanFieldAccess GenSSHeapScheme_requiresFullGC = new TeleInstanceBooleanFieldAccess(GenSSHeapScheme.class, "requiresFullGC");
    public final TeleInstanceReferenceFieldAccess GenSSHeapScheme_resizingPolicy = new TeleInstanceReferenceFieldAccess(GenSSHeapScheme.class, "resizingPolicy", GenSSHeapSizingPolicy.class);
    public final TeleInstanceWordFieldAccess GenSSHeapScheme_youngOverflowEvacuationMark = new TeleInstanceWordFieldAccess(GenSSHeapScheme.class, "youngOverflowEvacuationMark");
    public final TeleInstanceReferenceFieldAccess GenSSHeapScheme_youngSpace = new TeleInstanceReferenceFieldAccess(GenSSHeapScheme.class, "youngSpace", ContiguousAllocatingSpace.class);
    public final TeleInstanceReferenceFieldAccess GenSSHeapScheme_youngSpaceEvacuator = new TeleInstanceReferenceFieldAccess(GenSSHeapScheme.class, "youngSpaceEvacuator", NoAgingNurseryEvacuator.class);
    public final TeleInstanceBooleanFieldAccess GenSSHeapSizingPolicy_minorEvacuationOverflow = new TeleInstanceBooleanFieldAccess(GenSSHeapSizingPolicy.class, "minorEvacuationOverflow");
    public final TeleInstanceBooleanFieldAccess GenSSHeapSizingPolicy_oldEvacuationOverflow = new TeleInstanceBooleanFieldAccess(GenSSHeapSizingPolicy.class, "oldEvacuationOverflow");
    public final TeleStaticReferenceFieldAccess Heap_HEAP_BOOT_NAME = new TeleStaticReferenceFieldAccess(Heap.class, "HEAP_BOOT_NAME", String.class);
    public final TeleStaticReferenceFieldAccess Heap_bootHeapRegion = new TeleStaticReferenceFieldAccess(Heap.class, "bootHeapRegion", BootHeapRegion.class);
    public final TeleInstanceReferenceFieldAccess HeapFreeChunk_next = new TeleInstanceReferenceFieldAccess(HeapFreeChunk.class, "next", HeapFreeChunk.class);
    public final TeleInstanceWordFieldAccess HeapFreeChunk_size = new TeleInstanceWordFieldAccess(HeapFreeChunk.class, "size");
    public final TeleStaticIntFieldAccess HeapRegionConstants_regionSizeInBytes = new TeleStaticIntFieldAccess(HeapRegionConstants.class, "regionSizeInBytes");
    public final TeleInstanceIntFieldAccess HeapRegionInfo_firstFreeChunkOffset = new TeleInstanceIntFieldAccess(HeapRegionInfo.class, "firstFreeChunkOffset");
    public final TeleInstanceIntFieldAccess HeapRegionInfo_flags = new TeleInstanceIntFieldAccess(HeapRegionInfo.class, "flags");
    public final TeleInstanceIntFieldAccess HeapRegionInfo_numFreeChunks = new TeleInstanceIntFieldAccess(HeapRegionInfo.class, "numFreeChunks");
    public final TeleInstanceIntFieldAccess HeapRegionInfo_tag = new TeleInstanceIntFieldAccess(HeapRegionInfo.class, "tag");
    public final TeleInstanceReferenceFieldAccess Hub_classActor = new TeleInstanceReferenceFieldAccess(Hub.class, "classActor", ClassActor.class);
    public final TeleInstanceIntFieldAccess Hub_mTableLength = new TeleInstanceIntFieldAccess(Hub.class, "mTableLength");
    public final TeleInstanceIntFieldAccess Hub_mTableStartIndex = new TeleInstanceIntFieldAccess(Hub.class, "mTableStartIndex");
    public final TeleInstanceIntFieldAccess Hub_referenceMapLength = new TeleInstanceIntFieldAccess(Hub.class, "referenceMapLength");
    public final TeleInstanceIntFieldAccess Hub_referenceMapStartIndex = new TeleInstanceIntFieldAccess(Hub.class, "referenceMapStartIndex");
    public final TeleInstanceReferenceFieldAccess HybridClassActor_constantPool = new TeleInstanceReferenceFieldAccess(HybridClassActor.class, "constantPool", ConstantPool.class);
    public final TeleStaticReferenceFieldAccess ImmortalHeap_immortalHeap = new TeleStaticReferenceFieldAccess(ImmortalHeap.class, "immortalHeap", ImmortalMemoryRegion.class);
    public final TeleStaticIntFieldAccess Inspectable_flags = new TeleStaticIntFieldAccess(Inspectable.class, "flags");
    public final TeleStaticIntFieldAccess InspectableClassInfo_classActorCount = new TeleStaticIntFieldAccess(InspectableClassInfo.class, "classActorCount");
    public final TeleStaticReferenceFieldAccess InspectableClassInfo_classActors = new TeleStaticReferenceFieldAccess(InspectableClassInfo.class, "classActors", ClassActor[].class);
    public final TeleStaticReferenceFieldAccess InspectableCompilationInfo_breakpointClassDescriptorCharArray = new TeleStaticReferenceFieldAccess(InspectableCompilationInfo.class, "breakpointClassDescriptorCharArray", char[].class);
    public final TeleStaticIntFieldAccess InspectableCompilationInfo_breakpointClassDescriptorsCharCount = new TeleStaticIntFieldAccess(InspectableCompilationInfo.class, "breakpointClassDescriptorsCharCount");
    public final TeleStaticIntFieldAccess InspectableCompilationInfo_breakpointClassDescriptorsEpoch = new TeleStaticIntFieldAccess(InspectableCompilationInfo.class, "breakpointClassDescriptorsEpoch");
    public final TeleStaticReferenceFieldAccess InspectableHeapInfo_dynamicHeapMemoryRegions = new TeleStaticReferenceFieldAccess(InspectableHeapInfo.class, "dynamicHeapMemoryRegions", MemoryRegion[].class);
    public final TeleStaticLongFieldAccess InspectableHeapInfo_gcCompletedCounter = new TeleStaticLongFieldAccess(InspectableHeapInfo.class, "gcCompletedCounter");
    public final TeleStaticLongFieldAccess InspectableHeapInfo_gcStartedCounter = new TeleStaticLongFieldAccess(InspectableHeapInfo.class, "gcStartedCounter");
    public final TeleStaticIntFieldAccess InspectableHeapInfo_heapPhaseOrdinal = new TeleStaticIntFieldAccess(InspectableHeapInfo.class, "heapPhaseOrdinal");
    public final TeleStaticLongFieldAccess InspectableHeapInfo_recentHeapSizeRequest = new TeleStaticLongFieldAccess(InspectableHeapInfo.class, "recentHeapSizeRequest");
    public final TeleInstanceCharFieldAccess Kind_character = new TeleInstanceCharFieldAccess(Kind.class, "character");
    public final TeleInstanceWordFieldAccess DynamicLinker$LibInfo_handle = new TeleInstanceWordFieldAccess(DynamicLinker.LibInfo.class, "handle");
    public final TeleInstanceWordFieldAccess DynamicLinker$LibInfo_pathAsCString = new TeleInstanceWordFieldAccess(DynamicLinker.LibInfo.class, "pathAsCString");
    public final TeleInstanceWordFieldAccess DynamicLinker$LibInfo_sentinelAddress = new TeleInstanceWordFieldAccess(DynamicLinker.LibInfo.class, "sentinelAddress");
    public final TeleInstanceWordFieldAccess DynamicLinker$LibInfo_sentinelAsCString = new TeleInstanceWordFieldAccess(DynamicLinker.LibInfo.class, "sentinelAsCString");
    public final TeleInstanceReferenceFieldAccess LinearAllocationMemoryRegion_mark = new TeleInstanceReferenceFieldAccess(LinearAllocationMemoryRegion.class, "mark", AtomicWord.class);
    public final TeleInstanceWordFieldAccess Log2RegionToByteMapTable_coveredAreaEnd = new TeleInstanceWordFieldAccess(Log2RegionToByteMapTable.class, "coveredAreaEnd");
    public final TeleInstanceWordFieldAccess Log2RegionToByteMapTable_coveredAreaStart = new TeleInstanceWordFieldAccess(Log2RegionToByteMapTable.class, "coveredAreaStart");
    public final TeleInstanceWordFieldAccess Log2RegionToByteMapTable_tableAddress = new TeleInstanceWordFieldAccess(Log2RegionToByteMapTable.class, "tableAddress");
    public final TeleInstanceReferenceFieldAccess MSHeapScheme_heapMarker = new TeleInstanceReferenceFieldAccess(MSHeapScheme.class, "heapMarker", TricolorHeapMarker.class);
    public final TeleInstanceReferenceFieldAccess MSHeapScheme_objectSpace = new TeleInstanceReferenceFieldAccess(MSHeapScheme.class, "objectSpace", FreeHeapSpaceManager.class);
    public final TeleInstanceReferenceFieldAccess MaxineVM_config = new TeleInstanceReferenceFieldAccess(MaxineVM.class, "config", VMConfiguration.class);
    public final TeleStaticWordFieldAccess MaxineVM_primordialTLBlock = new TeleStaticWordFieldAccess(MaxineVM.class, "primordialTLBlock");
    public final TeleStaticIntFieldAccess MaxineVM_primordialTLBlockSize = new TeleStaticIntFieldAccess(MaxineVM.class, "primordialTLBlockSize");
    public final TeleStaticReferenceFieldAccess MaxineVM_vm = new TeleStaticReferenceFieldAccess(MaxineVM.class, "vm", MaxineVM.class);
    public final TeleInstanceReferenceFieldAccess MemberActor_descriptor = new TeleInstanceReferenceFieldAccess(MemberActor.class, "descriptor", Descriptor.class);
    public final TeleInstanceReferenceFieldAccess MemberActor_holder = new TeleInstanceReferenceFieldAccess(MemberActor.class, "holder", ClassActor.class);
    public final TeleInstanceReferenceFieldAccess MemoryRegion_regionName = new TeleInstanceReferenceFieldAccess(MemoryRegion.class, "regionName", String.class);
    public final TeleInstanceWordFieldAccess MemoryRegion_size = new TeleInstanceWordFieldAccess(MemoryRegion.class, "size");
    public final TeleInstanceWordFieldAccess MemoryRegion_start = new TeleInstanceWordFieldAccess(MemoryRegion.class, "start");
    public final TeleInstanceWordFieldAccess JVMTIRawMonitor$Monitor_name = new TeleInstanceWordFieldAccess(JVMTIRawMonitor.Monitor.class, "name");
    public final TeleInstanceReferenceFieldAccess JVMTIRawMonitor$Monitor_owner = new TeleInstanceReferenceFieldAccess(JVMTIRawMonitor.Monitor.class, "owner", VmThread.class);
    public final TeleInstanceReferenceFieldAccess NoAgingRegionalizedNursery_allocator = new TeleInstanceReferenceFieldAccess(NoAgingRegionalizedNursery.class, "allocator", AtomicBumpPointerAllocator.class);
    public final TeleInstanceReferenceFieldAccess ObjectReferenceValue_value = new TeleInstanceReferenceFieldAccess(ObjectReferenceValue.class, "value", Object.class);
    public final TeleInstanceIntFieldAccess VMLogArray$Record0_header = new TeleInstanceIntFieldAccess(VMLogArray.Record0.class, "header");
    public final TeleInstanceWordFieldAccess VMLogArray$Record1_arg1 = new TeleInstanceWordFieldAccess(VMLogArray.Record1.class, "arg1");
    public final TeleInstanceWordFieldAccess VMLogArray$Record2_arg2 = new TeleInstanceWordFieldAccess(VMLogArray.Record2.class, "arg2");
    public final TeleInstanceWordFieldAccess VMLogArray$Record3_arg3 = new TeleInstanceWordFieldAccess(VMLogArray.Record3.class, "arg3");
    public final TeleInstanceWordFieldAccess VMLogArray$Record4_arg4 = new TeleInstanceWordFieldAccess(VMLogArray.Record4.class, "arg4");
    public final TeleInstanceWordFieldAccess VMLogArray$Record5_arg5 = new TeleInstanceWordFieldAccess(VMLogArray.Record5.class, "arg5");
    public final TeleInstanceWordFieldAccess VMLogArray$Record6_arg6 = new TeleInstanceWordFieldAccess(VMLogArray.Record6.class, "arg6");
    public final TeleInstanceWordFieldAccess VMLogArray$Record7_arg7 = new TeleInstanceWordFieldAccess(VMLogArray.Record7.class, "arg7");
    public final TeleInstanceWordFieldAccess VMLogArray$Record8_arg8 = new TeleInstanceWordFieldAccess(VMLogArray.Record8.class, "arg8");
    public final TeleInstanceIntFieldAccess RegionTable_length = new TeleInstanceIntFieldAccess(RegionTable.class, "length");
    public final TeleInstanceIntFieldAccess RegionTable_regionInfoSize = new TeleInstanceIntFieldAccess(RegionTable.class, "regionInfoSize");
    public final TeleInstanceWordFieldAccess RegionTable_regionPoolEnd = new TeleInstanceWordFieldAccess(RegionTable.class, "regionPoolEnd");
    public final TeleInstanceWordFieldAccess RegionTable_regionPoolStart = new TeleInstanceWordFieldAccess(RegionTable.class, "regionPoolStart");
    public final TeleStaticReferenceFieldAccess RegionTable_theRegionTable = new TeleStaticReferenceFieldAccess(RegionTable.class, "theRegionTable", RegionTable.class);
    public final TeleInstanceReferenceFieldAccess ClassConstant$Resolved_classActor = new TeleInstanceReferenceFieldAccess(ClassConstant.Resolved.class, "classActor", ClassActor.class);
    public final TeleInstanceReferenceFieldAccess FieldRefConstant$Resolved_fieldActor = new TeleInstanceReferenceFieldAccess(FieldRefConstant.Resolved.class, "fieldActor", FieldActor.class);
    public final TeleInstanceReferenceFieldAccess ResolvedMethodRefConstant_methodActor = new TeleInstanceReferenceFieldAccess(ResolvedMethodRefConstant.class, "methodActor", MethodActor.class);
    public final TeleInstanceIntFieldAccess SemiSpaceCodeRegion_fromLength = new TeleInstanceIntFieldAccess(SemiSpaceCodeRegion.class, "fromLength");
    public final TeleInstanceWordFieldAccess SemiSpaceCodeRegion_fromSpace = new TeleInstanceWordFieldAccess(SemiSpaceCodeRegion.class, "fromSpace");
    public final TeleInstanceReferenceFieldAccess SemiSpaceCodeRegion_fromTargetMethods = new TeleInstanceReferenceFieldAccess(SemiSpaceCodeRegion.class, "fromTargetMethods", TargetMethod[].class);
    public final TeleInstanceWordFieldAccess SemiSpaceCodeRegion_spaceSize = new TeleInstanceWordFieldAccess(SemiSpaceCodeRegion.class, "spaceSize");
    public final TeleInstanceWordFieldAccess SemiSpaceCodeRegion_toSpace = new TeleInstanceWordFieldAccess(SemiSpaceCodeRegion.class, "toSpace");
    public final TeleInstanceWordFieldAccess SemiSpaceCodeRegion_topOfSpace = new TeleInstanceWordFieldAccess(SemiSpaceCodeRegion.class, "topOfSpace");
    public final TeleInstanceReferenceFieldAccess SemiSpaceHeapScheme_fromSpace = new TeleInstanceReferenceFieldAccess(SemiSpaceHeapScheme.class, "fromSpace", LinearAllocationMemoryRegion.class);
    public final TeleInstanceReferenceFieldAccess SemiSpaceHeapScheme_toSpace = new TeleInstanceReferenceFieldAccess(SemiSpaceHeapScheme.class, "toSpace", LinearAllocationMemoryRegion.class);
    public final TeleInstanceReferenceFieldAccess StringConstant_value = new TeleInstanceReferenceFieldAccess(StringConstant.class, "value", String.class);
    public final TeleStaticReferenceFieldAccess TargetMethod_WIPED_CODE = new TeleStaticReferenceFieldAccess(TargetMethod.class, "WIPED_CODE", byte[].class);
    public final TeleInstanceReferenceFieldAccess TargetMethod_callEntryPoint = new TeleInstanceReferenceFieldAccess(TargetMethod.class, "callEntryPoint", CallEntryPoint.class);
    public final TeleInstanceReferenceFieldAccess TargetMethod_classMethodActor = new TeleInstanceReferenceFieldAccess(TargetMethod.class, "classMethodActor", ClassMethodActor.class);
    public final TeleInstanceReferenceFieldAccess TargetMethod_code = new TeleInstanceReferenceFieldAccess(TargetMethod.class, "code", byte[].class);
    public final TeleInstanceWordFieldAccess TargetMethod_codeStart = new TeleInstanceWordFieldAccess(TargetMethod.class, "codeStart");
    public final TeleInstanceWordFieldAccess TargetMethod_oldStart = new TeleInstanceWordFieldAccess(TargetMethod.class, "oldStart");
    public final TeleInstanceReferenceFieldAccess TargetMethod_referenceLiterals = new TeleInstanceReferenceFieldAccess(TargetMethod.class, "referenceLiterals", Object[].class);
    public final TeleInstanceReferenceFieldAccess TargetMethod_scalarLiterals = new TeleInstanceReferenceFieldAccess(TargetMethod.class, "scalarLiterals", byte[].class);
    public final TeleStaticIntFieldAccess Trace_level = new TeleStaticIntFieldAccess(Trace.class, "level");
    public final TeleStaticLongFieldAccess Trace_threshold = new TeleStaticLongFieldAccess(Trace.class, "threshold");
    public final TeleInstanceWordFieldAccess TricolorHeapMarker_base = new TeleInstanceWordFieldAccess(TricolorHeapMarker.class, "base");
    public final TeleInstanceReferenceFieldAccess TricolorHeapMarker_colorMap = new TeleInstanceReferenceFieldAccess(TricolorHeapMarker.class, "colorMap", MemoryRegion.class);
    public final TeleInstanceWordFieldAccess TricolorHeapMarker_coveredAreaEnd = new TeleInstanceWordFieldAccess(TricolorHeapMarker.class, "coveredAreaEnd");
    public final TeleInstanceWordFieldAccess TricolorHeapMarker_coveredAreaStart = new TeleInstanceWordFieldAccess(TricolorHeapMarker.class, "coveredAreaStart");
    public final TeleInstanceIntFieldAccess TricolorHeapMarker_wordsCoveredPerBit = new TeleInstanceIntFieldAccess(TricolorHeapMarker.class, "wordsCoveredPerBit");
    public final TeleInstanceReferenceFieldAccess TupleClassActor_constantPool = new TeleInstanceReferenceFieldAccess(TupleClassActor.class, "constantPool", ConstantPool.class);
    public final TeleInstanceReferenceFieldAccess Utf8Constant_string = new TeleInstanceReferenceFieldAccess(Utf8Constant.class, "string", String.class);
    public final TeleInstanceReferenceFieldAccess VMConfiguration_heapScheme = new TeleInstanceReferenceFieldAccess(VMConfiguration.class, "heapScheme", HeapScheme.class);
    public final TeleInstanceReferenceFieldAccess VMConfiguration_layoutScheme = new TeleInstanceReferenceFieldAccess(VMConfiguration.class, "layoutScheme", LayoutScheme.class);
    public final TeleInstanceReferenceFieldAccess VMConfiguration_monitorScheme = new TeleInstanceReferenceFieldAccess(VMConfiguration.class, "monitorScheme", MonitorScheme.class);
    public final TeleInstanceReferenceFieldAccess VMConfiguration_referenceScheme = new TeleInstanceReferenceFieldAccess(VMConfiguration.class, "referenceScheme", ReferenceScheme.class);
    public final TeleInstanceReferenceFieldAccess VMConfiguration_runScheme = new TeleInstanceReferenceFieldAccess(VMConfiguration.class, "runScheme", RunScheme.class);
    public final TeleInstanceIntFieldAccess VMLog_logEntries = new TeleInstanceIntFieldAccess(VMLog.class, "logEntries");
    public final TeleInstanceReferenceFieldAccess VMLog_loggers = new TeleInstanceReferenceFieldAccess(VMLog.class, "loggers", VMLogger[].class);
    public final TeleInstanceIntFieldAccess VMLog_nextId = new TeleInstanceIntFieldAccess(VMLog.class, "nextId");
    public final TeleStaticReferenceFieldAccess VMLog_vmLog = new TeleStaticReferenceFieldAccess(VMLog.class, "vmLog", VMLog.class);
    public final TeleInstanceReferenceFieldAccess VMLogArray_buffer = new TeleInstanceReferenceFieldAccess(VMLogArray.class, "buffer", Record[].class);
    public final TeleInstanceIntFieldAccess VMLogNative_defaultNativeRecordSize = new TeleInstanceIntFieldAccess(VMLogNative.class, "defaultNativeRecordSize");
    public final TeleInstanceWordFieldAccess VMLogNative_logBuffer = new TeleInstanceWordFieldAccess(VMLogNative.class, "logBuffer");
    public final TeleInstanceIntFieldAccess VMLogNative_logSize = new TeleInstanceIntFieldAccess(VMLogNative.class, "logSize");
    public final TeleInstanceIntFieldAccess VMLogNative_nativeRecordArgsOffset = new TeleInstanceIntFieldAccess(VMLogNative.class, "nativeRecordArgsOffset");
    public final TeleInstanceReferenceFieldAccess VmThread_name = new TeleInstanceReferenceFieldAccess(VmThread.class, "name", String.class);
    public final TeleStaticReferenceFieldAccess VmThreadLocal_VALUES = new TeleStaticReferenceFieldAccess(VmThreadLocal.class, "VALUES", List.class);
    // END GENERATED CONTENT

    // Injected JDK fields:

    public final TeleInstanceReferenceFieldAccess Class_classActor = new TeleInstanceReferenceFieldAccess(Class.class, ClassActor.class, InjectedReferenceFieldActor.Class_classActor);
    public final TeleInstanceReferenceFieldAccess ClassLoader_classRegistry = new TeleInstanceReferenceFieldAccess(ClassLoader.class, ClassRegistry.class, InjectedReferenceFieldActor.ClassLoader_classRegistry);
    public final TeleInstanceReferenceFieldAccess Field_fieldActor = new TeleInstanceReferenceFieldAccess(Field.class, FieldActor.class, InjectedReferenceFieldActor.Field_fieldActor);
    public final TeleInstanceReferenceFieldAccess Method_methodActor = new TeleInstanceReferenceFieldAccess(Method.class, MethodActor.class, InjectedReferenceFieldActor.Method_methodActor);
    public final TeleInstanceReferenceFieldAccess Constructor_methodActor = new TeleInstanceReferenceFieldAccess(Constructor.class, MethodActor.class, InjectedReferenceFieldActor.Constructor_methodActor);

    // Other JDK fields:

    private final Class ConcurrentHashMap$Segment = Classes.getInnerClass(ConcurrentHashMap.class, "Segment");
    private final Class ConcurrentHashMap$HashEntry = Classes.getInnerClass(ConcurrentHashMap.class, "HashEntry");
    public final TeleInstanceReferenceFieldAccess ConcurrentHashMap_segments = new TeleInstanceReferenceFieldAccess(ConcurrentHashMap.class, "segments", Array.newInstance(ConcurrentHashMap$Segment, 0).getClass());
    public final TeleInstanceReferenceFieldAccess ConcurrentHashMap$Segment_table = new TeleInstanceReferenceFieldAccess(ConcurrentHashMap$Segment, "table", Array.newInstance(ConcurrentHashMap$HashEntry, 0).getClass());
    public final TeleInstanceReferenceFieldAccess ConcurrentHashMap$HashEntry_next = new TeleInstanceReferenceFieldAccess(ConcurrentHashMap$HashEntry, "next", ConcurrentHashMap$HashEntry);
    public final TeleInstanceReferenceFieldAccess ConcurrentHashMap$HashEntry_value = new TeleInstanceReferenceFieldAccess(ConcurrentHashMap$HashEntry, "value", Object.class);
    public final TeleInstanceIntFieldAccess Enum_ordinal = new TeleInstanceIntFieldAccess(Enum.class, "ordinal");
    public final TeleInstanceReferenceFieldAccess String_value = new TeleInstanceReferenceFieldAccess(String.class, "value", char[].class);

    // Other fields:

    public final TeleInstanceReferenceFieldAccess CiConstant_object = new TeleInstanceReferenceFieldAccess(CiConstant.class, "object", Object.class);

    // Checkstyle: resume field name check

    public interface InspectedMemberReifier<Member_Type extends Member> {
        /**
         * Reifies a {@link Method}, {@link Field} or {@link Constructor} annotated with {@link INSPECTED} found on a
         * classpath search.
         *
         * @param member the member to be reified
         * @param writer the Java source to which the declaration of the reified member should be written
         */
        void reify(Member_Type member, IndentWriter writer);
    }

    public static <Member_Type extends Member> void updateSource(final Class sourceClass, final Class<Member_Type> memberClass, final InspectedMemberReifier<Member_Type> memberReifier, final boolean inInspector) {
        final File sourceFile = new File(JavaProject.findSourceDirectory(VmFieldAccess.class), sourceClass.getName().replace('.', File.separatorChar) + ".java").getAbsoluteFile();
        if (!sourceFile.exists()) {
            TeleWarning.message("Source file does not exist: " + sourceFile.getAbsolutePath());
        }
        final Runnable runnable = new Runnable() {
            public void run() {
                final Classpath classpath = Classpath.fromSystem();
                final PackageLoader packageLoader = new PackageLoader(ClassLoader.getSystemClassLoader(), classpath);
                if (inInspector) {
                    packageLoader.setTraceLevel(Integer.MAX_VALUE);
                }
                final CharArraySource charArrayWriter = new CharArraySource();
                final IndentWriter writer = new IndentWriter(new PrintWriter(charArrayWriter));
                writer.indent();
                final Set<Member> reified = new TreeSet<Member>(new Comparator<Member>() {
                    public int compare(Member member1, Member member2) {
                        if (member1.equals(member2)) {
                            return 0;
                        }
                        final int classNameComparison = member1.getDeclaringClass().getSimpleName().compareTo(member2.getDeclaringClass().getSimpleName());
                        if (classNameComparison != 0) {
                            return classNameComparison;
                        }
                        final int result = member1.getName().compareTo(member2.getName());
                        assert result != 0 : member1 + " " + member2;
                        return result;
                    }

                });

                new ClassSearch() {
                    final HashSet<String> seenPackages = new HashSet<String>();
                    @Override
                    protected boolean visitClass(boolean isArchiveEntry, String className) {
                        if (!className.endsWith("package-info")) {
                            Class c = Classes.forName(className, false, getClass().getClassLoader());
                            String pkg = Classes.getPackageName(className);
                            if (seenPackages.add(pkg)) {
                                Trace.line(1, pkg);
                            }
                            final AccessibleObject[] members = memberClass.equals(Method.class) ? c.getDeclaredMethods() : (memberClass.equals(Field.class) ? c.getDeclaredFields() : c.getDeclaredConstructors());
                            for (AccessibleObject member : members) {
                                if (member.getAnnotation(INSPECTED.class) != null) {
                                    if (!reified.contains(member)) {
                                        reified.add((Member) member);
                                    }

                                }
                            }
                        }
                        return true;
                    }
                }.run(Classpath.fromSystem(), "com/sun/max");

                for (Member member : reified) {
                    memberReifier.reify(memberClass.cast(member), writer);
                }

                try {
                    final boolean changed = Files.updateGeneratedContent(sourceFile, charArrayWriter, "    // START GENERATED CONTENT", "    // END GENERATED CONTENT", false);
                    if (changed) {
                        TeleWarning.message("The source file " + sourceFile + " was updated" + (inInspector ? ": recompile and restart the inspector" : ""));
                    } else {
                        Trace.line(1, "The source file " + sourceFile + " did not need to be updated.");
                    }
                } catch (IOException exception) {
                    if (inInspector) {
                        TeleWarning.message("Error while verifying that " + sourceFile + " is up to date", exception);
                    } else {
                        TeleError.unexpected(exception);
                    }
                }
            }
        };

        if (!inInspector) {
            runnable.run();
        } else {
            final Runnable inspectorRunnable = new Runnable() {
                public void run() {
                    Trace.begin(1, "Verifying that " + sourceClass + " is up to date");
                    try {
                        runnable.run();
                    } finally {
                        Trace.end(1, "Verifying that " + sourceClass + " is up to date");
                    }
                }
            };
            final Thread thread = new Thread(inspectorRunnable, "Inspected" + memberClass.getSimpleName() + "s verifier");
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.start();
        }
    }

    public static void main(String[] args) {
        Trace.on(1);
        Trace.begin(1, "VmFields updating GENERATED CONTENT");
        updateSource(false);
        Trace.end(1, "VmFields updating GENERATED CONTENT");
    }

    private static void updateSource(boolean inInspector) {
        final InspectedMemberReifier<Field> fieldReifier = new InspectedMemberReifier<Field>() {
            public void reify(Field field, IndentWriter writer) {
                final Class c = field.getDeclaringClass();
                final boolean isStatic = Modifier.isStatic(field.getModifiers());
                final Class type = field.getType();
                final Kind kind = Word.class.isAssignableFrom(type) ? Kind.WORD : Kind.fromJava(type);
                final String holder = c.getName().substring(c.getPackage().getName().length() + 1);
                final String name = field.getName();
                final String kindName = kind.toString();
                final String inspectorFieldName = holder + (name.charAt(0) == '_' ? name : '_' + name);
                final String inspectorFieldType = "Tele" + (isStatic ? "Static" : "Instance") + Strings.capitalizeFirst(kindName, true) + "FieldAccess";
                writer.print("public final " + inspectorFieldType + " " + inspectorFieldName + " = ");

                switch (kind.asEnum) {
                    case BOOLEAN:
                    case BYTE:
                    case CHAR:
                    case SHORT:
                    case INT:
                    case FLOAT:
                    case LONG:
                    case DOUBLE:
                    case WORD: {
                        writer.print("new " + inspectorFieldType + "(" + holder.replace('$', '.') + ".class, \"" + name + "\")");
                        break;
                    }
                    case REFERENCE: {
                        writer.print("new " + inspectorFieldType + "(" + holder.replace('$', '.') + ".class, \"" + name + "\", " +  type.getSimpleName() + ".class)");
                        break;
                    }
                    default: {
                        TeleError.unexpected("Invalid field kind: " + kind);
                    }
                }
                writer.println(";");
            }
        };
        updateSource(VmFieldAccess.class, Field.class, fieldReifier, inInspector);
    }
}
