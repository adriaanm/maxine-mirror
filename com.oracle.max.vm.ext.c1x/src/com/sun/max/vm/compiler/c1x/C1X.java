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
package com.sun.max.vm.compiler.c1x;

import static com.sun.max.platform.Platform.*;
import static com.sun.max.vm.MaxineVM.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.max.asm.*;
import com.oracle.max.vm.ext.maxri.*;
import com.oracle.max.vm.ext.maxri.MaxXirGenerator.*;
import com.sun.c1x.*;
import com.sun.c1x.graph.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;
import com.sun.cri.xir.*;
import com.sun.max.annotate.*;
import com.sun.max.platform.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.MaxineVM.Phase;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.compiler.deopt.*;
import com.sun.max.vm.compiler.deps.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.thread.*;
import com.sun.max.vm.type.*;

/**
 * Integration of the C1X compiler into Maxine's compilation framework.
 */
public class C1X implements RuntimeCompiler {

    /**
     * The Maxine specific implementation of the {@linkplain RiRuntime runtime interface} needed by C1X.
     */
    public final MaxRuntime runtime = MaxRuntime.getInstance();

    /**
     * The {@linkplain CiTarget target} environment derived from a Maxine {@linkplain Platform platform} description.
     */
    public final CiTarget target;

    /**
     * The Maxine specific implementation of the {@linkplain RiXirGenerator interface} used by C1X
     * to incorporate runtime specific details when translating bytecode methods.
     */
    public final RiXirGenerator xirGenerator;

    /**
     * The C1X compiler instance configured for the Maxine runtime.
     */
    private C1XCompiler compiler;

    /**
     * Set to true once the C1X options are set (to allow subclasses of this scheme to coexist in the same image).
     */
    @HOSTED_ONLY
    public static boolean optionsRegistered;

    private static final int DEFAULT_OPT_LEVEL = 3;

    public static final VMIntOption optLevelOption = VMOptions.register(new VMIntOption("-C1X:OptLevel=", DEFAULT_OPT_LEVEL,
        "Set the optimization level of C1X.") {
            @Override
            public boolean parseValue(com.sun.max.unsafe.Pointer optionValue) {
                boolean result = super.parseValue(optionValue);
                if (result) {
                    C1XOptions.setOptimizationLevel(getValue());
                    return true;
                }
                return false;
            }
        }, MaxineVM.Phase.STARTING);

    /**
     * A map from option field names to some text describing the meaning and
     * usage of the corresponding C1X option.
     */
    private static Map<String, String> helpMap;

    public static Map<String, String> getHelpMap() {
        if (helpMap == null) {
            HashMap<String, String> map = new HashMap<String, String>();
            map.put("PrintFilter",
                    "Filter compiler tracing to methods whose fully qualified name " +
                    "matches <arg>. If <arg> starts with \"~\", then <arg> (without " +
                    "the \"~\") is interpreted as a regular expression. Otherwise, " +
                    "<arg> is interpreted as a simple substring.");

            map.put("TraceBytecodeParserLevel",
                    "Trace frontend bytecode parser at level <n> where 0 means no " +
                    "tracing, 1 means instruction tracing and 2 means instruction " +
                    "plus frame state tracing.");

            map.put("DetailedAsserts",
                    "Turn on detailed error checking that has a noticeable performance impact.");

            map.put("GenSpecialDivChecks",
                    "Generate code to check for (Integer.MIN_VALUE / -1) or (Long.MIN_VALUE / -1) " +
                    "instead of detecting these cases via instruction decoding in a trap handler.");

            map.put("UseStackMapTableLiveness",
                    "Use liveness information derived from StackMapTable class file attribute.");

            for (String name : map.keySet()) {
                try {
                    C1XOptions.class.getField(name);
                } catch (Exception e) {
                    throw new InternalError("The name '" + name + "' does not denote a field in " + C1XOptions.class);
                }
            }
            helpMap = Collections.unmodifiableMap(map);
        }
        return helpMap;
    }

    @HOSTED_ONLY
    public static C1X instance;

    @HOSTED_ONLY
    public C1X() {
        this(new MaxXirGenerator(C1XOptions.PrintXirTemplates), platform().target);
    }

    @HOSTED_ONLY
    protected C1X(RiXirGenerator xirGenerator, CiTarget target) {
        this.xirGenerator = xirGenerator;
        this.target = target;
        if (instance == null) {
            instance = this;
        }
    }

