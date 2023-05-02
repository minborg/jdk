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

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Supplier;

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
     * If the pre-set supplier returns {@code null}, no value is bound and {@code null} is returned.
     * If the mapping function itself throws an (unchecked) exception, the
     * exception is wrapped into a NoSuchElementException which is thrown, and no value is bound.
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
     * If another thread attempts to bind a value, the current thread will be suspended until
     * the attempt completes (successfully or not).  Otherwise, this method is guaranteed to be lock-free.
     *
     * @throws NoSuchElementException if a value cannot be bound
     * @throws IllegalStateException  if a circular dependency is detected (i.e. a lazy value calls itself).
     */
    @Override
    V get();

    /**
     * {@return the bound value of this lazy value.  If no value is bound, atomically attempts
     * to compute and record a bound value using the <em>pre-set {@linkplain LazyValue#of(Supplier) supplier}</em>, or,
     * if this fails, returns the provided {@code other} value}
     * <p>
     * If another thread attempts to bind a value, the current thread will be suspended until
     * the attempt completes (successfully or not).  Otherwise, this method is guaranteed to be lock-free.
     *
     * @param other to use if no value neither is bound nor can be bound (may be null)
     * @throws IllegalStateException  if a circular dependency is detected (i.e. a lazy value calls itself).
     */
    V orElse(V other);

    /**
     * {@return the bound value of this lazy value. If no value is bound, atomically attempts
     * to compute and record a bound value using the <em>pre-set {@linkplain LazyValue#of(Supplier) supplier}</em>, or,
     * if this fails, throws an exception produced by invoking the provided {@code exceptionSupplier} function}
     * <p>
     * If another thread attempts to bind a value, the current thread will be suspended until
     * the attempt completes (successfully or not).  Otherwise, this method is guaranteed to be lock-free.
     *
     * @param <X> the type of the exception that may be thrown
     * @param exceptionSupplier the supplying function that produces the exception to throw
     * @throws X if a value cannot be bound.
     */
    public <X extends Throwable> V orElseThrow(Supplier<? extends X> exceptionSupplier) throws X;

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
    public static <V> LazyValue<V> of(Supplier<? extends V> presetSupplier) {
        Objects.requireNonNull(presetSupplier);
        return new StandardLazyValue<>(presetSupplier);
    }

    /**
     * {@return a pre-evaluated {@link LazyValue} with the provided {@code value} bound}
     *
     * @param <V>   The type of the value
     * @param value to bind
     */
    @SuppressWarnings("unchecked")
    public static <V> LazyValue<V> of(V value) {
        Objects.requireNonNull(value);
        return new PreEvaluatedLazyValue<>(value);
    }

}
