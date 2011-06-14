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
package com.sun.max.tele.debug.solaris;

import java.io.*;

import com.sun.max.platform.*;
import com.sun.max.tele.*;
import com.sun.max.tele.debug.*;
import com.sun.max.tele.debug.TeleNativeThread.Params;
import com.sun.max.tele.debug.unix.*;
import com.sun.max.vm.hosted.*;

/**
 */
public final class SolarisTeleProcess extends UnixTeleProcessAdaptor {

    /**
     * Creates a handle to a native Solaris Maxine VM process by launching a new process with a given set of command line arguments.
     *
     * @param teleVM
     * @param platform
     * @param programFile
     * @param commandLineArguments
     * @throws BootImageException
     */
    SolarisTeleProcess(TeleVM teleVM, Platform platform, File programFile, String[] commandLineArguments) throws BootImageException {
        super(teleVM, platform, programFile, commandLineArguments);
    }

    /**
     * Attach to an existing native Solaris Maxine VM process, or a core dump.
     * @param teleVM
     * @param platform
     * @param programFile
     * @param id
     * @throws BootImageException
     */
    SolarisTeleProcess(TeleVM teleVM, Platform platform, File programFile, int id) throws BootImageException {
        super(teleVM, platform, programFile, id);
    }

    @Override
    protected TeleNativeThread createTeleNativeThread(Params params) {
        return new SolarisTeleNativeThread(this, params);
    }

    @Override
    public int platformWatchpointCount() {
        // not sure, try max
        return Integer.MAX_VALUE;
    }


}
