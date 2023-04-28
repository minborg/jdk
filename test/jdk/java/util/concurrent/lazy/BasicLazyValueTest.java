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
 * @summary Verify basic LazyValue operations
 * @enablePreview
 * @run junit BasicLazyValueTest
 */

import org.junit.jupiter.api.*;

import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.lazy.LazyValue;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class BasicLazyValueTest {

    @TestFactory
    Stream<DynamicTest> get() {
        return DynamicTest.stream(lazyVariants(), LazyVariant::name, (LazyVariant lv) -> {
            Integer val = lv.lazyValue().get();
            assertEquals(CountingIntegerSupplier.MAGIC_VALUE, val);
            assertEquals(1, lv.supplier().invocations());
            Integer val2 = lv.lazyValue().get();
            assertEquals(CountingIntegerSupplier.MAGIC_VALUE, val);
            assertEquals(1, lv.supplier().invocations());
        });
    }

    @TestFactory
    Stream<DynamicTest> nulls() {
        return DynamicTest.stream(lazyVariants(), LazyVariant::name, (LazyVariant lv) -> {
            // Mapper is null
            assertThrows(NullPointerException.class,
                    () -> lv.constructor().apply(null));
            // Mapper returns null
            LazyValue<Integer> l = lv.constructor().apply(() -> null);
            assertNull(l.get());
        });
    }

/*    @TestFactory
    Stream<DynamicTest> state() {
        return DynamicTest.stream(lazyVariants(), LazyVariant::name, (LazyVariant lv) -> {
            assertEquals(LazyState.EMPTY, lv.lazyValue().state());
            Integer val = lv.lazyValue().get();
            assertEquals(LazyState.PRESENT, lv.lazyValue().state());
        });
    }*/

    @TestFactory
    Stream<DynamicTest> presetSupplierBasic() {
        return DynamicTest.stream(lazyVariants(), LazyVariant::name, (LazyVariant lv) -> {
            for (int i = 0; i < 2; i++) {
                assertEquals(CountingIntegerSupplier.MAGIC_VALUE, lv.lazyValue().get());
                assertEquals(1, lv.supplier().invocations());
            }
        });
    }

    @Test
    void optionalModelling() {
        Supplier<Optional<String>> empty = LazyValue.of(() -> Optional.empty());
        assertTrue(empty.get().isEmpty());
        Supplier<Optional<String>> present = LazyValue.of(() -> Optional.of("A"));
        assertEquals("A", present.get().orElseThrow());
    }

    @TestFactory
    Stream<DynamicTest> error() {
        return DynamicTest.stream(lazyVariants(), LazyVariant::name, (LazyVariant lv) -> {
            Supplier<Integer> throwingSupplier = () -> {
                throw new UnsupportedOperationException();
            };
            LazyValue<Integer> l = lv.constructor().apply(throwingSupplier);
            assertThrows(UnsupportedOperationException.class,
                    () -> l.get());

            // Should invoke the supplier again
            assertThrows(UnsupportedOperationException.class,
                    () -> l.get());

        });
    }

    // Todo:repeate the test 1000 times
    @TestFactory
    Stream<DynamicTest> threadTest() throws InterruptedException {
        return DynamicTest.stream(lazyVariants(), LazyVariant::name, (LazyVariant lv) -> {
            var gate = new AtomicBoolean();
            var threads = IntStream.range(0, Runtime.getRuntime().availableProcessors() * 2)
                    .mapToObj(i -> new Thread(() -> {
                        while (!gate.get()) {
                            Thread.onSpinWait();
                        }
                        // Try to access the instance "simultaneously"
                        lv.lazyValue().get();
                    }))
                    .toList();
            threads.forEach(Thread::start);
            Thread.sleep(10);
            gate.set(true);
            join(threads);
            assertEquals(CountingIntegerSupplier.MAGIC_VALUE, lv.lazyValue().get());
            assertEquals(1, lv.supplier().invocations());
        });
    }

    @TestFactory
    Stream<DynamicTest> testToString() throws InterruptedException {
        return DynamicTest.stream(lazyVariants(), LazyVariant::name, (LazyVariant lv) -> {
            var lazy0 = lv.constructor().apply(() -> 0);
            var lazy1 = lv.constructor().apply(() -> 1);
            lazy1.get();

            // Do not touch lazy0
            lazy1.get();
            if (lv.name().contains("Value")) {
                assertEquals(lazy0.getClass().getSimpleName() + "[0]", lazy0.toString());
            } else {
                assertEquals(lazy0.getClass().getSimpleName() + ".unbound", lazy0.toString());
            }
            assertEquals(lazy0.getClass().getSimpleName()+"[1]", lazy1.toString());
        });
    }

    @TestFactory
    Stream<DynamicTest> testCircular() {
        return DynamicTest.stream(lazyVariants(), LazyVariant::name, lv -> {
            staticLazyValue = LazyValue.of(() -> staticLazyValue.get());
            assertThrows(IllegalStateException.class, () -> {
                staticLazyValue.get();
            });
        });
    }

    private static LazyValue<Integer> staticLazyValue;


    private static Stream<LazyVariant> lazyVariants() {
        return Stream.of(
                        new NamedConstructor("Lazy::of(Supplier)", LazyValue::of),
                        new NamedConstructor("Lazy::of(Value)", s -> LazyValue.of(s.get())))
                .map(nc -> {
                    var supplier = new CountingIntegerSupplier();
                    var lazy = nc.constructor().apply(supplier);
                    return new LazyVariant(nc.name(), nc.constructor(), lazy, supplier);
                });
    }

    private record NamedConstructor(String name,
                                    Function<Supplier<Integer>, LazyValue<Integer>> constructor){

    }

    private record LazyVariant(String name,
                              Function<Supplier<Integer>, LazyValue<Integer>> constructor,
                              LazyValue<Integer> lazyValue,
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

}
