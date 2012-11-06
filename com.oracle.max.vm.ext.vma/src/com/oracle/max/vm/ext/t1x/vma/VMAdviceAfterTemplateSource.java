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
package com.oracle.max.vm.ext.t1x.vma;

import static com.oracle.max.vm.ext.t1x.T1XRuntime.*;
import static com.oracle.max.vm.ext.t1x.T1XTemplateTag.*;

import com.oracle.max.vm.ext.vma.run.java.*;
import com.oracle.max.vm.ext.vma.runtime.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.heap.*;
import com.sun.max.vm.object.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.runtime.*;
import com.oracle.max.vm.ext.t1x.*;

/**
 * Template source for after advice (where available).
 */
public class VMAdviceAfterTemplateSource {

// START GENERATED CODE
    @T1X_TEMPLATE(AALOAD)
    public static Reference aaload(@Slot(1) Object array, @Slot(0) int index, int bci) {
        ArrayAccess.checkIndex(array, index);
        Object result = ArrayAccess.getObject(array, index);
        if (Intrinsics.readLatchBit(VMAJavaRunScheme.VM_ADVISING.offset, 0)) {
            VMAStaticBytecodeAdvice.adviseAfterArrayLoad(bci, array, index, result);
        }
        return Reference.fromJava(result);
    }

    @T1X_TEMPLATE(NEW)
    public static Object new_(ResolutionGuard guard, int bci) {
        Object object = resolveClassForNewAndCreate(guard);
        if (Intrinsics.readLatchBit(VMAJavaRunScheme.VM_ADVISING.offset, 0)) {
            VMAStaticBytecodeAdvice.adviseAfterNew(bci, object);
        }
        return object;
    }

    @T1X_TEMPLATE(NEW$init)
    public static Object new_(DynamicHub hub, int bci) {
        Object object = Heap.createTuple(hub);
        if (Intrinsics.readLatchBit(VMAJavaRunScheme.VM_ADVISING.offset, 0)) {
            VMAStaticBytecodeAdvice.adviseAfterNew(bci, object);
        }
        return object;
    }

    @T1X_TEMPLATE(NEW_HYBRID)
    public static Object new_hybrid(DynamicHub hub, int bci) {
        Object object = Heap.createHybrid(hub);
        if (Intrinsics.readLatchBit(VMAJavaRunScheme.VM_ADVISING.offset, 0)) {
            VMAStaticBytecodeAdvice.adviseAfterNew(bci, object);
        }
        return object;
    }

    @T1X_TEMPLATE(NEWARRAY)
    public static Object newarray(ClassActor arrayClass, @Slot(0) int length, int bci) {
        Object array = Snippets.createArray(arrayClass, length);
        if (Intrinsics.readLatchBit(VMAJavaRunScheme.VM_ADVISING.offset, 0)) {
            VMAStaticBytecodeAdvice.adviseAfterNewArray(bci, array, length);
        }
        return array;
    }

    @T1X_TEMPLATE(ANEWARRAY)
    public static Object anewarray(ResolutionGuard arrayType, @Slot(0) int length, int bci) {
        ArrayClassActor<?> arrayClassActor = UnsafeCast.asArrayClassActor(Snippets.resolveArrayClass(arrayType));
        Object array = Snippets.createArray(arrayClassActor, length);
        if (Intrinsics.readLatchBit(VMAJavaRunScheme.VM_ADVISING.offset, 0)) {
            VMAStaticBytecodeAdvice.adviseAfterNewArray(bci, array, length);
        }
        return array;
    }

    @T1X_TEMPLATE(ANEWARRAY$resolved)
    public static Object anewarray(ArrayClassActor<?> arrayType, @Slot(0) int length, int bci) {
        ArrayClassActor<?> arrayClassActor = arrayType;
        Object array = Snippets.createArray(arrayClassActor, length);
        if (Intrinsics.readLatchBit(VMAJavaRunScheme.VM_ADVISING.offset, 0)) {
            VMAStaticBytecodeAdvice.adviseAfterNewArray(bci, array, length);
        }
        return array;
    }

    @T1X_TEMPLATE(MULTIANEWARRAY)
    public static Reference multianewarray(ResolutionGuard guard, int[] lengths, int bci) {
        ClassActor arrayClassActor = Snippets.resolveClass(guard);
        Object array = Snippets.createMultiReferenceArray(arrayClassActor, lengths);
        if (Intrinsics.readLatchBit(VMAJavaRunScheme.VM_ADVISING.offset, 0)) {
            VMAStaticBytecodeAdvice.adviseAfterMultiNewArray(bci, array, lengths);
        }
        return Reference.fromJava(array);
    }

    @T1X_TEMPLATE(MULTIANEWARRAY$resolved)
    public static Reference multianewarray(ArrayClassActor<?> arrayClassActor, int[] lengths, int bci) {
        Object array = Snippets.createMultiReferenceArray(arrayClassActor, lengths);
        if (Intrinsics.readLatchBit(VMAJavaRunScheme.VM_ADVISING.offset, 0)) {
            VMAStaticBytecodeAdvice.adviseAfterMultiNewArray(bci, array, lengths);
        }
        return Reference.fromJava(array);
    }

    @T1X_TEMPLATE(TRACE_METHOD_ENTRY)
    public static void traceMethodEntry(MethodActor methodActor, Object receiver, int bci) {
        if (Intrinsics.readLatchBit(VMAJavaRunScheme.VM_ADVISING.offset, 0)) {
            VMAStaticBytecodeAdvice.adviseAfterMethodEntry(bci, receiver, methodActor);
        }
    }

    @T1X_TEMPLATE(ALOAD)
    public static Reference aload(int index, int localOffset, int bci) {
        Reference value = VMRegister.getAbiFramePointer().readReference(localOffset);
        if (Intrinsics.readLatchBit(VMAJavaRunScheme.VM_ADVISING.offset, 0)) {
            VMAStaticBytecodeAdvice.adviseAfterLoad(bci, index, value);
        }
        return value;
    }

// END GENERATED CODE
}
