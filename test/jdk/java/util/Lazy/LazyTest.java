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

/* @test
 * @summary Tests for the supplier, function, and collection factories in java.util.Lazy
 * @enablePreview
 * @library /test/lib
 * @run junit LazyTest
 */

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Lazy;
import java.util.Lazy.Mode;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.RandomAccess;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import jdk.test.lib.Utils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import static java.util.Lazy.Mode.COMPUTE_ONCE;
import static java.util.Lazy.Mode.PUBLISH_ONCE;
import static org.junit.jupiter.api.Assertions.*;

class LazyTest {
    private static final long TIMEOUT_SECONDS = Utils.adjustTimeout(30);
    private static final int THREADS = 4;

    // Exercise the common initialization contract through each access shape.
    enum Shape {
        SUPPLIER, CONTEXT_FUNCTION, LIST, MAP;

        <T> Supplier<T> create(Mode mode, Supplier<T> computation) {
            return switch (this) {
                case SUPPLIER -> Lazy.of(mode, computation);
                case CONTEXT_FUNCTION -> {
                    Object context = new Object();
                    Function<Object, T> lazy =
                            Lazy.ofContext(mode, _ -> computation.get());
                    yield () -> lazy.apply(context);
                }
                case LIST -> {
                    List<T> lazy = Lazy.ofList(mode, 1, _ -> computation.get());
                    yield () -> lazy.get(0);
                }
                case MAP -> {
                    Map<String, T> lazy =
                            Lazy.ofMap(mode, Set.of("key"), _ -> computation.get());
                    yield () -> lazy.get("key");
                }
            };
        }
    }

    static Stream<Arguments> shapesAndModes() {
        return Arrays.stream(Shape.values()).flatMap(shape ->
                Arrays.stream(Mode.values()).map(mode -> Arguments.of(shape, mode)));
    }

    @Test
    void defaultModeIsExactlyOnce() {
        AtomicInteger calls = new AtomicInteger();
        IllegalArgumentException failure = new IllegalArgumentException("computation");
        Supplier<Object> lazy = Lazy.of(() -> {
            calls.incrementAndGet();
            throw failure;
        });
        assertSame(failure, assertThrows(NoSuchElementException.class, lazy::get).getCause());
        assertNull(assertThrows(NoSuchElementException.class, lazy::get).getCause());
        assertEquals(1, calls.get());
    }

    @ParameterizedTest
    @MethodSource("shapesAndModes")
    void lazyAndMemoized(Shape shape, Mode mode) {
        AtomicInteger calls = new AtomicInteger();
        Supplier<Object> lazy = shape.create(mode, () -> {
            calls.incrementAndGet();
            return new Object();
        });
        assertEquals(0, calls.get());
        Object value = lazy.get();
        assertSame(value, lazy.get());
        assertSame(value, lazy.get());
        assertEquals(1, calls.get());
    }

    @ParameterizedTest
    @EnumSource(Shape.class)
    void exactlyOnceExceptionIsSticky(Shape shape) {
        for (Throwable failure : List.of(new IOException("computation"),
                                         new IllegalArgumentException("computation"))) {
            AtomicInteger calls = new AtomicInteger();
            Supplier<Object> lazy = shape.create(COMPUTE_ONCE, () -> {
                calls.incrementAndGet();
                return throwUnchecked(failure);
            });
            NoSuchElementException first = assertThrows(NoSuchElementException.class, lazy::get);
            assertSame(failure, first.getCause());
            NoSuchElementException later = assertThrows(NoSuchElementException.class, lazy::get);
            assertNull(later.getCause());
            assertTrue(later.getMessage().contains(failure.getClass().getName()));
            assertEquals(1, calls.get());
        }
    }

