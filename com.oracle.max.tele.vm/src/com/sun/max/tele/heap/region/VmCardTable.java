/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.tele.heap.region;

import static com.sun.max.tele.MaxMarkBitmap.MarkColor.*;

import java.io.*;
import java.util.*;

import com.sun.max.tele.*;
import com.sun.max.tele.data.*;
import com.sun.max.tele.memory.*;
import com.sun.max.tele.object.*;
import com.sun.max.tele.reference.*;
import com.sun.max.tele.util.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.heap.*;
import com.sun.max.vm.heap.gcx.*;

/**
 * A specialized subclass of the Mark Bitmap used by some GC implementations. The singleton instance, corresponding to
 * the one used in the VM, is not created until the VM heap scheme has allocated the bit map data and initialized
 * everything, so no refreshing is needed.
 * <p>
 * The Mark Bitmap's data is stored in a region separate from the heap, allocated from the OS. The region is filled with
 * a single array (in standard Maxine format) of longs.
 */
public final class VmCardTable extends AbstractVmHolder implements MaxCardTable, VmObjectHoldingRegion<MaxCardTable> {

    private static final String ENTITY_NAME = "Heap-Card Table data";

    /**
     * Representation of a VM memory region used to hold a card Table.  The card table is implemented as a single long array that
     * occupied the entire region.
     * <p>
     * This region has no parent; it is allocated dynamically from the OS
     * <p>
     * This region has no children.
     */
    private static final class CardTableMemoryRegion extends TeleFixedMemoryRegion implements MaxEntityMemoryRegion<MaxCardTable> {

        private static final List<MaxEntityMemoryRegion< ? extends MaxEntity>> EMPTY = Collections.emptyList();

        private final MaxCardTable owner;

        protected CardTableMemoryRegion(MaxVM vm, MaxCardTable owner, String regionName, Address start, long nBytes) {
            super(vm, regionName, start, nBytes);
            this.owner = owner;
        }

        @Override
        public MaxEntityMemoryRegion< ? extends MaxEntity> parent() {
            // The MarkBitmap fully occupies a region allocated from the OS, not part of any other region.
            return null;
        }

        @Override
        public List<MaxEntityMemoryRegion< ? extends MaxEntity>> children() {
            return EMPTY;
        }

        @Override
        public MaxCardTable owner() {
            return owner;
        }
    }

    private final TeleCardTableRSet cardTableRSet;
    private final TeleFixedMemoryRegion coveredMemoryRegion;
    private final CardTableMemoryRegion cardTableMemoryRegion;
    private final TeleArrayObject cardTableArray;
    private final CardTableObjectReferenceManager objectReferenceManager;

    // TODO (mlvdv)  should be a TeleCardTable
    public VmCardTable(TeleVM vm, TeleCardTableRSet cardTableRSet) {
        super(vm);
        this.cardTableRSet = cardTableRSet;
        final long coveredSize = 0L; // remoteHeapMarker.coveredAreaEnd().minus(remoteHeapMarker.coveredAreaStart()).toLong();
        this.coveredMemoryRegion = null; // new TeleFixedMemoryRegion(vm, ENTITY_NAME, remoteHeapMarker.coveredAreaStart(), coveredSize);
        final Address dataRegionStart = Address.zero(); //remoteHeapMarker.colorMapDataRegion().getRegionStart();
        final long dataRegionSize = 0L; //remoteHeapMarker.colorMapDataRegion().getRegionNBytes();
        this.cardTableMemoryRegion = new CardTableMemoryRegion(vm, this, ENTITY_NAME, dataRegionStart, dataRegionSize);
        this.objectReferenceManager = new CardTableObjectReferenceManager(vm, dataRegionStart);
        this.cardTableArray = (TeleArrayObject) vm.objects().makeTeleObject(objectReferenceManager.byteArrayRef);
        this.cardTableArray.setMaxineRole(ENTITY_NAME);
    }

    public String entityName() {
        return cardTableMemoryRegion.regionName();
    }

    public String entityDescription() {
        return "The region of OS-allocated memory in which a Card Table is stored, formatted as a single long array";
    }

