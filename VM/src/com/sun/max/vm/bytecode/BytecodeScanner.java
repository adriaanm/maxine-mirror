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
package com.sun.max.vm.bytecode;

import static com.sun.max.vm.classfile.ErrorContext.*;

import com.sun.max.annotate.*;
import com.sun.max.program.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;

/**
 * Visitor dispatch over byte codes.
 *
 * @author Bernd Mathiske
 */
public final class BytecodeScanner {

    final BytecodeVisitor bytecodeVisitor;

    public BytecodeScanner(BytecodeVisitor bytecodeVisitor) {
        this.bytecodeVisitor = bytecodeVisitor;
        bytecodeVisitor.setBytecodeScanner(this);
    }

    protected BytecodeBlock bytecodeBlock;

    public BytecodeBlock bytecodeBlock() {
        return bytecodeBlock;
    }

    private int currentBytePosition;

    public int currentBytePosition() {
        return currentBytePosition;
    }

    protected boolean stopped;

    /**
     * Stop the scanning.
     */
    public void stop() {
        stopped = true;
    }

    public boolean wasStopped() {
        return stopped;
    }

    protected Bytecode currentOpcode;

    /**
     * Gets the most recently scanned opcode. Note that if this is called while in
     * {@link BytecodeVisitor#opcodeDecoded()} and the current instruction is widened then the {@link Bytecode#WIDE}
     * opcode will be returned. If it's called while in the visitor method for the widened instruction then the widened
     * opcode is returned and {@link #isCurrentOpcodeWidened()} will return true. If it's called while in
     * {@link BytecodeVisitor#instructionDecoded()} then the opcode is returned and {@link #isCurrentOpcodeWidened()}
     * will return false.
     */
    public Bytecode currentOpcode() {
        return currentOpcode;
    }

    protected int currentOpcodePosition;

    @INLINE
    public int currentOpcodePosition() {
        return currentOpcodePosition;
    }

    protected boolean currentOpcodeWidened;

    /**
     * @see #currentOpcode()
     */
    public boolean isCurrentOpcodeWidened() {
        return currentOpcodeWidened;
    }

    /**
     * Gets a description of the current scan location in a string that resembles a line in a standard stack trace.
     *
     * @param classMethodActor the context of the bytecode being scanned
     */
    public String getCurrentLocationAsString(ClassMethodActor classMethodActor) {
        final int lineNumber = classMethodActor.codeAttribute().lineNumberTable().findLineNumber(currentOpcodePosition);
        final StringBuilder buf = new StringBuilder();
        if (lineNumber != -1) {
            final ClassActor holder = classMethodActor.holder();
            String sourceFileName = holder.sourceFileName;
            if (sourceFileName == null) {
                sourceFileName = "<unknown>";
            }
            buf.append(classMethodActor.format("%H.%n(" + sourceFileName + ":" + lineNumber + ")"));
        } else {
            buf.append(classMethodActor.format("%H.%n(%p)"));
        }
        return buf.append(" [bytecode index=" + currentOpcodePosition + ", opcode=" + currentOpcode + "]").toString();
    }

    private byte readByte() {
        return bytecodeBlock.code()[currentBytePosition++];
    }

    private int readUnsigned1() {
        return readByte() & 0xff;
    }

    private int readSigned1() {
        return readByte();
    }

    private int readUnsigned2() {
        final int high = readByte() & 0xff;
        final int low = readByte() & 0xff;
        return (high << 8) | low;
    }

    private int readSigned2() {
        final int high = readByte();
        final int low = readByte() & 0xff;
        return (high << 8) | low;
    }

    private int readSigned4() {
        final int b3 = readByte() << 24;
        final int b2 = (readByte() & 0xff) << 16;
        final int b1 = (readByte() & 0xff) << 8;
        final int b0 = readByte() & 0xff;
        return b3 | b2 | b1 | b0;
    }

    public int readSwitchCase() {
        return readSigned4();
    }

    public int readSwitchOffset() {
        return readSigned4();
    }

    private void alignAddress() {
        final int remainder = currentBytePosition % 4;
        if (remainder != 0) {
            currentBytePosition += 4 - remainder;
        }
    }

