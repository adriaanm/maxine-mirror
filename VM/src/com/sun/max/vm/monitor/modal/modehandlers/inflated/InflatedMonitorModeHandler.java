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
package com.sun.max.vm.monitor.modal.modehandlers.inflated;

import com.sun.max.annotate.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.monitor.modal.modehandlers.*;
import com.sun.max.vm.monitor.modal.modehandlers.lightweight.biased.*;
import com.sun.max.vm.monitor.modal.modehandlers.lightweight.thin.*;
import com.sun.max.vm.monitor.modal.sync.*;
import com.sun.max.vm.monitor.modal.sync.JavaMonitorManager.*;
import com.sun.max.vm.object.*;
import com.sun.max.vm.object.host.*;
import com.sun.max.vm.thread.*;

/**
 * Inflated mode handler for ModalMonitorSchemes.
 *
 * @author Simon Wilkinson
 */
public abstract class InflatedMonitorModeHandler extends AbstractModeHandler {

    protected InflatedMonitorModeHandler(UnboundMiscWordWriter unboundMiscWordWriter) {
        super(null);
        JavaMonitorManager.registerMonitorUnbinder(unboundMiscWordWriter);
    }

    /**
     * Returns an InflatedMonitorModeHandler with the required interface for fast-path entry from a MonitorScheme.
     */
    public static MonitorSchemeEntry asFastPath() {
        return new InflatedMonitorModeHandler.FastPath();
    }

    /**
     * Returns an InflatedMonitorModeHandler with the required interface to act as delegate for biased locking.
     */
    public static ModeDelegate asBiasedLockDelegate() {
        return new InflatedMonitorModeHandler.BiasedLockDelegate();
    }

    /**
     * Returns an InflatedMonitorModeHandler with the required interface to act as delegate for thin locking.
     */
    public static ModeDelegate asThinLockDelegate() {
        return new InflatedMonitorModeHandler.ThinLockDelegate();
    }

    @INLINE
    protected final InflatedMonitorLockword64 readMiscAndProtectBinding(Object object) {
        final InflatedMonitorLockword64 lockword = InflatedMonitorLockword64.from(ObjectAccess.readMisc(object));
        if (lockword.isBound()) {
            JavaMonitorManager.protectBinding(lockword.getBoundMonitor());
        }
        return lockword;
    }

    protected int makeBoundHashCode(Object object, InflatedMonitorLockword64 lockword) {
        final JavaMonitor monitor = lockword.getBoundMonitor();
        final HashableLockword64 swappedLockword = HashableLockword64.from(monitor.displacedMisc());
        int hashcode = swappedLockword.getHashcode();
        if (hashcode == 0) {
            hashcode = monitorScheme().createHashCode(object);
            final HashableLockword64 newSwappedLockword = swappedLockword.setHashcode(hashcode);
            final HashableLockword64 answer = HashableLockword64.from(monitor.compareAndSwapDisplacedMisc(swappedLockword, newSwappedLockword));
            if (!answer.equals(swappedLockword)) {
                hashcode = answer.getHashcode();
            }
        }
        return hashcode;
    }

    protected void monitorExit(Object object, InflatedMonitorLockword64 lockword) {
        if (!lockword.isBound()) {
            throw new IllegalMonitorStateException();
        }
        final JavaMonitor monitor = lockword.getBoundMonitor();
        monitor.monitorExit();
    }

    protected void monitorNotify(Object object, boolean all, InflatedMonitorLockword64 lockword) {
        if (!lockword.isBound()) {
            throw new IllegalMonitorStateException();
        }
        final JavaMonitor monitor = lockword.getBoundMonitor();
        monitor.monitorNotify(all);
    }

    protected void monitorWait(Object object, long timeout, InflatedMonitorLockword64 lockword) throws InterruptedException {
        if (!lockword.isBound()) {
            throw new IllegalMonitorStateException();
        }
        final JavaMonitor monitor = lockword.getBoundMonitor();
        monitor.monitorWait(timeout);
    }

    protected boolean threadHoldsMonitor(InflatedMonitorLockword64 lockword, VmThread thread) {
        return lockword.isBound() && lockword.getBoundMonitor().isOwnedBy(thread);
    }

