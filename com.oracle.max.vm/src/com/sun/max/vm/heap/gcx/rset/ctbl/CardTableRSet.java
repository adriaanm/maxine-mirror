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
package com.sun.max.vm.heap.gcx.rset.ctbl;

import static com.sun.max.vm.heap.HeapSchemeAdaptor.*;

import java.util.*;

import com.sun.cri.ci.CiAddress.Scale;
import com.sun.cri.ci.*;
import com.sun.cri.xir.*;
import com.sun.cri.xir.CiXirAssembler.XirConstant;
import com.sun.cri.xir.CiXirAssembler.XirOperand;
import com.sun.max.annotate.*;
import com.sun.max.memory.*;
import com.sun.max.platform.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.MaxineVM.Phase;
import com.sun.max.vm.code.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.heap.*;
import com.sun.max.vm.heap.gcx.*;
import com.sun.max.vm.heap.gcx.rset.*;
import com.sun.max.vm.hosted.*;
import com.sun.max.vm.layout.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.runtime.*;
/**
 * Card-table based remembered set.
 */
public class CardTableRSet extends DeadSpaceListener implements HeapManagementMemoryRequirement {

    /**
     * Log2 of a card size in bytes.
     */
    static final int LOG2_CARD_SIZE = 9;

    /**
     * Number of bytes per card.
     */
    static final int CARD_SIZE = 1 << LOG2_CARD_SIZE;

    static final int LOG2_NUM_WORDS_PER_CARD = LOG2_CARD_SIZE - Word.widthValue().log2numberOfBytes;

    static final int NUM_WORDS_PER_CARD = 1 << LOG2_NUM_WORDS_PER_CARD;

    static final int NO_CARD_INDEX = -1;

    static final Address CARD_ADDRESS_MASK = Address.fromInt(CARD_SIZE - 1).not();

    private static boolean TraceCardTableRSet = false;

    static {
        if (MaxineVM.isDebug()) {
            VMOptions.addFieldOption("-XX:", "TraceCardTableRSet", CardTableRSet.class, "Enables CardTableRSet Debugging Traces", Phase.PRISTINE);
        }
    }

    @INLINE
    public static boolean traceCardTableRSet() {
        return MaxineVM.isDebug() && TraceCardTableRSet;
    }

    public static void setTraceCardTableRSet(boolean flag) {
        TraceCardTableRSet = flag;
    }

    /**
     * Contiguous regions of virtual memory holding the card table data.
     * Mostly used to feed the inspector.
     */
    final MemoryRegion cardTableMemory;

    /**
     * The table recording card state. The table is updated by compiler-generated write-barrier execution and explicitely by the GC.
     */
    public final CardTable cardTable;

    /**
     * The table recording the first object overlapping with every card.
     * The table is updated by allocators and free space reclamation.
     */
    public final CardFirstObjectTable cfoTable;

    /**
     * CiConstant holding the card table's biased address in boot code region and used for all XirSnippets implementing the write barrier.
     * Biased card table address XirConstant are initialized with this CiConstant which holds a  WrappedWord object with a dummy address.
     * The WrappedWord object can be used during the serializing phase of boot image generation  to identify easily the literal locations
     * in the boot code region that hold the biased card table address. A table of these location is added in the boot image and used at
     * VM startup to patch them with the actual biased card table address once this one is known.
     * See {@link #recordLiteralsLocations()}
     * See {@link #patchBootCodeLiterals()}
     */
    @HOSTED_ONLY
    private CiConstant biasedCardTableAddressCiConstant;

    @HOSTED_ONLY
    private List<XirConstant> xirConstants = new ArrayList<XirConstant>(16);

    /**
     * List of XIR constants representing the biased card table address.
     * The list is used at startup to initialize the "startup-time" constant value.
     */
    private XirBiasedCardTableConstant [] biasedCardTableAddressXirConstants = new XirBiasedCardTableConstant[0];

    @HOSTED_ONLY
    ReferenceLiteralLocationRecorder literalRecorder;

