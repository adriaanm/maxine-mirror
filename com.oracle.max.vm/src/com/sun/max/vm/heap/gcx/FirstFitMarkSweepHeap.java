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

import static com.sun.max.vm.heap.gcx.HeapRegionConstants.*;
import static com.sun.max.vm.heap.gcx.HeapRegionInfo.*;
import static com.sun.max.vm.heap.gcx.RegionTable.*;

import com.sun.max.annotate.*;
import com.sun.max.memory.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.heap.*;
import com.sun.max.vm.runtime.*;
/**
 * A region-based, flat, mark-sweep heap, with bump pointer allocation only.
 * Each partially occupied region has a list of addressed ordered free chunks, used to allocate TLAB refill and an overflow allocator.
 */
public final class FirstFitMarkSweepHeap extends HeapRegionSweeper implements HeapAccountOwner, ApplicationHeap {
    /* For simplicity at the moment. Should be able to allocate this in GC's own heap (i.e., the bootstrap allocator).
     */
    private static final OutOfMemoryError outOfMemoryError = new OutOfMemoryError();

    /**
     * Heap account tracking the pool of regions allocated to this heap.
     */
    final HeapAccount<FirstFitMarkSweepHeap> heapAccount;

    /**
     * List of region with space available for allocation.
     * Initialized with all regions. Then reset by the sweeper at every collection.
     * Used to refill the TLAB allocator and the overflow allocator.
     */
    private HeapRegionList allocationRegions;
    /**
     * List of region with space available for TLAB allocation only.
     */
    private HeapRegionList tlabAllocationRegions;

    /**
     * Region currently used for tlab allocation.
     */
    private int currentTLABAllocatingRegion;

    /**
     * Region currently used for overflow allocation.
     */
    private int currentOverflowAllocatingRegion;

    /**
     * Total free space in allocation regions.
     * Reset after each GC. Then decremented when allocators refill.
     */
    private Size allocationRegionsFreeSpace;

    /**
     * TLAB refill allocator. Can supplies TLAB refill either as a single contiguous chunk,
     * or as an address-ordered list of chunks.
     */
    final ChunkListAllocator<TLABRefillManager> tlabAllocator;

    /**
     * Overflow allocator. Handles direct allocation request and all small overflow of TLABs.
     */
    final BaseAtomicBumpPointerAllocator<Refiller> overflowAllocator;

    /**
     * Large object allocator. Handles direct allocation requests for large object as well as large overflow of TLABs.
     */
    final LargeObjectSpace largeObjectAllocator;

    /**
     * Support for iterating over contiguous region ranges from a list.
     */
    final HeapRegionRangeIterable regionsRangeIterable;

    /**
     * Support for iterating over region info of regions from a list.
     */
    final HeapRegionInfoIterable regionInfoIterable;

    /**
     * Minimum size to be treated as a large object.
     */
    private Size minLargeObjectSize;
    /**
     * Minimum free space to refill the overflow allocator.
     */
    private Size minOverflowRefillSize;

    /**
     * Indicate whether a size is categorized as large. Request for large size must go to the large object allocator.
     * @param size size in words
     * @return true if the size is considered large.
     */
    private boolean isLarge(Size size) {
        return size.greaterEqual(minLargeObjectSize);
    }

    private Pointer allocateSingleRegionLargeObject(HeapRegionInfo rinfo, Pointer allocated, Size requestedSize, Size totalChunkSize) {
        final int regionID = rinfo.toRegionID();
        allocationRegions.remove(regionID);
        Pointer leftover = allocated.plus(requestedSize);
        Size spaceLeft = totalChunkSize.minus(requestedSize);
        if (spaceLeft.lessThan(minReclaimableSpace)) {
            HeapSchemeAdaptor.fillWithDeadObject(leftover, leftover.plus(spaceLeft));
            rinfo.setFull();
        } else {
            HeapFreeChunk.format(leftover, spaceLeft);
            rinfo.setFreeChunks(leftover,  spaceLeft, 1);
            tlabAllocationRegions.append(regionID);
            allocationRegionsFreeSpace = allocationRegionsFreeSpace.plus(spaceLeft);
        }
        return allocated;
    }

    private int debug_numContiguousRegionNeeded;
    private int debug_firstRegion;
    private int debug_lastRegion;

