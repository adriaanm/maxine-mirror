/*
 * Copyright (c) 2008, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.tele;

import static com.sun.max.vm.VMConfiguration.*;

import java.io.*;
import java.math.*;
import java.text.*;
import java.util.*;

import com.sun.max.lang.*;
import com.sun.max.program.*;
import com.sun.max.tele.memory.*;
import com.sun.max.tele.method.*;
import com.sun.max.tele.object.*;
import com.sun.max.tele.reference.*;
import com.sun.max.tele.type.*;
import com.sun.max.tele.util.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.heap.*;
import com.sun.max.vm.hosted.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.tele.*;

/**
 * Singleton cache of information about the heap in the VM.
 *<br>
 * Initialization between this class and {@link TeleClassRegistry} are mutually
 * dependent.  The cycle is broken by creating this class in a partially initialized
 * state that only considers the boot heap region; this class is only made fully functional
 * with a call to {@link #initialize()}, which requires that {@link TeleClassRegistry} be
 * fully initialized.
 * <br>
  * Interesting heap state includes the list of memory regions allocated.
 * <br>
 * This class also provides access to a special root table in the VM, active
 * only when being inspected, that allows Inspector references
 * to track object locations when they are relocated by GC.
 * <br>
 * This class needs to be specialized by a helper class that
 * implements the interface {@link TeleHeapScheme}, typically
 * a class that contains knowledge of the heap implementation
 * configured into the VM.
 *
 * @author Michael Van De Vanter
 * @author Hannes Payer
 *
 * @see InspectableHeapInfo
 * @see TeleRoots
 * @see HeapScheme
 * @see TeleHeapScheme
 */
public final class TeleHeap extends AbstractTeleVMHolder implements TeleVMCache, MaxHeap, TeleHeapScheme {
     /**
     * Name of system property that specifies the address where the heap is located, or where it should be relocated, depending
     * on the user of the TeleHeap class.
     */
    public static final String HEAP_ADDRESS_PROPERTY = "max.heap";

    private static final int TRACE_VALUE = 1;

    private final TimedTrace updateTracer;

    protected static TeleHeap teleHeap;

    public static long heapAddressOption() {
        long heap = 0L;
        String heapValue = System.getProperty(HEAP_ADDRESS_PROPERTY);
        if (heapValue != null) {
            try {
                int radix = 10;
                if (heapValue.startsWith("0x")) {
                    radix = 16;
                    heapValue = heapValue.substring(2);
                }
                // Use BigInteger to handle unsigned 64-bit values that are greater than Long.MAX_VALUE
                BigInteger bi = new BigInteger(heapValue, radix);
                heap = bi.longValue();
            } catch (NumberFormatException e) {
                System.err.println("Error parsing value of " + HEAP_ADDRESS_PROPERTY + " system property: " + heapValue + ": " +  e);
            }
        }
        return heap;
    }

    /**
     * Returns the singleton manager of cached information about the heap in the VM,
     * specialized for the particular implementation of {@link HeapScheme} in the VM.
     * <br>
     * Not usable until after a call to {@link #initialize()}, which must be called
     * after the {@link TeleClassRegistry} is fully initialized; otherwise, a circular
     * dependency will cause breakage.
     */
    public static TeleHeap make(TeleVM teleVM) {
        // TODO (mlvdv) Replace this hard-wired GC-specific dispatch with something more sensible.
        if (teleHeap ==  null) {
            final String heapSchemeName = vmConfig().heapScheme().name();
            TeleHeapScheme teleHeapScheme = null;
            if (heapSchemeName.equals("SemiSpaceHeapScheme")) {
                teleHeapScheme = new TeleSemiSpaceHeapScheme(teleVM);
            } else if (heapSchemeName.equals("MSHeapScheme")) {
                teleHeapScheme = new TeleMSHeapScheme(teleVM);
            } else if (heapSchemeName.equals("MSEHeapScheme")) {
                teleHeapScheme = new TeleMSEHeapScheme(teleVM);
            } else {
                teleHeapScheme = new TeleUnknownHeapScheme(teleVM);
                TeleWarning.message("Unable to locate implementation of TeleHeapScheme for HeapScheme=" + heapSchemeName + ", using default");
            }
            teleHeap = new TeleHeap(teleVM, teleHeapScheme);
            Trace.line(1, "[TeleHeap] Scheme=" + heapSchemeName + " using TeleHeapScheme=" + teleHeapScheme.getClass().getSimpleName());
        }
        return teleHeap;
    }

