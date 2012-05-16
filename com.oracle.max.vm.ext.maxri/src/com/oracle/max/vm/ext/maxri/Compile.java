/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.vm.ext.maxri;

import java.io.*;
import java.util.*;

import com.oracle.max.asm.*;
import com.sun.cri.ci.*;
import com.sun.max.*;
import com.sun.max.config.*;
import com.sun.max.io.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;
import com.sun.max.program.option.*;
import com.sun.max.test.*;
import com.sun.max.vm.*;
import com.sun.max.vm.MaxineVM.Phase;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.hosted.*;
import com.sun.max.vm.profile.*;
import com.sun.max.vm.reflection.*;
import com.sun.max.vm.type.*;
import com.sun.max.vm.value.*;

/**
 * A harness to run a {@linkplain RuntimeCompiler compiler} offline.
 */
public class Compile {

    private static final OptionSet options = new OptionSet(false);

    private static final Map<String, String> compilerAliases = RuntimeCompiler.aliases;
    private static final String compilerAliasNames = compilerAliases.keySet().toString().replaceAll("[\\[\\]]", "");

    private static final Option<String> compilerOption = options.newStringOption("c", null,
        "The alias of the compiler to use " + compilerAliases.keySet() + " chosen from the following list: " + compilerAliasNames);
    private static final Option<Integer> traceOption = options.newIntegerOption("trace", 0,
        "Set the tracing level of the Maxine VM and runtime.");
    private static final Option<Integer> verboseOption = options.newIntegerOption("verbose", 1,
        "Set the verbosity level of the testing framework.");
    private static final Option<Boolean> reflectionStubsOption = options.newBooleanOption("reflect", false,
        "Generate and compile reflection stubs for the methods.");
    private static final Option<Boolean> failFastOption = options.newBooleanOption("fail-fast", false,
        "Stop compilation upon the first bailout.");
    private static final Option<Boolean> helpOption = options.newBooleanOption("help", false,
        "Show help message and exit.");
    private static final Option<Boolean> profOption = options.newBooleanOption("prof", true,
        "Emit method profiling in baseline compiled methods.");

