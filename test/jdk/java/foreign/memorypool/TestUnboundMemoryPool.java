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
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestUnboundMemoryPool
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryPool;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.lang.foreign.ValueLayout.*;
import static org.junit.jupiter.api.Assertions.*;

final class TestUnboundMemoryPool {

    private static final long SMALL_ALLOC_SIZE = JAVA_INT.byteSize();

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
            var segment = arena.allocate(SMALL_ALLOC_SIZE);
            assertThrows(IndexOutOfBoundsException.class, () -> segment.get(JAVA_BYTE, SMALL_ALLOC_SIZE));
        }
    }

    @ParameterizedTest
    @MethodSource("pools")
    void sizesReuse(MemoryPool pool) {
        for (int i = 0; i < 2; i++) {
            for (int size = 0; size < 256; size++) {
                MemorySegment segment;
                try (var arena = pool.get()) {
                    segment = arena.allocate(size);
                    assertEquals(size, segment.byteSize());
                    assertTrue(segment.scope().isAlive());
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
            for (int i = 0; i < 2; i++) {
                for (int size = 0; size < 256; size++) {
                    var segment = arena.allocate(size);
                    segments.add(segment);
                    assertEquals(size, segment.byteSize());
                    assertTrue(segment.scope().isAlive());
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

    @ParameterizedTest
    @MethodSource("pools")
    void toStringTest(MemoryPool pool) {
        assertEquals("UnboundMemoryPool(segmentFifo=SegmentFifo(0 bytes in 0 segments))", pool.toString());
        try (var arena = pool.get()) {
            var firstSegment = arena.allocate(SMALL_ALLOC_SIZE);
            // Nothing has been returned to the pool yet
            assertEquals("UnboundMemoryPool(segmentFifo=SegmentFifo(0 bytes in 0 segments))", pool.toString());
        }
        // The first allocation has now been returned to the pool
        assertEquals("UnboundMemoryPool(segmentFifo=SegmentFifo(4 bytes in 1 segments))", pool.toString());

        try (var arena = pool.get()) {
            var secondSegment = arena.allocate(SMALL_ALLOC_SIZE);
            // Nothing has been returned to the pool yet
            assertEquals("UnboundMemoryPool(segmentFifo=SegmentFifo(0 bytes in 0 segments))", pool.toString());
        }
        // The second allocation has now been returned to the pool
        assertEquals("UnboundMemoryPool(segmentFifo=SegmentFifo(4 bytes in 1 segments))", pool.toString());
    }

    @ParameterizedTest
    @MethodSource("pools")
    void hashCodeTest(MemoryPool pool) {
        assertEquals(System.identityHashCode(pool), pool.hashCode());
    }

    @ParameterizedTest
    @MethodSource("pools")
    void equals(MemoryPool pool) {
        var firstPool = newPool();
        var secondPool = newPool();
        assertNotEquals(firstPool, secondPool);
        assertEquals(firstPool, firstPool);

        var firstArena = firstPool.get();
        var secondArena = firstPool.get();
        assertNotEquals(firstArena, secondArena);
        assertEquals(firstArena, firstArena);
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

    private static MemoryPool newPool() {
        return MemoryPool.ofConcurrentUnbound();
    }

    private static Stream<MemoryPool> pools() {
        return Stream.of(
                MemoryPool.ofUnbound(),
                MemoryPool.ofConcurrentUnbound()
        );
    }

}
