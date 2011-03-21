/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.vm.runtime;

import static com.sun.cri.bytecode.Bytecodes.Infopoints.*;
import static com.sun.cri.bytecode.Bytecodes.MemoryBarriers.*;
import static com.sun.max.vm.runtime.VMRegister.*;
import static com.sun.max.vm.runtime.VmOperation.*;
import static com.sun.max.vm.stack.JavaFrameAnchor.*;
import static com.sun.max.vm.thread.VmThread.*;
import static com.sun.max.vm.thread.VmThreadLocal.*;

import java.lang.reflect.*;

import com.sun.cri.bytecode.*;
import com.sun.cri.bytecode.Bytecodes.MemoryBarriers;
import com.sun.max.annotate.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.classfile.constant.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.heap.*;
import com.sun.max.vm.object.*;
import com.sun.max.vm.profile.*;
import com.sun.max.vm.runtime.ResolutionGuard.InAccessingClass;
import com.sun.max.vm.runtime.ResolutionGuard.InPool;
import com.sun.max.vm.stack.*;
import com.sun.max.vm.thread.*;

/**
 * Runtime routines to be used for translating/implementing bytecodes.
 *
 * A compiler could preprocess some/all of these routines into its own
 * IR for direct use during translation (as opposed to making runtime
 * calls).
 *
 * History: These routines are a collection of some of the routines
 * previously encapsulated in subclasses of the Snippet class.
 */
public class Snippets {

    @INLINE
    public static Object createTupleOrHybrid(ClassActor classActor) {
        if (MaxineVM.isHosted()) {
            try {
                return Objects.allocateInstance(classActor.toJava());
            } catch (InstantiationException instantiationException) {
                throw ProgramError.unexpected(instantiationException);
            }
        }
        if (classActor.isHybridClass()) {
            return Heap.createHybrid(classActor.dynamicHub());
        }
        final Object object = Heap.createTuple(classActor.dynamicHub());
        if (classActor.hasFinalizer()) {
            SpecialReferenceManager.registerFinalizee(object);
        }
        return object;
    }

    @INLINE
    public static Object createArray(ClassActor arrayClassActor, int length) {
        if (length < 0) {
            Throw.negativeArraySizeException(length);
        }
        if (MaxineVM.isHosted()) {
            return Array.newInstance(arrayClassActor.componentClassActor().toJava(), length);
        }
        return Heap.createArray(arrayClassActor.dynamicHub(), length);
    }

    public static Object createMultiReferenceArray(ClassActor classActor, int[] lengths) {
        if (!classActor.isArrayClass()) {
            throw new VerifyError("MULTIANEWARRAY cannot be applied to non-array type " + classActor);
        }
        return createMultiReferenceArrayAtIndex(0, classActor, lengths);
    }

    private static Object createMultiReferenceArrayAtIndex(int index, ClassActor arrayClassActor, int[] lengths) {
        final int length = lengths[index];
        final Object result = createNonNegativeSizeArray(arrayClassActor, length);
        if (length > 0) {
            final int nextIndex = index + 1;
            if (nextIndex < lengths.length) {
                final ClassActor subArrayClassActor = arrayClassActor.componentClassActor();
                for (int i = 0; i < length; i++) {
                    final Object subArray = createMultiReferenceArrayAtIndex(nextIndex, subArrayClassActor, lengths);
                    if (MaxineVM.isHosted()) {
                        final Object[] array = (Object[]) result;
                        array[i] = subArray;
                    } else {
                        ArrayAccess.setObject(result, i, subArray);
                    }
                }
            }
        }
        return result;
    }

    @INLINE
    static Object createNonNegativeSizeArray(ClassActor arrayClassActor, int length) {
        if (MaxineVM.isHosted()) {
            return Array.newInstance(arrayClassActor.componentClassActor().toJava(), length);
        }
        return Heap.createArray(arrayClassActor.dynamicHub(), length);
    }

    @INLINE
    public static Word selectNonPrivateVirtualMethod(Object receiver, VirtualMethodActor declaredMethod) {
        final Hub hub = ObjectAccess.readHub(receiver);
        return hub.getWord(declaredMethod.vTableIndex());
    }

