/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.vm.compiler.target.amd64;

import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.cri.intrinsics.*;
import com.sun.cri.ci.*;
import com.sun.max.annotate.*;
import com.sun.max.lang.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.stack.*;
import com.sun.max.vm.stack.amd64.*;

/**
 * A utility class factoring out code common to all AMD64 target method.
 */
public final class AMD64TargetMethodUtil {

    /**
     * Opcode of a RIP-relative call instruction.
     */
    public static final int RIP_CALL = 0xe8;

    /**
     * Opcode of a register-based call instruction.
     */
    public static final int REG_CALL = 0xff;

    /**
     * Opcode of a RIP-relative jump instruction.
     */
    public static final int RIP_JMP = 0xe9;

    /**
     * Opcode of a (near) return instruction.
     */
    public static final int RET = 0xc3;

    /**
     * Size (in bytes) of a RIP-relative call instruction.
     */
    public static final int RIP_CALL_INSTRUCTION_SIZE = 5;

    /**
     * Lock to avoid race on concurrent icache invalidation when patching target methods.
     */
    private static final Object PatchingLock = new Object(); // JavaMonitorManager.newVmLock("PATCHING_LOCK");

    public static int registerReferenceMapSize() {
        return UnsignedMath.divide(AMD64.cpuRegisters.length, Bytes.WIDTH);
    }

    public static boolean isPatchableCallSite(CodePointer callSite) {
        // We only update the disp of the call instruction.
        // The compiler(s) ensure that disp of the call be aligned to a word boundary.
        // This may cause up to 7 nops to be inserted before a call.
        final Address callSiteAddress = callSite.toAddress();
        final Address endOfCallSite = callSiteAddress.plus(RIP_CALL_INSTRUCTION_LENGTH - 1);
        return callSiteAddress.plus(1).isWordAligned() ? true :
        // last byte of call site:
        callSiteAddress.roundedDownBy(8).equals(endOfCallSite.roundedDownBy(8));
    }

    /**
     * Gets the target of a 32-bit relative CALL instruction.
     *
     * @param tm the method containing the CALL instruction
     * @param callPos the offset within the code of {@code targetMethod} of the CALL
     * @return the absolute target address of the CALL
     */
    public static CodePointer readCall32Target(TargetMethod tm, int callPos) {
        final CodePointer callSite = tm.codeAt(callPos);
        int disp32;
        if (MaxineVM.isHosted()) {
            final byte[] code = tm.code();
            assert code[0] == (byte) RIP_CALL;
            disp32 =
                (code[callPos + 4] & 0xff) << 24 |
                (code[callPos + 3] & 0xff) << 16 |
                (code[callPos + 2] & 0xff) << 8 |
                (code[callPos + 1] & 0xff) << 0;
        } else {
            final Pointer callSitePointer = callSite.toPointer();
            assert callSitePointer.readByte(0) == (byte) RIP_CALL
                // deopt might replace the first call in a method with a jump (redirection)
                || (callSitePointer.readByte(0) == (byte) RIP_JMP && callPos == 0)
                : callSitePointer.readByte(0);
            disp32 = callSitePointer.readInt(1);
        }
        return callSite.plus(RIP_CALL_INSTRUCTION_LENGTH).plus(disp32);
    }


