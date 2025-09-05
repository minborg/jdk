/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestMemoryPool
 */

// Todo: Also run with the main thread as a virtual thread (avoids using VThreadRunner for all test)

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryPool;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.lang.foreign.ValueLayout.*;
import static org.junit.jupiter.api.Assertions.*;

final class TestMemoryPool {

    private static final long POOL_SIZE = 1 << 20;
    private static final long SMALL_ALLOC_SIZE = JAVA_INT.byteSize();

    @Test
    void basic() {
        var pool = MemoryPool.ofStacked(SMALL_ALLOC_SIZE);
        Arena arena = pool.get();

        assertTrue(arena.scope().isAlive());
        arena.allocate(SMALL_ALLOC_SIZE);
        assertTrue(arena.scope().isAlive());

        arena.close();
        assertFalse(arena.scope().isAlive());
        assertTrue(arena.scope().toString().contains("ConfinedSession"));
    }
    @Test
    void basicZeroSize() {
        var pool = MemoryPool.ofStacked(SMALL_ALLOC_SIZE);
        Arena arena = pool.get();

        assertTrue(arena.scope().isAlive());
        arena.allocate(0);
        assertTrue(arena.scope().isAlive());

        arena.close();
        assertFalse(arena.scope().isAlive());
        assertTrue(arena.scope().toString().contains("ConfinedSession"));
    }

    @ParameterizedTest
    @MethodSource("pools")
    void allocateConfinement(MemoryPool pool) {
        Consumer<Arena> allocateAction = arena ->
                assertThrows(WrongThreadException.class, () -> {
                    CompletableFuture<Arena> future = CompletableFuture.supplyAsync(pool::get);
                    var otherThreadArena = future.get();
                    otherThreadArena.allocate(SMALL_ALLOC_SIZE);
                    // Intentionally do not close the otherThreadArena here.
                });
        doInTwoStackedArenas(pool, allocateAction, allocateAction);
    }

    @ParameterizedTest
    @MethodSource("pools")
    void closeConfinement(MemoryPool pool) {
        Consumer<Arena> closeAction = arena -> {
            // Do not use CompletableFuture here as it might accidentally run on the
            // same carrier thread as a virtual thread.
            AtomicReference<Arena> otherThreadArena = new AtomicReference<>();
            var thread = Thread.ofPlatform().start(() -> {
                otherThreadArena.set(pool.get());
            });
            try {
                thread.join();
            } catch (InterruptedException ie) {
                fail(ie);
            }
            assertThrows(WrongThreadException.class, otherThreadArena.get()::close);
        };
        doInTwoStackedArenas(pool, closeAction, closeAction);
    }

    @ParameterizedTest
    @MethodSource("pools")
    void allocBounds(MemoryPool pool) {
        try (var arena = pool.get()) {
            assertThrows(IllegalArgumentException.class, () -> arena.allocate(-1));
            assertDoesNotThrow(() -> arena.allocate(SMALL_ALLOC_SIZE));
        }
    }

