/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.tele.memory;

import java.io.*;
import java.text.*;
import java.util.*;

import com.sun.max.lang.*;
import com.sun.max.program.*;
import com.sun.max.tele.*;
import com.sun.max.tele.object.*;
import com.sun.max.unsafe.*;

/**
 * Description of an allocation area in the VM owned by the heap.
 * <p>
 * Interaction with objects in the area is delegated to an instance of {@link RemoteObjectReferenceManager}, which permits
 * specialized implementations of {@link Reference}s to be created that embody knowledge of specific heap implementations in the VM.
 * <p>
 * If no {@link RemoteObjectReferenceManager} is specified, the default is an instance of {@link FixedObjectRemoteReferenceManager},
 * whose implementation assumes that the allocation never moves and that it is unmanaged:  objects, once
 * created, are never moved or collected.
 *
 * @see RemoteObjectReferenceManager
 */
public class VmHeapRegion extends AbstractVmHolder implements MaxHeapRegion, ObjectHoldingRegion {

    private static final int TRACE_VALUE = 1;
    private static final List<MaxEntityMemoryRegion< ? extends MaxEntity>> EMPTY_REGION_LIST = Collections.emptyList();

    private final String entityDescription = "An allocation area owned by the VM heap";
    private final TeleRuntimeMemoryRegion teleRuntimeMemoryRegion;
    private final boolean isBootRegion;
    private final MaxEntityMemoryRegion<MaxHeapRegion> memoryRegion;
    private final RemoteObjectReferenceManager objectReferenceManager;

    /**
     * Creates a description of a heap allocation region in the VM, with information drawn from
     * a VM object describing the memory region.  The region is assumed to be at a fixed location, and
     * it is assumed to be unmanaged: objects, once created are never moved or collected.
     */
    public VmHeapRegion(TeleVM vm, TeleRuntimeMemoryRegion teleRuntimeMemoryRegion, boolean isBootRegion) {
        super(vm);
        this.teleRuntimeMemoryRegion = teleRuntimeMemoryRegion;
        this.isBootRegion = isBootRegion;
        this.memoryRegion = new DelegatedHeapRegionMemoryRegion(vm, teleRuntimeMemoryRegion);
        this.objectReferenceManager = new FixedObjectRemoteReferenceManager(vm, this);
        Trace.line(TRACE_VALUE, tracePrefix() + "heap region created for " + memoryRegion.regionName() + " with " + objectReferenceManager.getClass().getSimpleName());
    }

    /**
     * Creates a description of a heap allocation region in the VM, with information about the
     * memory region described explicitly.  The region is assumed to be at a fixed location, and
     * it is assumed to be unmanaged: objects, once created are never moved or collected.
     */
    public VmHeapRegion(TeleVM vm, String name, Address start, long nBytes, boolean isBootRegion) {
        super(vm);
        this.teleRuntimeMemoryRegion = null;
        this.isBootRegion = isBootRegion;
        this.memoryRegion = new FixedHeapRegionMemoryRegion(vm, name, start, nBytes);
        this.objectReferenceManager = new FixedObjectRemoteReferenceManager(vm, this);
        Trace.line(TRACE_VALUE, tracePrefix() + "heap region created for " + memoryRegion.regionName() + " with " + objectReferenceManager.getClass().getSimpleName());
    }

    public final String entityName() {
        return memoryRegion().regionName();
    }

    public final String entityDescription() {
        return entityDescription;
    }

    public MaxEntityMemoryRegion<MaxHeapRegion> memoryRegion() {
        return memoryRegion;
    }

    public final boolean contains(Address address) {
        return memoryRegion().contains(address);
    }

    public TeleObject representation() {
        return teleRuntimeMemoryRegion;
    }

    public final boolean isBootRegion() {
        return memoryRegion().isBootRegion();
    }

    public RemoteObjectReferenceManager objectReferenceManager() {
        return objectReferenceManager;
    }

    public void printSessionStats(PrintStream printStream, int indent, boolean verbose) {
        final String indentation = Strings.times(' ', indent);
        final NumberFormat formatter = NumberFormat.getInstance();
        // Line 1
        final StringBuilder sb1 = new StringBuilder();
        sb1.append(entityName());
        sb1.append(": size=" + formatter.format(memoryRegion().nBytes()));
        printStream.println(indentation + sb1.toString());
        // Line 2
        final StringBuilder sb2 = new StringBuilder();
        final int activeReferenceCount = objectReferenceManager.activeReferenceCount();
        final int totalReferenceCount = objectReferenceManager.totalReferenceCount();
        sb2.append("object refs:  active=" + formatter.format(activeReferenceCount));
        sb2.append(", inactive=" + formatter.format(totalReferenceCount - activeReferenceCount));
        sb2.append(", mgr=" + objectReferenceManager.getClass().getSimpleName());
        printStream.println(indentation + "      " + sb2.toString());
    }

    public void updateStatus(long epoch) {
        teleRuntimeMemoryRegion.updateCache(epoch);
    }

    /**
     * Description of an ordinary memory region allocated by the VM heap, as described by a VM object.
     * <p>
     * This region has no parent; it is allocated from the OS.
     * <p>
     * This region has no children. We could decompose the region into sub-regions representing individual objects, but
     * we don't do that at this time.
     */
    private final class DelegatedHeapRegionMemoryRegion extends TeleDelegatedMemoryRegion implements MaxEntityMemoryRegion<MaxHeapRegion> {

        protected DelegatedHeapRegionMemoryRegion(MaxVM vm, TeleRuntimeMemoryRegion teleRuntimeMemoryRegion) {
            super(vm, teleRuntimeMemoryRegion);
            assert teleRuntimeMemoryRegion != null;
        }

        public MaxEntityMemoryRegion< ? extends MaxEntity> parent() {
            // Heap regions are allocated from the OS, not part of any other region
            return null;
        }

        public List<MaxEntityMemoryRegion< ? extends MaxEntity>> children() {
            // We don't break a heap memory region into any smaller entities, but could.
            return EMPTY_REGION_LIST;
        }

        public VmHeapRegion owner() {
            return VmHeapRegion.this;
        }

        public boolean isBootRegion() {
            return isBootRegion;
        }
    }

    /**
     * Description of a memory region allocated by the VM heap, where the description is known completely
     * without reference to a VM object.
     * <p>
     * This region has no parent; it is allocated from the OS.
     * <p>
     * This region has no children. We could decompose the region into sub-regions representing individual objects, but
     * we don't do that at this time.
     */
    private final class FixedHeapRegionMemoryRegion extends TeleFixedMemoryRegion implements MaxEntityMemoryRegion<MaxHeapRegion> {

        protected FixedHeapRegionMemoryRegion(MaxVM vm, String regionName, Address start, long nBytes) {
            super(vm, regionName, start, nBytes);
        }

        public MaxEntityMemoryRegion< ? extends MaxEntity> parent() {
            // Heap regions are allocated from the OS, not part of any other region
            return null;
        }

        public List<MaxEntityMemoryRegion< ? extends MaxEntity>> children() {
            // We don't break a heap memory region into any smaller entities, but could.
            return EMPTY_REGION_LIST;
        }

        public VmHeapRegion owner() {
            return VmHeapRegion.this;
        }

        public boolean isBootRegion() {
            return isBootRegion;
        }
    }


}
