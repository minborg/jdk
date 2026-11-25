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
 * @summary Test C2 lowering of bit compression and expansion with constant masks on AArch64
 * @requires os.arch == "aarch64" & vm.compiler2.enabled
 * @library /test/lib /
 * @run driver compiler.intrinsics.TestBitShuffleConstantMask
 */

package compiler.intrinsics;

import compiler.lib.ir_framework.ForceInline;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Run;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;

public class TestBitShuffleConstantMask {
    private static final int RGB565_MASK = 0x00f8_fcf8;
    private static final int HIGH_RUN_MASK = 0xffff_fffe;
    private static final int LOW_RUN_MASK = 0x7fff_ffff;
    private static final int TWO_RUNS_MASK = 0x8000_000f;
    private static final int THREE_RUNS_MASK = 0x8000_0f03;
    private static final int FOUR_RUNS_MASK = 0x8008_0101;

    private static final long HIGH_RUN_MASK_LONG = 0xffff_ffff_ffff_fffeL;
    private static final long TWO_RUNS_MASK_LONG = 0x8000_0000_0000_000fL;
    private static final long THREE_RUNS_MASK_LONG = 0x0f00_00fc_0000_f800L;
    private static final long FOUR_RUNS_MASK_LONG = 0x8000_0008_0000_0101L;
    private static final long EIGHT_RUNS_MASK_LONG = 0x0f0f_0f0f_0f0f_0f0fL; // UUID.hex8
    private static final long NINE_RUNS_MASK_LONG = 0x8101_0101_0101_0101L;

    private static final int[] EDGE_VALUES = {
        0, -1, 1, Integer.MIN_VALUE, Integer.MAX_VALUE, 0x5555_5555, 0xaaaa_aaaa, 0x1234_5678
    };

    private static final long[] EDGE_VALUES_LONG = {
        0, -1, 1, Long.MIN_VALUE, Long.MAX_VALUE,
        0x5555_5555_5555_5555L, 0xaaaa_aaaa_aaaa_aaaaL, 0x0123_4567_89ab_cdefL
    };

