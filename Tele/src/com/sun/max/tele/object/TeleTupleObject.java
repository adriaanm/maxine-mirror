/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.max.lang.*;
import com.sun.max.program.*;
import com.sun.max.tele.*;
import com.sun.max.tele.util.*;
import com.sun.max.tele.value.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.layout.*;
import com.sun.max.vm.layout.Layout.HeaderField;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.value.*;

/**
 * Canonical surrogate for an object implemented as a tuple in the VM,
 * one of the three kinds of low level Maxine heap implementation objects.
 *
 * @author Michael Van De Vanter
  */
public class TeleTupleObject extends TeleObject {

    protected TeleTupleObject(TeleVM vm, Reference reference) {
        super(vm, reference, Layout.tupleLayout());
    }

    @Override
    public ObjectKind kind() {
        return ObjectKind.TUPLE;
    }

    @Override
    public HeaderField[] headerFields() {
        return Layout.tupleLayout().headerFields();
    }

    @Override
    public int objectSize() {
        return classActorForObjectType().dynamicTupleSize().toInt();
    }

    @Override
    public Address fieldAddress(FieldActor fieldActor) {
        return origin().plus(fieldActor.offset());
    }

    @Override
    public int fieldSize(FieldActor fieldActor) {
        return fieldActor.kind.width.numberOfBytes;
    }

    @Override
    public Value readFieldValue(FieldActor fieldActor) {
        if (fieldActor.kind.isReference) {
            return TeleReferenceValue.from(vm(), vm().wordToReference(reference().readWord(fieldActor.offset())));
        }
        return fieldActor.readValue(reference());
    }

    @Override
    public TeleClassMethodActor getTeleClassMethodActorForObject() {
        final Class<?> javaClass = classActorForObjectType().toJava();
        if (TargetMethod.class.isAssignableFrom(javaClass)) {
            final Reference classMethodActorReference = vm().teleFields().TargetMethod_classMethodActor.readReference(reference());
            if (classMethodActorReference != null && !classMethodActorReference.isZero()) {
                return (TeleClassMethodActor) heap().makeTeleObject(classMethodActorReference);
            }
        }
        return null;
    }

    @Override
    public Object shallowCopy() {
        final ClassActor classActor = classActorForObjectType();
        final Class<?> javaClass = classActor.toJava();
        try {
            final Object newTupleObject = Objects.allocateInstance(javaClass);
            ClassActor holderClassActor = classActor;
            do {
                for (FieldActor fieldActor : holderClassActor.localInstanceFieldActors()) {
                    if (!(fieldActor.kind.isReference || fieldActor.isInjected())) {
                        final Field field = fieldActor.toJava();
                        field.setAccessible(true);
                        final Value fieldValue = readFieldValue(fieldActor);
                        final Object asBoxedJavaValue = fieldValue.asBoxedJavaValue();
                        try {
                            field.set(newTupleObject, asBoxedJavaValue);
                        } catch (IllegalAccessException illegalAccessException) {
                            TeleError.unexpected("could not access field: " + field, illegalAccessException);
                        } catch (IllegalArgumentException illegalArgumentException) {
                            TeleError.unexpected("illegal argument field: " + field, illegalArgumentException);
                        }
                    }
                }
                holderClassActor = holderClassActor.superClassActor;
            } while (holderClassActor != null);
            return newTupleObject;
        } catch (InstantiationException instantiationException) {
            TeleError.unexpected("could not allocate instance: " + javaClass, instantiationException);
            return null;
        }
    }

    @Override
    protected Object createDeepCopy(DeepCopier context) {
        final ClassActor classActor = classActorForObjectType();
        final Class<?> javaClass = classActor.toJava();
        final String classMessage = "Copying instance fields of " + javaClass + " from VM";
        Trace.begin(COPY_TRACE_VALUE, classMessage);
        try {
            final Object newTuple = Objects.allocateInstance(javaClass);

            if (javaClass.getName().startsWith("java.lang") && !Number.class.isAssignableFrom(javaClass)) {
                TeleWarning.message("Deep copying instance of " + javaClass.getName());
            }

            context.register(this, newTuple, true);
            ClassActor holderClassActor = classActor;
            do {
                for (FieldActor fieldActor : holderClassActor.localInstanceFieldActors()) {
                    final String fieldMessage = fieldActor.format("Copying instance field '%n' of type '%t' from VM");
                    Trace.begin(COPY_TRACE_VALUE, fieldMessage);
                    context.copyField(this, newTuple, fieldActor);
                    Trace.end(COPY_TRACE_VALUE, fieldMessage);
                }
                holderClassActor = holderClassActor.superClassActor;
            } while (holderClassActor != null);
            return newTuple;
        } catch (InstantiationException instantiationException) {
            TeleError.unexpected("could not allocate instance: " + javaClass, instantiationException);
            return null;
        } finally {
            Trace.end(COPY_TRACE_VALUE, classMessage);
        }
    }

}
