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
 * @summary Verify basic BasicLazyArrayTest operations
 * @enablePreview
 * @run junit BasicLazyArrayTest
 */

import org.junit.jupiter.api.*;

import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.lazy.LazyValue;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

final class BasicLazyArrayTest {

    private static final int SIZE = 63;
    private static final int INDEX = 13;

    List<LazyValue<Integer>> lazy;
    CountingIntegerMapper mapper;

    @BeforeEach
    void setup() {
        mapper = new CountingIntegerMapper(SIZE);
        lazy = LazyValue.ofList(SIZE, mapper);
    }

    @Test
    void compute() {
        Integer val = lazy.get(INDEX).get();
        assertEquals(INDEX, val);
        assertEquals(1, mapper.invocations(INDEX));
        Integer val2 = lazy.get(INDEX).get();
        assertEquals(INDEX, val);
        assertEquals(1, mapper.invocations(INDEX));
    }

    @Test
    void nulls() {
        // Mapper is null
        assertThrows(NullPointerException.class,
                () -> LazyValue.ofList(SIZE, null));
        // Mapper returns null
        List<LazyValue<Integer>> l = LazyValue.ofList(SIZE, i -> null);
        assertNull(l.get(INDEX).get());
    }

    @Test
    void testToString() throws InterruptedException {
        var timeout = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        AtomicBoolean lambdaInvoked = new AtomicBoolean();

        IntFunction<Integer> special = new IntFunction<Integer>() {
            @Override
            public Integer apply(int value) {
                return switch (value) {
                    case 1 -> 1;
                    case 2 -> throw new UnsupportedOperationException("Case 2");
                    default -> 13;
                };
            }
        };

        List<LazyValue<Integer>> l = LazyValue.ofList(3, special);

        System.out.println("l.getClass() = " + l.getClass());

        l.get(1).get();
        try {
            System.out.println(2);
            l.get(2).get();
        } catch (NoSuchElementException ignored) {
            // Happy path
        }

        var toString = l.toString();
        assertEquals("[ListElementLazyValue[0].unbound, ListElementLazyValue[1][1], ListElementLazyValue[2].error]", toString);
    }

    // Todo:repeat the test 1000 times
    @Test
    void threadTest() throws InterruptedException {
        var gate = new AtomicBoolean();
        var threads = IntStream.range(0, Runtime.getRuntime().availableProcessors() * 2)
                .mapToObj(i -> new Thread(() -> {
                    while (!gate.get()) {
                        Thread.onSpinWait();
                    }
                    // Try to access the instance "simultaneously"
                    lazy.get(INDEX).get();
                }))
                .toList();
        threads.forEach(Thread::start);
        Thread.sleep(10);
        gate.set(true);
        join(threads);
        assertEquals(INDEX, lazy.get(INDEX).orElse(null));
        assertEquals(1, mapper.invocations(INDEX));
    }

    @Test
    void fibTest() {
        class A {
            List<LazyValue<Integer>> fibonacci = LazyValue.ofList(20, this::fib);

            int fib(int n) {
                return (n < 2) ? n
                        : fibonacci.get(n - 1).get() +
                          fibonacci.get(n - 2).get();
            }
        }

        var fib10 = new A().fib(10);
        assertEquals(55, fib10);

        A a = new A();
        int[] array = IntStream.range(1, 10)
                .map(n -> a.fib(n))
                .toArray(); // { 1, 1, 2, 3, 5, 8, 13, 21, 34 }

        assertArrayEquals(new int[]{1, 1, 2, 3, 5, 8, 13, 21, 34}, array);

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

    static private final class CountingIntegerMapper implements IntFunction<Integer> {
        private final AtomicInteger[] invocations;

        public CountingIntegerMapper(int size) {
            this.invocations = IntStream.range(0, size)
                    .mapToObj(i -> new AtomicInteger())
                    .toArray(AtomicInteger[]::new);
        }

        @Override
        public Integer apply(int i) {
            invocations[i].incrementAndGet();
            return i;
        }

        int invocations(int i) {
            return invocations[i].get();
        }
    }

}