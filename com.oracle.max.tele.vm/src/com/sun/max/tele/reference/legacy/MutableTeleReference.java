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
package com.sun.max.tele.reference.legacy;

import static com.sun.max.vm.heap.ObjectMemoryStatus.*;

import com.sun.max.tele.*;
import com.sun.max.tele.reference.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.heap.*;

// TODO (mlvdv) Old Heap
/**
 * Raw bits may change due to tele GC.
 */
public final class MutableTeleReference extends RemoteReference {

    private int index;
    private final LegacyReferenceManager legacyReferenceManager;
    private Address lastValidPointer = Address.zero();

    MutableTeleReference(TeleVM vm, int index, LegacyReferenceManager legacyReferenceManager) {
        super(vm);
        this.index = index;
        this.legacyReferenceManager = legacyReferenceManager;
    }

    @Override
    public ObjectMemoryStatus memoryStatus() {
        if (forwardedTeleRef != null) {
            MutableTeleReference forwardedTeleRef = (MutableTeleReference) getForwardedTeleRef();
            if (forwardedTeleRef.index() == -1) {
                return DEAD;
            }
            return FORWARDED;
        }
        if (index == -1) {
            return DEAD;
        }
        return LIVE;
    }

    @Override
    public Address raw() {
        if (index == -1 || forwardedTeleRef != null) {
            return lastValidPointer;
        }
        Address tmp = legacyReferenceManager.getRawReference(this);
        if (!tmp.equals(Address.zero())) {
            lastValidPointer = tmp;
            return tmp;
        }
        index = -1;
        return lastValidPointer;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof MutableTeleReference) {
            final MutableTeleReference mutableTeleRef = (MutableTeleReference) other;
            return index == mutableTeleRef.index;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return index;
    }

    @Override
    public void finalize() throws Throwable {
        if (memoryStatus().isLive()) {
            legacyReferenceManager.finalizeMutableTeleReference(index);
        }
        super.finalize();
    }

    @Override
    public String toString() {
        return "<" + index + ">";
    }

    int index() {
        if (forwardedTeleRef != null) {
            if (forwardedTeleRef instanceof MutableTeleReference) {
                final MutableTeleReference mutableTeleRef = (MutableTeleReference) getForwardedTeleRef();
                return mutableTeleRef.index();
            }
        }
        return index;
    }
}
