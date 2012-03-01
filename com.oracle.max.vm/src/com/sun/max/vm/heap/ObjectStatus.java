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
package com.sun.max.vm.heap;

import java.util.*;

//TODO (mlvdv) articulate behavior with respect to relocated/forwarded objects.
/**
 * The status of an object in the VM, or more precisely the status of the region of memory
 * in which an object's state is or was represented.
 * <ul>
 * <li> {@link #LIVE}: Determined to be reachable as of the most recent collection</li>
 * <li> {@link #UNKNOWN}: During liveness analysis:  formerly live, not yet determined reachable</li>
 * <li> {@link #DEAD}: Determined unreachable; no further assumptions about memory may be made</li>
 * <li> {@link #FORWARDED}: a possibly obsolete status, may be removed</li>
 * </ul>
 * <p>
 * Possible transitions:
 * <ul>
 * <li> {@link #LIVE} --> {@link #UNKNOWN}: at the beginning of liveness analysis,
 * when the status of all objects becomes uncertain until further notice;</li>
 * <li> {@link #UNKNOWN} --> {@link #LIVE}: during liveness analysis, when an object
 * is determined to be reachable;</li>
 * <li> {@link #UNKNOWN} --> {@link #DEAD}: at the conclusion of liveness analysis, when
 * it becomes certain that an object is unreachable;</li>
 * </ul>
 */
public enum ObjectStatus {

    /**
     * The region of memory is in a live allocation area and represents an object
     * that was determined to be reachable as of the most recent collection.
     */
    LIVE("Live", "Determined to be reachable as of the most recent collection"),

    /**
     * Only during liveness analysis: the region of memory was live as of the previous collection
     * and has not yet been determined live during the current analysis.
     */
    UNKNOWN("Unknown", "During liveness analysis:  formerly live, not yet determined reachable"),

    /**
     * The region of memory formerly represented an object that has been collected.
     */
    DEAD("Dead", "The region of memory formerly represented an object that has been collected"),

    /**
     * The region of memory formerly represented an object that has been moved to another location.
     * This status is possibly obsolete and may be removed.
     */
    FORWARDED("Forwarded", "An object that has been moved to another location");

    private final String label;
    private final String description;

    private ObjectStatus(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    /**
     * Does the memory represent an object that is currently assumed to be reachable?
     *
     * @return {@code this == } {@link #LIVE}.
     */
    public boolean isLive() {
        return this == LIVE;
    }

    /**
     * Does the memory represent an object that we treat provisionally as reachable: either {@link #LIVE},
     * {@link #UNKNOWN}, or {@link #FORWARDED}.
     *
     * @return {@code this != } {@link #DEAD}.
     */
    public boolean isNotDead() {
        return this != DEAD;
    }

    /**
     * During liveness analysis only, has the formerly live object not yet been determined to be reachable?
     * {@code false} at other times.
     *
     * @return {@code this == } {@link #UNKNOWN}.
     */
    public boolean isUnknown() {
        return this == UNKNOWN;
    }

    /**
     * Should he object be presumed live until further notice: either {@link #LIVE} or (during liveness analysis only)
     * not yet determined to be {@link #DEAD}?
     *
     * @return {@code this == } {@link #LIVE} {@code ||} {@link #UNKNOWN}.
     * @see Monty Python
     */
    public boolean isNotDeadYet() {
        return this == LIVE || this == UNKNOWN;
    }

    /**
     * Does the memory contain a copy of an object in the VM that has been abandoned after relocation by GC.
     *
     * @return {@code this == } {@link #FORWARDED}.
     */
    public boolean isForwarded() {
        return this == FORWARDED;
    }

    /**
     * Has the object represented by the memory been determined to be unreachable?
     *
     * @return {@code this == } {@link #DEAD}.
     */
    public boolean isDead() {
        return this == DEAD;
    }

    public static final List<ObjectStatus> VALUES = Arrays.asList(values());

}
