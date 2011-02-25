/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.vm.compiler.target;

import static com.sun.max.vm.VMOptions.*;

import java.util.*;
import java.util.concurrent.*;

import com.sun.max.annotate.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.compiler.CompilationScheme.Inspect;
import com.sun.max.vm.heap.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.stack.*;
import com.sun.max.vm.thread.*;

/**
 * This class represents an ongoing or completed compilation.
 *
 * @author Ben L. Titzer
 */
public class Compilation implements Future<TargetMethod> {

    /**
     * Used to detect re-entrant compilation which indicates the boot image closure was not incomplete.
     */
    private static final ObjectThreadLocal<Compilation> COMPILATION = new ObjectThreadLocal<Compilation>("COMPILATION", "current compilation");

    private static boolean GCOnCompilation;
    private static String GCOnCompilationOf;
    static {
        VMOptions.addFieldOption("-XX:", "GCOnCompilation", Compilation.class, "Perform a GC before every compilation.");
        VMOptions.addFieldOption("-XX:", "GCOnCompilationOf", Compilation.class, "Perform a GC before every compilation of a method whose fully qualified name contains <value>.");
    }

    public static final VMBooleanXXOption TIME_COMPILATION = register(new VMBooleanXXOption("-XX:-TimeCompilation",
        "Report time spent in compilation.") {
        @Override
        protected void beforeExit() {
            if (getValue()) {
                Log.print("Time spent in compilation: ");
                Log.print(compilationTime);
                Log.println("ms");
            }

        }
    }, MaxineVM.Phase.STARTING);

    @RESET
    private static long compilationTime;

    public final CompilationScheme compilationScheme;
    public final RuntimeCompiler compiler;
    public final ClassMethodActor classMethodActor;
    public final Compilation parent;
    @INSPECTED
    public final Object previousTargetState;
    public Thread compilingThread;
    public TargetMethod result;

    /**
     * State of this compilation. If {@code true}, then this compilation has finished and the target
     * method is available.
     */
    public boolean done;

    public Compilation(CompilationScheme compilationScheme,
                       RuntimeCompiler compiler,
                       ClassMethodActor classMethodActor,
                       Object previousTargetState, Thread compilingThread) {
        this.parent = COMPILATION.get();
        this.compilationScheme = compilationScheme;
        this.compiler = compiler;
        this.classMethodActor = classMethodActor;
        this.previousTargetState = previousTargetState;
        this.compilingThread = compilingThread;

        for (Compilation scope = parent; scope != null; scope = scope.parent) {
            if (scope.classMethodActor.equals(classMethodActor)) {
                FatalError.unexpected("Recursive compilation of " + classMethodActor);
            }
        }
        COMPILATION.set(this);
    }

    /**
     * Cancel this compilation. Ignored.
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    /**
     * Checks whether this compilation was canceled. This method always returns {@code false}.
     */
    public boolean isCancelled() {
        return false;
    }

    /**
     * Returns whether this compilation is done.
     */
    public boolean isDone() {
        synchronized (classMethodActor) {
            return done;
        }
    }

    /**
     * Gets the result of this compilation, blocking if necessary.
     *
     * @return the target method that resulted from this compilation
     */
    public TargetMethod get() throws InterruptedException {
        synchronized (classMethodActor) {
            if (!done) {
                if (compilingThread == Thread.currentThread()) {
                    throw new RuntimeException("Compilation of " + classMethodActor.format("%H.%n(%p)") + " is recursive, current compilation scheme: " + this.compilationScheme);
                }

                // the class method actor is used here as the condition variable
                classMethodActor.wait();
            }
            return classMethodActor.currentTargetMethod();
        }
    }

    /**
     * Gets the result of this compilation, blocking for a maximum amount of time.
     *
     * @return the target method that resulted from this compilation
     */
    public TargetMethod get(long timeout, TimeUnit unit) throws InterruptedException {
        synchronized (classMethodActor) {
            if (!done) {
                // the class method actor is used here as the condition variable
                classMethodActor.wait(timeout); // TODO: convert timeout to milliseconds
            }
            return classMethodActor.currentTargetMethod();
        }
    }

