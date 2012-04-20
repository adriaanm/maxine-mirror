/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.max.annotate.*;
import com.sun.max.memory.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.MaxineVM.Phase;
import com.sun.max.vm.heap.*;
import com.sun.max.vm.heap.gcx.rset.ctbl.*;
import com.sun.max.vm.layout.*;
import com.sun.max.vm.runtime.*;
/**
 * A heap space evacuator that evacuate objects from one space to another, without aging.
 * Locations of references to evacuatees from other heap spaces are provided by a card table.
 *
 * TODO: replace direct cfotable updates with proper use of the DeadSpaceListener interface implemented by the card table.
 * (see all fixme comments below). This would make allocation in survivor space independent of details of the card table RSet.
 */
public final class NoAgingEvacuator extends Evacuator {
    public static boolean TraceDirtyCardWalk = false;
    static {
        VMOptions.addFieldOption("-XX:", "TraceDirtyCardWalk", NoAgingEvacuator.class, "Trace Dirty Card Walk", Phase.PRISTINE);
    }

    @FOLD
    private static Size labHeadroom() {
        return minObjectSize();
    }

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
    private Size minRefillThreshold;

    /**
     * Hint of amount of space to use to refill the promotion allocation buffer.
     */
    private Size pSize;

    /**
     * Remembered set of the from space.
     */
    private final CardTableRSet rset;

    /**
     * Cached card first object table for fast access.
     */
    private final CardFirstObjectTable cfoTable;

    /**
     * Amount of evacuated bytes.
     */
    private Size evacuatedBytes;

   /**
     * Allocation hand to the evacuator's private promotion space.
     */
    private Pointer ptop;

    /***
     * End of the evacuator's private promotion space.
     */
    private Pointer pend;

    /**
     * Next free space chunk in the evacuator's private promotion space.
     */
    private Address pnextChunk;

    /**
     * Mark to keep track of survivor ranges.
     */
    private Address allocatedRangeStart;

    /**
     * Start of the last unrecorded survivor ranges resulting from overflow allocation.
     */
    private Address lastOverflowAllocatedRangeStart;
    /**
     * End of the last unrecorded survivor ranges resulting from overflow allocation. This is kept to try
     * to coalesce the range with the overflow allocation request. If the ranges cannot be coalesced,
     * the last range is recorded and a new range begin with the result of new allocation request.
     */
    private Address lastOverflowAllocatedRangeEnd;

    /**
     * Queue of survivor ranges remaining to process for evacuation.
     */
    private SurvivorRangesQueue survivorRanges;

    /**
     * Closure for evacuating cells in dirty card. A dirty card may overlap with an area currently used for allocation.
     * Allocation is made via the evacuator's promotion lab, which may during iteration over dirty cards.
     * To avoid maintaining parsability of the promotion lab at every allocation,
     * we check if the visited cell boundary coincide with the first free bytes of the allocator, and skip it if it does.
     *
     * Note that the allocator that feed the promotion lab is kept in an iterable state.
     */
    final class DirtyCardEvacuationClosure implements CellVisitor, OverlappingCellVisitor,  HeapSpaceRangeVisitor {
        private final CardTableRSet cachedRSet;

        DirtyCardEvacuationClosure() {
            cachedRSet = rset;
        }
        @Override
        public Pointer visitCell(Pointer cell, Address start, Address end) {
            if (cell.equals(ptop)) {
                // Skip allocating area.
                return pend;
            }
            return scanCellForEvacuatees(cell, start, end);
        }

        @Override
        public Pointer visitCell(Pointer cell) {
            if (cell.equals(ptop)) {
                // Skip allocating area
                return pend;
            }
            return scanCellForEvacuatees(cell);
        }

        public void visitCells(Address start, Address end) {
            if (MaxineVM.isDebug() && dumper != null && HeapRangeDumper.DumpOnError) {
                dumper.setRange(start, end);
                FatalError.setOnVMOpError(dumper);
            }
            cachedRSet.cleanAndVisitCards(start, end, this);
            if (MaxineVM.isDebug()) {
                FatalError.setOnVMOpError(null);
            }
        }
    }

