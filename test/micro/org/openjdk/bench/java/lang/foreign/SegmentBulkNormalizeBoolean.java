/*
 *  Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

package org.openjdk.bench.java.lang.foreign;

import jdk.internal.foreign.AbstractMemorySegmentImpl;
import jdk.internal.misc.ScopedMemoryAccess;
import jdk.internal.util.Architecture;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.foreign.MemorySegment;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgs = {
        "--add-exports=java.base/jdk.internal.foreign=ALL-UNNAMED",
        "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
        "--add-exports=java.base/jdk.internal.util=ALL-UNNAMED"})
public class SegmentBulkNormalizeBoolean {

    private static final ScopedMemoryAccess SCOPED_MEMORY_ACCESS = ScopedMemoryAccess.getScopedMemoryAccess();

    @Param({"8", "64", "512", "4096", "32768", "262144", "2097152"})
    public int ELEM_SIZE;

    byte[] array;
    AbstractMemorySegmentImpl src;
    AbstractMemorySegmentImpl dst;

    @Setup
    public void setup() {
        array = new byte[ELEM_SIZE];
        Random rnd = new Random(42);
        for (int i = 0; i < array.length; i++) {
            array[i] = (byte) rnd.nextInt(Byte.MIN_VALUE, Byte.MAX_VALUE + 1);
        }
        src = (AbstractMemorySegmentImpl) MemorySegment.ofArray(array);
        dst = (AbstractMemorySegmentImpl) MemorySegment.ofArray(array);
    }

    @Benchmark
    public int base() {
        int sum = 0;
        for (int i = 0; i < ELEM_SIZE; i++) {
            final byte v = SCOPED_MEMORY_ACCESS.getByte(src.sessionImpl(), src.unsafeGetBase(), src.unsafeGetOffset() + i);
            byte val = (byte) (v == 0 ? 0 : 1);
            SCOPED_MEMORY_ACCESS.putByte(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + i, val);
        }
        return sum;
    }

    @Benchmark
    public int min() {
        int sum = 0;
        for (int i = 0; i < ELEM_SIZE; i++) {
            final byte v = SCOPED_MEMORY_ACCESS.getByte(src.sessionImpl(), src.unsafeGetBase(), src.unsafeGetOffset() + i);
            byte val = (byte) Math.min(1, v & 0xff);
            SCOPED_MEMORY_ACCESS.putByte(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + i, val);
        }
        return sum;
    }

    @Benchmark
    public int minShort() {
        int sum = 0;
        for (int i = 0; i < ELEM_SIZE; i+=2) {
            final short v = SCOPED_MEMORY_ACCESS.getShortUnaligned(src.sessionImpl(), src.unsafeGetBase(), src.unsafeGetOffset() + i, !Architecture.isLittleEndian());
            short val = (short) (Math.min(1, v & 0x00ff) + Math.min(0x0100, v & 0xff00));
            SCOPED_MEMORY_ACCESS.putShortUnaligned(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + i, val, !Architecture.isLittleEndian());
        }
        return sum;
    }

    @Benchmark
    public void minLong() {
        for (int i = 0; i < ELEM_SIZE; i += Long.BYTES) {
            final long v = SCOPED_MEMORY_ACCESS.getLongUnaligned(src.sessionImpl(), src.unsafeGetBase(), src.unsafeGetOffset() + i, !Architecture.isLittleEndian());
            long val = (Math.min(0x0001000000000000L, (v >>> 8) & 0x00ff000000000000L) << 8) +
                    Math.min(0x0001000000000000L, v & 0x00ff000000000000L) +
                    Math.min(0x0000010000000000L, v & 0x0000ff0000000000L) +
                    Math.min(0x0000000100000000L, v & 0x000000ff00000000L) +
                    Math.min(0x0000000001000000L, v & 0x00000000ff000000L) +
                    Math.min(0x0000000000010000L, v & 0x0000000000ff0000L) +
                    Math.min(0x0000000000000100L, v & 0x000000000000ff00L) +
                    Math.min(0x0000000000000001L, v & 0x00000000000000ffL);
            SCOPED_MEMORY_ACCESS.putLongUnaligned(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + i, val, !Architecture.isLittleEndian());
        }
    }

    @Benchmark
    public void minInt() {
        for (int i = 0; i < ELEM_SIZE; i += Integer.BYTES) {
            final int v = SCOPED_MEMORY_ACCESS.getIntUnaligned(src.sessionImpl(), src.unsafeGetBase(), src.unsafeGetOffset() + i, !Architecture.isLittleEndian());
            int val = (int) Math.min(0x0000000001000000L, v & 0x00000000ff000000L) +
                    Math.min(0x00010000, v & 0x00ff0000) +
                    Math.min(0x00000100, v & 0x0000ff00) +
                    Math.min(0x00000001, v & 0x000000ff);
            SCOPED_MEMORY_ACCESS.putIntUnaligned(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + i, val, !Architecture.isLittleEndian());
        }
    }

    @Benchmark
    public void andShift() {
        for (int i = 0; i < ELEM_SIZE; i++) {
            final byte v = SCOPED_MEMORY_ACCESS.getByte(src.sessionImpl(), src.unsafeGetBase(), src.unsafeGetOffset() + i);
            byte val = (byte) (-(v & 0xff) >>> 31);
            SCOPED_MEMORY_ACCESS.putByte(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + i, val);
        }
    }

}