    private static final String entityName = "Heap";
    private final String entityDescription;

    private String bootHeapRegionName = "uninitialized";
    private TeleRuntimeMemoryRegion teleBootHeapRegion = null;
    private TeleHeapRegion bootHeapRegion = null;

    private TeleRuntimeMemoryRegion teleImmortalHeapRegion = null;
    private TeleHeapRegion immortalHeapRegion = null;

    // Keep track of the VM objects representing heap regions and the entities we use to model their state
    private final HashMap<TeleRuntimeMemoryRegion, TeleHeapRegion> regionToTeleHeapRegion = new HashMap<TeleRuntimeMemoryRegion, TeleHeapRegion>();

    /**
     * Unmodifiable list of all currently known heap regions.
     */
    private volatile List<MaxHeapRegion> allHeapRegions;

    private Pointer teleRuntimeMemoryRegionRegistrationPointer = Pointer.zero();

    private Pointer teleRootsPointer = Pointer.zero();

    private TeleRuntimeMemoryRegion teleRootsRegion = null;
    private TeleFixedMemoryRegion rootsRegion = null;

    private final TeleHeapScheme teleHeapScheme;

    private List<MaxCodeLocation> inspectableMethods = null;

    private long gcStartedCount = -1;
    private long gcCompletedCount = -1;

    private int lastRegionCount = 0;

    private final TeleObjectFactory teleObjectFactory;

    private final Object statsPrinter = new Object() {
        @Override
        public String toString() {
            final StringBuilder msg = new StringBuilder();
            final int size = allHeapRegions.size();
            msg.append("#regions=(").append(size);
            msg.append(", new=").append(size - lastRegionCount).append(")");
            lastRegionCount = size;
            if (isInGC()) {
                msg.append(", IN GC(");
                msg.append("#starts=").append(gcStartedCount);
                msg.append(", #complete=").append(gcCompletedCount).append(")");
            } else if (gcCompletedCount >= 0) {
                msg.append(", #GCs=").append(gcCompletedCount);
            }
            return msg.toString();
        }
    };

    protected TeleHeap(TeleVM vm, TeleHeapScheme teleHeapScheme) {
        super(vm);
        final TimedTrace tracer = new TimedTrace(TRACE_VALUE, tracePrefix() + " creating");
        tracer.begin();

        this.teleObjectFactory = TeleObjectFactory.make(vm, vm.teleProcess().epoch());
        this.teleHeapScheme = teleHeapScheme;
        this.entityDescription = "Object creation and management for the " + vm().entityName();
        this.updateTracer = new TimedTrace(TRACE_VALUE, tracePrefix() + " updating");

        final List<MaxHeapRegion> heapRegions = new ArrayList<MaxHeapRegion>();

        /*
         * Before initialization we can't actually read the VM's descriptions of its heap regions,
         * since those are represented as VM objects (circularity).  This is true for both the
         * boot heap and any dynamically allocated heap regions (we might find these when attaching
         * to a running VM or inspecting a dumped image).
         * But we have to know the location of the heap regions in order to build up the
         * {@link TeleClassRegistry}, so we start here by determining the locations with very
         * low level (unsafe) machinery and using those to create fake representations.
         * The fake representations are replaced with the real ones when this class is initialized.
         */

        // We know specifically about the boot heap region.
        final Pointer bootHeapStart = vm().bootImageStart();
        final Size bootHeapSize = Size.fromInt(vm().bootImage().header.heapSize);
        final TeleFixedHeapRegion fakeBootHeapRegion =
            new TeleFixedHeapRegion(vm, "Fake Heap-boot region", bootHeapStart, bootHeapSize, true);
        heapRegions.add(fakeBootHeapRegion);
        // There might be dynamically allocated regions in a dumped image or when attaching to a running VM
        for (MaxMemoryRegion dynamicHeapRegion : vm.getDynamicHeapRegionsUnsafe()) {
            final TeleFixedHeapRegion fakeDynamicHeapRegion =
                new TeleFixedHeapRegion(vm, dynamicHeapRegion.regionName(), dynamicHeapRegion.start(), dynamicHeapRegion.size(), false);
            heapRegions.add(fakeDynamicHeapRegion);
        }
        this.allHeapRegions = Collections.unmodifiableList(heapRegions);

        tracer.end(statsPrinter);
    }

