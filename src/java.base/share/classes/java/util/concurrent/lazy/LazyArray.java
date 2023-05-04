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
import jdk.internal.util.concurrent.lazy.AbstractLazyArray;
import jdk.internal.util.concurrent.lazy.AbstractPreEvaluatedArray;
import jdk.internal.util.concurrent.lazy.DoubleLazyArray;
import jdk.internal.util.concurrent.lazy.IntLazyArray;
import jdk.internal.util.concurrent.lazy.LazyUtil;
import jdk.internal.util.concurrent.lazy.LongLazyArray;
import jdk.internal.util.concurrent.lazy.PreEvaluatedDoubleArray;
import jdk.internal.util.concurrent.lazy.PreEvaluatedIntArray;
import jdk.internal.util.concurrent.lazy.PreEvaluatedReferenceLazyArray;
import jdk.internal.util.concurrent.lazy.OptimizedReferenceLazyArray;
import jdk.internal.util.concurrent.lazy.PreEvaluatedLongArray;
import jdk.internal.util.concurrent.lazy.ReferenceLazyArray;

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
        permits AbstractLazyArray,
        AbstractPreEvaluatedArray,
        DoubleLazyArray,
        IntLazyArray,
        LongLazyArray,
        OptimizedReferenceLazyArray,
        PreEvaluatedDoubleArray,
        PreEvaluatedIntArray,
        PreEvaluatedLongArray,
        PreEvaluatedReferenceLazyArray,
        ReferenceLazyArray {

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
     * {@return the bound value at the provided {@code index}.  If no value is bound, atomically attempts
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
     * If another thread attempts to bind a value at the provided index, the current thread will be suspended until
     * the attempt completes (successfully or not).  Otherwise, this method is guaranteed to be lock-free.
     *
     * @param index for which a bound value shall be obtained.
     * @throws ArrayIndexOutOfBoundsException if {@code index < 0} or {@code index >= length()}
     * @throws NoSuchElementException         if a value cannot be bound
     * @throws IllegalStateException          if a circular dependency is detected (i.e. a lazy value calls itself
     *                                        for the same index).
     */
    public V get(int index);

    /**
     * {@return the bound value at the provided {@code index}.  If no value is bound, atomically attempts
     * to compute and record a bound value using the <em>pre-set {@linkplain LazyArray#of(int, IntFunction) mapper}</em>
     * , or, if this fails, returns the provided {@code other} value}
     * <p>
     * If another thread attempts to bind a value at the provided index, the current thread will be suspended until
     * the attempt completes (successfully or not).  Otherwise, this method is guaranteed to be lock-free.
     *
     * @param index for which a value shall be obtained.
     * @param other to use if no value neither is bound nor can be bound (may be null)
     * @throws ArrayIndexOutOfBoundsException if {@code index< 0} or {@code index >= length()}
     * @throws IllegalStateException          if a circular dependency is detected (i.e. a lazy value calls itself
     *                                        for the same index).
     */
    V orElse(int index,
             V other);

    /**
     * {@return the bound value at the provided {@code index}.  If no value is bound, atomically attempts
     * to compute and record a bound value using the <em>pre-set {@linkplain LazyArray#of(int, IntFunction) mapper}</em>
     * , or, if this fails, throws an exception produced by the provided {@code exceptionSupplier} function}
     * <p>
     * If another thread attempts to bind a value at the provided index, the current thread will be suspended until
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
     * {@return a Stream with the bound values in this lazy array.  If a value is not bound, atomically attempts
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
     * @throws IllegalStateException  if a circular dependency is detected (I.e. a lazy value
     *                                calls itself at a certain index).
     */
    public Stream<V> stream();

    /**
     * {@return a Stream with the bound values in this lazy array. If a value is not bound, atomically attempts
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
     * @throws IllegalStateException if a circular dependency is detected (i.e. a lazy value
     *                               calls itself at a certain index).
     */
    public Stream<V> stream(V other);

    /**
     * {@return a new {@link LazyArray} with the provided {@code length} and provided {@code presetmapper}}
     * <p>
     * Below, an example of how to cache values in an array is shown:
     * {@snippet lang = java:
     *     class DemoArray {
     *
     *         private static final LazyArray<Long> PO2_CACHE =
     *                 LazyValue.ofArray(32, index -> 1L << index);
     *
     *         public long powerOfTwoValue(int n) {
     *             return PO2_CACHE.get(n);
     *         }
     *     }
     *}
     *
     * @param <V>          the type of the values
     * @param length       the length of the array
     * @param presetMapper to invoke when lazily constructing and binding values
     */
    public static <V> LazyArray<V> of(int length,
                                      IntFunction<? extends V> presetMapper) {
        if (length < 0) {
            throw new IllegalArgumentException();
        }
        Objects.requireNonNull(presetMapper);
        return new ReferenceLazyArray<>(length, presetMapper);
    }

    /**
     * {@return a new {@link LazyArray} with the provided {@code length} and the provided {@code presetmapper}
     * where the elements are of the provided {@code elementType}}
     * <p>
     * Providing an {@code elementType} of the elements allows the method to select the most
     * sutable implementation for that type. For example, providing {@code long.class} might return a
     * LazyArray that is backed by an array of longs.
     * <p>
     * Below, an example of how to cache values in an array is shown:
     * {@snippet lang = java:
     *     class DemoArray {
     *
     *         private static final LazyArray<Long> PO2_CACHE =
     *                 LazyValue.ofArray(long.class, 32, index -> 1L << index);
     *
     *         public long powerOfTwoValue(int n) {
     *             return PO2_CACHE.get(n);
     *         }
     *     }
     *}
     *
     * @param <V>          The type of the values
     * @param elementType  The element type (e.g. int.class)
     * @param length       The length of the array
     * @param presetMapper to invoke when lazily constructing and binding values
     */
    @SuppressWarnings("unchecked")
    public static <V> LazyArray<V> of(Class<V> elementType,
                                      int length,
                                      IntFunction<? extends V> presetMapper) {
        Objects.requireNonNull(elementType);
        if (length < 0) {
            throw new IllegalArgumentException();
        }
        Objects.requireNonNull(presetMapper);


        return switch (elementType) {
            case Class<V> c when c == int.class || c == Integer.class ->
                    (LazyArray<V>) new IntLazyArray(length, (IntFunction<? extends Integer>) presetMapper);
            case Class<V> c when c == long.class || c == Long.class ->
                    (LazyArray<V>) new LongLazyArray(length, (IntFunction<? extends Long>) presetMapper);
            case Class<V> c when c == double.class || c == Double.class ->
                    (LazyArray<V>) new DoubleLazyArray(length, (IntFunction<? extends Double>) presetMapper);
            default -> new ReferenceLazyArray<>(length, presetMapper);
        };


    }

    /**
     * {@return a pre-evaluated {@code LazyArray} with the provided {@code values} bound}
     *
     * @param <V>    The type of the values
     * @param values to bind
     */
    @SuppressWarnings("unchecked")
    public static <V> LazyArray<V> of(V... values) {
        Objects.requireNonNull(values);
        return new PreEvaluatedReferenceLazyArray<>(values);
    }

    /**
     * {@return a pre-evaluated {@code LazyArray} with the provided {@code values} bound}
     * <p>
     * Providing an {@code elementType} of the elements allows the method to select the most
     * sutable implementation for that type. For example, providing {@code long.class} might return a
     * LazyArray that is backed by an array of longs.
     *
     * @param <V>         The type of the values
     * @param elementType The element type (e.g. int.class)
     * @param values      to bind
     * @throws IllegalArgumentException if the provided {@code values} is not of type array or if its
     *                                  elements are not identical to the {@code elementType}
     */
    @SuppressWarnings("unchecked")
    public static <V> LazyArray<V> of(Class<V> elementType,
                                      Object values) {
        Objects.requireNonNull(elementType);
        Objects.requireNonNull(values);
        if (!values.getClass().isArray()) {
            throw new IllegalArgumentException("Not an array" + values);
        }
        var componentType = values.getClass().componentType();
        if (!(componentType == elementType)) {
            throw new IllegalArgumentException("The provided element type is " + elementType + " but the array components are of type " + componentType);
        }

        return switch (elementType) {
            case Class<V> c when c == int.class ->
                    (LazyArray<V>) new PreEvaluatedIntArray((int[]) values);
            case Class<V> c when c == Integer.class ->
                    (LazyArray<V>) new PreEvaluatedIntArray((Integer[]) values);
            case Class<V> c when c == long.class ->
                    (LazyArray<V>) new PreEvaluatedLongArray((long[]) values);
            case Class<V> c when c == Long.class ->
                    (LazyArray<V>) new PreEvaluatedLongArray((Long[]) values);
            case Class<V> c when c == double.class ->
                    (LazyArray<V>) new PreEvaluatedDoubleArray((double[]) values);
            case Class<V> c when c == Long.class ->
                    (LazyArray<V>) new PreEvaluatedDoubleArray((Double[]) values);
            // Take care of the "unsupported" primitive types
            case Class<V> c when c == byte.class ->
                    (LazyArray<V>) new PreEvaluatedReferenceLazyArray<>((V[]) LazyUtil.toObjectArray((byte[]) values));
            case Class<V> c when c == boolean.class ->
                    (LazyArray<V>) new PreEvaluatedReferenceLazyArray<>((V[]) LazyUtil.toObjectArray((boolean[]) values));
            case Class<V> c when c == short.class ->
                    (LazyArray<V>) new PreEvaluatedReferenceLazyArray<>((V[]) LazyUtil.toObjectArray((short[]) values));
            case Class<V> c when c == char.class ->
                    (LazyArray<V>) new PreEvaluatedReferenceLazyArray<>((V[]) LazyUtil.toObjectArray((char[]) values));
            case Class<V> c when c == float.class ->
                    (LazyArray<V>) new PreEvaluatedReferenceLazyArray<>((V[]) LazyUtil.toObjectArray((float[]) values));
            // Here is the general case
            default -> new PreEvaluatedReferenceLazyArray<>((V[]) values);
        };
    }

}
