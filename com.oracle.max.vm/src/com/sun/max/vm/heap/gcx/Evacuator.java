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

import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.MaxineVM.Phase;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.code.*;
import com.sun.max.vm.heap.*;
import com.sun.max.vm.layout.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.type.*;

/**
 * Base class for evacuating objects of an evacuated area made of possibly discontinuous range of addresses.
 * The class provides common services. Sub-classes define what set of contiguous ranges delimit the evacuated area,
 *  what set of contiguous ranges hold the evacuated objects whose references haven't been processed yet, and
 * the remembered set holdings references to the evacuated area.
 *
 */
public abstract class Evacuator extends PointerIndexAndHeaderVisitor implements CellVisitor, OverlappingCellVisitor, SpecialReferenceManager.GC  {
    protected static boolean TraceEvacVisitedCell = false;
    static {
        VMOptions.addFieldOption("-XX:", "TraceEvacVisitedCell", Evacuator.class, "Trace cells visited by the evacuator (Debug mode only)", Phase.PRISTINE);
    }
    private final SequentialHeapRootsScanner heapRootsScanner = new SequentialHeapRootsScanner(this);

    private boolean refDiscoveryEnabled = true;

    private void updateSpecialReference(Pointer origin) {
        if (refDiscoveryEnabled) {
            SpecialReferenceManager.discoverSpecialReference(origin);
        } else {
            // Treat referent as strong reference.
            if (MaxineVM.isDebug() && TraceEvacVisitedCell) {
                final int index = SpecialReferenceManager.REFERENT_WORD_INDEX;
                Log.print("Resurecting referent");
                Log.print(origin.getReference(index).toOrigin());
                Log.print(" from special ref ");
                Log.print(origin); Log.print(" + ");
                Log.println(index);
            }
            updateEvacuatedRef(origin, SpecialReferenceManager.REFERENT_WORD_INDEX);
        }
    }

    final void enableSpecialRefDiscovery() {
        refDiscoveryEnabled = true;
    }

    final void disableSpecialRefDiscovery() {
        refDiscoveryEnabled = false;
    }

    private void updateReferenceArray(Pointer refArrayOrigin, final int firstIndex, final int length) {
        for (int index = firstIndex; index < length; index++) {
            updateEvacuatedRef(refArrayOrigin, index);
        }
    }

    private void updateReferenceArray(Pointer refArrayOrigin) {
        final int length = Layout.readArrayLength(refArrayOrigin) + FIRST_ELEMENT_INDEX;
        updateReferenceArray(refArrayOrigin, FIRST_ELEMENT_INDEX, length);
    }

    private void updateReferenceArray(Pointer refArrayOrigin, Address start, Address end) {
        final int endOfArrayIndex = Layout.readArrayLength(refArrayOrigin) + FIRST_ELEMENT_INDEX;
        final Address firstElementAddr = refArrayOrigin.plusWords(FIRST_ELEMENT_INDEX);
        final Address endOfArrayAddr = refArrayOrigin.plusWords(endOfArrayIndex);
        final int firstIndex = start.greaterThan(firstElementAddr) ? start.minus(refArrayOrigin).unsignedShiftedRight(Kind.REFERENCE.width.log2numberOfBytes).toInt() : FIRST_ELEMENT_INDEX;
        final int endIndex = endOfArrayAddr.greaterThan(end) ? end.minus(refArrayOrigin).unsignedShiftedRight(Kind.REFERENCE.width.log2numberOfBytes).toInt() : endOfArrayIndex;
        updateReferenceArray(refArrayOrigin, firstIndex, endIndex);
    }

    protected HeapRangeDumper dumper;

    protected Evacuator() {
    }

    public void setDumper(HeapRangeDumper dumper) {
        this.dumper = dumper;
    }


  /**
     * Indicate whether the cell at the specified origin is in an area under evacuation.
     * @param origin origin of a cell
     * @return true if the cell is in an evacuation area
     */
    abstract boolean inEvacuatedArea(Pointer origin);
    /**
     * Evacuate the cell at the specified origin. The destination of the cell is
     * @param origin origin of the cell to evacuate
     * @return origin of the cell after evacuation
     */
    abstract Pointer evacuate(Pointer origin);

    /**
     * Remembered set updates to apply to a reference to an evacuated cell.
     * Default is to do nothing.
     *
     * @param refHolderOrigin origin of the reference holder
     * @param wordIndex
     * @param ref reference to an evacuated cell
     */
    void updateRSet(Pointer refHolderOrigin, int wordIndex, Reference ref) {
        // default is doing nothing.
    }


