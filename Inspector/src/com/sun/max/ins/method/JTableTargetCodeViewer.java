/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.max.ins.method;

import java.awt.*;
import java.awt.event.*;
import java.awt.print.*;
import java.text.*;
import java.util.*;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;

import com.sun.max.asm.*;
import com.sun.max.ins.*;
import com.sun.max.ins.constant.*;
import com.sun.max.ins.debug.*;
import com.sun.max.ins.gui.*;
import com.sun.max.ins.object.*;
import com.sun.max.ins.value.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;
import com.sun.max.tele.*;
import com.sun.max.tele.method.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.bytecode.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.value.*;

/**
 * A table-based viewer for an (immutable) section of {@link TargetCode} in the VM.
 * Supports visual effects for execution state, and permits user selection
 * of instructions for various purposes (e.g. set breakpoint).
 *
 * @author Mick Jordan
 * @author Doug Simon
 * @author Michael Van De Vanter
 */
public class JTableTargetCodeViewer extends TargetCodeViewer {

    private static final int TRACE_VALUE = 2;

    private final Inspection inspection;
    private final TargetCodeTable table;
    private final TargetCodeTableModel tableModel;
    private final TargetCodeViewPreferences instanceViewPreferences;
    private final TableColumn[] columns;
    private final OperandsRenderer operandsRenderer;
    private final SourceLineRenderer sourceLineRenderer;
    private final Color defaultBackgroundColor;
    private final Color stopBackgroundColor;

    public JTableTargetCodeViewer(Inspection inspection, MethodInspector parent, TeleTargetRoutine teleTargetRoutine) {
        super(inspection, parent, teleTargetRoutine);
        this.inspection = inspection;
        this.operandsRenderer = new OperandsRenderer();
        this.sourceLineRenderer = new SourceLineRenderer();
        this.tableModel = new TargetCodeTableModel(inspection, teleTargetRoutine);
        this.columns = new TableColumn[TargetCodeColumnKind.VALUES.length()];
        instanceViewPreferences = new TargetCodeViewPreferences(TargetCodeViewPreferences.globalPreferences(inspection())) {
            @Override
            public void setIsVisible(TargetCodeColumnKind columnKind, boolean visible) {
                super.setIsVisible(columnKind, visible);
                table.getInspectorTableColumnModel().setColumnVisible(columnKind.ordinal(), visible);
                JTableColumnResizer.adjustColumnPreferredWidths(table);
                refresh(true);
            }
        };
        final TargetCodeTableColumnModel tableColumnModel = new TargetCodeTableColumnModel(instanceViewPreferences);
        this.table = new TargetCodeTable(inspection, tableModel, tableColumnModel);
        defaultBackgroundColor = this.table.getBackground();
        stopBackgroundColor = style().darken2(defaultBackgroundColor);
        createView();
    }

