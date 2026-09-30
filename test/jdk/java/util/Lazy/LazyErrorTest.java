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
 * @summary Errors propagate unchanged on first computation and remain errors on later access
 * @enablePreview
 * @library /test/lib
 * @run junit LazyErrorTest
 */

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Lazy;
import java.util.Lazy.InitializationError;
import java.util.Lazy.Mode;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import jdk.test.lib.Utils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static java.util.Lazy.Mode.COMPUTE_ONCE;
import static java.util.Lazy.Mode.PUBLISH_ONCE;
import static org.junit.jupiter.api.Assertions.*;

class LazyErrorTest {
    private static final long TIMEOUT_SECONDS = Utils.adjustTimeout(30);
    private static final int THREADS = 4;

    enum Key { FIRST, SECOND }

    enum Shape {
        SUPPLIER, CONTEXT_FUNCTION, LIST, MAP, ENUM_MAP, SET, ENUM_SET;

        Supplier<Boolean> create(Mode mode, Supplier<Boolean> computation) {
            IntFunction<Boolean> access = create(mode, 1, _ -> computation.get());
            return () -> access.apply(0);
        }

        IntFunction<Boolean> create(Mode mode, int size, IntFunction<Boolean> computation) {
            return switch (this) {
                case SUPPLIER -> {
                    assertEquals(1, size);
                    Supplier<Boolean> lazy = Lazy.of(mode, () -> computation.apply(0));
                    yield _ -> lazy.get();
                }
                case CONTEXT_FUNCTION -> {
                    assertEquals(1, size);
                    Object context = new Object();
                    Function<Object, Boolean> lazy =
                            Lazy.ofContext(mode, _ -> computation.apply(0));
                    yield _ -> lazy.apply(context);
                }
                case LIST -> {
                    List<Boolean> lazy = Lazy.ofList(mode, size, computation);
                    yield lazy::get;
                }
                case MAP -> {
                    Set<Integer> keys = size == 1 ? Set.of(0) : Set.of(0, 1);
                    Map<Integer, Boolean> lazy = Lazy.ofMap(mode, keys, computation::apply);
                    yield lazy::get;
                }
                case ENUM_MAP -> {
                    Set<Key> keys = EnumSet.range(Key.FIRST, Key.values()[size - 1]);
                    Map<Key, Boolean> lazy =
                            Lazy.ofMap(mode, keys, key -> computation.apply(key.ordinal()));
                    yield index -> lazy.get(Key.values()[index]);
                }
                case SET -> {
                    Set<Integer> candidates = size == 1 ? Set.of(0) : Set.of(0, 1);
                    Set<Integer> lazy = Lazy.ofSet(mode, candidates, computation::apply);
                    yield lazy::contains;
                }
                case ENUM_SET -> {
                    Set<Key> candidates = EnumSet.range(Key.FIRST, Key.values()[size - 1]);
                    Set<Key> lazy = Lazy.ofSet(mode, candidates,
                            key -> computation.apply(key.ordinal()));
                    yield index -> lazy.contains(Key.values()[index]);
                }
            };
        }

        Supplier<Boolean> createLegacy(Supplier<Boolean> computation) {
            return switch (this) {
                case LIST -> {
                    List<Boolean> lazy = List.ofLazy(1, _ -> computation.get());
                    yield () -> lazy.get(0);
                }
                case MAP -> {
                    Map<String, Boolean> lazy = Map.ofLazy(Set.of("key"), _ -> computation.get());
                    yield () -> lazy.get("key");
                }
                case ENUM_MAP -> {
                    Map<Key, Boolean> lazy = Map.ofLazy(EnumSet.of(Key.FIRST), _ -> computation.get());
                    yield () -> lazy.get(Key.FIRST);
                }
                case SET -> {
                    Set<String> lazy = Set.ofLazy(Set.of("key"), _ -> computation.get());
                    yield () -> lazy.contains("key");
                }
                case ENUM_SET -> {
                    Set<Key> lazy = Set.ofLazy(EnumSet.of(Key.FIRST), _ -> computation.get());
                    yield () -> lazy.contains(Key.FIRST);
                }
                default -> throw new AssertionError("No legacy collection for " + this);
            };
        }
    }

    @ParameterizedTest
    @EnumSource(Shape.class)
    void computeOncePreservesTheFirstErrorAndDoesNotRetry(Shape shape) {
        for (Error original : errors()) {
            AtomicInteger calls = new AtomicInteger();
            Supplier<Boolean> lazy = shape.create(COMPUTE_ONCE, () -> {
                calls.incrementAndGet();
                throw original;
            });

            assertSame(original, assertThrows(Error.class, lazy::get));
            InitializationError second = assertRecordedError(original, lazy);
            InitializationError third = assertRecordedError(original, lazy);
            assertNotSame(second, third);
            assertEquals(1, calls.get());
        }
    }

    @ParameterizedTest
    @EnumSource(Shape.class)
    void publishOncePropagatesErrorsAndRetries(Shape shape) {
        for (Error original : errors()) {
            AtomicInteger calls = new AtomicInteger();
            Supplier<Boolean> lazy = shape.create(PUBLISH_ONCE, () -> {
                if (calls.incrementAndGet() == 1) {
                    throw original;
                }
                return Boolean.TRUE;
            });

            assertSame(original, assertThrows(Error.class, lazy::get));
            assertTrue(lazy.get());
            assertTrue(lazy.get());
            assertEquals(2, calls.get());
        }
    }

