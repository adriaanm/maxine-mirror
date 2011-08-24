/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.vm.compiler.adaptive;

import static com.sun.max.vm.MaxineVM.*;
import static com.sun.max.vm.VMOptions.*;
import static com.sun.max.vm.compiler.RuntimeCompiler.*;
import static com.sun.max.vm.compiler.target.Compilations.Attr.*;

import java.util.*;

import com.sun.max.annotate.*;
import com.sun.max.vm.*;
import com.sun.max.vm.MaxineVM.Phase;
import com.sun.max.vm.actor.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.code.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.profile.*;
import com.sun.max.vm.runtime.*;

/**
 * This class implements an adaptive compilation system with multiple compilers with different compilation time / code
 * quality tradeoffs. It encapsulates the necessary infrastructure for recording profiling data, selecting what and when
 * to recompile, etc.
 */
public class AdaptiveCompilationScheme extends AbstractVMScheme implements CompilationScheme {

    /**
     * A constant used to indicated that recompilation is disabled.
     */
    public static final int RECOMPILATION_DISABLED = -1;

    private static final int DEFAULT_RECOMPILATION_THRESHOLD = 5000;

    /**
     * Stores the default threshold at which a recompilation is triggered from the baseline compiler to the next level
     * of optimization. This is typically the number of invocations of the method.
     */
    public static int defaultRecompilationThreshold0 = DEFAULT_RECOMPILATION_THRESHOLD;

    /**
     * A queue of pending compilations.
     */
    protected final LinkedList<Compilation> pending = new LinkedList<Compilation>();

    /**
     * The baseline compiler.
     */
    public final RuntimeCompiler baselineCompiler;

    /**
     * The optimizing compiler.
     */
    public final RuntimeCompiler optimizingCompiler;

    private static boolean baseline;
    private static boolean opt;
    private static int RCT = DEFAULT_RECOMPILATION_THRESHOLD;
    private static boolean GCOnRecompilation;
    private static boolean FailOverCompilation;
    static int PrintCodeCacheMetrics;

    static {
        addFieldOption("-X", "baseline", "Select baseline compiler whenever possible (disables recompilation).");
        addFieldOption("-X", "opt", "Select optimizing-only compilation whenever possible.");
        addFieldOption("-XX:", "RCT", "Set the recompilation threshold for methods.");
        addFieldOption("-XX:", "GCOnRecompilation", "Force GC before every re-compilation.");
        addFieldOption("-XX:", "FailOverCompilation", "Retry failed compilations with another compiler (if available).");
        addFieldOption("-XX:", "PrintCodeCacheMetrics", "Print code cache metrics (0 = disabled, 1 = summary, 2 = verbose).");
    }

    /**
     * An enum that selects between different runtime behavior of the compilation scheme.
     */
    public enum Mode {
        /**
         * Use the baseline compiler whenever possible.
         */
        BASELINE,

        /**
         * Use the optimizing compiler whenever possible.
         */
        OPTIMIZED,

        /**
         * Use both compilers according to dynamic feedback.
         */
        MIXED;
    }

    /**
     * The (dynamically selected) compilation mode.
     */
    private Mode mode = Mode.MIXED;

    private static final boolean BACKGROUND_COMPILATION = false;

    public boolean needsAdapters() {
        return baselineCompiler != optimizingCompiler;
    }

    private static final String OPTIMIZING_COMPILER_PROPERTY = AdaptiveCompilationScheme.class.getSimpleName() + "." + optimizingCompilerOption.getName();
    private static final String BASELINE_COMPILER_PROPERTY = AdaptiveCompilationScheme.class.getSimpleName() + "." + baselineCompilerOption.getName();

    /**
     * Gets the class name of the optimizing compiler that will be configured when an instance of this scheme is instantiated.
     */
    @HOSTED_ONLY
    public static String optName() {
        return configValue(OPTIMIZING_COMPILER_PROPERTY, optimizingCompilerOption, aliases);
    }

    /**
     * Gets the class name of the baseline compiler that will be configured when an instance of this scheme is instantiated.
     */
    @HOSTED_ONLY
    public static String baselineName() {
        return configValue(BASELINE_COMPILER_PROPERTY, baselineCompilerOption, aliases);
    }

    /**
     * The constructor for this class initializes a new adaptive compilation.
     */
    @HOSTED_ONLY
    public AdaptiveCompilationScheme() {
        assert optimizingCompilerOption.getValue() != null;
        String optName = optName();
        String baselineName = baselineName();
        optimizingCompiler = instantiateCompiler(optName);
        if (!optName.equals(baselineName) && baselineName != null) {
            baselineCompiler = instantiateCompiler(baselineName);
        } else {
            baselineCompiler = optimizingCompiler;
        }
        if (!baselineCompiler.supportsInterpreterCompatibility()) {
            optimizingCompiler.deoptimizationNotSupported();
        }
    }

