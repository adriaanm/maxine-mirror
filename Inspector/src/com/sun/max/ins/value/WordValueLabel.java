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
package com.sun.max.ins.value;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;

import com.sun.max.ins.*;
import com.sun.max.ins.gui.*;
import com.sun.max.ins.method.*;
import com.sun.max.program.*;
import com.sun.max.tele.*;
import com.sun.max.tele.object.*;
import com.sun.max.tele.reference.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.value.*;

/**
 * A textual label for a word of machine data from the VM,
 * with multiple display modes and user interaction affordances.
 *
 * @author Bernd Mathiske
 * @author Michael Van De Vanter
 */
public class WordValueLabel extends ValueLabel {

    private static final int TRACE_VALUE = 1;

    // Optionally supplied component that needs to be
    // repainted when this label changes its appearance.
    private final Component parent;

    /**
     * The expected kind of word value. The visual
     * representations available (of which there may only
     * be one) are derived from this and the word's value.
     */
    public enum ValueMode {
        WORD,
        REFERENCE,
        LITERAL_REFERENCE,
        INTEGER_REGISTER,
        FLAGS_REGISTER,
        FLOATING_POINT,
        CALL_ENTRY_POINT,
        ITABLE_ENTRY,
        CALL_RETURN_POINT;
    }

    private final ValueMode valueMode;

    /**
     * The actual kind of word value, determined empirically by reading from the VM; this may change after update.
     * Possible visual presentations of a word, constrained by the {@linkplain ValueMode valueMode} of the
     * label and its value.
     */
    /**
     * @author Michael Van De Vanter
     *
     */
    /**
     * @author Michael Van De Vanter
     *
     */
    private enum DisplayMode {

        /**
         * Display generically as a word of data, about which nothing is known other than non-null.
         */
        WORD,

        /**
         * Display generically as a word of data in the special case where the word is the null value.
         */
        NULL_WORD,

        /**
         * Expected to be an object reference, but something about it is broken.
         */
        INVALID_OBJECT_REFERENCE,

        /**
         * Numeric display of a valid object reference.
         */
        OBJECT_REFERENCE,

        /**
         * Textual display of a valid object reference.
         */
        OBJECT_REFERENCE_TEXT,

        /**
         * Numeric display of a valid pointer into a stack.
         */
        STACK_LOCATION,

        /**
         * Textual display of a valid pointer into a stack.
         */
        STACK_LOCATION_TEXT,

        /**
         * Numeric display of a valid pointer to a thread local value.
         */
        THREAD_LOCALS_BLOCK_LOCATION,

        /**
         * Textual display of a valid pointer to a thread local value.
         */
        THREAD_LOCALS_BLOCK_LOCATION_TEXT,

        /**
         * Numeric display of a valid pointer to a method entry in compiled code.
         */
        CALL_ENTRY_POINT,

        /**
         * Textual display of a valid pointer to a method entry in compiled code.
         */
        CALL_ENTRY_POINT_TEXT,

        /**
         * The internal ID of a {@link ClassActor} in the VM.
         */
        CLASS_ACTOR_ID,

        CLASS_ACTOR,
        CALL_RETURN_POINT,
        CALL_RETURN_POINT_TEXT,

        /**
         * Display of bits interpreted as flags.
         */
        FLAGS,

        /**
         * Display of numeric value in decimal.
         */
        DECIMAL,

        /**
         * Display of a floating point value.
         */
        FLOAT,

        /**
         * Display of an extended floating point value.
         */
        DOUBLE,

        /**
         * Numeric display of what is expected to be a reference, but which is not checked by reading from the VM.
         */
        UNCHECKED_REFERENCE,

        /**
         * Numeric display of what is expected to point to a code call site, but which is not checked by reading from the VM.
         */
        UNCHECKED_CALL_POINT,

        /**
         * Numeric display of a word for which there are no expectations, and which is not checked by reading from the VM.
         */
        UNCHECKED_WORD,

        /**
         * Numeric display of a word whose value is invalid relative to expectations for it.
         */
        INVALID,

        /**
         * Display in situations where the value cannot be read from the VM.
         */
        UNAVAILABLE;
    }

    private DisplayMode displayMode;

    /**
     * Creates a display label for a word of machine data, initially set to null.
     * <br>
     * Content of label is supplied by override {@link ValueLabel#fetchValue()}, which
     * gets called initially and when the label is refreshed.
     * <br>
     * Display state can be cycled among alternate presentations in some situations.
     * <br>
     * Can be used as a cell renderer in a table, but the enclosing table must be explicitly repainted
     * when the display state is cycled; this will be done automatically if the table is passed in
     * as the parent component.
     *
     * @param inspection
     * @param valueMode presumed type of value for the word, influences display modes
     * @param parent a component that should be repainted when the display state is cycled;
     */
    public WordValueLabel(Inspection inspection, ValueMode valueMode, Component parent) {
        this(inspection, valueMode, Word.zero(), parent);
    }

