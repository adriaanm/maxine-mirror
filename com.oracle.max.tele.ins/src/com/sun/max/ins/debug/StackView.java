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
package com.sun.max.ins.debug;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

import javax.swing.*;
import javax.swing.event.*;

import com.sun.cri.ci.*;
import com.sun.max.gui.*;
import com.sun.max.ins.*;
import com.sun.max.ins.gui.*;
import com.sun.max.ins.util.*;
import com.sun.max.ins.value.*;
import com.sun.max.ins.view.*;
import com.sun.max.ins.view.InspectionViews.ViewKind;
import com.sun.max.program.*;
import com.sun.max.tele.*;
import com.sun.max.tele.data.*;
import com.sun.max.tele.object.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.actor.member.*;

/**
 * A singleton view that displays stack contents for the thread in the VM that is the current user focus.
 */
public final class StackView extends AbstractView<StackView> {
    private static final int TRACE_VALUE = 1;
    private static final ViewKind VIEW_KIND = ViewKind.STACK;
    private static final String SHORT_NAME = "Stack";
    private static final String LONG_NAME = "Stack View";
    private static final String GEOMETRY_SETTINGS_KEY = "stackViewGeometry";

    public static final int DEFAULT_MAX_FRAMES_DISPLAY;
    private static final String MAX_FRAMES_DISPLAY_PROPERTY = "inspector.max.stack.frames.display";
    static {
        int def = 500;
        final String value = System.getProperty(MAX_FRAMES_DISPLAY_PROPERTY);
        if (value != null) {
            try {
                def = Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                InspectorError.unexpected(MAX_FRAMES_DISPLAY_PROPERTY + " value " +  value + " not an integer");
            }
        }
        DEFAULT_MAX_FRAMES_DISPLAY = def;
    }

    public static final class StackViewManager extends AbstractSingletonViewManager<StackView> {

        protected StackViewManager(Inspection inspection) {
            super(inspection, VIEW_KIND, SHORT_NAME, LONG_NAME);
        }

        @Override
        protected StackView createView(Inspection inspection) {
            return new StackView(inspection);
        }

    }

    // Will be non-null before any instances created.
    private static StackViewManager viewManager = null;

    public static StackViewManager makeViewManager(Inspection inspection) {
        if (viewManager == null) {
            viewManager = new StackViewManager(inspection);
        }
        return viewManager;
    }

    private final class StackFrameListCellRenderer extends MachineCodeLabel implements ListCellRenderer {

        StackFrameListCellRenderer(Inspection inspection) {
            super(inspection, "");
        }

