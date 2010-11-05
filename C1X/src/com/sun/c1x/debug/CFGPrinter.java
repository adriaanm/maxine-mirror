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
package com.sun.c1x.debug;

import java.io.*;
import java.util.*;

import com.sun.c1x.alloc.*;
import com.sun.c1x.alloc.Interval.*;
import com.sun.c1x.graph.*;
import com.sun.c1x.ir.*;
import com.sun.c1x.lir.*;
import com.sun.c1x.lir.LIRInstruction.*;
import com.sun.c1x.util.*;
import com.sun.c1x.value.*;
import com.sun.cri.ci.*;
import com.sun.cri.ci.CiAddress.*;
import com.sun.cri.ri.*;

/**
 * Utility for printing the control flow graph of a method being compiled by C1X at various compilation phases.
 * The output format matches that produced by HotSpot so that it can then be fed to the
 * <a href="https://c1visualizer.dev.java.net/">C1 Visualizer</a>.
 *
 * @author Doug Simon
 */
public class CFGPrinter {
    private static final String COLUMN_END = " <|@";
    private static final String HOVER_START = "<@";
    private static final String HOVER_SEP = "|@";
    private static final String HOVER_END = ">@";

    private static OutputStream cfgFileStream;

    /**
     * Gets the output stream  on the file "output.cfg" in the current working directory.
     * This stream is first opened if necessary.
     *
     * @return the output stream to "output.cfg" or {@code null} if there was an error opening this file for writing
     */
    public static synchronized OutputStream cfgFileStream() {
        if (cfgFileStream == null) {
            File cfgFile = new File("output.cfg");
            try {
                cfgFileStream = new FileOutputStream(cfgFile);
            } catch (FileNotFoundException e) {
                TTY.println("WARNING: Could not open " + cfgFile.getAbsolutePath());
            }
        }
        return cfgFileStream;
    }

    private final LogStream out;
    private final CiTarget target;

    /**
     * Creates a control flow graph printer.
     *
     * @param os where the output generated via this printer shown be written
     * @param target the target architecture description
     */
    public CFGPrinter(OutputStream os, CiTarget target) {
        out = new LogStream(os);
        this.target = target;
    }

    /**
     * Flushes all buffered output to the stream passed to {@link #CFGPrinter(OutputStream, CiTarget)}.
     */
    public void flush() {
        out.flush();
    }

    private void begin(String string) {
        out.println("begin_" + string);
        out.adjustIndentation(2);
    }

    private void end(String string) {
        out.adjustIndentation(-2);
        out.println("end_" + string);
    }

    /**
     * Prints a compilation timestamp for a given method.
     *
     * @param method the method for which a timestamp will be printed
     */
    public void printCompilation(RiMethod method) {
        begin("compilation");
        out.print("name \" ").print(CiUtil.format("%H::%n", method, true)).println('"');
        out.print("method \"").print(CiUtil.format("%f %r %H.%n(%p)", method, true)).println('"');
        out.print("date ").println(System.currentTimeMillis());
        end("compilation");
    }

    /**
     * Print the details of a given control flow graph block.
     *
     * @param block the block to print
     * @param successors the successor blocks of {@code block}
     * @param handlers the exception handler blocks of {@code block}
     * @param printHIR if {@code true} the HIR for each instruction in the block will be printed
     * @param printLIR if {@code true} the LIR for each instruction in the block will be printed
     */
    void printBlock(BlockBegin block, List<BlockBegin> successors, Iterable<BlockBegin> handlers, boolean printHIR, boolean printLIR) {
        begin("block");

        out.print("name \"B").print(block.blockID).println('"');
        out.print("from_bci ").println(block.bci());
        out.print("to_bci ").println(block.end() == null ? -1 : block.end().bci());

        out.print("predecessors ");
        for (BlockBegin pred : block.predecessors()) {
            out.print("\"B").print(pred.blockID).print("\" ");
        }
        out.println();

        out.print("successors ");
        for (BlockBegin succ : successors) {
            out.print("\"B").print(succ.blockID).print("\" ");
        }
        out.println();

        out.print("xhandlers");
        for (BlockBegin handler : handlers) {
            out.print("\"B").print(handler.blockID).print("\" ");
        }
        out.println();

        out.print("flags ");
        if (block.isStandardEntry()) {
            out.print("\"std\" ");
        }
        if (block.isOsrEntry()) {
            out.print("\"osr\" ");
        }
        if (block.isExceptionEntry()) {
            out.print("\"ex\" ");
        }
        if (block.isSubroutineEntry()) {
            out.print("\"sr\" ");
        }
        if (block.isBackwardBranchTarget()) {
            out.print("\"bb\" ");
        }
        if (block.isParserLoopHeader()) {
            out.print("\"plh\" ");
        }
        if (block.isCriticalEdgeSplit()) {
            out.print("\"ces\" ");
        }
        if (block.isLinearScanLoopHeader()) {
            out.print("\"llh\" ");
        }
        if (block.isLinearScanLoopEnd()) {
            out.print("\"lle\" ");
        }
        out.println();

        if (block.dominator() != null) {
            out.print("dominator \"B").print(block.dominator().blockID).println('"');
        }
        if (block.loopIndex() != -1) {
            out.print("loop_index ").println(block.loopIndex());
            out.print("loop_depth ").println(block.loopDepth());
        }

        if (printHIR) {
            printState(block);
            printHIR(block);
        }

        if (printLIR) {
            printLIR(block);
        }

        end("block");
    }

