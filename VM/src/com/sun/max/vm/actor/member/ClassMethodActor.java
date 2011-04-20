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
package com.sun.max.vm.actor.member;

import static com.sun.max.vm.actor.member.LivenessAdapter.*;

import java.lang.reflect.*;
import java.util.*;

import com.sun.cri.ci.*;
import com.sun.max.annotate.*;
import com.sun.max.program.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.bytecode.*;
import com.sun.max.vm.bytecode.graft.*;
import com.sun.max.vm.classfile.*;
import com.sun.max.vm.classfile.constant.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.hosted.*;
import com.sun.max.vm.jni.*;
import com.sun.max.vm.type.*;
import com.sun.max.vm.verifier.*;

/**
 * Non-interface methods.
 *
 * @author Bernd Mathiske
 * @author Hiroshi Yamauchi
 * @author Doug Simon
 */
public abstract class ClassMethodActor extends MethodActor {

    @RESET
    public static boolean TraceJNI;
    static {
        VMOptions.addFieldOption("-XX:", "TraceJNI", "Trace JNI calls.");
    }

    @INSPECTED
    private CodeAttribute codeAttribute;

    @INSPECTED
    public volatile Object targetState;

    /**
     * This is the method whose code is actually compiled/executed. In most cases, it will be
     * equal to this object, unless this method has a {@linkplain SUBSTITUTE substitute}.
     *
     * This field is declared volatile so that the double-checking locking idiom used in
     * {@link #compilee()} works as expected. This correctness is guaranteed as long as the
     * compiler follows all the rules of the Java Memory Model as of JDK5 (JSR-133).
     */
    private volatile ClassMethodActor compilee;

    /**
     * The object representing the linkage of this native method actor to a native machine code address.
     * This value is {@code null} if this method is not {@linkplain #isNative() native}
     */
    public final NativeFunction nativeFunction;

    private CiExceptionHandler[] exceptionHandlers;

    public ClassMethodActor(Utf8Constant name, SignatureDescriptor descriptor, int flags, CodeAttribute codeAttribute, int intrinsic) {
        super(name, descriptor, flags, intrinsic);
        this.codeAttribute = codeAttribute;
        this.nativeFunction = isNative() ? new NativeFunction(this) : null;

        if (MaxineVM.isHosted() && codeAttribute != null) {
            ClassfileWriter.classfileCodeAttributeMap.put(this, codeAttribute);
            ClassfileWriter.classfileCodeMap.put(this, codeAttribute.code().clone());
        }
    }

    /**
     * @return number of locals used by the method parameters (including the receiver if this method isn't static).
     */
    public int numberOfParameterSlots() {
        return descriptor().computeNumberOfSlots() + ((isStatic()) ? 0 : 1);
    }

    public boolean isDeclaredNeverInline() {
        return compilee().isNeverInline();
    }

    @INLINE
    public final boolean isDeclaredFoldable() {
        return isDeclaredFoldable(flags());
    }

    @Override
    public final byte[] code() {
        CodeAttribute codeAttribute = codeAttribute();
        if (codeAttribute != null) {
            return codeAttribute.code();
        }
        return null;
    }

    private CiBitMap[] livenessMap;

    @Override
    public final CiBitMap[] livenessMap() {
        if (livenessMap != null) {
            return livenessMap == NO_LIVENESS_MAP ? null : livenessMap;
        }
        livenessMap = new LivenessAdapter(this).livenessMap;
        if (livenessMap.length == 0) {
            assert livenessMap == NO_LIVENESS_MAP;
            return null;
        }
        return livenessMap;
    }

    @Override
    public CiExceptionHandler[] exceptionHandlers() {
        if (exceptionHandlers != null) {
            // return the cached exception handlers
            return exceptionHandlers;
        }

        CodeAttribute codeAttribute = codeAttribute();
        exceptionHandlers = codeAttribute != null ? codeAttribute.exceptionHandlers() : CiExceptionHandler.NONE;
        return exceptionHandlers;
    }

    @Override
    public String jniSymbol() {
        if (nativeFunction != null) {
            return nativeFunction.makeSymbol();
        }
        return null;
    }

    @Override
    public int maxLocals() {
        CodeAttribute codeAttribute = codeAttribute();
        if (codeAttribute != null) {
            return codeAttribute.maxLocals;
        }
        return 0;
    }

