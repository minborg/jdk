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
import jdk.internal.util.concurrent.lazy.PreEvaluatedLazyValue;
import jdk.internal.util.concurrent.lazy.StandardLazyValue;

import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

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
        permits StandardLazyValue, PreEvaluatedLazyValue {

    /**
     * {@return {@code true} if a value is bound to this lazy value}
     */
    boolean isBound();

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
     * if this fails, returns the provided {@code other} value}
     * <p>
     * If a thread calls this method while being bound by another thread, the current thread will be suspended until
     * the binding completes (successfully or not).  Otherwise, this method is guaranteed to be lock-free.
     *
     * @param other to use if no value neither is bound nor can be bound (can be null)
     * @throws NoSuchElementException if a value cannot be bound
     * @throws StackOverflowError     if a circular dependency is detected (i.e. calls itself directly or
     *                                indirectly in the same thread).
     */
    V orElse(V other);

    /**
     * {@return the bound value of this lazy value. If no value is bound, atomically attempts
     * to compute and record a bound value using the <em>pre-set {@linkplain LazyValue#of(Supplier) supplier}</em>, or,
     * if this fails, throws an exception produced by invoking the provided {@code exceptionSupplier} function}
     * <p>
     * If a thread calls this method while being bound by another thread, the current thread will be suspended until
     * the binding completes (successfully or not).  Otherwise, this method is guaranteed to be lock-free.
     *
     * @param <X>               the type of the exception that may be thrown
     * @param exceptionSupplier the supplying function that produces the exception to throw
     * @throws X if a value cannot be bound.
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
        return new StandardLazyValue<>(presetSupplier);
    }

    /**
     * {@return a pre-evaluated {@link LazyValue} with the provided {@code value} bound}
     *
     * @param <V>   The type of the value (can be {@code null})
     * @param value to bind
     */
    static <V> LazyValue<V> of(V value) {
        return new PreEvaluatedLazyValue<>(value);
    }

    /**
     * {@return a {@link LazyValue} that will lazily compute the reduction of the
     * provided {@code first} and the provided {@code others} by successively and
     * lazily applying the provided {@code accumulator} function}.
     *
     * @param <V>         the value type
     * @param accumulator an associative stateless function for combining two inner lazy values
     * @param first       the first lazy value for the accumulating function
     * @param others      the other lazy values on which a reduction shall be performed
     */
    @SuppressWarnings("unchecked")
    static <V> LazyValue<V> reduce(BinaryOperator<V> accumulator,
                                   LazyValue<? extends V> first,
                                   Collection<LazyValue<? extends V>> others) {
        Objects.requireNonNull(accumulator);
        LazyValue<V> identity = (LazyValue<V>) Objects.requireNonNull(first);
        // This also checks for null
        List<LazyValue<? extends V>> list = List.copyOf(others);
        if (list.isEmpty()) {
            return identity;
        }
        return LazyValue.of(() ->
                others.stream()
                        .skip(1)
                        .map(l -> (LazyValue<V>) l)
                        .map(LazyValue::get)
                        .reduce(identity.get(), accumulator)
        );
    }

    /**
     * {@return a {@link LazyValue} that will lazily compute the reduction of the
     * provided {@code lazies} by successively and lazily applying the provided
     * {@code accumulator} function or {@linkplain Optional#empty()} if there are no
     * elements in {@code lazies}}.
     *
     * @param <V>         the value type
     * @param lazies      the lazy values on which a reduction shall be performed
     * @param accumulator an associative stateless function for combining two inner lazy values
     */
    static <V> Optional<LazyValue<V>> reduce(BinaryOperator<V> accumulator,
                                             Collection<LazyValue<? extends V>> lazies) {
        Objects.requireNonNull(accumulator);
        // This also checks for null
        List<LazyValue<? extends V>> list = List.copyOf(lazies);
        if (list.isEmpty()) {
            return Optional.empty();
        }
        LazyValue<? extends V> identity = list.getFirst();
        return Optional.of(reduce(accumulator, identity, list.subList(1, list.size())));
    }

    /**
     * {@return a {@link LazyValue} that will lazily compute the reduction of the
     * provided {@code first} and the provided {@code others} by successively and
     * lazily applying the provided {@code accumulator} function}
     *
     * @param <V>         the value type
     * @param first       the first lazy value for the accumulating function
     * @param others      the other lazy values on which a reduction shall be performed
     * @param accumulator an associative stateless function for combining two inner lazy values
     */
    @SuppressWarnings({"unchecked", "varargs"})
    @SafeVarargs // Creating a stream from a vararg is safe
    static <V> LazyValue<V> reduce(BinaryOperator<V> accumulator,
                                   LazyValue<? extends V> first,
                                   LazyValue<? extends V>... others) {
        Objects.requireNonNull(accumulator);
        Objects.requireNonNull(first);
        return LazyValue.of(() -> {
                    // Make sure we bind the lazies in the right order
                    V firstValue = first.get();
                    return Stream.of((LazyValue<V>[]) others)
                            .map(LazyValue::get)
                            .reduce(firstValue, accumulator);
                }
        );
    }

}
