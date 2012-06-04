/*
 * Copyright (c) 2004, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.max.vma.tools.qa.queries;

import java.io.*;

import com.oracle.max.vma.tools.qa.*;
import com.oracle.max.vma.tools.qa.TransientVMAdviceHandlerTypes.*;

/**
 * Helper class for queries that report by object instance.
 */

public class DataByObjectQueryHelper extends QueryBase {

    static void showDataOnTD(TraceRun traceRun, ObjectRecord td,
            PrintStream ps, String[] args) {

        boolean showThread = false;
        boolean showAllAccesses = false;

        for (String arg : args) {
            if (arg.equals("-accesses")) {
                showAllAccesses = true;
            } else if (arg.equals("-showthread")) {
                showThread = true;
            }
        }
        ps.println(td.toString(traceRun, true, showThread, true, true, true, true));

        for (int i = 0; i < td.getAdviceRecords().size(); i++) {
            AdviceRecord ar =  td.getAdviceRecords().get(i);
            if (showAllAccesses) {
                ps.println("  field " + getQualName(ar) + " accessed (" + AdviceRecordHelper.accessType(ar) + ") at "
                        + ms(traceRun.relTime(ar.time)) + " in thread " + ar.thread);
            }
        }

    }

    private static String getQualName(AdviceRecord ar) {
        ObjectFieldAdviceRecord far = (ObjectFieldAdviceRecord) ar;
        ObjectRecord or = (ObjectRecord) far.value;
        FieldRecord fr = (FieldRecord) far.field;
        return or.getClassName() + "." + fr.getName();
    }


}
