/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.vm.ext.vma.store.txt;

import java.util.*;

/**
 * Defines a compact textual format for the the {@link VMATextStore} output.
 *
 * There is an explicit assumption that store records are ordered in time to support
 * the relative time optimization. However, an embedded {@link Key#THREAD_SWITCH} record can be
 * used to "reset" the time for stores that are created from a set of records for a set
 * of threads. Such "batched" stores are also indicated by the second argument to
 * the {@link Key#INITIALIZE_LOG} record being {@code true}.
 *
 * Normally the store uses relative time, recording the offset from the previous
 * record for that thread. However, it is possible to use absolute time and this is
 * indicated by a {@code true} value to first argument of the {@link Key#INITIALIZE_LOG} record.
 *
 * Each store record occupies one line starting with the string code for the {@link Key}.
 * The key is followed by the time (either absolute or relative) and then, for most records,
 * the thread that created the record, and the bytecode index of the associated instruction.
 * This is then followed by arguments that are specific
 * to the record, generally in same order as the parameters to the methods in {@link VMATextStore}.
 *
 */

public abstract class CVMATextStore extends VMATextStore {

    public static final long REPEAT_ID_VALUE = Long.MIN_VALUE;
    public static final char REPEAT_ID = '*';

    /**
     * Log records that do not have a time component.
     * N.B. {@link Key#INITIALIZE_LOG}, {@link Key#FINALIZE_LOG} and {@link Key.THREAD_SWITCH}
     * are in this set because their time component is always absolute.
     */
    public static final EnumSet<Key> noTimeSet = EnumSet.of(
                    Key.INITIALIZE_STORE, Key.FINALIZE_STORE, Key.THREAD_SWITCH,
                    Key.CLASS_DEFINITION, Key.FIELD_DEFINITION,
                    Key.THREAD_DEFINITION, Key.METHOD_DEFINITION,
                    Key.REMOVAL);

    public static final Map<String, Key> commandMap = new HashMap<String, Key>();

    static {
        for (Key key : Key.values()) {
            commandMap.put(key.code, key);
        }
    }

    public static final char OBJ_VALUE = 'O';
    public static final char LONG_VALUE = 'J';
    public static final char FLOAT_VALUE = 'F';
    public static final char DOUBLE_VALUE = 'D';

    // Argument indices for frequently accessed parts of the majority of records
    public static final int KEY_INDEX = 0;
    public static final int TIME_INDEX = 1;
    public static final int THREAD_INDEX = 2;
    public static final int BCI_INDEX = 3;
    public static final int OBJ_ID_INDEX = 4;
    public static final int STATIC_CLASSNAME_INDEX = 4;
    public static final int ID_CLASSNAME_INDEX = 5;
    public static final int ARRAY_INDEX_INDEX = 5;
    public static final int DEFINE_ARG_INDEX = 1;

    public static boolean hasId(Key code) {
        return code == Key.UNSEEN || hasIdSet.contains(code);
    }

    public static boolean hasTime(Key key) {
        return !noTimeSet.contains(key);
    }

    public static boolean hasTimeAndThread(Key key) {
        return hasTime(key);
    }

    public static boolean hasBci(Key key) {
        return hasBciSet.contains(key);
    }

// START GENERATED CODE
// EDIT AND RUN CVMATextStoreGenerator.main() TO MODIFY

    public enum Key {
        CLASS_DEFINITION("C"),
        FIELD_DEFINITION("F"),
        THREAD_DEFINITION("T"),
        METHOD_DEFINITION("M"),
        ADVISE_BEFORE_THROW("BT"),
        ADVISE_BEFORE_IF("BI"),
        ADVISE_BEFORE_LOAD("BL"),
        ADVISE_AFTER_MULTI_NEW_ARRAY("AMNA"),
        ADVISE_BEFORE_RETURN_BY_THROW("BRBT"),
        ADVISE_BEFORE_INVOKE_INTERFACE("BII"),
        ADVISE_BEFORE_STORE("BS"),
        ADVISE_BEFORE_INSTANCE_OF("BIO"),
        ADVISE_BEFORE_ARRAY_STORE("BAS"),
        ADVISE_BEFORE_GET_STATIC("BGS"),
        ADVISE_BEFORE_PUT_FIELD("BPF"),
        ADVISE_BEFORE_INVOKE_STATIC("BIS"),
        ADVISE_BEFORE_PUT_STATIC("BPS"),
        ADVISE_BEFORE_MONITOR_EXIT("BMX"),
        ADVISE_BEFORE_ARRAY_LENGTH("BAG"),
        REMOVAL("D"),
        ADVISE_BEFORE_CHECK_CAST("BCC"),
        ADVISE_AFTER_GC("AGC"),
        ADVISE_BEFORE_GET_FIELD("BGF"),
        ADVISE_BEFORE_OPERATION("BO"),
        INITIALIZE_STORE("IL"),
        ADVISE_BEFORE_INVOKE_SPECIAL("BIZ"),
        ADVISE_BEFORE_STACK_ADJUST("BSA"),
        ADVISE_BEFORE_GC("BGC"),
        ADVISE_BEFORE_RETURN("BR"),
        ADVISE_BEFORE_ARRAY_LOAD("BAL"),
        ADVISE_BEFORE_CONVERSION("BC"),
        THREAD_SWITCH("ZT"),
        ADVISE_BEFORE_MONITOR_ENTER("BME"),
        ADVISE_BEFORE_THREAD_TERMINATING("BTT"),
        ADVISE_BEFORE_GOTO("BG"),
        FINALIZE_STORE("FL"),
        ADVISE_BEFORE_THREAD_STARTING("BTS"),
        ADVISE_AFTER_METHOD_ENTRY("AME"),
        ADVISE_BEFORE_CONST_LOAD("BCL"),
        ADVISE_AFTER_NEW("AN"),
        ADVISE_AFTER_NEW_ARRAY("ANA"),
        ADVISE_BEFORE_INVOKE_VIRTUAL("BIV"),
        UNSEEN("U");
        public final String code;
        private Key(String code) {
            this.code = code;
        }
    }

