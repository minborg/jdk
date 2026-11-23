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
package org.openjdk.bench.java.lang;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 4, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class BitFields {
    private int intValue = 0xcafe_babe;
    private int intField = 0x1234_5678;
    private long longValue = 0xfedc_ba98_7654_3210L;
    private long longField = 0x1234_5678_9abc_def0L;

    // Mutable fields keep the range from being a compile-time constant.
    private int intOffset = 7;
    private int intLength = 11;
    private int intMask = 0x7ff << 7;
    private int longOffset = 31;
    private int longLength = 31;
    private long longMask = 0x7fff_ffffL << 31;

    @Benchmark
    public int intExtractFixed() {
        return Integer.extractBits(intValue, 7, 11);
    }

    @Benchmark
    public int intExtractFixedRaw() {
        return (intValue >>> 7) & 0x7ff;
    }

    @Benchmark
    public int intExtractRuntime() {
        return Integer.extractBits(intValue, intOffset, intLength);
    }

    @Benchmark
    public int intExtractRuntimeCachedMask() {
        return (intValue & intMask) >>> intOffset;
    }

    @Benchmark
    public int intExtractRuntimeRaw() {
        return extract(intValue, intOffset, intLength);
    }

    @Benchmark
    public int intExtractRuntimeChecked() {
        int offset = intOffset;
        int length = intLength;
        Objects.checkFromIndexSize(offset, length, Integer.SIZE);
        return extract(intValue, offset, length);
    }

    @Benchmark
    public int intReplaceFixed() {
        return Integer.replaceBits(intValue, 7, 11, intField);
    }

    @Benchmark
    public int intReplaceFixedRaw() {
        return (intValue & ~0x3ff80) | ((intField & 0x7ff) << 7);
    }

    @Benchmark
    public int intReplaceRuntime() {
        return Integer.replaceBits(intValue, intOffset, intLength, intField);
    }

    @Benchmark
    public int intReplaceRuntimeCachedMask() {
        return (intValue & ~intMask) | ((intField << intOffset) & intMask);
    }

    @Benchmark
    public int intReplaceRuntimeRaw() {
        return replace(intValue, intOffset, intLength, intField);
    }

    @Benchmark
    public int intReplaceRuntimeChecked() {
        int offset = intOffset;
        int length = intLength;
        Objects.checkFromIndexSize(offset, length, Integer.SIZE);
        return replace(intValue, offset, length, intField);
    }

    @Benchmark
    public long longExtractFixed() {
        return Long.extractBits(longValue, 31, 31);
    }

    @Benchmark
    public long longExtractFixedRaw() {
        return (longValue >>> 31) & 0x7fff_ffffL;
    }

    @Benchmark
    public long longExtractRuntime() {
        return Long.extractBits(longValue, longOffset, longLength);
    }

    @Benchmark
    public long longExtractRuntimeCachedMask() {
        return (longValue & longMask) >>> longOffset;
    }

    @Benchmark
    public long longExtractRuntimeRaw() {
        return extract(longValue, longOffset, longLength);
    }

    @Benchmark
    public long longExtractRuntimeChecked() {
        int offset = longOffset;
        int length = longLength;
        Objects.checkFromIndexSize(offset, length, Long.SIZE);
        return extract(longValue, offset, length);
    }

    @Benchmark
    public long longReplaceFixed() {
        return Long.replaceBits(longValue, 0, Integer.SIZE, longField);
    }

    @Benchmark
    public long longReplaceFixedRaw() {
        return (longValue & 0xffff_ffff_0000_0000L) | (longField & 0xffff_ffffL);
    }

    @Benchmark
    public long longReplaceRuntime() {
        return Long.replaceBits(longValue, longOffset, longLength, longField);
    }

    @Benchmark
    public long longReplaceRuntimeCachedMask() {
        return (longValue & ~longMask) | ((longField << longOffset) & longMask);
    }

    @Benchmark
    public long longReplaceRuntimeRaw() {
        return replace(longValue, longOffset, longLength, longField);
    }

    @Benchmark
    public long longReplaceRuntimeChecked() {
        int offset = longOffset;
        int length = longLength;
        Objects.checkFromIndexSize(offset, length, Long.SIZE);
        return replace(longValue, offset, length, longField);
    }

    private static int extract(int value, int offset, int length) {
        return length == 0 ? 0 : (value >>> offset) & (-1 >>> (Integer.SIZE - length));
    }

    private static long extract(long value, int offset, int length) {
        return length == 0 ? 0 : (value >>> offset) & (-1L >>> (Long.SIZE - length));
    }

    private static int replace(int value, int offset, int length, int field) {
        if (length == 0) {
            return value;
        }
        int mask = -1 >>> (Integer.SIZE - length);
        return (value & ~(mask << offset)) | ((field & mask) << offset);
    }

    private static long replace(long value, int offset, int length, long field) {
        if (length == 0) {
            return value;
        }
        long mask = -1L >>> (Long.SIZE - length);
        return (value & ~(mask << offset)) | ((field & mask) << offset);
    }
}
