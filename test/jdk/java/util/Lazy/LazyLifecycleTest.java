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
 * @summary Lazy publication, execution context, interruption, composition, and callback lifetime
 * @enablePreview
 * @library /test/lib
 * @run junit LazyLifecycleTest
 */

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Lazy;
import java.util.Lazy.Mode;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import jdk.test.lib.Utils;
import jdk.test.lib.util.ForceGC;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static java.util.Lazy.Mode.COMPUTE_ONCE;
import static java.util.Lazy.Mode.PUBLISH_ONCE;
import static org.junit.jupiter.api.Assertions.*;

class LazyLifecycleTest {
    private static final long TIMEOUT_SECONDS = Utils.adjustTimeout(30);

    enum Shape {
        SUPPLIER, CONTEXT, LIST, MAP, SET;

        IntFunction<Boolean> create(Mode mode, int size, IntFunction<Boolean> compute) {
            return switch (this) {
                case SUPPLIER -> {
                    assertEquals(1, size);
                    var lazy = Lazy.of(mode, () -> compute.apply(0));
                    yield _ -> lazy.get();
                }
                case CONTEXT -> {
                    assertEquals(1, size);
                    Function<Integer, Boolean> lazy = Lazy.ofContext(mode, compute::apply);
                    yield lazy::apply;
                }
                case LIST -> Lazy.ofList(mode, size, compute)::get;
                case MAP -> Lazy.ofMap(mode, keys(size), compute::apply)::get;
                case SET -> Lazy.ofSet(mode, keys(size), compute::apply)::contains;
            };
        }

        boolean isCollection() {
            return this == LIST || this == MAP || this == SET;
        }
    }

    private static Set<Integer> keys(int size) {
        return switch (size) {
            case 0 -> Set.of();
            case 1 -> Set.of(0);
            case 2 -> Set.of(0, 1);
            default -> throw new AssertionError(size);
        };
    }

