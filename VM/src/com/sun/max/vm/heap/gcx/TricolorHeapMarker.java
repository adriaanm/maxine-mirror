/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.max.vm.heap.gcx;

import com.sun.max.annotate.*;
import com.sun.max.memory.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.code.*;
import com.sun.max.vm.grip.*;
import com.sun.max.vm.heap.*;
import com.sun.max.vm.layout.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.runtime.*;

/**
 * A marking algorithm that uses a three-color mark-bitmap with a fixed-size tiny marking stack, a rescan map, and a
 * finger. The tricolor mark-bitmap encodes three colors using two consecutive bits but consumes as much space overhead as
 * a single-bit mark bitmap, thanks to padding rare tiny objects to guarantee two color bits for every objects.
 * Tracing algorithm uses a single-bit mark bitmap and a fairly large marking stack (from several thousands of references, up
 * hundreds of thousands of references). The reason for the large marking stack is that if this one overflows, tracing must
 * rescan the heap starting from leftmost reference that was on the stack, and must visit every marked object from that point,
 * thus revisiting many objects. The cost of rescan is so overwhelming that a very large marking stack is used to avoid
 * this possibility. The reason for the blind rescan is that with a single bit, one cannot distinguish visited (black) objects from
 * unvisited but live (grey) objects.
 *
 * Every bit maps to a fixed chunk of heap such that every object's first words coincide with one fixed chunk.
 *  Almost all objects have a size larger than the size of a single chunk covered by one bit. Those that don't
 *  (called tiny objects) are segregated or padded (i.e., the heap allocate them the required space cover 2 bits of the mark bitmap).
 *  Maxine currently aligns objects on a 8-byte word boundary, uses 8-bytes words, and uses a two-words header.
 *
 * The following choices are considered:
 * - each bit corresponds to a single word of the heap; Every object is thus guaranteed two-bit; the mark bitmap consumes 16 Kb
 * per Mb of heap.
 * - each bit corresponds to two Words of the heap; Every object larger that 3 words occupies 2 chunks. With this design,
 * the smallest objects can only be allocated 1 bit. Since such objects are generally rare they can be treated
 * specially: e.g., padded to be associated with two bits, or segregated to be allocated in an area covered by a one bit
 * bitmap. Padding is simpler as it allows a unique bitmaps. Maxine's current 8-byte alignment raises another problem
 * with this approach: a chunk can be shared by two objects. This complicates finding the "origin" of an object. The solution
 * is to require objects to be chunk-aligned (i.e., 16-byte aligned) potentially wasting heap space. This would make the
 * mark bitmap consumes 8 Kb / Mb of heap.
 *
 * Which solution is best depends on the amount of space wasted by the 2-words alignment requirement, compared to bitmap
 * space saved. A larger grain also means less time to scan the bitmap. We leave this choice to the heap implementation
 * / collector, which is responsible for aligning object and dealing with small object. For simplicity, we begin here
 * with the first alternative (1 bit per word).
 *
 * Finally, note that in both cases, the first bit of a color may be either an odd or an even bit, and a color may span
 * two bitmaps words. This complicates color search/update operation. A heap allocator may arrange for guaranteeing that
 * an object marks never span a bitmap word by padding a dead object before (Dead objects are special instance of object
 * whose size is strictly 2 words, regardless of other rules for dealing with tiny objects).
 *
 * An other alternative is to exploit location of an object with respect to the current cursor on the mark bitmap:
 * since object located after the cursor aren't visited yet, we can use the black mark for marking these grey
 * (i.e., an object with the black mark set is black only located before the finger). In this case, the grey mark is really only used
 * on overflow of the mark stack.
 *
 * This class enables both designs, and provides generic bitmap manipulation that understands color coding and
 * color-oriented operations (i.e., searching grey or black mark, etc.). It provides fast and slow variant of
 * operations, wherein the fast variant assumes that a color never span a bitmap word. The GC is responsible for
 * guaranteeing this property when it uses the fast variant.
 *
 * @author Laurent Daynes
 */
public class TricolorHeapMarker implements MarkingStack.OverflowHandler {

    // The color encoding is chosen to optimize the mark bitmaps.
    // The tracing algorithm primarily search for grey objects, 64-bits at a time.
    // By encoding black as 10 and grey as 11, a fast and common way to
    // determine if a 64-bit words holds any grey objects is to compare w & (w <<1).
    // A bit pattern with only black and white objects will result in a 0, whereas a
    // bit pattern with at least one grey object will yield a non zero result, with the
    // leading bit indicating the position of a first grey object.

    // The encoding below follows the following convention:
    // [Heap position] [bitmap word] [bit index in bitmap word].
    // Word 0  0 0
    // Word 1  0 1
    // Word 63 0 63
    // Word 64 1 0
    // Word 65 1 1
    // ...
    // This means that the leading bit of a color is always at a lower position in a bitmap word.
    // Iterating over a word of the bitmap goes from low order bit to high order bit.
    // A mark in the color map is always identified by the bit index of the leading bit.

    /**
     * 2-bit mark for white object (00).
     */
    static final long WHITE = 0L;
    /**
     * 2-bit mark for black object (01).
     */
    static final long BLACK = 1L;
    /**
     * 2-bit mark for grey objects (11).
     */
    static final long GREY = 3L;
    /**
     * Invalid 2-bit mark pattern (10).
     */
    static final long INVALID = 2L;

    static final long COLOR_MASK = 3L;

    static final int LAST_BIT_INDEX_IN_WORD = Word.width() - 1;

    static final int bitIndexInWordMask = LAST_BIT_INDEX_IN_WORD;

    /**
     * Return the index within a word of the bit index.
     *
     * @param bitIndex
     * @return
     */
    @INLINE
    static final int bitIndexInWord(int bitIndex) {
        return bitIndex & bitIndexInWordMask;
    }

    @INLINE
    static final long bitmaskFor(int bitIndex) {
        return 1L << bitIndexInWord(bitIndex);
    }

    static final void printVisitedCell(Address cell) {
        final boolean lockDisabledSafepoints = Log.lock();
        Log.print("Visiting cell ");
        Log.println(cell);
        Log.unlock(lockDisabledSafepoints);
    }

    /**
     * Number of bytes covered by each bit of the bitmaps. Must be a power of 2 of a number of words.
     */
    final int wordsCoveredPerBit;

