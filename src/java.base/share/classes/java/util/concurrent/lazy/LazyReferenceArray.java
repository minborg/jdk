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

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * An object reference array in which the values are lazily and atomically computed.
 * <p>
 * At most one invocation is made of any provided set of mapper per slot.
 * <p>
 * This contrasts to {@link java.util.concurrent.atomic.AtomicReferenceArray } where any number of updates can be done
 * and where there is no simple way to atomically compute
 * a value (guaranteed to only be computed once) if missing.
 * <p>
 * The implementation is optimized for the case where there are N invocations
 * trying to obtain a slot value and where N >> 1, for example where N is > 2<sup>20</sup>.
 * <p>
 * This class is thread-safe.
 * <p>
 * The JVM may apply certain optimizations as it knows the value is updated just once
 * at most as described by {@link Stable}.
 *
 * @param <V> The type of the values to be recorded
 */
public final class LazyReferenceArray<V> implements IntFunction<V> {

    private final IntFunction<? extends V> presetMapper;

    @Stable
    private final LazyReference<V>[] lazyReferences;

    // Todo: use an array of V and a bit-set (3 bits per element or perhaps an entire int)
    // Todo: Bit CAS granularity. Perhaps int[] or several arrays (@Stable and non-@Stable)

    @SuppressWarnings("unchecked")
    private LazyReferenceArray(int size,
                               IntFunction<? extends V> presetMapper) {
        lazyReferences = IntStream.range(0, size)
                .mapToObj(i -> LazyReference.<V>of(toSupplier(i, presetMapper)))
                .toArray(LazyReference[]::new);
        this.presetMapper = presetMapper;
    }

    @SuppressWarnings("unchecked")
    private LazyReferenceArray(int size) {
        lazyReferences = IntStream.range(0, size)
                .mapToObj(i -> LazyReference.ofEmpty())
                .toArray(LazyReference[]::new);
        this.presetMapper = null;
    }

    /**
     * {@return the length of the array}.
     */
    public int length() {
        return lazyReferences.length;
    }

    /**
     * Returns the present value at the provided {@code index} or, if no present value exists,
     * atomically attempts to compute the value using the <em>pre-set {@linkplain #of(int, IntFunction)} mapper}</em>.
     * If no pre-set {@linkplain #of(int, IntFunction)} mapper} exists,
     * throws an IllegalStateException exception.
     * <p>
     * If the pre-set mapper itself throws an (unchecked) exception, the
     * exception is rethrown, and no value is recorded. The most
     * common usage is to construct a new object serving as a memoized result, as in:
     * <p>
     * {@snippet lang = java:
     *    LazyReferenceArray<V> lazy = LazyReferenceArray.of(Value::new);
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
     * @throws ArrayIndexOutOfBoundsException if the provided {@code index} is {@code < 0}
     *                                        or {@code index >= length()}
     * @throws IllegalStateException          if a value was not already present and no
     *                                        pre-set mapper was specified.
     * @throws NoSuchElementException         if a maper has previously thrown an exception for the
     *                                        provided {@code index}.
     */
    @Override
    public V apply(int index) {
        return lazyReferences[index]
                .get();
    }

    /**
     * {@return if a value is present at the provided {@code index}}.
     * <p>
     * No attempt is made to compute a value if it is not already present.
     * <p>
     * This method can be used to act on a value if it is present:
     * {@snippet lang = java:
     *     if (lazy.isPresent(index)) {
     *         V value = lazy.get(index);
     *         // perform action on the value
     *     }
     *}
     *
     * @param index to the slot to be used
     * @throws ArrayIndexOutOfBoundsException if the provided {@code index} is {@code < 0}
     *                                        or {@code index >= length()}
     */
    public Lazy.State state(int index) {
        return lazyReferences[index]
                .state();
    }

