/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.vm.ext.vma.runtime;

import com.oracle.max.vm.ext.vma.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.layout.*;
import com.sun.max.vm.object.*;
import com.sun.max.vm.reference.*;

/**
 * An adaptor class that handles the state (id, liveness) management for advice handlers.
 *
 */

public abstract class ObjectStateHandlerAdaptor extends VMAdviceHandler {

    protected ObjectStateHandler.RemovalTracker removalTracker;

    protected void setRemovalTracker(ObjectStateHandler.RemovalTracker removalTracker) {
        this.removalTracker = removalTracker;
    }

    /**
     * Notify our specific subclass that a previously unseen object has been observed.
     * @param obj
     */
    protected abstract void handleUnseen(Object obj);

    /**
     * Ensure that {@code obj} has a valid unique id.
     * @param obj
     * @return
     */
    private void checkId(Object obj) {
        if (obj != null) {
            long id = state.readId(obj);
            if (id == 0) {
                state.assignUnseenId(obj);
                // check the classloader also
                final Reference objRef = Reference.fromJava(obj);
                final Hub hub = UnsafeCast.asHub(Layout.readHubReference(objRef));
                checkId(hub.classActor.classLoader);
                handleUnseen(obj);
            }
        }
    }

    private void checkClassLoaderId(Object staticTuple) {
        checkId(ObjectAccess.readClassActor(staticTuple).classLoader);
    }


    @Override
    public void adviseAfterGC() {
        // generate log records for objects that didn't survive this GC
        state.gc(removalTracker);
    }

    @Override
    public void gcSurvivor(Pointer cell) {
        state.incrementLifetime(cell);
    }

    // BEGIN GENERATED CODE

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseAfterNew(Object arg1) {
        final Reference objRef = Reference.fromJava(arg1);
        final Hub hub = UnsafeCast.asHub(Layout.readHubReference(objRef));
        state.assignId(objRef);
        checkId(hub.classActor.classLoader);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseAfterNewArray(Object arg1, int arg2) {
        final Reference objRef = Reference.fromJava(arg1);
        final Hub hub = UnsafeCast.asHub(Layout.readHubReference(objRef));
        state.assignId(objRef);
        checkId(hub.classActor.classLoader);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeConstLoad(float arg1) {
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeConstLoad(double arg1) {
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeConstLoad(Object arg1) {
        checkId(arg1);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeConstLoad(long arg1) {
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeIPush(int arg1) {
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeLoad(int arg1) {
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeArrayLoad(Object arg1, int arg2) {
        checkId(arg1);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeStore(int arg1, double arg2) {
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeStore(int arg1, Object arg2) {
        checkId(arg2);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeStore(int arg1, float arg2) {
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeStore(int arg1, long arg2) {
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeArrayStore(Object arg1, int arg2, double arg3) {
        checkId(arg1);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeArrayStore(Object arg1, int arg2, Object arg3) {
        checkId(arg1);
        checkId(arg3);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeArrayStore(Object arg1, int arg2, long arg3) {
        checkId(arg1);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeArrayStore(Object arg1, int arg2, float arg3) {
        checkId(arg1);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeStackAdjust(int arg1) {
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeOperation(int arg1, double arg2, double arg3) {
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeOperation(int arg1, float arg2, float arg3) {
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeOperation(int arg1, long arg2, long arg3) {
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeIInc(int arg1, int arg2, int arg3) {
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeConversion(int arg1, float arg2) {
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeConversion(int arg1, double arg2) {
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeConversion(int arg1, long arg2) {
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeIf(int arg1, int arg2, int arg3) {
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeIf(int arg1, Object arg2, Object arg3) {
        checkId(arg2);
        checkId(arg3);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeReturn(Object arg1) {
        checkId(arg1);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeReturn(long arg1) {
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeReturn(float arg1) {
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeReturn(double arg1) {
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeReturn() {
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeGetStatic(Object arg1, int arg2) {
        checkClassLoaderId(arg1);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforePutStatic(Object arg1, int arg2, float arg3) {
        checkClassLoaderId(arg1);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforePutStatic(Object arg1, int arg2, Object arg3) {
        checkClassLoaderId(arg1);
        checkId(arg3);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforePutStatic(Object arg1, int arg2, double arg3) {
        checkClassLoaderId(arg1);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforePutStatic(Object arg1, int arg2, long arg3) {
        checkClassLoaderId(arg1);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeGetField(Object arg1, int arg2) {
        checkId(arg1);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforePutField(Object arg1, int arg2, float arg3) {
        checkId(arg1);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforePutField(Object arg1, int arg2, Object arg3) {
        checkId(arg1);
        checkId(arg3);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforePutField(Object arg1, int arg2, long arg3) {
        checkId(arg1);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforePutField(Object arg1, int arg2, double arg3) {
        checkId(arg1);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeInvokeVirtual(Object arg1, MethodActor arg2) {
        checkId(arg1);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeInvokeSpecial(Object arg1, MethodActor arg2) {
        checkId(arg1);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeInvokeStatic(Object arg1, MethodActor arg2) {
        checkId(arg1);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeInvokeInterface(Object arg1, MethodActor arg2) {
        checkId(arg1);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeArrayLength(Object arg1, int arg2) {
        checkId(arg1);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeThrow(Object arg1) {
        checkId(arg1);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeCheckCast(Object arg1, Object arg2) {
        checkId(arg1);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeInstanceOf(Object arg1, Object arg2) {
        checkId(arg1);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeMonitorEnter(Object arg1) {
        checkId(arg1);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeMonitorExit(Object arg1) {
        checkId(arg1);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseBeforeBytecode(int arg1) {
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseAfterInvokeVirtual(Object arg1, MethodActor arg2) {
        checkId(arg1);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseAfterInvokeSpecial(Object arg1, MethodActor arg2) {
        checkId(arg1);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseAfterInvokeStatic(Object arg1, MethodActor arg2) {
        checkId(arg1);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseAfterInvokeInterface(Object arg1, MethodActor arg2) {
        checkId(arg1);
    }

    // GENERATED -- EDIT AND RUN ObjectStateHandlerAdaptorGenerator.main() TO MODIFY
    @Override
    public void adviseAfterMultiNewArray(Object arg1, int[] arg2) {
        checkId(arg1);
    }



}
