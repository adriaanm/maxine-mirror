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

import static com.sun.max.vm.classfile.ErrorContext.*;

import java.io.*;
import java.util.*;

import com.sun.max.vm.bytecode.graft.*;
import com.sun.max.vm.classfile.constant.*;
import com.sun.max.vm.type.*;

/**
 * "LocalVariableTable" attributes in class files, see #4.7.9.
 *
 * @author Bernd Mathiske
 * @author Doug Simon
 */
public final class LocalVariableTable {

    /**
     * Represents a merged entry from one or more LocalVariableTables and LocalVariableTypeTables.
     */
    public static final class Entry {

        private final char startPosition;
        private final char length;
        private final char slot;
        private final char nameIndex;
        private final char descriptorIndex;
        private char signatureIndex;

        public Entry(ClassfileStream classfileStream, boolean forLocalVariableTypeTables) {
            startPosition = (char) classfileStream.readUnsigned2();
            length = (char) classfileStream.readUnsigned2();
            nameIndex = (char) classfileStream.readUnsigned2();
            if (forLocalVariableTypeTables) {
                signatureIndex = (char) classfileStream.readUnsigned2();
                descriptorIndex = 0;
            } else {
                descriptorIndex = (char) classfileStream.readUnsigned2();
            }
            slot = (char) classfileStream.readUnsigned2();
        }

        public Entry(char startPosition, char length, char slot, char nameIndex, char descriptorIndex, char signatureIndex) {
            this.startPosition = startPosition;
            this.length = length;
            this.slot = slot;
            this.nameIndex = nameIndex;
            this.descriptorIndex = descriptorIndex;
            this.signatureIndex = signatureIndex;
        }

        public int startPosition() {
            return startPosition;
        }

        public int length() {
            return length;
        }

        public int slot() {
            return slot;
        }

        public int nameIndex() {
            return nameIndex;
        }

        public int descriptorIndex() {
            return descriptorIndex;
        }

        public int signatureIndex() {
            return signatureIndex;
        }

        public Utf8Constant name(ConstantPool constantPool) {
            return constantPool.utf8At(nameIndex, "local variable name");
        }

        public TypeDescriptor descriptor(ConstantPool constantPool) {
            return JavaTypeDescriptor.parseTypeDescriptor(constantPool.utf8At(descriptorIndex, "local variable type").toString());
        }

        public Utf8Constant signature(ConstantPool constantPool) {
            return constantPool.utf8At(signatureIndex);
        }

        public void copySignatureIndex(Entry lvttEntry) {
            signatureIndex = lvttEntry.signatureIndex;
        }

        @Override
        public boolean equals(Object object) {
            if (object instanceof Entry) {
                final Entry otherEntry = (Entry) object;
                return otherEntry.startPosition == startPosition &&
                       otherEntry.length == length &&
                       otherEntry.slot == slot;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return startPosition + (length * 37) + (slot * 37);
        }

        public void verify(ConstantPool constantPool, int codeLength, int maxLocals, boolean forLVTT) {
            name(constantPool);
            if (startPosition >= codeLength) {
                throw classFormatError("Invalid start_pc (" + startPosition + ") in LocalVariableTable");
            }
            final int endPC = startPosition + length;
            if (endPC > codeLength) {
                throw classFormatError("Invalid length (" + length + ") in LocalVariableTable");
            }
            if (!forLVTT) {
                final TypeDescriptor descriptor = descriptor(constantPool);
                final int index = descriptor.toKind().isCategory2() ? slot + 1 : slot;
                if (index >= maxLocals) {
                    throw classFormatError("Invalid local variable index (" + slot + ") in LocalVariableTable");
                }
            }
        }

        @Override
        public String toString() {
            return (int) slot + "@" + (int) startPosition + "+" + (int) length + "{name=" + (int) nameIndex + ",descriptor=" + (int) descriptorIndex + ",signature=" + (int) signatureIndex + "}";
        }
    }

    public static final LocalVariableTable EMPTY = new LocalVariableTable(new char[] {0});

    /**
     * An encoded local variable table. The format is as follows:
     * <p>
     *
     * <pre>
     * encoded_entries {
     *     u2 number_of_entries_with_a_signature;
     *     {
     *         u2 start_position;
     *         u2 length;
     *         u2 slot;
     *         u2 name_index;
     *         u2 descriptor_index;
     *         u2 signature_index;
     *     } entries[number_of_entries]
     * }
     * </pre>
     */
    private final char[] encodedEntries;

