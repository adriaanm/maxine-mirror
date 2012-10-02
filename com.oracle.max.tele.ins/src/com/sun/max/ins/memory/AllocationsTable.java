/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.ins.memory;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;

import com.sun.max.ins.*;
import com.sun.max.ins.debug.*;
import com.sun.max.ins.gui.*;
import com.sun.max.ins.value.*;
import com.sun.max.ins.value.WordValueLabel.ValueMode;
import com.sun.max.tele.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.value.*;

/**
 * A table specialized for displaying memory regions that the VM has allocated from the OS.
 */
public final class AllocationsTable extends InspectorTable {

    private final InspectorView view;
    private final AllocationsTableModel tableModel;

    AllocationsTable(Inspection inspection, InspectorView view, AllocationsViewPreferences viewPreferences) {
        super(inspection);
        this.view = view;
        this.tableModel = new AllocationsTableModel(inspection);
        AllocationsColumnModel columnModel = new AllocationsColumnModel(this, this.tableModel, viewPreferences);
        configureDefaultTable(tableModel, columnModel);
    }

    @Override
    protected InspectorPopupMenu getPopupMenu(int row, int col, MouseEvent mouseEvent) {
        final InspectorPopupMenu menu = new InspectorPopupMenu();
        final MaxMemoryRegion memoryRegion = tableModel.getMemoryRegion(row);
        final String regionName = memoryRegion.regionName();
        final JMenu viewMenu = new JMenu("View for \"" + regionName + "\"");
        InspectorAction ownerAction = null;
        if (memoryRegion instanceof MaxEntityMemoryRegion) {
            MaxEntityMemoryRegion entityMemoryRegion = (MaxEntityMemoryRegion) memoryRegion;
            if (entityMemoryRegion.owner() != null) {
                MaxObject representation = entityMemoryRegion.owner().representation();
                if (representation != null) {
                    final String actionTitle = "Owner: " + inspection().nameDisplay().longName(representation);
                    ownerAction = views().objects().makeViewAction(representation, actionTitle);
                }
            }
        }
        if (ownerAction == null) {
            ownerAction = actions().inertAction("Owner");
        }
        viewMenu.add(ownerAction);
        viewMenu.add(views().memory().makeViewAction(memoryRegion, regionName, "Memory"));
        menu.add(viewMenu);
        // menu.add(actions().setRegionWatchpoint(memoryRegionDisplay, "Watch region memory"));
        menu.add(Watchpoints.createEditMenu(inspection(), tableModel.getWatchpoints(row)));
        menu.add(Watchpoints.createRemoveActionOrMenu(inspection(), tableModel.getWatchpoints(row)));
        return menu;
    }

    /**
     * Sets table selection to the memory region, if any, that is the current user focus.
     */
    @Override
    public void updateFocusSelection() {
        final MaxMemoryRegion memoryRegion = focus().memoryRegion();
        final int row = tableModel.findRow(memoryRegion);
        updateSelection(row);
    }

    @Override
    public void valueChanged(ListSelectionEvent listSelectionEvent) {
        // Row selection changed, perhaps by user mouse click or navigation;
        // update user focus to follow the selection.
        super.valueChanged(listSelectionEvent);
        if (!listSelectionEvent.getValueIsAdjusting()) {
            final int row = getSelectedRow();
            if (row >= 0) {
                focus().setMemoryRegion(tableModel.getMemoryRegion(row));
            }
        }
    }

    /**
     * {@inheritDoc}.
     * <p>
     * Color the text specially in the row where a watchpoint is triggered
     */
    @Override
    public Color cellForegroundColor(int row, int col) {
        final MaxWatchpointEvent watchpointEvent = vm().state().watchpointEvent();
        if (watchpointEvent != null && tableModel.getMemoryRegion(row).contains(watchpointEvent.address())) {
            return preference().style().debugIPTagColor();
        }
        return null;
    }

    public InspectorView getView() {
        return view;
    }

