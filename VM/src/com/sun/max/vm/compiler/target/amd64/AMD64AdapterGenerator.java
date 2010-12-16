/*
 * Copyright (c) 2009 Sun Microsystems, Inc.  All rights reserved.
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
package com.sun.max.vm.compiler.target.amd64;

import static com.sun.c1x.target.amd64.AMD64.*;
import static com.sun.cri.ci.CiCallingConvention.Type.*;
import static com.sun.max.platform.Platform.*;
import static com.sun.max.vm.VMConfiguration.*;
import static com.sun.max.vm.compiler.CallEntryPoint.*;

import java.io.*;

import com.sun.c1x.asm.*;
import com.sun.c1x.target.amd64.*;
import com.sun.cri.ci.*;
import com.sun.max.annotate.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.collect.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.compiler.target.Adapter.Type;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.stack.*;
import com.sun.max.vm.stack.StackFrameWalker.Cursor;

/**
 * Adapter generators for AMD64.
 *
 * @author Doug Simon
 */
public abstract class AMD64AdapterGenerator extends AdapterGenerator {

    final CiRegister scratch;

    static {
        if (vmConfig().needsAdapters()) {
            // Create and register the adapter generators
            new Jit2Opt();
            new Opt2Jit();
        }
    }

    public AMD64AdapterGenerator(Adapter.Type adapterType) {
        super(adapterType);
        scratch = opt.getScratchRegister();
    }

    @Override
    public boolean advanceIfInPrologue(Cursor current) {
        if (inPrologue(current.ip(), current.targetMethod())) {
            StackFrameWalker stackFrameWalker = current.stackFrameWalker();
            final Pointer callerIP = stackFrameWalker.readWord(current.sp(), 0).asPointer();
            Pointer callerSP = current.sp().plus(Word.size()); // skip RIP
            stackFrameWalker.advance(callerIP, callerSP, current.fp(), true);
            return true;
        }
        return false;
    }

    /**
     * AMD64 specific generator for {@link Type#JIT2OPT} adapters.
     */
    public static class Jit2Opt extends AMD64AdapterGenerator {

        /**
         * AMD64 specific {@link Type#JIT2OPT} adapters.
         */
        public static class Jit2OptAdapter extends Adapter {

            Jit2OptAdapter(AdapterGenerator generator, String description, int frameSize, byte[] code, int callPosition) {
                super(generator, description, frameSize, code, callPosition);
            }

            /**
             * Computes the state of an adapter frame based on an execution point in this adapter.
             *
             * This complex computation is only necessary for the Inspector as that is the only context
             * in which the current execution point in an adapter can be anywhere except for in
             * the call back to the callee being adapted. Hence the {@link HOSTED_ONLY} annotation.
             *
             * @param cursor a stack frame walker cursor denoting an execution point in this adapter
             * @return the amount by which {@code current.sp()} should be adjusted to obtain the slot holding the
             *         address to which the adapter will return. If the low bit of this value is set, then it means that
             *         the caller's RBP is obtained by reading the word at {@code current.fp()} otherwise it is equal to
             *         {@code current.fp()}.
             */
            @HOSTED_ONLY
            private int computeFrameState(Cursor cursor) {
                int ripAdjustment = frameSize();
                boolean rbpSaved = false;
                StackFrameWalker stackFrameWalker = cursor.stackFrameWalker();
                int position = cursor.ip().minus(codeStart).toInt();

                byte b = stackFrameWalker.readByte(cursor.ip(), 0);
                if (position == 0 || b == ENTER) {
                    ripAdjustment = Word.size();
                } else if (b == RET2) {
                    ripAdjustment = 0;
                } else if (b == REXW) {
                    b = stackFrameWalker.readByte(cursor.ip(), 1);
                    if (b == ADDQ_SUBQ_imm8) {
                        ripAdjustment = Word.size();
                    } else if (b == ADDQ_SUBQ_imm32) {
                        ripAdjustment = Word.size();
                    }
                } else {
                    rbpSaved = true;
                }

                assert (ripAdjustment & 1) == 0;
                return ripAdjustment | (rbpSaved ? 1 : 0);
            }

