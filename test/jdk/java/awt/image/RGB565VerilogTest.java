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
 * @summary Round-trip the RGB565 packing and widening expressions used by PixelConverter
 * @modules java.desktop/sun.awt.image:+open
 * @run main RGB565VerilogTest
 */

import sun.awt.image.PixelConverter;

public class RGB565VerilogTest {
    private static IntBits recipe(String name) throws ReflectiveOperationException {
        var field = PixelConverter.Ushort565Rgb.class.getDeclaredField(name);
        field.setAccessible(true);
        return (IntBits) field.get(null);
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        var converter = PixelConverter.Ushort565Rgb.instance;
        var pack = IntBits.Shuffle.fromVerilogExpression(recipe("RGB565").toVerilogExpression("compress", "argb"));
        var widen = IntBits.Shuffle.fromVerilogExpression(recipe("WIDEN").toVerilogExpression("rearrange", "pixel"));

        // Visit every RGB triple, varying the alpha byte as well.
        for (int rgb = 0; rgb < (1 << 24); rgb++) {
            int argb = rgb | (rgb << 24);
            if (pack.rearrange(argb) != converter.rgbToPixel(argb, null)) {
                throw new AssertionError("Packing differs for ARGB %08x".formatted(argb));
            }
        }
        for (int alpha = 0; alpha < 256; alpha++) {
            int argb = alpha << 24;
            if (pack.rearrange(argb) != converter.rgbToPixel(argb, null)) {
                throw new AssertionError("Packing differs for alpha %02x".formatted(alpha));
            }
        }
        for (int pixel = 0; pixel <= 0xffff; pixel++) {
            int argb = widen.rearrange(pixel);
            if (argb != converter.pixelToRgb(pixel, null)
                    || argb != widen.rearrange(0xffff_0000 | pixel)
                    || pack.rearrange(argb) != pixel) {
                throw new AssertionError("Widening or repacking differs for RGB565 %04x".formatted(pixel));
            }
        }
    }
}