    private Pointer allocateLarge(Size size) {
        final Size roundedUpSize = size.alignUp(regionSizeInBytes);
        final Size tailSize = roundedUpSize.minus(size);
        final int extraRegion = tailSize.greaterThan(0) && tailSize.lessThan(HeapSchemeAdaptor.MIN_OBJECT_SIZE)  ? 1 : 0;
        int numContiguousRegionNeeded = roundedUpSize.unsignedShiftedRight(log2RegionSizeInBytes).toInt() + extraRegion;
        synchronized (heapLock()) {
            do {
                regionInfoIterable.initialize(allocationRegions);
                regionInfoIterable.reset();
                if (numContiguousRegionNeeded == 1) {
                    final int numWordsNeeded = size.unsignedShiftedRight(Word.widthValue().log2numberOfBytes).toInt();
                    // Actually, any region with a chunk large enough can do in that case.
                    while (regionInfoIterable.hasNext()) {
                        HeapRegionInfo rinfo = regionInfoIterable.next();
                        if (rinfo.isEmpty()) {
                            allocationRegionsFreeSpace = allocationRegionsFreeSpace.minus(regionSizeInBytes);
                            return allocateSingleRegionLargeObject(rinfo, rinfo.regionStart().asPointer(), size, Size.fromInt(regionSizeInBytes));
                        } else if (rinfo.isAllocating() && rinfo.numFreeChunks == 1 && rinfo.freeSpace >= numWordsNeeded) {
                            allocationRegionsFreeSpace = allocationRegionsFreeSpace.minus(rinfo.freeBytes());
                            return allocateSingleRegionLargeObject(rinfo,  rinfo.firstFreeBytes().asPointer(), size, Size.fromInt(rinfo.freeBytes()));
                        }
                    }
                } else {
                    int n = 0;
                    int firstRegion = INVALID_REGION_ID;
                    int lastRegion = INVALID_REGION_ID;

                    debug_numContiguousRegionNeeded = numContiguousRegionNeeded;
                    debug_firstRegion = firstRegion;
                    debug_lastRegion = lastRegion;

                    while (regionInfoIterable.hasNext()) {
                        HeapRegionInfo rinfo = regionInfoIterable.next();
                        if (rinfo.isEmpty()) {
                            int rid = rinfo.toRegionID();
                            if (n == 0) {
                                firstRegion  = rid;
                                lastRegion  = rid;
                                debug_firstRegion = firstRegion;
                                n = 1;
                            } else if (rid == lastRegion + 1) {
                                lastRegion = rid;
                                debug_lastRegion = lastRegion;
                                if (++n >= numContiguousRegionNeeded) {
                                    // Got the number of requested contiguous regions.
                                    // Remove them all from the list (except the tail if it leaves enough space for overflow allocation)
                                    // and turn them into large object regions.
                                    allocationRegions.remove(firstRegion);
                                    HeapRegionInfo firstRegionInfo = HeapRegionInfo.fromRegionID(firstRegion);
                                    firstRegionInfo.setLargeHead();
                                    if (n > 2) {
                                        for (int i = firstRegion + 1; i < lastRegion; i++) {
                                            allocationRegions.remove(i);
                                            HeapRegionInfo.fromRegionID(i).setLargeBody();
                                        }
                                    }
                                    HeapRegionInfo lastRegionInfo =  HeapRegionInfo.fromRegionID(lastRegion);
                                    Pointer tailEnd = lastRegionInfo.regionStart().plus(regionSizeInBytes).asPointer();
                                    Pointer tail = tailEnd.minus(tailSize);
                                    if (tailSize.lessThan(minReclaimableSpace)) {
                                        HeapSchemeAdaptor.fillWithDeadObject(tail, tailEnd);
                                        allocationRegions.remove(lastRegion);
                                        lastRegionInfo.setLargeTail();
                                    } else {
                                        // Format the tail as a free chunk.
                                        HeapFreeChunk.format(tail, tailSize);
                                        lastRegionInfo.setLargeTail(tail, tailSize);
                                        if (tailSize.lessThan(minOverflowRefillSize)) {
                                            allocationRegions.remove(lastRegion);
                                            tlabAllocationRegions.append(lastRegion);
                                        }
                                    }
                                    return firstRegionInfo.regionStart().asPointer();
                                }
                            } else {
                                n = 0;
                            }
                        }
                    }
                }
            } while(Heap.collectGarbage(roundedUpSize)); // Always collect for at least one region.
            return Pointer.zero();
        }
    }

