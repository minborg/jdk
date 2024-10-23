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
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestUnionCarrierLayout
 */

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.CompositeLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.UnionLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.stream.Stream;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.*;

final class TestUnionCarrierLayout {

    public record Coordinate(int c){}

    private static final UnionLayout POINT = MemoryLayout.unionLayout(
        JAVA_INT,
        JAVA_INT
    );

    private static Coordinate unmarshal(MemorySegment s, long o) {
        return new Coordinate(s.get(JAVA_INT, o));
    }

    private static void marshal(MemorySegment s, long o, Coordinate v) {
        s.set(JAVA_INT, o, v.c());
    }

    private static MethodHandle getter;
    private static MethodHandle setter;

    static {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            getter = lookup.findStatic(TestUnionCarrierLayout.class, "unmarshal",
                    MethodType.methodType(Coordinate.class, MemorySegment.class, long.class));
            setter = lookup.findStatic(TestUnionCarrierLayout.class, "marshal",
                    MethodType.methodType(void.class, MemorySegment.class, long.class, Coordinate.class));
        } catch (ReflectiveOperationException e) {
            fail(e);
        }
    }

    private static final CompositeLayout.OfClass<Coordinate> COORDINATE_CARRIER =
            POINT.bind(Coordinate.class, getter, setter);

    private static final Coordinate C0 = new Coordinate(3);
    private static final Coordinate C1 = new Coordinate(6);
    private static final int[] C0_C1_INT_ARRAY = new int[]{3, 6};

    @Test
    void set() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocate(COORDINATE_CARRIER, 2);
            segment.set(COORDINATE_CARRIER, 0, C0);
            segment.set(COORDINATE_CARRIER, 4, C1);
            assertArrayEquals(C0_C1_INT_ARRAY, segment.toArray(JAVA_INT));
        }
    }

    @Test
    void setAt() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocate(COORDINATE_CARRIER, 2);
            segment.setAtIndex(COORDINATE_CARRIER, 0, C0);
            segment.setAtIndex(COORDINATE_CARRIER, 1, C1);
            assertArrayEquals(C0_C1_INT_ARRAY, segment.toArray(JAVA_INT));
        }
    }

    @Test
    void get() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocateFrom(JAVA_INT, C0_C1_INT_ARRAY);
            Coordinate p0 = segment.get(COORDINATE_CARRIER, 0);
            Coordinate p1 = segment.get(COORDINATE_CARRIER, 4);
            assertEquals(C0, p0);
            assertEquals(C1, p1);
        }
    }

    @Test
    void getAtIndex() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocateFrom(JAVA_INT, C0_C1_INT_ARRAY);
            Coordinate p0 = segment.getAtIndex(COORDINATE_CARRIER, 0);
            Coordinate p1 = segment.getAtIndex(COORDINATE_CARRIER, 1);
            assertEquals(C0, p0);
            assertEquals(C1, p1);
        }
    }

    @Test
    void allocateFrom() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocateFrom(COORDINATE_CARRIER, C0);
            assertArrayEquals(new int[]{C0.c()}, segment.toArray(JAVA_INT));
        }
    }

    @Test
    void allocateFromArray() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocateFrom(COORDINATE_CARRIER, C0, C1);
            assertArrayEquals(C0_C1_INT_ARRAY, segment.toArray(JAVA_INT));
        }
    }


    @Test
    void toArray() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocateFrom(COORDINATE_CARRIER, C0, C1);
            Coordinate[] points = segment.toArray(COORDINATE_CARRIER);
            assertArrayEquals(new Coordinate[]{C0, C1}, points);
        }
    }

    @Test
    void stream() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocateFrom(COORDINATE_CARRIER, C0, C1);
            Stream<Coordinate> stream = segment.elements(COORDINATE_CARRIER);
            assertArrayEquals(new Coordinate[]{C0, C1}, stream.toArray());
        }
    }

    // Todo: Improve this test
    @Test
    void spliterator() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocateFrom(COORDINATE_CARRIER, C0, C1);
            Spliterator<Coordinate> spliterator = segment.spliterator(COORDINATE_CARRIER);
            Set<Integer> characteristics = Set.of(Spliterator.SIZED, Spliterator.SUBSIZED, Spliterator.IMMUTABLE, Spliterator.NONNULL, Spliterator.ORDERED);
            assertEquals(characteristics.stream().mapToInt(i -> i).sum(), spliterator.characteristics());
            assertEquals(2, spliterator.estimateSize());
            assertEquals(2, spliterator.getExactSizeIfKnown());
            assertThrows(IllegalStateException.class, spliterator::getComparator);
            for (int characteristic: characteristics) {
                assertTrue(spliterator.hasCharacteristics(characteristic));
            }
            List<Coordinate> points = new ArrayList<>();
            spliterator.forEachRemaining(points::add);
            assertEquals(List.of(C0, C1), points);;
        }
    }



}
