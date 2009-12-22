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
package test.com.sun.max.vm.compiler.cps.bytecode;

import java.lang.reflect.*;

import test.com.sun.max.vm.compiler.cps.*;

import com.sun.max.vm.classfile.create.*;
import com.sun.max.vm.compiler.cps.ir.*;
import com.sun.max.vm.type.*;
import com.sun.max.vm.value.*;

public abstract class BytecodeTest_const<Method_Type extends IrMethod> extends CompilerTestCase<Method_Type> {

    protected BytecodeTest_const(String name) {
        super(name);
    }

    private Object perform_aconstnull() {
        return null;
    }

    public void test_aconstnull() {
        final Method_Type method = compileMethod("perform_aconstnull", SignatureDescriptor.create(Object.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override
            public void aconst_null() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method);
        assertTrue(result.asObject() == null);
    }

    private int perform_iconst_m1() {
        return -1;
    }

    public void test_iconst_m1() {
        final Method_Type method = compileMethod("perform_iconst_m1", SignatureDescriptor.create(int.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override
            public void iconst_m1() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method);
        assertTrue(result.asInt() == -1);
    }

    private int perform_iconst_0() {
        return 0;
    }

    public void test_iconst_0() {
        final Method_Type method = compileMethod("perform_iconst_0", SignatureDescriptor.create(int.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override
            public void iconst_0() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method);
        assertTrue(result.asInt() == 0);
    }

    private int perform_iconst_1() {
        return 1;
    }

    public void test_iconst_1() {
        final Method_Type method = compileMethod("perform_iconst_1", SignatureDescriptor.create(int.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override
            public void iconst_1() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method);
        assertTrue(result.asInt() == 1);
    }

    private int perform_iconst_2() {
        return 2;
    }

    public void test_iconst_2() {
        final Method_Type method = compileMethod("perform_iconst_2", SignatureDescriptor.create(int.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override
            public void iconst_2() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method);
        assertTrue(result.asInt() == 2);
    }

    private int perform_iconst_3() {
        return 3;
    }

    public void test_iconst_3() {
        final Method_Type method = compileMethod("perform_iconst_3", SignatureDescriptor.create(int.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override
            public void iconst_3() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method);
        assertTrue(result.asInt() == 3);
    }

    private int perform_iconst_4() {
        return 4;
    }

    public void test_iconst_4() {
        final Method_Type method = compileMethod("perform_iconst_4", SignatureDescriptor.create(int.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override
            public void iconst_4() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method);
        assertTrue(result.asInt() == 4);
    }

    private int perform_iconst_5() {
        return 5;
    }

    public void test_iconst_5() {
        final Method_Type method = compileMethod("perform_iconst_5", SignatureDescriptor.create(int.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override
            public void iconst_5() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method);
        assertTrue(result.asInt() == 5);
    }

    private long perform_lconst_0() {
        return 0L;
    }

    public void test_lconst_0() {
        final Method_Type method = compileMethod("perform_lconst_0", SignatureDescriptor.create(long.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override
            public void lconst_0() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method);
        assertTrue(result.asLong() == 0L);
    }

    private long perform_lconst_1() {
        return 1L;
    }

    public void test_lconst_1() {
        final Method_Type method = compileMethod("perform_lconst_1", SignatureDescriptor.create(long.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override
            public void lconst_1() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method);
        assertTrue(result.asLong() == 1L);
    }

    private float perform_fconst_0() {
        return (float) 0.0;
    }

    public void test_fconst_0() {
        final Method_Type method = compileMethod("perform_fconst_0", SignatureDescriptor.create(float.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override
            public void fconst_0() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method);
        assertTrue(result.asFloat() == (float) 0.0);
    }

    private float perform_fconst_1() {
        return (float) 1.0;
    }

    public void test_fconst_1() {
        final Method_Type method = compileMethod("perform_fconst_1", SignatureDescriptor.create(float.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override
            public void fconst_1() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method);
        assertTrue(result.asFloat() == (float) 1.0);
    }

    private float perform_fconst_2() {
        return (float) 2.0;
    }

    public void test_fconst_2() {
        final Method_Type method = compileMethod("perform_fconst_2", SignatureDescriptor.create(float.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override
            public void fconst_2() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method);
        assertTrue(result.asFloat() == (float) 2.0);
    }

    private double perform_dconst_0() {
        return 0.0;
    }

    public void test_dconst_0() {
        final Method_Type method = compileMethod("perform_dconst_0", SignatureDescriptor.create(double.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override
            public void dconst_0() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method);
        assertTrue(result.asDouble() == 0.0);
    }

    private double perform_dconst_1() {
        return 1.0;
    }

    public void test_dconst_1() {
        final Method_Type method = compileMethod("perform_dconst_1", SignatureDescriptor.create(double.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override
            public void dconst_1() {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method);
        assertTrue(result.asDouble() == 1.0);
    }

    protected static final int BIPUSH_RESULT = 123;

    private int perform_bipush() {
        return BIPUSH_RESULT;
    }

    public void test_bipush() {
        final Method_Type method = compileMethod("perform_bipush", SignatureDescriptor.create(int.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override
            public void bipush(int operand) {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method);
        assertTrue(result.asInt() == BIPUSH_RESULT);
    }

    protected static final int SIPUSH_RESULT = -245;

    private int perform_sipush() {
        return SIPUSH_RESULT;
    }

    public void test_sipush() {
        final Method_Type method = compileMethod("perform_sipush", SignatureDescriptor.create(int.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override
            public void sipush(int operand) {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method);
        assertTrue(result.asInt() == SIPUSH_RESULT);
    }

    protected static final int LDC_RESULT = 12345678;

    private int perform_ldc() {
        return LDC_RESULT;
    }

    public void test_ldc() {
        final Method_Type method = compileMethod("perform_ldc", SignatureDescriptor.create(int.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override
            public void ldc(int index) {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method);
        assertTrue(result.asInt() == LDC_RESULT);
    }

    protected static final int LDCW_RESULT = 87654321;

    public void test_ldc_w() {
        final String className = getClass().getName() + "_test_ldc_w";

        final MillClass millClass = new MillClass(Modifier.PUBLIC, className, Object.class.getName());
        final MillCode code = new MillCode(0, 1);
        code.ldc_w(millClass.makeIntConstant(LDCW_RESULT));
        code.ireturn();
        millClass.addMethod(Modifier.PUBLIC + Modifier.STATIC, "perform_ldc_w", SignatureDescriptor.create(int.class), code);
        final byte[] classfileBytes = millClass.generate();

        final Method_Type method = compileMethod(className, classfileBytes, "perform_ldc_w", SignatureDescriptor.create(int.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override
            public void ldc_w(int index) {
                confirmPresence();
            }
        };
        final Value result = execute(method);
        assertTrue(result.asInt() == LDCW_RESULT);
    }

    protected static final double LDC2W_RESULT = 234234.23423;

    private double perform_ldc2_w() {
        return LDC2W_RESULT;
    }

    public void test_ldc2_w() {
        final Method_Type method = compileMethod("perform_ldc2_w", SignatureDescriptor.create(double.class));
        new BytecodeConfirmation(method.classMethodActor()) {

            @Override
            public void ldc2_w(int index) {
                confirmPresence();
            }
        };
        final Value result = executeWithReceiver(method);
        assertTrue(result.asDouble() == LDC2W_RESULT);
    }
}
