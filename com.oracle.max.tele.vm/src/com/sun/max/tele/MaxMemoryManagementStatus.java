/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.tele;


/**
 * An enumeration of the states in which a location in VM memory can be with respect to
 * the VM's memory management at a given point in time.  This is a very preliminary
 * placeholder version, not in wide use, to be elaborated as the framework for memory
 * management in the VM and Inspector are generalized.
 */
public enum MaxMemoryManagementStatus {

    // TODO (mlvdv) This is a preliminary rough cut; it will be elaborated in discussion with Laurent
    // as we define a more general way to manage and describe memory management in the VM.
    // A more generalized set of states should be defined, along with some predicates that define
    // static properties of each defined state.
    // It isn't yet clear which attributes to define by a specific enum, and which by predicates
    // on one more members.

    /**
     * The memory is not in the heap.
     */
    NONE("None", "Not in heap"),
    /**
     * The memory is allocated and in use by the VM.
     */
    LIVE("Live", "Allocated"),

    /**
     * The memory is owned by the heap, but not in use and not being allocated from.
     */
    DEAD("Dead", "Unused"),

    /**
     * The memory is owned by the VM, in a region from which allocation is happening, but which is not
     * in a part of the region that has been allocated yet.
     */
    FREE("Free", "Available for allocation");

    private final String asString;
    private final String description;

    MaxMemoryManagementStatus(String asString, String description) {
        this.asString = asString;
        this.description = description;
    }

    public boolean isKnown() {
        return this != NONE;
    }

    public boolean isLive() {
        return this == LIVE;
    }

    public String description() {
        return description;
    }

    @Override
    public String toString() {
        return asString;
    }

}
