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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.lazy.LazyLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class LazyLongBench {

    private static final LongSupplier SUPPLIER = () -> 42;

    public LongSupplier lazy;

    public LongSupplier threadUnsafe;

    /**
     * The test variables are allocated every iteration so you can assume
     * they are initialized to get similar behaviour across iterations
     */
    @Setup(Level.Iteration)
    public void setupIteration() {
        lazy = LazyLong.of(SUPPLIER);
        threadUnsafe = new ThreadUnsafe(SUPPLIER);
    }

    @Benchmark
    public void lazyLong(Blackhole bh) {
        bh.consume(lazy.getAsLong());
    }

    @Benchmark
    public void threadUnsafe(Blackhole bh) {
        bh.consume(threadUnsafe.getAsLong());
    }

    private static final class ThreadUnsafe implements LongSupplier {

        private LongSupplier supplier;

        private long value;
        private boolean present;

        public ThreadUnsafe(LongSupplier supplier) {
            this.supplier = supplier;
        }

        @Override
        public long getAsLong() {
            if (!present) {
                value = supplier.getAsLong();
                present = true;
                supplier = null;
            }
            return value;
        }
    }

}
