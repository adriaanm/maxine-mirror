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
/*VCSID=2347d87c-c14d-49d8-8f90-9f452abc8220*/
package com.sun.max.vm.compiler.dir;

import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.type.*;

/**
 * An abstract reference to another method from within DIR.
 * 
 * @author Bernd Mathiske
 */
public class DirMethodValue extends DirValue {

    private final ClassMethodActor _classMethodActor;

    public ClassMethodActor classMethodActor() {
        return _classMethodActor;
    }

    public DirMethodValue(ClassMethodActor classMethodActor) {
        super();
        _classMethodActor = classMethodActor;
    }

    public Kind kind() {
        return Kind.WORD;
    }

    public boolean isConstant() {
        return true;
    }

    @Override
    public int hashCodeForBlock() {
        return _classMethodActor.name().hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof DirMethodValue) {
            final DirMethodValue dirMethodValue = (DirMethodValue) other;
            return _classMethodActor == dirMethodValue._classMethodActor;
        }
        return false;
    }

    @Override
    public String toString() {
        return _classMethodActor.name().toString();
    }
}