    /**
     * Creates a display label for a word of machine data, initially set to null.
     * <br>
     * Content of label is set initially by parameter.  It can be updated by overriding{@link ValueLabel#fetchValue()}, which
     * gets called initially and when the label is refreshed.
     * <br>
     * Display state can be cycled among alternate presentations in some situations.
     * <br>
     * Can be used as a cell renderer in a table, but the enclosing table must be explicitly repainted
     * when the display state is cycled; this will be done automatically if the table is passed in
     * as the parent component.
     *
     * @param inspection
     * @param valueMode presumed type of value for the word, influences display modes
     * @param word initial value for content.
     * @param parent a component that should be repainted when the display state is cycled;
     */
    public WordValueLabel(Inspection inspection, ValueMode valueMode, Word word, Component parent) {
        super(inspection, null);
        this.parent = parent;
        this.valueMode = valueMode;
        initializeValue();
        if (value() == null) {
            setValue(new WordValue(word));
        } else {
            setValue(value());
        }
        redisplay();
        addMouseListener(new InspectorMouseClickAdapter(inspection()) {
            @Override
            public void procedure(final MouseEvent mouseEvent) {
                //System.out.println("WVL (" + _valueMode.toString() + ", " + _valueKind.toString() + ")");
                switch (inspection().gui().getButton(mouseEvent)) {
                    case MouseEvent.BUTTON1: {
                        final InspectorAction inspectAction = getInspectValueAction(value());
                        if (inspectAction != null) {
                            inspectAction.perform();
                        }
                        break;
                    }
                    case MouseEvent.BUTTON2: {
                        final InspectorAction cycleAction = getCycleDisplayTextAction();
                        if (cycleAction != null) {
                            cycleAction.perform();
                        }
                        break;
                    }
                    case MouseEvent.BUTTON3: {
                        final InspectorPopupMenu menu = new InspectorPopupMenu();
                        menu.add(new WordValueMenuItems(inspection(), value()));
                        switch (displayMode) {
                            case OBJECT_REFERENCE:
                            case OBJECT_REFERENCE_TEXT: {
                                TeleObject teleObject = null;
                                try {
                                    teleObject = vm().heap().findTeleObject(vm().wordToReference(value().toWord()));
                                } catch (MaxVMBusyException e) {
                                    // Can't learn anything about it right now
                                }
                                if (teleObject != null) {
                                    final TeleClassMethodActor teleClassMethodActor = teleObject.getTeleClassMethodActorForObject();
                                    if (teleClassMethodActor != null) {
                                        // Add method-related menu items
                                        final ClassMethodActorMenuItems items = new ClassMethodActorMenuItems(inspection(), teleClassMethodActor);
                                        items.addTo(menu);
                                    }
                                }
                                break;
                            }
                            case STACK_LOCATION:
                            case STACK_LOCATION_TEXT: {
                                // TODO (mlvdv)  special right-button menu items appropriate to a pointer into stack memory
                                break;
                            }
                            case THREAD_LOCALS_BLOCK_LOCATION:
                            case THREAD_LOCALS_BLOCK_LOCATION_TEXT: {
                                // TODO (mlvdv)  special right-button menu items appropriate to a pointer into a thread locals block
                                break;
                            }
                            default: {
                                break;
                            }
                        }
                        menu.show(mouseEvent.getComponent(), mouseEvent.getX(), mouseEvent.getY());
                    }
                }
            }
        });
    }

    /** Object in the VM heap pointed to by the word, if it is a valid reference. */
    private TeleObject teleObject;

    /** Non-null if a Class ID. */
    private TeleClassActor teleClassActor;

    /** Non-null if a code pointer. */
    private MaxCompiledCode compiledCode;

    /** Non-null if a stack reference. */
    private MaxThread thread;

