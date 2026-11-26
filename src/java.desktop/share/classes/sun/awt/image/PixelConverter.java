/*
 * Copyright (c) 2000, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.awt.image;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;

/**
 * This class provides utilities for converting between the standard
 * rgb colorspace specification and the equivalent value for a pixel
 * of a given surface type.  The class was designed for use by the
 * SurfaceType objects, since the conversion between pixel values
 * and rgb values is inherently tied to the type of surface we are
 * dealing with.  Some conversions cannot be done automatically,
 * however (for example, the AnyInt or AnyDCM surface types), so
 * we require the caller to pass in a ColorModel object so that
 * we can calculate the pixel values in these generic cases as well.
 */
public class PixelConverter {

    /**
     * Default object, used as a fallback for any surface types where
     * we do not know enough about the surface to calculate the
     * conversions directly.  We use the ColorModel object to assist
     * us in these cases.
     */
    public static final PixelConverter instance = new PixelConverter();


    protected int alphaMask = 0;

    protected PixelConverter() {}

    @SuppressWarnings("fallthrough")
    public int rgbToPixel(int rgb, ColorModel cm) {
        Object obj = cm.getDataElements(rgb, null);
        switch (cm.getTransferType()) {
        case DataBuffer.TYPE_BYTE:
            byte[] bytearr = (byte[]) obj;
            int pix = 0;

            switch(bytearr.length) {
            default: // bytearr.length >= 4
                pix = bytearr[3] << 24;
                // FALLSTHROUGH
            case 3:
                pix |= (bytearr[2] & 0xff) << 16;
                // FALLSTHROUGH
            case 2:
                pix |= (bytearr[1] & 0xff) << 8;
                // FALLSTHROUGH
            case 1:
                pix |= (bytearr[0] & 0xff);
            }

            return pix;
        case DataBuffer.TYPE_SHORT:
        case DataBuffer.TYPE_USHORT:
            short[] shortarr = (short[]) obj;

            return (((shortarr.length > 1) ? shortarr[1] << 16 : 0) |
                    shortarr[0] & 0xffff);
        case DataBuffer.TYPE_INT:
            return ((int[]) obj)[0];
        default:
            return rgb;
        }
    }

    public int pixelToRgb(int pixel, ColorModel cm) {
        // REMIND: Not yet implemented
        return pixel;
    }

    public final int getAlphaMask() {
        return alphaMask;
    }


    /**
     * Subclasses of PixelConverter.  These subclasses are
     * specific to surface types where we can definitively
     * calculate the conversions.  Note that some conversions
     * are lossy; that is, we cannot necessarily convert a
     * value and then convert it back and wind up with the
     * original value.  For example, an rgb value  that has
     * an alpha != 1 cannot be converted to an Xrgb pixel
     * without losing the information in the alpha component.
     *
     * The conversion strategies associated with the ThreeByte*
     * and FourByte* surface types swap the components around
     * due to the ordering used when the bytes are stored.  The
     * low order byte of a packed-byte pixel will be the first
     * byte stored and the high order byte will be the last byte
     * stored.  For example, the ThreeByteBgr surface type is
     * associated with an Xrgb conversion object because the
     * three bytes are stored as follows:
     *   pixels[0] = b;    // low order byte of an Xrgb pixel
     *   pixels[1] = g;
     *   pixels[2] = r;    // high order byte of an Xrgb pixel
     */

    public static class Rgbx extends PixelConverter {
        public static final PixelConverter instance = new Rgbx();

        private Rgbx() {}

        public int rgbToPixel(int rgb, ColorModel cm) {
            return (rgb << 8);
        }

        public int pixelToRgb(int pixel, ColorModel cm) {
            return (0xff000000 | (pixel >> 8));
        }
    }
    public static class Xrgb extends PixelConverter {
        public static final PixelConverter instance = new Xrgb();

        private Xrgb() {}

        public int rgbToPixel(int rgb, ColorModel cm) {
            return rgb;
        }

        public int pixelToRgb(int pixel, ColorModel cm) {
            return (0xff000000 | pixel);
        }
    }
    public static class Argb extends PixelConverter {
        public static final PixelConverter instance = new Argb();