    /**
     * Log 2 of the number of bytes covered by a bit. Used to compute bit index in the color map from address.
     */
    final int log2BytesCoveredPerBit;
    /**
     * Log 2 to get bitmap word index from an address.
     */
    final int log2BitmapWord;

    /**
     * Start of the heap area covered by the mark bitmap.
     */
    Address coveredAreaStart;

    /**
     * End of the heap area covered by the mark bitmap.
     */
    Address coveredAreaEnd;

    /**
     * Finger that points to the rightmost visited (black) object.
     */
    private Pointer finger;

    /**
     * Leftmost marked position.
     */
    private Pointer leftmost;

    /**
     * Rightmost marked position.
     */
    private Pointer rightmost;

    /**
     * Indicates whether we're recovering from a marking stack overflow
     * (i.e., a scan of the marking stack in recovery mode is initiated).
     */
    boolean recovering = false;
    /**
     * Start of the current scan of the color map to recover from a marking stack overflow.
     */
    Address startOfCurrentOverflowScan;

    /**
     * Start of the next scan of the color map to recover from secondary marking stack overflow.
     */
    Address startOfNextOverflowScan;


    @INLINE
    public final boolean isCovered(Address address) {
        return address.greaterEqual(coveredAreaStart) && address.lessThan(coveredAreaEnd);
    }

    /**
     * Base of the bitmap biased with offset to the first word's bit. For fast-computing of heap word's color index in
     * the color map.
     */
    Address biasedBitmapBase;

    /**
     * Bias to the first word of the heap.
     */
    int baseBias;
    /**
     * Memory where the color map is stored.
     */
    final RuntimeMemoryRegion colorMap;

    /**
     * Shortcut to colorMap.start() for fast bitmap operation.
     */
    private Address base;

    /**
     * The marking stack.
     */
    private final MarkingStack markingStack;

    /**
     * Return the size in bytes required for a tricolor mark bitmap to cover a contiguous
     * heap area of the specified size.
     *
     * @param coveredAreaSize
     * @return the size a three color mark bitmaps should have to cover the specified area size.
     */
    Size bitmapSize(Size coveredAreaSize) {
        return coveredAreaSize.dividedBy(wordsCoveredPerBit * Word.widthValue().numberOfBits);
    }


    /**
     * Returns max amount of memory needed for a max heap size.
     * Let the HeapScheme decide where to allocate.
     *
     * @param maxHeapSize
     * @return
     */
    public Size memoryRequirement(Size maxHeapSize) {
        return bitmapSize(maxHeapSize);
    }

    /**
     *
     */
    public TricolorHeapMarker(int wordsCoveredPerBit) {
        this.wordsCoveredPerBit = wordsCoveredPerBit;
        log2BytesCoveredPerBit = Word.widthValue().log2numberOfBytes + Integer.numberOfTrailingZeros(wordsCoveredPerBit);
        assert wordsCoveredPerBit * Word.widthValue().numberOfBytes == 1 << log2BytesCoveredPerBit;
        log2BitmapWord = log2BytesCoveredPerBit + Word.widthValue().log2numberOfBits;
        colorMap = new RuntimeMemoryRegion("Mark Bitmap");
        markingStack = new MarkingStack();
        markingStack.setOverflowHandler(this);
    }

    /**
     * Initialize a three color mark bitmap for the covered area. The mark-bitmap is generated at VM startup.
     *
     * @param bitmapStorage
     * @param coveredArea
     */
    public void initialize(RuntimeMemoryRegion coveredArea, Address bitmapStorage, Size bitmapSize) {
        if (MaxineVM.isDebug()) {
            FatalError.check(bitmapSize.toLong() >= bitmapSize(coveredArea.size()).toLong(), "Mark bitmap too small to cover heap");
        }
        coveredAreaStart = coveredArea.start();
        coveredAreaEnd = coveredArea.end();

        colorMap.setStart(bitmapStorage);
        colorMap.setSize(bitmapSize);
        base = bitmapStorage;
        baseBias = coveredArea.start().unsignedShiftedRight(log2BitmapWord).toInt();
        biasedBitmapBase = colorMap.start().minus(baseBias);
        markingStack.initialize();

        clear();
    }

    // Address to bitmap word / bit index operations.

    @INLINE
    final boolean colorSpanWords(int bitIndex) {
        return bitIndexInWord(bitIndex) == LAST_BIT_INDEX_IN_WORD;
    }

    // Pointer to the word in the bitmap containing the bit for the specified address.
    @INLINE
    final Pointer bitmapWordPointerOf(Address addr) {
        return biasedBitmapBase.asPointer().plus(addr.unsignedShiftedRight(log2BitmapWord));
    }

    /**
     * Index in the color map to the word containing the bit at the specified index.
     * @param bitIndex a bit index.
     * @return an index to a word of the color map.
     */
    @INLINE
    final int bitmapWordIndex(int bitIndex) {
        return bitIndex >> Word.widthValue().log2numberOfBits;
    }

    @INLINE
    final int bitmapWordIndex(Pointer pointer) {
        return pointer.minus(coveredAreaStart).unsignedShiftedRight(log2BitmapWord).toInt();
    }

    @INLINE
    final long bitmapWordAt(int bitIndex) {
        return base.asPointer().getLong(bitmapWordIndex(bitIndex));
    }

    /**
     *  Pointer to the bitmap word in the color map containing the bit at specified bit index.
     * @param bitIndex a bit index
     * @return a pointer to a word of the color map
  */
    @INLINE
    final Pointer bitmapWordPointerAt(int bitIndex) {
        return base.asPointer().plus(bitmapWordIndex(bitIndex) << Word.widthValue().log2numberOfBytes);
    }

    /**
     *  Bit index in the bitmap for the address into the covered area.
     */
    @INLINE
    final int bitIndexOf(Address address) {
        return address.minus(coveredAreaStart).unsignedShiftedRight(log2BytesCoveredPerBit).toInt();
    }

    @INLINE
    final Address addressOf(int bitIndex) {
        return coveredAreaStart.plus(bitIndex << log2BytesCoveredPerBit);
    }

    @INLINE
    final void markGrey_(int bitIndex) {
        FatalError.check(!colorSpanWords(bitIndex), "Color must not cross word boundary.");
        final int wordIndex = bitmapWordIndex(bitIndex);
        final Pointer basePointer = base.asPointer();
        basePointer.setLong(wordIndex, basePointer.getLong(wordIndex) | (GREY << bitIndexInWord(bitIndex)));
    }

