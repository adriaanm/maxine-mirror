/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.ins.method;

import static com.sun.max.ins.gui.Inspector.MenuKind.*;
import static com.sun.max.tele.MaxProcessState.*;

import java.util.*;

import com.sun.max.ins.*;
import com.sun.max.ins.gui.*;
import com.sun.max.ins.method.MethodInspectorContainer.MethodViewManager;
import com.sun.max.ins.view.InspectionViews.ViewKind;
import com.sun.max.lang.*;
import com.sun.max.program.*;
import com.sun.max.tele.*;
import com.sun.max.tele.object.*;
import com.sun.max.unsafe.*;

/**
 * An inspector that can present one or more code representations of a method. MethodInspectors are unique, keyed from
 * an instance of {@link TeleClassMethodActor}.
 * <p>
 * Views are managed via the container class {@link MethodInspectorContainer}, in which method inspectors are displayed.
 * <p>
 * Instance view creation follows the user focus, and a static listener here drives the factory for method views.
 *
 *
 * @author Michael Van De Vanter
 * @author Doug Simon
 */
public abstract class MethodInspector extends Inspector<MethodInspector> {

    private static final int TRACE_VALUE = 2;
    private static final ViewKind VIEW_KIND = ViewKind.METHOD_CODE;

    private static ViewFocusListener methodFocusListener;

    /**
     * Gets (singleton) listener that can be added to focus listeners, which causes the MethodInspector factory
     * to create/highlight a view on the method in focus.
     *
     * @param inspection
     * @return
     */
    static ViewFocusListener methodFocusListener(final Inspection inspection) {
        if (methodFocusListener == null) {
            methodFocusListener = new InspectionFocusAdapter() {

                @Override
                public void codeLocationFocusSet(MaxCodeLocation codeLocation, boolean interactiveForNative) {
                    if (codeLocation != null && ViewKind.METHODS.viewManager().isActive()) {
                        try {
                            final MethodInspector methodInspector = MethodInspector.make(inspection, codeLocation, interactiveForNative);
                            if (methodInspector != null) {
                                methodInspector.setCodeLocationFocus();
                                methodInspector.highlightIfNotVisible();
                            }
                        } catch (MaxVMBusyException maxVMBusyException) {
                            inspection.announceVMBusyFailure("Can't view method");
                        }
                    }
                }
            };
        }
        return methodFocusListener;
    }

    /**
     * Makes an inspector displaying code for the method pointed to by the instructionPointer. Should always work for
     * Java methods. For external native methods, only works if the code block is already known to the inspector or if the user
     * supplies some additional information at an optional prompt.
     *
     * @param address machine code location in the VM.
     * @param interactive Should user be prompted for additional address information in case the location is unknown
     *            native code.
     * @return A possibly new inspector, null if unable to view.
     * @throws MaxVMBusyException if creation of a new inspection fails because the VM is unavailable
     */
    private static MethodInspector make(final Inspection inspection, Address address, boolean interactive) throws MaxVMBusyException {
        MethodInspector methodInspector = null;
        final MaxCompiledCode compiledCode = inspection.vm().codeCache().findCompiledCode(address);
        if (compiledCode != null) {
            // Java method
            methodInspector = make(inspection, compiledCode, MethodCodeKind.MACHINE_CODE);
        } else {
            final MaxExternalCode externalCode = inspection.vm().codeCache().findExternalCode(address);
            if (externalCode != null) {
                // Some other kind of known external machine code
                methodInspector = make(inspection, externalCode);
            } else if (interactive) {
                // Code location is not in a Java method or runtime stub and has not yet been viewed in a native routine.
                // Give the user a chance to guess at its length so we can register and view it
                final MutableInnerClassGlobal<MethodInspector> result = new MutableInnerClassGlobal<MethodInspector>();
                final String defaultDescription = "Native code @0x" + address.toHexString();
                new NativeLocationInputDialog(inspection, "Name unknown native code", address, MaxExternalCode.DEFAULT_NATIVE_CODE_LENGTH, defaultDescription) {
                    @Override
                    public void entered(Address nativeAddress, long nBytes, String enteredName) {
                        try {
                            String name = enteredName;
                            if (name == null || name.equals("")) {
                                name = defaultDescription;
                            }
                            final MaxExternalCode externalCode = vm().codeCache().createExternalCode(nativeAddress, nBytes, name);
                            result.setValue(MethodInspector.make(inspection, externalCode));
                            // inspection.focus().setCodeLocation(new TeleCodeLocation(inspection.teleVM(), nativeAddress));
                        } catch (IllegalArgumentException illegalArgumentException) {
                            inspection.gui().errorMessage("Specified external code range overlaps region already registered in Inpsector");
                        } catch (MaxVMBusyException maxVMBusyException) {
                            inspection.announceVMBusyFailure("inspect native code");
                        } catch (MaxInvalidAddressException e) {
                            inspection.gui().errorMessage("Unable to read memory at " + nativeAddress.to0xHexString());
                            e.printStackTrace();
                        }
                    }
                    @Override
                    public boolean isValidSize(long nBytes) {
                        return nBytes > 0;
                    }
                };
                methodInspector = result.value();
            }
        }
        return methodInspector;
    }