            @Override
            public void advance(Cursor current) {
                StackFrameWalker stackFrameWalker = current.stackFrameWalker();
                int ripAdjustment = frameSize();
                boolean rbpSaved = true;
                if (MaxineVM.isHosted()) {
                    // Inspector context only
                    int state = computeFrameState(current);
                    ripAdjustment = state & ~1;
                    rbpSaved = (state & 1) == 1;
                }

                Pointer ripPointer = current.sp().plus(ripAdjustment);
                Pointer callerIP = stackFrameWalker.readWord(ripPointer, 0).asPointer();
                Pointer callerSP = ripPointer.plus(Word.size()); // Skip RIP word
                Pointer callerFP = rbpSaved ? stackFrameWalker.readWord(ripPointer, -Word.size() * 2).asPointer() : current.fp();
                stackFrameWalker.advance(callerIP, callerSP, callerFP, true);
            }

            @Override
            public boolean acceptStackFrameVisitor(Cursor current, StackFrameVisitor visitor) {
                int ripAdjustment = MaxineVM.isHosted() ? computeFrameState(current) & ~1 : frameSize();
                Pointer ripPointer = current.sp().plus(ripAdjustment);
                Pointer fp = ripPointer.minus(frameSize());
                return visitor.visitFrame(new AdapterStackFrame(current.stackFrameWalker().calleeStackFrame(), new Jit2OptAdapterFrameLayout(frameSize()), current.targetMethod(), current.ip(), fp, current.sp()));
            }
        }

        /**
         * A specialization of an AMD64 specific {@link Type#JIT2OPT} adapter that contains a reference map occupying 64 or less bits.
         */
        public static class Jit2OptAdapterWithRefMap extends Jit2OptAdapter {

            final long refMap;

            public Jit2OptAdapterWithRefMap(AdapterGenerator generator, String description, long refMap, int frameSize, byte[] code, int callPosition) {
                super(generator, description, frameSize, code, callPosition);
                this.refMap = refMap;
            }

            @Override
            public void prepareReferenceMap(Cursor current, Cursor callee, StackReferenceMapPreparer preparer) {
                preparer.tracePrepareReferenceMap(this, 0, current.sp(), "frame");
                int frameSlotIndex = preparer.referenceMapBitIndex(current.sp());
                int byteIndex = 0;
                long refMap = this.refMap;
                for (int i = 0; i < 8; i++) {
                    final byte refMapByte = (byte) (refMap & 0xff);
                    preparer.traceReferenceMapByteBefore(byteIndex, refMapByte, "Frame");
                    preparer.setBits(frameSlotIndex, refMapByte);
                    preparer.traceReferenceMapByteAfter(current.sp(), frameSlotIndex, refMapByte);
                    refMap >>>= Bytes.WIDTH;
                    frameSlotIndex += Bytes.WIDTH;
                    byteIndex++;
                }
            }
        }

        /**
         * A specialization of an AMD64 specific {@link Type#JIT2OPT} adapter that contains a reference map occupying more than 64 bits.
         */
        public static class Jit2OptAdapterWithBigRefMap extends Jit2OptAdapter {

            final byte[] refMap;

            public Jit2OptAdapterWithBigRefMap(AdapterGenerator generator, String description, byte[] refMap, int frameSize, byte[] code, int callPosition) {
                super(generator, description, frameSize, code, callPosition);
                this.refMap = refMap;
            }