    private void wide() {
        currentOpcodeWidened = true;
        currentOpcode = Bytecode.from(readUnsigned1());
        final int index = readUnsigned2();
        switch (currentOpcode) {
            case ILOAD: {
                bytecodeVisitor.iload(index);
                break;
            }
            case LLOAD: {
                bytecodeVisitor.lload(index);
                break;
            }
            case FLOAD: {
                bytecodeVisitor.fload(index);
                break;
            }
            case DLOAD: {
                bytecodeVisitor.dload(index);
                break;
            }
            case ALOAD: {
                bytecodeVisitor.aload(index);
                break;
            }
            case ISTORE: {
                bytecodeVisitor.istore(index);
                break;
            }
            case LSTORE: {
                bytecodeVisitor.lstore(index);
                break;
            }
            case FSTORE: {
                bytecodeVisitor.fstore(index);
                break;
            }
            case DSTORE: {
                bytecodeVisitor.dstore(index);
                break;
            }
            case ASTORE: {
                bytecodeVisitor.astore(index);
                break;
            }
            case IINC: {
                final int addend = readSigned2();
                bytecodeVisitor.iinc(index, addend);
                break;
            }
            case RET: {
                bytecodeVisitor.ret(index);
                break;
            }
            default: {
                throw verifyError("Invalid application of WIDE prefix to " + currentOpcode);
            }
        }
        currentOpcodeWidened = false;
    }

