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
package com.sun.c1x.opt;

import java.lang.reflect.*;
import java.util.*;

import com.sun.c1x.*;
import com.sun.c1x.bytecode.*;
import com.sun.c1x.ci.*;
import com.sun.c1x.ir.*;
import com.sun.c1x.ri.*;
import com.sun.c1x.util.*;

/**
 * The <code>Canonicalizer</code> reduces instructions to a canonical form by folding constants,
 * putting constants on the right side of commutative operators, simplifying conditionals,
 * and several other transformations.
 *
 * @author Ben L. Titzer
 */
public class Canonicalizer extends ValueVisitor {

    private static final Object[] NO_ARGUMENTS = {};

    final RiRuntime runtime;
    Value canonical;
    int bci;
    List<Instruction> extra;

    public Canonicalizer(RiRuntime runtime) {
        this.runtime = runtime;
    }

    public Value canonicalize(Instruction original, int bci) {
        this.canonical = original;
        this.bci = bci;
        this.extra = null;
        original.accept(this);
        return this.canonical;
    }

    /**
     * Gets the canonicalized version of the instruction.
     * @return the canonicalized version of the instruction
     */
    public Value canonical() {
        return canonical;
    }

    public List<Instruction> extra() {
        return extra;
    }

    private <T extends Instruction> T addInstr(T x) {
        if (extra == null) {
            extra = new LinkedList<Instruction>();
        }
        extra.add(x);
        return x;
    }

    private Constant intInstr(int v) {
        return addInstr(Constant.forInt(v));
    }

    private Constant longInstr(long v) {
        return addInstr(Constant.forLong(v));
    }

    private Value setCanonical(Value x) {
        return canonical = x;
    }

    private Value setIntConstant(int val) {
        return canonical = Constant.forInt(val);
    }

    private Value setConstant(CiConstant val) {
        return canonical = new Constant(val);
    }

    private Value setBooleanConstant(boolean val) {
        return canonical = Constant.forBoolean(val);
    }

    private Value setObjectConstant(Object val) {
        if (C1XOptions.SupportObjectConstants) {
            return canonical = Constant.forObject(val);
        }
        return canonical;
    }

    private Value setLongConstant(long val) {
        return canonical = Constant.forLong(val);
    }

    private Value setFloatConstant(float val) {
        return canonical = Constant.forFloat(val);
    }

    private Value setDoubleConstant(double val) {
        return canonical = Constant.forDouble(val);
    }

    private void moveConstantToRight(Op2 x) {
        if (x.x().isConstant() && Bytecodes.isCommutative(x.opcode())) {
            x.swapOperands();
        }
    }

    private void visitOp2(Op2 i) {
        Value x = i.x();
        Value y = i.y();

        if (x == y) {
            // the left and right operands are the same value, try reducing some operations
            switch (i.opcode()) {
                case Bytecodes.ISUB: setIntConstant(0); return;
                case Bytecodes.LSUB: setLongConstant(0); return;
                case Bytecodes.IAND: // fall through
                case Bytecodes.LAND: // fall through
                case Bytecodes.IOR:  // fall through
                case Bytecodes.LOR: setCanonical(x); return;
                case Bytecodes.IXOR: setIntConstant(0); return;
                case Bytecodes.LXOR: setLongConstant(0); return;
            }
        }

        CiKind xt = x.type();
        if (x.isConstant() && y.isConstant()) {
            // both operands are constants, try constant folding
            switch (xt) {
                case Int: {
                    Integer val = Bytecodes.foldIntOp2(i.opcode(), x.asConstant().asInt(), y.asConstant().asInt());
                    if (val != null) {
                        setIntConstant(val); // the operation was successfully folded to an int
                        return;
                    }
                    break;
                }
                case Long: {
                    Long val = Bytecodes.foldLongOp2(i.opcode(), x.asConstant().asLong(), y.asConstant().asLong());
                    if (val != null) {
                        setLongConstant(val); // the operation was successfully folded to a long
                        return;
                    }
                    break;
                }
                case Float: {
                    if (C1XOptions.CanonicalizeFloatingPoint) {
                        // try to fold a floating point operation
                        Float val = Bytecodes.foldFloatOp2(i.opcode(), x.asConstant().asFloat(), y.asConstant().asFloat());
                        if (val != null) {
                            setFloatConstant(val); // the operation was successfully folded to a float
                            return;
                        }
                    }
                    break;
                }
                case Double: {
                    if (C1XOptions.CanonicalizeFloatingPoint) {
                        // try to fold a floating point operation
                        Double val = Bytecodes.foldDoubleOp2(i.opcode(), x.asConstant().asDouble(), y.asConstant().asDouble());
                        if (val != null) {
                            setDoubleConstant(val); // the operation was successfully folded to a double
                            return;
                        }
                    }
                    break;
                }
            }
        }

        // if there is a constant on the left and the operation is commutative, move it to the right
        moveConstantToRight(i);

        if (i.y().isConstant()) {
            // the right side is a constant, try strength reduction
            switch (xt) {
                case Int: {
                    if (reduceIntOp2(i, i.x(), i.y().asConstant().asInt()) != null) {
                        return;
                    }
                    break;
                }
                case Long: {
                    if (reduceLongOp2(i, i.x(), i.y().asConstant().asLong()) != null) {
                        return;
                    }
                    break;
                }
                // XXX: note that other cases are possible, but harder
                // floating point operations need to be extra careful
            }
        }
        assert Value.sameBasicType(i, canonical);
    }

