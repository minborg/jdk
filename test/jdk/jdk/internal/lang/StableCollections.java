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

package internal.lang;

import java.io.Serializable;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadFactory;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Factory methods that produce memoized constructs that safely wrap the internal
 * {@linkplain jdk.internal.vm.annotation.Stable} annotation.
 * <p>
 * As such, the memoized objects are eligible for certain optimizations by the JVM.
 */
public interface StableCollections {

    static <T> Supplier<T> ofSupplier(Supplier<? extends T> original) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@return an unmodifiable, shallowly immutable, thread-safe, stable, lazily
     * computed {@linkplain List} containing {@code size} elements where the stable
     * elements are lazily computed upon being first accessed (e.g. via
     * {@linkplain List#get(int)})}
     * <p>
     * If non-empty, neither the returned list nor its elements are {@linkplain Serializable}.
     * <p>
     * This static factory methods return list instances that are
     * <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
     * Programmers should not use lists instances for synchronization, or unpredictable
     * behavior may occur. For example, in future release, synchronization may fail.
     *
     * @param <E> the type of elements in the returned list
     * @param size the number of elements in the returned list
     * @throws IllegalArgumentException if the provided {@code size} is negative
     *
     * @since 23
     */
    static <E> List<E> ofList(int size,
                              IntFunction<? extends E> mapper) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@return an unmodifiable, shallowly immutable, thread-safe, stable,
     * value-lazy computed {@linkplain Map} where the {@linkplain java.util.Map#keySet() keys}
     * contains precisely the distinct provided set of {@code keys} and where the
     * stable values are, in turn, lazily computed upon being first accessed
     * (e.g. via {@linkplain Map#get(Object) get(key)})}
     * <p>
     * If non-empty, neither the returned map nor its values are {@linkplain Serializable}.
     * <p>
     * <p>
     * This static factory methods return map instances that are
     * <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
     * Programmers should not use map instances for synchronization, or unpredictable
     * behavior may occur. For example, in a future release, synchronization may fail.
     * <p>
     * Providing an {@linkplain EnumSet} as {@code keys} will
     * make the returned Map eligible for certain additional optimizations.
     *
     * @param keys the keys in the map
     * @param <K> the type of keys maintained by the returned map
     * @param <V> the type of mapped values
     */
    static <K, V> Map<K, V> ofMap(Set<? extends K> keys,
                                  Function<? super K, ? extends V> mapper) {
        throw new UnsupportedOperationException();
    }


    /**
     * Demo
     * @param args not used
     */
    static void main(String[] args) {

        List<Integer> list = StableCollections.ofList(3, i -> i); // [1, 2, 3]
        IntFunction<Integer> memoizedIntFunction = list::get;

        Map<String, Integer> map = StableCollections.ofMap(Set.of("A", "AB"), String::length);
        Function<String, Integer> memoizedFunction = map::get;

        Map<String, Integer> mapBg = compute(Thread.ofVirtual().factory(),
                ofMap(Set.of("ABC", "263tvg1"), String::length),
                Comparator.naturalOrder());
    }



    // Drop the background-thread-compute methods below?

    static <T> Supplier<T> compute(ThreadFactory threadFactory,
                                   Supplier<T> supplier) {
        throw new UnsupportedOperationException();
    }

    static <E extends Comparable<? super E>> List<E> compute(ThreadFactory threadFactory,
                                                             List<E> list,
                                                             Comparator<? super E> order) {
        throw new UnsupportedOperationException();
    }

    static <K extends Comparable<? super K>, V> Map<K, V> compute(ThreadFactory threadFactory,
                                                                  Map<K, V> map,
                                                                  Comparator<? super K> order) {
        throw new UnsupportedOperationException();
    }


}
