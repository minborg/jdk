/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

/*
 * @test
 * @summary Test ArenaPool
 * @library /test/lib
 * @compile --enable-preview -source ${jdk.version} TestArenaPool.java
 * @run junit/othervm --enable-preview TestArenaPool
 */

import java.lang.foreign.Arena;
import java.lang.foreign.ArenaPool;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Stream;

import jdk.test.lib.thread.VThreadRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.jupiter.api.Assertions.*;

final class TestArenaPool {

    private static final long POOL_SIZE = 64;
    private static final long SMALL_ALLOC_SIZE = 8;
    private static final long VERY_LARGE_ALLOC_SIZE = 1L << 10;

    @Test
    void invariants1LongArg() {
        assertThrows(IllegalArgumentException.class, () -> ArenaPool.create(-1));
        ArenaPool pool = ArenaPool.create(0);
        try (var arena = pool.take()) {
            // This should come from the underlying arena and not from recyclable memory
            assertDoesNotThrow(() -> arena.allocate(1));
            try (var arena2 = pool.take()) {
                assertDoesNotThrow(() -> arena.allocate(1));
            }
        }
    }

    @Test
    void invariants2LongArgs() {
        assertThrows(IllegalArgumentException.class, () -> ArenaPool.create(-1, 2));
        assertThrows(IllegalArgumentException.class, () -> ArenaPool.create(1, -1));
        assertThrows(IllegalArgumentException.class, () -> ArenaPool.create(1, 3));
        ArenaPool pool = ArenaPool.create(0, 16);
        try (var arena = pool.take()) {
            // This should come from the underlying arena and not from recyclable memory
            assertDoesNotThrow(() -> arena.allocate(1));
            try (var arena2 = pool.take()) {
                assertDoesNotThrow(() -> arena.allocate(1));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("pools")
    void negativeAlloc(ArenaPool pool) {
        Consumer<Arena> action = arena ->
                assertThrows(IllegalArgumentException.class, () -> arena.allocate(-1));
        doInTwoStackedArenas(pool, action, action);
    }

    @ParameterizedTest
    @MethodSource("pools")
    void negativeAllocVt(ArenaPool pool) {
        VThreadRunner.run(() -> negativeAlloc(pool));
    }

    @ParameterizedTest
    @MethodSource("pools")
    void allocateConfinement(ArenaPool pool) {
        Consumer<Arena> allocateAction = arena ->
                assertThrows(WrongThreadException.class, () -> {
                    CompletableFuture<Arena> future = CompletableFuture.supplyAsync(pool::take);
                    var otherThreadArena = future.get();
                    otherThreadArena.allocate(SMALL_ALLOC_SIZE);
                    // Intentionally do not close the otherThreadArena here.
                });
        doInTwoStackedArenas(pool, allocateAction, allocateAction);
    }

    @ParameterizedTest
    @MethodSource("pools")
    void allocateConfinementVt(ArenaPool pool) {
        VThreadRunner.run(() -> allocateConfinement(pool));
    }

    @ParameterizedTest
    @MethodSource("pools")
    void closeConfinement(ArenaPool pool) {
        Consumer<Arena> closeAction = arena -> {
            CompletableFuture<Arena> future = CompletableFuture.supplyAsync(pool::take);
            Arena otherThreadArena = null;
            try {
                otherThreadArena = future.get();
            } catch (InterruptedException | ExecutionException e) {
                fail(e);
            }
            assertThrows(WrongThreadException.class, otherThreadArena::close);
        };
        doInTwoStackedArenas(pool, closeAction, closeAction);
    }

    @ParameterizedTest
    @MethodSource("pools")
    void closeConfinementVt(ArenaPool pool) {
        VThreadRunner.run(() -> closeConfinement(pool));
    }

    @ParameterizedTest
    @MethodSource("pools")
    void reuse(ArenaPool pool) {
        MemorySegment firstSegment;
        MemorySegment secondSegment;
        try (var arena = pool.take()) {
            firstSegment = arena.allocate(SMALL_ALLOC_SIZE);
        }
        try (var arena = pool.take()) {
            secondSegment = arena.allocate(SMALL_ALLOC_SIZE);
        }
        assertNotSame(firstSegment, secondSegment);
        assertNotSame(firstSegment.scope(), secondSegment.scope());
        assertEquals(firstSegment.address(), secondSegment.address());
        assertThrows(IllegalStateException.class, () -> firstSegment.get(JAVA_BYTE, 0));
        assertThrows(IllegalStateException.class, () -> secondSegment.get(JAVA_BYTE, 0));
    }

    @ParameterizedTest
    @MethodSource("pools")
    void reuseVt(ArenaPool pool) {
        VThreadRunner.run(() -> reuse(pool));
    }

    @ParameterizedTest
    @MethodSource("pools")
    void largeAlloc(ArenaPool pool) {
        try (var arena = pool.take()) {
            var segment = arena.allocate(VERY_LARGE_ALLOC_SIZE);
            assertEquals(VERY_LARGE_ALLOC_SIZE, segment.byteSize());
        }
    }

    @ParameterizedTest
    @MethodSource("pools")
    void largeAllocSizeVt(ArenaPool pool) {
        VThreadRunner.run(() -> largeAlloc(pool));
    }

    @Test
    void allocationSameAsPoolSize() {
        var pool = ArenaPool.create(4);
        long firstAddress;
        try (var arena = pool.take()) {
            var segment = arena.allocate(4);
            firstAddress = segment.address();
        }
        try (var arena = pool.take()) {
            var segment = arena.allocate(4);
            assertEquals(firstAddress, segment.address());
            var segmentTwo = arena.allocate(4);
            assertNotEquals(firstAddress, segmentTwo.address());
        }
    }

    @ParameterizedTest
    @MethodSource("pools")
    void outOfOrderUse(ArenaPool pool) {
        Arena firstArena = pool.take();
        Arena secondArena = pool.take();
        firstArena.close();
        Arena thirdArena = pool.take();
        secondArena.close();
        thirdArena.close();
    }

    @ParameterizedTest
    @MethodSource("pools")
    void zeroing(ArenaPool pool) {
        try (var arena = pool.take()) {
            var seg = arena.allocate(SMALL_ALLOC_SIZE);
            seg.fill((byte) 1);
        }
        try (var arena = pool.take()) {
            var seg = arena.allocate(SMALL_ALLOC_SIZE);
            for (int i = 0; i < SMALL_ALLOC_SIZE; i++) {
                assertEquals((byte) 0, seg.get(JAVA_BYTE, i));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("pools")
    void zeroingVt(ArenaPool pool) {
        VThreadRunner.run(() -> zeroing(pool));
    }

    @ParameterizedTest
    @MethodSource("pools")
    void useAfterFree(ArenaPool pool) {
        MemorySegment segment = null;
        try (var arena = pool.take()){
            segment = arena.allocate(SMALL_ALLOC_SIZE);
        }
        final var closedSegment = segment;
        var e = assertThrows(IllegalStateException.class, () -> closedSegment.get(ValueLayout.JAVA_INT, 0));
        assertEquals("Already closed", e.getMessage());
    }

    @ParameterizedTest
    @MethodSource("pools")
    void toStringTest(ArenaPool pool) {
        assertTrue(pool.toString().contains("ArenaPool"));
        try (var arena = pool.take()) {
            assertTrue(arena.toString().contains("SlicingArena"));
        }
    }

    // Factories and helper methods

    static Stream<ArenaPool> pools() {
        return Stream.of(
                ArenaPool.create(POOL_SIZE),
                ArenaPool.create(POOL_SIZE, 16),
                ArenaPool.create(MemoryLayout.sequenceLayout(POOL_SIZE, JAVA_BYTE))
        );
    }

    static void doInTwoStackedArenas(ArenaPool pool,
                                     Consumer<Arena> firstAction,
                                     Consumer<Arena> secondAction) {
        try (var firstArena = pool.take()) {
            firstAction.accept(firstArena);
            try (var secondArena = pool.take()) {
                secondAction.accept(secondArena);
            }
        }
    }

}