    /**
     * Prints the JVM frame state upon entry to a given block.
     *
     * @param block the block for which the frame state is to be printed
     */
    private void printState(BlockBegin block) {
        begin("states");

        FrameState state = block.stateBefore();

        do {
            int stackSize = state.stackSize();
            if (stackSize > 0) {
                begin("stack");
                out.print("size ").println(stackSize);
                out.print("method \"").print(CiUtil.format("%f %h.%n(%p):%r", state.scope().method, false)).println('"');

                int i = 0;
                while (i < stackSize) {
                    Value value = state.stackAt(i);
                    out.disableIndentation();
                    out.print(InstructionPrinter.stateString(i, value, block));
                    printOperand(value);
                    out.println();
                    out.enableIndentation();
                    if (value == null) {
                        i++;
                    } else {
                        i += value.kind.sizeInSlots();
                    }
                }
                end("stack");
            }

            if (state.locksSize() > 0) {
                begin("locks");
                out.print("size ").println(state.locksSize());
                out.print("method \"").print(CiUtil.format("%f %h.%n(%p):%r", state.scope().method, false)).println('"');

                for (int i = 0; i < state.locksSize(); ++i) {
                    Value value = state.lockAt(i);
                    out.disableIndentation();
                    out.print(InstructionPrinter.stateString(i, value, block));
                    printOperand(value);
                    out.println();
                    out.enableIndentation();
                }
                end("locks");
            }

            begin("locals");
            out.print("size ").println(state.localsSize());
            out.print("method \"").print(CiUtil.format("%f %h.%n(%p):%r", state.scope().method, false)).println('"');
            int i = 0;
            while (i < state.localsSize()) {
                Value value = state.localAt(i);
                if (value != null) {
                    out.disableIndentation();
                    out.print(InstructionPrinter.stateString(i, value, block));
                    printOperand(value);
                    out.println();
                    out.enableIndentation();
                    // also ignore illegal HiWords
                    i += value.isIllegal() ? 1 : value.kind.sizeInSlots();
                } else {
                    i++;
                }
            }
            state = state.callerState();
            end("locals");
        } while (state != null);

        end("states");
    }

    /**
     * Formats a given {@linkplain FrameState JVM frame state} as a multi line string.
     */
    private String stateToString(FrameState state, CFGOperandFormatter operandFmt) {
        if (state == null) {
            return null;
        }

        StringBuilder buf = new StringBuilder();

        boolean multipleScopes = state.callerState() != null;
        int bci = -1;
        do {
            // Only qualify state with method name if there are multiple scopes (due to inlining)
            if (multipleScopes) {
                buf.append(CiUtil.format("%H.%n(%p)", state.scope().method, false));
                if (bci >= 0) {
                    buf.append(" @ ").append(bci);
                }
                buf.append('\n');
            }
            if (state.stackSize() > 0) {
                int i = 0;
                buf.append("stack: ");
                while (i < state.stackSize()) {
                    if (i == 0) {
                        buf.append(' ');
                    }
                    Value value = state.stackAt(i);
                    buf.append(stateValueToString(value, operandFmt)).append(' ');
                    i++;
                }
                buf.append("\n");
            }

            if (state.locksSize() > 0) {
                buf.append("locks: ");
                for (int i = 0; i < state.locksSize(); ++i) {
                    if (i == 0) {
                        buf.append(' ');
                    }
                    Value value = state.lockAt(i);
                    buf.append(stateValueToString(value, operandFmt)).append(' ');
                }
                buf.append("\n");
            }

            buf.append("locals: ");
            int i = 0;
            while (i < state.localsSize()) {
                if (i == 0) {
                    buf.append(' ');
                }
                Value value = state.localAt(i);
                buf.append(stateValueToString(value, operandFmt)).append(' ');
                i++;
            }
            buf.append("\n");
            bci = state.scope().callerBCI();
            state = state.callerState();
        } while (state != null);
        return buf.toString();
    }

