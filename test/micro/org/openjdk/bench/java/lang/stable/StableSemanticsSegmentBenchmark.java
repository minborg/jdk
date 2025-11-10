/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.java.lang.stable;

import jdk.internal.misc.Unsafe;
import org.openjdk.jmh.annotations.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Benchmark measuring stable semantics access performance
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark) // Share the same state instance (for contention)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 3, time = 2)
@Fork(value = 2, jvmArgs = {
        "--enable-native-access=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:+PrintCompilation",
        "-XX:+PrintAssembly"})
public class StableSemanticsSegmentBenchmark {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    private static final VarHandle INT_SEGMENT_VH = JAVA_INT.varHandle();

    private static final int LENGTH = 16;
    private static final int[] INT_ARRAY = IntStream.range(0, LENGTH).toArray();

    private static final MemorySegment HEAP_INT_SEGMENT = MemorySegment.ofArray(INT_ARRAY);
    private static final MemorySegment NATIVE_INT_SEGMENT = Arena.global().allocateFrom(JAVA_INT, HEAP_INT_SEGMENT, JAVA_INT, 0, LENGTH);

    // Arrays via Unsafe (xBaseline is normal non-Unsafe array access to give a baseline figure)
    @Benchmark public int     unsafeArrayIntegerBaseline()               { return INT_ARRAY[0]; }
    @Benchmark public int     unsafeHeapSegmentInteger()                 { return UNSAFE.getInt(HEAP_INT_SEGMENT.heapBase().orElseThrow(), Unsafe.ARRAY_INT_BASE_OFFSET); }
    @Benchmark public int     unsafeHeapSegmentIntegerStable()           { return UNSAFE.getIntStable(HEAP_INT_SEGMENT.heapBase().orElseThrow(), Unsafe.ARRAY_INT_BASE_OFFSET); }
    @Benchmark public int     unsafeHeapSegmentIntegerStableVolatile()   { return UNSAFE.getIntStableVolatile(HEAP_INT_SEGMENT.heapBase().orElseThrow(), Unsafe.ARRAY_INT_BASE_OFFSET); }
    @Benchmark public int     unsafeNativeSegmentInteger()               { return UNSAFE.getInt(null, NATIVE_INT_SEGMENT.address()); }
    @Benchmark public int     unsafeNativeSegmentIntegerStable()         { return UNSAFE.getIntStable(null, NATIVE_INT_SEGMENT.address()); }
    @Benchmark public int     unsafeNativeSegmentIntegerStableVolatile() { return UNSAFE.getIntStableVolatile(null, NATIVE_INT_SEGMENT.address()); }

    @Benchmark
    public int sum() {
        int sum = 0;
        for (int i = 0; i < LENGTH; i++) {
            sum += INT_ARRAY[i];
        }
        return sum;
    }

    @Benchmark
    public int sumNativeStable() {
        int sum = 0;
        for (int i = 0; i < LENGTH; i++) {
            sum += UNSAFE.getIntStable(INT_ARRAY, Unsafe.ARRAY_INT_BASE_OFFSET + (long) Unsafe.ARRAY_INT_INDEX_SCALE * i);
        }
        return sum;
    }

    @Benchmark
    public int sumVarHandleStable() {
        int sum = 0;
        for (int i = 0; i < LENGTH; i++) {
            sum += (int) INT_SEGMENT_VH.getStable(NATIVE_INT_SEGMENT, 0, i);
        }
        return sum;
    }

}
