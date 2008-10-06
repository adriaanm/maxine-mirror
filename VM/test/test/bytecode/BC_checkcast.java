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
/*VCSID=2e72081f-ab62-4250-8350-3931f6378ff5*/
package test.bytecode;

/*
 * @Harness: java
 * @Runs: 0 = -1; 1 = -1; 4 = 4
 */
public class BC_checkcast {
    static Object _object2 = new Object();
    static Object _object3 = "";
    static Object _object4 = new BC_checkcast();

    public static int test(int arg) {
        Object obj;
        if (arg == 2) {
            obj = _object2;
        } else if (arg == 3) {
            obj = _object3;
        } else if (arg == 4) {
            obj = _object4;
        } else {
            obj = null;
        }
        final BC_checkcast bc = (BC_checkcast) obj;
        if (bc != null) {
            return arg;
        }
        return -1;
    }
}
