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
package com.sun.c1x.alloc;

import java.util.*;

import com.sun.c1x.ci.*;
import com.sun.c1x.debug.*;
import com.sun.c1x.lir.*;
import com.sun.c1x.util.*;

/**
 *
 * @author Thomas Wuerthinger
 */
public class Interval {

    enum IntervalUseKind {
        // priority of use kinds must be ascending
        noUse("N"), loopEndMarker("L"), shouldHaveRegister("S"), mustHaveRegister("M");
        private String name;

        private IntervalUseKind(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    enum IntervalKind {
        fixedKind, // interval pre-colored by LIR_Generator
        anyKind // no register/memory allocated by LIR_Generator
    }

    // during linear scan an interval is in one of four states in
    enum IntervalState {
        unhandledState("unhandled"), // unhandled state (not processed yet)
        activeState("active"), // life and is in a physical register
        inactiveState("inactive"), // in a life time hole and is in a physical register
        handledState("handled"), // spilled or not life again
        invalidState("invalid");

        private String name;

        private IntervalState(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    enum IntervalSpillState {
        noDefinitionFound("no definition"), // starting state of calculation: no definition found yet
        oneDefinitionFound("no spill store"), // one definition has already been found.
        // Note: two consecutive definitions are treated as one (e.g. consecutive move and add because of two-operand
        // LIR form)
        // the position of this definition is stored in definitionPos
        oneMoveInserted ("one spill store"), // one spill move has already been inserted.
        storeAtDefinition ("store at definition"), // the interval should be stored immediately after its definition because otherwise
        // there would be multiple redundant stores
        startInMemory("start in memory"), // the interval starts in memory (e.g. method parameter), so a store is never necessary
        noOptimization("no optimization");
        // the interval has more then one definition (e.g. resulting from phi moves), so stores to memory are not
        // optimized


        private String name;

        private IntervalSpillState(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    int regNum;
    CiKind type; // valid only for virtual registers
    Range first; // sorted list of Ranges
    List<Integer> usePosAndKinds; // sorted list of use-positions and their according use-kinds

    Range current; // interval iteration: the current Range
    Interval next; // interval iteration: sorted list of Intervals (ends with sentinel)
    IntervalState state; // interval iteration: to which set belongs this interval

    int assignedReg;
    int assignedRegHi;

    int cachedTo; // cached value: to of last range (-1: not cached)
    LIROperand cachedOpr;
    CiLocation cachedVmReg;

    Interval splitParent; // the original interval where this interval is derived from
    List<Interval> splitChildren; // list of all intervals that are split off from this interval (only available for
    // split parents)
    Interval currentSplitChild; // the current split child that has been active or inactive last (always stored in split
    // parents)

    int canonicalSpillSlot; // the stack slot where all split parts of this interval are spilled to (always stored in
    // split parents)
    boolean insertMoveWhenActivated; // true if move is inserted between currentSplitChild and this interval when
    // interval gets active the first time
    IntervalSpillState spillState; // for spill move optimization
    int spillDefinitionPos; // position where the interval is defined (if defined only once)
    Interval registerHint; // this interval should be in the same register as the hint interval

    static Interval end() {
        return end;
    }

    // accessors
    int regNum() {
        return regNum;
    }

    void setRegNum(int r) {
        assert regNum == -1 : "cannot change regNum";
        regNum = r;
    }

    CiKind type() {
        assert regNum == -1 || regNum >= CiRegister.vregBase : "cannot access type for fixed interval";
        return type;
    }

    void setType(CiKind type) {
        assert regNum < CiRegister.vregBase || this.type == CiKind.Illegal || this.type == type : "overwriting existing type";
        assert type != CiKind.Boolean && type != CiKind.Byte && type != CiKind.Char : "these basic types should have int type registers";
        this.type = type;
    }

    Range first() {
        return first;
    }

    int from() {
        return first.from();
    }

    int to() {
        if (cachedTo == -1) {
            cachedTo = calcTo();
        }
        assert cachedTo == calcTo() : "invalid cached value";
        return cachedTo;
    }

    int numUsePositions() {
        return usePosAndKinds.size() / 2;
    }

    Interval next() {
        return next;
    }

    void setNext(Interval next) {
        this.next = next;
    }

    int assignedReg() {
        return assignedReg;
    }

    int assignedRegHi() {
        return assignedRegHi;
    }

    void assignReg(int reg) {
        assert reg != 5;
        assignedReg = reg;
        assignedRegHi = LinearScan.getAnyreg();
    }

    void assignReg(int reg, int regHi) {
        assert reg != 5;
        assignedReg = reg;
        assignedRegHi = regHi;
    }

    void setRegisterHint(Interval i) {
        registerHint = i;
    }

    IntervalState state() {
        return state;
    }

    void setState(IntervalState s) {
        state = s;
    }

    // access to split parent and split children
    boolean isSplitParent() {
        return splitParent == this;
    }

    boolean isSplitChild() {
        return splitParent != this;
    }

    Interval splitParent() {
        assert splitParent.isSplitParent() : "must be";
        return splitParent;
    }

    // information stored in split parent, but available for all children
    int canonicalSpillSlot() {
        return splitParent().canonicalSpillSlot;
    }

    void setCanonicalSpillSlot(int slot) {
        assert splitParent().canonicalSpillSlot == -1 : "overwriting existing value";
        splitParent().canonicalSpillSlot = slot;
    }

    Interval currentSplitChild() {
        return splitParent().currentSplitChild;
    }

    void makeCurrentSplitChild() {
        splitParent().currentSplitChild = this;
    }

    boolean insertMoveWhenActivated() {
        return insertMoveWhenActivated;
    }

    void setInsertMoveWhenActivated(boolean b) {
        insertMoveWhenActivated = b;
    }

    // for spill optimization
    IntervalSpillState spillState() {
        return splitParent().spillState;
    }

    int spillDefinitionPos() {
        return splitParent().spillDefinitionPos;
    }

    void setSpillState(IntervalSpillState state) {
        assert state.ordinal() >= spillState().ordinal() : "state cannot decrease";
        splitParent().spillState = state;
    }

    void setSpillDefinitionPos(int pos) {
        assert spillDefinitionPos() == -1 : "cannot set the position twice";
        splitParent().spillDefinitionPos = pos;
    }

    // returns true if this interval has a shadow copy on the stack that is always correct
    boolean alwaysInMemory() {
        return splitParent().spillState == IntervalSpillState.storeAtDefinition || splitParent().spillState == IntervalSpillState.startInMemory;
    }

    // caching of values that take time to compute and are used multiple times
    LIROperand cachedOpr() {
        return cachedOpr;
    }

    CiLocation cachedVmReg() {
        return cachedVmReg;
    }

    void setCachedOpr(LIROperand opr) {
        cachedOpr = opr;
    }

    void setCachedVmReg(CiLocation reg) {
        cachedVmReg = reg;
    }

    void removeFirstUsePos() {
        Util.truncate(usePosAndKinds, usePosAndKinds.size() - 2);
    }

    // test intersection
    boolean intersects(Interval i) {
        return first.intersects(i.first);
    }

    int intersectsAt(Interval i) {
        return first.intersectsAt(i.first);
    }

    // range iteration
    void rewindRange() {
        current = first;
    }

    void nextRange() {
        assert this != end : "not allowed on sentinel";
        current = current.next();
    }

    int currentFrom() {
        return current.from();
    }

    int currentTo() {
        return current.to();
    }

    boolean currentAtEnd() {
        return current == Range.end();
    }

    boolean currentIntersects(Interval it) {
        return current.intersects(it.current);
    }

    int currentIntersectsAt(Interval it) {
        return current.intersectsAt(it.current);
    }

    // initialize sentinel
    static Interval end = new Interval(-1);

    Interval(int regNum) {

        this.regNum = regNum;
        this.type = CiKind.Illegal;
        this.first = Range.end();
        this.usePosAndKinds = new ArrayList<Integer>(12);
        this.current = Range.end();
        this.next = end;
        this.state = IntervalState.invalidState;
        this.assignedReg = LinearScan.getAnyreg();
        this.assignedRegHi = LinearScan.getAnyreg();
        this.cachedTo = -1;
        this.cachedOpr = LIROperandFactory.IllegalOperand;
        this.cachedVmReg = null; // TODO: Check if to use VMReg.Bad
        this.canonicalSpillSlot = -1;
        this.insertMoveWhenActivated = false;
        this.registerHint = null;
        this.spillState = IntervalSpillState.noDefinitionFound;
        this.spillDefinitionPos = -1;
        splitParent = this;
        currentSplitChild = this;
        splitChildren = new ArrayList<Interval>(4);
    }

    int calcTo() {
        assert first != Range.end() : "interval has no range";

        Range r = first;
        while (r.next() != Range.end()) {
            r = r.next();
        }
        return r.to();
    }

    // consistency check of split-children
    boolean checkSplitChildren() {
        if (splitChildren.size() > 0) {
            assert isSplitParent() : "only split parents can have children";

            for (int i = 0; i < splitChildren.size(); i++) {
                Interval i1 = splitChildren.get(i);

                assert i1.splitParent() == this : "not a split child of this interval";
                assert i1.type() == type() : "must be equal for all split children";
                assert i1.canonicalSpillSlot() == canonicalSpillSlot() : "must be equal for all split children";

                for (int j = i + 1; j < splitChildren.size(); j++) {
                    Interval i2 = splitChildren.get(j);

                    assert i1.regNum() != i2.regNum() : "same register number";

                    if (i1.from() < i2.from()) {
                        assert i1.to() <= i2.from() && i1.to() < i2.to() : "intervals overlapping";
                    } else {
                        assert i2.from() < i1.from() : "intervals start at same opId";
                        assert i2.to() <= i1.from() && i2.to() < i1.to() : "intervals overlapping";
                    }
                }
            }
        }

        return true;
    }

    Interval registerHint(boolean searchSplitChild, LinearScan allocator) {
        if (!searchSplitChild) {
            return registerHint;
        }

        if (registerHint != null) {
            assert registerHint.isSplitParent() : "ony split parents are valid hint registers";

            if (registerHint.assignedReg() >= 0 && registerHint.assignedReg() < allocator.nofRegs) {
                return registerHint;
            } else if (registerHint.splitChildren.size() > 0) {
                // search the first split child that has a register assigned
                int len = registerHint.splitChildren.size();
                for (int i = 0; i < len; i++) {
                    Interval cur = registerHint.splitChildren.get(i);

                    if (cur.assignedReg() >= 0 && cur.assignedReg() < allocator.nofRegs) {
                        return cur;
                    }
                }
            }
        }

        // no hint interval found that has a register assigned
        return null;
    }

    Interval splitChildAtOpId(int opId, LIRVisitState.OperandMode mode, LinearScan allocator) {
        assert isSplitParent() : "can only be called for split parents";
        assert opId >= 0 : "invalid opId (method can not be called for spill moves)";

        Interval result;
        if (splitChildren.size() == 0) {
            result = this;
        } else {
            result = null;
            int len = splitChildren.size();

            // in outputMode, the end of the interval (opId == cur.to()) is not valid
            int toOffset = (mode == LIRVisitState.OperandMode.OutputMode ? 0 : 1);

            int i;
            for (i = 0; i < len; i++) {
                Interval cur = splitChildren.get(i);
                if (cur.from() <= opId && opId < cur.to() + toOffset) {
                    if (i > 0) {
                        // exchange current split child to start of list (faster access for next call)
                        Util.atPutGrow(splitChildren, i, splitChildren.get(0), null);
                        Util.atPutGrow(splitChildren, 0, cur, null);
                    }

                    // interval found
                    result = cur;
                    break;
                }
            }

            assert result != null : "no matching interval found";
            for (i = 0; i < len; i++) {
                Interval tmp = splitChildren.get(i);
                if (tmp != result && tmp.from() <= opId && opId < tmp.to() + toOffset) {
                    TTY.println(String.format("two valid result intervals found for opId %d: %d and %d", opId, result.regNum(), tmp.regNum()));
                    result.print(TTY.out, allocator);
                    tmp.print(TTY.out, allocator);
                    assert false : "two valid result intervals found";
                }
            }
        }

        assert result.covers(opId, mode) : "opId not covered by interval";

        return result;
    }

    // returns the last split child that ends before the given opId
    Interval splitChildBeforeOpId(int opId) {
        assert opId >= 0 : "invalid opId";

        Interval parent = splitParent();
        Interval result = null;

        int len = parent.splitChildren.size();
        assert len > 0 : "no split children available";

        for (int i = len - 1; i >= 0; i--) {
            Interval cur = parent.splitChildren.get(i);
            if (cur.to() <= opId && (result == null || result.to() < cur.to())) {
                result = cur;
            }
        }

        assert result != null : "no split child found";
        return result;
    }

    // checks if opId is covered by any split child
    boolean splitChildCovers(int opId, LIRVisitState.OperandMode mode) {
        assert isSplitParent() : "can only be called for split parents";
        assert opId >= 0 : "invalid opId (method can not be called for spill moves)";

        if (splitChildren.size() == 0) {
            // simple case if interval was not split
            return covers(opId, mode);

        } else {
            // extended case: check all split children
            int len = splitChildren.size();
            for (int i = 0; i < len; i++) {
                Interval cur = splitChildren.get(i);
                if (cur.covers(opId, mode)) {
                    return true;
                }
            }
            return false;
        }
    }

    // Note: use positions are sorted descending . first use has highest index
    int firstUsage(IntervalUseKind minUseKind) {
        assert isVirtualInterval() : "cannot access use positions for fixed intervals";

        for (int i = usePosAndKinds.size() - 2; i >= 0; i -= 2) {
            if (usePosAndKinds.get(i + 1) >= minUseKind.ordinal()) {
                return usePosAndKinds.get(i);
            }
        }
        return Integer.MAX_VALUE;
    }

    int nextUsage(IntervalUseKind minUseKind, int from) {
        assert isVirtualInterval() : "cannot access use positions for fixed intervals";

        for (int i = usePosAndKinds.size() - 2; i >= 0; i -= 2) {
            if (usePosAndKinds.get(i) >= from && usePosAndKinds.get(i + 1) >= minUseKind.ordinal()) {
                return usePosAndKinds.get(i);
            }
        }
        return Integer.MAX_VALUE;
    }

    int nextUsageExact(IntervalUseKind exactUseKind, int from) {
        assert isVirtualInterval() : "cannot access use positions for fixed intervals";

        for (int i = usePosAndKinds.size() - 2; i >= 0; i -= 2) {
            if (usePosAndKinds.get(i) >= from && usePosAndKinds.get(i + 1) == exactUseKind.ordinal()) {
                return usePosAndKinds.get(i);
            }
        }
        return Integer.MAX_VALUE;
    }

    int previousUsage(IntervalUseKind minUseKind, int from) {
        assert isVirtualInterval() : "cannot access use positions for fixed intervals";

        int prev = 0;
        for (int i = usePosAndKinds.size() - 2; i >= 0; i -= 2) {
            if (usePosAndKinds.get(i) > from) {
                return prev;
            }
            if (usePosAndKinds.get(i + 1) >= minUseKind.ordinal()) {
                prev = usePosAndKinds.get(i);
            }
        }
        return prev;
    }

    void addUsePos(int pos, IntervalUseKind useKind) {
        assert covers(pos, LIRVisitState.OperandMode.InputMode) : "use position not covered by live range";

        // do not add use positions for precolored intervals because
        // they are never used
        if (useKind != IntervalUseKind.noUse && regNum() >= CiRegister.vregBase) {
            assert usePosAndKinds.size() % 2 == 0 : "must be";
            for (int i = 0; i < usePosAndKinds.size(); i += 2) {
                assert pos <= usePosAndKinds.get(i) : "already added a use-position with lower position";
                assert usePosAndKinds.get(i + 1) >= 0 && usePosAndKinds.get(i + 1) < IntervalUseKind.values().length : "invalid use kind";
                if (i > 0) {
                    assert usePosAndKinds.get(i) < usePosAndKinds.get(i - 2) : "not sorted descending";
                }
            }

            // Note: addUse is called in descending order, so list gets sorted
            // automatically by just appending new use positions
            int len = usePosAndKinds.size();
            if (len == 0 || usePosAndKinds.get(len - 2) > pos) {
                usePosAndKinds.add(pos);
                usePosAndKinds.add(useKind.ordinal());
            } else if (usePosAndKinds.get(len - 1) < useKind.ordinal()) {
                assert usePosAndKinds.get(len - 2) == pos : "list not sorted correctly";
                usePosAndKinds.set(len - 1, useKind.ordinal());
            }
        }
    }

    void addRange(int from, int to) {
        assert from < to : "invalid range";
        assert first() == Range.end() || to < first().next().from() : "not inserting at begin of interval";
        assert from <= first().to() : "not inserting at begin of interval";

        if (first().from() <= to) {
            // join intersecting ranges
            first().setFrom(Math.min(from, first().from()));
            first().setTo(Math.max(to, first().to()));
        } else {
            // insert new range
            first = new Range(from, to, first());
        }
    }

    Interval newSplitChild() {
        // allocate new interval
        Interval result = new Interval(-1);
        result.setType(type());

        Interval parent = splitParent();
        result.splitParent = parent;
        result.setRegisterHint(parent);

        // insert new interval in children-list of parent
        if (parent.splitChildren.size() == 0) {
            assert isSplitParent() : "list must be initialized at first split";

            parent.splitChildren = new ArrayList<Interval>(4);
            parent.splitChildren.add(this);
        }
        parent.splitChildren.add(result);

        return result;
    }

    // split this interval at the specified position and return
    // the remainder as a new interval.
    //
    // when an interval is split, a bi-directional link is established between the original interval
    // (the split parent) and the intervals that are split off this interval (the split children)
    // When a split child is split again, the new created interval is also a direct child
    // of the original parent (there is no tree of split children stored, but a flat list)
    // All split children are spilled to the same stack slot (stored in canonicalSpillSlot)
    //
    // Note: The new interval has no valid regNum
    Interval split(int splitPos) {
        assert isVirtualInterval() : "cannot split fixed intervals";

        // allocate new interval
        Interval result = newSplitChild();

        // split the ranges
        Range prev = null;
        Range cur = first;
        while (cur != Range.end() && cur.to() <= splitPos) {
            prev = cur;
            cur = cur.next();
        }
        assert cur != Range.end() : "split interval after end of last range";

        if (cur.from() < splitPos) {
            result.first = new Range(splitPos, cur.to(), cur.next());
            cur.setTo(splitPos);
            cur.setNext(Range.end());

        } else {
            assert prev != null : "split before start of first range";
            result.first = cur;
            prev.setNext(Range.end());
        }
        result.current = result.first;
        cachedTo = -1; // clear cached value

        // split list of use positions
        int totalLen = usePosAndKinds.size();
        int startIdx = totalLen - 2;
        while (startIdx >= 0 && usePosAndKinds.get(startIdx) < splitPos) {
            startIdx -= 2;
        }

        List<Integer> newUsePosAndKinds = new ArrayList<Integer>(totalLen - startIdx);
        int i;
        for (i = startIdx + 2; i < totalLen; i++) {
            newUsePosAndKinds.add(usePosAndKinds.get(i));
        }

        Util.truncate(usePosAndKinds, startIdx + 2);
        result.usePosAndKinds = usePosAndKinds;
        usePosAndKinds = newUsePosAndKinds;

        assert usePosAndKinds.size() % 2 == 0 : "must have use kind for each use pos";
        assert result.usePosAndKinds.size() % 2 == 0 : "must have use kind for each use pos";
        assert usePosAndKinds.size() + result.usePosAndKinds.size() == totalLen : "missed some entries";

        for (i = 0; i < usePosAndKinds.size(); i += 2) {
            assert usePosAndKinds.get(i) < splitPos : "must be";
            assert usePosAndKinds.get(i + 1) >= 0 && usePosAndKinds.get(i + 1) < IntervalUseKind.values().length : "invalid use kind";
        }
        for (i = 0; i < result.usePosAndKinds.size(); i += 2) {
            assert result.usePosAndKinds.get(i) >= splitPos : "must be";
            assert result.usePosAndKinds.get(i + 1) >= 0 && result.usePosAndKinds.get(i + 1) < IntervalUseKind.values().length : "invalid use kind";
        }

        return result;
    }


    boolean isVirtualInterval() {
        return regNum() >= CiRegister.vregBase;
    }

    // split this interval at the specified position and return
    // the head as a new interval (the original interval is the tail)
    //
    // Currently, only the first range can be split, and the new interval
    // must not have split positions
    Interval splitFromStart(int splitPos) {
        assert isVirtualInterval() : "cannot split fixed intervals";
        assert splitPos > from() && splitPos < to() : "can only split inside interval";
        assert splitPos > first.from() && splitPos <= first.to() : "can only split inside first range";
        assert firstUsage(IntervalUseKind.noUse) > splitPos : "can not split when use positions are present";

        // allocate new interval
        Interval result = newSplitChild();

        // the new created interval has only one range (checked by assert on above,
        // so the splitting of the ranges is very simple
        result.addRange(first.from(), splitPos);

        if (splitPos == first.to()) {
            assert first.next() != Range.end() : "must not be at end";
            first = first.next();
        } else {
            first.setFrom(splitPos);
        }

        return result;
    }

    // returns true if the opId is inside the interval
    boolean covers(int opId, LIRVisitState.OperandMode mode) {
        Range cur = first;

        while (cur != Range.end() && cur.to() < opId) {
            cur = cur.next();
        }
        if (cur != Range.end()) {
            assert cur.to() != cur.next().from() : "ranges not separated";

            if (mode == LIRVisitState.OperandMode.OutputMode) {
                return cur.from() <= opId && opId < cur.to();
            } else {
                return cur.from() <= opId && opId <= cur.to();
            }
        }
        return false;
    }

    // returns true if the interval has any hole between holeFrom and holeTo
    // (even if the hole has only the length 1)
    boolean hasHoleBetween(int holeFrom, int holeTo) {
        assert holeFrom < holeTo : "check";
        assert from() <= holeFrom && holeTo <= to() : "index out of interval";

        Range cur = first;
        while (cur != Range.end()) {
            assert cur.to() < cur.next().from() : "no space between ranges";

            // hole-range starts before this range . hole
            if (holeFrom < cur.from()) {
                return true;

                // hole-range completely inside this range . no hole
            } else if (holeTo <= cur.to()) {
                return false;

                // overlapping of hole-range with this range . hole
            } else if (holeFrom <= cur.to()) {
                return true;
            }

            cur = cur.next();
        }

        return false;
    }

    @Override
    public String toString() {
        return regNum() + " " + typeName();
    }

    private String typeName() {

        String typeName;
        if (regNum() < CiRegister.vregBase) {
            typeName = "fixed";
        } else {
            typeName = type().name();
        }
        return typeName;
    }

    public void print(LogStream out, LinearScan allocator) {

        LIROperand opr = LIROperandFactory.illegal();
        if (regNum() < CiRegister.vregBase) {
            // need a temporary operand for fixed intervals because type() cannot be called
            if (allocator.isCpu(assignedReg())) {
                opr = LIROperandFactory.singleLocation(CiKind.Int, allocator.toRegister(assignedReg()));
            } else if (allocator.isXmm(assignedReg())) {
                opr = LIROperandFactory.singleLocation(CiKind.Float, allocator.toRegister(assignedReg()));
            } else {
                Util.shouldNotReachHere();
            }
        } else {
            if (assignedReg() != -1) {
                opr = allocator.calcOperandForInterval(this);
            }
        }

        out.printf("%d %s ", regNum(), typeName());
        if (opr.isValid()) {
            out.print("\"");
            opr.print(out);
            out.print("\" ");
        }
        out.printf("%d %d ", splitParent().regNum(), registerHint(false, allocator) != null ? registerHint(false, allocator).regNum() : -1);

        // print ranges
        Range cur = first;
        while (cur != Range.end()) {
            cur.print(out);
            cur = cur.next();
            assert cur != null : "range list not closed with range sentinel";
        }

        // print use positions
        int prev = 0;
        assert usePosAndKinds.size() % 2 == 0 : "must be";
        for (int i = usePosAndKinds.size() - 2; i >= 0; i -= 2) {
            assert prev < usePosAndKinds.get(i) : "use positions not sorted";

            out.printf("%d %s ", usePosAndKinds.get(i), IntervalUseKind.values()[usePosAndKinds.get(i + 1)].toString());
            prev = usePosAndKinds.get(i);
        }

        out.printf(" \"%s\"", spillState().toString());
        out.println();
    }
}
