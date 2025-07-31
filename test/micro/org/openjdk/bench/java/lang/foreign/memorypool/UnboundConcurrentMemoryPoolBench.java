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

import jdk.internal.foreign.BufferStack;
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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryPool;
import java.lang.foreign.SegmentAllocator;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgs = {"--add-exports=java.base/jdk.internal.foreign=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED"})
public class UnboundConcurrentMemoryPoolBench {

    public static final MemoryPool CONCURRENT_POOL = MemoryPool.ofConcurrentUnbound();
    public static final MemoryPool THREAD_LOCAL_POOL = MemoryPool.ofThreadLocal();

    @Param({"5", "20", "100", "451"})
    //@Param({"20"})
    public int size;

    private BufferStack bufferStack;
    private MemoryPool stackPool;
    private ThreadLocal<SegmentAllocator> tlAllocator;

    @Setup
    public void setup() {
        bufferStack = BufferStack.of(size + 16);
        stackPool = MemoryPool.ofStack(size + 16);
        tlAllocator = ThreadLocal.withInitial(
                () -> SegmentAllocator.prefixAllocator(Arena.ofAuto().allocate(size)));
    }

    @Benchmark
    public long confined() {
        try (var arena = Arena.ofConfined()) {
            return arena.allocate(size).address();
        }
    }

 /*   @Benchmark
    public long concurrentPool() {
        try (var arena = CONCURRENT_POOL.get()) {
            return arena.allocate(size).address();
        }
    }

    @Benchmark
    public long threadLocalPool() {
        try (var arena = THREAD_LOCAL_POOL.get()) {
            return arena.allocate(size).address();
        }
    }*/

    @Benchmark
    public long bufferStack() {
        try (var arena = bufferStack.pushFrame(size)) {
            return arena.allocate(size)
                    .fill((byte) 0)
                    .address();
        }
    }

    @Benchmark
    public long stackPool() {
        try (var arena = stackPool.get()) {
            return arena.allocate(size)
                    .address();
        }
    }

/*    @Benchmark
    public long tlPrefix() {
        return tlAllocator.get().allocate(size)
                .fill((byte) 0)
                .address();
    }*/

    @Threads(8)   // Benchmark under contention
    public static class Contention8Threads extends UnboundConcurrentMemoryPoolBench {}

    @Fork(jvmArgsAppend = "-Djmh.executor=VIRTUAL")
    @Threads(8)   // Benchmark under contention with virtual threads
    public static class Contention8VirtualThreads extends UnboundConcurrentMemoryPoolBench {}

}
