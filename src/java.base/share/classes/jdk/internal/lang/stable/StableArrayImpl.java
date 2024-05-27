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
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static jdk.internal.lang.stable.StableUtil.UNSAFE;
import static jdk.internal.lang.stable.StableUtil.objectOffset;

@ValueBased
public final class StableArrayImpl<T> implements StableArray<T> {

    @Stable
    private final Computation<T>[] computations;
    @Stable
    private final Object[] mutexes;

    @SuppressWarnings("unchecked")
    private StableArrayImpl(int length) {
        computations = (Computation<T>[]) new Computation<?>[length];
        mutexes = Stream.generate(Object::new).limit(length).toArray();
    }

    public boolean trySet(int index, T value) {
        // Implicit index check
        if (computations[index] != null) {
            return false;
        }
        synchronized (mutexes[index]) {
            return trySet0(index, Computation.Value.of(value));
        }
    }

    private boolean trySet0(int index, Computation<T> comp) {
        return UNSAFE.compareAndSetReference(computations, objectOffset(index), null, comp);
    }

    @Override
    @ForceInline
    public T orElseThrow(int index) {
        return switch (computation(index)) {
            case Computation.Value<T> n -> n.value();
            case Computation.Error<T> e -> throw new NoSuchElementException(e.throwableClassName());
            case null                   -> throw new NoSuchElementException("No value set");
        };
    }

    @Override
    public T orElse(int index, T other) {
        return switch (computation(index)) {
            case Computation.Value<T> n -> n.value();
            case Computation.Error<T> e -> throw new NoSuchElementException(e.throwableClassName());
            case null                   -> other;
        };
    }

    @Override
    public int length() {
        return computations.length;
    }

    @Override
    public T computeIfUnset(int index, IntFunction<? extends T> mapper) {
        Objects.checkIndex(index, computations.length);
        if (computationUnchecked(index) instanceof Computation.Value<T> v) {
            return v.value();
        }
        // Implicit range checking of `index`
        synchronized (mutexes[index]) {
            return switch (computationUnchecked(index)) {
                case Computation.Value<T> n -> n.value();
                case Computation.Error<T> e -> throw new NoSuchElementException(e.throwableClassName());
                case null -> {
                    try {
                        T t = mapper.apply(index);
                        trySet0(index, Computation.Value.of(t));
                        yield t;
                    } catch (Throwable th) {
                        trySet0(index, Computation.Error.of(th));
                        throw th;
                    }
                }
            };
        }
    }

    @Override
    public int hashCode() {
        return IntStream.range(0, length())
                .mapToObj(this::computationUnchecked)
                .mapToInt(Objects::hashCode)
                .reduce(1, (a, e) -> 31 * a + e);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof StableArrayImpl<?> other &&
                length() == other.length() &&
                IntStream.range(0, length())
                        .allMatch(i -> Objects.equals(computationUnchecked(i), other.computationUnchecked(i)));
    }

    @Override
    public String toString() {
        return "StableArray[" +
                IntStream.range(0, length())
                        .mapToObj(this::computationUnchecked)
                        .map(StableUtil::render)
                        .collect(Collectors.joining(", ")) +
                ']';
    }

    @ForceInline
    private Computation<T> computation(int index) {
        // Explicitly check the index as we are performing unsafe operations later on
        Objects.checkIndex(index, computations.length);
        return computationUnchecked(index);
    }

    @ForceInline
    @SuppressWarnings("unchecked")
    private Computation<T> computationUnchecked(int index) {
        return (Computation<T>) UNSAFE.getReferenceVolatile(computations, objectOffset(index));
    }

    // Factory
    public static <T> StableArrayImpl<T> of(int length) {
        return new StableArrayImpl<>(length);
    }

}
