/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.vm.heap.gcx.ms;

import static com.sun.max.vm.VMConfiguration.*;

import com.sun.max.annotate.*;
import com.sun.max.memory.*;
import com.sun.max.platform.*;
import com.sun.max.program.*;
import com.sun.max.unsafe.*;
import com.sun.max.util.timer.*;
import com.sun.max.vm.*;
import com.sun.max.vm.code.*;
import com.sun.max.vm.heap.*;
import com.sun.max.vm.heap.gcx.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.thread.*;

/**
 * A simple mark-sweep collector. Only used for testing / debugging
 * marking and sweeping algorithms.
 * Implements TLAB over a linked list of free chunk provided by an object space manager.
 *
 * @see FreeHeapSpaceManager
 */
public final class MSHeapScheme extends HeapSchemeWithTLABAdaptor {
    private static final int WORDS_COVERED_PER_BIT = 1;
    /**
     * A marking algorithm for the MSHeapScheme.
     */
    @INSPECTED
    final TricolorHeapMarker heapMarker;

    /**
     * Space where objects are allocated from by default.
     * Implements the {@link Sweeper} interface to be notified by a sweeper of
     * free space.
     */
    @INSPECTED
    final FreeHeapSpaceManager objectSpace;

    private final MSCollection collect = new MSCollection();

    final AfterMarkSweepVerifier afterGCVerifier;

    private final AtomicPinCounter pinnedCounter = MaxineVM.isDebug() ? new AtomicPinCounter() : null;

    @HOSTED_ONLY
    public MSHeapScheme() {
        heapMarker = new TricolorHeapMarker(WORDS_COVERED_PER_BIT, new ContiguousHeapRootCellVisitor());
        objectSpace = new FreeHeapSpaceManager();
        afterGCVerifier = new AfterMarkSweepVerifier(heapMarker, objectSpace, AfterMarkSweepBootHeapVerifier.makeVerifier(heapMarker));

        pinningSupportFlags = PIN_SUPPORT_FLAG.makePinSupportFlags(true, false, true);
    }

    @Override
    public void initialize(MaxineVM.Phase phase) {
        super.initialize(phase);
    }

    /**
     * Allocate memory for both the heap and the GC's data structures (mark bitmaps, marking stacks, etc.).
     */
    @Override
    protected void allocateHeapAndGCStorage() {
        final Size reservedSpace = Size.K.times(reservedVirtualSpaceKB());
        final Size initSize = Heap.initialSize();
        final Size maxSize = Heap.maxSize();
        final int pageSize = Platform.platform().pageSize;

        // Verify that the constraint of the heap scheme are met:
        FatalError.check(Heap.bootHeapRegion.start() == Heap.startOfReservedVirtualSpace(),
                        "Boot heap region must be mapped at start of reserved virtual space");

        final Address endOfCodeRegion = Code.getCodeManager().getRuntimeOptCodeRegion().end();
        final Address endOfReservedSpace = Heap.bootHeapRegion.start().plus(reservedSpace);
        final Address immortalStart = endOfCodeRegion.alignUp(pageSize);
        // Relocate immortal memory immediately after the end of the code region.
        ImmortalMemoryRegion immortalRegion = ImmortalHeap.getImmortalHeap();
        FatalError.check(immortalRegion.used().isZero(), "Immortal heap must be unused");
        VirtualMemory.deallocate(immortalRegion.start(), immortalRegion.size(), VirtualMemory.Type.HEAP);
        immortalRegion.setStart(immortalStart);
        immortalRegion.mark.set(immortalStart);
        final Address firstUnusedByteAddress = immortalRegion.end();

        if (firstUnusedByteAddress.greaterThan(endOfReservedSpace)) {
            MaxineVM.reportPristineMemoryFailure("immortalRegion.end", "allocateHeapAndGCStorage", immortalRegion.size());
        }

        final Address heapStart = firstUnusedByteAddress.roundedUpBy(pageSize);
        final Size heapMarkerDatasize = heapMarker.memoryRequirement(maxSize);
        final Address heapMarkerDataStart = heapStart.plus(maxSize).roundedUpBy(pageSize);
        final Address leftoverStart = heapMarkerDataStart.plus(heapMarkerDatasize).roundedUpBy(pageSize);

        try {
            // Use immortal memory for now.
            Heap.enableImmortalMemoryAllocation();
            objectSpace.initialize(this, heapStart, initSize, maxSize, true);
            ContiguousHeapSpace markedSpace = objectSpace.committedHeapSpace;

            // Initialize the heap marker's data structures. Needs to make sure it is outside of the heap reserved space.
            if (!Heap.AvoidsAnonOperations) {
                if (!VirtualMemory.commitMemory(heapMarkerDataStart, heapMarkerDatasize,  VirtualMemory.Type.DATA)) {
                    MaxineVM.reportPristineMemoryFailure("heapMarkerDataStart", "commit", heapMarkerDatasize);
                }
            }
            heapMarker.initialize(markedSpace.start(), markedSpace.committedEnd(), heapMarkerDataStart, heapMarkerDatasize);

            // Free reserved space we will not be using.
            Size leftoverSize = endOfReservedSpace.minus(leftoverStart).asSize();
            // First, uncommit range we want to free (this will create a new mapping that can then be deallocated)
            if (!Heap.AvoidsAnonOperations) {
                if (!VirtualMemory.uncommitMemory(leftoverStart, leftoverSize,  VirtualMemory.Type.DATA)) {
                    MaxineVM.reportPristineMemoryFailure("reserved space leftover", "uncommit", leftoverSize);
                }
            }
            if (VirtualMemory.deallocate(leftoverStart, leftoverSize, VirtualMemory.Type.DATA).isZero()) {
                MaxineVM.reportPristineMemoryFailure("reserved space leftover", "deallocate", leftoverSize);
            }

            // From now on, we can allocate.
            // Make the heap (and mark bitmap) inspectable
            HeapScheme.Inspect.init(true);
            HeapScheme.Inspect.notifyHeapRegions(markedSpace, heapMarker.memory());
        } finally {
            Heap.disableImmortalMemoryAllocation();
        }
    }

