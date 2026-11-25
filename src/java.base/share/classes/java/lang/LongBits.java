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

package java.lang;

import jdk.internal.lang.LongBitsImpl;

/**
 * Descriptors for extracting, moving, and replacing bits in {@code long} values.
 * Bit positions are numbered from zero, starting with the least significant bit.
 * Descriptors are immutable and can be reused across values.
 *
 * @since 28
 */
public sealed interface LongBits
        permits LongBits.Field, LongBits.IndexedField, LongBits.Selection, LongBits.Shuffle {

    /**
     * A contiguous range of bits in a {@code long}.
     *
     * @since 28
     */
    sealed interface Field extends LongBits, Shuffle.Part permits LongBitsImpl.Field {
        /**
         * Creates a field with the given offset and width.
         *
         * @param offset the position of the least significant bit of the field
         * @param width the number of bits in the field
         * @return the field
         * @throws IllegalArgumentException if the offset or width is negative,
         *         or their sum exceeds {@link Long#SIZE}
         */
        static Field of(int offset, int width) {
            return LongBitsImpl.ofField(offset, width);
        }

        /**
         * Creates a field with the given exclusive end and width. The offset
         * is {@code endExclusive - width}. A width of zero yields an empty
         * field at the given end.
         *
         * @param endExclusive the exclusive end of the field
         * @param width the number of bits in the field
         * @return the field
         * @throws IllegalArgumentException if {@code width < 0},
         *         {@code width > endExclusive}, or
         *         {@code endExclusive > Long.SIZE}
         */
        static Field endingAt(int endExclusive, int width) {
            if (width < 0 || width > endExclusive || endExclusive > Long.SIZE) {
                throw new IllegalArgumentException("Invalid bit field end " + endExclusive + " and width " + width);
            }
            return of(endExclusive - width, width);
        }

        /**
         * Creates a field containing the bits from {@code fromInclusive},
         * inclusive, to {@code toExclusive}, exclusive. Equal endpoints yield
         * an empty field at that position.
         *
         * @param fromInclusive the inclusive start of the range
         * @param toExclusive the exclusive end of the range
         * @return the field
         * @throws IllegalArgumentException if {@code fromInclusive < 0},
         *         {@code fromInclusive > toExclusive}, or {@code toExclusive > Long.SIZE}
         */
        static Field ofRange(int fromInclusive, int toExclusive) {
            return LongBitsImpl.fieldOfRange(fromInclusive, toExclusive);
        }

        /**
         * Creates a field containing all bits from {@code firstBit} through
         * {@code lastBit}, inclusive.
         *
         * @param firstBit the position of the least significant bit
         * @param lastBit the position of the most significant bit
         * @return the field
         * @throws IllegalArgumentException if {@code firstBit < 0},
         *         {@code firstBit > lastBit}, or {@code lastBit >= Long.SIZE}
         */
        static Field ofClosedRange(int firstBit, int lastBit) {
            return LongBitsImpl.fieldOfClosedRange(firstBit, lastBit);
        }

        /**
         * Creates a field from a mask with a contiguous run of set bits.
         * A zero mask yields a field of width zero at offset zero.
         *
         * @param mask the bits included in the field
         * @return the field
         * @throws IllegalArgumentException if the set bits are not contiguous
         */
        static Field fromMask(long mask) {
            return LongBitsImpl.fieldFromMask(mask);
        }

        /** {@return the offset of this field} */
        int offset();

        /** {@return the number of bits in this field} */
        @Override
        int width();

        /**
         * {@return the exclusive end of this field}
         * This is the sum of its {@linkplain #offset() offset} and
         * {@linkplain #width() width}. An empty field ends at its offset.
         * The exclusive end can be as large as {@link Long#SIZE}.
         */
        default int endExclusive() {
            return offset() + width();
        }

        /** {@return the mask of this field in its original position} */
        long mask();

        /**
         * Extracts this field into the low bits of the result, with other bits
         * set to zero.
         *
         * @param word the word containing the field
         * @return the extracted bits, or zero for an empty field
         */
        long extract(long word);

        /**
         * Extracts this field and sign-extends its most significant bit.
         *
         * @param word the word containing the field
         * @return the sign-extended field, or zero for an empty field
         */
        long extractSigned(long word);

        /**
         * Replaces this field with the low {@linkplain #width() width} bits of
         * {@code value}, preserving the other bits of {@code word}.
         *
         * @param word the word containing the field
         * @param value the replacement value
         * @return the updated word, or the original word for an empty field
         */
        long replace(long word, long value);
    }

    /**
     * Equally spaced fields of the same width in a {@code long}.
     * The field at index {@code i} has offset {@code firstBit + i * strideBits}.
     *
     * @since 28
     */
    sealed interface IndexedField extends LongBits permits LongBitsImpl.IndexedField {
        /**
         * Creates an indexed field descriptor. The stride can be negative or
         * zero, and consecutive fields may overlap.
         *
         * @param firstBit the offset of the field at index zero
         * @param width the number of bits in each field
         * @param strideBits the difference in offset between successive fields
         * @param count the number of fields
         * @return the indexed field descriptor
         * @throws IllegalArgumentException if the count is not positive, the
         *         width is negative, or any field falls outside the word
         */
        static IndexedField of(int firstBit, int width, int strideBits, int count) {
            return LongBitsImpl.ofIndexedField(firstBit, width, strideBits, count);
        }

        /**
         * Creates a selection of all bits in the indexed fields. A bit
         * belonging to more than one field is selected once.
         *
         * @return the selection, which is empty if the field width is zero
         */
        Selection asSelection();

        /**
         * Extracts the indexed field into the low bits of the result, with
         * other bits set to zero.
         *
         * @param word the word containing the fields
         * @param index the index of the field
         * @return the extracted bits, or zero for a field of width zero
         * @throws IndexOutOfBoundsException if the index is out of range
         */
        long extract(long word, int index);

        /**
         * Extracts the indexed field and sign-extends its most significant bit.
         *
         * @param word the word containing the fields
         * @param index the index of the field
         * @return the sign-extended field, or zero for a field of width zero
         * @throws IndexOutOfBoundsException if the index is out of range
         */
        long extractSigned(long word, int index);

        /**
         * Replaces the indexed field with the low bits of {@code value},
         * preserving the other bits of {@code word}.
         *
         * @param word the word containing the fields
         * @param index the index of the field
         * @param value the replacement value
         * @return the updated word, or the original word for a field of width zero
         * @throws IndexOutOfBoundsException if the index is out of range
         */
        long replace(long word, int index, long value);
    }

    /**
     * A mask selecting any subset of the bits in a {@code long}.
     *
     * @since 28
     */
    sealed interface Selection extends LongBits permits LongBitsImpl.Selection {
        /**
         * Creates a selection of the set bits in the mask.
         *
         * @param mask the bits to select
         * @return the selection
         */
        static Selection ofMask(long mask) {
            return LongBitsImpl.selectionOfMask(mask);
        }

        /**
         * Creates a selection of all bits in the given fields. Field order
         * does not affect the selection, and a bit belonging to more than one
         * field is selected once.
         *
         * @param fields the fields whose bits are selected
         * @return the selection, which is empty if no bits are selected
         * @throws NullPointerException if the array or any field is null
         */
        static Selection ofFields(Field... fields) {
            return LongBitsImpl.selectionOfFields(fields);
        }

        /**
         * Gathers the selected bits into the low bits of the result in order
         * from least to most significant, as in {@link Long#compress(long, long)}.
         *
         * @param word the source word
         * @return the compressed bits
         */
        long compress(long word);

        /**
         * Deposits the low bits of {@code value} into the selected positions,
         * setting other bits to zero, as in {@link Long#expand(long, long)}.
         *
         * @param value the packed bits to deposit
         * @return the expanded bits
         */
        long expand(long value);

        /**
         * Replaces the selected bits with the expanded bits of {@code value},
         * preserving the other bits of {@code word}.
         *
         * @param word the word containing the bits to replace
         * @param value the packed bits to deposit
         * @return the updated word
         */
        long replace(long word, long value);
    }

    /**
     * A mapping from the result bits of an {@code long} to source bits
     * or constant bits. More than one result bit may read the same source bit.
     *
     * @since 28
     */
    sealed interface Shuffle extends LongBits permits LongBitsImpl.Shuffle {
        /** Selects a constant zero bit or a byte filled with zero bits. */
        int ZERO = -1;

        /** Selects a constant one bit or a byte filled with one bits. */
        int ONE = -2;

        /**
         * An immutable part of a concatenation, either a source field or a
         * literal bit pattern.
         *
         * @since 28
         */
        sealed interface Part permits Field, LongBitsImpl.Literal {
            /** {@return the number of bits in this part} */
            int width();
        }

        /**
         * Creates a literal part containing the low {@code width} bits of
         * {@code value}. A width of zero yields an empty part.
         *
         * @param value the literal bit pattern
         * @param width the number of bits in the literal
         * @return the literal part
         * @throws IllegalArgumentException if the width is outside
         *         the range from zero through {@link Long#SIZE}
         */
        static Part literal(long value, int width) {
            return LongBitsImpl.shuffleLiteral(value, width);
        }

        /**
         * Creates a literal part containing {@code width} zero bits.
         *
         * @param width the number of zero bits
         * @return the literal part
         * @throws IllegalArgumentException if the width is outside
         *         the range from zero through {@link Long#SIZE}
         */
        static Part zeros(int width) {
            return literal(0, width);
        }

        /**
         * Creates a literal part containing {@code width} one bits.
         *
         * @param width the number of one bits
         * @return the literal part
         * @throws IllegalArgumentException if the width is outside
         *         the range from zero through {@link Long#SIZE}
         */
        static Part ones(int width) {
            return literal(-1, width);
        }

        /**
         * Creates a shuffle with one entry per result bit, ordered from least
         * to most significant. Each entry is the source bit index,
         * {@link #ZERO}, or {@link #ONE}.
         *
         * @param sourceBits the source for each result bit
         * @return the shuffle
         * @throws IllegalArgumentException if there are not exactly
         *         {@link Long#SIZE} entries, or an entry is neither a
         *         valid source index nor a constant
         * @throws NullPointerException if the array is null
         */
        static Shuffle ofBits(int... sourceBits) {
            return LongBitsImpl.shuffleOfBits(sourceBits);
        }

        /**
         * Creates a shuffle with one entry per result byte, ordered from
         * least to most significant. Each entry is the source byte index,
         * {@link #ZERO}, or {@link #ONE}. Bits within a byte retain their
         * order.
         *
         * @param sourceBytes the source for each result byte
         * @return the shuffle
         * @throws IllegalArgumentException if there are not exactly
         *         {@link Long#BYTES} entries, or an entry is neither a
         *         valid source index nor a constant
         * @throws NullPointerException if the array is null
         */
        static Shuffle ofBytes(int... sourceBytes) {
            return LongBitsImpl.shuffleOfBytes(sourceBytes);
        }

        /**
         * Creates a shuffle that concatenates the source fields in the order
         * given, starting at the least significant bit of the result. Bits
         * within each field retain their order. The fields may overlap or
         * repeat, and unused high bits of the result are filled with zeros.
         *
         * @param fields the fields to copy into the result
         * @return the shuffle
         * @throws IllegalArgumentException if the total width exceeds
         *         {@link Long#SIZE}
         * @throws NullPointerException if the array or any field is null
         */
        static Shuffle concatFields(Field... fields) {
            return LongBitsImpl.shuffleConcatFields(fields);
        }

        /**
         * Creates a shuffle that concatenates source fields and literals from
         * most to least significant within the result. The last part supplies
         * the lowest bits. Bits within each part retain their order, and any
         * unused high bits are filled with zeros. Parts of width zero
         * contribute no bits. Source fields may overlap or repeat.
         *
         * @param parts the source fields and literals to concatenate
         * @return the shuffle
         * @throws IllegalArgumentException if the total width exceeds
         *         {@link Long#SIZE}
         * @throws NullPointerException if the array or any part is null
         */
        static Shuffle concatHighToLow(Part... parts) {
            return LongBitsImpl.shuffleConcatHighToLow(parts);
        }

        /**
         * Rearranges the bits of the given word according to this shuffle.
         *
         * @param word the source word
         * @return the rearranged bits
         */
        long rearrange(long word);

        /**
         * Returns the shuffle that reverses this mapping.
         *
         * @return the inverse shuffle
         * @throws IllegalStateException if any source bit is omitted or
         *         selected more than once, or the mapping contains a constant
         */
        Shuffle inverse();
    }
}
