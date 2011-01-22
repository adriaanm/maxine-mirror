/*
 * Copyright (c) 2007, 2010, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.ins.method;

import com.sun.max.ins.*;
import com.sun.max.ins.gui.*;
import com.sun.max.tele.object.*;
import com.sun.max.vm.actor.member.*;

/**
 * Provides menu items related to a specific {@link MethodActor} in the VM.
 *
 * @author Bernd Mathiske
 * @author Doug Simon
 * @author Michael Van De Vanter
 */
public final class ClassMethodActorMenuItems extends AbstractInspectorMenuItems {

    private final TeleClassMethodActor teleClassMethodActor;

    public ClassMethodActorMenuItems(Inspection inspection, TeleClassMethodActor teleClassMethodActor) {
        super(inspection);
        this.teleClassMethodActor = teleClassMethodActor;
        refresh(true);
    }

    public void addTo(InspectorMenu menu) {
        final InspectorMenu objectMenu = new InspectorMenu("Object");
        objectMenu.add(actions().inspectObject(teleClassMethodActor, "Method: " + teleClassMethodActor.classActorForObjectType().simpleName()));
        final TeleClassActor teleClassActor = teleClassMethodActor.getTeleHolder();
        objectMenu.add(actions().inspectObject(teleClassActor, "Holder: " + teleClassActor.classActorForObjectType().simpleName()));
        objectMenu.add(actions().inspectSubstitutionSourceClassActorAction(teleClassMethodActor));
        objectMenu.add(actions().inspectTargetMethodCompilationsMenu(teleClassMethodActor));
        menu.add(objectMenu);

        final InspectorMenu codeMenu = new InspectorMenu("Code");
        codeMenu.add(actions().viewJavaSource(teleClassMethodActor));
        codeMenu.add(actions().viewMethodBytecode(teleClassMethodActor));
        codeMenu.add(actions().viewTargetMethodCodeMenu(teleClassMethodActor));
        menu.add(codeMenu);

        final InspectorMenu debugMenu = new InspectorMenu("Debug");
        debugMenu.add(actions().setBytecodeBreakpointAtMethodEntry(teleClassMethodActor));
        debugMenu.add(actions().debugInvokeMethod(teleClassMethodActor));
        menu.add(debugMenu);
    }

    public void addTo(InspectorPopupMenu menu) {
        final InspectorMenu objectMenu = new InspectorMenu("Object");
        objectMenu.add(actions().inspectObject(teleClassMethodActor, "Method: " + teleClassMethodActor.classActorForObjectType().simpleName()));
        final TeleClassActor teleClassActor = teleClassMethodActor.getTeleHolder();
        objectMenu.add(actions().inspectObject(teleClassActor, "Holder: " + teleClassActor.classActorForObjectType().simpleName()));
        objectMenu.add(actions().inspectSubstitutionSourceClassActorAction(teleClassMethodActor));
        objectMenu.add(actions().inspectTargetMethodCompilationsMenu(teleClassMethodActor));
        menu.add(objectMenu);

        final InspectorMenu codeMenu = new InspectorMenu("Code");
        codeMenu.add(actions().viewJavaSource(teleClassMethodActor));
        codeMenu.add(actions().viewMethodBytecode(teleClassMethodActor));
        codeMenu.add(actions().viewTargetMethodCodeMenu(teleClassMethodActor));
        menu.add(codeMenu);

        final InspectorMenu debugMenu = new InspectorMenu("Debug");
        final InspectorMenu breakOnEntryMenu = new InspectorMenu("Break at method entry");
        breakOnEntryMenu.add(actions().setBytecodeBreakpointAtMethodEntry(teleClassMethodActor, "Bytecodes"));
        debugMenu.add(breakOnEntryMenu);
        debugMenu.add(actions().debugInvokeMethod(teleClassMethodActor));
        menu.add(debugMenu);
    }

    @Override
    public void refresh(boolean force) {
        if (teleClassMethodActor != null) {
            teleClassMethodActor.refreshView();
        }
        super.refresh(force);
    }

}
