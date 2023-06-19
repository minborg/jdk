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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.lazy.LazyValue;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.IntStream;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value=3, jvmArgsAppend = "--enable-preview")
public class LazyArrayBench {

    private static final int SIZE = 10;
    private static final int POS = SIZE / 2;
    private static final IntFunction<Integer> MAPPER = i -> i;
    private static final IntFunction<Integer> NULL_MAPPER = i -> null;

    public static final List<LazyValue<Integer>> LAZY_LIST_OF_LAZY = LazyValue.ofListOfLazyValues(SIZE, MAPPER);
    public static final List<LazyValue<Integer>> LAZY_LIST_OF_LAZY_NULL = LazyValue.ofListOfLazyValues(SIZE, NULL_MAPPER);
    public static final List<Integer> LAZY_LIST = LazyValue.ofList(SIZE, MAPPER);
    public static final List<Integer> LAZY_INT_LIST = LazyValue.ofList(int.class, SIZE, MAPPER);
    public static final List<VolatileDoubleChecked<Integer>> LAZY_DC = IntStream.range(0, SIZE)
            .mapToObj(i -> new VolatileDoubleChecked<>(i, MAPPER))
            .toList();

    // Add chain

    public List<LazyValue<Integer>> lazyListOfLazy;
    public List<LazyValue<Integer>> lazyListOfLazyNull;
    public List<Integer> lazyList;
    public List<Integer> lazyIntList;
    public List<VolatileDoubleChecked<Integer>> volatileDoubleChecked;

    private int value;

