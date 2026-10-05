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

import java.lang.invoke.MethodHandles;
import java.lang.util.Lazy;
import java.util.function.IntSupplier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LazyDemo {

    static class Point {
        private interface ToDoubleFunctionPoint {
            double applyAsDouble(Point point);
        }

        private static final ToDoubleFunctionPoint LAZY_RADIUS =
                Lazy.ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, MethodHandles.lookup(),
                        ToDoubleFunctionPoint.class, "radius",
                        p -> Math.hypot(p.x, p.y));

        private static final ToDoubleFunctionPoint LAZY_PHI =
                Lazy.ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, MethodHandles.lookup(),
                        ToDoubleFunctionPoint.class, "phi",
                        p -> Math.atan2(p.y, p.x));

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

    }

    static class MeaningOfLife {

        private MeaningOfLife() { throw new RuntimeException(); }

        private static final IntSupplier LAZY_MEANING_OF_LIFE =
                Lazy.ofLocationUpdater(Lazy.UpdaterMode.PUBLISH_ONCE, MethodHandles.lookup(),
                        IntSupplier.class,
                        MeaningOfLife.class.getName() + ".meaningOfLife",
                        () -> 42);
        private static int meaningOfLife;

        public static int get() {
            return LAZY_MEANING_OF_LIFE.getAsInt();
        }

    }

    @Test
    void pointProperties() {
        Point point = new Point(3, 4);
        assertEquals(5.0, point.radius(point));
        assertEquals(Math.atan2(4, 3), point.phi(point));
    }

    @Test
    void meaningOfLife() {
        assertEquals(42, MeaningOfLife.get());
    }

}
