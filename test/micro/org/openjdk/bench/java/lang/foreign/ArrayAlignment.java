package org.openjdk.bench.java.lang.foreign;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.TimeUnit;

import static java.lang.foreign.MemoryLayout.sequenceLayout;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3)
@OperationsPerInvocation(30_000_000)
public class ArrayAlignment {
    // base offset = 0
    static final VarHandle ALIGNED = MethodHandles.insertCoordinates(JAVA_LONG.arrayElementVarHandle(), 1, 0).withInvokeExactBehavior();
    static final VarHandle UNALIGNED = MethodHandles.insertCoordinates(JAVA_LONG_UNALIGNED.arrayElementVarHandle(), 1, 0).withInvokeExactBehavior();
    // base offset = 8
    static final VarHandle NEXT_ALIGNED = MethodHandles.insertCoordinates(JAVA_LONG.arrayElementVarHandle(), 1, JAVA_LONG.byteSize()).withInvokeExactBehavior();
    static final VarHandle NEXT_UNALIGNED = MethodHandles.insertCoordinates(JAVA_LONG_UNALIGNED.arrayElementVarHandle(), 1, JAVA_LONG.byteSize()).withInvokeExactBehavior();

    static final long HAYSTACK = 30_000_000L;
    static final long NEEDLE_INDEX = 934L;
    static final long NEEDLE = 456L;

    static final MemorySegment segment = Arena.global().allocate(sequenceLayout(HAYSTACK, JAVA_LONG));

    static {
        ALIGNED.set(segment, NEEDLE_INDEX, NEEDLE);
    }

    // 6 different ways to loop over segment[1, size):

    @Benchmark
    public int findUnaligned() {
        long size = segment.byteSize() / JAVA_LONG.byteSize();
        int numFound = 0;
        for (long i = 1; i < size; ++i) {
            if ((long) UNALIGNED.get(segment, i) == NEEDLE) ++numFound;
        }
        return numFound;
    }

    @Benchmark
    public int findAligned() {
        long size = segment.byteSize() / JAVA_LONG.byteSize();
        int numFound = 0;
        for (long i = 1; i < size; ++i) {
            if ((long) ALIGNED.get(segment, i) == NEEDLE) ++numFound;
        }
        return numFound;
    }

    @Benchmark
    public int findUnalignedPlusOne() {
        long size = segment.byteSize() / JAVA_LONG.byteSize();
        int numFound = 0;
        for (long i = 0; i < size - 1; ++i) {
            if ((long) UNALIGNED.get(segment, i + 1) == NEEDLE) ++numFound;
        }
        return numFound;
    }

    @Benchmark
    public int findAlignedPlusOne() {
        long size = segment.byteSize() / JAVA_LONG.byteSize();
        int numFound = 0;
        for (long i = 0; i < size - 1; ++i) {
            if ((long) ALIGNED.get(segment, i + 1) == NEEDLE) ++numFound;
        }
        return numFound;
    }

    @Benchmark
    public int findUnalignedNext() {
        long size = segment.byteSize() / JAVA_LONG.byteSize();
        int numFound = 0;
        for (long i = 0; i < size - 1; ++i) {
            if ((long) NEXT_UNALIGNED.get(segment, i) == NEEDLE) ++numFound;
        }
        return numFound;
    }

    @Benchmark
    public int findAlignedNext() {
        long size = segment.byteSize() / JAVA_LONG.byteSize();
        int numFound = 0;
        for (long i = 0; i < size - 1; ++i) {
            if ((long) NEXT_ALIGNED.get(segment, i) == NEEDLE) ++numFound;
        }
        return numFound;
    }

    // Base reference

    @Benchmark
    public int baseAligned() {
        long size = segment.byteSize() / JAVA_LONG.byteSize();
        int numFound = 0;
        for (long i = 0; i < size - 1; ++i) {
            if (segment.get(JAVA_LONG, i * JAVA_LONG.byteSize()) == NEEDLE) ++numFound;
        }
        return numFound;
    }

}