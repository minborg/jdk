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

/* @test
 * @summary Test of getStable() and getStableVolatile semantics for atomic classes
 * @modules java.base/jdk.internal.misc
 * @run junit AtomicStablesTest
 */

import jdk.internal.misc.Unsafe;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.atomic.*;
import java.util.function.IntFunction;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class AtomicStablesTest {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    // The VarHandle array access hinges on this invariant being true.
    // See `java.lang.invoke.MethodHandleStatics.OFFSET_FOR_ARRAY_LENGTH_OF_TYPE_*`
    @ParameterizedTest()
    @MethodSource("arrayLengthVariants")
    void assertArrayLengthAccess(String name, IntFunction<Object> factory, long offset) {
        for (int i = 0; i < 384; i++) {
            Object arr = factory.apply(i);
            int unsafeLength = UNSAFE.getInt(arr, offset - Integer.BYTES);
            assertEquals(i, unsafeLength, name + " failed for " + i);
        }
    }

    @Test
    void atomicReference() {
        var ab = new AtomicReference<Integer>();
        assertEquals(ab.get(), ab.getStable());
        ab.set(1);
        assertEquals(ab.get(), ab.getStable());
    }

    @Test
    void atomicBoolean() {
        var ab = new AtomicBoolean();
        assertEquals(ab.get(), ab.getStable());
        ab.set(true);
        assertEquals(ab.get(), ab.getStable());
    }

    @Test
    void atomicInteger() {
        var ab = new AtomicInteger();
        assertEquals(ab.get(), ab.getStable());
        ab.set(1);
        assertEquals(ab.get(), ab.getStable());
    }

    @Test
    void atomicLong() {
        var ab = new AtomicLong();
        assertEquals(ab.get(), ab.getStable());
        ab.set(1L);
        assertEquals(ab.get(), ab.getStable());
    }

    @Test
    void atomicReferenceVolatile() {
        var ab = new AtomicReference<Integer>();
        assertEquals(ab.get(), ab.getStableVolatile());
        ab.set(1);
        assertEquals(ab.get(), ab.getStableVolatile());
    }

    @Test
    void atomicBooleanVolatile() {
        var ab = new AtomicBoolean();
        assertEquals(ab.get(), ab.getStableVolatile());
        ab.set(true);
        assertEquals(ab.get(), ab.getStableVolatile());
    }

    @Test
    void atomicIntegerVolatile() {
        var ab = new AtomicInteger();
        assertEquals(ab.get(), ab.getStableVolatile());
        ab.set(1);
        assertEquals(ab.get(), ab.getStableVolatile());
    }

    @Test
    void atomicLongVolatile() {
        var ab = new AtomicLong();
        assertEquals(ab.get(), ab.getStableVolatile());
        ab.set(1L);
        assertEquals(ab.get(), ab.getStableVolatile());
    }

    // Arrays

    @Test
    void atomicReferenceArray() {
        var aa = new AtomicReferenceArray<Integer>(new Integer[1]);
        assertEquals(aa.get(0), aa.getStable(0));
        aa.set(0, 1);
        assertEquals(aa.get(0), aa.getStable(0));

        assertThrows(ArrayIndexOutOfBoundsException.class, () -> aa.getStable(-1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> aa.getStable(1));
    }

    @Test
    void atomicIntegerArray() {
        var aa = new AtomicIntegerArray(new int[1]);
        assertEquals(aa.get(0), aa.getStable(0));
        aa.set(0, 1);
        assertEquals(aa.get(0), aa.getStable(0));

        assertThrows(ArrayIndexOutOfBoundsException.class, () -> aa.getStable(-1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> aa.getStable(1));
    }

    @Test
    void atomicLongArray() {
        var aa = new AtomicLongArray(new long[1]);
        assertEquals(aa.get(0), aa.getStable(0));
        aa.set(0, 1L);
        assertEquals(aa.get(0), aa.getStable(0));

        assertThrows(ArrayIndexOutOfBoundsException.class, () -> aa.getStable(-1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> aa.getStable(1));
    }

    @Test
    void atomicReferenceArrayVolatile() {
        var aa = new AtomicReferenceArray<Integer>(new Integer[1]);
        assertEquals(aa.get(0), aa.getStableVolatile(0));
        aa.set(0, 1);
        assertEquals(aa.get(0), aa.getStableVolatile(0));

        assertThrows(ArrayIndexOutOfBoundsException.class, () -> aa.getStableVolatile(-1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> aa.getStableVolatile(1));
    }

    @Test
    void atomicIntegerArrayVolatile() {
        var aa = new AtomicIntegerArray(new int[1]);
        assertEquals(aa.get(0), aa.getStableVolatile(0));
        aa.set(0, 1);
        assertEquals(aa.get(0), aa.getStableVolatile(0));

        assertThrows(ArrayIndexOutOfBoundsException.class, () -> aa.getStable(-1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> aa.getStable(1));
    }

    @Test
    void atomicLongArrayVolatile() {
        var aa = new AtomicLongArray(new long[1]);
        assertEquals(aa.get(0), aa.getStableVolatile(0));
        aa.set(0, 1L);
        assertEquals(aa.get(0), aa.getStableVolatile(0));

        assertThrows(ArrayIndexOutOfBoundsException.class, () -> aa.getStable(-1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> aa.getStable(1));
    }

    public static Stream<Arguments> arrayLengthVariants() {
        return Stream.of(
                Arguments.of("Object[]", asIntFunction(Object[]::new), Unsafe.ARRAY_OBJECT_BASE_OFFSET),
                Arguments.of("boolean[]", asIntFunction(boolean[]::new), Unsafe.ARRAY_BOOLEAN_BASE_OFFSET),
                Arguments.of("byte[]", asIntFunction(byte[]::new), Unsafe.ARRAY_BYTE_BASE_OFFSET),
                Arguments.of("short[]", asIntFunction(short[]::new), Unsafe.ARRAY_SHORT_BASE_OFFSET),
                Arguments.of("char[]", asIntFunction(char[]::new), Unsafe.ARRAY_CHAR_BASE_OFFSET),
                Arguments.of("int[]", asIntFunction(int[]::new), Unsafe.ARRAY_INT_BASE_OFFSET),
                Arguments.of("long[]", asIntFunction(long[]::new), Unsafe.ARRAY_LONG_BASE_OFFSET),
                Arguments.of("float[]", asIntFunction(float[]::new), Unsafe.ARRAY_FLOAT_BASE_OFFSET),
                Arguments.of("double[]", asIntFunction(double[]::new), Unsafe.ARRAY_DOUBLE_BASE_OFFSET)
        );
    }

    private static IntFunction<?> asIntFunction(IntFunction<?> f) {
        return f;
    }

}