    @HOSTED_ONLY
    private static RuntimeCompiler instantiateCompiler(String name) {
        try {
            return (RuntimeCompiler) Class.forName(name).newInstance();
        } catch (Exception e) {
            throw FatalError.unexpected("Error instantiating compiler " + name, e);
        }
    }

    public String description() {
        return "compilation: " + mode.name().toLowerCase();
    }

    @Override
    public String about() {
        return super.about() + " [opt=" + optimizingCompiler.getClass().getSimpleName() + ", baseline=" + baselineCompiler.getClass().getSimpleName() + "]";
    }

    @Override
    public Properties properties() {
        Properties props = new Properties();
        props.put(OPTIMIZING_COMPILER_PROPERTY, optimizingCompiler.getClass().getName());
        props.put(BASELINE_COMPILER_PROPERTY, baselineCompiler.getClass().getName());
        return props;
    }

    /**
     * This method initializes the adaptive compilation system, either while bootstrapping or
     * at VM startup time. This implementation creates daemon threads to handle asynchronous
     * compilations.
     *
     * @param phase the phase of VM starting up.
     */
    @Override
    public void initialize(MaxineVM.Phase phase) {
        optimizingCompiler.initialize(phase);
        if (optimizingCompiler != baselineCompiler) {
            baselineCompiler.initialize(phase);
        }

        if (isHosted()) {
            if (BACKGROUND_COMPILATION) {
                // launch a compiler thread if background compilation is supported (currently no)
                final CompilationThread compilationThread = new CompilationThread();
                compilationThread.setDaemon(true);
                compilationThread.start();
            }
        } else if (phase == MaxineVM.Phase.STARTING) {
            if (baseline) {
                defaultRecompilationThreshold0 = RECOMPILATION_DISABLED;
                this.mode = Mode.BASELINE;
            } else if (opt) {
                defaultRecompilationThreshold0 = RECOMPILATION_DISABLED;
                this.mode = Mode.OPTIMIZED;
            } else {
                defaultRecompilationThreshold0 = RCT;
                MethodInstrumentation.enable(defaultRecompilationThreshold0);
                this.mode = Mode.MIXED;
            }

            if (BACKGROUND_COMPILATION) {
                // launch a compiler thread if background compilation is supported (currently no)
                final CompilationThread compilationThread = new CompilationThread();
                compilationThread.setDaemon(true);
                compilationThread.start();
            }
        } else if (phase == Phase.RUNNING) {
            if (PrintCodeCacheMetrics != 0) {
                Runtime.getRuntime().addShutdownHook(new Thread("CodeCacheMetricsPrinter") {
                    @Override
                    public void run() {
                        new CodeCacheMetricsPrinter(AdaptiveCompilationScheme.PrintCodeCacheMetrics > 1).printTo(Log.out);
                    }
                });
            }
        }
    }

    /**
     * Performs a compilation of the specified method, waiting for the compilation to finish.
     *
     * @param classMethodActor the method to compile
     * @param flags a mask of {@link Compilations.Attr} values
     * @return the target method that results from compiling the specified method
     */
    public TargetMethod synchronousCompile(ClassMethodActor classMethodActor, int flags) {
        RuntimeCompiler retryCompiler = null;
        while (true) {
            Compilation compilation;
            boolean doCompile = true;
            synchronized (classMethodActor) {
                assert !(classMethodActor.isNative() && classMethodActor.isVmEntryPoint()) : "cannot compile JNI functions that are native";
                Object compiledState = classMethodActor.compiledState;
                compilation = compiledState instanceof Compilation ? (Compilation) compiledState : null;
                if (compilation != null && (flags == 0 || flags == compilation.flags)) {
                    // Only wait for a pending compilation if it compatible with the current request.
                    // That is the current request does not specify a special type of method (flags == 0)
                    // or it specifies the same type of method as the pending compilation (flags == compilation.flags)
                    if (retryCompiler != null) {
                        assert compilation.compilingThread == Thread.currentThread();
                        assert flags == NONE : "cannot retry if specific compilation mode is specified";
                        compilation.compiler = retryCompiler;
                    } else {
                        // the method is currently being compiled, just wait for the result
                        doCompile = false;
                    }
                } else {
                    Compilations prevCompilations = compilation != null ? compilation.prevCompilations :  (Compilations) compiledState;
                    RuntimeCompiler compiler = retryCompiler == null ? selectCompiler(classMethodActor, flags) : retryCompiler;
                    compilation = new Compilation(this, compiler, classMethodActor, prevCompilations, Thread.currentThread(), flags);
                    classMethodActor.compiledState = compilation;
                }
            }

            try {
                if (doCompile) {
                    return compilation.compile();
                }
                return compilation.get();
            } catch (Throwable t) {
                classMethodActor.compiledState = Compilations.EMPTY;
                String errorMessage = "Compilation of " + classMethodActor + " by " + compilation.compiler + " failed";
                if (VMOptions.verboseOption.verboseCompilation) {
                    boolean lockDisabledSafepoints = Log.lock();
                    Log.printCurrentThread(false);
                    Log.print(": ");
                    Log.println(errorMessage);
                    t.printStackTrace(Log.out);
                    Log.unlock(lockDisabledSafepoints);
                }
                if (!FailOverCompilation || retryCompiler != null || (optimizingCompiler == baselineCompiler)) {
                    // This is the final failure: no other compilers available or failover is disabled
                    throw FatalError.unexpected(errorMessage + " (final attempt)", t);
                }
                if (compilation.compiler == optimizingCompiler) {
                    retryCompiler = baselineCompiler;
                } else {
                    retryCompiler = optimizingCompiler;
                }
                if (VMOptions.verboseOption.verboseCompilation) {
                    boolean lockDisabledSafepoints = Log.lock();
                    Log.printCurrentThread(false);
                    Log.println(": Retrying with " + retryCompiler + "...");
                    Log.unlock(lockDisabledSafepoints);
                }
            }
        }
    }

