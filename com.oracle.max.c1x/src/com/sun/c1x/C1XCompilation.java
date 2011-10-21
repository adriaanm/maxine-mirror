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

package com.sun.c1x;

import java.util.*;

import com.oracle.max.asm.*;
import com.sun.c1x.alloc.*;
import com.sun.c1x.asm.*;
import com.sun.c1x.gen.*;
import com.sun.c1x.gen.LIRGenerator.*;
import com.sun.c1x.graph.*;
import com.sun.c1x.ir.*;
import com.sun.c1x.lir.*;
import com.sun.c1x.observer.*;
import com.sun.c1x.value.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;
import com.sun.cri.ci.CiCompiler.DebugInfoLevel;
import com.sun.cri.ri.*;

/**
 * This class encapsulates global information about the compilation of a particular method,
 * including a reference to the runtime, statistics about the compiled code, etc.
 */
public final class C1XCompilation {

    private static ThreadLocal<C1XCompilation> currentCompilation = new ThreadLocal<C1XCompilation>();

    public final C1XCompiler compiler;
    public final CiTarget target;
    public final RiRuntime runtime;
    public final RiResolvedMethod method;
    public final RiRegisterConfig registerConfig;
    public final CiStatistics stats;
    public final int osrBCI;
    public final CiAssumptions assumptions = new CiAssumptions();
    public final FrameState placeholderState;

    private boolean hasExceptionHandlers;
    private final C1XCompilation parent;

    /**
     * @see #setNotTypesafe()
     * @see #isTypesafe()
     */
    private boolean typesafe = true;

    private int nextID = 1;

    private FrameMap frameMap;
    private TargetMethodAssembler assembler;

    private IR hir;

    private LIRGenerator lirGenerator;

    /**
     * Creates a new compilation for the specified method and runtime.
     *
     * @param compiler the compiler
     * @param method the method to be compiled or {@code null} if generating code for a stub
     * @param osrBCI the bytecode index for on-stack replacement, if requested
     * @param stats externally supplied statistics object to be used if not {@code null}
     */
    public C1XCompilation(C1XCompiler compiler, RiResolvedMethod method, int osrBCI, CiStatistics stats, DebugInfoLevel debugInfoLevel) {
        this.parent = currentCompilation.get();
        currentCompilation.set(this);
        this.compiler = compiler;
        this.target = compiler.target;
        this.runtime = compiler.runtime;
        this.method = method;
        this.osrBCI = osrBCI;
        this.stats = stats == null ? new CiStatistics() : stats;
        this.registerConfig = method == null ? compiler.compilerStubRegisterConfig : runtime.getRegisterConfig(method);
        this.placeholderState = debugInfoLevel == DebugInfoLevel.REF_MAPS ? new MutableFrameState(new IRScope(null, null, method, -1), 0, 0, 0) : null;

        if (compiler.isObserved()) {
            compiler.fireCompilationStarted(new CompilationEvent(this));
        }
    }

    public void close() {
        currentCompilation.set(parent);
    }

    public IR hir() {
        return hir;
    }

    /**
     * Records that this compilation has exception handlers.
     */
    public void setHasExceptionHandlers() {
        hasExceptionHandlers = true;
    }

    /**
     * Records that this compilation encountered an instruction (e.g. {@link Bytecodes#UNSAFE_CAST})
     * that breaks the type safety invariant of the input bytecode.
     */
    public void setNotTypesafe() {
        typesafe = false;
    }

    /**
     * Checks whether this compilation is for an on-stack replacement.
     *
     * @return {@code true} if this compilation is for an on-stack replacement
     */
    public boolean isOsrCompilation() {
        return osrBCI >= 0;
    }

    /**
     * Records an assumption that the specified type has no finalizable subclasses.
     *
     * @param receiverType the type that is assumed to have no finalizable subclasses
     * @return {@code true} if the assumption was recorded and can be assumed; {@code false} otherwise
     */
    public boolean recordNoFinalizableSubclassAssumption(RiType receiverType) {
        return false;
    }

    /**
     * Converts this compilation to a string.
     *
     * @return a string representation of this compilation
     */
    @Override
    public String toString() {
        if (isOsrCompilation()) {
            return "osr-compile @ " + osrBCI + ": " + method;
        }
        return "compile: " + method;
    }

