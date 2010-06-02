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
package com.sun.max.vm.classfile;

import java.io.*;

import com.sun.max.annotate.*;
import com.sun.max.program.*;
import com.sun.max.vm.bytecode.*;
import com.sun.max.vm.classfile.constant.*;

/**
 * "Code" attributes in class files, see #4.7.3.
 *
 * @author Bernd Mathiske
 * @author Doug Simon
 * @author David Liu
 */
public final class CodeAttribute {

    public static final ExceptionHandlerEntry[] NO_EXCEPTION_HANDLER_TABLE = ExceptionHandlerEntry.NONE;

    @INSPECTED
    public final ConstantPool constantPool;
    public final char maxStack;
    public final char maxLocals;

    @INSPECTED
    private final byte[] code;

    private StackMapTable stackMapTableAttribute;
    private final byte[] encodedData;
    private final int exceptionHandlerTableOffset;
    private final int lineNumberTableOffset;
    private final int localVariableTableOffset;
    private LineNumberTable lineNumberTable;

    public CodeAttribute(ConstantPool constantPool,
                    byte[] code,
                    char maxStack,
                    char maxLocals,
                    ExceptionHandlerEntry[] exceptionHandlerTable,
                    LineNumberTable lineNumberTable,
                    LocalVariableTable localVariableTable,
                    StackMapTable stackMapTable) {
        this.constantPool = constantPool;
        this.code = code;
        this.maxStack = maxStack;
        this.maxLocals = maxLocals;
        this.stackMapTableAttribute = stackMapTable;

        final ByteArrayOutputStream encodingStream = new ByteArrayOutputStream();
        final DataOutputStream dataOutputStream = new DataOutputStream(encodingStream);

        int exceptionHandlerTableOff = -1;
        int lineNumberTableOff = -1;
        int localVariableTableOff = -1;

        try {
            dataOutputStream.write(code);
            if (exceptionHandlerTable.length != 0) {
                exceptionHandlerTableOff = encodingStream.size();
                ExceptionHandlerEntry.encode(exceptionHandlerTable, dataOutputStream);
            }
            if (!lineNumberTable.isEmpty()) {
                lineNumberTableOff = encodingStream.size();
                lineNumberTable.encode(dataOutputStream);
            }
            if (!localVariableTable.isEmpty()) {
                localVariableTableOff = encodingStream.size();
                localVariableTable.encode(dataOutputStream);
            }
        } catch (IOException e) {
            ProgramError.unexpected(e);
        }

        this.exceptionHandlerTableOffset = exceptionHandlerTableOff;
        this.lineNumberTableOffset = lineNumberTableOff;
        this.localVariableTableOffset = localVariableTableOff;
        encodedData = encodingStream.toByteArray();

    }

    static void writeCharArray(DataOutputStream dataOutputStream, char[] buf) throws IOException {
        assert buf.length <= Short.MAX_VALUE;
        dataOutputStream.writeShort(buf.length);
        for (char c : buf) {
            dataOutputStream.writeChar(c);
        }
    }

    static char[] readCharArray(DataInputStream dataInputStream) throws IOException {
        final int length = dataInputStream.readUnsignedShort();
        assert length != 0;
        final char[] buf = new char[length];
        for (int i = 0; i != length; ++i) {
            buf[i] = dataInputStream.readChar();
        }
        return buf;
    }

    public byte[] code() {
        return code;
    }

    public byte[] encodedData() {
        return encodedData;
    }

    private DataInputStream encodedData(int offset) {
        return new DataInputStream(new ByteArrayInputStream(encodedData, offset, encodedData.length - offset));
    }

    /**
     * Gets the exception handler table as an array of triplets (start bci, end bci, handler bci).
     *
     * @return {@code null} if this code attribute has no exception handlers
     */
    public int[] exceptionHandlerPositions() {
        if (exceptionHandlerTableOffset == -1) {
            return null;
        }
        try {
            return ExceptionHandlerEntry.decodeHandlerPositions(encodedData(exceptionHandlerTableOffset));
        } catch (IOException e) {
            throw ProgramError.unexpected(e);
        }
    }

    public ExceptionHandlerEntry[] exceptionHandlerTable() {
        try {
            return exceptionHandlerTableOffset == -1 ? ExceptionHandlerEntry.NONE : ExceptionHandlerEntry.decode(encodedData(exceptionHandlerTableOffset));
        } catch (IOException e) {
            throw ProgramError.unexpected(e);
        }
    }

    public LineNumberTable lineNumberTable() {
        if (lineNumberTable == null) {
            // cache the line number table
            try {
                lineNumberTable = lineNumberTableOffset == -1 ? LineNumberTable.EMPTY : LineNumberTable.decode(encodedData(lineNumberTableOffset));
            } catch (IOException e) {
                throw ProgramError.unexpected(e);
            }
        }
        return lineNumberTable;
    }

    public LocalVariableTable localVariableTable() {
        try {
            return localVariableTableOffset == -1 ? LocalVariableTable.EMPTY : LocalVariableTable.decode(encodedData(localVariableTableOffset));
        } catch (IOException e) {
            throw ProgramError.unexpected(e);
        }
    }

    /**
     * @return null if there is no stack map table associated with this code attribute
     */
    public StackMapTable stackMapTable() {
        return stackMapTableAttribute;
    }

    public void setStackMapTableAttribute(StackMapTable stackMapTable) {
        stackMapTableAttribute = stackMapTable;
    }

    @Override
    public String toString() {
        try {
            return CodeAttributePrinter.toString(this);
        } catch (Exception e) {
            return super.toString() + "[" + e + "]";
        }
    }
}