    private final DirtyCardEvacuationClosure heapSpaceDirtyCardClosure;

    public NoAgingEvacuator(HeapSpace fromSpace, HeapSpace toSpace, CardTableRSet rset) {
        this.fromSpace = fromSpace;
        this.toSpace = toSpace;
        this.rset = rset;
        this.cfoTable = rset.cfoTable;
        this.heapSpaceDirtyCardClosure = new DirtyCardEvacuationClosure();
    }

    public void initialize(int maxSurvivorRanges, Size labSize, Size minRefillThreshold) {
        this.survivorRanges = new SurvivorRangesQueue(maxSurvivorRanges);
        this.pSize = labSize;
        this.minRefillThreshold = minRefillThreshold;
    }

    public Size evacuatedBytes() {
        return evacuatedBytes;
    }

    /**
     * Retire promotion buffer before a GC on the promotion space is performed.
     */
    @Override
    public void doBeforeGC() {
        if (MaxineVM.isDebug() && !ptop.isZero()) {
            FatalError.check(HeapFreeChunk.isTailFreeChunk(ptop, pend.plus(labHeadroom())), "Evacuator's allocation buffer must be parseable");
        }
        ptop = Pointer.zero();
        pend = Pointer.zero();
    }

    @Override
    protected void doBeforeEvacuation() {
        fromSpace.doBeforeGC();
        evacuatedBytes = Size.zero();
        lastOverflowAllocatedRangeStart = Pointer.zero();
        lastOverflowAllocatedRangeEnd = Pointer.zero();

        if (ptop.isZero()) {
            Address chunk = toSpace.allocateTLAB(pSize);
            Size chunkSize = HeapFreeChunk.getFreechunkSize(chunk);
            pnextChunk = HeapFreeChunk.getFreeChunkNext(chunk);
            rset.notifyRefill(chunk, chunkSize);
            ptop = chunk.asPointer();
            pend = chunk.plus(chunkSize.minus(labHeadroom())).asPointer();
        }
        allocatedRangeStart = ptop;
    }

    @Override
    protected void doAfterEvacuation() {
        survivorRanges.clear();
        fromSpace.doAfterGC();
        Pointer limit = pend.plus(labHeadroom());
        Size spaceLeft = limit.minus(ptop).asSize();
        if (spaceLeft.lessThan(minRefillThreshold)) {
            // Will trigger refill in doBeforeEvacution on next GC
            if (!spaceLeft.isZero()) {
                fillWithDeadObject(ptop, limit);
                rset.notifyRetireDeadSpace(ptop, spaceLeft);
            }
            ptop = Pointer.zero();
            pend = Pointer.zero();
        } else {
            // Leave remaining space in an iterable format.
            // Next evacuation will start from top again.
            HeapFreeChunk.format(ptop, spaceLeft);
            rset.notifyRetireFreeSpace(ptop, spaceLeft);
        }
    }

    private void recordRange(Address start, Address end) {
        evacuatedBytes = evacuatedBytes.plus(end.minus(start));
        survivorRanges.add(start, end);
    }

    private void updateSurvivorRanges() {
        if (ptop.greaterThan(allocatedRangeStart)) {
            // Something was allocated in the current evacuation allocation buffer.
            recordRange(allocatedRangeStart, ptop);
            allocatedRangeStart = ptop;
        }
        if (lastOverflowAllocatedRangeEnd.greaterThan(lastOverflowAllocatedRangeStart)) {
            recordRange(lastOverflowAllocatedRangeStart, lastOverflowAllocatedRangeEnd);
            lastOverflowAllocatedRangeStart = lastOverflowAllocatedRangeEnd;
        }
    }

