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

import org.openjdk.jmh.annotations.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark measuring unbound stable list performance
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark) // Share the same state instance (for contention)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(value = 2, jvmArgsAppend = {
        "--enable-preview"
})
@Threads(Threads.MAX)   // Benchmark under contention
public class UnboundedStableMapBenchmark {

    private static final int SIZE = 30;

    private static final Map<Integer, Integer> STABLE = unboundedStableList();
    private static final Map<Integer, Integer> CHM = new ConcurrentHashMap<>(STABLE);
    private final Map<Integer, Integer> stable = unboundedStableList();

    @Benchmark
    public int map() {
        return stable.get(1);
    }

    @Benchmark
    public int staticCHM() {
        return CHM.get(1);
    }

    @Benchmark
    public int staticMap() {
        return STABLE.get(1);
    }

    @Benchmark
    public int staticSize() {
        return STABLE.size();
    }

    private static Map<Integer, Integer> unboundedStableList() {
        Map<Integer, Integer> map = Map.<Integer, Integer>ofStable()
                .toMap();
        for (int i = 0; i < SIZE; i++) {
            map.put(i, i);
        }
        return map;
    }

}
