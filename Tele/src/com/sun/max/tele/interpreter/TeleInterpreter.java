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
package com.sun.max.tele.interpreter;

import static com.sun.cri.bytecode.Bytecodes.*;
import static com.sun.cri.bytecode.Bytecodes.UnsignedComparisons.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import com.sun.cri.bytecode.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;
import com.sun.max.tele.*;
import com.sun.max.tele.reference.*;
import com.sun.max.tele.value.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.bytecode.*;
import com.sun.max.vm.classfile.constant.*;
import com.sun.max.vm.cps.ir.*;
import com.sun.max.vm.cps.ir.interpreter.*;
import com.sun.max.vm.hosted.*;
import com.sun.max.vm.layout.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.type.*;
import com.sun.max.vm.value.*;

/**
 * Entry points and utility functions for the interpreter.
 *
 * @author Athul Acharya
 * @author Doug Simon
 */
public final class TeleInterpreter extends IrInterpreter<ActorIrMethod> {

    private final TeleVM teleVM;

    private Machine machine;
    private Value returnValue;
    private int instructionsExecuted;

    public TeleInterpreter(TeleVM teleVM) {
        this.teleVM = teleVM;
    }

    @Override
    public Value execute(IrMethod method, Value... arguments) throws InvocationTargetException {
        return run(method.classMethodActor(), arguments);
    }

    /**
     * Creates an interpreter instance and uses it to execute a given method with the given arguments.
     * Note that arguments must be dynamic types seen by the JavaPrototyper as legitimate VM classes.
     *
     * @param teleVM the remote VM
     * @param classMethodActor the method to be executed
     * @param args the arguments to passed to the method for execution
     * @return the result of the execution
     * @throws TeleInterpreterException if an uncaught exception occurs during execution of the method
     */
    public static Value execute(TeleVM teleVM, ClassMethodActor classMethodActor, Value... args) throws TeleInterpreterException {
        return new TeleInterpreter(teleVM).run(classMethodActor, args);
    }

    /**
     * Creates an interpreter instance and uses it to execute a given method with the given arguments.
     *
     * @param teleVM the remote VM
     * @param declaringClassName the name of the class that declares the method to be executed
     * @param name the name of the method to be executed
     * @param signature the signature of the method to be executed
     * @param args the arguments to passed to the method for execution
     * @return the result of the execution
     * @throws TeleInterpreterException if an uncaught exception occurs during execution of the method
     * @throws NoSuchMethodError if the specified method cannot be found
     */
    public static Value execute(TeleVM teleVM, String declaringClassName, String name, SignatureDescriptor signature, Value... args) throws TeleInterpreterException {
        ClassActor classActor;
        ClassMethodActor classMethodActor;

        classActor = HostedBootClassLoader.HOSTED_BOOT_CLASS_LOADER.mustMakeClassActor(JavaTypeDescriptor.getDescriptorForJavaString(declaringClassName));
        classMethodActor = classActor.findClassMethodActor(SymbolTable.makeSymbol(name), signature);

        if (classMethodActor == null) {
            throw new NoSuchMethodError(declaringClassName + "." + name + signature);
        }

        return execute(teleVM, classMethodActor, args);
    }

    /**
     * Creates an interpreter instance and uses it to execute a given method with the given arguments.
     *
     * @param teleVM the remote VM
     * @param declaringClass the class that declares the method to be executed
     * @param name the name of the method to be executed
     * @param signature the signature of the method to be executed
     * @param args the arguments to passed to the method for execution
     * @return the result of the execution
     * @throws TeleInterpreterException if an uncaught exception occurs during execution of the method
     */
    public static Value execute(TeleVM teleVM, Class declaringClass, String name, SignatureDescriptor signature, Value... args) throws TeleInterpreterException {
        ClassActor classActor;
        ClassMethodActor classMethodActor;

        classActor = HostedBootClassLoader.HOSTED_BOOT_CLASS_LOADER.mustMakeClassActor(JavaTypeDescriptor.forJavaClass(declaringClass));
        classMethodActor = classActor.findClassMethodActor(SymbolTable.makeSymbol(name), signature);

        if (classMethodActor == null) {
            throw new NoSuchMethodError(declaringClass.getName() + "." + name + signature);
        }

        return execute(teleVM, classMethodActor, args);
    }

    /**
     * A lazy constructed cache of mappings from opcode positions to disassembled bytecode instructions.
     */
    private Map<MethodActor, Map<Integer, String>> bytecodeTraces;

    /**
     * The last frame traced in {@link #traceExecution()}.
     */
    private ExecutionFrame lastTracedFrame;

    /**
     * Traces the current execution point.
     */
    private void traceExecution() {
        if (Trace.hasLevel(2)) {
            PrintStream stream = Trace.stream();
            ExecutionFrame frame = machine.currentThread().frame();

            int depth = frame.depth();
            if (lastTracedFrame == null) {
                stream.println("Interpreter: " + Strings.spaces(depth * 2) + "ENTERING: " + frame.method().format("%H.%n(%p)"));
            } else if (lastTracedFrame != frame) {
                int lastFrameDepth = lastTracedFrame.depth();
                if (lastFrameDepth < depth) {
                    stream.println("Interpreter: " + Strings.spaces(depth * 2) + "ENTERING: " + frame.method().format("%H.%n(%p)"));
                } else {
                    stream.println("Interpreter: " + Strings.spaces(lastFrameDepth * 2) + "EXITING: " + lastTracedFrame.method().format("%H.%n(%p)"));
                }
            }
            if (Trace.hasLevel(3)) {
                if (bytecodeTraces == null) {
                    bytecodeTraces = new HashMap<MethodActor, Map<Integer, String>>();
                }
                Map<Integer, String> bcpToTrace = bytecodeTraces.get(machine.currentMethod());
                if (bcpToTrace == null) {
                    bcpToTrace = new HashMap<Integer, String>();
                    bytecodeTraces.put(machine.currentMethod(), bcpToTrace);
                    ConstantPool constantPool = frame.constantPool();
                    BytecodeBlock bytecodeBlock = new BytecodeBlock(frame.code());
                    String[] instructions = BytecodePrinter.toString(constantPool, bytecodeBlock, "", "\0", 0).split("\0");
                    for (String instruction : instructions) {
                        int colonIndex = instruction.indexOf(':');
                        assert colonIndex != -1 : "instruction trace does not start with expected '<bcp>:': " + instruction;
                        try {
                            int bcp = Integer.parseInt(instruction.substring(0, colonIndex));
                            bcpToTrace.put(bcp, instruction);
                        } catch (NumberFormatException numberFormatException) {
                            ProgramWarning.message("instruction trace does not start with expected '<bcp>:': " + instruction);
                        }
                    }
                }

                stream.println("Interpreter: " + Strings.spaces(depth * 2) + bcpToTrace.get(frame.currentOpcodePosition()));
            }
            lastTracedFrame = frame;
            stream.flush();
        }
    }

