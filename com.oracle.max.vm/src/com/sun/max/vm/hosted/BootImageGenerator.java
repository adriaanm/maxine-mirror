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
package com.sun.max.vm.hosted;

import java.io.*;
import java.util.*;

import com.sun.max.*;
import com.sun.max.ide.*;
import com.sun.max.lang.*;
import com.sun.max.profile.*;
import com.sun.max.program.*;
import com.sun.max.program.option.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.classfile.*;
import com.sun.max.vm.classfile.constant.*;
import com.sun.max.vm.code.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.compiler.deps.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.heap.*;
import com.sun.max.vm.jdk.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.type.*;
import com.sun.max.vm.verifier.*;

/**
 * Construction of a virtual machine image begins here by running on a host virtual
 * machine (e.g. Hotspot). This process involves creating a target VM configuration
 * and loading and initializing the classes that implement VM services. The representation
 * of the virtual machine being built is referred to as the "prototype", and the final
 * product, a binary image that contains the compiled machine code of the virtual machine
 * as well as objects and metadata that implement the virtual machine, is called the
 * "image".
 */
public final class BootImageGenerator {

    public static final String IMAGE_OBJECT_TREE_FILE_NAME = "maxine.object.tree";
    public static final String IMAGE_METHOD_TREE_FILE_NAME = "maxine.method.tree";
    public static final String IMAGE_JAR_FILE_NAME = "maxine.jar";
    public static final String IMAGE_FILE_NAME = "maxine.vm";
    public static final String STATS_FILE_NAME = "maxine.stats";
    public static final String DEPS_FILE_NAME = "maxine.deps";

    public static final String DEFAULT_VM_DIRECTORY = Prototype.TARGET_GENERATED_ROOT;

    private static final OptionSet options = new OptionSet();

    private static final Option<Boolean> help = options.newBooleanOption("help", false,
            "Show help message and exit.");

    private static final Option<Boolean> treeOption = options.newBooleanOption("tree", false,
            "Create a file showing the connectivity of objects in the image.");

    private static final Option<Boolean> statsOption = options.newBooleanOption("stats", false,
            "Create a file detailing the number and size of each type of object in the image.");

    private static final Option<Boolean> testNative = options.newBooleanOption("native-tests", false,
            "For the Java tester, this option specifies that " + System.mapLibraryName("javatest") + " should be dynamically loaded.");

    private static final Option<String> compilationBrokerClassOption = options.newStringOption("compilationBrokerClass", null,
            "The CompilationBroker subclass to use.");

    private static final Option<Boolean> debugClassIDOption = options.newBooleanOption("debug-classid", false,
            "Trace array class id creation and prints reserved class id without array class actors.");

    // TODO: clean this up. Just for getting perf numbers.
    private static final Option<Boolean> inlinedTLABOption = options.newBooleanOption("inline-tlabs", true,
            "Generate inline TLAB allocation code in boot image.");
    // TODO: clean this up. Just for getting perf numbers.
    private static final Option<Boolean> useOutOfLineStubs = options.newBooleanOption("out-stubs", true,
                    "Uses out of line runtime stubs when generating inlined TLAB allocations with XIR");

    // Options shared with the Inspector

    public static final OptionSet inspectorSharedOptions = new OptionSet();

    /**
     * Partial override of the location of the target VM and associated files, relative to a new workspace root.
     * Abstracts project names, OS and platform details.
     */
    private static final Option<String> targetWSRootDirectoryOption = inspectorSharedOptions.newStringOption("target-ws-root", null,
            "Alternate workspace root for output of the binary image generator.");

    /**
     * A complete override of the location of the target VM and associated files. Must be absolutely specific
     * about location, i.e., project names, OS and platform.
     */
    private static final Option<File> vmDirectoryOption = inspectorSharedOptions.newFileOption("vmdir", getDefaultVMDirectory(),
            "The output directory for the binary image generator.");

    public static final Option<Boolean> checkGeneratedCodeOption = inspectorSharedOptions.newBooleanOption("checkautogen", true,
            "Check the automically generated code for consistency");

    static {
        options.addOptions(inspectorSharedOptions);
    }

    public static boolean nativeTests;

    /**
     * Gets the default VM directory where the VM executable, shared libraries, boot image
     * and related files are located.
     */
    public static File getDefaultVMDirectory() {
        return getDefaultVMDirectory(false);
    }

