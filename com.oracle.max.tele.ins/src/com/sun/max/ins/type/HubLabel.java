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
package com.sun.max.ins.type;

import java.awt.event.*;

import com.sun.max.ins.*;
import com.sun.max.ins.gui.*;
import com.sun.max.tele.*;
import com.sun.max.tele.object.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.actor.holder.*;

/**
 * A label specialized for displaying a reference in the header of a low-level
 * object in the VM to the object's {@link Hub}.
 */
public final class HubLabel extends InspectorLabel {

    private final TeleHub teleHub;

    private MaxVMState lastRefreshedState = null;

    public HubLabel(Inspection inspection, TeleHub hub) {
        super(inspection);
        this.teleHub = hub;
        addMouseListener(new InspectorMouseClickAdapter(inspection()) {
            @Override
            public void procedure(MouseEvent mouseEvent) {
                if (inspection().gui().getButton(mouseEvent) == MouseEvent.BUTTON1) {
                    if (mouseEvent.isControlDown()) {
                        views().memory().makeView(teleHub);
                    } else {
                        focus().setHeapObject(teleHub);
                    }
                }
            }
        });
        redisplay();
    }

    public void refresh(boolean force) {
        if (vm().state().newerThan(lastRefreshedState) || force) {
            lastRefreshedState = vm().state();
            updateText();
        }
    }

    public void redisplay() {
        setFont(preference().style().javaNameFont());
        updateText();
    }

    private void updateText() {
        final Class javaType = teleHub.hub().classActor.toJava();
        setText(inspection().nameDisplay().referenceLabelText(teleHub));
        if (!(javaType.isPrimitive() || Word.class.isAssignableFrom(javaType))) {
            setToolTipText(inspection().nameDisplay().referenceToolTipText(teleHub));
        }
    }

}
