/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.vm.compiler.c1x;

import static com.sun.max.platform.Platform.*;
import static com.sun.max.vm.MaxineVM.*;
import static com.sun.max.vm.compiler.deopt.Deoptimization.*;
import static com.sun.max.vm.compiler.target.Stub.Type.*;
import static com.sun.max.vm.stack.StackReferenceMapPreparer.*;

import java.util.*;

import com.sun.cri.ci.*;
import com.sun.cri.ci.CiCallingConvention.Type;
import com.sun.cri.ci.CiRegister.RegisterFlag;
import com.sun.cri.ci.CiTargetMethod.CodeAnnotation;
import com.sun.cri.ci.CiTargetMethod.ExceptionHandler;
import com.sun.cri.ci.CiTargetMethod.JumpTable;
import com.sun.cri.ci.CiTargetMethod.Mark;
import com.sun.cri.ci.CiTargetMethod.Site;
import com.sun.cri.ri.*;
import com.sun.max.annotate.*;
import com.sun.max.asm.*;
import com.sun.max.asm.InlineDataDescriptor.JumpTable32;
import com.sun.max.io.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.collect.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.compiler.deopt.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.compiler.target.amd64.*;
import com.sun.max.vm.object.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.stack.*;
import com.sun.max.vm.stack.StackFrameWalker.Cursor;
import com.sun.max.vm.thread.*;
import com.sun.max.vm.type.*;

/**
 * This class implements a {@link TargetMethod target method} for
 * the Maxine VM that represents a compiled method generated by C1X.
 */
public final class C1XTargetMethod extends TargetMethod implements Cloneable {

    /**
     * An array of pairs denoting the code positions protected by an exception handler.
     * A pair {@code {p,h}} at index {@code i} in this array specifies that code position
     * {@code h} is the handler for an exception of type {@code t} occurring at position
     * {@code p} where {@code t} is the element at index {@code i / 2} in {@link #exceptionClassActors}.
     */
    private int[] exceptionPositionsToCatchPositions;

    /**
     * @see #exceptionPositionsToCatchPositions
     */
    private ClassActor[] exceptionClassActors;

    /**
     * Debug info.
     */
    private DebugInfo debugInfo;

    private final CodeAnnotation[] annotations;

    @HOSTED_ONLY
    private CiTargetMethod bootstrappingCiTargetMethod;

    public C1XTargetMethod(ClassMethodActor classMethodActor, CiTargetMethod ciTargetMethod, boolean install) {
        super(classMethodActor, CallEntryPoint.OPTIMIZED_ENTRY_POINT);
        assert classMethodActor != null;
        List<CodeAnnotation> annotations = ciTargetMethod.annotations();
        this.annotations = annotations == null ? null : annotations.toArray(new CodeAnnotation[annotations.size()]);
        init(ciTargetMethod, install);
    }

    private void init(CiTargetMethod ciTargetMethod, boolean install) {

        if (isHosted()) {
            // Save the target method for later gathering of calls and duplication
            this.bootstrappingCiTargetMethod = ciTargetMethod;
        }

        for (Mark mark : ciTargetMethod.marks) {
            FatalError.unexpected("Unknown mark in code generated for " + this + ": " + mark);
        }

        if (classMethodActor != null) {
            int customStackAreaOffset = ciTargetMethod.customStackAreaOffset();
            if (customStackAreaOffset != DEOPT_RETURN_ADDRESS_OFFSET) {
                throw new InternalError("custom stack area offset should be " + DEOPT_RETURN_ADDRESS_OFFSET + ", not " + customStackAreaOffset);
            }
        }

        initCodeBuffer(ciTargetMethod, install);
        initFrameLayout(ciTargetMethod);
        CiDebugInfo[] debugInfos = initStopPositions(ciTargetMethod);
        initExceptionTable(ciTargetMethod);

        debugInfo = new DebugInfo(debugInfos, this);

        if (!isHosted()) {
            if (install) {
                linkDirectCalls();
            } else {
                // the displacement between a call site in the heap and a code cache location may not fit in the offset operand of a call
            }
        }
    }

