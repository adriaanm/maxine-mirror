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
package com.sun.max.vm.verifier.types;

import java.util.*;


/**
 * This class is used when verifying subroutines. This is best explained by the following excerpt from a <a
 * href="http://java.sun.com/sfaq/verifier.html">site<a> discussing the initial Java bytecode verifier. This excerpt
 * discusses implementing Java's {@code try/finally} construct with subroutines:
 * <p>
 * To implement this construct, the Java compiler uses the exception handling facilities, together with two special
 * instructions, <tt>jsr</tt> (jump to subroutine) and <tt>ret</tt> (return from subroutine). The cleanup code is
 * compiled as a subroutine. When it is called, the top object on the stack will be the return position; this return
 * position is saved in a register. At the end of the cleanup code, it performs a <tt>ret</tt> to return to whatever
 * code called the cleanup.
 * <p>
 *
 * To implement <tt>try</tt>/<tt>finally</tt>, a special exception handler is set up around the public code
 * which catches all exceptions. This exception handler:
 * <p>
 *
 * <ol>
 * <li>Saves the exception in a register.
 * <li>Executes a <tt>jsr</tt> to the cleanup code.
 * <li>Upon return from the exception, re-<tt>throw'</tt>s the exception.
 * </ol>
 * If the public code has a <tt>return</tt>, it performs the following code:
 * <p>
 *
 * <ol>
 *
 * <li>Saves the return value (if any) in a register.
 * <li>Executes a <tt>jsr</tt> to the cleanup code.
 * <li>Upon return from the exception, returns the value saved in the register.
 * </ol>
 * Breaks or continues inside the public code that go to outside the public code execute a <tt>jsr</tt> to the
 * cleanup code before performing their <tt>goto</tt>. Likewise, at the end of the public code is a <tt>jsr</tt>
 * to the cleanup code.
 * <p>
 *
 * The cleanup code presents a special problem to the verifier. Usually, if a particular instruction can be reached via
 * multiple paths and a particular register contains incompatible values through those multiple paths, then the register
 * becomes unusable. However, a particular piece of cleanup code might be called from several different places:
 * <p>
 *
 * <ul>
 * <li>The call from the exception handler will have a certain register containing an exception.
 * <li>The call to implement "return" will have some register containing the return value.
 * <li>The call from the bottom of the public code may have trash in that same register.
 * </ul>
 * The cleanup code may pass verification, but after updating all the successors of the <tt>ret</tt> instruction, the
 * verifier will note that the register that the exception handler expects to hold an exception or that the return code
 * expects to hold a return value now contains trash.
 * <p>
 *
 * Verifying code that contains <tt>finally</tt>'s can be somewhat complicated. Fortunately, most code does not have
 * <tt>finally</tt>'s. The basic idea is the following:
 * <p>
 *
 * <ul>
 * <li>Each instruction keeps track of the smallest number of <tt>jsr</tt> targets needed to reach that instruction.
 * For most code, this field will be empty. For instructions inside cleanup code, it will be of length one. For
 * multiply-nested cleanup code (extremely rare!), it may be longer than one.
 * <li>For each instruction and each <tt>jsr</tt> needed to reach that instruction, a bit vector is maintained of all
 * registers accessed or modified since the execution of the <tt>jsr</tt> instruction.
 * <li>When executing the <tt>ret</tt> from a subroutine, there must be only one possible subroutine target from
 * which the instruction can be returning. Two different targets of <tt>jsr</tt> instructions cannot "merge"
 * themselves into a single <tt>ret</tt> instruction.
 *
 * <li>When performing the data-flow analysis on a <tt>ret</tt> instruction, modify the directions given above. Since
 * the verifier knows the target of the <tt>jsr</tt> from which the instruction must be returning, it can find all the
 * <tt>jsr</tt>'s to the target, and merge the state of the stack and registers at the time of the <tt>ret</tt>
 * instruction into the stack and registers of the instructions following the <tt>jsr</tt> using a special set of
 * values for the registers:
 * <li>For any register that the bit vector (constructed above) indicates that the subroutine has accessed or modified,
 * use the type of the register at the time of the <tt>ret</tt>.
 *
 * <li>For other registers, use the type of the register at the time of the preceding <tt>jsr</tt> instruction.
 * </ul>
 *
 * @author Doug Simon
 */
public class Subroutine extends Category1Type {

    /**
     * The position at which the subroutine was entered (i.e. the target of one or more JSR instructions).
     */
    public final int entryPosition;

    /**
     * The positions of the successors of all the JSR instructions that enter this subroutine.
     */
    private int[] retTargets = {};

    /**
     * The positions of the RET instructions that effect a return from this subroutine.
     */
    private int[] retInstructions = {};

    /**
     * A mask of local variables mask denoting those accessed (i.e. read or written) within the subroutine.
     */
    private final boolean[] accessedVariableMask;

    public Subroutine(int entryPosition, int maxLocals) {
        assert SUBROUTINE == null || entryPosition != -1;
        this.entryPosition = entryPosition;
        this.accessedVariableMask = new boolean[maxLocals];
    }

    /**
     * Gets the positions of the successors of all the JSR instructions that enter this subroutine.
     *
     * @return the bytecode positions to which this subroutine returns
     */
    public int[] retTargets() {
        return retTargets;
    }

    /**
     * Gets the positions of the RET instructions that effect a return from this subroutine.
     */
    public int[] retInstructions() {
        return retInstructions;
    }

    /**
     * Records that the local variable at a given index is accessed (i.e. read or written) within this subroutine.
     */
    public void accessesVariable(int index) {
        accessedVariableMask[index] = true;
    }

    /**
     * Determines if the local variable at a given index is accessed (i.e. read or written) within this subroutine.
     */
    public boolean isVariableAccessed(int index) {
        return accessedVariableMask[index];
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("subroutine{entry = ").
            append(entryPosition).
            append(", ret instructions = " + Arrays.toString(retInstructions)).
            append(", ret targets = " + Arrays.toString(retTargets)).
            append(", accessed variables = [");
        String separator = "";
        for (int i = 0; i < accessedVariableMask.length; i++) {
            if (accessedVariableMask[i]) {
                sb.append(separator).append(i);
                separator = ",";
            }
        }
        return sb.append("]}").toString();
    }

    /**
     * Adds a bytecode position to which this subroutine returns.
     */
    public void addRetTarget(int position) {
        assert !containsRetTarget(position);
        retTargets = Arrays.copyOf(retTargets, retTargets.length + 1);
        retTargets[retTargets.length - 1] = position;
    }

    public boolean containsRetTarget(int position) {
        for (int retTarget : retTargets) {
            if (retTarget == position) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds a bytecode position of a RET instruction that effects a return from this subroutine.
     */
    public void addRetInstruction(int position) {
        if (!containsRetInstruction(position)) {
            retInstructions = Arrays.copyOf(retInstructions, retInstructions.length + 1);
            retInstructions[retInstructions.length - 1] = position;
        }
    }

    public boolean containsRetInstruction(int position) {
        for (int retInstruction : retInstructions) {
            if (retInstruction == position) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isAssignableFromDifferentType(VerificationType from) {
        return this == SUBROUTINE && from instanceof Subroutine;
    }
}
