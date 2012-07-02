/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.vm.ext.jjvmti.agents;

import com.sun.max.config.*;
import com.sun.max.vm.*;

/**
 * This supports optional inclusion of an agent in the boot image.
 * The property {@value MAX_JJVMTI_PROPERTY} should be
 * set with a value equal to the last component of the agent package.
 */
public class Package extends BootImagePackage {

    public static final String MAX_JJVMTI_PROPERTY = "max.jjvmti.agents";

    @Override
    public boolean isPartOfMaxineVM(VMConfiguration config) {
        return System.getProperty(MAX_JJVMTI_PROPERTY) != null;
    }

    public static boolean agentIsIncluded(String name) {
        String prop = System.getProperty(MAX_JJVMTI_PROPERTY);
        return  prop != null && prop.contains(name);
    }

}