            @Override
            public void prepareReferenceMap(Cursor current, Cursor callee, StackReferenceMapPreparer preparer) {
                preparer.tracePrepareReferenceMap(this, 0, current.sp(), "frame");
                int frameSlotIndex = preparer.referenceMapBitIndex(current.sp());
                int byteIndex = 0;
                for (int i = 0; i < refMap.length; i++) {
                    final byte frameReferenceMapByte = refMap[byteIndex];
                    preparer.traceReferenceMapByteBefore(byteIndex, frameReferenceMapByte, "Frame");
                    preparer.setBits(frameSlotIndex, frameReferenceMapByte);
                    preparer.traceReferenceMapByteAfter(current.sp(), frameSlotIndex, frameReferenceMapByte);
                    frameSlotIndex += Bytes.WIDTH;
                    byteIndex++;
                }
            }
        }

        /**
         * Frame layout for an AMD64 specific {@link Type#JIT2OPT} adapter frame.
         * <pre>
         *
         *          +------------------------+
         *          |     JIT caller RIP     |
         *          +------------------------+     ---
         *          |     OPT main body      |      ^
         *          +------------------------+      |
         *          |       saved RBP        |      |
         *          +------------------------+  frame size
         *          |        OPT arg N       |      |
         *          +------------------------+      |
         *          :                        :      |
         *          +------------------------+      |
         *   RSP--> |        OPT arg R       |      v
         *          +------------------------+     ---
         *
         *   N == number of args - 1
         *   R == number of register args
         * </pre>
         */
        public static class Jit2OptAdapterFrameLayout extends AdapterStackFrameLayout {

            public Jit2OptAdapterFrameLayout(int frameSize) {
                super(frameSize, true);
            }

            @Override
            public int frameReferenceMapOffset() {
                return 0;
            }

            @Override
            public int frameReferenceMapSize() {
                return ByteArrayBitMap.computeBitMapSize(Unsigned.idiv(frameSize(), STACK_SLOT_SIZE));
            }

            @Override
            public Slots slots() {
                return new Slots() {
                    @Override
                    protected String nameOfSlot(int offset) {
                        final int offsetOfReturnAddress = frameSize();
                        if (offset == offsetOfReturnAddress) {
                            return "return address";
                        }
                        final int offsetOfOptPrologueCallReturnAddress = offsetOfReturnAddress - Word.size();
                        if (offset == offsetOfOptPrologueCallReturnAddress) {
                            return "prologue return";
                        }
                        final int callersRBPOffset = offsetOfOptPrologueCallReturnAddress - Word.size();
                        if (offset == callersRBPOffset) {
                            return "caller's FP";
                        }
                        return "stack arg " + (offset / Word.size());
                    }
                };
            }
        }

        private static final int PROLOGUE_SIZE = 8;

        public Jit2Opt() {
            super(Adapter.Type.JIT2OPT);
        }

        @Override
        public int prologueSizeForCallee(ClassMethodActor callee) {
            return PROLOGUE_SIZE;
        }

        /**
         * The prologue for a method with a JIT2OPT adapter has a call to the adapter
         * at the {@link CallEntryPoint#JIT_ENTRY_POINT}. The body of the method starts at the
         * {@link CallEntryPoint#OPTIMIZED_ENTRY_POINT}. The assembler code is as follows:
         * <pre>
         *     +0:  call <JIT2OPT adapter>
         *     +5:  nop
         *     +6:  nop
         *     +7:  nop
         *     +8:  // method body
         * </pre>
         */
        @Override
        protected int emitPrologue(Object out, Adapter adapter) {
            AMD64Assembler asm = out instanceof OutputStream ? new AMD64Assembler(target(), null) : (AMD64Assembler) out;

            if (adapter == null) {
                asm.nop();
                asm.nop();
                asm.nop();
                asm.nop();
                asm.nop();
                asm.nop();
                asm.nop();
                asm.nop();
            } else {

                // This instruction is 5 bytes long
                asm.directCall(adapter, null);

                // Pad with 3 bytes to yield an 8-byte long prologue,
                asm.nop();
                asm.nop();
                asm.nop();
            }
            int size = asm.codeBuffer.position();
            assert size == PROLOGUE_SIZE;
            copyIfOutputStream(asm.codeBuffer, out);
            return size;
        }

        @Override
        public void linkAdapterCallInPrologue(TargetMethod targetMethod, Adapter adapter) {
            targetMethod.fixupCallSite(0, adapter.codeStart());
        }

