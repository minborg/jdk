/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test basic operations for mappers
 * @run junit TestMinimalistic
 */

import org.junit.jupiter.api.Test;

import java.lang.foreign.*;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.function.Function;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.*;

final class TestMinimalistic {

    // Open issues:
    //   Should we generate nice toString,equals and hashCode or should we retain Object's methods?
    //   Perhaps just override toString() -> Point[x=3, y=4]
    //   Partial mapping?
    //   If the interface only contains read operations then we could store a read-only segment
    //   What about thread safety. Barriers?
    //   How to handle sequence layouts? Maybe SequenceLayout has another binder() with an extra arity???

    interface OptionalMapped<T extends OptionalMapped<T>> {
        MemorySegment segment();
        long offset();
    }

    static final StructLayout POINT = MemoryLayout.structLayout(
            JAVA_INT.withName("x"),
            JAVA_INT.withName("y")
    );

    interface Point {
        int x();

        int y();

        void x(int x);

        void y(int y);
    }

    static final StructLayout LINE = MemoryLayout.structLayout(
            POINT.withName("begin"),
            POINT.withName("end")
    );

    interface Line {
        Point/*!*/ begin();
        Point/*!*/ end();
    }

    // Generated
    /* value */ record PointImpl(MemorySegment/*!*/ segment, long offset) implements Point {

        static final VarHandle X = POINT.varHandle(MemoryLayout.PathElement.groupElement("x"));
        static final VarHandle Y = POINT.varHandle(MemoryLayout.PathElement.groupElement("y"));

        PointImpl {
            Objects.requireNonNull(segment);
            if (offset < 0) {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public int x() {
            return (int) X.get(segment, offset);
        }

        @Override
        public int y() {
            return (int) Y.get(segment, offset);
        }

        @Override
        public void x(int x) {
            X.set(segment, offset, x);
        }

        @Override
        public void y(int y) {
            Y.set(segment, offset, y);
        }

        @Override
        public String toString() {
            return "Point[x=" + x() + ", y=" + y() + "]";
        }

        // Retain the Object::hashCode and Object::equals methods ???
        // Partially mapped objects will be difficult to handle otherwise

        @Override
        public boolean equals(Object o) {
            return (o instanceof Point other)
                    && x() == other.x()
                    && y() == other.y();
        }

        @Override
        public int hashCode() {
            return Objects.hash(x(), y());
        }

    }

    // Generated
    /* value */ record LineImpl(MemorySegment/*!*/ segment, long offset) implements Line {

        static final long BEGIN_OFFSET = LINE.byteOffset(MemoryLayout.PathElement.groupElement("begin"));
        static final long END_OFFSET = LINE.byteOffset(MemoryLayout.PathElement.groupElement("end"));

        LineImpl {
            Objects.requireNonNull(segment);
            if (offset < 0) {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public Point/*!*/ begin() {
            return new PointImpl(segment, offset + BEGIN_OFFSET);
        }

        @Override
        public Point/*!*/ end() {
            return new PointImpl(segment, offset + END_OFFSET);
        }

        @Override
        public String toString() {
            return "Line[begin=" + begin() + ", end=" + end() + "]";
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof Line other)
                    && begin().equals(other.begin())
                    && end().equals(other.end());
        }

        @Override
        public int hashCode() {
            return Objects.hash(begin(), end());
        }

    }

    static final Function<MemorySegment/*!*/, Point/*!*/> POINT_BINDER =
            // POINT.binder(Point.class);
            segment -> new PointImpl(segment, 0);

    static final int[] POINT_ARRAY = new int[]{3, 4, 6, 8};

    static MemorySegment pointSegment() {
        return MemorySegment.ofArray(POINT_ARRAY);
    }

    @Test
    void scalar() {
        MemorySegment segment = pointSegment();
        Point point = POINT_BINDER.apply(segment);

        // Reading
        assertEquals(POINT_ARRAY[0], point.x());
        assertEquals(POINT_ARRAY[1], point.y());

        // Writing
        point.x(42);
        assertEquals(42, segment.get(JAVA_INT, 0));
    }

    @Test
    void pointer() {
        MemorySegment segment = pointSegment();
        Point point = POINT_BINDER.apply(segment);
        // Pointer is segment -- a bit lame
    }

    @Test
    void streaming() {
        MemorySegment segment = pointSegment();
        var points = segment.elements(POINT)
                .map(POINT_BINDER)
                .toList(); // [Point[3, 4], Point[6, 8]]
    }

    @Test
    void iterating() {
        MemorySegment segment = pointSegment();
        // A bit ugly
        for (var s : (Iterable<MemorySegment>) segment.elements(POINT)::iterator) {
            Point point = POINT_BINDER.apply(segment);
            // ...
        }
    }

    @Test
    void cursor() {
        MemorySegment segment = pointSegment();
        Point point = POINT_BINDER.apply(segment);
        MemorySegment nextSegment = segment.asSlice(POINT.scale(0, 1), POINT);
        Point nextPoint = POINT_BINDER.apply(nextSegment);
    }

    @Test
    void array() {
        MemorySegment segment = pointSegment();
        long index = 1;
        Point point = POINT_BINDER.apply(segment.asSlice(POINT.scale(0, index), POINT));
    }

    @Test
    void arrayPointer() {
        MemorySegment segment = pointSegment();
        long index = 1;
        MemorySegment ptr = segment.asSlice(POINT.scale(0, index));
        Point point = POINT_BINDER.apply(ptr);
        // Pointer is ptr -- a bit lame
    }


    record PointRecord(int x, int y){}
    record LineRecord(PointRecord begin, PointRecord end){}

    @Test
    void records() {
        Point point = POINT_BINDER.apply(pointSegment());

        // Read
        PointRecord record = new PointRecord(point.x(), point.y());

        // Write
        point.x(record.x());
        point.y(record.y());

        var begin = POINT_BINDER.apply(pointSegment());
        var beginRecord = new PointRecord(begin.x(), begin.y());
        var end = POINT_BINDER.apply(pointSegment().asSlice(LINE.byteOffset(MemoryLayout.PathElement.groupElement("end")), POINT));
        var endRecord = new PointRecord(end.x(), end.y());

        var line = new LineRecord(beginRecord, endRecord);


    }


}
