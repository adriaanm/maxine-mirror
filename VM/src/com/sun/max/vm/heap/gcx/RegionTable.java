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
package com.sun.max.vm.heap.gcx;
import static com.sun.cri.bytecode.Bytecodes.*;
import static com.sun.max.vm.heap.gcx.HeapRegionConstants.*;

import com.sun.cri.bytecode.*;
import com.sun.max.annotate.*;
import com.sun.max.memory.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.heap.*;
import com.sun.max.vm.layout.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.runtime.*;

/**
 * The heap region table centralizes all the descriptors for all regions in the heap space.
 * Region descriptors of all the regions are allocated in a single contiguous space in order
 * to enable a simple mapping from region address to region descriptor, which in turn, enables
 * direct retrieval of a region descriptor from an arbitrary pointer to the heap space.
 * The index in the region table forms a unique region identifier that can be simply maps to a region
 * descriptor and the region address in the heap space. The region table occupies the first bytes of
 * the heap space.
 */
public final class RegionTable {
    static final int TableOffset = ClassActor.fromJava(RegionTable.class).dynamicTupleSize().toInt();

    @CONSTANT_WHEN_NOT_ZERO
    private static RegionTable theRegionTable;

    @INLINE
    static RegionTable theRegionTable() {
        return theRegionTable;
    }

    @INTRINSIC(UNSAFE_CAST)
    private static native HeapRegionInfo asHeapRegionInfo(Object regionInfo);

    @INLINE
    private static HeapRegionInfo toHeapRegionInfo(Pointer regionInfoPointer) {
        return asHeapRegionInfo(Reference.fromOrigin(Layout.cellToOrigin(regionInfoPointer)).toJava());
    }

    private Pointer table() {
        return Reference.fromJava(this).toOrigin().plus(TableOffset);
    }

    final private Address regionPoolStart;

    final private Address regionPoolEnd;

    final private int regionInfoSize;

    final private int length;

    private boolean isInHeapRegion(Address address) {
        return address.greaterEqual(regionPoolStart) && address.lessThan(regionPoolEnd);
    }

    private RegionTable(Class<HeapRegionInfo> regionInfoClass, MemoryRegion regionPool, int numRegions) {
        final HeapScheme heapScheme = VMConfiguration.vmConfig().heapScheme();
        final Hub regionInfoHub = ClassActor.fromJava(regionInfoClass).dynamicHub();
        regionPoolStart = regionPool.start();
        regionPoolEnd = regionPool.end();
        length = numRegions;
        regionInfoSize = regionInfoHub.tupleSize.toInt();

        for (int i = 0; i < numRegions; i++) {
            Object regionInfo = heapScheme.createTuple(regionInfoHub);
            if (MaxineVM.isDebug()) {
                FatalError.check(regionInfo == regionInfo(i), "Failed to create valid region table");
            }
        }
    }

    static void initialize(Class<HeapRegionInfo> regionInfoClass, MemoryRegion regionPool, int numRegions) {
        theRegionTable = new RegionTable(regionInfoClass, regionPool, numRegions);
    }

    int regionID(HeapRegionInfo regionInfo) {
        final int regionID = Reference.fromJava(regionInfo).toOrigin().minus(table()).dividedBy(regionInfoSize).toInt();
        return regionID;
    }

    int regionID(Address addr) {
        if (!isInHeapRegion(addr)) {
            return INVALID_REGION_ID;
        }
        return addr.minus(regionPoolStart).unsignedShiftedRight(log2RegionSizeInBytes).toInt();
    }

    HeapRegionInfo regionInfo(int regionID) {
        return toHeapRegionInfo(table().plus(regionID * regionInfoSize));
    }

    HeapRegionInfo regionInfo(Address addr) {
        if (!isInHeapRegion(addr)) {
            return null;
        }
        return regionInfo(regionID(addr));
    }

    Address regionAddress(int regionID) {
        return regionPoolStart.plus(regionID << log2RegionSizeInBytes);
    }

    Address regionAddress(HeapRegionInfo regionInfo) {
        return regionAddress(regionID(regionInfo));
    }

    HeapRegionInfo next(HeapRegionInfo regionInfo) {
        return toHeapRegionInfo(Reference.fromJava(regionInfo).toOrigin().plus(regionInfoSize));
    }

    HeapRegionInfo prev(HeapRegionInfo regionInfo) {
        return toHeapRegionInfo(Reference.fromJava(regionInfo).toOrigin().minus(regionInfoSize));
    }
}
