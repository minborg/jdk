/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestSharedMemoryPool
 */

// Todo: Also run with the main thread as a virtual thread (avoids using VThreadRunner for all test)

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryPool;
import java.util.stream.Stream;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.*;

final class TestSharedMemoryPool {

    private static final long SMALL_ALLOC_SIZE = JAVA_INT.byteSize();

    @ParameterizedTest
    @MethodSource("pools")
    void basic(MemoryPool pool) {
        Arena arena = pool.get();

        assertTrue(arena.scope().isAlive());
        arena.allocate(SMALL_ALLOC_SIZE);
        assertTrue(arena.scope().isAlive());

        arena.close();
        assertFalse(arena.scope().isAlive());
        assertTrue(arena.scope().toString().contains("ConfinedSession"));
    }

    @ParameterizedTest
    @MethodSource("pools")
    void basicZeroSize(MemoryPool pool) {
        Arena arena = pool.get();

        assertTrue(arena.scope().isAlive());
        arena.allocate(0);
        assertTrue(arena.scope().isAlive());

        arena.close();
        assertFalse(arena.scope().isAlive());
        assertTrue(arena.scope().toString().contains("ConfinedSession"));
    }


    private static Stream<MemoryPool> pools() {
        return Stream.of(
                MemoryPool.ofShared(SMALL_ALLOC_SIZE, 1, 0),
                MemoryPool.ofShared(SMALL_ALLOC_SIZE, 1, 1)
        );
    }

}
