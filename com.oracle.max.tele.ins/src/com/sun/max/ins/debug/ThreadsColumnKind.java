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
package com.sun.max.ins.debug;

import com.sun.max.tele.*;


/**
 * Defines the columns that can be displayed describing a thread in the VM.
 */
public enum ThreadsColumnKind implements ColumnKind {

    ID("ID", "ID assigned by VM, none if native", true, 15),
    HANDLE("Handle", "Thread handle assigned by the remote native thread library", true, 15),
    LOCAL_HANDLE("Local Handle", "Thread handle assigned by the process control API used by the Inspector", false, 15),
    KIND("Kind", "Either a Java thread or some other kind", true, -1),
    NAME("Name", "Name assigned to the thread", true, -1) {
        @Override
        public boolean canBeMadeInvisible() {
            return false;
        }
    },
    STATUS("Status", "Current thread status, e.g. Suspended, Breakpoint, Watchpoint", true, -1);

    private final String label;
    private final String toolTipText;
    private final boolean defaultVisibility;
    private final int minWidth;

    private ThreadsColumnKind(String label, String toolTipText, boolean defaultVisibility, int minWidth) {
        this.label = label;
        this.toolTipText = toolTipText;
        this.defaultVisibility = defaultVisibility;
        this.minWidth = minWidth;
        assert defaultVisibility || canBeMadeInvisible();
    }

    public boolean isSupported(MaxVM vm) {
        return true;
    }

    public String label() {
        return label;
    }

    public String toolTipText() {
        return toolTipText;
    }

    public int minWidth() {
        return minWidth;
    }

    @Override
    public String toString() {
        return label;
    }

    public boolean canBeMadeInvisible() {
        return true;
    }

    public boolean defaultVisibility() {
        return defaultVisibility;
    }

}
