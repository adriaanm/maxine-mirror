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
package com.sun.max.tele;


/**
 * An object that refers to a resource in the VM, and which reads
 * and caches some related state from the VM.
 */
public interface TeleVMCache {

    /**
     * Causes this object to refresh any state that is read and cached from the VM, must
     * be called in a thread holding the VM lock.
     * <p>
     * Caches can be tagged with the process epoch, the number of times the process has run,
     * on the assumption that the contents of VM memory will not change without
     * an increment of the epoch counter.
     *
     * @param epoch the number of times the VM process has run so far.
     * @see TeleVM#lock()
     */
    void updateCache(long epoch);

}
