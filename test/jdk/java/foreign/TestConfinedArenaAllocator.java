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
 * @run testng/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                   --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                   TestConfinedArenaAllocator
 */

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Field;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.*;

public class TestConfinedArenaAllocator {

    static final Field THREAD_ALLOCATOR_FIELD;
    static final Field BACKING_ARENA_FIELD;

    static {
        try {
            THREAD_ALLOCATOR_FIELD = Thread.class.getDeclaredField("confinedArenaAllocator");
            THREAD_ALLOCATOR_FIELD.setAccessible(true);
            Class<?> allocatorClass = Class.forName("jdk.internal.foreign.ThreadConfinedArenaAllocator");
            BACKING_ARENA_FIELD = allocatorClass.getDeclaredField("backingArena");
            BACKING_ARENA_FIELD.setAccessible(true);
        } catch (ReflectiveOperationException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @Test(dataProvider = "threadFactories")
    public void testThreadLocalAllocator(String name, ThreadFactory threadFactory) throws Throwable {
        AtomicReference<Object> allocatorRef = new AtomicReference<>();
        AtomicReference<Throwable> failureRef = new AtomicReference<>();
        Thread thread = threadFactory.newThread(() -> {
            try {
                assertNull(threadAllocator(Thread.currentThread()));

                long firstAddress;
                try (Arena arena = Arena.ofConfined()) {
                    Object allocator = threadAllocator(Thread.currentThread());
                    assertNotNull(allocator);
                    assertNull(backingArena(allocator));
                    allocatorRef.set(allocator);

                    MemorySegment segment = arena.allocate(ValueLayout.JAVA_LONG);
                    firstAddress = segment.address();
                    segment.set(ValueLayout.JAVA_LONG, 0, -1L);
                    assertNotNull(backingArena(allocator));
                }

                try (Arena arena = Arena.ofConfined()) {
                    MemorySegment segment = arena.allocate(ValueLayout.JAVA_LONG);
                    assertEquals(segment.address(), firstAddress);
                    assertEquals(segment.get(ValueLayout.JAVA_LONG, 0), 0L);
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
        assertNull(threadAllocator(thread), name);
        assertNull(backingArena(allocatorRef.get()), name);
    }

    @DataProvider
    public Object[][] threadFactories() {
        return new Object[][] {
                { "platform", (ThreadFactory) Thread::new },
                { "virtual", (ThreadFactory) task -> Thread.ofVirtual().unstarted(task) }
        };
    }

    static Object threadAllocator(Thread thread) throws ReflectiveOperationException {
        return THREAD_ALLOCATOR_FIELD.get(thread);
    }

    static Object backingArena(Object allocator) throws ReflectiveOperationException {
        return BACKING_ARENA_FIELD.get(allocator);
    }
}