    @ParameterizedTest
    @EnumSource(Shape.class)
    void raceRetriesFailures(Shape shape) {
        for (Throwable failure : List.of(new IOException("computation"),
                                         new IllegalArgumentException("computation"),
                                         new AssertionError("computation"))) {
            AtomicInteger calls = new AtomicInteger();
            Object value = new Object();
            Supplier<Object> lazy = shape.create(PUBLISH_ONCE, () -> {
                if (calls.incrementAndGet() == 1) {
                    return throwUnchecked(failure);
                }
                return value;
            });
            assertSame(failure, assertThrows(failure.getClass(), lazy::get));
            assertSame(value, lazy.get());
            assertSame(value, lazy.get());
            assertEquals(2, calls.get());
        }
    }

    @ParameterizedTest
    @MethodSource("shapesAndModes")
    void nullResultFailsAccordingToMode(Shape shape, Mode mode) {
        AtomicInteger calls = new AtomicInteger();
        Object value = new Object();
        Supplier<Object> lazy = shape.create(mode, () ->
                calls.incrementAndGet() == 1 ? null : value);
        if (mode == COMPUTE_ONCE) {
            assertInstanceOf(NullPointerException.class,
                    assertThrows(NoSuchElementException.class, lazy::get).getCause());
            assertNull(assertThrows(NoSuchElementException.class, lazy::get).getCause());
            assertEquals(1, calls.get());
        } else {
            assertThrows(NullPointerException.class, lazy::get);
            assertSame(value, lazy.get());
            assertSame(value, lazy.get());
            assertEquals(2, calls.get());
        }
    }

    @ParameterizedTest
    @EnumSource(Shape.class)
    void exactlyOnceDetectsRecursiveAccess(Shape shape) {
        AtomicReference<Supplier<Object>> reference = new AtomicReference<>();
        Supplier<Object> lazy = shape.create(COMPUTE_ONCE, () -> reference.get().get());
        reference.set(lazy);
        assertInstanceOf(IllegalStateException.class,
                assertThrows(NoSuchElementException.class, lazy::get).getCause());
        assertNull(assertThrows(NoSuchElementException.class, lazy::get).getCause());
    }

    @ParameterizedTest
    @EnumSource(Shape.class)
    void exactlyOnceCanRecoverFromRecursiveAccess(Shape shape) {
        AtomicReference<Supplier<Object>> reference = new AtomicReference<>();
        Object value = new Object();
        Supplier<Object> lazy = shape.create(COMPUTE_ONCE, () -> {
            assertThrows(IllegalStateException.class, () -> reference.get().get());
            return value;
        });
        reference.set(lazy);
        assertSame(value, lazy.get());
        assertSame(value, lazy.get());
    }

