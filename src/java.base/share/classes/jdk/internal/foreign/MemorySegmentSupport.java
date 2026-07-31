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

import jdk.internal.access.foreign.UnmapperProxy;
import jdk.internal.misc.ScopedMemoryAccess;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.TrustFinalFields;

import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;

/**
 * Describes the storage-specific behavior of a memory segment.
 *
 * <p>The {@link AbstractMemorySegmentImpl} class is the only implementation of
 * {@code MemorySegment}. Differences between native, mapped and heap storage
 * are represented by implementations of this support class instead of by
 * subclasses of the memory segment implementation.</p>
 */
abstract sealed class MemorySegmentSupport
        permits MemorySegmentSupport.Heap, MemorySegmentSupport.Native {

    abstract MemorySegmentSupport slice(long offset);

    abstract long address();

    abstract long unsafeGetOffset();

    abstract Object unsafeGetBase();

    abstract long maxAlignMask();

    abstract long maxByteAlignment();

    abstract ByteBuffer makeByteBuffer(AbstractMemorySegmentImpl segment);

    abstract String kind();

    Optional<Object> heapBase(boolean readOnly) {
        return Optional.empty();
    }

    boolean isNative() {
        return false;
    }

    boolean isMapped() {
        return false;
    }

    void load(AbstractMemorySegmentImpl segment) {
        throw notAMappedSegment();
    }

    void unload(AbstractMemorySegmentImpl segment) {
        throw notAMappedSegment();
    }

    boolean isLoaded(AbstractMemorySegmentImpl segment) {
        throw notAMappedSegment();
    }

    void force(AbstractMemorySegmentImpl segment) {
        throw notAMappedSegment();
    }

    private static UnsupportedOperationException notAMappedSegment() {
        return new UnsupportedOperationException("Not a mapped segment");
    }

    static Native ofNative(long address) {
        return new Native(address);
    }

    static Mapped ofMapped(long address, UnmapperProxy unmapper) {
        return new Mapped(address, unmapper);
    }

    static OfByte ofByte(long offset, Object base) {
        return new OfByte(offset, base);
    }

    static OfChar ofChar(long offset, Object base) {
        return new OfChar(offset, base);
    }

    static OfShort ofShort(long offset, Object base) {
        return new OfShort(offset, base);
    }

    static OfInt ofInt(long offset, Object base) {
        return new OfInt(offset, base);
    }

    static OfLong ofLong(long offset, Object base) {
        return new OfLong(offset, base);
    }

    static OfFloat ofFloat(long offset, Object base) {
        return new OfFloat(offset, base);
    }

    static OfDouble ofDouble(long offset, Object base) {
        return new OfDouble(offset, base);
    }

    static sealed class Native extends MemorySegmentSupport permits Mapped {
        final long min;

        Native(long min) {
            this.min = Unsafe.ADDRESS_SIZE == 4
                    // On 32-bit systems, normalize the upper unused 32-bits to zero
                    ? min & 0x0000_0000_FFFF_FFFFL
                    // On 64-bit systems, all the bits are used
                    : min;
            super();
        }

        @Override
        MemorySegmentSupport slice(long offset) {
            return new Native(min + offset);
        }

        @Override
        long address() {
            return min;
        }

        @Override
        long unsafeGetOffset() {
            return min;
        }

        @Override
        Object unsafeGetBase() {
            return null;
        }

        @Override
        long maxAlignMask() {
            return 0;
        }

        @Override
        long maxByteAlignment() {
            return min == 0 ? 1L << 62 : Long.lowestOneBit(min);
        }

        @Override
        ByteBuffer makeByteBuffer(AbstractMemorySegmentImpl segment) {
            return AbstractMemorySegmentImpl.NIO_ACCESS.newDirectByteBuffer(
                    min, (int) segment.length, null, segment);
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

        private final UnmapperProxy unmapper;

        Mapped(long min, UnmapperProxy unmapper) {
            this.unmapper = unmapper;
            super(min);
        }

        @Override
        MemorySegmentSupport slice(long offset) {
            return new Mapped(min + offset, unmapper);
        }

        @Override
        ByteBuffer makeByteBuffer(AbstractMemorySegmentImpl segment) {
            return AbstractMemorySegmentImpl.NIO_ACCESS.newMappedByteBuffer(
                    unmapper, min, (int) segment.length, null, segment);
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
        void load(AbstractMemorySegmentImpl segment) {
            if (unmapper != null) {
                SCOPED_MEMORY_ACCESS.load(segment.sessionImpl(),
                        AbstractMemorySegmentImpl.NIO_ACCESS.mappedMemoryUtils(),
                        min, unmapper.isSync(), segment.length);
            }
        }

        @Override
        void unload(AbstractMemorySegmentImpl segment) {
            if (unmapper != null) {
                SCOPED_MEMORY_ACCESS.unload(segment.sessionImpl(),
                        AbstractMemorySegmentImpl.NIO_ACCESS.mappedMemoryUtils(),
                        min, unmapper.isSync(), segment.length);
            }
        }

        @Override
        boolean isLoaded(AbstractMemorySegmentImpl segment) {
            return unmapper == null ||
                    SCOPED_MEMORY_ACCESS.isLoaded(segment.sessionImpl(),
                            AbstractMemorySegmentImpl.NIO_ACCESS.mappedMemoryUtils(),
                            min, unmapper.isSync(), segment.length);
        }

        @Override
        void force(AbstractMemorySegmentImpl segment) {
            if (unmapper != null) {
                SCOPED_MEMORY_ACCESS.force(segment.sessionImpl(),
                        AbstractMemorySegmentImpl.NIO_ACCESS.mappedMemoryUtils(),
                        unmapper.fileDescriptor(), min, unmapper.isSync(), 0, segment.length);
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

        final long offset;
        final Object base;

        Heap(long offset, Object base) {
            this.offset = offset;
            this.base = base;
            super();
        }

        abstract long arrayBaseOffset();

        @Override
        long address() {
            return offset - arrayBaseOffset();
        }

        @Override
        long unsafeGetOffset() {
            return offset;
        }

        @Override
        Optional<Object> heapBase(boolean readOnly) {
            return readOnly ? Optional.empty() : Optional.of(base);
        }

        @Override
        long maxByteAlignment() {
            long address = address();
            return address == 0
                    ? maxAlignMask()
                    : Math.min(maxAlignMask(), Long.lowestOneBit(address));
        }

        @Override
        ByteBuffer makeByteBuffer(AbstractMemorySegmentImpl segment) {
            if (!(base instanceof byte[] bytes)) {
                throw new UnsupportedOperationException(
                        "Not an address to a heap-allocated byte array");
            }
            return AbstractMemorySegmentImpl.NIO_ACCESS.newHeapByteBuffer(bytes,
                    (int) (offset - Utils.BaseAndScale.BYTE.base()),
                    (int) segment.length, null);
        }

        @Override
        String kind() {
            return "heap";
        }
    }

    static final class OfByte extends Heap {
        OfByte(long offset, Object base) {
            super(offset, base);
        }

        @Override
        MemorySegmentSupport slice(long delta) {
            return new OfByte(offset + delta, base);
        }

        @Override
        byte[] unsafeGetBase() {
            return (byte[]) Objects.requireNonNull(base);
        }

        @Override
        long maxAlignMask() {
            return MAX_ALIGN_BYTE_ARRAY;
        }

        @Override
        long arrayBaseOffset() {
            return Utils.BaseAndScale.BYTE.base();
        }
    }

    static final class OfChar extends Heap {
        OfChar(long offset, Object base) {
            super(offset, base);
        }

        @Override
        MemorySegmentSupport slice(long delta) {
            return new OfChar(offset + delta, base);
        }

        @Override
        char[] unsafeGetBase() {
            return (char[]) Objects.requireNonNull(base);
        }

        @Override
        long maxAlignMask() {
            return MAX_ALIGN_SHORT_ARRAY;
        }

        @Override
        long arrayBaseOffset() {
            return Utils.BaseAndScale.CHAR.base();
        }
    }

    static final class OfShort extends Heap {
        OfShort(long offset, Object base) {
            super(offset, base);
        }

        @Override
        MemorySegmentSupport slice(long delta) {
            return new OfShort(offset + delta, base);
        }

        @Override
        short[] unsafeGetBase() {
            return (short[]) Objects.requireNonNull(base);
        }

        @Override
        long maxAlignMask() {
            return MAX_ALIGN_SHORT_ARRAY;
        }

        @Override
        long arrayBaseOffset() {
            return Utils.BaseAndScale.SHORT.base();
        }
    }

    static final class OfInt extends Heap {
        OfInt(long offset, Object base) {
            super(offset, base);
        }

        @Override
        MemorySegmentSupport slice(long delta) {
            return new OfInt(offset + delta, base);
        }

        @Override
        int[] unsafeGetBase() {
            return (int[]) Objects.requireNonNull(base);
        }

        @Override
        long maxAlignMask() {
            return MAX_ALIGN_INT_ARRAY;
        }

        @Override
        long arrayBaseOffset() {
            return Utils.BaseAndScale.INT.base();
        }
    }

    static final class OfLong extends Heap {
        OfLong(long offset, Object base) {
            super(offset, base);
        }

        @Override
        MemorySegmentSupport slice(long delta) {
            return new OfLong(offset + delta, base);
        }

        @Override
        long[] unsafeGetBase() {
            return (long[]) Objects.requireNonNull(base);
        }

        @Override
        long maxAlignMask() {
            return MAX_ALIGN_LONG_ARRAY;
        }

        @Override
        long arrayBaseOffset() {
            return Utils.BaseAndScale.LONG.base();
        }
    }

    static final class OfFloat extends Heap {
        OfFloat(long offset, Object base) {
            super(offset, base);
        }

        @Override
        MemorySegmentSupport slice(long delta) {
            return new OfFloat(offset + delta, base);
        }

        @Override
        float[] unsafeGetBase() {
            return (float[]) Objects.requireNonNull(base);
        }

        @Override
        long maxAlignMask() {
            return MAX_ALIGN_INT_ARRAY;
        }

        @Override
        long arrayBaseOffset() {
            return Utils.BaseAndScale.FLOAT.base();
        }
    }

    static final class OfDouble extends Heap {
        OfDouble(long offset, Object base) {
            super(offset, base);
        }

        @Override
        MemorySegmentSupport slice(long delta) {
            return new OfDouble(offset + delta, base);
        }

        @Override
        double[] unsafeGetBase() {
            return (double[]) Objects.requireNonNull(base);
        }

        @Override
        long maxAlignMask() {
            return MAX_ALIGN_LONG_ARRAY;
        }

        @Override
        long arrayBaseOffset() {
            return Utils.BaseAndScale.DOUBLE.base();
        }
    }
}
