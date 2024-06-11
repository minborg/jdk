/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.lang;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

/**
 * This class consists of static methods returning constructs involving StableValue.
 *
 * <p>The methods of this class all throw {@code NullPointerException}
 * if provided with {@code null} arguments unless otherwise specified.
 * <p>
 * The constructs returned are eligible for similar JVM optimizations as the
 * {@linkplain StableValue} itself.
 *
 * @see   StableValue
 * @since 24
 */
public final class StableValues {

    // Suppresses default constructor, ensuring non-instantiability.
    private StableValues() {}

    /**
     * {@return a new thread-safe, stable, lazily computed {@linkplain Supplier supplier}
     * that records the value of the provided {@code original} supplier upon being first
     * accessed via {@linkplain Supplier#get()}, or via a background thread created from
     * the provided {@code factory} (if non-null)}
     * <p>
     * The provided {@code original} supplier is guaranteed to be successfully invoked
     * at most once even in a multi-threaded environment. Competing threads invoking the
     * {@linkplain Supplier#get()} method when a value is already under computation
     * will block until a value is computed or an exception is thrown by the
     * computing thread.
     * <p>
     * If the {@code original} Supplier invokes the returned Supplier recursively,
     * a StackOverflowError will be thrown when the returned
     * Supplier's {@linkplain Supplier#get()} method is invoked.
     * <p>
     * If the provided {@code original} supplier throws an exception, it is relayed
     * to the initial caller. If the memoized supplier is computed by a background thread,
     * exceptions from the provided {@code original} supplier will be relayed to the
     * background thread's {@linkplain Thread#getUncaughtExceptionHandler() uncaught
     * exception handler}.
     *
     * @param original supplier used to compute a memoized value
     * @param factory  an optional factory that, if non-null, will be used to create
     *                 a background thread that will compute the memoized value. If the
     *                 factory is {@code null}, no background thread will be started.
     * @param <T>      the type of results supplied by the returned supplier
     */
    public static <T> Supplier<T> memoizedSupplier(Supplier<? extends T> original,
                                                   ThreadFactory factory) {
          Objects.requireNonNull(original);
          // `factory` is nullable

          // The memoized value is backed by a StableValue
          final StableValue<T> stable = StableValue.newInstance();
          // A record provides better debug capabilities than a lambda
          record MemoizedSupplier<T>(StableValue<T> stable,
                                     Supplier<? extends T> original) implements Supplier<T> {
              @Override public T get() { return stable.computeIfUnset(original); }
          }
          final Supplier<T> memoized = new MemoizedSupplier<>(stable, original);
          spinOffThread(memoized, factory);
          return memoized;
      }

