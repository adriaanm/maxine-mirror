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
package com.sun.max.ins.debug;

import java.awt.*;
import java.awt.event.*;
import java.util.*;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;

import com.sun.max.collect.*;
import com.sun.max.ins.*;
import com.sun.max.ins.gui.*;
import com.sun.max.ins.memory.*;
import com.sun.max.ins.value.*;
import com.sun.max.ins.value.WordValueLabel.*;
import com.sun.max.memory.*;
import com.sun.max.tele.*;
import com.sun.max.tele.memory.*;
import com.sun.max.tele.reference.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.classfile.*;
import com.sun.max.vm.classfile.LocalVariableTable.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.cps.jit.*;
import com.sun.max.vm.stack.*;
import com.sun.max.vm.stack.CompiledStackFrameLayout.*;
import com.sun.max.vm.value.*;

/**
 * A table that displays the contents of a VM compiled method stack frame in the VM.
 *
 * @author Michael Van De Vanter
 */
public class CompiledStackFrameTable extends InspectorTable {

    private final MaxStackFrame.Compiled compiledStackFrame;
    private final CompiledStackFrameViewPreferences viewPreferences;
    private final CompiledStackFrameTableModel tableModel;
    private final CompiledStackFrameTableColumnModel columnModel;

    /**
     * A table specialized to display the slots in a Java method stack frame in the VM.
     * <br>
     * Each slot is assumed to occupy one word in memory.
     * @param thread TODO
     */
    public CompiledStackFrameTable(Inspection inspection, MaxStackFrame.Compiled compiledStackFrame, CompiledStackFrameViewPreferences viewPreferences) {
        super(inspection);
        this.compiledStackFrame = compiledStackFrame;
        this.viewPreferences = viewPreferences;
        this.tableModel = new CompiledStackFrameTableModel(inspection, compiledStackFrame);
        this.columnModel = new CompiledStackFrameTableColumnModel(viewPreferences);
        configureMemoryTable(tableModel, columnModel);
    }

    @Override
    protected void mouseButton1Clicked(final int row, final int col, MouseEvent mouseEvent) {
        if (mouseEvent.getClickCount() > 1 && vm().watchpointManager() != null) {
            final InspectorAction toggleAction = new Watchpoints.ToggleWatchpointRowAction(inspection(), tableModel, row, "Toggle watchpoint") {

                @Override
                public MaxWatchpoint setWatchpoint() {
                    final MemoryRegion memoryRegion = tableModel.getMemoryRegion(row);
                    final String regionDescription =  "Stack: thread="  + inspection().nameDisplay().shortName(compiledStackFrame.stack().thread());
                    actions().setRegionWatchpoint(memoryRegion, "Set memory watchpoint", regionDescription).perform();
                    final Sequence<MaxWatchpoint> watchpoints = tableModel.getWatchpoints(row);
                    if (watchpoints.length() > 0) {
                        return watchpoints.first();
                    }
                    return null;
                }
            };
            toggleAction.perform();
        }
    }

    @Override
    protected InspectorPopupMenu getPopupMenu(final int row, final int col, MouseEvent mouseEvent) {
        if (vm().watchpointManager() != null && col == CompiledStackFrameColumnKind.TAG.ordinal()) {
            final InspectorPopupMenu menu = new InspectorPopupMenu();
            final MemoryRegion memoryRegion = tableModel.getMemoryRegion(row);
            final Slot slot = (Slot) tableModel.getValueAt(row, col);
            final String regionDescription =  "Stack slot : " + slot.name;
            menu.add(new Watchpoints.ToggleWatchpointRowAction(inspection(), tableModel, row, "Toggle watchpoint (double-click)") {

                @Override
                public MaxWatchpoint setWatchpoint() {

                    actions().setRegionWatchpoint(memoryRegion, "Set memory watchpoint", regionDescription).perform();
                    final Sequence<MaxWatchpoint> watchpoints = tableModel.getWatchpoints(row);
                    if (watchpoints.length() > 0) {
                        return watchpoints.first();
                    }
                    return null;
                }
            });
            menu.add(actions().setRegionWatchpoint(memoryRegion, "Watch this memory location", regionDescription));
            menu.add(Watchpoints.createEditMenu(inspection(), tableModel.getWatchpoints(row)));
            menu.add(Watchpoints.createRemoveActionOrMenu(inspection(), tableModel.getWatchpoints(row)));
            return menu;
        }
        return null;
    }

