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
package com.sun.max.ins.value;

import java.awt.event.*;

import com.sun.max.ins.*;
import com.sun.max.ins.gui.*;
import com.sun.max.tele.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.monitor.*;
import com.sun.max.vm.monitor.modal.modehandlers.*;
import com.sun.max.vm.monitor.modal.modehandlers.inflated.*;
import com.sun.max.vm.monitor.modal.modehandlers.lightweight.biased.*;
import com.sun.max.vm.monitor.modal.modehandlers.lightweight.thin.*;
import com.sun.max.vm.monitor.modal.schemes.*;
import com.sun.max.vm.monitor.modal.schemes.ModalMonitorScheme.ModalLockwordDecoder;
import com.sun.max.vm.value.*;

/**
 * A label specialized for displaying the contents of the "misc." word value in
 * the header of an object in the VM.
 */
public final class MiscWordLabel extends ValueLabel {

    private final MaxObject object;
    private MaxObject javaMonitor = null;

    public MiscWordLabel(Inspection inspection, MaxObject object) {
        super(inspection, null);
        this.object = object;
        addMouseListener(new InspectorMouseClickAdapter(inspection()) {
            @Override
            public void procedure(final MouseEvent mouseEvent) {
                switch (inspection().gui().getButton(mouseEvent)) {
                    case MouseEvent.BUTTON1: {
                        final InspectorAction inspectAction = getInspectJavaMonitorAction();
                        if (inspectAction.isEnabled()) {
                            inspectAction.perform();
                        }
                        break;
                    }
                    case MouseEvent.BUTTON2: {
                        // No toggle display action yet
                        break;
                    }
                    case MouseEvent.BUTTON3: {
                        final InspectorPopupMenu menu = new InspectorPopupMenu("Header Misc. Word");
                        menu.add(getCopyWordAction());
                        menu.add(getInspectJavaMonitorAction());
                        menu.show(mouseEvent.getComponent(), mouseEvent.getX(), mouseEvent.getY());
                    }
                }
            }
        });
        initializeValue();
        redisplay();
    }

    @Override
    public Value fetchValue() {
        return new WordValue(object.readMiscWord());
    }

    public void redisplay() {
        setFont(preference().style().hexDataFont());
        updateText();
    }

    private static ModalLockwordDecoder modalLockwordDecoder;

    @Override
    public void updateText() {
        if (vm().memoryIO().isZappedValue(value())) {
            setText(inspection().nameDisplay().zappedDataShortText());
            setToolTipText(inspection().nameDisplay().zappedDataLongText());
        } else {
            final Word miscWord = value().asWord();
            final String hexString = miscWord.toHexString();
            final MonitorScheme monitorScheme = vm().bootImage().vmConfiguration.monitorScheme();
            if (monitorScheme instanceof ModalMonitorScheme) {
                javaMonitor = null;
                if (modalLockwordDecoder == null) {
                    final ModalMonitorScheme modalMonitorScheme = (ModalMonitorScheme) monitorScheme;
                    modalLockwordDecoder = modalMonitorScheme.getModalLockwordDecoder();
                }
                final ModalLockword64 modalLockword = ModalLockword64.from(miscWord);
                if (modalLockwordDecoder.isLockwordInMode(modalLockword, BiasedLockword64.class)) {
                    final BiasedLockword64 biasedLockword = BiasedLockword64.from(modalLockword);
                    final int hashcode = biasedLockword.getHashcode();
                    final int recursion = biasedLockword.getRecursionCount();
                    final int ownerThreadID = BiasedLockModeHandler.decodeBiasOwnerThreadID(biasedLockword);
                    final MaxThread thread = vm().threadManager().getThread(ownerThreadID);
                    final String threadName = inspection().nameDisplay().longName(thread);
                    final int biasEpoch = biasedLockword.getEpoch().toInt();
                    setText("BiasedLock(" + recursion + "): " + hexString);
                    setToolTipText("BiasedLockword64:  recursion=" + recursion + ";  thread=" + threadName + ";  biasEpoch=" + biasEpoch + "; hashcode=" + hashcode);
                } else if (modalLockwordDecoder.isLockwordInMode(modalLockword, ThinLockword64.class)) {
                    final ThinLockword64 thinLockword = ThinLockword64.from(modalLockword);
                    final int hashcode = thinLockword.getHashcode();
                    final int recursionCount = thinLockword.getRecursionCount();
                    final int ownerThreadID = ThinLockModeHandler.decodeLockOwnerThreadID(thinLockword);
                    final MaxThread thread = vm().threadManager().getThread(ownerThreadID);
                    final String threadName = inspection().nameDisplay().longName(thread);
                    setText("ThinLock(" + recursionCount + "): " + hexString);
                    setToolTipText("ThinLockword64:  recursion=" + recursionCount + ";  thread=" + threadName + "; hashcode=" + hashcode);
                } else if (modalLockwordDecoder.isLockwordInMode(modalLockword, InflatedMonitorLockword64.class)) {
                    setText("InflatedMonitorLock: " + hexString);
                    final InflatedMonitorLockword64 inflatedLockword = InflatedMonitorLockword64.from(modalLockword);
                    final boolean isBound = inflatedLockword.isBound();
                    if (isBound) {
                        // JavaMonitor is a proper object, not just a Word.
                        try {
                            javaMonitor = vm().objects().findObjectAt(inflatedLockword.getBoundMonitorReferenceAsWord().asAddress());
                        } catch (MaxVMBusyException e) {
                        }
                        if (javaMonitor == null) {
                            setToolTipText("InflatedMonitorLockword64:  bound, monitor=" + inspection().nameDisplay().unavailableDataLongText());
                        } else {
                            final String name = javaMonitor.classActorForObjectType().qualifiedName();
                            setToolTipText("InflatedMonitorLockword64:  bound, monitor=" + name);
                        }
                    } else {
                        // Field access
                        final int hashcode = inflatedLockword.getHashcode();
                        setToolTipText("InflatedMonitorLockword64:  unbound, hashcode=" + hashcode);
                    }
                } else {
                    setText(hexString);
                    setToolTipText("Non-decodable ModalLockword64");
                }
            } else {
                setText(hexString);
                setToolTipText(null);
            }
        }
    }

    private InspectorAction getCopyWordAction() {
        return actions().copyWord(value().asWord(), "Copy word to clipboard");
    }

    private InspectorAction getInspectJavaMonitorAction() {
        final InspectorAction action = views().objects().makeViewAction(javaMonitor, "View JavaMonitor (left-button)");
        action.setEnabled(javaMonitor != null);
        return action;
    }
}
