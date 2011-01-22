/*
 * Copyright (c) 2010, 2010, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.annotate;
import java.lang.annotation.*;
import java.util.*;

import com.sun.max.lang.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.classfile.constant.*;
import com.sun.max.vm.runtime.*;

/**
 * Mechanism for referring to fields, methods and constructors otherwise inaccessible due to Java language access
 * control rules. This enables VM code to directly access a private field or invoke a private method in a JDK class
 * without using reflection. Aliases avoid the boxing/unboxing required by reflection and they type check an aliased
 * field access or method invocation statically.
 *
 * The idiom for using ALIAS is somewhat related to the {@link @SUBSTITUTE} annotation, but reversed and are often used
 * in combination. In both cases a separate class is used to declare the aliased or substituted methods. In the
 * substitution case occurrences of {@code this} actually refer to the instance of the class being substituted. In the
 * aliased case we pretend that the class declaring the aliased method is an instance of the aliasee in order to access
 * its fields or invoke its methods.
 *
 * For example, assume we want to create an instance of a class {@code Foo} which has a private constructor
 * that takes one {@code int} argument. To to this we declare a new class {@code FooAlias} that contains the following:
 *
 * <code>
 * final class FooAlias {
 *     @ALIAS(declaringClass = Foo.class, name="<init>");
 *     private native void init(int arg);
 *
 *     @INTRINSIC(UNSAFE_CAST)
 *     static native FooAlias asThis(Foo foo);
 *
 *     public static Foo createFoo(int arg) {
 *         final Foo foo = (Foo) Heap.createTuple(ClassActor.fromJava(Foo,.class).dynamicHub());
 *         FooAlias thisFoo = asThis(foo);
 *         thisFoo.init(arg);
 *         return foo;
 *     }
 * }
 * </code>
 *
 * The idiomatic use of {@code native} serves merely to avoid providing a body for the annotated methods.
 *
 * The code for field access is similar; declare an {@code @ALIAS} annotated field in the class with the same
 * name as the field in the aliasee and then use {@code thisFoo.field}.
 *
 * @author Doug Simon
 * @author Mick Jordan
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface ALIAS {
    String name() default "";

    /**
     * Specifies the class in which the aliased method is declared.
     * If the default value is specified for this element, then a non-default
     * value must be given for the {@link #declaringClassName()} element.
     */
    Class declaringClass() default ALIAS.class;

    /**
     * Specifies the class in which the aliased method is declared.
     * This method is provided for cases where the declaring class
     * is not accessible (according to Java language access control rules)
     * in the scope of the alias method.
     *
     * If the default value is specified for this element, then a non-default
     * value must be given for the {@link #declaringClassName()} element.
     */
    String declaringClassName() default "";

    /**
     * Specifies the suffix of the declaring class name when it is an inner class.
     */
    String innerClass() default "";

    /**
     * Specifies if the aliased target must exist. This property is useful to handle differences
     * in JDK versions for private methods.
     */
    boolean optional() default false;

    @HOSTED_ONLY
    public static class Static {

        /**
         * Map from method alias to aliased method.
         */
        private static final HashMap<MethodActor, MethodActor> aliasedMethods = new HashMap<MethodActor, MethodActor>();

        /**
         * Map from aliased field to aliased field.
         */
        private static final HashMap<FieldActor, FieldActor> aliasedFields = new HashMap<FieldActor, FieldActor>();

        /**
         * Gets the field aliased by a given field, if any.
         *
         * @param field a field that may be an alias (i.e. annotated with {@link ALIAS})
         * @return the field aliased by {@code field} or {@code null} if it is not an alias
         */
        public static synchronized FieldActor aliasedField(FieldActor field) {
            ALIAS alias = field.getAnnotation(ALIAS.class);
            if (alias != null) {
                FieldActor aliasedField = aliasedFields.get(field);
                if (aliasedField == null) {
                    Class holder = declaringClass(alias);
                    String name = alias.name();
                    if (name.isEmpty()) {
                        name = field.name();
                    }
                    aliasedField = ClassActor.fromJava(holder).findLocalFieldActor(SymbolTable.makeSymbol(name), field.descriptor());
                    if (aliasedField == null) {
                        if (alias.optional()) {
                            return field;
                        }
                        throw FatalError.unexpected("Could not find target for alias " + field + " in " + holder.getName());
                    }
                    assert aliasedField.isStatic() == field.isStatic() : "Alias " + field + " must be static if " + aliasedField + " is";
                    aliasedFields.put(field, aliasedField);
                }
                return aliasedField;
            }
            return null;
        }

        /**
         * Gets the method (or constructor) aliased by a given method (or constructor), if any.
         *
         * @param method a method that may be an alias (i.e. annotated with {@link ALIAS})
         * @return the method aliased by {@code method} or {@code null} if it is not an alias
         */
        public static synchronized MethodActor resolveAlias(MethodActor method) {
            ALIAS alias = method.getAnnotation(ALIAS.class);
            if (alias != null) {
                MethodActor aliasedMethod = aliasedMethods.get(method);
                if (aliasedMethod == null) {
                    Class holder = declaringClass(alias);
                    String name = alias.name();
                    if (name.isEmpty()) {
                        name = method.name();
                    }

                    aliasedMethod = ClassActor.fromJava(holder).findLocalMethodActor(SymbolTable.makeSymbol(name), method.descriptor());
                    if (aliasedMethod == null) {
                        if (alias.optional()) {
                            return method;
                        }
                        throw FatalError.unexpected("Could not find target for alias " + method + " in " + holder.getName());
                    }
                    assert aliasedMethod.isStatic() == method.isStatic() : "Alias " + method + " must be static if " + aliasedMethod + " is";
                    aliasedMethods.put(method, aliasedMethod);
                }
                return aliasedMethod;
            }
            return null;
        }

        public static synchronized boolean isAliased(FieldActor field) {
            return aliasedFields.containsValue(field);
        }

        public static synchronized boolean isAliased(MethodActor method) {
            return aliasedMethods.containsValue(method);
        }

        private static Class declaringClass(ALIAS alias) {
            Class holder;
            if (alias.declaringClass() == ALIAS.class) {
                assert !alias.declaringClassName().isEmpty();
                holder = Classes.forName(alias.declaringClassName(), false, ALIAS.class.getClassLoader());
            } else {
                assert alias.declaringClassName().isEmpty();
                holder = alias.declaringClass();
            }
            if (!alias.innerClass().isEmpty()) {
                holder = Classes.getInnerClass(holder, alias.innerClass());
            }
            return holder;
        }
    }
}
