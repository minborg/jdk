/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @summary Lazy collection mutation, spliterator, factory, and entry contracts
 * @enablePreview
 * @run junit LazyCollectionContractTest
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LazyCaches;
import java.util.LazyCaches.Mode;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Spliterator;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import static java.util.LazyCaches.Mode.COMPUTE_ONCE;
import static java.util.Spliterator.*;
import static org.junit.jupiter.api.Assertions.*;

class LazyCollectionContractTest {
    private static final int COMMON = NONNULL | IMMUTABLE;
    private static final Set<Integer> KEYS = Set.of(0, 1, 2, 3);

    enum Key { A, B, C, D }

    enum Source {
        LIST, SUBLIST, REVERSED, MAP_KEYS, MAP_VALUES, MAP_ENTRIES,
        ENUM_MAP_KEYS, ENUM_MAP_VALUES, ENUM_MAP_ENTRIES, SET, ENUM_SET;

        Collection<?> create(Mode mode, IntFunction<Integer> computation) {
            return switch (this) {
                case LIST -> LazyCaches.ofList(mode, 4, computation);
                case SUBLIST -> LazyCaches.ofList(mode, 4, computation).subList(1, 3);
                case REVERSED -> LazyCaches.ofList(mode, 4, computation).reversed();
                case MAP_KEYS, MAP_VALUES, MAP_ENTRIES -> {
                    var map = LazyCaches.ofMap(mode, KEYS, computation::apply);
                    yield switch (this) {
                        case MAP_KEYS -> map.keySet();
                        case MAP_VALUES -> map.values();
                        default -> map.entrySet();
                    };
                }
                case ENUM_MAP_KEYS, ENUM_MAP_VALUES, ENUM_MAP_ENTRIES -> {
                    var map = LazyCaches.ofMap(mode, EnumSet.allOf(Key.class),
                            key -> computation.apply(key.ordinal()));
                    yield switch (this) {
                        case ENUM_MAP_KEYS -> map.keySet();
                        case ENUM_MAP_VALUES -> map.values();
                        default -> map.entrySet();
                    };
                }
                case SET -> LazyCaches.ofSet(mode, KEYS, key -> computation.apply(key) != 1);
                case ENUM_SET -> LazyCaches.ofSet(mode, EnumSet.allOf(Key.class),
                        key -> computation.apply(key.ordinal()) != 1);
            };
        }

        int characteristics() {
            return COMMON | switch (this) {
                case LIST, SUBLIST, REVERSED -> ORDERED | SIZED | SUBSIZED;
                case MAP_KEYS, MAP_ENTRIES, ENUM_MAP_KEYS, ENUM_MAP_ENTRIES ->
                        DISTINCT | SIZED | SUBSIZED;
                case MAP_VALUES, ENUM_MAP_VALUES -> SIZED | SUBSIZED;
                case SET, ENUM_SET -> DISTINCT;
            };
        }

        boolean readsValues() {
            return switch (this) {
                case MAP_KEYS, MAP_ENTRIES, ENUM_MAP_KEYS, ENUM_MAP_ENTRIES -> false;
                default -> true;
            };
        }

        int size() {
            return switch (this) {
                case SUBLIST -> 2;
                case SET, ENUM_SET -> 3;
                default -> 4;
            };
        }
    }

