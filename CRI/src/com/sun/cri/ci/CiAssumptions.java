/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.cri.ci;

import com.sun.cri.ri.*;

/**
 * Class for recording optimistic assumptions made during compilation.
 * @author Thomas Wuerthinger
 *
 */
public final class CiAssumptions {

    public abstract static class Assumption {
    }
    
    public final static class ConcreteSubtype extends Assumption {
        /**
         * Type the assumption is made about.
         */
        public final RiType context;
        /**
         * Assumed unique concrete sub-type of the context type.
         */
        public final RiType subtype;
        
        private ConcreteSubtype(RiType context, RiType subtype) {
            this.context = context;
            this.subtype = subtype;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ConcreteSubtype) {
                ConcreteSubtype other = (ConcreteSubtype)obj;
                return other.context == context && other.subtype == subtype;
            }
            return false;
        }
    }
    
    public final static class ConcreteMethod extends Assumption {
        public final RiMethod context;
        public final RiMethod method;
        
        private ConcreteMethod(RiMethod context, RiMethod method) {
            this.context = context;
            this.method = method;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ConcreteMethod) {
                ConcreteMethod other = (ConcreteMethod)obj;
                return other.context == context && other.method == method;
            }
            return false;
        }
    }
    
    private Assumption[] list;
    private int count;
    
    public int count() {
        return count;
    }
    
    public void recordConcreteSubtype(RiType context, RiType subtype) {
        record(new ConcreteSubtype(context, subtype));
    }
    
    public void recordConcreteMethod(RiMethod context, RiMethod method) {
        record(new ConcreteMethod(context, method));
    }

    private void record(Assumption assumption) {
        if (list == null) {
            list = new Assumption[4];
        } else {
            for (int i = 0; i < count; ++i) {
                if (assumption.equals(list[i])) {
                    return;
                }
            }
        }
        if (list.length == count) {
            Assumption[] newList = new Assumption[list.length*2];
            for (int i = 0; i < list.length; ++i) {
                newList[i] = list[i];
            }
            list = newList;
        }
        list[count] = assumption;
        count++;
    }
 
    
}
