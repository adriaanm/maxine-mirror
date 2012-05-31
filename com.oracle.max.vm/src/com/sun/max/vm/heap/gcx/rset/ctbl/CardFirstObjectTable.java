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
import static com.sun.max.vm.heap.gcx.rset.ctbl.CardTableRSet.*;

import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.MaxineVM.Phase;
import com.sun.max.vm.runtime.*;

/**
 * Table providing support for  linear scanning of arbitrary cards of a contiguous range of virtual addresses associated with a card table.
 *
 * Walking arbitrary cards requires that every contiguous range of virtual address is formatted to allow walking it linearly.
 * It also requires that the header of any object overlapping with the first word of any card
 * can be quickly retrieved (in order to figure out the layout of the object, its size, and its references locations).
 * The CardFirstObjectTable (or FOT for short) encodes per-card information allowing to find the
 * address of the object overlapping with the first word of a card.
 * Because the table only has 1 byte per entry, objects that overlaps many card will have to load multiple entries to find the amount of space
 * to remove to the start of a card to find the start of the object that overlaps with the first word of the card.
 * The value stored in an entry E of {@link CardFirstObjectTable} is either a negative number of words to add to the address of the first word of card E,
 * or an encoding of the number of cards to subtract to  E's index to obtain the index to the next  entry to read to find information about the start of the first object
 * overlapping E.
 * The number of cards to subtract is encoded as either a positive number of cards to subtract, or a power of 2 of the number of cards to subtract.
 *
 * More formally, let C be a card whose first word overlaps with an object that starts at address s, such that s points into card c0.
 * Let d be the distance in number of cards between c0 and C.
 * The value stored in the entry of {@link CardFirstObjectTable} is:
 * - the negative number of words from the end of card C to the start of the object s if d = 1,
 * - a positive number of cards n such that  0 < n < 8 if 1 < d <= 8,
 * - a value l >= 8 such that  k = 1 << (2+l), if  2^k < d < 2^(k+1).
 *
 *  For example, let assume that s points to a 270 Kb object that starts at card with index c0 and covers 540 512-bytes cards.
 *  If O[x] is the entry of {@link CardFirstObjectTable} for the card x, then
 * O[c0 + 1] = number of words from the end of c0 to s, and is a value between 0 and -127. One load to compute s.
 * O[c0 + i] / i : [2..8]  = i -1. Two loads to find s.
 * O[c0+9] = 8. Two loads to find s.
 * O[c0 + i] / i : [9..16] = 8.   Three loads to find s.
 * O[c0 + i] / i : [17..32] = 9.   Four loads to find s.
 * etc...
 * In general, this encoding allows to find the start of an object overlapping with a card C at a distance d from the card c0 in n hops,
 * where  n = k - 1 and  2^k < d <= 2^(k+1).
 *
 * A heap implementation using a card table is responsible for updating the information tracked by the FOT table.
 * If only dirty cards are required to be scanned, then only cards overlapping live objects need to be kept up to date at all time.
 * Cards that completely overlaps dead space don't need an accurate FOT entry.
 *
 * Update of the information simply requires calling {@link CardFirstObjectTable#set(Address, Size)} with the
 * address and size of an object crossing a card boundary.
 *
 */
public final class CardFirstObjectTable extends Log2RegionToByteMapTable {
    static final byte ZERO = 0;

    static boolean TraceFOT = false;
    static {
        VMOptions.addFieldOption("-XX:", "TraceFOT", CardFirstObjectTable.class, "Trace CardFirstObjectTable updates", Phase.PRISTINE);
    }

    static private final byte HIGH_BIT = (byte) 128;
    /**
     * Bias to subtract to encoded power of two stored in FOT.
     */
    static private final byte LOG2_BIAS = 5;
    /**
     * Positive values stored in entries of a FOT strictly above this value denotes a biased power of 2.
     * Positive values stored in entries of a FOT less or equal to this value denotes a number of cards.
     */
    static private final byte LOG2_ENCODING_THRESHOLD = 7;

    static private final Address CardAlignmentMask = Address.fromInt(CARD_SIZE).minus(1).not();

    CardFirstObjectTable() {
        super(LOG2_CARD_SIZE);
    }