    /**
     * {@return a shallowly immutable, stable List of distinct fresh stable values}
     * <p>
     * The method is equivalent to the following for a given non-negative {@code size}:
     * {@snippet lang = java :
     *     List<StableValue<T>> list = Stream.generate(StableValue::<T>newInstance)
     *                 .limit(size)
     *                 .toList();
     * }
     * @param size the size of the returned list
     * @param <T>  the {@code StableValue}s' element type
     */
    public static <T> List<StableValue<T>> ofList(int size) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        @SuppressWarnings("unchecked")
        final StableValue<T>[] stableValues = (StableValue<T>[]) new StableValue<?>[size];
        for (int i = 0; i < size; i++) {
            stableValues[i] = StableValue.newInstance();
        }
        return List.of(stableValues);
    }

    /**
     * {@return a shallowly immutable, stable Map with the provided {@code keys}
     * and associated distinct fresh stable values}
     * <p>
     * The method is equivalent to the following for a given non-null set of {@code keys}:
     * {@snippet lang = java :
     *     Map<K, StableValue<T>> map = keys.stream()
     *                 .collect(Collectors.toMap(
     *                     Function.identity(),
     *                     _ -> StableValue.newInstance()));
     * }
     * @param keys the keys in the {@code Map}
     * @param <K>  the {@code Map}'s key type
     * @param <T>  the StableValue's type for the {@code Map}'s value type
     */
    public static <K, T> Map<K, StableValue<T>> ofMap(Set<K> keys) {
        Objects.requireNonNull(keys);
        @SuppressWarnings("unchecked")
        Map.Entry<K, StableValue<T>>[] entries = (Map.Entry<K, StableValue<T>>[]) new Map.Entry<?, ?>[keys.size()];
        int i = 0;
        for (K key : keys) {
            entries[i++] = Map.entry(key, StableValue.newInstance());
        }
        return Map.ofEntries(entries);
    }

    // Initialization constructs

    /**
     * Holds a computation of type T which could either be a Value or an Error.
     * @param <T> computation type
     */
    public sealed interface Computation<T> extends Supplier<T> {

        /**
         * {@return the result of the Computation or throws
         * {@linkplain NoSuchElementException}}
         *
         * @throws NoSuchElementException if an {@linkplain Error} was encountered
         *         during computation
         */
        @Override
        T get();

        /**
         * Class indicating a value was successfully computed.
         * @param get the value that was computed
         * @param <T> the type of the value
         */
        record Value<T>(@Override T get) implements Computation<T>{}
        /**
         * Class indicating an error was encountered during computation.
         * <p>
         * For security reasons, only the class of the throwable is recorded.
         *
         * @param type the class of the throwable
         * @param <T> the type of the value intended to be computed
         */
        record Error<T>(Class<? extends Throwable> type) implements Computation<T>{
            @Override  public T get() { throw new NoSuchElementException(type.getName()); }
        }
    }

    /**
     * {@return a new thread-safe, stable, lazily computed {@linkplain Supplier supplier}
     * that records the value or error of the provided {@code original} supplier upon
     * being first accessed via {@linkplain Supplier#get()}, or via a background thread
     * created from the provided {@code factory} (if non-null)}
     * <p>
     * The provided {@code original} supplier is guaranteed to be invoked
     * at most once even in a multi-threaded environment. Competing threads invoking the
     * {@linkplain Supplier#get()} method when a value is already under computation
     * will block until a value is computed or an exception is thrown by the
     * computing thread.
     * <p>
     * If the {@code original} Supplier invokes the returned Supplier recursively,
     * a StackOverflowError will be thrown when the returned
     * Supplier's {@linkplain Supplier#get()} method is invoked.
     * <p>
     * If the provided {@code original} supplier throws an exception, the class of the
     * exception is memoized and the supplier will not be invoked again.
     *
     * @param original supplier used to compute a memoized value
     * @param factory  an optional factory that, if non-null, will be used to create
     *                 a background thread that will compute the memoized value. If the
     *                 factory is {@code null}, no background thread will be started.
     * @param <T>      the type of results supplied by the original supplier
     */
    public static <T> Supplier<Computation<T>> initSupplier(Supplier<? extends T> original,
                                                            ThreadFactory factory) {
        Objects.requireNonNull(original);
        // `factory` is nullable

        // The memoized value is backed by a StableValue
        final StableValue<Computation<T>> stable = StableValue.newInstance();
        // A record provides better debug capabilities than a lambda
        record InitSupplier<T>(StableValue<Computation<T>> stable,
                               Supplier<? extends T> original) implements Supplier<Computation<T>> {
            @Override
            public Computation<T> get() {
                return stable.computeIfUnset(new Supplier<>() {
                    @Override
                    public Computation<T> get() {
                        try {
                            T value = original.get();
                            return new Computation.Value<>(value);
                        } catch (Throwable t) {
                            return new Computation.Error<>(t.getClass());
                        }
                    }
                });
            }
        }
        final Supplier<Computation<T>> memoized = new InitSupplier<>(stable, original);
        spinOffThread(memoized, factory);
        return memoized;
    }

    private static void spinOffThread(Supplier<?> supplier,
                                      ThreadFactory factory) {
        if (factory != null) {
            final Thread thread = factory.newThread(new Runnable() {
                @Override public void run() { supplier.get(); }
            });
            thread.start();
        }
    }

}
