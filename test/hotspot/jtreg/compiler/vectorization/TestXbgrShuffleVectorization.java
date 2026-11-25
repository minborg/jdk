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
 * @summary Test vectorization of XBGR shuffles in regular C2 compilations
 * @requires vm.compiler2.enabled
 * @requires os.simpleArch == "aarch64"
 * @library /test/lib /
 * @modules java.desktop/sun.awt.image
 * @run driver compiler.vectorization.TestXbgrShuffleVectorization
 */

package compiler.vectorization;

import java.util.SplittableRandom;

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Run;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;
import sun.awt.image.PixelConverter;

public class TestXbgrShuffleVectorization {
    private static final PixelConverter XBGR = PixelConverter.Xbgr.instance;
    private static final int COUNT = 4096;

    private int[] inputs = new int[COUNT];
    private int[] outputs = new int[COUNT];

    public TestXbgrShuffleVectorization() {
        var random = new SplittableRandom(565);
        for (int i = 0; i < COUNT; i++) {
            inputs[i] = random.nextInt();
        }
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:-UseOnStackReplacement", "-XX:UseSVE=0",
                "--add-exports=java.desktop/sun.awt.image=ALL-UNNAMED");
    }

    @Test
    @IR(applyIf = {"UseSuperWord", "true"},
        counts = {IRNode.LOAD_VECTOR_I, "> 0", IRNode.STORE_VECTOR, "> 0"})
    public int[] xbgrPackArray() {
        int[] src = inputs;
        int[] dst = outputs;
        for (int i = 0; i < COUNT; i++) {
            dst[i] = XBGR.rgbToPixel(src[i], null);
        }
        return dst;
    }

    @Test
    @IR(applyIf = {"UseSuperWord", "true"},
        counts = {IRNode.LOAD_VECTOR_I, "> 0", IRNode.STORE_VECTOR, "> 0"})
    public int[] xbgrUnpackArray() {
        int[] src = inputs;
        int[] dst = outputs;
        for (int i = 0; i < COUNT; i++) {
            dst[i] = XBGR.pixelToRgb(src[i], null);
        }
        return dst;
    }

    @Run(test = {"xbgrPackArray", "xbgrUnpackArray"})
    public void run() {
        verify(xbgrPackArray(), 0);
        verify(xbgrUnpackArray(), 0xff000000);
    }

    private void verify(int[] actual, int alpha) {
        for (int i = 0; i < COUNT; i++) {
            int source = inputs[i];
            int expected = alpha | ((source & 0xff) << 16) |
                           (source & 0xff00) | ((source >>> 16) & 0xff);
            if (actual[i] != expected) {
                throw new AssertionError("Index " + i + " expected " +
                                         Integer.toHexString(expected) + " got " +
                                         Integer.toHexString(actual[i]));
            }
        }
    }
}
