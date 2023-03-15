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

import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * An object reference in which the value can be lazily and atomically computed.
 * <p>
 * At most one invocation is made of any provided set of suppliers.
 * <p>
 * This contrasts to {@link AtomicReference } where any number of updates can be done
 * and where there is no simple way to atomically compute
 * a value (guaranteed to only be computed once) if missing.
 * <p>
 * The implementation is optimized for the case where there are N invocations
 * trying to obtain the value and where N >> 1, for example where N is > 2<sup>20</sup>.
 * <p>
 * A supplier may return {@code null} which then will be perpetually recorded as the value.
 * <p>
 * This class is thread-safe.
 * <p>
 * The JVM may apply certain optimizations as it knows the value is updated just once
 * at most as described by {@link Stable} as exemplified here:
 * {@snippet lang = java:
 *     private static final LazyReference<Value> MY_LAZY_VALUE = LazyReference.of(Value::new);
 *     // ...
 *     public Value value() {
 *         // This will likely be constant-folded by the JIT C2 compiler.
 *         return MY_LAZY_VALUE.get();
 *     }
 *}
 *
 * @param <V> The type of the value to be recorded
 */
public final class LazyReference<V>
        extends AbstractLazy<Supplier<? extends V>>
        implements Lazy, Supplier<V> {

    @Stable
    private Object value;

    private LazyReference(Supplier<? extends V> presetSupplier) {
        super(presetSupplier);
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
     * <p>
     * {@snippet lang = java:
     *    LazyReference<V> lazy = LazyReference.of(Value::new);
     *    // ...
     *    V value = lazy.get();
     *    assertNotNull(value); // Value is non-null
     *}
     * <p>
     * If another thread attempts to compute the value, the current thread will be suspended until
     * the atempt completes (successfully or not).
     *
     * @return the value (pre-existing or newly computed)
     * @throws NullPointerException   if the pre-set supplier returns {@code null}.
     * @throws IllegalStateException  if a value was not already present and no
     *                                pre-set supplier was specified.
     * @throws NoSuchElementException if a supplier has previously thrown an exception.
     */
    @SuppressWarnings("unchecked")
    public V get() {
        return isPresentPlain()
                ? (V) value
                : supplyIfEmpty0(presetProvider());
    }

    /**
     * Returns the present value or, if no present value exists, atomically attempts
     * to compute the value using the <em>provided {@code supplier}</em>.
     * <p>
     * If the provided {@code supplier} itself throws an (unchecked) exception, the
     * exception is rethrown, and no value is recorded.  The most
     * common usage is to construct a new object serving as a memoized result, as in:
     * <p>
     * {@snippet lang = java:
     *    LazyReference<V> lazy = LazyReference.ofEmpty();
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
     * @throws NullPointerException   if the provided {@code supplier} is {@code null}.
     * @throws NoSuchElementException if a supplier has previously thrown an exception.
     */
    public V supplyIfEmpty(Supplier<? extends V> supplier) {
        Objects.requireNonNull(supplier);
        return supplyIfEmpty0(supplier);
    }

    /**
     * {@return the excption thrown by the supplier invoked or
     * {@link Optional#empty()} if no exception was thrown}.
     */
    public Optional<Throwable> exception() {
        return is(State.ERROR)
                ? Optional.of((Throwable) value)
                : Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private V supplyIfEmpty0(Supplier<? extends V> supplier) {
        if (!isPresentPlain()) {
            // Synchronized implies acquire/release semantics when entering/leaving the monitor
            synchronized (this) {
                if (isPlain(State.ERROR)) {
                    throw new NoSuchElementException(exception().get());
                }
                if (!isPresentPlain()) {
                    if (supplier == null) {
                        throw new IllegalStateException("No pre-set supplier specified.");
                    }
                    try {
                        constructing(true);
                        V v = supplier.get();
                        if (v != null) {
                            value = v;
                        }
                        // Prevents reordering. Changes only go in one direction.
                        // https://developer.arm.com/documentation/102336/0100/Load-Acquire-and-Store-Release-instructions
                        stateValueRelease(State.PRESENT);
                    } catch (Throwable e) {
                        // Record the throwable instead of the value.
                        value = e;
                        // Prevents reordering.
                        stateValueRelease(State.ERROR);
                        // Rethrow
                        throw e;
                    } finally {
                        constructing(false);
                        forgetPresetProvided();
                    }
                }
            }
        }
        return (V) value;
    }

    @Override
    protected String renderValue() {
        return value.toString();
    }

    @Override
    protected String renderError() {
        return super.renderError() + "[" + value + "]";
    }

    /*
    private Object value() {
        if (state().isFinal()) {
            // If we can observe a final state we know
            // it is safe to observe the value direcly
            return value;
        }
        synchronized (this) {
            return value;
        }
    }*/

    /**
     * {@return a new empty LazyReference with no pre-set supplier}.
     * <p>
     * If an attempt is made to invoke the {@link #get()} method when no element is present,
     * an exception will be thrown.
     * <p>
     * {@snippet lang = java:
     *    LazyReference<T> lazy = LazyReference.ofEmpty();
     *    T value = lazy.getOrNull();
     *    assertIsNull(value); // Value is initially null
     *    // ...
     *    T value = lazy.supplyIfEmpty(Value::new);
     *    assertNotNull(value); // Value is non-null
     *}
     *
     * @param <T> The type of the value
     */
    public static <T> LazyReference<T> ofEmpty() {
        return new LazyReference<>(null);
    }

    /**
     * {@return a new empty LazyReference with a pre-set supplier}.
     * <p>
     * If an attempt is made to invoke the {@link #get()} method when no element is present,
     * the provided {@code presetSupplier} will automatically be invoked as specified by
     * {@link #supplyIfEmpty(Supplier)}.
     * <p>
     * {@snippet lang = java:
     *    LazyReference<T> lazy = LazyReference.of(Value::new);
     *    // ...
     *    T value = lazy.get();
     *    assertNotNull(value); // Value is never null
     *}
     *
     * @param <T>            The type of the value
     * @param presetSupplier to invoke when lazily constructing a value
     * @throws NullPointerException if the provided {@code presetSupplier} is {@code null}
     */
    public static <T> LazyReference<T> of(Supplier<? extends T> presetSupplier) {
        Objects.requireNonNull(presetSupplier);
        return new LazyReference<>(presetSupplier);
    }

}