    @INLINE
    final void markGrey_(Address cell) {
        markGrey_(bitIndexOf(cell));
    }


    @INLINE
    final void markGrey(int bitIndex) {
        if (!colorSpanWords(bitIndex)) {
            markGrey_(bitIndex);
        } else {
            // Color span words.
            final Pointer basePointer = base.asPointer();
            int wordIndex = bitmapWordIndex(bitIndex);
            basePointer.setLong(wordIndex, basePointer.getLong(wordIndex) | bitmaskFor(bitIndex));
            wordIndex++;
            basePointer.setLong(wordIndex,  basePointer.getLong(wordIndex) | 1L);
        }
    }

    @INLINE
    final void markGrey(Address cell) {
        markGrey(bitIndexOf(cell));
    }

    @INLINE
    final void markBlackFromWhite(int bitIndex) {
        final int wordIndex = bitmapWordIndex(bitIndex);
        final Pointer basePointer = base.asPointer();
        basePointer.setLong(wordIndex, basePointer.getLong(wordIndex) | (BLACK << bitIndexInWord(bitIndex)));
    }

    @INLINE
    final void markBlackFromWhite(Address cell) {
        markBlackFromWhite(bitIndexOf(cell));
    }

    @INLINE
    final boolean markGreyIfWhite_(Pointer cell) {
        final int bitIndex = bitIndexOf(cell);
        if (isWhite(bitIndex)) {
            markGrey_(bitIndex);
            return true;
        }
        return false;
    }

    final boolean markGreyIfWhite(Pointer cell) {
        final int bitIndex = bitIndexOf(cell);
        if (isWhite(bitIndex)) {
            markGrey(bitIndex);
            return true;
        }
        return false;
    }

    @INLINE
    final boolean markBlackIfWhite(Pointer cell) {
        return markBlackIfWhite(bitIndexOf(cell));
    }

    /**
     * Mark cell corresponding to this bit index black if white.
     * @param bitIndex bit index corresponding to the address of a cell in the heap covered area.
     * @return true if the mark was white.
     */
    @INLINE
    final boolean markBlackIfWhite(int bitIndex) {
        final Pointer basePointer = base.asPointer();
        final int wordIndex = bitmapWordIndex(bitIndex);
        final long bitmask = bitmaskFor(bitIndex);
        final long bitmapWord = basePointer.getLong(wordIndex);
        if ((bitmapWord & bitmask) == 0) {
            // Is white. Mark black.
            basePointer.setLong(wordIndex, bitmapWord | bitmask);
            return true;
        }
        return false;
    }

    @INLINE
    final void markBlackFromGrey(int bitIndex) {
        final Pointer basePointer = base.asPointer();
        // Only need to clear the second bit. No need to worry about the color crossing a word boundary here.
        final int greyBitIndex = bitIndex + 1;
        final int wordIndex = bitmapWordIndex(greyBitIndex);
        basePointer.setLong(wordIndex, basePointer.getLong(wordIndex) & ~bitmaskFor(greyBitIndex));
    }

    @INLINE
    final void markBlackFromGrey(Address cell) {
        markBlackFromGrey(bitIndexOf(cell));
    }


    /**
     * Set a black mark not assuming previous mark in place.
     *
     * @param bitIndex an index in the color map corresponding to the first word of an object in the heap.
     */
    @INLINE
    final void markBlack_(int bitIndex) {
        FatalError.check(!colorSpanWords(bitIndex), "Color must not cross word boundary.");
        final int wordIndex = bitmapWordIndex(bitIndex);
        final Pointer basePointer = base.asPointer();
        final long blackBit = bitmaskFor(bitIndex);
        // Clear grey bit and set black one.
        long bitmapWord = basePointer.getLong(wordIndex);
        // Clear grey bit and set black one.
        bitmapWord |= blackBit;
        bitmapWord &= ~(blackBit << 1);
        basePointer.setLong(wordIndex, bitmapWord);
    }

    @INLINE
    final boolean isColor_(int bitIndex, long color) {
        FatalError.check(!colorSpanWords(bitIndex), "Color must not cross word boundary.");
        final long bitmapWord = bitmapWordAt(bitIndex);
        return ((bitmapWord >>> bitIndexInWord(bitIndex)) & COLOR_MASK) == color;
    }

    final boolean isGrey(int bitIndex) {
        int bitIndexInWord = bitIndexInWord(bitIndex);
        if (bitIndexInWord == LAST_BIT_INDEX_IN_WORD) {
            return (bitmapWordAt(bitIndex + 1) & 1L) != 0;
        }
        return (bitmapWordAt(bitIndex) & bitmaskFor(bitIndexInWord + 1)) != 0;
    }

    /**
     * Check the color map for a white object. Thanks to the color encoding, it only needs to
     * check the lowest bit of the two-bit color, i.e., the bit corresponding to the cell address. As a result, no special care is needed
     * if the object's color spans two words of the color map.
     *
     * @param bitIndex an index in the color map corresponding to the first word of an object in the heap.
     * @return true if the object is white.
     */
    @INLINE
    final boolean isWhite(int bitIndex) {
        return (bitmapWordAt(bitIndex) & bitmaskFor(bitIndex)) == 0;
    }

    @INLINE
    final boolean isWhite(Pointer cell) {
        return isWhite(bitIndexOf(cell));
    }

    @INLINE
    final boolean isBlack_(int bitIndex) {
        return isColor_(bitIndex, BLACK);
    }

    @INLINE
    final boolean isGrey_(int bitIndex) {
        return isColor_(bitIndex, GREY);
    }

    @INLINE
    final boolean isBlack_(Pointer cell) {
        return isBlack_(bitIndexOf(cell));
    }

    @INLINE
    final boolean isGrey_(Pointer cell) {
        return isGrey_(bitIndexOf(cell));
    }

    @INLINE
    final boolean isBlack_(Object o) {
        return isBlack_(Reference.fromJava(o));
    }

    @INLINE
    final boolean isGrey_(Object o) {
        return isGrey_(Reference.fromJava(o));
    }

    /**
     * Clear the color map, i.e., turn all bits to white.
     */
    void clear() {
        Memory.clearWords(colorMap.start().asPointer(), colorMap.size().toInt() >> Word.widthValue().log2numberOfBytes);
    }