    /**
     * Table holding all the locations in the boot code region of reference literals to the biased card table.
     * This allows to patch these literals with the actual correct value of the biased card table, known only at
     * heap scheme pristine initialization.
     * The index are word indexes relative to the start of the boot code region.
     */
    private int [] bootCardTableLiterals;

    private void patchBootCodeLiterals() {
        final Pointer base = Code.bootCodeRegion().start().asPointer();
        final Address biasedTableAddress = cardTable.biasedTableAddress;
        for (int literalPos : bootCardTableLiterals) {
            base.setWord(literalPos, biasedTableAddress);
        }
    }

    public CardTableRSet() {
        cardTable = new CardTable();
        cfoTable = new CardFirstObjectTable();
        cardTableMemory = new MemoryRegion("Card and FOT tables");
    }

    public void initialize(MaxineVM.Phase phase) {
        if (MaxineVM.isHosted()) {
            if (phase == Phase.BOOTSTRAPPING) {
                Log2RegionToByteMapTable.hostInitialize();
            } else if (phase == Phase.SERIALIZING_IMAGE) {
                // Build a table of indexes to reference literals that point to the card table.
                assert biasedCardTableAddressCiConstant != null;
                literalRecorder = new ReferenceLiteralLocationRecorder(Code.bootCodeRegion(), biasedCardTableAddressCiConstant.asObject());
                bootCardTableLiterals = literalRecorder.getLiteralLocations();
                biasedCardTableAddressXirConstants = xirConstants.toArray(biasedCardTableAddressXirConstants);
            } else if (phase == MaxineVM.Phase.WRITING_IMAGE) {
                literalRecorder.fillLiteralLocations();
            }
        } else if (phase == MaxineVM.Phase.PRIMORDIAL) {
            final Size reservedSpace = Size.K.times(VMConfiguration.vmConfig().heapScheme().reservedVirtualSpaceKB());
            final Size bootCardTableSize = memoryRequirement(Heap.bootHeapRegion.size()).roundedUpBy(Platform.platform().pageSize);
            final Address bootCardTableStart = Heap.bootHeapRegion.start().plus(reservedSpace).minus(bootCardTableSize);
            initialize(Heap.bootHeapRegion.start(), Heap.bootHeapRegion.size(), bootCardTableStart, bootCardTableSize);
        }
    }

    public void initialize(Address coveredAreaStart, Size coveredAreaSize, Address cardTableDataStart, Size cardTableDataSize) {
        cardTableMemory.setStart(cardTableDataStart);
        cardTableMemory.setSize(cardTableDataSize);
        cardTable.initialize(coveredAreaStart, coveredAreaSize, cardTableDataStart);
        final Address cfoTableStart = cardTableDataStart.plus(cardTable.tableSize(coveredAreaSize).wordAligned());
        cfoTable.initialize(coveredAreaStart, coveredAreaSize, cfoTableStart);
        if (bootCardTableLiterals != null) {
            patchBootCodeLiterals();
        }
    }

    static class XirBiasedCardTableConstant extends CiXirAssembler.XirConstant {
        XirBiasedCardTableConstant(CiXirAssembler asm, CiConstant value) {
            super(asm, "Card Table biased-address", value);
            asm.recordConstant(this);
        }

        void setStartupValue(CiConstant startupValue) {
            value = startupValue;
        }
    }

    public void initializeXirStartupConstants() {
        final CiConstant biasedCardTableCiConstant = CiConstant.forLong(cardTable.biasedTableAddress.toLong());
        for (XirBiasedCardTableConstant c : biasedCardTableAddressXirConstants) {
            c.setStartupValue(biasedCardTableCiConstant);
        }
    }

    @HOSTED_ONLY
    private XirConstant biasedCardTableAddressXirConstant(CiXirAssembler asm) {
        if (biasedCardTableAddressCiConstant == null) {
            biasedCardTableAddressCiConstant = WordUtil.wrappedConstant(Address.fromLong(123456789L));
        }
        XirConstant constant = new XirBiasedCardTableConstant(asm, biasedCardTableAddressCiConstant);
        xirConstants.add(constant);
        return constant;
    }