        public Component getListCellRendererComponent(JList list, Object value, int modelIndex, boolean isSelected, boolean cellHasFocus) {
            final MaxStackFrame stackFrame = (MaxStackFrame) value;
            String methodName = "";
            String toolTip = null;
            if (stackFrame instanceof MaxStackFrame.Compiled) {
                final MaxCompilation compilation = stackFrame.compilation();
                methodName += inspection().nameDisplay().veryShortName(compilation);
                CiDebugInfo debugInfo = stackFrame.codeLocation().debugInfo();
                if (debugInfo == null || debugInfo.frame() == null || debugInfo.frame().caller() == null) {
                    toolTip = htmlify(inspection().nameDisplay().longName(compilation, stackFrame.ip()));
                    if (compilation != null) {
                        try {
                            vm().acquireLegacyVMAccess();
                            try {
                                final TeleClassMethodActor teleClassMethodActor = compilation.getTeleClassMethodActor();
                                if (teleClassMethodActor != null && teleClassMethodActor.isSubstituted()) {
                                    methodName += inspection().nameDisplay().methodSubstitutionShortAnnotation(teleClassMethodActor);
                                    try {
                                        toolTip += inspection().nameDisplay().methodSubstitutionLongAnnotation(teleClassMethodActor);
                                    } catch (Exception e) {
                                        // There's corner cases where we can't obtain detailed information for the tool tip (e.g., the method we're trying to get the substitution info about
                                        //  is being constructed. Instead of propagating the exception, just use a default tool tip. [Laurent].
                                        toolTip += inspection().nameDisplay().unavailableDataLongText();
                                    }
                                }
                            } finally {
                                vm().releaseLegacyVMAccess();
                            }
                        } catch (DataIOError e) {
                            methodName += inspection().nameDisplay().unavailableDataShortText();
                            toolTip = inspection().nameDisplay().unavailableDataLongText();
                        } catch (MaxVMBusyException e) {
                            methodName += inspection().nameDisplay().unavailableDataShortText();
                            toolTip = inspection().nameDisplay().unavailableDataLongText();
                        }
                    }
                } else {
                    // There is at least one inlined frame available in debug info
                    final Stack<CiFrame> frames = new Stack<CiFrame>();
                    CiFrame frame = debugInfo.frame();
                    while (frame != null) {
                        frames.push(frame);
                        frame = frame.caller();
                    }
                    final StringBuilder sb = new StringBuilder();
                    // Display the immediate frame using the standard format
                    sb.append(htmlify(inspection().nameDisplay().longName(compilation, stackFrame.ip())));
                    frame = frames.pop();
                    while (!frames.isEmpty()) {
                        frame = frames.pop();
                        sb.append("<br>    Inlined from: <br>");
                        CiUtil.appendLocation(sb, frame.method, frame.bci).toString();
                    }
                    toolTip = sb.toString();
                }
            } else if (stackFrame instanceof MaxStackFrame.Truncated) {
                final MaxStackFrame.Truncated truncated = (MaxStackFrame.Truncated) stackFrame;
                if (truncated.error() != null) {
                    methodName += "*a stack walker error occurred*";
                    toolTip = truncated.error().toString();
                } else {
                    methodName += "*select here to extend the display*";
                }
            } else {
                InspectorWarning.check(inspection(), stackFrame instanceof MaxStackFrame.Native, "Unhandled type of non-native stack frame: " + stackFrame.getClass().getName());
                final Pointer instructionPointer = stackFrame.ip();
                final MaxNativeFunction externalCode = vm().machineCode().findNativeFunction(instructionPointer);
                if (externalCode != null) {
                    // native that we know something about
                    methodName += inspection().nameDisplay().shortName(externalCode);
                    toolTip = "native function:  " + inspection().nameDisplay().longName(externalCode);
                } else {
                    methodName += "nativeMethod:" + instructionPointer.to0xHexString();
                    toolTip = "nativeMethod";
                }
            }
            if (modelIndex == 0) {
                setToolTipPrefix("IP in frame " + modelIndex + " points at:<br>");
                setForeground(preference().style().wordCallEntryPointColor());
            } else {
                setToolTipPrefix("call return in frame " + modelIndex + " points at:<br>");
                setForeground(preference().style().wordCallReturnPointColor());
            }
            setText(Integer.toString(modelIndex) + ":  " + methodName);
            setWrappedToolTipHtmlText(toolTip);
            setBackground(isSelected ? stackFrameList.getSelectionBackground() : stackFrameList.getBackground());
            return this;
        }

    }

    /**
     * Listens for mouse events over the stack frame list so that the right button
     * will bring up a contextual menu.
     */
    private final MouseListener frameMouseListener = new InspectorMouseClickAdapter(inspection()) {

        @Override
        public void procedure(final MouseEvent mouseEvent) {
            switch(inspection().gui().getButton(mouseEvent)) {
                case MouseEvent.BUTTON3:
                    int index = stackFrameList.locationToIndex(mouseEvent.getPoint());
                    if (index >= 0 && index < stackFrameList.getModel().getSize()) {
                        getPopupMenu(index, mouseEvent).show(mouseEvent.getComponent(), mouseEvent.getX(), mouseEvent.getY());
                    }
                    break;
            }
        }
    };

