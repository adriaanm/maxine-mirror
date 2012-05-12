/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package demo.jvmti;

/**
 * Program used to debug Maxine's breakpoint implementation. Basic test 03.
 * Tests that a method does not get optimized after a breakpoint is set.
 * Usage:
 * <ol>
 * <li>Set a breakpoint at {@link #foo}.</li>
 * <li>Run the program, should hit breakpoint.</li>
 * <li>Disable the breakpoint at {@link #foo}, set one at {@link #bar} and continue</li>
 * <li>Should stop in bar. Re-enable the breakpoint at {@link #foo}</li>
 * <li>Continue, should take the breakpoint.
 * </ol>
 * N.B. This test isn't definitive since, even if {@link #foo} were optimized, it should be
 * de-optimized when the breakpoint is re-enabled. However, by tracing compilations, the correct
 * behavior can be observed.
 *
 */

public class BreakpointTest_basic03 {
    public static void main(String[] args) {
        foo();
        tryOptFoo();
        bar();
    }

    private static void foo() {
    }

    private static void bar() {
        foo();
    }

    private static void tryOptFoo() {
        for (int i = 0; i < 10000; i++) {
            foo();
        }
    }
}
