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
 * @run junit/othervm -Djava.lang.foreign.native.confined.pool.power.size=6 TestConcurrentArenaPool
 * @run junit/othervm -Djava.lang.foreign.native.confined.pool.power.size=0 TestConcurrentArenaPool
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TestConcurrentArenaPool {

    private static final int THREAD_COUNT = 8;
    private static final int ALLOCATIONS_PER_THREAD = 128;

    @ParameterizedTest
    @MethodSource("arenaFactories")
    void concurrentAllocations(String name, Supplier<Arena> arenaFactory, boolean closeable)
            throws Exception {
        Arena arena = arenaFactory.get();
        Set<Long> addresses = ConcurrentHashMap.newKeySet();
        MemorySegment[] firstSegment = new MemorySegment[1];
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(THREAD_COUNT)) {
            Future<?>[] tasks = new Future<?>[THREAD_COUNT];
            for (int thread = 0; thread < THREAD_COUNT; thread++) {
                int threadIndex = thread;
                tasks[thread] = executor.submit(() -> {
                    start.await();
                    for (int allocation = 0; allocation < ALLOCATIONS_PER_THREAD; allocation++) {
                        MemorySegment segment = arena.allocate(ValueLayout.JAVA_LONG);
                        assertEquals(0L, segment.get(ValueLayout.JAVA_LONG, 0));
                        assertEquals(0L, segment.address() % ValueLayout.JAVA_LONG.byteAlignment());
                        assertTrue(addresses.add(segment.address()),
                                () -> "duplicate address from " + name + ": " + segment.address());
                        segment.set(ValueLayout.JAVA_LONG, 0, 42L);
                        if (threadIndex == 0 && allocation == 0) {
                            firstSegment[0] = segment;
                        }
                    }
                    return null;
                });
            }

            start.countDown();
            for (Future<?> task : tasks) {
                task.get();
            }
        } finally {
            if (closeable) {
                arena.close();
            }
        }

        assertEquals(THREAD_COUNT * ALLOCATIONS_PER_THREAD, addresses.size());
        if (closeable) {
            assertFalse(arena.scope().isAlive());
            assertThrows(IllegalStateException.class,
                    () -> firstSegment[0].get(ValueLayout.JAVA_LONG, 0));
        } else {
            assertTrue(arena.scope().isAlive());
            assertEquals(42L, firstSegment[0].get(ValueLayout.JAVA_LONG, 0));
        }
    }

    @Test
    void concurrentZeroSizedAllocationsHaveUniqueAddresses() throws Exception {
        try (Arena arena = Arena.ofShared();
             var executor = Executors.newFixedThreadPool(THREAD_COUNT)) {
            Set<Long> addresses = ConcurrentHashMap.newKeySet();
            Future<?>[] tasks = new Future<?>[THREAD_COUNT];
            for (int thread = 0; thread < THREAD_COUNT; thread++) {
                tasks[thread] = executor.submit(() -> {
                    for (int allocation = 0; allocation < ALLOCATIONS_PER_THREAD; allocation++) {
                        MemorySegment segment = arena.allocate(0, 1);
                        assertTrue(addresses.add(segment.address()));
                    }
                });
            }
            for (Future<?> task : tasks) {
                task.get();
            }
            assertEquals(THREAD_COUNT * ALLOCATIONS_PER_THREAD, addresses.size());
        }
    }

    static Stream<Arguments> arenaFactories() {
        return Stream.of(
                Arguments.of("shared", (Supplier<Arena>) Arena::ofShared, true),
                Arguments.of("auto", (Supplier<Arena>) Arena::ofAuto, false),
                Arguments.of("global", (Supplier<Arena>) Arena::global, false));
    }
}