    public static final EnumSet<Key> hasIdSet = EnumSet.of(
        Key.ADVISE_BEFORE_RETURN_BY_THROW,
        Key.ADVISE_BEFORE_ARRAY_LOAD,
        Key.ADVISE_BEFORE_ARRAY_STORE,
        Key.ADVISE_BEFORE_GET_FIELD,
        Key.ADVISE_BEFORE_PUT_FIELD,
        Key.ADVISE_BEFORE_INVOKE_VIRTUAL,
        Key.ADVISE_BEFORE_INVOKE_SPECIAL,
        Key.ADVISE_BEFORE_INVOKE_STATIC,
        Key.ADVISE_BEFORE_INVOKE_INTERFACE,
        Key.ADVISE_BEFORE_ARRAY_LENGTH,
        Key.ADVISE_BEFORE_THROW,
        Key.ADVISE_BEFORE_CHECK_CAST,
        Key.ADVISE_BEFORE_INSTANCE_OF,
        Key.ADVISE_BEFORE_MONITOR_ENTER,
        Key.ADVISE_BEFORE_MONITOR_EXIT,
        Key.ADVISE_AFTER_NEW,
        Key.ADVISE_AFTER_NEW_ARRAY,
        Key.ADVISE_AFTER_MULTI_NEW_ARRAY,
        Key.ADVISE_AFTER_METHOD_ENTRY);

    public static final EnumSet<Key> hasBciSet = EnumSet.of(
        Key.ADVISE_BEFORE_CONST_LOAD,
        Key.ADVISE_BEFORE_LOAD,
        Key.ADVISE_BEFORE_ARRAY_LOAD,
        Key.ADVISE_BEFORE_STORE,
        Key.ADVISE_BEFORE_ARRAY_STORE,
        Key.ADVISE_BEFORE_STACK_ADJUST,
        Key.ADVISE_BEFORE_OPERATION,
        Key.ADVISE_BEFORE_CONVERSION,
        Key.ADVISE_BEFORE_IF,
        Key.ADVISE_BEFORE_GOTO,
        Key.ADVISE_BEFORE_RETURN,
        Key.ADVISE_BEFORE_GET_STATIC,
        Key.ADVISE_BEFORE_PUT_STATIC,
        Key.ADVISE_BEFORE_GET_FIELD,
        Key.ADVISE_BEFORE_PUT_FIELD,
        Key.ADVISE_BEFORE_INVOKE_VIRTUAL,
        Key.ADVISE_BEFORE_INVOKE_SPECIAL,
        Key.ADVISE_BEFORE_INVOKE_STATIC,
        Key.ADVISE_BEFORE_INVOKE_INTERFACE,
        Key.ADVISE_BEFORE_ARRAY_LENGTH,
        Key.ADVISE_BEFORE_THROW,
        Key.ADVISE_BEFORE_CHECK_CAST,
        Key.ADVISE_BEFORE_INSTANCE_OF,
        Key.ADVISE_BEFORE_MONITOR_ENTER,
        Key.ADVISE_BEFORE_MONITOR_EXIT,
        Key.ADVISE_AFTER_NEW,
        Key.ADVISE_AFTER_NEW_ARRAY,
        Key.ADVISE_AFTER_MULTI_NEW_ARRAY,
        Key.ADVISE_AFTER_METHOD_ENTRY);
// END GENERATED CODE
}