    private static final Map<MaxMachineCode, MethodInspector> machineCodeToMethodInspector =
        new IdentityHashMap<MaxMachineCode, MethodInspector>();

    private static final Map<TeleClassMethodActor, MethodInspector> teleClassMethodActorToMethodInspector =
        new IdentityHashMap<TeleClassMethodActor, MethodInspector>();

    /**
     * Makes an inspector displaying code for specified code location. Should always work for
     * Java methods. For native methods, only works if the code block is already known.
     *
     * @param codeLocation a code location
     * @return A possibly new inspector, null if unable to view.
     * @throws MaxVMBusyException if trying to visit a method not yet seen and the VM is unavailable
     */
    private static MethodInspector make(Inspection inspection, MaxCodeLocation codeLocation, boolean interactiveForNative) throws MaxVMBusyException {
        if (codeLocation.hasAddress()) {
            return make(inspection, codeLocation.address(), interactiveForNative);
        }
        if (codeLocation.hasTeleClassMethodActor()) {
            // TODO (mlvdv)  Select the specified bytecode position
            return make(inspection, codeLocation.teleClassMethodActor(), MethodCodeKind.BYTECODES);
        }
        // Has neither machine nor bytecode location specified.
        return null;
    }

    /**
     * Display an inspector for a Java method, showing the kind of code requested if available. If an inspector for the
     * method doesn't exist, create a new one and display the kind of code requested if available. if an inspector for
     * the method does exist, add a display of the kind of code requested if available.
     *
     * @return A possibly new inspector for the method.
     * @throws MaxVMBusyException if creation of a new method inspection fails because the VM is unavailable
     */
    private static JavaMethodInspector make(Inspection inspection, TeleClassMethodActor teleClassMethodActor, MethodCodeKind codeKind) throws MaxVMBusyException {
        JavaMethodInspector javaMethodInspector = null;
        // If there are compilations, then inspect in association with the most recent
        final MaxCompiledCode compiledCode = inspection.vm().codeCache().latestCompilation(teleClassMethodActor);
        if (compiledCode != null) {
            return make(inspection, compiledCode, codeKind);
        }
        final MethodInspector methodInspector = teleClassMethodActorToMethodInspector.get(teleClassMethodActor);
        if (methodInspector == null) {
            final MethodViewManager methodViewManager = (MethodViewManager) ViewKind.METHODS.viewManager();
            final MethodInspectorContainer container = methodViewManager.activateView(inspection);
            inspection.vm().acquireLegacyVMAccess();
            try {
                javaMethodInspector = new JavaMethodInspector(inspection, container, teleClassMethodActor, codeKind);
                container.add(javaMethodInspector);
                teleClassMethodActorToMethodInspector.put(teleClassMethodActor, javaMethodInspector);
            } finally {
                inspection.vm().releaseLegacyVMAccess();
            }

        } else {
            javaMethodInspector = (JavaMethodInspector) methodInspector;
        }
        return javaMethodInspector;
    }

    /**
     * Gets the {@link MethodInspector} associated with a specific compilation of a Java method in the VM,
     * creating a new one if necessary, and makes the requested code visible.
     *
     * @return a possibly new inspector with the specified code visible.
     * @throws MaxVMBusyException if can't create a new method inspection because the VM is unavailable
     */
    private static JavaMethodInspector make(Inspection inspection, MaxCompiledCode compiledCode, MethodCodeKind codeKind) throws MaxVMBusyException {
        JavaMethodInspector javaMethodInspector = null;

        // Is there already an inspection open that is bound to this compilation?
        MethodInspector methodInspector = machineCodeToMethodInspector.get(compiledCode);
        if (methodInspector == null) {
            // No existing inspector is bound to this compilation; see if there is an inspector for this method that is
            // unbound
            inspection.vm().acquireLegacyVMAccess();
            try {
                TeleClassMethodActor teleClassMethodActor = compiledCode.getTeleClassMethodActor();
                if (teleClassMethodActor != null) {
                    methodInspector = teleClassMethodActorToMethodInspector.get(teleClassMethodActor);
                }
                final MethodViewManager methodViewManager = (MethodViewManager) ViewKind.METHODS.viewManager();
                final MethodInspectorContainer container = methodViewManager.activateView(inspection);
                if (methodInspector == null) {
                    // No existing inspector exists for this method; create new one bound to this compilation
                    javaMethodInspector = new JavaMethodInspector(inspection, container, compiledCode, codeKind);
                } else {
                    // An inspector exists for the method, but not bound to any compilation; bind it to this compilation
                    // TODO (mlvdv) Temp patch; just create a new one in this case too.
                    javaMethodInspector = new JavaMethodInspector(inspection, container, compiledCode, codeKind);
                }
                if (javaMethodInspector != null) {
                    container.add(javaMethodInspector);
                    machineCodeToMethodInspector.put(compiledCode, javaMethodInspector);
                }
            } finally {
                inspection.vm().releaseLegacyVMAccess();
            }
        } else {
            // An existing inspector is bound to this method & compilation; ensure that it has the requested code view
            javaMethodInspector = (JavaMethodInspector) methodInspector;
            javaMethodInspector.viewCodeKind(codeKind);
        }
        return javaMethodInspector;
    }