    @Override
    public final void setValue(Value newValue) {
        teleObject = null;
        teleClassActor = null;
        compiledCode = null;
        thread = null;

        if (newValue == VoidValue.VOID) {
            displayMode = DisplayMode.INVALID;
        } else if (valueMode == ValueMode.FLAGS_REGISTER) {
            if (newValue == null) {
                displayMode = DisplayMode.INVALID;
            } else if (displayMode == null) {
                displayMode = DisplayMode.FLAGS;
            }
        } else if (valueMode == ValueMode.FLOATING_POINT) {
            if (newValue == null) {
                displayMode = DisplayMode.INVALID;
            } else if (displayMode == null) {
                displayMode = DisplayMode.DOUBLE;
            }
        } else if (!inspection().investigateWordValues()) {
            if (valueMode == ValueMode.REFERENCE || valueMode == ValueMode.LITERAL_REFERENCE) {
                displayMode = DisplayMode.UNCHECKED_REFERENCE;
            } else if (valueMode == ValueMode.CALL_ENTRY_POINT || valueMode == ValueMode.CALL_RETURN_POINT) {
                displayMode = DisplayMode.UNCHECKED_CALL_POINT;
            } else {
                displayMode = DisplayMode.UNCHECKED_WORD;
            }
        } else {
            displayMode = DisplayMode.WORD;
            if (vm().isBootImageRelocated()) {
                if (newValue == null || newValue.isZero()) {
                    if (valueMode == ValueMode.REFERENCE) {
                        displayMode = DisplayMode.NULL_WORD;
                    }
                } else if (vm().isValidReference(vm().wordToReference(newValue.toWord()))) {
                    displayMode = (valueMode == ValueMode.REFERENCE || valueMode == ValueMode.LITERAL_REFERENCE) ? DisplayMode.OBJECT_REFERENCE_TEXT : DisplayMode.OBJECT_REFERENCE;
                    final TeleReference ref = (TeleReference) vm().wordToReference(newValue.toWord());

                    try {
                        teleObject = vm().heap().findTeleObject(ref);
                        if (teleObject == null) {
                            displayMode = DisplayMode.INVALID_OBJECT_REFERENCE;
                        }
                    } catch (MaxVMBusyException maxVMBusyException) {
                        displayMode = DisplayMode.UNAVAILABLE;
                    } catch (Throwable throwable) {
                        // If we don't catch this the views will not be updated at all.
                        teleObject = null;
                        displayMode = DisplayMode.INVALID_OBJECT_REFERENCE;
                        setToolTipText("<html><b>" + throwable + "</b><br>See log for complete stack trace.");
                        throwable.printStackTrace(Trace.stream());
                    }
                } else {
                    final Address address = newValue.toWord().asAddress();
                    thread = vm().threadManager().findThread(address);
                    if (thread != null && thread.stack().memoryRegion().contains(address)) {
                        displayMode = valueMode == ValueMode.REFERENCE ? DisplayMode.STACK_LOCATION_TEXT : DisplayMode.STACK_LOCATION;
                    } else if (thread != null && thread.localsBlock().memoryRegion() != null && thread.localsBlock().memoryRegion().contains(address)) {
                        displayMode = valueMode == ValueMode.REFERENCE ? DisplayMode.THREAD_LOCALS_BLOCK_LOCATION_TEXT : DisplayMode.THREAD_LOCALS_BLOCK_LOCATION;
                    } else {
                        if (valueMode == ValueMode.REFERENCE || valueMode == ValueMode.LITERAL_REFERENCE) {
                            displayMode = DisplayMode.INVALID_OBJECT_REFERENCE;
                        } else {
                            try {
                                compiledCode = vm().codeCache().findCompiledCode(newValue.toWord().asAddress());
                                if (compiledCode != null) {
                                    final Address codeStart = compiledCode.getCodeStart();
                                    final Word jitEntryPoint = codeStart.plus(CallEntryPoint.JIT_ENTRY_POINT.offset());
                                    final Word optimizedEntryPoint = codeStart.plus(CallEntryPoint.OPTIMIZED_ENTRY_POINT.offset());
                                    if (newValue.toWord().equals(optimizedEntryPoint) || newValue.toWord().equals(jitEntryPoint)) {
                                        displayMode = (valueMode == ValueMode.CALL_ENTRY_POINT) ? DisplayMode.CALL_ENTRY_POINT_TEXT : DisplayMode.CALL_ENTRY_POINT;
                                    } else {
                                        displayMode = (valueMode == ValueMode.CALL_RETURN_POINT) ? DisplayMode.CALL_RETURN_POINT : DisplayMode.CALL_RETURN_POINT;
                                    }
                                } else if (valueMode == ValueMode.ITABLE_ENTRY) {
                                    final TeleClassActor teleClassActor = vm().classRegistry().findTeleClassActor(newValue.asWord().asAddress().toInt());
                                    if (teleClassActor != null) {
                                        this.teleClassActor = teleClassActor;
                                        displayMode = DisplayMode.CLASS_ACTOR;
                                    } else {
                                        displayMode = DisplayMode.CLASS_ACTOR_ID;
                                    }
                                }
                            } catch (DataIOError dataIOError) {
                                // Can't read anything, so just display as a plain word.
                                displayMode = DisplayMode.WORD;
                            } catch (Throwable throwable) {

                                // If we don't catch this the views will not be updated at all.
                                displayMode = DisplayMode.INVALID;
                                setToolTipText("<html><b>" + throwable + "</b><br>See log for complete stack trace.");
                                throwable.printStackTrace(Trace.stream());
                            }
                        }
                    }
                }
            }
        }
        super.setValue(newValue);
    }

