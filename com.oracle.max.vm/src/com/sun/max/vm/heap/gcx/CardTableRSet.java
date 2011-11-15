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

import com.sun.max.unsafe.*;
import com.sun.max.vm.heap.*;


public class CardTableRSet {
    /**
     * Power of 2 of a card size in bytes.
     */
    private static final int LOG2_CARD_SIZE = 9;
    /**
     * Number of bytes per card.
     */
    private static final int CARD_SIZE = 1 << LOG2_CARD_SIZE;

    private static final int INVALID_CARD_INDEX = -1;

    final Power2RegionToByteMapTable cardTable;

    public void  recordWrite(Address referenceLocation) {

    }

    public void recordWrite(Address cell, Offset offset) {

    }


    public CardTableRSet() {
        cardTable = new Power2RegionToByteMapTable(LOG2_CARD_SIZE);
    }

    public void initialized(Address coveredAreaStart, Size coveredAreaSize) {

    }

    public void iterateDirtyCards(CellVisitor visitor) {

    }

    void visitCard(int cardIndex, CellVisitor visitor) {
    }

    /**
     * Iterate over cells that overlap the specified region and comprises recorded reference locations.
     * @param start
     * @param end
     * @param cellVisitor
     */
    void iterate(Address start, Address end, CellVisitor visitor) {
        final int endOfRange = cardTable.tableEntryIndex(end);
        int cardIndex = cardTable.firstNonZero(cardTable.tableEntryIndex(start), endOfRange);
        while (cardIndex < endOfRange) {
            visitCard(cardIndex, visitor);
            cardIndex = cardTable.firstNonZero(cardIndex, endOfRange);
        }
    }
}