    @ParameterizedTest
    @EnumSource(value = Shape.class, names = { "ENUM_MAP", "SET", "ENUM_SET" })
    void ordinaryExceptionsKeepTheirModeSpecificPolicy(Shape shape) {
        for (Mode mode : Mode.values()) {
            IllegalArgumentException original = new IllegalArgumentException("computation");
            AtomicInteger calls = new AtomicInteger();
            Supplier<Boolean> lazy = shape.create(mode, () -> {
                if (calls.incrementAndGet() == 1) {
                    throw original;
                }
                return Boolean.TRUE;
            });

            if (mode == COMPUTE_ONCE) {
                assertSame(original,
                        assertThrows(NoSuchElementException.class, lazy::get).getCause());
                NoSuchElementException later = assertThrows(NoSuchElementException.class, lazy::get);
                assertNull(later.getCause());
                assertTrue(later.getMessage().contains(original.getClass().getName()));
                assertEquals(1, calls.get());
            } else {
                assertSame(original, assertThrows(IllegalArgumentException.class, lazy::get));
                assertTrue(lazy.get());
                assertEquals(2, calls.get());
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Shape.class)
    void waitingAccessesReceiveFreshInitializationErrors(Shape shape) throws Exception {
        Error original = new LinkageError("initialization failed");
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch attempting = new CountDownLatch(THREADS - 1);
        CountDownLatch release = new CountDownLatch(1);
        Supplier<Boolean> lazy = shape.create(COMPUTE_ONCE, () -> {
            calls.incrementAndGet();
            entered.countDown();
            await(release);
            throw original;
        });
        try (var executor = Executors.newFixedThreadPool(THREADS)) {
            Future<Error> first = executor.submit(() -> assertThrows(Error.class, lazy::get));
            List<Future<InitializationError>> waiting = new ArrayList<>();
            try {
                await(entered);
                for (int i = 1; i < THREADS; i++) {
                    waiting.add(executor.submit(() -> {
                        attempting.countDown();
                        return assertRecordedError(original, lazy);
                    }));
                }
                await(attempting);
                for (Future<InitializationError> result : waiting) {
                    assertFalse(result.isDone());
                }
                assertEquals(1, calls.get());
            } finally {
                release.countDown();
            }
            assertSame(original, first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            List<InitializationError> recorded = new ArrayList<>();
            for (Future<InitializationError> result : waiting) {
                InitializationError error = result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                for (InitializationError previous : recorded) {
                    assertNotSame(previous, error);
                }
                recorded.add(error);
            }
            InitializationError later = assertRecordedError(original, lazy);
            for (InitializationError previous : recorded) {
                assertNotSame(previous, later);
            }
            assertEquals(1, calls.get());
        }
    }

    @ParameterizedTest
    @EnumSource(value = Shape.class, names = { "LIST", "MAP", "ENUM_MAP", "SET", "ENUM_SET" })
    void errorsAreIsolatedToTheirStorageSlot(Shape shape) {
        for (Mode mode : Mode.values()) {
            Error original = new AssertionError("first slot failed");
            AtomicIntegerArray calls = new AtomicIntegerArray(2);
            IntFunction<Boolean> lazy = shape.create(mode, 2, index -> {
                if (calls.incrementAndGet(index) == 1 && index == 0) {
                    throw original;
                }
                return Boolean.TRUE;
            });

            assertSame(original, assertThrows(Error.class, () -> lazy.apply(0)));
            assertTrue(lazy.apply(1));
            assertTrue(lazy.apply(1));
            if (mode == COMPUTE_ONCE) {
                assertRecordedError(original, () -> lazy.apply(0));
                assertEquals(1, calls.get(0));
            } else {
                assertTrue(lazy.apply(0));
                assertEquals(2, calls.get(0));
            }
            assertEquals(1, calls.get(1));
        }
    }

    @ParameterizedTest
    @EnumSource(value = Shape.class, names = { "LIST", "MAP", "ENUM_MAP", "SET", "ENUM_SET" })
    void legacyCollectionsKeepWrappingErrors(Shape shape) {
        for (Error original : errors()) {
            AtomicInteger calls = new AtomicInteger();
            Supplier<Boolean> lazy = shape.createLegacy(() -> {
                calls.incrementAndGet();
                throw original;
            });

            assertSame(original,
                    assertThrows(NoSuchElementException.class, lazy::get).getCause());
            assertNull(assertThrows(NoSuchElementException.class, lazy::get).getCause());
            assertEquals(1, calls.get());
        }
    }

    private static List<Error> errors() {
        return List.of(new Error("computation"), new AssertionError("computation"),
                new LinkageError("computation"), new OutOfMemoryError("simulated computation failure"));
    }

    private static InitializationError assertRecordedError(Error original, Supplier<?> access) {
        InitializationError error = assertThrowsExactly(InitializationError.class, access::get);
        assertNull(error.getCause());
        assertTrue(error.getMessage().contains(original.getClass().getName()));
        return error;
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "latch timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