    private static int random = 0x9e37_79b9;

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:UseSVE=0");
    }

    @Test
    @IR(counts = {IRNode.URSHIFT_I, "3", IRNode.AND_I, "3", IRNode.OR_I, "2"},
        failOn = {IRNode.XOR_I})
    public static int testRgb565(int value) {
        return compress(value, RGB565_MASK);
    }

    @ForceInline
    private static int compress(int value, int mask) {
        return Integer.compress(value, mask);
    }

    @Test
    public static int testNoBits(int value) {
        return Integer.compress(value, 0);
    }

    @Test
    public static int testAllBits(int value) {
        return Integer.compress(value, -1);
    }

    @Test
    public static int testHighRun(int value) {
        return Integer.compress(value, HIGH_RUN_MASK);
    }

    @Test
    public static int testLowRun(int value) {
        return Integer.compress(value, LOW_RUN_MASK);
    }

    @Test
    public static int testTwoRuns(int value) {
        return Integer.compress(value, TWO_RUNS_MASK);
    }

    @Test
    public static int testThreeRuns(int value) {
        return Integer.compress(value, THREE_RUNS_MASK);
    }

    @Test
    public static int testFourRuns(int value) {
        return Integer.compress(value, FOUR_RUNS_MASK);
    }

    @Test
    public static int testVariableMask(int value, int mask) {
        return Integer.compress(value, mask);
    }

    @Run(test = {"testRgb565", "testNoBits", "testAllBits", "testHighRun", "testLowRun",
                 "testTwoRuns", "testThreeRuns", "testFourRuns", "testVariableMask"})
    public static void run() {
        for (int value : EDGE_VALUES) {
            checkCases(value, nextRandom());
            checkCases(nextRandom(), nextRandom());
        }
    }

    @Test
    public static long testLongCompressNoBits(long value) {
        return Long.compress(value, 0);
    }

    @Test
    public static long testLongCompressAllBits(long value) {
        return Long.compress(value, -1);
    }

    @Test
    public static long testLongCompressHighRun(long value) {
        return Long.compress(value, HIGH_RUN_MASK_LONG);
    }

    @Test
    public static long testLongCompressTwoRuns(long value) {
        return Long.compress(value, TWO_RUNS_MASK_LONG);
    }

    @Test
    @IR(counts = {IRNode.URSHIFT_L, "3", IRNode.AND_L, "3", IRNode.OR_L, "2"},
        failOn = {IRNode.XOR_L})
    public static long testLongCompressThreeRuns(long value) {
        return Long.compress(value, THREE_RUNS_MASK_LONG);
    }

    @Test
    public static long testLongCompressFourRuns(long value) {
        return Long.compress(value, FOUR_RUNS_MASK_LONG);
    }

    @Test
    public static long testLongCompressVariableMask(long value, long mask) {
        return Long.compress(value, mask);
    }

    @Run(test = {"testLongCompressNoBits", "testLongCompressAllBits", "testLongCompressHighRun",
                 "testLongCompressTwoRuns", "testLongCompressThreeRuns", "testLongCompressFourRuns",
                 "testLongCompressVariableMask"})
    public static void runLongCompress() {
        for (long value : EDGE_VALUES_LONG) {
            checkLongCompress(value, nextRandomLong());
            checkLongCompress(nextRandomLong(), nextRandomLong());
        }
    }

    @Test
    public static int testIntExpandNoBits(int value) {
        return Integer.expand(value, 0);
    }

    @Test
    public static int testIntExpandAllBits(int value) {
        return Integer.expand(value, -1);
    }

    @Test
    public static int testIntExpandHighRun(int value) {
        return Integer.expand(value, HIGH_RUN_MASK);
    }

    @Test
    public static int testIntExpandTwoRuns(int value) {
        return Integer.expand(value, TWO_RUNS_MASK);
    }

    @Test
    @IR(counts = {IRNode.LSHIFT_I, "3", IRNode.AND_I, "3", IRNode.OR_I, "2"})
    public static int testIntExpandRgb565(int value) {
        return Integer.expand(value, RGB565_MASK);
    }

    @Test
    public static int testIntExpandFourRuns(int value) {
        return Integer.expand(value, FOUR_RUNS_MASK);
    }

    @Test
    public static int testIntExpandVariableMask(int value, int mask) {
        return Integer.expand(value, mask);
    }

    @Run(test = {"testIntExpandNoBits", "testIntExpandAllBits", "testIntExpandHighRun",
                 "testIntExpandTwoRuns", "testIntExpandRgb565", "testIntExpandFourRuns",
                 "testIntExpandVariableMask"})
    public static void runIntExpand() {
        for (int value : EDGE_VALUES) {
            checkIntExpand(value, nextRandom());
            checkIntExpand(nextRandom(), nextRandom());
        }
    }

    @Test
    public static long testLongExpandNoBits(long value) {
        return Long.expand(value, 0);
    }

    @Test
    public static long testLongExpandAllBits(long value) {
        return Long.expand(value, -1);
    }

    @Test
    public static long testLongExpandHighRun(long value) {
        return Long.expand(value, HIGH_RUN_MASK_LONG);
    }

    @Test
    public static long testLongExpandTwoRuns(long value) {
        return Long.expand(value, TWO_RUNS_MASK_LONG);
    }

    @Test
    @IR(counts = {IRNode.LSHIFT_L, "3", IRNode.AND_L, "3", IRNode.OR_L, "2"})
    public static long testLongExpandThreeRuns(long value) {
        return Long.expand(value, THREE_RUNS_MASK_LONG);
    }

    @Test
    public static long testLongExpandNineRuns(long value) {
        return Long.expand(value, NINE_RUNS_MASK_LONG);
    }

    @Test
    public static long testLongExpandEightRuns(long value) {
        return Long.expand(value, EIGHT_RUNS_MASK_LONG);
    }

    @Test
    public static long testLongExpandVariableMask(long value, long mask) {
        return Long.expand(value, mask);
    }

    @Run(test = {"testLongExpandNoBits", "testLongExpandAllBits", "testLongExpandHighRun",
                 "testLongExpandTwoRuns", "testLongExpandThreeRuns", "testLongExpandNineRuns",
                 "testLongExpandEightRuns", "testLongExpandVariableMask"})
    public static void runLongExpand() {
        for (long value : EDGE_VALUES_LONG) {
            checkLongExpand(value, nextRandomLong());
            checkLongExpand(nextRandomLong(), nextRandomLong());
        }
    }

    private static int nextRandom() {
        random = random * 1664525 + 1013904223;
        return random;
    }

    private static long nextRandomLong() {
        return ((long) nextRandom() << Integer.SIZE) | Integer.toUnsignedLong(nextRandom());
    }

    private static void checkCases(int value, int variableMask) {
        checkCompress(value, RGB565_MASK, testRgb565(value));
        checkCompress(value, 0, testNoBits(value));
        checkCompress(value, -1, testAllBits(value));
        checkCompress(value, HIGH_RUN_MASK, testHighRun(value));
        checkCompress(value, LOW_RUN_MASK, testLowRun(value));
        checkCompress(value, TWO_RUNS_MASK, testTwoRuns(value));
        checkCompress(value, THREE_RUNS_MASK, testThreeRuns(value));
        checkCompress(value, FOUR_RUNS_MASK, testFourRuns(value));
        checkCompress(value, variableMask, testVariableMask(value, variableMask));
    }

    private static void checkLongCompress(long value, long variableMask) {
        checkCompress(value, 0, testLongCompressNoBits(value));
        checkCompress(value, -1, testLongCompressAllBits(value));
        checkCompress(value, HIGH_RUN_MASK_LONG, testLongCompressHighRun(value));
        checkCompress(value, TWO_RUNS_MASK_LONG, testLongCompressTwoRuns(value));
        checkCompress(value, THREE_RUNS_MASK_LONG, testLongCompressThreeRuns(value));
        checkCompress(value, FOUR_RUNS_MASK_LONG, testLongCompressFourRuns(value));
        checkCompress(value, variableMask, testLongCompressVariableMask(value, variableMask));
    }

    private static void checkIntExpand(int value, int variableMask) {
        checkExpand(value, 0, testIntExpandNoBits(value));
        checkExpand(value, -1, testIntExpandAllBits(value));
        checkExpand(value, HIGH_RUN_MASK, testIntExpandHighRun(value));
        checkExpand(value, TWO_RUNS_MASK, testIntExpandTwoRuns(value));
        checkExpand(value, RGB565_MASK, testIntExpandRgb565(value));
        checkExpand(value, FOUR_RUNS_MASK, testIntExpandFourRuns(value));
        checkExpand(value, variableMask, testIntExpandVariableMask(value, variableMask));
    }

    private static void checkLongExpand(long value, long variableMask) {
        checkExpand(value, 0, testLongExpandNoBits(value));
        checkExpand(value, -1, testLongExpandAllBits(value));
        checkExpand(value, HIGH_RUN_MASK_LONG, testLongExpandHighRun(value));
        checkExpand(value, TWO_RUNS_MASK_LONG, testLongExpandTwoRuns(value));
        checkExpand(value, THREE_RUNS_MASK_LONG, testLongExpandThreeRuns(value));
        checkExpand(value, NINE_RUNS_MASK_LONG, testLongExpandNineRuns(value));
        checkExpand(value, EIGHT_RUNS_MASK_LONG, testLongExpandEightRuns(value));
        checkExpand(value, variableMask, testLongExpandVariableMask(value, variableMask));
    }

    private static void checkCompress(int value, int mask, int actual) {
        checkCompress(Integer.toUnsignedLong(value), Integer.toUnsignedLong(mask), Integer.toUnsignedLong(actual));
    }

    private static void checkExpand(int value, int mask, int actual) {
        checkExpand(Integer.toUnsignedLong(value), Integer.toUnsignedLong(mask), Integer.toUnsignedLong(actual));
    }

    private static void checkCompress(long value, long mask, long actual) {
        checkReference(value, mask, false, actual);
    }

    private static void checkExpand(long value, long mask, long actual) {
        checkReference(value, mask, true, actual);
    }

    private static void checkReference(long value, long mask, boolean expand, long actual) {
        long expected = 0;
        int packed = 0;
        for (int bit = 0; bit < Long.SIZE; bit++) {
            if (((mask >>> bit) & 1L) != 0) {
                int source = expand ? packed : bit;
                int destination = expand ? bit : packed;
                expected |= ((value >>> source) & 1L) << destination;
                packed++;
            }
        }

        if (actual != expected) {
            throw new AssertionError("%s value=%016x mask=%016x expected=%016x actual=%016x"
                                     .formatted(expand ? "expand" : "compress", value, mask, expected, actual));
        }
    }
}
