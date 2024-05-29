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
import jdk.internal.lang.stable.SimpleMemoizedSupplier;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * This class provides atomic, thread-safe, set-at-most-once, non-blocking, lazily
 * computed stable value holders eligible for certain JVM optimizations.
 * <p>
 * A stable value is said to be monotonic because the state of a stable value can only go
 * from <em>unset</em> to <em>set</em> and consequently, a value can only be set
 * at most once.
 * <p>
 * All method parameters must be <em>non-null</em> or a {@link NullPointerException} will
 * be thrown.
 *
 * @since 24
 */
public final class StableValues {

    // Suppresses default constructor, ensuring non-instantiability.
    private StableValues() {}

    /**
     * {@return a new atomic, thread-safe, stable, non-blocking, lazily computed
     * {@linkplain Supplier supplier} that records the value of the provided
     * {@code original} supplier upon being first accessed via {@linkplain Supplier#get()}}
     * <p>
     * Only one (witness) value is elected as a memoized value even though the provided
     * {@code original} supplier might be invoked simultaneously by multiple threads.
     * <p>
     * If the {@code original} Supplier invokes the returned Supplier recursively,
     * a StackOverflowError will be thrown when the returned
     * Supplier's {@linkplain Function#apply(Object)}} method is invoked.
     * <p>
     * If the provided {@code original} supplier throws an exception, it is relayed
     * to the initial {@linkplain Supplier#get()} caller and no value is memoized.
     *
     * @param original supplier
     * @param <T> the type of results supplied by the returned supplier
     */
    public static <T> Supplier<T> memoizedSupplier(Supplier<? extends T> original) {
        Objects.requireNonNull(original);
        return new SimpleMemoizedSupplier<>(original);
    }

    /**
     * {@return a new atomic, thread-safe, stable, non-blocking, lazily computed
     * {@linkplain IntFunction} that, for each allowed distinct input value, records
     * the value of the provided {@code original} IntFunction upon being first accessed
     * via {@linkplain IntFunction#apply(int)}}
     * <p>
     * Only one (witness) value per input value is elected as a memoized value even
     * though the provided {@code original} IntFunction might be invoked simultaneously
     * for the same input value by multiple threads.
     * <p>
     * If the {@code original} IntFunction invokes the returned IntFunction recursively
     * for the same input value, a StackOverflowError will be thrown when the returned
     * IntFunction's {@linkplain IntFunction#apply(int)}} method is invoked.
     * <p>
     * If the provided {@code original} IntFunction throws an exception, it is relayed
     * to the initial {@linkplain IntFunction#apply(int)} caller and no value is memoized.
     * <p>
     * The returned IntFunction will throw {@linkplain IndexOutOfBoundsException} if
     * {@linkplain IntFunction#apply(int)} is invoked with a value {@code < 0 || > size}.
     *
     * @param length   the number of input values. Allowed inputs are [0, length)
     * @param original the original IntFunction to convert to a memoized IntFunction
     * @param <R>      the return type of the IntFunction
     */
    public static <R> IntFunction<R> memoizedIntFunction(int length,
                                                         IntFunction<? extends R> original) {
        if (length < 0) {
            throw new IllegalArgumentException();
        }
        Objects.requireNonNull(original);
        return new MemoizedIntFunction<>(original, StableArray.of(length));
    }

    /**
     * {@return a new atomic, thread-safe, stable, non-blocking, lazily computed
     * {@linkplain Function} that, for each allowed distinct input value, records
     * the value of the provided {@code original} Function upon being first accessed
     * via {@linkplain Function#apply(Object)}}
     * <p>
     * Only one (witness) value per input value is elected as a memoized value even
     * though the provided {@code original} Function might be invoked simultaneously
     * for the same input value by multiple threads.
     * <p>
     * If the {@code original} Function invokes the returned Function recursively for
     * the same input value, a StackOverflowError will be thrown when the returned
     * Function's {@linkplain Function#apply(Object)}} method is invoked.
     * <p>
     * If the provided {@code original} IntFunction throws an exception, it is relayed
     * to the initial {@linkplain IntFunction#apply(int)} caller and no value is memoized.
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
    public static <T, R> Function<T, R> memoizedFunction(Set<T> inputs,
                                                         Function<? super T, ? extends R> original) {
        Objects.requireNonNull(inputs);
        Objects.requireNonNull(original);
        return MemoizedFunction.of(inputs, original);
    }

}
