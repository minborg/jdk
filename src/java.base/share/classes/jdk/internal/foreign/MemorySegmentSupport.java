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

package jdk.internal.foreign;

import jdk.internal.misc.ScopedMemoryAccess;
import jdk.internal.misc.Unsafe;

import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;

/**
 * Describes the storage-specific behavior of a memory segment.
 *
 * <p>The {@link MemorySegmentImpl} class is the only implementation of
 * {@code MemorySegment}. All per-segment state is stored in that class;
 * instances of this class are stateless, shared strategies.</p>
 */
abstract sealed class MemorySegmentSupport
        permits MemorySegmentSupport.Heap, MemorySegmentSupport.Native {

    private static final MemorySegmentSupport NATIVE = new Native();
    private static final MemorySegmentSupport MAPPED = new Mapped();
    private static final MemorySegmentSupport OF_BYTE = new OfByte();
    private static final MemorySegmentSupport OF_CHAR = new OfChar();
    private static final MemorySegmentSupport OF_SHORT = new OfShort();
    private static final MemorySegmentSupport OF_INT = new OfInt();
    private static final MemorySegmentSupport OF_LONG = new OfLong();
    private static final MemorySegmentSupport OF_FLOAT = new OfFloat();
    private static final MemorySegmentSupport OF_DOUBLE = new OfDouble();

    long normalizeOffset(long offset) {
        return offset;
    }

    abstract long address(MemorySegmentImpl segment);

    abstract Object unsafeGetBase(MemorySegmentImpl segment);

    abstract long maxAlignMask(MemorySegmentImpl segment);

    abstract long maxByteAlignment(MemorySegmentImpl segment);

    abstract ByteBuffer makeByteBuffer(MemorySegmentImpl segment);

    abstract String kind();

    Optional<Object> heapBase(MemorySegmentImpl segment) {
        return Optional.empty();
    }

    boolean isNative() {
        return false;
    }

    boolean isMapped() {
        return false;
    }

    void load(MemorySegmentImpl segment) {
        throw notAMappedSegment();
    }

    void unload(MemorySegmentImpl segment) {
        throw notAMappedSegment();
    }

    boolean isLoaded(MemorySegmentImpl segment) {
        throw notAMappedSegment();
    }

    void force(MemorySegmentImpl segment) {
        throw notAMappedSegment();
    }

    private static UnsupportedOperationException notAMappedSegment() {
        return new UnsupportedOperationException("Not a mapped segment");
    }

    static MemorySegmentSupport nativeSupport() {
        return NATIVE;
    }

    static MemorySegmentSupport mappedSupport() {
        return MAPPED;
    }

    static MemorySegmentSupport ofByteSupport() {
        return OF_BYTE;
    }

    static MemorySegmentSupport ofCharSupport() {
        return OF_CHAR;
    }

    static MemorySegmentSupport ofShortSupport() {
        return OF_SHORT;
    }

    static MemorySegmentSupport ofIntSupport() {
        return OF_INT;
    }

    static MemorySegmentSupport ofLongSupport() {
        return OF_LONG;
    }

    static MemorySegmentSupport ofFloatSupport() {
        return OF_FLOAT;
    }

    static MemorySegmentSupport ofDoubleSupport() {
        return OF_DOUBLE;
    }

    static sealed class Native extends MemorySegmentSupport permits Mapped {
        private Native() {
        }

        @Override
        long normalizeOffset(long offset) {
            return Unsafe.ADDRESS_SIZE == 4
                    // On 32-bit systems, normalize the upper unused 32-bits to zero
                    ? offset & 0x0000_0000_FFFF_FFFFL
                    // On 64-bit systems, all the bits are used
                    : offset;
        }

        @Override
        long address(MemorySegmentImpl segment) {
            return segment.offset;
        }

        @Override
        Object unsafeGetBase(MemorySegmentImpl segment) {
            return null;
        }

        @Override
        long maxAlignMask(MemorySegmentImpl segment) {
            return 0;
        }

        @Override
        long maxByteAlignment(MemorySegmentImpl segment) {
            return segment.offset == 0
                    ? 1L << 62
                    : Long.lowestOneBit(segment.offset);
        }

        @Override
        ByteBuffer makeByteBuffer(MemorySegmentImpl segment) {
            return MemorySegmentImpl.NIO_ACCESS.newDirectByteBuffer(
                    segment.offset, (int) segment.length, null, segment);
        }

        @Override
        String kind() {
            return "native";
        }

        @Override
        boolean isNative() {
            return true;
        }
    }

    static final class Mapped extends Native {
        private static final ScopedMemoryAccess SCOPED_MEMORY_ACCESS =
                ScopedMemoryAccess.getScopedMemoryAccess();

        private Mapped() {
        }

        @Override
        ByteBuffer makeByteBuffer(MemorySegmentImpl segment) {
            return MemorySegmentImpl.NIO_ACCESS.newMappedByteBuffer(
                    segment.unmapper, segment.offset, (int) segment.length, null, segment);
        }

        @Override
        String kind() {
            return "mapped";
        }

        @Override
        boolean isMapped() {
            return true;
        }

        @Override
        void load(MemorySegmentImpl segment) {
            if (segment.unmapper != null) {
                SCOPED_MEMORY_ACCESS.load(segment.sessionImpl(),
                        MemorySegmentImpl.NIO_ACCESS.mappedMemoryUtils(),
                        segment.offset, segment.unmapper.isSync(), segment.length);
            }
        }

        @Override
        void unload(MemorySegmentImpl segment) {
            if (segment.unmapper != null) {
                SCOPED_MEMORY_ACCESS.unload(segment.sessionImpl(),
                        MemorySegmentImpl.NIO_ACCESS.mappedMemoryUtils(),
                        segment.offset, segment.unmapper.isSync(), segment.length);
            }
        }

        @Override
        boolean isLoaded(MemorySegmentImpl segment) {
            return segment.unmapper == null ||
                    SCOPED_MEMORY_ACCESS.isLoaded(segment.sessionImpl(),
                            MemorySegmentImpl.NIO_ACCESS.mappedMemoryUtils(),
                            segment.offset, segment.unmapper.isSync(), segment.length);
        }

        @Override
        void force(MemorySegmentImpl segment) {
            if (segment.unmapper != null) {
                SCOPED_MEMORY_ACCESS.force(segment.sessionImpl(),
                        MemorySegmentImpl.NIO_ACCESS.mappedMemoryUtils(),
                        segment.unmapper.fileDescriptor(), segment.offset,
                        segment.unmapper.isSync(), 0, segment.length);
            }
        }
    }

    abstract static sealed class Heap extends MemorySegmentSupport
            permits OfByte, OfChar, OfShort, OfInt, OfLong, OfFloat, OfDouble {

        // Constants defining the maximum alignment supported by heap arrays.
        static final long MAX_ALIGN_BYTE_ARRAY = ValueLayout.JAVA_BYTE.byteAlignment();
        static final long MAX_ALIGN_SHORT_ARRAY = ValueLayout.JAVA_SHORT.byteAlignment();
        static final long MAX_ALIGN_INT_ARRAY = ValueLayout.JAVA_INT.byteAlignment();
        static final long MAX_ALIGN_LONG_ARRAY = ValueLayout.JAVA_LONG.byteAlignment();

        private Heap() {
        }

        abstract long arrayBaseOffset();

        @Override
        long address(MemorySegmentImpl segment) {
            return segment.offset - arrayBaseOffset();
        }

        @Override
        Optional<Object> heapBase(MemorySegmentImpl segment) {
            return segment.readOnly
                    ? Optional.empty()
                    : Optional.of(segment.base);
        }

        @Override
        long maxByteAlignment(MemorySegmentImpl segment) {
            long address = address(segment);
            return address == 0
                    ? maxAlignMask(segment)
                    : Math.min(maxAlignMask(segment), Long.lowestOneBit(address));
        }

        @Override
        ByteBuffer makeByteBuffer(MemorySegmentImpl segment) {
            if (!(segment.base instanceof byte[] bytes)) {
                throw new UnsupportedOperationException(
                        "Not an address to a heap-allocated byte array");
            }
            return MemorySegmentImpl.NIO_ACCESS.newHeapByteBuffer(bytes,
                    (int) (segment.offset - Utils.BaseAndScale.BYTE.base()),
                    (int) segment.length, null);
        }

        @Override
        String kind() {
            return "heap";
        }
    }

    static final class OfByte extends Heap {
        private OfByte() {
        }

        @Override
        byte[] unsafeGetBase(MemorySegmentImpl segment) {
            return (byte[]) Objects.requireNonNull(segment.base);
        }

        @Override
        long maxAlignMask(MemorySegmentImpl segment) {
            return MAX_ALIGN_BYTE_ARRAY;
        }

        @Override
        long arrayBaseOffset() {
            return Utils.BaseAndScale.BYTE.base();
        }
    }

    static final class OfChar extends Heap {
        private OfChar() {
        }

        @Override
        char[] unsafeGetBase(MemorySegmentImpl segment) {
            return (char[]) Objects.requireNonNull(segment.base);
        }

        @Override
        long maxAlignMask(MemorySegmentImpl segment) {
            return MAX_ALIGN_SHORT_ARRAY;
        }

        @Override
        long arrayBaseOffset() {
            return Utils.BaseAndScale.CHAR.base();
        }
    }

    static final class OfShort extends Heap {
        private OfShort() {
        }

        @Override
        short[] unsafeGetBase(MemorySegmentImpl segment) {
            return (short[]) Objects.requireNonNull(segment.base);
        }

        @Override
        long maxAlignMask(MemorySegmentImpl segment) {
            return MAX_ALIGN_SHORT_ARRAY;
        }

        @Override
        long arrayBaseOffset() {
            return Utils.BaseAndScale.SHORT.base();
        }
    }

    static final class OfInt extends Heap {
        private OfInt() {
        }

        @Override
        int[] unsafeGetBase(MemorySegmentImpl segment) {
            return (int[]) Objects.requireNonNull(segment.base);
        }

        @Override
        long maxAlignMask(MemorySegmentImpl segment) {
            return MAX_ALIGN_INT_ARRAY;
        }

        @Override
        long arrayBaseOffset() {
            return Utils.BaseAndScale.INT.base();
        }
    }

    static final class OfLong extends Heap {
        private OfLong() {
        }

        @Override
        long[] unsafeGetBase(MemorySegmentImpl segment) {
            return (long[]) Objects.requireNonNull(segment.base);
        }

        @Override
        long maxAlignMask(MemorySegmentImpl segment) {
            return MAX_ALIGN_LONG_ARRAY;
        }

        @Override
        long arrayBaseOffset() {
            return Utils.BaseAndScale.LONG.base();
        }
    }

    static final class OfFloat extends Heap {
        private OfFloat() {
        }

        @Override
        float[] unsafeGetBase(MemorySegmentImpl segment) {
            return (float[]) Objects.requireNonNull(segment.base);
        }

        @Override
        long maxAlignMask(MemorySegmentImpl segment) {
            return MAX_ALIGN_INT_ARRAY;
        }

        @Override
        long arrayBaseOffset() {
            return Utils.BaseAndScale.FLOAT.base();
        }
    }

    static final class OfDouble extends Heap {
        private OfDouble() {
        }

        @Override
        double[] unsafeGetBase(MemorySegmentImpl segment) {
            return (double[]) Objects.requireNonNull(segment.base);
        }

        @Override
        long maxAlignMask(MemorySegmentImpl segment) {
            return MAX_ALIGN_LONG_ARRAY;
        }

        @Override
        long arrayBaseOffset() {
            return Utils.BaseAndScale.DOUBLE.base();
        }
    }
}