    @INLINE
    public static Word selectInterfaceMethod(Object receiver, InterfaceMethodActor interfaceMethod) {
        final Hub hub = ObjectAccess.readHub(receiver);
        final InterfaceActor interfaceActor = UnsafeCast.asInterfaceActor(interfaceMethod.holder());
        final int interfaceIndex = hub.getITableIndex(interfaceActor.id);
        return hub.getWord(interfaceIndex + interfaceMethod.iIndexInInterface());
    }

    @INLINE
    public static Address selectInterfaceMethod(Object receiver, InterfaceMethodActor interfaceMethodActor, MethodProfile mpo, int mpoIndex) {
        Hub hub = ObjectAccess.readHub(receiver);
        Address entryPoint = selectInterfaceMethod(receiver, interfaceMethodActor).asAddress();
        MethodInstrumentation.recordType(mpo, hub, mpoIndex, MethodInstrumentation.DEFAULT_RECEIVER_METHOD_PROFILE_ENTRIES);
        return entryPoint;
    }

    @NEVER_INLINE
    private static void resolveStaticFieldForReading0(ResolutionGuard.InPool guard) {
        final ConstantPool constantPool = guard.pool;
        final int index = guard.cpi;
        final FieldActor fieldActor = constantPool.fieldAt(index).resolve(constantPool, index);
        if (!fieldActor.isStatic()) {
            throw new IncompatibleClassChangeError();
        }
        guard.value = fieldActor;
    }

    /**
     * Resolves a constant pool entry denoting a static field that is being read.
     *
     * This snippet is used when translating the {@link Bytecodes#GETSTATIC} instruction.
     */
    @INLINE
    public static FieldActor resolveStaticFieldForReading(ResolutionGuard.InPool guard) {
        if (guard.value == null) {
            resolveStaticFieldForReading0(guard);
        }
        return UnsafeCast.asFieldActor(guard.value);
    }

    @NEVER_INLINE
    private static void resolveStaticFieldForWriting0(ResolutionGuard.InPool guard) {
        final ConstantPool constantPool = guard.pool;
        final int index = guard.cpi;
        final FieldActor fieldActor = constantPool.fieldAt(index).resolve(constantPool, index);
        if (!fieldActor.isStatic()) {
            throw new IncompatibleClassChangeError();
        }
        if (fieldActor.isFinal() && fieldActor.holder() != constantPool.holder()) {
            throw new IllegalAccessError();
        }
        guard.value = fieldActor;
    }

    /**
     * Resolves a constant pool entry denoting a static field that is being written to.
     *
     * This snippet is used when translating the {@link Bytecodes#PUTSTATIC} instruction.
     */
    @INLINE
    public static FieldActor resolveStaticFieldForWriting(ResolutionGuard.InPool guard) {
        if (guard.value == null) {
            resolveStaticFieldForWriting0(guard);
        }
        return UnsafeCast.asFieldActor(guard.value);
    }

    @NEVER_INLINE
    public static void resolveClass0(ResolutionGuard guard) {
        final ClassActor classActor;
        if (guard instanceof InPool) {
            InPool guardInPool = (InPool) guard;
            final ConstantPool constantPool = guardInPool.pool;
            final int index = guardInPool.cpi;
            classActor = constantPool.classAt(index).resolve(constantPool, index);
        } else {
            InAccessingClass guardInClass = (InAccessingClass) guard;
            classActor = guardInClass.resolve();
        }
        guard.value = classActor;
    }

    /**
     * Resolves a constant pool entry denoting a class that will be resolved to any arbitrary class type. That is, the
     * entry can denote an array type, interface or normal class.
     *
     * This snippet is used when translating the {@link Bytecodes#INSTANCEOF}, {@link Bytecodes#CHECKCAST},
     * {@link Bytecodes#LDC} and {@link Bytecodes#MULTIANEWARRAY} instructions.
     */
    @INLINE
    public static ClassActor resolveClass(ResolutionGuard guard) {
        if (guard.value == null) {
            resolveClass0(guard);
        }
        return UnsafeCast.asClassActor(guard.value);
    }

