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
package com.sun.max.vm.type;

import static com.sun.max.vm.actor.member.InjectedReferenceFieldActor.*;
import static com.sun.max.vm.jdk.JDK.*;
import static com.sun.max.vm.prototype.HostedBootClassLoader.*;

import java.io.*;
import java.lang.reflect.*;
import java.security.*;
import java.util.*;

import sun.misc.*;

import com.sun.max.*;
import com.sun.max.annotate.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.classfile.*;
import com.sun.max.vm.classfile.constant.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.object.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.reflection.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.thread.*;
import com.sun.max.vm.value.*;

/**
 * Each class loader is associated with a class registry and vice versa.
 * The class registry augments the class loader with a mapping from
 * {@linkplain TypeDescriptor type descriptors} to {@linkplain ClassActor class actors}.
 *
 * The {@linkplain BootClassLoader#BOOT_CLASS_LOADER boot class loader} is associated the
 * {@linkplain #BOOT_CLASS_REGISTRY boot class registry}.
 *
 * This class also contains a number static variables for the actors of well known classes,
 * methods and fields.
 *
 * @author Bernd Mathiske
 * @author Doug Simon
 */
public final class ClassRegistry {

    /**
     * The class registry associated with the boot class loader.
     */
    public static final ClassRegistry BOOT_CLASS_REGISTRY = new ClassRegistry(HOSTED_BOOT_CLASS_LOADER);

    public static final TupleClassActor OBJECT = createClass(Object.class);
    public static final TupleClassActor CLASS = createClass(Class.class);
    public static final TupleClassActor THROWABLE = createClass(Throwable.class);
    public static final TupleClassActor THREAD = createClass(Thread.class);
    public static final InterfaceActor CLONEABLE = createClass(Cloneable.class);
    public static final InterfaceActor SERIALIZABLE = createClass(Serializable.class);

    public static final PrimitiveClassActor VOID = createPrimitiveClass(Kind.VOID);
    public static final PrimitiveClassActor BYTE = createPrimitiveClass(Kind.BYTE);
    public static final PrimitiveClassActor BOOLEAN = createPrimitiveClass(Kind.BOOLEAN);
    public static final PrimitiveClassActor SHORT = createPrimitiveClass(Kind.SHORT);
    public static final PrimitiveClassActor CHAR = createPrimitiveClass(Kind.CHAR);
    public static final PrimitiveClassActor INT = createPrimitiveClass(Kind.INT);
    public static final PrimitiveClassActor FLOAT = createPrimitiveClass(Kind.FLOAT);
    public static final PrimitiveClassActor LONG = createPrimitiveClass(Kind.LONG);
    public static final PrimitiveClassActor DOUBLE = createPrimitiveClass(Kind.DOUBLE);

    public static final ArrayClassActor<ByteValue> BYTE_ARRAY = createPrimitiveArrayClass(BYTE);
    public static final ArrayClassActor<BooleanValue> BOOLEAN_ARRAY = createPrimitiveArrayClass(BOOLEAN);
    public static final ArrayClassActor<ShortValue> SHORT_ARRAY = createPrimitiveArrayClass(SHORT);
    public static final ArrayClassActor<CharValue> CHAR_ARRAY = createPrimitiveArrayClass(CHAR);
    public static final ArrayClassActor<IntValue> INT_ARRAY = createPrimitiveArrayClass(INT);
    public static final ArrayClassActor<FloatValue> FLOAT_ARRAY = createPrimitiveArrayClass(FLOAT);
    public static final ArrayClassActor<LongValue> LONG_ARRAY = createPrimitiveArrayClass(LONG);
    public static final ArrayClassActor<DoubleValue> DOUBLE_ARRAY = createPrimitiveArrayClass(DOUBLE);

