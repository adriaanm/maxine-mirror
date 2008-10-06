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
/*VCSID=a17c8c88-6eed-4aeb-a26f-3cd695fb9197*/
package com.sun.max.asm.gen.risc.sparc;

import static com.sun.max.asm.gen.risc.sparc.SPARCFields.*;

/**
 * @author Bernd Mathiske
 * @author Dave Ungar
 * @author Adam Spitz
 */
class MemorySynchronization extends SPARCInstructionDescriptionCreator {

    private void create_A32() {
        define("membar", op(0x2), _res_29_25, op3(0x28), bits_18_14(0xf), i(1), _res_12_7, _membarMask);
    }

    private void create_A51() {
        if (assembly().generatingDeprecatedInstructions()) {
            define("stbar", op(0x2), _res_29_25, op3(0x28), bits_18_14(0xf), i(0), _res_12_0);
        }
    }

    MemorySynchronization(SPARCTemplateCreator templateCreator) {
        super(templateCreator);

        setCurrentArchitectureManualSection("A.32");
        create_A32();

        setCurrentArchitectureManualSection("A.51");
        create_A51();
    }
}
