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

/*
 * @test
 * @summary Stable accessor factories express storage and context shapes through OfScalar and OfArray
 * @enablePreview
 * @run junit AccessorShapes
 */

import java.lang.invoke.ReflectiveAccessOnly;
import java.lang.invoke.StableFieldAccessors;
import java.lang.invoke.StableFieldAccessors.OfArray;
import java.lang.invoke.StableFieldAccessors.OfScalar;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AccessorShapes {
    private static class Fields {
        @ReflectiveAccessOnly
        private String value;
        @ReflectiveAccessOnly
        private static String staticValue;
        @ReflectiveAccessOnly(arrayElements = true)
        private final String[] array = new String[3];
        @ReflectiveAccessOnly(arrayElements = true)
        private static final String[] staticArray = new String[3];
    }

    @Test
    void instanceFieldShapes() {
        assertThrows(UnsupportedOperationException.class, () -> {
            OfScalar<Fields, Void, String> accessor = StableFieldAccessors.ofField(
                    Fields.class, String.class, "value", Void.class,
                    (_, _) -> unexpectedComputation());
        });
        assertThrows(UnsupportedOperationException.class, () -> {
            OfScalar<Fields, Integer, String> accessor = StableFieldAccessors.ofField(
                    Fields.class, String.class, "value", Integer.class,
                    (Fields _, Integer _) -> unexpectedComputation());
        });
    }

    @Test
    void staticFieldShapes() {
        assertThrows(UnsupportedOperationException.class, () -> {
            OfScalar<Void, Void, String> accessor = StableFieldAccessors.ofStaticField(
                    Fields.class, String.class, "staticValue", Void.class,
                    (_, _) -> unexpectedComputation());
        });
        assertThrows(UnsupportedOperationException.class, () -> {
            OfScalar<Void, Integer, String> accessor = StableFieldAccessors.ofStaticField(
                    Fields.class, String.class, "staticValue", Integer.class,
                    (_, _) -> unexpectedComputation());
        });
    }

    @Test
    void instanceArrayShapes() {
        assertThrows(UnsupportedOperationException.class, () -> {
            OfArray<Fields, Void, String> accessor = StableFieldAccessors.ofArrayField(
                    Fields.class, String.class, "array", Void.class,
                    (_, _, _) -> unexpectedComputation());
        });
        assertThrows(UnsupportedOperationException.class, () -> {
            OfArray<Fields, Integer, String> accessor = StableFieldAccessors.ofArrayField(
                    Fields.class, String.class, "array", Integer.class,
                    (Fields _, int _, Integer _) -> unexpectedComputation());
        });
    }

    @Test
    void staticArrayShapes() {
        assertThrows(UnsupportedOperationException.class, () -> {
            OfArray<Void, Void, String> accessor = StableFieldAccessors.ofStaticArrayField(
                    Fields.class, String.class, "staticArray", Void.class,
                    (_, _, _) -> unexpectedComputation());
        });
        assertThrows(UnsupportedOperationException.class, () -> {
            OfArray<Void, Integer, String> accessor = StableFieldAccessors.ofStaticArrayField(
                    Fields.class, String.class, "staticArray", Integer.class,
                    (_, _, _) -> unexpectedComputation());
        });
    }

    @Test
    void onlyTwoAccessorInterfaces() throws ReflectiveOperationException {
        assertEquals(Set.of(OfScalar.class, OfArray.class),
                Arrays.stream(StableFieldAccessors.class.getDeclaredClasses())
                        .filter(Class::isInterface)
                        .collect(Collectors.toSet()));
        assertEquals(Set.of(OfScalar.Computer.class),
                Set.of(OfScalar.class.getDeclaredClasses()));
        assertEquals(Set.of(OfArray.Computer.class),
                Set.of(OfArray.class.getDeclaredClasses()));
        assertEquals(Object.class,
                OfScalar.Computer.class.getMethod("compute", Object.class, Object.class).getReturnType());
        assertEquals(Object.class,
                OfArray.Computer.class.getMethod("compute", Object.class, int.class, Object.class).getReturnType());
    }

    // These methods check the invocation signatures at compile time. Accessor
    // implementations are still prototype stubs, so there are no runtime calls.
    private static <T, C, R> void fieldInvocations(OfScalar<T, C, R> accessor,
                                                   T receiver, C context) {
        Optional<R> observed = accessor.get(receiver);
        R computed = accessor.getOrCompute(receiver, context);
    }

    private static <T, C, R> void arrayInvocations(OfArray<T, C, R> accessor,
                                                   T receiver, int index, C context) {
        Optional<R> observed = accessor.get(receiver, index);
        R computed = accessor.getOrCompute(receiver, index, context);
    }

    private static String unexpectedComputation() {
        throw new AssertionError("A factory must not invoke its computing function");
    }
}
