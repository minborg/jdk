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
 * @summary Stable accessor factories express storage and context shapes through OfField and OfArrayField
 * @enablePreview
 * @run junit AccessorShapes
 */

import java.lang.invoke.ReflectiveAccessOnly;
import java.lang.invoke.StableAccessors;
import java.lang.invoke.StableAccessors.OfArrayField;
import java.lang.invoke.StableAccessors.OfField;
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
            OfField<Fields, Void, String> accessor = StableAccessors.ofField(
                    Fields.class, String.class, "value", () -> unexpectedComputation());
        });
        assertThrows(UnsupportedOperationException.class, () -> {
            OfField<Fields, Integer, String> accessor = StableAccessors.ofField(
                    Fields.class, String.class, "value", Integer.class,
                    context -> unexpectedComputation());
        });
    }

    @Test
    void staticFieldShapes() {
        assertThrows(UnsupportedOperationException.class, () -> {
            OfField<Void, Void, String> accessor = StableAccessors.ofStaticField(
                    Fields.class, String.class, "staticValue", () -> unexpectedComputation());
        });
        assertThrows(UnsupportedOperationException.class, () -> {
            OfField<Void, Integer, String> accessor = StableAccessors.ofStaticField(
                    Fields.class, String.class, "staticValue", Integer.class,
                    context -> unexpectedComputation());
        });
    }

    @Test
    void instanceArrayShapes() {
        assertThrows(UnsupportedOperationException.class, () -> {
            OfArrayField<Fields, Void, String> accessor = StableAccessors.ofArrayField(
                    Fields.class, String.class, "array", index -> unexpectedComputation());
        });
        assertThrows(UnsupportedOperationException.class, () -> {
            OfArrayField<Fields, Integer, String> accessor = StableAccessors.ofArrayField(
                    Fields.class, String.class, "array", Integer.class,
                    (index, context) -> unexpectedComputation());
        });
    }

    @Test
    void staticArrayShapes() {
        assertThrows(UnsupportedOperationException.class, () -> {
            OfArrayField<Void, Void, String> accessor = StableAccessors.ofStaticArrayField(
                    Fields.class, String.class, "staticArray", index -> unexpectedComputation());
        });
        assertThrows(UnsupportedOperationException.class, () -> {
            OfArrayField<Void, Integer, String> accessor = StableAccessors.ofStaticArrayField(
                    Fields.class, String.class, "staticArray", Integer.class,
                    (index, context) -> unexpectedComputation());
        });
    }

    @Test
    void onlyTwoAccessorInterfaces() throws ReflectiveOperationException {
        assertEquals(Set.of(OfField.class, OfArrayField.class),
                Arrays.stream(StableAccessors.class.getDeclaredClasses())
                        .filter(Class::isInterface)
                        .collect(Collectors.toSet()));
        assertEquals(Set.of(OfField.Computer.class),
                Set.of(OfField.class.getDeclaredClasses()));
        assertEquals(Set.of(OfArrayField.Computer.class),
                Set.of(OfArrayField.class.getDeclaredClasses()));
        assertEquals(Object.class,
                OfField.Computer.class.getMethod("compute", Object.class).getReturnType());
        assertEquals(Object.class,
                OfArrayField.Computer.class.getMethod("compute", int.class, Object.class).getReturnType());
    }

    // These methods check the invocation signatures at compile time. Accessor
    // implementations are still prototype stubs, so there are no runtime calls.
    private static <T, C, R> void fieldInvocations(OfField<T, C, R> accessor,
                                                   T receiver, C context) {
        Optional<R> observed = accessor.get(receiver);
        R computed = accessor.getOrCompute(receiver, context);
    }

    private static <T, C, R> void arrayInvocations(OfArrayField<T, C, R> accessor,
                                                   T receiver, int index, C context) {
        Optional<R> observed = accessor.get(receiver, index);
        R computed = accessor.getOrCompute(receiver, index, context);
    }

    private static String unexpectedComputation() {
        throw new AssertionError("A factory must not invoke its computing function");
    }
}