    /**
     * Evacuate a cell of the evacuated area if not already done, and return the reference to the evacuated cell new location.
     *
     * @param origin origin of the cell in the evacuated area
     * @return a reference to the evacuated cell's new location
     */
    protected final Reference getForwardRef(Pointer origin) {
        Reference forwardRef = Layout.readForwardRef(origin);
        if (forwardRef.isZero()) {
            final Pointer toOrigin = evacuate(origin);
            forwardRef = Reference.fromOrigin(toOrigin);
            HeapScheme.Inspect.notifyObjectRelocated(Layout.originToCell(origin), Layout.originToCell(toOrigin));
            Layout.writeForwardRef(origin, forwardRef);
        }
        return forwardRef;
    }

    /**
     * Test if a reference in an cell points to the evacuated area. If it does, the referenced cell is
     * first evacuated if it is still in the evacuated area.
     * The reference is updated to the evacuated cell's new location.
     * @param refHolderOrigin origin of the holder of the reference
     * @param wordIndex index to a reference of the evacuated cell.
     */
    final void updateEvacuatedRef(Pointer refHolderOrigin, int wordIndex) {
        final Reference ref = refHolderOrigin.getReference(wordIndex);
        final Pointer origin = ref.toOrigin();
        if (inEvacuatedArea(origin)) {
            final Reference forwardRef = getForwardRef(origin);
            refHolderOrigin.setReference(wordIndex, forwardRef);
            updateRSet(refHolderOrigin, wordIndex, forwardRef);
        }
    }

    /**
     * Apply the evacuation logic to the reference at the specified index from the origin of a cell.
     * @param origin origin of the cell holding the visited reference
     * @param wordIndex  index of the visited reference from the origin
     */
    @Override
    public void visit(Pointer origin, int wordIndex) {
        updateEvacuatedRef(origin, wordIndex);
    }

    @Override
    public boolean isReachable(Reference ref) {
        final Pointer origin = ref.toOrigin();
        if (inEvacuatedArea(origin)) {
            return !Layout.readForwardRef(origin).isZero();
        }
        return true;
    }

    @Override
    public Reference preserve(Reference ref) {
        final Pointer origin = ref.toOrigin();
        if (inEvacuatedArea(origin)) {
            return getForwardRef(origin);
        }
        return ref;
    }

    @Override
    public boolean mayRelocateLiveObjects() {
        return true;
    }

    /**
     * Evacuate all objects of the evacuated area directly reachable from roots (thread stacks, monitors, etc.).
     */
    void evacuateFromRoots() {
        heapRootsScanner.run();
    }
    /**
     * Evacuate all objects of the evacuated area directly reachable from the remembered sets of the evacuated area. By default, this does nothing
     * (i.e., there are no remembered sets). For instance, a pure semi-space flat heap doesn't have any remembered sets to evacuate from.
     */
    protected void evacuateFromRSets() {
    }

    /**
     * Evacuate all objects of the evacuated area reachable from already evacuated cells.
     */
    abstract protected void evacuateReachables();

    /**
     * Action to performed before evacuation begins, e.g., making evacuated space iterable, etc, initializing allocation buffers, etc.
     */
    abstract protected void doBeforeEvacuation();
    /**
     * Action to performed once evacuation is complete, e.g., releasing or making allocation buffers iterable, zero-ing out evacuated regions, etc.
     */
    abstract protected void doAfterEvacuation();

    /**
     * Action to performed before a GC on the heap space where objects are evacuated begins.
     */
    public void doBeforeGC() {
    }

    /**
     * Action to performed after a GC on the heap space where objects are evacuated begins.
     */
    public void doAfterGC() {

    }
    /**
     * Evacuate all objects of the evacuated area directly reachable from the boot heap.
     */
    void evacuateFromBootHeap() {
        Heap.bootHeapRegion.visitReferences(this);
    }

    void evacuateFromCode() {
        // References in the boot code region are immutable and only ever refer
        // to objects in the boot heap region.
        boolean includeBootCode = false;
        Code.visitCells(this, includeBootCode);
    }

    /**
     * Scan a cell to evacuate the cells in the evacuation area it refers to and update its references to already evacuated cells.
     * @param cell
     * @return
     */
    final protected Pointer scanCellForEvacuatees(Pointer cell) {
        if (MaxineVM.isDebug() && TraceEvacVisitedCell) {
            Log.print("visitCell "); Log.println(cell);
        }
        final Pointer origin = Layout.cellToOrigin(cell);
        // Update the hub first so that is can be dereferenced to obtain
        // the reference map needed to find the other references in the object
        updateEvacuatedRef(origin,  HUB_WORD_INDEX);
        final Hub hub = getHub(origin);
        if (hub == HeapFreeChunk.HEAP_FREE_CHUNK_HUB) {
            return cell.plus(HeapFreeChunk.getFreechunkSize(cell));
        }
       // Update the other references in the object
        final SpecificLayout specificLayout = hub.specificLayout;
        if (specificLayout.isTupleLayout()) {
            TupleReferenceMap.visitReferences(hub, origin, this);
            if (hub.isJLRReference) {
                updateSpecialReference(origin);
            }
            return cell.plus(hub.tupleSize);
        }
        if (specificLayout.isHybridLayout()) {
            TupleReferenceMap.visitReferences(hub, origin, this);
        } else if (specificLayout.isReferenceArrayLayout()) {
            updateReferenceArray(origin);
        }
        return cell.plus(Layout.size(origin));
    }

