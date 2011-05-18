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

import com.sun.max.unsafe.*;
import com.sun.max.vm.heap.*;

/**
 * A refill manager that can also hands out list of free chunks to an allocator.
 */
abstract class ChunkListRefillManager extends RefillManager {
    /**
     * Minimum size for a chunk.
     */
    protected Size minChunkSize;

    void setMinChunkSize(Size size) {
        minChunkSize = size;
    }
    /**
     * Format specified region as a free chunk and returns it if not smaller than the minimum chunk size.
     * Otherwise, plant a dead object and return zero.
     * @param address address to the first word of the region
     * @param size size of the region
     * @return a non-null address if the specified region can be used as a free chunk.
     */
    protected Address chunkOrZero(Pointer address, Size size) {
        if (size.greaterThan(minChunkSize)) {
            HeapFreeChunk.format(address, size);
            return address;
        }
        if (!size.isZero()) {
            // Don't bother with the left over in the allocator.
            HeapSchemeAdaptor.fillWithDeadObject(address, address.plus(size));
        }
        return Address.zero();
    }

    abstract Address allocateChunkList(Size listSize, Pointer leftover, Size leftoverSize);
}
