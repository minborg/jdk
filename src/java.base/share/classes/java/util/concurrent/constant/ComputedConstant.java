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
     * {@return {@code true} if no attempt has been made to bind a value to this constant}
     */
    boolean isUnbound();

    /**
     * {@return {@code true} if a thread is in the process of binding a value to this constant
     * but the outcome of the computation is not yet known}
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
     * {@return a new unmodifiable List of {@link ComputedConstant } elements with the provided
     * {@code size} and provided {@code presetMapper}}
     * <p>
     * The List and its elements are eligible for constant folding optimizations by the JVM.
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

}
