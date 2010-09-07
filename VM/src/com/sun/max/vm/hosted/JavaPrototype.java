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
package com.sun.max.vm.hosted;

import static com.sun.max.annotate.LOCAL_SUBSTITUTION.Static.*;
import static com.sun.max.vm.VMConfiguration.*;
import static com.sun.max.vm.hosted.HostedBootClassLoader.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import com.sun.max.*;
import com.sun.max.annotate.*;
import com.sun.max.asm.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.classfile.constant.*;
import com.sun.max.vm.compiler.builtin.*;
import com.sun.max.vm.compiler.snippet.*;
import com.sun.max.vm.jdk.Package;
import com.sun.max.vm.thread.*;
import com.sun.max.vm.type.*;

/**
 * The {@link ClassActor} context when {@linkplain BootImageGenerator generating} the
 * boot image or otherwise executing code that loads and uses {@link ClassActor}s.
 *
 * There is a single global {@code JavaPrototype} object which is {@linkplain #initialize(boolean) initialized} once.
 *
 * @author Bernd Mathiske
 * @author Doug Simon
 */
public final class JavaPrototype extends Prototype {

    /**
     * The name of the system that can be used to specify extra classes and packages to be loaded
     * into a Java prototype by {@link #loadCoreJavaPackages()}. The value of the property is
     * parsed as a space separated list of class and package names. Package names are those
     * prefixed by '^'.
     */
    public static final String EXTRA_CLASSES_AND_PACKAGES_PROPERTY_NAME = "max.image.extraClassesAndPackages";

    private static JavaPrototype theJavaPrototype;
    private final Map<MaxPackage, MaxPackage> excludedMaxPackages = new HashMap<MaxPackage, MaxPackage>();
    private final Set<MaxPackage> loadedMaxPackages = new HashSet<MaxPackage>();
    private final Map<MethodActor, AccessibleObject> methodActorMap = new HashMap<MethodActor, AccessibleObject>();
    private final Map<FieldActor, Field> fieldActorMap = new HashMap<FieldActor, Field>();
    private final Map<ClassActor, Class> classActorMap = new ConcurrentHashMap<ClassActor, Class>();
    private final Map<Method, MethodActor> javaMethodMap = new HashMap<Method, MethodActor>();
    private final Map<Constructor, MethodActor> javaConstructorMap = new HashMap<Constructor, MethodActor>();
    private final Map<Field, FieldActor> javaFieldMap = new HashMap<Field, FieldActor>();

    /**
     * Gets a reference to the singleton Java prototype.
     *
     * @return the global java prototype
     */
    public static JavaPrototype javaPrototype() {
        return theJavaPrototype;
    }

    /**
     * Gets all packages in the specified root package that extend the specified package class.
     *
     * @param maxPackageClass the package class that the package must extend
     * @param rootPackage the root package in which to begin the search
     * @return a sequence of the packages that match the criteria
     */
    private List<MaxPackage> getPackages(final Class<? extends MaxPackage> maxPackageClass, MaxPackage rootPackage) {
        final List<MaxPackage> packages = new LinkedList<MaxPackage>();
        for (MaxPackage maxPackage : rootPackage.getTransitiveSubPackages(HOSTED_BOOT_CLASS_LOADER.classpath())) {
            if (maxPackageClass.isInstance(maxPackage) && vmConfig().isMaxineVMPackage(maxPackage)) {
                packages.add(maxPackage);
            }
        }
        MaxPackage[] result = packages.toArray(new MaxPackage[packages.size()]);
        java.util.Arrays.sort(result);
        return java.util.Arrays.asList(result);
    }

    /**
     * Loads a single java class into the prototype, building the corresponding Actor
     * representation.
     *
     * @param javaClass the class to load into the prototype
     */
    private void loadClass(Class javaClass) {
        assert !MaxineVM.isHostedOnly(javaClass);
        loadClass(javaClass.getName());
    }

    /**
     * Loads a single java class into the prototype, building the corresponding Actor
     * representation.
     *
     * @param name the name of the java class as a string
     */
    public void loadClass(String name) {
        Class clazz = Classes.load(HOSTED_BOOT_CLASS_LOADER, name);
        Classes.initialize(clazz);
    }

    private final PackageLoader packageLoader;

    /**
     * Gets the package loader for this java prototype.
     *
     * @return the package loader for this prototype
     */
    public PackageLoader packageLoader() {
        return packageLoader;
    }