        /**
         * Creates a JIT2OPT adapter.
         *
         * @see Jit2OptAdapterFrameLayout
         */
        @Override
        protected Adapter create(Sig sig) {
            CiValue[] optArgs = opt.getCallingConvention(JavaCall, sig.kinds, target()).locations;

            AMD64Assembler asm = new AMD64Assembler(target(), null);

            // On entry to the frame, there are 2 return addresses on the stack at [RSP] and [RSP + 8].
            // The one at [RSP] is the return address of the call in the OPT callee's prologue (which is
            // also the entry to the main body of the OPT callee) and one at [RSP + 8] is the return
            // address in the JIT caller.

            // Save the address of the OPT callee's main body in RAX
            asm.movq(rax, new CiAddress(CiKind.Word, rsp.asValue()));

            // Compute the number of stack args needed for the call (i.e. the args that won't
            // be put into registers)
            int stackArgumentsSize = 0;
            for (int i = optArgs.length - 1; i >= 0; i--) {
                if (optArgs[i].isStackSlot()) {
                    stackArgumentsSize += OPT_SLOT_SIZE;
                }
            }

            final int rbpSlotSize = OPT_SLOT_SIZE;
            final int optCallerRIPSlotSize = OPT_SLOT_SIZE;
            final int jitCallerRIPSlotSize = OPT_SLOT_SIZE;

            // The amount by which RSP is adjusted by CALL and ENTER instructions
            final int implicitlyAllocatedFrameSize = optCallerRIPSlotSize + jitCallerRIPSlotSize + rbpSlotSize;

            // The adapter frame size does not include the slot holding the JIT caller's RIP.
            // It must also be aligned according platform's stack frame alignment requirements.
            int adapterFrameSize = target().alignFrameSize(stackArgumentsSize + implicitlyAllocatedFrameSize - optCallerRIPSlotSize);

            // The amount by which RSP must be explicitly adjusted to create the adapter frame
            final int explicitlyAllocatedFrameSize = adapterFrameSize - rbpSlotSize - jitCallerRIPSlotSize;

            // Allocate the frame and save RBP to the stack with an ENTER instruction
            assert explicitlyAllocatedFrameSize >= 0 && explicitlyAllocatedFrameSize <= Short.MAX_VALUE;
            asm.enter(explicitlyAllocatedFrameSize, 0);

            // At this point, the top of the JIT caller's stack (i.e the last arg to the call) is immediately
            // above the adapter's RIP slot. That is, it's at RSP + adapterFrameSize + OPT_SLOT_SIZE.
            int jitStackOffset = adapterFrameSize + OPT_SLOT_SIZE;
            int jitArgsSize = 0;
            CiBitMap refMap = null;
            for (int i = optArgs.length - 1; i >= 0;  i--) {
                CiKind kind = sig.kinds[i];
                int refMapIndex = adaptArgument(asm, kind, optArgs[i], jitStackOffset, 0);
                if (refMapIndex != -1) {
                    if (refMap == null) {
                        refMap = new CiBitMap(adapterFrameSize / Word.size());
                    }
                    refMap.set(refMapIndex);
                }
                int jitArgSize = frameSizeFor(kind, JIT_SLOT_SIZE);
                jitArgsSize += jitArgSize;
                jitStackOffset += jitArgSize;
            }

            // Args are now copied to the OPT locations; call the OPT main body
            int callPosition = asm.codeBuffer.position();
            asm.indirectCall(rax, null, null);

            // Restore RSP and RBP. Given that RBP is never modified by OPT methods and JIT methods always
            // restore it, RBP is guaranteed to be pointing to the slot holding the caller's RBP
            asm.leave();

            String description = Type.JIT2OPT + "-Adapter" + sig;
            // RSP has been restored to the location holding the address of the OPT main body.
            // The adapter must return to the JIT caller whose RIP is one slot higher up.
            asm.addq(rsp, 8);

            assert WordWidth.signedEffective(jitArgsSize).lessEqual(WordWidth.BITS_16);
            // Retract the stack pointer back to its position before the first argument on the caller's stack.
            asm.ret((short) jitArgsSize);

            final byte[] code = asm.codeBuffer.close(true);
            if (refMap != null) {
                if (refMap.size() <= 64) {
                    long longRefMap = refMap.toLong();
                    return new Jit2OptAdapterWithRefMap(this, description, longRefMap, adapterFrameSize, code, callPosition);
                }
                return new Jit2OptAdapterWithBigRefMap(this, description, refMap.toByteArray(), adapterFrameSize, code, callPosition);
            }
            return new Jit2OptAdapter(this, description, adapterFrameSize, code, callPosition);
        }