    private Value run(ClassMethodActor classMethodActor, Value... arguments) throws TeleInterpreterException {

        machine = new Machine(teleVM);
        machine.pushFrame(classMethodActor);
        int j = 0;
        for (int i = 0; i < arguments.length; i++, j++) {
            setLocal(j, arguments[i]);
            if (arguments[i].isCategory2()) {
                j++;
            }
        }

        int opcode;
        MethodStatus status;
        instructionsExecuted = 0;

        while (true) {
            opcode = machine.readOpcode();

            traceExecution();

            try {

                boolean isWide = false;
                if (opcode == WIDE) {
                    opcode = machine.readOpcode();
                    isWide = true;
                }

                status = interpret(opcode, isWide);

                if (status == MethodStatus.METHOD_END) {
                    break;
                }
            } catch (TeleInterpreterException executionException) {
                ReferenceValue throwableReference = executionException.throwableReference();
                boolean handled = machine.handleException(throwableReference); //if this succeeds we keep looping
                if (!handled) {
                    throw executionException;
                }
            } catch (Throwable throwable) {
                ReferenceValue throwableReference = ReferenceValue.from(throwable);
                boolean handled = machine.handleException(throwableReference); //if this succeeds we keep looping
                if (!handled) {
                    throw new TeleInterpreterException(throwable, machine);
                }
            } finally {
                instructionsExecuted++;
            }
        }

        if (returnValue instanceof TeleReferenceValue) {
            returnValue = TeleReferenceValue.from(teleVM, machine.makeLocalReference((TeleReference) returnValue.asReference()));
        }

        Kind resultKind = classMethodActor.resultKind();
        if (resultKind.stackKind == Kind.INT) {
            returnValue = resultKind.convert(returnValue);
        }
        return returnValue;
    }

    private Value pop() {
        return machine.pop();
    }

    private Value popCheckZero() throws TeleInterpreterException {
        Value value = machine.pop();
        if (value.isZero()) {
            machine.raiseException(new ArithmeticException("Division by zero"));
        }
        return value;
    }

    private void push(Value value) {
        machine.push(value);
    }

    private void jumpIf(boolean condition, int offset) {
        if (condition) {
            machine.jump(offset);
        }
    }

    private void push(int value) {
        push(IntValue.from(value));
    }

    private void setLocal(int index, Value value) {
        machine.setLocal(index, value);
    }

    private Value local(int index) {
        return machine.getLocal(index);
    }

    private short readS2() {
        return machine.readShort();
    }

    private byte readS1() {
        return machine.readByte();
    }

    private int readU2() {
        return machine.readShort() & 0xffff;
    }

    private int readU1() {
        return machine.readByte() & 0xff;
    }

    private void pointerLoad(Kind kind, boolean intOffset) throws TeleInterpreterException {
        Offset off = intOffset ? Offset.fromInt(pop().asInt()) : pop().asWord().asOffset();
        Pointer ptr = pop().asWord().asPointer();
        DataAccess dataAccess = teleVM.teleProcess().dataAccess();
        switch (kind.asEnum) {
            // Checkstyle: stop
            case BYTE:      push(IntValue.from(dataAccess.readByte(ptr, off))); break;
            case CHAR:      push(IntValue.from(dataAccess.readChar(ptr, off))); break;
            case SHORT:     push(IntValue.from(dataAccess.readShort(ptr, off))); break;
            case INT:       push(IntValue.from(dataAccess.readInt(ptr, off))); break;
            case LONG:      push(LongValue.from(dataAccess.readLong(ptr, off))); break;
            case FLOAT:     push(FloatValue.from(dataAccess.readFloat(ptr, off))); break;
            case DOUBLE:    push(DoubleValue.from(dataAccess.readDouble(ptr, off))); break;
            case WORD:      push(WordValue.from(dataAccess.readWord(ptr, off))); break;
            case REFERENCE: push(machine.toReferenceValue(teleVM.wordToReference(dataAccess.readWord(ptr, off)))); break;
            default:        machine.raiseException(new ClassFormatError("Invalid pointer load kind: " + kind));
            // Checkstyle: resume
        }
    }

    private void pointerGet(Kind kind) throws TeleInterpreterException {
        int index = pop().asInt();
        int disp = pop().asInt();
        Pointer ptr = pop().asWord().asPointer();
        DataAccess dataAccess = teleVM.teleProcess().dataAccess();
        switch (kind.asEnum) {
            // Checkstyle: stop
            case BYTE:      push(IntValue.from(dataAccess.getByte(ptr, disp, index))); break;
            case CHAR:      push(IntValue.from(dataAccess.getChar(ptr, disp, index))); break;
            case SHORT:     push(IntValue.from(dataAccess.getShort(ptr, disp, index))); break;
            case INT:       push(IntValue.from(dataAccess.getInt(ptr, disp, index))); break;
            case LONG:      push(LongValue.from(dataAccess.getLong(ptr, disp, index))); break;
            case FLOAT:     push(FloatValue.from(dataAccess.getFloat(ptr, disp, index))); break;
            case DOUBLE:    push(DoubleValue.from(dataAccess.getDouble(ptr, disp, index))); break;
            case WORD:      push(WordValue.from(dataAccess.getWord(ptr, disp, index))); break;
            case REFERENCE: push(machine.toReferenceValue(teleVM.wordToReference(dataAccess.getWord(ptr, disp, index)))); break;
            default:        machine.raiseException(new ClassFormatError("Invalid pointer load kind: " + kind));
            // Checkstyle: resume
        }
    }