    Pointer allocateLargeCleared(Size size) {
        Pointer cell = allocateLarge(size);
        Memory.clearWords(cell, size.unsignedShiftedRight(Word.widthValue().log2numberOfBytes).toInt());
        return cell;
    }

    /**
     * The lock on which refill and region allocation to object spaces synchronize on.
     */
    private Object heapLock() {
        return this;
    }

    private void outOfMemory() {
        throw outOfMemoryError;
    }


    /**
     * Refill Manager for the small object space.
     */
    class TLABRefillManager extends ChunkListRefillManager {
        /**
         * Threshold below which the allocator should be refilled.
         */
        private Size refillThreshold;

        private Address nextFreeChunkInRegion;

        /**
         * Free space left in the region. This doesn't count the free space in the allocator this refill manager serves.
         *
         */
        private Size freeSpace;

        /**
         * Space wasted on refill. For statistics only.
         */
        private Size wastedSpace;

        TLABRefillManager() {
            nextFreeChunkInRegion = Address.zero();
        }

        void setRefillPolicy(Size refillThreshold) {
            this.refillThreshold = refillThreshold;
        }

        // FIXME: The following two are inherited from the ChunkListRefillManager used for the ChunkListAllocator.
        // These aren't needed for the logic here where allocation is always dispatched to three independent allocators:
        // large, overflow, and tlab. The latter never comes across a large or overflow situation,  and therefore never call these.
        // What should be done is a variant of the ChunkListAllocator that extend the BaseBumpPointer allocator and override
        // the refill allocate. We currently leave this as is because doing this change also requires changing the FreeHeapSpaceManager of the MSHeapScheme

        @Override
        Address allocateLarge(Size size) {
            FatalError.unexpected("Should not reach here");
            return Address.zero();
        }

        /**
         * Request cannot be satisfied with allocator and refill manager doesn't want to refill.
         * Allocate to large or overflow allocator.
         */
        @Override
        Address allocateOverflow(Size size) {
            FatalError.unexpected("Should not reach here");
            return Address.zero();
        }

        /**
         *
         */
        @Override
        @NO_SAFEPOINTS("tlab allocation loop must not be subjected to safepoints")
        Address allocateChunkList(Size tlabSize, Pointer leftover, Size leftoverSize) {
            Address firstChunk = chunkOrZero(leftover, leftoverSize);
            if (!firstChunk.isZero()) {
                tlabSize = tlabSize.minus(leftoverSize);
                if (tlabSize.lessThan(minChunkSize)) {
                    // don't bother with it. Just return.
                    return firstChunk;
                }
            }
            if (nextFreeChunkInRegion.isZero()) {
                if (!firstChunk.isZero()) {
                    // Return what we have for now as changeAllocatingRegion can cause a GC.
                    return firstChunk;
                }
                HeapRegionInfo regionInfo = changeAllocatingRegion();
                FatalError.check(regionInfo != null, "must never be null");
                nextFreeChunkInRegion = regionInfo.firstFreeBytes();
                if (regionInfo.hasFreeChunks()) {
                    freeSpace = Size.fromInt(regionInfo.freeBytes());
                } else {
                    // It's an empty region.2328

                    freeSpace = Size.fromInt(regionSizeInBytes);
                    HeapFreeChunk.format(nextFreeChunkInRegion, freeSpace);
                }
            }
            // Grab enough chunks to satisfy TLAB refill
            Size allocatedSize = tlabSize; // remember how much space was initially requested.
            Address lastChunk = Address.zero();
            Address chunk = nextFreeChunkInRegion.asPointer();
            do {
                Size chunkSize = HeapFreeChunk.getFreechunkSize(chunk);
                if (chunkSize.greaterThan(tlabSize)) {
                    Address next = HeapFreeChunk.getFreeChunkNext(chunk);
                    // Split if leftover larger that min tlab size.
                    if (chunkSize.minus(tlabSize).greaterEqual(minChunkSize)) {
                        lastChunk = HeapFreeChunk.splitRight(chunk, tlabSize, next);
                    } else {
                        lastChunk = next;
                        // Adjust allocated size, to keep accounting correct.
                        allocatedSize = allocatedSize.plus(chunkSize.minus(tlabSize));
                    }
                    freeSpace = freeSpace.minus(allocatedSize);
                    HeapFreeChunk.setFreeChunkNext(chunk, Address.zero());
                    break;
                }
                tlabSize = tlabSize.minus(chunkSize);
                chunk = HeapFreeChunk.getFreeChunkNext(chunk);
            } while(!chunk.isZero());

            Address result = nextFreeChunkInRegion;
            nextFreeChunkInRegion = lastChunk;
            if (!firstChunk.isZero()) {
                HeapFreeChunk.setFreeChunkNext(firstChunk, chunk);
                result = firstChunk;
            }
            return result;
        }

