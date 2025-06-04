package org.openjdk.bench.java.lang.foreign;

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorShape;
import jdk.incubator.vector.VectorSpecies;
import jdk.internal.misc.Unsafe;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgs = {"--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED", "--add-modules=jdk.incubator.vector"})
public class BinarySearchBench {

    static {
        System.out.println("SPECIES = " + VectorSpecies.ofPreferred(int.class));
    }

    static final Unsafe unsafe = Utils.unsafe;

    final static int CARRIER_SIZE = 4;
    final static int ALLOC_SIZE = CARRIER_SIZE * 1024;
    final static int ELEM_SIZE = ALLOC_SIZE / CARRIER_SIZE;

    Arena arena;
    MemorySegment segment;
    long address;

    int toFind = 42;

    @Setup
    public void setup() {
        address = unsafe.allocateMemory(ALLOC_SIZE);
        for (int i = 0; i < ELEM_SIZE; i++) {
            unsafe.putInt(address + (i * CARRIER_SIZE), i);
        }
        arena = Arena.ofConfined();
        segment = arena.allocate(ALLOC_SIZE, CARRIER_SIZE);
        for (int i = 0; i < ELEM_SIZE; i++) {
            segment.setAtIndex(ValueLayout.JAVA_INT_UNALIGNED, i, i);
        }
    }

    @TearDown
    public void tearDown() throws Throwable {
        unsafe.freeMemory(address);
        arena.close();
    }

