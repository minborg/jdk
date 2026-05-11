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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;

final class ThreadConfinedArenaAllocator implements SegmentAllocator, AutoCloseable {

    private static final long POOL_SIZE = 4096;
    private static final long POOL_ALIGNMENT = 64;

    private Arena backingArena;
    private SlicingAllocator slicingAllocator;
    private boolean acquired;

    Arena acquire(Thread owner) {
        if (acquired) {
            return MemorySessionImpl.createConfined(owner).asArena();
        }
        acquired = true;
        return new PooledArena(MemorySessionImpl.createConfined(owner));
    }

    @Override
    public MemorySegment allocate(long byteSize, long byteAlignment) {
        Utils.checkAllocationSizeAndAlign(byteSize, byteAlignment);
        SlicingAllocator allocator = slicingAllocator(byteSize, byteAlignment);
        if (allocator == null) {
            throw new IndexOutOfBoundsException();
        }
        return allocator.allocate(byteSize, byteAlignment).fill((byte) 0);
    }

    @Override
    public void close() {
        if (backingArena != null) {
            backingArena.close();
            backingArena = null;
            slicingAllocator = null;
        }
    }

    private void release() {
        if (slicingAllocator != null) {
            slicingAllocator.resetTo(0);
        }
        acquired = false;
    }

    private SlicingAllocator slicingAllocator(long byteSize, long byteAlignment) {
        if (VM.isDirectMemoryPageAligned() || byteSize == 0 || byteSize > POOL_SIZE || byteAlignment > POOL_ALIGNMENT) {
            return null;
        }
        SlicingAllocator allocator = slicingAllocator;
        if (allocator == null) {
            try {
                backingArena = Arena.ofShared();
                MemorySegment segment = backingArena.allocate(POOL_SIZE, POOL_ALIGNMENT);
                allocator = (SlicingAllocator) SegmentAllocator.slicingAllocator(segment);
                slicingAllocator = allocator;
            } catch (OutOfMemoryError ignore) {
                backingArena = null;
                return null;
            }
        }
        return allocator.canAllocate(byteSize, byteAlignment) ? allocator : null;
    }

    private final class PooledArena implements Arena {

        private final MemorySessionImpl session;

        PooledArena(MemorySessionImpl session) {
            this.session = session;
        }

        @Override
        public MemorySegment.Scope scope() {
            return session;
        }

        @Override
        public void close() {
            session.close();
            release();
        }

        @Override
        public MemorySegment allocate(long byteSize, long byteAlignment) {
            Utils.checkAllocationSizeAndAlign(byteSize, byteAlignment);
            session.checkValidState();
            SlicingAllocator allocator = slicingAllocator(byteSize, byteAlignment);
            if (allocator != null) {
                MemorySegment segment = allocator.allocate(byteSize, byteAlignment).fill((byte) 0);
                return SegmentFactories.makeNativeSegmentUnchecked(segment.address(), byteSize, session);
            }
            return SegmentFactories.allocateNativeSegment(byteSize, byteAlignment, session, false, true);
        }
    }
}
