/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Array;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LazyCaches;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.RandomAccess;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import jdk.internal.lang.LazyConstantImpl;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.AOTSafeClassInitializer;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

/**
 * Container class for lazy collections implementations. Not part of the public API.
 */
@AOTSafeClassInitializer
public final class LazyCollections {

    /**
     * No instances.
     */
    private LazyCollections() { }

    // Unsafe allows LazyCollection classes to be used early in the boot sequence
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    private static UnsupportedOperationException uoe() {
        return new UnsupportedOperationException();
    }

    /** Unmodifiable bases kept here so the implementations do not depend on
     *  package-private classes in java.util. */
    private abstract static class AbstractImmutableCollection<E> extends AbstractCollection<E> {
        @Override public boolean add(E e) { throw uoe(); }
        @Override public boolean addAll(Collection<? extends E> c) { throw uoe(); }
        @Override public void clear() { throw uoe(); }
        @Override public boolean remove(Object o) { throw uoe(); }
        @Override public boolean removeAll(Collection<?> c) { throw uoe(); }
        @Override public boolean removeIf(Predicate<? super E> filter) { throw uoe(); }
        @Override public boolean retainAll(Collection<?> c) { throw uoe(); }
    }

    private abstract static class AbstractImmutableList<E>
            extends AbstractImmutableCollection<E> implements List<E>, RandomAccess {
        @Override public void add(int index, E element) { throw uoe(); }
        @Override public boolean addAll(int index, Collection<? extends E> c) { throw uoe(); }
        @Override public E remove(int index) { throw uoe(); }
        @Override public E removeFirst() { throw uoe(); }
        @Override public E removeLast() { throw uoe(); }
        @Override public void replaceAll(UnaryOperator<E> operator) { throw uoe(); }
        @Override public E set(int index, E element) { throw uoe(); }
        @Override public void sort(Comparator<? super E> c) { throw uoe(); }

        @Override
        public List<E> subList(int fromIndex, int toIndex) {
            subListRangeCheck(fromIndex, toIndex, size());
            return new ImmutableSubList<>(this, fromIndex, toIndex - fromIndex);
        }

        static void subListRangeCheck(int fromIndex, int toIndex, int size) {
            if (fromIndex < 0) {
                throw new IndexOutOfBoundsException("fromIndex = " + fromIndex);
            }
            if (toIndex > size) {
                throw new IndexOutOfBoundsException("toIndex = " + toIndex);
            }
            if (fromIndex > toIndex) {
                throw new IllegalArgumentException("fromIndex(" + fromIndex
                        + ") > toIndex(" + toIndex + ")");
            }
        }

        @Override public Iterator<E> iterator() { return new ImmutableListIterator<>(this, size()); }
        @Override public ListIterator<E> listIterator() { return listIterator(0); }

        @Override
        public ListIterator<E> listIterator(int index) {
            if (index < 0 || index > size()) {
                throw outOfBounds(index);
            }
            return new ImmutableListIterator<>(this, size(), index);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof List<?> other)) {
                return false;
            }
            Iterator<?> it = other.iterator();
            for (int i = 0, s = size(); i < s; i++) {
                if (!it.hasNext() || !Objects.equals(get(i), it.next())) {
                    return false;
                }
            }
            return !it.hasNext();
        }

        @Override
        public int hashCode() {
            int hash = 1;
            for (int i = 0, s = size(); i < s; i++) {
                hash = 31 * hash + Objects.hashCode(get(i));
            }
            return hash;
        }

        @Override public boolean contains(Object o) { return indexOf(o) >= 0; }

        @Override
        public int indexOf(Object o) {
            Objects.requireNonNull(o);
            for (int i = 0, s = size(); i < s; i++) {
                if (o.equals(get(i))) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public int lastIndexOf(Object o) {
            Objects.requireNonNull(o);
            for (int i = size() - 1; i >= 0; i--) {
                if (o.equals(get(i))) {
                    return i;
                }
            }
            return -1;
        }

        @Override public List<E> reversed() { return new ImmutableReversedList<>(this); }

        IndexOutOfBoundsException outOfBounds(int index) {
            return new IndexOutOfBoundsException("Index: " + index + " Size: " + size());
        }
    }

    private static final class ImmutableSubList<E> extends AbstractImmutableList<E> {
        private final AbstractImmutableList<E> root;
        private final int offset;
        private final int size;

        ImmutableSubList(AbstractImmutableList<E> root, int offset, int size) {
            this.root = root;
            this.offset = offset;
            this.size = size;
        }

        @Override public int size() { return size; }
        @Override public E get(int index) { return root.get(offset + Objects.checkIndex(index, size)); }
    }

    private static final class ImmutableReversedList<E> extends AbstractImmutableList<E> {
        private final AbstractImmutableList<E> base;

        ImmutableReversedList(AbstractImmutableList<E> base) {
            this.base = base;
        }

        @Override public int size() { return base.size(); }
        @Override public E get(int index) {
            return base.get(size() - 1 - Objects.checkIndex(index, size()));
        }
        @Override public List<E> reversed() { return base; }
    }

    private static final class ImmutableListIterator<E> implements ListIterator<E> {
        private final List<E> list;
        private final int size;
        private final boolean isListIterator;
        private int cursor;

        ImmutableListIterator(List<E> list, int size) {
            this.list = list;
            this.size = size;
            this.isListIterator = false;
        }

        ImmutableListIterator(List<E> list, int size, int index) {
            this.list = list;
            this.size = size;
            this.cursor = index;
            this.isListIterator = true;
        }

        @Override public boolean hasNext() { return cursor != size; }
        @Override public boolean hasPrevious() {
            if (!isListIterator) throw uoe();
            return cursor != 0;
        }
        @Override public int nextIndex() {
            if (!isListIterator) throw uoe();
            return cursor;
        }
        @Override public int previousIndex() {
            if (!isListIterator) throw uoe();
            return cursor - 1;
        }
        @Override public void remove() { throw uoe(); }
        @Override public void set(E value) { throw uoe(); }
        @Override public void add(E value) { throw uoe(); }

        @Override
        public E next() {
            try {
                int i = cursor;
                E value = list.get(i);
                cursor = i + 1;
                return value;
            } catch (IndexOutOfBoundsException e) {
                throw new NoSuchElementException();
            }
        }

        @Override
        public E previous() {
            if (!isListIterator) throw uoe();
            try {
                int i = cursor - 1;
                E value = list.get(i);
                cursor = i;
                return value;
            } catch (IndexOutOfBoundsException e) {
                throw new NoSuchElementException();
            }
        }
    }

    private abstract static class AbstractImmutableSet<E>
            extends AbstractImmutableCollection<E> implements Set<E> {
        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (!(o instanceof Set<?> other) || other.size() != size()) return false;
            for (Object e : other) {
                if (e == null || !contains(e)) return false;
            }
            return true;
        }

        @Override public abstract int hashCode();
    }

    private abstract static class AbstractImmutableMap<K, V> extends AbstractMap<K, V> {
        @Override public void clear() { throw uoe(); }
        @Override public V compute(K key, BiFunction<? super K, ? super V, ? extends V> rf) { throw uoe(); }
        @Override public V computeIfAbsent(K key, Function<? super K, ? extends V> mf) { throw uoe(); }
        @Override public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> rf) { throw uoe(); }
        @Override public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> rf) { throw uoe(); }
        @Override public V put(K key, V value) { throw uoe(); }
        @Override public void putAll(Map<? extends K, ? extends V> m) { throw uoe(); }
        @Override public V putIfAbsent(K key, V value) { throw uoe(); }
        @Override public V remove(Object key) { throw uoe(); }
        @Override public boolean remove(Object key, Object value) { throw uoe(); }
        @Override public V replace(K key, V value) { throw uoe(); }
        @Override public boolean replace(K key, V oldValue, V newValue) { throw uoe(); }
        @Override public void replaceAll(BiFunction<? super K, ? super V, ? extends V> f) { throw uoe(); }

        @Override
        public V getOrDefault(Object key, V defaultValue) {
            V value = get(key);
            return value != null ? value : defaultValue;
        }
    }

    /** List operations whose inherited implementations lose lazy failures. */
    private abstract static class AbstractLazyList<E>
            extends AbstractImmutableList<E> {

        boolean legacy() { return false; }

        @Override
        public Iterator<E> iterator() {
            return legacy() ? super.iterator() : new LazyListIterator<>(this, 0);
        }

        @Override
        public ListIterator<E> listIterator(int index) {
            if (legacy()) {
                return super.listIterator(index);
            }
            if (index < 0 || index > size()) {
                throw outOfBounds(index);
            }
            return new LazyListIterator<>(this, index);
        }

        @Override
        public List<E> subList(int fromIndex, int toIndex) {
            if (legacy()) {
                return super.subList(fromIndex, toIndex);
            }
            subListRangeCheck(fromIndex, toIndex, size());
            return new LazyListView<>(this, fromIndex, toIndex - fromIndex, 1);
        }

        @Override
        public List<E> reversed() {
            return legacy() ? super.reversed()
                    : new LazyListView<>(this, size() - 1, size(), -1);
        }

        @Override
        public Spliterator<E> spliterator() {
            return legacy() ? super.spliterator()
                    : new IndexedSpliterator<>(this::get, 0, size(), Spliterator.ORDERED);
        }

        @Override
        public int indexOf(Object value) {
            Objects.requireNonNull(value);
            for (int i = 0; i < size(); i++) {
                if (value.equals(get(i))) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public int lastIndexOf(Object value) {
            Objects.requireNonNull(value);
            for (int i = size() - 1; i >= 0; i--) {
                if (value.equals(get(i))) {
                    return i;
                }
            }
            return -1;
        }
    }

    /** A flattened view keeps both random access and the original lazy storage. */
    private static final class LazyListView<E> extends AbstractLazyList<E> {
        private final AbstractLazyList<E> root;
        private final int offset;
        private final int size;
        private final int direction;

        LazyListView(AbstractLazyList<E> list, int offset, int size, int direction) {
            if (list instanceof LazyListView<E> view) {
                this.root = view.root;
                this.offset = view.offset + view.direction * offset;
                this.direction = view.direction * direction;
            } else {
                this.root = list;
                this.offset = offset;
                this.direction = direction;
            }
            this.size = size;
        }

        @Override public int size() { return size; }

        @Override
        public E get(int index) {
            return root.get(offset + direction * Objects.checkIndex(index, size));
        }
    }

    /** Bounds are checked separately so computation exceptions are not translated. */
    private static final class LazyListIterator<E> implements ListIterator<E> {
        private final List<E> list;
        private int cursor;

        LazyListIterator(List<E> list, int cursor) {
            this.list = list;
            this.cursor = cursor;
        }

        @Override public boolean hasNext() { return cursor < list.size(); }
        @Override public boolean hasPrevious() { return cursor > 0; }
        @Override public int nextIndex() { return cursor; }
        @Override public int previousIndex() { return cursor - 1; }
        @Override public void remove() { throw uoe(); }
        @Override public void set(E value) { throw uoe(); }
        @Override public void add(E value) { throw uoe(); }

        @Override
        public E next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            E value = list.get(cursor);
            cursor++;
            return value;
        }

        @Override
        public E previous() {
            if (!hasPrevious()) {
                throw new NoSuchElementException();
            }
            E value = list.get(cursor - 1);
            cursor--;
            return value;
        }
    }

    private static final class IndexedIterator<E> implements Iterator<E> {
        private final IntFunction<? extends E> access;
        private final int fence;
        private int index;

        IndexedIterator(IntFunction<? extends E> access, int fence) {
            this.access = access;
            this.fence = fence;
        }

        @Override public boolean hasNext() { return index < fence; }

        @Override
        public E next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            E value = access.apply(index);
            index++;
            return value;
        }
    }

    /** Splitting and metadata never access lazy values. */
    private static final class IndexedSpliterator<E> implements Spliterator<E> {
        private final IntFunction<? extends E> access;
        private final int fence;
        private final int characteristics;
        private int index;

        IndexedSpliterator(IntFunction<? extends E> access, int index,
                           int fence, int characteristics) {
            this.access = access;
            this.index = index;
            this.fence = fence;
            this.characteristics = characteristics | NONNULL | IMMUTABLE | SIZED | SUBSIZED;
        }

        @Override public long estimateSize() { return fence - index; }
        @Override public int characteristics() { return characteristics; }

        @Override
        public boolean tryAdvance(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            if (index >= fence) {
                return false;
            }
            E value = access.apply(index);
            index++;
            action.accept(value);
            return true;
        }

        @Override
        public Spliterator<E> trySplit() {
            int midpoint = (index + fence) >>> 1;
            if (index >= midpoint) {
                return null;
            }
            var prefix = new IndexedSpliterator<E>(access, index, midpoint, characteristics);
            index = midpoint;
            return prefix;
        }
    }

    @jdk.internal.vm.annotation.TrustFinalFields
    @jdk.internal.ValueBased
    static final class LazyList<E>
            extends AbstractLazyList<E> {

        @Stable
        private final E[] elements;
        private final LazyCaches.Mode mode;
        // Keeping track of `size` separately reduces bytecode size compared to
        // using `elements.length`.
        private final int size;
        private final FunctionHolder<IntFunction<? extends E>> functionHolder;
        private final Mutexes mutexes;
        private final Throwables throwables;

        private LazyList(LazyCaches.Mode mode, int size,
                         IntFunction<? extends E> computingFunction) {
            this.elements = newGenericArray(size);
            this.mode = mode;
            this.size = size;
            this.functionHolder = new FunctionHolder<>(computingFunction, size);
            this.mutexes = mode == LazyCaches.Mode.PUBLISH_ONCE ? null : new Mutexes(size);
            this.throwables = mode == LazyCaches.Mode.PUBLISH_ONCE ? null
                    : new Throwables(size, mode == LazyCaches.Mode.COMPUTE_ONCE);
            super();
        }

        @Override public boolean  isEmpty() { return size == 0; }
        @Override public int      size() { return size; }
        @Override public Object[] toArray() { return copyInto(new Object[size]); }
        @Override boolean legacy() { return mode == null; }

        @ForceInline
        @Override
        public E get(int i) {
            final E e = contents(offsetFor(Objects.checkIndex(i, size)));
            return (e != null) ? e : getSlowPath(i);
        }

        private E getSlowPath(int i) {
            if (mode == LazyCaches.Mode.PUBLISH_ONCE) {
                return orElseComputeRacy(elements, i, i, functionHolder, true);
            }
            return orElseComputeSlowPath(elements,
                    i, mutexes, throwables, i, functionHolder, mode != null, true);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T[] toArray(T[] a) {
            if (a.length < size) {
                // Make a new array of a's runtime type, but my contents:
                T[] n = (T[]) Array.newInstance(a.getClass().getComponentType(), size);
                return copyInto(n);
            }
            copyInto(a);
            if (a.length > size) {
                a[size] = null; // null-terminate
            }
            return a;
        }

        @Override
        public int indexOf(Object o) {
            Objects.requireNonNull(o);
            for (int i = 0; i < size; i++) {
                if (Objects.equals(o, get(i))) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public int lastIndexOf(Object o) {
            Objects.requireNonNull(o);
            for (int i = size - 1; i >= 0; i--) {
                if (Objects.equals(o, get(i))) {
                    return i;
                }
            }
            return -1;
        }

        @SuppressWarnings("unchecked")
        private <T> T[] copyInto(Object[] a) {
            for (int i = 0; i < size; i++) {
                a[i] = get(i);
            }
            return (T[]) a;
        }

        @SuppressWarnings("unchecked")
        @ForceInline
        private E contents(long offset) {
            return (E) UNSAFE.getReferenceAcquire(elements, offset);
        }

    }

    @jdk.internal.vm.annotation.TrustFinalFields
    private static final class LazyEnumMap<K extends Enum<K>, V>
            extends AbstractLazyMap<K, V> {

        private final Class<K> enumType;
        // We are using a wrapper class here to be able to use a min value of zero that
        // is also stable.
        private final Integer min;
        private final IntPredicate member;

        public LazyEnumMap(LazyCaches.Mode mode,
                           Set<K> set,
                           Class<K> enumType,
                           int min,
                           int backingSize,
                           IntPredicate member,
                           Function<? super K, ? extends V> computingFunction) {
            this.enumType = enumType;
            this.min = min;
            this.member = member;
            super(mode, set, set.size(), backingSize, computingFunction);
        }

        @Override
        @ForceInline
        public boolean containsKey(Object o) {
            return enumType.isAssignableFrom(o.getClass())
                    && member.test(((Enum<?>) o).ordinal());
        }

        @ForceInline
        @Override
        public V getOrDefault(Object key, V defaultValue) {
            if (enumType.isAssignableFrom(key.getClass())) {
                final int ordinal = ((Enum<?>) key).ordinal();
                if (member.test(ordinal)) {
                    @SuppressWarnings("unchecked")
                    final K k = (K) key;
                    return orElseCompute(k, indexFor(k));
                }
            }
            return defaultValue;
        }

        @ForceInline
        @Override
        int indexFor(K key) {
            return key.ordinal() - min;
        }

        @Override
        K canonicalKey(K key, int index) {
            // Enum equality is identity, so a matching lookup key is retained.
            return key;
        }

    }

    @jdk.internal.vm.annotation.TrustFinalFields
    private static final class LazyMap<K, V>
            extends AbstractLazyMap<K, V> {

        // Use an unmodifiable map with known entries that are @Stable. Lookups through this map can be folded because
        // it is created using Map.ofEntries. This allows us to avoid creating a separate hashing function.
        private final Map<K, Integer> indexMapper;
        @Stable
        private final K[] keysByIndex;

        public LazyMap(LazyCaches.Mode mode, Set<K> keys,
                       Function<? super K, ? extends V> computingFunction) {
            @SuppressWarnings("unchecked")
            final Entry<K, Integer>[] entries = (Entry<K, Integer>[]) new Entry<?, ?>[keys.size()];
            final K[] keysByIndex = newGenericArray(keys.size());
            int i = 0;
            for (K k : keys) {
                keysByIndex[i] = k;
                entries[i] = Map.entry(k, i++);
            }
            this.indexMapper = Map.ofEntries(entries);
            this.keysByIndex = keysByIndex;
            super(mode, keys, i, i, computingFunction);
        }

        @ForceInline
        @Override
        public V getOrDefault(Object key, V defaultValue) {
            final Integer index = indexMapper.get(key);
            if (index != null) {
                @SuppressWarnings("unchecked")
                final K k = (K) key;
                return orElseCompute(k, index);
            }
            return defaultValue;
        }

        @Override public boolean containsKey(Object o) { return indexMapper.containsKey(o); }

        @ForceInline
        @Override
        // This method will throw an NPE if the key does not exist. So, callers need to
        // make sure the key exist before invoking this method.
        int indexFor(K key) {
            return indexMapper.get(key);
        }

        @Override
        K canonicalKey(K key, int index) {
            return keysByIndex[index];
        }
    }

    @jdk.internal.vm.annotation.TrustFinalFields
    private static abstract sealed class AbstractLazyMap<K, V>
            extends AbstractImmutableMap<K, V> {

        private final Mutexes mutexes;
        private final Throwables throwables;
        private final int size;
        private final FunctionHolder<Function<? super K, ? extends V>> functionHolder;
        private final Set<Entry<K, V>> entrySet;
        // This field shadows AbstractMap.values which is of another type
        @Stable
        private final V[] values;
        private final LazyCaches.Mode mode;
        @Stable
        private final K[] iterationKeys;
        // This field shadows AbstractMap.keySet which is not trusted
        private final Set<K> keySet;

        // We are using a `long` here to get stable access even in the case
        // that the 32-bit hash code is zero.
        @Stable
        private long hash;

        private AbstractLazyMap(LazyCaches.Mode mode,
                                Set<K> keySet,
                                int size,
                                int backingSize,
                                Function<? super K, ? extends V> computingFunction) {
            this.size = size;
            this.functionHolder = new FunctionHolder<>(computingFunction, size);
            this.values = newGenericArray(backingSize);
            this.mode = mode;
            this.iterationKeys = mode == null ? null : keySet.toArray(newGenericArray(size));
            this.mutexes = mode == LazyCaches.Mode.PUBLISH_ONCE ? null : new Mutexes(backingSize);
            this.throwables = mode == LazyCaches.Mode.PUBLISH_ONCE ? null
                    : new Throwables(backingSize, mode == LazyCaches.Mode.COMPUTE_ONCE);
            super();
            this.keySet = mode == null ? keySet : new LazyMapKeySet<>(this);
            this.entrySet = LazyMapEntrySet.of(this);
        }

        // Abstract methods
        @Override public abstract boolean containsKey(Object o);
        abstract int indexFor(K key);
        abstract K canonicalKey(K key, int index);

        // Public methods
        @Override public final int              size() { return size; }
        @Override public final boolean          isEmpty() { return size == 0; }
        @Override public final Set<Entry<K, V>> entrySet() { return entrySet; }
        @Override public Set<K>                 keySet() { return keySet; }

        private K keyAt(int index) {
            return iterationKeys[index];
        }

        private V valueAt(int index) {
            K key = keyAt(index);
            return orElseCompute(key, indexFor(key));
        }

        private Entry<K, V> entryAt(int index) {
            return new LazyEntry<>(keyAt(index), this);
        }

        @Override
        public final boolean equals(Object other) {
            if (mode == null) {
                return super.equals(other);
            }
            if (other == this) {
                return true;
            }
            if (!(other instanceof Map<?, ?> map) || map.size() != size) {
                return false;
            }
            for (Entry<?, ?> entry : map.entrySet()) {
                // AbstractMap.equals catches NPE and CCE around this entire
                // operation, which would hide PUBLISH_ONCE computation failures.
                // Look up the other map's keys here rather than passing our
                // keys to a map that might restrict their type (e.g. TreeMap).
                Object key = entry.getKey();
                if (key == null) {
                    return false;
                }
                V value = get(key);
                if (value == null || !value.equals(entry.getValue())) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public final boolean containsValue(Object value) {
            Objects.requireNonNull(value);
            for (K key : keySet) {
                if (value.equals(orElseCompute(key, indexFor(key)))) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public final int hashCode() {
            if (mode != null) {
        // A retained value may itself be mutable. New LazyCaches factories
                // must reflect its current hash code on every invocation.
                return hashCode0();
            }
            // Racy computation
            long h = hash;
            if (h == 0) {
                // Set a bit in the upper 32-bit region of the `long` to
                // cater for the case the lower 32-bit hash is zero.
                hash = h = expandToLong(hashCode0());
            }
            return reduceToInt(h);
        }

        private int hashCode0() {
            int hash = 0;
            for (K key : keySet) {
                hash += key.hashCode() ^ orElseCompute(key, indexFor(key)).hashCode();
            }
            return hash;
        }

        @Override
        public final void forEach(BiConsumer<? super K, ? super V> action) {
            Objects.requireNonNull(action);
            for (K key : keySet) {
                action.accept(key, orElseCompute(key, indexFor(key)));
            }
        }

        @ForceInline
        @Override
        public final V get(Object key) {
            return getOrDefault(key, null);
        }

        @SuppressWarnings("unchecked")
        @ForceInline
        final V orElseCompute(K key, int index) {
            final long offset = offsetFor(index);
            final V v = (V) UNSAFE.getReferenceAcquire(values, offset);
            if (v != null) {
                return v;
            }
            // Computation uses the retained key, even when an equal but
            // distinct lookup object triggered this access. Keep this lookup
            // off the fast path for already initialized values.
            final K retainedKey = canonicalKey(key, index);
            if (mode == LazyCaches.Mode.PUBLISH_ONCE) {
                return orElseComputeRacy(values, index, retainedKey, functionHolder, false);
            }
            return orElseComputeSlowPath(values,
                    index, mutexes, throwables, retainedKey, functionHolder, mode != null, false);
        }

        /** Unlike a detached key snapshot, this view retains all lazy storage. */
        private static final class LazyMapKeySet<K, V>
                extends AbstractImmutableSet<K> {
            private final AbstractLazyMap<K, V> map;

            LazyMapKeySet(AbstractLazyMap<K, V> map) {
                this.map = map;
            }

            @Override public int size() { return map.size; }
            @Override public boolean contains(Object key) { return map.containsKey(key); }

            @Override
            public Iterator<K> iterator() {
                return new IndexedIterator<>(map::keyAt, map.size);
            }

            @Override
            public Spliterator<K> spliterator() {
                return new IndexedSpliterator<>(map::keyAt, 0, map.size, Spliterator.DISTINCT);
            }

            @Override
            public int hashCode() {
                int hash = 0;
                for (K key : map.iterationKeys) {
                    hash += key.hashCode();
                }
                return hash;
            }
        }

        @jdk.internal.vm.annotation.TrustFinalFields
        @jdk.internal.ValueBased
        private static final class LazyMapEntrySet<K, V> extends AbstractImmutableSet<Entry<K, V>> {

            // Use a separate field for the outer class in order to facilitate
            // a trusted field.
            private final AbstractLazyMap<K, V> map;

            private LazyMapEntrySet(AbstractLazyMap<K, V> map) {
                this.map = map;
                super();
            }

            @Override
            public Iterator<Entry<K, V>> iterator() {
                return map.mode == null ? LazyMapIterator.of(map)
                        : new IndexedIterator<>(map::entryAt, map.size);
            }
            @Override public int                   size() { return map.size(); }
            @Override public int                   hashCode() { return map.hashCode(); }

            @Override
            public Spliterator<Entry<K, V>> spliterator() {
                return map.mode == null ? super.spliterator()
                        : new IndexedSpliterator<>(map::entryAt, 0, map.size, Spliterator.DISTINCT);
            }

            // For @ValueBased
            private static <K, V> LazyMapEntrySet<K, V> of(AbstractLazyMap<K, V> outer) {
                return new LazyMapEntrySet<>(outer);
            }

            @jdk.internal.vm.annotation.TrustFinalFields
            @jdk.internal.ValueBased
            static final class LazyMapIterator<K, V> implements Iterator<Entry<K, V>> {

                // Use a separate field for the outer class in order to facilitate
                // a trusted field.
                private final AbstractLazyMap<K, V> map;
                private final Iterator<K> keyIterator;

                private LazyMapIterator(AbstractLazyMap<K, V> map) {
                    this.map = map;
                    this.keyIterator = map.keySet.iterator();
                    super();
                }

                @Override public boolean hasNext() { return keyIterator.hasNext(); }

                @Override
                public Entry<K, V> next() {
                    final K k = keyIterator.next();
                    return new LazyEntry<>(k, map);
                }

                @Override
                public void forEachRemaining(Consumer<? super Entry<K, V>> action) {
                    Objects.requireNonNull(action);
                    final Consumer<? super K> innerAction =
                            new Consumer<>() {
                                @Override
                                public void accept(K key) {
                                    action.accept(new LazyEntry<>(key, map));
                                }
                            };
                    keyIterator.forEachRemaining(innerAction);
                }

                // For @ValueBased
                private static <K, V> LazyMapIterator<K, V> of(AbstractLazyMap<K, V> map) {
                    return new LazyMapIterator<>(map);
                }

            }
        }

        private record LazyEntry<K, V>(@Override K getKey, // trick
                                       AbstractLazyMap<K, V> map) implements Entry<K, V> {

            @Override public V      setValue(V value) { throw uoe(); }
            @Override public V      getValue() { return map.orElseCompute(getKey, map.indexFor(getKey)); }
            @Override public int    hashCode() { return Objects.hashCode(getKey()) ^ Objects.hashCode(getValue()); }
            @Override public String toString() { return getKey() + "=" + getValue(); }

            @Override
            public boolean equals(Object o) {
                return o instanceof Map.Entry<?, ?> e
                        && Objects.equals(getKey(), e.getKey())
                        // Invoke `getValue()` as late as possible to avoid evaluation
                        && Objects.equals(getValue(), e.getValue());
            }

        }

        @Override
        public Collection<V> values() {
            return LazyMapValues.of(this);
        }

        @jdk.internal.vm.annotation.TrustFinalFields
        @jdk.internal.ValueBased
        static final class LazyMapValues<K, V> extends AbstractImmutableCollection<V> {

            // Use a separate field for the outer class in order to facilitate
            // a trusted field.
            private final AbstractLazyMap<K, V> map;

            private LazyMapValues(AbstractLazyMap<K, V> map) {
                this.map = map;
                super();
            }

            @Override
            public Iterator<V> iterator() {
                return map.mode == null ? new LegacyValueIterator<>(map.entrySet().iterator())
                        : new IndexedIterator<>(map::valueAt, map.size);
            }
            @Override public int         size() { return map.size(); }
            @Override public boolean     isEmpty() { return map.isEmpty(); }
            @Override public boolean     contains(Object v) { return map.containsValue(v); }

            @Override
            public Spliterator<V> spliterator() {
                return map.mode == null ? super.spliterator()
                        : new IndexedSpliterator<>(map::valueAt, 0, map.size, 0);
            }

            // For @ValueBased
            private static <K, V> LazyMapValues<K, V> of(AbstractLazyMap<K, V> outer) {
                return new LazyMapValues<>(outer);
            }

            private static final class LegacyValueIterator<K, V> implements Iterator<V> {
                private final Iterator<Entry<K, V>> entries;

                LegacyValueIterator(Iterator<Entry<K, V>> entries) {
                    this.entries = entries;
                }

                @Override public boolean hasNext() { return entries.hasNext(); }
                @Override public V next() { return entries.next().getValue(); }
                @Override public void remove() { throw uoe(); }
            }

        }

    }

    @jdk.internal.vm.annotation.TrustFinalFields
    private static final class LazySet<E>
            extends AbstractImmutableSet<E>
            implements Set<E> {

        private static final long SIZE_OFFSET = UNSAFE.objectFieldOffset(LazySet.class, "size");
        private static final long HASH_OFFSET = UNSAFE.objectFieldOffset(LazySet.class, "hash");

        private final Map<E, Boolean> map;
        private final AbstractLazyMap<E, Boolean> lazyMap;

        // -1 is used as a sentinel value for zero so we can get
        // stable access for all `size` values. `size` is always non-negative.
        @Stable
        private int size;
        // We are using a `long` here to get stable access even in the case
        // that the 32-bit hash code is zero.
        @Stable
        private long hash;

        public LazySet(LazyCaches.Mode mode,
                       Set<? extends E> elementCandidates,
                       Predicate<? super E> computingFunction) {
            this.map = mode == null
                    ? Map.ofLazy(elementCandidates, computingFunction::test)
                    : ofLazyMap(mode, elementCandidates, computingFunction::test);
            super();
            this.lazyMap = mode == null ? null : (AbstractLazyMap<E, Boolean>) map;
        }

        @Override
        public boolean contains(Object o) {
            return map.getOrDefault(o, Boolean.FALSE).booleanValue();
        }

        @Override
        public int hashCode() {
            // Racy computation
            long h = lazyMap == null ? hash : UNSAFE.getLongAcquire(this, HASH_OFFSET);
            if (h == 0) {
                // Set a bit in the upper 32-bit region of the `long` to
                // cater for the case the lower 32-bit hash is zero.
                h = expandToLong(hashCode0());
                if (lazyMap == null) {
                    hash = h;
                } else {
                    // A cached aggregate must publish the membership accesses
                    // that the next reader can consequently avoid.
                    UNSAFE.putLongRelease(this, HASH_OFFSET, h);
                }
            }
            return reduceToInt(h);
        }

        private int hashCode0() {
            int hash = 0;
            for (var e: map.entrySet()) {
                if (e.getValue()) {
                    hash += e.getKey().hashCode();
                }
            }
            return hash;
        }

        @Override
        public Iterator<E> iterator() {
            return new LazySetIterator<>(map.entrySet().iterator(), lazyMap != null);
        }

        @Override
        public Spliterator<E> spliterator() {
            return lazyMap == null ? super.spliterator()
                    : new LazySetSpliterator<>(lazyMap, 0, lazyMap.size());
        }

        @jdk.internal.vm.annotation.TrustFinalFields
        static final class LazySetIterator<E> implements Iterator<E> {

            private final Iterator<Map.Entry<E, Boolean>> iterator;
            private final boolean retainFailure;

            E current;
            private Map.Entry<E, Boolean> pending;

            public LazySetIterator(Iterator<Map.Entry<E, Boolean>> iterator,
                                   boolean retainFailure) {
                this.iterator = iterator;
                this.retainFailure = retainFailure;
                super();
            }

            @Override
            public boolean hasNext() {
                if (current != null) {
                    return true;
                }
                while (pending != null || iterator.hasNext()) {
                    Map.Entry<E, Boolean> e = pending != null ? pending : iterator.next();
                    if (retainFailure) {
                        pending = e;
                    }
                    if (e.getValue()) {
                        current = e.getKey();
                        pending = null;
                        return true;
                    }
                    pending = null;
                }
                return false;
            }

            @Override
            public E next() {
                E e = current;
                if (e != null) {
                    return consumeCurrent(e);
                }
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return consumeCurrent(current);
            }

            private E consumeCurrent(E e) {
                current = null;
                return e;
            }

        }

        /** Candidate ranges can be partitioned without determining membership. */
        private static final class LazySetSpliterator<E> implements Spliterator<E> {
            private final AbstractLazyMap<E, Boolean> map;
            private final int fence;
            private int index;

            LazySetSpliterator(AbstractLazyMap<E, Boolean> map, int index, int fence) {
                this.map = map;
                this.index = index;
                this.fence = fence;
            }

            @Override public long estimateSize() { return fence - index; }
            @Override public int characteristics() { return DISTINCT | NONNULL | IMMUTABLE; }

            @Override
            public boolean tryAdvance(Consumer<? super E> action) {
                Objects.requireNonNull(action);
                while (index < fence) {
                    if (map.valueAt(index)) {
                        E element = map.keyAt(index);
                        index++;
                        action.accept(element);
                        return true;
                    }
                    index++;
                }
                return false;
            }

            @Override
            public Spliterator<E> trySplit() {
                int midpoint = (index + fence) >>> 1;
                if (index >= midpoint) {
                    return null;
                }
                var prefix = new LazySetSpliterator<>(map, index, midpoint);
                index = midpoint;
                return prefix;
            }
        }

        @Override
        public int size() {
            // Racy computation
            int s = lazyMap == null ? size : UNSAFE.getIntAcquire(this, SIZE_OFFSET);
            if (s == 0) {
                s = size0();
                if (s == 0) {
                    s = -1;
                }
                if (lazyMap == null) {
                    size = s;
                } else {
                    UNSAFE.putIntRelease(this, SIZE_OFFSET, s);
                }
            }
            return s == -1 ? 0 : s;
        }

        private int size0() {
            int size = 0;
            for (var e: map.entrySet()) {
                if (e.getValue()) {
                    size++;
                }
            }
            return size;
        }

    }

    private static final class Mutexes {

        private static final Object TOMB_STONE = new Object();

        // Filled on demand and then discarded once it is not needed anymore.
        // A mutex element can only transition like so: `null` -> `new Object()` -> `TOMB_STONE`
        private volatile Object[] mutexes;
        // Used to detect we have computed all elements and no longer need the `mutexes` array
        private volatile AtomicInteger counter;

        private Mutexes(int length) {
            this.mutexes = new Object[length];
            this.counter = new AtomicInteger(length);
        }

        private Object acquireMutex(long offset) {
            // Snapshot
            var mutexes = this.mutexes;
            if (mutexes == null) {
                // We have already computed all the elements and if we end up here
                // there was at least one unchecked exception thrown by the
                // computing function.
                return null;
            }
            // Check if there already is a mutex (Object or TOMB_STONE)
            final Object mutex = UNSAFE.getReferenceVolatile(mutexes, offset);
            if (mutex != null) {
                return mutex;
            }
            // Protect against racy stores of mutex candidates
            final Object candidate = new Object();
            final Object witness = UNSAFE.compareAndExchangeReference(mutexes, offset, null, candidate);
            return witness == null ? candidate : witness;
        }

        private void releaseMutex(long offset) {
            // Replace the old mutex with a tomb stone since now the old mutex can be collected.
            UNSAFE.putReferenceVolatile(mutexes, offset, TOMB_STONE);
            if (counter != null && counter.decrementAndGet() == 0) {
                mutexes = null;
                counter = null;
            }
        }

    }

    /** Holds the throwable class names and categories produced by the computing function.
     * <p>
     * Class names are used instead of Class objects to avoid pinning class loaders after
     * a failed computation.
     * <p>
     * Each slot is written at most once under its initialization monitor. Readers
     * either acquire that monitor or observe publication through a terminal mutex
     * marker or the cleared mutex-array reference.
     */
    private static final class Throwables {

        @Stable
        final String[] throwables;
        // Only the new COMPUTE_ONCE mode distinguishes failures from Errors.
        // Preallocate this array so recording an Error needs no new holder.
        final boolean[] errors;

        Throwables(int size, boolean distinguishErrors) {
            this.throwables = new String[size];
            this.errors = distinguishErrors ? new boolean[size] : null;
            super();
        }

        Optional<String> get(int index) {
            return Optional.ofNullable(throwables[index]);
        }

        boolean isError(int index) {
            return errors != null && errors[index];
        }

        void set(int index, Throwable throwable) {
            if (errors != null && throwable instanceof Error) {
                // Establish terminal failure before obtaining diagnostic data,
                // which can itself fail, for example when memory is exhausted.
                errors[index] = true;
                try {
                    throwables[index] = throwable.getClass().getName();
                } catch (Throwable ignored) {
                    // Later accesses report the failure without a class name.
                }
                return;
            }
            throwables[index] = throwable.getClass().getName().intern();
        }
    }

    @ForceInline
    private static long offsetFor(long index) {
        return Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * index;
    }

    @SuppressWarnings("unchecked")
    private static <E> E[] newGenericArray(int length) {
        return (E[]) new Object[length];
    }


    @SuppressWarnings("unchecked")
    private static <T> T orElseComputeSlowPath(final T[] array,
                                       final int index,
                                       final Mutexes mutexes,
                                       final Throwables throwables,
                                       final Object input,
                                       final FunctionHolder<?> functionHolder,
                                       final boolean acquireAccess,
                                       final boolean indexed) {
        final long offset = offsetFor(index);
        final Object mutex = mutexes.acquireMutex(offset);
        if (mutex == null) {
            // There might be a race where the value is already computed and
            // the mutex is cleared, so we need to re-check the value again
            final T t = (T) UNSAFE.getReferenceAcquire(array, offset);
            if (t != null) {
                return t;
            }
            throwIfPreviousException(index, throwables, input);
            // There must be an exception
            throw cannotReachHere(functionHolder, input);
        }
        preventReentry(mutex, input);
        synchronized (mutex) {
            final T t = acquireAccess
                    ? (T) UNSAFE.getReferenceAcquire(array, offset)
                    : array[index]; // Plain semantics suffice for legacy factories.
            if (t == null) {
                throwIfPreviousException(index, throwables, input);
                try {
                    final T newValue = compute(functionHolder.function(), input, indexed);

                    // The mutex is not reentrant so we know newValue should be returned
                    set(array, index, mutex, newValue);
                    return newValue;
                } catch (Throwable x) {
                    throwables.set(index, x);
                    if (x instanceof Error error && throwables.isError(index)) {
                        throw error;
                    }
                    // Wrap the initial throwable without pinning its class loader.
                    throw noSuchElementException(x.getClass().getName(), input, x);
                } finally {
                    // Reduce the counter and if it reaches zero, clear the reference
                    // to the underlying holder.
                    functionHolder.countDown();

                    // We do not need the mutex anymore
                    mutexes.releaseMutex(offset);
                }
            }
            return t;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T orElseComputeRacy(T[] array, int index, Object input,
                                         FunctionHolder<?> functionHolder, boolean indexed) {
        // Snapshot the function before checking the value. The function may be
        // released once another thread has installed the last missing value.
        final Object function = functionHolder.function();
        final long offset = offsetFor(index);
        final T value = (T) UNSAFE.getReferenceAcquire(array, offset);
        if (value != null) {
            return value;
        }
        // Reading a cleared volatile function makes the winning publication
        // visible to the subsequent acquire array read above.
        assert function != null;
        final T candidate = compute(function, input, indexed);
        final T witness = (T) UNSAFE.compareAndExchangeReferenceRelease(array, offset, null, candidate);
        if (witness != null) {
            // The witness of a release exchange has only plain read semantics.
            // Acquire the winning publication before returning its value.
            return (T) UNSAFE.getReferenceAcquire(array, offset);
        }
        // Only the successful publisher accounts for this slot. Failures and
        // losing computations must not release a still-needed function.
        functionHolder.countDown();
        return candidate;
    }

    @SuppressWarnings("unchecked")
    private static <T> T compute(Object function, Object input, boolean indexed) {
        final T value = indexed
                ? ((IntFunction<T>) function).apply((int) input)
                : ((Function<Object, T>) function).apply(input);
        return Objects.requireNonNull(value);
    }

    private static void throwIfPreviousException(int index, Throwables throwables, Object input) {
        final var throwable = throwables.get(index);
        if (throwables.isError(index)) {
            throw new LazyCaches.InitializationError(throwable.isPresent()
                    ? failureMessage(throwable.get(), input)
                    : "Lazy initialization previously failed with an Error");
        }
        if (throwable.isPresent()) {
            throw noSuchElementException(throwable.get(), input, null);
        }
    }

    private static NoSuchElementException noSuchElementException(String throwableName,
                                                         Object input,
                                                         Throwable cause) {
        return new NoSuchElementException(failureMessage(throwableName, input), cause);
    }

    private static String failureMessage(String throwableName, Object input) {
        final String isolatedToString = LazyConstantImpl.isolateToString(input);
        return "Unable to access the lazy collection because " + throwableName +
                " was thrown at initial computation for input '" + isolatedToString + "'";
    }

    private static InternalError cannotReachHere(FunctionHolder<?> functionHolder, Object input) {
        return new InternalError("cannot reach here: " + functionHolder.function() + " for " + LazyConstantImpl.isolateToString(input));
    }

    private static void preventReentry(Object mutex, Object input) {
        if (Thread.holdsLock(mutex)) {
            throw new IllegalStateException("Recursive initialization of a lazy collection is illegal: " + LazyConstantImpl.isolateToString(input));
        }
    }

    private static <T> void set(T[] array, int index, Object mutex, T newValue) {
        assert Thread.holdsLock(mutex) : index + "didn't hold " + mutex;
        // The monitor makes a plain check sufficient. Publish with release
        // semantics for readers that do not acquire the monitor.
        if (array[index] == null) {
            UNSAFE.putReferenceRelease(array, offsetFor(index), newValue);
        }
    }

    /**
     * Holds a function until every slot has completed initialization. A racing
     * caller snapshots the function before rechecking its slot. If the function
     * has been cleared, that recheck observes the completed initialization.
     *
     * @param <U> the underlying function type
     */
    @AOTSafeClassInitializer
    private static final class FunctionHolder<U> {

        private static final long COUNTER_OFFSET = UNSAFE.objectFieldOffset(FunctionHolder.class, "counter");

        // This field can transition at most once from a non-null reference to
        // null. Volatile access makes clearing visible to racing callers.
        private volatile U function;
        // Used reflectively via Unsafe
        private int counter;

        public FunctionHolder(U function, int counter) {
            this.function = (counter == 0) ? null : function;
            this.counter = counter;
            // Safe publication
            UNSAFE.storeStoreFence();
        }

        @ForceInline
        public U function() {
            return function;
        }

        public void countDown() {
            if (UNSAFE.getAndAddInt(this, COUNTER_OFFSET, -1) == 1) {
                // Do not reference the underlying function anymore so it can be collected.
                function = null;
            }
        }
    }

    // Methods for supporting stable `int` values using a `long` field.

    private static long expandToLong(int value) {
        return (value + (1L << 33));
    }

    private static int reduceToInt(long value) {
        return (int) value;
    }

    // Factories

    public static <E> List<E> ofLazyList(int size,
                                 IntFunction<? extends E> computingFunction) {
        return new LazyList<>(null, size, computingFunction);
    }

    public static <E> List<E> ofLazyList(LazyCaches.Mode mode, int size,
                                  IntFunction<? extends E> computingFunction) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(computingFunction);
        if (size < 0) {
            throw new IllegalArgumentException("Negative size: " + size);
        }
        return new LazyList<>(mode, size, computingFunction);
    }

    public static <K, V> Map<K, V> ofLazyMap(Set<K> keys,
                                     Function<? super K, ? extends V> computingFunction) {
        return new LazyMap<>(null, keys, computingFunction);
    }

    public static <K, V> Map<K, V> ofLazyMap(LazyCaches.Mode mode, Set<? extends K> keys,
                                      Function<? super K, ? extends V> computingFunction) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(computingFunction);
        final Set<K> keyCopies = Set.copyOf(keys);
        return keys instanceof EnumSet<?> && !keyCopies.isEmpty()
                ? ofLazyMapWithEnumKeys(mode, keyCopies, computingFunction)
                : new LazyMap<>(mode, keyCopies, computingFunction);
    }

    public static <E> Set<E> ofLazySet(Set<? extends E> elementCandidates,
                                       Predicate<? super E> computingFunction) {
        return new LazySet<>(null, elementCandidates, computingFunction);
    }

    public static <E> Set<E> ofLazySet(LazyCaches.Mode mode,
                               Set<? extends E> elementCandidates,
                               Predicate<? super E> computingFunction) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(computingFunction);
        return new LazySet<>(mode, elementCandidates, computingFunction);
    }

    public static <K, E extends Enum<E>, V> Map<K, V> ofLazyMapWithEnumKeys(Set<K> keys,
                                                                     Function<? super K, ? extends V> computingFunction) {
        return ofLazyMapWithEnumKeys(null, keys, computingFunction);
    }

    @SuppressWarnings("unchecked")
    private static <K, E extends Enum<E>, V> Map<K, V> ofLazyMapWithEnumKeys(LazyCaches.Mode mode,
                                                                             Set<K> keys,
                                                                             Function<? super K, ? extends V> computingFunction) {
        // The input set is not empty
        final Class<E> enumType = ((E) keys.iterator().next()).getDeclaringClass();
        final BitSet bitSet = new BitSet(enumType.getEnumConstants().length);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (K t : keys) {
            final int ordinal = ((E) t).ordinal();
            min = Math.min(min, ordinal);
            max = Math.max(max, ordinal);
            bitSet.set(ordinal);
        }
        final int backingSize = max - min + 1;
        final IntPredicate member = ImmutableBitSetPredicate.of(bitSet);
        return (Map<K, V>) new LazyEnumMap<>(mode, (Set<E>) keys, enumType,
                min, backingSize, member, (Function<E, V>) computingFunction);
    }

}
