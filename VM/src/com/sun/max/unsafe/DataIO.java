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
package com.sun.max.unsafe;

import java.nio.*;

/**
 * Buffered reading/writing of bytes from/to a source/destination that can be identified by an {@link Address}.
 *
 * @author Bernd Mathiske
 * @author Michael Van De Vanter
 */
public interface DataIO {

    /**
     * Reads bytes from an address into a given byte buffer.
     *
     * Precondition:
     * {@code buffer != null && offset >= 0 && offset < buffer.capacity() && length >= 0 && offset + length <= buffer.capacity()}
     *
     * @param src the address from which reading should start
     * @param dst the buffer into which the bytes are read
     * @param dstOffset the offset in {@code dst} at which the bytes are read
     * @param length the maximum number of bytes to be read
     * @return the number of bytes read into {@code dst}
     *
     * @throws DataIOError if some IO error occurs
     * @throws IndexOutOfBoundsException if {@code offset} is negative, {@code length} is negative, or
     *             {@code length > buffer.limit() - offset}
     */
    int read(Address src, ByteBuffer dst, int dstOffset, int length) throws DataIOError, IndexOutOfBoundsException;

    /**
     * Writes bytes from a given byte buffer to a given address.
     *
     * Precondition:
     * {@code buffer != null && offset >= 0 && offset < buffer.capacity() && length >= 0 && offset + length <= buffer.capacity()}
     *
     * @param src the buffer from which the bytes are written
     * @param srcOffset the offset in {@code src} from which the bytes are written
     * @param length the maximum number of bytes to be written
     * @param dst the address at which writing should start
     * @return the number of bytes written to {@code dst}
     *
     * @throws DataIOError if some IO error occurs
     * @throws IndexOutOfBoundsException if {@code srcOffset} is negative, {@code length} is negative, or
     *             {@code length > src.limit() - srcOffset}
     */
    int write(ByteBuffer src, int srcOffset, int length, Address dst) throws DataIOError, IndexOutOfBoundsException;

    public static class Static {

        /**
         * Fills a buffer by reading bytes from a source.
         *
         * @param dataIO the source of data to be read
         * @param src the location in the source where reading should start
         * @param dst the buffer to be filled with the data
         */
        public static void readFully(DataIO dataIO, Address src, ByteBuffer dst) {
            final int length = dst.limit();
            int n = 0;
            assert dst.position() == 0;
            while (n < length) {
                final int count = dataIO.read(src.plus(n), dst, n, length - n);
                if (count <= 0) {
                    throw new DataIOError(src, (length - n) + " of " + length + " bytes unread");
                }
                n += count;
                dst.position(0);
            }
        }

        /**
         * Reads bytes from a source.
         *
         * @param dataIO the source of data to be read
         * @param src the location in the source where reading should start
         * @param length the total number of bytes to be read
         * @return the bytes read from the source.
         */
        public static byte[] readFully(DataIO dataIO, Address src, int length) {
            final ByteBuffer buffer = ByteBuffer.wrap(new byte[length]);
            readFully(dataIO, src, buffer);
            return buffer.array();
        }

        /**
         * Checks the preconditions related to the destination buffer for {@link DataIO#read(Address, ByteBuffer, int, int)}.
         */
        public static void checkRead(ByteBuffer dst, int dstOffset, int length) {
            if (dst == null) {
                throw new NullPointerException();
            } else if (dstOffset < 0 || length < 0 || length > dst.limit() - dstOffset) {
                throw new IndexOutOfBoundsException();
            }
        }

        /**
         * Checks the preconditions related to the source buffer for {@link DataIO#write(ByteBuffer, int, int, Address)}.
         */
        public static void checkWrite(ByteBuffer src, int srcOffset, int length) {
            if ((srcOffset < 0) || (srcOffset > src.limit()) || (length < 0) ||
                            ((srcOffset + length) > src.limit()) || ((srcOffset + length) < 0)) {
                throw new IndexOutOfBoundsException();
            }
        }
    }
}