    /**
     * Gets the default VM directory where the VM executable, shared libraries, boot image
     * and related files are located, with optional override.
     */
    public static File getDefaultVMDirectory(boolean checkOptions) {
        File wsRoot = JavaProject.findWorkspace();
        if (checkOptions) {
            if (vmDirectoryOption.isAssigned()) {
                return vmDirectoryOption.getValue();
            } else {
                String path = targetWSRootDirectoryOption.getValue();
                if (path != null) {
                    wsRoot = new File(path);
                }
            }
        }
        return new File(wsRoot, DEFAULT_VM_DIRECTORY);
    }

    /**
     * Gets the boot image file given a VM directory.
     *
     * @param vmdir a VM directory. If {@code null}, then {@link #getDefaultVMDirectory()} is used.
     */
    public static File getBootImageFile(File vmdir) {
        if (vmdir == null) {
            vmdir = getDefaultVMDirectory();
        }
        return new File(vmdir, IMAGE_FILE_NAME);
    }

    /**
     * Gets the boot image jar file given a VM directory.
     *
     * @param vmdir a VM directory. If {@code null}, then {@link #getDefaultVMDirectory()} is used.
     */
    public static File getBootImageJarFile(File vmdir) {
        if (vmdir == null) {
            vmdir = getDefaultVMDirectory();
        }
        return new File(vmdir, IMAGE_JAR_FILE_NAME);
    }

    /**
     * Gets the object tree file given a VM directory.
     *
     * @param vmdir a VM directory. If {@code null}, then {@link #getDefaultVMDirectory()} is used.
     */
    public static File getBootImageObjectTreeFile(File vmdir) {
        if (vmdir == null) {
            vmdir = getDefaultVMDirectory();
        }
        return new File(vmdir, IMAGE_OBJECT_TREE_FILE_NAME);
    }

    /**
     * Gets the method tree file given a VM directory.
     *
     * @param vmdir a VM directory. If {@code null}, then {@link #getDefaultVMDirectory()} is used.
     */
    public static File getBootImageMethodTreeFile(File vmdir) {
        if (vmdir == null) {
            vmdir = getDefaultVMDirectory();
        }
        return new File(vmdir, IMAGE_METHOD_TREE_FILE_NAME);
    }

    /**
     * Creates and runs the binary image generator with the specified command line arguments.
     *
     * @param programArguments the arguments from the command line
     */
    public BootImageGenerator(String[] programArguments) {
        final long start = System.currentTimeMillis();
        try {
            VMConfigurator configurator = new VMConfigurator(options);
            PrototypeGenerator prototypeGenerator = new PrototypeGenerator(options);
            Trace.addTo(options);

            options.addOptions(RuntimeCompiler.compilers);

            programArguments = VMOption.extractVMArgs(programArguments);
            options.parseArguments(programArguments);

            if (help.getValue()) {
                options.printHelp(System.out, 80);
                return;
            }

            if (compilationBrokerClassOption.getValue() != null) {
                System.setProperty(CompilationBroker.COMPILATION_BROKER_CLASS_PROPERTY_NAME, compilationBrokerClassOption.getValue());
            }

            ClassID.traceArrayClassIDs = debugClassIDOption.getValue();

            String[] extraClassesAndPackages = options.getArguments();
            if (extraClassesAndPackages.length != 0) {
                System.setProperty(JavaPrototype.EXTRA_CLASSES_AND_PACKAGES_PROPERTY_NAME, Utils.toString(extraClassesAndPackages, " "));
            }

            enableProxyClassFileDumping();

            nativeTests = testNative.getValue();

            final File vmDirectory = getDefaultVMDirectory(true);
            vmDirectory.mkdirs();

            // Create and installs the VM
            configurator.create();

            // Initialize the Java prototype
            JavaPrototype.initialize(prototypeGenerator.threadsOption.getValue(),  checkGeneratedCodeOption.getValue());

            Heap.genInlinedTLAB = inlinedTLABOption.getValue(); // TODO: cleanup. Just for evaluating impact on performance of inlined tlab alloc.
            Heap.useOutOfLineStubs = useOutOfLineStubs.getValue(); // TODO: cleanup.

            DataPrototype dataPrototype = prototypeGenerator.createDataPrototype(treeOption.getValue());

            final GraphPrototype graphPrototype = dataPrototype.graphPrototype();

            VMOptions.beforeExit();

            // write the statistics
            if (statsOption.getValue()) {
                writeStats(graphPrototype, new File(vmDirectory, STATS_FILE_NAME));
            }

            if (DependenciesManager.dependenciesLogger.traceEnabled()) {
                DependenciesStats.dump(new PrintStream(new File(vmDirectory, DEPS_FILE_NAME)));
            }

            // ClassID debugging
            ClassID.validateUsedClassIds();

            writeJar(new File(vmDirectory, IMAGE_JAR_FILE_NAME));
            writeImage(dataPrototype, new File(vmDirectory, IMAGE_FILE_NAME));
            verifyBootClasses();
            if (treeOption.getValue()) {
                // write the tree file only if specified by the user.
                writeObjectTree(dataPrototype, graphPrototype, new File(vmDirectory, IMAGE_OBJECT_TREE_FILE_NAME));
                writeMethodTree(graphPrototype.compiledPrototype, new File(vmDirectory, IMAGE_METHOD_TREE_FILE_NAME));
            }
            if (statsOption.getValue()) {
                writeMiscStatistics(Trace.stream());
            }
        } catch (IOException ioException) {
            throw ProgramError.unexpected("could not write file ", ioException);
        } finally {
            final long timeInMilliseconds = System.currentTimeMillis() - start;
            Trace.line(1, "Total time: " + (timeInMilliseconds / 1000.0f) + " seconds");
            System.out.flush();
        }
    }

