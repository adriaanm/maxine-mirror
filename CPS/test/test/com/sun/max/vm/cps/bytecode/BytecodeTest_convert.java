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
package test.com.sun.max.vm.cps.bytecode;

import test.com.sun.max.vm.cps.*;

import com.sun.max.vm.cps.ir.*;
import com.sun.max.vm.type.*;
import com.sun.max.vm.value.*;

public abstract class BytecodeTest_convert<Method_Type extends IrMethod> extends CompilerTestCase<Method_Type> {

    protected BytecodeTest_convert(String name) {
        super(name);
    }

    private long perform_i2l(int a) {
        return a;
    }

    public void test_i2l() {
        final Method_Type method = compileMethod("perform_i2l", SignatureDescriptor.create(long.class, int.class));
        new BytecodeConfirmation(method.classMethodActor()) {
            @Override
            public void i2l() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, IntValue.from(23));
        assertTrue(result.asLong() == 23);
    }

    private float perform_i2f(int a) {
        return a;
    }

    public void test_i2f() {
        final Method_Type method = compileMethod("perform_i2f", SignatureDescriptor.create(float.class, int.class));
        new BytecodeConfirmation(method.classMethodActor()) {
            @Override
            public void i2f() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, IntValue.from(23));
        assertTrue(result.asFloat() == 23);
    }

    private double perform_i2d(int a) {
        return a;
    }

    public void test_i2d() {
        final Method_Type method = compileMethod("perform_i2d", SignatureDescriptor.create(double.class, int.class));
        new BytecodeConfirmation(method.classMethodActor()) {
            @Override
            public void i2d() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, IntValue.from(23));
        assertTrue(result.asDouble() == 23);
    }

    private int perform_l2i(long a) {
        return (int) a;
    }

    public void test_l2i() {
        final Method_Type method = compileMethod("perform_l2i", SignatureDescriptor.create(int.class, long.class));
        new BytecodeConfirmation(method.classMethodActor()) {
            @Override
            public void l2i() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, LongValue.from(23));
        assertTrue(result.asInt() == 23);
    }

    private float perform_l2f(long a) {
        return a;
    }

    public void test_l2f() {
        final Method_Type method = compileMethod("perform_l2f", SignatureDescriptor.create(float.class, long.class));
        new BytecodeConfirmation(method.classMethodActor()) {
            @Override
            public void l2f() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, LongValue.from(23));
        assertTrue(result.asFloat() == 23);
    }

    private double perform_l2d(long a) {
        return a;
    }

    public void test_l2d() {
        final Method_Type method = compileMethod("perform_l2d", SignatureDescriptor.create(double.class, long.class));
        new BytecodeConfirmation(method.classMethodActor()) {
            @Override
            public void l2d() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, LongValue.from(23));
        assertTrue(result.asDouble() == 23);
    }

    private int perform_f2i(float a) {
        return (int) a;
    }

    public void test_f2i() {
        final Method_Type method = compileMethod("perform_f2i", SignatureDescriptor.create(int.class, float.class));
        new BytecodeConfirmation(method.classMethodActor()) {
            @Override
            public void f2i() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, FloatValue.from(23));
        assertTrue(result.asInt() == 23);
    }

    private long perform_f2l(float a) {
        return (long) a;
    }

    public void test_f2l() {
        final Method_Type method = compileMethod("perform_f2l", SignatureDescriptor.create(long.class, float.class));
        new BytecodeConfirmation(method.classMethodActor()) {
            @Override
            public void f2l() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, FloatValue.from(23));
        assertTrue(result.asLong() == 23);
    }

    private double perform_f2d(float a) {
        return a;
    }

    public void test_f2d() {
        final Method_Type method = compileMethod("perform_f2d", SignatureDescriptor.create(double.class, float.class));
        new BytecodeConfirmation(method.classMethodActor()) {
            @Override
            public void f2d() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, FloatValue.from(23));
        assertTrue(result.asDouble() == 23);
    }

    private int perform_d2i(double a) {
        return (int) a;
    }

    public void test_d2i() {
        final Method_Type method = compileMethod("perform_d2i", SignatureDescriptor.create(int.class, double.class));
        new BytecodeConfirmation(method.classMethodActor()) {
            @Override
            public void d2i() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, DoubleValue.from(23));
        assertTrue(result.asInt() == 23);
    }

    private long perform_d2l(double a) {
        return (long) a;
    }

    public void test_d2l() {
        final Method_Type method = compileMethod("perform_d2l", SignatureDescriptor.create(long.class, double.class));
        new BytecodeConfirmation(method.classMethodActor()) {
            @Override
            public void d2l() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, DoubleValue.from(23));
        assertTrue(result.asLong() == 23);
    }

    private float perform_d2f(double a) {
        return (float) a;
    }

    public void test_d2f() {
        final Method_Type method = compileMethod("perform_d2f", SignatureDescriptor.create(float.class, double.class));
        new BytecodeConfirmation(method.classMethodActor()) {
            @Override
            public void d2f() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, DoubleValue.from(23));
        assertTrue(result.asFloat() == 23);
    }

    private byte perform_i2b(int a) {
        return (byte) a;
    }

    public void test_i2b() {
        final Method_Type method = compileMethod("perform_i2b", SignatureDescriptor.create(byte.class, int.class));
        new BytecodeConfirmation(method.classMethodActor()) {
            @Override
            public void i2b() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, IntValue.from(23));
        assertTrue(result.asByte() == 23);
    }

    private char perform_i2c(int a) {
        return (char) a;
    }

    public void test_i2c() {
        final Method_Type method = compileMethod("perform_i2c", SignatureDescriptor.create(char.class, int.class));
        new BytecodeConfirmation(method.classMethodActor()) {
            @Override
            public void i2c() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, IntValue.from(23));
        assertTrue(result.asChar() == 23);
    }

    private short perform_i2s(int a) {
        return (short) a;
    }

    public void test_i2s() {
        final Method_Type method = compileMethod("perform_i2s", SignatureDescriptor.create(short.class, int.class));
        new BytecodeConfirmation(method.classMethodActor()) {
            @Override
            public void i2s() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, IntValue.from(23));
        assertTrue(result.asShort() == 23);
    }

}
