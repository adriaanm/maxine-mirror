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
package com.sun.max.tele.debug;

import java.util.*;

import com.sun.max.platform.*;
import com.sun.max.tele.*;
import com.sun.max.tele.memory.*;
import com.sun.max.tele.util.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.runtime.Safepoint.*;
import com.sun.max.vm.thread.*;

/**
 * Access to a block of thread local storage.
 *
 * @see VmThreadLocal
 *
 * @author Michael Van De Vanter
 */
public final class TeleThreadLocalsBlock extends AbstractTeleVMHolder implements TeleVMCache, MaxThreadLocalsBlock {

    private static final int TRACE_VALUE = 2;

    private final TimedTrace updateTracer;

    /**
     * Description of the memory region occupied by a {@linkplain MaxThreadLocalsBlock thread locals block} in the VM.
     * <br>
     * This region has no parent; it is allocated from the OS.
     * <br>
     * This region's children are
     * the {@linkplain MaxThreadLocalsArea thread locals areas} it contains.
     */
    private final class ThreadLocalsBlockMemoryRegion extends TeleFixedMemoryRegion implements MaxEntityMemoryRegion<MaxThreadLocalsBlock> {

        private final TeleThreadLocalsBlock teleThreadLocalsBlock;

        private ThreadLocalsBlockMemoryRegion(TeleVM vm, TeleThreadLocalsBlock owner, String regionName, Address start, Size size) {
            super(vm, regionName, start, size);
            this.teleThreadLocalsBlock = owner;
        }

        public MaxEntityMemoryRegion<? extends MaxEntity> parent() {
            // Thread local memory blocks are allocated from the OS, not part of any other region
            return null;
        }

        public List<MaxEntityMemoryRegion<? extends MaxEntity>> children() {
            if (threadLocalsBlockMemoryRegion == null) {
                return new ArrayList<MaxEntityMemoryRegion<? extends MaxEntity>>(0);
            }
            final List<MaxEntityMemoryRegion<? extends MaxEntity>> regions =
                new ArrayList<MaxEntityMemoryRegion<? extends MaxEntity>>(areas.size());
            for (TeleThreadLocalsArea teleThreadLocalsArea : areas.values()) {
                if (teleThreadLocalsArea != null) {
                    regions.add(teleThreadLocalsArea.memoryRegion());
                }
            }
            return regions;
        }

        public MaxThreadLocalsBlock owner() {
            return teleThreadLocalsBlock;
        }

        public boolean isBootRegion() {
            return false;
        }
    }

    private final String entityName;
    private final String entityDescription;
    private final TeleNativeThread teleNativeThread;

    /**
     * The region of VM memory occupied by this block, null if this is a dummy for which there are no locals (as for a native thread).
     */
    private final ThreadLocalsBlockMemoryRegion threadLocalsBlockMemoryRegion;

    /**
     * The thread locals areas for each state; null if no actual thread locals allocated.
     */
    private final Map<Safepoint.State, TeleThreadLocalsArea> areas;
    private final int offsetToTriggeredThreadLocals;
    private long lastRefreshedEpoch = -1L;

    /**
     * Control to prevent infinite recursion due to cycle in call path.
     */
    private boolean updatingCache = false;

    /**
     * The VM thread object pointed to by the most recently read value of a particular thread local variable.
     */
    private TeleVmThread teleVmThread = null;

    /**
     * Creates an accessor for thread local information in the ordinary case.
     *
     * @param teleNativeThread the thread owning the thread local information
     * @param regionName descriptive name for this thread locals block in the VM
     * @param start starting location of the memory associated with this entity in the VM.
     * @param size length of the memory associated with this entity in the VM.
     * @return access to thread local information
     */
    public TeleThreadLocalsBlock(TeleNativeThread teleNativeThread, String regionName, Address start, Size size) {
        super(teleNativeThread.vm());
        final TimedTrace tracer = new TimedTrace(TRACE_VALUE, tracePrefix() + " creating");
        tracer.begin();

        this.teleNativeThread = teleNativeThread;
        this.entityName = regionName;
        this.threadLocalsBlockMemoryRegion = new ThreadLocalsBlockMemoryRegion(teleNativeThread.vm(), this, regionName, start, size);
        this.areas = new EnumMap<Safepoint.State, TeleThreadLocalsArea>(Safepoint.State.class);
        this.offsetToTriggeredThreadLocals = Platform.platform().pageSize - Word.size();
        this.entityDescription = "The set of local variables for thread " + teleNativeThread.entityName() + " in the " + teleNativeThread.vm().entityName();
        this.updateTracer = new TimedTrace(TRACE_VALUE, tracePrefix() + " updating");

        tracer.end(null);
    }

    /**
     * Creates an accessor for thread local information in the special case where there is actually no thread local storage
     * identified. This might happen if the thread is non-Java, or isn't far enough along in its creation sequence for
     * the storage to be known.
     *
     * @param teleNativeThread the thread owning the thread local information
     * @param name a descriptive name for the area, in the absence of one associated with a memory region
     * @return access to thread local information
     */
    public TeleThreadLocalsBlock(TeleNativeThread teleNativeThread, String name) {
        super(teleNativeThread.vm());
        final TimedTrace tracer = new TimedTrace(TRACE_VALUE, tracePrefix() + " creating");
        tracer.begin();

        this.teleNativeThread = teleNativeThread;
        this.entityName = name;
        this.threadLocalsBlockMemoryRegion = null;
        this.areas = null;
        this.offsetToTriggeredThreadLocals = Platform.platform().pageSize - Word.size();
        this.entityDescription = "The set of local variables for thread " + teleNativeThread.entityName() + " in the " + teleNativeThread.vm().entityName();
        this.updateTracer = new TimedTrace(TRACE_VALUE, tracePrefix() + " updating");

        tracer.end(null);
    }

