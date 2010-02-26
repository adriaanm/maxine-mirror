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

import java.io.*;
import java.lang.reflect.*;
import java.math.*;
import java.util.*;

import javax.swing.*;
import javax.swing.event.*;

import com.sun.max.asm.*;
import com.sun.max.asm.dis.*;
import com.sun.max.collect.*;
import com.sun.max.ins.debug.*;
import com.sun.max.ins.gui.*;
import com.sun.max.ins.java.*;
import com.sun.max.ins.memory.*;
import com.sun.max.ins.method.*;
import com.sun.max.ins.object.*;
import com.sun.max.ins.type.*;
import com.sun.max.io.*;
import com.sun.max.memory.*;
import com.sun.max.platform.*;
import com.sun.max.program.*;
import com.sun.max.tele.*;
import com.sun.max.tele.debug.TeleWatchpoint.*;
import com.sun.max.tele.interpreter.*;
import com.sun.max.tele.object.*;
import com.sun.max.unsafe.*;
import com.sun.max.util.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.bytecode.*;
import com.sun.max.vm.classfile.constant.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.cps.target.*;
import com.sun.max.vm.debug.*;
import com.sun.max.vm.layout.Layout.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.thread.*;
import com.sun.max.vm.type.*;
import com.sun.max.vm.value.*;

/**
 * Provider of {@link InspectorAction}s that are of general use.
 * <p>
 * <b>How to create an {@link InspectorAction} to perform "doSomething":</b>
 *
 * <ol>
 *
 * <li><b>Create an action class:</b>
 * <ul>
 * <li> {@code final class DoSometingAction extends InspectorAction}</li>
 * <li> The Action classes are in package scope so that they can be used by {@link InspectorKeyBindings}.</li>
 * <li> Add a title:  {@code private static final DEFAULT_NAME = "do something"}.</li>
 * <li> If the
 * action is interactive, for example if it produces a dialog, then the name should conclude with "...".
 * Capitalize the first word of the title but not the others, except for distinguished names such as
 * "Inspector" and acronyms.</li>
 * <li> For singletons, add a package scope constructor with one argument:  {@code String title}</li>
 * <li> For non-singletons, package scope constructor contains additional arguments that
 * customize the action, for example that specify to what "something" is to be done.</li>
 * <li> In the constructor: {@code super(inspection(), title == null ? DEFAULT_TITLE : title);}
 * (being able to override isn't used in many cases, but it adds flexibility).</li>
 * <li> If a singleton and if it contains state, for example enabled/disabled, that might change
 * depending on external circumstances, then register for general notification:
 * {@code _refreshableActions.append(this);} in the constructor.</li>
 * <li> Alternately, if state updates depend on a more specific kind of event, register
 * in the constructor explicitly for that event with a listener, for example
 * {@code focus().addListener(new InspectionFocusAdapter() { .... });}
 * The body of the listener should call {@code refresh}.</li>
 * <li>Override {@code protected void procedure()} with a method that does what
 * needs to be done.</li>
 * <li>If a singleton and if it contains state that might be changed depending on
 * external circumstances, override {@code public void refresh(boolean force)}
 * with a method that updates the state.</li>
 * </ul></li>
 *
 *<li><b>Create a singleton variable if needed</b>:
 *<ul>
 * <li>If the command is a singleton, create an initialized variable, static if possible.</li>
 * <li>{@code private static InspectorAction _doSomething = new DoSomethingAction(null);}</li>
 * </ul></li>
 *
 * <li><b>Create an accessor:</b>
 * <ul>
 * <li>Singleton: {@code public InspectorAction doSomething()}.</li>
 * <li> Singleton accessor returns the singleton variable.</li>
 * <li>Non-singletons have additional arguments that customize the action, e.g. specifying to what "something"
 * is to be done; they also take a {@code String title} argument that permits customization of the
 * action's name, for example when it appears in menus.</li>
 * <li> Non-singletons return {@code new DoSomethignAction(args, String title)}.</li>
 * <li>Add a descriptive Javadoc comment:  "@return an Action that does something".</li>
 * </ul></li>
 *
 * </ol>
 * <p>
 * @author Bernd Mathiske
 * @author Michael Van De Vanter
 * @author Aritra Bandyopadhyay
 */
public class InspectionActions extends AbstractInspectionHolder implements Prober{

    private static final int TRACE_VALUE = 2;

    /**
     * Name of the Action for searching in an Inspector view.
     */
    public static final String SEARCH_ACTION = "Search";

    /**
     * Actions that are held and shared; they have state that will be refreshed.
     * This is particularly important for actions that enable/disable, depending on the inspection state.
     */
    private final AppendableSequence<InspectorAction> refreshableActions = new ArrayListSequence<InspectorAction>();

    InspectionActions(Inspection inspection) {
        super(inspection);
        Trace.line(TRACE_VALUE, "InspectionActions initialized.");
    }

    public final void refresh(boolean force) {
        for (Prober prober : refreshableActions) {
            prober.refresh(force);
        }
    }

    public final void redisplay() {
        // non-op
    }

    /**
     * Action:  displays the {@link AboutDialog}.
     */
    final class AboutAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "About";

        AboutAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
        }

        @Override
        protected void procedure() {
            AboutDialog.create(inspection());
        }
    }

    private InspectorAction about = new AboutAction(null);

    /**
     * @return an Action that will display the {@link AboutDialog}.
     */
    public final InspectorAction about() {
        return about;
    }

    /**
     * Action:  displays the {@link PreferenceDialog}.
     */
    final class PreferencesAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Preferences";

        PreferencesAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
        }

        @Override
        protected void procedure() {
            PreferenceDialog.create(inspection());
        }
    }

    private InspectorAction preferences = new PreferencesAction(null);

    /**
     * @return an Action that will display the {@link PreferenceDialog}.
     */
    public final InspectorAction preferences() {
        return preferences;
    }

    /**
     * Action:  refreshes all data from the VM.
     */
    final class RefreshAllAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Refresh all views";

        RefreshAllAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
        }

        @Override
        protected void procedure() {
            inspection().refreshAll(true);
        }
    }

    private final InspectorAction refreshAll = new RefreshAllAction(null);

    /**
     * @return singleton Action that updates all displayed information read from the VM.
     */
    public final InspectorAction refreshAll() {
        return refreshAll;
    }

    /**
     * Action: causes a specific Inspector to become visible,
     * even if hidden or iconic.
     */
    final class ShowViewAction extends InspectorAction {

        private final Inspector inspector;

        ShowViewAction(Inspector inspector) {
            super(inspection(), inspector.getTextForTitle());
            this.inspector = inspector;
        }

        @Override
        protected void procedure() {
            inspector.highlight();
        }

    }

    /**
     * @return an action that will make a specified inspector become
     * visible, even if hidden or iconic.
     */
    public final InspectorAction showView(Inspector inspector) {
        return new ShowViewAction(inspector);
    }

    /**
     * Action:  close all open inspectors that match a predicate.
     */
    final class CloseViewsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Close views";

        private final Predicate<Inspector> predicate;

        CloseViewsAction(Predicate<Inspector> predicate, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.predicate = predicate;
        }

        @Override
        protected void procedure() {
            gui().removeInspectors(predicate);
        }
    }

    /**
     * Closes views matching a specified criterion.
     *
     * @param predicate a predicate that returns true for all Inspectors to be closed.
     * @param actionTitle a string name for the Action, uses default name if null.
     * @return an Action that will close views.
     */
    public final InspectorAction closeViews(Predicate<Inspector> predicate, String actionTitle) {
        return new CloseViewsAction(predicate, actionTitle);
    }

    /**
     * Closes all views of a specified type, optionally with an exception.
     *
     * @param inspectorType the type of Inspectors to be closed
     * @param exceptInspector an inspector that should not be closed
     * @param actionTitle a string name for the Action
     * @return an action that will close views
     */
    public final InspectorAction closeViews(final Class<? extends Inspector> inspectorType, final Inspector exceptInspector, String actionTitle) {
        Predicate<Inspector> predicate = new Predicate<Inspector>() {
            public boolean evaluate(Inspector inspector) {
                return inspectorType.isAssignableFrom(inspector.getClass()) && inspector != exceptInspector;
            }
        };
        return new CloseViewsAction(predicate, actionTitle);
    }

    /**
     * Action:  closes all open inspectors.
     */
    final class CloseAllViewsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Close all views";

        CloseAllViewsAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
        }

        @Override
        protected void procedure() {
            gui().removeInspectors(Predicate.Static.alwaysTrue(Inspector.class));
        }
    }

    private final InspectorAction closeAllViewsAction = new CloseAllViewsAction(null);

    /**
     * @return Singleton Action that closes all open inspectors.
     */
    public final InspectorAction closeAllViews() {
        return closeAllViewsAction;
    }

    /**
     * Action:  quits inspector.
     */
    final class QuitAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Quit Inspector";

        QuitAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
        }

        @Override
        protected void procedure() {
            inspection().quit();
        }
    }

    private final InspectorAction quitAction = new QuitAction(null);

    /**
     * @return Singleton Action that quits the VM inspection session.
     */
    public final InspectorAction quit() {
        return quitAction;
    }

    /**
     * Action:  relocates the boot image, assuming that the inspector was invoked
     * with the option {@link MaxineInspector#suspendingBeforeRelocating()} set.
     */
    final class RelocateBootImageAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Relocate Boot Image";

        RelocateBootImageAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
        }

        @Override
        protected void procedure() {
            try {
                maxVM().advanceToJavaEntryPoint();
            } catch (IOException ioException) {
                gui().errorMessage("error during relocation of boot image");
            }
            setEnabled(false);
        }
    }

    private final InspectorAction relocateBootImageAction = new RelocateBootImageAction(null);

    /**
     * @return Singleton Action that relocates the boot image, assuming that the inspector was invoked
     * with the option {@link MaxineInspector#suspendingBeforeRelocating()} set.
     */
    public final InspectorAction relocateBootImage() {
        return relocateBootImageAction;
    }

    /**
     * Action:  sets level of trace output in inspector code.
     */
    final class SetInspectorTraceLevelAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Set Inspector trace level...";

        SetInspectorTraceLevelAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
        }

        @Override
        protected void procedure() {
            final int oldLevel = Trace.level();
            int newLevel = oldLevel;
            final String input = gui().inputDialog(DEFAULT_TITLE, Integer.toString(oldLevel));
            if (input == null) {
                // User clicked cancel.
                return;
            }
            try {
                newLevel = Integer.parseInt(input);
            } catch (NumberFormatException numberFormatException) {
                gui().errorMessage(numberFormatException.toString());
            }
            if (newLevel != oldLevel) {
                Trace.on(newLevel);
            }
        }
    }

    private final InspectorAction setInspectorTraceLevelAction = new SetInspectorTraceLevelAction(null);

    /**
     * @return Singleton interactive Action that permits setting the level of inspector {@link Trace} output.
     */
    public final InspectorAction setInspectorTraceLevel() {
        return setInspectorTraceLevelAction;
    }

    /**
     * Action:  changes the threshold determining when the Inspectors uses its
     * {@linkplain InspectorInterpeter interpreter} for access to VM state.
     */
    final class ChangeInterpreterUseLevelAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Change Interpreter use level...";

        ChangeInterpreterUseLevelAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final int oldLevel = maxVM().getInterpreterUseLevel();
            int newLevel = oldLevel;
            final String input = gui().inputDialog("Change interpreter use level (0=none, 1=some, etc)", Integer.toString(oldLevel));
            if (input == null) {
                // User clicked cancel.
                return;
            }
            try {
                newLevel = Integer.parseInt(input);
            } catch (NumberFormatException numberFormatException) {
                gui().errorMessage(numberFormatException.toString());
            }
            if (newLevel != oldLevel) {
                maxVM().setInterpreterUseLevel(newLevel);
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess());
        }
    }

    private final InspectorAction changeInterpreterUseLevelAction = new ChangeInterpreterUseLevelAction(null);

    /**
     * @return Singleton interactive action that permits changing the level at which the interpreter
     * will be used when communicating with the VM.
     */
    public final InspectorAction changeInterpreterUseLevel() {
        return changeInterpreterUseLevelAction;
    }

    /**
     * Action:  sets debugging level for transport.
     * Appears unused October '08 (mlvdv)
     */
    final class SetTransportDebugLevelAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Set transport debug level...";

        SetTransportDebugLevelAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final int oldLevel = maxVM().transportDebugLevel();
            int newLevel = oldLevel;
            final String input = gui().inputDialog(" (Set transport debug level, 0=none, 1=some, etc)", Integer.toString(oldLevel));
            if (input == null) {
                // User clicked cancel.
                return;
            }
            try {
                newLevel = Integer.parseInt(input);
            } catch (NumberFormatException numberFormatException) {
                gui().errorMessage(numberFormatException.toString());
            }
            if (newLevel != oldLevel) {
                maxVM().setTransportDebugLevel(newLevel);
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess());
        }
    }

    private final InspectorAction setTransportDebugLevelAction = new SetTransportDebugLevelAction(null);

    /**
     * @return Singleton interactive action that permits setting the debugging level for transport.
     */
    public final InspectorAction setTransportDebugLevel() {
        return setTransportDebugLevelAction;
    }

    /**
     * Action: runs Inspector commands from a specified file.
     */
    final class RunFileCommandsAction extends InspectorAction {

        RunFileCommandsAction() {
            super(inspection(), "Execute commands from file...");
        }

        @Override
        protected void procedure() {
            final String fileName = gui().inputDialog("File name: ", FileCommands.defaultCommandFile());
            if (fileName != null && !fileName.equals("")) {
                maxVM().executeCommandsFromFile(fileName);
            }
        }
    }

    private final InspectorAction runFileCommandsAction = new RunFileCommandsAction();

    /**
     * @return Singleton interactive Action that will run Inspector commands from a specified file.
     */
    public final InspectorAction runFileCommands() {
        return runFileCommandsAction;
    }

    /**
     * Action:  updates the {@linkplain MaxVM#updateLoadableTypeDescriptorsFromClasspath() types available} on
     * the VM's class path by rescanning the complete class path for types.
     */
    final class UpdateClasspathTypesAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Rescan class path for types";

        UpdateClasspathTypesAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
        }

        @Override
        protected void procedure() {
            maxVM().updateLoadableTypeDescriptorsFromClasspath();
        }
    }

    private final InspectorAction updateClasspathTypesAction = new UpdateClasspathTypesAction(null);

    /**
     * @return Singleton Action that updates the {@linkplain MaxVM#updateLoadableTypeDescriptorsFromClasspath() types available} on
     * the VM's class path by rescanning the complete class path for types.
     */
    public final InspectorAction updateClasspathTypes() {
        return updateClasspathTypesAction;
    }

    /**
     * Action: sets the level of tracing in the VM interactively.
     */
    final class SetVMTraceLevelAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Set VM trace level";

        SetVMTraceLevelAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final int oldLevel = maxVM().getVMTraceLevel();
            int newLevel = oldLevel;
            final String input = gui().inputDialog("Set VM Trace Level", Integer.toString(oldLevel));
            if (input == null) {
                // User clicked cancel.
                return;
            }
            try {
                newLevel = Integer.parseInt(input);
            } catch (NumberFormatException numberFormatException) {
                gui().errorMessage(numberFormatException.toString());
            }
            if (newLevel != oldLevel) {
                maxVM().setVMTraceLevel(newLevel);
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess());
        }
    }

    private InspectorAction setVMTraceLevel = new SetVMTraceLevelAction(null);

    /**
     * @return an interactive Action that will set the level of tracing in the VM.
     */
    public final InspectorAction setVMTraceLevel() {
        return setVMTraceLevel;
    }

    /**
     * Action: sets the threshold of tracing in the VM interactively.
     */
    final class SetVMTraceThresholdAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Set VM trace threshold";

        SetVMTraceThresholdAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final long oldThreshold = maxVM().getVMTraceThreshold();
            long newThreshold = oldThreshold;
            final String input = gui().inputDialog("Set VM trace threshold", Long.toString(oldThreshold));
            if (input == null) {
                // User clicked cancel.
                return;
            }
            try {
                newThreshold = Long.parseLong(input);
            } catch (NumberFormatException numberFormatException) {
                gui().errorMessage(numberFormatException.toString());
            }
            if (newThreshold != oldThreshold) {
                maxVM().setVMTraceThreshold(newThreshold);
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess());
        }
    }

    private InspectorAction setVMTraceThreshold = new SetVMTraceThresholdAction(null);

    /**
     * @return an interactive Action that will set the threshold of tracing in the VM.
     */
    public final InspectorAction setVMTraceThreshold() {
        return setVMTraceThreshold;
    }

    /**
     * Action:  makes visible and highlights the {@link BootImageInspector}.
     */
    final class ViewBootImageAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Boot image info";

        ViewBootImageAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
        }

        @Override
        protected void procedure() {
            BootImageInspector.make(inspection()).highlight();
        }
    }

    private InspectorAction viewBootImage = new ViewBootImageAction(null);

    /**
     * @return an Action that will make visible the {@link BootImageInspector}.
     */
    public final InspectorAction viewBootImage() {
        return viewBootImage;
    }

    /**
     * Action:  makes visible and highlights the {@link BreakpointsInspector}.
     */
    final class ViewBreakpointsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Breakpoints";

        ViewBreakpointsAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            BreakpointsInspector.make(inspection()).highlight();
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess());
        }
    }

    private InspectorAction viewBreakpoints = new ViewBreakpointsAction(null);

    /**
     * @return an Action that will make visible the {@link BreakpointsInspector}.
     */
    public final InspectorAction viewBreakpoints() {
        return viewBreakpoints;
    }

    /**
     * Action:  makes visible and highlights the {@link MemoryRegionsInspector}.
     */
    final class ViewMemoryRegionsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Memory regions";

        ViewMemoryRegionsAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            MemoryRegionsInspector.make(inspection()).highlight();
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess());
        }
    }

    private InspectorAction viewMemoryRegions = new ViewMemoryRegionsAction(null);

    /**
     * @return an Action that will make visible the {@link MemoryRegionsInspector}.
     */
    public final InspectorAction viewMemoryRegions() {
        return viewMemoryRegions;
    }

    /**
     * Action:  makes visible the {@link MethodInspector}.
     */
    final class ViewMethodCodeAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Method code";

        ViewMethodCodeAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
        }

        @Override
        protected void procedure() {
            focus().setCodeLocation(focus().thread().instructionLocation());
        }
    }

    private InspectorAction viewMethodCode = new ViewMethodCodeAction(null);

    /**
     * @return an Action that will make visible the {@link MethodInspector}, with
     * initial view set to the method containing the instruction pointer of the current thread.
     */
    public final InspectorAction viewMethodCode() {
        return viewMethodCode;
    }

    /**
     * Action:  makes visible and highlights the {@link RegistersInspector}.
     */
    final class ViewRegistersAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Registers";

        ViewRegistersAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            RegistersInspector.make(inspection()).highlight();
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess() && focus().hasThread());
        }
    }

    private InspectorAction viewRegisters = new ViewRegistersAction(null);

    /**
     * @return an Action that will make visible the {@link RegistersInspector}.
     */
    public final InspectorAction viewRegisters() {
        return viewRegisters;
    }

    /**
     * Action:  makes visible and highlights the {@link StackInspector}.
     */
    final class ViewStackAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Stack";

        ViewStackAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            StackInspector.make(inspection()).highlight();
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess() && focus().hasThread());
        }
    }

    private InspectorAction viewStack = new ViewStackAction(null);

    /**
     * @return an Action that will make visible the {@link StackInspector}.
     */
    public final InspectorAction viewStack() {
        return viewStack;
    }

    /**
     * Action:  makes visible and highlights the {@link ThreadsInspector}.
     */
    final class ViewThreadsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Threads";

        ViewThreadsAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            ThreadsInspector.make(inspection()).highlight();
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess());
        }
    }

    private InspectorAction viewThreads = new ViewThreadsAction(null);

    /**
     * @return an Action that will make visible the {@link ThreadsInspector}.
     */
    public final InspectorAction viewThreads() {
        return viewThreads;
    }

    /**
     * Action:  makes visible and highlights the {@link ThreadLocalsInspector}.
     */
    final class ViewVmThreadLocalsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "VM thread locals";

        ViewVmThreadLocalsAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            ThreadLocalsInspector.make(inspection()).highlight();
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess() && focus().hasThread());
        }
    }

    private InspectorAction viewVmThreadLocals = new ViewVmThreadLocalsAction(null);

    /**
     * @return an Action that will make visible the {@link ThreadsInspector}.
     */
    public final InspectorAction viewVmThreadLocals() {
        return viewVmThreadLocals;
    }

    /**
     * Action:  makes visible and highlights the {@link WatchpointsInspector}.
     */
    final class ViewWatchpointsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Watchpoints";

        ViewWatchpointsAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            WatchpointsInspector.make(inspection()).highlight();
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(watchpointFactory() != null);
        }
    }

    private InspectorAction viewWatchpoints = new ViewWatchpointsAction(null);

    /**
     * @return an Action that will make visible the {@link WatchpointsInspector}.
     */
    public final InspectorAction viewWatchpoints() {
        return viewWatchpoints;
    }

    /**
     * Action:  copies a hex string version of a {@link Word} to the system clipboard.
     */
    final class CopyWordAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Copy word to clipboard";
        private final Word word;

        private CopyWordAction(Word word, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.word = word;
        }

        @Override
        public void procedure() {
            gui().postToClipboard(word.toHexString());
        }
    }

    /**
     * @param a {@link Word} from the VM.
     * @param actionTitle a string to use as the title of the action, uses default name if null.
     * @return an Action that copies the word's text value in hex to the system clipboard
     */
    public final InspectorAction copyWord(Word word, String actionTitle) {
        return new CopyWordAction(word, actionTitle);
    }

    /**
     * @param a {@link Word} wrapped as a {@link Value} from the VM.
     * @param actionTitle a string to use as the title of the action, uses default name if null.
     * @return an Action that copies the word's text value in hex to the system clipboard,
     * null if not a word.
     */
    public final InspectorAction copyValue(Value value, String actionTitle) {
        Word word = Word.zero();
        try {
            word = value.asWord();
        } catch (Throwable throwable) {
        }
        final InspectorAction action = new CopyWordAction(word, actionTitle);
        if (word.isZero()) {
            action.setEnabled(false);
        }
        return action;
    }

    /**
     * Action:  inspect a memory region, interactive if no location is specified.
     */
    final class InspectMemoryBytesAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Inspect memory as bytes";
        private final Address address;
        private final TeleObject teleObject;

        InspectMemoryBytesAction() {
            super(inspection(), "Inspect memory bytes at address...");
            this.address = null;
            this.teleObject = null;
            refreshableActions.append(this);
        }

        InspectMemoryBytesAction(Address address, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.address = address;
            this.teleObject = null;
        }

        InspectMemoryBytesAction(TeleObject teleObject, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.address = null;
            this.teleObject = teleObject;
        }

        @Override
        protected void procedure() {
            if (teleObject != null) {
                MemoryBytesInspector.create(inspection(), teleObject).highlight();
            } else if (address != null) {
                MemoryBytesInspector.create(inspection(), address).highlight();
            } else {
                new AddressInputDialog(inspection(), maxVM().bootImageStart(), "Inspect memory bytes at address...", "Inspect") {
                    @Override
                    public void entered(Address address) {
                        MemoryBytesInspector.create(inspection(), address).highlight();
                    }
                };
            }
        }
    }

    private final InspectorAction inspectMemoryBytesAction = new InspectMemoryBytesAction();

    /**
     * @return Singleton interactive Action that will create a Memory Inspector
     */
    public final InspectorAction inspectMemoryBytes() {
        return inspectMemoryBytesAction;
    }

    /**
     * @param address a valid memory {@link Address} in the VM
     * @param actionTitle a string name for the action, uses default name if null
     * @return an interactive Action that will create a Memory Inspector at the address
     */
    public final InspectorAction inspectMemoryBytes(Address address, String actionTitle) {
        return new InspectMemoryBytesAction(address, actionTitle);
    }

    /**
     * Menu: display a sub-menu of commands to make visible
     * existing memory words inspectors.  It includes a command
     * that closes all of them.
     */
    final class MemoryWordsInspectorsMenu extends JMenu {
        public MemoryWordsInspectorsMenu() {
            super("Memory inspectors");
            addMenuListener(new MenuListener() {

                public void menuCanceled(MenuEvent e) {
                }

                public void menuDeselected(MenuEvent e) {
                }

                public void menuSelected(MenuEvent e) {
                    removeAll();
                    final Set<MemoryWordsInspector> inspectors = inspection().memoryWordsInspectors();
                    if (inspectors.size() > 0) {
                        final Set<MemoryWordsInspector> sortedSet  =  new TreeSet<MemoryWordsInspector>(new Comparator<MemoryWordsInspector>() {
                            public int compare(MemoryWordsInspector inspector1, MemoryWordsInspector inspector2) {
                                final Long startLocation1 = inspector1.getCurrentMemoryRegion().start().toLong();
                                final Long startLocation2 = inspector2.getCurrentMemoryRegion().start().toLong();
                                return startLocation1.compareTo(startLocation2);
                            }
                        });
                        for (MemoryWordsInspector inspector : inspectors) {
                            sortedSet.add(inspector);
                        }
                        for (MemoryWordsInspector inspector : sortedSet) {
                            add(actions().showView(inspector));
                        }
                        addSeparator();
                        add(actions().closeViews(MemoryWordsInspector.class, null, "Close all memory inspectors"));
                    }
                }
            });
        }
    }

    /**
     * Creates a menu of actions to make visible existing memory words inspectors.
     * <br>
     * <strong>Note:</strong> This menu does not depend on context, so it would be natural to use
     * a singleton to be shared among all uses.  Unfortunately, that does not seem to work.
     *
     * @return a dynamically populated menu that contains an action to make visible each
     * existing memory words inspector, even if hidden or iconic.
     */
    public final JMenu memoryWordsInspectorsMenu() {
        return new MemoryWordsInspectorsMenu();
    }

    /**
     * Action:   inspect a memory region, interactive if no location specified.
     */
    final class InspectMemoryWordsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Inspect memory";
        private final Address address;
        private final MemoryRegion memoryRegion;

        InspectMemoryWordsAction() {
            super(inspection(), "Inspect memory at address...");
            this.address = null;
            this.memoryRegion = null;
        }

        InspectMemoryWordsAction(MemoryRegion memoryRegion, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.address = null;
            this.memoryRegion = memoryRegion;
        }

        InspectMemoryWordsAction(Address address, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.address = address;
            this.memoryRegion = null;
        }

        @Override
        protected void procedure() {
            if (memoryRegion != null) {
                final Inspector inspector = new MemoryWordsInspector(inspection(), memoryRegion, memoryRegion.description());
                inspector.highlight();
            } else  if (address != null) {
                final Inspector inspector = new MemoryWordsInspector(inspection(), new FixedMemoryRegion(address, maxVM().wordSize().times(10), ""));
                inspector.highlight();
            } else {
                new AddressInputDialog(inspection(), maxVM().bootImageStart(), "Inspect memory at address...", "Inspect") {

                    @Override
                    public void entered(Address address) {
                        final Inspector inspector = new MemoryWordsInspector(inspection(), new FixedMemoryRegion(address, maxVM().wordSize().times(10), ""));
                        inspector.highlight();
                    }
                };
            }
        }
    }

    private final InspectorAction inspectMemoryWordsAction = new InspectMemoryWordsAction();

    /**
     * @return Singleton interactive Action that will create a MemoryWords Inspector
     */
    public final InspectorAction inspectMemoryWords() {
        return inspectMemoryWordsAction;
    }

    public final InspectorAction inspectMemoryWords(MemoryRegion memoryRegion) {
        return new InspectMemoryWordsAction(memoryRegion, null);
    }

    /**
     * @param address a valid memory {@link Address} in the VM
     * @param actionTitle a name for the action
     * @return an Action that will create a Memory Words Inspector at the address
     */
    public final InspectorAction inspectMemoryWords(Address address, String actionTitle) {
        return new InspectMemoryWordsAction(address, actionTitle);
    }

    /**
     * @param address a valid memory {@link Address} in the VM
     * @return an Action that will create a Memory Words Inspector at the address
     */
    public final InspectorAction inspectMemoryWords(Address address) {
        return new InspectMemoryWordsAction(address, null);
    }

    /**
     * Menu: display a sub-menu of commands to inspect the basic allocation
     * regions of the VM.
     */
    final class InspectMemoryRegionsMenu extends JMenu {
        public InspectMemoryRegionsMenu() {
            super("Inspect memory region");
            addMenuListener(new MenuListener() {

                public void menuCanceled(MenuEvent e) {
                }

                public void menuDeselected(MenuEvent e) {
                }

                public void menuSelected(MenuEvent e) {
                    removeAll();
                    for (MemoryRegion memoryRegion : maxVM().allocatedMemoryRegions()) {
                        //System.out.println(memoryRegion.toString());
                        add(actions().inspectRegionMemoryWords(memoryRegion, memoryRegion.description(), memoryRegion.description()));
                    }
                }
            });
        }
    }

    /**
     * Creates a menu of actions to inspect memory regions.
     * <br>
     * <strong>Note:</strong> This menu does not depend on context, so it would be natural to use
     * a singleton to be shared among all uses.  Unfortunately, that does not seem to work.
     *
     * @return a dynamically populated menu that contains an action to inspect each currently allocated
     * region of memory in the VM.
     */
    public final JMenu inspectMemoryRegionsMenu() {
        return new InspectMemoryRegionsMenu();
    }

    /**
     * Action: inspects memory occupied by an object.
     */

    final class InspectObjectMemoryWordsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Inspect memory";
        private final TeleObject teleObject;

        InspectObjectMemoryWordsAction(TeleObject teleObject, String actionTitle) {
            super(inspection(), (actionTitle == null) ? DEFAULT_TITLE : actionTitle);
            this.teleObject = teleObject;
        }

        @Override
        protected void procedure() {
            final Inspector inspector = new MemoryWordsInspector(inspection(), teleObject);
            inspector.highlight();
        }
    }

    /**
     * @param teleObject a surrogate for a valid object in the VM
     * @param actionTitle a name for the action
     * @return an Action that will create a Memory Words Inspector at the address
     */
    public final InspectorAction inspectObjectMemoryWords(TeleObject teleObject, String actionTitle) {
        return new InspectObjectMemoryWordsAction(teleObject, actionTitle);
    }

    /**
     * @param teleObject a surrogate for a valid object in the VM
     * @return an Action that will create a Memory Words Inspector at the address
     */
    public final InspectorAction inspectObjectMemoryWords(TeleObject teleObject) {
        return new InspectObjectMemoryWordsAction(teleObject, null);
    }

    /**
     * Action:  inspect the memory holding a block of target code.
     */
    final class InspectTargetRegionMemoryWordsAction extends InspectorAction {

        private static final String  DEFAULT_TITLE = "Inspect Target Code memory region";
        private final TeleTargetRoutine teleTargetRoutine;

        private InspectTargetRegionMemoryWordsAction(TeleTargetRoutine teleTargetRoutine, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.teleTargetRoutine = teleTargetRoutine;
        }

        @Override
        protected void procedure() {
            final String description;
            if (teleTargetRoutine instanceof TeleTargetMethod) {
                final TeleTargetMethod teleTargetMethod = (TeleTargetMethod) teleTargetRoutine;
                description = "Target Method " + inspection().nameDisplay().shortName(teleTargetMethod);
            } else {
                description = "Native Target Method: " + teleTargetRoutine.getName();
            }
            actions().inspectRegionMemoryWords(teleTargetRoutine.targetCodeRegion(), description).perform();
        }
    }

    /**
     * Creates an action that will inspect memory containing a block of target code.
     *
     * @param teleTargetRoutine a block of target code in the VM, either a Java method or native
     * @param actionTitle a name for the action
     * @return an Action that will create a Memory Words Inspector for the code
     */
    public final InspectorAction inspectTargetRegionMemoryWords(TeleTargetRoutine teleTargetRoutine, String actionTitle) {
        return new InspectTargetRegionMemoryWordsAction(teleTargetRoutine, actionTitle);
    }

    /**
     * Creates an action that will inspect memory containing a block of target code.
     *
     * @param teleTargetRoutine a block of target code in the VM, either a Java method or native
     * @return an Action that will create a Memory Words Inspector for the code
     */
    public final InspectorAction inspectTargetRegionMemoryWords(TeleTargetRoutine teleTargetRoutine) {
        return new InspectTargetRegionMemoryWordsAction(teleTargetRoutine, null);
    }

    /**
     *Action:  inspect the memory allocated to the currently selected thread.
     */
    final class InspectSelectedThreadMemoryWordsAction extends InspectorAction {

        public InspectSelectedThreadMemoryWordsAction(String actionTitle) {
            super(inspection(), actionTitle == null ? "Inspect memory for selected thread" : actionTitle);
        }

        @Override
        protected void procedure() {
            final MaxThread thread = focus().thread();
            if (thread != null) {
                final Inspector inspector = new MemoryWordsInspector(inspection(), thread.stack().memoryRegion(), "Thread " + thread.toShortString());
                inspector.highlight();
            } else {
                gui().errorMessage("no thread selected");
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasThread());
        }
    }

    /**
     * @param actionTitle title for the action, uses a default if null
     * @return an action that will create a memory words inspector
     * for memory allocated by the currently selected thread
     */
    public final InspectorAction inspectSelectedThreadMemoryWords(String actionTitle) {
        return new InspectSelectedThreadMemoryWordsAction(actionTitle);
    }

    /**
     *Action:  inspect the memory allocated to the currently selected memory watchpoint.
     */
    final class InspectSelectedMemoryWatchpointWordsAction extends InspectorAction {

        public InspectSelectedMemoryWatchpointWordsAction(String actionTitle) {
            super(inspection(), actionTitle == null ? "Inspect memory at selected watchpoint" : actionTitle);
        }

        @Override
        protected void procedure() {
            final MaxWatchpoint watchpoint = focus().watchpoint();
            if (watchpoint != null) {
                final Inspector inspector = new MemoryWordsInspector(inspection(), watchpoint, "Watchpoint " + watchpoint.description());
                inspector.highlight();
            } else {
                gui().errorMessage("no watchpoint selected");
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasWatchpoint());
        }
    }

    private final InspectorAction inspectSelectedMemoryWatchpointWordsAction = new InspectSelectedMemoryWatchpointWordsAction(null);

    /**
     * @return Singleton action that will create a memory words inspector
     * for memory allocated by the currently selected thread
     */
    public final InspectorAction inspectSelectedMemoryWatchpointWordsAction() {
        return inspectSelectedMemoryWatchpointWordsAction;
    }

    /**
     * Action: inspect a named region as memory words.
     */
    final class InspectRegionMemoryWordsAction extends InspectorAction {

        private final MemoryRegion memoryRegion;
        private final String regionName;

        InspectRegionMemoryWordsAction(MemoryRegion memoryRegion, String regionName, String actionTitle) {
            super(inspection(), actionTitle == null ? ("Inspect memory region \"" + regionName + "\"") : actionTitle);
            this.memoryRegion = memoryRegion;
            this.regionName = regionName;
            refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final Inspector inspector = new MemoryWordsInspector(inspection(), memoryRegion, regionName);
            inspector.highlight();
        }
    }

    /**
     * @return an Action that will create a Memory Words Inspector for the boot heap region.
     */
    public final InspectorAction inspectBootHeapMemoryWords() {
        return new InspectRegionMemoryWordsAction(maxVM().teleBootHeapRegion(), "Heap-Boot", null);
    }

    /**
     * @return an Action that will create a Memory Words Inspector for the immortal heap region.
     */
    public final InspectorAction inspectImmortalHeapMemoryWords() {
        return new InspectRegionMemoryWordsAction(maxVM().teleImmortalHeapRegion(), "Heap-Immortal", null);
    }

    /**
     * @return an Action that will create a Memory Words Inspector for the boot code region.
     */
    public final InspectorAction inspectBootCodeMemoryWords() {
        return new InspectRegionMemoryWordsAction(maxVM().teleBootCodeRegion(), "Heap-Code", null);
    }

    /**
     * Creates a MemoryWords Inspector for a named region of memory.
     *
     * @param memoryRegion a region of memory in the VM
     * @param regionName the name of the region to display
     * @param actionTitle the name of the action that will create the display, default title if null
     * @return an action that will create a Memory Words Inspector for the region
     */
    public final InspectorAction inspectRegionMemoryWords(MemoryRegion memoryRegion, String regionName, String actionTitle) {
        final String title = (actionTitle == null) ? ("Inspect memory region \"" + regionName + "\"") : actionTitle;
        return new InspectRegionMemoryWordsAction(memoryRegion, regionName, title);
    }

    /**
     * Creates a MemoryWords Inspector for a named region of memory.
     *
     * @param memoryRegion a region of memory in the VM
     * @param regionName the name of the region to display
     * @return an action that will create a Memory Words Inspector for the region
     */
    public final InspectorAction inspectRegionMemoryWords(MemoryRegion memoryRegion, String regionName) {
        return new InspectRegionMemoryWordsAction(memoryRegion, regionName, null);
    }

    /**
     * Action:  creates a memory inspector for the currently selected memory region, if any.
     */

    final class InspectSelectedMemoryRegionWordsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Inspect selected memory region";

        InspectSelectedMemoryRegionWordsAction() {
            super(inspection(), DEFAULT_TITLE);
            refreshableActions.append(this);
            refresh(true);
        }

        @Override
        protected void procedure() {
            final MemoryRegion memoryRegion = focus().memoryRegion();
            if (memoryRegion != null) {
                final Inspector inspector = new MemoryWordsInspector(inspection(), memoryRegion, memoryRegion.description());
                inspector.highlight();
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasMemoryRegion());
        }
    }

    private final InspectorAction inspectSelectedMemoryRegionWordsAction = new InspectSelectedMemoryRegionWordsAction();

    /**
     * @return Singleton Action that will create a Memory inspector for the currently selected region of memory
     */
    public final InspectorAction inspectSelectedMemoryRegionWords() {
        return inspectSelectedMemoryRegionWordsAction;
    }

    /**
     * Action: sets inspection focus to specified {@link MemoryRegion}.
     */
    final class SelectMemoryRegionAction extends InspectorAction {

        private final MemoryRegion memoryRegion;
        private static final String DEFAULT_TITLE = "Select memory region";

        SelectMemoryRegionAction(MemoryRegion memoryRegion, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.memoryRegion = memoryRegion;
        }

        @Override
        protected void procedure() {
            focus().setMemoryRegion(memoryRegion);
        }
    }

    /**
     * @return an Action that will create a Memory Inspector at the start of the boot code
     */
    public final InspectorAction selectMemoryRegion(MemoryRegion memoryRegion) {
        final String actionTitle = "Select memory region \"" + memoryRegion.description() + "\"";
        return new SelectMemoryRegionAction(memoryRegion, actionTitle);
    }

    /**
     * Action: create an Object Inspector, interactively specified by address..
     */
    final class InspectObjectAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Inspect object at address...";

        InspectObjectAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
        }

        @Override
        protected void procedure() {
            new AddressInputDialog(inspection(), maxVM().teleBootHeapRegion().start(), "Inspect object at address...", "Inspect") {

                @Override
                public void entered(Address address) {
                    final Pointer pointer = address.asPointer();
                    if (maxVM().isValidOrigin(pointer)) {
                        final Reference objectReference = maxVM().originToReference(pointer);
                        final TeleObject teleObject = maxVM().makeTeleObject(objectReference);
                        focus().setHeapObject(teleObject);
                    } else {
                        gui().errorMessage("heap object not found at 0x"  + address.toHexString());
                    }
                }
            };
        }
    }

    /**
     * Menu: display a sub-menu of commands to make visible
     * existing object inspectors.  It includes a command
     * that closes all of them.
     */
    final class ObjectInspectorsMenu extends JMenu {
        public ObjectInspectorsMenu() {
            super("Object inspectors");
            addMenuListener(new MenuListener() {

                public void menuCanceled(MenuEvent e) {
                }

                public void menuDeselected(MenuEvent e) {
                }

                public void menuSelected(MenuEvent e) {
                    removeAll();
                    final Set<ObjectInspector> inspectors = inspection().objectInspectors();
                    if (inspectors.size() > 0) {
                        for (ObjectInspector objectInspector : inspection().objectInspectors()) {
                            add(actions().showView(objectInspector));
                        }
                        addSeparator();
                        add(actions().closeViews(ObjectInspector.class, null, "Close all object inspectors"));
                    }
                }
            });
        }
    }

    /**
     * Creates a menu of actions to make visible existing object inspectors.
     * <br>
     * <strong>Note:</strong> This menu does not depend on context, so it would be natural to use
     * a singleton to be shared among all uses.  Unfortunately, that does not seem to work.
     *
     * @return a dynamically populated menu that contains an action to make visible each
     * existing object inspector, even if hidden or iconic.
     */
    public final JMenu objectInspectorsMenu() {
        return new ObjectInspectorsMenu();
    }

    private final InspectorAction inspectObjectAction = new InspectObjectAction(null);

    /**
     * @return Singleton Action that will create an Object Inspector interactively, prompting the user for a numeric object ID
     */
    public final InspectorAction inspectObject() {
        return inspectObjectAction;
    }

    /**
     * Action:  creates an inspector for a specific heap object in the VM.
     */
    final class InspectSpecifiedObjectAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Inspect object";
        final TeleObject teleObject;

        InspectSpecifiedObjectAction(TeleObject teleObject, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.teleObject = teleObject;
        }

        @Override
        protected void procedure() {
            focus().setHeapObject(teleObject);
        }
    }

    /**
     * @param surrogate for a heap object in the VM.
     * @param actionTitle a string name for the Action, uses default name if null
     * @return an Action that will create an Object Inspector
     */
    public final InspectorAction inspectObject(TeleObject teleObject, String actionTitle) {
        return new InspectSpecifiedObjectAction(teleObject, actionTitle);
    }

    /**
     * Action: create an Object Inspector, interactively specified by the inspector's OID.
     */
    final class InspectObjectByIDAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Inspect object by ID...";

        InspectObjectByIDAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
        }

        @Override
        protected void procedure() {
            final String input = gui().inputDialog("Inspect object by ID..", "");
            if (input == null) {
                // User clicked cancel.
                return;
            }
            try {
                final long oid = Long.parseLong(input);
                final TeleObject teleObject = maxVM().findObjectByOID(oid);
                if (teleObject != null) {
                    focus().setHeapObject(teleObject);
                } else {
                    gui().errorMessage("failed to find heap object for ID: " + input);
                }
            } catch (NumberFormatException numberFormatException) {
                gui().errorMessage("Not a ID: " + input);
            }
        }
    }

    private final InspectorAction inspectObjectByIDAction = new InspectObjectByIDAction(null);

    /**
     * @return Singleton Action that will create an Object Inspector interactively, prompting the user for a numeric object ID
     */
    public final InspectorAction inspectObjectByID() {
        return inspectObjectByIDAction;
    }

    /**
     * Action: create an Object Inspector for the boot {@link ClassRegistry} in the VM.
     */
    final class InspectBootClassRegistryAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Inspect boot class registry";

        InspectBootClassRegistryAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
        }

        @Override
        protected void procedure() {
            final TeleObject teleBootClassRegistry = maxVM().makeTeleObject(maxVM().bootClassRegistryReference());
            focus().setHeapObject(teleBootClassRegistry);
        }
    }

    private final InspectorAction inspectBootClassRegistryAction = new InspectBootClassRegistryAction(null);

    /**
     * @return Singleton action that will create an Object Inspector for the boot {@link ClassRegistry} in the VM.
     */
    public final InspectorAction inspectBootClassRegistry() {
        return inspectBootClassRegistryAction;
    }

    /**
     * Action:  inspect a {@link ClassActor} object for an interactively named class loaded in the VM,
     * specified by class name.
     */
    final class InspectClassActorByNameAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Inspect ClassActor by name...";

        InspectClassActorByNameAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
        }

        @Override
        protected void procedure() {
            final TeleClassActor teleClassActor = ClassActorSearchDialog.show(inspection(), "Inspect ClassActor ...", "Inspect");
            if (teleClassActor != null) {
                focus().setHeapObject(teleClassActor);
            }
        }
    }

    private final InspectorAction inspectClassActorByNameAction = new InspectClassActorByNameAction(null);

    /**
     * @return Singleton interactive Action that inspects a {@link ClassActor} object for a class loaded in the VM,
     * specified by class name.
     */
    public final InspectorAction inspectClassActorByName() {
        return inspectClassActorByNameAction;
    }

    /**
     * Action:  inspect a {@link ClassActor} for an interactively named class loaded in the VM,
     * specified by the class ID in hex.
     */
    final class InspectClassActorByHexIdAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Inspect ClassActor by ID (Hex) ...";

        InspectClassActorByHexIdAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
        }

        @Override
        protected void procedure() {
            final String value = gui().questionMessage("ID (hex): ");
            if (value != null && !value.equals("")) {
                try {
                    final int serial = Integer.parseInt(value, 16);
                    final TeleClassActor teleClassActor = maxVM().findTeleClassActor(serial);
                    if (teleClassActor == null) {
                        gui().errorMessage("failed to find classActor for ID:  0x" + Integer.toHexString(serial));
                    } else {
                        focus().setHeapObject(teleClassActor);
                    }
                } catch (NumberFormatException ex) {
                    gui().errorMessage("Hex integer required");
                }
            }
        }
    }

    private final InspectorAction inspectClassActorByHexIdAction = new InspectClassActorByHexIdAction(null);

    /**
     * @return Singleton interactive Action that inspects a {@link ClassActor} object for a class loaded in the VM,
     * specified by class ID in hex.
     */
    public final InspectorAction inspectClassActorByHexId() {
        return inspectClassActorByHexIdAction;
    }

    /**
     * Action:  inspect a {@link ClassActor} for an interactively named class loaded in the VM,
     * specified by the class ID in decimal.
     */
    final class InspectClassActorByDecimalIdAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Inspect ClassActor by ID (decimal) ...";
        InspectClassActorByDecimalIdAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
        }

        @Override
        protected void procedure() {
            final String value = gui().questionMessage("ID (decimal): ");
            if (value != null && !value.equals("")) {
                try {
                    final int serial = Integer.parseInt(value, 10);
                    final TeleClassActor teleClassActor = maxVM().findTeleClassActor(serial);
                    if (teleClassActor == null) {
                        gui().errorMessage("failed to find ClassActor for ID: " + serial);
                    } else {
                        focus().setHeapObject(teleClassActor);
                    }
                } catch (NumberFormatException ex) {
                    gui().errorMessage("Hex integer required");
                }
            }
        }
    }

    private final InspectorAction inspectClassActorByDecimalIdAction = new InspectClassActorByDecimalIdAction(null);

    /**
     * @return Singleton interactive Action that inspects a {@link ClassActor} object for a class loaded in the VM,
     * specified by class ID in decimal.
     */
    public final InspectorAction inspectClassActorByDecimalId() {
        return inspectClassActorByDecimalIdAction;
    }

    /**
     * Action: visits a {@link MethodActor} object in the VM, specified by name.
     */
    final class InspectMethodActorByNameAction extends InspectorAction {

        private static final String DEFAULT_TITLE =  "Inspect MethodActor by name...";

        InspectMethodActorByNameAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
        }

        @Override
        protected void procedure() {
            final TeleClassActor teleClassActor = ClassActorSearchDialog.show(inspection(), "Inspect MethodActor in class...", "Select");
            if (teleClassActor != null) {
                final TeleMethodActor teleMethodActor = MethodActorSearchDialog.show(inspection(), teleClassActor, "Inspect MethodActor...", "Inspect");
                if (teleMethodActor != null) {
                    focus().setHeapObject(teleMethodActor);
                }
            }
        }

    }

    private InspectorAction inspectMethodActorByName = new InspectMethodActorByNameAction(null);

    /**
     * @return an interactive Action that will visit a {@link MethodActor} object in the VM, specified by name.
     */
    public final InspectorAction inspectMethodActorByName() {
        return inspectMethodActorByName;
    }

    /**
     * Action:  inspects the class actor from which a method was substituted.
     */
    final class InspectSubstitutionSourceClassActorAction extends InspectorAction {

        private static final String DEFAULT_TITLE =  "Method substitution source";

        private final TeleClassMethodActor teleClassMethodActor;

        private InspectSubstitutionSourceClassActorAction(TeleClassMethodActor teleClassMethodActor, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.teleClassMethodActor = teleClassMethodActor;
            setEnabled(teleClassMethodActor.isSubstituted());
        }

        @Override
        public void procedure() {
            if (teleClassMethodActor != null) {
                focus().setHeapObject(teleClassMethodActor.teleClassActorSubstitutedFrom());
            }
        }
    }

    /**
     * Creates an action to inspect the class actor from which a method was substituted.
     *
     * @param teleClassMethodActor representation of a class method in the VM
     * @param actionTitle name of the action
     * @return an action that will inspect the class actor, if any, from which the method was substituted
     */
    public final InspectorAction inspectSubstitutionSourceClassActorAction(TeleClassMethodActor teleClassMethodActor, String actionTitle) {
        return new InspectSubstitutionSourceClassActorAction(teleClassMethodActor, actionTitle);
    }

    /**
     * Creates an action to inspect the class actor from which a method was substituted.
     *
     * @param teleClassMethodActor representation of a class method in the VM
     * @return an action that will inspect the class actor, if any, from which the method was substituted
     */
    public final InspectorAction inspectSubstitutionSourceClassActorAction(TeleClassMethodActor teleClassMethodActor) {
        return new InspectSubstitutionSourceClassActorAction(teleClassMethodActor, null);
    }

    /**
     * Menu: contains actions to inspect each of the compilations of a target method.
     */
    final class InspectTargetMethodCompilationsMenu extends InspectorMenu {

        private static final String DEFAULT_TITLE = "Compilations";
        private final TeleClassMethodActor teleClassMethodActor;

        public InspectTargetMethodCompilationsMenu(TeleClassMethodActor teleClassMethodactor, String actionTitle) {
            super(actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.teleClassMethodActor = teleClassMethodactor;
            refresh(true);
        }

        @Override
        public void refresh(boolean force) {
            if (getMenuComponentCount() < teleClassMethodActor.numberOfCompilations()) {
                for (int index = getMenuComponentCount(); index < teleClassMethodActor.numberOfCompilations(); index++) {
                    final TeleTargetMethod teleTargetMethod = teleClassMethodActor.getJavaTargetMethod(index);
                    final StringBuilder name = new StringBuilder();
                    name.append(inspection().nameDisplay().methodCompilationID(teleTargetMethod));
                    name.append("  ");
                    name.append(teleTargetMethod.classActorForType().simpleName());
                    add(actions().inspectObject(teleTargetMethod, name.toString()));
                }
            }
        }
    }

    /**
     * Creates a menu containing actions to inspect all compilations of a method, dynamically updated
     * as compilations are added.
     *
     * @param teleClassMethodActor representation of a Java method in the VM
     * @param actionTitle name of the action to appear on button or menu
     * @return a dynamically refreshed menu that contains actions to inspect each of the compilations of a method.
     */
    public InspectorMenu inspectTargetMethodCompilationsMenu(TeleClassMethodActor teleClassMethodActor, String actionTitle) {
        return new InspectTargetMethodCompilationsMenu(teleClassMethodActor, actionTitle);
    }

    /**
     * Creates a menu containing actions to inspect all compilations of a method, dynamically updated
     * as compilations are added.
     *
     * @param teleClassMethodActor representation of a Java method in the VM
     * @return a dynamically refreshed menu that contains actions to inspect each of the compilations of a method.
     */
    public InspectorMenu inspectTargetMethodCompilationsMenu(TeleClassMethodActor teleClassMethodActor) {
        return new InspectTargetMethodCompilationsMenu(teleClassMethodActor, null);
    }

    /**
     * Action:  displays Java source for a specified method.
     */
    final class ViewJavaSourceAction extends InspectorAction {

        private final TeleClassMethodActor teleClassMethodActor;

        public ViewJavaSourceAction(TeleClassMethodActor teleClassMethodActor) {
            super(inspection(), "View Java Source (external)");
            this.teleClassMethodActor = teleClassMethodActor;
        }

        @Override
        public void procedure() {
            inspection().viewSourceExternally(new BytecodeLocation(teleClassMethodActor.classMethodActor(), 0));
        }
    }

    /**
     * Creates an action that will produce an external view of method source code.
     *
     * @param teleClassMethodActor surrogate of a Java method in the VM.
     * @return an action that creates an external of the Java source for the method.
     */
    public InspectorAction viewJavaSource(TeleClassMethodActor teleClassMethodActor) {
        return new ViewJavaSourceAction(teleClassMethodActor);
    }

    /**
     * Action:  displays in the {@MethodInspector} the method whose target code contains
     * an interactively specified address.
     */
    final class ViewMethodCodeByAddressAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Target method by address...";

        public ViewMethodCodeByAddressAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
        }

        @Override
        protected void procedure() {
            new AddressInputDialog(inspection(), maxVM().bootImageStart(), "View method code containing target code address...", "View Code") {

                @Override
                public String validateInput(Address address) {
                    if (maxVM().makeTeleTargetMethod(address) != null) {
                        return null;
                    }
                    return "There is no method containing the address " + address.toHexString();
                }

                @Override
                public void entered(Address address) {
                    focus().setCodeLocation(codeManager().createMachineCodeLocation(address, "user specified address"));
                }
            };
        }
    }

    private final InspectorAction viewMethodCodeByAddressAction = new ViewMethodCodeByAddressAction(null);

    /**
     * @return Singleton interactive action that displays in the {@link MethodInspector} the method whose
     * target code contains the specified address in the VM.
     */
    public final InspectorAction viewMethodCodeByAddress() {
        return viewMethodCodeByAddressAction;
    }

    /**
     * @param actionTitle name of the action to appear in menu or button, uses default if null
     * @return an interactive action that displays in the {@link MethodInspector} the method whose
     * target code contains the specified address in the VM.
     */
    public final InspectorAction viewMethodCodeByAddress(String actionTitle) {
        return new ViewMethodCodeByAddressAction(actionTitle);
    }

    /**
     * Action:  displays in the {@MethodInspector} the method code containing an address.
     */
    final class ViewMethodCodeAtLocationAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "View code at a location";
        private final MaxCodeLocation codeLocation;

        public ViewMethodCodeAtLocationAction(MaxCodeLocation codeLocation, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            assert codeLocation != null;
            this.codeLocation = codeLocation;
            refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            focus().setCodeLocation(codeLocation, true);
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasThread());
        }
    }

    /**
     * @return an Action that displays in the {@link MethodInspector} a method at some code location.
     */
    public final InspectorAction viewMethodCodeAtLocation(MaxCodeLocation codeLocation, String actionTitle) {
        return new ViewMethodCodeAtLocationAction(codeLocation, actionTitle);
    }

    /**
     * Action:  displays in the {@MethodInspector} the method code containing the current code selection.
     */
    final class ViewMethodCodeAtSelectionAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "View code at current selection";
        public ViewMethodCodeAtSelectionAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            focus().setCodeLocation(focus().codeLocation(), true);
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasCodeLocation());
        }
    }

    private final ViewMethodCodeAtSelectionAction viewMethodCodeAtSelection = new ViewMethodCodeAtSelectionAction(null);

    /**
     * @return Singleton action that displays in the {@link MethodInspector} the method code
     * containing the current code selection.
     */
    public final InspectorAction viewMethodCodeAtSelection() {
        return viewMethodCodeAtSelection;
    }

    /**
     * Action:  displays in the {@MethodInspector} the method code containing the current instruction pointer.
     */
    final class ViewMethodCodeAtIPAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "View code at current IP";
        public ViewMethodCodeAtIPAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            focus().setCodeLocation(focus().thread().instructionLocation());
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasThread());
        }
    }

    private final ViewMethodCodeAtIPAction viewMethodCodeAtIP = new ViewMethodCodeAtIPAction(null);

    /**
     * @return Singleton Action that displays in the {@link MethodInspector} the method
     * containing the current instruction pointer.
     */
    public final InspectorAction viewMethodCodeAtIP() {
        return viewMethodCodeAtIP;
    }

    /**
     * Action:  displays in the {@MethodInspector} the bytecode for a specified method.
     */
    final class ViewMethodBytecodeAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "View bytecode...";
        private final TeleClassMethodActor teleClassMethodActor;

        public ViewMethodBytecodeAction(TeleClassMethodActor teleClassMethodActor, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.teleClassMethodActor = teleClassMethodActor;
            setEnabled(teleClassMethodActor.hasCodeAttribute());
        }

        @Override
        protected void procedure() {
            final MaxCodeLocation teleCodeLocation = codeManager().createBytecodeLocation(teleClassMethodActor, 0, "view method bytecode action");
            focus().setCodeLocation(teleCodeLocation);
        }
    }

    /**
     * @return an interactive Action that displays bytecode in the {@link MethodInspector}
     * for a selected method.
     */
    public final InspectorAction viewMethodBytecode(TeleClassMethodActor teleClassMethodActor) {
        return new ViewMethodBytecodeAction(teleClassMethodActor, null);
    }

    /**
     * Creates an action to view the bytecodes (if they exist) for a Java method.
     *
     * @param teleClassMethodActor
     * @param actionTitle name of the action to appear in menu or button
     * @return an interactive Action that displays bytecode in the {@link MethodInspector}
     * for a selected method.
     */
    public final InspectorAction viewMethodBytecode(TeleClassMethodActor teleClassMethodActor, String actionTitle) {
        return new ViewMethodBytecodeAction(teleClassMethodActor, actionTitle);
    }

    /**
     * Action:  displays in the {@MethodInspector} the bytecode for an interactively specified method.
     */
    final class ViewMethodBytecodeByNameAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "View bytecode...";

        public ViewMethodBytecodeByNameAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
        }

        @Override
        protected void procedure() {
            final TeleClassActor teleClassActor = ClassActorSearchDialog.show(inspection(), "View bytecode for method in class...", "Select");
            if (teleClassActor != null) {
                final Predicate<TeleMethodActor> hasBytecodePredicate = new Predicate<TeleMethodActor>() {
                    public boolean evaluate(TeleMethodActor teleMethodActor) {
                        return teleMethodActor.hasCodeAttribute();
                    }
                };
                final TeleMethodActor teleMethodActor = MethodActorSearchDialog.show(inspection(), teleClassActor, hasBytecodePredicate, "View Bytecodes for Method...", "Inspect");
                if (teleMethodActor != null && teleMethodActor instanceof TeleClassMethodActor) {
                    final TeleClassMethodActor teleClassMethodActor = (TeleClassMethodActor) teleMethodActor;
                    final MaxCodeLocation teleCodeLocation = codeManager().createBytecodeLocation(teleClassMethodActor, 0, "view method by name bytecode action");
                    focus().setCodeLocation(teleCodeLocation);
                }
            }
        }
    }

    private final InspectorAction viewMethodBytecodeByNameAction = new  ViewMethodBytecodeByNameAction(null);

    /**
     * @return an interactive Action that displays bytecode in the {@link MethodInspector}
     * for a selected method.
     */
    public final InspectorAction viewMethodBytecodeByName() {
        return viewMethodBytecodeByNameAction;
    }

    /**
     * @param actionTitle name of the action to appear in menu or button
     * @return an interactive Action that displays bytecode in the {@link MethodInspector}
     * for a selected method.
     */
    public final InspectorAction viewMethodBytecodeByName(String actionTitle) {
        return new ViewMethodBytecodeByNameAction(actionTitle);
    }

    /**
     * Action:  displays in the {@MethodInspector} the target code for an interactively specified method.
     */
    final class ViewMethodTargetCodeByNameAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "View target code...";

        public ViewMethodTargetCodeByNameAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
        }

        @Override
        protected void procedure() {
            final TeleClassActor teleClassActor = ClassActorSearchDialog.show(inspection(), "View target code for method in class...", "Select");
            if (teleClassActor != null) {
                final Sequence<TeleTargetMethod> teleTargetMethods = TargetMethodSearchDialog.show(inspection(), teleClassActor, "View Target Code for Method...", "View Code", false);
                if (teleTargetMethods != null) {
                    focus().setCodeLocation(teleTargetMethods.first().callEntryLocation());
                }
            }
        }
    }

    private final InspectorAction viewMethodTargetCodeByNameAction = new ViewMethodTargetCodeByNameAction(null);

    /**
     * @return Singleton interactive Action that displays target code in the {@link MethodInspector}
     * for a selected method.
     */
    public final InspectorAction viewMethodTargetCodeByName() {
        return viewMethodTargetCodeByNameAction;
    }

    /**
     * Action:  displays in the {@MethodInspector} the target code for an interactively specified method.
     */