    void initialize(Address coveredAreaStart, Size coveredAreaSize) {
        super.initialize(coveredAreaStart, coveredAreaSize, true);
        fill(ZERO);
    }

    @Override
    void initialize(Address coveredAreaStart, Size coveredAreaSize, Address storageArea) {
        super.initialize(coveredAreaStart, coveredAreaSize, storageArea);
        fill(ZERO);
    }

    /**
     * Indicate whether allocating the specify ranges requires updating the FOT.
     * @param cellStart start of the cell (inclusive)
     * @param cellEnd end of the cell (exclusive)
     * @return true if the specified range cross or start at a card boundary
     */
    static boolean needsUpdate(Address cellStart, Address cellEnd) {
        return cellEnd.minus(1).and(Address.fromInt(CARD_SIZE).minus(1).not()).greaterEqual(cellStart);
    }

    private void setOffset(int index, byte numWords) {
        set(index, (byte) (-numWords));
    }
    /**
     * Set FOT entries corresponding to the cards overlapped by the specified cell.
     * If the object fit within a single card and doesn't overlap with its first word, no update take place.
     * This method must be used only to
     *
     * @param cell address of an object
     * @param size size of the object
     */
    public void set(Address cell, Size size) {
        set(cell, cell.plus(size));
    }

    /**
     * Set entries of the FOT for the cell whose boundary is specified in parameters.
     * This assumes that subsequent slots in the slots aren't initialized.
     *
     * Note: this must be used carefully as it does not update the subsequent entries of the FOT, which may make these entries
     * invalid and the corresponding cards non-iterable. The subsequent cards become invalid if the new cell overwrite a
     * larger previous cell (e.g., typically, a formatted heap free chunk).
     *
     * @param cell
     * @param cellEnd
     */
    public void set(Address cell, Address cellEnd) {
        // ADD CHECK TO VERIFY LIMIT ON SIZE.
        int firstCard = tableEntryIndex(cell);
        if (atBoundary(cell)) {
            set(firstCard, (byte) 0);
        }
        int lastCard = tableEntryIndex(cellEnd.minus(Word.size()));
        // This is the number of cards, excluding the cards where the cell start.
        final int numCards = lastCard  - firstCard;
        if (numCards == 0) {
            return;
        }
        // FOT entry next to that for the card that contains the start of the cell always encode a negative offset.
        final int offsetCard = firstCard + 1;
        final byte numWords = (byte) rangeStart(offsetCard).minus(cell).unsignedShiftedRight(Word.widthValue().log2numberOfBytes).toInt();
        if (MaxineVM.isDebug() && TraceFOT) {
            Log.print("setting FOT for card ");  Log.print(offsetCard);
            Log.print(" = ");
            Log.print(" #words = ");
            Log.println(numWords);
        }
        setOffset(offsetCard, numWords);
        int remainingCards = numCards - 1;
        if (remainingCards == 0) {
            return;
        }
        // Subsequent LOG2_ENCODING_THRESHOLD entries encode a distance (in number of cards) to the cards holding the offset information.
        int numNonLogEncodedCards = remainingCards >= LOG2_ENCODING_THRESHOLD ? LOG2_ENCODING_THRESHOLD : remainingCards;
        for (byte i = 1; i <= numNonLogEncodedCards; i++) {
            set(offsetCard + i, i);
        }
        int nextCard = offsetCard + numNonLogEncodedCards + 1;

        if (MaxineVM.isDebug() && TraceFOT) {
            Log.print("setting FOT for range [");  Log.print(cell);
            Log.print(", ");  Log.print(cellEnd);
            Log.print("#cards = ");  Log.print(numCards);
            Log.print("] non-log encoding cards = ");  Log.print(numNonLogEncodedCards);
            Log.print(" last card = "); Log.print(lastCard);
            Log.print(" next card = "); Log.println(nextCard);
        }

        // All subsequent entries, if any, encode the distance to a previous entry as a biased power of two.
        int log2 = 3;
        while (nextCard <= lastCard) {
            // get the range of FOT entries that will store  the encoded current log2.
            int endOfCardRange  = 1 << (log2 + 1);
            int c = offsetCard + (numCards > endOfCardRange ? endOfCardRange : numCards);
            byte biasedLog2 = (byte) (log2 + LOG2_BIAS);
            while (nextCard < c) {
                set(nextCard++, biasedLog2);
            }
            log2++;
        }
    }