    @Override
    public void updateFocusSelection() {
        // Sets table selection to the memory word, if any, that is the current user focus.
        final Address address = focus().address();
        updateSelection(tableModel.findRow(address));
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        // The selection in the table has changed; might have happened via user action (click, arrow) or
        // as a side effect of a focus change.
        super.valueChanged(e);
        if (!e.getValueIsAdjusting()) {
            final int row = getSelectedRow();
            if (row >= 0 && row < tableModel.getRowCount()) {
                focus().setAddress(tableModel.getAddress(row));
            }
        }
    }

    /**
     * A column model for Java stack frames.
     * Column selection is driven by choices in the parent.
     * This implementation cannot update column choices dynamically.
     */
    private final class CompiledStackFrameTableColumnModel extends InspectorTableColumnModel<CompiledStackFrameColumnKind> {

        CompiledStackFrameTableColumnModel(CompiledStackFrameViewPreferences viewPreferences) {
            super(CompiledStackFrameColumnKind.VALUES.length(), viewPreferences);
            addColumn(CompiledStackFrameColumnKind.TAG, new TagRenderer(inspection()), null);
            addColumn(CompiledStackFrameColumnKind.NAME, new NameRenderer(inspection()), null);
            addColumn(CompiledStackFrameColumnKind.ADDRESS, new AddressRenderer(inspection()), null);
            addColumn(CompiledStackFrameColumnKind.OFFSET_SP, new OffsetSPRenderer(inspection()), null);
            addColumn(CompiledStackFrameColumnKind.OFFSET_FP, new OffsetFPRenderer(inspection()), null);
            addColumn(CompiledStackFrameColumnKind.VALUE, new ValueRenderer(inspection()), null);
            addColumn(CompiledStackFrameColumnKind.REGION, new RegionRenderer(inspection()), null);
        }
    }

    /**
     * A table model that represents the information in a Java stack frame as a table of
     * slots, one per memory word.
     * <br>
     * For the purposes of memory in this view, the origin is assumed to be the Stack Pointer.
     *
     */
    private final class CompiledStackFrameTableModel extends InspectorMemoryTableModel {

        private final MaxStackFrame.Compiled javaStackFrame;
        private final int frameSize;
        private final Slots slots;
        private final MemoryRegion[] regions;

        public CompiledStackFrameTableModel(Inspection inspection,  MaxStackFrame.Compiled javaStackFrame) {
            super(inspection, javaStackFrame.slotBase());
            this.javaStackFrame = javaStackFrame;
            frameSize = javaStackFrame.layout().frameSize();
            slots = javaStackFrame.layout().slots();
            regions = new MemoryRegion[slots.length()];
            int index = 0;
            for (Slot slot : slots) {
                regions[index] = new TeleMemoryRegion(getOrigin().plus(slot.offset), vm().wordSize(), "");
                index++;
            }
        }

        public int getColumnCount() {
            return CompiledStackFrameColumnKind.VALUES.length();
        }

        public int getRowCount() {
            return javaStackFrame.layout().slots().length();
        }

        public Object getValueAt(int rowIndex, int columnIndex) {
            return slots.slot(rowIndex);
        }

        @Override
        public Class< ? > getColumnClass(int col) {
            return Slot.class;
        }

        @Override
        public int findRow(Address address) {
            final int wordOffset = address.minus(getOrigin()).dividedBy(vm().wordSize()).toInt();
            return (wordOffset >= 0 && wordOffset < slots.length()) ? wordOffset : -1;
        }