    protected void afterGarbageCollection() {
        JavaMonitorManager.afterGarbageCollection();
    }

    protected void beforeGarbageCollection() {
        JavaMonitorManager.beforeGarbageCollection();
    }

    /**
     * Implements fast-path monitor entry and exit for an InflatedMonitorModeHandler.
     */
    private static final class FastPath extends InflatedMonitorModeHandler implements MonitorSchemeEntry {

        protected FastPath() {
            super(new UnboundMiscWordWriter() {
                public void writeUnboundMiscWord(Object object, Word preBindingMiscWord) {
                    ObjectAccess.writeMisc(object, preBindingMiscWord);
                }
            });
        }

        @Override
        public void initialize(MaxineVM.Phase phase) {
            if (MaxineVM.isPrototyping()) {
                JavaMonitorManager.setRequireProxyAcquirableMonitors(false);
            }
        }

        public Word createMisc(Object object) {
            return InflatedMonitorLockword64.unboundFromHashcode(monitorScheme().createHashCode(object));
        }

        public int makeHashCode(Object object) {
            nullCheck(object);
            if (MaxineVM.isPrototyping()) {
                return monitorScheme().createHashCode(object);
            }
            final InflatedMonitorLockword64 lockword = readMiscAndProtectBinding(object);
            if (lockword.isBound()) {
                return makeBoundHashCode(object, lockword);
            }
            int hashcode = lockword.getHashcode();
            if (hashcode == 0) {
                hashcode = monitorScheme().createHashCode(object);
                final HashableLockword64 newLockword = lockword.setHashcode(hashcode);
                final Word answer = ObjectAccess.compareAndSwapMisc(object, lockword, newLockword);
                if (!answer.equals(lockword)) {
                    return makeHashCode(object);
                }
            }
            return hashcode;
        }

        public void monitorEnter(Object object) {
            nullCheck(object);
            if (MaxineVM.isPrototyping()) {
                HostMonitor.enter(object);
                return;
            }
            InflatedMonitorLockword64 lockword = readMiscAndProtectBinding(object);
            JavaMonitor monitor = null;
            while (true) {
                if (lockword.isBound()) {
                    if (monitor != null) {
                        monitor.monitorExit();
                        JavaMonitorManager.unbindMonitor(monitor);
                    }
                    final JavaMonitor boundMonitor = lockword.getBoundMonitor();
                    boundMonitor.monitorEnter();
                    return;
                } else if (monitor == null) {
                    monitor = JavaMonitorManager.bindMonitor(object);
                    monitor.monitorEnter();
                }
                monitor.setDisplacedMisc(lockword);
                final InflatedMonitorLockword64 newLockword = InflatedMonitorLockword64.boundFromMonitor(monitor);
                final Word answer = ObjectAccess.compareAndSwapMisc(object, lockword, newLockword);
                if (answer.equals(lockword)) {
                    return;
                }
                // Another thread installed a hashcode or got the monitor.
                // Try again.
                lockword = InflatedMonitorLockword64.from(answer);
            }
        }

        public void monitorExit(Object object) {
            nullCheck(object);
            if (MaxineVM.isPrototyping()) {
                HostMonitor.exit(object);
                return;
            }
            final InflatedMonitorLockword64 lockword = InflatedMonitorLockword64.from(ObjectAccess.readMisc(object));
            super.monitorExit(object, lockword);
        }

        public void monitorNotify(Object object, boolean all) {
            nullCheck(object);
            if (MaxineVM.isPrototyping()) {
                HostMonitor.notify(object);
                return;
            }
            final InflatedMonitorLockword64 lockword = InflatedMonitorLockword64.from(ObjectAccess.readMisc(object));
            super.monitorNotify(object, all, lockword);
        }

        public void monitorWait(Object object, long timeout) throws InterruptedException {
            nullCheck(object);
            if (MaxineVM.isPrototyping()) {
                HostMonitor.wait(object, timeout);
                return;
            }
            final InflatedMonitorLockword64 lockword = readMiscAndProtectBinding(object);
            super.monitorWait(object, timeout, lockword);
        }

