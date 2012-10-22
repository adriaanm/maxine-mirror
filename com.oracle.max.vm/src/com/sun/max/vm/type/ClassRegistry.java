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
package com.sun.max.vm.type;

import static com.sun.max.vm.actor.member.InjectedReferenceFieldActor.*;
import static com.sun.max.vm.hosted.HostedBootClassLoader.*;
import static com.sun.max.vm.hosted.HostedVMClassLoader.*;
import static com.sun.max.vm.jdk.JDK.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;

import com.sun.max.*;
import com.sun.max.annotate.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.classfile.constant.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.compiler.deps.*;
import com.sun.max.vm.hosted.*;
import com.sun.max.vm.jdk.JDK.ClassRef;
import com.sun.max.vm.log.VMLog.Record;
import com.sun.max.vm.log.hosted.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.reflection.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.tele.*;
import com.sun.max.vm.thread.*;
import com.sun.max.vm.value.*;

/**
 * Each class loader is associated with a class registry and vice versa.
 * The class registry augments the class loader with a mapping from
 * {@linkplain TypeDescriptor type descriptors} to {@linkplain ClassActor class actors}.
 *
 * The {@linkplain BootClassLoader#BOOT_CLASS_LOADER boot class loader} is associated the
 * {@linkplain #BOOT_CLASS_REGISTRY boot class registry}.
 * The {@linkplain VMClassLoader#VM_CLASS_LOADER M class loader} is associated the
 * {@linkplain #VM_CLASS_REGISTRY VM class registry}
 *
 * This class also contains a number static variables for the actors of well known classes,
 * methods and fields.
 *
 * Note that this design (a separate dictionary of classes per class loader) differs from
 * the global system dictionary (implemented in systemDictionary.[hpp|cpp]) used by HotSpot.
 */
public final class ClassRegistry {

    /**
     * This has to be first to ensure that it is initialized before any classes are defined.
     */
    public static final ClassLoadingLogger logger = new ClassLoadingLogger();

    /**
     * The class registry associated with the boot class loader.
     */
    public static final ClassRegistry BOOT_CLASS_REGISTRY = new ClassRegistry(HOSTED_BOOT_CLASS_LOADER);
    /**
     * The class registry associated with the VM class loader.
     */
    public static final ClassRegistry VM_CLASS_REGISTRY = new ClassRegistry(HOSTED_VM_CLASS_LOADER);

    public static final TupleClassActor OBJECT = createClass(Object.class);
    public static final TupleClassActor CLASS = createClass(Class.class);
    public static final TupleClassActor THROWABLE = createClass(Throwable.class);
    public static final TupleClassActor THREAD = createClass(Thread.class);
    public static final TupleClassActor JLR_REFERENCE = createClass(java.lang.ref.Reference.class);
    public static final TupleClassActor JLR_FINAL_REFERENCE = createClass(Classes.forName("java.lang.ref.FinalReference"));
    public static final InterfaceActor CLONEABLE = createClass(Cloneable.class);
    public static final InterfaceActor SERIALIZABLE = createClass(Serializable.class);
    public static final HybridClassActor STATIC_HUB = createClass(StaticHub.class, VM_CLASS_REGISTRY);

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
    public static final FieldActor Buffer_address = findField(Buffer.class, "address");
    public static final FieldActor JLRReference_referent = findField(java.lang.ref.Reference.class, "referent");

    public static final FieldActor SYSTEM_IN = findField(System.class, "in");
    public static final FieldActor SYSTEM_OUT = findField(System.class, "out");
    public static final FieldActor SYSTEM_ERR = findField(System.class, "err");

    public static final ClassMethodActor Object_finalize = (ClassMethodActor) findMethod("finalize", Object.class);
    public static final ClassMethodActor ReferenceHandler_init = (ClassMethodActor) findMethod(java_lang_ref_Reference$ReferenceHandler, "<init>", ThreadGroup.class, String.class);
    public static final ClassMethodActor FinalizerThread_init = (ClassMethodActor) findMethod(java_lang_ref_Finalizer$FinalizerThread, "<init>", ThreadGroup.class);
    public static final ClassMethodActor Method_invoke = (ClassMethodActor) findMethod(Method.class, "invoke", Object.class, Object[].class);
    public static final ClassMethodActor MaxineVM_run = (ClassMethodActor) findMethod("run", MaxineVM.class);
    public static final ClassMethodActor VmThread_add = (ClassMethodActor) findMethod("add", VmThread.class);
    public static final ClassMethodActor VmThread_run = (ClassMethodActor) findMethod("run", VmThread.class);
    public static final ClassMethodActor VmThread_attach = (ClassMethodActor) findMethod("attach", VmThread.class);
    public static final ClassMethodActor VmThread_detach = (ClassMethodActor) findMethod("detach", VmThread.class);
    public static final ClassMethodActor ClassLoader_findBootstrapClass = (ClassMethodActor) findMethod("findBootstrapClass", ClassLoader.class);