    @Override
    protected void createView() {
        super.createView();

        // Set up toolbar
        JButton button = new InspectorButton(inspection, actions().toggleTargetCodeBreakpoint());
        button.setToolTipText(button.getText());
        button.setText(null);
        button.setIcon(style().debugToggleBreakpointbuttonIcon());
        toolBar().add(button);

        button = new InspectorButton(inspection, actions().debugStepOver());
        button.setToolTipText(button.getText());
        button.setText(null);
        button.setIcon(style().debugStepOverButtonIcon());
        toolBar().add(button);

        button = new InspectorButton(inspection, actions().debugSingleStep());
        button.setToolTipText(button.getText());
        button.setText(null);
        button.setIcon(style().debugStepInButtonIcon());
        toolBar().add(button);

        button = new InspectorButton(inspection, actions().debugReturnFromFrame());
        button.setToolTipText(button.getText());
        button.setText(null);
        button.setIcon(style().debugStepOutButtonIcon());
        toolBar().add(button);

        button = new InspectorButton(inspection, actions().debugRunToSelectedInstruction());
        button.setToolTipText(button.getText());
        button.setText(null);
        button.setIcon(style().debugRunToCursorButtonIcon());
        toolBar().add(button);

        button = new InspectorButton(inspection, actions().debugResume());
        button.setToolTipText(button.getText());
        button.setText(null);
        button.setIcon(style().debugContinueButtonIcon());
        toolBar().add(button);

        button = new InspectorButton(inspection, actions().debugPause());
        button.setToolTipText(button.getText());
        button.setText(null);
        button.setIcon(style().debugPauseButtonIcon());
        toolBar().add(button);

        toolBar().add(Box.createHorizontalGlue());

        toolBar().add(new JLabel("Target Code"));

        toolBar().add(Box.createHorizontalGlue());

        addActiveRowsButton();

        addSearchButton();

        final JButton viewOptionsButton = new InspectorButton(inspection(), new AbstractAction("View...") {
            public void actionPerformed(ActionEvent actionEvent) {
                final TargetCodeViewPreferences globalPreferences = TargetCodeViewPreferences.globalPreferences(inspection());
                new TableColumnVisibilityPreferences.ColumnPreferencesDialog<TargetCodeColumnKind>(inspection(), "TargetCode View Options", instanceViewPreferences, globalPreferences);
            }
        });
        viewOptionsButton.setToolTipText("Target code view options");
        viewOptionsButton.setText(null);
        viewOptionsButton.setIcon(style().generalPreferencesIcon());
        toolBar().add(viewOptionsButton);

        toolBar().add(Box.createHorizontalGlue());
        addCodeViewCloseButton();

        final JScrollPane scrollPane = new InspectorScrollPane(inspection(), table);
        add(scrollPane, BorderLayout.CENTER);

        refresh(true);
        JTableColumnResizer.adjustColumnPreferredWidths(table);
    }

    @Override
    protected int getRowCount() {
        return table.getRowCount();
    }

    @Override
    protected int getSelectedRow() {
        return table.getSelectedRow();
    }

    @Override
    protected void setFocusAtRow(int row) {
        focus().setCodeLocation(instructionLocations().get(row));
    }

    @Override
    protected RowTextSearcher getRowTextSearcher() {
        return new TableRowTextSearcher(inspection, table);
    }

    /**
     * Global code selection has been set; return true iff the view contains selection.
     * Update even when the selection is set to the same value, because we want
     * that to force a scroll to make the selection visible.
     */
    @Override
    public boolean updateCodeFocus(MaxCodeLocation codeLocation) {
        return table.updateCodeFocus(codeLocation);
    }

    @Override
    public void refresh(boolean force) {
        super.refresh(force);
        table.refresh(force);
//      updateSize();
    }

    @Override
    public void redisplay() {
        super.redisplay();
        table.redisplay();
        // TODO (mlvdv)  code view hack for style changes
        table.setRowHeight(style().codeTableRowHeight());
        invalidate();
        repaint();
    }

    /**
     * A table specialized for displaying a block of disassembled target code, one instruction per line.
     */
    private final class TargetCodeTable extends InspectorTable {

        // TODO (mlvdv) Extract the table class

        TargetCodeTable(Inspection inspection, TargetCodeTableModel tableModel, TargetCodeTableColumnModel tableColumnModel) {
            super(inspection, tableModel, tableColumnModel);
            setFillsViewportHeight(true);
            setShowHorizontalLines(style().codeTableShowHorizontalLines());
            setShowVerticalLines(style().codeTableShowVerticalLines());
            setIntercellSpacing(style().codeTableIntercellSpacing());
            setRowHeight(style().codeTableRowHeight());
            setRowSelectionAllowed(true);
            setColumnSelectionAllowed(true);
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        }

        @Override
        protected void mouseButton1Clicked(int row, int col, MouseEvent mouseEvent) {
            if (mouseEvent.getClickCount() > 1) {
                // Depends on the first click selecting the row, and that changing the current
                // code location focus to the location under the mouse event.7
                actions().toggleTargetCodeBreakpoint().perform();
            }
        }

