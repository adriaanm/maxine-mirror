/*
 * Copyright (c) 2009 Sun Microsystems, Inc.  All rights reserved.
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
package com.sun.cri.ci;



/**
 * Represents a constant (boxed) value, such as an integer, floating point number, or object reference,
 * within the compiler and across the compiler/runtime interface. Exports a set of {@code CiConstant}
 * instances that represent frequently used constant values, such as {@link #ZERO}.
 *
 * @author Ben L. Titzer
 */
public final class CiConstant extends CiValue {

    private static final CiConstant[] INT_CONSTANT_CACHE = new CiConstant[100];
    static {
        for (int i = 0; i < INT_CONSTANT_CACHE.length; ++i) {
            INT_CONSTANT_CACHE[i] = new CiConstant(CiKind.Int, i);
        }
    }
    
    public static final CiConstant NULL_OBJECT = new CiConstant(CiKind.Object, null);
    public static final CiConstant ZERO = new CiConstant(CiKind.Word, 0L);
    public static final CiConstant INT_MINUS_1 = new CiConstant(CiKind.Int, -1);
    public static final CiConstant INT_0 = forInt(0);
    public static final CiConstant INT_1 = forInt(1);
    public static final CiConstant INT_2 = forInt(2);
    public static final CiConstant INT_3 = forInt(3);
    public static final CiConstant INT_4 = forInt(4);
    public static final CiConstant INT_5 = forInt(5);
    public static final CiConstant LONG_0 = new CiConstant(CiKind.Long, 0L);
    public static final CiConstant LONG_1 = new CiConstant(CiKind.Long, 1L);
    public static final CiConstant FLOAT_0 = new CiConstant(CiKind.Float, Float.floatToRawIntBits(0.0F));
    public static final CiConstant FLOAT_1 = new CiConstant(CiKind.Float, Float.floatToRawIntBits(1.0F));
    public static final CiConstant FLOAT_2 = new CiConstant(CiKind.Float, Float.floatToRawIntBits(2.0F));
    public static final CiConstant DOUBLE_0 = new CiConstant(CiKind.Double, Double.doubleToRawLongBits(0.0D));
    public static final CiConstant DOUBLE_1 = new CiConstant(CiKind.Double, Double.doubleToRawLongBits(1.0D));
    public static final CiConstant TRUE = new CiConstant(CiKind.Boolean, 1L);
    public static final CiConstant FALSE = new CiConstant(CiKind.Boolean, 0L);

    static {
        assert ZERO.isDefaultValue();
        assert NULL_OBJECT.isDefaultValue();
        assert INT_0.isDefaultValue();
        assert FLOAT_0.isDefaultValue();
        assert DOUBLE_0.isDefaultValue();
        assert FALSE.isDefaultValue();

        // Ensure difference between 0.0f and -0.0f is preserved
        assert FLOAT_0 != forFloat(-0.0F);
        assert !forFloat(-0.0F).isDefaultValue();
        
        // Ensure difference between 0.0d and -0.0d is preserved
        assert DOUBLE_0 != forDouble(-0.0d);
        assert !forDouble(-0.0D).isDefaultValue();
        
        assert NULL_OBJECT.isNull();
    }
    
    /**
     * The boxed object value. This is ignored iff {@code !kind.isObject()}.
     */
    private final Object object;
    
    /**
     * The boxed primitive value as a {@code long}. This is ignored iff {@code kind.isObject()}.
     * For {@code float} and {@code double} values, this value is the result of
     * {@link Float#floatToRawIntBits(float)} and {@link Double#doubleToRawLongBits(double)} respectively. 
     */
    private final long primitive;

    /**
     * Create a new constant represented by the specified object reference.
     * 
     * @param kind the type of this constant
     * @param object the value of this constant
     */
    private CiConstant(CiKind kind, Object object) {
        super(kind);
        this.object = object;
        this.primitive = 0L;
    }

    /**
     * Create a new constant represented by the specified primitive.
     * 
     * @param kind the type of this constant
     * @param primitive the value of this constant
     */
    public CiConstant(CiKind kind, long primitive) {
        super(kind);
        this.object = null;
        this.primitive = primitive;
    }

    /**
     * Checks whether this constant is non-null.
     * @return {@code true} if this constant is a primitive, or an object constant that is not null
     */
    public boolean isNonNull() {
        return !kind.isObject() || object != null;
    }

    /**
     * Checks whether this constant is null.
     * @return {@code true} if this constant is the null constant
     */
    public boolean isNull() {
        return kind.isObject() && object == null;
    }