    /**
     * Listens for change of selection in the list of stack frames and passes this along
     * to the global focus setting.
     */
    private final ListSelectionListener frameSelectionListener = new ListSelectionListener() {

        public void valueChanged(ListSelectionEvent listSelectionEvent) {
            if (!listSelectionEvent.getValueIsAdjusting()) {
                final int index = stackFrameList.getSelectedIndex();
                if (index >= 0 && index < stackFrameListModel.getSize()) {
                    MaxStackFrame stackFrame = (MaxStackFrame) stackFrameListModel.get(index);
                    // New stack frame selection; set the global focus.
                    inspection().focus().setStackFrame(stackFrame, false);
                    if (stackFrame instanceof MaxStackFrame.Truncated && ((MaxStackFrame.Truncated) stackFrame).omitted() >= 0) {
                        // A user selection of the pseudo frame that marks the end
                        // of a partial (truncated) stack list has the effect of
                        // doubling the number of frames so that you can see more.
                        maxFramesDisplay *= 2;
                        lastChangedState = vm().state();
                        // Reconstruct the frame display with the new, extended cap
                        forceRefresh();
                        // Finally, reset the focus on the actual stack, frame, not the
                        // special frame that was just replaced after the maximum increased.
                        stackFrame = (MaxStackFrame) stackFrameListModel.get(index);
                        focus().setStackFrame(stackFrame, true);
                    }
                }
            }
        }
    };

    private final StackFrameListCellRenderer stackFrameListCellRenderer = new StackFrameListCellRenderer(inspection());
    private final InspectorAction copyStackToClipboardAction = new CopyStackToClipboardAction();

    private MaxStack stack = null;

    /**
     * Marks the most recent time in VM state history that we refreshed from the stack.
     */
    private MaxVMState lastUpdatedState = null;

    /**
     * Marks the most recent time in VM state history when the stack was observed to have changed structurally.
     */
    private MaxVMState lastChangedState = null;

    private InspectorPanel contentPane = null;
    private DefaultListModel stackFrameListModel = null;  // TODO (mlvdv) generic in Java 7
    private DefaultListModel emptyModel = new DefaultListModel();  // TODO (mlvdv) generic in Java 7
    private JList stackFrameList = null;  // TODO (mlvdv) generic in Java 7

    /**
     * The maximum number of frames to be displayed at any given time, to defend against extremely large
     * stacks.  The user can click in the final pseudo-frame in the display when it has been limited
     * this way, and this will cause the number displayed to grow.
     */
    private int maxFramesDisplay = DEFAULT_MAX_FRAMES_DISPLAY;

    public StackView(Inspection inspection) {
        super(inspection, VIEW_KIND, GEOMETRY_SETTINGS_KEY);
        Trace.begin(TRACE_VALUE,  tracePrefix() + " initializing");
        createFrame(true);
        forceRefresh();
        Trace.end(TRACE_VALUE,  tracePrefix() + " initializing");
    }

