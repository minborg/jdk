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
 * @build LazyUpdaterLoaderTarget
 * @build UpdaterTestAdapter
 * @run junit OfLocationTest
 * @run junit/othervm -Xint OfLocationTest
 */

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.util.Lazy;
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

    private static <T> T ofLocationUpdater(Lazy.UpdaterMode mode,
                                           Lookup lookup, Class<T> type,
                                           String selector, T computingFunction) {
        return UpdaterTestAdapter.locationBuilder(mode, lookup, type,
                selector, Holder.class).build(computingFunction);
    }

    private static <T> T ofArrayUpdater(Lazy.UpdaterMode mode,
                                        Class<T> type, T computingFunction) {
        return Lazy.ofUpdater(type).withMode(mode).build(computingFunction);
    }

    private static boolean deferredHolderInitialized;
    private static boolean deferredSynchronizedHolderInitialized;
    private static String privateStaticText;

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
    private interface HolderFunction<R> { R apply(Holder holder); }
    private interface BoxedField { Object apply(Holder holder); }
    private interface IntArrayField { int[] apply(Holder holder); }
    private interface DoubleBoxField { Double apply(Holder holder); }
    private interface PayloadField { Payload apply(Holder holder); }
    private interface StringValueField { String apply(Holder holder); }
    private interface StaticStringSupplier { String get(); }
    private interface StaticIntegerSupplier { Integer get(); }
    private interface HolderIntFunction { int applyAsInt(Holder holder); }
    private interface InheritedStringField extends HolderFunction<String> { }
    private interface GenericField<C, R> { R get(C holder); }
    private interface ConcreteStringField { String get(Holder holder); }
    @FunctionalInterface
    private interface ParameterBridgeField extends GenericField<Holder, String>, ConcreteStringField { }
    private interface TwoArguments { int get(Holder first, Holder second); }
    private interface FinalHolderFunction { Object apply(FinalHolder holder); }
    private interface IntArrayElement { int get(int[] array, int index); }
    private interface IntArrayRow { int[] get(int[][] array, int row); }
    private interface IntArray2DElement { int get(int[][] array, int row, int column); }
    private interface IntArray3DElement { int get(int[][][] array, int i0, int i1, int i2); }
    private interface IntArray4DElement { int get(int[][][][] array, int i0, int i1, int i2, int i3); }
    private interface IntArray4DRow { int[] get(int[][][][] array, int i0, int i1, int i2); }
    private interface IntArray3DRow { int[] get(int[][][] array, int i0, int i1); }
    private interface TooManyArrayIndices { int get(int[][] array, int i0, int i1, int i2); }
    private interface PrivateElementArrayElement { PrivateElement get(PrivateElement[] array, int index); }
    private interface OwnedIntArrayElement { int get(ArrayOwner owner, int index); }
    private interface OwnedIntArray2DElement { int get(ArrayOwner owner, int row, int column); }
    private interface OwnedIntArray3DElement { int get(ArrayOwner owner, int i0, int i1, int i2); }
    private interface TooManyOwnedArrayIndices { int get(ArrayOwner owner, int i0, int i1, int i2); }
    private interface StringArrayElement { String get(String[] array, int index); }

    private static final class PrivateElement {
        final int value;

        PrivateElement(int value) {
            this.value = value;
        }
    }

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
        private final int[][][] volume = { { new int[2], new int[2] }, { new int[2] } };
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

    public static class PublicStaticHolder {
        public static String text;
        public static String initialized = "already initialized";
        private static String hidden;
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

    @Test
    public void primitiveInstanceFields() {
        Holder holder = new Holder();
        BooleanField flag = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE,
                LOOKUP, BooleanField.class, "flag", h -> true);
        ByteField octet = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE,
                LOOKUP, ByteField.class, "octet", h -> (byte) 12);
        ShortField small = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE,
                LOOKUP, ShortField.class, "small", h -> (short) 123);
        CharField character = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE,
                LOOKUP, CharField.class, "character", h -> 'x');
        CountField count = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE,
                LOOKUP, CountField.class, "count", h -> 1234);
        LongField large = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE,
                LOOKUP, LongField.class, "large", h -> 12345L);
        FloatField fraction = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE,
                LOOKUP, FloatField.class, "fraction", h -> 1.25f);
        DoubleField radius = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE,
                LOOKUP, DoubleField.class, "radius", h -> 2.5);

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
        checkBoxing("flag", h -> h.flag, true);
        checkBoxing("octet", h -> h.octet, (byte) 12);
        checkBoxing("small", h -> h.small, (short) 123);
        checkBoxing("character", h -> h.character, 'x');
        checkBoxing("count", h -> h.count, 1234);
        checkBoxing("large", h -> h.large, 12345L);
        checkBoxing("fraction", h -> h.fraction, 1.25f);
        checkBoxing("radius", h -> h.radius, 2.5);
    }

    private static <R> void checkBoxing(String field, Function<Holder, R> accessor, R result) {
        AtomicInteger computations = new AtomicInteger();
        BoxedField updater = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP, BoxedField.class,
                field, h -> {
                    computations.incrementAndGet();
                    return result;
                });
        Holder holder = new Holder();
        assertEquals(result, updater.apply(holder));
        assertEquals(result, updater.apply(holder));
        assertEquals(result, accessor.apply(holder));
        assertEquals(1, computations.get());
    }

    @Test
    public void referenceFieldsAndInheritedInterfaces() {
        Holder holder = new Holder();
        String result = new String("computed");
        StringField covariant = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE,
                LOOKUP, StringField.class, "text", h -> result);
        ParentStringField parentView = covariant;
        assertSame(result, covariant.get(holder));
        assertSame(result, parentView.get(holder));
        assertSame(result, holder.text);

        InheritedStringField inherited = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                InheritedStringField.class, "text", h -> "inherited");
        assertEquals("inherited", inherited.apply(new Holder()));
        assertSame(result, inherited.apply(holder));

        int[] array = { 1, 2, 3 };
        IntArrayField arrayUpdater = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                IntArrayField.class, "array", h -> array);
        assertSame(array, arrayUpdater.apply(holder));
        assertSame(array, arrayUpdater.apply(holder));

        // A boxed zero is an ordinary non-null value in a reference field.
        DoubleBoxField boxed = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                DoubleBoxField.class, "boxed", h -> 0.0);
        assertEquals(0.0, boxed.apply(holder));
        assertEquals(0.0, holder.boxed);
    }

    @Test
    public void initializationIsLazyAndPerReceiver() {
        AtomicInteger computations = new AtomicInteger();
        HolderIntFunction updater = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                HolderIntFunction.class, "count",
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
    public void defaultModeSupportsDirectArrayElements() {
        AtomicInteger computations = new AtomicInteger();
        IntArrayElement updater = ofArrayUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, IntArrayElement.class,
                (array, index) -> {
                    computations.incrementAndGet();
                    return index + 1;
                });
        int[] array = new int[2];
        assertEquals(2, updater.get(array, 1));
        assertEquals(2, updater.get(array, 1));
        assertEquals(2, array[1]);
        assertEquals(1, computations.get());
    }

    @Test
    public void privateArrayComponentNeedsNoLookup() {
        AtomicInteger computations = new AtomicInteger();
        PrivateElementArrayElement updater = ofArrayUpdater(
                Lazy.UpdaterMode.PUBLISH_ONCE,
                PrivateElementArrayElement.class, (array, index) -> {
                    computations.incrementAndGet();
                    return new PrivateElement(index + 1);
                });
        PrivateElement[] array = new PrivateElement[2];
        PrivateElement value = updater.get(array, 1);
        assertSame(value, updater.get(array, 1));
        assertSame(value, array[1]);
        assertEquals(2, value.value);
        assertEquals(1, computations.get());
    }

    @Test
    public void oneRankArrayElements() {
        for (Lazy.UpdaterMode mode : new Lazy.UpdaterMode[] {
                Lazy.UpdaterMode.PLAIN, Lazy.UpdaterMode.PUBLISH_ONCE,
                Lazy.UpdaterMode.SERIALIZED }) {
            AtomicInteger computations = new AtomicInteger();
            IntArrayElement updater = ofArrayUpdater(mode, IntArrayElement.class,
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
            IntArrayElement retry = ofArrayUpdater(mode, IntArrayElement.class,
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
        for (Lazy.UpdaterMode mode : Lazy.UpdaterMode.values()) {
            AtomicInteger computations = new AtomicInteger();
            IntArray2DElement updater = ofArrayUpdater(mode, IntArray2DElement.class,
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
            IntArray2DElement retry = ofArrayUpdater(mode, IntArray2DElement.class,
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
    public void threeRankArrayElements() {
        for (Lazy.UpdaterMode mode : Lazy.UpdaterMode.values()) {
            AtomicInteger computations = new AtomicInteger();
            IntArray3DElement updater = ofArrayUpdater(mode, IntArray3DElement.class,
                    (array, i0, i1, i2) -> {
                        computations.incrementAndGet();
                        return 1000 + 100 * i0 + 10 * i1 + i2;
                    });
            int[] shared = new int[2];
            int[][][] first = { { { 0, 29 }, shared }, { new int[2] } };
            int[][][] alias = { { shared } };
            assertEquals(0, computations.get());
            assertEquals(1000, updater.get(first, 0, 0, 0));
            assertEquals(1000, updater.get(first, 0, 0, 0));
            assertEquals(29, updater.get(first, 0, 0, 1));
            assertEquals(1010, updater.get(first, 0, 1, 0));
            assertEquals(1010, updater.get(alias, 0, 0, 0));
            assertEquals(1101, updater.get(first, 1, 0, 1));
            assertEquals(3, computations.get());
            assertEquals(1000, first[0][0][0]);
            assertEquals(1010, shared[0]);
            assertEquals(1101, first[1][0][1]);

            assertThrows(ArrayIndexOutOfBoundsException.class,
                    () -> updater.get(first, -1, 0, 0));
            assertThrows(ArrayIndexOutOfBoundsException.class,
                    () -> updater.get(first, 0, 2, 0));
            assertThrows(ArrayIndexOutOfBoundsException.class,
                    () -> updater.get(first, 0, 0, 2));
            assertThrows(NullPointerException.class, () -> updater.get(null, 0, 0, 0));
            int[][][] nullPlane = { null };
            int[][][] nullRow = { { null } };
            assertThrows(NullPointerException.class,
                    () -> updater.get(nullPlane, 0, 0, 0));
            assertThrows(NullPointerException.class,
                    () -> updater.get(nullRow, 0, 0, 0));
            assertEquals(3, computations.get());

            AtomicInteger attempts = new AtomicInteger();
            IntArray3DElement retry = ofArrayUpdater(mode, IntArray3DElement.class,
                    (array, i0, i1, i2) -> attempts.incrementAndGet() == 1 ? 0 : 7);
            int[][][] empty = { { new int[1] } };
            assertThrows(IllegalArgumentException.class, () -> retry.get(empty, 0, 0, 0));
            assertEquals(0, empty[0][0][0]);
            assertEquals(7, retry.get(empty, 0, 0, 0));
            assertEquals(7, retry.get(empty, 0, 0, 0));
            assertEquals(2, attempts.get());
        }
    }

    @Test
    public void fourRankArrayElements() {
        for (Lazy.UpdaterMode mode : Lazy.UpdaterMode.values()) {
            AtomicInteger computations = new AtomicInteger();
            IntArray4DElement updater = ofArrayUpdater(mode, IntArray4DElement.class,
                    (array, i0, i1, i2, i3) -> {
                        computations.incrementAndGet();
                        return 10000 + 1000 * i0 + 100 * i1 + 10 * i2 + i3;
                    });
            int[][][][] array = new int[2][2][2][2];
            array[0][1][0][1] = 77;
            assertEquals(11011, updater.get(array, 1, 0, 1, 1));
            assertEquals(11011, updater.get(array, 1, 0, 1, 1));
            assertEquals(77, updater.get(array, 0, 1, 0, 1));
            assertEquals(10000, updater.get(array, 0, 0, 0, 0));
            assertEquals(2, computations.get());
            assertEquals(11011, array[1][0][1][1]);
            assertEquals(10000, array[0][0][0][0]);
        }
    }

    @Test
    public void threeIndicesCanInitializeRowsOfAFourRankArray() {
        for (Lazy.UpdaterMode mode : Lazy.UpdaterMode.values()) {
            AtomicInteger computations = new AtomicInteger();
            IntArray4DRow updater = ofArrayUpdater(mode, IntArray4DRow.class,
                    (array, i0, i1, i2) -> {
                        computations.incrementAndGet();
                        return new int[] { 1000 + 100 * i0 + 10 * i1 + i2 };
                    });
            int[] preexisting = { 9 };
            int[][][][] array = { { { null, preexisting } }, { { null } } };
            int[] first = updater.get(array, 0, 0, 0);
            int[] second = updater.get(array, 1, 0, 0);
            assertSame(first, updater.get(array, 0, 0, 0));
            assertSame(second, updater.get(array, 1, 0, 0));
            assertSame(first, array[0][0][0]);
            assertSame(second, array[1][0][0]);
            assertSame(preexisting, updater.get(array, 0, 0, 1));
            assertEquals(1000, first[0]);
            assertEquals(1100, second[0]);
            assertEquals(2, computations.get());
        }
    }

    @Test
    public void twoIndicesCanInitializeRowsOfAThreeRankArray() {
        for (Lazy.UpdaterMode mode : Lazy.UpdaterMode.values()) {
            AtomicInteger computations = new AtomicInteger();
            IntArray3DRow updater = ofArrayUpdater(mode, IntArray3DRow.class,
                    (array, i0, i1) -> {
                        computations.incrementAndGet();
                        return new int[] { 100 + 10 * i0 + i1, 200 + 10 * i0 + i1 };
                    });
            int[] preexisting = { 9 };
            int[][][] array = { { null, preexisting }, { null } };
            int[] first = updater.get(array, 0, 0);
            int[] second = updater.get(array, 1, 0);
            assertSame(first, updater.get(array, 0, 0));
            assertSame(second, updater.get(array, 1, 0));
            assertSame(first, array[0][0]);
            assertSame(second, array[1][0]);
            assertSame(preexisting, updater.get(array, 0, 1));
            assertEquals(100, first[0]);
            assertEquals(200, first[1]);
            assertEquals(110, second[0]);
            assertEquals(210, second[1]);
            assertEquals(2, computations.get());
        }
    }

    @Test
    public void oneIndexCanInitializeRowsOfATwoRankArray() {
        for (Lazy.UpdaterMode mode : Lazy.UpdaterMode.values()) {
            AtomicInteger computations = new AtomicInteger();
            IntArrayRow updater = ofArrayUpdater(mode, IntArrayRow.class,
                    (array, row) -> {
                        computations.incrementAndGet();
                        return new int[] { 10 + row, 20 + row };
                    });
            int[] preexisting = { 9 };
            int[][] array = { null, preexisting };
            int[] first = updater.get(array, 0);
            assertSame(first, updater.get(array, 0));
            assertSame(first, array[0]);
            assertEquals(10, first[0]);
            assertEquals(20, first[1]);
            assertSame(preexisting, updater.get(array, 1));
            assertEquals(1, computations.get());
        }
    }

    @Test
    public void arrayElementsInFinalOwnerFields() {
        for (Lazy.UpdaterMode mode : Lazy.UpdaterMode.values()) {
            AtomicInteger rowComputations = new AtomicInteger();
            OwnedIntArrayElement rowUpdater = ofLocationUpdater(mode, LOOKUP,
                    OwnedIntArrayElement.class, "row",
                    (owner, index) -> {
                        rowComputations.incrementAndGet();
                        return 20 + index;
                    });
            AtomicInteger matrixComputations = new AtomicInteger();
            OwnedIntArray2DElement matrixUpdater = ofLocationUpdater(mode, LOOKUP,
                    OwnedIntArray2DElement.class, "matrix",
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

            assertEquals(112, matrixUpdater.get(first, 1, 2));
            assertEquals(112, first.matrix[1][2]);
        }
    }

    @Test
    public void threeRankArrayElementsInFinalOwnerField() {
        for (Lazy.UpdaterMode mode : Lazy.UpdaterMode.values()) {
            AtomicInteger computations = new AtomicInteger();
            OwnedIntArray3DElement updater = ofLocationUpdater(mode, LOOKUP,
                    OwnedIntArray3DElement.class, "volume",
                    (owner, i0, i1, i2) -> {
                        computations.incrementAndGet();
                        return 1000 + 100 * i0 + 10 * i1 + i2;
                    });
            ArrayOwner first = new ArrayOwner();
            ArrayOwner second = new ArrayOwner();
            first.volume[0][1][1] = 57;
            assertEquals(1000, updater.get(first, 0, 0, 0));
            assertEquals(1000, updater.get(first, 0, 0, 0));
            assertEquals(57, updater.get(first, 0, 1, 1));
            assertEquals(1101, updater.get(first, 1, 0, 1));
            assertEquals(1101, updater.get(second, 1, 0, 1));
            assertEquals(3, computations.get());
            assertEquals(1000, first.volume[0][0][0]);
            assertEquals(1101, first.volume[1][0][1]);
            assertEquals(1101, second.volume[1][0][1]);

            assertThrows(NullPointerException.class,
                    () -> updater.get(null, 0, 0, 0));
            assertThrows(ArrayIndexOutOfBoundsException.class,
                    () -> updater.get(first, 0, 2, 0));
            assertEquals(3, computations.get());
        }
    }

    @Test
    public void referenceArrayElementsUseNullAsTheSentinel() {
        for (Lazy.UpdaterMode mode : Lazy.UpdaterMode.values()) {
            AtomicInteger computations = new AtomicInteger();
            String computed = new String("computed");
            StringArrayElement updater = ofArrayUpdater(mode, StringArrayElement.class,
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
            StringArrayElement retry = ofArrayUpdater(mode, StringArrayElement.class,
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
        IntArray2DElement updater = ofArrayUpdater(Lazy.UpdaterMode.SERIALIZED,
                IntArray2DElement.class, (array, i0, i1) -> {
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
        IntArray2DElement aliasedUpdater = ofArrayUpdater(Lazy.UpdaterMode.SERIALIZED,
                IntArray2DElement.class, (array, i0, i1) -> {
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
        CountField inherited = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE,
                LOOKUP, CountField.class, "inherited", h -> 12);
        CountField published = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE,
                LOOKUP, CountField.class, "published", h -> 34);
        assertEquals(12, inherited.get(holder));
        assertEquals(12, holder.inherited);
        assertEquals(34, published.get(holder));
        assertEquals(34, holder.published);
    }

    @Test
    public void primitiveStaticFields() {
        BooleanSupplier flag = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP, BooleanSupplier.class,
                StaticHolder.class.getName() + ".flag", () -> true);
        ByteSupplier octet = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP, ByteSupplier.class,
                StaticHolder.class.getName() + ".octet", () -> (byte) 12);
        ShortSupplier small = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP, ShortSupplier.class,
                StaticHolder.class.getName() + ".small", () -> (short) 123);
        CharSupplier character = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP, CharSupplier.class,
                StaticHolder.class.getName() + ".character", () -> 'x');
        IntSupplier count = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP, IntSupplier.class,
                StaticHolder.class.getName() + ".count", () -> 1234);
        LongSupplier large = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP, LongSupplier.class,
                StaticHolder.class.getName() + ".large", () -> 12345L);
        FloatSupplier fraction = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP, FloatSupplier.class,
                StaticHolder.class.getName() + ".fraction", () -> 1.25f);
        DoubleSupplier radius = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP, DoubleSupplier.class,
                StaticHolder.class.getName() + ".radius", () -> 2.5);
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
        StaticStringSupplier updater = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                StaticStringSupplier.class, StaticHolder.class.getName() + ".text", () -> {
                    computations.incrementAndGet();
                    return result;
                });
        assertEquals(0, computations.get());
        assertSame(result, updater.get());
        assertSame(result, updater.get());
        assertSame(result, StaticHolder.text);
        assertEquals(1, computations.get());

        StaticIntegerSupplier boxed = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                StaticIntegerSupplier.class, StaticHolder.class.getName() + ".boxed", () -> 0);
        assertEquals(0, boxed.get());
        assertEquals(0, boxed.get());
    }

    @Test
    public void convenienceStaticFieldUpdaterComputesAndRetains() {
        PublicStaticHolder.text = null;
        AtomicInteger computations = new AtomicInteger();
        String result = new String("computed");
        Supplier<String> updater = Lazy.ofStaticFieldUpdater(PublicStaticHolder.class, "text", () -> {
            computations.incrementAndGet();
            return result;
        });

        assertNull(PublicStaticHolder.text);
        assertEquals(0, computations.get());
        assertSame(result, updater.get());
        assertSame(result, PublicStaticHolder.text);
        assertSame(result, updater.get());
        assertEquals(1, computations.get());
    }

    @Test
    public void convenienceStaticFieldUpdaterUsesPreinitializedValue() {
        AtomicInteger computations = new AtomicInteger();
        Supplier<String> updater = Lazy.ofStaticFieldUpdater(PublicStaticHolder.class,
                "initialized", () -> {
                    computations.incrementAndGet();
                    return "computed";
                });

        assertEquals("already initialized", updater.get());
        assertEquals(0, computations.get());
    }

    @Test
    public void convenienceStaticFieldUpdaterAccessesPrivateFieldInCallerClass() {
        privateStaticText = null;
        String result = new String("private caller field");
        Supplier<String> updater = Lazy.ofStaticFieldUpdater(OfLocationTest.class,
                "privateStaticText", () -> result);

        assertNull(privateStaticText);
        assertSame(result, updater.get());
        assertSame(result, privateStaticText);
        assertSame(result, updater.get());
    }

    @Test
    public void convenienceStaticFieldUpdaterAccessesPrivateNestmateField() {
        PublicStaticHolder.hidden = null;
        String result = new String("private nestmate field");
        Supplier<String> updater = Lazy.ofStaticFieldUpdater(PublicStaticHolder.class,
                "hidden", () -> result);

        assertNull(PublicStaticHolder.hidden);
        assertSame(result, updater.get());
        assertSame(result, PublicStaticHolder.hidden);
        assertSame(result, updater.get());
    }

    @Test
    public void convenienceStaticFieldUpdaterRejectsUnrelatedPrivateField() {
        IllegalArgumentException denied = assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofStaticFieldUpdater(UnrelatedStaticHolder.class,
                        "hidden", () -> "computed"));
        assertInstanceOf(IllegalAccessException.class, denied.getCause());
    }

    @Test
    public void convenienceStaticFieldUpdaterRequiresSimpleFieldName() {
        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofStaticFieldUpdater(PublicStaticHolder.class,
                        "other.text", () -> "computed"));
    }

    @Test
    public void convenienceStaticFieldUpdaterUsesReflectiveCaller() throws Exception {
        Method method = Lazy.class.getMethod("ofStaticFieldUpdater",
                Class.class, String.class, Supplier.class);
        PublicStaticHolder.hidden = null;
        Supplier<?> updater = (Supplier<?>) method.invoke(null,
                PublicStaticHolder.class, "hidden", (Supplier<String>) () -> "reflected");

        assertEquals("reflected", updater.get());
        assertEquals("reflected", PublicStaticHolder.hidden);
    }

    @Test
    public void convenienceStaticFieldUpdaterUsesMethodHandleCaller() throws Throwable {
        MethodHandle methodHandle = LOOKUP.findStatic(Lazy.class, "ofStaticFieldUpdater",
                MethodType.methodType(Supplier.class, Class.class, String.class, Supplier.class));
        PublicStaticHolder.hidden = null;
        Supplier<?> updater = (Supplier<?>) methodHandle.invokeExact(
                (Class<?>) PublicStaticHolder.class, "hidden", (Supplier<?>) () -> "method handle");

        assertEquals("method handle", updater.get());
        assertEquals("method handle", PublicStaticHolder.hidden);
    }

    @Test
    public void staticInitializationIsDeferred() {
        assertFalse(deferredHolderInitialized);
        AtomicInteger computations = new AtomicInteger();
        IntSupplier updater = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP, IntSupplier.class,
                DeferredHolder.class.getName() + ".count", () -> {
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
    public void qualifiedStaticFieldNameUsesLookupClassLoader() throws Exception {
        String binaryName = LazyUpdaterLoaderTarget.class.getName();
        byte[] bytes;
        try (var resource = OfLocationTest.class.getResourceAsStream(
                "LazyUpdaterLoaderTarget.class")) {
            assertNotNull(resource);
            bytes = resource.readAllBytes();
        }

        Class<?> firstClass = loadTargetInNewLoader(binaryName, bytes);
        Class<?> secondClass = loadTargetInNewLoader(binaryName, bytes);
        assertEquals(firstClass.getName(), secondClass.getName());
        assertFalse(firstClass == secondClass);
        Lookup firstLookup = MethodHandles.privateLookupIn(firstClass, LOOKUP);
        Lookup secondLookup = MethodHandles.privateLookupIn(secondClass, LOOKUP);
        IntSupplier first = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, firstLookup, IntSupplier.class,
                binaryName + ".value", () -> 11);
        IntSupplier second = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, secondLookup, IntSupplier.class,
                binaryName + ".value", () -> 22);

        assertEquals(11, first.getAsInt());
        assertEquals(22, second.getAsInt());
        assertEquals(11, (int) firstLookup.findStaticVarHandle(
                firstClass, "value", int.class).get());
        assertEquals(22, (int) secondLookup.findStaticVarHandle(
                secondClass, "value", int.class).get());
    }

    @Test
    public void convenienceStaticFieldUpdaterUsesOwnerClassLoader() throws Exception {
        String binaryName = LazyUpdaterLoaderTarget.class.getName();
        byte[] bytes;
        try (var resource = OfLocationTest.class.getResourceAsStream(
                "LazyUpdaterLoaderTarget.class")) {
            assertNotNull(resource);
            bytes = resource.readAllBytes();
        }

        Class<?> firstClass = loadTargetInNewLoader(binaryName, bytes);
        Class<?> secondClass = loadTargetInNewLoader(binaryName, bytes);
        assertFalse(firstClass == secondClass);
        Supplier<Integer> first = Lazy.ofStaticFieldUpdater(firstClass, "value", () -> 11);
        Supplier<Integer> second = Lazy.ofStaticFieldUpdater(secondClass, "value", () -> 22);

        assertEquals(11, first.get());
        assertEquals(22, second.get());
        assertEquals(11, firstClass.getField("value").getInt(null));
        assertEquals(22, secondClass.getField("value").getInt(null));
    }

    @Test
    public void convenienceInstanceFieldUpdaterComputesPerReceiver() {
        Holder first = new Holder();
        Holder second = new Holder();
        AtomicInteger computations = new AtomicInteger();
        String firstResult = new String("first");
        String secondResult = new String("second");
        Function<Holder, String> updater = Lazy.ofFieldUpdater(Holder.class,
                "text", holder -> {
                    computations.incrementAndGet();
                    return holder == first ? firstResult : secondResult;
                });

        assertNull(first.text);
        assertEquals(0, computations.get());
        assertSame(firstResult, updater.apply(first));
        assertSame(firstResult, updater.apply(first));
        assertSame(firstResult, first.text);
        assertSame(secondResult, updater.apply(second));
        assertSame(secondResult, updater.apply(second));
        assertSame(secondResult, second.text);
        assertEquals(2, computations.get());
    }

    @Test
    public void convenienceInstanceFieldUpdaterUsesPreexistingAndPrimitiveValues() {
        Holder holder = new Holder();
        String existing = new String("already initialized");
        holder.text = existing;
        AtomicInteger computations = new AtomicInteger();
        Function<Holder, String> text = Lazy.ofFieldUpdater(Holder.class,
                "text", h -> {
                    computations.incrementAndGet();
                    return "unused";
                });
        assertSame(existing, text.apply(holder));
        assertEquals(0, computations.get());

        Function<Holder, Integer> count = Lazy.ofFieldUpdater(Holder.class,
                "count", h -> 42);
        assertEquals(42, count.apply(holder));
        assertEquals(42, count.apply(holder));
        assertEquals(42, holder.count);
    }

    @Test
    public void convenienceInstanceFieldUpdaterRejectsDefaultAndRetries() {
        Holder holder = new Holder();
        AtomicInteger computations = new AtomicInteger();
        Function<Holder, String> updater = Lazy.ofFieldUpdater(Holder.class,
                "text", h -> computations.incrementAndGet() == 1 ? null : "recovered");

        assertThrows(IllegalArgumentException.class, () -> updater.apply(holder));
        assertNull(holder.text);
        assertEquals("recovered", updater.apply(holder));
        assertEquals("recovered", updater.apply(holder));
        assertEquals(2, computations.get());
    }

    @Test
    public void convenienceInstanceFieldUpdaterChecksCallerAccessAndArguments() {
        IllegalArgumentException denied = assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofFieldUpdater(UnrelatedInstanceHolder.class,
                        "hidden", h -> "computed"));
        assertInstanceOf(IllegalAccessException.class, denied.getCause());
        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofFieldUpdater(Holder.class, "other.text", h -> "computed"));
        assertThrows(NullPointerException.class,
                () -> Lazy.ofFieldUpdater(null, "text", (Function<Holder, String>) h -> "computed"));
        assertThrows(NullPointerException.class,
                () -> Lazy.ofFieldUpdater(Holder.class, null, h -> "computed"));
        assertThrows(NullPointerException.class,
                () -> Lazy.ofFieldUpdater(Holder.class, "text", null));
    }

    @Test
    public void convenienceArrayUpdaterComputesPerElementAndArray() {
        AtomicInteger computations = new AtomicInteger();
        Lazy.ReferenceArrayAccessor.Computer<String> updater = Lazy.ofArrayUpdater(String.class,
                (array, index) -> {
                    computations.incrementAndGet();
                    return new String("element " + index);
                });
        String existing = new String("preexisting");
        String[] first = { null, existing };
        String[] second = new String[1];

        assertEquals(0, computations.get());
        assertSame(existing, updater.apply(first, 1));
        assertEquals(0, computations.get());
        String firstResult = updater.apply(first, 0);
        assertSame(firstResult, first[0]);
        assertSame(firstResult, updater.apply(first, 0));
        String secondResult = updater.apply(second, 0);
        assertSame(secondResult, second[0]);
        assertSame(secondResult, updater.apply(second, 0));
        assertEquals(2, computations.get());
    }

    @Test
    public void convenienceArrayUpdaterSupportsPrivateComponents() {
        PrivateElement result = new PrivateElement(7);
        Lazy.ReferenceArrayAccessor.Computer<PrivateElement> updater =
                Lazy.ofArrayUpdater(PrivateElement.class, (array, index) -> result);
        PrivateElement[] array = new PrivateElement[1];

        assertSame(result, updater.apply(array, 0));
        assertSame(result, updater.apply(array, 0));
        assertSame(result, array[0]);
    }

    @Test
    public void convenienceArrayUpdaterCanInitializeAnArrayValuedElement() {
        Lazy.ReferenceArrayAccessor.Computer<int[]> updater = Lazy.ofArrayUpdater(
                int[].class, (array, index) -> new int[] { index + 1 });
        int[][] matrix = new int[2][];

        int[] row = updater.apply(matrix, 1);
        assertSame(row, matrix[1]);
        assertSame(row, updater.apply(matrix, 1));
        assertEquals(2, row[0]);
    }

    @Test
    public void convenienceArrayUpdaterChecksDefaultBoundsAndArguments() {
        AtomicInteger computations = new AtomicInteger();
        Lazy.ReferenceArrayAccessor.Computer<String> updater = Lazy.ofArrayUpdater(String.class,
                (array, index) -> computations.incrementAndGet() == 1 ? null : "recovered");
        String[] array = new String[1];

        assertThrows(NullPointerException.class, () -> updater.apply(null, 0));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> updater.apply(array, 1));
        assertEquals(0, computations.get());
        assertThrows(IllegalArgumentException.class, () -> updater.apply(array, 0));
        assertNull(array[0]);
        assertEquals("recovered", updater.apply(array, 0));
        assertEquals(2, computations.get());

        assertThrows(NullPointerException.class,
                () -> Lazy.ofArrayUpdater(null,
                        (Lazy.ReferenceArrayAccessor.Computer<String>) (a, i) -> "unused"));
        assertThrows(NullPointerException.class,
                () -> Lazy.ofArrayUpdater(String.class, null));
        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofArrayUpdater(int.class, (values, index) -> 1));
        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofArrayUpdater(void.class, (values, index) -> null));
    }

    private static Class<?> loadTargetInNewLoader(String binaryName, byte[] bytes)
            throws ClassNotFoundException {
        ClassLoader loader = new ClassLoader(OfLocationTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve)
                    throws ClassNotFoundException {
                if (!name.equals(binaryName)) {
                    return super.loadClass(name, resolve);
                }
                synchronized (getClassLoadingLock(name)) {
                    Class<?> type = findLoadedClass(name);
                    if (type == null) {
                        type = defineClass(name, bytes, 0, bytes.length);
                    }
                    if (resolve) {
                        resolveClass(type);
                    }
                    return type;
                }
            }
        };
        return loader.loadClass(binaryName);
    }

    @Test
    public void defaultResultsAreRejectedAndRetried() {
        checkDefault("flag", h -> h.flag, false);
        checkDefault("octet", h -> h.octet, (byte) 0);
        checkDefault("small", h -> h.small, (short) 0);
        checkDefault("character", h -> h.character, '\0');
        checkDefault("count", h -> h.count, 0);
        checkDefault("large", h -> h.large, 0L);
        checkDefault("fraction", h -> h.fraction, 0.0f);
        checkDefault("radius", h -> h.radius, 0.0);
        checkDefault("text", h -> h.text, null);

        AtomicInteger computations = new AtomicInteger();
        CountField updater = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP, CountField.class,
                "count", h -> computations.getAndIncrement() == 0 ? 0 : 7);
        Holder holder = new Holder();
        assertThrows(IllegalArgumentException.class, () -> updater.get(holder));
        assertEquals(0, holder.count);
        assertEquals(7, updater.get(holder));
        assertEquals(7, updater.get(holder));
        assertEquals(2, computations.get());
    }

    private static <R> void checkDefault(String field, Function<Holder, R> accessor, R result) {
        AtomicInteger computations = new AtomicInteger();
        BoxedField updater = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP, BoxedField.class,
                field, h -> {
                    computations.incrementAndGet();
                    return result;
                });
        Holder holder = new Holder();
        assertThrows(IllegalArgumentException.class, () -> updater.apply(holder));
        assertThrows(IllegalArgumentException.class, () -> updater.apply(holder));
        assertEquals(result, accessor.apply(holder));
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
        FloatField updater = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE,
                LOOKUP, FloatField.class, "fraction", h -> {
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
        DoubleField updater = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE,
                LOOKUP, DoubleField.class, "radius", h -> {
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
        CountField updater = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE,
                LOOKUP, CountField.class, "count", h -> {
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
        CountField first = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP, CountField.class,
                "count", h -> computations.incrementAndGet());
        CountField second = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP, CountField.class,
                "count", h -> computations.incrementAndGet());
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
        PayloadField compute = h -> {
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
        PayloadField first = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                PayloadField.class, "payload", compute);
        PayloadField second = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                PayloadField.class, "payload", compute);
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

    private static Payload checkedRead(PayloadField updater, Holder holder) {
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
        CountField first = ofLocationUpdater(Lazy.UpdaterMode.SERIALIZED, LOOKUP,
                CountField.class, "synchronizedCount", h -> {
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
        CountField second = ofLocationUpdater(Lazy.UpdaterMode.SERIALIZED, LOOKUP,
                CountField.class, "synchronizedCount", h -> {
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
        CountField first = ofLocationUpdater(Lazy.UpdaterMode.SERIALIZED, LOOKUP,
                CountField.class, "synchronizedCount", h -> {
                    firstComputations.incrementAndGet();
                    throw exception;
                });
        CountField second = ofLocationUpdater(Lazy.UpdaterMode.SERIALIZED, LOOKUP,
                CountField.class, "synchronizedCount", h -> {
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
        CountField errorFirst = ofLocationUpdater(Lazy.UpdaterMode.SERIALIZED, LOOKUP,
                CountField.class, "synchronizedCount", h -> { throw error; });
        CountField errorSecond = ofLocationUpdater(Lazy.UpdaterMode.SERIALIZED, LOOKUP,
                CountField.class, "synchronizedCount", h -> 101);
        Holder other = new Holder();
        assertSame(error, assertThrows(Error.class, () -> errorFirst.get(other)));
        assertEquals(0, other.synchronizedCount);
        assertEquals(101, errorSecond.get(other));
        assertEquals(101, other.synchronizedCount);
    }

    @Test
    public void synchronizedStaticFieldIsSharedAcrossUpdaterInstances() {
        AtomicInteger computations = new AtomicInteger();
        IntSupplier first = ofLocationUpdater(Lazy.UpdaterMode.SERIALIZED, LOOKUP,
                IntSupplier.class, SynchronizedStaticHolder.class.getName() + ".count",
                () -> computations.incrementAndGet());
        IntSupplier second = ofLocationUpdater(Lazy.UpdaterMode.SERIALIZED, LOOKUP,
                IntSupplier.class, SynchronizedStaticHolder.class.getName() + ".count", () -> 99);
        assertEquals(1, first.getAsInt());
        assertEquals(1, second.getAsInt());
        assertEquals(1, SynchronizedStaticHolder.count);
        assertEquals(1, computations.get());
    }

    @Test
    public void synchronizedDefaultResultCanBeRetried() {
        AtomicInteger firstComputations = new AtomicInteger();
        AtomicInteger secondComputations = new AtomicInteger();
        CountField first = ofLocationUpdater(Lazy.UpdaterMode.SERIALIZED, LOOKUP,
                CountField.class, "synchronizedCount", h -> {
                    firstComputations.incrementAndGet();
                    return 0;
                });
        CountField second = ofLocationUpdater(Lazy.UpdaterMode.SERIALIZED, LOOKUP,
                CountField.class, "synchronizedCount", h -> {
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
        CountField updater = ofLocationUpdater(Lazy.UpdaterMode.SERIALIZED, LOOKUP,
                CountField.class, "synchronizedCount", h -> {
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
        CountField first = ofLocationUpdater(Lazy.UpdaterMode.SERIALIZED, LOOKUP,
                CountField.class, "synchronizedCount", h -> {
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
        CountField second = ofLocationUpdater(Lazy.UpdaterMode.SERIALIZED, LOOKUP,
                CountField.class, "synchronizedCount", h -> {
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
        IntSupplier updater = ofLocationUpdater(Lazy.UpdaterMode.SERIALIZED, LOOKUP,
                IntSupplier.class, DeferredSynchronizedHolder.class.getName() + ".count", () -> 42);
        assertFalse(deferredSynchronizedHolderInitialized);
        assertEquals(19, updater.getAsInt());
        assertTrue(deferredSynchronizedHolderInitialized);
    }

    @Test
    public void plainAndSynchronizedWritesSupportReferencesAndLongs() {
        checkDirectWrites(Lazy.UpdaterMode.SERIALIZED,
                "synchronizedText", h -> h.synchronizedText,
                "synchronizedLarge", h -> h.synchronizedLarge);
        checkDirectWrites(Lazy.UpdaterMode.PLAIN,
                "plainText", h -> h.plainText,
                "plainLarge", h -> h.plainLarge);

        LongSupplier staticWide = ofLocationUpdater(Lazy.UpdaterMode.PLAIN, LOOKUP,
                LongSupplier.class, PlainStaticHolder.class.getName() + ".large", () -> 0x2_0000_0001L);
        assertEquals(0x2_0000_0001L, staticWide.getAsLong());
        assertEquals(0x2_0000_0001L, staticWide.getAsLong());
        assertEquals(0x2_0000_0001L, PlainStaticHolder.large);
    }

    private static void checkDirectWrites(Lazy.UpdaterMode mode,
                                          String stringField,
                                          Function<Holder, String> stringAccessor,
                                          String longField,
                                          LongField longAccessor) {
        Holder holder = new Holder();
        String value = new String("computed");
        StringValueField reference = ofLocationUpdater(mode, LOOKUP,
                StringValueField.class, stringField, h -> value);
        LongField wide = ofLocationUpdater(mode, LOOKUP,
                LongField.class, longField, h -> 0x1_0000_0001L);
        assertSame(value, reference.apply(holder));
        assertSame(value, reference.apply(holder));
        assertSame(value, stringAccessor.apply(holder));
        assertEquals(0x1_0000_0001L, wide.get(holder));
        assertEquals(0x1_0000_0001L, wide.get(holder));
        assertEquals(0x1_0000_0001L, longAccessor.get(holder));
    }

    @Test
    public void synchronizedDetectsRecursiveAccess() {
        AtomicReference<CountField> reference = new AtomicReference<>();
        AtomicInteger computations = new AtomicInteger();
        CountField updater = ofLocationUpdater(Lazy.UpdaterMode.SERIALIZED, LOOKUP,
                CountField.class, "synchronizedCount", h -> {
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
        CountField updater = ofLocationUpdater(Lazy.UpdaterMode.PLAIN, LOOKUP,
                CountField.class, "plainCount", h -> {
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
        CountField updater = ofLocationUpdater(Lazy.UpdaterMode.PLAIN, LOOKUP,
                CountField.class, "plainCount", h -> {
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
        String selector = "count";
        CountField compute = h -> 42;
        assertThrows(NullPointerException.class,
                () -> ofLocationUpdater(null, LOOKUP, CountField.class, selector, compute));
        assertThrows(NullPointerException.class,
                () -> ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE,
                        null, CountField.class, selector, compute));
        assertThrows(NullPointerException.class,
                () -> ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP, null, selector, compute));
        assertThrows(NullPointerException.class,
                () -> ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP, CountField.class, null, compute));
        assertThrows(NullPointerException.class,
                () -> ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE,
                        LOOKUP, CountField.class, selector, null));

        AtomicInteger computations = new AtomicInteger();
        CountField updater = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP, CountField.class,
                selector, h -> computations.incrementAndGet());
        assertThrows(NullPointerException.class, () -> updater.get(null));
        assertEquals(0, computations.get());

        IntArrayElement arrayCompute = (array, index) -> 42;
        assertThrows(NullPointerException.class,
                () -> ofArrayUpdater(null, IntArrayElement.class, arrayCompute));
        assertThrows(NullPointerException.class,
                () -> ofArrayUpdater(Lazy.UpdaterMode.PUBLISH_ONCE,
                        null, arrayCompute));
        assertThrows(NullPointerException.class,
                () -> ofArrayUpdater(Lazy.UpdaterMode.PUBLISH_ONCE,
                        IntArrayElement.class, null));
    }

    @Test
    public void invalidFactories() {
        AtomicInteger computations = new AtomicInteger();
        CountField compute = h -> computations.incrementAndGet();
        assertThrows(IllegalArgumentException.class,
                () -> ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP, CountField.class,
                        "", compute));
        assertThrows(IllegalArgumentException.class,
                () -> ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP, CountField.class,
                        "missing", compute));
        assertThrows(IllegalArgumentException.class,
                () -> ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP, IntSupplier.class,
                        "Holder..count", () -> 42));
        assertThrows(IllegalArgumentException.class,
                () -> ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP, TwoArguments.class,
                        "count", (first, second) -> 42));
        assertThrows(IllegalArgumentException.class,
                () -> ofArrayUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, TwoArguments.class,
                        (first, second) -> 42));
        assertThrows(IllegalArgumentException.class,
                () -> ofArrayUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, TooManyArrayIndices.class,
                        (array, i0, i1, i2) -> 42));
        assertThrows(IllegalArgumentException.class,
                () -> ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP, TooManyOwnedArrayIndices.class,
                        "matrix", (owner, i0, i1, i2) -> 42));
        assertThrows(IllegalArgumentException.class,
                () -> ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP, ParameterBridgeField.class,
                        "text", h -> "unsupported parameter bridge"));
        assertThrows(IllegalArgumentException.class,
                () -> ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                        FinalHolderFunction.class, "value", h -> new Object()));
        IllegalArgumentException denied = assertThrows(IllegalArgumentException.class,
                () -> ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, MethodHandles.publicLookup(),
                        CountField.class, "count", compute));
        assertInstanceOf(IllegalAccessException.class, denied.getCause());
        assertEquals(0, computations.get());
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void rawTypesAreValidatedEagerly() {
        CountField compute = h -> 42;
        Class type = CountField.class;
        assertThrows(IllegalArgumentException.class,
                () -> ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP, type, "notAField", compute));
        assertThrows(IllegalArgumentException.class,
                () -> ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP, type, "count", "not a function"));
        assertThrows(IllegalArgumentException.class,
                () -> ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE,
                        LOOKUP, (Class) Object.class, "count", compute));
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void explicitFieldOwnerSupportsErasedGenericAccessorTokens() {
        Holder holder = new Holder();
        HolderFunction fieldComputer = ignored -> "generic field";
        HolderFunction fieldUpdater = ofLocationUpdater(
                Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                HolderFunction.class, "text", fieldComputer);
        assertEquals("generic field", fieldUpdater.apply(holder));
        assertEquals("generic field", holder.text);

        StaticHolder.text = null;
        Supplier staticComputer = () -> "generic static field";
        Supplier staticUpdater = ofLocationUpdater(
                Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                Supplier.class, StaticHolder.class.getName() + ".text",
                staticComputer);
        assertEquals("generic static field", staticUpdater.get());
        assertEquals("generic static field", StaticHolder.text);
    }
}

class UnrelatedStaticHolder {
    private static String hidden;
}

class UnrelatedInstanceHolder {
    private String hidden;
}
