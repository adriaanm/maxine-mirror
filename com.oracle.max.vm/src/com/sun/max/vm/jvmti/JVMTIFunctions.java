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
package com.sun.max.vm.jvmti;

import static com.sun.max.vm.jvmti.JVMTI.*;
import static com.sun.max.vm.jvmti.JVMTICapabilities.*;
import static com.sun.max.vm.jvmti.JVMTIConstants.*;
import static com.sun.max.vm.jvmti.JVMTIEnvNativeStruct.*;
import static com.sun.max.vm.jni.JniFunctions.epilogue;

import java.util.*;
import java.util.regex.*;

import com.sun.max.annotate.*;
import com.sun.max.memory.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.jni.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.thread.*;

/**
 * The transformed form of {@link JVMTIFunctionsSource}.
 * This file is read-only to all but {@link JVMTIFunctionsGenerator}.
 * Do not add code here, add it to the appropriate implementation class.
 */
public class JVMTIFunctions  {
    static {
        VMOptions.addFieldOption("-XX:", "TraceJVMTI", "Trace JVMTI calls.");
        VMOptions.addFieldOption("-XX:", "TraceJVMTIInclude", "list of methods to include");
        VMOptions.addFieldOption("-XX:", "TraceJVMTIExclude", "list of methods to exclude");
    }

    static boolean TraceJVMTI;
    static String TraceJVMTIInclude;
    static String TraceJVMTIExclude;

    /* The standard JNI entry prologue uses the fact that the jni env value is a slot in the
     * thread local storage area in order to reset the safepoint latch register
     * on an upcall, by indexing back to the base of the storage area.
     *
     * A jvmti env value is agent-specific and can be used across threads.
     * Therefore it cannot have a stored jni env value since that is thread-specific.
     * So we load the current value of the TLA from the native thread control control block.
     * and we use a special variant of C_FUNCTION that does not do anything with the
     * safepoint latch register, since it isn't valid at this point.
     *
     * One possible problem: if the TLA has been set to triggered or disabled this will be wrong.
     * I believe this could only happen in the case of a callback from such a state which is unlikely.
     * However, the callback typically passes the jni env as well as the jvmti env so,if this is an issue,
     * there should be some way to cache the jni env value on the way down and use it on any nested
     * upcalls.

     * TODO handle the (error) case of an upcall from an unattached thread, which will not
     * have a valid TLA in its native thread control control block.
     */

    @C_FUNCTION
    private static native Pointer currentJniEnv();

    public static final ClassMethodActor currentJniEnv;

    static {
        CriticalNativeMethod cnm = new CriticalNativeMethod(JVMTIFunctions.class, "currentJniEnv");
        currentJniEnv = cnm.classMethodActor;
    }

    @INLINE
    static Pointer prologue(Pointer env) {
        return JniFunctions.prologue(currentJniEnv());
    }

    @INLINE
    static void tracePrologue(String name, Pointer anchor) {
        if (TraceJVMTI) {
            if (methodTraceStates == null || methodTraceStates.get(name)) {
                JniFunctions.traceEntry(name, "JVMTI", anchor);
            }
        }
    }

    @INLINE
    static void traceEpilogue(String name) {
        if (TraceJVMTI) {
            boolean lockDisabledSafepoints = Log.lock();
            if (methodTraceStates == null || methodTraceStates.get(name)) {
                JniFunctions.traceExitNoLock(name, "JVMTI");
            }
            Log.unlock(lockDisabledSafepoints);
        }
    }

    static Map<String, Boolean> methodTraceStates;

    /**
     * Called once the VM is up to check for limitations on JVMTI tracing.
     *
     */
    static void checkTracing() {
        if (TraceJVMTIInclude != null || TraceJVMTIExclude != null) {
            methodTraceStates = new HashMap<String, Boolean>();
            for (String methodName : methodNames) {
                methodTraceStates.put(methodName, TraceJVMTIInclude == null ? true : false);
            }
            if (TraceJVMTIInclude != null) {
                Pattern inclusionPattern = Pattern.compile(TraceJVMTIInclude);
                for (String methodName : methodNames) {
                    if (inclusionPattern.matcher(methodName).matches()) {
                        methodTraceStates.put(methodName, true);
                    }
                }
            }
            if (TraceJVMTIExclude != null) {
                Pattern exclusionPattern = Pattern.compile(TraceJVMTIExclude);
                for (String methodName : methodNames) {
                    if (exclusionPattern.matcher(methodName).matches()) {
                        methodTraceStates.put(methodName, false);
                    }
                }
            }
        }
    }

 // Checkstyle: stop method name check

// START GENERATED CODE

    private static final boolean INSTRUMENTED = false;

    @VM_ENTRY_POINT
    private static native void reserved1();
        // Source: JVMTIFunctionsSource.java:100

