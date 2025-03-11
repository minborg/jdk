/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark measuring custom stable value types
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark) // Share the same state instance (for contention)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 1, time = 1)
@Fork(value = 2, jvmArgsAppend = {
        "--enable-preview"
})
@Threads(Threads.MAX)   // Benchmark under contention
public class JavaOneFib {

    private static final int MAX = 46; // fib(46) just about fits in an `int`

    private static final List<Integer> STABLE_LIST =
            StableValue.list(MAX, JavaOneFib::stableFib);

    /**
     * {@return Fibonacci element of i}
     * @param i element
     */
    public static int stableFib(int i) { // `i` positive
        return (i < 2)
                ? i
                : STABLE_LIST.get(i - 1) + STABLE_LIST.get(i - 2);
    }

    @Benchmark
    public int stable() {
        return STABLE_LIST.get(10);
    }






    private static final Map<Integer, Integer> CONCURRENT_MAP = new ConcurrentHashMap<>();

    @Benchmark
    public int map() {
        return CONCURRENT_MAP.computeIfAbsent(10, JavaOneFib::mapFib);
    }

    public static int mapFib(int i) { // `i` positive
        return (i < 2)
                ? i
                : CONCURRENT_MAP.computeIfAbsent(i - 1, JavaOneFib::mapFib) +
                  CONCURRENT_MAP.computeIfAbsent(i - 2, JavaOneFib::mapFib);
    }


}