    public static final FieldActor ClassActor_javaClass = findField(ClassActor.class, "javaClass");
    public static final FieldActor System_in = findField(System.class, "in");
    public static final FieldActor System_out = findField(System.class, "out");
    public static final FieldActor System_err = findField(System.class, "err");
    public static final FieldActor Thread_priority = findField(Thread.class, "priority");
    public static final FieldActor Thread_name = findField(Thread.class, "name");
    public static final FieldActor NativeLibrary_handle = findField(java_lang_ClassLoader$NativeLibrary, "handle");
    public static final FieldActor Thread_inheritedAccessControlContext = findField(Thread.class, "inheritedAccessControlContext");
    public static final FieldActor ClassLoader_loadedLibraryNames = findField(ClassLoader.class, "loadedLibraryNames");
    public static final FieldActor ClassLoader_nativeLibraries = findField(ClassLoader.class, "nativeLibraries");
    public static final FieldActor AccessControlContext_privilegedContext = findField(AccessControlContext.class, "privilegedContext");
    public static final FieldActor Throwable_stackTrace = findField(Throwable.class, "stackTrace");

    public static final MethodActor ThreadGroup_add_Thread = findMethod(ThreadGroup.class, "add", Thread.class);
    public static final MethodActor Thread_init = findMethod("init", Thread.class);
    public static final MethodActor Thread_nextThreadNum = findMethod("nextThreadNum", Thread.class);
    public static final MethodActor ClassLoader_findNative = findMethod(ClassLoader.class, "findNative", ClassLoader.class, String.class);
    public static final MethodActor NativeLibrary_init = findMethod(java_lang_ClassLoader$NativeLibrary, "<init>", Class.class, String.class);
    public static final MethodActor Finalizer_register_Object = findMethod(java_lang_ref_Finalizer, "register", Object.class);
    public static final MethodActor ReferenceHandler_init = findMethod(java_lang_ref_Reference$ReferenceHandler, "<init>", ThreadGroup.class, String.class);
    public static final MethodActor Method_invoke = findMethod(Method.class, "invoke", Object.class, Object[].class);
    public static final MethodActor MaxineVM_run = findMethod("run", MaxineVM.class);
    public static final MethodActor VmThread_add = findMethod("add", VmThread.class);
    public static final MethodActor VmThread_run = findMethod("run", VmThread.class);
    public static final MethodActor VmThread_attach = findMethod("attach", VmThread.class);
    public static final MethodActor VmThread_detach = findMethod("detach", VmThread.class);
    public static final MethodActor AccessControlContext_init = findMethod(AccessControlContext.class, "<init>", ProtectionDomain[].class, boolean.class);
    public static final MethodActor DirectByteBuffer_init = findMethod(Classes.forName("java.nio.DirectByteBuffer"), "<init>", long.class, int.class);
    public static final MethodActor Signal_dispatch = findMethod(Signal.class, "dispatch", int.class);

    private static int loadCount;        // total loaded
    private static int unloadCount;    // total unloaded

    static {
        new CriticalNativeMethod(Log.class, "log_lock");
        new CriticalNativeMethod(Log.class, "log_unlock");
        new CriticalNativeMethod(Log.class, "log_flush");

        new CriticalNativeMethod(Log.class, "log_print_buffer");
        new CriticalNativeMethod(Log.class, "log_print_boolean");
        new CriticalNativeMethod(Log.class, "log_print_char");
        new CriticalNativeMethod(Log.class, "log_print_int");
        new CriticalNativeMethod(Log.class, "log_print_long");
        new CriticalNativeMethod(Log.class, "log_print_float");
        new CriticalNativeMethod(Log.class, "log_print_double");
        new CriticalNativeMethod(Log.class, "log_print_word");
        new CriticalNativeMethod(Log.class, "log_print_newline");
        new CriticalNativeMethod(Log.class, "log_print_symbol");

        new CriticalNativeMethod(MaxineVM.class, "native_exit");
        new CriticalNativeMethod(MaxineVM.class, "native_trap_exit");

        new CriticalNativeMethod(VmThread.class, "nonJniNativeSleep");
        new CriticalNativeMethod(VmThread.class, "nativeSleep");
        new CriticalNativeMethod(VmThread.class, "nativeYield");
    }

    /**
     * Support for ClassFileWriter.testLoadGeneratedClasses().
     */
    @HOSTED_ONLY
    public static ClassLoader testClassLoader;
    @HOSTED_ONLY
    private static ClassRegistry testClassRegistry;

