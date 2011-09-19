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
package com.sun.max.vm.stack;

import com.oracle.max.cri.intrinsics.*;
import com.sun.cri.ci.*;
import com.sun.max.vm.collect.*;

/**
 * Describes the layout of a frame produced by the optimizing compiler.
 */
public class OptoStackFrameLayout extends VMFrameLayout {

    private final int frameSize;
    private final boolean isReturnAddressPushedByCall;
    private final CiRegister fp;

    public OptoStackFrameLayout(int frameSize, boolean isReturnAddressPushedByCall, CiRegister fp) {
        this.frameSize = frameSize;
        this.isReturnAddressPushedByCall = isReturnAddressPushedByCall;
        this.fp = fp;
    }

    @Override
    public int frameSize() {
        return frameSize;
    }

    @Override
    public CiRegister framePointerReg() {
        return fp;
    }

    @Override
    public boolean isReturnAddressPushedByCall() {
        return isReturnAddressPushedByCall;
    }

    @Override
    public int frameReferenceMapOffset() {
        return 0;
    }

    @Override
    public int frameReferenceMapSize() {
        return ByteArrayBitMap.computeBitMapSize(UnsignedMath.divide(frameSize(), STACK_SLOT_SIZE));
    }
}
