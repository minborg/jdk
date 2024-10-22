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
import org.junit.jupiter.api.Assertions;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.Assert.*;

public class TestStructCarrierLayout {

    record Point(int x, int y){}

    private static final StructLayout POINT = MemoryLayout.structLayout(
        JAVA_INT,
        JAVA_INT
    );

    private static final Function<MemorySegment, Point> UNMARSHALLER =
            s -> new Point(s.get(JAVA_INT, 0), s.get(JAVA_INT, 4));

    private static final BiConsumer<MemorySegment, Point> MARSHALLER = (s, p) -> {
        s.set(JAVA_INT, 0, p.x());
        s.set(JAVA_INT, 0, p.y());
    };

    private static final StructLayout.OfCarrier<Point> POINT_CARRIER =
            POINT.withCarrier(Point.class, UNMARSHALLER, MARSHALLER);

    @Test
    public void basicTest() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocate(POINT_CARRIER, 2);
            segment.set(POINT_CARRIER, 0, new Point(3, 4));
            segment.setAtIndex(POINT_CARRIER, 1, new Point(6, 8));
            assertArrayEquals(new int[]{3, 4, 6, 8}, segment.toArray(JAVA_INT));
        }
    }

}