    private Value reduceIntOp2(Op2 original, Value x, int y) {
        // attempt to reduce a binary operation with a constant on the right
        int opcode = original.opcode();
        switch (opcode) {
            case Bytecodes.IADD: return y == 0 ? setCanonical(x) : null;
            case Bytecodes.ISUB: return y == 0 ? setCanonical(x) : null;
            case Bytecodes.IMUL: {
                if (y == 1) {
                    return setCanonical(x);
                }
                if (y > 0 && (y & y - 1) == 0 && C1XOptions.CanonicalizeMultipliesToShifts) {
                    // strength reduce multiply by power of 2 to shift operation
                    return setCanonical(new ShiftOp(Bytecodes.ISHL, x, intInstr(Util.log2(y))));
                }
                return y == 0 ? setIntConstant(0) : null;
            }
            case Bytecodes.IDIV: return y == 1 ? setCanonical(x) : null;
            case Bytecodes.IREM: return y == 1 ? setCanonical(x) : null;
            case Bytecodes.IAND: {
                if (y == -1) {
                    return setCanonical(x);
                }
                return y == 0 ? setIntConstant(0) : null;
            }
            case Bytecodes.IOR: {
                if (y == -1) {
                    return setIntConstant(-1);
                }
                return y == 0 ? setCanonical(x) : null;
            }
            case Bytecodes.IXOR: return y == 0 ? setCanonical(x) : null;
            case Bytecodes.ISHL: return reduceShift(false, opcode, Bytecodes.IUSHR, x, y);
            case Bytecodes.ISHR: return reduceShift(false, opcode, 0, x, y);
            case Bytecodes.IUSHR: return reduceShift(false, opcode, Bytecodes.ISHL, x, y);
        }
        return null;
    }

    private Value reduceShift(boolean islong, int opcode, int reverse, Value x, long y) {
        int mod = islong ? 0x3f : 0x1f;
        long shift = y & mod;
        if (shift == 0) {
            return setCanonical(x);
        }
        if (x instanceof ShiftOp) {
            // this is a chained shift operation ((e shift e) shift K)
            ShiftOp s = (ShiftOp) x;
            if (s.y().isConstant()) {
                long z = s.y().asConstant().asLong();
                if (s.opcode() == opcode) {
                    // this is a chained shift operation (e >> C >> K)
                    y = y + z;
                    shift = y & mod;
                    if (shift == 0) {
                        return setCanonical(s.x());
                    }
                    // reduce to (e >> (C + K))
                    return setCanonical(new ShiftOp(opcode, s.x(), intInstr((int) shift)));
                }
                if (s.opcode() == reverse && y == z) {
                    // this is a chained shift of the form (e >> K << K)
                    if (islong) {
                        long mask = -1;
                        if (opcode == Bytecodes.LUSHR) {
                            mask = mask >>> y;
                        } else {
                            mask = mask << y;
                        }
                        // reduce to (e & mask)
                        return setCanonical(new LogicOp(Bytecodes.LAND, s.x(), longInstr(mask)));
                    } else {
                        int mask = -1;
                        if (opcode == Bytecodes.IUSHR) {
                            mask = mask >>> y;
                        } else {
                            mask = mask << y;
                        }
                        return setCanonical(new LogicOp(Bytecodes.IAND, s.x(), intInstr(mask)));
                    }
                }
            }
        }
        if (y != shift) {
            // (y & mod) != y
            return setCanonical(new ShiftOp(opcode, x, intInstr((int) shift)));
        }
        return null;
    }

    private Value reduceLongOp2(Op2 original, Value x, long y) {
        // attempt to reduce a binary operation with a constant on the right
        int opcode = original.opcode();
        switch (opcode) {
            case Bytecodes.LADD: return y == 0 ? setCanonical(x) : null;
            case Bytecodes.LSUB: return y == 0 ? setCanonical(x) : null;
            case Bytecodes.LMUL: {
                if (y == 1) {
                    return setCanonical(x);
                }
                if (y > 0 && (y & y - 1) == 0 && C1XOptions.CanonicalizeMultipliesToShifts) {
                    // strength reduce multiply by power of 2 to shift operation
                    return setCanonical(new ShiftOp(Bytecodes.LSHL, x, intInstr(Util.log2(y))));
                }
                return y == 0 ? setLongConstant(0) : null;
            }
            case Bytecodes.LDIV: return y == 1 ? setCanonical(x) : null;
            case Bytecodes.LREM: return y == 1 ? setCanonical(x) : null;
            case Bytecodes.LAND: {
                if (y == -1) {
                    return setCanonical(x);
                }
                return y == 0 ? setLongConstant(0) : null;
            }
            case Bytecodes.LOR: {
                if (y == -1) {
                    return setLongConstant(-1);
                }
                return y == 0 ? setCanonical(x) : null;
            }
            case Bytecodes.LXOR: return y == 0 ? setCanonical(x) : null;
            case Bytecodes.LSHL: return reduceShift(true, opcode, Bytecodes.LUSHR, x, y);
            case Bytecodes.LSHR: return reduceShift(true, opcode, 0, x, y);
            case Bytecodes.LUSHR: return reduceShift(true, opcode, Bytecodes.LSHL, x, y);
        }
        return null;
    }

    private boolean inCurrentBlock(Value x) {
        if (x instanceof Instruction) {
            Instruction i = (Instruction) x;
            int max = 4; // XXX: anything special about 4? seems like a tunable heuristic
            while (max > 0 && i != null && !(i instanceof BlockEnd)) {
                i = i.next();
                max--;
            }
            return i == null;
        }
        return true;
    }