    /**
     * Patches the offset operand of a 32-bit relative CALL instruction.
     *
     * @param tm the method containing the CALL instruction
     * @param callOffset the offset within the code of {@code targetMethod} of the CALL to be patched
     * @param target the absolute target address of the CALL
     * @return the target of the call prior to patching
     */
    public static CodePointer fixupCall32Site(TargetMethod tm, int callOffset, CodePointer target) {
        CodePointer callSite = tm.codeAt(callOffset);
        if (!isPatchableCallSite(callSite)) {
            // Every call site that is fixed up here might also be patched later.  To avoid failed patching,
            // check for alignment of call site also here.
            // TODO(cwi): This is a check that I would like to have, however, T1X does not ensure proper alignment yet when it stitches together templates that contain calls.
            // FatalError.unexpected(" invalid patchable call site:  " + targetMethod + "+" + offset + " " + callSite.toHexString());
        }

        long disp64 = target.toLong() - callSite.plus(RIP_CALL_INSTRUCTION_LENGTH).toLong();
        int disp32 = (int) disp64;
        int oldDisp32;
        FatalError.check(disp64 == disp32, "Code displacement out of 32-bit range");
        if (MaxineVM.isHosted()) {
            final byte[] code = tm.code();
            oldDisp32 =
                (code[callOffset + 4] & 0xff) << 24 |
                (code[callOffset + 3] & 0xff) << 16 |
                (code[callOffset + 2] & 0xff) << 8 |
                (code[callOffset + 1] & 0xff) << 0;
            if (oldDisp32 != disp32) {
                code[callOffset] = (byte) RIP_CALL;
                code[callOffset + 1] = (byte) disp32;
                code[callOffset + 2] = (byte) (disp32 >> 8);
                code[callOffset + 3] = (byte) (disp32 >> 16);
                code[callOffset + 4] = (byte) (disp32 >> 24);
            }
        } else {
            final Pointer callSitePointer = callSite.toPointer();
            oldDisp32 =
                (callSitePointer.readByte(4) & 0xff) << 24 |
                (callSitePointer.readByte(3) & 0xff) << 16 |
                (callSitePointer.readByte(2) & 0xff) << 8 |
                (callSitePointer.readByte(1) & 0xff) << 0;
            if (oldDisp32 != disp32) {
                callSitePointer.writeByte(0, (byte) RIP_CALL);
                callSitePointer.writeByte(1, (byte) disp32);
                callSitePointer.writeByte(2, (byte) (disp32 >> 8));
                callSitePointer.writeByte(3, (byte) (disp32 >> 16));
                callSitePointer.writeByte(4, (byte) (disp32 >> 24));
            }
        }
        return callSite.plus(RIP_CALL_INSTRUCTION_LENGTH).plus(oldDisp32);
    }

    private static final int RIP_CALL_INSTRUCTION_LENGTH = 5;

    private static final int RIP_JMP_INSTRUCTION_LENGTH = 5;

    /**
     * Thread safe patching of the displacement field in a direct call.
     *
     * @return the target of the call prior to patching
     */
    public static CodePointer mtSafePatchCallDisplacement(TargetMethod tm, CodePointer callSite, CodePointer target) {
        if (!isPatchableCallSite(callSite)) {
            throw FatalError.unexpected(" invalid patchable call site:  " + callSite.toHexString());
        }
        final Pointer callSitePointer = callSite.toPointer();
        long disp64 = target.toLong() - callSite.plus(RIP_CALL_INSTRUCTION_LENGTH).toLong();
        int disp32 = (int) disp64;
        FatalError.check(disp64 == disp32, "Code displacement out of 32-bit range");
        int oldDisp32 = callSitePointer.readInt(1);
        if (oldDisp32 != disp64) {
            synchronized (PatchingLock) {
                // Just to prevent concurrent writing and invalidation to the same instruction cache line
                // (although the lock excludes ALL concurrent patching)
                callSitePointer.writeInt(1,  disp32);
                // Don't need icache invalidation to be correct (see AMD64's Architecture Programmer Manual Vol.2, p173 on self-modifying code)
            }
        }
        return callSite.plus(RIP_CALL_INSTRUCTION_LENGTH).plus(oldDisp32);
    }

    /**
     * Patches a position in a target method with a direct jump to a given target address.
     *
     * @param tm the target method to be patched
     * @param pos the position in {@code tm} at which to apply the patch
     * @param target the target of the jump instruction being patched in
     */
    public static void patchWithJump(TargetMethod tm, int pos, CodePointer target) {
        // We must be at a global safepoint to safely patch TargetMethods
        FatalError.check(VmOperation.atSafepoint(), "should only be patching entry points when at a safepoint");

        final Pointer patchSite = tm.codeAt(pos).toPointer();

        long disp64 = target.toLong() - patchSite.plus(RIP_JMP_INSTRUCTION_LENGTH).toLong();
        int disp32 = (int) disp64;
        FatalError.check(disp64 == disp32, "Code displacement out of 32-bit range");

        patchSite.writeByte(0, (byte) RIP_JMP);
        patchSite.writeByte(1, (byte) disp32);
        patchSite.writeByte(2, (byte) (disp32 >> 8));
        patchSite.writeByte(3, (byte) (disp32 >> 16));
        patchSite.writeByte(4, (byte) (disp32 >> 24));
    }

    /**
     * Indicate with the instruction in a target method at a given position is a jump to a specified destination.
     * Used in particular for testing if the entry points of a target method were patched to jump to a trampoline.
     *
     * @param tm a target method
     * @param pos byte index relative to the start of the method to a call site
     * @param jumpTarget target to compare with the target of the assumed jump instruction
     * @return {@code true} if the instruction is a jump to the target, false otherwise
     */
    public static boolean isJumpTo(TargetMethod tm, int pos, CodePointer jumpTarget) {
        final Pointer jumpSite = tm.codeAt(pos).toPointer();
        if (jumpSite.readByte(0) == (byte) RIP_JMP) {
            final int disp32 = jumpSite.readInt(1);
            final Pointer target = jumpSite.plus(RIP_CALL_INSTRUCTION_LENGTH).plus(disp32);
            return jumpTarget.toPointer().equals(target);
        }
        return false;
    }

