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
package com.sun.max.tele.object;

import static com.sun.max.platform.Platform.*;

import java.util.*;

import com.sun.max.io.*;
import com.sun.max.jdwp.vm.data.*;
import com.sun.max.jdwp.vm.proxy.*;
import com.sun.max.tele.*;
import com.sun.max.tele.field.*;
import com.sun.max.tele.method.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.bytecode.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.cps.target.*;
import com.sun.max.vm.reference.*;

/**
 * Canonical surrogate for several possible kinds of compilation of a Java {@link ClassMethod} in the VM.
 *
 * @author Michael Van De Vanter
 */
public class TeleTargetMethod extends TeleRuntimeMemoryRegion implements TargetMethodAccess {

    /**
     * Reason that a particular instruction is identified as a "Stop".
     *
     * @see TargetMethod
     * @see StopPositions
     */
    public enum CodeStopKind {
        DIRECT_CALL,   // Non-native direct call
        NATIVE_CALL,   // Native direct call
        INDIRECT_CALL, // Indirect call
        SAFE;          // Safepoint
    }

    /**
     * A copier to be used for implementing {@link TeleTargetMethod#targetMethod()}.
     */
    class ReducedDeepCopier extends DeepCopier {
        public ReducedDeepCopier() {
            TeleFields teleFields = vm().teleFields();
            omit(teleFields.TargetMethod_scalarLiterals.fieldActor());
            omit(teleFields.TargetMethod_referenceLiterals.fieldActor());
            generator = teleFields.Adapter_generator.fieldActor();
        }
        private final FieldActor generator;

        @Override
        protected Object makeDeepCopy(FieldActor fieldActor, TeleObject teleObject) {
            if (fieldActor.equals(generator)) {
                return null;
            } else {
                return super.makeDeepCopy(fieldActor, teleObject);
            }
        }
    }

