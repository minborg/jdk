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

package java.util.concurrent.constant;

import jdk.internal.javac.PreviewFeature;
import jdk.internal.util.concurrent.constant.IntComputedConstantList;
import jdk.internal.util.concurrent.constant.ComputedConstantList;
import jdk.internal.util.concurrent.constant.ListElementComputedConstant;
import jdk.internal.util.concurrent.constant.MapElementComputedConstant;
import jdk.internal.util.concurrent.constant.OnDemandComputedConstantList;
import jdk.internal.util.concurrent.constant.PreEvaluatedComputedConstant;
import jdk.internal.util.concurrent.constant.AbstractComputedConstant;
import jdk.internal.util.concurrent.constant.StandardComputedConstant;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * A computed constant which can be queried later to provide a bound value,
 * for example when {@link ComputedConstant#get() get()} is invoked.
 * <p>
 * Once bound (or if it failed to bound), computed constant instances are guaranteed to
 * be lock free and are eligible for constant folding optimizations by the JVM.
 *
 * @param <V> The type of the value to be bound
 * @sealedGraph
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.COMPUTED_CONSTANTS)
public sealed interface ComputedConstant<V>
        extends Supplier<V>
        permits AbstractComputedConstant,
        ListElementComputedConstant,
        MapElementComputedConstant,
        PreEvaluatedComputedConstant,
        StandardComputedConstant {

    /**
     * {@return {@code true} if no attempt has been made to bind a value}
     */
    boolean isUnbound();

    /**
     * {@return {@code true} if a thread is in the process of binding a value but
     * the outcome of the evaluation is not yet known}
     */
    boolean isBinding();

    /**
     * {@return {@code true} if a value is bound to this constant}
     */
    boolean isBound();

    /**
     * {@return {@code true} if an attempt was made to bind a value but
     * a value could not be bound to this constant}
     */
    boolean isError();

    /**
     * {@return the bound value of this computed constant. If no value is bound, atomically attempts
     * to compute and record a bound value using the <em>pre-set {@linkplain ComputedConstant#of(Supplier) supplier}</em> (if any)}
     * <p>
     * If no attempt to bind a value was made previously and no pre-set supplier exists, throws a NoSuchElementException.
     * If the pre-set supplier returns {@code null}, {@code null} is bound and returned.
     * If the pre-set supplier throws an (unchecked) exception, the exception is wrapped into
     * a {@link NoSuchElementException} which is thrown, and no value is bound.  If an Error
     * is thrown by the per-set supplier, the Error is relayed to the caller.  If an Exception
     * or an Error is thrown by the pre-set supplier, no further attempt is made to bind the value and all
     * subsequent invocations of this method will throw a new {@link NoSuchElementException}.
     * <p>
     * The most common usage is to construct a new object serving as a memoized result, as in:
     * <p>
     * {@snippet lang = java:
     *    ComputedConstant<V> constant = ComputedConstant.of(Value::new);
     *    // ...
     *    V value = constant.get();
     *    assertNotNull(value); // Value is non-null
     *}
     * <p>
     * If a thread calls this method while being bound by another thread, the current thread will be suspended until
     * the binding completes (successfully or not).  Otherwise, this method is guaranteed to be lock-free.
     *
     * @throws NoSuchElementException if a value cannot be bound
     * @throws StackOverflowError     if a circular dependency is detected (i.e. calls itself directly or
     *                                indirectly in the same thread).
     * @throws Error                  if the pre-set supplier throws an Error
     */
    @Override
    V get();

    /**
     * {@return the bound value of this computed constant.  If no value is bound, atomically attempts
     * to compute and record a bound value using the <em>pre-set {@linkplain ComputedConstant#of(Supplier) supplier}</em>
     * (if any), or, if the supplier throws an unchecked exception, returns the provided {@code other} value}
     * <p>
     * If a thread calls this method while being bound by another thread, the current thread will be suspended until
     * the binding completes (successfully or not).  Otherwise, this method is guaranteed to be lock-free.
     *
     * @param other to use if no value neither is bound nor can be bound (can be null)
     * @throws NoSuchElementException if a value cannot be bound
     * @throws StackOverflowError     if a circular dependency is detected (i.e. calls itself directly or
     *                                indirectly in the same thread).
     * @throws Error                  if the pre-set supplier throws an Error
     */
    V orElse(V other);

    /**
     * {@return the bound value of this computed constant. If no value is bound, atomically attempts
     * to compute and record a bound value using the <em>pre-set {@linkplain ComputedConstant#of(Supplier) supplier}</em>
     * (if any), or, if the supplier throws an unchecked exception, throws an exception produced by invoking the
     * provided {@code exceptionSupplier} function}
     * <p>
     * If a thread calls this method while being bound by another thread, the current thread will be suspended until
     * the binding completes (successfully or not).  Otherwise, this method is guaranteed to be lock-free.
     *
     * @param <X>               the type of the exception that may be thrown
     * @param exceptionSupplier the supplying function that produces the exception to throw
     * @throws X                if a value cannot be bound.
     * @throws Error            if the pre-set supplier throws an Error
     */
    <X extends Throwable> V orElseThrow(Supplier<? extends X> exceptionSupplier) throws X;

    /**
     * Atomically binds the value of this computed constant to the provided {@code value}.
     * <p>
     * If a thread calls this method while being bound by another thread, the current thread will be suspended until
     * the binding completes (successfully or not).
     *
     * @param value to bind
     * @throws IllegalStateException if a value is already bound or a previous attempt was made to bind a value
     */
    void bind(V value);

    /**
     * {@return the bound value of this computed constant. If no value is bound, atomically attempts to
     * compute and record a bound value using the provided {@code supplier}}
     * <p>
     * If the supplier returns {@code null}, {@code null} is bound and returned.
     * If the supplier throws an (unchecked) exception, the exception is wrapped into
     * a {@link NoSuchElementException} which is thrown, and no value is bound.  If an Error
     * is thrown by the supplier, the Error is relayed to the caller.  If an Exception
     * or an Error is thrown by the supplier, no further attempt is made to bind the value and all
     * subsequent invocations of this method will throw a new {@link NoSuchElementException}.
     * <p>
     * The most common usage is to construct a new object serving as a memoized result, as in:
     * <p>
     * {@snippet lang = java:
     *    ComputedConstant<V> constant = ComputedConstant.ofEmpty();
     *    // ...
     *    V value = constant.computeIfUnbound(Value::new);
     *    assertNotNull(value); // Value is non-null
     *}
     * <p>
     * If a thread calls this method while being bound by another thread, the current thread will be suspended until
     * the binding completes (successfully or not).  Otherwise, this method is guaranteed to be lock-free.
     *
     * @param supplier to invoke when computing a value
     * @throws NoSuchElementException if a value cannot be bound
     * @throws StackOverflowError     if a circular dependency is detected (i.e. calls itself directly or
     *                                indirectly in the same thread).
     * @throws Error                  if the supplier throws an Error
     */
    V computeIfUnbound(Supplier<? extends V> supplier);

    /**
     * {@return a new {@link ComputedConstant } that will use this computed constant's eventually bound value
     * and then apply the provided {@code mapper}}
     *
     * @param mapper to apply to this computed constant
     * @param <R>    the return type of the provided {@code mapper}
     */
    default <R> ComputedConstant<R> map(Function<? super V, ? extends R> mapper) {
        Objects.requireNonNull(mapper);
        return of(() -> mapper.apply(this.get()));
    }

    /**
     * {@return a new empty {@link ComputedConstant } with no pre-set supplier}
     * <p>
     * If a later attempt is made to invoke the {@link ComputedConstant#get()} method when no element is bound,
     * a {@link NoSuchElementException} will be thrown.
     * <p>
     * {@snippet lang = java:
     *     class DemoSet {
     *
     *         private static final ComputedConstant<Foo> FOO = ComputedConstant.ofEmpty();
     *
     *         public Foo theBar() {
     *             // Foo is lazily constructed and recorded here upon first invocation
     *             return FOO.computeIfUnbound(Foo::new);
     *         }
     *     }
     *}
     *
     * @param <V> The type of the value
     */
    static <V> ComputedConstant<V> ofEmpty() {
        return StandardComputedConstant.create();
    }

    /**
     * {@return a new {@link ComputedConstant } with the provided {@code presetSupplier}}
     * <p>
     * If a later attempt is made to invoke the {@link ComputedConstant#get()} method when no element is bound,
     * the provided {@code presetSupplier} will automatically be invoked.
     * <p>
     * {@snippet lang = java:
     *     class DemoPreset {
     *
     *         private static final ComputedConstant<Foo> FOO = ComputedConstant.of(Foo::new);
     *
     *         public Foo theBar() {
     *             // Foo is lazily constructed and recorded here upon first invocation
     *             return FOO.get();
     *         }
     *     }
     *}
     *
     * @param <V>            The type of the value
     * @param presetSupplier to invoke when computing a value
     */
    static <V> ComputedConstant<V> of(Supplier<? extends V> presetSupplier) {
        Objects.requireNonNull(presetSupplier);
        return StandardComputedConstant.create(presetSupplier);
    }

    /**
     * {@return a pre-evaluated {@link ComputedConstant } with the provided {@code value} bound}
     *
     * @param <V>   The type of the value (can be {@code null})
     * @param value to bind
     */
    static <V> ComputedConstant<V> of(V value) {
        return PreEvaluatedComputedConstant.create(value);
    }

    /**
     * This interface hides prototype methods that should not be a part of the API.
     */
    @PreviewFeature(feature = PreviewFeature.Feature.COMPUTED_CONSTANTS)
    interface Hidden {
        /**
         * {@return a new unmodifiable List of lazily evaluated elements with the provided
         * {@code size} and provided {@code presetMapper}}
         * <p>
         * Below, an example of how to cache values in a list is shown:
         * {@snippet lang = java:
         *     class DemoList {
         *
         *         private static final List<Long> PO2_CACHE =
         *                 ComputedConstant.ofActualList(32, index -> 1L << index);
         *
         *         public long powerOfTwoValue(int n) {
         *             return PO2_CACHE.get(n);
         *         }
         *     }
         *}
         *
         * @param <V>          the type of the values
         * @param size         the size of the List
         * @param presetMapper to invoke when computing and binding element values
         */
        static <V> List<V> ofActualList(int size,
                                        IntFunction<? extends V> presetMapper) {
            if (size < 0) {
                throw new IllegalArgumentException();
            }
            Objects.requireNonNull(presetMapper);
            return ComputedConstantList.create(size, presetMapper);
        }

        /**
         * {@return a new unmodifiable List of lazily evaluated elements with the provided
         * {@code size} and provided {@code presetMapper}}
         * <p>
         * Below, an example of how to cache values in a list is shown:
         * {@snippet lang = java:
         *     class DemoList {
         *
         *         private static final List<Long> PO2_CACHE =
         *                 ComputedConstant.ofActualList(32, index -> 1L << index);
         *
         *         public long powerOfTwoValue(int n) {
         *             return PO2_CACHE.get(n);
         *         }
         *     }
         *}
         *
         * @param <V>          the type of the values
         * @param type         a class to use for the backing array.
         * @param size         the size of the List
         * @param presetMapper to invoke when computing and binding element values
         */
        @SuppressWarnings("unchecked")
        static <V> List<V> ofActualList(Class<? super V> type,
                                        int size,
                                        IntFunction<? extends V> presetMapper) {
            Objects.requireNonNull(type);
            if (size < 0) {
                throw new IllegalArgumentException();
            }
            Objects.requireNonNull(presetMapper);
            if (type == int.class || type == Integer.class) {
                return (List<V>) IntComputedConstantList.create(size, (IntFunction<Integer>) presetMapper);
            }
            return ComputedConstantList.create(size, presetMapper);
        }
    }

    /**
     * {@return a new unmodifiable List of {@link ComputedConstant } elements with the provided
     * {@code size} and provided {@code presetMapper}}
     * <p>
     * Below, an example of how to cache values in a list is shown:
     * {@snippet lang = java:
     *     class DemoList {
     *
     *         private static final List<ComputedConstant<Long>> PO2_CACHE =
     *                 ComputedConstant.ofList(32, index -> 1L << index);
     *
     *         public long powerOfTwoValue(int n) {
     *             return PO2_CACHE.get(n);
     *         }
     *     }
     *}
     *
     * @param <V>          the type of the values
     * @param size         the size of the List
     * @param presetMapper to invoke when computing and binding element values
     */
    static <V> List<ComputedConstant<V>> ofList(int size,
                                                IntFunction<? extends V> presetMapper) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        Objects.requireNonNull(presetMapper);
        return OnDemandComputedConstantList.create(size, presetMapper);
    }

    /**
     * {@return a new unmodifiable Map of {@link ComputedConstant } values with the provided
     * {@code keys} and provided {@code presetMapper}}
     * <p>
     * Below, an example of how to cache values in a Map is shown:
     * {@snippet lang = java:
     *     class DemoMap {
     *
     *         private static final Map<Integer, ComputedConstant<User>> USER_ID_CACHE =
     *                 ComputedConstant.ofMap(List.of(0, 1, 1000), DB::findUserById);
     *
     *         public User userFromCache(int userId) {
     *             return USER_ID_CACHE.get(userId);
     *         }
     *     }
     *}
     *
     * @param <K>          the type of the keys
     * @param <V>          the type of the values
     * @param keys         the keys to associate with ComputedConstant instances
     * @param presetMapper to invoke when computing and binding element values
     */
    static <K, V> Map<K, ComputedConstant<V>> ofMap(Collection<K> keys,
                                                    Function<? super K, ? extends V> presetMapper) {
        Objects.requireNonNull(keys);
        Objects.requireNonNull(presetMapper);
        return keys.stream()
                .collect(Collectors.toUnmodifiableMap(Function.identity(), k -> MapElementComputedConstant.create(k, presetMapper)));
    }

}
