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
package com.oracle.max.graal.compiler.target.amd64;

import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.graal.compiler.asm.*;
import com.oracle.max.graal.compiler.lir.*;
import com.sun.cri.ci.*;

/**
 * LIR operations that are HotSpot-specific, and should therefore be moved to a HotSpot-specific project.
 */
public class AMD64HotSpotOpcode {

    // Note: This LIR operation is just a LEA, so reusing the LEA op would be desirable.
    // However, the address that is loaded depends on the stack slot, and the stack slot numbers are
    // only fixed after register allocation when the number of spill slots is known. Therefore, the address
    // is not known when the LIR is generated.
    public enum MonitorAddressOpcode implements LIROpcode {
        MONITOR_ADDRESS;

        public LIRInstruction create(CiVariable result, final int monitorIndex) {
            return new AMD64LIRInstruction(this, result, null, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS) {
                @Override
                public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                    CiStackSlot slot = tasm.compilation.frameMap().toMonitorBaseStackAddress(monitorIndex);
                    masm.leaq(tasm.asRegister(result()), new CiAddress(slot.kind, AMD64.rsp.asValue(), slot.index() * tasm.target.arch.wordSize));
                }
            };
        }
    }
}
