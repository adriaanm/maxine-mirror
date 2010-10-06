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
package com.sun.max.ins;

import java.util.*;

import com.sun.cri.ci.*;
import com.sun.max.ins.debug.*;
import com.sun.max.ins.util.*;
import com.sun.max.program.*;
import com.sun.max.tele.*;
import com.sun.max.tele.object.*;
import com.sun.max.unsafe.*;

/**
 * Holds the focus of user attention, expressed by user actions
 * that select something.
 *
 *  Implements a user model policy that changes
 *  focus as a side effect of some other user action.
 *
 * Other kinds of items could also have focus in the user model.
 *
 * @author Michael Van De Vanter
 */
public class InspectionFocus extends AbstractInspectionHolder {

    private static final int TRACE_VALUE = 2;
    private boolean settingCodeLocation = false;

    public InspectionFocus(Inspection inspection) {
        super(inspection);
    }

    private Set<ViewFocusListener> listeners = CiUtil.newIdentityHashSet();

    public void addListener(ViewFocusListener listener) {
        Trace.line(TRACE_VALUE, tracePrefix() + "adding listener: " + listener);
        listeners.add(listener);
    }

    public void removeListener(ViewFocusListener listener) {
        Trace.line(TRACE_VALUE, tracePrefix() + " removing listener: " + listener);
        listeners.remove(listener);
    }

    public ViewFocusListener[] copyListeners() {
        return listeners.toArray(new ViewFocusListener[listeners.size()]);
    }

    private MaxCodeLocation codeLocation = null;

    private final Object codeLocationTracer = new Object() {
        @Override
        public String toString() {
            return tracePrefix() + "Focus (Code Location):  " + inspection().nameDisplay().longName(codeLocation);
        }
    };

    public void clearAll() {
        thread = null;
        stackFrame = null;
        memoryRegion = null;
        breakpoint  = null;
        heapObject = null;
    }

    /**
     * The current location of user interest in the code being inspected (view state).
     */
    public MaxCodeLocation codeLocation() {
        return codeLocation;
    }

    /**
     * Is there a currently selected code location.
     */
    public boolean hasCodeLocation() {
        return codeLocation != null;
    }

    /**
     * Selects a code location that is of immediate visual interest to the user.
     * This is view state only, not necessarily related to VM execution.
     *
     * @param codeLocation a location in code in the VM
     * @param interactiveForNative should user be prompted interactively if in native code without
     * any meta information?
     */
    public void setCodeLocation(MaxCodeLocation codeLocation, boolean interactiveForNative) {
        // Terminate any loops in focus setting.
        if (!settingCodeLocation) {
            settingCodeLocation = true;
            try {
                this.codeLocation = codeLocation;
                Trace.line(TRACE_VALUE, codeLocationTracer);
                for (ViewFocusListener listener : copyListeners()) {
                    listener.codeLocationFocusSet(codeLocation, interactiveForNative);
                }
                // User Model Policy: when setting code location, if it happens to match a stack frame of the current thread then focus on that frame.
                if (thread != null && codeLocation.hasAddress()) {
                    for (MaxStackFrame maxStackFrame : thread.stack().frames()) {
                        if (codeLocation.isSameAs(maxStackFrame.codeLocation())) {
                            setStackFrame(stackFrame, false);
                            break;
                        }
                    }
                }
            } finally {
                settingCodeLocation = false;
            }
        }
    }

    /**
     * Selects a code location that is of immediate visual interest to the user.
     * This is view state only, not necessarily related to VM execution.
     *
     * @param codeLocation a location in code in the VM
     */
    public void setCodeLocation(MaxCodeLocation codeLocation) {
        setCodeLocation(codeLocation, false);
    }

    private MaxThread thread;

    private final Object threadFocusTracer = new Object() {
        @Override
        public String toString() {
            final StringBuilder name = new StringBuilder();
            name.append(tracePrefix() + "Focus (Thread): ").append(inspection().nameDisplay().longName(thread)).append(" {").append(thread).append("}");
            return name.toString();
        }
    };

