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
 * @summary Stable accessor context tokens must represent reference types
 * @enablePreview
 * @run junit ContextTypeValidation
 */

import java.lang.invoke.ReflectiveAccessOnly;
import java.lang.invoke.StableAccessors;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class ContextTypeValidation {
    enum Factory { INSTANCE_FIELD, STATIC_FIELD, INSTANCE_ARRAY, STATIC_ARRAY }

    private static class Fields {
        @ReflectiveAccessOnly
        private String value;
        @ReflectiveAccessOnly
        private static String staticValue;
        @ReflectiveAccessOnly(arrayElements = true)
        private final String[] array = new String[1];
        @ReflectiveAccessOnly(arrayElements = true)
        private static final String[] staticArray = new String[1];
    }

    private static value class ValueContext { }

    @ParameterizedTest
    @EnumSource(Factory.class)
    void primitiveContextTypesAreRejected(Factory factory) {
        for (Class<?> contextType : new Class<?>[] {
                boolean.class, byte.class, short.class, char.class,
                int.class, long.class, float.class, double.class, void.class
        }) {
            assertThrows(IllegalArgumentException.class,
                    () -> create(factory, contextType), contextType.getTypeName());
        }
    }

    @ParameterizedTest
    @EnumSource(Factory.class)
    void nullContextTypeIsRejected(Factory factory) {
        assertThrows(NullPointerException.class, () -> create(factory, null));
    }

    @ParameterizedTest
    @EnumSource(Factory.class)
    void referenceContextTypesReachPrototypeStub(Factory factory) {
        // Contexts are not storage element types. Interfaces, value classes,
        // and arrays (including primitive and multidimensional arrays) are valid.
        // Void.class is a reference type, unlike void.class.
        for (Class<?> contextType : new Class<?>[] {
                Object.class, String.class, CharSequence.class, ValueContext.class,
                String[].class, int[].class, String[][].class, Void.class
        }) {
            assertThrows(UnsupportedOperationException.class,
                    () -> create(factory, contextType), contextType.getTypeName());
        }
    }

    private static <C> void create(Factory factory, Class<C> contextType) {
        switch (factory) {
            case INSTANCE_FIELD -> StableAccessors.ofField(
                    Fields.class, String.class, "value", contextType,
                    context -> unexpectedComputation());
            case STATIC_FIELD -> StableAccessors.ofStaticField(
                    Fields.class, String.class, "staticValue", contextType,
                    context -> unexpectedComputation());
            case INSTANCE_ARRAY -> StableAccessors.ofArrayField(
                    Fields.class, String.class, "array", contextType,
                    (index, context) -> unexpectedComputation());
            case STATIC_ARRAY -> StableAccessors.ofStaticArrayField(
                    Fields.class, String.class, "staticArray", contextType,
                    (index, context) -> unexpectedComputation());
        }
    }

    private static String unexpectedComputation() {
        throw new AssertionError("A factory must not invoke its computing function");
    }
}
