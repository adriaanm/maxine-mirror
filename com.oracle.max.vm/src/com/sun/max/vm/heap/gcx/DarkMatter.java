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
import com.sun.max.vm.classfile.constant.*;
import com.sun.max.vm.layout.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.type.*;
import com.sun.max.vm.value.*;

/**
 * Implementation of a special "<em>dark matter</em>" object type, used to fill free space that cannot be allocated, so that:
 * <ul>
 * <li>Memory management related operations that walk over memory regions comprising dark matter pay no extra cost for skipping dark matter. Examples
 * include sweep operations of  mark-sweep collector, and dirty card scanning in a generational GC with a card-table based remembered set. </li>
 * <li>The inspector can easily and unambiguously distinguish dark matter.</li>
 * </ul>
 * To achieve the above, each span of dark memory is formatted as a special type of object that can be neither allocated nor referenced directly
 * by application code.
 * Because dark matter can be of arbitrary size, it is formatted as an instance of the special scalar array type represented by the
 * {@linkplain DarkMatter#DARK_MATTER_ARRAY DARK_MATTER_ARRAY} array class actor.  This class:
 * <ul>
 * <li>has no symbolic definition;</li>
 * <li>is not registered in the class registry;</li>
 * <li>cannot be named in Java; and</li>
 * <li>cannot be instantiated via reflection.</li>
 * </ul>
 * This class is otherwise equivalent in layout to a long array.  As any array can have zero length, the minimum size for an instance is
 * three words.  The smallest pieces of heap space, which are two words wide, are formatted specially as instances of the class {@link SmallestDarkMatter}.
 */
public final class DarkMatter {

    @INSPECTED
    public static final String DARK_MATTER_CLASS_NAME = "dark matter []";

    @INSPECTED
    public static final ArrayClassActor<LongValue> DARK_MATTER_ARRAY =
        new ArrayClassActor<LongValue>(ClassRegistry.LONG, SymbolTable.makeSymbol(DARK_MATTER_CLASS_NAME));

    /**
     * Variable-less class used to format the smallest possible dark-matter (i.e., two-words space).
     */
    public static class SmallestDarkMatter {

        @INTRINSIC(UNSAFE_CAST)
        private static native SmallestDarkMatter asSmallestDarkMatter(Object darkMatter);

        @FOLD
        static DynamicHub hub() {
            return ClassActor.fromJava(SmallestDarkMatter.class).dynamicHub();
        }

        static void format(Address darkMatter) {
            final Pointer origin = Layout.cellToOrigin(darkMatter.asPointer());
            Layout.writeHubReference(origin, Reference.fromJava(hub()));
            Layout.writeMisc(origin, Word.zero());
        }

        private SmallestDarkMatter() {
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
        return DARK_MATTER_ARRAY.dynamicHub();
    }

    @FOLD
    private static Size darkMatterHeaderSize() {
        return Layout.longArrayLayout().getArraySize(Kind.LONG, 0);
    }

    /**
     * Format a word-aligned heap region as dark matter. A {@linkplain FatalError} is raised if the region is less than two-words wide.
     * @param start address to the first word of the region
     * @param size size of the region
     */
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

    /**
      * Format a word-aligned heap region as dark matter. A {@linkplain FatalError} is raised if the region is less than two-words wide.
     * @param start address to the first word of the region
     * @param end  address to the end of the last word of the region
     */
    public static void format(Address start, Address end) {
        format(start, end.minus(start).asSize());
    }
}
