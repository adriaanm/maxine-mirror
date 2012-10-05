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
package com.sun.max.tele.object;

import java.lang.reflect.*;
import java.util.*;
import java.util.logging.*;

import com.sun.max.jdwp.vm.proxy.*;
import com.sun.max.tele.*;
import com.sun.max.tele.reference.*;
import com.sun.max.tele.util.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.layout.*;
import com.sun.max.vm.layout.Layout.HeaderField;
import com.sun.max.vm.type.*;
import com.sun.max.vm.value.*;

/**
 * Inspector's canonical surrogate for an object implemented as an {@link Array} in the {@link TeleVM},
 * one of the three kinds of low level Maxine heap implementation objects.
  */
public class TeleArrayObject extends TeleObject implements ArrayProvider {

    private static final HashSet<FieldActor> EMPTY_FIELD_ACTOR_SET = new HashSet<FieldActor>();

    private static final Logger LOGGER = Logger.getLogger(TeleArrayObject.class.getName());

    private int length = -1;

    private final Kind componentKind;

    private String maxineRole = null;

    protected TeleArrayObject(TeleVM vm, RemoteReference reference, Kind componentKind, SpecificLayout layout) {
        super(vm, reference, layout);
        this.componentKind = componentKind;
    }

    public ObjectKind kind() {
        return ObjectKind.ARRAY;
    }

    public HeaderField[] headerFields() {
        return objects().arrayLayout().headerFields();
    }

    /**
     * Gets the number of elements contained in the array; -1 if not yet available.  Once available, this
     * value will be reported every time, even if the object dies and the length field is overwritten.
     *
     * @return length of this array in the VM when it was created.
     */
    public int length() {
        if (length < 0) {
            length = objects().unsafeReadArrayLength(reference());
        }
        return length;
    }

    public TypeDescriptor componentType() {
        return classActorForObjectType().componentClassActor().typeDescriptor;
    }

    public Kind componentKind() {
        return componentKind;
    }

    public int arrayOffsetFromOrigin() {
        return objects().arrayLayout(componentKind()).getElementOffsetFromOrigin(0).toInt();
    }

    @Override
    public int objectSize() {
        return Layout.getArraySize(componentKind(), length()).toInt();
    }

    @Override
    public Set<FieldActor> getFieldActors() {
        return EMPTY_FIELD_ACTOR_SET;
    }

    @Override
    public boolean hasTextualVisualization() {
        return classActorForObjectType().javaClass() == char[].class;
    }

    @Override
    public String textualVisualization() {
        if (hasTextualVisualization()) {
            final char[] chars = (char[]) shallowCopy();
            return new String(chars);
        }
        return null;
    }

    /**
     * Reads an array element from VM memory as a boxed value.
     *
     * @param index
     * @return the value read from the specified field in this array in the VM
     * @throws ArrayIndexOutOfBoundsException if the index is out of bounds
     */
    public Value readElementValue(int index) {
        if (index < 0 || index >= length()) {
            throw new ArrayIndexOutOfBoundsException();
        }
        return reference().readArrayAsValue(componentKind(), index);
    }

    /**
     * From a reference array in VM memory, gets the value of an element interpreted as a
     * reference, traversing a forwarder if present; returns {@linkplain RemoteReference#zero()} if the
     * value does not point at a live object.
     *
     * @param index the
     * @return the value of the array element, interpreted as a reference.
     * @throws UnsupportedOperationException if not a reference array
     * @throws ArrayIndexOutOfBoundsException if the index is out of bounds
     */
    public RemoteReference readRemoteReference(int index) {
        if (!componentKind().isReference) {
            throw new UnsupportedOperationException();
        }
        if (index < 0 || index >= length()) {
            throw new ArrayIndexOutOfBoundsException();
        }
        return reference().readArrayAsRemoteReference(index);
    }

    @Override
    public  Address fieldAddress(FieldActor fieldActor) {
        throw TeleError.unexpected("Maxine Array objects don't contain fields");
    }

    @Override
    public int fieldSize(FieldActor fieldActor) {
        throw TeleError.unexpected("Maxine Array objects don't contain fields");
    }

    @Override
    public Value readFieldValue(FieldActor fieldActor) {
        throw TeleError.unexpected("Maxine Array objects don't contain fields");
    }

    @Override
    public Object shallowCopy() {
        final int length = length();
        if (componentKind().isReference) {
            final RemoteReference[] newRefArray = new RemoteReference[length];
            for (int index = 0; index < length; index++) {
                newRefArray[index] = (RemoteReference) readElementValue(index).asReference();
            }
            return newRefArray;
        }
        final Class<?> componentJavaClass = classActorForObjectType().componentClassActor().toJava();
        final Object newArray = Array.newInstance(componentJavaClass, length);
        for (int index = 0; index < length; index++) {
            Array.set(newArray, index, readElementValue(index).asBoxedJavaValue());
        }
        return newArray;
    }

    @Override
    protected Object createDeepCopy(DeepCopier context) {
        final Kind componentKind = componentKind();
        final int length = length();
        final Class<?> componentJavaClass = classActorForObjectType().componentClassActor().toJava();
        final Object newArray = Array.newInstance(componentJavaClass, length);
        context.register(this, newArray, true);
        if (length != 0) {
            if (componentKind != Kind.REFERENCE) {
                objects().unsafeCopyElements(componentKind, reference(), 0, newArray, 0, length);
            } else {
                Object[] referenceArray = (Object[]) newArray;
                for (int index = 0; index < length; index++) {
                    final Value value = readElementValue(index);
                    final TeleObject teleValueObject = objects().makeTeleObject((RemoteReference) value.asReference());
                    if (teleValueObject != null) {
                        referenceArray[index] = teleValueObject.makeDeepCopy(context);
                    }
                }
            }
        }
        return newArray;
    }

    public ArrayTypeProvider getArrayType() {
        return (ArrayTypeProvider) this.getReferenceType();
    }

    public VMValue getValue(int i) {
        return vm().maxineValueToJDWPValue(readElementValue(i));
    }

    public void setValue(int i, VMValue value) {
        LOGGER.info("Command received to SET ARRAY at index " + i + " + to + " + value);
    }


    @Override
    public String maxineRole() {
        return maxineRole;
    }

    /**
     * Assign to this array object a short description of the role played by this specific
     * array in the VM, suitable for textual annotation where mention of the object appears.
     *
     * @param role a short string describing the role played by this array in the VM.
     */
    public void setMaxineRole(String role) {
        this.maxineRole = role;
    }
}