    @INSPECTED
    private final HashMap<TypeDescriptor, ClassActor> typeDescriptorToClassActor = new HashMap<TypeDescriptor, ClassActor>(16384);
    private final HashMap<Object, Object>[] propertyMaps;

    public final ClassLoader classLoader;

    private ClassRegistry(ClassLoader classLoader) {
        propertyMaps = Utils.cast(new HashMap[Property.VALUES.size()]);
        for (Property property : Property.VALUES) {
            propertyMaps[property.ordinal()] = property.createMap();
        }
        this.classLoader = classLoader;
    }

    /**
     * Creates a ClassActor for a primitive type.
     */
    @HOSTED_ONLY
    private static <Value_Type extends Value<Value_Type>> PrimitiveClassActor createPrimitiveClass(Kind<Value_Type> kind) {
        return put(new PrimitiveClassActor(kind));
    }

    /**
     * Creates an ArrayClassActor for a primitive array type.
     */
    @HOSTED_ONLY
    private static <Value_Type extends Value<Value_Type>> ArrayClassActor<Value_Type> createPrimitiveArrayClass(PrimitiveClassActor componentClassActor) {
        return put(new ArrayClassActor<Value_Type>(componentClassActor));
    }

    /**
     * Creates a ClassActor for a tuple or interface type.
     */
    @HOSTED_ONLY
    private static <ClassActor_Type extends ClassActor> ClassActor_Type createClass(Class javaClass) {
        TypeDescriptor typeDescriptor = JavaTypeDescriptor.forJavaClass(javaClass);
        ClassActor classActor = BOOT_CLASS_REGISTRY.get(typeDescriptor);
        if (classActor == null) {
            final String name = typeDescriptor.toJavaString();
            Classpath classpath = HOSTED_BOOT_CLASS_LOADER.classpath();
            final ClasspathFile classpathFile = classpath.readClassFile(name);
            classActor = ClassfileReader.defineClassActor(name, HOSTED_BOOT_CLASS_LOADER, classpathFile.contents, null, classpathFile.classpathEntry, false);
        }
        Class<ClassActor_Type> type = null;
        return Utils.cast(type, classActor);
    }

    @HOSTED_ONLY
    public static Class asClass(Object classObject) {
        if (classObject instanceof Class) {
            return (Class) classObject;
        }
        assert classObject instanceof ClassRef;
        return ((ClassRef) classObject).javaClass();
    }

    /**
     * Finds the field actor denoted by a given name and declaring class.
     *
     * @param name the name of the field which must be unique in the declaring class
     * @param declaringClass the class to search for a field named {@code name}
     * @return the actor for the unique field in {@code declaringClass} named {@code name}
     */
    @HOSTED_ONLY
    public static FieldActor findField(Object declaringClassObject, String name) {
        Class declaringClass = asClass(declaringClassObject);
        ClassActor classActor = ClassActor.fromJava(declaringClass);
        FieldActor fieldActor = classActor.findLocalInstanceFieldActor(name);
        if (fieldActor == null) {
            fieldActor = classActor.findLocalStaticFieldActor(name);
        }
        assert fieldActor != null;
        return fieldActor;
    }

