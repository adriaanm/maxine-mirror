/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.vm.jni;

import static com.sun.max.vm.classfile.ErrorContext.*;
import static com.sun.max.vm.intrinsics.MaxineIntrinsicIDs.*;
import static com.sun.max.vm.thread.VmThread.*;
import static com.sun.max.vm.thread.VmThreadLocal.*;
import static com.sun.max.vm.jni.JniFunctions.JxxFunctionsLogger.*;

import java.lang.reflect.*;
import java.nio.*;
import java.util.*;

import com.sun.max.*;
import com.sun.max.annotate.*;
import com.sun.max.lang.*;
import com.sun.max.memory.*;
import com.sun.max.program.*;
import com.sun.max.unsafe.*;
import com.sun.max.util.*;
import com.sun.max.vm.*;
import com.sun.max.vm.MaxineVM.Phase;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.classfile.*;
import com.sun.max.vm.classfile.constant.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.heap.*;
import com.sun.max.vm.jdk.*;
import com.sun.max.vm.layout.*;
import com.sun.max.vm.log.*;
import com.sun.max.vm.log.VMLog.*;
import com.sun.max.vm.monitor.*;
import com.sun.max.vm.object.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.stack.*;
import com.sun.max.vm.thread.*;
import com.sun.max.vm.type.*;
import com.sun.max.vm.value.*;

/**
 * Upcalls from C that implement the <a href="http://java.sun.com/j2se/1.5.0/docs/guide/jni/spec/jniTOC.html">JNI Interface Functions</a>.
 * <p>
 * <b>DO NOT EDIT CODE BETWEEN "START GENERATED CODE" AND "END GENERATED CODE" IN THIS FILE.</b>
 * <p>
 * Instead, modify the corresponding source in JniFunctionsSource.java denoted by the "// Source: ..." comments.
 * Once finished with editing, execute {@link JniFunctionsGenerator} as a Java application to refresh this file.
 *
 * @see NativeInterfaces
 * @see JniFunctionsSource
 * @see Native/substrate/jni.c
 */
public final class JniFunctions {

    @RESET
    public static boolean TraceJNI;

    public static boolean CheckJNI;
    static {
        VMOptions.register(new VMOption("-Xcheck:jni", "Perform additional checks for JNI functions.") {
            @Override
            public boolean parseValue(Pointer optionValue) {
                CheckJNI = true;
                return true;
            }
        }, Phase.STARTING);
    }

    public static final int JNI_OK = 0;
    public static final int JNI_ERR = -1; /* unknown error */
    public static final int JNI_EDETACHED = -2; /* thread detached from the VM */
    public static final int JNI_EVERSION = -3; /* JNI version error */
    public static final int JNI_ENOMEM = -4; /* not enough memory */
    public static final int JNI_EEXIST = -5; /* VM already created */
    public static final int JNI_EINVAL = -6; /* invalid arguments */

    public static final int JNI_COMMIT = 1;
    public static final int JNI_ABORT = 2;

    @INTRINSIC(UNSAFE_CAST) public static native JniHandle asJniHandle(int value);
    @INTRINSIC(UNSAFE_CAST) public static native MethodID  asMethodID(int value);
    @INTRINSIC(UNSAFE_CAST) public static native FieldID   asFieldID(int value);
    @INTRINSIC(UNSAFE_CAST) public static native Pointer   asPointer(int value);

    /**
     * This method implements part of the prologue for entering a JNI upcall from native code.
     *
     * @param etla
     * @return an anchor for the JNI function frame. The anchor previous to this anchor is either that of the JNI stub
     *         frame that called out to native code or the native anchor of a thread that attached to the VM.
     */
    @INLINE
    public static Pointer reenterJavaFromNative(Pointer etla) {
        Word previousAnchor = LAST_JAVA_FRAME_ANCHOR.load(etla);
        Pointer anchor = JavaFrameAnchor.create(Word.zero(), Word.zero(), CodePointer.zero(), previousAnchor);
        // a JNI upcall is similar to a native method returning; reuse the native call epilogue sequence
        Snippets.nativeCallEpilogue0(etla, anchor);
        return anchor;
    }

    @INLINE
    public static Pointer prologue(Pointer env) {
        SafepointPoll.setLatchRegister(env.minus(JNI_ENV.offset));
        Pointer etla = ETLA.load(currentTLA());
        Pointer anchor = reenterJavaFromNative(etla);
        return anchor;
    }

    /**
     * This method implements the epilogue for leaving an JNI upcall. The steps performed are to
     * reset the thread-local information which stores the last Java caller SP, FP, and IP.     *
     */
    @INLINE
    public static void epilogue(Pointer anchor) {
        // returning from a JNI upcall is similar to a entering a native method returning; reuse the native call prologue sequence
        Pointer etla = ETLA.load(currentTLA());
        Snippets.nativeCallPrologue0(etla, JavaFrameAnchor.PREVIOUS.get(anchor));
    }

    private static class Triple implements Comparable<Triple> {
        public long counter;
        public long timer;
        public String methodName;
        @Override
        public int compareTo(Triple o) {
            long diff = o.timer - this.timer;
            return diff < 0 ? -1 : diff > 0 ? 1 : 0;
        }
    }

    /**
     * Print counters and timers for all of the JNI and JMM entrypoints.
     * To generate the necessary instrumentation code, set {@linkplain JniFunctionsGenerator#TIME_JNI_FUNCTIONS}
     * to true and run "max jnigen".
     */
    public static void printJniFunctionTimers() {
        if (INSTRUMENTED) {
            List<Triple> counters = new ArrayList<Triple>();
            try {
                for (Class clazz : new Class[] {JniFunctions.class, JmmFunctions.class}) {
                    Field[] fields = clazz.getDeclaredFields();
                    for (Field field : fields) {
                        if (Modifier.isStatic(field.getModifiers()) && field.getName().startsWith("COUNTER_")) {
                            Triple triple = new Triple();
                            triple.methodName = field.getName().substring("COUNTER_".length());
                            triple.counter = field.getLong(null);
                            triple.timer = clazz.getDeclaredField("TIMER_" + triple.methodName).getLong(null);
                            counters.add(triple);
                        }
                    }
                }
            } catch (Throwable ex) {
                throw ProgramError.unexpected(ex);
            }
            Collections.sort(counters);
            Log.println("JNI Function counters and timers:");
            Log.println("_______count_______ms__us__ns___method________");
            for (Triple triple : counters) {
                Log.println(String.format("%,12d %,16d   %s", triple.counter, triple.timer, triple.methodName));
            }
        }
    }

    /**
     * Logging/Tracing of JNI/JMM entry/exit.
     */
    static abstract class JxxFunctionsLogger extends VMLogger {
        static final Word ENTRY = Word.allOnes();
        static final Word EXIT = Word.zero();

        JxxFunctionsLogger(String name, int entryPointsLength) {
            super(name, entryPointsLength);
        }

        @Override
        protected void trace(Record r) {
            boolean entry = r.getArg(1).isAllOnes();
            Log.print("[Thread \"");
            Log.print(threadName(r.getThreadId()));
            Log.print("\" ");
            Log.print(entry ? "-->" : "<--");
            Log.print(" JNI upcall: ");
            Log.print(operationName(r.getOperation()));
            if (entry) {
                Pointer anchor = r.getArg(2).asPointer();
                Pointer jniStubAnchor = JavaFrameAnchor.PREVIOUS.get(anchor);
                final Address jniStubPC = jniStubAnchor.isZero() ? Address.zero() : JavaFrameAnchor.PC.get(jniStubAnchor).asAddress();
                if (!jniStubPC.isZero()) {
                    final TargetMethod nativeMethod = CodePointer.from(jniStubPC).toTargetMethod();
                    Log.print(", last down call: ");
                    FatalError.check(nativeMethod != null, "Could not find Java down call when entering JNI upcall");
                    Log.print(nativeMethod.classMethodActor().name.string);
                } else {
                    Log.print(", called from attached native thread");
                }
            }
            Log.println("]");
        }

    }

    /**
     * Logging/Tracing of JNI entry/exit.
     */
    private static class JniFunctionsLogger extends JxxFunctionsLogger {
        private static EntryPoints[] entryPoints = EntryPoints.values();

        private JniFunctionsLogger() {
            super("JNI", entryPoints.length);
        }

        @Override
        public String operationName(int op) {
            return entryPoints[op].name();
        }

        @Override
        protected void checkLogOptions() {
            super.checkLogOptions();
            TraceJNI = logger.traceEnabled();
        }
    }

    static JniFunctionsLogger logger = new JniFunctionsLogger();


    /*
     * DO NOT EDIT CODE BETWEEN "START GENERATED CODE" AND "END GENERATED CODE" IN THIS FILE.
     *
     * Instead, modify the corresponding source in JniFunctionsSource.java denoted by the "// Source: ..." comments.
     * Once finished with editing, execute JniFunctionsGenerator as a Java application to refresh this file.
     */

// START GENERATED CODE

    private static final boolean INSTRUMENTED = false;

    @VM_ENTRY_POINT
    private static native void reserved0();
        // Source: JniFunctionsSource.java:71

    @VM_ENTRY_POINT
    private static native void reserved1();
        // Source: JniFunctionsSource.java:74

    @VM_ENTRY_POINT
    private static native void reserved2();
        // Source: JniFunctionsSource.java:77

    @VM_ENTRY_POINT
    private static native void reserved3();
        // Source: JniFunctionsSource.java:80

    // Checkstyle: stop method name check

    @VM_ENTRY_POINT
    private static native int GetVersion(Pointer env);
        // Source: JniFunctionsSource.java:85

    private static String dottify(String slashifiedName) {
        return slashifiedName.replace('/', '.');
    }

    private static void traceReflectiveInvocation(MethodActor methodActor) {
        if (TraceJNI) {
            Log.print("[Thread \"");
            Log.print(VmThread.current().getName());
            Log.print("\" --> JNI invoke: ");
            Log.println(methodActor.format("%H.%n(%p)"));
        }
    }

    private static final Class[] defineClassParameterTypes = {String.class, byte[].class, int.class, int.class};

