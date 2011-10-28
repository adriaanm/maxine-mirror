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
package com.oracle.max.graal.compiler.lir;

import com.sun.cri.ci.*;

public class LIRAddressInstruction extends LIRInstruction {

    /**
     * The operand type of the move. Since this can be Byte, Short, ... the kind of the
     * input or result operand is not enough to generate the correct code for moves.
     */
    public final CiKind kind;

    public final CiAddress.Scale addrScale;

    public final int addrDisplacement;

    public LIRAddressInstruction(LIROpcode opcode, CiValue result, LIRDebugInfo info, CiKind kind, CiAddress.Scale addrScale, int addrDisplacement, CiValue...opr) {
        super(opcode, result, info, opr);
        this.kind = kind;
        this.addrScale = addrScale;
        this.addrDisplacement = addrDisplacement;
    }

    public CiAddress createAddress(int baseOpd, int indexOpd) {
        return new CiAddress(kind, input(baseOpd), input(indexOpd), addrScale, addrDisplacement);
    }
}