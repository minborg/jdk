/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.misc.VM;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

// This class is not thread safe and does not need to be as it, by definition, is only
// operated on using a distict thread. However, the close action can be run by
// another thread.
final class ThreadConfinedSegmentPool implements AutoCloseable {

    private static final String PROPERTY_PATH = "java.lang.foreign.native.confined.arena.";

    // We are using a `long` for keeping track of slot used so, we can only handle
    // a maximum of 64 slots.
    private static final int POOL_SLOTS =
            Math.min(64, Integer.getInteger(PROPERTY_PATH + "pool-slots", 2));
    private static final long POOL_SLOT_ALIGNMENT =
            SegmentBulkOperations.powerOfPropertyOr(PROPERTY_PATH + "power.pool-slot-alignment", 4);
    // The slot size cannot be smaller than the alignment due to how the backingSegment
    // is sliced.
    private static final long POOL_SLOT_SIZE =
            Math.max(POOL_SLOT_ALIGNMENT, SegmentBulkOperations.powerOfPropertyOr(PROPERTY_PATH + "power.pool-slot-size", 6));

    private final ArenaImpl backingArena;
    private final MemorySegment backingSegment;
    @Stable
    private final SlicingAllocator[] allocators;
    // An optimized bit set in the form of a long
    // 0 means acquired, 1 means free
    // Set all available pool slots to 1
    private long allocatorSet = POOL_SLOTS == 64
            ? -1L
            : (1L << POOL_SLOTS) - 1;

    private ThreadConfinedSegmentPool(Thread thread) {
        final ArenaImpl backingArenaImpl = MemorySessionImpl.createConfined(thread).asArena();
        this.backingArena = backingArenaImpl;
        this.allocators = new SlicingAllocator[POOL_SLOTS];
        this.backingSegment = backingArenaImpl.allocate(POOL_SLOTS * POOL_SLOT_SIZE, POOL_SLOT_ALIGNMENT);
        super();
        // By slicing up a single memory segment, the allocators return segments with a
        // more likely hi degree of locality compared to allocating them separately.
        for (int i = 0; i < POOL_SLOTS; i++) {
            allocators[i] = new SlicingAllocator(backingSegment.asSlice(i * POOL_SLOT_SIZE, POOL_SLOT_SIZE));
        }

        IO.println("Created " + this + " with backing arena " + backingArenaImpl);
    }

    static ThreadConfinedSegmentPool of(Thread thread) {
        if (VM.isDirectMemoryPageAligned() || POOL_SLOTS <= 0 || POOL_SLOT_SIZE <= 1) {
            return null;
        }
        try {
            return new ThreadConfinedSegmentPool(thread);
        } catch (OutOfMemoryError _) {
            return null;
        }
    }

    Arena acquire(Thread owner) {
        final int allocatorIndex = acquireAllocator();
        if (allocatorIndex < Long.SIZE) {
            return new CachedArena(this, owner, allocatorIndex, allocators[allocatorIndex]);
        }
        // No pools left ...
        return MemorySessionImpl.createConfined(owner).asArena();
    }

    private int acquireAllocator() {
        final int allocatorIndex = Long.numberOfTrailingZeros(allocatorSet);
        allocatorSet &= ~(1L << allocatorIndex); // 1 -> 0
        return allocatorIndex;
    }

    private void releaseAllocator(int allocatorIndex) {
        allocatorSet |= 1L << allocatorIndex; // 0 -> 1
    }

    // This method might be called by another thread during thread cleanup.
    @Override
    public void close() {
        // Cleanup the resource list directly without going via close() as
        // this method might be invoked by another thread.
        // Todo: Verify the HB properties here
        backingArena.session.resourceList.cleanup();
    }

    static final class CachedArena extends ArenaImpl {
        private final ThreadConfinedSegmentPool outerInstance;
        private final int allocatorIndex;
        private final SlicingAllocator allocator;

        CachedArena(ThreadConfinedSegmentPool outerInstance,
                    Thread owner,
                    int allocatorIndex,
                    SlicingAllocator allocator) {
            this.outerInstance = outerInstance;
            this.allocatorIndex = allocatorIndex;
            this.allocator = allocator;
            super(MemorySessionImpl.createConfined(owner));
        }

        @ForceInline
        @Override
        @SuppressWarnings("restricted")
        public NativeMemorySegmentImpl allocate(long byteSize, long byteAlignment) {
            return allocate0(byteSize, byteAlignment, true);
        }

        @ForceInline
        @Override
        public NativeMemorySegmentImpl allocateNoInit(long byteSize, long byteAlignment) {
            return allocate0(byteSize, byteAlignment, false);
        }

        @ForceInline
        private NativeMemorySegmentImpl allocate0(long byteSize, long byteAlignment, boolean init) {
            Utils.checkAllocationSizeAndAlign(byteSize, byteAlignment);
            session.checkValidState();
            if (!allocator.canAllocate(byteSize, byteAlignment)) {
                return init ? super.allocate(byteSize, byteAlignment) : super.allocateNoInit(byteSize, byteAlignment);
            }
            final NativeMemorySegmentImpl segment = (NativeMemorySegmentImpl) allocator.allocate(byteSize, byteAlignment);
            if (init) {
                segment.fill((byte) 0);
            }
            // Reinterpret the slice to use this arena's scope.
            return SegmentFactories.makeNativeSegmentUnchecked(segment.address(), segment.byteSize(), segment.scope);
        }

        @ForceInline
        @Override
        public void close() {
            // The Arena::close method is called first as it checks thread
            // confinement and liveness before cached chunks are made available again.
            super.close();
            // Reset the allocator allowing future reuse
            allocator.resetTo(0);
            outerInstance.releaseAllocator(allocatorIndex);
        }
    }
}
