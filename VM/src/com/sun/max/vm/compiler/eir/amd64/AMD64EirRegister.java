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
/*VCSID=fa30956d-17c9-4e57-845f-148d01358b7c*/
package com.sun.max.vm.compiler.eir.amd64;

import com.sun.max.asm.amd64.*;
import com.sun.max.asm.x86.*;
import com.sun.max.collect.*;
import com.sun.max.lang.*;
import com.sun.max.vm.compiler.eir.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.type.*;

/**
 * @author Bernd Mathiske
 */
public abstract class AMD64EirRegister extends EirRegister {

    private static AMD64EirRegister[] _registers = new AMD64EirRegister[32];

    private static Pool<AMD64EirRegister> _pool = new ArrayPool<AMD64EirRegister>(_registers);

    public static Pool<AMD64EirRegister> pool() {
        return _pool;
    }

    private final int _ordinal;

    private final int _serial;

    private static int _nextSerial;

    protected AMD64EirRegister(int ordinal) {
        _ordinal = ordinal;
        _serial = _nextSerial++;
        assert _registers[_serial] == null;
        _registers[_serial] = this;
    }

    @Override
    public final int ordinal() {
        return _ordinal;
    }

    @Override
    public final int serial() {
        return _serial;
    }

    public static final class General extends AMD64EirRegister implements StaticFieldName, PoolObject {

        private General(int ordinal) {
            super(ordinal);
        }

        @Override
        public Kind kind() {
            return Kind.WORD;
        }

        public static final General RAX = new General(0);
        public static final General RCX = new General(1);
        public static final General RDX = new General(2);
        public static final General RBX = new General(3);
        public static final General RSP = new General(4);
        public static final General RBP = new General(5);
        public static final General RSI = new General(6);
        public static final General RDI = new General(7);
        public static final General R8 = new General(8);
        public static final General R9 = new General(9);
        public static final General R10 = new General(10);
        public static final General R11 = new General(11);
        public static final General R12 = new General(12);
        public static final General R13 = new General(13);
        public static final General R14 = new General(14);
        public static final General R15 = new General(15);

        private static final General[] _values = {RAX, RCX, RDX, RBX, RSP, RBP, RSI, RDI, R8, R9, R10, R11, R12, R13, R14, R15};

        public static final IndexedSequence<General> VALUES = new ArraySequence<General>(_values);

        private static final PoolSet<AMD64EirRegister> _poolSet = PoolSet.of(_pool, _values);

        public static PoolSet<AMD64EirRegister> poolSet() {
            return _poolSet;
        }

        public AMD64GeneralRegister8 as8() {
            return AMD64GeneralRegister8.ENUMERATOR.get(ordinal());
        }

        public AMD64GeneralRegister16 as16() {
            return AMD64GeneralRegister16.ENUMERATOR.get(ordinal());
        }

        public AMD64GeneralRegister32 as32() {
            return AMD64GeneralRegister32.ENUMERATOR.get(ordinal());
        }

        public AMD64GeneralRegister64 as64() {
            return AMD64GeneralRegister64.ENUMERATOR.get(ordinal());
        }

        public static General from(GeneralRegister register) {
            return _values[register.value()];
        }

        @Override
        public EirLocationCategory category() {
            return EirLocationCategory.INTEGER_REGISTER;
        }

        private String _name;

        public String name() {
            return _name;
        }

        public void setName(String name) {
            _name = name;
        }

        static {
            StaticFieldName.Static.initialize(General.class);
        }

        @Override
        public String toString() {
            return name();
        }

        @Override
        public TargetLocation toTargetLocation() {
            return new TargetLocation.IntegerRegister(serial());
        }
    }

    public static final class XMM extends AMD64EirRegister {

        private XMM(int ordinal) {
            super(ordinal);
        }

        @Override
        public Kind kind() {
            return Kind.DOUBLE;
        }

        public static final XMM XMM0 = new XMM(0);
        public static final XMM XMM1 = new XMM(1);
        public static final XMM XMM2 = new XMM(2);
        public static final XMM XMM3 = new XMM(3);
        public static final XMM XMM4 = new XMM(4);
        public static final XMM XMM5 = new XMM(5);
        public static final XMM XMM6 = new XMM(6);
        public static final XMM XMM7 = new XMM(7);
        public static final XMM XMM8 = new XMM(8);
        public static final XMM XMM9 = new XMM(9);
        public static final XMM XMM10 = new XMM(10);
        public static final XMM XMM11 = new XMM(11);
        public static final XMM XMM12 = new XMM(12);
        public static final XMM XMM13 = new XMM(13);
        public static final XMM XMM14 = new XMM(14);
        public static final XMM XMM15 = new XMM(15);

        private static final XMM[] _values = {XMM0, XMM1, XMM2, XMM3, XMM4, XMM5, XMM6, XMM7, XMM8, XMM9, XMM10, XMM11, XMM12, XMM13, XMM14, XMM15};

        public static final IndexedSequence<XMM> VALUES = new ArraySequence<XMM>(_values);

        private static final PoolSet<AMD64EirRegister> _poolSet = PoolSet.of(_pool, _values);

        public static PoolSet<AMD64EirRegister> poolSet() {
            return _poolSet;
        }

        public AMD64XMMRegister as() {
            return AMD64XMMRegister.ENUMERATOR.get(ordinal());
        }

        public static XMM from(AMD64XMMRegister register) {
            return _values[register.value()];
        }

        @Override
        public EirLocationCategory category() {
            return EirLocationCategory.FLOATING_POINT_REGISTER;
        }

        @Override
        public String toString() {
            return as().name();
        }

        @Override
        public TargetLocation toTargetLocation() {
            return new TargetLocation.FloatingPointRegister(ordinal());
        }
    }
}
