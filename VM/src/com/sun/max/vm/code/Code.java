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
package com.sun.max.vm.code;

import static com.sun.max.vm.VMOptions.*;

import java.lang.management.*;

import com.sun.max.annotate.*;
import com.sun.max.platform.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.heap.*;
import com.sun.max.vm.management.*;
import com.sun.max.vm.runtime.*;

/**
 * Utilities for managing target code. This class offers a number of services
 * for allocating and managing the target code regions. It also offers a static
 * facade that hides the details of operations such as finding a target method
 * by a code address, allocating space for a new target method, etc.
 */
public final class Code {

    private Code() {
    }

    public static final VMBooleanXXOption traceAllocation = register(new VMBooleanXXOption("-XX:-TraceCodeAllocation", "Trace allocation from the code cache."), MaxineVM.Phase.STARTING);


    /**
     * Used by the Inspector to uniquely identify the special boot code region.
     */
    @INSPECTED
    private static final String CODE_BOOT_NAME = "Code-Boot";

    /**
     * The code region that contains the boot code.
     */
    @INSPECTED
    public static final CodeRegion bootCodeRegion = new CodeRegion(Address.fromInt(Integer.MAX_VALUE / 2).wordAligned(), Size.fromInt(Integer.MAX_VALUE / 4), CODE_BOOT_NAME);

    /**
     * Creates the singleton code manager for this operating system.
     * @return a new code manager
     */
    @HOSTED_ONLY
    private static CodeManager createCodeManager() {
        switch (Platform.hostOrTarget().operatingSystem) {
            case LINUX: {
                return new LowAddressCodeManager();
            }

            case GUESTVM:
                return new FixedAddressCodeManager();

            case DARWIN:
            case SOLARIS: {
                return new VariableAddressCodeManager();
            }
            default: {
                FatalError.unimplemented();
                return null;
            }
        }
    }

    /**
     * The code manager singleton instance.
     */
    @INSPECTED
    private static final CodeManager codeManager = createCodeManager();

    public static CodeManager getCodeManager() {
        return codeManager;
    }

    /**
     * Initializes the code manager.
     */
    public static void initialize() {
        codeManager.initialize();
    }

    /**
     * Allocates space in a code region for the code-related arrays of a given target method
     * and {@linkplain TargetMethod#setCodeArrays(byte[], byte[], Object[]) initializes} them.
     *
     * @param targetBundleLayout describes the layout of the arrays in the allocate space
     * @param targetMethod the target method for which the code-related arrays are allocated
     */
    public static void allocate(TargetBundleLayout targetBundleLayout, TargetMethod targetMethod) {
        codeManager.allocate(targetBundleLayout, targetMethod);
    }

    /**
     * Determines whether any code region contains the specified address.
     *
     * @param address the address to check
     * @return {@code true} if the specified address is contained in some code region
     * managed by the code manager; {@code false} otherwise
     */
    public static boolean contains(Address address) {
        return codeManager.codePointerToCodeRegion(address) != null;
    }

    /**
     * Looks up the target method that contains the specified code pointer.
     *
     * @param codePointer a pointer to code
     * @return a reference to the target method that contains the specified code pointer, if
     * one exists; {@code null} otherwise
     */
    @INSPECTED
    public static TargetMethod codePointerToTargetMethod(Address codePointer) {
        return codeManager.codePointerToTargetMethod(codePointer);
    }

    /**
     * Performs code update, updating the old target method to the new target method. This typically
     * involves platform-specific code modifications to forward old code to the new code.
     *
     * @param oldTargetMethod the old target method to target to the new target method
     * @param newTargetMethod the new target method
     */
    public static void updateTargetMethod(TargetMethod oldTargetMethod, TargetMethod newTargetMethod) {
        oldTargetMethod.forwardTo(newTargetMethod);
        discardTargetMethod(oldTargetMethod);
    }

    /**
     * Removes a target method from this code manager, freeing its space when it is safe to do so.
     *
     * @param targetMethod the target method to discard
     */
    public static void discardTargetMethod(TargetMethod targetMethod) {
        // do nothing.
    }

    /**
     * Visits each cell that is managed by the code manager.
     *
     * @param cellVisitor the cell visitor to call back for each cell
     * @param includeBootCode specifies if the cells in the {@linkplain Code#bootCodeRegion() boot code region} should
     *            also be visited
     */
    public static void visitCells(CellVisitor cellVisitor, boolean includeBootCode) {
        codeManager.visitCells(cellVisitor, includeBootCode);
    }

    public static Size getRuntimeCodeRegionSize() {
        return codeManager.getRuntimeCodeRegionSize();
    }

    public static MemoryManagerMXBean getMemoryManagerMXBean() {
        return new CodeMemoryManagerMXBean("Code");
    }

    private static class CodeMemoryManagerMXBean extends MemoryManagerMXBeanAdaptor {
        CodeMemoryManagerMXBean(String name) {
            super(name);
            add(new CodeMemoryPoolMXBean(bootCodeRegion, this));
            add(new CodeMemoryPoolMXBean(codeManager.getRuntimeCodeRegion(), this));

        }
    }

    private static class CodeMemoryPoolMXBean extends MemoryPoolMXBeanAdaptor {
        CodeMemoryPoolMXBean(CodeRegion codeRegion, MemoryManagerMXBean manager) {
            super(MemoryType.NON_HEAP, codeRegion, manager);
        }
    }

}
