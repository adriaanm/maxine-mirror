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
package com.sun.max.tele.reference;

import com.sun.max.tele.*;
import com.sun.max.tele.object.*;
import com.sun.max.tele.reference.direct.*;
import com.sun.max.tele.type.*;
import com.sun.max.tele.util.*;
import com.sun.max.tele.value.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.heap.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.reference.hosted.*;
import com.sun.max.vm.value.*;

/**
 * The singleton manager for instances of {@link Reference} that point (or pretend to point) at
 * objects in the VM.
 * <p>
 * This is a work in progress; part of an evolution toward modeling correctly the
 * generality of object representations in the VM.
 * <p>
 * A <strong>raw reference</strong> is an {@link Address} in VM memory where the object is currently
 * located.  However, the location may be subject to change by GC, so the raw reference may change over time.
 * <p>
 * References are intended to be canonical, i.e. refer to only one object.  However, in the course of inspection
 * duplicates may appear.  These are resolved at the conclusion of each GC.
 */
public final class VmReferenceManager extends AbstractVmHolder {

    private static final int TRACE_VALUE = 1;

    private static VmReferenceManager vmReferenceManager;

    public static VmReferenceManager make(TeleVM vm, RemoteReferenceScheme referenceScheme) {
        if (vmReferenceManager == null) {
            vmReferenceManager = new VmReferenceManager(vm, referenceScheme);
        }
        return vmReferenceManager;
    }

    private long lastUpdateEpoch = -1L;

    private final RemoteReferenceScheme referenceScheme;

    private final RemoteReference zeroReference;

    private VmReferenceManager(TeleVM vm, RemoteReferenceScheme referenceScheme) {
        super(vm);
        this.referenceScheme = referenceScheme;
        referenceScheme.setContext(vm);

        this.zeroReference = new RemoteReference(vm) {

            @Override
            public ObjectStatus status() {
                return ObjectStatus.DEAD;
            }

            @Override
            public String toString() {
                return "null";
            }

            @Override
            public boolean equals(Reference other) {
                return this == other;
            }

            @Override
            public int hashCode() {
                return 0;
            }

            @Override
            public Address raw() {
                return Address.zero();
            }
        };
    }

    /**
     * Checks that a {@link Reference} points to a heap object in the VM;
     * throws an unchecked exception if not.  This is a low-level method
     * that uses a debugging tag or (if no tags in image) a heuristic; it does
     * not require access to the {@link VmClassAccess}.
     *
     * @param reference memory location in the VM
     * @throws InvalidReferenceException when the location does <strong>not</strong> point
     * at a valid heap object.
     */
    public void checkReference(Reference reference) throws InvalidReferenceException {
        if (!objects().isValidOrigin(reference.toOrigin())) {
            throw new InvalidReferenceException(reference);
        }
    }

    /**
     * Gets the location of an object's origin in VM memory.
     *
     * @param reference a remote reference to a VM object
     * @return a VM memory location that is the object's origin.
     */
    public Address toOrigin(Reference reference) {
        return referenceScheme.toOrigin(reference);
    }

    /**
     * Create a remote instance of {@link Reference} whose origin is at a given address,
     * but without any checking that a valid object is at that address and without any
     * support for possible relocation.
     * <p>
     * <strong>Unsafe:</strong> These are not canonical and should only be used
     * for temporary, low level access to object state.  They should not be retained across
     * VM execution.
     *
     * @param address a location in VM memory
     * @return the address wrapped as a remote object reference
     */
    public UnsafeRemoteReference makeUnsafeRemoteReference(Address address) {
        return new UnsafeRemoteReference(vm(), address);
    }

    public ReferenceValue createReferenceValue(Reference reference) {
        if (reference instanceof RemoteReference) {
            return TeleReferenceValue.from(vm(), reference);
        } else if (reference instanceof HostedReference) {
            return TeleReferenceValue.from(vm(), Reference.fromJava(reference.toJava()));
        }
        throw TeleError.unexpected("Got a non-Prototype, non-Tele reference in createReferenceValue");
    }

    /**
     * @return the canonical null/zero reference, can be compared with {@code ==}
     */
    public RemoteReference zeroReference() {
        return zeroReference;
    }

//    /**
//     * Returns some kind of reference associated with the given raw reference in the VM, depending
//     * on what is known about the address.
//     * <ol>
//     * <li>If a canonical reference pointing at that location already exists, then returns it.</li>
//     * <li>If the address is the valid origin of an object in a <strong>non-collected</strong> heap region, for
//     * example the boot heap or an immortal heap, then return a new reference that is canonical,
//     * but which is not tracked for possible GC relocation.</li>
//     * <li>If the address is the valid origin of a live object in a dynamic heap region, then return
//     * a new reference that is canonical and which is tracked for possible GC relocation.</li>
//     * <li>If the address is the valid origin of an object in a dynamic heap region, but the object
//     * is known <strong>not</strong> to be live, then return an unsafe, temporary reference that
//     * wraps the address.</li>
//     * <li>If the address does not point an an object origin,  then return an unsafe, temporary reference that
//     * wraps the address.</li>
//     * </ol>
//     *
//     * @param address a memory location in VM memory
//     * @return a special kind of {@link Reference} implementation that encapsulates a remote
//     * location in VM memory, allowing the reuse of much VM code that deals with references.
//     */


    /**
     * Creates an specialized instance of the VM's {@link Reference} class that can refer to objects
     * remotely in the VM.  Each instance is specialized for the kind of object management that takes
     * place in the memory region that contains the specified location, and in the case of managed
     * regions, the {@link Reference} tracks the object, just as in the VM itself.
     * <ol>
     * <li>Returns {@link Reference#zero()} if the specified location is {@link Address#zero()}.</li>
     * </ol>
     *
     * @param address a location in VM Memory
     * @return a reference
     */
    public RemoteReference makeReference(Address address) {
        if (address.isZero()) {
            return zeroReference();
        }
        vm().lock();
        try {
            VmObjectHoldingRegion<?> objectHoldingRegion = null;
            objectHoldingRegion = vm().heap().findHeapRegion(address);
            if (objectHoldingRegion == null) {
                objectHoldingRegion = vm().codeCache().findCodeCacheRegion(address);
            }
            if (objectHoldingRegion != null) {
                RemoteReference remoteReference = objectHoldingRegion.objectReferenceManager().makeReference(address);
                return remoteReference == null ? zeroReference() : remoteReference;
            }
            return makeUnsafeRemoteReference(address);
        } finally {
            vm().unlock();
        }
    }

}
