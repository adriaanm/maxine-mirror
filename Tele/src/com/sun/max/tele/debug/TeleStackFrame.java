/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.tele.debug;

import static com.sun.max.platform.Platform.*;
import static com.sun.max.vm.compiler.target.TargetMethod.Flavor.*;

import java.util.*;

import com.sun.max.tele.*;
import com.sun.max.tele.debug.TeleStackFrameWalker.ErrorStackFrame;
import com.sun.max.tele.memory.*;
import com.sun.max.tele.method.*;
import com.sun.max.tele.util.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.stack.*;

/**
 * Hierarchy of classes that act as wrappers for VM {@linkplain StackFrame stack frames}, with additional
 * contextual information added for the benefits of clients.  The hierarchy also includes the two subclasses
 * {@link ErrorFrame} and {@link TruncatedFrame}, which are <em>synthetic</em>: they
 * correspond to no VM frame types, but are rather used to as markers by the stack walker for errors
 * and compression of long stacks respectively.
 *
 * @author Michael Van De Vanter
 */
public abstract class TeleStackFrame<StackFrame_Type extends StackFrame> extends AbstractTeleVMHolder implements MaxStackFrame {

    private static final int TRACE_LEVEL = 2;

    /**
     * Description of the memory region occupied by a {@linkplain MaxStackFrame stack frame} in the VM.
     * <br>
     * The parent of this region is the {@linkplain MaxStack stack} in which it is contained.
     * <br>
     * This region has no children (although it could if we chose to decompose it into slots).
     */
    private static final class StackFrameMemoryRegion extends TeleFixedMemoryRegion implements MaxEntityMemoryRegion<MaxStackFrame> {

        private static final List<MaxEntityMemoryRegion< ? extends MaxEntity>> EMPTY = Collections.emptyList();
        private final TeleStackFrame teleStackFrame;

        private StackFrameMemoryRegion(TeleVM teleVM, TeleStackFrame teleStackFrame, String regionName, Address start, long nBytes) {
            super(teleVM, regionName, start, nBytes);
            this.teleStackFrame = teleStackFrame;
        }

        public MaxEntityMemoryRegion< ? extends MaxEntity> parent() {
            return teleStackFrame.stack().memoryRegion();
        }

        public List<MaxEntityMemoryRegion< ? extends MaxEntity>> children() {
            return EMPTY;
        }

        public MaxStackFrame owner() {
            return teleStackFrame;
        }

        public boolean isBootRegion() {
            return false;
        }
    }

    /**
     * Factory method for wrapping VM (and synthetic) stack frames with additional information, in a type
     * hierarchy that partially mirrors the types of frames.
     *
     * @param teleVM the VM
     * @param teleStack the stack containing the frame
     * @param position the position in the stack of the frame; position 0 is the top
     * @param stackFrame the frame to be wrapped
     * @return a newly created instance of {@link TeleStackFrame}
     */
    public static TeleStackFrame createFrame(TeleVM teleVM, TeleStack teleStack, int position, StackFrame stackFrame) {
        if (stackFrame instanceof CompiledStackFrame) {
            final CompiledStackFrame compiledStackFrame = (CompiledStackFrame) stackFrame;
            return new CompiledFrame(teleVM, teleStack, position, compiledStackFrame);
        }
        if (stackFrame instanceof NativeStackFrame) {
            final NativeStackFrame nativeStackFrame = (NativeStackFrame) stackFrame;
            return new NativeFrame(teleVM, teleStack, position, nativeStackFrame);
        }
        if (stackFrame instanceof ErrorStackFrame) {
            final ErrorStackFrame errorStackFrame = (ErrorStackFrame) stackFrame;
            return new ErrorFrame(teleVM, teleStack, position, errorStackFrame);
        }
        TeleError.unexpected("Unknown stack frame kind");
        return null;
    }

    protected final StackFrame_Type stackFrame;
    private final TeleStack teleStack;
    private final int position;
    private final CodeLocation codeLocation;
    private final TeleCompiledCode teleCompiledCode;

    protected TeleStackFrame(TeleVM teleVM, TeleStack teleStack, int position, StackFrame_Type stackFrame) {
        super(teleVM);
        this.stackFrame = stackFrame;
        this.teleStack = teleStack;
        this.position = position;
        this.teleCompiledCode = teleVM.codeCache().findCompiledCode(stackFrame.ip);

        CodeLocation location = null;
        Pointer instructionPointer = stackFrame.ip;
        if (!instructionPointer.isZero()) {
            final StackFrame callee = stackFrame.calleeFrame();
            if (callee == null) {
                // Top frame, not a call return so no adjustment.
                location = teleVM.codeManager().createMachineCodeLocation(instructionPointer, "top stack frame IP");
            } else {
                // A call frame; record the return location, the next to be executed upon return.
                // Add a platform-specific offset from the stored code address to the actual call return site.
                final TargetMethod calleeTargetMethod = callee.targetMethod();
                if (calleeTargetMethod != null) {
                    if (calleeTargetMethod.is(TrapStub)) {
                        // Special case, where the IP caused a trap; no adjustment.
                        location = teleVM.codeManager().createMachineCodeLocation(instructionPointer, "stack frame return");
                    }
                }
                if (location == null) {
                    // An ordinary call; apply a platform-specific adjustment to get the real return address.
                    final int offsetToReturnPC = platform().isa.offsetToReturnPC;
                    location = teleVM.codeManager().createMachineCodeLocation(instructionPointer.plus(offsetToReturnPC), "stack frame return");
                }
            }
        }
        this.codeLocation = location;
    }

