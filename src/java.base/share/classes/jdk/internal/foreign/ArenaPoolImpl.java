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

public final class ArenaPoolImpl implements ArenaPool {

    @Stable
    private final TerminatingThreadLocal<ThreadLocalArenaPoolImpl> tl;

    public ArenaPoolImpl(long byteSize, long byteAlignment) {
        this.tl = new TerminatingThreadLocal<>() {

            private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

            @Override
            protected ThreadLocalArenaPoolImpl initialValue() {
                if (JLA.currentCarrierThread() instanceof CarrierThread) {
                    // Only a carrier thread that is an instance of `CarrierThread` can
                    // ever carry virtual threads. (Notably, a `CarrierThread` can also
                    // carry a platform thread.) This means a `CarrierThread` can carry
                    // any number of virtual threads, and they can be mounted/unmounted
                    // from the carrier thread at almost any time. Therefore, we must use
                    // stronger-than-plain semantics when dealing with mutual exclusion
                    // of thread local resources.
                    return new ThreadLocalArenaPoolImpl.OfCarrier(byteSize, byteAlignment);
                } else {
                    // A carrier thread that is not an instance of `CarrierThread` can
                    // never carry a virtual thread. Because of this, only one thread will
                    // be mounted on such a carrier thread. Therefore, we can use plain
                    // memory semantics when dealing with mutual exclusion of thread local
                    // resources.
                    return new ThreadLocalArenaPoolImpl.OfPlatform(byteSize, byteAlignment);
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

        @Stable
        private final Arena pooledArena;
        @Stable
        private final MemorySegment segment;
        // Used both directly and reflectively
        int segmentAvailability;

        private ThreadLocalArenaPoolImpl(long byteSize,
                                         long byteAlignment) {
            this.pooledArena = Arena.ofConfined();
            this.segment = pooledArena.allocate(byteSize, byteAlignment);
        }

        @ForceInline
        public final Arena take() {
            final Arena arena = Arena.ofConfined();
            return tryAcquireSegment()
                    ? new SlicingArena((ArenaImpl) arena, segment)
                    : arena;
        }

        public final void close() {
            // This arena is closed by another thread and the creating thread is dead.
            // So, this will fail currently??
            pooledArena.close();
        }

        /**
         * {@return {@code true } if the segment was acquired for exclusive use,
         *          {@code false} otherwise}
         */
        abstract boolean tryAcquireSegment();

        /**
         * Unconditionally releases the acquired segment if it was previously acquired,
         * otherwise this is a no-op.
         */
        abstract void releaseSegment();

        /**
         * Thread safe implementation.
         */
        public static final class OfCarrier
                extends ThreadLocalArenaPoolImpl {

            // Unsafe allows earlier use in the init sequence and
            // better start and warmup properties.
            static final Unsafe UNSAFE = Unsafe.getUnsafe();
            static final long SEG_AVAIL_OFFSET =
                    UNSAFE.objectFieldOffset(ThreadLocalArenaPoolImpl.class, "segmentAvailability");

            public OfCarrier(long byteSize,
                             long byteAlignment) {
                super(byteSize, byteAlignment);
            }

            @ForceInline
            boolean tryAcquireSegment() {
                return UNSAFE.compareAndSetInt(this, SEG_AVAIL_OFFSET, AVAILABLE, TAKEN);
            }

            @ForceInline
            void releaseSegment() {
                UNSAFE.putIntVolatile(this, SEG_AVAIL_OFFSET, AVAILABLE);
            }
        }

        /**
         * No need for thread-safe implementation here as a platform thread is
         * exclusively mounted on a particular carrier thread.
         */
        public static final class OfPlatform
                extends ThreadLocalArenaPoolImpl {

            public OfPlatform(long byteSize,
                              long byteAlignment) {
                super(byteSize, byteAlignment);
            }

            @ForceInline
            boolean tryAcquireSegment() {
                if (segmentAvailability == TAKEN) {
                    return false;
                } else {
                    segmentAvailability = TAKEN;
                    return true;
                }
            }

            @ForceInline
            void releaseSegment() {
                segmentAvailability = AVAILABLE;
            }
        }

        /**
         * A SlicingArena is similar to a {@linkplain SlicingAllocator} but if the
         * backing segment cannot be used for allocation, a fall-back arena is used
         * instead. This means allocation never fails due to the size and alignment
         * of the backing segment.
         *
         * Todo: Should we expose a variant of this class as a complement
         *       to SlicingAllocator?
         *
         */
        private final class SlicingArena implements Arena, NoInitSegmentAllocator {

            @Stable
            private final ArenaImpl delegate;
            @Stable
            private final MemorySegment segment;

            private long sp = 0L;

            @ForceInline
            private SlicingArena(ArenaImpl arena,
                                 MemorySegment segment) {
                this.delegate = arena;
                this.segment = segment;
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
                if (start + byteSize < segment.byteSize()) {
                    Utils.checkAllocationSizeAndAlign(byteSize, byteAlignment);
                    final MemorySegment slice = segment.asSlice(start, byteSize, byteAlignment);
                    sp = start + byteSize;
                    return fastReinterpret(delegate, (NativeMemorySegmentImpl) slice, byteSize);
/*                    return (NativeMemorySegmentImpl) slice
                            .reinterpret(byteSize, delegate, null);*/
                } else {
                    return delegate.allocateNoInit(byteSize, byteAlignment);
                }
            }

            @ForceInline
            @Override
            public void close() {
                delegate.close();
                // Intentionally do not releaseSegment() in a finally clause as
                // the segment still is in play if close() initially fails (e.g. is closed
                // from a non-owner thread). Later on the close() method might be
                // successfully re-invoked (e.g. from its owner thread).
                ThreadLocalArenaPoolImpl.this.releaseSegment();
            }
        }
    }

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

}