    /**
     * Gets all target methods that encapsulate code compiled for a given method, either as a top level compilation or
     * as a result of inlining.
     *
     * TODO: Once inlining dependencies are tracked, this method needs to use them.
     *
     * @param vm the VM to search
     * @param methodKey the key denoting a method for which the target methods are being requested
     * @return local surrogates for all {@link TargetMethod}s in the VM that include code compiled for the
     *         method matching {@code methodKey}
     */
    public static List<TeleTargetMethod> get(TeleVM vm, MethodKey methodKey) {
        TeleClassActor teleClassActor = vm.findTeleClassActor(methodKey.holder());
        if (teleClassActor != null) {
            final List<TeleTargetMethod> result = new LinkedList<TeleTargetMethod>();
            for (TeleClassMethodActor teleClassMethodActor : teleClassActor.getTeleClassMethodActors()) {
                if (teleClassMethodActor.hasTargetMethod()) {
                    ClassMethodActor classMethodActor = teleClassMethodActor.classMethodActor();
                    if (classMethodActor.name.equals(methodKey.name()) && classMethodActor.descriptor.equals(methodKey.signature())) {
                        for (int i = 0; i < teleClassMethodActor.numberOfCompilations(); ++i) {
                            TeleTargetMethod teleTargetMethod = teleClassMethodActor.getCompilation(i);
                            if (teleTargetMethod != null) {
                                result.add(teleTargetMethod);
                            }
                        }
                    }
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    private static final Map<TeleObject, TargetABI> abiCache = new HashMap<TeleObject, TargetABI>();

    private List<TargetCodeInstruction> instructions;

    /**
     * @see  StopPositions
     * @see  TargetMethod#stopPositions()
     */
    protected StopPositions stopPositions;

    private TeleClassMethodActor teleClassMethodActor;

    /**
     * Cached copy of the {@link TargetMethod} from the VM.
     */
    private TargetMethod targetMethod;

    protected TeleTargetMethod(TeleVM vm, Reference targetMethodReference) {
        super(vm, targetMethodReference);
        // Exception to the general policy of not performing VM i/o during object
        // construction.  This is needed for the code registry.
        // A consequence is synchronized call to the registry from within a synchronized call to {@link TeleObject} construction.
        updateCache();
        // Register every method compilation, so that they can be located by code address.
        vm.codeCache().register(this);
    }

    public final TargetMethod targetMethod() {
        if (targetMethod == null) {
            targetMethod = (TargetMethod) deepCopy();
        }
        return targetMethod;
    }

    /**
     * @return disassembled target code instructions
     */
    public final List<TargetCodeInstruction> getInstructions() {
        if (instructions == null) {
            final byte[] code = getCode();
            if (code != null) {
                instructions = TeleDisassembler.decode(platform(), getCodeStart(), code, targetMethod().encodedInlineDataDescriptors());
            }
        }
        return instructions;
    }

    /**
     * Gets VM memory location of the first instruction in the method.
     *
     * @see TargetMethod#codeStart()
     */
    public final Pointer getCodeStart() {
        return targetMethod().codeStart();
    }

    /**
     * Gets the call entry memory location for this method.
     *
     * @return {@link Address#zero()} if this target method has not yet been compiled
     */
    public final Address callEntryPoint() {
        Address callEntryAddress = Address.zero();
        final Pointer codeStart = getCodeStart();
        if (!codeStart.isZero()) {
            callEntryAddress = codeStart;
            TeleObject teleCallEntryPoint = null;
            if (vm().tryLock()) {
                try {
                    final Reference callEntryPointReference = vm().teleFields().TargetMethod_callEntryPoint.readReference(reference());
                    teleCallEntryPoint = heap().makeTeleObject(callEntryPointReference);
                } finally {
                    vm().unlock();
                }
            }
            if (teleCallEntryPoint != null) {
                final CallEntryPoint callEntryPoint = (CallEntryPoint) teleCallEntryPoint.deepCopy();
                if (callEntryPoint != null) {
                    callEntryAddress = codeStart.plus(callEntryPoint.offset());
                }
            }
        }
        return callEntryAddress;
    }

    /**
     * Gets the method actor in the VM for this target method.
     *
     * @return {@code null} if the class method actor is null the target method
     */
    public final TeleClassMethodActor getTeleClassMethodActor() {
        if (teleClassMethodActor == null && vm().tryLock()) {
            try {
                final Reference classMethodActorReference = vm().teleFields().TargetMethod_classMethodActor.readReference(reference());
                teleClassMethodActor = (TeleClassMethodActor) heap().makeTeleObject(classMethodActorReference);
            } finally {
                vm().unlock();
            }
        }
        return teleClassMethodActor;
    }

    /**
     * Gets the locations in compiled code (by byte offset from the start)
     * of stop positions:  calls and safepoints.
     *
     * @return the stop positions for the compiled code.
     * @see TargetMethod#stopPositions()
     */
    public final StopPositions getStopPositions() {
        if (stopPositions == null) {
            if (targetMethod().stopPositions() != null) {
                stopPositions = new StopPositions(targetMethod().stopPositions());
            }
        }
        return stopPositions;
    }

    /**
     * Gets the local mirror of the class method actor associated with this target method. This may be null.
     */
    public final ClassMethodActor classMethodActor() {
        final TeleClassMethodActor teleClassMethodActor = getTeleClassMethodActor();
        return teleClassMethodActor == null ? null : teleClassMethodActor.classMethodActor();
    }

    /**
     * Gets the byte array containing the target-specific machine code of this target method in the VM.
     * @see TargetMethod#code()
     */
    public final byte[] getCode() {
        return targetMethod().code();
    }

    /**
     * Gets the length of the byte array containing the target-specific machine code of this target method in the VM.
     * @see TargetMethod#codeLength()
     */
    public final int getCodeLength() {
        return targetMethod().codeLength();
    }

    /**
     * Creates a map:  instruction position (bytes offset from start) -> the kind of stop, null if not a stop.
     */
    public final CodeStopKind[] getPositionToStopKindMap() {
        final StopPositions stopPositions = getStopPositions();
        if (stopPositions == null) {
            return null;
        }
        final CodeStopKind[] stopKinds = new CodeStopKind[getCodeLength()];

        final int directCallCount = targetMethod().numberOfDirectCalls();
        final int indirectCallCount = targetMethod().numberOfIndirectCalls();
        final int safepointCount = targetMethod().numberOfSafepoints();
        assert directCallCount + indirectCallCount + safepointCount == stopPositions.length();

        for (int stopIndex = 0; stopIndex < directCallCount; stopIndex++) {
            if (stopPositions.isNativeFunctionCall(stopIndex)) {
                stopKinds[stopPositions.get(stopIndex)] = CodeStopKind.NATIVE_CALL;
            } else {
                stopKinds[stopPositions.get(stopIndex)] = CodeStopKind.DIRECT_CALL;
            }
        }
        for (int stopIndex = directCallCount; stopIndex < directCallCount + indirectCallCount; stopIndex++) {
            stopKinds[stopPositions.get(stopIndex)] = CodeStopKind.INDIRECT_CALL;
        }
        for (int stopIndex = directCallCount + indirectCallCount; stopIndex < stopPositions.length(); stopIndex++) {
            stopKinds[stopPositions.get(stopIndex)] = CodeStopKind.SAFE;
        }
        return stopKinds;
    }

    /**
     * Creates a map:  instruction position (bytes offset from start) -> bytecode location  (as much as can be determined).
     */
    public BytecodeLocation[] getPositionToBytecodeLocationMap() {
        final StopPositions stopPositions = getStopPositions();
        if (stopPositions == null) {
            return null;
        }
        BytecodeLocation[] bytecodeLocations = new BytecodeLocation[getCodeLength()];
        for (int stopIndex = 0; stopIndex < stopPositions.length(); ++stopIndex) {
            bytecodeLocations[stopPositions.get(stopIndex)] = getBytecodeLocation(stopIndex);
        }
        return bytecodeLocations;
    }

    public int[] getBytecodeToTargetCodePositionMap() {
        return null;
    }

    public List<TargetJavaFrameDescriptor> getJavaFrameDescriptors() {
        return null;
    }

    public TargetJavaFrameDescriptor getTargetFrameDescriptor(int instructionIndex) {
        final TargetCodeInstruction instruction = getInstructions().get(instructionIndex);
        final List<TargetJavaFrameDescriptor> javaFrameDescriptors = getJavaFrameDescriptors();
        if (javaFrameDescriptors != null) {
            final StopPositions stopPositions = getStopPositions();
            if (stopPositions != null) {
                for (int i = 0; i < stopPositions.length(); i++) {
                    if (stopPositions.get(i) == instruction.position) {
                        return javaFrameDescriptors.get(i);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Gets the Java frame descriptor corresponding to a given stop index.
     *
     * @param stopIndex a stop index
     * @return the Java frame descriptor corresponding to {@code stopIndex} or null if there is no Java frame descriptor
     *         for {@code stopIndex}
     * @see TargetMethod#getBytecodeLocationFor(int)
     */
    public BytecodeLocation getBytecodeLocation(int stopIndex) {
        return targetMethod().getBytecodeLocationFor(stopIndex);
    }

    /**
     * Gets the bytecode locations for the inlining chain rooted at a given instruction pointer. The first bytecode
     * location in the returned sequence is the one at the closest position less or equal to the position denoted by
     * {@code instructionPointer}.
     *
     * @param instructionPointer a pointer to an instruction within this method
     * @param implicitExceptionPoint {@code true} if the instruction pointer corresponds to an implicit exception point
     * @return the bytecode locations for the inlining chain rooted at {@code instructionPointer}. This will be null if
     *         no bytecode location can be determined for {@code instructionPointer}.
     * @see TargetMethod#getBytecodeLocationFor(Pointer, boolean)
     */
    protected final BytecodeLocation getBytecodeLocationFor(Pointer instructionPointer, boolean implicitExceptionPoint) {
        return targetMethod().getBytecodeLocationFor(instructionPointer, implicitExceptionPoint);
    }

    /**
     * Gets the name of the source variable corresponding to a stack slot, if any.
     *
     * @param slot a stack slot
     * @return the Java source name for the frame slot, null if not available.
     */
    public String sourceVariableName(MaxStackFrame.Compiled javaStackFrame, int slot) {
        return null;
    }

    public byte[] encodedInlineDataDescriptors() {
        return targetMethod().encodedInlineDataDescriptors();
    }

    /**
     * Disassembles this target method's code to a given writer.
     */
    public void disassemble(IndentWriter writer) {
    }

    // [tw] Warning: duplicated code!
    public MachineCodeInstructionArray getTargetCodeInstructions() {
        final List<TargetCodeInstruction> instructions = getInstructions();
        final MachineCodeInstruction[] result = new MachineCodeInstruction[instructions.size()];
        for (int i = 0; i < result.length; i++) {
            final TargetCodeInstruction ins = instructions.get(i);
            result[i] = new MachineCodeInstruction(ins.mnemonic, ins.position, ins.address.toLong(), ins.label, ins.bytes, ins.operands, ins.getTargetAddressAsLong());
        }
        return new MachineCodeInstructionArray(result);
    }

    public MethodProvider getMethodProvider() {
        return this.teleClassMethodActor;
    }

    @Override
    protected DeepCopier newDeepCopier() {
        return new ReducedDeepCopier();
    }

    @Override
    public ReferenceTypeProvider getReferenceType() {
        return vm().vmAccess().getReferenceType(getClass());
    }

    @Override
    public boolean isRelocatable() {
        return false;
    }
}