    @Benchmark
    public long binarySearchSimpleUnsafe() {
        long lo = 0;
        long hi = ALLOC_SIZE;
        while (true) {
            long mid = (hi + lo) / 2;
            int curr = unsafe.getIntUnaligned(null, address + mid);
            if (curr == toFind) {
                return check(mid / 4, toFind);
            } else if (curr > toFind) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
    }

    @Benchmark
    public long binarySearchSimpleSegment() {
        long lo = 0;
        long hi = ALLOC_SIZE;
        while (true) {
            long mid = (hi + lo) / 2;
            int curr = segment.get(ValueLayout.JAVA_INT_UNALIGNED, mid);
            if (curr == toFind) {
                return check(mid / 4, toFind);
            } else if (curr > toFind) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
    }

    @Benchmark
    public long binarySearchBranchlessUnsafe() {
        long ret = 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 9)) <= toFind ? CARRIER_SIZE << 9 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 8)) <= toFind ? CARRIER_SIZE << 8 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 7)) <= toFind ? CARRIER_SIZE << 7 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 6)) <= toFind ? CARRIER_SIZE << 6 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 5)) <= toFind ? CARRIER_SIZE << 5 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 4)) <= toFind ? CARRIER_SIZE << 4 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 3)) <= toFind ? CARRIER_SIZE << 3 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 2)) <= toFind ? CARRIER_SIZE << 2 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 1)) <= toFind ? CARRIER_SIZE << 1 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + CARRIER_SIZE) <= toFind ? CARRIER_SIZE : 0;
        return check(ret / 4, toFind);
    }

    @Benchmark
    public long binarySearchBranchlessVectorUnsafe() {
        long ret = 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 9)) <= toFind ? CARRIER_SIZE << 9 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 8)) <= toFind ? CARRIER_SIZE << 8 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 7)) <= toFind ? CARRIER_SIZE << 7 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 6)) <= toFind ? CARRIER_SIZE << 6 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 5)) <= toFind ? CARRIER_SIZE << 5 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 4)) <= toFind ? CARRIER_SIZE << 4 : 0;
        ret += unsafe.getIntUnaligned(null, address + ret + (CARRIER_SIZE << 3)) <= toFind ? CARRIER_SIZE << 3 : 0;
        IntVector vector = IntVector.fromMemorySegment(SPECIES, MemorySegment.ofAddress(address).reinterpret(ALLOC_SIZE), ret, ByteOrder.nativeOrder());
        IntVector expected = IntVector.broadcast(SPECIES, toFind);
        var mask = vector.compare(VectorOperators.EQ, expected);
        return check(ret / 4 + mask.firstTrue(), toFind);
    }

    @Benchmark
    public long binarySearchBranchlessDirectSegment() {
        long ret = 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 9)) <= toFind ? CARRIER_SIZE << 9 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 8)) <= toFind ? CARRIER_SIZE << 8 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 7)) <= toFind ? CARRIER_SIZE << 7 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 6)) <= toFind ? CARRIER_SIZE << 6 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 5)) <= toFind ? CARRIER_SIZE << 5 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 4)) <= toFind ? CARRIER_SIZE << 4 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 3)) <= toFind ? CARRIER_SIZE << 3 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 2)) <= toFind ? CARRIER_SIZE << 2 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 1)) <= toFind ? CARRIER_SIZE << 1 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + CARRIER_SIZE) <= toFind ? CARRIER_SIZE : 0;
        return check(ret / 4, toFind);
    }

    @Benchmark
    public long binarySearchBranchlessSlicedSegment() {
        long ret = 0;
        MemorySegment segment = this.segment.asSlice(0, ALLOC_SIZE);
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 9)) <= toFind ? CARRIER_SIZE << 9 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 8)) <= toFind ? CARRIER_SIZE << 8 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 7)) <= toFind ? CARRIER_SIZE << 7 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 6)) <= toFind ? CARRIER_SIZE << 6 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 5)) <= toFind ? CARRIER_SIZE << 5 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 4)) <= toFind ? CARRIER_SIZE << 4 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 3)) <= toFind ? CARRIER_SIZE << 3 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 2)) <= toFind ? CARRIER_SIZE << 2 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 1)) <= toFind ? CARRIER_SIZE << 1 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + CARRIER_SIZE) <= toFind ? CARRIER_SIZE : 0;
        return check(ret / 4, toFind);
    }

    @Benchmark
    public long binarySearchBranchlessReinterpretedSegment() {
        long ret = 0;
        MemorySegment segment = this.segment.reinterpret(ALLOC_SIZE, Arena.global(), null);
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 9)) <= toFind ? CARRIER_SIZE << 9 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 8)) <= toFind ? CARRIER_SIZE << 8 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 7)) <= toFind ? CARRIER_SIZE << 7 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 6)) <= toFind ? CARRIER_SIZE << 6 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 5)) <= toFind ? CARRIER_SIZE << 5 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 4)) <= toFind ? CARRIER_SIZE << 4 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 3)) <= toFind ? CARRIER_SIZE << 3 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 2)) <= toFind ? CARRIER_SIZE << 2 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (CARRIER_SIZE << 1)) <= toFind ? CARRIER_SIZE << 1 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + CARRIER_SIZE) <= toFind ? CARRIER_SIZE : 0;
        return check(ret / 4, toFind);
    }

    final static VectorSpecies<Integer> SPECIES = VectorSpecies.of(int.class, VectorShape.forBitSize(32 * 8));

    @Benchmark
    public long binarySearchBranchlessVectorDirectSegment() {
        long ret = 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 9)) <= toFind ? 4 << 9 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 8)) <= toFind ? 4 << 8 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 7)) <= toFind ? 4 << 7 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 6)) <= toFind ? 4 << 6 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 5)) <= toFind ? 4 << 5 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 4)) <= toFind ? 4 << 4 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 3)) <= toFind ? 4 << 3 : 0;
        IntVector vector = IntVector.fromMemorySegment(SPECIES, segment, ret, ByteOrder.nativeOrder());
        IntVector expected = IntVector.broadcast(SPECIES, toFind);
        var mask = vector.compare(VectorOperators.EQ, expected);
        return check(ret / 4 + mask.firstTrue(), toFind);
    }

    @Benchmark
    public long binarySearchBranchlessVectorSlicedSegment() {
        long ret = 0;
        MemorySegment segment = this.segment.asSlice(0, ALLOC_SIZE);
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 9)) <= toFind ? 4 << 9 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 8)) <= toFind ? 4 << 8 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 7)) <= toFind ? 4 << 7 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 6)) <= toFind ? 4 << 6 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 5)) <= toFind ? 4 << 5 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 4)) <= toFind ? 4 << 4 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 3)) <= toFind ? 4 << 3 : 0;
        IntVector vector = IntVector.fromMemorySegment(SPECIES, segment, ret, ByteOrder.nativeOrder());
        IntVector expected = IntVector.broadcast(SPECIES, toFind);
        var mask = vector.compare(VectorOperators.EQ, expected);
        return check(ret / 4 + mask.firstTrue(), toFind);
    }

    @Benchmark
    public long binarySearchBranchlessVectorReinterpretedSegment() {
        long ret = 0;
        MemorySegment segment = this.segment.reinterpret(ALLOC_SIZE, Arena.global(), null);
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 9)) <= toFind ? 4 << 9 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 8)) <= toFind ? 4 << 8 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 7)) <= toFind ? 4 << 7 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 6)) <= toFind ? 4 << 6 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 5)) <= toFind ? 4 << 5 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 4)) <= toFind ? 4 << 4 : 0;
        ret += segment.get(ValueLayout.JAVA_INT_UNALIGNED, ret + (4 << 3)) <= toFind ? 4 << 3 : 0;
        IntVector vector = IntVector.fromMemorySegment(SPECIES, segment, ret, ByteOrder.nativeOrder());
        IntVector expected = IntVector.broadcast(SPECIES, toFind);
        var mask = vector.compare(VectorOperators.EQ, expected);
        return check(ret / 4 + mask.firstTrue(), toFind);
    }

    long check(long found, long expected) {
        if (found != expected) throw new AssertionError("Found: " + found + " Expected: " + expected);
        return found;
    }
}
