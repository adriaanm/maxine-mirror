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

/**
 * An iterator over a {@link HeapRegionList}.
 * The iterator return ranges of contiguous regions in the order of the list.
 */
public final class HeapRegionRangeIterable extends HeapRegionListIterable {
    public HeapRegionRangeIterable() {
    }

    public RegionRange next() {
        final int firstRegion = cursor;
        int endRange = cursor + 1;
        int next = regionList.next(cursor);
        while (next == endRange) {
            endRange++;
            next = regionList.next(next);
        }
        final int numRegions = endRange - firstRegion;
        cursor = next;
        return RegionRange.from(firstRegion, numRegions);
    }

    /**
     * Reset the iterator to the first iterable region if any.
     * @see HeapRegionInfo#isIterable()
     */
    public void resetToFirstIterable() {
        reset();
        nextIterable();
    }

    private void nextIterable() {
        final RegionTable theRegionTable = RegionTable.theRegionTable();
        while (cursor != INVALID_REGION_ID) {
            if (theRegionTable.regionInfo(cursor).isIterable()) {
                return;
            }
            cursor = regionList.next(cursor);
        }
    }

    public RegionRange nextIterableRange() {
        final int firstRegion = cursor;
        int endRange = cursor + 1;
        int next = regionList.next(cursor);
        final RegionTable theRegionTable = RegionTable.theRegionTable();
        while (next == endRange) {
            if (!theRegionTable.regionInfo(next).isIterable()) {
                break;
            }
            endRange++;
            next = regionList.next(next);
        }
        final int numRegions = endRange - firstRegion;
        cursor = next;
        nextIterable();
        return RegionRange.from(firstRegion, numRegions);
    }


    /**
     * Reset the iterator to the first iterable region if any.
     * @see HeapRegionInfo#isIterable()
     */
    public void resetToFirstIterable(int tag) {
        reset();
        nextIterable(tag);
    }

    private void nextIterable(int tag) {
        final RegionTable theRegionTable = RegionTable.theRegionTable();
        while (cursor != INVALID_REGION_ID) {
            HeapRegionInfo rinfo = theRegionTable.regionInfo(cursor);
            if (rinfo.tag == tag && rinfo.isIterable()) {
                return;
            }
            cursor = regionList.next(cursor);
        }
    }

    public RegionRange nextIterableRange(int tag) {
        final int firstRegion = cursor;
        int endRange = cursor + 1;
        int next = regionList.next(cursor);
        final RegionTable theRegionTable = RegionTable.theRegionTable();
        while (next == endRange) {
            HeapRegionInfo rinfo = theRegionTable.regionInfo(next);
            if (!(rinfo.tag == tag && rinfo.isIterable())) {
                break;
            }
            endRange++;
            next = regionList.next(next);
        }
        final int numRegions = endRange - firstRegion;
        cursor = next;
        nextIterable(tag);
        return RegionRange.from(firstRegion, numRegions);
    }
}
