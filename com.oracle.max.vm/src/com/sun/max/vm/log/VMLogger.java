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
package com.sun.max.vm.log;

import static com.sun.max.vm.log.VMLog.*;

import java.util.*;
import java.util.regex.*;

import com.sun.max.annotate.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.thread.*;

/**
 * A {@link VMLogger} defines a set of operations, cardinality {@code N} each identified
 * by an {@code int} code in the range {@code [0 .. N-1]}.
 * A series of "log" methods are provided, that take the operation code
 * and a varying number of {@link Word} arguments (up to {@value VMLog.Record#MAX_ARGS}).
 * Currently these must not be {@link Reference} types as no GC support is provided
 * for values in the log buffer. The thread (id) generating the log record
 * is automatically recorded.
 * <p>
 * A logger typically will implement the {@link VMLogger#operationName(int)}
 * method that returns a descriptive name for the operation.
 * <p>
 * Logging is enabled on a per logger basis through the use of
 * a standard {@code -XX:+LogXXX} option derived from the logger name.
 * Tracing to the {@link Log} stream is also available through {@code -XX:+TraceXXX},
 * and a default implementation is provided, although this can be overridden.
 *
 * Fine control over which operations are logged (and therefore traced) is provided
 * by the {@code -XX:LogXXXInclude=pattern} and {@code -XX:LogXXXExclude=pattern} options.
 * The {@code pattern} is a regular expression in the syntax expected by {@link java.util.regex.Pattern}
 * and refers to the operation names returned by {@link VMLogger#operationName(int)}.
 * By default all operations are logged. However, if the include option is set only
 * those operations that match the pattern are logged. In either case, if the exclude
 * option is provided, the set is reduced by those operations that match the exclude pattern.
 *
 */
public class VMLogger {
    private static int nextLoggerId = 1;

    /**
     * Descriptive name also used to create option names.
     */
    public final String name;
    /**
     * Creates a unique id when combined with operation id. Identifies the logger in the loggers map.
     */
    final int loggerId;
    /**
     * Number of distinct operations that can be logged.
     */
    private final int numOps;
    /**
     * Bit n is set of operation n is to be logged.
     */
    private final BitSet logOp;

    private final VMBooleanXXOption logOption;
    private final VMBooleanXXOption traceOption;
    private final VMStringOption logIncludeOption;
    private final VMStringOption logExcludeOption;
    private boolean log;
    private boolean trace;

    private final VMLog vmLog;

    private int[] argCounts = new int[8];

    @HOSTED_ONLY
    protected VMLogger(String name, int numOps) {
        this.name = name;
        this.numOps = numOps;
        loggerId = nextLoggerId++;
        logOp = new BitSet(numOps);
        // At VM startup we log everything; this gets refined once the VM is up in checkLogging.
        // This is because we cannot control the logging until the VM has parsed the PRISTINE options.
        log = true;
        for (int i = 0; i < numOps; i++) {
            logOp.set(i, true);
        }
        String logName = "Log" + name;
        logOption = new VMBooleanXXOption("-XX:-" + logName, "Log" + name);
        traceOption = new VMBooleanXXOption("-XX:-" + "Trace" + name, "Trace" + name);
        logIncludeOption = new VMStringOption("-XX:" + logName + "Include=", false, null, "list of " + name + " operations to include");
        logExcludeOption = new VMStringOption("-XX:" + logName + "Exclude=", false, null, "list of " + name + " operations to exclude");
        VMOptions.register(logOption, MaxineVM.Phase.PRISTINE);
        VMOptions.register(traceOption, MaxineVM.Phase.PRISTINE);
        VMOptions.register(logIncludeOption, MaxineVM.Phase.PRISTINE);
        VMOptions.register(logExcludeOption, MaxineVM.Phase.PRISTINE);
        vmLog = VMLog.vmLog();
        vmLog.registerLogger(this);
    }

    public String threadName(int id) {
        VmThread vmThread = VmThreadMap.ACTIVE.getVmThreadForID(id);
        return vmThread == null ? "DEAD" : vmThread.getName();
    }

    /**
     * Provides a mnemonic name for the given operation.
     */
    public String operationName(int op) {
        return "Op " + Integer.toString(op);
    }

    /**
     * Provides a string decoding of an argument value.
     * @param op the operation id
     * @param argNum the argument index in the original log call, {@code [0 .. argCount - 1])
     * @param arg the argument value from the original log call
     * @return a descriptive string. Default implementation is raw value as hex.
     */
    protected String argString(int argNum, Word arg) {
        return Long.toHexString(arg.asAddress().toLong());
    }

    protected boolean traceEnabled() {
        return trace;
    }