    public boolean collectGarbage() {
        final GCRequest gcRequest = VmThread.current().gcRequest;
        if (gcRequest.explicit) {
            collect.submit();
            return true;
        }
        // We may reach here after a race. Don't run GC if request can be satisfied.

        // TODO (ld) might be better to try allocate the requested space and save the result for the caller.
        // This may avoid starvation case where in concurrent threads allocate the requested space
        // in after this method returns but before the caller allocated the space..
        if (objectSpace.canSatisfyAllocation(gcRequest.requestedBytes)) {
            return true;
        }
        collect.submit();
        return objectSpace.canSatisfyAllocation(gcRequest.requestedBytes);
    }

    public boolean contains(Address address) {
        return objectSpace.contains(address);
    }

    public Size reportFreeSpace() {
        return objectSpace.freeSpace();
    }

    public Size reportUsedSpace() {
        return objectSpace.usedSpace();
    }

    @INLINE
    public boolean pin(Object object) {
        // Objects never relocate. So this is always safe.
        if (MaxineVM.isDebug()) {
            pinnedCounter.increment();
        }
        return true;
    }

    @INLINE
    public void unpin(Object object) {
        if (MaxineVM.isDebug()) {
            pinnedCounter.decrement();
        }
    }

    @INLINE
    public void writeBarrier(Reference from, Reference to) {
    }

    private static final class MSGCRequest extends GCRequest {
        protected MSGCRequest(VmThread thread) {
            super(thread);
        }
    }

    public GCRequest createThreadLocalGCRequest(VmThread vmThread) {
        return new MSGCRequest(vmThread);
    }

    /**
     * Class implementing the garbage collection routine.
     * This is the {@link VmOperationThread}'s entry point to garbage collection.
     */
    final class MSCollection extends GCOperation {
        public MSCollection() {
            super("MSCollection");
        }

        private final TimerMetric reclaimTimer = new TimerMetric(new SingleUseTimer(HeapScheme.GC_TIMING_CLOCK));
        private final TimerMetric totalPauseTime = new TimerMetric(new SingleUseTimer(HeapScheme.GC_TIMING_CLOCK));

        private boolean traceGCTimes = false;

        private void startTimer(Timer timer) {
            if (traceGCTimes) {
                timer.start();
            }
        }
        private void stopTimer(Timer timer) {
            if (traceGCTimes) {
                timer.stop();
            }
        }

        private void reportLastGCTimes() {
            final boolean lockDisabledSafepoints = Log.lock();
            Log.print("Timings (");
            Log.print(TimerUtil.getHzSuffix(HeapScheme.GC_TIMING_CLOCK));
            Log.print(") for GC ");
            Log.print(collectionCount);
            Log.print(" : ");
            heapMarker.reportLastElapsedTimes();
            Log.print(", sweeping=");
            Log.print(reclaimTimer.getLastElapsedTime());
            Log.print(", total=");
            Log.println(totalPauseTime.getLastElapsedTime());
            Log.unlock(lockDisabledSafepoints);
        }

