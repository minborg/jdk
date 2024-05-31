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
import java.util.function.IntFunction;

import static jdk.internal.lang.stable.StableUtil.*;

/**
 * Implementation of a StableArray.
 * <p>
 * Note: In order for a StaleArray to be used very early in the boot sequence,
 * it cannot rely on constructs backed by SharedSecrets such as reflection. This
 * includes lambdas, most classes in java.util.stream.* and StringJoiner.
 *
 * @param <T> the component type
 */
@ValueBased
public final class StableArrayImpl<T> implements StableArray<T> {

    // Unset:         null
    // Set(non-null): The set value
    // Set(null):     nullSentinel()
    @Stable
    private final T[] values;

    @SuppressWarnings("unchecked")
    private StableArrayImpl(int length) {
        values = (T[]) new Object[length];
    }

    public boolean trySet(int index, T value) {
        Objects.checkIndex(index, values.length);
        return UNSAFE.compareAndSetReference(values, objectOffset(index),
                null, (value == null) ? nullSentinel() : value);
    }

    @ForceInline
    @Override
    public T orElseThrow(int index) {
        final T t = value(index);
        if (t != null) {
            return t == nullSentinel() ? null : t;
        }
        throw new NoSuchElementException("No value set for index: " + index);
    }

    @ForceInline
    @Override
    public T orElse(int index, T other) {
        final T t = value(index);
        if (t != null) {
            return t == nullSentinel() ? null : t;
        }
        return other;
    }

    @ForceInline
    @Override
    public int length() {
        return values.length;
    }

    @ForceInline
    @Override
    public T computeIfUnset(int index, IntFunction<? extends T> mapper) {
        final T t = value(index);
        if (t != null) {
            return t == nullSentinel() ? null : t;
        }
        return compute(index, mapper);
    }

    @DontInline
    private T compute(int index, IntFunction<? extends T> mapper) {
        final T t = mapper.apply(index);
        trySet(index, t);
        return orElseThrow(index);
    }

    @Override
    public int hashCode() {
        int h = 1;
        for (int i = 0; i < length(); i++) {
            h = 31 * h + Objects.hashCode(orElse(i, null));
        }
        return h;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof StableArrayImpl<?> other)) {
            return false;
        }
        for (int i = 0; i < length(); i++) {
            if (!Objects.equals(valueUnchecked(i), other.valueUnchecked(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        // Refrain from using Streams and StringJoiner as they cannot
        // be used early in the boot sequence
        final StringBuilder sb = new StringBuilder("StableArray[");
        for (int i = 0; i < length(); i++) {
            if (i != 0) {
                sb.append(',').append(' ');
            }
            sb.append(StableUtil.render(valueUnchecked(i)));
        }
        return sb.append(']').toString();
    }

    @ForceInline
    private T value(int index) {
        Objects.checkIndex(index, values.length);
        return valueUnchecked(index);
    }

    @SuppressWarnings("unchecked")
    @ForceInline
    private T valueUnchecked(int index) {
        return (T) UNSAFE.getReferenceVolatile(values, objectOffset(index));
    }

    // Factory
    public static <T> StableArrayImpl<T> of(int length) {
        return new StableArrayImpl<>(length);
    }

}
