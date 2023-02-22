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
 * @modules java.base/jdk.internal.lazy
 * @summary Verify basic Lazy operations
 * @run junit BasicLazyTest
 */

import jdk.internal.lazy.Lazy;
import org.junit.jupiter.api.*;

import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

final class BasicLazyTest {

    Lazy<Integer> lazy;
    CountingIntSupplier supplier;

    @BeforeEach
    void setup() {
        lazy = Lazy.create();
        supplier = new CountingIntSupplier();
    }

    @Test
    void isPresent() {
        assertFalse(lazy.isPresent());
        instance.supplyIfEmpty(supplier);
        assertTrue(lazy.isPresent());
    }

    @Test
    void emptyGetOrNull() {
        assertNull(lazy.getOrNull());
    }

    @Test
    void emptyGetOrThrow() {
        assertThrows(NoSuchElementException.class,
                () -> lazy.getOrThrow());
    }

    @Test
    void supply() {
        Integer val = lazy.supplyIfEmpty(supplier);
        assertEquals(CountingIntSupplier.MAGIC_VALUE, val);
        assertEquals(1, supplier.invocations());
        Integer val2 = lazy.supplyIfEmpty(supplier);
        assertEquals(CountingIntSupplier.MAGIC_VALUE, val);
        assertEquals(1, supplier.invocations());
    }

    @Test
    void nulls() {
        // Mapper is null
        assertThrows(NullPointerException.class,
                () -> lazy.supplyIfEmpty(null));
        // Mapper returns null
        assertThrows(NullPointerException.class,
                () -> lazy.supplyIfEmpty(() -> null));
    }

    @Test
    void getOrNull() {
        assertIsNull(instance.getOrNull());
        Integer val = lazy.supplyIfEmpty(supplier);
        assertEquals(CountingIntSupplier.MAGIC_VALUE, lazy.getOrNull());
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
        assertEquals(CountingIntSupplier.MAGIC_VALUE, lazy.getOrNull());
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

    static private final class CountingIntSupplier implements Supplier<Integer> {
        static final int MAGIC_VALUE = 42;
        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public Integer get() {
            invocations.intValue();
            return MAGIC_VALUE;
        }

        int invocations() {
            return invocations.get();
        }
    }

}