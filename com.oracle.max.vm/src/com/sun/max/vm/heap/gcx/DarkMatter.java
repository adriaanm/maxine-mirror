/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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

import static com.sun.max.vm.intrinsics.MaxineIntrinsicIDs.*;

import com.sun.max.annotate.*;
import com.sun.max.memory.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.layout.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.type.*;

/**
 * An helper class to fill free space that cannot be allocated with instances of distinguishable "dark matter" object types, so that:
 * <ul>
 * <li>a GC walking a memory region including dark-matter objects doesn't pay extra cost for jumping over the dark matter. </li>
 * <li> the inspector can easily, and unambiguously distinguish dark matter object.</li>
 * <ul>
 *
 */
public final class DarkMatter {

    private static class SmallestDarkMatter {

        @INTRINSIC(UNSAFE_CAST)
        private static native SmallestDarkMatter asDarkMatterTag(Object darkMatter);

        static SmallestDarkMatter toDarkMatter(Address cell) {
            return asDarkMatterTag(Reference.fromOrigin(Layout.cellToOrigin(cell.asPointer())).toJava());
        }

        @FOLD
        static DynamicHub hub() {
            return ClassActor.fromJava(SmallestDarkMatter.class).dynamicHub();
        }

        @FOLD
        static Word hubWord() {
            return Reference.fromJava(hub()).toOrigin();
        }

        static void format(Address darkMatter) {
            final Pointer origin = Layout.cellToOrigin(darkMatter.asPointer());
            Layout.writeHubReference(origin, Reference.fromJava(hub()));
            Layout.writeMisc(origin, Word.zero());
        }
    }

    private DarkMatter() {
    }

    @FOLD
    public static Size minSize() {
        return SmallestDarkMatter.hub().tupleSize;
    }

    @FOLD
    private static DynamicHub hub() {
        return ClassRegistry.SYSTEM_LONG_ARRAY.dynamicHub();
    }

    @FOLD
    private static Size darkMatterHeaderSize() {
        return Layout.longArrayLayout().getArraySize(Kind.LONG, 0);
    }

    @FOLD
    private static Word hubWord() {
        return Reference.fromJava(hub()).toOrigin();
    }

    /**
     * Tells whether an address is the origin of an dark matter chunk.
     * @param origin
     * @return
     */
    public static boolean isDarkMatterOrigin(Address origin) {
        final Word hubWord =  origin.asPointer().readWord(Layout.hubIndex());
        return hubWord.equals(hubWord()) && hubWord.equals(SmallestDarkMatter.hubWord());
    }

    public static Size darkMatterChunkSize(Address address) {
        final Word hubWord =  address.asPointer().readWord(Layout.hubIndex());
        if (hubWord.equals(hubWord())) {
            return Size.fromInt(Layout.readArrayLength(address.asPointer()));
        }
        FatalError.check(hubWord.equals(SmallestDarkMatter.hubWord()), "not dark matter origin");
        return minSize();
    }

    public static void format(Address start, Size size) {
        if (size.greaterThan(minSize())) {
            final Pointer origin = Layout.cellToOrigin(start.asPointer());
            final int length = size.minus(darkMatterHeaderSize()).unsignedShiftedRight(Word.widthValue().log2numberOfBytes).toInt();
            Layout.writeHubReference(origin, Reference.fromJava(hub()));
            Layout.writeMisc(origin, Word.zero());
            Layout.writeArrayLength(origin, length);
            if (MaxineVM.isDebug()) {
                Memory.setWords(start.plus(darkMatterHeaderSize()).asPointer(), length, Memory.zappedMarker());
            }
        } else if (size.equals(minSize())) {
            SmallestDarkMatter.format(start);
        } else {
            final boolean lockDisabledSafepoints = Log.lock();
            Log.print("[");
            Log.print(start);
            Log.print(",");
            Log.print(start.plus(size));
            Log.print(" (");
            Log.print(size);
            Log.print(")");
            Log.unlock(lockDisabledSafepoints);
            FatalError.unexpected("invalid dark matter size");
        }
    }

    public static void format(Address start, Address end) {
        format(start, end.minus(start).asSize());
    }
}
