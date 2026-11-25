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

import jdk.internal.vm.annotation.Stable;
import jdk.internal.vm.annotation.TrustFinalFields;

/**
 * The source mapping and the precomputed moves for an int or long shuffle.
 */
@TrustFinalFields
final class BitShuffle {
    private static final int ZERO = IntBits.Shuffle.ZERO;
    private static final int ONE = IntBits.Shuffle.ONE;

    private final int[] sourceBits;
    private final long ones;
    @Stable
    private final long[] moveMasks;
    @Stable
    private final int[] encodedShifts;

    static BitShuffle ofBits(int wordSize, int[] sourceBits) {
        if (sourceBits.length != wordSize) {
            throw new IllegalArgumentException("Expected " + wordSize + " source bits");
        }
        return new BitShuffle(sourceBits.clone());
    }

    static BitShuffle ofBytes(int wordSize, int[] sourceBytes) {
        int byteCount = wordSize / Byte.SIZE;
        if (sourceBytes.length != byteCount) {
            throw new IllegalArgumentException("Expected " + byteCount + " source bytes");
        }
        int[] bits = new int[wordSize];
        for (int i = 0; i < byteCount; i++) {
            int source = sourceBytes[i];
            int destinationBit = i * Byte.SIZE;
            if (source == ZERO || source == ONE) {
                Arrays.fill(bits, destinationBit, destinationBit + Byte.SIZE, source);
            } else {
                if (source < 0 || source >= byteCount) {
                    throw new IllegalArgumentException("Invalid source byte: " + source);
                }
                appendField(bits, destinationBit, source * Byte.SIZE, Byte.SIZE);
            }
        }
        return new BitShuffle(bits);
    }

    static int appendField(int[] sourceBits, int destination, int source, int width) {
        checkRemainingWidth(sourceBits, destination, width);
        for (int i = 0; i < width; i++) {
            sourceBits[destination + i] = source + i;
        }
        return destination + width;
    }

    static int appendLiteral(int[] sourceBits, int destination, long value, int width) {
        checkRemainingWidth(sourceBits, destination, width);
        for (int i = 0; i < width; i++) {
            sourceBits[destination + i] = ((value >>> i) & 1) == 0 ? ZERO : ONE;
        }
        return destination + width;
    }

    private static void checkRemainingWidth(int[] sourceBits, int destination, int width) {
        if (width > sourceBits.length - destination) {
            throw new IllegalArgumentException("Total width exceeds " + sourceBits.length);
        }
    }

    // The factories supply a private array.
    BitShuffle(int[] sourceBits) {
        int wordSize = sourceBits.length;
        long[] masksByShift = new long[2 * wordSize - 1];
        long constantOnes = 0;
        for (int destination = 0; destination < wordSize; destination++) {
            int source = sourceBits[destination];
            if (source == ONE) {
                constantOnes |= 1L << destination;
            } else if (source != ZERO) {
                if (source < 0 || source >= wordSize) {
                    throw new IllegalArgumentException("Invalid source bit: " + source);
                }
                // Bits that move by the same distance share one mask and shift.
                int shiftIndex = destination - source + wordSize - 1;
                masksByShift[shiftIndex] |= 1L << source;
            }
        }

        int moveCount = 0;
        for (long mask : masksByShift) {
            if (mask != 0) {
                moveCount++;
            }
        }

        long[] masks = new long[moveCount];
        int[] shifts = new int[moveCount];
        int move = 0;
        for (int i = 0; i < masksByShift.length; i++) {
            long mask = masksByShift[i];
            if (mask != 0) {
                masks[move] = mask;
                // Encode the shift as shift + wordSize to keep stable entries nonzero.
                shifts[move++] = i + 1;
            }
        }
        this.sourceBits = sourceBits;
        this.ones = constantOnes;
        this.moveMasks = masks;
        this.encodedShifts = shifts;
    }

    int rearrange(int word) {
        int result = (int) ones;
        for (int i = 0; i < moveMasks.length; i++) {
            int shift = encodedShifts[i] - Integer.SIZE;
            int bits = word & (int) moveMasks[i];
            result |= shift < 0 ? bits >>> -shift : bits << shift;
        }
        return result;
    }

    long rearrange(long word) {
        long result = ones;
        for (int i = 0; i < moveMasks.length; i++) {
            int shift = encodedShifts[i] - Long.SIZE;
            long bits = word & moveMasks[i];
            result |= shift < 0 ? bits >>> -shift : bits << shift;
        }
        return result;
    }

    BitShuffle inverse() {
        int[] inverse = new int[sourceBits.length];
        long seen = 0;
        for (int destination = 0; destination < sourceBits.length; destination++) {
            int source = sourceBits[destination];
            if (source < 0 || (seen & (1L << source)) != 0) {
                throw new IllegalStateException("Shuffle is not a permutation");
            }
            seen |= 1L << source;
            inverse[source] = destination;
        }
        return new BitShuffle(inverse);
    }
}
