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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

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
public class StableSemanticsArrayBenchmark {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    private static final VarHandle INT_ARRAY_VH = MethodHandles.arrayElementVarHandle(int[].class);

    private static final int LENGTH = 16;
    private static final int[] INT_ARRAY = IntStream.range(0, LENGTH).toArray();
    private static final long[] LONG_ARRAY = LongStream.range(0, LENGTH).toArray();

    // Arrays via Unsafe (xBaseline is normal non-Unsafe array access to give a baseline figure)
    @Benchmark public int     unsafeArrayIntegerBaseline()         { return INT_ARRAY[0]; }
    @Benchmark public int     unsafeArrayInteger()                 { return UNSAFE.getInt(INT_ARRAY, Unsafe.ARRAY_INT_BASE_OFFSET); }
    @Benchmark public int     unsafeArrayIntegerStable()           { return UNSAFE.getIntStable(INT_ARRAY, Unsafe.ARRAY_INT_BASE_OFFSET); }
    @Benchmark public int     unsafeArrayIntegerStableVolatile()   { return UNSAFE.getIntStableVolatile(INT_ARRAY, Unsafe.ARRAY_INT_BASE_OFFSET); }

    @Benchmark
    public int sum() {
        int sum = 0;
        for (int i = 0; i < LENGTH; i++) {
            sum += INT_ARRAY[i];
        }
        return sum;
    }

    @Benchmark
    public int sumUnsafeStable() {
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
            sum += (int)INT_ARRAY_VH.getStable(INT_ARRAY, i);
        }
        return sum;
    }

    @Benchmark public long    unsafeArrayLongBaseline()            { return LONG_ARRAY[0]; }
    @Benchmark public long    unsafeArrayLong()                    { return UNSAFE.getLong(LONG_ARRAY, Unsafe.ARRAY_LONG_BASE_OFFSET); }
    @Benchmark public long    unsafeArrayLongStable()              { return UNSAFE.getLongStable(LONG_ARRAY, Unsafe.ARRAY_LONG_BASE_OFFSET); }
    @Benchmark public long    unsafeArrayLongStableVolatile()      { return UNSAFE.getLongStableVolatile(LONG_ARRAY, Unsafe.ARRAY_LONG_BASE_OFFSET); }
}
