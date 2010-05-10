/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.max.tele.debug.guestvm.xen;

import java.nio.*;

import com.sun.max.collect.*;
import com.sun.max.memory.*;
import com.sun.max.tele.MaxWatchpoint.*;
import com.sun.max.tele.debug.*;
import com.sun.max.unsafe.*;

/**
 * This class encapsulates all interaction with the Xen db communication channel.
 * A variety of channel implementations are possible, currently there are two:
 * <ul>
 * <li>Direct communication to the target domain via the {@code db-front/db-back} split device driver, using the {@code guk_db} library.
 * Note that this requires that the Inspector be run in the privileged dom0 in order to access the target domain.</li>
 * <li>Indirect communication to the {@code db-front/db-back} split device driver via a TCP connection to an agent running domU.
 * This allows the Inspector to run in an unprivileged domain (domU).
 * </ul>
 * The choice of which mechanism to use is based on the value of the {@code max.ins.guestvm.channel} property.
 *
 * @author Mick Jordan
 *
 */
public final class GuestVMXenDBChannel {
    private static final String CHANNEL_PROPERTY = "max.ins.guestvm.channel";
    private static GuestVMXenTeleDomain teleDomain;
    private static GuestVMXenDBChannelProtocol channelProtocol;
    private static int maxByteBufferSize;

    public static synchronized void attach(GuestVMXenTeleDomain teleDomain, int domId) {
        GuestVMXenDBChannel.teleDomain = teleDomain;
        String channelType = System.getProperty(CHANNEL_PROPERTY);
        if (channelType == null) {
            channelType = "ring.direct";
        }
        if (channelType.equals("ring.direct")) {
            channelProtocol = new GuestVMXenDBNativeChannelProtocol();
        } else if (channelType.startsWith("ring.tcp")) {
            final int sep = channelType.indexOf(',');
            if (sep > 0) {
                channelProtocol = new GuestVMXenDBTCPNativeChannelProtocol(channelType.substring(sep + 1));
            } else {
                throw new IllegalArgumentException("host/port not specified with " + CHANNEL_PROPERTY);
            }
        } else if (channelType.startsWith("gdbsx.tcp")) {
            throw new IllegalArgumentException("gdbsx.tcp is not implemented");
        }
        channelProtocol.attach(domId);
        maxByteBufferSize = channelProtocol.maxByteBufferSize();
    }

    public static synchronized Pointer getBootHeapStart() {
        return Pointer.fromLong(channelProtocol.getBootHeapStart());
    }

    public static synchronized void setTransportDebugLevel(int level) {
        channelProtocol.setTransportDebugLevel(level);
    }

    private static int readBytes0(long src, ByteBuffer dst, int dstOffset, int length) {
        assert dst.limit() - dstOffset >= length;
        if (dst.isDirect()) {
            return channelProtocol.readBytes(src, dst, true, dstOffset, length);
        }
        assert dst.array() != null;
        return channelProtocol.readBytes(src, dst.array(), false, dst.arrayOffset() + dstOffset, length);
    }

    public static synchronized int readBytes(Address src, ByteBuffer dst, int dstOffset, int length) {
        int lengthLeft = length;
        int localOffset = dstOffset;
        long localAddress = src.toLong();
        while (lengthLeft > 0) {
            final int toDo = lengthLeft > maxByteBufferSize ? maxByteBufferSize : lengthLeft;
            final int r = readBytes0(localAddress, dst, localOffset, toDo);
            if (r != toDo) {
                return -1;
            }
            lengthLeft -= toDo;
            localOffset += toDo;
            localAddress += toDo;
        }
        return length;
    }

    private static int writeBytes0(long dst, ByteBuffer src, int srcOffset, int length) {
        assert src.limit() - srcOffset >= length;
        if (src.isDirect()) {
            return channelProtocol.writeBytes(dst, src, true, srcOffset, length);
        }
        assert src.array() != null;
        return channelProtocol.writeBytes(dst, src.array(), false, src.arrayOffset() + srcOffset, length);

    }

    public static synchronized int writeBytes(ByteBuffer buffer, int offset, int length, Address address) {
        int lengthLeft = length;
        int localOffset = offset;
        long localAddress = address.toLong();
        while (lengthLeft > 0) {
            final int toDo = lengthLeft > maxByteBufferSize ? maxByteBufferSize : lengthLeft;
            final int r = writeBytes0(localAddress, buffer, localOffset, toDo);
            if (r != toDo) {
                return -1;
            }
            lengthLeft -= toDo;
            localOffset += toDo;
            localAddress += toDo;
        }
        return length;
    }

    public static synchronized void gatherThreads(AppendableSequence<TeleNativeThread> threads, long threadLocalsList, long primordialThreadLocals) {
        channelProtocol.gatherThreads(teleDomain, threads, threadLocalsList, primordialThreadLocals);
    }

    public static synchronized int resume(int domainId) {
        return channelProtocol.resume();
    }

    public static synchronized int readByte(long domainId, long address) {
        return channelProtocol.readByte(address);
    }

    public static synchronized long readInt(int domainId, long address) {
        return channelProtocol.readInt(address);
    }

    public static synchronized int readShort(int domainId, long address) {
        return channelProtocol.readShort(address);
    }

    public static synchronized boolean writeByte(int domainId, long address, byte value) {
        return channelProtocol.writeByte(address, value);
    }

    public static synchronized int setInstructionPointer(int threadId, long ip) {
        return channelProtocol.setInstructionPointer(threadId, ip);
    }

    public static synchronized boolean readRegisters(int threadId,
                    byte[] integerRegisters, int integerRegistersSize,
                    byte[] floatingPointRegisters, int floatingPointRegistersSize,
                    byte[] stateRegisters, int stateRegistersSize) {
        return channelProtocol.readRegisters(threadId, integerRegisters, integerRegistersSize, floatingPointRegisters, floatingPointRegistersSize, stateRegisters, stateRegistersSize);
    }

    public static synchronized boolean singleStep(int threadId) {
        return channelProtocol.singleStep(threadId);
    }

    /**
     * This is not synchronized because it is used to interrupt a resume that already holds the lock.
     * @return
     */
    public static boolean suspendAll() {
        return channelProtocol.suspendAll();
    }

    public static synchronized boolean suspend(int threadId) {
        return channelProtocol.suspend(threadId);
    }

    public static synchronized boolean activateWatchpoint(int domainId, TeleWatchpoint teleWatchpoint) {
        final WatchpointSettings settings = teleWatchpoint.getSettings();
        return channelProtocol.activateWatchpoint(teleWatchpoint.start().toLong(), teleWatchpoint.size().toLong(), true, settings.trapOnRead, settings.trapOnWrite, settings.trapOnExec);
    }

    public static synchronized boolean deactivateWatchpoint(int domainId, MemoryRegion memoryRegion) {
        return channelProtocol.deactivateWatchpoint(memoryRegion.start().toLong(), memoryRegion.size().toLong());
    }

    public static synchronized long readWatchpointAddress(int domainId) {
        return channelProtocol.readWatchpointAddress();
    }

    public static synchronized int readWatchpointAccessCode(int domainId) {
        return channelProtocol.readWatchpointAccessCode();
    }

}