    private boolean cellInRegion(Pointer cell, Pointer endOfCell, Address start, Address end) {
        return cell.greaterEqual(start) && endOfCell.lessEqual(end);
    }

    private void checkCellOverlap(Pointer cell, Address start, Address end) {
        if (MaxineVM.isDebug()) {
            final Pointer origin = Layout.cellToOrigin(cell);
            final boolean isHeapFreeChunk = HeapFreeChunk.isHeapFreeChunkOrigin(origin);
            final Pointer endOfCell = isHeapFreeChunk ? cell.plus(HeapFreeChunk.getFreechunkSize(cell)) : cell.plus(Layout.size(origin));
            if ((cell.lessThan(end) && endOfCell.greaterThan(start)) || cellInRegion(cell, endOfCell, start, end)) {
                return;
            }

            Log.print(isHeapFreeChunk ? "Free Chunk [" : "Cell [");
            Log.print(cell);
            Log.print(',');
            Log.print(endOfCell);
            Log.print("]  don't overlap range [");
            Log.print(start);
            Log.print(", ");
            Log.print(end);
            Log.println("]");
            FatalError.check(false, "Cell doesn't overlap range");
        }
    }

    /**
     * Scan the part of cell that overlap with a region of memory to evacuate the cells in the evacuation area it refers to and update its references to already evacuated cells.
     *
     * @param cell Pointer to the first word of the cell to be visited
     * @param start start of the region overlapping with the cell
     * @param end end of the region overlapping with the cell
     * @return pointer to the end of the cell
     */
    final protected Pointer scanCellForEvacuatees(Pointer cell, Address start, Address end) {
        checkCellOverlap(cell, start, end);
        final Pointer origin = Layout.cellToOrigin(cell);
        Pointer hubReferencePtr = origin.plus(HUB_WORD_INDEX);
        if (hubReferencePtr.greaterEqual(start)) {
            updateEvacuatedRef(origin,  HUB_WORD_INDEX);
        }
        final Hub hub = UnsafeCast.asHub(origin.getReference(HUB_WORD_INDEX));
        if (hub == HeapFreeChunk.HEAP_FREE_CHUNK_HUB) {
            return cell.plus(HeapFreeChunk.getFreechunkSize(cell));
        }
        // Update the other references in the object
        final SpecificLayout specificLayout = hub.specificLayout;
        if (specificLayout.isTupleLayout()) {
            TupleReferenceMap.visitReferences(hub, origin, this, start, end);
            if (hub.isJLRReference) {
                updateSpecialReference(origin);
            }
            return cell.plus(hub.tupleSize);
        }
        if (specificLayout.isHybridLayout()) {
            TupleReferenceMap.visitReferences(hub, origin, this, start, end);
        } else if (specificLayout.isReferenceArrayLayout()) {
            updateReferenceArray(origin, start, end);
        }
        return cell.plus(Layout.size(origin));
    }

    /**
     * Evacuate all cells from the evacuated area reachable from the specified range of heap addresses.
     * The range comprise an integral number of cells.
     *
     * @param start first address of the range, must coincide with the start of a cell
     * @param end last address of the range, must coincide with the end of a cell
     */
    final void evacuateRange(Pointer start, Pointer end) {
        Pointer cell = start;
        while (cell.lessThan(end)) {
            cell = visitCell(cell);
        }
    }

    public void evacuate() {
        doBeforeEvacuation();
        HeapScheme.Inspect.notifyHeapPhaseChange(HeapPhase.ANALYZING);
        evacuateFromRoots();
        evacuateFromBootHeap();
        evacuateFromCode();
        evacuateFromRSets();
        evacuateReachables();
        disableSpecialRefDiscovery();
        SpecialReferenceManager.processDiscoveredSpecialReferences(this);
        evacuateReachables();
        enableSpecialRefDiscovery();
        HeapScheme.Inspect.notifyHeapPhaseChange(HeapPhase.RECLAIMING);
        doAfterEvacuation();
    }
}