    protected void scanInstruction() {
        currentOpcode = Bytecode.from(readUnsigned1());
        bytecodeVisitor.opcodeDecoded();
        if (stopped) {
            return;
        }
        switch (currentOpcode) {
            case NOP: {
                bytecodeVisitor.nop();
                break;
            }
            case ACONST_NULL: {
                bytecodeVisitor.aconst_null();
                break;
            }
            case ICONST_M1: {
                bytecodeVisitor.iconst_m1();
                break;
            }
            case ICONST_0: {
                bytecodeVisitor.iconst_0();
                break;
            }
            case ICONST_1: {
                bytecodeVisitor.iconst_1();
                break;
            }
            case ICONST_2: {
                bytecodeVisitor.iconst_2();
                break;
            }
            case ICONST_3: {
                bytecodeVisitor.iconst_3();
                break;
            }
            case ICONST_4: {
                bytecodeVisitor.iconst_4();
                break;
            }
            case ICONST_5: {
                bytecodeVisitor.iconst_5();
                break;
            }
            case LCONST_0: {
                bytecodeVisitor.lconst_0();
                break;
            }
            case LCONST_1: {
                bytecodeVisitor.lconst_1();
                break;
            }
            case FCONST_0: {
                bytecodeVisitor.fconst_0();
                break;
            }
            case FCONST_1: {
                bytecodeVisitor.fconst_1();
                break;
            }
            case FCONST_2: {
                bytecodeVisitor.fconst_2();
                break;
            }
            case DCONST_0: {
                bytecodeVisitor.dconst_0();
                break;
            }
            case DCONST_1: {
                bytecodeVisitor.dconst_1();
                break;
            }
            case BIPUSH: {
                final int operand = readSigned1();
                bytecodeVisitor.bipush(operand);
                break;
            }
            case SIPUSH: {
                final int operand = readSigned2();
                bytecodeVisitor.sipush(operand);
                break;
            }
            case LDC: {
                final int index = readUnsigned1();
                bytecodeVisitor.ldc(index);
                break;
            }
            case LDC_W: {
                final int index = readUnsigned2();
                bytecodeVisitor.ldc_w(index);
                break;
            }
            case LDC2_W: {
                final int index = readUnsigned2();
                bytecodeVisitor.ldc2_w(index);
                break;
            }
            case ILOAD: {
                final int index = readUnsigned1();
                bytecodeVisitor.iload(index);
                break;
            }
            case LLOAD: {
                final int index = readUnsigned1();
                bytecodeVisitor.lload(index);
                break;
            }
            case FLOAD: {
                final int index = readUnsigned1();
                bytecodeVisitor.fload(index);
                break;
            }
            case DLOAD: {
                final int index = readUnsigned1();
                bytecodeVisitor.dload(index);
                break;
            }
            case ALOAD: {
                final int index = readUnsigned1();
                bytecodeVisitor.aload(index);
                break;
            }
            case ILOAD_0: {
                bytecodeVisitor.iload_0();
                break;
            }
            case ILOAD_1: {
                bytecodeVisitor.iload_1();
                break;
            }
            case ILOAD_2: {
                bytecodeVisitor.iload_2();
                break;
            }
            case ILOAD_3: {
                bytecodeVisitor.iload_3();
                break;
            }
            case LLOAD_0: {
                bytecodeVisitor.lload_0();
                break;
            }
            case LLOAD_1: {
                bytecodeVisitor.lload_1();
                break;
            }
            case LLOAD_2: {
                bytecodeVisitor.lload_2();
                break;
            }
            case LLOAD_3: {
                bytecodeVisitor.lload_3();
                break;
            }
            case FLOAD_0: {
                bytecodeVisitor.fload_0();
                break;
            }
            case FLOAD_1: {
                bytecodeVisitor.fload_1();
                break;
            }
            case FLOAD_2: {
                bytecodeVisitor.fload_2();
                break;
            }
            case FLOAD_3: {
                bytecodeVisitor.fload_3();
                break;
            }
            case DLOAD_0: {
                bytecodeVisitor.dload_0();
                break;
            }
            case DLOAD_1: {
                bytecodeVisitor.dload_1();
                break;
            }
            case DLOAD_2: {
                bytecodeVisitor.dload_2();
                break;
            }
            case DLOAD_3: {
                bytecodeVisitor.dload_3();
                break;
            }
            case ALOAD_0: {
                bytecodeVisitor.aload_0();
                break;
            }
            case ALOAD_1: {
                bytecodeVisitor.aload_1();
                break;
            }
            case ALOAD_2: {
                bytecodeVisitor.aload_2();
                break;
            }
            case ALOAD_3: {
                bytecodeVisitor.aload_3();
                break;
            }
            case IALOAD: {
                bytecodeVisitor.iaload();
                break;
            }
            case LALOAD: {
                bytecodeVisitor.laload();
                break;
            }
            case FALOAD: {
                bytecodeVisitor.faload();
                break;
            }
            case DALOAD: {
                bytecodeVisitor.daload();
                break;
            }
            case AALOAD: {
                bytecodeVisitor.aaload();
                break;
            }
            case BALOAD: {
                bytecodeVisitor.baload();
                break;
            }
            case CALOAD: {
                bytecodeVisitor.caload();
                break;
            }
            case SALOAD: {
                bytecodeVisitor.saload();
                break;
            }
            case ISTORE: {
                final int index = readUnsigned1();
                bytecodeVisitor.istore(index);
                break;
            }
            case LSTORE: {
                final int index = readUnsigned1();
                bytecodeVisitor.lstore(index);
                break;
            }
            case FSTORE: {
                final int index = readUnsigned1();
                bytecodeVisitor.fstore(index);
                break;
            }
            case DSTORE: {
                final int index = readUnsigned1();
                bytecodeVisitor.dstore(index);
                break;
            }
            case ASTORE: {
                final int index = readUnsigned1();
                bytecodeVisitor.astore(index);
                break;
            }
            case ISTORE_0: {
                bytecodeVisitor.istore_0();
                break;
            }
            case ISTORE_1: {
                bytecodeVisitor.istore_1();
                break;
            }
            case ISTORE_2: {
                bytecodeVisitor.istore_2();
                break;
            }
            case ISTORE_3: {
                bytecodeVisitor.istore_3();
                break;
            }
            case LSTORE_0: {
                bytecodeVisitor.lstore_0();
                break;
            }
            case LSTORE_1: {
                bytecodeVisitor.lstore_1();
                break;
            }
            case LSTORE_2: {
                bytecodeVisitor.lstore_2();
                break;
            }
            case LSTORE_3: {
                bytecodeVisitor.lstore_3();
                break;
            }
            case FSTORE_0: {
                bytecodeVisitor.fstore_0();
                break;
            }
            case FSTORE_1: {
                bytecodeVisitor.fstore_1();
                break;
            }
            case FSTORE_2: {
                bytecodeVisitor.fstore_2();
                break;
            }
            case FSTORE_3: {
                bytecodeVisitor.fstore_3();
                break;
            }
            case DSTORE_0: {
                bytecodeVisitor.dstore_0();
                break;
            }
            case DSTORE_1: {
                bytecodeVisitor.dstore_1();
                break;
            }
            case DSTORE_2: {
                bytecodeVisitor.dstore_2();
                break;
            }
            case DSTORE_3: {
                bytecodeVisitor.dstore_3();
                break;
            }
            case ASTORE_0: {
                bytecodeVisitor.astore_0();
                break;
            }
            case ASTORE_1: {
                bytecodeVisitor.astore_1();
                break;
            }
            case ASTORE_2: {
                bytecodeVisitor.astore_2();
                break;
            }
            case ASTORE_3: {
                bytecodeVisitor.astore_3();
                break;
            }
            case IASTORE: {
                bytecodeVisitor.iastore();
                break;
            }
            case LASTORE: {
                bytecodeVisitor.lastore();
                break;
            }
            case FASTORE: {
                bytecodeVisitor.fastore();
                break;
            }
            case DASTORE: {
                bytecodeVisitor.dastore();
                break;
            }
            case AASTORE: {
                bytecodeVisitor.aastore();
                break;
            }
            case BASTORE: {
                bytecodeVisitor.bastore();
                break;
            }
            case CASTORE: {
                bytecodeVisitor.castore();
                break;
            }
            case SASTORE: {
                bytecodeVisitor.sastore();
                break;
            }
            case POP: {
                bytecodeVisitor.pop();
                break;
            }
            case POP2: {
                bytecodeVisitor.pop2();
                break;
            }
            case DUP: {
                bytecodeVisitor.dup();
                break;
            }
            case DUP_X1: {
                bytecodeVisitor.dup_x1();
                break;
            }
            case DUP_X2: {
                bytecodeVisitor.dup_x2();
                break;
            }
            case DUP2: {
                bytecodeVisitor.dup2();
                break;
            }
            case DUP2_X1: {
                bytecodeVisitor.dup2_x1();
                break;
            }
            case DUP2_X2: {
                bytecodeVisitor.dup2_x2();
                break;
            }
            case SWAP: {
                bytecodeVisitor.swap();
                break;
            }
            case IADD: {
                bytecodeVisitor.iadd();
                break;
            }
            case LADD: {
                bytecodeVisitor.ladd();
                break;
            }
            case FADD: {
                bytecodeVisitor.fadd();
                break;
            }
            case DADD: {
                bytecodeVisitor.dadd();
                break;
            }
            case ISUB: {
                bytecodeVisitor.isub();
                break;
            }
            case LSUB: {
                bytecodeVisitor.lsub();
                break;
            }
            case FSUB: {
                bytecodeVisitor.fsub();
                break;
            }
            case DSUB: {
                bytecodeVisitor.dsub();
                break;
            }
            case IMUL: {
                bytecodeVisitor.imul();
                break;
            }
            case LMUL: {
                bytecodeVisitor.lmul();
                break;
            }
            case FMUL: {
                bytecodeVisitor.fmul();
                break;
            }
            case DMUL: {
                bytecodeVisitor.dmul();
                break;
            }
            case IDIV: {
                bytecodeVisitor.idiv();
                break;
            }
            case LDIV: {
                bytecodeVisitor.ldiv();
                break;
            }
            case FDIV: {
                bytecodeVisitor.fdiv();
                break;
            }
            case DDIV: {
                bytecodeVisitor.ddiv();
                break;
            }
            case IREM: {
                bytecodeVisitor.irem();
                break;
            }
            case LREM: {
                bytecodeVisitor.lrem();
                break;
            }
            case FREM: {
                bytecodeVisitor.frem();
                break;
            }
            case DREM: {
                bytecodeVisitor.drem();
                break;
            }
            case INEG: {
                bytecodeVisitor.ineg();
                break;
            }
            case LNEG: {
                bytecodeVisitor.lneg();
                break;
            }
            case FNEG: {
                bytecodeVisitor.fneg();
                break;
            }
            case DNEG: {
                bytecodeVisitor.dneg();
                break;
            }
            case ISHL: {
                bytecodeVisitor.ishl();
                break;
            }
            case LSHL: {
                bytecodeVisitor.lshl();
                break;
            }
            case ISHR: {
                bytecodeVisitor.ishr();
                break;
            }
            case LSHR: {
                bytecodeVisitor.lshr();
                break;
            }
            case IUSHR: {
                bytecodeVisitor.iushr();
                break;
            }
            case LUSHR: {
                bytecodeVisitor.lushr();
                break;
            }
            case IAND: {
                bytecodeVisitor.iand();
                break;
            }
            case LAND: {
                bytecodeVisitor.land();
                break;
            }
            case IOR: {
                bytecodeVisitor.ior();
                break;
            }
            case LOR: {
                bytecodeVisitor.lor();
                break;
            }
            case IXOR: {
                bytecodeVisitor.ixor();
                break;
            }
            case LXOR: {
                bytecodeVisitor.lxor();
                break;
            }
            case IINC: {
                final int index = readUnsigned1();
                final int addend = readSigned1();
                bytecodeVisitor.iinc(index, addend);
                break;
            }
            case I2L: {
                bytecodeVisitor.i2l();
                break;
            }
            case I2F: {
                bytecodeVisitor.i2f();
                break;
            }
            case I2D: {
                bytecodeVisitor.i2d();
                break;
            }
            case L2I: {
                bytecodeVisitor.l2i();
                break;
            }
            case L2F: {
                bytecodeVisitor.l2f();
                break;
            }
            case L2D: {
                bytecodeVisitor.l2d();
                break;
            }
            case F2I: {
                bytecodeVisitor.f2i();
                break;
            }
            case F2L: {
                bytecodeVisitor.f2l();
                break;
            }
            case F2D: {
                bytecodeVisitor.f2d();
                break;
            }
            case D2I: {
                bytecodeVisitor.d2i();
                break;
            }
            case D2L: {
                bytecodeVisitor.d2l();
                break;
            }
            case D2F: {
                bytecodeVisitor.d2f();
                break;
            }
            case I2B: {
                bytecodeVisitor.i2b();
                break;
            }
            case I2C: {
                bytecodeVisitor.i2c();
                break;
            }
            case I2S: {
                bytecodeVisitor.i2s();
                break;
            }
            case LCMP: {
                bytecodeVisitor.lcmp();
                break;
            }
            case FCMPL: {
                bytecodeVisitor.fcmpl();
                break;
            }
            case FCMPG: {
                bytecodeVisitor.fcmpg();
                break;
            }
            case DCMPL: {
                bytecodeVisitor.dcmpl();
                break;
            }
            case DCMPG: {
                bytecodeVisitor.dcmpg();
                break;
            }
            case IFEQ: {
                final int offset = readSigned2();
                bytecodeVisitor.ifeq(offset);
                break;
            }
            case IFNE: {
                final int offset = readSigned2();
                bytecodeVisitor.ifne(offset);
                break;
            }
            case IFLT: {
                final int offset = readSigned2();
                bytecodeVisitor.iflt(offset);
                break;
            }
            case IFGE: {
                final int offset = readSigned2();
                bytecodeVisitor.ifge(offset);
                break;
            }
            case IFGT: {
                final int offset = readSigned2();
                bytecodeVisitor.ifgt(offset);
                break;
            }
            case IFLE: {
                final int offset = readSigned2();
                bytecodeVisitor.ifle(offset);
                break;
            }
            case IF_ICMPEQ: {
                final int offset = readSigned2();
                bytecodeVisitor.if_icmpeq(offset);
                break;
            }
            case IF_ICMPNE: {
                final int offset = readSigned2();
                bytecodeVisitor.if_icmpne(offset);
                break;
            }
            case IF_ICMPLT: {
                final int offset = readSigned2();
                bytecodeVisitor.if_icmplt(offset);
                break;
            }
            case IF_ICMPGE: {
                final int offset = readSigned2();
                bytecodeVisitor.if_icmpge(offset);
                break;
            }
            case IF_ICMPGT: {
                final int offset = readSigned2();
                bytecodeVisitor.if_icmpgt(offset);
                break;
            }
            case IF_ICMPLE: {
                final int offset = readSigned2();
                bytecodeVisitor.if_icmple(offset);
                break;
            }
            case IF_ACMPEQ: {
                final int offset = readSigned2();
                bytecodeVisitor.if_acmpeq(offset);
                break;
            }
            case IF_ACMPNE: {
                final int offset = readSigned2();
                bytecodeVisitor.if_acmpne(offset);
                break;
            }
            case GOTO: {
                final int offset = readSigned2();
                bytecodeVisitor.goto_(offset);
                break;
            }
            case JSR: {
                final int offset = readSigned2();
                bytecodeVisitor.jsr(offset);
                break;
            }
            case RET: {
                final int index = readUnsigned1();
                bytecodeVisitor.ret(index);
                break;
            }
            case TABLESWITCH: {
                alignAddress();
                final int defaultOffset = readSigned4();
                final int lowMatch = readSigned4();
                final int highMatch = readSigned4();
                if (lowMatch > highMatch) {
                    throw verifyError("Low must be less than or equal to high in TABLESWITCH");
                }
                final int numberOfCases = highMatch - lowMatch + 1;
                final int start = currentBytePosition;
                bytecodeVisitor.tableswitch(defaultOffset, lowMatch, highMatch, numberOfCases);
                final int caseBytesRead = currentBytePosition - start;
                if ((caseBytesRead % 4) != 0 || (caseBytesRead >> 2) != numberOfCases) {
                    ProgramError.unexpected("Bytecode visitor did not consume exactly the offset operands of the tableswitch instruction at " + currentOpcodePosition);
                }
                break;
            }
            case LOOKUPSWITCH: {
                alignAddress();
                final int defaultOffset = readSigned4();
                final int numberOfCases = readSigned4();
                if (numberOfCases < 0) {
                    throw verifyError("Number of keys in LOOKUPSWITCH less than 0");
                }
                final int start = currentBytePosition;
                bytecodeVisitor.lookupswitch(defaultOffset, numberOfCases);
                final int caseBytesRead = currentBytePosition - start;
                if ((caseBytesRead % 8) != 0 || (caseBytesRead >> 3) != numberOfCases) {
                    ProgramError.unexpected("Bytecode visitor did not consume exactly the offset operands of the tableswitch instruction at " + currentOpcodePosition);
                }
                break;
            }
            case IRETURN: {
                bytecodeVisitor.ireturn();
                break;
            }
            case LRETURN: {
                bytecodeVisitor.lreturn();
                break;
            }
            case FRETURN: {
                bytecodeVisitor.freturn();
                break;
            }
            case DRETURN: {
                bytecodeVisitor.dreturn();
                break;
            }
            case ARETURN: {
                bytecodeVisitor.areturn();
                break;
            }
            case RETURN: {
                bytecodeVisitor.vreturn();
                break;
            }
            case GETSTATIC: {
                final int index = readUnsigned2();
                bytecodeVisitor.getstatic(index);
                break;
            }
            case PUTSTATIC: {
                final int index = readUnsigned2();
                bytecodeVisitor.putstatic(index);
                break;
            }
            case GETFIELD: {
                final int index = readUnsigned2();
                bytecodeVisitor.getfield(index);
                break;
            }
            case PUTFIELD: {
                final int index = readUnsigned2();
                bytecodeVisitor.putfield(index);
                break;
            }
            case INVOKEVIRTUAL: {
                final int index = readUnsigned2();
                bytecodeVisitor.invokevirtual(index);
                break;
            }
            case INVOKESPECIAL: {
                final int index = readUnsigned2();
                bytecodeVisitor.invokespecial(index);
                break;
            }
            case INVOKESTATIC: {
                final int index = readUnsigned2();
                bytecodeVisitor.invokestatic(index);
                break;
            }
            case INVOKEINTERFACE: {
                final int index = readUnsigned2();
                final int countUnused = readUnsigned1();
                final byte zero = readByte();
                if (zero != 0) {
                    throw verifyError("Fourth operand byte of invokeinterface must be zero");
                }
                bytecodeVisitor.invokeinterface(index, countUnused);
                break;
            }
            case NEW: {
                final int index = readUnsigned2();
                bytecodeVisitor.new_(index);
                break;
            }
            case NEWARRAY: {
                final int tag = readByte();
                bytecodeVisitor.newarray(tag);
                break;
            }
            case ANEWARRAY: {
                final int index = readUnsigned2();
                bytecodeVisitor.anewarray(index);
                break;
            }
            case ARRAYLENGTH: {
                bytecodeVisitor.arraylength();
                break;
            }
            case ATHROW: {
                bytecodeVisitor.athrow();
                break;
            }
            case CHECKCAST: {
                final int index = readUnsigned2();
                bytecodeVisitor.checkcast(index);
                break;
            }
            case INSTANCEOF: {
                final int index = readUnsigned2();
                bytecodeVisitor.instanceof_(index);
                break;
            }
            case MONITORENTER: {
                bytecodeVisitor.monitorenter();
                break;
            }
            case MONITOREXIT: {
                bytecodeVisitor.monitorexit();
                break;
            }
            case WIDE: {
                bytecodeVisitor.wide();
                wide();
                break;
            }
            case MULTIANEWARRAY: {
                final int index = readUnsigned2();
                final int nDimensions = readUnsigned1();
                bytecodeVisitor.multianewarray(index, nDimensions);
                break;
            }
            case IFNULL: {
                final int offset = readSigned2();
                bytecodeVisitor.ifnull(offset);
                break;
            }
            case IFNONNULL: {
                final int offset = readSigned2();
                bytecodeVisitor.ifnonnull(offset);
                break;
            }
            case GOTO_W: {
                final int offset = readSigned4();
                bytecodeVisitor.goto_w(offset);
                break;
            }
            case JSR_W: {
                final int offset = readSigned4();
                bytecodeVisitor.jsr_w(offset);
                break;
            }
            case BREAKPOINT: {
                bytecodeVisitor.breakpoint();
                break;
            }
            case CALLNATIVE: {
                final int nativeFunctionDescriptorIndex = readUnsigned2();
                bytecodeVisitor.callnative(nativeFunctionDescriptorIndex);
                break;
            }
            default: {
                throw verifyError("Unsupported bytecode: " + currentOpcode);
            }
        }
        bytecodeVisitor.instructionDecoded();
    }

