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
 * @Runs: 0 = true; 1 = true; 3 = true; 15 = true
 */
package jtt.threads;

public class Object_wait01 implements Runnable {

    static volatile int count = 0;
    static volatile boolean done;
    static final Object object = new Object();

    public static boolean test(int i) throws InterruptedException {
        count = 0;
        done = false;
        new Thread(new Object_wait01()).start();
        synchronized (object) {
            while (count < i) {
                object.wait();
            }
            done = true;
            return count >= i;
        }

    }

    public void run() {
        int i = 0;
        while (i++ < 1000000 && !done) {
            synchronized (object) {
                count++;
                object.notifyAll();
            }
        }
    }
}
