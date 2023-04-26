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
import jdk.internal.util.concurrent.lazy.StandardLazyArray;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.stream.Stream;

/**
 * A lazy array with a pre-set supplier which will be invoken at most once,
 * per slot, for example when {@link LazyArray#apply(int) apply(index)} is invoked.
 *
 * @param <V> The type of the values to be recorded
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.LAZY)
public sealed interface LazyArray<V>
        extends IntFunction<V>
        permits StandardLazyArray {

    /**
     * {@return the length of the array}.
     */
    public int length();

    /**
     * {@return the {@link LazyState State} of this Lazy}.
     * <p>
     * The value is a snapshot of the current State.
     * No attempt is made to compute a value if it is not already present.
     * <p>
     * If the returned State is either {@link LazyState#PRESENT} or
     * {@link LazyState#ERROR}, it is guaranteed the state will
     * never change in the future.
     * <p>
     * This method can be used to act on a value if it is present:
     * {@snippet lang = java:
     *     if (lazy.state() == State.PRESENT) {
     *         // perform action on the value
     *     }
     *}
     * @param index to retrieve the State from
     * @throws ArrayIndexOutOfBoundsException if {@code index < 0} or {@code index >= length()}
     */
    public LazyState state(int index);

    /**
     * {@return the excption thrown by the supplier invoked or
     * {@link Optional#empty()} if no exception was thrown}.
     *
     * @param index to retrieve the exception from
     * @throws ArrayIndexOutOfBoundsException if {@code index < 0} or {@code index >= length()}
     */
    public Optional<Throwable> exception(int index);

    /**
     * {@return the value at the provided {@code index} if the state is {@link LazyState#PRESENT PRESENT}
     * or {@code defaultValue} if the value is {@link LazyState#EMPTY EMPTY} or
     * {@link LazyState#CONSTRUCTING CONSTRUCTING}}.
     *
     * @param index        for which the value shall be obtained.
     * @param defaultValue to use if no value is present
     * @throws ArrayIndexOutOfBoundsException if {@code index< 0} or {@code index >= length()}
     * @throws NoSuchElementException         if a provider for the provided {@code index} has previously
     *                                        thrown an exception.
     */
    V getOr(int index, V defaultValue);

    /**
     * Returns an unmodifiable view of the elements in this lazy array
     * where the empty elements will be replaced with {@code null}.
     * <p>
     * If a mapper has previously thrown an exception for an
     * accessed element at a certain index, accessing that index will result in
     * a NoSuchElementException being thrown.
     *
     * @return a view of the elements
     */
    public List<V> asList();

    /**
     * Returns an unmodifiable view of the elements in this lazy array
     * where the empty elements will be replaced with the provided {@code defaulValue}.
     * <p>
     * If a mapper has previously thrown an exception for an
     * accessed element at a certain index, accessing that index will result in
     * a NoSuchElementException being thrown.
     *
     * @param defaulValue to use for elements not yet created
     * @return a view of the elements
     */
    public List<V> asList(V defaulValue);

    /**
     * {@return A Stream with the lazy elements in this lazy array}.
     * <p>
     * Upon encountering a state at position {@code index} in the array, the following actions
     * will be taken:
     * <ul>
     *     <li><b>EMPTY</b>
     *     <p>An Optional.empty() element is selected.</p></li>
     *     <li><b>CONSTRUCTING</b>
     *     <p>An Optional.empty() element is selected.</p></li>
     *     <li><b>PRESENT</b>
     *     <p>An Optional.ofNullable(lazy.get(index)) element is selected.</p></li>
     *     <li><b>ERROR</b>
     *     <p>A NoSuchElementException is thrown.</p></li>
     * </ul>
     *
     */
    public Stream<Optional<V>> stream();

    /**
     * {@return A Stream with the lazy elements in this LazyReferenceArray}.
     * <p>
     * Upon encountering a state at position {@code index} in the array, the following actions
     * will be taken:
     * <ul>
     *     <li><b>EMPTY</b>
     *     <p>The provided {@code defaultValue} is selected.</p></li>
     *     <li><b>CONSTRUCTING</b>
     *     <p>The provided {@code defaultValue} is selected.</p></li>
     *     <li><b>PRESENT</b>
     *     <p>lazy.get(index)) is selected.</p></li>
     *     <li><b>ERROR</b>
     *     <p>A NoSuchElementException is thrown.</p></li>
     * </ul>
     *
     * @param defaultValue the default value to use for empty/contructing slots.
     */
    public Stream<V> stream(V defaultValue);

    /**
     * Returns the present value at the provided {@code index} or, if no present value exists,
     * atomically attempts to compute the value using the <em>pre-set {@linkplain LazyArray#ofArray(int, IntFunction) mapper}</em>.
     * <p>
     * If the pre-set mapper itself throws an (unchecked) exception, the
     * exception is rethrown, and no value is recorded. The most
     * common usage is to construct a new object serving as a memoized result, as in:
     * <p>
     * {@snippet lang = java:
     *    LazyArray<V> lazy = Lazy.ofArray(64, Value::new);
     *    // ...
     *    V value = lazy.apply(42);
     *    assertNotNull(value); // Value is non-null
     *}
     * <p>
     * If another thread attempts to compute the value, the current thread will be suspended until
     * the atempt completes (successfully or not).
     *
     * @param index to the slot to be used
     * @return the value (pre-existing or newly computed)
     * @throws ArrayIndexOutOfBoundsException if {@code index < 0} or {@code index >= length()}
     * @throws IllegalStateException          if a value was not already present and no
     *                                        pre-set mapper was specified.
     * @throws NoSuchElementException         if a maper has previously thrown an exception for the
     *                                        provided {@code index}.
     */
    @Override
    public V apply(int index);

    /**
     * Forces computation of all {@link LazyState#EMPTY EMPTY} slots in
     * slot order.
     * <p>
     * If the pre-set mapper throws an (unchecked) exception, the
     * exception is rethrown, and no value is recorded. This also means, subsequent slots
     * are not computed.
     */
    public void force();

    /**
     * {@return a new EmptyLazyArray with a pre-set mapper}.
     * <p>
     * Below, an example of how to cache values in an array is shown:
     * {@snippet lang = java:
     *     class DemoArray {
     *
     *         private static final LazyArray<Value> VALUE_PO2_CACHE =
     *                 Lazy.ofArray(32, index -> new Value(1L << index));
     *
     *         public Value powerOfTwoValue(int n) {
     *             if (n < 0 || n >= VALUE_PO2_CACHE.length()) {
     *                 throw new IllegalArgumentException(Integer.toString(n));
     *             }
     *
     *             return VALUE_PO2_CACHE.apply(n);
     *         }
     *     }
     *}
     *
     * @param <V>          The type of the values
     * @param size         the size of the array
     * @param presetMapper to invoke when lazily constructing a value
     */
    public static <V> LazyArray<V> ofArray(int size,
                                           IntFunction<? extends V> presetMapper) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        Objects.requireNonNull(presetMapper);
        return new StandardLazyArray<>(size, presetMapper);
    }

}