    static Stream<Arguments> sourcesAndModes() {
        return Arrays.stream(Source.values()).flatMap(source ->
                Arrays.stream(Mode.values()).map(mode -> Arguments.of(source, mode)));
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void rejectedListMutationsDoNotComputeOrInvokeCallbacks(Mode mode) {
        AtomicInteger calls = new AtomicInteger();
        List<Integer> list = LazyCaches.ofList(mode, 3, i -> {
            calls.incrementAndGet();
            return i;
        });
        for (List<Integer> view : List.of(list, list.subList(1, 3), list.reversed(),
                list.reversed().subList(1, 3), list.subList(0, 0))) {
            assertRejectedCollectionMutations(view, 99);
            assertThrows(UnsupportedOperationException.class,
                    () -> view.addAll(0, List.of()));
            assertThrows(UnsupportedOperationException.class,
                    () -> view.replaceAll(_ -> fail("replaceAll callback")));
            assertThrows(UnsupportedOperationException.class,
                    () -> view.sort((_, _) -> fail("sort comparator")));
            assertThrows(UnsupportedOperationException.class, () -> view.addFirst(99));
            assertThrows(UnsupportedOperationException.class, () -> view.addLast(99));
            assertThrows(UnsupportedOperationException.class, view::removeFirst);
            assertThrows(UnsupportedOperationException.class, view::removeLast);
            assertThrows(UnsupportedOperationException.class,
                    () -> view.listIterator().add(99));
            if (!view.isEmpty()) {
                assertThrows(UnsupportedOperationException.class, () -> view.set(0, 99));
                assertThrows(UnsupportedOperationException.class, () -> view.remove(0));
            }
        }
        assertEquals(0, calls.get());

        for (List<Integer> view : List.of(list, list.subList(1, 3), list.reversed())) {
            var iterator = view.listIterator();
            iterator.next();
            int before = calls.get();
            assertThrows(UnsupportedOperationException.class, iterator::remove);
            assertThrows(UnsupportedOperationException.class, () -> iterator.set(99));
            assertThrows(UnsupportedOperationException.class, () -> iterator.add(99));
            assertEquals(before, calls.get());
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void rejectedMapAndSetMutationsDoNotComputeOrInvokeCallbacks(Mode mode) {
        for (boolean empty : List.of(false, true)) {
            AtomicInteger calls = new AtomicInteger();
            Set<Integer> keys = empty ? Set.of() : KEYS;
            Map<Integer, Integer> map = LazyCaches.ofMap(mode, keys, key -> {
                calls.incrementAndGet();
                return key;
            });
            assertThrows(UnsupportedOperationException.class, () -> map.putAll(Map.of()));
            assertThrows(UnsupportedOperationException.class, () -> map.putIfAbsent(0, 99));
            assertThrows(UnsupportedOperationException.class, () -> map.remove(99, 99));
            assertThrows(UnsupportedOperationException.class, () -> map.replace(99, 99));
            assertThrows(UnsupportedOperationException.class, () -> map.replace(0, 0, 99));
            assertThrows(UnsupportedOperationException.class,
                    () -> map.replaceAll((_, _) -> fail("map replaceAll callback")));
            for (int key : new int[] { 0, 99 }) {
                assertThrows(UnsupportedOperationException.class,
                        () -> map.computeIfAbsent(key, _ -> fail("computeIfAbsent callback")));
                assertThrows(UnsupportedOperationException.class,
                        () -> map.computeIfPresent(key, (_, _) -> fail("computeIfPresent callback")));
                assertThrows(UnsupportedOperationException.class,
                        () -> map.compute(key, (_, _) -> fail("compute callback")));
                assertThrows(UnsupportedOperationException.class,
                        () -> map.merge(key, 99, (_, _) -> fail("merge callback")));
            }
            assertRejectedCollectionMutations(map.keySet(), 99);
            assertRejectedCollectionMutations(map.values(), 99);
            assertRejectedCollectionMutations(map.entrySet(), Map.entry(99, 99));
            if (!empty) {
                var iterator = map.entrySet().iterator();
                var entry = iterator.next();
                assertThrows(UnsupportedOperationException.class, () -> entry.setValue(99));
                assertThrows(UnsupportedOperationException.class, iterator::remove);
            }

            Set<Integer> set = LazyCaches.ofSet(mode, keys, key -> {
                calls.incrementAndGet();
                return true;
            });
            assertRejectedCollectionMutations(set, 99);
            assertEquals(0, calls.get());
            if (!empty) {
                for (Collection<?> collection : List.of(map.keySet(), map.values(), map.entrySet(), set)) {
                    var iterator = collection.iterator();
                    iterator.next();
                    int before = calls.get();
                    assertThrows(UnsupportedOperationException.class, iterator::remove);
                    assertEquals(before, calls.get());
                }
            }
        }
    }

    private static <E> void assertRejectedCollectionMutations(Collection<E> collection, E value) {
        assertThrows(UnsupportedOperationException.class, collection::clear);
        assertThrows(UnsupportedOperationException.class, () -> collection.add(value));
        assertThrows(UnsupportedOperationException.class, () -> collection.addAll(List.of()));
        assertThrows(UnsupportedOperationException.class, () -> collection.remove(value));
        assertThrows(UnsupportedOperationException.class, () -> collection.removeAll(List.of()));
        assertThrows(UnsupportedOperationException.class, () -> collection.retainAll(List.of()));
        assertThrows(UnsupportedOperationException.class,
                () -> collection.removeIf(_ -> fail("removeIf callback")));
    }

    @ParameterizedTest
    @MethodSource("sourcesAndModes")
    void streamAndSpliteratorMetadataAreLazy(Source source, Mode mode) {
        AtomicInteger calls = new AtomicInteger();
        Collection<?> collection = source.create(mode, i -> {
            calls.incrementAndGet();
            return i;
        });
        try (var sequential = collection.stream(); var parallel = collection.parallelStream()) {
            checkMetadata(sequential.spliterator(), source.characteristics(), source.size());
            checkMetadata(parallel.spliterator(), source.characteristics(), source.size());
        }
        Spliterator<?> spliterator = collection.spliterator();
        checkMetadata(spliterator, source.characteristics(), source.size());
        assertEquals(0, calls.get());

        List<Object> values = new ArrayList<>();
        splitAndCollect(spliterator, source.characteristics(), values);
        assertEquals(source.size(), values.size());
        // Hash only keys, since hashing a lazy entry also accesses its value.
        var identities = values.stream().map(value -> value instanceof Map.Entry<?, ?> entry
                ? entry.getKey() : value).toList();
        assertEquals(source.size(), new HashSet<>(identities).size());
        if (!source.readsValues()) {
            assertEquals(0, calls.get());
        }
    }

    private static void checkMetadata(Spliterator<?> spliterator, int required, int expectedSize) {
        assertTrue(spliterator.hasCharacteristics(required),
                () -> "missing characteristics: " + Integer.toHexString(required)
                        + ", actual: " + Integer.toHexString(spliterator.characteristics()));
        int characteristics = spliterator.characteristics();
        assertTrue(spliterator.estimateSize() >= expectedSize);
        if ((characteristics & SIZED) != 0) {
            assertEquals(expectedSize, spliterator.getExactSizeIfKnown());
        } else {
            assertEquals(-1, spliterator.getExactSizeIfKnown());
        }
        assertEquals(characteristics, spliterator.characteristics());
    }

    private static void splitAndCollect(Spliterator<?> spliterator, int required, List<Object> values) {
        assertTrue(spliterator.hasCharacteristics(required));
        long before = spliterator.getExactSizeIfKnown();
        Spliterator<?> prefix = spliterator.trySplit();
        if (prefix == null) {
            spliterator.forEachRemaining(values::add);
        } else {
            if ((required & SUBSIZED) != 0) {
                assertEquals(before, prefix.getExactSizeIfKnown() + spliterator.getExactSizeIfKnown());
            }
            splitAndCollect(prefix, required, values);
            splitAndCollect(spliterator, required, values);
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void computingMembershipElsewhereDoesNotChangeSpliteratorCharacteristics(Mode mode) {
        for (Source source : List.of(Source.SET, Source.ENUM_SET)) {
            AtomicInteger calls = new AtomicInteger();
            Collection<?> set = source.create(mode, i -> {
                calls.incrementAndGet();
                return i;
            });
            Spliterator<?> spliterator = set.spliterator();
            int characteristics = spliterator.characteristics();
            assertEquals(0, calls.get());
            assertEquals(3, set.size());
            assertEquals(4, calls.get());
            assertEquals(characteristics, spliterator.characteristics());
            checkMetadata(spliterator, source.characteristics(), 3);
            assertEquals(4, calls.get());
        }
    }

    @ParameterizedTest
    @MethodSource("sourcesAndModes")
    void metadataDoesNotReportAComputationFailure(Source source, Mode mode) {
        if (!source.readsValues()) {
            return;
        }
        AtomicInteger calls = new AtomicInteger();
        Collection<?> collection = source.create(mode, _ -> {
            calls.incrementAndGet();
            throw new IllegalStateException("computation");
        });
        Class<? extends RuntimeException> failureType = mode == COMPUTE_ONCE
                ? NoSuchElementException.class : IllegalStateException.class;
        assertThrows(failureType, () -> collection.iterator().next());
        int before = calls.get();
        checkMetadata(collection.spliterator(), source.characteristics(), source.size());
        try (var sequential = collection.stream(); var parallel = collection.parallelStream()) {
            checkMetadata(sequential.spliterator(), source.characteristics(), source.size());
            checkMetadata(parallel.spliterator(), source.characteristics(), source.size());
        }
        assertEquals(before, calls.get());
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void factoriesDoNotShareStorageForTheSameArguments(Mode mode) {
        AtomicInteger calls = new AtomicInteger();
        IntFunction<Object> listComputation = _ -> {
            calls.incrementAndGet();
            return new Object();
        };
        var firstList = LazyCaches.ofList(mode, 1, listComputation);
        var secondList = LazyCaches.ofList(mode, 1, listComputation);
        assertNotSame(firstList.getFirst(), secondList.getFirst());
        assertEquals(2, calls.get());

        for (Set<?> keys : List.of(Set.of("key"), EnumSet.of(Key.A))) {
            Function<Object, Object> computation = _ -> {
                calls.incrementAndGet();
                return new Object();
            };
            var first = LazyCaches.ofMap(mode, keys, computation);
            var second = LazyCaches.ofMap(mode, keys, computation);
            Object key = keys.iterator().next();
            int before = calls.get();
            assertNotSame(first.get(key), second.get(key));
            assertEquals(before + 2, calls.get());
        }

        for (Set<?> candidates : List.of(Set.of("key"), EnumSet.of(Key.A))) {
            AtomicInteger predicateCalls = new AtomicInteger();
            java.util.function.Predicate<Object> predicate = _ -> predicateCalls.incrementAndGet() == 2;
            var first = LazyCaches.ofSet(mode, candidates, predicate);
            var second = LazyCaches.ofSet(mode, candidates, predicate);
            Object candidate = candidates.iterator().next();
            assertFalse(first.contains(candidate));
            assertTrue(second.contains(candidate));
            assertFalse(first.contains(candidate));
            assertEquals(2, predicateCalls.get());
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void aPermanentFailureDoesNotInitializeAnotherCollection(Mode mode) {
        AtomicInteger calls = new AtomicInteger();
        IntFunction<Integer> computation = index -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("first collection");
            }
            return index;
        };
        var first = LazyCaches.ofList(mode, 1, computation);
        var second = LazyCaches.ofList(mode, 1, computation);
        Class<? extends RuntimeException> failureType = mode == COMPUTE_ONCE
                ? NoSuchElementException.class : IllegalStateException.class;
        assertThrows(failureType, first::getFirst);
        assertEquals(0, second.getFirst());
        assertEquals(2, calls.get());
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void mapEqualityDoesNotTreatComputationFailuresAsUnequalValues(Mode mode) {
        for (Object key : List.of("key", Key.A)) {
            for (RuntimeException failure : List.of(new NullPointerException("computation"),
                                                   new ClassCastException("computation"))) {
                Set<?> keys = key instanceof Key enumKey ? EnumSet.of(enumKey) : Set.of(key);
                Map<?, Integer> map = LazyCaches.ofMap(mode, keys, _ -> { throw failure; });
                Map<?, Integer> other = Map.of(key, 42);
                if (mode == COMPUTE_ONCE) {
                    assertSame(failure, assertThrows(NoSuchElementException.class,
                            () -> map.equals(other)).getCause());
                } else {
                    assertSame(failure, assertThrows(failure.getClass(), () -> map.equals(other)));
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void mapEqualitySupportsOtherMapsWithIncompatibleKeyTypes(Mode mode) {
        Map<Integer, Integer> other = new TreeMap<>(Map.of(1, 42));
        for (Object key : List.of("key", Key.A)) {
            Set<?> keys = key instanceof Key enumKey ? EnumSet.of(enumKey) : Set.of(key);
            Map<?, Integer> map = LazyCaches.ofMap(mode, keys, _ -> 42);
            assertFalse(map.entrySet().equals(other.entrySet()));
            assertFalse(map.equals(other));
            assertFalse(other.equals(map));
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void mapEqualityPreservesFailuresFromTheOtherLazyMap(Mode mode) {
        for (RuntimeException failure : List.of(new NullPointerException("other map"),
                                               new ClassCastException("other map"))) {
            AtomicInteger ownCalls = new AtomicInteger();
            AtomicInteger otherCalls = new AtomicInteger();
            Map<String, Integer> map = LazyCaches.ofMap(mode, Set.of("key"), _ -> {
                ownCalls.incrementAndGet();
                return 42;
            });
            Map<String, Integer> other = LazyCaches.ofMap(mode, Set.of("key"), _ -> {
                otherCalls.incrementAndGet();
                throw failure;
            });
            if (mode == COMPUTE_ONCE) {
                assertSame(failure, assertThrows(NoSuchElementException.class,
                        () -> map.equals(other)).getCause());
                assertNull(assertThrows(NoSuchElementException.class,
                        () -> map.equals(other)).getCause());
                assertEquals(1, otherCalls.get());
            } else {
                assertSame(failure, assertThrows(failure.getClass(), () -> map.equals(other)));
                assertSame(failure, assertThrows(failure.getClass(), () -> map.equals(other)));
                assertEquals(2, otherCalls.get());
            }
            assertEquals(42, map.get("key"));
            assertEquals(1, ownCalls.get());
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void nullCandidatesAreRejectedBeforeComputation(Mode mode) {
        Set<Integer> input = new HashSet<>();
        input.add(1);
        input.add(null);
        assertThrows(NullPointerException.class,
                () -> LazyCaches.ofMap(mode, input, _ -> fail("map computation during construction")));
        assertThrows(NullPointerException.class,
                () -> LazyCaches.ofSet(mode, input, _ -> fail("set computation during construction")));
        assertThrows(NullPointerException.class, () -> LazyCaches.ofSet(mode, null, _ -> true));
        assertThrows(NullPointerException.class, () -> LazyCaches.ofSet(mode, Set.of(), null));
        assertThrows(NullPointerException.class, () -> LazyCaches.ofSet(null, Set.of(), _ -> true));
    }

    enum EntrySource { ITERATOR, SPLITERATOR, STREAM, ARRAY }

    static Stream<Arguments> entriesAndModes() {
        return Arrays.stream(EntrySource.values()).flatMap(source ->
                Arrays.stream(Mode.values()).flatMap(mode ->
                        Stream.of(Arguments.of(source, mode, false), Arguments.of(source, mode, true))));
    }

    @ParameterizedTest
    @MethodSource("entriesAndModes")
    void entriesOutliveTheirTraversalAndShareLazyStorage(EntrySource source, Mode mode, boolean enumKeys) {
        AtomicInteger calls = new AtomicInteger();
        Object value = new Object();
        Map.Entry<?, Object> entry = detachedFromTraversal(source, mode, enumKeys, () -> {
            calls.incrementAndGet();
            return value;
        });
        assertEquals(0, calls.get());
        Map.Entry<?, Object> copy = Map.Entry.copyOf(entry);
        assertSame(value, copy.getValue());
        assertSame(value, entry.getValue());
        assertEquals(1, calls.get());
    }

    @ParameterizedTest
    @MethodSource("entriesAndModes")
    void entriesKeepTheInitializationModeAfterTraversal(EntrySource source, Mode mode, boolean enumKeys) {
        AtomicInteger calls = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("entry computation");
        Object value = new Object();
        Map.Entry<?, Object> entry = detachedFromTraversal(source, mode, enumKeys, () -> {
            if (calls.incrementAndGet() == 1) {
                throw failure;
            }
            return value;
        });
        assertEquals(0, calls.get());
        if (mode == COMPUTE_ONCE) {
            assertSame(failure, assertThrows(NoSuchElementException.class,
                    () -> Map.Entry.copyOf(entry)).getCause());
            assertNull(assertThrows(NoSuchElementException.class, entry::getValue).getCause());
            assertEquals(1, calls.get());
        } else {
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> Map.Entry.copyOf(entry)));
            assertSame(value, entry.getValue());
            assertEquals(2, calls.get());
        }
    }

    private static Map.Entry<?, Object> detachedFromTraversal(EntrySource source, Mode mode,
                                                            boolean enumKeys, Supplier<Object> computation) {
        Map<?, Object> map = enumKeys
                ? LazyCaches.ofMap(mode, EnumSet.of(Key.A), _ -> computation.get())
                : LazyCaches.ofMap(mode, Set.of("key"), _ -> computation.get());
        return switch (source) {
            case ITERATOR -> {
                var iterator = map.entrySet().iterator();
                var entry = iterator.next();
                assertFalse(iterator.hasNext());
                yield entry;
            }
            case SPLITERATOR -> {
                List<Map.Entry<?, Object>> entries = new ArrayList<>();
                map.entrySet().spliterator().forEachRemaining(entries::add);
                yield entries.getFirst();
            }
            case STREAM -> map.entrySet().stream().toList().getFirst();
            case ARRAY -> {
                Object[] entries = map.entrySet().toArray();
                @SuppressWarnings("unchecked")
                var entry = (Map.Entry<?, Object>) entries[0];
                yield entry;
            }
        };
    }
}
