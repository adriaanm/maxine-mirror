/*
 * Copyright (c) 2009 Sun Microsystems, Inc.  All rights reserved.
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
package com.sun.max.vm.compiler.target;

import static com.sun.max.vm.compiler.CallEntryPoint.*;

import java.util.*;

import com.sun.max.annotate.*;
import com.sun.max.io.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.code.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.compiler.target.TargetBundleLayout.ArrayField;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.stack.StackFrameWalker.Cursor;
import com.sun.max.vm.stack.*;

/**
 * An adapter is a code stub interposing a call between two methods that have different calling conventions. The adapter
 * is called upon entry to the callee in the prologue specific to the {@linkplain CallEntryPoint entry point} used by
 * the caller. This allows a call between two methods that share the same calling convention to avoid an adapter
 * altogether.
 *
 * The adapter framework assumes there are exactly two calling conventions in use by the compilers in the VM. While the
 * details are platform specific, the {@linkplain CallEntryPoint#OPTIMIZED_ENTRY_POINT "OPT"} calling convention mostly
 * conforms to the C ABI of the underlying platform. The {@linkplain CallEntryPoint#JIT_ENTRY_POINT "JIT"} convention is
 * used by code that maintains an expression stack (much like an interpreter) and it uses two separate registers for a
 * frame pointer and a stack pointer. The frame pointer is used to access incoming arguments and local variables, and
 * the stack pointer is used to maintain the Java expression stack. All arguments to Java calls under the "JIT"
 * convention are passed via the Java expression stack.
 *
 * Return values are placed in a register under both conventions.
 *
 * @author Doug Simon
 * @author Laurent Daynes
 */
public abstract class Adapter extends TargetMethod {

    /**
     * The type of adaptation performed by an adapter. Each enum value denotes an ordered pair of two
     * different calling conventions. The platform specific details of each calling convention
     * are documented by the platform-specific subclasses of {@link AdapterGenerator}.
     *
     * @author Doug Simon
     */
    public enum Type {
        /**
         * Type of an adapter that interposes a call from code compiled with the "JIT" calling convention to
         * code compiled with the "OPT" calling convention.
         */
        JIT2OPT(JIT_ENTRY_POINT, OPTIMIZED_ENTRY_POINT),

        /**
         * Type of an adapter that interposes a call from code compiled with the "OPT" calling convention to
         * code compiled with the "JIT" calling convention.
         */
        OPT2JIT(OPTIMIZED_ENTRY_POINT, JIT_ENTRY_POINT);

        Type(CallEntryPoint caller, CallEntryPoint callee) {
            this.caller = caller;
            this.callee = callee;
        }

        /**
         * Denotes the calling convention of an adapter's caller.
         */
        public final CallEntryPoint caller;

        /**
         * Denotes the calling convention of an adapter's callee.
         */
        public final CallEntryPoint callee;
    }

    /**
     * The generator that produced this adapter.
     */
    @INSPECTED
    public final AdapterGenerator generator;

    /**
     * Creates an adapter and installs it in the code manager.
     *
     * @param generator the generator that produced the adapter
     * @param description a textual description of the adapter
     * @param frameSize the size of the adapter frame
     * @param code the adapter code
     * @param callPosition TODO
     */
    public Adapter(AdapterGenerator generator, String description, int frameSize, byte[] code, int callPosition) {
        super(description, CallEntryPoint.OPTIMIZED_ENTRY_POINT);
        this.setFrameSize(frameSize);
        this.generator = generator;

        final TargetBundleLayout targetBundleLayout = new TargetBundleLayout(0, 0, 0);
        targetBundleLayout.update(ArrayField.code, code.length);
        Code.allocate(targetBundleLayout, this);
        setData(null, null, code);
        setStopPositions(new int[] {callPosition}, NO_DIRECT_CALLEES, 1, 0);
    }

    public static final Object[] NO_DIRECT_CALLEES = {};

    @Override
    public void forwardTo(TargetMethod newTargetMethod) {
        FatalError.unexpected("Adapter should never be forwarded");
    }

    @Override
    public void gatherCalls(Set<MethodActor> directCalls, Set<MethodActor> virtualCalls, Set<MethodActor> interfaceCalls, Set<MethodActor> inlinedMethods) {
    }

    @Override
    public boolean isPatchableCallSite(Address callSite) {
        FatalError.unexpected("Adapter should never be patched");
        return false;
    }

    @Override
    public void fixupCallSite(int callOffset, Address callEntryPoint) {
        FatalError.unexpected("Adapter should never be patched");
    }

    @Override
    public void patchCallSite(int callOffset, Address callEntryPoint) {
        FatalError.unexpected("Adapter should never be patched");
    }

    @Override
    public Address throwAddressToCatchAddress(boolean isTopFrame, Address throwAddress, Class<? extends Throwable> throwableClass) {
        if (isTopFrame) {
            throw FatalError.unexpected("Exception occurred in frame adapter");
        }
        return Address.zero();
    }

    @Override
    public void traceDebugInfo(IndentWriter writer) {
    }

    @Override
    public void traceExceptionHandlers(IndentWriter writer) {
    }

    @Override
    public byte[] referenceMaps() {
        return null;
    }

    @Override
    public void prepareReferenceMap(Cursor current, Cursor callee, StackReferenceMapPreparer preparer) {
    }

    @Override
    public void catchException(Cursor current, Cursor callee, Throwable throwable) {
        // Exceptions do not occur in adapters
    }
}
