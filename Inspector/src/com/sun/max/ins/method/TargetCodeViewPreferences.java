/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.ins.method;

import com.sun.max.ins.*;
import com.sun.max.ins.gui.*;

/**
 * Persistent preferences for viewing disassembled target code in the VM.
 *
 * @author Michael Van De Vanter
  */
public class TargetCodeViewPreferences extends TableColumnVisibilityPreferences<TargetCodeColumnKind> {

    private static TargetCodeViewPreferences globalPreferences;

    public static TargetCodeViewPreferences globalPreferences(Inspection inspection) {
        if (globalPreferences == null) {
            globalPreferences = new TargetCodeViewPreferences(inspection);
        }
        return globalPreferences;
    }

    // Prefix for all persistent column preferences in view
    private static final String TARGET_CODE_COLUMN_PREFERENCE = "targetCodeViewColumn";

    /**
     * Creates the global, persistent set of preferences.
     */
    private TargetCodeViewPreferences(Inspection inspection) {
        super(inspection, TARGET_CODE_COLUMN_PREFERENCE, TargetCodeColumnKind.values());
        // There are no view preferences beyond the column choices, so no additional saving needed here.
    }

    /**
     * Creates a non-persistent set of preferences by cloning another set of preferences (i.e. the globally persistent set).
     */
    public TargetCodeViewPreferences(TableColumnVisibilityPreferences<TargetCodeColumnKind> otherPreferences) {
        super(otherPreferences);
    }
}
