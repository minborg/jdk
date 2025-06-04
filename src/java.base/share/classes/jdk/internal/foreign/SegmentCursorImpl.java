package jdk.internal.foreign;

import jdk.internal.misc.ScopedMemoryAccess;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentCursor;
import java.lang.foreign.ValueLayout;
import java.lang.foreign.ValueLayout.OfBoolean;
import java.lang.foreign.ValueLayout.OfByte;
import java.lang.foreign.ValueLayout.OfChar;
import java.lang.foreign.ValueLayout.OfDouble;
import java.lang.foreign.ValueLayout.OfFloat;
import java.lang.foreign.ValueLayout.OfInt;
import java.lang.foreign.ValueLayout.OfLong;
import java.lang.foreign.ValueLayout.OfShort;
import java.nio.ByteOrder;
import java.util.function.LongBinaryOperator;

public final class SegmentCursorImpl implements SegmentCursor {

    static final ScopedMemoryAccess SCOPED_MEMORY_ACCESS = ScopedMemoryAccess.getScopedMemoryAccess();

    final AbstractMemorySegmentImpl segment;
    final long base;
    final long limit;
    final long offset;
    final LongBinaryOperator offsetUpdater;

    public SegmentCursorImpl(AbstractMemorySegmentImpl segment, long base, long limit, LongBinaryOperator offsetUpdater) {
        this.segment = segment;
        this.base = base;
        this.limit = limit;
        this.offsetUpdater = offsetUpdater;
        this.offset = offsetUpdater.applyAsLong(base, limit);
    }

    @Override
    public MemorySegment segment() {
        return segment;
    }

    @Override
    public boolean get(OfBoolean layout) {
        check(layout);
        return SCOPED_MEMORY_ACCESS.getBoolean(
                segment.sessionImpl(),
                segment.unsafeGetBase(),
                segment.unsafeGetOffset() + offset);
    }

    @Override
    public char get(OfChar layout) {
        check(layout);
        return SCOPED_MEMORY_ACCESS.getCharUnaligned(
                segment.sessionImpl(),
                segment.unsafeGetBase(),
                segment.unsafeGetOffset() + offset,
                layout.order() == ByteOrder.BIG_ENDIAN);
    }

    @Override
    public byte get(OfByte layout) {
        check(layout);
        return SCOPED_MEMORY_ACCESS.getByte(
                segment.sessionImpl(),
                segment.unsafeGetBase(),
                segment.unsafeGetOffset() + offset);
    }

    @Override
    public short get(OfShort layout) {
        check(layout);
        return SCOPED_MEMORY_ACCESS.getShortUnaligned(
                segment.sessionImpl(),
                segment.unsafeGetBase(),
                segment.unsafeGetOffset() + offset,
                layout.order() == ByteOrder.BIG_ENDIAN);
    }

    @Override
    public int get(OfInt layout) {
        check(layout);
        return SCOPED_MEMORY_ACCESS.getIntUnaligned(
                segment.sessionImpl(),
                segment.unsafeGetBase(),
                segment.unsafeGetOffset() + offset,
                layout.order() == ByteOrder.BIG_ENDIAN);
    }

    @Override
    public long get(OfLong layout) {
        check(layout);
        return SCOPED_MEMORY_ACCESS.getLongUnaligned(
                segment.sessionImpl(),
                segment.unsafeGetBase(),
                segment.unsafeGetOffset() + offset,
                layout.order() == ByteOrder.BIG_ENDIAN);
    }

    @Override
    public float get(OfFloat layout) {
        check(layout);
        return Float.intBitsToFloat(SCOPED_MEMORY_ACCESS.getIntUnaligned(
                segment.sessionImpl(),
                segment.unsafeGetBase(),
                segment.unsafeGetOffset() + offset,
                layout.order() == ByteOrder.BIG_ENDIAN));
    }

    @Override
    public double get(OfDouble layout) {
        check(layout);
        return Double.longBitsToDouble(SCOPED_MEMORY_ACCESS.getLongUnaligned(
                segment.sessionImpl(),
                segment.unsafeGetBase(),
                segment.unsafeGetOffset() + offset,
                layout.order() == ByteOrder.BIG_ENDIAN));
    }

    @Override
    public long offset() {
        return offset;
    }

    private void check(ValueLayout layout) {
        if (segment.maxByteAlignment() < layout.byteAlignment()) {
            throw new IllegalArgumentException("Bad access alignment: " + layout.byteAlignment());
        }
        if (layout.byteSize() > (limit - offset)) {
            throw new IllegalArgumentException("Bad access size: " + layout.byteSize());
        }
    }

    @Override
    public SegmentCursor left() {
        return new SegmentCursorImpl(segment, 0, offset, offsetUpdater);
    }

    @Override
    public SegmentCursor right() {
        return new SegmentCursorImpl(segment, offset, limit, offsetUpdater);
    }

    @Override
    public String toString() {
        return String.format("Cursor(base=%d, limit=%d, offset=%d)", base, limit, offset);
    }
}