    private String stateValueToString(Value value, OperandFormatter operandFmt) {
        if (operandFmt == null) {
            return Util.valueString(value);
        }
        if (value == null) {
            return "-";
        }
        return operandFmt.format(value.operand());
    }

    /**
     * Prints the HIR for each instruction in a given block.
     *
     * @param block
     */
    private void printHIR(BlockBegin block) {
        begin("IR");
        out.println("HIR");
        out.disableIndentation();
        for (Instruction i = block.next(); i != null; i = i.next()) {
            printInstructionHIR(i);
        }
        out.enableIndentation();
        end("IR");
    }

    /**
     * Formats LIR operands as expected by the C1 Visualizer.
     */
    public static class CFGOperandFormatter extends OperandFormatter {
        /**
         * The textual delimiters used for an operand depend on the context in which it is being
         * printed. When printed as part of a frame state or as the result operand in a HIR node listing,
         * it is enclosed in double-quotes (i.e. {@code "}'s).
         */
        public final boolean asStateOrHIROperandResult;

        public CFGOperandFormatter(boolean asStateOrHIROperandResult) {
            this.asStateOrHIROperandResult = asStateOrHIROperandResult;
        }

        @Override
        public String format(CiValue operand) {
            if (operand.isLegal()) {
                String op;
                if (operand.isVariableOrRegister() || operand.isStackSlot()) {
                    op = operand.name();
                } else if (operand.isConstant()) {
                    CiConstant constant = (CiConstant) operand;
                    op = operand.kind.javaName + ":" + operand.kind.format(constant.boxedValue());
                } else if (operand.isAddress()) {
                    CiAddress address = (CiAddress) operand;
                    op = "Base:" + format(address.base);
                    if (!address.index.isIllegal()) {
                        op += " Index:" + format(address.index);
                    }
                    if (address.scale != Scale.Times1) {
                        op += " * " + address.scale.value;
                    }
                    op += " Disp:" + address.displacement;
                } else {
                    assert operand.isIllegal();
                    op = "-";
                }
                if (operand.kind != CiKind.Illegal) {
                    op += "|" + operand.kind.typeChar;
                }
                if (asStateOrHIROperandResult) {
                    op = " \"" + op + "\" ";
                }
                return op;
            }
            return "";
        }
    }

    /**
     * Prints the LIR for each instruction in a given block.
     *
     * @param block the block to print
     */
    private void printLIR(BlockBegin block) {
        LIRList lir = block.lir();
        if (lir != null) {
            begin("IR");
            out.println("LIR");
            for (int i = 0; i < lir.length(); i++) {
                LIRInstruction inst = lir.at(i);
                out.printf("nr %4d ", inst.id).print(COLUMN_END);

                if (inst.info != null) {
                    int level = out.indentationLevel();
                    out.adjustIndentation(-level);
                    String state = stateToString(inst.info.state, new CFGOperandFormatter(false));
                    if (state != null) {
                        out.print(" st ").print(HOVER_START).print("st").print(HOVER_SEP).print(state).print(HOVER_END).print(COLUMN_END);
                    }
                    out.adjustIndentation(level);
                }

                out.print(" instruction ").print(inst.toString(new CFGOperandFormatter(false))).print(COLUMN_END);
                out.println(COLUMN_END);
            }
            end("IR");
        }
    }

    private void printOperand(Value i) {
        if (i != null && i.operand().isLegal()) {
            out.print(new CFGOperandFormatter(true).format(i.operand()));
        }
    }

