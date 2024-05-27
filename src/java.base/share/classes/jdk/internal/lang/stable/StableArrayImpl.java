/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.lang.stable;

import jdk.internal.ValueBased;
import jdk.internal.lang.StableArray;
import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static jdk.internal.lang.stable.StableUtil.UNSAFE;
import static jdk.internal.lang.stable.StableUtil.objectOffset;

@ValueBased
public final class StableArrayImpl<T> implements StableArray<T> {

    @Stable
    private final T[] elements;

    @SuppressWarnings("unchecked")
    private StableArrayImpl(int length) {
        elements =  (T[]) new Object[length];
    }

    public boolean trySet(int index, T value) {
        // Explicitly check the index as we are performing unsafe operations later on
        Objects.checkIndex(index, elements.length);
        // Prevent store/store reordering to assure newly created object's fields are all
        // visible before they can be observed by other threads that are loading under
        // plain memory semantics.
        // In other words, avoids partially constructed objects to be observed.
        UNSAFE.storeStoreFence();
        return UNSAFE.compareAndSetReference(elements, objectOffset(index), null, value);
    }

    @ForceInline
    public T orElseThrow(int index) {
        // Implicit array bounds check
        final T e = elements[index];
        if (e != null) {
            return e;
        }
        return getOrThrowSlowPath(index);
    }

    @DontInline
    private T getOrThrowSlowPath(int index) {
        @SuppressWarnings("unchecked")
        final T e = (T) UNSAFE.getReferenceVolatile(elements, objectOffset(index));
        if (e != null) {
            return e;
        }
        throw new NoSuchElementException();
    }

    @SuppressWarnings("unchecked")
    @ForceInline
    public T orElseNull(int index) {
        // Implicit array bounds check
        final T e = elements[index];
        if (e != null) {
            return e;
        }
        return (T) UNSAFE.getReferenceVolatile(elements, objectOffset(index));
    }

    @Override
    public int length() {
        return elements.length;
    }

    @Override
    public int hashCode() {
        return IntStream.range(0, length())
                .mapToObj(this::orElseNull)
                .mapToInt(Objects::hashCode)
                .reduce(1, (a, e) -> 31 * a + e);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof StableArrayImpl<?> other &&
                length() == other.length() &&
                IntStream.range(0, length())
                        .allMatch(i -> Objects.equals(orElseNull(i), other.orElseNull(i)));
    }

    @Override
    public String toString() {
        return "StableArray[" +
                IntStream.range(0, length())
                        .mapToObj(this::orElseNull)
                        .map(Objects::toString)
                        .collect(Collectors.joining(", ")) +
                ']';
    }

    // Factory
    public static <T> StableArrayImpl<T> of(int length) {
        return new StableArrayImpl<>(length);
    }

}
