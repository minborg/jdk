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
import jdk.internal.util.concurrent.constant.MapElementComputedConstant;
import jdk.internal.util.concurrent.constant.PreEvaluatedComputedConstant;
import jdk.internal.util.concurrent.constant.StandardComputedConstant;
import jdk.internal.util.concurrent.constant.StandardConstant;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A constant which can be set only once and then queried later to provide a bound value,
 * for example when {@link Constant#get() get()} is invoked.
 * <p>
 * Once bound (or if it failed to bound), Constant instances are guaranteed to
 * be lock free and are eligible for constant folding optimizations by the JVM.
 *
 * @param <V> The type of the value to be bound
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.COMPUTED_CONSTANTS)
public sealed interface Constant<V>
        extends Supplier<V>, ConstantPredicates
        permits StandardConstant {

    /**
     * {@inheritDoc}
     */
    boolean isUnbound();

    /**
     * {@inheritDoc}
     */
    boolean isBinding();

    /**
     * {@inheritDoc}
     */
    boolean isBound();

    /**
     * {@inheritDoc}
     */
    boolean isError();

    /**
     * {@return the bound value of this constant or throws a NoSuchElementException if no value is bound}
     * <p>
     * The most common usage is to construct a new object serving as a memoized result, as in:
     * <p>
     * {@snippet lang = java:
     *    Constant<V> constant = Constant.ofUnbound();
     *    constant.bind(new Value());
     *    // ...
     *    V value = constant.get();
     *    assertNotNull(value); // Value is non-null
     *}
     * <p>
     * If a thread calls this method while being bound by another thread, the current thread will be suspended until
     * the binding completes (successfully or not).  Otherwise, this method is guaranteed to be lock-free.
     *
     * @throws NoSuchElementException if a value is not bound
     */
    @Override
    V get();

    /**
     * {@return the bound value of this constant or {@code other} if no value is bound.}
     * <p>
     * If a thread calls this method while being bound by another thread, the current thread will be suspended until
     * the binding completes (successfully or not).  Otherwise, this method is guaranteed to be lock-free.
     *
     * @param other to use if no value is bound (can be null)
     */
    V orElse(V other);

    /**
     * {@return the bound value of this constant or throws an exception by invoking
     * the provided {@code exceptionSupplier}}
     * <p>
     * If a thread calls this method while being bound by another thread, the current thread will be suspended until
     * the binding completes (successfully or not).  Otherwise, this method is guaranteed to be lock-free.
     *
     * @param <X>               the type of the exception that may be thrown
     * @param exceptionSupplier the supplying function that produces the exception to throw
     * @throws X                if a value is not bound.
     */
    <X extends Throwable> V orElseThrow(Supplier<? extends X> exceptionSupplier) throws X;

    /**
     * Atomically binds the value of this constant to the provided {@code value}.
     * <p>
     * If a thread calls this method while being bound by another thread, the current thread will be suspended until
     * the binding completes (successfully or not).
     *
     * @param value to bind
     * @throws IllegalStateException if a value is already bound or a previous attempt was made to bind a value
     */
    void bind(V value);

    /**
     * {@return the bound value of this constant. If no value is bound, atomically attempts to
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
     *    Constant<V> constant = Constant.ofUnbound();
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
     * {@return a new empty {@link Constant } with no pre-set supplier}
     * <p>
     * If a later attempt is made to invoke the {@link Constant#get()} method when no element is bound,
     * a {@link NoSuchElementException} will be thrown.
     * <p>
     * {@snippet lang = java:
     *     class DemoSet {
     *
     *         private static final Constant<Foo> FOO = Constant.ofUnbound();
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
    static <V> Constant<V> ofUnbound() {
        return StandardConstant.ofUnbound();
    }

    /**
     * {@return a pre-evaluated {@link Constant } with the provided {@code value} bound}
     *
     * @param <V>   The type of the value
     * @param value to bind (can be {@code null})
     */
    static <V> Constant<V> of(V value) {
        return StandardConstant.of(value);
    }

    /**
     * {@return a new unmodifiable List of {@link Constant } elements with the provided
     * {@code size}}
     * <p>
     * The List and its elements are eligible for constant folding optimizations by the JVM.
     * <p>
     * Below, an example of how to cache values in a list is shown:
     * {@snippet lang = java:
     *     class DemoList {
     *
     *         private static final List<Constant<Long>> PO2_CACHE = Constant.ofList(32);
     *
     *         public long powerOfTwoValue(int n) {
     *             return PO2_CACHE.get(n).computeIfUnbound(index -> 1L << index);
     *         }
     *     }
     *}
     *
     * @param <V>          the type of the values
     * @param size         the size of the List
     */
    static <V> List<Constant<V>> ofList(int size) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        // Todo: Create a lazy populated list
        return IntStream.range(0, size)
                .mapToObj(__ -> Constant.<V>ofUnbound())
                .toList();
    }

    /**
     * {@return a new unmodifiable Map of {@link Constant } values with the provided
     * {@code keys} and provided {@code presetMapper}}
     * <p>
     * The Map and its values are eligible for constant folding optimizations by the JVM.
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
     */
    static <K, V> Map<K, Constant<V>> ofMap(Collection<K> keys) {
        Objects.requireNonNull(keys);
        // Todo: Create a lazy populated list
        return keys.stream()
                .collect(Collectors.toUnmodifiableMap(Function.identity(), __ -> Constant.ofUnbound()));
    }

}
