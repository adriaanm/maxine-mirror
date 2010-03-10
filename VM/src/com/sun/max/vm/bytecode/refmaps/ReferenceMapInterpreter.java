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
package com.sun.max.vm.bytecode.refmaps;

import static com.sun.c1x.bytecode.Bytecodes.*;

import com.sun.c1x.bytecode.*;
import com.sun.max.annotate.*;
import com.sun.max.program.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.bytecode.*;
import com.sun.max.vm.bytecode.graft.*;
import com.sun.max.vm.classfile.*;
import com.sun.max.vm.classfile.constant.*;
import com.sun.max.vm.classfile.stackmap.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.thread.*;
import com.sun.max.vm.type.*;
import com.sun.max.vm.verifier.types.*;

/**
 * An abstract interpreter for computing the reference map for the local variables and operand stack at any position in
 * a method. Each concrete interpreter denotes if it is suitable for use in an
 * {@linkplain #performsAllocation() allocation free} context such as during a garbage collection.
 *
 * @author Doug Simon
 */
public abstract class ReferenceMapInterpreter {

    /**
     * Initializes frames for each basic block in the {@linkplain ReferenceMapInterpreterContext#classMethodActor() method}
     * associated with a given interpretation context. The returned frames are encoded in a format unique to a specific
     * interpreter which can be retrieved by calling {@link #from(Object)} on the returned value.
     */
    public static Object createFrames(ReferenceMapInterpreterContext context) {
        final ClassMethodActor classMethodActor = context.classMethodActor();
        final CodeAttribute codeAttribute = classMethodActor.codeAttribute();
        final int maxStack = codeAttribute.maxStack;
        final int maxLocals = codeAttribute.maxLocals;
        final ReferenceMapInterpreter interpreter;
        if (maxStack <= CompactReferenceMapInterpreter.MAX_STACK && (maxStack + maxLocals) < CompactReferenceMapInterpreter.MAX_SLOTS) {
            // Cannot use the shared thread-local compact reference map interpreter (i.e. VmThread.current().compactReferenceMapInterpreter())
            // here as a GC may be triggered while initializing the frames.
            interpreter = new CompactReferenceMapInterpreter();
        } else {
            interpreter = new StandardReferenceMapInterpreter();
        }
        return interpreter.createFrames0(context);
    }

    /**
     * Gets the interpreter that compatible with the frames encoded in {@code blockFrames}.
     *
     * @param blockFrames a value that was obtained by calling {@link #createFrames(ReferenceMapInterpreterContext)}.
     */
    public static ReferenceMapInterpreter from(Object blockFrames) {
        if (blockFrames instanceof int[]) {
            return VmThread.current().compactReferenceMapInterpreter();
        }
        assert blockFrames instanceof StandardReferenceMapInterpreter.Frame[];
        return new StandardReferenceMapInterpreter();
    }

    private ConstantPool constantPool;
    private CodeAttribute codeAttribute;
    private byte[] code;
    private int bytecodePosition;
    private int sp;

    private ReferenceMapInterpreterContext context;

    protected int maxStack() {
        return codeAttribute.maxStack;
    }

    protected int maxLocals() {
        return codeAttribute.maxLocals;
    }

    /**
     * Merges the current frame state into the block denoted by a given block index.
     *
     * @param targetBlockIndex
     * @return true if the target block's frame state was changed as a result of the merge
     */
    protected abstract boolean mergeInto(int targetBlockIndex, int stackDepth);

    /**
     * Determines if a given {@linkplain VerificationType verification type} denotes a reference type. This method is
     * necessary as the verification type hierarchy does not model the special {@linkplain Word word} types. Decoding of
     * a verification type occurs in the context of a method as certain verification types refer to the constant pool
     * and bytecode positions of the method.
     *
     * @param receiverTypeIsWord specifies if the type of 'this' in the enclosing context is a Word type. If the
     *            enclosing context is a static method, this value will be {@code false}.
     * @param code the bytecode of the enclosing context
     * @param constantPool the constant pool of the enclosing context
     * @param type the verification type to be tested
     * @return true if {@code type} denotes a reference kind
     */
    public static boolean isReference(boolean receiverTypeIsWord, byte[] code, ConstantPool constantPool, VerificationType type) {
        if (VerificationType.UNINITIALIZED_THIS == type) {
            return !receiverTypeIsWord;
        } else if (VerificationType.UNINITIALIZED.isAssignableFrom(type)) {
            final UninitializedNewType uninitializedNewType = (UninitializedNewType) type;
            final int positionOfNew = uninitializedNewType.position();
            final int constantPoolIndex = ((code[positionOfNew + 1] & 0xFF) << 8) | (code[positionOfNew + 2] & 0xFF);
            final TypeDescriptor typeDescriptor = constantPool.classAt(constantPoolIndex).typeDescriptor();
            return typeDescriptor.toKind().isReference;
        } else if (VerificationType.REFERENCE.isAssignableFrom(type)) {
            final TypeDescriptor typeDescriptor = type.typeDescriptor();
            if (typeDescriptor == null || typeDescriptor.toKind().isReference) {
                return true;
            }
        }
        return false;
    }

    private boolean merge(int targetBlockIndex) {
        return mergeInto(targetBlockIndex, sp);
    }

    private boolean merge(int targetBlock1Index, int targetBlock2Index) {
        return merge(targetBlock1Index) | merge(targetBlock2Index);
    }

    // Keeping this info around for debugging:
    private ClassMethodActor classMethodActor;

