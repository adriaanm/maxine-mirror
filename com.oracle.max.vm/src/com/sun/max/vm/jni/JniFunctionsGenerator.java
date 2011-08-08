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
package com.sun.max.vm.jni;

import java.io.*;
import java.util.regex.*;

import com.sun.max.annotate.*;
import com.sun.max.ide.*;
import com.sun.max.io.*;

/**
 * This class implements the {@linkplain #generate process} by which the source in {@link JniFunctionsSource JniFunctionsSource.java}
 * and {@link JmmFunctionsSource JmmFunctionsSource.java} is pre-processed to produce source in
 * {@link JniFunctions JniFunctions.java} and {@link JmmFunctions JmmFunctions.java}.
 * The generated source is delineated by the following lines:
 * <pre>
 * // START GENERATED CODE
 *
 * ...
 *
 * // END GENERATED CODE
 * </pre>
 */
@HOSTED_ONLY
public class JniFunctionsGenerator {
    /**
     * Flag that controls if timing / counting code is inserted into the generated JNI functions.
     */
    private static final boolean TIME_JNI_FUNCTIONS = false;

    private static final String JNI_FUNCTION_ANNOTATION = "@VM_ENTRY_POINT";
    static final int BEFORE_FIRST_JNI_FUNCTION = -1;
    static final int BEFORE_JNI_FUNCTION = 0;
    static final int BEFORE_PROLOGUE = 1;

    /**
     * An extension of {@link BufferedReader} that tracks the line number.
     */
    static class LineReader extends BufferedReader {
        int lineNo;
        String line;
        final File file;
        public LineReader(File file) throws FileNotFoundException {
            super(new FileReader(file));
            this.file = file;
        }

        @Override
        public String readLine() throws IOException {
            lineNo++;
            line = super.readLine();
            return line;
        }

        public String where() {
            return file.getAbsolutePath() + ":" + lineNo;
        }

        public void check(boolean condition, String errorMessage) {
            if (!condition) {
                throw new InternalError(String.format("%n" + where() + ": " + errorMessage + "%n" + line));
            }
        }
    }

    static class JniFunctionDeclaration {
        static Pattern PATTERN = Pattern.compile("    private static (native )?(\\w+) (\\w+)\\(([^)]*)\\).*");

        String line;
        String returnType;
        boolean isNative;
        String name;
        String parameters;
        String arguments;
        String sourcePos;

        static JniFunctionDeclaration parse(String line, String sourcePos) {
            Matcher m = PATTERN.matcher(line);
            if (!m.matches()) {
                return null;
            }

            JniFunctionDeclaration decl = new JniFunctionDeclaration();
            decl.line = line;
            decl.isNative = m.group(1) != null;
            decl.returnType = m.group(2);
            decl.name = m.group(3);
            decl.parameters = m.group(4);

            String[] parameters = decl.parameters.split(",\\s*");
            StringBuilder arguments = new StringBuilder();
            for (int i = 0; i < parameters.length; ++i) {
                if (arguments.length() != 0) {
                    arguments.append(", ");
                }
                arguments.append(parameters[i].substring(parameters[i].lastIndexOf(' ') + 1));
            }
            decl.arguments = arguments.toString();
            decl.sourcePos = sourcePos;
            return decl;
        }

        public String declareHelper() {
            int index = line.indexOf('(');
            return line.substring(0, index) + '_' + line.substring(index);
        }

        public String callHelper() {
            return name + "_(" + arguments + ")";
        }
    }

