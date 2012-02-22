/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
package jtt.reflect;

/*
 * @Harness: java
 * @Runs: 0=true; 1=true; 2=true; 3=true; 4=true; 5=true; 6=true; 7=true; 8=false;
 */
public class Field_get04 {

    private static final Field_get04 object = new Field_get04();

    public final byte byteField = 11;
    public final short shortField = 12;
    public final char charField = 13;
    public final int intField = 14;
    public final long longField = 15;
    public final float floatField = 16;
    public final double doubleField = 17;
    public final boolean booleanField = true;

    public static boolean test(int arg) throws NoSuchFieldException, IllegalAccessException {
        if (arg == 0) {
            return Field_get04.class.getField("byteField").getByte(object) == object.byteField;
        } else if (arg == 1) {
            return Field_get04.class.getField("shortField").getShort(object) == object.shortField;
        } else if (arg == 2) {
            return Field_get04.class.getField("charField").getChar(object) == object.charField;
        } else if (arg == 3) {
            return Field_get04.class.getField("intField").getInt(object) == object.intField;
        } else if (arg == 4) {
            return Field_get04.class.getField("longField").getLong(object) == object.longField;
        } else if (arg == 5) {
            return Field_get04.class.getField("floatField").getFloat(object) == object.floatField;
        } else if (arg == 6) {
            return Field_get04.class.getField("doubleField").getDouble(object) == object.doubleField;
        } else if (arg == 7) {
            return Field_get04.class.getField("booleanField").getBoolean(object) == object.booleanField;
        }
        return false;
    }
}
