/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.java.util.concurrent;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.lazy.LazyValue;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3, jvmArgsAppend = "--enable-preview")
public class LazyCreate {

    private static final IntFunction<Integer> MAPPER = i -> i;
    private static final List<Integer> SET_OF_0_to_999 = IntStream.range(0, 1000).boxed().toList();

    @Benchmark
    public void create1000(Blackhole bh) {
        for (int i = 0; i < 1000; i++) {
            bh.consume(LazyValue.of(MAPPER));
        }
    }

    @Benchmark
    public void create100(Blackhole bh) {
        for (int i = 0; i < 100; i++) {
            bh.consume(LazyValue.of(MAPPER));
        }
    }

    @Benchmark
    public void createListOfLazyValues1000(Blackhole bh) {
        bh.consume(LazyValue.ofListOfLazyValues(1000, MAPPER));
    }

    @Benchmark
    public void createListOfLazyValues100(Blackhole bh) {
        bh.consume(LazyValue.ofListOfLazyValues(100, MAPPER));
    }

    @Benchmark
    public void createList1000(Blackhole bh) {
        bh.consume(LazyValue.ofList(1000, MAPPER));
    }

    @Benchmark
    public void createList100(Blackhole bh) {
        bh.consume(LazyValue.ofList(100, MAPPER));
    }

    @Benchmark
    public void createIntList1000(Blackhole bh) {
        bh.consume(LazyValue.ofList(int.class, 1000, MAPPER));
    }

    @Benchmark
    public void createMapOf1000(Blackhole bh) {
        bh.consume(LazyValue.ofMapOfLazyValues(SET_OF_0_to_999, Function.identity()));
    }
}
