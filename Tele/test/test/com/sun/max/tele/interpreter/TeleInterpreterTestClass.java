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
package test.com.sun.max.tele.interpreter;

/**
 * A class on which to test the interpreter locally.
 *
 * @author Athul Acharya
 */
public class TeleInterpreterTestClass {

    protected TeleInterpreterTestClass() {
    }

    protected TeleInterpreterTestClass(int y) {
        _y = y;
    }

    private static int _x = 0xdeadbeef;

    public int _y = 0xcafebabe;

    public static int iadd(int a, int b) {
        return a + b;
    }

    public static int argpad(double a, int b, double c, int d, double e, int f) {
        return b + d + f;
    }

    public static int aastore_iastore_aaload_iaload(int[][] a, int[] b) {
        b[0] = 10;
        a[1] = b;
        return a[0][0] + a[1][0];
    }

    public static int getstatic() {
        final int y = _x;
        return y;
    }

    public static int putstatic() {
        _x = 4;
        return _x;
    }

    public static int getfield(TeleInterpreterTestClass i) {
        return i._y;
    }

    public static int putfield(TeleInterpreterTestClass i) {
        i._y = 4;
        return i._y;
    }

    public int return_y() {
        return _y;
    }

    public static int invokevirtual1(TeleInterpreterTestClass i) {
        return i.return_y();
    }

    public int virtual_argpad(double a, int b, double c, int d, double e, int f) {
        return b + d + f;
    }

    public static int invokevirtual2(TeleInterpreterTestClass i) {
        return i.virtual_argpad(1.0, 2, 3.0, 4, 5.0, 6);
    }

    public int virtual_overriden(int a, int b, int c) {
        return 3;
    }

    public static int invokevirtual3(TeleInterpreterTestClass i) {
        return i.virtual_overriden(1, 2, 3);
    }

    public static int invokespecial_super(TeleInterpreterTestChildClass i) {
        return i.invokespecial_super(1, 2, 3);
    }

    private int special_private(double b, int a) {
        return a + (int) b;
    }

    public static int invokespecial_private(TeleInterpreterTestClass i) {
        return i.special_private(2.3, 1);
    }

    public static int invokestatic() {
        return getstatic();
    }

    public static int invokeinterface(TeleInterpreterTestInterface i) {
        return i.interfacemethod(3, 6);
    }

    public static int newarray() {
        final int[] a = new int[10];

        a[4] = 5;
        return a[4];
    }

    public static int anewarray() {
        final int[][] a = new int[2][];
        final int[] b = new int[2];

        a[0] = b;
        b[0] = 1;
        return a[0][0];
    }

    public static int arraylength(Object[] array) {
        return array.length;
    }

    public static void athrow1(Throwable t) throws Throwable {
        throw t;
    }

    public static int athrow2(Throwable t) {
        try {
            throw t;
        } catch (Throwable s) {
            //System.out.println(s);
            return 1;
        }
    }

    public static int athrow3(Throwable t) {
        try {
            athrow1(t);
        } catch (Throwable s) {
            return 3;
        }

        return 5;
    }

    public static int athrow4(Throwable t) throws Throwable {
        try {
            throw t;
        } catch (Exception s) {  //should work because we pass an Exception
            return 1;
        }
    }

    public static int athrow5(Throwable t) throws Throwable {
        try {
            throw t;
        } catch (Error s) {  //should not work because we pass an Exception
            //System.out.println(s);
            return 1;
        }
    }

    public static int athrow6() throws Exception {
        throw new Exception();
    }

    public static int checkcast(Object o) throws ClassCastException {
        final TeleInterpreterTestClass i = (TeleInterpreterTestClass) o;
        return i._y;
    }

    public static boolean instanceof_(Object o) {
        return o instanceof TeleInterpreterTestClass;
    }

    public static int multianewarray() {
        final int[][][] a = new int[4][5][6];

        a[3][4][5] = 6;
        a[0][0][0] = 7;

        return a[3][4][5] +  a[0][0][0];
    }

    public static int new1() {
        final TeleInterpreterTestClass i = new TeleInterpreterTestClass();
        return i._y;
    }

    public static int new2() {
        final TeleInterpreterTestClass i = new TeleInterpreterTestClass(0x80081355);
        return i._y;
    }

    public static int println() {
        System.out.println("hello, world!");
        return 500;
    }
}
