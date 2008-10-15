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
package test.com.sun.max.vm.compiler.bytecode;

import test.com.sun.max.vm.compiler.*;

import com.sun.max.vm.compiler.ir.*;
import com.sun.max.vm.type.*;
import com.sun.max.vm.value.*;

public abstract class BytecodeTest_binaryArithmetic<Method_Type extends IrMethod> extends CompilerTestCase<Method_Type> {

    protected BytecodeTest_binaryArithmetic(String name) {
        super(name);
    }

    private int perform_iadd(int a, int b) {
        return a + b;
    }

    public void test_iadd() {
        final Method_Type method = compileMethod("perform_iadd", SignatureDescriptor.create(int.class, int.class, int.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override public void iadd() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, IntValue.from(3), IntValue.from(20));
        assertTrue(result.asInt() == 23);
    }

    private long perform_ladd(long a, long b) {
        return a + b;
    }

    public void test_ladd() {
        final Method_Type method = compileMethod("perform_ladd", SignatureDescriptor.create(long.class, long.class, long.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override public void ladd() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, LongValue.from(23), LongValue.from(57));
        assertTrue(result.asLong() == 80);
    }

    private float perform_fadd(float a, float b) {
        return a + b;
    }

    public void test_fadd() {
        final Method_Type method = compileMethod("perform_fadd", SignatureDescriptor.create(float.class, float.class, float.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override public void fadd() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, FloatValue.from((float) 253.11), FloatValue.from((float) 54.43));
        assertTrue(result.asFloat() == (float) 307.54);
    }

    private double perform_dadd(double a, double b) {
        return a + b;
    }

    public void test_dadd() {
        final Method_Type method = compileMethod("perform_dadd", SignatureDescriptor.create(double.class, double.class, double.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override public void dadd() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, DoubleValue.from(311.65), DoubleValue.from(10.091));
        assertTrue(result.asDouble() == 321.741);
    }

    private int perform_isub(int a, int b) {
        return a - b;
    }

    public void test_isub() {
        final Method_Type method = compileMethod("perform_isub", SignatureDescriptor.create(int.class, int.class, int.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override public void isub() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, IntValue.from(3), IntValue.from(20));
        assertTrue(result.asInt() == -17);
    }

    private long perform_lsub(long a, long b) {
        return a - b;
    }

    public void test_lsub() {
        final Method_Type method = compileMethod("perform_lsub", SignatureDescriptor.create(long.class, long.class, long.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override public void lsub() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, LongValue.from(23), LongValue.from(57));
        assertTrue(result.asLong() == -34);
    }

    private float perform_fsub(float a, float b) {
        return a - b;
    }

    public void test_fsub() {
        final Method_Type method = compileMethod("perform_fsub", SignatureDescriptor.create(float.class, float.class, float.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override public void fsub() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, FloatValue.from((float) 253.11), FloatValue.from((float) 54.43));
        assertTrue(result.asFloat() == (float) 198.68);
    }

    private double perform_dsub(double a, double b) {
        return a - b;
    }

    public void test_dsub() {
        final Method_Type method = compileMethod("perform_dsub", SignatureDescriptor.create(double.class, double.class, double.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override public void dsub() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, DoubleValue.from(311.0), DoubleValue.from(10.0));
        assertTrue(result.asDouble() == 301.0);
    }

    private int perform_imul(int a, int b) {
        return a * b;
    }

    public void test_imul() {
        final Method_Type method = compileMethod("perform_imul", SignatureDescriptor.create(int.class, int.class, int.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override public void imul() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, IntValue.from(3), IntValue.from(20));
        assertTrue(result.asInt() == 60);
    }

    private long perform_lmul(long a, long b) {
        return a * b;
    }

    public void test_lmul() {
        final Method_Type method = compileMethod("perform_lmul", SignatureDescriptor.create(long.class, long.class, long.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override public void lmul() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, LongValue.from(23), LongValue.from(100));
        assertTrue(result.asLong() == 2300);
    }

    private float perform_fmul(float a, float b) {
        return a * b;
    }

    public void test_fmul() {
        final Method_Type method = compileMethod("perform_fmul", SignatureDescriptor.create(float.class, float.class, float.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override public void fmul() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, FloatValue.from((float) 253.11), FloatValue.from((float) 54.43));
        assertTrue(result.asFloat() == (float) 13776.7773);
    }

    private double perform_dmul(double a, double b) {
        return a * b;
    }

    public void test_dmul() {
        final Method_Type method = compileMethod("perform_dmul", SignatureDescriptor.create(double.class, double.class, double.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override public void dmul() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, DoubleValue.from(311.0), DoubleValue.from(10.0));
        assertTrue(result.asDouble() == 3110.0);
    }

    private int perform_idiv(int a, int b) {
        return a / b;
    }

    public void test_idiv() {
        final Method_Type method = compileMethod("perform_idiv", SignatureDescriptor.create(int.class, int.class, int.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override public void idiv() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, IntValue.from(21), IntValue.from(7));
        assertTrue(result.asInt() == 3);
    }

    private long perform_ldiv(long a, long b) {
        return a / b;
    }

    public void test_ldiv() {
        final Method_Type method = compileMethod("perform_ldiv", SignatureDescriptor.create(long.class, long.class, long.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override public void ldiv() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, LongValue.from(2345), LongValue.from(100));
        assertTrue(result.asLong() == 23);
    }

    private float perform_fdiv(float a, float b) {
        return a / b;
    }

    public void test_fdiv() {
        final Method_Type method = compileMethod("perform_fdiv", SignatureDescriptor.create(float.class, float.class, float.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override public void fdiv() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, FloatValue.from((float) 253.11), FloatValue.from((float) 54.43));
        assertTrue(result.asFloat() == (float) 4.650192908);
    }

    private double perform_ddiv(double a, double b) {
        return a / b;
    }

    public void test_ddiv() {
        final Method_Type method = compileMethod("perform_ddiv", SignatureDescriptor.create(double.class, double.class, double.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override public void ddiv() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, DoubleValue.from(311.0), DoubleValue.from(10.0));
        assertTrue(result.asDouble() == 31.1);
    }

    private int perform_irem(int a, int b) {
        return a % b;
    }

    public void test_irem() {
        final Method_Type method = compileMethod("perform_irem", SignatureDescriptor.create(int.class, int.class, int.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override public void irem() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, IntValue.from(23), IntValue.from(7));
        assertTrue(result.asInt() == 2);
    }

    private long perform_lrem(long a, long b) {
        return a % b;
    }

    public void test_lrem() {
        final Method_Type method = compileMethod("perform_lrem", SignatureDescriptor.create(long.class, long.class, long.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override public void lrem() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, LongValue.from(2345), LongValue.from(100));
        assertTrue(result.asLong() == 45);
    }

    private float perform_frem(float a, float b) {
        return a % b;
    }

    public void notest_frem() {
        final Method_Type method = compileMethod("perform_frem", SignatureDescriptor.create(float.class, float.class, float.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override public void frem() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, FloatValue.from((float) 253.11), FloatValue.from((float) 54.43));
        assertTrue(result.asFloat() == (float) 35.39);
    }

    private double perform_drem(double a, double b) {
        return a % b;
    }

    public void notest_drem() {
        final Method_Type method = compileMethod("perform_drem", SignatureDescriptor.create(double.class, double.class, double.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override public void drem() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method, DoubleValue.from(311.0), DoubleValue.from(10.0));
        assertTrue(result.asDouble() == 1.0);
    }
}
