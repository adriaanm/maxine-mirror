/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.vm.compiler;

import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.compiler.target.*;

/**
 * This interface represents an observer that can be installed with a {@link CompilationScheme} which
 * is notified before and after compilation of methods.
 *
 * @author Ben L. Titzer
 */
public interface CompilationObserver {

    /**
     * This method allows an observer to be notified before the compilation of a method begins.
     * @param classMethodActor the method being compiled
     * @param compiler the compiler performing the compilation
     */
    void observeBeforeCompilation(ClassMethodActor classMethodActor, RuntimeCompilerScheme compiler);

    /**
     * This method allows an observer to be notified after the compilation of a method completes.
     * @param classMethodActor the method being compiled
     * @param compiler the compiler performing the compilation
     * @param targetMethod the target method produced by the compilation; {@code null} if the compilation
     */
    void observeAfterCompilation(ClassMethodActor classMethodActor, RuntimeCompilerScheme compiler, TargetMethod targetMethod);
}