    /**
     * Performs any initialization necessary before interpretation begins.
     * <p>
     * Sub-classes must override this method to either get a hold of the block frames from {@code context} or allocate
     * them if {@link ReferenceMapInterpreterContext#blockFrames()} returns null. The overriding method must also initially
     * call {@code super.resetInterpreter(context)}.
     */
    protected void resetInterpreter(ReferenceMapInterpreterContext context) {
        this.classMethodActor = context.classMethodActor();
        final CodeAttribute codeAttribute = classMethodActor.codeAttribute();
        this.constantPool = codeAttribute.constantPool;
        this.codeAttribute = codeAttribute;
        this.code = codeAttribute.code();
        this.context = context;
        this.sp = 0;
        this.bytecodePosition = -1;
    }

    /**
     * Determines if this interpreter performs any allocation during a call to
     * {@link #finalizeFrames(ReferenceMapInterpreterContext)} or
     * {@link #interpretReferenceSlotsAt(ReferenceMapInterpreterContext, ReferenceSlotVisitor, int)}.
     */
    public abstract boolean performsAllocation();

    /**
     * Helper class used by {@link ReferenceMapInterpreter#createFrames0(ReferenceMapInterpreterContext)}.
     */
    class FramesInitialization implements FrameModel, VerificationRegistry {

        int activeLocals;

        // Implementation of ParameterVisitor

        /**
         * Interprets the parameters in a method's signature to initialize the frame state of the entry block.
         */
        public void interpret(SignatureDescriptor signature) {
            for (int i = 0; i < signature.numberOfParameters(); ++i) {
                final TypeDescriptor parameter = signature.parameterDescriptorAt(i);
                final Kind parameterKind = parameter.toKind();
                if (parameterKind.isReference) {
                    updateLocal(activeLocals, true);
                }
                activeLocals += parameterKind.stackSlots;
            }
        }

        // Implementation of FrameModel

        public int activeLocals() {
            return activeLocals;
        }

        public void chopLocals(int numberOfLocals) {
            activeLocals -= numberOfLocals;
            adjustCurrentFrame();
        }

        public void clear() {
            activeLocals = 0;
            sp = 0;
            adjustCurrentFrame();
        }

        public void clearStack() {
            sp = 0;
            adjustCurrentFrame();
        }

        private void adjustCurrentFrame() {
            for (int sp = ReferenceMapInterpreter.this.sp; sp < maxStack(); ++sp) {
                updateStack(sp, false);
            }
            for (int local = activeLocals; local < maxLocals(); ++local) {
                updateLocal(local, false);
            }
        }

        private boolean isReference(VerificationType type) {
            return ReferenceMapInterpreter.isReference(context.classMethodActor().holder().kind.isWord, code, constantPool, type);
        }

        public void push(VerificationType type) {
            if (type.isCategory2()) {
                pushCategory2();
            } else {
                pushCategory1(isReference(type));
            }
        }

        public void store(VerificationType type, int index) {
            if (type.isCategory2()) {
                storeCategory2(index);
                activeLocals = Math.max(activeLocals, index + 2);
            } else {
                storeCategory1(index, isReference(type));
                activeLocals = Math.max(activeLocals, index + 1);
            }
        }

        // Implementation of VerificationRegistry

        public int clearSubroutines() {
            throw ProgramError.unexpected();
        }

        public ConstantPool constantPool() {
            return constantPool;
        }

        public ObjectType getObjectType(TypeDescriptor typeDescriptor) {
            if (typeDescriptor.toKind().isWord) {
                return VerificationType.WORD;
            }
            return VerificationType.OBJECT;
        }

        public Subroutine getSubroutine(int entryPosition, int maxLocals) {
            throw ProgramError.unexpected();
        }

        public UninitializedNewType getUninitializedNewType(int position) {
            return new UninitializedNewType(position);
        }

        public VerificationType getVerificationType(TypeDescriptor typeDescriptor) {
            return VerificationType.getVerificationType(typeDescriptor, this);
        }

        public ClassActor resolve(TypeDescriptor type) {
            throw ProgramError.unexpected();
        }
    }

    /**
     * Creates a set of frames to be used when interpreting the
     * {@linkplain ReferenceMapInterpreterContext#classMethodActor() method} associated with a given interpretation context.
     * The first frame is initialized based on the signature of the method. Additionally, if the method has a
     * {@linkplain CodeAttribute#stackMapTable() stack map attribute}, then the frames for the blocks that have a
     * corresponding entry in the stack map table are initialized from the entry.
     *
     * TODO: The motivation for initializing frames based on a StackMapTable attribute is to reduce the number of
     * iterations performed in {@link #finalizeFrames(ReferenceMapInterpreterContext)}. Analysis is still required
     * to determine if this trade-off provides a win in the common case.
     */
    private Object createFrames0(ReferenceMapInterpreterContext context) {
        resetInterpreter(context);
        final FramesInitialization framesInitialization = new FramesInitialization();
        final ClassMethodActor classMethodActor = context.classMethodActor();
        if (!classMethodActor.isStatic()) {
            final VerificationType receiverType = classMethodActor.holder().kind.isReference ? VerificationType.OBJECT : VerificationType.WORD;
            framesInitialization.store(receiverType, 0);
        }

        framesInitialization.interpret(classMethodActor.descriptor());

        merge(0);

        final StackMapTable stackMapTable = codeAttribute.stackMapTable();

        if (stackMapTable != null) {
            final StackMapFrame[] stackMapFrames = stackMapTable.getFrames(framesInitialization);
            int previousFramePosition = -1;
            for (int frameIndex = 0; frameIndex != stackMapFrames.length; ++frameIndex) {
                final StackMapFrame stackMapFrame = stackMapFrames[frameIndex];
                stackMapFrame.applyTo(framesInitialization);
                final int position = stackMapFrame.getPosition(previousFramePosition);
                final int blockIndex = context.blockIndexFor(position);
                final int blockStartBytecodePosition = context.blockStartBytecodePosition(blockIndex);
                if (position == blockStartBytecodePosition) {
                    merge(blockIndex);
                } else {
                    //ProgramWarning.message("Ignoring StackMapTable frame for non-block start position: " + position);
                }
                previousFramePosition = position;
            }
        }
        final Object frames = frames();
        assert frames != null;
        this.context = null;
        return frames;
    }

