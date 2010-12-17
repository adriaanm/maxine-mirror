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

import com.sun.cri.bytecode.*;
import com.sun.max.lang.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.compiler.snippet.*;
import com.sun.max.vm.cps.dir.*;
import com.sun.max.vm.cps.dir.transform.*;
import com.sun.max.vm.cps.eir.*;
import com.sun.max.vm.cps.ir.*;
import com.sun.max.vm.type.*;
import com.sun.max.vm.value.*;

/**
 * Platform-independent aspects of translating any kind of DIR instruction to EIR.
 *
 * @author Bernd Mathiske
 */
public abstract class DirToEirInstructionTranslation implements DirVisitor {

    public final DirToEirMethodTranslation methodTranslation;

    private EirBlock eirBlock;

    public final EirBlock eirBlock() {
        return eirBlock;
    }

    public final void setEirBlock(EirBlock eirBlock) {
        this.eirBlock = eirBlock;
    }

    public final EirABI abi() {
        return methodTranslation.eirMethod().abi;
    }

    public final void addInstruction(EirInstruction instruction) {
        eirBlock.appendInstruction(instruction);
    }

    public final EirBlock createEirBlock(IrBlock.Role role) {
        return methodTranslation.createEirBlock(role);
    }

    public final void setBlock(EirBlock block) {
        this.eirBlock = block;
    }

    public final EirBlock dirToEirBlock(DirBlock dirBlock) {
        return methodTranslation.dirToEirBlock(dirBlock);
    }

    public DirToEirInstructionTranslation(DirToEirMethodTranslation methodTranslation, EirBlock eirBlock) {
        this.methodTranslation = methodTranslation;
        this.eirBlock = eirBlock;
    }

    public final void addTry(DirCall dirCall) {
        final EirBlock eirCatchBlock = methodTranslation.dirToEirBlock(dirCall.catchBlock());
        if (eirCatchBlock != null) {
            eirCatchBlock.addPredecessor(eirBlock);
        }
        addInstruction(new EirTry(eirBlock(), eirCatchBlock));
    }

    public final void addJump(EirBlock toBlock) {
        methodTranslation.addJump(eirBlock, toBlock);
    }

    public final void splitBlock() {
        final EirBlock block = createEirBlock(IrBlock.Role.NORMAL);
        addJump(block);
        setEirBlock(block);
    }

    protected abstract DirToEirBuiltinTranslation createBuiltinTranslation(DirToEirInstructionTranslation instructionTranslation, DirJavaFrameDescriptor javaFrameDescriptor);

    public final void visitBuiltinCall(DirBuiltinCall dirBuiltinCall) {
        addTry(dirBuiltinCall);
        final DirToEirBuiltinTranslation builtinCallTranslation = createBuiltinTranslation(this, dirBuiltinCall.javaFrameDescriptor());
        dirBuiltinCall.builtin().acceptVisitor(builtinCallTranslation, dirBuiltinCall.result(), dirBuiltinCall.arguments());
        if (dirBuiltinCall.catchBlock() != null) {
            splitBlock();
        }
    }

    public final EirVariable createEirVariable(Kind kind) {
        return methodTranslation.createEirVariable(kind);
    }

    public EirConstant createEirConstant(Value value) {
        return methodTranslation.createEirConstant(value);
    }

    /**
     * Puts zero into a given variable.
     */
    public abstract void assignZero(Kind kind, EirValue variable);

    public EirValue dirToEirValue(DirValue dirValue) {
        if (dirValue != null && dirValue.isZeroConstant() && (dirValue.kind().width == WordWidth.BITS_64 || dirValue.kind() == Kind.FLOAT)) {
            final EirVariable variable = createEirVariable(dirValue.kind());
            assignZero(dirValue.kind(), variable);
            return variable;
        }
        return methodTranslation.dirToEirValue(dirValue);
    }

    public final EirConstant dirToEirConstant(DirConstant dirConstant) {
        return methodTranslation.dirToEirConstant(dirConstant);
    }

    public final EirInstruction assign(Kind kind, EirValue destination, EirValue source) {
        final EirInstruction assignment = methodTranslation.createAssignment(eirBlock, kind, destination, source);
        eirBlock.appendInstruction(assignment);
        return assignment;
    }

    public void visitReturn(DirReturn dirReturn) {
        final DirValue dirValue = dirReturn.returnValue();
        Kind kind = dirValue.kind();
        if (methodTranslation.usesSharedEpilogue) {
            EirBlock eirEpilogueBlock = methodTranslation.makeEpilogueBlock();
            EirEpilogue eirEpilogue = methodTranslation.makeEpilogue();
            if (kind != Kind.VOID) {
                EirVariable eirResultVariable = (EirVariable) eirEpilogue.resultValue();
                if (eirResultVariable == null) {
                    eirResultVariable = methodTranslation.createEirVariable(kind);
                    eirEpilogue.setResultValue(eirResultVariable);
                }
                assign(kind, eirResultVariable, methodTranslation.dirToEirValue(dirValue));
            }
            addJump(eirEpilogueBlock);
        } else {
            final EirEpilogue eirEpilogue = methodTranslation.createEpilogueAndReturn(eirBlock);
            if (kind != Kind.VOID) {
                final EirValue eirResultValue = methodTranslation.dirToEirValue(dirValue);
                eirEpilogue.setResultValue(eirResultValue);
            }
        }
    }