    private static int loadCount;        // total loaded
    private static int unloadCount;    // total unloaded

    static {
        new CriticalNativeMethod(Log.class, "log_lock");
        new CriticalNativeMethod(Log.class, "log_unlock");
        new CriticalNativeMethod(Log.class, "log_flush");

        new CriticalNativeMethod(Log.class, "log_print_bytes");
        new CriticalNativeMethod(Log.class, "log_print_chars");
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
     * The map from symbol to classes for the classes defined by the class loader associated with this registry.
     * Use of {@link ConcurrentHashMap} allows for atomic insertion while still supporting fast, non-blocking lookup.
     * There's no need for deletion as class unloading removes a whole class registry and all its contained classes.
     */
    @INSPECTED
    private final ConcurrentHashMap<TypeDescriptor, ClassActor> typeDescriptorToClassActor = new ConcurrentHashMap<TypeDescriptor, ClassActor>(16384);

    private final ConcurrentHashMap<Object, Object>[] propertyMaps;

    @INSPECTED
    private ClassRegistry bootClassRegistry;

    /**
     * The class loader associated with this registry.
     */
    public final ClassLoader classLoader;

    private ClassRegistry(ClassLoader classLoader) {
        propertyMaps = Utils.cast(new ConcurrentHashMap[Property.VALUES.size()]);
        for (Property property : Property.VALUES) {
            propertyMaps[property.ordinal()] = property.createMap();
        }
        this.classLoader = classLoader;
        if (MaxineVM.isHosted()) {
            bootImageClasses = new ConcurrentLinkedQueue<ClassActor>();
            if (classLoader == HOSTED_VM_CLASS_LOADER) {
                bootClassRegistry = BOOT_CLASS_REGISTRY;
            }
        }
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
            return classLoader == HOSTED_BOOT_CLASS_LOADER ? BOOT_CLASS_REGISTRY : VM_CLASS_REGISTRY;
        }
        if (classLoader == null) {
            return BOOT_CLASS_REGISTRY;
        }

        FieldActor crField = Utils.cast(ClassLoader_classRegistry);
        final ClassRegistry result = (ClassRegistry) crField.getObject(classLoader);
        if (result != null) {
            return result;
        }

        // Non-blocking synchronization is used here to swap in a new ClassRegistry reference.
        // This could lead to some extra ClassRegistry objects being created that become garbage, but should be harmless.
        final ClassRegistry newRegistry = new ClassRegistry(classLoader);
        final Reference oldRegistry = Reference.fromJava(classLoader).compareAndSwapReference(crField.offset(), null,  Reference.fromJava(newRegistry));
        if (oldRegistry == null) {
            return newRegistry;
        }
        return UnsafeCast.asClassRegistry(oldRegistry.toJava());
    }

    public int numberOfClassActors() {
        return typeDescriptorToClassActor.size();
    }

    @HOSTED_ONLY
    public static int numberOfBootImageClassActors() {
        return BOOT_CLASS_REGISTRY.numberOfClassActors() + VM_CLASS_REGISTRY.numberOfClassActors();
    }

    /**
     * For JVMTI.
     */
    public Collection<ClassActor> getClassActors() {
        return typeDescriptorToClassActor.values();
    }

