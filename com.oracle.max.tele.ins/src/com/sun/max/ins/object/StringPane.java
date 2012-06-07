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
package com.sun.max.ins.object;

import java.awt.*;

import javax.swing.*;

import com.sun.max.ins.*;
import com.sun.max.ins.gui.*;
import com.sun.max.tele.*;
import com.sun.max.tele.object.*;

/**
 * A factory class that creates scrollable pane components, each of which displays a string representation of some value in the VM.
 */
public final class StringPane extends InspectorScrollPane {

    public static StringPane createStringPane(ObjectView objectInspector, StringSource stringSource) {
        return new StringPane(objectInspector.inspection(), new JTextArea(), stringSource);
    }

    public interface StringSource {
        String fetchString();
        ObjectStatus status();
    }

    private final StringSource stringSource;
    private String stringValue;
    private final JTextArea textArea;

    private StringPane(Inspection inspection, JTextArea textArea, StringSource stringSource) {
        super(inspection, textArea);
        this.stringSource = stringSource;
        this.stringValue = stringSource.fetchString();
        this.textArea = textArea;
        this.textArea.append(stringValue);
        this.textArea.setEditable(false);
        refresh(true);
    }

    @Override
    public void redisplay() {
        textArea.setFont(preference().style().defaultFont());
    }

    private MaxVMState lastRefreshedState = null;

    @Override
    public void refresh(boolean force) {
        if (vm().state().newerThan(lastRefreshedState) || force) {
            lastRefreshedState = vm().state();
            String stringValue = "";
            Color background = null;
            switch(stringSource.status()) {
                case LIVE:
                    stringValue = stringSource.fetchString();
                    break;
                case DEAD:
                    background = preference().style().deadObjectBackgroundColor();
                    break;
            }
            textArea.selectAll();
            textArea.replaceSelection(stringValue);
            textArea.setBackground(background);
        }
    }

}
