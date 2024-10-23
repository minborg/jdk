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
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestStructCarrierLayout
 */

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.CompositeLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.*;

final class TestStructCarrierLayout {

    public record Point(int x, int y){}

    private static final StructLayout POINT = MemoryLayout.structLayout(
        JAVA_INT,
        JAVA_INT
    );

    // We only allow records in the first iteration.

    private static Point unmarshal(MemorySegment s, long o) {
        return new Point(s.get(JAVA_INT, o), s.get(JAVA_INT, o+4));
    }

    private static void marshal(MemorySegment s, long o, Point v) {
        s.set(JAVA_INT, o, v.x());
        s.set(JAVA_INT, o + 4, v.y());
    }

    private static MethodHandle getter;
    private static MethodHandle setter;

    static {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            getter = lookup.findStatic(TestStructCarrierLayout.class, "unmarshal",
                    MethodType.methodType(Point.class, MemorySegment.class, long.class));
            setter = lookup.findStatic(TestStructCarrierLayout.class, "marshal",
                    MethodType.methodType(void.class, MemorySegment.class, long.class, Point.class));
        } catch (ReflectiveOperationException e) {
            fail(e);
        }
    }

    private static final CompositeLayout.OfClass<Point> POINT_CARRIER =
            POINT.bind(Point.class, getter, setter);

    private static final Point P0 = new Point(3, 4);
    private static final Point P1 = new Point(6, 8);
    private static final int[] P0_P1_INT_ARRAY = new int[]{3, 4, 6, 8};

    @Test
    void set() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocate(POINT_CARRIER, 2);
            segment.set(POINT_CARRIER, 0, P0);
            segment.set(POINT_CARRIER, 8, P1);
            assertArrayEquals(P0_P1_INT_ARRAY, segment.toArray(JAVA_INT));
        }
    }

    @Test
    void setAt() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocate(POINT_CARRIER, 2);
            segment.setAtIndex(POINT_CARRIER, 0, P0);
            segment.setAtIndex(POINT_CARRIER, 1, P1);
            assertArrayEquals(P0_P1_INT_ARRAY, segment.toArray(JAVA_INT));
        }
    }

    @Test
    void get() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocateFrom(JAVA_INT, P0_P1_INT_ARRAY);
            Point p0 = segment.get(POINT_CARRIER, 0);
            Point p1 = segment.get(POINT_CARRIER, 8);
            assertEquals(P0, p0);
            assertEquals(P1, p1);
        }
    }

    @Test
    void getAtIndex() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocateFrom(JAVA_INT, P0_P1_INT_ARRAY);
            Point p0 = segment.getAtIndex(POINT_CARRIER, 0);
            Point p1 = segment.getAtIndex(POINT_CARRIER, 1);
            assertEquals(P0, p0);
            assertEquals(P1, p1);
        }
    }

    @Test
    void allocateFrom() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocateFrom(POINT_CARRIER, P0);
            assertArrayEquals(new int[]{P0.x(), P0.y()}, segment.toArray(JAVA_INT));
        }
    }

    @Test
    void allocateFromArray() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocateFrom(POINT_CARRIER, P0, P1);
            assertArrayEquals(P0_P1_INT_ARRAY, segment.toArray(JAVA_INT));
        }
    }

    @Test
    void toArray() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocateFrom(POINT_CARRIER, P0, P1);
            Point[] points = segment.toArray(POINT_CARRIER);
            assertArrayEquals(new Point[]{P0, P1}, points);
        }
    }

    @Test
    void getter() {
        MethodHandle getter = POINT_CARRIER.getter();
        assertEquals(Point.class, getter.type().returnType());
        assertEquals(MemorySegment.class, getter.type().parameterType(0));
        assertEquals(long.class, getter.type().parameterType(1));
        assertEquals(2, getter.type().parameterCount());

        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocateFrom(JAVA_INT, P0_P1_INT_ARRAY);
            try {
                Point o0 = (Point) getter.invokeExact(segment, 0L);
                assertEquals(P0, o0);
                Point o1 = (Point) getter.invokeExact(segment, 8L);
                assertEquals(P1, o1);
            } catch (Throwable t) {
                throw new AssertionError(t);
            }
        }

    }

    @Test
    void setter() {
        MethodHandle setter = POINT_CARRIER.setter();
        assertEquals(void.class, setter.type().returnType());
        assertEquals(MemorySegment.class, setter.type().parameterType(0));
        assertEquals(long.class, setter.type().parameterType(1));
        assertEquals(Point.class, setter.type().parameterType(2));
        assertEquals(3, setter.type().parameterCount());

        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocate(POINT_CARRIER, 2);
            try {
                setter.invokeExact(segment, 0L, P0);
                setter.invokeExact(segment, 8L, P1);
                assertArrayEquals(P0_P1_INT_ARRAY, segment.toArray(JAVA_INT));
            } catch (Throwable t) {
                throw new AssertionError(t);
            }
        }
    }

}