    @HOSTED_ONLY
    public void genTuplePostWriteBarrier(CiXirAssembler asm, XirOperand tupleCell) {
        final XirOperand temp = asm.createTemp("temp", WordUtil.archKind());
        asm.shr(temp, tupleCell, asm.i(CardTableRSet.LOG2_CARD_SIZE));
        // Watch out: this create a reference literal that will not point to an object!
        // The GC will need to carefully skip reference table entries holding the biased base of the card table.
        // final XirConstant biasedCardTableAddress = asm.createConstant(CiConstant.forObject(dummyCardTable));
        final XirConstant biasedCardTableAddress = biasedCardTableAddressXirConstant(asm);
        asm.pstore(CiKind.Byte, biasedCardTableAddress, temp, asm.i(CardState.DIRTY_CARD.value()), false);

        // FIXME: remove this temp debug code
        if (MaxineVM.isDebug()) {
            // Just so that we get the address of the card entry in a register when inspecting...
            asm.lea(temp, biasedCardTableAddress, temp, 0, Scale.Times1);
        }
    }

    @HOSTED_ONLY
    public void genArrayPostWriteBarrier(CiXirAssembler asm, XirOperand arrayCell, XirOperand elemIndex) {
        final XirOperand temp = asm.createTemp("temp", WordUtil.archKind());
        final Scale scale = Scale.fromInt(Word.size());
        final int disp = Layout.referenceArrayLayout().getElementOffsetInCell(0).toInt();
        asm.lea(temp, arrayCell, elemIndex, disp, scale);
        asm.shr(temp, temp, asm.i(CardTableRSet.LOG2_CARD_SIZE));
        // final XirConstant biasedCardTableAddress = asm.createConstant(CiConstant.forObject(dummyCardTable));
        final XirConstant biasedCardTableAddress = biasedCardTableAddressXirConstant(asm);
        asm.pstore(CiKind.Byte, biasedCardTableAddress, temp, asm.i(CardState.DIRTY_CARD.value()), false);
    }

    /**
     * Record update to a reference slot of a cell.
     * @param ref the cell whose reference is updated
     * @param offset the offset from the origin of the cell to the updated reference.
     */
    public void record(Reference ref, Offset offset) {
        cardTable.dirtyCovered(ref.toOrigin().plus(offset));
    }

    /**
     * Record update to a reference slot of a cell.
     * @param ref the cell whose reference is updated
     * @param displacement a displacement from the origin of the cell
     * @param index a word index to the updated reference
     */
    public void record(Reference ref,  int displacement, int index) {
        cardTable.dirtyCovered(ref.toOrigin().plus(Address.fromInt(index).shiftedLeft(Word.widthValue().log2numberOfBytes).plus(displacement)));
    }

    /**
     * Visit the cells that overlap a card.
     *
     * @param cardIndex index of the card
     * @param cellVisitor the logic to apply to the visited cell
     */
    private void visitCard(int cardIndex, OverlappingCellVisitor cellVisitor) {
        visitCards(cardIndex, cardIndex + 1, cellVisitor);
    }

    /**
     * Visit all the cells that overlap the specified range of cards.
     * The range of visited cards extends from the card startCardIndex, inclusive, to the
     * card endCardIndex, exclusive.
     *
     * @param cardIndex index of the card
     * @param cellVisitor the logic to apply to the visited cell
     */
    private void visitCards(int startCardIndex, int endCardIndex, OverlappingCellVisitor cellVisitor) {
        final Address start = cardTable.rangeStart(startCardIndex);
        final Address end = cardTable.rangeStart(endCardIndex);
        Pointer cell = cfoTable.cellStart(startCardIndex).asPointer();
        do {
            cell = cellVisitor.visitCell(cell, start, end);
            if (MaxineVM.isDebug()) {
                // FIXME: this is too strong, because DeadSpaceCardTableUpdater may leave a FOT entry temporarily out-dated by up
                // to MIN_OBJECT_SIZE words when the HeapFreeChunkHeader of the space the allocator was refill with is immediately
                // before a card boundary.
                // I 'm leaving this as is now to see whether we ever run into this case, but this
                // should really be cell.plus(MIN_OBJECT_SIZE).greaterThan(start);
                FatalError.check(cell.plus(HeapSchemeAdaptor.MIN_OBJECT_SIZE).greaterThan(start), "visited cell must overlap visited card.");
            }
        } while (cell.lessThan(end));
    }

