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

package java.lang;

import jdk.internal.lang.AbstractRigidValue;
import jdk.internal.lang.ResettableRigidValue;
import jdk.internal.lang.StableRigidValue;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A rigid value is a holder of contents that is rarely updated.
 *
 * @param <T> type of the rigid value's contents
 * @since 99
 */
public sealed interface RigidValue<T> permits AbstractRigidValue, ResettableRigidValue, StableRigidValue {

    // Query operations

    /**
     * {@return the contents of this rigid value}
     *
     * @throws NoSuchElementException if no value is set
     */
    T get();

    /**
     * {@return the contents of this rigid value if set, otherwise,
     *          returns {@code other}}
     *
     * @param other value to return if the content is not set
     *              (can be {@code null})
     */
    T orElse(T other);

    /**
     * {@return the contents of this rigid value if set, otherwise,
     *          returns {@code supplier.get()}}
     *
     * @param supplier to invoke if the content is not set
     *                 (can return {@code null})
     */
    T orElseGet(Supplier<T> supplier);

    /**
     * {@return {@code true} if the constant is set, {@code false} otherwise}
     */
    boolean isSet();

    // Setters

    /**
     * Sets the contents of this rigid value.
     *
     * @param value to set
     */
    void set(T value);

    /**
     * {@return the contents of this rigid value if set, otherwise, invokes the provided
     *          {@code supplier} and atomically sets the contents of this rigid value
     *          and returns the newly set value}
     *
     * @param supplier to invoke if the content is not set
     *                 (can <em>not</em> return {@code null})
     */
    T orElseSet(Supplier<T> supplier);

    /**
     * Atomically sets the contents of this rigid value to the {@code newValue} with the
     * memory semantics of {@link java.lang.invoke.VarHandle#setRelease(Object...)} if this
     * rigid value's current contents, referred to as the <em>witness value</em>, {@code ==} the
     * {@code expectedValue}, as accessed with the memory semantics of
     * {@link java.lang.invoke.VarHandle#getAcquire(Object...)}.
     *
     * @return if the contents was updated
     * @param expectValue to compare with before setting the {@code newValue}
     * @param newValue    to set if the witness value was equal to the {@code expectValue}
     */
    boolean compareAndSet(T expectValue, T newValue);

    // Factories

    /**
     * {@return a new unset rigid value that can only be set once}
     *
     * @param <T> type of the rigid value's contents
     */
    static <T> RigidValue<T> of() {
        return new StableRigidValue<>();
    }

    /**
     * {@return a new unset rigid value that can be set multiple times}
     *
     * @param <T> type of the rigid value's contents
     */
    static <T> RigidValue<T> ofResettable() {
        return new ResettableRigidValue<>();
    }

    /**
     * {@return a list of new unset rigid values that can be set once per element}
     *
     * @param size the size of the returned list
     * @param <E> type of the rigid values' contents
     */
    static <E> List<RigidValue<E>> list(int size) {
        return list0(size, RigidValue::of);
    }

    /**
     * {@return a list of new unset rigid values that can be set set multiple times
     *          per element}
     *
     * @param size the size of the returned list
     * @param <E> type of the rigid values' contents
     */
    static <E> List<RigidValue<E>> listOfResettable(int size) {
        return list0(size, RigidValue::ofResettable);
    }

    /**
     * {@return a map of new unset rigid values that can be set once per element}
     *
     * @param keys the keys in the returned map
     * @param <K> type of keys in the returned map
     * @param <V> type of the rigid values' contents
     */
    static <K, V> Map<K, RigidValue<V>> map(Set<? super K> keys) {
        return map0(keys, _ -> RigidValue.of());
    }

    /**
     * {@return a map of new unset rigid values that can be set multiple times
     *          per value}
     *
     * @param keys the keys in the returned map
     * @param <K> type of keys in the returned map
     * @param <V> type of the rigid values' contents
     */
    static <K, V> Map<K, RigidValue<V>> mapOfResettable(Set<? super K> keys) {
        return map0(keys, _ -> RigidValue.ofResettable());
    }

    // Private factories

    private static <E> List<RigidValue<E>> list0(int size, Supplier<RigidValue<E>> generator) {
        if (size < 0) {
            throw new IllegalArgumentException("size < 0");
        }
        return Stream.generate(generator)
                .limit(size)
                .toList();
    }

    private static <K, V> Map<K, RigidValue<V>> map0(Set<? super K> keys,  Function<? super K, RigidValue<V>> valueMapper) {
        @SuppressWarnings("unchecked")
        final Set<K> setCopy = (Set<K>) Set.copyOf(keys);
        return setCopy.stream()
                .collect(Collectors.toUnmodifiableMap(Function.identity(), valueMapper));
    }

    // With the lump solution above, we get bi-morphic paths
    // It would also be possible to introduce sub-interfaces (e.g., OfResettable and OfStable)
    // to promote monomorphic paths.

}
