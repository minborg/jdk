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
 * @summary The stable array factory validates its type tokens without computing a value
 * @enablePreview
 * @run junit ArrayFactoryValidation
 */

import java.lang.invoke.StableAccessors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class ArrayFactoryValidation {
    @Test
    void nullArguments() {
        assertThrows(NullPointerException.class, () ->
                StableAccessors.ofArray(null, String.class, Object.class,
                        (length, index, context) -> unexpectedComputation()));
        assertThrows(NullPointerException.class, () ->
                StableAccessors.ofArray(String[].class, null, Object.class,
                        (length, index, context) -> unexpectedComputation()));
        assertThrows(NullPointerException.class, () ->
                StableAccessors.ofArray(String[].class, String.class, null,
                        (length, index, context) -> unexpectedComputation()));
        assertThrows(NullPointerException.class, () ->
                StableAccessors.ofArray(String[].class, String.class, Object.class, null));
    }

    @Test
    void unsupportedArrayTypes() {
        rejectArrayType(String.class);
        rejectArrayType(int.class);
        rejectArrayType(void.class);
        rejectArrayType(String[][].class);
        rejectArrayType(int[][].class);
    }

    @Test
    void unsupportedElementTypes() {
        rejectElementType(void.class);
        rejectElementType(String[].class);
        rejectElementType(int[].class);
        rejectElementType(Object[][].class);
    }

    @Test
    void incompatibleElementTypes() {
        assertThrows(IllegalArgumentException.class, () ->
                StableAccessors.ofArray(String[].class, Integer.class, Object.class,
                        (length, index, context) -> unexpectedComputation()));
        assertThrows(IllegalArgumentException.class, () ->
                StableAccessors.ofArray(int[].class, Long.class, Object.class,
                        (length, index, context) -> unexpectedComputation()));
        assertThrows(IllegalArgumentException.class, () ->
                StableAccessors.ofArray(Object[].class, String.class, Object.class,
                        (length, index, context) -> unexpectedComputation()));
    }

    @Test
    void inaccessibleElementTypes() throws ClassNotFoundException {
        rejectInaccessibleType(Class.forName("java.util.Collections$EmptyList"));
        rejectInaccessibleType(Class.forName("jdk.internal.misc.Unsafe"));
    }

    @Test
    void validTypesReachPrototypeStub() {
        // A valid factory call reaches the unimplemented prototype, without
        // invoking the computing function.
        acceptsTypes(String[].class, String.class);
        acceptsTypes(String[].class, CharSequence.class);
        acceptsTypes(Void[].class, Void.class);
        acceptsTypes(boolean[].class, Boolean.class);
        acceptsTypes(byte[].class, Byte.class);
        acceptsTypes(short[].class, Short.class);
        acceptsTypes(char[].class, Character.class);
        acceptsTypes(int[].class, Integer.class);
        acceptsTypes(int[].class, int.class);
        acceptsTypes(int[].class, Number.class);
        acceptsTypes(long[].class, Long.class);
        acceptsTypes(float[].class, Float.class);
        acceptsTypes(double[].class, Double.class);
    }

    private static <A> void rejectArrayType(Class<A> type) {
        assertThrows(IllegalArgumentException.class, () ->
                StableAccessors.ofArray(type, Object.class, Object.class,
                        (length, index, context) -> unexpectedComputation()));
    }

    private static <V> void rejectElementType(Class<V> type) {
        assertThrows(IllegalArgumentException.class, () ->
                StableAccessors.ofArray(Object[].class, type, Object.class,
                        (length, index, context) -> unexpectedComputation()));
    }

    private static <V> void rejectInaccessibleType(Class<V> type) {
        assertThrows(IllegalArgumentException.class, () ->
                StableAccessors.ofArray(Object[].class, type, Object.class,
                        (length, index, context) -> unexpectedComputation()));
        Class<?> arrayType = java.lang.reflect.Array.newInstance(type, 0).getClass();
        assertThrows(IllegalArgumentException.class, () ->
                StableAccessors.ofArray(arrayType, Object.class, Object.class,
                        (length, index, context) -> unexpectedComputation()));
    }

    private static <A, V> void acceptsTypes(Class<A> arrayType, Class<V> elementType) {
        assertThrows(UnsupportedOperationException.class, () ->
                StableAccessors.ofArray(arrayType, elementType, Object.class,
                        (length, index, context) -> unexpectedComputation()));
    }

    private static <V> V unexpectedComputation() {
        throw new AssertionError("A factory must not invoke its computing function");
    }
}
