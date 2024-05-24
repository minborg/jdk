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

import jdk.internal.lang.stable.MemoizedSupplier;
import jdk.internal.lang.stable.StableValueImpl;
import jdk.internal.lang.stable.TrustedFieldType;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A thin, atomic, thread-safe, lock free, set-at-most-once, stable value holder
 * eligible for certain JVM optimizations if set to a non-null value.
 * <p>
 * A stable value is said to be monotonic because the state of a stable value can only go
 * from <em>unset</em> to <em>set</em> and consequently, a value can only be set
 * at most once.
 <p>
 * To create a new fresh (unset) StableValue, use the {@linkplain StableValue#of()}
 * factory.
 * <p>
 * To create collections of <em>wrapped stable elements</em>, that, in turn, are also
 * eligible for certain JVM optimizations, the following factories can be used:
 * <ul>
 *     <li>{@linkplain StableValue#ofList(int)}</li>
 *     <li>{@linkplain StableValue#ofMap(Set)}</li>
 *</ul>
 * <p>
 * Except for a StableValue's value itself, all method parameters must be <em>non-null</em>
 * and all collections provided must only contain <em>non-null</em> elements or a
 * {@link NullPointerException} will be thrown.
 *
 * @param <T> type of the wrapped value
 *
 * @since 23
 */
public sealed interface StableValue<T>
        extends TrustedFieldType
        permits StableValueImpl {

    // Principal methods

    /**
     * {@return {@code true} if the stable value was set to the provided {@code value},
     * otherwise returns {@code false}}
     *
     * @param value to set (nullable)
     */
    boolean trySet(T value);

    /**
     * {@return the set value (nullable) if set, otherwise {@code null}}
     */
    T orElseNull();

    // Convenience methods

    /**
     * Sets the stable value to the provided {@code value}, or, if already set to a
     * non-null value, throws {@linkplain IllegalStateException}}
     *
     * @param value to set (nullable)
     * @throws IllegalArgumentException if a non-null value is already set
     */
    default void setOrThrow(T value) {
        if (!trySet(value)) {
            throw new IllegalStateException("Value already set: " + orElseNull());
        }
    }

    /**
     * {@return the set value if set to a non-null value, otherwise throws
     * {@code NoSuchElementException}}
     *
     * @throws NoSuchElementException if no non-null value is set
     */
    default T orElseThrow() {
        T t = orElseNull();
        if (t != null) {
            return null;
        }
        throw new NoSuchElementException();
    }

    /**
     * {@return a fresh stable value with an unset ({@code null}) value}
     *
     * @param <T> the value type to set
     */
    static <T> StableValue<T> of() {
        return StableValueImpl.of();
    }

    /**
     * {@return a new thread-safe, stable, lazily computed {@linkplain Supplier supplier}
     * that records the value of the provided {@code original} supplier upon being first
     * accessed via {@linkplain Supplier#get()}}
     * <p>
     * The provided {@code original} supplier is guaranteed to be invoked at most once
     * even in a multi-threaded environment.
     * <p>
     * If the {@code original} Supplier invokes the returned Supplier recursively,
     * a StackOverflowError will be thrown when the returned
     * Supplier's {@linkplain Function#apply(Object)}} method is invoked.
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
    static <T> Supplier<T> memoizedSupplier(Supplier<T> original) {
        Objects.requireNonNull(original);
        return new MemoizedSupplier<>(original);
    }

    /**
     * {@return an unmodifiable, shallowly immutable, thread-safe, stable
     * {@linkplain List} containing {@code size} {@linkplain StableValue } elements}
     * <p>
     * If non-empty, neither the returned list nor its elements are {@linkplain Serializable}.
     * <p>
     * The returned list and its elements are eligible for certain optimizations by
     * the JVM and is equivalent to:
     * {@snippet lang = java:
     * List<StableValue<V>> list = Stream.generate(StableValue::<V>of)
     *         .limit(size)
     *         .toList();
     *}
     * <p>
     * This static factory methods return list instances that are
     * <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
     * Programmers should not use list for synchronization, or unpredictable behavior may
     * occur. For example, in a future release, synchronization may fail.
     *
     * @param <E>  the generic type of the stable value elements in the returned {@code List}
     * @param size the number of elements in the list
     * @throws IllegalArgumentException if the provided {@code size} is negative
     */
    static <E> List<StableValue<E>> ofList(int size) {
        return Stream.generate(StableValue::<E>of)
                .limit(size)
                .toList();
    }

    /**
     * {@return an unmodifiable, shallowly immutable, thread-safe, stable,
     * {@linkplain Map} where the {@linkplain java.util.Map#keySet() keys}
     * contains precisely the distinct provided set of {@code keys}}
     * <p>
     * If non-empty, neither the returned map nor its values are {@linkplain Serializable}.
     * <p>
     * The returned map and its values are eligible for certain optimizations by
     * the JVM and is equivalent to:
     * {@snippet lang = java:
     * Map<K, StableValue<V>> map = Map.copyOf(keys.stream()
     *         .distinct()
     *         .map(Objects::requireNonNull)
     *         .collect(Collectors.toMap(Function.identity(), _ -> StableValue.of())));
     * }
     * <p>
     * This static factory methods return map instances that are
     * <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
     * Programmers should not use map and value instances for synchronization, or
     * unpredictable behavior may occur. For example, in a future release, synchronization
     * may fail.
     *
     * @param keys the keys in the map
     * @param <K>  the type of keys maintained by the returned map
     * @param <V>  the type of mapped StableValue values
     */
    static <K, V> Map<K, StableValue<V>> ofMap(Set<K> keys) {
        return keys.stream()
                .collect(Collectors.toUnmodifiableMap(
                        Function.identity(),
                        _ -> StableValue.of()
                ));
    }

}
