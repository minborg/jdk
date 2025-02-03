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
 * @modules java.base/jdk.internal.foreign
 * @library /test/lib
 * @run junit TestArenaPool
 */

import java.lang.foreign.Arena;
import java.lang.foreign.ArenaPool;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import jdk.test.lib.thread.VThreadRunner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class TestArenaPool {

    private static final long POOL_SIZE = 64;
    private static final long SMALL_ALLOC_SIZE = 8;

    @Test
    void invariants() {
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
    void negativeAlloc() {
        Consumer<Arena> action = arena ->
                assertThrows(IllegalArgumentException.class, () -> arena.allocate(-1));
        doInTwoStackedArenas(action, action);
    }

    @Test
    void negativeAllocVt() {
        VThreadRunner.run(this::negativeAlloc);
    }

    @Test
    void allocateConfinement() {
        Consumer<Arena> allocateAction = arena ->
                assertThrows(WrongThreadException.class, () -> {
                    var pool = newPool();
                    CompletableFuture<Arena> future = CompletableFuture.supplyAsync(pool::take);
                    var otherThreadArena = future.get();
                    otherThreadArena.allocate(SMALL_ALLOC_SIZE);
                    // Intentionally do not close the otherThreadArena here.
                });
        doInTwoStackedArenas(allocateAction, allocateAction);
    }

    @Test
    void allocateConfinementVt() {
        VThreadRunner.run(this::allocateConfinement);
    }

    @Test
    void closeConfinement() {
        Consumer<Arena> closeAction = arena -> {
            var pool = newPool();
            CompletableFuture<Arena> future = CompletableFuture.supplyAsync(pool::take);
            Arena otherThreadArena = null;
            try {
                otherThreadArena = future.get();
            } catch (InterruptedException | ExecutionException e) {
                fail(e);
            }
            assertThrows(WrongThreadException.class, otherThreadArena::close);
        };
        doInTwoStackedArenas(closeAction, closeAction);
    }

    @Test
    void closeConfinementVt() {
        VThreadRunner.run(this::closeConfinement);
    }

    @Test
    void reuse() {
        var pool = newPool();
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
        assertThrows(IllegalStateException.class, () -> firstSegment.get(ValueLayout.JAVA_BYTE, 0));
        assertThrows(IllegalStateException.class, () -> secondSegment.get(ValueLayout.JAVA_BYTE, 0));
    }

    @Test
    void reuseVt() {
        VThreadRunner.run(this::reuse);
    }

    @Test
    void outOfOrderUse() {
        var pool = newPool();
        Arena firstArena = pool.take();
        Arena secondArena = pool.take();
        firstArena.close();
        Arena thirdArena = pool.take();
        secondArena.close();
        thirdArena.close();
    }

    @Test
    void zeroing() {
        var pool = newPool();
        try (var arena = pool.take()) {
            var seg = arena.allocate(SMALL_ALLOC_SIZE);
            seg.fill((byte) 1);
        }
        try (var arena = pool.take()) {
            var seg = arena.allocate(SMALL_ALLOC_SIZE);
            for (int i = 0; i < SMALL_ALLOC_SIZE; i++) {
                assertEquals((byte) 0, seg.get(ValueLayout.JAVA_BYTE, i));
            }
        }
    }

    @Test
    void zeroingVt() {
        VThreadRunner.run(this::zeroing);
    }

    @Test
    void useAfterFree() {
        // How can we test this? Is it even possible to identify a UAF case?
    }

    static void doInTwoStackedArenas(Consumer<Arena> firstAction,
                                     Consumer<Arena> secondAction) {
        var pool = newPool();
        try (var firstArena = pool.take()) {
            firstAction.accept(firstArena);
            try (var secondArena = pool.take()) {
                secondAction.accept(secondArena);
            }
        }
    }

    static ArenaPool newPool() {
        return ArenaPool.create(POOL_SIZE);
    }

}
