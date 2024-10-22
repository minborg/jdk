/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @library ../
 * @modules java.base/jdk.internal.foreign
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestStructCarrierLayout
 */

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.*;

public class TestStructCarrierLayout {

    public record Point(int x, int y){}

    private static final StructLayout POINT = MemoryLayout.structLayout(
        JAVA_INT,
        JAVA_INT
    );

    private static final Function<MemorySegment, Point> UNMARSHALLER =
            s -> new Point(s.get(JAVA_INT, 0), s.get(JAVA_INT, 4));

    private static final BiConsumer<MemorySegment, Point> MARSHALLER = (s, p) -> {
        s.set(JAVA_INT, 0, p.x());
        s.set(JAVA_INT, 4, p.y());
    };

    private static final StructLayout.OfCarrier<Point> POINT_CARRIER =
            POINT.withCarrier(Point.class, UNMARSHALLER, MARSHALLER);

    private static final Point P0 = new Point(3, 4);
    private static final Point P1 = new Point(6, 8);
    private static final int[] PO_P1_INT_ARRAY = new int[]{3, 4, 6, 8};

    @Test
    public void set() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocate(POINT_CARRIER, 2);
            segment.set(POINT_CARRIER, 0, P0);
            segment.set(POINT_CARRIER, 8, P1);
            assertArrayEquals(PO_P1_INT_ARRAY, segment.toArray(JAVA_INT));
        }
    }

    @Test
    public void setAt() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocate(POINT_CARRIER, 2);
            segment.setAtIndex(POINT_CARRIER, 0, P0);
            segment.setAtIndex(POINT_CARRIER, 1, P1);
            assertArrayEquals(PO_P1_INT_ARRAY, segment.toArray(JAVA_INT));
        }
    }

    @Test
    public void get() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocateFrom(JAVA_INT, PO_P1_INT_ARRAY);
            Point p0 = segment.get(POINT_CARRIER, 0);
            Point p1 = segment.get(POINT_CARRIER, 8);
            assertEquals(P0, p0);
            assertEquals(P1, p1);
        }
    }

    @Test
    public void getAtIndex() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocateFrom(JAVA_INT, PO_P1_INT_ARRAY);
            Point p0 = segment.getAtIndex(POINT_CARRIER, 0);
            Point p1 = segment.getAtIndex(POINT_CARRIER, 1);
            assertEquals(P0, p0);
            assertEquals(P1, p1);
        }
    }

    @Test
    public void allocateFrom() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocateFrom(POINT_CARRIER, P0, P1);
            assertArrayEquals(PO_P1_INT_ARRAY, segment.toArray(JAVA_INT));
        }
    }

    @Test
    public void toArray() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocateFrom(POINT_CARRIER, P0, P1);
            Point[] points = segment.toArray(POINT_CARRIER);
            assertArrayEquals(new Point[]{P0, P1}, points);
        }
    }

    @Test
    public void stream() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocateFrom(POINT_CARRIER, P0, P1);
            Stream<Point> stream = segment.elements(POINT_CARRIER);
            assertArrayEquals(new Point[]{P0, P1}, stream.toArray());
        }
    }


}
