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

import jdk.internal.foreign.StackMemoryPool;

/**
 * A memory pool that may recycle {@linkplain MemorySegment}.
 *
 * @implNote All implementations of the MemoryPool interface are thread safe.
 */
public sealed interface MemoryPool
        permits
        StackMemoryPool,
        StackMemoryPool.PerPlatformThread,
        StackMemoryPool.PerVirtualThread {

    /**
     * {@return a new confined {@linkplain Arena} that will try to use recycled memory in
     *          order to satisfy {@linkplain Arena#allocate(long, long) allocation}
     *          requests}
     * <p>
     * When the returned {@linkplain Arena} is closed, its underlying memory may
     * be recycled back to the memory pool.
     * <p>
     * The returned Arena is {@linkplain Arena#ofConfined() confined} to the thread
     * that created it. In other words, this method is equivalent to the following:
     *
     * {@snippet lang=java :
     * Arena get() {
     *     return Arena.ofConfined();
     * }
     * }
     * Except, it may provide more performant Arena implementations.
     * <p>
     * The method can be invoked several times to create a plurality of distinct arenas.
     */
    Arena get();

    /**
     * {@return a new bound, per-thread memory pool of a certain {@code size} in bytes}
     * <p>
     * The returned memory pool will allocate backing memory up front and will always
     * recycle any and all memory returned to the pool upon {@linkplain Arena#close()}.
     * The backing memory will be allocated as if:
     *
     * {@snippet lang=java :
     * segmentAllocator.allocate(size);
     * }
     *
     * <p>
     * Memory segments {@linkplain Arena#allocate(long, long) allocated} via
     * {@linkplain Arena arenas} obtained from the returned arena pool
     * are zero-initialized.
     * <p>
     * Recycled memory in the pool will be returned to the operating system,
     * automatically, by the garbage collector at the earliest once all
     * arenas emanating from the pool have been closed and the allocating thread has died.
     * <p>
     * For virtual threads, the returned memory pool is using an efficient algorithm
     * to avoid creating a pool for each virtual thread. Instead, a pool of memory pools
     * is used that is shared across virtual threads as they become mounted on carrier
     * threads.
     * <p>
     * When the backing memory is fully allocated, subsequent allocations attempts will
     * throw IndexOutOfBoundsException.
     *
     * @param size the size in bytes the memory pool shall hold per platform/carrier thread
     * @throws IllegalArgumentException if the provided {@code size} is negative
     */
    // Todo: ofThreadLocal()?
    static MemoryPool ofStack(long size) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        return StackMemoryPool.of(size);
    }

}