    public static InlineDataDecoder inlineDataDecoder(CodeAnnotation[] annotations) {
        if (annotations == null) {
            return null;
        }
        ArrayList<InlineDataDescriptor> descriptors = new ArrayList<InlineDataDescriptor>();
        for (CodeAnnotation c : annotations) {
            if (c instanceof JumpTable) {
                JumpTable jt = (JumpTable) c;
                if (jt.entrySize == 4) {
                    descriptors.add(new JumpTable32(jt.position, jt.low, jt.high));
                }
            }
        }
        if (descriptors.isEmpty()) {
            return null;
        }
        return new InlineDataDecoder(descriptors);
    }

    @Override
    public InlineDataDecoder inlineDataDecoder() {
        return inlineDataDecoder(annotations);
    }

    /**
     * Gets the size (in bytes) of a bit map covering all the registers that may store references.
     * The bit position of a register in the bit map is the register's {@linkplain CiRegister#encoding encoding}.
     */
    @FOLD
    public static int regRefMapSize() {
        return ByteArrayBitMap.computeBitMapSize(target().arch.registerReferenceMapBitCount);
    }

    /**
     * @return the size of an activation frame for this target method in words.
     */
    private int frameWords() {
        return frameSize() / Word.size();
    }

    /**
     * @return the size (in bytes) of a reference map covering an activation frame for this target method.
     */
    public int frameRefMapSize() {
        return ByteArrayBitMap.computeBitMapSize(frameWords());
    }

    /**
     * @return the number of bytes in {@link #refMaps} corresponding to one stop position.
     */
    public int totalRefMapSize() {
        return regRefMapSize() + frameRefMapSize();
    }

    public DebugInfo debugInfo() {
        return debugInfo;
    }

    @Override
    protected ClassMethodActor toMethodActor(CiRuntimeCall rtCall) {
        return C1XRuntimeCalls.getClassMethodActor(rtCall);
    }

    private void initExceptionTable(CiTargetMethod ciTargetMethod) {
        if (ciTargetMethod.exceptionHandlers.size() > 0) {
            exceptionPositionsToCatchPositions = new int[ciTargetMethod.exceptionHandlers.size() * 2];
            exceptionClassActors = new ClassActor[ciTargetMethod.exceptionHandlers.size()];

            int z = 0;
            for (ExceptionHandler handler : ciTargetMethod.exceptionHandlers) {
                exceptionPositionsToCatchPositions[z * 2] = handler.pcOffset;
                exceptionPositionsToCatchPositions[z * 2 + 1] = handler.handlerPos;
                exceptionClassActors[z] = (handler.exceptionType == null) ? null : (ClassActor) handler.exceptionType;
                z++;
            }
        }
    }

    private void encodeSourcePos(int index,
                                 int[] sourceInfoData,
                                 CiCodePos curPos,
                                 IdentityHashMap<ClassMethodActor, Integer> inlinedMethodMap,
                                 IdentityHashMap<CiCodePos, Integer> codePosMap,
                                 int stopCount,
                                 List<ClassMethodActor> inlinedMethodList) {
        // encodes three integers into the sourceInfoData array:
        // the index into the sourceMethods array, the bytecode index, and the index of the caller method
        // (if this entry is an inlined method)
        int start = index * 3;

        if (curPos == null) {
            sourceInfoData[start] = -1;
            sourceInfoData[start + 1] = -1;
            sourceInfoData[start + 2] = -1;
            return;
        }

        ClassMethodActor cma = (ClassMethodActor) curPos.method;
        Integer methodIndex = inlinedMethodMap.get(cma);
        if (methodIndex == null) {
            methodIndex = inlinedMethodList.size();
            inlinedMethodMap.put(cma, methodIndex);
            inlinedMethodList.add(cma);
        }
        int bytecodeIndex = curPos.bci;
        int callerIndex;
        if (curPos.caller == null) {
            callerIndex = -1;
        } else {
            Integer sourceInfoIndex = codePosMap.get(curPos.caller);
            callerIndex = sourceInfoIndex < 0 ? (-sourceInfoIndex - 1) + stopCount : sourceInfoIndex;
        }
        sourceInfoData[start] = methodIndex;
        sourceInfoData[start + 1] = bytecodeIndex;
        sourceInfoData[start + 2] = callerIndex;
    }