        private void reportTotalGCTimes() {
            final boolean lockDisabledSafepoints = Log.lock();
            Log.print("Timings (");
            Log.print(TimerUtil.getHzSuffix(HeapScheme.GC_TIMING_CLOCK));
            Log.print(") for all GC: ");
            heapMarker.reportTotalElapsedTimes();
            Log.print(", sweeping=");
            Log.print(reclaimTimer.getElapsedTime());
            Log.print(", total=");
            Log.println(totalPauseTime.getElapsedTime());
            Log.unlock(lockDisabledSafepoints);
        }

        private Size reclaim() {
            startTimer(reclaimTimer);
            objectSpace.beginSweep();
            heapMarker.impreciseSweep(objectSpace);
            objectSpace.endSweep();
            stopTimer(reclaimTimer);
            return objectSpace.freeSpaceAfterSweep();
        }

        private HeapResizingPolicy heapResizingPolicy = new HeapResizingPolicy();

        @Override
        public void collect(int invocationCount) {
            traceGCTimes = Heap.logGCTime();
            startTimer(totalPauseTime);
            VmThreadMap.ACTIVE.forAllThreadLocals(null, tlabFiller);

            HeapScheme.Inspect.notifyHeapPhaseChange(HeapPhase.ANALYZING);

            vmConfig().monitorScheme().beforeGarbageCollection();
            objectSpace.doBeforeGC();

            collectionCount++;
            if (MaxineVM.isDebug() && Heap.logGCPhases()) {
                Log.print("Begin mark-sweep #");
                Log.println(collectionCount);
            }
            heapMarker.markAll();
            HeapScheme.Inspect.notifyHeapPhaseChange(HeapPhase.RECLAIMING);
            Size freeSpaceAfterGC = reclaim();
            if (VerifyAfterGC) {
                afterGCVerifier.run();
            }
            vmConfig().monitorScheme().afterGarbageCollection();

            if (heapResizingPolicy.resizeAfterCollection(freeSpaceAfterGC, objectSpace)) {
                // Heap was resized.
                // Update heapMarker's coveredArea.
                ContiguousHeapSpace markedSpace = objectSpace.committedHeapSpace;
                heapMarker.setCoveredArea(markedSpace.start(), markedSpace.committedEnd());
            }
            if (MaxineVM.isDebug() && Heap.logGCPhases()) {
                Log.print("End mark-sweep #");
                Log.println(collectionCount);
            }
            final GCRequest gcRequest = callingThread().gcRequest;
            gcRequest.lastInvocationCount = invocationCount;
            HeapScheme.Inspect.notifyHeapPhaseChange(HeapPhase.MUTATING);
            stopTimer(totalPauseTime);

            if (traceGCTimes) {
                reportLastGCTimes();
            }
        }
    }

    private Size setNextTLABChunk(Pointer chunk) {
        if (MaxineVM.isDebug()) {
            FatalError.check(!chunk.isZero(), "TLAB chunk must not be null");
        }
        Size chunkSize =  HeapFreeChunk.getFreechunkSize(chunk);
        Size effectiveSize = chunkSize.minus(tlabHeadroom());
        Address nextChunk = HeapFreeChunk.getFreeChunkNext(chunk);
        // Zap chunk data to leave allocation area clean.
        Memory.clearWords(chunk, effectiveSize.unsignedShiftedRight(Word.widthValue().log2numberOfBytes).toInt());
        chunk.plus(effectiveSize).setWord(nextChunk);
        return effectiveSize;
    }

    @INLINE
    private Size setNextTLABChunk(Pointer etla, Pointer nextChunk) {
        Size nextChunkEffectiveSize = setNextTLABChunk(nextChunk);
        fastRefillTLAB(etla, nextChunk, nextChunkEffectiveSize);
        return nextChunkEffectiveSize;
    }

    /**
     * Check if changing TLAB chunks may satisfy the allocation request. If not, allocated directly from the underlying free space manager,
     * otherwise, refills the TLAB with the next TLAB chunk and allocated from it.
     *
     * @param etla Pointer to enabled VMThreadLocals
     * @param tlabMark current mark of the TLAB
     * @param tlabHardLimit hard limit of the current TLAB
     * @param chunk next chunk of this TLAB
     * @param size requested amount of memory
     * @return a pointer to the allocated memory
     */
    private Pointer changeTLABChunkOrAllocate(Pointer etla, Pointer tlabMark, Pointer tlabHardLimit, Pointer chunk, Size size) {
        Size chunkSize =  HeapFreeChunk.getFreechunkSize(chunk);
        Size effectiveSize = chunkSize.minus(tlabHeadroom());
        if (size.greaterThan(effectiveSize))  {
            // Don't bother with searching another TLAB chunk that fits. Allocate out of TLAB.
            return objectSpace.allocate(size);
        }
        Address nextChunk = HeapFreeChunk.getFreeChunkNext(chunk);
        // We will not reuse the leftover, turn it into dark matter.
        DarkMatter.format(tlabMark, tlabHardLimit);
        // Zap chunk data to leave allocation area clean.
        Memory.clearWords(chunk, effectiveSize.unsignedShiftedRight(Word.widthValue().log2numberOfBytes).toInt());
        chunk.plus(effectiveSize).setWord(nextChunk);
        fastRefillTLAB(etla, chunk, effectiveSize);
        return tlabAllocate(size);
    }