    /**
     * @return the {@link MaxThread} that is the current user focus (view state); non-null once set.
     */
    public MaxThread thread() {
        return thread;
    }

    /**
     * Is there a currently selected thread.
     */
    public boolean hasThread() {
        return thread != null;
    }

    /**
     * Shifts the focus of the Inspection to a particular thread; notify interested inspectors.
     * Sets the code location to the current InstructionPointer of the newly focused thread.
     * This is a view state change that can happen when there is no change to VM  state.
     */
    public void setThread(MaxThread thread) {
        assert thread != null;
        if (!thread.equals(this.thread)) {
            final MaxThread oldThread = this.thread;
            this.thread = thread;
            Trace.line(TRACE_VALUE, threadFocusTracer);
            for (ViewFocusListener listener : copyListeners()) {
                listener.threadFocusSet(oldThread, thread);
            }
            // User Model Policy:  when thread focus changes, restore an old frame focus if possible.
            // If no record of a prior choice and thread is at a breakpoint, focus there.
            // Else focus on the top frame.
            final MaxStackFrame previousStackFrame = frameSelections.get(thread);
            MaxStackFrame newStackFrame = null;
            if (previousStackFrame != null) {
                for (MaxStackFrame stackFrame : this.thread.stack().frames()) {
                    if (stackFrame.isSameFrame(previousStackFrame)) {
                        newStackFrame = stackFrame;
                        break;
                    }
                }
            }
            if (newStackFrame != null) {
                // Reset frame selection to one previously selected by the user
                setStackFrame(newStackFrame, false);
            } else {
                // No prior frame selection
                final MaxBreakpoint breakpoint = this.thread.breakpoint();
                if (breakpoint != null && !breakpoint.isTransient()) {
                    // thread is at a breakpoint; focus on the breakpoint, which should also cause focus on code and frame
                    setBreakpoint(breakpoint);
                } else {
                    // default is to focus on the top frame
                    setStackFrame(thread.stack().top(), false);
                }
            }
            // User Model Policy:  when thread focus changes, also set the memory region focus to the thread's stack memory.
            setMemoryRegion(this.thread.stack().memoryRegion());

        }
    }

    // Remember most recent frame selection per thread, and restore this selection (if possible) when thread focus changes.
    private final Map<MaxThread, MaxStackFrame> frameSelections = new HashMap<MaxThread, MaxStackFrame>();

    private MaxStackFrame stackFrame;
    // Since frames don't record what stack they're in, we must keep a reference to the thread of the frame.
    private MaxThread threadForStackFrame;

    private final Object stackFrameFocusTracer = new Object() {
        @Override
        public String toString() {
            return tracePrefix() + "Focus (StackFrame):  " + stackFrame;
        }
    };

    /**
     * @return the {@link MaxStackFrame} that is current user focus (view state).
     */
    public MaxStackFrame stackFrame() {
        return stackFrame;
    }

    /**
     * Shifts the focus of the Inspection to a particular stack frame in a particular thread; notify interested inspectors.
     * Sets the current thread to be the thread of the frame.
     * This is a view state change that can happen when there is no change to VM state.
     * @param newStackFrame the frame on which to focus.
     * @param interactiveForNative whether (should a side effect be to land in a native method) the user should be consulted if unknown.
     */
    public void setStackFrame(MaxStackFrame newStackFrame, boolean interactiveForNative) {
        final MaxThread newThread = newStackFrame.stack().thread();
        if (!newThread.equals(this.threadForStackFrame) || !newStackFrame.isSameFrame(this.stackFrame)) {
            final MaxStackFrame oldStackFrame = this.stackFrame;
            this.threadForStackFrame = newThread;
            this.stackFrame = newStackFrame;
            frameSelections.put(newThread, newStackFrame);
            Trace.line(TRACE_VALUE, stackFrameFocusTracer);
            // For consistency, be sure we're in the right thread context before doing anything with the stack frame.
            setThread(newThread);
            for (ViewFocusListener listener : copyListeners()) {
                listener.stackFrameFocusChanged(oldStackFrame, newStackFrame);
            }
        }
        // User Model Policy:  When a stack frame becomes the focus, then also focus on the code at the frame's instruction pointer
        // or call return location.
        // Update code location, even if stack frame is the "same", where same means at the same logical position in the stack as the old one.
        // Note that the old and new stack frames are not identical, and in fact may have different instruction pointers.
        final MaxCodeLocation newCodeLocation = newStackFrame.codeLocation();
        if (this.codeLocation == null || !this.codeLocation.isSameAs(newCodeLocation)) {
            setCodeLocation(newCodeLocation, interactiveForNative);
        }
    }