    @Override
    public boolean isPatchableCallSite(Address callSite) {
        return AMD64TargetMethodUtil.isPatchableCallSite(callSite);
    }

    @Override
    public Address fixupCallSite(int callOffset, Address callEntryPoint) {
        return AMD64TargetMethodUtil.fixupCall32Site(this, callOffset, callEntryPoint);
    }

    @Override
    public Address patchCallSite(int callOffset, Address callEntryPoint) {
        return AMD64TargetMethodUtil.mtSafePatchCallDisplacement(this, codeStart().plus(callOffset), callEntryPoint.asAddress());
    }

    @Override
    public Address throwAddressToCatchAddress(boolean isTopFrame, Address throwAddress, Class<? extends Throwable> throwableClass) {
        final int exceptionPos = throwAddress.minus(codeStart).toInt();
        int count = getExceptionHandlerCount();
        for (int i = 0; i < count; i++) {
            int codePos = getExceptionPosAt(i);
            int catchPos = getCatchPosAt(i);
            ClassActor catchType = getCatchTypeAt(i);

            if (codePos == exceptionPos && checkType(throwableClass, catchType)) {
                return codeStart.plus(catchPos);
            }
        }
        return Address.zero();
    }

    private boolean checkType(Class<? extends Throwable> throwableClass, ClassActor catchType) {
        return catchType == null || catchType.isAssignableFrom(ClassActor.fromJava(throwableClass));
    }

    /**
     * Gets the exception code position of an entry in the exception handler table.
     *
     * @param i the index of the requested exception handler table entry
     * @return the exception code position of element {@code i} in the exception handler table
     */
    private int getExceptionPosAt(int i) {
        return exceptionPositionsToCatchPositions[i * 2];
    }

    /**
     * Gets the exception handler code position of an entry in the exception handler table.
     *
     * @param i the index of the requested exception handler table entry
     * @return the exception handler position of element {@code i} in the exception handler table
     */
    private int getCatchPosAt(int i) {
        return exceptionPositionsToCatchPositions[i * 2 + 1];
    }

    /**
     * Gets the exception type of an entry in the exception handler table.
     *
     * @param i the index of the requested exception handler table entry
     * @return the exception type of element {@code i} in the exception handler table
     */
    private ClassActor getCatchTypeAt(int i) {
        return exceptionClassActors[i];
    }

    /**
     * Gets the number of entries in the exception handler table.
     */
    private int getExceptionHandlerCount() {
        return exceptionClassActors == null ? 0 : exceptionClassActors.length;
    }

    @HOSTED_ONLY
    private void gatherInlinedMethods(Site site, Set<MethodActor> inlinedMethods) {
        CiDebugInfo debugInfo = site.debugInfo();
        if (debugInfo != null) {
            for (CiCodePos pos = debugInfo.codePos; pos != null; pos = pos.caller) {
                inlinedMethods.add((MethodActor) pos.method);
            }
        }
    }

    @Override
    @HOSTED_ONLY
    public void gatherCalls(Set<MethodActor> directCalls, Set<MethodActor> virtualCalls, Set<MethodActor> interfaceCalls, Set<MethodActor> inlinedMethods) {
        // first gather methods in the directCallees array
        if (directCallees != null) {
            for (Object o : directCallees) {
                if (o instanceof MethodActor) {
                    directCalls.add((MethodActor) o);
                }
            }
        }

        // iterate over direct calls
        for (CiTargetMethod.Call site : bootstrappingCiTargetMethod.directCalls) {
            if (site.runtimeCall != null) {
                directCalls.add(getClassMethodActor(site.runtimeCall, site.method));
            } else if (site.method != null) {
                MethodActor methodActor = (MethodActor) site.method;
                directCalls.add(methodActor);
            }
            gatherInlinedMethods(site, inlinedMethods);
        }

        // iterate over all the calls and append them to the appropriate lists
        for (CiTargetMethod.Call site : bootstrappingCiTargetMethod.indirectCalls) {
            if (site.method != null) {
                if (site.method.isResolved()) {
                    MethodActor methodActor = (MethodActor) site.method;
                    if (site.method.holder().isInterface()) {
                        interfaceCalls.add(methodActor);
                    } else {
                        virtualCalls.add(methodActor);
                    }
                }
            }
            gatherInlinedMethods(site, inlinedMethods);
        }
    }

