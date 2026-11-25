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
 * @summary Exercise bit recipes in packed sample models and pixel conversions
 * @modules java.desktop/sun.awt.image
 * @run main PackedSampleModelBitRecipesTest
 */

import java.awt.image.DataBuffer;
import java.awt.image.MultiPixelPackedSampleModel;
import java.awt.image.SinglePixelPackedSampleModel;
import java.util.Arrays;

import sun.awt.image.PixelConverter;

public class PackedSampleModelBitRecipesTest {
    public static void main(String[] args) {
        testSinglePixel();
        testMultiPixel();
        testRgb565();
        testXbgr();
        testFullWidthPixel();
    }

    private static void testSinglePixel() {
        int[] masks = {0xff, 0xf0, 0, 0xff00_0000};
        var model = new SinglePixelPackedSampleModel(DataBuffer.TYPE_INT, 2, 2, 3, masks);
        DataBuffer data = model.createDataBuffer();
        for (int i = 0; i < data.getSize(); i++) {
            data.setElem(i, 0x1234_5678);
        }
        checkArray("band masks", masks, model.getBitMasks());
        checkArray("band widths", new int[] {8, 4, 0, 8}, model.getSampleSize());
        checkArray("band offsets", new int[] {0, 4, 0, 24}, model.getBitOffsets());

        model.setPixel(0, 0, new int[] {0xb3, 5, -1, 0x1e7}, data);
        check("overlapping fields and unallocated bits", 0xe734_5653, data.getElem(0));
        checkArray("single pixel", new int[] {0x53, 5, 0, 0xe7},
                   model.getPixel(0, 0, (int[]) null, data));
        model.setSample(0, 0, 2, -1, data);
        check("writing empty band", 0xe734_5653, data.getElem(0));

        model.setPixels(0, 0, 2, 2, new int[] {
            0x12, 0x3, 0x99, 0x81,  0x34, 0x5, 0x99, 0x82,
            0x56, 0x7, 0x99, 0x83,  0x78, 0x19, 0x99, 0x184
        }, data);
        checkArray("bulk pixels", new int[] {
            0x32, 0x3, 0, 0x81,  0x54, 0x5, 0, 0x82,
            0x76, 0x7, 0, 0x83,  0x98, 0x9, 0, 0x84
        }, model.getPixels(0, 0, 2, 2, (int[]) null, data));
        model.setSamples(0, 0, 2, 2, 1, new int[] {4, 6, 8, 0x1a}, data);
        checkArray("bulk samples in overlapping band", new int[] {0x42, 0x64, 0x86, 0xa8},
                   model.getSamples(0, 0, 2, 2, 0, (int[]) null, data));
        check("sample in sign-bit field", 0x84, model.getSample(1, 1, 3, data));
        check("first packed element", 0x8134_5642, data.getElem(0));
        check("last packed element", 0x8434_56a8, data.getElem(4));
        check("scanline padding", 0x1234_5678, data.getElem(2));

        var byteModel = new SinglePixelPackedSampleModel(DataBuffer.TYPE_BYTE, 1, 1,
                                                         new int[] {0x1f0, 0x100});
        checkArray("masks constrained to carrier width", new int[] {0xf0, 0},
                   byteModel.getBitMasks());
    }