    /**
     * @return A possibly new inspector for a block of native code in the VM already known to the inspector.
     * @throws MaxVMBusyException if a new inspector cannot be created because the VM is unavailable
     */
    private static NativeMethodInspector make(Inspection inspection, MaxExternalCode maxExternalCode) throws MaxVMBusyException {
        NativeMethodInspector nativeMethodInspector = null;
        MethodInspector methodInspector = machineCodeToMethodInspector.get(maxExternalCode);
        if (methodInspector == null) {
            inspection.vm().acquireLegacyVMAccess();
            try {
                final MethodViewManager methodViewManager = (MethodViewManager) ViewKind.METHODS.viewManager();
                final MethodInspectorContainer container = methodViewManager.activateView(inspection);
                container.add(nativeMethodInspector);
                machineCodeToMethodInspector.put(maxExternalCode, nativeMethodInspector);
            } finally {
                inspection.vm().releaseLegacyVMAccess();
            }
        } else {
            nativeMethodInspector = (NativeMethodInspector) methodInspector;
        }
        return nativeMethodInspector;
    }

    private final MethodInspectorContainer container;

    protected MethodInspector(Inspection inspection, MethodInspectorContainer container) {
        super(inspection, VIEW_KIND, null);
        this.container = container;
    }

    @Override
    public InspectorFrame createTabFrame(TabbedInspector<MethodInspector> parent) {

        final InspectorFrame frame = super.createTabFrame(parent);

        // The default menu operates from the perspective of the parent container.
        frame.makeMenu(DEFAULT_MENU).add(defaultMenuItems(DEFAULT_MENU, parent));

        frame.makeMenu(EDIT_MENU);

        final InspectorMenu memoryMenu = frame.makeMenu(MEMORY_MENU);
        memoryMenu.add(actions().inspectMachineCodeRegionMemory(machineCode()));
        memoryMenu.add(defaultMenuItems(MEMORY_MENU));
        memoryMenu.add(actions().activateSingletonView(ViewKind.ALLOCATIONS));

        frame.makeMenu(OBJECT_MENU);

        frame.makeMenu(CODE_MENU);

        frame.makeMenu(DEBUG_MENU);

        frame.makeMenu(VIEW_MENU).add(defaultMenuItems(VIEW_MENU));
        return frame;
    }

    /**
     * Updates the code selection to agree with the current focus.
     */
    private void setCodeLocationFocus() {
        codeLocationFocusSet(inspection().focus().codeLocation(), false);
    }

    @Override
    public void breakpointStateChanged() {
        // TODO (mlvdv)  Data reading PATCH, there should be a more systematic way of handling this.
        if (vm().state().processState() != TERMINATED) {
            forceRefresh();
        }
    }

    public void close() {
        container.close(this);
    }

    public void closeOthers() {
        container.closeOthers(this);
    }

    /**
     * @return Local {@link MaxMachineCode} for the method in the VM; null if not bound to compiled code yet.
     */
    public abstract MaxMachineCode machineCode();

    /**
     * @return Java method information; null if not known to be associated with a Java method.
     */
    public abstract TeleClassMethodActor teleClassMethodActor();

    /**
     * @return Text suitable for a tool tip.
     */
    public abstract String getToolTip();

    /**
     * Prints the content of the method display.
     */
    public abstract void print();

    /**
     * @param codeViewer Code view that should be closed and removed from the visual inspection; if this is the only
     *            view in the method inspection, then dispose of the method inspection as well.
     */
    public abstract void closeCodeViewer(CodeViewer codeViewer);

    @Override
    public void inspectorClosing() {
        Trace.line(1, tracePrefix() + " closing for " + getTitle());
        machineCodeToMethodInspector.remove(machineCode());
        teleClassMethodActorToMethodInspector.remove(teleClassMethodActor());
        super.inspectorClosing();
    }

}
