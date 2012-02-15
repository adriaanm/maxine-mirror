/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.max.annotate.*;
import com.sun.max.unsafe.*;

final public class ContiguousHeapRootCellVisitor extends RootCellVisitor {

    public ContiguousHeapRootCellVisitor() {
        super();
    }

    @Override
    void reset() {
        super.reset();
        bottom = heapMarker.coveredAreaStart;
    }

    /**
     * Marking cell referenced from outside of the covered area.
     * If the cell is itself outside of the covered area, nothing is done.
     *
     * @param cell a pointer read from an external root (may be zero).
     */
    @INLINE
    @Override
    boolean isNonNullCovered(Pointer cell) {
        // Note: the first test also acts as a null pointer filter.
        return true;
    }
}