    // never null, zero if none set
    private Address address = Address.zero();

    private final Object addressFocusTracer = new Object() {
        @Override
        public String toString() {
            final StringBuilder name = new StringBuilder();
            name.append(tracePrefix()).append("Focus (Address): ").append(address.toHexString());
            return name.toString();
        }
    };

    /**
     * @return the {@link Address} that is the current user focus (view state), {@link Address#zero()} if none.
     */
    public Address address() {
        return address;
    }

    /**
     * Is there a currently selected {@link Address}.
     */
    public boolean hasAddress() {
        return  !address.isZero();
    }

    /**
     * Shifts the focus of the Inspection to a particular {@link Address}; notify interested inspectors.
     * This is a view state change that can happen when there is no change to the VM state.
     */
    public void setAddress(Address address) {
        InspectorError.check(address != null, "setAddress(null) should use zero Address instead");
        if ((address.isZero() && hasAddress()) || (!address.isZero() && !address.equals(this.address))) {
            final Address oldAddress = this.address;
            this.address = address;
            Trace.line(TRACE_VALUE, addressFocusTracer);
            for (ViewFocusListener listener : copyListeners()) {
                listener.addressFocusChanged(oldAddress, address);
            }
            // User Model Policy:  select the memory region that contains the newly selected address; clears if not known.
            // If
            setMemoryRegion(vm().findMemoryRegion(address));
        }
    }

    private MaxMemoryRegion memoryRegion;

    private final Object memoryRegionFocusTracer = new Object() {
        @Override
        public String toString() {
            final StringBuilder name = new StringBuilder();
            name.append(tracePrefix()).append("Focus (MemoryRegion): ").append(memoryRegion.regionName());
            return name.toString();
        }
    };

    /**
     * @return the {@linkplain MaxMemoryRegion memory region} that is the current user focus (view state).
     */
    public MaxMemoryRegion memoryRegion() {
        return memoryRegion;
    }

    /**
     * Is there a currently selected {@linkplain MaxMemoryRegion memory region}.
     */
    public boolean hasMemoryRegion() {
        return memoryRegion != null;
    }

    /**
     * Shifts the focus of the Inspection to a particular {@linkplain MaxMemoryRegion memory region}; notify interested inspectors.
     * If the region is a  stackRegion, then set the current thread to the thread owning the stack.
     * This is a view state change that can happen when there is no change to the VM state.
     */
    public void setMemoryRegion(MaxMemoryRegion memoryRegion) {
        // TODO (mlvdv) see about setting to null if a thread is observed to have died, or mark the region as dead?
        if ((memoryRegion == null && this.memoryRegion != null) || (memoryRegion != null && !memoryRegion.sameAs(this.memoryRegion))) {
            final MaxMemoryRegion oldMemoryRegion = this.memoryRegion;
            this.memoryRegion = memoryRegion;
            Trace.line(TRACE_VALUE, memoryRegionFocusTracer);
            for (ViewFocusListener listener : copyListeners()) {
                listener.memoryRegionFocusChanged(oldMemoryRegion, memoryRegion);
            }
            // User Model Policy:  When a stack memory region gets selected for focus, also set focus to the thread owning the stack.
//            if (_memoryRegion != null) {
//                final MaxThread thread = teleVM().threadContaining(_memoryRegion.start());
//                if (thread != null) {
//                    setThread(thread);
//                }
//            }
        }
    }

    private MaxBreakpoint breakpoint;

    private final Object breakpointFocusTracer = new Object() {
        @Override
        public String toString() {
            return tracePrefix() + "Focus(Breakpoint):  " + (breakpoint == null ? "null" : inspection().nameDisplay().longName(breakpoint.codeLocation()));
        }
    };

