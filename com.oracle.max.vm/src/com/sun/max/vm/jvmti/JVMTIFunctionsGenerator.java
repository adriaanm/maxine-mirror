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
package com.sun.max.vm.jvmti;

import static com.sun.max.vm.jni.JniFunctionsGenerator.Customizer;
import static com.sun.max.vm.jvmti.JVMTIEnvNativeStruct.*;

import com.sun.max.annotate.*;
import com.sun.max.vm.jni.*;

@HOSTED_ONLY
public class JVMTIFunctionsGenerator {

    private static final String PHASES = "// PHASES: ";
    private static final String NULLCHECK = "// NULLCHECK: ";
    private static final String TYPECHECK = "// TYPECHECK: ";
    private static final String CAPABILITIES = "// CAPABILITIES: ";
    private static final String INDENT8 = "        ";
    private static final String INDENT12 = INDENT8 + "    ";
    private static final String INDENT16 = INDENT12 + "    ";
    private static final String FIRST_LINE_INDENT = INDENT8;

    public static class JVMTICustomizer extends Customizer {
        @Override
        public String customizeBody(String line) {
            // a 4 space indent has already been appended
            String result = customizePhases(line);
            if (result != null) {
                return result;
            }
            result = customizeNullCheck(line);
            if (result != null) {
                return result;
            }
            result = customizeTypeCheck(line);
            if (result != null) {
                return result;
            }
            result = customizeCapabilities(line);
            if (result != null) {
                return result;
            }
            return line;
        }

        /**
         * Check for a PHASES comment. If found generate the check against the current phase.
         * @param line
         * @return null for no change or the string to replace the line
         */
        private String customizePhases(String line) {
            String[] tagArgs = getTagArgs(line, PHASES);
            if (tagArgs == null) {
                return null;
            }
            if (tagArgs[0].equals("ANY")) {
                return null;
            }
            StringBuilder sb = new StringBuilder(FIRST_LINE_INDENT);
            sb.append("if (!(");
            for (int i = 0; i < tagArgs.length; i++) {
                if (i > 0) {
                    sb.append(" || ");
                }
                sb.append("phase == JVMTI_PHASE_");
                sb.append(tagArgs[i]);
            }
            sb.append(")");
            return closeCheck(sb, "WRONG_PHASE");
        }

        private String customizeNullCheck(String line) {
            String[] tagArgs = getTagArgs(line, NULLCHECK);
            if (tagArgs == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder(FIRST_LINE_INDENT);
            sb.append("if (");
            for (int i = 0; i < tagArgs.length; i++) {
                if (i > 0) {
                    sb.append(" || ");
                }
                sb.append(tagArgs[i]);
                sb.append(".isZero()");
            }
            return closeCheck(sb, "NULL_POINTER");
        }

        private String customizeTypeCheck(String line) {
            String[] tagArgs = getTagArgs(line, TYPECHECK);
            if (tagArgs == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder(FIRST_LINE_INDENT);
            sb.append("try {\n");
            for (int i = 0; i < tagArgs.length; i++) {
                String[] tagParts = tagArgs[i].split("=");
                String className = tagParts[1];
                String varName = tagParts[0];
                sb.append(INDENT16);
                sb.append(varName);
                sb.append("As");
                sb.append(className);
                sb.append(" = ");
                sb.append("(");
                sb.append(className);
                sb.append(") ");
                sb.append(varName);
                sb.append(".unhand();\n");
                sb.append(INDENT12);
                sb.append("} catch (ClassCastException ex) {\n");
                sb.append(INDENT16);
                sb.append("return JVMTI_ERROR_INVALID_");
                sb.append(invalidName(className).toUpperCase());
                sb.append(";\n");
                sb.append(INDENT12);
                sb.append("}");
            }
            return sb.toString();
        }

        private String customizeCapabilities(String line) {
            String[] tagArgs = getTagArgs(line, CAPABILITIES);
            if (tagArgs == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder(FIRST_LINE_INDENT);
            sb.append("if (!(");
            for (int i = 0; i < tagArgs.length; i++) {
                if (i > 0) {
                    sb.append(" || ");
                }
                sb.append(tagArgs[i]);
                sb.append(".get(CAPABILITIES.getPtr(env))");
            }
            sb.append(")");
            return closeCheck(sb, "MUST_POSSESS_CAPABILITY");
        }

        private String invalidName(String className) {
            if (className.equals("Thread") || className.equals("Class"))  {
                return className;
            } else {
                return "Object";
            }
        }

        @Override
        public String customizeHandler(String returnStatement) {
            // any failure just means an internal error
            return "            return JVMTI_ERROR_INTERNAL;";
        }
    }

    private static String[] getTagArgs(String line, String tag) {
        int index = line.indexOf(tag);
        if (index < 0) {
            return null;
        }
        String nullCheckList = line.substring(index + tag.length());
        return nullCheckList.split(",");
    }

    private static String closeCheck(StringBuilder sb, String error) {
        sb.append(") {\n");

        sb.append(INDENT16 + "return JVMTI_ERROR_");
        sb.append(error);
        sb.append(";\n" + INDENT12 + "}");
        return sb.toString();
    }


    public static void main(String[] args) throws Exception {
        boolean updated = false;
        if (JniFunctionsGenerator.generate(false, JVMTIFunctionsSource.class, JVMTIFunctions.class, new JVMTICustomizer())) {
            System.out.println("Source for " + JVMTIFunctions.class + " was updated");
            updated = true;
        }
        if (updated) {
            System.exit(1);
        }

    }

}