        @Override
        boolean shouldRefill(Size requestedSpace, Size spaceLeft) {
            // Should refill only if we're not going to waste too much space and
            // the refill will succeed (we assume it will if switching regions).
            return spaceLeft.lessThan(refillThreshold) && (nextFreeChunkInRegion.isZero() ||
                            requestedSpace.lessThan(HeapFreeChunk.getFreechunkSize(nextFreeChunkInRegion)));
        }

        /**
         * Refill the linear space allocator. Note that this one already guarantees that only one
         * thread can enter this method.
         */
        @Override
        Address allocateRefill(Pointer startOfSpaceLeft, Size spaceLeft) {
            // FIXME: see comment above. We should never reach here as request for refilling the allocator can only happen via the allocateCleared call, which
            // should never be called on the tlab allocator since these are routed early on to the overflow allocator.
            FatalError.unexpected("Should not reach here");
            FatalError.check(spaceLeft.lessThan(refillThreshold), "Should not refill before threshold is reached");
            return Address.zero();
        }


        @Override
        void makeParsable(Pointer start, Pointer end) {
            if (MaxineVM.isDebug()) {
                FatalError.check(regionStart(end).lessEqual(start), "space left must be in the same regions");
            }
            HeapSchemeAdaptor.fillWithDeadObject(start, end);
            theRegionTable().regionInfo(start).setIterable();
        }

        Size freeSpace() {
            return freeSpace;
        }
    }

    HeapRegionInfo changeAllocatingRegion() {
        synchronized (heapLock()) {
            int gcCount = 0;
            // No more free chunk in this region.
            fromRegionID(currentTLABAllocatingRegion).setFull();
            do {
                HeapRegionList regionList = tlabAllocationRegions.isEmpty() ? allocationRegions : tlabAllocationRegions;

                currentTLABAllocatingRegion = regionList.removeHead();
                if (currentTLABAllocatingRegion != INVALID_REGION_ID) {
                    final HeapRegionInfo regionInfo = fromRegionID(currentTLABAllocatingRegion);
                    regionInfo.setAllocating();
                    return regionInfo;
                }

                if (MaxineVM.isDebug() && Heap.traceGC()) {
                    gcCount++;
                    final boolean lockDisabledSafepoints = Log.lock();
                    Log.unlock(lockDisabledSafepoints);
                    if (gcCount > 5) {
                        FatalError.unexpected("Suspiscious repeating GC calls detected");
                    }
                }
            } while(Heap.collectGarbage(Size.fromInt(regionSizeInBytes))); // Always collect for at least one region.
            // Not enough freed memory.
            throw outOfMemoryError;
        }
    }

    /**
     * Count of waste left after overflow refill.
     */
    private Size overflowRefillWaste;

    /**
     * Count of free space put back in TLAB after overflow refill.
     */
    private Size overflowRefillFreeSpace;