    /**
     * This class must function before being fully initialized in order to avoid an initialization
     * cycle with {@link TeleClassRegistry}; each depends on the other for full initialization.
     *
     * @return whether this manager has been fully initialized.
     */
    private boolean isInitialized() {
        return teleBootHeapRegion != null;
    }

    /**
     * Lazy initialization; try to keep data reading out of constructor.
     */
    public void initialize() {
        assert vm().lockHeldByCurrentThread();
        final TimedTrace tracer = new TimedTrace(TRACE_VALUE, tracePrefix() + " initializing");
        tracer.begin();

        final Reference nameReference = vm().teleFields().Heap_HEAP_BOOT_NAME.readReference(vm());
        this.bootHeapRegionName = vm().getString(nameReference);

        final Reference bootHeapRegionReference = vm().teleFields().Heap_bootHeapRegion.readReference(vm());
        this.teleBootHeapRegion = (TeleRuntimeMemoryRegion) makeTeleObject(bootHeapRegionReference);
        this.bootHeapRegion = new TeleHeapRegion(vm(), teleBootHeapRegion, true);

        // The address of the tele roots field must be accessible before any {@link TeleObject}s can be created,
        // which means that it must be accessible before calling {@link #refresh()} here.
        final int teleRootsOffset = vm().teleFields().InspectableHeapInfo_rootsPointer.fieldActor().offset();
        this.teleRootsPointer = vm().teleFields().InspectableHeapInfo_rootsPointer.staticTupleReference(vm()).toOrigin().plus(teleRootsOffset);

        updateCache();
        tracer.end(statsPrinter);
    }

    public void updateObjectCache() {
        teleObjectFactory.updateCache();
    }

