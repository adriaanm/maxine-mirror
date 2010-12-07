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
package com.sun.max.tele.reference.direct;

import java.lang.reflect.*;

import com.sun.max.annotate.*;
import com.sun.max.tele.reference.*;
import com.sun.max.tele.util.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.reference.*;

/**
 * @author Bernd Mathiske
 * @author Michael Van De Vanter
 */
public final class DirectInspectorReferenceScheme extends TeleReferenceScheme {

    public boolean isConstant() {
        return false;
    }

    public Pointer toOrigin(Reference ref) {
        if (ref.isZero()) {
            return Pointer.zero();
        }
        if (ref instanceof LocalTeleReference) {
            throw new UnsupportedOperationException();
        }
        final RemoteTeleReference remoteTeleRef = (RemoteTeleReference) ref;
        return remoteTeleRef.raw().asPointer();
    }

    public Reference fromOrigin(Pointer origin) {
        return makeTeleReference(origin);
    }

    @Override
    public RemoteTeleReference temporaryRemoteTeleReferenceFromOrigin(Word origin) {
        return createTemporaryRemoteTeleReference(origin.asAddress());
    }

    public Reference fromReference(Reference reference) {
        final TeleReference teleReference = (TeleReference) reference;
        return teleReference;
    }

    public Reference fromJava(Object object) {
        return makeLocalReference(object);
    }

    public Object toJava(Reference ref) {
        if (ref instanceof LocalTeleReference) {
            final LocalTeleReference inspectorLocalTeleRef = (LocalTeleReference) ref;
            return inspectorLocalTeleRef.object();
        }
        throw new UnsupportedOperationException();
    }

    public Reference zero() {
        return TeleReference.ZERO;
    }

    public boolean isZero(Reference ref) {
        return ref == TeleReference.ZERO;
    }

    @INLINE
    public boolean isAllOnes(Reference ref) {
        if (ref.isZero()) {
            return false;
        } else if (ref instanceof LocalTeleReference) {
            TeleError.unexpected();
        }
        return toOrigin(ref).isAllOnes();
    }

    public boolean equals(Reference ref1, Reference ref2) {
        return ref1.equals(ref2);
    }

    public boolean isMarked(Reference ref) {
        throw new UnsupportedOperationException();
    }

    public Reference marked(Reference ref) {
        throw new UnsupportedOperationException();
    }

    public Reference unmarked(Reference ref) {
        throw new UnsupportedOperationException();
    }

    private Object readField(Reference ref, int offset) {
        final Object object = toJava(ref);
        if (object instanceof StaticTuple) {
            final StaticTuple staticTuple = (StaticTuple) object;
            final FieldActor fieldActor = staticTuple.findStaticFieldActor(offset);
            final Class javaClass = staticTuple.classActor().toJava();
            try {
                return WithoutAccessCheck.getStaticField(javaClass, fieldActor.name.toString());
            } catch (Throwable throwable) {
                TeleError.unexpected("could not access static field: " + fieldActor.name, throwable);
            }
        }
        final Class javaClass = object.getClass();
        final ClassActor classActor = ClassActor.fromJava(javaClass);

        if (classActor.isArrayClass()) {
            return Array.getLength(object);
        }

        final FieldActor fieldActor = classActor.findInstanceFieldActor(offset);
        try {
            return WithoutAccessCheck.getInstanceField(object, fieldActor.name.toString());
        } catch (Throwable throwable) {
            throw TeleError.unexpected("could not access field: " + fieldActor.name, throwable);
        }
    }

    public byte readByte(Reference ref, Offset offset) {
        if (ref instanceof LocalTeleReference) {
            return readByte(ref, offset.toInt());
        }

        return vm().dataAccess().readByte(toOrigin(ref), offset);
    }

    public byte readByte(Reference ref, int offset) {
        if (ref instanceof LocalTeleReference) {
            final Byte result = (Byte) readField(ref, offset);
            return result.byteValue();
        }

        return vm().dataAccess().readByte(toOrigin(ref), offset);
    }

    public byte getByte(Reference ref, int displacement, int index) {
        if (ref instanceof LocalTeleReference) {
            final byte[] array = (byte[]) ref.toJava();
            return array[index];
        }

        return vm().dataAccess().getByte(toOrigin(ref), displacement, index);
    }

    public boolean readBoolean(Reference ref, Offset offset) {
        if (ref instanceof LocalTeleReference) {
            return readBoolean(ref, offset.toInt());
        }

        return vm().dataAccess().readBoolean(toOrigin(ref), offset);
    }

