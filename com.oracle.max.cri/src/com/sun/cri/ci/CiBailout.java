/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.cri.ci;

import java.util.*;

/**
 * {@code CiBailout} is thrown when the compiler refuses to compile a method because of problems with the method.
 * e.g. bytecode wouldn't verify, too big, JSR/ret too complicated, etc. This exception is <i>not</i>
 * meant to indicate problems with the compiler itself.
 */
public class CiBailout extends RuntimeException {

    public static final long serialVersionUID = 8974598793458772L;

    /**
     * Create a new {@code CiBailout}.
     * @param reason a message indicating the reason
     */
    public CiBailout(String reason) {
        super(reason);
    }

    /**
     * Create a new {@code CiBailout}.
     * @param reason a message indicating the reason with a String.format - syntax
     * @param args parameters to the formatter
     */
    public CiBailout(String format, Object... args) {
        this(String.format(Locale.ENGLISH, format, args));
    }

    /**
     * Create a new {@code CiBailout} t due to an internal exception being thrown.
     * @param reason a message indicating the reason
     * @param cause the throwable that was the cause of the bailout
     */
    public CiBailout(String reason, Throwable cause) {
        super(reason);
        initCause(cause);
    }
}
