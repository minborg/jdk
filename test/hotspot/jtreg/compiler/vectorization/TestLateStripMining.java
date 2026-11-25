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
 * @summary Strip mine a counted loop once its inner loop is fully unrolled
 * @requires vm.compiler2.enabled
 * @library /test/lib /
 * @run driver compiler.vectorization.TestLateStripMining
 */

package compiler.vectorization;

import java.util.SplittableRandom;

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Run;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;

public class TestLateStripMining {
    private static final int LENGTH = 4096;
    private static final int[] INPUT = new int[LENGTH];
    private static final int[] OUTPUT = new int[LENGTH];

    static {
        var random = new SplittableRandom(892428);
        for (int i = 0; i < LENGTH; i++) {
            INPUT[i] = random.nextInt();
        }
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:-UseOnStackReplacement", "-XX:+UseCountedLoopSafepoints",
                                   "-XX:LoopStripMiningIter=16");
    }

    @Test
    @IR(applyIf = {"UseSuperWord", "true"},
        applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.OUTER_STRIP_MINED_LOOP, "> 0", IRNode.LOAD_VECTOR_I, "> 0", IRNode.STORE_VECTOR, "> 0"})
    public static void nestedLoop() {
        for (int i = 0; i < LENGTH; i++) {
            int result = INPUT[i];
            for (int j = 0; j < 3; j++) {
                result = (result << 1) ^ (result >>> 2);
            }
            OUTPUT[i] = result;
        }
    }

    @Run(test = "nestedLoop")
    public static void run() {
        nestedLoop();
        for (int i = 0; i < LENGTH; i++) {
            int expected = step(step(step(INPUT[i])));
            if (OUTPUT[i] != expected) {
                throw new AssertionError("Incorrect output at " + i);
            }
        }
    }

    private static int step(int value) {
        return (value << 1) ^ (value >>> 2);
    }
}
