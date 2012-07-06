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
/**
 * This package contains classes that can only be run in a {@linkplain com.sun.max.vm.MaxineVM#isHosted() hosted}
 * environment.
 *
 * <h2>Hosted Class Loading</h2>
 *
 * One purpose of the boot image generator is to populate the {@link com.sun.max.vm.type.ClassRegistry class registries} for the target VM's
 * boot class loader and the target VM's VM class loader. Unlike a traditional C-based VM like Hotspot, Maxine's boot
 * image must contain implementations of those JDK (boot) classes that are used by the VM classes, in order to enable
 * the VM to have enough functionality to load additional classes.
 * <p>
 * In early versions of Maxine, the VM classes and the JDK (boot) classes were co-mingled in the boot class loader. This
 * had a number of drawbacks, so the VM classes are now loaded by the {@link com.sun.max.vm.type.VMClassLoader VM class loader} and kept
 * separate from the {@link com.sun.max.vm.type.BootClassLoader boot class loader}. New classes are loaded by the boot loader at runtime
 * but, currently, the set of VM classes is fixed at image build time.
 * <p>>
 *
 * {@link com.sun.max.vm.type.BootClassLoader} and {@link com.sun.max.vm.type.VMClassLoader} are runtime entities only. During image build the
 * {@link com.sun.max.vm.type.ClassRegistry class registries} are built up by parallel classes {@link com.sun.max.vm.hosted.HostedBootClassLoader} and
 * {@link com.sun.max.vm.hosted.HostedVMClassLoader} that operate in a somewhat special way in order to deal with the unique characteristics
 * of the prototype environment. In the final image any instances of {@link com.sun.max.vm.hosted.HostedBootClassLoader} and
 * {@link com.sun.max.vm.hosted.HostedVMClassLoader} are replaced by the singleton instances of {@link com.sun.max.vm.type.BootClassLoader} and
 * {@link com.sun.max.vm.type.VMClassLoader}.
 * <p>
 * The {@link com.sun.max.vm.type.VMClassLoader} has the {@link com.sun.max.vm.type.BootClassLoader} as its parent, and this is mimiced by the
 * {@link com.sun.max.vm.hosted.HostedBootClassLoader} and {@link com.sun.max.vm.hosted.HostedVMClassLoader} classes. {@link com.sun.max.vm.hosted.HostedBootClassLoader} is a direct
 * subclass of {@link java.lang.ClassLoader}, therefore its parent is the host VM's boot class loader. It is valuable to review
 * the basic class loading mechanism that is enforced by {@link java.lang.ClassLoader#loadClass(String)}. The fundamental mechanism is
 * delegation based. If the classloader has a parent, {@link java.lang.ClassLoader#loadClass(String)} first invokes
 * {@code parent.loadClass}. Every classloader has the boot class loader as its ultimate parent, so if the class can be
 * found on the {@code bootclasspath}, it will be loaded by the boot class loader. If the parent fails to load the class
 * {@link java.lang.ClassLoader#loadClass(String)} invokes the {@link java.lang.ClassLoader#findClass} method of the classloader. This happens at every
 * level of the parent delegation chain. The default implementation of {@link java.lang.ClassLoader#findClass} simply throws
 * {@link java.lang.ClassNotFoundException}. Evidently, {@link java.lang.ClassLoader#findClass} is where a specific classloader
 * specifies how it is going to locate the bytes that will define the class, e.g. by searching a file system, loading from
 * a URL, etc. The general method for reporting failure to load/find a class is to throw {@link java.lang.ClassNotFoundException}.
 * Note that the delegation model guarantees that many exceptions will be thrown for every attempt to load a class, as every search
 * must delegate up to the boot class loader, which will normally not be the loader that will be able to find the class.
 * <p>
 * Class loader semantics are complicated by inheritance between classloader implementations. However,
 * calls on {@code super.loadClass} are conceptually quite different from {@code parent.loadClass}. In the latter case a
 * different classloader instance is being delegated to. In the former, the call merely represents code reuse. Mentally
 * inlining the code of {@code super.loadClass} can help make this clear.
 * <h3>Loading VM classes</h2>
 * The process of defining the contents of a boot image starts with the code that collects together all the packages
 * that are candidates for inclusion in the boot image. Generally speaking, every class in such as package is "loaded"
 * into the boot image by invoking {@link com.sun.max.vm.hosted.HostedVMClassLoader#loadClass}. The decision as to whether
 * a class is placed into the boot class registry or the VM class registry is made entirely based on whether
 * {@link com.sun.max.vm.hosted.HostedBootClassLoader} or {@link com.sun.max.vm.hosted.HostedVMClassLoader} succeeds in defining the class, which is itself determined
 * by the delegation hierarchy (recall that {@link com.sun.max.vm.hosted.HostedBootClassLoader} is the parent of {@link com.sun.max.vm.hosted.HostedVMClassLoader}).
 *<p>
 * Note that (almost) every class is actually "loaded" twice. The first load is by one of the host VM's classloaders.
 * Which classloader succeeded, determines which {@link com.sun.max.vm.type.ClassRegistry} the {@link com.sun.max.vm.actor.holder.ClassActor} for the {@link java.lang.Class} is
 * then "loaded" in. Loading into the host VM is important in order that the image generator itself can execute the class methods.
 * Loading into the class registry has no host effect at all, it is for the target VM only. Some classes that are in the
 * VM package set must be loaded into the host VM but <i>not</i> loaded into the target VM class registry. These comprise
 * classes annotated as {@link com.sun.max.annotate.HOSTED_ONLY} and certain JDK classes that are explicitly prevented from being in the boot image.
 * Special checks are made in {@link com.sun.max.vm.hosted.HostedBootClassLoader} or {@link com.sun.max.vm.hosted.HostedVMClassLoader} to avoid these classes being
 * defined in the target VM class registry.
 * <h3>Implementation Details</h3>
 * There is a lot of similarity in the code for {@link com.sun.max.vm.hosted.HostedBootClassLoader} and {@link com.sun.max.vm.hosted.HostedVMClassLoader} and,
 * despite the caveat above regarding implementation inheritance, this is abstracted into a {@link com.sun.max.vm.hosted.HostedClassLoader}
 * superclass. {@link com.sun.max.vm.hosted.HostedClassLoader} maintains one significant piece of state, {@link com.sun.max.vm.hosted.HostedClassLoader#definedClasses}
 * which is a map from class names to {@link java.lang.Class instances}. Searching this map first, avoids the delegation
 * chain for classes that have already been loaded.It is an invariant that membership in this map also implies membership in the
 * associated {@link com.sun.max.vm.type.ClassRegistry}. Note that the {@link com.sun.max.vm.type.ClassRegistry} is not especially helpful in locating {@link java.lang.Class} instances,
 * as it maintains a map from {@link com.sun.max.vm.type.TypeDescriptor} instances to {@link com.sun.max.vm.actor.holder.ClassActor} instances.
 * <p>
 * The other piece of state maintained by {@link com.sun.max.vm.hosted.HostedClassLoader} is a {@link com.sun.max.program.Classpath}. For {@link com.sun.max.vm.hosted.HostedBootClassLoader}
 * this is identical to the system boot class path and for {@link com.sun.max.vm.hosted.HostedVMClassLoader} it is identical to the system
 * (or application) class loader classpath. The purpose of this is to enable to actual bytes of the class file to be located
 * in order to execute Maxine's class file processing code as part of creating the {@link com.sun.max.vm.actor.holder.ClassActor} instance. N.B. The bytes
 * cannot be acquired through the host VM as there is no API for that.
 * <h3>Array Classes and Invocation Stub Classes</h3>
 * <h4>Array Classes</h4>
 * Maxine must be able to "load" array classes into the {@link com.sun.max.vm.type.ClassRegistry}. However, it is illegal to pass an array class
 * to {@link java.lang.ClassLoader#loadClass(String)}. Although array classes are not in the initial set of classes processed by the
 * boot image generator, they occur in the code of VM classes. In the host VM they are handled automatically and always
 * defined by the class loader that defined the array element type. In order to get these into the appropriate  {@link com.sun.max.vm.type.ClassRegistry},
 * the Maxine class file analysis includes a call to {@link com.sun.max.vm.hosted.HostedClassLoader#mustMakeClassActor}, with the array name
 * as argument. The array class is "loaded" by special code in {@link com.sun.max.vm.hosted.HostedClassLoader#findClass}, that first ensures that the
 * class for the component type is loaded.
 * <h4>Invocation Stub Classes</h4>
 * These classes are generated dynamically by Maxine and are not available in the host file system. The class file bytes are
 * stored in the Java heap and accessed as needed through {@link com.sun.max.vm.classfile.ClassfileReader#findGeneratedClassfile}. Stub classes
 * are also handled in {@link com.sun.max.vm.hosted.HostedClassLoader#findClass}. Stub classes are not accessed via the file system so they cannot
 * be loaded by either the boot loader or the system class loader; they are created by invoking {@link java.lang.ClassLoader#defineClass}
 * on the bytes from {@link com.sun.max.vm.classfile.ClassfileReader#findGeneratedClassfile}. Stub classes are , therefore, the only classes
 * whose defining loader is {@link com.sun.max.vm.hosted.HostedBootClassLoader} or {@link com.sun.max.vm.hosted.HostedVMClassLoader}.
 * <p>
 * Note that both array classes and stub classes can be associated with either {@link com.sun.max.vm.hosted.HostedBootClassLoader} or {@link com.sun.max.vm.hosted.HostedVMClassLoader},
 * depending on the array component type, and target of the stub.
 *<h3>The Loading Process</h3>
 * It was noted that the {@code loadClass} method of the {@link com.sun.max.vm.hosted.HostedVMClassLoader} instance starts the loading process for
 * a named class. This might be a VM class or it might be a JDK class. {@link com.sun.max.vm.hosted.HostedVMClassLoader} does not override {@code loadClass}
 * so it is {@link com.sun.max.vm.hosted.HostedClassLoader#loadClass} that is actually invoked. After checking whether this class has been defined (loaded)
 * already, it invokes {@code super.loadClass}, which resolves to {@link java.lang.ClassLoader#loadClass(String)}. As noted, this will first delegate
 * to the parent, which is {@link com.sun.max.vm.hosted.HostedBootClassLoader}. This does {@link com.sun.max.vm.hosted.HostedBootClassLoader#loadClass override} {@code loadClass},
 * to handle a special case we will come back to. Initially, however, it again simply invokes {@code super.loadClass}, which will delegate to
 * the host VM's boot class loader. If the boot class loader fails to load the class, {@link com.sun.max.vm.hosted.HostedClassLoader#findClass} will
 * be run, but since, this only handles array classes and stubs, the lookup will fall back to {@link com.sun.max.vm.hosted.HostedVMClassLoader}, where
 * its {@code findClass} method will be run. This also invokes {@code super.findClass} to handle arrays and stubs associated with the
 * VM classes. Note that the delegated to {@link com.sun.max.vm.hosted.HostedBootClassLoader} instance will not load VM array classes because
 * it will not resolve VM classes, which is a pre-requisite. So, finally, {@link com.sun.max.vm.hosted.HostedVMClassLoader#findClass} tries to load the class using
 * {@link java.lang.ClassLoader#getSystemClassLoader}. This must succeed unless there is some error in the
 * initial configuration. SImilarly a search for a JDK class must succeed in the boot class loader again, unless there is a
 * naming error. Using the system class loader to load the VM class ensures that there is only a single instance of the
 * class in the host VM, whether it is being loaded as part of the VM configuration specification, or explicitly used in the code of
 * the boot image generator.
 * <p>
 * Note that whether a JDK class or a VM class is successfully loaded, control returns to the common code in
 * {@link com.sun.max.vm.hosted.HostedClassLoader#loadClass}. The "this" argument will be {@link com.sun.max.vm.hosted.HostedBootClassLoader} or {@link com.sun.max.vm.hosted.HostedVMClassLoader},
 * respectively. The common code then invokes the {@link com.sun.max.vm.hosted.HostedClassLoader#extraLoadClassChecks} (abstract) method. This is where
 * the classloader-specific checks against restrictions on classes are specified. This method can throw an exception
 * or return false to prevent the class being defined in the {@link com.sun.max.vm.type.ClassRegistry}, which is handled by
 * {@link com.sun.max.vm.hosted.HostedClassLoader#defineLoadedClassActor}. This method reads the bytes of the class, and the causes the
 * {@link com.sun.max.vm.actor.holder.ClassActor} to be created and placed in the appropriate registry.
 * <p>
 * <h2>Boot Class References to VM classes, aka "The Special Case"</h2>
 * All the above discussion has assumed that relationships between VM classes and JDK (boot) classes are one-way, i.e. originating from the VM
 * classes. Hence the fact that {@link com.sun.max.vm.hosted.HostedBootClassLoader} is the parent of {@link com.sun.max.vm.hosted.HostedVMClassLoader}. Unfortunately, this is not
 * completely true. Evidently, this must be true at the source code level, as the JDK classes are VM independent. However,
 * there are a number of special cases that result from implementation mechanisms used in Maxine that cause references from JDK classes
 * to VM classes. These are:
 * <ul>
 * <li>Native methods. Maxine implements the JNI invocation mechanism by defining bytecodes for the native method that refer to the
 * Maxine JNI classes.</li>
 * <li>{@link com.sun.max.annotate.SUBSTITUTE Substituted methods}. The Java definitions of substituted methods refer to VM classes.</li>
 * <li>Injected fields. Maxine injects additional fields into certain JDK classes to support the VM implementation. The types of these
 * fields are VM classes.
 * <li>{@linkplain com.sun.max.vm.reflection.InvocationStub Invocation stubs} to methods in JDK classes contain references to VM classes (see below).
 * </ul>
 * <p>
 * To resolve the class references that result from these special cases requires that the Maxine boot class loader be able to access the
 * requisite VM class. This can occur at image build time or at runtime. Note, however, that there is never a need to invoke
 * {@link java.lang.ClassLoader#loadClass(String)} to resolve the class, as at the time the reference occurs, the VM class will have been defined.
 * This is fortunate since invoking {@link com.sun.max.vm.hosted.HostedVMClassLoader#loadClass} would cause recursion, owing to {@link com.sun.max.vm.hosted.HostedBootClassLoader}
 * being its parent. In {@link com.sun.max.vm.hosted.HostedBootClassLoader}, locating VM classes is handled by last chance code in the catch clause for
 * {@link java.lang.ClassNotFoundException} that searches the {@link com.sun.max.vm.hosted.HostedVMClassLoader#definedClasses} map.
 * <p>
 *
 *<h3>Consequences</h3>
 * This ability to resolve VM classes requires special handling in certain situations.
 *<h4>Array classes</h4>
 * It is now possible for {@link com.sun.max.vm.hosted.HostedBootClassLoader}
 * to resolve a VM class that is a component type of an array, and then go on to define the array itself, which would
 * violate the rules for arrays. A special check is made in {@link com.sun.max.vm.hosted.HostedClassLoader#findArrayClass} to
 * catch this situation and throw {@link java.lang.ClassNotFoundException}. The array will then be correctly resolved by
 * {@link com.sun.max.vm.hosted.HostedVMClassLoader}.
 *<h4>Invocation Stub Classes</h4>
 * Invocation stubs created by {@link com.sun.max.vm.reflection.InvocationStubGenerator} are assigned
 * a class loader based on the classloader of the class declaring the method that the stub is targeting. As noted above,
 * some of the stubs in the boot image target methods in JDK classes, so those stub classes are associated with
 * {@link com.sun.max.vm.hosted.HostedBootClassLoader}. The stub class contains references to VM classes, e.g. {@link com.sun.max.vm.value.Value}. It turns out that
 * during the stub class creation, the JDK reflection machinery invokes {@link java.lang.ClassLoader#loadClass(String)} on the {@link com.sun.max.vm.value.Value} class.
 * This call succeeds but has the side effect of marking the boot class loader as an "initiating" loader of {@link com.sun.max.vm.value.Value}.
 * This is a Catch-22 situation. If the load is rejected, the stub class resolution will fail. If the load succeeds the
 * {@link com.sun.max.vm.hosted.HostedBootClassLoader} will be marked an "initiating" loader - it matters not that the defining loader is the system class loader.
 * <p>
 * It also happens that the {@code Value[]} class is resolved after this occurs. Unfortunately, when loading the
 * {@link com.sun.max.vm.value.Value} component class, since the boot class loader is marked as an initiating loader, {@link java.lang.ClassLoader#loadClass(String)}
 * returns the {@link com.sun.max.vm.value.Value} class (defined by the system class loader) immediately instead of throwing {@link java.lang.ClassNotFoundException}.
 * {@link com.sun.max.vm.hosted.HostedBootClassLoader} then goes on to try to define the {@link com.sun.max.vm.value.Value} class in the boot class registry.
 * The solution adopted to catch this is to check for non-JDK (and non-stub) classes
 * in {@link com.sun.max.vm.hosted.HostedClassLoader#extraLoadClassChecks} and throw {@link java.lang.ClassNotFoundException}.
 */
package com.sun.max.vm.hosted;

