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
package com.sun.max.vm.ext.jvmti;

import static com.sun.max.vm.ext.jvmti.JVMTIConstants.*;

import java.lang.reflect.*;
import java.util.*;

import com.sun.max.annotate.*;
import com.sun.max.vm.actor.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.ext.jvmti.JVMTIThreadFunctions.*;
import com.sun.max.vm.hosted.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.thread.*;


public class JJVMTIAgentAdapter implements JJVMTI {

    static {
        JavaPrototype.registerInitializationCompleteCallback(new InitializationCompleteCallback());
    }

    protected static void registerCriticalMethods(Class<?> klass) {
        for (Method m : klass.getDeclaredMethods()) {
            new CriticalMethod(ClassMethodActor.fromJava(m), CallEntryPoint.OPTIMIZED_ENTRY_POINT);
        }
    }

    @HOSTED_ONLY
    private static class InitializationCompleteCallback implements JavaPrototype.InitializationCompleteCallback {
        @Override
        public void initializationComplete() {
            registerCriticalMethods(JJVMTIAgentAdapter.class);
        }
    }

    protected static final JJVMTIException notImplemented = new JJVMTIException(JVMTI_ERROR_NOT_AVAILABLE);

    protected JVMTI.JavaEnv env;

    /**
     * Register an agent.
     * @param the agent implementation subclass
     */
    public static JJVMTIAgentAdapter register(JJVMTIAgentAdapter agent) {
        agent.registerEnv(new JVMTI.JavaEnv((JJVMTI.EventCallbacks) agent));
        return agent;
    }

    protected void registerEnv(JVMTI.JavaEnv env) {
        this.env = env;
        JVMTI.setJVMTIJavaEnv(env);
    }

    @Override
    public void setEventNotificationMode(int mode, int event, Thread thread) throws JJVMTIException {
        JVMTIEvent.setEventNotificationMode(env, mode, event, thread);
    }

    @Override
    public Thread[] getAllThreads() throws JJVMTIException {
        return VmThreadMap.getThreads(true);
    }

    @Override
    public void suspendThread(Thread thread) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public void resumeThread(Thread thread) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public void stopThread(Thread thread, Throwable t) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public void interruptThread(Thread thread) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public void getThreadInfo(Thread thread) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public void getOwnedMonitorInfo(Thread thread) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public Object getCurrentContendedMonitor(Thread thread) throws JJVMTIException {
        throw notImplemented;
    }

    @Override
    public void runAgentThread(Runnable runnable, int priority) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public ThreadGroup[] getTopThreadGroups() throws JJVMTIException {
        throw notImplemented;
    }

    @Override
    public void getThreadGroupInfo(ThreadGroup threadGroup) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public void getThreadGroupChildren(ThreadGroup threadGroup) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public int getFrameCount(Thread thread) throws JJVMTIException {
        VmThread vmThread = JVMTIThreadFunctions.checkVmThread(thread);
        if (vmThread == null) {
            throw new JJVMTIException(JVMTI_ERROR_THREAD_NOT_ALIVE);
        }
        FindAppFramesStackTraceVisitor stackTraceVisitor = SingleThreadStackTraceVmOperation.invoke(vmThread);
        return stackTraceVisitor.stackElements.size();
    }

    @Override
    public int getThreadState(Thread thread) throws JJVMTIException {
        VmThread vmThread = JVMTIThreadFunctions.checkVmThread(thread);
        if (thread == null) {
            throw new JJVMTIException(JVMTI_ERROR_THREAD_NOT_ALIVE);
        }
        return JVMTIThreadFunctions.getThreadState(vmThread);
    }

    @Override
    public Thread getCurrentThread() throws JJVMTIException {
        return VmThread.current().javaThread();
    }

    @Override
    public FrameInfo getFrameLocation(Thread thread, int depth) throws JJVMTIException {
        return JVMTIThreadFunctions.getFrameLocation(thread, depth);
    }

    @Override
    public void notifyFramePop(Thread thread, int depth) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public Object getLocalObject(Thread thread, int depth, int slot) throws JJVMTIException {
        return JVMTIThreadFunctions.getLocalObject(thread, depth, slot);
    }

    @Override
    public int getLocalInt(Thread thread, int depth, int slot) throws JJVMTIException {
        return JVMTIThreadFunctions.getLocalInt(thread, depth, slot);
    }

    @Override
    public long getLocalLong(Thread thread, int depth, int slot) throws JJVMTIException {
        return JVMTIThreadFunctions.getLocalLong(thread, depth, slot);
    }

    @Override
    public float getLocalFloat(Thread thread, int depth, int slot) throws JJVMTIException {
        return JVMTIThreadFunctions.getLocalFloat(thread, depth, slot);
    }

    @Override
    public double getLocalDouble(Thread thread, int depth, int slot) throws JJVMTIException {
        return JVMTIThreadFunctions.getLocalDouble(thread, depth, slot);
    }

    @Override
    public void setLocalObject(Thread thread, int depth, int slot, Object value) throws JJVMTIException {
        JVMTIThreadFunctions.setLocalObject(thread, depth, slot, value);
    }