    @NEVER_INLINE
    private static void resolveClassForNew0(ResolutionGuard guard) {
        final ClassActor classActor;
        if (guard instanceof InPool) {
            InPool guardInPool = (InPool) guard;
            final ConstantPool constantPool = guardInPool.pool;
            final int index = guardInPool.cpi;
            classActor = constantPool.classAt(index).resolve(constantPool, index);
        } else {
            classActor = ((InAccessingClass) guard).resolve();
        }
        if (classActor.isAbstract() || classActor.isArrayClass()) {
            // Covers abstract classes and interfaces
            throw new InstantiationError();
        }
        guard.value = classActor;
    }

    /**
     * Resolves a constant pool entry denoting a class that specifies a class type that can be instantiated.
     *
     * This snippet is used when translating the {@link Bytecodes#NEW} instruction.
     */
    @INLINE
    public static ClassActor resolveClassForNew(ResolutionGuard guard) {
        if (guard.value == null) {
            resolveClassForNew0(guard);
        }
        return UnsafeCast.asClassActor(guard.value);
    }

    @NEVER_INLINE
    private static void resolveArrayClass0(ResolutionGuard guard) {
        final ClassActor arrayClassActor;
        if (guard instanceof InPool) {
            InPool guardInPool = (InPool) guard;
            final ConstantPool constantPool = guardInPool.pool;
            final int index = guardInPool.cpi;
            arrayClassActor = ArrayClassActor.forComponentClassActor(constantPool.classAt(index).resolve(constantPool, index));
        } else {
            arrayClassActor = ((InAccessingClass) guard).resolve();
        }
        guard.value = arrayClassActor;
    }

    /**
     * Resolves a constant pool entry denoting a class that specifies the component type of an object.
     * The resolved value returned by this snippet is the array type derived from the component type.
     *
     * This snippet is used when translating the {@link Bytecodes#ANEWARRAY} instruction.
     */
    @INLINE
    public static ArrayClassActor resolveArrayClass(ResolutionGuard guard) {
        if (guard.value == null) {
            resolveArrayClass0(guard);
        }
        return UnsafeCast.asArrayClassActor(guard.value);
    }

    @NEVER_INLINE
    private static void resolveInstanceFieldForReading0(ResolutionGuard.InPool guard) {
        final ConstantPool constantPool = guard.pool;
        final int index = guard.cpi;
        final FieldActor fieldActor = constantPool.fieldAt(index).resolve(constantPool, index);
        if (fieldActor.isStatic()) {
            throw new IncompatibleClassChangeError();
        }
        guard.value = fieldActor;
    }

    /**
     * Resolves a constant pool entry denoting an instance field that is being read.
     *
     * This snippet is used when translating the {@link Bytecodes#GETFIELD} instruction.
     */
    @INLINE
    public static FieldActor resolveInstanceFieldForReading(ResolutionGuard.InPool guard) {
        if (guard.value == null) {
            resolveInstanceFieldForReading0(guard);
        }
        return UnsafeCast.asFieldActor(guard.value);
    }

    @NEVER_INLINE
    private static void resolveInstanceFieldForWriting0(ResolutionGuard.InPool guard) {
        final ConstantPool constantPool = guard.pool;
        final int index = guard.cpi;
        final FieldActor fieldActor = constantPool.fieldAt(index).resolve(constantPool, index);
        if (fieldActor.isStatic()) {
            throw new IncompatibleClassChangeError();
        }
        if (fieldActor.isFinal() && fieldActor.holder() != constantPool.holder()) {
            throw new IllegalAccessError();
        }
        guard.value = fieldActor;

    }

    /**
     * Resolves a constant pool entry denoting an instance field that is being written to.
     *
     * This snippet is used when translating the {@link Bytecodes#PUTFIELD} instruction.
     */
    @INLINE
    public static FieldActor resolveInstanceFieldForWriting(ResolutionGuard.InPool guard) {
        if (guard.value == null) {
            resolveInstanceFieldForWriting0(guard);
        }
        return UnsafeCast.asFieldActor(guard.value);
    }

    /**
     * Resolves a constant pool entry denoting a static method that is being invoked.
     *
     * This snippet is used when translating the {@link Bytecodes#INVOKESTATIC} instruction.
     */
    @NEVER_INLINE
    private static void resolveStaticMethod0(ResolutionGuard.InPool guard) {
        final ConstantPool constantPool = guard.pool;
        final int index = guard.cpi;
        guard.value = constantPool.resolveInvokeStatic(index);
    }

