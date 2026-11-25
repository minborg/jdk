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
 * @summary Test fields, indexed fields, selections, and shuffles on int and long values
 * @run junit BitRecipesTest
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BitRecipesTest {
    private static final int[] INT_VALUES = {
            0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE, 0x5544_AABB
    };
    private static final long[] LONG_VALUES = {
            0, -1, Long.MIN_VALUE, Long.MAX_VALUE, 0x5544_AABB_6677_8899L
    };

    @Test
    void intFields() {
        for (int width = 0; width <= Integer.SIZE; width++) {
            for (int offset = 0; offset <= Integer.SIZE - width; offset++) {
                int mask = (int) fieldMask(offset, width);
                int end = offset + width;
                var field = IntBits.Field.of(offset, width);
                var fromMask = IntBits.Field.fromMask(mask);
                var fromRange = IntBits.Field.ofRange(offset, end);
                var fromEnd = IntBits.Field.endingAt(end, width);
                assertEquals(offset, field.offset());
                assertEquals(width, field.width());
                assertEquals(end, field.endExclusive());
                assertEquals(mask, field.mask());
                assertEquals(mask == 0 ? 0 : offset, fromMask.offset());
                assertEquals(width, fromMask.width());
                assertEquals(mask == 0 ? 0 : end, fromMask.endExclusive());
                assertEquals(mask, fromMask.mask());
                assertEquals(offset, fromRange.offset());
                assertEquals(width, fromRange.width());
                assertEquals(end, fromRange.endExclusive());
                assertEquals(mask, fromRange.mask());
                assertEquals(offset, fromEnd.offset());
                assertEquals(width, fromEnd.width());
                assertEquals(end, fromEnd.endExclusive());
                assertEquals(mask, fromEnd.mask());
                for (int i = 0; i < INT_VALUES.length; i++) {
                    int word = INT_VALUES[i];
                    int value = INT_VALUES[INT_VALUES.length - 1 - i];
                    int extracted = Integer.compress(word, mask);
                    int signed = (int) signExtend(Integer.toUnsignedLong(extracted), width);
                    int replaced = (word & ~mask) | Integer.expand(value, mask);
                    assertEquals(extracted, field.extract(word));
                    assertEquals(signed, field.extractSigned(word));
                    assertEquals(replaced, field.replace(word, value));
                    assertEquals(extracted, fromMask.extract(word));
                    assertEquals(signed, fromMask.extractSigned(word));
                    assertEquals(replaced, fromMask.replace(word, value));
                    assertEquals(extracted, fromRange.extract(word));
                    assertEquals(signed, fromRange.extractSigned(word));
                    assertEquals(replaced, fromRange.replace(word, value));
                }
            }
        }
    }

    @Test
    void longFields() {
        for (int width = 0; width <= Long.SIZE; width++) {
            for (int offset = 0; offset <= Long.SIZE - width; offset++) {
                long mask = fieldMask(offset, width);
                int end = offset + width;
                var field = LongBits.Field.of(offset, width);
                var fromMask = LongBits.Field.fromMask(mask);
                var fromRange = LongBits.Field.ofRange(offset, end);
                var fromEnd = LongBits.Field.endingAt(end, width);
                assertEquals(offset, field.offset());
                assertEquals(width, field.width());
                assertEquals(end, field.endExclusive());
                assertEquals(mask, field.mask());
                assertEquals(mask == 0 ? 0 : offset, fromMask.offset());
                assertEquals(width, fromMask.width());
                assertEquals(mask == 0 ? 0 : end, fromMask.endExclusive());
                assertEquals(mask, fromMask.mask());
                assertEquals(offset, fromRange.offset());
                assertEquals(width, fromRange.width());
                assertEquals(end, fromRange.endExclusive());
                assertEquals(mask, fromRange.mask());
                assertEquals(offset, fromEnd.offset());
                assertEquals(width, fromEnd.width());
                assertEquals(end, fromEnd.endExclusive());
                assertEquals(mask, fromEnd.mask());
                for (int i = 0; i < LONG_VALUES.length; i++) {
                    long word = LONG_VALUES[i];
                    long value = LONG_VALUES[LONG_VALUES.length - 1 - i];
                    long extracted = Long.compress(word, mask);
                    long signed = signExtend(extracted, width);
                    long replaced = (word & ~mask) | Long.expand(value, mask);
                    assertEquals(extracted, field.extract(word));
                    assertEquals(signed, field.extractSigned(word));
                    assertEquals(replaced, field.replace(word, value));
                    assertEquals(extracted, fromMask.extract(word));
                    assertEquals(signed, fromMask.extractSigned(word));
                    assertEquals(replaced, fromMask.replace(word, value));
                    assertEquals(extracted, fromRange.extract(word));
                    assertEquals(signed, fromRange.extractSigned(word));
                    assertEquals(replaced, fromRange.replace(word, value));
                }
            }
        }
    }

    @Test
    void invalidFields() {
        assertThrows(IllegalArgumentException.class, () -> IntBits.Field.of(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> IntBits.Field.of(0, -1));
        assertThrows(IllegalArgumentException.class, () -> IntBits.Field.of(0, 33));
        assertThrows(IllegalArgumentException.class, () -> IntBits.Field.of(31, 2));
        assertThrows(IllegalArgumentException.class, () -> IntBits.Field.of(33, 0));
        assertThrows(IllegalArgumentException.class, () -> IntBits.Field.of(Integer.MAX_VALUE, 2));
        assertThrows(IllegalArgumentException.class, () -> IntBits.Field.fromMask(0x8000_0001));
        assertThrows(IllegalArgumentException.class, () -> LongBits.Field.of(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> LongBits.Field.of(0, -1));
        assertThrows(IllegalArgumentException.class, () -> LongBits.Field.of(0, 65));
        assertThrows(IllegalArgumentException.class, () -> LongBits.Field.of(63, 2));
        assertThrows(IllegalArgumentException.class, () -> LongBits.Field.of(65, 0));
        assertThrows(IllegalArgumentException.class, () -> LongBits.Field.of(Integer.MAX_VALUE, 2));
        assertThrows(IllegalArgumentException.class, () -> LongBits.Field.fromMask(0x8000_0000_0000_0001L));
    }

    @Test
    void invalidEndingFields() {
        int[][] invalidEndsAndWidths = {
                {-1, 0}, {0, -1}, {0, 1}, {5, 6},
                {Integer.MIN_VALUE, 1}, {0, Integer.MIN_VALUE},
                {Integer.MAX_VALUE, Integer.MAX_VALUE}, {1, Integer.MAX_VALUE}
        };
        for (int[] pair : invalidEndsAndWidths) {
            int end = pair[0];
            int width = pair[1];
            assertThrows(IllegalArgumentException.class, () -> IntBits.Field.endingAt(end, width));
            assertThrows(IllegalArgumentException.class, () -> LongBits.Field.endingAt(end, width));
        }
        assertThrows(IllegalArgumentException.class, () -> IntBits.Field.endingAt(Integer.SIZE + 1, 0));
        assertThrows(IllegalArgumentException.class, () -> LongBits.Field.endingAt(Long.SIZE + 1, 0));
    }

    @Test
    void closedRanges() {
        for (int first = 0; first < Integer.SIZE; first++) {
            for (int last = first; last < Integer.SIZE; last++) {
                int width = last - first + 1;
                var field = IntBits.Field.ofClosedRange(first, last);
                assertEquals(first, field.offset());
                assertEquals(width, field.width());
                assertEquals(last + 1, field.endExclusive());
                assertEquals((int) fieldMask(first, width), field.mask());
            }
        }
        for (int first = 0; first < Long.SIZE; first++) {
            for (int last = first; last < Long.SIZE; last++) {
                int width = last - first + 1;
                var field = LongBits.Field.ofClosedRange(first, last);
                assertEquals(first, field.offset());
                assertEquals(width, field.width());
                assertEquals(last + 1, field.endExclusive());
                assertEquals(fieldMask(first, width), field.mask());
            }
        }
        for (int[] ends : invalidClosedRanges(Integer.SIZE)) {
            assertThrows(IllegalArgumentException.class,
                    () -> IntBits.Field.ofClosedRange(ends[0], ends[1]));
        }
        for (int[] ends : invalidClosedRanges(Long.SIZE)) {
            assertThrows(IllegalArgumentException.class,
                    () -> LongBits.Field.ofClosedRange(ends[0], ends[1]));
        }
    }

    private static int[][] invalidClosedRanges(int size) {
        return new int[][] {
                {-1, 0}, {0, -1}, {2, 1}, {0, size}, {size, size},
                {Integer.MIN_VALUE, Integer.MAX_VALUE}, {Integer.MAX_VALUE, Integer.MIN_VALUE}
        };
    }

    @Test
    void invalidHalfOpenRanges() {
        for (int[] ends : invalidHalfOpenRangeEndpoints(Integer.SIZE)) {
            assertThrows(IllegalArgumentException.class,
                    () -> IntBits.Field.ofRange(ends[0], ends[1]));
        }
        for (int[] ends : invalidHalfOpenRangeEndpoints(Long.SIZE)) {
            assertThrows(IllegalArgumentException.class,
                    () -> LongBits.Field.ofRange(ends[0], ends[1]));
        }
    }

    private static int[][] invalidHalfOpenRangeEndpoints(int size) {
        return new int[][] {
                {-1, 0}, {-1, -1}, {0, -1}, {2, 1}, {0, size + 1}, {size + 1, size + 1},
                {0, Integer.MAX_VALUE}, {1, Integer.MIN_VALUE},
                {Integer.MIN_VALUE, Integer.MAX_VALUE}, {Integer.MAX_VALUE, Integer.MIN_VALUE}
        };
    }

    @Test
    void intIndexedFields() {
        for (IndexLayout layout : indexedLayouts(Integer.SIZE)) {
            var field = IntBits.IndexedField.of(layout.first(), layout.width(), layout.stride(), layout.count());
            int unionMask = 0;
            for (int index = 0; index < layout.count(); index++) {
                int mask = (int) fieldMask(layout.offset(index), layout.width());
                unionMask |= mask;
                for (int i = 0; i < INT_VALUES.length; i++) {
                    int word = INT_VALUES[i];
                    int value = INT_VALUES[INT_VALUES.length - 1 - i];
                    int extracted = Integer.compress(word, mask);
                    int signed = (int) signExtend(Integer.toUnsignedLong(extracted), layout.width());
                    assertEquals(extracted, field.extract(word, index));
                    assertEquals(signed, field.extractSigned(word, index));
                    assertEquals((word & ~mask) | Integer.expand(value, mask), field.replace(word, index, value));
                }
            }
            checkIntSelection(field.asSelection(), unionMask, 0x5544_AABB, 0xAABB_5544);
            assertThrows(IndexOutOfBoundsException.class, () -> field.extract(-1, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> field.extractSigned(-1, layout.count()));
            assertThrows(IndexOutOfBoundsException.class, () -> field.replace(-1, Integer.MAX_VALUE, 0));
        }
        var repeated = IntBits.IndexedField.of(31, 1, 0, Integer.MAX_VALUE);
        assertEquals(1, repeated.extract(Integer.MIN_VALUE, Integer.MAX_VALUE - 1));
        assertEquals(Integer.MIN_VALUE, repeated.asSelection().expand(-1));
        assertEquals(0, IntBits.IndexedField.of(Integer.SIZE, 0, 0, Integer.MAX_VALUE).asSelection().expand(-1));
    }

    @Test
    void longIndexedFields() {
        for (IndexLayout layout : indexedLayouts(Long.SIZE)) {
            var field = LongBits.IndexedField.of(layout.first(), layout.width(), layout.stride(), layout.count());
            long unionMask = 0;
            for (int index = 0; index < layout.count(); index++) {
                long mask = fieldMask(layout.offset(index), layout.width());
                unionMask |= mask;
                for (int i = 0; i < LONG_VALUES.length; i++) {
                    long word = LONG_VALUES[i];
                    long value = LONG_VALUES[LONG_VALUES.length - 1 - i];
                    long extracted = Long.compress(word, mask);
                    assertEquals(extracted, field.extract(word, index));
                    assertEquals(signExtend(extracted, layout.width()), field.extractSigned(word, index));
                    assertEquals((word & ~mask) | Long.expand(value, mask), field.replace(word, index, value));
                }
            }
            checkLongSelection(field.asSelection(), unionMask, 0x5544_AABB_6677_8899L, 0x9988_7766_BBAA_4455L);
            assertThrows(IndexOutOfBoundsException.class, () -> field.extract(-1L, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> field.extractSigned(-1L, layout.count()));
            assertThrows(IndexOutOfBoundsException.class, () -> field.replace(-1L, Integer.MAX_VALUE, 0));
        }
        var repeated = LongBits.IndexedField.of(63, 1, 0, Integer.MAX_VALUE);
        assertEquals(1L, repeated.extract(Long.MIN_VALUE, Integer.MAX_VALUE - 1));
        assertEquals(Long.MIN_VALUE, repeated.asSelection().expand(-1L));
        assertEquals(0L, LongBits.IndexedField.of(Long.SIZE, 0, 0, Integer.MAX_VALUE).asSelection().expand(-1L));

        var lowNibbles = LongBits.IndexedField.of(0, 4, 8, 8).asSelection();
        checkLongSelection(lowNibbles, 0x0F0F_0F0F_0F0F_0F0FL, 0x1122_3344_5566_7788L, 0xABCD_EF01L);
    }

    @Test
    void invalidIndexedFields() {
        for (IndexLayout layout : invalidIndexedLayouts(Integer.SIZE)) {
            assertThrows(IllegalArgumentException.class,
                    () -> IntBits.IndexedField.of(layout.first(), layout.width(), layout.stride(), layout.count()),
                    layout.toString());
        }
        for (IndexLayout layout : invalidIndexedLayouts(Long.SIZE)) {
            assertThrows(IllegalArgumentException.class,
                    () -> LongBits.IndexedField.of(layout.first(), layout.width(), layout.stride(), layout.count()),
                    layout.toString());
        }
    }

    @Test
    void sparseSelections() {
        int[] intMasks = {0, -1, Integer.MIN_VALUE, 1, 0x00F8_FCF8, 0x8000_0001};
        long[] longMasks = {0, -1, Long.MIN_VALUE, 1, 0x0F0F_0F0F_0F0F_0F0FL, 0x8000_0000_0000_0001L};
        for (int mask : intMasks) {
            for (int word : INT_VALUES) {
                checkIntSelection(mask, word, ~word);
            }
        }
        for (long mask : longMasks) {
            for (long word : LONG_VALUES) {
                checkLongSelection(mask, word, ~word);
            }
        }
        var random = new SplittableRandom(0xB17);
        for (int i = 0; i < 1_000; i++) {
            checkIntSelection(random.nextInt(), random.nextInt(), random.nextInt());
            checkLongSelection(random.nextLong(), random.nextLong(), random.nextLong());
        }
    }

    @Test
    void intSelectionsOfFields() {
        var high = IntBits.Field.ofClosedRange(28, 31);
        var overlap = IntBits.Field.ofClosedRange(25, 29);
        var low = IntBits.Field.ofClosedRange(0, 0);
        var empty = IntBits.Field.of(Integer.SIZE, 0);
        var full = IntBits.Field.ofClosedRange(0, Integer.SIZE - 1);
        IntBits.Field[] fields = {high, empty, overlap, high, low};
        var selection = IntBits.Selection.ofFields(fields);
        fields[0] = full;
        checkIntSelection(selection, 0xFE00_0001, 0xA400_0001, 0xB3);
        checkIntSelection(IntBits.Selection.ofFields(low, overlap, high), 0xFE00_0001, 0xA400_0001, 0xB3);

        var rgb565 = IntBits.Selection.ofFields(IntBits.Field.ofClosedRange(19, 23),
                IntBits.Field.ofClosedRange(10, 15), IntBits.Field.ofClosedRange(3, 7));
        checkIntSelection(rgb565, 0x00F8_FCF8, 0xFF53_AB22, 0xCAFE);
        assertEquals(-1, IntBits.Selection.ofFields(full).expand(-1));
        assertEquals(0, IntBits.Selection.ofFields().expand(-1));
        assertEquals(0, IntBits.Selection.ofFields(empty).expand(-1));
        assertThrows(NullPointerException.class, () -> IntBits.Selection.ofFields((IntBits.Field[]) null));
        assertThrows(NullPointerException.class, () -> IntBits.Selection.ofFields(full, null));
    }

    @Test
    void longSelectionsOfFields() {
        var high = LongBits.Field.ofClosedRange(60, 63);
        var overlap = LongBits.Field.ofClosedRange(57, 61);
        var low = LongBits.Field.ofClosedRange(0, 0);
        var empty = LongBits.Field.of(Long.SIZE, 0);
        var full = LongBits.Field.ofClosedRange(0, Long.SIZE - 1);
        LongBits.Field[] fields = {high, empty, overlap, high, low};
        var selection = LongBits.Selection.ofFields(fields);
        fields[0] = full;
        checkLongSelection(selection, 0xFE00_0000_0000_0001L, 0xA400_0000_0000_0001L, 0xB3L);
        checkLongSelection(LongBits.Selection.ofFields(low, overlap, high),
                0xFE00_0000_0000_0001L, 0xA400_0000_0000_0001L, 0xB3L);

        assertEquals(-1L, LongBits.Selection.ofFields(full).expand(-1L));
        assertEquals(0L, LongBits.Selection.ofFields().expand(-1L));
        assertEquals(0L, LongBits.Selection.ofFields(empty).expand(-1L));
        assertThrows(NullPointerException.class, () -> LongBits.Selection.ofFields((LongBits.Field[]) null));
        assertThrows(NullPointerException.class, () -> LongBits.Selection.ofFields(full, null));
    }

    @Test
    void shufflesOfBits() {
        int[] intSources = new int[Integer.SIZE];
        Arrays.fill(intSources, IntBits.Shuffle.ZERO);
        intSources[0] = intSources[1] = Integer.SIZE - 1;
        intSources[30] = IntBits.Shuffle.ONE;
        intSources[31] = 0;
        var intShuffle = IntBits.Shuffle.ofBits(intSources);
        Arrays.fill(intSources, IntBits.Shuffle.ZERO);
        assertEquals(0x4000_0000, intShuffle.rearrange(0));
        assertEquals(0xC000_0003, intShuffle.rearrange(Integer.MIN_VALUE | 1));
        assertThrows(IllegalStateException.class, intShuffle::inverse);
        Arrays.fill(intSources, IntBits.Shuffle.ONE);
        assertEquals(-1, IntBits.Shuffle.ofBits(intSources).rearrange(0));

        int[] longSources = new int[Long.SIZE];
        Arrays.fill(longSources, LongBits.Shuffle.ZERO);
        longSources[0] = longSources[1] = Long.SIZE - 1;
        longSources[62] = LongBits.Shuffle.ONE;
        longSources[63] = 0;
        var longShuffle = LongBits.Shuffle.ofBits(longSources);
        Arrays.fill(longSources, LongBits.Shuffle.ZERO);
        assertEquals(0x4000_0000_0000_0000L, longShuffle.rearrange(0L));
        assertEquals(0xC000_0000_0000_0003L, longShuffle.rearrange(Long.MIN_VALUE | 1L));
        assertThrows(IllegalStateException.class, longShuffle::inverse);
        Arrays.fill(longSources, LongBits.Shuffle.ONE);
        assertEquals(-1L, LongBits.Shuffle.ofBits(longSources).rearrange(0L));
    }

    @Test
    void randomBitShuffles() {
        var random = new SplittableRandom(0xB175);
        for (int i = 0; i < 100; i++) {
            int[] intSources = random.ints(Integer.SIZE, IntBits.Shuffle.ONE, Integer.SIZE).toArray();
            int word = random.nextInt();
            int expected = (int) referenceShuffle(word, intSources, IntBits.Shuffle.ONE);
            assertEquals(expected, IntBits.Shuffle.ofBits(intSources).rearrange(word));

            int[] longSources = random.ints(Long.SIZE, LongBits.Shuffle.ONE, Long.SIZE).toArray();
            long longWord = random.nextLong();
            long longExpected = referenceShuffle(longWord, longSources, LongBits.Shuffle.ONE);
            assertEquals(longExpected, LongBits.Shuffle.ofBits(longSources).rearrange(longWord));
        }
    }

    private static long referenceShuffle(long word, int[] sources, int one) {
        long result = 0;
        for (int destination = 0; destination < sources.length; destination++) {
            int source = sources[destination];
            if (source == one || (source >= 0 && ((word >>> source) & 1L) != 0)) {
                result |= 1L << destination;
            }
        }
        return result;
    }

    @Test
    void shufflesOfBytes() {
        int[] intSources = {2, 1, 0, IntBits.Shuffle.ZERO};
        var intShuffle = IntBits.Shuffle.ofBytes(intSources);
        intSources[0] = IntBits.Shuffle.ONE;
        assertEquals(0x0056_3412, intShuffle.rearrange(0xEF12_3456));
        assertEquals(0x00AA_AAFF, IntBits.Shuffle.ofBytes(IntBits.Shuffle.ONE, 3, 3, IntBits.Shuffle.ZERO)
                .rearrange(0xAA11_8855));

        int[] longSources = {7, LongBits.Shuffle.ONE, 4, 4, LongBits.Shuffle.ZERO, 2, 0, 6};
        var longShuffle = LongBits.Shuffle.ofBytes(longSources);
        longSources[0] = LongBits.Shuffle.ZERO;
        assertEquals(0xDC10_5400_9898_FFFEL, longShuffle.rearrange(0xFEDC_BA98_7654_3210L));
    }

    @Test
    void inverseShuffles() {
        int[] intSources = new int[Integer.SIZE];
        Arrays.setAll(intSources, bit -> (bit + 5) % Integer.SIZE);
        var intShuffle = IntBits.Shuffle.ofBits(intSources);
        intSources[0] = IntBits.Shuffle.ZERO;
        var intInverse = intShuffle.inverse();
        for (int word : INT_VALUES) {
            assertEquals(Integer.rotateRight(word, 5), intShuffle.rearrange(word));
            assertEquals(word, intInverse.rearrange(intShuffle.rearrange(word)));
            assertEquals(word, intShuffle.rearrange(intInverse.rearrange(word)));
        }
        assertThrows(IllegalStateException.class, IntBits.Shuffle.ofBytes(0, 0, 2, 3)::inverse);
        assertThrows(IllegalStateException.class, IntBits.Shuffle.ofBits(intSources)::inverse);

        int[] longSources = new int[Long.SIZE];
        Arrays.setAll(longSources, bit -> (bit + 5) % Long.SIZE);
        var longShuffle = LongBits.Shuffle.ofBits(longSources);
        longSources[0] = LongBits.Shuffle.ONE;
        var longInverse = longShuffle.inverse();
        for (long word : LONG_VALUES) {
            assertEquals(Long.rotateRight(word, 5), longShuffle.rearrange(word));
            assertEquals(word, longInverse.rearrange(longShuffle.rearrange(word)));
            assertEquals(word, longShuffle.rearrange(longInverse.rearrange(word)));
        }
        assertThrows(IllegalStateException.class, LongBits.Shuffle.ofBytes(0, 0, 2, 3, 4, 5, 6, 7)::inverse);
        assertThrows(IllegalStateException.class, LongBits.Shuffle.ofBits(longSources)::inverse);
    }

    @Test
    void shufflesOfFields() {
        var intHigh = IntBits.Field.ofRange(28, 32);
        var intOverlap = IntBits.Field.ofRange(26, 30);
        var intEmpty = IntBits.Field.ofRange(Integer.SIZE, Integer.SIZE);
        var intLow = IntBits.Field.ofRange(0, 2);
        var intFull = IntBits.Field.ofRange(0, Integer.SIZE);
        IntBits.Field[] intFields = {intHigh, intEmpty, intOverlap, intHigh, intLow};
        var intShuffle = IntBits.Shuffle.concatFields(intFields);
        intFields[0] = intEmpty;
        assertEquals(0x3D6D, intShuffle.rearrange(0xD800_0003));
        assertEquals(0, IntBits.Shuffle.concatFields().rearrange(-1));
        assertEquals(Integer.MIN_VALUE, IntBits.Shuffle.concatFields(intFull).rearrange(Integer.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> IntBits.Shuffle.concatFields(intFull, intLow));
        assertThrows(NullPointerException.class, () -> IntBits.Shuffle.concatFields((IntBits.Field[]) null));
        assertThrows(NullPointerException.class, () -> IntBits.Shuffle.concatFields(intFull, null));

        var longHigh = LongBits.Field.ofRange(60, 64);
        var longOverlap = LongBits.Field.ofRange(58, 62);
        var longEmpty = LongBits.Field.ofRange(Long.SIZE, Long.SIZE);
        var longLow = LongBits.Field.ofRange(0, 2);
        var longFull = LongBits.Field.ofRange(0, Long.SIZE);
        LongBits.Field[] longFields = {longHigh, longEmpty, longOverlap, longHigh, longLow};
        var longShuffle = LongBits.Shuffle.concatFields(longFields);
        longFields[0] = longEmpty;
        assertEquals(0x3D6DL, longShuffle.rearrange(0xD800_0000_0000_0003L));
        assertEquals(0L, LongBits.Shuffle.concatFields().rearrange(-1L));
        assertEquals(Long.MIN_VALUE, LongBits.Shuffle.concatFields(longFull).rearrange(Long.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> LongBits.Shuffle.concatFields(longFull, longLow));
        assertThrows(NullPointerException.class, () -> LongBits.Shuffle.concatFields((LongBits.Field[]) null));
        assertThrows(NullPointerException.class, () -> LongBits.Shuffle.concatFields(longFull, null));
    }

    @Test
    void intShufflesHighToLow() {
        var high = IntBits.Field.ofRange(28, 32);
        var low = IntBits.Field.ofRange(0, 4);
        var empty = IntBits.Field.of(Integer.SIZE, 0);
        var full = IntBits.Field.of(0, Integer.SIZE);
        IntBits.Shuffle.Part[] parts = {
                IntBits.Shuffle.literal(0x1A, 4), high, IntBits.Shuffle.zeros(4),
                empty, high, IntBits.Shuffle.literal(-1, 0), low
        };
        var shuffle = IntBits.Shuffle.concatHighToLow(parts);
        parts[0] = low;
        assertEquals(0x000A_D0D3, shuffle.rearrange(0xD800_0003));
        assertThrows(IllegalStateException.class, shuffle::inverse);
        assertEquals(0, IntBits.Shuffle.concatHighToLow().rearrange(-1));
        assertEquals(Integer.MIN_VALUE, IntBits.Shuffle.concatHighToLow(full).rearrange(Integer.MIN_VALUE));
        assertEquals(-1, IntBits.Shuffle.concatHighToLow(IntBits.Shuffle.ones(Integer.SIZE)).rearrange(0));
        assertEquals(0, IntBits.Shuffle.concatHighToLow(IntBits.Shuffle.zeros(Integer.SIZE)).rearrange(-1));
        int literal = 0x89AB_CDEF;
        assertEquals(literal, IntBits.Shuffle.concatHighToLow(IntBits.Shuffle.literal(literal, Integer.SIZE))
                .rearrange(0));
    }

    @Test
    void longShufflesHighToLow() {
        var high = LongBits.Field.ofRange(60, 64);
        var low = LongBits.Field.ofRange(0, 4);
        var empty = LongBits.Field.of(Long.SIZE, 0);
        var full = LongBits.Field.of(0, Long.SIZE);
        LongBits.Shuffle.Part[] parts = {
                LongBits.Shuffle.literal(0x1A, 4), high, LongBits.Shuffle.zeros(4),
                empty, high, LongBits.Shuffle.literal(-1, 0), low
        };
        var shuffle = LongBits.Shuffle.concatHighToLow(parts);
        parts[0] = low;
        assertEquals(0x000A_D0D3L, shuffle.rearrange(0xD800_0000_0000_0003L));
        assertThrows(IllegalStateException.class, shuffle::inverse);
        assertEquals(0L, LongBits.Shuffle.concatHighToLow().rearrange(-1L));
        assertEquals(Long.MIN_VALUE, LongBits.Shuffle.concatHighToLow(full).rearrange(Long.MIN_VALUE));
        assertEquals(-1L, LongBits.Shuffle.concatHighToLow(LongBits.Shuffle.ones(Long.SIZE)).rearrange(0L));
        assertEquals(0L, LongBits.Shuffle.concatHighToLow(LongBits.Shuffle.zeros(Long.SIZE)).rearrange(-1L));
        long literal = 0x89AB_CDEF_0123_4567L;
        assertEquals(literal, LongBits.Shuffle.concatHighToLow(LongBits.Shuffle.literal(literal, Long.SIZE))
                .rearrange(0L));
        assertEquals(0x8000_0000_7FFF_FFFFL, LongBits.Shuffle.concatHighToLow(
                LongBits.Shuffle.literal(1L << 32, 33), LongBits.Field.of(33, 31)).rearrange(-1L));
    }

    @Test
    void invalidHighToLowShuffles() {
        assertThrows(IllegalArgumentException.class, () -> IntBits.Shuffle.literal(0, -1));
        assertThrows(IllegalArgumentException.class, () -> IntBits.Shuffle.ones(Integer.SIZE + 1));
        assertThrows(IllegalArgumentException.class, () -> IntBits.Shuffle.zeros(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> IntBits.Shuffle.concatHighToLow(
                IntBits.Field.of(0, Integer.SIZE), IntBits.Shuffle.ones(1)));
        assertThrows(NullPointerException.class, () -> IntBits.Shuffle.concatHighToLow((IntBits.Shuffle.Part[]) null));
        assertThrows(NullPointerException.class, () -> IntBits.Shuffle.concatHighToLow(IntBits.Shuffle.zeros(0), null));

        assertThrows(IllegalArgumentException.class, () -> LongBits.Shuffle.literal(0, -1));
        assertThrows(IllegalArgumentException.class, () -> LongBits.Shuffle.ones(Long.SIZE + 1));
        assertThrows(IllegalArgumentException.class, () -> LongBits.Shuffle.zeros(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> LongBits.Shuffle.concatHighToLow(
                LongBits.Shuffle.zeros(1), LongBits.Field.of(0, Long.SIZE)));
        assertThrows(NullPointerException.class, () -> LongBits.Shuffle.concatHighToLow((LongBits.Shuffle.Part[]) null));
        assertThrows(NullPointerException.class, () -> LongBits.Shuffle.concatHighToLow(LongBits.Shuffle.zeros(0), null));
    }

    @Test
    void invalidShuffles() {
        assertThrows(NullPointerException.class, () -> IntBits.Shuffle.ofBits((int[]) null));
        assertThrows(NullPointerException.class, () -> IntBits.Shuffle.ofBytes((int[]) null));
        assertThrows(IllegalArgumentException.class, () -> IntBits.Shuffle.ofBits(new int[Integer.SIZE - 1]));
        assertThrows(IllegalArgumentException.class, () -> IntBits.Shuffle.ofBits(new int[Integer.SIZE + 1]));
        assertThrows(IllegalArgumentException.class, () -> IntBits.Shuffle.ofBytes(new int[Integer.BYTES - 1]));
        assertThrows(IllegalArgumentException.class, () -> IntBits.Shuffle.ofBytes(new int[Integer.BYTES + 1]));
        int[] intBits = new int[Integer.SIZE];
        int[] intBytes = new int[Integer.BYTES];
        for (int source : new int[] {-3, Integer.SIZE, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            intBits[0] = source;
            intBytes[0] = source;
            assertThrows(IllegalArgumentException.class, () -> IntBits.Shuffle.ofBits(intBits));
            assertThrows(IllegalArgumentException.class, () -> IntBits.Shuffle.ofBytes(intBytes));
        }
        intBytes[0] = Integer.BYTES;
        assertThrows(IllegalArgumentException.class, () -> IntBits.Shuffle.ofBytes(intBytes));

        assertThrows(NullPointerException.class, () -> LongBits.Shuffle.ofBits((int[]) null));
        assertThrows(NullPointerException.class, () -> LongBits.Shuffle.ofBytes((int[]) null));
        assertThrows(IllegalArgumentException.class, () -> LongBits.Shuffle.ofBits(new int[Long.SIZE - 1]));
        assertThrows(IllegalArgumentException.class, () -> LongBits.Shuffle.ofBits(new int[Long.SIZE + 1]));
        assertThrows(IllegalArgumentException.class, () -> LongBits.Shuffle.ofBytes(new int[Long.BYTES - 1]));
        assertThrows(IllegalArgumentException.class, () -> LongBits.Shuffle.ofBytes(new int[Long.BYTES + 1]));
        int[] longBits = new int[Long.SIZE];
        int[] longBytes = new int[Long.BYTES];
        for (int source : new int[] {-3, Long.SIZE, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            longBits[0] = source;
            longBytes[0] = source;
            assertThrows(IllegalArgumentException.class, () -> LongBits.Shuffle.ofBits(longBits));
            assertThrows(IllegalArgumentException.class, () -> LongBits.Shuffle.ofBytes(longBytes));
        }
        longBytes[0] = Long.BYTES;
        assertThrows(IllegalArgumentException.class, () -> LongBits.Shuffle.ofBytes(longBytes));
    }

    private static void checkIntSelection(int mask, int word, int value) {
        checkIntSelection(IntBits.Selection.ofMask(mask), mask, word, value);
    }

    private static void checkIntSelection(IntBits.Selection selection, int mask, int word, int value) {
        assertEquals(Integer.compress(word, mask), selection.compress(word));
        assertEquals(Integer.expand(value, mask), selection.expand(value));
        assertEquals((word & ~mask) | Integer.expand(value, mask), selection.replace(word, value));
    }

    private static void checkLongSelection(long mask, long word, long value) {
        checkLongSelection(LongBits.Selection.ofMask(mask), mask, word, value);
    }

    private static void checkLongSelection(LongBits.Selection selection, long mask, long word, long value) {
        assertEquals(Long.compress(word, mask), selection.compress(word));
        assertEquals(Long.expand(value, mask), selection.expand(value));
        assertEquals((word & ~mask) | Long.expand(value, mask), selection.replace(word, value));
    }

    private static List<IndexLayout> indexedLayouts(int size) {
        var layouts = new ArrayList<>(List.of(
                new IndexLayout(18, 6, -6, 4),
                new IndexLayout(2, 5, 3, 4),  // Overlapping fields
                new IndexLayout(0, 1, 1, size),
                new IndexLayout(size - 1, 1, -1, size),
                new IndexLayout(size, 0, -1, size + 1),
                new IndexLayout(3, 5, 0, 3),
                new IndexLayout(0, size, Integer.MIN_VALUE, 1)));
        var random = new SplittableRandom(0xF1E1D);
        for (int i = 0; i < 100; i++) {
            int width = random.nextInt(size + 1);
            int first = random.nextInt(size - width + 1);
            int stride = random.nextInt(-8, 9);
            int distance = stride < 0 ? first : size - width - first;
            int maxCount = stride == 0 ? 4 : distance / Math.abs(stride) + 1;
            layouts.add(new IndexLayout(first, width, stride, random.nextInt(1, maxCount + 1)));
        }
        return layouts;
    }

    private static IndexLayout[] invalidIndexedLayouts(int size) {
        return new IndexLayout[] {
                new IndexLayout(-1, 0, 1, 1),
                new IndexLayout(0, -1, 1, 1),
                new IndexLayout(0, size + 1, 1, 1),
                new IndexLayout(size + 1, 0, 1, 1),
                new IndexLayout(0, 1, 1, 0),
                new IndexLayout(0, 1, 1, -1),
                new IndexLayout(size - 1, 1, 1, 2),
                new IndexLayout(0, 1, -1, 2),
                new IndexLayout(2, 1, Integer.MAX_VALUE, 3),
                new IndexLayout(0, 1, Integer.MIN_VALUE, 3)
        };
    }

    private static long fieldMask(int offset, int width) {
        long mask = 0;
        for (int bit = offset; bit < offset + width; bit++) {
            mask |= 1L << bit;
        }
        return mask;
    }

    private static long signExtend(long extracted, int width) {
        if (width == 0 || width == Long.SIZE || (extracted & (1L << (width - 1))) == 0) {
            return extracted;
        }
        return extracted - (1L << width);
    }

    private record IndexLayout(int first, int width, int stride, int count) {
        int offset(int index) {
            return Math.toIntExact(first + (long) index * stride);
        }
    }
}
