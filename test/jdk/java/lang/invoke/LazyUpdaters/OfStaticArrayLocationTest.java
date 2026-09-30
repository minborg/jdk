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
 * @summary Lazy location updaters for elements of static array fields
 * @enablePreview
 * @run junit OfStaticArrayLocationTest
 * @run junit/othervm -Xint OfStaticArrayLocationTest
 */

import java.lang.invoke.LazyUpdaters;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntBinaryOperator;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OfStaticArrayLocationTest {
    private static final Lookup LOOKUP = MethodHandles.lookup();
    private static boolean deferredArrayHolderInitialized;

    private interface FloatElement {
        float get(int index);
    }

    private interface LongElement {
        long get(int index);
    }

    private interface IntArrayRow {
        int[] get(int index);
    }

    private interface IntArrayElement {
        int get(int[] array, int index);
    }

    private static class IntArrays {
        private static final int[] plain = { 0, 31, 0 };
        private static final int[] published = { 0, 31, 0 };
        private static final int[] serialized = { 0, 31, 0 };
    }

    private static class DefaultModeArray {
        private static final int[] elements = new int[1];
    }

    private static class IntMatrices {
        private static final int[][] plain = { { 0, 29 }, new int[2] };
        private static final int[][] published = { { 0, 29 }, new int[2] };
        private static final int[][] serialized = { { 0, 29 }, new int[2] };
    }

    private static class SwappedMatrix {
        private static final int[][] elements = { new int[3], new int[3] };
    }

    private static class RetryArrays {
        private static final float[] floats = new float[2];
        private static final String[] strings = { null, "preexisting" };
    }

    private static class RowArray {
        private static final int[][] rows = { null, { 5, 6 } };
    }

    private static class DeferredArrayHolder {
        private static final int[] ints = initialize();

        private static int[] initialize() {
            deferredArrayHolderInitialized = true;
            return new int[] { 19 };
        }
    }

    private static class InvalidArrays {
        private static final int[] ints = new int[1];
        private static final int[][] nullRow = { null };
        private static final int[] nullArray = null;
    }

    private static class AliasedMatrices {
        private static final int[] shared = new int[2];
        private static final int[][] first = { shared };
        private static final int[][] second = { shared };
    }

    private static class CrossShapeArray {
        private static final int[] elements = new int[1];
    }

    @SuppressWarnings("unchecked")
    private static Class<IntFunction<String>> stringFunctionType() {
        return (Class<IntFunction<String>>) (Class<?>) IntFunction.class;
    }

    @Test
    public void defaultModeSupportsStaticArrayElements() {
        AtomicInteger computations = new AtomicInteger();
        IntUnaryOperator updater = LazyUpdaters.ofLocation(LOOKUP,
                IntUnaryOperator.class, i -> DefaultModeArray.elements[i], i -> {
                    computations.incrementAndGet();
                    return 42;
                });
        assertEquals(42, updater.applyAsInt(0));
        assertEquals(42, updater.applyAsInt(0));
        assertEquals(42, DefaultModeArray.elements[0]);
        assertEquals(1, computations.get());
    }

    @Test
    public void oneRankStaticArrayFields() {
        for (LazyUpdaters.Mode mode : LazyUpdaters.Mode.values()) {
            IntUnaryOperator selector = switch (mode) {
                case PLAIN -> i -> IntArrays.plain[i];
                case PUBLISH_ONCE -> i -> IntArrays.published[i];
                case SERIALIZED -> i -> IntArrays.serialized[i];
            };
            int[] array = switch (mode) {
                case PLAIN -> IntArrays.plain;
                case PUBLISH_ONCE -> IntArrays.published;
                case SERIALIZED -> IntArrays.serialized;
            };
            AtomicInteger computations = new AtomicInteger();
            IntUnaryOperator updater = LazyUpdaters.ofLocation(mode, LOOKUP,
                    IntUnaryOperator.class, selector, index -> {
                        computations.incrementAndGet();
                        return 11 + index;
                    });
            assertEquals(0, computations.get());
            assertEquals(11, updater.applyAsInt(0));
            assertEquals(11, updater.applyAsInt(0));
            assertEquals(31, updater.applyAsInt(1));
            assertEquals(13, updater.applyAsInt(2));
            assertEquals(2, computations.get());
            assertEquals(11, array[0]);
            assertEquals(31, array[1]);
            assertEquals(13, array[2]);
        }
    }

    @Test
    public void twoRankStaticArrayFields() {
        for (LazyUpdaters.Mode mode : LazyUpdaters.Mode.values()) {
            IntBinaryOperator selector = switch (mode) {
                case PLAIN -> (i, j) -> IntMatrices.plain[i][j];
                case PUBLISH_ONCE -> (i, j) -> IntMatrices.published[i][j];
                case SERIALIZED -> (i, j) -> IntMatrices.serialized[i][j];
            };
            int[][] array = switch (mode) {
                case PLAIN -> IntMatrices.plain;
                case PUBLISH_ONCE -> IntMatrices.published;
                case SERIALIZED -> IntMatrices.serialized;
            };
            AtomicInteger computations = new AtomicInteger();
            IntBinaryOperator updater = LazyUpdaters.ofLocation(mode, LOOKUP,
                    IntBinaryOperator.class, selector, (row, column) -> {
                        computations.incrementAndGet();
                        return 100 + 10 * row + column;
                    });
            assertEquals(0, computations.get());
            assertEquals(100, updater.applyAsInt(0, 0));
            assertEquals(100, updater.applyAsInt(0, 0));
            assertEquals(29, updater.applyAsInt(0, 1));
            assertEquals(110, updater.applyAsInt(1, 0));
            assertEquals(111, updater.applyAsInt(1, 1));
            assertEquals(3, computations.get());
            assertEquals(100, array[0][0]);
            assertEquals(29, array[0][1]);
            assertEquals(110, array[1][0]);
            assertEquals(111, array[1][1]);
        }
    }

    @Test
    public void twoRankStaticArrayMaySwapIndices() {
        AtomicInteger computations = new AtomicInteger();
        IntBinaryOperator updater = LazyUpdaters.ofLocation(
                LazyUpdaters.Mode.PUBLISH_ONCE, LOOKUP,
                IntBinaryOperator.class,
                (i, j) -> SwappedMatrix.elements[j][i],
                (i, j) -> {
                    computations.incrementAndGet();
                    return 100 + 10 * i + j;
                });
        assertEquals(120, updater.applyAsInt(2, 0));
        assertEquals(120, updater.applyAsInt(2, 0));
        assertEquals(121, updater.applyAsInt(2, 1));
        assertEquals(2, computations.get());
        assertEquals(120, SwappedMatrix.elements[0][2]);
        assertEquals(121, SwappedMatrix.elements[1][2]);
        assertEquals(0, SwappedMatrix.elements[0][0]);
    }

    @Test
    public void oneIndexMayInitializeRowsOfATwoRankArray() {
        AtomicInteger computations = new AtomicInteger();
        IntArrayRow updater = LazyUpdaters.ofLocation(LazyUpdaters.Mode.PUBLISH_ONCE,
                LOOKUP, IntArrayRow.class, index -> RowArray.rows[index],
                index -> {
                    computations.incrementAndGet();
                    return new int[] { index + 10, index + 20 };
                });
        int[] first = updater.get(0);
        assertSame(first, updater.get(0));
        assertSame(first, RowArray.rows[0]);
        assertEquals(10, first[0]);
        assertEquals(20, first[1]);
        assertSame(RowArray.rows[1], updater.get(1));
        assertEquals(1, computations.get());
    }

    @Test
    public void constructionDoesNotInitializeDeclaringClass() {
        AtomicInteger computations = new AtomicInteger();
        IntUnaryOperator updater = LazyUpdaters.ofLocation(LazyUpdaters.Mode.PUBLISH_ONCE,
                LOOKUP, IntUnaryOperator.class, index -> DeferredArrayHolder.ints[index],
                index -> {
                    computations.incrementAndGet();
                    return 42;
                });
        assertFalse(deferredArrayHolderInitialized);
        assertEquals(19, updater.applyAsInt(0));
        assertTrue(deferredArrayHolderInitialized);
        assertEquals(0, computations.get());
    }

    @Test
    public void defaultResultsAreRejectedAndCanBeRetried() {
        for (LazyUpdaters.Mode mode : LazyUpdaters.Mode.values()) {
            // Each mode uses a separate slot so a successful retry does not
            // hide the reserved-value check in another mode.
            int index = mode.ordinal();
            AtomicInteger attempts = new AtomicInteger();
            IntUnaryOperator updater = LazyUpdaters.ofLocation(mode, LOOKUP,
                    IntUnaryOperator.class, i -> ModeRetryArrays.ints[i],
                    i -> attempts.incrementAndGet() == 1 ? 0 : 40 + i);
            assertThrows(IllegalArgumentException.class, () -> updater.applyAsInt(index));
            assertEquals(0, ModeRetryArrays.ints[index]);
            assertEquals(40 + index, updater.applyAsInt(index));
            assertEquals(40 + index, updater.applyAsInt(index));
            assertEquals(2, attempts.get());
        }
    }

    private static class ModeRetryArrays {
        private static final int[] ints = new int[LazyUpdaters.Mode.values().length];
    }

    @Test
    public void customPrimitiveAndReferenceInterfaces() {
        AtomicInteger floatAttempts = new AtomicInteger();
        FloatElement floats = LazyUpdaters.ofLocation(LazyUpdaters.Mode.PUBLISH_ONCE,
                LOOKUP, FloatElement.class, i -> RetryArrays.floats[i],
                index -> {
                    if (index == 0) {
                        return -0.0f;
                    }
                    return floatAttempts.incrementAndGet() == 1 ? +0.0f : 2.5f;
                });
        assertEquals(Float.floatToRawIntBits(-0.0f),
                Float.floatToRawIntBits(floats.get(0)));
        assertThrows(IllegalArgumentException.class, () -> floats.get(1));
        assertEquals(0.0f, RetryArrays.floats[1]);
        assertEquals(2.5f, floats.get(1));
        assertEquals(2, floatAttempts.get());

        AtomicInteger stringAttempts = new AtomicInteger();
        IntFunction<String> strings = LazyUpdaters.ofLocation(LazyUpdaters.Mode.SERIALIZED,
                LOOKUP, stringFunctionType(), i -> RetryArrays.strings[i],
                index -> stringAttempts.incrementAndGet() == 1 ? null : "computed");
        assertThrows(IllegalArgumentException.class, () -> strings.apply(0));
        assertNull(RetryArrays.strings[0]);
        assertEquals("computed", strings.apply(0));
        assertEquals("computed", strings.apply(0));
        assertEquals("preexisting", strings.apply(1));
        assertEquals(2, stringAttempts.get());
    }

    @Test
    public void invalidCoordinatesFailBeforeComputation() {
        AtomicInteger computations = new AtomicInteger();
        IntUnaryOperator updater = LazyUpdaters.ofLocation(LazyUpdaters.Mode.PUBLISH_ONCE,
                LOOKUP, IntUnaryOperator.class, i -> InvalidArrays.ints[i],
                i -> {
                    computations.incrementAndGet();
                    return 7;
                });
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> updater.applyAsInt(-1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> updater.applyAsInt(1));

        IntUnaryOperator missing = LazyUpdaters.ofLocation(LazyUpdaters.Mode.PUBLISH_ONCE,
                LOOKUP, IntUnaryOperator.class, i -> InvalidArrays.nullArray[i],
                i -> {
                    computations.incrementAndGet();
                    return 7;
                });
        assertThrows(NullPointerException.class, () -> missing.applyAsInt(0));

        IntBinaryOperator nullRow = LazyUpdaters.ofLocation(LazyUpdaters.Mode.PUBLISH_ONCE,
                LOOKUP, IntBinaryOperator.class, (i, j) -> InvalidArrays.nullRow[i][j],
                (i, j) -> {
                    computations.incrementAndGet();
                    return 7;
                });
        assertThrows(NullPointerException.class, () -> nullRow.applyAsInt(0, 0));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> nullRow.applyAsInt(1, 0));
        assertEquals(0, computations.get());
    }

    @Test
    public void selectorsAndInterfacesAreValidated() {
        IntUnaryOperator unary = i -> 7;
        IntBinaryOperator binary = (i, j) -> 7;
        IntUnaryOperator selector = i -> InvalidArrays.ints[i];
        assertThrows(NullPointerException.class,
                () -> LazyUpdaters.ofLocation(null, LOOKUP, IntUnaryOperator.class,
                        selector, unary));
        assertThrows(NullPointerException.class,
                () -> LazyUpdaters.ofLocation(LazyUpdaters.Mode.PUBLISH_ONCE, null,
                        IntUnaryOperator.class, selector, unary));
        assertThrows(NullPointerException.class,
                () -> LazyUpdaters.ofLocation(LazyUpdaters.Mode.PUBLISH_ONCE, LOOKUP,
                        null, selector, unary));
        assertThrows(NullPointerException.class,
                () -> LazyUpdaters.ofLocation(LazyUpdaters.Mode.PUBLISH_ONCE, LOOKUP,
                        IntUnaryOperator.class, null, unary));
        assertThrows(NullPointerException.class,
                () -> LazyUpdaters.ofLocation(LazyUpdaters.Mode.PUBLISH_ONCE, LOOKUP,
                        IntUnaryOperator.class, selector, null));

        assertThrows(IllegalArgumentException.class,
                () -> LazyUpdaters.ofLocation(LazyUpdaters.Mode.PUBLISH_ONCE, LOOKUP,
                        IntUnaryOperator.class, i -> InvalidArrays.ints.clone()[i], unary));
        int[] captured = new int[1];
        assertThrows(IllegalArgumentException.class,
                () -> LazyUpdaters.ofLocation(LazyUpdaters.Mode.PUBLISH_ONCE, LOOKUP,
                        IntUnaryOperator.class, i -> captured[i], unary));
        assertThrows(IllegalArgumentException.class,
                () -> LazyUpdaters.ofLocation(LazyUpdaters.Mode.PUBLISH_ONCE, LOOKUP,
                        IntUnaryOperator.class, i -> InvalidArrays.ints[i] + 1, unary));
        assertThrows(IllegalArgumentException.class,
                () -> LazyUpdaters.ofLocation(LazyUpdaters.Mode.PUBLISH_ONCE, LOOKUP,
                        IntBinaryOperator.class, (i, j) -> InvalidArrays.ints[i], binary));
        assertThrows(IllegalArgumentException.class,
                () -> LazyUpdaters.ofLocation(LazyUpdaters.Mode.PUBLISH_ONCE, LOOKUP,
                        LongElement.class, i -> (long) InvalidArrays.ints[i], i -> 7L));

        IllegalArgumentException denied = assertThrows(IllegalArgumentException.class,
                () -> LazyUpdaters.ofLocation(LazyUpdaters.Mode.PUBLISH_ONCE,
                        MethodHandles.publicLookup(), IntUnaryOperator.class,
                        selector, unary));
        assertInstanceOf(IllegalAccessException.class, denied.getCause());
    }

    @Test
    public void serializedAliasesCoordinateOnLeafArray() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch aliasStarted = new CountDownLatch(1);
        AtomicInteger firstComputations = new AtomicInteger();
        AtomicInteger aliasComputations = new AtomicInteger();
        IntBinaryOperator first = LazyUpdaters.ofLocation(LazyUpdaters.Mode.SERIALIZED,
                LOOKUP, IntBinaryOperator.class, (i, j) -> AliasedMatrices.first[i][j],
                (row, column) -> {
                    firstComputations.incrementAndGet();
                    if (column == 0) {
                        entered.countDown();
                        try {
                            assertTrue(release.await(20, SECONDS));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(e);
                        }
                    }
                    return 42 + column;
                });
        IntBinaryOperator second = LazyUpdaters.ofLocation(LazyUpdaters.Mode.SERIALIZED,
                LOOKUP, IntBinaryOperator.class, (i, j) -> AliasedMatrices.second[i][j],
                (row, column) -> {
                    aliasComputations.incrementAndGet();
                    return 99;
                });

        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<Integer> computing = executor.submit(() -> first.applyAsInt(0, 0));
            assertTrue(entered.await(20, SECONDS));
            Future<Integer> independent = executor.submit(() -> first.applyAsInt(0, 1));
            assertEquals(43, independent.get(20, SECONDS).intValue());

            Future<Integer> sameElement = executor.submit(() -> {
                aliasStarted.countDown();
                return second.applyAsInt(0, 0);
            });
            assertTrue(aliasStarted.await(20, SECONDS));
            assertThrows(TimeoutException.class, () -> sameElement.get(250, MILLISECONDS));
            assertEquals(0, aliasComputations.get());

            release.countDown();
            assertEquals(42, computing.get(20, SECONDS).intValue());
            assertEquals(42, sameElement.get(20, SECONDS).intValue());
            assertEquals(42, AliasedMatrices.shared[0]);
            assertEquals(43, AliasedMatrices.shared[1]);
            assertEquals(2, firstComputations.get());
            assertEquals(0, aliasComputations.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(20, SECONDS));
        }
    }

    @Test
    public void serializedStaticAndReceiverArrayLocationsShareElementCoordination() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch locationStarted = new CountDownLatch(1);
        AtomicInteger locationComputations = new AtomicInteger();
        IntUnaryOperator staticUpdater = LazyUpdaters.ofLocation(
                LazyUpdaters.Mode.SERIALIZED, LOOKUP, IntUnaryOperator.class,
                index -> CrossShapeArray.elements[index], index -> {
                    entered.countDown();
                    try {
                        assertTrue(release.await(20, SECONDS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                    return 42;
                });
        IntArrayElement locationUpdater = LazyUpdaters.ofLocation(
                LazyUpdaters.Mode.SERIALIZED, LOOKUP, IntArrayElement.class,
                (array, index) -> array[index], (array, index) -> {
                    locationComputations.incrementAndGet();
                    return 99;
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> computing = executor.submit(() -> staticUpdater.applyAsInt(0));
            assertTrue(entered.await(20, SECONDS));
            Future<Integer> sameElement = executor.submit(() -> {
                locationStarted.countDown();
                return locationUpdater.get(CrossShapeArray.elements, 0);
            });
            assertTrue(locationStarted.await(20, SECONDS));
            assertThrows(TimeoutException.class, () -> sameElement.get(250, MILLISECONDS));
            assertEquals(0, locationComputations.get());

            release.countDown();
            assertEquals(42, computing.get(20, SECONDS).intValue());
            assertEquals(42, sameElement.get(20, SECONDS).intValue());
            assertEquals(42, CrossShapeArray.elements[0]);
            assertEquals(0, locationComputations.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(20, SECONDS));
        }
    }
}
