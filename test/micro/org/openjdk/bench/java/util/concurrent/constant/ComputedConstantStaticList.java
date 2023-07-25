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

package org.openjdk.bench.java.util.concurrent.constant;

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
import java.util.concurrent.constant.ComputedConstant;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.IntStream;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value=3, jvmArgsAppend = "--enable-preview")
public class ComputedConstantStaticList {

    private static final int SIZE = 10;
    private static final int POS = SIZE / 2;
    private static final IntFunction<Integer> MAPPER = i -> i;
    private static final IntFunction<Integer> NULL_MAPPER = i -> null;

    public static final List<ComputedConstant.OfSupplied<Integer>> LIST_OF_CONSTANTS = ComputedConstant.ofList(SIZE, MAPPER);
    public static final List<ComputedConstant.OfSupplied<Integer>> LIST_OF_CONSTANTS_NULL = ComputedConstant.ofList(SIZE, NULL_MAPPER);
    public static final List<Integer> LIST = ComputedConstant.Hidden.ofActualList(SIZE, MAPPER);
    public static final List<Integer> INT_LIST = ComputedConstant.Hidden.ofActualList(int.class, SIZE, MAPPER);
    public static final List<VolatileDoubleChecked<Integer>> DC = IntStream.range(0, SIZE)
            .mapToObj(i -> new VolatileDoubleChecked<>(i, MAPPER))
            .toList();

    private int value;

    private static VarHandle valueHandle() {
        try {
            return MethodHandles.lookup()
                    .findVarHandle(ComputedConstantStaticList.class, "value", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final VarHandle VALUE_HANDLE = valueHandle();
    private static final List<ComputedConstant.OfSupplied<VarHandle>> VALUE_HANDLE_LIST_OF_CONSTANT = ComputedConstant.ofList(SIZE, i -> org.openjdk.bench.java.util.concurrent.constant.ComputedConstantStaticList.valueHandle());
    private static final List<VarHandle> VALUE_HANDLE_LIST = ComputedConstant.Hidden.ofActualList(SIZE, i -> org.openjdk.bench.java.util.concurrent.constant.ComputedConstantStaticList.valueHandle());

    private static final Map<Integer, Integer> FIB_MAP = new ConcurrentHashMap<>();
    private static final List<ComputedConstant.OfSupplied<Integer>> FIB_LIST_CONSTANTS = ComputedConstant.ofList(20, org.openjdk.bench.java.util.concurrent.constant.ComputedConstantStaticList::fibArrayFunction);
    private static final Map<Integer, ComputedConstant.OfSupplied<Integer>> FIB_MAP_CONSTANTS = ComputedConstant.ofMap(IntStream.range(0, 20).boxed().toList(), org.openjdk.bench.java.util.concurrent.constant.ComputedConstantStaticList::fibMapFunction);

    private static int fibArrayFunction(int n) {
        return (n < 2)
                ? n
                : FIB_LIST_CONSTANTS.get(n - 1).get() + FIB_LIST_CONSTANTS.get(n - 2).get();
    }

    private static int fibMapFunction(int n) {
        return (n < 2)
                ? n
                : FIB_MAP_CONSTANTS.get(n - 1).get() + FIB_MAP_CONSTANTS.get(n - 2).get();
    }

    private static int fibArray(int n) {
        return (n < 2)
                ? n
                : FIB_LIST_CONSTANTS.get(n).get();
    }

    private static int fibConstantLazyMap(int n) {
        return (n < 2)
                ? n
                : FIB_MAP_CONSTANTS.get(n).get();
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

    @Benchmark
    public void listOfConstants(Blackhole bh) {
        bh.consume(LIST_OF_CONSTANTS.get(POS).get());
    }

    @Benchmark
    public void list(Blackhole bh) {
        bh.consume(LIST.get(POS));
    }

    @Benchmark
    public void intList(Blackhole bh) {
        bh.consume(INT_LIST.get(POS));
    }

    @Benchmark
    public void listOfConstantsNull(Blackhole bh) {
        bh.consume(LIST_OF_CONSTANTS_NULL.get(POS).get());
    }

    @Benchmark
    public void localClass(Blackhole bh) {
        class Lazy {
            private static final int[] INT = IntStream.range(0, SIZE).toArray();
        }
        bh.consume(Lazy.INT[POS]);
    }

    @Benchmark
    public void volatileDoubleChecked(Blackhole bh) {
        bh.consume(DC.get(POS).get());
    }

    @Benchmark
    public void fibConcurrentMap(Blackhole bh) {
        bh.consume(fibMap(POS));
    }

    @Benchmark
    public void fibConstantList(Blackhole bh) {
        bh.consume(fibArray(POS));
    }

    @Benchmark
    public void fibConstantMap(Blackhole bh) {
        bh.consume(fibConstantLazyMap(POS));
    }

    @Benchmark
    public void methodHandle(Blackhole bh) {
        bh.consume((int) VALUE_HANDLE.get(this));
    }

    @Benchmark
    public void valueHandleListOfConstant(Blackhole bh) {
        bh.consume((int) VALUE_HANDLE_LIST_OF_CONSTANT.get(POS).get().get(this));
    }

    @Benchmark
    public void methodHandleList(Blackhole bh) {
        bh.consume((int) VALUE_HANDLE_LIST.get(POS).get(this));
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
