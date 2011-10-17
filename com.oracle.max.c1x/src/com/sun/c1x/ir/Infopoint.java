/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.ir;

import com.oracle.max.criutils.*;
import com.sun.c1x.value.*;
import com.sun.cri.ci.*;

/**
 * Records debug info at the current code location.
 */
public final class Infopoint extends Instruction {

    public static enum Op { SAFEPOINT_POLL, HERE, INFO, UNCOMMON_TRAP }

    public final FrameState state;

    public final Op op;

    /**
     * Creates a new Infopoint instance.
     * @param state the debug info at this instruction
     */
    public Infopoint(Op op, FrameState state) {
        super(op == Op.HERE ? CiKind.Long : CiKind.Void);
        this.op = op;
        this.state = state;
        setFlag(Flag.LiveSideEffect); // ensure this instruction is not eliminated
        if (op == Op.SAFEPOINT_POLL) {
            setFlag(Value.Flag.IsSafepointPoll);
        }
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitInfopoint(this);
    }

    @Override
    public FrameState stateBefore() {
        return state;
    }

    @Override
    public void print(LogStream out) {
        out.print(op.name());
    }
}
