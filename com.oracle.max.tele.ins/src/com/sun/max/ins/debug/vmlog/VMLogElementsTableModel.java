/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.ins.debug.vmlog;

import java.util.*;

import com.sun.max.ins.*;
import com.sun.max.ins.gui.*;
import com.sun.max.tele.*;
import com.sun.max.tele.util.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.log.*;


abstract class VMLogElementsTableModel extends InspectorTableModel {

    public static class HostedLogRecord extends VMLog.Record implements Comparable<HostedLogRecord> {
        public final int header;
        public final int id;
        public final Word[] args;

        HostedLogRecord(int id, int header, Word... args) {
            this.id = id;
            this.header = header;
            this.args = args;
        }

        /**
         * For when we can't access VM but need to create a record.
         */
        HostedLogRecord() {
            header = 0;
            id = 0;
            args = new Word[0];
        }

        @Override
        public String toString() {
            if (VMLog.Record.isFree(header)) {
                return "free";
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("id=");
                sb.append(id);
                sb.append(",lid=");
                sb.append(VMLog.Record.getLoggerId(header));
                sb.append(",th=");
                sb.append(VMLog.Record.getThreadId(header));
                sb.append(",op=");
                sb.append(VMLog.Record.getOperation(header));
                sb.append(",ac");
                sb.append(VMLog.Record.getArgCount(header));
                return sb.toString();
            }
        }

        @Override
        public int getHeader() {
            return header;
        }

        @Override
        public void setHeader(int header) {
            assert false;
        }

        public int compareTo(HostedLogRecord other) {
            if (id < other.id) {
                return -1;
            } else if (id > other.id) {
                return 1;
            } else {
                return 0;
            }
        }

    }


    protected TeleVM vm;

    /**
     * Cache of the (logical) buffer of log records in the target VM.
     * Logical in the sense that per-thread log buffers are reconstituted into
     * a single, ordered, log in the Inspector.
     *
     * Although most VM implementations use a circular buffer and eventually
     * overwrite records we keep them all. (At some point may add a capability
     * to flush old records).
     *
     * Note that overwritten records may not be seen if the time between entries to the
     * Inspector is sufficiently long that the circular buffer wraps.
     * This could be addressed by a hidden breakpoint that was triggered
     * appropriately.
     *
     */
    protected List<HostedLogRecord> logRecordCache;

    /**
     * During {@link #refresh}, this holds the new value of the {@link VMLog#nextId} field,
     * which is the id of the next record that will be written.
     */
    protected int nextId;

    /**
     * After {@link #refresh}, this is set equal to {@link #nextId}, allowing
     * optimization on a subsequent refresh where nothing changed.
     */
    protected int lastNextId;

    protected VMLogView vmLogView;

    private int[] displayedRows;

    protected VMLogElementsTableModel(Inspection inspection, VMLogView vmLogView) {
        super(inspection);
        vm = (TeleVM) vm();
        this.vmLogView = vmLogView;
        logRecordCache = new ArrayList<HostedLogRecord>(vmLogView.logBufferEntries);
    }

    public int getColumnCount() {
        return VMLogColumnKind.values().length;
    }

    /**
     * The number of log records in the view.
     * @return
     */
    public int getRowCount() {
        return displayedRows == null ? logRecordCache.size() : displayedRows.length;
    }

    public void setDisplayedRows(int[] displayedRows) {
        this.displayedRows = displayedRows;
        this.fireTableDataChanged();
    }

    private int displayed2ModelRow(int displayedRow) {
        return displayedRows == null ? displayedRow : displayedRows[displayedRow];
    }

    HostedLogRecord getRecord(int row) {
        return logRecordCache.get(displayed2ModelRow(row));
    }

    /**
     * Get the value of the slot in the log buffer at the given logical row and column.
     */
    public Object getValueAt(int row, int col) {
        HostedLogRecord record = getRecord(row);
        if (record == null) {
            TeleError.unexpected("null log record in LogElementsTableModel.getValueAt");
        }
        Object result = null;
        int argCount = VMLog.Record.getArgCount(record.header);

        switch (VMLogColumnKind.values()[col]) {
            case ID:
                result = record.id;
                break;
            case THREAD:
                result = VMLog.Record.getThreadId(record.header);
                break;
            case OPERATION:
                result = VMLog.Record.getOperation(record.header);
                break;
            case ARG1:
                if (argCount > 0) {
                    result = record.args[0];
                }
                break;
            case ARG2:
                if (argCount > 1) {
                    result = record.args[1];
                }
                break;
            case ARG3:
                if (argCount > 2) {
                    result = record.args[2];
                }
                break;
            case ARG4:
                if (argCount > 3) {
                    result = record.args[3];
                }
                break;
            case ARG5:
                if (argCount > 4) {
                    result = record.args[4];
                }
                break;
            case ARG6:
                if (argCount > 5) {
                    result = record.args[5];
                }
                break;
            case ARG7:
                if (argCount > 6) {
                    result = record.args[6];
                }
                break;
            default:
                TeleError.unexpected("illegal column value kind");
                result = null;
        }
        return result;
    }

    @Override
    public void refresh() {
        nextId = vmLogView.nextIdFieldAccess.readInt(vmLogView.vmLogRef);
        if (nextId != lastNextId) {
            // Some new records.
            // The maximum possible number of records is nextId - lastNextId,
            // as that is the total number allocated since the last refresh.
            // However, depending on the target implementation, it is entirely possible
            // that enough change has occurred that some records were overwritten in the target.

            modelSpecificRefresh();

            lastNextId = nextId;
        }
        super.refresh();
    }

    /**
     * Is this header value well-formed?
     * @param header
     * @return
     */
    boolean wellFormedHeader(int header) {
        // there are brief periods when a record may not be well formed,
        // e.g., we have stopped in the Inspector after the log buffer id has been bumped
        // but before the data has been filled in.
        // specifically, the logger id may be bogus, which will cause a crash.
        if (VMLog.Record.isFree(header)) {
            return false;
        }
        int loggerId = VMLog.Record.getLoggerId(header);
        if (vmLogView.loggers.get(loggerId) == null) {
            return false;
        }
        return true;
    }


    /**
     * Responsible for any model-specific refresh before the main refresh happens.
     * E.g., collecting together the thread-specific records in {@link NativeThreadFixedLogElementsTableModel per-thread buffer model}.
     */
    protected void modelSpecificRefresh() {
        int id = firstId();

        while (id < nextId) {
            logRecordCache.add(getRecordFromVM(id));
            id++;
        }
    }

    /**
     * Return the first id to start gathering new records from.
     * Default assumes contiguous id range, i.e. global, shared, buffer, handling case
     * where some records have been overwritten.
     * @return
     */
    protected int firstId() {
        if (nextId - lastNextId > vmLogView.logBufferEntries) {
            // missed some records
            return nextId - vmLogView.logBufferEntries;
        } else {
            // pick up where we left off
            return lastNextId;
        }
    }

    /**
     * Create a {@link HostedLogRecord} from the target VM record.
     * @param id the id of the record (N.B. may not be stored in target).
     * @return
     */
    protected abstract HostedLogRecord getRecordFromVM(int id);

}