        @Override
        protected InspectorPopupMenu getPopupMenu(int row, int col, MouseEvent mouseEvent) {
            if (col == ObjectColumnKind.TAG.ordinal()) {
                final InspectorPopupMenu menu = new InspectorPopupMenu();
                final TargetCodeTableModel targetCodeTableModel = (TargetCodeTableModel) getModel();
                final MaxCodeLocation codeLocation = targetCodeTableModel.rowToLocation(row);
                menu.add(actions().debugRunToInstructionWithBreakpoints(codeLocation, "Run to this instruction"));
                menu.add(actions().debugRunToInstruction(codeLocation, "Run to this instruction (ignoring breakpoints)"));
                menu.add(actions().toggleTargetCodeBreakpoint(codeLocation, "Toggle breakpoint (double-click)"));
                menu.add(actions().setTargetCodeBreakpoint(codeLocation, "Set breakpoint"));
                menu.add(actions().removeTargetCodeBreakpoint(codeLocation, "Unset breakpoint"));
                return menu;
            }
            return null;
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {
            // The selection in the table has changed; might have happened via user action (click, arrow) or
            // as a side effect of a focus change.
            super.valueChanged(e);
            if (!e.getValueIsAdjusting()) {
                final int selectedRow = getSelectedRow();
                final TargetCodeTableModel targetCodeTableModel = (TargetCodeTableModel) getModel();
                if (selectedRow >= 0 && selectedRow < targetCodeTableModel.getRowCount()) {
                    focus().setCodeLocation(targetCodeTableModel.rowToLocation(selectedRow));
                }
            }
        }

        /**
         * Global code selection has been set; return true iff the view contains selection.
         * Update even when the selection is set to the same value, because we want
         * that to force a scroll to make the selection visible.
         */
        public boolean updateCodeFocus(MaxCodeLocation codeLocation) {
            final int oldSelectedRow = getSelectedRow();
            if (codeLocation.hasAddress()) {
                final Address targetCodeInstructionAddress = focus().codeLocation().address();
                if (teleTargetRoutine().targetCodeRegion().contains(targetCodeInstructionAddress)) {
                    final TargetCodeTableModel model = (TargetCodeTableModel) getModel();
                    final int row = model.findRow(targetCodeInstructionAddress);
                    if (row >= 0) {
                        if (row != oldSelectedRow) {
                            updateSelection(row);
                            Trace.line(TRACE_VALUE, tracePrefix() + "changeSelection " + row);
                        }
                        scrollToRows(row, row);
                        Trace.line(TRACE_VALUE, tracePrefix() + " scroll to row " + row);
                        return true;
                    }
                }
            }
            // View doesn't contain the focus; clear any old selection
            if (oldSelectedRow >= 0) {
                clearSelection();
            }
            return false;
        }
    }

    private final class TargetCodeTableColumnModel extends InspectorTableColumnModel<TargetCodeColumnKind> {

        private TargetCodeTableColumnModel(TargetCodeViewPreferences viewPreferences) {
            super(TargetCodeColumnKind.VALUES.length(), viewPreferences);
            final Address startAddress = tableModel.rowToInstruction(0).address;
            addColumn(TargetCodeColumnKind.TAG, new TagRenderer(), null);
            addColumn(TargetCodeColumnKind.NUMBER, new NumberRenderer(), null);
            addColumn(TargetCodeColumnKind.ADDRESS, new AddressRenderer(startAddress), null);
            addColumn(TargetCodeColumnKind.POSITION, new PositionRenderer(startAddress), null);
            addColumn(TargetCodeColumnKind.LABEL, new LabelRenderer(startAddress), null);
            addColumn(TargetCodeColumnKind.INSTRUCTION, new InstructionRenderer(inspection), null);
            addColumn(TargetCodeColumnKind.OPERANDS, operandsRenderer, null);
            addColumn(TargetCodeColumnKind.SOURCE_LINE, sourceLineRenderer, null);
            addColumn(TargetCodeColumnKind.BYTES, new BytesRenderer(inspection), null);
        }
    }

    /**
     * Data model representing a block of disassembled code, one row per instruction.
     */
    private final class TargetCodeTableModel extends InspectorTableModel {

        final TeleTargetRoutine teleTargetRoutine;

        public TargetCodeTableModel(Inspection inspection, TeleTargetRoutine teleTargetRoutine) {
            super(inspection);
            assert teleTargetRoutine != null;
            this.teleTargetRoutine = teleTargetRoutine;
        }

        public int getColumnCount() {
            return TargetCodeColumnKind.VALUES.length();
        }

