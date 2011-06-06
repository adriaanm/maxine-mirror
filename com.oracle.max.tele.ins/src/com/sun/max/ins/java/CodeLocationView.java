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
package com.sun.max.ins.java;

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;

import com.sun.cri.ci.*;
import com.sun.max.ins.*;
import com.sun.max.ins.gui.*;
import com.sun.max.ins.view.*;
import com.sun.max.ins.view.InspectionViews.ViewKind;
import com.sun.max.program.*;
import com.sun.max.tele.*;
import com.sun.max.tele.object.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.classfile.*;


/**
 * A view that displays detailed information about the currently selected location in compiled code,
 * starting with Java frame descriptors, if available.
 *
 * @author Michael Van De Vanter
 */
public final class CodeLocationView extends AbstractView<CodeLocationView> {

    private static final int TRACE_VALUE = 1;
    private static final ViewKind VIEW_KIND = ViewKind.CODE_LOCATION;
    private static final String SHORT_NAME = "Code Location";
    private static final String LONG_NAME = "CodeLocation View";
    private static final String GEOMETRY_SETTINGS_KEY = "codeLocationViewGeometry";


    public static final class CodeLocationViewManager extends AbstractSingletonViewManager<CodeLocationView> {

        protected CodeLocationViewManager(Inspection inspection) {
            super(inspection, VIEW_KIND, SHORT_NAME, LONG_NAME);
        }

        @Override
        protected CodeLocationView createView(Inspection inspection) {
            return new CodeLocationView(inspection);
        }

    }

    private static CodeLocationViewManager viewManager = null;

    public static CodeLocationViewManager makeViewManager(Inspection inspection) {
        if (viewManager == null) {
            viewManager = new CodeLocationViewManager(inspection);
        }
        return viewManager;
    }

    private MaxCodeLocation codeLocation = null;
    private MaxCompiledCode compiledCode = null;
    private CiFrame frames = null;


    private final Rectangle originalFrameGeometry;
    private final InspectorPanel nullPanel;
    private final InspectorPanel simplePanel;
    private final PlainLabel simplePanelLabel;

    protected CodeLocationView(Inspection inspection) {
        super(inspection, VIEW_KIND, GEOMETRY_SETTINGS_KEY);
        Trace.begin(1,  tracePrefix() + " initializing");

        nullPanel = new InspectorPanel(inspection, new BorderLayout());
        nullPanel.add(new PlainLabel(inspection, inspection.nameDisplay().unavailableDataShortText()), BorderLayout.PAGE_START);

        simplePanel = new InspectorPanel(inspection, new BorderLayout());
        simplePanelLabel = new PlainLabel(inspection, "");
        simplePanel.add(simplePanelLabel, BorderLayout.PAGE_START);

        updateCodeLocation(focus().codeLocation());

        final InspectorFrame frame = createFrame(true);

        frame.makeMenu(MenuKind.DEFAULT_MENU).add(defaultMenuItems(MenuKind.DEFAULT_MENU));

        frame.makeMenu(MenuKind.VIEW_MENU).add(defaultMenuItems(MenuKind.VIEW_MENU));

        originalFrameGeometry = getGeometry();
        Trace.end(1,  tracePrefix() + " initializing");
    }

    @Override
    public String getTextForTitle() {
        final StringBuilder sb = new StringBuilder(viewManager.shortName() + ": ");
        if (codeLocation == null) {
            sb.append("<none>");
        } else if (codeLocation.hasTeleClassMethodActor()) {
            final TeleClassMethodActor teleClassMethodActor = codeLocation.teleClassMethodActor();
            sb.append(teleClassMethodActor.classMethodActor().holder().simpleName()).append(".");
            sb.append(inspection().nameDisplay().veryShortName(teleClassMethodActor));
        } else if (codeLocation.hasAddress()) {
            MaxExternalCode externalCode = vm().codeCache().findExternalCode(codeLocation.address());
            if (externalCode == null) {
                sb.append("<native>");
            } else {
                sb.append(externalCode.entityName());
            }
        }
        return sb.toString();
    }

