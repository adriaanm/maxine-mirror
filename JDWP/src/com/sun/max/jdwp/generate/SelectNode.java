/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.max.jdwp.generate;

import java.io.*;
import java.util.*;

/**
 * @author JDK7: jdk/make/tools/src/build/tools/jdwpgen
 * @author Thomas Wuerthinger
 */
class SelectNode extends AbstractGroupNode implements TypeNode {

    private TypeNode typeNode;

    @Override
    void prune() {
        super.prune();
        final Iterator it = components.iterator();

        if (it.hasNext()) {
            final Node typeNode = (Node) it.next();

            if (typeNode.javaType().equals("byte") || typeNode.javaType().equals("int")) {
                this.typeNode = (AbstractSimpleTypeNode) typeNode;
                it.remove();
            } else {
                error("Select must be based on 'int' or 'byte'");
            }
        } else {
            error("empty");
        }
    }

    @Override
    void constrain(Context ctx) {
        super.constrain(ctx);
        if (components.size() < 2) {
            error("Select must have at least two options");
        }
    }

    @Override
    void constrainComponent(Context ctx, Node node) {
        node.constrain(ctx);
        if (!(node instanceof AltNode)) {
            error("Select must consist of selector followed by Alt items");
        }
    }

    String commonBaseClass() {
        return name() + "Common";
    }

    private String commonVar() {
        return "a" + commonBaseClass();
    }

    private String commonVarField() {
        return commonVar();
    }

    @Override
    void genJavaClassSpecifics(PrintWriter writer, int depth) {
        indent(writer, depth);
        writer.println("public abstract static class " + commonBaseClass() + " {");
        indent(writer, depth + 1);
        writer.println("public abstract void write(JDWPOutputStream ps) throws java.io.IOException;");
        indent(writer, depth + 1);
        writer.println("public abstract void read(JDWPInputStream ps) throws java.io.IOException, JDWPException;");
        if (!context.isWritingCommand()) {
            indent(writer, depth + 1);
            writer.println("public abstract " + typeNode.javaParam() + "();");
        }
        indent(writer, depth);
        writer.println("}");
        typeNode.genJavaDeclaration(writer, depth);
        indent(writer, depth);
        writer.println("public " + commonBaseClass() + " " + commonVarField() + ";");
        super.genJavaClassSpecifics(writer, depth);
    }

    @Override
    void genJavaClassBodyComponents(PrintWriter writer, int depth) {
        // don't naively include alt components
    }

    @Override
    void genJavaReadingClassBody(PrintWriter writer, int depth, String className) {
        writer.println();
        indent(writer, depth);
        writer.print("public " + className + "(" + typeNode.javaParam() + ", ");
        writer.print(commonBaseClass() + " " + commonVar());
        writer.println(") {");
        indent(writer, depth + 1);
        writer.println("this." + typeNode.fieldName() + " = " + typeNode.name() + ";");
        indent(writer, depth + 1);
        writer.println("this." + commonVarField() + " = " + commonVar() + ";");
        indent(writer, depth);
        writer.println("}");

        writer.println();
        indent(writer, depth);
        writer.print("public " + className + "(");
        writer.println(") {");
        indent(writer, depth);
        writer.println("}");

        indent(writer, depth);
        writer.println("public void read(JDWPInputStream ps) throws java.io.IOException, JDWPException {");
        genJavaReads(writer, depth + 1);
        indent(writer, depth);
        writer.println("}");

        indent(writer, depth);
        writer.println("public void write(JDWPOutputStream ps) throws java.io.IOException {");
        genJavaWrites(writer, depth + 1);
        indent(writer, depth);
        writer.println("}");

        indent(writer, depth);
        writer.println("@Override");
        indent(writer, depth);
        writer.println("public String toString() {");
        indent(writer, depth + 1);
        writer.println("final StringBuilder stringBuilder = new StringBuilder();");
        genJavaToString(writer, depth + 1);
        indent(writer, depth + 1);
        writer.println("return stringBuilder.toString();");
        indent(writer, depth);
        writer.println("}");
    }

    @Override
    void genJavaWritingClassBody(PrintWriter writer, int depth, String className) {
        writer.println();
        indent(writer, depth);
        writer.print("public " + className + "(" + typeNode.javaParam() + ", ");
        writer.print(commonBaseClass() + " " + commonVar());
        writer.println(") {");
        indent(writer, depth + 1);
        writer.println("this." + typeNode.fieldName() + " = " + typeNode.name() + ";");
        indent(writer, depth + 1);
        writer.println("this." + commonVarField() + " =" + commonVar() + ";");
        indent(writer, depth);
        writer.println("}");
    }

    @Override
    void genJavaWrites(PrintWriter writer, int depth) {
        typeNode.genJavaWrite(writer, depth, typeNode.fieldName());
        indent(writer, depth);
        writer.println(commonVarField() + ".write(ps);");
    }

    @Override
    void genJavaToString(PrintWriter writer, int depth) {
        typeNode.genJavaToString(writer, depth, typeNode.fieldName());
        indent(writer, depth);
        writer.println("stringBuilder.append(" + commonVarField() + ");");
    }

    @Override
    void genJavaReads(PrintWriter writer, int depth) {
        typeNode.genJavaRead(writer, depth, typeNode.fieldName());
        indent(writer, depth);
        writer.println("switch (" + typeNode.fieldName() + ") {");
        for (final Iterator it = components.iterator(); it.hasNext();) {
            final AltNode alt = (AltNode) it.next();
            alt.genJavaReadsSelectCase(writer, depth + 1, commonVarField());
        }
        indent(writer, depth);
        writer.println("}");
        indent(writer, depth);
        writer.println(commonVarField() + ".read(ps);");
    }

    @Override
    public void genJavaDeclaration(PrintWriter writer, int depth) {
        typeNode.genJavaDeclaration(writer, depth);
        super.genJavaDeclaration(writer, depth);
    }

    @Override
    public String javaParam() {
        return typeNode.javaParam() + ", " + name() + " a" + name();
    }

    public TypeNode typeNode() {
        return typeNode;
    }
}
