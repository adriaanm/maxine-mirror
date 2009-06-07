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
package com.sun.max.vm.compiler.eir;

import com.sun.max.collect.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;

/**
 * @author Bernd Mathiske
 */
public enum EirLocationCategory implements PoolObject {
    // In order of preference:
    UNDEFINED("U"),
    INTEGER_REGISTER("G"),
    FLOATING_POINT_REGISTER("F"),
    IMMEDIATE_8("I8"),
    IMMEDIATE_16("I16"),
    IMMEDIATE_32("I32"),
    IMMEDIATE_64("I64"),
    BLOCK("B"),
    METHOD("M"),
    STACK_SLOT("S"),
    LITERAL("L");

    public static final IndexedSequence<EirLocationCategory> VALUES = new ArraySequence<EirLocationCategory>(values());

    private final String _shortName;

    private EirLocationCategory(String shortName) {
        _shortName = shortName;
    }

    @Override
    public int serial() {
        return ordinal();
    }

    @Override
    public String toString() {
        return _shortName;
    }

    public static EirLocationCategory immediateFromWordWidth(WordWidth width) {
        switch (width) {
            case BITS_8:
                return IMMEDIATE_8;
            case BITS_16:
                return IMMEDIATE_16;
            case BITS_32:
                return IMMEDIATE_32;
            case BITS_64:
                return IMMEDIATE_64;
        }
        ProgramError.unknownCase();
        return null;
    }

    public EirLocationCategory next() {
        final int index = ordinal() + 1;
        if (index >= VALUES.length()) {
            return null;
        }
        return VALUES.get(index);
    }

    public PoolSet<EirLocationCategory> asSet() {
        return PoolSet.of(VALUE_POOL, this);
    }

    public static final PoolSet<EirLocationCategory> all() {
        return PoolSet.allOf(VALUE_POOL);
    }

    private static final Pool<EirLocationCategory> VALUE_POOL = new ArrayPool<EirLocationCategory>(values());

    public static final PoolSet<EirLocationCategory> R = PoolSet.of(VALUE_POOL, INTEGER_REGISTER, FLOATING_POINT_REGISTER);

    public static final PoolSet<EirLocationCategory> I = PoolSet.of(VALUE_POOL, IMMEDIATE_8, IMMEDIATE_16, IMMEDIATE_32, IMMEDIATE_64);

    public static final PoolSet<EirLocationCategory> G = PoolSet.of(VALUE_POOL, INTEGER_REGISTER);

    public static final PoolSet<EirLocationCategory> F = PoolSet.of(VALUE_POOL, FLOATING_POINT_REGISTER);

    public static final PoolSet<EirLocationCategory> G_F = PoolSet.of(VALUE_POOL, INTEGER_REGISTER, FLOATING_POINT_REGISTER);

    public static final PoolSet<EirLocationCategory> S = PoolSet.of(VALUE_POOL, STACK_SLOT);

    public static final PoolSet<EirLocationCategory> G_I = PoolSet.of(VALUE_POOL, INTEGER_REGISTER, IMMEDIATE_8, IMMEDIATE_16, IMMEDIATE_32, IMMEDIATE_64);

    public static final PoolSet<EirLocationCategory> I8_I32 = PoolSet.of(VALUE_POOL, IMMEDIATE_8, IMMEDIATE_32);

    public static final PoolSet<EirLocationCategory> G_I32_I64 = PoolSet.of(VALUE_POOL, IMMEDIATE_32, IMMEDIATE_64, INTEGER_REGISTER);

    public static final PoolSet<EirLocationCategory> I32_I64_L = PoolSet.of(VALUE_POOL, IMMEDIATE_32, IMMEDIATE_64, LITERAL);

    public static final PoolSet<EirLocationCategory> G_I8_I32 = PoolSet.of(VALUE_POOL, IMMEDIATE_8, IMMEDIATE_32, INTEGER_REGISTER);

    public static final PoolSet<EirLocationCategory> G_I32 = PoolSet.of(VALUE_POOL, IMMEDIATE_32, INTEGER_REGISTER);

    public static final PoolSet<EirLocationCategory> G_I32_L = PoolSet.of(VALUE_POOL, IMMEDIATE_32, INTEGER_REGISTER, LITERAL);

    public static final PoolSet<EirLocationCategory> M = PoolSet.of(VALUE_POOL, METHOD);

    public static final PoolSet<EirLocationCategory> M_G = PoolSet.of(VALUE_POOL, METHOD, INTEGER_REGISTER);

    public static final PoolSet<EirLocationCategory> M_G_L_S = PoolSet.of(VALUE_POOL, METHOD, INTEGER_REGISTER, LITERAL, STACK_SLOT);

    public static final PoolSet<EirLocationCategory> G_S = PoolSet.of(VALUE_POOL, INTEGER_REGISTER, STACK_SLOT);

    public static final PoolSet<EirLocationCategory> B_G_S = PoolSet.of(VALUE_POOL, BLOCK, INTEGER_REGISTER, STACK_SLOT);

    public static final PoolSet<EirLocationCategory> G_L_S = PoolSet.of(VALUE_POOL, INTEGER_REGISTER, LITERAL, STACK_SLOT);

    public static final PoolSet<EirLocationCategory> G_I32_L_S = PoolSet.of(VALUE_POOL, INTEGER_REGISTER, IMMEDIATE_32, LITERAL, STACK_SLOT);

    public static final PoolSet<EirLocationCategory> G_I32_I64_L_S = PoolSet.of(VALUE_POOL, INTEGER_REGISTER, IMMEDIATE_64, LITERAL, STACK_SLOT);

    public static final PoolSet<EirLocationCategory> B_G_I32_I64_L_S = PoolSet.of(VALUE_POOL, BLOCK, INTEGER_REGISTER, IMMEDIATE_32, IMMEDIATE_64, LITERAL, STACK_SLOT);

    public static final PoolSet<EirLocationCategory> F_I8 = PoolSet.of(VALUE_POOL, FLOATING_POINT_REGISTER, IMMEDIATE_8);

    public static final PoolSet<EirLocationCategory> F_S = PoolSet.of(VALUE_POOL, FLOATING_POINT_REGISTER, STACK_SLOT);

    public static final PoolSet<EirLocationCategory> F_L_S = PoolSet.of(VALUE_POOL, FLOATING_POINT_REGISTER, LITERAL, STACK_SLOT);

    private static boolean isSharingRegisters(PoolSet<EirLocationCategory> a, PoolSet<EirLocationCategory> b) {
        for (EirLocationCategory category : a) {
            if (R.contains(category) && b.contains(category)) {
                return true;
            }
        }
        return false;
    }

    public static boolean areSharingRegisters(PoolSet<EirLocationCategory> a, PoolSet<EirLocationCategory> b) {
        return isSharingRegisters(a, b) || isSharingRegisters(b, a);
    }

}
