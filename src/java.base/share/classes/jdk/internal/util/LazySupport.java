/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.util;

import java.util.LazyCaches;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

/** Implementations of suppliers and functions that own their lazy storage. */
public final class LazySupport {
    private LazySupport() { }

    public static <T> Supplier<T> ofSupplier(LazyCaches.Mode mode,
                                             Supplier<? extends T> function) {
        return new LazySupplier<>(mode, function);
    }

    public static <C, T> Function<C, T> ofContext(LazyCaches.Mode mode,
                                                   Function<? super C, ? extends T> function) {
        return new LazyFunction<>(mode, function);
    }

    private abstract static class LazyValue<C, F, T> {
        private static final Unsafe U = Unsafe.getUnsafe();
        private static final long VALUE_OFFSET =
                U.objectFieldOffset(LazyValue.class, "value");

        private final LazyCaches.Mode mode;

        // Installed once and accessed with acquire/release semantics. Reads may
        // be folded provided the installed value's publication is preserved.
        @Stable
        private T value;
        private volatile F function;

        // Accessed only while holding this object's monitor. Keep only the
        // throwable's name and category so a failure does not pin its class
        // loader or stack.
        private String failure;
        private boolean failureIsError;
        private Thread computingThread;

        LazyValue(LazyCaches.Mode mode, F function) {
            this.mode = mode;
            this.function = function;
        }

        abstract T compute(F function, C context);

        @ForceInline
        final T getValue(C context) {
            T result = valueAcquire();
            if (result != null) {
                return result;
            }
            return mode == LazyCaches.Mode.COMPUTE_ONCE
                    ? getExactlyOnce(context) : getRacing(context);
        }

        @ForceInline
        @SuppressWarnings("unchecked")
        private T valueAcquire() {
            return (T) U.getReferenceAcquire(this, VALUE_OFFSET);
        }

        private synchronized T getExactlyOnce(C context) {
            T result = value;
            if (result != null) {
                return result;
            }
            if (failureIsError) {
                throw new LazyCaches.InitializationError(
                        "Lazy initialization previously failed with "
                                + (failure != null ? failure : "an Error"));
            }
            if (failure != null) {
                throw failed(failure, null);
            }
            if (computingThread == Thread.currentThread()) {
                throw new IllegalStateException("Recursive computation of a lazy value");
            }
            computingThread = Thread.currentThread();
            try {
                result = Objects.requireNonNull(compute(function, context));
                U.putReferenceRelease(this, VALUE_OFFSET, result);
                return result;
            } catch (Throwable ex) {
                if (ex instanceof Error error) {
                    // Make failure permanent before diagnostic work that might
                    // itself fail, for example after an OutOfMemoryError.
                    failureIsError = true;
                    try {
                        failure = error.getClass().getName();
                    } catch (Throwable ignored) {
                        // Preserve the original error even without its name.
                    }
                    throw error;
                }
                failure = ex.getClass().getName();
                throw failed(failure, ex);
            } finally {
                function = null;
                computingThread = null;
            }
        }

        private T getRacing(C context) {
            F computingFunction = function;
            // A winner may have cleared the function after our first read of
            // value. Reading it before rereading value prevents invoking null.
            T result = valueAcquire();
            if (result != null) {
                return result;
            }
            T candidate = Objects.requireNonNull(compute(computingFunction, context));
            Object witness = U.compareAndExchangeReferenceRelease(this, VALUE_OFFSET, null, candidate);
            if (witness != null) {
                // A failed release compare-and-exchange only reads plainly.
                // Acquire the winner's publication before returning its value.
                return valueAcquire();
            }
            function = null;
            return candidate;
        }

        private static NoSuchElementException failed(String failure, Throwable cause) {
            return new NoSuchElementException(
                    "Unable to access the lazy value because " + failure
                            + " was thrown at initial computation", cause);
        }
    }

    private static final class LazySupplier<T>
            extends LazyValue<Void, Supplier<? extends T>, T>
            implements Supplier<T> {
        LazySupplier(LazyCaches.Mode mode, Supplier<? extends T> function) {
            super(mode, function);
        }

        @Override
        T compute(Supplier<? extends T> function, Void context) {
            return function.get();
        }

        @Override
        public T get() {
            return getValue(null);
        }
    }

    private static final class LazyFunction<C, T>
            extends LazyValue<C, Function<? super C, ? extends T>, T>
            implements Function<C, T> {
        LazyFunction(LazyCaches.Mode mode, Function<? super C, ? extends T> function) {
            super(mode, function);
        }

        @Override
        T compute(Function<? super C, ? extends T> function, C context) {
            return function.apply(context);
        }

        @Override
        public T apply(C context) {
            return getValue(Objects.requireNonNull(context));
        }
    }
}
