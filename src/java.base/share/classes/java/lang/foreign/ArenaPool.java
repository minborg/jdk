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

import jdk.internal.foreign.ArenaPoolImpl;
import jdk.internal.vm.annotation.ForceInline;

import java.util.Objects;

/**
 * An arena pool allows a per-thread reuse of pre-allocated memory of a certain size and
 * alignment.
 * <p>
 * To be further expanded ...
 *
 * @since 25
 */
public sealed interface ArenaPool
        permits ArenaPoolImpl {

    /**
     * {@return a new Arena that might reuse pre-allocated memory}
     * <p>
     * Returned arenas are often short-lived as shown in this example:
     * {@snippet lang=java :
     * int result;
     * try (var arena = arenaPool.take()) {
     *     MemorySegment segment = arena.allocate(JAVA_INT);
     *     doMemoryOperation(segment); // Writes something into the segments
     *     result = segment.get(JAVA_INT, 0);
     * }
     * }
     * <p>
     * Memory segments {@linkplain Arena#allocate(long, long) allocated} by the returned arena
     * are zero-initialized.
     * <p>
     * It is imperative that the returned Arena is closed or else pre-allocated memory
     * may become inaccessible by the thread. For perpetual threads, this constitutes a memory leak.
     * <p>
     * To be further expanded ...
     */
    Arena take();

    /**
     * {@return a new arena pool that can return Arenas capable of recycling
     *          memory up to the given {@code byteSize} before allocating new
     *          memory}
     * <p>
     * To be further expanded ...
     *
     * @param byteSize the maximum amount of recyclable memory for a thread
     * @throws IllegalArgumentException if the provided {@code byteSize} is negative.
     */
    static ArenaPool create(long byteSize) {
        if (byteSize < 0) {
            throw new IllegalArgumentException();
        }
        return new ArenaPoolImpl(byteSize, 1L);
    }

    /**
     * {@return a new arena pool that can return Arenas capable of recycling
     *          memory up to the given {@code byteSize} before allocating new
     *          memory}
     * <p>
     * To be further expanded ...
     *
     * @param layout describing the alignment and size of recyclable memory
     *
     */
    static ArenaPool create(MemoryLayout layout) {
        Objects.requireNonNull(layout);
        return new ArenaPoolImpl(layout.byteSize(), layout.byteAlignment());
    }

    /**
     * {@return the global arena pool}
     */
    @ForceInline
    static ArenaPool global() {
        class Holder {
            static final ArenaPool GLOBAL_ARENA_POOL = new ArenaPoolImpl(
                    Integer.getInteger(
                            "jdk.internal.foreign.GLOBAL_ARENA_POOL_SIZE",
                            1 << 8), 1L);
        }
        return Holder.GLOBAL_ARENA_POOL;
    }

}