    /**
     * Marking cell referenced from outside of the covered area.
     * If the cell is itself outside of the covered area, nothing is done.
     *
     * @param cell a pointer read from an external root (may be zero).
     */
    @INLINE
    public final void markExternalRoot(Pointer cell) {
        // Note: the first test also acts as a null pointer filter.
        if (cell.greaterEqual(coveredAreaStart)) {
            markGrey(cell);
            if (cell.lessThan(leftmost)) {
                leftmost = cell;
            } else if (cell.greaterThan(rightmost)) {
                rightmost = cell;
            }
        }
    }

    /**
     * Marking of strong roots outside of the covered area.
     *
     * Implements cell visitor and pointer index visitor.
     * Cell visitor must be used only for region outside of the heap areas covered by the mark
     * bitmap. This currently includes the boot region, the code region and the immortal heap.
     * Pointer visitor should be used only for references from outside of the covered area,
     * i.e., reference from the boot region and external roots (thread stack, live monitors).
     *
     * We haven't started visiting the mark bitmap, so we don't have any black marks.
     * Thus, we don't bother with first testing if a reference is white to mark it grey (it cannot be),
     * or to test it against the finger to decide whether to mark it grey or push it on the marking stack.
     * We can just blindingly mark grey any references to the covered area,
     * and update the leftmost and rightmost marked positions.
     */
    class RootCellVisitor extends PointerIndexVisitor implements CellVisitor {
        @Override
        public void visit(Pointer pointer, int wordIndex) {
            markExternalRoot(Layout.originToCell(pointer.getGrip(wordIndex).toOrigin()));
        }

        /**
         * Visits a cell in the boot region. No need to mark the cell, it is outside of the covered area.
         *
         * @param cell a cell in 'boot region'
         */
        @Override
        public Pointer visitCell(Pointer cell) {
            if (Heap.traceRootScanning()) {
                printVisitedCell(cell);
            }
            final Pointer origin = Layout.cellToOrigin(cell);
            final Grip hubGrip = Layout.readHubGrip(origin);
            markExternalRoot(Layout.originToCell(hubGrip.toOrigin()));
            final Hub hub = UnsafeCast.asHub(hubGrip.toJava());

            // Update the other references in the object
            final SpecificLayout specificLayout = hub.specificLayout;
            if (specificLayout.isTupleLayout()) {
                TupleReferenceMap.visitReferences(hub, origin, this);
                if (hub.isSpecialReference) {
                    SpecialReferenceManager.discoverSpecialReference(Grip.fromOrigin(origin));
                }
                return cell.plus(hub.tupleSize);
            }
            if (specificLayout.isHybridLayout()) {
                TupleReferenceMap.visitReferences(hub, origin, this);
            } else if (specificLayout.isReferenceArrayLayout()) {
                final int length = Layout.readArrayLength(origin);
                for (int index = 0; index < length; index++) {
                    markExternalRoot(Layout.originToCell(Layout.getGrip(origin, index).toOrigin()));
                }
            }
            return cell.plus(Layout.size(origin));
        }

    }
    private final RootCellVisitor rootCellVisitor = new RootCellVisitor();

    /**
     * Mark the object at the specified address grey.
     *
     * @param cell
     */
    @INLINE
    private void markObjectGrey(Pointer cell) {
        if (cell.greaterThan(finger)) {
            // Object is after the finger. Mark grey and update rightmost if white.
            if (markGreyIfWhite(cell) && cell.greaterThan(rightmost)) {
                rightmost = cell;
            }
        } else if (cell.greaterEqual(coveredAreaStart) && markGreyIfWhite(cell)) {
            markingStack.push(cell);
        }
    }

    @INLINE
    final void markGripGrey(Grip grip) {
        markObjectGrey(Layout.originToCell(grip.toOrigin()));
    }

    final PointerIndexVisitor markGreyPointerIndexVisitor = new PointerIndexVisitor() {
        @Override
        public  final void visit(Pointer pointer, int wordIndex) {
            markGripGrey(pointer.getGrip(wordIndex));
        }
    };

    Pointer visitGreyCell(Pointer cell) {
        if (Heap.traceGC()) {
            printVisitedCell(cell);
        }
        final Pointer origin = Layout.cellToOrigin(cell);
        final Grip hubGrip = Layout.readHubGrip(origin);
        markGripGrey(hubGrip);
        final Hub hub = UnsafeCast.asHub(hubGrip.toJava());

        // Update the other references in the object
        final SpecificLayout specificLayout = hub.specificLayout;
        if (specificLayout.isTupleLayout()) {
            TupleReferenceMap.visitReferences(hub, origin, markGreyPointerIndexVisitor);
            if (hub.isSpecialReference) {
                SpecialReferenceManager.discoverSpecialReference(Grip.fromOrigin(origin));
            }
            return cell.plus(hub.tupleSize);
        }
        if (specificLayout.isHybridLayout()) {
            TupleReferenceMap.visitReferences(hub, origin, markGreyPointerIndexVisitor);
        } else if (specificLayout.isReferenceArrayLayout()) {
            final int length = Layout.readArrayLength(origin);
            for (int index = 0; index < length; index++) {
                markGripGrey(Layout.getGrip(origin, index));
            }
        }
        return cell.plus(Layout.size(origin));
    }

    /**
     * Set the finger to the discovered grey cell and visit its references.
     *
     * White cells after the finger are marked grey. Those before the finger are
     * pushed on the marking stack, but aren't marked. They will be popped after
     * the marking stack reaches a drain threshold.
     * A cell pointed by the finger must have a grey mark.
     *
     * Reference outside of the covered area are ignored (they've been treated
     * as root already). By arranging for all "root" heap areas to be located
     * before the covered area in the virtual address space, we can avoid a
     * boundary check: cell after the finger are guaranteed to be in the covered area;
     * Cells before the finger must be checked against the leftmost position.
     */
    final Pointer setAndVisitGreyFinger(Pointer cell) {
        finger = cell;
        final Pointer endOfCell = visitGreyCell(cell);
        markBlackFromGrey(cell);
        return endOfCell;
    }

    @INLINE
    final void markAndVisitPoppedCell(Pointer cell) {
        visitGreyCell(cell);
        markBlackFromGrey(cell);
    }

    final Pointer markAndVisitGreyCell(Pointer cell) {
        final Pointer endOfCell = visitGreyCell(cell);
        markBlackFromGrey(cell);
        return endOfCell;
    }

    final class MarkingStackCellVisitor extends MarkingStack.MarkingStackCellVisitor {
        Pointer leftmostFlushed;
        void resetLeftmostFlushed() {
            leftmostFlushed = finger;
        }