    public void updateCache() {
        // Replaces local cache of information about heap regions in the VM.
        // During this update, any method calls to check heap containment are handled specially.
        updateTracer.begin();
        assert vm().lockHeldByCurrentThread();
        if (!isInitialized()) {
            updateTracer.end("not initialized yet");
        } else {
            // Check GC status and update references if a GC has completed since last time we checked
            final long oldGcStartedCount = gcStartedCount;
            gcStartedCount = vm().teleFields().InspectableHeapInfo_gcStartedCounter.readLong(vm());
            gcCompletedCount = vm().teleFields().InspectableHeapInfo_gcCompletedCounter.readLong(vm());
            // Invariant:  oldGcStartedCount <= gcCompletedCount <= gcStartedCount
            if (gcStartedCount != gcCompletedCount) {
                // A GC is in progress, local cache is out of date by definition but can't update yet
                // Sanity check; collection count increases monotonically
                assert  gcCompletedCount < gcStartedCount;
            } else if (oldGcStartedCount != gcStartedCount) {
                // GC is not in progress, but a GC has completed since the last time
                // we checked, so cached reference data is out of date
                // Sanity check; collection count increases monotonically
                assert oldGcStartedCount < gcStartedCount;
                vm().teleReferenceScheme().refresh();
            } else {
                // oldGcStartedCount == gcStartedCount == gcCompletedCount
                // GC is not in progress, and no new GCs have happened, so cached reference data is up to date
            }

            updatingHeapMemoryRegions = true;
            final List<MaxHeapRegion> heapRegions = new ArrayList<MaxHeapRegion>(allHeapRegions.size());
            heapRegions.add(bootHeapRegion);
            final Reference runtimeHeapRegionsArrayReference = vm().teleFields().InspectableHeapInfo_dynamicHeapMemoryRegions.readReference(vm());
            if (!runtimeHeapRegionsArrayReference.isZero()) {
                final TeleArrayObject teleArrayObject = (TeleArrayObject) makeTeleObject(runtimeHeapRegionsArrayReference);
                final Reference[] heapRegionReferences = (Reference[]) teleArrayObject.shallowCopy();
                TeleRuntimeMemoryRegion[] teleRuntimeMemoryRegions = new TeleRuntimeMemoryRegion[heapRegionReferences.length];
                int next = 0;
                for (int i = 0; i < heapRegionReferences.length; i++) {
                    final TeleRuntimeMemoryRegion teleRegion = (TeleRuntimeMemoryRegion) makeTeleObject(heapRegionReferences[i]);
                    if (teleRegion != null) {
                        final TeleHeapRegion teleHeapRegion = regionToTeleHeapRegion.get(teleRegion);
                        if (teleHeapRegion != null) {
                            // We've seen this VM heap region object before and already have an entity that models the state
                            heapRegions.add(teleHeapRegion);
                        } else {
                            final TeleHeapRegion newTeleHeapRegion = new TeleHeapRegion(vm(), teleRegion, false);
                            heapRegions.add(newTeleHeapRegion);
                            regionToTeleHeapRegion.put(teleRegion, newTeleHeapRegion);
                        }
                        teleRuntimeMemoryRegions[next++] = teleRegion;
                    } else {
                        // This can happen when inspecting VM startup
                    }
                }
                // Copy result to self, removing any null memory regions.
                if (next != teleRuntimeMemoryRegions.length) {
                    teleRuntimeMemoryRegions = Arrays.copyOf(teleRuntimeMemoryRegions, next);
                }
            }
            updatingHeapMemoryRegions = false;
            if (teleRootsRegion == null) {
                final Reference teleRootsRegionReference = vm().teleFields().InspectableHeapInfo_rootTableMemoryRegion.readReference(vm());
                if (teleRootsRegionReference != null && !teleRootsRegionReference.isZero()) {
                    final TeleRuntimeMemoryRegion maybeAllocatedRegion = (TeleRuntimeMemoryRegion) makeTeleObject(teleRootsRegionReference);
                    if (maybeAllocatedRegion != null && maybeAllocatedRegion.isAllocated()) {
                        teleRootsRegion = maybeAllocatedRegion;
                        rootsRegion = new TeleFixedMemoryRegion(vm(), maybeAllocatedRegion.getRegionName(), maybeAllocatedRegion.getRegionStart(), maybeAllocatedRegion.getRegionSize());
                    }
                }
            }

            if (teleImmortalHeapRegion == null) {
                final Reference immortalHeapReference = vm().teleFields().ImmortalHeap_immortalHeap.readReference(vm());
                if (immortalHeapReference != null && !immortalHeapReference.isZero()) {
                    final TeleRuntimeMemoryRegion maybeAllocatedRegion = (TeleRuntimeMemoryRegion) makeTeleObject(immortalHeapReference);
                    if (maybeAllocatedRegion != null && maybeAllocatedRegion.isAllocated()) {
                        teleImmortalHeapRegion = maybeAllocatedRegion;
                        immortalHeapRegion = new TeleHeapRegion(vm(), teleImmortalHeapRegion, false);
                    }
                }
            }
            if (immortalHeapRegion != null) {
                heapRegions.add(immortalHeapRegion);
            }
            allHeapRegions = Collections.unmodifiableList(heapRegions);
            updateTracer.end(statsPrinter);
        }
    }

    public String entityName() {
        return entityName;
    }

    public String entityDescription() {
        return entityDescription;
    }

    public MaxEntityMemoryRegion<MaxHeap> memoryRegion() {
        // The heap has no VM memory allocated, other than the regions allocated directly from the OS.
        return null;
    }

