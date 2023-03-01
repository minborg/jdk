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

import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * TO BE REMOVED. JUST USED FOR BENCHMARK COMPARISON.
 *
 * An object reference in which the value is lazily and atomically computed.
 * <p>
 * It is guaranteed that just at most one supplier is invoked and that
 * that supplier (if any) is invoked just once per LazyReference instance provided
 * a value is sucessfully computed. More formally, at most one sucessfull invocation is
 * made of any provided set of suppliers.
 * <p>
 * This contrasts to {@link AtomicReference } where any number of updates can be done
 * and where there is no simple way to atomically compute
 * a value (guaranteed to only be computed once) if missing.
 * <p>
 * The implementation is optimized for the case where there are N invocations
 * trying to obtain the value and where N >> 1, for example where N is > 2<sup>20</sup>.
 * <p>
 * This class is thread-safe.
 * <p>
 * The JVM may apply certain optimizations as it knows the value is updated just once
 * at most as described by {@link Stable}.
 *
 * @param <V> The type of the value to be recorded
 */
public final class LazyReference2<V> implements Supplier<V> {


    private Supplier<? extends V> presetSupplier;
    @Stable
    private Object value;

    static final VarHandle VALUE_VH;

    static {
        try {
            VALUE_VH = MethodHandles.lookup()
                    .findVarHandle(LazyReference2.class, "value", Object.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private LazyReference2(Supplier<? extends V> presetSupplier) {
        this.presetSupplier = presetSupplier;
    }

    /**
     * Returns the present value or, if no present value exists, atomically attempts
     * to compute the value using the <em>pre-set {@linkplain #of(Supplier)} supplier}</em>.
     * If no pre-set {@linkplain #of(Supplier)} supplier} exists,
     * throws an IllegalStateException exception.
     * <p>
     * If the pre-set supplier itself throws an (unchecked) exception, the
     * exception is rethrown, and no value is recorded. The most
     * common usage is to construct a new object serving as a memoized result, as in:
     *
     * {@snippet lang = java:
     *    LazyReference2<V> lazy = LazyReference2.of(Value::new);
     *    // ...
     *    V value = lazy.get();
     *    assertNotNull(value); // Value is non-null
     *}
     *<p>
     * If another thread attempts to compute the value, the current thread will be suspended until
     * the atempt completes (successfully or not).
     *
     * @return the value (pre-existing or newly computed)
     * @throws NullPointerException   if the pre-set supplier returns {@code null}.
     * @throws IllegalStateException  if a value was not already present and no
     *                                pre-set supplier was specified.
     */
    public V get() {
        V value;
        return (value = getAcquire()) == null
                ? supplyIfEmpty0(presetSupplier)
                : value;
    }

    /**
     * {@return a snapshot of the present value or {@code null} if no such value is present}.
     * <p>
     * No attempt is made to compute a value if it is not already present.
     * <p>
     * This method can be wrapped into an {@link Optional} for functional composition and more:
     * {@snippet lang = java:
     *     Optional.ofNullable(lazy.getOrNull())
     *         .map(Logic::computeResult)
     *         .ifPresentOrElse(Presentation::renderResult, Presentation::showFailure);
     * }
     */
    public V getOrNull() {
        return getAcquire();
    }

    /**
     * Returns the present value or, if no present value exists, atomically attempts
     * to compute the value using the <em>provided {@code supplier}</em>.
     *
     * <p>If the supplier returns {@code null}, an exception is thrown.
     * If the provided {@code supplier} itself throws an (unchecked) exception, the
     * exception is rethrown, and no value is recorded.  The most
     * common usage is to construct a new object serving as a memoized result, as in:
     *
     * {@snippet lang = java:
     *    LazyReference2<V> lazy = LazyReference2.ofEmpty();
     *    // ...
     *    V value = lazy.supplyIfAbsent(Value::new);
     *    assertNotNull(value); // Value is non-null
     *}
     * <p>
     * If another thread attempts to compute the value, the current thread will be suspended until
     * the atempt completes (successfully or not).
     *
     * @param supplier to apply if no previous value exists
     * @return the value (pre-existing or newly computed)
     * @throws NullPointerException if the provided {@code supplier} is {@code null} or
     *                              the provided {@code supplier} returns {@code null}.
     */
    public final V supplyIfEmpty(Supplier<? extends V> supplier) {
        Objects.requireNonNull(supplier);
        return supplyIfEmpty0(supplier);
    }

    private final V supplyIfEmpty0(Supplier<? extends V> supplier) {
        V value = getAcquire();
        if (value == null) {
            synchronized (this) {
                value = getAcquire();
                if (value == null) {
                    if (supplier == null) {
                        throw new IllegalArgumentException("No pre-set supplier specified.");
                    }
                    value = supplier.get();
                    if (value == null) {
                        throw new NullPointerException("The supplier returned null: " + supplier);
                    }
                    setRelease(value);
                    forgetPresetSupplier();
                }
            }
        }
        return value;
    }


    @Override
    public final String toString() {
        var v = getAcquire();
        return v != null
                ? ("LazyReference[" + v + "]")
                : "LazyReference.empty";
    }

    void forgetPresetSupplier() {
        // Stops preventing the supplier from being collected once it has been
        // used (if initially set).
        this.presetSupplier = null;
    }

    // Use acquire/release to ensure happens-before so that newly
    // constructed elements are always observed correctly in combination
    // with double-checked locking.
    V getAcquire() {
        return (V) VALUE_VH.getAcquire(this);
    }

    void setRelease(Object value) {
        VALUE_VH.setRelease(this, value);
    }

    /**
     * {@return a new empty LazyReference with no pre-set supplier}.
     * <p>
     * If an attempt is made to invoke the {@link #get()} method when no element is present,
     * an exception will be thrown.
     * <p>
     * {@snippet lang = java:
     *    LazyReference2<V> lazy = LazyReference2.ofEmpty();
     *    V value = lazy.getOrNull();
     *    assertIsNull(value); // Value is initially null
     *    // ...
     *    V value = lazy.supplyIfEmpty(Value::new);
     *    assertNotNull(value); // Value is non-null
     *}
     * @param <T> The type of the value
     */
    public static <T> LazyReference2<T> ofEmpty() {
        return new LazyReference2<>(null);
    }

    /**
     * {@return a new empty LazyReference with a pre-set supplier}.
     * <p>
     * If an attempt is made to invoke the {@link #get()} method when no element is present,
     * the provided {@code presetSupplier} will automatically be invoked as specified by
     * {@link #supplyIfEmpty(Supplier)}.
     * <p>
     * {@snippet lang = java:
     *    LazyReference2<V> lazy = LazyReference2.of(Value::new);
     *    // ...
     *    V value = lazy.get();
     *    assertNotNull(value); // Value is never null
     *}
     * @param <T> The type of the value
     * @param presetSupplier to invoke when lazily constructing a value
     * @throws NullPointerException if the provided {@code presetSupplier} is {@code null}
     */
    public static <T> LazyReference2<T> of(Supplier<? extends T> presetSupplier) {
        Objects.requireNonNull(presetSupplier);
        return new LazyReference2<>(presetSupplier);
    }

}
