/*
 * Copyright (c) 2009 Sun Microsystems, Inc.  All rights reserved.
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
package com.sun.max.vm.classfile.constant;

import com.sun.cri.ci.*;
import com.sun.cri.ri.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.type.*;
import com.sun.max.vm.type.JavaTypeDescriptor.*;

/**
 * An {@linkplain RiType#isResolved() unresolved} type. An unresolved type is
 * derived from a {@linkplain UnresolvedType.InPool constant pool entry} or
 * from a {@linkplain TypeDescriptor type descriptor} and an associated
 * accessing class.
 *
 * @author Ben L. Titzer
 * @author Doug Simon
 */
public abstract class UnresolvedType implements RiType {

    /**
     * Gets a {@link RiType}. This method will return a {@linkplain RiType#isResolved() resolved}
     * type if possible but without triggering any class loading or resolution.
     *
     * @param typeDescriptor a type descriptor
     * @param accessingClass the context of the type lookup. If accessing class is resolved, its class loader
     *        is used to retrieve an existing resolved type. This value can be {@code null} if the caller does
     *        not care for a resolved type.
     * @return a {@link RiType} object for {@code typeDescriptor}
     */
    public static RiType toRiType(TypeDescriptor typeDescriptor, RiType accessingClass) {
        if (typeDescriptor instanceof AtomicTypeDescriptor) {
            final AtomicTypeDescriptor atom = (AtomicTypeDescriptor) typeDescriptor;
            return ClassActor.fromJava(atom.toKind().javaClass);
        } else if (typeDescriptor instanceof WordTypeDescriptor) {
            final WordTypeDescriptor word = (WordTypeDescriptor) typeDescriptor;
            if (word.javaClass instanceof Class) {
                return ClassActor.fromJava((Class) word.javaClass);
            }
        } else if (accessingClass != null) {
            if (accessingClass instanceof ClassActor) {
                ClassLoader loader = ((ClassActor) accessingClass).classLoader;
                if (typeDescriptor.isResolvableWithoutClassLoading(loader)) {
                    return typeDescriptor.resolve(loader);
                }
            }
        }
        return new ByAccessingClass(typeDescriptor, (ClassActor) accessingClass);
    }

    /**
     * An unresolved type corresponding to a constant pool entry.
     */
    public static final class InPool extends UnresolvedType {
        public final ConstantPool pool;
        public final int cpi;
        public InPool(TypeDescriptor typeDescriptor, ConstantPool pool, int cpi) {
            super(typeDescriptor);
            this.pool = pool;
            this.cpi = cpi;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof InPool) {
                InPool other = (InPool) o;
                return typeDescriptor.equals(other.typeDescriptor) &&
                       pool == other.pool && cpi == other.cpi;
            }
            return false;
        }
    }

    /**
     * An unresolved type corresponding to a type descriptor and an accessing class.
     */
    public static final class ByAccessingClass extends UnresolvedType {
        public final ClassActor accessingClass;
        public ByAccessingClass(TypeDescriptor typeDescriptor, ClassActor accessingClass) {
            super(typeDescriptor);
            this.accessingClass = accessingClass;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof ByAccessingClass) {
                ByAccessingClass other = (ByAccessingClass) o;
                return typeDescriptor.equals(other.typeDescriptor) &&
                       accessingClass.equals(other.accessingClass);
            }
            return false;
        }
    }

    /**
     * The symbol for the unresolved type.
     */
    public final TypeDescriptor typeDescriptor;

    /**
     * Creates a new unresolved type for a specified type descriptor.
     *
     * @param typeDescriptor the type's descriptor
     * @param pool the constant pool containing the unresolved type reference
     * @param cpi the index in {@code constantPool} of the unresolved type reference
     */
    public UnresolvedType(TypeDescriptor typeDescriptor) {
        this.typeDescriptor = typeDescriptor;
    }

    public String name() {
        return typeDescriptor.string;
    }

    public Class<?> javaClass() {
        throw unresolved("javaClass");
    }

    public boolean hasSubclass() {
        throw unresolved("hasSubclass()");
    }

    public boolean hasFinalizer() {
        throw unresolved("hasFinalizer()");
    }

    public boolean hasFinalizableSubclass() {
        throw unresolved("hasFinalizableSubclass()");
    }

    public boolean isInterface() {
        throw unresolved("isInterface()");
    }

    public boolean isArrayClass() {
        throw unresolved("isArrayClass()");
    }

    public boolean isInstanceClass() {
        throw unresolved("isInstanceClass()");
    }

    public int accessFlags() {
        throw unresolved("accessFlags()");
    }

    public boolean isResolved() {
        return false;
    }

    public boolean isInitialized() {
        throw unresolved("isInitialized()");
    }

    public boolean isSubtypeOf(RiType other) {
        throw unresolved("isSubtypeOf()");
    }

    public boolean isInstance(Object obj) {
        throw unresolved("isInstance()");
    }

    public RiType componentType() {
        return UnresolvedType.toRiType(typeDescriptor.componentTypeDescriptor(), null);
    }

    public RiType exactType() {
        throw unresolved("exactType()");
    }

    /**
     * Gets the compiler interface type representing an array of this compiler interface type.
     * @return the compiler interface type representing an array with elements of this compiler interface type
     */
    public RiType arrayOf() {
        return UnresolvedType.toRiType(JavaTypeDescriptor.getArrayDescriptorForDescriptor(typeDescriptor, 1), null);
    }

    public RiMethod resolveMethodImpl(RiMethod method) {
        throw unresolved("resolveMethodImpl()");
    }

    public CiKind kind() {
        return typeDescriptor.toKind().ciKind;
    }

    public CiUnresolvedException unresolved(String operation) {
        throw new CiUnresolvedException(operation + " not defined for unresolved class " + typeDescriptor.toString());
    }

    private static boolean isFinalOrPrimitive(ClassActor classActor) {
        return classActor.isFinal() || classActor.isPrimitiveClassActor();
    }

    @Override
    public final int hashCode() {
        return typeDescriptor.hashCode();
    }

    @Override
    public abstract boolean equals(Object o);

    @Override
    public String toString() {
        return name() + " [unresolved]";
    }

    public CiConstant getEncoding(RiType.Representation r) {
        throw unresolved("getEncoding()");
    }

    public CiKind getRepresentationKind(RiType.Representation r) {
        // all portions of a type are represented by objects in Maxine
        return CiKind.Object;
    }
}