    /**
     * Sets a display filter that will cause only the specified rows
     * to be displayed.
     *
     * @param displayedRows the rows to be displayed, sorted in ascending order, null if all should be displayed.
     */
    public void setDisplayedRows(int[] displayedRows) {
        tableModel.setDisplayedRows(displayedRows);
    }

    private final class AllocationsColumnModel extends InspectorTableColumnModel<AllocationsColumnKind> {

        private AllocationsColumnModel(InspectorTable table, InspectorMemoryTableModel tableModel, AllocationsViewPreferences viewPreferences) {
            super(inspection(), AllocationsColumnKind.values().length, viewPreferences);
            addColumnIfSupported(AllocationsColumnKind.TAG, new MemoryTagTableCellRenderer(inspection(), table, tableModel), null);
            addColumnIfSupported(AllocationsColumnKind.NAME, new NameCellRenderer(), null);
            addColumnIfSupported(AllocationsColumnKind.START, new StartAddressCellRenderer(), null);
            addColumnIfSupported(AllocationsColumnKind.MARK, new MarkAddressCellRenderer(), null);
            addColumnIfSupported(AllocationsColumnKind.END, new EndAddressCellRenderer(), null);
            addColumnIfSupported(AllocationsColumnKind.SIZE, new SizeCellRenderer(), null);
            addColumnIfSupported(AllocationsColumnKind.ALLOC, new AllocCellRenderer(), null);
        }
    }

    /**
     * A table data model built around the list of currently allocated memory regions in the VM.
     *
     */
    private final class AllocationsTableModel extends InspectorMemoryTableModel {

        private MaxMemoryRegion[] sortedRegions = null;
        private int[] displayedRows = null;

        public AllocationsTableModel(Inspection inspection) {
            super(inspection, Address.zero());
            refresh();
        }

        @Override
        public void refresh() {
            final List<MaxEntityMemoryRegion<? extends MaxEntity> > memoryAllocations = vm().state().memoryAllocations();
            sortedRegions = memoryAllocations.toArray(new MaxMemoryRegion[memoryAllocations.size()]);
            Arrays.sort(sortedRegions, MaxMemoryRegion.Util.startComparator());
            // Flush any filtering
            displayedRows = null;
            super.refresh();
        }

        /**
         * {@inheritDoc}
         * <p>
         * The number of rows actually being displayed, possibly filtered.
         */
        public int getRowCount() {
            return displayedRows == null ? sortedRegions.length : displayedRows.length;
        }

        public int getColumnCount() {
            return AllocationsColumnKind.values().length;
        }

        @Override
        public Class<?> getColumnClass(int row) {
            return MaxMemoryRegion.class;
        }

        public Object getValueAt(int row, int col) {
            return getMemoryRegion(row);
        }

        @Override
        public MaxMemoryRegion getMemoryRegion(int row) {
            return sortedRegions[displayed2ModelRow(row)];
        }

        @Override
        public int getOffset(int row) {
            return getAddress(row).toInt();
        }

        /**
         * {@inheritDoc}
         * <p>
         * Only find rows that are being displayed.
         */
        @Override
        public int findRow(Address address) {
            final int displayedRowCount = getRowCount();
            for (int row = 0; row < displayedRowCount; row++) {
                if (getMemoryRegion(row).contains(address)) {
                    return row;
                }
            }
            return -1;
        }

        @Override
        public String getRowDescription(int row) {
            return "Memory region \"" + getMemoryRegion(row).regionName() + "\"";
        }

        /**
         * Find the row, if any, whose memory region specifies the same region
         * of VM memory as the one specified.
         *
         * @param memoryRegion description of a region of VM memory
         */
        int findRow(MaxMemoryRegion memoryRegion) {
            final int displayedRowCount = getRowCount();
            for (int row = 0; row < displayedRowCount; row++) {
                if (getMemoryRegion(row).sameAs(memoryRegion)) {
                    return row;
                }
            }
            return -1;
        }

