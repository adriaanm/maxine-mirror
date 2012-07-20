/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.tele.heap;

import java.lang.ref.*;
import java.util.*;

import com.sun.max.tele.reference.*;
import com.sun.max.tele.util.*;
import com.sun.max.unsafe.*;


/**
 * A map:  {@link Address} {@code -->} {@link RemoteReference}.
 * <p>
 * The map is implemented with {@link WeakReference}s to the {@link RemoteReference}s, and
 * is constrained not to permit additions that overwrite existing ones.
 */
public final class WeakRemoteReferenceMap<Ref_Type extends RemoteReference> {

    private final Map<Long, WeakReference<Ref_Type>> map = new HashMap<Long, WeakReference<Ref_Type>>();

    /**
     * Add an entry to the map.
     * <p>
     * It is an error if the map already contains an entry at the specified location.
     *
     * @param origin a location in VM memory
     * @param ref a reference to a VM object whose origin is at the specified location.
     */
    public void put(Address origin, Ref_Type ref) {
        assert origin.isNotZero();
        final WeakReference<Ref_Type> oldWeakRef = map.put(origin.toLong(), new WeakReference<Ref_Type>(ref));
        if (oldWeakRef != null && oldWeakRef.get() != null) {
            TeleWarning.message("Replacing reference already in map" + oldWeakRef.get().toOrigin().to0xHexString());
        }
        // FIXME: assert oldWeakRef == null || oldWeakRef.get() == null;
        // This assertion was too strong. In some rare scenario, this can "legally" occur.
        // For example, the Gen GC may format a free space as a dead object in the toSpace which may result in a remote reference to it being recorded in the
        // toSpace ref map. The free space may subsequently be re-used to refill an evacuation buffer and the first object to be allocated in that free space
        // will find its address already recorded in the remote reference map.
    }

    /**
     * Gets an existing reference to an object whose origin is at the specified location in VM memory, null if none.
     *
     * @param origin a location in VM memory.
     * @return the reference in the map at the specified location, null if none.
     */
    public Ref_Type get(Address origin) {
        final WeakReference<Ref_Type> weakRef = map.get(origin.toLong());
        return weakRef == null ? null : weakRef.get();
    }

    /**
     * Removes an existing reference to an object whose origin is at the specified location in VM memory.
     *
     * @param origin a location in VM memory.
     * @return the reference formerly in the map at the specified location, null if there was no such reference
     */
    public  Ref_Type remove(Address origin) {
        final WeakReference<Ref_Type> weakRef = map.remove(origin.toLong());
        return weakRef == null ? null : weakRef.get();
    }

    /**
     * Enumerates the {@link RemoteReference}s contained in the map.
     *
     * @return a new list containing the values, modification-safe
     */
    public List<Ref_Type> values() {
        final ArrayList<Ref_Type> values = new ArrayList<Ref_Type>(map.size());
        for (WeakReference<Ref_Type> weakRef : map.values()) {
            if (weakRef != null) {
                final Ref_Type ref = weakRef.get();
                if (ref != null) {
                    values.add(ref);
                }
            }
        }
        return values;
    }

    /**
     * @return the number of entries in the map.
     */
    public long size() {
        return map.size();
    }

    /**
     * Is the map completely empty?
     */
    public boolean isEmpty() {
        return map.isEmpty();
    }

    /**
     * Removes all entries from the map.
     */
    public void clear() {
        map.clear();
    }

}
