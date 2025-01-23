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

/**
 * An arena pool allows a per-thread reuse of pre-allocated memory.
 * <p>
 * To be further expanded ...
 *
 * @see Arena#ofPooled()
 * @since 25
 */
public sealed interface ArenaPool
        permits ArenaPoolImpl {

    /**
     * {@return a new Arena that might reuse pre-allocated memory}
     * <p>
     * It is imperative that the returned Arena is closed or else pre-allocated memory
     * may be inaccessible, effectively creating a memory leak. Returned arenas are
     * often short-lived as shown in this example:
     * {@snippet lang=java :
     * int result;
     * try (var arena = arenaPool.take()) {
     *     MemorySegment segment = arena.allocateFrom(Java_INT, 0);
     *     doMemoryOperation(segment);
     *     result = segment.get(JAVA_INT, 0);
     * }
     * }
     * <p>
     * To be further expanded ...
     */
    Arena take();

    /**
     * {@return a new arena pool that can return Arenas capable of allocating
     *          memory up to the given {@code byteSize}}
     * <p>
     * To be further expanded ...
     *
     * @param byteSize the maximum aggregated arena allocations
     * @throws IllegalArgumentException if the provided {@code byteSize} is negative.
     */
    static ArenaPool create(long byteSize) {
        if (byteSize < 0) {
            throw new IllegalArgumentException();
        }
        return new ArenaPoolImpl(byteSize);
    }

}
