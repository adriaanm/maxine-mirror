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
package com.sun.max.vm.cps.dir.eir;

import java.util.*;

import com.sun.max.*;
import com.sun.max.collect.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.cps.dir.*;
import com.sun.max.vm.cps.eir.*;
import com.sun.max.vm.cps.eir.EirTraceObserver.*;
import com.sun.max.vm.cps.eir.allocate.*;
import com.sun.max.vm.cps.ir.*;
import com.sun.max.vm.type.*;
import com.sun.max.vm.value.*;

/**
 * Aggregates various state required while translating one method from DIR to EIR form.
 *
 * The bulk of the translation is straight-forward,
 * mapping DIR instructions to sequences of EIR instructions.
 * Most of the complications have to do with ABI specifics and register allocation.
 *
 * @author Bernd Mathiske
 */
public abstract class DirToEirMethodTranslation extends EirMethodGeneration {

    public final EirRegister[] calleeSavedEirRegisters;
    public final EirVariable[] calleeSavedEirVariables;
    public final BitSet isCalleeSavedParameter = new BitSet();
    public final EirLocation[] parameterEirLocations;
    public final EirVariable[] eirParameters;

    private final EirVariable[] calleeRepositoryEirVariables;

    protected abstract EirPrologue createPrologue(EirBlock block);

    protected abstract EirInstruction createReturn(EirBlock eirBlock);

    protected EirInstruction createTrampolineExit(EirBlock eirBlock, boolean isStaticTrampoline) {
        return createReturn(eirBlock);
    }

    protected EirInstruction createTrapStubExit(EirBlock eirBlock) {
        return createReturn(eirBlock);
    }

    protected abstract EirEpilogue createEpilogue(EirBlock eirBlock);

    @Override
    protected EirEpilogue createEpilogueAndReturn(EirBlock eirBlock) {
        for (int i = 0; i < calleeSavedEirRegisters.length; i++) {
            eirBlock.appendInstruction(createAssignment(eirBlock, calleeSavedEirRegisters[i].kind(), calleeSavedEirVariables[i], calleeRepositoryEirVariables[i]));
        }
        final EirEpilogue eirEpilogue = createEpilogue(eirBlock);
        eirBlock.appendInstruction(eirEpilogue);
        if (!isTemplate()) {
            ClassMethodActor classMethodActor = eirMethod.classMethodActor();
            if (classMethodActor.isTrampoline()) {
                eirBlock.appendInstruction(createTrampolineExit(eirBlock, classMethodActor.isStaticTrampoline()));
            } else if (eirMethod().classMethodActor().isTrapStub()) {
                eirBlock.appendInstruction(createTrapStubExit(eirBlock));
            } else {
                eirBlock.appendInstruction(createReturn(eirBlock));
            }
        }
        return eirEpilogue;
    }

