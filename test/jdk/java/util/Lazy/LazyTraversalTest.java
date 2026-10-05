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
 * @summary Failed lazy computations do not consume iterator or spliterator positions
 * @enablePreview
 * @run junit LazyTraversalTest
 */

import java.lang.util.Lazy;
import java.lang.util.Lazy.CacheMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.IntFunction;
import java.util.stream.Stream;

import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static java.lang.util.Lazy.CacheMode.COMPUTE_ONCE;
import static org.junit.jupiter.api.Assertions.*;

class LazyTraversalTest {
    enum Key { A, B, C }

    enum Shape {
        LIST, SUBLIST, REVERSED, MAP_VALUES, ENUM_MAP_VALUES, SET, ENUM_SET;

        Collection<?> create(CacheMode mode, IntFunction<Integer> computation) {
            return switch (this) {
                case LIST -> Lazy.ofListCache(mode, 3, computation);
                case SUBLIST -> Lazy.ofListCache(mode, 5,
                        index -> computation.apply(index - 1)).subList(1, 4);
                case REVERSED -> Lazy.ofListCache(mode, 3,
                        index -> computation.apply(2 - index)).reversed();
                case MAP_VALUES -> Lazy.ofMapCache(mode, Set.of(0, 1, 2), computation::apply).values();
                case ENUM_MAP_VALUES -> Lazy.ofMapCache(mode, EnumSet.allOf(Key.class),
                        key -> computation.apply(key.ordinal())).values();
                case SET -> Lazy.ofSetCache(mode, Set.of(0, 1, 2),
                        key -> computation.apply(key) >= 0);
                case ENUM_SET -> Lazy.ofSetCache(mode, EnumSet.allOf(Key.class),
                        key -> computation.apply(key.ordinal()) >= 0);
            };
        }

        Set<?> expectedValues() {
            return this == ENUM_SET ? EnumSet.allOf(Key.class) : Set.of(0, 1, 2);
        }

        Object element(int index) {
            return this == ENUM_SET ? Key.values()[index] : index;
        }
    }

    static Stream<Arguments> shapesAndModes() {
        return Arrays.stream(Shape.values()).flatMap(shape ->
                Arrays.stream(CacheMode.values()).map(mode -> Arguments.of(shape, mode)));
    }

    static Stream<Arguments> listShapesAndModes() {
        return Stream.of(Shape.LIST, Shape.SUBLIST, Shape.REVERSED).flatMap(shape ->
                Arrays.stream(CacheMode.values()).flatMap(mode ->
                        Stream.of(Arguments.of(shape, mode, false), Arguments.of(shape, mode, true))));
    }

    // Fail the second distinct computation, without assuming an iteration order.
    static final class Computation implements IntFunction<Integer> {
        final IllegalStateException failure = new IllegalStateException("computation");
        final int[] calls = new int[3];
        final int failAt;
        int encountered;
        int failingIndex = -1;

        Computation(int failAt) {
            this.failAt = failAt;
        }

        @Override
        public Integer apply(int index) {
            if (++calls[index] == 1 && ++encountered == failAt) {
                failingIndex = index;
                throw failure;
            }
            return index;
        }

        void assertFirstFailure(CacheMode mode, Executable operation) {
            if (mode == COMPUTE_ONCE) {
                assertSame(failure, assertThrows(NoSuchElementException.class, operation).getCause());
            } else {
                assertSame(failure, assertThrows(IllegalStateException.class, operation));
            }
            assertTrue(failingIndex >= 0);
            assertEquals(1, calls[failingIndex]);
        }

        void assertRecordedFailure(Executable operation) {
            assertNull(assertThrows(NoSuchElementException.class, operation).getCause());
            assertEquals(1, calls[failingIndex]);
        }

        void assertSuccessfulRetry() {
            assertEquals(2, calls[failingIndex]);
        }
    }

    @ParameterizedTest
    @MethodSource("shapesAndModes")
    void iteratorNextDoesNotSkipAFailedValue(Shape shape, CacheMode mode) {
        Computation computation = new Computation(2);
        Iterator<?> iterator = shape.create(mode, computation).iterator();
        List<Object> seen = new ArrayList<>();
        computation.assertFirstFailure(mode, () -> {
            while (iterator.hasNext()) {
                seen.add(iterator.next());
            }
        });
        finishIterator(shape, mode, computation, iterator, seen);
    }

    @ParameterizedTest
    @MethodSource("shapesAndModes")
    void iteratorBulkTraversalDoesNotSkipAFailedValue(Shape shape, CacheMode mode) {
        Computation computation = new Computation(2);
        Iterator<?> iterator = shape.create(mode, computation).iterator();
        List<Object> seen = new ArrayList<>();
        computation.assertFirstFailure(mode, () -> iterator.forEachRemaining(seen::add));
        finishIterator(shape, mode, computation, iterator, seen);
    }

    private static void finishIterator(Shape shape, CacheMode mode, Computation computation,
                                       Iterator<?> iterator, List<Object> seen) {
        if (mode == COMPUTE_ONCE) {
            computation.assertRecordedFailure(() -> iterator.forEachRemaining(seen::add));
            assertFalse(seen.contains(shape.element(computation.failingIndex)));
            assertEquals(seen.size(), new HashSet<>(seen).size());
        } else {
            iterator.forEachRemaining(seen::add);
            assertExhausted(shape, seen);
            assertFalse(iterator.hasNext());
            computation.assertSuccessfulRetry();
        }
    }

