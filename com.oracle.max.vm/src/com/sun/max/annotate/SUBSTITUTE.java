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
package com.sun.max.annotate;
import java.lang.annotation.*;

/**
 * Indicates that a method declaration is intended to be a substitute for a
 * method declaration in another class. A substitute method must be declared
 * in a class annotated with {@link METHOD_SUBSTITUTIONS} as the
 * {@link METHOD_SUBSTITUTIONS#value() value} element of that annotation specifies
 * the class containing the method to be substituted (the <i>substitutee</i> class).
 * <p>
 * The method to be substituted is determined based on a name and a list of parameter types.
 * The name is specified by the {@link #value()}
 * element of this annotation. If this element is not specified, then the
 * name of the substitution method is used. The parameter types are those of the
 * substitution method.
 * <p>
 * There must never be an explicit call to a non-static method annotated with SUBSTITUTE
 * unless it is from another non-static method in the same class.
 *
 * @see METHOD_SUBSTITUTIONS
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SUBSTITUTE {

    /**
     * The name of the substitutee. If the element is {@code ""}, then the name of the substitute method is used.
     */
    String value() default "";

    /**
     * The signature of the substitutee. This is required if one of the parameter types is non-public in the
     * substitutee's package. If this element is {@code ""}, the signature is derived from the substitute.
     */
    String signatureDescriptor() default "";

    /**
     * Specifies if the substitutee must exist. This property is useful to handle differences
     * in JDK versions for private methods.
     */
    boolean optional() default false;

    /**
     * Specifies the substitution of a constructor. The substitute method can have an arbitrary name but must have a void result.
     */
    boolean constructor() default false;
}
