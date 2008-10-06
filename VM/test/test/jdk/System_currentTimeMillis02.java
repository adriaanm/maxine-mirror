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
/*VCSID=8812b78e-0a90-4533-ab3b-c0476213e39b*/
package test.jdk;

/*
 * @Harness: java
 * @Runs: 0 = true
 */
public class System_currentTimeMillis02 {
    public static boolean test(int arg) {
        long start = System.currentTimeMillis();
        long delta = 0;
        for (int i = 0; delta == 0 && i < 5000000; i++) {
            delta = System.currentTimeMillis() - start;
            // do nothing.
        }
        // better get at least 20 millisecond resolution.
        return delta >= 1 && delta < 20;
    }
}
