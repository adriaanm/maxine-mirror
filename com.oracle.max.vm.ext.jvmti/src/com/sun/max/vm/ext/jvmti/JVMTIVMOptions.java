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
package com.sun.max.vm.ext.jvmti;

import com.sun.max.vm.*;

public class JVMTIVMOptions {
    /*
     * Possibly temporary hack to add jdwp logging options when run by debugger, e.g.  Eclipse.
     */
    static VMBooleanXXOption jdwpLogOption = new VMBooleanXXOption("-XX:-JDWPLog", "force JDWP logging");

    static {
        VMOptions.register(new PathAgentVMOption(), MaxineVM.Phase.PRISTINE);
        VMOptions.register(new LibAgentVMOption(), MaxineVM.Phase.PRISTINE);
        VMOptions.register(new RunAgentVMOption(), MaxineVM.Phase.PRISTINE);
        VMOptions.register(new VMOption("-Xdebug ", "(deprecated) debugging support"), MaxineVM.Phase.PRISTINE);
        VMOptions.register(jdwpLogOption, MaxineVM.Phase.PRISTINE);
    }
}