    private static void testMultiPixel() {
        var model = new MultiPixelPackedSampleModel(DataBuffer.TYPE_BYTE, 5, 2, 2, 3, 2);
        DataBuffer data = model.createDataBuffer();
        int[] initial = {0xc0, 0x0f, 0x5a, 0x80, 0x0a, 0x5b};
        for (int i = 0; i < initial.length; i++) {
            data.setElem(i, initial[i]);
        }

        model.setPixels(0, 0, 5, 2, new int[] {1, 2, 7, 0, 5, 3, 0, 1, 2, 3}, data);
        checkArray("pixels across carriers", new int[] {1, 2, 3, 0, 1, 3, 0, 1, 2, 3},
                   model.getPixels(0, 0, 5, 2, (int[]) null, data));
        check("first carrier", 0xdb, data.getElem(0));
        check("second carrier", 0x1f, data.getElem(1));
        check("first scanline padding", 0x5a, data.getElem(2));
        check("second scanline padding", 0x5b, data.getElem(5));
        byte[] elements = (byte[]) model.getDataElements(2, 0, null, data);
        check("pixel data element", 3, elements[0]);
        model.setDataElements(4, 0, new byte[] {-1}, data);
        check("replacement from data element", 3, model.getSample(4, 0, 0, data));

        model.setSamples(2, 0, 2, 2, 0, new int[] {0, 3, 2, 1}, data);
        checkArray("sample rectangle across carriers", new int[] {0, 3, 2, 1},
                   model.getSamples(2, 0, 2, 2, 0, (int[]) null, data));
        check("prefix bits preserved", 0xd8, data.getElem(0));
        check("suffix bits preserved", 0xff, data.getElem(1));

        var shortModel = new MultiPixelPackedSampleModel(DataBuffer.TYPE_USHORT, 3, 1, 8);
        DataBuffer shortData = shortModel.createDataBuffer();
        shortModel.setPixels(0, 0, 3, 1, new int[] {0x12, 0x80, 0xff}, shortData);
        check("short carrier", 0x1280, shortData.getElem(0));
        checkArray("samples in short carriers", new int[] {0x12, 0x80, 0xff},
                   shortModel.getPixels(0, 0, 3, 1, (int[]) null, shortData));
    }

    private static void testFullWidthPixel() {
        var model = new MultiPixelPackedSampleModel(DataBuffer.TYPE_INT, 2, 2, 32, 3, 32);
        DataBuffer data = model.createDataBuffer();
        data.setElem(1, 0x8765_4321);
        check("full-width sample", 0x8765_4321, model.getSample(0, 0, 0, data));
        model.setSample(1, 0, 0, Integer.MIN_VALUE, data);
        check("full-width replacement", Integer.MIN_VALUE, data.getElem(2));
        model.setDataElements(0, 1, new int[] {-1}, data);
        check("full-width data element", -1, ((int[]) model.getDataElements(0, 1, null, data))[0]);
        model.setPixel(1, 1, new int[] {0x0123_4567}, data);
        checkArray("full-width pixels", new int[] {0x8765_4321, Integer.MIN_VALUE, -1, 0x0123_4567},
                   model.getPixels(0, 0, 2, 2, (int[]) null, data));
    }

    private static void testRgb565() {
        PixelConverter converter = PixelConverter.Ushort565Rgb.instance;
        for (int pixel = 0; pixel <= 0xffff; pixel++) {
            int r = pixel >>> 11;
            int g = (pixel >>> 5) & 0x3f;
            int b = pixel & 0x1f;
            int expectedRgb = 0xff00_0000 | ((8 * r + r / 4) << 16)
                                        | ((4 * g + g / 16) << 8)
                                        | (8 * b + b / 4);
            int rgb = converter.pixelToRgb(pixel, null);
            check("RGB565 expansion", expectedRgb, rgb);
            check("RGB565 round trip", pixel, converter.rgbToPixel(rgb, null));

            int argb = pixel * 0x9e37_79b9;
            int expected = ((argb >>> 8) & 0xf800)
                         | ((argb >>> 5) & 0x07e0)
                         | ((argb >>> 3) & 0x001f);
            check("RGB565 conversion", expected, converter.rgbToPixel(argb, null));
        }
    }

    private static void testXbgr() {
        PixelConverter converter = PixelConverter.Xbgr.instance;
        int pixel = converter.rgbToPixel(0x8012_34FE, null);
        check("XBGR packing drops alpha", 0x00FE_3412, pixel);
        check("XBGR unpacking ignores the padding byte",
              0xFF12_34FE, converter.pixelToRgb(0x7F00_0000 | pixel, null));
        check("XBGR black unpacks as opaque", 0xFF00_0000,
              converter.pixelToRgb(0x8000_0000, null));
    }

    private static void check(String description, int expected, int actual) {
        if (actual != expected) {
            throw new AssertionError("%s: expected 0x%08x, got 0x%08x"
                                     .formatted(description, expected, actual));
        }
    }

    private static void checkArray(String description, int[] expected, int[] actual) {
        if (!Arrays.equals(actual, expected)) {
            throw new AssertionError("%s: expected %s, got %s"
                                     .formatted(description, Arrays.toString(expected),
                                                Arrays.toString(actual)));
        }
    }
}
