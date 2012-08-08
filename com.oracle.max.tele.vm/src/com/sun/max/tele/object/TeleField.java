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

import java.lang.reflect.*;

import com.sun.max.tele.*;
import com.sun.max.tele.reference.*;

/**
* Canonical surrogate for an object of type {@link Field} in the VM.
*
*/
public class TeleField extends TeleTupleObject {

    private Field field;

    protected TeleField(TeleVM vm, RemoteReference fieldReference) {
        super(vm, fieldReference);
    }

    /**
     * @return the local instance of {@link Field} equivalent to this object in the VM.
     */
    public Field toJava() {
        if (field == null) {
            final RemoteReference fieldActorReference = fields().Field_fieldActor.readRemoteReference(reference());
            final TeleFieldActor teleFieldActor = (TeleFieldActor) objects().makeTeleObject(fieldActorReference);
            field = teleFieldActor.fieldActor().toJava();
        }
        return field;
    }
    @Override
    public String maxineRole() {
        return "Field";
    }

    @Override
    public String maxineTerseRole() {
        return "Field";
    }

}