        private Argb() {
            alphaMask = 0xff000000;
        }

        public int rgbToPixel(int rgb, ColorModel cm) {
            return rgb;
        }

        public int pixelToRgb(int pixel, ColorModel cm) {
            return pixel;
        }
    }
    public static class Ushort565Rgb extends PixelConverter {
        public static final PixelConverter instance = new Ushort565Rgb();

        // RGB565 stores 5 red, 6 green, and 5 blue bits in the low 16 bits.
        private static final int RED_BITS = 5;
        private static final int GREEN_BITS = 6;
        private static final int BLUE_BITS = 5;

        // Lay out the fields from high to low. Each field ends where the
        // preceding field starts.
        private static final IntBits.Field RED = IntBits.Field.endingAt(Short.SIZE, RED_BITS);
        private static final IntBits.Field GREEN = IntBits.Field.endingAt(RED.offset(), GREEN_BITS);
        private static final IntBits.Field BLUE = IntBits.Field.endingAt(GREEN.offset(), BLUE_BITS);

        // Read 0xAARRGGBB from the top. Skip alpha, then take the high bits
        // of each color byte. Compress packs these into the low 16 bits as RGB565.
        //
        // {16'b0, rgb[23:19], rgb[15:10], rgb[7:3]}
        private static final IntBits.Selection RGB565 = IntBits.Selection.highToLow()
                .skip(Byte.SIZE)
                .takeHigh(Byte.SIZE, RED_BITS)
                .takeHigh(Byte.SIZE, GREEN_BITS)
                .takeHigh(Byte.SIZE, BLUE_BITS)
                .build();

        // Build 0xAARRGGBB from high to low, starting with opaque alpha (0xff).
        // Each color byte starts with the original channel bits. Repeat its
        // high bits to fill the low positions. For example, blue expands
        // from b4 b3 b2 b1 b0 to b4 b3 b2 b1 b0 b4 b3 b2.
        //
        // {8'b11111111, pixel[15:11], pixel[15:13], pixel[10:5], pixel[10:9], pixel[4:0], pixel[4:2]}
        private static final IntBits.Shuffle WIDEN = IntBits.Shuffle.concatHighToLow(
                IntBits.Shuffle.ones(Byte.SIZE),                  // AA
                IntBits.Shuffle.repeatToWidth(RED,   Byte.SIZE),  // RR
                IntBits.Shuffle.repeatToWidth(GREEN, Byte.SIZE),  // GG
                IntBits.Shuffle.repeatToWidth(BLUE,  Byte.SIZE)); // BB

        private Ushort565Rgb() {}

        public int rgbToPixel(int rgb, ColorModel cm) {
            return RGB565.compress(rgb);
        }

        public int pixelToRgb(int pixel, ColorModel cm) {
            return WIDEN.rearrange(pixel);
        }
    }
    public static class Ushort555Rgbx extends PixelConverter {
        public static final PixelConverter instance = new Ushort555Rgbx();

        private Ushort555Rgbx() {}

        public int rgbToPixel(int rgb, ColorModel cm) {
            return (((rgb >> (16 + 3 - 11)) & 0xf800) |
                    ((rgb >> ( 8 + 3 -  6)) & 0x07c0) |
                    ((rgb >> ( 0 + 3 -  1)) & 0x003e));
        }

        public int pixelToRgb(int pixel, ColorModel cm) {
            int r, g, b;
            r = (pixel >> 11) & 0x1f;
            r = (r << 3) | (r >> 2);
            g = (pixel >>  6) & 0x1f;
            g = (g << 3) | (g >> 2);
            b = (pixel >>  1) & 0x1f;
            b = (b << 3) | (b >> 2);
            return (0xff000000 | (r << 16) | (g << 8) | (b));
        }
    }
    public static class Ushort555Rgb extends PixelConverter {
        public static final PixelConverter instance = new Ushort555Rgb();

        private Ushort555Rgb() {}

