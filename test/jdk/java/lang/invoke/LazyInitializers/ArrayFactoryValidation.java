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

import java.lang.invoke.ReflectiveAccessOnly;
import java.lang.invoke.StableFieldAccessors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class ArrayFactoryValidation {
    private static class Fields {
        @ReflectiveAccessOnly(arrayElements = true)
        private final String[] strings = new String[3];
        @ReflectiveAccessOnly(arrayElements = true)
        private final int[] ints = new int[3];
        @ReflectiveAccessOnly(arrayElements = true)
        private int[] mutable = new int[3];
        @ReflectiveAccessOnly(arrayElements = true)
        private final boolean[] booleans = new boolean[3];
        @ReflectiveAccessOnly(arrayElements = true)
        private final byte[] bytes = new byte[3];
        @ReflectiveAccessOnly(arrayElements = true)
        private final short[] shorts = new short[3];
        @ReflectiveAccessOnly(arrayElements = true)
        private final char[] chars = new char[3];
        @ReflectiveAccessOnly(arrayElements = true)
        private final long[] longs = new long[3];
        @ReflectiveAccessOnly(arrayElements = true)
        private final float[] floats = new float[3];
        @ReflectiveAccessOnly(arrayElements = true)
        private final double[] doubles = new double[3];
        @ReflectiveAccessOnly(arrayElements = true)
        private final Void[] voids = new Void[3];
        @ReflectiveAccessOnly(arrayElements = true)
        private final String[][] multidimensional = new String[1][];
        @ReflectiveAccessOnly(arrayElements = true)
        private static final int[] staticArray = new int[3];
        @ReflectiveAccessOnly
        private final int[] fieldMode = new int[3];
        private final int[] unmarked = new int[3];
        private final int scalar = 1;
    }

    private static class Parent {
        @ReflectiveAccessOnly(arrayElements = true)
        private final int[] inherited = new int[3];
        @ReflectiveAccessOnly(arrayElements = true)
        private final int[] hidden = new int[3];
    }

    private static class Child extends Parent {
        private final int[] hidden = new int[3];
    }

    @Test
    void nullArguments() {
        assertThrows(NullPointerException.class, () ->
                StableFieldAccessors.ofArrayField(null, String.class, "strings", Void.class,
                        (_, _, _) -> unexpectedComputation()));
        assertThrows(NullPointerException.class, () ->
                StableFieldAccessors.ofArrayField(Fields.class, null, "strings", Void.class,
                        (_, _, _) -> unexpectedComputation()));
        assertThrows(NullPointerException.class, () ->
                StableFieldAccessors.ofArrayField(Fields.class, String.class, null, Void.class,
                        (_, _, _) -> unexpectedComputation()));
        assertThrows(NullPointerException.class, () ->
                StableFieldAccessors.ofArrayField(Fields.class, String.class, "strings", Void.class, null));
        assertThrows(NullPointerException.class, () ->
                StableFieldAccessors.ofArrayField(Fields.class, String.class, "strings", null,
                        (_, _, _) -> unexpectedComputation()));
    }

    @Test
    void unsupportedArrayFields() {
        rejects(Fields.class, Object.class, "scalar");
        rejects(Fields.class, Object.class, "multidimensional");
        rejects(Fields.class, Integer.class, "staticArray");
        rejects(Fields.class, Integer.class, "mutable");
        rejects(Fields.class, Integer.class, "missing");
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

    private static <T, V> void rejects(Class<T> owner, Class<V> type, String name) {
        assertThrows(IllegalArgumentException.class, () ->
                StableFieldAccessors.ofArrayField(owner, type, name, Void.class,
                        (_, _, _) -> unexpectedComputation()));
    }

    private static <T, V> void accepts(Class<T> owner, Class<V> type, String name) {
        assertThrows(UnsupportedOperationException.class, () ->
                StableFieldAccessors.ofArrayField(owner, type, name, Void.class,
                        (_, _, _) -> unexpectedComputation()));
    }

    private static <V> V unexpectedComputation() {
        throw new AssertionError("A factory must not invoke its computing function");
    }
}
