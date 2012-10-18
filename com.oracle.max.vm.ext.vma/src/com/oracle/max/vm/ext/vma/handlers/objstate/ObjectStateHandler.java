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
package com.oracle.max.vm.ext.vma.handlers.objstate;

import com.sun.max.vm.jni.*;
import com.sun.max.vm.reference.*;

/**
 * Abstracts the implementation of state used for object tracking, in particular,
 * the mechanism for allocation and storage of unique identifiers, and the recording
 * of which objects are live (optional). An 8 bit bitset is supported for
 * arbitrary tagging of objects.
 *
 * An implementation is required to be thread safe by design or by explicit synchronization.
 *
 * This interface uses the opaque {@link ObjectID} class to represent object ids.
 *
 */
public abstract class ObjectStateHandler {

    /**
     * An instance of this class is used to inform about dead objects, in response to invoking the {@link #gc} method.
     * N.B. An implementation may not support the dead object handling.
     *
     */
    public interface DeadObjectHandler {
        void dead(ObjectID id);
    }

    /**
     * Create and assign a unique id for a tracked object.
     */
    public abstract ObjectID assignId(Object obj);

    /**
     * Create and assign a unique id for a tracked object via its reference.
     */
    public abstract ObjectID assignId(Reference objRef);

    /**
     * Create a unique id for an object that we did not see the creation of
     * and therefore likely will not see the garbage collection, e.g. (immortal,
     * boot heap objects).
     */
    public abstract ObjectID assignUnseenId(Object obj);

    /**
     * Return the unique id for given object or zero if {@code obj == null}.
     * @param obj
     */
    public abstract ObjectID readId(Object obj);

    /**
     * Read the {@code n}th bit of the bitset of the object.
     */
    public abstract int readBit(Object obj, int n);

    /**
     * Set the {@code n}th bit of the bitset of the object to {@code value}.
     */
    public abstract void writeBit(Object obj, int n, int value);

    /**
     * Optionally generate callbacks for objects that did not survive the gc that just completed.
     * @param rt
     */
    public abstract void gc(DeadObjectHandler rt);

}
