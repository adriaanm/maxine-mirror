/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.config.c1xgraal;

import java.util.*;

import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.*;
import com.sun.max.config.*;
import com.sun.max.vm.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.hosted.*;

public class Package extends BootImagePackage {

    public Package() {
        super("com.oracle.max.vm.ext.c1xgraal.**",
              "com.oracle.max.graal.compiler.**",
              "com.oracle.max.graal.graph.**",
              //"com.oracle.max.graal.graphviz.**",
              "com.oracle.max.graal.nodes.**",
              "com.oracle.max.graal.snippets.**");
    }

    @Override
    public boolean isPartOfMaxineVM(VMConfiguration vmConfig) {
        return CompilationBroker.optName().contains("C1XGraal");
    }

    public static class GraalObjectMapContributor implements JavaPrototype.ObjectIdentityMapContributor {
        @Override
        public void initializeObjectIdentityMap(Map<Object, Object> objectMap) {
            objectMap.put(TTY.out(), new LogStream(Log.os));
            if (GraalOptions.PrintCFGToFile) {
                objectMap.put(CompilationPrinter.globalOut(), JavaPrototype.NULL);
            }
        }
    }

    @Override
    protected boolean includesClass(String className) {
        if (className.startsWith("com.oracle.max.graal.compiler.tests.")) {
            return false;
        }
        return super.includesClass(className);
    }
}