        // Checkstyle: stop
        @Override
        protected void adapt(AMD64Assembler asm, CiKind kind, CiRegister reg, int offset32) {
            switch (kind) {
                case Byte:     asm.movsxb(reg, new CiAddress(CiKind.Byte, rsp.asValue(), offset32));    break;
                case Boolean:  asm.movzxb(reg, new CiAddress(CiKind.Boolean, rsp.asValue(), offset32)); break;
                case Short:    asm.movsxw(reg, new CiAddress(CiKind.Short, rsp.asValue(), offset32));   break;
                case Char:     asm.movzxl(reg, new CiAddress(CiKind.Char, rsp.asValue(), offset32));    break;
                case Int:      asm.movslq(reg, new CiAddress(CiKind.Int, rsp.asValue(), offset32));     break;
                case Long:
                case Word:
                case Object:   asm.movq(reg, new CiAddress(CiKind.Word, rsp.asValue(), offset32));      break;
                case Float:    asm.movss(reg, new CiAddress(CiKind.Float, rsp.asValue(), offset32));    break;
                case Double:   asm.movsd(reg, new CiAddress(CiKind.Double, rsp.asValue(), offset32));   break;
                default:       throw ProgramError.unexpected();
            }
        }
        // Checkstyle: resume

        @Override
        public void adapt(AMD64Assembler asm, CiKind kind, int optStackOffset32, int jitStackOffset32, int adapterFrameSize) {
            int src = jitStackOffset32;
            int dst = optStackOffset32;
            stackCopy(asm, kind, src, dst);
        }
    }

    /**
     * AMD64 specific generator for {@link Type#OPT2JIT} adapters.
     */
    static class Opt2Jit extends AMD64AdapterGenerator {

        /**
         * AMD64 specific {@link Type#OPT2JIT} adapter.
         */
        static final class Opt2JitAdapter extends Adapter {

            Opt2JitAdapter(AdapterGenerator generator, String description, int frameSize, byte[] code, int callPosition) {
                super(generator, description, frameSize, code, callPosition);
            }

            /**
             * Computes the amount by which the stack pointer in a given {@linkplain Cursor cursor}
             * should be adjusted to obtain the location on the stack holding the RIP to which
             * the adapter will return.
             *
             * This complex computation is only necessary for the Inspector as that is the only context
             * in which the current execution point in an adapter can be anywhere except for in
             * the call to the body of the callee being adapted. Hence the {@link HOSTED_ONLY} annotation.
             *
             * @param cursor a stack frame walker cursor denoting an execution point in this adapter
             */
            @HOSTED_ONLY
            private int computeRipAdjustment(Cursor cursor) {
                int ripAdjustment = frameSize();
                StackFrameWalker stackFrameWalker = cursor.stackFrameWalker();
                int position = cursor.ip().minus(codeStart).toInt();
                if (!cursor.isTopFrame()) {
                    // Inside call to JIT body. The value of RSP in cursor is now the value
                    // that the JIT caller will leave it in after popping the arguments from the stack
                    ripAdjustment = Word.size();
                } else if (position == 0) {
                    ripAdjustment = Word.size();
                } else {
                    switch (stackFrameWalker.readByte(cursor.ip(), 0)) {
                        case REXW:
                            byte b = stackFrameWalker.readByte(cursor.ip(), 1);
                            if (b == ADDQ_SUBQ_imm8) {
                                ripAdjustment = Word.size();
                            } else if (b == ADDQ_SUBQ_imm32) {
                                ripAdjustment = Word.size();
                            }
                            break;
                        case RET2:
                            ripAdjustment = 0;
                    }
                }
                return ripAdjustment;
            }

