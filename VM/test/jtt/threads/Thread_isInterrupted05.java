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
package jtt.threads;


/*
 * @Harness: java
 * @Runs: 0 = true
 */

// Interrupted during wait, with interrupter joining
public class Thread_isInterrupted05 {

    public static boolean test(int i)  throws InterruptedException {
        final Thread waitInterruptee = new WaitInterruptee();
        waitInterruptee.start();
        waitInterruptee.interrupt();
        waitInterruptee.join();
        return true;
    }

    static class WaitInterruptee extends Thread {
        public WaitInterruptee() {
            super("WaitInterruptee");
        }
        @Override
        public void run() {
            synchronized (this) {
                try {
                    wait();
                } catch (InterruptedException ex) {
                }
            }
        }
    }
}
