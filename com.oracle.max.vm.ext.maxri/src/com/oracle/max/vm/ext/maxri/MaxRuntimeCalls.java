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
package com.oracle.max.vm.ext.maxri;

import static com.sun.max.vm.MaxineVM.*;
import java.lang.annotation.*;
import java.lang.reflect.*;

import com.sun.cri.ci.*;
import com.sun.max.annotate.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.heap.*;
import com.sun.max.vm.object.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.stack.*;

/**
 * This class contains the implementation of runtime calls that are called by
 * code emitted by the C1X compiler.
 */
public class MaxRuntimeCalls {

    // a local flag to enable calls to verify the reference map for each runtime call
    private static final boolean ENABLE_REFMAP_VERIFICATION = true;

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface C1X_RUNTIME_ENTRYPOINT {
        CiRuntimeCall runtimeCall();
    }

    public static ClassMethodActor getClassMethodActor(CiRuntimeCall call) {
        final ClassMethodActor result = runtimeCallMethods[call.ordinal()];
        assert result != null : "no runtime method defined for " + call;
        return result;
    }

    private static ClassMethodActor[] runtimeCallMethods = new ClassMethodActor[CiRuntimeCall.values().length];

    static {
        for (Method method : MaxRuntimeCalls.class.getMethods()) {
            C1X_RUNTIME_ENTRYPOINT entry = method.getAnnotation(C1X_RUNTIME_ENTRYPOINT.class);
            if (entry != null) {
                registerMethod(method, entry.runtimeCall());
            }
        }

        for (CiRuntimeCall call : CiRuntimeCall.values()) {
            assert checkCompatible(call, getClassMethodActor(call));
        }
    }

    @C1X_RUNTIME_ENTRYPOINT(runtimeCall = CiRuntimeCall.UnwindException)
    public static void runtimeUnwindException(Throwable throwable) throws Throwable {
        Throw.raise(throwable);
    }

    @HOSTED_ONLY
    private static boolean checkCompatible(CiRuntimeCall call, ClassMethodActor classMethodActor) {
        assert classMethodActor.descriptor().returnKind(true) == call.resultKind;
        CiKind[] kinds = CiUtil.signatureToKinds(classMethodActor.signature(), classMethodActor.isStatic() ? null : classMethodActor.holder().kind(true));
        for (int i = 0; i < call.arguments.length; i++) {
            assert kinds[i] == call.arguments[i] : call + " incompatible with " + classMethodActor;
        }
        return true;
    }

    private static void verifyRefMaps() {
        if (ENABLE_REFMAP_VERIFICATION && StackReferenceMapPreparer.VerifyRefMaps) {
            StackReferenceMapPreparer.verifyReferenceMapsForThisThread();
        }
    }

    @C1X_RUNTIME_ENTRYPOINT(runtimeCall = CiRuntimeCall.RegisterFinalizer)
    public static void runtimeRegisterFinalizer(Object object) {
        verifyRefMaps();
        if (ObjectAccess.readClassActor(object).hasFinalizer()) {
            SpecialReferenceManager.registerFinalizee(object);
        }
    }

    @C1X_RUNTIME_ENTRYPOINT(runtimeCall = CiRuntimeCall.HandleException)
    public static void runtimeHandleException(Throwable throwable) throws Throwable {
        verifyRefMaps();
        Throw.raise(throwable);
    }

    @C1X_RUNTIME_ENTRYPOINT(runtimeCall = CiRuntimeCall.OSRMigrationEnd)
    public static void runtimeOSRMigrationEnd() {
        verifyRefMaps();
    }

    @C1X_RUNTIME_ENTRYPOINT(runtimeCall = CiRuntimeCall.JavaTimeMillis)
    public static long runtimeJavaTimeMillis() {
        verifyRefMaps();
        return MaxineVM.native_currentTimeMillis();
    }

    @C1X_RUNTIME_ENTRYPOINT(runtimeCall = CiRuntimeCall.JavaTimeNanos)
    public static long runtimeJavaTimeNanos() {
        verifyRefMaps();
        return MaxineVM.native_nanoTime();
    }

    @C1X_RUNTIME_ENTRYPOINT(runtimeCall = CiRuntimeCall.Debug)
    public static void runtimeDebug() {
        verifyRefMaps();
        throw FatalError.unexpected("Debug");
    }

    @C1X_RUNTIME_ENTRYPOINT(runtimeCall = CiRuntimeCall.ArithmethicLrem)
    public static long runtimeArithmethicLrem(long a, long b) {
        verifyRefMaps();
        throw FatalError.unexpected("Compiler should directly translate LREM");
    }

    @C1X_RUNTIME_ENTRYPOINT(runtimeCall = CiRuntimeCall.ArithmeticLdiv)
    public static long runtimeArithmeticLdiv(long a, long b) {
        verifyRefMaps();
        throw FatalError.unexpected("Compiler should directly translate LDIV");
    }

    @C1X_RUNTIME_ENTRYPOINT(runtimeCall = CiRuntimeCall.ArithmeticFrem)
    public static float runtimeArithmeticFrem(float v1, float v2) {
        verifyRefMaps();
        return Snippets.floatRemainder(v1, v2);
    }

    @C1X_RUNTIME_ENTRYPOINT(runtimeCall = CiRuntimeCall.ArithmeticDrem)
    public static double runtimeArithmeticDrem(double v1, double v2) {
        verifyRefMaps();
        return Snippets.doubleRemainder(v1, v2);
    }

    @C1X_RUNTIME_ENTRYPOINT(runtimeCall = CiRuntimeCall.ArithmeticCos)
    public static double runtimeArithmeticCos(double v) {
        verifyRefMaps();
        return Math.cos(v);
    }

    @C1X_RUNTIME_ENTRYPOINT(runtimeCall = CiRuntimeCall.ArithmeticTan)
    public static double runtimeArithmeticTan(double v) {
        verifyRefMaps();
        return Math.tan(v);
    }

    @C1X_RUNTIME_ENTRYPOINT(runtimeCall = CiRuntimeCall.ArithmeticLog)
    public static double runtimeArithmeticLog(double v) {
        verifyRefMaps();
        return Math.log(v);
    }

    @C1X_RUNTIME_ENTRYPOINT(runtimeCall = CiRuntimeCall.ArithmeticLog10)
    public static double runtimeArithmeticLog10(double v) {
        verifyRefMaps();
        return Math.log10(v);
    }

    @C1X_RUNTIME_ENTRYPOINT(runtimeCall = CiRuntimeCall.ArithmeticSin)
    public static double runtimeArithmeticSin(double v) {
        verifyRefMaps();
        return Math.sin(v);
    }

    /**
     * The body of this method is provided by {@link Stubs#genUncommonTrapStub()}.
     */
    @C1X_RUNTIME_ENTRYPOINT(runtimeCall = CiRuntimeCall.Deoptimize)
    public static void uncommonTrap() {
        throw FatalError.unexpected("stub should be overwritten");
    }

    @HOSTED_ONLY
    private static void registerMethod(Method selectedMethod, CiRuntimeCall call) {
        ClassMethodActor classMethodActor = null;
        assert runtimeCallMethods[call.ordinal()] == null : "method already defined";
        classMethodActor = ClassMethodActor.fromJava(selectedMethod);
        runtimeCallMethods[call.ordinal()] = classMethodActor;
        if (MaxineVM.isHosted()) {
            new CriticalMethod(classMethodActor, CallEntryPoint.OPTIMIZED_ENTRY_POINT);
        } else {
            vm().compilationBroker.compile(classMethodActor, null);
        }
    }
}
