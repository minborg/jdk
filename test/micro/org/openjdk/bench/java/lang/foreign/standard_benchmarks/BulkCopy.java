/*
 *  Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.java.lang.foreign.standard_benchmarks;

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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3)
public class BulkCopy {

    @Param({"8", "512", "16777216"})
    public int ELEM_SIZE;

    byte[] srcArray;
    byte[] dstArray;
    MemorySegment heapSrcSegment;
    MemorySegment heapDstSegment;
    MemorySegment nativeSrcSegment;
    MemorySegment nativeDstSegment;
    ByteBuffer srcBuffer;
    ByteBuffer dstBuffer;

    @Setup
    public void setup() {
        final Arena arena = Arena.ofAuto();
        final Random rnd = new Random(42);
        srcArray = new byte[ELEM_SIZE];
        rnd.nextBytes(srcArray);
        dstArray = new byte[ELEM_SIZE];
        heapSrcSegment = MemorySegment.ofArray(srcArray);
        heapDstSegment = MemorySegment.ofArray(dstArray);
        nativeSrcSegment = arena.allocateFrom(ValueLayout.JAVA_BYTE, srcArray);
        nativeDstSegment = arena.allocate(ELEM_SIZE);
        srcBuffer = ByteBuffer.wrap(srcArray);
        dstBuffer = ByteBuffer.wrap(dstArray);
    }

    @Benchmark
    public void arrayCopy() {
        System.arraycopy(srcArray, 0, dstArray, 0, ELEM_SIZE);
    }

    @Benchmark
    public void bufferCopy() {
        dstBuffer.put(srcBuffer);
    }

    @Fork(value = 3, jvmArgs = {"-Djava.lang.foreign.native.threshold.power.copy=31"})
    @Benchmark
    public void heapSegmentCopyJava() {
        MemorySegment.copy(heapSrcSegment, 0, heapDstSegment, 0, ELEM_SIZE);
    }

    @Fork(value = 3, jvmArgs = {"-Djava.lang.foreign.native.threshold.power.copy=0"})
    @Benchmark
    public void heapSegmentCopyUnsafe() {
        MemorySegment.copy(heapSrcSegment, 0, heapDstSegment, 0, ELEM_SIZE);
    }

    @Fork(value = 3, jvmArgs = {"-Djava.lang.foreign.native.threshold.power.copy=31"})
    @Benchmark
    public void nativeSegmentCopyJava() {
        MemorySegment.copy(nativeSrcSegment, 0, nativeDstSegment, 0, ELEM_SIZE);
    }

    @Fork(value = 3, jvmArgs = {"-Djava.lang.foreign.native.threshold.power.copy=0"})
    @Benchmark
    public void nativeSegmentCopyUnsafe() {
        MemorySegment.copy(nativeSrcSegment, 0, nativeDstSegment, 0, ELEM_SIZE);
    }

}
