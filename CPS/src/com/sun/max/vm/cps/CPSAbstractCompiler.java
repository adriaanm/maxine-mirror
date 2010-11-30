/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.max.vm.cps;

import java.util.*;

import com.sun.max.*;
import com.sun.max.annotate.*;
import com.sun.max.vm.*;
import com.sun.max.vm.MaxineVM.Phase;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.compiler.builtin.*;
import com.sun.max.vm.compiler.snippet.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.cps.ir.*;
import com.sun.max.vm.cps.ir.observer.*;
import com.sun.max.vm.hotpath.*;

/**
 * @author Bernd Mathiske
 */
public abstract class CPSAbstractCompiler extends AbstractVMScheme implements BootstrapCompilerScheme {

    /**
     * Gets this compiler's last IR generator (typically a {@link com.sun.max.vm.cps.target.TargetGenerator}).
     *
     * @return the last IR generator of this compiler
     */
    public abstract IrGenerator irGenerator();

    @Override
    public CallEntryPoint calleeEntryPoint() {
        return CallEntryPoint.OPTIMIZED_ENTRY_POINT;
    }

    protected abstract List<IrGenerator> irGenerators();

    @Override
    public void initialize(Phase phase) {
        super.initialize(phase);

        if (phase == Phase.BOOTSTRAPPING || phase == Phase.STARTING) {
            IrObserverConfiguration.attach(irGenerators());
        }

        if (MaxineVM.isHosted() && phase == MaxineVM.Phase.COMPILING) {
            compileSnippets();
        }

        if (phase == Phase.TERMINATING) {
            for (IrGenerator generator : irGenerators()) {
                generator.notifyAfterFinish();
            }
        }
    }

    @HOSTED_ONLY
    public void createBuiltins(PackageLoader packageLoader) {
        packageLoader.loadAndInitializeAll(Builtin.class);
        Builtin.initialize();
    }

    @HOSTED_ONLY
    public void createSnippets(PackageLoader packageLoader) {
        packageLoader.loadAndInitializeAll(Snippet.class);
        packageLoader.loadAndInitializeAll(HotpathSnippet.class);
    }

    @HOSTED_ONLY
    private boolean areSnippetsCompiled = false;

    @HOSTED_ONLY
    public boolean areSnippetsCompiled() {
        return areSnippetsCompiled;
    }

    @HOSTED_ONLY
    public void compileSnippets() {
        areSnippetsCompiled = true;
        ClassActor.DEFERRABLE_QUEUE_2.runAll();
    }

    @Override
    public final <Type extends TargetMethod> Class<Type> compiledType() {
        Class irMethodType = irGenerator().irMethodType;
        if (TargetMethod.class.isAssignableFrom(irMethodType)) {
            Class<Class<Type>> type = null;
            return Utils.cast(type, irMethodType);
        }
        return null;
    }

    public boolean compilesToTargetMethod() {
        return TargetMethod.class.isAssignableFrom(irGenerator().irMethodType);
    }

    public final TargetMethod compile(ClassMethodActor classMethodActor) {
        IrMethod method = compileIR(classMethodActor);
        if (method instanceof TargetMethod) {
            return (TargetMethod) method;
        }
        return null;
    }

    public final IrMethod compileIR(ClassMethodActor classMethodActor) {
        return irGenerator().makeIrMethod(classMethodActor);
    }

    @HOSTED_ONLY
    public void initializeForJitCompilations() {
    }

    public boolean isBuiltinImplemented(Builtin builtin) {
        return true;
    }
}
