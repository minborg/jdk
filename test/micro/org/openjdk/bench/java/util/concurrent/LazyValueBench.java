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
import org.openjdk.jmh.annotations.OperationsPerInvocation;
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
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.lazy.LazyValue;
import java.util.function.Supplier;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value=3, jvmArgsAppend = "--enable-preview")
public class LazyValueBench {

    private static final Supplier<Integer> SUPPLIER = () -> 2 << 16;
    private static final Supplier<Integer> NULL_SUPPLIER = () -> null;

    public static final Supplier<Integer> LAZY = LazyValue.of(SUPPLIER);
    public static final Supplier<Integer> LAZY_NULL = LazyValue.of(NULL_SUPPLIER);
    public static final Supplier<Integer> LAZY_DC = new VolatileDoubleChecked<>(SUPPLIER);

    // Add chain

    public Supplier<Integer> lazy;
    public Supplier<Integer> lazyNull;
    public Supplier<Integer> volatileDoubleChecked;

    private int value;

    private static VarHandle valueHandle() {
        try {
            return MethodHandles.lookup()
                    .findVarHandle(org.openjdk.bench.java.util.concurrent.LazyValueBench.class, "value", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final VarHandle VALUE_HANDLE = valueHandle();
    private static final LazyValue<VarHandle> LAZY_VALUE_HANDLE = LazyValue.of(LazyValueBench::valueHandle);

    private static final Map<Integer, Integer> FIB_MAP = new ConcurrentHashMap<>();
    private static final List<LazyValue<Integer>> FIB_LAZY_ARRAY = LazyValue.ofListOfLazyValues(20, LazyValueBench::fibArrayFunction);

    private static int fibArrayFunction(int n) {
        return (n < 2)
                ? n
                : FIB_LAZY_ARRAY.get(n - 1).get() + FIB_LAZY_ARRAY.get(n - 2).get();
    }

    private static int fibArray(int n) {
        return (n < 2)
                ? n
                : FIB_LAZY_ARRAY.get(n).get();
    }

    private static int fibMap(int n) {
        return (n < 2)
                ? n
                : FIB_MAP.computeIfAbsent(n, nk -> fibMap(nk - 1) + fibMap(nk - 2) );
    }

    @State(Scope.Thread)
    public static class MyState {
        public int n = 10;
    }

    /**
     * The test variables are allocated every iteration so you can assume
     * they are initialized to get similar behaviour across iterations
     */
    @Setup(Level.Iteration)
    public void setupIteration() {
        lazy = LazyValue.of(SUPPLIER);
        lazyNull = LazyValue.of(NULL_SUPPLIER);
        // threadUnsafe = new ThreadUnsafe<>(SUPPLIER);
        volatileDoubleChecked = new VolatileDoubleChecked<>(SUPPLIER);
/*        volatileVhDoubleChecked = new VolatileVhDoubleChecked<>(SUPPLIER);
        acquireReleaseDoubleChecked = new AquireReleaseDoubleChecked<>(SUPPLIER);
        delegated = new DelegatorLazy<>(SUPPLIER);*/
    }

    @Benchmark
    public void staticLazyRef(Blackhole bh) {
        bh.consume(LAZY.get());
    }

    @Benchmark
    public void staticLazyRefNull(Blackhole bh) {
        bh.consume(LAZY_NULL.get());
    }

    @Benchmark
    public void staticLocalClass(Blackhole bh) {
        class Lazy {
            private static final int INT = SUPPLIER.get();
        }
        bh.consume(Lazy.INT);
    }

    @Benchmark
    public void staticVolatileDoubleChecked(Blackhole bh) {
        bh.consume(LAZY_DC.get());
    }

    @Benchmark
    public void lazyRef(Blackhole bh) {
        bh.consume(lazy.get());
    }

    @Benchmark
    public void lazyRefNull(Blackhole bh) {
        bh.consume(lazyNull.get());
    }

    @Benchmark
    public void volatileDoubleChecked(Blackhole bh) {
        bh.consume(volatileDoubleChecked.get());
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
    public void methodHandle(Blackhole bh) {
        bh.consume((int) VALUE_HANDLE.get(this));
    }

    @Benchmark
    public void methodHandleLazy(Blackhole bh) {
        bh.consume((int) LAZY_VALUE_HANDLE.get().get(this));
    }


    private static final class DelegatorLazy<T> implements Supplier<T> {

        private final Supplier<T> original;

        public DelegatorLazy(Supplier<T> supplier) {
            this.original = Objects.requireNonNull(supplier);
        }

        Supplier<T> delegate = this::firstTime;
        boolean initialized;

        public T get() {
            return delegate.get();
        }

        private synchronized T firstTime() {
            if (!initialized) {
                T value = original.get();
                delegate = () -> value;
                initialized = true;
            }
            return delegate.get();
        }
    }

    private static final class VolatileDoubleChecked<T> implements Supplier<T> {

        private Supplier<? extends T> supplier;

        private volatile T value;

        public VolatileDoubleChecked(Supplier<? extends T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public T get() {
            T v = value;
            if (v == null) {
                synchronized (this) {
                    v = value;
                    if (v == null) {
                        v = supplier.get();
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

    private static final class VolatileVhDoubleChecked<T> implements Supplier<T> {

        private Supplier<? extends T> supplier;

        private T value;

        static final VarHandle VALUE_VH;

        static {
            try {
                VALUE_VH = MethodHandles.lookup()
                        .findVarHandle(VolatileVhDoubleChecked.class, "value", Object.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        public VolatileVhDoubleChecked(Supplier<? extends T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public T get() {
            T v = getVolatile();
            if (v == null) {
                synchronized (this) {
                    v = getVolatile();
                    if (v == null) {
                        v = supplier.get();
                        if (v == null) {
                            throw new NullPointerException();
                        }
                        setVolatile(v);
                        supplier = null;
                    }
                }
            }
            return v;
        }

        T getVolatile() {
            return (T) VALUE_VH.getVolatile(this);
        }

        void setVolatile(Object value) {
            VALUE_VH.setVolatile(this, value);
        }

    }

    private static final class AquireReleaseDoubleChecked<T> implements Supplier<T> {

        private Supplier<? extends T> supplier;

        private Object value;

        static final VarHandle VALUE_VH;

        static {
            try {
                VALUE_VH = MethodHandles.lookup()
                        .findVarHandle(AquireReleaseDoubleChecked.class, "value", Object.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        public AquireReleaseDoubleChecked(Supplier<? extends T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public T get() {
            T value = getAcquire();
            if (value == null) {
                synchronized (this) {
                    value = getAcquire();
                    if (value == null) {
                        if (supplier == null) {
                            throw new IllegalArgumentException("No pre-set supplier specified.");
                        }
                        value = supplier.get();
                        if (value == null) {
                            throw new NullPointerException("The supplier returned null: " + supplier);
                        }
                        setRelease(value);
                        supplier = null;
                    }
                }
            }
            return value;
        }

        T getAcquire() {
            return (T) VALUE_VH.getAcquire(this);
        }

        void setRelease(Object value) {
            VALUE_VH.setRelease(this, value);
        }

    }

}
