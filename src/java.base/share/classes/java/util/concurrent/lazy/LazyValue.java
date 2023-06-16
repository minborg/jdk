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
import jdk.internal.util.concurrent.lazy.ListElementLazyValue;
import jdk.internal.util.concurrent.lazy.PreEvaluatedLazyValue;
import jdk.internal.util.concurrent.lazy.AbstractLazyValue;
import jdk.internal.util.concurrent.lazy.StandardLazyValue;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * A lazy value with a pre-set supplier which can be invoked later to form a bound value,
 * for example when {@link LazyValue#get() get()} is invoked.
 *
 * @param <V> The type of the value to be bound
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.LAZY)
public sealed interface LazyValue<V>
        extends Supplier<V>
        permits AbstractLazyValue, ListElementLazyValue, PreEvaluatedLazyValue, StandardLazyValue {

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
     * {@return {@code true} if a value is bound to this lazy value}
     */
    boolean isBound();

    /**
     * {@return {@code true} if an attempt was made to bind a value but
     * a value could not be bound to this lazy value}
     */
    boolean isError();

    /**
     * {@return the bound value of this lazy value. If no value is bound, atomically attempts
     * to compute and record a bound value using the <em>pre-set {@linkplain LazyValue#of(Supplier) supplier}</em>}
     * <p>
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
     *    LazyValue<V> lazy = LazyValue.of(Value::new);
     *    // ...
     *    V value = lazy.get();
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
     * {@return the bound value of this lazy value.  If no value is bound, atomically attempts
     * to compute and record a bound value using the <em>pre-set {@linkplain LazyValue#of(Supplier) supplier}</em>, or,
     * if the supplier throws an unchecked exception, returns the provided {@code other} value}
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
     * {@return the bound value of this lazy value. If no value is bound, atomically attempts
     * to compute and record a bound value using the <em>pre-set {@linkplain LazyValue#of(Supplier) supplier}</em>, or,
     * if the supplier throws an unchecked exception, throws an exception produced by invoking the
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
     * {@return a {@link LazyValue} that will use this lazy value's eventually bound value
     * and then apply the provided {@code mapper}}
     *
     * @param mapper to apply to this lazy value
     * @param <R>    the return type of the provided {@code mapper}
     */
    default <R> LazyValue<R> map(Function<? super V, ? extends R> mapper) {
        Objects.requireNonNull(mapper);
        return of(() -> mapper.apply(this.get()));
    }

    /**
     * {@return a {@link LazyValue} with the provided {@code presetSupplier}}
     * <p>
     * If a later attempt is made to invoke the {@link LazyValue#get()} method when no element is bound,
     * the provided {@code presetSupplier} will automatically be invoked.
     * <p>
     * {@snippet lang = java:
     *     class DemoPreset {
     *
     *         private static final LazyValue<Foo> FOO = LazyValue.of(Foo::new);
     *
     *         public Foo theBar() {
     *             // Foo is lazily constructed and recorded here upon first invocation
     *             return FOO.get();
     *         }
     *     }
     *}
     *
     * @param <V>            The type of the value
     * @param presetSupplier to invoke when lazily constructing a value
     */
    static <V> LazyValue<V> of(Supplier<? extends V> presetSupplier) {
        Objects.requireNonNull(presetSupplier);
        return StandardLazyValue.create(presetSupplier);
    }

    /**
     * {@return a pre-evaluated {@link LazyValue} with the provided {@code value} bound}
     *
     * @param <V>   The type of the value (can be {@code null})
     * @param value to bind
     */
    static <V> LazyValue<V> of(V value) {
        return PreEvaluatedLazyValue.create(value);
    }

    /**
     * {@return a new unmodifiable List of {@link LazyValue} elements with the provided
     * {@code size} and provided {@code presetMapper}}
     * <p>
     * Below, an example of how to cache values in an array is shown:
     * {@snippet lang = java:
     *     class DemoArray {
     *
     *         private static final List<LazyValue<Long>> PO2_CACHE =
     *                 LazyValue.ofList(32, index -> 1L << index);
     *
     *         public long powerOfTwoValue(int n) {
     *             return PO2_CACHE.get(n);
     *         }
     *     }
     *}
     *
     * @param <V>          the type of the values
     * @param size         the size of the List
     * @param presetMapper to invoke when lazily constructing and binding element values
     */
    @SuppressWarnings("unchecked")
    static <V> List<LazyValue<V>> ofList(int size,
                                         IntFunction<? extends V> presetMapper) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        Objects.requireNonNull(presetMapper);
        return IntStream.range(0, size)
                .mapToObj(i -> ListElementLazyValue.create(i, presetMapper))
                .map(l -> (LazyValue<V>) l)
                .toList();
    }

}
