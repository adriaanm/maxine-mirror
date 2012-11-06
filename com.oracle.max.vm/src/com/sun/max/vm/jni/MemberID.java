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
package com.sun.max.vm.jni;

import com.sun.max.annotate.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;

/**
 * @see FieldID
 * @see MethodID
 */
public abstract class MemberID extends Word {

    static final int NUMBER_OF_MEMBER_BITS = 16;
    static final int MEMBER_INDEX_MASK = 0x0000ffff;

    public static Word create(int holderID, int memberIndex) {
        assert (0 <= memberIndex) && (memberIndex <= MEMBER_INDEX_MASK);
        final Address word = Address.fromInt(holderID).shiftedLeft(NUMBER_OF_MEMBER_BITS);
        return word.or(memberIndex);
    }

    public static Word create(MemberActor memberActor) {
        return create(memberActor.holder().id, memberActor.memberIndex());
    }

    @HOSTED_ONLY
    public MemberID(long value) {
        super(value);
    }

    @INLINE
    protected final ClassActor getHolder() {
        return ClassIDManager.toClassActor(getHolderIDAsInt());
    }

    @INLINE
    protected final int getMemberIndex() {
        final Address word = asAddress().and(MEMBER_INDEX_MASK);
        return word.toInt();
    }

    @INLINE
    protected final int getHolderIDAsInt() {
        return asAddress().unsignedShiftedRight(NUMBER_OF_MEMBER_BITS).toInt();
    }

    public static int getClassIDAsInt(MemberID memberID) {
        return memberID.getHolderIDAsInt();
    }

    public static int getMemberIDAsInt(MemberID memberID) {
        return memberID.getMemberIndex();
    }
}
