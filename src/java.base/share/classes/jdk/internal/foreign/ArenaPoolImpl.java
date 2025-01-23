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

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.misc.CarrierThread;
import jdk.internal.misc.TerminatingThreadLocal;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.lang.foreign.Arena;
import java.lang.foreign.ArenaPool;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;

// Todo: Alignment, close in different order, VT remount, make sure pinning/unpinning is correct, zeroing
// It is better to zero out memory after it has been used compared to when it is being reused.

// VT0 is mounted on a PT and an arena is allocated. Then another VT1 is mounted on the same PT and
// allocates an arena. Then VT0 is remounted on the PT and closes its arena.

public final class ArenaPoolImpl implements ArenaPool {

    @Stable
    private final TerminatingThreadLocal<ThreadLocalArenaPoolImpl> tl;

    public ArenaPoolImpl(long size) {
        this.tl = new TerminatingThreadLocal<>() {

            private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

            @Override
            protected ThreadLocalArenaPoolImpl initialValue() {
                final Thread carrierThread = JLA.currentCarrierThread();
                if (carrierThread instanceof CarrierThread) {
                    return new ThreadLocalArenaPoolImpl.OfCarrier(size);
                } else {
                    return new ThreadLocalArenaPoolImpl.OfPlatform(size);
                }
            }

            @Override
            protected void threadTerminated(ThreadLocalArenaPoolImpl stack) {
                stack.close();
            }
        };
    }

    @ForceInline
    @Override
    public Arena take() {
        return tl.get()
                .take();
    }

    private static sealed abstract class ThreadLocalArenaPoolImpl {

        static final int AVAILABLE = 0;
        static final int TAKEN = 1;
        static final Unsafe UNSAFE = Unsafe.getUnsafe();
        static final long SEG_AVAIL_OFFSET =
                UNSAFE.objectFieldOffset(ThreadLocalArenaPoolImpl.class, "segmentAvailability");

        @Stable
        private final Arena pooledArena;
        @Stable
        private final MemorySegment segment;
        // Used both directly and reflectively
        int segmentAvailability;

        private ThreadLocalArenaPoolImpl(long size) {
            this.pooledArena = Arena.ofConfined();
            this.segment = pooledArena.allocate(size);
        }

        @ForceInline
        public final Arena take() {
            if (acquireSegment()) {
                return new SlicingArena(segment);
            } else {
                return new SlicingArena(segment.byteSize());
            }
        }

        public final void close() {
            // This arena is closed by another thread and the creating thread is dead.
            // So, this will fail currently
            pooledArena.close();
        }

        abstract boolean acquireSegment();

        abstract void releaseSegment();

        public static final class OfCarrier
                extends ThreadLocalArenaPoolImpl {

            public OfCarrier(long size) {
                super(size);
            }

            @ForceInline
            boolean acquireSegment() {
                return UNSAFE.compareAndSetInt(this, SEG_AVAIL_OFFSET, AVAILABLE, TAKEN);
            }

            @ForceInline
            void releaseSegment() {
                UNSAFE.putIntVolatile(this, SEG_AVAIL_OFFSET, AVAILABLE);
            }
        }

        public static final class OfPlatform
                extends ThreadLocalArenaPoolImpl {

            public OfPlatform(long size) {
                super(size);
            }

            @ForceInline
            boolean acquireSegment() {
                segmentAvailability = TAKEN;
                return true;
            }

            @ForceInline
            void releaseSegment() {
                segmentAvailability = AVAILABLE;
            }
        }

        private final class SlicingArena implements Arena, NoInitSegmentAllocator {

            @Stable
            private final Arena delegate;
            @Stable
            private final SlicingAllocator allocator;
            @Stable
            private final boolean releaseSegment;

            @ForceInline
            private SlicingArena(MemorySegment segment) {
                this.delegate = Arena.ofConfined();
                this.allocator = (SlicingAllocator) SegmentAllocator.slicingAllocator(segment);
                this.releaseSegment = true;
            }

            @ForceInline
            private SlicingArena(long size) {
                this.delegate = Arena.ofConfined();
                this.allocator = (SlicingAllocator) SegmentAllocator.slicingAllocator(delegate.allocate(size));
                this.releaseSegment = false;
            }

            @ForceInline
            @Override
            public MemorySegment.Scope scope() {
                return delegate.scope();
            }

            @ForceInline
            @Override
            public NativeMemorySegmentImpl allocate(long byteSize, long byteAlignment) {
                return NoInitSegmentAllocator.super.allocate(byteSize, byteAlignment);
            }

            @SuppressWarnings("restricted")
            @ForceInline
            public NativeMemorySegmentImpl allocateNoInit(long byteSize, long byteAlignment) {
                return (NativeMemorySegmentImpl) allocator.allocate(byteSize, byteAlignment)
                        .reinterpret(byteSize, delegate, null);
            }

            @ForceInline
            @Override
            public void close() {
                try {
                    delegate.close();
                } finally {
                    if (releaseSegment) {
                        ThreadLocalArenaPoolImpl.this.releaseSegment();
                    }
                }
            }
        }
    }
}
