/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package test.com.sun.max.collect;

import java.util.*;

import com.sun.max.collect.*;
import com.sun.max.ide.*;

/**
 * Tests for {@link IntHashMap}.
 */
public class IntHashMapTest extends MaxTestCase {

    public IntHashMapTest(String name) {
        super(name);
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(IntHashMapTest.class);
    }

    private static final int N = 1000;

    private final Integer[] integers = new Integer[N];

    private void initialize() {
        for (int i = 0; i < N; i++) {
            integers[i] = new Integer(i);
        }
    }

    private void check(IntHashMap<Object> table, int n) {
        for (int i = 0; i < n; i++) {
            final Object entry = table.get(i);
            assertSame(entry, integers[i]);
        }
    }

    public void test_serialPut() {
        initialize();
        final IntHashMap<Object> table = new IntHashMap<Object>();
        for (int i = 0; i < N; i++) {
            assertEquals(table.get(i), null);
            table.put(i, integers[i] + "");
            table.put(i, integers[i]);
            check(table, i);
        }
    }

    public void test_randomPut() {
        initialize();
        final IntHashMap<Object> table = new IntHashMap<Object>();
        final Random random = new Random();
        final int[] keys = new int[N];
        for (int i = 0; i < N; i++) {
            int k = 0;
            do {
                k = random.nextInt();
            } while (table.get(k) != null);
            keys[i] = k;
            table.put(k, integers[i] + "");
            table.put(k, integers[i]);
        }
        for (int i = 0; i < N; i++) {
            assertSame(table.get(keys[i]), integers[i]);
        }
    }

}