    /**
     * Defines a class and publishes it (i.e. makes it visible to the rest of the system).
     * In the context of parallel-capable class loaders, multiple threads may be concurrently trying to
     * define a given class. This method ensures that exactly one definition happens in this context.
     *
     * @param classActor the class being defined
     * @return the newly defined class (which may not be the same value as {@code classActor} in the context of parallel-capable class loaders)
     *
     * @see <a href="http://download.java.net/jdk7/docs/api/java/lang/ClassLoader.html#registerAsParallelCapable()">registerAsParallelCapable</a>
     */
    private ClassActor define0(ClassActor classActor) {
        if (logger.enabled() && logger.classRegistrationEnabled()) {
            // Use -XX:LogClassLoadingExclude=ClassRegistration to suppress this
            logger.logClassRegistration(classLoader, classActor.name(), classActor, classActor.id);
        }
        final TypeDescriptor typeDescriptor = classActor.typeDescriptor;

        final ClassActor existingClassActor = typeDescriptorToClassActor.putIfAbsent(typeDescriptor, classActor);
        if (existingClassActor != null) {
            if (classActor.isArrayClass()) {
                // The IDs of array classes are maintained by ClassActor.arrayClassIDs, they don't need to be released.
                assert classActor.id == existingClassActor.id;
                existingClassActor.copyHubs(classActor);
            } else {
                // Lost the race to define the class; release id(s) associated with 'classActor'.
                ClassIDManager.remove(classActor);
            }
            return existingClassActor;
        }
        loadCount++;

        // Add to class hierarchy, initialize vtables, and do possible deoptimizations.
        DependenciesManager.addToHierarchy(classActor);

        if (MaxineVM.isHosted()) {
            bootImageClasses.add(classActor);
        }

        if (logger.enabled()) {
            logger.logClassDefinition(classLoader, classActor.name(), classActor, classActor.id);
        }
        InspectableClassInfo.notifyClassLoaded(classActor);

        return classActor;
    }