    public MaxEntityMemoryRegion<MaxCardTable> memoryRegion() {
        return cardTableMemoryRegion;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This the allocation holding the mark bitmap.
     */
    public boolean contains(Address address) {
        return cardTableMemoryRegion.contains(address);
    }

    public MaxObject representation() {
        return cardTableArray;
    }

    @Override
    public MaxMemoryRegion coveredMemoryRegion() {
        return coveredMemoryRegion;
    }

//    public int getBitIndexOf(Address heapAddress) {
//        return coveredMemoryRegion.contains(heapAddress) ? byteIndexOf(heapAddress) : -1;
//    }
//
//
//    public Address heapAddress(int bitIndex) {
//        return addressOf(bitIndex);
//    }
//
//    public Address bitmapWordAddress(int bitIndex) {
//        return bitmapWordPointerAt(bitIndex);
//    }
//
//    public long readBitmapWord(int bitIndex) {
//        return hostedBitmapWordAt(bitIndex);
//    }
//
//    public int getBitIndexInWord(int bitIndex) {
//        return bitIndexInWord(bitIndex);
//    }
//
//    public boolean isBitSet(int bitIndex) {
//        return isSet(bitIndex);
//    }
//
//    public void setBit(int bitIndex) {
//        TeleError.unimplemented();
//    }

//    /**
//     * {@inheritDoc}
//     * <p>
//     * Override the standard method in {@link TricolorHeapMarker} for reading a word out of the bitmap table with one
//     * that reads the memory remotely from the VM.
//     */
//    @Override
//    protected long hostedBitmapWordAt(int bitIndex) {
//        return vm().memoryIO().getLong(base, 0, bitmapWordIndex(bitIndex));
//    }
//
//    public MarkColor getMarkColor(Address heapAddress) {
//        if (vm().objects().objectStatusAt(heapAddress).isLive() && isCovered(heapAddress)) {
//            return getMarkColorUnsafe(heapAddress);
//        }
//        return null;
//    }
//
//    public MarkColor getMarkColor(int bitIndex) {
//        final Address heapAddress = addressOf(bitIndex);
//        if (vm().objects().objectStatusAt(heapAddress).isLive()) {
//            return getMarkColorUnsafe(heapAddress);
//        }
//        return null;
//    }
//
//    /**
//     * Gets the marking at a particular index of the bitmap. Does <em>not check</em> that there is an object at that
//     * location, or even whether the address is covered by the bitmap, so the mark could be at an invalid location.
//     */
//    public MarkColor getMarkColorUnsafe(Address address) {
//        final int bitIndex = bitIndexOf(address);
//        try {
//            if (isWhite(bitIndex)) {
//                if (isClear(bitIndex + 1)) {
//                    return MARK_WHITE;
//                }
//                TeleWarning.message("Invalid mark in mark bitmap @" + bitIndex);
//                return MARK_INVALID;
//            } else if (isGreyWhenNotWhite(bitIndex)) {
//                return MARK_GRAY;
//            }
//            if (isClear(bitIndex + 1)) {
//                return MARK_BLACK;
//            }
//            TeleWarning.message("Invalid mark in mark bitmap @" + bitIndex);
//            return MARK_INVALID;
//        } catch (DataIOError e) {
//            return MARK_UNAVAILABLE;
//        }
//    }
//
//    /**
//     * @return bitmap index one past the last covered heap address
//     */
//    private int endBitIndex() {
//        return bitIndexOf(coveredMemoryRegion.end());
//    }
//
//    public int nextSetBitAfter(int startBitIndex) {
//        for (int bitIndex = startBitIndex + 1; bitIndex < endBitIndex(); bitIndex++) {
//            if (isSet(bitIndex)) {
//                return bitIndex;
//            }
//        }
//        return -1;
//    }
//
//    public int previousSetBitBefore(int startBitIndex) {
//        for (int bitIndex = startBitIndex - 1; bitIndex >= 0; bitIndex--) {
//            if (isSet(bitIndex)) {
//                return bitIndex;
//            }
//        }
//        return -1;
//    }
//
//    public int nextMarkAfter(int startBitIndex, MarkColor color) {
//        switch(color) {
//            case MARK_BLACK:
//            case MARK_GRAY:
//                for (int bitIndex = startBitIndex + 1; bitIndex < endBitIndex(); bitIndex++) {
//                    // For BLACK and GRAY marks, we only have to check at locations where the first bit is set
//                    if (isSet(bitIndex) && getMarkColor(bitIndex) == color) {
//                        return bitIndex;
//                    }
//                }
//                return -1;
//            case MARK_WHITE:
//            case MARK_INVALID:
//                for (int bitIndex = startBitIndex + 1; bitIndex < endBitIndex(); bitIndex++) {
//                    if (getMarkColor(bitIndex) == color) {
//                        return bitIndex;
//                    }
//                }
//                return -1;
//            case MARK_UNAVAILABLE:
//                return -1;
//        }
//        return -1;
//    }
//
//    public int previousMarkBefore(int startBitIndex, MarkColor color) {
//        switch(color) {
//            case MARK_BLACK:
//            case MARK_GRAY:
//                for (int bitIndex = startBitIndex - 1; bitIndex >= 0; bitIndex--) {
//                    // For BLACK and GRAY marks, we only have to check at locations where the first bit is set
//                    if (isSet(bitIndex) && getMarkColor(bitIndex) == color) {
//                        return bitIndex;
//                    }
//                }
//                return -1;
//            case MARK_WHITE:
//            case MARK_INVALID:
//                for (int bitIndex = startBitIndex - 1; bitIndex >= 0; bitIndex--) {
//                    if (getMarkColor(bitIndex) == color) {
//                        return bitIndex;
//                    }
//                }
//                return -1;
//            case MARK_UNAVAILABLE:
//                return -1;
//        }
//        return -1;
//    }

    public RemoteObjectReferenceManager objectReferenceManager() {
        return objectReferenceManager;
    }

    public void printObjectSessionStats(PrintStream printStream, int indent, boolean verbose) {
        TeleWarning.unimplemented();
    }

    public boolean isCovered(Address heapAddress) {
        // TODO Auto-generated method stub
        return false;
    }

    /**
     * Manager for object references for the unmanaged card table region, which contains,
     * once initialized, a singleton long array.
     */
    private class CardTableObjectReferenceManager extends AbstractVmHolder implements RemoteObjectReferenceManager {

        private final Address byteArrayOrigin;
        private final ConstantRemoteReference byteArrayRef;

        protected CardTableObjectReferenceManager(TeleVM vm, Address start) {
            super(vm);
            byteArrayOrigin = objects().layoutScheme().generalLayout.cellToOrigin(start.asPointer());
            byteArrayRef = new ConstantRemoteReference(vm(), byteArrayOrigin) {

                @Override
                public ObjectStatus status() {
                    return ObjectStatus.LIVE;
                }

                @Override
                public ObjectStatus priorStatus() {
                    return null;
                }
            };
        }

        /**
         * {@inheritDoc}
         * <p>
         * There is no GC cycle; the singleton byte array is neither relocated nor collected.
         */
        public HeapPhase phase() {
            return HeapPhase.MUTATING;
        }

        public ObjectStatus objectStatusAt(Address origin) {
            TeleError.check(memoryRegion().contains(origin), "Location is outside region");
            return origin.equals(byteArrayOrigin) ? ObjectStatus.LIVE : ObjectStatus.DEAD;
        }

        public boolean isForwardingAddress(Address forwardingAddress) {
            return false;
        }

        /**
         * {@inheritDoc}
         * <p>
         * The only reference possible is to the header of the array that holds the whole map in the region.
         */
        public RemoteReference makeReference(Address origin) throws TeleError {
            return objectStatusAt(origin).isLive() ? byteArrayRef : null;
        }

        public RemoteReference makeQuasiReference(Address origin) {
            return null;
        }

        public void printObjectSessionStats(PrintStream printStream, int indent, boolean verbose) {
            TeleWarning.unimplemented();
        }

    }


}
