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
package com.sun.max.vm.layout.xohm;

import static com.sun.max.vm.VMConfiguration.*;

import com.sun.max.annotate.*;
import com.sun.max.program.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.layout.*;
import com.sun.max.vm.layout.Layout.HeaderField;
import com.sun.max.vm.layout.SpecificLayout.ObjectCellVisitor;
import com.sun.max.vm.layout.SpecificLayout.ObjectMirror;
import com.sun.max.vm.layout.ohm.*;
import com.sun.max.vm.object.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.type.*;
import com.sun.max.vm.value.*;

/**
 * A variant of {@link OhmGeneralLayout} with variable number of extra header words.
 * eXtended, Origin, Header, Mixed.
 *
 * Header words in tuples: hub, misc, xtra1, xtra2, ...
 * Header words in arrays: hub, misc, xtra1, xtra2, ..., length.
 */
public class XOhmGeneralLayout extends AbstractLayout implements GeneralLayout {

    private static final String XOHM_WORDS_PROPERTY = "max.vm.layout.xohm.words";

    /**
     * One more word in the header to record extra info.
     */
    public static class XHeaderField extends HeaderField {
        /**
         * The header word(s) in which the extra analysis info is encoded.
         */
        public final int slot;

        public XHeaderField(int slot, String description) {
            super("XTRA" + slot, description);
            this.slot = slot;
        }

        @Override
        public String toString() {
            return name;
        }

        static HeaderField[] headerFields(boolean isArray, int xtraCount) {
            HeaderField[] result = new HeaderField[(isArray ? 3 : 2) + xtraCount];
            result[0] = HeaderField.HUB;
            result[1] = HeaderField.MISC;
            for (int i = 0; i < xtraCount; i++) {
                result[i + 2] = new XHeaderField(i, "extra word " + i);
            }
            if (isArray) {
                result[result.length - 1] = HeaderField.LENGTH;
            }
            return result;

        }
    }

    /**
     * These are the equivalents to the similar methods in the {@link Layout} class.
     */
    public static class Static {
        @ACCESSOR(Pointer.class)
        @INLINE
        public static Word readXtra(Pointer origin, int slot) {
            return ((XOhmGeneralLayout) Layout.generalLayout()).readXtra(origin, slot);
        }

        @ACCESSOR(Reference.class)
        @INLINE
        public static Word readXtra(Reference reference, int slot) {
            return ((XOhmGeneralLayout) Layout.generalLayout()).readXtra(reference, slot);
        }

        @ACCESSOR(Pointer.class)
        @INLINE
        public static void writeXtra(Pointer origin, int slot, Word value) {
            ((XOhmGeneralLayout) Layout.generalLayout()).writeXtra(origin, slot, value);
        }

        @ACCESSOR(Reference.class)
        @INLINE
        public static void writeXtra(Reference reference, int slot, Word value) {
            ((XOhmGeneralLayout) Layout.generalLayout()).writeXtra(reference, slot, value);
        }

        @ACCESSOR(Reference.class)
        @INLINE
        public static Word compareAndSwapXtra(Reference reference, int slot, Word expectedValue, Word newValue) {
            return ((XOhmGeneralLayout) Layout.generalLayout()).compareAndSwapXtra(reference, slot, expectedValue, newValue);
        }
    }

    public boolean isTupleLayout() {
        return false;
    }

    public boolean isHybridLayout() {
        return false;
    }

    public boolean isReferenceArrayLayout() {
        return false;
    }

    /**
     * The offset of the hub pointer.
     */
    final int hubOffset = 0;

    /**
     * The offset of the extras (such as monitor and hashCode info).
     */
    final int miscOffset;

    /**
     * The offset of the first xtra field.
     */
    final int xOffset;

    /**
     * The number of xtra words.
     */
    public final int xtraCount;

    public XOhmGeneralLayout() {
        miscOffset = hubOffset + Word.size();
        xOffset = miscOffset + Word.size();
        final String countProp = System.getProperty(XOHM_WORDS_PROPERTY);
        xtraCount = countProp == null ? 1 : Integer.parseInt(countProp);
    }

    @INLINE
    public final Pointer cellToOrigin(Pointer cell) {
        return cell;
    }

    @INLINE
    public final Pointer originToCell(Pointer origin) {
        return origin;
    }

    public Offset getOffsetFromOrigin(HeaderField headerField) {
        if (headerField == HeaderField.HUB) {
            return Offset.fromInt(hubOffset);
        } else if (headerField == HeaderField.MISC) {
            return Offset.fromInt(miscOffset);
        } else if (headerField instanceof XHeaderField) {
            return Offset.fromInt(xOffset + ((XHeaderField) headerField).slot * Word.size());
        }
        throw new IllegalArgumentException(getClass().getSimpleName() + " does not know about header field: " + headerField);
    }

    @INLINE
    private Hub getHub(Accessor accessor) {
        return UnsafeCast.asHub(readHubReference(accessor).toJava());
    }

    @INLINE
    public final Layout.Category category(Accessor accessor) {
        final Hub hub = getHub(accessor);
        return hub.layoutCategory;
    }

    @INLINE
    public final boolean isArray(Accessor accessor) {
        return specificLayout(accessor).isArrayLayout();
    }