        public void setDisplayedRows(int[] displayedRows) {
            this.displayedRows = displayedRows;
            this.fireTableDataChanged();
        }

        private int displayed2ModelRow(int displayedRow) {
            return displayedRows == null ? displayedRow : displayedRows[displayedRow];
        }
    }

    private final class NameCellRenderer implements TableCellRenderer, Prober  {

        // The labels have important user interaction state, so create one per memory region and keep them around,
        // even though they may not always appear in the same row.
        private final Map<MaxMemoryRegion, InspectorLabel> regionToLabel = new HashMap<MaxMemoryRegion, InspectorLabel>();

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            final MaxMemoryRegion memoryRegion = (MaxMemoryRegion) value;
            if (memoryRegion == null) {
                return gui().getUnavailableDataTableCellRenderer();
            }
            InspectorLabel label = regionToLabel.get(memoryRegion);
            if (label == null) {
                label = new MemoryRegionNameLabel(inspection(), memoryRegion);
                regionToLabel.put(memoryRegion, label);
            }
            label.setForeground(cellForegroundColor(row, column));
            label.setBackground(cellBackgroundColor());
            return label;
        }

        public void redisplay() {
            for (Prober prober : regionToLabel.values()) {
                prober.redisplay();
            }
        }

        public void refresh(boolean force) {
            for (Prober prober : regionToLabel.values()) {
                prober.refresh(force);
            }
        }
    }

    private final class StartAddressCellRenderer implements TableCellRenderer, Prober {

        // ValueLabels have important user interaction state, so create one per memory region and keep them around,
        // even though they may not always appear in the same row.
        private final Map<MaxMemoryRegion, InspectorLabel> regionToLabel = new HashMap<MaxMemoryRegion, InspectorLabel>();

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            final MaxMemoryRegion memoryRegion = (MaxMemoryRegion) value;
            if (memoryRegion == null) {
                return gui().getUnavailableDataTableCellRenderer();
            }
            InspectorLabel label = regionToLabel.get(memoryRegion);
            if (label == null) {
                label = new WordValueLabel(inspection(), ValueMode.WORD, AllocationsTable.this) {

                    @Override
                    public Value fetchValue() {
                        return WordValue.from(memoryRegion.start());
                    }
                };
                label.setToolTipPrefix(tableModel.getRowDescription(row) + "<br>Starts @");
                label.setOpaque(true);
                regionToLabel.put(memoryRegion, label);
            }
            label.setBackground(cellBackgroundColor());
            return label;
        }

        public void redisplay() {
            for (Prober prober : regionToLabel.values()) {
                prober.redisplay();
            }
        }

        public void refresh(boolean force) {
            for (Prober prober : regionToLabel.values()) {
                prober.refresh(force);
            }
        }
    }

    private final class MarkAddressCellRenderer implements TableCellRenderer, Prober {

        // ValueLabels have important user interaction state, so create one per memory region and keep them around,
        // even though they may not always appear in the same row.
        private final Map<MaxMemoryRegion, DataLabel.AddressAsHex> regionToLabel = new HashMap<MaxMemoryRegion, DataLabel.AddressAsHex>();

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            final MaxMemoryRegion memoryRegion = (MaxMemoryRegion) value;
            if (memoryRegion == null || !memoryRegion.isAllocated()) {
                return gui().getUnavailableDataTableCellRenderer();
            }
            final Address mark = memoryRegion.mark();
            if (mark == null) {
                return gui().getEmptyDataTableCellRenderer();
            }
            DataLabel.AddressAsHex label = regionToLabel.get(memoryRegion);
            if (label == null) {
                label = new DataLabel.AddressAsHex(inspection(), Address.zero());
                label.setToolTipPrefix(tableModel.getRowDescription(row) + "<br>Next allocation @");
                label.setOpaque(true);
                regionToLabel.put(memoryRegion, label);
            }
            label.setValue(mark);
            label.setBackground(cellBackgroundColor());
            return label;
        }

        public void redisplay() {
            for (Prober prober : regionToLabel.values()) {
                prober.redisplay();
            }
        }

        public void refresh(boolean force) {
            for (Prober prober : regionToLabel.values()) {
                prober.refresh(force);
            }
        }
    }


    private final class EndAddressCellRenderer implements TableCellRenderer, Prober {

        // Labels have important user interaction state, so create one per memory region and keep them around,
        // even though they may not always appear in the same row.
        private final Map<MaxMemoryRegion, DataLabel.AddressAsHex> regionToLabel = new HashMap<MaxMemoryRegion, DataLabel.AddressAsHex>();

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            final MaxMemoryRegion memoryRegion = (MaxMemoryRegion) value;
            if (memoryRegion == null || !memoryRegion.isAllocated()) {
                return gui().getUnavailableDataTableCellRenderer();
            }
            DataLabel.AddressAsHex label = regionToLabel.get(memoryRegion);
            if (label == null) {
                label = new DataLabel.AddressAsHex(inspection(), Address.zero());
                label.setOpaque(true);
                regionToLabel.put(memoryRegion, label);
            }
            label.setValue(memoryRegion.end());
            label.setToolTipPrefix(tableModel.getRowDescription(row) + "<br>Ends @");
            label.setBackground(cellBackgroundColor());
            return label;
        }

        public void redisplay() {
            for (Prober prober : regionToLabel.values()) {
                prober.redisplay();
            }
        }

        public void refresh(boolean force) {
            for (Prober prober : regionToLabel.values()) {
                prober.refresh(force);
            }
        }
    }

    private final class SizeCellRenderer implements TableCellRenderer, Prober {

        private final Map<MaxMemoryRegion, InspectorLabel> regionToLabel = new HashMap<MaxMemoryRegion, InspectorLabel>();

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            final MaxMemoryRegion memoryRegion = (MaxMemoryRegion) value;
            if (memoryRegion == null || !memoryRegion.isAllocated()) {
                return gui().getUnavailableDataTableCellRenderer();
            }
            InspectorLabel label = regionToLabel.get(memoryRegion);
            if (label == null) {
                label = new MemoryAllocationsSizeLabel(inspection(), memoryRegion);
                regionToLabel.put(memoryRegion, label);
            }
            // Can't set the prefix (row description) permanently on the label, as they
            // are cached by location and may not always be displayed on the same row.
            label.setToolTipPrefix(tableModel.getRowDescription(row) + "<br>Size = ");
            label.setForeground(cellForegroundColor(row, column));
            label.setBackground(cellBackgroundColor());
            return label;
        }

        public void redisplay() {
            for (Prober prober : regionToLabel.values()) {
                prober.redisplay();
            }
        }

        public void refresh(boolean force) {
            for (Prober prober : regionToLabel.values()) {
                prober.refresh(force);
            }
        }
    }

    private final class AllocCellRenderer implements TableCellRenderer, Prober {

        private final Map<MaxMemoryRegion, InspectorLabel> regionToLabel = new HashMap<MaxMemoryRegion, InspectorLabel>();

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            final MaxMemoryRegion memoryRegion = (MaxMemoryRegion) value;
            if (memoryRegion == null || !memoryRegion.isAllocated()) {
                return gui().getUnavailableDataTableCellRenderer();
            }
            InspectorLabel label = regionToLabel.get(memoryRegion);
            if (label == null) {
                label = new MemoryRegionAllocationLabel(inspection(), memoryRegion, AllocationsTable.this);
                regionToLabel.put(memoryRegion, label);
            }
            label.setToolTipPrefix(tableModel.getRowDescription(row) + "<br>Alloc = ");
            label.setForeground(cellForegroundColor(row, column));
            label.setBackground(cellBackgroundColor());
            return label;
        }

        public void redisplay() {
            for (Prober prober : regionToLabel.values()) {
                prober.redisplay();
            }
        }

        public void refresh(boolean force) {
            for (Prober prober : regionToLabel.values()) {
                prober.refresh(force);
            }
        }
    }

}