// TODO (mlvdv) review
    final class ViewMethodTargetCodeAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "View target code...";

        public ViewMethodTargetCodeAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
        }

        @Override
        protected void procedure() {
            final Sequence<TeleTargetMethod> teleTargetMethods = TargetMethodSearchDialog.show(inspection(), null, "View Target Code for Method...", "View Code", false);
            if (teleTargetMethods != null) {
                focus().setCodeLocation(codeManager().createMachineCodeLocation(teleTargetMethods.first().callEntryPoint(), "target code for method"), false);
            }
        }
    }

    private final InspectorAction viewMethodTargetCodeAction = new ViewMethodTargetCodeAction(null);

    /**
     * @return Singleton interactive Action that displays target code in the {@link MethodInspector}
     * for a selected method.
     */
    public final InspectorAction viewMethodTargetCode() {
        return viewMethodTargetCodeAction;
    }

    /**
     * Menu: contains actions to view code for each of the compilations of a target method.
     */
    final class ViewTargetMethodCodeMenu extends InspectorMenu {

        private static final String DEFAULT_TITLE = "View compilations";
        private final TeleClassMethodActor teleClassMethodActor;

        public ViewTargetMethodCodeMenu(TeleClassMethodActor teleClassMethodactor, String actionTitle) {
            super(actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.teleClassMethodActor = teleClassMethodactor;
            refresh(true);
        }

        @Override
        public void refresh(boolean force) {
            if (getMenuComponentCount() < teleClassMethodActor.numberOfCompilations()) {
                for (int index = getMenuComponentCount(); index < teleClassMethodActor.numberOfCompilations(); index++) {
                    final TeleTargetMethod teleTargetMethod = teleClassMethodActor.getJavaTargetMethod(index);
                    final StringBuilder name = new StringBuilder();
                    name.append(inspection().nameDisplay().methodCompilationID(teleTargetMethod));
                    name.append("  ");
                    name.append(teleTargetMethod.classActorForType().simpleName());
                    add(actions().viewMethodCodeAtLocation(teleTargetMethod.callEntryLocation(), name.toString()));
                }
            }
        }
    }

    /**
     * Creates a menu containing actions to inspect the target code for all compilations of a method, dynamically updated
     * as compilations are added.
     *
     * @param teleClassMethodActor representation of a Java method in the VM
     * @param actionTitle name of the action to appear on button or menu
     * @return a dynamically refreshed menu that contains actions to view code for each of the compilations of a method.
     */
    public InspectorMenu viewTargetMethodCodeMenu(TeleClassMethodActor teleClassMethodActor, String actionTitle) {
        return new ViewTargetMethodCodeMenu(teleClassMethodActor, actionTitle);
    }

    /**
     * Creates a menu containing actions to inspect the target code for all compilations of a method, dynamically updated
     * as compilations are added.
     *
     * @param teleClassMethodActor representation of a Java method in the VM
     * @return a dynamically refreshed menu that contains actions to view code for each of the compilations of a method.
     */
    public InspectorMenu viewTargetMethodCodeMenu(TeleClassMethodActor teleClassMethodActor) {
        return new ViewTargetMethodCodeMenu(teleClassMethodActor, null);
    }

    /**
     * Action:  displays in the {@link MethodInspector} the code of a specified
     * method in the boot image.
     */
    final class ViewMethodCodeInBootImageAction extends InspectorAction {

        private final int offset;

        public ViewMethodCodeInBootImageAction(int offset, Class clazz, String name, Class... parameterTypes) {
            super(inspection(), clazz.getName() + "." + name + SignatureDescriptor.fromJava(Void.TYPE, parameterTypes).toJavaString(false, false));
            this.offset = offset;
        }

        public ViewMethodCodeInBootImageAction(int offset, Method method) {
            this(offset, method.getDeclaringClass(), method.getName(), method.getParameterTypes());
        }

        @Override
        protected void procedure() {
            focus().setCodeLocation(codeManager().createMachineCodeLocation(maxVM().bootImageStart().plus(offset), "address from boot image"), true);
        }
    }

    private final InspectorAction viewRunMethodCodeInBootImageAction =
        new ViewMethodCodeInBootImageAction(maxVM().bootImage().header.vmRunMethodOffset, ClassRegistry.MaxineVM_run.toJava());

    /**
     * @return an Action that displays in the {@link MethodInspector} the code of
     * the {@link MaxineVM#run()} method in the boot image.
     */
    public final InspectorAction viewRunMethodCodeInBootImage() {
        return viewRunMethodCodeInBootImageAction;
    }

    private final InspectorAction viewThreadRunMethodCodeInBootImageAction =
        new ViewMethodCodeInBootImageAction(maxVM().bootImage().header.vmThreadRunMethodOffset, ClassRegistry.VmThread_run.toJava());

    /**
     * @return an Action that displays in the {@link MethodInspector} the code of
     * the {@link VmThread#run()} method in the boot image.
     */
    public final InspectorAction viewThreadRunMethodCodeInBootImage() {
        return viewThreadRunMethodCodeInBootImageAction;
    }

    /**
     * Action:  displays in the {@MethodInspector} a body of native code whose location contains
     * an interactively specified address.
     */
    final class ViewNativeCodeByAddressAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Native method by address...";

        public ViewNativeCodeByAddressAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
        }

        @Override
        protected void procedure() {
            // Most likely situation is that we are just about to call a native method in which case RAX is the address
            final MaxThread thread = focus().thread();
            assert thread != null;
            final Address indirectCallAddress = thread.integerRegisters().getCallRegisterValue();
            final Address initialAddress = indirectCallAddress == null ? maxVM().bootImageStart() : indirectCallAddress;
            new AddressInputDialog(inspection(), initialAddress, "View native code containing code address...", "View Code") {
                @Override
                public void entered(Address address) {
                    focus().setCodeLocation(codeManager().createMachineCodeLocation(address, "native code address specified by user"), true);
                }
            };
        }
    }

    private final InspectorAction viewNativeCodeByAddressAction = new ViewNativeCodeByAddressAction(null);

    /**
      * @return Singleton interactive action that displays in the {@link MethodInspector} a body of native code whose
     * location contains the specified address in the VM.
     */
    public final InspectorAction viewNativeCodeByAddress() {
        return viewNativeCodeByAddressAction;
    }

    /**
     * @param actionTitle name of the action, as it will appear on a menu or button, default name if null
     * @return an interactive action that displays in the {@link MethodInspector} a body of native code whose
     * location contains the specified address in the VM.
     */
    public final InspectorAction viewNativeCodeByAddress(String actionTitle) {
        return new ViewNativeCodeByAddressAction(actionTitle);
    }

    /**
     * Action:  copies to the system clipboard a textual representation of the
     * disassembled target code for a compiled method.
     */
    final class CopyTargetMethodCodeToClipboardAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Copy disassembled target code to clipboard";

        private final TeleTargetMethod teleTargetMethod;

        /**
         * @param teleTargetRoutine
         */
        private CopyTargetMethodCodeToClipboardAction(TeleTargetMethod teleTargetMethod, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.teleTargetMethod = teleTargetMethod;
        }

        @Override
        public void procedure() {
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final IndentWriter writer = new IndentWriter(new OutputStreamWriter(byteArrayOutputStream));
            writer.println("target method: " + teleTargetMethod.classMethodActor().format("%H.%n(%p)"));
            writer.println("compilation: " + inspection().nameDisplay().methodCompilationID(teleTargetMethod) + "  " + teleTargetMethod.classActorForType().simpleName());
            teleTargetMethod.disassemble(writer);
            writer.flush();
            final ProcessorKind processorKind = maxVM().vmConfiguration().platform().processorKind;
            final InlineDataDecoder inlineDataDecoder = InlineDataDecoder.createFrom(teleTargetMethod.targetMethod().encodedInlineDataDescriptors());
            final Pointer startAddress = teleTargetMethod.getCodeStart();
            final DisassemblyPrinter disassemblyPrinter = new DisassemblyPrinter(false) {
                @Override
                protected String disassembledObjectString(Disassembler disassembler, DisassembledObject disassembledObject) {
                    final String string = super.disassembledObjectString(disassembler, disassembledObject);
                    if (string.startsWith("call ")) {
                        final BytecodeLocation bytecodeLocation = null; //_teleTargetMethod.getBytecodeLocationFor(startAddress.plus(disassembledObject.startPosition()));
                        if (bytecodeLocation != null) {
                            final MethodRefConstant methodRef = bytecodeLocation.getCalleeMethodRef();
                            if (methodRef != null) {
                                final ConstantPool pool = bytecodeLocation.classMethodActor.codeAttribute().constantPool;
                                return string + " [" + methodRef.holder(pool).toJavaString(false) + "." + methodRef.name(pool) + methodRef.signature(pool).toJavaString(false, false) + "]";
                            }
                        }
                    }
                    return string;
                }
            };
            Disassemble.disassemble(byteArrayOutputStream, teleTargetMethod.getCode(), processorKind, startAddress, inlineDataDecoder, disassemblyPrinter);
            gui().postToClipboard(byteArrayOutputStream.toString());
        }
    }

    /**
     * @return an Action that copies to the system clipboard a textual disassembly of a method's target code.
     */
    public InspectorAction copyTargetMethodCodeToClipboard(TeleTargetMethod teleTargetMethod, String actionTitle) {
        return new CopyTargetMethodCodeToClipboardAction(teleTargetMethod, actionTitle);
    }


    /**
     * Menu: display a sub-menu of commands to make visible
     * existing object inspectors.  It includes a command
     * that closes all of them.
     */
    final class BuiltinBreakpointsMenu extends InspectorMenu {
        public BuiltinBreakpointsMenu(String title) {
            super(title == null ? "Break at" : title);
            addMenuListener(new MenuListener() {

                public void menuCanceled(MenuEvent e) {
                }

                public void menuDeselected(MenuEvent e) {
                }

                public void menuSelected(MenuEvent e) {
                    removeAll();
                    for (MaxCodeLocation codeLocation : maxVM().inspectableMethods()) {
                        add(actions().setBreakpoint(codeLocation));
                    }
                }
            });
        }
    }

   /**
     * Action:  removes the currently selected breakpoint from the VM.
     */
    final class RemoveSelectedBreakpointAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Remove selected breakpoint";

        RemoveSelectedBreakpointAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            focus().addListener(new InspectionFocusAdapter() {
                @Override
                public void breakpointFocusSet(MaxBreakpoint oldBreakpoint, MaxBreakpoint breakpoint) {
                    refresh(false);
                }
            });
            refresh(false);
        }

        @Override
        protected void procedure() {
            final MaxBreakpoint selectedBreakpoint = focus().breakpoint();
            if (selectedBreakpoint != null) {
                try {
                    selectedBreakpoint.remove();
                    focus().setBreakpoint(null);
                }  catch (MaxVMBusyException maxVMBusyException) {
                    inspection().announceVMBusyFailure(name());
                }
            } else {
                gui().errorMessage("No breakpoint selected");
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasBreakpoint());
        }
    }

    private InspectorAction removeBreakpoint = new RemoveSelectedBreakpointAction(null);

    /**
     * @return an Action that will remove the currently selected breakpoint, if any.
     */
    public final InspectorAction removeSelectedBreakpoint() {
        return removeBreakpoint;
    }

    /**
     * Action: removes a specific  breakpoint in the VM.
     */
    final class RemoveBreakpointAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Remove breakpoint";

        final MaxBreakpoint breakpoint;

        RemoveBreakpointAction(MaxBreakpoint breakpoint, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.breakpoint = breakpoint;
        }

        @Override
        protected void procedure() {
            if (focus().breakpoint() == breakpoint) {
                focus().setBreakpoint(null);
            }
            try {
                breakpoint.remove();
            } catch (MaxVMBusyException maxVMBusyException) {
                inspection().announceVMBusyFailure(name());
            }
        }
    }

    /**
     * @param surrogate for a breakpoint in the VM.
     * @param actionTitle a string name for the Action, uses default name if null.
     * @return an Action that will remove the breakpoint
     */
    public final InspectorAction removeBreakpoint(MaxBreakpoint breakpoint, String actionTitle) {
        return new RemoveBreakpointAction(breakpoint, actionTitle);
    }

    /**
     * Action: removes all existing breakpoints in the VM.
     */
    final class RemoveAllBreakpointsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Remove all breakpoints";

        RemoveAllBreakpointsAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
            inspection().addInspectionListener(new InspectionListenerAdapter() {
                @Override
                public void breakpointStateChanged() {
                    refresh(true);
                }
            });
        }

        @Override
        protected void procedure() {
            focus().setBreakpoint(null);
            try {
                for (MaxBreakpoint breakpoint : breakpointFactory().breakpoints()) {
                    breakpoint.remove();
                }
            } catch (MaxVMBusyException maxVMBusyException) {
                inspection().announceVMBusyFailure(name());
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess() && (breakpointFactory().breakpoints().length() > 0));
        }
    }

    private InspectorAction removeAllBreakpoints = new RemoveAllBreakpointsAction(null);

    /**
     * @return an Action that will remove all breakpoints in the VM.
     */
    public final InspectorAction removeAllBreakpoints() {
        return removeAllBreakpoints;
    }

    /**
     * Action: enables a specific  breakpoint in the VM.
     */
    final class EnableBreakpointAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Enable breakpoint";
        final MaxBreakpoint breakpoint;

        EnableBreakpointAction(MaxBreakpoint breakpoint, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.breakpoint = breakpoint;
        }

        @Override
        protected void procedure() {
            if (focus().breakpoint() == breakpoint) {
                focus().setBreakpoint(null);
            }
            try {
                breakpoint.setEnabled(true);
            } catch (MaxVMBusyException maxVMBusyException) {
                inspection().announceVMBusyFailure(name());
            }
            inspection().refreshAll(false);
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess() && !breakpoint.isEnabled());
        }
    }

    /**
     * @param breakpoint surrogate for a breakpoint in the VM.
     * @param actionTitle a string name for the Action, uses default name if null.
     * @return an Action that will enable the breakpoint
     */
    public final InspectorAction enableBreakpoint(MaxBreakpoint breakpoint, String actionTitle) {
        return new EnableBreakpointAction(breakpoint, actionTitle);
    }

    /**
     * Action: disables a specific  breakpoint in the VM.
     */
    final class DisableBreakpointAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Disable breakpoint";

        final MaxBreakpoint breakpoint;

        DisableBreakpointAction(MaxBreakpoint breakpoint, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.breakpoint = breakpoint;
        }

        @Override
        protected void procedure() {
            if (focus().breakpoint() == breakpoint) {
                focus().setBreakpoint(null);
            }
            try {
                breakpoint.setEnabled(false);
            } catch (MaxVMBusyException maxVMBusyException) {
                inspection().announceVMBusyFailure(name());
            }
            inspection().refreshAll(false);
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess() && breakpoint.isEnabled());
        }
    }

    /**
     * @param breakpoint surrogate for a breakpoint in the VM.
     * @param actionTitle a string name for the Action, uses default name if null.
     * @return an Action that will disable the breakpoint
     */
    public final InspectorAction disableBreakpoint(MaxBreakpoint breakpoint, String actionTitle) {
        return new DisableBreakpointAction(breakpoint, actionTitle);
    }

    /**
     * Action:  set a breakpoint.
     */
    final class SetBreakpointAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Set breakpoint";

        private final MaxCodeLocation codeLocation;

        public SetBreakpointAction(MaxCodeLocation codeLocation, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            assert codeLocation != null;
            this.codeLocation = codeLocation;
            refresh(true);
        }

        @Override
        protected void procedure() {
            try {
                final MaxBreakpoint breakpoint = breakpointFactory().makeBreakpoint(codeLocation);
                focus().setBreakpoint(breakpoint);
            } catch (MaxVMBusyException maxVMBusyException) {
                inspection().announceVMBusyFailure(name());
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(breakpointFactory().findBreakpoint(codeLocation) == null);
        }
    }

    public final InspectorAction setBreakpoint(MaxCodeLocation codeLocation, String actionTitle) {
        return new SetBreakpointAction(codeLocation, actionTitle);
    }


    public final InspectorAction setBreakpoint(MaxCodeLocation codeLocation) {
        return new SetBreakpointAction(codeLocation, codeLocation.description());
    }

    /**
     * Action:  set a target code breakpoint at a particular address.
     */
    final class SetTargetCodeBreakpointAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Set target code breakpoint";

        private final MaxCodeLocation codeLocation;

        public SetTargetCodeBreakpointAction(MaxCodeLocation codeLocation, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            assert codeLocation != null && codeLocation.hasAddress();
            this.codeLocation = codeLocation;
            refresh(true);
        }

        @Override
        protected void procedure() {
            try {
                final MaxBreakpoint breakpoint = breakpointFactory().makeBreakpoint(codeLocation);
                focus().setBreakpoint(breakpoint);
            } catch (MaxVMBusyException maxVMBusyException) {
                inspection().announceVMBusyFailure(name());
            }

        }

        @Override
        public void refresh(boolean force) {
            setEnabled(breakpointFactory().findBreakpoint(codeLocation) == null);
        }
    }

    public final InspectorAction setTargetCodeBreakpoint(MaxCodeLocation codeLocation, String actionTitle) {
        return new SetTargetCodeBreakpointAction(codeLocation, actionTitle);
    }

    /**
     * Action:  remove a target code breakpoint at a particular address.
     */
    final class RemoveTargetCodeBreakpointAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Remove target code breakpoint";

        private final MaxCodeLocation codeLocation;

        public RemoveTargetCodeBreakpointAction(MaxCodeLocation codeLocation, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            assert codeLocation != null && codeLocation.hasAddress();
            this.codeLocation = codeLocation;
            refresh(true);
        }

        @Override
        protected void procedure() {
            final MaxBreakpoint breakpoint = breakpointFactory().findBreakpoint(codeLocation);
            if (breakpoint != null) {
                try {
                    breakpoint.remove();
                } catch (MaxVMBusyException maxVMBusyException) {
                    inspection().announceVMBusyFailure(name());
                }
                focus().setBreakpoint(null);
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(breakpointFactory().findBreakpoint(codeLocation) != null);
        }
    }

    public final InspectorAction removeTargetCodeBreakpoint(MaxCodeLocation codeLocation, String actionTitle) {
        return new RemoveTargetCodeBreakpointAction(codeLocation, actionTitle);
    }

     /**
     * Action:  toggle on/off a breakpoint at the target code location specified, or
     * if not initialized, then to the target code location of the current focus.
     */
    final class ToggleTargetCodeBreakpointAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Toggle target code breakpoint";

        private final MaxCodeLocation codeLocation;

        ToggleTargetCodeBreakpointAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.codeLocation = null;
            refreshableActions.append(this);
            focus().addListener(new InspectionFocusAdapter() {
                @Override
                public void codeLocationFocusSet(MaxCodeLocation codeLocation, boolean interactiveForNative) {
                    refresh(false);
                }
            });
            refresh(true);
        }

        ToggleTargetCodeBreakpointAction(MaxCodeLocation codeLocation, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            assert codeLocation != null && codeLocation.hasAddress();
            this.codeLocation = codeLocation;
        }

        @Override
        protected void procedure() {
            final MaxCodeLocation toggleCodeLocation = (codeLocation != null) ? codeLocation : focus().codeLocation();
            if (toggleCodeLocation.hasAddress() && !toggleCodeLocation.address().isZero()) {
                MaxBreakpoint breakpoint = breakpointFactory().findBreakpoint(toggleCodeLocation);
                try {
                    if (breakpoint == null) {
                        try {
                            breakpoint = breakpointFactory().makeBreakpoint(toggleCodeLocation);
                            focus().setBreakpoint(breakpoint);
                        } catch (MaxVMBusyException maxVMBusyException) {
                            inspection().announceVMBusyFailure(name());
                        }
                    } else {
                        breakpoint.remove();
                        focus().setBreakpoint(null);
                    }
                } catch (MaxVMBusyException maxVMBusyException) {
                    inspection().announceVMBusyFailure(name());
                }
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess()  && focus().hasCodeLocation() && focus().codeLocation().hasAddress());
        }
    }

    private InspectorAction toggleTargetCodeBreakpoint = new ToggleTargetCodeBreakpointAction(null);

    /**
     * @return an Action that will toggle on/off a breakpoint at the target code location of the current focus.
     */
    public final InspectorAction toggleTargetCodeBreakpoint() {
        return toggleTargetCodeBreakpoint;
    }

    /**
     * @param actionTitle string that identifies the action
     * @return an Action that will toggle on/off a breakpoint at the target code location of the current focus.
     */
    public final InspectorAction toggleTargetCodeBreakpoint(String actionTitle) {
        return new ToggleTargetCodeBreakpointAction(actionTitle);
    }

    /**
     * @param codeLocation code location
     * @param actionTitle string that identifies the action
     * @return an Action that will toggle on/off a breakpoint at the specified target code location.
     */
    public final InspectorAction toggleTargetCodeBreakpoint(MaxCodeLocation codeLocation, String actionTitle) {
        return new ToggleTargetCodeBreakpointAction(codeLocation, actionTitle);
    }

    /**
     * Action:  sets a  breakpoint at the target code location specified interactively..
     */
    final class SetTargetCodeBreakpointAtAddressAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "At address...";

        SetTargetCodeBreakpointAtAddressAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
            refresh(true);
        }

        @Override
        protected void procedure() {
            new NativeLocationInputDialog(inspection(), "Break at target code address...", maxVM().bootImageStart(), "") {
                @Override
                public void entered(Address address, String description) {
                    if (!address.isZero()) {
                        try {
                            final MaxBreakpoint breakpoint = breakpointFactory().makeBreakpoint(codeManager().createMachineCodeLocation(address, "set target breakpoint"));
                            if (breakpoint == null) {
                                gui().errorMessage("Unable to create breakpoint at: " + "0x" + address.toHexString());
                            } else {
                                breakpoint.setDescription(description);
                            }
                        } catch (MaxVMBusyException maxVMBusyException) {
                            inspection().announceVMBusyFailure(name());
                        }
                    }
                }
            };
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess());
        }
    }

    private InspectorAction setTargetCodeBreakpointAtAddressAction = new SetTargetCodeBreakpointAtAddressAction(null);

    /**
     * @return an Action that will toggle on/off a breakpoint at the target code location of the current focus.
     */
    public final InspectorAction setTargetCodeBreakpointAtAddress() {
        return setTargetCodeBreakpointAtAddressAction;
    }

    /**
     * Action:  sets a breakpoint at every label in a target method.
     */
    final class SetTargetCodeLabelBreakpointsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Set breakpoint at every target code label";
        private final Sequence<MaxCodeLocation> locations;

        SetTargetCodeLabelBreakpointsAction(TeleTargetRoutine teleTargetRoutine, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.locations = teleTargetRoutine.labelLocations();
            setEnabled(inspection().hasProcess() && locations.length() > 0);
        }

        @Override
        protected void procedure() {
            try {
                for (MaxCodeLocation location : locations) {
                    breakpointFactory().makeBreakpoint(location);
                }
            } catch (MaxVMBusyException maxVMBusyException) {
                inspection().announceVMBusyFailure(name());
            }
        }

    }

    /**
     * @return an Action that will set a breakpoint at every label in a target routine
     */
    public final InspectorAction setTargetCodeLabelBreakpoints(TeleTargetRoutine teleTargetRoutine, String actionTitle) {
        return new SetTargetCodeLabelBreakpointsAction(teleTargetRoutine, actionTitle);
    }

    /**
     * Action:  removes any breakpoints at labels in a target method.
     */
    final class RemoveTargetCodeLabelBreakpointsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Remove breakpoint at every target code label";
        private final Sequence<MaxCodeLocation> labelLocations;

        RemoveTargetCodeLabelBreakpointsAction(TeleTargetRoutine teleTargetRoutine, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            labelLocations = teleTargetRoutine.labelLocations();
            setEnabled(inspection().hasProcess() && labelLocations.length() > 0);
        }

        @Override
        protected void procedure() {
            try {
                for (MaxCodeLocation location : labelLocations) {
                    final MaxBreakpoint breakpoint = breakpointFactory().findBreakpoint(location);
                    if (breakpoint != null) {
                        breakpoint.remove();
                    }
                }
            } catch (MaxVMBusyException maxVMBusyException) {
                inspection().announceVMBusyFailure(name());
            }
        }
    }

    /**
     * @return an Action that will remove any breakpoints labels in a target method.
     */
    public final InspectorAction removeTargetCodeLabelBreakpoints(TeleTargetRoutine teleTargetRoutine, String actionTitle) {
        return new RemoveTargetCodeLabelBreakpointsAction(teleTargetRoutine, actionTitle);
    }

     /**
     * Action:  sets target code breakpoints at  a specified method entry.
     */
    final class SetTargetCodeBreakpointAtMethodEntryAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Set target code breakpoint at method entry";
        private final TeleTargetMethod teleTargetMethod;
        SetTargetCodeBreakpointAtMethodEntryAction(TeleTargetMethod teleTargetMethod, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.teleTargetMethod = teleTargetMethod;
            refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final MaxCodeLocation entryLocation = teleTargetMethod.entryLocation();
            try {
                MaxBreakpoint breakpoint = breakpointFactory().makeBreakpoint(entryLocation);
                focus().setBreakpoint(breakpoint);
            } catch (MaxVMBusyException maxVMBusyException) {
                inspection().announceVMBusyFailure(name());
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess());
        }
    }

    /**
     * @return an interactive Action that sets a target code breakpoint at  a method entry
     */
    public final InspectorAction setTargetCodeBreakpointAtMethodEntry(TeleTargetMethod teleTargetMethod, String actionTitle) {
        return new  SetTargetCodeBreakpointAtMethodEntryAction(teleTargetMethod, actionTitle);
    }

    /**
     * @return an interactive Action that sets a target code breakpoint at  a method entry
     */
    public final InspectorAction setTargetCodeBreakpointAtMethodEntry(TeleTargetMethod teleTargetMethod) {
        return new  SetTargetCodeBreakpointAtMethodEntryAction(teleTargetMethod, null);
    }

    /**
     * Action:  sets target code breakpoints at  method entries to be selected interactively by name.
     */
    final class SetTargetCodeBreakpointAtMethodEntriesByNameAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Compiled methods...";

        SetTargetCodeBreakpointAtMethodEntriesByNameAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final TeleClassActor teleClassActor = ClassActorSearchDialog.show(inspection(), "Class for compiled method entry breakpoints...", "Select");
            if (teleClassActor != null) {
                final Sequence<TeleTargetMethod> teleTargetMethods = TargetMethodSearchDialog.show(inspection(), teleClassActor, "Compiled Method Entry Breakpoints", "Set Breakpoints", true);
                if (teleTargetMethods != null) {
                    try {
                        // There may be multiple compilations of a method in the result.
                        MaxBreakpoint targetBreakpoint = null;
                        for (TeleTargetMethod teleTargetMethod : teleTargetMethods) {
                            targetBreakpoint = breakpointFactory().makeBreakpoint(teleTargetMethod.getTeleClassMethodActor().entryLocation());
                        }
                        focus().setBreakpoint(targetBreakpoint);
                    } catch (MaxVMBusyException maxVMBusyException) {
                        inspection().announceVMBusyFailure(name());
                    }
                }
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess());
        }
    }

    private final SetTargetCodeBreakpointAtMethodEntriesByNameAction setTargetCodeBreakpointAtMethodEntriesByNameAction =
        new SetTargetCodeBreakpointAtMethodEntriesByNameAction(null);

    /**
     * @return Singleton interactive Action that sets a target code breakpoint at  a method entry to be selected by name.
     */
    public final InspectorAction setTargetCodeBreakpointAtMethodEntriesByName() {
        return setTargetCodeBreakpointAtMethodEntriesByNameAction;
    }

    /**
     * Action: sets target code breakpoint at object initializers of a class specified interactively by name.
     */
    final class SetTargetCodeBreakpointAtObjectInitializerAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Object initializers of class...";

        SetTargetCodeBreakpointAtObjectInitializerAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final TeleClassActor teleClassActor = ClassActorSearchDialog.show(inspection(), "Add breakpoint in Object Initializers of Class...", "Set Breakpoint");
            if (teleClassActor != null) {
                final ClassActor classActor = teleClassActor.classActor();
                if (classActor.localVirtualMethodActors() != null) {
                    try {
                        MaxBreakpoint breakpoint = null;
                        for (VirtualMethodActor virtualMethodActor : classActor.localVirtualMethodActors()) {
                            if (virtualMethodActor.name == SymbolTable.INIT) {
                                final TeleClassMethodActor teleClassMethodActor = maxVM().findTeleMethodActor(TeleClassMethodActor.class, virtualMethodActor);
                                if (teleClassMethodActor != null) {
                                    for (TeleTargetMethod teleTargetMethod : teleClassMethodActor.targetMethods()) {
                                        final MaxCodeLocation entryLocation = teleTargetMethod.entryLocation();
                                        breakpoint = breakpointFactory().makeBreakpoint(entryLocation);
                                    }
                                }
                            }
                        }
                        if (breakpoint != null) {
                            focus().setBreakpoint(breakpoint);
                        }
                    } catch (MaxVMBusyException maxVMBusyException) {
                        inspection().announceVMBusyFailure(name());
                    }
                }
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess());
        }
    }

    private InspectorAction setTargetCodeBreakpointAtObjectInitializer =
        new SetTargetCodeBreakpointAtObjectInitializerAction(null);

    /**
     * @return an interactive Action that will set a target code breakpoint at the
     * object initializer for a class specified by name.
     */
    public final InspectorAction setTargetCodeBreakpointAtObjectInitializer() {
        return setTargetCodeBreakpointAtObjectInitializer;
    }

    /**
     * Action:  toggle on/off a breakpoint at the  bytecode location of the current focus.
     */
    class ToggleBytecodeBreakpointAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Toggle bytecode breakpoint";

        ToggleBytecodeBreakpointAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
            focus().addListener(new InspectionFocusAdapter() {
                @Override
                public void codeLocationFocusSet(MaxCodeLocation codeLocation, boolean interactiveForNative) {
                    refresh(false);
                }
            });
        }

        @Override
        protected void procedure() {
            final MaxCodeLocation codeLocation = focus().codeLocation();
            if (codeLocation.hasMethodKey()) {
                final MaxBreakpoint breakpoint = breakpointFactory().findBreakpoint(codeLocation);
                try {
                    if (breakpoint == null) {
                        breakpointFactory().makeBreakpoint(codeLocation);
                    } else {
                        breakpoint.remove();
                    }
                } catch (MaxVMBusyException maxVMBusyException) {
                    inspection().announceVMBusyFailure(name());
                }
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess()  && focus().hasCodeLocation() && focus().codeLocation().hasTeleClassMethodActor());
        }
    }

    private InspectorAction toggleBytecodeBreakpoint = new ToggleBytecodeBreakpointAction(null);

    /**
     * @return an Action that will toggle on/off a breakpoint at the bytecode location of the current focus.
     */
    public final InspectorAction toggleBytecodeBreakpoint() {
        return toggleBytecodeBreakpoint;
    }

    /**
     * Action: sets a bytecode breakpoint at a specified method entry.
     */
    final class SetBytecodeBreakpointAtMethodEntryAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Method on classpath";
        private final TeleClassMethodActor teleClassMethodActor;

        SetBytecodeBreakpointAtMethodEntryAction(TeleClassMethodActor teleClassMethodActor, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.teleClassMethodActor = teleClassMethodActor;
            refreshableActions.append(this);
            refresh(true);
        }

        @Override
        protected void procedure() {
            final MaxCodeLocation location = codeManager().createBytecodeLocation(teleClassMethodActor, -1, "teleClassMethodActor entry");
            try {
                breakpointFactory().makeBreakpoint(location);
            } catch (MaxVMBusyException maxVMBusyException) {
                inspection().announceVMBusyFailure(name());
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess() && teleClassMethodActor.hasCodeAttribute());
        }
    }

    /**
     * @return an Action  that will set a target code breakpoint at  a method entry.
     */
    public final InspectorAction setBytecodeBreakpointAtMethodEntry(TeleClassMethodActor teleClassMethodActor, String actionTitle) {
        return new SetBytecodeBreakpointAtMethodEntryAction(teleClassMethodActor, actionTitle);
    }

    /**
     * @return an Action  that will set a target code breakpoint at  a method entry.
     */
    public final InspectorAction setBytecodeBreakpointAtMethodEntry(TeleClassMethodActor teleClassMethodActor) {
        return new SetBytecodeBreakpointAtMethodEntryAction(teleClassMethodActor, null);
    }

     /**
     * Action: sets a bytecode breakpoint at a method entry specified interactively by name.
     */
    final class SetBytecodeBreakpointAtMethodEntryByNameAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Method on classpath, by name...";

        SetBytecodeBreakpointAtMethodEntryByNameAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final TypeDescriptor typeDescriptor = TypeSearchDialog.show(inspection(), "Class for bytecode method entry breakpoint...", "Select");
            if (typeDescriptor != null) {
                final MethodKey methodKey = MethodSearchDialog.show(inspection(), typeDescriptor, "Bytecodes method entry breakpoint", "Set Breakpoint");
                if (methodKey != null) {
                    try {
                        breakpointFactory().makeBreakpoint(codeManager().createBytecodeLocation(methodKey, "set bytecode breakpoint"));
                    } catch (MaxVMBusyException maxVMBusyException) {
                        inspection().announceVMBusyFailure(name());
                    }
                }
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess());
        }
    }

    private final SetBytecodeBreakpointAtMethodEntryByNameAction setBytecodeBreakpointAtMethodEntryByNameAction =
        new SetBytecodeBreakpointAtMethodEntryByNameAction(null);

    /**
     * @return Singleton interactive Action  that will set a target code breakpoint at  a method entry to be selected by name.
     */
    public final InspectorAction setBytecodeBreakpointAtMethodEntryByName() {
        return setBytecodeBreakpointAtMethodEntryByNameAction;
    }

    /**
     * Action: sets a bytecode breakpoint at a method entry specified interactively by name.
     */
    final class SetBytecodeBreakpointAtMethodEntryByKeyAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Method matched by key...";

        SetBytecodeBreakpointAtMethodEntryByKeyAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final MethodKey methodKey = MethodKeyInputDialog.show(inspection(), "Specify method");
            if (methodKey != null) {
                try {
                    breakpointFactory().makeBreakpoint(codeManager().createBytecodeLocation(methodKey, "set bytecode breakpoint"));
                } catch (MaxVMBusyException maxVMBusyException) {
                    inspection().announceVMBusyFailure(name());
                }
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess());
        }
    }

    private final SetBytecodeBreakpointAtMethodEntryByKeyAction setBytecodeBreakpointAtMethodEntryByKeyAction =
        new SetBytecodeBreakpointAtMethodEntryByKeyAction(null);

    /**
     * @return Singleton interactive Action  that will set a target code breakpoint at  a method entry to be selected by name.
     */
    public final InspectorAction setBytecodeBreakpointAtMethodEntryByKey() {
        return setBytecodeBreakpointAtMethodEntryByKeyAction;
    }

   /**
     * Action: create a memory word watchpoint, interactive if no location specified.
     */
    final class SetWordWatchpointAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Watch memory word";
        private final MemoryRegion memoryRegion;

        SetWordWatchpointAction() {
            super(inspection(), "Watch memory word at address...");
            this.memoryRegion = null;
            setEnabled(true);
        }

        SetWordWatchpointAction(Address address, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.memoryRegion = new MemoryWordRegion(address, 1, maxVM().wordSize());
            setEnabled(watchpointFactory().findWatchpoints(memoryRegion).isEmpty());
        }

        @Override
        protected void procedure() {
            if (memoryRegion != null) {
                setWatchpoint(memoryRegion, "");
            } else {
                new MemoryRegionInputDialog(inspection(), maxVM().bootImageStart(), "Watch memory starting at address...", "Watch") {
                    @Override
                    public void entered(Address address, Size size) {
                        setWatchpoint(new MemoryWordRegion(address, size.toInt() / Word.size(), Size.fromInt(Word.size())), "User specified region");
                    }
                };
            }
        }

        private void setWatchpoint(MemoryRegion memoryRegion, String description) {
            final WatchpointsViewPreferences prefs = WatchpointsViewPreferences.globalPreferences(inspection());
            try {
                final MaxWatchpoint watchpoint = watchpointFactory().createRegionWatchpoint(description, memoryRegion, prefs.settings());
                if (watchpoint == null) {
                    gui().errorMessage("Watchpoint creation failed");
                } else {
                    focus().setWatchpoint(watchpoint);
                }
            } catch (TooManyWatchpointsException tooManyWatchpointsException) {
                gui().errorMessage(tooManyWatchpointsException.getMessage());
            } catch (DuplicateWatchpointException duplicateWatchpointException) {
                gui().errorMessage(duplicateWatchpointException.getMessage());
            } catch (MaxVMBusyException maxVMBusyException) {
                inspection().announceVMBusyFailure(name());
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess()  && watchpointFactory() != null);
        }
    }

    private final SetWordWatchpointAction setWordWatchpointAction = new SetWordWatchpointAction();

    /**
     * @return Singleton interactive Action that will create a memory word watchpoint in the VM.
     */
    public final InspectorAction setWordWatchpoint() {
        return setWordWatchpointAction;
    }

    /**
     * Creates an action that will create a memory word watchpoint.
     *
     * @param address a memory location in the VM
     * @return an Action that will set a memory watchpoint at the address.
     */
    public final InspectorAction setWordWatchpoint(Address address, String string) {
        return new SetWordWatchpointAction(address, string);
    }

    /**
     * Action: create a memory watchpoint, interactive if no location specified.
     */
    final class SetRegionWatchpointAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Watch memory region";
        private static final String DEFAULT_REGION_DESCRIPTION = "";
        private final MemoryRegion memoryRegion;
        private final String regionDescription;

        SetRegionWatchpointAction() {
            super(inspection(), "Watch memory region...");
            this.memoryRegion = null;
            this.regionDescription = null;
            setEnabled(true);
        }

        SetRegionWatchpointAction(MemoryRegion memoryRegion, String actionTitle, String regionDescription) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.memoryRegion = memoryRegion;
            this.regionDescription = regionDescription == null ? DEFAULT_REGION_DESCRIPTION : regionDescription;
            setEnabled(watchpointFactory().findWatchpoints(memoryRegion).isEmpty());
        }

        @Override
        protected void procedure() {
            if (memoryRegion != null) {
                setWatchpoint(memoryRegion, regionDescription);
            } else {
                // TODO (mlvdv) Generalize AddressInputDialog for a Region
                new AddressInputDialog(inspection(), maxVM().bootImageStart(), "Watch memory...", "Watch") {
                    @Override
                    public void entered(Address address) {
                        setWatchpoint(new FixedMemoryRegion(address, maxVM().wordSize(), ""), "User specified region");
                    }
                };
            }
        }

        private void setWatchpoint(MemoryRegion memoryRegion, String description) {
            final WatchpointsViewPreferences prefs = WatchpointsViewPreferences.globalPreferences(inspection());
            try {
                final MaxWatchpoint watchpoint = watchpointFactory().createRegionWatchpoint(description, memoryRegion, prefs.settings());
                if (watchpoint == null) {
                    gui().errorMessage("Watchpoint creation failed");
                } else {
                    focus().setWatchpoint(watchpoint);
                }
            } catch (TooManyWatchpointsException tooManyWatchpointsException) {
                gui().errorMessage(tooManyWatchpointsException.getMessage());
            } catch (DuplicateWatchpointException duplicateWatchpointException) {
                gui().errorMessage(duplicateWatchpointException.getMessage());
            } catch (MaxVMBusyException maxVMBusyException) {
                inspection().announceVMBusyFailure(name());
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess()  && watchpointFactory() != null);
        }
    }

    private final InspectorAction setRegionWatchpointAction = new SetRegionWatchpointAction();

    /**
     * @return Singleton interactive Action that will create a memory  watchpoint in the VM.
     */
    public final InspectorAction setRegionWatchpoint() {
        return setRegionWatchpointAction;
    }

    /**
     * Creates an action that will create a memory watchpoint.
     *
     * @param memoryRegion an area of memory in the VM
     * @param actionTitle a name for the action, use default name if null
     * @param regionDescription a description that will be attached to the watchpoint for viewing purposes, default if null.
     * @return an Action that will set a memory watchpoint at the address.
     */
    public final InspectorAction setRegionWatchpoint(MemoryRegion memoryRegion, String actionTitle, String regionDescription) {
        return new SetRegionWatchpointAction(memoryRegion, actionTitle, regionDescription);
    }

     /**
     * Action: create an object memory watchpoint.
     */
    final class SetObjectWatchpointAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Watch object memory";
        private final TeleObject teleObject;
        private final MemoryRegion memoryRegion;

        SetObjectWatchpointAction(TeleObject teleObject, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.teleObject = teleObject;
            this.memoryRegion = teleObject.memoryRegion();
            refresh(true);
        }

        @Override
        protected void procedure() {
            final WatchpointsViewPreferences prefs = WatchpointsViewPreferences.globalPreferences(inspection());
            try {
                final String description = "Whole object";
                final MaxWatchpoint watchpoint = watchpointFactory().createObjectWatchpoint(description, teleObject, prefs.settings());
                if (watchpoint == null) {
                    gui().errorMessage("Watchpoint creation failed");
                } else {
                    focus().setWatchpoint(watchpoint);
                }
            } catch (TooManyWatchpointsException tooManyWatchpointsException) {
                gui().errorMessage(tooManyWatchpointsException.getMessage());
            } catch (DuplicateWatchpointException duplicateWatchpointException) {
                gui().errorMessage(duplicateWatchpointException.getMessage());
            } catch (MaxVMBusyException maxVMBusyException) {
                ProgramWarning.message("Watchpoint creation failed:  VM busy");
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess()
                && watchpointFactory() != null
                && watchpointFactory().findWatchpoints(memoryRegion).isEmpty());
        }
    }

    /**
     * Creates an action that will create an object memory watchpoint.
     *
     * @param teleObject a heap object in the VM
     * @param actionTitle a name for the action, use default name if null
     * @return an Action that will set an object field watchpoint.
     */
    public final InspectorAction setObjectWatchpoint(TeleObject teleObject, String actionTitle) {
        return new SetObjectWatchpointAction(teleObject, actionTitle);
    }

    /**
     * Action: create an object field watchpoint.
     */
    final class SetFieldWatchpointAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Watch object field";
        private final TeleObject teleObject;
        private final FieldActor fieldActor;
        private final MemoryRegion memoryRegion;

        SetFieldWatchpointAction(TeleObject teleObject, FieldActor fieldActor, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.teleObject = teleObject;
            this.fieldActor = fieldActor;
            this.memoryRegion = teleObject.fieldMemoryRegion(fieldActor);
            refresh(true);
        }

        @Override
        protected void procedure() {
            final WatchpointsViewPreferences prefs = WatchpointsViewPreferences.globalPreferences(inspection());
            try {
                final String description = "Field \"" + fieldActor.name.toString() + "\"";
                final MaxWatchpoint watchpoint = watchpointFactory().createFieldWatchpoint(description, teleObject, fieldActor, prefs.settings());
                if (watchpoint == null) {
                    gui().errorMessage("Watchpoint creation failed");
                } else {
                    focus().setWatchpoint(watchpoint);
                }
            } catch (TooManyWatchpointsException tooManyWatchpointsException) {
                gui().errorMessage(tooManyWatchpointsException.getMessage());
            } catch (DuplicateWatchpointException duplicateWatchpointException) {
                gui().errorMessage(duplicateWatchpointException.getMessage());
            } catch (MaxVMBusyException maxVMBusyException) {
                inspection().announceVMBusyFailure(name());
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess()
                && watchpointFactory() != null
                && watchpointFactory().findWatchpoints(memoryRegion).isEmpty());
        }
    }

    /**
     * Creates an action that will create an object field watchpoint.
     *
     * @param teleObject a heap object in the VM
     * @param fieldActor description of a field in the class type of the heap object
     * @param actionTitle a name for the action, use default name if null
     * @return an Action that will set an object field watchpoint.
     */
    public final InspectorAction setFieldWatchpoint(TeleObject teleObject, FieldActor fieldActor, String actionTitle) {
        return new SetFieldWatchpointAction(teleObject, fieldActor, actionTitle);
    }

    /**
     * Action: create an object field watchpoint.
     */
    final class SetArrayElementWatchpointAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Watch array element";
        private final TeleObject teleObject;
        private final Kind elementKind;
        private final Offset arrayOffsetFromOrigin;
        private final int index;
        private final String indexPrefix;
        private final MemoryRegion memoryRegion;

        SetArrayElementWatchpointAction(TeleObject teleObject, Kind elementKind, Offset arrayOffsetFromOrigin, int index, String indexPrefix, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.teleObject = teleObject;
            this.elementKind = elementKind;
            this.arrayOffsetFromOrigin = arrayOffsetFromOrigin;
            this.index = index;
            this.indexPrefix = indexPrefix;
            final Pointer address = teleObject.origin().plus(arrayOffsetFromOrigin.plus(index * elementKind.width.numberOfBytes));
            this.memoryRegion = new FixedMemoryRegion(address, Size.fromInt(elementKind.width.numberOfBytes), "");
            refresh(true);
        }

        @Override
        protected void procedure() {
            final WatchpointsViewPreferences prefs = WatchpointsViewPreferences.globalPreferences(inspection());
            try {
                final String description = "Element " + indexPrefix + "[" + Integer.toString(index) + "]";
                final MaxWatchpoint watchpoint
                    = watchpointFactory().createArrayElementWatchpoint(description, teleObject, elementKind, arrayOffsetFromOrigin, index, prefs.settings());
                if (watchpoint == null) {
                    gui().errorMessage("Watchpoint creation failed");
                } else {
                    focus().setWatchpoint(watchpoint);
                }
            } catch (TooManyWatchpointsException tooManyWatchpointsException) {
                gui().errorMessage(tooManyWatchpointsException.getMessage());
            } catch (DuplicateWatchpointException duplicateWatchpointException) {
                gui().errorMessage(duplicateWatchpointException.getMessage());
            } catch (MaxVMBusyException maxVMBusyException) {
                inspection().announceVMBusyFailure(name());
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess()
                && watchpointFactory() != null
                && watchpointFactory().findWatchpoints(memoryRegion).isEmpty());
        }
    }

    /**
     * Creates an action that will create an array element watchpoint.
     *
     * @param teleObject a heap object in the VM
     * @param elementKind type category of the array elements
     * @param arrayOffsetFromOrigin offset in bytes from the object origin of element 0
     * @param index index into the array
     * @param indexPrefix  text to prepend to the displayed name(index) of each element.
     * @param actionTitle a name for the action, use default name if null
     * @return an Action that will set an array element watchpoint.
     */
    public final InspectorAction setArrayElementWatchpoint(TeleObject teleObject, Kind elementKind, Offset arrayOffsetFromOrigin, int index, String indexPrefix, String actionTitle) {
        return new SetArrayElementWatchpointAction(teleObject, elementKind, arrayOffsetFromOrigin, index, indexPrefix, actionTitle);
    }

     /**
     * Action: create an object header field watchpoint.
     */
    final class SetHeaderWatchpointAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Watch object header field";
        private final TeleObject teleObject;
        private final HeaderField headerField;
        private final MemoryRegion memoryRegion;

        SetHeaderWatchpointAction(TeleObject teleObject, HeaderField headerField, String actionTitle)  {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.teleObject = teleObject;
            this.headerField = headerField;
            this.memoryRegion = teleObject.headerMemoryRegion(headerField);
            refresh(true);
        }

        @Override
        protected void procedure() {
            final WatchpointsViewPreferences prefs = WatchpointsViewPreferences.globalPreferences(inspection());
            try {
                final String description = "Field \"" + headerField.name + "\"";
                final MaxWatchpoint watchpoint = watchpointFactory().createHeaderWatchpoint(description, teleObject, headerField, prefs.settings());
                if (watchpoint == null) {
                    gui().errorMessage("Watchpoint creation failed");
                } else {
                    focus().setWatchpoint(watchpoint);
                }
            } catch (TooManyWatchpointsException tooManyWatchpointsException) {
                gui().errorMessage(tooManyWatchpointsException.getMessage());
            } catch (DuplicateWatchpointException duplicateWatchpointException) {
                gui().errorMessage(duplicateWatchpointException.getMessage());
            } catch (MaxVMBusyException maxVMBusyException) {
                inspection().announceVMBusyFailure(name());
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess()
                && watchpointFactory() != null
                && watchpointFactory().findWatchpoints(memoryRegion).isEmpty());
        }
    }

    /**
     * Creates an action that will create an object header field watchpoint.
     *
     * @param teleObject a heap object in the VM
     * @param headerField identification of an object header field
     * @param actionTitle a name for the action, use default name if null
     * @return an Action that will set an object header watchpoint
     */
    public final InspectorAction setHeaderWatchpoint(TeleObject teleObject, HeaderField headerField, String actionTitle) {
        return new SetHeaderWatchpointAction(teleObject, headerField, actionTitle);
    }

    /**
     * Action: create an object field watchpoint.
     */
    final class SetThreadLocalWatchpointAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Watch thread local variable";
        private final MaxThreadLocalVariable threadLocalVariable;

        SetThreadLocalWatchpointAction(MaxThreadLocalVariable threadLocalVariable, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.threadLocalVariable = threadLocalVariable;
            refresh(true);
        }

        @Override
        protected void procedure() {
            final WatchpointsViewPreferences prefs = WatchpointsViewPreferences.globalPreferences(inspection());
            try {
                final String description = "Thread local variable\"" + threadLocalVariable.name()
                    + "\" (" + inspection().nameDisplay().shortName(threadLocalVariable.thread()) + ","
                    + threadLocalVariable.safepointState().toString() + ")";
                final MaxWatchpoint watchpoint = watchpointFactory().createVmThreadLocalWatchpoint(description, threadLocalVariable, prefs.settings());
                if (watchpoint == null) {
                    gui().errorMessage("Watchpoint creation failed");
                } else {
                    focus().setWatchpoint(watchpoint);
                }
            } catch (TooManyWatchpointsException tooManyWatchpointsException) {
                gui().errorMessage(tooManyWatchpointsException.getMessage());
            } catch (DuplicateWatchpointException duplicateWatchpointException) {
                gui().errorMessage(duplicateWatchpointException.getMessage());
            } catch (MaxVMBusyException maxVMBusyException) {
                inspection().announceVMBusyFailure(name());
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspection().hasProcess()
                && watchpointFactory() != null
                && watchpointFactory().findWatchpoints(threadLocalVariable.memoryRegion()).isEmpty());
        }
    }

    /**
     * Creates an action that will create a thread local variable watchpoint.
     *
     * @param threadLocalVariable a thread local variable
     * @param actionTitle a name for the action, use default name if null
     * @return an action that will create a thread local variable watchpoint
     */
    public final InspectorAction setThreadLocalWatchpoint(MaxThreadLocalVariable threadLocalVariable, String actionTitle) {
        return new SetThreadLocalWatchpointAction(threadLocalVariable, actionTitle);
    }

    /**
     * Action: remove a specified memory watchpoint.
     */
    final class RemoveWatchpointAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Remove memory watchpoint";
        private final MaxWatchpoint watchpoint;

        RemoveWatchpointAction(MaxWatchpoint watchpoint, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.watchpoint = watchpoint;
        }

        @Override
        protected void procedure() {
            try {
                if (watchpoint.remove()) {
                    focus().setWatchpoint(null);
                }  else {
                    gui().errorMessage("Watchpoint removal failed");
                }
            } catch (MaxVMBusyException maxVMBusyException) {
                inspection().announceVMBusyFailure(name());
            }
        }

    }

    /**
     * Creates an action that will remove a watchpoint.
     *
     * @param watchpoint an existing VM memory watchpoint
     * @param actionTitle a title for the action, use default name if null
     * @return an Action that will remove a watchpoint, if present at memory location.
     */
    public final InspectorAction removeWatchpoint(MaxWatchpoint watchpoint, String actionTitle) {
        return new RemoveWatchpointAction(watchpoint, actionTitle);
    }

    /**
     * Action:  removes the watchpoint from the VM that is currently selected.
     */
    final class RemoveSelectedWatchpointAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Remove selected watchpoint";

        RemoveSelectedWatchpointAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            focus().addListener(new InspectionFocusAdapter() {
                @Override
                public void watchpointFocusSet(MaxWatchpoint oldWatchpoint, MaxWatchpoint watchpoint) {
                    refresh(false);
                }
            });
            refresh(false);
        }

        @Override
        protected void procedure() {
            final MaxWatchpoint watchpoint = focus().watchpoint();
            try {
                if (watchpoint != null) {
                    if (watchpoint.remove()) {
                        focus().setWatchpoint(null);
                    } else {
                        gui().errorMessage("Watchpoint removal failed");
                    }
                } else {
                    gui().errorMessage("No watchpoint selected");
                }
            } catch (MaxVMBusyException maxVMBusyException) {
                inspection().announceVMBusyFailure(name());
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasWatchpoint());
        }
    }

    private InspectorAction removeSelectedWatchpoint = new RemoveSelectedWatchpointAction(null);

    /**
     * @return an Action that will remove the currently selected breakpoint, if any.
     */
    public final InspectorAction removeSelectedWatchpoint() {
        return removeSelectedWatchpoint;
    }

    /**
     * Action: removes a set of existing watchpoints in the VM.
     */
    final class RemoveWatchpointsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Remove watchpoints";
        private final Sequence<MaxWatchpoint> watchpoints;

        RemoveWatchpointsAction(Sequence<MaxWatchpoint> watchpoints, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.watchpoints = watchpoints;
        }

        @Override
        protected void procedure() {
            final MaxWatchpoint focusWatchpoint = focus().watchpoint();
            for (MaxWatchpoint watchpoint : watchpoints) {
                if (focusWatchpoint == watchpoint) {
                    focus().setWatchpoint(null);
                }
                try {
                    if (!watchpoint.remove()) {
                        gui().errorMessage("Failed to remove watchpoint" + watchpoint);
                    }
                } catch (MaxVMBusyException maxVMBusyException) {
                    inspection().announceVMBusyFailure(name());
                }
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(watchpoints.length() > 0);
        }
    }

    /**
     * @return an Action that will remove all watchpoints in the VM.
     */
    public final InspectorAction removeWatchpoints(Sequence<MaxWatchpoint> watchpoints, String actionTitle) {
        return new RemoveWatchpointsAction(watchpoints, actionTitle);
    }

     /**
     * Action: removes all existing watchpoints in the VM.
     */
    final class RemoveAllWatchpointsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Remove all watchpoints";

        RemoveAllWatchpointsAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
            inspection().addInspectionListener(new InspectionListenerAdapter() {
                @Override
                public void watchpointSetChanged() {
                    refresh(true);
                }
            });
        }

        @Override
        protected void procedure() {
            focus().setWatchpoint(null);
            for (MaxWatchpoint watchpoint : watchpointFactory().watchpoints()) {
                try {
                    if (!watchpoint.remove()) {
                        gui().errorMessage("Failed to remove watchpoint" + watchpoint);
                    }
                } catch (MaxVMBusyException maxVMBusyException) {
                    inspection().announceVMBusyFailure(name());
                }
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(watchpointFactory() != null && watchpointFactory().watchpoints().length() > 0);
        }
    }

    private InspectorAction removeAllWatchpoints = new RemoveAllWatchpointsAction(null);

    /**
     * @return an Action that will remove all watchpoints in the VM.
     */
    public final InspectorAction removeAllWatchpoints() {
        return removeAllWatchpoints;
    }

     /**
     * Action:  pause the running VM.
     */
    final class DebugPauseAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Pause process";

        DebugPauseAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            try {
                maxVM().pauseVM();
            } catch (Exception exception) {
                gui().errorMessage("Pause could not be initiated", exception.toString());
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasThread() && inspection().isVMRunning());
        }
    }

    private final InspectorAction debugPauseAction = new DebugPauseAction(null);

    /**
     * @return Singleton Action that will pause execution of theVM.
     */
    public final InspectorAction debugPause() {
        return debugPauseAction;
    }

    /**
     * Action: resumes the running VM.
     */
    final class DebugResumeAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Resume";

        DebugResumeAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            try {
                maxVM().resume(false, true);
            } catch (Exception exception) {
                gui().errorMessage("Run to instruction could not be performed.", exception.toString());
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasThread() && inspection().isVMReady());
        }
    }

    private final InspectorAction debugResumeAction = new DebugResumeAction(null);

     /**
     * @return Singleton Action that will resume full execution of theVM.
     */
    public final InspectorAction debugResume() {
        return debugResumeAction;
    }

    /**
     * Action:  advance the currently selected thread until it returns from its current frame in the VM,
     * ignoring breakpoints.
     */
    final class DebugReturnFromFrameAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Return from frame (ignoring breakpoints)";

        DebugReturnFromFrameAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            try {
                maxVM().returnFromFrame(focus().thread(), false, false);
                // TODO (mlvdv) too broad a catch; narrow this
            } catch (Exception exception) {
                gui().errorMessage("Return from frame (ignoring breakpoints) could not be performed.", exception.toString());
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasThread() && inspection().isVMReady());
        }
    }

    private final InspectorAction debugReturnFromFrameAction = new DebugReturnFromFrameAction(null);

    /**
     * @return Singleton Action that will resume execution in the VM, stopping at the first instruction after returning
     *         from the current frame of the currently selected thread
     */
    public final InspectorAction debugReturnFromFrame() {
        return debugReturnFromFrameAction;
    }

    /**
     * Action:  advance the currently selected thread until it returns from its current frame
     * or hits a breakpoint in the VM.
     */
    final class DebugReturnFromFrameWithBreakpointsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Return from frame";

        DebugReturnFromFrameWithBreakpointsAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
        }

        @Override
        public void procedure() {
            try {
                maxVM().returnFromFrame(focus().thread(), false, true);
            } catch (Exception exception) {
                gui().errorMessage("Return from frame could not be performed.", exception.toString());
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasThread() && inspection().isVMReady());
        }
    }

    private final InspectorAction debugReturnFromFrameWithBreakpointsAction = new DebugReturnFromFrameWithBreakpointsAction(null);

    /**
     * @return Singleton Action that will resume execution in the VM, stopping at the first instruction after returning
     *         from the current frame of the currently selected thread, or at a breakpoint, whichever comes first.
     */
    public final InspectorAction debugReturnFromFrameWithBreakpoints() {
        return debugReturnFromFrameWithBreakpointsAction;
    }

    /**
     * Action:  advance the currently selected thread in the VM until it reaches the specified instruction, or
     * if none specified, then the currently selected instruction, ignoring breakpoints.
     */
    final class DebugRunToInstructionAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Run to selected instruction (ignoring breakpoints)";

        private final MaxCodeLocation codeLocation;

        DebugRunToInstructionAction(MaxCodeLocation codeLocation, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.codeLocation = codeLocation;
            refreshableActions.append(this);
            refresh(true);
        }

        @Override
        protected void procedure() {
            final MaxCodeLocation targetLocation = (codeLocation != null) ? codeLocation : focus().codeLocation();
            if (!targetLocation.address().isZero()) {
                try {
                    maxVM().runToInstruction(targetLocation, false, false);
                } catch (Exception exception) {
                    throw new InspectorError("Run to instruction (ignoring breakpoints) could not be performed.", exception);
                }
            } else {
                gui().errorMessage("No instruction selected");
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasThread() && focus().hasCodeLocation() && inspection().isVMReady());
        }
    }

    private final InspectorAction debugRunToSelectedInstructionAction = new DebugRunToInstructionAction(null, null);

    /**
     * @return Singleton Action that will resume execution in the VM, stopping at the the currently
     * selected instruction, ignoring breakpoints.
     */
    public final InspectorAction debugRunToSelectedInstruction() {
        return debugRunToSelectedInstructionAction;
    }

    /**
     * @param codeLocation a code location in the VM
     * @param actionTitle string that describes the action
     * @return an Action that will resume execution in the VM, stopping at the specified
     * code location, ignoring breakpoints.
     */
    public final InspectorAction debugRunToInstruction(MaxCodeLocation codeLocation, String actionTitle) {
        return new DebugRunToInstructionAction(codeLocation, actionTitle);
    }

    /**
     * Action:  advance the currently selected thread in the VM until it reaches the specified code location
     * (or the currently selected instruction if none specified) or a breakpoint, whichever comes first.
     */
    final class DebugRunToInstructionWithBreakpointsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Run to selected instruction";

        final MaxCodeLocation codeLocation;

        DebugRunToInstructionWithBreakpointsAction(MaxCodeLocation codeLocation, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.codeLocation = codeLocation;
            refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final MaxCodeLocation targetLocation = (codeLocation != null) ? codeLocation : focus().codeLocation();
            if (targetLocation != null && targetLocation.hasAddress()) {
                try {
                    maxVM().runToInstruction(targetLocation, false, true);
                    // TODO (mlvdv)  narrow the catch
                } catch (Exception exception) {
                    throw new InspectorError("Run to selection instruction could not be performed.", exception);
                }
            } else {
                gui().errorMessage("No instruction selected");
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasThread() && focus().hasCodeLocation() && inspection().isVMReady());
        }
    }

    private final InspectorAction debugRunToSelectedInstructionWithBreakpointsAction = new DebugRunToInstructionWithBreakpointsAction(null, null);

    /**
     * @return Singleton Action that will resume execution in the VM, stopping at the selected instruction
     * or a breakpoint, whichever comes first..
     */
    public final InspectorAction debugRunToSelectedInstructionWithBreakpoints() {
        return debugRunToSelectedInstructionWithBreakpointsAction;
    }

    /**
     * @param codeLocation a code location in the VM
     * @param actionTitle string that identifies the action
     * @return an Action that will resume execution in the VM, stopping at the specified instruction
     * or a breakpoint, whichever comes first..
     */
    public final InspectorAction debugRunToInstructionWithBreakpoints(MaxCodeLocation codeLocation, String actionTitle) {
        return new DebugRunToInstructionWithBreakpointsAction(codeLocation, actionTitle);
    }

    /**
     * Action:  advance the currently selected thread in the VM until it reaches the next call instruction,
     * ignoring breakpoints; fails if there is no known call in the method containing the IP.
     */
    final class DebugRunToNextCallAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Run to next call instruction (ignoring breakpoints)";

        DebugRunToNextCallAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final MaxCodeLocation maxCodeLocation = focus().codeLocation();
            if (maxCodeLocation != null && !maxCodeLocation.hasAddress()) {
                final TeleTargetMethod teleTargetMethod = maxVM().findTeleTargetRoutine(TeleTargetMethod.class, maxCodeLocation.address());
                if (teleTargetMethod != null) {
                    final MaxCodeLocation nextCallLocation = teleTargetMethod.getNextCallLocation(maxCodeLocation);
                    if (nextCallLocation != null) {
                        try {
                            maxVM().runToInstruction(nextCallLocation, false, false);
                        } catch (Exception exception) {
                            throw new InspectorError("Run to next call instruction (ignoring breakpoints) could not be performed.", exception);
                        }
                    }
                }
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasThread() && focus().hasCodeLocation() && inspection().isVMReady());
        }
    }

    private final InspectorAction debugRunToNextCallAction = new DebugRunToNextCallAction(null);

    /**
     * @return Singleton Action that will resume execution in the VM, stopping at the next call instruction,
     * ignoring breakpoints; fails if there is no known call in the method containing the IP.
     */
    public final InspectorAction debugRunToNextCall() {
        return debugRunToNextCallAction;
    }

    /**
     * Action:  advance the currently selected thread in the VM until it reaches the selected instruction
     * or a breakpoint.
     */
    final class DebugRunToNextCallWithBreakpointsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Run to next call instruction";

        DebugRunToNextCallWithBreakpointsAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final MaxCodeLocation maxCodeLocation = focus().codeLocation();
            assert maxCodeLocation.hasAddress();
            final TeleTargetMethod teleTargetMethod = maxVM().findTeleTargetRoutine(TeleTargetMethod.class, maxCodeLocation.address());
            if (teleTargetMethod != null) {
                final MaxCodeLocation nextCallLocation = teleTargetMethod.getNextCallLocation(maxCodeLocation);
                if (nextCallLocation != null) {
                    try {
                        maxVM().runToInstruction(nextCallLocation, false, true);
                    } catch (Exception exception) {
                        throw new InspectorError("Run to next call instruction could not be performed.", exception);
                    }
                }
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasThread() && focus().hasCodeLocation() && inspection().isVMReady());
        }
    }

    private final InspectorAction debugNextRunToCallWithBreakpointsAction = new DebugRunToNextCallWithBreakpointsAction(null);

    /**
     * @return Singleton Action that will resume execution in the VM, stopping at the first instruction after returning
     *         from the current frame of the currently selected thread, or at a breakpoint, whichever comes first.
     */
    public final InspectorAction debugRunToNextCallWithBreakpoints() {
        return debugNextRunToCallWithBreakpointsAction;
    }

    /**
     * Action:  advances the currently selected thread one step in the VM.
     */
    class DebugSingleStepAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Single instruction step";

        DebugSingleStepAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
        }

        @Override
        public  void procedure() {
            final MaxThread thread = focus().thread();
            try {
                maxVM().singleStepThread(thread, false);
            } catch (Exception exception) {
                gui().errorMessage("Couldn't single step", exception.toString());
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasThread() && inspection().isVMReady());
        }
    }

    private final InspectorAction debugSingleStepAction = new DebugSingleStepAction(null);

    /**
     * @return Singleton action that will single step the currently selected thread in the VM
     */
    public final InspectorAction debugSingleStep() {
        return debugSingleStepAction;
    }

    /**
     * Action:   resumes execution of the VM, stopping at the one immediately after the current
     *         instruction of the currently selected thread.
     */
    final class DebugStepOverAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Step over (ignoring breakpoints)";

        DebugStepOverAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final MaxThread thread = focus().thread();
            try {
                maxVM().stepOver(thread, false, false);
            } catch (Exception exception) {
                gui().errorMessage("Step over (ignoring breakpoints) could not be performed.", exception.toString());
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasThread() && inspection().isVMReady());
        }
    }

    private final InspectorAction debugStepOverAction = new DebugStepOverAction(null);

    /**
     * @return Singleton Action that will resume execution of the VM, stopping at the one immediately after the current
     *         instruction of the currently selected thread
     */
    public final InspectorAction debugStepOver() {
        return debugStepOverAction;
    }

    /**
     * Action:   resumes execution of the VM, stopping at the one immediately after the current
     *         instruction of the currently selected thread or at a breakpoint.
     */
    final class DebugStepOverWithBreakpointsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Step over";

        DebugStepOverWithBreakpointsAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
        }

        @Override
        protected void procedure() {
            final MaxThread thread = focus().thread();
            try {
                maxVM().stepOver(thread, false, true);
            } catch (Exception exception) {
                gui().errorMessage("Step over could not be performed.", exception.toString());
            }
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(focus().hasThread() && inspection().isVMReady());
        }
    }

    private final InspectorAction debugStepOverWithBreakpointsAction = new DebugStepOverWithBreakpointsAction(null);

    /**
     * @return Singleton Action that will resume execution of the VM, stopping at the one immediately after the current
     *         instruction of the currently selected thread
     */
    public final InspectorAction debugStepOverWithBreakpoints() {
        return debugStepOverWithBreakpointsAction;
    }

    /**
     * Action:  interactively invoke a method.
     */
    private class DebugInvokeMethodAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "Invoke method";
        private final TeleClassMethodActor teleClassMethodActor;

        public DebugInvokeMethodAction(TeleClassMethodActor teleClassMethodActor, String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            this.teleClassMethodActor =  teleClassMethodActor;
        }

        @Override
        public void procedure() {
            ClassMethodActor classMethodActor = teleClassMethodActor.classMethodActor();
            ReferenceValue receiver = null;

            if (classMethodActor instanceof VirtualMethodActor) {
                final String input = gui().inputDialog("Argument 0 (receiver, must be a reference to a " + classMethodActor.holder() + " or subclass, origin address in hex):", "");
                if (input == null) {
                    // User clicked cancel.
                    return;
                }
                receiver = maxVM().createReferenceValue(maxVM().originToReference(Pointer.fromLong(new BigInteger(input, 16).longValue())));
                final ClassActor dynamicClass = receiver.getClassActor();
                classMethodActor = dynamicClass.findClassMethodActor(classMethodActor);
            }
            final Value[] arguments = MethodArgsDialog.getArgs(inspection(), classMethodActor, receiver);
            if (arguments == null) {
                // User clicked cancel.
                return;
            }
            try {
                final Value returnValue = maxVM().interpretMethod(classMethodActor, arguments);
                gui().informationMessage("Method " + classMethodActor.name + " returned " + returnValue.toString());
            } catch (TeleInterpreterException teleInterpreterException) {
                throw new InspectorError(teleInterpreterException);
            }
        }
    }

    /**
     * Creates an action that lets the user invoke a method interactively.
     *
     * @param teleClassMethodActor representation of a method in the VM
     * @param actionTitle name of the action for display on menu or button
     * @return an interactive action for method invocation
     */
    public InspectorAction debugInvokeMethod(TeleClassMethodActor teleClassMethodActor, String actionTitle) {
        return new DebugInvokeMethodAction(teleClassMethodActor, actionTitle);
    }

    /**
     * Creates an action that lets the user invoke a method interactively.
     *
     * @param teleClassMethodActor representation of a method in the VM
     * @return an interactive action for method invocation
     */
    public InspectorAction debugInvokeMethod(TeleClassMethodActor teleClassMethodActor) {
        return new DebugInvokeMethodAction(teleClassMethodActor, null);
    }

    /**
     * Action:  displays and highlights an inspection of the current Java frame descriptor.
     */
    final class InspectJavaFrameDescriptorAction extends InspectorAction {
        private static final String DEFAULT_TITLE = "Inspect Java frame descriptor";
        private TargetJavaFrameDescriptor targetJavaFrameDescriptor;
        private TargetABI abi;

        InspectJavaFrameDescriptorAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
            refreshableActions.append(this);
            focus().addListener(new InspectionFocusAdapter() {
                @Override
                public void codeLocationFocusSet(MaxCodeLocation codeLocation, boolean interactiveForNative) {
                    refresh(false);
                }
            });
        }

        @Override
        protected void procedure() {
            assert targetJavaFrameDescriptor != null;
            TargetJavaFrameDescriptorInspector.make(inspection(), targetJavaFrameDescriptor, abi).highlight();
        }

        /**
         * @return whether there is a Java frame descriptor at the focus target code location
         */
        private boolean inspectable() {
            if (focus().hasCodeLocation()) {
                final Address instructionAddress = focus().codeLocation().address();
                if (instructionAddress != null && !instructionAddress.isZero()) {
                    final TeleTargetMethod teleTargetMethod = maxVM().makeTeleTargetMethod(instructionAddress);
                    if (teleTargetMethod != null) {
                        final int stopIndex = teleTargetMethod.getJavaStopIndex(instructionAddress);
                        if (stopIndex >= 0) {
                            BytecodeLocation bytecodeLocation = teleTargetMethod.getBytecodeLocation(stopIndex);
                            if (!(bytecodeLocation instanceof TargetJavaFrameDescriptor)) {
                                return false;
                            }
                            targetJavaFrameDescriptor = (TargetJavaFrameDescriptor) bytecodeLocation;
                            if (targetJavaFrameDescriptor == null) {
                                return false;
                            }
                            abi = teleTargetMethod.getAbi();
                            return true;
                        }
                    }
                }
            }
            targetJavaFrameDescriptor = null;
            abi = null;
            return false;
        }

        @Override
        public void refresh(boolean force) {
            setEnabled(inspectable());
        }
    }

    private InspectorAction inspectJavaFrameDescriptor = new InspectJavaFrameDescriptorAction(null);

    /**
     * @return an Action that will display an inspection of the current Java frame descriptor.
     */
    public final InspectorAction inspectJavaFrameDescriptor() {
        return inspectJavaFrameDescriptor;
    }

    /**
     * Action:  makes visible and highlight the {@link FocusInspector}.
     */
    final class ViewFocusAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "View User Focus";

        ViewFocusAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
        }

        @Override
        protected void procedure() {
            FocusInspector.make(inspection()).highlight();
        }
    }

    private InspectorAction viewFocus = new ViewFocusAction(null);

    /**
     * @return an Action that will make visible the {@link FocusInspector}.
     */
    public final InspectorAction viewFocus() {
        return viewFocus;
    }

    /**
     * Action:  lists to the console this history of the VM state.
     */
    final class ListVMStateHistoryAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "List VM state history";

        ListVMStateHistoryAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
        }

        @Override
        protected void procedure() {
            vmState().writeSummary(System.out);
        }
    }

    private InspectorAction listVMStateHistory = new ListVMStateHistoryAction(null);

    /**
     * @return an Action that will list to the console the history of the VM state
     */
    public final InspectorAction listVMStateHistory() {
        return listVMStateHistory;
    }

    /**
     * Action:  lists to the console the stack frames in the currently focused thread.
     */
    final class ListStackFrames extends InspectorAction {

        private static final String DEFAULT_TITLE = "List current thread's stack";

        ListStackFrames(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
        }

        @Override
        protected void procedure() {
            final MaxThread thread = focus().thread();
            if (thread != null) {
                thread.stack().writeSummary(System.out);
            }
        }
    }

    private InspectorAction listStackFrames = new ListStackFrames(null);

    /**
     * @return an Action that will list to the console the history of the VM state
     */
    public final InspectorAction listStackFrames() {
        return listStackFrames;
    }

    /**
     * Action:  lists to the console all entries in the {@link TeleCodeRegistry}.
     */
    final class ListCodeRegistryAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "List code registry contents";

        ListCodeRegistryAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
        }

        @Override
        protected void procedure() {
            maxVM().describeTeleTargetRoutines(System.out);
        }
    }

    private InspectorAction listCodeRegistry = new ListCodeRegistryAction(null);

    /**
     * @return an Action that will list to the console the entries in the {@link TeleCodeRegistry}.
     */
    public final InspectorAction listCodeRegistry() {
        return listCodeRegistry;
    }

    /**
     * Action:  lists to the console all entries in the {@link TeleCodeRegistry} to an interactively specified file.
     */
    final class ListCodeRegistryToFileAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "List code registry contents to a file...";

        ListCodeRegistryToFileAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
        }

        @Override
        protected void procedure() {
            //Create a file chooser
            final JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogType(JFileChooser.SAVE_DIALOG);
            fileChooser.setDialogTitle("Save code registry summary to file:");
            final int returnVal = fileChooser.showSaveDialog(gui().frame());
            if (returnVal != JFileChooser.APPROVE_OPTION) {
                return;
            }
            final File file = fileChooser.getSelectedFile();
            if (file.exists() && !gui().yesNoDialog("File " + file + "exists.  Overwrite?\n")) {
                return;
            }
            try {
                final PrintStream printStream = new PrintStream(new FileOutputStream(file, false));
                maxVM().describeTeleTargetRoutines(printStream);
            } catch (FileNotFoundException fileNotFoundException) {
                gui().errorMessage("Unable to open " + file + " for writing:" + fileNotFoundException);
            }
        }
    }

    private InspectorAction listCodeRegistryToFile = new ListCodeRegistryToFileAction(null);

    /**
     * @return an interactive Action that will list to a specified file the entries in the {@link TeleCodeRegistry}.
     */
    public final InspectorAction listCodeRegistryToFile() {
        return listCodeRegistryToFile;
    }

    /**
     * Action:  lists to the console all existing breakpoints.
     */
    final class ListBreakpointsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "List all breakpoints";

        ListBreakpointsAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
        }

        @Override
        protected void procedure() {
            maxVM().breakpointFactory().writeSummary(System.out);
        }
    }

    private InspectorAction listBreakpoints = new ListBreakpointsAction(null);

    /**
     * @return an Action that will list to the console a summary of breakpoints in the VM.
     */
    public final InspectorAction listBreakpoints() {
        return listBreakpoints;
    }

    /**
     * Action:  lists to the console all existing watchpoints.
     */
    final class ListWatchpointsAction extends InspectorAction {

        private static final String DEFAULT_TITLE = "List all watchpoints";

        ListWatchpointsAction(String actionTitle) {
            super(inspection(), actionTitle == null ? DEFAULT_TITLE : actionTitle);
        }

        @Override
        protected void procedure() {
            watchpointFactory().writeSummary(System.out);
        }
    }

    private InspectorAction listWatchpoints = new ListWatchpointsAction(null);

    /**
     * @return an Action that will list to the console a summary of watchpoints in the VM.
     */
    public final InspectorAction listWatchpoints() {
        return listWatchpoints;
    }

    /**
     * @return menu items for memory-related actions that are independent of context
     */
    public InspectorMenuItems genericMemoryMenuItems() {
        return new AbstractInspectorMenuItems(inspection()) {
            public void addTo(InspectorMenu menu) {
                menu.add(inspectMemoryRegionsMenu());
                menu.add(inspectMemoryWords());
                menu.add(inspectMemoryBytes());
                menu.add(memoryWordsInspectorsMenu());
            }
        };
    }

    /**
     * @return menu items for code-related actions that are independent of context
     */
    public InspectorMenuItems genericCodeMenuItems() {
        return new AbstractInspectorMenuItems(inspection()) {
            public void addTo(InspectorMenu menu) {
                menu.add(actions().viewMethodCodeAtSelection());
                menu.add(actions().viewMethodCodeAtIP());
                menu.add(actions().viewMethodTargetCode());
                final JMenu methodSub = new JMenu("View method code by name");
                methodSub.add(actions().viewMethodBytecodeByName());
                methodSub.add(actions().viewMethodTargetCodeByName());
                menu.add(methodSub);
                final JMenu bootMethodSub = new JMenu("View boot image method code");
                bootMethodSub.add(actions().viewRunMethodCodeInBootImage());
                bootMethodSub.add(actions().viewThreadRunMethodCodeInBootImage());
                menu.add(bootMethodSub);
                final JMenu byAddressSub = new JMenu("View target code by address");
                byAddressSub.add(actions().viewMethodCodeByAddress());
                byAddressSub.add(actions().viewNativeCodeByAddress());
                menu.add(byAddressSub);
            }
        };
    }

    /**
     * @return menu items for breakpoint-related actions that are independent of context
     */
    public InspectorMenuItems genericBreakpointMenuItems() {
        return new AbstractInspectorMenuItems(inspection()) {
            public void addTo(InspectorMenu menu) {

                final InspectorMenu builtinBreakpointsMenu = new BuiltinBreakpointsMenu("Break at builtin");
                menu.add(builtinBreakpointsMenu);

                final InspectorMenu methodEntryBreakpoints = new InspectorMenu("Break at method entry");
                methodEntryBreakpoints.add(actions().setTargetCodeBreakpointAtMethodEntriesByName());
                methodEntryBreakpoints.add(actions().setBytecodeBreakpointAtMethodEntryByName());
                methodEntryBreakpoints.add(actions().setBytecodeBreakpointAtMethodEntryByKey());
                menu.add(methodEntryBreakpoints);

                final InspectorMenu breakAt = new InspectorMenu("Break at target code");
                breakAt.add(actions().setTargetCodeBreakpointAtAddress());
                breakAt.add(actions().setTargetCodeBreakpointAtObjectInitializer());
                menu.add(breakAt);

                final InspectorMenu toggle = new InspectorMenu("Toggle breakpoint");
                toggle.add(actions().toggleTargetCodeBreakpoint());
                menu.add(toggle);

                menu.add(actions().removeAllBreakpoints());
            }
        };
    }

    /**
     * @return menu items for watchpoint-related actions that are independent of context
     */
    public InspectorMenuItems genericWatchpointMenuItems() {
        return new AbstractInspectorMenuItems(inspection()) {
            public void addTo(InspectorMenu menu) {
                menu.add(actions().setWordWatchpoint());
                menu.add(actions().removeAllWatchpoints());
            }
        };
    }

    /**
     * @return menu items for object-related actions that are independent of context
     */
    public InspectorMenuItems genericObjectMenuItems() {
        return new AbstractInspectorMenuItems(inspection()) {
            public void addTo(InspectorMenu menu) {

                final JMenu methodActorMenu = new JMenu("Inspect method actor");
                methodActorMenu.add(inspectMethodActorByName());
                menu.add(methodActorMenu);

                final JMenu classActorMenu = new JMenu("Inspect class actor");
                classActorMenu.add(inspectClassActorByName());
                classActorMenu.add(inspectClassActorByHexId());
                classActorMenu.add(inspectClassActorByDecimalId());
                classActorMenu.add(inspectBootClassRegistry());
                menu.add(classActorMenu);

                final JMenu objectMenu = new JMenu("Inspect object");
                objectMenu.add(inspectObject());
                objectMenu.add(inspectObjectByID());
                menu.add(objectMenu);

                menu.add(objectInspectorsMenu());
            }
        };
    }

    /**
     * @return menu items for view-related actions that are independent of context
     */
    public InspectorMenuItems genericViewMenuItems() {
        return new AbstractInspectorMenuItems(inspection()) {
            public void addTo(InspectorMenu menu) {
                menu.add(actions().viewBootImage());
                menu.add(actions().viewBreakpoints());
                menu.add(actions().memoryWordsInspectorsMenu());
                menu.add(actions().viewMemoryRegions());
                menu.add(actions().viewMethodCode());
                menu.add(actions().objectInspectorsMenu());
                menu.add(actions().viewRegisters());
                menu.add(actions().viewStack());
                menu.add(actions().viewThreads());
                menu.add(actions().viewVmThreadLocals());
                if (watchpointsEnabled()) {
                    menu.add(actions().viewWatchpoints());
                }
            }
        };
    }

}