    /**
     * Implements the default trace option {@code -XX:+TraceXXX}.
     * {@link Log#lock()} and {@link Log#unlock(boolean)} are
     * handled by the caller.
     * @param r
     */
    protected void trace(Record r) {
        Log.print("Thread \"");
        Log.print(threadName(r.getThreadId()));
        Log.print("\" ");
        Log.print(name);
        Log.print('.');
        Log.print(operationName(r.getOperation()));
        int argCount = r.getArgCount();
        for (int i = 1; i <= argCount; i++) {
            Log.print(' ');
            Word arg = Word.zero();
            // Checkstyle: stop
            switch (i) {
                case 1: arg = asRecord1(r).arg1; break;
                case 2: arg = asRecord2(r).arg2; break;
                case 3: arg = asRecord3(r).arg3; break;
                case 4: arg = asRecord4(r).arg4; break;
                case 5: arg = asRecord5(r).arg5; break;
                case 6: arg = asRecord6(r).arg6; break;
                case 7: arg = asRecord7(r).arg7; break;
            }
            // Checkstyle: resume
            Log.print(argString(i, arg));
        }
        Log.println();
    }

    protected void checkLogOptions() {
        trace = traceOption.getValue();
        log = trace | logOption.getValue();
        if (log) {
            String logInclude = logIncludeOption.getValue();
            String logExclude = logExcludeOption.getValue();
            if (logInclude != null || logExclude != null) {
                for (int i = 0; i < numOps; i++) {
                    logOp.set(i, logInclude == null ? true : false);
                }
                if (logInclude != null) {
                    Pattern inclusionPattern = Pattern.compile(logInclude);
                    for (int i = 0; i < numOps; i++) {
                        if (inclusionPattern.matcher(operationName(i)).matches()) {
                            logOp.set(i, true);
                        }
                    }
                }
                if (logExclude != null) {
                    Pattern exclusionPattern = Pattern.compile(logExclude);
                    for (int i = 0; i < numOps; i++) {
                        if (exclusionPattern.matcher(operationName(i)).matches()) {
                            logOp.set(i, false);
                        }
                    }
                }
            }
        }
    }

    private Record log(int op, int argCount) {
        Record r = null;
        if (log && logOp.get(op)) {
            r = vmLog.getRecord(argCount);
            r.setHeader(op, argCount, loggerId);
            argCounts[argCount]++;
        }
        return r;
    }

    public void log(int op) {
        Record r = log(op, 0);
        if (r != null && trace) {
            doTrace(r);
        }
    }

    public void log(int op, Word arg1) {
        Record1 r = asRecord1(log(op, 1));
        if (r != null) {
            r.arg1 = arg1;
        }
        if (r != null && trace) {
            doTrace(r);
        }
    }

    public void log(int op, Word arg1, Word arg2) {
        Record2 r = asRecord2(log(op, 2));
        if (r != null) {
            r.arg1 = arg1;
            r.arg2 = arg2;
        }
        if (r != null && trace) {
            doTrace(r);
        }
    }

    public void log(int op, Word arg1, Word arg2, Word arg3) {
        Record3 r = asRecord3(log(op, 3));
        if (r != null) {
            r.arg1 = arg1;
            r.arg2 = arg2;
            r.arg3 = arg3;
        }
        if (r != null && trace) {
            doTrace(r);
        }
    }

    public void log(int op, Word arg1, Word arg2, Word arg3, Word arg4) {
        Record4 r = asRecord4(log(op, 4));
        if (r != null) {
            r.arg1 = arg1;
            r.arg2 = arg2;
            r.arg3 = arg3;
            r.arg4 = arg4;
        }
        if (r != null && trace) {
            doTrace(r);
        }
    }

    public void log(int op, Word arg1, Word arg2, Word arg3, Word arg4, Word arg5) {
        Record5 r = asRecord5(log(op, 5));
        if (r != null) {
            r.arg1 = arg1;
            r.arg2 = arg2;
            r.arg3 = arg3;
            r.arg4 = arg4;
            r.arg5 = arg5;
        }
        if (r != null && trace) {
            doTrace(r);
        }
    }

    public void log(int op, Word arg1, Word arg2, Word arg3, Word arg4, Word arg5, Word arg6) {
        Record6 r = asRecord6(log(op, 6));
        if (r != null) {
            r.arg1 = arg1;
            r.arg2 = arg2;
            r.arg3 = arg3;
            r.arg4 = arg4;
            r.arg5 = arg5;
            r.arg6 = arg6;
        }
        if (r != null && trace) {
            doTrace(r);
        }
    }

    public void log(int op, Word arg1, Word arg2, Word arg3, Word arg4, Word arg5, Word arg6, Word arg7) {
        Record7 r = asRecord7(log(op, 7));
        if (r != null) {
            r.arg1 = arg1;
            r.arg2 = arg2;
            r.arg3 = arg3;
            r.arg4 = arg4;
            r.arg5 = arg5;
            r.arg6 = arg6;
            r.arg7 = arg7;
        }
        if (r != null && trace) {
            doTrace(r);
        }
    }

    private void doTrace(Record r) {
        boolean lockDisabledSafepoints = Log.lock();
        trace(r);
        Log.unlock(lockDisabledSafepoints);
    }


}
