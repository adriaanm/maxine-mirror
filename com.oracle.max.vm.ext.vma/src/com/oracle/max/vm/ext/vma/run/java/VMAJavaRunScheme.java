/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.vm.ext.vma.run.java;

import com.oracle.max.vm.ext.vma.*;
import com.oracle.max.vm.ext.vma.options.*;
import com.oracle.max.vm.ext.vma.runtime.*;
import com.sun.max.annotate.*;
import com.sun.max.memory.VirtualMemory;
import com.sun.max.program.ProgramError;
import com.sun.max.unsafe.*;
import com.sun.max.vm.MaxineVM;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.run.java.JavaRunScheme;
import com.sun.max.vm.thread.VmThread;
import com.sun.max.vm.thread.VmThreadLocal;

/**
 * Variant of {@link JavaRunScheme} that supports the VMA framework.
 *
 */

public class VMAJavaRunScheme extends JavaRunScheme {
    /**
     * A thread local variable that is used to support VM advising, in
     * particular to indicate whether tracking is currently enabled.
     */
    public static final VmThreadLocal VM_ADVISING = new VmThreadLocal(
            "VM_ADVISING", false, "For use by VM advising framework");

    /**
     * When after-INVOKE advice is requested, this thread local saves the
     * method actor that was computed prior to the call.
     */
    public static final VmThreadLocal VMA_METHODACTOR = new VmThreadLocal(
            "VMA_METHODACTOR", true, "Saved MethodActor value for VMA");

    /**
     * When after-INVOKE advice is requested, this thread local saves the
     * receiver object that was used in the call.
     */
    public static final VmThreadLocal VMA_METHODRECEIVER = new VmThreadLocal(
                    "VMA_METHODRECEIVER", true, "Saved method receiver value for VMA");

    /**
     * A thread local variable that is used to support VM advising, in
     * particular the native buffer for per thread event storage.
     */
    public static final VmThreadLocal VM_ADVISING_BUFFER = new VmThreadLocal(
            "VM_ADVISING_BUFFER", false, "For use by VM advising framework");

    private static final ObjectStateHandler state = BitSetObjectStateHandler.create();

    private static final int BUFFER_SIZE = 64 * 1024;

    /**
     * Set to true when {@link VMAOptions.VMA} is set AND the VM is in a state to start advising.
     */
    private static boolean advising;

    /**
     * The build time specified {@link VMAdviceHandler}.
     */
    @CONSTANT_WHEN_NOT_ZERO
    private static VMAdviceHandler adviceHandler;

    /**
     * This property must be specified at boot image time.
     */
    private static final String VMA_HANDLER_CLASS_PROPERTY = "max.vma.handler";
    private static final String DEFAULT_HANDLER_CLASS = "com.oracle.max.vm.ext.vma.runtime.SyncLogVMAdviceHandler";

    @Override
    public void initialize(MaxineVM.Phase phase) {
        super.initialize(phase);
        if (phase == MaxineVM.Phase.BOOTSTRAPPING) {
            try {
                String handlerClassName = System.getProperty(VMA_HANDLER_CLASS_PROPERTY);
                if (handlerClassName == null) {
                    handlerClassName = DEFAULT_HANDLER_CLASS;
                }
                adviceHandler = (VMAdviceHandler) Class.forName(handlerClassName).newInstance();
            } catch (Throwable ex) {
                ProgramError.unexpected("failed to instantiate VMA advice handler class: ", ex);
            }
        }
        if (phase == MaxineVM.Phase.RUNNING) {
            if (VMAOptions.VMA) {
                adviceHandler.initialise(state);
                advising = true;
                threadStarting();
            }
        } else if (phase == MaxineVM.Phase.TERMINATING) {
            if (advising) {
                disableAdvising();
                // N.B. daemon threads may still be running and invoking advice.
                // There is nothing we can do about that as they may be in the act
                // of logging so disabling advising for them would be meaningless.
                adviceHandler.finalise();
            }
        }
    }

    @INLINE
    public static VMAdviceHandler adviceHandler() {
        return adviceHandler;
    }

    /**
     * If the VM is being run with advising turned on, enables advising for the current thread.
     */
    public static void threadStarting() {
        if (advising) {
            Pointer buffer = VirtualMemory.allocate(Size.fromInt(BUFFER_SIZE), VirtualMemory.Type.DATA);
            assert !buffer.isZero();
            VM_ADVISING_BUFFER.store3(buffer);
            adviceHandler.adviseBeforeThreadStarting(VmThread.current());
            enableAdvising();
        }
    }

    /**
     * If the VM is being run with advising turned on, notifies the advisee that this thread is terminating.
     */
    public static void threadTerminating() {
        if (advising) {
            adviceHandler.adviseBeforeThreadTerminating(VmThread.current());
        }
    }

    /**
     * Unconditionally enables advising for the current thread.
     */
    @INLINE
    public static void enableAdvising() {
        VM_ADVISING.store3(Address.fromLong(1));
    }

    /**
     * Unconditionally disables advising for the current thread.
     */
    @INLINE
    public static void disableAdvising() {
        VM_ADVISING.store3(Word.zero());
    }

    /**
     * Is the VM being run with advising turned on?
     */
    @INLINE
    public static boolean isVMAdvising() {
        return advising;
    }

    /**
     * Is advising enabled for the current thread?
     * @return
     */
    @INLINE
    public static boolean isAdvising() {
        return VmThread.currentTLA().getWord(VM_ADVISING.index) != Word.zero();
    }

    @INLINE
    public static void saveMethodActor(MethodActor methodActor) {
        VmThread.currentTLA().setReference(VMA_METHODACTOR.index, Reference.fromJava(methodActor));
    }

    @INLINE
    public static void saveReceiver(Reference receiver) {
        VmThread.currentTLA().setReference(VMA_METHODRECEIVER.index, receiver);
    }

    @INLINE
    public static void saveMethodActorAndReceiver(Reference receiver, MethodActor methodActor) {
        Pointer tla = VmThread.currentTLA();
        tla.setReference(VMA_METHODACTOR.index, Reference.fromJava(methodActor));
        tla.setReference(VMA_METHODRECEIVER.index, receiver);
    }

    @INLINE
    public static MethodActor loadMethodActor() {
        return UnsafeCast.asClassMethodActor(VmThread.currentTLA().getReference(VMA_METHODACTOR.index).toJava());
    }

    @INLINE
    public static Object loadReceiver() {
        return VmThread.currentTLA().getReference(VMA_METHODRECEIVER.index).toJava();
    }


}