    public void redisplay() {
        setValue(value());
    }

    @Override
    public void updateText() {
        final Value value = value();
        if (value == null) {
            return;
        }
        if (value == VoidValue.VOID) {
            setFont(style().wordAlternateTextFont());
            setForeground(style().wordInvalidDataColor());
            setWrappedText("void");
            setToolTipText("Unable to read value");
            if (parent != null) {
                parent.repaint();
            }
            return;
        }
        final String hexString = (valueMode == ValueMode.WORD
                        || valueMode == ValueMode.INTEGER_REGISTER
                        || valueMode == ValueMode.FLAGS_REGISTER
                        || valueMode == ValueMode.FLOATING_POINT) ? value.toWord().toPaddedHexString('0') : value.toWord().toHexString();
        switch (displayMode) {
            case WORD: {
                setFont(style().wordDataFont());
                setForeground(value.isZero() ? style().wordNullDataColor() : null);
                setWrappedText(hexString);
                setWrappedToolTipText("Int: " + (value.isZero() ? 0 : Long.toString(value.toLong())));
                break;
            }
            case UNCHECKED_WORD: {
                setFont(style().wordDataFont());
                setForeground(value.isZero() ? style().wordNullDataColor() : null);
                setWrappedText(hexString);
                setWrappedToolTipText("Unchecked word");
                break;
            }
            case NULL_WORD: {
                setFont(style().wordAlternateTextFont());
                setForeground(style().wordNullDataColor());
                setWrappedText("null");
                if (valueMode == ValueMode.LITERAL_REFERENCE) {
                    setWrappedToolTipText("null");
                }
                break;
            }
            case INVALID: {
                setFont(style().wordAlternateTextFont());
                setForeground(style().wordInvalidDataColor());
                setWrappedText(hexString);
                if (valueMode == ValueMode.LITERAL_REFERENCE) {
                    setWrappedToolTipText("invalid");
                }
                break;
            }
            case OBJECT_REFERENCE: {
                setFont(style().wordDataFont());
                setForeground(style().wordValidObjectReferenceDataColor());
                setWrappedText(hexString);
                try {
                    if (valueMode == ValueMode.LITERAL_REFERENCE) {
                        setWrappedToolTipText(inspection().nameDisplay().referenceToolTipText(teleObject));
                    } else {
                        setWrappedToolTipText(inspection().nameDisplay().referenceToolTipText(teleObject));
                    }
                } catch (Throwable throwable) {
                    // If we don't catch this the views will not be updated at all.
                    teleObject = null;
                    displayMode = DisplayMode.INVALID_OBJECT_REFERENCE;
                    setToolTipText("<html><b>" + throwable + "</b><br>See log for complete stack trace.");
                    throwable.printStackTrace(Trace.stream());
                }
                break;
            }
            case OBJECT_REFERENCE_TEXT: {
                try {
                    final String labelText = inspection().nameDisplay().referenceLabelText(teleObject);
                    if (labelText != null) {
                        setWrappedText(labelText);
                        setWrappedToolTipText(inspection().nameDisplay().referenceToolTipText(teleObject));
                        setFont(style().wordAlternateTextFont());
                        setForeground(style().wordValidObjectReferenceDataColor());
                        if (valueMode == ValueMode.LITERAL_REFERENCE) {
                            setWrappedToolTipText(getToolTipText());
                        }
                        break;
                    }
                } catch (Throwable throwable) {
                    // If we don't catch this the views will not be updated at all.
                    teleObject = null;
                    displayMode = DisplayMode.INVALID_OBJECT_REFERENCE;
                    setToolTipText("<html><b>" + throwable + "</b><br>See log for complete stack trace.");
                    throwable.printStackTrace(Trace.stream());
                    break;
                }
                displayMode = DisplayMode.OBJECT_REFERENCE;
                updateText();
                break;
            }
            case STACK_LOCATION: {
                setFont(style().wordDataFont());
                setForeground(style().wordStackLocationDataColor());
                setWrappedText(hexString);
                final String threadName = inspection().nameDisplay().longName(thread);
                final long offset = value().asWord().asAddress().minus(thread.stack().memoryRegion().start()).toLong();
                final String hexOffsetString = offset >= 0 ? ("+0x" + Long.toHexString(offset)) : "0x" + Long.toHexString(offset);
                setWrappedToolTipText("Stack:  thread=" + threadName + ", offset=" + hexOffsetString);
                break;
            }
            case STACK_LOCATION_TEXT: {
                setFont(style().wordAlternateTextFont());
                setForeground(style().wordStackLocationDataColor());
                final String threadName = inspection().nameDisplay().longName(thread);
                final long offset = value().asWord().asAddress().minus(thread.stack().memoryRegion().start()).toLong();
                final String decimalOffsetString = offset >= 0 ? ("+" + offset) : Long.toString(offset);
                setWrappedText(threadName + " " + decimalOffsetString);
                setWrappedToolTipText("Stack:  thread=" + threadName + ", addr=0x" +  Long.toHexString(value().asWord().asAddress().toLong()));
                break;
            }
            case THREAD_LOCALS_BLOCK_LOCATION: {
                setFont(style().wordDataFont());
                setForeground(style().wordThreadLocalsBlockLocationDataColor());
                setWrappedText(hexString);
                final String threadName = inspection().nameDisplay().longName(thread);
                final long offset = value().asWord().asAddress().minus(thread.localsBlock().memoryRegion().start()).toLong();
                final String hexOffsetString = offset >= 0 ? ("+0x" + Long.toHexString(offset)) : "0x" + Long.toHexString(offset);
                setWrappedToolTipText("Thread locals:  thread=" + threadName + ", offset=" + hexOffsetString);
                break;
            }
            case THREAD_LOCALS_BLOCK_LOCATION_TEXT: {
                setFont(style().wordAlternateTextFont());
                setForeground(style().wordThreadLocalsBlockLocationDataColor());
                final String threadName = inspection().nameDisplay().longName(thread);
                final long offset = value().asWord().asAddress().minus(thread.localsBlock().memoryRegion().start()).toLong();
                final String decimalOffsetString = offset >= 0 ? ("+" + offset) : Long.toString(offset);
                setWrappedText(threadName + " " + decimalOffsetString);
                setWrappedToolTipText("Thread locals:  thread=" + threadName + ", addr=0x" +  Long.toHexString(value().asWord().asAddress().toLong()));
                break;
            }
            case UNCHECKED_REFERENCE: {
                setFont(style().wordDataFont());
                setForeground(style().wordUncheckedReferenceDataColor());
                setWrappedText(hexString);
                if (valueMode == ValueMode.LITERAL_REFERENCE) {
                    setWrappedToolTipText("<unchecked>");
                } else {
                    setWrappedToolTipText("Unchecked Reference");
                }
                break;
            }
            case INVALID_OBJECT_REFERENCE: {
                setFont(style().wordDataFont());
                setForeground(style().wordInvalidObjectReferenceDataColor());
                setWrappedText(hexString);
                if (valueMode == ValueMode.LITERAL_REFERENCE) {
                    setWrappedToolTipText("<invalid>");
                }
                break;
            }
            case CALL_ENTRY_POINT: {
                setFont(style().wordDataFont());
                setForeground(style().wordCallEntryPointColor());
                setWrappedText(hexString);
                setWrappedToolTipText("Code: " + inspection().nameDisplay().longName(compiledCode));
                break;
            }
            case CALL_ENTRY_POINT_TEXT: {
                setFont(style().wordAlternateTextFont());
                setForeground(style().wordCallEntryPointColor());
                setWrappedText(inspection().nameDisplay().veryShortName(compiledCode));
                setWrappedToolTipText("Code: " + inspection().nameDisplay().longName(compiledCode));
                break;
            }
            case CLASS_ACTOR_ID: {
                setFont(style().wordDataFont());
                setForeground(null);
                setWrappedText(Long.toString(value.asWord().asAddress().toLong()));
                if (teleClassActor != null) {
                    setWrappedToolTipText(inspection().nameDisplay().referenceToolTipText(teleClassActor));
                } else {
                    setToolTipText("Class{???}");
                }
                break;
            }
            case CLASS_ACTOR: {
                setWrappedText(teleClassActor.classActor().simpleName());
                setWrappedToolTipText(inspection().nameDisplay().referenceToolTipText(teleClassActor));
                break;
            }
            case CALL_RETURN_POINT: {
                setFont(style().wordDataFont());
                setForeground(style().wordCallReturnPointColor());
                setWrappedText(hexString);
                if (compiledCode != null) {
                    setWrappedToolTipText("Code: " + inspection().nameDisplay().longName(compiledCode, value.toWord().asAddress()));
                }
                break;
            }
            case CALL_RETURN_POINT_TEXT: {
                setFont(style().wordAlternateTextFont());
                setForeground(style().wordCallReturnPointColor());
                if (compiledCode != null) {
                    setWrappedText(inspection().nameDisplay().veryShortName(compiledCode, value.toWord().asAddress()));
                    setWrappedToolTipText("Code: " + inspection().nameDisplay().longName(compiledCode, value.toWord().asAddress()));
                }
                break;
            }
            case UNCHECKED_CALL_POINT: {
                setFont(style().wordDataFont());
                setForeground(style().wordUncheckedCallPointColor());
                setWrappedText(hexString);
                setWrappedToolTipText("Unchecked call entry/return point");
                break;
            }
            case FLAGS: {
                setFont(style().wordFlagsFont());
                setForeground(null);
                setWrappedText(focus().thread().registers().stateRegisterValueToString(value.toLong()));
                setWrappedToolTipText("Flags 0x" + hexString);
                break;
            }
            case DECIMAL: {
                setFont(style().decimalDataFont());
                setForeground(null);
                setWrappedText(Integer.toString(value.toInt()));
                setWrappedToolTipText("0x" + hexString);
                break;
            }
            case FLOAT: {
                setFont(style().wordAlternateTextFont());
                setForeground(null);
                setWrappedText(Float.toString(Float.intBitsToFloat((int) (value.toLong() & 0xffffffffL))) + "f");
                setWrappedToolTipText("0x" + hexString + "  (as double = " + Double.toString(Double.longBitsToDouble(value.toLong())) + ")");
                break;
            }
            case DOUBLE: {
                setFont(style().wordAlternateTextFont());
                setForeground(null);
                setWrappedText(Double.toString(Double.longBitsToDouble(value.toLong())) + "d");
                setWrappedToolTipText("0x" + hexString + "(as float = " + Float.intBitsToFloat((int) (value.toLong() & 0xffffffffL)) + ")");
                break;
            }
            case UNAVAILABLE: {
                setFont(style().wordDataFont());
                setForeground(null);
                setWrappedText(inspection().nameDisplay().unavailableDataShortText());
                setWrappedToolTipText(inspection().nameDisplay().unavailableDataLongText());
                break;
            }
        }
        if (parent != null) {
            parent.repaint();
        }
    }

