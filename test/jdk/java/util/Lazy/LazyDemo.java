/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Demo
 * @enablePreview
 * @run junit LazyDemo
 */

import org.junit.jupiter.api.Test;

import java.lang.invoke.LazyUpdaters;
import java.lang.invoke.MethodHandles;
import java.util.Lazy;
import java.util.function.IntSupplier;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

public class LazyDemo {

    static class Point {
        // Runtime reifiable types
        private interface ToDoubleFunctionPoint extends ToDoubleFunction<Point> {}

        private static final ToDoubleFunctionPoint LAZY_RADIUS =
                LazyUpdaters.ofField(MethodHandles.lookup(), ToDoubleFunctionPoint.class, p -> p.radius, p -> Math.hypot(p.x, p.y));

        private static final ToDoubleFunctionPoint LAZY_PHI =
                Lazy.ofField(MethodHandles.lookup(), ToDoubleFunctionPoint.class, p -> p.phi, p -> Math.atan2(p.y, p.x));

        final double x, y;

        // Lazily computed fields
        double radius;
        double phi;

        public Point(double x, double y) { this.x = x; this.y = y; }
        public double x() { return x; }
        public double y() { return y; }

        public double radius(Point p) {
            return LAZY_RADIUS.applyAsDouble(p);
        }

        public double phi(Point p) {
            return LAZY_PHI.applyAsDouble(p);
        }

        ToDoubleFunctionPoint radiusExtractor() {
            return p -> p.radius;
        }

    }

    static class MeaningOfLife {

        private MeaningOfLife() { throw new RuntimeException(); }

        private static final IntSupplier LAZY_MEANING_OF_LIFE =
                LazyUpdaters.ofField(MethodHandles.lookup(), IntSupplier.class, () -> MeaningOfLife.meaningOfLife, () -> 42);
        private static int meaningOfLife;

        public static int get() {
            return LAZY_MEANING_OF_LIFE.getAsInt();
        }

    }

    @Test
    void meaningOfLife() {
        assertEquals(42, MeaningOfLife.get());
    }

}
