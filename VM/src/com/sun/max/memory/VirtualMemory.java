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

package com.sun.max.memory;

import java.io.*;

import com.sun.max.annotate.*;
import com.sun.max.platform.*;
import com.sun.max.unsafe.*;

/**
 * Access to the virtual memory facilities of the underlying operating system.
 * Provides methods to allocate quantities of memory that are expected to be
 * multiples of page size (or may be rounded up). To enable possible optimizations
 * in virtual memory management, memory is classified into different different uses
 * by the {@link Type} enum.
 *
 * Also provides the ability to map files into virtual memory and to change page protection.
 *
 * @author Bernd Mathiske
 * @author Mick Jordan
 */
public final class VirtualMemory {

    public enum Type {
        HEAP,   // for the garbage collected heap
        STACK,  // for thread stacks
        CODE,   // for compiled code
        DATA    // for miscellaneous data
    }

    private VirtualMemory() {
    }

    /**
     * Allocates virtual memory of a given type.
     *
     * @param size the amount requested
     * @param type the type of memory requested
     * @return the address of the allocated memory or {@link Pointer#zero()} if the allocation failed
     */
    public static Pointer allocate(Size size, Type type) {
        return virtualMemory_allocate(size, type.ordinal());
    }

    /**
     * Deallocates virtual memory of a given type.
     *
     * @param pointer base address of previously allocated memory
     * @param size size of previously allocated memory
     * @param type type of memory
     * @return {@link Address#zero()} if failed, {@code pointer} otherwise.
     */
    public static Address deallocate(Address pointer, Size size, Type type) {
        return virtualMemory_deallocate(pointer, size, type.ordinal());
    }

    /**
     * Allocates virtual memory at a fixed address.
     * Evidently the caller of this method must know that the virtual memory
     * at the given address is available for allocation.
     *
     * @param pointer page aligned address at which to allocate the virtual memory
     * @param size the size requested
     * @return true if the memory was allocated, false otherwise
     */
    public static boolean allocateAtFixedAddress(Address address, Size size, Type type) {
        return virtualMemory_allocateAtFixedAddress(address, size, type.ordinal());
    }

    /**
     * Allocates virtual memory at a fixed address, which must be page aligned.
     * Evidently the caller of this method must know that the virtual memory
     * at the given address is available for allocation.
     *
     * @param pointer page aligned address at which to allocate the virtual memory
     * @param size the size requested
     * @type type of memory
     * @return true if the memory was allocated, false otherwise
     */
    public static boolean allocatePageAlignedAtFixedAddress(Address pointer, Size size, Type type) {
        if (!pointer.isAligned(Platform.platform().pageSize)) {
            throw new IllegalArgumentException("start address of the request memory must be page aligned ");
        }
        return virtualMemory_allocateAtFixedAddress(pointer, size, type.ordinal());
    }

    /**
     * Allocates virtual memory in the address range available using 31 bits of addressing.
     * I.e., in the first 2GB of memory. This method may not be implemented on all platforms.
     *
     * @param size the size requested
     * @type type of memory
     * @return the address of the allocated memory or {@link Pointer#zero()} if unsuccessful
     */
    public static Pointer allocateIn31BitSpace(Size size, Type type) {
        return virtualMemory_allocateIn31BitSpace(size, type.ordinal());
    }

    /**
     * Generic virtual memory allocator.
     * @param address reserve virtual memory at specified address if not zero, otherwise let the underlying OS decide where to allocate
     * @param size
     * @param reserveSwap reserve swap space if set to true.
     * @param protNone no protection is set if true, all are set otherwise
     * @return address to the allocated chunk of virtual memory, zero otherwise if allocation failed for any reason.
     */
    @C_FUNCTION
    private static native Pointer virtualMemory_allocatePrivateAnon(Address address, Size size, boolean reserveSwap, boolean protNone, int type);

    @C_FUNCTION
    private static native boolean virtualMemory_allocateAtFixedAddress(Address address, Size size, int type);

    @C_FUNCTION
    private static native Pointer virtualMemory_allocateIn31BitSpace(Size size, int type);

    @C_FUNCTION
    private static native Pointer virtualMemory_allocate(Size size, int type);

    @C_FUNCTION
    private static native Pointer virtualMemory_deallocate(Address start, Size size, int type);


    /**
     * Allocates virtual memory that is not backed by swap space.
     *
     * @param size the size requested
     * @type type of memory
     * @return the address of the allocated memory or zero if unsuccessful
     */
    public static Pointer allocateNoSwap(Size size, Type type) {
        return virtualMemory_allocatePrivateAnon(Address.zero(), size, false, false, type.ordinal());
    }

    public static Pointer reserveMemory(Address address, Size size, Type type) {
        return virtualMemory_allocatePrivateAnon(address, size, false, true, type.ordinal());
    }

    public static boolean commitMemory(Address address, Size size, Type type) {
        if (address.isZero()) {
            return false;
        }
        Pointer committed = virtualMemory_allocatePrivateAnon(address, size, true, false, type.ordinal());
        return !committed.isZero();
    }

    public static boolean uncommitMemory(Address address, Size size, Type type) {
        if (address.isZero()) {
            return false;
        }
        // Remap previously mapped space so the new space isn't backed with swap space and has not protection set (i.e., prevents any access).
        Pointer uncommitted = virtualMemory_allocatePrivateAnon(address, size, false, true, type.ordinal());
        return !uncommitted.isZero();
    }

    /* Page protection methods */

    /**
     * Sets access protection for a number of memory pages such that any access (read or write) to them causes a trap.
     *
     * @param address an address denoting the first page. This value must be aligned to the
     *            underlying platform's {@linkplain Platform#pageSize() page size}.
     * @param count the number of pages to protect
     */
    @INLINE
    public static void protectPages(Address address, int count) {
        virtualMemory_protectPages(address, count);
    }

    @C_FUNCTION
    private static native void virtualMemory_protectPages(Address address, int count);

    @C_FUNCTION
    public static native Address virtualMemory_pageAlign(Address address);

    /* File mapping methods */

    /**
     * Maps an open file into virtual memory.
     *
     * @param size
     * @param fileDescriptor
     * @param fileOffset
     * @return
     * @throws IOException
     */
    public static Pointer mapFile(Size size, FileDescriptor fileDescriptor, Address fileOffset) throws IOException {
        final Integer fd = (Integer) WithoutAccessCheck.getInstanceField(fileDescriptor, "fd");
        return Pointer.fromLong(virtualMemory_mapFile(size.toLong(), fd, fileOffset.toLong()));
    }

    /**
     * Maps an open file into virtual memory restricted to the address range available in 31 bits, i.e. up to 2GB.
     * This is only available on Linux.
     *
     * @param size
     * @param fileDescriptor
     * @param fileOffset
     * @return
     * @throws IOException
     */
    public static Pointer mapFileIn31BitSpace(int size, FileDescriptor fileDescriptor, Address fileOffset) throws IOException {
        if (Platform.platform().operatingSystem != OperatingSystem.LINUX) {
            throw new UnsupportedOperationException();
        }
        final Integer fd = (Integer) WithoutAccessCheck.getInstanceField(fileDescriptor, "fd");
        return Pointer.fromLong(virtualMemory_mapFileIn31BitSpace(size, fd, fileOffset.toLong()));
    }

    /* These are JNI functions because they may block */

    private static native long virtualMemory_mapFile(long size, int fd, long fileOffset);

    private static native long virtualMemory_mapFileIn31BitSpace(int size, int fd, long fileOffset);

}