    @Override
    public void setLocalInt(Thread thread, int depth, int slot, int value) throws JJVMTIException {
        JVMTIThreadFunctions.setLocalInt(thread, depth, slot, value);
    }

    @Override
    public void setLocalLong(Thread thread, int depth, int slot, long value) throws JJVMTIException {
        JVMTIThreadFunctions.setLocalLong(thread, depth, slot, value);
    }

    @Override
    public void setLocalFloat(Thread thread, int depth, int slot, float value) throws JJVMTIException {
        JVMTIThreadFunctions.setLocalFloat(thread, depth, slot, value);
    }

    @Override
    public void setLocalDouble(Thread thread, int depth, int slot, double value) throws JJVMTIException {
        JVMTIThreadFunctions.setLocalDouble(thread, depth, slot, value);
    }

    @Override
    public int getObjectHashCode(Object object) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public ObjectMonitorUsage getObjectMonitorUsage(Object object) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public void setNativeMethodPrefix(String prefix) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public void setNativeMethodPrefixes(String[] prefixes) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public void popFrame(Thread thread) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public void forceEarlyReturnObject(Thread thread, Object value) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public void forceEarlyReturnInt(Thread thread, int value) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public void forceEarlyReturnLong(Thread thread, long value) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public void forceEarlyReturnFloat(Thread thread, float value) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public void forceEarlyReturnDouble(Thread thread, double value) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public void forceEarlyReturnVoid(Thread thread) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public int getVersionNumber() throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public long getCapabilities() throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public void suspendThreadList(Thread[] threads) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public void resumeThreadList(Thread[] threads) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public StackInfo[] getAllStackTraces(int maxFrameCount) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public void getThreadListStackTraces(Thread[] threads, int maxFrameCount) throws JJVMTIException {
        throw notImplemented;

    }

    private static class ObjectThreadLocal extends ThreadLocal<Object> {
        @Override
        public Object initialValue() {
            return null;
        }
    }

    private static final ObjectThreadLocal otl = new ObjectThreadLocal();

    @Override
    public Object getThreadLocalStorage(Thread thread) throws JJVMTIException {
        thread = JVMTIThreadFunctions.checkThread(thread);
        if (thread == null) {
            throw new JJVMTIException(JVMTI_ERROR_THREAD_NOT_ALIVE);
        }
        return otl.get();
    }

    @Override
    public void setThreadLocalStorage(Thread thread, Object data) throws JJVMTIException {
        thread = JVMTIThreadFunctions.checkThread(thread);
        if (thread == null) {
            throw new JJVMTIException(JVMTI_ERROR_THREAD_NOT_ALIVE);
        }
        otl.set(data);
    }

    @Override
    public int getStackTrace(Thread thread, int startDepth, int maxFrameCount, FrameInfo[] stackframeInfo) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public Object getTag(Object object) throws JJVMTIException {
        return env.tags.getTag(object);
    }

    @Override
    public void setTag(Object object, Object tag) throws JJVMTIException {
        env.tags.setTag(object, tag);
    }

    @Override
    public void forceGarbageCollection() throws JJVMTIException {
        System.gc();
    }

    @Override
    public void disposeEnvironment() throws JJVMTIException {
        JVMTI.disposeJVMTIJavaEnv(env);
    }

    @Override
    public String getErrorName(int error) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public int getJLocationFormat() throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public Properties getSystemProperties() throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public String getSystemProperty(String key) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public void setSystemProperty(String key, String value) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public int getPhase() throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public long getCurrentThreadCpuTime() throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public long getThreadCpuTime(Thread thread) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public long getTime() throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public EnumSet<JVMTICapabilities.E> getPotentialCapabilities() throws JJVMTIException {
        return JVMTICapabilities.getPotentialCapabilities(env);
    }

    @Override
    public void addCapabilities(EnumSet<JVMTICapabilities.E> caps) throws JJVMTIException {
        JVMTICapabilities.addCapabilities(env, caps);
    }

    @Override
    public void relinquishCapabilities(EnumSet<JVMTICapabilities.E> caps) throws JJVMTIException {
        JVMTICapabilities.relinquishCapabilities(env, caps);
    }

    @Override
    public int getAvailableProcessors() throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public Object getEnvironmentLocalStorage() throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public void setEnvironmentLocalStorage(Object data) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public void addToBootstrapClassLoaderSearch(String path) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public void setVerboseFlag(int arg1, boolean arg2) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public void addToSystemClassLoaderSearch(String path) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public MonitorStackDepthInfo[] getOwnedMonitorStackDepthInfo(Thread thread) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public long getObjectSize(Object object) throws JJVMTIException {
        throw notImplemented;
    }

    @Override
    public void setBreakpoint(MethodActor method, long location) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public void clearBreakpoint(MethodActor method, long location) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public void setFieldAccessWatch(FieldActor field) throws JJVMTIException {
        JVMTIFieldWatch.setWatch(field, JVMTIFieldWatch.ACCESS_STATE);
    }

