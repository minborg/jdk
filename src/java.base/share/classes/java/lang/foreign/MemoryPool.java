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

package java.lang.foreign;

import jdk.internal.foreign.SharedMemoryPool;
import jdk.internal.foreign.StackedMemoryPool;
import jdk.internal.foreign.Utils;

/**
 * A memory pool that may recycle {@linkplain MemorySegment}.
 *
 * @implNote All implementations of the MemoryPool interface are thread safe.
 */
public sealed interface MemoryPool
        permits SharedMemoryPool, StackedMemoryPool, StackedMemoryPool.PerPlatformThread, StackedMemoryPool.PerVirtualThread, StackedMemoryPool.SingleThreaded {

    /**
     * {@return a new confined {@linkplain Arena} that will try to use recycled memory in
     *          order to satisfy {@linkplain Arena#allocate(long, long) allocation}
     *          requests}
     * <p>
     * When the returned {@linkplain Arena} is closed, its underlying memory may
     * be recycled back to the memory pool.
     * <p>
     * The returned Arena is {@linkplain Arena#ofConfined() confined} to the thread
     * that created it. In other words, the semantics of this method is equivalent to
     * the following:
     *
     * {@snippet lang=java :
     * Arena get() {
     *     return Arena.ofConfined();
     * }
     * }
     * except, it may provide more performant Arena implementations.
     * <p>
     * The method can be invoked several times to create a plurality of distinct arenas.
     */
    Arena get();

    /**
     * {@return a new bound, per-thread, and stacked memory pool of a certain
     *          {@code byteSize} in bytes}
     * <p>
     * The returned stacked memory pool will allocate all backing <em>recyclable memory</em>
     * up front and will then try to provide access to the recyclable memory on
     * a best-effort basis. Upon {@linkplain Arena#close()} any and all recyclable memory
     * is returned to the memory pool.
     *
     * The <em>recyclable memory</em> of the memory pool will be allocated as:
     *
     * {@snippet lang=java :
     * segmentAllocator.allocate(byteSize);
     * }
     *
     * In order to provide maximum efficiency of the returned memory pool, clients are
     * encouraged to only use this memory pool for short-lived segment access
     * as shown in this example:
     *
     * {@snippet lang=java :
     * private static final MemoryPool POOL = MemoryPool.ofStacked(JAVA_INT.byteSize());
     *
     * try (var arena = POOL.get()) {
     *     var segment = arena.allocate(JAVA_INT);
     *     process(segment);
     * }
     * }
     * <p>
     * Several stacked arenas can be obtained from the memory pool but, they need to
     * be used in stack order in order to maximize performance. Arenas emanating from
     * the memory pool must be closed in reversed order or an
     * {@linkplain IllegalStateException} with be thrown.
     * Here is an example of a correct stacked arena example:
     *
     * {@snippet lang=java :
     * try (var firstArena = POOL.get()) {
     *     // Use the firstArena with pooling
     *     try (var secondArena = POOL.get()) {
     *         // Use the secondArena with pooling
     *         // Optionally use the firstArena but with no pooling
     *     }
     *     // Use the firstArena with pooling
     * }
     * }
     *
     * Arenas obtained from the returned memory pool allows allocation of more memory
     * than specified by the provided {@code byteSize} but then with no pooling. That is,
     * the memory pool gracefully degrades to allocating from a normal confined
     * {@linkplain Arena#ofConfined() Arena}.
     * <p>
     * Memory segments {@linkplain Arena#allocate(long, long) allocated} via
     * {@linkplain Arena arenas} obtained from the returned memory pool
     * are zero-initialized.
     * <p>
     * Recycled memory in the memory pool will be returned to the operating system,
     * automatically, by the garbage collector at the earliest once all
     * arenas emanating from the memory pool have been closed and the allocating thread
     * has died.
     * <p>
     * For virtual threads, the returned memory pool is using an efficient algorithm
     * to avoid creating distinct pools for each virtual thread. Instead, a pool of
     * memory pools is used that is shared across virtual threads as they become mounted
     * on carrier threads. Hence, it is not guaranteed, all virtual threads gain access
     * to the pool at every occasion. In such rare occasions, the memory pool gracefully
     * degrades to allocating from a normal confined {@linkplain Arena#ofConfined() Arena}.
     *
     * @param byteSize in bytes of the <em>recyclable memory</em> the memory pool shall hold
     *                 per platform/carrier thread
     * @throws IllegalArgumentException if the provided {@code byteSize} is negative
     */
    // Todo: rename ofThreadLocal()?
    // Todo: It is very easy to deplete the pool for a carrier thread:
    //       Thread.ofVirtual().start(pool::get);
    // Todo: provide factories for the usual (byteSize, byteAlignment), (MemoryLayout)
    static MemoryPool ofStacked(long byteSize) {
        if (byteSize < 0) {
            throw new IllegalArgumentException();
        }
        return new StackedMemoryPool(byteSize);
    }

    /**
     * {@return a new bound, single-threaded, and stacked memory pool of a certain
     *          {@code byteSize} in bytes}
     *
     * Todo: Write docs
     *
     * @param byteSize in bytes of the <em>recyclable memory</em> the memory pool shall hold
     *                 per platform/carrier thread
     * @throws IllegalArgumentException if the provided {@code byteSize} is negative
     */
    // Todo: Keep this?
    static MemoryPool ofStackedSingleThreadedOrWhateverItShallBeCalled(long byteSize) {
        return StackedMemoryPool.SingleThreaded.ofSingleThreaded(byteSize);
    }

    /**
     * {@return a new bound and shared memory pool of a certain
     *          {@code byteSize}, {@code byteAlignment}, and {@code maxBiasedThreads}}
     *
     * Todo: Write docs
     *
     * @param byteSize in bytes of the <em>recyclable memory</em> the memory pool shall hold
     *                 per platform/carrier thread
     * @param byteAlignment in bytes ... TBW
     * @param maxBiasedThreads the upper limit of how many threads that should get
     *                         preferential treatment ... TBW
     * @throws IllegalArgumentException if {@code byteSize < 0},
     *         {@code byteAlignment <= 0},
     *         or if {@code byteAlignment} is not a power of 2
     * @throws IllegalArgumentException if {@code maxBiasedThreads < 0}
     */
    static MemoryPool ofShared(long byteSize, long byteAlignment, int maxBiasedThreads) {
        Utils.checkNonNegativeArgument(byteSize, "byteSize");
        Utils.checkAlign(byteAlignment);
        Utils.checkNonNegativeArgument(maxBiasedThreads, "maxBiasedThreads");
        return SharedMemoryPool.of(byteSize, byteAlignment, maxBiasedThreads);
    }

}