    /**
     * Allocate a chunk of memory of the specified size and refill a thread's TLAB with it.
     * @param etla the thread whose TLAB will be refilled
     * @param tlabSize the size of the chunk of memory used to refill the TLAB
     */
    private void allocateAndRefillTLAB(Pointer etla, Size tlabSize) {
        Pointer tlab = objectSpace.allocateTLAB(tlabSize);
        Size effectiveSize = setNextTLABChunk(tlab);
        refillTLAB(etla, tlab, effectiveSize);
    }

    @Override
    protected Pointer customAllocate(Pointer customAllocator, Size size) {
        // Default is to use the immortal heap.
        return ImmortalHeap.allocate(size, true);
    }

    @Override
    protected Pointer handleTLABOverflow(Size size, Pointer etla, Pointer tlabMark, Pointer tlabEnd) {      // Should we refill the TLAB ?
        final TLABRefillPolicy refillPolicy = TLABRefillPolicy.getForCurrentThread(etla);
        if (refillPolicy == null) {
            // No policy yet for the current thread. This must be the first time this thread uses a TLAB (it does not have one yet).
            ProgramError.check(tlabMark.isZero(), "thread must not have a TLAB yet");
            if (!usesTLAB()) {
                // We're not using TLAB. So let's assign the never refill tlab policy.
                TLABRefillPolicy.setForCurrentThread(etla, NEVER_REFILL_TLAB);
                return objectSpace.allocate(size);
            }
            // Allocate an initial TLAB and a refill policy. For simplicity, this one is allocated from the TLAB (see comment below).
            final Size tlabSize = initialTlabSize();
            allocateAndRefillTLAB(etla, tlabSize);
            // Let's do a bit of dirty meta-circularity. The TLAB is refilled, and no-one except the current thread can use it.
            // So the tlab allocation is going to succeed here
            TLABRefillPolicy.setForCurrentThread(etla, new SimpleTLABRefillPolicy(tlabSize));
            // Now, address the initial request. Note that we may recurse down to handleTLABOverflow again here if the
            // request is larger than the TLAB size. However, this second call will succeed and allocate outside of the tlab.
            return tlabAllocate(size);
        }
        final Size nextTLABSize = refillPolicy.nextTlabSize();
        if (size.greaterThan(nextTLABSize)) {
            // This couldn't be allocated in a TLAB, so go directly to direct allocation routine.
            return objectSpace.allocate(size);
        }
        // TLAB may have been wiped out by a previous direct allocation routine.
        if (!tlabEnd.isZero()) {
            final Pointer hardLimit = tlabEnd.plus(tlabHeadroom());
            final Pointer nextChunk = tlabEnd.getWord().asPointer();

            final Pointer cell = tlabMark;
            if (cell.plus(size).equals(hardLimit)) {
                // Can actually fit the object in space left.
                // zero-fill the headroom we left.
                Memory.clearWords(tlabEnd, tlabHeadroomNumWords());
                if (nextChunk.isZero()) {
                    // Zero-out TLAB top and mark.
                    fastRefillTLAB(etla, Pointer.zero(), Size.zero());
                } else {
                    // TLAB has another chunk of free space. Set it.
                    setNextTLABChunk(etla, nextChunk);
                }
                return cell;
            } else if (!(cell.equals(hardLimit) || nextChunk.isZero())) {
                // We have another chunk, and we're not to limit yet. So we may change of TLAB chunk to satisfy the request.
                return changeTLABChunkOrAllocate(etla, tlabMark, hardLimit, nextChunk, size);
            }

            if (!refillPolicy.shouldRefill(size, tlabMark)) {
                // Size would fit in a new tlab, but the policy says we shouldn't refill the tlab yet, so allocate directly in the heap.
                return objectSpace.allocate(size);
            }
            // Leave as is. Parsability of the TLAB will be taken care by what follows.
        }
        // Refill TLAB and allocate (we know the request can be satisfied with a fresh TLAB and will therefore succeed).
        allocateAndRefillTLAB(etla, nextTLABSize);
        return tlabAllocate(size);
    }

    @Override
    public PhaseLogger phaseLogger() {
        return HeapSchemeLoggerAdaptor.phaseLogger;
    }

    @Override
    public TimeLogger timeLogger() {
        return HeapSchemeLoggerAdaptor.timeLogger;
    }

}