    @ParameterizedTest
    @EnumSource(Shape.class)
    void exactlyOnceUnderContention(Shape shape) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch attempting = new CountDownLatch(THREADS);
        CountDownLatch release = new CountDownLatch(1);
        Object value = new Object();
        Supplier<Object> lazy = shape.create(COMPUTE_ONCE, () -> {
            calls.incrementAndGet();
            entered.countDown();
            await(release);
            return value;
        });
        try (var executor = Executors.newFixedThreadPool(THREADS)) {
            List<Future<Object>> results = new ArrayList<>();
            try {
                for (int i = 0; i < THREADS; i++) {
                    results.add(executor.submit(() -> {
                        attempting.countDown();
                        return lazy.get();
                    }));
                }
                await(entered);
                await(attempting);
                assertEquals(1, calls.get());
            } finally {
                release.countDown();
            }
            for (Future<Object> result : results) {
                assertSame(value, result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            assertEquals(1, calls.get());
        }
    }

    @ParameterizedTest
    @EnumSource(Shape.class)
    void competingComputationsReturnTheCasWinner(Shape shape) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(THREADS);
        CountDownLatch release = new CountDownLatch(1);
        Supplier<Object> lazy = shape.create(PUBLISH_ONCE, () -> {
            calls.incrementAndGet();
            entered.countDown();
            await(release);
            // No latch publishes these writes. Losing accesses must acquire
            // the installed candidate's state through the lazy storage.
            return new Payload(7);
        });
        try (var executor = Executors.newFixedThreadPool(THREADS)) {
            List<Future<Object>> results = new ArrayList<>();
            try {
                for (int i = 0; i < THREADS; i++) {
                    results.add(executor.submit(() -> {
                        Payload result = (Payload) lazy.get();
                        assertEquals(8, result.number);
                        assertEquals("7", result.text);
                        assertArrayEquals(new int[] { 7, ~7 }, result.array);
                        return result;
                    }));
                }
                await(entered);
                assertEquals(THREADS, calls.get());
            } finally {
                release.countDown();
            }
            Object winner = results.getFirst().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            for (Future<Object> result : results) {
                assertSame(winner, result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            assertSame(winner, lazy.get());
            assertEquals(THREADS, calls.get());
        }
    }

    @ParameterizedTest
    @EnumSource(Shape.class)
    void racingFailureDoesNotReplacePublishedValue(Shape shape) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        IllegalArgumentException failure = new IllegalArgumentException("losing computation");
        Object value = new Object();
        Supplier<Object> lazy = shape.create(PUBLISH_ONCE, () -> {
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                await(release);
                throw failure;
            }
            return value;
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<IllegalArgumentException> failed = executor.submit(() ->
                    assertThrows(IllegalArgumentException.class, lazy::get));
            try {
                await(entered);
                assertSame(value, executor.submit(lazy::get)
                        .get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
            assertSame(failure, failed.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertSame(value, lazy.get());
            assertEquals(2, calls.get());
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void contextFunctionHasOneValueAndDoesNotBindAtCreation(Mode mode) {
        AtomicInteger calls = new AtomicInteger();
        Function<String, Object> computation = context -> {
            calls.incrementAndGet();
            return context;
        };
        Function<String, Object> first = Lazy.ofContext(mode, computation);
        Function<String, Object> second = Lazy.ofContext(mode, computation);
        assertEquals(0, calls.get());
        assertThrows(NullPointerException.class, () -> first.apply(null));
        assertEquals(0, calls.get());
        String one = new String("one");
        String two = new String("two");
        assertSame(one, first.apply(one));
        assertSame(one, first.apply(two));
        assertSame(two, second.apply(two));
        assertSame(two, second.apply(one));
        assertEquals(2, calls.get());
        assertThrows(NullPointerException.class, () -> first.apply(null));
    }

    @Test
    void failedRacyContextComputationCanUseAnotherContext() {
        Function<String, String> lazy = Lazy.ofContext(PUBLISH_ONCE, context -> {
            if (context.isEmpty()) {
                throw new IllegalArgumentException("empty context");
            }
            return context;
        });
        assertThrows(IllegalArgumentException.class, () -> lazy.apply(""));
        assertEquals("value", lazy.apply("value"));
        assertEquals("value", lazy.apply(""));
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void listViewsShareLazyElements(Mode mode) {
        AtomicIntegerArray calls = new AtomicIntegerArray(4);
        List<Object> lazy = Lazy.ofList(mode, 4, index -> {
            calls.incrementAndGet(index);
            return new Object();
        });
        List<Object> subList = lazy.subList(1, 3);
        List<Object> reversed = lazy.reversed();
        var iterator = subList.listIterator();
        var spliterator = lazy.spliterator();
        assertInstanceOf(RandomAccess.class, lazy);
        assertEquals(4, lazy.size());
        assertFalse(lazy.isEmpty());
        assertEquals(2, subList.size());
        assertTrue(iterator.hasNext());
        assertEquals(4, spliterator.estimateSize());
        assertCounts(calls, 0, 0, 0, 0);
        Object value = iterator.next();
        assertSame(value, lazy.get(1));
        assertSame(value, reversed.get(2));
        assertCounts(calls, 0, 1, 0, 0);
        Object last = lazy.getLast();
        assertSame(last, reversed.getFirst());
        assertCounts(calls, 0, 1, 0, 1);
        assertThrows(IndexOutOfBoundsException.class, () -> lazy.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> lazy.get(4));
        assertCounts(calls, 0, 1, 0, 1);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void mapSnapshotsKeysWithoutComputingValues(Mode mode) {
        AtomicInteger calls = new AtomicInteger();
        String canonicalKey = new String("first");
        Set<String> keys = new HashSet<>(Set.of(canonicalKey, "second"));
        Map<String, Object> lazy = Lazy.ofMap(mode, keys, key -> {
            calls.incrementAndGet();
            return key;
        });
        keys.clear();
        keys.add("third");
        assertEquals(2, lazy.size());
        assertFalse(lazy.isEmpty());
        assertTrue(lazy.containsKey("first"));
        assertFalse(lazy.containsKey("third"));
        assertEquals(Set.of("first", "second"), lazy.keySet());
        assertEquals(2, lazy.entrySet().size());
        assertEquals(2, lazy.values().size());
        Map.Entry<String, Object> firstEntry = null;
        for (Map.Entry<String, Object> entry : lazy.entrySet()) {
            if (entry.getKey().equals("first")) {
                firstEntry = entry;
            }
        }
        assertNotNull(firstEntry);
        assertNull(lazy.get("third"));
        assertEquals(0, calls.get());
        Object first = lazy.get(new String("first"));
        assertSame(canonicalKey, first);
        assertSame(first, lazy.get("first"));
        assertSame(first, firstEntry.getValue());
        assertEquals(1, calls.get());
        assertEquals(Map.of("first", "first", "second", "second"), lazy);
        assertEquals(2, calls.get());
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void collectionsAreUnmodifiable(Mode mode) {
        List<Integer> list = Lazy.ofList(mode, 3, index -> index);
        for (List<Integer> view : List.of(list, list.subList(0, 2), list.reversed())) {
            assertThrows(UnsupportedOperationException.class, () -> view.add(42));
            assertThrows(UnsupportedOperationException.class, () -> view.set(0, 42));
            assertThrows(UnsupportedOperationException.class, () -> view.remove(0));
            assertThrows(UnsupportedOperationException.class, () -> view.removeIf(_ -> false));
            assertThrows(UnsupportedOperationException.class, () -> view.replaceAll(i -> i));
            assertThrows(UnsupportedOperationException.class, view::clear);
            var iterator = view.listIterator();
            iterator.next();
            assertThrows(UnsupportedOperationException.class, iterator::remove);
            assertThrows(UnsupportedOperationException.class, () -> iterator.set(42));
            assertThrows(UnsupportedOperationException.class, () -> iterator.add(42));
        }
        Map<String, Integer> map = Lazy.ofMap(mode, Set.of("first", "second"), String::length);
        assertThrows(UnsupportedOperationException.class, () -> map.put("third", 3));
        assertThrows(UnsupportedOperationException.class, () -> map.remove("first"));
        assertThrows(UnsupportedOperationException.class, () -> map.compute("first", (_, _) -> 3));
        assertThrows(UnsupportedOperationException.class, () -> map.replaceAll((_, value) -> value));
        assertThrows(UnsupportedOperationException.class, map::clear);
        assertThrows(UnsupportedOperationException.class, () -> map.keySet().remove("first"));
        assertThrows(UnsupportedOperationException.class, () -> map.values().remove(5));
        var entries = map.entrySet().iterator();
        Map.Entry<String, Integer> entry = entries.next();
        assertThrows(UnsupportedOperationException.class, () -> entry.setValue(42));
        assertThrows(UnsupportedOperationException.class, entries::remove);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void emptyCollectionsAndNullQueries(Mode mode) {
        for (int size : new int[] { 0, 1 }) {
            AtomicInteger calls = new AtomicInteger();
            List<Integer> list = Lazy.ofList(mode, size, index -> {
                calls.incrementAndGet();
                return index;
            });
            assertFalse(list instanceof Serializable);
            assertThrows(UnsupportedOperationException.class, list::clear);
            assertThrows(UnsupportedOperationException.class, () -> list.removeIf(_ -> false));
            assertThrows(NullPointerException.class, () -> list.contains(null));
            assertThrows(NullPointerException.class, () -> list.indexOf(null));
            assertThrows(NullPointerException.class, () -> list.lastIndexOf(null));
            Map<Integer, Integer> map = Lazy.ofMap(mode,
                    size == 0 ? Set.of() : Set.of(0), key -> {
                        calls.incrementAndGet();
                        return key;
                    });
            assertFalse(map instanceof Serializable);
            assertThrows(UnsupportedOperationException.class, map::clear);
            assertThrows(UnsupportedOperationException.class, () -> map.remove(42));
            assertThrows(UnsupportedOperationException.class, () -> map.values().removeIf(_ -> false));
            assertThrows(NullPointerException.class, () -> map.get(null));
            assertThrows(NullPointerException.class, () -> map.containsKey(null));
            assertThrows(NullPointerException.class, () -> map.containsValue(null));
            assertEquals(0, calls.get());
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void enumKeysHonorInitializationMode(Mode mode) {
        AtomicInteger calls = new AtomicInteger();
        Map<Mode, String> map = Lazy.ofMap(mode, EnumSet.allOf(Mode.class), key -> {
            if (key == COMPUTE_ONCE && calls.incrementAndGet() == 1) {
                throw new IllegalArgumentException("first computation");
            }
            return key.name();
        });
        assertEquals(0, calls.get());
        if (mode == COMPUTE_ONCE) {
            assertThrows(NoSuchElementException.class, () -> map.get(COMPUTE_ONCE));
            assertThrows(NoSuchElementException.class, () -> map.get(COMPUTE_ONCE));
            assertEquals(1, calls.get());
        } else {
            assertThrows(IllegalArgumentException.class, () -> map.get(COMPUTE_ONCE));
            assertEquals(COMPUTE_ONCE.name(), map.get(COMPUTE_ONCE));
            assertEquals(2, calls.get());
        }
        assertEquals(PUBLISH_ONCE.name(), map.get(PUBLISH_ONCE));
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void mapHashCodeTracksMutableValues(Mode mode) {
        Map<String, List<Integer>> lazy = Lazy.ofMap(mode, Set.of("key"),
                _ -> new ArrayList<>(List.of(1)));
        List<Integer> value = lazy.get("key");
        Map<String, List<Integer>> eager = Map.of("key", value);
        Map.Entry<String, List<Integer>> entry = lazy.entrySet().iterator().next();
        int initialHashCode = eager.hashCode();
        assertEquals(initialHashCode, lazy.hashCode());
        assertEquals(initialHashCode, lazy.entrySet().hashCode());
        assertEquals(initialHashCode, entry.hashCode());

        value.add(2);
        assertNotEquals(initialHashCode, eager.hashCode());
        assertEquals(eager, lazy);
        assertEquals(lazy, eager);
        assertEquals(eager.hashCode(), lazy.hashCode());
        assertEquals(eager.entrySet().hashCode(), lazy.entrySet().hashCode());
        assertEquals(eager.entrySet().iterator().next().hashCode(), entry.hashCode());
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void collectionFailuresArePerElement(Mode mode) {
        AtomicIntegerArray listCalls = new AtomicIntegerArray(2);
        List<Integer> list = Lazy.ofList(mode, 2, index -> {
            if (listCalls.incrementAndGet(index) == 1 && index == 0) {
                throw new IllegalArgumentException("first element");
            }
            return index;
        });
        Class<? extends Throwable> failure = mode == COMPUTE_ONCE
                ? NoSuchElementException.class : IllegalArgumentException.class;
        assertThrows(failure, () -> list.get(0));
        assertEquals(1, list.get(1));
        assertEquals(1, list.get(1));
        if (mode == COMPUTE_ONCE) {
            assertThrows(NoSuchElementException.class, () -> list.get(0));
            assertCounts(listCalls, 1, 1);
        } else {
            assertEquals(0, list.get(0));
            assertCounts(listCalls, 2, 1);
        }

        AtomicIntegerArray mapCalls = new AtomicIntegerArray(2);
        Map<Integer, Integer> map = Lazy.ofMap(mode, Set.of(0, 1), key -> {
            if (mapCalls.incrementAndGet(key) == 1 && key == 0) {
                throw new IllegalArgumentException("first value");
            }
            return key;
        });
        assertThrows(failure, () -> map.get(0));
        assertEquals(1, map.get(1));
        assertEquals(1, map.get(1));
        if (mode == COMPUTE_ONCE) {
            assertThrows(NoSuchElementException.class, () -> map.get(0));
            assertCounts(mapCalls, 1, 1);
        } else {
            assertEquals(0, map.get(0));
            assertCounts(mapCalls, 2, 1);
        }
    }

    @Test
    void factoryArguments() {
        assertThrows(NullPointerException.class, () -> Lazy.of((Supplier<Object>) null));
        assertThrows(NullPointerException.class, () -> Lazy.of(null, Object::new));
        assertThrows(NullPointerException.class, () ->
                Lazy.ofContext(null, (Function<Object, Object>) Function.identity()));
        for (Mode mode : Mode.values()) {
            assertThrows(NullPointerException.class, () -> Lazy.of(mode, (Supplier<Object>) null));
            assertThrows(NullPointerException.class, () ->
                    Lazy.ofContext(mode, (Function<Object, Object>) null));
            assertThrows(IllegalArgumentException.class, () -> Lazy.ofList(mode, -1, i -> i));
            assertThrows(NullPointerException.class, () -> Lazy.ofList(mode, 0, null));
            assertThrows(NullPointerException.class, () -> Lazy.ofMap(mode, null, Function.identity()));
            assertThrows(NullPointerException.class, () -> Lazy.ofMap(mode, Set.of(), null));
            Set<Object> nullKeys = new HashSet<>();
            nullKeys.add(null);
            assertThrows(NullPointerException.class, () ->
                    Lazy.ofMap(mode, nullKeys, Function.identity()));
            assertTrue(Lazy.ofList(mode, 0, _ -> fail("empty list computed an element")).isEmpty());
            assertTrue(Lazy.ofMap(mode, Set.of(), _ -> fail("empty map computed a value")).isEmpty());
        }
        assertThrows(NullPointerException.class, () -> Lazy.ofList(null, 0, i -> i));
        assertThrows(NullPointerException.class, () -> Lazy.ofMap(null, Set.of(), Function.identity()));
    }

    @ParameterizedTest
    @MethodSource("shapesAndModes")
    void publishesNonFinalFields(Shape shape, Mode mode) throws Exception {
        int rounds = 256;
        List<Supplier<Payload>> lazies = new ArrayList<>();
        for (int i = 0; i < rounds; i++) {
            int value = i;
            lazies.add(shape.create(mode, () -> new Payload(value)));
        }
        CyclicBarrier startRound = new CyclicBarrier(THREADS);
        try (var executor = Executors.newFixedThreadPool(THREADS)) {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                results.add(executor.submit(() -> {
                    for (int round = 0; round < rounds; round++) {
                        // The barrier precedes construction, so it cannot publish
                        // the payload's writes from this round to its readers.
                        startRound.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                        Payload value = lazies.get(round).get();
                        assertEquals(round + 1, value.number);
                        assertEquals(Integer.toString(round), value.text);
                        assertArrayEquals(new int[] { round, ~round }, value.array);
                    }
                    return null;
                }));
            }
            for (Future<?> result : results) {
                result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        }
    }

    private static final class Payload {
        int number;
        String text;
        int[] array;

        Payload(int value) {
            number = value + 1;
            text = Integer.toString(value);
            array = new int[] { value, ~value };
        }
    }

    private static void assertCounts(AtomicIntegerArray counts, int... expected) {
        assertEquals(expected.length, counts.length());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], counts.get(i), "computation count for index " + i);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "latch timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T, E extends Throwable> T throwUnchecked(Throwable failure) throws E {
        throw (E) failure;
    }
}