    /**
     * Checks whether there is currently a compilation being performed.
     *
     * @return {@code true} if there is currently a compilation pending or being performed; {@code false} otherwise
     */
    public boolean isCompiling() {
        throw FatalError.unimplemented();
    }

    @HOSTED_ONLY
    public static final HashSet<Class> compileWithBaseline = new HashSet<Class>();

    /**
     * Select the appropriate compiler based on the current state of the method.
     *
     * @param classMethodActor the class method actor to compile
     * @param flags a mask of {@link Compilations.Attr} values
     * @return the compiler that should be used to perform the next compilation of the method
     */
    public RuntimeCompiler selectCompiler(ClassMethodActor classMethodActor, int flags) {

        if (Actor.isUnsafe(classMethodActor.flags() | classMethodActor.compilee().flags())) {
            assert !INTERPRETER_COMPATIBLE.isSet(flags) : "cannot produce interpreter compatible version of " + classMethodActor;
            return optimizingCompiler;
        }

        RuntimeCompiler compiler;

        if (isHosted()) {
            if (compileWithBaseline.contains(classMethodActor.holder().javaClass())) {
                compiler = baselineCompiler;
            } else {
                // at prototyping time, default to the opt compiler
                compiler = optimizingCompiler;
            }
        } else {
            if (INTERPRETER_COMPATIBLE.isSet(flags)) {
                compiler = baselineCompiler;
                assert baselineCompiler.supportsInterpreterCompatibility() : "interpreter compatibility is not supported by the baseline compiler " + baselineCompiler;
            } else if (mode == Mode.OPTIMIZED || OPTIMIZE.isSet(flags)) {
                compiler = optimizingCompiler;
            } else {
                compiler = baselineCompiler;
            }
        }

        return compiler;
    }

    /**
     * This class implements a daemon thread that performs compilations in the background. Depending on the compiler
     * configuration, multiple compilation threads may be working in parallel.
     */
    protected class CompilationThread extends Thread {

        protected CompilationThread() {
            super("compile");
            setDaemon(true);
        }

        /**
         * The current compilation being performed by this thread.
         */
        Compilation compilation;

        /**
         * Continuously polls the compilation queue for work, performing compilations as they are removed from the
         * queue.
         */
        @Override
        public void run() {
            while (true) {
                try {
                    compileOne();
                } catch (InterruptedException e) {
                    // do nothing.
                } catch (Throwable t) {
                    Log.print("Exception during compilation of " + compilation.classMethodActor);
                    t.printStackTrace();
                }
            }
        }

        /**
         * Polls the compilation queue and performs a single compilation.
         *
         * @throws InterruptedException if the thread was interrupted waiting on the queue
         */
        void compileOne() throws InterruptedException {
            compilation = null;
            synchronized (pending) {
                while (compilation == null) {
                    compilation = pending.poll();
                    if (compilation == null) {
                        pending.wait();
                    }
                }
            }
            compilation.compilingThread = Thread.currentThread();
            if (GCOnRecompilation) {
                System.gc();
            }
            compilation.compile();
            compilation = null;
        }
    }
}