    public boolean isFirstCellOfCard(Address cell) {
        int card = tableEntryIndex(cell);
        if (alignDownToCard(cell) == cell) {
            FatalError.check(get(card) == 0, "FOT entry must be null when first cell of card starts at card boundary");
            return true;
        }
        byte offsetToCell = (byte) -alignUpToCard(cell).minus(cell).unsignedShiftedRight(Word.widthValue().log2numberOfBytes).toInt();
        return get(card + 1) == offsetToCell;
    }

    /**
     * Update the FOT to take into account the split of a cell in two. i.e., a cell [a,b] is split into two cells
     * [a, c] and [c,b].
     *
     * @param leftCell start of the cell being split
     * @param rightCell start of the new cell being split off from the first
     * @param end end of the original cell
     */
    public void split(Address leftCell, Address rightCell, Address end) {
        if (leftCell.greaterThan(alignDownToCard(end))) {
            // No update needed. Both side of the split are on the same card.
            return;
        }
        if (MaxineVM.isDebug()) {
            FatalError.check(isFirstCellOfCard(leftCell), "Cell isn't current first cell of card");
        }
        int offsetCard = tableEntryIndex(leftCell) + 1;
        Address nextCardAddress = alignUpToCard(leftCell);
        if (rightCell.lessThan(nextCardAddress)) {
            byte offsetToNewCell = (byte) nextCardAddress.minus(rightCell).unsignedShiftedRight(Word.widthValue().log2numberOfBytes).toInt();
            setOffset(offsetCard, offsetToNewCell);
            // No need to update any subsequent FOT entries, they remain valid.
        } else if (rightCell.greaterThan(nextCardAddress)) {
            set(rightCell, end);
        } else {
            // Split at card boundary. No need to update any subsequent FOT entries, they remain valid.
            set(offsetCard, (byte) 0);
        }
    }

    /**
     * Return the address of the cell that overlaps the first word of the card specified by the card index.
     * @param cardIndex the index of a card
     * @return an address in the contiguous range of virtual memory covered by the card table associated with this FOT.
     */
    public Address cellStart(int cardIndex) {
        int nextCardIndex = cardIndex;
        byte startInfo = get(nextCardIndex);
        if (startInfo > ZERO) {
            while (startInfo > LOG2_ENCODING_THRESHOLD) {
                int distanceToNextCard = 1 << (startInfo - LOG2_BIAS);
                nextCardIndex -= distanceToNextCard;
                startInfo = get(nextCardIndex);
            }
            if (startInfo > ZERO) {
                nextCardIndex -= startInfo;
                startInfo = get(nextCardIndex);
            }
        }
        if (MaxineVM.isDebug() && startInfo > 0) {
            Log.print("Incorrect format of FOT for card #");
            Log.print(cardIndex);
            Log.print(" :  card #");
            Log.print(nextCardIndex);
            Log.print(" has negative word index ");
            Log.println(startInfo);
            FatalError.unexpected("word index encoded in FOT entries must be negative");
        }
        int offset = startInfo << Word.widthValue().log2numberOfBytes;
        return rangeStart(nextCardIndex).plus(offset);
    }

    public void clear(int fromIndex, int toIndex) {
        fill(fromIndex, toIndex, ZERO);
    }

    public void verify(Address start, Address end) {
        // iterate over all entry and verify the invariant on the first object.
        int firstCard = tableEntryIndex(start);
        int lastCard = tableEntryIndex(end);
        if (MaxineVM.isDebug() && TraceFOT) {
            Log.println("Verify FOT for cards [");
            Log.print(firstCard);
            Log.print(", ");
            Log.print(lastCard);
            Log.println("]");
        }
        for (int card = firstCard; card < lastCard; card++) {
            Address cardStart = rangeStart(card);
            Address firstObjectStart = cellStart(card);
            if (firstObjectStart.greaterThan(cardStart)) {
                Log.print("Incorrect FOT entry for card ");
                Log.print(card);
                Log.print(" card start = ");
                Log.print(cardStart);
                Log.print(" first object = ");
                Log.println(firstCard);
                FatalError.unexpected("First object must be before or at the card start");
            }
        }
    }

}
