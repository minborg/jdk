/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.misc.VM;

import java.lang.foreign.Arena;

// This class is not thread safe.
final class ThreadConfinedArenaAllocator implements AutoCloseable {

    private static final String PROPERTY_NAME = "java.lang.foreign.confined.pool-size";
    private static final long POOL_SIZE = Integer.getInteger(PROPERTY_NAME, 512);
    private static final long POOL_ALIGNMENT = 64;

    private final BufferStack.PerThread stack;

    private ThreadConfinedArenaAllocator(BufferStack.PerThread stack) {
        this.stack = stack;
    }

    static ThreadConfinedArenaAllocator of() {
        if (VM.isDirectMemoryPageAligned() || POOL_SIZE <= 0) {
            return null;
        }
        try {
            return new ThreadConfinedArenaAllocator(BufferStack.PerThread.ofCloseable(POOL_SIZE, POOL_ALIGNMENT));
        } catch (OutOfMemoryError _) {
            return null;
        }

    }

    Arena acquire(Thread owner) {
        return stack.pushFrame(owner, POOL_SIZE, POOL_ALIGNMENT, true, true);
    }

    @Override
    public void close() {
        stack.close();
    }
}
