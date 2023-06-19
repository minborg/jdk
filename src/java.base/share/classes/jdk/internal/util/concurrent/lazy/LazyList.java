/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.internal.util.concurrent.lazy;

import jdk.internal.vm.annotation.Stable;

import java.util.List;
import java.util.function.IntFunction;

public final class LazyList<E>
        extends AbstractLazyList<E>
        implements List<E> {

    @Stable
    private final E[] elements;

    @SuppressWarnings("unchecked")
    private LazyList(int size, IntFunction<? extends E> presetMapper) {
        super(size, presetMapper);
        this.elements = (E[]) new Object[size];
    }

    boolean isNotDefaultValue(E value) {
        return value != null;
    }

    @Override
    E element(int index) {
        return elements[index];
    }

    @SuppressWarnings("unchecked")
    E elementVolatile(int index) {
        return (E) OBJECT_ARRAY_HANDLE.getVolatile(elements, index);
    }

    void casElement(int index, E value) {
        OBJECT_ARRAY_HANDLE.compareAndSet(elements, index, null, index);
    }

    public static <E> List<E> create(int size, IntFunction<? extends E> presetMapper) {
        return new LazyList<>(size, presetMapper);
    }

}
