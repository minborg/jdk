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
 * @summary Lazy location updaters for fields and array elements
 * @enablePreview
 * @run junit OfLocationTest
 * @run junit/othervm -Xint OfLocationTest
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OfLocationTest {
    private static final Lookup LOOKUP = MethodHandles.lookup();
    private static boolean deferredHolderInitialized;
    private static boolean deferredSynchronizedHolderInitialized;

    private interface BooleanField { boolean get(Holder holder); }
    private interface ByteField { byte get(Holder holder); }
    private interface ShortField { short get(Holder holder); }
    private interface CharField { char get(Holder holder); }
    private interface CountField {
        int get(Holder holder);

        default int twice(Holder holder) {
            return 2 * get(holder);
        }
    }
    private interface LongField { long get(Holder holder); }
    private interface FloatField { float get(Holder holder); }
    private interface DoubleField { double get(Holder holder); }
    private interface BooleanSupplier { boolean get(); }
    private interface ByteSupplier { byte get(); }
    private interface ShortSupplier { short get(); }
    private interface CharSupplier { char get(); }
    private interface FloatSupplier { float get(); }
    private interface ParentStringField { CharSequence get(Holder holder); }
    private interface StringField extends ParentStringField {
        @Override
        String get(Holder holder);
    }
    private interface InheritedStringField extends Function<Holder, String> { }
    private interface GenericField<C, R> { R get(C holder); }
    private interface ConcreteStringField { String get(Holder holder); }
    @FunctionalInterface
    private interface ParameterBridgeField extends GenericField<Holder, String>, ConcreteStringField { }
    private interface TwoArguments { int get(Holder first, Holder second); }
    private interface IntArrayElement { int get(int[] array, int index); }
    private interface IntArray2DElement { int get(int[][] array, int row, int column); }
    private interface OwnedIntArrayElement { int get(ArrayOwner owner, int index); }
    private interface OwnedIntArray2DElement { int get(ArrayOwner owner, int row, int column); }
    private interface StringArrayElement { String get(String[] array, int index); }

    static class Parent {
        int inherited;
    }

    static class Holder extends Parent {
        private boolean flag;
        private byte octet;
        private short small;
        private char character;
        private int count;
        private int synchronizedCount;
        private int plainCount;
        private long large;
        private long synchronizedLarge;
        private long plainLarge;
        private float fraction;
        private double radius;
        private String text;
        private String synchronizedText;
        private String plainText;
        private Double boxed;
        private int[] array;
        private Payload payload;
        private volatile int published;

        int count() {
            return count;
        }
    }

    static class EqualHolder extends Holder {
        @Override
        public boolean equals(Object other) {
            return other instanceof EqualHolder;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }

    static class ArrayOwner {
        private final int[] row = new int[2];
        private final int[][] matrix = { new int[3], new int[3] };
    }

    static class StaticHolder {
        private static boolean flag;
        private static byte octet;
        private static short small;
        private static char character;
        private static int count;
        private static long large;
        private static float fraction;
        private static double radius;
        private static String text;
        private static Integer boxed;
    }

    static class DeferredHolder {
        private static int count = initialize();

        private static int initialize() {
            deferredHolderInitialized = true;
            return 19;
        }
    }

    static class SynchronizedStaticHolder {
        private static int count;
    }

    static class PlainStaticHolder {
        private static long large;
    }

    static class DeferredSynchronizedHolder {
        private static int count = initialize();

        private static int initialize() {
            deferredSynchronizedHolderInitialized = true;
            return 19;
        }
    }

    static class FinalHolder {
        private final Object value = new Object();
    }

    static class Payload {
        int value;
        Object marker;
    }

    @SuppressWarnings("unchecked")
    private static <C, R> Class<Function<C, R>> functionType() {
        return (Class<Function<C, R>>) (Class<?>) Function.class;
    }

    @SuppressWarnings("unchecked")
    private static <R> Class<Supplier<R>> supplierType() {
        return (Class<Supplier<R>>) (Class<?>) Supplier.class;
    }

    @SuppressWarnings("unchecked")
    private static <C> Class<ToIntFunction<C>> intFunctionType() {
        return (Class<ToIntFunction<C>>) (Class<?>) ToIntFunction.class;
    }

    @Test
    public void primitiveInstanceFields() {
        Holder holder = new Holder();
        BooleanField flag = LazyUpdaters.ofLocation(LOOKUP, BooleanField.class,
                h -> h.flag, h -> true);
        ByteField octet = LazyUpdaters.ofLocation(LOOKUP, ByteField.class,
                h -> h.octet, h -> (byte) 12);
        ShortField small = LazyUpdaters.ofLocation(LOOKUP, ShortField.class,
                h -> h.small, h -> (short) 123);
        CharField character = LazyUpdaters.ofLocation(LOOKUP, CharField.class,
                h -> h.character, h -> 'x');
        CountField count = LazyUpdaters.ofLocation(LOOKUP, CountField.class,
                h -> h.count, h -> 1234);
        LongField large = LazyUpdaters.ofLocation(LOOKUP, LongField.class,
                h -> h.large, h -> 12345L);
        FloatField fraction = LazyUpdaters.ofLocation(LOOKUP, FloatField.class,
                h -> h.fraction, h -> 1.25f);
        DoubleField radius = LazyUpdaters.ofLocation(LOOKUP, DoubleField.class,
                h -> h.radius, h -> 2.5);

        for (int i = 0; i < 2; i++) {
            assertTrue(flag.get(holder));
            assertEquals((byte) 12, octet.get(holder));
            assertEquals((short) 123, small.get(holder));
            assertEquals('x', character.get(holder));
            assertEquals(1234, count.get(holder));
            assertEquals(2468, count.twice(holder));
            assertEquals(12345L, large.get(holder));
            assertEquals(1.25f, fraction.get(holder));
            assertEquals(2.5, radius.get(holder));
        }
        assertTrue(holder.flag);
        assertEquals((byte) 12, holder.octet);
        assertEquals((short) 123, holder.small);
        assertEquals('x', holder.character);
        assertEquals(1234, holder.count);
        assertEquals(12345L, holder.large);
        assertEquals(1.25f, holder.fraction);
        assertEquals(2.5, holder.radius);
    }

    @Test
    public void boxedPrimitiveFields() {
        checkBoxing(h -> h.flag, true);
        checkBoxing(h -> h.octet, (byte) 12);
        checkBoxing(h -> h.small, (short) 123);
        checkBoxing(h -> h.character, 'x');
        checkBoxing(h -> h.count, 1234);
        checkBoxing(h -> h.large, 12345L);
        checkBoxing(h -> h.fraction, 1.25f);
        checkBoxing(h -> h.radius, 2.5);
    }

    private static <R> void checkBoxing(Function<Holder, R> selector, R result) {
        AtomicInteger computations = new AtomicInteger();
        Function<Holder, R> updater = LazyUpdaters.ofLocation(LOOKUP, functionType(),
                selector, h -> {
                    computations.incrementAndGet();
                    return result;
                });
        Holder holder = new Holder();
        assertEquals(result, updater.apply(holder));
        assertEquals(result, updater.apply(holder));
        assertEquals(result, selector.apply(holder));
        assertEquals(1, computations.get());
    }

    @Test
    public void referenceFieldsAndInheritedInterfaces() {
        Holder holder = new Holder();
        String result = new String("computed");
        StringField covariant = LazyUpdaters.ofLocation(LOOKUP, StringField.class,
                h -> h.text, h -> result);
        ParentStringField parentView = covariant;
        assertSame(result, covariant.get(holder));
        assertSame(result, parentView.get(holder));
        assertSame(result, holder.text);

        InheritedStringField inherited = LazyUpdaters.ofLocation(LOOKUP,
                InheritedStringField.class, h -> h.text, h -> "inherited");
        assertEquals("inherited", inherited.apply(new Holder()));
        assertSame(result, inherited.apply(holder));

        int[] array = { 1, 2, 3 };
        Function<Holder, int[]> arrayUpdater = LazyUpdaters.ofLocation(LOOKUP,
                OfLocationTest.<Holder, int[]>functionType(), h -> h.array, h -> array);
        assertSame(array, arrayUpdater.apply(holder));
        assertSame(array, arrayUpdater.apply(holder));

        // A boxed zero is an ordinary non-null value in a reference field.
        Function<Holder, Double> boxed = LazyUpdaters.ofLocation(LOOKUP,
                OfLocationTest.<Holder, Double>functionType(), h -> h.boxed, h -> 0.0);
        assertEquals(0.0, boxed.apply(holder));
        assertEquals(0.0, holder.boxed);
    }

    @Test
    public void initializationIsLazyAndPerReceiver() {
        AtomicInteger computations = new AtomicInteger();
        ToIntFunction<Holder> updater = LazyUpdaters.ofLocation(LOOKUP,
                OfLocationTest.<Holder>intFunctionType(), h -> h.count,
                h -> computations.incrementAndGet());
        assertEquals(0, computations.get());
        Holder first = new Holder();
        Holder second = new Holder();
        assertEquals(1, updater.applyAsInt(first));
        assertEquals(1, updater.applyAsInt(first));
        assertEquals(2, updater.applyAsInt(second));
        assertEquals(2, updater.applyAsInt(second));
        assertEquals(1, first.count);
        assertEquals(2, second.count);
        assertEquals(2, computations.get());
    }

    @Test
    public void oneRankArrayElements() {
        for (LazyUpdaters.Mode mode : new LazyUpdaters.Mode[] {
                LazyUpdaters.Mode.PLAIN, LazyUpdaters.Mode.PUBLISH_ONCE,
                LazyUpdaters.Mode.SERIALIZED }) {
            AtomicInteger computations = new AtomicInteger();
            IntArrayElement updater = LazyUpdaters.ofLocation(mode, LOOKUP,
                    IntArrayElement.class, (array, index) -> array[index],
                    (array, index) -> {
                        computations.incrementAndGet();
                        return 11 + index;
                    });
            int[] first = { 0, 31, 0 };
            int[] second = new int[1];
            assertEquals(0, computations.get());
            assertEquals(11, updater.get(first, 0));
            assertEquals(11, updater.get(first, 0));
            assertEquals(31, updater.get(first, 1));
            assertEquals(13, updater.get(first, 2));
            assertEquals(11, updater.get(second, 0));
            assertEquals(3, computations.get());
            assertEquals(11, first[0]);
            assertEquals(31, first[1]);
            assertEquals(13, first[2]);
            assertEquals(11, second[0]);

            assertThrows(ArrayIndexOutOfBoundsException.class,
                    () -> updater.get(first, -1));
            assertThrows(ArrayIndexOutOfBoundsException.class,
                    () -> updater.get(first, first.length));
            assertThrows(NullPointerException.class, () -> updater.get(null, 0));
            assertEquals(3, computations.get());

            AtomicInteger attempts = new AtomicInteger();
            IntArrayElement retry = LazyUpdaters.ofLocation(mode, LOOKUP,
                    IntArrayElement.class, (array, index) -> array[index],
                    (array, index) -> attempts.incrementAndGet() == 1 ? 0 : 7);
            int[] empty = new int[1];
            assertThrows(IllegalArgumentException.class, () -> retry.get(empty, 0));
            assertEquals(0, empty[0]);
            assertEquals(7, retry.get(empty, 0));
            assertEquals(7, retry.get(empty, 0));
            assertEquals(2, attempts.get());
        }
    }

    @Test
    public void twoRankArrayElements() {
        for (LazyUpdaters.Mode mode : new LazyUpdaters.Mode[] {
                LazyUpdaters.Mode.PUBLISH_ONCE, LazyUpdaters.Mode.SERIALIZED }) {
            AtomicInteger computations = new AtomicInteger();
            IntArray2DElement updater = LazyUpdaters.ofLocation(mode, LOOKUP,
                    IntArray2DElement.class, (array, row, column) -> array[row][column],
                    (array, row, column) -> {
                        computations.incrementAndGet();
                        return 100 + 10 * row + column;
                    });
            int[] shared = new int[2];
            int[][] first = { { 0, 29 }, shared };
            int[][] alias = { shared };
            assertEquals(0, computations.get());
            assertEquals(100, updater.get(first, 0, 0));
            assertEquals(100, updater.get(first, 0, 0));
            assertEquals(29, updater.get(first, 0, 1));
            assertEquals(110, updater.get(first, 1, 0));
            assertEquals(110, updater.get(alias, 0, 0));
            assertEquals(111, updater.get(first, 1, 1));
            assertEquals(3, computations.get());
            assertEquals(100, first[0][0]);
            assertEquals(29, first[0][1]);
            assertEquals(110, shared[0]);
            assertEquals(111, shared[1]);

            assertThrows(ArrayIndexOutOfBoundsException.class,
                    () -> updater.get(first, -1, 0));
            assertThrows(ArrayIndexOutOfBoundsException.class,
                    () -> updater.get(first, 0, first[0].length));
            assertThrows(NullPointerException.class, () -> updater.get(null, 0, 0));
            int[][] nullRow = { null };
            assertThrows(NullPointerException.class, () -> updater.get(nullRow, 0, 0));
            assertEquals(3, computations.get());

            AtomicInteger attempts = new AtomicInteger();
            IntArray2DElement retry = LazyUpdaters.ofLocation(mode, LOOKUP,
                    IntArray2DElement.class, (array, row, column) -> array[row][column],
                    (array, row, column) -> attempts.incrementAndGet() == 1 ? 0 : 7);
            int[][] empty = { new int[1] };
            assertThrows(IllegalArgumentException.class, () -> retry.get(empty, 0, 0));
            assertEquals(0, empty[0][0]);
            assertEquals(7, retry.get(empty, 0, 0));
            assertEquals(7, retry.get(empty, 0, 0));
            assertEquals(2, attempts.get());
        }
    }

    @Test
    public void twoRankArrayElementsMaySwapIndices() {
        for (LazyUpdaters.Mode mode : new LazyUpdaters.Mode[] {
                LazyUpdaters.Mode.PUBLISH_ONCE, LazyUpdaters.Mode.SERIALIZED }) {
            AtomicInteger computations = new AtomicInteger();
            IntArray2DElement updater = LazyUpdaters.ofLocation(mode, LOOKUP,
                    IntArray2DElement.class, (array, i0, i1) -> array[i1][i0],
                    (array, i0, i1) -> {
                        computations.incrementAndGet();
                        return 100 + 10 * i0 + i1;
                    });
            int[][] array = { new int[3], new int[3] };
            assertEquals(120, updater.get(array, 2, 0));
            assertEquals(120, updater.get(array, 2, 0));
            assertEquals(110, updater.get(array, 1, 0));
            assertEquals(121, updater.get(array, 2, 1));
            assertEquals(3, computations.get());
            assertEquals(120, array[0][2]);
            assertEquals(110, array[0][1]);
            assertEquals(121, array[1][2]);
            assertEquals(0, array[0][0]);
            assertEquals(0, array[1][1]);
        }
    }

    @Test
    public void arrayElementsInFinalOwnerFields() {
        for (LazyUpdaters.Mode mode : new LazyUpdaters.Mode[] {
                LazyUpdaters.Mode.PUBLISH_ONCE, LazyUpdaters.Mode.SERIALIZED }) {
            AtomicInteger rowComputations = new AtomicInteger();
            OwnedIntArrayElement rowUpdater = LazyUpdaters.ofLocation(mode, LOOKUP,
                    OwnedIntArrayElement.class, (owner, index) -> owner.row[index],
                    (owner, index) -> {
                        rowComputations.incrementAndGet();
                        return 20 + index;
                    });
            AtomicInteger matrixComputations = new AtomicInteger();
            OwnedIntArray2DElement matrixUpdater = LazyUpdaters.ofLocation(mode, LOOKUP,
                    OwnedIntArray2DElement.class,
                    (owner, row, column) -> owner.matrix[row][column],
                    (owner, row, column) -> {
                        matrixComputations.incrementAndGet();
                        return 100 + 10 * row + column;
                    });
            ArrayOwner first = new ArrayOwner();
            ArrayOwner second = new ArrayOwner();
            first.row[1] = 57;
            first.matrix[0][0] = 79;
            assertEquals(20, rowUpdater.get(first, 0));
            assertEquals(20, rowUpdater.get(first, 0));
            assertEquals(57, rowUpdater.get(first, 1));
            assertEquals(20, rowUpdater.get(second, 0));
            assertEquals(2, rowComputations.get());
            assertEquals(20, first.row[0]);
            assertEquals(20, second.row[0]);

            assertEquals(101, matrixUpdater.get(first, 0, 1));
            assertEquals(101, matrixUpdater.get(first, 0, 1));
            assertEquals(110, matrixUpdater.get(first, 1, 0));
            assertEquals(79, matrixUpdater.get(first, 0, 0));
            assertEquals(101, matrixUpdater.get(second, 0, 1));
            assertEquals(3, matrixComputations.get());
            assertEquals(101, first.matrix[0][1]);
            assertEquals(110, first.matrix[1][0]);
            assertEquals(101, second.matrix[0][1]);

            OwnedIntArray2DElement swapped = LazyUpdaters.ofLocation(mode, LOOKUP,
                    OwnedIntArray2DElement.class,
                    (owner, i0, i1) -> owner.matrix[i1][i0],
                    (owner, i0, i1) -> 200 + 10 * i0 + i1);
            assertEquals(220, swapped.get(first, 2, 0));
            assertEquals(220, first.matrix[0][2]);
            assertEquals(0, first.matrix[1][2]);
        }
    }

    @Test
    public void referenceArrayElementsUseNullAsTheSentinel() {
        for (LazyUpdaters.Mode mode : new LazyUpdaters.Mode[] {
                LazyUpdaters.Mode.PUBLISH_ONCE, LazyUpdaters.Mode.SERIALIZED }) {
            AtomicInteger computations = new AtomicInteger();
            String computed = new String("computed");
            StringArrayElement updater = LazyUpdaters.ofLocation(mode, LOOKUP,
                    StringArrayElement.class, (array, index) -> array[index],
                    (array, index) -> {
                        computations.incrementAndGet();
                        return computed;
                    });
            String[] array = { null, "preexisting" };
            assertSame(computed, updater.get(array, 0));
            assertSame(computed, updater.get(array, 0));
            assertEquals("preexisting", updater.get(array, 1));
            assertSame(computed, array[0]);
            assertEquals(1, computations.get());

            AtomicInteger attempts = new AtomicInteger();
            StringArrayElement retry = LazyUpdaters.ofLocation(mode, LOOKUP,
                    StringArrayElement.class, (values, index) -> values[index],
                    (values, index) -> attempts.incrementAndGet() == 1 ? null : computed);
            String[] empty = new String[1];
            assertThrows(IllegalArgumentException.class, () -> retry.get(empty, 0));
            assertNull(empty[0]);
            assertSame(computed, retry.get(empty, 0));
            assertSame(computed, retry.get(empty, 0));
            assertEquals(2, attempts.get());
        }
    }

    @Test
    public void serializedArrayCoordinatesUseTheFinalArrayAndIndex() throws Exception {
        int[] row = new int[2];
        int[][] first = { row };
        int[][] alias = { row };
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch aliasStarted = new CountDownLatch(1);
        AtomicInteger firstComputations = new AtomicInteger();
        AtomicInteger aliasComputations = new AtomicInteger();
        IntArray2DElement updater = LazyUpdaters.ofLocation(LazyUpdaters.Mode.SERIALIZED,
                LOOKUP, IntArray2DElement.class,
                (array, i0, i1) -> array[i0][i1], (array, i0, i1) -> {
                    firstComputations.incrementAndGet();
                    if (i1 == 0) {
                        entered.countDown();
                        try {
                            assertTrue(release.await(20, SECONDS), "computation was not released");
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(e);
                        }
                    }
                    return 42 + i1;
                });
        IntArray2DElement aliasedUpdater = LazyUpdaters.ofLocation(LazyUpdaters.Mode.SERIALIZED,
                LOOKUP, IntArray2DElement.class,
                (array, i0, i1) -> array[i0][i1], (array, i0, i1) -> {
                    aliasComputations.incrementAndGet();
                    return 99;
                });

        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<Integer> computing = executor.submit(() -> updater.get(first, 0, 0));
            assertTrue(entered.await(20, SECONDS));
            Future<Integer> independent = executor.submit(() -> updater.get(first, 0, 1));
            assertEquals(43, independent.get(20, SECONDS).intValue(),
                    "different array elements must compute independently");

            Future<Integer> sameElement = executor.submit(() -> {
                aliasStarted.countDown();
                return aliasedUpdater.get(alias, 0, 0);
            });
            assertTrue(aliasStarted.await(20, SECONDS));
            assertThrows(TimeoutException.class, () -> sameElement.get(250, MILLISECONDS));
            assertEquals(0, aliasComputations.get());

            release.countDown();
            assertEquals(42, computing.get(20, SECONDS).intValue());
            assertEquals(42, sameElement.get(20, SECONDS).intValue());
            assertEquals(42, row[0]);
            assertEquals(43, row[1]);
            assertEquals(2, firstComputations.get());
            assertEquals(0, aliasComputations.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(20, SECONDS));
        }
    }

    @Test
    public void inheritedAndVolatileFields() {
        Holder holder = new Holder();
        CountField inherited = LazyUpdaters.ofLocation(LOOKUP, CountField.class,
                h -> h.inherited, h -> 12);
        CountField published = LazyUpdaters.ofLocation(LOOKUP, CountField.class,
                h -> h.published, h -> 34);
        assertEquals(12, inherited.get(holder));
        assertEquals(12, holder.inherited);
        assertEquals(34, published.get(holder));
        assertEquals(34, holder.published);
    }

    @Test
    public void primitiveStaticFields() {
        BooleanSupplier flag = LazyUpdaters.ofLocation(LOOKUP, BooleanSupplier.class,
                () -> StaticHolder.flag, () -> true);
        ByteSupplier octet = LazyUpdaters.ofLocation(LOOKUP, ByteSupplier.class,
                () -> StaticHolder.octet, () -> (byte) 12);
        ShortSupplier small = LazyUpdaters.ofLocation(LOOKUP, ShortSupplier.class,
                () -> StaticHolder.small, () -> (short) 123);
        CharSupplier character = LazyUpdaters.ofLocation(LOOKUP, CharSupplier.class,
                () -> StaticHolder.character, () -> 'x');
        IntSupplier count = LazyUpdaters.ofLocation(LOOKUP, IntSupplier.class,
                () -> StaticHolder.count, () -> 1234);
        LongSupplier large = LazyUpdaters.ofLocation(LOOKUP, LongSupplier.class,
                () -> StaticHolder.large, () -> 12345L);
        FloatSupplier fraction = LazyUpdaters.ofLocation(LOOKUP, FloatSupplier.class,
                () -> StaticHolder.fraction, () -> 1.25f);
        DoubleSupplier radius = LazyUpdaters.ofLocation(LOOKUP, DoubleSupplier.class,
                () -> StaticHolder.radius, () -> 2.5);
        for (int i = 0; i < 2; i++) {
            assertTrue(flag.get());
            assertEquals((byte) 12, octet.get());
            assertEquals((short) 123, small.get());
            assertEquals('x', character.get());
            assertEquals(1234, count.getAsInt());
            assertEquals(12345L, large.getAsLong());
            assertEquals(1.25f, fraction.get());
            assertEquals(2.5, radius.getAsDouble());
        }
        assertEquals(1234, StaticHolder.count);
        assertEquals(2.5, StaticHolder.radius);
    }

    @Test
    public void referenceStaticFields() {
        AtomicInteger computations = new AtomicInteger();
        String result = new String("static");
        Supplier<String> updater = LazyUpdaters.ofLocation(LOOKUP,
                OfLocationTest.<String>supplierType(), () -> StaticHolder.text, () -> {
                    computations.incrementAndGet();
                    return result;
                });
        assertEquals(0, computations.get());
        assertSame(result, updater.get());
        assertSame(result, updater.get());
        assertSame(result, StaticHolder.text);
        assertEquals(1, computations.get());

        Supplier<Integer> boxed = LazyUpdaters.ofLocation(LOOKUP,
                OfLocationTest.<Integer>supplierType(), () -> StaticHolder.boxed, () -> 0);
        assertEquals(0, boxed.get());
        assertEquals(0, boxed.get());
    }

    @Test
    public void staticInitializationIsDeferred() {
        assertFalse(deferredHolderInitialized);
        AtomicInteger computations = new AtomicInteger();
        IntSupplier updater = LazyUpdaters.ofLocation(LOOKUP, IntSupplier.class,
                () -> DeferredHolder.count, () -> {
                    computations.incrementAndGet();
                    return 42;
                });
        assertFalse(deferredHolderInitialized);
        assertEquals(0, computations.get());
        assertEquals(19, updater.getAsInt());
        assertTrue(deferredHolderInitialized);
        assertEquals(0, computations.get());
    }

    @Test
    public void defaultResultsAreRejectedAndRetried() {
        checkDefault(h -> h.flag, false);
        checkDefault(h -> h.octet, (byte) 0);
        checkDefault(h -> h.small, (short) 0);
        checkDefault(h -> h.character, '\0');
        checkDefault(h -> h.count, 0);
        checkDefault(h -> h.large, 0L);
        checkDefault(h -> h.fraction, 0.0f);
        checkDefault(h -> h.radius, 0.0);
        checkDefault(h -> h.text, null);

        AtomicInteger computations = new AtomicInteger();
        CountField updater = LazyUpdaters.ofLocation(LOOKUP, CountField.class,
                h -> h.count, h -> computations.getAndIncrement() == 0 ? 0 : 7);
        Holder holder = new Holder();
        assertThrows(IllegalArgumentException.class, () -> updater.get(holder));
        assertEquals(0, holder.count);
        assertEquals(7, updater.get(holder));
        assertEquals(7, updater.get(holder));
        assertEquals(2, computations.get());
    }

    private static <R> void checkDefault(Function<Holder, R> selector, R result) {
        AtomicInteger computations = new AtomicInteger();
        Function<Holder, R> updater = LazyUpdaters.ofLocation(LOOKUP, functionType(),
                selector, h -> {
                    computations.incrementAndGet();
                    return result;
                });
        Holder holder = new Holder();
        assertThrows(IllegalArgumentException.class, () -> updater.apply(holder));
        assertThrows(IllegalArgumentException.class, () -> updater.apply(holder));
        assertEquals(result, selector.apply(holder));
        assertEquals(2, computations.get());
    }

    @Test
    public void negativeZeroAndNanAreCacheable() {
        checkFloat(-0.0f);
        checkFloat(Float.intBitsToFloat(0x7fc01234));
        checkDouble(-0.0);
        checkDouble(Double.longBitsToDouble(0x7ff8000000001234L));
    }

    private static void checkFloat(float value) {
        AtomicInteger computations = new AtomicInteger();
        FloatField updater = LazyUpdaters.ofLocation(LOOKUP, FloatField.class,
                h -> h.fraction, h -> {
                    computations.incrementAndGet();
                    return value;
                });
        Holder holder = new Holder();
        assertEquals(Float.floatToRawIntBits(value),
                Float.floatToRawIntBits(updater.get(holder)));
        assertEquals(Float.floatToRawIntBits(value),
                Float.floatToRawIntBits(updater.get(holder)));
        assertEquals(Float.floatToRawIntBits(value),
                Float.floatToRawIntBits(holder.fraction));
        assertEquals(1, computations.get());
    }

    private static void checkDouble(double value) {
        AtomicInteger computations = new AtomicInteger();
        DoubleField updater = LazyUpdaters.ofLocation(LOOKUP, DoubleField.class,
                h -> h.radius, h -> {
                    computations.incrementAndGet();
                    return value;
                });
        Holder holder = new Holder();
        assertEquals(Double.doubleToRawLongBits(value),
                Double.doubleToRawLongBits(updater.get(holder)));
        assertEquals(Double.doubleToRawLongBits(value),
                Double.doubleToRawLongBits(updater.get(holder)));
        assertEquals(Double.doubleToRawLongBits(value),
                Double.doubleToRawLongBits(holder.radius));
        assertEquals(1, computations.get());
    }

    @Test
    public void exceptionsPropagateAndCanBeRetried() {
        AtomicInteger computations = new AtomicInteger();
        RuntimeException exception = new IllegalStateException("first attempt");
        Error error = new AssertionError("second attempt");
        CountField updater = LazyUpdaters.ofLocation(LazyUpdaters.Mode.PUBLISH_ONCE, LOOKUP, CountField.class,
                h -> h.count, h -> {
                    return switch (computations.getAndIncrement()) {
                        case 0 -> throw exception;
                        case 1 -> throw error;
                        default -> 42;
                    };
                });
        Holder holder = new Holder();
        assertSame(exception,
                assertThrows(RuntimeException.class, () -> updater.get(holder)));
        assertSame(error, assertThrows(Error.class, () -> updater.get(holder)));
        assertEquals(0, holder.count);
        assertEquals(42, updater.get(holder));
        assertEquals(42, updater.get(holder));
        assertEquals(3, computations.get());
    }

    @Test
    public void preexistingValuesAndCompatibleUpdaters() {
        AtomicInteger computations = new AtomicInteger();
        CountField first = LazyUpdaters.ofLocation(LOOKUP, CountField.class,
                h -> h.count, h -> computations.incrementAndGet());
        CountField second = LazyUpdaters.ofLocation(LOOKUP, CountField.class,
                h -> h.count, h -> computations.incrementAndGet());
        Holder preinitialized = new Holder();
        preinitialized.count = 37;
        assertEquals(37, first.get(preinitialized));
        assertEquals(37, second.get(preinitialized));
        assertEquals(0, computations.get());

        Holder empty = new Holder();
        assertEquals(1, first.get(empty));
        assertEquals(1, second.get(empty));
        assertEquals(1, computations.get());
    }

    @Test
    public void concurrentComputationsPublishOneValue() throws Exception {
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger computations = new AtomicInteger();
        Function<Holder, Payload> compute = h -> {
            computations.incrementAndGet();
            entered.countDown();
            try {
                assertTrue(release.await(20, SECONDS), "computation was not released");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
            // Populate after the coordination so the latch does not publish
            // the payload's state to the other accessing thread.
            Payload payload = new Payload();
            payload.value = 42;
            payload.marker = new Object();
            return payload;
        };
        Function<Holder, Payload> first = LazyUpdaters.ofLocation(LazyUpdaters.Mode.PUBLISH_ONCE, LOOKUP,
                OfLocationTest.<Holder, Payload>functionType(), h -> h.payload, compute);
        Function<Holder, Payload> second = LazyUpdaters.ofLocation(LOOKUP,
                OfLocationTest.<Holder, Payload>functionType(), h -> h.payload, compute);
        Holder holder = new Holder();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Payload> firstResult = executor.submit(() -> checkedRead(first, holder));
            Future<Payload> secondResult = executor.submit(() -> checkedRead(second, holder));
            assertTrue(entered.await(20, SECONDS), "both computations must race");
            release.countDown();
            Payload winner = firstResult.get(20, SECONDS);
            assertSame(winner, secondResult.get(20, SECONDS));
            assertSame(winner, holder.payload);
            assertSame(winner, first.apply(holder));
            assertSame(winner, second.apply(holder));
            assertEquals(2, computations.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(20, SECONDS));
        }
    }

    private static Payload checkedRead(Function<Holder, Payload> updater, Holder holder) {
        Payload result = updater.apply(holder);
        // Check on the accessing thread, before Future.get adds synchronization.
        assertEquals(42, result.value);
        assertNotNull(result.marker);
        return result;
    }

    @Test
    public void synchronizedCoordinatesAcrossUpdaterInstances() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger firstComputations = new AtomicInteger();
        AtomicInteger secondComputations = new AtomicInteger();
        CountField first = LazyUpdaters.ofLocation(LazyUpdaters.Mode.SERIALIZED, LOOKUP,
                CountField.class, h -> h.synchronizedCount, h -> {
                    firstComputations.incrementAndGet();
                    entered.countDown();
                    try {
                        assertTrue(release.await(20, SECONDS), "computation was not released");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                    return 42;
                });
        CountField second = LazyUpdaters.ofLocation(LazyUpdaters.Mode.SERIALIZED, LOOKUP,
                CountField.class, h -> h.synchronizedCount, h -> {
                    secondComputations.incrementAndGet();
                    return 99;
                });
        Holder holder = new Holder();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> firstResult = executor.submit(() -> first.get(holder));
            assertTrue(entered.await(20, SECONDS));
            Future<Integer> secondResult = executor.submit(() -> {
                secondStarted.countDown();
                return second.get(holder);
            });
            assertTrue(secondStarted.await(20, SECONDS));
            assertThrows(TimeoutException.class, () -> secondResult.get(250, MILLISECONDS));
            assertEquals(0, secondComputations.get());
            release.countDown();
            assertEquals(42, firstResult.get(20, SECONDS).intValue());
            assertEquals(42, secondResult.get(20, SECONDS).intValue());
            assertEquals(42, holder.synchronizedCount);
            assertEquals(1, firstComputations.get());
            assertEquals(0, secondComputations.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(20, SECONDS));
        }
    }

    @Test
    public void synchronizedFailuresCanBeRetriedAcrossUpdaterInstances() {
        AtomicInteger firstComputations = new AtomicInteger();
        AtomicInteger secondComputations = new AtomicInteger();
        IllegalStateException exception = new IllegalStateException("first computation");
        CountField first = LazyUpdaters.ofLocation(LazyUpdaters.Mode.SERIALIZED, LOOKUP,
                CountField.class, h -> h.synchronizedCount, h -> {
                    firstComputations.incrementAndGet();
                    throw exception;
                });
        CountField second = LazyUpdaters.ofLocation(LazyUpdaters.Mode.SERIALIZED, LOOKUP,
                CountField.class, h -> h.synchronizedCount, h -> {
                    secondComputations.incrementAndGet();
                    return 99;
                });
        Holder holder = new Holder();
        assertSame(exception, assertThrows(IllegalStateException.class,
                () -> first.get(holder)));
        assertEquals(0, holder.synchronizedCount);
        assertEquals(99, second.get(holder));
        assertEquals(99, first.get(holder));
        assertEquals(99, holder.synchronizedCount);
        assertEquals(1, firstComputations.get());
        assertEquals(1, secondComputations.get());

        Error error = new AssertionError("first computation");
        CountField errorFirst = LazyUpdaters.ofLocation(LazyUpdaters.Mode.SERIALIZED, LOOKUP,
                CountField.class, h -> h.synchronizedCount, h -> { throw error; });
        CountField errorSecond = LazyUpdaters.ofLocation(LazyUpdaters.Mode.SERIALIZED, LOOKUP,
                CountField.class, h -> h.synchronizedCount, h -> 101);
        Holder other = new Holder();
        assertSame(error, assertThrows(Error.class, () -> errorFirst.get(other)));
        assertEquals(0, other.synchronizedCount);
        assertEquals(101, errorSecond.get(other));
        assertEquals(101, other.synchronizedCount);
    }

    @Test
    public void synchronizedStaticFieldIsSharedAcrossUpdaterInstances() {
        AtomicInteger computations = new AtomicInteger();
        IntSupplier first = LazyUpdaters.ofLocation(LazyUpdaters.Mode.SERIALIZED, LOOKUP,
                IntSupplier.class, () -> SynchronizedStaticHolder.count,
                () -> computations.incrementAndGet());
        IntSupplier second = LazyUpdaters.ofLocation(LazyUpdaters.Mode.SERIALIZED, LOOKUP,
                IntSupplier.class, () -> SynchronizedStaticHolder.count, () -> 99);
        assertEquals(1, first.getAsInt());
        assertEquals(1, second.getAsInt());
        assertEquals(1, SynchronizedStaticHolder.count);
        assertEquals(1, computations.get());
    }

    @Test
    public void synchronizedDefaultResultCanBeRetried() {
        AtomicInteger firstComputations = new AtomicInteger();
        AtomicInteger secondComputations = new AtomicInteger();
        CountField first = LazyUpdaters.ofLocation(LazyUpdaters.Mode.SERIALIZED, LOOKUP,
                CountField.class, h -> h.synchronizedCount, h -> {
                    firstComputations.incrementAndGet();
                    return 0;
                });
        CountField second = LazyUpdaters.ofLocation(LazyUpdaters.Mode.SERIALIZED, LOOKUP,
                CountField.class, h -> h.synchronizedCount, h -> {
                    secondComputations.incrementAndGet();
                    return 99;
                });
        Holder holder = new Holder();
        assertThrows(IllegalArgumentException.class, () -> first.get(holder));
        assertEquals(0, holder.synchronizedCount);
        assertEquals(99, second.get(holder));
        assertEquals(99, holder.synchronizedCount);
        assertEquals(1, firstComputations.get());
        assertEquals(1, secondComputations.get());
    }

    @Test
    public void synchronizedStateUsesReceiverIdentity() throws Exception {
        EqualHolder firstHolder = new EqualHolder();
        EqualHolder secondHolder = new EqualHolder();
        assertFalse(firstHolder == secondHolder);
        assertTrue(firstHolder.equals(secondHolder));
        assertEquals(firstHolder.hashCode(), secondHolder.hashCode());
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger computations = new AtomicInteger();
        CountField updater = LazyUpdaters.ofLocation(LazyUpdaters.Mode.SERIALIZED, LOOKUP,
                CountField.class, h -> h.synchronizedCount, h -> {
                    computations.incrementAndGet();
                    entered.countDown();
                    try {
                        assertTrue(release.await(20, SECONDS), "computation was not released");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                    return h == firstHolder ? 41 : 42;
                });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> firstResult = executor.submit(() -> updater.get(firstHolder));
            Future<Integer> secondResult = executor.submit(() -> updater.get(secondHolder));
            assertTrue(entered.await(20, SECONDS), "equal receivers must compute independently");
            release.countDown();
            assertEquals(41, firstResult.get(20, SECONDS).intValue());
            assertEquals(42, secondResult.get(20, SECONDS).intValue());
            assertEquals(41, ((Holder) firstHolder).synchronizedCount);
            assertEquals(42, ((Holder) secondHolder).synchronizedCount);
            assertEquals(2, computations.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(20, SECONDS));
        }
    }

    @Test
    public void synchronizedWaiterRetriesAfterFailure() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch waiterStarted = new CountDownLatch(1);
        AtomicInteger firstComputations = new AtomicInteger();
        AtomicInteger secondComputations = new AtomicInteger();
        int[] written = new int[1];
        IllegalStateException failure = new IllegalStateException("first computation");
        CountField first = LazyUpdaters.ofLocation(LazyUpdaters.Mode.SERIALIZED, LOOKUP,
                CountField.class, h -> h.synchronizedCount, h -> {
                    firstComputations.incrementAndGet();
                    entered.countDown();
                    try {
                        assertTrue(release.await(20, SECONDS), "computation was not released");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                    written[0] = 42;
                    throw failure;
                });
        CountField second = LazyUpdaters.ofLocation(LazyUpdaters.Mode.SERIALIZED, LOOKUP,
                CountField.class, h -> h.synchronizedCount, h -> {
                    secondComputations.incrementAndGet();
                    return 99;
                });
        Holder holder = new Holder();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<IllegalStateException> computing = executor.submit(() ->
                    assertThrows(IllegalStateException.class, () -> first.get(holder)));
            assertTrue(entered.await(20, SECONDS));
            Future<Integer> waiting = executor.submit(() -> {
                waiterStarted.countDown();
                int result = second.get(holder);
                assertEquals(42, written[0]);
                return result;
            });
            assertTrue(waiterStarted.await(20, SECONDS));
            assertThrows(TimeoutException.class, () -> waiting.get(250, MILLISECONDS));
            assertEquals(0, secondComputations.get());
            release.countDown();
            assertSame(failure, computing.get(20, SECONDS));
            assertEquals(99, waiting.get(20, SECONDS).intValue());
            assertEquals(99, holder.synchronizedCount);
            assertEquals(1, firstComputations.get());
            assertEquals(1, secondComputations.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(20, SECONDS));
        }
    }

    @Test
    public void synchronizedFactoryDefersStaticInitialization() {
        assertFalse(deferredSynchronizedHolderInitialized);
        IntSupplier updater = LazyUpdaters.ofLocation(LazyUpdaters.Mode.SERIALIZED, LOOKUP,
                IntSupplier.class, () -> DeferredSynchronizedHolder.count, () -> 42);
        assertFalse(deferredSynchronizedHolderInitialized);
        assertEquals(19, updater.getAsInt());
        assertTrue(deferredSynchronizedHolderInitialized);
    }

    @Test
    public void plainAndSynchronizedWritesSupportReferencesAndLongs() {
        checkDirectWrites(LazyUpdaters.Mode.SERIALIZED, h -> h.synchronizedText, h -> h.synchronizedLarge);
        checkDirectWrites(LazyUpdaters.Mode.PLAIN, h -> h.plainText, h -> h.plainLarge);

        LongSupplier staticWide = LazyUpdaters.ofLocation(LazyUpdaters.Mode.PLAIN, LOOKUP,
                LongSupplier.class, () -> PlainStaticHolder.large, () -> 0x2_0000_0001L);
        assertEquals(0x2_0000_0001L, staticWide.getAsLong());
        assertEquals(0x2_0000_0001L, staticWide.getAsLong());
        assertEquals(0x2_0000_0001L, PlainStaticHolder.large);
    }

    private static void checkDirectWrites(LazyUpdaters.Mode mode,
                                           Function<Holder, String> stringSelector,
                                           LongField longSelector) {
        Holder holder = new Holder();
        String value = new String("computed");
        Function<Holder, String> reference = LazyUpdaters.ofLocation(mode, LOOKUP,
                OfLocationTest.<Holder, String>functionType(), stringSelector, h -> value);
        LongField wide = LazyUpdaters.ofLocation(mode, LOOKUP,
                LongField.class, longSelector, h -> 0x1_0000_0001L);
        assertSame(value, reference.apply(holder));
        assertSame(value, reference.apply(holder));
        assertSame(value, stringSelector.apply(holder));
        assertEquals(0x1_0000_0001L, wide.get(holder));
        assertEquals(0x1_0000_0001L, wide.get(holder));
        assertEquals(0x1_0000_0001L, longSelector.get(holder));
    }

    @Test
    public void synchronizedDetectsRecursiveAccess() {
        AtomicReference<CountField> reference = new AtomicReference<>();
        AtomicInteger computations = new AtomicInteger();
        CountField updater = LazyUpdaters.ofLocation(LazyUpdaters.Mode.SERIALIZED, LOOKUP,
                CountField.class, h -> h.synchronizedCount, h -> {
                    computations.incrementAndGet();
                    return reference.get().get(h);
                });
        reference.set(updater);
        Holder holder = new Holder();
        assertThrows(IllegalStateException.class, () -> updater.get(holder));
        assertThrows(IllegalStateException.class, () -> updater.get(holder));
        assertEquals(0, holder.synchronizedCount);
        assertEquals(2, computations.get());
    }

    @Test
    public void plainComputationsReturnTheirOwnResults() throws Exception {
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger computations = new AtomicInteger();
        CountField updater = LazyUpdaters.ofLocation(LazyUpdaters.Mode.PLAIN, LOOKUP,
                CountField.class, h -> h.plainCount, h -> {
                    int value = computations.incrementAndGet();
                    entered.countDown();
                    try {
                        assertTrue(release.await(20, SECONDS), "computation was not released");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                    return value;
                });

        Holder preinitialized = new Holder();
        preinitialized.plainCount = 37;
        assertEquals(37, updater.get(preinitialized));
        assertEquals(0, computations.get());

        Holder holder = new Holder();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> updater.get(holder));
            Future<Integer> second = executor.submit(() -> updater.get(holder));
            assertTrue(entered.await(20, SECONDS), "both computations must run");
            release.countDown();
            int firstValue = first.get(20, SECONDS);
            int secondValue = second.get(20, SECONDS);
            assertEquals(3, firstValue + secondValue);
            assertTrue(firstValue != secondValue);
            assertTrue(holder.plainCount == 1 || holder.plainCount == 2);
            assertEquals(holder.plainCount, updater.get(holder));
            assertEquals(2, computations.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(20, SECONDS));
        }
    }

    @Test
    public void plainFailuresPropagateAndCanBeRetried() {
        AtomicInteger computations = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("first computation");
        CountField updater = LazyUpdaters.ofLocation(LazyUpdaters.Mode.PLAIN, LOOKUP,
                CountField.class, h -> h.plainCount, h -> {
                    if (computations.incrementAndGet() == 1) {
                        throw failure;
                    }
                    return 42;
                });
        Holder holder = new Holder();
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> updater.get(holder)));
        assertEquals(0, holder.plainCount);
        assertEquals(42, updater.get(holder));
        assertEquals(42, updater.get(holder));
        assertEquals(2, computations.get());
    }

    @Test
    public void nullArgumentsAndReceiver() {
        CountField selector = h -> h.count;
        CountField compute = h -> 42;
        assertThrows(NullPointerException.class,
                () -> LazyUpdaters.ofLocation(null, LOOKUP, CountField.class, selector, compute));
        assertThrows(NullPointerException.class,
                () -> LazyUpdaters.ofLocation(null, CountField.class, selector, compute));
        assertThrows(NullPointerException.class,
                () -> LazyUpdaters.ofLocation(LOOKUP, null, selector, compute));
        assertThrows(NullPointerException.class,
                () -> LazyUpdaters.ofLocation(LOOKUP, CountField.class, null, compute));
        assertThrows(NullPointerException.class,
                () -> LazyUpdaters.ofLocation(LOOKUP, CountField.class, selector, null));

        AtomicInteger computations = new AtomicInteger();
        CountField updater = LazyUpdaters.ofLocation(LOOKUP, CountField.class,
                selector, h -> computations.incrementAndGet());
        assertThrows(NullPointerException.class, () -> updater.get(null));
        assertEquals(0, computations.get());
    }

    @Test
    public void invalidFactories() {
        AtomicInteger computations = new AtomicInteger();
        CountField compute = h -> computations.incrementAndGet();
        assertThrows(IllegalArgumentException.class,
                () -> LazyUpdaters.ofLocation(LOOKUP, CountField.class,
                        h -> h.count + 1, compute));
        assertThrows(IllegalArgumentException.class,
                () -> LazyUpdaters.ofLocation(LOOKUP, CountField.class,
                        Holder::count, compute));
        Holder captured = new Holder();
        assertThrows(IllegalArgumentException.class,
                () -> LazyUpdaters.ofLocation(LOOKUP, IntSupplier.class,
                        () -> captured.count, () -> 42));
        assertThrows(IllegalArgumentException.class,
                () -> LazyUpdaters.ofLocation(LOOKUP, TwoArguments.class,
                        (first, second) -> first.count, (first, second) -> 42));
        assertThrows(IllegalArgumentException.class,
                () -> LazyUpdaters.ofLocation(LOOKUP, IntArray2DElement.class,
                        (array, i0, i1) -> array[i0][i0],
                        (array, i0, i1) -> 42));
        assertThrows(IllegalArgumentException.class,
                () -> LazyUpdaters.ofLocation(LOOKUP, ParameterBridgeField.class,
                        h -> h.text, h -> "unsupported parameter bridge"));
        assertThrows(IllegalArgumentException.class,
                () -> LazyUpdaters.ofLocation(LOOKUP,
                        OfLocationTest.<FinalHolder, Object>functionType(),
                        h -> h.value, h -> new Object()));
        IllegalArgumentException denied = assertThrows(IllegalArgumentException.class,
                () -> LazyUpdaters.ofLocation(MethodHandles.publicLookup(), CountField.class,
                        h -> h.count, compute));
        assertInstanceOf(IllegalAccessException.class, denied.getCause());
        assertEquals(0, computations.get());
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void rawTypesAreValidatedEagerly() {
        CountField selector = h -> h.count;
        CountField compute = h -> 42;
        Class type = CountField.class;
        assertThrows(IllegalArgumentException.class,
                () -> LazyUpdaters.ofLocation(LOOKUP, type, "not an accessor", compute));
        assertThrows(IllegalArgumentException.class,
                () -> LazyUpdaters.ofLocation(LOOKUP, type, selector, "not a function"));
        assertThrows(IllegalArgumentException.class,
                () -> LazyUpdaters.ofLocation(LOOKUP, (Class) Object.class, selector, compute));
    }
}