    /**
     * Returns the present value at the provided {@code index} or, if no present value exists,
     * atomically attempts to compute the value using the <em>provided {@code mappper}</em>.
     *
     * <p>If the mapper returns {@code null}, an exception is thrown.
     * If the provided {@code ,mapper} itself throws an (unchecked) exception, the
     * exception is rethrown, and no value is recorded.  The most
     * common usage is to construct a new object serving as a memoized result, as in:
     * <p>
     * {@snippet lang = java:
     *    LazyReference<V> lazy = LazyReferenceArray.ofEmpty();
     *    // ...
     *    V value = lazy.supplyIfAbsent(42, Value::new);
     *    assertNotNull(value); // Value is non-null
     *}
     * <p>
     * If another thread attempts to compute the value, the current thread will be suspended until
     * the atempt completes (successfully or not).
     *
     * @param index   to the slot to be used
     * @param mappper to apply if no previous value exists
     * @return the value (pre-existing or newly computed)
     * @throws ArrayIndexOutOfBoundsException if the provided {@code index} is {@code < 0}
     *                                        or {@code index >= length()}
     * @throws NullPointerException           if the provided {@code mappper} is {@code null}.
     * @throws NoSuchElementException         if a maper has previously thrown an exception for the
     *                                        provided {@code index}.
     */
    public V computeIfEmpty(int index,
                            IntFunction<? extends V> mappper) {
        Objects.requireNonNull(mappper);
        return lazyReferences[index]
                .supplyIfEmpty(
                        toSupplier(index, mappper));
    }

    /**
     * {@return the excption thrown by the mapper invoked at the provided
     * {@code index} or {@link Optional#empty()} if no exception was thrown}.
     *
     * @param index to the slot to be accessed
     * @throws ArrayIndexOutOfBoundsException if the provided {@code index} is {@code < 0}
     *                                        or {@code index >= length()}
     */
    public Optional<Throwable> exception(int index) {
        return lazyReferences[index]
                .exception();
    }

    /**
     * Creates a new unmodifiable view of the elements in this LazyReferenceArray
     * where the empty elements will be replaced with {@code null}.
     * <p>
     * If a mapper has previously thrown an exception for an
     * accessed element at a certain index, accessing that index will result in
     * a NoSuchElementException being thrown.
     *
     * @return a view of the elements
     */
    public List<V> asList() {
        return new ListView(null);
    }

    /**
     * Creates a new unmodifiable view of the elements in this LazyReferenceArray
     * where the empty elements will be replaced with the provided {@code defaulValue}.
     * <p>
     * If a mapper has previously thrown an exception for an
     * accessed element at a certain index, accessing that index will result in
     * a NoSuchElementException being thrown.
     *
     * @param defaulValue to use for elements not yet created
     * @return a view of the elements
     */
    public List<V> asList(V defaulValue) {
        return new ListView(defaulValue);
    }

    /**
     * {@return A Stream with the lazy elements in this LazyReferenceArray}.
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
     * @throws NoSuchElementException if a slot is in state ERROR and is being accessed.
     */
    public Stream<Optional<V>> stream() {
        return IntStream.range(0, length())
                .mapToObj(i -> {
                    var lazy = lazyReferences[i];
                    return switch (lazy.state()) {
                        case EMPTY, CONSTRUCTING -> Optional.empty();
                        case PRESENT -> Optional.ofNullable(lazy.get());
                        case ERROR -> throw new NoSuchElementException("At index: " + i);
                    };
                });
    }

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
     * @param defaultValue the default value to use for empty/contructing slots.
     * @throws NoSuchElementException if a slot is in state ERROR and is being accessed.
     */
    public Stream<V> stream(V defaultValue) {
        return IntStream.range(0, length())
                .mapToObj(i -> {
                    var lazy = lazyReferences[i];
                    return switch (lazy.state()) {
                        case EMPTY, CONSTRUCTING -> defaultValue;
                        case PRESENT -> lazy.get();
                        case ERROR -> throw new NoSuchElementException("At index: " + i);
                    };
                });
    }

    /**
     * Forces computation of all {@link java.util.concurrent.lazy.Lazy.State#EMPTY} slots in
     * slot order.
     * <p>
     * If the pre-set mapper throws an (unchecked) exception, the
     * exception is rethrown, and no value is recorded. This means, subsequent slots
     * are not computed.
     *
     * @throws IllegalStateException  if no pre-set mapper was specified.
     */
    public void force() {
        if (presetMapper == null) {
            throw new IllegalStateException();
        }
        for (LazyReference<V> lazy : lazyReferences) {
            lazy.get();
        }
    }

