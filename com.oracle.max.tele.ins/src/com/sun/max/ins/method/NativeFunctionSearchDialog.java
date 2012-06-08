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
package com.sun.max.ins.method;

import java.util.*;

import javax.swing.*;

import com.sun.max.ins.*;
import com.sun.max.ins.gui.*;
import com.sun.max.lang.*;
import com.sun.max.tele.*;

public class NativeFunctionSearchDialog extends FilteredListDialog<MaxNativeFunction> {

    private static class NullDialog extends SimpleDialog {
        private static NullDialog nullDialog;

        private NullDialog(Inspection inspection) {
            super(inspection, new JLabel("Functions are not available at this stage"), "Native Function Search", true);
        }

        static void create(Inspection inspection) {
            if (nullDialog == null) {
                nullDialog = new NullDialog(inspection);
            } else {
                nullDialog.setVisible(true);
            }
        }

    }

    private static class NativeFunctionItem extends FilteredListItem<MaxNativeFunction> {

        private final MaxNativeFunction nativeFunction;

        NativeFunctionItem(Inspection inspection, MaxNativeFunction nativeFunction) {
            super(inspection);
            this.nativeFunction = nativeFunction;
        }

        @Override
        public MaxNativeFunction object() {
            return nativeFunction;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void rebuildList(String filterText) {
        final String filter = filterText.toLowerCase();
        if (maxNativeLibrary != null && maxNativeLibrary.functions() != null) {
            for (MaxNativeFunction info : maxNativeLibrary.functions()) {
                if (filter.endsWith(" ")) {
                    if (info.name().equalsIgnoreCase(Strings.chopSuffix(filter, 1))) {
                        listModel.addElement(new NativeFunctionItem(inspection(), info));
                    }
                } else if (info.name().toLowerCase().contains(filter)) {
                    listModel.addElement(new NativeFunctionItem(inspection(), info));
                }
            }
        }
    }

    private NativeFunctionSearchDialog(Inspection inspection, MaxNativeLibrary maxNativeLibrary, String title, String actionName, boolean multiSelection) {
        super(inspection, title == null ? "Select Native Function" : title, "Filter text", actionName, multiSelection);
        this.maxNativeLibrary = maxNativeLibrary;
        rebuildList();
    }

    private MaxNativeLibrary maxNativeLibrary;

    /**
     * Displays a dialog to let the use select one or more native functions in the VM.
     *
     * @param title for dialog window
     * @param actionName name to appear on button
     * @param multi allow multiple selections if true
     * @return references to the selected instances of {@link MaxNativeFunction}, null if user canceled.
     */
    public static List<MaxNativeFunction> show(Inspection inspection, MaxNativeLibrary maxNativeLibrary, String title, String actionName, boolean multi) {
        if (maxNativeLibrary.functions() == null) {
            NullDialog.create(inspection);
            return null;
        } else {
            final NativeFunctionSearchDialog dialog = new NativeFunctionSearchDialog(inspection, maxNativeLibrary, title, actionName, multi);
            dialog.setVisible(true);
            return dialog.selectedObjects();
        }
    }
}
