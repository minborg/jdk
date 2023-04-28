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

import jdk.internal.javac.PreviewFeature;
import jdk.internal.util.concurrent.lazy.PreEvaluatedLazyArray;
import jdk.internal.util.concurrent.lazy.PreEvaluatedLazyValue;
import jdk.internal.util.concurrent.lazy.StandardLazyArray;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A lazy array with a pre-set mapper which will be invoked at most once (if successful),
 * per element, for example when {@link LazyArray#get(int) get(index)} is invoked.
 *
 * @param <V> The type of the values to be recorded
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.LAZY)
public sealed interface LazyArray<V>
        permits StandardLazyArray, PreEvaluatedLazyArray {

    /**
     * {@return the length of the array}
     */
    public int length();

    /**
     * {@return {@code true} if a value is bound at the provided {@code index}}
     *
     * @param index of the element to be used
     * @throws ArrayIndexOutOfBoundsException if {@code index < 0} or {@code index >= length()}
     */
    boolean isBound(int index);

    /**
     * {@return the bound value at the provided {@code index}. If no value is bound, atomically attempts
     * to compute and record a bound value using the <em>pre-set {@linkplain LazyArray#of(int, IntFunction) mapper}</em>}
     * <p>
     * If the pre-set mapper returns {@code null}, no value is bound and {@code null} is returned.
     * If the mapper itself throws an (unchecked) exception, the
     * exception is wrapped into a NoSuchElementException which is thrown, and no value is bound.
     * <p>
     * The most common usage is to construct a new object serving as a memoized result, as in:
     * <p>
     * {@snippet lang = java:
     *    LazyArray<V> lazy = LazyValue.ofArray(64, Value::new);
     *    // ...
     *    V value = lazy.get(42);
     *    assertNotNull(value); // Value is non-null
     *}
     * <p>
     * If another thread attempts to bind a value, the current thread will be suspended until
     * the attempt completes (successfully or not).  Otherwise, this method is guaranteed to be lock-free.
     *
     * @param index for which a bound value shall be obtained.
     * @throws ArrayIndexOutOfBoundsException if {@code index < 0} or {@code index >= length()}
     * @throws NoSuchElementException         if a value cannot be bound
     * @throws IllegalStateException          if a circular dependency is detected (I.e. a lazy value calls itself).
     */
    public V get(int index);

    /**
     * {@return the bound value at the provided {@code index}.  If no value is bound, atomically attempts
     * to compute and record a bound value using the <em>pre-set {@linkplain LazyArray#of(int, IntFunction) mapper}}</em>
     * , or, if this fails, returns the provided {@code other} value}
     * <p>
     * If another thread attempts to bind a value, the current thread will be suspended until
     * the attempt completes (successfully or not).  Otherwise, this method is guaranteed to be lock-free.
     *
     * @param index for which a value shall be obtained.
     * @param other to use if no value neither is bound nor can be bound (may be null)
     * @throws ArrayIndexOutOfBoundsException if {@code index< 0} or {@code index >= length()}
     * @throws IllegalStateException          if a circular dependency is detected (I.e. a lazy value calls itself).
     */
    V orElse(int index,
             V other);

    /**
     * {@return the bound value at the provided {@code index}. If no value is bound, atomically attempts
     * to compute and record a bound value using the <em>pre-set {@linkplain LazyArray#of(int, IntFunction) mapper}</em>
     * , or, if this fails, throws an exception produced by the provided {@code exceptionSupplier} function}
     * <p>
     * If another thread attempts to bind a value, the current thread will be suspended until
     * the attempt completes (successfully or not).  Otherwise, this method is guaranteed to be lock-free.
     *
     * @param <X>               the type of the exception that may be thrown
     * @param index             for which the value shall be obtained.
     * @param exceptionSupplier the supplying function that produces the exception to throw
     * @throws ArrayIndexOutOfBoundsException if {@code index< 0} or {@code index >= length()}
     * @throws X                              if a value cannot be bound.
     */
    public <X extends Throwable> V orElseThrow(int index,
                                               Supplier<? extends X> exceptionSupplier) throws X;

    /**
     * {@return A Stream with the bound values in this lazy array. If a value is not bound, atomically attempts
     * to compute and record a bound value using the <em>pre-set {@linkplain LazyArray#of(int, IntFunction) mapper}</em>
     * , or, if this fails, throws an Exception}
     * <p>
     * In other words, the returned stream is equivalent to the following code:
     * {@snippet lang = java:
     *     Stream<V> stream() {
     *         return IntStream.range(0, length())
     *                 .mapToObj(this::get);
     *     }
     * }
     *
     * @throws NoSuchElementException if a value cannot be bound
     * @throws IllegalStateException  if a circular dependency is detected (I.e. a lazy value calls itself).
     */
    public Stream<V> stream();

    /**
     * {@return A Stream with the bound values in this lazy array. If a value is not bound, atomically attempts
     * to compute and record a bound value using the <em>pre-set {@linkplain LazyArray#of(int, IntFunction) mapper}</em>
     * , or, if this fails, returns the provided {@code other} value}
     * <p>
     * In other words, the returned stream is equivalent to the following code:
     * {@snippet lang = java:
     *     Stream<V> stream(V other) {
     *         return IntStream.range(0, length())
     *                 .mapToObj(i -> orElse(i, other));
     *     }
     * }
     *
     * @param other the other value to use for values that cannot be bound (may be null)
     * @throws IllegalStateException if a circular dependency is detected (I.e. a lazy value calls itself).
     */
    public Stream<V> stream(V other);

    /**
     * {@return a new LazyArray with a pre-set mapper}
     * <p>
     * Below, an example of how to cache values in an array is shown:
     * {@snippet lang = java:
     *     class DemoArray {
     *
     *         private static final LazyArray<Value> VALUE_PO2_CACHE =
     *                 LazyValue.ofArray(32, index -> new Value(1L << index));
     *
     *         public Value powerOfTwoValue(int n) {
     *             if (n < 0 || n >= VALUE_PO2_CACHE.length()) {
     *                 throw new IllegalArgumentException(Integer.toString(n));
     *             }
     *
     *             return VALUE_PO2_CACHE.get(n);
     *         }
     *     }
     *}
     *
     * @param <V>          The type of the values
     * @param size         the size of the array
     * @param presetMapper to invoke when lazily constructing and binding values
     */
    public static <V> LazyArray<V> of(int size,
                                      IntFunction<? extends V> presetMapper) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        Objects.requireNonNull(presetMapper);
        return new StandardLazyArray<>(size, presetMapper);
    }

    /**
     * {@return a pre-evaluated LazyArray with bound values}
     *
     * @param <V>    The type of the values
     * @param values to bind
     */
    @SuppressWarnings("unchecked")
    public static <V> LazyArray<V> of(V... values) {
        Objects.requireNonNull(values);
        return new PreEvaluatedLazyArray<>(values);
    }

}