    /**
     * This is an array of directory names relative to the current working directory (system property "user.dir")
     * to which proxies will be dumped. These directories are created before boot image construction, and deleted
     * afterwards, if they had not existed beforehand. Note that all directories of a path have to be given.
     */
    private static String[] proxyDirs = new String[] {
        "java",
        "java/lang",
        "java/lang/invoke",
    };

    /**
     * Add the current working directory to the class path that will be created when HostedBootClassLoader.classpath() is
     * first called. This is the directory where ProxyGenerator dumps out the class files it generates.
     */
    private static void enableProxyClassFileDumping() {
        final String cwd = System.getProperty("user.dir");
        String javaClassPath = System.getProperty("java.class.path");
        System.setProperty("java.class.path", cwd + File.pathSeparatorChar + javaClassPath);
        final File cwdFile = new File(cwd);

        // See sun.misc.ProxyGenerator.saveGeneratedFiles field
        System.setProperty("sun.misc.ProxyGenerator.saveGeneratedFiles", "true");
        final Set<File> existingProxyClassFilesAndDirectories = collectProxyClassFilesAndDirectories(cwdFile);

        // create proxy directories
        for (String d : proxyDirs) {
            File proxyDir = new File(cwdFile, d);
            if (!proxyDir.exists()) {
                proxyDir.mkdirs();
                proxyDir.deleteOnExit();
            }
        }

        Runtime.getRuntime().addShutdownHook(new Thread("RemovingProxyClassFilesAndDirectories") {
            @Override
            public void run() {
                System.setProperty("sun.misc.ProxyGenerator.saveGeneratedFiles", "false");
                for (File f : collectProxyClassFilesAndDirectories(cwdFile)) {
                    if (!existingProxyClassFilesAndDirectories.contains(f)) {
                        f.delete();
                    }
                }
            }
        });
    }