    @INLINE
    public static StaticMethodActor resolveStaticMethod(ResolutionGuard.InPool guard) {
        if (guard.value == null) {
            resolveStaticMethod0(guard);
        }
        return UnsafeCast.asStaticMethodActor(guard.value);
    }

    @NEVER_INLINE
    private static void resolveSpecialMethod0(ResolutionGuard.InPool guard) {
        final ConstantPool constantPool = guard.pool;
        final int index = guard.cpi;
        guard.value = constantPool.resolveInvokeSpecial(index);
    }

    @INLINE
    public static VirtualMethodActor resolveSpecialMethod(ResolutionGuard.InPool guard) {
        if (guard.value == null) {
            resolveSpecialMethod0(guard);
        }
        return UnsafeCast.asVirtualMethodActor(guard.value);
    }

    @NEVER_INLINE
    private static void resolveVirtualMethod0(ResolutionGuard.InPool guard) {
        final ConstantPool constantPool = guard.pool;
        final int index = guard.cpi;

        final MethodActor methodActor = constantPool.resolveInvokeVirtual(index);
        if (methodActor.isInitializer()) {
            throw new VerifyError("<init> must be invoked with invokespecial");
        }
        guard.value = methodActor;
    }

    @INLINE
    public static VirtualMethodActor resolveVirtualMethod(ResolutionGuard.InPool guard) {
        if (guard.value == null) {
            resolveVirtualMethod0(guard);
        }
        return UnsafeCast.asVirtualMethodActor(guard.value);
    }

    @NEVER_INLINE
    public static void resolveInterfaceMethod0(ResolutionGuard.InPool guard) {
        final ConstantPool constantPool = guard.pool;
        final int index = guard.cpi;
        guard.value = constantPool.resolveInvokeInterface(index);
    }

    /**
     * Resolves a constant pool entry denoting a method that is being invoked "interfacially".
     *
     * This snippet is used when translating the {@link Bytecodes#INVOKEINTERFACE} instruction.
     *
     * Special note: While it is legal to invoke a virtual method declared in java.lang.Object with invokeinterface, an
     * assumption is made by this snippet that the verifier detects such cases and rewrites the instruction to use
     * invokevirtual instead.
     */
    @INLINE
    public static InterfaceMethodActor resolveInterfaceMethod(ResolutionGuard.InPool guard) {
        if (guard.value == null) {
            resolveInterfaceMethod0(guard);
        }
        return UnsafeCast.asInterfaceMethodActor(guard.value);
    }

    /**
     * Ensures that the class in which a given static method or field is declared is initialized, performing class
     * initialization if necessary.
     */
    @INLINE
    public static void makeHolderInitialized(MemberActor memberActor) {
        Snippets.makeClassInitialized(memberActor.holder());
    }

    /**
     * Ensures that a given class is initialized, performing class initialization if necessary.
     */
    @INLINE
    public static void makeClassInitialized(ClassActor classActor) {
        if (MaxineVM.isHosted()) {
            classActor.makeInitialized();
        } else if (!classActor.isInitialized()) {
            classActor.makeInitialized();
        }
    }

    /**
     * Produces an address corresponding to the entry point for the code of a given method
     * as compiled by the {@linkplain CallEntryPoint#OPTIMIZED_ENTRY_POINT optimizing compiler}.
     *
     * If the compiled code does not yet exist for the method, it is compiled with the
     * default compiler.
     */
    @INLINE
    public static Address makeEntrypoint(ClassMethodActor classMethodActor) {
        return CompilationScheme.Static.compile(classMethodActor, CallEntryPoint.OPTIMIZED_ENTRY_POINT);
    }

    /**
     * Finds the address of the native function for a {@code native} Java method.
     * <p>
     * This snippet does not subclass {@link NonFoldableSnippet} as it needs to be foldable when executing an
     * {@linkplain IrInterpreter IR interpreter}.
     */
    public static Word linkNativeMethod(ClassMethodActor classMethodActor) {
        return classMethodActor.nativeFunction.link();
    }