    public boolean readBoolean(Reference ref, int offset) {
        if (ref instanceof LocalTeleReference) {
            final Boolean result = (Boolean) readField(ref, offset);
            return result.booleanValue();
        }

        return vm().dataAccess().readBoolean(toOrigin(ref), offset);
    }

    public boolean getBoolean(Reference ref, int displacement, int index) {
        if (ref instanceof LocalTeleReference) {
            final boolean[] array = (boolean[]) ref.toJava();
            return array[index];
        }

        return vm().dataAccess().getBoolean(toOrigin(ref), displacement, index);
    }

    public short readShort(Reference ref, Offset offset) {
        if (ref instanceof LocalTeleReference) {
            return readShort(ref, offset.toInt());
        }

        return vm().dataAccess().readShort(toOrigin(ref), offset);
    }

    public short readShort(Reference ref, int offset) {
        if (ref instanceof LocalTeleReference) {
            final Short result = (Short) readField(ref, offset);
            return result.shortValue();
        }

        return vm().dataAccess().readShort(toOrigin(ref), offset);
    }

    public short getShort(Reference ref, int displacement, int index) {
        if (ref instanceof LocalTeleReference) {
            final short[] array = (short[]) ref.toJava();
            return array[index];
        }

        return vm().dataAccess().getShort(toOrigin(ref), displacement, index);
    }

    public char readChar(Reference ref, Offset offset) {
        if (ref instanceof LocalTeleReference) {
            return readChar(ref, offset.toInt());
        }

        return vm().dataAccess().readChar(toOrigin(ref), offset);
    }

    public char readChar(Reference ref, int offset) {
        if (ref instanceof LocalTeleReference) {
            final Character result = (Character) readField(ref, offset);
            return result.charValue();
        }

        return vm().dataAccess().readChar(toOrigin(ref), offset);
    }

    public char getChar(Reference ref, int displacement, int index) {
        if (ref instanceof LocalTeleReference) {
            final char[] array = (char[]) ref.toJava();
            return array[index];
        }

        return vm().dataAccess().getChar(toOrigin(ref), displacement, index);
    }

    public int readInt(Reference ref, Offset offset) {
        if (ref instanceof LocalTeleReference) {
            return readInt(ref, offset.toInt());
        }

        return vm().dataAccess().readInt(toOrigin(ref), offset);
    }

    public int readInt(Reference ref, int offset) {
        if (ref instanceof LocalTeleReference) {
            final Integer result = (Integer) readField(ref, offset);
            return result.intValue();
        }

        return vm().dataAccess().readInt(toOrigin(ref), offset);
    }

    public int getInt(Reference ref, int displacement, int index) {
        if (ref instanceof LocalTeleReference) {
            final int[] array = (int[]) ref.toJava();
            return array[index];
        }

        return vm().dataAccess().getInt(toOrigin(ref), displacement, index);
    }

    public float readFloat(Reference ref, Offset offset) {
        if (ref instanceof LocalTeleReference) {
            return readFloat(ref, offset.toInt());
        }

        return vm().dataAccess().readFloat(toOrigin(ref), offset);
    }

    public float readFloat(Reference ref, int offset) {
        if (ref instanceof LocalTeleReference) {
            final Float result = (Float) readField(ref, offset);
            return result.floatValue();
        }

        return vm().dataAccess().readFloat(toOrigin(ref), offset);
    }

    public float getFloat(Reference ref, int displacement, int index) {
        if (ref instanceof LocalTeleReference) {
            final float[] array = (float[]) ref.toJava();
            return array[index];
        }

        return vm().dataAccess().getFloat(toOrigin(ref), displacement, index);
    }

    public long readLong(Reference ref, Offset offset) {
        if (ref instanceof LocalTeleReference) {
            return readLong(ref, offset.toInt());
        }

        return vm().dataAccess().readLong(toOrigin(ref), offset);
    }

    public long readLong(Reference ref, int offset) {
        if (ref instanceof LocalTeleReference) {
            final Long result = (Long) readField(ref, offset);
            return result.longValue();
        }

        return vm().dataAccess().readLong(toOrigin(ref), offset);
    }

    public long getLong(Reference ref, int displacement, int index) {
        if (ref instanceof LocalTeleReference) {
            final long[] array = (long[]) ref.toJava();
            return array[index];
        }

        return vm().dataAccess().getLong(toOrigin(ref), displacement, index);
    }

