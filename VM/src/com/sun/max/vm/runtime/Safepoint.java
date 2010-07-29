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
package com.sun.max.vm.runtime;

import static com.sun.max.vm.thread.VmThreadLocal.*;

import java.lang.reflect.*;
import java.util.*;

import com.sun.max.*;
import com.sun.max.annotate.*;
import com.sun.max.collect.*;
import com.sun.max.memory.*;
import com.sun.max.program.*;
import com.sun.max.unsafe.*;
import com.sun.max.util.*;
import com.sun.max.vm.*;
import com.sun.max.vm.compiler.builtin.*;
import com.sun.max.vm.thread.*;

/**
 * The platform specific details of the mechanism by which a thread can be
 * {@linkplain FreezeThreads frozen} via polling at prudently
 * chosen execution points.
 *
 * @author Bernd Mathiske
 * @author Doug Simon
 * @author Hannes Payer
 * @author Paul Caprioli
 * @author Mick Jordan
 */
public abstract class Safepoint {

    /**
     * The three states a thread can be in with respect to safepoints.
     * Note that the order of these enum matches the layout of the three
     * {@linkplain VmThreadLocal thread local areas}.
     */
    public enum State implements PoolObject {
        TRIGGERED(SAFEPOINTS_TRIGGERED_THREAD_LOCALS),
        ENABLED(SAFEPOINTS_ENABLED_THREAD_LOCALS),
        DISABLED(SAFEPOINTS_DISABLED_THREAD_LOCALS);

        public static final List<State> CONSTANTS = Arrays.asList(values());

        private final VmThreadLocal key;

        State(VmThreadLocal key) {
            this.key = key;
        }

        public int serial() {
            return ordinal();
        }

        public int offset() {
            return key.offset;
        }
    }

    @HOSTED_ONLY
    public static Safepoint create(VMConfiguration vmConfiguration) {
        try {
            final String isa = vmConfiguration.platform().processorKind.instructionSet.name();
            final Class<?> safepointClass = Class.forName(MaxPackage.fromClass(Safepoint.class).subPackage(isa.toLowerCase()).name() + "." + isa + Safepoint.class.getSimpleName());
            final Constructor<?> constructor = safepointClass.getConstructor(VMConfiguration.class);
            return (Safepoint) constructor.newInstance(vmConfiguration);
        } catch (Exception exception) {
            exception.printStackTrace();
            throw ProgramError.unexpected("could not create safepoint: " + exception);
        }
    }

    public final byte[] code = createCode();

    protected Safepoint() {
    }

    /**
     * Gets the current value of the safepoint latch register.
     */
    @INLINE
    public static Pointer getLatchRegister() {
        return VMRegister.getSafepointLatchRegister();
    }

    /**
     * Updates the current value of the safepoint latch register.
     *
     * @param vmThreadLocals a pointer to a copy of the thread locals from which the base of the safepoints-enabled
     *            thread locals can be obtained
     */
    @INLINE
    public static void setLatchRegister(Pointer vmThreadLocals) {
        VMRegister.setSafepointLatchRegister(vmThreadLocals);
    }

    /**
     * Determines if safepoints are disabled for the current thread.
     * @return {@code true} if safepoints are disabled
     */
    public static boolean isDisabled() {
        return getLatchRegister().equals(SAFEPOINTS_DISABLED_THREAD_LOCALS.getConstantWord().asPointer());
    }

    /**
     * Determines if safepoints are triggered for the current thread.
     * @return {@code true} if safepoints are triggered
     */
    @INLINE
    public static boolean isTriggered() {
        return !MaxineVM.isHosted() && SAFEPOINT_LATCH.getVariableWord().equals(SAFEPOINTS_TRIGGERED_THREAD_LOCALS.getConstantWord());
    }

    /**
     * Disables safepoints for the current thread. To temporarily disable safepoints on a thread, a call to this method
     * should paired with a call to {@link #enable()}, passing the value returned by the former as the single
     * parameter to the later. That is:
     * <pre>
     *     boolean wasDisabled = Safepoint.disable();
     *     // perform action with safepoints disabled
     *     if (!wasDisabled) {
     *         Safepoints.enable();
     *     }
     * </pre>
     *
     * @return true if this call caused safepoints to be disabled (i.e. they were enabled upon entry to this method)
     */
    @INLINE
    public static boolean disable() {
        final boolean wasDisabled = getLatchRegister().equals(SAFEPOINTS_DISABLED_THREAD_LOCALS.getConstantWord().asPointer());
        setLatchRegister(SAFEPOINTS_DISABLED_THREAD_LOCALS.getConstantWord().asPointer());
        return wasDisabled;
    }

    /**
     * Enables safepoints for the current thread, irrespective of whether or not they are currently enabled.
     *
     * @see #disable()
     */
    @INLINE
    public static void enable() {
        setLatchRegister(SAFEPOINTS_ENABLED_THREAD_LOCALS.getConstantWord().asPointer());
    }

    public static void initializePrimordial(Pointer primordialVmThreadLocals) {
        primordialVmThreadLocals.setWord(SAFEPOINTS_ENABLED_THREAD_LOCALS.index, primordialVmThreadLocals);
        primordialVmThreadLocals.setWord(SAFEPOINTS_DISABLED_THREAD_LOCALS.index, primordialVmThreadLocals);
        primordialVmThreadLocals.setWord(SAFEPOINTS_TRIGGERED_THREAD_LOCALS.index, primordialVmThreadLocals);
        primordialVmThreadLocals.setWord(SAFEPOINT_LATCH.index, primordialVmThreadLocals);
        Safepoint.setLatchRegister(primordialVmThreadLocals);
    }

    /**
     * Emits a safepoint at the call site.
     */
    @INLINE
    public static void safepoint() {
        SafepointBuiltin.safepointBuiltin();
    }

    public abstract Symbol latchRegister();

    @HOSTED_ONLY
    protected abstract byte[] createCode();

    public boolean isAt(Pointer instructionPointer) {
        return Memory.equals(instructionPointer, code);
    }
}
