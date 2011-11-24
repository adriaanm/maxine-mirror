/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.max.graal.snippets;

public class ArrayCopySnippets {

    public static void arraycopy(int[] src, int srcPos, int[] dest, int destPos, int length) {
        if (src == null || dest == null) {
            throw new NullPointerException();
        }
        if (srcPos < 0 || destPos < 0 || length < 0 || srcPos + length > src.length || destPos + length > dest.length) {
            throw new IndexOutOfBoundsException();
        }
        if (src == dest) { //aliased case
            if (srcPos > destPos) {
                for (int i = 0; i < length; i++) {
                    src[i + destPos] = src[i + srcPos];
                }
            } else if (srcPos < destPos) {
                for (int i = length; i > 0; i--) {
                    src[i + destPos] = src[i + srcPos];
                }
            }
        } else {
            for (int i = 0; i < length; i++) {
                dest[i + destPos] = src[i + srcPos];
            }
        }
    }

    public static void arraycopy(char[] src, int srcPos, char[] dest, int destPos, int length) {
        if (src == null || dest == null) {
            throw new NullPointerException();
        }
        if (srcPos < 0 || destPos < 0 || length < 0 || srcPos + length > src.length || destPos + length > dest.length) {
            throw new IndexOutOfBoundsException();
        }
        if (src == dest) { //aliased case
            if (srcPos > destPos) {
                for (int i = 0; i < length; i++) {
                    src[i + destPos] = src[i + srcPos];
                }
            } else if (srcPos < destPos) {
                for (int i = length; i > 0; i--) {
                    src[i + destPos] = src[i + srcPos];
                }
            }
        } else {
            for (int i = 0; i < length; i++) {
                dest[i + destPos] = src[i + srcPos];
            }
        }
    }

    public static void arraycopy(long[] src, int srcPos, long[] dest, int destPos, int length) {
        if (src == null || dest == null) {
            throw new NullPointerException();
        }
        if (srcPos < 0 || destPos < 0 || length < 0 || srcPos + length > src.length || destPos + length > dest.length) {
            throw new IndexOutOfBoundsException();
        }
        if (src == dest) { //aliased case
            if (srcPos > destPos) {
                for (int i = 0; i < length; i++) {
                    src[i + destPos] = src[i + srcPos];
                }
            } else if (srcPos < destPos) {
                for (int i = length; i > 0; i--) {
                    src[i + destPos] = src[i + srcPos];
                }
            }
        } else {
            for (int i = 0; i < length; i++) {
                dest[i + destPos] = src[i + srcPos];
            }
        }
    }
}