    private void arrayLoad(Kind kind) throws TeleInterpreterException {
        int index = machine.pop().asInt();
        Reference array = machine.pop().asReference();

        if (array.isZero()) {
            machine.raiseException(new NullPointerException());
        }

        if (Layout.readArrayLength(array) <= index || index < 0) {
            machine.raiseException(new ArrayIndexOutOfBoundsException());
        }

        switch (kind.asEnum) {
            // Checkstyle: stop
            case BYTE:
                if (machine.toReferenceValue(array).getClassActor() == ClassRegistry.BOOLEAN_ARRAY) {
                    push(Layout.getBoolean(array, index) ? IntValue.ONE : IntValue.ZERO);
                } else {
                    push(IntValue.from(Layout.getByte(array, index)));
                }
                break;
            case CHAR:      push(IntValue.from(Layout.getChar(array, index))); break;
            case SHORT:     push(IntValue.from(Layout.getShort(array, index))); break;
            case INT:       push(IntValue.from(Layout.getInt(array, index))); break;
            case LONG:      push(LongValue.from(Layout.getLong(array, index))); break;
            case FLOAT:     push(FloatValue.from(Layout.getFloat(array, index))); break;
            case DOUBLE:    push(DoubleValue.from(Layout.getDouble(array, index))); break;
            case REFERENCE: push(machine.toReferenceValue(Layout.getReference(array, index))); break;
            default:        machine.raiseException(new ClassFormatError("Invalid array kind: " + kind));
            // Checkstyle: resume
        }
    }

    private void arrayStore(Kind kind) throws TeleInterpreterException {
        Value val = pop();
        int index = pop().toInt();
        Reference array = pop().asReference();

        if (array.isZero()) {
            machine.raiseException(new NullPointerException());
        }

        if (Layout.readArrayLength(array) <= index || index < 0) {
            machine.raiseException(new ArrayIndexOutOfBoundsException());
        }

        switch (kind.asEnum) {
            // Checkstyle: stop
            case BYTE:      Layout.setByte(array, index, val.toByte()); break;
            case CHAR:      Layout.setChar(array, index, val.toChar()); break;
            case SHORT:     Layout.setShort(array, index, val.toShort()); break;
            case INT:       Layout.setInt(array, index, val.toInt()); break;
            case LONG:      Layout.setLong(array, index, val.toLong()); break;
            case FLOAT:     Layout.setFloat(array, index, val.toFloat()); break;
            case DOUBLE:    Layout.setDouble(array, index, val.toDouble()); break;
            case REFERENCE: Layout.setReference(array, index, val.asReference()); break;
            default:        machine.raiseException(new ClassFormatError("Invalid array kind: " + kind));
            // Checkstyle: resume
        }
    }

    private int minus1IfWordWidth(int bitPosition) {
        return bitPosition == Word.widthValue().numberOfBits ? -1 : bitPosition;
    }

