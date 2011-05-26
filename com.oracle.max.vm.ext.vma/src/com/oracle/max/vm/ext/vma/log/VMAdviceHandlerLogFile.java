/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.vm.ext.vma.log;

/**
 * Specification of the file to be used for a persistent log.
 * The default file name is {@value DEFAULT_LOGFILE} but this
 * can be changed using the {@value LOGFILE_PROPERTY} system property.
 *
 * @author Mick Jordan
 *
 */
public class VMAdviceHandlerLogFile {
    public static final String LOGFILE_PROPERTY = "max.vma.logfile";
    public static final String DEFAULT_LOGFILE = "default.vma";

    public static String getLogFile() {
        String logFile = System.getProperty(LOGFILE_PROPERTY);
        if (logFile == null) {
            logFile = DEFAULT_LOGFILE;
        }
        return logFile;
    }
}