    protected DirToEirMethodTranslation(EirGenerator eirGenerator, EirMethod eirMethod, DirMethod dirMethod) {
        super(eirGenerator, eirMethod.abi, eirMethod.isTemplate(), eirMethod.isTemplate() || dirMethod.usesMakeStackVariable());
        this.eirMethod = eirMethod;
        this.dirMethod = dirMethod;

        // Location where translated method returns its result.
        resultEirLocation = abi.getReturnLocation(eirMethod.classMethodActor().resultKind());

        final EirRegister safepointLatchRegister = abi.safepointLatchRegister();
        final EirVariable safepointLatchVariable = makeRegisterVariable(safepointLatchRegister);

        final EirVariable[] sharedEirVariables = new EirVariable[abi.registerPool().length()];
        sharedEirVariables[safepointLatchRegister.serial()] = safepointLatchVariable;

        DirVariable[] parameters = dirMethod.parameters();
        Kind[] parameterKinds = new Kind[parameters.length];
        for (int i1 = 0; i1 < parameters.length; i1++) {
            parameterKinds[i1] = parameters[i1].kind();
        }
        parameterEirLocations = abi.getParameterLocations(dirMethod.classMethodActor(), EirStackSlot.Purpose.PARAMETER, parameterKinds);

        Map<EirVariable, EirVariable> refParamToLocalMoves = null;

        eirParameters = new EirVariable[parameterEirLocations.length];
        for (int i = 0; i < parameterEirLocations.length; i++) {
            EirVariable eirParameter = dirToEirVariable(dirMethod.parameters()[i]);
            EirLocation parameterEirLocation = parameterEirLocations[i];
            if (parameterEirLocation instanceof EirRegister) {
                eirParameters[i] = eirParameter;
                final EirRegister eirRegister = (EirRegister) parameterEirLocation;
                sharedEirVariables[eirRegister.serial()] = eirParameter;
            } else {
                eirParameters[i] = eirParameter;
                DirVariable dirParameter = dirMethod.parameters()[i];
                // Reference parameters passed via the stack must be copied to local
                // stack locations as the GC refmaps do not cover the parameter stack locations.
                // Normally this is ok as the parameter stack locations are in the frame
                // of the caller which will have them covered by a stack reference map.
                // However, if there is an adapter frame in between, then the parameter
                // stack locations are in a frame covered by no reference map.
                if (dirParameter.kind().isReference && parameterEirLocation instanceof EirStackSlot) {
                    EirVariable stackReferenceParameter = createEirVariable(Kind.REFERENCE);
                    eirParameters[i] = stackReferenceParameter;
                    if (refParamToLocalMoves == null) {
                        refParamToLocalMoves = new IdentityHashMap<EirVariable, EirVariable>();
                    }
                    refParamToLocalMoves.put(stackReferenceParameter, eirParameter);
                }
            }
        }

        final List<EirRegister> calleeSavedRegisters = Utils.cast(abi.calleeSavedRegisters());
        calleeSavedEirRegisters = calleeSavedRegisters.toArray(new EirRegister[calleeSavedRegisters.size()]);

        calleeSavedEirVariables = new EirVariable[calleeSavedEirRegisters.length];
        calleeRepositoryEirVariables = new EirVariable[calleeSavedEirRegisters.length];
        for (int i = 0; i < calleeSavedEirRegisters.length; i++) {
            final EirVariable sharedEirVariable = sharedEirVariables[calleeSavedEirRegisters[i].serial()];
            if (sharedEirVariable != null) {
                calleeSavedEirVariables[i] = sharedEirVariable;
                isCalleeSavedParameter.set(i);
            } else {
                calleeSavedEirVariables[i] = createEirVariable(calleeSavedEirRegisters[i].kind());
            }
            calleeRepositoryEirVariables[i] = createEirVariable(calleeSavedEirRegisters[i].kind());
            calleeRepositoryEirVariables[i].fixLocation(allocateSpillStackSlot());
        }

        final EirBlock prologueBlock = createEirBlock(IrBlock.Role.NORMAL);
        final EirPrologue prologue = createPrologue(prologueBlock);
        prologueBlock.appendInstruction(prologue);
        for (int i = 0; i < calleeSavedEirRegisters.length; i++) {
            prologueBlock.appendInstruction(createAssignment(prologueBlock, calleeSavedEirRegisters[i].kind(), calleeRepositoryEirVariables[i], calleeSavedEirVariables[i]));
        }

        if (refParamToLocalMoves != null) {
            for (Map.Entry<EirVariable, EirVariable> entry : refParamToLocalMoves.entrySet()) {
                EirVariable localReferenceParameter = entry.getValue();
                EirVariable stackReferenceParameter = entry.getKey();
                prologueBlock.appendInstruction(createAssignment(prologueBlock, Kind.REFERENCE, localReferenceParameter, stackReferenceParameter));
            }
        }

        final Class<PoolSet<EirRegister>> type = null;
        final PoolSet<EirRegister> callerSavedRegisters = Utils.cast(type, abi.callerSavedRegisters());
        if (callerSavedRegisters.contains(abi.framePointer())) {
            prologue.addDefinition(framePointerVariable());
            addEpilogueUse(framePointerVariable());
        }
        prologue.addDefinition(safepointLatchVariable);

        createBodyEirBlocks();

        addJump(prologueBlock, dirToEirBlock.get(Utils.first(dirMethod.blocks())));
    }

    private final EirMethod eirMethod;

    @Override
    public EirMethod eirMethod() {
        return eirMethod;
    }

    @Override
    public ClassMethodActor classMethodActor() {
        return eirMethod.classMethodActor();
    }

    private final DirMethod dirMethod;

    private final EirLocation resultEirLocation;

    public EirLocation resultEirLocation() {
        return resultEirLocation;
    }

    protected final Map<DirBlock, EirBlock> dirToEirBlock = new IdentityHashMap<DirBlock, EirBlock>();

    public EirBlock dirToEirBlock(DirBlock dirBlock) {
        return dirToEirBlock.get(dirBlock);
    }

    protected void createBodyEirBlocks() {
        for (DirBlock dirBlock : dirMethod.blocks()) {
            final EirBlock eirBlock = createEirBlock(dirBlock.role());
            dirToEirBlock.put(dirBlock, eirBlock);
        }
    }

    private final Map<DirVariable, EirVariable> dirToEirVariable = new IdentityHashMap<DirVariable, EirVariable>();

    private EirVariable dirToEirVariable(DirVariable dirVariable) {
        EirVariable eirVariable = dirToEirVariable.get(dirVariable);
        if (eirVariable == null) {
            eirVariable = createEirVariable(dirVariable.kind());
            dirToEirVariable.put(dirVariable, eirVariable);
        }
        return eirVariable;
    }

