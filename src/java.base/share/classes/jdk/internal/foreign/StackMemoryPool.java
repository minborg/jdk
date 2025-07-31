/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.internal.foreign;

import jdk.internal.misc.CarrierThreadLocal;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryPool;
import java.lang.foreign.MemorySegment;
import java.lang.ref.Reference;

/**
 * A buffer stack that allows efficient reuse of memory segments. This is useful in cases
 * where temporary memory is needed.
 */
public record StackMemoryPool(long byteSize,
                              long byteAlignment,
                              CarrierThreadLocal<PerPlatformThread> tl) implements MemoryPool {

    StackMemoryPool(long byteSize, long byteAlignment) {
        this(byteSize, byteAlignment, new CarrierThreadLocal<>() {
            @Override
            protected PerPlatformThread initialValue() {
                // Todo: Check if this method is always run using the current thread.
                return Thread.currentThread().isVirtual()
                        ? PerVirtualThread.of(byteSize, byteAlignment)
                        : PerPlatformThread.of(byteSize, byteAlignment);
            }
        });
    }

    @ForceInline
    @Override
    public Arena get() {
        return tl.get()
                .get();
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public String toString() {
        return "StackMemoryPool[byteSize=" + byteSize + ", byteAlignment=" + byteAlignment + "]";
    }

    public static sealed class PerPlatformThread implements MemoryPool {

        private final SlicingAllocator allocator;
        final CleanupAction cleanupAction;
        private long lastPos;

        private PerPlatformThread(long byteSize, long byteAlignment) {
            final Arena arena = Arena.ofAuto();
            allocator = new SlicingAllocator(arena.allocate(byteSize, byteAlignment));
            cleanupAction = new CleanupAction(arena);
        }

        @ForceInline
        @Override
        public Arena get() {
            return new Frame(this, lastPos = allocator.currentOffset());
        }

        @ForceInline
        void pop(long initialFramePos) {
            if (initialFramePos < lastPos) {
                throw new IllegalStateException("The stacked arena was closed out of sequence.");
                // Todo: How do we recover from this situation? "replace=true" for all arenas in the stack?
            }
            lastPos = initialFramePos;
            allocator.resetTo(initialFramePos);
        }

        static PerPlatformThread of(long byteSize, long byteAlignment) {
            return new PerPlatformThread(byteSize, byteAlignment);
        }

    }

    public static final class PerVirtualThread extends PerPlatformThread implements MemoryPool {

        private final ThreadLocalLock lock;

        private PerVirtualThread(long byteSize, long byteAlignment) {
            super(byteSize, byteAlignment);
            lock = new ThreadLocalLock();
        }

        @Override
        public Arena get() {
            // Upon first or fresh use, acquire the lock so that other VTs cannot use
            // pooled memory in case they get scheduled on the same PT.
            return lock.tryLock()
                    // Likely: We own the lock so we can use the pooled memory.
                    ? super.get()
                    // Rare: some other VT on the same PT have outstanding allocations
                    // so, we have to fall back to using a non-pooled Arena.
                    : Arena.ofConfined();
        }

        @Override
        void pop(long initialFramePos) {
            super.pop(initialFramePos);
            if (initialFramePos == 0) {
                // All allocations have been returned. Release the lock so that other
                // VT can use the pool.
                lock.unlock();
            }
        }

        static PerPlatformThread of(long byteSize, long byteAlignment) {
            return new PerVirtualThread(byteSize, byteAlignment);
        }

    }

    private record Frame(PerPlatformThread inner,
                         long initialPos,
                         Arena arena) implements Arena {

        @ForceInline
        private Frame(PerPlatformThread inner, long initialPos) {
            this(inner, initialPos, Arena.ofConfined());
            // We know the arena is alive, the thread is the owner thread, and the
            // `cleanupAction` is non-null
            ((MemorySessionImpl) (arena.scope())).addInternalUnchecked(
                    MemorySessionImpl.ResourceList.ResourceCleanup.ofRunnable(inner.cleanupAction));
        }

        @SuppressWarnings("restricted")
        @ForceInline
        @Override
        public MemorySegment allocate(long byteSize, long byteAlignment) {
            return inner.allocator.allocate(byteSize, byteAlignment)
                    .reinterpret(arena, null)
                    .fill((byte) 0);
        }

        @ForceInline
        @Override
        public MemorySegment.Scope scope() {
            return arena.scope();
        }

        @ForceInline
        @Override
        public void close() {
            arena.close();
            inner.pop(initialPos);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Frame other)) return false;
            return arena == other.arena;
        }

        @Override
        public int hashCode() {
            return arena.hashCode();
        }

        @Override
        public String toString() {
            return "Frame(arena=" + arena + ")";
        }
    }

    private record CleanupAction(Arena arena) implements Runnable {
        @Override
        public void run() {
            Reference.reachabilityFence(arena);
        }
    }

    // Low-cost reentrant lock that must always run on the same PT in order for correct
    // memory visibility and ordering.
    //
    private static final class ThreadLocalLock {

        private static final Unsafe UNSAFE = Unsafe.getUnsafe();
        private static final long THREAD_ID_OFFSET = UNSAFE.objectFieldOffset(ThreadLocalLock.class, "threadId");
        private static final int RELEASED = 0;

        // Used reflectively
        private long threadId;

        boolean tryLock() {
            final long threadId = Thread.currentThread().threadId();
            //final long witness = UNSAFE.compareAndExchangeLong(this, THREAD_ID_OFFSET, RELEASED, threadId);
            final long witness = UNSAFE.compareAndExchangeLongAcquire(this, THREAD_ID_OFFSET, RELEASED, threadId);
            return witness == RELEASED      // Newly acquired
                    || threadId == witness; // Reentered
        }

        void unlock() {
/*            UNSAFE.storeStoreFence();
            threadId = RELEASED;*/
            UNSAFE.putLongRelease(this, THREAD_ID_OFFSET, RELEASED);
        }

    }

    public static StackMemoryPool of(long byteSize, long byteAlignment) {
        if (byteSize < 0) {
            throw new IllegalArgumentException("Negative byteSize: " + byteSize);
        }
        if (byteAlignment < 0) {
            throw new IllegalArgumentException("Negative byteAlignment: " + byteAlignment);
        }
        return new StackMemoryPool(byteSize, byteAlignment);
    }

    public static StackMemoryPool of(long byteSize) {
        return new StackMemoryPool(byteSize, 1);
    }

    public static StackMemoryPool of(MemoryLayout layout) {
        // Implicit null check
        return of(layout.byteSize(), layout.byteAlignment());
    }

}
