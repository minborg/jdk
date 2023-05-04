/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.lazy.LazyArray;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/*
 * @test
 * @summary Test the API
 * @enablePreview
 * @run junit LazyArrayApiTest
 */
public class LazyArrayApiTest {

    private static final int[] PRIMITIVE_INT_ARRAY = new int[]{1,2};
    private static final Integer[] INTEGER_ARRAY = new Integer[]{1,2};

    @Test
    void testArrayFactories() {
        LazyArray<Integer> a = LazyArray.of(10, i -> i);

        LazyArray<Integer> b = LazyArray.of(int.class, 10, i -> i);
        assertTrue("IntLazyArray".equals(b.getClass().getSimpleName()));
        assertEquals(5, b.get(5));

        LazyArray<Integer> pa = LazyArray.of(INTEGER_ARRAY);
        LazyArray<Integer> pb = LazyArray.of(1, 2);
        assertThrows(IllegalArgumentException.class, () -> {
            // Fails because teh component type is not correct (Integer.class != int.class)
            LazyArray<Integer> pc = LazyArray.of(Integer.class, PRIMITIVE_INT_ARRAY);
        });
        LazyArray<Integer> pd = LazyArray.of(int.class, PRIMITIVE_INT_ARRAY);

        record Point(int x, int y){}

        LazyArray<Point> pe = LazyArray.of(Point.class, new Point[0]);
    }

}