    @ParameterizedTest
    @MethodSource("listShapesAndModes")
    void failedListIteratorMovementLeavesItsCursorUnchanged(Shape shape, CacheMode mode, boolean backwards) {
        Computation computation = new Computation(1);
        List<?> list = (List<?>) shape.create(mode, computation);
        int cursor = backwards ? 2 : 1;
        var iterator = list.listIterator(cursor);
        Executable movement = backwards ? iterator::previous : iterator::next;
        computation.assertFirstFailure(mode, movement);
        assertEquals(cursor, iterator.nextIndex());
        assertEquals(cursor - 1, iterator.previousIndex());
        if (mode == COMPUTE_ONCE) {
            computation.assertRecordedFailure(movement);
            assertEquals(cursor, iterator.nextIndex());
            assertEquals(cursor - 1, iterator.previousIndex());
        } else {
            assertEquals(1, backwards ? iterator.previous() : iterator.next());
            assertEquals(backwards ? 1 : 2, iterator.nextIndex());
            assertEquals(backwards ? 0 : 1, iterator.previousIndex());
            computation.assertSuccessfulRetry();
        }
    }

    @ParameterizedTest
    @MethodSource("shapesAndModes")
    void failedTryAdvanceDoesNotInvokeItsActionOrConsumeTheValue(Shape shape, CacheMode mode) {
        Computation computation = new Computation(2);
        Spliterator<?> spliterator = shape.create(mode, computation).spliterator();
        List<Object> seen = new ArrayList<>();
        int[] before = { 0 };
        computation.assertFirstFailure(mode, () -> {
            do {
                before[0] = seen.size();
            } while (spliterator.tryAdvance(seen::add));
        });
        assertEquals(before[0], seen.size(), "a failing tryAdvance must not invoke its action");
        if (spliterator.hasCharacteristics(Spliterator.SIZED)) {
            assertEquals(3 - seen.size(), spliterator.getExactSizeIfKnown());
        }
        finishSpliterator(shape, mode, computation, spliterator, seen);
    }

    @ParameterizedTest
    @MethodSource("shapesAndModes")
    void spliteratorBulkTraversalDoesNotSkipAFailedValue(Shape shape, CacheMode mode) {
        Computation computation = new Computation(2);
        Spliterator<?> spliterator = shape.create(mode, computation).spliterator();
        List<Object> seen = new ArrayList<>();
        computation.assertFirstFailure(mode, () -> spliterator.forEachRemaining(seen::add));
        finishSpliterator(shape, mode, computation, spliterator, seen);
    }

    private static void finishSpliterator(Shape shape, CacheMode mode, Computation computation,
                                          Spliterator<?> spliterator, List<Object> seen) {
        if (mode == COMPUTE_ONCE) {
            computation.assertRecordedFailure(() -> spliterator.forEachRemaining(seen::add));
            assertFalse(seen.contains(shape.element(computation.failingIndex)));
            assertEquals(seen.size(), new HashSet<>(seen).size());
        } else {
            spliterator.forEachRemaining(seen::add);
            assertExhausted(shape, seen);
            assertFalse(spliterator.tryAdvance(_ -> fail("exhausted spliterator invoked its action")));
            computation.assertSuccessfulRetry();
        }
    }

    @ParameterizedTest
    @MethodSource("shapesAndModes")
    void aSplitThatComputesAndFailsKeepsTheOriginalCoverage(Shape shape, CacheMode mode) {
        Computation computation = new Computation(2);
        Spliterator<?> original = shape.create(mode, computation).spliterator();
        Spliterator<?> prefix;
        try {
            prefix = original.trySplit();
        } catch (RuntimeException failure) {
            if (mode == COMPUTE_ONCE) {
                assertInstanceOf(NoSuchElementException.class, failure);
                assertSame(computation.failure, failure.getCause());
            } else {
                assertSame(computation.failure, failure);
            }
            finishSpliterator(shape, mode, computation, original, new ArrayList<>());
            return;
        }
        // Splitting is also permitted to avoid computation entirely. Traverse
        // the returned partitions, retrying a failing partition in place.
        List<Object> seen = new ArrayList<>();
        boolean failed = false;
        for (Spliterator<?> part : prefix == null ? List.of(original) : List.of(prefix, original)) {
            try {
                part.forEachRemaining(seen::add);
            } catch (RuntimeException failure) {
                assertFalse(failed);
                failed = true;
                if (mode == COMPUTE_ONCE) {
                    assertInstanceOf(NoSuchElementException.class, failure);
                    assertSame(computation.failure, failure.getCause());
                    computation.assertRecordedFailure(() -> part.forEachRemaining(seen::add));
                } else {
                    assertSame(computation.failure, failure);
                    part.forEachRemaining(seen::add);
                }
            }
        }
        assertTrue(failed);
        if (mode == COMPUTE_ONCE) {
            assertFalse(seen.contains(shape.element(computation.failingIndex)));
            assertEquals(seen.size(), new HashSet<>(seen).size());
        } else {
            assertExhausted(shape, seen);
            computation.assertSuccessfulRetry();
        }
    }

    private static void assertExhausted(Shape shape, List<Object> seen) {
        assertEquals(3, seen.size(), "an element was skipped or returned twice");
        assertEquals(shape.expectedValues(), new HashSet<>(seen));
    }
}
