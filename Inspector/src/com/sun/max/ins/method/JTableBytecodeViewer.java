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

import com.sun.max.collect.*;
import com.sun.max.ins.*;
import com.sun.max.ins.constant.*;
import com.sun.max.ins.debug.*;
import com.sun.max.ins.gui.*;
import com.sun.max.program.*;
import com.sun.max.tele.*;
import com.sun.max.tele.object.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.bytecode.*;

/**
 * A table-based viewer for an (immutable) block of bytecodes.
 * Supports visual effects for execution state, and permits user selection
 * of instructions for various purposes (e.g. set breakpoint)
 *
 * @author Michael Van De Vanter
 */
public class JTableBytecodeViewer extends BytecodeViewer {

    /** Maximum literal string length displayed directly in operand field. */
    public static final int MAX_BYTECODE_OPERAND_DISPLAY = 15;

    private final Inspection inspection;
    private final BytecodeTable table;
    private final BytecodeTableModel tableModel;
    private final BytecodeViewPreferences instanceViewPreferences;

    public JTableBytecodeViewer(Inspection inspection, MethodInspector parent, TeleClassMethodActor teleClassMethodActor, TeleTargetMethod teleTargetMethod) {
        super(inspection, parent, teleClassMethodActor, teleTargetMethod);
        this.inspection = inspection;
        tableModel = new BytecodeTableModel(inspection, bytecodeInstructions());
        instanceViewPreferences = new BytecodeViewPreferences(BytecodeViewPreferences.globalPreferences(inspection())) {
            @Override
            public void setIsVisible(BytecodeColumnKind columnKind, boolean visible) {
                super.setIsVisible(columnKind, visible);
                table.getInspectorTableColumnModel().setColumnVisible(columnKind.ordinal(), visible);
                JTableColumnResizer.adjustColumnPreferredWidths(table);
                refresh(true);
            }
            @Override
            public void setOperandDisplayMode(PoolConstantLabel.Mode mode) {
                super.setOperandDisplayMode(mode);
                tableModel.fireTableDataChanged();
            }
        };
        final BytecodeTableColumnModel tableColumnModel = new BytecodeTableColumnModel(instanceViewPreferences);
        table = new BytecodeTable(inspection, tableModel, tableColumnModel);
        createView();
    }

    @Override
    protected void createView() {
        super.createView();

        // Set up toolbar
        // TODO (mlvdv) implement remaining debugging controls in Bytecode view
        // the disabled ones haven't been adapted for bytecode-based debugging
        JButton button = new InspectorButton(inspection, actions().toggleBytecodeBreakpoint());
        button.setToolTipText(button.getText());
        button.setText(null);
        button.setIcon(style().debugToggleBreakpointbuttonIcon());
        button.setEnabled(false);
        toolBar().add(button);

        button = new InspectorButton(inspection, actions().debugStepOver());
        button.setToolTipText(button.getText());
        button.setText(null);
        button.setIcon(style().debugStepOverButtonIcon());
        button.setEnabled(false);
        toolBar().add(button);

        button = new InspectorButton(inspection, actions().debugSingleStep());
        button.setToolTipText(button.getText());
        button.setText(null);
        button.setIcon(style().debugStepInButtonIcon());
        button.setEnabled(false);
        toolBar().add(button);

        button = new InspectorButton(inspection, actions().debugReturnFromFrame());
        button.setToolTipText(button.getText());
        button.setText(null);
        button.setIcon(style().debugStepOutButtonIcon());
        button.setEnabled(haveTargetCodeAddresses());
        toolBar().add(button);

        button = new InspectorButton(inspection, actions().debugRunToSelectedInstruction());
        button.setToolTipText(button.getText());
        button.setText(null);
        button.setIcon(style().debugRunToCursorButtonIcon());
        button.setEnabled(haveTargetCodeAddresses());
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

        toolBar().add(new TextLabel(inspection(), "Bytecode"));

        toolBar().add(Box.createHorizontalGlue());

        addSearchButton();

        addActiveRowsButton();

        final JButton viewOptionsButton = new InspectorButton(inspection, new AbstractAction("View...") {
            public void actionPerformed(ActionEvent actionEvent) {
                final BytecodeViewPreferences globalPreferences = BytecodeViewPreferences.globalPreferences(inspection());
                new TableColumnVisibilityPreferences.ColumnPreferencesDialog<BytecodeColumnKind>(inspection(), "Bytecode View Options", instanceViewPreferences, globalPreferences);
            }
        });
        viewOptionsButton.setToolTipText("Bytecode view options");
        viewOptionsButton.setText(null);
        viewOptionsButton.setIcon(style().generalPreferencesIcon());
        toolBar().add(viewOptionsButton);

        toolBar().add(Box.createHorizontalGlue());
        addCodeViewCloseButton();

        final JScrollPane scrollPane = new InspectorScrollPane(inspection, table);
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
        final int position = tableModel.rowToInstruction(row).position();
        focus().setCodeLocation(codeManager().createBytecodeLocation(teleClassMethodActor(), position, "bytecode view set focus"), false);
    }