    public boolean contains(Address address) {
        if (updatingHeapMemoryRegions) {
            // The call is nested within a call to {@link #refresh}, assume all is well in order
            // avoid circularity problems while updating the heap region list.
            return true;
        }
        return findHeapRegion(address) != null;
    }

    /**
     * @return description of the special heap region in the {@link BootImage} of the VM.
     */
    public TeleHeapRegion bootHeapRegion() {
        return bootHeapRegion;
    }

    /**
     * @return description of the immortal heap region of the VM.
     */
    public TeleHeapRegion immortalHeapRegion() {
        return immortalHeapRegion;
    }

    public List<MaxHeapRegion> heapRegions() {
        return allHeapRegions;
    }

    public MaxHeapRegion findHeapRegion(Address address) {
        final List<MaxHeapRegion> heapRegions = allHeapRegions;
        for (MaxHeapRegion heapRegion : heapRegions) {
            if (heapRegion.memoryRegion().contains(address)) {
                return heapRegion;
            }
        }
        return null;
    }

    public boolean containsInDynamicHeap(Address address) {
        final MaxHeapRegion heapRegion = findHeapRegion(address);
        return heapRegion != null && !heapRegion.isBootRegion() && !heapRegion.equals(immortalHeapRegion);
    }

    public MaxMemoryRegion rootsMemoryRegion() {
        return rootsRegion;
    }

    private static  final int MAX_VM_LOCK_TRIALS = 100;

    public TeleObject findTeleObject(Reference reference) throws MaxVMBusyException {
        if (vm().tryLock(MAX_VM_LOCK_TRIALS)) {
            try {
                return makeTeleObject(reference);
            } finally {
                vm().unlock();
            }
        } else {
            throw new MaxVMBusyException();
        }
    }

    public TeleObject makeTeleObject(Reference reference) {
        return teleObjectFactory.make(reference);
    }


    public TeleObject findObjectByOID(long id) {
        return teleObjectFactory.lookupObject(id);
    }

    public TeleObject findObjectAt(Address origin) {
        if (vm().tryLock(MAX_VM_LOCK_TRIALS)) {
            try {
                return makeTeleObject(vm().originToReference(origin.asPointer()));
            } catch (Throwable throwable) {
                // Can't resolve the address somehow
            } finally {
                vm().unlock();
            }
        }
        return null;
    }

    public TeleObject findObjectFollowing(Address cellAddress, long maxSearchExtent) {

        // Search limit expressed in words
        long wordSearchExtent = Long.MAX_VALUE;
        final Size wordSize = vm().wordSize();
        if (maxSearchExtent > 0) {
            wordSearchExtent = maxSearchExtent / wordSize.toInt();
        }
        try {
            Pointer origin = cellAddress.asPointer();
            for (long count = 0; count < wordSearchExtent; count++) {
                origin = origin.plus(wordSize);
                if (vm().isValidOrigin(origin)) {
                    return makeTeleObject(vm().originToReference(origin));
                }
            }
        } catch (Throwable throwable) {
        }
        return null;
    }

    public TeleObject findObjectPreceding(Address cellAddress, long maxSearchExtent) {

        // Search limit expressed in words
        long wordSearchExtent = Long.MAX_VALUE;
        final Size wordSize = vm().wordSize();
        if (maxSearchExtent > 0) {
            wordSearchExtent = maxSearchExtent / wordSize.toInt();
        }
        try {
            Pointer origin = cellAddress.asPointer();
            for (long count = 0; count < wordSearchExtent; count++) {
                origin = origin.minus(wordSize);
                if (vm().isValidOrigin(origin)) {
                    return makeTeleObject(vm().originToReference(origin));
                }
            }
        } catch (Throwable throwable) {
        }
        return null;
    }