    private static final int ENCODED_START_POSITION = 0;
    private static final int ENCODED_LENGTH = 1;
    private static final int ENCODED_SLOT = 2;
    private static final int ENCODED_NAME_INDEX = 3;
    private static final int ENCODED_DESCRIPTOR_INDEX = 4;
    private static final int ENCODED_SIGNATURE_INDEX = 5;

    LocalVariableTable(char[] encodedEntries) {
        assert encodedEntries.length >= 1;
        this.encodedEntries = encodedEntries;
    }

    public LocalVariableTable(Collection<Entry> entries) {
        encodedEntries = new char[(entries.size() * 6) + 1];
        int i = 1;
        for (Entry entry : entries) {
            encodedEntries[i + ENCODED_START_POSITION] = entry.startPosition;
            encodedEntries[i + ENCODED_LENGTH] = entry.length;
            encodedEntries[i + ENCODED_SLOT] = entry.slot;
            encodedEntries[i + ENCODED_NAME_INDEX] = entry.nameIndex;
            encodedEntries[i + ENCODED_DESCRIPTOR_INDEX] = entry.descriptorIndex;
            encodedEntries[i + ENCODED_SIGNATURE_INDEX] = entry.signatureIndex;
            i += 6;
            if (entry.signatureIndex != 0) {
                encodedEntries[0]++;
            }
        }
    }

    public LocalVariableTable relocate(OpcodePositionRelocator relocator) {
        if (encodedEntries.length == 1) {
            return this;
        }
        final char[] relocEncodedEntries = new char[this.encodedEntries.length];
        relocEncodedEntries[0] = relocEncodedEntries[0];
        for (int i = 1; i != relocEncodedEntries.length; i += 6) {
            final char startPosition = relocEncodedEntries[i + ENCODED_START_POSITION];
            final char length = relocEncodedEntries[i + ENCODED_LENGTH];
            final char relocatedEndPosition = (char) relocator.relocate(startPosition + length);
            // Special case for start address 0: only parameters can be defined at 0
            final char relocatedStartPosition = startPosition == 0 ? 0 : (char) relocator.relocate(startPosition);
            final char relocatedLength = (char) (relocatedEndPosition - relocatedStartPosition);
            relocEncodedEntries[i + ENCODED_START_POSITION] = relocatedStartPosition;
            relocEncodedEntries[i + ENCODED_LENGTH] = relocatedLength;
            relocEncodedEntries[i + ENCODED_SLOT] = relocEncodedEntries[i + ENCODED_SLOT];
            relocEncodedEntries[i + ENCODED_NAME_INDEX] = relocEncodedEntries[i + ENCODED_NAME_INDEX];
            relocEncodedEntries[i + ENCODED_DESCRIPTOR_INDEX] = relocEncodedEntries[i + ENCODED_DESCRIPTOR_INDEX];
            relocEncodedEntries[i + ENCODED_SIGNATURE_INDEX] = relocEncodedEntries[i + ENCODED_SIGNATURE_INDEX];
        }
        return new LocalVariableTable(relocEncodedEntries);
    }

    public int numberOfEntries() {
        return (encodedEntries.length - 1) / 6;
    }

    /**
     * Gets the number of {@linkplain #entries() entries} whose {@linkplain Entry#signatureIndex() signature index} is not 0.
     * That is, how many entries in this table are derived from a LocalVariableTypeTable class file attribute.
     */
    public int numberOfEntriesWithSignature() {
        return encodedEntries[0];
    }

    public boolean isEmpty() {
        return encodedEntries.length == 1;
    }