        public int getRowCount() {
            return teleTargetRoutine.getInstructions().length();
        }

        public Object getValueAt(int row, int col) {
            final TargetCodeInstruction targetCodeInstruction = rowToInstruction(row);
            switch (TargetCodeColumnKind.VALUES.get(col)) {
                case TAG:
                    return null;
                case NUMBER:
                    return row;
                case ADDRESS:
                    return targetCodeInstruction.address;
                case POSITION:
                    return targetCodeInstruction.position;
                case LABEL:
                    final String label = targetCodeInstruction.label;
                    return label != null ? label + ":" : "";
                case INSTRUCTION:
                    return targetCodeInstruction.mnemonic;
                case OPERANDS:
                    return targetCodeInstruction.operands;
                case SOURCE_LINE:
                    return "";
                case BYTES:
                    return targetCodeInstruction.bytes;
                default:
                    throw new RuntimeException("Column out of range: " + col);
            }
        }

        @Override
        public Class< ? > getColumnClass(int col) {
            switch (TargetCodeColumnKind.VALUES.get(col)) {
                case TAG:
                    return Object.class;
                case NUMBER:
                    return Integer.class;
                case ADDRESS:
                    return Address.class;
                case POSITION:
                    return Integer.class;
                case LABEL:
                case INSTRUCTION:
                case OPERANDS:
                case SOURCE_LINE:
                    return String.class;
                case BYTES:
                    return byte[].class;
                default:
                    throw new RuntimeException("Column out of range: " + col);
            }
        }

        public TargetCodeInstruction rowToInstruction(int row) {
            return teleTargetRoutine.getInstructions().get(row);
        }

        public MaxCodeLocation rowToLocation(int row) {
            return teleTargetRoutine.getInstructionLocations().get(row);
        }

        /**
         * @param address a code address in the VM.
         * @return the row in this block of code containing an instruction starting at the address, -1 if none.
         */
        public int findRow(Address address) {
            int row = 0;
            for (TargetCodeInstruction targetCodeInstruction : teleTargetRoutine.getInstructions()) {
                if (targetCodeInstruction.address.equals(address)) {
                    return row;
                }
                row++;
            }
            return -1;
        }
    }

    /**
     * Return the appropriate color for displaying the row's text depending on whether the instruction pointer is at
     * this row.
     *
     * @param row the row to check
     * @return the color to be used
     */
    private Color getRowTextColor(int row) {
        return isInstructionPointer(row) ? style().debugIPTextColor() : (isCallReturn(row) ? style().debugCallReturnTextColor() : null);
    }

    private void setBorderForRow(JComponent component, int row) {
        if (isBoundaryRow[row]) {
            component.setBorder(style().defaultPaneTopBorder());
        } else {
            component.setBorder(null);
        }
    }

    /**
     * Sets the background of a cell rendering component, depending on the row context.
     * <br>
     * Makes the renderer transparent if there is no special background needed.
     */
    private void setBackgroundForRow(JComponent component, int row) {

        if (isSearchMatchRow(row)) {
            component.setOpaque(true);
            component.setBackground(style().searchMatchedBackground());
        } else if (isStopRow[row]) {
            component.setOpaque(true);
            component.setBackground(stopBackgroundColor);
        } else {
            component.setOpaque(false);
            //component.setBackground(getBackground());
        }
    }

    private final class TagRenderer extends JLabel implements TableCellRenderer, TextSearchable, Prober {

