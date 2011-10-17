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
package com.oracle.max.graal.hotspot.logging;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * Scoped logging class used to display the call hierarchy of VMEntries/VMExits calls.
 */
public class Logger {

    public static final boolean ENABLED = Boolean.valueOf(System.getProperty("graal.debug"));
    private static final int SPACING = 4;
    private static Deque<Boolean> openStack = new LinkedList<Boolean>();
    private static boolean open = false;
    private static int level = 0;

    private static final PrintStream out;

    static {
        PrintStream ps = null;
        String filename = System.getProperty("graal.info_file");
        if (filename != null && !"".equals(filename)) {
            try {
                ps = new PrintStream(new FileOutputStream(filename));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                ps = null;
            }
        }
        out = ps;
        if (out != null) {
            out.println("start: " + new Date());
        }
    }

    public static void info(String message) {
        if (ENABLED) {
            log(message);
        } else {
            System.out.println(message);
        }
        if (out != null) {
            out.println(message);
            out.flush();
        }
    }

    public static void log(String message) {
        if (ENABLED) {
            for (String line : message.split("\n")) {
                if (open) {
                    System.out.println("...");
                    open = false;
                }
                System.out.print(space(level));
                System.out.println(line);
            }
        }
    }

    public static void startScope(String message) {
        if (ENABLED) {
            if (open) {
                System.out.println("...");
                open = false;
            }
            System.out.print(space(level));
            System.out.print(message);
            openStack.push(open);
            open = true;
            level++;
        }
    }

    public static void endScope(String message) {
        if (ENABLED) {
            level--;
            if (open) {
                System.out.println(message);
            } else {
                System.out.println(space(level) + "..." + message);
            }
            open = openStack.pop();
        }
    }

    private static String[] spaces = new String[50];

    private static String space(int count) {
        assert count >= 0;
        String result;
        if (count >= spaces.length || spaces[count] == null) {
            StringBuilder str = new StringBuilder();
            for (int i = 0; i < count * SPACING; i++) {
                str.append(' ');
            }
            result = str.toString();
            if (count < spaces.length) {
                spaces[count] = result;
            }
        } else {
            result = spaces[count];
        }
        return result;
    }

    public static String pretty(Object value) {
        if (value == null) {
            return "null";
        }

        Class<?> klass = value.getClass();
        if (value instanceof Void) {
            return "void";
        } else if (value instanceof String) {
            return "\"" + value + "\"";
        } else if (value instanceof Method) {
            return "method \"" + ((Method) value).getName() + "\"";
        } else if (value instanceof Class<?>) {
            return "class \"" + ((Class<?>) value).getSimpleName() + "\"";
        } else if (value instanceof Integer) {
            if ((Integer) value < 10) {
                return value.toString();
            }
            return value + " (0x" + Integer.toHexString((Integer) value) + ")";
        } else if (value instanceof Long) {
            if ((Long) value < 10) {
                return value + "l";
            }
            return value + "l (0x" + Long.toHexString((Long) value) + "l)";
        } else if (klass.isArray()) {
            StringBuilder str = new StringBuilder();
            int dimensions = 0;
            while (klass.isArray()) {
                dimensions++;
                klass = klass.getComponentType();
            }
            str.append(klass.getSimpleName()).append('[').append(Array.getLength(value)).append(']');
            for (int i = 1; i < dimensions; i++) {
                str.append("[]");
            }
            return str.toString();
        }

        return value.toString();
    }
}