    static final VmThreadLocal NATIVE_CALLS_DISABLED = new VmThreadLocal("NATIVE_CALLS_DISABLED", false, "");

    /**
     * Disables calling native methods on the current thread. This state is recursive. That is,
     * natives calls are only re-enabled once {@link #enableNativeCallsForCurrentThread()} is
     * called the same number of times as this method has been called.
     *
     * It is a {@linkplain FatalError fatal error} if calls to this method and {@link #enableNativeCallsForCurrentThread()}
     * are unbalanced.
     *
     * Note: This feature is only provided as a debugging aid. It imposes an overhead (a test and branch on a VM thread local)
     * on every native call. It could be removed or disabled in a product build of the VM once GC is debugged.
     */
    public static void disableNativeCallsForCurrentThread() {
        final Address value = NATIVE_CALLS_DISABLED.load(currentTLA());
        NATIVE_CALLS_DISABLED.store3(value.plus(1));
    }

    /**
     * Re-enables calling native methods on the current thread. This state is recursive. That is,
     * native calls are only re-enabled once this method is called the same number of times as
     * {@link #disableNativeCallsForCurrentThread()} has been called.
     *
     * It is a {@linkplain FatalError fatal error} if calls to this method and {@link #disableNativeCallsForCurrentThread()}
     * are unbalanced.
     */
    public static void enableNativeCallsForCurrentThread() {
        final Address value = NATIVE_CALLS_DISABLED.load(currentTLA());
        if (value.isZero()) {
            FatalError.unexpected("Unbalanced calls to disable/enable native calls for current thread");
        }
        NATIVE_CALLS_DISABLED.store3(value.minus(1));
    }

    /**
     * Performs any operations necessary immediately before entering native code.
     */
    @INLINE
    public static void nativeCallPrologue() {
        Pointer etla = ETLA.load(currentTLA());
        Pointer previousAnchor = LAST_JAVA_FRAME_ANCHOR.load(etla);
        Pointer anchor = JavaFrameAnchor.create(getCpuStackPointer(), getCpuFramePointer(), Pointer.fromLong(here()), previousAnchor);

        nativeCallPrologue0(etla, anchor);
    }

    /**
     * Makes the transition from the 'in Java' state to the 'in native' state.
     *
     * @param etla the safepoints-triggered TLA for the current thread
     * @param anchor the value to which {@link VmThreadLocal#LAST_JAVA_FRAME_ANCHOR} will be set just before
     *            the transition is made
     */
    @INLINE
    public static void nativeCallPrologue0(Pointer etla, Word anchor) {
        if (!NATIVE_CALLS_DISABLED.load(currentTLA()).isZero()) {
            FatalError.unexpected("Calling native code while native calls are disabled");
        }

        // Update the last Java frame anchor for the current thread:
        LAST_JAVA_FRAME_ANCHOR.store(etla, anchor);

        if (UseCASBasedThreadFreezing) {
            MUTATOR_STATE.store(etla, THREAD_IN_NATIVE);
        } else {
            memopStore();
            // The following store must be last:
            MUTATOR_STATE.store(etla, THREAD_IN_NATIVE);
        }
    }

    /**
     * Performs any operations necessary immediately after returning from native code.
     */
    @INLINE
    public static void nativeCallEpilogue() {
        Pointer etla = ETLA.load(currentTLA());
        Pointer anchor = LAST_JAVA_FRAME_ANCHOR.load(etla);
        nativeCallEpilogue0(etla, PREVIOUS.get(anchor));
    }

    /**
     * Makes the transition from the 'in native' state to the 'in Java' state, blocking on a
     * spin lock if the current thread is {@linkplain VmOperation frozen}.
     *
     * @param etla the safepoints-triggered TLA for the current thread
     * @param anchor the value to which {@link VmThreadLocal#LAST_JAVA_FRAME_ANCHOR} will be set just after
     *            the transition is made
     */
    @INLINE
    public static void nativeCallEpilogue0(Pointer etla, Pointer anchor) {
        spinWhileFrozen(etla);
        LAST_JAVA_FRAME_ANCHOR.store(etla, anchor);
    }