    /**
     * Try to refill the overflow allocator with a single continuous chunk. Runs GC if can't.
     * @param minRefillSize minimum amount of space to refill the allocator with
     * @return address to a chunk of the requested size, or zero if none requested.
     */
    private Address overflowRefill(Pointer startOfSpaceLeft, Size spaceLeft) {
        final int minFreeWords = minOverflowRefillSize.unsignedShiftedRight(Word.widthValue().log2numberOfBytes).toInt();
        int gcCount = 0;
        final boolean traceOverflowRefill = true;
        synchronized (heapLock()) {
            if (currentOverflowAllocatingRegion != INVALID_REGION_ID) {
                final HeapRegionInfo regionInfo = fromRegionID(currentOverflowAllocatingRegion);
                FatalError.check(!regionInfo.hasFreeChunks(), "must not have any free chunks");
                if (spaceLeft.greaterEqual(minReclaimableSpace)) {
                    if (traceOverflowRefill) {
                        Log.print("overflow allocator putback region #");
                        Log.print(currentOverflowAllocatingRegion);
                        Log.print(" in TLAB allocation list with ");
                        Log.print(spaceLeft.toInt());
                        Log.println(" bytes");
                    }
                    overflowRefillFreeSpace = overflowRefillFreeSpace.plus(spaceLeft);
                    HeapFreeChunk.format(startOfSpaceLeft, spaceLeft);
                    regionInfo.setFreeChunks(startOfSpaceLeft, spaceLeft, 1);
                    // Can turn the left over into a chunk for tlab allocation.
                    allocationRegionsFreeSpace = allocationRegionsFreeSpace.plus(spaceLeft);
                    tlabAllocationRegions.append(currentOverflowAllocatingRegion);
                } else {
                    if (traceOverflowRefill) {
                        Log.print("overflow allocator full region #");
                        Log.println(currentOverflowAllocatingRegion);
                    }
                   // Just make the space left parsable.
                    if (!spaceLeft.isZero()) {
                        overflowRefillWaste = overflowRefillWaste.plus(spaceLeft);
                        HeapSchemeAdaptor.fillWithDeadObject(startOfSpaceLeft, startOfSpaceLeft.plus(spaceLeft));
                    }
                    regionInfo.setFull();
                }
                currentOverflowAllocatingRegion = INVALID_REGION_ID;
            }
            do {
                regionInfoIterable.initialize(allocationRegions);
                regionInfoIterable.reset();
                Address refill = Address.zero();
                for (HeapRegionInfo regionInfo : regionInfoIterable) {
                    if (regionInfo.isEmpty()) {
                        refill =  regionInfo.regionStart();
                        HeapFreeChunk.format(refill, Size.fromInt(regionSizeInBytes));
                        allocationRegionsFreeSpace = allocationRegionsFreeSpace.minus(regionSizeInBytes);
                    } else if (regionInfo.freeWords() >= minFreeWords && regionInfo.numFreeChunks() == 1) {
                        refill = regionInfo.firstFreeBytes();
                        regionInfo.clearFreeChunks();
                        allocationRegionsFreeSpace = allocationRegionsFreeSpace.minus(regionInfo.freeBytes());
                    } else {
                        continue;
                    }
                     // Found a refill.
                    currentOverflowAllocatingRegion = regionInfo.toRegionID();
                    if (traceOverflowRefill) {
                        Log.print("Refill overflow allocator w/ region #");
                        Log.println(currentOverflowAllocatingRegion);
                    }
                    allocationRegions.remove(currentOverflowAllocatingRegion);
                    regionInfo.setAllocating();
                    return refill;
                }

                if (MaxineVM.isDebug() && Heap.traceGC()) {
                    gcCount++;
                    final boolean lockDisabledSafepoints = Log.lock();
                    Log.unlock(lockDisabledSafepoints);
                    if (gcCount > 5) {
                        FatalError.unexpected("Suspiscious repeating GC calls detected");
                    }
                }
            } while(Heap.collectGarbage(minOverflowRefillSize));
            throw outOfMemoryError;
        }
    }

    /**
     * Simple implementation for the overflow allocator's refiller. Delegate to FirstFitMarkSweepHeap.
     */
    final class  OverflowAllocatorRefiller extends Refiller {
        @Override
        Address allocateRefill(Pointer startOfSpaceLeft, Size spaceLeft) {
            return overflowRefill(startOfSpaceLeft, spaceLeft);
        }

        @Override
        void makeParsable(Pointer start, Pointer end) {
            HeapSchemeAdaptor.fillWithDeadObject(start, end);
            currentOverflowAllocatingRegion = INVALID_REGION_ID;
        }
    }

    public FirstFitMarkSweepHeap() {
        heapAccount = new HeapAccount<FirstFitMarkSweepHeap>(this);
        currentOverflowAllocatingRegion = INVALID_REGION_ID;
        currentTLABAllocatingRegion = INVALID_REGION_ID;
        overflowAllocator = new BaseAtomicBumpPointerAllocator<Refiller>(new OverflowAllocatorRefiller());
        tlabAllocator = new ChunkListAllocator<TLABRefillManager>(new TLABRefillManager());
        tlabAllocator.refillManager.setMinChunkSize(Size.fromInt(regionSizeInBytes).dividedBy(2));
        largeObjectAllocator = new LargeObjectSpace();
        regionsRangeIterable = new HeapRegionRangeIterable();
        regionInfoIterable = new HeapRegionInfoIterable();
    }

