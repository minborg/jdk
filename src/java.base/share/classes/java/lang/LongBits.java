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
     * Returns a Verilog-2005 expression describing the result bits of an
     * instance operation on this descriptor. Operands are packed vectors
     * numbered down to bit zero, with the width of the declared Java
     * parameter. The result has the width of the Java return type.
     *
     * <p>A field supports {@code extract}, {@code extractSigned}, {@code replace},
     * {@code offset}, {@code width}, {@code endExclusive}, and {@code mask}.
     * An indexed field supports {@code extract}, {@code extractSigned}, and
     * {@code replace}. For an out-of-range index the expression yields unknown
     * bits ({@code x}). A selection supports {@code compress}, {@code expand},
     * {@code replace}, and {@code blend}. A shuffle supports {@code rearrange}.
     *
     * <p>Names are substituted in declaration order. Omitted trailing names
     * retain their declared Java spelling. Each supplied name denotes one
     * signal and is escaped if required by Verilog.
     *
     * @apiNote For example, {@code Field.of(11, 5).toVerilogExpression("extract", "pixel")}
     * describes the zero-extended slice {@code {59'b0, pixel[15:11]}}.
     *
     * @param methodName the Java name of the operation
     * @param parameterNames zero or more signal names, each consisting of
     *        one or more printable, non-whitespace ASCII characters
     * @return the expression
     * @throws IllegalArgumentException if the operation is unsupported by this
     *         descriptor, too many names are supplied, or a name is invalid
     * @throws NullPointerException if the method name, array, or any name is null
     */
    String toVerilogExpression(String methodName, String... parameterNames);

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
         * Creates a builder that selects bits starting at the most significant
         * bit of a {@code long} and proceeds toward the least significant bit.
         *
         * @return a new builder with all {@link Long#SIZE} bits remaining
         */
        static Builder highToLow() {
            return new LongBitsImpl.SelectionBuilder();
        }

        /**
         * A mutable builder that consumes consecutive groups of bits from
         * most to least significant. Each step advances past the whole group.
         * A step with invalid widths throws before changing the builder.
         *
         * @since 28
         */
        sealed interface Builder permits LongBitsImpl.SelectionBuilder {
            /**
             * Selects the next {@code width} bits and advances past them.
             *
             * @param width the number of bits to select
             * @return this builder
             * @throws IllegalArgumentException if the width is negative or
             *         exceeds the number of remaining bits
             */
            default Builder take(int width) {
                return takeHigh(width, width);
            }

            /**
             * Advances past the next {@code width} bits, leaving them unselected.
             *
             * @param width the number of bits to skip
             * @return this builder
             * @throws IllegalArgumentException if the width is negative or
             *         exceeds the number of remaining bits
             */
            default Builder skip(int width) {
                return takeHigh(width, 0);
            }

            /**
             * Selects the highest {@code width} bits of the next group and
             * advances past all {@code groupWidth} bits. For example,
             * {@code takeHigh(8, 5)} takes the next five bits and skips three.
             *
             * @param groupWidth the number of bits to consume
             * @param width the number of high bits to select within the group
             * @return this builder
             * @throws IllegalArgumentException if the group width is negative or
             *         exceeds the number of remaining bits, or if {@code width}
             *         is outside the range from zero through {@code groupWidth}
             */
            Builder takeHigh(int groupWidth, int width);

            /**
             * Builds a selection from the bits taken so far. Any remaining
             * low bits are unselected. The builder may continue from its
             * current position independently of the returned selection.
             *
             * @return the selection
             */
            Selection build();
        }

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

        /**
         * Copies the selected bits from {@code donor} into the same positions
         * in {@code base}, preserving all unselected bits of {@code base}.
         * Equivalent to {@code replace(base, compress(donor))}.
         *
         * @param base the source of the unselected bits
         * @param donor the source of the selected bits
         * @return the blended word
         */
        long blend(long base, long donor);
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
         * An immutable part of a concatenation representing a source field,
         * a repeated source field, or a literal bit pattern.
         *
         * @since 28
         */
        sealed interface Part permits Field, LongBitsImpl.Literal, LongBitsImpl.RepeatedField {
            /** {@return the number of bits in this part} */
            int width();
        }

        /**
         * Creates a part by reading {@code field} from most to least significant
         * bit, starting over as needed until {@code width} bits have been read.
         * For example, the five bits {@code abcde} give {@code abcdeabc} at
         * width eight and {@code abc} at width three. A width of zero yields
         * an empty part.
         *
         * @param field the source field to repeat
         * @param width the number of bits in the resulting part
         * @return the shuffle part
         * @throws IllegalArgumentException if the width is outside the range
         *         from zero through {@link Long#SIZE}, or the field is empty
         *         and the width is positive
         * @throws NullPointerException if the field is null
         */
        static Part repeatToWidth(Field field, int width) {
            return LongBitsImpl.shuffleRepeatToWidth(field, width);
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
         * Creates a shuffle from a Verilog wiring expression whose result is exactly
         * 64 bits wide. The expression may name a single 64-bit input signal,
         * or consist entirely of literals. A signal name may be a simple or escaped
         * Verilog identifier.
         *
         * <p>Supported forms are a whole signal, a bit or descending range selected
         * with decimal indices, unsigned sized binary, octal, decimal, or hexadecimal
         * literals with known bits, concatenation, and replication by a positive
         * decimal count. Concatenations are written most significant part first.
         * Parentheses may group expressions. For example, {@code {2{word[31:0]}}}
         * copies the low half of the input into both halves of the result.
         *
         * <p>Parsing the result of {@code shuffle.toVerilogExpression("rearrange")}
         * produces a shuffle with the same bit mapping.
         *
         * @param expression the Verilog expression
         * @return the shuffle described by the expression
         * @throws IllegalArgumentException if the expression is outside the supported
         *         syntax, names multiple signals, selects a bit outside the input,
         *         or has a result width other than 64
         * @throws NullPointerException if the expression is null
         */
        static Shuffle fromVerilogExpression(String expression) {
            return LongBitsImpl.shuffleFromVerilogExpression(expression);
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
         * Creates a shuffle that concatenates the given parts from
         * most to least significant within the result. The last part supplies
         * the lowest bits. Bits within each part retain their order, and any
         * unused high bits are filled with zeros. Parts of width zero
         * contribute no bits. Source fields may overlap or repeat.
         *
         * @param parts the parts to concatenate
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