    /**
     * This methods spins in a busy loop while the current thread is {@linkplain VmOperation frozen}.
     */
    @INLINE
    @NO_SAFEPOINTS("Cannot take a trap while frozen")
    private static void spinWhileFrozen(Pointer etla) {
        if (UseCASBasedThreadFreezing) {
            while (true) {
                if (MUTATOR_STATE.load(etla).equals(THREAD_IN_NATIVE)) {
                    if (etla.compareAndSwapWord(MUTATOR_STATE.offset, THREAD_IN_NATIVE, THREAD_IN_JAVA).equals(THREAD_IN_NATIVE)) {
                        break;
                    }
                } else {
                    if (MUTATOR_STATE.load(etla).equals(THREAD_IN_JAVA)) {
                        FatalError.unexpected("Thread transitioned itself from THREAD_IS_FROZEN to THREAD_IN_JAVA -- only the VM operation thread should do that");
                    }
                }
                Intrinsics.pause();
            }
        } else {
            while (true) {
                // Signal that we intend to go back into Java:
                MUTATOR_STATE.store(etla, THREAD_IN_JAVA);

                // Ensure that the freezer thread sees the above state transition:
                MemoryBarriers.storeLoad();

                // Ask if current thread is frozen:
                if (FROZEN.load(etla).isZero()) {
                    // If current thread is not frozen then the state transition above was valid (common case)
                    return;
                }

                // Current thread is frozen so above state transition is invalid
                // so undo it and spin until freezer thread thaws the current thread then retry transition
                MUTATOR_STATE.store(etla, THREAD_IN_NATIVE);
                while (!FROZEN.load(etla).isZero()) {
                    // Spin without doing unnecessary stores
                    Intrinsics.pause();
                }
            }
        }
    }

    /**
     * Saves information about the last Java caller for direct/C_FUNCTION calls.
     * Used by the Inspector for debugging.
     *
     * ATTENTION: If this is ever used for anything else than the inspector,
     *            use memory barriers properly.
     */
    @INLINE
    public static void nativeCallPrologueForC() {
        Pointer etla = ETLA.load(currentTLA());
        Pointer previousAnchor = LAST_JAVA_FRAME_ANCHOR.load(etla);
        Pointer anchor = JavaFrameAnchor.create(VMRegister.getCpuStackPointer(), VMRegister.getCpuFramePointer(), Pointer.fromLong(here()), previousAnchor);
        LAST_JAVA_FRAME_ANCHOR.store(etla, anchor);
    }

    @INLINE
    public static void nativeCallEpilogueForC() {
        Pointer etla = ETLA.load(currentTLA());
        Pointer anchor = LAST_JAVA_FRAME_ANCHOR.load(etla);
        LAST_JAVA_FRAME_ANCHOR.store(etla, PREVIOUS.get(anchor));
    }

    @INLINE
    public static void checkArrayDimension(int length) {
        if (length < 0) {
            throw new NegativeArraySizeException();
        }
    }

    @INLINE
    public static void checkCast(ClassActor classActor, Object object) {
        if (MaxineVM.isHosted()) {
            if (object != null && !classActor.toJava().isAssignableFrom(object.getClass())) {
                Throw.classCastException(classActor, object);
            }
        } else if (!classActor.isNullOrInstance(object)) {
            Throw.classCastException(classActor, object);
        }
    }

    @INLINE
    public static boolean instanceOf(ClassActor classActor, Object object) {
        if (MaxineVM.isHosted()) {
            return object != null && classActor.toJava().isAssignableFrom(object.getClass());
        }
        return classActor.isNonNullInstance(object);
    }

    @C_FUNCTION
    private static native double nativeDoubleRemainder(double dividend, double divisor);

    @INLINE
    public static double doubleRemainder(double dividend, double divisor) {
        if (MaxineVM.isHosted()) {
            return dividend % divisor;
        }
        return nativeDoubleRemainder(dividend, divisor);
    }

    @C_FUNCTION
    private static native float nativeFloatRemainder(float dividend, float divisor);

    @INLINE
    public static float floatRemainder(float dividend, float divisor) {
        if (MaxineVM.isHosted()) {
            return dividend % divisor;
        }
        return nativeFloatRemainder(dividend, divisor);
    }
}
