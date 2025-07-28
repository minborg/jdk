package jdk.internal.foreign;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

abstract class SegmentFifo {

    static final long MIN_ALIGNMENT = 8;

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    static final int MAX_INDEX = Long.SIZE - 1; // Regions with all the powers of two

    // Constants for spin-locks
    private static final int RELEASED = 0;
    private static final int ACQUIRED = 1;

    // An array with stacks that holds recycled segments
    @Stable
    private final UnboundSegmentStack[] stacks;
    private final Arena arena;
    private final Resolution resolution;

    SegmentFifo(Arena arena, Resolution resolution) {
        this.stacks = new UnboundSegmentStack[MAX_INDEX];
        for (int i = 0; i < MAX_INDEX; i++) {
            stacks[i] = new UnboundSegmentStack();
        }

        this.arena = arena;
        this.resolution = resolution;
    }

    @ForceInline
    final MemorySegment take(long byteSize, long byteAlignment) {
        final long requiredRawSize = byteAlignment > MIN_ALIGNMENT
                ? byteSize + byteAlignment - 1  // Worst case needed to align
                : byteSize;                     // No need to look at alignment
        final int index = index(requiredRawSize);

        acquireLock(index);
        // We are now exclusive for the `index` at hand and have established an HB
        // relation to everything in program order before the lock was acquired
        // pertaining to actions made for the `index` (but not for other indices).

        MemorySegment rawSegment;
        try {
            rawSegment = stacks[index].pop();
        } finally {
            releaseLock(index);
        }
        return (rawSegment == null)
                // E.g., Create a new segment
                ? resolution.resolve(arena, power2(index), MIN_ALIGNMENT)
                : rawSegment;
    }

    @ForceInline
    final void release(MemorySegment rawSegment) {
        final int index = index(rawSegment.byteSize());
        acquireLock(index);
        // We are now exclusive for the `index` at hand and have established an HB
        // relation to everything in program order before the lock was acquired
        // pertaining to actions made for the `index` (but not for other indices).
        try {
            stacks[index].push(rawSegment);
        } finally {
            releaseLock(index);
        }
    }

    @Override
    final public String toString() {
        long segments = 0;
        long bytes = 0;
        for (int i = 0; i < SegmentFifo.MAX_INDEX; i++) {
            acquireLock(i);
            try {
                // Unfortunately, this operation may block for some time
                for (MemorySegment segment : stacks[i]) {
                    segments++;
                    bytes += segment.byteSize();
                }
            } finally {
                releaseLock(i);
            }
        }
        return "SegmentFifo(" + bytes + " bytes in " + segments + " segments)";
    }

    @ForceInline
    private int index(long byteSize) {
        return (64 - Long.numberOfLeadingZeros(byteSize - 1)) % 64;
    }

    @ForceInline
    private long power2(int index) {
        return 1L << index;
    }

    abstract void acquireLock(int index);

    abstract void releaseLock(int index);

    // Defines how do we resolve the situation if a segment cannot be found in the FIFO.
    @FunctionalInterface
    interface Resolution {
        MemorySegment resolve(Arena arena, long byteSize, long byteAlignment);
    }

    static final class OfConcurrent extends SegmentFifo {

        // Locks, one for each region
        private final int[] locks;

        public OfConcurrent(Arena arena, Resolution resolution) {
            super(arena, resolution);
            this.locks = new int[MAX_INDEX];
        }

        @ForceInline
        void acquireLock(int index) {
            final long offset = intArrayOffset(index);
            while (!UNSAFE.compareAndSetInt(locks, offset, RELEASED, ACQUIRED)) {
                Thread.onSpinWait();
            }
        }

        @ForceInline
        void releaseLock(int index) {
            // Todo: Is release semantics is enough? We do not need HB here.
            UNSAFE.putIntVolatile(locks, intArrayOffset(index), RELEASED);
        }

        @ForceInline
        private long intArrayOffset(int index) {
            return Unsafe.ARRAY_INT_BASE_OFFSET + (long) Unsafe.ARRAY_INT_INDEX_SCALE * index;
        }

    }

    static final class OfNonConcurrent extends SegmentFifo {

        private final Thread owningThread;

        OfNonConcurrent(Arena arena, Resolution resolution) {
            super(arena, resolution);
            this.owningThread = Thread.currentThread();
        }

        @ForceInline
        void acquireLock(int index) {
            checkThread();
        }

        @ForceInline
        void releaseLock(int index) {
            checkThread();
        }

        @ForceInline
        private void checkThread() {
            if (owningThread != Thread.currentThread()) {
                throw new WrongThreadException();
            }
        }

    }

}
