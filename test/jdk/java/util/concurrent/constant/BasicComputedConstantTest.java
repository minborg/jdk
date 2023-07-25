/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Verify basic ComputedConstant operations
 * @enablePreview
 * @run junit BasicComputedConstantTest
 */

import org.junit.jupiter.api.*;

import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.constant.ComputedConstant;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class BasicComputedConstantTest {

    @TestFactory
    Stream<DynamicTest> get() {
        return DynamicTest.stream(computedConstantVariants(), ComputedConstantVariant::name, (ComputedConstantVariant lv) -> {
            Integer val = lv.computedConstant().get();
            assertEquals(CountingIntegerSupplier.MAGIC_VALUE, val);
            assertEquals(1, lv.supplier().invocations());
            Integer val2 = lv.computedConstant().get();
            assertEquals(CountingIntegerSupplier.MAGIC_VALUE, val2);
            assertEquals(1, lv.supplier().invocations());
        });
    }

    @TestFactory
    Stream<DynamicTest> nulls() {
        return DynamicTest.stream(computedConstantVariants(), ComputedConstantVariant::name, (ComputedConstantVariant lv) -> {
            // Mapper is null
            assertThrows(NullPointerException.class,
                    () -> lv.constructor().apply(null));
            // Mapper returns null
            ComputedConstant<Integer> l = lv.constructor().apply(() -> null);
            assertNull(l.get());
        });
    }

    @TestFactory
    Stream<DynamicTest> presetSupplierBasic() {
        return DynamicTest.stream(computedConstantVariants(), ComputedConstantVariant::name, (ComputedConstantVariant lv) -> {
            for (int i = 0; i < 2; i++) {
                assertEquals(CountingIntegerSupplier.MAGIC_VALUE, lv.computedConstant().get());
                assertEquals(1, lv.supplier().invocations());
            }
        });
    }

    @Test
    void optionalModelling() {
        Supplier<Optional<String>> empty = ComputedConstant.of(Optional::empty);
        assertTrue(empty.get().isEmpty());
        Supplier<Optional<String>> present = ComputedConstant.of(() -> Optional.of("A"));
        assertEquals("A", present.get().orElseThrow());
    }

    @TestFactory
    Stream<DynamicTest> error() {
        return DynamicTest.stream(computedConstantVariants().filter(lv -> !isOfValue(lv)), ComputedConstantVariant::name, (ComputedConstantVariant lv) -> {
            Supplier<Integer> throwingSupplier = () -> {
                throw new UnsupportedOperationException();
            };
            ComputedConstant<Integer> l = lv.constructor().apply(throwingSupplier);
            assertThrows(NoSuchElementException.class,
                    () -> l.get());

            // Should not invoke the supplier again
            assertThrows(NoSuchElementException.class,
                    () -> l.get());

        });
    }

    @Test
    void bind() {
        ComputedConstant<Integer> constant = ComputedConstant.ofEmpty();
        assertTrue(constant.isUnbound());
        assertFalse(constant.isBinding());
        assertFalse(constant.isBound());
        assertFalse(constant.isError());
        constant.bind(42);
        assertEquals(42, constant.get());
        assertFalse(constant.isUnbound());
        assertFalse(constant.isBinding());
        assertTrue(constant.isBound());
        assertFalse(constant.isError());
    }

    @Test
    void bindNull() {
        ComputedConstant<Integer> constant = ComputedConstant.ofEmpty();
        assertTrue(constant.isUnbound());
        assertFalse(constant.isBinding());
        assertFalse(constant.isBound());
        assertFalse(constant.isError());
        constant.bind(null);
        assertNull(constant.get());
        assertFalse(constant.isUnbound());
        assertFalse(constant.isBinding());
        assertTrue(constant.isBound());
        assertFalse(constant.isError());
    }

    @Test
    void computeIfUnbound() {
        ComputedConstant<Integer> constant = ComputedConstant.ofEmpty();
        int actual = constant.computeIfUnbound(() -> 42);
        assertEquals(42, actual);
        assertEquals(42, constant.get());
        assertFalse(constant.isUnbound());
        assertFalse(constant.isBinding());
        assertTrue(constant.isBound());
        assertFalse(constant.isError());
    }

    @Test
    void computeIfUnboundNull() {
        ComputedConstant<Integer> constant = ComputedConstant.ofEmpty();
        Integer actual = constant.computeIfUnbound(() -> null);
        assertNull(actual);
        assertNull(constant.get());
        assertFalse(constant.isUnbound());
        assertFalse(constant.isBinding());
        assertTrue(constant.isBound());
        assertFalse(constant.isError());
    }

    // Todo:repeat the test 1000 times
    @TestFactory
    Stream<DynamicTest> threadTest() throws InterruptedException {
        return DynamicTest.stream(computedConstantVariants(), ComputedConstantVariant::name, (ComputedConstantVariant lv) -> {
            var gate = new AtomicBoolean();
            var threads = IntStream.range(0, Runtime.getRuntime().availableProcessors() * 2)
                    .mapToObj(i -> new Thread(() -> {
                        while (!gate.get()) {
                            Thread.onSpinWait();
                        }
                        // Try to access the instance "simultaneously"
                        lv.computedConstant().get();
                    }))
                    .toList();
            threads.forEach(Thread::start);
            Thread.sleep(10);
            gate.set(true);
            join(threads);
            assertEquals(CountingIntegerSupplier.MAGIC_VALUE, lv.computedConstant().get());
            assertEquals(1, lv.supplier().invocations());
        });
    }

    @TestFactory
    Stream<DynamicTest> testToString() throws InterruptedException {
        return DynamicTest.stream(computedConstantVariants(), ComputedConstantVariant::name, (ComputedConstantVariant lv) -> {
            var c0 = lv.constructor().apply(() -> 0);
            var c1 = lv.constructor().apply(() -> 1);

            c1.get();


            // Do not touch c0
            c1.get();
            if (isOfValue(lv)) {
                assertEquals(c0.getClass().getSimpleName() + "[0]", c0.toString());
            } else {
                assertEquals(c0.getClass().getSimpleName() + ".unbound", c0.toString());
            }
            assertEquals(c0.getClass().getSimpleName()+"[1]", c1.toString());

            if (!isOfValue(lv)) {
                ComputedConstant<Integer> c2 = lv.constructor().apply(() -> {
                    throw new UnsupportedOperationException();
                });
                try {
                    c2.get();
                } catch (NoSuchElementException ignore) {
                }
                assertEquals(c0.getClass().getSimpleName() + ".error", c2.toString());
            }

        });
    }

    @TestFactory
    Stream<DynamicTest> testCircular() {
        return DynamicTest.stream(computedConstantVariants(), ComputedConstantVariant::name, lv -> {
            staticComputedConstant = ComputedConstant.of(() -> staticComputedConstant.get());
            assertThrows(StackOverflowError.class, () -> {
                staticComputedConstant.get();
            });
        });
    }

    private static ComputedConstant<Integer> staticComputedConstant;

    private static ComputedConstant<Integer> a;
    private static ComputedConstant<Integer> b;

    @TestFactory
    Stream<DynamicTest> testCircular2() {
        return DynamicTest.stream(computedConstantVariants(), ComputedConstantVariant::name, lv -> {
            a = ComputedConstant.of(() -> b.get());
            b = ComputedConstant.of(() -> a.get());
            assertThrows(StackOverflowError.class, () -> {
                a.get();
            });
        });
    }

    private static Stream<ComputedConstantVariant> computedConstantVariants() {
        return Stream.of(
                        new NamedConstructor("ComputedConstant::of(Supplier)", ComputedConstant::of),
                        new NamedConstructor("ComputedConstant::of(Value)", s -> ComputedConstant.of(s.get())))
                .map(nc -> {
                    var supplier = new CountingIntegerSupplier();
                    var constant = nc.constructor().apply(supplier);
                    return new ComputedConstantVariant(nc.name(), nc.constructor(), constant, supplier);
                });
    }

    private record NamedConstructor(String name,
                                    Function<Supplier<Integer>, ComputedConstant<Integer>> constructor){

    }

    private record ComputedConstantVariant(String name,
                                           Function<Supplier<Integer>, ComputedConstant<Integer>> constructor,
                                           ComputedConstant<Integer> computedConstant,
                                           CountingIntegerSupplier supplier) {
    }


    private static void join(Collection<Thread> threads) {
        for (var t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
    }

    static private final class CountingIntegerSupplier implements Supplier<Integer> {
        static final int MAGIC_VALUE = 42;
        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public Integer get() {
            invocations.incrementAndGet();
            return MAGIC_VALUE;
        }

        int invocations() {
            return invocations.get();
        }
    }

    private boolean isOfValue(ComputedConstantVariant lv) {
        return lv.name().contains("Value");
    }

}
