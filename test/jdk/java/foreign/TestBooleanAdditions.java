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
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

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

    @Nested
    final class TestSegmentBulkOperations {

        private static final UnaryOperator<Byte> BYTE_TO_BOOLEAN_BYTE = b -> (byte) (b != 0 ? 1 : 0);

        // compress
        // 256 -> 8 -> 3 -> 2 -> 1
        private static final UnaryOperator<Byte> BIT_COUNT =
                b -> (byte) Integer.bitCount(Integer.bitCount(Integer.bitCount(Integer.bitCount(b))));

        private static final UnaryOperator<Byte> MASK_SIFT_OR =
                b -> (byte) (((b & 0x80) >> 7) |
                        ((b & 0x40) >> 6) |
                        ((b & 0x20) >> 5) |
                        ((b & 0x10) >> 4) |
                        ((b & 0x08) >> 3) |
                        ((b & 0x04) >> 2) |
                        ((b & 0x02) >> 1) |
                        ((b & 0x01)));

        private static final UnaryOperator<Byte> MIN = b -> (byte) Math.min(1, b & 0xff);

        private static final UnaryOperator<Byte> BIT_COUNT2 = b -> (byte) ((Integer.bitCount(b & 0xff) + 7) / 8);
        private static final UnaryOperator<Byte> X0 = b -> (byte) ((b | -b) >>> 31);
        private static final UnaryOperator<Byte> X1 = b -> (byte) (-(b & 0xff) >>> 31);
        private static final UnaryOperator<Byte> X2 = b -> (byte) (Integer.compress(-(b & 0xff), 1<<31));
        private static final UnaryOperator<Byte> X3 = b -> (byte)(b >>> 31 - Integer.numberOfLeadingZeros(b));
        private static final UnaryOperator<Byte> X4 = b -> (byte)((((~b & 0xFF) + 1) >> 8) ^ 1);

        @Test
        void branchLessConversion() {
            for (int i = Byte.MIN_VALUE; i <= Byte.MAX_VALUE; i++) {
                byte b = (byte) i;
                byte expected = BYTE_TO_BOOLEAN_BYTE.apply(b);
                assertEquals(expected, BIT_COUNT.apply(b), "BIT_COUNT: " + i);
                assertEquals(expected, MASK_SIFT_OR.apply(b), "MASK_SIFT_OR: " + i);
                assertEquals(expected, MIN.apply(b), "MIN: " + i);
                assertEquals(expected, BIT_COUNT2.apply(b), "BIT_COUNT2: " + i);
                assertEquals(expected, X0.apply(b), "X0: " + i);
                assertEquals(expected, X1.apply(b), "X1: " + i);
                assertEquals(expected, X2.apply(b), "X2: " + i);
                assertEquals(expected, X3.apply(b), "X3: " + i);
                assertEquals(expected, X4.apply(b), "X4: " + i);
            }
        }

        @Test
        void find() {
            int best = Integer.MAX_VALUE;
            for (int i = 0; i < 7; i++) {
                final int multiplier = i;
                System.out.println("multiplier = " + multiplier);
                UnaryOperator<Byte> mapper = b -> (byte) ((b * ~(b + multiplier) & 0x01));
                int err = 0;
                for (int v = Byte.MIN_VALUE; v <= Byte.MAX_VALUE; v++) {
                    byte b = (byte) v;
                    byte e = BYTE_TO_BOOLEAN_BYTE.apply(b);
                    byte a = mapper.apply(b);
                    if (!Objects.equals(e, a)) {
                        err++;
                        System.out.println("failed for = " + b + ", e=" + e + ", a=" + a);
                    }
                }
                System.out.println("i=" + i + ", err=" + err);
                best = Math.min(best, err);
            }
            System.out.println("best = " + best);
        }


    }

    static byte normalize(byte b) {
        return (byte) (b == 0 ? 0 : 1);
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
