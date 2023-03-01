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
package java.util.concurrent.lazy;

import jdk.internal.util.lazy.StandardLazyReferenceArray;
import jdk.internal.vm.annotation.Stable;

import java.util.Objects;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * An object reference array in which the values are lazily and atomically computed.
 * <p>
 * It is guaranteed that just at most one mapper is invoked and that
 * that mapper (if any) is invoked just once per slot LazyReferenceArray instance provided
 * a value is sucessfully computed. More formally, at most one sucessfull invocation per slot is
 * made of any provided set of mappers.
 * <p>
 * This contrasts to {@link java.util.concurrent.atomic.AtomicReferenceArray } where any number of updates can be done
 * and where there is no simple way to atomically compute
 * a value (guaranteed to only be computed once) if missing.
 * <p>
 * The implementation is optimized for the case where there are N invocations
 * trying to obtain a slot value and where N >> 1, for example where N is > 2<sup>20</sup>.
 * <p>
 * This class is thread-safe.
 * <p>
 * The JVM may apply certain optimizations as it knows the value is updated just once
 * at most as described by {@link Stable}.
 *
 * @param <V> The type of the values to be recorded
 */
sealed public interface LazyReferenceArray<V> extends IntFunction<V> permits StandardLazyReferenceArray {

    /**
     * {@return the length of the array}.
     */
    public int length();

    /**
     * Returns the present value at the provided {@code index} or, if no present value exists,
     * atomically attempts to compute the value using the <em>pre-set {@linkplain #of(int, IntFunction)} mapper}</em>.
     * If no pre-set {@linkplain #of(int, IntFunction)} mapper} exists,
     * throws an IllegalStateException exception.
     * <p>
     * If the pre-set mapper itself throws an (unchecked) exception, the
     * exception is rethrown, and no value is recorded. The most
     * common usage is to construct a new object serving as a memoized result, as in:
     *
     * {@snippet lang = java:
     *    LazyReferenceArray<V> lazy = LazyReferenceArray.of(Value::new);
     *    // ...
     *    V value = lazy.apply(42);
     *    assertNotNull(value); // Value is non-null
     *}
     *<p>
     * If another thread attempts to compute the value, the current thread will be suspended until
     * the atempt completes (successfully or not).
     *
     * @param index to the slot to be used
     * @return the value (pre-existing or newly computed)
     * @throws ArrayIndexOutOfBoundsException if the provided {@code index} is {@code < 0}
     *                                        or {@code index >= length()}
     * @throws NullPointerException           if the pre-set mapper returns {@code null}.
     * @throws IllegalStateException          if a value was not already present and no
     *                                        pre-set mapper was specified.
     */
    @Override
    V apply(int index);

    /**
     * {@return a snapshot of the present value at the provided {@code index} or {@code null} if no such value is present}.
     * <p>
     * No attempt is made to compute a value if it is not already present.
     * <p>
     * This method can be wrapped into an {@link Optional} for functional composition and more:
     * {@snippet lang = java:
     *     Optional.ofNullable(lazy.getOrNull(42))
     *         .map(Logic::computeResult)
     *         .ifPresentOrElse(Presentation::renderResult, Presentation::showFailure);
     * }
     * @param index to the slot to be used
     * @throws ArrayIndexOutOfBoundsException if the provided {@code index} is {@code < 0}
     *                                        or {@code index >= length()}
     */
    V getOrNull(int index);

    /**
     * Returns the present value at the provided {@code index} or, if no present value exists,
     * atomically attempts to compute the value using the <em>provided {@code mappper}</em>.
     *
     * <p>If the mapper returns {@code null}, an exception is thrown.
     * If the provided {@code ,mapper} itself throws an (unchecked) exception, the
     * exception is rethrown, and no value is recorded.  The most
     * common usage is to construct a new object serving as a memoized result, as in:
     *
     * {@snippet lang = java:
     *    LazyReference<V> lazy = LazyReferenceArray.ofEmpty();
     *    // ...
     *    V value = lazy.supplyIfAbsent(42, Value::new);
     *    assertNotNull(value); // Value is non-null
     * }
     * <p>
     * If another thread attempts to compute the value, the current thread will be suspended until
     * the atempt completes (successfully or not).
     *
     * @param index to the slot to be used
     * @param mappper to apply if no previous value exists
     * @return the value (pre-existing or newly computed)
     * @throws NullPointerException if the provided {@code mappper} is {@code null} or
     *                              the provided {@code mappper} returns {@code null}.
     */
    V computeIfEmpty(int index,
                     IntFunction<? extends V> mappper);

    /**
     * {@return a new empty LazyReferenceArray with no pre-set mapper}.
     * <p>
     * If an attempt is made to invoke the {@link #apply(int)} ()} method when no element is present,
     * an exception will be thrown.
     * <p>
     * {@snippet lang = java:
     *    LazyReferenceArray<V> lazy = LazyReferenceArray.ofEmpty();
     *    V value = lazy.getOrNull(42);
     *    assertIsNull(value); // Value is initially null
     *    // ...
     *    V value = lazy.supplyIfEmpty(42, Value::new);
     *    assertNotNull(value); // Value is non-null
     *}
     * @param <T> The type of the values
     * @param size the size of the array
     */
    public static <T> LazyReferenceArray<T> of(int size) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        return new StandardLazyReferenceArray<>(size,null);
    }

    /**
     * {@return a new empty LazyReferenceArray with a pre-set mapper}.
     * <p>
     * If an attempt is made to invoke the {@link #apply(int)} ()} method when no element is present,
     * the provided {@code presetMapper} will automatically be invoked as specified by
     * {@link #computeIfEmpty(int, IntFunction)}.
     * <p>
     * {@snippet lang = java:
     *    LazyReferenceArray<V> lazy = LazyReferenceArray.of(Value::new);
     *    // ...
     *    V value = lazy.get(42);
     *    assertNotNull(value); // Value is never null
     *}
     * @param <T> The type of the values
     * @param size the size of the array
     * @param presetMapper to invoke when lazily constructing a value
     * @throws NullPointerException if the provided {@code presetMapper} is {@code null}
     */
    public static <T> LazyReferenceArray<T> of(int size,
                                          IntFunction<? extends T> presetMapper) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        Objects.requireNonNull(presetMapper);
        return new StandardLazyReferenceArray<>(size, presetMapper);
    }

}