        public int rgbToPixel(int rgb, ColorModel cm) {
            return (((rgb >> (16 + 3 - 10)) & 0x7c00) |
                    ((rgb >> ( 8 + 3 -  5)) & 0x03e0) |
                    ((rgb >> ( 0 + 3 -  0)) & 0x001f));
        }

        public int pixelToRgb(int pixel, ColorModel cm) {
            int r, g, b;
            r = (pixel >> 10) & 0x1f;
            r = (r << 3) | (r >> 2);
            g = (pixel >>  5) & 0x1f;
            g = (g << 3) | (g >> 2);
            b = (pixel      ) & 0x1f;
            b = (b << 3) | (b >> 2);
            return (0xff000000 | (r << 16) | (g << 8) | (b));
        }
    }
    public static class Ushort4444Argb extends PixelConverter {
        public static final PixelConverter instance = new Ushort4444Argb();

        // Packing maps 0xAaRrGgBb to 0x0000ARGB by keeping each byte's high nibble.
        private static final IntBits.Shuffle PACK = IntBits.Shuffle.fromVerilogExpression(
                "{16'b0, argb[31:28], argb[23:20], argb[15:12], argb[7:4]}");

        // Widening maps 0xARGB to 0xAARRGGBB by repeating each nibble twice.
        private static final IntBits.Shuffle WIDEN = IntBits.Shuffle.fromVerilogExpression(
                "{{2{pixel[15:12]}}, {2{pixel[11:8]}}, {2{pixel[7:4]}}, {2{pixel[3:0]}}}");

        private Ushort4444Argb() {
            alphaMask = 0xf000;
        }

        public int rgbToPixel(int argb, ColorModel cm) {
            return PACK.rearrange(argb);
        }

        public int pixelToRgb(int pixel, ColorModel cm) {
            return WIDEN.rearrange(pixel);
        }
    }
    public static class Xbgr extends PixelConverter {
        public static final PixelConverter instance = new Xbgr();

        private static final IntBits.Shuffle SWAP_RED_BLUE =
                IntBits.Shuffle.ofBytes(2, 1, 0, IntBits.Shuffle.ZERO);

        private Xbgr() {}

        public int rgbToPixel(int rgb, ColorModel cm) {
            return SWAP_RED_BLUE.rearrange(rgb);
        }

        public int pixelToRgb(int pixel, ColorModel cm) {
            return 0xff000000 | SWAP_RED_BLUE.rearrange(pixel);
        }
    }
    public static class Bgrx extends PixelConverter {
        public static final PixelConverter instance = new Bgrx();

        private Bgrx() {}

        public int rgbToPixel(int rgb, ColorModel cm) {
            return ((rgb << 24) |
                    ((rgb & 0xff00) << 8) |
                    ((rgb >> 8) & 0xff00));
        }

        public int pixelToRgb(int pixel, ColorModel cm) {
            return (0xff000000                   |
                    ((pixel & 0xff00) << 8) |
                    ((pixel >> 8) & 0xff00) |
                    (pixel >>> 24));
        }
    }
    public static class Rgba extends PixelConverter {
        public static final PixelConverter instance = new Rgba();

        private Rgba() {
            alphaMask = 0x000000ff;
        }

        public int rgbToPixel(int rgb, ColorModel cm) {
            return ((rgb << 8) | (rgb >>> 24));
        }

        public int pixelToRgb(int pixel, ColorModel cm) {
            return ((pixel << 24) | (pixel >>> 8));
        }
    }
    public static class RgbaPre extends PixelConverter {
        public static final PixelConverter instance = new RgbaPre();

        private RgbaPre() {
            alphaMask = 0x000000ff;
        }

        public int rgbToPixel(int rgb, ColorModel cm) {
            if ((rgb >> 24) == -1) {
                return ((rgb << 8) | (rgb >>> 24));
            }
            int a = rgb >>> 24;
            int r = (rgb >> 16) & 0xff;
            int g = (rgb >>  8) & 0xff;
            int b = (rgb      ) & 0xff;
            int a2 = a + (a >> 7);
            r = (r * a2) >> 8;
            g = (g * a2) >> 8;
            b = (b * a2) >> 8;
            return ((r << 24) | (g << 16) | (b << 8) | (a));
        }

