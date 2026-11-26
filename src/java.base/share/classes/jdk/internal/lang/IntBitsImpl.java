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

package jdk.internal.lang;

import java.util.Arrays;
import java.util.Objects;

import jdk.internal.vm.annotation.TrustFinalFields;

public final class IntBitsImpl {
    private IntBitsImpl() {}

    public static IntBits.Field ofField(int offset, int width) {
        checkRange(offset, width, Integer.SIZE);
        return new Field(offset, width, lowMask(width));
    }

    public static IntBits.Field fieldOfRange(int fromInclusive, int toExclusive) {
        int width = rangeWidth(fromInclusive, toExclusive, Integer.SIZE);
        return new Field(fromInclusive, width, lowMask(width));
    }

    public static IntBits.Field fieldOfClosedRange(int firstBit, int lastBit) {
        int width = closedRangeWidth(firstBit, lastBit, Integer.SIZE);
        return new Field(firstBit, width, lowMask(width));
    }

    public static IntBits.Field fieldFromMask(int mask) {
        if (mask == 0) {
            return ofField(0, 0);
        }
        int offset = Integer.numberOfTrailingZeros(mask);
        int valueMask = mask >>> offset;
        if ((valueMask & (valueMask + 1)) != 0) {
            throw new IllegalArgumentException("Mask " + mask + " must be contiguous");
        }
        return new Field(offset, Integer.bitCount(mask), valueMask);
    }

    public static IntBits.IndexedField ofIndexedField(int firstBit, int width, int strideBits, int count) {
        checkIndexedRange(firstBit, width, strideBits, count, Integer.SIZE);
        return new IndexedField(firstBit, strideBits, count, lowMask(width));
    }

    public static IntBits.Selection selectionOfMask(int mask) {
        return new Selection(mask);
    }

    public static IntBits.Selection selectionOfFields(IntBits.Field[] fields) {
        int mask = 0;
        for (IntBits.Field field : fields) {
            mask |= field.mask();
        }
        return selectionOfMask(mask);
    }

    public static IntBits.Shuffle shuffleFromVerilogExpression(String expression) {
        return new Shuffle(VerilogShuffleParser.parse(Integer.SIZE, expression));
    }

    public static IntBits.Shuffle shuffleOfBits(int[] sourceBits) {
        return new Shuffle(BitShuffle.ofBits(Integer.SIZE, sourceBits));
    }

    public static IntBits.Shuffle shuffleOfBytes(int[] sourceBytes) {
        return new Shuffle(BitShuffle.ofBytes(Integer.SIZE, sourceBytes));
    }

    public static IntBits.Shuffle shuffleConcatFields(IntBits.Field[] fields) {
        return concat(fields, false);
    }

    public static IntBits.Shuffle shuffleConcatHighToLow(IntBits.Shuffle.Part[] parts) {
        return concat(parts, true);
    }

    public static IntBits.Shuffle.Part shuffleLiteral(int value, int width) {
        checkRange(0, width, Integer.SIZE);
        return new Literal(value, width);
    }

    public static IntBits.Shuffle.Part shuffleRepeatToWidth(IntBits.Field field, int width) {
        Objects.requireNonNull(field);
        checkRange(0, width, Integer.SIZE);
        if (field.width() == 0 && width > 0) {
            throw new IllegalArgumentException("Cannot repeat an empty field to a positive width");
        }
        return new RepeatedField(field, width);
    }

    private static Shuffle concat(IntBits.Shuffle.Part[] parts, boolean highToLow) {
        int[] sourceBits = new int[Integer.SIZE];
        int destination = 0;
        for (int i = 0; i < parts.length; i++) {
            var part = parts[highToLow ? parts.length - 1 - i : i];
            destination = switch (part) {
                case IntBits.Field field -> BitShuffle.appendField(sourceBits, destination, field.offset(), field.width());
                case Literal literal -> BitShuffle.appendLiteral(sourceBits, destination, literal.value, literal.width);
                case RepeatedField repeat -> BitShuffle.appendRepeatedField(sourceBits, destination,
                        repeat.field.endExclusive(), repeat.field.width(), repeat.width);
            };
        }
        Arrays.fill(sourceBits, destination, sourceBits.length, IntBits.Shuffle.ZERO);
        return new Shuffle(new BitShuffle(sourceBits));
    }

    @TrustFinalFields
    public static final class RepeatedField implements IntBits.Shuffle.Part {
        private final IntBits.Field field;
        private final int width;

        private RepeatedField(IntBits.Field field, int width) {
            this.field = field;
            this.width = width;
        }

        @Override
        public int width() {
            return width;
        }
    }

    @TrustFinalFields
    public static final class Literal implements IntBits.Shuffle.Part {
        private final int value;
        private final int width;

        private Literal(int value, int width) {
            this.value = value;
            this.width = width;
        }

        @Override
        public int width() {
            return width;
        }
    }

    @TrustFinalFields
    public static final class Field implements IntBits.Field {
        private final int offset;
        private final int width;
        private final int valueMask;
        private final int signBit;

        private Field(int offset, int width, int valueMask) {
            this.offset = offset;
            this.width = width;
            this.valueMask = valueMask;
            this.signBit = valueMask ^ (valueMask >>> 1);
        }

        @Override
        public String toVerilogExpression(String methodName, String... parameterNames) {
            return VerilogExpression.field(Integer.SIZE, offset, width, methodName, parameterNames);
        }

        @Override
        public int offset() {
            return offset;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int mask() {
            return valueMask << offset;
        }

        @Override
        public int extract(int word) {
            return (word >>> offset) & valueMask;
        }

        @Override
        public int extractSigned(int word) {
            return (extract(word) ^ signBit) - signBit;
        }

        @Override
        public int replace(int word, int value) {
            return replaceRange(word, value, offset, valueMask);
        }
    }

