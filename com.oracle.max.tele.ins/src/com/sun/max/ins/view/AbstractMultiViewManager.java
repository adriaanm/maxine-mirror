/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.max.ins.view;

import java.util.*;

import javax.swing.*;
import javax.swing.event.*;

import com.sun.max.ins.*;
import com.sun.max.ins.gui.*;
import com.sun.max.ins.view.InspectionViews.ViewKind;
import com.sun.max.tele.*;


/**
 * Abstract manager for a kind of view that can occur in multiple
 * instances.
 */
public abstract class AbstractMultiViewManager<View_Kind extends InspectorView>
    extends AbstractInspectionHolder
    implements MultiViewManager, InspectionViewFactory<View_Kind>, InspectionListener {

    private final ViewKind viewKind;
    private final String shortName;
    private final String longName;

    private final ArrayList<View_Kind> views = new ArrayList<View_Kind>();

    private final InspectorAction deactivateAllAction;

    protected AbstractMultiViewManager(Inspection inspection, ViewKind viewKind, String shortName, String longName) {
        super(inspection);
        this.viewKind = viewKind;
        this.shortName = shortName;
        this.longName = longName;
        this.deactivateAllAction = new DeactivateAllAction(shortName);
        inspection().addInspectionListener(this);
        refresh();
    }

    public final ViewKind viewKind() {
        return viewKind;
    }

    public final String shortName() {
        return shortName;
    }

    public final String longName() {
        return longName;
    }

    public final boolean isSingleton() {
        return false;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Multiple view kinds are assumed by default to be supported.
     * Concrete view manager types should override if this
     * isn't always so.
     */
    public boolean isSupported() {
        return true;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Multiple view kinds are assumed by default to be enabled
     * if they are supported.
     * Concrete view manager types should override if this
     * isn't always so.
     */
    public boolean isEnabled() {
        return isSupported();
    }

    public final boolean isActive() {
        return views.size() > 0;
    }

    public final List<View_Kind> activeViews() {
        return views;
    }

    public final JMenu viewMenu() {
        final JMenu menu = new JMenu("View " + shortName);
        menu.addMenuListener(new MenuListener() {

            public void menuCanceled(MenuEvent e) {
            }

            public void menuDeselected(MenuEvent e) {
            }

            public void menuSelected(MenuEvent e) {
                menu.removeAll();
                final List<View_Kind> views = activeViews();
                if (views.size() > 0) {
                    for (InspectorAction makeViewAction : makeViewActions()) {
                        menu.add(makeViewAction);
                    }
                    menu.addSeparator();
                    menu.add(deactivateAllAction);
                    for (InspectorAction closeViewAction : closeViewActions()) {
                        menu.add(closeViewAction);
                    }
                    menu.addSeparator();
                    for (InspectorView view : views) {
                        menu.add(view.getShowViewAction());
                    }
                } else {
                    for (InspectorAction makeViewAction : makeViewActions()) {
                        menu.add(makeViewAction);
                    }
                }
            }
        });
        return menu;
    }

    public final void deactivateAllUnpinnedViews() {
        for (InspectorView view : new ArrayList<View_Kind>(views)) {
            if (!view.isPinned()) {
                view.dispose();
            }
        }
        refresh();
    }

    public final InspectorAction deactivateAllAction(InspectorView exception) {
        if (exception == null) {
            return deactivateAllAction;
        }
        return new DeactivateAllExceptAction(shortName, exception);
    }

    /**
     * Allows concrete subclasses to register the creation of a new view.
     */
    protected final void notifyAddingView(View_Kind view) {
        assert views.add(view);
        view.addViewEventListener(new ViewEventListener() {

            @Override
            public void viewClosing(InspectorView view) {
                assert views.remove(view);
                refresh();
            }

        });
        refresh();
    }

    /**
     * Gets a list of interactive (context-independent) actions that
     * can make a new view.  These will be added to the view menu
     * for this kind.
     *
     * @return actions that can create a new view
     */
    protected List<InspectorAction> makeViewActions() {
        return Collections.emptyList();
    }

    /**
     * Gets a list of interactive (context-independent) actions that
     * can close existing views.  These will be added to the view menu
     * for this kind.
     *
     * @return actions that can close existing views
     */
    protected List<InspectorAction> closeViewActions() {
        return Collections.emptyList();
    }

    public void vmStateChanged(boolean force) {
    }

    public void breakpointStateChanged() {
    }

    public void breakpointToBeDeleted(MaxBreakpoint breakpoint, String reason) {
    }

    public void watchpointSetChanged() {
    }

    public void viewConfigurationChanged() {
    }

    public void vmProcessTerminated() {
    }

    public void inspectionEnding() {
    }

    /**
     * Update any internal state on occasion of view activation/deactivation.
     */
    private void refresh() {
        deactivateAllAction.refresh(true);
    }

    private final class DeactivateAllAction extends InspectorAction {

        public DeactivateAllAction(String title) {
            super(inspection(), "Close unpinned " + title + " views");
        }

        @Override
        protected void procedure() {
            deactivateAllUnpinnedViews();
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(isActive());
        }
    }

    private final class DeactivateAllExceptAction extends InspectorAction {

        private final InspectorView exceptInspector;

        public DeactivateAllExceptAction(String title, InspectorView exceptInspector) {
            super(inspection(), "Close other " + title + " views");
            this.exceptInspector = exceptInspector;
        }

        @Override
        protected void procedure() {
            for (InspectorView view : new ArrayList<View_Kind>(views)) {
                if (!view.equals(exceptInspector)) {
                    view.dispose();
                }
            }
            AbstractMultiViewManager.this.refresh();
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(isActive());
        }
    }
}
