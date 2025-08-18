/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.lang.stable;

import jdk.internal.ValueBased;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.AbstractList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.RandomAccess;
import java.util.concurrent.atomic.StableValue;
import java.util.function.Supplier;

@ValueBased
public class StandardStableList<E>
        extends AbstractList<StableValue<E>>
        implements List<StableValue<E>>, RandomAccess {

    // Unsafe allows StableValue to be used early in the boot sequence
    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    @Stable
    private final Object[] elements;
    @Stable
    private final DenseLocks locks;

    private StandardStableList(int size) {
        this.elements = new Object[size];
        this.locks = new DenseLocks(size);
        super();
    }

    @ForceInline
    @Override
    public ElementStableValue<E> get(int index) {
        Objects.checkIndex(index, elements.length);
        return new ElementStableValue<>(elements, locks, offsetFor(index));
    }

    @Override
    public int size() {
        return elements.length;
    }

    // Todo: Views
    // Todo: Consider having just a StandardStableList field and an offset
    public record ElementStableValue<T>(@Stable Object[] elements,
                                        DenseLocks locks,
                                        long offset)
            implements StableValue<T> {

        @ForceInline
        @Override
        public boolean trySet(T contents) {
            Objects.requireNonNull(contents);
            if (contentsAcquire() != null) {
                return false;
            }
            // Mutual exclusion is required here as `orElseSet` might also
            // attempt to modify the element
            preventLockReentry();
            if (lock()) {
                boolean rollback = false;
                try {
                    return set(contents);
                } catch (Throwable t) {
                    rollback = true;
                    throw t;
                } finally {
                    if (rollback) rollback(); else unlock();
                }
            }
            return false;
        }

        @SuppressWarnings("unchecked")
        @ForceInline
        @Override
        public Optional<T> toOptional() {
            return Optional.ofNullable((T) contentsAcquire());
        }

        @SuppressWarnings("unchecked")
        @ForceInline
        @Override
        public T get() {
            final Object t = contentsAcquire();
            if (t == null) {
                throw new NoSuchElementException("No contents set");
            }
            return (T) t;
        }

        @ForceInline
        @Override
        public boolean isSet() {
            return contentsAcquire() != null;
        }

        @SuppressWarnings("unchecked")
        @ForceInline
        @Override
        public T orElseSet(Supplier<? extends T> supplier) {
            Objects.requireNonNull(supplier);
            final Object t = contentsAcquire();
            return (t == null) ? orElseSetSlowPath(supplier) : (T) t;
        }

        @SuppressWarnings("unchecked")
        private T orElseSetSlowPath(Supplier<? extends T> supplier) {
            preventLockReentry();
            if (lock()) {
                boolean rollback = false;
                try {
                    final T newValue = supplier.get();
                    Objects.requireNonNull(newValue);
                    // The lock is not reentrant so we know newValue should be returned
                    set(newValue);
                    return newValue;
                } catch (Throwable t) {
                    // Something bad happened.
                    // We need to back out and allow for future retries.
                    rollback = true;
                    throw t;
                } finally {
                    if (rollback) rollback(); else unlock();
                }
            }
            return (T) contentsAcquire();
        }

        // Object methods
        @Override public boolean equals(Object obj) { return this == obj; }
        @Override public int     hashCode() { return System.identityHashCode(this); }
        @Override public String toString() {
            final Object t = contentsAcquire();
            return t == this
                    ? "(this StableValue)"
                    : StandardStableValue.render(t);
        }

        @ForceInline
        private Object contentsAcquire() {
            return UNSAFE.getReferenceAcquire(elements, offset);
        }

        private boolean lock() {
            return locks.lock(index());
        }

        private void unlock() {
            locks.unlock(index());
        }

        private void rollback() {
            locks.rollBack(index());
        }

        private void preventLockReentry() {
            locks.preventLockReentry(index());
        }

        // Only called in slow paths
        private int index() {
            return (int) (offset - Unsafe.ARRAY_OBJECT_BASE_OFFSET) / Unsafe.ARRAY_OBJECT_INDEX_SCALE;
        }

        /**
         * Tries to set the contents at the provided {@code index} to {@code newValue}.
         * <p>
         * This method ensures the {@link Stable} element is written to at most once.
         *
         * @param newValue to set
         * @return if the contents was set
         */
        @ForceInline
        private boolean set(T newValue) {
            // We know we hold the lock here so plain semantic is enough
            if (UNSAFE.getReference(elements, offset) == null) {
                UNSAFE.putReferenceRelease(elements, offset, newValue);
                return true;
            }
            return false;
        }

    }

    @ForceInline
    private static long offsetFor(long index) {
        return Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * index;
    }

    public static <T> List<StableValue<T>> ofList(int size) {
        return new StandardStableList<>(size);
    }

}
