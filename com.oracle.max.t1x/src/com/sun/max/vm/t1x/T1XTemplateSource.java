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
package com.sun.max.vm.t1x;

import static com.sun.cri.bytecode.Bytecodes.MemoryBarriers.*;
import static com.sun.max.vm.t1x.T1XFrameOps.*;
import static com.sun.max.vm.t1x.T1XRuntime.*;
import static com.sun.max.vm.t1x.T1XTemplateTag.*;


import com.sun.cri.bytecode.*;
import com.sun.max.annotate.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.heap.*;
import com.sun.max.vm.monitor.*;
import com.sun.max.vm.object.*;
import com.sun.max.vm.profile.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.type.*;

/**
 * The Java source for the templates used by T1X.
 *
 * The templates are almost all automatically generated as many bytecodes fall in groups that share a very similar implementation.
 * Auto-generation also allows for easy and optional customization, in particular, tracking of bytecode execution.
 * The automatically generated code is created by running {@link T1XTemplateGenerator#main} and is inserted (manually) at the end of the class.
 */
public class T1XTemplateSource {

    @INTRINSIC(Bytecodes.LCMP)
    public static native int lcmp(long l, long r);
    @INTRINSIC(Bytecodes.FCMPG)
    public static native int fcmpg(float l, float r);
    @INTRINSIC(Bytecodes.FCMPL)
    public static native int fcmpl(float l, float r);
    @INTRINSIC(Bytecodes.DCMPG)
    public static native int dcmpg(double l, double r);
    @INTRINSIC(Bytecodes.DCMPL)
    public static native int dcmpl(double l, double r);

    @T1X_TEMPLATE(COUNT_BYTECODE)
    public static void countBytecode(int opcode, long[] array) {
        // Disable bounds-checking by use of ArrayAccess
        ArrayAccess.setLong(array, opcode, ArrayAccess.getLong(array, opcode) + 1);
    }

    @T1X_TEMPLATE(LOAD_EXCEPTION)
    public static void loadException() {
        pushObject(T1XRuntime.loadException());
    }

    @T1X_TEMPLATE(RETHROW_EXCEPTION)
    public static void rethrowException() {
        T1XRuntime.rethrowException();
    }

    @T1X_TEMPLATE(PROFILE_NONSTATIC_METHOD_ENTRY)
    public static void profileNonstaticMethodEntry(MethodProfile mpo, int dispToRcvr) {
        Object rcvr = getLocalObject(dispToRcvr);
        // entrypoint counters count down to zero ("overflow")
        MethodInstrumentation.recordEntrypoint(mpo, rcvr);
    }

    @T1X_TEMPLATE(PROFILE_STATIC_METHOD_ENTRY)
    public static void profileStaticMethodEntry(MethodProfile mpo) {
        // entrypoint counters count down to zero ("overflow")
        MethodInstrumentation.recordEntrypoint(mpo, null);
    }

    @T1X_TEMPLATE(TRACE_METHOD_ENTRY)
    public static void traceMethodEntry(String method) {
        Log.println(method);
    }

    @T1X_TEMPLATE(LOCK_RECEIVER)
    public static void lockReceiver(int dispToRcvr, int dispToRcvrCopy) {
        Object rcvr = getLocalObject(dispToRcvr);
        T1XRuntime.monitorenter(rcvr);
        setLocalObject(dispToRcvrCopy, rcvr);
    }

    @T1X_TEMPLATE(UNLOCK_RECEIVER)
    public static void unlockReceiver(int dispToRcvrCopy) {
        Object rcvr = getLocalObject(dispToRcvrCopy);
        T1XRuntime.monitorexit(rcvr);
    }

    @T1X_TEMPLATE(LOCK_CLASS)
    public static void lockClass(Class c) {
        T1XRuntime.monitorenter(c);
    }

    @T1X_TEMPLATE(UNLOCK_CLASS)
    public static void unlockClass(Class c) {
        T1XRuntime.monitorexit(c);
    }

    @T1X_TEMPLATE(DUP)
    public static void dup() {
        pushWord(peekWord(0));
    }

    @T1X_TEMPLATE(DUP_X1)
    public static void dup_x1() {
        Word value1 = peekWord(0);
        Word value2 = peekWord(1);
        pokeWord(1, value1);
        pokeWord(0, value2);
        pushWord(value1);
    }

    @T1X_TEMPLATE(DUP_X2)
    public static void dup_x2() {
        Word value1 = peekWord(0);
        Word value2 = peekWord(1);
        Word value3 = peekWord(2);
        pushWord(value1);
        pokeWord(1, value2);
        pokeWord(2, value3);
        pokeWord(3, value1);
    }

    @T1X_TEMPLATE(DUP2)
    public static void dup2() {
        Word value1 = peekWord(0);
        Word value2 = peekWord(1);
        pushWord(value2);
        pushWord(value1);
    }

    @T1X_TEMPLATE(DUP2_X1)
    public static void dup2_x1() {
        Word value1 = peekWord(0);
        Word value2 = peekWord(1);
        Word value3 = peekWord(2);
        pokeWord(2, value2);
        pokeWord(1, value1);
        pokeWord(0, value3);
        pushWord(value2);
        pushWord(value1);
    }

    @T1X_TEMPLATE(DUP2_X2)
    public static void dup2_x2() {
        Word value1 = peekWord(0);
        Word value2 = peekWord(1);
        Word value3 = peekWord(2);
        Word value4 = peekWord(3);
        pokeWord(3, value2);
        pokeWord(2, value1);
        pokeWord(1, value4);
        pokeWord(0, value3);
        pushWord(value2);
        pushWord(value1);
    }

    @T1X_TEMPLATE(NOP)
    public static void nop() {
        // do nothing.
    }

    @T1X_TEMPLATE(SWAP)
    public static void swap() {
        Word value0 = peekWord(0);
        Word value1 = peekWord(1);
        pokeWord(0, value1);
        pokeWord(1, value0);
    }


    @INLINE
    public static void icmp0_prefix() {
        Intrinsics.compareInts(popInt(), 0);
    }

    @INLINE
    public static void acmp0_prefix() {
        Object value = peekObject(0);
        removeSlots(1);
        Intrinsics.compareWords(toWord(value), Address.zero());
    }

    @INLINE
    public static void icmp_prefix() {
        int value2 = peekInt(0);
        int value1 = peekInt(1);
        removeSlots(2);
        Intrinsics.compareInts(value1, value2);
    }

    @INTRINSIC(Bytecodes.UNSAFE_CAST)
    private static native Word toWord(Object object);

    @INLINE
    public static void acmp_prefix() {
        Object value2 = peekObject(0);
        Object value1 = peekObject(1);
        removeSlots(2);
        Intrinsics.compareWords(toWord(value1), toWord(value2));
    }

    @INLINE
    public static void nullCheck(Pointer receiver) {
        receiver.readWord(0);
    }

    @INLINE
    public static Address selectVirtualMethod(Object receiver, int vTableIndex, MethodProfile mpo, int mpoIndex) {
        Hub hub = ObjectAccess.readHub(receiver);
        Address entryPoint = hub.getWord(vTableIndex).asAddress();
        MethodInstrumentation.recordType(mpo, hub, mpoIndex, MethodInstrumentation.DEFAULT_RECEIVER_METHOD_PROFILE_ENTRIES);
        return entryPoint;
    }

    @T1X_TEMPLATE(LSB)
    public static void lsb() {
        Word value2 = peekWord(0);
        pokeInt(0, value2.leastSignificantBitSet());
    }

    @T1X_TEMPLATE(MSB)
    public static void msb() {
        Word value2 = peekWord(0);
        pokeInt(0, value2.mostSignificantBitSet());
    }

    @T1X_TEMPLATE(MEMBAR_LOAD_LOAD)
    public static void membar_load_load() {
        loadLoad();
    }

    @T1X_TEMPLATE(MEMBAR_LOAD_STORE)
    public static void membar_load_store() {
        loadStore();
    }

    @T1X_TEMPLATE(MEMBAR_STORE_STORE)
    public static void membar_store_store() {
        loadStore();
    }

    @T1X_TEMPLATE(MEMBAR_STORE_LOAD)
    public static void membar_store_load() {
        storeLoad();
    }

    @T1X_TEMPLATE(PAUSE)
    public static void pause() {
        Intrinsics.pause();
    }

    @T1X_TEMPLATE(READREG$fp_cpu)
    public static void readreg_fp_cpu() {
        pushWord(VMRegister.getCpuFramePointer());
    }

    @T1X_TEMPLATE(READREG$sp_cpu)
    public static void readreg_sp_cpu() {
        pushWord(VMRegister.getCpuStackPointer());
    }

    @T1X_TEMPLATE(READREG$fp_abi)
    public static void readreg_fp_abi() {
        pushWord(VMRegister.getAbiFramePointer());
    }

    @T1X_TEMPLATE(READREG$sp_abi)
    public static void readreg_sp_abi() {
        pushWord(VMRegister.getAbiStackPointer());
    }

    @T1X_TEMPLATE(READREG$latch)
    public static void readreg_latch() {
        pushWord(VMRegister.getSafepointLatchRegister());
    }

    @T1X_TEMPLATE(WRITEREG$fp_cpu)
    public static void writereg_fp_cpu() {
        Word value = peekWord(0);
        removeSlots(1);
        VMRegister.setCpuFramePointer(value);
    }

    @T1X_TEMPLATE(WRITEREG$sp_cpu)
    public static void writereg_sp_cpu() {
        Word value = peekWord(0);
        removeSlots(1);
        VMRegister.setCpuStackPointer(value);
    }

    @T1X_TEMPLATE(WRITEREG$fp_abi)
    public static void writereg_fp_abi() {
        Word value = peekWord(0);
        removeSlots(1);
        VMRegister.setAbiFramePointer(value);
    }

    @T1X_TEMPLATE(WRITEREG$sp_abi)
    public static void writereg_sp_abi() {
        Word value = peekWord(0);
        removeSlots(1);
        VMRegister.setAbiStackPointer(value);
    }

    @T1X_TEMPLATE(WRITEREG$latch)
    public static void writereg_latch() {
        Word value = peekWord(0);
        removeSlots(1);
        VMRegister.setSafepointLatchRegister(value);
    }

    @PLATFORM(cpu = "sparc")
    @T1X_TEMPLATE(WRITEREG$link)
    public static void writereg_link() {
        Word value = peekWord(0);
        removeSlots(1);
        VMRegister.setCallAddressRegister(value);
    }