    /**
     * Finds an object in the VM that has been located at a particular place in memory, but which
     * may have been relocated.
     * <br>
     * Must be called in thread holding the VM lock
     *
     * @param origin an object origin in the VM
     * @return the object originally at the origin, possibly relocated
     */
    public TeleObject getForwardedObject(Pointer origin) {
        final Reference forwardedObjectReference = vm().originToReference(getForwardedOrigin(origin));
        return teleObjectFactory.make(forwardedObjectReference);
    }


    /**
     * Avoid potential circularity problems by handling heap queries specially when we
     * know we are in a refresh cycle during which information about heap regions may not
     * be well formed.  This variable is true during those periods.
     */
    private boolean updatingHeapMemoryRegions = false;

    /**
     * Gets the name used by the VM to identify the distinguished
     * boot heap region, determined by static inspection of the field
     * that holds the value in the VM.
     *
     * @return the name assigned to the VM's boot heap memory region
     * @see Heap
     */
    public String bootHeapRegionName() {
        return bootHeapRegionName;
    }

    /**
     * Gets the raw location of the tele roots table in VM memory.  This is a specially allocated
     * region of memory that is assumed will not move.
     * <br>
     * It is equivalent to the starting location of the roots region, but must be
     * accessed this way instead to avoid a circularity.  It is used before
     * more abstract objects such as {@link TeleFixedMemoryRegion}s can be created.
     *
     * @return location of the specially allocated VM memory region where teleRoots are stored.
     * @see TeleRoots
     * @see InspectableHeapInfo
     */
    public Pointer teleRootsPointer() {
        return teleRootsPointer;
    }

    /**
     * @ return is the VM in GC
     */
    public boolean isInGC() {
        return gcCompletedCount != gcStartedCount;
    }

    public Class heapSchemeClass() {
        return teleHeapScheme.heapSchemeClass();
    }

    public List<MaxCodeLocation> inspectableMethods() {
        if (inspectableMethods == null) {
            final List<MaxCodeLocation> locations = new ArrayList<MaxCodeLocation>();
            locations.add(CodeLocation.createMachineCodeLocation(vm(), vm().teleMethods().HeapScheme$Inspect_inspectableIncreaseMemoryRequested, "Increase heap memory"));
            locations.add(CodeLocation.createMachineCodeLocation(vm(), vm().teleMethods().HeapScheme$Inspect_inspectableDecreaseMemoryRequested, "Decrease heap memory"));
            // There may be implementation-specific methods of interest
            locations.addAll(teleHeapScheme.inspectableMethods());
            inspectableMethods = Collections.unmodifiableList(locations);
        }
        return inspectableMethods;
    }

    public Offset gcForwardingPointerOffset() {
        return teleHeapScheme.gcForwardingPointerOffset();
    }

    public boolean isInLiveMemory(Address address) {
        return teleHeapScheme.isInLiveMemory(address);
    }

    public  boolean isObjectForwarded(Pointer origin) {
        return teleHeapScheme.isObjectForwarded(origin);
    }

    public boolean isForwardingPointer(Pointer pointer) {
        return teleHeapScheme.isForwardingPointer(pointer);
    }

    public Pointer getTrueLocationFromPointer(Pointer pointer) {
        return teleHeapScheme.getTrueLocationFromPointer(pointer);
    }

    public Pointer getForwardedOrigin(Pointer origin) {
        return teleHeapScheme.getForwardedOrigin(origin);
    }

    public void printStats(PrintStream printStream, int indent, boolean verbose) {
        final String indentation = Strings.times(' ', indent);
        final NumberFormat formatter = NumberFormat.getInstance();
        Size totalHeapSize = Size.zero();
        for (MaxHeapRegion region : allHeapRegions) {
            totalHeapSize = totalHeapSize.plus(region.memoryRegion().size());
        }
        printStream.print(indentation + "No. regions: " + allHeapRegions.size() +
                        " (" + "total size: " + formatter.format(totalHeapSize.toLong()) + " bytes\n");
        printStream.print(indentation + "Inspection references: " + formatter.format(teleObjectFactory.referenceCount()) +
                        " (" + formatter.format(teleObjectFactory.liveObjectCount()) + " live)\n");
    }

}
