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
 * @summary Verify basic LazyLong operations
 * @run junit BasicLazyLongTest
 */

import org.junit.jupiter.api.*;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.lazy.Lazy;
import java.util.concurrent.lazy.LazyLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

final class BasicLazyLongTest {

    LazyLong lazy;
    CountingLongSupplier supplier;

    @BeforeEach
    void setup() {
        lazy = LazyLong.ofEmpty();
        supplier = new CountingLongSupplier();
    }

    @Test
    void supply() {
        long val = lazy.supplyIfEmpty(supplier);
        assertEquals(CountingLongSupplier.MAGIC_VALUE, val);
        assertEquals(1, supplier.invocations());
        long val2 = lazy.supplyIfEmpty(supplier);
        assertEquals(CountingLongSupplier.MAGIC_VALUE, val);
        assertEquals(1, supplier.invocations());
    }

    @Test
    void nulls() {
        // Mapper is null
        assertThrows(NullPointerException.class,
                () -> lazy.supplyIfEmpty(null));
    }

    @Test
    void noPresetGet() {
        assertThrows(IllegalStateException.class,
                () -> lazy.getAsLong());
    }

    @Test
    void state() {
        assertEquals(Lazy.State.EMPTY, lazy.state());
        long val = lazy.supplyIfEmpty(supplier);
        assertEquals(CountingLongSupplier.MAGIC_VALUE, val);
        assertEquals(Lazy.State.PRESENT, lazy.state());
    }

    @Test
    void presetSupplierBasic() {
        LazyLong presetLazy = LazyLong.of(supplier);
        assertEquals(Lazy.State.EMPTY, presetLazy.state());
        assertEquals(0, supplier.invocations());
        for (int i = 0; i < 2; i++) {
            assertEquals(CountingLongSupplier.MAGIC_VALUE, presetLazy.getAsLong());
            assertEquals(1, supplier.invocations());
        }
    }

    @Test
    void presetSupplierNullSuppying() {
        // Mapper is null
        assertThrows(NullPointerException.class,
                () -> LazyLong.of(null));
    }

    // Todo:repeate the test 1000 times
    @Test
    void threadTest() throws InterruptedException {
        var gate = new AtomicBoolean();
        var threads = IntStream.range(0, Runtime.getRuntime().availableProcessors() * 2)
                .mapToObj(i -> new Thread(()->{
                    while (!gate.get()) {
                        Thread.onSpinWait();
                    }
                    // Try to access the instance "simultaneously"
                    lazy.supplyIfEmpty(supplier);
                }))
                .toList();
        threads.forEach(Thread::start);
        Thread.sleep(10);
        gate.set(true);
        join(threads);
        assertEquals(CountingLongSupplier.MAGIC_VALUE, lazy.getAsLong());
        assertEquals(1, supplier.invocations());
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

    static private final class CountingLongSupplier implements LongSupplier {
        static final long MAGIC_VALUE = 42L;
        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public long getAsLong() {
            invocations.incrementAndGet();
            return MAGIC_VALUE;
        }

        int invocations() {
            return invocations.get();
        }
    }

}