    // BEGIN GENERATED CODE

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETFIELD$boolean$resolved)
    public static void getfieldBoolean(int offset) {
        Object object = peekObject(0);
        pokeBoolean(0, TupleAccess.readBoolean(object, offset));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETFIELD$boolean)
    public static void getfieldBoolean(ResolutionGuard.InPool guard) {
        Object object = peekObject(0);
        pokeBoolean(0, resolveAndGetFieldBoolean(guard, object));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE    public static boolean resolveAndGetFieldBoolean(ResolutionGuard.InPool guard, Object object) {
        FieldActor f = Snippets.resolveInstanceFieldForReading(guard);
        if (f.isVolatile()) {
            preVolatileRead();
            boolean value = TupleAccess.readBoolean(object, f.offset());
            postVolatileRead();
            return value;
        } else {
            return TupleAccess.readBoolean(object, f.offset());
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETSTATIC$boolean)
    public static void getstaticBoolean(ResolutionGuard.InPool guard) {
        pushBoolean(resolveAndGetStaticBoolean(guard));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE    public static boolean resolveAndGetStaticBoolean(ResolutionGuard.InPool guard) {
        FieldActor f = Snippets.resolveStaticFieldForReading(guard);
        Snippets.makeHolderInitialized(f);
        if (f.isVolatile()) {
            preVolatileRead();
            boolean value = TupleAccess.readBoolean(f.holder().staticTuple(), f.offset());
            postVolatileRead();
            return value;
        } else {
            return TupleAccess.readBoolean(f.holder().staticTuple(), f.offset());
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETSTATIC$boolean$init)
    public static void getstaticBoolean(Object staticTuple, int offset) {
        pushBoolean(TupleAccess.readBoolean(staticTuple, offset));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTFIELD$boolean$resolved)
    public static void putfieldBoolean(int offset) {
        Object object = peekObject(1);
        boolean value = peekBoolean(0);
        removeSlots(2);
        TupleAccess.writeBoolean(object, offset, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTFIELD$boolean)
    public static void putfieldBoolean(ResolutionGuard.InPool guard) {
        Object object = peekObject(1);
        boolean value = peekBoolean(0);
        resolveAndPutFieldBoolean(guard, object, value);
        removeSlots(2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE
    public static void resolveAndPutFieldBoolean(ResolutionGuard.InPool guard, Object object, boolean value) {
        FieldActor f = Snippets.resolveInstanceFieldForWriting(guard);
        if (f.isVolatile()) {
            preVolatileWrite();
            TupleAccess.writeBoolean(object, f.offset(), value);
            postVolatileWrite();
        } else {
            TupleAccess.writeBoolean(object, f.offset(), value);
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTSTATIC$boolean$init)
    public static void putstaticBoolean(Object staticTuple, int offset) {
        boolean value = popBoolean();
        TupleAccess.writeBoolean(staticTuple, offset, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTSTATIC$boolean)
    public static void putstaticBoolean(ResolutionGuard.InPool guard) {
        resolveAndPutStaticBoolean(guard, popBoolean());
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE
    public static void resolveAndPutStaticBoolean(ResolutionGuard.InPool guard, boolean value) {
        FieldActor f = Snippets.resolveStaticFieldForWriting(guard);
        Snippets.makeHolderInitialized(f);
        if (f.isVolatile()) {
            preVolatileWrite();
            TupleAccess.writeBoolean(f.holder().staticTuple(), f.offset(), value);
            postVolatileWrite();
        } else {
            TupleAccess.writeBoolean(f.holder().staticTuple(), f.offset(), value);
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(BALOAD)
    public static void baload() {
        int index = peekInt(0);
        Object array = peekObject(1);
        ArrayAccess.checkIndex(array, index);
        removeSlots(1);
        pokeBoolean(0, ArrayAccess.getBoolean(array, index));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(BASTORE)
    public static void bastore() {
        int index = peekInt(1);
        Object array = peekObject(2);
        ArrayAccess.checkIndex(array, index);
        boolean value = peekBoolean(0);
        ArrayAccess.setBoolean(array, index, value);
        removeSlots(3);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETFIELD$byte$resolved)
    public static void getfieldByte(int offset) {
        Object object = peekObject(0);
        pokeByte(0, TupleAccess.readByte(object, offset));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETFIELD$byte)
    public static void getfieldByte(ResolutionGuard.InPool guard) {
        Object object = peekObject(0);
        pokeByte(0, resolveAndGetFieldByte(guard, object));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE    public static byte resolveAndGetFieldByte(ResolutionGuard.InPool guard, Object object) {
        FieldActor f = Snippets.resolveInstanceFieldForReading(guard);
        if (f.isVolatile()) {
            preVolatileRead();
            byte value = TupleAccess.readByte(object, f.offset());
            postVolatileRead();
            return value;
        } else {
            return TupleAccess.readByte(object, f.offset());
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETSTATIC$byte)
    public static void getstaticByte(ResolutionGuard.InPool guard) {
        pushByte(resolveAndGetStaticByte(guard));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE    public static byte resolveAndGetStaticByte(ResolutionGuard.InPool guard) {
        FieldActor f = Snippets.resolveStaticFieldForReading(guard);
        Snippets.makeHolderInitialized(f);
        if (f.isVolatile()) {
            preVolatileRead();
            byte value = TupleAccess.readByte(f.holder().staticTuple(), f.offset());
            postVolatileRead();
            return value;
        } else {
            return TupleAccess.readByte(f.holder().staticTuple(), f.offset());
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETSTATIC$byte$init)
    public static void getstaticByte(Object staticTuple, int offset) {
        pushByte(TupleAccess.readByte(staticTuple, offset));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTFIELD$byte$resolved)
    public static void putfieldByte(int offset) {
        Object object = peekObject(1);
        byte value = peekByte(0);
        removeSlots(2);
        TupleAccess.writeByte(object, offset, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTFIELD$byte)
    public static void putfieldByte(ResolutionGuard.InPool guard) {
        Object object = peekObject(1);
        byte value = peekByte(0);
        resolveAndPutFieldByte(guard, object, value);
        removeSlots(2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE
    public static void resolveAndPutFieldByte(ResolutionGuard.InPool guard, Object object, byte value) {
        FieldActor f = Snippets.resolveInstanceFieldForWriting(guard);
        if (f.isVolatile()) {
            preVolatileWrite();
            TupleAccess.writeByte(object, f.offset(), value);
            postVolatileWrite();
        } else {
            TupleAccess.writeByte(object, f.offset(), value);
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTSTATIC$byte$init)
    public static void putstaticByte(Object staticTuple, int offset) {
        byte value = popByte();
        TupleAccess.writeByte(staticTuple, offset, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTSTATIC$byte)
    public static void putstaticByte(ResolutionGuard.InPool guard) {
        resolveAndPutStaticByte(guard, popByte());
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE
    public static void resolveAndPutStaticByte(ResolutionGuard.InPool guard, byte value) {
        FieldActor f = Snippets.resolveStaticFieldForWriting(guard);
        Snippets.makeHolderInitialized(f);
        if (f.isVolatile()) {
            preVolatileWrite();
            TupleAccess.writeByte(f.holder().staticTuple(), f.offset(), value);
            postVolatileWrite();
        } else {
            TupleAccess.writeByte(f.holder().staticTuple(), f.offset(), value);
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(I2B)
    public static void i2b() {
        int value = peekInt(0);
        pokeByte(0, (byte) value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PGET_BYTE)
    public static void pget_byte() {
        int index = peekInt(0);
        int disp = peekInt(1);
        Pointer ptr = peekWord(2).asPointer();
        removeSlots(2);
        pokeByte(0, ptr.getByte(disp, index));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PSET_BYTE)
    public static void pset_byte() {
        byte value = peekByte(0);
        int index = peekInt(1);
        int disp = peekInt(2);
        Pointer ptr = peekWord(3).asPointer();
        removeSlots(4);
        ptr.setByte(disp, index, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PREAD_BYTE)
    public static void pread_byte() {
        Offset off = peekWord(0).asOffset();
        Pointer ptr = peekWord(1).asPointer();
        removeSlots(1);
        pokeByte(0, ptr.readByte(off));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PWRITE_BYTE)
    public static void pwrite_byte() {
        Pointer ptr = peekWord(2).asPointer();
        Offset off = peekWord(1).asOffset();
        byte value = peekByte(0);
        removeSlots(3);
        ptr.writeByte(off, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PREAD_BYTE_I)
    public static void pread_byte_i() {
        int off = peekInt(0);
        Pointer ptr = peekWord(1).asPointer();
        removeSlots(1);
        pokeByte(0, ptr.readByte(off));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PWRITE_BYTE_I)
    public static void pwrite_byte_i() {
        Pointer ptr = peekWord(2).asPointer();
        int off = peekInt(1);
        byte value = peekByte(0);
        removeSlots(3);
        ptr.writeByte(off, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETFIELD$char$resolved)
    public static void getfieldChar(int offset) {
        Object object = peekObject(0);
        pokeChar(0, TupleAccess.readChar(object, offset));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETFIELD$char)
    public static void getfieldChar(ResolutionGuard.InPool guard) {
        Object object = peekObject(0);
        pokeChar(0, resolveAndGetFieldChar(guard, object));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE    public static char resolveAndGetFieldChar(ResolutionGuard.InPool guard, Object object) {
        FieldActor f = Snippets.resolveInstanceFieldForReading(guard);
        if (f.isVolatile()) {
            preVolatileRead();
            char value = TupleAccess.readChar(object, f.offset());
            postVolatileRead();
            return value;
        } else {
            return TupleAccess.readChar(object, f.offset());
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETSTATIC$char)
    public static void getstaticChar(ResolutionGuard.InPool guard) {
        pushChar(resolveAndGetStaticChar(guard));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE    public static char resolveAndGetStaticChar(ResolutionGuard.InPool guard) {
        FieldActor f = Snippets.resolveStaticFieldForReading(guard);
        Snippets.makeHolderInitialized(f);
        if (f.isVolatile()) {
            preVolatileRead();
            char value = TupleAccess.readChar(f.holder().staticTuple(), f.offset());
            postVolatileRead();
            return value;
        } else {
            return TupleAccess.readChar(f.holder().staticTuple(), f.offset());
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETSTATIC$char$init)
    public static void getstaticChar(Object staticTuple, int offset) {
        pushChar(TupleAccess.readChar(staticTuple, offset));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTFIELD$char$resolved)
    public static void putfieldChar(int offset) {
        Object object = peekObject(1);
        char value = peekChar(0);
        removeSlots(2);
        TupleAccess.writeChar(object, offset, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTFIELD$char)
    public static void putfieldChar(ResolutionGuard.InPool guard) {
        Object object = peekObject(1);
        char value = peekChar(0);
        resolveAndPutFieldChar(guard, object, value);
        removeSlots(2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE
    public static void resolveAndPutFieldChar(ResolutionGuard.InPool guard, Object object, char value) {
        FieldActor f = Snippets.resolveInstanceFieldForWriting(guard);
        if (f.isVolatile()) {
            preVolatileWrite();
            TupleAccess.writeChar(object, f.offset(), value);
            postVolatileWrite();
        } else {
            TupleAccess.writeChar(object, f.offset(), value);
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTSTATIC$char$init)
    public static void putstaticChar(Object staticTuple, int offset) {
        char value = popChar();
        TupleAccess.writeChar(staticTuple, offset, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTSTATIC$char)
    public static void putstaticChar(ResolutionGuard.InPool guard) {
        resolveAndPutStaticChar(guard, popChar());
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE
    public static void resolveAndPutStaticChar(ResolutionGuard.InPool guard, char value) {
        FieldActor f = Snippets.resolveStaticFieldForWriting(guard);
        Snippets.makeHolderInitialized(f);
        if (f.isVolatile()) {
            preVolatileWrite();
            TupleAccess.writeChar(f.holder().staticTuple(), f.offset(), value);
            postVolatileWrite();
        } else {
            TupleAccess.writeChar(f.holder().staticTuple(), f.offset(), value);
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(I2C)
    public static void i2c() {
        int value = peekInt(0);
        pokeChar(0, (char) value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(CALOAD)
    public static void caload() {
        int index = peekInt(0);
        Object array = peekObject(1);
        ArrayAccess.checkIndex(array, index);
        removeSlots(1);
        pokeChar(0, ArrayAccess.getChar(array, index));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(CASTORE)
    public static void castore() {
        int index = peekInt(1);
        Object array = peekObject(2);
        ArrayAccess.checkIndex(array, index);
        char value = peekChar(0);
        ArrayAccess.setChar(array, index, value);
        removeSlots(3);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PGET_CHAR)
    public static void pget_char() {
        int index = peekInt(0);
        int disp = peekInt(1);
        Pointer ptr = peekWord(2).asPointer();
        removeSlots(2);
        pokeChar(0, ptr.getChar(disp, index));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PREAD_CHAR)
    public static void pread_char() {
        Offset off = peekWord(0).asOffset();
        Pointer ptr = peekWord(1).asPointer();
        removeSlots(1);
        pokeChar(0, ptr.readChar(off));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PREAD_CHAR_I)
    public static void pread_char_i() {
        int off = peekInt(0);
        Pointer ptr = peekWord(1).asPointer();
        removeSlots(1);
        pokeChar(0, ptr.readChar(off));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETFIELD$short$resolved)
    public static void getfieldShort(int offset) {
        Object object = peekObject(0);
        pokeShort(0, TupleAccess.readShort(object, offset));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETFIELD$short)
    public static void getfieldShort(ResolutionGuard.InPool guard) {
        Object object = peekObject(0);
        pokeShort(0, resolveAndGetFieldShort(guard, object));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE    public static short resolveAndGetFieldShort(ResolutionGuard.InPool guard, Object object) {
        FieldActor f = Snippets.resolveInstanceFieldForReading(guard);
        if (f.isVolatile()) {
            preVolatileRead();
            short value = TupleAccess.readShort(object, f.offset());
            postVolatileRead();
            return value;
        } else {
            return TupleAccess.readShort(object, f.offset());
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETSTATIC$short)
    public static void getstaticShort(ResolutionGuard.InPool guard) {
        pushShort(resolveAndGetStaticShort(guard));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE    public static short resolveAndGetStaticShort(ResolutionGuard.InPool guard) {
        FieldActor f = Snippets.resolveStaticFieldForReading(guard);
        Snippets.makeHolderInitialized(f);
        if (f.isVolatile()) {
            preVolatileRead();
            short value = TupleAccess.readShort(f.holder().staticTuple(), f.offset());
            postVolatileRead();
            return value;
        } else {
            return TupleAccess.readShort(f.holder().staticTuple(), f.offset());
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETSTATIC$short$init)
    public static void getstaticShort(Object staticTuple, int offset) {
        pushShort(TupleAccess.readShort(staticTuple, offset));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTFIELD$short$resolved)
    public static void putfieldShort(int offset) {
        Object object = peekObject(1);
        short value = peekShort(0);
        removeSlots(2);
        TupleAccess.writeShort(object, offset, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTFIELD$short)
    public static void putfieldShort(ResolutionGuard.InPool guard) {
        Object object = peekObject(1);
        short value = peekShort(0);
        resolveAndPutFieldShort(guard, object, value);
        removeSlots(2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE
    public static void resolveAndPutFieldShort(ResolutionGuard.InPool guard, Object object, short value) {
        FieldActor f = Snippets.resolveInstanceFieldForWriting(guard);
        if (f.isVolatile()) {
            preVolatileWrite();
            TupleAccess.writeShort(object, f.offset(), value);
            postVolatileWrite();
        } else {
            TupleAccess.writeShort(object, f.offset(), value);
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTSTATIC$short$init)
    public static void putstaticShort(Object staticTuple, int offset) {
        short value = popShort();
        TupleAccess.writeShort(staticTuple, offset, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTSTATIC$short)
    public static void putstaticShort(ResolutionGuard.InPool guard) {
        resolveAndPutStaticShort(guard, popShort());
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE
    public static void resolveAndPutStaticShort(ResolutionGuard.InPool guard, short value) {
        FieldActor f = Snippets.resolveStaticFieldForWriting(guard);
        Snippets.makeHolderInitialized(f);
        if (f.isVolatile()) {
            preVolatileWrite();
            TupleAccess.writeShort(f.holder().staticTuple(), f.offset(), value);
            postVolatileWrite();
        } else {
            TupleAccess.writeShort(f.holder().staticTuple(), f.offset(), value);
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(I2S)
    public static void i2s() {
        int value = peekInt(0);
        pokeShort(0, (short) value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(SALOAD)
    public static void saload() {
        int index = peekInt(0);
        Object array = peekObject(1);
        ArrayAccess.checkIndex(array, index);
        removeSlots(1);
        pokeShort(0, ArrayAccess.getShort(array, index));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(SASTORE)
    public static void sastore() {
        int index = peekInt(1);
        Object array = peekObject(2);
        ArrayAccess.checkIndex(array, index);
        short value = peekShort(0);
        ArrayAccess.setShort(array, index, value);
        removeSlots(3);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PGET_SHORT)
    public static void pget_short() {
        int index = peekInt(0);
        int disp = peekInt(1);
        Pointer ptr = peekWord(2).asPointer();
        removeSlots(2);
        pokeShort(0, ptr.getShort(disp, index));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PSET_SHORT)
    public static void pset_short() {
        short value = peekShort(0);
        int index = peekInt(1);
        int disp = peekInt(2);
        Pointer ptr = peekWord(3).asPointer();
        removeSlots(4);
        ptr.setShort(disp, index, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PREAD_SHORT)
    public static void pread_short() {
        Offset off = peekWord(0).asOffset();
        Pointer ptr = peekWord(1).asPointer();
        removeSlots(1);
        pokeShort(0, ptr.readShort(off));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PWRITE_SHORT)
    public static void pwrite_short() {
        Pointer ptr = peekWord(2).asPointer();
        Offset off = peekWord(1).asOffset();
        short value = peekShort(0);
        removeSlots(3);
        ptr.writeShort(off, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PREAD_SHORT_I)
    public static void pread_short_i() {
        int off = peekInt(0);
        Pointer ptr = peekWord(1).asPointer();
        removeSlots(1);
        pokeShort(0, ptr.readShort(off));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PWRITE_SHORT_I)
    public static void pwrite_short_i() {
        Pointer ptr = peekWord(2).asPointer();
        int off = peekInt(1);
        short value = peekShort(0);
        removeSlots(3);
        ptr.writeShort(off, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(ICONST_0)
    public static void iconst_0() {
        pushInt(0);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(ICONST_1)
    public static void iconst_1() {
        pushInt(1);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(ICONST_2)
    public static void iconst_2() {
        pushInt(2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(ICONST_3)
    public static void iconst_3() {
        pushInt(3);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(ICONST_4)
    public static void iconst_4() {
        pushInt(4);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(ICONST_5)
    public static void iconst_5() {
        pushInt(5);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(ICONST_M1)
    public static void iconst_m1() {
        pushInt(-1);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETFIELD$int$resolved)
    public static void getfieldInt(int offset) {
        Object object = peekObject(0);
        pokeInt(0, TupleAccess.readInt(object, offset));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETFIELD$int)
    public static void getfieldInt(ResolutionGuard.InPool guard) {
        Object object = peekObject(0);
        pokeInt(0, resolveAndGetFieldInt(guard, object));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE    public static int resolveAndGetFieldInt(ResolutionGuard.InPool guard, Object object) {
        FieldActor f = Snippets.resolveInstanceFieldForReading(guard);
        if (f.isVolatile()) {
            preVolatileRead();
            int value = TupleAccess.readInt(object, f.offset());
            postVolatileRead();
            return value;
        } else {
            return TupleAccess.readInt(object, f.offset());
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETSTATIC$int)
    public static void getstaticInt(ResolutionGuard.InPool guard) {
        pushInt(resolveAndGetStaticInt(guard));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE    public static int resolveAndGetStaticInt(ResolutionGuard.InPool guard) {
        FieldActor f = Snippets.resolveStaticFieldForReading(guard);
        Snippets.makeHolderInitialized(f);
        if (f.isVolatile()) {
            preVolatileRead();
            int value = TupleAccess.readInt(f.holder().staticTuple(), f.offset());
            postVolatileRead();
            return value;
        } else {
            return TupleAccess.readInt(f.holder().staticTuple(), f.offset());
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETSTATIC$int$init)
    public static void getstaticInt(Object staticTuple, int offset) {
        pushInt(TupleAccess.readInt(staticTuple, offset));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTFIELD$int$resolved)
    public static void putfieldInt(int offset) {
        Object object = peekObject(1);
        int value = peekInt(0);
        removeSlots(2);
        TupleAccess.writeInt(object, offset, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTFIELD$int)
    public static void putfieldInt(ResolutionGuard.InPool guard) {
        Object object = peekObject(1);
        int value = peekInt(0);
        resolveAndPutFieldInt(guard, object, value);
        removeSlots(2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE
    public static void resolveAndPutFieldInt(ResolutionGuard.InPool guard, Object object, int value) {
        FieldActor f = Snippets.resolveInstanceFieldForWriting(guard);
        if (f.isVolatile()) {
            preVolatileWrite();
            TupleAccess.writeInt(object, f.offset(), value);
            postVolatileWrite();
        } else {
            TupleAccess.writeInt(object, f.offset(), value);
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTSTATIC$int$init)
    public static void putstaticInt(Object staticTuple, int offset) {
        int value = popInt();
        TupleAccess.writeInt(staticTuple, offset, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTSTATIC$int)
    public static void putstaticInt(ResolutionGuard.InPool guard) {
        resolveAndPutStaticInt(guard, popInt());
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE
    public static void resolveAndPutStaticInt(ResolutionGuard.InPool guard, int value) {
        FieldActor f = Snippets.resolveStaticFieldForWriting(guard);
        Snippets.makeHolderInitialized(f);
        if (f.isVolatile()) {
            preVolatileWrite();
            TupleAccess.writeInt(f.holder().staticTuple(), f.offset(), value);
            postVolatileWrite();
        } else {
            TupleAccess.writeInt(f.holder().staticTuple(), f.offset(), value);
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(ILOAD)
    public static void iload(int dispToLocalSlot) {
        int value = getLocalInt(dispToLocalSlot);
        pushInt(value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(ISTORE)
    public static void istore(int dispToLocalSlot) {
        int value = popInt();
        setLocalInt(dispToLocalSlot, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(LDC$int)
    public static void ildc(int constant) {
        pushInt(constant);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(L2I)
    public static void l2i() {
        long value = peekLong(0);
        removeSlots(1);
        pokeInt(0, (int) value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(F2I)
    public static void f2i() {
        float value = peekFloat(0);
        pokeInt(0, T1XRuntime.f2i(value));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(D2I)
    public static void d2i() {
        double value = peekDouble(0);
        removeSlots(1);
        pokeInt(0, T1XRuntime.d2i(value));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(IADD)
    public static void iadd() {
        int value2 = peekInt(0);
        int value1 = peekInt(1);
        removeSlots(1);
        pokeInt(0, value1 + value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(ISUB)
    public static void isub() {
        int value2 = peekInt(0);
        int value1 = peekInt(1);
        removeSlots(1);
        pokeInt(0, value1 - value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(IMUL)
    public static void imul() {
        int value2 = peekInt(0);
        int value1 = peekInt(1);
        removeSlots(1);
        pokeInt(0, value1 * value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(IDIV)
    public static void idiv() {
        int value2 = peekInt(0);
        int value1 = peekInt(1);
        removeSlots(1);
        pokeInt(0, value1 / value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(IREM)
    public static void irem() {
        int value2 = peekInt(0);
        int value1 = peekInt(1);
        removeSlots(1);
        pokeInt(0, value1 % value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INEG)
    public static void ineg() {
        int value = -peekInt(0);
        pokeInt(0, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(IOR)
    public static void ior() {
        int value2 = peekInt(0);
        int value1 = peekInt(1);
        removeSlots(1);
        pokeInt(0, value1 | value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(IAND)
    public static void iand() {
        int value2 = peekInt(0);
        int value1 = peekInt(1);
        removeSlots(1);
        pokeInt(0, value1 & value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(IXOR)
    public static void ixor() {
        int value2 = peekInt(0);
        int value1 = peekInt(1);
        removeSlots(1);
        pokeInt(0, value1 ^ value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(ISHL)
    public static void ishl() {
        int value2 = peekInt(0);
        int value1 = peekInt(1);
        removeSlots(1);
        pokeInt(0, value1 << value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(ISHR)
    public static void ishr() {
        int value2 = peekInt(0);
        int value1 = peekInt(1);
        removeSlots(1);
        pokeInt(0, value1 >> value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(IUSHR)
    public static void iushr() {
        int value2 = peekInt(0);
        int value1 = peekInt(1);
        removeSlots(1);
        pokeInt(0, value1 >>> value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(IF_ICMPEQ)
    public static void if_icmpeq() {
        icmp_prefix();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(IFEQ)
    public static void ifeq() {
        icmp0_prefix();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(IF_ICMPNE)
    public static void if_icmpne() {
        icmp_prefix();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(IFNE)
    public static void ifne() {
        icmp0_prefix();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(IF_ICMPLT)
    public static void if_icmplt() {
        icmp_prefix();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(IFLT)
    public static void iflt() {
        icmp0_prefix();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(IF_ICMPGE)
    public static void if_icmpge() {
        icmp_prefix();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(IFGE)
    public static void ifge() {
        icmp0_prefix();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(IF_ICMPGT)
    public static void if_icmpgt() {
        icmp_prefix();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(IFGT)
    public static void ifgt() {
        icmp0_prefix();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(IF_ICMPLE)
    public static void if_icmple() {
        icmp_prefix();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(IFLE)
    public static void ifle() {
        icmp0_prefix();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(IRETURN)
    public static int ireturn() {
        return popInt();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(IRETURN$unlockClass)
    public static int ireturnUnlockClass(Class<?> rcvr) {
        Monitor.noninlineExit(rcvr);
        return popInt();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(IRETURN$unlockReceiver)
    public static int ireturnUnlockReceiver(int dispToRcvrCopy) {
        Object rcvr = getLocalObject(dispToRcvrCopy);
        Monitor.noninlineExit(rcvr);
        return popInt();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(IALOAD)
    public static void iaload() {
        int index = peekInt(0);
        Object array = peekObject(1);
        ArrayAccess.checkIndex(array, index);
        removeSlots(1);
        pokeInt(0, ArrayAccess.getInt(array, index));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(IASTORE)
    public static void iastore() {
        int index = peekInt(1);
        Object array = peekObject(2);
        ArrayAccess.checkIndex(array, index);
        int value = peekInt(0);
        ArrayAccess.setInt(array, index, value);
        removeSlots(3);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PGET_INT)
    public static void pget_int() {
        int index = peekInt(0);
        int disp = peekInt(1);
        Pointer ptr = peekWord(2).asPointer();
        removeSlots(2);
        pokeInt(0, ptr.getInt(disp, index));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PSET_INT)
    public static void pset_int() {
        int value = peekInt(0);
        int index = peekInt(1);
        int disp = peekInt(2);
        Pointer ptr = peekWord(3).asPointer();
        removeSlots(4);
        ptr.setInt(disp, index, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PREAD_INT)
    public static void pread_int() {
        Offset off = peekWord(0).asOffset();
        Pointer ptr = peekWord(1).asPointer();
        removeSlots(1);
        pokeInt(0, ptr.readInt(off));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PWRITE_INT)
    public static void pwrite_int() {
        Pointer ptr = peekWord(2).asPointer();
        Offset off = peekWord(1).asOffset();
        int value = peekInt(0);
        removeSlots(3);
        ptr.writeInt(off, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PREAD_INT_I)
    public static void pread_int_i() {
        int off = peekInt(0);
        Pointer ptr = peekWord(1).asPointer();
        removeSlots(1);
        pokeInt(0, ptr.readInt(off));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PWRITE_INT_I)
    public static void pwrite_int_i() {
        Pointer ptr = peekWord(2).asPointer();
        int off = peekInt(1);
        int value = peekInt(0);
        removeSlots(3);
        ptr.writeInt(off, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PCMPSWP_INT)
    public static void pcmpswp_int() {
        int newValue = peekInt(0);
        int expectedValue = peekInt(1);
        Offset off = peekWord(2).asOffset();
        Pointer ptr = peekWord(3).asPointer();
        removeSlots(3);
        pokeInt(0, ptr.compareAndSwapInt(off, expectedValue, newValue));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PCMPSWP_INT_I)
    public static void pcmpswp_int_i() {
        int newValue = peekInt(0);
        int expectedValue = peekInt(1);
        int off = peekInt(2);
        Pointer ptr = peekWord(3).asPointer();
        removeSlots(3);
        pokeInt(0, ptr.compareAndSwapInt(off, expectedValue, newValue));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(FCONST)
    public static void fconst(float constant) {
        pushFloat(constant);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETFIELD$float$resolved)
    public static void getfieldFloat(int offset) {
        Object object = peekObject(0);
        pokeFloat(0, TupleAccess.readFloat(object, offset));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETFIELD$float)
    public static void getfieldFloat(ResolutionGuard.InPool guard) {
        Object object = peekObject(0);
        pokeFloat(0, resolveAndGetFieldFloat(guard, object));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE    public static float resolveAndGetFieldFloat(ResolutionGuard.InPool guard, Object object) {
        FieldActor f = Snippets.resolveInstanceFieldForReading(guard);
        if (f.isVolatile()) {
            preVolatileRead();
            float value = TupleAccess.readFloat(object, f.offset());
            postVolatileRead();
            return value;
        } else {
            return TupleAccess.readFloat(object, f.offset());
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETSTATIC$float)
    public static void getstaticFloat(ResolutionGuard.InPool guard) {
        pushFloat(resolveAndGetStaticFloat(guard));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE    public static float resolveAndGetStaticFloat(ResolutionGuard.InPool guard) {
        FieldActor f = Snippets.resolveStaticFieldForReading(guard);
        Snippets.makeHolderInitialized(f);
        if (f.isVolatile()) {
            preVolatileRead();
            float value = TupleAccess.readFloat(f.holder().staticTuple(), f.offset());
            postVolatileRead();
            return value;
        } else {
            return TupleAccess.readFloat(f.holder().staticTuple(), f.offset());
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETSTATIC$float$init)
    public static void getstaticFloat(Object staticTuple, int offset) {
        pushFloat(TupleAccess.readFloat(staticTuple, offset));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTFIELD$float$resolved)
    public static void putfieldFloat(int offset) {
        Object object = peekObject(1);
        float value = peekFloat(0);
        removeSlots(2);
        TupleAccess.writeFloat(object, offset, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTFIELD$float)
    public static void putfieldFloat(ResolutionGuard.InPool guard) {
        Object object = peekObject(1);
        float value = peekFloat(0);
        resolveAndPutFieldFloat(guard, object, value);
        removeSlots(2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE
    public static void resolveAndPutFieldFloat(ResolutionGuard.InPool guard, Object object, float value) {
        FieldActor f = Snippets.resolveInstanceFieldForWriting(guard);
        if (f.isVolatile()) {
            preVolatileWrite();
            TupleAccess.writeFloat(object, f.offset(), value);
            postVolatileWrite();
        } else {
            TupleAccess.writeFloat(object, f.offset(), value);
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTSTATIC$float$init)
    public static void putstaticFloat(Object staticTuple, int offset) {
        float value = popFloat();
        TupleAccess.writeFloat(staticTuple, offset, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTSTATIC$float)
    public static void putstaticFloat(ResolutionGuard.InPool guard) {
        resolveAndPutStaticFloat(guard, popFloat());
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE
    public static void resolveAndPutStaticFloat(ResolutionGuard.InPool guard, float value) {
        FieldActor f = Snippets.resolveStaticFieldForWriting(guard);
        Snippets.makeHolderInitialized(f);
        if (f.isVolatile()) {
            preVolatileWrite();
            TupleAccess.writeFloat(f.holder().staticTuple(), f.offset(), value);
            postVolatileWrite();
        } else {
            TupleAccess.writeFloat(f.holder().staticTuple(), f.offset(), value);
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(FLOAD)
    public static void fload(int dispToLocalSlot) {
        float value = getLocalFloat(dispToLocalSlot);
        pushFloat(value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(FSTORE)
    public static void fstore(int dispToLocalSlot) {
        float value = popFloat();
        setLocalFloat(dispToLocalSlot, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(LDC$float)
    public static void fldc(float constant) {
        pushFloat(constant);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(I2F)
    public static void i2f() {
        int value = peekInt(0);
        pokeFloat(0, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(L2F)
    public static void l2f() {
        long value = peekLong(0);
        removeSlots(1);
        pokeFloat(0, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(D2F)
    public static void d2f() {
        double value = peekDouble(0);
        removeSlots(1);
        pokeFloat(0, (float) value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(FADD)
    public static void fadd() {
        float value2 = peekFloat(0);
        float value1 = peekFloat(1);
        removeSlots(1);
        pokeFloat(0, value1 + value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(FSUB)
    public static void fsub() {
        float value2 = peekFloat(0);
        float value1 = peekFloat(1);
        removeSlots(1);
        pokeFloat(0, value1 - value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(FMUL)
    public static void fmul() {
        float value2 = peekFloat(0);
        float value1 = peekFloat(1);
        removeSlots(1);
        pokeFloat(0, value1 * value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(FDIV)
    public static void fdiv() {
        float value2 = peekFloat(0);
        float value1 = peekFloat(1);
        removeSlots(1);
        pokeFloat(0, value1 / value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(FREM)
    public static void frem() {
        float value2 = peekFloat(0);
        float value1 = peekFloat(1);
        removeSlots(1);
        pokeFloat(0, value1 % value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(FNEG)
    public static void fneg(float zero) {
        float value = zero - peekFloat(0);
        pokeFloat(0, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(FCMPG)
    public static void fcmpgOp() {
        float value2 = peekFloat(0);
        float value1 = peekFloat(1);
        int result = fcmpg(value1, value2);
        removeSlots(1);
        pokeInt(0, result);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(FCMPL)
    public static void fcmplOp() {
        float value2 = peekFloat(0);
        float value1 = peekFloat(1);
        int result = fcmpl(value1, value2);
        removeSlots(1);
        pokeInt(0, result);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(FRETURN)
    public static float freturn() {
        return popFloat();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(FRETURN$unlockClass)
    public static float freturnUnlockClass(Class<?> rcvr) {
        Monitor.noninlineExit(rcvr);
        return popFloat();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(FRETURN$unlockReceiver)
    public static float freturnUnlockReceiver(int dispToRcvrCopy) {
        Object rcvr = getLocalObject(dispToRcvrCopy);
        Monitor.noninlineExit(rcvr);
        return popFloat();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(FALOAD)
    public static void faload() {
        int index = peekInt(0);
        Object array = peekObject(1);
        ArrayAccess.checkIndex(array, index);
        removeSlots(1);
        pokeFloat(0, ArrayAccess.getFloat(array, index));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(FASTORE)
    public static void fastore() {
        int index = peekInt(1);
        Object array = peekObject(2);
        ArrayAccess.checkIndex(array, index);
        float value = peekFloat(0);
        ArrayAccess.setFloat(array, index, value);
        removeSlots(3);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKEVIRTUAL$float)
    public static void invokevirtualFloat(ResolutionGuard.InPool guard, int receiverStackIndex) {
        Object receiver = peekObject(receiverStackIndex);
        Address entryPoint = resolveAndSelectVirtualMethod(receiver, guard, receiverStackIndex);
        indirectCallFloat(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKEVIRTUAL$float$resolved)
    public static void invokevirtualFloat(int vTableIndex, int receiverStackIndex) {
        Object receiver = peekObject(receiverStackIndex);
        Address entryPoint = ObjectAccess.readHub(receiver).getWord(vTableIndex).asAddress();
        indirectCallFloat(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKEVIRTUAL$float$instrumented)
    public static void invokevirtualFloat(int vTableIndex, int receiverStackIndex, MethodProfile mpo, int mpoIndex) {
        Object receiver = peekObject(receiverStackIndex);
        Address entryPoint = selectVirtualMethod(receiver, vTableIndex, mpo, mpoIndex);
        indirectCallFloat(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKEINTERFACE$float)
    public static void invokeinterfaceFloat(ResolutionGuard.InPool guard, int receiverStackIndex) {
        Object receiver = peekObject(receiverStackIndex);
        Address entryPoint = resolveAndSelectInterfaceMethod(guard, receiver);
        indirectCallFloat(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKEINTERFACE$float$resolved)
    public static void invokeinterfaceFloat(InterfaceMethodActor interfaceMethodActor, int receiverStackIndex) {
        Object receiver = peekObject(receiverStackIndex);
        Address entryPoint = Snippets.selectInterfaceMethod(receiver, interfaceMethodActor).asAddress();
        indirectCallFloat(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKEINTERFACE$float$instrumented)
    public static void invokeinterfaceFloat(InterfaceMethodActor interfaceMethodActor, int receiverStackIndex, MethodProfile mpo, int mpoIndex) {
        Object receiver = peekObject(receiverStackIndex);
        Address entryPoint = Snippets.selectInterfaceMethod(receiver, interfaceMethodActor, mpo, mpoIndex);
        indirectCallFloat(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKESPECIAL$float)
    public static void invokespecialFloat(ResolutionGuard.InPool guard, int receiverStackIndex) {
        Pointer receiver = peekWord(receiverStackIndex).asPointer();
        nullCheck(receiver);
        indirectCallFloat(resolveSpecialMethod(guard), CallEntryPoint.OPTIMIZED_ENTRY_POINT);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKESPECIAL$float$resolved)
    public static void invokespecialFloat(int receiverStackIndex) {
        Pointer receiver = peekWord(receiverStackIndex).asPointer();
        nullCheck(receiver);
        directCallFloat();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKESTATIC$float)
    public static void invokestaticFloat(ResolutionGuard.InPool guard) {
        indirectCallFloat(resolveStaticMethod(guard), CallEntryPoint.OPTIMIZED_ENTRY_POINT);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKESTATIC$float$init)
    public static void invokestaticFloat() {
        directCallFloat();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PGET_FLOAT)
    public static void pget_float() {
        int index = peekInt(0);
        int disp = peekInt(1);
        Pointer ptr = peekWord(2).asPointer();
        removeSlots(2);
        pokeFloat(0, ptr.getFloat(disp, index));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PSET_FLOAT)
    public static void pset_float() {
        float value = peekFloat(0);
        int index = peekInt(1);
        int disp = peekInt(2);
        Pointer ptr = peekWord(3).asPointer();
        removeSlots(4);
        ptr.setFloat(disp, index, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PREAD_FLOAT)
    public static void pread_float() {
        Offset off = peekWord(0).asOffset();
        Pointer ptr = peekWord(1).asPointer();
        removeSlots(1);
        pokeFloat(0, ptr.readFloat(off));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PWRITE_FLOAT)
    public static void pwrite_float() {
        Pointer ptr = peekWord(2).asPointer();
        Offset off = peekWord(1).asOffset();
        float value = peekFloat(0);
        removeSlots(3);
        ptr.writeFloat(off, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PREAD_FLOAT_I)
    public static void pread_float_i() {
        int off = peekInt(0);
        Pointer ptr = peekWord(1).asPointer();
        removeSlots(1);
        pokeFloat(0, ptr.readFloat(off));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PWRITE_FLOAT_I)
    public static void pwrite_float_i() {
        Pointer ptr = peekWord(2).asPointer();
        int off = peekInt(1);
        float value = peekFloat(0);
        removeSlots(3);
        ptr.writeFloat(off, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(LCONST)
    public static void lconst(long constant) {
        pushLong(constant);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETFIELD$long$resolved)
    public static void getfieldLong(int offset) {
        Object object = peekObject(0);
        addSlots(1);
        pokeLong(0, TupleAccess.readLong(object, offset));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETFIELD$long)
    public static void getfieldLong(ResolutionGuard.InPool guard) {
        Object object = peekObject(0);
        addSlots(1);
        pokeLong(0, resolveAndGetFieldLong(guard, object));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE    public static long resolveAndGetFieldLong(ResolutionGuard.InPool guard, Object object) {
        FieldActor f = Snippets.resolveInstanceFieldForReading(guard);
        if (f.isVolatile()) {
            preVolatileRead();
            long value = TupleAccess.readLong(object, f.offset());
            postVolatileRead();
            return value;
        } else {
            return TupleAccess.readLong(object, f.offset());
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETSTATIC$long)
    public static void getstaticLong(ResolutionGuard.InPool guard) {
        pushLong(resolveAndGetStaticLong(guard));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE    public static long resolveAndGetStaticLong(ResolutionGuard.InPool guard) {
        FieldActor f = Snippets.resolveStaticFieldForReading(guard);
        Snippets.makeHolderInitialized(f);
        if (f.isVolatile()) {
            preVolatileRead();
            long value = TupleAccess.readLong(f.holder().staticTuple(), f.offset());
            postVolatileRead();
            return value;
        } else {
            return TupleAccess.readLong(f.holder().staticTuple(), f.offset());
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETSTATIC$long$init)
    public static void getstaticLong(Object staticTuple, int offset) {
        pushLong(TupleAccess.readLong(staticTuple, offset));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTFIELD$long$resolved)
    public static void putfieldLong(int offset) {
        Object object = peekObject(2);
        long value = peekLong(0);
        removeSlots(3);
        TupleAccess.writeLong(object, offset, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTFIELD$long)
    public static void putfieldLong(ResolutionGuard.InPool guard) {
        Object object = peekObject(2);
        long value = peekLong(0);
        resolveAndPutFieldLong(guard, object, value);
        removeSlots(3);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE
    public static void resolveAndPutFieldLong(ResolutionGuard.InPool guard, Object object, long value) {
        FieldActor f = Snippets.resolveInstanceFieldForWriting(guard);
        if (f.isVolatile()) {
            preVolatileWrite();
            TupleAccess.writeLong(object, f.offset(), value);
            postVolatileWrite();
        } else {
            TupleAccess.writeLong(object, f.offset(), value);
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTSTATIC$long$init)
    public static void putstaticLong(Object staticTuple, int offset) {
        long value = popLong();
        TupleAccess.writeLong(staticTuple, offset, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTSTATIC$long)
    public static void putstaticLong(ResolutionGuard.InPool guard) {
        resolveAndPutStaticLong(guard, popLong());
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE
    public static void resolveAndPutStaticLong(ResolutionGuard.InPool guard, long value) {
        FieldActor f = Snippets.resolveStaticFieldForWriting(guard);
        Snippets.makeHolderInitialized(f);
        if (f.isVolatile()) {
            preVolatileWrite();
            TupleAccess.writeLong(f.holder().staticTuple(), f.offset(), value);
            postVolatileWrite();
        } else {
            TupleAccess.writeLong(f.holder().staticTuple(), f.offset(), value);
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(LLOAD)
    public static void lload(int dispToLocalSlot) {
        long value = getLocalLong(dispToLocalSlot);
        pushLong(value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(LSTORE)
    public static void lstore(int dispToLocalSlot) {
        long value = popLong();
        setLocalLong(dispToLocalSlot, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(LDC$long)
    public static void lldc(long constant) {
        pushLong(constant);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(I2L)
    public static void i2l() {
        int value = peekInt(0);
        addSlots(1);
        pokeLong(0, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(F2L)
    public static void f2l() {
        float value = peekFloat(0);
        addSlots(1);
        pokeLong(0, T1XRuntime.f2l(value));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(D2L)
    public static void d2l() {
        double value = peekDouble(0);
        pokeLong(0, T1XRuntime.d2l(value));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(LADD)
    public static void ladd() {
        long value2 = peekLong(0);
        long value1 = peekLong(2);
        removeSlots(2);
        pokeLong(0, value1 + value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(LSUB)
    public static void lsub() {
        long value2 = peekLong(0);
        long value1 = peekLong(2);
        removeSlots(2);
        pokeLong(0, value1 - value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(LMUL)
    public static void lmul() {
        long value2 = peekLong(0);
        long value1 = peekLong(2);
        removeSlots(2);
        pokeLong(0, value1 * value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(LDIV)
    public static void ldiv() {
        long value2 = peekLong(0);
        long value1 = peekLong(2);
        removeSlots(2);
        pokeLong(0, value1 / value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(LREM)
    public static void lrem() {
        long value2 = peekLong(0);
        long value1 = peekLong(2);
        removeSlots(2);
        pokeLong(0, value1 % value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(LNEG)
    public static void lneg() {
        long value = -peekLong(0);
        pokeLong(0, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(LOR)
    public static void lor() {
        long value2 = peekLong(0);
        long value1 = peekLong(2);
        removeSlots(2);
        pokeLong(0, value1 | value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(LAND)
    public static void land() {
        long value2 = peekLong(0);
        long value1 = peekLong(2);
        removeSlots(2);
        pokeLong(0, value1 & value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(LXOR)
    public static void lxor() {
        long value2 = peekLong(0);
        long value1 = peekLong(2);
        removeSlots(2);
        pokeLong(0, value1 ^ value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(LSHL)
    public static void lshl() {
        int value2 = peekInt(0);
        long value1 = peekLong(1);
        removeSlots(1);
        pokeLong(0, value1 << value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(LSHR)
    public static void lshr() {
        int value2 = peekInt(0);
        long value1 = peekLong(1);
        removeSlots(1);
        pokeLong(0, value1 >> value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(LUSHR)
    public static void lushr() {
        int value2 = peekInt(0);
        long value1 = peekLong(1);
        removeSlots(1);
        pokeLong(0, value1 >>> value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(LCMP)
    public static void lcmpOp() {
        long value2 = peekLong(0);
        long value1 = peekLong(2);
        int result = lcmp(value1, value2);
        removeSlots(3);
        pokeInt(0, result);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(LRETURN)
    public static long lreturn() {
        return popLong();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(LRETURN$unlockClass)
    public static long lreturnUnlockClass(Class<?> rcvr) {
        Monitor.noninlineExit(rcvr);
        return popLong();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(LRETURN$unlockReceiver)
    public static long lreturnUnlockReceiver(int dispToRcvrCopy) {
        Object rcvr = getLocalObject(dispToRcvrCopy);
        Monitor.noninlineExit(rcvr);
        return popLong();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(LALOAD)
    public static void laload() {
        int index = peekInt(0);
        Object array = peekObject(1);
        ArrayAccess.checkIndex(array, index);
        pokeLong(0, ArrayAccess.getLong(array, index));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(LASTORE)
    public static void lastore() {
        int index = peekInt(2);
        Object array = peekObject(3);
        ArrayAccess.checkIndex(array, index);
        long value = peekLong(0);
        ArrayAccess.setLong(array, index, value);
        removeSlots(4);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKEVIRTUAL$long)
    public static void invokevirtualLong(ResolutionGuard.InPool guard, int receiverStackIndex) {
        Object receiver = peekObject(receiverStackIndex);
        Address entryPoint = resolveAndSelectVirtualMethod(receiver, guard, receiverStackIndex);
        indirectCallLong(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKEVIRTUAL$long$resolved)
    public static void invokevirtualLong(int vTableIndex, int receiverStackIndex) {
        Object receiver = peekObject(receiverStackIndex);
        Address entryPoint = ObjectAccess.readHub(receiver).getWord(vTableIndex).asAddress();
        indirectCallLong(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKEVIRTUAL$long$instrumented)
    public static void invokevirtualLong(int vTableIndex, int receiverStackIndex, MethodProfile mpo, int mpoIndex) {
        Object receiver = peekObject(receiverStackIndex);
        Address entryPoint = selectVirtualMethod(receiver, vTableIndex, mpo, mpoIndex);
        indirectCallLong(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKEINTERFACE$long)
    public static void invokeinterfaceLong(ResolutionGuard.InPool guard, int receiverStackIndex) {
        Object receiver = peekObject(receiverStackIndex);
        Address entryPoint = resolveAndSelectInterfaceMethod(guard, receiver);
        indirectCallLong(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKEINTERFACE$long$resolved)
    public static void invokeinterfaceLong(InterfaceMethodActor interfaceMethodActor, int receiverStackIndex) {
        Object receiver = peekObject(receiverStackIndex);
        Address entryPoint = Snippets.selectInterfaceMethod(receiver, interfaceMethodActor).asAddress();
        indirectCallLong(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKEINTERFACE$long$instrumented)
    public static void invokeinterfaceLong(InterfaceMethodActor interfaceMethodActor, int receiverStackIndex, MethodProfile mpo, int mpoIndex) {
        Object receiver = peekObject(receiverStackIndex);
        Address entryPoint = Snippets.selectInterfaceMethod(receiver, interfaceMethodActor, mpo, mpoIndex);
        indirectCallLong(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKESPECIAL$long)
    public static void invokespecialLong(ResolutionGuard.InPool guard, int receiverStackIndex) {
        Pointer receiver = peekWord(receiverStackIndex).asPointer();
        nullCheck(receiver);
        indirectCallLong(resolveSpecialMethod(guard), CallEntryPoint.OPTIMIZED_ENTRY_POINT);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKESPECIAL$long$resolved)
    public static void invokespecialLong(int receiverStackIndex) {
        Pointer receiver = peekWord(receiverStackIndex).asPointer();
        nullCheck(receiver);
        directCallLong();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKESTATIC$long)
    public static void invokestaticLong(ResolutionGuard.InPool guard) {
        indirectCallLong(resolveStaticMethod(guard), CallEntryPoint.OPTIMIZED_ENTRY_POINT);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKESTATIC$long$init)
    public static void invokestaticLong() {
        directCallLong();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PGET_LONG)
    public static void pget_long() {
        int index = peekInt(0);
        int disp = peekInt(1);
        Pointer ptr = peekWord(2).asPointer();
        removeSlots(1);
        pokeLong(0, ptr.getLong(disp, index));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PSET_LONG)
    public static void pset_long() {
        long value = peekLong(0);
        int index = peekInt(2);
        int disp = peekInt(3);
        Pointer ptr = peekWord(4).asPointer();
        removeSlots(5);
        ptr.setLong(disp, index, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PREAD_LONG)
    public static void pread_long() {
        Offset off = peekWord(0).asOffset();
        Pointer ptr = peekWord(1).asPointer();
        pokeLong(0, ptr.readLong(off));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PWRITE_LONG)
    public static void pwrite_long() {
        Pointer ptr = peekWord(2).asPointer();
        Offset off = peekWord(1).asOffset();
        long value = peekLong(0);
        removeSlots(4);
        ptr.writeLong(off, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PREAD_LONG_I)
    public static void pread_long_i() {
        int off = peekInt(0);
        Pointer ptr = peekWord(1).asPointer();
        pokeLong(0, ptr.readLong(off));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PWRITE_LONG_I)
    public static void pwrite_long_i() {
        Pointer ptr = peekWord(2).asPointer();
        int off = peekInt(1);
        long value = peekLong(0);
        removeSlots(4);
        ptr.writeLong(off, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(DCONST)
    public static void dconst(double constant) {
        pushDouble(constant);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETFIELD$double$resolved)
    public static void getfieldDouble(int offset) {
        Object object = peekObject(0);
        addSlots(1);
        pokeDouble(0, TupleAccess.readDouble(object, offset));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETFIELD$double)
    public static void getfieldDouble(ResolutionGuard.InPool guard) {
        Object object = peekObject(0);
        addSlots(1);
        pokeDouble(0, resolveAndGetFieldDouble(guard, object));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE    public static double resolveAndGetFieldDouble(ResolutionGuard.InPool guard, Object object) {
        FieldActor f = Snippets.resolveInstanceFieldForReading(guard);
        if (f.isVolatile()) {
            preVolatileRead();
            double value = TupleAccess.readDouble(object, f.offset());
            postVolatileRead();
            return value;
        } else {
            return TupleAccess.readDouble(object, f.offset());
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETSTATIC$double)
    public static void getstaticDouble(ResolutionGuard.InPool guard) {
        pushDouble(resolveAndGetStaticDouble(guard));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE    public static double resolveAndGetStaticDouble(ResolutionGuard.InPool guard) {
        FieldActor f = Snippets.resolveStaticFieldForReading(guard);
        Snippets.makeHolderInitialized(f);
        if (f.isVolatile()) {
            preVolatileRead();
            double value = TupleAccess.readDouble(f.holder().staticTuple(), f.offset());
            postVolatileRead();
            return value;
        } else {
            return TupleAccess.readDouble(f.holder().staticTuple(), f.offset());
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETSTATIC$double$init)
    public static void getstaticDouble(Object staticTuple, int offset) {
        pushDouble(TupleAccess.readDouble(staticTuple, offset));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTFIELD$double$resolved)
    public static void putfieldDouble(int offset) {
        Object object = peekObject(2);
        double value = peekDouble(0);
        removeSlots(3);
        TupleAccess.writeDouble(object, offset, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTFIELD$double)
    public static void putfieldDouble(ResolutionGuard.InPool guard) {
        Object object = peekObject(2);
        double value = peekDouble(0);
        resolveAndPutFieldDouble(guard, object, value);
        removeSlots(3);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE
    public static void resolveAndPutFieldDouble(ResolutionGuard.InPool guard, Object object, double value) {
        FieldActor f = Snippets.resolveInstanceFieldForWriting(guard);
        if (f.isVolatile()) {
            preVolatileWrite();
            TupleAccess.writeDouble(object, f.offset(), value);
            postVolatileWrite();
        } else {
            TupleAccess.writeDouble(object, f.offset(), value);
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTSTATIC$double$init)
    public static void putstaticDouble(Object staticTuple, int offset) {
        double value = popDouble();
        TupleAccess.writeDouble(staticTuple, offset, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTSTATIC$double)
    public static void putstaticDouble(ResolutionGuard.InPool guard) {
        resolveAndPutStaticDouble(guard, popDouble());
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE
    public static void resolveAndPutStaticDouble(ResolutionGuard.InPool guard, double value) {
        FieldActor f = Snippets.resolveStaticFieldForWriting(guard);
        Snippets.makeHolderInitialized(f);
        if (f.isVolatile()) {
            preVolatileWrite();
            TupleAccess.writeDouble(f.holder().staticTuple(), f.offset(), value);
            postVolatileWrite();
        } else {
            TupleAccess.writeDouble(f.holder().staticTuple(), f.offset(), value);
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(DLOAD)
    public static void dload(int dispToLocalSlot) {
        double value = getLocalDouble(dispToLocalSlot);
        pushDouble(value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(DSTORE)
    public static void dstore(int dispToLocalSlot) {
        double value = popDouble();
        setLocalDouble(dispToLocalSlot, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(LDC$double)
    public static void dldc(double constant) {
        pushDouble(constant);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(I2D)
    public static void i2d() {
        int value = peekInt(0);
        addSlots(1);
        pokeDouble(0, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(L2D)
    public static void l2d() {
        long value = peekLong(0);
        pokeDouble(0, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(F2D)
    public static void f2d() {
        float value = peekFloat(0);
        addSlots(1);
        pokeDouble(0, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(DADD)
    public static void dadd() {
        double value2 = peekDouble(0);
        double value1 = peekDouble(2);
        removeSlots(2);
        pokeDouble(0, value1 + value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(DSUB)
    public static void dsub() {
        double value2 = peekDouble(0);
        double value1 = peekDouble(2);
        removeSlots(2);
        pokeDouble(0, value1 - value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(DMUL)
    public static void dmul() {
        double value2 = peekDouble(0);
        double value1 = peekDouble(2);
        removeSlots(2);
        pokeDouble(0, value1 * value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(DDIV)
    public static void ddiv() {
        double value2 = peekDouble(0);
        double value1 = peekDouble(2);
        removeSlots(2);
        pokeDouble(0, value1 / value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(DREM)
    public static void drem() {
        double value2 = peekDouble(0);
        double value1 = peekDouble(2);
        removeSlots(2);
        pokeDouble(0, value1 % value2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(DNEG)
    public static void dneg(double zero) {
        double value = zero - peekDouble(0);
        pokeDouble(0, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(DCMPG)
    public static void dcmpgOp() {
        double value2 = peekDouble(0);
        double value1 = peekDouble(2);
        int result = dcmpg(value1, value2);
        removeSlots(3);
        pokeInt(0, result);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(DCMPL)
    public static void dcmplOp() {
        double value2 = peekDouble(0);
        double value1 = peekDouble(2);
        int result = dcmpl(value1, value2);
        removeSlots(3);
        pokeInt(0, result);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(DRETURN)
    public static double dreturn() {
        return popDouble();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(DRETURN$unlockClass)
    public static double dreturnUnlockClass(Class<?> rcvr) {
        Monitor.noninlineExit(rcvr);
        return popDouble();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(DRETURN$unlockReceiver)
    public static double dreturnUnlockReceiver(int dispToRcvrCopy) {
        Object rcvr = getLocalObject(dispToRcvrCopy);
        Monitor.noninlineExit(rcvr);
        return popDouble();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(DALOAD)
    public static void daload() {
        int index = peekInt(0);
        Object array = peekObject(1);
        ArrayAccess.checkIndex(array, index);
        pokeDouble(0, ArrayAccess.getDouble(array, index));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(DASTORE)
    public static void dastore() {
        int index = peekInt(2);
        Object array = peekObject(3);
        ArrayAccess.checkIndex(array, index);
        double value = peekDouble(0);
        ArrayAccess.setDouble(array, index, value);
        removeSlots(4);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKEVIRTUAL$double)
    public static void invokevirtualDouble(ResolutionGuard.InPool guard, int receiverStackIndex) {
        Object receiver = peekObject(receiverStackIndex);
        Address entryPoint = resolveAndSelectVirtualMethod(receiver, guard, receiverStackIndex);
        indirectCallDouble(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKEVIRTUAL$double$resolved)
    public static void invokevirtualDouble(int vTableIndex, int receiverStackIndex) {
        Object receiver = peekObject(receiverStackIndex);
        Address entryPoint = ObjectAccess.readHub(receiver).getWord(vTableIndex).asAddress();
        indirectCallDouble(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKEVIRTUAL$double$instrumented)
    public static void invokevirtualDouble(int vTableIndex, int receiverStackIndex, MethodProfile mpo, int mpoIndex) {
        Object receiver = peekObject(receiverStackIndex);
        Address entryPoint = selectVirtualMethod(receiver, vTableIndex, mpo, mpoIndex);
        indirectCallDouble(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKEINTERFACE$double)
    public static void invokeinterfaceDouble(ResolutionGuard.InPool guard, int receiverStackIndex) {
        Object receiver = peekObject(receiverStackIndex);
        Address entryPoint = resolveAndSelectInterfaceMethod(guard, receiver);
        indirectCallDouble(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKEINTERFACE$double$resolved)
    public static void invokeinterfaceDouble(InterfaceMethodActor interfaceMethodActor, int receiverStackIndex) {
        Object receiver = peekObject(receiverStackIndex);
        Address entryPoint = Snippets.selectInterfaceMethod(receiver, interfaceMethodActor).asAddress();
        indirectCallDouble(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKEINTERFACE$double$instrumented)
    public static void invokeinterfaceDouble(InterfaceMethodActor interfaceMethodActor, int receiverStackIndex, MethodProfile mpo, int mpoIndex) {
        Object receiver = peekObject(receiverStackIndex);
        Address entryPoint = Snippets.selectInterfaceMethod(receiver, interfaceMethodActor, mpo, mpoIndex);
        indirectCallDouble(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKESPECIAL$double)
    public static void invokespecialDouble(ResolutionGuard.InPool guard, int receiverStackIndex) {
        Pointer receiver = peekWord(receiverStackIndex).asPointer();
        nullCheck(receiver);
        indirectCallDouble(resolveSpecialMethod(guard), CallEntryPoint.OPTIMIZED_ENTRY_POINT);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKESPECIAL$double$resolved)
    public static void invokespecialDouble(int receiverStackIndex) {
        Pointer receiver = peekWord(receiverStackIndex).asPointer();
        nullCheck(receiver);
        directCallDouble();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKESTATIC$double)
    public static void invokestaticDouble(ResolutionGuard.InPool guard) {
        indirectCallDouble(resolveStaticMethod(guard), CallEntryPoint.OPTIMIZED_ENTRY_POINT);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKESTATIC$double$init)
    public static void invokestaticDouble() {
        directCallDouble();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PGET_DOUBLE)
    public static void pget_double() {
        int index = peekInt(0);
        int disp = peekInt(1);
        Pointer ptr = peekWord(2).asPointer();
        removeSlots(1);
        pokeDouble(0, ptr.getDouble(disp, index));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PSET_DOUBLE)
    public static void pset_double() {
        double value = peekDouble(0);
        int index = peekInt(2);
        int disp = peekInt(3);
        Pointer ptr = peekWord(4).asPointer();
        removeSlots(5);
        ptr.setDouble(disp, index, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PREAD_DOUBLE)
    public static void pread_double() {
        Offset off = peekWord(0).asOffset();
        Pointer ptr = peekWord(1).asPointer();
        pokeDouble(0, ptr.readDouble(off));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PWRITE_DOUBLE)
    public static void pwrite_double() {
        Pointer ptr = peekWord(2).asPointer();
        Offset off = peekWord(1).asOffset();
        double value = peekDouble(0);
        removeSlots(4);
        ptr.writeDouble(off, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PREAD_DOUBLE_I)
    public static void pread_double_i() {
        int off = peekInt(0);
        Pointer ptr = peekWord(1).asPointer();
        pokeDouble(0, ptr.readDouble(off));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PWRITE_DOUBLE_I)
    public static void pwrite_double_i() {
        Pointer ptr = peekWord(2).asPointer();
        int off = peekInt(1);
        double value = peekDouble(0);
        removeSlots(4);
        ptr.writeDouble(off, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(ACONST_NULL)
    public static void aconst_null() {
        pushObject(null);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETFIELD$reference$resolved)
    public static void getfieldReference(int offset) {
        Object object = peekObject(0);
        pokeObject(0, TupleAccess.readObject(object, offset));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETFIELD$reference)
    public static void getfieldReference(ResolutionGuard.InPool guard) {
        Object object = peekObject(0);
        pokeObject(0, resolveAndGetFieldReference(guard, object));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE    public static Object resolveAndGetFieldReference(ResolutionGuard.InPool guard, Object object) {
        FieldActor f = Snippets.resolveInstanceFieldForReading(guard);
        if (f.isVolatile()) {
            preVolatileRead();
            Object value = TupleAccess.readObject(object, f.offset());
            postVolatileRead();
            return value;
        } else {
            return TupleAccess.readObject(object, f.offset());
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETSTATIC$reference)
    public static void getstaticReference(ResolutionGuard.InPool guard) {
        pushObject(resolveAndGetStaticReference(guard));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE    public static Object resolveAndGetStaticReference(ResolutionGuard.InPool guard) {
        FieldActor f = Snippets.resolveStaticFieldForReading(guard);
        Snippets.makeHolderInitialized(f);
        if (f.isVolatile()) {
            preVolatileRead();
            Object value = TupleAccess.readObject(f.holder().staticTuple(), f.offset());
            postVolatileRead();
            return value;
        } else {
            return TupleAccess.readObject(f.holder().staticTuple(), f.offset());
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETSTATIC$reference$init)
    public static void getstaticReference(Object staticTuple, int offset) {
        pushObject(TupleAccess.readObject(staticTuple, offset));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTFIELD$reference$resolved)
    public static void putfieldReference(int offset) {
        Object object = peekObject(1);
        Object value = peekObject(0);
        removeSlots(2);
        TupleAccess.noninlineWriteObject(object, offset, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTFIELD$reference)
    public static void putfieldReference(ResolutionGuard.InPool guard) {
        Object object = peekObject(1);
        Object value = peekObject(0);
        resolveAndPutFieldReference(guard, object, value);
        removeSlots(2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE
    public static void resolveAndPutFieldReference(ResolutionGuard.InPool guard, Object object, Object value) {
        FieldActor f = Snippets.resolveInstanceFieldForWriting(guard);
        if (f.isVolatile()) {
            preVolatileWrite();
            TupleAccess.writeObject(object, f.offset(), value);
            postVolatileWrite();
        } else {
            TupleAccess.writeObject(object, f.offset(), value);
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTSTATIC$reference$init)
    public static void putstaticReference(Object staticTuple, int offset) {
        Object value = popObject();
        TupleAccess.noninlineWriteObject(staticTuple, offset, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTSTATIC$reference)
    public static void putstaticReference(ResolutionGuard.InPool guard) {
        resolveAndPutStaticReference(guard, popObject());
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE
    public static void resolveAndPutStaticReference(ResolutionGuard.InPool guard, Object value) {
        FieldActor f = Snippets.resolveStaticFieldForWriting(guard);
        Snippets.makeHolderInitialized(f);
        if (f.isVolatile()) {
            preVolatileWrite();
            TupleAccess.writeObject(f.holder().staticTuple(), f.offset(), value);
            postVolatileWrite();
        } else {
            TupleAccess.writeObject(f.holder().staticTuple(), f.offset(), value);
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(ALOAD)
    public static void aload(int dispToLocalSlot) {
        Object value = getLocalObject(dispToLocalSlot);
        pushObject(value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(ASTORE)
    public static void astore(int dispToLocalSlot) {
        Object value = popObject();
        setLocalObject(dispToLocalSlot, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(LDC$reference$resolved)
    public static void rldc(Object constant) {
        pushObject(constant);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(LDC$reference)
    public static void urldc(ResolutionGuard guard) {
        ClassActor classActor = Snippets.resolveClass(guard);
        Object constant = T1XRuntime.getClassMirror(classActor);
        pushObject(constant);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(IF_ACMPEQ)
    public static void if_acmpeq() {
        acmp_prefix();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(IF_ACMPNE)
    public static void if_acmpne() {
        acmp_prefix();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(IFNULL)
    public static void ifnull() {
        acmp0_prefix();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(IFNONNULL)
    public static void ifnonnull() {
        acmp0_prefix();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(ARETURN)
    public static Object areturn() {
        return popObject();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(ARETURN$unlockClass)
    public static Object areturnUnlockClass(Class<?> rcvr) {
        Monitor.noninlineExit(rcvr);
        return popObject();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(ARETURN$unlockReceiver)
    public static Object areturnUnlockReceiver(int dispToRcvrCopy) {
        Object rcvr = getLocalObject(dispToRcvrCopy);
        Monitor.noninlineExit(rcvr);
        return popObject();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(AALOAD)
    public static void aaload() {
        int index = peekInt(0);
        Object array = peekObject(1);
        ArrayAccess.checkIndex(array, index);
        removeSlots(1);
        pokeObject(0, ArrayAccess.getObject(array, index));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(AASTORE)
    public static void aastore() {
        int index = peekInt(1);
        Object array = peekObject(2);
        ArrayAccess.checkIndex(array, index);
        Object value = peekObject(0);
        ArrayAccess.checkSetObject(array, value);
        ArrayAccess.setObject(array, index, value);
        removeSlots(3);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PGET_REFERENCE)
    public static void pget_reference() {
        int index = peekInt(0);
        int disp = peekInt(1);
        Pointer ptr = peekWord(2).asPointer();
        removeSlots(2);
        pokeReference(0, ptr.getReference(disp, index));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PSET_REFERENCE)
    public static void pset_reference() {
        Reference value = peekReference(0);
        int index = peekInt(1);
        int disp = peekInt(2);
        Pointer ptr = peekWord(3).asPointer();
        removeSlots(4);
        ptr.setReference(disp, index, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PREAD_REFERENCE)
    public static void pread_reference() {
        Offset off = peekWord(0).asOffset();
        Pointer ptr = peekWord(1).asPointer();
        removeSlots(1);
        pokeReference(0, ptr.readReference(off));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PWRITE_REFERENCE)
    public static void pwrite_reference() {
        Pointer ptr = peekWord(2).asPointer();
        Offset off = peekWord(1).asOffset();
        Reference value = peekReference(0);
        removeSlots(3);
        ptr.writeReference(off, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PREAD_REFERENCE_I)
    public static void pread_reference_i() {
        int off = peekInt(0);
        Pointer ptr = peekWord(1).asPointer();
        removeSlots(1);
        pokeReference(0, ptr.readReference(off));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PWRITE_REFERENCE_I)
    public static void pwrite_reference_i() {
        Pointer ptr = peekWord(2).asPointer();
        int off = peekInt(1);
        Reference value = peekReference(0);
        removeSlots(3);
        ptr.writeReference(off, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PCMPSWP_REFERENCE)
    public static void pcmpswp_reference() {
        Reference newValue = peekReference(0);
        Reference expectedValue = peekReference(1);
        Offset off = peekWord(2).asOffset();
        Pointer ptr = peekWord(3).asPointer();
        removeSlots(3);
        pokeReference(0, ptr.compareAndSwapReference(off, expectedValue, newValue));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PCMPSWP_REFERENCE_I)
    public static void pcmpswp_reference_i() {
        Reference newValue = peekReference(0);
        Reference expectedValue = peekReference(1);
        int off = peekInt(2);
        Pointer ptr = peekWord(3).asPointer();
        removeSlots(3);
        pokeReference(0, ptr.compareAndSwapReference(off, expectedValue, newValue));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(WCONST_0)
    public static void wconst_0() {
        pushWord(Address.zero());
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETFIELD$word$resolved)
    public static void getfieldWord(int offset) {
        Object object = peekObject(0);
        pokeWord(0, TupleAccess.readWord(object, offset));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETFIELD$word)
    public static void getfieldWord(ResolutionGuard.InPool guard) {
        Object object = peekObject(0);
        pokeWord(0, resolveAndGetFieldWord(guard, object));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE    public static Word resolveAndGetFieldWord(ResolutionGuard.InPool guard, Object object) {
        FieldActor f = Snippets.resolveInstanceFieldForReading(guard);
        if (f.isVolatile()) {
            preVolatileRead();
            Word value = TupleAccess.readWord(object, f.offset());
            postVolatileRead();
            return value;
        } else {
            return TupleAccess.readWord(object, f.offset());
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETSTATIC$word)
    public static void getstaticWord(ResolutionGuard.InPool guard) {
        pushWord(resolveAndGetStaticWord(guard));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE    public static Word resolveAndGetStaticWord(ResolutionGuard.InPool guard) {
        FieldActor f = Snippets.resolveStaticFieldForReading(guard);
        Snippets.makeHolderInitialized(f);
        if (f.isVolatile()) {
            preVolatileRead();
            Word value = TupleAccess.readWord(f.holder().staticTuple(), f.offset());
            postVolatileRead();
            return value;
        } else {
            return TupleAccess.readWord(f.holder().staticTuple(), f.offset());
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(GETSTATIC$word$init)
    public static void getstaticWord(Object staticTuple, int offset) {
        pushWord(TupleAccess.readWord(staticTuple, offset));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTFIELD$word$resolved)
    public static void putfieldWord(int offset) {
        Object object = peekObject(1);
        Word value = peekWord(0);
        removeSlots(2);
        TupleAccess.writeWord(object, offset, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTFIELD$word)
    public static void putfieldWord(ResolutionGuard.InPool guard) {
        Object object = peekObject(1);
        Word value = peekWord(0);
        resolveAndPutFieldWord(guard, object, value);
        removeSlots(2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE
    public static void resolveAndPutFieldWord(ResolutionGuard.InPool guard, Object object, Word value) {
        FieldActor f = Snippets.resolveInstanceFieldForWriting(guard);
        if (f.isVolatile()) {
            preVolatileWrite();
            TupleAccess.writeWord(object, f.offset(), value);
            postVolatileWrite();
        } else {
            TupleAccess.writeWord(object, f.offset(), value);
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTSTATIC$word$init)
    public static void putstaticWord(Object staticTuple, int offset) {
        Word value = popWord();
        TupleAccess.writeWord(staticTuple, offset, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PUTSTATIC$word)
    public static void putstaticWord(ResolutionGuard.InPool guard) {
        resolveAndPutStaticWord(guard, popWord());
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @NEVER_INLINE
    public static void resolveAndPutStaticWord(ResolutionGuard.InPool guard, Word value) {
        FieldActor f = Snippets.resolveStaticFieldForWriting(guard);
        Snippets.makeHolderInitialized(f);
        if (f.isVolatile()) {
            preVolatileWrite();
            TupleAccess.writeWord(f.holder().staticTuple(), f.offset(), value);
            postVolatileWrite();
        } else {
            TupleAccess.writeWord(f.holder().staticTuple(), f.offset(), value);
        }
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(WLOAD)
    public static void wload(int dispToLocalSlot) {
        Word value = getLocalWord(dispToLocalSlot);
        pushWord(value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(WSTORE)
    public static void wstore(int dispToLocalSlot) {
        Word value = popWord();
        setLocalWord(dispToLocalSlot, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(WRETURN)
    public static Word wreturn() {
        return popWord();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(WRETURN$unlockClass)
    public static Word wreturnUnlockClass(Class<?> rcvr) {
        Monitor.noninlineExit(rcvr);
        return popWord();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(WRETURN$unlockReceiver)
    public static Word wreturnUnlockReceiver(int dispToRcvrCopy) {
        Object rcvr = getLocalObject(dispToRcvrCopy);
        Monitor.noninlineExit(rcvr);
        return popWord();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKEVIRTUAL$word)
    public static void invokevirtualWord(ResolutionGuard.InPool guard, int receiverStackIndex) {
        Object receiver = peekObject(receiverStackIndex);
        Address entryPoint = resolveAndSelectVirtualMethod(receiver, guard, receiverStackIndex);
        indirectCallWord(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKEVIRTUAL$word$resolved)
    public static void invokevirtualWord(int vTableIndex, int receiverStackIndex) {
        Object receiver = peekObject(receiverStackIndex);
        Address entryPoint = ObjectAccess.readHub(receiver).getWord(vTableIndex).asAddress();
        indirectCallWord(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKEVIRTUAL$word$instrumented)
    public static void invokevirtualWord(int vTableIndex, int receiverStackIndex, MethodProfile mpo, int mpoIndex) {
        Object receiver = peekObject(receiverStackIndex);
        Address entryPoint = selectVirtualMethod(receiver, vTableIndex, mpo, mpoIndex);
        indirectCallWord(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKEINTERFACE$word)
    public static void invokeinterfaceWord(ResolutionGuard.InPool guard, int receiverStackIndex) {
        Object receiver = peekObject(receiverStackIndex);
        Address entryPoint = resolveAndSelectInterfaceMethod(guard, receiver);
        indirectCallWord(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKEINTERFACE$word$resolved)
    public static void invokeinterfaceWord(InterfaceMethodActor interfaceMethodActor, int receiverStackIndex) {
        Object receiver = peekObject(receiverStackIndex);
        Address entryPoint = Snippets.selectInterfaceMethod(receiver, interfaceMethodActor).asAddress();
        indirectCallWord(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKEINTERFACE$word$instrumented)
    public static void invokeinterfaceWord(InterfaceMethodActor interfaceMethodActor, int receiverStackIndex, MethodProfile mpo, int mpoIndex) {
        Object receiver = peekObject(receiverStackIndex);
        Address entryPoint = Snippets.selectInterfaceMethod(receiver, interfaceMethodActor, mpo, mpoIndex);
        indirectCallWord(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKESPECIAL$word)
    public static void invokespecialWord(ResolutionGuard.InPool guard, int receiverStackIndex) {
        Pointer receiver = peekWord(receiverStackIndex).asPointer();
        nullCheck(receiver);
        indirectCallWord(resolveSpecialMethod(guard), CallEntryPoint.OPTIMIZED_ENTRY_POINT);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKESPECIAL$word$resolved)
    public static void invokespecialWord(int receiverStackIndex) {
        Pointer receiver = peekWord(receiverStackIndex).asPointer();
        nullCheck(receiver);
        directCallWord();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKESTATIC$word)
    public static void invokestaticWord(ResolutionGuard.InPool guard) {
        indirectCallWord(resolveStaticMethod(guard), CallEntryPoint.OPTIMIZED_ENTRY_POINT);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKESTATIC$word$init)
    public static void invokestaticWord() {
        directCallWord();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PGET_WORD)
    public static void pget_word() {
        int index = peekInt(0);
        int disp = peekInt(1);
        Pointer ptr = peekWord(2).asPointer();
        removeSlots(2);
        pokeWord(0, ptr.getWord(disp, index));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PSET_WORD)
    public static void pset_word() {
        Word value = peekWord(0);
        int index = peekInt(1);
        int disp = peekInt(2);
        Pointer ptr = peekWord(3).asPointer();
        removeSlots(4);
        ptr.setWord(disp, index, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PREAD_WORD)
    public static void pread_word() {
        Offset off = peekWord(0).asOffset();
        Pointer ptr = peekWord(1).asPointer();
        removeSlots(1);
        pokeWord(0, ptr.readWord(off));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PWRITE_WORD)
    public static void pwrite_word() {
        Pointer ptr = peekWord(2).asPointer();
        Offset off = peekWord(1).asOffset();
        Word value = peekWord(0);
        removeSlots(3);
        ptr.writeWord(off, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PREAD_WORD_I)
    public static void pread_word_i() {
        int off = peekInt(0);
        Pointer ptr = peekWord(1).asPointer();
        removeSlots(1);
        pokeWord(0, ptr.readWord(off));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PWRITE_WORD_I)
    public static void pwrite_word_i() {
        Pointer ptr = peekWord(2).asPointer();
        int off = peekInt(1);
        Word value = peekWord(0);
        removeSlots(3);
        ptr.writeWord(off, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PCMPSWP_WORD)
    public static void pcmpswp_word() {
        Word newValue = peekWord(0);
        Word expectedValue = peekWord(1);
        Offset off = peekWord(2).asOffset();
        Pointer ptr = peekWord(3).asPointer();
        removeSlots(3);
        pokeWord(0, ptr.compareAndSwapWord(off, expectedValue, newValue));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(PCMPSWP_WORD_I)
    public static void pcmpswp_word_i() {
        Word newValue = peekWord(0);
        Word expectedValue = peekWord(1);
        int off = peekInt(2);
        Pointer ptr = peekWord(3).asPointer();
        removeSlots(3);
        pokeWord(0, ptr.compareAndSwapWord(off, expectedValue, newValue));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(RETURN)
    public static void vreturn() {
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(RETURN$unlockClass)
    public static void vreturnUnlockClass(Class<?> rcvr) {
        Monitor.noninlineExit(rcvr);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(RETURN$unlockReceiver)
    public static void vreturnUnlockReceiver(int dispToRcvrCopy) {
        Object rcvr = getLocalObject(dispToRcvrCopy);
        Monitor.noninlineExit(rcvr);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKEVIRTUAL$void)
    public static void invokevirtualVoid(ResolutionGuard.InPool guard, int receiverStackIndex) {
        Object receiver = peekObject(receiverStackIndex);
        Address entryPoint = resolveAndSelectVirtualMethod(receiver, guard, receiverStackIndex);
        indirectCallVoid(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKEVIRTUAL$void$resolved)
    public static void invokevirtualVoid(int vTableIndex, int receiverStackIndex) {
        Object receiver = peekObject(receiverStackIndex);
        Address entryPoint = ObjectAccess.readHub(receiver).getWord(vTableIndex).asAddress();
        indirectCallVoid(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKEVIRTUAL$void$instrumented)
    public static void invokevirtualVoid(int vTableIndex, int receiverStackIndex, MethodProfile mpo, int mpoIndex) {
        Object receiver = peekObject(receiverStackIndex);
        Address entryPoint = selectVirtualMethod(receiver, vTableIndex, mpo, mpoIndex);
        indirectCallVoid(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKEINTERFACE$void)
    public static void invokeinterfaceVoid(ResolutionGuard.InPool guard, int receiverStackIndex) {
        Object receiver = peekObject(receiverStackIndex);
        Address entryPoint = resolveAndSelectInterfaceMethod(guard, receiver);
        indirectCallVoid(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKEINTERFACE$void$resolved)
    public static void invokeinterfaceVoid(InterfaceMethodActor interfaceMethodActor, int receiverStackIndex) {
        Object receiver = peekObject(receiverStackIndex);
        Address entryPoint = Snippets.selectInterfaceMethod(receiver, interfaceMethodActor).asAddress();
        indirectCallVoid(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKEINTERFACE$void$instrumented)
    public static void invokeinterfaceVoid(InterfaceMethodActor interfaceMethodActor, int receiverStackIndex, MethodProfile mpo, int mpoIndex) {
        Object receiver = peekObject(receiverStackIndex);
        Address entryPoint = Snippets.selectInterfaceMethod(receiver, interfaceMethodActor, mpo, mpoIndex);
        indirectCallVoid(entryPoint, CallEntryPoint.VTABLE_ENTRY_POINT, receiver);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKESPECIAL$void)
    public static void invokespecialVoid(ResolutionGuard.InPool guard, int receiverStackIndex) {
        Pointer receiver = peekWord(receiverStackIndex).asPointer();
        nullCheck(receiver);
        indirectCallVoid(resolveSpecialMethod(guard), CallEntryPoint.OPTIMIZED_ENTRY_POINT);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKESPECIAL$void$resolved)
    public static void invokespecialVoid(int receiverStackIndex) {
        Pointer receiver = peekWord(receiverStackIndex).asPointer();
        nullCheck(receiver);
        directCallVoid();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKESTATIC$void)
    public static void invokestaticVoid(ResolutionGuard.InPool guard) {
        indirectCallVoid(resolveStaticMethod(guard), CallEntryPoint.OPTIMIZED_ENTRY_POINT);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INVOKESTATIC$void$init)
    public static void invokestaticVoid() {
        directCallVoid();
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(WDIV)
    public static void wdiv() {
        Address value2 = peekWord(0).asAddress();
        Address value1 = peekWord(1).asAddress();
        removeSlots(1);
        pokeWord(0, value1.dividedBy(value2));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(WDIVI)
    public static void wdivi() {
        int value2 = peekInt(0);
        Address value1 = peekWord(1).asAddress();
        removeSlots(1);
        pokeWord(0, value1.dividedBy(value2));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(WREM)
    public static void wrem() {
        Address value2 = peekWord(0).asAddress();
        Address value1 = peekWord(1).asAddress();
        removeSlots(1);
        pokeWord(0, value1.remainder(value2));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(WREMI)
    public static void wremi() {
        int value2 = peekInt(0);
        Address value1 = peekWord(1).asAddress();
        removeSlots(1);
        pokeInt(0, value1.remainder(value2));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(IINC)
    public static void iinc(int dispToLocalSlot, int increment) {
        int value = getLocalInt(dispToLocalSlot);
        setLocalInt(dispToLocalSlot, value  + increment);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(MOV_F2I)
    public static void mov_f2i() {
        float value = peekFloat(0);
        pokeInt(0, Intrinsics.floatToInt(value));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(MOV_I2F)
    public static void mov_i2f() {
        int value = peekInt(0);
        pokeFloat(0, Intrinsics.intToFloat(value));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(MOV_D2L)
    public static void mov_d2l() {
        double value = peekDouble(0);
        pokeLong(0, Intrinsics.doubleToLong(value));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(MOV_L2D)
    public static void mov_l2d() {
        long value = peekLong(0);
        pokeDouble(0, Intrinsics.longToDouble(value));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(BIPUSH)
    public static void bipush(byte value) {
        pushInt(value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(SIPUSH)
    public static void sipush(short value) {
        pushInt(value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(POP)
    public static void pop() {
        removeSlots(1);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(POP2)
    public static void pop2() {
        removeSlots(2);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(NEW)
    public static void new_(ResolutionGuard arg) {
        Object object = resolveClassForNewAndCreate(arg);
        pushObject(object);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(NEW$init)
    public static void new_(ClassActor arg) {
        Object object = createTupleOrHybrid(arg);
        pushObject(object);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(NEWARRAY)
    public static void newarray(Kind<?> kind) {
        int length = peekInt(0);
        Object array = createPrimitiveArray(kind, length);
        pokeObject(0, array);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(ANEWARRAY)
    public static void anewarray(ResolutionGuard guard) {
        ArrayClassActor<?> arrayClassActor = UnsafeCast.asArrayClassActor(Snippets.resolveArrayClass(guard));
        int length = peekInt(0);
        Object array = T1XRuntime.createReferenceArray(arrayClassActor, length);
        pokeObject(0, array);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(ANEWARRAY$resolved)
    public static void anewarray(ArrayClassActor<?> arrayClassActor) {
        int length = peekInt(0);
        Object array = T1XRuntime.createReferenceArray(arrayClassActor, length);
        pokeObject(0, array);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(MULTIANEWARRAY)
    public static void multianewarray(ResolutionGuard guard, int[] lengthsShared) {
        ClassActor arrayClassActor = Snippets.resolveClass(guard);
        // Need to use an unsafe cast to remove the checkcast inserted by javac as that
        // causes this template to have a reference literal in its compiled form.
        int[] lengths = UnsafeCast.asIntArray(cloneArray(lengthsShared));
        int numberOfDimensions = lengths.length;

        for (int i = 1; i <= numberOfDimensions; i++) {
            int length = popInt();
            checkArrayDimension(length);
            ArrayAccess.setInt(lengths, numberOfDimensions - i, length);
        }

        Object array = Snippets.createMultiReferenceArray(arrayClassActor, lengths);
        pushObject(array);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(MULTIANEWARRAY$resolved)
    public static void multianewarray(ArrayClassActor<?> arrayClassActor, int[] lengthsShared) {
        // Need to use an unsafe cast to remove the checkcast inserted by javac as that
        // causes this template to have a reference literal in its compiled form.
        int[] lengths = UnsafeCast.asIntArray(cloneArray(lengthsShared));
        int numberOfDimensions = lengths.length;

        for (int i = 1; i <= numberOfDimensions; i++) {
            int length = popInt();
            checkArrayDimension(length);
            ArrayAccess.setInt(lengths, numberOfDimensions - i, length);
        }

        Object array = Snippets.createMultiReferenceArray(arrayClassActor, lengths);
        pushObject(array);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(CHECKCAST)
    public static void checkcast(ResolutionGuard arg) {
        Object value = peekObject(0);
        resolveAndCheckcast(arg, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(CHECKCAST$resolved)
    public static void checkcast(ClassActor arg) {
        Object value = peekObject(0);
        Snippets.checkCast(arg, value);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(ARRAYLENGTH)
    public static void arraylength() {
        Object array = peekObject(0);
        int length = ArrayAccess.readArrayLength(array);
        pokeInt(0, length);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(ATHROW)
    public static void athrow() {
        Object object = peekObject(0);
        Throw.raise(object);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(MONITORENTER)
    public static void monitorenter() {
        Object object = peekObject(0);
        T1XRuntime.monitorenter(object);
        removeSlots(1);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(MONITOREXIT)
    public static void monitorexit() {
        Object object = peekObject(0);
        T1XRuntime.monitorexit(object);
        removeSlots(1);
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INSTANCEOF)
    public static void instanceof_(ResolutionGuard guard) {
        ClassActor classActor = Snippets.resolveClass(guard);
        Object object = peekObject(0);
        pokeInt(0, UnsafeCast.asByte(Snippets.instanceOf(classActor, object)));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(INSTANCEOF$resolved)
    public static void instanceof_(ClassActor classActor) {
        Object object = peekObject(0);
        pokeInt(0, UnsafeCast.asByte(Snippets.instanceOf(classActor, object)));
    }

    // GENERATED -- EDIT AND RUN T1XTemplateGenerator.main() TO MODIFY
    @T1X_TEMPLATE(RETURN$registerFinalizer)
    public static void vreturnRegisterFinalizer(int dispToRcvrCopy) {
        Object rcvr = getLocalObject(dispToRcvrCopy);
        if (ObjectAccess.readClassActor(rcvr).hasFinalizer()) {
            SpecialReferenceManager.registerFinalizee(rcvr);
        }
    }



}