    static void addFieldOptions(String prefix, String optionsClassName) {
        Class optionsClass = Classes.forName(optionsClassName);
        try {
            Map<String, String> m = null;
            try {
                m = Utils.cast(Classes.getDeclaredMethod(optionsClass, "getHelpMap").invoke(null));
            } catch (NoSuchMethodError e) {
            }
            options.addFieldOptions(optionsClass, prefix, m);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    static {
        // add all the compiler options
        addFieldOptions("G", "com.oracle.max.graal.compiler.GraalOptions");
        addFieldOptions("C1X", "com.sun.c1x.C1XOptions");
        addFieldOptions("T1X", "com.oracle.max.vm.ext.t1x.T1XOptions");

        options.addFieldOptions(AsmOptions.class, "ASM", null);
    }

    private static PrintStream out = System.out;

    private static String[] expandArguments(String[] args) throws IOException {
        List<String> result = new ArrayList<String>(args.length);
        for (String arg : args) {
            if (arg.charAt(0) == '@') {
                File file = new File(arg.substring(1));
                result.addAll(Files.readLines(file));
            } else {
                result.add(arg);
            }
        }
        return result.toArray(new String[result.size()]);
    }

    /**
     * Check alias (short form of compiler name).
     * @param compilerName
     * @return full (aliased) class name or {@code compilerName} if not found
     */
    private static String getCompilerClassname(String compilerName) {
        if (compilerName == null) {
            return null;
        }
        String compilerClassname = compilerAliases.get(compilerName);
        return compilerClassname == null ? compilerName : compilerClassname;
    }

    public static void main(String[] args) throws IOException {

        args = VMOption.extractVMArgs(args);

        VMConfigurator vmConfigurator = new VMConfigurator(options);
        options.parseArguments(args);

        options.setValuesAgain();
        final String[] arguments = expandArguments(options.getArguments());

        if (helpOption.getValue()) {
            options.printHelp(System.out, 80);
            return;
        }
        String compilerName = getCompilerClassname(compilerOption.getValue());
        if (compilerName == null) {
            System.out.println("Must specify compiler to use with the -" + compilerOption + " option");
            System.out.println("Valid values are: " + compilerAliasNames + " or fully qualified class name");
            return;
        }

        if (compilerName.contains("T1X")) {
            RuntimeCompiler.baselineCompilerOption.setValue(compilerName);
        } else {
            RuntimeCompiler.optimizingCompilerOption.setValue(compilerName);
        }

        Trace.on(traceOption.getValue());

        if (profOption.getValue()) {
            MethodInstrumentation.enable(500);
        }

        vmConfigurator.create();

        // create the prototype
        if (verboseOption.getValue() > 0) {
            out.print("Initializing Java prototype... ");
        }
        JavaPrototype.initialize(false);
        if (verboseOption.getValue() > 0) {
            out.println("done");
        }

        CompilationBroker cb = MaxineVM.vm().compilationBroker;
        final RuntimeCompiler compiler = compilerName.contains("T1X") ? cb.baselineCompiler : cb.optimizingCompiler;
        cb.optimizingCompiler.initialize(Phase.HOSTED_COMPILING);
        if (cb.optimizingCompiler != cb.baselineCompiler && compiler == cb.baselineCompiler) {
            cb.baselineCompiler.initialize(Phase.HOSTED_COMPILING);
        }

        final Classpath classpath = Classpath.fromSystem();
//        final List<MethodActor> methods = new MyMethodFinder().find(arguments, classpath, HostedVMClassLoader.HOSTED_VM_CLASS_LOADER, null);
        final List<MethodActor> methods = new MyMethodFinder().find(arguments, classpath, Compile.class.getClassLoader(), null);
        final ProgressPrinter progress = new ProgressPrinter(out, methods.size(), verboseOption.getValue(), false);

        if (reflectionStubsOption.getValue()) {
            addReflectionStubs(methods);
        }

        doCompile(compiler, methods, progress);

        if (verboseOption.getValue() > 0) {
            progress.report();
        }

        compiler.initialize(Phase.TERMINATING);

        // Non-zero exit code indicates number of failures
        System.exit(progress.failed());
    }

    protected static void addReflectionStubs(final List<MethodActor> methods) {
        ArrayList<MethodActor> stubMethods = new ArrayList<MethodActor>();
        for (MethodActor m : methods) {
            try {
                if (!m.isInstanceInitializer()) {
                    InvocationStub javaStub = InvocationStub.newMethodStub(m.toJava(), Boxing.JAVA);
                    InvocationStub valueStub = InvocationStub.newMethodStub(m.toJava(), Boxing.VALUE);
                    stubMethods.add(ClassRegistry.findMethod(javaStub.getClass(), "invoke", Object.class, Object[].class));
                    stubMethods.add(ClassRegistry.findMethod(valueStub.getClass(), "invoke", Value[].class));
                } else {
                    InvocationStub javaStub = InvocationStub.newConstructorStub(m.toJavaConstructor(), null, Boxing.JAVA);
                    InvocationStub valueStub = InvocationStub.newConstructorStub(m.toJavaConstructor(), null, Boxing.VALUE);
                    stubMethods.add(ClassRegistry.findMethod(javaStub.getClass(), "newInstance", Object[].class));
                    stubMethods.add(ClassRegistry.findMethod(valueStub.getClass(), "newInstance", Value[].class));
                }
            } catch (Throwable e) {
                out.println("Error adding reflectionstubs for " + m + ": " + e);
            }
        }
        methods.addAll(stubMethods);
    }

    private static void doCompile(RuntimeCompiler compiler, List<MethodActor> methods, ProgressPrinter progress) {
        // compile all the methods and report progress
        for (MethodActor methodActor : methods) {
            progress.begin(methodActor.toString());
            Throwable error = compile(compiler, methodActor, true);
            if (error == null) {
                progress.pass();
            } else {
                progress.fail(error.toString());
                if (failFastOption.getValue()) {
                    out.println("");
                    break;
                }
            }
        }
    }

    private static Throwable compile(RuntimeCompiler compiler, MethodActor method, boolean printBailout) {
        // compile a single method
        ClassMethodActor classMethodActor = (ClassMethodActor) method;
        Throwable thrown = null;
        CiStatistics stats = new CiStatistics();
        try {
            compiler.compile(classMethodActor, false, true, stats);
        } catch (Throwable t) {
            thrown = t;
        }
        if (printBailout && thrown != null) {
            out.println("");
            out.println(method);
            thrown.printStackTrace();
        }

        return thrown;
    }

    static class MyMethodFinder extends MethodFinder {
        HashSet<PatternMatcher> patterns;
        @Override
        protected void addClassToProcess(PatternMatcher classNamePattern, String className, List<String> matchingClasses) {
            boolean added = patterns.add(classNamePattern);
            if (added && verboseOption.getValue() > 0) {
                out.print("Classes " + classNamePattern.type + " '" + classNamePattern.pattern + "'... ");
            }
            super.addClassToProcess(classNamePattern, className, matchingClasses);
        }

        @Override
        public List<MethodActor> find(String[] patterns, Classpath classpath, ClassLoader classLoader, List<Throwable> nonFatalErrors) {
            this.patterns = new HashSet<PatternMatcher>();
            return super.find(patterns, classpath, classLoader, nonFatalErrors);
        }

        private static boolean isCompilable(MethodActor method) {
            return method instanceof ClassMethodActor && !method.isAbstract() && !method.isIntrinsic();
        }

        @Override
        protected void addMethod(MethodActor method, List<MethodActor> methods) {

            if (isCompilable(method)) {
                super.addMethod(method, methods);
                if ((methods.size() % 1000) == 0 && verboseOption.getValue() >= 1) {
                    out.print('.');
                }
            }
        }

        @Override
        protected ClassActor getClassActor(Class< ? > javaClass) {
            ClassActor classActor = null;
            try {
                classActor = ClassActor.fromJava(javaClass);

                BootImagePackage bootImagePackage = BootImagePackage.fromClass(javaClass);
                if (bootImagePackage != null && bootImagePackage.name().contains(".prototype")) {
                    return null;
                }
            } catch (Throwable t) {
                // do nothing.
            }
            return classActor;
        }
    }
}
