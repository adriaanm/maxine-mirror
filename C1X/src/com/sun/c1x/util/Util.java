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
package com.sun.c1x.util;

import java.util.*;

import com.sun.c1x.*;
import com.sun.c1x.debug.*;
import com.sun.c1x.ir.*;
import com.sun.cri.ci.*;
import com.sun.cri.ci.CiDebugInfo.Frame;
import com.sun.cri.ri.*;

/**
 * The {@code Util} class contains a motley collection of utility methods used throughout the compiler.
 *
 * @author Ben L. Titzer
 * @author Doug Simon
 */
public class Util {

    public static final int PRINTING_LINE_WIDTH = 40;
    public static final char SECTION_CHARACTER = '*';
    public static final char SUB_SECTION_CHARACTER = '=';
    public static final char SEPERATOR_CHARACTER = '-';

    public static RuntimeException unimplemented() {
        throw new Error("unimplemented");
    }

    public static RuntimeException unimplemented(String msg) {
        throw new Error("unimplemented:" + msg);
    }

    public static RuntimeException shouldNotReachHere() {
        throw new Error("should not reach here");
    }

    public static <T> boolean replaceInList(T a, T b, List<T> list) {
        final int max = list.size();
        for (int i = 0; i < max; i++) {
            if (list.get(i) == a) {
                list.set(i, b);
                return true;
            }
        }
        return false;
    }

    public static <T> boolean replaceAllInList(T a, T b, List<T> list) {
        final int max = list.size();
        for (int i = 0; i < max; i++) {
            if (list.get(i) == a) {
                list.set(i, b);
            }
        }
        return false;
    }

    /**
     * Statically cast an object to an arbitrary Object type. Dynamically checked.
     */
    @SuppressWarnings("unchecked")
    public static <T> T uncheckedCast(Class<T> type, Object object) {
        return (T) object;
    }

    /**
     * Statically cast an object to an arbitrary Object type. Dynamically checked.
     */
    @SuppressWarnings("unchecked")
    public static <T> T uncheckedCast(Object object) {
        return (T) object;
    }

    /**
     * Utility method to combine a base hash with the identity hash of one or more objects.
     *
     * @param hash the base hash
     * @param x the object to add to the hash
     * @return the combined hash
     */
    public static int hash1(int hash, Object x) {
        // always set at least one bit in case the hash wraps to zero
        return 0x10000000 | (hash + 7 * System.identityHashCode(x));
    }

    /**
     * Utility method to combine a base hash with the identity hash of one or more objects.
     *
     * @param hash the base hash
     * @param x the first object to add to the hash
     * @param y the second object to add to the hash
     * @return the combined hash
     */
    public static int hash2(int hash, Object x, Object y) {
        // always set at least one bit in case the hash wraps to zero
        return 0x20000000 | (hash + 7 * System.identityHashCode(x) + 11 * System.identityHashCode(y));
    }

    /**
     * Utility method to combine a base hash with the identity hash of one or more objects.
     *
     * @param hash the base hash
     * @param x the first object to add to the hash
     * @param y the second object to add to the hash
     * @param z the third object to add to the hash
     * @return the combined hash
     */
    public static int hash3(int hash, Object x, Object y, Object z) {
        // always set at least one bit in case the hash wraps to zero
        return 0x30000000 | (hash + 7 * System.identityHashCode(x) + 11 * System.identityHashCode(y) + 13 * System.identityHashCode(z));
    }

    /**
     * Utility method to combine a base hash with the identity hash of one or more objects.
     *
     * @param hash the base hash
     * @param x the first object to add to the hash
     * @param y the second object to add to the hash
     * @param z the third object to add to the hash
     * @param w the fourth object to add to the hash
     * @return the combined hash
     */
    public static int hash4(int hash, Object x, Object y, Object z, Object w) {
        // always set at least one bit in case the hash wraps to zero
        return 0x40000000 | (hash + 7 * System.identityHashCode(x) + 11 * System.identityHashCode(y) + 13 * System.identityHashCode(z) + 17 * System.identityHashCode(w));
    }