    /**
     * Currently selected breakpoint, typically controlled by the {@link BreakpointsInspector}.
     * May be null.
     */
    public MaxBreakpoint breakpoint() {
        return breakpoint;
    }

    /**
     * Is there a currently selected breakpoint in the BreakpointsInspector.
     */
    public boolean hasBreakpoint() {
        return breakpoint != null;
    }

    /**
     * Selects a breakpoint that is of immediate visual interest to the user, possibly null.
     * This is view state only, not necessarily related to VM execution.
     */
    public void setBreakpoint(MaxBreakpoint maxBreakpoint) {
        if (breakpoint != maxBreakpoint) {
            final MaxBreakpoint oldMaxBreakpoint = breakpoint;
            breakpoint = maxBreakpoint;
            Trace.line(TRACE_VALUE, breakpointFocusTracer);
            for (ViewFocusListener listener : copyListeners()) {
                listener.breakpointFocusSet(oldMaxBreakpoint, maxBreakpoint);
            }
        }
        if (maxBreakpoint != null) {
            MaxThread threadAtBreakpoint = null;
            for (MaxThread thread : vm().state().threads()) {
                if (thread.breakpoint() == maxBreakpoint) {
                    threadAtBreakpoint = thread;
                    break;
                }
            }
            // User Model Policy:  when a breakpoint acquires focus, also set focus to the
            // thread, if any, that is stopped at the breakpoint.  If no thread stopped,
            // then just focus on the code location.
            if (threadAtBreakpoint != null) {
                setStackFrame(threadAtBreakpoint.stack().top(), false);
            } else {
                setCodeLocation(maxBreakpoint.codeLocation());
            }
        }
    }

    private MaxWatchpoint watchpoint;

    private final Object watchpointFocusTracer = new Object() {
        @Override
        public String toString() {
            return tracePrefix() + "Focus(Watchpoint):  " + (watchpoint == null ? "null" : watchpoint.toString());
        }
    };

    /**
     * Currently selected watchpoint, typically controlled by the {@link WatchpointsInspector}.
     * May be null.
     */
    public MaxWatchpoint watchpoint() {
        return watchpoint;
    }

    /**
     * Is there a currently selected watchpoint in the WatchpointsInspector.
     */
    public boolean hasWatchpoint() {
        return watchpoint != null;
    }

    /**
     * Selects a watchpoint that is of immediate visual interest to the user, possibly null.
     * This is view state only, not necessarily related to VM execution.
     */
    public void setWatchpoint(MaxWatchpoint watchpoint) {
        if (this.watchpoint != watchpoint) {
            final MaxWatchpoint oldWatchpoint = this.watchpoint;
            this.watchpoint = watchpoint;
            Trace.line(TRACE_VALUE, watchpointFocusTracer);
            for (ViewFocusListener listener : copyListeners()) {
                listener.watchpointFocusSet(oldWatchpoint, watchpoint);
            }
        }
    }

    private TeleObject heapObject;

    private final Object objectFocusTracer = new Object() {
        @Override
        public String toString() {
            return tracePrefix() + "Focus(Heap Object):  " + (heapObject == null ? "null" : heapObject.toString());
        }
    };

    /**
     * Currently selected object in the tele VM heap; may be null.
     */
    public TeleObject heapObject() {
        return heapObject;
    }

    /**
     * Whether there is a currently selected heap object.
     */
    public boolean hasHeapObject() {
        return heapObject != null;
    }

    /**
     * Shifts the focus of the Inspection to a particular heap object in the VM; notify interested inspectors.
     * This is a view state change that can happen when there is no change to VM state.
     */
    public void setHeapObject(TeleObject heapObject) {
        if (this.heapObject != heapObject) {
            final TeleObject oldTeleObject = this.heapObject;
            this.heapObject = heapObject;
            Trace.line(TRACE_VALUE, objectFocusTracer);
            for (ViewFocusListener listener : copyListeners()) {
                listener.heapObjectFocusChanged(oldTeleObject, heapObject);
            }
        }
    }

}
