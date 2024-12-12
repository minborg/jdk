/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test the addition of Boolean method in JDK 25
 * @run junit TestBooleanAdditions
 */

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.function.Predicate;

import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static org.junit.jupiter.api.Assertions.*;

final class TestBooleanAdditions {

    // == 0 -> false, != 0 -> true
    private static final Predicate<Byte> BYTE_TO_BOOLEAN = b -> b != 0;

    @Nested
    final class TestMemorySegment {

        @Test
        void ofArray() {
            var arr = new boolean[]{false, true};
            var segment = MemorySegment.ofArray(arr);
            assertEquals(arr.length, segment.byteSize());
            assertEquals(arr[0], segment.get(JAVA_BOOLEAN, 0));
            assertEquals(arr[1], segment.get(JAVA_BOOLEAN, 1));
            assertSame(arr, segment.heapBase().orElseThrow());
            assertFalse(segment.isNative());
        }

        @Test
        void toArray() {
            var arr = new boolean[]{false, true};
            var segment = MemorySegment.ofArray(arr);
            var actual = segment.toArray(JAVA_BOOLEAN);
            assertArrayEquals(arr, actual);
        }

        @Test
        void toArrayRawSegment() {
            var segment = MemorySegment.ofArray(byteArray());
            boolean[] actual = segment.toArray(JAVA_BOOLEAN);
            boolean[] expected = booleanArray();
            assertArrayEquals(expected, actual);
        }

        @Test
        void get() {
            var segment = MemorySegment.ofArray(byteArray());
            boolean[] expected = booleanArray();
            for (int i = 0; i < segment.byteSize(); i++) {
                assertEquals(expected[i], segment.get(JAVA_BOOLEAN, i));
            }
        }

        @Test
        void copyToArray() {
            var segment = MemorySegment.ofArray(byteArray());
            boolean[] expected = booleanArray();
            var arr = new boolean[(int) segment.byteSize()];
            for (int srcOffset = 0; srcOffset < 10; srcOffset++) {
                for (int dstOffset = 0; dstOffset < 10; dstOffset++) {
                    int maxIndex = Math.toIntExact(segment.byteSize() - srcOffset - dstOffset);
                    MemorySegment.copy(segment, JAVA_BOOLEAN, srcOffset, arr, dstOffset, maxIndex);
                    for (int i = srcOffset; i < maxIndex; i++) {
                        int segmentIndex = i + dstOffset;
                        assertEquals(expected[i + dstOffset], segment.get(JAVA_BOOLEAN, segmentIndex));
                    }
                }
            }
        }

    }

    @Nested
    final class TestSegmentAllocator {

        @Test
        void allocateFromScalar() {
            try (var arena = Arena.ofConfined()) {
                for (boolean value : List.of(false, true)) {
                    var segment = arena.allocateFrom(JAVA_BOOLEAN, value);
                    assertEquals(value, segment.get(JAVA_BOOLEAN, 0));
                    assertEquals(1, segment.byteSize());
                    assertTrue(segment.isNative());
                }
            }
        }

        @Test
        void allocateFromArray() {
            try (var arena = Arena.ofConfined()) {
                for (boolean[] value : List.of(new boolean[]{false, true}, new boolean[]{false, true, false, true})) {
                    var segment = arena.allocateFrom(JAVA_BOOLEAN, value);
                    assertArrayEquals(value, segment.toArray(JAVA_BOOLEAN));
                    assertEquals(value.length, segment.byteSize());
                    assertTrue(segment.isNative());
                }
            }
        }
    }

    private static byte[] byteArray() {
        byte[] arr = new byte[256];
        int i = 0;
        for (int v = Byte.MIN_VALUE; v <= Byte.MAX_VALUE; v++) {
            arr[i++] = (byte) v;
        }
        return arr;
    }

    private static boolean[] booleanArray() {
        byte[] bytes = byteArray();
        boolean[] arr = new boolean[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            arr[i] = BYTE_TO_BOOLEAN.test(bytes[i]);
        }
        return arr;
    }

}
