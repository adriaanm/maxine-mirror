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
package com.sun.max.vm.jvmti;

import static com.sun.max.vm.MaxineVM.*;

import java.util.*;

import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.compiler.RuntimeCompiler.*;
import com.sun.max.vm.compiler.deopt.*;
import com.sun.max.vm.compiler.deps.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.jvmti.JVMTIThreadFunctions.*;
import com.sun.max.vm.thread.*;

/**
 * Everything concerned with modifications to compiled code for JVMTI code events.
 */
public class JVMTICode {

    /**
     * Handle any change in compiled code for the methods on the (logical) call stack
     * necessary for the pervasive events such as SINGLE_STEP, FRAME_POP, for a single thread.
     * This check occurs just before the thread is resumed.
     */
    private static void checkDeOptForEvent(JVMTI.Env jvmtiEnv, VmThread vmThread) {
        long codeEventSettings = JVMTIEvent.codeEventSettings(jvmtiEnv, vmThread);
        if (codeEventSettings != 0) {
            SingleThreadStackTraceVmOperation op = new FindAppFramesStackTraceOperation(vmThread).submitOp();
            // we only deopt the top frame, which means we need to handle leaving the frame later
            checkDeOptForMethod(op.stackTraceVisitor.getStackElement(0).classMethodActor, codeEventSettings);
        } else {
            // is it worth reopting? perhaps if we are resuming without, say, single step set and
            // the code contains single step event calls. They won't be delivered but they reduce
            // performance noticeably.
        }
    }

    static void checkDeOptForMethod(ClassMethodActor classMethodActor, long codeEventSettings) {
        TargetMethod targetMethod = classMethodActor.currentTargetMethod();
        // we check here if the code is already adequate for the settings we want
        if (targetMethod.jvmtiCheck(codeEventSettings, JVMTIBreakpoints.getBreakpoints(classMethodActor))) {
            return;
        }
        ArrayList<TargetMethod> targetMethods = new ArrayList<TargetMethod>();
        targetMethod.finalizeReferenceMaps();
        targetMethods.add(targetMethod);
        // Calling this multiple times for different threads is harmless as it takes care to
        // filter out already invalidated methods.
        new Deoptimization(targetMethods).go();
    }

    /**
     * A new breakpoint is being set.
     * @param classMethodActor
     */
    static void deOptForNewBreakpoint(ClassMethodActor classMethodActor) {
        TargetMethod targetMethod = classMethodActor.currentTargetMethod();
        if (targetMethod != null) {
            // It was compiled already, need to recompile
            // Potentially need to deopt the code in all threads, but note that it
            // may not be active on any thread stack, in which case we just need to recompile
            // and patch call sites. If it is not active, Deoptimization.go does
            // invalidation but does not recompile the method.
            long codeEventSettings = JVMTIEvent.codeEventSettings(null, null);
            checkDeOptForMethod(classMethodActor, codeEventSettings);
            // recheck
            targetMethod = classMethodActor.currentTargetMethod();
            if (!targetMethod.jvmtiCheck(codeEventSettings, JVMTIBreakpoints.getBreakpoints(classMethodActor))) {
                // Wasn't active so didn't get recompiled, or a previous baseline was picked up by deopt
                vm().compilationBroker.compile(classMethodActor, Nature.BASELINE);
            }
        } else {
            // There are three possibilities in this case
            // 1. It was inlined everywhere so never compiled in isolation.
            // 2. It was inlined somewhere but also compiled in isolation
            // 3. It has never been compiled or inlined.
            // Have to check the inline dependencies to distinguish these cases.
            ArrayList<TargetMethod> inliners = DependenciesManager.getInliners(classMethodActor);
            if (inliners.size() == 0) {
                // Case 3 requires nothing to be done as the breakpoint will be
                // added when the method is compiled.
                return;
            } else {
                // all the inliners need to be deopted and the inlinee needs to be (re)compiled
                new Deoptimization(inliners).go();
                vm().compilationBroker.compile(classMethodActor, Nature.BASELINE);
            }
        }
    }

    static void resumeThreadNotify(JVMTI.Env jvmtiEnv, VmThread vmThread) {
        checkDeOptForEvent(jvmtiEnv, vmThread);
    }

    static void suspendThreadNotify(JVMTI.Env jvmtiEnv, VmThread vmThread) {
    }

    static void resumeThreadListNotify(JVMTI.Env jvmtiEnv, Set<VmThread> set) {
        for (VmThread vmThread : set) {
            resumeThreadNotify(jvmtiEnv, vmThread);
        }
    }

    static void suspendThreadListNotify(JVMTI.Env jvmtiEnv, Set<VmThread> set) {
        for (VmThread vmThread : set) {
            suspendThreadNotify(jvmtiEnv, vmThread);
        }
    }
}