    static {
        assert CiUtil.log2(2) == 1;
        assert CiUtil.log2(4) == 2;
        assert CiUtil.log2(8) == 3;
        assert CiUtil.log2(16) == 4;
        assert CiUtil.log2(32) == 5;
        assert CiUtil.log2(0x40000000) == 30;

        assert CiUtil.log2(2L) == 1;
        assert CiUtil.log2(4L) == 2;
        assert CiUtil.log2(8L) == 3;
        assert CiUtil.log2(16L) == 4;
        assert CiUtil.log2(32L) == 5;
        assert CiUtil.log2(0x4000000000000000L) == 62;

        assert !CiUtil.isPowerOf2(3);
        assert !CiUtil.isPowerOf2(5);
        assert !CiUtil.isPowerOf2(7);
        assert !CiUtil.isPowerOf2(-1);

        assert CiUtil.isPowerOf2(2);
        assert CiUtil.isPowerOf2(4);
        assert CiUtil.isPowerOf2(8);
        assert CiUtil.isPowerOf2(16);
        assert CiUtil.isPowerOf2(32);
        assert CiUtil.isPowerOf2(64);
    }

    /**
     * Sets the element at a given position of a list and ensures that this position exists. If the list is current
     * shorter than the position, intermediate positions are filled with a given value.
     *
     * @param list the list to put the element into
     * @param pos the position at which to insert the element
     * @param x the element that should be inserted
     * @param filler the filler element that is used for the intermediate positions in case the list is shorter than pos
     */
    public static <T> void atPutGrow(List<T> list, int pos, T x, T filler) {
        if (list.size() < pos + 1) {
            while (list.size() < pos + 1) {
                list.add(filler);
            }
            assert list.size() == pos + 1;
        }

        assert list.size() >= pos + 1;
        list.set(pos, x);
    }

    public static void breakpoint() {
        // do nothing.
    }

    public static void guarantee(boolean b, String string) {
        if (!b) {
            throw new CiBailout(string);
        }
    }

    public static boolean is8bit(long l) {
        return l < 128 && l >= -128;
    }

    public static void warning(String string) {
    }

    public static int safeToInt(long l) {
        assert (int) l == l;
        return (int) l;
    }

    public static int roundUp(int number, int mod) {
        return ((number + mod - 1) / mod) * mod;
    }

    public static void truncate(List<?> list, int length) {
        while (list.size() > length) {
            list.remove(list.size() - 1);
        }
    }

    public static void printBytes(String name, byte[] array, int bytesPerLine) {
        printBytes(name, array, array.length, bytesPerLine);
    }

    public static void printSection(String name, char sectionCharacter) {

        String header = " " + name + " ";
        int remainingCharacters = PRINTING_LINE_WIDTH - header.length();
        int leftPart = remainingCharacters / 2;
        int rightPart = remainingCharacters - leftPart;
        for (int i = 0; i < leftPart; i++) {
            TTY.print(sectionCharacter);
        }

        TTY.print(header);

        for (int i = 0; i < rightPart; i++) {
            TTY.print(sectionCharacter);
        }

        TTY.println();
    }

    public static void printBytes(String name, byte[] array, int length, int bytesPerLine) {
        assert bytesPerLine > 0;
        TTY.println("%s: %d bytes", name, length);
        for (int i = 0; i < length; i++) {
            TTY.print("%02x ", array[i]);
            if (i % bytesPerLine == bytesPerLine - 1) {
                TTY.println();
            }
        }

        if (length % bytesPerLine != bytesPerLine) {
            TTY.println();
        }
    }

    public static CiKind[] signatureToKinds(RiSignature signature, CiKind receiverKind) {
        int args = signature.argumentCount(false);
        CiKind[] result;
        int i = 0;
        if (receiverKind != null) {
            result = new CiKind[args + 1];
            result[0] = receiverKind;
            i = 1;
        } else {
            result = new CiKind[args];
        }
        for (int j = 0; j < args; j++) {
            result[i + j] = signature.argumentKindAt(j);
        }
        return result;
    }

    public static <T> T nonFatalUnimplemented(T val) {
        if (C1XOptions.FatalUnimplemented) {
            throw new Error("unimplemented");
        }
        return val;
    }

    public static int nonFatalUnimplemented(int val) {
        if (C1XOptions.FatalUnimplemented) {
            throw new Error("unimplemented");
        }
        return val;
    }

    public static boolean nonFatalUnimplemented(boolean val) {
        if (C1XOptions.FatalUnimplemented) {
            throw new Error("unimplemented");
        }
        return val;
    }

    public static boolean isShiftCount(int x) {
        return 0 <= x && x < 32;
    }