    @Override
    public void initialize(Phase phase) {
        if (isHosted() && !optionsRegistered) {
            C1XOptions.setOptimizationLevel(optLevelOption.getValue());
            C1XOptions.UseConstDirectCall = true; // Default
            C1XOptions.OptIntrinsify = false; // TODO (ds): remove once intrinisification works for Maxine
            C1XOptions.StackShadowPages = VmThread.STACK_SHADOW_PAGES;
            VMOptions.addFieldOptions("-C1X:", C1XOptions.class, getHelpMap());
            VMOptions.addFieldOptions("-ASM:", AsmOptions.class, getHelpMap());

            // Boot image code may not be safely deoptimizable due to metacircular issues
            // so only enable speculative optimizations at runtime
            C1XOptions.UseAssumptions = false;

            optionsRegistered = true;
        }

        if (isHosted() && phase == Phase.COMPILING) {
            // Temporary work-around to support the @ACCESSOR annotation.
            GraphBuilder.setAccessor(ClassActor.fromJava(Accessor.class));

            compiler = new C1XCompiler(runtime, target, xirGenerator, vm().registerConfigs.compilerStub);

            // search for the runtime call and register critical methods
            for (Method m : RuntimeCalls.class.getDeclaredMethods()) {
                int flags = m.getModifiers();
                if (Modifier.isStatic(flags) && Modifier.isPublic(flags)) {
                    new CriticalMethod(RuntimeCalls.class, m.getName(), SignatureDescriptor.create(m.getReturnType(), m.getParameterTypes()));
                }
            }

            // The direct call made from C1X compiled code for the UNCOMMON_TRAP intrinisic
            // must go through a stub that saves the register state before calling the deopt routine.
            CriticalMethod uncommonTrap = new CriticalMethod(MaxRuntimeCalls.class, "uncommonTrap", null);
            uncommonTrap.classMethodActor.compiledState = new Compilations(null, vm().stubs.genUncommonTrapStub());

        }
        if (phase == Phase.STARTING) {
            // Now it is safe to use speculative opts
            C1XOptions.UseAssumptions = deoptimizationSupported && Deoptimization.UseDeopt;
        } else if (phase == Phase.TERMINATING) {
            if (C1XOptions.PrintMetrics) {
                C1XMetrics.print();
                DebugInfo.dumpStats(Log.out);
            }
            if (C1XOptions.PrintTimers) {
                C1XTimers.print();
            }
        }
    }

    public C1XCompiler compiler() {
        if (isHosted() && compiler == null) {
            initialize(Phase.COMPILING);
        }
        return compiler;
    }

    public final TargetMethod compile(final ClassMethodActor method, boolean install, CiStatistics stats) {
        CiTargetMethod compiledMethod;
        do {
            compiledMethod = compiler().compileMethod(method, -1, xirGenerator, stats).targetMethod();
            Dependencies deps = DependenciesManager.validateDependencies(compiledMethod.assumptions());
            if (deps != Dependencies.INVALID) {
                if (C1XOptions.PrintTimers) {
                    C1XTimers.INSTALL.start();
                }
                MaxTargetMethod c1xTargetMethod = new MaxTargetMethod(method, compiledMethod, install);
                if (C1XOptions.PrintTimers) {
                    C1XTimers.INSTALL.stop();
                }
                if (deps != null) {
                    DependenciesManager.registerValidatedTarget(deps, c1xTargetMethod);
                    if (DependenciesManager.TraceDeps) {
                        Log.println("DEPS: " + deps.toString(true));
                    }
                }
                return c1xTargetMethod;

            }
            // Loop back and recompile.
        } while(true);
    }

    public boolean supportsInterpreterCompatibility() {
        return false;
    }

    /**
     * Specifies whether or not the VM context supports deoptimization. By default, this
     * is true and is only changed if {@link #deoptimizationNotSupported()} is called.
     */
    private static boolean deoptimizationSupported = true;

    @HOSTED_ONLY
    public void deoptimizationNotSupported() {
        C1XOptions.UseAssumptions = false;
        deoptimizationSupported = false;
    }

    public void resetMetrics() {
        C1XTimers.reset();
        for (Field f : C1XMetrics.class.getFields()) {
            if (f.getType() == int.class) {
                try {
                    f.set(null, 0);
                } catch (IllegalAccessException e) {
                    // do nothing.
                }
            }
        }
    }

    public CallEntryPoint calleeEntryPoint() {
        return CallEntryPoint.OPTIMIZED_ENTRY_POINT;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