    @INLINE
    public final boolean isTuple(Accessor accessor) {
        return specificLayout(accessor).isTupleLayout();
    }

    @INLINE
    public final boolean isHybrid(Accessor accessor) {
        return specificLayout(accessor).isHybridLayout();
    }

    @INLINE
    public final SpecificLayout specificLayout(Accessor accessor) {
        return getHub(accessor).specificLayout;
    }

    @INLINE
    public final Size size(Accessor accessor) {
        final Hub hub = getHub(accessor);
        switch (hub.layoutCategory) {
            case TUPLE:
                return Layout.tupleLayout().specificSize(accessor);
            case ARRAY:
                return Layout.arrayLayout().getArraySize(hub.classActor.componentClassActor().kind, Layout.arrayLayout().readLength(accessor));
            case HYBRID:
                return Layout.hybridLayout().specificSize(accessor);
        }
        throw ProgramError.unknownCase();
    }

    @INLINE
    public final Reference readHubReference(Accessor accessor) {
        return accessor.readReference(hubOffset);
    }

    @INLINE
    public final Word readHubReferenceAsWord(Accessor accessor) {
        return accessor.readWord(hubOffset);
    }

    @INLINE
    public final void writeHubReference(Accessor accessor, Reference referenceClassReference) {
        accessor.writeReference(hubOffset, referenceClassReference);
    }

    @INLINE
    public final Word readXtra(Accessor accessor) {
        return readXtra(accessor, 0);
    }

    @INLINE
    public final Word readXtra(Accessor accessor, int slot) {
        return accessor.readWord(xOffset + slot * Word.size());
    }

    @INLINE
    public final void writeXtra(Accessor accessor, Word value) {
        writeXtra(accessor, 0, value);
    }

    @INLINE
    public final void writeXtra(Accessor accessor, int slot, Word value) {
        accessor.writeWord(xOffset + slot * Word.size(), value);
    }

    @INLINE
    public final Word compareAndSwapXtra(Accessor accessor, Word expectedValue, Word newValue) {
        return compareAndSwapXtra(accessor, 0, expectedValue, newValue);
    }

    @INLINE
    public final Word compareAndSwapXtra(Accessor accessor, int slot, Word expectedValue, Word newValue) {
        return accessor.compareAndSwapWord(xOffset + slot * Word.size(), expectedValue, newValue);
    }

    @INLINE
    public final Word readMisc(Accessor accessor) {
        return accessor.readWord(miscOffset);
    }

    @INLINE
    public final void writeMisc(Accessor accessor, Word value) {
        accessor.writeWord(miscOffset, value);
    }

    @INLINE
    public final Word compareAndSwapMisc(Accessor accessor, Word suspectedValue, Word newValue) {
        return accessor.compareAndSwapWord(miscOffset, suspectedValue, newValue);
    }

    @INLINE
    public final Reference forwarded(Reference ref) {
        if (ref.isMarked()) {
            return ref.readReference(hubOffset).unmarked();
        }
        return ref;
    }

    @INLINE
    public final Reference readForwardRef(Accessor accessor) {
        final Reference forwardRef = accessor.readReference(hubOffset);
        if (forwardRef.isMarked()) {
            return forwardRef.unmarked();
        }

        // no forward reference has been stored
        return Reference.zero();
    }

    @INLINE
    public final Reference readForwardRefValue(Accessor accessor) {
        final Reference forwardRef = accessor.readReference(hubOffset);
        if (forwardRef.isMarked()) {
            return forwardRef.unmarked();
        }
        // no forward reference has been stored
        //return the value (instead of zero) to be used in CAS
        return forwardRef;
    }

    @INLINE
    public final void writeForwardRef(Accessor accessor, Reference forwardRef) {
        accessor.writeReference(hubOffset, forwardRef.marked());
    }

    @INLINE
    public final Reference compareAndSwapForwardRef(Accessor accessor, Reference suspectedRef, Reference forwardRef) {
        return Reference.fromOrigin(accessor.compareAndSwapWord(hubOffset, suspectedRef.toOrigin(), forwardRef.marked().toOrigin()).asPointer());
    }

    @HOSTED_ONLY
    public void visitHeader(ObjectCellVisitor visitor, Object object) {
        final Hub hub = ObjectAccess.readHub(object);
        visitor.visitHeaderField(hubOffset, "hub", JavaTypeDescriptor.forJavaClass(hub.getClass()), ReferenceValue.from(hub));
        visitor.visitHeaderField(miscOffset, "misc", JavaTypeDescriptor.WORD, new WordValue(vmConfig().monitorScheme().createMisc(object)));
    }

    public int getHubReferenceOffsetInCell() {
        return hubOffset;
    }

    @HOSTED_ONLY
    protected Value readHeaderValue(ObjectMirror mirror, int offset) {
        if (offset == hubOffset) {
            return mirror.readHub();
        } else if (offset == miscOffset) {
            return mirror.readMisc();
        }
        return null;
    }

    @HOSTED_ONLY
    protected boolean writeHeaderValue(ObjectMirror mirror, int offset, Value value) {
        if (offset == hubOffset) {
            mirror.writeHub(value);
        } else if (offset == miscOffset) {
            mirror.writeMisc(value);
        } else {
            return false;
        }
        return true;
    }

}