        public boolean threadHoldsMonitor(Object object, VmThread thread) {
            nullCheck(object);
            final InflatedMonitorLockword64 lockword = readMiscAndProtectBinding(object);
            return super.threadHoldsMonitor(lockword, thread);
        }

        @Override
        public void afterGarbageCollection() {
            super.afterGarbageCollection();
        }

        @Override
        public void beforeGarbageCollection() {
            super.beforeGarbageCollection();
        }
    }

    /**
     * Implements default delegate entry points for InflatedMonitorModeHandler.
     */
    private abstract static class Delegate extends InflatedMonitorModeHandler implements ModeDelegate {

        private Delegate(UnboundMiscWordWriter unboundMiscWordWriter) {
            super(unboundMiscWordWriter);
        }

        public int delegateMakeHashcode(Object object, ModalLockword64 lockword) {
            final InflatedMonitorLockword64 inflatedLockword = readMiscAndProtectBinding(object);
            if (inflatedLockword.isBound()) {
                return makeBoundHashCode(object, inflatedLockword);
            }
            return 0;
        }

        public boolean delegateMonitorEnter(Object object, ModalLockword64 lockword, int lockwordThreadID) {
            final InflatedMonitorLockword64 inflatedLockword = readMiscAndProtectBinding(object);
            if (!inflatedLockword.isBound()) {
                return false;
            }
            final JavaMonitor monitor = inflatedLockword.getBoundMonitor();
            monitor.monitorEnter();
            return true;
        }

        public void delegateMonitorExit(Object object, ModalLockword64 lockword) {
            monitorExit(object, InflatedMonitorLockword64.from(lockword));
        }

        public void delegateMonitorNotify(Object object, boolean all, ModalLockword64 lockword) {
            monitorNotify(object, all, InflatedMonitorLockword64.from(lockword));
        }

        public void delegateMonitorWait(Object object, long timeout, ModalLockword64 lockword) throws InterruptedException {
            final InflatedMonitorLockword64 inflatedLockword = InflatedMonitorLockword64.from(lockword);
            if (inflatedLockword.isBound()) {
                JavaMonitorManager.protectBinding(inflatedLockword.getBoundMonitor());
            }
            monitorWait(object, timeout, inflatedLockword);
        }

        public DelegatedThreadHoldsMonitorResult delegateThreadHoldsMonitor(Object object, ModalLockword64 lockword, VmThread thread, int lockwordThreadID) {
            final InflatedMonitorLockword64 inflatedLockword = readMiscAndProtectBinding(object);
            if (!inflatedLockword.isBound()) {
                return DelegatedThreadHoldsMonitorResult.NOT_THIS_MODE;
            }
            return super.threadHoldsMonitor(inflatedLockword, thread) ? DelegatedThreadHoldsMonitorResult.TRUE : DelegatedThreadHoldsMonitorResult.FALSE;
        }

        public void delegateAfterGarbageCollection() {
            afterGarbageCollection();
        }

        public void delegateBeforeGarbageCollection() {
            beforeGarbageCollection();
        }
    }

    /**
     * Implements the required interface to allow an InflatedMonitorModeHandler to acts as a delegate to thin locking.
     */
    static final class ThinLockDelegate extends Delegate implements ModeDelegate {

        ThinLockDelegate() {
            super(new UnboundMiscWordWriter() {
                public void writeUnboundMiscWord(Object object, Word preBindingMiscWord) {
                    ObjectAccess.writeMisc(object, ThinLockword64.from(preBindingMiscWord).asUnlocked());
                }
            });
        }

        @Override
        public void initialize(MaxineVM.Phase phase) {
            if (MaxineVM.isPrototyping()) {
                JavaMonitorManager.setRequireProxyAcquirableMonitors(true);
            }
        }

        public ModalLockword64 prepareModalLockword(Object object, ModalLockword64 currentLockword) {
            final ThinLockword64 thinLockword = ThinLockword64.from(currentLockword);
            // FIXME: We have to map the thinLockword thread ID back to the VmThread. This is expensive -
            // and has to lock VMThread.ACTIVE. Maybe we should just keep threadID's in Monitor's too?
            // FIXME: What if the VmThread is null?
            final JavaMonitor monitor = JavaMonitorManager.bindMonitor(object);
            monitor.setDisplacedMisc(thinLockword.asUnlocked());
            if (!thinLockword.equals(thinLockword.asUnlocked())) {
                final VmThread owner = VmThreadMap.ACTIVE.getVmThreadForID(decodeLockwordThreadID(thinLockword.getLockOwnerID()));
                monitor.monitorPrivateAcquire(owner, thinLockword.getRecursionCount());
            } else {
                monitor.monitorPrivateRelease();
            }
            return InflatedMonitorLockword64.boundFromMonitor(monitor);
        }