    @Override
    protected RowTextSearcher getRowTextSearcher() {
        return new TableRowTextSearcher(inspection, table);
    }

   /**
     * Global code selection has changed.
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

    // TODO (mlvdv) Extract the table class
    private final class BytecodeTable extends InspectorTable {

        BytecodeTable(Inspection inspection, InspectorTableModel tableModel, InspectorTableColumnModel tableColumnModel) {
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
        public void valueChanged(ListSelectionEvent e) {
            // The selection in the table has changed; might have happened via user action (click, arrow) or
            // as a side effect of a focus change.
            super.valueChanged(e);
            if (!e.getValueIsAdjusting()) {
                final int selectedRow = getSelectedRow();
                final BytecodeTableModel bytecodeTableModel = (BytecodeTableModel) getModel();
                if (selectedRow >= 0 && selectedRow < bytecodeTableModel.getRowCount()) {
                    final BytecodeInstruction bytecodeInstruction = bytecodeTableModel.rowToInstruction(selectedRow);
                    final Address targetCodeFirstAddress = bytecodeInstruction.targetCodeFirstAddress();
                    final int position = bytecodeInstruction.position();
                    if (targetCodeFirstAddress.isZero()) {
                        focus().setCodeLocation(codeManager().createBytecodeLocation(teleClassMethodActor(), position, "bytecode view"));
                    } else {
                        focus().setCodeLocation(codeManager().createMachineCodeLocation(targetCodeFirstAddress, teleClassMethodActor(), position, "bytecode view"), true);
                    }
                }
            }
        }

        public boolean updateCodeFocus(MaxCodeLocation codeLocation) {
            final int oldSelectedRow = getSelectedRow();
            final BytecodeTableModel model = (BytecodeTableModel) getModel();
            int focusRow = -1;
            if (codeLocation.hasTeleClassMethodActor()) {
                if (codeLocation.teleClassMethodActor().classMethodActor() == teleClassMethodActor().classMethodActor()) {
                    focusRow = model.findRowAtPosition(codeLocation.bytecodePosition());
                }
            } else if (codeLocation.hasMethodKey()) {
                // Shouldn't happen, but...
                if (codeLocation.methodKey().equals(methodKey())) {
                    focusRow = model.findRowAtPosition(0);
                }
            } else if (codeLocation.hasAddress()) {
                if (teleTargetMethod() != null && teleTargetMethod().targetCodeRegion().contains(codeLocation.address())) {
                    focusRow = model.findRow(codeLocation.address());
                }
            }
            if (focusRow >= 0) {
                // View contains the focus; ensure it is selected and visible
                if (focusRow != oldSelectedRow) {
                    updateSelection(focusRow);
                }
                scrollToRows(focusRow, focusRow);
                return true;
            }
            // View doesn't contain the focus; clear any old selection
            if (oldSelectedRow >= 0) {
                clearSelection();
            }
            return false;
        }
    }

    private final class BytecodeTableColumnModel extends InspectorTableColumnModel<BytecodeColumnKind> {

        BytecodeTableColumnModel(BytecodeViewPreferences instanceViewPreferences) {
            super(BytecodeColumnKind.VALUES.length(), instanceViewPreferences);
            addColumn(BytecodeColumnKind.TAG, new TagRenderer(), null);
            addColumn(BytecodeColumnKind.NUMBER, new NumberRenderer(), null);
            addColumn(BytecodeColumnKind.POSITION, new PositionRenderer(), null);
            addColumn(BytecodeColumnKind.INSTRUCTION, new InstructionRenderer(), null);
            addColumn(BytecodeColumnKind.OPERAND1, new OperandRenderer(), null);
            addColumn(BytecodeColumnKind.OPERAND2, new OperandRenderer(), null);
            addColumn(BytecodeColumnKind.SOURCE_LINE, new SourceLineRenderer(), null);
            addColumn(BytecodeColumnKind.BYTES, new BytesRenderer(), null);
        }
    }

    private final class BytecodeTableModel extends InspectorTableModel {

        private AppendableIndexedSequence<BytecodeInstruction> bytecodeInstructions;

        public BytecodeTableModel(Inspection inspection, AppendableIndexedSequence<BytecodeInstruction> bytecodeInstructions) {
            super(inspection);
            this.bytecodeInstructions = bytecodeInstructions;
        }

        public int getColumnCount() {
            return BytecodeColumnKind.VALUES.length();
        }

        public int getRowCount() {
            return bytecodeInstructions().length();
        }

        public Object getValueAt(int row, int col) {
            final BytecodeInstruction instruction = rowToInstruction(row);
            switch (BytecodeColumnKind.VALUES.get(col)) {
                case TAG:
                    return null;
                case NUMBER:
                    return row;
                case POSITION:
                    return new Integer(instruction.position);
                case INSTRUCTION:
                    return instruction.opcode;
                case OPERAND1:
                    return instruction.operand1;
                case OPERAND2:
                    return instruction.operand2;
                case SOURCE_LINE:
                    return new BytecodeLocation(teleClassMethodActor().classMethodActor(), instruction.position);
                case BYTES:
                    return instruction.instructionBytes;
                default:
                    throw new RuntimeException("Column out of range: " + col);
            }
        }

        @Override
        public Class< ? > getColumnClass(int col) {
            switch (BytecodeColumnKind.VALUES.get(col)) {
                case TAG:
                    return Object.class;
                case NUMBER:
                    return Integer.class;
                case POSITION:
                    return Integer.class;
                case INSTRUCTION:
                    return Bytecode.class;
                case OPERAND1:
                case OPERAND2:
                case SOURCE_LINE:
                    return Object.class;
                case BYTES:
                    return byte[].class;
                default:
                    throw new RuntimeException("Column out of range: " + col);
            }
        }

        public BytecodeInstruction rowToInstruction(int row) {
            return bytecodeInstructions.get(row);
        }

        /**
         * @param position a position (in bytes) in this block of bytecodes
         * @return the row in this block of bytecodes containing an instruction starting at this position, -1 if none
         */
        public int findRowAtPosition(int position) {
            for (BytecodeInstruction instruction : bytecodeInstructions) {
                if (instruction.position() == position) {
                    return instruction.row();
                }
            }
            return -1;
        }

