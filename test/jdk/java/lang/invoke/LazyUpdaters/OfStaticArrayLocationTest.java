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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.util.Lazy;
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

    private interface StringElement {
        String apply(int index);
    }

    private interface IntArrayRow {
        int[] get(int index);
    }

    private interface IntTernaryOperator {
        int applyAsInt(int i0, int i1, int i2);
    }

    private interface IntVolumeRow {
        int[] get(int i0, int i1);
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

    private static class NaturalMatrix {
        private static final int[][] elements = { new int[3], new int[3] };
    }

    private static class IntVolumes {
        private static final int[][][] plain = newVolume();
        private static final int[][][] published = newVolume();
        private static final int[][][] serialized = newVolume();

        private static int[][][] newVolume() {
            return new int[][][] { { { 0, 29 }, new int[2] }, { new int[2] } };
        }
    }

    private static class VolumeRows {
        private static final int[][][] rows = { { null, { 9 } } };
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

    @Test
    public void defaultModeSupportsStaticArrayElements() {
        AtomicInteger computations = new AtomicInteger();
        IntUnaryOperator updater = Lazy.ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                IntUnaryOperator.class, DefaultModeArray.class.getName() + ".elements", i -> {
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
        for (Lazy.UpdaterMode mode : Lazy.UpdaterMode.values()) {
            String selector = switch (mode) {
                case PLAIN -> IntArrays.class.getName() + ".plain";
                case PUBLISH_ONCE -> IntArrays.class.getName() + ".published";
                case SERIALIZED -> IntArrays.class.getName() + ".serialized";
            };
            int[] array = switch (mode) {
                case PLAIN -> IntArrays.plain;
                case PUBLISH_ONCE -> IntArrays.published;
                case SERIALIZED -> IntArrays.serialized;
            };
            AtomicInteger computations = new AtomicInteger();
            IntUnaryOperator updater = Lazy.ofLocationUpdater(mode, LOOKUP,
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
        for (Lazy.UpdaterMode mode : Lazy.UpdaterMode.values()) {
            String selector = switch (mode) {
                case PLAIN -> IntMatrices.class.getName() + ".plain";
                case PUBLISH_ONCE -> IntMatrices.class.getName() + ".published";
                case SERIALIZED -> IntMatrices.class.getName() + ".serialized";
            };
            int[][] array = switch (mode) {
                case PLAIN -> IntMatrices.plain;
                case PUBLISH_ONCE -> IntMatrices.published;
                case SERIALIZED -> IntMatrices.serialized;
            };
            AtomicInteger computations = new AtomicInteger();
            IntBinaryOperator updater = Lazy.ofLocationUpdater(mode, LOOKUP,
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
    public void threeRankStaticArrayFields() {
        for (Lazy.UpdaterMode mode : Lazy.UpdaterMode.values()) {
            String selector = switch (mode) {
                case PLAIN -> IntVolumes.class.getName() + ".plain";
                case PUBLISH_ONCE -> IntVolumes.class.getName() + ".published";
                case SERIALIZED -> IntVolumes.class.getName() + ".serialized";
            };
            int[][][] array = switch (mode) {
                case PLAIN -> IntVolumes.plain;
                case PUBLISH_ONCE -> IntVolumes.published;
                case SERIALIZED -> IntVolumes.serialized;
            };
            AtomicInteger computations = new AtomicInteger();
            IntTernaryOperator updater = Lazy.ofLocationUpdater(mode, LOOKUP,
                    IntTernaryOperator.class, selector, (i0, i1, i2) -> {
                        computations.incrementAndGet();
                        return 1000 + 100 * i0 + 10 * i1 + i2;
                    });
            assertEquals(0, computations.get());
            assertEquals(1000, updater.applyAsInt(0, 0, 0));
            assertEquals(1000, updater.applyAsInt(0, 0, 0));
            assertEquals(29, updater.applyAsInt(0, 0, 1));
            assertEquals(1010, updater.applyAsInt(0, 1, 0));
            assertEquals(1101, updater.applyAsInt(1, 0, 1));
            assertEquals(3, computations.get());
            assertEquals(1000, array[0][0][0]);
            assertEquals(29, array[0][0][1]);
            assertEquals(1010, array[0][1][0]);
            assertEquals(1101, array[1][0][1]);
        }
    }

    @Test
    public void twoRankStaticArrayUsesNaturalIndexOrder() {
        AtomicInteger computations = new AtomicInteger();
        IntBinaryOperator updater = Lazy.ofLocationUpdater(
                Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                IntBinaryOperator.class, NaturalMatrix.class.getName() + ".elements",
                (i, j) -> {
                    computations.incrementAndGet();
                    return 100 + 10 * i + j;
                });
        assertEquals(102, updater.applyAsInt(0, 2));
        assertEquals(102, updater.applyAsInt(0, 2));
        assertEquals(112, updater.applyAsInt(1, 2));
        assertEquals(2, computations.get());
        assertEquals(102, NaturalMatrix.elements[0][2]);
        assertEquals(112, NaturalMatrix.elements[1][2]);
        assertEquals(0, NaturalMatrix.elements[0][0]);
    }

    @Test
    public void oneIndexMayInitializeRowsOfATwoRankArray() {
        AtomicInteger computations = new AtomicInteger();
        IntArrayRow updater = Lazy.ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE,
                LOOKUP, IntArrayRow.class, RowArray.class.getName() + ".rows",
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
    public void twoIndicesMayInitializeRowsOfAThreeRankStaticArray() {
        AtomicInteger computations = new AtomicInteger();
        IntVolumeRow updater = Lazy.ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE,
                LOOKUP, IntVolumeRow.class, VolumeRows.class.getName() + ".rows",
                (i0, i1) -> {
                    computations.incrementAndGet();
                    return new int[] { 100 + 10 * i0 + i1 };
                });
        int[] first = updater.get(0, 0);
        assertSame(first, updater.get(0, 0));
        assertSame(first, VolumeRows.rows[0][0]);
        assertEquals(100, first[0]);
        assertSame(VolumeRows.rows[0][1], updater.get(0, 1));
        assertEquals(1, computations.get());
    }

    @Test
    public void constructionDoesNotInitializeDeclaringClass() {
        AtomicInteger computations = new AtomicInteger();
        IntUnaryOperator updater = Lazy.ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE,
                LOOKUP, IntUnaryOperator.class, DeferredArrayHolder.class.getName() + ".ints",
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
        for (Lazy.UpdaterMode mode : Lazy.UpdaterMode.values()) {
            // Each mode uses a separate slot so a successful retry does not
            // hide the reserved-value check in another mode.
            int index = mode.ordinal();
            AtomicInteger attempts = new AtomicInteger();
            IntUnaryOperator updater = Lazy.ofLocationUpdater(mode, LOOKUP,
                    IntUnaryOperator.class, ModeRetryArrays.class.getName() + ".ints",
                    i -> attempts.incrementAndGet() == 1 ? 0 : 40 + i);
            assertThrows(IllegalArgumentException.class, () -> updater.applyAsInt(index));
            assertEquals(0, ModeRetryArrays.ints[index]);
            assertEquals(40 + index, updater.applyAsInt(index));
            assertEquals(40 + index, updater.applyAsInt(index));
            assertEquals(2, attempts.get());
        }
    }

    private static class ModeRetryArrays {
        private static final int[] ints = new int[Lazy.UpdaterMode.values().length];
    }

    @Test
    public void customPrimitiveAndReferenceInterfaces() {
        AtomicInteger floatAttempts = new AtomicInteger();
        FloatElement floats = Lazy.ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE,
                LOOKUP, FloatElement.class, RetryArrays.class.getName() + ".floats",
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
        StringElement strings = Lazy.ofLocationUpdater(Lazy.UpdaterMode.SERIALIZED,
                LOOKUP, StringElement.class, RetryArrays.class.getName() + ".strings",
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
        IntUnaryOperator updater = Lazy.ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE,
                LOOKUP, IntUnaryOperator.class, InvalidArrays.class.getName() + ".ints",
                i -> {
                    computations.incrementAndGet();
                    return 7;
                });
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> updater.applyAsInt(-1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> updater.applyAsInt(1));

        IntUnaryOperator missing = Lazy.ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE,
                LOOKUP, IntUnaryOperator.class, InvalidArrays.class.getName() + ".nullArray",
                i -> {
                    computations.incrementAndGet();
                    return 7;
                });
        assertThrows(NullPointerException.class, () -> missing.applyAsInt(0));

        IntBinaryOperator nullRow = Lazy.ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE,
                LOOKUP, IntBinaryOperator.class, InvalidArrays.class.getName() + ".nullRow",
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
        String selector = InvalidArrays.class.getName() + ".ints";
        assertThrows(NullPointerException.class,
                () -> Lazy.ofLocationUpdater(null, LOOKUP, IntUnaryOperator.class,
                        selector, unary));
        assertThrows(NullPointerException.class,
                () -> Lazy.ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, null,
                        IntUnaryOperator.class, selector, unary));
        assertThrows(NullPointerException.class,
                () -> Lazy.ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                        null, selector, unary));
        assertThrows(NullPointerException.class,
                () -> Lazy.ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                        IntUnaryOperator.class, null, unary));
        assertThrows(NullPointerException.class,
                () -> Lazy.ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                        IntUnaryOperator.class, selector, null));

        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                        IntUnaryOperator.class, "", unary));
        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP, IntUnaryOperator.class,
                        "ints", unary));
        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                        IntUnaryOperator.class, InvalidArrays.class.getName() + "..ints", unary));
        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                        IntUnaryOperator.class, InvalidArrays.class.getName() + ".missing", unary));
        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                        IntUnaryOperator.class,
                        InvalidArrays.class.getCanonicalName() + ".ints", unary));
        IllegalArgumentException missingOwner = assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP, IntUnaryOperator.class,
                        "no.such.Class.ints", unary));
        assertInstanceOf(ClassNotFoundException.class, missingOwner.getCause());
        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                        IntBinaryOperator.class, selector, binary));
        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                        IntTernaryOperator.class, InvalidArrays.class.getName() + ".nullRow",
                        (i0, i1, i2) -> 7));
        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                        LongElement.class, selector, i -> 7L));

        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofArrayUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, IntUnaryOperator.class, unary));

        IllegalArgumentException denied = assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE,
                        LOOKUP.dropLookupMode(Lookup.PRIVATE), IntUnaryOperator.class,
                        selector, unary));
        assertInstanceOf(IllegalAccessException.class, denied.getCause());
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void rawGenericInterfaceTokenExplainsRemedy() {
        IntFunction computer = index -> "unused";
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP, IntFunction.class,
                        RetryArrays.class.getName() + ".strings", computer));
        String message = error.getMessage();
        assertTrue(message.contains("IntFunction"), () -> message);
        assertTrue(message.contains("type variable") && message.contains("erased"),
                () -> message);
        assertTrue(message.contains("non-generic") && message.contains("concrete"),
                () -> message);
    }

    @Test
    public void serializedAliasesCoordinateOnLeafArray() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch aliasStarted = new CountDownLatch(1);
        AtomicInteger firstComputations = new AtomicInteger();
        AtomicInteger aliasComputations = new AtomicInteger();
        IntBinaryOperator first = Lazy.ofLocationUpdater(Lazy.UpdaterMode.SERIALIZED,
                LOOKUP, IntBinaryOperator.class, AliasedMatrices.class.getName() + ".first",
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
        IntBinaryOperator second = Lazy.ofLocationUpdater(Lazy.UpdaterMode.SERIALIZED,
                LOOKUP, IntBinaryOperator.class, AliasedMatrices.class.getName() + ".second",
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
        IntUnaryOperator staticUpdater = Lazy.ofLocationUpdater(
                Lazy.UpdaterMode.SERIALIZED, LOOKUP, IntUnaryOperator.class,
                CrossShapeArray.class.getName() + ".elements", index -> {
                    entered.countDown();
                    try {
                        assertTrue(release.await(20, SECONDS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                    return 42;
                });
        IntArrayElement locationUpdater = Lazy.ofArrayUpdater(
                Lazy.UpdaterMode.SERIALIZED, IntArrayElement.class,
                (array, index) -> {
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
