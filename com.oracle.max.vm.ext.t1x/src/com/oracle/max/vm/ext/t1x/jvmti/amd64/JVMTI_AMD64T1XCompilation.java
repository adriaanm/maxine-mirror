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
package com.oracle.max.vm.ext.t1x.jvmti.amd64;

import static com.oracle.max.vm.ext.t1x.T1XTemplateTag.*;

import com.oracle.max.vm.ext.t1x.*;
import com.oracle.max.vm.ext.t1x.amd64.*;
import com.sun.cri.bytecode.*;
import com.sun.max.vm.jvmti.*;

/**
 * Custom compilation class for generating JVMTI code-related events.
 *
 * When there are any JVMTI agents that have requested <i>any</i> of the events that require
 * compiled code modifications, an instance of this class is used to compile any non-VM
 * class. During the compilation each bytecode that can generate an event (including the
 * pseudo-bytecode for method entry) is checked explicitly. If the event is not needed
 * the default compiler templates and behavior are used, otherwise the
 * JVMTI templates and behavior defined here are used.
 *
 * TODO: Since field events are specified per field by the agent, a further optimization
 * would be to determine whether the specific field being accessed is subject to a watch.
 *
 */
public class JVMTI_AMD64T1XCompilation extends AMD64T1XCompilation {
    /**
     * The specific templates to be used for processing the current bytecode,
     * based on whether there are any agents registered for associated events.
     *
     * The choice is between {@link #compiler#templates} which are JVMTI enabled
     * and (@link altT1X#compiler#templates} which are the standard templates.
     */
    private T1XTemplate[] templates;

    private final T1X defaultT1X;

    public JVMTI_AMD64T1XCompilation(T1X compiler) {
        super(compiler);
        defaultT1X = compiler.altT1X;
        templates = defaultT1X.templates;
    }

    @Override
    protected void do_methodTraceEntry() {
        if (JVMTI.byteCodeEventNeeded(-1)) {
            templates = compiler.templates;
            start(TRACE_METHOD_ENTRY);
            assignObject(0, "methodActor", method);
            finish();
        } else {
            super.do_methodTraceEntry();
        }
    }

    @Override
    protected void beginBytecode(int opcode) {
        super.beginBytecode(opcode);
        switch (opcode) {
            case Bytecodes.GETFIELD:
            case Bytecodes.GETSTATIC:
            case Bytecodes.PUTFIELD:
            case Bytecodes.PUTSTATIC:
                templates = JVMTI.byteCodeEventNeeded(opcode) ? compiler.templates : defaultT1X.templates;
                break;
            default:
        }
    }

    @Override
    protected T1XTemplate getTemplate(T1XTemplateTag tag) {
        // Use the templates chosen in beginBytecode/do_methodTraceEntry
        T1XTemplate tx1Template = templates[tag.ordinal()];
        // reset to default
        templates = defaultT1X.templates;
        return tx1Template;
    }


}
