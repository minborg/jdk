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
import java.lang.foreign.MemoryPool;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.ref.Reference;

/**
 * A buffer stack that allows efficient reuse of memory segments. This is useful in cases
 * where temporary memory is needed.
 */
public record StackedMemoryPool(long byteSize,
                                CarrierThreadLocal<PerPlatformThread> tl) implements MemoryPool {

    public StackedMemoryPool(long byteSize) {
        this(byteSize, new CarrierThreadLocal<>() {
            @Override
            protected PerPlatformThread initialValue() {
                return Thread.currentThread().isVirtual()
                        ? PerVirtualThread.ofVirtualThread(byteSize)
                        : PerPlatformThread.ofPlatformThread(byteSize);
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
        return "StackedMemoryPool[byteSize=" + byteSize + "]";
    }

    public static sealed class PerPlatformThread implements MemoryPool {

        private final SlicingAllocator allocator;
        final CleanupAction cleanupAction;
        private int topFrameNumber;

        private PerPlatformThread(long byteSize) {
            final Arena arena = Arena.ofAuto();
            allocator = new SlicingAllocator(arena.allocate(byteSize));
            cleanupAction = new CleanupAction(arena);
        }

        @ForceInline
        @Override
        public Arena get() {
            return new ArenaFrame(this, ++topFrameNumber, allocator.currentOffset());
        }

        @ForceInline
        void pop(int frameNumber, long initialPos) {
            allocator.resetTo(initialPos);
            // This is the next stack frame number to expect a close from
            topFrameNumber--;
        }

        static PerPlatformThread ofPlatformThread(long byteSize) {
            return new PerPlatformThread(byteSize);
        }

    }

    public static final class PerVirtualThread extends PerPlatformThread implements MemoryPool {

        private final SimpleLock lock;

        private PerVirtualThread(long byteSize) {
            super(byteSize);
            lock = new SimpleLock();
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

                    // Todo: It is very hard to implement an arena that keeps track
                    // of how much is allocated considering a VT can be mounted/unmounted
                    // on a carrier thread at any time.
                    : Arena.ofConfined();
        }

        @ForceInline
        @Override
        void pop(int frameNumber, long initialPosOfFrame) {
            super.pop(frameNumber, initialPosOfFrame);
            if (frameNumber == 1) {
                // All allocations have been returned. Release the lock so that other
                // VT can use the pool.
                lock.unlock();
            }
        }

        static PerPlatformThread ofVirtualThread(long byteSize) {
            return new PerVirtualThread(byteSize);
        }

    }

    public static final class SingleThreaded extends PerPlatformThread implements MemoryPool {

        private final Thread owner;

        private SingleThreaded(long byteSize) {
            super(byteSize);
            owner = Thread.currentThread();
        }

        @Override
        public Arena get() {
            checkThread();
            return super.get();
        }

        @ForceInline
        @Override
        void pop(int frameNumber, long initialPosOfFrame) {
            checkThread();
            super.pop(frameNumber, initialPosOfFrame);
        }

        private void checkThread() {
            if (owner != Thread.currentThread()) {
                throw new WrongThreadException();
            }
        }

        public static PerPlatformThread ofSingleThreaded(long byteSize) {
            return new PerVirtualThread(byteSize);
        }

    }

    public record ArenaFrame(PerPlatformThread inner,
                             int frameNumber,
                             long initialPos,
                             ArenaImpl arena) implements Arena, SegmentAllocator.NonZeroable {

        @ForceInline
        private ArenaFrame(PerPlatformThread inner, int frameNumber, long initialPos) {
            this(inner, frameNumber, initialPos, (ArenaImpl) Arena.ofConfined());
            // Attach the cleanup action to the scope of the arena. The scope is then
            // present at every segment allocated from this arena.
            // We know the arena is alive, the thread is the owner thread, and the
            // `cleanupAction` is non-null so it is ok to call this method.
            ((MemorySessionImpl) (arena.scope())).addInternalUnchecked(
                    MemorySessionImpl.ResourceList.ResourceCleanup.ofRunnable(inner.cleanupAction));
        }

        @SuppressWarnings("restricted")
        @ForceInline
        @Override
        public MemorySegment allocate(long byteSize, long byteAlignment) {
            if (isNotTopFrame()) {
                // Todo: Harsh but honest
/*                throw new IllegalStateException("Trying to allocate from arena frame " + frameNumber +
                        " that is not on the top of the stack (" + inner.stackCounter + ")");*/
                // Todo: This is more lenient
                return arena.allocate(byteSize, byteAlignment);
            }

            final SlicingAllocator allocator = inner.allocator;
            return (allocator.canAllocate(byteSize, byteAlignment))
                    ? allocator.allocate(byteSize, byteAlignment)
                    .reinterpret(arena, null)
                    .fill((byte) 0)
                    : arena.allocate(byteSize, byteAlignment);
        }

        @SuppressWarnings("restricted")
        @ForceInline
        @Override
        public MemorySegment allocateNonZeroing(long byteSize, long byteAlignment) {
            if (isNotTopFrame()) {
                return arena.allocateNonZeroing(byteSize, byteAlignment);
            }

            final SlicingAllocator allocator = inner.allocator;
            return (allocator.canAllocate(byteSize, byteAlignment))
                    ? allocator.allocateNonZeroing(byteSize, byteAlignment)
                    .reinterpret(arena, null)
                    : arena.allocateNonZeroing(byteSize, byteAlignment);
        }

        @ForceInline
        @Override
        public MemorySegment.Scope scope() {
            return arena.scope();
        }

        @ForceInline
        @Override
        public void close() {
            if (isNotTopFrame()) {
                throw new IllegalStateException(String.format(
                        "The stacked arena was closed out of sequence. Expected stack frame number %d but got %d."
                        , inner.topFrameNumber, frameNumber));
            }
            arena.close();
            inner.pop(frameNumber, initialPos);
        }

        @Override
        public boolean equals(Object o) {
            return this == o;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        @ForceInline
        private boolean isNotTopFrame() {
            return inner.topFrameNumber != frameNumber;
        }

    }

    private record CleanupAction(Arena arena) implements Runnable {
        @Override
        // Reference the automatic arena from which the underlying pool memory was
        // allocated. This means every derived arena and segment strongly references
        // the automatic arena and thus, prevents use-after-free.
        public void run() {
            Reference.reachabilityFence(arena);
        }
    }

    // A very rudimentary, low-cost reentrant lock. The lock does not keep track of how
    // many times it has been locked and consequently, `unlock()` releases the lock
    // no matter how many times it has been locked. Furthermore, `unlock()` does not check
    // the invariant that the lock has actually been acquired by the holding thread.
    private static final class SimpleLock {

        private static final Unsafe UNSAFE = Unsafe.getUnsafe();
        private static final long THREAD_ID_OFFSET = UNSAFE.objectFieldOffset(SimpleLock.class, "threadId");
        private static final int RELEASED = 0;

        // Used reflectively
        private long threadId;

        @ForceInline
        boolean tryLock() {
            final long threadId = Thread.currentThread().threadId();
            final long witness = UNSAFE.compareAndExchangeLongAcquire(this, THREAD_ID_OFFSET, RELEASED, threadId);
            return witness == RELEASED      // Newly acquired
                    || threadId == witness; // Reentered
        }

        @ForceInline
        void unlock() {
            // Unconditionally release the lock
            UNSAFE.putLongRelease(this, THREAD_ID_OFFSET, RELEASED);
        }

    }

}
