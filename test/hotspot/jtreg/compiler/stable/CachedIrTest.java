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
 * @summary Check cached constant folding
 * @library /test/lib /
 * @enablePreview
 * @run main ${test.main.class}
 */

package compiler.stable;

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;

import java.util.Set;

public class CachedIrTest {

    public static void main(String[] args) {
        new TestFramework()
                .addTestClassesToBootClassPath()
                .addFlags(
                        "--enable-preview",
                        "-XX:+UnlockExperimentalVMOptions")
                .addCrossProductScenarios(
                        Set.of("-XX:+TieredCompilation", "-XX:-TieredCompilation"))
                .setDefaultWarmup(5000)
                .start();
    }


    record Point(int x,  int y) {
        public cached float distance() {
            return (float) Math.sqrt(x * x + y * y);
        }

        public static cached Point unit() {
            return new Point(1, 1);
        }
    }

    private static final Point POINT = new Point(3, 4);

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static float foldCached() {
        // Access should be folded.
        // No barriers expected for a folded access (as opposed to a non-folded).
        return POINT.distance();
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static Point foldCachedStatic() {
        // Access should be folded.
        // No barriers expected for a folded access (as opposed to a non-folded).
        return Point.unit();
    }

}