    @HOSTED_ONLY
    private ClassMethodActor getClassMethodActor(CiRuntimeCall runtimeCall, RiMethod method) {
        if (method != null) {
            return (ClassMethodActor) method;
        }

        assert runtimeCall != null : "A call can either be a call to a method or a runtime call";
        return C1XRuntimeCalls.getClassMethodActor(runtimeCall);
    }

    @Override
    public void traceDebugInfo(IndentWriter writer) {
    }

    @Override
    public void traceExceptionHandlers(IndentWriter writer) {
        if (getExceptionHandlerCount() != 0) {
            writer.println("Exception handlers:");
            writer.indent();
            for (int i = 0; i < getExceptionHandlerCount(); i++) {
                ClassActor catchType = getCatchTypeAt(i);
                writer.println((catchType == null ? "<any>" : catchType) + " @ " + getExceptionPosAt(i) + " -> " + getCatchPosAt(i));
            }
            writer.outdent();
        }
    }

    /**
     * Prepares the reference map for this frame.
     *
     * @param current the current frame
     * @param callee the callee frame
     * @param preparer the reference map preparer
     */
    @Override
    public void prepareReferenceMap(Cursor current, Cursor callee, StackReferenceMapPreparer preparer) {
        CiCalleeSaveLayout csl = callee.csl();
        Pointer csa = callee.csa();
        TargetMethod calleeTM = callee.targetMethod();
        if (calleeTM != null) {
            Stub.Type st = calleeTM.stubType();
            if (st == InterfaceTrampoline || st == VirtualTrampoline || st == InterfaceTrampoline) {
                prepareTrampolineRefMap(current, callee, preparer);
            } else if (calleeTM.is(TrapStub) && Trap.Number.isStackOverflow(csa)) {
                // a method can never catch stack overflow for itself so there
                // is no need to scan the references in the trapped method
                return;
            }
        }

        int stopIndex = findClosestStopIndex(current.ip());
        if (stopIndex < 0) {
            // this is very bad.
            throw FatalError.unexpected("could not find stop index");
        }

        int frameRefMapSize = frameRefMapSize();
        if (!csa.isZero()) {
            // the callee contains register state from this frame;
            // use register reference maps in this method to fill in the map for the callee
            Pointer slotPointer = csa;
            int byteIndex = debugInfo.regRefMapStart(stopIndex);
            preparer.tracePrepareReferenceMap(this, stopIndex, slotPointer, "C1X registers frame");

            // Need to translate from register numbers (as stored in the reg ref maps) to frame slots.
            for (int i = 0; i < regRefMapSize(); i++) {
                int b = debugInfo.data[byteIndex] & 0xff;
                int reg = i * 8;
                while (b != 0) {
                    if ((b & 1) != 0) {
                        int offset = csl.offsetOf(reg);
                        if (traceStackRootScanning()) {
                            Log.print("    register: ");
                            Log.println(csl.registers[reg].name);
                        }
                        preparer.setReferenceMapBits(callee, slotPointer.plus(offset), 1, 1);
                    }
                    reg++;
                    b = b >>> 1;
                }
                byteIndex++;
            }
        }

        // prepare the map for this stack frame
        Pointer slotPointer = current.sp();
        preparer.tracePrepareReferenceMap(this, stopIndex, slotPointer, "C1X stack frame");
        int byteIndex = debugInfo.frameRefMapStart(stopIndex);
        for (int i = 0; i < frameRefMapSize; i++) {
            preparer.setReferenceMapBits(current, slotPointer, debugInfo.data[byteIndex] & 0xff, Bytes.WIDTH);
            slotPointer = slotPointer.plusWords(Bytes.WIDTH);
            byteIndex++;
        }
    }