    /**
     * Builds the block map for the specified method.
     *
     * @param method the method for which to build the block map
     * @param osrBCI the OSR bytecode index; {@code -1} if this is not an OSR
     * @return the block map for the specified method
     */
    public BlockMap getBlockMap(RiResolvedMethod method, int osrBCI) {
        // PERF: cache the block map for methods that are compiled or inlined often
        BlockMap map = new BlockMap(method, hir.numberOfBlocks());
        boolean isOsrCompilation = false;
        if (osrBCI >= 0) {
            map.addEntrypoint(osrBCI, BlockBegin.BlockFlag.OsrEntry);
            isOsrCompilation = true;
        }
        if (!map.build(!isOsrCompilation && C1XOptions.PhiLoopStores)) {
            throw new CiBailout("build of BlockMap failed for " + method);
        } else {
            if (compiler.isObserved()) {
                String label = CiUtil.format("BlockListBuilder %f %R %H.%n(%P)", method);
                compiler.fireCompilationEvent(new CompilationEvent(this, method, label, map, method.code().length));
            }
        }
        map.cleanup();
        stats.bytecodeCount += map.numberOfBytes();
        stats.blockCount += map.numberOfBlocks();
        return map;
    }

    /**
     * Returns the frame map of this compilation.
     * @return the frame map
     */
    public FrameMap frameMap() {
        return frameMap;
    }

    public TargetMethodAssembler assembler() {
        if (assembler == null) {
            AbstractAssembler asm = compiler.backend.newAssembler(registerConfig);
            assembler = new TargetMethodAssembler(asm);
            assembler.setFrameSize(frameMap.frameSize());
            assembler.targetMethod.setCustomStackAreaOffset(frameMap.offsetToCustomArea());
        }
        return assembler;
    }

    public boolean hasExceptionHandlers() {
        return hasExceptionHandlers;
    }

    /**
     * Determines if this compilation has encountered any instructions (e.g. {@link Bytecodes#UNSAFE_CAST})
     * that break the type safety invariant of the input bytecode.
     */
    public boolean isTypesafe() {
        return typesafe;
    }

    public CiResult compile() {
        CiTargetMethod targetMethod;
        try {
            emitHIR();
            emitLIR();
            targetMethod = emitCode();

            if (C1XOptions.PrintMetrics) {
                C1XMetrics.BytecodesCompiled += method.code().length;
            }
        } catch (CiBailout b) {
            return new CiResult(null, b, stats);
        } catch (Throwable t) {
            return new CiResult(null, new CiBailout("Exception while compiling: " + method, t), stats);
        } finally {
            if (compiler.isObserved()) {
                compiler.fireCompilationFinished(new CompilationEvent(this));
            }
        }

        return new CiResult(targetMethod, null, stats);
    }

    public IR emitHIR() {
        hir = new IR(this);
        hir.build();
        return hir;
    }

    public void initFrameMap(int numberOfLocks) {
        frameMap = compiler.backend.newFrameMap(this, method, numberOfLocks);
    }

    private void emitLIR() {
        if (C1XOptions.GenLIR) {
            if (C1XOptions.PrintTimers) {
                C1XTimers.LIR_CREATE.start();
            }

            initFrameMap(hir.topScope.maxLocks());

            lirGenerator = compiler.backend.newLIRGenerator(this);
            for (BlockBegin begin : hir.linearScanOrder()) {
                lirGenerator.doBlock(begin);
            }

            if (C1XOptions.PrintTimers) {
                C1XTimers.LIR_CREATE.stop();
            }

            new LinearScan(this, hir, lirGenerator, frameMap()).allocate();
        }
    }

    private CiTargetMethod emitCode() {
        if (C1XOptions.GenLIR && C1XOptions.GenCode) {
            final LIRAssembler lirAssembler = compiler.backend.newLIRAssembler(this, assembler());
            lirAssembler.emitCode(hir.linearScanOrder());

            // generate code for slow cases
            lirAssembler.emitLocalStubs();

            // generate exception adapters
            lirAssembler.emitExceptionEntries();

            // generate deoptimization stubs
            ArrayList<DeoptimizationStub> deoptimizationStubs = lirGenerator.deoptimizationStubs();
            if (deoptimizationStubs != null) {
                for (DeoptimizationStub stub : deoptimizationStubs) {
                    lirAssembler.emitDeoptizationStub(stub);
                }
            }

            // generate traps at the end of the method
            lirAssembler.emitTraps();

            CiTargetMethod targetMethod = assembler().finishTargetMethod(method, runtime, lirAssembler.registerRestoreEpilogueOffset, false);
            if (!assumptions.isEmpty()) {
                targetMethod.setAssumptions(assumptions);
            }

            if (compiler.isObserved()) {
                compiler.fireCompilationEvent(new CompilationEvent(this, "After code generation", hir.startBlock, false, true, targetMethod));
            }

            if (C1XOptions.PrintTimers) {
                C1XTimers.CODE_CREATE.stop();
            }
            return targetMethod;
        }

        return null;
    }

    public int nextID() {
        return nextID++;
    }

    public static C1XCompilation compilation() {
        C1XCompilation compilation = currentCompilation.get();
        assert compilation != null;
        return compilation;
    }

    public static C1XCompilation compilationOrNull() {
        return currentCompilation.get();
    }
}
