/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
import jdk.internal.vm.annotation.ForceInline;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.ref.Reference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * A buffer stack that allows efficient reuse of memory segments. This is useful in cases
 * where temporary memory is needed.
 * <p>
 * Use the factories {@code BufferStack.of(...)} to create new instances of this class.
 * <p>
 * Note: The reused segments are neither zeroed out before nor after re-use.
 */
public final class BufferStack {

    private final long byteSize;
    private final long byteAlignment;
    private final CarrierThreadLocal<PerThread> tl;

    private BufferStack(long byteSize, long byteAlignment) {
        this.byteSize = byteSize;
        this.byteAlignment = byteAlignment;
        this.tl = new CarrierThreadLocal<>() {
            @Override
            protected BufferStack.PerThread initialValue() {
                return BufferStack.PerThread.of(byteSize, byteAlignment);
            }
        };
    }

    /**
     * {@return a new Arena that tries to provide {@code byteSize} and {@code byteAlignment}
     *          allocations by recycling the BufferStack's internal memory}
     *
     * @param byteSize      to be reserved from this BufferStack's internal memory
     * @param byteAlignment to be used for reservation
     */
    @ForceInline
    public Arena pushFrame(long byteSize, long byteAlignment) {
        return tl.get().pushFrame(byteSize, byteAlignment);
    }

    /**
     * {@return a new Arena that tries to provide {@code byteSize}
     *          allocations by recycling the BufferStack's internal memory}
     *
     * @param byteSize      to be reserved from this BufferStack's internal memory
     */
    @ForceInline
    public Arena pushFrame(long byteSize) {
        return pushFrame(byteSize, 1);
    }

    /**
     * {@return a new Arena that tries to provide {@code layout}
     *          allocations by recycling the BufferStack's internal memory}
     *
     * @param layout for which to reserve internal memory
     */
    @ForceInline
    public Arena pushFrame(MemoryLayout layout) {
        return pushFrame(layout.byteSize(), layout.byteAlignment());
    }

    @Override
    public String toString() {
        return "BufferStack[byteSize=" + byteSize + ", byteAlignment=" + byteAlignment + "]";
    }

    record PerThread(ReentrantLock lock,
                     Arena arena,
                     SlicingAllocator stack,
                     CleanupAction cleanupAction,
                     boolean closeable) implements AutoCloseable {

        @ForceInline
        public Arena pushFrame(long size, long byteAlignment) {
            return pushFrame(Thread.currentThread(), size, byteAlignment, false, false);
        }

        @ForceInline
        Arena pushFrame(Thread owner, long size, long byteAlignment, boolean clear, boolean fallbackAllocations) {
            boolean needsLock = Thread.currentThread().isVirtual() && !lock.isHeldByCurrentThread();
            if (needsLock && !lock.tryLock()) {
                // Rare: another virtual thread on the same carrier competed for acquisition.
                return MemorySessionImpl.createConfined(owner).asArena();
            }
            if (!stack.canAllocate(size, byteAlignment)) {
                if (needsLock) lock.unlock();
                return MemorySessionImpl.createConfined(owner).asArena();
            }
            return new Frame(owner, needsLock, size, byteAlignment, clear, fallbackAllocations);
        }

        static PerThread of(long byteSize, long byteAlignment) {
            return of(byteSize, byteAlignment, Arena.ofAuto(), false);
        }

        static PerThread ofCloseable(long byteSize, long byteAlignment) {
            return of(byteSize, byteAlignment, Arena.ofShared(), true);
        }

        private static PerThread of(long byteSize, long byteAlignment, Arena arena, boolean closeable) {
            try {
                return new PerThread(new ReentrantLock(),
                        arena,
                        new SlicingAllocator(arena.allocate(byteSize, byteAlignment)),
                        new CleanupAction(arena),
                        closeable);
            } catch (Throwable ex) {
                if (closeable) {
                    try {
                        arena.close();
                    } catch (Throwable suppressed) {
                        ex.addSuppressed(suppressed);
                    }
                }
                throw ex;
            }
        }

        @Override
        public void close() {
            if (closeable) {
                arena.close();
            }
        }

        private record CleanupAction(Arena arena) implements Consumer<MemorySegment> {
            @Override
            public void accept(MemorySegment memorySegment) {
                Reference.reachabilityFence(arena);
            }
        }

        final class Frame extends ArenaImpl {

            private final boolean locked;
            private final long parentOffset;
            private final long topOfStack;
            private final SlicingAllocator frame;
            private final boolean clear;
            private final boolean fallbackAllocations;

            @SuppressWarnings("restricted")
            @ForceInline
            public Frame(Thread owner, boolean locked, long byteSize, long byteAlignment,
                         boolean clear, boolean fallbackAllocations) {
                this.locked = locked;
                this.clear = clear;
                this.fallbackAllocations = fallbackAllocations;
                this.parentOffset = stack.currentOffset();
                final MemorySegment frameSegment = stack.allocate(byteSize, byteAlignment);
                this.topOfStack = stack.currentOffset();
                super(MemorySessionImpl.createConfined(owner));
                // The cleanup action will keep the original automatic `arena` (from which
                // the reusable segment is first allocated) alive even if this Frame
                // becomes unreachable but there are reachable segments still alive.
                this.frame = new SlicingAllocator(frameSegment.reinterpret(this, cleanupAction));
            }

            @ForceInline
            private void assertOrder() {
                if (topOfStack != stack.currentOffset())
                    throw new IllegalStateException("Out of order access: frame not top-of-stack");
            }

            @ForceInline
            @Override
            @SuppressWarnings("restricted")
            public NativeMemorySegmentImpl allocate(long byteSize, long byteAlignment) {
                return allocate0(byteSize, byteAlignment, clear);
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
                if (frame.canAllocate(byteSize, byteAlignment)) {
                    final NativeMemorySegmentImpl segment = (NativeMemorySegmentImpl)frame.allocate(byteSize, byteAlignment);
                    if (init) {
                        segment.fill((byte) 0);
                    }
                    return segment;
                }
                return fallbackAllocations ?
                        init ? super.allocate(byteSize, byteAlignment) : super.allocateNoInit(byteSize, byteAlignment) :
                        (NativeMemorySegmentImpl)frame.allocate(byteSize, byteAlignment);
            }

            @ForceInline
            @Override
            public void close() {
                assertOrder();
                // the Arena::close method is called "early" as it checks thread
                // confinement and crucially before any mutation of the internal
                // state takes place.
                super.close();
                stack.resetTo(parentOffset);
                if (locked) {
                    lock.unlock();
                }
            }
        }
    }

    public static BufferStack of(long byteSize, long byteAlignment) {
        if (byteSize < 0) {
            throw new IllegalArgumentException("Negative byteSize: " + byteSize);
        }
        if (byteAlignment < 0) {
            throw new IllegalArgumentException("Negative byteAlignment: " + byteAlignment);
        }
        return new BufferStack(byteSize, byteAlignment);
    }

    public static BufferStack of(long byteSize) {
        return new BufferStack(byteSize, 1);
    }

    public static BufferStack of(MemoryLayout layout) {
        // Implicit null check
        return of(layout.byteSize(), layout.byteAlignment());
    }
}
