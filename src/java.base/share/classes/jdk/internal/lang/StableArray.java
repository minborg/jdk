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

import jdk.internal.lang.stable.MemoizedFunction;
import jdk.internal.lang.stable.MemoizedIntFunction;
import jdk.internal.lang.stable.StableArrayImpl;
import jdk.internal.lang.stable.TrustedFieldType;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * A thin, atomic, thread-safe, set-at-most-once-per-index, stable array holder
 * eligible for certain JVM optimizations for components set to a value.
 * <p>
 * A stable array's component is said to be monotonic because the state of a stable value
 * can only go from <em>unset</em> to <em>set</em> and consequently, a value can only be
 * set at most once.
 <p>
 * To create a new fresh StableArray, use the {@linkplain StableArray#of(int)}
 * factory.
 * <p>
 * Except for a StableArray component's value itself, all method parameters must be
 * <em>non-null</em> and all collections provided must only contain <em>non-null</em>
 * elements or a {@link NullPointerException} will be thrown.
 * <p>
 * StableArrays are <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 * Programmers should not use StableArrays for synchronization, or unpredictable behavior
 * may occur. For example, in a future release, synchronization may fail.
 *
 * @param <T> type of the components
 *
 * @since 23
 */
public sealed interface StableArray<T>
        extends TrustedFieldType
        permits StableArrayImpl {

    // Principal methods

    /**
     * {@return {@code true} if the stable value at the provided {@code index} was set to
     * the provided {@code value}, otherwise returns {@code false}}
     *
     * @param index in the array
     * @param value to set (nullable)
     * @throws IndexOutOfBoundsException if the provided {@code index < 0 || >= length()}
     */
    boolean trySet(int index, T value);

    /**
     * {@return the set value (nullable) at the provided {@code index} if set,
     * otherwise {@code null}}
     *
     * @param index in the array
     * @param other to return if a value at {@code index} is not set
     * @throws IndexOutOfBoundsException if the provided {@code index < 0 || >= length()}
     */
    T orElse(int index, T other);

    /**
     * {@return the length of this array}
     */
    int length();

    // Convenience methods

    /**
     * Sets the stable value at the provided {@code index} to the provided {@code value},
     * or, if already set to a non-null value, throws {@linkplain IllegalStateException}}
     *
     * @param index in the array
     * @param value to set (nullable)
     * @throws IndexOutOfBoundsException if the provided {@code index < 0 || >= length()}
     * @throws IllegalArgumentException if a non-null value is already set
     */
    default void setOrThrow(int index, T value) {
        if (!trySet(index, value)) {
            throw new IllegalStateException("Value already set: " + orElseThrow(index));
        }
    }

    /**
     * {@return the set value at the provided {@code index} if set to a value,
     * otherwise throws {@code NoSuchElementException}}
     *
     * @param index in the array
     * @throws IndexOutOfBoundsException if the provided {@code index < 0 || >= length()}
     * @throws NoSuchElementException if no non-null value is set
     */
    T orElseThrow(int index);

    /**
     * If the stable value at the provided {@code index} is unset
     * (or is set to {@code null}), attempts to compute its value using the given mapper
     * function and enters it into this stable value unless {@code null}.
     *
     * <p>If the mapper function returns {@code null}, no value is set. If the supplier
     * function itself throws an (unchecked) exception, the exception is rethrown, and
     * no value is set. The most common usage is to construct a new object serving as
     * an initial value or memoized result, as in:
     *
     * <pre> {@code
     * T t = array.computeIfUnset(42, T::new);
     * }</pre>
     *
     * @implSpec
     * The default implementation is equivalent to the following steps for this
     * {@code array} and {@code index}, then returning the current value or {@code null}
     * if now absent:
     *
     * <pre> {@code
     * if (array.orElseNull(index) == null) {
     *     T newValue = supplier.apply(index);
     *     if (newValue != null)
     *         array.trySet(newValue);
     * }
     * }</pre>
     * Except, the method is atomic, thread-safe and guarantees the provided
     * mapper function is successfully invoked at most once even in
     * a multi-thread environment.
     *
     * @param index in the array
     * @param mapper the mapping function to compute a value
     * @return the current (existing or computed) value associated with
     *         the stable value
     */
    T computeIfUnset(int index, IntFunction<? extends T> mapper);

    /**
     * {@return a fresh stable array with unset ({@code null}) elements}
     *
     * @param <T> the value type to set
     */
    static <T> StableArray<T> of(int length) {
        if (length < 0) {
            throw new IllegalArgumentException();
        }
        return StableArrayImpl.of(length);
    }

    /**
     * {@return a new <em>memoized</em> {@linkplain IntFunction} backed by an internal
     * stable array of the provided {@code size} where the provided {@code original}
     * IntFunction will only be invoked at most once per distinct {@code int} value}
     * <p>
     * The provided {@code original} IntFunction is guaranteed to be invoked at most once
     * per input value, even in a multi-threaded environment. Competing threads invoking
     * the {@linkplain IntFunction#apply(int)} method for an input value already under
     * computation will block until a value is computed or an exception is thrown by the
     * computing thread.
     * <p>
     * If the provided {@code original} IntFunction throws an exception for a certain input
     * value, it is relayed to the initial caller. Subsequent read operations for the
     * same input value will throw {@linkplain java.util.NoSuchElementException}. The
     * class of the original exception is also recorded and is available via the
     * {@linkplain Object#toString()} method.
     * For security reasons, the entire original exception is not retained.
     * <p>
     * If the {@code original} IntFunction invokes the returned IntFunction recursively
     * for a specific input value, a StackOverflowError will be thrown when the returned
     * IntFunction's {@linkplain IntFunction#apply(int)} ()} method is invoked.
     * <p>
     * The returned IntFunction will throw {@linkplain IndexOutOfBoundsException} if
     * {@linkplain IntFunction#apply(int)} is invoked with a value {@code < 0 || > size}.
     *
     * @param length   the number of elements in the backing list
     * @param original the original IntFunction to convert to a memoized IntFunction
     * @param <R>      the return type of the IntFunction
     */
    static <R> IntFunction<R> memoizedIntFunction(int length,
                                                  IntFunction<? extends R> original) {
        if (length < 0) {
            throw new IllegalArgumentException();
        }
        Objects.requireNonNull(original);
        return new MemoizedIntFunction<>(original, StableArray.of(length));
    }

    /**
     * {@return a new <em>memoized</em> {@linkplain Function} backed by an internal
     * stable array with the provided {@code inputs} keys mapped to indices where the
     * provided {@code original} Function will only be invoked at most once per distinct
     * input}
     * <p>
     * The provided {@code original} Function is guaranteed to be invoked at most once
     * per input value, even in a multi-threaded environment. Competing threads invoking
     * the {@linkplain Function#apply(Object)} method for an input value already under
     * computation will block until a value is computed or an exception is thrown by the
     * computing thread.
     * <p>
     * If the provided {@code original} Function throws an exception for a certain input
     * value, it is relayed to the initial caller. Subsequent read operations for the
     * same input value will throw {@linkplain java.util.NoSuchElementException}. The
     * class of the original exception is also recorded and is available via the
     * {@linkplain Object#toString()} method.
     * For security reasons, the entire original exception is not retained.
     * <p>
     * If the {@code original} Function invokes the returned Function recursively
     * for a specific input value, a StackOverflowError will be thrown when the returned
     * Function's {@linkplain Function#apply(Object)}} method is invoked.
     * <p>
     * The returned Function will throw {@linkplain NoSuchElementException} if
     * {@linkplain Function#apply(Object)} is invoked with a value that is not in the
     * given {@code inputs} Set.
     *
     * @param original the original Function to convert to a memoized Function
     * @param inputs   the potential input values to the Function
     * @param <T>      the type of input values
     * @param <R>      the return type of the function
     */
    static <T, R> Function<T, R> memoizedFunction(Set<T> inputs,
                                                  Function<T, R> original) {
        Objects.requireNonNull(inputs);
        Objects.requireNonNull(original);
        return MemoizedFunction.of(inputs, original);
    }

}
