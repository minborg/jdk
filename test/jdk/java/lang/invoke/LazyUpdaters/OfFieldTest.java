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
 * @summary Lazy field updaters for ordinary instance and static getter lambdas
 * @run junit OfFieldTest
 * @run junit/othervm -Xint OfFieldTest
 */

import java.lang.invoke.LazyUpdaters;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OfFieldTest {
    private static final Lookup LOOKUP = MethodHandles.lookup();
    private static boolean deferredHolderInitialized;

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

    static class Parent {
        int inherited;
    }

    static class Holder extends Parent {
        private boolean flag;
        private byte octet;
        private short small;
        private char character;
        private int count;
        private long large;
        private float fraction;
        private double radius;
        private String text;
        private Double boxed;
        private int[] array;
        private Payload payload;
        private volatile int published;

        int count() {
            return count;
        }
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
        BooleanField flag = LazyUpdaters.ofField(LOOKUP, BooleanField.class,
                h -> h.flag, h -> true);
        ByteField octet = LazyUpdaters.ofField(LOOKUP, ByteField.class,
                h -> h.octet, h -> (byte) 12);
        ShortField small = LazyUpdaters.ofField(LOOKUP, ShortField.class,
                h -> h.small, h -> (short) 123);
        CharField character = LazyUpdaters.ofField(LOOKUP, CharField.class,
                h -> h.character, h -> 'x');
        CountField count = LazyUpdaters.ofField(LOOKUP, CountField.class,
                h -> h.count, h -> 1234);
        LongField large = LazyUpdaters.ofField(LOOKUP, LongField.class,
                h -> h.large, h -> 12345L);
        FloatField fraction = LazyUpdaters.ofField(LOOKUP, FloatField.class,
                h -> h.fraction, h -> 1.25f);
        DoubleField radius = LazyUpdaters.ofField(LOOKUP, DoubleField.class,
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
        Function<Holder, R> updater = LazyUpdaters.ofField(LOOKUP, functionType(),
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
        StringField covariant = LazyUpdaters.ofField(LOOKUP, StringField.class,
                h -> h.text, h -> result);
        ParentStringField parentView = covariant;
        assertSame(result, covariant.get(holder));
        assertSame(result, parentView.get(holder));
        assertSame(result, holder.text);

        InheritedStringField inherited = LazyUpdaters.ofField(LOOKUP,
                InheritedStringField.class, h -> h.text, h -> "inherited");
        assertEquals("inherited", inherited.apply(new Holder()));
        assertSame(result, inherited.apply(holder));

        int[] array = { 1, 2, 3 };
        Function<Holder, int[]> arrayUpdater = LazyUpdaters.ofField(LOOKUP,
                OfFieldTest.<Holder, int[]>functionType(), h -> h.array, h -> array);
        assertSame(array, arrayUpdater.apply(holder));
        assertSame(array, arrayUpdater.apply(holder));

        // A boxed zero is an ordinary non-null value in a reference field.
        Function<Holder, Double> boxed = LazyUpdaters.ofField(LOOKUP,
                OfFieldTest.<Holder, Double>functionType(), h -> h.boxed, h -> 0.0);
        assertEquals(0.0, boxed.apply(holder));
        assertEquals(0.0, holder.boxed);
    }

    @Test
    public void initializationIsLazyAndPerReceiver() {
        AtomicInteger computations = new AtomicInteger();
        ToIntFunction<Holder> updater = LazyUpdaters.ofField(LOOKUP,
                OfFieldTest.<Holder>intFunctionType(), h -> h.count,
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
    public void inheritedAndVolatileFields() {
        Holder holder = new Holder();
        CountField inherited = LazyUpdaters.ofField(LOOKUP, CountField.class,
                h -> h.inherited, h -> 12);
        CountField published = LazyUpdaters.ofField(LOOKUP, CountField.class,
                h -> h.published, h -> 34);
        assertEquals(12, inherited.get(holder));
        assertEquals(12, holder.inherited);
        assertEquals(34, published.get(holder));
        assertEquals(34, holder.published);
    }

    @Test
    public void primitiveStaticFields() {
        BooleanSupplier flag = LazyUpdaters.ofField(LOOKUP, BooleanSupplier.class,
                () -> StaticHolder.flag, () -> true);
        ByteSupplier octet = LazyUpdaters.ofField(LOOKUP, ByteSupplier.class,
                () -> StaticHolder.octet, () -> (byte) 12);
        ShortSupplier small = LazyUpdaters.ofField(LOOKUP, ShortSupplier.class,
                () -> StaticHolder.small, () -> (short) 123);
        CharSupplier character = LazyUpdaters.ofField(LOOKUP, CharSupplier.class,
                () -> StaticHolder.character, () -> 'x');
        IntSupplier count = LazyUpdaters.ofField(LOOKUP, IntSupplier.class,
                () -> StaticHolder.count, () -> 1234);
        LongSupplier large = LazyUpdaters.ofField(LOOKUP, LongSupplier.class,
                () -> StaticHolder.large, () -> 12345L);
        FloatSupplier fraction = LazyUpdaters.ofField(LOOKUP, FloatSupplier.class,
                () -> StaticHolder.fraction, () -> 1.25f);
        DoubleSupplier radius = LazyUpdaters.ofField(LOOKUP, DoubleSupplier.class,
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
        Supplier<String> updater = LazyUpdaters.ofField(LOOKUP,
                OfFieldTest.<String>supplierType(), () -> StaticHolder.text, () -> {
                    computations.incrementAndGet();
                    return result;
                });
        assertEquals(0, computations.get());
        assertSame(result, updater.get());
        assertSame(result, updater.get());
        assertSame(result, StaticHolder.text);
        assertEquals(1, computations.get());

        Supplier<Integer> boxed = LazyUpdaters.ofField(LOOKUP,
                OfFieldTest.<Integer>supplierType(), () -> StaticHolder.boxed, () -> 0);
        assertEquals(0, boxed.get());
        assertEquals(0, boxed.get());
    }

    @Test
    public void staticInitializationIsDeferred() {
        assertFalse(deferredHolderInitialized);
        AtomicInteger computations = new AtomicInteger();
        IntSupplier updater = LazyUpdaters.ofField(LOOKUP, IntSupplier.class,
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
        CountField updater = LazyUpdaters.ofField(LOOKUP, CountField.class,
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
        Function<Holder, R> updater = LazyUpdaters.ofField(LOOKUP, functionType(),
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
        FloatField updater = LazyUpdaters.ofField(LOOKUP, FloatField.class,
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
        DoubleField updater = LazyUpdaters.ofField(LOOKUP, DoubleField.class,
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
        CountField updater = LazyUpdaters.ofField(LOOKUP, CountField.class,
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
        CountField first = LazyUpdaters.ofField(LOOKUP, CountField.class,
                h -> h.count, h -> computations.incrementAndGet());
        CountField second = LazyUpdaters.ofField(LOOKUP, CountField.class,
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
        Function<Holder, Payload> first = LazyUpdaters.ofField(LOOKUP,
                OfFieldTest.<Holder, Payload>functionType(), h -> h.payload, compute);
        Function<Holder, Payload> second = LazyUpdaters.ofField(LOOKUP,
                OfFieldTest.<Holder, Payload>functionType(), h -> h.payload, compute);
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
    public void nullArgumentsAndReceiver() {
        CountField selector = h -> h.count;
        CountField compute = h -> 42;
        assertThrows(NullPointerException.class,
                () -> LazyUpdaters.ofField(null, CountField.class, selector, compute));
        assertThrows(NullPointerException.class,
                () -> LazyUpdaters.ofField(LOOKUP, null, selector, compute));
        assertThrows(NullPointerException.class,
                () -> LazyUpdaters.ofField(LOOKUP, CountField.class, null, compute));
        assertThrows(NullPointerException.class,
                () -> LazyUpdaters.ofField(LOOKUP, CountField.class, selector, null));

        AtomicInteger computations = new AtomicInteger();
        CountField updater = LazyUpdaters.ofField(LOOKUP, CountField.class,
                selector, h -> computations.incrementAndGet());
        assertThrows(NullPointerException.class, () -> updater.get(null));
        assertEquals(0, computations.get());
    }

    @Test
    public void invalidFactories() {
        AtomicInteger computations = new AtomicInteger();
        CountField compute = h -> computations.incrementAndGet();
        assertThrows(IllegalArgumentException.class,
                () -> LazyUpdaters.ofField(LOOKUP, CountField.class,
                        h -> h.count + 1, compute));
        assertThrows(IllegalArgumentException.class,
                () -> LazyUpdaters.ofField(LOOKUP, CountField.class,
                        Holder::count, compute));
        Holder captured = new Holder();
        assertThrows(IllegalArgumentException.class,
                () -> LazyUpdaters.ofField(LOOKUP, IntSupplier.class,
                        () -> captured.count, () -> 42));
        assertThrows(IllegalArgumentException.class,
                () -> LazyUpdaters.ofField(LOOKUP, TwoArguments.class,
                        (first, second) -> first.count, (first, second) -> 42));
        assertThrows(IllegalArgumentException.class,
                () -> LazyUpdaters.ofField(LOOKUP, ParameterBridgeField.class,
                        h -> h.text, h -> "unsupported parameter bridge"));
        assertThrows(IllegalArgumentException.class,
                () -> LazyUpdaters.ofField(LOOKUP,
                        OfFieldTest.<FinalHolder, Object>functionType(),
                        h -> h.value, h -> new Object()));
        IllegalArgumentException denied = assertThrows(IllegalArgumentException.class,
                () -> LazyUpdaters.ofField(MethodHandles.publicLookup(), CountField.class,
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
                () -> LazyUpdaters.ofField(LOOKUP, type, "not an accessor", compute));
        assertThrows(IllegalArgumentException.class,
                () -> LazyUpdaters.ofField(LOOKUP, type, selector, "not a function"));
        assertThrows(IllegalArgumentException.class,
                () -> LazyUpdaters.ofField(LOOKUP, (Class) Object.class, selector, compute));
    }
}
