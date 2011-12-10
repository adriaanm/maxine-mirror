/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.tele.debug;

import java.io.*;
import java.util.*;

import com.sun.max.tele.*;
import com.sun.max.tele.debug.VmTargetBreakpoint.TargetBreakpointManager;
import com.sun.max.tele.method.*;
import com.sun.max.tele.method.CodeLocation.MachineCodeLocation;
import com.sun.max.tele.method.CodeLocation.*;
import com.sun.max.tele.util.*;

/**
 * Singleton access to breakpoint creation and management in the VM.
 */
public class VmBreakpointManager extends AbstractVmHolder implements MaxBreakpointManager, TeleVMCache {

    private static final int TRACE_VALUE = 1;

    private static VmBreakpointManager breakpointManager;

    public static VmBreakpointManager make(TeleVM vm) {
        if (breakpointManager == null) {
            breakpointManager = new VmBreakpointManager(vm);
        }
        return breakpointManager;
    }

    private final VmBytecodeBreakpoint.BytecodeBreakpointManager bytecodeBreakpointManager;
    private final VmTargetBreakpoint.TargetBreakpointManager targetBreakpointManager;
    private final TimedTrace updateTracer;

    // Thread-safe, immutable list.  Will be read many, many more times than will change.
    private volatile List<MaxBreakpoint> breakpointCache = Collections.emptyList();

    private VmBreakpointManager(TeleVM vm) {
        super(vm);
        this.bytecodeBreakpointManager = VmBytecodeBreakpoint.makeManager(vm);
        this.targetBreakpointManager = VmTargetBreakpoint.makeManager(vm);
        this.updateTracer = new TimedTrace(TRACE_VALUE, tracePrefix() + "updating");
        rebuildBreakpointCache();
        addListener(new MaxBreakpointListener() {

            public void breakpointsChanged() {
                rebuildBreakpointCache();
            }

            public void breakpointToBeDeleted(MaxBreakpoint breakpoint, String reason) {
            }

        });
    }

    public void addListener(MaxBreakpointListener listener) {
        targetBreakpointManager.addListener(listener);
        bytecodeBreakpointManager.addListener(listener);
    }

    public void removeListener(MaxBreakpointListener listener) {
        targetBreakpointManager.removeListener(listener);
        bytecodeBreakpointManager.removeListener(listener);
    }

    public VmBreakpoint makeBreakpoint(MaxCodeLocation maxCodeLocation) throws MaxVMBusyException {
        final CodeLocation codeLocation = (CodeLocation) maxCodeLocation;
        if (maxCodeLocation.hasAddress()) {
            return targetBreakpointManager.makeClientBreakpoint(codeLocation);
        }
        return bytecodeBreakpointManager.makeClientBreakpoint(codeLocation);
    }

    public VmBreakpoint findBreakpoint(MaxCodeLocation maxCodeLocation) {
        if (maxCodeLocation instanceof MachineCodeLocation) {
            final MachineCodeLocation compiledCodeLocation = (MachineCodeLocation) maxCodeLocation;
            return targetBreakpointManager.findClientBreakpoint(compiledCodeLocation.codePointer());
        }
        final BytecodeLocation methodCodeLocation = (BytecodeLocation) maxCodeLocation;
        return bytecodeBreakpointManager.findClientBreakpoint(methodCodeLocation);
    }

    public List<MaxBreakpoint> breakpoints() {
        return breakpointCache;
    }

    public void writeSummary(PrintStream printStream) {
        targetBreakpointManager.writeSummaryToStream(printStream);
        bytecodeBreakpointManager.writeSummaryToStream(printStream);
    }

    public void updateCache(long epoch) {
        updateTracer.begin();
        targetBreakpointManager.updateCache(epoch);
        bytecodeBreakpointManager.updateCache(epoch);
        updateTracer.end();
    }

    public VmTargetBreakpoint makeSystemTargetBreakpoint(CodeLocation codeLocation, VMTriggerEventHandler triggerEventHandler) throws MaxVMBusyException {
        return targetBreakpointManager.makeSystemBreakpoint(codeLocation, triggerEventHandler);
    }

    /**
     * @return access to low level functionality concerning machine code breakpoints.
     */
    TargetBreakpointManager targetBreakpoints() {
        return targetBreakpointManager;
    }

    /**
     * Recomputes the immutable list cache of all client breakpoints.
     */
    private void rebuildBreakpointCache() {
        final List<MaxBreakpoint> newBreakpointsCache = new  ArrayList<MaxBreakpoint>(targetBreakpointManager.clientBreakpoints());
        for (MaxBreakpoint breakpoint : bytecodeBreakpointManager.clientBreakpoints()) {
            newBreakpointsCache.add(breakpoint);
        }
        breakpointCache = Collections.unmodifiableList(newBreakpointsCache);
    }

}