        public Component getTableCellRendererComponent(JTable table, Object ignore, boolean isSelected, boolean hasFocus, int row, int col) {

            final StringBuilder toolTipText = new StringBuilder(100);
            final MaxStackFrame stackFrame = stackFrame(row);
            if (stackFrame != null) {
                toolTipText.append("Stack ");
                toolTipText.append(stackFrame.position());
                toolTipText.append(":  0x");
                toolTipText.append(stackFrame.codeLocation().address().toHexString());
                toolTipText.append("  thread=");
                toolTipText.append(inspection.nameDisplay().longName(stackFrame.stack().thread()));
                toolTipText.append("; ");
                if (stackFrame.isTop()) {
                    setIcon(style().debugIPTagIcon());
                    setForeground(style().debugIPTagColor());
                } else {
                    setIcon(style().debugCallReturnTagIcon());
                    setForeground(style().debugCallReturnTagColor());
                }
            } else {
                setIcon(null);
                setForeground(null);
            }
            setText(rowToTagText(row));
            final MaxBreakpoint targetBreakpoint = getTargetBreakpointAtRow(row);
            if (targetBreakpoint != null) {
                toolTipText.append(targetBreakpoint);
                if (targetBreakpoint.isEnabled()) {
                    setBorder(style().debugEnabledTargetBreakpointTagBorder());
                } else {
                    setBorder(style().debugDisabledTargetBreakpointTagBorder());
                }
            } else if (isBoundaryRow[row]) {
                setBorder(style().defaultPaneTopBorder());
            } else {
                setBorder(null);
            }
            setToolTipText(toolTipText.toString());
            setBackgroundForRow(this, row);
            return this;
        }

        public String getSearchableText() {
            return "";
        }

        public void redisplay() {
        }

        public void refresh(boolean force) {
        }
    }

    private final class NumberRenderer extends PlainLabel implements TableCellRenderer {

        public NumberRenderer() {
            super(inspection, "");
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            setValue(row);
            setToolTipText("Instruction no. " + row + "in method");
            setBackgroundForRow(this, row);
            setForeground(getRowTextColor(row));
            setBorderForRow(this, row);
            return this;
        }
    }

    private final class AddressRenderer extends LocationLabel.AsAddressWithPosition implements TableCellRenderer {

        private final Address entryAddress;

        AddressRenderer(Address entryAddress) {
            super(inspection, 0, entryAddress);
            this.entryAddress = entryAddress;
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            final Address address = (Address) value;
            setValue(address.minus(entryAddress).toInt());
            setBackgroundForRow(this, row);
            setForeground(getRowTextColor(row));
            setBorderForRow(this, row);
            return this;
        }
    }

    private final class PositionRenderer extends LocationLabel.AsPosition implements TableCellRenderer {
        private int position;

        public PositionRenderer(Address entryAddress) {
            super(inspection, 0, entryAddress);
            this.position = 0;
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            final Integer position = (Integer) value;
            if (this.position != position) {
                this.position = position;
                setValue(position);
            }
            setBackgroundForRow(this, row);
            setForeground(getRowTextColor(row));
            setBorderForRow(this, row);
            return this;
        }
    }

    private final class LabelRenderer extends LocationLabel.AsTextLabel implements TableCellRenderer {

        public LabelRenderer(Address entryAddress) {
            super(inspection, entryAddress);
            setOpaque(true);
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            final Integer position = (Integer) tableModel.getValueAt(row, TargetCodeColumnKind.POSITION.ordinal());
            setLocation(value.toString(), position);
            setFont(style().defaultFont());
            setBackgroundForRow(this, row);
            //setForeground(getRowTextColor(row));

            if (isInstructionPointer(row)) {
                setForeground(style().debugIPTextColor());
            } else if (isCallReturn(row)) {
                setForeground(style().debugCallReturnTextColor());
            } else {
                setForeground(null);
            }
            setBorderForRow(this, row);
            return this;
        }
    }

    private final class InstructionRenderer extends TargetCodeLabel implements TableCellRenderer {
        InstructionRenderer(Inspection inspection) {
            super(inspection, "");
            setOpaque(true);
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            setBackgroundForRow(this, row);
            setForeground(getRowTextColor(row));
            final String string = value.toString();
            setValue(string, null);
            setBorderForRow(this, row);
            return this;
        }
    }

    private interface LiteralRenderer {
        WordValueLabel render(Inspection inspection, String literalLoadText, Address literalAddress);
    }

    static final LiteralRenderer AMD64_LITERAL_RENDERER = new LiteralRenderer() {
        public WordValueLabel render(Inspection inspection, String literalLoadText, final Address literalAddress) {
            final WordValueLabel wordValueLabel = new WordValueLabel(inspection, WordValueLabel.ValueMode.LITERAL_REFERENCE, null) {
                @Override
                public Value fetchValue() {
                    return new WordValue(vm().readWord(literalAddress, 0));
                }
            };
            wordValueLabel.setPrefix(literalLoadText.substring(0, literalLoadText.indexOf("[")));
            wordValueLabel.setToolTipSuffix(" from RIP " + literalLoadText.substring(literalLoadText.indexOf("["), literalLoadText.length()));
            wordValueLabel.updateText();
            return wordValueLabel;
        }
    };

