/*
 * Copyright (c) 2007, 2009, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.vm.compiler.snippet;

import com.sun.max.annotate.*;
import com.sun.max.vm.monitor.*;

/**
 * Snippets for monitorentry/exit that delegate to the monitor scheme.
 *
 * @author Mick Jordan
 * @author Bernd Mathiske
 */
public abstract class MonitorSnippet extends Snippet {

    private MonitorSnippet() {
        super();
    }

    public static final class MonitorEnter extends MonitorSnippet {
        @SNIPPET
        @INLINE
        public static void monitorEnter(Object object) {
            Monitor.enter(object);
        }

        public static final MonitorEnter SNIPPET = new MonitorEnter();
    }

    public static final class MonitorExit extends MonitorSnippet {
        @SNIPPET
        @INLINE
        public static void monitorExit(Object object) {
            Monitor.exit(object);
        }

        public static final MonitorExit SNIPPET = new MonitorExit();
    }

}