        public ModalLockword64 reprepareModalLockword(ModalLockword64 preparedLockword, ModalLockword64 currentLockword) {
            final ThinLockword64 thinLockword =  ThinLockword64.from(currentLockword);
            final JavaMonitor monitor = InflatedMonitorLockword64.from(preparedLockword).getBoundMonitor();
            monitor.setDisplacedMisc(thinLockword);
            if (!thinLockword.equals(thinLockword.asUnlocked())) {
                final VmThread owner = VmThreadMap.ACTIVE.getVmThreadForID(decodeLockwordThreadID(thinLockword.getLockOwnerID()));
                monitor.monitorPrivateAcquire(owner, thinLockword.getRecursionCount());
            } else {
                monitor.monitorPrivateRelease();
            }
            return preparedLockword;
        }

        public void cancelPreparedModalLockword(ModalLockword64 preparedLockword) {
            JavaMonitorManager.unbindMonitor(InflatedMonitorLockword64.from(preparedLockword).getBoundMonitor());
        }
    }

    /**
     * Implements the required interface to allow an InflatedMonitorModeHandler to acts as a delegate to biased locking.
     */
    private static final class BiasedLockDelegate extends Delegate {

        protected BiasedLockDelegate() {
            super(new UnboundMiscWordWriter() {
                public void writeUnboundMiscWord(Object object, Word preBindingMiscWord) {
                    ObjectAccess.writeMisc(object, BiasedLockword64.from(preBindingMiscWord).asAnonBiased());
                }
            });
        }

        @Override
        public void initialize(MaxineVM.Phase phase) {
            if (MaxineVM.isPrototyping()) {
                JavaMonitorManager.setRequireProxyAcquirableMonitors(true);
            }
        }

        public ModalLockword64 prepareModalLockword(Object object, ModalLockword64 currentLockword) {
            final BiasedLockword64 biasedLockword = BiasedLockword64.from(currentLockword);
            final JavaMonitor monitor = JavaMonitorManager.bindMonitor(object);
            final InflatedMonitorLockword64 newLockword = InflatedMonitorLockword64.boundFromMonitor(monitor);
            monitor.setDisplacedMisc(biasedLockword);
            if (!biasedLockword.countUnderflow()) {
                // It does not matter if the threadID has been recycled after the bias request.
                final VmThread biasOwner = VmThreadMap.ACTIVE.getVmThreadForID(decodeLockwordThreadID(biasedLockword.getBiasOwnerID()));
                monitor.monitorPrivateAcquire(biasOwner, biasedLockword.getRecursionCount());
            } else {
                monitor.monitorPrivateRelease();
            }
            return newLockword;
        }

        public ModalLockword64 reprepareModalLockword(ModalLockword64 preparedLockword, ModalLockword64 currentLockword) {
            final BiasedLockword64 biasedLockword =  BiasedLockword64.from(currentLockword);
            final JavaMonitor monitor = InflatedMonitorLockword64.from(preparedLockword).getBoundMonitor();
            monitor.setDisplacedMisc(biasedLockword);
            if (!biasedLockword.countUnderflow()) {
                final VmThread owner = VmThreadMap.ACTIVE.getVmThreadForID(decodeLockwordThreadID(biasedLockword.getBiasOwnerID()));
                monitor.monitorPrivateAcquire(owner, biasedLockword.getRecursionCount());
            } else {
                monitor.monitorPrivateRelease();
            }
            return preparedLockword;
        }

        public void cancelPreparedModalLockword(ModalLockword64 preparedLockword) {
            JavaMonitorManager.unbindMonitor(InflatedMonitorLockword64.from(preparedLockword).getBoundMonitor());
        }
    }
}
