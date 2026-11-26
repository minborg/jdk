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

import java.util.ArrayDeque;
import java.util.regex.Matcher;

/** Parses single-input Verilog wiring directly into a shuffle's source-bit mapping. */
final class VerilogShuffleParser {
    private final String expression;
    private final Matcher identifier;
    private final int[] bits;
    private String signal;
    private int position;
    // Verilog lists the most significant bits first. Fill the mapping from the end.
    private int remaining;

    private VerilogShuffleParser(int size, String expression) {
        this.expression = expression;
        this.identifier = VerilogExpression.SIMPLE_IDENTIFIER.matcher(expression);
        this.bits = new int[size];
        this.remaining = size;
    }

    static BitShuffle parse(int size, String expression) {
        return new VerilogShuffleParser(size, expression).parse();
    }

    // copies is positive only for a replication, which has an additional closing brace.
    private record Group(char close, int end, int copies) {}

    private BitShuffle parse() {
        var groups = new ArrayDeque<Group>();
        while (true) {
            if (take('{')) {
                groups.push(new Group('}', remaining, replicationCount()));
                continue;
            }
            if (take('(')) {
                groups.push(new Group(')', remaining, 0));
                continue;
            }
            if (isDecimal(peek())) {
                literal();
            } else {
                signal();
            }

            while (!groups.isEmpty()) {
                Group group = groups.peek();
                if (group.close == '}' && take(',')) {
                    break;
                }
                require(group.close);
                groups.pop();
                if (group.copies > 0) {
                    require('}');
                    int width = group.end - remaining;
                    if (group.copies - 1 > remaining / width) {
                        throw error("Replication exceeds the word width");
                    }
                    for (int i = 1; i < group.copies; i++) {
                        int source = remaining;
                        System.arraycopy(bits, source, bits, reserve(width), width);
                    }
                }
            }
            if (groups.isEmpty()) {
                skipSpace();
                if (position != expression.length()) {
                    throw error("Unexpected trailing input");
                }
                if (remaining != 0) {
                    throw error("Expected " + bits.length + " result bits, got " + (bits.length - remaining));
                }
                return new BitShuffle(bits);
            }
        }
    }

    private int replicationCount() {
        skipSpace();
        if (isDecimal(peek())) {
            int start = position;
            int n = number();
            if (take('{')) {
                if (n == 0) {
                    throw error("Expected a positive replication count");
                }
                return n;
            }
            // A sized literal can also be the first part of a concatenation.
            position = start;
        }
        return 0;
    }

    private void signal() {
        String name;
        if (take('\\')) {
            int start = position;
            while (peek() >= '!' && peek() <= '~') {
                position++;
            }
            if (position == start || !isSpace(peek())) {
                throw error("Expected a whitespace-terminated escaped identifier");
            }
            name = expression.substring(start, position);
        } else {
            identifier.region(position, expression.length());
            if (!identifier.lookingAt() || VerilogExpression.isKeyword(identifier.group())) {
                throw error("Expected a signal name or sized literal");
            }
            name = identifier.group();
            position = identifier.end();
        }
        if (signal != null && !signal.equals(name)) {
            throw error("Expected a single input signal");
        }
        signal = name;
        int high = bits.length - 1;
        int low = 0;
        if (take('[')) {
            high = number();
            low = take(':') ? number() : high;
            require(']');
            if (high >= bits.length || low > high) {
                throw error("Invalid bit selection");
            }
        }
        int width = high - low + 1;
        BitShuffle.appendField(bits, reserve(width), low, width);
    }

    private void literal() {
        int width = number();
        if (width == 0) {
            throw error("Expected a positive literal width");
        }
        require('\'');
        int radix = switch (peek()) {
            case 'b', 'B' -> 2;
            case 'o', 'O' -> 8;
            case 'd', 'D' -> 10;
            case 'h', 'H' -> 16;
            default -> throw error("Expected a literal base");
        };
        position++;
        skipSpace();
        int digit = digit(radix);
        if (digit < 0) {
            throw error("Expected a base-" + radix + " digit");
        }
        long value = 0;
        do {
            // Only the low width bits of a Verilog sized literal contribute.
            value = value * radix + digit;
            position++;
            while (peek() == '_') {
                position++;
            }
            digit = digit(radix);
        } while (digit >= 0);
        BitShuffle.appendLiteral(bits, reserve(width), value, width);
    }

    private int digit(int radix) {
        char c = peek();
        return c < 128 ? Character.digit(c, radix) : -1;
    }

    private int number() {
        skipSpace();
        if (!isDecimal(peek())) {
            throw error("Expected a decimal number");
        }
        int value = 0;
        do {
            value = 10 * value + peek() - '0';
            if (value > bits.length) {
                throw error("Number exceeds " + bits.length);
            }
            position++;
            while (peek() == '_') {
                position++;
            }
        } while (isDecimal(peek()));
        return value;
    }

    private int reserve(int width) {
        if (width > remaining) {
            throw error("Result exceeds " + bits.length + " bits");
        }
        remaining -= width;
        return remaining;
    }

    private char peek() {
        return position < expression.length() ? expression.charAt(position) : '\0';
    }

    private static boolean isDecimal(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
    }

    private void skipSpace() {
        while (isSpace(peek())) {
            position++;
        }
    }

    private boolean take(char c) {
        skipSpace();
        if (peek() != c) {
            return false;
        }
        position++;
        return true;
    }

    private void require(char c) {
        if (!take(c)) {
            throw error("Expected '" + c + "'");
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at index " + position);
    }
}
