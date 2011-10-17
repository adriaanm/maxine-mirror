/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.hotspot;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.max.criutils.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * Implementation of RiMethod for resolved HotSpot methods.
 */
public final class HotSpotMethodResolvedImpl extends HotSpotMethod implements HotSpotMethodResolved {

    /** DO NOT USE IN JAVA CODE! */
    @Deprecated
    private Object javaMirror;

    // cached values
    private final int codeSize;
    private final int accessFlags;
    private final int maxLocals;
    private final int maxStackSize;
    private RiExceptionHandler[] exceptionHandlers;
    private RiSignature signature;
    private Boolean hasBalancedMonitors;
    private Graph intrinsicGraph;
    private Map<Object, Object> compilerStorage;
    private RiResolvedType holder;

    private HotSpotMethodResolvedImpl() {
        super(null);
        codeSize = -1;
        accessFlags = -1;
        maxLocals = -1;
        maxStackSize = -1;
    }

    @Override
    public RiResolvedType holder() {
        return holder;
    }

    @Override
    public int accessFlags() {
        return accessFlags;
    }

    @Override
    public boolean canBeStaticallyBound() {
        return isLeafMethod() || Modifier.isStatic(accessFlags());
    }

    @Override
    public byte[] code() {
        byte[] ret = compiler.getVMEntries().RiMethod_code(this);
        assert ret.length == codeSize : "expected: " + codeSize + ", actual: " + ret.length;
        return ret;
    }

    @Override
    public int codeSize() {
        return codeSize;
    }

    @Override
    public RiExceptionHandler[] exceptionHandlers() {
        return compiler.getVMEntries().RiMethod_exceptionHandlers(this);
    }

    @Override
    public boolean hasBalancedMonitors() {
        if (hasBalancedMonitors == null) {
            hasBalancedMonitors = compiler.getVMEntries().RiMethod_hasBalancedMonitors(this);
        }
        return hasBalancedMonitors;
    }

    @Override
    public boolean isClassInitializer() {
        return "<clinit>".equals(name) && Modifier.isStatic(accessFlags());
    }

    @Override
    public boolean isConstructor() {
        return "<init>".equals(name) && !Modifier.isStatic(accessFlags());
    }

    @Override
    public boolean isLeafMethod() {
        return Modifier.isFinal(accessFlags()) || Modifier.isPrivate(accessFlags());
    }

    @Override
    public boolean isOverridden() {
        throw new UnsupportedOperationException("isOverridden");
    }

    @Override
    public boolean noSafepointPolls() {
        return false;
    }

    @Override
    public String jniSymbol() {
        throw new UnsupportedOperationException("jniSymbol");
    }

    public CiBitMap[] livenessMap() {
        return null;
    }

    @Override
    public int maxLocals() {
        return maxLocals;
    }

    @Override
    public int maxStackSize() {
        return maxStackSize;
    }

    @Override
    public StackTraceElement toStackTraceElement(int bci) {
        return CiUtil.toStackTraceElement(this, bci);
    }

    @Override
    public RiResolvedMethod uniqueConcreteMethod() {
        return (RiResolvedMethod) compiler.getVMEntries().RiMethod_uniqueConcreteMethod(this);
    }

    @Override
    public RiSignature signature() {
        if (signature == null) {
            signature = new HotSpotSignature(compiler, compiler.getVMEntries().RiMethod_signature(this));
        }
        return signature;
    }

    @Override
    public String toString() {
        return "HotSpotMethod<" + CiUtil.format("%h.%n", this, false) + ">";
    }

    public boolean hasCompiledCode() {
        return compiler.getVMEntries().RiMethod_hasCompiledCode(this);
    }

    @Override
    public RiResolvedType accessor() {
        return null;
    }

    @Override
    public String intrinsic() {
        return null;
    }

    @Override
    public boolean minimalDebugInfo() {
        return false;
    }

    public int invocationCount() {
        return compiler.getVMEntries().RiMethod_invocationCount(this);
    }

    public int exceptionProbability(int bci) {
        return compiler.getVMEntries().RiMethod_exceptionProbability(this, bci);
    }

    public RiTypeProfile typeProfile(int bci) {
        return compiler.getVMEntries().RiMethod_typeProfile(this, bci);
    }

    public double branchProbability(int bci) {
        return compiler.getVMEntries().RiMethod_branchProbability(this, bci);
    }

    public double[] switchProbability(int bci) {
        return compiler.getVMEntries().RiMethod_switchProbability(this, bci);
    }

    @Override
    public int compiledCodeSize() {
        return compiler.getVMEntries().RiMethod_compiledCodeSize(this);
    }

    @Override
    public Map<Object, Object> compilerStorage() {
        if (compilerStorage == null) {
            compilerStorage = new HashMap<Object, Object>();
        }
        return compilerStorage;
    }

    public void dumpProfile() {
        TTY.println("profile info for %s", this);
        TTY.println("canBeStaticallyBound: " + canBeStaticallyBound());
        TTY.println("invocationCount: " + invocationCount());
        for (int i = 0; i < codeSize(); i++) {
            if (branchProbability(i) != -1) {
                TTY.println("  branchProbability@%d: %f", i, branchProbability(i));
            }
            if (exceptionProbability(i) > 0) {
                TTY.println("  exceptionProbability@%d: %d", i, exceptionProbability(i));
            }
            RiTypeProfile profile = typeProfile(i);
            if (profile != null && profile.count > 0) {
                TTY.print("  profile@%d: count: %d, morphism: %d", i, profile.count, profile.morphism);
                if (profile.types != null) {
                    TTY.print(", types:");
                    for (int i2 = 0; i2 < profile.types.length; i2++) {
                        TTY.print(" %s (%f)", profile.types[i2], profile.probabilities[i2]);
                    }
                }
                TTY.println();
                if (exceptionProbability(i) > 0) {
                    TTY.println("  exceptionProbability@%d: %d", i, exceptionProbability(i));
                }
            }
        }
    }
}