        @Override
        public MemoryRegion getMemoryRegion(int row) {
            return regions[row];
        }

        @Override
        public Offset getOffset(int row) {
            // Slot offsets are relative to Stack Pointer
            return Offset.fromInt(slots.slot(row).offset);
        }

        /**
         * Offset of the slot relative to the Stack Pointer.
         *
         * @param row row number of the stack frame slot
         * @param biasOffset whether offsets should be biased
         * @return the slot offset relative to the SP
         */
        public Offset getSPOffset(int row, boolean biasOffset) {
            if (biasOffset) {
                return javaStackFrame.biasedFPOffset(getOffset(row)).plus(frameSize);
            }
            return getOffset(row);
        }

        /**
         * Offset of the slot relative to the Frame Pointer.
         *
         * @param row row number of the stack frame slot
         * @param biasOffset whether offsets should be biased
         * @return the slot offset relative to the FP
         */
        public Offset getFPOffset(int row, boolean biasOffset) {
            if (biasOffset) {
                return javaStackFrame.biasedFPOffset(getOffset(row));
            }
            return getOffset(row).minus(frameSize);
        }

        /**
         * Gets the Java source variable name (if any) for a given slot.
         *
         * @param row the slot for which the Java source variable name is being requested
         * @return the Java source name for {@code slot} or null if a name is not available
         */
        public String sourceVariableName(int row) {
            final TargetMethod targetMethod = javaStackFrame.targetMethod();
            if (targetMethod instanceof JitTargetMethod) {
                final JitTargetMethod jitTargetMethod = (JitTargetMethod) targetMethod;
                final JitStackFrameLayout jitLayout = (JitStackFrameLayout) javaStackFrame.layout();
                final int bytecodePosition = jitTargetMethod.bytecodePositionFor(javaStackFrame.ip());
                final ClassMethodActor classMethodActor = targetMethod.classMethodActor();
                CodeAttribute codeAttribute = classMethodActor == null ? null : classMethodActor.codeAttribute();
                if (bytecodePosition != -1 && codeAttribute != null) {
                    for (int localVariableIndex = 0; localVariableIndex < codeAttribute.maxLocals; ++localVariableIndex) {
                        final int localVariableOffset = jitLayout.localVariableOffset(localVariableIndex);
                        if (getOffset(row).equals(localVariableOffset)) {
                            final Entry entry = codeAttribute.localVariableTable().findLocalVariable(localVariableIndex, bytecodePosition);
                            if (entry != null) {
                                return entry.name(codeAttribute.constantPool).string;
                            }
                        }
                    }
                }
            }
            return null;
        }
    }

    /**
     * @return foreground color for row; color the text specially in the row where a watchpoint is triggered
     */
    private Color getRowTextColor(int row) {
        final MaxWatchpointEvent watchpointEvent = vm().state().watchpointEvent();
        if (watchpointEvent != null && tableModel.getMemoryRegion(row).contains(watchpointEvent.address())) {
            return style().debugIPTagColor();
        }
        return null;
    }

    private final class TagRenderer extends MemoryTagTableCellRenderer implements TableCellRenderer {

        TagRenderer(Inspection inspection) {
            super(inspection);
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            final Component renderer = getRenderer(tableModel.getMemoryRegion(row), focus().thread(), tableModel.getWatchpoints(row));
            renderer.setForeground(getRowTextColor(row));
            renderer.setBackground(cellBackgroundColor(isSelected));
            return renderer;
        }
    }

    private final class NameRenderer extends TextLabel implements TableCellRenderer {

