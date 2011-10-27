/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.max.asm.*;
import com.oracle.max.graal.compiler.asm.*;

public interface LIROpcode<A extends AbstractAssembler, I extends LIRInstruction> {
    void emitCode(TargetMethodAssembler<A> tasm, I op);

    /**
     * Marker interface for the register allocator: If an operation implements this interface,
     * all operands can be a split slot and need not be loaded to a register.
     */
    public interface AllOperandsCanBeMemory {
    }

    /**
     * Marker interface for the register allocator: If an operation implements this interface,
     * the second operand can be a split slot and needs not be loaded to a register.
     */
    public interface SecondOperandCanBeMemory {
    }

    /**
     * Marker interface for the register allocator: If an operation implements this interface,
     * the register assigned to the first operand should be assigned to the result operand.
     */
    public interface FirstOperandRegisterHint {
    }

    /**
     * Marker interface for the register allocator: If an operation implements this interface,
     * the register assigned to the second operand should be assigned to the result operand.
     */
    public interface SecondOperandRegisterHint {
    }
}
