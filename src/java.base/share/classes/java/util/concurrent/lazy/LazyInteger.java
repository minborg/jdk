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

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

/**
 * An int in which the value can be lazily and atomically computed.
 * <p>
 * At most one invocation is made of any provided set of suppliers.
 * <p>
 * This contrasts to {@link AtomicInteger } where any number of updates can be done
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
 */
@Deprecated(forRemoval = true)
public final class LazyInteger
        extends AbstractLazy<IntSupplier>
        implements Lazy, IntSupplier {

    @Stable
    private int value;

    private LazyInteger(IntSupplier presetSupplier) {
        super(presetSupplier);
    }

    /**
     * Returns the present value or, if no present value exists, atomically attempts
     * to compute the value using the <em>pre-set {@linkplain #of(IntSupplier)} supplier}</em>.
     * If no pre-set {@linkplain #of(IntSupplier)} supplier} exists,
     * throws an IllegalStateException exception.
     * <p>
     * If the pre-set supplier itself throws an (unchecked) exception, the
     * exception is rethrown, and no value is recorded. The most
     * common usage is to construct a new object serving as a memoized result, as in:
     * <p>
     * {@snippet lang = java:
     *    LazyInteger lazy = LazyInteger.of(MyLogic::computeValue);
     *    // ...
     *    int value = lazy.get();
     *}
     * <p>
     * If another thread attempts to compute the value, the current thread will be suspended until
     * the atempt completes (successfully or not).
     *
     * @return the value (pre-existing or newly computed)
     * @throws IllegalStateException  if a value was not already present and no
     *                                pre-set supplier was specified.
     * @throws NoSuchElementException if a supplier has previously thrown an exception.
     */
    @Override
    public int getAsInt() {
        return isPresentPlain()
                ? value
                : supplyIfEmpty0(presetProvider());
    }

    /**
     * Returns the present value or, if no present value exists, atomically attempts
     * to compute the value using the <em>provided {@code supplier}</em>.
     *
     * If the provided {@code supplier} itself throws an (unchecked) exception, the
     * exception is rethrown, and no value is recorded.  The most
     * common usage is to construct a new object serving as a memoized result, as in:
     * <p>
     * {@snippet lang = java:
     *    LazyInteger lazy = LazyInteger.ofEmpty();
     *    // ...
     *    int value = lazy.supplyIfAbsent(MyLogic::computeValue);
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
    public int supplyIfEmpty(IntSupplier supplier) {
        Objects.requireNonNull(supplier);
        return supplyIfEmpty0(supplier);
    }

    private int supplyIfEmpty0(IntSupplier supplier) {
        if (!isPresentPlain()) {
            synchronized (this) {
                if (isPlain(State.ERROR)) {
                    throw new NoSuchElementException("A supplier has perviously thrown an exception.");
                }
                if (!isPresentPlain()) {
                    if (supplier == null) {
                        throw new IllegalStateException("No pre-set supplier specified.");
                    }
                    try {
                        constructing(true);
                        int v = supplier.getAsInt();
                        if (v != 0) {
                            value = v;
                        }
                        stateValueRelease(State.PRESENT);
                    } catch (Throwable e) {
                        stateValueRelease(State.ERROR);
                        throw e;
                    } finally {
                        constructing(false);
                        forgetPresetProvided();
                    }
                }
            }
        }
        return value;
    }

    @Override
    protected String renderValue() {
        return Integer.toString(value);
    }

    /**
     * {@return a new empty LazyInt with no pre-set supplier}.
     * <p>
     * If an attempt is made to invoke the {@link #getAsInt()} } method when no element is present,
     * an exception will be thrown.
     * <p>
     * {@snippet lang = java:
     *    LazyInteger lazy = LazyInteger.ofEmpty();
     *    assertFalse(lazy.isPresentPlain()); // Value is initially not present
     *    // ...
     *    int value = lazy.supplyIfEmpty(MyLogic::computeValue);
     *    assertNotNull(value); // Value is non-null
     *}
     */
    public static LazyInteger ofEmpty() {
        return new LazyInteger(null);
    }

    /**
     * {@return a new empty LazyInt with a pre-set supplier}.
     * <p>
     * If an attempt is made to invoke the {@link #getAsInt()} method when no element is present,
     * the provided {@code presetSupplier} will automatically be invoked as specified by
     * {@link #supplyIfEmpty(IntSupplier)} )}.
     * <p>
     * {@snippet lang = java:
     *    LazyInteger lazy = LazyInteger.of(MyLogic::computeValue);
     *    // ...
     *    int value = lazy.get();
     *}
     *
     * @param presetSupplier to invoke when lazily constructing a value
     * @throws NullPointerException if the provided {@code presetSupplier} is {@code null}
     */
    public static LazyInteger of(IntSupplier presetSupplier) {
        Objects.requireNonNull(presetSupplier);
        return new LazyInteger(presetSupplier);
    }

}
