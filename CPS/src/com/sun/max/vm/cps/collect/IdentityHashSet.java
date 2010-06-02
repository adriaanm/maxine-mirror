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
package com.sun.max.vm.cps.collect;

import java.util.*;

/**
 * An identity hash set backed by java.util.IdentityHashMap.
 *
 * @author Hiroshi Yamauchi
 * @author Bernd Mathiske
 * @author Michael Van De Vanter
 */
public class IdentityHashSet<T> extends AbstractSet<T> implements Cloneable {

    private final Map<T, T> internalMap;

    public IdentityHashSet() {
        internalMap = new IdentityHashMap<T, T>();
    }

    /**
     * Adds a specified element to this set.
     *
     * @param element the element to add
     * @return true if {@code element} was already in this set
     */
    @Override
    public boolean add(T element) {
        return internalMap.put(element, element) != null;
    }

    /**
     * Adds all the elements in a given Iterable to this set. The addition is always done by calling
     * {@link #add(Object)}.
     *
     * @param iterable the collection of elements to add
     */
    public final void addAll(Iterable<? extends T> iterable) {
        for (T element : iterable) {
            add(element);
        }
    }

    /**
     * Adds all the elements in a given array to this set. The addition is always done by calling
     * {@link #add(Object)}.
     *
     * @param elements the collection of elements to add
     */
    public final void addAll(T[] elements) {
        for (T element : elements) {
            add(element);
        }
    }

    @Override
    public boolean isEmpty() {
        return internalMap.isEmpty();
    }

    @Override
    public void clear() {
        internalMap.clear();
    }

    /**
     * Remove an element from the set.
     *
     * @param element the element to remove.
     */
    public void remove(T element) {
        internalMap.remove(element);
    }

    /**
     * Removes all the elements in a given Iterable from this set. The removal is always done by calling
     * {@link #remove(Object)}.
     *
     * @param iterable the collection of elements to remove
     */
    public final void removeAll(Iterable<? extends T> iterable) {
        for (T element : iterable) {
            remove(element);
        }
    }

    /**
     * Removes all the elements in a given array from this set. The removal is always done by calling
     * {@link #remove(Object)}.
     *
     * @param elements the collection of elements to remove
     */
    public final void removeAll(T[] elements) {
        for (T element : elements) {
            remove(element);
        }
    }

    @Override
    public Iterator<T> iterator() {
        return internalMap.keySet().iterator();
    }

    @Override
    public IdentityHashSet<T> clone() {
        final IdentityHashSet<T> copy = new IdentityHashSet<T>();
        copy.addAll(this);
        return copy;
    }

    @Override
    public boolean contains(Object element) {
        return internalMap.containsKey(element);
    }

    @Override
    public int size() {
        return internalMap.size();
    }

    public final IdentityHashSet<T> union(IdentityHashSet<T> other) {
        for (T element : other) {
            add(element);
        }
        return this;
    }

    public boolean isSuperSetOf(IdentityHashSet<T> other) {
        if (size() < other.size()) {
            return false;
        }
        for (T element : other) {
            if (!contains(element)) {
                return false;
            }
        }
        return true;
    }

    public boolean isStrictSuperSetOf(IdentityHashSet<T> other) {
        if (size() <= other.size()) {
            return false;
        }
        for (T element : other) {
            if (!contains(element)) {
                return false;
            }
        }
        return true;
    }

    public boolean isSubSetOf(IdentityHashSet<T> other) {
        if (size() > other.size()) {
            return false;
        }
        for (T element : this) {
            if (!other.contains(element)) {
                return false;
            }
        }
        return true;
    }

    public boolean isStrictSubSetOf(IdentityHashSet<T> other) {
        if (size() >= other.size()) {
            return false;
        }
        for (T element : this) {
            if (!other.contains(element)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public <E> E[] toArray(E[] a) {
        return internalMap.keySet().toArray(a);
    }

    @Override
    public String toString() {
        String string = "[ ";
        for (T element : internalMap.keySet()) {
            string += element.toString() + " ";
        }
        string += "]";
        return string;
    }
}