    @ParameterizedTest
    @EnumSource(Shape.class)
    void recordedFailuresPublishComputingThreadActions(Shape shape) throws Exception {
        for (boolean error : new boolean[] { false, true }) {
            int rounds = 64;
            int[] written = new int[rounds];
            List<IntFunction<Boolean>> lazies = new ArrayList<>();
            for (int i = 0; i < rounds; i++) {
                int round = i;
                lazies.add(shape.create(COMPUTE_ONCE, 1, _ -> {
                    written[round] = round + 1;
                    if (error) {
                        throw new LinkageError("computation");
                    }
                    throw new IllegalArgumentException("computation");
                }));
            }
            CyclicBarrier start = new CyclicBarrier(4);
            try (var executor = Executors.newFixedThreadPool(4)) {
                List<Future<?>> readers = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    readers.add(executor.submit(() -> {
                        for (int round = 0; round < rounds; round++) {
                            // The barrier precedes the write; it cannot publish it.
                            start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                            var lazy = lazies.get(round);
                            if (error) {
                                assertThrows(Error.class, () -> lazy.apply(0));
                            } else {
                                assertThrows(NoSuchElementException.class, () -> lazy.apply(0));
                            }
                            assertEquals(round + 1, written[round]);
                        }
                        return null;
                    }));
                }
                for (var reader : readers) {
                    reader.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Shape.class)
    void waitingPreservesInterruptionOnNormalAndAbruptCompletion(Shape shape) throws Exception {
        for (int outcome = 0; outcome < 3; outcome++) {
            for (boolean alreadyInterrupted : new boolean[] { false, true }) {
                int result = outcome;
                CountDownLatch entered = new CountDownLatch(1);
                CountDownLatch release = new CountDownLatch(1);
                var lazy = shape.create(COMPUTE_ONCE, 1, _ -> {
                    entered.countDown();
                    await(release);
                    return finish(result);
                });
                FutureTask<Boolean> computation = new FutureTask<>(() -> {
                    if (result == 0) {
                        assertTrue(lazy.apply(0));
                    } else if (result == 1) {
                        assertThrows(NoSuchElementException.class, () -> lazy.apply(0));
                    } else {
                        assertThrows(LinkageError.class, () -> lazy.apply(0));
                    }
                    return true;
                });
                CountDownLatch attempting = new CountDownLatch(1);
                FutureTask<Boolean> access = new FutureTask<>(() -> {
                    if (alreadyInterrupted) {
                        Thread.currentThread().interrupt();
                    }
                    attempting.countDown();
                    if (result == 0) {
                        assertTrue(lazy.apply(0));
                    } else if (result == 1) {
                        assertThrows(NoSuchElementException.class, () -> lazy.apply(0));
                    } else {
                        assertThrows(Lazy.InitializationError.class, () -> lazy.apply(0));
                    }
                    return Thread.currentThread().isInterrupted();
                });
                Thread computing = Thread.ofPlatform().unstarted(computation);
                Thread waiting = Thread.ofPlatform().unstarted(access);
                computing.start();
                try {
                    await(entered);
                    waiting.start();
                    await(attempting);
                    assertTrue(Utils.waitForCondition(() -> switch (waiting.getState()) {
                        case BLOCKED, WAITING, TIMED_WAITING -> true;
                        default -> false;
                    }, TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS)));
                    if (!alreadyInterrupted) {
                        waiting.interrupt();
                    }
                    assertFalse(access.isDone());
                } finally {
                    release.countDown();
                }
                assertTrue(computation.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
                assertTrue(access.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
        }
    }

    private static boolean finish(int outcome) {
        return switch (outcome) {
            case 0 -> true;
            case 1 -> throw new IllegalArgumentException("computation");
            case 2 -> throw new LinkageError("computation");
            default -> throw new AssertionError(outcome);
        };
    }

    @ParameterizedTest
    @EnumSource(Shape.class)
    void computationRunsOnAccessingThread(Shape shape) {
        Thread accessing = Thread.currentThread();
        for (Mode mode : Mode.values()) {
            var lazy = shape.create(mode, 1, _ -> {
                assertSame(accessing, Thread.currentThread());
                return true;
            });
            assertTrue(lazy.apply(0));
        }
    }

    @ParameterizedTest
    @EnumSource(value = Shape.class, names = { "LIST", "MAP", "SET" })
    void differentSlotsCanComputeConcurrently(Shape shape) throws Exception {
        CyclicBarrier bothComputing = new CyclicBarrier(2);
        var lazy = shape.create(COMPUTE_ONCE, 2, _ -> {
            try {
                bothComputing.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            return true;
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> lazy.apply(0));
            var second = executor.submit(() -> lazy.apply(1));
            assertTrue(first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertTrue(second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void setMetadataPublishesMembershipActions(Mode mode) throws Exception {
        int rounds = 128;
        int[][] written = new int[rounds][4];
        List<Set<Integer>> sets = new ArrayList<>();
        for (int round = 0; round < rounds; round++) {
            int[] writes = written[round];
            sets.add(Lazy.ofSet(mode, Set.of(0, 1, 2, 3), candidate -> {
                writes[candidate] = candidate + 1;
                return (candidate & 1) == 0;
            }));
        }
        CyclicBarrier start = new CyclicBarrier(4);
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Future<?>> readers = new ArrayList<>();
            for (int thread = 0; thread < 4; thread++) {
                boolean size = (thread & 1) == 0;
                readers.add(executor.submit(() -> {
                    for (int round = 0; round < rounds; round++) {
                        start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                        var set = sets.get(round);
                        // A cached size or hash must preserve publication of
                        // the memberships consulted when the cache was filled.
                        assertEquals(2, size ? set.size() : set.hashCode());
                        assertArrayEquals(new int[] { 1, 2, 3, 4 }, written[round]);
                    }
                    return null;
                }));
            }
            for (var reader : readers) {
                reader.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void compositionRunsExtraFunctionsOnEveryAccess(Mode mode) {
        AtomicInteger calls = new AtomicInteger();
        Function<String, String> lazy = Lazy.ofContext(mode, value -> {
            calls.incrementAndGet();
            return value;
        });
        IllegalArgumentException original = new IllegalArgumentException("additional function");
        AtomicInteger beforeCalls = new AtomicInteger();
        Function<String, String> composed = lazy.compose(value -> {
            if (beforeCalls.incrementAndGet() == 1) {
                throw original;
            }
            return value;
        });
        assertSame(original, assertThrows(IllegalArgumentException.class, () -> composed.apply("a")));
        assertEquals(0, calls.get());
        assertEquals("b", composed.apply("b"));
        assertEquals("b", composed.apply("c"));
        assertEquals(3, beforeCalls.get());
        AtomicInteger afterCalls = new AtomicInteger();
        Function<String, String> after = lazy.andThen(value -> {
            if (afterCalls.incrementAndGet() == 1) {
                throw original;
            }
            return value + afterCalls.get();
        });
        assertSame(original, assertThrows(IllegalArgumentException.class, () -> after.apply("d")));
        assertEquals("b2", after.apply("e"));
        assertEquals(1, calls.get());
    }

    private static final class Computation implements IntFunction<Boolean> {
        private final int outcome;
        private int calls;

        Computation(int outcome) {
            this.outcome = outcome;
        }

        @Override
        public Boolean apply(int index) {
            if (index == 0 && calls++ == 0) {
                return finish(outcome);
            }
            return true;
        }
    }

    private record Retention(IntFunction<Boolean> access, WeakReference<Computation> reference) { }

    private static Retention retention(Shape shape, Mode mode, int size, int outcome) {
        var computation = new Computation(outcome);
        return new Retention(shape.create(mode, size, computation), new WeakReference<>(computation));
    }

    @ParameterizedTest
    @EnumSource(Shape.class)
    void callbackIsReleasedOnlyAfterAllSlotsBecomeTerminal(Shape shape) {
        int size = shape.isCollection() ? 2 : 1;
        for (Mode mode : Mode.values()) {
            for (int outcome = 0; outcome < 3; outcome++) {
                Retention fixture = retention(shape, mode, size, outcome);
                try {
                    System.gc();
                    assertFalse(fixture.reference.refersTo(null));
                    if (outcome == 0) {
                        assertTrue(fixture.access.apply(0));
                    } else if (mode == COMPUTE_ONCE && outcome == 1) {
                        assertThrows(NoSuchElementException.class, () -> fixture.access.apply(0));
                    } else {
                        Class<? extends Throwable> failure = outcome == 1
                                ? IllegalArgumentException.class : LinkageError.class;
                        assertThrows(failure, () -> fixture.access.apply(0));
                    }
                    if (mode == PUBLISH_ONCE && outcome != 0) {
                        System.gc();
                        assertFalse(fixture.reference.refersTo(null));
                        assertTrue(fixture.access.apply(0));
                    }
                    if (size == 2) {
                        // Even a permanently failed first slot must retain the
                        // callback until the still-uninitialized second slot completes.
                        System.gc();
                        assertFalse(fixture.reference.refersTo(null));
                        assertTrue(fixture.access.apply(1));
                    }
                    assertTrue(ForceGC.waitFor(() -> fixture.reference.refersTo(null),
                            Utils.adjustTimeout(4_000L)));
                } finally {
                    Reference.reachabilityFence(fixture.access);
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = Shape.class, names = { "LIST", "MAP", "SET" })
    void emptyCollectionsReleaseCallbacks(Shape shape) {
        for (Mode mode : Mode.values()) {
            Retention fixture = retention(shape, mode, 0, 0);
            try {
                assertTrue(ForceGC.waitFor(() -> fixture.reference.refersTo(null),
                        Utils.adjustTimeout(4_000L)));
            } finally {
                Reference.reachabilityFence(fixture.access);
            }
        }
    }

    private record ViewRetention(Object view, WeakReference<Computation> reference) { }

    private static ViewRetention viewRetention(Mode mode, int kind) {
        var computation = new Computation(0);
        Object view;
        if (kind < 2) {
            var list = Lazy.ofList(mode, 2, computation);
            var subList = kind == 0 ? list.subList(0, 1) : list.reversed().subList(0, 1);
            assertTrue(subList.get(0));
            view = subList;
        } else {
            var map = Lazy.ofMap(mode, Set.of(0, 1), computation::apply);
            var entry = map.entrySet().iterator().next();
            assertTrue(entry.getValue());
            view = kind == 2 ? entry : map.keySet();
        }
        return new ViewRetention(view, new WeakReference<>(computation));
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void viewsRetainCallbacksForUninitializedSlotsOutsideTheView(Mode mode) {
        for (int kind = 0; kind < 4; kind++) {
            var fixture = viewRetention(mode, kind);
            try {
                System.gc();
                assertFalse(fixture.reference.refersTo(null));
            } finally {
                Reference.reachabilityFence(fixture.view);
            }
        }
    }

    private record FailedRetention(Supplier<Boolean> lazy, WeakReference<Throwable> reference) { }

    private static FailedRetention failedRetention(boolean error) {
        Throwable failure = error ? new LinkageError("computation")
                : new IllegalArgumentException("computation");
        Supplier<Boolean> lazy = Lazy.of(() -> {
            if (failure instanceof Error original) {
                throw original;
            }
            throw (RuntimeException) failure;
        });
        Class<? extends Throwable> expected = error ? Error.class : NoSuchElementException.class;
        assertThrows(expected, lazy::get);
        return new FailedRetention(lazy, new WeakReference<>(failure));
    }

    @Test
    void permanentFailureDoesNotRetainOriginalThrowable() {
        for (boolean error : new boolean[] { false, true }) {
            var fixture = failedRetention(error);
            try {
                assertTrue(ForceGC.waitFor(() -> fixture.reference.refersTo(null),
                        Utils.adjustTimeout(4_000L)));
                Class<? extends Throwable> failure = error
                        ? Lazy.InitializationError.class : NoSuchElementException.class;
                assertNull(assertThrows(failure, fixture.lazy::get).getCause());
            } finally {
                Reference.reachabilityFence(fixture.lazy);
            }
        }
    }

    private static WeakReference<Object> applyWithTemporaryContext(Function<Object, Boolean> lazy) {
        Object context = new Object();
        lazy.apply(context);
        return new WeakReference<>(context);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void contextIsNotRetainedAfterComputation(Mode mode) {
        Function<Object, Boolean> lazy = Lazy.ofContext(mode, _ -> true);
        var reference = applyWithTemporaryContext(lazy);
        try {
            assertTrue(ForceGC.waitFor(() -> reference.refersTo(null), Utils.adjustTimeout(4_000L)));
        } finally {
            Reference.reachabilityFence(lazy);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "latch timed out");
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }
}