    private Value eliminateNarrowing(CiKind type, Convert c) {
        Value nv = null;
        switch (c.opcode()) {
            case Bytecodes.I2B:
                if (type == CiKind.Byte) {
                    nv = c.value();
                }
                break;
            case Bytecodes.I2S:
                if (type == CiKind.Short || type == CiKind.Byte) {
                    nv = c.value();
                }
                break;
            case Bytecodes.I2C:
                if (type == CiKind.Char || type == CiKind.Byte) {
                    nv = c.value();
                }
                break;
        }
        return nv;
    }

    @Override
    public void visitLoadField(LoadField i) {
        if (i.isStatic() && i.isLoaded() && C1XOptions.CanonicalizeConstantFields) {
            // only try to canonicalize static field loads
            RiField field = i.field();
            if (field.isConstant()) {
                setConstant(field.constantValue());
            }
        }
    }

    @Override
    public void visitStoreField(StoreField i) {
        if (C1XOptions.CanonicalizeNarrowingInStores) {
            // Eliminate narrowing conversions emitted by javac which are unnecessary when
            // writing the value to a field that is packed
            Value v = i.value();
            if (v instanceof Convert) {
                Value nv = eliminateNarrowing(i.field().basicType(), (Convert) v);
                // limit this optimization to the current basic block
                // XXX: why is this limited to the current block?
                if (nv != null && inCurrentBlock(v)) {
                    setCanonical(new StoreField(i.object(), i.field(), nv, i.isStatic(),
                                                i.stateBefore(), i.isLoaded(), i.cpi, i.constantPool));
                }
            }
        }
    }

    @Override
    public void visitArrayLength(ArrayLength i) {
        // we can compute the length of the array statically if the object
        // is a NewArray of a constant, or if the object is a constant reference
        // (either by itself or loaded from a constant value field)
        Value array = i.array();
        if (array instanceof NewArray) {
            // the array is a NewArray; check if it has a constant length
            NewArray newArray = (NewArray) array;
            Value length = newArray.length();
            if (length instanceof Constant) {
                // note that we don't use the Constant instruction itself
                // as that would cause problems with liveness later
                int actualLength = length.asConstant().asInt();
                setIntConstant(actualLength);
            }
        } else if (array instanceof LoadField) {
            // the array is a load of a field; check if it is a constant
            RiField field = ((LoadField) array).field();
            if (field.isConstant() && field.isStatic()) {
                Object obj = field.constantValue().asObject();
                if (obj != null) {
                    setIntConstant(java.lang.reflect.Array.getLength(obj));
                }
            }
        } else if (array.isConstant()) {
            // the array itself is a constant object reference
            Object obj = array.asConstant().asObject();
            if (obj != null) {
                setIntConstant(java.lang.reflect.Array.getLength(obj));
            }
        }
    }

    @Override
    public void visitStoreIndexed(StoreIndexed i) {
        if (C1XOptions.CanonicalizeNarrowingInStores) {
            // Eliminate narrowing conversions emitted by javac which are unnecessary when
            // writing the value to an array (which is packed)
            Value v = i.value();
            if (v instanceof Convert) {
                Value nv = eliminateNarrowing(i.elementType(), (Convert) v);
                if (nv != null && inCurrentBlock(v)) {
                    setCanonical(new StoreIndexed(i.array(), i.index(), i.length(), i.elementType(), nv, i.stateBefore()));
                }
            }
        }
    }

    @Override
    public void visitNegateOp(NegateOp i) {
        CiKind vt = i.x().type();
        Value v = i.x();
        if (i.x().isConstant()) {
            switch (vt) {
                case Int: setIntConstant(-v.asConstant().asInt()); break;
                case Long: setLongConstant(-v.asConstant().asLong()); break;
                case Float: setFloatConstant(-v.asConstant().asFloat()); break;
                case Double: setDoubleConstant(-v.asConstant().asDouble()); break;
            }
        }
        assert vt == canonical.type();
    }

    @Override
    public void visitArithmeticOp(ArithmeticOp i) {
        visitOp2(i);
    }

    @Override
    public void visitShiftOp(ShiftOp i) {
        visitOp2(i);
    }

    @Override
    public void visitLogicOp(LogicOp i) {
        visitOp2(i);
    }

    @Override
    public void visitCompareOp(CompareOp i) {
        // we can reduce a compare op if the two inputs are the same,
        // or if both are constants
        Value x = i.x();
        Value y = i.y();
        CiKind xt = x.type();
        if (x == y) {
            // x and y are generated by the same instruction
            switch (xt) {
                case Long: setIntConstant(0); return;
                case Float:
                    if (x.isConstant()) {
                        float xval = x.asConstant().asFloat(); // get the actual value of x (and y since x == y)
                        Integer val = Bytecodes.foldFloatCompare(i.opcode(), xval, xval);
                        assert val != null : "invalid opcode in float compare op";
                        setIntConstant(val);
                        return;
                    }
                    break;
                case Double:
                    if (x.isConstant()) {
                        double xval = x.asConstant().asDouble(); // get the actual value of x (and y since x == y)
                        Integer val = Bytecodes.foldDoubleCompare(i.opcode(), xval, xval);
                        assert val != null : "invalid opcode in double compare op";
                        setIntConstant(val);
                        return;
                    }
                    break;
                // note that there are no integer CompareOps
            }
        }
        if (x.isConstant() && y.isConstant()) {
            // both x and y are constants
            switch (xt) {
                case Long:
                    setIntConstant(Bytecodes.foldLongCompare(x.asConstant().asLong(), y.asConstant().asLong()));
                    break;
                case Float: {
                    Integer val = Bytecodes.foldFloatCompare(i.opcode(), x.asConstant().asFloat(), y.asConstant().asFloat());
                    assert val != null : "invalid opcode in float compare op";
                    setIntConstant(val);
                    break;
                }
                case Double: {
                    Integer val = Bytecodes.foldDoubleCompare(i.opcode(), x.asConstant().asDouble(), y.asConstant().asDouble());
                    assert val != null : "invalid opcode in float compare op";
                    setIntConstant(val);
                    break;
                }
            }
        }
        assert Value.sameBasicType(i, canonical);
    }

