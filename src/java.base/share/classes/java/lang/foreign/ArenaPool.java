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
import jdk.internal.foreign.Utils;
import jdk.internal.javac.PreviewFeature;
import jdk.internal.vm.annotation.ForceInline;

import java.util.Objects;

import static jdk.internal.javac.PreviewFeature.Feature.POOLED_MEMORY_ALLOCATION;

/**
 * An arena pool that allows the reuse of pre-allocated recyclable native memory regions.
 * <p>
 * The arena pool keeps an unspecified number of recyclable memory regions in the pool
 * where the recyclable memory regions have a certain size and alignment that is specified
 * upon creating the pool via {@linkplain #create(MemoryLayout) one of the factory methods}.
 * <p>
 * The pool attempts to provide recyclable memory regions fairly across threads on a
 * best-effort basis. The pool also tries to balance the number of recyclable memory
 * regions held internally in the pool considering trade-offs between performance and
 * lingering memory allocations.
 * <p>
 * If an Arena allocation operation cannot be satisfied by using a recyclable memory
 * region, new memory segments are allocated instead; allowing for graceful degradation.
 * <p>
 * By design, there is no "global" arena pool for the JDK as the use of such an arena
 * could introduce security and predictability concerns. Instead, distinct arena pools
 * should be used in specific application domains.
 *
 * @see Arena
 * @since 25
 */
@PreviewFeature(feature = POOLED_MEMORY_ALLOCATION)
public sealed interface ArenaPool
        permits ArenaPoolImpl {

    /**
     * {@return a new confined Arena that is free to use pre-allocated recyclable memory
     *          from the pool}
     * <p>
     * Segments allocated with a confined arena can be
     * {@linkplain MemorySegment#isAccessibleBy(Thread) accessed} by the thread that
     * created the arena, the arena's <em>owner thread</em>.
     * <p>
     * Memory segments {@linkplain Arena#allocate(long, long) allocated} by the returned
     * arena are zero-initialized.
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
     * The returned arena is free to recycle segments previously allocated and these
     * recyclable segments may not be zero-initialized until being recycled later on.
     * Security sensitive applications should therefore explicitly reset the content of
     * segments before the arena is closed, as shown in this example:
     * {@snippet lang=java :
     * int result;
     * try (var arena = arenaPool.take()) {
     *     MemorySegment segment = arena.allocate(JAVA_INT);
     *     doMemoryOperation(segment); // Writes something into the segments
     *     result = segment.get(JAVA_INT, 0);
     *     segment.fill((byte) 0); // Remove any security sensitive data
     * }
     * }
     *<p>
     * It is imperative that the returned Arena is closed after use or else recyclable
     * memory may become inaccessible and might therefore constitute a memory leak.
     */
    Arena take();

    /**
     * {@return a new arena pool that is free to return Arenas capable of recycling
     *          memory up to the given {@code byteSize} and with an alignment of 1
     *          byte before allocating new memory}
     *
     * @param byteSize the byte size for recyclable memory regions
     * @throws IllegalArgumentException if the provided {@code byteSize} is negative.
     */
    static ArenaPool create(long byteSize) {
        if (byteSize < 0) {
            throw new IllegalArgumentException();
        }
        return new ArenaPoolImpl(byteSize, 1L);
    }

    /**
     * {@return a new arena pool that is free to return Arenas capable of recycling
     *          memory up to the given {@code byteSize} and with an alignment of
     *          {@code byteAlignment} before allocating new memory}
     *
     * @param byteSize      the byte size for recyclable memory regions
     * @param byteAlignment the byte alignment for recyclable memory regions
     * @throws IllegalArgumentException if the provided {@code byteSize} is negative.
     * @throws IllegalArgumentException if the provided {@code byteAlignment} is not
     *                                  greater than zero or is not a power of two.
     */
    static ArenaPool create(long byteSize,
                            long byteAlignment) {
        Utils.checkAllocationSizeAndAlign(byteSize, byteAlignment);
        return new ArenaPoolImpl(byteSize, byteAlignment);
    }

    /**
     * {@return a new arena pool that is free to return Arenas capable of recycling
     *          memory with the given {@code layout.byteSize()} and with an alignment of
     *          {@code layout.byteAlignment()} before allocating new memory}
     *
     * @param layout describing the alignment and size of recyclable memory
     *
     */
    static ArenaPool create(MemoryLayout layout) {
        Objects.requireNonNull(layout);
        return new ArenaPoolImpl(layout.byteSize(), layout.byteAlignment());
    }

}