    private InspectorAction getCycleDisplayTextAction() {
        DisplayMode alternateValueKind = displayMode;
        if (valueMode == ValueMode.FLAGS_REGISTER) {
            switch (displayMode) {
                case WORD: {
                    alternateValueKind = DisplayMode.FLAGS;
                    break;
                }
                case FLAGS: {
                    alternateValueKind = DisplayMode.WORD;
                    break;
                }
                default: {
                    break;
                }
            }
        }
        if (valueMode == ValueMode.FLOATING_POINT) {
            switch (alternateValueKind) {
                case WORD: {
                    alternateValueKind = DisplayMode.DOUBLE;
                    break;
                }
                case DOUBLE: {
                    alternateValueKind = DisplayMode.FLOAT;
                    break;
                }
                case FLOAT: {
                    alternateValueKind = DisplayMode.WORD;
                    break;
                }
                default: {
                    break;
                }
            }
        }
        if (valueMode == ValueMode.INTEGER_REGISTER) {
            switch (alternateValueKind) {
                case WORD: {
                    alternateValueKind = DisplayMode.DECIMAL;
                    break;
                }
                case DECIMAL: {
                    alternateValueKind = DisplayMode.WORD;
                    break;
                }
                default: {
                    break;
                }
            }
        }
        switch (alternateValueKind) {
            case OBJECT_REFERENCE: {
                alternateValueKind = DisplayMode.OBJECT_REFERENCE_TEXT;
                break;
            }
            case OBJECT_REFERENCE_TEXT: {
                alternateValueKind = DisplayMode.OBJECT_REFERENCE;
                break;
            }
            case STACK_LOCATION: {
                alternateValueKind = DisplayMode.STACK_LOCATION_TEXT;
                break;
            }
            case STACK_LOCATION_TEXT: {
                alternateValueKind = DisplayMode.STACK_LOCATION;
                break;
            }
            case THREAD_LOCALS_BLOCK_LOCATION: {
                alternateValueKind = DisplayMode.THREAD_LOCALS_BLOCK_LOCATION_TEXT;
                break;
            }
            case THREAD_LOCALS_BLOCK_LOCATION_TEXT: {
                alternateValueKind = DisplayMode.THREAD_LOCALS_BLOCK_LOCATION;
                break;
            }
            case CALL_ENTRY_POINT: {
                alternateValueKind = DisplayMode.CALL_ENTRY_POINT_TEXT;
                break;
            }
            case CALL_ENTRY_POINT_TEXT: {
                alternateValueKind = DisplayMode.CALL_ENTRY_POINT;
                break;
            }
            case CLASS_ACTOR_ID: {
                if (teleClassActor != null) {
                    alternateValueKind = DisplayMode.CLASS_ACTOR;
                }
                break;
            }
            case CLASS_ACTOR: {
                alternateValueKind = DisplayMode.CLASS_ACTOR_ID;
                break;
            }
            case CALL_RETURN_POINT: {
                alternateValueKind = DisplayMode.CALL_RETURN_POINT_TEXT;
                break;
            }
            case CALL_RETURN_POINT_TEXT: {
                alternateValueKind = DisplayMode.CALL_RETURN_POINT;
                break;
            }
            default: {
                break;
            }
        }
        if (alternateValueKind != displayMode) {
            final DisplayMode newValueKind = alternateValueKind;
            return new InspectorAction(inspection(), "Cycle alternate display text") {

                @Override
                public void procedure() {
                    Trace.line(TRACE_VALUE, "WVL: " + displayMode.toString() + "->" + newValueKind);
                    displayMode = newValueKind;
                    WordValueLabel.this.updateText();
                }
            };
        }
        return null;
    }