    @Override
    public void clearFieldAccessWatch(FieldActor field) throws JJVMTIException {
        JVMTIFieldWatch.clearWatch(field, JVMTIFieldWatch.ACCESS_STATE);
    }

    @Override
    public void setFieldModificationWatch(FieldActor field) throws JJVMTIException {
        JVMTIFieldWatch.setWatch(field, JVMTIFieldWatch.MODIFICATION_STATE);
    }

    @Override
    public void clearFieldModificationWatch(FieldActor field) throws JJVMTIException {
        JVMTIFieldWatch.clearWatch(field, JVMTIFieldWatch.MODIFICATION_STATE);
    }

    @Override
    public boolean isModifiableClass(ClassActor klass) throws JJVMTIException {
        throw notImplemented;
    }

    @Override
    public String getClassSignature(ClassActor klass) throws JJVMTIException {
        return klass.typeDescriptor.string;
    }

    @Override
    public int getClassStatus(ClassActor klass) throws JJVMTIException {
        return JVMTIClassFunctions.getClassStatus(klass);
    }

    @Override
    public String getSourceFileName(ClassActor klass) throws JJVMTIException {
        throw notImplemented;
    }

    @Override
    public int getClassModifiers(ClassActor klass) throws JJVMTIException {
        throw notImplemented;
    }

    @Override
    public MethodActor[] getClassMethods(ClassActor klass) throws JJVMTIException {
        throw notImplemented;
    }

    @Override
    public FieldActor[] getClassFields(ClassActor klass) throws JJVMTIException {
        throw notImplemented;
    }

    @Override
    public ClassActor[] getImplementedInterfaces(ClassActor klass) throws JJVMTIException {
        throw notImplemented;
    }

    @Override
    public boolean isInterface(ClassActor klass) throws JJVMTIException {
        throw notImplemented;
    }

    @Override
    public boolean isArrayClass(ClassActor klass) throws JJVMTIException {
        throw notImplemented;
    }

    @Override
    public ClassLoader getClassLoader(ClassActor klass) throws JJVMTIException {
        throw notImplemented;
    }

    @Override
    public String getFieldName(FieldActor field) throws JJVMTIException {
        throw notImplemented;
    }

    @Override
    public String getFieldSignature(FieldActor field) throws JJVMTIException {
        throw notImplemented;
    }

    @Override
    public ClassActor getFieldDeclaringClass(FieldActor field) throws JJVMTIException {
        throw notImplemented;
    }

    @Override
    public int getFieldModifiers(FieldActor field) throws JJVMTIException {
        throw notImplemented;
    }

    @Override
    public boolean isFieldSynthetic(FieldActor field) throws JJVMTIException {
        throw notImplemented;
    }

    @Override
    public String getMethodName(MethodActor method) throws JJVMTIException {
        throw notImplemented;
    }

    @Override
    public String getMethodSignature(MethodActor method) throws JJVMTIException {
        throw notImplemented;
    }

    @Override
    public String getMethodGenericSignature(MethodActor method) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public ClassActor getMethodDeclaringClass(MethodActor method) throws JJVMTIException {
        throw notImplemented;
    }

    @Override
    public int getMethodModifiers(MethodActor method) throws JJVMTIException {
        throw notImplemented;
    }

    @Override
    public int getMaxLocals(MethodActor method) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public int getArgumentsSize(MethodActor method) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public LineNumberEntry[] getLineNumberTable(MethodActor method) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public MethodLocation getMethodLocation(MethodActor method) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public LocalVariableEntry[] getLocalVariableTable(MethodActor methodActor) throws JJVMTIException {
        return JVMTIClassFunctions.getLocalVariableTable((ClassMethodActor) methodActor);
    }

    @Override
    public byte[] getBytecodes(MethodActor method, byte[] useThis) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public boolean isMethodNative(MethodActor method) throws JJVMTIException {
        return method.isNative();
    }

    @Override
    public boolean isMethodSynthetic(MethodActor method) throws JJVMTIException {
        return (method.flags() & Actor.ACC_SYNTHETIC) != 0;
    }

    @Override
    public ClassActor[] getLoadedClasses() throws JJVMTIException {
        return JVMTIClassFunctions.getLoadedClassActors();
    }

    @Override
    public ClassActor[] getClassLoaderClasses(ClassLoader loader) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public void redefineClasses(ClassDefinition[] classDefinitions) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public String getSourceDebugExtension(ClassActor klass) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public boolean isMethodObsolete(MethodActor method) throws JJVMTIException {
        throw notImplemented;
    }

    @Override
    public ClassVersionInfo getClassVersionNumbers(ClassActor klasss, ClassVersionInfo classVersionInfo) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public int getConstantPool(ClassActor klass, byte[] pool) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public void retransformClasses(ClassActor[] klasses) throws JJVMTIException {
        throw notImplemented;

    }

    @Override
    public void iterateThroughHeap(int filter, ClassActor classActor, HeapCallbacks heapCallbacks, Object userData) throws JJVMTIException {
        JVMTIHeapFunctions.iterateThroughHeap(env, filter, classActor, heapCallbacks, userData);
    }


}