    @Override
    public void visitIfOp(IfOp i) {
        moveConstantToRight(i);
    }

    @Override
    public void visitConvert(Convert i) {
        Value v = i.value();
        if (v.isConstant()) {
            // fold conversions between primitive types
            // Checkstyle: stop
            switch (i.opcode()) {
                case Bytecodes.I2B: setIntConstant   ((byte)   v.asConstant().asInt()); return;
                case Bytecodes.I2S: setIntConstant   ((short)  v.asConstant().asInt()); return;
                case Bytecodes.I2C: setIntConstant   ((char)   v.asConstant().asInt()); return;
                case Bytecodes.I2L: setLongConstant  (         v.asConstant().asInt()); return;
                case Bytecodes.I2F: setFloatConstant (         v.asConstant().asInt()); return;
                case Bytecodes.L2I: setIntConstant   ((int)    v.asConstant().asLong()); return;
                case Bytecodes.L2F: setFloatConstant (         v.asConstant().asLong()); return;
                case Bytecodes.L2D: setDoubleConstant(         v.asConstant().asLong()); return;
                case Bytecodes.F2D: setDoubleConstant(         v.asConstant().asFloat()); return;
                case Bytecodes.F2I: setIntConstant   ((int)    v.asConstant().asFloat()); return;
                case Bytecodes.F2L: setLongConstant  ((long)   v.asConstant().asFloat()); return;
                case Bytecodes.D2F: setFloatConstant ((float)  v.asConstant().asDouble()); return;
                case Bytecodes.D2I: setIntConstant   ((int)    v.asConstant().asDouble()); return;
                case Bytecodes.D2L: setLongConstant  ((long)   v.asConstant().asDouble()); return;
            }
            // Checkstyle: resume
        }

        CiKind type = CiKind.Illegal;
        if (v instanceof LoadField) {
            // remove redundant conversions from field loads of the correct type
            type = ((LoadField) v).field().basicType();
        } else if (v instanceof LoadIndexed) {
            // remove redundant conversions from array loads of the correct type
            type = ((LoadIndexed) v).elementType();
        } else if (v instanceof Convert) {
            // remove chained redundant conversions
            Convert c = (Convert) v;
            switch (c.opcode()) {
                case Bytecodes.I2B: type = CiKind.Byte; break;
                case Bytecodes.I2S: type = CiKind.Short; break;
                case Bytecodes.I2C: type = CiKind.Char; break;
            }
        }

        if (type != CiKind.Illegal) {
            // if any of the above matched
            switch (i.opcode()) {
                case Bytecodes.I2B:
                    if (type == CiKind.Byte) {
                        setCanonical(v);
                    }
                    break;
                case Bytecodes.I2S:
                    if (type == CiKind.Byte || type == CiKind.Short) {
                        setCanonical(v);
                    }
                    break;
                case Bytecodes.I2C:
                    if (type == CiKind.Char) {
                        setCanonical(v);
                    }
                    break;
            }
        }

        if (v instanceof Op2) {
            // check if the operation was IAND with a constant; it may have narrowed the value already
            Op2 op = (Op2) v;
            // constant should be on right hand side if there is one
            if (op.opcode() == Bytecodes.IAND && op.y().isConstant()) {
                int safebits = 0;
                int mask = op.y().asConstant().asInt();
                switch (i.opcode()) {
                    case Bytecodes.I2B: safebits = 0x7f; break;
                    case Bytecodes.I2S: safebits = 0x7fff; break;
                    case Bytecodes.I2C: safebits = 0xffff; break;
                }
                if (safebits != 0 && (mask & ~safebits) == 0) {
                    // the mask already cleared all the upper bits necessary.
                    setCanonical(v);
                }
            }
        }
    }

    @Override
    public void visitNullCheck(NullCheck i) {
        Value o = i.object();
        if (o.isNonNull()) {
            // if the instruction producing the object was a new, no check is necessary
            setCanonical(o);
        } else if (o.isConstant()) {
            // if the object is a constant, check if it is nonnull
            CiConstant c = o.asConstant();
            if (c.basicType.isObject() && c.asObject() != null) {
                setCanonical(o);
            }
        }
    }

    @Override
    public void visitInvoke(Invoke i) {
        if (C1XOptions.CanonicalizeFoldableMethods) {
            RiMethod method = i.target();
            if (method.isLoaded()) {
                // only try to fold resolved method invocations
                CiConstant result = foldInvocation(i.target(), i.arguments());
                if (result != null) {
                    // folding was successful
                    CiKind basicType = method.signatureType().returnBasicType();
                    setCanonical(new Constant(new CiConstant(basicType, result)));
                }
            }
        }
    }

