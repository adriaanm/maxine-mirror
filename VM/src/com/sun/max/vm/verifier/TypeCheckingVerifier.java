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
/*VCSID=a8b812f2-8c84-489d-a744-a63547900289*/
package com.sun.max.vm.verifier;

import com.sun.max.profile.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.classfile.*;

/**
 * @author David Liu
 * @author Doug Simon
 */
public class TypeCheckingVerifier extends ClassVerifier {

    private final boolean _fallBackToTypeInferencing;

    public TypeCheckingVerifier(ClassActor classActor, boolean fallBackToTypeInferencing) {
        super(classActor);
        if (classActor.majorVersion() < 50) {
            throw new IllegalArgumentException("Cannot perform type checking verification on class " + classActor.name() + " with version number less than 50: " + classActor.majorVersion());
        }
        _fallBackToTypeInferencing = fallBackToTypeInferencing;
    }

    @Override
    public synchronized void verify() {
        try {
            super.verify();
        } catch (VerifyError verifyError) {
            if (classActor().majorVersion() == 50 && _fallBackToTypeInferencing) {
                final TypeInferencingVerifier typeInferencingVerifier = new TypeInferencingVerifier(classActor());
                typeInferencingVerifier.setVerbose(verbose());
                typeInferencingVerifier.verify();
            }
            throw verifyError;
        }
    }

    @Override
    public synchronized CodeAttribute verify(ClassMethodActor classMethodActor, CodeAttribute codeAttribute) {
        Metrics.increment("TypeCheckingVerifications");
        new TypeCheckingMethodVerifier(this, classMethodActor, codeAttribute).verify();
        return codeAttribute;
    }
}