    private static Set<File> collectProxyClassFilesAndDirectories(File cwd) {
        final FilenameFilter proxyFilter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.startsWith(JDK_java_lang_reflect_Proxy.proxyClassNamePrefix) && name.endsWith(".class");
            }
        };

        // proxy files without package name
        Set<File> filesAndDirs = new HashSet<File>(Arrays.asList(cwd.listFiles(proxyFilter)));

        // recurse into given directories and identify proxy files there
        for (String d : proxyDirs) {
            File proxyDir = new File(cwd, d);
            if (proxyDir.exists()) {
                filesAndDirs.add(proxyDir);
                filesAndDirs.addAll(Arrays.asList(proxyDir.listFiles(proxyFilter)));
            }
        }

        return filesAndDirs;
    }

    /**
     * Writes the image data to the specified file.
     *
     * @param dataPrototype the data prototype containing a data-level representation of the image
     * @param file the file to which to write the data prototype
     */
    private void writeImage(DataPrototype dataPrototype, File file) {
        try {
            final FileOutputStream outputStream = new FileOutputStream(file);
            final BootImage bootImage = new BootImage(dataPrototype);
            try {
                Trace.begin(1, "writing boot image file: " + file);
                bootImage.write(outputStream);
                Trace.end(1, "end boot image file: " + file + " (" + Longs.toUnitsString(file.length(), false) + ")");
            } catch (IOException ioException) {
                throw ProgramError.unexpected("could not write file: " + file, ioException);
            } finally {
                try {
                    outputStream.close();
                } catch (IOException ioException) {
                    ProgramWarning.message("could not close file: " + file);
                }
            }
        } catch (FileNotFoundException fileNotFoundException) {
            throw ProgramError.unexpected("could not open file: " + file);
        } catch (BootImageException bootImageException) {
            throw ProgramError.unexpected("could not construct proper boot image", bootImageException);
        }
    }

    /**
     * Writes a jar file containing all of the (potentially rewritten) VM class files to the specified file.
     *
     * @param file the file to which to write the jar
     * @throws IOException if there is a problem writing the jar
     */
    private void writeJar(File file) throws IOException {
        Trace.begin(1, "writing boot image jar file: " + file);
        ClassfileReader.writeClassfilesToJar(file);
        Trace.end(1, "end boot image jar file: " + file + " (" + Longs.toUnitsString(file.length(), false) + ")");
    }

    private void verifyBootClasses() {
        Trace.begin(1, "verifying boot image classes");
        // The verification process may loaded extra classes which may in turn require generation of
        // more vtrampoline which in turn requires the ability to allocate code. Since the boot image
        // has already been written, resetting the boot code region is now safe.
        Code.resetBootCodeRegion();
        long start = System.currentTimeMillis();
        for (ClassActor ca : ClassRegistry.allBootImageClasses()) {
            if (!ca.kind.isWord) {
                Verifier.verifierFor(ca).verify();
            }
        }
        Trace.end(1, "verifying boot image classes", start);
    }

    /**
     * Writes miscellaneous statistics about the boot image creation process to a file.
     *
     * @param graphPrototype the graph (i.e. nodes/edges) representation of the prototype
     * @param file the file to which to write the statistics
     * @throws IOException if there is a problem writing to the file
     */
    private void writeStats(GraphPrototype graphPrototype, File file) throws IOException {
        Trace.begin(1, "writing boot image statistics file: " + file);
        final FileOutputStream fileOutputStream = new FileOutputStream(file);
        GraphStats stats = new GraphStats(graphPrototype);
        stats.dumpStats(new PrintStream(fileOutputStream));
        fileOutputStream.close();
        Trace.end(1, "end boot image statistics file: " + file + " (" + Longs.toUnitsString(file.length(), false) + ")");
        new SavingsEstimator(stats).report(Trace.stream());
    }

    /**
     * Writes the object tree to a file. The object tree helps to diagnose space usage problems,
     * typically caused by including too much into the image.
     *
     * @param dataPrototype the data representation of the prototype
     * @param graphPrototype the graph representation of the prototype
     * @param file the file to which to write the object
     * @throws IOException
     */
    private void writeObjectTree(DataPrototype dataPrototype, GraphPrototype graphPrototype, File file) throws IOException {
        Trace.begin(1, "writing boot image object tree file: " + file);
        final FileOutputStream fileOutputStream = new FileOutputStream(file);
        final DataOutputStream dataOutputStream = new DataOutputStream(new BufferedOutputStream(fileOutputStream, 1000000));
        BootImageObjectTree.saveTree(dataOutputStream, graphPrototype.links(), dataPrototype.allocationMap());
        dataOutputStream.flush();
        fileOutputStream.close();
        Trace.end(1, "writing boot image object tree file: " + file + " (" + Longs.toUnitsString(file.length(), false) + ")");
    }

    /**
     * Writes the method tree to a file. The method tree helps to diagnose the inclusion
     * of methods into the image that should not be included.
     *
     * @param compiledPrototype the compiled-code representation of the prototype
     * @param file the file to which to write the tree
     * @throws IOException if there is a problem writing to the file
     */
    private void writeMethodTree(CompiledPrototype compiledPrototype, File file) throws IOException {
        Trace.begin(1, "writing boot image method tree file: " + file);
        final FileOutputStream fileOutputStream = new FileOutputStream(file);
        final DataOutputStream dataOutputStream = new DataOutputStream(new BufferedOutputStream(fileOutputStream, 1000000));
        BootImageMethodTree.saveTree(dataOutputStream, compiledPrototype.links());
        dataOutputStream.flush();
        fileOutputStream.close();
        Trace.end(1, "writing boot image method tree file: " + file + " (" + Longs.toUnitsString(file.length(), false) + ")");
    }

    /**
     * The main entry point, which creates a binary image generator and then runs it.
     *
     * @param programArguments the arguments from the command line
     */
    public static void main(String[] programArguments) {
        new BootImageGenerator(programArguments);
    }

    /**
     * Writes various statistics about the image creation process to the standard output.
     * @param out the output stream to which to write the statistics
     */
    private static void writeMiscStatistics(PrintStream out) {
        Log.println();
        Log.println("==== All Loaded Classes ====");
        TreeSet<String> names = new TreeSet<String>();
        for (ClassActor classActor : ClassRegistry.allBootImageClasses()) {
            if (classActor.isInstanceClass() && !classActor.isReflectionStub()) {
                names.add(classActor.toString());
            }
        }
        for (String name : names) {
            Log.println(name);
        }
        int numClasses = names.size();
        Log.println();

        Log.println();
        Log.println("==== All Compiled Method ====");
        names.clear();
        for (TargetMethod m : Code.bootCodeRegion().copyOfTargetMethods()) {
            if (m.stubType() == null) {
                String name = m.toString();
                names.add(name);
            }
        }
        for (String name : names) {
            Log.println(name);
        }
        int numMethods = names.size();

        Log.println();
        Log.println("==== Unresolved Calls and Fields in Target Methods ====");
        TreeMap<String, TreeSet<String>> elements = new TreeMap<String, TreeSet<String>>();
        for (TargetMethod targetMethod : Code.bootCodeRegion().copyOfTargetMethods()) {
            if (targetMethod.referenceLiterals() != null) {
                for (Object o : targetMethod.referenceLiterals()) {
                    if (o instanceof ResolutionGuard) {
                        String method = targetMethod.classMethodActor().toString();
                        String element = o.toString();
                        if (!elements.containsKey(method)) {
                            elements.put(method, new TreeSet<String>());
                        }
                        elements.get(method).add(element);
                    }
                }
            }
        }
        int numUnresolved = 0;
        for (Map.Entry<String, TreeSet<String>> entry : elements.entrySet()) {
            Log.println(entry.getKey());
            numUnresolved += entry.getValue().size();
            for (String value : entry.getValue()) {
                Log.println("    " + value);
            }
        }
        Log.println();

        Log.println();
        Log.println("==== Unlinked Direct Calls in Target Methods ====");
        elements.clear();
        for (TargetMethod targetMethod : Code.bootCodeRegion().copyOfTargetMethods()) {
            if (!(targetMethod instanceof Adapter)) {
                final Object[] directCallees = targetMethod.directCallees();
                if (directCallees != null) {
                    for (Object directCallee : directCallees) {
                        TargetMethod callee = targetMethod.getTargetMethod(directCallee);
                        if (directCallee != null && callee == null) {
                            String method = targetMethod.classMethodActor().toString();
                            String element = directCallee.toString();
                            if (!elements.containsKey(method)) {
                                elements.put(method, new TreeSet<String>());
                            }
                            elements.get(method).add(element);
                        }
                    }
                }
            }
        }
        int numUnlinked = 0;
        for (Map.Entry<String, TreeSet<String>> entry : elements.entrySet()) {
            Log.println(entry.getKey());
            numUnlinked += entry.getValue().size();
            for (String value : entry.getValue()) {
                Log.println("    " + value);
            }
        }
        Log.println();

        Log.println();
        Log.println("# Classes:    " + numClasses);
        Log.println("# Compiled:   " + numMethods);
        Log.println("# Unresolved: " + numUnresolved);
        Log.println("# Unlinked:   " + numUnlinked);
        Log.println();

        Trace.line(1, "# utf8 constants: " + SymbolTable.length());
        Trace.line(1, "# type descriptors: " + TypeDescriptor.numberOfDescriptors());
        Trace.line(1, "# signature descriptors: " + SignatureDescriptor.totalNumberOfDescriptors());

        GlobalMetrics.report(out);
    }
}
