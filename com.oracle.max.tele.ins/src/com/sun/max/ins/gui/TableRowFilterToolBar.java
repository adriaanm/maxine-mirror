/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.event.*;
import java.util.regex.*;

import javax.swing.*;
import javax.swing.event.*;

import com.sun.max.ins.*;
import com.sun.max.ins.util.*;

/**
 * A toolbar containing controls for identifying rows that match a (optionally regexp) pattern.
 */
public class TableRowFilterToolBar extends InspectorToolBar {

    private final RowMatchListener parent;
    private final TableRowTextMatcher tableRowMatcher;
    private final InspectorCheckBox regexpCheckBox;
    private final JTextField textField;
    private final Color textFieldDefaultBackground;
    private final JLabel statusLabel;

    private int[] matchingRows = null;

    private class SearchTextListener implements DocumentListener {
        public void changedUpdate(DocumentEvent e) {
            // This should never be called
            InspectorError.unexpected();
        }

        public void insertUpdate(DocumentEvent event) {
            processTextInput();
        }

        public void removeUpdate(DocumentEvent e) {
            processTextInput();
        }
    }

    /**
     * Creates a toolbar with controls for performing regular expression filtering over a row-based view.
     *
     * @param parent where to send search outcomes and user requests
     * @param jTable the table to be filtered
     */
    public TableRowFilterToolBar(Inspection inspection, RowMatchListener parent, JTable jTable) {
        super(inspection);
        this.parent = parent;
        tableRowMatcher = new TableRowTextMatcher(inspection, jTable);
        setBorder(inspection.preference().style().defaultPaneBorder());
        setFloatable(false);
        setRollover(true);
        add(new TextLabel(inspection, "Filter pattern: "));

        regexpCheckBox = new InspectorCheckBox(inspection, "regexp", "Treat filter pattern as a regular expression?", false);
        regexpCheckBox.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                processTextInput();
            }
        });

        textField = new JTextField();
        textField.setColumns(10);  // doesn't seem to have an effect
        textFieldDefaultBackground = textField.getBackground();
        textField.setToolTipText("Search code for regexp pattern, case-insensitive, Return=Next");
        textField.getDocument().addDocumentListener(new SearchTextListener());

        textField.requestFocusInWindow();
        add(textField);

        add(regexpCheckBox);

        add(Box.createHorizontalGlue());

        statusLabel = new JLabel("");
        add(statusLabel);

        final JButton closeButton = new JButton(new AbstractAction() {
            public void actionPerformed(ActionEvent actionEvent) {
                TableRowFilterToolBar.this.parent.closeRequested();
            }
        });
        closeButton.setIcon(inspection.preference().style().codeViewCloseIcon());
        closeButton.setToolTipText("Close Filter");
        add(closeButton);
    }

    @Override
    public void refresh(boolean force) {
        tableRowMatcher.refresh();
        processTextInput();
    }

    /**
     * Causes the keyboard focus to be set to the text field.
     */
    public void getFocus() {
        textField.requestFocusInWindow();
    }

    private void processTextInput() {
        final String text = textField.getText();
        if (text.equals("")) {
            textField.setBackground(textFieldDefaultBackground);
            statusLabel.setText("");
            matchingRows = null;
            parent.setSearchResult(null);
        } else {
            Pattern pattern;
            try {
                if (regexpCheckBox.isSelected()) {
                    pattern = Pattern.compile(text, Pattern.CASE_INSENSITIVE);
                } else {
                    pattern = Pattern.compile(text, Pattern.CASE_INSENSITIVE + Pattern.LITERAL);
                }
            } catch (PatternSyntaxException patternSyntaxException) {
                textField.setBackground(preference().style().searchFailedBackground());
                statusLabel.setText("regexp error");
                matchingRows = null;
                parent.setSearchResult(matchingRows);
                return;
            }
            matchingRows = tableRowMatcher.findMatches(pattern);
            final int matchCount = matchingRows.length;
            statusLabel.setText(Integer.toString(matchCount) + "/" + tableRowMatcher.rowCount() + " rows");
            if (matchCount > 0) {
                textField.setBackground(preference().style().searchMatchedBackground());
            } else {
                textField.setBackground(preference().style().searchFailedBackground());
            }
            parent.setSearchResult(matchingRows);
        }
    }
}