    @Override
    public void visitCheckCast(CheckCast i) {
        // we can remove a redundant check cast if it is an object constant or the exact type is known
        if (i.targetClass().isLoaded()) {
            Value o = i.object();
            RiType type = o.exactType();
            if (type == null) {
                type = o.declaredType();
            }
            if (type != null && type.isLoaded() && type.isSubtypeOf(i.targetClass())) {
                // cast is redundant if exact type or declared type is already a subtype of the target type
                setCanonical(o);
            }
            if (o.isConstant()) {
                final Object obj = o.asConstant().asObject();
                if (obj == null) {
                    // checkcast of null is null
                    setCanonical(o);
                } else if (C1XOptions.SupportObjectConstants && C1XOptions.CanonicalizeObjectCheckCast) {
                    if (i.targetClass().isInstance(obj)) {
                        // fold the cast if it will succeed
                        setCanonical(o);
                    }
                }
            }
        }
    }

    @Override
    public void visitInstanceOf(InstanceOf i) {
        // we can fold an instanceof if it is an object constant or the exact type is known
        if (i.targetClass().isLoaded()) {
            Value o = i.object();
            RiType exact = o.exactType();
            if (exact != null && exact.isLoaded()) {
                setIntConstant(exact.isSubtypeOf(i.targetClass()) ? 1 : 0);
            }
            if (o.isConstant()) {
                final Object obj = o.asConstant().asObject();
                if (obj == null) {
                    // instanceof of null is false
                    setIntConstant(0);
                } else if (C1XOptions.SupportObjectConstants && C1XOptions.CanonicalizeObjectInstanceOf) {
                    // fold the instanceof test
                    setIntConstant(i.targetClass().isInstance(obj) ? 1 : 0);
                }
            }
        }
    }

    @Override
    public void visitIntrinsic(Intrinsic i) {
        if (!C1XOptions.CanonicalizeIntrinsics) {
            return;
        }
        if (!foldIntrinsic(i)) {
            // folding did not work, try recognizing special intrinsics
            reduceIntrinsic(i);
        }
        assert Value.sameBasicType(i, canonical);
    }

    private void reduceIntrinsic(Intrinsic i) {
        Value[] args = i.arguments();
        C1XIntrinsic intrinsic = i.intrinsic();
        if (C1XOptions.IntrinsifyClassOps && intrinsic == C1XIntrinsic.java_lang_Class$isInstance) {
            // try to convert a call to Class.isInstance() into an InstanceOf
            RiType type = asRiType(args[0]);
            if (type != null) {
                setCanonical(new InstanceOf(type, Constant.forObject(type.getEncoding(RiType.Representation.TypeInfo)), args[1], i.stateBefore()));
                return;
            }
        }
        if (C1XOptions.IntrinsifyArrayOps && intrinsic == C1XIntrinsic.java_lang_reflect_Array$newArray) {
            // try to convert a call to Array.newInstance() into a NewObjectArray or NewTypeArray
            RiType type = asRiType(args[0]);
            if (type != null) {
                if (type.basicType() == CiKind.Object) {
                    setCanonical(new NewObjectArray(type, args[1], i.stateBefore(), '\0', null));
                } else {
                    setCanonical(new NewTypeArray(args[1], type.basicType(), i.stateBefore()));
                }
                return;
            }
        }
        assert Value.sameBasicType(i, canonical);
    }