    static final LiteralRenderer SPARC_LITERAL_RENDERER = new LiteralRenderer() {
        public WordValueLabel render(Inspection inspection, String literalLoadText, final Address literalAddress) {
            final WordValueLabel wordValueLabel = new WordValueLabel(inspection, WordValueLabel.ValueMode.LITERAL_REFERENCE, null) {
                @Override
                public Value fetchValue() {
                    return new WordValue(vm().readWord(literalAddress, 0));
                }
            };
            wordValueLabel.setSuffix(literalLoadText.substring(literalLoadText.indexOf(",")));
            wordValueLabel.setToolTipSuffix(" from " + literalLoadText.substring(0, literalLoadText.indexOf(",")));
            wordValueLabel.updateText();
            return wordValueLabel;
        }
    };

    LiteralRenderer getLiteralRenderer(Inspection inspection) {
        InstructionSet instructionSet = vm().vmConfiguration().platform().instructionSet();
        switch (instructionSet) {
            case AMD64:
                return AMD64_LITERAL_RENDERER;
            case SPARC:
                return SPARC_LITERAL_RENDERER;
            case ARM:
            case PPC:
            case IA32:
                FatalError.unimplemented();
                return null;
        }
        ProgramError.unknownCase();
        return null;
    }

    private final class SourceLineRenderer extends PlainLabel implements TableCellRenderer {

        private BytecodeLocation lastBytecodeLocation;

        SourceLineRenderer() {
            super(JTableTargetCodeViewer.this.inspection(), null);
            addMouseListener(new InspectorMouseClickAdapter(inspection()) {
                @Override
                public void procedure(final MouseEvent mouseEvent) {
                    final BytecodeLocation bytecodeLocation = lastBytecodeLocation;
                    if (bytecodeLocation != null) {
                        final InspectorPopupMenu menu = new InspectorPopupMenu();
                        for (BytecodeLocation location = bytecodeLocation; location != null; location = location.parent()) {
                            final StackTraceElement stackTraceElement = location.toStackTraceElement();
                            final String fileName = stackTraceElement.getFileName();
                            if (fileName != null) {
                                final int lineNumber = stackTraceElement.getLineNumber();
                                if (lineNumber > 0) {
                                    if (vm().findJavaSourceFile(location.classMethodActor.holder()) != null) {
                                        final BytecodeLocation locationCopy = location;
                                        menu.add(new AbstractAction("Open " + fileName + " at line " + lineNumber) {
                                            public void actionPerformed(ActionEvent e) {
                                                inspection().viewSourceExternally(locationCopy);
                                            }
                                        });
                                    }
                                }
                            }
                        }
                        if (menu.getComponentCount() > 0) {
                            menu.show(mouseEvent.getComponent(), mouseEvent.getX(), mouseEvent.getY());
                        }
                    }
                }
            });
        }

        private String toolTipText(StackTraceElement stackTraceElement) {
            String s = stackTraceElement.toString();
            final int openParen = s.indexOf('(');
            s = Classes.getSimpleName(stackTraceElement.getClassName()) + "." + stackTraceElement.getMethodName() + s.substring(openParen);
            final String text = s;
            return text;
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            final BytecodeLocation bytecodeLocation = rowToBytecodeLocation(row);
            setText("");
            setToolTipText("Source line not available");
            setBackgroundForRow(this, row);
            if (bytecodeLocation != null) {
                final StackTraceElement stackTraceElement = bytecodeLocation.toStackTraceElement();
                setText(String.valueOf(stackTraceElement.getLineNumber()));
                final StringBuilder stackTrace = new StringBuilder("<html><table cellpadding=\"1%\"><tr><td></td><td>").append(toolTipText(stackTraceElement)).append("</td></tr>");
                for (BytecodeLocation parent = bytecodeLocation.parent(); parent != null; parent = parent.parent()) {
                    stackTrace.append("<tr><td>--&gt;&nbsp;</td><td>").append(toolTipText(parent.toStackTraceElement())).append("</td></tr>");
                }
                setToolTipText(stackTrace.append("</table>").toString());
            }
            lastBytecodeLocation = bytecodeLocation;
            setBorderForRow(this, row);
            return this;
        }
    }

