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

import static com.sun.max.vm.heap.gcx.CardTable.CardState.*;

import com.sun.max.unsafe.*;
/**
 *
 *
 */
class CardTable extends  Log2RegionToByteMapTable {
    /**
     * Log2 of a card size in bytes.
     */
    static final int LOG2_CARD_SIZE = 9;
    /**
     * Number of bytes per card.
     */
    static final int CARD_SIZE = 1 << LOG2_CARD_SIZE;

    static final int NO_CARD_INDEX = -1;

    static enum CardState {
        CLEAN_CARD(0xff),
        DIRTY_CARD(0);
        byte value;
        CardState(int value) {
            this.value = (byte) value;
        }
        byte value() {
            return value;
        }
    }

    CardTable() {
        super(LOG2_CARD_SIZE);
    }

    @Override
    void initialize(Address coveredAreaStart, Size coveredAreaSize, Address storageArea) {
        super.initialize(coveredAreaStart, coveredAreaSize, storageArea);
        cleanAll();
    }

    @Override
    void initialize(Address coveredAreaStart, Size coveredAreaSize) {
        super.initialize(coveredAreaStart, coveredAreaSize);
        cleanAll();
    }

    /**
     * Set all cards of the table to the {@link CardState#CLEAN_CARD} value.
     */
    void cleanAll() {
        fill(CLEAN_CARD.value());
    }

    /**
     * Set all cards in the index range to the {@link CardState#CLEAN_CARD} value.
     * @param fromIndex index to the first card of the range (inclusive)
     * @param toIndex index to the last card of the range (exclusive)
     */
    void clean(int fromIndex, int toIndex) {
        fill(fromIndex, toIndex, CLEAN_CARD.value());
    }

    /**
     * Set all cards in the index range to the {@link CardState#DIRTY_CARD} value.
     * @param fromIndex index to the first card of the range (inclusive)
     * @param toIndex index to the last card of the range (exclusive)
     */
    void dirty(int fromIndex, int toIndex) {
        fill(fromIndex, toIndex, DIRTY_CARD.value());
    }

    /**
     * Set card at specified index to the {@link CardState#CLEAN_CARD} value.
     * @param index a card index
     */
    void clean(int index) {
        set(index, CLEAN_CARD.value());
    }

   /**
    * Set card at specified index to the {@link CardState#DIRTY_CARD} value.
    * @param index a card index
    */
    void dirty(int index) {
        set(index, DIRTY_CARD.value());
    }

    /**
     * Dirty the entry in the card table corresponding to the card of the covered heap address.
     * @param coveredAddress an address in heap covered by the card table
     */
    void dirtyCovered(Address coveredAddress) {
        unsafeSet(coveredAddress, DIRTY_CARD.value());
    }

    /**
     * Find the first card set to the specified value in the specified range of entries in the table .
     * @param start index of the first card in the range (inclusive)
     * @param end index of the last card of the range (exclusive)
     * @param cardState a card state
    * @return {@link #NO_CARD_INDEX} if all the cards in the range are clean, the index to the first dirty card otherwise.
    */
    final int first(int start, int end, CardState cardState) {
        // This may be optimized with special support from the compiler to exploit cpu-specific instruction for string ops (e.g.).
        // We may also get rid of the limit test by making the end of the range looking like a marked card.
        // e.g.:   tmp = limit.getByte(); limit.setByte(1);  loop; limit.setByte(tmp); This could be factor over multiple call of firstNonZero...
        final Pointer first = tableAddress.plus(start);
        final Pointer limit = tableAddress.plus(end);
        final byte cardValue = cardState.value;
        Pointer cursor = first;
        while (cursor.getByte() != cardValue) {
            cursor = cursor.plus(1);
            if (cursor.greaterEqual(limit)) {
                return -1;
            }
        }
        return cursor.minus(first).toInt();
    }
}