    @ParameterizedTest
    @MethodSource("pools")
    void accessBounds(MemoryPool pool) {
        try (var arena = pool.get()) {
            arena.allocate(SMALL_ALLOC_SIZE);
            if (isStackedPool(pool)) {
                // Graceful degradation
                assertDoesNotThrow(() -> arena.allocate(POOL_SIZE));
            } else {
                var x = assertThrows(OutOfMemoryError.class, () -> arena.allocate(POOL_SIZE));
                assertTrue(x.getMessage().startsWith("Unable to allocate from "));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("pools")
    void sizesReuse(MemoryPool pool) {
        for (int i = 1; i < 2; i++) {
            for (int size = 0; size < 256; size++) {
                MemorySegment segment;
                try (var arena = pool.get()) {
                    segment = arena.allocate(size);
                    assertEquals(size, segment.byteSize());
                    assertTrue(segment.scope().isAlive());
                    assertSame(segment.scope(), arena.scope());
                }
                assertFalse(segment.scope().isAlive());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("pools")
    void sizesNoReuse(MemoryPool pool) {
        var segments = new ArrayList<MemorySegment>();
        try (var arena = pool.get()) {
            for (int i = 1; i < 2; i++) {
                for (int size = 1; size < 256; size++) {
                    var segment = arena.allocate(size);
                    segments.add(segment);
                    assertEquals(size, segment.byteSize());
                    assertTrue(segment.scope().isAlive());
                    assertSame(segment.scope(), arena.scope());
                }
            }
        }

        for (var segment : segments) {
            assertFalse(segment.scope().isAlive());
        }
    }

    @Test
    void alignmentReuse() {
        // Todo
    }

    @ParameterizedTest
    @MethodSource("pools")
    void zeroingOut(MemoryPool pool) {
        for (int a = 0; a < 8; a++) {
            long byteAlignment = 1L << a;
            try (var arena = pool.get()) {
                var segment = arena.allocate(SMALL_ALLOC_SIZE, byteAlignment);
                segment.fill((byte) 0xFF);
            }
            try (var arena = pool.get()) {
                var segment = arena.allocate(SMALL_ALLOC_SIZE, byteAlignment);
                for (int i = 0; i < SMALL_ALLOC_SIZE; i++) {
                    assertEquals(0, segment.get(JAVA_BYTE, i));
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("pools")
    void noInit(MemoryPool pool) {
        try (var arena = pool.get()) {
            assertDoesNotThrow(() -> arena.allocateFrom(JAVA_INT, 1));
        }
    }

    @SuppressWarnings("restricted")
    @ParameterizedTest
    @MethodSource("pools")
    void stackedArenas(MemoryPool pool) {
        try (var firstArena = pool.get()) {
            firstArena.allocate(SMALL_ALLOC_SIZE);
            try (var secondArena = pool.get()) {
                secondArena.allocate(SMALL_ALLOC_SIZE);
                try (var thirdArena = pool.get()) {
                    thirdArena.allocate(SMALL_ALLOC_SIZE);
                }
            }
        }
    }

    @SuppressWarnings("restricted")
    @ParameterizedTest
    @MethodSource("pools")
    void stackedArenasInterAllocationCheck(MemoryPool pool) {
        // We are checking for potential illegal sharing of the backing memory here
        Map<Byte, MemorySegment> segmentMap = new HashMap<>();
        AtomicInteger color = new AtomicInteger();
        try (var firstArena = pool.get()) {
            segmentMap.put((byte) color.get(), firstArena.allocate(SMALL_ALLOC_SIZE).fill((byte) color.get()));
            color.getAndIncrement();
            try (var secondArena = pool.get()) {
                segmentMap.put((byte) color.get(), secondArena.allocate(SMALL_ALLOC_SIZE).fill((byte) color.get()));
                color.getAndIncrement();
                segmentMap.put((byte) color.get(), firstArena.allocate(SMALL_ALLOC_SIZE).fill((byte) color.get()));
                color.getAndIncrement();
                segmentMap.put((byte) color.get(), secondArena.allocate(SMALL_ALLOC_SIZE).fill((byte) color.get()));
                color.getAndIncrement();
                try (var thirdArena = pool.get()) {
                    segmentMap.put((byte) color.get(), thirdArena.allocate(SMALL_ALLOC_SIZE).fill((byte) color.get()));
                    color.getAndIncrement();
                    segmentMap.put((byte) color.get(), firstArena.allocate(SMALL_ALLOC_SIZE).fill((byte) color.get()));
                    color.getAndIncrement();
                    segmentMap.put((byte) color.get(), thirdArena.allocate(SMALL_ALLOC_SIZE).fill((byte) color.get()));
                    color.getAndIncrement();
                }
            }
            segmentMap.put((byte) color.get(), firstArena.allocate(SMALL_ALLOC_SIZE).fill((byte) color.get()));
            color.getAndIncrement();
        }
        segmentMap.forEach(TestMemoryPool::checkSegment);
    }

    static void checkSegment(Byte val, MemorySegment segment) {
        try {
            segment.get(JAVA_BYTE, 0);
            for (int i = 0; i < segment.byteSize(); i++) {
                assertEquals(val, segment.get(JAVA_BYTE, i), "Mismatch at " + i);
            }
        } catch (IllegalStateException ise) {
            // We will end up here for segments that are closed.
            // If they are, we should not check against mismatch as they are not accessible.
        }
    }
    @ParameterizedTest
    @MethodSource("pools")
    void outOfSequenceClose(MemoryPool pool) {
        if (!isStackedPool(pool)) {
            return;
        }
        var firstArena = pool.get();
        var segmentFromFirst = firstArena.allocate(SMALL_ALLOC_SIZE);
        var secondArena = pool.get();
        var segmentFromSecond = secondArena.allocate(SMALL_ALLOC_SIZE);

        var x = assertThrows(IllegalStateException.class, firstArena::close);
        assertEquals(
                "The stacked arena was closed out of sequence. Expected stack frame number 2 but got 1.",
                x.getMessage());
        assertDoesNotThrow(() -> segmentFromFirst.get(JAVA_BYTE, 0));
        assertDoesNotThrow(() -> segmentFromSecond.get(JAVA_BYTE, 0));

        secondArena.close();
        assertDoesNotThrow(() -> segmentFromFirst.get(JAVA_BYTE, 0));
        assertThrows(IllegalStateException.class, () -> segmentFromSecond.get(JAVA_BYTE, 0));

        firstArena.close();
        assertThrows(IllegalStateException.class, () -> segmentFromFirst.get(JAVA_BYTE, 0));
        assertThrows(IllegalStateException.class, () -> segmentFromSecond.get(JAVA_BYTE, 0));
    }

    @ParameterizedTest
    @MethodSource("pools")
    void toStringTest(MemoryPool pool) {
        var toString = pool.toString();
        assertTrue(toString.contains("MemoryPool"), toString);
        assertTrue(toString.contains("[byteSize=" + POOL_SIZE), toString);
        assertTrue(toString.endsWith("]"));
        try (var arena = pool.get()) {
            var arenaToString = arena.toString();
            var initialPart = isStackedPool(pool) ? "ArenaFrame" : "PooledArena";
            assertTrue(arenaToString.startsWith(initialPart), arenaToString + " did not start with " + initialPart);
        }
    }

    @ParameterizedTest
    @MethodSource("pools")
    void hashCodeTest(MemoryPool pool) {
        assertIdentityHashCode(pool);
        try (var arena = pool.get()) {
            assertIdentityHashCode(arena);
        }
    }

    static void assertIdentityHashCode(Object o) {
        assertEquals(System.identityHashCode(o), o.hashCode());
    }

    @ParameterizedTest
    @MethodSource("pools")
    void equals(MemoryPool firstPool) {
        var secondPool = MemoryPool.ofStacked(SMALL_ALLOC_SIZE);
        maybeDefaultEquals(firstPool, secondPool);
        try (var firstArena = firstPool.get()){
            try (var secondArena = firstPool.get()) {
                maybeDefaultEquals(firstArena, secondArena);
            }
        }
    }

    static void maybeDefaultEquals(Object o, Object notO) {
        assertNotEquals(o, notO);
        assertNotEquals(notO, o);
        assertEquals(o, o);
    }

    static void doInTwoStackedArenas(MemoryPool pool,
                                     Consumer<Arena> firstAction,
                                     Consumer<Arena> secondAction) {
        try (var firstArena = pool.get()) {
            firstAction.accept(firstArena);
            try (var secondArena = pool.get()) {
                secondAction.accept(secondArena);
            }
        }
    }

    private static Stream<MemoryPool> pools() {
        return Stream.of(
                MemoryPool.ofStacked(POOL_SIZE),
                MemoryPool.ofShared(POOL_SIZE, 1)
        );
    }

    static boolean isStackedPool(MemoryPool pool) {
        return pool.getClass().getSimpleName().equals("StackedMemoryPool");
    }

}
