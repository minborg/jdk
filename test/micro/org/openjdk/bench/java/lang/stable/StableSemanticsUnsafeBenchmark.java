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

import jdk.internal.misc.Unsafe;
import org.openjdk.jmh.annotations.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;

import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Benchmark measuring stable semantics access performance
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark) // Share the same state instance (for contention)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 3, time = 2)
@Fork(value = 2, jvmArgs = {
        "--enable-native-access=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"})
public class StableSemanticsUnsafeBenchmark {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private static final MethodHandle INT_IDENTITY_MH = MethodHandles.identity(int.class);

    private static final int INT_VALUE = 42;
    private static final long LONG_VALUE = 42L;

    private static final VarHandle INT_VALUE_VH;
    private static final VarHandle LONG_VALUE_VH;

    static {
        try {
            INT_VALUE_VH = LOOKUP.findStaticVarHandle(StableSemanticsUnsafeBenchmark.class, "INT_VALUE", int.class);
            LONG_VALUE_VH = LOOKUP.findStaticVarHandle(StableSemanticsUnsafeBenchmark.class, "LONG_VALUE", long.class);
        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }

    private static final int[] INT_ARRAY = new int[]{1};
    private static final long[] LONG_ARRAY = new long[]{1L};

    private static final MethodHandlerHolder INT_MH_HOLDER = new MethodHandlerHolder(INT_IDENTITY_MH);

    private static final ByteHolder BYTE_HOLDER = new ByteHolder();
    private static final StableByteHolder STABLE_BYTE_HOLDER = new StableByteHolder();
    private static final Map<ByteHolder, Byte> BYTE_MAP = Map.of(STABLE_BYTE_HOLDER, STABLE_BYTE_HOLDER.value);

    private static final ShortHolder SHORT_HOLDER = new ShortHolder();
    private static final StableShortHolder STABLE_SHORT_HOLDER = new StableShortHolder();
    private static final Map<ShortHolder, Short> SHORT_MAP = Map.of(STABLE_SHORT_HOLDER, STABLE_SHORT_HOLDER.value);

    private static final CharHolder CHAR_HOLDER = new CharHolder();
    private static final StableCharHolder STABLE_CHAR_HOLDER = new StableCharHolder();
    private static final Map<CharHolder, Character> CHAR_MAP = Map.of(STABLE_CHAR_HOLDER, STABLE_CHAR_HOLDER.value);

    private static final IntHolder INT_HOLDER = new IntHolder();
    private static final StableIntHolder STABLE_INT_HOLDER = new StableIntHolder();
    private static final StableVolatileIntHolder STABLE_VOLATILE_INT_HOLDER = new StableVolatileIntHolder();
    private static final Map<IntHolder, Integer> INT_MAP = Map.of(STABLE_INT_HOLDER, STABLE_INT_HOLDER.value);
    private static final Map<IntHolder, Integer> INT_VOLATILE_MAP = Map.of(STABLE_VOLATILE_INT_HOLDER, STABLE_VOLATILE_INT_HOLDER.value);

    private static final LongHolder LONG_HOLDER = new LongHolder();
    private static final StableLongHolder STABLE_LONG_HOLDER = new StableLongHolder();
    private static final Map<LongHolder, Long> LONG_MAP = Map.of(STABLE_LONG_HOLDER, STABLE_LONG_HOLDER.value);

    private static final FloatHolder FLOAT_HOLDER = new FloatHolder();
    private static final StableFloatHolder STABLE_FLOAT_HOLDER = new StableFloatHolder();
    private static final Map<FloatHolder, Float> FLOAT_MAP = Map.of(STABLE_FLOAT_HOLDER, STABLE_FLOAT_HOLDER.value);

    private static final DoubleHolder DOUBLE_HOLDER = new DoubleHolder();
    private static final StableDoubleHolder STABLE_DOUBLE_HOLDER = new StableDoubleHolder();
    private static final Map<DoubleHolder, Double> DOUBLE_MAP = Map.of(STABLE_DOUBLE_HOLDER, STABLE_DOUBLE_HOLDER.value);


    private static final SegmentIntHolder SEGMENT_INT_HOLDER = new SegmentIntHolder();
    private static final StableSegmentIntHolder STABLE_SEGMENT_INT_HOLDER = new StableSegmentIntHolder();
    private static final Map<SegmentIntHolder, Integer> SEGMENT_INT_MAP = Map.of(STABLE_SEGMENT_INT_HOLDER, STABLE_SEGMENT_INT_HOLDER.value());

    MethodHandle intIdentityMH = INT_IDENTITY_MH;

    private static final AtomicReference<?> ATOMIC_REFERENCE = new AtomicReference<>(new Object());
    private static final AtomicBoolean ATOMIC_BOOLEAN = new AtomicBoolean(true);
    private static final AtomicInteger ATOMIC_INTEGER = new AtomicInteger(1);
    private static final AtomicLong ATOMIC_LONG = new AtomicLong(1L);

    private static final AtomicReferenceArray<?> ATOMIC_REFERENCE_ARRAY = new AtomicReferenceArray<>(new Object[]{new Object()});
    private static final AtomicIntegerArray ATOMIC_INTEGER_ARRAY = new AtomicIntegerArray(new int[]{1});
    private static final AtomicLongArray ATOMIC_LONG_ARRAY = new AtomicLongArray(new long[]{1L});

    private static final VarHandle INT_ARRAY_VH = MethodHandles.arrayElementVarHandle(int[].class);
    private static final VarHandle LONG_ARRAY_VH = MethodHandles.arrayElementVarHandle(long[].class);

    // Method handle to amplify constant folding effects
    @Benchmark public int     mh()       throws Throwable { return (int) intIdentityMH.invokeExact(42); }
    @Benchmark public int     mhStable() throws Throwable { return (int) INT_MH_HOLDER.getStable().invokeExact(42); }
    @Benchmark public int     mhStatic() throws Throwable { return (int) INT_IDENTITY_MH.invokeExact(42); }

    // Atomics
    @Benchmark public Object  atomicReference()               { return ATOMIC_REFERENCE.get(); }
    @Benchmark public Object  atomicReferenceStable()         { return ATOMIC_REFERENCE.getStable(); }
    @Benchmark public Object  atomicReferenceStableVolatile() { return ATOMIC_REFERENCE.getStableVolatile(); }
    @Benchmark public boolean atomicBoolean()                 { return ATOMIC_BOOLEAN.get(); }
    @Benchmark public boolean atomicBooleanStable()           { return ATOMIC_BOOLEAN.getStable(); }
    @Benchmark public boolean atomicBooleanStableVolatile()   { return ATOMIC_BOOLEAN.getStableVolatile(); }
    @Benchmark public int     atomicInteger()                 { return ATOMIC_INTEGER.get(); }
    @Benchmark public int     atomicIntegerStable()           { return ATOMIC_INTEGER.getStable(); }
    @Benchmark public int     atomicIntegerStableVolatile()   { return ATOMIC_INTEGER.getStableVolatile(); }
    @Benchmark public long    atomicLong()                    { return ATOMIC_LONG.get(); }
    @Benchmark public long    atomicLongStable()              { return ATOMIC_LONG.getStable(); }
    @Benchmark public long    atomicLongStableVolatile()      { return ATOMIC_LONG.getStableVolatile(); }

    // AtomicArrays
    @Benchmark public Object  arrayAtomicReference()               { return ATOMIC_REFERENCE_ARRAY.get(0); }
    @Benchmark public Object  arrayAtomicReferenceStable()         { return ATOMIC_REFERENCE_ARRAY.getStable(0); }
    @Benchmark public Object  arrayAtomicReferenceStableVolatile() { return ATOMIC_REFERENCE_ARRAY.getStableVolatile(0); }
    @Benchmark public int     arrayAtomicInteger()                 { return ATOMIC_INTEGER_ARRAY.get(0); }
    @Benchmark public int     arrayAtomicIntegerStable()           { return ATOMIC_INTEGER_ARRAY.getStable(0); }
    @Benchmark public int     arrayAtomicIntegerStableVolatile()   { return ATOMIC_INTEGER_ARRAY.getStableVolatile(0); }
    @Benchmark public long    arrayAtomicLong()                    { return ATOMIC_LONG_ARRAY.get(0); }
    @Benchmark public long    arrayAtomicLongStable()              { return ATOMIC_LONG_ARRAY.getStable(0); }
    @Benchmark public long    arrayAtomicLongStableVolatile()      { return ATOMIC_LONG_ARRAY.getStableVolatile(0); }

    // Arrays via Unsafe (xBaseline is normal non-Unsafe array access to give a baseline figure)
    @Benchmark public int     unsafeArrayIntegerBaseline()         { return INT_ARRAY[0]; }
    @Benchmark public int     unsafeArrayInteger()                 { return UNSAFE.getInt(INT_ARRAY, Unsafe.ARRAY_INT_BASE_OFFSET); }
    @Benchmark public int     unsafeArrayIntegerStable()           { return UNSAFE.getIntStable(INT_ARRAY, Unsafe.ARRAY_INT_BASE_OFFSET); }
    @Benchmark public int     unsafeArrayIntegerStableVolatile()   { return UNSAFE.getIntStableVolatile(INT_ARRAY, Unsafe.ARRAY_INT_BASE_OFFSET); }
    @Benchmark public long    unsafeArrayLongBaseline()            { return LONG_ARRAY[0]; }
    @Benchmark public long    unsafeArrayLong()                    { return UNSAFE.getLong(LONG_ARRAY, Unsafe.ARRAY_LONG_BASE_OFFSET); }
    @Benchmark public long    unsafeArrayLongStable()              { return UNSAFE.getLongStable(LONG_ARRAY, Unsafe.ARRAY_LONG_BASE_OFFSET); }
    @Benchmark public long    unsafeArrayLongStableVolatile()      { return UNSAFE.getLongStableVolatile(LONG_ARRAY, Unsafe.ARRAY_LONG_BASE_OFFSET); }

    // Arrays via VarHandle
    @Benchmark public int     varHandleArrayInteger()                 { return (int) INT_ARRAY_VH.get(INT_ARRAY, 0); }
    @Benchmark public int     varHandleArrayIntegerStable()           { return (int) INT_ARRAY_VH.getStable(INT_ARRAY, 0); }
    @Benchmark public int     varHandleArrayIntegerStableVolatile()   { return (int) INT_ARRAY_VH.getStableVolatile(INT_ARRAY, 0); }
    @Benchmark public long    varHandleArrayLong()                    { return (long) LONG_ARRAY_VH.get(LONG_ARRAY, 0); }
    @Benchmark public long    varHandleArrayLongStable()              { return (long) LONG_ARRAY_VH.getStable(LONG_ARRAY, 0); }
    @Benchmark public long    varHandleArrayLongStableVolatile()      { return (long) LONG_ARRAY_VH.getStableVolatile(LONG_ARRAY, 0); }

    // Primitives (via Map to amplify constant folding effects)
    @Benchmark public byte    byteMap()         { return BYTE_MAP.get(BYTE_HOLDER); }
    @Benchmark public byte    byteMapStable()   { return BYTE_MAP.get(STABLE_BYTE_HOLDER); }
    @Benchmark public short   shortMap()        { return SHORT_MAP.get(SHORT_HOLDER); }
    @Benchmark public short   shortMapStable()  { return SHORT_MAP.get(STABLE_SHORT_HOLDER); }
    @Benchmark public char    charMap()         { return CHAR_MAP.get(CHAR_HOLDER); }
    @Benchmark public char    charMapStable()   { return CHAR_MAP.get(STABLE_CHAR_HOLDER); }
    @Benchmark public int     intMap()          { return INT_MAP.get(INT_HOLDER); }
    @Benchmark public int     intMapStable()    { return INT_MAP.get(STABLE_INT_HOLDER); }
    @Benchmark public int     intMapStableVolatile()    { return INT_VOLATILE_MAP.get(STABLE_VOLATILE_INT_HOLDER); }
    @Benchmark public long    longMap()         { return LONG_MAP.get(LONG_HOLDER); }
    @Benchmark public long    longMapStable()   { return LONG_MAP.get(STABLE_LONG_HOLDER); }
    @Benchmark public float   floatMap()        { return FLOAT_MAP.get(FLOAT_HOLDER); }
    @Benchmark public float   floatMapStable()  { return FLOAT_MAP.get(STABLE_FLOAT_HOLDER); }
    @Benchmark public double  doubleMap()       { return DOUBLE_MAP.get(DOUBLE_HOLDER); }
    @Benchmark public double  doubleMapStable() { return DOUBLE_MAP.get(STABLE_DOUBLE_HOLDER); }

    // Static VHs
    @Benchmark public int   staticVhIntegerBaseline()       { return INT_VALUE; }
    @Benchmark public int   staticVhInteger()               { return (int)INT_VALUE_VH.get(); }
    @Benchmark public int   staticVhIntegerStable()         { return (int)INT_VALUE_VH.getStable(); }
    @Benchmark public int   staticVhIntegerStableVolatile() { return (int)INT_VALUE_VH.getStableVolatile(); }
    @Benchmark public long  staticVhLongBaseline()          { return LONG_VALUE; }
    @Benchmark public long  staticVhLong()                  { return (long)LONG_VALUE_VH.get(); }
    @Benchmark public long  staticVhLongStable()            { return (long)LONG_VALUE_VH.getStable(); }
    @Benchmark public long  staticVhLongStableVolatile()    { return (long)LONG_VALUE_VH.getStableVolatile(); }

    // Segment primitives (via Map to amplify constant folding effects)
    @Benchmark public int     segmentIntMap()          { return SEGMENT_INT_MAP.get(SEGMENT_INT_HOLDER); }
    @Benchmark public int     segmentIntMapStable()    { return SEGMENT_INT_MAP.get(STABLE_SEGMENT_INT_HOLDER); }

    final static class MethodHandlerHolder {
        private static final long OFFSET = UNSAFE.objectFieldOffset(MethodHandlerHolder.class, "methodHandle");

        MethodHandle methodHandle;

        public MethodHandlerHolder(MethodHandle methodHandle) {
            this.methodHandle = methodHandle;
        }

        MethodHandle getStable() {
            return (MethodHandle) UNSAFE.getReferenceStable(this, OFFSET);
        }
    }

    static sealed class ByteHolder {
        byte value = 1;
        @Override public final boolean equals(Object obj) { return obj instanceof ByteHolder that  && this.value == that.value; }
        @Override public int hashCode() { return value; }
    }

    final static class StableByteHolder extends ByteHolder {
        private static final long OFFSET = valueOffset(ByteHolder.class);
        int getStable() { return UNSAFE.getByteStable(this, OFFSET); }
        @Override public int hashCode() { return getStable(); }
    }

    static sealed class ShortHolder {
        short value = 1;
        @Override public final boolean equals(Object obj) { return obj instanceof ShortHolder that  && this.value == that.value; }
        @Override public int hashCode() { return value; }
    }

    final static class StableShortHolder extends ShortHolder {
        private static final long OFFSET = valueOffset(ShortHolder.class);
        short getStable() { return UNSAFE.getShortStable(this, OFFSET); }
        @Override public int hashCode() { return getStable(); }
    }

    static sealed class CharHolder {
        char value = 1;
        @Override public final boolean equals(Object obj) { return obj instanceof CharHolder that  && this.value == that.value; }
        @Override public int hashCode() { return value; }
    }

    final static class StableCharHolder extends CharHolder {
        private static final long OFFSET = valueOffset(CharHolder.class);
        char getStable() { return UNSAFE.getCharStable(this, OFFSET); }
        @Override public int hashCode() { return getStable(); }
    }

    static sealed class IntHolder {
        int value = 1;
        @Override public final boolean equals(Object obj) { return obj instanceof IntHolder that  && this.value == that.value; }
        @Override public int hashCode() { return value; }
    }

    final static class StableIntHolder extends IntHolder {
        private static final long OFFSET = valueOffset(IntHolder.class);
        int getStable() { return UNSAFE.getIntStable(this, OFFSET); }
        @Override public int hashCode() { return getStable(); }
    }

    final static class StableVolatileIntHolder extends IntHolder {
        private static final long OFFSET = valueOffset(IntHolder.class);
        int getStableVolatile() { return UNSAFE.getIntStableVolatile(this, OFFSET); }
        @Override public int hashCode() { return getStableVolatile(); }
    }

    static sealed class LongHolder {
        long value = 1;
        @Override public final boolean equals(Object obj) { return obj instanceof LongHolder that  && this.value == that.value; }
        @Override public int hashCode() { return (int) value; }
    }

    final static class StableLongHolder extends LongHolder {
        private static final long OFFSET = valueOffset(LongHolder.class);
        long getStable() { return UNSAFE.getLongStable(this, OFFSET); }
        @Override public int hashCode() { return (int) getStable(); }
    }

    static sealed class FloatHolder {
        float value = 1;
        @Override public final boolean equals(Object obj) { return obj instanceof FloatHolder that  && this.value == that.value; }
        @Override public int hashCode() { return (int) value; }
    }

    final static class StableFloatHolder extends FloatHolder {
        private static final long OFFSET = valueOffset(FloatHolder.class);
        float getStable() { return UNSAFE.getFloatStable(this, OFFSET); }
        @Override public int hashCode() { return Float.floatToRawIntBits(getStable()); }
    }

    static sealed class DoubleHolder {
        double value = 1;
        @Override public final boolean equals(Object obj) { return obj instanceof DoubleHolder that  && this.value == that.value; }
        @Override public int hashCode() { return (int) value; }
    }

    final static class StableDoubleHolder extends DoubleHolder {
        private static final long OFFSET = valueOffset(DoubleHolder.class);
        double getStable() { return UNSAFE.getDoubleStable(this, OFFSET); }
        @Override public int hashCode() { return (int) Double.doubleToRawLongBits(getStable()); }
    }

    // Segment access via VarHandle

    static sealed class SegmentIntHolder {
        MemorySegment segment;

        public SegmentIntHolder() {
            segment = Arena.global().allocate(JAVA_INT);
            segment.set(JAVA_INT, 0, 1);
        }
        int value() { return segment.get(JAVA_INT, 0); }
        @Override public final boolean equals(Object obj) { return obj instanceof SegmentIntHolder that  && this.value() == that.value(); }
        @Override public int hashCode() { return value(); }
    }

    final static class StableSegmentIntHolder extends SegmentIntHolder {
        private static final VarHandle VAR_HANDLE = JAVA_INT.varHandle();
        int getStable() { return (int)VAR_HANDLE.getStable(segment, 0); }
        @Override public int hashCode() { return getStable(); }
    }

    private static long valueOffset(Class<?> declaredIn) {
        return UNSAFE.objectFieldOffset(declaredIn, "value");
    }

}