    private MethodStatus interpret(int opcode, boolean isWide) throws Throwable {
        switch (opcode) {
            // Checkstyle: stop
            case NOP:                break;

            case ACONST_NULL:        push(ReferenceValue.NULL); break;
            case ICONST_M1:          push(IntValue.from(-1));   break;
            case ICONST_0:           push(IntValue.ZERO); break;
            case ICONST_1:           push(IntValue.ONE); break;
            case ICONST_2:           push(IntValue.TWO); break;
            case ICONST_3:           push(IntValue.THREE); break;
            case ICONST_4:           push(IntValue.FOUR); break;
            case ICONST_5:           push(IntValue.FIVE); break;
            case LCONST_0:           push(LongValue.ZERO); break;
            case LCONST_1:           push(LongValue.ONE); break;
            case FCONST_0:           push(FloatValue.ZERO); break;
            case FCONST_1:           push(FloatValue.ONE); break;
            case FCONST_2:           push(FloatValue.TWO); break;
            case DCONST_0:           push(DoubleValue.ZERO); break;
            case DCONST_1:           push(DoubleValue.ONE); break;
            case BIPUSH:             push(IntValue.from(readS1())); break;
            case SIPUSH:             push(IntValue.from(readS2())); break;
            case LDC:                push(machine.resolveConstantReference(readU1())); break;
            case LDC_W:
            case LDC2_W:             push(machine.resolveConstantReference(readU2())); break;
            case ILOAD:
            case LLOAD:
            case FLOAD:
            case DLOAD:
            case WLOAD:
            case ALOAD:              push(local(isWide ? readU2() : readU1())); break;
            case ILOAD_0:
            case ILOAD_1:
            case ILOAD_2:
            case ILOAD_3:            push(local(opcode - ILOAD_0)); break;
            case LLOAD_0:
            case LLOAD_1:
            case LLOAD_2:
            case LLOAD_3:            push(local(opcode - LLOAD_0)); break;
            case FLOAD_0:
            case FLOAD_1:
            case FLOAD_2:
            case FLOAD_3:            push(local(opcode - FLOAD_0)); break;
            case DLOAD_0:
            case DLOAD_1:
            case DLOAD_2:
            case DLOAD_3:            push(local(opcode - DLOAD_0)); break;
            case ALOAD_0:
            case ALOAD_1:
            case ALOAD_2:
            case ALOAD_3:            push(local(opcode - ALOAD_0)); break;
            case WLOAD_0:
            case WLOAD_1:
            case WLOAD_2:
            case WLOAD_3:            push(local(opcode - WLOAD_0)); break;
            case IALOAD:             arrayLoad(Kind.INT); break;
            case LALOAD:             arrayLoad(Kind.LONG); break;
            case FALOAD:             arrayLoad(Kind.FLOAT); break;
            case DALOAD:             arrayLoad(Kind.DOUBLE); break;
            case AALOAD:             arrayLoad(Kind.REFERENCE); break;
            case BALOAD:             arrayLoad(Kind.BYTE); break;
            case CALOAD:             arrayLoad(Kind.CHAR); break;
            case SALOAD:             arrayLoad(Kind.SHORT); break;
            case ISTORE:
            case LSTORE:
            case FSTORE:
            case DSTORE:
            case WSTORE:
            case ASTORE:             setLocal(isWide ? readU2() : readU1(), pop()); break;
            case ISTORE_0:
            case ISTORE_1:
            case ISTORE_2:
            case ISTORE_3:           setLocal(opcode - ISTORE_0, pop()); break;
            case LSTORE_0:
            case LSTORE_1:
            case LSTORE_2:
            case LSTORE_3:           setLocal(opcode - LSTORE_0, pop()); break;
            case FSTORE_0:
            case FSTORE_1:
            case FSTORE_2:
            case FSTORE_3:           setLocal(opcode - FSTORE_0, pop()); break;
            case DSTORE_0:
            case DSTORE_1:
            case DSTORE_2:
            case DSTORE_3:           setLocal(opcode - DSTORE_0, pop()); break;
            case ASTORE_0:
            case ASTORE_1:
            case ASTORE_2:
            case ASTORE_3:           setLocal(opcode - ASTORE_0, pop()); break;
            case WSTORE_0:
            case WSTORE_1:
            case WSTORE_2:
            case WSTORE_3:           setLocal(opcode - WSTORE_0, pop()); break;
            case IASTORE:            arrayStore(Kind.INT); break;
            case LASTORE:            arrayStore(Kind.LONG); break;
            case FASTORE:            arrayStore(Kind.FLOAT); break;
            case DASTORE:            arrayStore(Kind.DOUBLE); break;
            case AASTORE:            arrayStore(Kind.REFERENCE); break;
            case BASTORE:            arrayStore(Kind.BYTE); break;
            case CASTORE:            arrayStore(Kind.CHAR); break;
            case SASTORE:            arrayStore(Kind.SHORT); break;
            case POP:                pop(); break;
            case POP2:               if (!pop().isCategory2()) pop(); break;
            case DUP:
                push(machine.peek());
                break;
            case DUP_X1: {
                Value s1 = pop();
                Value s2 = pop();

                push(s1);
                push(s2);
                push(s1);
                break;
            }

            case DUP_X2: {
                Value s1 = pop();
                Value s2 = pop();

                if (s2.isCategory2()) {
                    push(s1);
                    push(s2);
                    push(s1);
                } else {
                    Value s3 = pop();

                    push(s1);
                    push(s3);
                    push(s2);
                    push(s1);
                }
                break;
            }

            case DUP2: {
                Value s1 = pop();

                if (s1.isCategory2()) {
                    push(s1);
                    push(s1);
                } else {
                    Value s2 = pop();

                    push(s2);
                    push(s1);
                    push(s2);
                    push(s1);
                }
                break;
            }

            case DUP2_X1: {
                Value s1 = pop();

                if (s1.isCategory2()) {
                    Value s2 = pop();

                    push(s1);
                    push(s2);
                    push(s1);
                } else {
                    Value s2 = pop();
                    Value s3 = pop();

                    push(s2);
                    push(s1);
                    push(s3);
                    push(s2);
                    push(s1);
                }
                break;
            }

            case DUP2_X2: {
                Value s1 = pop();

                if (s1.isCategory2()) {
                    Value s2 = pop();

                    if (s2.isCategory2()) {
                        push(s1);
                        push(s2);
                        push(s1);
                    } else {
                        Value s3 = pop();

                        push(s1);
                        push(s3);
                        push(s2);
                        push(s1);
                    }
                } else {
                    Value s2 = pop();
                    Value s3 = pop();

                    if (s3.isCategory2()) {
                        push(s2);
                        push(s1);
                        push(s3);
                        push(s2);
                        push(s1);
                    } else {
                        Value s4 = pop();

                        push(s2);
                        push(s1);
                        push(s4);
                        push(s3);
                        push(s2);
                        push(s1);
                    }
                }
                break;
            }

            case SWAP: {
                Value s1 = pop();
                Value s2 = pop();

                push(s1);
                push(s2);
                break;
            }

            case IADD:               push(IntValue.from(pop().asInt() + pop().asInt())); break;
            case LADD:               push(LongValue.from(pop().asLong() + pop().asLong())); break;
            case FADD:               push(FloatValue.from(pop().asFloat() + pop().asFloat())); break;
            case DADD:               push(DoubleValue.from(pop().asDouble() + pop().asDouble())); break;

            case ISUB:               push(IntValue.from(-pop().asInt() + pop().asInt())); break;
            case LSUB:               push(LongValue.from(-pop().asLong() + pop().asLong())); break;
            case FSUB:               push(FloatValue.from(-pop().asFloat() + pop().asFloat())); break;
            case DSUB:               push(DoubleValue.from(-pop().asDouble() + pop().asDouble())); break;

            case IMUL:               push(IntValue.from(pop().asInt() * pop().asInt())); break;
            case LMUL:               push(LongValue.from(pop().asLong() * pop().asLong())); break;
            case FMUL:               push(FloatValue.from(pop().asFloat() * pop().asFloat())); break;
            case DMUL:               push(DoubleValue.from(pop().asDouble() * pop().asDouble())); break;

            case IDIV:             { int v2 = popCheckZero().asInt(); push(IntValue.from(pop().asInt() / v2)); break; }
            case LDIV:             { long v2 = popCheckZero().asLong(); push(LongValue.from(pop().asLong() / v2)); break; }
            case FDIV:             { float v2 = popCheckZero().asFloat(); push(FloatValue.from(pop().asFloat() / v2)); break; }
            case DDIV:             { double v2 = popCheckZero().asDouble(); push(DoubleValue.from(pop().asDouble() / v2)); break; }
            case WDIV: {
                machine.skipBytes(2);
                Address v2 = popCheckZero().asWord().asAddress();
                Address v1 = pop().asWord().asAddress();
                push(WordValue.from(v1.dividedBy(v2)));
                break;
            }
            case WDIVI: {
                machine.skipBytes(2);
                int v2 = popCheckZero().asInt();
                Address v1 = pop().asWord().asAddress();
                push(WordValue.from(v1.dividedBy(v2)));
                break;
            }
            case WREM: {
                machine.skipBytes(2);
                Address v2 = popCheckZero().asWord().asAddress();
                Address v1 = pop().asWord().asAddress();
                push(WordValue.from(v1.remainder(v2)));
                break;
            }
            case WREMI: {
                machine.skipBytes(2);
                int v2 = popCheckZero().asInt();
                Address v1 = pop().asWord().asAddress();
                push(IntValue.from(v1.remainder(v2)));
                break;
            }

            case IREM:             { int v2 = popCheckZero().asInt(); push(IntValue.from(pop().asInt() % v2)); break; }
            case LREM:             { long v2 = popCheckZero().asLong(); push(LongValue.from(pop().asLong() % v2)); break; }
            case FREM:             { float v2 = popCheckZero().asFloat(); push(FloatValue.from(pop().asFloat() % v2)); break; }
            case DREM:             { double v2 = popCheckZero().asDouble(); push(DoubleValue.from(pop().asDouble() % v2)); break; }

            case INEG:               push(IntValue.from(0 - pop().asInt())); break;
            case LNEG:               push(LongValue.from(0 - pop().asLong())); break;
            case FNEG:               push(FloatValue.from((float) 0.0 - pop().asFloat())); break;
            case DNEG:               push(DoubleValue.from(0.0 - pop().asDouble())); break;

            case ISHL:             { int amount = pop().asInt(); int value =  pop().asInt();  push(IntValue.from(value << (amount & 0x1F))); break; }
            case LSHL:             { int amount = pop().asInt(); long value = pop().asLong(); push(LongValue.from(value << (amount & 0x3F))); break; }
            case ISHR:             { int amount = pop().asInt(); int value =  pop().asInt();  push(IntValue.from(value >> (amount & 0x1F))); break; }
            case LSHR:             { int amount = pop().asInt(); long value = pop().asLong(); push(LongValue.from(value >> (amount & 0x3F))); break; }
            case IUSHR:            { int amount = pop().asInt(); int value  = pop().asInt();  push(IntValue.from(value >>> (amount & 0x1F))); break; }
            case LUSHR:            { int amount = pop().asInt(); long value = pop().asLong(); push(LongValue.from(value >>> (amount & 0x3F))); break; }

            case IAND:               push(IntValue.from(pop().asInt() & pop().asInt())); break;
            case LAND:               push(LongValue.from(pop().asLong() & pop().asLong())); break;
            case IOR:                push(IntValue.from(pop().asInt() | pop().asInt())); break;
            case LOR:                push(LongValue.from(pop().asLong() | pop().asLong())); break;
            case IXOR:               push(IntValue.from(pop().asInt() ^ pop().asInt())); break;
            case LXOR:               push(LongValue.from(pop().asLong() ^ pop().asLong())); break;

            case IINC: {
                int index     = isWide ? readU2() : readU1();
                int increment = isWide ? readS2() : readS1();
                int value     = local(index).asInt();
                setLocal(index, IntValue.from(value + increment));
                break;
            }

            case I2L:                 push(LongValue.from(pop().asInt())); break;
            case I2F:                 push(FloatValue.from(pop().asInt())); break;
            case I2D:                 push(DoubleValue.from(pop().asInt())); break;
            case L2I:                 push(IntValue.from((int) pop().asLong())); break;
            case L2F:                 push(FloatValue.from(pop().asLong())); break;
            case L2D:                 push(DoubleValue.from(pop().asLong())); break;
            case F2I:                 push(IntValue.from((int) pop().asFloat())); break;
            case F2L:                 push(LongValue.from((long) pop().asFloat())); break;
            case F2D:                 push(DoubleValue.from(pop().asFloat())); break;
            case D2I:                 push(IntValue.from((int) pop().asDouble())); break;
            case D2L:                 push(LongValue.from((long) pop().asDouble())); break;
            case D2F:                 push(FloatValue.from((float) pop().asDouble())); break;
            case I2B:                 push(IntValue.from(pop().toByte())); break;
            case I2C:                 push(IntValue.from(pop().toChar())); break;
            case I2S:                 push(IntValue.from(pop().toShort())); break;

            case LCMP: {
                long right  = pop().asLong();
                long left   = pop().asLong();
                int  result = (left < right) ? -1 : (left == right) ? 0 : 1;

                push(IntValue.from(result));
                break;
            }

            case FCMPL:
            case FCMPG: {
                float right  = pop().asFloat();
                float left   = pop().asFloat();
                int   result = (left < right) ? -1 : (left == right) ? 0 : 1;

                push(IntValue.from(result));
                break;
            }

            case DCMPL:
            case DCMPG: {
                double right  = pop().asDouble();
                double left   = pop().asDouble();
                int    result = (left < right) ? -1 : (left == right) ? 0 : 1;

                push(IntValue.from(result));
                break;
            }

            case IFEQ: {
                final short offset = readS2();
                final int s1 = machine.pop().asInt();
                if (s1 == 0) {
                    machine.jump(offset);
                }
                break;
            }

            case IFNE: {
                final short offset = readS2();
                final int s1 = machine.pop().asInt();
                if (s1 != 0) {
                    machine.jump(offset);
                }
                break;
            }

            case IFLT: {
                final short offset = readS2();
                final int s1 = machine.pop().asInt();
                if (s1 < 0) {
                    machine.jump(offset);
                }
                break;
            }

            case IFGE: {
                final short offset = readS2();
                final int s1 = machine.pop().asInt();
                if (s1 >= 0) {
                    machine.jump(offset);
                }
                break;
            }

            case IFGT: {
                final short offset = readS2();
                final int s1 = machine.pop().asInt();
                if (s1 > 0) {
                    machine.jump(offset);
                }
                break;
            }

            case IFLE: {
                final short offset = readS2();
                final int s1 = machine.pop().asInt();
                if (s1 <= 0) {
                    machine.jump(offset);
                }
                break;
            }

            case IF_ICMPEQ: {
                final short offset = readS2();
                final int s1 = machine.pop().asInt();
                final int s2 = machine.pop().asInt();
                if (s2 == s1) {
                    machine.jump(offset);
                }
                break;
            }

            case IF_ICMPNE: {
                final short offset = readS2();
                final int s1 = machine.pop().asInt();
                final int s2 = machine.pop().asInt();
                if (s2 != s1) {
                    machine.jump(offset);
                }
                break;
            }

            case IF_ICMPLT: {
                final short offset = readS2();
                final int s1 = machine.pop().asInt();
                final int s2 = machine.pop().asInt();
                if (s2 < s1) {
                    machine.jump(offset);
                }
                break;
            }

            case IF_ICMPGE: {
                final short offset = readS2();
                final int s1 = machine.pop().asInt();
                final int s2 = machine.pop().asInt();
                if (s2 >= s1) {
                    machine.jump(offset);
                }
                break;
            }

            case IF_ICMPGT: {
                final short offset = readS2();
                final int s1 = machine.pop().asInt();
                final int s2 = machine.pop().asInt();
                if (s2 > s1) {
                    machine.jump(offset);
                }
                break;
            }

            case IF_ICMPLE: {
                final short offset = readS2();
                final int s1 = machine.pop().asInt();
                final int s2 = machine.pop().asInt();
                if (s2 <= s1) {
                    machine.jump(offset);
                }
                break;
            }

            case IF_ACMPEQ: {
                final short offset = readS2();
                final ReferenceValue s1 = (ReferenceValue) machine.pop();
                final ReferenceValue s2 = (ReferenceValue) machine.pop();
                if (s2.equals(s1)) {
                    machine.jump(offset);
                }
                break;
            }

            case IF_ACMPNE: {
                final short offset = readS2();
                final ReferenceValue s1 = (ReferenceValue) machine.pop();
                final ReferenceValue s2 = (ReferenceValue) machine.pop();

                if (!s2.equals(s1)) {
                    machine.jump(offset);
                }
                break;
            }

            case GOTO: {
                final short offset = readS2();
                machine.jump(offset);
                break;
            }

            case JSR: {
                short offset = readS2();
                int returnPosition = machine.currentThread().frame().currentBytePosition();
                push(IntValue.from(returnPosition));
                machine.jump(offset);
                break;
            }
            case RET: {
                int index = isWide ? readU2() : readU1();
                int value = local(index).asInt();

                machine.currentThread().frame().setBytecodePosition(value);
                break;
            }

            case TABLESWITCH: {
                int index   = pop().asInt();
                machine.alignInstructionPosition();
                int defawlt = machine.readInt();
                int low     = machine.readInt();
                int high    = machine.readInt();

                if (index < low || index > high) {
                    machine.jump(defawlt);
                } else {
                    int jumpTableIndex = index - low;
                    machine.skipBytes(jumpTableIndex * 4);
                    int offset = machine.readInt();
                    machine.jump(offset);
                }

                break;
            }

            case LOOKUPSWITCH: {
                int key     = pop().asInt();
                machine.alignInstructionPosition();
                int defawlt = machine.readInt();
                int nPairs  = machine.readInt();

                boolean foundMatch = false;
                for (int i = 0; i < nPairs; i++) {
                    int value = machine.readInt();
                    int offset = machine.readInt();
                    if (value == key) {
                        machine.jump(offset);
                        foundMatch = true;
                        break;
                    }
                }

                if (!foundMatch) {
                    machine.jump(defawlt);
                }
                break;
            }

            case IRETURN:
            case LRETURN:
            case FRETURN:
            case DRETURN:
            case WRETURN:
            case ARETURN: {
                Value result = pop();
                ExecutionFrame frame = machine.popFrame();

                //if this was the topmost frame on the stack
                if (frame == null) {
                    returnValue = result;
                    return MethodStatus.METHOD_END;
                }

                push(result);
                break;
            }

            case RETURN: {
                ExecutionFrame frame = machine.popFrame();
                if (frame == null) {
                    returnValue = VoidValue.VOID;
                    return MethodStatus.METHOD_END;
                }
                break;
            }

            case GETSTATIC:  push(machine.getStatic(readU2())); break;
            case PUTSTATIC:  machine.putStatic(readU2(), pop()); break;
            case GETFIELD:   push(machine.getField(pop().asReference(), readU2())); break;
            case PUTFIELD: {
                Value value = pop();
                Object instance = pop().asBoxedJavaValue();
                machine.putField(instance, readU2(), value);
                break;
            }

            case INVOKEVIRTUAL: {
                int cpIndex = readU2();
                ClassMethodActor resolveMethod = (ClassMethodActor) machine.resolveMethod(cpIndex);
                ClassMethodActor methodActor = resolveMethod;
                Value value = machine.peek(methodActor.descriptor().numberOfParameters() + 1);
                if (value instanceof ReferenceValue) {
                    ReferenceValue receiver = (ReferenceValue) value;
                    if (receiver.isZero()) {
                        machine.raiseException(new NullPointerException());
                    }

                    ClassActor dynamicClass = receiver.getClassActor();
                    assert dynamicClass != null;

                    methodActor = dynamicClass.findVirtualMethodActor(methodActor);
                }

                if (methodActor == null) {
                    ReferenceValue receiver = (ReferenceValue) value;
                    ClassActor dynamicClass = receiver.getClassActor();

                    methodActor = dynamicClass.findVirtualMethodActor(methodActor);
                    machine.raiseException(new AbstractMethodError());
                } else if (methodActor.isAbstract()) {
                    machine.raiseException(new AbstractMethodError());
                }

                machine.invokeMethod(methodActor);
                break;
            }

            case INVOKESPECIAL: {
                int cpIndex = readU2();
                ClassMethodActor methodActor = (ClassMethodActor) machine.resolveMethod(cpIndex);
                Value receiver = machine.peek(methodActor.descriptor().numberOfParameters() + 1);

                if (receiver.isZero() && receiver instanceof ReferenceValue) {
                    machine.raiseException(new NullPointerException());
                }

                machine.invokeMethod(methodActor);
                break;
            }

            case INVOKESTATIC: {
                int cpIndex = readU2();
                ClassMethodActor methodActor = (ClassMethodActor) machine.resolveMethod(cpIndex);
                machine.invokeMethod(methodActor);
                break;
            }

            case INVOKEINTERFACE: {
                int cpIndex = readU2();
                readU2();
                InterfaceMethodActor methodActor = (InterfaceMethodActor) machine.resolveMethod(cpIndex);
                ReferenceValue receiver = (ReferenceValue) machine.peek(methodActor.descriptor().numberOfParameters() + 1);

                if (receiver.isZero()) {
                    machine.raiseException(new NullPointerException());
                }

                ClassActor dynamicClass = receiver.getClassActor();
                assert dynamicClass != null;

                if (!dynamicClass.getAllInterfaceActors().contains((InterfaceActor) methodActor.holder())) {
                    machine.raiseException(new IncompatibleClassChangeError(dynamicClass + " does not implement " + methodActor.holder()));
                }

                VirtualMethodActor dynamicMethodActor = dynamicClass.findVirtualMethodActor(methodActor);

                if (dynamicMethodActor == null) {
                    machine.raiseException(new AbstractMethodError("No such method " + methodActor + " found in " + dynamicClass));
                } else if (dynamicMethodActor.isAbstract()) {
                    machine.raiseException(new AbstractMethodError("Method " + dynamicMethodActor + " is abstract in " + dynamicClass));
                } else if (!dynamicMethodActor.isPublic()) {
                    machine.raiseException(new IllegalAccessError("Method " + dynamicMethodActor + " is not public in " + dynamicClass));
                }

                machine.invokeMethod(dynamicMethodActor);
                break;
            }

            case NEW: {
                int cpIndex = readU2();
                ClassActor classActor = machine.resolveClassReference(cpIndex);
                push(ReferenceValue.from(Objects.allocateInstance(classActor.toJava())));
                break;
            }
            case NEWARRAY: {
                int arrayType = readU1();
                int arraySize  = pop().asInt();

                if (arraySize < 0) {
                    machine.raiseException(new NegativeArraySizeException());
                }

                switch (arrayType) {
                    case 4:
                        push(ReferenceValue.from(new boolean[arraySize]));
                        break;
                    case 5:
                        push(ReferenceValue.from(new char[arraySize]));
                        break;
                    case 6:
                        push(ReferenceValue.from(new float[arraySize]));
                        break;
                    case 7:
                        push(ReferenceValue.from(new double[arraySize]));
                        break;
                    case 8:
                        push(ReferenceValue.from(new byte[arraySize]));
                        break;
                    case 9:
                        push(ReferenceValue.from(new short[arraySize]));
                        break;
                    case 10:
                        push(ReferenceValue.from(new int[arraySize]));
                        break;
                    case 11:
                        push(ReferenceValue.from(new long[arraySize]));
                        break;
                }

                break;
            }

            case ANEWARRAY: {
                int cpIndex = readU2();
                int arraySize = pop().asInt();

                ClassActor classActor = machine.resolveClassReference(cpIndex);

                if (arraySize < 0) {
                    machine.raiseException(new NegativeArraySizeException());
                }

                push(ReferenceValue.from(Array.newInstance(classActor.toJava(), arraySize)));
                break;
            }

            case ARRAYLENGTH: {
                Reference array = pop().asReference();

                if (array.isZero()) {
                    machine.raiseException(new NullPointerException());
                }

                push(IntValue.from(Layout.readArrayLength(array)));
                break;
            }

            case ATHROW: {
                ReferenceValue t = (ReferenceValue) pop();
                if (t.isZero()) {
                    throw new NullPointerException();
                } else {
                    throw machine.raiseException(t);
                }
            }

            case CHECKCAST: {
                int cpIndex = readU2();
                ClassActor classActor = machine.resolveClassReference(cpIndex);
                ReferenceValue object = (ReferenceValue) pop();

                if (!object.isZero()) {
                    if (!classActor.isAssignableFrom(object.getClassActor())) {
                        String message = object.getClassActor().toJava() + " is not a subclass of " + classActor;
                        machine.raiseException(new ClassCastException(message));
                    }
                }

                push(object);
                break;
            }

            case INSTANCEOF: {
                int cpIndex = readU2();
                ClassActor classActor = machine.resolveClassReference(cpIndex);
                ReferenceValue object = (ReferenceValue) pop();

                if (object.isZero() || !classActor.isAssignableFrom(object.getClassActor())) {
                    push(IntValue.from(0));
                } else {
                    push(IntValue.from(1));
                }

                break;
            }

            case MONITORENTER:
            case MONITOREXIT:
                pop();
                break;

            case MULTIANEWARRAY: {
                int cpIndex = readU2();
                ClassActor arrayClassActor = machine.resolveClassReference(cpIndex);
                int lengthsCount = (short) (readS1() & 0x7F);
                if (lengthsCount < 1) {
                    throw new ClassFormatError("dimensions operand of multianewarray is less than 1");
                }
                int[] lengths = new int[lengthsCount];

                if (lengthsCount > arrayClassActor.numberOfDimensions()) {
                    throw new IncompatibleClassChangeError(lengthsCount + " is too many dimensions for " + arrayClassActor);
                }

                for (int i = lengthsCount - 1; i >= 0; --i) {
                    lengths[i] = pop().asInt();
                    if (lengths[i] < 0) {
                        machine.raiseException(new NegativeArraySizeException());
                    }
                }

                push(ReferenceValue.from(createMultiDimensionArray(arrayClassActor, 0, lengths)));
                break;
            }

            case IFNULL: {
                final int offset = machine.readShort();
                final Value r = machine.pop();

                if (r.isZero()) {
                    machine.jump(offset);
                }

                break;
            }

            case IFNONNULL: {
                final int offset = machine.readShort();
                final Value r = machine.pop();

                if (!r.isZero()) {
                    machine.jump(offset);
                }

                break;
            }

            case GOTO_W:
                machine.jump(machine.readInt());
                break;

            case JSR_W:
                int offset = machine.readInt();
                int returnPosition = machine.currentThread().frame().currentBytePosition();
                push(IntValue.from(returnPosition));
                machine.jump(offset);
                break;

            case UNSAFE_CAST:            machine.skipBytes(2); break;
            case WCONST_0:               machine.skipBytes(2); push(WordValue.ZERO); break;


            case PCMPSWP:
            case MEMBAR:
            case PGET:
            case PSET:
            case PREAD:
            case PWRITE: {
                int operand = readU2();
                opcode = opcode | operand << 8;
                switch (opcode) {

                    case PREAD_BYTE:             pointerLoad(Kind.BYTE, false); break;
                    case PREAD_CHAR:             pointerLoad(Kind.CHAR, false); break;
                    case PREAD_SHORT:            pointerLoad(Kind.SHORT, false); break;
                    case PREAD_INT:              pointerLoad(Kind.INT, false); break;
                    case PREAD_FLOAT:            pointerLoad(Kind.FLOAT, false); break;
                    case PREAD_LONG:             pointerLoad(Kind.LONG, false); break;
                    case PREAD_DOUBLE:           pointerLoad(Kind.DOUBLE, false); break;
                    case PREAD_WORD:             pointerLoad(Kind.WORD, false); break;
                    case PREAD_REFERENCE:        pointerLoad(Kind.REFERENCE, false); break;
                    case PREAD_BYTE_I:           pointerLoad(Kind.BYTE, true); break;
                    case PREAD_CHAR_I:           pointerLoad(Kind.CHAR, true); break;
                    case PREAD_SHORT_I:          pointerLoad(Kind.SHORT, true); break;
                    case PREAD_INT_I:            pointerLoad(Kind.INT, true); break;
                    case PREAD_FLOAT_I:          pointerLoad(Kind.FLOAT, true); break;
                    case PREAD_LONG_I:           pointerLoad(Kind.LONG, true); break;
                    case PREAD_DOUBLE_I:         pointerLoad(Kind.DOUBLE, true); break;
                    case PREAD_WORD_I:           pointerLoad(Kind.WORD, true); break;
                    case PREAD_REFERENCE_I:      pointerLoad(Kind.REFERENCE, true); break;
                    case PWRITE_BYTE:
                    case PWRITE_SHORT:
                    case PWRITE_INT:
                    case PWRITE_FLOAT:
                    case PWRITE_LONG:
                    case PWRITE_DOUBLE:
                    case PWRITE_WORD:
                    case PWRITE_REFERENCE:
                    case PWRITE_BYTE_I:
                    case PWRITE_SHORT_I:
                    case PWRITE_INT_I:
                    case PWRITE_FLOAT_I:
                    case PWRITE_LONG_I:
                    case PWRITE_DOUBLE_I:
                    case PWRITE_WORD_I:
                    case PWRITE_REFERENCE_I:     throw FatalError.unexpected("Cannot interpret pointer writes remotely");
                    case PGET_BYTE:              pointerGet(Kind.BYTE); break;
                    case PGET_CHAR:              pointerGet(Kind.CHAR); break;
                    case PGET_SHORT:             pointerGet(Kind.SHORT); break;
                    case PGET_INT:               pointerGet(Kind.INT); break;
                    case PGET_FLOAT:             pointerGet(Kind.FLOAT); break;
                    case PGET_LONG:              pointerGet(Kind.LONG); break;
                    case PGET_DOUBLE:            pointerGet(Kind.DOUBLE); break;
                    case PGET_WORD:              pointerGet(Kind.WORD); break;
                    case PGET_REFERENCE:         pointerGet(Kind.REFERENCE); break;
                    case PSET_BYTE:
                    case PSET_SHORT:
                    case PSET_INT:
                    case PSET_FLOAT:
                    case PSET_LONG:
                    case PSET_DOUBLE:
                    case PSET_WORD:
                    case PSET_REFERENCE:
                    case PCMPSWP_INT:
                    case PCMPSWP_WORD:
                    case PCMPSWP_REFERENCE:
                    case PCMPSWP_INT_I:
                    case PCMPSWP_WORD_I:
                    case PCMPSWP_REFERENCE_I:    throw FatalError.unexpected("Cannot interpret pointer writes remotely");
                    default:                     machine.raiseException(new ClassFormatError("Unsupported bytecode: " + opcode + " [" + Bytecodes.nameOf(opcode) + "]"));
                }
                break;
            }
            case MOV_I2F:                machine.skipBytes(2); push(FloatValue.from(Float.intBitsToFloat(pop().asInt()))); break;
            case MOV_F2I:                machine.skipBytes(2); push(IntValue.from(Float.floatToRawIntBits(pop().asFloat()))); break;
            case MOV_L2D:                machine.skipBytes(2); push(DoubleValue.from(Double.longBitsToDouble(pop().asLong()))); break;
            case MOV_D2L:                machine.skipBytes(2); push(LongValue.from(Double.doubleToRawLongBits(pop().asDouble()))); break;
            case SAFEPOINT:              machine.skipBytes(2); break;
            case PAUSE:                  machine.skipBytes(2); break;
            case FLUSHW:                 machine.skipBytes(2); break;
            case LSB:                    machine.skipBytes(2); push(minus1IfWordWidth(Long.numberOfTrailingZeros((pop().asLong())))); break;
            case MSB:                    machine.skipBytes(2); push(minus1IfWordWidth(Long.numberOfLeadingZeros((pop().asLong())))); break;
            case UWCMP: {
                int operand = readU2();
                Address value2 = pop().asWord().asAddress();
                Address value1 = pop().asWord().asAddress();
                switch (operand) {
                    case ABOVE_EQUAL: push(value1.greaterEqual(value2) ? 1 : 0); break;
                    case ABOVE_THAN:  push(value1.greaterThan(value2) ? 1 : 0); break;
                    case BELOW_EQUAL: push(value1.lessEqual(value2) ? 1 : 0); break;
                    case BELOW_THAN:  push(value1.lessThan(value2) ? 1 : 0); break;
                    default:
                        machine.raiseException(new ClassFormatError("Unsupported UWCMP operand: " + operand));
                }
                break;
            }
            case UCMP: {
                int operand = readU2();
                long value2 = pop().toLong() & 0xFFFFFFFFL;
                long value1 = pop().toLong() & 0xFFFFFFFFL;
                switch (operand) {
                    case ABOVE_EQUAL: push(value1 >= value2 ? 1 : 0); break;
                    case ABOVE_THAN:  push(value1 >  value2 ? 1 : 0); break;
                    case BELOW_EQUAL: push(value1 <= value2 ? 1 : 0); break;
                    case BELOW_THAN:  push(value1 <  value2 ? 1 : 0); break;
                    default:
                        machine.raiseException(new ClassFormatError("Unsupported UCMP operand: " + operand));
                }
                break;
            }
            default:                     machine.raiseException(new ClassFormatError("Unsupported bytecode: " + opcode + " [" + Bytecodes.nameOf(opcode) + "]"));
            // Checkstyle: resume
        }
        return MethodStatus.METHOD_CONTINUE;
    }

    public static enum MethodStatus {
        METHOD_END,
        METHOD_CONTINUE,
    }

    private static Object createMultiDimensionArray(ClassActor arrayClassActor, int lengthIndex, int[] lengths) {
        ClassActor componentClassActor = arrayClassActor.componentClassActor();
        assert componentClassActor != null : arrayClassActor + " is not an array class";
        int length = lengths[lengthIndex];
        assert length >= 0 : "negative array length: " + length;
        Object result = Array.newInstance(componentClassActor.toJava(), length);
        if (length > 0) {
            int nextLengthIndex = lengthIndex + 1;
            if (nextLengthIndex < lengths.length) {
                for (int i = 0; i < length; i++) {
                    Object subArray = createMultiDimensionArray(componentClassActor, nextLengthIndex, lengths);
                    Object[] array = (Object[]) result;
                    array[i] = subArray;
                }
            }
        }
        return result;
    }
}