    private void generateCall(DirJavaFrameDescriptor dirJavaFrameDescriptor, EirABI abi, Kind resultKind, EirValue result, EirValue function, Kind[] argumentKinds, boolean isNativeFunctionCall, EirValue... arguments) {
        final EirLocation[] argumentLocations = abi.getParameterLocations(EirStackSlot.Purpose.LOCAL, argumentKinds);
        final EirLocation resultLocation = (result == null) ? null : abi.getResultLocation(resultKind);
        final boolean isTemplate = methodTranslation.isTemplate();
        final EirCall instruction = isTemplate ?
                        methodTranslation.createRuntimeCall(eirBlock, abi, result, resultLocation, function, arguments, argumentLocations) :
                        methodTranslation.createCall(eirBlock, abi, result, resultLocation, function, arguments, argumentLocations, isNativeFunctionCall);
        addInstruction(instruction);
        if (!isTemplate) {
            instruction.setEirJavaFrameDescriptor(methodTranslation.dirToEirJavaFrameDescriptor(dirJavaFrameDescriptor, instruction));
        }
    }

    private EirValue raiseThrowableEirValue = null;

    private EirValue makeRaiseThrowableEirValue() {
        if (raiseThrowableEirValue == null) {
            raiseThrowableEirValue = methodTranslation.makeEirMethodValue(Snippet.RaiseThrowable.SNIPPET.executable);
        }
        return raiseThrowableEirValue;
    }

    public final void visitThrow(DirThrow dirThrow) {
        addInstruction(new EirTry(eirBlock(), null));
        final MethodActor classMethodActor = Snippet.RaiseThrowable.SNIPPET.executable;
        final EirValue eirThrowable = methodTranslation.dirToEirValue(dirThrow.throwable());
        generateCall(null, methodTranslation.eirGenerator.eirABIsScheme().javaABI, null, null,
                     makeRaiseThrowableEirValue(), classMethodActor.getParameterKinds(), false, eirThrowable);
        // No need for a JavaFrameDescriptor here.
        // Throw.raise() disables safepoints until the exception has been delivered to its dispatcher.
    }

    public final void visitMethodCall(DirMethodCall dirMethodCall) {
        addTry(dirMethodCall);

        final DirValue dirResult = dirMethodCall.result();
        final Kind resultKind = (dirResult == null) ? null : dirResult.kind();
        final EirValue eirResult = dirToEirValue(dirResult);
        final EirValue methodEirValue = dirToEirValue(dirMethodCall.method());

        final int numberOfArguments = dirMethodCall.arguments().length;
        final Kind[] argumentKinds = new Kind[numberOfArguments];
        final EirValue[] eirArguments = new EirValue[numberOfArguments];
        for (int i = 0; i < numberOfArguments; i++) {
            final DirValue dirArgument = dirMethodCall.arguments()[i];
            argumentKinds[i] = dirArgument.kind();
            eirArguments[i] = dirToEirValue(dirArgument);
        }

        EirABI abi;
        boolean isNativeFunctionCall = false;
        if (dirMethodCall.method() instanceof DirMethodValue) {
            final DirMethodValue dirMethodValue = (DirMethodValue) dirMethodCall.method();
            abi = methodTranslation.eirGenerator.eirABIsScheme().getABIFor(dirMethodValue.classMethodActor());
        } else if (dirMethodCall.isNative()) {
            abi = methodTranslation.eirGenerator.eirABIsScheme().nativeABI;
            isNativeFunctionCall = true;
        } else {
            abi = methodTranslation.eirGenerator.eirABIsScheme().javaABI;
        }

        generateCall(dirMethodCall.javaFrameDescriptor(), abi, resultKind, eirResult, methodEirValue, argumentKinds, isNativeFunctionCall, eirArguments);

        if (dirMethodCall.catchBlock() != null && methodEirValue != makeRaiseThrowableEirValue()) {
            splitBlock();
        }
    }

    public final void visitGoto(DirGoto dirGoto) {
        final EirBlock block = methodTranslation.dirToEirBlock(dirGoto.targetBlock());
        addJump(block);
    }

    public final void visitAssign(DirAssign dirAssign) {
        final EirValue destination = methodTranslation.dirToEirValue(dirAssign.destination());
        final EirValue source = methodTranslation.dirToEirValue(dirAssign.source());
        assign(destination.kind(), destination, source);
    }

    public final void visitInfopoint(DirInfopoint dirInfopoint) {
        final EirValue destination = dirInfopoint.opcode == Bytecodes.HERE ? createEirVariable(Kind.LONG) : null;
        final EirInfopoint instruction = methodTranslation.createInfopoint(eirBlock(), dirInfopoint.opcode, destination);
        addInstruction(instruction);
        instruction.setEirJavaFrameDescriptor(methodTranslation.dirToEirJavaFrameDescriptor(dirInfopoint.javaFrameDescriptor(), instruction));
        if (dirInfopoint.opcode == Bytecodes.HERE) {
            final EirValue result = dirToEirValue(dirInfopoint.destination);
            assign(Kind.LONG, result, destination);
        }
    }
}
