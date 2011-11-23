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
package com.sun.max.vm.heap.gcx;

import static com.sun.max.vm.heap.HeapSchemeAdaptor.*;

import com.sun.max.memory.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.layout.*;
/**
 * A simple evacuator that evacuates only from one space to another, without aging.
 * The evacuator is parameterized with two heap space.
 *
 * TODO: move allocation and cfotable update code into a wrapper of the FirsFitMarkSweepSpace.
 * The wrapper keeps track of survivor ranges and update the remembered set (mostly the cfo table).
 * This makes the evacuator independent of the detail of survivor rangestracking and imprecise rset subtleties.
 */
public final class NoAgingEvacuator extends Evacuator {
    private static final Size LAB_HEADROOM = MIN_OBJECT_SIZE;
    /**
     * Heap Space that is being evacuated.
     */
    final HeapSpace fromSpace;

    /**
     * Heap Space where the evacuated cells will relocate.
     */
    final HeapSpace toSpace;

    /**
     * Threshold below which refills of the thread local promotion space is automatic.
     */
    private final Size minRefillThreshold;

    /**
     * Hint of size of local allocation buffer when refilling.
     */
    private final Size labSize;
    /**
     * Cached card first object table for fast access.
     */
    private final CardFirstObjectTable cfoTable;

    /**
     * Amount of promoted bytes.
     */
    private Size promotedBytes;

   /**
     * Allocation hand in private promotion space.
     */
    private Pointer top;
    /***
     * End of the private promotion space.
     */
    private Pointer end;

    private Address nextLABChunk;

    /**
     * Mark to keep track of allocated ranges recording.
     */
    private Address allocatedRangeStart;

    /**
     * Mark to keep track of ranges from overflow allocation to record.
     */
    private Address lastOverflowAllocatedRangeStart;
    private Address lastOverflowAllocatedRangeEnd;

    /**
     * Queue of survivor ranges remaining to process for evacuation.
     */
    private final SurvivorRangesQueue survivorRanges;


    public NoAgingEvacuator(HeapSpace fromSpace, HeapSpace toSpace, CardTableRSet rset, Size minRefillThreshold, SurvivorRangesQueue queue, Size labSize) {
        this.fromSpace = fromSpace;
        this.toSpace = toSpace;
        this.cfoTable = rset.cfoTable;
        this.minRefillThreshold = minRefillThreshold;
        this.survivorRanges = queue;
        this.labSize = labSize;
    }

    @Override
    protected void doBeforeEvacuation() {
        promotedBytes = Size.zero();
        top = Pointer.zero();
        end = Pointer.zero();
        nextLABChunk = Pointer.zero();
        allocatedRangeStart = Pointer.zero();
        lastOverflowAllocatedRangeStart = Pointer.zero();
        lastOverflowAllocatedRangeEnd = Pointer.zero();
        fromSpace.doBeforeGC();
    }

    @Override
    protected void doAfterEvacuation() {
        // Give back LAB allocator ?
        survivorRanges.clear();
        fromSpace.doAfterGC();
    }

    private void recordRange(Address start, Address end) {
        promotedBytes = promotedBytes.plus(end.minus(start));
        survivorRanges.add(start, end);
    }

    private void updateSurvivorRanges() {
        if (allocatedRangeStart.greaterThan(top)) {
            recordRange(allocatedRangeStart, top);
            allocatedRangeStart = top;
        }
        if (lastOverflowAllocatedRangeEnd.greaterThan(lastOverflowAllocatedRangeStart)) {
            recordRange(lastOverflowAllocatedRangeStart, lastOverflowAllocatedRangeEnd);
            lastOverflowAllocatedRangeStart = lastOverflowAllocatedRangeEnd;
        }
    }

    Pointer refillOrAllocate(Size size) {
        if (size.lessThan(minRefillThreshold)) {
            // check if request can fit in the remaining space when taking the headroom into account.
            Pointer limit = end.plus(LAB_HEADROOM);
            if (top.plus(size).lessEqual(limit)) {
                // Does fit.
                return top;
            }
            // format remaining storage into dead space for parsability
            fillWithDeadObject(top, limit);
            // Check if there is another chunk in the lab.
            Address chunk = nextLABChunk;
            if (chunk.isZero()) {
                chunk = toSpace.allocateTLAB(labSize);
                // FIXME: we should have exception path to handle out of memory here -- rollback or stop evacuation to initiate full GC or throw OOM
                assert !chunk.isZero();
            }
            nextLABChunk = HeapFreeChunk.getFreeChunkNext(chunk);
            if (!chunk.equals(limit)) {
                recordRange(allocatedRangeStart, top);
                allocatedRangeStart = chunk;
            }
            top = chunk.asPointer();
            end = chunk.plus(HeapFreeChunk.getFreeChunkNext(chunk)).minus(LAB_HEADROOM).asPointer();
            return Pointer.zero();
        }
        // Overflow allocate
        final Pointer cell = toSpace.allocate(size);
        if (!cell.equals(lastOverflowAllocatedRangeEnd)) {
            if (lastOverflowAllocatedRangeEnd.greaterThan(lastOverflowAllocatedRangeStart)) {
                recordRange(lastOverflowAllocatedRangeStart, lastOverflowAllocatedRangeEnd);
            }
            lastOverflowAllocatedRangeStart = cell;
        }
        lastOverflowAllocatedRangeEnd = cell.plus(size);
        return cell;
    }

    @Override
    boolean inEvacuatedArea(Pointer origin) {
        return fromSpace.contains(origin);
    }

    private Pointer allocate(Size size) {
        Pointer cell = top;
        Pointer newTop = top.plus(size);
        while (newTop.greaterThan(end)) {
            cell = refillOrAllocate(size);
            if (!cell.isZero()) {
                break;
            }
            cell = top;
            newTop = top.plus(size);
        }

        if (CardFirstObjectTable.needsUpdate(cell, top)) {
            cfoTable.set(cell, top);
        }
        return cell;
    }

    @Override
    Pointer evacuate(Pointer fromOrigin) {
        final Pointer fromCell = Layout.originToCell(fromOrigin);
        final Size size = Layout.size(fromOrigin);
        final Pointer toCell = allocate(size);
        Memory.copyBytes(fromCell, toCell, size);
        return toCell;
    }

    @Override
    protected void evacuateReachables() {
        updateSurvivorRanges();
        while (!survivorRanges.isEmpty()) {
            evacuateRange(survivorRanges.start(), survivorRanges.end());
            survivorRanges.remove();
            updateSurvivorRanges();
        }
    }
}