    private final Map<Value, EirConstant> valueToEirConstant = new HashMap<Value, EirConstant>();

    public EirConstant dirToEirConstant(DirConstant dirConstant) {
        Value value = dirConstant.value();
        return makeEirConstant(value);
    }

    public EirConstant makeEirConstant(Value value) {
        value = value.kind().stackKind.convert(value); // we make no EIR constants smaller than INT

        EirConstant eirConstant = valueToEirConstant.get(value);
        if (eirConstant == null) {
            eirConstant = createEirConstant(value);
            valueToEirConstant.put(value, eirConstant);
        }
        return eirConstant;
    }

    private final Map<ClassMethodActor, EirMethodValue> classMethodActorToEirMethodConstant = new IdentityHashMap<ClassMethodActor, EirMethodValue>();

    public EirValue dirToEirValue(DirValue dirValue) {
        if (dirValue == null) {
            return null;
        } else if (dirValue instanceof DirVariable) {
            return dirToEirVariable((DirVariable) dirValue);
        } else if (dirValue instanceof DirConstant) {
            return dirToEirConstant((DirConstant) dirValue);
        } else if (dirValue == DirValue.UNDEFINED) {
            return EirValue.UNDEFINED;
        } else if (dirValue instanceof DirBlock) {
            return dirToEirBlock((DirBlock) dirValue);
        } else {
            final DirMethodValue dirMethodValue = (DirMethodValue) dirValue;
            final ClassMethodActor classMethodActor = dirMethodValue.classMethodActor();
            return makeEirMethodValue(classMethodActor);
        }
    }

    public EirMethodValue makeEirMethodValue(ClassMethodActor classMethodActor) {
        EirMethodValue eirMethodValue = classMethodActorToEirMethodConstant.get(classMethodActor);
        if (eirMethodValue == null) {
            eirMethodValue = createEirMethodValue(classMethodActor);
            classMethodActorToEirMethodConstant.put(classMethodActor, eirMethodValue);
        }
        return eirMethodValue;
    }

    private EirValue[] dirToEirValues(DirValue[] dirValues) {
        EirValue[] eirValues = new EirValue[dirValues.length];
        for (int i = 0; i < eirValues.length; i++) {
            eirValues[i] = dirToEirValue(dirValues[i]);
        }
        return eirValues;
    }

    public EirJavaFrameDescriptor dirToEirJavaFrameDescriptor(DirJavaFrameDescriptor dirJavaFrameDescriptor,
                                                              EirInstruction instruction) {
        if (dirJavaFrameDescriptor == null) {
            return null;
        }
        return new EirJavaFrameDescriptor(instruction,
                                          dirToEirJavaFrameDescriptor(dirJavaFrameDescriptor.parent(), instruction),
                                          dirJavaFrameDescriptor.classMethodActor,
                                          dirJavaFrameDescriptor.bytecodePosition,
                                          dirToEirValues(dirJavaFrameDescriptor.locals),
                                          dirToEirValues(dirJavaFrameDescriptor.stackSlots));
    }

    protected abstract DirToEirInstructionTranslation createInstructionTranslation(EirBlock eirBlock);

    private void translateBlock(DirBlock dirBlock, EirBlock eirBlock) {
        final DirToEirInstructionTranslation instructionTranslation = createInstructionTranslation(eirBlock);
        if (dirBlock instanceof DirCatchBlock) {
            final DirCatchBlock dirCatchBlock = (DirCatchBlock) dirBlock;
            final EirValue eirCatchParameter = dirToEirValue(dirCatchBlock.catchParameter());
            eirBlock.appendInstruction(new EirCatch(eirBlock, eirCatchParameter, eirGenerator.catchParameterLocation()));
        }
        for (DirInstruction dirInstruction : dirBlock.instructions()) {
            dirInstruction.acceptVisitor(instructionTranslation);
        }
    }

    private void translateDirBlocks() {
        for (DirBlock dirBlock : dirMethod.blocks()) {
            final EirBlock eirBlock = dirToEirBlock(dirBlock);
            translateBlock(dirBlock, eirBlock);
        }
    }

    public void translateMethod() {
        // do the translation
        notifyBeforeTransformation(eirBlocks(), Transformation.INITIAL_EIR_CREATION);
        translateDirBlocks();
        notifyAfterTransformation(eirBlocks(), Transformation.INITIAL_EIR_CREATION);

        // perform register allocation
        EirAllocatorFactory.createAllocator(this).run();

        notifyBeforeTransformation(eirBlocks(), Transformation.BLOCK_LAYOUT);
        rearrangeBlocks();
        notifyAfterTransformation(eirBlocks(), Transformation.BLOCK_LAYOUT);
    }

}