    @VM_ENTRY_POINT
    private static int SetEventNotificationMode(Pointer env, int mode, int event_type, JniHandle event_thread) {
        // Source: JVMTIFunctionsSource.java:103
        Pointer anchor = prologue(env);
        tracePrologue("SetEventNotificationMode", anchor);
        try {
            if (!(phase == JVMTI_PHASE_ONLOAD || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            return JVMTIEvent.setEventNotificationMode(env, mode, event_type, event_thread);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("SetEventNotificationMode");
        }
    }

    @VM_ENTRY_POINT
    private static native void reserved3();
        // Source: JVMTIFunctionsSource.java:109

    @VM_ENTRY_POINT
    private static int GetAllThreads(Pointer env, Pointer threads_count_ptr, Pointer threads_ptr) {
        // Source: JVMTIFunctionsSource.java:112
        Pointer anchor = prologue(env);
        tracePrologue("GetAllThreads", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (threads_count_ptr.isZero() || threads_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            return JVMTIThreadFunctions.getAllThreads(threads_count_ptr, threads_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetAllThreads");
        }
    }

    @VM_ENTRY_POINT
    private static int SuspendThread(Pointer env, JniHandle thread) {
        // Source: JVMTIFunctionsSource.java:119
        Pointer anchor = prologue(env);
        tracePrologue("SuspendThread", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (!(CAN_SUSPEND.get(CAPABILITIES.getPtr(env)))) {
                return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
            }
            Thread handleAsThread;
            try {
                handleAsThread = (Thread) thread.unhand();
                if (handleAsThread == null) {
                    return JVMTI_ERROR_INVALID_THREAD;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_THREAD;
            }
            return JVMTIThreadFunctions.suspendThread(handleAsThread);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("SuspendThread");
        }
    }

    @VM_ENTRY_POINT
    private static int ResumeThread(Pointer env, JniHandle thread) {
        // Source: JVMTIFunctionsSource.java:127
        Pointer anchor = prologue(env);
        tracePrologue("ResumeThread", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (!(CAN_SUSPEND.get(CAPABILITIES.getPtr(env)))) {
                return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
            }
            Thread handleAsThread;
            try {
                handleAsThread = (Thread) thread.unhand();
                if (handleAsThread == null) {
                    return JVMTI_ERROR_INVALID_THREAD;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_THREAD;
            }
            return JVMTIThreadFunctions.resumeThread(handleAsThread);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("ResumeThread");
        }
    }

    @VM_ENTRY_POINT
    private static int StopThread(Pointer env, JniHandle thread, JniHandle exception) {
        // Source: JVMTIFunctionsSource.java:135
        Pointer anchor = prologue(env);
        tracePrologue("StopThread", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("StopThread");
        }
    }

    @VM_ENTRY_POINT
    private static int InterruptThread(Pointer env, JniHandle thread) {
        // Source: JVMTIFunctionsSource.java:140
        Pointer anchor = prologue(env);
        tracePrologue("InterruptThread", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (!(CAN_SIGNAL_THREAD.get(CAPABILITIES.getPtr(env)))) {
                return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
            }
            Thread handleAsThread;
            try {
                handleAsThread = (Thread) thread.unhand();
                if (handleAsThread == null) {
                    return JVMTI_ERROR_INVALID_THREAD;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_THREAD;
            }
            return JVMTIThreadFunctions.interruptThread(handleAsThread);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("InterruptThread");
        }
    }

    @VM_ENTRY_POINT
    private static int GetThreadInfo(Pointer env, JniHandle thread, Pointer info_ptr) {
        // Source: JVMTIFunctionsSource.java:148
        Pointer anchor = prologue(env);
        tracePrologue("GetThreadInfo", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (info_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            Thread handleAsThread;
            try {
                handleAsThread = (Thread) thread.unhand();
                if (handleAsThread == null) {
                    return JVMTI_ERROR_INVALID_THREAD;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_THREAD;
            }
            return JVMTIThreadFunctions.getThreadInfo(handleAsThread, info_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetThreadInfo");
        }
    }

    @VM_ENTRY_POINT
    private static int GetOwnedMonitorInfo(Pointer env, JniHandle thread, Pointer owned_monitor_count_ptr, Pointer owned_monitors_ptr) {
        // Source: JVMTIFunctionsSource.java:156
        Pointer anchor = prologue(env);
        tracePrologue("GetOwnedMonitorInfo", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetOwnedMonitorInfo");
        }
    }

    @VM_ENTRY_POINT
    private static int GetCurrentContendedMonitor(Pointer env, JniHandle thread, Pointer monitor_ptr) {
        // Source: JVMTIFunctionsSource.java:161
        Pointer anchor = prologue(env);
        tracePrologue("GetCurrentContendedMonitor", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetCurrentContendedMonitor");
        }
    }

    @VM_ENTRY_POINT
    private static int RunAgentThread(Pointer env, JniHandle jthread, Address proc, Pointer arg, int priority) {
        // Source: JVMTIFunctionsSource.java:166
        Pointer anchor = prologue(env);
        tracePrologue("RunAgentThread", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (proc.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            return JVMTI.runAgentThread(env, jthread, proc, arg, priority);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("RunAgentThread");
        }
    }

    @VM_ENTRY_POINT
    private static int GetTopThreadGroups(Pointer env, Pointer group_count_ptr, Pointer groups_ptr) {
        // Source: JVMTIFunctionsSource.java:173
        Pointer anchor = prologue(env);
        tracePrologue("GetTopThreadGroups", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (group_count_ptr.isZero() || groups_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            return JVMTIThreadFunctions.getTopThreadGroups(group_count_ptr, groups_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetTopThreadGroups");
        }
    }

    @VM_ENTRY_POINT
    private static int GetThreadGroupInfo(Pointer env, JniHandle group, Pointer info_ptr) {
        // Source: JVMTIFunctionsSource.java:180
        Pointer anchor = prologue(env);
        tracePrologue("GetThreadGroupInfo", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            ThreadGroup handleAsThreadGroup;
            try {
                handleAsThreadGroup = (ThreadGroup) group.unhand();
                if (handleAsThreadGroup == null) {
                    return JVMTI_ERROR_INVALID_THREAD_GROUP;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_THREAD_GROUP;
            }
            if (info_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            return JVMTIThreadFunctions.getThreadGroupInfo(handleAsThreadGroup, info_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetThreadGroupInfo");
        }
    }

    @VM_ENTRY_POINT
    private static int GetThreadGroupChildren(Pointer env, JniHandle group, Pointer thread_count_ptr, Pointer threads_ptr, Pointer group_count_ptr, Pointer groups_ptr) {
        // Source: JVMTIFunctionsSource.java:188
        Pointer anchor = prologue(env);
        tracePrologue("GetThreadGroupChildren", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (thread_count_ptr.isZero() || thread_count_ptr.isZero() || group_count_ptr.isZero() || groups_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            ThreadGroup handleAsThreadGroup;
            try {
                handleAsThreadGroup = (ThreadGroup) group.unhand();
                if (handleAsThreadGroup == null) {
                    return JVMTI_ERROR_INVALID_THREAD_GROUP;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_THREAD_GROUP;
            }
            return JVMTIThreadFunctions.getThreadGroupChildren(handleAsThreadGroup, thread_count_ptr,
                            threads_ptr, group_count_ptr,  groups_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetThreadGroupChildren");
        }
    }

    @VM_ENTRY_POINT
    private static int GetFrameCount(Pointer env, JniHandle thread, Pointer count_ptr) {
        // Source: JVMTIFunctionsSource.java:197
        Pointer anchor = prologue(env);
        tracePrologue("GetFrameCount", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (count_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            Thread handleAsThread;
            try {
                handleAsThread = (Thread) thread.unhand();
                if (handleAsThread == null) {
                    return JVMTI_ERROR_INVALID_THREAD;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_THREAD;
            }
            return JVMTIThreadFunctions.getFrameCount(handleAsThread, count_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetFrameCount");
        }
    }

    @VM_ENTRY_POINT
    private static int GetThreadState(Pointer env, JniHandle thread, Pointer thread_state_ptr) {
        // Source: JVMTIFunctionsSource.java:205
        Pointer anchor = prologue(env);
        tracePrologue("GetThreadState", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (thread_state_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            Thread handleAsThread;
            try {
                handleAsThread = (Thread) thread.unhand();
                if (handleAsThread == null) {
                    return JVMTI_ERROR_INVALID_THREAD;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_THREAD;
            }
            return JVMTIThreadFunctions.getThreadState(handleAsThread, thread_state_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetThreadState");
        }
    }

    @VM_ENTRY_POINT
    private static int GetCurrentThread(Pointer env, Pointer thread_ptr) {
        // Source: JVMTIFunctionsSource.java:213
        Pointer anchor = prologue(env);
        tracePrologue("GetCurrentThread", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (thread_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            thread_ptr.setWord(JniHandles.createLocalHandle(VmThread.current().javaThread()));
            return JVMTI_ERROR_NONE;
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetCurrentThread");
        }
    }

    @VM_ENTRY_POINT
    private static int GetFrameLocation(Pointer env, JniHandle thread, int depth, Pointer method_ptr, Pointer location_ptr) {
        // Source: JVMTIFunctionsSource.java:221
        Pointer anchor = prologue(env);
        tracePrologue("GetFrameLocation", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            Thread handleAsThread;
            try {
                handleAsThread = (Thread) thread.unhand();
                if (handleAsThread == null) {
                    return JVMTI_ERROR_INVALID_THREAD;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_THREAD;
            }
            if (method_ptr.isZero() ||  location_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            return JVMTIThreadFunctions.getFrameLocation(handleAsThread, depth, method_ptr, location_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetFrameLocation");
        }
    }

    @VM_ENTRY_POINT
    private static int NotifyFramePop(Pointer env, JniHandle thread, int depth) {
        // Source: JVMTIFunctionsSource.java:229
        Pointer anchor = prologue(env);
        tracePrologue("NotifyFramePop", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("NotifyFramePop");
        }
    }

    @VM_ENTRY_POINT
    private static int GetLocalObject(Pointer env, JniHandle thread, int depth, int slot, Pointer value_ptr) {
        // Source: JVMTIFunctionsSource.java:234
        Pointer anchor = prologue(env);
        tracePrologue("GetLocalObject", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (!(CAN_ACCESS_LOCAL_VARIABLES.get(CAPABILITIES.getPtr(env)))) {
                return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
            }
            Thread handleAsThread;
            try {
                handleAsThread = (Thread) thread.unhand();
                if (handleAsThread == null) {
                    return JVMTI_ERROR_INVALID_THREAD;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_THREAD;
            }
            if (value_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            return JVMTIThreadFunctions.getLocalValue(handleAsThread, depth, slot, value_ptr, 'L');
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetLocalObject");
        }
    }

    @VM_ENTRY_POINT
    private static int GetLocalInt(Pointer env, JniHandle thread, int depth, int slot, Pointer value_ptr) {
        // Source: JVMTIFunctionsSource.java:243
        Pointer anchor = prologue(env);
        tracePrologue("GetLocalInt", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (!(CAN_ACCESS_LOCAL_VARIABLES.get(CAPABILITIES.getPtr(env)))) {
                return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
            }
            Thread handleAsThread;
            try {
                handleAsThread = (Thread) thread.unhand();
                if (handleAsThread == null) {
                    return JVMTI_ERROR_INVALID_THREAD;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_THREAD;
            }
            if (value_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            return JVMTIThreadFunctions.getLocalValue(handleAsThread, depth, slot, value_ptr, 'I');
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetLocalInt");
        }
    }

    @VM_ENTRY_POINT
    private static int GetLocalLong(Pointer env, JniHandle thread, int depth, int slot, Pointer value_ptr) {
        // Source: JVMTIFunctionsSource.java:252
        Pointer anchor = prologue(env);
        tracePrologue("GetLocalLong", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (!(CAN_ACCESS_LOCAL_VARIABLES.get(CAPABILITIES.getPtr(env)))) {
                return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
            }
            Thread handleAsThread;
            try {
                handleAsThread = (Thread) thread.unhand();
                if (handleAsThread == null) {
                    return JVMTI_ERROR_INVALID_THREAD;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_THREAD;
            }
            if (value_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            return JVMTIThreadFunctions.getLocalValue(handleAsThread, depth, slot, value_ptr, 'J');
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetLocalLong");
        }
    }

    @VM_ENTRY_POINT
    private static int GetLocalFloat(Pointer env, JniHandle thread, int depth, int slot, Pointer value_ptr) {
        // Source: JVMTIFunctionsSource.java:261
        Pointer anchor = prologue(env);
        tracePrologue("GetLocalFloat", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (!(CAN_ACCESS_LOCAL_VARIABLES.get(CAPABILITIES.getPtr(env)))) {
                return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
            }
            Thread handleAsThread;
            try {
                handleAsThread = (Thread) thread.unhand();
                if (handleAsThread == null) {
                    return JVMTI_ERROR_INVALID_THREAD;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_THREAD;
            }
            if (value_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            return JVMTIThreadFunctions.getLocalValue(handleAsThread, depth, slot, value_ptr, 'F');
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetLocalFloat");
        }
    }

    @VM_ENTRY_POINT
    private static int GetLocalDouble(Pointer env, JniHandle thread, int depth, int slot, Pointer value_ptr) {
        // Source: JVMTIFunctionsSource.java:270
        Pointer anchor = prologue(env);
        tracePrologue("GetLocalDouble", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (!(CAN_ACCESS_LOCAL_VARIABLES.get(CAPABILITIES.getPtr(env)))) {
                return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
            }
            Thread handleAsThread;
            try {
                handleAsThread = (Thread) thread.unhand();
                if (handleAsThread == null) {
                    return JVMTI_ERROR_INVALID_THREAD;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_THREAD;
            }
            if (value_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            return JVMTIThreadFunctions.getLocalValue(handleAsThread, depth, slot, value_ptr, 'D');
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetLocalDouble");
        }
    }

    @VM_ENTRY_POINT
    private static int SetLocalObject(Pointer env, JniHandle thread, int depth, int slot, JniHandle value) {
        // Source: JVMTIFunctionsSource.java:279
        Pointer anchor = prologue(env);
        tracePrologue("SetLocalObject", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (!(CAN_ACCESS_LOCAL_VARIABLES.get(CAPABILITIES.getPtr(env)))) {
                return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
            }
            Thread handleAsThread;
            try {
                handleAsThread = (Thread) thread.unhand();
                if (handleAsThread == null) {
                    return JVMTI_ERROR_INVALID_THREAD;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_THREAD;
            }
            return JVMTIThreadFunctions.setLocalObject(handleAsThread, depth, slot, value.unhand());
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("SetLocalObject");
        }
    }

    @VM_ENTRY_POINT
    private static int SetLocalInt(Pointer env, JniHandle thread, int depth, int slot, int value) {
        // Source: JVMTIFunctionsSource.java:287
        Pointer anchor = prologue(env);
        tracePrologue("SetLocalInt", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (!(CAN_ACCESS_LOCAL_VARIABLES.get(CAPABILITIES.getPtr(env)))) {
                return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
            }
            Thread handleAsThread;
            try {
                handleAsThread = (Thread) thread.unhand();
                if (handleAsThread == null) {
                    return JVMTI_ERROR_INVALID_THREAD;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_THREAD;
            }
            return JVMTIThreadFunctions.setLocalInt(handleAsThread, depth, slot, value);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("SetLocalInt");
        }
    }

    @VM_ENTRY_POINT
    private static int SetLocalLong(Pointer env, JniHandle thread, int depth, int slot, long value) {
        // Source: JVMTIFunctionsSource.java:295
        Pointer anchor = prologue(env);
        tracePrologue("SetLocalLong", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (!(CAN_ACCESS_LOCAL_VARIABLES.get(CAPABILITIES.getPtr(env)))) {
                return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
            }
            Thread handleAsThread;
            try {
                handleAsThread = (Thread) thread.unhand();
                if (handleAsThread == null) {
                    return JVMTI_ERROR_INVALID_THREAD;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_THREAD;
            }
            return JVMTIThreadFunctions.setLocalLong(handleAsThread, depth, slot, value);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("SetLocalLong");
        }
    }

    @VM_ENTRY_POINT
    private static int SetLocalFloat(Pointer env, JniHandle thread, int depth, int slot, float value) {
        // Source: JVMTIFunctionsSource.java:303
        Pointer anchor = prologue(env);
        tracePrologue("SetLocalFloat", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (!(CAN_ACCESS_LOCAL_VARIABLES.get(CAPABILITIES.getPtr(env)))) {
                return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
            }
            Thread handleAsThread;
            try {
                handleAsThread = (Thread) thread.unhand();
                if (handleAsThread == null) {
                    return JVMTI_ERROR_INVALID_THREAD;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_THREAD;
            }
            return JVMTIThreadFunctions.setLocalFloat(handleAsThread, depth, slot, value);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("SetLocalFloat");
        }
    }

    @VM_ENTRY_POINT
    private static int SetLocalDouble(Pointer env, JniHandle thread, int depth, int slot, double value) {
        // Source: JVMTIFunctionsSource.java:311
        Pointer anchor = prologue(env);
        tracePrologue("SetLocalDouble", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (!(CAN_ACCESS_LOCAL_VARIABLES.get(CAPABILITIES.getPtr(env)))) {
                return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
            }
            Thread handleAsThread;
            try {
                handleAsThread = (Thread) thread.unhand();
                if (handleAsThread == null) {
                    return JVMTI_ERROR_INVALID_THREAD;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_THREAD;
            }
            return JVMTIThreadFunctions.setLocalDouble(handleAsThread, depth, slot, value);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("SetLocalDouble");
        }
    }

    @VM_ENTRY_POINT
    private static int CreateRawMonitor(Pointer env, Pointer name, Pointer monitor_ptr) {
        // Source: JVMTIFunctionsSource.java:319
        Pointer anchor = prologue(env);
        tracePrologue("CreateRawMonitor", anchor);
        try {
            if (!(phase == JVMTI_PHASE_ONLOAD || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (name.isZero() || monitor_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            return JVMTIRawMonitor.create(name, monitor_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("CreateRawMonitor");
        }
    }

    @VM_ENTRY_POINT
    private static int DestroyRawMonitor(Pointer env, Word rawMonitor) {
        // Source: JVMTIFunctionsSource.java:326
        Pointer anchor = prologue(env);
        tracePrologue("DestroyRawMonitor", anchor);
        try {
            if (!(phase == JVMTI_PHASE_ONLOAD || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            return JVMTIRawMonitor.destroy(rawMonitor);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("DestroyRawMonitor");
        }
    }

    @VM_ENTRY_POINT
    private static int RawMonitorEnter(Pointer env, Word rawMonitor) {
        // Source: JVMTIFunctionsSource.java:332
        Pointer anchor = prologue(env);
        tracePrologue("RawMonitorEnter", anchor);
        try {
            // PHASES: ANY
            return JVMTIRawMonitor.enter(rawMonitor);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("RawMonitorEnter");
        }
    }

    @VM_ENTRY_POINT
    private static int RawMonitorExit(Pointer env, Word rawMonitor) {
        // Source: JVMTIFunctionsSource.java:338
        Pointer anchor = prologue(env);
        tracePrologue("RawMonitorExit", anchor);
        try {
            // PHASES: ANY
            return JVMTIRawMonitor.exit(rawMonitor);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("RawMonitorExit");
        }
    }

    @VM_ENTRY_POINT
    private static int RawMonitorWait(Pointer env, Word rawMonitor, long millis) {
        // Source: JVMTIFunctionsSource.java:344
        Pointer anchor = prologue(env);
        tracePrologue("RawMonitorWait", anchor);
        try {
            // PHASES: ANY
            return JVMTIRawMonitor.wait(rawMonitor, millis);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("RawMonitorWait");
        }
    }

    @VM_ENTRY_POINT
    private static int RawMonitorNotify(Pointer env, Word rawMonitor) {
        // Source: JVMTIFunctionsSource.java:350
        Pointer anchor = prologue(env);
        tracePrologue("RawMonitorNotify", anchor);
        try {
            // PHASES: ANY
            return JVMTIRawMonitor.notify(rawMonitor);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("RawMonitorNotify");
        }
    }

    @VM_ENTRY_POINT
    private static int RawMonitorNotifyAll(Pointer env, Word rawMonitor) {
        // Source: JVMTIFunctionsSource.java:356
        Pointer anchor = prologue(env);
        tracePrologue("RawMonitorNotifyAll", anchor);
        try {
            // PHASES: ANY
            return JVMTIRawMonitor.notifyAll(rawMonitor);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("RawMonitorNotifyAll");
        }
    }

    @VM_ENTRY_POINT
    private static int SetBreakpoint(Pointer env, MethodID method, long location) {
        // Source: JVMTIFunctionsSource.java:362
        Pointer anchor = prologue(env);
        tracePrologue("SetBreakpoint", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            ClassMethodActor classMethodActor = JVMTIUtil.toClassMethodActor(method);
            if (classMethodActor == null) {
                return JVMTI_ERROR_INVALID_METHODID;
            }
            return JVMTIBreakpoints.setBreakpoint(classMethodActor, method, location);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("SetBreakpoint");
        }
    }

    @VM_ENTRY_POINT
    private static int ClearBreakpoint(Pointer env, MethodID method, long location) {
        // Source: JVMTIFunctionsSource.java:369
        Pointer anchor = prologue(env);
        tracePrologue("ClearBreakpoint", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            ClassMethodActor classMethodActor = JVMTIUtil.toClassMethodActor(method);
            if (classMethodActor == null) {
                return JVMTI_ERROR_INVALID_METHODID;
            }
            return JVMTIBreakpoints.clearBreakpoint(classMethodActor, method, location);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("ClearBreakpoint");
        }
    }

    @VM_ENTRY_POINT
    private static native void reserved40();
        // Source: JVMTIFunctionsSource.java:376

    @VM_ENTRY_POINT
    private static int SetFieldAccessWatch(Pointer env, JniHandle klass, FieldID field) {
        // Source: JVMTIFunctionsSource.java:379
        Pointer anchor = prologue(env);
        tracePrologue("SetFieldAccessWatch", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            Class handleAsClass;
            try {
                handleAsClass = (Class) klass.unhand();
                if (handleAsClass == null) {
                    return JVMTI_ERROR_INVALID_CLASS;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_CLASS;
            }
            FieldActor fieldActor = FieldID.toFieldActor(field);
            if (fieldActor == null) {
                return JVMTI_ERROR_INVALID_FIELDID;
            }
            if (!(CAN_GENERATE_FIELD_ACCESS_EVENTS.get(CAPABILITIES.getPtr(env)))) {
                return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
            }
            return JVMTIFieldWatch.setAccessWatch(handleAsClass, fieldActor);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("SetFieldAccessWatch");
        }
    }

    @VM_ENTRY_POINT
    private static int ClearFieldAccessWatch(Pointer env, JniHandle klass, FieldID field) {
        // Source: JVMTIFunctionsSource.java:388
        Pointer anchor = prologue(env);
        tracePrologue("ClearFieldAccessWatch", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            Class handleAsClass;
            try {
                handleAsClass = (Class) klass.unhand();
                if (handleAsClass == null) {
                    return JVMTI_ERROR_INVALID_CLASS;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_CLASS;
            }
            FieldActor fieldActor = FieldID.toFieldActor(field);
            if (fieldActor == null) {
                return JVMTI_ERROR_INVALID_FIELDID;
            }
            if (!(CAN_GENERATE_FIELD_ACCESS_EVENTS.get(CAPABILITIES.getPtr(env)))) {
                return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
            }
            return JVMTIFieldWatch.clearAccessWatch(handleAsClass, fieldActor);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("ClearFieldAccessWatch");
        }
    }

    @VM_ENTRY_POINT
    private static int SetFieldModificationWatch(Pointer env, JniHandle klass, FieldID field) {
        // Source: JVMTIFunctionsSource.java:397
        Pointer anchor = prologue(env);
        tracePrologue("SetFieldModificationWatch", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            Class handleAsClass;
            try {
                handleAsClass = (Class) klass.unhand();
                if (handleAsClass == null) {
                    return JVMTI_ERROR_INVALID_CLASS;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_CLASS;
            }
            FieldActor fieldActor = FieldID.toFieldActor(field);
            if (fieldActor == null) {
                return JVMTI_ERROR_INVALID_FIELDID;
            }
            if (!(CAN_GENERATE_FIELD_MODIFICATION_EVENTS.get(CAPABILITIES.getPtr(env)))) {
                return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
            }
            return JVMTIFieldWatch.setModificationWatch(handleAsClass, fieldActor);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("SetFieldModificationWatch");
        }
    }

    @VM_ENTRY_POINT
    private static int ClearFieldModificationWatch(Pointer env, JniHandle klass, FieldID field) {
        // Source: JVMTIFunctionsSource.java:406
        Pointer anchor = prologue(env);
        tracePrologue("ClearFieldModificationWatch", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            Class handleAsClass;
            try {
                handleAsClass = (Class) klass.unhand();
                if (handleAsClass == null) {
                    return JVMTI_ERROR_INVALID_CLASS;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_CLASS;
            }
            FieldActor fieldActor = FieldID.toFieldActor(field);
            if (fieldActor == null) {
                return JVMTI_ERROR_INVALID_FIELDID;
            }
            if (!(CAN_GENERATE_FIELD_MODIFICATION_EVENTS.get(CAPABILITIES.getPtr(env)))) {
                return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
            }
            return JVMTIFieldWatch.clearModificationWatch(handleAsClass, fieldActor);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("ClearFieldModificationWatch");
        }
    }

    @VM_ENTRY_POINT
    private static int IsModifiableClass(Pointer env, JniHandle klass, Pointer is_modifiable_class_ptr) {
        // Source: JVMTIFunctionsSource.java:415
        Pointer anchor = prologue(env);
        tracePrologue("IsModifiableClass", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("IsModifiableClass");
        }
    }

    @VM_ENTRY_POINT
    private static int Allocate(Pointer env, long size, Pointer mem_ptr) {
        // Source: JVMTIFunctionsSource.java:420
        Pointer anchor = prologue(env);
        tracePrologue("Allocate", anchor);
        try {
            // PHASES: ANY
            if (mem_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            if (size < 0) {
                return JVMTI_ERROR_ILLEGAL_ARGUMENT;
            }
            if (size == 0) {
                mem_ptr.setWord(Word.zero());
            } else {
                Pointer mem = Memory.allocate(Size.fromLong(size));
                if (mem.isZero()) {
                    return JVMTI_ERROR_OUT_OF_MEMORY;
                }
                mem_ptr.setWord(mem);
            }
            return JVMTI_ERROR_NONE;
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("Allocate");
        }
    }

    @VM_ENTRY_POINT
    private static int Deallocate(Pointer env, Pointer mem) {
        // Source: JVMTIFunctionsSource.java:439
        Pointer anchor = prologue(env);
        tracePrologue("Deallocate", anchor);
        try {
            // PHASES: ANY
            Memory.deallocate(mem);
            return JVMTI_ERROR_NONE;
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("Deallocate");
        }
    }

    @VM_ENTRY_POINT
    private static int GetClassSignature(Pointer env, JniHandle klass, Pointer signature_ptr, Pointer generic_ptr) {
        // Source: JVMTIFunctionsSource.java:446
        Pointer anchor = prologue(env);
        tracePrologue("GetClassSignature", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            Class handleAsClass;
            try {
                handleAsClass = (Class) klass.unhand();
                if (handleAsClass == null) {
                    return JVMTI_ERROR_INVALID_CLASS;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_CLASS;
            }
            return JVMTIClassFunctions.getClassSignature(handleAsClass, signature_ptr, generic_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetClassSignature");
        }
    }

    @VM_ENTRY_POINT
    private static int GetClassStatus(Pointer env, JniHandle klass, Pointer status_ptr) {
        // Source: JVMTIFunctionsSource.java:453
        Pointer anchor = prologue(env);
        tracePrologue("GetClassStatus", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            Class handleAsClass;
            try {
                handleAsClass = (Class) klass.unhand();
                if (handleAsClass == null) {
                    return JVMTI_ERROR_INVALID_CLASS;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_CLASS;
            }
            if (status_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            return JVMTIClassFunctions.getClassStatus(handleAsClass, status_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetClassStatus");
        }
    }

    @VM_ENTRY_POINT
    private static int GetSourceFileName(Pointer env, JniHandle klass, Pointer source_name_ptr) {
        // Source: JVMTIFunctionsSource.java:461
        Pointer anchor = prologue(env);
        tracePrologue("GetSourceFileName", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (!(CAN_GET_SOURCE_FILE_NAME.get(CAPABILITIES.getPtr(env)))) {
                return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
            }
            if (source_name_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            Class handleAsClass;
            try {
                handleAsClass = (Class) klass.unhand();
                if (handleAsClass == null) {
                    return JVMTI_ERROR_INVALID_CLASS;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_CLASS;
            }
            return JVMTIClassFunctions.getSourceFileName(handleAsClass, source_name_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetSourceFileName");
        }
    }

    @VM_ENTRY_POINT
    private static int GetClassModifiers(Pointer env, JniHandle klass, Pointer modifiers_ptr) {
        // Source: JVMTIFunctionsSource.java:470
        Pointer anchor = prologue(env);
        tracePrologue("GetClassModifiers", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (modifiers_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            Class handleAsClass;
            try {
                handleAsClass = (Class) klass.unhand();
                if (handleAsClass == null) {
                    return JVMTI_ERROR_INVALID_CLASS;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_CLASS;
            }
            modifiers_ptr.setInt(ClassActor.fromJava(handleAsClass).accessFlags());
            return JVMTI_ERROR_NONE;
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetClassModifiers");
        }
    }

    @VM_ENTRY_POINT
    private static int GetClassMethods(Pointer env, JniHandle klass, Pointer method_count_ptr, Pointer methods_ptr) {
        // Source: JVMTIFunctionsSource.java:479
        Pointer anchor = prologue(env);
        tracePrologue("GetClassMethods", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (method_count_ptr.isZero() || methods_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            Class handleAsClass;
            try {
                handleAsClass = (Class) klass.unhand();
                if (handleAsClass == null) {
                    return JVMTI_ERROR_INVALID_CLASS;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_CLASS;
            }
            return JVMTIClassFunctions.getClassMethods(handleAsClass, method_count_ptr, methods_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetClassMethods");
        }
    }

    @VM_ENTRY_POINT
    private static int GetClassFields(Pointer env, JniHandle klass, Pointer field_count_ptr, Pointer fields_ptr) {
        // Source: JVMTIFunctionsSource.java:487
        Pointer anchor = prologue(env);
        tracePrologue("GetClassFields", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (field_count_ptr.isZero() || fields_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            Class handleAsClass;
            try {
                handleAsClass = (Class) klass.unhand();
                if (handleAsClass == null) {
                    return JVMTI_ERROR_INVALID_CLASS;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_CLASS;
            }
            return JVMTIClassFunctions.getClassFields(handleAsClass, field_count_ptr, fields_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetClassFields");
        }
    }

    @VM_ENTRY_POINT
    private static int GetImplementedInterfaces(Pointer env, JniHandle klass, Pointer interface_count_ptr, Pointer interfaces_ptr) {
        // Source: JVMTIFunctionsSource.java:495
        Pointer anchor = prologue(env);
        tracePrologue("GetImplementedInterfaces", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (interface_count_ptr.isZero() || interfaces_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            Class handleAsClass;
            try {
                handleAsClass = (Class) klass.unhand();
                if (handleAsClass == null) {
                    return JVMTI_ERROR_INVALID_CLASS;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_CLASS;
            }
            return JVMTIClassFunctions.getImplementedInterfaces(handleAsClass, interface_count_ptr, interfaces_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetImplementedInterfaces");
        }
    }

    @VM_ENTRY_POINT
    private static int IsInterface(Pointer env, JniHandle klass, Pointer is_interface_ptr) {
        // Source: JVMTIFunctionsSource.java:503
        Pointer anchor = prologue(env);
        tracePrologue("IsInterface", anchor);
        try {
            // PHASES LIVE
            if (is_interface_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            Class handleAsClass;
            try {
                handleAsClass = (Class) klass.unhand();
                if (handleAsClass == null) {
                    return JVMTI_ERROR_INVALID_CLASS;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_CLASS;
            }
            boolean is = ClassActor.isInterface(ClassActor.fromJava(handleAsClass).flags());
            is_interface_ptr.setBoolean(is);
            return JVMTI_ERROR_NONE;
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("IsInterface");
        }
    }

    @VM_ENTRY_POINT
    private static int IsArrayClass(Pointer env, JniHandle klass, Pointer is_array_class_ptr) {
        // Source: JVMTIFunctionsSource.java:513
        Pointer anchor = prologue(env);
        tracePrologue("IsArrayClass", anchor);
        try {
            // PHASES LIVE
            if (is_array_class_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            Class handleAsClass;
            try {
                handleAsClass = (Class) klass.unhand();
                if (handleAsClass == null) {
                    return JVMTI_ERROR_INVALID_CLASS;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_CLASS;
            }
            boolean is = ClassActor.fromJava(handleAsClass).isArrayClass();
            is_array_class_ptr.setBoolean(is);
            return JVMTI_ERROR_NONE;
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("IsArrayClass");
        }
    }

    @VM_ENTRY_POINT
    private static int GetClassLoader(Pointer env, JniHandle klass, Pointer classloader_ptr) {
        // Source: JVMTIFunctionsSource.java:523
        Pointer anchor = prologue(env);
        tracePrologue("GetClassLoader", anchor);
        try {
            // PHASES START,LIVE
            if (classloader_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            Class handleAsClass;
            try {
                handleAsClass = (Class) klass.unhand();
                if (handleAsClass == null) {
                    return JVMTI_ERROR_INVALID_CLASS;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_CLASS;
            }
            classloader_ptr.setWord(JniHandles.createLocalHandle(handleAsClass.getClassLoader()));
            return JVMTI_ERROR_NONE;
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetClassLoader");
        }
    }

    @VM_ENTRY_POINT
    private static int GetObjectHashCode(Pointer env, JniHandle handle, Pointer hash_code_ptr) {
        // Source: JVMTIFunctionsSource.java:532
        Pointer anchor = prologue(env);
        tracePrologue("GetObjectHashCode", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (hash_code_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            Object object = handle.unhand();
            if (object == null) {
                return JVMTI_ERROR_INVALID_OBJECT;
            }
            hash_code_ptr.setInt(System.identityHashCode(object));
            return JVMTI_ERROR_NONE;
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetObjectHashCode");
        }
    }

    @VM_ENTRY_POINT
    private static int GetObjectMonitorUsage(Pointer env, JniHandle object, Pointer info_ptr) {
        // Source: JVMTIFunctionsSource.java:544
        Pointer anchor = prologue(env);
        tracePrologue("GetObjectMonitorUsage", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetObjectMonitorUsage");
        }
    }

    @VM_ENTRY_POINT
    private static int GetFieldName(Pointer env, JniHandle klass, FieldID field, Pointer name_ptr, Pointer signature_ptr, Pointer generic_ptr) {
        // Source: JVMTIFunctionsSource.java:549
        Pointer anchor = prologue(env);
        tracePrologue("GetFieldName", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            Class handleAsClass;
            try {
                handleAsClass = (Class) klass.unhand();
                if (handleAsClass == null) {
                    return JVMTI_ERROR_INVALID_CLASS;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_CLASS;
            }
            FieldActor fieldActor = FieldID.toFieldActor(field);
            if (fieldActor == null) {
                return JVMTI_ERROR_INVALID_FIELDID;
            }
            return JVMTIClassFunctions.getFieldName(fieldActor, name_ptr, signature_ptr, generic_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetFieldName");
        }
    }

    @VM_ENTRY_POINT
    private static int GetFieldDeclaringClass(Pointer env, JniHandle klass, FieldID field, Pointer declaring_class_ptr) {
        // Source: JVMTIFunctionsSource.java:557
        Pointer anchor = prologue(env);
        tracePrologue("GetFieldDeclaringClass", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (declaring_class_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            FieldActor fieldActor = FieldID.toFieldActor(field);
            if (fieldActor == null) {
                return JVMTI_ERROR_INVALID_FIELDID;
            }
            return JVMTIClassFunctions.getFieldDeclaringClass(fieldActor, declaring_class_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetFieldDeclaringClass");
        }
    }

    @VM_ENTRY_POINT
    private static int GetFieldModifiers(Pointer env, JniHandle klass, FieldID field, Pointer modifiers_ptr) {
        // Source: JVMTIFunctionsSource.java:565
        Pointer anchor = prologue(env);
        tracePrologue("GetFieldModifiers", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (modifiers_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            FieldActor fieldActor = FieldID.toFieldActor(field);
            if (fieldActor == null) {
                return JVMTI_ERROR_INVALID_FIELDID;
            }
            modifiers_ptr.setInt(fieldActor.flags());
            return JVMTI_ERROR_NONE;
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetFieldModifiers");
        }
    }

    @VM_ENTRY_POINT
    private static int IsFieldSynthetic(Pointer env, JniHandle klass, FieldID field, Pointer is_synthetic_ptr) {
        // Source: JVMTIFunctionsSource.java:574
        Pointer anchor = prologue(env);
        tracePrologue("IsFieldSynthetic", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (!(CAN_GET_SYNTHETIC_ATTRIBUTE.get(CAPABILITIES.getPtr(env)))) {
                return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
            }
            if (is_synthetic_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            FieldActor fieldActor = FieldID.toFieldActor(field);
            if (fieldActor == null) {
                return JVMTI_ERROR_INVALID_FIELDID;
            }
            boolean result = (fieldActor.flags() & Actor.ACC_SYNTHETIC) != 0;
            is_synthetic_ptr.setBoolean(result);
            return JVMTI_ERROR_NONE;
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("IsFieldSynthetic");
        }
    }

    @VM_ENTRY_POINT
    private static int GetMethodName(Pointer env, MethodID method, Pointer name_ptr, Pointer signature_ptr, Pointer generic_ptr) {
        // Source: JVMTIFunctionsSource.java:585
        Pointer anchor = prologue(env);
        tracePrologue("GetMethodName", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            MethodActor methodActor = MethodID.toMethodActor(method);
            if (methodActor == null) {
                return JVMTI_ERROR_INVALID_METHODID;
            }
            return JVMTIClassFunctions.getMethodName(methodActor, name_ptr, signature_ptr, generic_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetMethodName");
        }
    }

    @VM_ENTRY_POINT
    private static int GetMethodDeclaringClass(Pointer env, MethodID method, Pointer declaring_class_ptr) {
        // Source: JVMTIFunctionsSource.java:592
        Pointer anchor = prologue(env);
        tracePrologue("GetMethodDeclaringClass", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (declaring_class_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            MethodActor methodActor = MethodID.toMethodActor(method);
            if (methodActor == null) {
                return JVMTI_ERROR_INVALID_METHODID;
            }
            return JVMTIClassFunctions.getMethodDeclaringClass(methodActor, declaring_class_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetMethodDeclaringClass");
        }
    }

    @VM_ENTRY_POINT
    private static int GetMethodModifiers(Pointer env, MethodID method, Pointer modifiers_ptr) {
        // Source: JVMTIFunctionsSource.java:600
        Pointer anchor = prologue(env);
        tracePrologue("GetMethodModifiers", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (modifiers_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            MethodActor methodActor = MethodID.toMethodActor(method);
            if (methodActor == null) {
                return JVMTI_ERROR_INVALID_METHODID;
            }
            modifiers_ptr.setInt(methodActor.flags());
            return JVMTI_ERROR_NONE;
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetMethodModifiers");
        }
    }

    @VM_ENTRY_POINT
    private static native void reserved67();
        // Source: JVMTIFunctionsSource.java:609

    @VM_ENTRY_POINT
    private static int GetMaxLocals(Pointer env, MethodID method, Pointer max_ptr) {
        // Source: JVMTIFunctionsSource.java:612
        Pointer anchor = prologue(env);
        tracePrologue("GetMaxLocals", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (max_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            ClassMethodActor classMethodActor = JVMTIUtil.toClassMethodActor(method);
            if (classMethodActor == null) {
                return JVMTI_ERROR_INVALID_METHODID;
            }
            return JVMTIClassFunctions.getMaxLocals(classMethodActor, max_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetMaxLocals");
        }
    }

    @VM_ENTRY_POINT
    private static int GetArgumentsSize(Pointer env, MethodID method, Pointer size_ptr) {
        // Source: JVMTIFunctionsSource.java:620
        Pointer anchor = prologue(env);
        tracePrologue("GetArgumentsSize", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (size_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            ClassMethodActor classMethodActor = JVMTIUtil.toClassMethodActor(method);
            if (classMethodActor == null) {
                return JVMTI_ERROR_INVALID_METHODID;
            }
            return JVMTIClassFunctions.getArgumentsSize(classMethodActor, size_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetArgumentsSize");
        }
    }

    @VM_ENTRY_POINT
    private static int GetLineNumberTable(Pointer env, MethodID method, Pointer entry_count_ptr, Pointer table_ptr) {
        // Source: JVMTIFunctionsSource.java:628
        Pointer anchor = prologue(env);
        tracePrologue("GetLineNumberTable", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (!(CAN_GET_LINE_NUMBERS.get(CAPABILITIES.getPtr(env)))) {
                return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
            }
            if (entry_count_ptr.isZero() || table_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            ClassMethodActor classMethodActor = JVMTIUtil.toClassMethodActor(method);
            if (classMethodActor == null) {
                return JVMTI_ERROR_INVALID_METHODID;
            }
            return JVMTIClassFunctions.getLineNumberTable(classMethodActor, entry_count_ptr, table_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetLineNumberTable");
        }
    }

    @VM_ENTRY_POINT
    private static int GetMethodLocation(Pointer env, MethodID method, Pointer start_location_ptr, Pointer end_location_ptr) {
        // Source: JVMTIFunctionsSource.java:637
        Pointer anchor = prologue(env);
        tracePrologue("GetMethodLocation", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (start_location_ptr.isZero() || end_location_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            ClassMethodActor classMethodActor = JVMTIUtil.toClassMethodActor(method);
            if (classMethodActor == null) {
                return JVMTI_ERROR_INVALID_METHODID;
            }
            return JVMTIClassFunctions.getMethodLocation(classMethodActor, start_location_ptr, end_location_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetMethodLocation");
        }
    }

    @VM_ENTRY_POINT
    private static int GetLocalVariableTable(Pointer env, MethodID method, Pointer entry_count_ptr, Pointer table_ptr) {
        // Source: JVMTIFunctionsSource.java:645
        Pointer anchor = prologue(env);
        tracePrologue("GetLocalVariableTable", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (entry_count_ptr.isZero() ||  table_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            ClassMethodActor classMethodActor = JVMTIUtil.toClassMethodActor(method);
            if (classMethodActor == null) {
                return JVMTI_ERROR_INVALID_METHODID;
            }
            return JVMTIClassFunctions.getLocalVariableTable(classMethodActor, entry_count_ptr, table_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetLocalVariableTable");
        }
    }

    @VM_ENTRY_POINT
    private static int SetNativeMethodPrefix(Pointer env, Pointer prefix) {
        // Source: JVMTIFunctionsSource.java:653
        Pointer anchor = prologue(env);
        tracePrologue("SetNativeMethodPrefix", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("SetNativeMethodPrefix");
        }
    }

    @VM_ENTRY_POINT
    private static int SetNativeMethodPrefixes(Pointer env, int prefix_count, Pointer prefixes) {
        // Source: JVMTIFunctionsSource.java:658
        Pointer anchor = prologue(env);
        tracePrologue("SetNativeMethodPrefixes", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("SetNativeMethodPrefixes");
        }
    }

    @VM_ENTRY_POINT
    private static int GetBytecodes(Pointer env, MethodID method, Pointer bytecode_count_ptr, Pointer bytecodes_ptr) {
        // Source: JVMTIFunctionsSource.java:663
        Pointer anchor = prologue(env);
        tracePrologue("GetBytecodes", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (!(CAN_GET_BYTECODES.get(CAPABILITIES.getPtr(env)))) {
                return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
            }
            if (bytecode_count_ptr.isZero() || bytecodes_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            ClassMethodActor classMethodActor = JVMTIUtil.toClassMethodActor(method);
            if (classMethodActor == null) {
                return JVMTI_ERROR_INVALID_METHODID;
            }
            return JVMTIClassFunctions.getByteCodes(classMethodActor, bytecode_count_ptr, bytecodes_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetBytecodes");
        }
    }

    @VM_ENTRY_POINT
    private static int IsMethodNative(Pointer env, MethodID method, Pointer is_native_ptr) {
        // Source: JVMTIFunctionsSource.java:672
        Pointer anchor = prologue(env);
        tracePrologue("IsMethodNative", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (is_native_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            MethodActor methodActor = MethodID.toMethodActor(method);
            if (methodActor == null) {
                return JVMTI_ERROR_INVALID_METHODID;
            }
            is_native_ptr.setBoolean(methodActor.isNative());
            return JVMTI_ERROR_NONE;
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("IsMethodNative");
        }
    }

    @VM_ENTRY_POINT
    private static int IsMethodSynthetic(Pointer env, MethodID method, Pointer is_synthetic_ptr) {
        // Source: JVMTIFunctionsSource.java:681
        Pointer anchor = prologue(env);
        tracePrologue("IsMethodSynthetic", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (!(CAN_GET_SYNTHETIC_ATTRIBUTE.get(CAPABILITIES.getPtr(env)))) {
                return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
            }
            if (is_synthetic_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            MethodActor methodActor = MethodID.toMethodActor(method);
            if (methodActor == null) {
                return JVMTI_ERROR_INVALID_METHODID;
            }
            boolean result = (methodActor.flags() & Actor.ACC_SYNTHETIC) != 0;
            is_synthetic_ptr.setBoolean(result);
            return JVMTI_ERROR_NONE;
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("IsMethodSynthetic");
        }
    }

    @VM_ENTRY_POINT
    private static int GetLoadedClasses(Pointer env, Pointer class_count_ptr, Pointer classes_ptr) {
        // Source: JVMTIFunctionsSource.java:692
        Pointer anchor = prologue(env);
        tracePrologue("GetLoadedClasses", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (class_count_ptr.isZero() || classes_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            return JVMTIClassFunctions.getLoadedClasses(class_count_ptr, classes_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetLoadedClasses");
        }
    }

    @VM_ENTRY_POINT
    private static int GetClassLoaderClasses(Pointer env, JniHandle initiatingLoader, Pointer class_count_ptr, Pointer classes_ptr) {
        // Source: JVMTIFunctionsSource.java:699
        Pointer anchor = prologue(env);
        tracePrologue("GetClassLoaderClasses", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (class_count_ptr.isZero() || classes_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            ClassLoader handleAsClassLoader;
            try {
                handleAsClassLoader = (ClassLoader) initiatingLoader.unhand();
                if (handleAsClassLoader == null) {
                    return JVMTI_ERROR_INVALID_OBJECT;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_OBJECT;
            }
            return JVMTIClassFunctions.getClassLoaderClasses(handleAsClassLoader, class_count_ptr, classes_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetClassLoaderClasses");
        }
    }

    @VM_ENTRY_POINT
    private static int PopFrame(Pointer env, JniHandle thread) {
        // Source: JVMTIFunctionsSource.java:707
        Pointer anchor = prologue(env);
        tracePrologue("PopFrame", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("PopFrame");
        }
    }

    @VM_ENTRY_POINT
    private static int ForceEarlyReturnObject(Pointer env, JniHandle thread, JniHandle value) {
        // Source: JVMTIFunctionsSource.java:712
        Pointer anchor = prologue(env);
        tracePrologue("ForceEarlyReturnObject", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("ForceEarlyReturnObject");
        }
    }

    @VM_ENTRY_POINT
    private static int ForceEarlyReturnInt(Pointer env, JniHandle thread, int value) {
        // Source: JVMTIFunctionsSource.java:717
        Pointer anchor = prologue(env);
        tracePrologue("ForceEarlyReturnInt", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("ForceEarlyReturnInt");
        }
    }

    @VM_ENTRY_POINT
    private static int ForceEarlyReturnLong(Pointer env, JniHandle thread, long value) {
        // Source: JVMTIFunctionsSource.java:722
        Pointer anchor = prologue(env);
        tracePrologue("ForceEarlyReturnLong", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("ForceEarlyReturnLong");
        }
    }

    @VM_ENTRY_POINT
    private static int ForceEarlyReturnFloat(Pointer env, JniHandle thread, float value) {
        // Source: JVMTIFunctionsSource.java:727
        Pointer anchor = prologue(env);
        tracePrologue("ForceEarlyReturnFloat", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("ForceEarlyReturnFloat");
        }
    }

    @VM_ENTRY_POINT
    private static int ForceEarlyReturnDouble(Pointer env, JniHandle thread, double value) {
        // Source: JVMTIFunctionsSource.java:732
        Pointer anchor = prologue(env);
        tracePrologue("ForceEarlyReturnDouble", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("ForceEarlyReturnDouble");
        }
    }

    @VM_ENTRY_POINT
    private static int ForceEarlyReturnVoid(Pointer env, JniHandle thread) {
        // Source: JVMTIFunctionsSource.java:737
        Pointer anchor = prologue(env);
        tracePrologue("ForceEarlyReturnVoid", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("ForceEarlyReturnVoid");
        }
    }

    @VM_ENTRY_POINT
    private static int RedefineClasses(Pointer env, int class_count, Pointer class_definitions) {
        // Source: JVMTIFunctionsSource.java:742
        Pointer anchor = prologue(env);
        tracePrologue("RedefineClasses", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("RedefineClasses");
        }
    }

    @VM_ENTRY_POINT
    private static int GetVersionNumber(Pointer env, Pointer version_ptr) {
        // Source: JVMTIFunctionsSource.java:747
        Pointer anchor = prologue(env);
        tracePrologue("GetVersionNumber", anchor);
        try {
            // PHASES: ANY
            if (version_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            version_ptr.setInt(JVMTI_VERSION);
            return JVMTI_ERROR_NONE;
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetVersionNumber");
        }
    }

    @VM_ENTRY_POINT
    private static int GetCapabilities(Pointer env, Pointer capabilities_ptr) {
        // Source: JVMTIFunctionsSource.java:755
        Pointer anchor = prologue(env);
        tracePrologue("GetCapabilities", anchor);
        try {
            // PHASES: ANY
            if (capabilities_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            capabilities_ptr.setLong(0, CAPABILITIES.getPtr(env).readLong(0));
            return JVMTI_ERROR_NONE;
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetCapabilities");
        }
    }

    @VM_ENTRY_POINT
    private static int GetSourceDebugExtension(Pointer env, JniHandle klass, Pointer source_debug_extension_ptr) {
        // Source: JVMTIFunctionsSource.java:763
        Pointer anchor = prologue(env);
        tracePrologue("GetSourceDebugExtension", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (!(CAN_GET_SOURCE_DEBUG_EXTENSION.get(CAPABILITIES.getPtr(env)))) {
                return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
            }
            if (source_debug_extension_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            Class handleAsClass;
            try {
                handleAsClass = (Class) klass.unhand();
                if (handleAsClass == null) {
                    return JVMTI_ERROR_INVALID_CLASS;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_CLASS;
            }
            return JVMTIClassFunctions.getSourceDebugExtension(handleAsClass, source_debug_extension_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetSourceDebugExtension");
        }
    }

    @VM_ENTRY_POINT
    private static int IsMethodObsolete(Pointer env, MethodID method, Pointer is_obsolete_ptr) {
        // Source: JVMTIFunctionsSource.java:772
        Pointer anchor = prologue(env);
        tracePrologue("IsMethodObsolete", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (is_obsolete_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            ClassMethodActor classMethodActor = JVMTIUtil.toClassMethodActor(method);
            if (classMethodActor == null) {
                return JVMTI_ERROR_INVALID_METHODID;
            }
            return JVMTIClassFunctions.isMethodObsolete(classMethodActor, is_obsolete_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("IsMethodObsolete");
        }
    }

    @VM_ENTRY_POINT
    private static int SuspendThreadList(Pointer env, int request_count, Pointer request_list, Pointer results) {
        // Source: JVMTIFunctionsSource.java:780
        Pointer anchor = prologue(env);
        tracePrologue("SuspendThreadList", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (!(CAN_SUSPEND.get(CAPABILITIES.getPtr(env)))) {
                return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
            }
            if (request_list.isZero() || results.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            if (request_count < 0) {
                return JVMTI_ERROR_ILLEGAL_ARGUMENT;
            }
            return JVMTIThreadFunctions.suspendThreadList(request_count, request_list, results);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("SuspendThreadList");
        }
    }

    @VM_ENTRY_POINT
    private static int ResumeThreadList(Pointer env, int request_count, Pointer request_list, Pointer results) {
        // Source: JVMTIFunctionsSource.java:791
        Pointer anchor = prologue(env);
        tracePrologue("ResumeThreadList", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (!(CAN_SUSPEND.get(CAPABILITIES.getPtr(env)))) {
                return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
            }
            if (request_list.isZero() || results.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            if (request_count < 0) {
                return JVMTI_ERROR_ILLEGAL_ARGUMENT;
            }
            return JVMTIThreadFunctions.resumeThreadList(request_count, request_list, results);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("ResumeThreadList");
        }
    }

    @VM_ENTRY_POINT
    private static native void reserved94();
        // Source: JVMTIFunctionsSource.java:802

    @VM_ENTRY_POINT
    private static native void reserved95();
        // Source: JVMTIFunctionsSource.java:805

    @VM_ENTRY_POINT
    private static native void reserved96();
        // Source: JVMTIFunctionsSource.java:808

    @VM_ENTRY_POINT
    private static native void reserved97();
        // Source: JVMTIFunctionsSource.java:811

    @VM_ENTRY_POINT
    private static native void reserved98();
        // Source: JVMTIFunctionsSource.java:814

    @VM_ENTRY_POINT
    private static native void reserved99();
        // Source: JVMTIFunctionsSource.java:817

    @VM_ENTRY_POINT
    private static int GetAllStackTraces(Pointer env, int max_frame_count, Pointer stack_info_ptr, Pointer thread_count_ptr) {
        // Source: JVMTIFunctionsSource.java:820
        Pointer anchor = prologue(env);
        tracePrologue("GetAllStackTraces", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (stack_info_ptr.isZero() || thread_count_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            if (max_frame_count < 0) {
                return JVMTI_ERROR_ILLEGAL_ARGUMENT;
            }
            return JVMTIThreadFunctions.getAllStackTraces(max_frame_count, stack_info_ptr, thread_count_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetAllStackTraces");
        }
    }

    @VM_ENTRY_POINT
    private static int GetThreadListStackTraces(Pointer env, int thread_count, Pointer thread_list, int max_frame_count, Pointer stack_info_ptr) {
        // Source: JVMTIFunctionsSource.java:830
        Pointer anchor = prologue(env);
        tracePrologue("GetThreadListStackTraces", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (thread_list.isZero() || stack_info_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            if (thread_count < 0 || max_frame_count < 0) {
                return JVMTI_ERROR_ILLEGAL_ARGUMENT;
            }
            return JVMTIThreadFunctions.getThreadListStackTraces(thread_count, thread_list, max_frame_count, stack_info_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetThreadListStackTraces");
        }
    }

    @VM_ENTRY_POINT
    private static int GetThreadLocalStorage(Pointer env, JniHandle thread, Pointer data_ptr) {
        // Source: JVMTIFunctionsSource.java:840
        Pointer anchor = prologue(env);
        tracePrologue("GetThreadLocalStorage", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            Thread handleAsThread;
            try {
                handleAsThread = (Thread) thread.unhand();
                if (handleAsThread == null) {
                    return JVMTI_ERROR_INVALID_THREAD;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_THREAD;
            }
            if (data_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            return JVMTIThreadLocalStorage.getThreadLocalStorage(handleAsThread, data_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetThreadLocalStorage");
        }
    }

    @VM_ENTRY_POINT
    private static int SetThreadLocalStorage(Pointer env, JniHandle thread, Pointer data) {
        // Source: JVMTIFunctionsSource.java:848
        Pointer anchor = prologue(env);
        tracePrologue("SetThreadLocalStorage", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            Thread handleAsThread;
            try {
                handleAsThread = (Thread) thread.unhand();
                if (handleAsThread == null) {
                    return JVMTI_ERROR_INVALID_THREAD;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_THREAD;
            }
            return JVMTIThreadLocalStorage.setThreadLocalStorage(handleAsThread, data);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("SetThreadLocalStorage");
        }
    }

    @VM_ENTRY_POINT
    private static int GetStackTrace(Pointer env, JniHandle thread, int start_depth, int max_frame_count, Pointer frame_buffer, Pointer count_ptr) {
        // Source: JVMTIFunctionsSource.java:855
        Pointer anchor = prologue(env);
        tracePrologue("GetStackTrace", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (frame_buffer.isZero() || count_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            Thread handleAsThread;
            try {
                handleAsThread = (Thread) thread.unhand();
                if (handleAsThread == null) {
                    return JVMTI_ERROR_INVALID_THREAD;
                }
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_THREAD;
            }
            if (max_frame_count < 0) {
                return JVMTI_ERROR_ILLEGAL_ARGUMENT;
            }
            return JVMTIThreadFunctions.getStackTrace(handleAsThread, start_depth, max_frame_count, frame_buffer, count_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetStackTrace");
        }
    }

    @VM_ENTRY_POINT
    private static native void reserved105();
        // Source: JVMTIFunctionsSource.java:866

    @VM_ENTRY_POINT
    private static int GetTag(Pointer env, JniHandle object, Pointer tag_ptr) {
        // Source: JVMTIFunctionsSource.java:869
        Pointer anchor = prologue(env);
        tracePrologue("GetTag", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (tag_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            Env jvmtiEnv = JVMTI.getEnv(env);
            if (jvmtiEnv == null) {
                return JVMTI_ERROR_INVALID_ENVIRONMENT;
            }

            return jvmtiEnv.tags.getTag(object.unhand(), tag_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetTag");
        }
    }

    @VM_ENTRY_POINT
    private static int SetTag(Pointer env, JniHandle object, long tag) {
        // Source: JVMTIFunctionsSource.java:877
        Pointer anchor = prologue(env);
        tracePrologue("SetTag", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            Env jvmtiEnv = JVMTI.getEnv(env);
            if (jvmtiEnv == null) {
                return JVMTI_ERROR_INVALID_ENVIRONMENT;
            }

            return jvmtiEnv.tags.setTag(object.unhand(), tag);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("SetTag");
        }
    }

    @VM_ENTRY_POINT
    private static int ForceGarbageCollection(Pointer env) {
        // Source: JVMTIFunctionsSource.java:884
        Pointer anchor = prologue(env);
        tracePrologue("ForceGarbageCollection", anchor);
        try {
            System.gc();
            return JVMTI_ERROR_NONE;
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("ForceGarbageCollection");
        }
    }

    @VM_ENTRY_POINT
    private static int IterateOverObjectsReachableFromObject(Pointer env, JniHandle object, Address object_reference_callback, Pointer user_data) {
        // Source: JVMTIFunctionsSource.java:890
        Pointer anchor = prologue(env);
        tracePrologue("IterateOverObjectsReachableFromObject", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("IterateOverObjectsReachableFromObject");
        }
    }

    @VM_ENTRY_POINT
    private static int IterateOverReachableObjects(Pointer env, Address heap_root_callback, Address stack_ref_callback, Address object_ref_callback, Pointer user_data) {
        // Source: JVMTIFunctionsSource.java:895
        Pointer anchor = prologue(env);
        tracePrologue("IterateOverReachableObjects", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("IterateOverReachableObjects");
        }
    }

    @VM_ENTRY_POINT
    private static int IterateOverHeap(Pointer env, int object_filter, Address heap_object_callback, Pointer user_data) {
        // Source: JVMTIFunctionsSource.java:900
        Pointer anchor = prologue(env);
        tracePrologue("IterateOverHeap", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("IterateOverHeap");
        }
    }

    @VM_ENTRY_POINT
    private static int IterateOverInstancesOfClass(Pointer env, JniHandle klass, int object_filter, Address heap_object_callback, Pointer user_data) {
        // Source: JVMTIFunctionsSource.java:905
        Pointer anchor = prologue(env);
        tracePrologue("IterateOverInstancesOfClass", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("IterateOverInstancesOfClass");
        }
    }

    @VM_ENTRY_POINT
    private static native void reserved113();
        // Source: JVMTIFunctionsSource.java:910

    @VM_ENTRY_POINT
    private static int GetObjectsWithTags(Pointer env, int tag_count, Pointer tags, Pointer count_ptr, Pointer object_result_ptr, Pointer tag_result_ptr) {
        // Source: JVMTIFunctionsSource.java:913
        Pointer anchor = prologue(env);
        tracePrologue("GetObjectsWithTags", anchor);
        try {
            if (!(CAN_TAG_OBJECTS.get(CAPABILITIES.getPtr(env)))) {
                return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
            }
            if (tags.isZero() || count_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            return JVMTI.getEnv(env).tags.getObjectsWithTags(tag_count, tags, count_ptr, object_result_ptr, tag_result_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetObjectsWithTags");
        }
    }

    @VM_ENTRY_POINT
    private static int FollowReferences(Pointer env, int heap_filter, JniHandle klass, JniHandle initial_object, Pointer callbacks, Pointer user_data) {
        // Source: JVMTIFunctionsSource.java:920
        Pointer anchor = prologue(env);
        tracePrologue("FollowReferences", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("FollowReferences");
        }
    }

    @VM_ENTRY_POINT
    private static int IterateThroughHeap(Pointer env, int heap_filter, JniHandle klass, Pointer callbacks, Pointer user_data) {
        // Source: JVMTIFunctionsSource.java:925
        Pointer anchor = prologue(env);
        tracePrologue("IterateThroughHeap", anchor);
        try {
            if (!(phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (!(CAN_TAG_OBJECTS.get(CAPABILITIES.getPtr(env)))) {
                return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
            }
            if (callbacks.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            Class handleAsClass;
            try {
                handleAsClass = (Class) klass.unhand();
            } catch (ClassCastException ex) {
                return JVMTI_ERROR_INVALID_CLASS;
            }
            Env jvmtiEnv = JVMTI.getEnv(env);
            if (jvmtiEnv == null) {
                return JVMTI_ERROR_INVALID_ENVIRONMENT;
            }

            return JVMTIHeapFunctions.iterateThroughHeap(jvmtiEnv, heap_filter, handleAsClass, callbacks, user_data);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("IterateThroughHeap");
        }
    }

    @VM_ENTRY_POINT
    private static native void reserved117();
        // Source: JVMTIFunctionsSource.java:935

    @VM_ENTRY_POINT
    private static native void reserved118();
        // Source: JVMTIFunctionsSource.java:938

    @VM_ENTRY_POINT
    private static native void reserved119();
        // Source: JVMTIFunctionsSource.java:941

    @VM_ENTRY_POINT
    private static int SetJNIFunctionTable(Pointer env, Pointer function_table) {
        // Source: JVMTIFunctionsSource.java:944
        Pointer anchor = prologue(env);
        tracePrologue("SetJNIFunctionTable", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("SetJNIFunctionTable");
        }
    }

    @VM_ENTRY_POINT
    private static int GetJNIFunctionTable(Pointer env, Pointer function_table) {
        // Source: JVMTIFunctionsSource.java:949
        Pointer anchor = prologue(env);
        tracePrologue("GetJNIFunctionTable", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetJNIFunctionTable");
        }
    }

    @VM_ENTRY_POINT
    private static int SetEventCallbacks(Pointer env, Pointer callbacks, int size_of_callbacks) {
        // Source: JVMTIFunctionsSource.java:954
        Pointer anchor = prologue(env);
        tracePrologue("SetEventCallbacks", anchor);
        try {
            if (!(phase == JVMTI_PHASE_ONLOAD || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            Pointer envCallbacks = CALLBACKS.get(env).asPointer();
            Memory.copyBytes(callbacks, envCallbacks, Size.fromInt(size_of_callbacks));
            return JVMTI_ERROR_NONE;
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("SetEventCallbacks");
        }
    }

    @VM_ENTRY_POINT
    private static int GenerateEvents(Pointer env, int event_type) {
        // Source: JVMTIFunctionsSource.java:962
        Pointer anchor = prologue(env);
        tracePrologue("GenerateEvents", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GenerateEvents");
        }
    }

    @VM_ENTRY_POINT
    private static int GetExtensionFunctions(Pointer env, Pointer extension_count_ptr, Pointer extensions) {
        // Source: JVMTIFunctionsSource.java:967
        Pointer anchor = prologue(env);
        tracePrologue("GetExtensionFunctions", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetExtensionFunctions");
        }
    }

    @VM_ENTRY_POINT
    private static int GetExtensionEvents(Pointer env, Pointer extension_count_ptr, Pointer extensions) {
        // Source: JVMTIFunctionsSource.java:972
        Pointer anchor = prologue(env);
        tracePrologue("GetExtensionEvents", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetExtensionEvents");
        }
    }

    @VM_ENTRY_POINT
    private static int SetExtensionEventCallback(Pointer env, int extension_event_index, Address callback) {
        // Source: JVMTIFunctionsSource.java:977
        Pointer anchor = prologue(env);
        tracePrologue("SetExtensionEventCallback", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("SetExtensionEventCallback");
        }
    }

    @VM_ENTRY_POINT
    private static int DisposeEnvironment(Pointer env) {
        // Source: JVMTIFunctionsSource.java:982
        Pointer anchor = prologue(env);
        tracePrologue("DisposeEnvironment", anchor);
        try {
            // PHASES: ANY
            return JVMTI.disposeEnv(env);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("DisposeEnvironment");
        }
    }

    @VM_ENTRY_POINT
    private static int GetErrorName(Pointer env, int error, Pointer name_ptr) {
        // Source: JVMTIFunctionsSource.java:988
        Pointer anchor = prologue(env);
        tracePrologue("GetErrorName", anchor);
        try {
            // PHASES: ANY
            if (name_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            if (error < 0 || error > JVMTI_ERROR_MAX) {
                return JVMTI_ERROR_ILLEGAL_ARGUMENT;
            }
            byte[] nameBytes = JVMTIError.nameBytes[error];
            Pointer cstring = Memory.allocate(Size.fromInt(nameBytes.length + 1));
            CString.writeBytes(nameBytes, 0, nameBytes.length, cstring, nameBytes.length + 1);
            name_ptr.setWord(0, cstring);
            return JVMTI_ERROR_NONE;
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetErrorName");
        }
    }

    @VM_ENTRY_POINT
    private static int GetlongFormat(Pointer env, Pointer format_ptr) {
        // Source: JVMTIFunctionsSource.java:1002
        Pointer anchor = prologue(env);
        tracePrologue("GetlongFormat", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetlongFormat");
        }
    }

    @VM_ENTRY_POINT
    private static int GetSystemProperties(Pointer env, Pointer count_ptr, Pointer property_ptr) {
        // Source: JVMTIFunctionsSource.java:1007
        Pointer anchor = prologue(env);
        tracePrologue("GetSystemProperties", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetSystemProperties");
        }
    }

    @VM_ENTRY_POINT
    private static int GetSystemProperty(Pointer env, Pointer property, Pointer value_ptr) {
        // Source: JVMTIFunctionsSource.java:1012
        Pointer anchor = prologue(env);
        tracePrologue("GetSystemProperty", anchor);
        try {
            if (!(phase == JVMTI_PHASE_ONLOAD || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (property.isZero() || value_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            return JVMTI.getSystemProperty(env, property, value_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetSystemProperty");
        }
    }

    @VM_ENTRY_POINT
    private static int SetSystemProperty(Pointer env, Pointer property, Pointer value) {
        // Source: JVMTIFunctionsSource.java:1019
        Pointer anchor = prologue(env);
        tracePrologue("SetSystemProperty", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("SetSystemProperty");
        }
    }

    @VM_ENTRY_POINT
    private static int GetPhase(Pointer env, Pointer phase_ptr) {
        // Source: JVMTIFunctionsSource.java:1024
        Pointer anchor = prologue(env);
        tracePrologue("GetPhase", anchor);
        try {
            // PHASES: ANY
            if (phase_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            return JVMTI.getPhase(phase_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetPhase");
        }
    }

    @VM_ENTRY_POINT
    private static int GetCurrentThreadCpuTimerInfo(Pointer env, Pointer info_ptr) {
        // Source: JVMTIFunctionsSource.java:1031
        Pointer anchor = prologue(env);
        tracePrologue("GetCurrentThreadCpuTimerInfo", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetCurrentThreadCpuTimerInfo");
        }
    }

    @VM_ENTRY_POINT
    private static int GetCurrentThreadCpuTime(Pointer env, Pointer nanos_ptr) {
        // Source: JVMTIFunctionsSource.java:1036
        Pointer anchor = prologue(env);
        tracePrologue("GetCurrentThreadCpuTime", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetCurrentThreadCpuTime");
        }
    }

    @VM_ENTRY_POINT
    private static int GetThreadCpuTimerInfo(Pointer env, Pointer info_ptr) {
        // Source: JVMTIFunctionsSource.java:1041
        Pointer anchor = prologue(env);
        tracePrologue("GetThreadCpuTimerInfo", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetThreadCpuTimerInfo");
        }
    }

    @VM_ENTRY_POINT
    private static int GetThreadCpuTime(Pointer env, JniHandle thread, Pointer nanos_ptr) {
        // Source: JVMTIFunctionsSource.java:1046
        Pointer anchor = prologue(env);
        tracePrologue("GetThreadCpuTime", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetThreadCpuTime");
        }
    }

    @VM_ENTRY_POINT
    private static int GetTimerInfo(Pointer env, Pointer info_ptr) {
        // Source: JVMTIFunctionsSource.java:1051
        Pointer anchor = prologue(env);
        tracePrologue("GetTimerInfo", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetTimerInfo");
        }
    }

    @VM_ENTRY_POINT
    private static int GetTime(Pointer env, Pointer nanos_ptr) {
        // Source: JVMTIFunctionsSource.java:1056
        Pointer anchor = prologue(env);
        tracePrologue("GetTime", anchor);
        try {
            // PHASES: ANY
            if (nanos_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            nanos_ptr.setLong(System.nanoTime());
            return JVMTI_ERROR_NONE;
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetTime");
        }
    }

    @VM_ENTRY_POINT
    private static int GetPotentialCapabilities(Pointer env, Pointer capabilities_ptr) {
        // Source: JVMTIFunctionsSource.java:1064
        Pointer anchor = prologue(env);
        tracePrologue("GetPotentialCapabilities", anchor);
        try {
            if (!(phase == JVMTI_PHASE_ONLOAD || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (capabilities_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            // Currently we don't have any phase-limited or ownership limitations
            JVMTICapabilities.setAll(capabilities_ptr);
            return JVMTI_ERROR_NONE;
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetPotentialCapabilities");
        }
    }

    @VM_ENTRY_POINT
    private static native void reserved141();
        // Source: JVMTIFunctionsSource.java:1073

    @VM_ENTRY_POINT
    private static int AddCapabilities(Pointer env, Pointer capabilities_ptr) {
        // Source: JVMTIFunctionsSource.java:1076
        Pointer anchor = prologue(env);
        tracePrologue("AddCapabilities", anchor);
        try {
            if (!(phase == JVMTI_PHASE_ONLOAD || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (capabilities_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            return JVMTICapabilities.addCapabilities(env, capabilities_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("AddCapabilities");
        }
    }

    @VM_ENTRY_POINT
    private static int RelinquishCapabilities(Pointer env, Pointer capabilities_ptr) {
        // Source: JVMTIFunctionsSource.java:1083
        Pointer anchor = prologue(env);
        tracePrologue("RelinquishCapabilities", anchor);
        try {
            if (!(phase == JVMTI_PHASE_ONLOAD || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (capabilities_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            Pointer envCaps = CAPABILITIES.getPtr(env);
            for (int i = 0; i < JVMTICapabilities.values.length; i++) {
                JVMTICapabilities cap = JVMTICapabilities.values[i];
                if (cap.get(capabilities_ptr)) {
                   cap.set(envCaps, false);
                }
            }
            return JVMTI_ERROR_NONE;
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("RelinquishCapabilities");
        }
    }

    @VM_ENTRY_POINT
    private static int GetAvailableProcessors(Pointer env, Pointer processor_count_ptr) {
        // Source: JVMTIFunctionsSource.java:1097
        Pointer anchor = prologue(env);
        tracePrologue("GetAvailableProcessors", anchor);
        try {
            // PHASES: ANY
            if (processor_count_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            processor_count_ptr.setInt(Runtime.getRuntime().availableProcessors());
            return JVMTI_ERROR_NONE;
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetAvailableProcessors");
        }
    }

    @VM_ENTRY_POINT
    private static int GetClassVersionNumbers(Pointer env, JniHandle klass, Pointer minor_version_ptr, Pointer major_version_ptr) {
        // Source: JVMTIFunctionsSource.java:1105
        Pointer anchor = prologue(env);
        tracePrologue("GetClassVersionNumbers", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (minor_version_ptr.isZero() ||  minor_version_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetClassVersionNumbers");
        }
    }

    @VM_ENTRY_POINT
    private static int GetConstantPool(Pointer env, JniHandle klass, Pointer constant_pool_count_ptr, Pointer constant_pool_byte_count_ptr, Pointer constant_pool_bytes_ptr) {
        // Source: JVMTIFunctionsSource.java:1112
        Pointer anchor = prologue(env);
        tracePrologue("GetConstantPool", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetConstantPool");
        }
    }

    @VM_ENTRY_POINT
    private static int GetEnvironmentLocalStorage(Pointer env, Pointer data_ptr) {
        // Source: JVMTIFunctionsSource.java:1117
        Pointer anchor = prologue(env);
        tracePrologue("GetEnvironmentLocalStorage", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetEnvironmentLocalStorage");
        }
    }

    @VM_ENTRY_POINT
    private static int SetEnvironmentLocalStorage(Pointer env, Pointer data) {
        // Source: JVMTIFunctionsSource.java:1122
        Pointer anchor = prologue(env);
        tracePrologue("SetEnvironmentLocalStorage", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("SetEnvironmentLocalStorage");
        }
    }

    @VM_ENTRY_POINT
    private static int AddToBootstrapClassLoaderSearch(Pointer env, Pointer segment) {
        // Source: JVMTIFunctionsSource.java:1127
        Pointer anchor = prologue(env);
        tracePrologue("AddToBootstrapClassLoaderSearch", anchor);
        try {
            // PHASES ONLOAD,LIVE
            if (segment.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            return JVMTIClassFunctions.addToBootstrapClassLoaderSearch(env, segment);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("AddToBootstrapClassLoaderSearch");
        }
    }

    @VM_ENTRY_POINT
    private static int SetVerboseFlag(Pointer env, int flag, boolean value) {
        // Source: JVMTIFunctionsSource.java:1134
        Pointer anchor = prologue(env);
        tracePrologue("SetVerboseFlag", anchor);
        try {
            // PHASES: ANY
            switch (flag) {
                case JVMTI_VERBOSE_GC:
                    VMOptions.verboseOption.verboseGC = value;
                    break;
                case JVMTI_VERBOSE_CLASS:
                    VMOptions.verboseOption.verboseClass = value;
                    break;
                case JVMTI_VERBOSE_JNI:
                    VMOptions.verboseOption.verboseJNI = value;
                    break;
                case JVMTI_VERBOSE_OTHER:
                    VMOptions.verboseOption.verboseCompilation = value;
                    break;
                default:
                    return JVMTI_ERROR_ILLEGAL_ARGUMENT;
            }
            return JVMTI_ERROR_NONE;
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("SetVerboseFlag");
        }
    }

    @VM_ENTRY_POINT
    private static int AddToSystemClassLoaderSearch(Pointer env, Pointer segment) {
        // Source: JVMTIFunctionsSource.java:1156
        Pointer anchor = prologue(env);
        tracePrologue("AddToSystemClassLoaderSearch", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("AddToSystemClassLoaderSearch");
        }
    }

    @VM_ENTRY_POINT
    private static int RetransformClasses(Pointer env, int class_count, Pointer classes) {
        // Source: JVMTIFunctionsSource.java:1161
        Pointer anchor = prologue(env);
        tracePrologue("RetransformClasses", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("RetransformClasses");
        }
    }

    @VM_ENTRY_POINT
    private static int GetOwnedMonitorStackDepthInfo(Pointer env, JniHandle thread, Pointer monitor_info_count_ptr, Pointer monitor_info_ptr) {
        // Source: JVMTIFunctionsSource.java:1166
        Pointer anchor = prologue(env);
        tracePrologue("GetOwnedMonitorStackDepthInfo", anchor);
        try {
            return JVMTI_ERROR_NOT_AVAILABLE; // TODO
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetOwnedMonitorStackDepthInfo");
        }
    }

    @VM_ENTRY_POINT
    private static int GetObjectSize(Pointer env, JniHandle object, Pointer size_ptr) {
        // Source: JVMTIFunctionsSource.java:1171
        Pointer anchor = prologue(env);
        tracePrologue("GetObjectSize", anchor);
        try {
            if (!(phase == JVMTI_PHASE_START || phase == JVMTI_PHASE_LIVE)) {
                return JVMTI_ERROR_WRONG_PHASE;
            }
            if (size_ptr.isZero()) {
                return JVMTI_ERROR_NULL_POINTER;
            }
            return JVMTIClassFunctions.getObjectSize(object.unhand(), size_ptr);
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("GetObjectSize");
        }
    }

    /**
     * This function is an extension and appears in the extended JVMTI interface table,
     * as that is a convenient way to invoke it from native code. It's purpose is
     * simply to record the value of the C struct that denotes the JVMTI environment.
     */
    @VM_ENTRY_POINT
    private static int SetJVMTIEnv(Pointer env) {
        // Source: JVMTIFunctionsSource.java:1183
        Pointer anchor = prologue(env);
        tracePrologue("SetJVMTIEnv", anchor);
        try {
            JVMTI.setJVMTIEnv(env);
            return 0;
        } catch (Throwable t) {
            return JVMTI_ERROR_INTERNAL;
        } finally {
            epilogue(anchor);
            traceEpilogue("SetJVMTIEnv");
        }
    }

    private static final String[] methodNames = new String[] {
        "SetEventNotificationMode",
        "GetAllThreads",
        "SuspendThread",
        "ResumeThread",
        "StopThread",
        "InterruptThread",
        "GetThreadInfo",
        "GetOwnedMonitorInfo",
        "GetCurrentContendedMonitor",
        "RunAgentThread",
        "GetTopThreadGroups",
        "GetThreadGroupInfo",
        "GetThreadGroupChildren",
        "GetFrameCount",
        "GetThreadState",
        "GetCurrentThread",
        "GetFrameLocation",
        "NotifyFramePop",
        "GetLocalObject",
        "GetLocalInt",
        "GetLocalLong",
        "GetLocalFloat",
        "GetLocalDouble",
        "SetLocalObject",
        "SetLocalInt",
        "SetLocalLong",
        "SetLocalFloat",
        "SetLocalDouble",
        "CreateRawMonitor",
        "DestroyRawMonitor",
        "RawMonitorEnter",
        "RawMonitorExit",
        "RawMonitorWait",
        "RawMonitorNotify",
        "RawMonitorNotifyAll",
        "SetBreakpoint",
        "ClearBreakpoint",
        "SetFieldAccessWatch",
        "ClearFieldAccessWatch",
        "SetFieldModificationWatch",
        "ClearFieldModificationWatch",
        "IsModifiableClass",
        "Allocate",
        "Deallocate",
        "GetClassSignature",
        "GetClassStatus",
        "GetSourceFileName",
        "GetClassModifiers",
        "GetClassMethods",
        "GetClassFields",
        "GetImplementedInterfaces",
        "IsInterface",
        "IsArrayClass",
        "GetClassLoader",
        "GetObjectHashCode",
        "GetObjectMonitorUsage",
        "GetFieldName",
        "GetFieldDeclaringClass",
        "GetFieldModifiers",
        "IsFieldSynthetic",
        "GetMethodName",
        "GetMethodDeclaringClass",
        "GetMethodModifiers",
        "GetMaxLocals",
        "GetArgumentsSize",
        "GetLineNumberTable",
        "GetMethodLocation",
        "GetLocalVariableTable",
        "SetNativeMethodPrefix",
        "SetNativeMethodPrefixes",
        "GetBytecodes",
        "IsMethodNative",
        "IsMethodSynthetic",
        "GetLoadedClasses",
        "GetClassLoaderClasses",
        "PopFrame",
        "ForceEarlyReturnObject",
        "ForceEarlyReturnInt",
        "ForceEarlyReturnLong",
        "ForceEarlyReturnFloat",
        "ForceEarlyReturnDouble",
        "ForceEarlyReturnVoid",
        "RedefineClasses",
        "GetVersionNumber",
        "GetCapabilities",
        "GetSourceDebugExtension",
        "IsMethodObsolete",
        "SuspendThreadList",
        "ResumeThreadList",
        "GetAllStackTraces",
        "GetThreadListStackTraces",
        "GetThreadLocalStorage",
        "SetThreadLocalStorage",
        "GetStackTrace",
        "GetTag",
        "SetTag",
        "ForceGarbageCollection",
        "IterateOverObjectsReachableFromObject",
        "IterateOverReachableObjects",
        "IterateOverHeap",
        "IterateOverInstancesOfClass",
        "GetObjectsWithTags",
        "FollowReferences",
        "IterateThroughHeap",
        "SetJNIFunctionTable",
        "GetJNIFunctionTable",
        "SetEventCallbacks",
        "GenerateEvents",
        "GetExtensionFunctions",
        "GetExtensionEvents",
        "SetExtensionEventCallback",
        "DisposeEnvironment",
        "GetErrorName",
        "GetlongFormat",
        "GetSystemProperties",
        "GetSystemProperty",
        "SetSystemProperty",
        "GetPhase",
        "GetCurrentThreadCpuTimerInfo",
        "GetCurrentThreadCpuTime",
        "GetThreadCpuTimerInfo",
        "GetThreadCpuTime",
        "GetTimerInfo",
        "GetTime",
        "GetPotentialCapabilities",
        "AddCapabilities",
        "RelinquishCapabilities",
        "GetAvailableProcessors",
        "GetClassVersionNumbers",
        "GetConstantPool",
        "GetEnvironmentLocalStorage",
        "SetEnvironmentLocalStorage",
        "AddToBootstrapClassLoaderSearch",
        "SetVerboseFlag",
        "AddToSystemClassLoaderSearch",
        "RetransformClasses",
        "GetOwnedMonitorStackDepthInfo",
        "GetObjectSize",
        "SetJVMTIEnv"};
// END GENERATED CODE
}