    @Override
    public String getTextForTitle() {
        String title = viewManager.shortName() + ": ";
        if (!inspection().hasProcess()) {
            title += inspection().nameDisplay().noProcessShortText();
        } else if (stack != null && stack.thread() != null) {
            title += inspection().nameDisplay().longNameWithState(stack.thread());
        }
        return title;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void createViewContent() {
        lastUpdatedState = null;
        lastChangedState = null;
        final MaxThread thread = inspection().focus().thread();
        contentPane = new InspectorPanel(inspection(), new BorderLayout());
        if (thread != null) {
            stack = thread.stack();
            lastUpdatedState = stack.lastUpdated();
            lastChangedState = stack.lastChanged();
            assert stack != null;
            stackFrameListModel = new DefaultListModel();
            stackFrameList = new JList(stackFrameListModel);
            stackFrameList.setCellRenderer(stackFrameListCellRenderer);

            final JPanel header = new InspectorPanel(inspection(), new SpringLayout());
            final TextLabel stackStartLabel = new TextLabel(inspection(), "start: ");
            stackStartLabel.setToolTipText("Stack memory start location");
            header.add(stackStartLabel);
            final WordValueLabel stackStartValueLabel = new WordValueLabel(inspection(), WordValueLabel.ValueMode.WORD, stack.memoryRegion().start(), contentPane);
            stackStartValueLabel.setToolTipPrefix("Stack memory start @ ");
            header.add(stackStartValueLabel);
            final TextLabel stackSizeLabel = new TextLabel(inspection(), "size: ");
            stackSizeLabel.setToolTipText("Stack size");
            header.add(stackSizeLabel);
            final DataLabel.LongAsDecimal stackSizeValueLabel = new DataLabel.LongAsDecimal(inspection());
            stackSizeValueLabel.setToolTipPrefix("Stack size ");
            stackSizeValueLabel.setValue(stack.memoryRegion().nBytes());
            header.add(stackSizeValueLabel);
            SpringUtilities.makeCompactGrid(header, 2);
            contentPane.add(header, BorderLayout.NORTH);

            stackFrameList.setSelectionInterval(1, 0);
            stackFrameList.setVisibleRowCount(10);
            stackFrameList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            stackFrameList.setLayoutOrientation(JList.VERTICAL);
            stackFrameList.addMouseListener(frameMouseListener);
            stackFrameList.addListSelectionListener(frameSelectionListener);

            final JScrollPane listScrollPane = new InspectorScrollPane(inspection(), stackFrameList);
            contentPane.add(listScrollPane, BorderLayout.CENTER);
        }
        setContentPane(contentPane);

        // Populate menu bar
        makeMenu(MenuKind.DEFAULT_MENU).add(defaultMenuItems(MenuKind.DEFAULT_MENU));

        final InspectorMenu editMenu = makeMenu(MenuKind.EDIT_MENU);
        editMenu.add(copyStackToClipboardAction);

        final InspectorMenu memoryMenu = makeMenu(MenuKind.MEMORY_MENU);
        memoryMenu.add(actions().viewSelectedThreadStackMemory("View memory for stack"));
        memoryMenu.add(defaultMenuItems(MenuKind.MEMORY_MENU));
        memoryMenu.add(views().activateSingletonViewAction(ViewKind.ALLOCATIONS));

        makeMenu(MenuKind.VIEW_MENU).add(defaultMenuItems(MenuKind.VIEW_MENU));

        forceRefresh();
        // TODO (mlvdv) try to set frame selection to match global focus at creation; doesn't display.
        frameFocusChanged(null, inspection().focus().stackFrame());
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void refreshState(boolean force) {
        if (stack != null && stack.thread() != null && stack.thread().isLive()) {
            if (force || stack.lastUpdated() == null || vm().state().newerThan(lastUpdatedState)) {
                final List<MaxStackFrame> frames = stack.frames(maxFramesDisplay);
                if (!frames.isEmpty()) {
                    if (force || stack.lastChanged().newerThan(this.lastChangedState)) {
                        // Set the list to an empty model while doing the update so that all
                        // the addition of each element to the list doing not fire a whole
                        // bunch of list GUI update events. This really helps for deep stacks.
                        stackFrameList.setModel(emptyModel);
                        stackFrameListModel.clear();
                        int omitted = -1;
                        for (MaxStackFrame stackFrame : frames) {
                            if (stackFrame instanceof MaxStackFrame.Truncated) {
                                final MaxStackFrame.Truncated tr = (MaxStackFrame.Truncated) stackFrame;
                                if (tr.omitted() >= 0) {
                                    stackFrameListModel.addElement(stackFrame);
                                    omitted = tr.omitted();
                                    break;
                                }
                            }
                            stackFrameListModel.addElement(stackFrame);
                        }
                        stackFrameList.setModel(stackFrameListModel);
                        if (omitted >= 0) {
                            inspection().gui().informationMessage("stack depth exceeds " + maxFramesDisplay + ": truncated " + omitted + " frames", "Stack View");
                        }

                        this.lastChangedState = stack.lastChanged();
                    } else {
                        // The stack is structurally unchanged with respect to methods,
                        // which typically happens after a single step.
                        // Avoid a complete redisplay for performance reasons.
                        // However, the object representing the top frame may be different,
                        // in which case the state of the old frame object is out of date.
                        final MaxStackFrame newTopFrame = frames.get(0);
                        stackFrameListModel.set(0, newTopFrame);
                    }
                    lastUpdatedState = stack.lastUpdated();
                }
            }
        }
        // The title displays thread state, so must be updated.
        setTitle();
    }

    @Override
    public void threadFocusSet(MaxThread oldThread, MaxThread thread) {
        reconstructView();
    }

    @Override
    public void frameFocusChanged(MaxStackFrame oldStackFrame, MaxStackFrame newStackFrame) {
        if (stackFrameList != null) {
            if (newStackFrame == null || newStackFrame.stack().thread() != this.stack.thread()) {
                stackFrameList.clearSelection();
            } else {
                final int oldIndex = stackFrameList.getSelectedIndex();
                for (int index = 0; index < stackFrameListModel.getSize(); index++) {
                    final MaxStackFrame stackFrame = (MaxStackFrame) stackFrameListModel.get(index);
                    if (stackFrame.isSameFrame(newStackFrame)) {
                        if (index != oldIndex) {
                            stackFrameList.setSelectedIndex(index);
                            stackFrameList.ensureIndexIsVisible(index);
                        }
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void vmProcessTerminated() {
        reconstructView();
    }

    @Override
    public void viewClosing() {
        // Unsubscribe to view preferences, when we get them.
        super.viewClosing();
    }

    private String javaStackFrameName(MaxStackFrame.Compiled javaStackFrame) {
        final Address address = javaStackFrame.ip();
        final MaxCompilation compilation = vm().machineCode().findCompilation(address);
        String name;
        if (compilation != null) {
            name = inspection().nameDisplay().veryShortName(compilation);
            final TeleClassMethodActor teleClassMethodActor = compilation.getTeleClassMethodActor();
            if (teleClassMethodActor != null && teleClassMethodActor.isSubstituted()) {
                name = name + inspection().nameDisplay().methodSubstitutionShortAnnotation(teleClassMethodActor);
            }
        } else {
            final MethodActor classMethodActor = javaStackFrame.compilation().classMethodActor();
            name = classMethodActor.format("%h.%n");
        }
        return name;
    }

    private InspectorPopupMenu getPopupMenu(int row, MouseEvent mouseEvent) {
        final MaxStackFrame stackFrame = (MaxStackFrame) stackFrameListModel.get(row);
        final InspectorPopupMenu menu = new InspectorPopupMenu("Stack Frame");

        menu.add(new InspectorAction(inspection(), "Select frame (Left-Button)") {
            @Override
            protected void procedure() {
                inspection().focus().setStackFrame(stackFrame, false);
            }
        });
        if (stackFrame instanceof MaxStackFrame.Compiled) {
            final MaxStackFrame.Compiled javaStackFrame = (MaxStackFrame.Compiled) stackFrame;
            final String frameName = javaStackFrameName(javaStackFrame);
            menu.add(actions().viewtackFrameMemory(javaStackFrame, "View memory for frame" + frameName));
            menu.add(new InspectorAction(inspection(), "View frame " + frameName) {
                @Override
                protected void procedure() {
                    inspection().focus().setStackFrame(stackFrame, false);
                    views().activateSingletonView(ViewKind.STACK_FRAME).highlight();
                }
            });
        }
        if (stackFrame instanceof MaxStackFrame.Native) {
            final Pointer instructionPointer = stackFrame.ip();
            final MaxNativeFunction externalCode = vm().machineCode().findNativeFunction(instructionPointer);
            if (externalCode == null) {
                menu.add(new InspectorAction(inspection(), "Open external code dialog...") {
                    @Override
                    protected void procedure() {
                        MaxCodeLocation codeLocation = stackFrame.codeLocation();
                        if (codeLocation == null) {
                            gui().errorMessage("Stack frame has no code location");
                        } else {
                            focus().setCodeLocation(codeLocation, true);
                        }
                    }
                });
            }
        }
        return menu;
    }

    private final class CopyStackToClipboardAction extends InspectorAction {

        private CopyStackToClipboardAction() {
            super(inspection(), "Copy stack list to clipboard");
        }

        @SuppressWarnings("unchecked")
        @Override
        public void procedure() {
            // (mlvdv)  This is pretty awkward, but has the virtue that it reproduces exactly what's displayed.  Could be improved.
            final StringBuilder result = new StringBuilder(100);
            final ListCellRenderer cellRenderer = stackFrameList.getCellRenderer();
            for (int index = 0; index < stackFrameListModel.getSize(); index++) {
                final Object elementAt = stackFrameListModel.getElementAt(index);
                final InspectorLabel label = (InspectorLabel) cellRenderer.getListCellRendererComponent(stackFrameList, elementAt, index, false, false);
                result.append(label.getTextDeHtmlify()).append("\n");
            }
            gui().postToClipboard(result.toString());
        }
    }

}