    private final class OperandsRenderer implements TableCellRenderer, Prober {
        private InspectorLabel[] inspectorLabels = new InspectorLabel[instructions().length()];
        private TargetCodeLabel targetCodeLabel = new TargetCodeLabel(inspection, "");
        private LiteralRenderer literalRenderer = getLiteralRenderer(inspection);

        public void refresh(boolean force) {
            for (InspectorLabel wordValueLabel : inspectorLabels) {
                if (wordValueLabel != null) {
                    wordValueLabel.refresh(force);
                }
            }
        }

        public void redisplay() {
            for (InspectorLabel wordValueLabel : inspectorLabels) {
                if (wordValueLabel != null) {
                    wordValueLabel.redisplay();
                }
            }
            targetCodeLabel.redisplay();
        }

        public Component getTableCellRendererComponent(JTable table, Object ignore, boolean isSelected, boolean hasFocus, int row, int col) {
            InspectorLabel renderer = inspectorLabels[row];
            if (renderer == null) {
                final TargetCodeInstruction targetCodeInstruction = tableModel.rowToInstruction(row);
                final String text = targetCodeInstruction.operands;
                if (targetCodeInstruction.targetAddress != null && !teleTargetRoutine().targetCodeRegion().contains(targetCodeInstruction.targetAddress)) {
                    renderer = new WordValueLabel(inspection, WordValueLabel.ValueMode.CALL_ENTRY_POINT, targetCodeInstruction.targetAddress, table);
                    inspectorLabels[row] = renderer;
                } else if (targetCodeInstruction.literalSourceAddress != null) {
                    final Address literalAddress = targetCodeInstruction.literalSourceAddress.asAddress();
                    renderer = literalRenderer.render(inspection, text, literalAddress);
                    inspectorLabels[row] = renderer;
                } else if (rowToCalleeIndex(row) >= 0 && targetCodeInstruction.mnemonic.contains("call")) {
                    final PoolConstantLabel poolConstantLabel = PoolConstantLabel.make(inspection, rowToCalleeIndex(row), localConstantPool(), teleConstantPool(), PoolConstantLabel.Mode.TERSE);
                    poolConstantLabel.setToolTipPrefix(text);
                    renderer = poolConstantLabel;
                    renderer.setForeground(getRowTextColor(row));
                } else {
                    final StopPositions stopPositions = teleTargetRoutine().getStopPositions();
                    if (stopPositions != null && stopPositions.isNativeFunctionCallPosition(targetCodeInstruction.position)) {
                        final TextLabel textLabel = new TextLabel(inspection, "<native function>", text);
                        renderer = textLabel;
                        renderer.setForeground(getRowTextColor(row));
                    } else {
                        renderer = targetCodeLabel;
                        renderer.setText(text);
                        renderer.setToolTipText(null);
                        renderer.setForeground(getRowTextColor(row));
                    }
                }
            }
            setBackgroundForRow(renderer, row);
            setBorderForRow(renderer, row);
            return renderer;
        }

    }

    private final class BytesRenderer extends DataLabel.ByteArrayAsHex implements TableCellRenderer {
        BytesRenderer(Inspection inspection) {
            super(inspection, null);
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            setBackgroundForRow(this, row);
            setForeground(getRowTextColor(row));
            setValue((byte[]) value);
            setBorderForRow(this, row);
            return this;
        }
    }

    @Override
    public void print(String name) {
        final MessageFormat header = new MessageFormat(name);
        final MessageFormat footer = new MessageFormat(vm().entityName() + ": " + codeViewerKindName() + "  Printed: " + new Date() + " -- Page: {0, number, integer}");
        try {
            table.print(JTable.PrintMode.FIT_WIDTH, header, footer);
        } catch (PrinterException printerException) {
            gui().errorMessage("Print failed: " + printerException.getMessage());
        }
    }
}