    /**
     * Gets an object describing a local variable that is live at a given bytecode position.
     *
     * @param index
     *                the index of the variable in the local variables array
     * @param bytecodePosition
     *                a bytecode position for which the details of the variable are being requested
     * @return a {@link Entry} object describing the requested variable. If the given local variable index and bytecode
     *         position do not denote a live local variable, then null is returned.
     */
    public Entry findLocalVariable(int index, int bytecodePosition) {
        if (encodedEntries.length != 1) {
            for (int i = 1; i != encodedEntries.length; i += 6) {
                final char thisSlot = encodedEntries[i + ENCODED_SLOT];
                if (thisSlot == index) {
                    final char thisStartPosition = encodedEntries[i + ENCODED_START_POSITION];
                    final char thisLength = encodedEntries[i + ENCODED_LENGTH];
                    final char thisEndPosition = (char) (thisStartPosition + thisLength);
                    if (bytecodePosition >= thisStartPosition && bytecodePosition <= thisEndPosition) {
                        final char thisNameIndex = encodedEntries[i + ENCODED_NAME_INDEX];
                        final char thisDescriptorIndex = encodedEntries[i + ENCODED_DESCRIPTOR_INDEX];
                        final char thisSignatureIndex = encodedEntries[i + ENCODED_SIGNATURE_INDEX];
                        return new Entry(thisStartPosition, thisLength, thisSlot, thisNameIndex, thisDescriptorIndex, thisSignatureIndex);
                    }
                }
            }
        }
        return null;
    }

    public Entry[] entries() {
        final Entry[] entries = new Entry[numberOfEntries()];
        for (int i = 1; i != encodedEntries.length; i += 6) {
            final char startPosition = encodedEntries[i + ENCODED_START_POSITION];
            final char slot = encodedEntries[i + ENCODED_SLOT];
            final char length = encodedEntries[i + ENCODED_LENGTH];
            final char nameIndex = encodedEntries[i + ENCODED_NAME_INDEX];
            final char descriptorIndex = encodedEntries[i + ENCODED_DESCRIPTOR_INDEX];
            final char signatureIndex = encodedEntries[i + ENCODED_SIGNATURE_INDEX];
            entries[i / 6] = new Entry(startPosition, length, slot, nameIndex, descriptorIndex, signatureIndex);
        }
        return entries;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("[");
        for (Entry entry : entries()) {
            if (sb.length() != 1) {
                sb.append(", ");
            }
            sb.append(entry);
        }
        return sb.append(']').toString();
    }

    public void encode(DataOutputStream dataOutputStream) throws IOException {
        CodeAttribute.writeCharArray(dataOutputStream, encodedEntries);
    }

    public static LocalVariableTable decode(DataInputStream dataInputStream) throws IOException {
        return new LocalVariableTable(CodeAttribute.readCharArray(dataInputStream));
    }

    /**
     * Writes this local variable table to a given stream as a LocalVariableTable class file attribute.
     *
     * @param stream
     *                a data output stream that has just written the 'attribute_name_index' and 'attribute_length'
     *                fields of a class file attribute
     * @param constantPoolEditor
     */
    public void writeLocalVariableTableAttributeInfo(DataOutputStream stream, ConstantPoolEditor constantPoolEditor) throws IOException {
        final int numberOfEntries = numberOfEntries();
        stream.writeShort(numberOfEntries);
        for (int i = 1; i != encodedEntries.length; i += 6) {
            stream.writeShort(encodedEntries[i + ENCODED_START_POSITION]);
            stream.writeShort(encodedEntries[i + ENCODED_LENGTH]);
            stream.writeShort(encodedEntries[i + ENCODED_NAME_INDEX]);
            stream.writeShort(encodedEntries[i + ENCODED_DESCRIPTOR_INDEX]);
            stream.writeShort(encodedEntries[i + ENCODED_SLOT]);
        }
    }

    /**
     * Writes this local variable table to a given stream as a LocalVariableTypeTable class file attribute.
     *
     * @param stream
     *                a data output stream that has just written the 'attribute_name_index' and 'attribute_length'
     *                fields of a class file attribute
     * @param constantPoolEditor
     */
    public void writeLocalVariableTypeTableAttributeInfo(DataOutputStream stream, ConstantPoolEditor constantPoolEditor) throws IOException {
        int numberOfEntries = numberOfEntriesWithSignature();
        stream.writeShort(numberOfEntries);
        for (int i = 1; i != encodedEntries.length; i += 6) {
            final int signatureIndex = encodedEntries[i + ENCODED_SIGNATURE_INDEX];
            if (signatureIndex != 0) {
                stream.writeShort(encodedEntries[i + ENCODED_START_POSITION]);
                stream.writeShort(encodedEntries[i + ENCODED_LENGTH]);
                stream.writeShort(encodedEntries[i + ENCODED_NAME_INDEX]);
                stream.writeShort(signatureIndex);
                stream.writeShort(encodedEntries[i + ENCODED_SLOT]);
                --numberOfEntries;
            }
        }
        assert numberOfEntries == 0;
    }
}
