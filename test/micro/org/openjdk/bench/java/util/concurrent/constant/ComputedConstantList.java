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
public class ComputedConstantList {

    private static final int SIZE = 10;
    private static final int POS = SIZE / 2;
    private static final IntFunction<Integer> MAPPER = i -> i;
    private static final IntFunction<Integer> NULL_MAPPER = i -> null;

    public List<ComputedConstant.OfSupplied<Integer>> listOfConstants;
    public List<ComputedConstant.OfSupplied<Integer>> listOfConstantsNull;
    public List<Integer> list;
    public List<Integer> intList;
    public List<VolatileDoubleChecked<Integer>> volatileDoubleChecked;

    private int value;

    private static VarHandle valueHandle() {
        try {
            return MethodHandles.lookup()
                    .findVarHandle(ComputedConstantList.class, "value", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
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
        listOfConstants = ComputedConstant.ofList(SIZE, MAPPER);;
        listOfConstantsNull = ComputedConstant.ofList(SIZE, MAPPER);
        volatileDoubleChecked = IntStream.range(0, SIZE)
                .mapToObj(i -> new VolatileDoubleChecked<>(i, MAPPER))
                .toList();
        list = ComputedConstant.Hidden.ofActualList(SIZE, MAPPER);
        intList = ComputedConstant.Hidden.ofActualList(int.class, SIZE, MAPPER);
    }

    @Benchmark
    public void ofConstant(MyState state, Blackhole bh) {
        bh.consume(listOfConstants.get(state.n).get());
    }

    @Benchmark
    public void ofConstantNull(MyState state, Blackhole bh) {
        bh.consume(listOfConstantsNull.get(state.n).get());
    }

    @Benchmark
    public void volatileDoubleChecked(MyState state, Blackhole bh) {
        bh.consume(volatileDoubleChecked.get(state.n).get());
    }

    @Benchmark
    public void list(MyState state, Blackhole bh) {
        bh.consume(list.get(state.n));
    }

    @Benchmark
    public void intList(MyState state, Blackhole bh) {
        bh.consume(intList.get(state.n));
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
