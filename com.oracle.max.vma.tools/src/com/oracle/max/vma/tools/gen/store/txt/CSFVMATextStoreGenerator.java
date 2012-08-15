/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.vma.tools.gen.store.txt;

import static com.oracle.max.vma.tools.gen.vma.AdviceGeneratorHelper.*;

import java.lang.reflect.*;

import com.oracle.max.vm.ext.vma.store.txt.*;
import com.oracle.max.vma.tools.gen.vma.*;

public class CSFVMATextStoreGenerator {

    public static void main(String[] args) throws Exception {
        createGenerator(CSFVMATextStoreGenerator.class);
        generateAutoComment();
        for (Method m : VMATextStore.class.getMethods()) {
            if (m.getName().startsWith("advise")) {
                generate(m);
            }
        }
        AdviceGeneratorHelper.updateSource(CSFVMATextStore.class, null, false);
    }

    private static void generate(Method m) {
        final String name = m.getName();
        out.printf("    @Override%n");
        generateSignature(m, null);
        out.printf(" {%n");
        if (name.equals("adviseAfterMultiNewArray")) {
            out.printf("        ProgramError.unexpected(\"adviseAfterMultiNewArray\");%n");
            out.printf("    }%n%n");
            return;
        }
        out.printf("        del.%s(", m.getName());
        out.print("arg1, getThreadShortForm(arg2)");
        if (name.contains("GetField") || name.contains("PutField")) {
            out.print(", checkRepeatId(arg3, arg2), getClassShortForm(arg4, arg5), arg5, getFieldShortForm(arg4, arg5, arg6)");
            if (name.contains("PutField")) {
                out.print(", arg7");
            }
            out.printf(");%n");
        } else if (name.contains("GetStatic") || name.contains("PutStatic")) {
            out.print(", getClassShortForm(arg3, arg4), arg4, getFieldShortForm(arg3, arg4, arg5)");
            if (name.contains("PutStatic")) {
                out.print(", arg6");
            }
            out.printf(");%n");
        } else if (name.contains("ArrayLoad") || name.contains("ArrayStore")) {
            out.print(",  checkRepeatId(arg3, arg2), arg4");
            if (name.contains("ArrayStore")) {
                out.print(", arg5");
            }
            out.printf(");%n");
        } else if (name.contains("New")) {
            out.print(", checkRepeatId(arg3, arg2), getClassShortForm(arg4, arg5), arg5");
            if (name.contains("NewArray")) {
                out.print(", arg6");
            }
            out.printf(");%n");
        } else if (name.contains("Invoke") || name.contains("MethodEntry")) {
            out.print(", checkRepeatId(arg3, arg2), getClassShortForm(arg4, arg5), arg5, getMethodShortForm(arg4, arg5, arg6)");
            out.printf(");%n");
        } else if (name.contains("Monitor") || name.contains("Throw")) {
            out.print(", checkRepeatId(arg3, arg2)");
            if (name.contains("ReturnByThrow")) {
                out.print(", arg4");
            }
            out.printf(");%n");
        } else if (name.contains("CheckCast") || name.contains("InstanceOf")) {
            out.print(", checkRepeatId(arg3, arg2), getClassShortForm(arg4, arg5), arg5");
            out.printf(");%n");
        } else {
            Class<?>[] params = m.getParameterTypes();
            for (int argc = 3; argc <= params.length; argc++) {
                out.printf(", %s", "arg" + argc);
            }
            out.printf(");%n");
        }
        out.printf("    }%n%n");
    }

}
