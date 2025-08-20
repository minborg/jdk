/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib
 * @modules java.base/jdk.internal.foreign
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestAccessToken
 */

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.*;

final class TestAccessToken {

    private static final VarHandle INT_HANDLE = JAVA_INT.varHandle();

    record Producer(MemorySegment segment) implements Runnable {

        @Override
        public void run() {
            // segment.set(JAVA_INT, 0, 42) but volatile
            INT_HANDLE.setVolatile(segment, 0, 42);
        }
    }

    record Consumer(MemorySegment segment) implements Runnable {
        @Override
        public void run() {
            int val;
            // while ((val = segment.get(JAVA_INT, 0)) != 0) { but volatile
            while ((val = (int) INT_HANDLE.getVolatile(segment, 0)) == 0) {
                Thread.onSpinWait();
            }
            assertEquals(42, val);
        }
    }

    static final class Init implements Runnable {
        private Arena arena;
        private MemorySegment segment;

        @Override
        public void run() {
            if (arena == null) {
                arena = Arena.ofConfined(); // Confined to the *access token*
                segment = arena.allocate(JAVA_INT);
            } else {
                arena.close();
            }
        }

        public MemorySegment segment() {
            return segment;
        }

    }

    @Test
    void producerConsumer() {
        var factory = Thread.ofPlatform().accessToken(1).factory();

        //var factory = Thread.ofPlatform().withAccessToken().factory();

        var init = new Init();
        var initThread = factory.newThread(init);
        initThread.start();
        join(initThread);

        var consumer = new Consumer(init.segment());
        var consumerThread = factory.newThread(consumer);
        var producer = new Producer(init.segment());
        var producerThread = factory.newThread(producer);

        consumerThread.start();
        producerThread.start();

        join(consumerThread, producerThread);

        var closeThread = factory.newThread(init);
        closeThread.start();
        join(closeThread);
    }

    // Todo: There needs to be some kind of protection against access token spoofing.
    //       One idea is that a builder/factory gets an automatic access token that cannot
    //       be set. Maybe Thread should not have a public token accessor.

    private static final int CHILDREN = 4;

    @Test
    void structuredConcurrencyPlatformThread() {
        structuredConcurrency0(Thread.ofPlatform());
    }

    @Test
    void structuredConcurrencyVirtualThread() {
        structuredConcurrency0(Thread.ofVirtual());
    }

    void structuredConcurrency0(Thread.Builder builder) {

        // This factory provides Thread instances with an access token that is 1
        var factory = builder.accessToken(1).factory();

        AtomicReference<Arena> confined = new AtomicReference<>(); // Now confined to an access token rather than a thread.
        Map<Integer, MemorySegment> segments = new ConcurrentHashMap<>();
        Thread[] c = new Thread[CHILDREN];

        var parent = factory.newThread(() -> {

            // Now confined to an access token rather than a thread.
            confined.set(Arena.ofConfined());

            for (int i = 0; i < CHILDREN; i++) {
                final int iFinal = i;
                c[i] = factory.newThread(() -> segments.put(iFinal, confined.get().allocate(8)));
                c[i].start();
            }
            join(c);
        });

        parent.start();
        join(parent);

        for (int i = 0; i < CHILDREN; i++) {
            var segment = segments.get(i);
            assertTrue(segment.isAccessibleBy(parent));
            assertFalse(segment.isAccessibleBy(Thread.currentThread())); // The main thread
            for (int j = 0; j < CHILDREN; j++) {
                assertTrue(segment.isAccessibleBy(c[j]));
            }
        }
    }

    private static void join(Thread... threads) {
        try {
            for (Thread t: threads) {
                t.join();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
