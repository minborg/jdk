package java.lang.foreign;

import jdk.internal.foreign.AbstractMemorySegmentImpl;
import jdk.internal.foreign.SegmentCursorImpl;

import java.util.function.LongBinaryOperator;

/**
 * Some comments
 */
public sealed interface SegmentCursor permits SegmentCursorImpl {
    /**
     * Some comments
     * @return offset
     */
    long offset();

    /**
     * Some comments
     * @return segment
     */
    MemorySegment segment();

    /**
     * Some comments
     * @return cursor
     */
    SegmentCursor left();

    /**
     * Some comments
     * @return cursor
     */
    SegmentCursor right();

    // accessors
    /**
     * Some comments
     * @param layout layout
     * @return boolean
     */
    boolean get(ValueLayout.OfBoolean layout);

    /**
     * Some comments
     * @param layout layout
     * @return char
     */
    char get(ValueLayout.OfChar layout);

    /**
     * Some comments
     * @param layout layout
     * @return byte
     */
    byte get(ValueLayout.OfByte layout);

    /**
     * Some comments
     * @param layout layout
     * @return short
     */
    short get(ValueLayout.OfShort layout);

    /**
     * Some comments
     * @param layout layout
     * @return int
     */
    int get(ValueLayout.OfInt layout);

    /**
     * Some comments
     * @param layout layout
     * @return long
     */
    long get(ValueLayout.OfLong layout);

    /**
     * Some comments
     * @param layout layout
     * @return float
     */
    float get(ValueLayout.OfFloat layout);

    /**
     * Some comments
     * @param layout layout
     * @return double
     */
    double get(ValueLayout.OfDouble layout);

    /**
     * Some comments
     * @param segment segment
     * @param offsetUpdater updater
     * @return cursor
     */
    static SegmentCursor of(MemorySegment segment, LongBinaryOperator offsetUpdater) {
        return new SegmentCursorImpl((AbstractMemorySegmentImpl) segment, 0, segment.byteSize(), offsetUpdater);
    }

    /** offset updater to the middle of a cursor */
    LongBinaryOperator MID = (base, limit) -> (base + limit) / 2;
//
//    static void main(String[] args) {
//        MemorySegment segment = Arena.ofAuto().allocate(1024);
//        // fill segment
//        for (int i = 0 ; i < 1024 / 4 ; i++) {
//            segment.setAtIndex(ValueLayout.JAVA_INT_UNALIGNED, i, i);
//        }
//        System.out.println(binarySearch(-42, segment));
//    }
//
//    static long binarySearch(int needle, MemorySegment segment) {
//        SegmentCursor cursor = SegmentCursor.of(segment, MID);
//        while (true) {
//            int curr = cursor.get(ValueLayout.JAVA_INT_UNALIGNED);
//            if (curr == needle) {
//                return (cursor.segment().address() - segment.address() + cursor.offset()) / 4;
//            } else if (curr > needle) {
//                cursor = cursor.left();
//            } else {
//                cursor = cursor.right();
//            }
//        }
//    }
}
