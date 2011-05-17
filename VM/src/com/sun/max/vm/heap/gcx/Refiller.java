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

/**
 * Basic refiller for bump pointer allocators, that allocate a refill regardless of the space left in the allocator.
 */
public abstract class Refiller {

    /**
     * Dispose of the contiguous space left in the allocator and return a new chunk of memory to refill it.
     *
     * @param startOfSpaceLeft address of the first byte of the space left at the end of the linear space allocator being asking for refill.
     * @param spaceLeft size, in bytes, of the space left
     * @return
     */
    abstract Address allocateRefill(Pointer startOfSpaceLeft, Size spaceLeft);

    /**
     * Make the portion of the allocator indicated by the start and end pointers iterable.
     * by an object iterator.
     * @param start
     * @param end
     */
    abstract void makeParsable(Pointer start, Pointer end);

}