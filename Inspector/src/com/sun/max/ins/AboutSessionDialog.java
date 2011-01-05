/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.max.ins;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;

import javax.swing.*;
import javax.swing.border.*;

import com.sun.max.ins.gui.*;

/**
 * @author Michael Van De Vanter
 *
 */
public final class AboutSessionDialog extends InspectorDialog {

    private static final String INDENT = "    ";
    private static final int indent = INDENT.length();


    private static final Border border = BorderFactory.createLineBorder(Color.black);


    private final JScrollPane scrollPane;
    private final JTextArea textArea;
    private final JRadioButton verboseRadioButton;

    public AboutSessionDialog(final Inspection inspection) {
        super(inspection, MaxineInspector.NAME + " session information", true);


        this.textArea = new JTextArea(17, 50);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);


        this.verboseRadioButton = new JRadioButton("Verbose");
        verboseRadioButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                refresh();
            }
        });

        final JPanel buttonsPanel = new InspectorPanel(inspection);
        buttonsPanel.add(verboseRadioButton);
        buttonsPanel.add(new InspectorButton(inspection, new AbstractAction("Close") {
            public void actionPerformed(ActionEvent e) {
                inspection.settings().save();
                dispose();
            }
        }));

        scrollPane = new InspectorScrollPane(inspection, textArea);
        scrollPane.setBorder(border);

        final JPanel dialogPanel = new InspectorPanel(inspection, new BorderLayout());
        dialogPanel.add(scrollPane, BorderLayout.CENTER);
        dialogPanel.add(buttonsPanel, BorderLayout.SOUTH);
        setContentPane(dialogPanel);

        refresh();
        pack();
        inspection.gui().setLocationRelativeToMouse(this, 5);
        setVisible(true);
    }

    private void refresh() {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final PrintStream stream = new PrintStream(byteArrayOutputStream);
        final long lastModified = vm().bootImageFile().lastModified();
        final Date bootImageDate = lastModified == 0 ? null : new Date(lastModified);
        final boolean verbose = verboseRadioButton.isSelected();
        if (verbose) {
            stream.print(MaxineInspector.NAME + " Ver. " + MaxineInspector.VERSION + "\n");
            stream.print(INDENT + "Mode: " + vm().inspectionMode().name() + ",  " + vm().inspectionMode().description() + "\n");
            stream.print("\nVM:\n");
            stream.print(INDENT + vm().getDescription() + "\n");
            stream.print(INDENT + "Boot image: " + vm().bootImageFile().getAbsolutePath().toString() + "\n");
            stream.print(INDENT + "Last modified: " + bootImageDate.toString() + "\n");
            stream.print(INDENT + "See also: View->Boot image info\n");
        } else {
            stream.print(MaxineInspector.NAME + " Ver. " + MaxineInspector.VERSION + " mode=" + vm().inspectionMode().name() + "\n");
            stream.print("\nVM:\n");
            stream.print(INDENT + vm().entityName() + " Ver. " + vm().getVersion() + "\n");
            stream.print(INDENT + vm().bootImageFile().getAbsolutePath().toString() + "\n");
            stream.print(INDENT + bootImageDate.toString() + "\n");
        }
        stream.print("\nSESSION OPTIONS: \n");
        inspection().options().printValues(stream, indent, verbose);
        stream.print("\nHEAP:\n");
        vm().heap().printStats(stream, indent, verbose);
        textArea.setText(byteArrayOutputStream.toString());
        textArea.setCaretPosition(0);
    }

}