    public final TeleStack stack() {
        return teleStack;
    }

    public final int position() {
        return position;
    }

    public final boolean isTop() {
        return position == 0;
    }

    public Pointer ip() {
        return stackFrame.ip;
    }

    public Pointer sp() {
        return stackFrame.sp;
    }

    public Pointer fp() {
        return stackFrame.fp;
    }

    public final MaxCodeLocation codeLocation() {
        return codeLocation;
    }

    public final TeleCompiledCode compiledCode() {
        return teleCompiledCode;
    }

    public boolean isSameFrame(MaxStackFrame maxStackFrame) {
        if (maxStackFrame instanceof TeleStackFrame) {
            // By default, delegate definition of "same" to the wrapped frames.
            final TeleStackFrame otherStackFrame = (TeleStackFrame) maxStackFrame;
            return this.stackFrame.isSameFrame(otherStackFrame.stackFrame);
        }
        return false;
    }

    @Override
    public String toString() {
        return Integer.toString(position) + ":  " + entityName();
    }

    static final class CompiledFrame extends TeleStackFrame<CompiledStackFrame> implements MaxStackFrame.Compiled {

        private final StackFrameMemoryRegion stackFrameMemoryRegion;
        private final String entityDescription;

        protected CompiledFrame(TeleVM teleVM, TeleStack teleStack, int position, CompiledStackFrame compiledStackFrame) {
            super(teleVM, teleStack, position, compiledStackFrame);
            final String description = teleStack.thread().entityName() + " frame(" + position() + ")";
            this.stackFrameMemoryRegion = new StackFrameMemoryRegion(teleVM, this, description, stackFrame.slotBase(), layout().frameSize());
            this.entityDescription = "Stack frame " + position() + " in the " + vm().entityName() + " for " + teleStack.thread().entityName();
        }

        public String entityName() {
            return "<" + stackFrame.getClass().getSimpleName() + "> " + stackFrame.toString();
        }

        public String entityDescription() {
            return entityDescription;
        }

        public MaxEntityMemoryRegion<MaxStackFrame> memoryRegion() {
            return stackFrameMemoryRegion;
        }

        public boolean contains(Address address) {
            return stackFrameMemoryRegion.contains(address);
        }

        public CompiledStackFrameLayout layout() {
            return stackFrame.layout;
        }

        public Pointer slotBase() {
            return stackFrame.slotBase();
        }

        public int biasedFPOffset(int offset) {
            return stackFrame.biasedFPOffset(Offset.fromInt(offset)).toInt();
        }

        public StackBias bias() {
            return stackFrame.bias();
        }

        public String sourceVariableName(int slot) {
            final TeleCompiledCode compiledCode = compiledCode();
            return compiledCode == null ? null : compiledCode.sourceVariableName(this, slot);
        }
    }

    static final class NativeFrame extends TeleStackFrame<NativeStackFrame> implements MaxStackFrame.Native {

        private NativeFrame(TeleVM teleVM, TeleStack teleStack, int position, NativeStackFrame nativeStackFrame) {
            super(teleVM, teleStack, position, nativeStackFrame);
        }

        public String entityName() {
            return "<Native stack frame>  " + stackFrame.toString();
        }

        public String entityDescription() {
            return "A stack frame discovered running unknown native code together with the " + vm().entityName();
        }

        public MaxEntityMemoryRegion<MaxStackFrame> memoryRegion() {
            // Don't know enough
            return null;
        }

        public boolean contains(Address address) {
            return false;
        }
    }

    static final class ErrorFrame extends TeleStackFrame<ErrorStackFrame> implements MaxStackFrame.Error {

        private ErrorFrame(TeleVM teleVM, TeleStack teleStack, int position, ErrorStackFrame errorStackFrame) {
            super(teleVM, teleStack, position, errorStackFrame);
        }

        public String entityName() {
            return "<error: " + errorMessage() + ">" + stackFrame.toString();
        }

        public String entityDescription() {
            return "A error frame created as part of a stack walking failure for the " + vm().entityName();
        }

        public MaxEntityMemoryRegion<MaxStackFrame> memoryRegion() {
            return null;
        }

        public boolean contains(Address address) {
            return false;
        }

        @Override
        public Pointer ip() {
            return Pointer.zero();
        }

        @Override
        public Pointer sp() {
            return Pointer.zero();
        }

        @Override
        public Pointer fp() {
            return Pointer.zero();
        }

        public String errorMessage() {
            return stackFrame.errorMessage();
        }
    }

}
