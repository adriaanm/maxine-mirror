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
/*VCSID=fc6c7bc4-0e06-495b-aaec-d2da9b2e9787*/
package com.sun.max.jdwp.generate;

import java.io.*;

/**
 * @author JDK7: jdk/make/tools/src/build/tools/jdwpgen
 * @author Thomas Wuerthinger
 */
public abstract class AbstractTypeNode extends AbstractNamedNode implements TypeNode {

    abstract String docType();

    // public abstract void genJavaWrite(PrintWriter writer, int depth, String writeLabel);
    // public abstract void genJavaToString(PrintWriter writer, int depth, String writeLabel);

    abstract String javaRead();

    @Override
    public String javaType() {
        return docType(); // default
    }

    @Override
    public void genJavaRead(PrintWriter writer, int depth, String readLabel) {
        indent(writer, depth);
        writer.print(readLabel);
        writer.print(" = ");
        writer.print(javaRead());
        writer.println(";");
    }

    public void genJavaDeclaration(PrintWriter writer, int depth) {
        writer.println();
        indent(writer, depth);
        writer.print("public ");
        writer.print(javaType());
        writer.print(" " + fieldName());
        writer.println(";");
    }

    public String javaParam() {
        return javaType() + " " + name();
    }
}
