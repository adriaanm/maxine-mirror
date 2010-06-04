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
package com.sun.max.tele.object;

import java.util.*;

import com.sun.max.jdwp.vm.data.*;
import com.sun.max.jdwp.vm.proxy.*;
import com.sun.max.tele.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.classfile.*;
import com.sun.max.vm.classfile.LineNumberTable.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.reference.*;

/**
 * Canonical surrogate for an object of type {@link ClassMethodActor} in the VM.
 *
 * @author Michael Van De Vanter
 * @author Ben L. Titzer
 */
public abstract class TeleClassMethodActor extends TeleMethodActor implements MethodProvider {

    private static final TeleTargetMethod[] NO_TELE_TARGET_METHODS = new TeleTargetMethod[0];

    // Keep construction minimal for both performance and synchronization.
    protected TeleClassMethodActor(TeleVM teleVM, Reference classMethodActorReference) {
        super(teleVM, classMethodActorReference);
    }

   /**
    * @return local {@link ClassMethodActor} corresponding the VM's {@link ClassMethodActor} for this method.
    */
    public ClassMethodActor classMethodActor() {
        return (ClassMethodActor) methodActor();
    }

    @Override
    public TeleClassMethodActor getTeleClassMethodActorForObject() {
        return this;
    }

    @Override
    public TeleCodeAttribute getTeleCodeAttribute() {
        Reference codeAttributeReference = null;
        if (vm().tryLock()) {
            try {
                codeAttributeReference = vm().teleFields().ClassMethodActor_codeAttribute.readReference(reference());
            } catch (DataIOError dataIOError) {
            } finally {
                vm().unlock();
            }
        }
        if (codeAttributeReference != null) {
            return (TeleCodeAttribute) vm().makeTeleObject(codeAttributeReference);
        }
        return null;
    }

    /**
     * Cached history of compilation for this method in the tele VM.  Null means not initialized yet.
     */
    private TeleTargetMethod[] teleTargetMethodHistory = null;

    private void initialize() {
        if (teleTargetMethodHistory == null) {
            teleTargetMethodHistory = NO_TELE_TARGET_METHODS;
            readTeleMethodState();
        }
    }

    public TargetMethodAccess[] getTargetMethods() {
        return teleTargetMethodHistory;
    }

    public void refreshView() {
        initialize();
        readTeleMethodState();
    }

    /**
     * Refreshes cache of information about the compilation state of this method in the VM.
     */
    private void readTeleMethodState() {
        final Reference targetStateReference = vm().teleFields().ClassMethodActor_targetState.readReference(reference());
        if (!targetStateReference.isZero()) {
            // the method has been compiled; check the type to determine the number of times
            translateTargetState(vm().makeTeleObject(targetStateReference));
        } else {
            teleTargetMethodHistory = NO_TELE_TARGET_METHODS;
        }
    }

    private void translateTargetState(TeleObject targetState) {
        if (targetState instanceof TeleTargetMethod) {
            // the object actually is an instance of TargetMethod
            teleTargetMethodHistory = new TeleTargetMethod[] {(TeleTargetMethod) targetState};

        } else if (targetState instanceof TeleArrayObject) {
            // the object actually is an instance of TargetMethod[]
            final TeleArrayObject teleTargetMethodHistoryArray = (TeleArrayObject) targetState;
            int numberOfCompilations = teleTargetMethodHistoryArray.length();
            teleTargetMethodHistory = new TeleTargetMethod[numberOfCompilations];
            for (int i = 0; i < numberOfCompilations; i++) {
                // copy the target methods in reverse order (most recent is stored at beginning of array)
                final Reference targetMethodReference = teleTargetMethodHistoryArray.readElementValue(i).asReference();
                teleTargetMethodHistory[numberOfCompilations - i - 1] = (TeleTargetMethod) vm().makeTeleObject(targetMethodReference);
            }

        } else if (targetState.classActorForObjectType().javaClass() == Compilation.class) {
            // this is a compilation, get the previous target state from it
            Reference previousTargetStateReference = vm().teleFields().Compilation_previousTargetState.readReference(targetState.reference());
            if (!previousTargetStateReference.isZero()) {
                translateTargetState(vm().makeTeleObject(previousTargetStateReference));
            }  else {
                // this is the first compilation, no previous state
                teleTargetMethodHistory = NO_TELE_TARGET_METHODS;
            }
        }
    }

    /**
     * @return the number of times this method has been compiled
     */
    public final int numberOfCompilations() {
        initialize();
        return teleTargetMethodHistory.length;
    }
    /**
     * @return whether there is any compiled target code for this method.
     */
    public boolean hasTargetMethod() {
        return numberOfCompilations() > 0;
    }

    /**
     * @return all compilations of the method, in order, oldest first.
     */
    public Iterable<TeleTargetMethod> compilations() {
        if (hasTargetMethod()) {
            return Arrays.asList(teleTargetMethodHistory);
        }
        return Arrays.asList(NO_TELE_TARGET_METHODS);
    }

   /**
     * @return  surrogate for the most recent compilation of this method in the VM.
     */
    public TeleTargetMethod getCurrentCompilation() {
        if (hasTargetMethod()) {
            return teleTargetMethodHistory[teleTargetMethodHistory.length - 1];
        }
        return null;
    }

    /**
     * @return the specified compilation of the method in the VM, first compilation at index=0; null if no such compilation.
     */
    public TeleTargetMethod getCompilation(int index) {
        initialize();
        if (0 <= index && index < teleTargetMethodHistory.length) {
            return teleTargetMethodHistory[index];
        }
        return null;
    }

    /**
     * @param teleTargetMethod
     * @return the position of the specified compilation in the VM in the compilation history of this method, -1 if not present
     */
    public int compilationIndexOf(TeleTargetMethod teleTargetMethod) {
        initialize();
        for (int i = 0; i <= teleTargetMethodHistory.length - 1; i++) {
            if (teleTargetMethodHistory[i] == teleTargetMethod) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public LineTableEntry[] getLineTable() {

        final ClassMethodActor classMethodActor = this.classMethodActor();
        final Entry[] entries = classMethodActor.codeAttribute().lineNumberTable().entries();
        final LineTableEntry[] result = new LineTableEntry[entries.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = new LineTableEntry(entries[i].position(), entries[i].lineNumber());
        }
        return result;
    }

    @Override
    public VariableTableEntry[] getVariableTable() {

        final ClassMethodActor classMethodActor = this.classMethodActor();
        final LocalVariableTable.Entry[] entries = classMethodActor.codeAttribute().localVariableTable().entries();

        final VariableTableEntry[] result = new VariableTableEntry[entries.length];

        for (int i = 0; i < result.length; i++) {

            String signature = null;
            if (entries[i].signatureIndex() == 0) {
                signature = entries[i].descriptor(classMethodActor.codeAttribute().constantPool).toString();
            } else {
                signature = entries[i].signature(classMethodActor.codeAttribute().constantPool).toString();
            }

            // TODO: Check if generic signature can be retrieved!
            final String genericSignature = signature;

            result[i] = new VariableTableEntry(entries[i].startPosition(), entries[i].length(), entries[i].name(classMethodActor.codeAttribute().constantPool).string, entries[i].slot(),
                            signature, genericSignature);
        }

        return result;
    }

}