    private InspectorAction getInspectValueAction(Value value) {
        InspectorAction action = null;
        switch (displayMode) {
            case OBJECT_REFERENCE:
            case UNCHECKED_REFERENCE:
            case OBJECT_REFERENCE_TEXT: {
                TeleObject teleObject = null;
                try {
                    teleObject = vm().heap().findTeleObject(vm().wordToReference(value.toWord()));
                } catch (MaxVMBusyException e) {
                    // Can't read VM right now
                }
                if (teleObject != null) {
                    action = actions().inspectObject(teleObject, null);
                }
                break;
            }
            case CALL_ENTRY_POINT:
            case CALL_ENTRY_POINT_TEXT:
            case CALL_RETURN_POINT:
            case CALL_RETURN_POINT_TEXT:
            case UNCHECKED_CALL_POINT: {
                final Address address = value.toWord().asAddress();
                action = new InspectorAction(inspection(), "View Code at address") {
                    @Override
                    public void procedure() {
                        focus().setCodeLocation(vm().codeManager().createMachineCodeLocation(address, "code address from WordValueLabel"), true);
                    }
                };
                break;
            }
            case CLASS_ACTOR_ID:
            case CLASS_ACTOR: {
                final TeleClassActor teleClassActor = vm().classRegistry().findTeleClassActor(value.asWord().asAddress().toInt());
                if (teleClassActor != null) {
                    action = actions().inspectObject(teleClassActor, "Inspect ClassActor");
                }
                break;
            }
            case STACK_LOCATION:
            case STACK_LOCATION_TEXT:
            case THREAD_LOCALS_BLOCK_LOCATION:
            case THREAD_LOCALS_BLOCK_LOCATION_TEXT:
            case WORD:
            case NULL_WORD:
            case INVALID_OBJECT_REFERENCE:
            case FLAGS:
            case DECIMAL:
            case FLOAT:
            case  DOUBLE:
            case UNCHECKED_WORD:
            case INVALID:
            case UNAVAILABLE: {
                // no action
                break;
            }
        }
        return action;
    }

