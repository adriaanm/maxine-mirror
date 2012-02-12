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
package com.oracle.max.vm.ext.t1x;

import static com.oracle.max.vm.ext.t1x.T1XTargetMethod.*;
import static com.sun.max.vm.MaxineVM.*;
import static com.sun.max.vm.stack.StackReferenceMapPreparer.*;
import static com.sun.max.vm.stack.VMFrameLayout.*;

import java.util.*;

import com.oracle.max.cri.intrinsics.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;
import com.sun.max.lang.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.bytecode.*;
import com.sun.max.vm.bytecode.refmaps.*;
import com.sun.max.vm.classfile.*;
import com.sun.max.vm.collect.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.log.VMLogger.Interval;
import com.sun.max.vm.stack.*;

public class T1XReferenceMapEditor implements ReferenceMapInterpreterContext, ReferenceSlotVisitor, ReferenceMapEditorLogHelper {
    private final T1XTargetMethod t1xMethod;
    private final JVMSFrameLayout frame;
    private final Object blockFrames;
    private final ExceptionHandler[] exceptionHandlerMap;
    private final BytecodeSafepointsIterator bytecodeSafepointsIterator;

    /**
     * The sorted list of basic block starting BCIs.
     * <p>
     * Using a char array (as opposed to a short array) means that {@linkplain Arrays#binarySearch(char[], char) binary search} can be used to
     * find the basic block enclosing a given BCI.
     */
    private final char[] blockBCIs;

    private T1XReferenceMapEditor() {
        t1xMethod = null;
        frame = null;
        blockFrames = null;
        exceptionHandlerMap = null;
        bytecodeSafepointsIterator = null;
        blockBCIs = null;
    }

    public T1XReferenceMapEditor(T1XTargetMethod t1xMethod, int numberOfBlocks, boolean[] blockBCIs, BytecodeSafepointsIterator bytecodeSafepointIterator, JVMSFrameLayout frame) {
        assert t1xMethod.safepoints().size() != 0;
        this.t1xMethod = t1xMethod;
        this.exceptionHandlerMap = ExceptionHandler.createHandlerMap(t1xMethod.codeAttribute);
        this.frame = frame;
        this.blockBCIs = new char[numberOfBlocks];
        int blockIndex = 0;
        for (int i = 0; i != blockBCIs.length; ++i) {
            if (blockBCIs[i]) {
                this.blockBCIs[blockIndex++] = (char) i;
            }
        }
        assert blockIndex == numberOfBlocks;
        this.blockFrames = ReferenceMapInterpreter.createFrames(this);
        this.bytecodeSafepointsIterator = bytecodeSafepointIterator;
    }

    public int blockIndexFor(int bci) {
        final int blockIndex = Arrays.binarySearch(blockBCIs, (char) bci);
        if (blockIndex >= 0) {
            return blockIndex;
        }
        return (-blockIndex) - 2;
    }

    public Object blockFrames() {
        return blockFrames;
    }

    public void visitReferenceInLocalVariable(int localVariableIndex) {
        for (int safepointIndex = bytecodeSafepointsIterator.nextSafepointIndex(true); safepointIndex != -1; safepointIndex = bytecodeSafepointsIterator.nextSafepointIndex(false)) {
            final int offset = safepointIndex * t1xMethod.refMapSize();
            final int fpRelativeIndex = frame.localVariableReferenceMapIndex(localVariableIndex);
            ByteArrayBitMap.set(t1xMethod.referenceMaps(), offset, t1xMethod.frameRefMapSize, fpRelativeIndex);
        }
    }

    public void visitReferenceOnOperandStack(int operandStackIndex, boolean parametersPopped) {
        for (int safepointIndex = bytecodeSafepointsIterator.nextSafepointIndex(true); safepointIndex != -1; safepointIndex = bytecodeSafepointsIterator.nextSafepointIndex(false)) {
            boolean templateCall = t1xMethod.safepoints().isSetAt(Safepoints.TEMPLATE_CALL, safepointIndex);
            if (parametersPopped == templateCall) {
                final int offset = safepointIndex * t1xMethod.refMapSize();
                final int fpRelativeIndex = frame.operandStackReferenceMapIndex(operandStackIndex);
                ByteArrayBitMap.set(t1xMethod.referenceMaps(), offset, t1xMethod.frameRefMapSize, fpRelativeIndex);
            }
        }
    }

    public int blockStartBCI(int blockIndex) {
        if (blockIndex == blockBCIs.length) {
            return codeAttribute().code().length;
        }
        return blockBCIs[blockIndex];
    }

    public CodeAttribute codeAttribute() {
        return t1xMethod.codeAttribute;
    }

    public ClassMethodActor classMethodActor() {
        return t1xMethod.classMethodActor();
    }

    public ExceptionHandler exceptionHandlersActiveAt(int bci) {
        if (exceptionHandlerMap == null) {
            return null;
        }
        return exceptionHandlerMap[bci];
    }

    public int numberOfBlocks() {
        return blockBCIs.length;
    }

