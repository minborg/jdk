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

package org.openjdk.bench.java.lang.foreign.memorypool;

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
import java.lang.foreign.MemoryPool;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgs = {"--add-exports=java.base/jdk.internal.foreign=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED"})
public class MemoryPoolBench {

    public static final MemoryPool POOL = MemoryPool.ofStacked(1 << 10);
    public static final MemoryPool POOL_SINGLE_THREADED = MemoryPool.ofStackedSingleThreadedOrWhateverItShallBeCalled(1 << 10);

    @Param({"5", "20", "100", "451"})
    public int size;

    private SegmentAllocator allocator;
    private byte[] array;

    @Setup
    public void setup() {
        allocator = SegmentAllocator.prefixAllocator(Arena.ofAuto().allocate(size));
        array = new byte[size];
    }

    @Benchmark
    public long confined() {
        try (var arena = Arena.ofConfined()) {
            return arena.allocate(size).address();
        }
    }

    @Benchmark
    public long confinedFrom() {
        try (var arena = Arena.ofConfined()) {
            return arena.allocateFrom(ValueLayout.JAVA_BYTE, array).address();
        }
    }

    @Benchmark
    public long pool() {
        try (var arena = POOL.get()) {
            return arena.allocate(size).address();
        }
    }

    @Benchmark
    public long poolFrom() {
        try (var arena = POOL.get()) {
            return arena.allocateFrom(ValueLayout.JAVA_BYTE, array).address();
        }
    }

    @Benchmark
    public long poolSingleThreaded() {
        try (var arena = POOL_SINGLE_THREADED.get()) {
            return arena.allocate(size).address();
        }
    }

    @Benchmark
    public long prefix() {
        return allocator.allocate(size)
                .fill((byte)0).address();
    }

    @Fork(jvmArgsAppend = "-Djmh.executor=VIRTUAL")
    public static class VirtualThread extends MemoryPoolBench {}

}