    private void traceVisitedCard(int startCardIndex, int endCardIndex, CardState cardState) {
        Log.print("Visiting ");
        Log.print(cardState.name());
        Log.print(" cards [");
        Log.print(startCardIndex);
        Log.print(", ");
        Log.print(endCardIndex);
        Log.print("]  (");
        Log.print(cardTable.rangeStart(startCardIndex));
        Log.print(", ");
        Log.print(cardTable.rangeStart(endCardIndex));
        Log.print(")  R = ");
        Log.print(RegionTable.theRegionTable().regionID(cardTable.rangeStart(startCardIndex)));
        Log.println(")");
    }

    /**
     * Iterate over cells that overlap the specified region and comprises recorded reference locations.
     * @param start
     * @param end
     * @param cellVisitor
     */
    public void cleanAndVisitCards(Address start, Address end, OverlappingCellVisitor cellVisitor) {
        final int endOfRange = cardTable.tableEntryIndex(end);
        int startCardIndex = cardTable.first(cardTable.tableEntryIndex(start), endOfRange, CardState.DIRTY_CARD);
        while (startCardIndex < endOfRange) {
            int endCardIndex = cardTable.firstNot(startCardIndex + 1, endOfRange, CardState.DIRTY_CARD);
            if (traceCardTableRSet()) {
                traceVisitedCard(startCardIndex, endCardIndex, CardState.DIRTY_CARD);
            }
            cardTable.clean(startCardIndex, endCardIndex);
            visitCards(startCardIndex, endCardIndex, cellVisitor);
            if (++endCardIndex >= endOfRange) {
                return;
            }
            startCardIndex = cardTable.first(endCardIndex, endOfRange, CardState.DIRTY_CARD);
        }
    }

    public void visitCards(Address start, Address end, CardState cardState, OverlappingCellVisitor cellVisitor) {
        final int endOfRange = cardTable.tableEntryIndex(end);
        int startCardIndex = cardTable.first(cardTable.tableEntryIndex(start), endOfRange, cardState);
        while (startCardIndex < endOfRange) {
            int endCardIndex = cardTable.firstNot(startCardIndex + 1, endOfRange, cardState);
            if (traceCardTableRSet()) {
                traceVisitedCard(startCardIndex, endCardIndex, cardState);
            }
            visitCards(startCardIndex, endCardIndex, cellVisitor);
            if (++endCardIndex >= endOfRange) {
                return;
            }
            startCardIndex = cardTable.first(endCardIndex, endOfRange, cardState);
        }
    }

    @Override
    public Size memoryRequirement(Size maxCoveredAreaSize) {
        return cardTable.tableSize(maxCoveredAreaSize).plus(cfoTable.tableSize(maxCoveredAreaSize));
    }

    /**
     * Contiguous region of memory used by the remembered set.
     * @return a non-null {@link MemoryRegion}
     */
    public MemoryRegion memory() {
        return cardTableMemory;
    }

    /**
     * Update the remembered set to take into account the newly freed space.
     * @param start address to the first word of the freed chunk
     * @param size size of the freed chunk
     */
    public void updateForFreeSpace(Address start, Size size) {
        final Address end = start.plus(size);
        // Note: this doesn't invalid subsequent entries of the FOT table.
        cfoTable.set(start, end);
        // Clean cards that are completely overlapped by the free space only.
        cardTable.setCardsInRange(start, end, CardState.CLEAN_CARD);
    }

