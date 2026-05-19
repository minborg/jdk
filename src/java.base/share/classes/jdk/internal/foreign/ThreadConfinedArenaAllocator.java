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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.ref.Reference;
import java.util.function.Consumer;

// This class is not thread safe.
final class ThreadConfinedArenaAllocator implements AutoCloseable {

    private static final String PROPERTY_NAME = "java.lang.foreign.confined.pool-size";
    private static final long POOL_SIZE = Long.getLong(PROPERTY_NAME, 512L);
    private static final long POOL_ALIGNMENT = 64;
    private static final long MIN_SEGMENT_SIZE = Math.min(POOL_ALIGNMENT, Math.max(1L, POOL_SIZE));

    private final Arena backingArena;
    private final CleanupAction cleanupAction;
    private Chunk freeList;
    private long allocatedBytes;
    private boolean closed;

    private ThreadConfinedArenaAllocator(Arena backingArena) {
        this.backingArena = backingArena;
        this.cleanupAction = new CleanupAction(backingArena);
    }

    static ThreadConfinedArenaAllocator of() {
        if (VM.isDirectMemoryPageAligned() || POOL_SIZE <= 0) {
            return null;
        }
        try {
            return new ThreadConfinedArenaAllocator(Arena.ofShared());
        } catch (OutOfMemoryError _) {
            return null;
        }
    }

    Arena acquire(Thread owner) {
        return new CachedArena(owner);
    }

    private Chunk acquireChunk(long byteSize, long byteAlignment) {
        if (closed) {
            return null;
        }
        long chunkSize = chunkSize(byteSize, byteAlignment);
        if (chunkSize < 0) {
            return null;
        }

        Chunk previous = null;
        Chunk bestPrevious = null;
        Chunk best = null;
        for (Chunk current = freeList; current != null; current = current.next) {
            if (current.canAllocate(byteSize, byteAlignment) &&
                    (best == null || current.segment.byteSize() < best.segment.byteSize())) {
                best = current;
                bestPrevious = previous;
            }
            previous = current;
        }
        if (best != null) {
            if (bestPrevious == null) {
                freeList = best.next;
            } else {
                bestPrevious.next = best.next;
            }
            best.next = null;
            return best;
        }

        if (allocatedBytes > POOL_SIZE - chunkSize) {
            return null;
        }
        try {
            long chunkAlignment = Math.min(chunkSize, Math.max(POOL_ALIGNMENT, byteAlignment));
            MemorySegment segment = backingArena.allocate(chunkSize, chunkAlignment);
            allocatedBytes += chunkSize;
            return new Chunk(segment);
        } catch (OutOfMemoryError _) {
            return null;
        }
    }

    private void releaseChunk(Chunk chunk) {
        if (!closed) {
            chunk.next = freeList;
            freeList = chunk;
        }
    }

    private static long chunkSize(long byteSize, long byteAlignment) {
        if (byteSize == 0) {
            return -1;
        }
        long alignmentSlack = byteAlignment - 1;
        if (byteSize > Long.MAX_VALUE - alignmentSlack) {
            return -1;
        }
        long size = Math.max(byteSize + alignmentSlack, MIN_SEGMENT_SIZE);
        if (size > POOL_SIZE) {
            return -1;
        }
        long roundedSize = roundUpPowerOfTwo(size);
        return roundedSize > POOL_SIZE ? POOL_SIZE : roundedSize;
    }

    private static long roundUpPowerOfTwo(long value) {
        if (value <= 1) {
            return 1;
        }
        long highest = Long.highestOneBit(value - 1);
        return highest > (Long.MAX_VALUE >>> 1) ? Long.MAX_VALUE : highest << 1;
    }

    @Override
    public void close() {
        closed = true;
        freeList = null;
        backingArena.close();
    }

    private static final class Chunk {
        private final MemorySegment segment;
        private Chunk next;

        Chunk(MemorySegment segment) {
            this.segment = segment;
        }

        boolean canAllocate(long byteSize, long byteAlignment) {
            return new SlicingAllocator(segment).canAllocate(byteSize, byteAlignment);
        }
    }

    private record CleanupAction(Arena backingArena) implements Consumer<MemorySegment> {
        @Override
        public void accept(MemorySegment memorySegment) {
            Reference.reachabilityFence(backingArena);
        }
    }

    private static final class ChunkAllocation {
        private final Chunk chunk;
        private final SlicingAllocator allocator;
        private ChunkAllocation next;

        @SuppressWarnings("restricted")
        ChunkAllocation(CachedArena arena, Chunk chunk, CleanupAction cleanupAction) {
            this.chunk = chunk;
            this.allocator = new SlicingAllocator(chunk.segment.reinterpret(arena, cleanupAction));
        }
    }

    final class CachedArena extends ArenaImpl {
        private ChunkAllocation allocations;

        CachedArena(Thread owner) {
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

            ChunkAllocation allocation = findAllocation(byteSize, byteAlignment);
            if (allocation == null) {
                Chunk chunk = acquireChunk(byteSize, byteAlignment);
                if (chunk == null) {
                    return init ? super.allocate(byteSize, byteAlignment) : super.allocateNoInit(byteSize, byteAlignment);
                }
                allocation = new ChunkAllocation(this, chunk, cleanupAction);
                allocation.next = allocations;
                allocations = allocation;
            }

            NativeMemorySegmentImpl segment = (NativeMemorySegmentImpl) allocation.allocator.allocate(byteSize, byteAlignment);
            if (init) {
                segment.fill((byte) 0);
            }
            return segment;
        }

        private ChunkAllocation findAllocation(long byteSize, long byteAlignment) {
            for (ChunkAllocation allocation = allocations; allocation != null; allocation = allocation.next) {
                if (allocation.allocator.canAllocate(byteSize, byteAlignment)) {
                    return allocation;
                }
            }
            return null;
        }

        @ForceInline
        @Override
        public void close() {
            // The Arena::close method is called first as it checks thread
            // confinement and liveness before cached chunks are made available again.
            super.close();
            ChunkAllocation allocation = allocations;
            allocations = null;
            while (allocation != null) {
                ChunkAllocation next = allocation.next;
                allocation.next = null;
                releaseChunk(allocation.chunk);
                allocation = next;
            }
        }
    }
}
