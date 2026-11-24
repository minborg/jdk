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
 * @summary Serialized accessor defaults hold the monitor through publication and retry failures
 * @enablePreview
 * @run junit SerializedComputation
 */

import java.lang.invoke.StableFieldAccessors.OfArray;
import java.lang.invoke.StableFieldAccessors.OfScalar;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SerializedComputation {
    enum Shape { FIELD, ARRAY }

    private static final String VALUE = "installed";
    private static final int INDEX = 1;
    private static final int TIMEOUT_SECONDS = 10;

    @ParameterizedTest
    @EnumSource(Shape.class)
    void waitersObservePublicationWithoutComputing(Shape shape) throws Exception {
        int threads = 6;
        Cell cell = new Cell();
        CountDownLatch allMissed = new CountDownLatch(threads);
        CountDownLatch candidateReady = new CountDownLatch(1);
        CountDownLatch allowPublication = new CountDownLatch(1);
        cell.onMiss = allMissed::countDown;
        cell.beforePublication = () -> {
            candidateReady.countDown();
            await(allowPublication);
        };

        try (var executor = Executors.newFixedThreadPool(threads)) {
            var results = new ArrayList<Future<String>>();
            try {
                results.add(executor.submit(() -> cell.read(shape)));
                await(candidateReady);
                // The waiters miss before publication. Their contexts would
                // be rejected if they needed to compute after taking the monitor.
                for (int i = 1; i < threads; i++) {
                    results.add(executor.submit(() -> cell.read(shape, cell.monitor, "unused")));
                }
                await(allMissed);
                assertEquals(1, cell.attempts.get());
            } finally {
                allowPublication.countDown();
            }
            for (Future<String> result : results) {
                assertSame(VALUE, result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
        }
        assertEquals(1, cell.attempts.get());
        assertEquals(1, cell.maximumActive.get());
    }

    @ParameterizedTest
    @EnumSource(Shape.class)
    void failurePropagatesAndPermitsRetry(Shape shape) {
        Cell cell = new Cell();
        RuntimeException failure = new RuntimeException("first attempt");
        cell.computer = () -> {
            if (cell.attempts.get() == 1) {
                throw failure;
            }
            return VALUE;
        };
        assertSame(failure, assertThrows(RuntimeException.class, () -> cell.read(shape)));
        assertTrue(cell.peek().isEmpty());
        assertSame(VALUE, cell.read(shape));
        assertSame(VALUE, cell.read(shape));
        assertEquals(2, cell.attempts.get());
        assertEquals(1, cell.maximumActive.get());
    }

    @ParameterizedTest
    @EnumSource(Shape.class)
    void publishedValueDoesNotAcquireMonitor(Shape shape) throws Exception {
        Cell cell = new Cell();
        cell.value.set(VALUE);
        FutureTask<String> result = new FutureTask<>(() -> cell.read(shape));
        Thread reader;
        synchronized (cell.monitor) {
            reader = Thread.ofPlatform().daemon().start(result);
            // Completion while another thread owns the monitor proves that the
            // cached path does not acquire it.
            assertSame(VALUE, result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }
        reader.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        assertEquals(0, cell.attempts.get());
    }

    @ParameterizedTest
    @EnumSource(Shape.class)
    void nullMonitorIsRejectedOnlyOnInitialMiss(Shape shape) {
        Cell cell = new Cell();
        assertThrows(NullPointerException.class, () -> cell.read(shape, null, null));
        cell.value.set(VALUE);
        assertSame(VALUE, cell.read(shape, null, null));
        assertEquals(0, cell.attempts.get());
    }

    @ParameterizedTest
    @EnumSource(Shape.class)
    void valueMonitorIsRejectedOnlyOnInitialMiss(Shape shape) {
        Cell cell = new Cell();
        Object invalidMonitor = new ValueMonitor();
        assertThrows(IdentityException.class, () -> cell.read(shape, invalidMonitor, null));
        cell.value.set(VALUE);
        assertSame(VALUE, cell.read(shape, invalidMonitor, null));
        assertEquals(0, cell.attempts.get());
    }

    @ParameterizedTest
    @EnumSource(Shape.class)
    void contextIsCheckedOnlyWhenComputationIsNeeded(Shape shape) {
        Cell cell = new Cell();
        Object invalidContext = new Object();
        assertThrows(ClassCastException.class,
                () -> cell.read(shape, cell.monitor, invalidContext));
        assertEquals(0, cell.attempts.get());
        assertSame(VALUE, cell.read(shape));
        assertSame(VALUE, cell.read(shape, cell.monitor, invalidContext));
        assertEquals(1, cell.attempts.get());
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Timed out waiting for worker");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static value class ValueMonitor { }

    // Factory implementations are still stubs. These accessors supply one atomic
    // location and use the real interface defaults for serialized access.
    private static final class Cell {
        final Object monitor = new Object();
        final AtomicReference<String> value = new AtomicReference<>();
        final AtomicInteger attempts = new AtomicInteger();
        final AtomicInteger active = new AtomicInteger();
        final AtomicInteger maximumActive = new AtomicInteger();
        Supplier<String> computer = () -> VALUE;
        Runnable onMiss = () -> { };
        Runnable beforePublication = () -> { };

        // Model a Void.class context token. Accept Object at the method boundary
        // so a bridge cast cannot reject the context before the location is read.
        final OfScalar<Cell, Object, String> field = new OfScalar<>() {
            @Override
            public Optional<String> get(Cell receiver) {
                assertSame(Cell.this, receiver);
                return peek();
            }

            @Override
            public String getOrCompute(Cell receiver, Object context) {
                assertSame(Cell.this, receiver);
                return compute(context);
            }
        };

        final OfArray<Cell, Object, String> array = new OfArray<>() {
            @Override
            public Optional<String> get(Cell receiver, int index) {
                assertSame(Cell.this, receiver);
                assertEquals(INDEX, index);
                return peek();
            }

            @Override
            public String getOrCompute(Cell receiver, int index, Object context) {
                assertSame(Cell.this, receiver);
                assertEquals(INDEX, index);
                return compute(context);
            }
        };

        String read(Shape shape) {
            return read(shape, monitor, null);
        }

        String read(Shape shape, Object monitor, Object context) {
            return switch (shape) {
                case FIELD -> field.getOrComputeSerialized(monitor, this, context);
                case ARRAY -> array.getOrComputeSerialized(monitor, this, INDEX, context);
            };
        }

        Optional<String> peek() {
            String observed = value.get();
            if (observed == null) {
                onMiss.run();
            }
            return Optional.ofNullable(observed);
        }

        String compute(Object context) {
            assertTrue(Thread.holdsLock(monitor), "The serialized default must delegate only under the monitor");
            String observed = value.get();
            if (observed != null) {
                return observed;
            }
            Void.class.cast(context);
            maximumActive.accumulateAndGet(active.incrementAndGet(), Math::max);
            attempts.incrementAndGet();
            try {
                String candidate = computer.get();
                beforePublication.run();
                assertTrue(Thread.holdsLock(monitor), "Publication must hold the monitor");
                value.compareAndSet(null, candidate);
                return value.get();
            } finally {
                active.decrementAndGet();
            }
        }
    }
}
