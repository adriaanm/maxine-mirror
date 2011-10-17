/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.examples;

import com.oracle.max.graal.examples.deopt.*;
import com.oracle.max.graal.examples.inlining.*;
import com.oracle.max.graal.examples.intrinsics.*;
import com.oracle.max.graal.examples.mock.*;
import com.oracle.max.graal.examples.opt.*;
import com.oracle.max.graal.examples.simple.*;

public class Main {

    public static void main(String[] args) {
        System.err.println("== Graal Examples ==");
        if (args.length == 1) {
            if (args[0].equals("simple")) {
                SimpleExample.run();
            } else if (args[0].equals("inlining")) {
                InliningExample.run();
            } else if (args[0].equals("safeadd")) {
                SafeAddExample.run();
            } else if (args[0].equals("mock")) {
                MockExample.run();
            } else if (args[0].equals("opt")) {
                OptimizationExample.run();
            } else if (args[0].equals("deopt")) {
                DeoptExample.run();
            } else {
                System.out.println("unknown example: " + args[0]);
            }
        } else {
            System.out.println("usage: java " + Main.class.getSimpleName() + " <example>");
        }
    }
}
