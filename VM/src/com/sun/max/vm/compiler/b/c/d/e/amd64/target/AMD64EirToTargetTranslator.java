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
/*VCSID=5c0a84f1-602e-41f1-b395-1c879fc053f3*/
package com.sun.max.vm.compiler.b.c.d.e.amd64.target;

import static com.sun.max.vm.compiler.CallEntryPoint.*;

import com.sun.max.asm.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.compiler.eir.*;
import com.sun.max.vm.compiler.eir.amd64.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.compiler.target.amd64.*;

/**
 * @author Bernd Mathiske
 * @author Doug Simon
 * @author Laurent Daynes
 */
public final class AMD64EirToTargetTranslator extends EirToTargetTranslator {

    public AMD64EirToTargetTranslator(TargetGeneratorScheme targetGeneratorScheme) {
        super(targetGeneratorScheme, InstructionSet.AMD64, AMD64TargetMethod.Static.registerReferenceMapSize());
    }

    @Override
    public TargetMethod createIrMethod(ClassMethodActor classMethodActor) {
        final AMD64OptimizedTargetMethod targetMethod = new AMD64OptimizedTargetMethod(classMethodActor);
        notifyAllocation(targetMethod);
        return targetMethod;
    }

    @Override
    protected EirTargetEmitter createEirTargetEmitter(EirMethod eirMethod) {
        final boolean requiresAdapter = (!(eirMethod.isTemplate() || eirMethod.abi().targetABI().callEntryPoint().equals(C_ENTRY_POINT))) && compilerScheme().vmConfiguration().jitScheme() != compilerScheme();
        AMD64AdapterFrameGenerator adapterFrameGenerator = null;
        if (requiresAdapter) {
            adapterFrameGenerator = AMD64AdapterFrameGenerator.jitToOptimizingCompilerAdapterFrameGenerator(eirMethod.classMethodActor(), eirMethod.abi());
        }
        return new AMD64EirTargetEmitter((AMD64EirABI) eirMethod.abi(), eirMethod.frameSize(), compilerScheme().vmConfiguration().safepoint(), adapterFrameGenerator);
    }

}