    /**
     * Inserts or updates generated source into {@code target}. The generated source is derived from
     * {@code source} and is delineated in {@code target} by the following lines:
     * <pre>
     * // START GENERATED CODE
     *
     * ...
     *
     * // END GENERATED CODE
     * </pre>

     *
     * @param checkOnly if {@code true}, then {@code target} is not updated; the value returned by this method indicates
     *            whether it would have been updated were this argument {@code true}
     * @return {@code true} if {@code target} was modified (or would have been if {@code checkOnly} was {@code false}); {@code false} otherwise
     */
    static boolean generate(boolean checkOnly, Class source, Class target) throws Exception {
        File base = new File(JavaProject.findWorkspaceDirectory(), "com.oracle.max.vm/src");
        File inputFile = new File(base, source.getName().replace('.', File.separatorChar) + ".java").getAbsoluteFile();
        File outputFile = new File(base, target.getName().replace('.', File.separatorChar) + ".java").getAbsoluteFile();

        LineReader lr = new LineReader(inputFile);
        String line = null;

        int state = BEFORE_FIRST_JNI_FUNCTION;
        Writer writer = new StringWriter();
        PrintWriter out = new PrintWriter(writer);

        while ((line = lr.readLine()) != null) {

            // Stop once the closing brace for the source class is found
            if (line.equals("}")) {
                break;
            }

            if (line.trim().equals(JNI_FUNCTION_ANNOTATION)) {
                lr.check(state == BEFORE_JNI_FUNCTION || state == BEFORE_FIRST_JNI_FUNCTION, "Illegal state (" + state + ") when parsing @JNI_FUNCTION");
                if (state == BEFORE_FIRST_JNI_FUNCTION) {
                    out.println();
                    out.println("    private static final boolean INSTRUMENTED = " + TIME_JNI_FUNCTIONS + ";");
                    out.println();
                }
                state = BEFORE_PROLOGUE;
                out.println(line);
                continue;
            }

            if (state == BEFORE_PROLOGUE) {

                JniFunctionDeclaration decl = JniFunctionDeclaration.parse(line, inputFile.getName() + ":" + lr.lineNo);
                lr.check(decl != null, "JNI function declaration does not match pattern \"" + JniFunctionDeclaration.PATTERN + "\"");

                out.println(line);
                out.println("        // Source: " + decl.sourcePos);

                if (!decl.isNative) {
                    StringBuilder bodyBuffer = new StringBuilder();
                    String body = null;
                    while ((line = lr.readLine()) != null) {
                        if (line.equals("    }")) {
                            body = bodyBuffer.toString();
                            break;
                        }
                        bodyBuffer.append("    ").append(line).append("\n");
                    }

                    if (body == null) {
                        assert false;
                    }

                    if (decl.returnType.equals("void")) {
                        generateVoidFunction(out, decl, body);
                    } else {
                        generateNonVoidFunction(out, decl, body);
                    }
                }
                state = BEFORE_JNI_FUNCTION;
                continue;
            }

            if (state == BEFORE_FIRST_JNI_FUNCTION) {
                continue;
            }
            out.println(line);
        }

        writer.close();
        return Files.updateGeneratedContent(outputFile, ReadableSource.Static.fromString(writer.toString()), "// START GENERATED CODE", "// END GENERATED CODE", checkOnly);
    }

    private static void generateNonVoidFunction(PrintWriter out, JniFunctionDeclaration decl, String body) {
        final String errReturnValue;
        if (decl.returnType.equals("boolean")) {
            errReturnValue = "false";
        } else if (decl.returnType.equals("char")) {
            errReturnValue = " (char) JNI_ERR";
        } else if (decl.returnType.equals("int") ||
                   decl.returnType.equals("byte") ||
                   decl.returnType.equals("char") ||
                   decl.returnType.equals("short") ||
                   decl.returnType.equals("float") ||
                   decl.returnType.equals("long") ||
                   decl.returnType.equals("double")) {
            errReturnValue = "JNI_ERR";
        } else {
            errReturnValue = "as" + decl.returnType + "(0)";
        }

        generateFunction(out, decl, body, "return " + errReturnValue + ";");
    }

    private static void generateVoidFunction(PrintWriter out, JniFunctionDeclaration decl, String body) {
        generateFunction(out, decl, body, null);
    }

    private static void generateFunction(PrintWriter out, JniFunctionDeclaration decl, String body, String returnStatement) {
        boolean insertTimers = TIME_JNI_FUNCTIONS && decl.name != null;

        out.println("        Pointer anchor = prologue(env, \"" + decl.name + "\");");
        if (insertTimers) {
            out.println("        long startTime = System.nanoTime();");
        }
        out.println("        try {");
        out.print(body);
        out.println("        } catch (Throwable t) {");
        out.println("            VmThread.fromJniEnv(env).setJniException(t);");
        if (returnStatement != null) {
            out.println("            " + returnStatement);
        }
        out.println("        } finally {");
        if (insertTimers) {
            out.println("            TIMER_" + decl.name + " += System.nanoTime() - startTime;");
            out.println("            COUNTER_" + decl.name + "++;");
        }
        out.println("            epilogue(anchor, \"" + decl.name + "\");");
        out.println("        }");
        out.println("    }");
        if (insertTimers) {
            out.println("    public static long COUNTER_" + decl.name + ";");
            out.println("    public static long TIMER_" + decl.name + ";");
        }
    }

    /**
     * Command line interface for running the source code generator.
     * If the generation process modifies the existing source, then the exit
     * code of the JVM process will be non-zero.
     */
    public static void main(String[] args) throws Exception {
        boolean updated = false;
        if (generate(false, JniFunctionsSource.class, JniFunctions.class)) {
            System.out.println("Source for " + JniFunctions.class + " was updated");
            updated = true;
        }
        if (generate(false, JmmFunctionsSource.class, JmmFunctions.class)) {
            System.out.println("Source for " + JmmFunctions.class + " was updated");
            updated = true;
        }
        if (updated) {
            System.exit(1);
        }
    }
}
