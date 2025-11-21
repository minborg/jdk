package java.foreign.mapper;/*
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
 * @run junit TestExtendsOtherInterface
 */

import org.junit.jupiter.api.Test;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class TestExtendsOtherInterface {

    interface HiddenMapped {
        MemorySegment _segment();
    }

    // It might be _optional_ to implement this interface
    // Some interfaces might be derived from other sources.
    // There might be fields already named `layout` etc.

    interface Fat<T extends Fat<T>> extends Iterable<T> {

        MemorySegment segment();
        GroupLayout layout();
        Function<MemorySegment, T> binder();

        default Stream<T> stream() {
            return segment().elements(layout())
                    .map(binder());
        }

        default T atDeltaIndex(long deltaIndex) {
            return binder().apply(segment().asSlice(layout().scale(0, deltaIndex)));
        }

    }

    static final StructLayout POINT = MemoryLayout.structLayout(
            JAVA_INT.withName("x"),
            JAVA_INT.withName("y")
    );

    interface Point extends Fat<Point> {
        int x();

        int y();

        void x(int x);

        void y(int y);
    }

    static final Function<MemorySegment, Point> POINT_BINDER = POINT.binder(Point.class);

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
        Point point = POINT_BINDER.apply(pointSegment());
        MemorySegment ptr = point.segment();
    }

    @Test
    void streaming() {
        // Point is several points so it is a bit confusing
        Point point = POINT_BINDER.apply(pointSegment());
        var points = point.stream().toList(); // [Point[3, 4], Point[6, 8]]
    }

    @Test
    void iterating() {
        Point points = POINT_BINDER.apply(pointSegment());
        // Nice
        for (var point : points) {
            // ...
        }
    }

    @Test
    void cursor() {
        Point point = POINT_BINDER.apply(pointSegment());
        Point nextPoint = point.iterator().next();
    }

    @Test
    void array() {
        Point points = POINT_BINDER.apply(pointSegment());
        long index = 1;
        Point point = points.atDeltaIndex(index); // Point[6, 8]
    }

    @Test
    void arrayPointer() {
        Point points = POINT_BINDER.apply(pointSegment());
        long index = 1;
        Point point = points.atDeltaIndex(index); // Point[6, 8]
        MemorySegment ptr = point.segment(); // This is all the rest of the points.... Not good
    }


}
