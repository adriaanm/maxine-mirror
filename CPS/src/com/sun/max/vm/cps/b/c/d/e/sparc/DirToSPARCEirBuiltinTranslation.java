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
package com.sun.max.vm.cps.b.c.d.e.sparc;

import com.sun.c1x.bytecode.Bytecodes.*;
import com.sun.max.asm.sparc.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.compiler.builtin.*;
import com.sun.max.vm.compiler.builtin.AddressBuiltin.*;
import com.sun.max.vm.compiler.builtin.IEEE754Builtin.*;
import com.sun.max.vm.compiler.builtin.JavaBuiltin.*;
import com.sun.max.vm.compiler.builtin.PointerAtomicBuiltin.*;
import com.sun.max.vm.compiler.builtin.PointerLoadBuiltin.*;
import com.sun.max.vm.compiler.builtin.PointerStoreBuiltin.*;
import com.sun.max.vm.compiler.builtin.SpecialBuiltin.*;
import com.sun.max.vm.cps.dir.*;
import com.sun.max.vm.cps.dir.eir.*;
import com.sun.max.vm.cps.dir.eir.sparc.*;
import com.sun.max.vm.cps.eir.*;
import com.sun.max.vm.cps.eir.sparc.*;
import com.sun.max.vm.cps.eir.sparc.SPARCEirInstruction.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.type.*;
import com.sun.max.vm.value.*;

/**
 * @author Bernd Mathiske
 * @author Laurent Daynes
 */
class DirToSPARCEirBuiltinTranslation extends DirToEirBuiltinTranslation {

    DirToSPARCEirBuiltinTranslation(DirToSPARCEirInstructionTranslation instructionTranslation, DirJavaFrameDescriptor javaFrameDescriptor) {
        super(instructionTranslation, javaFrameDescriptor);
    }

    private abstract class Unary {
        protected abstract EirInstruction createOperation(EirValue operand);

        protected Unary(Kind kind, DirValue dirResult, DirValue[] dirArguments) {
            final EirValue result = dirToEirValue(dirResult);
            final EirValue argument = dirToEirValue(dirArguments[0]);
            assign(kind, result, argument);
            addInstruction(createOperation(result));
        }
    }