    @TrustFinalFields
    public static final class IndexedField implements IntBits.IndexedField {
        private final int firstBit;
        private final int strideBits;
        private final int count;
        private final int valueMask;
        private final int signBit;

        private IndexedField(int firstBit, int strideBits, int count, int valueMask) {
            this.firstBit = firstBit;
            this.strideBits = strideBits;
            this.count = count;
            this.valueMask = valueMask;
            this.signBit = valueMask ^ (valueMask >>> 1);
        }

        private int offset(int index) {
            return firstBit + Objects.checkIndex(index, count) * strideBits;
        }

        @Override
        public String toVerilogExpression(String methodName, String... parameterNames) {
            return VerilogExpression.indexedField(Integer.SIZE, firstBit, Integer.bitCount(valueMask),
                    strideBits, count, methodName, parameterNames);
        }

        @Override
        public IntBits.Selection asSelection() {
            if (valueMask == 0 || strideBits == 0) {
                return selectionOfMask(valueMask << firstBit);
            }
            int mask = 0;
            for (int i = 0; i < count; i++) {
                mask |= valueMask << (firstBit + i * strideBits);
            }
            return selectionOfMask(mask);
        }

        @Override
        public int extract(int word, int index) {
            return (word >>> offset(index)) & valueMask;
        }

        @Override
        public int extractSigned(int word, int index) {
            return (extract(word, index) ^ signBit) - signBit;
        }

        @Override
        public int replace(int word, int index, int value) {
            return replaceRange(word, value, offset(index), valueMask);
        }
    }

    @TrustFinalFields
    public static final class SelectionBuilder implements IntBits.Selection.Builder {
        private int remaining = Integer.SIZE;
        private int mask;

        @Override
        public IntBits.Selection.Builder takeHigh(int groupWidth, int width) {
            checkSelectionWidths(groupWidth, width, remaining);
            mask |= lowMask(width) << (remaining - width);
            remaining -= groupWidth;
            return this;
        }

        @Override
        public IntBits.Selection build() {
            return selectionOfMask(mask);
        }
    }

    @TrustFinalFields
    public static final class Selection implements IntBits.Selection {
        private final int mask;

        private Selection(int mask) {
            this.mask = mask;
        }

        @Override
        public String toVerilogExpression(String methodName, String... parameterNames) {
            return VerilogExpression.selection(Integer.SIZE, mask, methodName, parameterNames);
        }

        @Override
        public int compress(int word) {
            return Integer.compress(word, mask);
        }

        @Override
        public int expand(int value) {
            return Integer.expand(value, mask);
        }

        @Override
        public int replace(int word, int value) {
            return (word & ~mask) | expand(value);
        }

        @Override
        public int blend(int base, int donor) {
            return (base & ~mask) | (donor & mask);
        }
    }

    @TrustFinalFields
    public static final class Shuffle implements IntBits.Shuffle {
        private final BitShuffle mapping;

        private Shuffle(BitShuffle mapping) {
            this.mapping = mapping;
        }

        @Override
        public String toVerilogExpression(String methodName, String... parameterNames) {
            return mapping.toVerilogExpression(methodName, parameterNames);
        }

        @Override
        public int rearrange(int word) {
            return mapping.rearrange(word);
        }

        @Override
        public IntBits.Shuffle inverse() {
            return new IntBitsImpl.Shuffle(mapping.inverse());
        }
    }

    private static int lowMask(int width) {
        return width == 0 ? 0 : -1 >>> (Integer.SIZE - width);
    }

    private static int replaceRange(int word, int value, int offset, int valueMask) {
        return (word & ~(valueMask << offset)) | ((value & valueMask) << offset);
    }

    static void checkSelectionWidths(int groupWidth, int width, int remaining) {
        if (groupWidth < 0 || groupWidth > remaining || width < 0 || width > groupWidth) {
            throw new IllegalArgumentException("Cannot select " + width + " high bits from a group of "
                    + groupWidth + " with " + remaining + " bits remaining");
        }
    }

    static void checkRange(int offset, int width, int wordSize) {
        if (width < 0 || width > wordSize || offset < 0 || offset > wordSize - width) {
            throw new IllegalArgumentException("Invalid bit field offset " + offset + " and width " + width);
        }
    }

    static int rangeWidth(int fromInclusive, int toExclusive, int wordSize) {
        if (fromInclusive < 0 || fromInclusive > toExclusive || toExclusive > wordSize) {
            throw new IllegalArgumentException("Invalid bit range: [" + fromInclusive + ", " + toExclusive + ")");
        }
        return toExclusive - fromInclusive;
    }

    static int closedRangeWidth(int firstBit, int lastBit, int wordSize) {
        if (firstBit < 0 || firstBit > lastBit || lastBit >= wordSize) {
            throw new IllegalArgumentException("Invalid closed bit range: " + firstBit + ".." + lastBit);
        }
        return lastBit - firstBit + 1;
    }

    static void checkIndexedRange(int firstBit, int width, int strideBits, int count, int wordSize) {
        checkRange(firstBit, width, wordSize);
        if (count <= 0) {
            throw new IllegalArgumentException("Non-positive field count: " + count);
        }
        // The offsets are monotonic, so checking the endpoints bounds every offset.
        long lastBit = firstBit + (count - 1L) * strideBits;
        if (lastBit < 0 || lastBit > wordSize - width) {
            throw new IllegalArgumentException("Last bit field is outside the word: " + lastBit);
        }
    }
}