    /**
     * Perform the compilation, notifying the specified observers.
     *
     * @param observers a list of observers to notify; {@code null} if there are no observers
     * to notify
     * @return the target method that is the result of the compilation
     */
    public TargetMethod compile(List<CompilationObserver> observers) {
        RuntimeCompiler compiler = this.compiler;
        TargetMethod targetMethod = null;

        // notify any compilation observers
        observeBeforeCompilation(observers, compiler);

        Throwable error = null;
        String methodString = "";
        try {

            Inspect.notifyCompilationStart(classMethodActor);

            methodString = logBeforeCompilation(compiler);
            if (!MaxineVM.isHosted()) {
                StackReferenceMapPreparer.verifyReferenceMapsForThisThread();
            }

            gcIfRequested(classMethodActor, methodString);

            long startCompile = 0;
            if (TIME_COMPILATION.getValue()) {
                startCompile = System.currentTimeMillis();
            }

            // attempt the compilation
            targetMethod = compiler.compile(classMethodActor);

            if (targetMethod == null) {
                throw new InternalError(classMethodActor.format("Result of compiling of %H.%n(%p) is null"));
            }
            if (startCompile != 0) {
                compilationTime += System.currentTimeMillis() - startCompile;
            }

            logAfterCompilation(compiler, targetMethod, methodString);
        } catch (RuntimeException t) {
            error = t;
        } catch (Error t) {
            error = t;
        } finally {
            // invariant: (targetMethod != null) != (error != null)
            synchronized (classMethodActor) {
                // update the target state of the class method actor
                // assert classMethodActor.targetState == this;
                if (targetMethod != null) {
                    // compilation succeeded and produced a target method
                    classMethodActor.targetState = TargetState.addTargetMethod(targetMethod, previousTargetState);
                }
                // compilation finished: this must come after the assignment to classMethodActor.targetState
                done = true;

                // notify any waiters on this compilation
                classMethodActor.notifyAll();
            }

            COMPILATION.set(parent);

            // notify any compilation observers
            observeAfterCompilation(observers, compiler, targetMethod);
        }

        if (error != null) {
            // an error occurred
            logCompilationError(error, compiler, methodString);
        } else if (targetMethod == null) {
            // the compilation didn't produce a target method
            FatalError.unexpected("target method should not be null");
        }

        return targetMethod;
    }

    /**
     * Invokes a garbage collection if the {@link #GCOnCompilation} or
     * {@link #GCOnCompilationOf} options imply one is requested for
     * the compilation of a given method.
     *
     * @param method the method about to be compiled
     * @param methodString the value of {@code method.format("%H.%n(%p)} if it has been pre-computed, {@code null} otherwise
     */
    private void gcIfRequested(ClassMethodActor method, String methodString) {
        if (Heap.isInitialized()) {
            if (GCOnCompilation) {
                System.gc();
            } else if (GCOnCompilationOf != null) {
                if (methodString == null) {
                    methodString = method.format("%H.%n(%p)");
                }
                if (methodString.contains(GCOnCompilationOf)) {
                    System.gc();
                }
            }
        }
    }

    private void logCompilationError(Throwable error, RuntimeCompiler compiler, String methodString) {
        if (verboseOption.verboseCompilation) {
            Log.printCurrentThread(false);
            Log.print(": ");
            Log.print(compiler.getClass().getSimpleName());
            Log.print(": Failed ");
            Log.println(methodString);
        }
        if (error instanceof Error) {
            throw (Error) error;
        }
        throw (RuntimeException) error;
    }

    private String logBeforeCompilation(RuntimeCompiler compiler) {
        String methodString = null;
        if (verboseOption.verboseCompilation) {
            methodString = classMethodActor.format("%H.%n(%p)");
            Log.printCurrentThread(false);
            Log.print(": ");
            Log.print(compiler.getClass().getSimpleName());
            Log.print(": Compiling ");
            Log.println(methodString);
        }
        return methodString;
    }

    private void logAfterCompilation(RuntimeCompiler compiler, TargetMethod targetMethod, String methodString) {
        if (verboseOption.verboseCompilation) {
            Log.printCurrentThread(false);
            Log.print(": ");
            Log.print(compiler.getClass().getSimpleName());
            Log.print(": Compiled  ");
            Log.print(methodString);
            Log.print(" @ ");
            Log.print(targetMethod.codeStart());
            Log.print(", size = ");
            Log.print(targetMethod.codeLength());
            Log.println();
        }
    }

    private void observeBeforeCompilation(List<CompilationObserver> observers, RuntimeCompiler compiler) {
        if (observers != null) {
            for (CompilationObserver observer : observers) {
                observer.observeBeforeCompilation(classMethodActor, compiler);
            }
        }
    }
    private void observeAfterCompilation(List<CompilationObserver> observers, RuntimeCompiler compiler, TargetMethod result) {
        if (observers != null) {
            for (CompilationObserver observer : observers) {
                observer.observeAfterCompilation(classMethodActor, compiler, result);
            }
        }
    }
}