        public int pixelToRgb(int pixel, ColorModel cm) {
            int a = pixel & 0xff;
            if ((a == 0xff) || (a == 0)) {
                return ((pixel >>> 8) | (pixel << 24));
            }
            int r = pixel >>> 24;
            int g = (pixel >> 16) & 0xff;
            int b = (pixel >>  8) & 0xff;
            r = ((r << 8) - r) / a;
            g = ((g << 8) - g) / a;
            b = ((b << 8) - b) / a;
            return ((r << 24) | (g << 16) | (b << 8) | (a));
        }
    }
    public static class ArgbPre extends PixelConverter {
        public static final PixelConverter instance = new ArgbPre();

        private ArgbPre() {
            alphaMask = 0xff000000;
        }

        public int rgbToPixel(int rgb, ColorModel cm) {
            if ((rgb >> 24) == -1) {
                return rgb;
            }
            int a = rgb >>> 24;
            int r = (rgb >> 16) & 0xff;
            int g = (rgb >>  8) & 0xff;
            int b = (rgb      ) & 0xff;
            int a2 = a + (a >> 7);
            r = (r * a2) >> 8;
            g = (g * a2) >> 8;
            b = (b * a2) >> 8;
            return ((a << 24) | (r << 16) | (g << 8) | (b));
        }

        public int pixelToRgb(int pixel, ColorModel cm) {
            int a = pixel >>> 24;
            if ((a == 0xff) || (a == 0)) {
                return pixel;
            }
            int r = (pixel >> 16) & 0xff;
            int g = (pixel >>  8) & 0xff;
            int b = (pixel      ) & 0xff;
            r = ((r << 8) - r) / a;
            g = ((g << 8) - g) / a;
            b = ((b << 8) - b) / a;
            return ((a << 24) | (r << 16) | (g << 8) | (b));
        }
    }
    public static class ArgbBm extends PixelConverter {
        public static final PixelConverter instance = new ArgbBm();

        private ArgbBm() {}

        public int rgbToPixel(int rgb, ColorModel cm) {
            return (rgb | ((rgb >> 31) << 24));
        }

        public int pixelToRgb(int pixel, ColorModel cm) {
            return ((pixel << 7) >> 7);
        }
    }
    public static class ByteGray extends PixelConverter {
        static final double RED_MULT = 0.299;
        static final double GRN_MULT = 0.587;
        static final double BLU_MULT = 0.114;
        public static final PixelConverter instance = new ByteGray();

        private ByteGray() {}

        public int rgbToPixel(int rgb, ColorModel cm) {
            int red = (rgb >> 16) & 0xff;
            int grn = (rgb >>  8) & 0xff;
            int blu = (rgb      ) & 0xff;
            return (int) (red * RED_MULT +
                          grn * GRN_MULT +
                          blu * BLU_MULT +
                          0.5);
        }

        public int pixelToRgb(int pixel, ColorModel cm) {
            return ((((((0xff << 8) | pixel) << 8) | pixel) << 8) | pixel);
        }
    }
    public static class UshortGray extends ByteGray {
        static final double SHORT_MULT = 257.0; // (65535.0 / 255.0);
        static final double USHORT_RED_MULT = RED_MULT * SHORT_MULT;
        static final double USHORT_GRN_MULT = GRN_MULT * SHORT_MULT;
        static final double USHORT_BLU_MULT = BLU_MULT * SHORT_MULT;
        public static final PixelConverter instance = new UshortGray();

        private UshortGray() {}

        public int rgbToPixel(int rgb, ColorModel cm) {
            int red = (rgb >> 16) & 0xff;
            int grn = (rgb >>  8) & 0xff;
            int blu = (rgb      ) & 0xff;
            return (int) (red * USHORT_RED_MULT +
                          grn * USHORT_GRN_MULT +
                          blu * USHORT_BLU_MULT +
                          0.5);
        }

        public int pixelToRgb(int pixel, ColorModel cm) {
            pixel = pixel >> 8;
            return ((((((0xff << 8) | pixel) << 8) | pixel) << 8) | pixel);
        }
    }
}