    private Pointer refillOrAllocate(Size size) {
        if (size.lessThan(minRefillThreshold)) {
            // check if request can fit in the remaining space when taking the headroom into account.
            Pointer limit = pend.plus(labHeadroom());
            if (ptop.plus(size).equals(limit)) {
                // Does fit.
                return ptop;
            }
            if (ptop.lessThan(limit)) {
                // format remaining storage into dead space for parsability
                fillWithDeadObject(ptop, limit);
                // Update FOT accordingly
                // FIXME:  it'll be cleaner to call  rset.notifyRetireDeadSpace(ptop, limit.minus(ptop).asSize());
                cfoTable.set(ptop, limit);
                if (MaxineVM.isDebug()) {
                    final Address deadSpaceLastWordAddress = limit.minus(Word.size());
                    if (CardTableRSet.alignDownToCard(ptop).lessThan(CardTableRSet.alignDownToCard(deadSpaceLastWordAddress))) {
                        FatalError.check(ptop.equals(cfoTable.cellStart(rset.cardTable.tableEntryIndex(deadSpaceLastWordAddress))), "corrupted FOT");
                    }
                }
            }
            // Check if there is another chunk in the lab.
            Address chunk = pnextChunk;
            if (chunk.isZero()) {
                chunk = toSpace.allocateTLAB(pSize);
                // FIXME: we should have exception path to handle out of memory here -- rollback or stop evacuation to initiate full GC or throw OOM
                assert !chunk.isZero() && HeapFreeChunk.getFreechunkSize(chunk).greaterEqual(minRefillThreshold);
            }
            pnextChunk = HeapFreeChunk.getFreeChunkNext(chunk);
            if (!chunk.equals(limit)) {
                recordRange(allocatedRangeStart, ptop);
                allocatedRangeStart = chunk;
            }
            Size chunkSize = HeapFreeChunk.getFreechunkSize(chunk);
            rset.notifyRefill(chunk, chunkSize);
            ptop = chunk.asPointer();
            pend = chunk.plus(chunkSize.minus(labHeadroom())).asPointer();
            // Return zero to force loop back.
            return Pointer.zero();
        }
        // Overflow allocate
        final Pointer cell = toSpace.allocate(size);
        // Allocator must have already fire a notifySplitLive event to the space's DeadSpaceListener (i.e., the CardTableRSet in this case).
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

    /**
     * Allocate space in evacuator's promotion allocation buffer.
     *
     * @param size
     * @return
     */
    private Pointer allocate(Size size) {
        Pointer cell = ptop;
        Pointer newTop = ptop.plus(size);
        while (newTop.greaterThan(pend)) {
            cell = refillOrAllocate(size);
            if (!cell.isZero()) {
                return cell;
            }
            // We refilled. Retry allocating from local allocation buffer.
            cell = ptop;
            newTop = ptop.plus(size);
        }
        ptop = newTop;
        // FIXME ? it'll be cleaner to do a rset.notifySplitLive(cell, size, hardLimit) here.
        cfoTable.set(cell, ptop);
        return cell;
    }

    /**
     * Scan a cell to evacuate the cells in the evacuation area it refers to and update its references to already evacuated cells.
     *
     * @param cell a pointer to a cell
     * @return pointer to the end of the cell
     */
    public Pointer visitCell(Pointer cell) {
        return scanCellForEvacuatees(cell);
    }

    /**
     * Scan a cell to evacuate the cells in the evacuation area it refers to and update its references to already evacuated cells.
     *
     * @param cell a pointer to a cell
     * @return pointer to the end of the cell
     */
    public Pointer visitCell(Pointer cell, Address start, Address end) {
        return scanCellForEvacuatees(cell, start, end);
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
    protected void evacuateFromRSets() {
        // Visit the dirty cards of the old gen (i.e., the toSpace).
        final boolean traceRSet = CardTableRSet.traceCardTableRSet();
        if (MaxineVM.isDebug() && TraceDirtyCardWalk) {
            CardTableRSet.setTraceCardTableRSet(true);
        }
        toSpace.visit(heapSpaceDirtyCardClosure);
        if (MaxineVM.isDebug() && TraceDirtyCardWalk) {
            CardTableRSet.setTraceCardTableRSet(traceRSet);
        }
    }

    @Override
    protected void evacuateReachables() {
        updateSurvivorRanges();
        while (!survivorRanges.isEmpty()) {
            final Pointer start = survivorRanges.start();
            final Pointer end = survivorRanges.end();
            survivorRanges.remove();
            evacuateRange(start, end);
            updateSurvivorRanges();
        }
    }
}