    /**
     * Gets the frames for the basic blocks.
     *
     * @return the interpreter sub-class specific encoding of the frames for each basic block
     */
    protected abstract Object frames();

    /**
     * Ensures that the frames for all <i>reachable</i> basic blocks are completely initialized and performs interpretation iteratively
     * if necessary until they are.
     * <p>
     * If {@link #performsAllocation()} returns false for this interpreter, then this method is guaranteed not to perform
     * any allocation.
     *
     * @param context the interpretation context for a method
     */
    public void finalizeFrames(ReferenceMapInterpreterContext context) {
        assert this.context == null;
        resetInterpreter(context);

        final int numberOfBlocks = context.numberOfBlocks();
        boolean changed = false;

        // If the map for each block is initialized, then they are in their final state
        // and so there's no need to run the iterative algorithm below to compute them
        for (int blockIndex = 0; blockIndex < numberOfBlocks; ++blockIndex) {
            if (!isFrameInitialized(blockIndex)) {
                changed = true;
                break;
            }
        }

        // This only runs if the map for at least one block was uninitialized
        int iterations = 0;
        while (changed) {
            changed = false;
            for (int blockIndex = 0; blockIndex < numberOfBlocks; ++blockIndex) {
                if (isFrameInitialized(blockIndex)) {
                    changed = interpretBlock(blockIndex, null, null) || changed;
                }
            }
            ++iterations;
        }
        this.context = null;
    }

    /**
     * Perform interpretation of the basic blocks containing the bytecode positions yielded by a given bytecode position
     * iterator.
     * <p>
     * If {@link #performsAllocation()} returns {@code false} for this interpreter, then this method is guaranteed not
     * to perform any allocation.
     *
     * @param context the interpretation context for a method
     * @param visitor the visitor to notify of reference slots in the frame state at the bytecode positions yielded by
     *            {@code bytecodePositionIterator}
     * @param bytecodePositionIterator the bytecode positions at which {@code visitor} should notified of the references
     *            in the abstract interpretation state
     */
    public void interpretReferenceSlots(ReferenceMapInterpreterContext context, ReferenceSlotVisitor visitor, BytecodePositionIterator bytecodePositionIterator) {
        assert this.context == null;
        resetInterpreter(context);

        bytecodePositionIterator.reset();
        for (int bytecodePosition = bytecodePositionIterator.bytecodePosition(); bytecodePosition != -1; bytecodePosition = bytecodePositionIterator.bytecodePosition()) {
            final int blockIndex = blockIndexFor(bytecodePosition);
            if (!isFrameInitialized(blockIndex)) {
                bytecodePositionIterator.next();
            } else {
                interpretBlock(blockIndex, bytecodePositionIterator, visitor);
            }
        }

        this.context = null;
    }

    abstract boolean isLocalRef(int index);
    abstract boolean isStackRef(int index);

    void popAndStoreRefOrWord(int index) {
        final boolean isRef = isStackRef(--sp);
        storeCategory1(index, isRef);
    }

    void popAndStoreCategory1(int index) {
        popCategory1();
        storeCategory1(index, false);
    }
    void popAndStoreCategory2(int index) {
        popCategory2();
        storeCategory2(index);
    }

    void storeCategory1(int index, boolean isRef) {
        updateLocal(index, isRef);
    }
    void storeCategory2(int index) {
        updateLocal(index, false);
        updateLocal(index + 1, false);
    }

    void loadAndPushRefOrWord(int index) {
        assert sp < maxStack();
        updateStack(sp++, isLocalRef(index));
    }

    void pushRef() {
        assert sp < maxStack();
        updateStack(sp++, true);
    }

    void pushCategory1() {
        assert sp < maxStack();
        updateStack(sp++, false);
    }

    void pushCategory1(boolean isRef) {
        assert sp < maxStack();
        updateStack(sp++, isRef);
    }

    void pushCategory2() {
        assert sp < maxStack() - 1;
        updateStack(sp++, false);
        updateStack(sp++, false);
    }

    void push(Kind kind) {
        if (kind.isReference) {
            pushRef();
        } else {
            if (!kind.isCategory1) {
                pushCategory2();
            } else {
                if (kind != Kind.VOID) {
                    pushCategory1();
                }
            }
        }
    }

    boolean topIsRef() {
        return isStackRef(sp - 1);
    }

    void popCategory2() {
        assert sp > 1;
        sp = sp - 2;
    }

    boolean popCategory1() {
        assert sp > 0;
        return isStackRef(--sp);
    }

    boolean pop(Kind kind) {
        if (kind.isCategory1) {
            return popCategory1();
        }
        popCategory2();
        return false;
    }

    abstract void updateStack(int index, boolean isRef);
    abstract void updateLocal(int index, boolean isRef);

    private void skip1() {
        bytecodePosition++;
    }

    private void skip2() {
        bytecodePosition += 2;
    }

    private void alignBytecodePosition() {
        final int remainder = bytecodePosition % 4;
        if (remainder != 0) {
            bytecodePosition += 4 - remainder;
        }
    }

    private byte readByte() {
        return code[bytecodePosition++];
    }

    private int readUnsigned1() {
        return readByte() & 0xff;
    }

    private int readUnsigned2() {
        final int high = readByte() & 0xff;
        final int low = readByte() & 0xff;
        return (high << 8) | low;
    }

    private int readSigned2() {
        final int high = readByte();
        final int low = readByte() & 0xff;
        return (high << 8) | low;
    }

    private int readSigned4() {
        final int b3 = readByte() << 24;
        final int b2 = (readByte() & 0xff) << 16;
        final int b1 = (readByte() & 0xff) << 8;
        final int b0 = readByte() & 0xff;
        return b3 | b2 | b1 | b0;
    }

