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

public final class LongBitsImpl {
    private LongBitsImpl() {}

    public static LongBits.Field ofField(int offset, int width) {
        IntBitsImpl.checkRange(offset, width, Long.SIZE);
        return new Field(offset, width, lowMask(width));
    }

    public static LongBits.Field fieldOfRange(int fromInclusive, int toExclusive) {
        int width = IntBitsImpl.rangeWidth(fromInclusive, toExclusive, Long.SIZE);
        return new Field(fromInclusive, width, lowMask(width));
    }

    public static LongBits.Field fieldOfClosedRange(int firstBit, int lastBit) {
        int width = IntBitsImpl.closedRangeWidth(firstBit, lastBit, Long.SIZE);
        return new Field(firstBit, width, lowMask(width));
    }

    public static LongBits.Field fieldFromMask(long mask) {
        if (mask == 0) {
            return ofField(0, 0);
        }
        int offset = Long.numberOfTrailingZeros(mask);
        long valueMask = mask >>> offset;
        if ((valueMask & (valueMask + 1)) != 0) {
            throw new IllegalArgumentException("Mask " + mask + " must be contiguous");
        }
        return new Field(offset, Long.bitCount(mask), valueMask);
    }

    public static LongBits.IndexedField ofIndexedField(int firstBit, int width, int strideBits, int count) {
        IntBitsImpl.checkIndexedRange(firstBit, width, strideBits, count, Long.SIZE);
        return new IndexedField(firstBit, strideBits, count, lowMask(width));
    }

    public static LongBits.Selection selectionOfMask(long mask) {
        return new Selection(mask);
    }

    public static LongBits.Selection selectionOfFields(LongBits.Field[] fields) {
        long mask = 0;
        for (LongBits.Field field : fields) {
            mask |= field.mask();
        }
        return selectionOfMask(mask);
    }

    public static LongBits.Shuffle shuffleFromVerilogExpression(String expression) {
        return new Shuffle(VerilogShuffleParser.parse(Long.SIZE, expression));
    }

    public static LongBits.Shuffle shuffleOfBits(int[] sourceBits) {
        return new Shuffle(BitShuffle.ofBits(Long.SIZE, sourceBits));
    }

    public static LongBits.Shuffle shuffleOfBytes(int[] sourceBytes) {
        return new Shuffle(BitShuffle.ofBytes(Long.SIZE, sourceBytes));
    }

    public static LongBits.Shuffle shuffleConcatFields(LongBits.Field[] fields) {
        return concat(fields, false);
    }

    public static LongBits.Shuffle shuffleConcatHighToLow(LongBits.Shuffle.Part[] parts) {
        return concat(parts, true);
    }

    public static LongBits.Shuffle.Part shuffleLiteral(long value, int width) {
        IntBitsImpl.checkRange(0, width, Long.SIZE);
        return new Literal(value, width);
    }

    public static LongBits.Shuffle.Part shuffleRepeatToWidth(LongBits.Field field, int width) {
        Objects.requireNonNull(field);
        IntBitsImpl.checkRange(0, width, Long.SIZE);
        if (field.width() == 0 && width > 0) {
            throw new IllegalArgumentException("Cannot repeat an empty field to a positive width");
        }
        return new RepeatedField(field, width);
    }

    private static Shuffle concat(LongBits.Shuffle.Part[] parts, boolean highToLow) {
        int[] sourceBits = new int[Long.SIZE];
        int destination = 0;
        for (int i = 0; i < parts.length; i++) {
            var part = parts[highToLow ? parts.length - 1 - i : i];
            destination = switch (part) {
                case LongBits.Field field -> BitShuffle.appendField(sourceBits, destination, field.offset(), field.width());
                case Literal literal -> BitShuffle.appendLiteral(sourceBits, destination, literal.value, literal.width);
                case RepeatedField repeat -> BitShuffle.appendRepeatedField(sourceBits, destination,
                        repeat.field.endExclusive(), repeat.field.width(), repeat.width);
            };
        }
        Arrays.fill(sourceBits, destination, sourceBits.length, LongBits.Shuffle.ZERO);
        return new Shuffle(new BitShuffle(sourceBits));
    }

    @TrustFinalFields
    public static final class RepeatedField implements LongBits.Shuffle.Part {
        private final LongBits.Field field;
        private final int width;

        private RepeatedField(LongBits.Field field, int width) {
            this.field = field;
            this.width = width;
        }

        @Override
        public int width() {
            return width;
        }
    }

