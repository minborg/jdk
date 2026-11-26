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
 * @summary Verilog expressions for int and long bit recipes
 * @run junit BitRecipeVerilogTest
 */

import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BitRecipeVerilogTest {
    @Test
    void fields() {
        IntBits red = IntBits.Field.endingAt(16, 5);
        assertEquals("{27'b0, word[15:11]}", red.toVerilogExpression("extract"));
        assertEquals("{{27{pixel[15]}}, pixel[15:11]}", red.toVerilogExpression("extractSigned", "pixel"));
        assertEquals("{pixel[31:16], value[4:0], pixel[10:0]}", red.toVerilogExpression("replace", "pixel"));
        assertEquals("{p[31:16], r[4:0], p[10:0]}", red.toVerilogExpression("replace", "p", "r"));
        assertEquals("32'hf800", red.toVerilogExpression("mask"));

        var bit = LongBits.Field.of(63, 1);
        assertEquals("{63'b0, word[63]}", bit.toVerilogExpression("extract"));
        assertEquals("{{63{word[63]}}, word[63]}", bit.toVerilogExpression("extractSigned"));
        assertEquals("{value[0], word[62:0]}", bit.toVerilogExpression("replace"));
        assertEquals("64'h8000000000000000", bit.toVerilogExpression("mask"));
        assertEquals("32'd63", bit.toVerilogExpression("offset"));
        assertEquals("32'd1", bit.toVerilogExpression("width"));
        assertEquals("32'd64", bit.toVerilogExpression("endExclusive"));

        var empty = IntBits.Field.of(32, 0);
        assertEquals("32'b0", empty.toVerilogExpression("extractSigned"));
        assertEquals("word", empty.toVerilogExpression("replace"));
        var full = LongBits.Field.of(0, 64);
        assertEquals("word", full.toVerilogExpression("extractSigned"));
        assertEquals("value", full.toVerilogExpression("replace"));
        assertEquals("64'hffffffffffffffff", full.toVerilogExpression("mask"));
    }

    @Test
    void indexedFields() {
        var fields = IntBits.IndexedField.of(12, 4, -4, 4);
        assertEquals("((index < 32'd4) ? {28'b0, word[12 - index * 4 +: 4]} : 32'bx)",
                fields.toVerilogExpression("extract"));
        assertEquals("((i < 32'd4) ? ((x & ~(32'hf << (12 - i * 4))) | ((value & 32'hf) << (12 - i * 4))) : 32'bx)",
                fields.toVerilogExpression("replace", "x", "i"));

        var overlapping = LongBits.IndexedField.of(31, 8, 1, 10);
        assertEquals("((i < 32'd10) ? {{56{x[31 + i + 7]}}, x[31 + i +: 8]} : 64'bx)",
                overlapping.toVerilogExpression("extractSigned", "x", "i"));
        var stationary = LongBits.IndexedField.of(32, 1, 0, Integer.MAX_VALUE);
        assertEquals("((index < 32'd2147483647) ? {63'b0, word[32]} : 64'bx)",
                stationary.toVerilogExpression("extract"));
        var single = IntBits.IndexedField.of(0, 32, Integer.MIN_VALUE, 1);
        assertEquals("((index < 32'd1) ? value : 32'bx)", single.toVerilogExpression("replace"));
        var empty = LongBits.IndexedField.of(64, 0, -1, 65);
        assertEquals("((index < 32'd65) ? 64'b0 : 64'bx)", empty.toVerilogExpression("extract"));
        assertEquals("((index < 32'd65) ? word : 64'bx)", empty.toVerilogExpression("replace"));
    }

    @Test
    void selections() {
        var bits = IntBits.Selection.ofMask(0x8000_0005);
        assertEquals("{29'b0, word[31], word[2], word[0]}", bits.toVerilogExpression("compress"));
        assertEquals("{v[2], 28'b0, v[1], 1'b0, v[0]}", bits.toVerilogExpression("expand", "v"));
        assertEquals("{value[2], word[30:3], value[1], word[1], value[0]}", bits.toVerilogExpression("replace"));
        assertEquals("{donor[31], x[30:3], donor[2], x[1], donor[0]}", bits.toVerilogExpression("blend", "x"));
        var empty = LongBits.Selection.ofMask(0);
        assertEquals("64'b0", empty.toVerilogExpression("compress"));
        assertEquals("64'b0", empty.toVerilogExpression("expand"));
        assertEquals("word", empty.toVerilogExpression("replace"));
        assertEquals("base", empty.toVerilogExpression("blend"));
        var full = LongBits.Selection.ofMask(-1L);
        assertEquals("word", full.toVerilogExpression("compress"));
        assertEquals("value", full.toVerilogExpression("expand"));
        assertEquals("value", full.toVerilogExpression("replace"));
        assertEquals("donor", full.toVerilogExpression("blend"));
        // A run must stop at the boundary between two source words.
        var top = LongBits.Selection.ofMask(Long.MIN_VALUE);
        assertEquals("{value[0], word[62:0]}", top.toVerilogExpression("replace"));
    }

    @Test
    void shuffles() {
        var shuffle = IntBits.Shuffle.concatHighToLow(IntBits.Shuffle.ones(2),
                IntBits.Shuffle.repeatToWidth(IntBits.Field.of(8, 4), 7), IntBits.Shuffle.literal(5, 3));
        assertEquals("{22'b11, word[11:8], word[11:9], 3'b101}", shuffle.toVerilogExpression("rearrange"));
        var broadcast = LongBits.Shuffle.concatHighToLow(
                LongBits.Shuffle.repeatToWidth(LongBits.Field.of(63, 1), 64));
        assertEquals("{64{word[63]}}", broadcast.toVerilogExpression("rearrange"));
        var swap = LongBits.Shuffle.concatHighToLow(LongBits.Field.of(0, 32), LongBits.Field.of(32, 32));
        assertEquals("{x[31:0], x[63:32]}", swap.inverse().toVerilogExpression("rearrange", "x"));
        assertEquals("64'b" + "1".repeat(64),
                LongBits.Shuffle.concatHighToLow(LongBits.Shuffle.ones(64)).toVerilogExpression("rearrange"));
        assertEquals("32'b0", IntBits.Shuffle.concatHighToLow().toVerilogExpression("rearrange"));
    }

    @Test
    void namesAndErrors() {
        var bit = IntBits.Field.of(0, 1);
        assertEquals("{31'b0, \\wire [0]}", bit.toVerilogExpression("extract", "wire"));
        assertEquals("{31'b0, \\pixel-red [0]}", bit.toVerilogExpression("extract", "pixel-red"));
        assertEquals("{31'b0, PIXEL$1[0]}", bit.toVerilogExpression("extract", "PIXEL$1"));
        assertThrows(NullPointerException.class, () -> bit.toVerilogExpression(null));
        assertThrows(NullPointerException.class, () -> bit.toVerilogExpression("extract", (String[]) null));
        assertThrows(NullPointerException.class, () -> bit.toVerilogExpression("replace", "x", null));
        for (String invalid : new String[] {"", " x", "a\nb", "ü"}) {
            assertThrows(IllegalArgumentException.class, () -> bit.toVerilogExpression("extract", invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> bit.toVerilogExpression("width", "unused"));
        assertThrows(IllegalArgumentException.class, () -> bit.toVerilogExpression("extract", "x", "extra"));
        assertThrows(IllegalArgumentException.class, () -> bit.toVerilogExpression("compress"));
        assertThrows(IllegalArgumentException.class, () -> LongBits.Selection.ofMask(1).toVerilogExpression("extract"));
        assertThrows(IllegalArgumentException.class, () -> IntBits.IndexedField.of(0, 1, 1, 32)
                .toVerilogExpression("asSelection"));
        assertThrows(IllegalArgumentException.class, () -> LongBits.Shuffle.concatHighToLow()
                .toVerilogExpression("inverse"));
    }

    @Test
    void parseSignalAndRanges() {
        int word = 0x8123_4567;
        assertEquals(word, IntBits.Shuffle.fromVerilogExpression(" ( PIXEL$1 ) ").rearrange(word));
        assertEquals(Integer.rotateLeft(word, 1), IntBits.Shuffle.fromVerilogExpression(
                "{p[30:0], \\p [31]}").rearrange(word));
        assertEquals(-1L, LongBits.Shuffle.fromVerilogExpression("{64{\\wire [63]}}").rearrange(Long.MIN_VALUE));
        assertEquals(0L, LongBits.Shuffle.fromVerilogExpression("{64{\\a,b{} [0]}}").rearrange(Long.MIN_VALUE));
        var field = LongBits.Field.endingAt(64, 7);
        var signed = LongBits.Shuffle.fromVerilogExpression(field.toVerilogExpression("extractSigned", "pixel-red"));
        for (int i = 0; i < 128; i++) {
            long input = (long) i << 57;
            assertEquals(field.extractSigned(input), signed.rearrange(input));
        }
    }

    @Test
    void parseLiteralsAndReplication() {
        assertEquals(0x12_12_12_12, IntBits.Shuffle.fromVerilogExpression(
                "{ 4 { 4'o1, 4'd2 } }").rearrange(-1));
        assertEquals(0xabab_abab, IntBits.Shuffle.fromVerilogExpression(
                "{2{{2{4'H A, 4'b1011}}}}").rearrange(0));
        assertEquals(-1L, LongBits.Shuffle.fromVerilogExpression("64'hffff_ffff_ffff_ffff").rearrange(0));
        assertEquals(Long.MIN_VALUE, LongBits.Shuffle.fromVerilogExpression(
                "64'd9223372036854775808").rearrange(0));
        assertEquals(1L, LongBits.Shuffle.fromVerilogExpression("64'h1_0000_0000_0000_0001").rearrange(-1));
        assertEquals(1, IntBits.Shuffle.fromVerilogExpression("{31'b0, 1'b101}").rearrange(-1));
        assertEquals(42, IntBits.Shuffle.fromVerilogExpression("{".repeat(4096) + "x" + "}".repeat(4096))
                .rearrange(42));
    }

    @Test
    void rejectInvalidExpressions() {
        assertThrows(NullPointerException.class, () -> IntBits.Shuffle.fromVerilogExpression(null));
        assertThrows(NullPointerException.class, () -> LongBits.Shuffle.fromVerilogExpression(null));
        for (String expression : new String[] {"", " ", "{}", "{word,}", "{,word}", "{word", "word}",
                "(word,word)", "word word", "32", "32'h", "32'h_ff", "32'b2", "32'o8", "32'dA",
                "32'bx", "32'hz", "32'b?", "32'shffff", "0'b0", "31'b0", "33'b0", "32'b0!",
                "wire", "\\", "\\wire", "p[32]", "p[2:3]", "p[-1]", "p[i]", "p[0 +: 32]",
                "{p[31:1], q[0]}", "p | q", "{0{p}}", "{1{p},p}", "{2{p}}", "{33{1'b0}}",
                "{999999999999999999999{1'b0}}", "{32{}}", "p[0]", "p\u0000"}) {
            assertThrows(IllegalArgumentException.class, () -> IntBits.Shuffle.fromVerilogExpression(expression),
                    expression);
        }
        for (String expression : new String[] {"32'b0", "x[64:1]", "{65{1'b1}}", "{x[63:1], y[0]}"}) {
            assertThrows(IllegalArgumentException.class, () -> LongBits.Shuffle.fromVerilogExpression(expression),
                    expression);
        }
    }

    @Test
    void roundTripBitMappings() {
        var random = new Random(565);
        for (int trial = 0; trial < 64; trial++) {
            int[] intMapping = random.ints(Integer.SIZE, IntBits.Shuffle.ONE, Integer.SIZE).toArray();
            var ints = IntBits.Shuffle.ofBits(intMapping);
            String intExpression = ints.toVerilogExpression("rearrange", "pixel-red");
            var parsedInts = IntBits.Shuffle.fromVerilogExpression(intExpression);
            assertEquals(ints.rearrange(0), parsedInts.rearrange(0), intExpression);
            for (int bit = 0; bit < Integer.SIZE; bit++) {
                int input = 1 << bit;
                assertEquals(ints.rearrange(input), parsedInts.rearrange(input), intExpression);
            }

            int[] longMapping = random.ints(Long.SIZE, LongBits.Shuffle.ONE, Long.SIZE).toArray();
            var longs = LongBits.Shuffle.ofBits(longMapping);
            String longExpression = longs.toVerilogExpression("rearrange", "pixel");
            var parsedLongs = LongBits.Shuffle.fromVerilogExpression(longExpression);
            assertEquals(longs.rearrange(0L), parsedLongs.rearrange(0L), longExpression);
            for (int bit = 0; bit < Long.SIZE; bit++) {
                long longInput = 1L << bit;
                assertEquals(longs.rearrange(longInput), parsedLongs.rearrange(longInput), longExpression);
            }
        }
    }
}
