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
 * @summary Lazy array and field accessors with use-site computing functions
 * @enablePreview
 * @build UpdaterTestAdapter
 * @run junit UseSiteAccessorTest
 * @run junit/othervm -Xint UseSiteAccessorTest
 */

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.util.Lazy;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UseSiteAccessorTest {
    private static final Lookup LOOKUP = MethodHandles.lookup();

    private static <T> T ofLocationUpdater(Lazy.UpdaterMode mode,
                                           Lookup lookup, Class<T> type,
                                           String selector, T computingFunction) {
        return UpdaterTestAdapter.locationBuilder(mode, lookup, type,
                selector, Holder.class).build(computingFunction);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static <T> T ofLocationUpdater(Lazy.UpdaterMode mode,
                                           Lookup lookup, Class<T> useSiteShape,
                                           String selector) {
        Class computerShape = UpdaterTestAdapter.computerShape(useSiteShape);
        return (T) UpdaterTestAdapter.locationBuilder(mode, lookup,
                computerShape, selector, Holder.class)
                .buildUseSite(useSiteShape);
    }

    private static <T> T ofArrayUpdater(Lazy.UpdaterMode mode,
                                        Class<T> type, T computingFunction) {
        return Lazy.ofUpdater(type).withMode(mode).build(computingFunction);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static <T> T ofArrayUpdater(Lazy.UpdaterMode mode,
                                        Class<T> useSiteShape) {
        Class computerShape = UpdaterTestAdapter.computerShape(useSiteShape);
        return (T) Lazy.ofUpdater(computerShape).withMode(mode)
                .buildUseSite(useSiteShape);
    }

    private interface IntCell {
        interface Computer { int compute(int[][] array, int row, int column); }
        int get(Computer computer, int[][] array, int row, int column);
    }

    private interface IntField {
        interface Computer { int compute(Holder holder); }
        int get(Computer computer, Holder holder);
    }

    private interface IntOwnerElement {
        interface Computer { int compute(Holder holder, int index); }
        int get(Computer computer, Holder holder, int index);
    }

    private interface StaticLongField {
        interface Computer { long compute(); }
        long get(Computer computer);
    }

    private interface StaticIntElement {
        interface Computer { int compute(int index); }
        int get(Computer computer, int index);
    }

    private interface StringElement {
        interface Computer { String compute(String[] array, int index); }
        String get(Computer computer, String[] array, int index);
    }

    private interface BoxedIntElement {
        interface Computer { Object compute(int[] array, int index); }
        Object get(Computer computer, int[] array, int index);
    }

    private interface IntegerIntoNumberField {
        interface Computer { Integer compute(Holder holder); }
        Number get(Computer computer, Holder holder);
    }

    private interface IntegerIntoNumberArray {
        interface Computer { Integer compute(Number[] array, int index); }
        Number get(Computer computer, Number[] array, int index);
    }

    private interface IntIntoLongField {
        interface Computer { int compute(Object receiver); }
        long get(Computer computer, Holder holder);
    }

    private interface DeclaredIntElement { int get(int[] array, int index); }

    private interface HolderIntAccessor extends Lazy.IntFieldAccessor<Holder> { }

    private interface HolderLongAccessor extends Lazy.LongFieldAccessor<Holder> { }

    private interface IndirectHolderLongAccessor extends HolderLongAccessor { }

    private interface StringArrayAccessor
            extends Lazy.ReferenceArrayAccessor<String> { }

    private interface NumberArrayAccessor
            extends Lazy.ReferenceArrayAccessor<Number> { }

    private interface StaticStringAccessor
            extends Lazy.StaticReferenceFieldAccessor<String> { }

    private interface HolderStringAccessor
            extends Lazy.ReferenceFieldAccessor<Holder, String> { }

    private interface HolderStringListAccessor
            extends Lazy.ReferenceFieldAccessor<Holder, List<String>> { }

    private interface GenericHolderListAccessor<T>
            extends Lazy.ReferenceFieldAccessor<Holder, List<T>> { }

    private interface IndirectHolderStringListAccessor
            extends GenericHolderListAccessor<String> { }

    private interface GenericWildcardListAccessor<T>
            extends Lazy.ReferenceFieldAccessor<Holder,
                    List<? extends T>> { }

    private interface IndirectHolderNumberListAccessor
            extends GenericWildcardListAccessor<Number> { }

    private interface StaticStringListAccessor
            extends Lazy.StaticReferenceFieldAccessor<List<String>> { }

    private interface GenericIntField<R> {
        interface Computer<R> { int compute(R receiver); }
        int get(Computer<R> computer, R receiver);
    }

    private interface GenericIntArray<A> {
        interface Computer<A> { int compute(A array, int index); }
        int get(Computer<A> computer, A array, int index);
    }

    private interface GenericDeclaredIntField<R> { int get(R receiver); }

    private interface GenericDeclaredIntArray<A> { int get(A array, int index); }

    private interface SpecializedGenericDeclaredIntField
            extends GenericDeclaredIntField<Holder> { }

    @SuppressWarnings("rawtypes")
    private interface RawInheritedIntField extends GenericDeclaredIntField { }

    private interface MethodGenericIntArray {
        interface Computer { int compute(int[] array, int index); }
        <A> int get(Computer computer, A array, int index);
    }

    private interface GenericArrayComputer<A> { int compute(A array, int index); }

    @SuppressWarnings("rawtypes")
    private interface RawComputerIntArray {
        int get(GenericArrayComputer computer, int[] array, int index);
    }

    private interface ConcreteIntArrayComputer {
        int compute(int[] array, int index);
    }

    private interface GenericCallbackAccessor<C> {
        int get(C computer, int[] array, int index);
    }

    private interface FixedCallbackIntArray
            extends GenericCallbackAccessor<ConcreteIntArrayComputer> { }

    private interface NoComputer { int get(int[] array, int index); }

    private interface WrongComputerArguments {
        interface Computer { int compute(long[] array, int index); }
        int get(Computer computer, int[] array, int index);
    }

    private interface VoidComputer {
        interface Computer { void compute(int[] array, int index); }
        int get(Computer computer, int[] array, int index);
    }

    private interface TooManyIndices {
        interface Computer { int compute(int[] array, int first, int second); }
        int get(Computer computer, int[] array, int first, int second);
    }

    private static class Holder {
        private int count;
        private long large;
        private Number number;
        private String text;
        private Object objectValue;
        private List<String> stringList;
        private List<Integer> integerList;
        private List<? extends Number> numberList;
        private final int[] entries = new int[2];
    }

    private static class StaticHolder {
        private static long large;
        private static int builtInInt;
        private static long builtInLong;
        private static String referencePlain;
        private static String referencePublish;
        private static String referenceSerialized;
        private static Object objectValue;
        private static List<String> stringList;
        private static List<Integer> integerList;
        private static final int[] entries = { 31, 0 };
    }

    @Test
    public void arrayComputationIsSuppliedPerCallInEveryMode() {
        for (Lazy.UpdaterMode mode : Lazy.UpdaterMode.values()) {
            IntCell accessor = ofArrayUpdater(mode, IntCell.class);
            int[][] first = { { 0, 31 }, { 0 } };
            int[][] second = { { 0 } };
            AtomicInteger computations = new AtomicInteger();

            assertEquals(10, accessor.get((array, row, column) -> {
                assertSame(first, array);
                assertEquals(0, row);
                assertEquals(0, column);
                computations.incrementAndGet();
                return 10;
            }, first, 0, 0));
            assertEquals(10, accessor.get((array, row, column) -> {
                throw new AssertionError("cached element was recomputed");
            }, first, 0, 0));
            assertEquals(31, accessor.get((array, row, column) -> {
                throw new AssertionError("preexisting element was recomputed");
            }, first, 0, 1));
            assertEquals(21, accessor.get((array, row, column) -> {
                computations.incrementAndGet();
                return 20 + row + column;
            }, first, 1, 0));
            assertEquals(40, accessor.get((array, row, column) -> {
                computations.incrementAndGet();
                return 40;
            }, second, 0, 0));
            assertEquals(3, computations.get());
            assertEquals(10, first[0][0]);
            assertEquals(21, first[1][0]);
            assertEquals(40, second[0][0]);

            assertThrows(NullPointerException.class,
                    () -> accessor.get((array, row, column) -> {
                        throw new AssertionError("invalid array reached computation");
                    }, null, 0, 0));
            assertThrows(ArrayIndexOutOfBoundsException.class,
                    () -> accessor.get((array, row, column) -> {
                        throw new AssertionError("invalid index reached computation");
                    }, first, 2, 0));
            assertEquals(3, computations.get());
        }
    }

    @Test
    public void arrayFailuresLeaveTheElementAvailableForAnotherComputation() {
        IntCell accessor = ofArrayUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, IntCell.class);
        int[][] array = { new int[2] };
        IllegalStateException failure = new IllegalStateException("failed");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> accessor.get((a, row, column) -> { throw failure; }, array, 0, 0)));
        assertEquals(0, array[0][0]);
        assertThrows(IllegalArgumentException.class,
                () -> accessor.get((a, row, column) -> 0, array, 0, 0));
        assertEquals(0, array[0][0]);
        assertEquals(17, accessor.get((a, row, column) -> 17, array, 0, 0));
        assertEquals(17, accessor.get((a, row, column) -> 99, array, 0, 0));
        assertThrows(NullPointerException.class,
                () -> accessor.get(null, array, 0, 1));
        assertEquals(0, array[0][1]);
    }

    @Test
    public void referenceArrayUsesNullAsTheSentinel() {
        StringElement accessor = ofArrayUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, StringElement.class);
        String[] array = new String[2];
        String value = new String("computed");
        assertThrows(IllegalArgumentException.class,
                () -> accessor.get((a, index) -> null, array, 0));
        assertSame(value, accessor.get((a, index) -> value, array, 0));
        assertSame(value, accessor.get((a, index) -> {
            throw new AssertionError("cached reference was recomputed");
        }, array, 0));
        assertSame(value, array[0]);
        assertSame(value, accessor.get((a, index) -> value, array, 1));
    }

    @Test
    public void boxedCallbackResultIsCheckedAgainstPrimitiveStorage() {
        BoxedIntElement accessor = ofArrayUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, BoxedIntElement.class);
        int[] array = new int[1];
        assertThrows(IllegalArgumentException.class,
                () -> accessor.get((a, index) -> Integer.valueOf(0), array, 0));
        assertEquals(0, array[0]);
        assertEquals(Integer.valueOf(15),
                accessor.get((a, index) -> Integer.valueOf(15), array, 0));
        assertEquals(Integer.valueOf(15), accessor.get((a, index) -> {
            throw new AssertionError("cached element was recomputed");
        }, array, 0));
        assertEquals(15, array[0]);
    }

    @Test
    public void referenceStorageAcceptsNarrowerCallbackResults() {
        IntegerIntoNumberField field = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                IntegerIntoNumberField.class, "number");
        IntegerIntoNumberArray element = ofArrayUpdater(
                Lazy.UpdaterMode.PUBLISH_ONCE,
                IntegerIntoNumberArray.class);
        Holder holder = new Holder();
        Number[] array = new Number[1];
        Integer fieldValue = Integer.valueOf(1000);
        Integer elementValue = Integer.valueOf(2000);

        assertSame(fieldValue, field.get(h -> fieldValue, holder));
        assertSame(fieldValue, field.get(h -> {
            throw new AssertionError("cached Number field was recomputed");
        }, holder));
        assertSame(fieldValue, holder.number);

        assertSame(elementValue, element.get((a, index) -> elementValue, array, 0));
        assertSame(elementValue, element.get((a, index) -> {
            throw new AssertionError("cached Number element was recomputed");
        }, array, 0));
        assertSame(elementValue, array[0]);
    }

    @Test
    public void primitiveWideningAndBroaderCallbackCoordinates() {
        IntIntoLongField accessor = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                IntIntoLongField.class, "large");
        Holder holder = new Holder();
        assertEquals(29L, accessor.get(receiver -> {
            assertSame(holder, receiver);
            return 29;
        }, holder));
        assertEquals(29L, accessor.get(receiver -> {
            throw new AssertionError("cached long field was recomputed");
        }, holder));
        assertEquals(29L, holder.large);
        assertThrows(NullPointerException.class, () -> accessor.get(null, holder));
    }

    @Test
    public void builtInIntAndLongArrayAccessors() {
        for (Lazy.UpdaterMode mode : Lazy.UpdaterMode.values()) {
            Lazy.IntArrayAccessor ints = ofArrayUpdater(mode,
                    Lazy.IntArrayAccessor.class);
            Lazy.LongArrayAccessor longs = ofArrayUpdater(mode,
                    Lazy.LongArrayAccessor.class);
            int[] intArray = new int[2];
            long[] longArray = new long[2];
            AtomicInteger computations = new AtomicInteger();

            assertEquals(4, ints.getOrCompute((array, index) -> {
                assertSame(intArray, array);
                computations.incrementAndGet();
                return array.length + index + 1;
            }, intArray, 1));
            assertEquals(4, ints.getOrCompute((array, index) -> {
                throw new AssertionError("cached int element was recomputed");
            }, intArray, 1));
            assertEquals(1L << 40, longs.getOrCompute((array, index) -> {
                assertSame(longArray, array);
                computations.incrementAndGet();
                return 1L << 40;
            }, longArray, 0));
            assertEquals(1L << 40, longs.getOrCompute((array, index) -> {
                throw new AssertionError("cached long element was recomputed");
            }, longArray, 0));
            assertEquals(2, computations.get());
            assertEquals(4, intArray[1]);
            assertEquals(1L << 40, longArray[0]);
        }
    }

    @Test
    public void builtInReferenceArrayAccessorsInEveryMode() {
        for (Lazy.UpdaterMode mode : Lazy.UpdaterMode.values()) {
            StringArrayAccessor strings = ofArrayUpdater(mode,
                    StringArrayAccessor.class);
            NumberArrayAccessor numbers = ofArrayUpdater(mode,
                    NumberArrayAccessor.class);
            String existing = new String("existing");
            String[] values = { null, existing };
            AtomicInteger computations = new AtomicInteger();

            assertThrows(IllegalArgumentException.class,
                    () -> strings.getOrCompute((array, index) -> null, values, 0));
            assertNull(values[0]);

            String value = new String("computed-" + mode);
            assertSame(value, strings.getOrCompute((array, index) -> {
                assertSame(values, array);
                assertEquals(0, index);
                computations.incrementAndGet();
                return value;
            }, values, 0));
            assertSame(value, strings.getOrCompute((array, index) -> {
                throw new AssertionError("cached reference was recomputed");
            }, values, 0));
            assertSame(existing, strings.getOrCompute((array, index) -> {
                throw new AssertionError("preexisting reference was recomputed");
            }, values, 1));
            assertThrows(NullPointerException.class,
                    () -> strings.getOrCompute(null, values, 0));
            assertSame(value, values[0]);

            Integer[] covariantArray = new Integer[1];
            Integer integer = Integer.valueOf(1000 + mode.ordinal());
            assertSame(integer, numbers.getOrCompute((array, index) -> {
                assertSame(covariantArray, array);
                computations.incrementAndGet();
                return integer;
            }, covariantArray, 0));
            assertSame(integer, numbers.getOrCompute((array, index) -> {
                throw new AssertionError("covariant array element was recomputed");
            }, covariantArray, 0));
            assertSame(integer, covariantArray[0]);
            assertEquals(2, computations.get());
        }
    }

    @Test
    public void declarationAndUseSiteAccessorsShareArrayElements() {
        Lazy.IntArrayAccessor useSite = ofArrayUpdater(
                Lazy.UpdaterMode.SERIALIZED, Lazy.IntArrayAccessor.class);
        AtomicInteger declarationComputations = new AtomicInteger();
        DeclaredIntElement declarationSite = ofArrayUpdater(
                Lazy.UpdaterMode.SERIALIZED, DeclaredIntElement.class,
                (array, index) -> {
                    declarationComputations.incrementAndGet();
                    return 19;
                });
        int[] array = new int[2];

        assertEquals(13, useSite.getOrCompute((a, i) -> 13, array, 0));
        assertEquals(13, declarationSite.get(array, 0));
        assertEquals(19, declarationSite.get(array, 1));
        assertEquals(19, useSite.getOrCompute((a, i) -> {
            throw new AssertionError("cached element was recomputed");
        }, array, 1));
        assertEquals(1, declarationComputations.get());
    }

    @Test
    public void fieldAndArrayFieldComputationIsSuppliedPerCall() {
        for (Lazy.UpdaterMode mode : Lazy.UpdaterMode.values()) {
            IntField field = ofLocationUpdater(mode, LOOKUP, IntField.class, "count");
            IntOwnerElement element = ofLocationUpdater(mode, LOOKUP,
                    IntOwnerElement.class, "entries");
            Holder first = new Holder();
            Holder second = new Holder();
            AtomicInteger computations = new AtomicInteger();

            assertEquals(27, field.get(holder -> {
                assertSame(first, holder);
                computations.incrementAndGet();
                return 27;
            }, first));
            assertEquals(27, field.get(holder -> {
                throw new AssertionError("cached field was recomputed");
            }, first));
            assertEquals(41, field.get(holder -> {
                assertSame(second, holder);
                computations.incrementAndGet();
                return 41;
            }, second));
            assertEquals(27, first.count);
            assertEquals(41, second.count);

            assertEquals(12, element.get((holder, index) -> {
                assertSame(first, holder);
                assertEquals(1, index);
                computations.incrementAndGet();
                return 12;
            }, first, 1));
            assertEquals(12, element.get((holder, index) -> {
                throw new AssertionError("cached array-field element was recomputed");
            }, first, 1));
            assertEquals(12, first.entries[1]);
            assertEquals(3, computations.get());

            assertThrows(NullPointerException.class, () -> field.get(holder -> {
                throw new AssertionError("null receiver reached computation");
            }, null));
            assertThrows(ArrayIndexOutOfBoundsException.class,
                    () -> element.get((holder, index) -> {
                        throw new AssertionError("invalid index reached computation");
                    }, first, 2));
            assertEquals(3, computations.get());
        }
    }

    @Test
    public void publishOnceLocationFactorySupportsStaticFields() {
        StaticLongField accessor = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE,
                LOOKUP, StaticLongField.class, StaticHolder.class.getName() + ".large");
        AtomicInteger computations = new AtomicInteger();
        assertEquals(52L, accessor.get(() -> {
            computations.incrementAndGet();
            return 52L;
        }));
        assertEquals(52L, accessor.get(() -> {
            throw new AssertionError("cached static field was recomputed");
        }));
        assertEquals(52L, StaticHolder.large);
        assertEquals(1, computations.get());
    }

    @Test
    public void useSiteStaticArrayFieldElement() {
        StaticIntElement accessor = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                StaticIntElement.class, StaticHolder.class.getName() + ".entries");
        AtomicInteger computations = new AtomicInteger();

        assertEquals(31, accessor.get(index -> {
            throw new AssertionError("preexisting element was recomputed");
        }, 0));
        assertEquals(42, accessor.get(index -> {
            assertEquals(1, index);
            computations.incrementAndGet();
            return 42;
        }, 1));
        assertEquals(42, accessor.get(index -> {
            throw new AssertionError("cached element was recomputed");
        }, 1));
        assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> accessor.get(index -> {
                    throw new AssertionError("invalid index reached computation");
                }, 2));
        assertEquals(1, computations.get());
        assertEquals(42, StaticHolder.entries[1]);
    }

    @Test
    public void builtInIntAndLongStaticFieldAccessors() {
        Lazy.StaticIntFieldAccessor ints = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                Lazy.StaticIntFieldAccessor.class,
                StaticHolder.class.getName() + ".builtInInt");
        Lazy.StaticLongFieldAccessor longs = ofLocationUpdater(
                Lazy.UpdaterMode.SERIALIZED, LOOKUP,
                Lazy.StaticLongFieldAccessor.class,
                StaticHolder.class.getName() + ".builtInLong");
        AtomicInteger computations = new AtomicInteger();

        assertThrows(IllegalArgumentException.class, () -> ints.getOrCompute(() -> 0));
        assertEquals(0, StaticHolder.builtInInt);
        assertEquals(23, ints.getOrCompute(() -> {
            computations.incrementAndGet();
            return 23;
        }));
        assertEquals(23, ints.getOrCompute(() -> {
            throw new AssertionError("cached int field was recomputed");
        }));
        assertEquals(1L << 42, longs.getOrCompute(() -> {
            computations.incrementAndGet();
            return 1L << 42;
        }));
        assertEquals(1L << 42, longs.getOrCompute(() -> {
            throw new AssertionError("cached long field was recomputed");
        }));
        assertEquals(23, StaticHolder.builtInInt);
        assertEquals(1L << 42, StaticHolder.builtInLong);
        assertEquals(2, computations.get());
    }

    @Test
    public void builtInReferenceStaticFieldAccessorsInEveryMode() {
        StaticHolder.referencePlain = null;
        StaticHolder.referencePublish = null;
        StaticHolder.referenceSerialized = null;
        for (Lazy.UpdaterMode mode : Lazy.UpdaterMode.values()) {
            String fieldName = switch (mode) {
                case PLAIN -> "referencePlain";
                case PUBLISH_ONCE -> "referencePublish";
                case SERIALIZED -> "referenceSerialized";
            };
            StaticStringAccessor accessor = ofLocationUpdater(mode, LOOKUP,
                    StaticStringAccessor.class,
                    StaticHolder.class.getName() + "." + fieldName);
            AtomicInteger computations = new AtomicInteger();

            assertThrows(IllegalArgumentException.class,
                    () -> accessor.getOrCompute(() -> null));
            String value = new String("static-" + mode);
            assertSame(value, accessor.getOrCompute(() -> {
                computations.incrementAndGet();
                return value;
            }));
            assertSame(value, accessor.getOrCompute(() -> {
                throw new AssertionError("cached reference field was recomputed");
            }));
            assertThrows(NullPointerException.class, () -> accessor.getOrCompute(null));
            assertEquals(1, computations.get());
            assertSame(value, switch (mode) {
                case PLAIN -> StaticHolder.referencePlain;
                case PUBLISH_ONCE -> StaticHolder.referencePublish;
                case SERIALIZED -> StaticHolder.referenceSerialized;
            });
        }
    }

    @Test
    public void builtInInstanceFieldAccessorsResolveSpecializedReceivers() {
        for (Lazy.UpdaterMode mode : Lazy.UpdaterMode.values()) {
            HolderIntAccessor ints = ofLocationUpdater(mode, LOOKUP,
                    HolderIntAccessor.class, "count");
            IndirectHolderLongAccessor longs = ofLocationUpdater(mode, LOOKUP,
                    IndirectHolderLongAccessor.class, "large");
            Holder holder = new Holder();
            AtomicInteger computations = new AtomicInteger();

            assertEquals(32, ints.getOrCompute(receiver -> {
                assertSame(holder, receiver);
                computations.incrementAndGet();
                return receiver.count + receiver.entries.length + 30;
            }, holder));
            assertEquals(32, ints.getOrCompute(receiver -> {
                throw new AssertionError("cached int field was recomputed");
            }, holder));
            assertEquals((1L << 45) + 2, longs.getOrCompute(receiver -> {
                assertSame(holder, receiver);
                computations.incrementAndGet();
                return (1L << 45) + receiver.entries.length;
            }, holder));
            assertEquals((1L << 45) + 2, longs.getOrCompute(receiver -> {
                throw new AssertionError("cached long field was recomputed");
            }, holder));
            assertEquals(32, holder.count);
            assertEquals((1L << 45) + 2, holder.large);
            assertEquals(2, computations.get());
        }
    }

    @Test
    public void builtInReferenceInstanceFieldAccessorsInEveryMode() {
        for (Lazy.UpdaterMode mode : Lazy.UpdaterMode.values()) {
            HolderStringAccessor accessor = ofLocationUpdater(mode, LOOKUP,
                    HolderStringAccessor.class, "text");
            Holder holder = new Holder();
            AtomicInteger computations = new AtomicInteger();

            assertThrows(IllegalArgumentException.class,
                    () -> accessor.getOrCompute(receiver -> null, holder));
            assertNull(holder.text);
            String value = new String("instance-" + mode);
            assertSame(value, accessor.getOrCompute(receiver -> {
                assertSame(holder, receiver);
                computations.incrementAndGet();
                return value;
            }, holder));
            assertSame(value, accessor.getOrCompute(receiver -> {
                throw new AssertionError("cached reference field was recomputed");
            }, holder));
            assertThrows(NullPointerException.class,
                    () -> accessor.getOrCompute(null, holder));
            assertSame(value, holder.text);
            assertEquals(1, computations.get());
        }
    }

    @Test
    public void referenceFieldAccessorsRejectIncompatibleStorageAtFactory() {
        assertThrows(IllegalArgumentException.class,
                () -> ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                        HolderStringAccessor.class, "objectValue"));
        assertThrows(IllegalArgumentException.class,
                () -> ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                        HolderStringAccessor.class, "count"));
        assertThrows(IllegalArgumentException.class,
                () -> ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                        StaticStringAccessor.class,
                        StaticHolder.class.getName() + ".objectValue"));
        assertThrows(IllegalArgumentException.class,
                () -> ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                        StaticStringAccessor.class,
                        StaticHolder.class.getName() + ".builtInInt"));
    }

    @Test
    public void parameterizedReferenceFieldTypesAreCheckedAtFactory() {
        HolderStringListAccessor instance = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                HolderStringListAccessor.class, "stringList");
        Holder holder = new Holder();
        List<String> instanceValue = List.of("instance");
        assertSame(instanceValue, instance.getOrCompute(receiver -> instanceValue, holder));
        assertSame(instanceValue, holder.stringList);
        assertThrows(IllegalArgumentException.class,
                () -> ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                        HolderStringListAccessor.class, "integerList"));

        StaticStringListAccessor staticAccessor = ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                StaticStringListAccessor.class,
                StaticHolder.class.getName() + ".stringList");
        List<String> staticValue = List.of("static");
        assertSame(staticValue, staticAccessor.getOrCompute(() -> staticValue));
        assertSame(staticValue, StaticHolder.stringList);
        assertThrows(IllegalArgumentException.class,
                () -> ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                        StaticStringListAccessor.class,
                        StaticHolder.class.getName() + ".integerList"));
    }

    @Test
    public void indirectParameterizedReferenceFieldTypeIsResolved() {
        IndirectHolderStringListAccessor accessor = ofLocationUpdater(
                Lazy.UpdaterMode.PUBLISH_ONCE,
                LOOKUP, IndirectHolderStringListAccessor.class, "stringList");
        Holder holder = new Holder();
        List<String> value = List.of("indirect");
        assertSame(value, accessor.getOrCompute(receiver -> value, holder));
        assertSame(value, holder.stringList);
        assertThrows(IllegalArgumentException.class,
                () -> ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                        IndirectHolderStringListAccessor.class, "integerList"));
    }

    @Test
    public void indirectWildcardReferenceFieldTypeIsResolved() {
        IndirectHolderNumberListAccessor accessor = ofLocationUpdater(
                Lazy.UpdaterMode.PUBLISH_ONCE,
                LOOKUP, IndirectHolderNumberListAccessor.class, "numberList");
        Holder holder = new Holder();
        List<Integer> value = List.of(17);
        assertSame(value, accessor.getOrCompute(receiver -> value, holder));
        assertSame(value, holder.numberList);
        assertThrows(IllegalArgumentException.class,
                () -> ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                        IndirectHolderNumberListAccessor.class, "stringList"));
    }

    @Test
    public void serializedAccessorsShareThePhysicalArrayElement() throws Exception {
        IntCell firstAccessor = ofArrayUpdater(Lazy.UpdaterMode.SERIALIZED, IntCell.class);
        IntCell secondAccessor = ofArrayUpdater(Lazy.UpdaterMode.SERIALIZED, IntCell.class);
        int[] row = new int[1];
        int[][] first = { row };
        int[][] alias = { row };
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger secondComputations = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> computing = executor.submit(() -> firstAccessor.get((array, r, c) -> {
                entered.countDown();
                try {
                    assertTrue(release.await(20, SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
                return 61;
            }, first, 0, 0));
            assertTrue(entered.await(20, SECONDS));
            Future<Integer> waiting = executor.submit(() -> {
                secondStarted.countDown();
                return secondAccessor.get((array, r, c) -> {
                    secondComputations.incrementAndGet();
                    return 99;
                }, alias, 0, 0);
            });
            assertTrue(secondStarted.await(20, SECONDS));
            assertThrows(TimeoutException.class, () -> waiting.get(250, MILLISECONDS));
            assertEquals(0, secondComputations.get());

            release.countDown();
            assertEquals(61, computing.get(20, SECONDS).intValue());
            assertEquals(61, waiting.get(20, SECONDS).intValue());
            assertEquals(61, row[0]);
            assertEquals(0, secondComputations.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(20, SECONDS));
        }
    }

    private static String assertErasureDiagnostic(Executable operation, Class<?> type) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                operation);
        String message = exception.getMessage();
        assertTrue(message != null && message.contains(type.getSimpleName()),
                () -> "Missing interface name in: " + exception);
        String lower = message.toLowerCase(Locale.ROOT);
        assertTrue(lower.contains("type variable"), () -> message);
        assertTrue(lower.contains("erased") || lower.contains("erasure"), () -> message);
        return lower;
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void explicitOwnerAllowsRawGenericFieldShapes() {
        Holder holder = new Holder();
        Lazy.IntFieldAccessor intField = ofLocationUpdater(
                Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                Lazy.IntFieldAccessor.class, "count");
        assertEquals(13, intField.getOrCompute(receiver -> 13, holder));
        assertEquals(13, holder.count);

        Lazy.LongFieldAccessor longField = ofLocationUpdater(
                Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                Lazy.LongFieldAccessor.class, "large");
        assertEquals(17L, longField.getOrCompute(receiver -> 17L, holder));
        assertEquals(17L, holder.large);

        StaticHolder.referencePlain = null;
        Lazy.StaticReferenceFieldAccessor staticReference = ofLocationUpdater(
                Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                Lazy.StaticReferenceFieldAccessor.class,
                StaticHolder.class.getName() + ".referencePlain");
        assertEquals("static", staticReference.getOrCompute(() -> "static"));
        assertEquals("static", StaticHolder.referencePlain);

        Lazy.ReferenceFieldAccessor referenceField = ofLocationUpdater(
                Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                Lazy.ReferenceFieldAccessor.class, "text");
        assertEquals("instance", referenceField.getOrCompute(
                receiver -> "instance", holder));
        assertEquals("instance", holder.text);

        GenericIntField genericUseSite = ofLocationUpdater(
                Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                GenericIntField.class, "count");
        assertEquals(13, genericUseSite.get(receiver -> 99, holder));

        Holder another = new Holder();
        GenericDeclaredIntField genericDeclaration = ofLocationUpdater(
                Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP,
                GenericDeclaredIntField.class, "count",
                (GenericDeclaredIntField) receiver -> 3);
        assertEquals(3, genericDeclaration.get(another));
        assertEquals(3, another.count);
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void directArraysStillRequireConcreteGenericShapes() {
        String referenceArray = assertErasureDiagnostic(
                () -> ofArrayUpdater(
                        Lazy.UpdaterMode.PUBLISH_ONCE,
                        Lazy.ReferenceArrayAccessor.class),
                Lazy.ReferenceArrayAccessor.class);
        assertTrue(referenceArray.contains("extends referencearrayaccessor<"),
                () -> referenceArray);

        String genericUseSiteArray = assertErasureDiagnostic(
                () -> ofArrayUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, GenericIntArray.class),
                GenericIntArray.class);
        assertTrue(genericUseSiteArray.contains("non-generic interface")
                && genericUseSiteArray.contains("concrete parameter"),
                () -> genericUseSiteArray);

        String genericDeclarationArray = assertErasureDiagnostic(
                () -> ofArrayUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, GenericDeclaredIntArray.class,
                        (GenericDeclaredIntArray) (array, index) -> 3),
                GenericDeclaredIntArray.class);
        assertTrue(genericDeclarationArray.contains("non-generic interface")
                && genericDeclarationArray.contains("concrete parameter"),
                () -> genericDeclarationArray);
    }

    @Test
    public void hiddenArrayTypeVariablesStillExplainHowToAvoidErasure() {

        String methodLocal = assertErasureDiagnostic(
                () -> ofArrayUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, MethodGenericIntArray.class),
                MethodGenericIntArray.class);
        assertTrue(methodLocal.contains("non-generic interface")
                && methodLocal.contains("concrete parameter"), () -> methodLocal);

        String callback = assertErasureDiagnostic(
                () -> ofArrayUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, RawComputerIntArray.class),
                GenericArrayComputer.class);
        assertTrue(callback.contains("non-generic interface")
                && callback.contains("concrete parameter"), () -> callback);

        assertThrows(IllegalArgumentException.class,
                () -> ofArrayUpdater(Lazy.UpdaterMode.PUBLISH_ONCE,
                        FixedCallbackIntArray.class));
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void factoriesRejectInvalidUseSiteShapes() {
        assertThrows(NullPointerException.class,
                () -> ofArrayUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, (Class<IntCell>) null));
        assertThrows(NullPointerException.class,
                () -> ofArrayUpdater((Lazy.UpdaterMode) null, IntCell.class));
        assertThrows(NullPointerException.class,
                () -> ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE,
                        null, IntField.class, "count"));
        assertThrows(NullPointerException.class,
                () -> ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP, IntField.class, null));
        assertThrows(IllegalArgumentException.class,
                () -> ofArrayUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, NoComputer.class));
        assertThrows(IllegalArgumentException.class,
                () -> ofArrayUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, WrongComputerArguments.class));
        assertThrows(IllegalArgumentException.class,
                () -> ofArrayUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, VoidComputer.class));
        assertThrows(IllegalArgumentException.class,
                () -> ofArrayUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, TooManyIndices.class));
        assertThrows(IllegalArgumentException.class,
                () -> ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, LOOKUP, IntField.class, "missing"));
    }
}