    private void visitReferencesAtCurrentBytecodePosition(ReferenceSlotVisitor visitor, boolean parametersPopped) {
        if (!parametersPopped) {
            for (int i = 0; i < maxLocals(); i++) {
                if (isLocalRef(i)) {
                    visitor.visitReferenceInLocalVariable(i);
                }
            }
        }
        for (int i = 0; i < sp; i++) {
            if (isStackRef(i)) {
                visitor.visitReferenceOnOperandStack(i, parametersPopped);
            }
        }
    }

    private int blockIndexFor(int bytecodePosition) {
        return context.blockIndexFor(bytecodePosition);
    }

    private int blockStartBytecodePosition(int blockIndex) {
        return context.blockStartBytecodePosition(blockIndex);
    }

    /**
     * Determines if the frame for the block denoted by a given index is initialized.
     *
     * @param blockIndex the index of the block to test
     */
    public abstract boolean isFrameInitialized(int blockIndex);

    /**
     * Interprets a given basic block.
     *
     * @param blockIndex the index of the block to be interpreted
     * @return true if the entry state of a control flow successor of this block was modified as a result of the
     *         interpretation
     */
    private boolean interpretBlock(int blockIndex, BytecodePositionIterator bytecodePositionIterator, ReferenceSlotVisitor visitor) {
        final int sp = resetAtBlock(blockIndex);
        return interpretBlock0(blockIndex, sp, bytecodePositionIterator, visitor);
    }

    /**
     * Configures this interpreter for interpreting a given basic block.
     *
     * @param blockIndex the index of the block to be interpreted
     * @return the stack depth upon entry to the block
     */
    abstract int resetAtBlock(int blockIndex);

    private boolean mergeWithExceptionHandlers(int bytecodePosition) {
        boolean changed = false;
        for (ExceptionHandler handler = context.exceptionHandlersActiveAt(bytecodePosition); handler != null; handler = handler.next()) {
            changed = mergeInto(blockIndexFor(handler.position()), -1) || changed;
        }
        return changed;
    }

    /**
     * Interprets a given basic block.
     *
     * @param blockIndex the index of the block to be interpreted
     * @param sp the stack depth upon entry to the block
     * @return true if the entry state of a control flow successor of this block was modified as a result of the
     *         interpretation
     */
    private boolean interpretBlock0(int blockIndex, int sp, BytecodePositionIterator bytecodePositionIterator, ReferenceSlotVisitor visitor) {
        if (MaxineVM.isHosted()) {
            // This indirection is simply for debugging the interpreter loop.
            try {
                return interpretBlock0(blockIndex, sp, bytecodePositionIterator, visitor, false);
            } catch (Throwable e) {
                System.err.println("Re-interpreting block after error: ");
                e.printStackTrace();
                System.err.println(context);
                CodeAttributePrinter.print(System.err, codeAttribute);
                return interpretBlock0(blockIndex, sp, bytecodePositionIterator, visitor, true);
            }
        }
        return interpretBlock0(blockIndex, sp, bytecodePositionIterator, visitor, false);
    }

    @HOSTED_ONLY
    public String[] framesToStrings(ReferenceMapInterpreterContext context) {
        assert this.context == null;
        resetInterpreter(context);

        final int numberOfBlocks = context.numberOfBlocks();
        final String[] frameStrings = new String[numberOfBlocks];
        for (int blockIndex = 0; blockIndex < numberOfBlocks; ++blockIndex) {
            resetAtBlock(blockIndex);
            frameStrings[blockIndex] = currentFrameToString();
        }
        this.context = null;
        return frameStrings;
    }

    @HOSTED_ONLY
    private String currentFrameToString() {
        final StringBuilder sb = new StringBuilder("locals[").append(maxLocals()).append("] = { ");
        for (int i = 0; i != maxLocals(); ++i) {
            if (isLocalRef(i)) {
                sb.append(i).append(" ");
            }
        }
        sb.append("}, stack[").append(sp).append("] = { ");
        for (int i = 0; i != sp; ++i) {
            if (isStackRef(i)) {
                sb.append(i).append(" ");
            }
        }
        return sb.append("}").toString();
    }

