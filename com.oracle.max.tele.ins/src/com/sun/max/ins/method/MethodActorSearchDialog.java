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
package com.sun.max.ins.method;

import java.util.*;

import com.sun.max.ins.*;
import com.sun.max.ins.gui.*;
import com.sun.max.lang.*;
import com.sun.max.tele.object.*;
import com.sun.max.util.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;

/**
 * A dialog to let the user select a method from a specified {@link ClassActor} in the VM.
 */
public final class MethodActorSearchDialog extends ObjectSearchDialog<TeleMethodActor> {

    private final static class MethodActorListItem extends FilteredListItem<TeleMethodActor> {

        final TeleMethodActor teleMethodActor;
        final String name;

        public MethodActorListItem(Inspection inspection, String name, TeleMethodActor teleMethodActor) {
            super(inspection);
            this.teleMethodActor = teleMethodActor;
            this.name = name;
        }

        @Override
        public TeleMethodActor object() {
            return teleMethodActor;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void rebuildList(String filterText) {
        final Iterator<TeleMethodActor> teleMethodActors = localTeleMethodActors.iterator();

        int i = 0;
        final MethodActorListItem[] methodItems = new MethodActorListItem[localTeleMethodActors.size()];

        final String filterLowerCase = filterText.toLowerCase();
        while (teleMethodActors.hasNext()) {
            final TeleMethodActor teleMethodActor = teleMethodActors.next();
            final MethodActor methodActor = teleMethodActor.methodActor();
            final String methodNameLowerCase = methodActor.name.toString().toLowerCase();
            if (filterLowerCase.isEmpty() ||
                (filterLowerCase.endsWith(" ") && methodNameLowerCase.equals(Strings.chopSuffix(filterLowerCase, 1))) ||
                methodNameLowerCase.contains(filterLowerCase)) {
                final String signature = methodActor.name + methodActor.descriptor().toJavaString(false, true);
                methodItems[i++] = new MethodActorListItem(inspection(), signature, teleMethodActor);
            }
        }

        Arrays.sort(methodItems, 0, i);
        for (int j = 0; j < i; ++j) {
            listModel.addElement(methodItems[j]);
        }
    }

    private final List<TeleMethodActor> localTeleMethodActors = new LinkedList<TeleMethodActor>();

    private MethodActorSearchDialog(Inspection inspection, TeleClassActor teleClassActor, Predicate<TeleMethodActor> filter, String title, String actionName) {
        super(inspection, title == null ? "Select Method" : title, "Method Name", actionName, false);
        for (TeleMethodActor teleMethodActor : teleClassActor.getTeleMethodActors()) {
            if (filter.evaluate(teleMethodActor)) {
                localTeleMethodActors.add(teleMethodActor);
            }
        }
        rebuildList();
    }

    /**
     * Displays a dialog to let the user select a reference to a method defined by a given class actor reference.
     *
     * @param filter only methods for which this holds are listed
     * @param title Title string for the dialog frame, "Select Method" if null
     * @param actionName Name of the action, appears on on the button to activate
     * @return local surrogate for the selected method actor or null if the user canceled the dialog
     */
    public static TeleMethodActor show(Inspection inspection, TeleClassActor teleClassActor, Predicate<TeleMethodActor> filter, String title, String actionName) {
        final MethodActorSearchDialog dialog = new MethodActorSearchDialog(inspection, teleClassActor, filter, title, actionName);
        dialog.setVisible(true);
        return dialog.selectedObject();
    }

    /**
     * Displays a dialog to let the user select a method defined by a given class actor.
     *
     * @param inspection
     * @param teleClassActor
     * @param title title string for the dialog frame, "Select Method" if null
     * @param actionName name of the action, appears on on the button to activate
     * @return local surrogate for the selected method actor or null if the user canceled the dialog
     */
    public static TeleMethodActor show(Inspection inspection, TeleClassActor teleClassActor, String title, String actionName) {
        return show(inspection, teleClassActor, Predicate.Static.alwaysTrue(TeleMethodActor.class), title, actionName);
    }
}
