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

 /*
 * @Harness: java
 * @Runs: (0, 0) = true; (1, 500) = true
 */

package jtt.threads;

//Test all, mainly monitors
public class Thread_isInterrupted02 {

    private static final Object start = new Object();
    private static final Object end = new Object();
    private static int waitTime;

    public static boolean test(int i, int time) throws InterruptedException {
        waitTime = time;
        final Thread thread = new Thread();
        synchronized (thread) {
            // start the thread and wait for it
            thread.setDaemon(true); // in case the thread gets stuck
            thread.start();
            thread.wait();
        }
        synchronized (start) {
            thread.interrupt();
        }
        synchronized (end) {
            end.wait(200);
        }
        return thread.interrupted;
    }

    private static class Thread extends java.lang.Thread {
        private boolean interrupted;
        @Override
        public void run() {
            try {
                synchronized (start) {
                    synchronized (this) {
                        // signal test thread that we are running
                        notify();
                    }
                    // wait for the condition, which should be interrupted
                    if (waitTime == 0) {
                        start.wait();
                    } else {
                        start.wait(waitTime);
                    }
                }
            } catch (InterruptedException e) {
                // interrupted successfully.
                interrupted = true;
                synchronized (end) {
                    // notify the other thread we are done
                    end.notify();
                }
            }
        }
    }
}