    /**
     * Prepares the reference map for the frame of a call to a trampoline from an OPT compiled method.
     *
     * An opto-compiled caller may pass some arguments in registers.  The trampoline is polymorphic, i.e. it does not have any
     * helpful maps regarding the actual callee.  It does store all potential parameter registers on its stack, though,
     * and recovers them before returning.  We mark those that contain references.
     *
     * @param current
     * @param callee
     * @param preparer
     */
    public static void prepareTrampolineRefMap(Cursor current, Cursor callee, StackReferenceMapPreparer preparer) {
        RiRegisterConfig registerConfig = vm().registerConfigs.trampoline;
        TargetMethod trampoline = callee.targetMethod();
        ClassMethodActor calledMethod;
        TargetMethod targetMethod = current.targetMethod();

        CiCalleeSaveLayout csl = callee.csl();
        Pointer csa = callee.csa();
        FatalError.check(csl != null && !csa.isZero(), "trampoline must have callee save area");
        CiRegister[] regs = registerConfig.getCallingConventionRegisters(Type.JavaCall, RegisterFlag.CPU);

        // figure out what method the caller is trying to call
        if (trampoline.is(StaticTrampoline)) {
            int stopIndex = targetMethod.findClosestStopIndex(current.ip().minus(1));
            calledMethod = (ClassMethodActor) targetMethod.directCallees()[stopIndex];
        } else {
            // this is a virtual or interface call; figure out the receiver method based on the
            // virtual or interface index
            Object receiver = csa.plus(csl.offsetOf(regs[0])).getReference().toJava();
            ClassActor classActor = ObjectAccess.readClassActor(receiver);
            // The virtual dispatch trampoline stubs put the virtual dispatch index into the
            // scratch register and then saves it to the stack.
            int index = vm().stubs.readVirtualDispatchIndexFromTrampolineFrame(csa);
            if (trampoline.is(VirtualTrampoline)) {
                calledMethod = classActor.getVirtualMethodActorByVTableIndex(index);
            } else {
                assert trampoline.is(InterfaceTrampoline);
                calledMethod = classActor.getVirtualMethodActorByIIndex(index);
            }
        }

        int regIndex = 0;
        if (!calledMethod.isStatic()) {
            // set a bit for the receiver object
            int offset = csl.offsetOf(regs[regIndex++]);
            preparer.setReferenceMapBits(current, csa.plus(offset), 1, 1);
        }

        SignatureDescriptor sig = calledMethod.descriptor();
        for (int i = 0; i < sig.numberOfParameters() && regIndex < regs.length; ++i) {
            TypeDescriptor arg = sig.parameterDescriptorAt(i);
            CiRegister reg = regs[regIndex];
            Kind kind = arg.toKind();
            if (kind.isReference) {
                // set a bit for this parameter
                int offset = csl.offsetOf(reg);
                preparer.setReferenceMapBits(current, csa.plus(offset), 1, 1);
            }
            if (kind != Kind.FLOAT && kind != Kind.DOUBLE) {
                // Only iterating over the integral arg registers
                regIndex++;
            }
        }
    }