    private static VarHandle valueHandle() {
        try {
            return MethodHandles.lookup()
                    .findVarHandle(LazyArrayBench.class, "value", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final VarHandle VALUE_HANDLE = valueHandle();
    private static final List<LazyValue<VarHandle>> LAZY_VALUE_HANDLE_LIST_OF_LAZY = LazyValue.ofListOfLazyValues(SIZE, i -> LazyArrayBench.valueHandle());
    private static final List<VarHandle> LAZY_VALUE_HANDLE_LIST = LazyValue.ofList(SIZE, i -> LazyArrayBench.valueHandle());

    private static final Map<Integer, Integer> FIB_MAP = new ConcurrentHashMap<>();
    private static final List<LazyValue<Integer>> FIB_LAZY_ARRAY = LazyValue.ofListOfLazyValues(20, LazyArrayBench::fibArrayFunction);
    private static final Map<Integer, LazyValue<Integer>> FIB_LAZY_MAP = LazyValue.ofMapOfLazyValues(IntStream.range(0, 20).boxed().toList(), LazyArrayBench::fibMapFunction);

    private static final List<Integer> SET_OF_0_to_999 = IntStream.range(0, 1000).boxed().toList();

    private static int fibArrayFunction(int n) {
        return (n < 2)
                ? n
                : FIB_LAZY_ARRAY.get(n - 1).get() + FIB_LAZY_ARRAY.get(n - 2).get();
    }

    private static int fibMapFunction(int n) {
        return (n < 2)
                ? n
                : FIB_LAZY_MAP.get(n - 1).get() + FIB_LAZY_MAP.get(n - 2).get();
    }

    private static int fibArray(int n) {
        return (n < 2)
                ? n
                : FIB_LAZY_ARRAY.get(n).get();
    }

    private static int fibLazyMap(int n) {
        return (n < 2)
                ? n
                : FIB_LAZY_MAP.get(n).get();
    }

    private static int fibMap(int n) {
        return (n < 2)
                ? n
                : FIB_MAP.computeIfAbsent(n, nk -> fibMap(nk - 1) + fibMap(nk - 2) );
    }

    @State(Scope.Thread)
    public static class MyState {
        public int n = POS;
    }

    /**
     * The test variables are allocated every iteration so you can assume
     * they are initialized to get similar behaviour across iterations
     */
    @Setup(Level.Iteration)
    public void setupIteration() {
        lazyListOfLazy = LazyValue.ofListOfLazyValues(SIZE, MAPPER);;
        lazyListOfLazyNull = LazyValue.ofListOfLazyValues(SIZE, MAPPER);
        // threadUnsafe = new ThreadUnsafe<>(SUPPLIER);
        volatileDoubleChecked = IntStream.range(0, SIZE)
                .mapToObj(i -> new VolatileDoubleChecked<>(i, MAPPER))
                .toList();
        lazyList = LazyValue.ofList(SIZE, MAPPER);
        lazyIntList = LazyValue.ofList(int.class, SIZE, MAPPER);
    }

    @Benchmark
    public void createListOfLazyValues1000(Blackhole bh) {
        bh.consume(LazyValue.ofListOfLazyValues(1000, MAPPER));
    }

    @Benchmark
    public void createList1000(Blackhole bh) {
        bh.consume(LazyValue.ofList(1000, MAPPER));
    }

    @Benchmark
    public void createIntList1000(Blackhole bh) {
        bh.consume(LazyValue.ofList(int.class, 1000, MAPPER));
    }

    @Benchmark
    public void createMapOf1000(Blackhole bh) {
        bh.consume(LazyValue.<Integer, Integer>ofMapOfLazyValues(SET_OF_0_to_999, Function.identity()));
    }

    @Benchmark
    public void staticLazyListOfLazy(Blackhole bh) {
        bh.consume(LAZY_LIST_OF_LAZY.get(POS).get());
    }

    @Benchmark
    public void staticLazyList(Blackhole bh) {
        bh.consume(LAZY_LIST.get(POS));
    }

    @Benchmark
    public void staticIntLazyList(Blackhole bh) {
        bh.consume(LAZY_INT_LIST.get(POS));
    }

    @Benchmark
    public void staticLazyListOfLazyNull(Blackhole bh) {
        bh.consume(LAZY_LIST_OF_LAZY_NULL.get(POS).get());
    }

    @Benchmark
    public void staticLocalClass(Blackhole bh) {
        class Lazy {
            private static final int[] INT = IntStream.range(0, SIZE).toArray();
        }
        bh.consume(Lazy.INT[POS]);
    }

    @Benchmark
    public void staticVolatileDoubleChecked(Blackhole bh) {
        bh.consume(LAZY_DC.get(POS).get());
    }

    @Benchmark
    public void lazyListOfLazy(MyState state, Blackhole bh) {
        bh.consume(lazyListOfLazy.get(state.n).get());
    }

    @Benchmark
    public void lazyListOfLazyNull(MyState state, Blackhole bh) {
        bh.consume(lazyListOfLazyNull.get(state.n).get());
    }

    @Benchmark
    public void volatileDoubleChecked(MyState state, Blackhole bh) {
        bh.consume(volatileDoubleChecked.get(state.n).get());
    }

    @Benchmark
    public void lazyList(MyState state, Blackhole bh) {
        bh.consume(lazyList.get(state.n));
    }

    @Benchmark
    public void lazyIntList(MyState state, Blackhole bh) {
        bh.consume(lazyIntList.get(state.n));
    }

    @Benchmark
    public void fibConcurrentMap(MyState state, Blackhole bh) {
        bh.consume(fibMap(state.n));
    }

    @Benchmark
    public void fibLazyArray(MyState state, Blackhole bh) {
        bh.consume(fibArray(state.n));
    }

    @Benchmark
    public void fibLazyMap(MyState state, Blackhole bh) {
        bh.consume(fibLazyMap(state.n));
    }

    @Benchmark
    public void methodHandle(Blackhole bh) {
        bh.consume((int) VALUE_HANDLE.get(this));
    }

    @Benchmark
    public void methodHandleListOfLazy(Blackhole bh) {
        bh.consume((int) LAZY_VALUE_HANDLE_LIST_OF_LAZY.get(POS).get().get(this));
    }

    @Benchmark
    public void methodHandleList(Blackhole bh) {
        bh.consume((int) LAZY_VALUE_HANDLE_LIST.get(POS).get(this));
    }

    private static final class VolatileDoubleChecked<T> implements Supplier<T> {

        private IntFunction<? extends T> supplier;
        private final int index;
        private volatile T value;


        public VolatileDoubleChecked(int index, IntFunction<? extends T> supplier) {
            this.supplier = supplier;
            this.index = index;
        }

        @Override
        public T get() {
            T v = value;
            if (v == null) {
                synchronized (this) {
                    v = value;
                    if (v == null) {
                        v = supplier.apply(index);
                        if (v == null) {
                            throw new NullPointerException();
                        }
                        value = v;
                        supplier = null;
                    }
                }
            }
            return v;
        }
    }

}