        /**
         * @param address a code address in the VM
         * @return the row in this block of bytecodes containing an
         *  instruction whose associated compiled code starts at the address, -1 if none.
         */
        public int findRow(Address address) {
            if (haveTargetCodeAddresses()) {
                for (BytecodeInstruction instruction : bytecodeInstructions) {
                    int row = instruction.row();
                    if (rowContainsAddress(row, address)) {
                        return row;
                    }
                    row++;
                }
            }
            return -1;
        }

    }

    /**
     * @return the default color to be used for all text labels on the row
     */
    private Color getRowTextColor(int row) {
        return isInstructionPointer(row) ? style().debugIPTextColor() : (isCallReturn(row) ? style().debugCallReturnTextColor() : null);
    }

    /**
     * @return Color to be used for the background of all row labels; may have special overrides in future, as for Target Code
     */
    private Color getRowBackgroundColor(int row) {
        final IndexedSequence<Integer> searchMatchingRows = getSearchMatchingRows();
        if (searchMatchingRows != null) {
            for (int matchingRow : searchMatchingRows) {
                if (row == matchingRow) {
                    return style().searchMatchedBackground();
                }
            }
        }
        return table.getBackground();
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
        } else {
            component.setOpaque(false);
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
                toolTipText.append(" thread=");
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
            final MaxBreakpoint bytecodeBreakpoint = getBytecodeBreakpointAtRow(row);
            final Sequence<MaxBreakpoint> targetBreakpoints = getTargetBreakpointsAtRow(row);
            if (bytecodeBreakpoint != null) {
                toolTipText.append(bytecodeBreakpoint);
                toolTipText.append("; ");
                if (bytecodeBreakpoint.isEnabled()) {
                    setBorder(style().debugEnabledBytecodeBreakpointTagBorder());
                } else {
                    setBorder(style().debugDisabledBytecodeBreakpointTagBorder());
                }
            } else if (targetBreakpoints.length() > 0) {
                boolean enabled = false;
                for (MaxBreakpoint targetBreakpoint : targetBreakpoints) {
                    toolTipText.append(targetBreakpoint);
                    toolTipText.append("; ");
                    enabled = enabled || targetBreakpoint.isEnabled();
                }
                if (enabled) {
                    setBorder(style().debugEnabledTargetBreakpointTagBorder());
                } else {
                    setBorder(style().debugDisabledTargetBreakpointTagBorder());
                }
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
            return this;
        }
    }

    private final class PositionRenderer extends LocationLabel.AsPosition implements TableCellRenderer {
        private int position;

        public PositionRenderer() {
            super(inspection, 0);
            position = 0;
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            final Integer position = (Integer) value;
            if (this.position != position) {
                this.position = position;
                setValue(position);
            }
            setBackgroundForRow(this, row);
            setForeground(getRowTextColor(row));
            return this;
        }
    }

    private final class InstructionRenderer extends BytecodeMnemonicLabel implements TableCellRenderer {

        public InstructionRenderer() {
            super(inspection, null);
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            final Bytecode opcode = (Bytecode) value;
            setValue(opcode);
            setBackgroundForRow(this, row);
            setForeground(getRowTextColor(row));
            return this;
        }
    }

    private final class OperandRenderer implements  TableCellRenderer, Prober {

        public OperandRenderer() {
        }

        public Component getTableCellRendererComponent(JTable table, Object tableValue, boolean isSelected, boolean hasFocus, int row, int col) {
            JComponent renderer = null;
            if (tableValue instanceof JComponent) {
                // BytecodePrinter returns a label component for simple values
                renderer = (JComponent) tableValue;
                renderer.setForeground(getRowTextColor(row));
            } else if (tableValue instanceof Integer) {
                // BytecodePrinter returns index of a constant pool entry, when that's the operand
                final int index = ((Integer) tableValue).intValue();
                renderer =  PoolConstantLabel.make(inspection(), index, localConstantPool(), teleConstantPool(), instanceViewPreferences.operandDisplayMode());
                if (renderer.getForeground() == null) {
                    setForeground(getRowTextColor(row));
                }
                setFont(style().bytecodeOperandFont());
            } else {
                ProgramError.unexpected("unrecognized table value at row=" + row + ", col=" + col);
            }
            setBackgroundForRow(renderer, row);
            return renderer;
        }

        public void redisplay() {
        }

        public void refresh(boolean force) {
        }
    }

    private final class SourceLineRenderer extends PlainLabel implements TableCellRenderer {
        private BytecodeLocation lastBytecodeLocation;
        SourceLineRenderer() {
            super(JTableBytecodeViewer.this.inspection(), null);
            addMouseListener(new InspectorMouseClickAdapter(inspection()) {
                @Override
                public void procedure(final MouseEvent mouseEvent) {
                    final BytecodeLocation bytecodeLocation = lastBytecodeLocation;
                    if (bytecodeLocation != null) {
                        inspection().viewSourceExternally(bytecodeLocation);
                    }
                }
            });
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            final BytecodeLocation bytecodeLocation = (BytecodeLocation) value;
            final String sourceFileName = bytecodeLocation.sourceFileName();
            final int lineNumber = bytecodeLocation.sourceLineNumber();
            if (sourceFileName != null && lineNumber >= 0) {
                setText(String.valueOf(lineNumber));
                setToolTipText(sourceFileName + ":" + lineNumber);
            } else {
                setText("");
                setToolTipText("Source line not available");
            }
            setBackgroundForRow(this, row);
            lastBytecodeLocation = bytecodeLocation;
            return this;
        }
    }

    private final class BytesRenderer extends DataLabel.ByteArrayAsHex implements TableCellRenderer {
        BytesRenderer() {
            super(inspection, null);
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            setBackgroundForRow(this, row);
            setForeground(getRowTextColor(row));
            setValue((byte[]) value);
            return this;
        }
    }

    @Override
    public void print(String name) {
        final MessageFormat header = new MessageFormat(name);
        final MessageFormat footer = new MessageFormat("Maxine: " + codeViewerKindName() + "  Printed: " + new Date() + " -- Page: {0, number, integer}");
        try {
            table.print(JTable.PrintMode.FIT_WIDTH, header, footer);
        } catch (PrinterException printerException) {
            gui().errorMessage("Print failed: " + printerException.getMessage());
        }
    }
}