    @Override
    public int maxStackSize() {
        CodeAttribute codeAttribute = codeAttribute();
        if (codeAttribute != null) {
            return codeAttribute.maxStack;
        }
        return 0;
    }

    /**
     * Gets the bytecode that is to be compiled and/or executed for this actor.
     * @return the code attribute
     */
    public final CodeAttribute codeAttribute() {
        // Ensure that any prerequisite substitution and/or preprocessing of the code to be
        // compiled/executed is performed first
        compilee();

        return codeAttribute;
    }

    /**
     * Allows bytecode verification and {@linkplain #validateInlineAnnotation(ClassMethodActor) validation} of
     * inlining annotations to be enabled by a hosted process.
     */
    @HOSTED_ONLY
    public static boolean hostedVerificationEnabled;

    /**
     * @return the actor for the method that will be compiled and/or executed in lieu of this method
     */
    public final ClassMethodActor compilee() {
        if (this.compilee == null) {
            synchronized (this) {
                if (compilee != null) {
                    return compilee;
                }

                if (!isMiranda()) {
                    final boolean isConstructor = isInitializer();
                    final ClassMethodActor substitute = METHOD_SUBSTITUTIONS.Static.findSubstituteFor(this);
                    if (substitute != null) {
                        compilee = substitute.compilee();
                        codeAttribute = compilee.codeAttribute;
                        return compilee;
                    }
                    if (MaxineVM.isHosted() && hostedVerificationEnabled && !isConstructor) {
                        validateInlineAnnotation(this);
                    }
                }

                ClassMethodActor compilee = this;
                CodeAttribute codeAttribute = this.codeAttribute;
                ClassVerifier verifier = null;

                final CodeAttribute processedCodeAttribute = preprocess(compilee, codeAttribute);
                final boolean modified = processedCodeAttribute != codeAttribute;
                codeAttribute = processedCodeAttribute;

                final ClassActor holder = compilee.holder();
                if (MaxineVM.isHosted()) {
                    if (hostedVerificationEnabled) {
                        // We simply verify all methods during boot image build time as the overhead should be acceptable.
                        verifier = modified ? new TypeInferencingVerifier(holder) : Verifier.verifierFor(holder);
                    }
                } else {
                    if (holder().majorVersion >= 50) {
                        if (modified) {
                            // The methods in class files whose version is greater than or equal to 50.0 are required to
                            // have stack maps. If the bytecode of such a method has been preprocessed, then its
                            // pre-existing stack maps will have been invalidated and must be regenerated with the
                            // type inferencing verifier
                            verifier = new TypeInferencingVerifier(holder);
                        }
                    }
                }

                if (verifier != null && codeAttribute != null && !compilee.holder().isReflectionStub()) {
                    if (MaxineVM.isHosted()) {
                        try {
                            codeAttribute = verifier.verify(compilee, codeAttribute);
                        } catch (HostOnlyClassError e) {
                        } catch (HostOnlyMethodError e) {
                        } catch (OmittedClassError e) {
                            // Ignore: assume all classes being loaded during boot imaging are verifiable.
                        }
                    } else {
                        codeAttribute = verifier.verify(compilee, codeAttribute);
                        beVerified();
                    }
                }

                intrinsify(compilee, codeAttribute);

                this.codeAttribute = codeAttribute;
                this.compilee = compilee;
            }
        }
        return compilee;
    }

