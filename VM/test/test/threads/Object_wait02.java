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
/*VCSID=bbae84bc-bf54-4d18-a277-7b7ba59a5a77*/
 /*
  * @Harness: java
  * @Runs: 0 = true; 1=true; 2=true;
 */
package test.threads;

public class Object_wait02  implements Runnable {
    static volatile boolean _done;
    static final Object _object = new Object();
    static int _sleep;

    public static boolean test(int i) throws InterruptedException {
        _done = false;
        _sleep = i * 200;
        new Thread(new Object_wait02()).start();
        synchronized (_object) {
            while (!_done) {
                _object.wait(200);
            }
        }
        return _done;
    }

    public void run() {
        try {
            Thread.sleep(_sleep);
        } catch (InterruptedException ex) {

        }
        synchronized (_object) {
            _done = true;
            _object.notifyAll();
        }
    }

}