    /**
     * Loads a package into the prototype, building the internal Actor representations
     * of all the classes in the package.
     *
     * @param maxPackage the package to load
     */
    private void loadPackage(MaxPackage maxPackage) {
        final MaxPackage excludedBy = excludedMaxPackages.get(maxPackage);
        if (excludedBy != null) {
            Trace.line(1, "Excluding " + maxPackage + " (excluded by " + excludedBy + ")");
            return;
        }

        for (MaxPackage excludedPackage : maxPackage.excludes()) {
            if (loadedMaxPackages.contains(excludedPackage)) {
                ProgramError.unexpected("Package " + excludedPackage + " is excluded by " + maxPackage + ". " +
                                "Adjust class path to ensure " + maxPackage + " is loaded before " + excludedPackage);
            }
            excludedMaxPackages.put(excludedPackage, maxPackage);
        }
        packageLoader.load(maxPackage, false);
        loadedMaxPackages.add(maxPackage);
    }

    /**
     * Loads a package into the prototype, building the internal Actor representations of all the classes in the
     * prototype.
     *
     * @param name the name of the package as a string
     * @param recursive a boolean indicating whether to load all subpackages of the specified package
     * @return a sequence of all the classes loaded from the specified package (and potentially its subpackages).
     */
    public List<Class> loadPackage(String name, boolean recursive) {
        return packageLoader.load(name, recursive);
    }

    /**
     * Load packages corresponding to VM configurations.
     */
    private void loadVMConfigurationPackages() {
        for (MaxPackage p : vmConfig().packages()) {
            loadPackage(p);
        }
    }

    /**
     * Loads a sequence of packages.
     *
     * @param packages the packages to load
     */
    private void loadPackages(List<MaxPackage> packages) {
        for (MaxPackage p : packages) {
            loadPackage(p);
        }
    }

    /**
     * Loads extra packages and classes that are necessary to build a self-sufficient VM image.
     */
    public void loadCoreJavaPackages() {
        if (System.getProperty("max.allow.all.core.packages") == null) {
            // Don't want the static Map fields initialized
            HostedBootClassLoader.omitClass(java.lang.reflect.Proxy.class);

            HostedBootClassLoader.omitClass(JavaTypeDescriptor.getDescriptorForJavaString(File.class.getName() + "$LazyInitialization"));
            HostedBootClassLoader.omitClass(JavaTypeDescriptor.getDescriptorForJavaString(java.util.Calendar.class.getName() + "$CalendarAccessControlContext"));

            // LogManager and FileSystemPreferences have many side effects
            // that we do not wish to account for before running the target VM.
            // In particular they install shutdown hooks,
            // which then end up in the boot image and cause bugs at target runtime.
            HostedBootClassLoader.omitPackage("java.util.logging", true);
            HostedBootClassLoader.omitPackage("java.util.prefs", true);
        }

        loadPackage("java.lang", false);
        loadPackage("java.lang.reflect", false); // needed to compile and to invoke the main method
        loadPackage("java.lang.ref", false);
        loadPackage("java.io", false);
        loadPackage("java.nio", false);
        loadPackage("java.nio.charset", false);
        loadPackage("java.util", false);
        loadPackage("java.util.zip", false); // needed to load classes from jar/zip files
        loadPackage("java.util.jar", false); // needed to load classes from jar files
        loadClass(sun.misc.VM.class);

        // Some of these classes contain field offsets cached in static fields.
        // These offsets need to be patched with Maxine values.
        loadPackage("java.util.concurrent.atomic", false);

        // These classes need to be compiled and in the boot image in order to be able to
        // run the optimizing compiler at run time (amongst other reasons)
        loadClass(sun.misc.SharedSecrets.class);
        loadClass(sun.reflect.annotation.AnnotationParser.class);
        loadClass(sun.reflect.Reflection.class);
        loadClass(java.util.concurrent.atomic.AtomicLong.class);
        loadClass(java.security.ProtectionDomain.class);
        loadClass(java.security.DomainCombiner.class);
        loadClass(java.security.PrivilegedAction.class);

        // Necessary for Java Run Scheme to initialize the System class:
        loadClass(sun.misc.Version.class);
        loadPackage("sun.nio.cs", false);

        // Needed to satisfy the requirement that java.lang.ref.Reference.ReferenceHandler.run()
        // does not do any allocation (as a result of loading this class) while holding the GC lock.
        loadClass(sun.misc.Cleaner.class);

        // Necessary for early tracing:
        loadPackage("java.util.regex", false);
        loadClass(sun.security.action.GetPropertyAction.class);

        String value = System.getProperty(EXTRA_CLASSES_AND_PACKAGES_PROPERTY_NAME);
        if (value != null) {
            for (String s : value.split("\\s+")) {
                if (s.charAt(0) == '^') {
                    loadPackage(s.substring(1), false);
                } else {
                    loadClass(s);
                }
            }
        }

        if (System.getProperty("max.allow.all.core.packages") == null) {
            HostedBootClassLoader.omitPackage("java.security", false);
        }
    }