        public NameRenderer(Inspection inspection) {
            super(inspection, null);
            setOpaque(true);
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            final Slot slot = (Slot) value;
            setText(slot.name);
            //setToolTipText("Stack frame name=" + slot.name);

            String otherInfo = "";
            if (viewPreferences.biasSlotOffsets()) {
                final Offset biasedOffset = tableModel.getFPOffset(row, viewPreferences.biasSlotOffsets());
                otherInfo = String.format("(%%fp %+d)", biasedOffset.toInt());
            }
            final String sourceVariableName = tableModel.sourceVariableName(row);
            final int offset = tableModel.getSPOffset(row, false).toInt();
            final String toolTipText = String.format("SP %+d%s%s", offset, otherInfo, sourceVariableName == null ? "" : " [" + sourceVariableName + "]");
            setToolTipText(toolTipText);
            setForeground(getRowTextColor(row));
            setBackground(cellBackgroundColor(isSelected));
            return this;
        }
    }

    private final class AddressRenderer extends LocationLabel.AsAddressWithOffset implements TableCellRenderer {

        AddressRenderer(Inspection inspection) {
            super(inspection, 0, tableModel.getOrigin());
            setOpaque(true);
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            setValue(tableModel.getOffset(row), tableModel.getOrigin());
            setForeground(getRowTextColor(row));
            setBackground(cellBackgroundColor(isSelected));
            return this;
        }
    }

    private final class OffsetSPRenderer extends LocationLabel.AsOffset implements TableCellRenderer {

        public OffsetSPRenderer(Inspection inspection) {
            super(inspection);
            setOpaque(true);
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            setValue(tableModel.getSPOffset(row, viewPreferences.biasSlotOffsets()), tableModel.getOrigin());
            setForeground(getRowTextColor(row));
            setBackground(cellBackgroundColor(isSelected));
            return this;
        }
    }

    private final class OffsetFPRenderer extends LocationLabel.AsOffset implements TableCellRenderer {

        public OffsetFPRenderer(Inspection inspection) {
            super(inspection);
            setOpaque(true);
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            setValue(tableModel.getFPOffset(row, viewPreferences.biasSlotOffsets()), tableModel.getAddress(0));
            setForeground(getRowTextColor(row));
            setBackground(cellBackgroundColor(isSelected));
            return this;
        }
    }

    private final class ValueRenderer extends DefaultTableCellRenderer implements Prober{

        private final Inspection inspection;
        // WordValueLabels have important user interaction state, so create one per memory location and keep them around,
        // even though they may not always appear in the same row.
        private final Map<Long, WordValueLabel> addressToLabelMap = new HashMap<Long, WordValueLabel>();

        public ValueRenderer(Inspection inspection) {
            this.inspection = inspection;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            final Address address = tableModel.getAddress(row);
            WordValueLabel label = addressToLabelMap.get(address.toLong());
            if (label == null) {
                label = new WordValueLabel(inspection, ValueMode.INTEGER_REGISTER, CompiledStackFrameTable.this) {
                    @Override
                    public Value fetchValue() {
                        return new WordValue(vm().readWord(address));
                    }
                };
                label.setOpaque(true);
                addressToLabelMap.put(address.toLong(), label);
            }
            label.setBackground(cellBackgroundColor(isSelected));
            return label;
        }

        public void redisplay() {
            for (WordValueLabel label : addressToLabelMap.values()) {
                if (label != null) {
                    label.redisplay();
                }
            }
        }

        public void refresh(boolean force) {
            for (WordValueLabel label : addressToLabelMap.values()) {
                if (label != null) {
                    label.refresh(force);
                }
            }
        }
    }

    private final class RegionRenderer extends MemoryRegionValueLabel implements TableCellRenderer {
        // Designed so that we only read memory lazily, for words that are visible
        // This label has no state, so we only need one.
        RegionRenderer(Inspection inspection) {
            super(inspection);
            setOpaque(true);
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, final int row, int column) {
            try {
                final Word word = vm().readWord(tableModel.getAddress(row));
                setValue(WordValue.from(word));
                setBackground(cellBackgroundColor(isSelected));
                return this;
            } catch (InvalidReferenceException invalidReferenceException) {
                return gui().getUnavailableDataTableCellRenderer();
            } catch (DataIOError dataIOError) {
                return gui().getUnavailableDataTableCellRenderer();
            }
        }
    }

}