    private InspectorAction getInspectMemoryWordsAction(Value value) {
        InspectorAction action = null;
        if (value != VoidValue.VOID) {
            final Address address = value.toWord().asAddress();
            switch (displayMode) {
                case INVALID_OBJECT_REFERENCE:
                case UNCHECKED_REFERENCE:
                case STACK_LOCATION:
                case STACK_LOCATION_TEXT:
                case THREAD_LOCALS_BLOCK_LOCATION:
                case THREAD_LOCALS_BLOCK_LOCATION_TEXT:
                case CALL_ENTRY_POINT:
                case CALL_ENTRY_POINT_TEXT:
                case CALL_RETURN_POINT:
                case CALL_RETURN_POINT_TEXT:
                case UNCHECKED_CALL_POINT: {
                    action = actions().inspectMemoryWords(address);
                    break;
                }
                case OBJECT_REFERENCE:
                case OBJECT_REFERENCE_TEXT: {
                    if (teleObject != null) {
                        action = actions().inspectObjectMemoryWords(teleObject, "Inspect memory for " + inspection().nameDisplay().referenceLabelText(teleObject));
                    } else {
                        action = actions().inspectMemoryWords(address);
                    }
                    break;
                }
                case WORD:
                case NULL_WORD:
                case CLASS_ACTOR_ID:
                case CLASS_ACTOR:
                case FLAGS:
                case DECIMAL:
                case FLOAT:
                case DOUBLE:
                case UNCHECKED_WORD:
                case INVALID: {
                    if (vm().findMemoryRegion(address) != null) {
                        action = actions().inspectMemoryWords(address);
                    }
                    break;
                }
                case UNAVAILABLE:
                    break;
            }
        }
        return action;
    }