    /**
     * Finds the method actor denoted by a given name and declaring class.
     *
     * @param name the name of the method which must be unique in the declaring class
     * @param declaringClass the class to search for a method named {@code name}
     * @return the actor for the unique method in {@code declaringClass} named {@code name}
     */
    @HOSTED_ONLY
    public static MethodActor findMethod(String name, Object declaringClassObject) {
        Class declaringClass = asClass(declaringClassObject);
        Method theMethod = null;
        for (Method method : declaringClass.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                ProgramError.check(theMethod == null, "There must only be one method named \"" + name + "\" in " + declaringClass);
                theMethod = method;
            }
        }
        if (theMethod == null) {
            if (declaringClass.getSuperclass() != null) {
                return findMethod(name, declaringClass.getSuperclass());
            }
        }
        ProgramError.check(theMethod != null, "Could not find method named \"" + name + "\" in " + declaringClass);
        return findMethod(declaringClass, name, theMethod.getParameterTypes());
    }

    /**
     * Finds the method actor denoted by a given name and declaring class.
     * A side effect of this is that the method is compiled into the image.
     * If the method is not a VM entry point (and hence not subject to reflective
     * invocation) its invocation stub is also compiled into the image.
     *
     * @param declaringClass the class to search for a method named {@code name}
     * @param name the name of the method to find
     * @param parameterTypes the types in the signature of the method
     * @return the actor for the unique method in {@code declaringClass} named {@code name} with the signature composed
     *         of {@code parameterTypes}
     */
    @HOSTED_ONLY
    public static MethodActor findMethod(Object declaringClassObject, String name, Class... parameterTypes) {
        Class declaringClass = asClass(declaringClassObject);
        MethodActor methodActor;
        if (name.equals("<init>")) {
            methodActor = MethodActor.fromJavaConstructor(Classes.getDeclaredConstructor(declaringClass, parameterTypes));
        } else {
            methodActor = MethodActor.fromJava(Classes.getDeclaredMethod(declaringClass, name, parameterTypes));
        }
        ProgramError.check(methodActor != null, "Could not find " + name + ".(" + Utils.toString(parameterTypes, ", ") + ") in " + declaringClass);
        if (methodActor instanceof ClassMethodActor) {
            // Some of these methods are called during VM startup
            // so they are compiled in the image.
            CallEntryPoint callEntryPoint = CallEntryPoint.OPTIMIZED_ENTRY_POINT;
            if (methodActor.isVmEntryPoint()) {
                callEntryPoint = CallEntryPoint.C_ENTRY_POINT;
            }
            new CriticalMethod((ClassMethodActor) methodActor, callEntryPoint);
        }
        if (!methodActor.isCFunction() && !methodActor.isVmEntryPoint()) {
            // Some of these methods are called via reflection during VM startup
            // so their reflection stubs are prebuilt into the image.
            MaxineVM.registerImageInvocationStub(methodActor);
        }
        return methodActor;
    }

    /**
     * Gets the registry for a given class loader, creating it first if necessary.
     *
     * @param classLoader the class loader for which the associated registry is requested
     * @return the class registry associated with {@code classLoader}
     */
    public static ClassRegistry makeRegistry(ClassLoader classLoader) {
        if (MaxineVM.isHosted()) {
            if (classLoader != null && classLoader == testClassLoader) {
                if (testClassRegistry == null) {
                    testClassRegistry = new ClassRegistry(classLoader);
                }
                return testClassRegistry;
            }
            return BOOT_CLASS_REGISTRY;
        }
        if (classLoader == null) {
            return BOOT_CLASS_REGISTRY;
        }

        final ClassRegistry result = (ClassRegistry) TupleAccess.readObject(classLoader, ClassLoader_classRegistry.offset());
        if (result != null) {
            return result;
        }

        // Non-blocking synchronization is used here to swap in a new ClassRegistry reference.
        // This could lead to some extra ClassRegistry objects being created that become garbage, but should be harmless.
        final ClassRegistry newRegistry = new ClassRegistry(classLoader);
        final Reference oldRegistry = Reference.fromJava(classLoader).compareAndSwapReference(ClassLoader_classRegistry.offset(), null,  Reference.fromJava(newRegistry));
        if (oldRegistry == null) {
            return newRegistry;
        }
        return UnsafeCast.asClassRegistry(oldRegistry.toJava());
    }

    public int numberOfClassActors() {
        return typeDescriptorToClassActor.size();
    }

    @HOSTED_ONLY
    private ClassActor[] classesCache;

    /**
     * Gets a snapshot of the classes currently in this registry. This method synchronizes
     * on {@link #classLoader} associated with this registry to ensure it is not taking
     * the snapshot while another thread is {@linkplain #put(ClassActor) adding} entries.
     */
    @HOSTED_ONLY
    public ClassActor[] copyOfClasses() {
        synchronized (classLoader) {
            int n = numberOfClassActors();
            if (classesCache != null && classesCache.length == n) {
                return classesCache;
            }
            classesCache = typeDescriptorToClassActor.values().toArray(new ClassActor[typeDescriptorToClassActor.size()]);
            assert classesCache.length == n;
            return classesCache;
        }
    }

    /**
     * Adds a class to this registry.
     *
     * The caller must only add classes that are known to not already be in this registry.
     * The only way to guarantee this is have this call be in a block that synchronizes on
     * {@link #classLoader} which first {@linkplain #get(TypeDescriptor) tests} whether
     * {@code classActor} is already in this registry.
     *
     * @param classActor the class actor to add. This class actor must not already be in this registry.
     */
    private void put0(ClassActor classActor) {
        final TypeDescriptor typeDescriptor = classActor.typeDescriptor;
        final ClassActor existingClassActor = typeDescriptorToClassActor.put(typeDescriptor, classActor);

        if (existingClassActor != null) {
            throw new NoClassDefFoundError("Cannot redefine " + classActor.name);
        }

        loadCount++;
    }

    /**
     * Adds a class to the registry associated with its class loader.
     *
     * The caller must only add classes that are known to not already be in the registry.
     * The only way to guarantee this is have this call be in a block that synchronizes on
     * {@link #classLoader} which first {@linkplain #get(TypeDescriptor) tests} whether
     * {@code classActor} is already in the registry.
     *
     * Only completely initialized ClassActors must be registered with the ClassRegistry.
     * This prevents another thread from getting hold of a partially initialized ClassActor.
     * As such, registration must not be performed from within the ClassActor constructor
     * or any ClassActor subclass constructor. Instead, it should be done at the call site
     * of the 'new' that is allocating a ClassActor instance, typically by enclosing the 'new'
     * expression with a call to this method.
     *
     * @param classActor the class actor to add. This class actor must not already be in the registry.
     */
    public static <ClassActor_Type extends ClassActor> ClassActor_Type put(ClassActor_Type classActor) {
        makeRegistry(classActor.classLoader).put0(classActor);
        return classActor;
    }

    /**
     * Looks up a class actor in this registry.
     *
     * @param typeDescriptor the type descriptor of the class actor to look up
     * @return the class actor corresponding to {@code typeDescriptor} in this registry or {@code null} if there is no
     *         entry in this registry corresponding to {@code typeDescriptor}
     */
    public ClassActor get(TypeDescriptor typeDescriptor) {
        return typeDescriptorToClassActor.get(typeDescriptor);
    }

    /**
     * Searches for a given type in a registry associated with a given class loader.
     *
     * @param classLoader the class loader to start searching in
     * @param typeDescriptor the type to look for
     * @param searchParents specifies if the {@linkplain ClassLoader#getParent() parents} of {@code classLoader} should
     *            be searched if the type is not in the registry of {@code classLoader}
     * @return the resolved actor corresponding to {@code typeDescriptor} or {@code null} if not found
     */
    public static ClassActor get(ClassLoader classLoader, TypeDescriptor typeDescriptor, boolean searchParents) {
        ClassRegistry registry = makeRegistry(classLoader);
        ClassActor classActor = registry.get(typeDescriptor);
        if (classActor != null) {
            return classActor;
        }

        if (!searchParents || classLoader == null) {
            return null;
        }

        ClassLoader parent = classLoader.getParent();
        if (parent == null) {
            if (classLoader != BootClassLoader.BOOT_CLASS_LOADER) {
                // Every class loader should ultimately delegate to the boot class loader
                parent = BootClassLoader.BOOT_CLASS_LOADER;
            } else {
                return null;
            }
        }
        return get(parent, typeDescriptor, true);
    }

    /**
     * An enumeration of the properties that can be associated with actors. These are properties for which only a small
     * percentage of actors will have a non-default value. As such, using maps to store the property values results in
     * a space saving.
     * <a>
     * One trade off of using maps for properties (as opposed to fields) is that access is slower and must be synchronized.
     *
     * @author Doug Simon
     */
    public enum Property {
        GENERIC_SIGNATURE(Actor.class, Utf8Constant.class, Actor.NO_GENERIC_SIGNATURE),
        RUNTIME_VISIBLE_ANNOTATION_BYTES(Actor.class, byte[].class, Actor.NO_RUNTIME_VISIBLE_ANNOTATION_BYTES),
        ENCLOSING_METHOD_INFO(ClassActor.class, EnclosingMethodInfo.class, null),
        INNER_CLASSES(ClassActor.class, TypeDescriptor[].class, null),
        OUTER_CLASS(ClassActor.class, TypeDescriptor.class, null),
        CHECKED_EXCEPTIONS(MethodActor.class, TypeDescriptor[].class, MethodActor.NO_CHECKED_EXCEPTIONS),
        CONSTANT_VALUE(FieldActor.class, Value.class, null),
        ANNOTATION_DEFAULT_BYTES(MethodActor.class, byte[].class, MethodActor.NO_ANNOTATION_DEFAULT_BYTES),
        ACCESSOR(MethodActor.class, Class.class, null),
        INVOCATION_STUB(false, MethodActor.class, InvocationStub.class, null),
        RUNTIME_VISIBLE_PARAMETER_ANNOTATION_BYTES(MethodActor.class, byte[].class, MethodActor.NO_RUNTIME_VISIBLE_PARAMETER_ANNOTATION_BYTES);

        public static final List<Property> VALUES = java.util.Arrays.asList(values());

        private final Class keyType;
        private final Class valueType;
        private final Object defaultValue;
        private final boolean isFinal;

        /**
         * Defines a property.
         *
         * @param isFinal determines if the property can only be set once for a given object
         * @param keyType the type of objects to which the property applies
         * @param valueType the type of the property's values
         * @param defaultValue the default value of the property
         */
        private Property(boolean isFinal, Class keyType, Class valueType, Object defaultValue) {
            this.keyType = keyType;
            this.valueType = valueType;
            this.defaultValue = defaultValue;
            this.isFinal = isFinal;
        }

        /**
         * Defines a property that can only be set once.
         *
         * @param keyType the type of objects to which the property applies
         * @param valueType the type of the property's values
         * @param defaultValue the default value of the property
         */
        private Property(Class keyType, Class valueType, Object defaultValue) {
            this(true, keyType, valueType, defaultValue);
        }

        HashMap<Object, Object> createMap() {
            return new HashMap<Object, Object>();
        }

        /**
         * Sets the value of this property for a given key.
         *
         * @param mapping the mapping from keys to values for this property
         * @param object the object for which the value of this property is to be retrieved
         * @param value the value to be set
         */
        void set(HashMap<Object, Object> mapping, Object object, Object value) {
            assert keyType.isInstance(object);
            if (value != null) {
                assert valueType.isInstance(value);
                final Object oldValue = mapping.put(object, value);
                assert !isFinal || oldValue == null;
            } else {
                mapping.remove(object);
            }
        }

        /**
         * Gets the value of this property for a given key.
         *
         * @param mapping the mapping from keys to values for this property
         * @param object the object for which the value of this property is to be retrieved
         */
        Object get(HashMap<Object, Object> mapping, Object object) {
            assert keyType.isInstance(object);
            final Object value = mapping.get(object);
            if (value != null) {
                return value;
            }
            return defaultValue;
        }
    }

    /**
     * Sets the value of a given property for a given object.
     */
    public synchronized <Key_Type, Value_Type> void set(Property property, Key_Type object, Value_Type value) {
        property.set(propertyMaps[property.ordinal()], object, value);
    }

    /**
     * Gets the value of a given property for a given object.
     */
    public synchronized <Key_Type, Value_Type> Value_Type get(Property property, Key_Type object) {
        final Class<Value_Type> type = null;
        return Utils.cast(type, property.get(propertyMaps[property.ordinal()], object));
    }

    public void trace(int level) {
        if (!Trace.hasLevel(level)) {
            return;
        }
        final PrintStream out = Trace.stream();
        out.println("BEGIN ClassRegistry");
        for (ClassActor classActor : typeDescriptorToClassActor.values()) {
            out.println("    " + classActor.name);
        }
        out.println("END ClassRegistry");
    }

    public static synchronized int getLoadedClassCount() {
        return loadCount - unloadCount;
    }

    public static synchronized int getTotalLoadedClassCount() {
        return loadCount;
    }

    public static synchronized int getUnloadedClassCount() {
        return unloadCount;
    }
}