    /**
     * Attempt to catch an exception that has been thrown with this method on the call stack.
     * @param current the current stack frame
     * @param callee the callee stack frame
     * @param throwable the exception being thrown
     */
    @Override
    public void catchException(Cursor current, Cursor callee, Throwable throwable) {
        Pointer ip = current.ip();
        Pointer sp = current.sp();
        Pointer fp = current.fp();
        Address catchAddress = throwAddressToCatchAddress(current.isTopFrame(), ip, throwable.getClass());
        if (!catchAddress.isZero()) {
            if (StackFrameWalker.TraceStackWalk) {
                Log.print("StackFrameWalk: Handler position for exception at position ");
                Log.print(ip.minus(codeStart()).toInt());
                Log.print(" is ");
                Log.println(catchAddress.minus(codeStart()).toInt());
            }

            if (invalidated() != null) {
                // Instead of unwinding to the invalidated method, execution is redirected to the void deopt stub.
                // And the original return address (i.e. current.ip()) is saved in the DEOPT_RETURN_ADDRESS_OFFSET
                // slot instead of the handler address. This is required so that the debug info associated with
                // the call site is used during deopt. This debug info matches the state on entry to the handler
                // except that the stack is empty (the exception object is explicitly retrieved and pushed by
                // the handler in the deoptimized code).
                current.sp().writeWord(DEOPT_RETURN_ADDRESS_OFFSET, ip);
                Stub stub = vm().stubs.deoptStub(CiKind.Void);
                Pointer deoptStub = stub.codeStart().asPointer();
                if (Deoptimization.TraceDeopt) {
                    Log.println("DEOPT: changed exception handler address " + catchAddress.to0xHexString() + " in " + this + " to redirect to deopt stub " +
                                    deoptStub.to0xHexString() + " [sp=" + sp.to0xHexString() + ", fp=" + fp.to0xHexString() + "]");
                }
                catchAddress = deoptStub;
            }

            TargetMethod calleeMethod = callee.targetMethod();
            // Reset the stack walker
            current.stackFrameWalker().reset();

            // Store the exception for the handler
            VmThread.current().storeExceptionForHandler(throwable, this, posFor(catchAddress));

            if (calleeMethod != null && calleeMethod.registerRestoreEpilogueOffset() != -1) {
                unwindToCalleeEpilogue(catchAddress, sp, calleeMethod);
            } else {
                Stubs.unwind(catchAddress, sp, fp);
            }
            ProgramError.unexpected("Should not reach here, unwind must jump to the exception handler!");
        }
    }

    @NEVER_INLINE
    public static void unwindToCalleeEpilogue(Address catchAddress, Pointer stackPointer, TargetMethod lastJavaCallee) {
        // Overwrite return address of callee with catch address
        final Pointer returnAddressPointer = stackPointer.minus(Word.size());
        returnAddressPointer.setWord(catchAddress);

        Address epilogueAddress = lastJavaCallee.codeStart().plus(lastJavaCallee.registerRestoreEpilogueOffset());

        final Pointer calleeStackPointer = stackPointer.minus(Word.size()).minus(lastJavaCallee.frameSize());
        Stubs.unwind(epilogueAddress, calleeStackPointer, Pointer.zero());
    }

    /**
     * Accept a visitor for this frame.
     * @param current the current stack frame
     * @param visitor the visitor
     * @return {@code true} if the stack walker should continue walking, {@code false} if the visitor is finished visiting
     */
    @Override
    public boolean acceptStackFrameVisitor(Cursor current, StackFrameVisitor visitor) {
        if (platform().isa == ISA.AMD64) {
            return AMD64TargetMethodUtil.acceptStackFrameVisitor(current, visitor);
        }
        throw FatalError.unimplemented();
    }

    /**
     * Advances the cursor to the caller's frame.
     * @param current the current frame
     */
    @Override
    public void advance(Cursor current) {
        if (platform().isa == ISA.AMD64) {
            CiCalleeSaveLayout csl = calleeSaveLayout();
            Pointer csa = Pointer.zero();
            if (csl != null) {
                // See com.sun.c1x.lir.FrameMap
                csa = current.sp().plus(frameSize() - csl.size);
            }
            AMD64TargetMethodUtil.advance(current, csl, csa);
        } else {
            throw FatalError.unimplemented();
        }
    }

    @Override
    public Pointer returnAddressPointer(Cursor frame) {
        if (platform().isa == ISA.AMD64) {
            return AMD64TargetMethodUtil.returnAddressPointer(frame);
        } else {
            throw FatalError.unimplemented();
        }
    }

    @Override
    public int forEachCodePos(CodePosClosure cpc, Pointer ip, boolean ipIsReturnAddress) {
        if (ipIsReturnAddress && platform().isa.offsetToReturnPC == 0) {
            ip = ip.minus(1);
        }

        int index = findClosestStopIndex(ip);
        if (index < 0) {
            return 0;
        }

        return debugInfo.forEachCodePos(cpc, index);
    }

    @Override
    public CiDebugInfo debugInfoAt(int stopIndex, FrameAccess fa) {
        return debugInfo.infoAt(stopIndex, fa);
    }
}
