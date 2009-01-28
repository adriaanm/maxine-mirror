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
package com.sun.max.ins.object;

import java.awt.*;
import java.awt.event.*;
import java.util.*;

import javax.swing.*;
import javax.swing.table.*;

import com.sun.max.ins.*;
import com.sun.max.ins.gui.*;
import com.sun.max.ins.type.*;
import com.sun.max.ins.value.*;
import com.sun.max.tele.object.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.type.*;
import com.sun.max.vm.value.*;

/**
 * A table-based panel that displays fields in a Maxine low level heap object (in tuples or hybrids).
 *
 * @author Michael Van De Vanter
 */
@Deprecated
public class JTableObjectFieldsPanel extends InspectorPanel {

    private final ObjectInspector _objectInspector;
    private final Inspection _inspection;
    private final FieldActor[] _fieldActors;
    private final TeleObject _teleObject;
    private Pointer _objectOrigin;
    private final boolean _isTeleActor;

    private final JTable _table;
    private final MyTableModel _model;
    private final MyTableColumnModel _columnModel;
    private final TableColumn[] _columns;

    /**
     * Creates a panel for displaying a list of object fields.
     *
     * @param objectInspector parent that contains this panel
     * @param fieldActors description of the fields to be displayed
     */
    public JTableObjectFieldsPanel(final ObjectInspector objectInspector, Collection<FieldActor> fieldActors) {
        super(objectInspector.inspection(), new BorderLayout());
        _objectInspector = objectInspector;
        _inspection = objectInspector.inspection();
        _fieldActors = new FieldActor[fieldActors.size()];
        _teleObject = objectInspector.teleObject();
        _isTeleActor = _teleObject instanceof TeleActor;

        // Sort fields by offset in object layout.
        fieldActors.toArray(_fieldActors);
        java.util.Arrays.sort(_fieldActors, new Comparator<FieldActor>() {
            public int compare(FieldActor a, FieldActor b) {
                final Integer aOffset = a.offset();
                return aOffset.compareTo(b.offset());
            }
        });

        _model = new MyTableModel();
        _columns = new TableColumn[ObjectFieldColumnKind.VALUES.length()];
        _columnModel = new MyTableColumnModel(_objectInspector);
        _table = new MyTable(_model, _columnModel);
        _table.setOpaque(true);
        _table.setBackground(style().defaultBackgroundColor());
        _table.setFillsViewportHeight(true);
        _table.setShowHorizontalLines(false);
        _table.setShowVerticalLines(false);
        _table.setIntercellSpacing(new Dimension(0, 0));
        _table.setRowHeight(20);
        _table.addMouseListener(new TableCellMouseClickAdapter(_inspection, _table));

        refresh(_inspection.teleVM().epoch(), true);
        JTableColumnResizer.adjustColumnPreferredWidths(_table);
        final JScrollPane scrollPane = new JScrollPane(_table, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBackground(style().defaultBackgroundColor());
        scrollPane.setOpaque(true);
        //add(_table, BorderLayout.CENTER);
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Models the fields/rows in a list of object fields;
     * the value of each cell is the {@link FieldActor} that describes the field.
     */
    private final class MyTableModel extends AbstractTableModel {

        public int getColumnCount() {
            return ObjectFieldColumnKind.VALUES.length();
        }

        public int getRowCount() {
            return _fieldActors.length;
        }

        public Object getValueAt(int row, int col) {
            return _fieldActors[row];
        }

        @Override
        public Class< ? > getColumnClass(int col) {
            return FieldActor.class;
        }
    }

    /**
     * A table customized with tool tips in the column headers.
     */
    private final class MyTable extends JTable {

        MyTable(TableModel model, TableColumnModel tableColumnModel) {
            super(model, tableColumnModel);
        }

        @Override
        protected JTableHeader createDefaultTableHeader() {
            System.out.println(this.getClass().toString() + "create Header");
            return new JTableHeader(_columnModel) {
                @Override
                public String getToolTipText(MouseEvent mouseEvent) {
                    final Point p = mouseEvent.getPoint();
                    final int index = _columnModel.getColumnIndexAtX(p.x);
                    final int modelIndex = _columnModel.getColumn(index).getModelIndex();
                    return ObjectFieldColumnKind.VALUES.get(modelIndex).toolTipText();
                }
            };
        }
    }

    /**
     * A column model for object headers, to be used in an {@link ObjectInspector}.
     * Column selection is driven by choices in the parent {@link ObjectInspector}.
     * This implementation cannot update column choices dynamically.
     */
    private final class MyTableColumnModel extends DefaultTableColumnModel {

        MyTableColumnModel(ObjectInspector objectInspector) {
            createColumn(ObjectFieldColumnKind.ADDRESS, new AddressRenderer(), objectInspector.showAddresses());
            createColumn(ObjectFieldColumnKind.POSITION, new PositionRenderer(), objectInspector.showOffsets());
            createColumn(ObjectFieldColumnKind.TYPE, new TypeRenderer(), objectInspector.showTypes());
            createColumn(ObjectFieldColumnKind.NAME, new NameRenderer(), true);
            createColumn(ObjectFieldColumnKind.VALUE, new ValueRenderer(), true);
            createColumn(ObjectFieldColumnKind.REGION, new RegionRenderer(), objectInspector.showMemoryRegions());
        }

        private void createColumn(ObjectFieldColumnKind columnKind, TableCellRenderer renderer, boolean isVisible) {
            final int col = columnKind.ordinal();
            _columns[col] = new TableColumn(col, 0, renderer, null);
            _columns[col].setHeaderValue(columnKind.label());
            _columns[col].setMinWidth(columnKind.minWidth());
            if (isVisible) {
                addColumn(_columns[col]);
            }
            _columns[col].setIdentifier(columnKind);
        }
    }

    private final class AddressRenderer extends LocationLabel.AsAddressWithOffset implements TableCellRenderer {

        AddressRenderer() {
            super(_inspection, 0, Address.zero());
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            final FieldActor fieldActor = (FieldActor) value;
            setValue(fieldActor.offset(), _objectOrigin);
            return this;
        }
    }

    private final class PositionRenderer extends LocationLabel.AsOffset implements TableCellRenderer {

        public PositionRenderer() {
            super(_inspection, 0, Address.zero());
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            final FieldActor fieldActor = (FieldActor) value;
            setValue(fieldActor.offset(), _objectOrigin);
            return this;
        }
    }

    private final class TypeRenderer implements TableCellRenderer, Prober {

        private TypeLabel[] _labels = new TypeLabel[_fieldActors.length];

        public void refresh(long epoch, boolean force) {
            for (InspectorLabel label : _labels) {
                if (label != null) {
                    label.refresh(epoch, force);
                }
            }
        }

        @Override
        public void redisplay() {
            for (InspectorLabel label : _labels) {
                if (label != null) {
                    label.redisplay();
                }
            }
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            TypeLabel label = _labels[row];
            if (label == null) {
                final FieldActor fieldActor = (FieldActor) value;
                label = new TypeLabel(_inspection, fieldActor.descriptor());
                _labels[row] = label;
            }
            return label;
        }
    }

    private final class NameRenderer extends FieldActorNameLabel implements TableCellRenderer {

        public NameRenderer() {
            super(_inspection, null);
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            final FieldActor fieldActor = (FieldActor) value;
            setValue(fieldActor);
            return this;
        }
    }

    private final class ValueRenderer implements TableCellRenderer, Prober {

        private InspectorLabel[] _labels = new InspectorLabel[_fieldActors.length];

        public void refresh(long epoch, boolean force) {
            for (InspectorLabel label : _labels) {
                if (label != null) {
                    label.refresh(epoch, force);
                }
            }
        }

        @Override
        public void redisplay() {
            for (InspectorLabel label : _labels) {
                if (label != null) {
                    label.redisplay();
                }
            }
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            InspectorLabel label = _labels[row];
            if (label == null) {
                final FieldActor fieldActor = (FieldActor) value;
                if (fieldActor.kind() == Kind.REFERENCE) {
                    label = new WordValueLabel(_inspection, WordValueLabel.ValueMode.REFERENCE) {
                        @Override
                        public Value fetchValue() {
                            return _teleObject.readFieldValue(fieldActor);
                        }
                    };
                } else if (fieldActor.kind() == Kind.WORD) {
                    label = new WordValueLabel(_inspection, WordValueLabel.ValueMode.WORD) {
                        @Override
                        public Value fetchValue() {
                            return _teleObject.readFieldValue(fieldActor);
                        }
                    };
                } else if (_isTeleActor && fieldActor.name().toString().equals("_flags")) {
                    final TeleActor teleActor = (TeleActor) _teleObject;
                    label = new ActorFlagsValueLabel(_inspection, teleActor);
                } else {
                    label = new PrimitiveValueLabel(_inspection, fieldActor.kind()) {
                        @Override
                        public Value fetchValue() {
                            return _teleObject.readFieldValue(fieldActor);
                        }
                    };
                }
                _labels[row] = label;
            }
            return label;
        }
    }

    private final class RegionRenderer implements TableCellRenderer, Prober {

        private InspectorLabel[] _labels = new InspectorLabel[_fieldActors.length];

        public void refresh(long epoch, boolean force) {
            for (InspectorLabel label : _labels) {
                if (label != null) {
                    label.refresh(epoch, force);
                }
            }
        }

        @Override
        public void redisplay() {
            for (InspectorLabel label : _labels) {
                if (label != null) {
                    label.redisplay();
                }
            }
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            InspectorLabel label = _labels[row];
            if (label == null) {
                final FieldActor fieldActor = (FieldActor) value;
                label = new MemoryRegionValueLabel(_inspection) {
                    @Override
                    public Value fetchValue() {
                        return _teleObject.readFieldValue(fieldActor);
                    }
                };
                _labels[row] = label;
            }
            return label;
        }
    }

    @Override
    public void redisplay() {
        for (TableColumn column : _columns) {
            final Prober prober = (Prober) column.getCellRenderer();
            prober.redisplay();
        }
        invalidate();
        repaint();
    }

    private long _lastRefreshEpoch = -1;

    @Override
    public void refresh(long epoch, boolean force) {
        if (epoch > _lastRefreshEpoch || force) {
            _lastRefreshEpoch = epoch;
            _objectOrigin = _teleObject.getCurrentOrigin();
            for (TableColumn column : _columns) {
                final Prober prober = (Prober) column.getCellRenderer();
                prober.refresh(epoch, force);
            }
        }
    }

}
