/*
 * Copyright (c) 2008, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.ins.gui;

import java.awt.*;

import javax.swing.*;

import com.sun.max.ins.*;
import com.sun.max.ins.gui.AbstractView.MenuKind;
import com.sun.max.ins.view.*;
import com.sun.max.tele.*;

/**
 * A menu bar specialized for use in the VM Inspector.
 * <p>
 * Instances of {@link InspectorMenu} can be added, and they can be retrieved by name.
 */
public class InspectorMenuBar extends JMenuBar implements Prober, InspectionHolder {

    private static final ImageIcon FRAME_ICON = InspectorImageIcon.createDownTriangle(12, 14);

    private final Inspection inspection;
    private final String tracePrefix;

    /**
     * Creates a new {@link JMenuBar}, specialized for use in the Inspector.
     */
    protected InspectorMenuBar(Inspection inspection) {
        this.inspection = inspection;
        this.tracePrefix = "[" + getClass().getSimpleName() + "] ";
        setOpaque(true);
    }

    public void add(InspectorMenu inspectorMenu) {
        assert inspectorMenu.getMenuName() != null;
        super.add(inspectorMenu);
    }

    private InspectorMenu findMenu(String name) {
        for (MenuElement element : getSubElements()) {
            final InspectorMenu inspectorMenu = (InspectorMenu) element;
            if (inspectorMenu.getMenuName().equals(name)) {
                return inspectorMenu;
            }
        }
        return null;
    }

    /**
     * @param menuKind a kind of menu
     * @return the menu in the menu bar of that kind, or
     * a new empty one if it doesn't already exist.
     */
    public InspectorMenu makeMenu(MenuKind menuKind) {
        InspectorMenu menu = findMenu(menuKind.label());
        if (menu != null) {
            return menu;
        }
        menu = new InspectorMenu(menuKind.label());
        if (menuKind == MenuKind.DEFAULT_MENU) {
            menu.setIcon(FRAME_ICON);
        }
        add(menu);
        return menu;
    }

    public void clearAll() {
        removeAll();
    }

    public final Inspection inspection() {
        return inspection;
    }

    public final MaxVM vm() {
        return inspection.vm();
    }

    public final InspectorGUI gui() {
        return inspection.gui();
    }

    public final InspectionFocus focus() {
        return inspection.focus();
    }

    public final InspectionViews views() {
        return inspection.views();
    }

    public final InspectionActions actions() {
        return inspection.actions();
    }

    public final InspectionPreferences preference() {
        return inspection.preference();
    }

    public void redisplay() {
    }

    public void refresh(boolean force) {
        for (MenuElement element : getSubElements()) {
            final InspectorMenu inspectorMenu = (InspectorMenu) element;
            inspectorMenu.refresh(force);
        }
    }

    @Override
    public void setBackground(Color color) {
        super.setBackground(color);
        for (MenuElement menuElement : getSubElements()) {
            final InspectorMenu menu = (InspectorMenu) menuElement;
            menu.setBackground(color);
        }
    }

    /**
     * @return default prefix text for trace messages; identifies the class being traced.
     */
    protected String tracePrefix() {
        return tracePrefix;
    }

}
