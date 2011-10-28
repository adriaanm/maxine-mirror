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

import com.oracle.max.graal.nodes.calc.*;
import com.sun.cri.ci.*;
import com.sun.cri.ci.CiValue.Formatter;

public class LIRCondition extends LIRInstruction {

    /**
     * The condition of this instruction.
     */
    public final Condition condition;

    /**
     * For floating point conditions only.
     */
    public final boolean unorderedIsTrue;

    public LIRCondition(LIROpcode opcode, CiValue result, LIRDebugInfo info, boolean hasCall, Condition condition, boolean unorderedIsTrue, CiValue[] inputs, CiValue[] temps) {
        super(opcode, result, info, hasCall, inputs, temps);
        this.condition = condition;
        this.unorderedIsTrue = unorderedIsTrue;
    }

    /**
     * Prints this instruction.
     */
    @Override
    public String operationString(Formatter operandFmt) {
        return condition.toString() + " " + super.operationString(operandFmt);
    }
}

