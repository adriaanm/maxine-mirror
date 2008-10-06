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
/*VCSID=11f7b7c4-8414-4746-bca2-8750ecc19aa1*/

package com.sun.max.vm.compiler.eir;

import java.lang.reflect.*;

/**
 * @author Michael Bebenita
 */
public final class EirGuardpoint<EirInstructionVisitor_Type extends EirInstructionVisitor, EirTargetEmitter_Type extends EirTargetEmitter>
                      extends EirStop<EirInstructionVisitor_Type, EirTargetEmitter_Type> {

    public EirGuardpoint(EirBlock block) {
        super(block);
    }

    @Override
    public String toString() {
        return super.toString() + " " + javaFrameDescriptor();
    }

    @Override
    public void acceptVisitor(EirInstructionVisitor_Type visitor) throws InvocationTargetException {
        visitor.visit(this);
    }

    @Override
    public void emit(EirTargetEmitter_Type emitter) {
        emitter.addGuardpoint(this);
    }
}
