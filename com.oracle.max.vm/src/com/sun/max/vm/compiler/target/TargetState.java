/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.vm.compiler.target;

import com.sun.max.*;
import com.sun.max.program.ProgramError;
import com.sun.max.vm.actor.member.*;

/**
 * This class implements utility functions for transitioning a {@link ClassMethodActor}
 * from one target state to another as a result of compilation.
 */
public class TargetState {
    private static final TargetMethod[] NOT_COMPILED = {};

    public static int targetMethodCount(Object targetState) {
        if (targetState == null || targetState instanceof Throwable) {
            // not compiled yet
            return 0;
        } else if (targetState instanceof TargetMethod) {
            // only compiled once
            return 1;
        } else if (targetState instanceof TargetMethod[]) {
            // compiled multiple times
            return ((TargetMethod[]) targetState).length;
        } else if (targetState instanceof Compilation) {
            // currently being compiled, return previous count of target methods
            return targetMethodCount(((Compilation) targetState).previousTargetState);
        }
        return 0;
    }

    public static TargetMethod currentTargetMethod(Object targetState) {
        if (targetState == null) {
            // not compiled yet
            return null;
        } else if (targetState instanceof TargetMethod) {
            // only compiled once
            return (TargetMethod) targetState;
        } else if (targetState instanceof TargetMethod[]) {
            // compiled multiple times, return latest
            return ((TargetMethod[]) targetState)[0];
        } else if (targetState instanceof Compilation) {
            // currently being compiled, return any previous target method
            return currentTargetMethod(((Compilation) targetState).previousTargetState);
        } else if (targetState instanceof Throwable) {
            return null;
        }
        return null;
    }

    public static TargetMethod[] targetMethodHistory(Object targetState) {
        if (targetState == null) {
            // not compiled yet
            return NOT_COMPILED;
        } else if (targetState instanceof TargetMethod) {
            // only compiled once
            return new TargetMethod[] {(TargetMethod) targetState};
        } else if (targetState instanceof TargetMethod[]) {
            // compiled multiple times
            return (TargetMethod[]) targetState;
        } else if (targetState instanceof Compilation) {
            // currently being compiled
            return targetMethodHistory(((Compilation) targetState).previousTargetState);
        } else if (targetState instanceof Throwable) {
            return NOT_COMPILED;
        }
        return NOT_COMPILED;
    }

    public static Object addTargetMethod(TargetMethod targetMethod, Object targetState) {
        if (targetState == null || targetState instanceof Throwable) {
            // not compiled yet
            return targetMethod;
        } else if (targetState instanceof TargetMethod) {
            // only compiled once, make into an array
            return new TargetMethod[] {targetMethod, (TargetMethod) targetState};
        } else if (targetState instanceof TargetMethod[]) {
            // compiled multiple times, make this the latest
            return Utils.prepend((TargetMethod[]) targetState, targetMethod);
        }
        throw ProgramError.unexpected("Unknown or invalid TargetState: " + targetState);
    }
}