            @Override
            public void advance(Cursor cursor) {
                int ripAdjustment = MaxineVM.isHosted() ? computeRipAdjustment(cursor) : Word.size();
                StackFrameWalker stackFrameWalker = cursor.stackFrameWalker();

                Pointer ripPointer = cursor.sp().plus(ripAdjustment);
                Pointer callerIP = stackFrameWalker.readWord(ripPointer, 0).asPointer();
                Pointer callerSP = ripPointer.plus(Word.size()); // Skip RIP word
                Pointer callerFP = cursor.fp();
                stackFrameWalker.advance(callerIP, callerSP, callerFP, true);
            }

            @Override
            public boolean acceptStackFrameVisitor(Cursor cursor, StackFrameVisitor visitor) {
                int ripAdjustment = MaxineVM.isHosted() ? computeRipAdjustment(cursor) : Word.size();

                Pointer ripPointer = cursor.sp().plus(ripAdjustment);
                Pointer fp = ripPointer.minus(frameSize());
                return visitor.visitFrame(new AdapterStackFrame(cursor.stackFrameWalker().calleeStackFrame(), new Opt2JitAdapterFrameLayout(frameSize()), cursor.targetMethod(), cursor.ip(), fp, cursor.sp()));
            }
        }

        /**
         * Frame layout for an AMD64 specific {@link Type#OPT2JIT} adapter frame.
         * <pre>
         *
         *          +------------------------+
         *          |     OPT caller RIP     |
         *          +------------------------+     ---
         *          |     JIT main body      |      ^
         *          +------------------------+      |
         *          |       JIT arg 0        |      |
         *          +------------------------+  frame size
         *          :                        :      |
         *          +------------------------+      |
         *   RSP--> |       JIT arg N        |      v
         *          +------------------------+     ---
         *
         *   N == number of args - 1
         * </pre>
         */
        public static class Opt2JitAdapterFrameLayout extends AdapterStackFrameLayout {

            public Opt2JitAdapterFrameLayout(int frameSize) {
                super(frameSize, true);
            }

            @Override
            public Slots slots() {
                return new Slots() {
                    @Override
                    protected String nameOfSlot(int offset) {
                        final int jitPrologueCallReturnAddress = frameSize() - Word.size();
                        if (offset == jitPrologueCallReturnAddress) {
                            return "prologue return";
                        }
                        return super.nameOfSlot(offset);
                    }
                };
            }
        }

        private static final int DIRECT_CALL_SIZE = 5;
        private static final int PROLOGUE_SIZE = 13;
        private static final int PROLOGUE_SIZE_FOR_NO_ARGS_CALLEE = 8;

        Opt2Jit() {
            super(Adapter.Type.OPT2JIT);
            assert JIT_ENTRY_POINT.offset() == 0;
            assert OPTIMIZED_ENTRY_POINT.offset() == 8;
        }

        @Override
        public int prologueSizeForCallee(ClassMethodActor callee) {
            if (callee.descriptor().numberOfParameters() == 0 && callee.isStatic()) {
                return PROLOGUE_SIZE_FOR_NO_ARGS_CALLEE;
            }
            return PROLOGUE_SIZE;
        }