    @Override
    public String name() {
        return "const[" + kind.format(boxedValue()) + "]";
    }

    /**
     * Gets this constant's value as a string.
     *
     * @return this constant's value as a string
     */
    public String valueString() {
        Object boxed = boxedValue();
        return (boxed == null) ? "null" : boxed.toString();
    }

    /**
     * Returns the value of this constant as a boxed Java value.
     * @return the value of this constant
     */
    public Object boxedValue() {
        switch (kind) {
            case Byte: return (byte) asInt();
            case Boolean: return asInt() == 0 ? Boolean.FALSE : Boolean.TRUE;
            case Short: return (short) asInt();
            case Char: return (char) asInt();
            case Jsr: return (int) primitive;
            case Int: return asInt();
            case Long: return asLong();
            case Float: return asFloat();
            case Double: return asDouble();
            case Object: return object;
            case Word: return asLong();
        }
        throw new IllegalArgumentException();
    }

    private boolean valueEqual(CiConstant other) {
        // must have equivalent kinds to be equal
        if (kind != other.kind) {
            return false;
        }
        if (kind.isObject()) {
            return object == other.object;
        }
        return primitive == other.primitive;
    }
    
    /**
     * Converts this constant to a primitive int.
     * @return the int value of this constant
     */
    public int asInt() {
        if (kind.stackKind().isInt() || kind.isJsr()) {
            return (int) primitive;
        }
        throw new Error("Constant is not int: " + this);
    }

    /**
     * Converts this constant to a primitive boolean.
     * @return the boolean value of this constant
     */
    public boolean asBoolean() {
    	if (kind == CiKind.Boolean) {
    	    return primitive != 0L;
    	}
        throw new Error("Constant is not boolean: " + this);
    }

    /**
     * Converts this constant to a primitive long.
     * @return the long value of this constant
     */
    public long asLong() {
        switch (kind.stackKind()) {
            case Int:
            case Word:
            case Long: return primitive;
            case Float: return (long) asFloat();
            case Double: return (long) asDouble();
            default: throw new Error("Constant is not long: " + this);
        }
    }

    /**
     * Converts this constant to a primitive float.
     * @return the float value of this constant
     */
    public float asFloat() {
        if (kind.isFloat()) {
            return Float.intBitsToFloat((int) primitive);
        }
        throw new Error("Constant is not float: " + this);
    }

    /**
     * Converts this constant to a primitive double.
     * @return the double value of this constant
     */
    public double asDouble() {
        if (kind.isFloat()) {
            return Float.intBitsToFloat((int) primitive);
        }
        if (kind.isDouble()) {
            return Double.longBitsToDouble(primitive);
        }
        throw new Error("Constant is not double: " + this);
    }

    /**
     * Converts this constant to the object reference it represents.
     * @return the object which this constant represents
     */
    public Object asObject() {
        if (kind.isObject()) {
            return object;
        }
        throw new Error("Constant is not object: " + this);
    }

    /**
     * Converts this constant to the jsr reference it represents.
     * @return the object which this constant represents
     */
    public int asJsr() {
        if (kind.isJsr()) {
            return (int) primitive;
        }
        throw new Error("Constant is not jsr: " + this);
    }

    /**
     * Computes the hashcode of this constant.
     * @return a suitable hashcode for this constant
     */
    @Override
    public int hashCode() {
        if (kind.isObject()) {
            return System.identityHashCode(object);
        }
        return (int) primitive;
    }