    public HeapAccount<FirstFitMarkSweepHeap> heapAccount() {
        return heapAccount;
    }

    /**
     * Initialization of those elements that relies on parameters available at VM start only.
     * @param minSize
     * @param maxSize
     */
    public void initialize(Size minSize, Size maxSize) {
        if (!heapAccount.open(numberOfRegions(maxSize))) {
            FatalError.unexpected("Failed to create application heap");
        }
        Size regionSize = Size.fromInt(regionSizeInBytes);
        tlabAllocationRegions = HeapRegionList.RegionListUse.OWNERSHIP.createList();
        allocationRegions = HeapRegionList.RegionListUse.OWNERSHIP.createList();
        heapAccount.allocate(numberOfRegions(minSize), allocationRegions, true);
        minReclaimableSpace = Size.fromInt(freeChunkMinSizeOption.getValue());
        // FIXME:  the following two are connected: if you deny refill after overflow, the only solution left is allocating large.
        minLargeObjectSize = regionSize;
        minOverflowRefillSize = regionSize.dividedBy(4);
        tlabAllocator.refillManager.setRefillPolicy(minReclaimableSpace);

        // Set the iterable to the allocating regions. This is the default. Any exception to this should
        // reset to the allocating region list when done.
        regionsRangeIterable.initialize(allocationRegions);

        // Initialize the tlab allocator with the first contiguous range.
        regionsRangeIterable.reset();
        ((ChunkListRefillManager) tlabAllocator.refillManager).setMinChunkSize(minReclaimableSpace);

        // Initialize TLAB allocator with first region.
        currentTLABAllocatingRegion = allocationRegions.removeHead();
        final HeapRegionInfo regionInfo = fromRegionID(currentTLABAllocatingRegion);
        regionInfo.setAllocating();
        final Size allocatorsHeadroom = HeapSchemeAdaptor.MIN_OBJECT_SIZE;
        tlabAllocator.initialize(regionInfo.firstFreeBytes(), regionSize, regionSize, allocatorsHeadroom);
        overflowAllocator.initialize(Address.zero(), Size.zero(), allocatorsHeadroom);

        allocationRegionsFreeSpace = regionSize.times(allocationRegions.size());
    }

    /**
     * Entry point for direct allocation when TLAB cannot be refilled.
     */
    @Override
    public Pointer allocate(Size size) {
        if (isLarge(size)) {
            return allocateLargeCleared(size);
        }
        return overflowAllocator.allocateCleared(size);
    }

    /**
     * Entry point to allocate storage for TLAB refill.
     */
    @Override
    public Pointer allocateTLAB(Size size) {
        return tlabAllocator.allocateTLAB(size);
    }

    @Override
    public boolean contains(Address address) {
        final HeapRegionInfo regionInfo = fromAddress(address);
        return regionInfo.owner() == this;
    }

    @Override
    public boolean canSatisfyAllocation(Size size) {
        // Keep it simple and over-conservative for now.
        if (isLarge(size)) {
            // Over simplistic. Assumes all regions in the allocationRegions list are empty.
            int numRegionsNeeded = size.alignUp(HeapRegionConstants.regionSizeInBytes).unsignedShiftedRight(HeapRegionConstants.log2RegionSizeInBytes).toInt();
            return numRegionsNeeded <= allocationRegions.size();
        }
        if (allocationRegions.size() > 0 || overflowAllocator.freeSpace().greaterEqual(size)) {
            return true;
        }
        return false;
    }

    @Override
    public Size totalSpace() {
        return Size.fromInt(heapAccount.used()).shiftedLeft(HeapRegionConstants.log2RegionSizeInBytes);
    }

    @Override
    public Size freeSpace() {
        return allocationRegionsFreeSpace.plus(tlabAllocator.refillManager.freeSpace().plus(tlabAllocator.freeSpace().plus(overflowAllocator.freeSpace())));
    }

    @Override
    public Size usedSpace() {
        return totalSpace().minus(freeSpace());
    }

