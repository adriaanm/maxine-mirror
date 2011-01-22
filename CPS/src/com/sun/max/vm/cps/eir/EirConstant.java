/*
 * Copyright (c) 2007, 2009, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.vm.cps.eir;

import com.sun.max.vm.type.*;
import com.sun.max.vm.value.*;

/**
 * @author Bernd Mathiske
 */
public class EirConstant extends EirValue {

    @Override
    public final boolean isConstant() {
        return true;
    }

    private Value value;

    @Override
    public final Value value() {
        return value;
    }

    public EirConstant(Value value) {
        this.value = value;
    }

    @Override
    public final Kind kind() {
        return value.kind();
    }

    @Override
    public String toString() {
        String s = "<" + value.toString() + ">";
        if (location() != null) {
            s += "@" + location();
        }
        return s;
    }

    public int compareTo(EirConstant other) {
        if (this == other) {
            return 0;
        }
        return value.compareTo(other.value());
    }

    public static final class Reference extends EirConstant {
        private int serial;

        public Reference(Value value, int serial) {
            super(value);
            this.serial = serial;
        }

        @Override
        public int compareTo(EirConstant other) {
            if (this == other) {
                return 0;
            }
            if (other instanceof Reference) {
                final Reference referenceConstant = (Reference) other;
                assert serial != referenceConstant.serial;
                return serial > referenceConstant.serial ? 1 : -1;
            }
            return 1;
        }
    }

}
