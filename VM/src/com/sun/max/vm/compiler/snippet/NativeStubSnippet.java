/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.max.vm.compiler.snippet;

import static com.sun.max.vm.runtime.VmOperation.*;
import static com.sun.max.vm.runtime.VMRegister.*;
import static com.sun.max.vm.stack.JavaFrameAnchor.*;
import static com.sun.max.vm.thread.VmThread.*;
import static com.sun.max.vm.thread.VmThreadLocal.*;

import com.sun.cri.bytecode.Bytecodes.MemoryBarriers;
import com.sun.max.annotate.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.compiler.builtin.*;
import com.sun.max.vm.jni.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.stack.*;
import com.sun.max.vm.thread.*;

/**
 * Snippets that are used in {@linkplain NativeStubGenerator native method stubs}.
 *
 * @author Doug Simon
 * @author Hannes Payer
 */
public abstract class NativeStubSnippet extends Snippet {

    /**
     * Finds the address of the native function for a {@code native} Java method.
     * <p>
     * This snippet does not subclass {@link NonFoldableSnippet} as it needs to be foldable when executing an
     * {@linkplain IrInterpreter IR interpreter}.
     */
    public static final class LinkNativeMethod extends Snippet {
        @SNIPPET
        public static Word linkNativeMethod(ClassMethodActor classMethodActor) {
            return classMethodActor.nativeFunction.link();
        }

        public static final LinkNativeMethod SNIPPET = new LinkNativeMethod();
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
    public static final class NativeCallPrologue extends NativeStubSnippet {
        @SNIPPET
        @INLINE
        public static void nativeCallPrologue() {
            Pointer etla = ETLA.load(currentTLA());
            Pointer previousAnchor = LAST_JAVA_FRAME_ANCHOR.load(etla);
            Pointer anchor = JavaFrameAnchor.create(getCpuStackPointer(), getCpuFramePointer(), getInstructionPointer(), previousAnchor);

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
                MemoryBarriers.memopStore();
                // The following store must be last:
                MUTATOR_STATE.store(etla, THREAD_IN_NATIVE);
            }
        }

        public static final NativeCallPrologue SNIPPET = new NativeCallPrologue();
    }

    /**
     * Performs any operations necessary immediately after returning from native code.
     */
    public static final class NativeCallEpilogue extends NativeStubSnippet {
        @SNIPPET
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
                    SpecialBuiltin.pause();
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
                        SpecialBuiltin.pause();
                    }
                }
            }
        }

        public static final NativeCallEpilogue SNIPPET = new NativeCallEpilogue();
    }

    /**
     * Saves information about the last Java caller for direct/C_FUNCTION calls.
     * Used by the Inspector for debugging.
     *
     * ATTENTION: If this is ever used for anything else than the inspector,
     *            use memory barriers properly.
     */
    public static final class NativeCallPrologueForC extends NativeStubSnippet {
        @SNIPPET
        @INLINE
        public static void nativeCallPrologueForC() {
            Pointer etla = ETLA.load(currentTLA());
            Pointer previousAnchor = LAST_JAVA_FRAME_ANCHOR.load(etla);
            Pointer anchor = JavaFrameAnchor.create(VMRegister.getCpuStackPointer(), VMRegister.getCpuFramePointer(), VMRegister.getInstructionPointer(), previousAnchor);
            LAST_JAVA_FRAME_ANCHOR.store(etla, anchor);
        }

        public static final NativeCallPrologueForC SNIPPET = new NativeCallPrologueForC();
    }

    public static final class NativeCallEpilogueForC extends NativeStubSnippet {
        @SNIPPET
        @INLINE
        public static void nativeCallEpilogueForC() {
            Pointer etla = ETLA.load(currentTLA());
            Pointer anchor = LAST_JAVA_FRAME_ANCHOR.load(etla);
            LAST_JAVA_FRAME_ANCHOR.store(etla, PREVIOUS.get(anchor));
        }

        public static final NativeCallEpilogueForC SNIPPET = new NativeCallEpilogueForC();
    }
}