    public void updateCache() {
        if (threadLocalsBlockMemoryRegion != null) {
            final long processEpoch = vm().teleProcess().epoch();
            // This gets called redundantly from several places; be sure it only gets done once per epoch.
            if (lastRefreshedEpoch < processEpoch) {
                assert vm().lockHeldByCurrentThread();
                if (updatingCache) {
                    return;
                }
                updatingCache = true;
                updateTracer.begin();
                for (TeleThreadLocalsArea teleThreadLocalsArea : areas.values()) {
                    if (teleThreadLocalsArea != null) {
                        teleThreadLocalsArea.updateCache();
                    }
                }
                final TeleThreadLocalsArea enabledThreadLocalsArea = areas.get(Safepoint.State.ENABLED);
                if (enabledThreadLocalsArea != null) {
                    final Word threadLocalValue = enabledThreadLocalsArea.getWord(VmThreadLocal.VM_THREAD);
                    if (!threadLocalValue.isZero()) {
                        final Reference vmThreadReference = vm().wordToReference(threadLocalValue);
                        teleVmThread = (TeleVmThread) heap().makeTeleObject(vmThreadReference);
                    }
                }
                updatingCache = false;
                lastRefreshedEpoch = processEpoch;
                updateTracer.end(null);
            }
        }
    }

    public String entityName() {
        return entityName;
    }

    public String entityDescription() {
        return entityDescription;
    }

    public MaxEntityMemoryRegion<MaxThreadLocalsBlock> memoryRegion() {
        return threadLocalsBlockMemoryRegion;
    }

    public boolean contains(Address address) {
        return threadLocalsBlockMemoryRegion.contains(address);
    }

    public TeleNativeThread thread() {
        return teleNativeThread;
    }

    public TeleThreadLocalsArea threadLocalsAreaFor(State state) {
        if (threadLocalsBlockMemoryRegion != null) {
            updateCache();
            return areas.get(state);
        }
        return null;
    }

    public MaxThreadLocalsArea findThreadLocalsArea(Address address) {
        if (threadLocalsBlockMemoryRegion != null) {
            for (Safepoint.State state : Safepoint.State.CONSTANTS) {
                final TeleThreadLocalsArea threadLocalsArea = threadLocalsAreaFor(state);
                if (threadLocalsArea.memoryRegion().contains(address)) {
                    return threadLocalsArea;
                }
            }
        }
        return null;
    }

    /**
     * Gets the value of the thread local variable holding a reference to the VM thread corresponding to the native
     * thread.
     *
     * @return access to the VM thread corresponding to this thread, if any
     */
    TeleVmThread teleVmThread() {
        return teleVmThread;
    }

    /**
     * Update any state related to this thread locals area, based on possibly more information having been acquired.
     *
     * @param threadLocalsRegion the memory region containing the thread locals block
     * @param tlaSize the size in bytes of each Thread Locals Area in the region.
     */
    void updateAfterGather(TeleFixedMemoryRegion threadLocalsRegion, int threadLocalsAreaSize) {
        if (threadLocalsRegion != null) {
            for (Safepoint.State safepointState : Safepoint.State.CONSTANTS) {
                final Pointer tlaStartPointer = getThreadLocalsAreaStart(threadLocalsRegion, threadLocalsAreaSize, safepointState);
                // Only create a new TeleThreadLocalsArea if the start address has changed which
                // should only happen once going from 0 to a non-zero value.
                final TeleThreadLocalsArea area = areas.get(safepointState);
                if (area == null || !area.memoryRegion().start().equals(tlaStartPointer)) {
                    areas.put(safepointState, new TeleThreadLocalsArea(vm(), thread(), safepointState, tlaStartPointer));
                }
            }
            updateCache();
        }
    }

    /**
     * Removes any state associated with the thread, typically because the thread has died.
     */
    void clear() {
        if (threadLocalsBlockMemoryRegion != null) {
            areas.clear();
            teleVmThread = null;
            lastRefreshedEpoch = vm().teleProcess().epoch();
        }
    }

    /**
     * Gets the address of one of the three thread locals areas inside a given thread locals region.
     *
     * @param threadLocalsRegion the VM memory region containing the thread locals block
     * @param threadLocalsAreaSize the size of a thread locals area within the region
     * @param safepointState denotes which of the three thread locals areas is being requested
     * @return the address of the thread locals areas in {@code threadLocalsRegion} corresponding to {@code state}
     * @see VmThreadLocal
     */
    private Pointer getThreadLocalsAreaStart(TeleFixedMemoryRegion threadLocalsRegion, int threadLocalsAreaSize, Safepoint.State safepointState) {
        if (threadLocalsRegion != null) {
            return threadLocalsRegion.start().plus(offsetToTriggeredThreadLocals).plus(threadLocalsAreaSize * safepointState.ordinal()).asPointer();
        }
        return null;
    }

}