    @TrustFinalFields
    public static final class Literal implements LongBits.Shuffle.Part {
        private final long value;
        private final int width;

        private Literal(long value, int width) {
            this.value = value;
            this.width = width;
        }

        @Override
        public int width() {
            return width;
        }
    }

    @TrustFinalFields
    public static final class Field implements LongBits.Field {
        private final int offset;
        private final int width;
        private final long valueMask;
        private final long signBit;

        private Field(int offset, int width, long valueMask) {
            this.offset = offset;
            this.width = width;
            this.valueMask = valueMask;
            this.signBit = valueMask ^ (valueMask >>> 1);
        }

        @Override
        public String toVerilogExpression(String methodName, String... parameterNames) {
            return VerilogExpression.field(Long.SIZE, offset, width, methodName, parameterNames);
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
        public long mask() {
            return valueMask << offset;
        }

        @Override
        public long extract(long word) {
            return (word >>> offset) & valueMask;
        }

        @Override
        public long extractSigned(long word) {
            return (extract(word) ^ signBit) - signBit;
        }

        @Override
        public long replace(long word, long value) {
            return replaceRange(word, value, offset, valueMask);
        }
    }

    @TrustFinalFields
    public static final class IndexedField implements LongBits.IndexedField {
        private final int firstBit;
        private final int strideBits;
        private final int count;
        private final long valueMask;
        private final long signBit;

        private IndexedField(int firstBit, int strideBits, int count, long valueMask) {
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
            return VerilogExpression.indexedField(Long.SIZE, firstBit, Long.bitCount(valueMask),
                    strideBits, count, methodName, parameterNames);
        }

        @Override
        public LongBits.Selection asSelection() {
            if (valueMask == 0 || strideBits == 0) {
                return selectionOfMask(valueMask << firstBit);
            }
            long mask = 0;
            for (int i = 0; i < count; i++) {
                mask |= valueMask << (firstBit + i * strideBits);
            }
            return selectionOfMask(mask);
        }

        @Override
        public long extract(long word, int index) {
            return (word >>> offset(index)) & valueMask;
        }

        @Override
        public long extractSigned(long word, int index) {
            return (extract(word, index) ^ signBit) - signBit;
        }

        @Override
        public long replace(long word, int index, long value) {
            return replaceRange(word, value, offset(index), valueMask);
        }
    }

    @TrustFinalFields
    public static final class SelectionBuilder implements LongBits.Selection.Builder {
        private int remaining = Long.SIZE;
        private long mask;

        @Override
        public LongBits.Selection.Builder takeHigh(int groupWidth, int width) {
            IntBitsImpl.checkSelectionWidths(groupWidth, width, remaining);
            mask |= lowMask(width) << (remaining - width);
            remaining -= groupWidth;
            return this;
        }

        @Override
        public LongBits.Selection build() {
            return selectionOfMask(mask);
        }
    }

    @TrustFinalFields
    public static final class Selection implements LongBits.Selection {
        private final long mask;

        private Selection(long mask) {
            this.mask = mask;
        }

        @Override
        public String toVerilogExpression(String methodName, String... parameterNames) {
            return VerilogExpression.selection(Long.SIZE, mask, methodName, parameterNames);
        }

        @Override
        public long compress(long word) {
            return Long.compress(word, mask);
        }

        @Override
        public long expand(long value) {
            return Long.expand(value, mask);
        }

        @Override
        public long replace(long word, long value) {
            return (word & ~mask) | expand(value);
        }

        @Override
        public long blend(long base, long donor) {
            return (base & ~mask) | (donor & mask);
        }
    }

    @TrustFinalFields
    public static final class Shuffle implements LongBits.Shuffle {
        private final BitShuffle mapping;

        private Shuffle(BitShuffle mapping) {
            this.mapping = mapping;
        }

        @Override
        public String toVerilogExpression(String methodName, String... parameterNames) {
            return mapping.toVerilogExpression(methodName, parameterNames);
        }

        @Override
        public long rearrange(long word) {
            return mapping.rearrange(word);
        }

        @Override
        public LongBits.Shuffle inverse() {
            return new LongBitsImpl.Shuffle(mapping.inverse());
        }
    }

    private static long lowMask(int width) {
        return width == 0 ? 0 : -1L >>> (Long.SIZE - width);
    }

    private static long replaceRange(long word, long value, int offset, long valueMask) {
        return (word & ~(valueMask << offset)) | ((value & valueMask) << offset);
    }
}
