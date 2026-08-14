/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.invoke.MhUtil;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment.Scope;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public sealed class ArenaImpl implements Arena {

    protected final MemorySessionImpl session;
    protected final boolean shouldReserve;

    ArenaImpl(MemorySessionImpl session) {
        this.session = session;
        this.shouldReserve = session instanceof ImplicitSession;
    }

    @Override
    public final Scope scope() {
        return session;
    }

    @Override
    public void close() {
        session.close();
    }

    public NativeMemorySegmentImpl allocateNoInit(long byteSize, long byteAlignment) {
        return SegmentFactories.allocateNativeSegment(byteSize, byteAlignment, session, shouldReserve, false);
    }

    @Override
    public NativeMemorySegmentImpl allocate(long byteSize, long byteAlignment) {
        return SegmentFactories.allocateNativeSegment(byteSize, byteAlignment, session, shouldReserve, true);
    }

    static final class OfConfined extends ArenaImpl {

        private static final long POOL_SIZE = ConfinedSegmentPool.pooledMemorySize();

        // Set at most once: an arena never switches backing pools.
        @Stable
        private long pool;
        private long poolSp;

        OfConfined(ConfinedSession session) {
            super(session);
        }

        @Override
        public void close() {
            // Invalidate every segment before making the pool reusable.
            session.justClose();
            // The session cleanup at the end of this method may throw, so we
            // need to release the acquired pooled memory first.
            if (pool > 0) {
                ConfinedSegmentPool.release(session.owner, pool, poolSp);
            }
            session.resourceList.cleanup();
        }

        @Override
        public NativeMemorySegmentImpl allocateNoInit(long byteSize, long byteAlignment) {
            return allocateLowLevel(byteSize, byteAlignment, false);
        }

        @Override
        public NativeMemorySegmentImpl allocate(long byteSize, long byteAlignment) {
            return allocateLowLevel(byteSize, byteAlignment, true);
        }

        @ForceInline
        NativeMemorySegmentImpl allocateLowLevel(long byteSize, long byteAlignment, boolean init) {
            // Only alignments no greater than the pool size are guaranteed to have an
            // aligned address within the pool. This also bounds the alignment arithmetic.
            if (byteSize <= POOL_SIZE && byteAlignment <= POOL_SIZE) {
                Utils.checkAllocationSizeAndAlign(byteSize, byteAlignment);
                session.checkValidState();
                long pool = this.pool;
                if (pool == 0) {
                    pool = ConfinedSegmentPool.acquire(session.owner);
                    if (pool == 0) {
                        pool = ConfinedSegmentPool.allocateLocal(session.owner);
                    }
                    if (pool > 0) {
                        this.pool = pool;
                    }
                }
                // Preserve the invariant that zero-sized segments have unique addresses
                // for any given Arena.
                final long allocationByteSize = Math.max(1, byteSize);
                final long address;
                if (pool > 0 && (address = trySlice(pool, allocationByteSize, byteAlignment)) != 0) {
                    return SegmentFactories.makeNativeSegmentUnchecked(address, byteSize, session);
                }
            }
            return init
                    ? super.allocate(byteSize, byteAlignment)
                    : super.allocateNoInit(byteSize, byteAlignment);
        }

        private long trySlice(long pool, long byteSize, long byteAlignment) {
            final long start = Utils.alignUp(pool + poolSp, byteAlignment) - pool;
            if (start + byteSize <= POOL_SIZE) {
                // The backing memory is zeroed on initial allocation and on each pool release.
                poolSp = start + byteSize;
                return pool + start;
            }
            return 0;
        }

    }

    /**
     * An arena whose allocations can be performed concurrently. The current
     * slab is installed under synchronization, while slices within that slab
     * are reserved using CAS.
     */
    static final class OfConcurrent extends ArenaImpl {

        private static final long POOL_SIZE = ConfinedSegmentPool.pooledMemorySize();

        private volatile ConcurrentPool pool;

        OfConcurrent(MemorySessionImpl session) {
            super(session);
        }

        @Override
        public NativeMemorySegmentImpl allocateNoInit(long byteSize, long byteAlignment) {
            return allocateLowLevel(byteSize, byteAlignment, false);
        }

        @Override
        public NativeMemorySegmentImpl allocate(long byteSize, long byteAlignment) {
            return allocateLowLevel(byteSize, byteAlignment, true);
        }

        @ForceInline
        private NativeMemorySegmentImpl allocateLowLevel(long byteSize, long byteAlignment, boolean init) {
            if (byteSize <= POOL_SIZE && byteAlignment <= POOL_SIZE) {
                Utils.checkAllocationSizeAndAlign(byteSize, byteAlignment);
                session.checkValidState();
                final long allocationByteSize = Math.max(1, byteSize);
                final ConcurrentPool current = pool;
                long address = current == null
                        ? 0
                        : current.trySlice(allocationByteSize, byteAlignment);
                // Allocate a new slab only when the request is guaranteed to fit
                // regardless of the native allocation's incidental alignment.
                if (address == 0 &&
                        allocationByteSize <= POOL_SIZE - (byteAlignment - 1)) {
                    address = allocateFromNewPool(current, allocationByteSize, byteAlignment);
                }
                if (address != 0) {
                    return SegmentFactories.makeNativeSegmentUnchecked(address, byteSize, session);
                }
            }
            return init
                    ? super.allocate(byteSize, byteAlignment)
                    : super.allocateNoInit(byteSize, byteAlignment);
        }

        /**
         * Installs a new pool only if {@code observed} is still current. Cleanup
         * is registered before publication so other threads cannot observe an
         * untracked native allocation.
         */
        private synchronized long allocateFromNewPool(ConcurrentPool observed,
                                                      long byteSize,
                                                      long byteAlignment) {
            session.checkValidState();
            ConcurrentPool current = pool;
            if (current != observed) {
                final long address = current.trySlice(byteSize, byteAlignment);
                if (address != 0) {
                    return address;
                }
            }

            final ConcurrentPool next = ConcurrentPool.allocate(shouldReserve);
            if (next == null) {
                return 0;
            }
            session.addOrCleanupIfFail(next);
            final long address = next.trySlice(byteSize, byteAlignment);
            pool = next;
            return address;
        }

        /**
         * A fixed-size native slab and its atomic bump pointer. This cleanup
         * object deliberately does not reference its arena or session, which
         * allows an implicit session's cleaner to become reachable.
         */
        private static final class ConcurrentPool extends MemorySessionImpl.ResourceList.ResourceCleanup {

            private static final Unsafe U = Unsafe.getUnsafe();

            private static final VarHandle POOL_SP = MhUtil.findVarHandle(
                    MethodHandles.lookup(), ConcurrentPool.class, "poolSp", long.class);

            private final long address;
            private final boolean reserved;
            private volatile long poolSp;

            private ConcurrentPool(long address, boolean reserved) {
                this.address = address;
                this.reserved = reserved;
            }

            static ConcurrentPool allocate(boolean reserve) {
                if (POOL_SIZE <= 0) {
                    return null;
                }
                if (reserve) {
                    try {
                        AbstractMemorySegmentImpl.NIO_ACCESS.reserveMemory(POOL_SIZE, POOL_SIZE);
                    } catch (OutOfMemoryError _) {
                        return null;
                    }
                }

                long address = 0;
                try {
                    address = U.allocateMemory(POOL_SIZE);
                    U.setMemory(address, POOL_SIZE, (byte) 0);
                    return new ConcurrentPool(address, reserve);
                } catch (OutOfMemoryError _) {
                    if (address != 0) {
                        U.freeMemory(address);
                    }
                    if (reserve) {
                        AbstractMemorySegmentImpl.NIO_ACCESS.unreserveMemory(POOL_SIZE, POOL_SIZE);
                    }
                    return null;
                }
            }

            @ForceInline
            long trySlice(long byteSize, long byteAlignment) {
                while (true) {
                    final long current = (long) POOL_SP.getVolatile(this);
                    final long start = Utils.alignUp(address + current, byteAlignment) - address;
                    final long next = start + byteSize;
                    if (next > POOL_SIZE) {
                        return 0;
                    }
                    if (POOL_SP.compareAndSet(this, current, next)) {
                        return address + start;
                    }
                }
            }

            @Override
            public void cleanup() {
                U.freeMemory(address);
                if (reserved) {
                    AbstractMemorySegmentImpl.NIO_ACCESS.unreserveMemory(POOL_SIZE, POOL_SIZE);
                }
            }
        }
    }

}