    // Disable instance creation.
    private AMD64TargetMethodUtil() {
    }

    @HOSTED_ONLY
    public static boolean atFirstOrLastInstruction(StackFrameCursor current) {
        // check whether the current ip is at the first instruction or a return
        // which means the stack pointer has not been adjusted yet (or has already been adjusted back)
        TargetMethod tm = current.targetMethod();
        CodePointer entryPoint = tm.callEntryPoint.equals(CallEntryPoint.C_ENTRY_POINT) ?
            CallEntryPoint.C_ENTRY_POINT.in(tm) :
            CallEntryPoint.OPTIMIZED_ENTRY_POINT.in(tm);

        return entryPoint.equals(current.vmIP()) || current.stackFrameWalker().readByte(current.vmIP().toAddress(), 0) == RET;
    }

    @HOSTED_ONLY
    public static boolean acceptStackFrameVisitor(StackFrameCursor current, StackFrameVisitor visitor) {
        AdapterGenerator generator = AdapterGenerator.forCallee(current.targetMethod());
        Pointer sp = current.sp();
        // Only during a stack walk in the context of the Inspector can execution
        // be anywhere other than at a safepoint.
        if (atFirstOrLastInstruction(current) || (generator != null && generator.inPrologue(current.vmIP(), current.targetMethod()))) {
            sp = sp.minus(current.targetMethod().frameSize());
        }
        StackFrameWalker stackFrameWalker = current.stackFrameWalker();
        StackFrame stackFrame = new AMD64JavaStackFrame(stackFrameWalker.calleeStackFrame(), current.targetMethod(), current.vmIP().toPointer(), sp, sp);
        return visitor.visitFrame(stackFrame);
    }

    public static VMFrameLayout frameLayout(TargetMethod tm) {
        return new OptoStackFrameLayout(tm.frameSize(), true, AMD64.rsp);
    }

    /**
     * Advances the stack walker such that {@code current} becomes the callee.
     *
     * @param current the frame just visited by the current stack walk
     * @param csl the layout of the callee save area in {@code current}
     * @param csa the address of the callee save area in {@code current}
     */
    public static void advance(StackFrameCursor current, CiCalleeSaveLayout csl, Pointer csa) {
        assert csa.isZero() == (csl == null);
        TargetMethod tm = current.targetMethod();
        Pointer sp = current.sp();
        Pointer ripPointer = sp.plus(tm.frameSize());
        if (MaxineVM.isHosted()) {
            // Only during a stack walk in the context of the Inspector can execution
            // be anywhere other than at a safepoint.
            AdapterGenerator generator = AdapterGenerator.forCallee(current.targetMethod());
            if (generator != null && generator.advanceIfInPrologue(current)) {
                return;
            }
            if (atFirstOrLastInstruction(current)) {
                ripPointer = sp;
            }
        }

        StackFrameWalker sfw = current.stackFrameWalker();
        Pointer callerIP = sfw.readWord(ripPointer, 0).asPointer();
        Pointer callerSP = ripPointer.plus(Word.size()); // Skip return instruction pointer on stack
        Pointer callerFP;
        if (!csa.isZero() && csl.contains(AMD64.rbp.number)) {
            // Read RBP from the callee save area
            callerFP = sfw.readWord(csa, csl.offsetOf(AMD64.rbp)).asPointer();
        } else {
            // Propagate RBP unchanged
            callerFP = current.fp();
        }

        current.setCalleeSaveArea(csl, csa);

        boolean wasDisabled = SafepointPoll.disable();
        sfw.advance(callerIP, callerSP, callerFP);
        if (!wasDisabled) {
            SafepointPoll.enable();
        }
    }

    public static Pointer returnAddressPointer(StackFrameCursor frame) {
        TargetMethod tm = frame.targetMethod();
        Pointer sp = frame.sp();
        return sp.plus(tm.frameSize());
    }

    public static int callInstructionSize(byte[] code, int pos) {
        if ((code[pos] & 0xFF) == RIP_CALL) {
            return RIP_CALL_INSTRUCTION_SIZE;
        }
        if ((code[pos] & 0xff) == REG_CALL) {
            return 2;
        }
        if ((code[pos + 1] & 0xff) == REG_CALL) {
            return 3;
        }
        return -1;
    }
}