    private boolean foldIntrinsic(Intrinsic i) {
        Value[] args = i.arguments();
        for (Value arg : args) {
            if (arg != null && !arg.isConstant()) {
                // one input is not constant, give up
                return true;
            }
        }
        switch (i.intrinsic()) {
            // do not use reflection here due to efficiency and potential bootstrap problems
            case java_lang_Object$hashCode: {
                Object object = argAsObject(args, 0);
                if (object != null) {
                    setIntConstant(System.identityHashCode(object));
                }
                return true;
            }
            case java_lang_Object$getClass: {
                Object object = argAsObject(args, 0);
                if (object != null) {
                    setObjectConstant(object.getClass());
                }
                return true;
            }

            // java.lang.Class
            case java_lang_Class$isAssignableFrom: {
                Class<?> javaClass = argAsClass(args, 0);
                Class<?> otherClass = argAsClass(args, 1);
                if (javaClass != null && otherClass != null) {
                    setBooleanConstant(javaClass.isAssignableFrom(otherClass));
                }
                return true;
            }
            case java_lang_Class$isInstance: {
                Class<?> javaClass = argAsClass(args, 0);
                Object object = argAsObject(args, 1);
                if (javaClass != null && object != null) {
                    setBooleanConstant(javaClass.isInstance(object));
                }
                return true;
            }
            case java_lang_Class$getModifiers: {
                Class<?> javaClass = argAsClass(args, 0);
                if (javaClass != null) {
                    setIntConstant(javaClass.getModifiers());
                }
                return true;
            }
            case java_lang_Class$isInterface: {
                Class<?> javaClass = argAsClass(args, 0);
                if (javaClass != null) {
                    setBooleanConstant(javaClass.isInterface());
                }
                return true;
            }
            case java_lang_Class$isArray: {
                Class<?> javaClass = argAsClass(args, 0);
                if (javaClass != null) {
                    setBooleanConstant(javaClass.isArray());
                }
                return true;
            }
            case java_lang_Class$isPrimitive: {
                Class<?> javaClass = argAsClass(args, 0);
                if (javaClass != null) {
                    setBooleanConstant(javaClass.isPrimitive());
                }
                return true;
            }
            case java_lang_Class$getSuperclass: {
                Class<?> javaClass = argAsClass(args, 0);
                if (javaClass != null) {
                    setObjectConstant(javaClass.getSuperclass());
                }
                return true;
            }
            case java_lang_Class$getComponentType: {
                Class<?> javaClass = argAsClass(args, 0);
                if (javaClass != null) {
                    setObjectConstant(javaClass.getComponentType());
                }
                return true;
            }

            // java.lang.String
            case java_lang_String$compareTo: {
                String s1 = argAsString(args, 0);
                String s2 = argAsString(args, 1);
                if (s1 != null && s2 != null) {
                    setIntConstant(s1.compareTo(s2));
                }
                return true;
            }
            case java_lang_String$indexOf: {
                String s1 = argAsString(args, 0);
                String s2 = argAsString(args, 1);
                if (s1 != null && s2 != null) {
                    setIntConstant(s1.indexOf(s2));
                }
                return true;
            }
            case java_lang_String$equals: {
                String s1 = argAsString(args, 0);
                String s2 = argAsString(args, 1);
                if (s1 != null && s2 != null) {
                    setBooleanConstant(s1.equals(s2));
                }
                return true;
            }

            // java.lang.Math
            case java_lang_Math$abs:   setDoubleConstant(Math.abs(argAsDouble(args, 0))); return true;
            case java_lang_Math$sin:   setDoubleConstant(Math.sin(argAsDouble(args, 0))); return true;
            case java_lang_Math$cos:   setDoubleConstant(Math.cos(argAsDouble(args, 0))); return true;
            case java_lang_Math$tan:   setDoubleConstant(Math.tan(argAsDouble(args, 0))); return true;
            case java_lang_Math$atan2: setDoubleConstant(Math.atan2(argAsDouble(args, 0), argAsDouble(args, 2))); return true;
            case java_lang_Math$sqrt:  setDoubleConstant(Math.sqrt(argAsDouble(args, 0))); return true;
            case java_lang_Math$log:   setDoubleConstant(Math.log(argAsDouble(args, 0))); return true;
            case java_lang_Math$log10: setDoubleConstant(Math.log10(argAsDouble(args, 0))); return true;
            case java_lang_Math$pow:   setDoubleConstant(Math.pow(argAsDouble(args, 0), argAsDouble(args, 2))); return true;
            case java_lang_Math$exp:   setDoubleConstant(Math.exp(argAsDouble(args, 0))); return true;
            case java_lang_Math$min:   setIntConstant(Math.min(argAsInt(args, 0), argAsInt(args, 1))); return true;
            case java_lang_Math$max:   setIntConstant(Math.max(argAsInt(args, 0), argAsInt(args, 1))); return true;

            // java.lang.Float
            case java_lang_Float$floatToRawIntBits: setIntConstant(Float.floatToRawIntBits(argAsFloat(args, 0))); return true;
            case java_lang_Float$floatToIntBits: setIntConstant(Float.floatToIntBits(argAsFloat(args, 0))); return true;
            case java_lang_Float$intBitsToFloat: setFloatConstant(Float.intBitsToFloat(argAsInt(args, 0))); return true;

            // java.lang.Double
            case java_lang_Double$doubleToRawLongBits: setLongConstant(Double.doubleToRawLongBits(argAsDouble(args, 0))); return true;
            case java_lang_Double$doubleToLongBits: setLongConstant(Double.doubleToLongBits(argAsDouble(args, 0))); return true;
            case java_lang_Double$longBitsToDouble: setDoubleConstant(Double.longBitsToDouble(argAsLong(args, 0))); return true;

            // java.lang.Integer
            case java_lang_Integer$bitCount: setIntConstant(Integer.bitCount(argAsInt(args, 0))); return true;
            case java_lang_Integer$reverseBytes: setIntConstant(Integer.reverseBytes(argAsInt(args, 0))); return true;

            // java.lang.Long
            case java_lang_Long$bitCount: setIntConstant(Long.bitCount(argAsLong(args, 0))); return true;
            case java_lang_Long$reverseBytes: setLongConstant(Long.reverseBytes(argAsLong(args, 0))); return true;

            // java.lang.System
            case java_lang_System$identityHashCode: {
                Object object = argAsObject(args, 0);
                if (object != null) {
                    setIntConstant(System.identityHashCode(object));
                }
                return true;
            }

            // java.lang.reflect.Array
            case java_lang_reflect_Array$getLength: {
                Object object = argAsObject(args, 0);
                if (object != null && object.getClass().isArray()) {
                    setIntConstant(Array.getLength(object));
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void visitIf(If i) {
        if (i.x().isConstant()) {
            // move constant to the right
            i.swapOperands();
        }
        Value l = i.x();
        Value r = i.y();

        if (l == r) {
            // this is a comparison of x op x
            reduceReflexiveIf(i);
            return;
        }

        CiKind rt = r.type();

        Condition ifcond = i.condition();
        if (l.isConstant() && r.isConstant()) {
            // fold comparisons between constants and convert to Goto
            Boolean result = ifcond.foldCondition(l.asConstant(), r.asConstant());
            if (result != null) {
                setCanonical(new Goto(i.successor(result), i.stateAfter(), i.isSafepoint()));
                return;
            }
        }

        if (r.isConstant() && rt.isInt()) {
            // attempt to reduce comparisons with constant on right side
            if (l instanceof CompareOp) {
                // attempt to reduce If ((a cmp b) op const)
                reduceIfCompareOpConstant(i, r.asConstant());
            }
        }

        if (isNullConstant(r) && l.isNonNull()) {
            // this is a comparison of null against something that is not null
            if (ifcond == Condition.eql) {
                // new() == null is always false
                setCanonical(new Goto(i.falseSuccessor(), i.stateAfter(), i.isSafepoint()));
            } else if (ifcond == Condition.neq) {
                // new() != null is always true
                setCanonical(new Goto(i.trueSuccessor(), i.stateAfter(), i.isSafepoint()));
            }
        }
    }

    private boolean isNullConstant(Value r) {
        return r.isConstant() && r.type().isObject() && r.asConstant().asObject() == null;
    }

    private void reduceIfCompareOpConstant(If i, CiConstant rtc) {
        Condition ifcond = i.condition();
        Value l = i.x();
        CompareOp cmp = (CompareOp) l;
        boolean unorderedIsLess = cmp.opcode() == Bytecodes.FCMPL || cmp.opcode() == Bytecodes.DCMPL;
        BlockBegin lssSucc = i.successor(ifcond.foldCondition(CiConstant.forInt(-1), rtc));
        BlockBegin eqlSucc = i.successor(ifcond.foldCondition(CiConstant.forInt(0), rtc));
        BlockBegin gtrSucc = i.successor(ifcond.foldCondition(CiConstant.forInt(1), rtc));
        BlockBegin nanSucc = unorderedIsLess ? lssSucc : gtrSucc;
        // Note: At this point all successors (lssSucc, eqlSucc, gtrSucc, nanSucc) are
        //       equal to x->tsux() or x->fsux(). Furthermore, nanSucc equals either
        //       lssSucc or gtrSucc.
        if (lssSucc == eqlSucc && eqlSucc == gtrSucc) {
            // all successors identical => simplify to: Goto
            setCanonical(new Goto(lssSucc, i.stateAfter(), i.isSafepoint()));
        } else {
            // two successors differ and two successors are the same => simplify to: If (x cmp y)
            // determine new condition & successors
            Condition cond;
            BlockBegin tsux;
            BlockBegin fsux;
            if (lssSucc == eqlSucc) {
                cond = Condition.leq;
                tsux = lssSucc;
                fsux = gtrSucc;
            } else if (lssSucc == gtrSucc) {
                cond = Condition.neq;
                tsux = lssSucc;
                fsux = eqlSucc;
            } else if (eqlSucc == gtrSucc) {
                cond = Condition.geq;
                tsux = eqlSucc;
                fsux = lssSucc;
            } else {
                throw Util.shouldNotReachHere();
            }
            // TODO: the state after is incorrect here: should it be preserved from the original if?
            If canon = new If(cmp.x(), cond, nanSucc == tsux, cmp.y(), tsux, fsux, cmp.stateBefore(), i.isSafepoint());
            if (cmp.x() == cmp.y()) {
                // re-canonicalize the new if
                visitIf(canon);
            } else {
                setCanonical(canon);
            }
        }
    }

    private void reduceReflexiveIf(If i) {
        // simplify reflexive comparisons If (x op x) to Goto
        BlockBegin succ;
        switch (i.condition()) {
            case eql: succ = i.successor(true); break;
            case neq: succ = i.successor(false); break;
            case lss: succ = i.successor(false); break;
            case leq: succ = i.successor(true); break;
            case gtr: succ = i.successor(false); break;
            case geq: succ = i.successor(true); break;
            default:
                throw Util.shouldNotReachHere();
        }
        setCanonical(new Goto(succ, i.stateAfter(), i.isSafepoint()));
    }

    @Override
    public void visitTableSwitch(TableSwitch i) {
        Value v = i.value();
        if (v.isConstant()) {
            // fold a table switch over a constant by replacing it with a goto
            int val = v.asConstant().asInt();
            BlockBegin succ = i.defaultSuccessor();
            if (val >= i.lowKey() && val <= i.highKey()) {
                succ = i.successors().get(val - i.lowKey());
            }
            setCanonical(new Goto(succ, i.stateAfter(), i.isSafepoint()));
            return;
        }
        int max = i.numberOfCases();
        if (max == 0) {
            // replace switch with Goto
            if (v instanceof Instruction) {
                // TODO: is it necessary to add the instruction explicitly?
                addInstr((Instruction) v);
            }
            setCanonical(new Goto(i.defaultSuccessor(), i.stateAfter(), i.isSafepoint()));
            return;
        }
        if (max == 1) {
            // replace switch with If
            Constant key = intInstr(i.lowKey());
            If newIf = new If(v, Condition.eql, false, key, i.successors().get(0), i.defaultSuccessor(), null, i.isSafepoint());
            newIf.setStateAfter(i.stateAfter());
            setCanonical(newIf);
        }
    }

    @Override
    public void visitLookupSwitch(LookupSwitch i) {
        Value v = i.value();
        if (v.isConstant()) {
            // fold a lookup switch over a constant by replacing it with a goto
            int val = v.asConstant().asInt();
            BlockBegin succ = i.defaultSuccessor();
            for (int j = 0; j < i.numberOfCases(); j++) {
                if (val == i.keyAt(j)) {
                    succ = i.successors().get(j);
                    break;
                }
            }
            setCanonical(new Goto(succ, i.stateAfter(), i.isSafepoint()));
            return;
        }
        int max = i.numberOfCases();
        if (max == 1) {
            // replace switch with Goto
            if (v instanceof Instruction) {
                addInstr((Instruction) v); // the value expression may produce side effects
            }
            setCanonical(new Goto(i.defaultSuccessor(), i.stateAfter(), i.isSafepoint()));
            return;
        }
        if (max == 2) {
            // replace switch with If
            Constant key = intInstr(i.keyAt(0));
            If newIf = new If(v, Condition.eql, false, key, i.successors().get(0), i.defaultSuccessor(), null, i.isSafepoint());
            newIf.setStateAfter(i.stateAfter());
            setCanonical(newIf);
        }
    }

    private void visitUnsafeRawOp(UnsafeRawOp i) {
        if (i.base() instanceof ArithmeticOp) {
            // if the base is an arithmetic op, try reducing
            ArithmeticOp root = (ArithmeticOp) i.base();
            if (!root.isLive() && root.opcode() == Bytecodes.LADD) {
                // match unsafe(x + y) if the x + y is not pinned
                // try reducing (x + y) and (y + x)
                Value y = root.y();
                Value x = root.x();
                if (reduceRawOp(i, x, y) || reduceRawOp(i, y, x)) {
                    // the operation was reduced
                    return;
                }
                if (y instanceof Convert) {
                    // match unsafe(x + (long) y)
                    Convert convert = (Convert) y;
                    if (convert.opcode() == Bytecodes.I2L && convert.value().type().isInt()) {
                        // the conversion is redundant
                        setUnsafeRawOp(i, x, convert.value(), 0);
                    }
                }
            }
        }
    }

    private boolean reduceRawOp(UnsafeRawOp i, Value base, Value index) {
        if (index instanceof Convert) {
            // skip any conversion operations
            index = ((Convert) index).value();
        }
        if (index instanceof ShiftOp) {
            // try to match the index as a shift by a constant
            ShiftOp shift = (ShiftOp) index;
            CiKind st = shift.y().type();
            if (shift.y().isConstant() && st.isInt()) {
                int val = shift.y().asConstant().asInt();
                switch (val) {
                    case 0: // fall through
                    case 1: // fall through
                    case 2: // fall through
                    case 3: return setUnsafeRawOp(i, base, shift.x(), val);
                }
            }
        }
        if (index instanceof ArithmeticOp) {
            // try to match the index as a multiply by a constant
            // note that this case will not happen if C1XOptions.CanonicalizeMultipliesToShifts is true
            ArithmeticOp arith = (ArithmeticOp) index;
            CiKind st = arith.y().type();
            if (arith.opcode() == Bytecodes.IMUL && arith.y().isConstant() && st.isInt()) {
                int val = arith.y().asConstant().asInt();
                switch (val) {
                    case 1: return setUnsafeRawOp(i, base, arith.x(), 0);
                    case 2: return setUnsafeRawOp(i, base, arith.x(), 1);
                    case 4: return setUnsafeRawOp(i, base, arith.x(), 2);
                    case 8: return setUnsafeRawOp(i, base, arith.x(), 3);
                }
            }
        }

        return false;
    }

    private boolean setUnsafeRawOp(UnsafeRawOp i, Value base, Value index, int log2scale) {
        i.setBase(base);
        i.setIndex(index);
        i.setLog2Scale(log2scale);
        return true;
    }

    @Override
    public void visitUnsafeGetRaw(UnsafeGetRaw i) {
        if (C1XOptions.CanonicalizeUnsafes) {
            visitUnsafeRawOp(i);
        }
    }

    @Override
    public void visitUnsafePutRaw(UnsafePutRaw i) {
        if (C1XOptions.CanonicalizeUnsafes) {
            visitUnsafeRawOp(i);
        }
    }

    private Object argAsObject(Value[] args, int index) {
        return args[index].asConstant().asObject();
    }

    private Class<?> argAsClass(Value[] args, int index) {
        return (Class<?>) args[index].asConstant().asObject();
    }

    private String argAsString(Value[] args, int index) {
        return (String) args[index].asConstant().asObject();
    }

    private double argAsDouble(Value[] args, int index) {
        return args[index].asConstant().asDouble();
    }

    private float argAsFloat(Value[] args, int index) {
        return args[index].asConstant().asFloat();
    }

    private int argAsInt(Value[] args, int index) {
        return args[index].asConstant().asInt();
    }

    private long argAsLong(Value[] args, int index) {
        return args[index].asConstant().asLong();
    }

    public static CiConstant foldInvocation(RiMethod method, Value[] args) {
        Method reflectMethod = C1XIntrinsic.getFoldableMethod(method);
        if (reflectMethod != null) {
            // the method is foldable. check that all input arguments are constants
            for (Value a : args) {
                if (a != null && !a.isConstant()) {
                    return null;
                }
            }
            // build the argument list
            Object recvr;
            Object[] argArray = NO_ARGUMENTS;
            if (method.isStatic()) {
                // static method invocation
                recvr = null;
                if (args.length > 0) {
                    ArrayList<Object> list = new ArrayList<Object>();
                    for (Value a : args) {
                        if (a != null) {
                            list.add(a.asConstant().boxedValue());
                        }
                    }
                    argArray = list.toArray();
                }
            } else {
                // instance method invocation
                recvr = args[0].asConstant().asObject();
                if (args.length > 1) {
                    ArrayList<Object> list = new ArrayList<Object>();
                    for (int i = 1; i < args.length; i++) {
                        Value a = args[i];
                        if (a != null) {
                            list.add(a.asConstant().boxedValue());
                        }
                    }
                    argArray = list.toArray();
                }
            }
            try {
                // attempt to invoke the method
                Object result = reflectMethod.invoke(recvr, argArray);
                CiKind basicType = method.signatureType().returnBasicType();
                // set the result of this instruction to be the result of invocation
                C1XMetrics.MethodsFolded++;
                return new CiConstant(basicType, result);
                // note that for void, we will have a void constant with value null
            } catch (IllegalAccessException e) {
                // folding failed; too bad
            } catch (InvocationTargetException e) {
                // folding failed; too bad
            }
        }
        return null;
    }

    private RiType asRiType(Value x) {
        if (x.isConstant()) {
            Object o = x.asConstant().asObject();
            if (o instanceof Class<?>) {
                return runtime.getRiType((Class<?>) o);
            }
        }
        return null;
    }
}
