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
@Fork(value = 3, jvmArgs = {"--add-exports=java.base/jdk.internal.foreign=ALL-UNNAMED", "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED"})
public class SegmentBulkNormalizeBoolean {

    private static final ScopedMemoryAccess SCOPED_MEMORY_ACCESS = ScopedMemoryAccess.getScopedMemoryAccess();

    @Param({"2", "3", "4", "5", "6", "7", "8", "64", "512",
            "4096", "32768", "262144", "2097152"})
    public int ELEM_SIZE;

    byte[] array;
    AbstractMemorySegmentImpl segment;

    @Setup
    public void setup() {
        array = new byte[ELEM_SIZE];
        Random rnd = new Random(42);
        for (int i = 0; i < array.length; i++) {
            array[i] = (byte) rnd.nextInt(Byte.MIN_VALUE, Byte.MAX_VALUE + 1);
        }
        segment = (AbstractMemorySegmentImpl) MemorySegment.ofArray(array);
    }

    @Benchmark
    public int conditional() {
        int sum = 0;
        for (int i = 0; i < ELEM_SIZE; i++) {
            final byte v = SCOPED_MEMORY_ACCESS.getByte(segment.sessionImpl(), segment.unsafeGetBase(), segment.unsafeGetOffset() + i);
            sum += v == 0 ? 0 : 1;
        }
        return sum;
    }

    @Benchmark
    public int bitLength() {
        int sum = 0;
        for (int i = 0; i < ELEM_SIZE; i++) {
            final byte v = SCOPED_MEMORY_ACCESS.getByte(segment.sessionImpl(), segment.unsafeGetBase(), segment.unsafeGetOffset() + i);
            sum += Integer.bitCount(Integer.bitCount(Integer.bitCount(Integer.bitCount(v))));
        }
        return sum;
    }

    @Benchmark
    public int maskShiftOr() {
        int sum = 0;
        for (int i = 0; i < ELEM_SIZE; i++) {
            final byte v = SCOPED_MEMORY_ACCESS.getByte(segment.sessionImpl(), segment.unsafeGetBase(), segment.unsafeGetOffset() + i);
            sum += (byte) (((v & 0x80) >> 7) |
                    ((v & 0x40) >> 6) |
                    ((v & 0x20) >> 5) |
                    ((v & 0x10) >> 4) |
                    ((v & 0x08) >> 3) |
                    ((v & 0x04) >> 2) |
                    ((v & 0x02) >> 1) |
                    ((v & 0x01)));
        }
        return sum;
    }

    @Benchmark
    public int min() {
        int sum = 0;
        for (int i = 0; i < ELEM_SIZE; i++) {
            final byte v = SCOPED_MEMORY_ACCESS.getByte(segment.sessionImpl(), segment.unsafeGetBase(), segment.unsafeGetOffset() + i);
            sum += (byte) Math.min(1, v & 0xff);
        }
        return sum;
    }

}