    @Override
    public String toString() {
        return IntStream.range(0, length())
                .mapToObj(i -> lazyReferences[i])
                .map(lazy -> switch (lazy.state()) {
                    case EMPTY -> "-";
                    case CONSTRUCTING -> "+";
                    case PRESENT -> Objects.toString(lazy.get());
                    case ERROR -> "!";
                })
                .collect(Collectors.joining(", ", "LazyReferenceArray[", "]"));
    }

    // Todo: Add supplyIfEmpty()?

    Supplier<V> toSupplier(int index,
                           IntFunction<? extends V> mappper) {
        return () -> mappper.apply(index);
    }

    V getOr(int index, V defaultValue) {
        var lazy = lazyReferences[index];
        var state = lazy.state();
        return switch (state) {
            case EMPTY, CONSTRUCTING -> defaultValue;
            case PRESENT -> lazy.get();
            case ERROR -> throw new NoSuchElementException();
        };
    }

    private final class ListView implements List<V> {

        private final V defaultValue;
        private final int begin;
        private final int end;

        ListView(int begin,
                 int end,
                 V defaultValue) {
            if (begin < 0) {
                throw new IndexOutOfBoundsException("begin: " + begin);
            }
            if (end > LazyReferenceArray.this.length()) {
                throw new IndexOutOfBoundsException("end: " + begin);
            }
            this.begin = begin;
            this.end = end;
            this.defaultValue = defaultValue;
        }

        ListView(V defaultValue) {
            this(0, LazyReferenceArray.this.length(), defaultValue);
        }

        @Override
        public int size() {
            return end - begin;
        }

        @Override
        public boolean isEmpty() {
            return size() == 0;
        }

