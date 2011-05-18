/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.cri.ci;

import java.util.*;

import com.sun.cri.ci.CiCallingConvention.Type;
import com.sun.cri.ci.CiRegister.RegisterFlag;
import com.sun.cri.ri.*;

/**
 * A default implementation of {@link RiRegisterConfig}.
 */
public class CiRegisterConfig implements RiRegisterConfig {

    /**
     * The object describing the callee save area of this register configuration.
     */
    protected CiCalleeSaveArea calleeSaveArea;

    /**
     * The minimum register role identifier.
     */
    protected final int minRole;

    /**
     * The map from register role IDs to registers.
     */
    protected final CiRegister[] registersRoleMap;

    /**
     * The set of registers that can be used by the register allocator.
     */
    protected final CiRegister[] allocatable;

    /**
     * The set of registers that can be used by the register allocator,
     * {@linkplain CiRegister#categorize(CiRegister[]) categorized} by register
     * {@linkplain RegisterFlag flags}.
     */
    protected final EnumMap<RegisterFlag, CiRegister[]> categorized;

    /**
     * The ordered set of registers used to pass integral arguments.
     */
    protected final CiRegister[] cpuParameters;

    /**
     * The ordered set of registers used to pass floating point arguments.
     */
    protected final CiRegister[] fpuParameters;

    /**
     * The caller saved registers.
     */
    protected final CiRegister[] callerSave;

    /**
     * The register to which {@link CiRegister#Frame} and {@link CiRegister#CallerFrame} are bound.
     */
    protected final CiRegister frame;

    /**
     * Register for returning an integral value.
     */
    protected final CiRegister integralReturn;

    /**
     * Register for returning a floating point value.
     */
    protected final CiRegister floatingPointReturn;

    /**
     * The map from register {@linkplain CiRegister#number numbers} to register
     * {@linkplain RiRegisterAttributes attributes} for this register configuration.
     */
    protected final RiRegisterAttributes[] attributesMap;

    /**
     * The scratch register.
     */
    protected final CiRegister scratch;

    public CiRegisterConfig(
                    CiRegister frame,
                    CiRegister integralReturn,
                    CiRegister floatingPointReturn,
                    CiRegister scratch,
                    CiRegister[] allocatable,
                    CiRegister[] callerSave,
                    CiRegister[] parameters,
                    CiCalleeSaveArea calleeSave,
                    CiRegister[] allRegisters,
                    Map<Integer, CiRegister> roles) {
        this.frame = frame;
        this.calleeSaveArea = calleeSave;
        this.allocatable = allocatable;
        this.callerSave = callerSave;
        assert !Arrays.asList(allocatable).contains(scratch);
        this.scratch = scratch;
        EnumMap<RegisterFlag, CiRegister[]> categorizedParameters = CiRegister.categorize(parameters);
        this.cpuParameters = categorizedParameters.get(RegisterFlag.CPU);
        this.fpuParameters = categorizedParameters.get(RegisterFlag.FPU);
        categorized = CiRegister.categorize(allocatable);
        attributesMap = RiRegisterAttributes.createMap(this, allRegisters);
        this.floatingPointReturn = floatingPointReturn;
        this.integralReturn = integralReturn;
        int minRoleId = Integer.MAX_VALUE;
        int maxRoleId = Integer.MIN_VALUE;
        for (Map.Entry<Integer, CiRegister> e : roles.entrySet()) {
            int id = e.getKey();
            assert id >= 0;
            if (minRoleId > id) {
                minRoleId = id;
            }
            if (maxRoleId < id) {
                maxRoleId = id;
            }
        }
        registersRoleMap = new CiRegister[(maxRoleId - minRoleId) + 1];
        for (Map.Entry<Integer, CiRegister> e : roles.entrySet()) {
            int id = e.getKey();
            registersRoleMap[id] = e.getValue();
        }
        minRole = minRoleId;
    }

