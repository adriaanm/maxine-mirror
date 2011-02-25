/*
 * Copyright (c) 2007, 2010, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.vm.actor.holder;

import java.util.*;

import com.sun.max.*;
import com.sun.max.annotate.*;
import com.sun.max.vm.*;

/**
 * Management of unique integer identifiers for {@link ClassActor}s.
 *
 * Every class in the system is assigned a globally unique identifier. This
 * identifier is used in the implementation of interface dispatch, type
 * tests and also serves as the opaque {@code jclass} handle to a
 * class in JNI code.
 *
 * @author Bernd Mathiske
 */
public final class ClassID {

    /**
     * Value to be used as invalid class id.
     */
    public static int NULL_CLASS_ID = -1;

    private ClassID() {
    }

    static class VariableLengthArray<E> {
        /*
         * Simple first implementation. Backing Storage for the array is made of a
         * fixed size initial prefix and a variable tail that is resized automatically
         * when trying to add an out of bound element.
         */

        private final E[] prefix;
        private E[] variable;

        public VariableLengthArray(int initialCapacity) {
            final Class<E []> type = null;
            prefix =  Utils.newArray(type, initialCapacity);
            variable = Utils.newArray(type, 0);
        }

        private void ensureCapacity(int minOverflowCapacity) {
            // FIXME: need to make sure that capacity doesn't go beyond max int.
            int newCapacity = (variable.length * 3) / 2 + 1;
            if (newCapacity < minOverflowCapacity) {
                newCapacity = minOverflowCapacity;
            }
            E [] newOverflow = Arrays.copyOf(variable, newCapacity);
            variable = newOverflow;
        }

        public E set(int index, E element) {
            final int pl = prefix.length;
            if (index < pl) {
                E oldValue = prefix[index];
                prefix[index] = element;
                return oldValue;
            }
            final int oindex = index - pl;

            if (oindex >= variable.length) {
                ensureCapacity(oindex + 1);
            }
            E oldValue = variable[oindex];
            variable[oindex] = element;
            return oldValue;
        }

        public E get(int index) {
            final int pl = prefix.length;
            if (index < pl) {
                return prefix[index];
            }
            final int oindex = index - pl;
            if (oindex < variable.length) {
                return variable[oindex];
            }
            return null;
        }

        public int length() {
            return prefix.length + variable.length;
        }
        // TODO:
        // Add trimming method
    }

    public static final int MINIMAL_CLASSES_POPULATIONS = 5000;

    // TODO: replace with the array above and make this field known to GC for class unloading support.
    private static List<ClassActor> idToClassActor = new ArrayList<ClassActor>();

    private static BitSet usedIDs = new BitSet();

    /**
     * Inspector support.
     */
    @HOSTED_ONLY
    public static interface Mapping {
        ClassActor idToClassActor(int id);
    }

    @HOSTED_ONLY
    private static Mapping mapping;

    @HOSTED_ONLY
    public static void setMapping(Mapping map) {
        mapping = map;
    }

    public static synchronized ClassActor toClassActor(int id) {
        try {
            if (MaxineVM.isHosted() && mapping != null) {
                final ClassActor classActor = mapping.idToClassActor(id);
                if (classActor != null) {
                    return classActor;
                }
            }
            return idToClassActor.get(id);
        } catch (IndexOutOfBoundsException indexOutOfBoundsException) {
            return null;
        }
    }

    static synchronized int create() {
        final int id = usedIDs.nextClearBit(0);
        if (id == idToClassActor.size()) {
            idToClassActor.add(null);
        }
        usedIDs.set(id);
        return id;
    }

    static synchronized void register(int id, ClassActor classActor) {
        idToClassActor.set(id, classActor);
    }

    static synchronized void clear(int id) {
        idToClassActor.set(id, null);
        usedIDs.clear(id);
    }

    public static synchronized int largetClassId() {
        return idToClassActor.size();
    }
}