    public int scanInstruction(BytecodeBlock block) {
        this.bytecodeBlock = block;
        try {
            currentBytePosition = block.start();
            currentOpcodePosition = currentBytePosition;
            scanInstruction();
            return currentBytePosition;
        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
            if (currentBytePosition > block.end()) {
                throw verifyError("Ran off end of code");
            }
            throw arrayIndexOutOfBoundsException;
        }
    }

    public int scanInstruction(byte[] bytecode, int startAddress) {
        return scanInstruction(new BytecodeBlock(bytecode, startAddress, bytecode.length));
    }

    public void scan(BytecodeBlock block) {
        this.bytecodeBlock = block;
        try {
            currentBytePosition = block.start();
            currentOpcodePosition = currentBytePosition;
            bytecodeVisitor.prologue();
            while (!stopped && currentBytePosition <= block.end()) {
                currentOpcodePosition = currentBytePosition;
                scanInstruction();
            }
        } catch (ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException) {
            if (currentBytePosition > block.end()) {
                throw verifyError("Ran off end of code");
            }
            throw arrayIndexOutOfBoundsException;
        }
    }

    public void scan(ClassMethodActor classMethodActor) {
        scan(new BytecodeBlock(classMethodActor.codeAttribute().code()));
    }

    public void skipBytes(int numBytes) {
        currentBytePosition += numBytes;
    }


}
