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
package com.sun.max.jdwp.vm.proxy;

import com.sun.max.jdwp.vm.core.JDWPPlus;

/**
 * Class representing a compiled version of the method. This interface is not needed for the JDWP protocol.
 *  <br>
 *  <strong>Note:</strong> methods disabled for the time being, as not needed for JDWP and their
 *  implementations were cluttering {@link TeleCompiledMethod}.  (mlvdv 5/11/2010)
 *
 */
@JDWPPlus
public interface TargetMethodAccess {

    /**
     * @return an object that allows access to the machine code instructions of this compiled method
     */
//    @ConstantReturnValue
//    MachineCodeInstructionArray getTargetCodeInstructions();

    /**
     * A target method always represents one Java method.
     *
     * @return the JDWP method object of this target method
     */
//    @ConstantReturnValue
//    MethodProvider getMethodProvider();

}
