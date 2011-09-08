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
package com.oracle.max.vma.tools.gen.vma;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import com.oracle.max.vm.ext.vma.*;
import com.sun.max.annotate.*;
import com.sun.max.vm.t1x.*;

/**
 * Helper methods for auto-generation of code related to the advice interface.
 * We use {@link T1XTemplateGenerator} for basic support methods.
 */

@HOSTED_ONLY
public class AdviceGeneratorHelper {

    public static final VMABytecodes[] VMABytecodeValues = VMABytecodes.values();
    /**
     * Map from actual bytecode encoding value to {@link VMABytecode}.
     */
    public static Map<Integer, VMABytecodes> codeMap = new HashMap<Integer, VMABytecodes>();

    static {
        for (VMABytecodes bc : VMABytecodeValues) {
            codeMap.put(bc.code, bc);
        }
    }

    public static T1XTemplateGenerator t1xTemplateGen;
    public static PrintStream out = System.out;

    public static void generateAutoComment() {
        t1xTemplateGen.generateAutoComment();
    }

    public static void createGenerator(Class<?> klass) {
        t1xTemplateGen = new T1XTemplateGenerator(klass, out);

    }

    /**
     * Generates the name/signature definition for given method, returning a count of the
     * number of arguments.
     * @param m
     * @param modifiers TODO
     * @return
     */
    public static int generateSignature(Method m, String modifiers) {
        out.print("    public ");
        if (modifiers != null) {
            out.printf("%s ", modifiers);
        }
        out.printf("void %s(", m.getName());
        int count = 1;
        for (Class<?> klass : m.getParameterTypes()) {
            if (count != 1) {
                out.print(", ");
            }
            out.printf("%s %s", klass.getSimpleName(), "arg" + count);
            count++;
        }
        out.print(")");
        return count;
    }

    /**
     * Generate the args for a method invocation and closing bracket and newline.
     * @param argCount
     */
    public static void generateInvokeArgs(int argCount) {
        for (int count = 1; count < argCount; count++) {
            if (count != 1) {
                out.print(", ");
            }
            out.printf("%s", "arg" + count);
        }
        out.printf(");%n");
    }

    public static String getLastParameterName(Method m) {
        Class<?>[] types = m.getParameterTypes();
        if (types.length == 0) {
            return null;
        }
        return types[types.length - 1].getSimpleName();
    }

    public static String getNthParameterName(Method m, int argc) {
        Class<?>[] types = m.getParameterTypes();
        if (types.length == 0 || argc > types.length) {
            return null;
        }
        return types[argc - 1].getSimpleName();

    }


}