        @Override
        public boolean contains(Object o) {
            for (int i = begin; i < end; i++) {
                if (Objects.equals(0, getOr(i, defaultValue))) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Iterator<V> iterator() {
            return new ListIteratorView(0, length(), null);
        }

        @Override
        public Object[] toArray() {
            return IntStream.range(0, size())
                    .mapToObj(i -> LazyReferenceArray.this.getOr(i, defaultValue))
                    .toArray();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T1> T1[] toArray(T1[] a) {
            if (a.length < size()) {
                return (T1[]) Arrays.copyOf(toArray(), size(), a.getClass());
            }
            System.arraycopy(toArray(), 0, a, 0, size());
            if (a.length > size())
                a[size()] = null;
            return a;
        }

        @Override
        public boolean add(V v) {
            throw newUnsupportedOperation();
        }

        @Override
        public boolean remove(Object o) {
            throw newUnsupportedOperation();
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            for (Object e : c)
                if (!contains(e))
                    return false;
            return true;
        }

        @Override
        public boolean addAll(Collection<? extends V> c) {
            throw newUnsupportedOperation();
        }

        @Override
        public boolean addAll(int index, Collection<? extends V> c) {
            throw newUnsupportedOperation();
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            throw newUnsupportedOperation();
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            throw newUnsupportedOperation();
        }

        @Override
        public void clear() {
            throw newUnsupportedOperation();
        }

        @Override
        public V get(int index) {
            return getOr(index, defaultValue);
        }

        @Override
        public V set(int index, V element) {
            throw newUnsupportedOperation();
        }

        @Override
        public void add(int index, V element) {
            throw newUnsupportedOperation();
        }

        @Override
        public V remove(int index) {
            throw newUnsupportedOperation();
        }

        @Override
        public int indexOf(Object o) {
            for (int i = 0; i < size(); i++) {
                if (Objects.equals(o, getOr(i, defaultValue))) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public int lastIndexOf(Object o) {
            for (int i = size() - 1; i >= 0; i--) {
                if (Objects.equals(o, getOr(i, defaultValue))) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public ListIterator<V> listIterator() {
            return new ListIteratorView(defaultValue);
        }

        @Override
        public ListIterator<V> listIterator(int index) {
            return new ListIteratorView(index, length(), defaultValue);
        }

        @Override
        public List<V> subList(int fromIndex, int toIndex) {
            if (fromIndex < 0) {
                throw new IndexOutOfBoundsException("fromIndex: " + fromIndex);
            }
            if (toIndex > size()) {
                throw new IndexOutOfBoundsException("toIndex: " + toIndex);
            }
            if (fromIndex > toIndex) {
                throw new IndexOutOfBoundsException("fromIndex > toIndex: " + fromIndex + ", " + toIndex);
            }
            return new ListView(begin + fromIndex, begin + toIndex, defaultValue);
        }

        @Override
        public void sort(Comparator<? super V> c) {
            throw newUnsupportedOperation();
        }
    }

    final class ListIteratorView implements ListIterator<V> {

        private final V defaultValue;
        private final int begin;
        private final int end;
        private int cursor;

        private ListIteratorView(V defaultValue) {
            this(0, LazyReferenceArray.this.length(), defaultValue);
        }

        private ListIteratorView(int begin,
                                int end,
                                V defaultValue) {
            this.begin = begin;
            this.end = end;
            this.defaultValue = defaultValue;
            this.cursor = begin;
        }

        @Override
        public boolean hasNext() {
            return cursor < end;
        }

        @Override
        public boolean hasPrevious() {
            return cursor != begin;
        }

        @Override
        public V previous() {
            int i = cursor - 1;
            if (i < begin)
                throw new NoSuchElementException();
            cursor = i;
            return getOr(i, defaultValue);
        }

        @Override
        public int nextIndex() {
            return cursor;
        }

        @Override
        public int previousIndex() {
            return cursor - 1;
        }

        @Override
        public void set(V v) {
            throw newUnsupportedOperation();
        }

        @Override
        public void add(V v) {
            throw newUnsupportedOperation();
        }

        @Override
        public V next() {
            var i = cursor + 1;
            if (i >= end) {
                throw new NoSuchElementException();
            }
            cursor = i;
            return getOr(i, defaultValue);
        }

        @Override
        public void remove() {
            throw newUnsupportedOperation();
        }

        @Override
        public void forEachRemaining(Consumer<? super V> action) {
            for (; cursor < end; cursor++) {
                action.accept(getOr(cursor, defaultValue));
            }
        }
    }

    private UnsupportedOperationException newUnsupportedOperation() {
        return new UnsupportedOperationException("Not supported on an unmodifiable list.");
    }

    /**
     * {@return a new empty LazyReferenceArray with no pre-set mapper}.
     * <p>
     * If an attempt is made to invoke the {@link #apply(int)} ()} method when no element is present,
     * an exception will be thrown.
     * <p>
     * {@snippet lang = java:
     *    LazyReferenceArray<T> lazy = LazyReferenceArray.ofEmpty();
     *    T value = lazy.getOrNull(42);
     *    assertIsNull(value); // Value is initially null
     *    // ...
     *    T value = lazy.supplyIfEmpty(42, Value::new);
     *    assertNotNull(value); // Value is non-null
     *}
     *
     * @param <T>  The type of the values
     * @param size the size of the array
     */
    public static <T> LazyReferenceArray<T> of(int size) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        return new LazyReferenceArray<>(size);
    }

    /**
     * {@return a new empty LazyReferenceArray with a pre-set mapper}.
     * <p>
     * If an attempt is made to invoke the {@link #apply(int)} ()} method when no element is present,
     * the provided {@code presetMapper} will automatically be invoked as specified by
     * {@link #computeIfEmpty(int, IntFunction)}.
     * <p>
     * {@snippet lang = java:
     *    LazyReferenceArray<T> lazy = LazyReferenceArray.of(Value::new);
     *    // ...
     *    T value = lazy.get(42);
     *    assertNotNull(value); // Value is never null
     *}
     *
     * @param <T>          The type of the values
     * @param size         the size of the array
     * @param presetMapper to invoke when lazily constructing a value
     * @throws NullPointerException if the provided {@code presetMapper} is {@code null}
     */
    public static <T> LazyReferenceArray<T> of(int size,
                                               IntFunction<? extends T> presetMapper) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        Objects.requireNonNull(presetMapper);
        return new LazyReferenceArray<>(size, presetMapper);
    }

    // Todo: Consider slot/external mapping of indexes

}
