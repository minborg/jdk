/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test contiguous bit extraction and replacement for int and long
 */

public class BitFields {
    private static final long[] PATTERNS = {
        0L, 1L, -1L, Integer.MIN_VALUE, Integer.MAX_VALUE,
        Long.MIN_VALUE, Long.MAX_VALUE, 0xaaaa_aaaa_aaaa_aaaaL,
        0x5555_5555_5555_5555L, 0x0123_4567_89ab_cdefL
    };

    public static void main(String[] args) {
        assertEquals(10, Integer.extractBits(42, 2, 5), "example", 2, 5);
        assertEquals(42, Integer.replaceBits(2, 2, 5, 10), "example", 2, 5);

        for (int offset = 0; offset <= Integer.SIZE; offset++) {
            for (int length = 0; length <= Integer.SIZE - offset; length++) {
                for (long pattern : PATTERNS) {
                    int value = (int) pattern;
                    assertEquals((int) extract(value, offset, length),
                                 Integer.extractBits(value, offset, length),
                                 "Integer.extractBits", offset, length);
                    for (long field : PATTERNS) {
                        assertEquals((int) replace(value, offset, length, field),
                                     Integer.replaceBits(value, offset, length, (int) field),
                                     "Integer.replaceBits", offset, length);
                    }
                }
            }
        }

        for (int offset = 0; offset <= Long.SIZE; offset++) {
            for (int length = 0; length <= Long.SIZE - offset; length++) {
                for (long value : PATTERNS) {
                    assertEquals(extract(value, offset, length),
                                 Long.extractBits(value, offset, length),
                                 "Long.extractBits", offset, length);
                    for (long field : PATTERNS) {
                        assertEquals(replace(value, offset, length, field),
                                     Long.replaceBits(value, offset, length, field),
                                     "Long.replaceBits", offset, length);
                    }
                }
            }
        }

        checkInvalidRanges(Integer.SIZE, false);
        checkInvalidRanges(Long.SIZE, true);
    }

    // Reference implementations that copy one bit at a time.
    private static long extract(long value, int offset, int length) {
        long result = 0;
        for (int bit = 0; bit < length; bit++) {
            result |= ((value >>> (offset + bit)) & 1L) << bit;
        }
        return result;
    }

    private static long replace(long value, int offset, int length, long field) {
        for (int bit = 0; bit < length; bit++) {
            long mask = 1L << (offset + bit);
            if ((field & (1L << bit)) == 0) {
                value &= ~mask;
            } else {
                value |= mask;
            }
        }
        return value;
    }

    private static void checkInvalidRanges(int size, boolean wide) {
        int[][] invalid = {
            {-1, 0}, {0, -1}, {size + 1, 0}, {size, 1}, {0, size + 1},
            {1, size}, {Integer.MIN_VALUE, 1}, {1, Integer.MIN_VALUE},
            {Integer.MAX_VALUE, 0}, {0, Integer.MAX_VALUE},
            {Integer.MAX_VALUE, Integer.MAX_VALUE}
        };
        for (int[] range : invalid) {
            int offset = range[0];
            int length = range[1];
            if (wide) {
                assertInvalid(() -> Long.extractBits(0L, offset, length), offset, length);
                assertInvalid(() -> Long.replaceBits(0L, offset, length, -1L), offset, length);
            } else {
                assertInvalid(() -> Integer.extractBits(0, offset, length), offset, length);
                assertInvalid(() -> Integer.replaceBits(0, offset, length, -1), offset, length);
            }
        }
    }

    private static void assertInvalid(Runnable operation, int offset, int length) {
        try {
            operation.run();
        } catch (IndexOutOfBoundsException expected) {
            return;
        }
        throw new AssertionError("Accepted invalid range: offset=" + offset + ", length=" + length);
    }

    private static void assertEquals(long expected, long actual, String operation,
                                     int offset, int length) {
        if (expected != actual) {
            throw new AssertionError(operation + " offset=" + offset + ", length=" + length
                                     + ": expected 0x" + Long.toHexString(expected)
                                     + ", got 0x" + Long.toHexString(actual));
        }
    }
}
