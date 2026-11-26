/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.lang;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Renders bit recipes as self-sized Verilog-2005 expressions. */
final class VerilogExpression {
    private VerilogExpression() {}

    static final Pattern SIMPLE_IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_$]*");
    private static final Set<String> KEYWORDS = Set.of("""
            always and assign automatic begin buf bufif0 bufif1 case casex casez cell cmos config
            deassign default defparam design disable edge else end endcase endconfig endfunction
            endgenerate endmodule endprimitive endspecify endtable endtask event for force forever
            fork forkjoin function generate genvar highz0 highz1 if ifnone incdir include initial
            inout input instance integer join large liblist library localparam macromodule medium
            module nand negedge nmos nor noshowcancelled not notif0 notif1 or output parameter
            pmos posedge primitive pull0 pull1 pulldown pullup pulsestyle_ondetect pulsestyle_onevent
            rcmos real realtime reg release repeat rnmos rpmos rtran rtranif0 rtranif1 scalared
            showcancelled signed small specify specparam strong0 strong1 supply0 supply1 table task
            time tran tranif0 tranif1 tri tri0 tri1 triand trior trireg unsigned use uwire vectored
            wait wand weak0 weak1 while wire wor xnor xor""".split("\\s+"));

    static boolean isKeyword(String name) {
        return KEYWORDS.contains(name);
    }

    private static String[] names(String[] supplied, String... declared) {
        if (supplied.length > declared.length) {
            throw new IllegalArgumentException("Expected at most " + declared.length + " parameter names");
        }
        for (int i = 0; i < supplied.length; i++) {
            String name = supplied[i];
            if (name.isEmpty() || !name.chars().allMatch(c -> c >= '!' && c <= '~')) {
                throw new IllegalArgumentException("Invalid Verilog signal name: " + name);
            }
            declared[i] = SIMPLE_IDENTIFIER.matcher(name).matches() && !isKeyword(name)
                    ? name : "\\" + name + " ";
        }
        return declared;
    }

    private static IllegalArgumentException unsupported(String descriptor, String method) {
        return new IllegalArgumentException("Unsupported " + descriptor + " operation: " + method);
    }

    private static String slice(String name, int size, int offset, int width) {
        if (width == size) {
            return name;
        }
        if (width == 1) {
            return name + "[" + offset + "]";
        }
        return name + "[" + (offset + width - 1) + ":" + offset + "]";
    }

    private static String concat(List<String> parts) {
        return parts.size() == 1 ? parts.getFirst() : "{" + String.join(", ", parts) + "}";
    }

    private static String extended(int size, int width, String bits, String sign) {
        if (width == size) {
            return bits;
        }
        int pad = size - width;
        String prefix = sign == null ? pad + "'b0" : "{" + pad + "{" + sign + "}}";
        return "{" + prefix + ", " + bits + "}";
    }

    static String field(int size, int offset, int width, String method, String[] supplied) {
        return switch (method) {
            case "offset", "width", "endExclusive", "mask" -> {
                names(supplied);
                yield switch (method) {
                    case "offset" -> "32'd" + offset;
                    case "width" -> "32'd" + width;
                    case "endExclusive" -> "32'd" + (offset + width);
                    default -> size + "'h" + Long.toHexString(lowMask(width) << offset);
                };
            }
            case "extract", "extractSigned" -> {
                String word = names(supplied, "word")[0];
                yield extract(size, offset, width, word, method.equals("extractSigned"));
            }
            case "replace" -> {
                String[] p = names(supplied, "word", "value");
                yield replace(size, offset, width, p[0], p[1]);
            }
            default -> throw unsupported("field", method);
        };
    }

    private static long lowMask(int width) {
        return width == 0 ? 0 : -1L >>> (Long.SIZE - width);
    }

    private static String extract(int size, int offset, int width, String word, boolean signed) {
        if (width == 0) {
            return size + "'b0";
        }
        String sign = signed ? slice(word, size, offset + width - 1, 1) : null;
        return extended(size, width, slice(word, size, offset, width), sign);
    }

    private static String replace(int size, int offset, int width, String word, String value) {
        if (width == 0) {
            return word;
        }
        int end = offset + width;
        var parts = new ArrayList<String>(3);
        if (end < size) {
            parts.add(slice(word, size, end, size - end));
        }
        parts.add(slice(value, size, 0, width));
        if (offset > 0) {
            parts.add(slice(word, size, 0, offset));
        }
        return concat(parts);
    }

    static String indexedField(int size, int first, int width, int stride, int count,
                               String method, String[] supplied) {
        String[] p = switch (method) {
            case "extract", "extractSigned" -> names(supplied, "word", "index");
            case "replace" -> names(supplied, "word", "index", "value");
            default -> throw unsupported("indexed field", method);
        };
        boolean replace = method.equals("replace");
        boolean signed = method.equals("extractSigned");
        String word = p[0];
        String index = p[1];
        String result;
        if (count == 1 || stride == 0 || width == 0) {
            result = replace ? replace(size, first, width, word, p[2]) : extract(size, first, width, word, signed);
        } else {
            String step = Math.abs(stride) == 1 ? index : index + " * " + Math.abs(stride);
            String offset = first + (stride < 0 ? " - " : " + ") + step;
            if (replace) {
                String mask = size + "'h" + Long.toHexString(lowMask(width));
                result = "((" + word + " & ~(" + mask + " << (" + offset + "))) | (("
                        + p[2] + " & " + mask + ") << (" + offset + ")))";
            } else {
                String bits = word + "[" + offset + " +: " + width + "]";
                String sign = signed ? word + "[" + offset + " + " + (width - 1) + "]" : null;
                result = extended(size, width, bits, sign);
            }
        }
        // The unsigned comparison also excludes negative Java indices.
        return "((" + index + " < 32'd" + count + ") ? " + result + " : " + size + "'bx)";
    }

    static String selection(int size, long mask, String method, String[] supplied) {
        int[] sources = new int[size];
        if ("compress".equals(method)) {
            String word = names(supplied, "word")[0];
            Arrays.fill(sources, IntBits.Shuffle.ZERO);
            int packed = 0;
            for (int bit = 0; bit < size; bit++) {
                if ((mask & (1L << bit)) != 0) {
                    sources[packed++] = bit;
                }
            }
            return wiring(sources, word, null);
        }
        String[] p = switch (method) {
            case "expand" -> names(supplied, "value");
            case "replace" -> names(supplied, "word", "value");
            case "blend" -> names(supplied, "base", "donor");
            default -> throw unsupported("selection", method);
        };
        boolean expand = method.equals("expand");
        boolean blend = method.equals("blend");
        int packed = 0;
        for (int bit = 0; bit < size; bit++) {
            if ((mask & (1L << bit)) != 0) {
                sources[bit] = size + (blend ? bit : packed++);
            } else {
                sources[bit] = expand ? IntBits.Shuffle.ZERO : bit;
            }
        }
        return wiring(sources, p[0], p[p.length - 1]);
    }

    static String shuffle(int[] sources, String method, String[] supplied) {
        if (!method.equals("rearrange")) {
            throw unsupported("shuffle", method);
        }
        return wiring(sources, names(supplied, "word")[0], null);
    }

    // Sources 0..size-1 address the first word, size..2*size-1 the second.
    // Negative sources are the same literal bits used by BitShuffle.
    private static String wiring(int[] sources, String first, String second) {
        var parts = new ArrayList<String>();
        int size = sources.length;
        for (int high = size - 1; high >= 0; ) {
            int start = sources[high];
            int low = high;
            if (start < 0) {
                long literal = 0;
                do {
                    literal = (literal << 1) | (sources[low] == IntBits.Shuffle.ONE ? 1 : 0);
                    low--;
                } while (low >= 0 && sources[low] < 0);
                parts.add((high - low) + "'b" + Long.toBinaryString(literal));
                high = low;
            } else {
                String name = start < size ? first : second;
                if (low > 0 && sources[low - 1] == start) {
                    while (low > 0 && sources[low - 1] == start) {
                        low--;
                    }
                    parts.add("{" + (high - low + 1) + "{" + slice(name, size, start % size, 1) + "}}");
                } else {
                    // Stay within one input word when forming a part-select.
                    while (low > 0 && sources[low] % size > 0 && sources[low - 1] == sources[low] - 1) {
                        low--;
                    }
                    parts.add(slice(name, size, sources[low] % size, high - low + 1));
                }
                high = low - 1;
            }
        }
        return concat(parts);
    }
}
