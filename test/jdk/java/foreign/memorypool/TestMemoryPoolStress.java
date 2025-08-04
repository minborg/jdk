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
 * @modules java.base/jdk.internal.foreign
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestMemoryPoolStress
 */

import jdk.internal.foreign.StackedMemoryPool;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.foreign.MemoryPool;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class TestMemoryPoolStress {

    private static final int MAX_ALLOC_SIZE = 1 << 10;
    private static final int ITERATIONS = 1_000;
    private static final int ALLOCATIONS = 100;

    @ParameterizedTest
    @MethodSource("concurrentPools")
    void multiThreadAccess(MemoryPool pool) throws InterruptedException {

        for (var threadFactory : List.of(Thread.ofPlatform(), Thread.ofVirtual())) {

            var actions = Stream.generate(() -> new Action(pool))
                    .limit(Math.min(16, Runtime.getRuntime().availableProcessors()))
                    .toList();

            var threads = actions.stream()
                    .map(threadFactory::start)
                    .toList();

            for (var thread : threads) {
                thread.join();
            }

            // Make sure there are no errors in any of the actions.
            assertTrue(actions.stream().allMatch(action -> action.errors.get() == 0));
        }
    }


    @ParameterizedTest
    @MethodSource("concurrentPools")
    // The implementation hinges on the correct version for PT or VT being selected
    // upon first use.
    void checkPtVtImplType(MemoryPool pool) throws InterruptedException {
        var rnd = ThreadLocalRandom.current();
        for (int i = 0; i < 1000; i++) {
            final boolean isVirtual = rnd.nextBoolean();
            var threadFactory = isVirtual
                    ? Thread.ofVirtual()
                    : Thread.ofPlatform();
            final AtomicReference<String> impName = new AtomicReference<>();
            threadFactory.start(() -> {
                try (var arena = (StackedMemoryPool.ArenaFrame) pool.get()) {
                    impName.set(arena.inner().getClass().getName());
                }
            }).join();
            assertEquals(isVirtual, impName.get().contains("Virtual"), impName.get());
        }
    }

    private static final class Action implements Runnable {

        private final MemoryPool pool;
        private final AtomicInteger errors = new AtomicInteger();

        public Action(MemoryPool pool) {
            this.pool = pool;
        }

        @Override
        public void run() {
            for (int i = 0; i < ITERATIONS; i++) {
                try (var arena = pool.get()) {
                    for (int j = 0; j < ALLOCATIONS; j++) {
                        int size = ThreadLocalRandom.current().nextInt(MAX_ALLOC_SIZE + 1);
                        int a = ThreadLocalRandom.current().nextInt(6);
                        int alignment = 1 << a;
                        var segment = arena.allocate(size, alignment);
                        boolean correct = (segment.byteSize() == size)
                                && segment.scope().isAlive()
                                && isZeroedOut(segment);
                        if (!correct) {
                            errors.incrementAndGet();
                        }
                    }
                }
            }
        }

        boolean isZeroedOut(MemorySegment segment) {
            final long size = segment.byteSize();
            for (int i = 0; i < size; i++) {
                if (segment.get(ValueLayout.JAVA_BYTE, i) != (byte) 0) {
                    return false;
                }
            }
            return true;
        }

    }

    private static Stream<MemoryPool> concurrentPools() {
        return Stream.of(
                MemoryPool.ofStacked(1 << 20)
        );
    }

}
