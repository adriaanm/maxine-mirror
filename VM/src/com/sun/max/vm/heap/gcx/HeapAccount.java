/*
 * Copyright (c) 2010, 2010, Oracle and/or its affiliates. All rights reserved.
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
import static com.sun.max.vm.heap.gcx.HeapRegionManager.*;

/**
 * Backing storage for a heap is managed via a heap account created on demand by the {@link HeapRegionManager}.
 * A heap account provides a guaranteed reserve of space, corresponding to the maximum space required
 * by the account owner. Space is expressed in terms of number of heap regions, whose size is defined in {@link HeapRegionConstants}.
 * The account owner can allocate regions on demand up to the account's reserve.
 *
 *
 * @author Laurent Daynes
 */
public class HeapAccount<Owner>{
    /**
     * Owner of the account. Typically, some heap implementation.
     */
    private Owner owner;
    /**
     * Guaranteed reserve of regions for this account.
     */
    private int reserve;

    /**
     * List of regions allocated to the account owner. All allocated regions are committed
     */
    private HeapRegionList allocated;

    HeapAccount(Owner owner, int reserve) {
        this.owner = owner;
        this.reserve = reserve;

    }

    /**
     * Number of regions in the reserve.
     * @return a number of regions.
     */
    int reserve() {
        return reserve;
    }
    /**
     * The owner of the heap account.
     * @return an object
     */
    public Owner owner() { return owner; }

    /**
     *
     * @return
     */
    public int allocate() {
        if (allocated.size() < reserve) {
            int regionID = theHeapRegionManager.regionAllocator().allocate();
            if (regionID != INVALID_REGION_ID) {
                allocated.prepend(regionID);
            }
        }
        return INVALID_REGION_ID;
    }

}
