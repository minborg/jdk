/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @summary Basic tests for making sure cached methods publishes values safely
 * @modules java.base/jdk.internal.misc
 * @modules java.base/jdk.internal.lang
 * @enablePreview
 */

import jdk.internal.lang.LazyConstantImpl;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.LazyConstant;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class SafePublicationTest {

    private static final int SIZE = 100_000;
    private static final int THREADS = Math.max(2, Runtime.getRuntime().availableProcessors());
    private static final Set<Holder> HOLDERS_SEEN = Collections.newSetFromMap(new ConcurrentHashMap<>());

    static final class Holder {
        // These are non-final fields but should be seen
        // fully initialized thanks to the HB properties of ComputedConstants.
        int a, b, c, d, e;

        Holder() {
            a = b = c = d = e = 1;
        }
    }

    static final class Lazy {

        cached Holder holder() {
            return new Holder();
        }
    }

    static final class Consumer implements Runnable {

        final Lazy[] constants;
        final int[] observations = new int[SIZE];
        int i = 0;

        public Consumer(Lazy[] constants) {
            this.constants = constants;
        }

        @Override
        public void run() {
            for (; i < SIZE; i++) {
                Holder h = constants[i].holder();
                int a = h.a;
                int b = h.b;
                int c = h.c;
                int d = h.d;
                int e = h.e;
                observations[i] = a + (b << 1) + (c << 2) + (c << 3) + (d << 4) + (e << 5);
                HOLDERS_SEEN.add(h);
            }
        }
    }

    public static void main(String[] args) {

        final Lazy[] constants = Stream.generate(Lazy::new)
                .limit(SIZE)
                .toArray(Lazy[]::new);

        List<Consumer> consumers = IntStream.range(0, THREADS)
                .mapToObj(_ -> new Consumer(constants))
                .toList();

        List<Thread> consumersThreads = IntStream.range(0, THREADS)
                .mapToObj(i -> Thread.ofPlatform()
                        .name("Consumer Thread " + i)
                        .unstarted(consumers.get(i)))
                .toList();

        // Start the race
        consumersThreads.forEach(Thread::start);

        join(constants, consumers, consumersThreads.toArray(Thread[]::new));

        int[] histogram = new int[64];
        for (Consumer consumer : consumers) {
            for (int i = 0; i < SIZE; i++) {
                histogram[consumer.observations[i]]++;
            }
        }

        // unless a = 1, ..., e = 1, zero observations should be seen
        for (int i = 0; i < 63; i++) {
            assertEquals(0, histogram[i]);
        }
        // a = 1, ..., e = 1 : index 2^5-1 = 63
        // All observations should end up in this bucket
        assertEquals(THREADS * SIZE, histogram[63]);

        // Check that the holers were actually cached.
        assertEquals(SIZE, HOLDERS_SEEN.size());
    }

    static void join(final Lazy[] constants, List<Consumer> consumers, Thread... threads) {
        try {
            for (Thread t:threads) {
                long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(1);
                while (t.isAlive()) {
                    t.join(TimeUnit.SECONDS.toMillis(10));
                    if (t.isAlive()) {
                        String stack = Arrays.stream(t.getStackTrace())
                                .map(Objects::toString)
                                .collect(Collectors.joining(System.lineSeparator()));
                        System.err.println(t + ": " + stack);
                        for (int i = 0; i < consumers.size(); i++) {
                            System.err.println("Consumer " + i + ": " + consumers.get(i).i);
                        }
                    }
                    if (System.nanoTime() > deadline) {
                        fail("Giving up!");
                    }
                }
            }
        } catch (InterruptedException ie) {
            fail(ie);
        }
    }

    static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("expected " + expected + ", got " + actual);
        }
    }

    static void fail(String msg) {
        throw new AssertionError(msg);
    }

    static void fail(Exception e) {
        throw new AssertionError(e);
    }

}
