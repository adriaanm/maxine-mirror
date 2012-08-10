/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.vm.ext.vma.handlers.util;

import com.oracle.max.vm.ext.vma.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.thread.*;

/**
 * The null implementation. Useful as an adaptor for handlers that only want to handle a subset
 * of the advice calls.
 *
 * N.B. This is <b>not</b> included in the boot image unless a handler that
 * subclasses it is also included. This prevents a deopt cascade when a
 * handler is dynamically loaded (because if it is included then all the
 * methods below provoke the unique concrete method optimization).
 *
 */
public class NullVMAdviceHandler extends VMAdviceHandler {

    @Override
    public void gcSurvivor(Pointer cell) {
    }

// START GENERATED CODE
// EDIT AND RUN NullVMAdviceHandlerGenerator.main() TO MODIFY

    @Override
    public void adviseBeforeGC() {
    }

    @Override
    public void adviseAfterGC() {
    }

    @Override
    public void adviseBeforeThreadStarting(VmThread arg1) {
    }

    @Override
    public void adviseBeforeThreadTerminating(VmThread arg1) {
    }

    @Override
    public void adviseBeforeReturnByThrow(Throwable arg1, int arg2) {
    }

    @Override
    public void adviseBeforeConstLoad(long arg1) {
    }

    @Override
    public void adviseBeforeConstLoad(Object arg1) {
    }

    @Override
    public void adviseBeforeConstLoad(float arg1) {
    }

    @Override
    public void adviseBeforeConstLoad(double arg1) {
    }

    @Override
    public void adviseBeforeLoad(int arg1) {
    }

    @Override
    public void adviseBeforeArrayLoad(Object arg1, int arg2) {
    }

    @Override
    public void adviseBeforeStore(int arg1, long arg2) {
    }

    @Override
    public void adviseBeforeStore(int arg1, float arg2) {
    }

    @Override
    public void adviseBeforeStore(int arg1, double arg2) {
    }

    @Override
    public void adviseBeforeStore(int arg1, Object arg2) {
    }

    @Override
    public void adviseBeforeArrayStore(Object arg1, int arg2, float arg3) {
    }

    @Override
    public void adviseBeforeArrayStore(Object arg1, int arg2, long arg3) {
    }

    @Override
    public void adviseBeforeArrayStore(Object arg1, int arg2, double arg3) {
    }

    @Override
    public void adviseBeforeArrayStore(Object arg1, int arg2, Object arg3) {
    }

    @Override
    public void adviseBeforeStackAdjust(int arg1) {
    }

    @Override
    public void adviseBeforeOperation(int arg1, long arg2, long arg3) {
    }

    @Override
    public void adviseBeforeOperation(int arg1, float arg2, float arg3) {
    }

    @Override
    public void adviseBeforeOperation(int arg1, double arg2, double arg3) {
    }

    @Override
    public void adviseBeforeConversion(int arg1, float arg2) {
    }

    @Override
    public void adviseBeforeConversion(int arg1, long arg2) {
    }

    @Override
    public void adviseBeforeConversion(int arg1, double arg2) {
    }

    @Override
    public void adviseBeforeIf(int arg1, int arg2, int arg3) {
    }

    @Override
    public void adviseBeforeIf(int arg1, Object arg2, Object arg3) {
    }

    @Override
    public void adviseBeforeBytecode(int arg1) {
    }

    @Override
    public void adviseBeforeReturn() {
    }

    @Override
    public void adviseBeforeReturn(long arg1) {
    }

    @Override
    public void adviseBeforeReturn(float arg1) {
    }

    @Override
    public void adviseBeforeReturn(double arg1) {
    }

    @Override
    public void adviseBeforeReturn(Object arg1) {
    }

    @Override
    public void adviseBeforeGetStatic(Object arg1, int arg2) {
    }

    @Override
    public void adviseBeforePutStatic(Object arg1, int arg2, Object arg3) {
    }

    @Override
    public void adviseBeforePutStatic(Object arg1, int arg2, float arg3) {
    }

    @Override
    public void adviseBeforePutStatic(Object arg1, int arg2, double arg3) {
    }

    @Override
    public void adviseBeforePutStatic(Object arg1, int arg2, long arg3) {
    }

    @Override
    public void adviseBeforeGetField(Object arg1, int arg2) {
    }

    @Override
    public void adviseBeforePutField(Object arg1, int arg2, Object arg3) {
    }

    @Override
    public void adviseBeforePutField(Object arg1, int arg2, float arg3) {
    }

    @Override
    public void adviseBeforePutField(Object arg1, int arg2, double arg3) {
    }

    @Override
    public void adviseBeforePutField(Object arg1, int arg2, long arg3) {
    }

    @Override
    public void adviseBeforeInvokeVirtual(Object arg1, MethodActor arg2) {
    }

    @Override
    public void adviseBeforeInvokeSpecial(Object arg1, MethodActor arg2) {
    }

    @Override
    public void adviseBeforeInvokeStatic(Object arg1, MethodActor arg2) {
    }

    @Override
    public void adviseBeforeInvokeInterface(Object arg1, MethodActor arg2) {
    }

    @Override
    public void adviseBeforeArrayLength(Object arg1, int arg2) {
    }

    @Override
    public void adviseBeforeThrow(Object arg1) {
    }

    @Override
    public void adviseBeforeCheckCast(Object arg1, Object arg2) {
    }

    @Override
    public void adviseBeforeInstanceOf(Object arg1, Object arg2) {
    }

    @Override
    public void adviseBeforeMonitorEnter(Object arg1) {
    }

    @Override
    public void adviseBeforeMonitorExit(Object arg1) {
    }

    @Override
    public void adviseAfterInvokeVirtual(Object arg1, MethodActor arg2) {
    }

    @Override
    public void adviseAfterInvokeSpecial(Object arg1, MethodActor arg2) {
    }

    @Override
    public void adviseAfterInvokeStatic(Object arg1, MethodActor arg2) {
    }

    @Override
    public void adviseAfterInvokeInterface(Object arg1, MethodActor arg2) {
    }

    @Override
    public void adviseAfterNew(Object arg1) {
    }

    @Override
    public void adviseAfterNewArray(Object arg1, int arg2) {
    }

    @Override
    public void adviseAfterMultiNewArray(Object arg1, int[] arg2) {
    }

    @Override
    public void adviseAfterMethodEntry(Object arg1, MethodActor arg2) {
    }

// END GENERATED CODE
}