        @Override
        void visitFlushedCell(Pointer cell) {
            // Record leftmost mark.
            // Note: references on the marking stack were already
            // marked grey to avoid storing multiple times the same reference.
            if (cell.lessThan(leftmostFlushed)) {
                leftmostFlushed = cell;
            }
        }

        @Override
        void visitPoppedCell(Pointer cell) {
            TricolorHeapMarker.this.markAndVisitPoppedCell(cell);
        }
    }

    MarkingStackCellVisitor markStackCellVisitor = new MarkingStackCellVisitor();

    abstract class ColorMapScanState {
        abstract  int rightmostBitmapWordIndex();
        abstract Pointer markAndVisitCell(Pointer cell);
    }

    final class ForwardScanState extends ColorMapScanState {
        @INLINE
        @Override
        public int rightmostBitmapWordIndex() {
            Address endOfRightmost = rightmost.plus(Layout.size(Layout.cellToOrigin(rightmost)));
            return bitmapWordIndex(bitIndexOf(endOfRightmost));
        }

        @Override
        Pointer markAndVisitCell(Pointer cell) {
            return TricolorHeapMarker.this.setAndVisitGreyFinger(cell);
        }

    }

    final class OverflowScanState extends ColorMapScanState {
        private int fingerBitmapWordIndex;
        private Address overflowFinger = Address.zero();

        void updateFinger() {
            fingerBitmapWordIndex = bitmapWordIndex(bitIndexOf(finger));
        }

        @INLINE
        Address visitedCell() {
            return overflowFinger;
        }

        @INLINE
        @Override
        public int rightmostBitmapWordIndex() {
            return fingerBitmapWordIndex;
        }

        @Override
        Pointer markAndVisitCell(Pointer cell) {
            overflowFinger = cell;
            return  TricolorHeapMarker.this.markAndVisitGreyCell(cell);
        }

    }

    private final ForwardScanState forwardScanState = new ForwardScanState();
    private final OverflowScanState overflowScanState = new OverflowScanState();

    /**
     * Scanning of strong roots external to the heap and boot region (namely, thread stacks and live monitors).
     */
    private final SequentialHeapRootsScanner heapRootsScanner = new SequentialHeapRootsScanner(rootCellVisitor);

    void markBootHeap() {
        Heap.bootHeapRegion.visitReferences(rootCellVisitor);
    }


    void markCode() {
        // References in the boot code region are immutable and only ever refer
        // to objects in the boot heap region.
        boolean includeBootCode = false;
        Code.visitCells(rootCellVisitor, includeBootCode);
    }

    void markImmortalHeap() {
        ImmortalHeap.visitCells(rootCellVisitor);
    }

    /**
     * Marking roots.
     * The boot region needs special treatment. Currently, the image generator generates a map of references in the boot
     * image, and all of its object are considered as root. This leaves the following options: 1. Walk once over the
     * boot image on first GC to mark permanently all boot objects black. However, the boot image generator needs to
     * follow the padding rules of the GC (which may be constraining if we want to be able to select another GC with
     * different padding rules at startup time. (Due to the encoding of colors, we cannot just mark bit to 1). 2.
     * Perform boundary check to ignore references to the boot region. In this case, we don't need to cover the boot
     * image with the mark bitmap. 3. Same as 2, but we cover the boot region with the mark bitmap, and we mark any boot
     * object we come across as black, and leave the mark permanently (i.e., only the heap space get's cleared at GC
     * start).
     *
     * We implement option 2 for now.
     *
     * The algorithm mark roots grey they linearly scan the mark bitmap looking for grey mark. A finger indicates the
     * current position on the heap: the goal is to limit random access when following lives objects. To this end,
     * references to white objects from the "fingered" object after the finger are marked grey and will be inspected
     * when the finger comes across them. White objects before the finger are entered into a tiny marking stack, which
     * is drained whenever reaching a drain threshold.
     */
    public void markRoots() {
        leftmost = coveredAreaEnd.asPointer();
        rightmost = coveredAreaStart.asPointer();

        // Mark all out of heap roots first (i.e., thread).
        // This only needs setting grey marks blindly (there are no black mark at this stage).
        heapRootsScanner.run();
        // Next, mark all reachable from the boot area.
        markBootHeap();
        markImmortalHeap();
    }

    /**
     * Return bit index in the color map of the first grey object in the specified area or -1 if none
     * is found.
     * @param start start of the scanned area
     * @param end end of the scanned area
     * @return a bit index in the color map, or -1 if no grey object was met during the scan.
     */
    public int scanForGrayMark(Address start, Address end) {
        Pointer p = start.asPointer();
        while (p.lessThan(end)) {
            final int bitIndex = bitIndexOf(p);
            if (isGrey(bitIndex)) {
                return bitIndex;
            }
            p = p.plus(Layout.size(Layout.cellToOrigin(p)));
        }
        return -1;
    }

    /**
     * Search the tricolor mark bitmap for a grey object in a specific region of the heap.
     * @param start start of the heap region.
     * @param end end of the heap region.
     * @return Return the bit index to the first grey mark found if any, -1 otherwise.
     */
    public int firstGreyMark(Pointer start, Pointer end) {
        final Pointer colorMapBase = base.asPointer();
        final int lastBitIndex = bitIndexOf(end);
        final int lastBitmapWordIndex = bitmapWordIndex(lastBitIndex);
        int bitmapWordIndex = bitmapWordIndex(bitIndexOf(start));
        while (bitmapWordIndex <= lastBitmapWordIndex) {
            long bitmapWord = colorMapBase.getLong(bitmapWordIndex);
            if (bitmapWord != 0) {
                final long greyMarksInWord = bitmapWord & (bitmapWord >> 1);
                if (greyMarksInWord != 0) {
                    // First grey mark is the least set bit.
                    final int bitIndexInWord = Pointer.fromLong(greyMarksInWord).leastSignificantBitSet();
                    final int bitIndexOfGreyCell = (bitmapWordIndex << Word.widthValue().log2numberOfBits) + bitIndexInWord;
                    return bitIndexOfGreyCell < lastBitIndex ? bitIndexOfGreyCell : -1;
                } else if ((bitmapWord >> LAST_BIT_INDEX_IN_WORD) == 1) {
                    // Mark span two words. Check first bit of next word to decide if mark is grey.
                    bitmapWord = colorMapBase.getLong(bitmapWordIndex + 1);
                    if ((bitmapWord & 1) != 0) {
                        // it is a grey object.
                        final int bitIndexOfGreyMark = (bitmapWordIndex << Word.widthValue().log2numberOfBits) + LAST_BIT_INDEX_IN_WORD;
                        return bitIndexOfGreyMark < lastBitIndex ? bitIndexOfGreyMark : -1;
                    }
                }
            }
            bitmapWordIndex++;
        }
        return -1;
    }