    @Override
    public void makeParsable() {
        overflowAllocator.makeParsable();
        tlabAllocator.makeParsable();
    }

    public void mark(TricolorHeapMarker heapMarker) {
        HeapRegionRangeIterable allRegions = heapAccount.allocatedRegions();
        allRegions.reset();
        heapMarker.markAll(allRegions);
    }


    @Override
    public void verify(AfterMarkSweepVerifier verifier) {
        tlabAllocationRegions.checkIsAddressOrdered();
        allocationRegions.checkIsAddressOrdered();
        HeapRegionRangeIterable allRegions = heapAccount.allocatedRegions();
        allRegions.reset();
        while (allRegions.hasNext()) {
            HeapRegionInfo.walk(allRegions.next(), verifier);
        }
    }


    public void sweep(TricolorHeapMarker heapMarker) {
        HeapRegionList allRegions = heapAccount.allocatedRegions().regionList;
        if (MaxineVM.isDebug()) {
            allRegions.checkIsAddressOrdered();
        }
        tlabAllocationRegions.clear();
        allocationRegions.clear();
        regionInfoIterable.initialize(allRegions);
        regionInfoIterable.reset();
        heapMarker.sweep(this);
    }

    @Override
    public boolean hasNextSweepingRegion() {
        return regionInfoIterable.hasNext();
    }

    @Override
    public void beginSweep() {
        resetSweepingRegion(regionInfoIterable.next());
    }


    private void traceSweptRegion() {
        Log.print("#");
        Log.print(csrInfo.toRegionID());
        if (csrInfo.isAllocating()) {
            if (csrFreeChunks > 1 || minOverflowRefillSize.greaterThan(csrFreeBytes)) {
                Log.print("'T (");
                Log.print("'A,  nc: ");
                Log.print(csrFreeChunks);
                Log.print(", nb: ");
            } else {
                Log.print("'A,  nc: 1, nb: ");
            }
            Log.println(csrFreeBytes);
        } else if (csrInfo.isFull()) {
            Log.println("  F");
        } else if (csrInfo.isEmpty()) {
            Log.println("  E");
        } else {
            FatalError.unexpected("Unknown region state");
        }
    }

    @Override
    public void endSweep() {
        if (csrIsLarge) {
            // Large object regions are at least 2 regions long.
            if (csrFreeBytes == 0) {
                // Skip all intermediate region. They are full.
                while (!csrInfo.next().isTailOfLargeObject()) {
                    csrInfo = regionInfoIterable.next();
                }
            } else {
                // Free all intermediate regions. The tail needs to be swept
                // in case it was used for allocating small objects, so we
                // don't free it. It'll be set as the next sweeping region by the next call to beginSweep.
                while (!csrInfo.next().isTailOfLargeObject()) {
                    csrInfo = regionInfoIterable.next();
                    csrInfo.setEmpty();
                    allocationRegions.append(csrInfo.toRegionID());
                }
            }
        } else if (csrFreeBytes == 0) {
            csrInfo.setFull();
        } else if (csrFreeBytes == regionSizeInBytes) {
            csrInfo.setEmpty();
            allocationRegions.append(csrInfo.toRegionID());
        } else if (csrFreeChunks == 1 && minOverflowRefillSize.lessEqual(csrFreeBytes)) {
            csrInfo.setFreeChunks(HeapFreeChunk.fromHeapFreeChunk(csrHead), (short) csrFreeBytes, (short) csrFreeChunks);
            allocationRegions.append(csrInfo.toRegionID());
        } else {
            FatalError.check(csrFreeBytes > 0 && (csrFreeChunks > 1 || minOverflowRefillSize.greaterThan(csrFreeBytes)) && csrHead != null, "unknown state for a swept region");
            csrInfo.setFreeChunks(HeapFreeChunk.fromHeapFreeChunk(csrHead), (short) csrFreeBytes, (short) csrFreeChunks);
            tlabAllocationRegions.append(csrInfo.toRegionID());
        }
        if (TraceSweep) {
            traceSweptRegion();
        }
    }

    @Override
    public Size freeSpaceAfterSweep() {
        return freeSpace();
    }

    @Override
    public Pointer processLiveObject(Pointer liveObject) {
        FatalError.unexpected("Precise Sweeping not implemented");
        return Pointer.zero();
    }
}

