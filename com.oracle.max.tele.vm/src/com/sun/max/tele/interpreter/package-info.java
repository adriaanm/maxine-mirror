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
/**
 * An interpreter for running queries on remote objects in the VM.
 * <p>
 * The interpreter runs in the inspection process, but operates on
 * {@linkplain com.sun.max.tele.reference.RemoteReference remote references} to objects in the VM for the data context
 * of the interpretation.
 *
 * Derived from the JavaInJava interpreter by Antero Taivalsaari. See Sun Labs tech report TR-98-64:
 * http://research.sun.com/techrep/1998/abstract-64.html
 */
package com.sun.max.tele.interpreter;
