/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.tele;

import com.sun.max.tele.data.*;
import com.sun.max.tele.object.*;
import com.sun.max.tele.reference.*;
import com.sun.max.vm.thread.*;

/**
 * Canonical surrogate for a {@link VmThread} in the {@link TeleVM}.
 * The name of a thread can be changed dynamically in the {@link TeleVM}.
 */
public class TeleVmThread extends TeleTupleObject {

    // the most recent state when we checked the name reference
    private long lastRefreshedEpoch = 0;

    /**
     * Reference to the string object in the VM holding the thread name.
     */
    private RemoteReference nameReference = vm().referenceManager().zeroReference();

    /**
     * Copy of the string representing the name of the thread the last time we checked the VM.
     */
    private String name = "*unset*";

    public TeleVmThread(TeleVM vm, RemoteReference vmThreadReference) {
        super(vm, vmThreadReference);
    }

    public String name() {
        if (vm().teleProcess().epoch() > lastRefreshedEpoch) {
            try {
                final RemoteReference nameReference = fields().VmThread_name.readRemoteReference(reference());
                if (!nameReference.equals(this.nameReference)) {
                    this.nameReference = nameReference;
                    if (this.nameReference.isZero()) {
                        name = "*unset*";
                    } else {
                        // Assume strings in the VM don't change, so we don't need to re-read
                        // if we've already seen the string (depends on canonical references).
                        try {
                            name = vm().getString(this.nameReference);
                        } catch (InvalidReferenceException invalidReferenceExceptoin) {
                            name = "?";
                        }
                    }
                }
                lastRefreshedEpoch = vm().teleProcess().epoch();
            } catch (DataIOError dataIOError) {
                name = "?";
            }
        }
        return name;
    }

    /**
     * Returns the native thread associated with this Java thread.
     * <br>
     * Thread safe.
     * @return the thread associated with this Java thread.
     */
    public MaxThread maxThread() {
        for (MaxThread maxThread : vm().state().threads()) {
            if (maxThread.teleVmThread() == this) {
                return maxThread;
            }
        }
        return null;
    }

}