    private InspectorAction getShowMemoryRegionAction(Value value) {
        InspectorAction action = null;
        if (value != VoidValue.VOID) {
            final Address address = value.toWord().asAddress();
            final MaxMemoryRegion memoryRegion = vm().findMemoryRegion(address);
            if (memoryRegion != null) {
                action = actions().selectMemoryRegion(memoryRegion);
            }
        }
        return action;
    }

    @Override
    public Transferable getTransferable() {
        Transferable transferable = null;
        if (value() != VoidValue.VOID) {
            final Address address = value().toWord().asAddress();
            switch (displayMode) {
                case INVALID_OBJECT_REFERENCE:
                case UNCHECKED_REFERENCE:
                case STACK_LOCATION:
                case STACK_LOCATION_TEXT:
                case THREAD_LOCALS_BLOCK_LOCATION:
                case THREAD_LOCALS_BLOCK_LOCATION_TEXT:
                case CALL_ENTRY_POINT:
                case CALL_RETURN_POINT:
                case UNCHECKED_CALL_POINT:
                case WORD:
                case NULL_WORD:
                case CLASS_ACTOR_ID:
                case CLASS_ACTOR:
                case FLAGS:
                case DECIMAL:
                case FLOAT:
                case DOUBLE:
                case OBJECT_REFERENCE:
                case UNCHECKED_WORD:
                case INVALID: {
                    if (vm().findMemoryRegion(address) != null) {
                        transferable = new InspectorTransferable.AddressTransferable(inspection(), address);
                    }
                    break;
                }
                case OBJECT_REFERENCE_TEXT: {
                    if (teleObject != null) {
                        transferable = new InspectorTransferable.TeleObjectTransferable(inspection(), teleObject);
                    } else {
                        transferable = new InspectorTransferable.AddressTransferable(inspection(), address);
                    }
                    break;
                }
                case CALL_ENTRY_POINT_TEXT:
                case CALL_RETURN_POINT_TEXT: {
                    if (compiledCode != null) {
                        transferable = new InspectorTransferable.TeleObjectTransferable(inspection(), compiledCode.teleTargetMethod());
                    } else {
                        transferable = new InspectorTransferable.AddressTransferable(inspection(), address);
                    }
                    break;
                }
                case UNAVAILABLE:
                    break;
            }
        }
        return transferable;
    }

    private final class WordValueMenuItems extends InspectorPopupMenuItems {

        private final class MenuInspectObjectAction extends InspectorAction {

            private final InspectorAction inspectAction;

            private MenuInspectObjectAction(Value value) {
                super(inspection(), "Inspect Object (Left-Button)");
                inspectAction = getInspectValueAction(value);
                setEnabled(inspectAction != null);
            }

            @Override
            public void procedure() {
                inspectAction.perform();
            }
        }

        private final class MenuCycleDisplayAction extends InspectorAction {

            private final InspectorAction cycleAction;

            private MenuCycleDisplayAction() {
                super(inspection(), "Cycle display (Middle-Button)");
                cycleAction = getCycleDisplayTextAction();
                setEnabled(cycleAction != null);
            }

            @Override
            public void procedure() {
                cycleAction.perform();
            }
        }

        private final class MenuInspectMemoryWordsAction extends InspectorAction {

            private final InspectorAction inspectMemoryWordsAction;

            private MenuInspectMemoryWordsAction(Value value) {
                super(inspection(), "Inspect memory");
                inspectMemoryWordsAction = getInspectMemoryWordsAction(value);
                if (inspectMemoryWordsAction == null) {
                    setEnabled(false);
                } else {
                    setName(inspectMemoryWordsAction.name());
                    setEnabled(true);
                }
            }

            @Override
            public void procedure() {
                inspectMemoryWordsAction.perform();
            }
        }

        private final class MenuShowMemoryRegionAction extends InspectorAction {

            private final InspectorAction showMemoryRegionAction;

            private MenuShowMemoryRegionAction(Value value) {
                super(inspection(), "Show memory region");
                showMemoryRegionAction = getShowMemoryRegionAction(value);
                if (showMemoryRegionAction == null) {
                    setEnabled(false);
                } else {
                    setEnabled(true);
                    setName(showMemoryRegionAction.name());
                }
            }

            @Override
            public void procedure() {
                showMemoryRegionAction.perform();
            }
        }

        public WordValueMenuItems(Inspection inspection, Value value) {
            add(actions().copyValue(value, "Copy value to clipboard"));
            add(new MenuInspectObjectAction(value));
            add(new MenuCycleDisplayAction());
            add(new MenuInspectMemoryWordsAction(value));
            add(new MenuShowMemoryRegionAction(value));
        }
    }

}