    public double readDouble(Reference ref, Offset offset) {
        if (ref instanceof LocalTeleReference) {
            return readDouble(ref, offset.toInt());
        }

        return vm().dataAccess().readDouble(toOrigin(ref), offset);
    }

    public double readDouble(Reference ref, int offset) {
        if (ref instanceof LocalTeleReference) {
            final Double result = (Double) readField(ref, offset);
            return result.doubleValue();
        }

        return vm().dataAccess().readDouble(toOrigin(ref), offset);
    }

    public double getDouble(Reference ref, int displacement, int index) {
        if (ref instanceof LocalTeleReference) {
            final double[] array = (double[]) ref.toJava();
            return array[index];
        }

        return vm().dataAccess().getDouble(toOrigin(ref), displacement, index);
    }

    public Word readWord(Reference ref, Offset offset) {
        if (ref instanceof LocalTeleReference) {
            return readWord(ref, offset.toInt());
        }

        return vm().dataAccess().readWord(toOrigin(ref), offset);
    }

    public Word readWord(Reference ref, int offset) {
        if (ref instanceof LocalTeleReference) {
            return (Word) readField(ref, offset);
        }

        return vm().dataAccess().readWord(toOrigin(ref), offset);
    }

    public Word getWord(Reference ref, int displacement, int index) {
        if (ref instanceof LocalTeleReference) {
            final Word[] array = (Word[]) ref.toJava();
            return array[index];
        }

        return vm().dataAccess().getWord(toOrigin(ref), displacement, index);
    }

    public Reference readReference(Reference ref, Offset offset) {
        if (ref instanceof LocalTeleReference) {
            return readReference(ref, offset.toInt());
        }

        return fromOrigin(readWord(ref, offset).asPointer());
    }

    public Reference readReference(Reference ref, int offset) {
        if (ref instanceof LocalTeleReference) {
            return fromJava(readField(ref, offset));
        }

        return fromOrigin(readWord(ref, offset).asPointer());
    }

    public Reference getReference(Reference ref, int displacement, int index) {
        if (ref instanceof LocalTeleReference) {
            final Object[] array = (Object[]) toJava(ref);
            return fromJava(array[index]);
        }

        return fromOrigin(getWord(ref, displacement, index).asPointer());
    }

    private void writeField(Reference ref, int offset, Object value) {
        final Object object = toJava(ref);
        if (object instanceof StaticTuple) {
            final StaticTuple staticTuple = (StaticTuple) object;
            final FieldActor fieldActor = staticTuple.findStaticFieldActor(offset);
            final Class javaClass = staticTuple.classActor().toJava();
            try {
                WithoutAccessCheck.setStaticField(javaClass, fieldActor.name.toString(), value);
            } catch (Throwable throwable) {
                TeleError.unexpected("could not access static field: " + fieldActor.name, throwable);
            }
        } else {
            final Class javaClass = object.getClass();
            final TupleClassActor tupleClassActor = (TupleClassActor) ClassActor.fromJava(javaClass);
            final FieldActor fieldActor = tupleClassActor.findInstanceFieldActor(offset);
            WithoutAccessCheck.setInstanceField(object, fieldActor.name.toString(), value);
        }
    }

    public void writeByte(Reference ref, Offset offset, byte value) {
        if (ref instanceof LocalTeleReference) {
            writeByte(ref, offset.toInt(), value);
            return;
        }

        vm().dataAccess().writeByte(toOrigin(ref), offset, value);
    }

    public void writeByte(Reference ref, int offset, byte value) {
        if (ref instanceof LocalTeleReference) {
            writeField(ref, offset, new Byte(value));
            return;
        }

        vm().dataAccess().writeByte(toOrigin(ref), offset, value);
    }

    public void setByte(Reference ref, int displacement, int index, byte value) {
        if (ref instanceof LocalTeleReference) {
            final byte[] array = (byte[]) ref.toJava();
            array[index] = value;
            return;
        }

        vm().dataAccess().setByte(toOrigin(ref), displacement, index, value);
    }

    public void writeBoolean(Reference ref, Offset offset, boolean value) {
        if (ref instanceof LocalTeleReference) {
            writeBoolean(ref, offset.toInt(), value);
            return;
        }

        vm().dataAccess().writeBoolean(toOrigin(ref), offset, value);
    }

