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
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * An object reference in which the element may be updated
 * just once (lazily) and atomically. This contrasts to {@link AtomicReference } where
 * any number of updates can be done and where there is no simple way to atomically compute
 * a value (guaranteed to only be computed once) if it is missing.
 * <p>
 * This class is thread-safe.
 * <p>
 * The JVM may apply certain optimizations as it knows the value is updated just once
 * at most as described by {@link Stable}.
 *
 * @param <E> The type of element held
 */
public final class Lazy<E> {

    @Stable
    private Object value;

    private static final VarHandle VALUE_VH;

    static {
        try {
            VALUE_VH = MethodHandles.lookup()
                    .findVarHandle(Lazy.class, "value", Object.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Creates a new LazyReference with its
     * element initially set to null. Intended
     * to be used by a static constructor only.
     */
    private Lazy() {}

    /**
     * If a value is present, returns {@code true}, otherwise {@code false}.
     *
     * @return {@code true} if a value is present, otherwise {@code false}
     */
    public boolean isPresent() {
        return getAcquire() != null;
    }

    /**
     * {@return the element or {@code null} if no element exists}.
     *
     */
    @SuppressWarnings("unchecked")
    public E getOrNull() {
        return getAcquire();
    }

    /**
     * {@return the element or, if no element exists, throws a NoSuchElementException}.
     *
     * @throws NoSuchElementException    if no element exists at the provided {@code index}
     */
    public E getOrThrow() {
        E value = getOrNull();
        if (value == null) {
            throw new NoSuchElementException();
        }
        return value;
    }

    public <R> R mapOr(Function<? super E, ? extends R> mapper, R other) {
        E value = getOrNull();
        return value == null
        ? other
        : mapper.apply(value);
    }

    /**
     * If a value is not already present, atomically attempts
     * to compute the value using the given {@code supplier}.
     *
     * <p>If the supplier returns {@code null}, an exception is thrown.
     * If the mapping function itself throws an (unchecked) exception, the
     * exception is rethrown, and no value is recorded.  The most
     * common usage is to construct a new object serving as a memoized result, as in:
     *
     * <pre> {@code
     * lazy.supplyIfAbsent(Value::new);
     * }</pre>
     *
     * @param supplier to apply if no previous value exists
     * @return the value (pre-existing or newly computed)
     * @throws NullPointerException      if the provided {@code supplier} is {@code null} or
     *                                   the provided {@code supplier} returns {@code null}.
     */
    public E supplyIfEmpty(Supplier<? extends E> supplier) {
        Objects.requireNonNull(supplier);

        E value = getAcquire();
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
    public boolean equals(Object o) {
        return this == o ||
                o instanceof Lazy<?> that &&
                        Objects.equals(this.value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        var v = getAcquire();
        return v != null
                ? ("Lazy[" + v + "]")
                : "Lazy.empty";
    }

    // Use acquire/release to ensure happens-before so that newly
    // constructed elements are always observed correctly
    private E getAcquire() {
        return (E) VALUE_VH.getAcquire(this);
    }

    void setRelease(Object value) {
        VALUE_VH.setRelease(this, value);
    }

    /**
     * {@return a new empty Lazy}.
     */
    public static <E> Lazy<E> create() {
        return new Lazy<>();
    }

}
