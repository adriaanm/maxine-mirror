/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.*;
import java.awt.event.*;
import java.util.*;

import javax.swing.*;

import com.sun.max.ins.*;
import com.sun.max.ins.InspectionSettings.AbstractSaveSettingsListener;
import com.sun.max.ins.InspectionSettings.SaveSettingsEvent;
import com.sun.max.ins.InspectionSettings.SaveSettingsListener;
import com.sun.max.ins.gui.*;
import com.sun.max.program.option.*;

public class MethodViewPreferences extends AbstractInspectionHolder {

    private static final String METHOD_VIEW_PREFS = "methodViewPrefs";

    private static MethodViewPreferences globalPreferences;

    /**
     * @return the global, persistent set of user preferences for viewing method code
     */
    public static MethodViewPreferences globalPreferences(Inspection inspection) {
        if (globalPreferences == null) {
            globalPreferences = new MethodViewPreferences(inspection);
        }
        return globalPreferences;
    }

    /**
     * @return a GUI panel suitable for setting global preferences for this kind of view.
     */
    public static JPanel globalPreferencesPanel(Inspection inspection) {
        return globalPreferences(inspection).getPanel();
    }

    /**
     * A predicate specifying which kinds of code are to be displayed in a method view.
     */
    private final Map<MethodCodeKind, Boolean> visibleCodeKinds = new EnumMap<MethodCodeKind, Boolean>(MethodCodeKind.class);

    public MethodViewPreferences(Inspection inspection) {
        super(inspection);
        final InspectionSettings settings = inspection.settings();
        final SaveSettingsListener saveSettingsListener = new AbstractSaveSettingsListener(METHOD_VIEW_PREFS) {
            public void saveSettings(SaveSettingsEvent saveSettingsEvent) {
                for (Map.Entry<MethodCodeKind, Boolean> entry : visibleCodeKinds.entrySet()) {
                    saveSettingsEvent.save(entry.getKey().name().toLowerCase(), entry.getValue());
                }
            }
        };
        settings.addSaveSettingsListener(saveSettingsListener);
        for (MethodCodeKind codeKind : MethodCodeKind.values()) {
            final boolean defaultVisibility = codeKind.defaultVisibility();
            final String name = codeKind.name().toLowerCase();
            final Boolean value = settings.get(saveSettingsListener, name, OptionTypes.BOOLEAN_TYPE, defaultVisibility);
            visibleCodeKinds.put(codeKind, value);
        }
    }

    /**
     * Determines if this preferences object indicates that a {@linkplain CodeViewer code viewer} should be created
     * for a given code kind.
     */
    public boolean isVisible(MethodCodeKind codeKind) {
        return visibleCodeKinds.get(codeKind);
    }

    /**
     * @return a GUI panel for setting these preferences
     */
    public JPanel getPanel() {
        final InspectorCheckBox[] checkBoxes = new InspectorCheckBox[MethodCodeKind.values().length];

        final ItemListener itemListener = new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                final Object source = e.getItemSelectable();
                for (MethodCodeKind codeKind : MethodCodeKind.values()) {
                    final InspectorCheckBox checkBox = checkBoxes[codeKind.ordinal()];
                    if (source == checkBox) {
                        visibleCodeKinds.put(codeKind, checkBox.isSelected());
                        inspection().settings().save();
                        break;
                    }
                }
            }
        };
        final JPanel content = new InspectorPanel(inspection());
        content.add(new TextLabel(inspection(), "View:  "));
        final String toolTipText = "Should new Method views initially display this code, when available?";
        for (MethodCodeKind codeKind : MethodCodeKind.values()) {
            final boolean currentValue = visibleCodeKinds.get(codeKind);
            final InspectorCheckBox checkBox =
                new InspectorCheckBox(inspection(), codeKind.toString(), toolTipText, currentValue);
            checkBox.addItemListener(itemListener);
            checkBoxes[codeKind.ordinal()] = checkBox;
            content.add(checkBox);
        }
        final JPanel panel = new InspectorPanel(inspection(), new BorderLayout());
        panel.add(content, BorderLayout.WEST);
        return panel;
    }

    public void showDialog() {
        new SimpleDialog(inspection(), getPanel(), "Method Code Display Preferences", true);
    }

}
