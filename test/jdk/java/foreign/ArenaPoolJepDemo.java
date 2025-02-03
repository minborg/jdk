/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestDowncallBase
 *
 * @run testng/othervm -Xcheck:jni -XX:+IgnoreUnrecognizedVMOptions -XX:-VerifyDependencies
 *   --enable-native-access=ALL-UNNAMED -Dgenerator.sample.factor=17
 *   TestDowncallStack
 */

import org.testng.annotations.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.ArenaPool;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.testng.Assert.assertEquals;

public class ArenaPoolJepDemo {

    static final
    class Motivation {
        void demo() {

            try (var arena = Arena.ofConfined()) {
                MemorySegment segment = arena.allocateFrom(ValueLayout.JAVA_INT, 42);
                consume(segment);
            }

        }

        private static final int STDOUT_FILENO = 1;

        void print(MemorySegment first, MemorySegment second) {
            try (var arena = Arena.ofConfined()) {
                MemorySegment ccat = arena.allocate(first.byteSize() + second.byteSize() - 1);
                ccat.copyFrom(first);
                MemorySegment.copy(second, 0, ccat, first.byteSize() - 1, second.byteSize());
                write(STDOUT_FILENO, ccat, ccat.byteSize() - 1);
            }
        }
    }

    static final
    class Description {

        private static final ArenaPool ARENA_POOL = ArenaPool.create(ValueLayout.JAVA_INT);

        void demo() {

            try (var arena = ARENA_POOL.take()) {
                MemorySegment segment = arena.allocateFrom(ValueLayout.JAVA_INT, 42);
                consume(segment);
            }

        }

    }



    static void consume(MemorySegment segment) {

    }

    static void write(int fd, MemorySegment mem, long len) {}

}