    @INLINE
    private boolean interpretBlock0(int blockIndex, int sp, BytecodePositionIterator bytecodePositionIterator, ReferenceSlotVisitor visitor, boolean trace) {
        assert sp >= 0;
        bytecodePosition = blockStartBytecodePosition(blockIndex);
        this.sp = sp;
        final int endPosition = blockStartBytecodePosition(blockIndex + 1);
        int opcode = -1;
        int opcodeBytecodePosition;
        boolean changed = false;

        int searchBytecodePosition = bytecodePositionIterator == null ? -1 : bytecodePositionIterator.bytecodePosition();

        while (bytecodePosition != endPosition) {
            opcodeBytecodePosition = bytecodePosition;
            changed = mergeWithExceptionHandlers(opcodeBytecodePosition) || changed;

            final boolean atSearchPosition = opcodeBytecodePosition == searchBytecodePosition;
            opcode = readUnsigned1();

            if (atSearchPosition) {
                // Record BEFORE popping invoke parameters,
                // because this is not the JIT-to-JIT call yet.
                visitReferencesAtCurrentBytecodePosition(visitor, false);
            }

            if (MaxineVM.isHosted() && trace) {
                System.err.println("  " + currentFrameToString());
                System.err.println(opcodeBytecodePosition + ":  " + Bytecodes.nameOf(opcode));
            }

            switch (opcode) {
                case NOP: {
                    break;
                }
                case ACONST_NULL: {
                    pushRef();
                    break;
                }
                case ICONST_M1:
                case ICONST_0:
                case ICONST_1:
                case ICONST_2:
                case ICONST_3:
                case ICONST_4:
                case ICONST_5:
                case FCONST_0:
                case FCONST_1:
                case FCONST_2: {
                    pushCategory1();
                    break;
                }

                case LCONST_0:
                case LCONST_1:
                case DCONST_0:
                case DCONST_1: {
                    pushCategory2();
                    break;
                }
                case BIPUSH: {
                    skip1();
                    pushCategory1();
                    break;
                }
                case SIPUSH: {
                    skip2();
                    pushCategory1();
                    break;
                }
                case LDC_W:
                case LDC: {
                    final int index = opcode == Bytecodes.LDC ? readUnsigned1() : readUnsigned2();
                    final ConstantPool.Tag tag = constantPool.tagAt(index);
                    switch (tag) {
                        case FLOAT:
                        case INTEGER: {
                            pushCategory1();
                            break;
                        }
                        case STRING:
                        case CLASS: {
                            pushRef();
                            break;
                        }
                        default: {
                            throw ProgramError.unknownCase();
                        }
                    }
                    break;
                }
                case LDC2_W: {
                    skip2();
                    pushCategory2();
                    break;
                }
                case ILOAD:
                case FLOAD: {
                    skip1(); // index
                    pushCategory1();
                    break;
                }
                case LLOAD:
                case DLOAD: {
                    skip1(); // index
                    pushCategory2();
                    break;
                }
                case ALOAD: {
                    loadAndPushRefOrWord(readUnsigned1());
                    break;
                }
                case ILOAD_0:
                case ILOAD_1:
                case ILOAD_2:
                case ILOAD_3:
                case FLOAD_0:
                case FLOAD_1:
                case FLOAD_2:
                case FLOAD_3: {
                    pushCategory1();
                    break;
                }
                case LLOAD_0:
                case LLOAD_1:
                case LLOAD_2:
                case LLOAD_3:
                case DLOAD_0:
                case DLOAD_1:
                case DLOAD_2:
                case DLOAD_3: {
                    pushCategory2();
                    break;
                }
                case ALOAD_0:
                case ALOAD_1:
                case ALOAD_2:
                case ALOAD_3: {
                    loadAndPushRefOrWord(opcode - ALOAD_0);
                    break;
                }
                case IALOAD:
                case BALOAD:
                case CALOAD:
                case SALOAD:
                case FALOAD: {
                    popCategory1();
                    popCategory1();
                    pushCategory1();
                    break;
                }
                case LALOAD:
                case DALOAD: {
                    popCategory1();
                    popCategory1();
                    pushCategory2();
                    break;
                }
                case AALOAD: {
                    popCategory1();
                    popCategory1();
                    pushRef();
                    break;
                }
                case FSTORE:
                case ISTORE: {
                    popAndStoreCategory1(readUnsigned1());
                    break;
                }
                case DSTORE:
                case LSTORE: {
                    popAndStoreCategory2(readUnsigned1());
                    break;
                }
                case ASTORE: {
                    popAndStoreRefOrWord(readUnsigned1());
                    break;
                }
                case ISTORE_0:
                case ISTORE_1:
                case ISTORE_2:
                case ISTORE_3: {
                    popAndStoreCategory1(opcode - ISTORE_0);
                    break;
                }
                case FSTORE_0:
                case FSTORE_1:
                case FSTORE_2:
                case FSTORE_3: {
                    popAndStoreCategory1(opcode - FSTORE_0);
                    break;
                }
                case LSTORE_0:
                case LSTORE_1:
                case LSTORE_2:
                case LSTORE_3: {
                    popAndStoreCategory2(opcode - LSTORE_0);
                    break;
                }
                case DSTORE_0:
                case DSTORE_1:
                case DSTORE_2:
                case DSTORE_3: {
                    popAndStoreCategory2(opcode - DSTORE_0);
                    break;
                }
                case ASTORE_0:
                case ASTORE_1:
                case ASTORE_2:
                case ASTORE_3: {
                    popAndStoreRefOrWord(opcode - ASTORE_0);
                    break;
                }
                case IASTORE:
                case FASTORE:
                case AASTORE:
                case BASTORE:
                case CASTORE:
                case SASTORE: {
                    popCategory1();
                    popCategory1();
                    popCategory1();
                    break;
                }
                case LASTORE:
                case DASTORE: {
                    popCategory2();
                    popCategory1();
                    popCategory1();
                    break;
                }
                case POP: {
                    popCategory1();
                    break;
                }
                case POP2: {
                    popCategory2();
                    break;
                }
                case DUP: {
                    if (isStackRef(this.sp - 1)) {
                        pushRef();
                    } else {
                        pushCategory1();
                    }
                    break;
                }
                case DUP_X1: {
                    final boolean value1 = popCategory1();
                    final boolean value2 = popCategory1();
                    pushCategory1(value1);
                    pushCategory1(value2);
                    pushCategory1(value1);
                    break;
                }
                case DUP_X2: {
                    final boolean value1 = popCategory1();
                    final boolean value2 = popCategory1();
                    final boolean value3 = popCategory1();
                    pushCategory1(value1);
                    pushCategory1(value3);
                    pushCategory1(value2);
                    pushCategory1(value1);
                    break;
                }
                case DUP2: {
                    final boolean value1 = popCategory1();
                    final boolean value2 = popCategory1();
                    pushCategory1(value2);
                    pushCategory1(value1);
                    pushCategory1(value2);
                    pushCategory1(value1);
                    break;
                }
                case DUP2_X1: {
                    final boolean value1 = popCategory1();
                    final boolean value2 = popCategory1();
                    final boolean value3 = popCategory1();
                    pushCategory1(value2);
                    pushCategory1(value1);
                    pushCategory1(value3);
                    pushCategory1(value2);
                    pushCategory1(value1);
                    break;
                }
                case DUP2_X2: {
                    final boolean value1 = popCategory1();
                    final boolean value2 = popCategory1();
                    final boolean value3 = popCategory1();
                    final boolean value4 = popCategory1();
                    pushCategory1(value2);
                    pushCategory1(value1);
                    pushCategory1(value4);
                    pushCategory1(value3);
                    pushCategory1(value2);
                    pushCategory1(value1);
                    break;
                }
                case SWAP: {
                    final boolean value1 = popCategory1();
                    final boolean value2 = popCategory1();
                    pushCategory1(value1);
                    pushCategory1(value2);
                    break;
                }
                case IADD:
                case FADD:
                case FSUB:
                case ISUB:
                case IREM:
                case FREM:
                case IDIV:
                case FDIV:
                case IMUL:
                case FMUL:
                case ISHL:
                case LSHL:
                case ISHR:
                case LSHR:
                case IUSHR:
                case LUSHR:
                case IAND:
                case IOR:
                case IXOR:
                case FCMPL:
                case FCMPG:
                {
                    popCategory1();
                    break;
                }
                case LADD:
                case DADD:
                case LSUB:
                case DSUB:
                case LMUL:
                case DMUL:
                case LDIV:
                case DDIV:
                case LREM:
                case DREM:
                case LAND:
                case LOR:
                case LXOR:
                {
                    popCategory2();
                    break;
                }
                case INEG:
                case LNEG:
                case FNEG:
                case DNEG:
                case I2F:
                case L2D:
                case F2I:
                case D2L:
                case I2B:
                case I2C:
                case I2S:
                {
                    break;
                }
                case IINC:
                {
                    skip2();
                    break;
                }
                case I2D:
                case I2L:
                case F2L:
                case F2D:
                {
                    popCategory1();
                    pushCategory2();
                    break;
                }
                case L2I:
                case L2F:
                case D2I:
                case D2F:
                {
                    popCategory2();
                    pushCategory1();
                    break;
                }
                case LCMP:
                case DCMPL:
                case DCMPG:
                {
                    popCategory2();
                    popCategory2();
                    pushCategory1();
                    break;
                }
                case IFNULL:
                case IFNONNULL:
                case IFEQ:
                case IFNE:
                case IFLT:
                case IFGE:
                case IFGT:
                case IFLE: {
                    popCategory1();
                    final int offset = readSigned2();
                    final int targetBytecodePosition = opcodeBytecodePosition + offset;
                    if (atSearchPosition) {
                        bytecodePositionIterator.next();
                    }
                    return merge(blockIndexFor(targetBytecodePosition), blockIndex + 1);
                }
                case IF_ICMPEQ:
                case IF_ICMPNE:
                case IF_ICMPLT:
                case IF_ICMPGE:
                case IF_ICMPGT:
                case IF_ICMPLE:
                case IF_ACMPEQ:
                case IF_ACMPNE: {
                    popCategory1();
                    popCategory1();
                    final int offset = readSigned2();
                    final int targetBytecodePosition = opcodeBytecodePosition + offset;
                    if (atSearchPosition) {
                        bytecodePositionIterator.next();
                    }
                    return merge(blockIndexFor(targetBytecodePosition), blockIndex + 1);
                }
                case GOTO: {
                    final int offset = readSigned2();
                    final int targetBytecodePosition = opcodeBytecodePosition + offset;
                    if (atSearchPosition) {
                        bytecodePositionIterator.next();
                    }
                    return merge(blockIndexFor(targetBytecodePosition));
                }
                case GOTO_W: {
                    final int offset = readSigned4();
                    final int targetBytecodePosition = opcodeBytecodePosition + offset;
                    if (atSearchPosition) {
                        bytecodePositionIterator.next();
                    }
                    return merge(blockIndexFor(targetBytecodePosition));
                }
                case JSR_W:
                case JSR:
                case RET: {
                    throw FatalError.unexpected("jsr/ret found");
                }
                case TABLESWITCH: {
                    popCategory1();
                    alignBytecodePosition();
                    final int defaultOffset = readSigned4();
                    final int lowMatch = readSigned4();
                    final int highMatch = readSigned4();
                    final int numberOfCases = highMatch - lowMatch + 1;
                    changed = merge(blockIndexFor(opcodeBytecodePosition + defaultOffset));
                    for (int i = 0; i < numberOfCases; i++) {
                        changed = merge(blockIndexFor(opcodeBytecodePosition + readSigned4())) || changed;
                    }
                    if (atSearchPosition) {
                        bytecodePositionIterator.next();
                    }
                    return changed;
                }
                case LOOKUPSWITCH: {
                    popCategory1();
                    alignBytecodePosition();
                    final int defaultOffset = readSigned4();
                    final int numberOfCases = readSigned4();
                    changed = merge(blockIndexFor(opcodeBytecodePosition + defaultOffset));
                    for (int i = 0; i < numberOfCases; i++) {
                        readSigned4();
                        changed = merge(blockIndexFor(opcodeBytecodePosition + readSigned4())) || changed;
                    }
                    if (atSearchPosition) {
                        bytecodePositionIterator.next();
                    }
                    return changed;
                }
                case IRETURN:
                case FRETURN:
                case WRETURN:
                case ARETURN:
                {
                    popCategory1();
                    if (atSearchPosition) {
                        bytecodePositionIterator.next();
                    }
                    return changed;
                }
                case LRETURN:
                case DRETURN:
                {
                    popCategory2();
                    if (atSearchPosition) {
                        bytecodePositionIterator.next();
                    }
                    return changed;
                }
                case RETURN: {
                    if (atSearchPosition) {
                        bytecodePositionIterator.next();
                    }
                    return changed;
                }
                case GETSTATIC: {
                    final int index = readUnsigned2();
                    final FieldRefConstant fieldConstant = constantPool.fieldAt(index);
                    final TypeDescriptor type = fieldConstant.type(constantPool);
                    final Kind kind = type.toKind();
                    if (kind.isReference) {
                        pushRef();
                    } else {
                        if (kind.isCategory1) {
                            pushCategory1();
                        } else {
                            pushCategory2();
                        }
                    }
                    break;
                }
                case PUTSTATIC: {
                    final int index = readUnsigned2();
                    final FieldRefConstant fieldConstant = constantPool.fieldAt(index);
                    final TypeDescriptor type = fieldConstant.type(constantPool);
                    final Kind kind = type.toKind();
                    if (kind.isCategory1) {
                        popCategory1();
                    } else {
                        popCategory2();
                    }
                    break;
                }
                case GETFIELD: {
                    popCategory1(); // instance containing the field
                    final int index = readUnsigned2();
                    final FieldRefConstant fieldConstant = constantPool.fieldAt(index);
                    final TypeDescriptor type = fieldConstant.type(constantPool);
                    final Kind kind = type.toKind();
                    if (kind.isReference) {
                        pushRef();
                    } else {
                        if (kind.isCategory1) {
                            pushCategory1();
                        } else {
                            pushCategory2();
                        }
                    }
                    break;

                }
                case PUTFIELD: {
                    final int index = readUnsigned2();
                    final FieldRefConstant fieldConstant = constantPool.fieldAt(index);
                    final TypeDescriptor type = fieldConstant.type(constantPool);
                    final Kind kind = type.toKind();
                    if (kind.isCategory1) {
                        popCategory1();
                    } else {
                        popCategory2();
                    }
                    popCategory1(); // instance containing the field
                    break;
                }
                case JNICALL: {
                    final int index = readUnsigned2();
                    final SignatureDescriptor methodSignature = SignatureDescriptor.create(constantPool.utf8At(index));
                    for (int i = methodSignature.numberOfParameters() - 1; i >= 0; --i) {
                        final TypeDescriptor parameter = methodSignature.parameterDescriptorAt(i);
                        pop(parameter.toKind());
                    }
                    if (atSearchPosition) {
                        visitReferencesAtCurrentBytecodePosition(visitor, true);
                    }

                    push(methodSignature.resultKind());
                    break;
                }

                case INVOKESPECIAL:
                case INVOKEVIRTUAL:
                case INVOKEINTERFACE:
                case INVOKESTATIC: {
                    final int index = readUnsigned2();
                    if (opcode == Bytecodes.INVOKEINTERFACE) {
                        skip2();
                    }
                    final MethodRefConstant methodConstant = constantPool.methodAt(index);
                    final SignatureDescriptor methodSignature = methodConstant.signature(constantPool);
                    for (int i = methodSignature.numberOfParameters() - 1; i >= 0; --i) {
                        final TypeDescriptor parameter = methodSignature.parameterDescriptorAt(i);
                        pop(parameter.toKind());
                    }
                    if (opcode != Bytecodes.INVOKESTATIC) {
                        popCategory1(); // receiver
                    }

                    if (atSearchPosition) {
                        // Record AFTER popping the parameters.
                        // They will be accounted for in the callee frame,
                        // with potentially different stack slot kinds - see JVM spec.
                        visitReferencesAtCurrentBytecodePosition(visitor, true);
                    }

                    push(methodSignature.resultKind());
                    break;
                }
                case NEW: {
                    final int index = readUnsigned2();
                    push(constantPool.classAt(index).typeDescriptor().toKind());
                    break;
                }
                case NEWARRAY: {
                    skip1();
                    popCategory1();
                    pushRef();
                    break;
                }
                case ANEWARRAY: {
                    skip2();
                    popCategory1();
                    pushRef();
                    break;
                }
                case ARRAYLENGTH: {
                    popCategory1();
                    pushCategory1();
                    break;
                }
                case ATHROW: {
                    popCategory1();
                    if (atSearchPosition) {
                        bytecodePositionIterator.next();
                    }
                    return changed;
                }
                case CHECKCAST: {
                    popCategory1();
                    final int index = readUnsigned2();
                    push(constantPool.classAt(index).typeDescriptor().toKind());
                    break;
                }
                case INSTANCEOF: {
                    skip2();
                    popCategory1();
                    pushCategory1();
                    break;
                }
                case MONITORENTER:
                case MONITOREXIT: {
                    popCategory1();
                    break;
                }
                case WIDE: {
                    final int widenedOpcode = readUnsigned1();
                    final int index = readUnsigned2();
                    switch (widenedOpcode) {
                        case ILOAD:
                        case FLOAD: {
                            pushCategory1();
                            break;
                        }
                        case LLOAD:
                        case DLOAD: {
                            pushCategory2();
                            break;
                        }
                        case ALOAD: {
                            loadAndPushRefOrWord(index);
                            break;
                        }
                        case ISTORE:
                        case FSTORE: {
                            popAndStoreCategory1(index);
                            break;
                        }
                        case LSTORE:
                        case DSTORE: {
                            popAndStoreCategory2(index);
                            break;
                        }
                        case ASTORE: {
                            popAndStoreRefOrWord(index);
                            break;
                        }
                        case IINC: {
                            skip2();
                            break;
                        }
                        default: {
                            ProgramError.unexpected();
                        }
                    }
                    break;
                }
                case MULTIANEWARRAY: {
                    skip2();
                    final int dimensions = readUnsigned1();
                    for (int i = 0; i < dimensions; i++) {
                        popCategory1();
                    }
                    pushRef();
                    break;
                }


                case UNSAFE_CAST: {
                    pop(Intrinsics.toUnsafeCastOperand((char) readUnsigned1()));
                    push(Intrinsics.toUnsafeCastOperand((char) readUnsigned1()));
                    break;
                }
                case WLOAD: {
                    skip1();
                    pushCategory1();
                    break;
                }
                case WLOAD_0:
                case WLOAD_1:
                case WLOAD_2:
                case WLOAD_3: {
                    pushCategory1();
                    break;
                }
                case WSTORE: {
                    skip1();
                    popCategory1();
                    break;
                }
                case WSTORE_0:
                case WSTORE_1:
                case WSTORE_2:
                case WSTORE_3: {
                    popCategory1();
                    break;
                }
                case WCONST_0: {
                    skip2();
                    pushCategory1();
                    break;
                }
                case WDIV:
                case WDIVI:
                case WREM:
                case WREMI: {
                    skip2();
                    popCategory1();
                    break;
                }

                case PREAD: {
                    opcode |= readUnsigned2() << 8;
                    switch (opcode) {
                        case PREAD_BYTE:
                        case PREAD_BYTE_I:
                        case PREAD_CHAR:
                        case PREAD_CHAR_I:
                        case PREAD_SHORT:
                        case PREAD_SHORT_I:
                        case PREAD_INT:
                        case PREAD_INT_I:
                        case PREAD_FLOAT:
                        case PREAD_FLOAT_I:
                        case PREAD_WORD:
                        case PREAD_WORD_I: {
                            popCategory1();
                            break;
                        }
                        case PREAD_REFERENCE:
                        case PREAD_REFERENCE_I: {
                            popCategory1();
                            popCategory1();
                            pushRef();
                            break;
                        }
                        case PREAD_LONG:
                        case PREAD_LONG_I:
                        case PREAD_DOUBLE:
                        case PREAD_DOUBLE_I: {
                            break;
                        }
                        default: {
                            FatalError.unexpected("Unknown bytcode");
                        }
                    }
                    break;
                }
                case PWRITE: {
                    opcode |= readUnsigned2() << 8;
                    switch (opcode) {
                        case PWRITE_WORD:
                        case PWRITE_WORD_I:
                        case PWRITE_REFERENCE:
                        case PWRITE_REFERENCE_I:
                        case PWRITE_BYTE:
                        case PWRITE_BYTE_I:
                        case PWRITE_SHORT:
                        case PWRITE_SHORT_I:
                        case PWRITE_INT:
                        case PWRITE_INT_I:
                        case PWRITE_FLOAT:
                        case PWRITE_FLOAT_I: {
                            popCategory1();
                            popCategory1();
                            popCategory1();
                            break;
                        }
                        case PWRITE_LONG:
                        case PWRITE_LONG_I:
                        case PWRITE_DOUBLE:
                        case PWRITE_DOUBLE_I: {
                            popCategory2();
                            popCategory1();
                            popCategory1();
                            break;
                        }
                        default: {
                            FatalError.unexpected("Unknown bytcode");
                        }
                    }
                    break;
                }
                case PGET: {
                    opcode |= readUnsigned2() << 8;
                    switch (opcode) {
                        case PGET_BYTE:
                        case PGET_CHAR:
                        case PGET_SHORT:
                        case PGET_INT:
                        case PGET_FLOAT:
                        case PGET_WORD: {
                            popCategory1();
                            popCategory1();
                            break;
                        }
                        case PGET_LONG:
                        case PGET_DOUBLE: {
                            popCategory1();
                            break;
                        }
                        case PGET_REFERENCE: {
                            popCategory1();
                            popCategory1();
                            popCategory1();
                            pushRef();
                            break;
                        }
                        default: {
                            FatalError.unexpected("Unknown bytcode");
                        }
                    }
                    break;
                }
                case PSET: {
                    opcode |= readUnsigned2() << 8;
                    switch (opcode) {
                        case PSET_BYTE:
                        case PSET_SHORT:
                        case PSET_INT:
                        case PSET_FLOAT:
                        case PSET_WORD:
                        case PSET_REFERENCE: {
                            popCategory1();
                            popCategory1();
                            popCategory1();
                            popCategory1();
                            break;
                        }
                        case PSET_DOUBLE:
                        case PSET_LONG: {
                            popCategory2();
                            popCategory1();
                            popCategory1();
                            popCategory1();
                            break;
                        }
                        default: {
                            FatalError.unexpected("Unknown bytcode");
                        }
                    }
                    break;
                }
                case PCMPSWP: {
                    opcode |= readUnsigned2() << 8;
                    switch (opcode) {
                        case PCMPSWP_INT:
                        case PCMPSWP_INT_I:
                        case PCMPSWP_WORD:
                        case PCMPSWP_WORD_I: {
                            popCategory1();
                            popCategory1();
                            popCategory1();
                            break;
                        }
                        case PCMPSWP_REFERENCE:
                        case PCMPSWP_REFERENCE_I: {
                            popCategory1();
                            popCategory1();
                            popCategory1();
                            popCategory1();
                            pushRef();
                            break;
                        }
                        default: {
                            FatalError.unexpected("Unknown bytcode");
                        }
                    }
                    break;
                }
                case MOV_I2F:
                case MOV_F2I:
                case MOV_L2D:
                case MOV_D2L: {
                    skip2();
                    break;
                }
                case UWLT:
                case UWLTEQ:
                case UWGT:
                case UWGTEQ:
                case UGE: {
                    skip2();
                    popCategory1();
                    break;
                }
                case MEMBAR: {
                    skip2();
                    break;
                }
                case LSB:
                case MSB:
                    skip2();
                    popCategory2(); // pop Word whose bits are scanned
                    pushCategory1(); // push back an int with the bit index or -1
                    break;
                default: {
                    FatalError.unexpected("Unknown bytcode");
                }
            }

            if (atSearchPosition) {
                searchBytecodePosition = bytecodePositionIterator.next();
                if (searchBytecodePosition == -1 || searchBytecodePosition >= endPosition) {
                    return false;
                }
            }
        }

        assert !Bytecodes.isStop(opcode);
        // Merge into successor
        return merge(blockIndex + 1) || changed;
    }
}