    @Override
    protected void createViewContent() {
        if (codeLocation == null) {
            setContentPane(nullPanel);
        } else if (frames == null) {
            simplePanelLabel.setText(inspection().nameDisplay().shortName(codeLocation));
            setContentPane(simplePanel);
        } else {
            final JPanel panel = new InspectorPanel(inspection(), new GridLayout(0, 1));
            CiFrame frame = frames;
            do {
                panel.add(createFramePanel(frame), 0);
                frame = frame.caller();
            } while (frame != null);
            setContentPane(new InspectorScrollPane(inspection(), panel));
        }
        setTitle();
    }

    @Override
    protected void refreshState(boolean force) {
        updateCodeLocation(codeLocation);
        reconstructView();
    }

    @Override
    public void codeLocationFocusSet(MaxCodeLocation codeLocation, boolean interactiveForNative) {
        updateCodeLocation(codeLocation);
        reconstructView();
    }

    private void updateCodeLocation(MaxCodeLocation codeLocation) {
        this.codeLocation = codeLocation;
        compiledCode = codeLocation.compiledCode();
        frames = codeLocation.bytecodeFrames();
    }

    private String shortString(CiCodePos codePos) {
        return codePos.method.name() + "() bci=" + codePos.bci;
    }

    private JPanel createFramePanel(CiFrame frame) {
        final JPanel panel = new InspectorPanel(inspection(), new BorderLayout());
        final JPanel headerPanel = new InspectorPanel(inspection(), new FlowLayout(FlowLayout.LEADING));

        final CiCodePos codePos = frame;
        final PlainLabel bytecodeLocationLabel = new PlainLabel(inspection(), shortString(codePos));
        bytecodeLocationLabel.setToolTipText(codePos.toString());
        headerPanel.add(bytecodeLocationLabel);

        ClassMethodActor method = (ClassMethodActor) codePos.method;
        String sourceFileName = method.holder().sourceFileName;
        final int lineNumber = method.sourceLineNumber(codePos.bci);
        if (sourceFileName != null || lineNumber >= 0) {
            if (sourceFileName == null) {
                sourceFileName = inspection().nameDisplay().unavailableDataShortText();
            }
            final String labelText = lineNumber >= 0 ? String.valueOf(lineNumber) : inspection().nameDisplay().unavailableDataShortText();
            final PlainLabel sourceLocationLabel = new PlainLabel(inspection(), " line=" + labelText);
            sourceLocationLabel.setToolTipText(sourceFileName);
            sourceLocationLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    inspection().viewSourceExternally(codePos);
                }
            });
            headerPanel.add(sourceLocationLabel);
        }
        panel.add(headerPanel, BorderLayout.PAGE_START);

        if (frame.numLocals + frame.numStack > 0) {
            final JPanel slotsPanel = new JPanel();
            slotsPanel.setLayout(new BoxLayout(slotsPanel, BoxLayout.PAGE_AXIS));
            final CodeAttribute codeAttribute = method.codeAttribute();
            for (int i = 0; i < frame.numLocals; i++) {
                String local = "local #" + i;
                final LocalVariableTable.Entry entry = codeAttribute.localVariableTable().findLocalVariable(i, codePos.bci);
                if (entry != null) {
                    local += ": " + entry.name(codeAttribute.cp);
                }
                local += " = " + frame.getLocalValue(i);
                slotsPanel.add(new TextLabel(inspection(), local));
            }
            for (int i = 0; i < frame.numStack; i++) {
                String stackSlot = "stack #" + i;
                stackSlot += " = " + frame.getStackValue(i);
                slotsPanel.add(new TextLabel(inspection(), stackSlot));
            }
            panel.add(slotsPanel, BorderLayout.LINE_START);
        }
        panel.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, style().defaultBorderColor()));
        return panel;
    }


}
