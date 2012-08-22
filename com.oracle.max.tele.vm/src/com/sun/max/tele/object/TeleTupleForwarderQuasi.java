/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import com.sun.max.tele.*;
import com.sun.max.tele.reference.*;
import com.sun.max.vm.actor.member.*;


/**
 * Canonical surrogate for a <em>quasi</em> object:  the old copy of a tuple object
 * that has been relocated by copying to a new location in the current GC cycle.
 */
public class TeleTupleForwarderQuasi extends TeleTupleObject {

    protected TeleTupleForwarderQuasi(TeleVM vm, RemoteReference quasiReference) {
        super(vm, quasiReference);
        assert quasiReference.status().isForwarder();
    }

    @Override
    public Set<FieldActor> getFieldActors() {
        TeleHub teleHub = getTeleHub();
        if (teleHub instanceof TeleStaticHub) {
            // Static tuples do not inherit fields; return only the local static fields.
            final Set<FieldActor> staticFieldActors = new HashSet<FieldActor>();
            for (FieldActor fieldActor : classActorForObjectType().localStaticFieldActors()) {
                staticFieldActors.add(fieldActor);
            }
            return staticFieldActors;
        }
        return super.getFieldActors();
    }


    @Override
    protected Object createDeepCopy(DeepCopier context) {
        return null;
    }
}
