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

import jdk.internal.misc.TerminatingThreadLocal;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

public final class CarrierLocalArenaPools {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    // Used by platform threads
    @Stable
    private final TerminatingThreadLocal<LocalArenaPoolImpl> tlArenaPools;

    // Used by virtual threads
    private final MemorySegment[] reusableVtSegments;

    private CarrierLocalArenaPools(long byteSize, long byteAlignment) {
        this.tlArenaPools = new TerminatingThreadLocal<>() {

            @Override
            protected LocalArenaPoolImpl initialValue() {
                // assert !(JLA.currentCarrierThread() instanceof CarrierThread);

                // A carrier thread that is not an instance of `CarrierThread` can
                // never carry a virtual thread. Because of this, only one thread will
                // be mounted on such a carrier thread. Therefore, we can use plain
                // memory semantics when dealing with mutual exclusion of thread local
                // resources.
                return new LocalArenaPoolImpl(byteSize, byteAlignment);
            }

            @Override
            protected void threadTerminated(LocalArenaPoolImpl pool) {
                pool.close();
            }
        };
        this.reusableVtSegments = reusableVtSegments(byteSize, byteAlignment);
    }

    @ForceInline
    public Arena take() {
        return (Thread.currentThread().isVirtual())
                ? arenaForVirtualThread()
                : tlArenaPools.get().take();
    }

    private Arena arenaForVirtualThread() {
        final long slot = Long.MAX_VALUE & (Thread.currentThread().threadId() % reusableVtSegments.length);
        final long offset = Unsafe.ARRAY_OBJECT_BASE_OFFSET + slot * Unsafe.ARRAY_OBJECT_INDEX_SCALE;
        final MemorySegment recyclableSegment = (MemorySegment) UNSAFE.getAndSetReference(reusableVtSegments, offset, null);
        final Arena delegate = Arena.ofConfined();
        return recyclableSegment == null
                ? delegate
                : new SlicingArena((ArenaImpl) delegate, recyclableSegment,
                new SegmentReleaser() {
                    @ForceInline
                    @Override
                    public void release(MemorySegment segment) {
                        UNSAFE.putReferenceVolatile(reusableVtSegments, offset, segment);
                    }
        });
    }

    @FunctionalInterface
    interface SegmentReleaser {
        void release(MemorySegment segment);
    }

    private static final class LocalArenaPoolImpl implements SegmentReleaser {

        static final int AVAILABLE = 0;
        static final int TAKEN = 1;

        // Hold a reference so that the arena is not GC:ed before the thread dies
        // and to allow the arena to be explicitly closed
        @Stable
        private final Arena originalArena;
        @Stable
        private final MemorySegment recyclableSegment;

        // Used both directly and reflectively
        int segmentAvailability;

        private LocalArenaPoolImpl(long byteSize,
                                   long byteAlignment) {
            this.originalArena = Arena.ofConfined();
            this.recyclableSegment = originalArena.allocate(byteSize, byteAlignment);
        }

        @ForceInline
        public Arena take() {
            final Arena arena = Arena.ofConfined();
            return tryAcquireSegment()
                    ? new SlicingArena((ArenaImpl) arena, recyclableSegment, this)
                    : arena;
        }

        void close() {
            originalArena.close();
        }

        /**
         * {@return {@code true } if the segment was acquired for exclusive use, {@code
         * false} otherwise}
         */
        @ForceInline
        boolean tryAcquireSegment() {
            if (segmentAvailability == TAKEN) {
                return false;
            } else {
                segmentAvailability = TAKEN;
                return true;
            }
        }

        /**
         * Unconditionally releases the acquired segment if it was previously acquired,
         * otherwise this is a no-op.
         */
        @ForceInline
        public void release(MemorySegment segment) {
            segmentAvailability = AVAILABLE;
        }

    }

    /**
     * A SlicingArena is similar to a {@linkplain SlicingAllocator} but if the backing
     * segment cannot be used for allocation, a fall-back arena is used instead. This
     * means allocation never fails due to the size and alignment of the backing
     * segment.
     */
    private static final class SlicingArena implements Arena, NoInitSegmentAllocator {

        @Stable
        private final ArenaImpl delegate;
        @Stable
        private final MemorySegment segment;
        @Stable
        private final SegmentReleaser releaser;
        @Stable
        private final Thread owner;

        private long sp = 0L;

        @ForceInline
        private SlicingArena(ArenaImpl delegate,
                             MemorySegment segment,
                             SegmentReleaser releaser) {
            this.delegate = delegate;
            this.segment = segment;
            this.releaser = releaser;
            this.owner = Thread.currentThread();
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
            final long min = segment.address();
            final long start = Utils.alignUp(min + sp, byteAlignment) - min;
            if (start + byteSize <= segment.byteSize()) {
                Utils.checkAllocationSizeAndAlign(byteSize, byteAlignment);
                final MemorySegment slice = segment.asSlice(start, byteSize, byteAlignment);
                sp = start + byteSize;
                return fastReinterpret(delegate, (NativeMemorySegmentImpl) slice, byteSize);
            } else {
                return delegate.allocateNoInit(byteSize, byteAlignment);
            }
        }

        @ForceInline
        @Override
        public void close() {
            assertOwnerThread();
            delegate.close();
            // Intentionally do not perform the cleanup action in a `finally` clause as
            // the segment still is in play if close() initially fails (e.g. is closed
            // from a non-owner thread). Later on the close() method might be
            // successfully re-invoked (e.g. from its owner thread).
            releaser.release(segment);
        }

        @ForceInline
        void assertOwnerThread() {
            if (owner != Thread.currentThread()) {
                throw new WrongThreadException();
            }
        }

    }


    // Equivalent to but faster than:
    //     return (NativeMemorySegmentImpl) slice
    //             .reinterpret(byteSize, delegate, null); */
    @ForceInline
    static NativeMemorySegmentImpl fastReinterpret(ArenaImpl arena,
                                                   NativeMemorySegmentImpl segment,
                                                   long byteSize) {
        // We already know the segment:
        //  * is native
        //  * we have native access
        //  * there is no cleanup action
        //  * the segment is read/write
        return SegmentFactories.makeNativeSegmentUnchecked(segment.address(), byteSize,
                MemorySessionImpl.toMemorySession(arena), false, null);
    }

    public static CarrierLocalArenaPools create(long byteSize) {
        if (byteSize < 0) {
            throw new IllegalArgumentException();
        }
        return new CarrierLocalArenaPools(byteSize, 1L);
    }

    public static CarrierLocalArenaPools create(long byteSize,
                                                long byteAlignment) {
        Utils.checkAllocationSizeAndAlign(byteSize, byteAlignment);
        return new CarrierLocalArenaPools(byteSize, byteAlignment);
    }

    public static CarrierLocalArenaPools create(MemoryLayout layout) {
        Objects.requireNonNull(layout);
        return new CarrierLocalArenaPools(layout.byteSize(), layout.byteAlignment());
    }

    private static MemorySegment[] reusableVtSegments(long byteSize,
                                                      long byteAlignment) {
        // Only create so many slots as the pool only provides a best-effort
        // implementation for reusing segments.
        final int maxSize = Runtime.getRuntime().availableProcessors();
        final MemorySegment[] vtSegments = new MemorySegment[maxSize];
        for (int i = 0; i < maxSize; i++) {
            vtSegments[i] = Arena.global().allocate(byteSize, byteAlignment);
        }
        return vtSegments;
    }

}