    /**
     * Verifies that a heap region has no grey objects.
     * @param start start of the region
     * @param end end of the region
     * @return true if the region has no grey objects, false otherwise.
     */
    public void verifyHasNoGreyMarks(Address start, Address end) {
        final int bitIndex = scanForGrayMark(start, end);
        FatalError.check(bitIndex >= 0, "Must not have any grey marks");
    }

    public void visitGreyObjects(Address start, ColorMapScanState scanState) {
        final Pointer colorMapBase = base.asPointer();
        int rightmostBitmapWordIndex = scanState.rightmostBitmapWordIndex();

        int bitmapWordIndex = bitmapWordIndex(bitIndexOf(start));
        do {
            while (bitmapWordIndex <= rightmostBitmapWordIndex) {
                long bitmapWord = colorMapBase.getLong(bitmapWordIndex);
                if (bitmapWord != 0) {
                    final long greyMarksInWord = bitmapWord & (bitmapWord >> 1);
                    if (greyMarksInWord != 0) {
                        // First grey mark is the least set bit.
                        final int bitIndexInWord = Pointer.fromLong(greyMarksInWord).leastSignificantBitSet();
                        final int bitIndexOfGreyCell = (bitmapWordIndex << Word.widthValue().log2numberOfBits) + bitIndexInWord;
                        final Pointer p = scanState.markAndVisitCell(addressOf(bitIndexOfGreyCell).asPointer());
                        // Get bitmap word index at the end of the object. This may avoid reading multiple mark bitmap words
                        // when marking objects crossing multiple mark bitmap words.
                        bitmapWordIndex = bitmapWordIndex(p);
                        continue;
                    } else if ((bitmapWord >> LAST_BIT_INDEX_IN_WORD) == 1) {
                        // Mark span two words. Check first bit of next word to decide if mark is grey.
                        bitmapWord = colorMapBase.getLong(bitmapWordIndex + 1);
                        if ((bitmapWord & 1) != 0) {
                            // it is a grey object.
                            final int bitIndexOfGreyCell = (bitmapWordIndex << Word.widthValue().log2numberOfBits) + LAST_BIT_INDEX_IN_WORD;
                            final Pointer p = scanState.markAndVisitCell(addressOf(bitIndexOfGreyCell).asPointer());
                            bitmapWordIndex = bitmapWordIndex(p);
                            continue;
                        }
                    }
                }
                bitmapWordIndex++;
            }
            // There might be some objects left in the marking stack. Drain it.
            markingStack.flush();
            // Rightmost may have been updated. Check for this, and loop back if it has.
            final int b = scanState.rightmostBitmapWordIndex();
            if (b <= rightmostBitmapWordIndex) {
                // We're done.
                break;
            }
            rightmostBitmapWordIndex = b;
        } while(true);
    }



    /**
     * Set the black bit of a live but not yet visited object.
     * This is used during normal forward scan of the mark bitmap.
     * Object after the finger are marked black directly, although in term of the
     * tricolor algorithm, they are grey. This simplifies forward scanning and avoid
     * updating the mark bitmap from grey to black when visiting a cell.
     * Object before the marking stack are marked black as well and pushed on the marking stack.
     * If this one overflows, the mark are set to grey so they can be distinguished from real black ones
     * during rescan of the mark bitmap.
     *
     * @param cell the address of the object to mark.
     */
    @INLINE
    private void markObjectBlack(Pointer cell) {
        if (cell.greaterThan(finger)) {
            // Object is after the finger. Mark grey and update rightmost if white.
            if (markBlackIfWhite(cell) && cell.greaterThan(rightmost)) {
                rightmost = cell;
            }
        } else if (cell.greaterEqual(coveredAreaStart) && markBlackIfWhite(cell)) {
            markingStack.push(cell);
        }
    }

    @INLINE
    final void markGripBlack(Grip grip) {
        markObjectBlack(Layout.originToCell(grip.toOrigin()));
    }

    final PointerIndexVisitor markBlackPointerIndexVisitor = new PointerIndexVisitor() {
        @Override
        public  final void visit(Pointer pointer, int wordIndex) {
            markGripBlack(pointer.getGrip(wordIndex));
        }
    };

    Pointer visitBlackCell(Pointer cell) {
        if (Heap.traceGC()) {
            printVisitedCell(cell);
        }
        final Pointer origin = Layout.cellToOrigin(cell);
        final Grip hubGrip = Layout.readHubGrip(origin);
        markGripBlack(hubGrip);
        final Hub hub = UnsafeCast.asHub(hubGrip.toJava());

        // Update the other references in the object
        final SpecificLayout specificLayout = hub.specificLayout;
        if (specificLayout.isTupleLayout()) {
            TupleReferenceMap.visitReferences(hub, origin, markBlackPointerIndexVisitor);
            if (hub.isSpecialReference) {
                SpecialReferenceManager.discoverSpecialReference(Grip.fromOrigin(origin));
            }
            return cell.plus(hub.tupleSize);
        }
        if (specificLayout.isHybridLayout()) {
            TupleReferenceMap.visitReferences(hub, origin, markBlackPointerIndexVisitor);
        } else if (specificLayout.isReferenceArrayLayout()) {
            final int length = Layout.readArrayLength(origin);
            for (int index = 0; index < length; index++) {
                markGripBlack(Layout.getGrip(origin, index));
            }
        }
        return cell.plus(Layout.size(origin));
    }

    final Pointer setAndVisitBlackFinger(Pointer cell) {
        finger = cell;
        return visitBlackCell(cell);
    }