    public static void nonFatalUnimplemented() {
        if (C1XOptions.FatalUnimplemented) {
            throw new Error("unimplemented");
        }
    }

    public static boolean isByte(int x) {
        return 0 <= x && x < 0x100;
    }

    public static boolean is8bit(int x) {
        return -0x80 <= x && x < 0x80;
    }

    public static boolean is16bit(int x) {
        return -0x8000 <= x && x < 0x8000;
    }

    public static short safeToShort(int v) {
        assert is16bit(v);
        return (short) v;
    }

    /**
     * Determines if the kinds of two given IR nodes are equal at the {@linkplain #archKind(CiKind) architecture}
     * level in the context of the {@linkplain C1XCompilation#compilation()} compilation.
     */
    public static boolean archKindsEqual(Value i, Value other) {
        return archKindsEqual(i.kind, other.kind);
    }

    /**
     * Determines if two given kinds are equal at the {@linkplain #archKind(CiKind) architecture} level
     * in the context of the {@linkplain C1XCompilation#compilation()} compilation.
     */
    public static boolean archKindsEqual(CiKind k1, CiKind k2) {
        C1XCompilation compilation = C1XCompilation.compilation();
        assert compilation != null : "missing compilation context";
        return compilation.archKindsEqual(k1, k2);
    }

    /**
     * Translates a given kind to a {@linkplain C1XCompilation#archKind(CiKind) canonical architecture}
     * kind in the context of the {@linkplain C1XCompilation#compilation() current} compilation.
     */
    public static CiKind archKind(CiKind kind) {
        C1XCompilation compilation = C1XCompilation.compilation();
        assert compilation != null : "missing compilation context";
        return compilation.archKind(kind);
    }


    /**
     * Checks that two instructions are equivalent, optionally comparing constants.
     * @param x the first instruction
     * @param y the second instruction
     * @param compareConstants {@code true} if equivalent constants should be considered equivalent
     * @return {@code true} if the instructions are equivalent; {@code false} otherwise
     */
    public static boolean equivalent(Instruction x, Instruction y, boolean compareConstants) {
        if (x == y) {
            return true;
        }
        if (compareConstants && x != null && y != null) {
            if (x.isConstant() && x.asConstant().equivalent(y.asConstant())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Converts a given instruction to a value string. The representation of an instruction as
     * a value is formed by concatenating the {@linkplain com.sun.cri.ci.CiKind#typeChar character} denoting its
     * {@linkplain Value#kind kind} and its {@linkplain Value#id()}. For example, <code>"i13"</code>.
     *
     * @param value the instruction to convert to a value string. If {@code value == null}, then "-" is returned.
     * @return the instruction representation as a string
     */
    public static String valueString(Value value) {
        return value == null ? "-" : "" + value.kind.typeChar + value.id();
    }

    /**
     * Prints a given list of {@link CiDebugInfo} objects to {@link TTY}.
     * <p>
     * Sample output:
     * <pre>
     *     java.lang.ClassLoader.loadClass(ClassLoader.java:296) [bci: 28], frame-ref-map: 0, 1, 4 [0x13]
     *         local[0] = stack:0:w
     *         local[1] = stack:1:w
     *         local[2] = stack:2:w
     *         local[3] = stack:4:w
     * </pre>
     */
    public static void printDebugInfoStack(CiDebugInfo[] infos, String indent) {
        for (CiDebugInfo info : infos) {
            String indentTwice = indent + indent;
            StringBuilder refMaps = new StringBuilder();
            if (info.hasRegisterRefMap()) {
                refMaps.append(", reg-ref-map:").append(CiBitMap.fromLong(info.registerRefMap));
            }
            if (info.hasStackRefMap()) {
                refMaps.append(", frame-ref-map: ").append(new CiBitMap(info.frameRefMap));
            }
            CiCodePos pos = info.codePos;
            while (pos != null) {
                TTY.println(CiUtil.appendLocation(new StringBuilder(indent), pos.method, pos.bci).append(refMaps).toString());
                refMaps.setLength(0);
                if (pos instanceof Frame) {
                    Frame frame = (Frame) pos;
                    String sep = "\n" + indentTwice;
                    TTY.println(CiUtil.appendValues(new StringBuilder(indentTwice), frame, sep).toString());
                }
                pos = pos.caller;
            }
        }
    }
}
