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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

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
public class JavaOneLazyBench {

    interface Lazy<T> extends Supplier<T> {

        static <T> Lazy<T> of(Supplier<? extends T> original) {
            return StableValue.supplier(original)::get;
        }

    }

    private static final Integer VALUE = 42;
    private static final Supplier<Integer> ORIGINAL = () -> {
        LockSupport.parkNanos(10);
        return VALUE;
    };

    // Static fields
    private static final Supplier<Integer> STABLE = StableValue.supplier(ORIGINAL);
    private static final Lazy<Integer> LAZY = Lazy.of(ORIGINAL);

    // Instance fields
    private final Supplier<Integer> stable = StableValue.supplier(ORIGINAL);
    private final Lazy<Integer> lazy = Lazy.of(ORIGINAL);

    record RecordHolder(Supplier<Integer> stable) {}

    static final class ClassHolder {
        private final Supplier<Integer> stable;

        public ClassHolder(Supplier<Integer> stable) {
            this.stable = stable;
        }

        public Supplier<Integer> stable() {
            return stable;
        }
    }

    // Holders
    private static final RecordHolder RECORD_HOLDER = new RecordHolder(StableValue.supplier(ORIGINAL));
    private static final ClassHolder CLASS_HOLDER = new ClassHolder(StableValue.supplier(ORIGINAL));

    // Super slow
    @Benchmark
    public int staticOriginal() {
        return ORIGINAL.get();
    }

    // Fast
    @Benchmark
    public int staticStable() {
        return STABLE.get();
    }

    // Fast
    @Benchmark
    public int staticLazy() {
        return LAZY.get();
    }

    // Slow
    @Benchmark
    public int stable() {
        return stable.get();
    }

    // Slow
    @Benchmark
    public int lazy() {
        return lazy.get();
    }

    // Fast (record)
    @Benchmark
    public int staticRecordHolder() {
        return RECORD_HOLDER.stable().get();
    }

    // Slow (normal class instance field)
    @Benchmark
    public int staticClassHolder() {
        return CLASS_HOLDER.stable().get();
    }


/*
    @Fork(jvmArgsPrepend = {
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:+TrustFinalNonStaticFields"
    })
    public static class TrustInstance extends JavaOneLazyBench {}
*/

/*

Benchmark                            Mode  Cnt     Score   Error  Units
JavaOneLazyBench.lazy                avgt    2     2.343          ns/op
JavaOneLazyBench.stable              avgt    2     2.050          ns/op
JavaOneLazyBench.staticClassHolder   avgt    2     2.163          ns/op
JavaOneLazyBench.staticLazy          avgt    2     0.692          ns/op
JavaOneLazyBench.staticOriginal      avgt    2  6503.419          ns/op
JavaOneLazyBench.staticRecordHolder  avgt    2     0.693          ns/op
JavaOneLazyBench.staticStable        avgt    2     0.734          ns/op

 */


}
