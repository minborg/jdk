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

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.lazy.LazyArray;
import java.util.concurrent.lazy.LazyValue;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class StandardLazyArray<V>  implements LazyArray<V> {

    final StandardLazyValue<V>[] lazyValueObjects;

    @SuppressWarnings("unchecked")
    public StandardLazyArray(int length,
                             IntFunction<? extends V> presetMapper) {

        lazyValueObjects = IntStream.range(0, length)
                .mapToObj(i -> new StandardLazyValue<>(() -> presetMapper.apply(i)))
                .toArray(StandardLazyValue[]::new);
    }

    @Override
    public final int length() {
        return lazyValueObjects.length;
    }

    @Override
    public boolean isBound(int index) {
        return lazyValueObjects[index].isBound();
    }

    @Override
    public V get(int index) {
        return lazyValueObjects[index].get();
    }

    @Override
    public V orElse(int index, V other) {
        return lazyValueObjects[index].orElse(other);
    }

    @Override
    public <X extends Throwable> V orElseThrow(int index, Supplier<? extends X> exceptionSupplier) throws X {
        return lazyValueObjects[index].orElseThrow(exceptionSupplier);
    }

    @Override
    public Stream<V> stream() {
        return IntStream.range(0, length())
                .mapToObj(i -> get(i));
    }

    @Override
    public Stream<V> stream(V other) {
        return IntStream.range(0, length())
                .mapToObj(i -> orElse(i, other));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + IntStream.range(0, length())
                .mapToObj(i -> switch (state(i)) {
                    case UNBOUND -> "-";
                    case CONSTRUCTING -> "+";
                    case BOUND -> Objects.toString(lazyValueObjects[i].valueVolatile());
                })
                .collect(Collectors.joining(", ")) + "]";
    }

    /**
     * {@return the {@link LazyState State} of this Lazy}.
     * <p>
     * The value is a snapshot of the current State.
     * No attempt is made to compute a value if it is not already present.
     * <p>
     * If the returned State is either {@link LazyState#BOUND} or
     * {@link LazyState#ERROR}, it is guaranteed the state will
     * never change in the future.
     * <p>
     * This method can be used to act on a value if it is present:
     * {@snippet lang = java:
     *     if (lazy.state() == State.PRESENT) {
     *         // perform action on the value
     *     }
     *}
     * @param index to retrieve the State from
     * @throws ArrayIndexOutOfBoundsException if {@code index < 0} or {@code index >= length()}
     */
    private final LazyState state(int index) {
        return lazyValueObjects[index].state();
    }

}