    private static List<Class> mainPackageClasses = new ArrayList<Class>();

    public static List<Class> mainPackageClasses() {
        return mainPackageClasses;
    }

    /**
     * Ensures that all the Maxine classes currently in the {@linkplain ClassRegistry#vmClassRegistry() VM class
     * registry} are {@linkplain Classes#initialize(Class) initialized}. Any class in a subpackage of {@code
     * com.sun.max} is deemed to be a Maxine class. These initializers are never re-run in the target VM
     * and so they are omitted from the boot image (as if they had the {@link HOSTED_ONLY} annotation
     * applied to them).
     */
    private static void initializeMaxClasses() {
        for (ClassActor classActor : ClassRegistry.BOOT_CLASS_REGISTRY.copyOfClasses()) {
            if (MaxineVM.isMaxineClass(classActor)) {
                try {
                    Classes.initialize(classActor.toJava());
                } catch (HostOnlyClassError error) {
                }
            }
        }
    }

    /**
     * Loads all classes annotated with {@link METHOD_SUBSTITUTIONS} and performs the relevant substitutions.
     */
    private void loadMethodSubstitutions(final VMConfiguration vmConfiguration, final PackageLoader pl) {
        new ClassSearch(true) {
            @Override
            protected boolean visitClass(String className) {
                if (className.endsWith(".Package")) {
                    try {
                        Class<?> packageClass = Class.forName(className);
                        if (VMPackage.class.isAssignableFrom(packageClass)) {
                            VMPackage vmPackage = (VMPackage) packageClass.newInstance();
                            if (vmPackage.isPartOfMaxineVM(vmConfiguration) && vmPackage.containsMethodSubstitutions()) {
                                String[] classes = pl.listClassesInPackage(vmPackage.name(), false);
                                for (String cn : classes) {
                                    try {
                                        Class<?> c = Class.forName(cn, false, Package.class.getClassLoader());
                                        METHOD_SUBSTITUTIONS annotation = c.getAnnotation(METHOD_SUBSTITUTIONS.class);
                                        if (annotation != null) {
                                            loadClass(c);
                                            METHOD_SUBSTITUTIONS.Static.processAnnotationInfo(annotation, toClassActor(c));
                                        }
                                    } catch (Exception e) {
                                        throw ProgramError.unexpected(e);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        throw ProgramError.unexpected(e);
                    }
                }
                return true;
            }
        }.run(pl.classpath);
    }

    /**
     * Extends {@link PackageLoader} to ignore classes that are {@link HostOnlyClassError prototype only} or
     * explicitly {@linkplain OmittedClassError omitted} from the boot image.
     *
     * @author Doug Simon
     */
    static class PrototypePackageLoader extends PackageLoader {

        public PrototypePackageLoader(ClassLoader classLoader, Classpath classpath) {
            super(classLoader, classpath);
        }

        @Override
        protected Class loadClass(String className) {
            try {
                return super.loadClass(className);
            } catch (HostOnlyClassError e) {
                Trace.line(2, "Ignoring hosted only type: " + className);
            } catch (OmittedClassError e) {
                Trace.line(2, "Ignoring explicitly omitted type: " + className);
            }
            return null;
        }
    }

    /**
     * Initializes the global Java prototype. This also initializes the global {@linkplain MaxineVM#vm() VM}
     * context if it hasn't been set.
     *
     * @param complete specifies whether to load more than just the VM scheme packages
     */
    public static void initialize(final boolean complete) {
        assert theJavaPrototype == null : "Cannot initialize the JavaPrototype more than once";
        if (MaxineVM.vm() == null) {
            new VMConfigurator(null).create(true);
        }
        theJavaPrototype = new JavaPrototype(complete);

    }

    /**
     * Create a new Java prototype with the specified VM configuration.
     *
     * @param complete specifies whether to load more than just the VM scheme packages
     */
    private JavaPrototype(final boolean complete) {
        VMConfiguration config = vmConfig();
        packageLoader = new PrototypePackageLoader(HOSTED_BOOT_CLASS_LOADER, HOSTED_BOOT_CLASS_LOADER.classpath());
        theJavaPrototype = this;

        if (Trace.hasLevel(1)) {
            PrintStream out = Trace.stream();
            out.println("======================== VM Configuration ========================");
            out.print(vmConfig());
            out.println("JDK: " + System.getProperty("java.version"));
            out.println("==================================================================");
        }

        loadVMConfigurationPackages();

        ClassActor.DEFERRABLE_QUEUE_1.runAll();

        initializeMaxClasses();

        config.bootCompilerScheme().createBuiltins(packageLoader);
        Builtin.register(config.bootCompilerScheme());
        config.bootCompilerScheme().createSnippets(packageLoader);
        Snippet.register();

        loadMethodSubstitutions(config, packageLoader);

        // Need all of C1X
        loadPackage("com.sun.cri", true);
        loadPackage("com.sun.c1x", true);

        if (complete) {

            // TODO: Load the following package groups in parallel
            loadPackages(getPackages(BasePackage.class, new com.sun.max.Package()));
            loadPackages(getPackages(VMPackage.class, new com.sun.max.vm.Package()));
            loadPackages(getPackages(AsmPackage.class, new com.sun.max.asm.Package()));

            initializeMaxClasses();

            config.initializeSchemes(MaxineVM.Phase.BOOTSTRAPPING);

            // VM implementation classes ending up in the bootstrap image
            // are supposed to be limited to those loaded up to here.
            //
            // This enables detection of violations of said requirement:
            ClassActor.prohibitPackagePrefix(new com.sun.max.Package());

            VmThreadLocal.completeInitialization();

        } else {
            config.initializeSchemes(MaxineVM.Phase.BOOTSTRAPPING);
        }
    }

    /**
     * Gets the corresponding Java reflection representation of the specified method.
     *
     * @param methodActor the method actor for which to retrieve the Java equivalent
     * @return the Java reflection method for the specified method actor
     */
    public Method toJava(MethodActor methodActor) {
        synchronized (methodActorMap) {
            Method javaMethod = (Method) methodActorMap.get(methodActor);
            if (javaMethod == null) {
                final Class<?> holder = methodActor.holder().toJava();
                final SignatureDescriptor descriptor = methodActor.descriptor();
                final Class[] parameterTypes = descriptor.resolveParameterTypes(holder.getClassLoader());
                final ClassLoader classLoader = holder.getClassLoader();
                final String name = methodActor.isLocalSubstitute() ? LOCAL_SUBSTITUTION.Static.toSubstituteName(methodActor.name.toString()) : methodActor.name.toString();
                javaMethod = Classes.getDeclaredMethod(holder, descriptor.resultDescriptor().resolveType(classLoader), name, parameterTypes);
                methodActorMap.put(methodActor, javaMethod);
            }
            assert MethodActor.fromJava(javaMethod) == methodActor;
            return javaMethod;
        }
    }

    /**
     * Gets the corresponding Java reflection representation of the specified constructor.
     *
     * @param methodActor the method actor for which to retrieve the Java equivalent
     * @return the Java reflection method for the specified method actor
     */
    public Constructor toJavaConstructor(MethodActor methodActor) {
        synchronized (methodActorMap) {
            Constructor javaConstructor = (Constructor) methodActorMap.get(methodActor);
            if (javaConstructor == null) {
                final Class<?> holder = methodActor.holder().toJava();
                final Class[] parameterTypes = methodActor.descriptor().resolveParameterTypes(holder.getClassLoader());
                javaConstructor = Classes.getDeclaredConstructor(holder, parameterTypes);
                methodActorMap.put(methodActor, javaConstructor);
            }
            assert MethodActor.fromJavaConstructor(javaConstructor) == methodActor;
            return javaConstructor;
        }
    }

    /**
     * Gets the corresponding Java reflection representation of the specified field.
     *
     * @param fieldActor the field actor for which to get the Java equivalent
     * @return the Java reflection field for the specified field actor
     */
    public Field toJava(FieldActor fieldActor) {
        synchronized (fieldActorMap) {
            Field javaField = fieldActorMap.get(fieldActor);
            if (javaField == null) {
                final Class javaHolder = fieldActor.holder().toJava();
                javaField = Classes.getDeclaredField(javaHolder, fieldActor.name.toString());
                fieldActorMap.put(fieldActor, javaField);
            }
            return javaField;
        }
    }

    /**
     * Gets the corresponding Java class for the specified class actor.
     *
     * @param classActor the class actor for which to get the Java equivalent
     * @return the Java reflection class for the specified class actor
     */
    public Class toJava(ClassActor classActor) {
        Class javaClass = classActorMap.get(classActor);
        if (javaClass == null) {
            try {
                javaClass = classActor.typeDescriptor.resolveType(classActor.classLoader);
            } catch (OmittedClassError e) {
                // Failed with the prototype loader: try again with the VM class loader.
                javaClass = classActor.typeDescriptor.resolveType(BootClassLoader.BOOT_CLASS_LOADER);
            }
            classActorMap.put(classActor, javaClass);
        }
        return javaClass;
    }

    /**
     * Gets the corresponding class actor for the specified Java class.
     *
     * @param javaClass the Java class for which to get the class actor
     * @return the class actor for {@code javaClass} or {@code null} if {@code javaClass} is annotated with {@link HOSTED_ONLY}
     */
    public ClassActor toClassActor(Class javaClass) {
        if (MaxineVM.isHostedOnly(javaClass)) {
            return null;
        }

        TypeDescriptor typeDescriptor = JavaTypeDescriptor.forJavaClass(javaClass);
        ClassActor classActor = ClassRegistry.BOOT_CLASS_REGISTRY.get(typeDescriptor);
        if (classActor != null) {
            return classActor;
        }
        return typeDescriptor.resolveHosted(javaClass.getClassLoader());
    }

    /**
     * Gets the corresponding method actor for the specified Java method.
     *
     * @param javaMethod the Java method for which to get the method actor
     * @return the method actor for {@code javaMethod}
     */
    public MethodActor toMethodActor(Method javaMethod) {
        synchronized (javaMethodMap) {
            MethodActor methodActor = javaMethodMap.get(javaMethod);
            if (methodActor == null) {
                final Utf8Constant name = SymbolTable.makeSymbol(javaMethod.getAnnotation(LOCAL_SUBSTITUTION.class) != null ? toSubstituteeName(javaMethod.getName()) : javaMethod.getName());
                final ClassActor holder = toClassActor(javaMethod.getDeclaringClass());
                ProgramError.check(holder != null, "Could not find " + javaMethod.getDeclaringClass());
                final SignatureDescriptor signature = SignatureDescriptor.fromJava(javaMethod);
                methodActor = holder.findLocalMethodActor(name, signature);
                ProgramError.check(methodActor != null, "Could not find " + name + signature + " in " + holder);
                javaMethodMap.put(javaMethod, methodActor);
            }
            return methodActor;
        }
    }

    /**
     * Gets the corresponding method actor for the specified Java constructor.
     *
     * @param javaConstructor the Java constructor for which to get the method actor
     * @return the method actor for {@code javaConstructor}
     */
    public MethodActor toMethodActor(Constructor javaConstructor) {
        synchronized (javaConstructorMap) {
            MethodActor methodActor = javaConstructorMap.get(javaConstructor);
            if (methodActor == null) {
                final ClassActor holder = toClassActor(javaConstructor.getDeclaringClass());
                final SignatureDescriptor signature = SignatureDescriptor.fromJava(javaConstructor);
                methodActor = holder.findLocalMethodActor(SymbolTable.INIT, signature);
                ProgramError.check(methodActor != null, "Could not find <init>" + signature + " in " + holder);
                javaConstructorMap.put(javaConstructor, methodActor);
            }
            return methodActor;
        }
    }

    /**
     * Gets the corresponding field actor for the specified Java field.
     *
     * @param javaField the Java field for which to get the field actor
     * @return the field actor for {@code javaField}
     */
    public FieldActor toFieldActor(Field javaField) {
        synchronized (javaFieldMap) {
            FieldActor fieldActor = javaFieldMap.get(javaField);
            if (fieldActor == null) {
                final ClassActor holder = toClassActor(javaField.getDeclaringClass());
                final TypeDescriptor signature = JavaTypeDescriptor.forJavaClass(javaField.getType());
                final Utf8Constant name = SymbolTable.makeSymbol(javaField.getName());
                fieldActor = holder.findFieldActor(name, signature);
                ProgramError.check(fieldActor != null, "Could not find " + name + signature + " in " + holder);
                javaFieldMap.put(javaField, fieldActor);
            }
            return fieldActor;
        }
    }
}