        /**
         * The prologue for a method with an OPT2JIT adapter has a call to the adapter
         * at the {@link CallEntryPoint#OPTIMIZED_ENTRY_POINT}. The code at the
         * {@link CallEntryPoint#JIT_ENTRY_POINT} has a jump over this call to the
         * body of the method. The assembler code is as follows:
         * <pre>
         *     +0:  jmp L1
         *     +8:  call <adapter>
         * L1 +13:  // method body
         * </pre>
         * In the case where there is no adaptation required (i.e. a parameterless call to a static method),
         * the assembler code in the prologue is a series of {@code nop}s up to the {@link CallEntryPoint#OPTIMIZED_ENTRY_POINT}
         * which is where the method body starts. This means that a JIT caller will fall through the {@code nop}s
         * to the method body.
         */
        @Override
        protected int emitPrologue(Object out, Adapter adapter) {
            AMD64Assembler asm = out instanceof OutputStream ? new AMD64Assembler(target(), null) : (AMD64Assembler) out;
            if (adapter == null) {
                asm.nop();
                asm.align(OPTIMIZED_ENTRY_POINT.offset());
                assert asm.codeBuffer.position() == PROLOGUE_SIZE_FOR_NO_ARGS_CALLEE;
                copyIfOutputStream(asm.codeBuffer, out);
                return PROLOGUE_SIZE_FOR_NO_ARGS_CALLEE;
            }

            // A JIT caller jumps over the call to the OPT2JIT adapter
            asm.jmp(new Label(8 + DIRECT_CALL_SIZE));

            // Pad with nops up to the OPT entry point
            asm.align(OPTIMIZED_ENTRY_POINT.offset());

            asm.directCall(adapter, null);

            int size = asm.codeBuffer.position();
            assert size == PROLOGUE_SIZE;
            copyIfOutputStream(asm.codeBuffer, out);
            return size;
        }

        @Override
        public void linkAdapterCallInPrologue(TargetMethod targetMethod, Adapter adapter) {
            targetMethod.fixupCallSite(8, adapter.codeStart());
        }

        /**
         * Creates an OPT2JIT adapter.
         *
         * @see Opt2JitAdapterFrameLayout
         */
        @Override
        protected Adapter create(Sig sig) {
            CiValue[] optArgs = opt.getCallingConvention(JavaCall, sig.kinds, target()).locations;
            AMD64Assembler asm = new AMD64Assembler(target(), null);

            // On entry to the frame, there are 2 return addresses on the JIT at [RSP] and [RSP + 8].
            // The one at [RSP] is the return address of the call in the JIT callee's prologue (which is
            // also the entry to the main body of the JIT callee) and one at [RSP + 8] is the return
            // address in the OPT caller.

            // Save the address of the JIT callee's main body in RAX
            asm.movq(rax, new CiAddress(CiKind.Word, rsp.asValue()));

            // Initial args are in registers, remaining args are on the stack.
            int jitArgsSize = frameSizeFor(sig.kinds, JIT_SLOT_SIZE);
            assert jitArgsSize % target().stackAlignment == 0 : "JIT_SLOT_SIZE should guarantee parametersSize satifies ABI alignment requirements";

            final int optCallerRIPSlotSize = OPT_SLOT_SIZE;
            int adapterFrameSize = jitArgsSize + optCallerRIPSlotSize;

            // Adjust RSP to create space for the JIT args
            asm.subq(rsp, jitArgsSize);

            // Copy OPT args into JIT args
            int jitStackOffset = 0;
            for (int i = optArgs.length - 1; i >= 0; i--) {
                adaptArgument(asm, sig.kinds[i], optArgs[i], jitStackOffset, adapterFrameSize);
                jitStackOffset += frameSizeFor(sig.kinds[i], JIT_SLOT_SIZE);
            }

            // Args are now copied to the JIT locations; call the JIT main body
            int callPosition = asm.codeBuffer.position();
            asm.indirectCall(rax, null, null);

            // The JIT method will have popped the args off the stack so now
            // RSP is pointing to the slot holding the address of the JIT main body.
            // The adapter must return to the OPT caller whose RIP is one slot higher up.
            asm.addq(rsp, OPT_SLOT_SIZE);

            // Return to the OPT caller
            asm.ret(0);

            final byte[] code = asm.codeBuffer.close(true);

            String description = Type.OPT2JIT + "-Adapter" + sig;
            return new Opt2JitAdapter(this, description, adapterFrameSize, code, callPosition);
        }

