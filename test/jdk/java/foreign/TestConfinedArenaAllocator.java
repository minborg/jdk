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
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    TestConfinedArenaAllocator
 */

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Field;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;

import static org.junit.Assert.*;

final class TestConfinedArenaAllocator {

    static final Field THREAD_ALLOCATOR_FIELD;
    static final Field STACK_FIELD;
    static final Field STACK_ARENA_FIELD;

    static {
        try {
            THREAD_ALLOCATOR_FIELD = Thread.class.getDeclaredField("confinedArenaAllocator");
            THREAD_ALLOCATOR_FIELD.setAccessible(true);
            Class<?> allocatorClass = Class.forName("jdk.internal.foreign.ThreadConfinedArenaAllocator");
            STACK_FIELD = allocatorClass.getDeclaredField("stack");
            STACK_FIELD.setAccessible(true);
            Class<?> perThreadClass = Class.forName("jdk.internal.foreign.BufferStack$PerThread");
            STACK_ARENA_FIELD = perThreadClass.getDeclaredField("arena");
            STACK_ARENA_FIELD.setAccessible(true);
        } catch (ReflectiveOperationException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @ParameterizedTest
    @MethodSource("threadFactories")
    void testThreadLocalAllocator(String name, Thread.Builder threadBuilder) throws Throwable {
        AtomicReference<Object> allocatorRef = new AtomicReference<>();
        AtomicReference<Throwable> failureRef = new AtomicReference<>();
        Thread thread = threadBuilder.factory().newThread(() -> {
            try {
                assertNull(threadAllocator(Thread.currentThread()));

                long firstAddress;
                try (Arena arena = Arena.ofConfined()) {
                    assertEquals("Frame", arena.getClass().getSimpleName());
                    Object allocator = threadAllocator(Thread.currentThread());
                    assertNotNull(allocator);
                    assertTrue(backingArena(allocator).scope().isAlive());
                    allocatorRef.set(allocator);

                    MemorySegment firstSegment = arena.allocate(ValueLayout.JAVA_LONG);
                    MemorySegment secondSegment = arena.allocate(ValueLayout.JAVA_LONG);
                    firstAddress = firstSegment.address();
                    assertEquals(secondSegment.address(), firstAddress + ValueLayout.JAVA_LONG.byteSize());
                    firstSegment.set(ValueLayout.JAVA_LONG, 0, -1L);
                    secondSegment.set(ValueLayout.JAVA_LONG, 0, -1L);
                }

                try (Arena arena = Arena.ofConfined()) {
                    assertEquals("Frame", arena.getClass().getSimpleName());
                    MemorySegment firstSegment = arena.allocate(ValueLayout.JAVA_LONG);
                    MemorySegment secondSegment = arena.allocate(ValueLayout.JAVA_LONG);
                    assertEquals(firstSegment.address(), firstAddress);
                    assertEquals(secondSegment.address(), firstAddress + ValueLayout.JAVA_LONG.byteSize());
                    assertEquals(firstSegment.get(ValueLayout.JAVA_LONG, 0), 0L);
                    assertEquals(secondSegment.get(ValueLayout.JAVA_LONG, 0), 0L);
                }
            } catch (Throwable ex) {
                failureRef.set(ex);
            }
        });

        thread.start();
        thread.join();

        if (failureRef.get() != null) {
            throw failureRef.get();
        }

        if (thread.isVirtual()) {
            // Give the virtual thread some time to clean up
            long timeOut = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (System.nanoTime() < timeOut) {
                if (threadAllocator(thread) == null) {
                    break;
                }
                LockSupport.parkNanos(1_000_000L);
            }
        }

        assertNull(name, threadAllocator(thread));
        assertFalse(name, backingArena(allocatorRef.get()).scope().isAlive());
    }

    static Stream<Arguments> threadFactories() {
        return Stream.of(
                Arguments.of("platform", Thread.ofPlatform()),
                Arguments.of("virtual", Thread.ofVirtual()));
    }

    static Object threadAllocator(Thread thread) throws ReflectiveOperationException {
        return THREAD_ALLOCATOR_FIELD.get(thread);
    }

    static Arena backingArena(Object allocator) throws ReflectiveOperationException {
        return (Arena) STACK_ARENA_FIELD.get(STACK_FIELD.get(allocator));
    }
}