    /**
     * Defines a class and publishes it (i.e. makes it visible to the rest of the system).
     * In the context of parallel-capable class loaders, multiple threads may be concurrently trying to
     * define a given class. This method ensures that exactly one definition happens in this context.
     *
     * @param classActor the class being defined
     * @return the newly defined class (which may not be the same value as {@code classActor} in the context of parallel-capable class loaders)
     *
     * @see <a href="http://download.java.net/jdk7/docs/api/java/lang/ClassLoader.html#registerAsParallelCapable()">registerAsParallelCapable</a>
     */
    public static <T extends ClassActor> T define(T classActor) {
        final Class<T> type = null;
        return Utils.cast(type, makeRegistry(classActor.classLoader).define0(classActor));
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

    @HOSTED_ONLY
    public static ClassActor getInBootOrVM(TypeDescriptor typeDescriptor) {
        ClassActor result =  BOOT_CLASS_REGISTRY.get(typeDescriptor);
        if (result == null) {
            result = VM_CLASS_REGISTRY.get(typeDescriptor);
        }
        return result;
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
            if (!MaxineVM.isHosted() && classLoader != BootClassLoader.BOOT_CLASS_LOADER) {
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

        ConcurrentHashMap<Object, Object> createMap() {
            return new ConcurrentHashMap<Object, Object>();
        }

        /**
         * Sets the value of this property for a given key.
         *
         * @param map the mapping from keys to values for this property
         * @param object the object for which the value of this property is to be retrieved
         * @param value the value to be set
         */
        void set(ConcurrentHashMap<Object, Object> map, Object object, Object value) {
            assert keyType.isInstance(object);
            if (value != null && value != defaultValue) {
                assert valueType.isInstance(value);
                final Object oldValue = map.put(object, value);
                assert !isFinal || oldValue == null;
            } else {
                map.remove(object);
            }
        }

        /**
         * Gets the value of this property for a given key.
         *
         * @param mapping the mapping from keys to values for this property
         * @param object the object for which the value of this property is to be retrieved
         */
        Object get(ConcurrentHashMap<Object, Object> mapping, Object object) {
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
     *
     * @param property the property to set
     * @param object the object for which the property is to be set
     * @param value the value of the property
     */
    public <Key_Type, Value_Type> void set(Property property, Key_Type object, Value_Type value) {
        property.set(propertyMaps[property.ordinal()], object, value);
    }

    /**
     * Gets the value of a given property for a given object.
     */
    public <Key_Type, Value_Type> Value_Type get(Property property, Key_Type object) {
        final Class<Value_Type> type = null;
        return Utils.cast(type, property.get(propertyMaps[property.ordinal()], object));
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

    /**
     * Classes in the boot image from this registry.
     */
    @HOSTED_ONLY
    private ConcurrentLinkedQueue<ClassActor> bootImageClasses;

    /**
     * Hidden classes not registered in the class registry.
     */
    @HOSTED_ONLY
    private static ConcurrentLinkedQueue<ClassActor> unregisteredClasses = new ConcurrentLinkedQueue<ClassActor>();

    @HOSTED_ONLY
    public  static void addUnregisteredClass(ClassActor classActor) {
        unregisteredClasses.add(classActor);
    }

    @HOSTED_ONLY
    private static class BootImageClassesIterator implements Iterable<ClassActor>, Iterator<ClassActor> {
        Iterator<ClassActor> bootListIter;
        Iterator<ClassActor> vmListIter;
        Iterator<ClassActor> unregisteredListIter;

        private BootImageClassesIterator() {
            bootListIter = BOOT_CLASS_REGISTRY.bootImageClasses.iterator();
            vmListIter = VM_CLASS_REGISTRY.bootImageClasses.iterator();
            unregisteredListIter = unregisteredClasses.iterator();
        }

        public Iterator<ClassActor> iterator() {
            return this;
        }

        public ClassActor next() {
            if (bootListIter.hasNext()) {
                return bootListIter.next();
            } else if (vmListIter.hasNext()) {
                return vmListIter.next();
            } else if (unregisteredListIter.hasNext()) {
                return unregisteredListIter.next();
            } else {
                throw new NoSuchElementException();
            }
        }

        public boolean hasNext() {
            return bootListIter.hasNext() || vmListIter.hasNext() || unregisteredListIter.hasNext();
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    @HOSTED_ONLY
    public Iterable<ClassActor> bootImageClasses() {
        return bootImageClasses;
    }

    /**
     * Gets a snapshot of the boot image classes currently in the {@link #BOOT_CLASS_REGISTRY} and {@link #VM_CLASS_REGISTRY}.
     */
    @HOSTED_ONLY
    public static Iterable<ClassActor> allBootImageClasses() {
        return new BootImageClassesIterator();
    }

    /**
     * Creates a ClassActor for a primitive type.
     */
    @HOSTED_ONLY
    private static <Value_Type extends Value<Value_Type>> PrimitiveClassActor createPrimitiveClass(Kind<Value_Type> kind) {
        return define(new PrimitiveClassActor(kind));
    }

    /**
     * Creates an ArrayClassActor for a primitive array type.
     */
    @HOSTED_ONLY
    private static <Value_Type extends Value<Value_Type>> ArrayClassActor<Value_Type> createPrimitiveArrayClass(PrimitiveClassActor componentClassActor) {
        return define(new ArrayClassActor<Value_Type>(componentClassActor));
    }

    /**
     * Creates a ClassActor for a tuple or interface type in the {@link #BOOT_CLASS_REGISTRY}.
     */
    @HOSTED_ONLY
    private static <T extends ClassActor> T createClass(Class javaClass) {
        ClassActor result = createClass(javaClass, BOOT_CLASS_REGISTRY);
        Class<T> type = null;
        return Utils.cast(type, result);
    }

    /**
     * Creates a ClassActor for a tuple or interface type in the given {@link ClassRegistry}.
     */
    @HOSTED_ONLY
    private static <T extends ClassActor> T createClass(Class javaClass, ClassRegistry classRegistry) {
        TypeDescriptor typeDescriptor = JavaTypeDescriptor.forJavaClass(javaClass);
        ClassActor classActor = classRegistry.get(typeDescriptor);
        if (classActor == null) {
            HostedClassLoader hostedClassLoader = (HostedClassLoader) classRegistry.classLoader;
            classActor = hostedClassLoader.mustMakeClassActor(typeDescriptor);
        }
        Class<T> type = null;
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
     * @param declaringClassObject the class to search for a field named {@code name}
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
        assert fieldActor != null : "Could not find field '" + name + "' in " + declaringClass;
        return fieldActor;
    }

    /**
     * Finds the method actor denoted by a given name and declaring class.
     *
     * @param name the name of the method which must be unique in the declaring class
     * @param declaringClassObject the class to search for a method named {@code name}
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
     *
     * @param declaringClassObject the class to search for a method named {@code name}
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
        } else if (name.equals("<clinit>")) {
            methodActor = ClassActor.fromJava(declaringClass).clinit;
        } else {
            methodActor = MethodActor.fromJava(Classes.getDeclaredMethod(declaringClass, name, parameterTypes));
        }
        assert methodActor != null : "Could not find method " + name + "(" + Utils.toString(parameterTypes, ", ") + ") in " + declaringClass;
        if (methodActor instanceof ClassMethodActor) {
            // Some of these methods are called during VM startup
            // so they are compiled in the image.
            CallEntryPoint callEntryPoint = CallEntryPoint.OPTIMIZED_ENTRY_POINT;
            if (methodActor.isVmEntryPoint()) {
                callEntryPoint = CallEntryPoint.C_ENTRY_POINT;
            }
            new CriticalMethod((ClassMethodActor) methodActor, callEntryPoint);
        }
        return methodActor;
    }

    /**
     * Support for ClassFileWriter.testLoadGeneratedClasses().
     */
    @HOSTED_ONLY
    public static ClassLoader testClassLoader;
    @HOSTED_ONLY
    private static ClassRegistry testClassRegistry;

    @HOSTED_ONLY
    @com.sun.max.vm.log.hosted.VMLoggerInterface(defaultConstructor = true)
    private interface ClassLoadingLoggerInterface {
        void classRegistration(
                        @VMLogParam(name = "classLoader") ClassLoader classLoader,
                        @VMLogParam(name = "className") String className,
                        @VMLogParam(name = "actor") Object classActor,
                        @VMLogParam(name = "classID") int classID
        );
        void classDefinition(
                        @VMLogParam(name = "classLoader") ClassLoader classLoader,
                        @VMLogParam(name = "className") String className,
                        @VMLogParam(name = "actor") Object classActor,
                        @VMLogParam(name = "classID") int classID
        );
    }

    public static class ClassLoadingLogger extends ClassLoadingLoggerAuto {

        boolean classRegistrationEnabled() {
            return opEnabled(Operation.ClassRegistration.ordinal());
        }

        private void traceClass(ClassLoader classLoader, String className, Object actor, int classID) {
            Log.print(className);
            Log.print(" [#");
            Log.print(classID);
            if (!MaxineVM.isHosted()) {
                Log.print(", ");
                Log.print(Reference.fromJava(actor).toOrigin());
            }
            Log.print("] < ");
            Log.print(classLoader.getClass().getName());
            if (!MaxineVM.isHosted()) {
                Log.print(" @");
                Log.print(Reference.fromJava(classLoader).toOrigin());
            }
            Log.println(" >");
        }
        @Override
        protected void traceClassDefinition(ClassLoader classLoader, String className, Object actor, int classID) {
            Log.print("Class Definition    ");
            traceClass(classLoader, className, actor, classID);
        }

        @Override
        protected void traceClassRegistration(ClassLoader classLoader, String className, Object actor, int classID) {
            Log.print("Class Registration     ");
            traceClass(classLoader, className, actor, classID);
        }
        ClassLoadingLogger() {
            super("ClassLoading", "Class loading & definition");
        }
    }

// START GENERATED CODE
    private static abstract class ClassLoadingLoggerAuto extends com.sun.max.vm.log.VMLogger {
        public enum Operation {
            ClassDefinition, ClassRegistration;

            @SuppressWarnings("hiding")
            public static final Operation[] VALUES = values();
        }

        private static final int[] REFMAPS = new int[] {0x7, 0x7};

        protected ClassLoadingLoggerAuto(String name, String optionDescription) {
            super(name, Operation.VALUES.length, optionDescription, REFMAPS);
        }

        protected ClassLoadingLoggerAuto() {
        }

        @Override
        public String operationName(int opCode) {
            return Operation.VALUES[opCode].name();
        }

        @INLINE
        public final void logClassDefinition(ClassLoader classLoader, String className, Object actor, int classID) {
            log(Operation.ClassDefinition.ordinal(), classLoaderArg(classLoader), objectArg(className), objectArg(actor), intArg(classID));
        }
        protected abstract void traceClassDefinition(ClassLoader classLoader, String className, Object actor, int classID);

        @INLINE
        public final void logClassRegistration(ClassLoader classLoader, String className, Object actor, int classID) {
            log(Operation.ClassRegistration.ordinal(), classLoaderArg(classLoader), objectArg(className), objectArg(actor), intArg(classID));
        }
        protected abstract void traceClassRegistration(ClassLoader classLoader, String className, Object actor, int classID);

        @Override
        protected void trace(Record r) {
            switch (r.getOperation()) {
                case 0: { //ClassDefinition
                    traceClassDefinition(toClassLoader(r, 1), toString(r, 2), toObject(r, 3), toInt(r, 4));
                    break;
                }
                case 1: { //ClassRegistration
                    traceClassRegistration(toClassLoader(r, 1), toString(r, 2), toObject(r, 3), toInt(r, 4));
                    break;
                }
            }
        }
    }

// END GENERATED CODE
}
