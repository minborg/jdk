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
import java.util.StringJoiner;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static jdk.internal.lang.stable.StableUtil.*;

@ValueBased
public final class StableArrayImpl<T> implements StableArray<T> {

    // The set values (if non-null)
    @Stable
    private final T[] values;

    // Used to signal UNSET, SET_NONNULL, or SET_NULL; and for piggybacking:
    // A `state` element must always be read before the corresponding `value` element is read
    // A `value` element must always be written before the corresponding `state` element is written
    @Stable
    private final byte[] states;

    @SuppressWarnings("unchecked")
    private StableArrayImpl(int length) {
        values = (T[]) new Object[length];
        states = new byte[length];
    }

    public boolean trySet(int index, T value) {
        if (state(index) != UNSET) {
            return false;
        }
        return trySet0(index, value);
    }

    private boolean trySet0(int index, T value) {
        // We need to replace the null value with something else or several invokers could set the value to `null`
        boolean set = UNSAFE.compareAndSetReference(values, objectOffset(index),
                null, (value == null) ? NULL_SENTINEL : value);
        if (set) {
            stateUnchecked(index, (value == null) ? SET_NULL : SET_NONNULL);
        }
        return set;
    }

    @ForceInline
    @Override
    public T orElseThrow(int index) {
        return switch (state(index)) {
            case SET_NONNULL -> valueUnchecked(index);
            case SET_NULL    -> null;
            default          -> throw new NoSuchElementException("No value set for index " + index);
        };
    }

    @ForceInline
    @Override
    public T orElse(int index, T other) {
        return switch (state(index)) {
            case SET_NONNULL -> valueUnchecked(index);
            case SET_NULL    -> null;
            default          -> other;
        };
    }

    @ForceInline
    @Override
    public int length() {
        return values.length;
    }

    @ForceInline
    @Override
    public T computeIfUnset(int index, IntFunction<? extends T> mapper) {
        return switch (state(index)) {
            case SET_NONNULL -> valueUnchecked(index);
            case SET_NULL    -> null;
            default          -> computeIfUnset0(index, mapper);
        };
    }

    @DontInline
    private T computeIfUnset0(int index, IntFunction<? extends T> mapper) {
        T t = mapper.apply(index);
        // There could be a race going on here because the two corresponding
        // array components are not set atomically. We have to be careful:
        //
        // 1) Maybe set the value (we are racing other threads)
        trySet0(index, t);
        // 2) Wait until we have a valid value (that some other thread might have written).
        while (stateUnchecked(index) == UNSET) {
            Thread.onSpinWait();
        }
        // 3) Now we know there is a value present
        return orElseThrow(index);
    }

    @Override
    public int hashCode() {
        // Todo: remove the use of streams
        return IntStream.range(0, length())
                .mapToObj(i -> this.orElse(i, null))
                .mapToInt(Objects::hashCode)
                .reduce(1, (a, e) -> 31 * a + e);
    }

    @Override
    public boolean equals(Object obj) {
        // Todo: remove the use of streams
        return obj instanceof StableArrayImpl<?> other &&
                length() == other.length() &&
                IntStream.range(0, length())
                        .allMatch(i -> stateUnchecked(i) == other.stateUnchecked(i) &&
                                Objects.equals(valueUnchecked(i), other.valueUnchecked(i)));
    }

    @Override
    public String toString() {
        // Refrain from using Streams and StringJoiner as they cannot
        // be used early in the boot sequence
        StringBuilder sb = new StringBuilder("StableArray[");
        for (int i = 0; i < length(); i++) {
            if (i != 0) {
                sb.append(',').append(' ');
            }
            // Make sure state is read first.
            byte state = stateUnchecked(i);
            sb.append(StableUtil.render(state, valueUnchecked(i)));
        }
        return sb.append(']').toString();
    }

    @SuppressWarnings("unchecked")
    @ForceInline
    private T valueUnchecked(int index) {
        // return values[index]; This should work with piggybacking
        return (T) UNSAFE.getReferenceVolatile(values, objectOffset(index));
    }

    @ForceInline
    private byte state(int index) {
        // Explicitly check the index as we are performing unsafe operations later on
        Objects.checkIndex(index, states.length);
        return stateUnchecked(index);
    }

    @ForceInline
    private byte stateUnchecked(int index) {
        return UNSAFE.getByteVolatile(states, byteOffset(index));
    }

    @ForceInline
    private void stateUnchecked(int index, byte value) {
        UNSAFE.putByteVolatile(states, byteOffset(index), value);
    }

    // Factory
    public static <T> StableArrayImpl<T> of(int length) {
        return new StableArrayImpl<>(length);
    }

}
