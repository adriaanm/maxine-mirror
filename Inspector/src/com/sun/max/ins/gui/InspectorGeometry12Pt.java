/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
import java.util.*;

import com.sun.max.ins.view.InspectionViews.*;

/**
 * Standard choices and policies for inspection window layout, tiled for use with 12 pt. font.
 *
 * @author Michael Van De Vanter
 */
public final class InspectorGeometry12Pt implements InspectorGeometry {

    private final Map<ViewKind, Rectangle> preferredFrameGeometry = new HashMap<ViewKind, Rectangle>();

    // Main Inspection frame
    private static final Point inspectionFrameDefaultLocation = new Point(100, 100);
    private static final Dimension inspectionFrameMinSize = new Dimension(100, 100);
    private static final Dimension inspectionFramePrefSize = new Dimension(1615, 960);

    public InspectorGeometry12Pt() {
        preferredFrameGeometry.put(ViewKind.ALLOCATIONS, new Rectangle(100, 100, 450, 250));
        preferredFrameGeometry.put(ViewKind.BOOT_IMAGE, new Rectangle(100, 0, 390, 900));
        preferredFrameGeometry.put(ViewKind.BREAKPOINTS, new Rectangle(1150, 0, 450, 170));
        preferredFrameGeometry.put(ViewKind.FRAME, new Rectangle(225, 300, 225, 600));
        preferredFrameGeometry.put(ViewKind.METHODS, new Rectangle(450, 0, 700, 900));
        preferredFrameGeometry.put(ViewKind.NOTEPAD, new Rectangle(200, 200, 200, 200));
        preferredFrameGeometry.put(ViewKind.REGISTERS, new Rectangle(0, 170, 225, 730));
        preferredFrameGeometry.put(ViewKind.STACK, new Rectangle(225, 0, 225, 300));
        preferredFrameGeometry.put(ViewKind.THREADS, new Rectangle(0, 0, 225, 170));
        preferredFrameGeometry.put(ViewKind.THREAD_LOCALS, new Rectangle(1150, 170, 450, 730));
        preferredFrameGeometry.put(ViewKind.WATCHPOINTS, new Rectangle(100, 100, 575, 150));
    }

    public Point inspectorDefaultFrameLocation() {
        return inspectionFrameDefaultLocation;
    }
    public Dimension inspectorMinFrameSize() {
        return inspectionFrameMinSize;
    }
    public Dimension inspectorPrefFrameSize() {
        return inspectionFramePrefSize;
    }

    public Rectangle preferredFrameGeometry(ViewKind viewKind) {
        return preferredFrameGeometry.get(viewKind);
    }

    // Java Source Inspector frame
    private static final Point javaSourceFrameDefaultLocation = new Point(1270, 0);
    private static final Dimension javaSourceFramePrefSize = new Dimension(605, 400);

    public Point javaSourceDefaultFrameLocation() {
        return javaSourceFrameDefaultLocation;
    }
    public Dimension javaSourcePrefFrameSize() {
        return javaSourceFramePrefSize;
    }

    // Offset from mouse location for new frames
    public static final int newFrameDiagonalOffset = 5;
    public int newFrameDiagonalOffset() {
        return newFrameDiagonalOffset;
    }

}