    @VM_ENTRY_POINT
    private static JniHandle DefineClass(Pointer env, Pointer slashifiedName, JniHandle classLoader, Pointer buffer, int length) throws ClassFormatError {
        // Source: JniFunctionsSource.java:103
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.DefineClass.ordinal(), ENTRY, anchor, env, slashifiedName, classLoader, buffer, Address.fromInt(length));
        }

        try {
            final byte[] bytes = new byte[length];
            Memory.readBytes(buffer, length, bytes);
            try {
                // TODO: find out whether already dottified class names should be rejected by this function
                String name = dottify(CString.utf8ToJava(slashifiedName));
                ClassLoader cl = (ClassLoader) classLoader.unhand();
                if (cl == null) {
                    cl = BootClassLoader.BOOT_CLASS_LOADER;
                }
                Class javaClass = ClassfileReader.defineClassActor(name, cl, bytes, 0, bytes.length, null, null, false).toJava();
                return JniHandles.createLocalHandle(javaClass);
            } catch (Utf8Exception utf8Exception) {
                throw classFormatError("Invalid class name");
            }
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.DefineClass.ordinal(), EXIT);
            }

        }
    }

    private static Class findClass(ClassLoader classLoader, String slashifiedName) throws ClassNotFoundException {
        if (slashifiedName.startsWith("[")) {
            TypeDescriptor descriptor = JavaTypeDescriptor.parseTypeDescriptor(slashifiedName);
            return descriptor.resolve(classLoader).toJava();
        }
        return classLoader.loadClass(dottify(slashifiedName));
    }

    @VM_ENTRY_POINT
    private static JniHandle FindClass(Pointer env, Pointer name) throws ClassNotFoundException {
        // Source: JniFunctionsSource.java:129
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.FindClass.ordinal(), ENTRY, anchor, env, name);
        }

        try {
            String className;
            try {
                className = CString.utf8ToJava(name);
            } catch (Utf8Exception utf8Exception) {
                throw new ClassNotFoundException();
            }
            // Skip our frame
            Class caller = JDK_sun_reflect_Reflection.getCallerClassForFindClass(1);
            ClassLoader classLoader = caller == null ? ClassLoader.getSystemClassLoader() : ClassActor.fromJava(caller).classLoader;
            final Class javaClass = findClass(classLoader, className);
            Snippets.makeClassInitialized(ClassActor.fromJava(javaClass));
            return JniHandles.createLocalHandle(javaClass);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.FindClass.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static MethodID FromReflectedMethod(Pointer env, JniHandle reflectedMethod) {
        // Source: JniFunctionsSource.java:145
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.FromReflectedMethod.ordinal(), ENTRY, anchor, env, reflectedMethod);
        }

        try {
            final MethodActor methodActor = MethodActor.fromJava((Method) reflectedMethod.unhand());
            return MethodID.fromMethodActor(methodActor);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asMethodID(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.FromReflectedMethod.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static FieldID FromReflectedField(Pointer env, JniHandle field) {
        // Source: JniFunctionsSource.java:151
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.FromReflectedField.ordinal(), ENTRY, anchor, env, field);
        }

        try {
            final FieldActor fieldActor = FieldActor.fromJava((Field) field.unhand());
            return FieldID.fromFieldActor(fieldActor);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asFieldID(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.FromReflectedField.ordinal(), EXIT);
            }

        }
    }

    private static Method ToReflectedMethod(MethodID methodID, boolean isStatic) throws NoSuchMethodException {
        final MethodActor methodActor = MethodID.toMethodActor(methodID);
        if (methodActor == null || methodActor.isStatic() != isStatic) {
            throw new NoSuchMethodException();
        }
        return methodActor.toJava();
    }

    @VM_ENTRY_POINT
    private static JniHandle ToReflectedMethod(Pointer env, JniHandle javaClass, MethodID methodID, boolean isStatic) throws NoSuchMethodException {
        // Source: JniFunctionsSource.java:165
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.ToReflectedMethod.ordinal(), ENTRY, anchor, env, javaClass, methodID, Address.fromInt(isStatic ? 1 : 0));
        }

        try {
            return JniHandles.createLocalHandle(ToReflectedMethod(methodID, isStatic));
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.ToReflectedMethod.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static JniHandle GetSuperclass(Pointer env, JniHandle subType) {
        // Source: JniFunctionsSource.java:170
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetSuperclass.ordinal(), ENTRY, anchor, env, subType);
        }

        try {
            return JniHandles.createLocalHandle(((Class) subType.unhand()).getSuperclass());
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetSuperclass.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static boolean IsAssignableFrom(Pointer env, JniHandle subType, JniHandle superType) {
        // Source: JniFunctionsSource.java:175
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.IsAssignableFrom.ordinal(), ENTRY, anchor, env, subType, superType);
        }

        try {
            return ClassActor.fromJava((Class) superType.unhand()).isAssignableFrom(ClassActor.fromJava((Class) subType.unhand()));
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return false;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.IsAssignableFrom.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static JniHandle ToReflectedField(Pointer env, JniHandle javaClass, FieldID fieldID, boolean isStatic) {
        // Source: JniFunctionsSource.java:180
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.ToReflectedField.ordinal(), ENTRY, anchor, env, javaClass, fieldID, Address.fromInt(isStatic ? 1 : 0));
        }

        try {
            final FieldActor fieldActor = FieldID.toFieldActor(fieldID);
            if (fieldActor == null || fieldActor.isStatic() != isStatic) {
                throw new NoSuchFieldError();
            }
            return JniHandles.createLocalHandle(fieldActor.toJava());
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.ToReflectedField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static int Throw(Pointer env, JniHandle throwable) {
        // Source: JniFunctionsSource.java:189
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.Throw.ordinal(), ENTRY, anchor, env, throwable);
        }

        try {
            VmThread.fromJniEnv(env).setJniException((Throwable) throwable.unhand());
            return JNI_OK;
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.Throw.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static int ThrowNew(Pointer env, JniHandle throwableClass, Pointer message) throws Throwable {
        // Source: JniFunctionsSource.java:195
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.ThrowNew.ordinal(), ENTRY, anchor, env, throwableClass, message);
        }

        try {
            final Class<Class<? extends Throwable>> type = null;
            Constructor<? extends Throwable> constructor = null;
            Class[] parameterTypes = null;
            if (message.isZero()) {
                parameterTypes = new Class[0];
            } else {
                parameterTypes = new Class[1];
                parameterTypes[0] = String.class;
            }
            constructor = Utils.cast(type, throwableClass.unhand()).getConstructor(parameterTypes);
            Throwable throwable = message.isZero() ? constructor.newInstance() : constructor.newInstance(CString.utf8ToJava(message));
            VmThread.fromJniEnv(env).setJniException(throwable);
            return JNI_OK;
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.ThrowNew.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static JniHandle ExceptionOccurred(Pointer env) {
        // Source: JniFunctionsSource.java:212
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.ExceptionOccurred.ordinal(), ENTRY, anchor, env);
        }

        try {
            return JniHandles.createLocalHandle(VmThread.fromJniEnv(env).jniException());
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.ExceptionOccurred.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void ExceptionDescribe(Pointer env) {
        // Source: JniFunctionsSource.java:217
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.ExceptionDescribe.ordinal(), ENTRY, anchor, env);
        }

        try {
            final Throwable exception = VmThread.fromJniEnv(env).jniException();
            if (exception != null) {
                exception.printStackTrace();
            }
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.ExceptionDescribe.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void ExceptionClear(Pointer env) {
        // Source: JniFunctionsSource.java:225
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.ExceptionClear.ordinal(), ENTRY, anchor, env);
        }

        try {
            VmThread.fromJniEnv(env).setJniException(null);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.ExceptionClear.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void FatalError(Pointer env, Pointer message) {
        // Source: JniFunctionsSource.java:230
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.FatalError.ordinal(), ENTRY, anchor, env, message);
        }

        try {
            try {
                FatalError.unexpected(CString.utf8ToJava(message));
            } catch (Utf8Exception utf8Exception) {
                FatalError.unexpected("fatal error with UTF8 error in message");
            }
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.FatalError.ordinal(), EXIT);
            }

        }
    }

    private static int PushLocalFrame0(Pointer env, int capacity) {
        JniHandles.pushLocalFrame(capacity);
        return JNI_OK;
    }

    @VM_ENTRY_POINT
    private static int PushLocalFrame(Pointer env, int capacity) {
        // Source: JniFunctionsSource.java:244
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.PushLocalFrame.ordinal(), ENTRY, anchor, env, Address.fromInt(capacity));
        }

        try {
            JniHandles.pushLocalFrame(capacity);
            return JNI_OK;
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.PushLocalFrame.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static JniHandle PopLocalFrame(Pointer env, JniHandle res) {
        // Source: JniFunctionsSource.java:250
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.PopLocalFrame.ordinal(), ENTRY, anchor, env, res);
        }

        try {
            return JniHandles.popLocalFrame(res);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.PopLocalFrame.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static JniHandle NewGlobalRef(Pointer env, JniHandle handle) {
        // Source: JniFunctionsSource.java:255
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.NewGlobalRef.ordinal(), ENTRY, anchor, env, handle);
        }

        try {
            return JniHandles.createGlobalHandle(handle.unhand());
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.NewGlobalRef.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void DeleteGlobalRef(Pointer env, JniHandle handle) {
        // Source: JniFunctionsSource.java:260
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.DeleteGlobalRef.ordinal(), ENTRY, anchor, env, handle);
        }

        try {
            JniHandles.destroyGlobalHandle(handle);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.DeleteGlobalRef.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void DeleteLocalRef(Pointer env, JniHandle handle) {
        // Source: JniFunctionsSource.java:265
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.DeleteLocalRef.ordinal(), ENTRY, anchor, env, handle);
        }

        try {
            JniHandles.destroyLocalHandle(handle);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.DeleteLocalRef.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static boolean IsSameObject(Pointer env, JniHandle object1, JniHandle object2) {
        // Source: JniFunctionsSource.java:270
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.IsSameObject.ordinal(), ENTRY, anchor, env, object1, object2);
        }

        try {
            return object1.unhand() == object2.unhand();
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return false;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.IsSameObject.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static JniHandle NewLocalRef(Pointer env, JniHandle object) {
        // Source: JniFunctionsSource.java:275
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.NewLocalRef.ordinal(), ENTRY, anchor, env, object);
        }

        try {
            return JniHandles.createLocalHandle(object.unhand());
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.NewLocalRef.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static int EnsureLocalCapacity(Pointer env, int capacity) {
        // Source: JniFunctionsSource.java:280
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.EnsureLocalCapacity.ordinal(), ENTRY, anchor, env, Address.fromInt(capacity));
        }

        try {
            // If this call fails, it will be with an OutOfMemoryError which will be
            // set as the pending exception for the current thread
            JniHandles.ensureLocalHandleCapacity(capacity);
            return JNI_OK;
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.EnsureLocalCapacity.ordinal(), EXIT);
            }

        }
    }

    private static Object allocObject(Class javaClass) throws InstantiationException {
        final ClassActor classActor = ClassActor.fromJava(javaClass);
        if (classActor.isTupleClass() && !classActor.isAbstract()) {
            return Heap.createTuple(classActor.dynamicHub());
        }
        throw new InstantiationException();
    }

    @VM_ENTRY_POINT
    private static JniHandle AllocObject(Pointer env, JniHandle javaClass) throws InstantiationException {
        // Source: JniFunctionsSource.java:296
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.AllocObject.ordinal(), ENTRY, anchor, env, javaClass);
        }

        try {
            return JniHandles.createLocalHandle(allocObject((Class) javaClass.unhand()));
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.AllocObject.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static native JniHandle NewObject(Pointer env, JniHandle javaClass, MethodID methodID /*, ...*/);
        // Source: JniFunctionsSource.java:301

    @VM_ENTRY_POINT
    private static native JniHandle NewObjectV(Pointer env, JniHandle javaClass, MethodID methodID, Pointer arguments);
        // Source: JniFunctionsSource.java:304

    @VM_ENTRY_POINT
    private static JniHandle NewObjectA(Pointer env, JniHandle javaClass, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:307
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.NewObjectA.ordinal(), ENTRY, anchor, env, javaClass, methodID, arguments);
        }

        try {

            final ClassActor classActor = ClassActor.fromJava((Class) javaClass.unhand());
            if (!(classActor instanceof TupleClassActor)) {
                throw new NoSuchMethodException();
            }
            final TupleClassActor tupleClassActor = (TupleClassActor) classActor;

            final MethodActor methodActor = MethodID.toMethodActor(methodID);
            if (methodActor == null || !methodActor.isInitializer()) {
                throw new NoSuchMethodException();
            }
            final VirtualMethodActor virtualMethodActor = tupleClassActor.findLocalVirtualMethodActor(methodActor.name, methodActor.descriptor());
            if (virtualMethodActor == null) {
                throw new NoSuchMethodException();
            }

            final SignatureDescriptor signature = virtualMethodActor.descriptor();
            final Value[] argumentValues = new Value[signature.numberOfParameters()];
            copyJValueArrayToValueArray(arguments, signature, argumentValues, 0);
            traceReflectiveInvocation(virtualMethodActor);
            return JniHandles.createLocalHandle(virtualMethodActor.invokeConstructor(argumentValues).asObject());
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.NewObjectA.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static JniHandle GetObjectClass(Pointer env, JniHandle object) {
        // Source: JniFunctionsSource.java:332
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetObjectClass.ordinal(), ENTRY, anchor, env, object);
        }

        try {
            final Class javaClass = object.unhand().getClass();
            return JniHandles.createLocalHandle(javaClass);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetObjectClass.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static boolean IsInstanceOf(Pointer env, JniHandle object, JniHandle javaType) {
        // Source: JniFunctionsSource.java:338
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.IsInstanceOf.ordinal(), ENTRY, anchor, env, object, javaType);
        }

        try {
            return ((Class) javaType.unhand()).isInstance(object.unhand());
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return false;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.IsInstanceOf.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static MethodID GetMethodID(Pointer env, JniHandle javaType, Pointer nameCString, Pointer descriptorCString) {
        // Source: JniFunctionsSource.java:343
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetMethodID.ordinal(), ENTRY, anchor, env, javaType, nameCString, descriptorCString);
        }

        try {
            final ClassActor classActor = ClassActor.fromJava((Class) javaType.unhand());
            Snippets.makeClassInitialized(classActor);
            try {
                final Utf8Constant name = SymbolTable.lookupSymbol(CString.utf8ToJava(nameCString));
                final SignatureDescriptor descriptor = SignatureDescriptor.lookup(CString.utf8ToJava(descriptorCString));
                if (name == null || descriptor == null) {
                    // The class should have been loaded (we have an instance of the class
                    // passed in) so the name and signature should already be in their respective canonicalization
                    // tables. If they're not there, the method doesn't exist.
                    throw new NoSuchMethodError();
                }
                final MethodActor methodActor = classActor.findMethodActor(name, descriptor);
                if (methodActor == null || methodActor.isStatic()) {
                    throw new NoSuchMethodError();
                }
                return MethodID.fromMethodActor(methodActor);
            } catch (Utf8Exception utf8Exception) {
                throw new NoSuchMethodError();
            }
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asMethodID(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetMethodID.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static native JniHandle CallObjectMethod(Pointer env, JniHandle object, MethodID methodID /*, ...*/);
        // Source: JniFunctionsSource.java:366

    @VM_ENTRY_POINT
    private static native JniHandle CallObjectMethodV(Pointer env, JniHandle object, MethodID methodID, Pointer vaList);
        // Source: JniFunctionsSource.java:369

    /**
     * Copies arguments from the native jvalue array at {@code arguments} into {@code argumentValues}. The number of
     * arguments copied is equal to {@code signature.getNumberOfParameters()}.
     *
     * @param signature describes the kind of each parameter
     * @param startIndex the index in {@code argumentValues} to start writing at
     */
    private static void copyJValueArrayToValueArray(Pointer arguments, SignatureDescriptor signature, Value[] argumentValues, int startIndex) {
        Pointer a = arguments;

        // This is equivalent to sizeof(jvalue) in C and gives us the size of each slot in a jvalue array.
        // Note that the size of the data in any given array element will be *at most* this size.
        final int jvalueSize = Kind.LONG.width.numberOfBytes;

        for (int i = 0; i < signature.numberOfParameters(); i++) {
            final int j = startIndex + i;
            switch (signature.parameterDescriptorAt(i).toKind().asEnum) {
                case BYTE: {
                    argumentValues[j] = ByteValue.from((byte) a.readInt(0));
                    break;
                }
                case BOOLEAN: {
                    argumentValues[j] = (a.readInt(0) != 0) ? BooleanValue.TRUE : BooleanValue.FALSE;
                    break;
                }
                case SHORT: {
                    argumentValues[j] = ShortValue.from((short) a.readInt(0));
                    break;
                }
                case CHAR: {
                    argumentValues[j] = CharValue.from((char) a.readInt(0));
                    break;
                }
                case INT: {
                    argumentValues[j] = IntValue.from(a.readInt(0));
                    break;
                }
                case FLOAT: {
                    argumentValues[j] = FloatValue.from(a.readFloat(0));
                    break;
                }
                case LONG: {
                    argumentValues[j] = LongValue.from(a.readLong(0));
                    break;
                }
                case DOUBLE: {
                    argumentValues[j] = DoubleValue.from(a.readDouble(0));
                    break;
                }
                case WORD: {
                    argumentValues[j] = new WordValue(a.readWord(0));
                    break;
                }
                case REFERENCE: {
                    final JniHandle jniHandle = a.readWord(0).asJniHandle();
                    argumentValues[j] = ReferenceValue.from(jniHandle.unhand());
                    break;
                }
                default: {
                    throw ProgramError.unexpected();
                }
            }
            a = a.plus(jvalueSize);
        }
    }

    private static Value checkResult(Kind expectedReturnKind, final MethodActor methodActor, Value result) {
        if (expectedReturnKind != result.kind()) {
            Value zero = expectedReturnKind.zeroValue();
            if (CheckJNI) {
                Log.println("JNI warning: returning " + zero + " for " + expectedReturnKind + " call to " + methodActor);
            }
            result = zero;
        }
        return result;
    }

    private static Value CallValueMethodA(Pointer env, JniHandle object, MethodID methodID, Pointer arguments, Kind expectedReturnKind) throws Exception {
        final MethodActor methodActor = MethodID.toMethodActor(methodID);
        if (methodActor == null) {
            throw new NoSuchMethodException("Invalid method ID " + methodID.asAddress().toLong());
        }
        if (methodActor.isStatic()) {
            throw new NoSuchMethodException(methodActor.toString() + " is static");
        }
        if (methodActor.isInitializer()) {
            throw new NoSuchMethodException(methodActor.toString() + " is an initializer");
        }
        final MethodActor selectedMethod;
        Object receiver = object.unhand();
        ClassActor holder = ObjectAccess.readClassActor(receiver);

        if (!methodActor.holder().isAssignableFrom(holder)) {
            throw new NoSuchMethodException(holder + " is not an instance of " + methodActor.holder());
        }
        selectedMethod = (MethodActor) holder.resolveMethodImpl(methodActor);
        final SignatureDescriptor signature = selectedMethod.descriptor();
        final Value[] argumentValues = new Value[1 + signature.numberOfParameters()];
        argumentValues[0] = ReferenceValue.from(object.unhand());
        copyJValueArrayToValueArray(arguments, signature, argumentValues, 1);
        traceReflectiveInvocation(selectedMethod);
        return checkResult(expectedReturnKind, methodActor, selectedMethod.invoke(argumentValues));

    }

    @VM_ENTRY_POINT
    private static JniHandle CallObjectMethodA(Pointer env, JniHandle object, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:477
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.CallObjectMethodA.ordinal(), ENTRY, anchor, env, object, methodID, arguments);
        }

        try {
            return JniHandles.createLocalHandle(CallValueMethodA(env, object, methodID, arguments, Kind.REFERENCE).asObject());
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.CallObjectMethodA.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static native boolean CallBooleanMethod(Pointer env, JniHandle object, MethodID methodID /*, ...*/);
        // Source: JniFunctionsSource.java:482

    @VM_ENTRY_POINT
    private static native boolean CallBooleanMethodV(Pointer env, JniHandle object, MethodID methodID, Pointer vaList);
        // Source: JniFunctionsSource.java:485

    @VM_ENTRY_POINT
    private static boolean CallBooleanMethodA(Pointer env, JniHandle object, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:488
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.CallBooleanMethodA.ordinal(), ENTRY, anchor, env, object, methodID, arguments);
        }

        try {
            return CallValueMethodA(env, object, methodID, arguments, Kind.BOOLEAN).asBoolean();
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return false;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.CallBooleanMethodA.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static native byte CallByteMethod(Pointer env, JniHandle object, MethodID methodID /*, ...*/);
        // Source: JniFunctionsSource.java:493

    @VM_ENTRY_POINT
    private static native byte CallByteMethodV(Pointer env, JniHandle object, MethodID methodID, Pointer arguments);
        // Source: JniFunctionsSource.java:496

    @VM_ENTRY_POINT
    private static byte CallByteMethodA(Pointer env, JniHandle object, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:499
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.CallByteMethodA.ordinal(), ENTRY, anchor, env, object, methodID, arguments);
        }

        try {
            return CallValueMethodA(env, object, methodID, arguments, Kind.BYTE).asByte();
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.CallByteMethodA.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static native char CallCharMethod(Pointer env, JniHandle object, MethodID methodID /*, ...*/);
        // Source: JniFunctionsSource.java:504

    @VM_ENTRY_POINT
    private static native char CallCharMethodV(Pointer env, JniHandle object, MethodID methodID, Pointer arguments);
        // Source: JniFunctionsSource.java:507

    @VM_ENTRY_POINT
    private static char CallCharMethodA(Pointer env, JniHandle object, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:510
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.CallCharMethodA.ordinal(), ENTRY, anchor, env, object, methodID, arguments);
        }

        try {
            return CallValueMethodA(env, object, methodID, arguments, Kind.CHAR).asChar();
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return  (char) JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.CallCharMethodA.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static native short CallShortMethod(Pointer env, JniHandle object, MethodID methodID /*, ...*/);
        // Source: JniFunctionsSource.java:515

    @VM_ENTRY_POINT
    private static native short CallShortMethodV(Pointer env, JniHandle object, MethodID methodID, Pointer arguments);
        // Source: JniFunctionsSource.java:518

    @VM_ENTRY_POINT
    private static short CallShortMethodA(Pointer env, JniHandle object, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:521
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.CallShortMethodA.ordinal(), ENTRY, anchor, env, object, methodID, arguments);
        }

        try {
            return CallValueMethodA(env, object, methodID, arguments, Kind.SHORT).asShort();
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.CallShortMethodA.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static native int CallIntMethod(Pointer env, JniHandle object, MethodID methodID /*, ...*/);
        // Source: JniFunctionsSource.java:526

    @VM_ENTRY_POINT
    private static native int CallIntMethodV(Pointer env, JniHandle object, MethodID methodID, Pointer arguments);
        // Source: JniFunctionsSource.java:529

    @VM_ENTRY_POINT
    private static int CallIntMethodA(Pointer env, JniHandle object, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:532
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.CallIntMethodA.ordinal(), ENTRY, anchor, env, object, methodID, arguments);
        }

        try {
            return CallValueMethodA(env, object, methodID, arguments, Kind.INT).asInt();
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.CallIntMethodA.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static native long CallLongMethod(Pointer env, JniHandle object, MethodID methodID /*, ...*/);
        // Source: JniFunctionsSource.java:537

    @VM_ENTRY_POINT
    private static native long CallLongMethodV(Pointer env, JniHandle object, MethodID methodID, Pointer arguments);
        // Source: JniFunctionsSource.java:540

    @VM_ENTRY_POINT
    private static long CallLongMethodA(Pointer env, JniHandle object, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:543
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.CallLongMethodA.ordinal(), ENTRY, anchor, env, object, methodID, arguments);
        }

        try {
            return CallValueMethodA(env, object, methodID, arguments, Kind.LONG).asLong();
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.CallLongMethodA.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static native float CallFloatMethod(Pointer env, JniHandle object, MethodID methodID /*, ...*/);
        // Source: JniFunctionsSource.java:548

    @VM_ENTRY_POINT
    private static native float CallFloatMethodV(Pointer env, JniHandle object, MethodID methodID, Pointer arguments);
        // Source: JniFunctionsSource.java:551

    @VM_ENTRY_POINT
    private static float CallFloatMethodA(Pointer env, JniHandle object, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:554
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.CallFloatMethodA.ordinal(), ENTRY, anchor, env, object, methodID, arguments);
        }

        try {
            return CallValueMethodA(env, object, methodID, arguments, Kind.FLOAT).asFloat();
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.CallFloatMethodA.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static native double CallDoubleMethod(Pointer env, JniHandle object, MethodID methodID /*, ...*/);
        // Source: JniFunctionsSource.java:559

    @VM_ENTRY_POINT
    private static native double CallDoubleMethodV(Pointer env, JniHandle object, MethodID methodID, Pointer arguments);
        // Source: JniFunctionsSource.java:562

    @VM_ENTRY_POINT
    private static double CallDoubleMethodA(Pointer env, JniHandle object, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:565
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.CallDoubleMethodA.ordinal(), ENTRY, anchor, env, object, methodID, arguments);
        }

        try {
            return CallValueMethodA(env, object, methodID, arguments, Kind.DOUBLE).asDouble();
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.CallDoubleMethodA.ordinal(), EXIT);
            }

        }
    }

    private static Value CallNonvirtualValueMethodA(Pointer env, JniHandle object, JniHandle javaClass, MethodID methodID, Pointer arguments, Kind expectedReturnKind) throws Exception {
        // Following Hotspot, the javaClass argument is ignored; we only need the methodId
        final MethodActor methodActor = MethodID.toMethodActor(methodID);
        if (methodActor == null || methodActor.isStatic() || methodActor.isInitializer()) {
            throw new NoSuchMethodException();
        }
        VirtualMethodActor virtualMethodActor;
        try {
            virtualMethodActor = (VirtualMethodActor) methodActor;
        } catch (ClassCastException ex) {
            throw new NoSuchMethodException();
        }
        final SignatureDescriptor signature = virtualMethodActor.descriptor();
        final Value[] argumentValues = new Value[1 + signature.numberOfParameters()];
        argumentValues[0] = ReferenceValue.from(object.unhand());
        copyJValueArrayToValueArray(arguments, signature, argumentValues, 1);
        traceReflectiveInvocation(virtualMethodActor);
        return checkResult(expectedReturnKind, methodActor, virtualMethodActor.invoke(argumentValues));
    }

    @VM_ENTRY_POINT
    private static native void CallVoidMethod(Pointer env, JniHandle object, MethodID methodID /*, ...*/);
        // Source: JniFunctionsSource.java:590

    @VM_ENTRY_POINT
    private static native void CallVoidMethodV(Pointer env, JniHandle object, MethodID methodID, Pointer arguments);
        // Source: JniFunctionsSource.java:593

    @VM_ENTRY_POINT
    private static void CallVoidMethodA(Pointer env, JniHandle object, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:596
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.CallVoidMethodA.ordinal(), ENTRY, anchor, env, object, methodID, arguments);
        }

        try {
            CallValueMethodA(env, object, methodID, arguments, Kind.VOID);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.CallVoidMethodA.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static native JniHandle CallNonvirtualObjectMethod(Pointer env, JniHandle object, JniHandle javaClass, MethodID methodID /*,...*/);
        // Source: JniFunctionsSource.java:601

    @VM_ENTRY_POINT
    private static native JniHandle CallNonvirtualObjectMethodV(Pointer env, JniHandle object, JniHandle javaClass, Pointer arguments);
        // Source: JniFunctionsSource.java:604

    @VM_ENTRY_POINT
    private static JniHandle CallNonvirtualObjectMethodA(Pointer env, JniHandle object, JniHandle javaClass, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:607
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.CallNonvirtualObjectMethodA.ordinal(), ENTRY, anchor, env, object, javaClass, methodID, arguments);
        }

        try {
            return JniHandles.createLocalHandle(CallNonvirtualValueMethodA(env, object, javaClass, methodID, arguments, Kind.REFERENCE).asObject());
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.CallNonvirtualObjectMethodA.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static native boolean CallNonvirtualBooleanMethod(Pointer env, JniHandle object, JniHandle javaClass, MethodID methodID /*,...*/);
        // Source: JniFunctionsSource.java:612

    @VM_ENTRY_POINT
    private static native boolean CallNonvirtualBooleanMethodV(Pointer env, JniHandle object, JniHandle javaClass, Pointer arguments);
        // Source: JniFunctionsSource.java:615

    @VM_ENTRY_POINT
    private static boolean CallNonvirtualBooleanMethodA(Pointer env, JniHandle object, JniHandle javaClass, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:618
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.CallNonvirtualBooleanMethodA.ordinal(), ENTRY, anchor, env, object, javaClass, methodID, arguments);
        }

        try {
            return CallNonvirtualValueMethodA(env, object, javaClass, methodID, arguments, Kind.BOOLEAN).asBoolean();
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return false;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.CallNonvirtualBooleanMethodA.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static native byte CallNonvirtualByteMethod(Pointer env, JniHandle object, JniHandle javaClass, MethodID methodID /*,...*/);
        // Source: JniFunctionsSource.java:623

    @VM_ENTRY_POINT
    private static native byte CallNonvirtualByteMethodV(Pointer env, JniHandle object, JniHandle javaClass, Pointer arguments);
        // Source: JniFunctionsSource.java:626

    @VM_ENTRY_POINT
    private static byte CallNonvirtualByteMethodA(Pointer env, JniHandle object, JniHandle javaClass, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:629
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.CallNonvirtualByteMethodA.ordinal(), ENTRY, anchor, env, object, javaClass, methodID, arguments);
        }

        try {
            return CallNonvirtualValueMethodA(env, object, javaClass, methodID, arguments, Kind.BYTE).asByte();
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.CallNonvirtualByteMethodA.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static native char CallNonvirtualCharMethod(Pointer env, JniHandle object, JniHandle javaClass, MethodID methodID /*,...*/);
        // Source: JniFunctionsSource.java:634

    @VM_ENTRY_POINT
    private static native char CallNonvirtualCharMethodV(Pointer env, JniHandle object, JniHandle javaClass, Pointer arguments);
        // Source: JniFunctionsSource.java:637

    @VM_ENTRY_POINT
    private static char CallNonvirtualCharMethodA(Pointer env, JniHandle object, JniHandle javaClass, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:640
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.CallNonvirtualCharMethodA.ordinal(), ENTRY, anchor, env, object, javaClass, methodID, arguments);
        }

        try {
            return CallNonvirtualValueMethodA(env, object, javaClass, methodID, arguments, Kind.CHAR).asChar();
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return  (char) JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.CallNonvirtualCharMethodA.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static native short CallNonvirtualShortMethod(Pointer env, JniHandle object, JniHandle javaClass, MethodID methodID /*,...*/);
        // Source: JniFunctionsSource.java:645

    @VM_ENTRY_POINT
    private static native short CallNonvirtualShortMethodV(Pointer env, JniHandle object, JniHandle javaClass, Pointer arguments);
        // Source: JniFunctionsSource.java:648

    @VM_ENTRY_POINT
    private static short CallNonvirtualShortMethodA(Pointer env, JniHandle object, JniHandle javaClass, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:651
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.CallNonvirtualShortMethodA.ordinal(), ENTRY, anchor, env, object, javaClass, methodID, arguments);
        }

        try {
            return CallNonvirtualValueMethodA(env, object, javaClass, methodID, arguments, Kind.SHORT).asShort();
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.CallNonvirtualShortMethodA.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static native int CallNonvirtualIntMethod(Pointer env, JniHandle object, JniHandle javaClass, MethodID methodID /*,...*/);
        // Source: JniFunctionsSource.java:656

    @VM_ENTRY_POINT
    private static native int CallNonvirtualIntMethodV(Pointer env, JniHandle object, JniHandle javaClass, Pointer arguments);
        // Source: JniFunctionsSource.java:659

    @VM_ENTRY_POINT
    private static int CallNonvirtualIntMethodA(Pointer env, JniHandle object, JniHandle javaClass, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:662
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.CallNonvirtualIntMethodA.ordinal(), ENTRY, anchor, env, object, javaClass, methodID, arguments);
        }

        try {
            return CallNonvirtualValueMethodA(env, object, javaClass, methodID, arguments, Kind.INT).asInt();
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.CallNonvirtualIntMethodA.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static native long CallNonvirtualLongMethod(Pointer env, JniHandle object, JniHandle javaClass, MethodID methodID /*,...*/);
        // Source: JniFunctionsSource.java:667

    @VM_ENTRY_POINT
    private static native long CallNonvirtualLongMethodV(Pointer env, JniHandle object, JniHandle javaClass, Pointer arguments);
        // Source: JniFunctionsSource.java:670

    @VM_ENTRY_POINT
    private static long CallNonvirtualLongMethodA(Pointer env, JniHandle object, JniHandle javaClass, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:673
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.CallNonvirtualLongMethodA.ordinal(), ENTRY, anchor, env, object, javaClass, methodID, arguments);
        }

        try {
            return CallNonvirtualValueMethodA(env, object, javaClass, methodID, arguments, Kind.LONG).asLong();
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.CallNonvirtualLongMethodA.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static native float CallNonvirtualFloatMethod(Pointer env, JniHandle object, JniHandle javaClass, MethodID methodID /*,...*/);
        // Source: JniFunctionsSource.java:678

    @VM_ENTRY_POINT
    private static native float CallNonvirtualFloatMethodV(Pointer env, JniHandle object, JniHandle javaClass, Pointer arguments);
        // Source: JniFunctionsSource.java:681

    @VM_ENTRY_POINT
    private static float CallNonvirtualFloatMethodA(Pointer env, JniHandle object, JniHandle javaClass, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:684
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.CallNonvirtualFloatMethodA.ordinal(), ENTRY, anchor, env, object, javaClass, methodID, arguments);
        }

        try {
            return CallNonvirtualValueMethodA(env, object, javaClass, methodID, arguments, Kind.FLOAT).asFloat();
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.CallNonvirtualFloatMethodA.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static native double CallNonvirtualDoubleMethod(Pointer env, JniHandle object, JniHandle javaClass, MethodID methodID /*,...*/);
        // Source: JniFunctionsSource.java:689

    @VM_ENTRY_POINT
    private static native double CallNonvirtualDoubleMethodV(Pointer env, JniHandle object, JniHandle javaClass, Pointer arguments);
        // Source: JniFunctionsSource.java:692

    @VM_ENTRY_POINT
    private static double CallNonvirtualDoubleMethodA(Pointer env, JniHandle object, JniHandle javaClass, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:695
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.CallNonvirtualDoubleMethodA.ordinal(), ENTRY, anchor, env, object, javaClass, methodID, arguments);
        }

        try {
            return CallNonvirtualValueMethodA(env, object, javaClass, methodID, arguments, Kind.DOUBLE).asDouble();
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.CallNonvirtualDoubleMethodA.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static native void CallNonvirtualVoidMethod(Pointer env, JniHandle object, JniHandle javaClass, MethodID methodID /*,...*/);
        // Source: JniFunctionsSource.java:700

    @VM_ENTRY_POINT
    private static native void CallNonvirtualVoidMethodV(Pointer env, JniHandle object, JniHandle javaClass, Pointer arguments);
        // Source: JniFunctionsSource.java:703

    @VM_ENTRY_POINT
    private static void CallNonvirtualVoidMethodA(Pointer env, JniHandle object, JniHandle javaClass, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:706
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.CallNonvirtualVoidMethodA.ordinal(), ENTRY, anchor, env, object, javaClass, methodID, arguments);
        }

        try {
            CallNonvirtualValueMethodA(env, object, javaClass, methodID, arguments, Kind.VOID);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.CallNonvirtualVoidMethodA.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static FieldID GetFieldID(Pointer env, JniHandle javaType, Pointer nameCString, Pointer descriptorCString) {
        // Source: JniFunctionsSource.java:711
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetFieldID.ordinal(), ENTRY, anchor, env, javaType, nameCString, descriptorCString);
        }

        try {
            final ClassActor classActor = ClassActor.fromJava((Class) javaType.unhand());
            Snippets.makeClassInitialized(classActor);
            try {

                final Utf8Constant name = SymbolTable.lookupSymbol(CString.utf8ToJava(nameCString));
                final TypeDescriptor descriptor = JavaTypeDescriptor.parseTypeDescriptor(CString.utf8ToJava(descriptorCString));
                if (name == null || descriptor == null) {
                    // The class should have been loaded (we have an instance of the class
                    // passed in) so the name and signature should already be in their respective canonicalization
                    // tables. If they're not there, the field doesn't exist.
                    throw new NoSuchFieldError();
                }
                final FieldActor fieldActor = classActor.findInstanceFieldActor(name, descriptor);
                if (fieldActor == null) {
                    throw new NoSuchFieldError(name.string);
                }
                return FieldID.fromFieldActor(fieldActor);
            } catch (Utf8Exception utf8Exception) {
                throw new NoSuchFieldError();
            }
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asFieldID(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetFieldID.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static JniHandle GetObjectField(Pointer env, JniHandle object, FieldID fieldID) {
        // Source: JniFunctionsSource.java:735
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetObjectField.ordinal(), ENTRY, anchor, env, object, fieldID);
        }

        try {
            return JniHandles.createLocalHandle(FieldID.toFieldActor(fieldID).getObject(object.unhand()));
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetObjectField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static boolean GetBooleanField(Pointer env, JniHandle object, FieldID fieldID) {
        // Source: JniFunctionsSource.java:740
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetBooleanField.ordinal(), ENTRY, anchor, env, object, fieldID);
        }

        try {
            return FieldID.toFieldActor(fieldID).getBoolean(object.unhand());
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return false;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetBooleanField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static byte GetByteField(Pointer env, JniHandle object, FieldID fieldID) {
        // Source: JniFunctionsSource.java:745
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetByteField.ordinal(), ENTRY, anchor, env, object, fieldID);
        }

        try {
            return FieldID.toFieldActor(fieldID).getByte(object.unhand());
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetByteField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static char GetCharField(Pointer env, JniHandle object, FieldID fieldID) {
        // Source: JniFunctionsSource.java:750
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetCharField.ordinal(), ENTRY, anchor, env, object, fieldID);
        }

        try {
            return FieldID.toFieldActor(fieldID).getChar(object.unhand());
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return  (char) JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetCharField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static short GetShortField(Pointer env, JniHandle object, FieldID fieldID) {
        // Source: JniFunctionsSource.java:755
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetShortField.ordinal(), ENTRY, anchor, env, object, fieldID);
        }

        try {
            return FieldID.toFieldActor(fieldID).getShort(object.unhand());
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetShortField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static int GetIntField(Pointer env, JniHandle object, FieldID fieldID) {
        // Source: JniFunctionsSource.java:760
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetIntField.ordinal(), ENTRY, anchor, env, object, fieldID);
        }

        try {
            return FieldID.toFieldActor(fieldID).getInt(object.unhand());
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetIntField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static long GetLongField(Pointer env, JniHandle object, FieldID fieldID) {
        // Source: JniFunctionsSource.java:765
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetLongField.ordinal(), ENTRY, anchor, env, object, fieldID);
        }

        try {
            return FieldID.toFieldActor(fieldID).getLong(object.unhand());
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetLongField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static float GetFloatField(Pointer env, JniHandle object, FieldID fieldID) {
        // Source: JniFunctionsSource.java:770
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetFloatField.ordinal(), ENTRY, anchor, env, object, fieldID);
        }

        try {
            return FieldID.toFieldActor(fieldID).getFloat(object.unhand());
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetFloatField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static double GetDoubleField(Pointer env, JniHandle object, FieldID fieldID) {
        // Source: JniFunctionsSource.java:775
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetDoubleField.ordinal(), ENTRY, anchor, env, object, fieldID);
        }

        try {
            return FieldID.toFieldActor(fieldID).getDouble(object.unhand());
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetDoubleField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void SetObjectField(Pointer env, JniHandle object, FieldID fieldID, JniHandle value) {
        // Source: JniFunctionsSource.java:780
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.SetObjectField.ordinal(), ENTRY, anchor, env, object, fieldID, value);
        }

        try {
            FieldID.toFieldActor(fieldID).setObject(object.unhand(), value.unhand());
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.SetObjectField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void SetBooleanField(Pointer env, JniHandle object, FieldID fieldID, boolean value) {
        // Source: JniFunctionsSource.java:785
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.SetBooleanField.ordinal(), ENTRY, anchor, env, object, fieldID, Address.fromInt(value ? 1 : 0));
        }

        try {
            FieldID.toFieldActor(fieldID).setBoolean(object.unhand(), value);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.SetBooleanField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void SetByteField(Pointer env, JniHandle object, FieldID fieldID, byte value) {
        // Source: JniFunctionsSource.java:790
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.SetByteField.ordinal(), ENTRY, anchor, env, object, fieldID, Address.fromInt(value));
        }

        try {
            FieldID.toFieldActor(fieldID).setByte(object.unhand(), value);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.SetByteField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void SetCharField(Pointer env, JniHandle object, FieldID fieldID, char value) {
        // Source: JniFunctionsSource.java:795
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.SetCharField.ordinal(), ENTRY, anchor, env, object, fieldID, Address.fromInt(value));
        }

        try {
            FieldID.toFieldActor(fieldID).setChar(object.unhand(), value);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.SetCharField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void SetShortField(Pointer env, JniHandle object, FieldID fieldID, short value) {
        // Source: JniFunctionsSource.java:800
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.SetShortField.ordinal(), ENTRY, anchor, env, object, fieldID, Address.fromInt(value));
        }

        try {
            FieldID.toFieldActor(fieldID).setShort(object.unhand(), value);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.SetShortField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void SetIntField(Pointer env, JniHandle object, FieldID fieldID, int value) {
        // Source: JniFunctionsSource.java:805
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.SetIntField.ordinal(), ENTRY, anchor, env, object, fieldID, Address.fromInt(value));
        }

        try {
            FieldID.toFieldActor(fieldID).setInt(object.unhand(), value);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.SetIntField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void SetLongField(Pointer env, JniHandle object, FieldID fieldID, long value) {
        // Source: JniFunctionsSource.java:810
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.SetLongField.ordinal(), ENTRY, anchor, env, object, fieldID, Address.fromLong(value));
        }

        try {
            FieldID.toFieldActor(fieldID).setLong(object.unhand(), value);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.SetLongField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void SetFloatField(Pointer env, JniHandle object, FieldID fieldID, float value) {
        // Source: JniFunctionsSource.java:815
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.SetFloatField.ordinal(), ENTRY, anchor, env, object, fieldID, Address.fromInt(Float.floatToRawIntBits(value)));
        }

        try {
            FieldID.toFieldActor(fieldID).setFloat(object.unhand(), value);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.SetFloatField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void SetDoubleField(Pointer env, JniHandle object, FieldID fieldID, double value) {
        // Source: JniFunctionsSource.java:820
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.SetDoubleField.ordinal(), ENTRY, anchor, env, object, fieldID, Address.fromLong(Double.doubleToRawLongBits(value)));
        }

        try {
            FieldID.toFieldActor(fieldID).setDouble(object.unhand(), value);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.SetDoubleField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static MethodID GetStaticMethodID(Pointer env, JniHandle javaType, Pointer nameCString, Pointer descriptorCString) {
        // Source: JniFunctionsSource.java:825
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetStaticMethodID.ordinal(), ENTRY, anchor, env, javaType, nameCString, descriptorCString);
        }

        try {
            final ClassActor classActor = ClassActor.fromJava((Class) javaType.unhand());
            Snippets.makeClassInitialized(classActor);
            try {
                final Utf8Constant name = SymbolTable.lookupSymbol(CString.utf8ToJava(nameCString));
                final SignatureDescriptor descriptor = SignatureDescriptor.create(CString.utf8ToJava(descriptorCString));
                if (name == null || descriptor == null) {
                    // The class should have been loaded (we have an instance of the class
                    // passed in) so the name and signature should already be in their respective canonicalization
                    // tables. If they're not there, the method doesn't exist.
                    throw new NoSuchMethodError();
                }
                final MethodActor methodActor = classActor.findStaticMethodActor(name, descriptor);
                if (methodActor == null) {
                    throw new NoSuchMethodError(classActor + "." + name.string);
                }
                return MethodID.fromMethodActor(methodActor);
            } catch (Utf8Exception utf8Exception) {
                throw new NoSuchMethodError();
            }
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asMethodID(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetStaticMethodID.ordinal(), EXIT);
            }

        }
    }

    private static Value CallStaticValueMethodA(Pointer env, JniHandle javaClass, MethodID methodID, Pointer arguments, Kind expectedReturnKind) throws Exception {
        final ClassActor classActor = ClassActor.fromJava((Class) javaClass.unhand());
        if (!(classActor instanceof TupleClassActor)) {
            throw new NoSuchMethodException(classActor + " is not a class with static methods");
        }

        final MethodActor methodActor = MethodID.toMethodActor(methodID);
        if (methodActor == null) {
            throw new NoSuchMethodException("Invalid method ID " + methodID.asAddress().toLong());
        }
        if (!methodActor.isStatic()) {
            throw new NoSuchMethodException(methodActor + " is not static");
        }
        if (!javaClass.isZero() && !methodActor.holder().toJava().isAssignableFrom((Class) javaClass.unhand())) {
            throw new NoSuchMethodException(javaClass.unhand() + " is not a subclass of " + methodActor.holder());
        }

        final SignatureDescriptor signature = methodActor.descriptor();
        final Value[] argumentValues = new Value[signature.numberOfParameters()];
        copyJValueArrayToValueArray(arguments, signature, argumentValues, 0);
        traceReflectiveInvocation(methodActor);
        return checkResult(expectedReturnKind, methodActor, methodActor.invoke(argumentValues));
    }

    @VM_ENTRY_POINT
    private static native JniHandle CallStaticObjectMethod(Pointer env, JniHandle javaClass, MethodID methodID /*, ...*/);
        // Source: JniFunctionsSource.java:872

    @VM_ENTRY_POINT
    private static native JniHandle CallStaticObjectMethodV(Pointer env, JniHandle javaClass, MethodID methodID, Pointer arguments);
        // Source: JniFunctionsSource.java:875

    @VM_ENTRY_POINT
    private static JniHandle CallStaticObjectMethodA(Pointer env, JniHandle javaClass, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:878
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.CallStaticObjectMethodA.ordinal(), ENTRY, anchor, env, javaClass, methodID, arguments);
        }

        try {
            return JniHandles.createLocalHandle(CallStaticValueMethodA(env, javaClass, methodID, arguments, Kind.REFERENCE).asObject());
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.CallStaticObjectMethodA.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static native boolean CallStaticBooleanMethod(Pointer env, JniHandle javaClass, MethodID methodID /*, ...*/);
        // Source: JniFunctionsSource.java:883

    @VM_ENTRY_POINT
    private static native boolean CallStaticBooleanMethodV(Pointer env, JniHandle javaClass, MethodID methodID, Pointer arguments);
        // Source: JniFunctionsSource.java:886

    @VM_ENTRY_POINT
    private static boolean CallStaticBooleanMethodA(Pointer env, JniHandle javaClass, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:889
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.CallStaticBooleanMethodA.ordinal(), ENTRY, anchor, env, javaClass, methodID, arguments);
        }

        try {
            return CallStaticValueMethodA(env, javaClass, methodID, arguments, Kind.BOOLEAN).asBoolean();
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return false;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.CallStaticBooleanMethodA.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static native byte CallStaticByteMethod(Pointer env, JniHandle javaClass, MethodID methodID /*, ...*/);
        // Source: JniFunctionsSource.java:894

    @VM_ENTRY_POINT
    private static native byte CallStaticByteMethodV(Pointer env, JniHandle javaClass, MethodID methodID, Pointer arguments);
        // Source: JniFunctionsSource.java:897

    @VM_ENTRY_POINT
    private static byte CallStaticByteMethodA(Pointer env, JniHandle javaClass, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:900
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.CallStaticByteMethodA.ordinal(), ENTRY, anchor, env, javaClass, methodID, arguments);
        }

        try {
            return CallStaticValueMethodA(env, javaClass, methodID, arguments, Kind.BYTE).asByte();
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.CallStaticByteMethodA.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static native char CallStaticCharMethod(Pointer env, JniHandle javaClass, MethodID methodID /*, ...*/);
        // Source: JniFunctionsSource.java:905

    @VM_ENTRY_POINT
    private static native char CallStaticCharMethodV(Pointer env, JniHandle javaClass, MethodID methodID, Pointer arguments);
        // Source: JniFunctionsSource.java:908

    @VM_ENTRY_POINT
    private static char CallStaticCharMethodA(Pointer env, JniHandle javaClass, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:911
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.CallStaticCharMethodA.ordinal(), ENTRY, anchor, env, javaClass, methodID, arguments);
        }

        try {
            return CallStaticValueMethodA(env, javaClass, methodID, arguments, Kind.CHAR).asChar();
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return  (char) JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.CallStaticCharMethodA.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static native short CallStaticShortMethod(Pointer env, JniHandle javaClass, MethodID methodID /*, ...*/);
        // Source: JniFunctionsSource.java:916

    @VM_ENTRY_POINT
    private static native short CallStaticShortMethodV(Pointer env, JniHandle javaClass, MethodID methodID, Pointer arguments);
        // Source: JniFunctionsSource.java:919

    @VM_ENTRY_POINT
    private static short CallStaticShortMethodA(Pointer env, JniHandle javaClass, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:922
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.CallStaticShortMethodA.ordinal(), ENTRY, anchor, env, javaClass, methodID, arguments);
        }

        try {
            return CallStaticValueMethodA(env, javaClass, methodID, arguments, Kind.SHORT).asShort();
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.CallStaticShortMethodA.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static native int CallStaticIntMethod(Pointer env, JniHandle javaClass, MethodID methodID /*, ...*/);
        // Source: JniFunctionsSource.java:927

    @VM_ENTRY_POINT
    private static native int CallStaticIntMethodV(Pointer env, JniHandle javaClass, MethodID methodID, Pointer arguments);
        // Source: JniFunctionsSource.java:930

    @VM_ENTRY_POINT
    private static int CallStaticIntMethodA(Pointer env, JniHandle javaClass, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:933
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.CallStaticIntMethodA.ordinal(), ENTRY, anchor, env, javaClass, methodID, arguments);
        }

        try {
            return CallStaticValueMethodA(env, javaClass, methodID, arguments, Kind.INT).asInt();
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.CallStaticIntMethodA.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static native long CallStaticLongMethod(Pointer env, JniHandle javaClass, MethodID methodID /*, ...*/);
        // Source: JniFunctionsSource.java:938

    @VM_ENTRY_POINT
    private static native long CallStaticLongMethodV(Pointer env, JniHandle javaClass, MethodID methodID, Pointer arguments);
        // Source: JniFunctionsSource.java:941

    @VM_ENTRY_POINT
    private static long CallStaticLongMethodA(Pointer env, JniHandle javaClass, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:944
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.CallStaticLongMethodA.ordinal(), ENTRY, anchor, env, javaClass, methodID, arguments);
        }

        try {
            return CallStaticValueMethodA(env, javaClass, methodID, arguments, Kind.LONG).asLong();
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.CallStaticLongMethodA.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static native float CallStaticFloatMethod(Pointer env, JniHandle javaClass, MethodID methodID /*, ...*/);
        // Source: JniFunctionsSource.java:949

    @VM_ENTRY_POINT
    private static native float CallStaticFloatMethodV(Pointer env, JniHandle javaClass, MethodID methodID, Pointer arguments);
        // Source: JniFunctionsSource.java:952

    @VM_ENTRY_POINT
    private static float CallStaticFloatMethodA(Pointer env, JniHandle javaClass, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:955
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.CallStaticFloatMethodA.ordinal(), ENTRY, anchor, env, javaClass, methodID, arguments);
        }

        try {
            return CallStaticValueMethodA(env, javaClass, methodID, arguments, Kind.FLOAT).asFloat();
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.CallStaticFloatMethodA.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static native double CallStaticDoubleMethod(Pointer env, JniHandle javaClass, MethodID methodID /*, ...*/);
        // Source: JniFunctionsSource.java:960

    @VM_ENTRY_POINT
    private static native double CallStaticDoubleMethodV(Pointer env, JniHandle javaClass, MethodID methodID, Pointer arguments);
        // Source: JniFunctionsSource.java:963

    @VM_ENTRY_POINT
    private static double CallStaticDoubleMethodA(Pointer env, JniHandle javaClass, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:966
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.CallStaticDoubleMethodA.ordinal(), ENTRY, anchor, env, javaClass, methodID, arguments);
        }

        try {
            return CallStaticValueMethodA(env, javaClass, methodID, arguments, Kind.DOUBLE).asDouble();
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.CallStaticDoubleMethodA.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static native void CallStaticVoidMethod(Pointer env, JniHandle javaClass, MethodID methodID /*, ...*/);
        // Source: JniFunctionsSource.java:971

    @VM_ENTRY_POINT
    private static native void CallStaticVoidMethodV(Pointer env, JniHandle javaClass, MethodID methodID, Pointer arguments);
        // Source: JniFunctionsSource.java:974

    @VM_ENTRY_POINT
    private static void CallStaticVoidMethodA(Pointer env, JniHandle javaClass, MethodID methodID, Pointer arguments) throws Exception {
        // Source: JniFunctionsSource.java:977
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.CallStaticVoidMethodA.ordinal(), ENTRY, anchor, env, javaClass, methodID, arguments);
        }

        try {
            CallStaticValueMethodA(env, javaClass, methodID, arguments, Kind.VOID);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.CallStaticVoidMethodA.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static FieldID GetStaticFieldID(Pointer env, JniHandle javaType, Pointer nameCString, Pointer descriptorCString) {
        // Source: JniFunctionsSource.java:982
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetStaticFieldID.ordinal(), ENTRY, anchor, env, javaType, nameCString, descriptorCString);
        }

        try {
            final ClassActor classActor = ClassActor.fromJava((Class) javaType.unhand());
            Snippets.makeClassInitialized(classActor);
            try {
                final Utf8Constant name = SymbolTable.lookupSymbol(CString.utf8ToJava(nameCString));
                final TypeDescriptor descriptor = TypeDescriptor.lookup(CString.utf8ToJava(descriptorCString));
                if (name == null || descriptor == null) {
                    // The class should have been loaded (we have an instance of the class
                    // passed in) so the name and signature should already be in their respective canonicalization
                    // tables. If they're not there, the field doesn't exist.
                    throw new NoSuchFieldError();
                }
                final FieldActor fieldActor = classActor.findStaticFieldActor(name, descriptor);
                if (fieldActor == null) {
                    throw new NoSuchFieldError();
                }
                return FieldID.fromFieldActor(fieldActor);
            } catch (Utf8Exception utf8Exception) {
                throw new NoSuchFieldError();
            }
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asFieldID(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetStaticFieldID.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static JniHandle GetStaticObjectField(Pointer env, JniHandle javaType, FieldID fieldID) {
        // Source: JniFunctionsSource.java:1005
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetStaticObjectField.ordinal(), ENTRY, anchor, env, javaType, fieldID);
        }

        try {
            return JniHandles.createLocalHandle(FieldID.toFieldActor(fieldID).getObject(null));
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetStaticObjectField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static boolean GetStaticBooleanField(Pointer env, JniHandle javaType, FieldID fieldID) {
        // Source: JniFunctionsSource.java:1010
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetStaticBooleanField.ordinal(), ENTRY, anchor, env, javaType, fieldID);
        }

        try {
            return FieldID.toFieldActor(fieldID).getBoolean(null);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return false;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetStaticBooleanField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static byte GetStaticByteField(Pointer env, JniHandle javaType, FieldID fieldID) {
        // Source: JniFunctionsSource.java:1015
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetStaticByteField.ordinal(), ENTRY, anchor, env, javaType, fieldID);
        }

        try {
            return FieldID.toFieldActor(fieldID).getByte(null);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetStaticByteField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static char GetStaticCharField(Pointer env, JniHandle javaType, FieldID fieldID) {
        // Source: JniFunctionsSource.java:1020
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetStaticCharField.ordinal(), ENTRY, anchor, env, javaType, fieldID);
        }

        try {
            return FieldID.toFieldActor(fieldID).getChar(null);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return  (char) JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetStaticCharField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static short GetStaticShortField(Pointer env, JniHandle javaType, FieldID fieldID) {
        // Source: JniFunctionsSource.java:1025
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetStaticShortField.ordinal(), ENTRY, anchor, env, javaType, fieldID);
        }

        try {
            return FieldID.toFieldActor(fieldID).getShort(null);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetStaticShortField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static int GetStaticIntField(Pointer env, JniHandle javaType, FieldID fieldID) {
        // Source: JniFunctionsSource.java:1030
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetStaticIntField.ordinal(), ENTRY, anchor, env, javaType, fieldID);
        }

        try {
            return FieldID.toFieldActor(fieldID).getInt(null);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetStaticIntField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static long GetStaticLongField(Pointer env, JniHandle javaType, FieldID fieldID) {
        // Source: JniFunctionsSource.java:1035
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetStaticLongField.ordinal(), ENTRY, anchor, env, javaType, fieldID);
        }

        try {
            return FieldID.toFieldActor(fieldID).getLong(null);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetStaticLongField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static float GetStaticFloatField(Pointer env, JniHandle javaType, FieldID fieldID) {
        // Source: JniFunctionsSource.java:1040
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetStaticFloatField.ordinal(), ENTRY, anchor, env, javaType, fieldID);
        }

        try {
            return FieldID.toFieldActor(fieldID).getFloat(null);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetStaticFloatField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static double GetStaticDoubleField(Pointer env, JniHandle javaType, FieldID fieldID) {
        // Source: JniFunctionsSource.java:1045
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetStaticDoubleField.ordinal(), ENTRY, anchor, env, javaType, fieldID);
        }

        try {
            return FieldID.toFieldActor(fieldID).getDouble(null);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetStaticDoubleField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void SetStaticObjectField(Pointer env, JniHandle javaType, FieldID fieldID, JniHandle value) {
        // Source: JniFunctionsSource.java:1050
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.SetStaticObjectField.ordinal(), ENTRY, anchor, env, javaType, fieldID, value);
        }

        try {
            FieldID.toFieldActor(fieldID).setObject(null, value.unhand());
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.SetStaticObjectField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void SetStaticBooleanField(Pointer env, JniHandle javaType, FieldID fieldID, boolean value) {
        // Source: JniFunctionsSource.java:1055
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.SetStaticBooleanField.ordinal(), ENTRY, anchor, env, javaType, fieldID, Address.fromInt(value ? 1 : 0));
        }

        try {
            FieldID.toFieldActor(fieldID).setBoolean(null, value);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.SetStaticBooleanField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void SetStaticByteField(Pointer env, JniHandle javaType, FieldID fieldID, byte value) {
        // Source: JniFunctionsSource.java:1060
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.SetStaticByteField.ordinal(), ENTRY, anchor, env, javaType, fieldID, Address.fromInt(value));
        }

        try {
            FieldID.toFieldActor(fieldID).setByte(null, value);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.SetStaticByteField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void SetStaticCharField(Pointer env, JniHandle javaType, FieldID fieldID, char value) {
        // Source: JniFunctionsSource.java:1065
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.SetStaticCharField.ordinal(), ENTRY, anchor, env, javaType, fieldID, Address.fromInt(value));
        }

        try {
            FieldID.toFieldActor(fieldID).setChar(null, value);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.SetStaticCharField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void SetStaticShortField(Pointer env, JniHandle javaType, FieldID fieldID, short value) {
        // Source: JniFunctionsSource.java:1070
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.SetStaticShortField.ordinal(), ENTRY, anchor, env, javaType, fieldID, Address.fromInt(value));
        }

        try {
            FieldID.toFieldActor(fieldID).setShort(null, value);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.SetStaticShortField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void SetStaticIntField(Pointer env, JniHandle javaType, FieldID fieldID, int value) {
        // Source: JniFunctionsSource.java:1075
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.SetStaticIntField.ordinal(), ENTRY, anchor, env, javaType, fieldID, Address.fromInt(value));
        }

        try {
            FieldID.toFieldActor(fieldID).setInt(null, value);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.SetStaticIntField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void SetStaticLongField(Pointer env, JniHandle javaType, FieldID fieldID, long value) {
        // Source: JniFunctionsSource.java:1080
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.SetStaticLongField.ordinal(), ENTRY, anchor, env, javaType, fieldID, Address.fromLong(value));
        }

        try {
            FieldID.toFieldActor(fieldID).setLong(null, value);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.SetStaticLongField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void SetStaticFloatField(Pointer env, JniHandle javaType, FieldID fieldID, float value) {
        // Source: JniFunctionsSource.java:1085
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.SetStaticFloatField.ordinal(), ENTRY, anchor, env, javaType, fieldID, Address.fromInt(Float.floatToRawIntBits(value)));
        }

        try {
            FieldID.toFieldActor(fieldID).setFloat(null, value);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.SetStaticFloatField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void SetStaticDoubleField(Pointer env, JniHandle javaType, FieldID fieldID, double value) {
        // Source: JniFunctionsSource.java:1090
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.SetStaticDoubleField.ordinal(), ENTRY, anchor, env, javaType, fieldID, Address.fromLong(Double.doubleToRawLongBits(value)));
        }

        try {
            FieldID.toFieldActor(fieldID).setDouble(null, value);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.SetStaticDoubleField.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static JniHandle NewString(Pointer env, Pointer chars, int length) {
        // Source: JniFunctionsSource.java:1095
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.NewString.ordinal(), ENTRY, anchor, env, chars, Address.fromInt(length));
        }

        try {
            final char[] charArray = new char[length];
            for (int i = 0; i < length; i++) {
                charArray[i] = chars.getChar(i);
            }
            return JniHandles.createLocalHandle(new String(charArray));
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.NewString.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static int GetStringLength(Pointer env, JniHandle string) {
        // Source: JniFunctionsSource.java:1104
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetStringLength.ordinal(), ENTRY, anchor, env, string);
        }

        try {
            return ((String) string.unhand()).length();
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetStringLength.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static JniHandle GetStringChars(Pointer env, JniHandle string, Pointer isCopy) {
        // Source: JniFunctionsSource.java:1109
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetStringChars.ordinal(), ENTRY, anchor, env, string, isCopy);
        }

        try {
            setCopyPointer(isCopy, true);
            return JniHandles.createLocalHandle(((String) string.unhand()).toCharArray());
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetStringChars.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void ReleaseStringChars(Pointer env, JniHandle string, Pointer chars) {
        // Source: JniFunctionsSource.java:1115
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.ReleaseStringChars.ordinal(), ENTRY, anchor, env, string, chars);
        }

        try {
            Memory.deallocate(chars);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.ReleaseStringChars.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static JniHandle NewStringUTF(Pointer env, Pointer utf) {
        // Source: JniFunctionsSource.java:1120
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.NewStringUTF.ordinal(), ENTRY, anchor, env, utf);
        }

        try {
            try {
                return JniHandles.createLocalHandle(CString.utf8ToJava(utf));
            } catch (Utf8Exception utf8Exception) {
                return JniHandle.zero();
            }
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.NewStringUTF.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static int GetStringUTFLength(Pointer env, JniHandle string) {
        // Source: JniFunctionsSource.java:1129
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetStringUTFLength.ordinal(), ENTRY, anchor, env, string);
        }

        try {
            return Utf8.utf8Length((String) string.unhand());
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetStringUTFLength.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static Pointer GetStringUTFChars(Pointer env, JniHandle string, Pointer isCopy) {
        // Source: JniFunctionsSource.java:1134
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetStringUTFChars.ordinal(), ENTRY, anchor, env, string, isCopy);
        }

        try {
            setCopyPointer(isCopy, true);
            return CString.utf8FromJava((String) string.unhand());
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asPointer(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetStringUTFChars.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void ReleaseStringUTFChars(Pointer env, JniHandle string, Pointer chars) {
        // Source: JniFunctionsSource.java:1140
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.ReleaseStringUTFChars.ordinal(), ENTRY, anchor, env, string, chars);
        }

        try {
            Memory.deallocate(chars);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.ReleaseStringUTFChars.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static int GetArrayLength(Pointer env, JniHandle array) {
        // Source: JniFunctionsSource.java:1145
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetArrayLength.ordinal(), ENTRY, anchor, env, array);
        }

        try {
            return Array.getLength(array.unhand());
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetArrayLength.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static JniHandle NewObjectArray(Pointer env, int length, JniHandle elementType, JniHandle initialElementValue) {
        // Source: JniFunctionsSource.java:1150
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.NewObjectArray.ordinal(), ENTRY, anchor, env, Address.fromInt(length), elementType, initialElementValue);
        }

        try {
            final Object array = Array.newInstance((Class) elementType.unhand(), length);
            final Object initialValue = initialElementValue.unhand();
            for (int i = 0; i < length; i++) {
                Array.set(array, i, initialValue);
            }
            return JniHandles.createLocalHandle(array);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.NewObjectArray.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static JniHandle GetObjectArrayElement(Pointer env, JniHandle array, int index) {
        // Source: JniFunctionsSource.java:1160
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetObjectArrayElement.ordinal(), ENTRY, anchor, env, array, Address.fromInt(index));
        }

        try {
            return JniHandles.createLocalHandle(((Object[]) array.unhand())[index]);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetObjectArrayElement.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void SetObjectArrayElement(Pointer env, JniHandle array, int index, JniHandle value) {
        // Source: JniFunctionsSource.java:1165
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.SetObjectArrayElement.ordinal(), ENTRY, anchor, env, array, Address.fromInt(index), value);
        }

        try {
            ((Object[]) array.unhand())[index] = value.unhand();
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.SetObjectArrayElement.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static JniHandle NewBooleanArray(Pointer env, int length) {
        // Source: JniFunctionsSource.java:1170
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.NewBooleanArray.ordinal(), ENTRY, anchor, env, Address.fromInt(length));
        }

        try {
            return JniHandles.createLocalHandle(new boolean[length]);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.NewBooleanArray.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static JniHandle NewByteArray(Pointer env, int length) {
        // Source: JniFunctionsSource.java:1175
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.NewByteArray.ordinal(), ENTRY, anchor, env, Address.fromInt(length));
        }

        try {
            return JniHandles.createLocalHandle(new byte[length]);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.NewByteArray.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static JniHandle NewCharArray(Pointer env, int length) {
        // Source: JniFunctionsSource.java:1180
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.NewCharArray.ordinal(), ENTRY, anchor, env, Address.fromInt(length));
        }

        try {
            return JniHandles.createLocalHandle(new char[length]);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.NewCharArray.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static JniHandle NewShortArray(Pointer env, int length) {
        // Source: JniFunctionsSource.java:1185
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.NewShortArray.ordinal(), ENTRY, anchor, env, Address.fromInt(length));
        }

        try {
            return JniHandles.createLocalHandle(new short[length]);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.NewShortArray.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static JniHandle NewIntArray(Pointer env, int length) {
        // Source: JniFunctionsSource.java:1190
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.NewIntArray.ordinal(), ENTRY, anchor, env, Address.fromInt(length));
        }

        try {
            return JniHandles.createLocalHandle(new int[length]);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.NewIntArray.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static JniHandle NewLongArray(Pointer env, int length) {
        // Source: JniFunctionsSource.java:1195
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.NewLongArray.ordinal(), ENTRY, anchor, env, Address.fromInt(length));
        }

        try {
            return JniHandles.createLocalHandle(new long[length]);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.NewLongArray.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static JniHandle NewFloatArray(Pointer env, int length) {
        // Source: JniFunctionsSource.java:1200
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.NewFloatArray.ordinal(), ENTRY, anchor, env, Address.fromInt(length));
        }

        try {
            return JniHandles.createLocalHandle(new float[length]);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.NewFloatArray.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static JniHandle NewDoubleArray(Pointer env, int length) {
        // Source: JniFunctionsSource.java:1205
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.NewDoubleArray.ordinal(), ENTRY, anchor, env, Address.fromInt(length));
        }

        try {
            return JniHandles.createLocalHandle(new double[length]);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.NewDoubleArray.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static Pointer GetBooleanArrayElements(Pointer env, JniHandle array, Pointer isCopy) {
        // Source: JniFunctionsSource.java:1210
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetBooleanArrayElements.ordinal(), ENTRY, anchor, env, array, isCopy);
        }

        try {
            return getBooleanArrayElements(array, isCopy);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asPointer(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetBooleanArrayElements.ordinal(), EXIT);
            }

        }
    }

    private static Pointer getBooleanArrayElements(JniHandle array, Pointer isCopy) throws OutOfMemoryError {
        setCopyPointer(isCopy, true);
        final boolean[] a = (boolean[]) array.unhand();
        final Pointer pointer = Memory.mustAllocate(a.length);
        for (int i = 0; i < a.length; i++) {
            pointer.setBoolean(i, a[i]);
        }
        return pointer;
    }

    @VM_ENTRY_POINT
    private static Pointer GetByteArrayElements(Pointer env, JniHandle array, Pointer isCopy) {
        // Source: JniFunctionsSource.java:1225
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetByteArrayElements.ordinal(), ENTRY, anchor, env, array, isCopy);
        }

        try {
            return getByteArrayElements(array, isCopy);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asPointer(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetByteArrayElements.ordinal(), EXIT);
            }

        }
    }

    private static Pointer getByteArrayElements(JniHandle array, Pointer isCopy) throws OutOfMemoryError {
        setCopyPointer(isCopy, true);
        final byte[] a = (byte[]) array.unhand();
        final Pointer pointer = Memory.mustAllocate(a.length * Kind.BYTE.width.numberOfBytes);
        for (int i = 0; i < a.length; i++) {
            pointer.setByte(i, a[i]);
        }
        return pointer;
    }

    @VM_ENTRY_POINT
    private static Pointer GetCharArrayElements(Pointer env, JniHandle array, Pointer isCopy) {
        // Source: JniFunctionsSource.java:1240
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetCharArrayElements.ordinal(), ENTRY, anchor, env, array, isCopy);
        }

        try {
            return getCharArrayElements(array, isCopy);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asPointer(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetCharArrayElements.ordinal(), EXIT);
            }

        }
    }

    private static Pointer getCharArrayElements(JniHandle array, Pointer isCopy) throws OutOfMemoryError {
        setCopyPointer(isCopy, true);
        final char[] a = (char[]) array.unhand();
        final Pointer pointer = Memory.mustAllocate(a.length * Kind.CHAR.width.numberOfBytes);
        for (int i = 0; i < a.length; i++) {
            pointer.setChar(i, a[i]);
        }
        return pointer;
    }

    @VM_ENTRY_POINT
    private static Pointer GetShortArrayElements(Pointer env, JniHandle array, Pointer isCopy) {
        // Source: JniFunctionsSource.java:1255
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetShortArrayElements.ordinal(), ENTRY, anchor, env, array, isCopy);
        }

        try {
            return getShortArrayElements(array, isCopy);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asPointer(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetShortArrayElements.ordinal(), EXIT);
            }

        }
    }

    private static Pointer getShortArrayElements(JniHandle array, Pointer isCopy) throws OutOfMemoryError {
        setCopyPointer(isCopy, true);
        final short[] a = (short[]) array.unhand();
        final Pointer pointer = Memory.mustAllocate(a.length * Kind.SHORT.width.numberOfBytes);
        for (int i = 0; i < a.length; i++) {
            pointer.setShort(i, a[i]);
        }
        return pointer;
    }

    @VM_ENTRY_POINT
    private static Pointer GetIntArrayElements(Pointer env, JniHandle array, Pointer isCopy) {
        // Source: JniFunctionsSource.java:1270
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetIntArrayElements.ordinal(), ENTRY, anchor, env, array, isCopy);
        }

        try {
            return getIntArrayElements(array, isCopy);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asPointer(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetIntArrayElements.ordinal(), EXIT);
            }

        }
    }

    private static Pointer getIntArrayElements(JniHandle array, Pointer isCopy) throws OutOfMemoryError {
        setCopyPointer(isCopy, true);
        final int[] a = (int[]) array.unhand();
        final Pointer pointer = Memory.mustAllocate(a.length * Kind.INT.width.numberOfBytes);
        for (int i = 0; i < a.length; i++) {
            pointer.setInt(i, a[i]);
        }
        return pointer;
    }

    @VM_ENTRY_POINT
    private static Pointer GetLongArrayElements(Pointer env, JniHandle array, Pointer isCopy) {
        // Source: JniFunctionsSource.java:1285
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetLongArrayElements.ordinal(), ENTRY, anchor, env, array, isCopy);
        }

        try {
            return getLongArrayElements(array, isCopy);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asPointer(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetLongArrayElements.ordinal(), EXIT);
            }

        }
    }

    private static Pointer getLongArrayElements(JniHandle array, Pointer isCopy) throws OutOfMemoryError {
        setCopyPointer(isCopy, true);
        final long[] a = (long[]) array.unhand();
        final Pointer pointer = Memory.mustAllocate(a.length * Kind.LONG.width.numberOfBytes);
        for (int i = 0; i < a.length; i++) {
            pointer.setLong(i, a[i]);
        }
        return pointer;
    }

    @VM_ENTRY_POINT
    private static Pointer GetFloatArrayElements(Pointer env, JniHandle array, Pointer isCopy) {
        // Source: JniFunctionsSource.java:1300
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetFloatArrayElements.ordinal(), ENTRY, anchor, env, array, isCopy);
        }

        try {
            return getFloatArrayElements(array, isCopy);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asPointer(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetFloatArrayElements.ordinal(), EXIT);
            }

        }
    }

    private static Pointer getFloatArrayElements(JniHandle array, Pointer isCopy) throws OutOfMemoryError {
        setCopyPointer(isCopy, true);
        final float[] a = (float[]) array.unhand();
        final Pointer pointer = Memory.mustAllocate(a.length * Kind.FLOAT.width.numberOfBytes);
        for (int i = 0; i < a.length; i++) {
            pointer.setFloat(i, a[i]);
        }
        return pointer;
    }

    @VM_ENTRY_POINT
    private static Pointer GetDoubleArrayElements(Pointer env, JniHandle array, Pointer isCopy) {
        // Source: JniFunctionsSource.java:1315
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetDoubleArrayElements.ordinal(), ENTRY, anchor, env, array, isCopy);
        }

        try {
            return getDoubleArrayElements(array, isCopy);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asPointer(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetDoubleArrayElements.ordinal(), EXIT);
            }

        }
    }

    private static Pointer getDoubleArrayElements(JniHandle array, Pointer isCopy) throws OutOfMemoryError {
        setCopyPointer(isCopy, true);
        final double[] a = (double[]) array.unhand();
        final Pointer pointer = Memory.mustAllocate(a.length * Kind.DOUBLE.width.numberOfBytes);
        for (int i = 0; i < a.length; i++) {
            pointer.setDouble(i, a[i]);
        }
        return pointer;
    }

    @VM_ENTRY_POINT
    private static void ReleaseBooleanArrayElements(Pointer env, JniHandle array, Pointer elements, int mode) {
        // Source: JniFunctionsSource.java:1330
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.ReleaseBooleanArrayElements.ordinal(), ENTRY, anchor, env, array, elements, Address.fromInt(mode));
        }

        try {
            releaseBooleanArrayElements(array, elements, mode);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.ReleaseBooleanArrayElements.ordinal(), EXIT);
            }

        }
    }

    private static void releaseBooleanArrayElements(JniHandle array, Pointer elements, int mode) {
        final boolean[] a = (boolean[]) array.unhand();
        if (mode == 0 || mode == JNI_COMMIT) {
            for (int i = 0; i < a.length; i++) {
                a[i] = elements.getBoolean(i);
            }
        }
        releaseElements(elements, mode);
    }

    @VM_ENTRY_POINT
    private static void ReleaseByteArrayElements(Pointer env, JniHandle array, Pointer elements, int mode) {
        // Source: JniFunctionsSource.java:1345
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.ReleaseByteArrayElements.ordinal(), ENTRY, anchor, env, array, elements, Address.fromInt(mode));
        }

        try {
            releaseByteArrayElements(array, elements, mode);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.ReleaseByteArrayElements.ordinal(), EXIT);
            }

        }
    }

    private static void releaseByteArrayElements(JniHandle array, Pointer elements, int mode) {
        final byte[] a = (byte[]) array.unhand();
        if (mode == 0 || mode == JNI_COMMIT) {
            for (int i = 0; i < a.length; i++) {
                a[i] = elements.getByte(i);
            }
        }
        releaseElements(elements, mode);
    }

    @VM_ENTRY_POINT
    private static void ReleaseCharArrayElements(Pointer env, JniHandle array, Pointer elements, int mode) {
        // Source: JniFunctionsSource.java:1360
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.ReleaseCharArrayElements.ordinal(), ENTRY, anchor, env, array, elements, Address.fromInt(mode));
        }

        try {
            releaseCharArrayElements(array, elements, mode);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.ReleaseCharArrayElements.ordinal(), EXIT);
            }

        }
    }

    private static void releaseCharArrayElements(JniHandle array, Pointer elements, int mode) {
        final char[] a = (char[]) array.unhand();
        if (mode == 0 || mode == JNI_COMMIT) {
            for (int i = 0; i < a.length; i++) {
                a[i] = elements.getChar(i);
            }
        }
        releaseElements(elements, mode);
    }

    @VM_ENTRY_POINT
    private static void ReleaseShortArrayElements(Pointer env, JniHandle array, Pointer elements, int mode) {
        // Source: JniFunctionsSource.java:1375
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.ReleaseShortArrayElements.ordinal(), ENTRY, anchor, env, array, elements, Address.fromInt(mode));
        }

        try {
            releaseShortArrayElements(array, elements, mode);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.ReleaseShortArrayElements.ordinal(), EXIT);
            }

        }
    }

    private static void releaseShortArrayElements(JniHandle array, Pointer elements, int mode) {
        final short[] a = (short[]) array.unhand();
        if (mode == 0 || mode == JNI_COMMIT) {
            for (int i = 0; i < a.length; i++) {
                a[i] = elements.getShort(i);
            }
        }
        releaseElements(elements, mode);
    }

    @VM_ENTRY_POINT
    private static void ReleaseIntArrayElements(Pointer env, JniHandle array, Pointer elements, int mode) {
        // Source: JniFunctionsSource.java:1390
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.ReleaseIntArrayElements.ordinal(), ENTRY, anchor, env, array, elements, Address.fromInt(mode));
        }

        try {
            releaseIntArrayElements(array, elements, mode);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.ReleaseIntArrayElements.ordinal(), EXIT);
            }

        }
    }

    private static void releaseIntArrayElements(JniHandle array, Pointer elements, int mode) {
        final int[] a = (int[]) array.unhand();
        if (mode == 0 || mode == JNI_COMMIT) {
            for (int i = 0; i < a.length; i++) {
                a[i] = elements.getInt(i);
            }
        }
        releaseElements(elements, mode);
    }

    @VM_ENTRY_POINT
    private static void ReleaseLongArrayElements(Pointer env, JniHandle array, Pointer elements, int mode) {
        // Source: JniFunctionsSource.java:1405
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.ReleaseLongArrayElements.ordinal(), ENTRY, anchor, env, array, elements, Address.fromInt(mode));
        }

        try {
            releaseLongArrayElements(array, elements, mode);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.ReleaseLongArrayElements.ordinal(), EXIT);
            }

        }
    }

    private static void releaseLongArrayElements(JniHandle array, Pointer elements, int mode) {
        final long[] a = (long[]) array.unhand();
        if (mode == 0 || mode == JNI_COMMIT) {
            for (int i = 0; i < a.length; i++) {
                a[i] = elements.getLong(i);
            }
        }
        releaseElements(elements, mode);
    }

    @VM_ENTRY_POINT
    private static void ReleaseFloatArrayElements(Pointer env, JniHandle array, Pointer elements, int mode) {
        // Source: JniFunctionsSource.java:1420
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.ReleaseFloatArrayElements.ordinal(), ENTRY, anchor, env, array, elements, Address.fromInt(mode));
        }

        try {
            releaseFloatArrayElements(array, elements, mode);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.ReleaseFloatArrayElements.ordinal(), EXIT);
            }

        }
    }

    private static void releaseFloatArrayElements(JniHandle array, Pointer elements, int mode) {
        final float[] a = (float[]) array.unhand();
        if (mode == 0 || mode == JNI_COMMIT) {
            for (int i = 0; i < a.length; i++) {
                a[i] = elements.getFloat(i);
            }
        }
        releaseElements(elements, mode);
    }

    @VM_ENTRY_POINT
    private static void ReleaseDoubleArrayElements(Pointer env, JniHandle array, Pointer elements, int mode) {
        // Source: JniFunctionsSource.java:1435
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.ReleaseDoubleArrayElements.ordinal(), ENTRY, anchor, env, array, elements, Address.fromInt(mode));
        }

        try {
            releaseDoubleArrayElements(array, elements, mode);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.ReleaseDoubleArrayElements.ordinal(), EXIT);
            }

        }
    }

    private static void releaseDoubleArrayElements(JniHandle array, Pointer elements, int mode) {
        final double[] a = (double[]) array.unhand();
        if (mode == 0 || mode == JNI_COMMIT) {
            for (int i = 0; i < a.length; i++) {
                a[i] = elements.getDouble(i);
            }
        }
        releaseElements(elements, mode);
    }

    @VM_ENTRY_POINT
    private static void GetBooleanArrayRegion(Pointer env, JniHandle array, int start, int length, Pointer buffer) {
        // Source: JniFunctionsSource.java:1450
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetBooleanArrayRegion.ordinal(), ENTRY, anchor, env, array, Address.fromInt(start), Address.fromInt(length), buffer);
        }

        try {
            final boolean[] a = (boolean[]) array.unhand();
            for (int i = 0; i < length; i++) {
                buffer.setBoolean(i, a[start + i]);
            }
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetBooleanArrayRegion.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void GetByteArrayRegion(Pointer env, JniHandle array, int start, int length, Pointer buffer) {
        // Source: JniFunctionsSource.java:1458
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetByteArrayRegion.ordinal(), ENTRY, anchor, env, array, Address.fromInt(start), Address.fromInt(length), buffer);
        }

        try {
            final byte[] a = (byte[]) array.unhand();
            for (int i = 0; i < length; i++) {
                buffer.setByte(i, a[start + i]);
            }
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetByteArrayRegion.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void GetCharArrayRegion(Pointer env, JniHandle array, int start, int length, Pointer buffer) {
        // Source: JniFunctionsSource.java:1466
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetCharArrayRegion.ordinal(), ENTRY, anchor, env, array, Address.fromInt(start), Address.fromInt(length), buffer);
        }

        try {
            final char[] a = (char[]) array.unhand();
            for (int i = 0; i < length; i++) {
                buffer.setChar(i, a[start + i]);
            }
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetCharArrayRegion.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void GetShortArrayRegion(Pointer env, JniHandle array, int start, int length, Pointer buffer) {
        // Source: JniFunctionsSource.java:1474
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetShortArrayRegion.ordinal(), ENTRY, anchor, env, array, Address.fromInt(start), Address.fromInt(length), buffer);
        }

        try {
            final short[] a = (short[]) array.unhand();
            for (int i = 0; i < length; i++) {
                buffer.setShort(i, a[start + i]);
            }
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetShortArrayRegion.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void GetIntArrayRegion(Pointer env, JniHandle array, int start, int length, Pointer buffer) {
        // Source: JniFunctionsSource.java:1482
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetIntArrayRegion.ordinal(), ENTRY, anchor, env, array, Address.fromInt(start), Address.fromInt(length), buffer);
        }

        try {
            final int[] a = (int[]) array.unhand();
            for (int i = 0; i < length; i++) {
                buffer.setInt(i, a[start + i]);
            }
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetIntArrayRegion.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void GetLongArrayRegion(Pointer env, JniHandle array, int start, int length, Pointer buffer) {
        // Source: JniFunctionsSource.java:1490
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetLongArrayRegion.ordinal(), ENTRY, anchor, env, array, Address.fromInt(start), Address.fromInt(length), buffer);
        }

        try {
            final long[] a = (long[]) array.unhand();
            for (int i = 0; i < length; i++) {
                buffer.setLong(i, a[start + i]);
            }
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetLongArrayRegion.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void GetFloatArrayRegion(Pointer env, JniHandle array, int start, int length, Pointer buffer) {
        // Source: JniFunctionsSource.java:1498
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetFloatArrayRegion.ordinal(), ENTRY, anchor, env, array, Address.fromInt(start), Address.fromInt(length), buffer);
        }

        try {
            final float[] a = (float[]) array.unhand();
            for (int i = 0; i < length; i++) {
                buffer.setFloat(i, a[start + i]);
            }
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetFloatArrayRegion.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void GetDoubleArrayRegion(Pointer env, JniHandle array, int start, int length, Pointer buffer) {
        // Source: JniFunctionsSource.java:1506
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetDoubleArrayRegion.ordinal(), ENTRY, anchor, env, array, Address.fromInt(start), Address.fromInt(length), buffer);
        }

        try {
            final double[] a = (double[]) array.unhand();
            for (int i = 0; i < length; i++) {
                buffer.setDouble(i, a[start + i]);
            }
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetDoubleArrayRegion.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void SetBooleanArrayRegion(Pointer env, JniHandle array, int start, int length, Pointer buffer) {
        // Source: JniFunctionsSource.java:1514
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.SetBooleanArrayRegion.ordinal(), ENTRY, anchor, env, array, Address.fromInt(start), Address.fromInt(length), buffer);
        }

        try {
            final boolean[] a = (boolean[]) array.unhand();
            for (int i = 0; i < length; i++) {
                a[start + i] = buffer.getBoolean(i);
            }
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.SetBooleanArrayRegion.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void SetByteArrayRegion(Pointer env, JniHandle array, int start, int length, Pointer buffer) {
        // Source: JniFunctionsSource.java:1522
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.SetByteArrayRegion.ordinal(), ENTRY, anchor, env, array, Address.fromInt(start), Address.fromInt(length), buffer);
        }

        try {
            final byte[] a = (byte[]) array.unhand();
            for (int i = 0; i < length; i++) {
                a[start + i] = buffer.getByte(i);
            }
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.SetByteArrayRegion.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void SetCharArrayRegion(Pointer env, JniHandle array, int start, int length, Pointer buffer) {
        // Source: JniFunctionsSource.java:1530
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.SetCharArrayRegion.ordinal(), ENTRY, anchor, env, array, Address.fromInt(start), Address.fromInt(length), buffer);
        }

        try {
            final char[] a = (char[]) array.unhand();
            for (int i = 0; i < length; i++) {
                a[start + i] = buffer.getChar(i);
            }
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.SetCharArrayRegion.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void SetShortArrayRegion(Pointer env, JniHandle array, int start, int length, Pointer buffer) {
        // Source: JniFunctionsSource.java:1538
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.SetShortArrayRegion.ordinal(), ENTRY, anchor, env, array, Address.fromInt(start), Address.fromInt(length), buffer);
        }

        try {
            final short[] a = (short[]) array.unhand();
            for (int i = 0; i < length; i++) {
                a[start + i] = buffer.getShort(i);
            }
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.SetShortArrayRegion.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void SetIntArrayRegion(Pointer env, JniHandle array, int start, int length, Pointer buffer) {
        // Source: JniFunctionsSource.java:1546
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.SetIntArrayRegion.ordinal(), ENTRY, anchor, env, array, Address.fromInt(start), Address.fromInt(length), buffer);
        }

        try {
            final int[] a = (int[]) array.unhand();
            for (int i = 0; i < length; i++) {
                a[start + i] = buffer.getInt(i);
            }
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.SetIntArrayRegion.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void SetLongArrayRegion(Pointer env, JniHandle array, int start, int length, Pointer buffer) {
        // Source: JniFunctionsSource.java:1554
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.SetLongArrayRegion.ordinal(), ENTRY, anchor, env, array, Address.fromInt(start), Address.fromInt(length), buffer);
        }

        try {
            final long[] a = (long[]) array.unhand();
            for (int i = 0; i < length; i++) {
                a[start + i] = buffer.getLong(i);
            }
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.SetLongArrayRegion.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void SetFloatArrayRegion(Pointer env, JniHandle array, int start, int length, Pointer buffer) {
        // Source: JniFunctionsSource.java:1562
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.SetFloatArrayRegion.ordinal(), ENTRY, anchor, env, array, Address.fromInt(start), Address.fromInt(length), buffer);
        }

        try {
            final float[] a = (float[]) array.unhand();
            for (int i = 0; i < length; i++) {
                a[start + i] = buffer.getFloat(i);
            }
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.SetFloatArrayRegion.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void SetDoubleArrayRegion(Pointer env, JniHandle array, int start, int length, Pointer buffer) {
        // Source: JniFunctionsSource.java:1570
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.SetDoubleArrayRegion.ordinal(), ENTRY, anchor, env, array, Address.fromInt(start), Address.fromInt(length), buffer);
        }

        try {
            final double[] a = (double[]) array.unhand();
            for (int i = 0; i < length; i++) {
                a[start + i] = buffer.getDouble(i);
            }
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.SetDoubleArrayRegion.ordinal(), EXIT);
            }

        }
    }

    /**
     * Registers a set of native methods.
     *
     * For reference, this code expects the type of the {@code methods} parameter to be {@code JNINativeMethod *methods}
     * where:
     *
     * typedef struct { char *name; char *signature; void *fnPtr; } JNINativeMethod;
     */
    @VM_ENTRY_POINT
    private static int RegisterNatives(Pointer env, JniHandle javaType, Pointer methods, int numberOfMethods) {
        // Source: JniFunctionsSource.java:1586
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.RegisterNatives.ordinal(), ENTRY, anchor, env, javaType, methods, Address.fromInt(numberOfMethods));
        }

        try {
            Pointer a = methods;

            final int pointerSize = Word.size();
            final int NAME = 0 * pointerSize;
            final int SIGNATURE = 1 * pointerSize;
            final int FNPTR = 2 * pointerSize;

            for (int i = 0; i < numberOfMethods; i++) {
                try {
                    final Utf8Constant name = SymbolTable.lookupSymbol(CString.utf8ToJava(a.readWord(NAME).asPointer()));
                    final SignatureDescriptor descriptor = SignatureDescriptor.lookup(CString.utf8ToJava(a.readWord(SIGNATURE).asPointer()));
                    if (name == null || descriptor == null) {
                        // The class should have been loaded (we have an instance of the class
                        // passed in) so the name and signature should already be in their respective canonicalization
                        // tables. If they're not there, the method doesn't exist.
                        throw new NoSuchMethodError();
                    }
                    final Address fnPtr = a.readWord(FNPTR).asAddress();

                    final ClassActor classActor = ClassActor.fromJava((Class) javaType.unhand());
                    final ClassMethodActor classMethodActor = classActor.findClassMethodActor(name, descriptor);
                    if (classMethodActor == null || !classMethodActor.isNative()) {
                        throw new NoSuchMethodError();
                    }
                    classMethodActor.nativeFunction.setAddress(fnPtr);

                } catch (Utf8Exception e) {
                    throw new NoSuchMethodError();
                }

                // advance to next JNINativeMethod struct
                a = a.plus(pointerSize * 3);
            }
            return 0;
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.RegisterNatives.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static int UnregisterNatives(Pointer env, JniHandle javaType) {
        // Source: JniFunctionsSource.java:1624
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.UnregisterNatives.ordinal(), ENTRY, anchor, env, javaType);
        }

        try {
            ClassActor classActor = ClassActor.fromJava((Class) javaType.unhand());
            for (VirtualMethodActor method : classActor.allVirtualMethodActors()) {
                method.nativeFunction.setAddress(Address.zero());
            }
            do {
                for (StaticMethodActor method : classActor.localStaticMethodActors()) {
                    method.nativeFunction.setAddress(Address.zero());
                }
                classActor = classActor.superClassActor;
            } while (classActor != null);
            return 0;
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.UnregisterNatives.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static int MonitorEnter(Pointer env, JniHandle object) {
        // Source: JniFunctionsSource.java:1639
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.MonitorEnter.ordinal(), ENTRY, anchor, env, object);
        }

        try {
            Monitor.enter(object.unhand());
            return 0;
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.MonitorEnter.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static int MonitorExit(Pointer env, JniHandle object) {
        // Source: JniFunctionsSource.java:1645
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.MonitorExit.ordinal(), ENTRY, anchor, env, object);
        }

        try {
            Monitor.exit(object.unhand());
            return 0;
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.MonitorExit.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static native int GetJavaVM(Pointer env, Pointer vmPointerPointer);
        // Source: JniFunctionsSource.java:1651

    @VM_ENTRY_POINT
    private static void GetStringRegion(Pointer env, JniHandle string, int start, int length, Pointer buffer) {
        // Source: JniFunctionsSource.java:1654
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetStringRegion.ordinal(), ENTRY, anchor, env, string, Address.fromInt(start), Address.fromInt(length), buffer);
        }

        try {
            final String s = (String) string.unhand();
            for (int i = 0; i < length; i++) {
                buffer.setChar(i, s.charAt(i + start));
            }
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetStringRegion.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void GetStringUTFRegion(Pointer env, JniHandle string, int start, int length, Pointer buffer) {
        // Source: JniFunctionsSource.java:1662
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetStringUTFRegion.ordinal(), ENTRY, anchor, env, string, Address.fromInt(start), Address.fromInt(length), buffer);
        }

        try {
            final String s = ((String) string.unhand()).substring(start, start + length);
            final byte[] utf = Utf8.stringToUtf8(s);
            Memory.writeBytes(utf, utf.length, buffer);
            buffer.setByte(utf.length, (byte) 0); // zero termination
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetStringUTFRegion.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static Pointer GetPrimitiveArrayCritical(Pointer env, JniHandle array, Pointer isCopy) {
        // Source: JniFunctionsSource.java:1670
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetPrimitiveArrayCritical.ordinal(), ENTRY, anchor, env, array, isCopy);
        }

        try {
            final Object arrayObject = array.unhand();
            if (Heap.useDirectPointer(arrayObject)) {
                setCopyPointer(isCopy, false);
                return Reference.fromJava(arrayObject).toOrigin().plus(Layout.byteArrayLayout().getElementOffsetFromOrigin(0));
            }

            if (arrayObject instanceof boolean[]) {
                return getBooleanArrayElements(array, isCopy);
            } else if (arrayObject instanceof byte[]) {
                return getByteArrayElements(array, isCopy);
            } else if (arrayObject instanceof char[]) {
                return getCharArrayElements(array, isCopy);
            } else if (arrayObject instanceof short[]) {
                return getShortArrayElements(array, isCopy);
            } else if (arrayObject instanceof int[]) {
                return getIntArrayElements(array, isCopy);
            } else if (arrayObject instanceof long[]) {
                return getLongArrayElements(array, isCopy);
            } else if (arrayObject instanceof float[]) {
                return getFloatArrayElements(array, isCopy);
            } else if (arrayObject instanceof double[]) {
                return getDoubleArrayElements(array, isCopy);
            }
            return Pointer.zero();
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asPointer(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetPrimitiveArrayCritical.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void ReleasePrimitiveArrayCritical(Pointer env, JniHandle array, Pointer elements, int mode) {
        // Source: JniFunctionsSource.java:1698
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.ReleasePrimitiveArrayCritical.ordinal(), ENTRY, anchor, env, array, elements, Address.fromInt(mode));
        }

        try {
            final Object arrayObject = array.unhand();
            if (Heap.releasedDirectPointer(arrayObject)) {
                return;
            }
            if (arrayObject instanceof boolean[]) {
                releaseBooleanArrayElements(array, elements, mode);
            } else if (arrayObject instanceof byte[]) {
                releaseByteArrayElements(array, elements, mode);
            } else if (arrayObject instanceof char[]) {
                releaseCharArrayElements(array, elements, mode);
            } else if (arrayObject instanceof short[]) {
                releaseShortArrayElements(array, elements, mode);
            } else if (arrayObject instanceof int[]) {
                releaseIntArrayElements(array, elements, mode);
            } else if (arrayObject instanceof long[]) {
                releaseLongArrayElements(array, elements, mode);
            } else if (arrayObject instanceof float[]) {
                releaseFloatArrayElements(array, elements, mode);
            } else if (arrayObject instanceof double[]) {
                releaseDoubleArrayElements(array, elements, mode);
            }
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.ReleasePrimitiveArrayCritical.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static Pointer GetStringCritical(Pointer env, JniHandle string, Pointer isCopy) {
        // Source: JniFunctionsSource.java:1723
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetStringCritical.ordinal(), ENTRY, anchor, env, string, isCopy);
        }

        try {
            // TODO(cwi): Implement optimized version for OptimizeJNICritical if a benchmark uses it frequently
            setCopyPointer(isCopy, true);
            final char[] a = ((String) string.unhand()).toCharArray();
            final Pointer pointer = Memory.mustAllocate(a.length * Kind.CHAR.width.numberOfBytes);
            for (int i = 0; i < a.length; i++) {
                pointer.setChar(i, a[i]);
            }
            return pointer;
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asPointer(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetStringCritical.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void ReleaseStringCritical(Pointer env, JniHandle string, final Pointer chars) {
        // Source: JniFunctionsSource.java:1735
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.ReleaseStringCritical.ordinal(), ENTRY, anchor, env, string, chars);
        }

        try {
            Memory.deallocate(chars);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.ReleaseStringCritical.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static JniHandle NewWeakGlobalRef(Pointer env, JniHandle handle) {
        // Source: JniFunctionsSource.java:1740
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.NewWeakGlobalRef.ordinal(), ENTRY, anchor, env, handle);
        }

        try {
            return JniHandles.createWeakGlobalHandle(handle.unhand());
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.NewWeakGlobalRef.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void DeleteWeakGlobalRef(Pointer env, JniHandle handle) {
        // Source: JniFunctionsSource.java:1745
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.DeleteWeakGlobalRef.ordinal(), ENTRY, anchor, env, handle);
        }

        try {
            JniHandles.destroyWeakGlobalHandle(handle);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.DeleteWeakGlobalRef.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static boolean ExceptionCheck(Pointer env) {
        // Source: JniFunctionsSource.java:1750
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.ExceptionCheck.ordinal(), ENTRY, anchor, env);
        }

        try {
            return VmThread.fromJniEnv(env).jniException() != null;
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return false;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.ExceptionCheck.ordinal(), EXIT);
            }

        }
    }

    private static final ClassActor DirectByteBuffer = ClassActor.fromJava(Classes.forName("java.nio.DirectByteBuffer"));

    @VM_ENTRY_POINT
    private static JniHandle NewDirectByteBuffer(Pointer env, Pointer address, long capacity) throws Exception {
        // Source: JniFunctionsSource.java:1757
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.NewDirectByteBuffer.ordinal(), ENTRY, anchor, env, address, Address.fromLong(capacity));
        }

        try {
            ByteBuffer buffer = ObjectAccess.createDirectByteBuffer(address.toLong(), (int) capacity);
            return JniHandles.createLocalHandle(buffer);
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asJniHandle(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.NewDirectByteBuffer.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static Pointer GetDirectBufferAddress(Pointer env, JniHandle buffer) throws Exception {
        // Source: JniFunctionsSource.java:1763
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetDirectBufferAddress.ordinal(), ENTRY, anchor, env, buffer);
        }

        try {
            Object buf = buffer.unhand();
            if (DirectByteBuffer.isInstance(buf)) {
                long address = ClassRegistry.Buffer_address.getLong(buf);
                return Pointer.fromLong(address);
            }
            return Pointer.zero();
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return asPointer(0);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetDirectBufferAddress.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static long GetDirectBufferCapacity(Pointer env, JniHandle buffer) {
        // Source: JniFunctionsSource.java:1773
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetDirectBufferCapacity.ordinal(), ENTRY, anchor, env, buffer);
        }

        try {
            Object buf = buffer.unhand();
            if (DirectByteBuffer.isInstance(buf)) {
                return ((Buffer) buf).capacity();
            }
            return -1;
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetDirectBufferCapacity.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static int GetObjectRefType(Pointer env, JniHandle obj) {
        // Source: JniFunctionsSource.java:1782
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetObjectRefType.ordinal(), ENTRY, anchor, env, obj);
        }

        try {
            final int tag = JniHandles.tag(obj);
            if (tag == JniHandles.Tag.STACK) {
                return JniHandles.Tag.LOCAL;
            }
            return tag;
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetObjectRefType.ordinal(), EXIT);
            }

        }
    }

    /*
     * Extended JNI native interface, see Native/jni/jni.c:
     */

    @VM_ENTRY_POINT
    private static int GetNumberOfArguments(Pointer env, MethodID methodID) throws Exception {
        // Source: JniFunctionsSource.java:1795
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetNumberOfArguments.ordinal(), ENTRY, anchor, env, methodID);
        }

        try {
            final MethodActor methodActor = MethodID.toMethodActor(methodID);
            if (methodActor == null) {
                throw new NoSuchMethodException();
            }
            return methodActor.descriptor().numberOfParameters();
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
            return JNI_ERR;
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetNumberOfArguments.ordinal(), EXIT);
            }

        }
    }

    @VM_ENTRY_POINT
    private static void GetKindsOfArguments(Pointer env, MethodID methodID, Pointer kinds) throws Exception {
        // Source: JniFunctionsSource.java:1804
        Pointer anchor = prologue(env);
        if (logger.enabled()) {
            logger.log(EntryPoints.GetKindsOfArguments.ordinal(), ENTRY, anchor, env, methodID, kinds);
        }

        try {
            final MethodActor methodActor = MethodID.toMethodActor(methodID);
            if (methodActor == null) {
                throw new NoSuchMethodException();
            }
            final SignatureDescriptor signature = methodActor.descriptor();
            for (int i = 0; i < signature.numberOfParameters(); ++i) {
                final Kind kind = signature.parameterDescriptorAt(i).toKind();
                kinds.setByte(i, (byte) kind.asEnum.ordinal());
            }
        } catch (Throwable t) {
            VmThread.fromJniEnv(env).setJniException(t);
        } finally {
            epilogue(anchor);
            if (logger.enabled()) {
                logger.log(EntryPoints.GetKindsOfArguments.ordinal(), EXIT);
            }

        }
    }

    // Checkstyle: resume method name check

    private static void setCopyPointer(Pointer isCopy, boolean bool) {
        if (!isCopy.isZero()) {
            isCopy.setBoolean(bool);
        }
    }

    private static void releaseElements(Pointer elements, int mode) {
        if (mode == 0 || mode == JNI_ABORT) {
            Memory.deallocate(elements);
        }
        assert mode == 0 || mode == JNI_COMMIT || mode == JNI_ABORT;
    }

    public static enum EntryPoints {
        /* 0 */ DefineClass,
        /* 1 */ FindClass,
        /* 2 */ FromReflectedMethod,
        /* 3 */ FromReflectedField,
        /* 4 */ ToReflectedMethod,
        /* 5 */ GetSuperclass,
        /* 6 */ IsAssignableFrom,
        /* 7 */ ToReflectedField,
        /* 8 */ Throw,
        /* 9 */ ThrowNew,
        /* 10 */ ExceptionOccurred,
        /* 11 */ ExceptionDescribe,
        /* 12 */ ExceptionClear,
        /* 13 */ FatalError,
        /* 14 */ PushLocalFrame,
        /* 15 */ PopLocalFrame,
        /* 16 */ NewGlobalRef,
        /* 17 */ DeleteGlobalRef,
        /* 18 */ DeleteLocalRef,
        /* 19 */ IsSameObject,
        /* 20 */ NewLocalRef,
        /* 21 */ EnsureLocalCapacity,
        /* 22 */ AllocObject,
        /* 23 */ NewObjectA,
        /* 24 */ GetObjectClass,
        /* 25 */ IsInstanceOf,
        /* 26 */ GetMethodID,
        /* 27 */ CallObjectMethodA,
        /* 28 */ CallBooleanMethodA,
        /* 29 */ CallByteMethodA,
        /* 30 */ CallCharMethodA,
        /* 31 */ CallShortMethodA,
        /* 32 */ CallIntMethodA,
        /* 33 */ CallLongMethodA,
        /* 34 */ CallFloatMethodA,
        /* 35 */ CallDoubleMethodA,
        /* 36 */ CallVoidMethodA,
        /* 37 */ CallNonvirtualObjectMethodA,
        /* 38 */ CallNonvirtualBooleanMethodA,
        /* 39 */ CallNonvirtualByteMethodA,
        /* 40 */ CallNonvirtualCharMethodA,
        /* 41 */ CallNonvirtualShortMethodA,
        /* 42 */ CallNonvirtualIntMethodA,
        /* 43 */ CallNonvirtualLongMethodA,
        /* 44 */ CallNonvirtualFloatMethodA,
        /* 45 */ CallNonvirtualDoubleMethodA,
        /* 46 */ CallNonvirtualVoidMethodA,
        /* 47 */ GetFieldID,
        /* 48 */ GetObjectField,
        /* 49 */ GetBooleanField,
        /* 50 */ GetByteField,
        /* 51 */ GetCharField,
        /* 52 */ GetShortField,
        /* 53 */ GetIntField,
        /* 54 */ GetLongField,
        /* 55 */ GetFloatField,
        /* 56 */ GetDoubleField,
        /* 57 */ SetObjectField,
        /* 58 */ SetBooleanField,
        /* 59 */ SetByteField,
        /* 60 */ SetCharField,
        /* 61 */ SetShortField,
        /* 62 */ SetIntField,
        /* 63 */ SetLongField,
        /* 64 */ SetFloatField,
        /* 65 */ SetDoubleField,
        /* 66 */ GetStaticMethodID,
        /* 67 */ CallStaticObjectMethodA,
        /* 68 */ CallStaticBooleanMethodA,
        /* 69 */ CallStaticByteMethodA,
        /* 70 */ CallStaticCharMethodA,
        /* 71 */ CallStaticShortMethodA,
        /* 72 */ CallStaticIntMethodA,
        /* 73 */ CallStaticLongMethodA,
        /* 74 */ CallStaticFloatMethodA,
        /* 75 */ CallStaticDoubleMethodA,
        /* 76 */ CallStaticVoidMethodA,
        /* 77 */ GetStaticFieldID,
        /* 78 */ GetStaticObjectField,
        /* 79 */ GetStaticBooleanField,
        /* 80 */ GetStaticByteField,
        /* 81 */ GetStaticCharField,
        /* 82 */ GetStaticShortField,
        /* 83 */ GetStaticIntField,
        /* 84 */ GetStaticLongField,
        /* 85 */ GetStaticFloatField,
        /* 86 */ GetStaticDoubleField,
        /* 87 */ SetStaticObjectField,
        /* 88 */ SetStaticBooleanField,
        /* 89 */ SetStaticByteField,
        /* 90 */ SetStaticCharField,
        /* 91 */ SetStaticShortField,
        /* 92 */ SetStaticIntField,
        /* 93 */ SetStaticLongField,
        /* 94 */ SetStaticFloatField,
        /* 95 */ SetStaticDoubleField,
        /* 96 */ NewString,
        /* 97 */ GetStringLength,
        /* 98 */ GetStringChars,
        /* 99 */ ReleaseStringChars,
        /* 100 */ NewStringUTF,
        /* 101 */ GetStringUTFLength,
        /* 102 */ GetStringUTFChars,
        /* 103 */ ReleaseStringUTFChars,
        /* 104 */ GetArrayLength,
        /* 105 */ NewObjectArray,
        /* 106 */ GetObjectArrayElement,
        /* 107 */ SetObjectArrayElement,
        /* 108 */ NewBooleanArray,
        /* 109 */ NewByteArray,
        /* 110 */ NewCharArray,
        /* 111 */ NewShortArray,
        /* 112 */ NewIntArray,
        /* 113 */ NewLongArray,
        /* 114 */ NewFloatArray,
        /* 115 */ NewDoubleArray,
        /* 116 */ GetBooleanArrayElements,
        /* 117 */ GetByteArrayElements,
        /* 118 */ GetCharArrayElements,
        /* 119 */ GetShortArrayElements,
        /* 120 */ GetIntArrayElements,
        /* 121 */ GetLongArrayElements,
        /* 122 */ GetFloatArrayElements,
        /* 123 */ GetDoubleArrayElements,
        /* 124 */ ReleaseBooleanArrayElements,
        /* 125 */ ReleaseByteArrayElements,
        /* 126 */ ReleaseCharArrayElements,
        /* 127 */ ReleaseShortArrayElements,
        /* 128 */ ReleaseIntArrayElements,
        /* 129 */ ReleaseLongArrayElements,
        /* 130 */ ReleaseFloatArrayElements,
        /* 131 */ ReleaseDoubleArrayElements,
        /* 132 */ GetBooleanArrayRegion,
        /* 133 */ GetByteArrayRegion,
        /* 134 */ GetCharArrayRegion,
        /* 135 */ GetShortArrayRegion,
        /* 136 */ GetIntArrayRegion,
        /* 137 */ GetLongArrayRegion,
        /* 138 */ GetFloatArrayRegion,
        /* 139 */ GetDoubleArrayRegion,
        /* 140 */ SetBooleanArrayRegion,
        /* 141 */ SetByteArrayRegion,
        /* 142 */ SetCharArrayRegion,
        /* 143 */ SetShortArrayRegion,
        /* 144 */ SetIntArrayRegion,
        /* 145 */ SetLongArrayRegion,
        /* 146 */ SetFloatArrayRegion,
        /* 147 */ SetDoubleArrayRegion,
        /* 148 */ RegisterNatives,
        /* 149 */ UnregisterNatives,
        /* 150 */ MonitorEnter,
        /* 151 */ MonitorExit,
        /* 152 */ GetStringRegion,
        /* 153 */ GetStringUTFRegion,
        /* 154 */ GetPrimitiveArrayCritical,
        /* 155 */ ReleasePrimitiveArrayCritical,
        /* 156 */ GetStringCritical,
        /* 157 */ ReleaseStringCritical,
        /* 158 */ NewWeakGlobalRef,
        /* 159 */ DeleteWeakGlobalRef,
        /* 160 */ ExceptionCheck,
        /* 161 */ NewDirectByteBuffer,
        /* 162 */ GetDirectBufferAddress,
        /* 163 */ GetDirectBufferCapacity,
        /* 164 */ GetObjectRefType,
        /* 165 */ GetNumberOfArguments,
        /* 166 */ GetKindsOfArguments;

    }
// END GENERATED CODE
}
