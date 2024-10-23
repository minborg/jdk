/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestSequenceCarrierLayout
 */

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.CompositeLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.StructLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.stream.Stream;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.*;

final class TestSequenceCarrierLayout {

    public record Point(int x, int y){}

    private static final StructLayout POINT = MemoryLayout.structLayout(
        JAVA_INT,
        JAVA_INT
    );

    private static final int SIZE = 2;

    private static final SequenceLayout POINTS = MemoryLayout.sequenceLayout(SIZE, POINT);


    // We only allow records in the first iteration.

    private static Point[] unmarshal(MemorySegment s, long o) {
        Point[] points = new Point[SIZE];
        for (int i = 0; i < SIZE; i++) {
            final long offset = o + i * 2 * 4;
            final Point point = new Point(s.get(JAVA_INT, offset), s.get(JAVA_INT, offset + 4));
            points[i] = point;
        }
        return points;
    }

    private static void marshal(MemorySegment s, long o, Point[] v) {
        if (v.length != SIZE) {
            throw new IllegalArgumentException();
        }
        for (int i = 0; i < SIZE; i++) {
            final long offset = o + i * 2 * 4;
            final Point point = v[i];
            s.set(JAVA_INT, offset, point.x());
            s.set(JAVA_INT, offset + 4, point.y());
        }
    }

    private static MethodHandle getter;
    private static MethodHandle setter;

    static {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            getter = lookup.findStatic(TestSequenceCarrierLayout.class, "unmarshal",
                    MethodType.methodType(Point[].class, MemorySegment.class, long.class));
            setter = lookup.findStatic(TestSequenceCarrierLayout.class, "marshal",
                    MethodType.methodType(void.class, MemorySegment.class, long.class, Point[].class));
        } catch (ReflectiveOperationException e) {
            fail(e);
        }
    }

    private static final CompositeLayout.OfClass<Point[]> POINTS_CARRIER =
            POINTS.bind(Point[].class, getter, setter);

    private static final Point P0 = new Point(3, 4);
    private static final Point P1 = new Point(6, 8);
    private static final Point[] POINT_ARRAY = new Point[]{P0, P1};
    private static final int[] P0_P1_INT_ARRAY = new int[]{3, 4, 6, 8};

    @Test
    void set() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocate(POINTS_CARRIER);
            segment.set(POINTS_CARRIER, 0, POINT_ARRAY);
            assertArrayEquals(P0_P1_INT_ARRAY, segment.toArray(JAVA_INT));
        }
    }

    @Test
    void setAt() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocate(POINTS_CARRIER, 2);
            segment.setAtIndex(POINTS_CARRIER, 1, POINT_ARRAY);
            assertArrayEquals(P0_P1_INT_ARRAY, segment.asSlice(POINTS_CARRIER.byteSize()).toArray(JAVA_INT));
        }
    }

    @Test
    void get() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocateFrom(JAVA_INT, P0_P1_INT_ARRAY);
            Point[] points = segment.get(POINTS_CARRIER, 0);
            assertEquals(P0, points[0]);
            assertEquals(P1, points[1]);
        }
    }

    @Test
    void getAtIndex() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocate(POINTS_CARRIER, 2);
            for (int i = 0; i < P0_P1_INT_ARRAY.length; i++) {
                segment.setAtIndex(JAVA_INT, i, P0_P1_INT_ARRAY[i]);
                segment.setAtIndex(JAVA_INT, i + P0_P1_INT_ARRAY.length, P0_P1_INT_ARRAY[i]);
            }
            Point[] points = segment.getAtIndex(POINTS_CARRIER, 1);
            System.out.println("Arrays.toString(points) = " + Arrays.toString(points));
            assertEquals(P0, points[0]);
            assertEquals(P1, points[1]);
        }
    }

    @Test
    void allocateFrom() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocateFrom(POINTS_CARRIER, POINT_ARRAY);
            assertArrayEquals(new int[]{P0.x(), P0.y(), P1.x(), P1.y()}, segment.toArray(JAVA_INT));
        }
    }

    @Test
    void allocateFromArray() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocateFrom(POINTS_CARRIER, POINT_ARRAY, POINT_ARRAY);
            assertArrayEquals(new int[]{
                    P0.x(), P0.y(), P1.x(), P1.y(),
                    P0.x(), P0.y(), P1.x(), P1.y()}, segment.toArray(JAVA_INT));
        }
    }

    @Test
    void toArray() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocateFrom(POINTS_CARRIER, POINT_ARRAY, POINT_ARRAY);
            Point[][] points = segment.toArray(POINTS_CARRIER);
            assertArrayEquals(new Point[]{P0, P1}, points[0]);
            assertArrayEquals(new Point[]{P0, P1}, points[1]);
        }
    }

    @Test
    void stream() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocateFrom(POINTS_CARRIER, POINT_ARRAY, POINT_ARRAY);
            Stream<Point[]> stream = segment.elements(POINTS_CARRIER);
            assertArrayEquals(new Point[][]{{P0, P1}, {P0, P1}}, stream.toArray());
        }
    }

    @Test
    void getter() {
        MethodHandle getter = POINTS_CARRIER.getter();
        assertEquals(Point[].class, getter.type().returnType());
        assertEquals(MemorySegment.class, getter.type().parameterType(0));
        assertEquals(long.class, getter.type().parameterType(1));
        assertEquals(2, getter.type().parameterCount());

        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocateFrom(POINTS_CARRIER, POINT_ARRAY, POINT_ARRAY);
            try {
                Point[] o0 = (Point[]) getter.invokeExact(segment, 0L);
                assertArrayEquals(POINT_ARRAY, o0);
                Point[] o1 = (Point[]) getter.invokeExact(segment, POINTS_CARRIER.byteSize());
                assertArrayEquals(POINT_ARRAY, o1);
            } catch (Throwable t) {
                throw new AssertionError(t);
            }
        }

    }

    @Test
    void setter() {
        MethodHandle setter = POINTS_CARRIER.setter();
        assertEquals(void.class, setter.type().returnType());
        assertEquals(MemorySegment.class, setter.type().parameterType(0));
        assertEquals(long.class, setter.type().parameterType(1));
        assertEquals(Point[].class, setter.type().parameterType(2));
        assertEquals(3, setter.type().parameterCount());

        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocate(POINTS_CARRIER, 2);
            try {
                setter.invokeExact(segment, 0L, POINT_ARRAY);
                setter.invokeExact(segment, POINTS_CARRIER.byteSize(), POINT_ARRAY);
                assertArrayEquals(new int[]{
                        P0.x(), P0.y(), P1.x(), P1.y(),
                        P0.x(), P0.y(), P1.x(), P1.y()}, segment.toArray(JAVA_INT));
            } catch (Throwable t) {
                throw new AssertionError(t);
            }
        }
    }

}