    /**
     * Checks whether this constant equals another object. This is only
     * true if the other object is a constant and has the same value.
     * @param o the object to compare equality
     * @return {@code true} if this constant is equivalent to the specified object
     */
    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof CiConstant && valueEqual((CiConstant) o);
    }

    /**
     * Checks whether this constant is identical to another constant or has the same value as it.
     * @param other the constant to compare for equality against this constant
     * @return {@code true} if this constant is equivalent to {@code other}
     */
    public boolean equivalent(CiConstant other) {
        return other == this || valueEqual(other);
    }

    /**
     * Checks whether this constant is the default value for its type.
     * @return {@code true} if the value is the default value for its type; {@code false} otherwise
     */
    public boolean isDefaultValue() {
        switch (kind.stackKind()) {
            case Int: return asInt() == 0;
            case Long: return asLong() == 0;
            case Float: return this == FLOAT_0;
            case Double: return this == DOUBLE_0;
            case Object: return object == null;
            case Word: return this == ZERO;
        }
        return false;
    }

    /**
     * Creates a boxed double constant.
     * @param d the double value to box
     * @return a boxed copy of {@code value}
     */
    public static CiConstant forDouble(double d) {
        if (Double.compare(0.0D, d) == 0) {
            return DOUBLE_0;
        }
        if (Double.compare(d, 1.0D) == 0) {
            return DOUBLE_1;
        }
        return new CiConstant(CiKind.Double, Double.doubleToRawLongBits(d));
    }

    /**
     * Creates a boxed float constant.
     * @param f the float value to box
     * @return a boxed copy of {@code value}
     */
    public static CiConstant forFloat(float f) {
        if (Float.compare(f, 0.0F) == 0) {
            return FLOAT_0;
        }
        if (Float.compare(f, 1.0F) == 0) {
            return FLOAT_1;
        }
        if (Float.compare(f, 2.0F) == 0) {
            return FLOAT_2;
        }
        return new CiConstant(CiKind.Float, Float.floatToRawIntBits(f));
    }

    /**
     * Creates a boxed long constant.
     * @param i the long value to box
     * @return a boxed copy of {@code value}
     */
    public static CiConstant forLong(long i) {
        return i == 0 ? LONG_0 : i == 1 ? LONG_1 : new CiConstant(CiKind.Long, i);
    }

    /**
     * Creates a boxed integer constant.
     * @param i the integer value to box
     * @return a boxed copy of {@code value}
     */
    public static CiConstant forInt(int i) {
        if (i == -1) {
            return INT_MINUS_1;
        }
        if (i >= 0 && i < INT_CONSTANT_CACHE.length) {
            return INT_CONSTANT_CACHE[i];
        }
        return new CiConstant(CiKind.Int, i);
    }

    /**
     * Creates a boxed byte constant.
     * @param i the byte value to box
     * @return a boxed copy of {@code value}
     */
    public static CiConstant forByte(byte i) {
        return new CiConstant(CiKind.Byte, i);
    }

    /**
     * Creates a boxed boolean constant.
     * @param i the boolean value to box
     * @return a boxed copy of {@code value}
     */
    public static CiConstant forBoolean(boolean i) {
        return i ? TRUE : FALSE;
    }

    /**
     * Creates a boxed char constant.
     * @param i the char value to box
     * @return a boxed copy of {@code value}
     */
    public static CiConstant forChar(char i) {
        return new CiConstant(CiKind.Char, i);
    }

    /**
     * Creates a boxed short constant.
     * @param i the short value to box
     * @return a boxed copy of {@code value}
     */
    public static CiConstant forShort(short i) {
        return new CiConstant(CiKind.Short, i);
    }

    /**
     * Creates a boxed address (jsr/ret address) constant.
     * @param i the address value to box
     * @return a boxed copy of {@code value}
     */
    public static CiConstant forJsr(int i) {
        return new CiConstant(CiKind.Jsr, i);
    }

    /**
     * Creates a boxed word constant.
     * @param w the value to box
     * @return a boxed copy of {@code value}
     */
    public static CiConstant forWord(long w) {
        if (w == 0) {
            return ZERO;
        }
        return new CiConstant(CiKind.Word, w);
    }

    /**
     * Creates a boxed object constant.
     * @param o the object value to box
     * @return a boxed copy of {@code value}
     */
    public static CiConstant forObject(Object o) {
        if (o == null) {
            return NULL_OBJECT;
        }
        return new CiConstant(CiKind.Object, o);
    }

    /**
     * Creates a boxed constant from a given {@linkplain #boxedValue() boxed value}.
     * 
     * @param kind
     * @param boxedValue the Java boxed value to box as a {@link CiConstant}
     * @return the {@link CiConstant} for {@code boxedValue}
     */
    public static CiConstant forBoxed(CiKind kind, Object boxedValue) {
        switch (kind) {
            case Byte    : return forByte(((Byte) boxedValue).byteValue());
            case Boolean : return forBoolean(((Boolean) boxedValue).booleanValue());
            case Char    : return forChar(((Character) boxedValue).charValue());
            case Short   : return forShort(((Short) boxedValue).shortValue());
            case Int     : return forInt(((Integer) boxedValue).intValue());
            case Float   : return forFloat(((Float) boxedValue).floatValue());
            case Long    : return forLong(((Long) boxedValue).longValue());
            case Double  : return forDouble(((Double) boxedValue).doubleValue());
            case Jsr     : return forJsr(((Integer) boxedValue).intValue());
            case Object  : return forObject(boxedValue);
            case Word    : return forWord(((Long) boxedValue).longValue());
        }
        throw new IllegalArgumentException("Cannot create CiConstant for boxed value of kind " + kind);
    }
}
