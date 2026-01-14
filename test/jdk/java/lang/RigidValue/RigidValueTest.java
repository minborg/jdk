/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Basic tests for the RigidValueTest implementations
 * @enablePreview
 * @run junit/othervm --add-opens java.base/jdk.internal.lang=ALL-UNNAMED RigidValueTest
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.LazyConstant;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class RigidValueTest {

    private static final int VALUE = 42;
    private static final Supplier<Integer> SUPPLIER = () -> VALUE;

    @Test
    void factoryInvariants() {
        assertThrows(IllegalArgumentException.class, () -> RigidValue.list(-1));
        assertThrows(IllegalArgumentException.class, () -> RigidValue.listOfResettable(-1));
        assertThrows(NullPointerException.class, () -> RigidValue.map(null));
        assertThrows(NullPointerException.class, () -> RigidValue.map(new HashSet<>(Collections.singleton(null))));
    }

    @ParameterizedTest
    @MethodSource("factories")
    void basic(Supplier<RigidValue<Integer>> factory) {
        var value = factory.get();
        assertFalse(value.isSet());
        assertThrows(NoSuchElementException.class, value::get);
        value.set(VALUE);
        assertTrue(value.isSet());
        assertEquals(VALUE, value.get());
        assertEquals(Integer.toString(VALUE), value.toString());
    }

    @ParameterizedTest
    @MethodSource("factories")
    void orElseSet(Supplier<RigidValue<Integer>> factory) {
        Supplier<Integer> throwingSupplier = new Supplier<Integer>() {
            @Override
            public Integer get() {
                throw new UnsupportedOperationException();
            }
        };
        var value = factory.get();
        assertThrows(UnsupportedOperationException.class, () -> value.orElseSet(throwingSupplier));
        assertEquals(VALUE, value.orElseSet(SUPPLIER));
    }

    @ParameterizedTest
    @MethodSource("factories")
    void orElse(Supplier<RigidValue<Integer>> factory) {
        var value = factory.get();
        assertEquals(1, value.orElse(1));
        value.set(VALUE);
        assertEquals(VALUE, value.orElse(1));
    }

    @ParameterizedTest
    @MethodSource("factories")
    void isSet(Supplier<RigidValue<Integer>> factory) {
        var value = factory.get();
        assertFalse(value.isSet());
        value.set(VALUE);
        assertTrue(value.isSet());
    }

    @ParameterizedTest
    @MethodSource("factories")
    void testHashCode(Supplier<RigidValue<Integer>> factory) {
        var value = factory.get();
        assertEquals(System.identityHashCode(value), value.hashCode());
    }

    @ParameterizedTest
    @MethodSource("factories")
    void testEquals(Supplier<RigidValue<Integer>> factory) {
        var value = factory.get();
        assertNotEquals(null, value);
        var different = factory.get();
        assertNotEquals(different, value);
        assertNotEquals(value, different);
        assertNotEquals("a", value);
    }

    @ParameterizedTest
    @MethodSource("factories")
    void toStringTest(Supplier<RigidValue<Integer>> factory) {
        var value = factory.get();
        String unInitializedToString = value.toString();
        assertEquals(".unset",  unInitializedToString);
        value.set(VALUE);
        String expectedInitialized = Integer.toString(VALUE);
        assertEquals(expectedInitialized, value.toString());
    }

    @Test
    void toStringCircular() {
        AtomicReference<RigidValue<?>> ref = new AtomicReference<>();
        RigidValue<RigidValue<?>> value = RigidValue.of();
        value.set(value);
        String toString = assertDoesNotThrow(value::toString);
        assertTrue(value.toString().contains("(this RigidValue)"), toString);
    }

    @ParameterizedTest
    @MethodSource("factories")
    void recursiveSet(Supplier<RigidValue<Integer>> factory) {
        var value = factory.get();
        assertThrows(IllegalStateException.class, () -> value.orElseSet(() -> {
            value.set(VALUE);
            return VALUE;
        }));
    }

    @ParameterizedTest
    @MethodSource("factories")
    void reSet(Supplier<RigidValue<Integer>> factory) {
        var value = factory.get();
        value.set(VALUE);
        int nextValue = VALUE + 1;
        if (isStable(value)) {
            assertThrows(IllegalStateException.class, () -> value.set(nextValue));
            assertEquals(VALUE, value.get());
        } else  {
            value.set(nextValue);
            assertEquals(nextValue, value.get());
        }
    }

    private static Stream<Supplier<RigidValue<Integer>>> factories() {
        return Stream.of(
                supplier("RigidValue.of()", RigidValue::of),
                supplier("RigidValue.ofResettable()", RigidValue::ofResettable)
        );
    }

    private static boolean isStable(RigidValue<?> rigidValue) {
        return rigidValue.getClass().getSimpleName().equals("StableRigidValue");
    }

    private static Supplier<RigidValue<Integer>> supplier(String name,
                                                          Supplier<RigidValue<Integer>> supplier) {

        return new Supplier<RigidValue<Integer>>() {
            @Override
            public RigidValue<Integer> get() {
                return supplier.get();
            }

            @Override
            public String toString() {
                return name;
            }

        };
    }

}
