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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark measuring StableValue performance
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark) // Share the same state instance (for contention)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(value = 3, jvmArgs = {
        "--enable-native-access=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"})
public class UnsafeStableSemanticsBenchmark {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private static final MethodHandle INT_IDENTITY_MH;
    private static final long INT_IDENTITY_OFFSET = UNSAFE.objectFieldOffset(UnsafeStableSemanticsBenchmark.class, "intIdentityMH");
    static {
        try {
            INT_IDENTITY_MH = LOOKUP.findStatic(UnsafeStableSemanticsBenchmark.class, "identity", MethodType.methodType(int.class, int.class));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static final long REFERENCE_OFFSET = UNSAFE.objectFieldOffset(UnsafeStableSemanticsBenchmark.class, "aReference");
    private static final long BYTE_OFFSET = UNSAFE.objectFieldOffset(UnsafeStableSemanticsBenchmark.class, "aByte");
    private static final long SHORT_OFFSET = UNSAFE.objectFieldOffset(UnsafeStableSemanticsBenchmark.class, "aShort");
    private static final long CHAR_OFFSET = UNSAFE.objectFieldOffset(UnsafeStableSemanticsBenchmark.class, "aChar");
    private static final long INT_OFFSET = UNSAFE.objectFieldOffset(UnsafeStableSemanticsBenchmark.class, "anInt");
    private static final long LONG_OFFSET = UNSAFE.objectFieldOffset(UnsafeStableSemanticsBenchmark.class, "aLong");
    private static final long FLOAT_OFFSET = UNSAFE.objectFieldOffset(UnsafeStableSemanticsBenchmark.class, "aFloat");
    private static final long DOUBLE_OFFSET = UNSAFE.objectFieldOffset(UnsafeStableSemanticsBenchmark.class, "aDouble");

    private static final IntHolder INT_HOLDER = new IntHolder();

    public static class Foo {}

    Object aReference = new Foo();
    MethodHandle intIdentityMH = INT_IDENTITY_MH;
    byte aByte = 1;
    short aShort = 1;
    char aChar = 'a';
    int anInt = 1;
    long aLong = 1;
    float aFloat = 1;
    double aDouble = 1;

    @Benchmark public Object getReference() { return aReference; }
    @Benchmark public Object getReferenceStable() { return UNSAFE.getReferenceStable(this, REFERENCE_OFFSET); }
    @Benchmark public byte getByteStable() { return UNSAFE.getByteStable(this, BYTE_OFFSET); }
    @Benchmark public short getShortStable() { return UNSAFE.getShortStable(this, SHORT_OFFSET); }
    @Benchmark public char getCharStable() { return UNSAFE.getCharStable(this, CHAR_OFFSET); }
    @Benchmark public int getInt() { return anInt; }
    @Benchmark public int getIntStable() { return UNSAFE.getIntStable(this, INT_OFFSET); }
    @Benchmark public long getLongStable() { return UNSAFE.getLongStable(this, LONG_OFFSET); }
    @Benchmark public float getFloatStable() { return UNSAFE.getFloatStable(this, FLOAT_OFFSET); }
    @Benchmark public double getDoubleStable() { return UNSAFE.getDoubleStable(this, DOUBLE_OFFSET); }

    @Benchmark public double intHolder() { return INT_HOLDER.getStable(); }

    @Benchmark
    public int intMh() throws Throwable {
        return (int) intIdentityMH.invokeExact(42);
    }

    @Benchmark
    public int intMhStable() throws Throwable {
        return (int) ((MethodHandle) UNSAFE.getReferenceStable(this, INT_IDENTITY_OFFSET)).invokeExact(42);
    }

    @Benchmark
    public int intMhStatic() throws Throwable {
        return (int) INT_IDENTITY_MH.invokeExact(42);
    }


    final static class IntHolder {
        private static final Unsafe UNSAFE = Unsafe.getUnsafe();
        private static final long OFFSET = UNSAFE.objectFieldOffset(IntHolder.class, "value");
        int value = 1;

        int getStable() {
            return UNSAFE.getIntStable(this, OFFSET);
        }

    }

    static int identity(int value) {
        return value;
    }

}