    public static Address alignUpToCard(Address coveredAddress) {
        return coveredAddress.plus(CARD_SIZE).and(CARD_ADDRESS_MASK);
    }

    public static Address alignDownToCard(Address coveredAddress) {
        return coveredAddress.and(CARD_ADDRESS_MASK);
    }

    @Override
    public void notifyCoaslescing(Address deadSpace, Size numDeadBytes) {
        // Allocation may occur while iterating over dirty cards to evacuate young objects. Such allocations may temporarily invalidate
        // the FOT for cards overlapping with the allocator, and may break the ability to walk over these cards.
        // One solution is keep the FOT up to date at every allocation, which may be expensive.
        // Another solution is to make the last card of free chunk used by allocator independent of most allocation activity.
        // The last card is the only one that may be dirtied, and therefore the only one that can be walked over during evacuation.
        //
        // Let s and e be the start and end of a free chunk.
        // Let c be the top-most card address such that c >= e, and C be the card starting at c, with FOT(C) denoting the entry
        // of the FOT for card C.
        // If e == c,  whatever objects are allocated between s and e, FOT(C) == 0, i.e., allocations never invalidate FOT(C).
        // Thus C is iterable at all time by a dirty card walker.
        //
        // If e > c, then FOT(C) might be invalidated by allocation.
        // We may avoid this by formatting the space delimited by [c,e] as a dead object embedded in the heap free chunk.
        // This will allow FOT(C) to be zero, and not be invalidated by any allocation of space before C.
        // Formatting [c,e] has however several corner cases:
        // 1. [c,e] may be smaller than the minimum object size, so we can't format it.
        // Instead, we can format [x,e], such x < e and [x,e] is the min object size. In this case, FOT(C) = x - e.
        // [x,e] can only be overwritten by the last allocation from the buffer,
        // so FOT(C) doesn't need to be updated until that last allocation,
        // which is always special cased (see why below).
        // 2. s + sizeof(free chunk header) > c, i.e., the head of the free space chunk overlap C
        // Don't reformat the heap chunk. FOT(C) will be updated correctly since the allocator
        // always set the FOT table for the newly allocated cells. Since this one always
        if (numDeadBytes.greaterEqual(CARD_SIZE)) {
            final Pointer end = deadSpace.plus(numDeadBytes).asPointer();
            final Address lastCardStart = alignDownToCard(end);
            if (lastCardStart.minus(deadSpace).greaterThan(HeapFreeChunk.heapFreeChunkHeaderSize())) {
                // Format the end of the heap free chunk as a dead object.
                Pointer deadObjectAddress = lastCardStart.asPointer();
                Size deadObjectSize = end.minus(deadObjectAddress).asSize();

                if (deadObjectSize.lessThan(MIN_OBJECT_SIZE)) {
                    deadObjectSize = MIN_OBJECT_SIZE;
                    deadObjectAddress = end.minus(deadObjectSize);
                }
                if (MaxineVM.isDebug() && CardFirstObjectTable.TraceFOT) {
                    Log.print("Update Card Table for reclaimed range [");
                    Log.print(deadSpace); Log.print(", ");
                    Log.print(end);
                    Log.print("] / tail dead object at ");
                    Log.print(deadObjectAddress);
                    Log.print(" card # ");
                    Log.println(cardTable.tableEntryIndex(deadObjectAddress));
                }
                HeapSchemeAdaptor.fillWithDeadObject(deadObjectAddress, end);
                updateForFreeSpace(deadSpace, deadObjectAddress.minus(deadSpace).asSize());
                updateForFreeSpace(deadObjectAddress, deadObjectSize);
                return;
            }
        }
        // Otherwise, the free chunk is either smaller than a card, or it is smaller than two cards and its header spawn the two cards.
        updateForFreeSpace(deadSpace, numDeadBytes);
    }

    @Override
    public void notifySplit(Address start, Address end, Size leftSize) {
        cfoTable.split(start, start.plus(leftSize), end);
    }
}