    /**
     * Alternative to visit grey object. Here, we assume that object after the finger are marked black directly.
     *
     * @param start
     * @param rightmostMark
     */
    public void scanForward(Pointer start, ColorMapScanState rightmostMark) {
        final Pointer colorMapBase = base.asPointer();
        int rightmostBitmapWordIndex = rightmostMark.rightmostBitmapWordIndex();
        int bitmapWordIndex = bitmapWordIndex(bitIndexOf(start));
        do {
            while (bitmapWordIndex <= rightmostBitmapWordIndex) {
                long bitmapWord = colorMapBase.getLong(bitmapWordIndex);
                if (bitmapWord != 0) {
                    // At least one mark is set.
                    int bitIndexInWord = 0;
                    final int bitmapWordFirstBitIndex = bitmapWordIndex << Word.widthValue().log2numberOfBits;
                    Pointer endOfLastVisitedCell;
                    do {
                        // First mark is the least set bit.
                        bitIndexInWord += Pointer.fromLong(bitmapWord).leastSignificantBitSet();
                        final int bitIndexOfGreyMark = bitmapWordFirstBitIndex + bitIndexInWord;
                        endOfLastVisitedCell = setAndVisitBlackFinger(addressOf(bitIndexOfGreyMark).asPointer());
                        // Need to read the mark bitmap word again in case a mark was set in the bitmap word by the visitor.
                        // We also right-shift the bitmap word shift it to flush the mark bits already processed.
                        // This is necessary because live objects after the finger are marked black only, so we cannot
                        // distinguish black from grey.
                        bitIndexInWord += 2;
                        bitmapWord = colorMapBase.getLong(bitmapWordIndex) >>> bitIndexInWord;
                    } while(bitmapWord != 0);
                    bitmapWordIndex++;
                    final int nextCellBitmapWordIndex =  bitmapWordIndex(endOfLastVisitedCell);
                    if (nextCellBitmapWordIndex > bitmapWordIndex) {
                        bitmapWordIndex = nextCellBitmapWordIndex;
                    }
                } else {
                    bitmapWordIndex++;
                }
            }
            // There might be some objects left in the marking stack. Drain it.
            markingStack.flush();
            // Rightmost may have been updated. Check for this, and loop back if it has.
            final int b = rightmostMark.rightmostBitmapWordIndex();
            if (b <= rightmostBitmapWordIndex) {
                // We're done.
                break;
            }
            rightmostBitmapWordIndex = b;
        } while(true);
    }

    private int numRecoveryScan = 0;

    public void recoverFromOverflow() {
        // First, flush the marking stack, greying all objects in there,
        // and tracking the left most grey.
        // TODO: rescan map.
        markStackCellVisitor.resetLeftmostFlushed();
        markingStack.flush();
        Address leftmostFlushed = markStackCellVisitor.leftmostFlushed;
        // Next, initiate scanning to recover from overflow. This consists of
        // marking grey objects between the leftmost flushed mark and the finger.
        // As for a normal scan, any reference pointing after the finger are marked grey and
        // the rightmost mark of the normal scan is updated.
        // Any reference before the finger are marked grey and  pushed on the marking stack.
        // The scan stops when reaching the finger (which act
        // as the rightmost bound for this scan).
        // If the marking stack overflow again, we flush the stack again, write down the leftmost mark
        // for the next scan.

        if (!recovering) {
            recovering = true;
            numRecoveryScan++;
            startOfNextOverflowScan = leftmostFlushed;
            overflowScanState.updateFinger();

            do {
                startOfCurrentOverflowScan = startOfNextOverflowScan;
                startOfNextOverflowScan = finger;
                visitGreyObjects(startOfCurrentOverflowScan, overflowScanState);
            } while (startOfNextOverflowScan.lessThan(finger));
            recovering = false;
            verifyHasNoGreyMarks(coveredAreaStart, finger);
        } else if (leftmostFlushed.lessThan(overflowScanState.visitedCell())) {
            // Schedule another rescan if the leftmost flushed cell is before the
            // currently visited cell.
            startOfNextOverflowScan = leftmostFlushed;
        }
    }

    /**
     * Visit all objects marked grey during root marking.
     */
    void visitAllGreyObjects() {
        markingStack.setCellVisitor(markStackCellVisitor);
        visitGreyObjects(leftmost, forwardScanState);
    }


    public Pointer firstBlackObject() {
        return firstBlackObject(coveredAreaStart, rightmost);
    }

    public Pointer firstBlackObject(Address start, Address end) {
        final int bitIndex = firstBlackMark(bitIndexOf(start), bitIndexOf(end));
        if (bitIndex >= 0) {
            return addressOf(bitIndex).asPointer();
        }
        return Pointer.zero();
    }

    int firstBlackMark(int firstBitIndex, int lastBitIndex) {
        final Pointer colorMapBase = base.asPointer();
        final int lastBitmapWordIndex = bitmapWordIndex(lastBitIndex);
        int bitmapWordIndex = firstBitIndex;
        while (bitmapWordIndex <= lastBitmapWordIndex) {
            long bitmapWord = colorMapBase.getLong(bitmapWordIndex);
            if (bitmapWord != 0) {
                // First mark is the least set bit.
                final int bitIndexInWord = Pointer.fromLong(bitmapWord).leastSignificantBitSet();
                final int bitIndexOfCell = (bitmapWordIndex << Word.widthValue().log2numberOfBits) + bitIndexInWord;
                if (bitIndexOfCell >= lastBitIndex) {
                    return -1;
                }
                return bitIndexOfCell;
            }
            bitmapWordIndex++;
        }
        return -1;
    }


    public void sweep(HeapSweeper sweeper) {
        final Pointer colorMapBase = base.asPointer();
        int rightmostBitmapWordIndex =  bitmapWordIndex(bitIndexOf(rightmost));
        int bitmapWordIndex = bitmapWordIndex(bitIndexOf(coveredAreaStart));

        while (bitmapWordIndex <= rightmostBitmapWordIndex) {
            long bitmapWord = colorMapBase.getLong(bitmapWordIndex);
            if (bitmapWord != 0) {
                // At least one mark is set.
                int bitIndexInWord = 0;
                final int bitmapWordFirstBitIndex = bitmapWordIndex << Word.widthValue().log2numberOfBits;
                Pointer endOfLastVisitedCell;
                do {
                    // First mark is the least set bit.
                    bitIndexInWord += Pointer.fromLong(bitmapWord).leastSignificantBitSet();
                    final int bitIndexOfBlackMark = bitmapWordFirstBitIndex + bitIndexInWord;
                    endOfLastVisitedCell = sweeper.processLiveObject(addressOf(bitIndexOfBlackMark).asPointer());
                    final int nextCellBitmapWordIndex =  bitmapWordIndex(endOfLastVisitedCell);
                    if (nextCellBitmapWordIndex > bitmapWordIndex) {
                        bitmapWordIndex = nextCellBitmapWordIndex;
                        break;
                    }
                    // End of visited cell is within the same mark word. Just
                    // right-shift the bitmap word to skip the mark bits already processed and loop back to
                    // find the next black object with this word.
                    bitIndexInWord += 2;
                    bitmapWord = bitmapWord >>> bitIndexInWord;
                } while(bitmapWord != 0);
                bitmapWordIndex++;
                final int nextCellBitmapWordIndex =  bitmapWordIndex(endOfLastVisitedCell);
                if (nextCellBitmapWordIndex > bitmapWordIndex) {
                    bitmapWordIndex = nextCellBitmapWordIndex;
                }
            } else {
                bitmapWordIndex++;
            }
        }
    }

