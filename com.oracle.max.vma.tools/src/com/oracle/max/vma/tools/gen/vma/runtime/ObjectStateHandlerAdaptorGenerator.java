/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.vma.tools.gen.vma.runtime;

import static com.sun.max.vm.t1x.T1XTemplateGenerator.*;
import static com.oracle.max.vma.tools.gen.vma.AdviceGeneratorHelper.*;

import java.lang.reflect.*;

import com.oracle.max.vm.ext.vma.*;
import com.sun.max.annotate.*;

/**
 * Generates the rote implementations of ObjectStateHandlerAdaptor.
 * Object creation is special and requires that we assign a unique id.
 * All other methods that take objects as parameters have to be checked
 * in case their creation was not observed.
 *
 */

@HOSTED_ONLY
public class ObjectStateHandlerAdaptorGenerator {

    public static void main(String[] args) {
        setGeneratingClass(ObjectStateHandlerAdaptorGenerator.class);
        for (Method m : VMAdviceHandler.class.getMethods()) {
            String name = m.getName();
            if (name.startsWith("advise")) {
                if (name.equals("adviseGC") ||
                    name.contains("ThreadStarting") || name.contains("ThreadTerminating")) {
                    continue;
                }
                generate(m);
            }
        }
    }

    private static void generate(Method m) {
        String name = m.getName();
        generateAutoComment();
        out.printf("    @Override%n");
        generateSignature(m, null);
        out.printf(" {%n");
        if (name.equals("adviseAfterNew") || name.equals("adviseAfterNewArray")) {
            out.printf("        final Reference objRef = Reference.fromJava(arg1);%n");
            out.printf("        final Hub hub = UnsafeCast.asHub(Layout.readHubReference(objRef));%n");
            out.printf("        state.assignId(objRef);%n");
            out.printf("        checkId(hub.classActor.classLoader);%n");
        } else {
            int i = 1;
            Class<?>[] params = m.getParameterTypes();
            for (Class<?> param : params) {
                if (param == Object.class) {
                    out.printf("        checkId(arg%d);%n", i);
                }
                i++;
            }
        }
        out.printf("    }%n%n");
    }

}