    public void writeBoolean(Reference ref, int offset, boolean value) {
        if (ref instanceof LocalTeleReference) {
            writeField(ref, offset, new Boolean(value));
            return;
        }

        vm().dataAccess().writeBoolean(toOrigin(ref), offset, value);
    }

    public void setBoolean(Reference ref, int displacement, int index, boolean value) {
        if (ref instanceof LocalTeleReference) {
            final boolean[] array = (boolean[]) ref.toJava();
            array[index] = value;
            return;
        }

        vm().dataAccess().setBoolean(toOrigin(ref), displacement, index, value);
    }

    public void writeShort(Reference ref, Offset offset, short value) {
        if (ref instanceof LocalTeleReference) {
            writeShort(ref, offset.toInt(), value);
            return;
        }

        vm().dataAccess().writeShort(toOrigin(ref), offset, value);
    }

    public void writeShort(Reference ref, int offset, short value) {
        if (ref instanceof LocalTeleReference) {
            writeField(ref, offset, new Short(value));
            return;
        }

        vm().dataAccess().writeShort(toOrigin(ref), offset, value);
    }

    public void setShort(Reference ref, int displacement, int index, short value) {
        if (ref instanceof LocalTeleReference) {
            final short[] array = (short[]) ref.toJava();
            array[index] = value;
            return;
        }

        vm().dataAccess().setShort(toOrigin(ref), displacement, index, value);
    }

    public void writeChar(Reference ref, Offset offset, char value) {
        if (ref instanceof LocalTeleReference) {
            writeChar(ref, offset.toInt(), value);
            return;
        }

        vm().dataAccess().writeChar(toOrigin(ref), offset, value);
    }

    public void writeChar(Reference ref, int offset, char value) {
        if (ref instanceof LocalTeleReference) {
            writeField(ref, offset, new Character(value));
            return;
        }

        vm().dataAccess().writeChar(toOrigin(ref), offset, value);
    }

    public void setChar(Reference ref, int displacement, int index, char value) {
        if (ref instanceof LocalTeleReference) {
            final char[] array = (char[]) ref.toJava();
            array[index] = value;
            return;
        }

        vm().dataAccess().setChar(toOrigin(ref), displacement, index, value);
    }

    public void writeInt(Reference ref, Offset offset, int value) {
        if (ref instanceof LocalTeleReference) {
            writeInt(ref, offset.toInt(), value);
            return;
        }

        vm().dataAccess().writeInt(toOrigin(ref), offset, value);
    }

    public void writeInt(Reference ref, int offset, int value) {
        if (ref instanceof LocalTeleReference) {
            writeField(ref, offset, new Integer(value));
            return;
        }

        vm().dataAccess().writeInt(toOrigin(ref), offset, value);
    }

    public void setInt(Reference ref, int displacement, int index, int value) {
        if (ref instanceof LocalTeleReference) {
            final int[] array = (int[]) ref.toJava();
            array[index] = value;
            return;
        }

        vm().dataAccess().setInt(toOrigin(ref), displacement, index, value);
    }

    public void writeFloat(Reference ref, Offset offset, float value) {
        if (ref instanceof LocalTeleReference) {
            writeFloat(ref, offset.toInt(), value);
            return;
        }

        vm().dataAccess().writeFloat(toOrigin(ref), offset, value);
    }

    public void writeFloat(Reference ref, int offset, float value) {
        if (ref instanceof LocalTeleReference) {
            writeField(ref, offset, new Float(value));
            return;
        }

        vm().dataAccess().writeFloat(toOrigin(ref), offset, value);
    }

    public void setFloat(Reference ref, int displacement, int index, float value) {
        if (ref instanceof LocalTeleReference) {
            final float[] array = (float[]) ref.toJava();
            array[index] = value;
            return;
        }

        vm().dataAccess().setFloat(toOrigin(ref), displacement, index, value);
    }

    public void writeLong(Reference ref, Offset offset, long value) {
        if (ref instanceof LocalTeleReference) {
            writeLong(ref, offset.toInt(), value);
            return;
        }

        vm().dataAccess().writeLong(toOrigin(ref), offset, value);
    }

    public void writeLong(Reference ref, int offset, long value) {
        if (ref instanceof LocalTeleReference) {
            writeField(ref, offset, new Long(value));
            return;
        }

        vm().dataAccess().writeLong(toOrigin(ref), offset, value);
    }

