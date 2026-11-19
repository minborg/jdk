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
 * @summary Stable static array factories validate fields without initializing their declaring class
 * @enablePreview
 * @run junit StaticArrayFactoryValidation
 */

import java.lang.invoke.ReflectiveAccessOnly;
import java.lang.invoke.StableAccessors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class StaticArrayFactoryValidation {
    private static class Fields {
        @ReflectiveAccessOnly(arrayElements = true)
        private static final String[] strings = new String[3];
        @ReflectiveAccessOnly(arrayElements = true)
        private static final boolean[] booleans = new boolean[3];
        @ReflectiveAccessOnly(arrayElements = true)
        private static final byte[] bytes = new byte[3];
        @ReflectiveAccessOnly(arrayElements = true)
        private static final short[] shorts = new short[3];
        @ReflectiveAccessOnly(arrayElements = true)
        private static final char[] chars = new char[3];
        @ReflectiveAccessOnly(arrayElements = true)
        private static final int[] ints = new int[3];
        @ReflectiveAccessOnly(arrayElements = true)
        private static final long[] longs = new long[3];
        @ReflectiveAccessOnly(arrayElements = true)
        private static final float[] floats = new float[3];
        @ReflectiveAccessOnly(arrayElements = true)
        private static final double[] doubles = new double[3];
        @ReflectiveAccessOnly(arrayElements = true)
        private static final Void[] voids = new Void[3];
        @ReflectiveAccessOnly(arrayElements = true)
        private static final String[][] multidimensional = new String[1][];
        @ReflectiveAccessOnly(arrayElements = true)
        private final int[] instanceArray = new int[3];
        @ReflectiveAccessOnly(arrayElements = true)
        private static int[] mutable = new int[3];
        @ReflectiveAccessOnly
        private static final int[] fieldMode = new int[3];
        private static final int[] unmarked = new int[3];
        private static final int scalar = 1;
    }

    private static class Parent {
        @ReflectiveAccessOnly(arrayElements = true)
        private static final int[] inherited = new int[3];
        @ReflectiveAccessOnly(arrayElements = true)
        private static final int[] hidden = new int[3];
    }

    private static class Child extends Parent {
        private static final int[] hidden = new int[3];
    }

    private static boolean initialized;

    private static class Uninitialized {
        @ReflectiveAccessOnly(arrayElements = true)
        private static final String[] values = initialize();

        private static String[] initialize() {
            initialized = true;
            return new String[1];
        }
    }

    @Test
    void nullArguments() {
        assertThrows(NullPointerException.class, () ->
                StableAccessors.ofStaticArrayField(null, String.class, "strings",
                        index -> unexpectedComputation()));
        assertThrows(NullPointerException.class, () ->
                StableAccessors.ofStaticArrayField(Fields.class, null, "strings",
                        index -> unexpectedComputation()));
        assertThrows(NullPointerException.class, () ->
                StableAccessors.ofStaticArrayField(Fields.class, String.class, null,
                        index -> unexpectedComputation()));
        assertThrows(NullPointerException.class, () ->
                StableAccessors.ofStaticArrayField(Fields.class, String.class, "strings", null));
        assertThrows(NullPointerException.class, () ->
                StableAccessors.ofStaticArrayField(null, String.class, "strings", Object.class,
                        (index, context) -> unexpectedComputation()));
        assertThrows(NullPointerException.class, () ->
                StableAccessors.ofStaticArrayField(Fields.class, null, "strings", Object.class,
                        (index, context) -> unexpectedComputation()));
        assertThrows(NullPointerException.class, () ->
                StableAccessors.ofStaticArrayField(Fields.class, String.class, null, Object.class,
                        (index, context) -> unexpectedComputation()));
        assertThrows(NullPointerException.class, () ->
                StableAccessors.ofStaticArrayField(Fields.class, String.class, "strings", null,
                        (index, context) -> unexpectedComputation()));
        assertThrows(NullPointerException.class, () ->
                StableAccessors.ofStaticArrayField(Fields.class, String.class, "strings", Object.class, null));
    }

    @Test
    void unsupportedArrayFields() {
        rejects(Fields.class, Object.class, "scalar");
        rejects(Fields.class, Object.class, "multidimensional");
        rejects(Fields.class, Integer.class, "instanceArray");
        rejects(Fields.class, Integer.class, "mutable");
        rejects(Fields.class, Integer.class, "missing");
        rejects(Fields.class, Integer.class, "");
        rejects(Fields.class, Integer.class, "Fields.ints");
    }

    @Test
    void unsupportedElementTypes() {
        rejects(Fields.class, void.class, "strings");
        rejects(Fields.class, String[].class, "strings");
        rejects(Fields.class, int[].class, "ints");
        rejects(Fields.class, Object[][].class, "strings");
    }

    @Test
    void incompatibleElementTypes() {
        rejects(Fields.class, Integer.class, "strings");
        rejects(Fields.class, Long.class, "ints");
    }

    @Test
    void inaccessibleElementTypes() throws ClassNotFoundException {
        rejects(Fields.class, Class.forName("java.util.Collections$EmptyList"), "strings");
        rejects(Fields.class, Class.forName("jdk.internal.misc.Unsafe"), "strings");
    }

    @Test
    void annotationAndResolution() {
        rejects(Fields.class, Integer.class, "fieldMode");
        rejects(Fields.class, Integer.class, "unmarked");
        rejects(Child.class, Integer.class, "hidden");
        accepts(Child.class, Integer.class, "inherited");
    }

    @Test
    void validTypesReachPrototypeStub() {
        // A valid factory call reaches the unimplemented prototype, without
        // invoking the computing function.
        accepts(Fields.class, String.class, "strings");
        accepts(Fields.class, CharSequence.class, "strings");
        accepts(Fields.class, Void.class, "voids");
        accepts(Fields.class, Boolean.class, "booleans");
        accepts(Fields.class, Byte.class, "bytes");
        accepts(Fields.class, Short.class, "shorts");
        accepts(Fields.class, Character.class, "chars");
        accepts(Fields.class, Integer.class, "ints");
        accepts(Fields.class, int.class, "ints");
        accepts(Fields.class, Number.class, "ints");
        accepts(Fields.class, Long.class, "longs");
        accepts(Fields.class, Float.class, "floats");
        accepts(Fields.class, Double.class, "doubles");
    }

    @Test
    void factoryDoesNotInitializeDeclaringClass() {
        assertFalse(initialized);
        accepts(Uninitialized.class, String.class, "values");
        assertFalse(initialized);
    }

    private static <V> void rejects(Class<?> owner, Class<V> type, String name) {
        assertThrows(IllegalArgumentException.class, () ->
                StableAccessors.ofStaticArrayField(owner, type, name,
                        index -> unexpectedComputation()));
        assertThrows(IllegalArgumentException.class, () ->
                StableAccessors.ofStaticArrayField(owner, type, name, Object.class,
                        (index, context) -> unexpectedComputation()));
    }

    private static <V> void accepts(Class<?> owner, Class<V> type, String name) {
        assertThrows(UnsupportedOperationException.class, () ->
                StableAccessors.ofStaticArrayField(owner, type, name,
                        index -> unexpectedComputation()));
        assertThrows(UnsupportedOperationException.class, () ->
                StableAccessors.ofStaticArrayField(owner, type, name, Object.class,
                        (index, context) -> unexpectedComputation()));
    }

    private static <V> V unexpectedComputation() {
        throw new AssertionError("A factory must not invoke its computing function");
    }
}