    public void fillInMaps() {
        if (logStackRootScanning()) {
            stackRootScanLogger.logFinalizeMaps(Interval.BEGIN, this);
        }

        final ReferenceMapInterpreter interpreter = ReferenceMapInterpreter.from(blockFrames);
        interpreter.finalizeFrames(this);
        interpreter.interpretReferenceSlots(this, this, bytecodeSafepointsIterator);

        if (logStackRootScanning()) {
            final boolean lockDisabledSafepoints = stackRootScanLogger.lock();
            bytecodeSafepointsIterator.reset();
            for (int bci = bytecodeSafepointsIterator.bci(); bci != -1; bci = bytecodeSafepointsIterator.next()) {
                for (int safepointIndex = bytecodeSafepointsIterator.nextSafepointIndex(true); safepointIndex != -1; safepointIndex = bytecodeSafepointsIterator.nextSafepointIndex(false)) {
                    stackRootScanLogger.logSafepoint(this, interpreter, bci, safepointIndex);
                }
            }

            stackRootScanLogger.logFinalizeMaps(Interval.END, this);
            stackRootScanLogger.unlock(lockDisabledSafepoints);
        }

    }

    public TargetMethod targetMethod() {
        return t1xMethod;
    }

    public String compilerName() {
        return "T1X";
    }

    public void traceSafepoint(ReferenceMapInterpreter interpreter, int bci, int safepointIndex) {
        final int offset = safepointIndex * t1xMethod.refMapSize();
        final CodeAttribute codeAttribute = codeAttribute();
        Log.print(bci);
        Log.print(":");
        int opc = codeAttribute.code()[bci] & 0xff;
        final String opcode = Bytecodes.baseNameOf(opc);
        Log.print(opcode);
        int chars = Ints.sizeOfBase10String(bci) + 1 + opcode.length();
        while (chars++ < 20) {
            Log.print(' ');
        }
        Log.print(" safepoint[");
        Log.print(safepointIndex);
        Log.print("]@");
        Log.print(t1xMethod.safepoints().posAt(safepointIndex));

        boolean templateCall = t1xMethod.safepoints().isSetAt(Safepoints.TEMPLATE_CALL, safepointIndex);
        if (templateCall) {
            Log.print('*');
        }
        if (interpreter.isFrameInitialized(blockIndexFor(bci))) {
            Log.print(", locals={");
            byte[] refMaps = t1xMethod.referenceMaps();
            for (int localVariableIndex = 0; localVariableIndex < codeAttribute.maxLocals; ++localVariableIndex) {
                final int refMapIndex = frame.localVariableReferenceMapIndex(localVariableIndex);
                CiRegister fp = frame.framePointerReg();
                if (ByteArrayBitMap.isSet(refMaps, offset, t1xMethod.frameRefMapSize, refMapIndex)) {
                    int fpOffset = frame.localVariableOffset(localVariableIndex);
                    Log.print(' ');
                    Log.print(localVariableIndex);
                    Log.print('[');
                    Log.print(fp.name);
                    if (fpOffset >= 0) {
                        Log.print('+');
                    }
                    Log.print(fpOffset);
                    Log.print("]");
                }
            }
            Log.print(" }");
            Log.print(", stack={");
            for (int operandStackIndex = 0; operandStackIndex < codeAttribute.maxStack; ++operandStackIndex) {
                final int refMapIndex = frame.operandStackReferenceMapIndex(operandStackIndex);
                CiRegister fp = frame.framePointerReg();
                if (ByteArrayBitMap.isSet(refMaps, offset, t1xMethod.frameRefMapSize, refMapIndex)) {
                    int fpOffset = frame.operandStackOffset(operandStackIndex);
                    Log.print(' ');
                    Log.print(operandStackIndex);
                    Log.print('[');
                    Log.print(fp.name);
                    if (fpOffset >= 0) {
                        Log.print('+');
                    }
                    Log.print(fpOffset);
                    Log.print("]");
                }
            }
            Log.print(" }");
            Log.print(", template={");
            for (int i = 0; i < frame.numberOfTemplateSlots(); i++) {
                int refMapIndex = UnsignedMath.divide(-t1xMethod.frameRefMapOffset, STACK_SLOT_SIZE) + i;
                CiRegister fp = frame.framePointerReg();
                if (ByteArrayBitMap.isSet(refMaps, offset, t1xMethod.frameRefMapSize, refMapIndex)) {
                    Log.print(' ');
                    Log.print(i);
                    Log.print('[');
                    Log.print(fp.name);
                    if (i >= 0) {
                        Log.print('+');
                    }
                    Log.print(i * STACK_SLOT_SIZE);
                    Log.print("]");
                }
            }
            Log.print(" }");
            Log.print(", registers={");
            CiCalleeSaveLayout csl = vm().registerConfigs.trapStub.getCalleeSaveLayout();
            for (int i = 0; i < regRefMapSize(); i++) {
                int b = refMaps[offset + t1xMethod.frameRefMapSize + i] & 0xff;
                int bit = i * 8;
                while (b != 0) {
                    if ((b & 1) != 0) {
                        CiRegister reg = csl.registerAt(bit);
                        Log.print(' ');
                        Log.print(reg.name);
                    }
                    bit++;
                    b = b >>> 1;
                }
            }
            Log.print(" }");
        } else {
            Log.print(", *unreachable*");
        }

    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + classMethodActor() + "]";
    }
}