    public void setLong(Reference ref, int displacement, int index, long value) {
        if (ref instanceof LocalTeleReference) {
            final long[] array = (long[]) ref.toJava();
            array[index] = value;
            return;
        }

        vm().dataAccess().setLong(toOrigin(ref), displacement, index, value);
    }

    public void writeDouble(Reference ref, Offset offset, double value) {
        if (ref instanceof LocalTeleReference) {
            writeDouble(ref, offset.toInt(), value);
            return;
        }

        vm().dataAccess().writeDouble(toOrigin(ref), offset, value);
    }

    public void writeDouble(Reference ref, int offset, double value) {
        if (ref instanceof LocalTeleReference) {
            writeField(ref, offset, new Double(value));
            return;
        }

        vm().dataAccess().writeDouble(toOrigin(ref), offset, value);
    }

    public void setDouble(Reference ref, int displacement, int index, double value) {
        if (ref instanceof LocalTeleReference) {
            final double[] array = (double[]) ref.toJava();
            array[index] = value;
            return;
        }

        vm().dataAccess().setDouble(toOrigin(ref), displacement, index, value);
    }

    public void writeWord(Reference ref, Offset offset, Word value) {
        if (ref instanceof LocalTeleReference) {
            writeWord(ref, offset.toInt(), value);
            return;
        }

        vm().dataAccess().writeWord(toOrigin(ref), offset, value);
    }

    public void writeWord(Reference ref, int offset, Word value) {
        if (ref instanceof LocalTeleReference) {
            final BoxedWord boxedWord = new BoxedWord(value); // avoiding word/ref kind mismatch
            writeField(ref, offset, boxedWord);
            return;
        }

        vm().dataAccess().writeWord(toOrigin(ref), offset, value);
    }

    public void setWord(Reference ref, int displacement, int index, Word value) {
        if (ref instanceof LocalTeleReference) {
            final Word[] array = (Word[]) ref.toJava();
            WordArray.set(array, index, value);
            return;
        }

        vm().dataAccess().setWord(toOrigin(ref), displacement, index, value);
    }

    public void writeReference(Reference ref, Offset offset, Reference value) {
        if (ref instanceof LocalTeleReference) {
            writeReference(ref, offset.toInt(), value);
            return;
        }

        writeWord(ref, offset, value.toOrigin());
    }

    public void writeReference(Reference ref, int offset, Reference value) {
        if (ref instanceof LocalTeleReference) {
            writeField(ref, offset, value.toJava());
            return;
        }

        writeWord(ref, offset, value.toOrigin());
    }

    public void setReference(Reference ref, int displacement, int index, Reference value) {
        if (ref instanceof LocalTeleReference) {
            final Object[] array = (Object[]) toJava(ref);
            array[index] = value.toJava();
            return;
        }

        setWord(ref, displacement, index, value.toOrigin());
    }

    public int compareAndSwapInt(Reference ref, Offset offset, int expectedValue, int newValue) {
        return toOrigin(ref).compareAndSwapInt(offset, expectedValue, newValue);
    }

    public int compareAndSwapInt(Reference ref, int offset, int expectedValue, int newValue) {
        return toOrigin(ref).compareAndSwapInt(offset, expectedValue, newValue);
    }

    public Word compareAndSwapWord(Reference ref, Offset offset, Word expectedValue, Word newValue) {
        TeleError.unimplemented();
        return Word.zero();
    }

    public Word compareAndSwapWord(Reference ref, int offset, Word expectedValue, Word newValue) {
        TeleError.unimplemented();
        return Word.zero();
    }

    public Reference compareAndSwapReference(Reference ref, Offset offset, Reference expectedValue, Reference newValue) {
        TeleError.unimplemented();
        return null;
    }

    public Reference compareAndSwapReference(Reference ref, int offset, Reference expectedValue, Reference newValue) {
        TeleError.unimplemented();
        return null;
    }

    public void copyElements(int displacement, Reference src, int srcIndex, Object dst, int dstIndex, int length) {
        if (src instanceof LocalTeleReference) {
            System.arraycopy(toJava(src), srcIndex, dst, dstIndex, length);
        } else {
            vm().dataAccess().copyElements(toOrigin(src), displacement, srcIndex, dst, dstIndex, length);
        }
    }

    @Override
    public byte[] asBytes(Pointer origin) {
        throw TeleError.unimplemented();
    }

    @Override
    public byte[] nullAsBytes() {
        throw TeleError.unimplemented();
    }
}
