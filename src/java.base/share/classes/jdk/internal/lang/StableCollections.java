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

package jdk.internal.lang;

import jdk.internal.access.SharedSecrets;
import jdk.internal.lang.stable.StableSupplier;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Factory methods that produce stable collections that safely wrap the internal
 * {@linkplain jdk.internal.vm.annotation.Stable} annotation.
 * <p>
 * As such, the memoized objects are eligible for certain optimizations by the JVM.
 * <p>
 * All returned objects are thread-safe.
 *
 * @since 23
 */
public final class StableCollections {

    // Suppresses default constructor, ensuring non-instantiability.
    private StableCollections() {}

    /**
     * {@return a new thread-safe, stable, lazily computed {@linkplain Supplier supplier}
     * that records the value of the provided {@code original} supplier upon being first
     * accessed via {@linkplain Supplier#get()}}
     * <p>
     * The provided {@code original} supplier is guaranteed to be invoked at most once
     * even in a multi-threaded environment.
     * <p>
     * If the provided {@code original} supplier throws an exception, it is relayed
     * to the initial caller. Subsequent read operations will throw
     * {@linkplain java.util.NoSuchElementException}. The class of the original exception
     * is also recorded and is available via the {@linkplain Object#toString()} method.
     * For security reasons, the entire original exception is not retained.
     *
     * @param original supplier
     * @param <T> the type of results supplied by the returned supplier
     */
    public static <T> Supplier<T> ofSupplier(Supplier<? extends T> original) {
        Objects.requireNonNull(original);
        return StableSupplier.create(original);
    }

    /**
     * {@return an unmodifiable, shallowly immutable, thread-safe, stable, lazily
     * computed {@linkplain List list} containing {@code size} elements where the stable
     * elements are lazily computed upon being first accessed (e.g. via
     * {@linkplain List#get(int)}) by applying the provided {@code mapper}}
     * <p>
     * The provided {@code mapper} is guaranteed to be invoked at most once per index
     * even in a multi-threaded environment.
     * <p>
     * If the provided {@code mapper} throws an exception for a certain index, it is relayed
     * to the initial caller. Subsequent read operations for the same index will throw
     * {@linkplain java.util.NoSuchElementException}. The class of the original exception
     * is also recorded and is available via the {@linkplain Object#toString()} method.
     * For security reasons, the entire original exception is not retained.
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
    public static <E> List<E> ofList(int size,
                                     IntFunction<? extends E> mapper) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        Objects.requireNonNull(mapper);
        return size == 0
                ? List.of()
                : SharedSecrets.getJavaUtilCollectionAccess().stableList(size, mapper);
    }

    /**
     * {@return an unmodifiable, shallowly immutable, thread-safe, stable,
     * value-lazy computed {@linkplain Map map} where the {@linkplain java.util.Map#keySet() keys}
     * contains precisely the distinct provided set of {@code keys} and where the
     * stable values are, in turn, lazily computed upon being first accessed
     * (e.g. via {@linkplain Map#get(Object) get(key)})}
     * <p>
     * The provided {@code mapper} is guaranteed to be invoked at most once per key
     * even in a multi-threaded environment.
     * <p>
     * If the provided {@code mapper} throws an exception for a certain key, it is relayed
     * to the initial caller. Subsequent read operations for the same key will throw
     * {@linkplain java.util.NoSuchElementException}. The class of the original exception
     * is also recorded and is available via the {@linkplain Object#toString()} method.
     * For security reasons, the entire original exception is not retained.
     * <p>
     * If non-empty, neither the returned map nor its values are {@linkplain Serializable}.
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
    public static <K, V> Map<K, V> ofMap(Set<? extends K> keys,
                                         Function<? super K, ? extends V> mapper) {
        Objects.requireNonNull(keys);
        Objects.requireNonNull(mapper);
        return keys.isEmpty()
                ? Map.of()
                : SharedSecrets.getJavaUtilCollectionAccess().stableMap(keys, mapper);
    }

}
