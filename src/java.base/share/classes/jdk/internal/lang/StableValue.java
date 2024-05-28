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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A thin, atomic, thread-safe, set-at-most-once, stable value holder eligible for
 * certain JVM optimizations if set to a value.
 * <p>
 * A stable value is said to be monotonic because the state of a stable value can only go
 * from <em>unset</em> to <em>set</em> and consequently, a value can only be set
 * at most once.
 <p>
 * To create a new fresh (unset) StableValue, use the {@linkplain StableValue#of()}
 * factory.
 * <p>
 * Except for a StableValue's value itself, all method parameters must be <em>non-null</em>
 * or a {@link NullPointerException} will be thrown.
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
     * {@return the set value (nullable) if set, otherwise return the {@code other} value}
     * @param other to return if the stable value is not set
     */
    T orElse(T other);

    /**
     * {@return the set value if set, otherwise throws
     * {@code NoSuchElementException}}
     *
     * @throws NoSuchElementException if no value is set
     */
    T orElseThrow();

    /**
     * If the stable value is unset (or is set to {@code null}), attempts to compute its
     * value using the given supplier function and enters it into this stable value
     * unless {@code null}.
     *
     * <p>If the supplier function returns {@code null}, no value is set. If the supplier
     * function itself throws an (unchecked) exception, the exception is rethrown, and
     * no value is set. The most common usage is to construct a new object serving as
     * an initial value or memoized result, as in:
     *
     * <pre> {@code
     * T t = stable.computeIfUnset(T::new);
     * }</pre>
     *
     * @implSpec
     * The default implementation is equivalent to the following steps for this
     * {@code stable}, then returning the current value or {@code null} if now
     * absent:
     *
     * <pre> {@code
     * if (stable.orElseNull() == null) {
     *     T newValue = supplier.apply(key);
     *     if (newValue != null)
     *         stable.trySet(newValue);
     * }
     * }</pre>
     * Except, the method is atomic, thread-safe and guarantees the provided
     * supplier function is successfully invoked at most once even in
     * a multi-thread environment.
     *
     * @param supplier the mapping supplier to compute a value
     * @return the current (existing or computed) value associated with
     *         the stable value
     */
    T computeIfUnset(Supplier<? extends T> supplier);

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
            throw new IllegalStateException("Value already set: " + this);
        }
    }

    // Factories

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
     * even in a multi-threaded environment. Competing threads invoking the
     * {@linkplain Supplier#get()} method when a value is already under computation
     * will block until a value is computed or an exception is thrown by the
     * computing thread.
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
        return new MemoizedSupplier<>(original, StableValue.of());
    }

}