    /**
     * Prints the HIR for a given instruction.
     *
     * @param i the instruction for which HIR will be printed
     */
    private void printInstructionHIR(Instruction i) {
        out.print("bci ").print(i.bci()).println(COLUMN_END);
        if (i.operand().isLegal()) {
            out.print("result ").print(new CFGOperandFormatter(false).format(i.operand())).println(COLUMN_END);
        }
        out.print("tid ").print(i).println(COLUMN_END);

        String state = stateToString(i.stateBefore(), null);
        if (state != null) {
            out.print("st ").print(HOVER_START).print("st").print(HOVER_SEP).print(state).print(HOVER_END).println(COLUMN_END);
        }

        out.print("instruction ");
        new InstructionPrinter(out, true, target).printInstruction(i);
        out.print(COLUMN_END).print(' ').println(COLUMN_END);
    }

    /**
     * Prints the control flow graph denoted by a given block map.
     *
     * @param blockMap a data structure describing the blocks in a method and how they are connected
     * @param codeSize the bytecode size of the method from which {@code blockMap} was produced
     * @param label a label describing the compilation phase that produced the control flow graph
     * @param printHIR if {@code true} the HIR for each instruction in the block will be printed
     * @param printLIR if {@code true} the LIR for each instruction in the block will be printed
     */
    public void printCFG(RiMethod method, BlockMap blockMap, int codeSize, String label, boolean printHIR, boolean printLIR) {
        begin("cfg");
        out.print("name \"").print(label).println('"');
        for (int bci = 0; bci < codeSize; ++bci) {
            BlockBegin block = blockMap.get(bci);
            if (block != null) {
                printBlock(block, Arrays.asList(blockMap.getSuccessors(block)), blockMap.getHandlers(block), printHIR, printLIR);
            }
        }
        end("cfg");
    }

    /**
     * Prints the control flow graph rooted at a given block.
     *
     * @param startBlock the entry block of the control flow graph to be printed
     * @param label a label describing the compilation phase that produced the control flow graph
     * @param printHIR if {@code true} the HIR for each instruction in the block will be printed
     * @param printLIR if {@code true} the LIR for each instruction in the block will be printed
     */
    public void printCFG(BlockBegin startBlock, String label, final boolean printHIR, final boolean printLIR) {
        begin("cfg");
        out.print("name \"").print(label).println('"');
        startBlock.iteratePreOrder(new BlockClosure() {
            public void apply(BlockBegin block) {
                List<BlockBegin> successors = block.end() != null ? block.end().successors() : new ArrayList<BlockBegin>(0);
                printBlock(block, successors, block.exceptionHandlerBlocks(), printHIR, printLIR);
            }
        });
        end("cfg");
    }

    public void printIntervals(LinearScan allocator, Interval[] intervals, String name) {
        begin("intervals");
        out.println(String.format("name \"%s\"", name));

        for (Interval interval : intervals) {
            if (interval != null) {
                printInterval(allocator, interval);
            }
        }

        end("intervals");
    }

    private void printInterval(LinearScan allocator, Interval interval) {
        out.printf("%d %s ", interval.operandNumber, (interval.operand.isRegister() ? "fixed" : interval.kind().name()));
        if (interval.operand.isRegister()) {
            out.printf("\"[%s|%c]\"", interval.operand.name(), interval.operand.kind.typeChar);
            if (!true) {
                out.print(' ');
            }
        } else {
            if (interval.location() != null) {
                out.printf("\"[%s|%c]\"", interval.location().name(), interval.location().kind.typeChar);
            }
        }

        Interval hint = interval.locationHint(false, allocator);
        out.printf("%d %d ", interval.splitParent().operandNumber, hint != null ? hint.operandNumber : -1);

        // print ranges
        Range cur = interval.first();
        while (cur != Range.EndMarker) {
            out.printf("[%d, %d[", cur.from, cur.to);
            cur = cur.next;
            assert cur != null : "range list not closed with range sentinel";
        }

        // print use positions
        int prev = 0;
        UsePosList usePosList = interval.usePosList();
        for (int i = usePosList.size() - 1; i >= 0; --i) {
            assert prev < usePosList.usePos(i) : "use positions not sorted";
            out.printf("%d %s ", usePosList.usePos(i), usePosList.registerPriority(i));
            prev = usePosList.usePos(i);
        }

        out.printf(" \"%s\"", interval.spillState());
        out.println();
    }

    public void printMachineCode(String code) {
        if (code.length() == 0) {
            return;
        }
        begin("nmethod");
        out.print(code);
        out.println(" <|@");
        end("nmethod");
    }
}