    private boolean intrinsify(ClassMethodActor compilee, CodeAttribute codeAttribute) {
        if (codeAttribute != null) {
            Intrinsifier intrinsifier = new Intrinsifier(compilee, codeAttribute);
            intrinsifier.run();
            if (intrinsifier.unsafe) {
                compilee.beUnsafe();
                beUnsafe();
            }
            if (intrinsifier.extended) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets a {@link StackTraceElement} object describing the source code location corresponding to a given BCI in this method.
     *
     * @param bci a BCI in this method's {@linkplain #codeAttribute() code}
     * @return the stack trace element
     */
    public StackTraceElement toStackTraceElement(int bci) {
        final ClassActor holder = holder();
        return new StackTraceElement(holder.name.string, name.string, holder.sourceFileName, sourceLineNumber(bci));
    }

    /**
     * Gets the source line number corresponding to a given BCI in this method.
     * @param bci the BCI
     * @return -1 if a source line number is not available
     */
    public int sourceLineNumber(int bci) {
        CodeAttribute codeAttribute = this.codeAttribute;
        if (codeAttribute == null) {
            return -1;
        }
        return codeAttribute.lineNumberTable().findLineNumber(bci);
    }

    /**
     * @see InliningAnnotationsValidator#apply(ClassMethodActor)
     */
    @HOSTED_ONLY
    private void validateInlineAnnotation(ClassMethodActor compilee) {
        if (!compilee.holder().isReflectionStub()) {
            try {
                InliningAnnotationsValidator.apply(compilee);
            } catch (LinkageError linkageError) {
                ProgramWarning.message("Error while validating INLINE annotation for " + compilee + ": " + linkageError);
            }
        }
    }

    /**
     * Gets the original if this is a {@linkplain SUBSTITUTE substitute} method actor otherwise
     * just return this method actor.
     */
    public ClassMethodActor original() {
        final ClassMethodActor original = METHOD_SUBSTITUTIONS.Static.findOriginal(this);
        if (original != null) {
            return original;
        }
        return this;
    }

    public synchronized void verify(ClassVerifier classVerifier) {
        if (codeAttribute() != null && !isVerified(flags())) {
            codeAttribute = classVerifier.verify(this, codeAttribute);
            beVerified();
        }
    }

    public static ClassMethodActor fromJava(Method javaMethod) {
        return (ClassMethodActor) MethodActor.fromJava(javaMethod);
    }

    public TargetMethod currentTargetMethod() {
        return TargetState.currentTargetMethod(targetState);
    }

    public TargetMethod[] targetMethodHistory() {
        return TargetState.targetMethodHistory(targetState);
    }

    public int targetMethodCount() {
        return TargetState.targetMethodCount(targetState);
    }

    /**
     * Records if this object returned {@code true} for a call to {@link #hasCompiledCode()} during
     * boot image building.
     */
    @HOSTED_ONLY
    public boolean mustCompileInImage;

    @Override
    public boolean hasCompiledCode() {
        if (MaxineVM.isHosted()) {
            if (!isAbstract() && !isIntrinsic()) {
                mustCompileInImage = true;
                return true;
            }
            return false;
        }
        return targetMethodCount() > 0;
    }

    private static BytecodeTransformation transformationClient;

    /**
     * This needs to be called before any compilation happens.
     */
    @HOSTED_ONLY
    public static void setTransformationClient(BytecodeTransformation client) {
        assert transformationClient == null : "only one transformation client is allowed";
        transformationClient = client;
    }

    private static final int PREPROCESS_TRACE_LEVEL = 6;

    private CodeAttribute preprocess(ClassMethodActor cma, CodeAttribute originalCodeAttribute) {
        CodeAttribute newCodeAttribute = originalCodeAttribute;
        ConstantPoolEditor constantPoolEditor = null;
        String reason = "";

        try {
            if (cma.isNative()) {
                assert newCodeAttribute == null;
                constantPoolEditor = cma.holder().constantPool().edit();
                newCodeAttribute = new NativeStubGenerator(constantPoolEditor, cma).codeAttribute();
                reason = "native";
            }
            if (transformationClient != null) {
                if (constantPoolEditor == null) {
                    constantPoolEditor = cma.holder().constantPool().edit();
                }
                newCodeAttribute = transformationClient.transform(constantPoolEditor, cma, newCodeAttribute);
                reason += "client";
            }
        } finally {
            if (constantPoolEditor != null) {
                constantPoolEditor.release();
                constantPoolEditor = null;
            }
        }

        if (newCodeAttribute == null) {
            return null;
        }

        if (newCodeAttribute != originalCodeAttribute) {
            if (Trace.hasLevel(PREPROCESS_TRACE_LEVEL)) {
                Trace.line(PREPROCESS_TRACE_LEVEL);
                Trace.line(PREPROCESS_TRACE_LEVEL, "bytecode preprocessed [" + reason + "]: " + cma.format("%r %H.%n(%p)"));
                if (!cma.isNative()) {
                    Trace.stream().println("--- BEFORE PREPROCESSING ---");
                    CodeAttributePrinter.print(Trace.stream(), originalCodeAttribute);
                }
                Trace.stream().println("--- AFTER PREPROCESSING ---");
                CodeAttributePrinter.print(Trace.stream(), newCodeAttribute);
            }
        }

        return newCodeAttribute;
    }
}