    /*
     * Helper instance variable for debugging purposes.
     */
    Pointer gapLeftObject;
    Pointer gapRightObject;
    int     gapBitmapWordIndex;
    int     gapBitIndex;
    // FIXME: make local vars again.
    int lastLiveMark;
    int bitIndexInWord;
    long darkMatterBitCount;
    long w;
    /**
     * Imprecise sweeping of the heap.
     * The sweeper is notified only when the distance between two live marks is larger than a specified minimum amount of
     * space. In this case, the sweeper is passed on the address of the two live objects delimiting the space.
     * This avoids touching the heap for adjacent small objects, and paying reclamation cost for unusable dead spaces.
     *
     * @param sweeper
     * @param minReclaimableSpace
     */
    public Size impreciseSweep(HeapSweeper sweeper, Size minReclaimableSpace) {
        final Pointer colorMapBase = base.asPointer();
        final int minBitsBetweenMark = minReclaimableSpace.toInt() >> log2BytesCoveredPerBit;
        int bitmapWordIndex = 0;

        // Debug helpers. REMOVE
        gapLeftObject = Pointer.zero();
        gapRightObject = Pointer.zero();
        gapBitIndex = 0;
        gapBitmapWordIndex = 0;

        // Indicate the closest position the next live mark should be at to make the space reclaimable.
        int nextReclaimableMark = minBitsBetweenMark;
        // long
        darkMatterBitCount = 0;
        // int
        lastLiveMark = firstBlackMark(0, bitIndexOf(rightmost));
        if (lastLiveMark > 0) {
            if (lastLiveMark >=  nextReclaimableMark) {
                sweeper.processDeadSpace(coveredAreaStart, Size.fromInt(lastLiveMark << log2BytesCoveredPerBit));
            }
            bitmapWordIndex =  bitmapWordIndex(lastLiveMark + 2);
            nextReclaimableMark = lastLiveMark + 2 + minBitsBetweenMark;
        } else if (lastLiveMark < 0) {
            // The whole heap is free. (is that ever possible ?)
            sweeper.processDeadSpace(coveredAreaStart, rightmost.minus(coveredAreaStart).asSize());
            return Size.zero();
        }

        // Loop over the color map and call the sweeper only when the distance between two live mark is larger than
        // the minimum reclaimable space specified.
        int rightmostBitmapWordIndex =  bitmapWordIndex(bitIndexOf(rightmost));

        while (bitmapWordIndex <= rightmostBitmapWordIndex) {
            final long bitmapWord = colorMapBase.getLong(bitmapWordIndex);
            if (bitmapWord != 0) {
                // At least one mark is set.
                //int bitIndexInWord = 0;
                bitIndexInWord = 0;
                final int bitmapWordFirstBitIndex = bitmapWordIndex << Word.widthValue().log2numberOfBits;
                int nextCellBitmapWordIndex = bitmapWordIndex + 1;
                // long w = bitmapWord;
                w = bitmapWord;
                do {
                    // First mark is the least set bit.
                    bitIndexInWord += Pointer.fromLong(w).leastSignificantBitSet();
                    final int bitIndexOfBlackMark = bitmapWordFirstBitIndex + bitIndexInWord;
                    if (bitIndexOfBlackMark < nextReclaimableMark) {
                        darkMatterBitCount += bitIndexOfBlackMark - lastLiveMark;
                        // Too small a gap between two live marks to be worth reporting to the sweeper.
                        // Reset the next mark.
                        lastLiveMark = bitIndexOfBlackMark;
                        nextReclaimableMark = bitIndexOfBlackMark + minBitsBetweenMark;
                        if (bitIndexInWord >= 62) {
                            // next object begins in next word.
                            break;
                        }
                    } else {
                        // Debug helpers. REMOVE
                        gapBitIndex = bitIndexOfBlackMark;
                        gapBitmapWordIndex = bitmapWordIndex;
                        gapLeftObject = addressOf(lastLiveMark).asPointer();
                        gapRightObject = addressOf(bitIndexOfBlackMark).asPointer();

                        final Pointer endOfLastVisitedCell = sweeper.processLargeGap(addressOf(lastLiveMark).asPointer(), addressOf(bitIndexOfBlackMark).asPointer());
                        lastLiveMark  = bitIndexOfBlackMark;
                        nextReclaimableMark = bitIndexOf(endOfLastVisitedCell) + minBitsBetweenMark;
                        final int bitIndex =  bitmapWordIndex(endOfLastVisitedCell);
                        if (bitIndex > bitmapWordIndex) {
                            nextCellBitmapWordIndex = bitIndex;
                            break;
                        }
                        // End of visited cell is within the same mark word. Just
                        // right-shift the bitmap word to skip the mark bits already processed and loop back to
                        // find the next black object with this word.
                    }
                    bitIndexInWord += 2;
                    w = bitmapWord >>> bitIndexInWord;
                } while(w != 0);
                bitmapWordIndex = nextCellBitmapWordIndex;
            } else {
                bitmapWordIndex++;
            }
        }
        Pointer tail = rightmost.asPointer().plus(Layout.size(Layout.cellToOrigin(rightmost.asPointer())));
        Size tailSpace = coveredAreaEnd.minus(tail).asSize();
        if (tailSpace.greaterEqual(minReclaimableSpace)) {
            sweeper.processDeadSpace(tail, tailSpace);
        }
        return Size.fromLong(darkMatterBitCount << log2BytesCoveredPerBit);
    }



    public void markAll() {
        markRoots();
        visitAllGreyObjects();
        verifyHasNoGreyMarks(coveredAreaStart, coveredAreaEnd);
    }
}