    public CiRegisterConfig(CiRegisterConfig src, CiCalleeSaveArea calleeSaveArea) {
        this.frame = src.frame;
        this.calleeSaveArea = calleeSaveArea;
        this.allocatable = src.allocatable;
        this.callerSave = src.callerSave;
        this.scratch = src.scratch;
        this.cpuParameters = src.cpuParameters;
        this.fpuParameters = src.fpuParameters;
        this.categorized = src.categorized;
        this.attributesMap = src.attributesMap;
        this.floatingPointReturn = src.floatingPointReturn;
        this.integralReturn = src.integralReturn;
        this.registersRoleMap = src.registersRoleMap;
        this.minRole = src.minRole;
    }

    public CiRegister getReturnRegister(CiKind kind) {
        if (kind.isDouble() || kind.isFloat()) {
            return floatingPointReturn;
        }
        return integralReturn;
    }

    public CiRegister getFrameRegister() {
        return frame;
    }

    public CiRegister getScratchRegister() {
        return scratch;
    }

    /**
     * {@inheritDoc}
     *
     * This implementation assigns all available registers to parameters before assigning
     * any stack slots to parameters.
     */
    public CiCallingConvention getCallingConvention(Type type, CiKind[] parameters, CiTarget target) {
        CiValue[] locations = new CiValue[parameters.length];

        int currentGeneral = 0;
        int currentXMM = 0;
        int currentStackIndex = 0;

        for (int i = 0; i < parameters.length; i++) {
            final CiKind kind = parameters[i];

            switch (kind) {
                case Byte:
                case Boolean:
                case Short:
                case Char:
                case Int:
                case Long:
                case Word:
                case Object:
                    if (currentGeneral < cpuParameters.length) {
                        CiRegister register = cpuParameters[currentGeneral++];
                        locations[i] = register.asValue(kind);
                    }
                    break;

                case Float:
                case Double:
                    if (currentXMM < fpuParameters.length) {
                        CiRegister register = fpuParameters[currentXMM++];
                        locations[i] = register.asValue(kind);
                    }
                    break;

                default:
                    throw new InternalError("Unexpected parameter kind: " + kind);
            }

            if (locations[i] == null) {
                locations[i] = CiStackSlot.get(kind.stackKind(), currentStackIndex, !type.out);
                currentStackIndex += target.spillSlots(kind);
            }
        }

        return new CiCallingConvention(locations, currentStackIndex * target.spillSlotSize);
    }

    public CiRegister[] getCallingConventionRegisters(Type type, RegisterFlag flag) {
        if (flag == RegisterFlag.CPU) {
            return cpuParameters;
        }
        assert flag == RegisterFlag.FPU;
        return fpuParameters;
    }

    public CiRegister[] getAllocatableRegisters() {
        return allocatable;
    }

    public EnumMap<RegisterFlag, CiRegister[]> getCategorizedAllocatableRegisters() {
        return categorized;
    }

    public CiRegister[] getCallerSaveRegisters() {
        return callerSave;
    }

    public CiCalleeSaveArea getCalleeSaveArea() {
        return calleeSaveArea;
    }

    public RiRegisterAttributes[] getAttributesMap() {
        return attributesMap;
    }

    public CiRegister getRegisterForRole(int id) {
        return registersRoleMap[id - minRole];
    }

    @Override
    public String toString() {
        StringBuilder roleMap = new StringBuilder();
        for (int i = 0; i < registersRoleMap.length; ++i) {
            CiRegister reg = registersRoleMap[i];
            if (reg != null) {
                if (roleMap.length() != 0) {
                    roleMap.append(", ");
                }
                roleMap.append(i + minRole).append(" -> ").append(reg);
            }
        }
        String res = String.format(
             "Allocatable: " + Arrays.toString(getAllocatableRegisters()) + "%n" +
             "CallerSave:  " + Arrays.toString(getCallerSaveRegisters()) + "%n" +
             "CalleeSave:  " + getCalleeSaveArea() + "%n" +
             "CPU Params:  " + Arrays.toString(cpuParameters) + "%n" +
             "FPU Params:  " + Arrays.toString(fpuParameters) + "%n" +
             "VMRoles:     " + roleMap + "%n" +
             "Scratch:     " + getScratchRegister() + "%n");
        return res;
    }
}