    @Override
    public void visitDoubleNegated(DoubleNegated builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Unary(Kind.DOUBLE, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue operand) {
                return new FNEG_D(eirBlock(), operand);
            }
        };
    }
    @Override
    public void visitFloatNegated(FloatNegated builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Unary(Kind.FLOAT, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue operand) {
                return new FNEG_S(eirBlock(), operand);
            }
        };
    }
    @Override
    public void visitIntNegated(IntNegated builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Unary(Kind.INT, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue operand) {
                return new NEG_I32(eirBlock(), operand);
            }
        };
    }

    @Override
    public void visitLongNegated(LongNegated builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Unary(Kind.LONG, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue operand) {
                return new NEG_I64(eirBlock(), operand);
            }
        };
    }

    @Override
    public void visitIntNot(IntNot builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Unary(Kind.INT, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue operand) {
                return new NOT_I32(eirBlock(), operand);
            }
        };
    }

    @Override
    public void visitLongNot(LongNot builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Unary(Kind.LONG, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue operand) {
                return new NOT_I64(eirBlock(), operand);
            }
        };
    }
    private abstract class Binary {
        protected abstract EirInstruction createOperation(EirValue destination, EirValue source1, EirValue source2);

        private Binary(Kind destinationKind, Kind sourceKind, DirValue dirResult, DirValue[] dirArguments) {
            final EirValue result = dirToEirValue(dirResult);
            final EirValue source1 = dirToEirValue(dirArguments[0]);
            final EirValue source2 = dirToEirValue(dirArguments[1]);
            addInstruction(createOperation(result, source1, source2));
        }

        private Binary(Kind kind, DirValue dirResult, DirValue[] dirArguments) {
            this(kind, kind, dirResult, dirArguments);
        }
    }

    @Override
    public void visitIntPlus(IntPlus builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Binary(Kind.INT, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue destination, EirValue source1, EirValue source2) {
                return new SPARCEirOperation.ADD_I32(eirBlock(), destination, source1, source2);
            }
        };
    }

    @Override
    public void visitFloatPlus(FloatPlus builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Binary(Kind.FLOAT, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue destination, EirValue source1, EirValue source2) {
                return new SPARCEirOperation.FADD_S(eirBlock(), destination, source1, source2);
            }
        };
    }

    @Override
    public void visitLongPlus(LongPlus builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Binary(Kind.LONG, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue destination, EirValue source1, EirValue source2) {
                return new ADD_I64(eirBlock(), destination, source1, source2);
            }
        };
    }

    @Override
    public void visitDoublePlus(DoublePlus builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Binary(Kind.DOUBLE, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue destination, EirValue source1, EirValue source2) {
                return new SPARCEirOperation.FADD_D(eirBlock(), destination, source1, source2);
            }
        };
    }

    @Override
    public void visitIntMinus(IntMinus builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Binary(Kind.INT, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue destination, EirValue source1, EirValue source2) {
                return new SUB_I32(eirBlock(), destination, source1, source2);
            }
        };
    }

    @Override
    public void visitFloatMinus(FloatMinus builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Binary(Kind.FLOAT, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue destination, EirValue source1, EirValue source2) {
                return new SPARCEirOperation.FSUB_S(eirBlock(), destination, source1, source2);
            }
        };
    }

    @Override
    public void visitLongMinus(LongMinus builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Binary(Kind.LONG, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue destination, EirValue source1, EirValue source2) {
                return new SUB_I64(eirBlock(), destination, source1, source2);
            }
        };
    }

    @Override
    public void visitDoubleMinus(DoubleMinus builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Binary(Kind.DOUBLE, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue destination, EirValue source1, EirValue source2) {
                return new FSUB_D(eirBlock(), destination, source1, source2);
            }
        };
    }

    @Override
    public void visitIntTimes(IntTimes builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Binary(Kind.INT, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue destination, EirValue source1, EirValue source2) {
                return new MUL_I32(eirBlock(), destination, source1, source2);
            }
        };
    }

    @Override
    public void visitFloatTimes(FloatTimes builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Binary(Kind.FLOAT, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue destination, EirValue source1, EirValue source2) {
                return new FMUL_S(eirBlock(), destination, source1, source2);
            }
        };
    }

    @Override
    public void visitLongTimes(LongTimes builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Binary(Kind.LONG, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue destination, EirValue source1, EirValue source2) {
                return new MUL_I64(eirBlock(), destination, source1, source2);
            }
        };
    }

    @Override
    public void visitDoubleTimes(DoubleTimes builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Binary(Kind.DOUBLE, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue destination, EirValue source1, EirValue source2) {
                return new FMUL_D(eirBlock(), destination, source1, source2);
            }
        };
    }

    @Override
    public void visitIntDivided(IntDivided builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Binary(Kind.INT, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue destination, EirValue source, EirValue divisor) {
                return new DIV_I32(eirBlock(), destination, source, divisor);
            }
        };
    }

    @Override
    public void visitFloatDivided(FloatDivided builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Binary(Kind.FLOAT, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue destination, EirValue source, EirValue divisor) {
                return new FDIV_S(eirBlock(), destination, source, divisor);
            }
        };
    }

    @Override
    public void visitLongDivided(LongDivided builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Binary(Kind.LONG, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue destination, EirValue source, EirValue divisor) {
                return new DIV_I64(eirBlock(), destination, source, divisor);
            }
        };
    }

    @Override
    public void visitDoubleDivided(DoubleDivided builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Binary(Kind.DOUBLE, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue destination, EirValue source, EirValue divisor) {
                return new FDIV_D(eirBlock(), destination, source, divisor);
            }
        };
    }

    @Override
    public void visitIntShiftedLeft(IntShiftedLeft builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Binary(Kind.INT, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue destination, EirValue source1, EirValue source2) {
                return new SLL_I32(eirBlock(), destination, source1, source2);
            }
        };
    }
    @Override
    public void visitLongShiftedLeft(LongShiftedLeft builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Binary(Kind.LONG, Kind.INT, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue destination, EirValue source1, EirValue source2) {
                return new SLL_I64(eirBlock(), destination, source1, source2);
            }
        };
    }

    @Override
    public void visitIntSignedShiftedRight(IntSignedShiftedRight builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Binary(Kind.INT, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue destination, EirValue source1, EirValue source2) {
                return new SRA_I32(eirBlock(), destination, source1, source2);
            }
        };
    }
    @Override
    public void visitLongSignedShiftedRight(LongSignedShiftedRight builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Binary(Kind.LONG, Kind.INT, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue destination, EirValue source1, EirValue source2) {
                return new SRA_I64(eirBlock(), destination, source1, source2);
            }
        };
    }

    @Override
    public void visitIntUnsignedShiftedRight(IntUnsignedShiftedRight builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Binary(Kind.INT, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue destination, EirValue source1, EirValue source2) {
                return new SRL_I32(eirBlock(), destination, source1, source2);
            }
        };
    }

    @Override
    public void visitLongUnsignedShiftedRight(LongUnsignedShiftedRight builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Binary(Kind.LONG, Kind.INT, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue destination, EirValue source1, EirValue source2) {
                return new SRL_I64(eirBlock(), destination, source1, source2);
            }
        };
    }

    @Override
    public void visitIntAnd(IntAnd builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Binary(Kind.INT, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue destination, EirValue source1, EirValue source2) {
                return new AND_I32(eirBlock(), destination, source1, source2);
            }
        };
    }
    @Override
    public void visitLongAnd(LongAnd builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Binary(Kind.LONG, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue destination, EirValue source1, EirValue source2) {
                return new AND_I64(eirBlock(), destination, source1, source2);
            }
        };
    }

    @Override
    public void visitIntOr(IntOr builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Binary(Kind.INT, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue destination, EirValue source1, EirValue source2) {
                return new OR_I32(eirBlock(), destination, source1, source2);
            }
        };
    }
    @Override
    public void visitLongOr(LongOr builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Binary(Kind.LONG, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue destination, EirValue source1, EirValue source2) {
                return new OR_I64(eirBlock(), destination, source1, source2);
            }
        };
    }

    @Override
    public void visitIntXor(IntXor builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Binary(Kind.INT, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue destination, EirValue source1, EirValue source2) {
                return new XOR_I32(eirBlock(), destination, source1, source2);
            }
        };
    }
    @Override
    public void visitLongXor(LongXor builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Binary(Kind.LONG, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue destination, EirValue source1, EirValue source2) {
                return new XOR_I64(eirBlock(), destination, source1, source2);
            }
        };
    }

    @Override
    public void visitLongCompare(LongCompare builtin, DirValue dirResult, DirValue[] dirArguments) {
        final EirValue result = dirToEirValue(dirResult);
        final EirValue a = dirToEirValue(dirArguments[0]);
        final EirValue b = dirToEirValue(dirArguments[1]);
        addInstruction(new CMP_I64(eirBlock(), a, b));
        addInstruction(new MOV_I32(eirBlock(),  result, createEirConstant(IntValue.ZERO)));
        addInstruction(new MOVG(eirBlock(),  ICCOperand.XCC, result, createEirConstant(IntValue.ONE)));
        addInstruction(new MOVL(eirBlock(),  ICCOperand.XCC, result, createEirConstant(IntValue.MINUS_ONE)));
    }

    @Override
    public void visitFloatCompareL(FloatCompareL builtin, DirValue dirResult, DirValue[] dirArguments) {
        final EirValue result = dirToEirValue(dirResult);
        final EirValue a = dirToEirValue(dirArguments[0]);
        final EirValue b = dirToEirValue(dirArguments[1]);
        addInstruction(new FCMP_S(eirBlock(), a, b));
        addInstruction(new MOV_I32(eirBlock(),  result, createEirConstant(IntValue.MINUS_ONE))); // if a < b or unordered
        addInstruction(new MOVFE(eirBlock(),  FCCOperand.FCC0, result, createEirConstant(IntValue.ZERO)));
        addInstruction(new MOVFG(eirBlock(),  FCCOperand.FCC0, result, createEirConstant(IntValue.ONE)));
    }

    @Override
    public void visitFloatCompareG(FloatCompareG builtin, DirValue dirResult, DirValue[] dirArguments) {
        final EirValue result = dirToEirValue(dirResult);
        final EirValue a = dirToEirValue(dirArguments[0]);
        final EirValue b = dirToEirValue(dirArguments[1]);
        addInstruction(new FCMP_S(eirBlock(), a, b));
        addInstruction(new MOV_I32(eirBlock(),  result, createEirConstant(IntValue.ONE))); // if a > b or unordered
        addInstruction(new MOVFE(eirBlock(),  FCCOperand.FCC0, result, createEirConstant(IntValue.ZERO)));
        addInstruction(new MOVFL(eirBlock(),  FCCOperand.FCC0, result, createEirConstant(IntValue.MINUS_ONE)));
    }

    @Override
    public void visitDoubleCompareL(DoubleCompareL builtin, DirValue dirResult, DirValue[] dirArguments) {
        final EirValue result = dirToEirValue(dirResult);
        final EirValue a = dirToEirValue(dirArguments[0]);
        final EirValue b = dirToEirValue(dirArguments[1]);
        addInstruction(new FCMP_D(eirBlock(), a, b));
        addInstruction(new MOV_I32(eirBlock(),  result, createEirConstant(IntValue.MINUS_ONE))); // if a < b or unordered
        addInstruction(new MOVFE(eirBlock(),  FCCOperand.FCC0, result, createEirConstant(IntValue.ZERO)));
        addInstruction(new MOVFG(eirBlock(),  FCCOperand.FCC0, result, createEirConstant(IntValue.ONE)));
    }

    @Override
    public void visitDoubleCompareG(DoubleCompareG builtin, DirValue dirResult, DirValue[] dirArguments) {
        final EirValue result = dirToEirValue(dirResult);
        final EirValue a = dirToEirValue(dirArguments[0]);
        final EirValue b = dirToEirValue(dirArguments[1]);
        addInstruction(new FCMP_D(eirBlock(), a, b));
        addInstruction(new MOV_I32(eirBlock(),  result, createEirConstant(IntValue.ONE))); // if a > b or unordered
        addInstruction(new MOVFE(eirBlock(),  FCCOperand.FCC0, result, createEirConstant(IntValue.ZERO)));
        addInstruction(new MOVFL(eirBlock(),  FCCOperand.FCC0, result,  createEirConstant(IntValue.MINUS_ONE)));
    }

    @Override
    public void visitCompareInts(CompareInts builtin, DirValue dirResult, DirValue[] dirArguments) {
        assert dirResult == null;
        final EirValue a = dirToEirValue(dirArguments[0]);
        final EirValue b = dirToEirValue(dirArguments[1]);
        addInstruction(new CMP_I32(eirBlock(), a, b));
    }

    @Override
    public void visitCompareWords(CompareWords builtin, DirValue dirResult, DirValue[] dirArguments) {
        assert dirResult == null;
        final EirValue a = dirToEirValue(dirArguments[0]);
        final EirValue b = dirToEirValue(dirArguments[1]);
        addInstruction(new CMP_I64(eirBlock(), a, b));
    }

    private MembarOperand toMembarOperand(int memoryBarriers) {
        MembarOperand operand = MembarOperand.NO_MEMBAR;
        if ((memoryBarriers & MemoryBarriers.LOAD_LOAD) != 0) {
            operand = operand.or(MembarOperand.LOAD_LOAD);
        }
        if ((memoryBarriers & MemoryBarriers.LOAD_STORE) != 0) {
            operand = operand.or(MembarOperand.LOAD_STORE);
        }
        if ((memoryBarriers & MemoryBarriers.STORE_STORE) != 0) {
            operand = operand.or(MembarOperand.STORE_STORE);
        }
        if ((memoryBarriers & MemoryBarriers.STORE_LOAD) != 0) {
            operand = operand.or(MembarOperand.STORE_LOAD);
        }
        return operand;
    }

    private static final MembarOperand FULL_MEMBAR = MembarOperand.LOAD_LOAD.or(MembarOperand.LOAD_STORE).or(MembarOperand.STORE_LOAD).or(MembarOperand.STORE_STORE);

    @Override
    public void visitBarMemory(BarMemory builtin, DirValue dirResult, DirValue[] dirArguments) {
        assert dirResult == null;
        assert dirArguments[0].isConstant();
        final DirConstant dirConstant = (DirConstant) dirArguments[0];
        assert dirConstant.value().kind() == Kind.INT;
        final int memoryBarriers = dirConstant.value().asInt() & ~(methodTranslation().memoryModel.impliedBarriers);
        if (memoryBarriers == 0) {
            return;
        }
        final MembarOperand membarOperand = toMembarOperand(memoryBarriers);
        if (!membarOperand.equals(MembarOperand.NO_MEMBAR)) {
            addInstruction(new MEMBAR(eirBlock(), membarOperand));
        }
    }

    @Override
    public void visitFlushRegisterWindows(FlushRegisterWindows builtin, DirValue dirResult, DirValue[] dirArguments) {
        assert dirResult == null;
        addInstruction(new FLUSHW(eirBlock()));
    }

    @Override
    public void visitMakeStackVariable(MakeStackVariable builtin, DirValue dirResult, DirValue[] dirArguments) {
        assert dirArguments.length == 1;
        final EirVariable result = (EirVariable) dirToEirValue(dirResult);
        final EirValue value = dirToEirValue(dirArguments[0]);

        final EirVariable stackSlot;
        if (value instanceof EirVariable) {
            stackSlot = (EirVariable) value;
        } else {
            stackSlot = createEirVariable(value.kind());
            assign(value.kind(), stackSlot, value);
        }
        result.setAliasedVariable(stackSlot);
        methodTranslation().addEpilogueStackSlotUse(stackSlot);
        addInstruction(new SET_STACK_ADDRESS(eirBlock(), result, stackSlot));
    }

    @Override
    public void visitStackAllocate(StackAllocate builtin, DirValue dirResult, DirValue[] dirArguments) {
        assert dirArguments.length == 1;
        assert dirArguments[0] instanceof DirConstant;
        final int size = ((DirConstant) dirArguments[0]).value().asInt();
        final int offset = methodTranslation().addStackAllocation(size);
        final EirVariable result = (EirVariable) dirToEirValue(dirResult);
        addInstruction(new STACK_ALLOCATE(eirBlock(), result, offset));
    }

    private void integerToFloatingPointRegister(DirValue dirDestination, DirValue dirSource, Kind loadKind) {
        integerToFloatingPointRegister(dirToEirValue(dirDestination),  dirToEirValue(dirSource), loadKind);
    }

    private void integerToFloatingPointRegister(EirValue destination, EirValue source, Kind loadKind) {
        final EirConstant offsetToConversionArea = ((DirToSPARCEirMethodTranslation) methodTranslation()).offsetToConversionArea();
        final EirValue stackPointer = methodTranslation().stackPointerVariable();
        addInstruction(new SPARCEirStore(eirBlock(), source.kind(), source, stackPointer, Kind.INT, offsetToConversionArea));
        addInstruction(new SPARCEirLoad(eirBlock(), loadKind, destination, stackPointer, Kind.INT, offsetToConversionArea));
    }

    /**
     * Moving a Floating point to a integer register requires storing the floating point value first  in memory, then reloading it.
     * The store operation needs to be told a floating-point kind for the value to stored. The kind for the load is the kind of the destination
     * variable.
     * @param dirDestination
     * @param dirSource
     * @param storeKind kind to be used in the "store" operation.
     */
    private void floatingPointToIntegerRegister(DirValue dirDestination, DirValue dirSource, Kind storeKind) {
        floatingPointToIntegerRegister(dirToEirValue(dirDestination),  dirToEirValue(dirSource), storeKind);
    }

    private void floatingPointToIntegerRegister(EirValue destination, EirValue source, Kind storeKind) {
        final EirConstant offsetToConversionArea = ((DirToSPARCEirMethodTranslation) methodTranslation()).offsetToConversionArea();
        final EirValue stackPointer = methodTranslation().stackPointerVariable();
        addInstruction(new SPARCEirStore(eirBlock(), storeKind, source, stackPointer, Kind.INT, offsetToConversionArea));
        addInstruction(new SPARCEirLoad(eirBlock(), destination.kind(), destination, stackPointer, Kind.INT, offsetToConversionArea));
    }

    @Override
    public void visitIntToFloat(IntToFloat builtin, DirValue dirResult, DirValue[] dirArguments) {
        integerToFloatingPointRegister(dirResult,  dirArguments[0], Kind.FLOAT);
    }

    @Override
    public void visitFloatToInt(FloatToInt builtin, DirValue dirResult, DirValue[] dirArguments) {
        floatingPointToIntegerRegister(dirResult,  dirArguments[0], Kind.FLOAT);
    }

    @Override
    public void visitLongToDouble(LongToDouble builtin, DirValue dirResult, DirValue[] dirArguments) {
        integerToFloatingPointRegister(dirResult,  dirArguments[0], Kind.DOUBLE);
    }

    @Override
    public void visitDoubleToLong(DoubleToLong builtin, DirValue dirResult, DirValue[] dirArguments) {
        floatingPointToIntegerRegister(dirResult,  dirArguments[0], Kind.DOUBLE);
    }

    private abstract class IntegerToFloatingPointConversion {
        protected abstract SPARCEirOperation convert(EirValue destination, EirValue temp);
        protected IntegerToFloatingPointConversion(DirValue dirResult, DirValue[] dirArguments, Kind tempKind) {
            final EirValue result = dirToEirValue(dirResult);
            final EirValue argument = dirToEirValue(dirArguments[0]);
            final EirVariable temp = createEirVariable(tempKind);
            integerToFloatingPointRegister(temp, argument, tempKind);
            addInstruction(convert(result, temp));
        }
    }

    private abstract class FloatingPointToIntegerConversion {
        protected abstract SPARCEirOperation convert(EirValue temp, EirValue source);

        protected FloatingPointToIntegerConversion(DirValue dirResult, DirValue[] dirArguments, Kind tempKind) {
            final EirValue result = dirToEirValue(dirResult);
            final EirValue argument = dirToEirValue(dirArguments[0]);
            final EirVariable temp = createEirVariable(tempKind);
            addInstruction(convert(temp, argument));
            floatingPointToIntegerRegister(result, temp, tempKind);
        }
    }

    private static final IntValue SHIFT_16 = IntValue.from(16);
    private static final IntValue SHIFT_24 = IntValue.from(24);

    private void signedConvertFromInt(DirValue dirResult, DirValue[] dirArguments, IntValue shiftCount) {
        final EirValue result = dirToEirValue(dirResult);
        final EirValue argument = dirToEirValue(dirArguments[0]);
        final EirConstant shiftConstant = createEirConstant(shiftCount);
        addInstruction(new SLL_I32(eirBlock(), result, argument, shiftConstant));
        addInstruction(new SRA_I32(eirBlock(), result, shiftConstant));
    }

    @Override
    public void visitConvertIntToByte(ConvertIntToByte builtin, DirValue dirResult, DirValue[] dirArguments) {
        signedConvertFromInt(dirResult, dirArguments, SHIFT_24);
    }

    @Override
    public void visitConvertIntToShort(ConvertIntToShort builtin, DirValue dirResult, DirValue[] dirArguments) {
        signedConvertFromInt(dirResult, dirArguments, SHIFT_16);
    }

    @Override
    public void visitConvertIntToChar(ConvertIntToChar builtin, DirValue dirResult, DirValue[] dirArguments) {
        final EirValue result = dirToEirValue(dirResult);
        final EirValue argument = dirToEirValue(dirArguments[0]);
        final EirConstant shiftConstant = createEirConstant(SHIFT_16);
        addInstruction(new SLL_I32(eirBlock(), result, argument, shiftConstant));
        addInstruction(new SRL_I32(eirBlock(), result, shiftConstant));
    }

    @Override
    public void visitConvertIntToLong(ConvertIntToLong builtin, DirValue dirResult, DirValue[] dirArguments) {
        final EirValue result = dirToEirValue(dirResult);
        final EirValue argument = dirToEirValue(dirArguments[0]);
        final EirConstant shiftConstant = createEirConstant(IntValue.ZERO);
        // Shift right arithmetic by zero causes the 32-bit sign to extend to 64
        addInstruction(new SRA_I32(eirBlock(), result, argument, shiftConstant));
    }

    @Override
    public void visitConvertLongToInt(ConvertLongToInt builtin, DirValue dirResult, DirValue[] dirArguments) {
        final EirValue result = dirToEirValue(dirResult);
        final EirValue argument = dirToEirValue(dirArguments[0]);
        final EirConstant shiftConstant = createEirConstant(IntValue.ZERO);
        // Shift right arithmetic by zero causes the 32-bit sign to be setup
        addInstruction(new SRA_I32(eirBlock(), result, argument, shiftConstant));
    }

    @Override
    public void visitConvertIntToFloat(ConvertIntToFloat builtin, DirValue dirResult, DirValue[] dirArguments) {
        assert dirArguments[0].kind() == Kind.INT;
        new IntegerToFloatingPointConversion(dirResult, dirArguments, Kind.FLOAT) {
            @Override
            protected SPARCEirOperation convert(EirValue destination, EirValue temp) {
                return new FITOS(eirBlock(), destination, temp);
            }
        };
    }

    @Override
    public void visitConvertIntToDouble(ConvertIntToDouble builtin, DirValue dirResult, DirValue[] dirArguments) {
        assert dirArguments[0].kind() == Kind.INT;
        new IntegerToFloatingPointConversion(dirResult, dirArguments, Kind.FLOAT) {
            @Override
            protected SPARCEirOperation convert(EirValue destination, EirValue temp) {
                return new FITOD(eirBlock(), destination, temp);
            }
        };
    }

    @Override
    public void visitConvertLongToFloat(ConvertLongToFloat builtin, DirValue dirResult, DirValue[] dirArguments) {
        assert dirArguments[0].kind() == Kind.LONG;
        new IntegerToFloatingPointConversion(dirResult, dirArguments, Kind.DOUBLE) {
            @Override
            protected SPARCEirOperation convert(EirValue destination, EirValue temp) {
                return new FXTOS(eirBlock(), destination, temp);
            }
        };
    }

    @Override
    public void visitConvertLongToDouble(ConvertLongToDouble builtin, DirValue dirResult, DirValue[] dirArguments) {
        assert dirArguments[0].kind() == Kind.LONG || dirArguments[0].kind().isWord;
        new IntegerToFloatingPointConversion(dirResult, dirArguments, Kind.DOUBLE) {
            @Override
            protected SPARCEirOperation convert(EirValue destination, EirValue temp) {
                return new FXTOD(eirBlock(), destination, temp);
            }
        };
    }

    @Override
    public void visitConvertFloatToInt(ConvertFloatToInt builtin, DirValue dirResult, DirValue[] dirArguments) {
        assert dirArguments[0].kind() == Kind.FLOAT;
        new FloatingPointToIntegerConversion(dirResult, dirArguments, Kind.FLOAT) {
            @Override
            protected SPARCEirOperation convert(EirValue temp, EirValue source) {
                return new FSTOI(eirBlock(), temp, source);
            }
        };
    }

    @Override
    public void visitConvertFloatToLong(ConvertFloatToLong builtin, DirValue dirResult, DirValue[] dirArguments) {
        assert dirArguments[0].kind() == Kind.FLOAT;
        new FloatingPointToIntegerConversion(dirResult, dirArguments, Kind.DOUBLE) {
            @Override
            protected SPARCEirOperation convert(EirValue temp, EirValue source) {
                return new FSTOX(eirBlock(), temp, source);
            }
        };
    }

    @Override
    public void visitConvertDoubleToInt(ConvertDoubleToInt builtin, DirValue dirResult, DirValue[] dirArguments) {
        assert dirArguments[0].kind() == Kind.DOUBLE;
        new FloatingPointToIntegerConversion(dirResult, dirArguments, Kind.FLOAT) {
            @Override
            protected SPARCEirOperation convert(EirValue temp, EirValue source) {
                return new FDTOI(eirBlock(), temp, source);
            }
        };
    }

    @Override
    public void visitConvertDoubleToLong(ConvertDoubleToLong builtin, DirValue dirResult, DirValue[] dirArguments) {
        assert dirArguments[0].kind() == Kind.DOUBLE;
        new FloatingPointToIntegerConversion(dirResult, dirArguments, Kind.DOUBLE) {
            @Override
            protected SPARCEirOperation convert(EirValue temp, EirValue source) {
                return new FDTOX(eirBlock(), temp, source);
            }
        };
    }

    @Override
    public void visitConvertFloatToDouble(ConvertFloatToDouble builtin, DirValue dirResult, DirValue[] dirArguments) {
        final EirValue result = dirToEirValue(dirResult);
        final EirValue argument = dirToEirValue(dirArguments[0]);
        addInstruction(new FSTOD(eirBlock(), result, argument));
    }

    @Override
    public void visitConvertDoubleToFloat(ConvertDoubleToFloat builtin, DirValue dirResult, DirValue[] dirArguments) {
        final EirValue result = dirToEirValue(dirResult);
        final EirValue argument = dirToEirValue(dirArguments[0]);
        addInstruction(new FDTOS(eirBlock(), result, argument));
    }

    @Override
    public void visitAboveEqual(AboveEqual builtin, DirValue dirResult, DirValue[] dirArguments) {
        final EirValue result = dirToEirValue(dirResult);
        final EirValue a = dirToEirValue(dirArguments[0]);
        final EirValue b = dirToEirValue(dirArguments[1]);

        addInstruction(new ZERO(eirBlock(), Kind.WORD, result));
        addInstruction(new CMP_I32(eirBlock(), a, b));
        //The result of the comparison has to be interpreted as unsigned
        addInstruction(new MOVCC(eirBlock(), ICCOperand.ICC, result, createEirConstant(IntValue.ONE)));
    }

    @Override
    public void visitLessEqual(LessEqual builtin, DirValue dirResult, DirValue[] dirArguments) {
        final EirValue result = dirToEirValue(dirResult);
        final EirValue a = dirToEirValue(dirArguments[0]);
        final EirValue b = dirToEirValue(dirArguments[1]);

        addInstruction(new ZERO(eirBlock(), Kind.WORD, result));
        addInstruction(new CMP_I64(eirBlock(), a, b));
        //The result of the comparison has to be interpreted as unsigned
        addInstruction(new MOVLEU(eirBlock(), ICCOperand.XCC, result, createEirConstant(IntValue.ONE)));
    }

    @Override
    public void visitLessThan(LessThan builtin, DirValue dirResult, DirValue[] dirArguments) {
        final EirValue result = dirToEirValue(dirResult);
        final EirValue a = dirToEirValue(dirArguments[0]);
        final EirValue b = dirToEirValue(dirArguments[1]);

        addInstruction(new ZERO(eirBlock(), Kind.WORD, result));
        addInstruction(new CMP_I64(eirBlock(), a, b));
        //The result of the comparison has to be interpreted as unsigned
        addInstruction(new MOVCS(eirBlock(), ICCOperand.XCC, result, createEirConstant(IntValue.ONE)));
    }

    @Override
    public void visitGreaterEqual(GreaterEqual builtin, DirValue dirResult, DirValue[] dirArguments) {
        final EirValue result = dirToEirValue(dirResult);
        final EirValue a = dirToEirValue(dirArguments[0]);
        final EirValue b = dirToEirValue(dirArguments[1]);

        addInstruction(new ZERO(eirBlock(), Kind.WORD, result));
        addInstruction(new CMP_I64(eirBlock(), a, b));
        //The result of the comparison has to be interpreted as unsigned
        addInstruction(new MOVCC(eirBlock(), ICCOperand.XCC, result, createEirConstant(IntValue.ONE)));
    }

    @Override
    public void visitGreaterThan(GreaterThan builtin, DirValue dirResult, DirValue[] dirArguments) {
        final EirValue result = dirToEirValue(dirResult);
        final EirValue a = dirToEirValue(dirArguments[0]);
        final EirValue b = dirToEirValue(dirArguments[1]);

        addInstruction(new ZERO(eirBlock(), Kind.WORD, result));
        addInstruction(new CMP_I64(eirBlock(), a, b));
        //The result of the comparison has to be interpreted as unsigned
        addInstruction(new MOVGU(eirBlock(), ICCOperand.XCC, result, createEirConstant(IntValue.ONE)));
    }

    @Override
    public void visitDividedByAddress(DividedByAddress builtin, DirValue dirResult, DirValue[] dirArguments) {
        new Binary(Kind.LONG, dirResult, dirArguments) {
            @Override
            protected SPARCEirOperation createOperation(EirValue destination, EirValue source, EirValue divisor) {
                return new DIV_I64(eirBlock(), destination, source, divisor);
            }
        };
    }

    @Override
    public void visitDividedByInt(DividedByInt builtin, DirValue dirResult, DirValue[] dirArguments) {
        // TODO: use different selection is divisor is power of two.
        final EirValue result = dirToEirValue(dirResult);
        final EirValue source = dirToEirValue(dirArguments[0]);
        final EirValue divisor = dirToEirValue(dirArguments[1]);
        final EirVariable zeroExtendedDivisor = createEirVariable(Kind.LONG);
        addInstruction(new SRA_I64(eirBlock(), zeroExtendedDivisor, divisor, createEirConstant(IntValue.ZERO)));
        addInstruction(new DIV_I64(eirBlock(), result, source, divisor));
    }

    @Override
    public void visitRemainderByInt(RemainderByInt builtin, DirValue dirResult, DirValue[] dirArguments) {
        // TODO: use different selection is divisor is power of two.
        final EirValue result = dirToEirValue(dirResult);
        final EirValue source = dirToEirValue(dirArguments[0]);
        final EirValue divisor = dirToEirValue(dirArguments[1]);
        final EirVariable zeroExtendedDivisor = createEirVariable(Kind.LONG);
        addInstruction(new SRA_I64(eirBlock(), zeroExtendedDivisor, divisor, createEirConstant(IntValue.ZERO)));
        addInstruction(new DIV_I64(eirBlock(), result, source, zeroExtendedDivisor));
        addInstruction(new MUL_I64(eirBlock(), result, zeroExtendedDivisor));
        addInstruction(new SUB_I64(eirBlock(), result, source, result));
        addInstruction(new SRA_I32(eirBlock(), result, createEirConstant(IntValue.ZERO))); // FIXME: is this conversion back to int necessary ?
    }

    @Override
    public void visitIntRemainder(IntRemainder builtin,  DirValue dirResult, DirValue[] dirArguments) {
        // TODO: use different selection is divisor is power of two.
        final EirValue result = dirToEirValue(dirResult);
        final EirValue source = dirToEirValue(dirArguments[0]);
        final EirValue divisor = dirToEirValue(dirArguments[1]);
        addInstruction(new DIV_I32(eirBlock(), result, source, divisor));
        addInstruction(new MUL_I32(eirBlock(), result, divisor));
        addInstruction(new SUB_I32(eirBlock(), result, source, result));
    }

    private void longRemainder(DirValue dirResult, DirValue[] dirArguments) {
        // TODO: use different selection is divisor is power of two.
        final EirValue result = dirToEirValue(dirResult);
        final EirValue source = dirToEirValue(dirArguments[0]);
        final EirValue divisor = dirToEirValue(dirArguments[1]);
        addInstruction(new DIV_I64(eirBlock(), result, source, divisor));
        addInstruction(new MUL_I64(eirBlock(), result, divisor));
        addInstruction(new SUB_I64(eirBlock(), result, source, result));
    }

    @Override
    public void visitLongRemainder(LongRemainder builtin,  DirValue dirResult, DirValue[] dirArguments) {
        longRemainder(dirResult, dirArguments);
    }

    @Override
    public void visitRemainderByAddress(RemainderByAddress builtin, DirValue dirResult, DirValue[] dirArguments) {
        longRemainder(dirResult, dirArguments);
    }

    private void read(Kind kind, final Kind offsetKind, DirValue dirResult, DirValue[] dirArguments) {
        final EirValue result = dirToEirValue(dirResult);
        final EirValue pointer = dirToEirValue(dirArguments[0]);
        final DirValue dirOffset = dirArguments[1];
        final SPARCEirLoad loadInstruction = dirOffset.isZeroConstant() ? new SPARCEirLoad(eirBlock(), kind, result, pointer) :
            new SPARCEirLoad(eirBlock(), kind, result, pointer, offsetKind, dirToEirValue(dirOffset));
        addInstruction(loadInstruction);
    }

    private void get(Kind kind, DirValue dirResult, DirValue[] dirArguments) {
        final EirValue result = dirToEirValue(dirResult);
        final EirValue pointer = dirToEirValue(dirArguments[0]);

        final DirValue dirDisplacement = dirArguments[1];
        final DirValue dirIndex = dirArguments[2];

        if (dirIndex.isConstant() && dirDisplacement.isConstant()) {
            final DirConstant indexConstant = (DirConstant) dirIndex;
            final DirConstant displacementConstant = (DirConstant) dirDisplacement;

            final long offset = (indexConstant.value().toInt() * kind.width.numberOfBytes) + displacementConstant.value().toInt();
            if (offset > Integer.MIN_VALUE && offset < Integer.MAX_VALUE) {
                if (offset == 0) {
                    addInstruction(new SPARCEirLoad(eirBlock(), kind, result, pointer));
                } else {
                    addInstruction(new SPARCEirLoad(eirBlock(), kind, result, pointer, Kind.INT, createEirConstant(IntValue.from((int) offset))));
                }
                return;
            }
            addInstruction(new SPARCEirLoad(eirBlock(), kind, result, pointer, Kind.LONG, createEirConstant(LongValue.from(offset))));
        }

        if (dirDisplacement.isConstant()) {
            if (dirDisplacement.value().isZero()) {
                addInstruction(new SPARCEirLoad(eirBlock(), kind, result, pointer, dirToEirValue(dirIndex)));
            } else {
                if (SPARCEirOperation.isSimm13(dirDisplacement.value().asInt())) {
                    addInstruction(new SPARCEirLoad(eirBlock(), kind, result, pointer,  Kind.INT,  createEirConstant(dirDisplacement.value()), dirToEirValue(dirIndex)));
                } else {
                    // Needs an extra register variable.
                    final EirVariable offset = createEirVariable(Kind.INT);
                    addInstruction(new SET_I32(eirBlock(), offset, createEirConstant(dirDisplacement.value())));
                    addInstruction(new SPARCEirLoad(eirBlock(), kind, result, pointer,  Kind.INT, offset, dirToEirValue(dirIndex)));
                }
            }
            return;
        } else if (dirIndex.isConstant()) {
            final DirConstant indexConstant = (DirConstant) dirIndex;
            if (indexConstant.value().isZero()) {
                addInstruction(new SPARCEirLoad(eirBlock(), kind, result, pointer, kind, dirToEirValue(dirDisplacement)));
                return;
            }
        }
        addInstruction(new SPARCEirLoad(eirBlock(), kind, result, pointer, kind, dirToEirValue(dirDisplacement), dirToEirValue(dirIndex)));
    }

    @Override
    public void visitReadByte(ReadByte builtin, DirValue dirResult, DirValue[] dirArguments) {
        read(Kind.BYTE, Kind.LONG, dirResult, dirArguments);
    }

    @Override
    public void visitReadByteAtIntOffset(ReadByteAtIntOffset builtin, DirValue dirResult, DirValue[] dirArguments) {
        read(Kind.BYTE, Kind.INT, dirResult, dirArguments);
    }
    @Override
    public void visitGetByte(GetByte builtin, DirValue dirResult, DirValue[] dirArguments) {
        get(Kind.BYTE, dirResult, dirArguments);
    }

    @Override
    public void visitReadShort(ReadShort builtin, DirValue dirResult, DirValue[] dirArguments) {
        read(Kind.SHORT, Kind.LONG, dirResult, dirArguments);
    }

    @Override
    public void visitReadShortAtIntOffset(ReadShortAtIntOffset builtin, DirValue dirResult, DirValue[] dirArguments) {
        read(Kind.SHORT, Kind.INT, dirResult, dirArguments);
    }

    @Override
    public void visitGetShort(GetShort builtin, DirValue dirResult, DirValue[] dirArguments) {
        get(Kind.SHORT, dirResult, dirArguments);
    }

    @Override
    public void visitReadChar(ReadChar builtin, DirValue dirResult, DirValue[] dirArguments) {
        read(Kind.CHAR, Kind.LONG, dirResult, dirArguments);
    }

    @Override
    public void visitReadCharAtIntOffset(ReadCharAtIntOffset builtin, DirValue dirResult, DirValue[] dirArguments) {
        read(Kind.CHAR, Kind.INT, dirResult, dirArguments);
    }

    @Override
    public void visitGetChar(GetChar builtin, DirValue dirResult, DirValue[] dirArguments) {
        get(Kind.CHAR, dirResult, dirArguments);
    }

    @Override
    public void visitReadInt(ReadInt builtin, DirValue dirResult, DirValue[] dirArguments) {
        read(Kind.INT, Kind.LONG, dirResult, dirArguments);
    }

    @Override
    public void visitReadIntAtIntOffset(ReadIntAtIntOffset builtin, DirValue dirResult, DirValue[] dirArguments) {
        read(Kind.INT, Kind.INT, dirResult, dirArguments);
    }

    @Override
    public void visitGetInt(GetInt builtin, DirValue dirResult, DirValue[] dirArguments) {
        get(Kind.INT, dirResult, dirArguments);
    }

    @Override
    public void visitReadFloat(ReadFloat builtin, DirValue dirResult, DirValue[] dirArguments) {
        read(Kind.FLOAT, Kind.LONG, dirResult, dirArguments);
    }

    @Override
    public void visitReadFloatAtIntOffset(ReadFloatAtIntOffset builtin, DirValue dirResult, DirValue[] dirArguments) {
        read(Kind.FLOAT, Kind.INT, dirResult, dirArguments);
    }

    @Override
    public void visitGetFloat(GetFloat builtin, DirValue dirResult, DirValue[] dirArguments) {
        get(Kind.FLOAT, dirResult, dirArguments);
    }

    @Override
    public void visitReadLong(ReadLong builtin, DirValue dirResult, DirValue[] dirArguments) {
        read(Kind.LONG, Kind.LONG, dirResult, dirArguments);
    }

    @Override
    public void visitReadLongAtIntOffset(ReadLongAtIntOffset builtin, DirValue dirResult, DirValue[] dirArguments) {
        read(Kind.LONG, Kind.INT, dirResult, dirArguments);
    }

    @Override
    public void visitGetLong(GetLong builtin, DirValue dirResult, DirValue[] dirArguments) {
        get(Kind.LONG, dirResult, dirArguments);
    }

    @Override
    public void visitReadDouble(ReadDouble builtin, DirValue dirResult, DirValue[] dirArguments) {
        read(Kind.DOUBLE, Kind.LONG, dirResult, dirArguments);
    }

    @Override
    public void visitReadDoubleAtIntOffset(ReadDoubleAtIntOffset builtin, DirValue dirResult, DirValue[] dirArguments) {
        read(Kind.DOUBLE, Kind.INT, dirResult, dirArguments);
    }

    @Override
    public void visitGetDouble(GetDouble builtin, DirValue dirResult, DirValue[] dirArguments) {
        get(Kind.DOUBLE, dirResult, dirArguments);
    }

    @Override
    public void visitReadWord(ReadWord builtin, DirValue dirResult, DirValue[] dirArguments) {
        read(Kind.WORD, Kind.LONG, dirResult, dirArguments);
    }

    @Override
    public void visitReadWordAtIntOffset(ReadWordAtIntOffset builtin, DirValue dirResult, DirValue[] dirArguments) {
        read(Kind.WORD, Kind.INT, dirResult, dirArguments);
    }

    @Override
    public void visitGetWord(GetWord builtin, DirValue dirResult, DirValue[] dirArguments) {
        get(Kind.WORD, dirResult, dirArguments);
    }

    @Override
    public void visitReadReference(ReadReference builtin, DirValue dirResult, DirValue[] dirArguments) {
        read(Kind.REFERENCE, Kind.LONG, dirResult, dirArguments);
    }

    @Override
    public void visitReadReferenceAtIntOffset(ReadReferenceAtIntOffset builtin, DirValue dirResult, DirValue[] dirArguments) {
        read(Kind.REFERENCE, Kind.INT, dirResult, dirArguments);
    }

    @Override
    public void visitGetReference(GetReference builtin, DirValue dirResult, DirValue[] dirArguments) {
        get(Kind.REFERENCE, dirResult, dirArguments);
    }

    private void write(Kind kind, Kind offsetKind, DirValue[] dirArguments) {
        final EirValue pointer = dirToEirValue(dirArguments[0]);
        final DirValue dirOffset = dirArguments[1];
        final EirValue value = dirToEirValue(dirArguments[2]);

        final SPARCEirStore loadInstruction = dirOffset.isZeroConstant() ? new SPARCEirStore(eirBlock(), kind, value, pointer) :
            new SPARCEirStore(eirBlock(), kind, value, pointer, offsetKind, dirToEirValue(dirOffset));
        addInstruction(loadInstruction);

    }
    private void set(Kind kind, DirValue[] dirArguments) {
        final EirValue pointer = dirToEirValue(dirArguments[0]);
        final DirValue dirDisplacement = dirArguments[1];
        final DirValue dirIndex = dirArguments[2];
        final EirValue value = dirToEirValue(dirArguments[3]);

        if (dirIndex.isConstant() && dirDisplacement.isConstant()) {
            final DirConstant indexConstant = (DirConstant) dirIndex;
            final DirConstant displacementConstant = (DirConstant) dirDisplacement;

            final long offset = (indexConstant.value().toInt() * kind.width.numberOfBytes) + displacementConstant.value().toInt();

            if (offset > Integer.MIN_VALUE && offset < Integer.MAX_VALUE) {
                if (offset == 0) {
                    addInstruction(new SPARCEirStore(eirBlock(), kind, value, pointer));
                } else {
                    addInstruction(new SPARCEirStore(eirBlock(), kind, value, pointer, Kind.INT, createEirConstant(IntValue.from((int) offset))));
                }
                return;
            }
            addInstruction(new SPARCEirStore(eirBlock(), kind, value, pointer, Kind.LONG, createEirConstant(LongValue.from(offset))));
        }

        if (dirDisplacement.isConstant()) {
            if (dirDisplacement.value().isZero()) {
                addInstruction(new SPARCEirStore(eirBlock(), kind, value, pointer, dirToEirValue(dirIndex)));
            } else {
                if (SPARCEirOperation.isSimm13(dirDisplacement.value().asInt())) {
                    addInstruction(new SPARCEirStore(eirBlock(), kind, value, pointer,  Kind.INT,  createEirConstant(dirDisplacement.value()), dirToEirValue(dirIndex)));
                } else {
                    // Needs an extra register variable.
                    final EirVariable offset = createEirVariable(Kind.INT);
                    addInstruction(new SET_I32(eirBlock(), offset, createEirConstant(dirDisplacement.value())));
                    addInstruction(new SPARCEirStore(eirBlock(), kind, value, pointer,  Kind.INT, offset, dirToEirValue(dirIndex)));
                }
            }
            return;
        } else if (dirIndex.isConstant()) {
            final DirConstant indexConstant = (DirConstant) dirIndex;
            if (indexConstant.value().isZero()) {
                addInstruction(new SPARCEirStore(eirBlock(), kind, value, pointer, kind, dirToEirValue(dirDisplacement)));
                return;
            }
        }
        addInstruction(new SPARCEirStore(eirBlock(), kind, value, pointer, kind, dirToEirValue(dirDisplacement), dirToEirValue(dirIndex)));
    }

    @Override
    public void visitWriteByte(WriteByte builtin, DirValue dirResult, DirValue[] dirArguments) {
        write(Kind.BYTE, Kind.LONG, dirArguments);
    }

    @Override
    public void visitWriteByteAtIntOffset(WriteByteAtIntOffset builtin, DirValue dirResult, DirValue[] dirArguments) {
        write(Kind.BYTE, Kind.INT, dirArguments);
    }

    @Override
    public void visitSetByte(SetByte builtin, DirValue dirResult, DirValue[] dirArguments) {
        set(Kind.BYTE, dirArguments);
    }

    @Override
    public void visitWriteShort(WriteShort builtin, DirValue dirResult, DirValue[] dirArguments) {
        write(Kind.SHORT, Kind.LONG, dirArguments);
    }

    @Override
    public void visitWriteShortAtIntOffset(WriteShortAtIntOffset builtin, DirValue dirResult, DirValue[] dirArguments) {
        write(Kind.SHORT, Kind.INT, dirArguments);
    }

    @Override
    public void visitSetShort(SetShort builtin, DirValue dirResult, DirValue[] dirArguments) {
        set(Kind.SHORT, dirArguments);
    }

    @Override
    public void visitWriteInt(WriteInt builtin, DirValue dirResult, DirValue[] dirArguments) {
        write(Kind.INT, Kind.LONG, dirArguments);
    }

    @Override
    public void visitWriteIntAtIntOffset(WriteIntAtIntOffset builtin, DirValue dirResult, DirValue[] dirArguments) {
        write(Kind.INT, Kind.INT, dirArguments);
    }

    @Override
    public void visitSetInt(SetInt builtin, DirValue dirResult, DirValue[] dirArguments) {
        set(Kind.INT, dirArguments);
    }

    @Override
    public void visitWriteFloat(WriteFloat builtin, DirValue dirResult, DirValue[] dirArguments) {
        write(Kind.FLOAT, Kind.LONG, dirArguments);
    }

    @Override
    public void visitWriteFloatAtIntOffset(WriteFloatAtIntOffset builtin, DirValue dirResult, DirValue[] dirArguments) {
        write(Kind.FLOAT, Kind.INT, dirArguments);
    }

    @Override
    public void visitSetFloat(SetFloat builtin, DirValue dirResult, DirValue[] dirArguments) {
        set(Kind.FLOAT, dirArguments);
    }

    @Override
    public void visitWriteLong(WriteLong builtin, DirValue dirResult, DirValue[] dirArguments) {
        write(Kind.LONG, Kind.LONG, dirArguments);
    }

    @Override
    public void visitWriteLongAtIntOffset(WriteLongAtIntOffset builtin, DirValue dirResult, DirValue[] dirArguments) {
        write(Kind.LONG, Kind.INT, dirArguments);
    }

    @Override
    public void visitSetLong(SetLong builtin, DirValue dirResult, DirValue[] dirArguments) {
        set(Kind.LONG, dirArguments);
    }

    @Override
    public void visitWriteDouble(WriteDouble builtin, DirValue dirResult, DirValue[] dirArguments) {
        write(Kind.DOUBLE, Kind.LONG, dirArguments);
    }

    @Override
    public void visitWriteDoubleAtIntOffset(WriteDoubleAtIntOffset builtin, DirValue dirResult, DirValue[] dirArguments) {
        write(Kind.DOUBLE, Kind.INT, dirArguments);
    }

    @Override
    public void visitSetDouble(SetDouble builtin, DirValue dirResult, DirValue[] dirArguments) {
        set(Kind.DOUBLE, dirArguments);
    }

    @Override
    public void visitWriteWord(WriteWord builtin, DirValue dirResult, DirValue[] dirArguments) {
        write(Kind.WORD, Kind.LONG, dirArguments);
    }

    @Override
    public void visitWriteWordAtIntOffset(WriteWordAtIntOffset builtin, DirValue dirResult, DirValue[] dirArguments) {
        write(Kind.WORD, Kind.INT, dirArguments);
    }

    @Override
    public void visitSetWord(SetWord builtin, DirValue dirResult, DirValue[] dirArguments) {
        set(Kind.WORD, dirArguments);
    }

    @Override
    public void visitWriteReference(WriteReference builtin, DirValue dirResult, DirValue[] dirArguments) {
        write(Kind.REFERENCE, Kind.LONG, dirArguments);
    }

    @Override
    public void visitWriteReferenceAtIntOffset(WriteReferenceAtIntOffset builtin, DirValue dirResult, DirValue[] dirArguments) {
        write(Kind.REFERENCE, Kind.INT, dirArguments);
    }

    @Override
    public void visitSetReference(SetReference builtin, DirValue dirResult, DirValue[] dirArguments) {
        set(Kind.REFERENCE, dirArguments);
    }

    private void compareAndSwapAtOffset(Kind kind, Kind offsetKind, DirValue dirResult, DirValue[] dirArguments) {
        final EirValue result = dirToEirValue(dirResult);
        final EirValue pointer = dirToEirValue(dirArguments[0]);
        final DirValue dirOffset = dirArguments[1];
        final EirValue expectedValue = dirToEirValue(dirArguments[2]);
        final EirValue newValue = dirToEirValue(dirArguments[3]);

        final EirVariable exchangedValue = createEirVariable(kind);
        assign(kind, exchangedValue, newValue);
        if (dirOffset.isZeroConstant()) {
            addInstruction(new SPARCEirCompareAndSwap(eirBlock(), kind, exchangedValue, pointer, expectedValue));
        } else {
            final EirVariable p = createEirVariable(pointer.kind());
            addInstruction(new ADD_I64(eirBlock(), p, pointer, dirToEirValue(dirOffset)));
            addInstruction(new SPARCEirCompareAndSwap(eirBlock(), kind, exchangedValue, p, expectedValue));
        }
        assign(kind, result, exchangedValue);
    }

    @Override
    public void visitCompareAndSwapInt(CompareAndSwapInt builtin, DirValue dirResult, DirValue[] dirArguments) {
        compareAndSwapAtOffset(Kind.INT, Kind.LONG, dirResult, dirArguments);
    }

    @Override
    public void visitCompareAndSwapIntAtIntOffset(CompareAndSwapIntAtIntOffset builtin, DirValue dirResult, DirValue[] dirArguments) {
        compareAndSwapAtOffset(Kind.INT, Kind.INT, dirResult, dirArguments);
    }

    @Override
    public void visitCompareAndSwapWord(CompareAndSwapWord builtin, DirValue dirResult, DirValue[] dirArguments) {
        compareAndSwapAtOffset(Kind.WORD, Kind.LONG, dirResult, dirArguments);
    }

    @Override
    public void visitCompareAndSwapWordAtIntOffset(CompareAndSwapWordAtIntOffset builtin, DirValue dirResult, DirValue[] dirArguments) {
        compareAndSwapAtOffset(Kind.WORD, Kind.INT, dirResult, dirArguments);
    }

    @Override
    public void visitCompareAndSwapReference(CompareAndSwapReference builtin, DirValue dirResult, DirValue[] dirArguments) {
        compareAndSwapAtOffset(Kind.REFERENCE, Kind.LONG, dirResult, dirArguments);
    }

    @Override
    public void visitCompareAndSwapReferenceAtIntOffset(CompareAndSwapReferenceAtIntOffset builtin, DirValue dirResult, DirValue[] dirArguments) {
        compareAndSwapAtOffset(Kind.REFERENCE, Kind.INT, dirResult, dirArguments);
    }

    @Override
    public void visitGetIntegerRegister(GetIntegerRegister builtin, DirValue dirResult, DirValue[] dirArguments) {
        assert dirArguments.length == 1 && dirArguments[0].isConstant() && dirArguments[0].value().asObject() instanceof VMRegister.Role;
        final EirValue result = dirToEirValue(dirResult);
        final VMRegister.Role registerRole = (VMRegister.Role) dirArguments[0].value().asObject();
        assign(Kind.LONG, result, methodTranslation().integerRegisterRoleValue(registerRole));
    }

    @Override
    public void visitSetIntegerRegister(SetIntegerRegister builtin, DirValue dirResult, DirValue[] dirArguments) {
        assert dirArguments.length == 2 && dirArguments[0].isConstant() && dirArguments[0].value().asObject() instanceof VMRegister.Role;
        final VMRegister.Role registerRole = (VMRegister.Role) dirArguments[0].value().asObject();
        final EirValue value = dirToEirValue(dirArguments[1]);
        assign(Kind.LONG, methodTranslation().integerRegisterRoleValue(registerRole), value);
    }

    @Override
    public void visitAdjustJitStack(AdjustJitStack builtin, DirValue dirResult, DirValue[] dirArguments) {
        assert dirArguments.length == 1;
        final EirValue registerPointerValue = methodTranslation().integerRegisterRoleValue(VMRegister.Role.ABI_STACK_POINTER);
        final EirValue delta = dirToEirValue(dirArguments[0]);
        if (delta.isConstant()) {
            final EirConstant constant = (EirConstant) delta;
            int addend = constant.value().asInt();
            if (addend == 0) {
                return;
            }
            addend <<= 3; // translate delta in number of bytes -- word size = 8 bytes.
            if (addend < 0) {
                addInstruction(new SPARCEirInstruction.SUB_I64(eirBlock(), registerPointerValue, methodTranslation().createEirConstant(IntValue.from(-addend))));
            } else {
                addInstruction(new SPARCEirInstruction.ADD_I64(eirBlock(), registerPointerValue, methodTranslation().createEirConstant(IntValue.from(addend))));
            }
            return;
        }
        // translate delta in number of bytes -- word size = 8 bytes.
        addInstruction(new SPARCEirInstruction.SLL_I64(eirBlock(), delta, methodTranslation().createEirConstant(IntValue.from(3))));
        addInstruction(new SPARCEirInstruction.ADD_I64(eirBlock(), registerPointerValue, delta));
    }

    private static final LongValue STACK_SLOT_SIZE = LongValue.from(Word.size());

    @Override
    public void visitGetInstructionPointer(GetInstructionPointer builtin, DirValue dirResult, DirValue[] dirArguments) {
        final EirValue result = dirToEirValue(dirResult);
        final EirVariable destination = createEirVariable(Kind.LONG);
        addInstruction(new RDPC(eirBlock(), destination));
        assign(Kind.LONG, result, destination);
    }
}
