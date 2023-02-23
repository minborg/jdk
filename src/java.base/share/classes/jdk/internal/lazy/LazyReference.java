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
package jdk.internal.lazy;

import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * An object reference in which the element may be updated
 * just once (lazily) and atomically. Provided supplier(s) are guaranteed to
 * be invoked at most once per LazyReference instance.
 *
 * This contrasts to {@link AtomicReference } where any number of updates can be done
 * and where there is no simple way to atomically compute
 * a value (guaranteed to only be computed once) if missing.
 * <p>
 * Suppliers of values may never return {@code null} or else a
 * {@link NullPointerException} will be thrown.
 * <p>
 * This class is thread-safe.
 * <p>
 * The JVM may apply certain optimizations as it knows the value is updated just once
 * at most as described by {@link Stable}.
 *
 * @param <E> The type of the element to be held
 */
public sealed interface LazyReference<E> extends Supplier<E> {

    /**
     * {@return the element or {@code null} if no element exists}.
     */
    @SuppressWarnings("unchecked")
    E get();

    /**
     * {@return An {@link Optional } representation of the element}.
     * <p>
     * If no element exists, {@link Optional#empty()} is returned.
     */
    Optional<E> toOptional();

    /**
     * If a value is not already present, atomically attempts
     * to compute the value using the given {@code supplier}.
     *
     * <p>If the supplier returns {@code null}, an exception is thrown.
     * If the mapping function itself throws an (unchecked) exception, the
     * exception is rethrown, and no value is recorded.  The most
     * common usage is to construct a new object serving as a memoized result, as in:
     *
     * {@snippet lang = java:
     *    V value = lazy.supplyIfAbsent(Value::new);
     *    assertNotNull(value); // Value is non-null
     * *}
     *
     * @param supplier to apply if no previous value exists
     * @return the value (pre-existing or newly computed)
     * @throws NullPointerException if the provided {@code supplier} is {@code null} or
     *                              the provided {@code supplier} returns {@code null}.
     */
    E supplyIfEmpty(Supplier<? extends E> supplier);

    /**
     * {@return a new empty LazyReference with no pre-set supplier}.
     * <p>
     * Hence, get* methods on instances created vis this method may return
     * {@code null} or {@link  Optional#empty()}.
     * <p>
     * {@snippet lang = java:
     *    LazyReference<V> lazy = LazyReference.create();
     *    assertIsNull(value); // Value is initially null
     *    // ...
     *    V value = lazy.supplyIfEmpty(Value::new);
     *    assertNotNull(value); // Value is non-null
     *}
     */
    public static <S> LazyReference<S> create() {
        return new StandardLazyReference<>();
    }

    /**
     * {@return a new empty LazyReference with a pre-set supplier}.
     * <p>
     * If an attempt is made to invoke any get* method when no element is present,
     * the provided {@code supplier} will automatically be invoked as specified by
     * {@link #supplyIfEmpty(Supplier)}.
     * <p>
     * Hence, get* methods on instances created via this method will never return {@code null}.
     * <p>
     * {@snippet lang = java:
     *    LazyReference<V> lazy = LazyReference.create(Value::new);
     *    // ...
     *    V value = lazy.getOrNull();
     *    assertNotNull(value); // Value is never null
     *}
     */
    public static <S> LazyReference<S> create(Supplier<? extends S> supplier) {
        Objects.requireNonNull(supplier);
        return new PreSuppliedLazyReference<>(supplier);
    }

    sealed class StandardLazyReference<T>
            implements LazyReference<T> {

        @Stable
        private Object value;

        static final VarHandle VALUE_VH;

        static {
            try {
                VALUE_VH = MethodHandles.lookup()
                        .findVarHandle(LazyReference.class, "value", Object.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        StandardLazyReference() {
        }

        @SuppressWarnings("unchecked")
        @Override
        public T get() {
            return getAcquire();
        }

        @Override
        public final Optional<T> toOptional() {
            return Optional.ofNullable(get());
        }

        @Override
        public final T supplyIfEmpty(Supplier<? extends T> supplier) {
            Objects.requireNonNull(supplier);

            T value = getAcquire();
            if (value == null) {
                synchronized (this) {
                    value = getAcquire();
                    if (value == null) {
                        value = Objects.requireNonNull(
                                supplier.get());
                        setRelease(value);
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

        // Use acquire/release to ensure happens-before so that newly
        // constructed elements are always observed correctly
        T getAcquire() {
            return (T) VALUE_VH.getAcquire(this);
        }

        void setRelease(Object value) {
            VALUE_VH.setRelease(this, value);
        }
    }

    final class PreSuppliedLazyReference<T>
            extends StandardLazyReference<T>
            implements LazyReference<T> {

        private final Supplier<? extends T> supplier;

        private PreSuppliedLazyReference(Supplier<? extends T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public T get() {
            T value;
            return (value = super.get()) == null
                    ? supplyIfEmpty(supplier)
                    : value;
        }
    }

}
