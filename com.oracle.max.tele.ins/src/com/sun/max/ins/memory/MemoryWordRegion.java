/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.ins.memory;

import com.sun.max.ins.*;
import com.sun.max.ins.util.*;
import com.sun.max.tele.*;
import com.sun.max.unsafe.*;

/**
 * Representation for a region of VM memory constrained to contain
 * only whole words, word aligned.
 */
public class MemoryWordRegion extends AbstractMemoryRegion {

    private final Address start;
    private final long nBytes;
    private final long nWords;

    private final MaxMemoryRegion memoryRegion;

    /**
     * Creates a memory region containing only aligned, whole words.
     * @param inspection TODO
     * @param start address at beginning of region, must be word aligned
     * @param nWords number of words to include in the region
     */
    public MemoryWordRegion(Inspection inspection, Address start, long nWords) {
        super(inspection);
        this.start = start;
        this.nWords = nWords;
        final int nBytesInWord = inspection.vm().platform().nBytesInWord();
        this.nBytes = nBytesInWord * nWords;
        this.memoryRegion = null;
        InspectorError.check(start.isAligned(nBytesInWord));
    }

    /**
     * Creates a memory region containing only aligned, whole words.
     * @param inspection TODO
     * @param memoryRegion description of a region of VM memory
     * @throws InspectorError if the start or end of the region is not word aligned
     */
    public MemoryWordRegion(Inspection inspection, MaxMemoryRegion memoryRegion) {
        super(inspection);
        this.start = Address.zero();
        this.nBytes = -1;
        final int nBytesInWord = inspection.vm().platform().nBytesInWord();
        this.nWords = memoryRegion.nBytes() / nBytesInWord;
        this.memoryRegion = memoryRegion;
        InspectorError.check(memoryRegion.start().isAligned(nBytesInWord));
        InspectorError.check(memoryRegion.start().plus(memoryRegion.nBytes()).isAligned(nBytesInWord));
    }

    /**
     * Address of the specified word in the region.
     *
     * @param index index of word, 0 is at start
     * @return the address of the specified word
     */
    public Address getAddressAt(int index) {
        assert index >= 0 && index < nWords();
        return start().plus(vm().platform().nBytesInWord() * index);
    }

    /**
     * @return Index of word at a specified address in the region, -1 if not in region.
     */
    public int indexAt(Address address) {
        assert address != null;
        if (contains(address)) {
            return address.minus(start()).dividedBy(vm().platform().nBytesInWord()).toInt();
        }
        return -1;
    }

    /**
     * @return the number of words in the region
     */
    public long nWords() {
        return nWords;
    }

    public String regionName() {
        return memoryRegion == null ? null : memoryRegion.regionName();
    }

    public Address start() {
        return memoryRegion == null ? start : memoryRegion.start();
    }

    public long nBytes() {
        return memoryRegion == null ? nBytes : memoryRegion.nBytes();
    }

}