        // Checkstyle: stop
        @Override
        protected void adapt(AMD64Assembler asm, CiKind kind, CiRegister reg, int offset32) {
            switch (kind) {
                case Byte:
                case Boolean:
                case Short:
                case Char:
                case Int:       asm.movl(new CiAddress(CiKind.Word, rsp.asValue(), offset32), reg);  break;
                case Long:
                case Word:
                case Object:    asm.movq(new CiAddress(CiKind.Word, rsp.asValue(), offset32), reg);  break;
                case Float:     asm.movss(new CiAddress(CiKind.Float, rsp.asValue(), offset32), reg);  break;
                case Double:    asm.movsd(new CiAddress(CiKind.Double, rsp.asValue(), offset32), reg);  break;
                default:        throw ProgramError.unexpected();
            }
        }
        // Checkstyle: resume

        @Override
        public void adapt(AMD64Assembler asm, CiKind kind, int optStackOffset32, int jitStackOffset32, int adapterFrameSize) {
            // Add word size to take into account the slot used by the RIP of the caller
            int src = adapterFrameSize + optStackOffset32 + Word.size();
            int dst = jitStackOffset32;
            stackCopy(asm, kind, src, dst);
        }
    }

    /**
     * Emits code to copy an incoming argument from ABI-specified location of the caller
     * to the ABI-specified location of the callee.
     *
     * @param asm assembler used to emit code
     * @param kind the kind of the argument
     * @param optArg the location of the argument according to the OPT convention
     * @param jitStackOffset32 the 32-bit offset of the argument on the stack according to the JIT convention
     * @param adapterFrameSize the size of the adapter frame
     * @return the reference map index of the reference slot on the adapter frame corresponding to the argument or -1
     */
    protected int adaptArgument(AMD64Assembler asm, CiKind kind, CiValue optArg, int jitStackOffset32, int adapterFrameSize) {
        if (optArg.isRegister()) {
            adapt(asm, kind, optArg.asRegister(), jitStackOffset32);
        } else if (optArg.isStackSlot()) {
            int optStackOffset32 = ((CiStackSlot) optArg).index() * OPT_SLOT_SIZE;
            adapt(asm, kind, optStackOffset32, jitStackOffset32, adapterFrameSize);
            if (kind.isObject()) {
                return optStackOffset32 / Word.size();
            }
        } else {
            throw FatalError.unexpected("Unadaptable parameter location type: " + optArg.getClass().getSimpleName());
        }
        return -1;
    }

    protected void stackCopy(AMD64Assembler asm, CiKind kind, int sourceStackOffset, int destStackOffset) {
        // First, load into a scratch register of appropriate size for the kind, then write to memory location
        if (kind.isDoubleWord() || kind.isWord() || kind.isObject()) {
            asm.movq(scratch, new CiAddress(CiKind.Word, rsp.asValue(), sourceStackOffset));
            asm.movq(new CiAddress(CiKind.Word, rsp.asValue(), destStackOffset), scratch);
        } else {
            asm.movzxd(scratch, new CiAddress(CiKind.Word, rsp.asValue(), sourceStackOffset));
            asm.movl(new CiAddress(CiKind.Word, rsp.asValue(), destStackOffset), scratch);
        }
    }

    protected abstract void adapt(AMD64Assembler asm, CiKind kind, int optStackOffset32, int jitStackOffset32, int adapterFrameSize);
    protected abstract void adapt(AMD64Assembler asm, CiKind kind, CiRegister reg, int offset32);

    public static final byte REXW = (byte) 0x48;
    public static final byte RET2 = (byte) 0xC2;
    public static final byte ENTER = (byte) 0xC8;
    public static final byte ADDQ_SUBQ_imm8 = (byte) 0x83;
    public static final byte ADDQ_SUBQ_imm32 = (byte) 0x81;
}
