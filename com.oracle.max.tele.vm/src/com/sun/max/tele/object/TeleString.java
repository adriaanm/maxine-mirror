/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.tele.object;

import com.sun.max.jdwp.vm.proxy.*;
import com.sun.max.tele.*;
import com.sun.max.vm.classfile.constant.*;
import com.sun.max.vm.reference.*;

/**
 * Canonical surrogate for an object of type {@link String} in the VM.
 */
public class TeleString extends TeleTupleObject implements StringProvider {

    private String string;

    public String getString() {
        if (status().isNotDeadYet()) {
            String s = vm().getString(reference());
            if (s != null) {
                string = SymbolTable.intern(s);
            }
        }
        return string;
    }

    protected TeleString(TeleVM vm, Reference stringReference) {
        super(vm, stringReference);
    }

    @Override
    protected Object createDeepCopy(DeepCopier context) {
        // Translate into local equivalent
        return getString();
    }

    @Override
    public String maxineRole() {
        return "String";
    }

    @Override
    public String maxineTerseRole() {
        return "String.";
    }

    public String stringValue() {
        return getString();
    }

}
