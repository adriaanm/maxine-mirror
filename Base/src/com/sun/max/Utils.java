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
package com.sun.max;

import java.io.*;
import java.util.*;

import com.sun.max.program.*;

/**
 * Miscellaneous utility methods.
 *
 * @author Doug Simon
 */
public final class Utils {

    private Utils() {
    }

    /**
     * Gets the first element in a list.
     */
    public static <T> T first(List<T> list) {
        if (list instanceof Queue) {
            Queue queue = (Queue) list;
            Class<T> type = null;
            T t = cast(type, queue.peek());
            return t;
        }
        return list.get(0);
    }

    /**
     * Gets the last element in a list.
     */
    public static <T> T last(List<T> list) {
        if (list instanceof Deque) {
            Deque deque = (Deque) list;
            Class<T> type = null;
            T t = cast(type, deque.getLast());
            return t;
        }
        return list.get(list.size() - 1);
    }

    /**
     * Creates an array of a generic type.
     *
     * @param <T> the type of the array elements
     * @param length the length of the array to create
     * @return a generic array of type {@code <T>} with a length of {@code length}
     */
    public static <T> T[] newArray(int length) {
        return cast(new Object[length]);
    }

    /**
     * Creates an array of a generic type.
     *
     * @param <T> the type of the array elements
     * @param type explicit class of {@code <T[]>} needed due to javac bug (see {@link #cast(Class, Object)})
     * @param length the length of the array to create
     * @return a generic array of type {@code <T>} with a length of {@code length}
     */
    public static <T> T[] newArray(Class<T[]> type, int length) {
        return cast(type, new Object[length]);
    }

    /**
     * Creates an array of a generic type and {@linkplain Arrays#asList(Object...) wraps} it with
     * a fixed-size list.
     *
     * @param <T> the type of the array elements
     * @param length the length of the array to create
     * @return a fixed-size list backed by a new generic array of type {@code <T>} with a length of {@code length}
     */
    public static <T> List<T> newArrayAsList(int length) {
        Class<T[]> type = null;
        T[] array = newArray(type, length);
        return Arrays.asList(array);
    }

    /**
     * Returns the index in {@code list} of the first occurrence identical to {@code value}, or -1 if
     * {@code list} does not contain {@code value}. More formally, returns the lowest index
     * {@code i} such that {@code (list.get(i) == value)}, or -1 if there is no such index.
     */
    public static int indexOfIdentical(List list, Object value) {
        int i = 0;
        for (Object element : list) {
            if (element == value) {
                return i;
            }
            ++i;
        }
        return -1;
    }

    /**
     * Returns the index in {@code array} of the first occurrence identical to {@code value}, or -1 if
     * {@code array} does not contain {@code value}. More formally, returns the lowest index
     * {@code i} such that {@code (array[i] == value)}, or -1 if there is no such index.
     */
    public static int indexOfIdentical(Object[] array, Object value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == value) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Statically cast an object to an arbitrary type. The cast is still dynamically checked.
     *
     * This alternate version of {@link #cast(Object)} is required due to bug
     * <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6302954">6302954</a> in javac.
     *
     * The pattern for calling this method is:
     * <pre>
     *     Foo foo = ...;
     *     Class<Foo<T>> type = null;
     *     Foo<T> genericFoo = uncheckedCast(type, foo);
     * </pre>
     *
     */
    @SuppressWarnings("unchecked")
    public static <T> T cast(Class<T> type, Object object) {
        return (T) object;
    }

    /**
     * Statically cast an object to an arbitrary type. The cast is still dynamically checked.
     */
    @SuppressWarnings("unchecked")
    public static <T> T cast(Object object) {
        return (T) object;
    }

    /**
     * Concatenates two arrays.
     *
     * @param <T> the type of elements in the source arrays
     * @param head the prefix of the result array
     * @param tail the elements to be concatenated to {@code head}
     * @return the result of concatenating {@code tail} to the end of {@code head}
     */
    public static <T> T[] concat(T[] head, T... tail) {
        T[] result = Arrays.copyOf(head, head.length + tail.length);
        System.arraycopy(tail, 0, result, head.length, tail.length);
        return result;
    }

    /**
     * Concatenates two arrays.
     *
     * @param <T> the type of elements in the source arrays
     * @param tail the elements to be concatenated to the end of {@code head}
     * @param head the prefix of the result array
     * @return the result of concatenating {@code tail} to the end of {@code head}
     */
    public static <T> T[] prepend(T[] tail, T... head) {
        return concat(head, tail);
    }

    /**
     * Returns a string representation of the contents of the specified array.
     * If the array contains other arrays as elements, they are converted to
     * strings by the {@link Object#toString} method inherited from
     * <tt>Object</tt>, which describes their <i>identities</i> rather than
     * their contents.
     *
     * @param array     the array whose string representation to return
     * @param separator the separator to use
     * @return a string representation of <tt>array</tt>
     * @throws NullPointerException if {@code array} or {@code separator} is null
     */
    public static <T> String toString(T[] array, String separator) {
        if (array == null || separator == null) {
            throw new NullPointerException();
        }
        if (array.length == 0) {
            return "";
        }

        final StringBuilder builder = new StringBuilder();
        builder.append(array[0]);

        for (int i = 1; i < array.length; i++) {
            builder.append(separator);
            builder.append(array[i]);
        }

        return builder.toString();
    }

    /**
     * Tests a given exception to see if it is an instance of a given exception type, casting and throwing it if so.
     * Otherwise if the exception is an unchecked exception (i.e. an instance of a {@link RuntimeException} or
     * {@link Error}) then it is cast to the appropriate unchecked exception type and thrown. Otherwise, it is wrapped
     * in a {@link ProgramError} and thrown.
     *
     * This method declares a return type simply so that a call to this method can be the expression to a throw
     * instruction.
     */
    public static <T extends Throwable> T cast(Class<T> exceptionType,  Throwable exception) throws T {
        if (exceptionType.isInstance(exception)) {
            throw exceptionType.cast(exception);
        }
        if (exception instanceof Error) {
            throw (Error) exception;
        }
        if (exception instanceof RuntimeException) {
            throw (RuntimeException) exception;
        }
        throw ProgramError.unexpected(exception);
    }

    /**
     * Gets the stack trace for a given exception as a string.
     */
    public static String stackTraceAsString(Throwable throwable) {
        final StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.getBuffer().toString();
    }
}
