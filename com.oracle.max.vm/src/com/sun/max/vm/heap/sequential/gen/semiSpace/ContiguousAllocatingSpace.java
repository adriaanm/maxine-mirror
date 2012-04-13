/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.vm.heap.sequential.gen.semiSpace;

import com.sun.max.unsafe.*;
import com.sun.max.vm.heap.gcx.*;


public class ContiguousAllocatingSpace<T extends BaseAtomicBumpPointerAllocator<? extends Refiller>> implements HeapSpace {
    /**
     * Contiguous space used for allocation.
     */
    protected ContiguousHeapSpace space;
    /**
     * Allocator that allocates cells from the toSpace.
     */
    protected T allocator;


    ContiguousAllocatingSpace(T allocator) {
        this.allocator = allocator;
    }

    public T allocator() {
        return allocator;
    }

    @Override
    public Size growAfterGC(Size delta) {
        // TODO Auto-generated method stub
        return null;
    }
    @Override
    public Size shrinkAfterGC(Size delta) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Size totalSpace() {
        return space.committedSize();
    }

    @Override
    public Size capacity() {
        return space.size();
    }

    @Override
    public Pointer allocate(Size size) {
        return allocator.allocateCleared(size);
    }
    @Override
    public Pointer allocateTLAB(Size size) {
        // FIXME: the interface really want a HeapFreeChunk, but that shouldn't be necessary here. See how this can be changed
        // based on the code for TLAB overflow handling.
        return allocator.allocateCleared(size);
    }

    @Override
    public boolean contains(Address address) {
        return space.inCommittedSpace(address);
    }

    @Override
    public void doBeforeGC() {
        // FIXME: should this be when we flip the to and from space ?
        allocator.doBeforeGC();
    }

    @Override
    public void doAfterGC() {
    }

    @Override
    public Size freeSpace() {
        return allocator.freeSpace();
    }
    @Override
    public Size usedSpace() {
        return allocator.usedSpace();
    }
    @Override
    public void visit(HeapSpaceRangeVisitor visitor) {
        // TODO Auto-generated method stub

    }


}
