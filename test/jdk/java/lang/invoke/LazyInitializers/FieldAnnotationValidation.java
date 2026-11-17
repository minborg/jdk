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
 * @summary Stable field factories require the field mode of ReflectiveAccessOnly
 * @enablePreview
 * @run junit FieldAnnotationValidation
 */

import java.lang.invoke.ReflectiveAccessOnly;
import java.lang.invoke.StableAccessors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class FieldAnnotationValidation {
    private static class Fields {
        @ReflectiveAccessOnly
        private String instanceMarked;
        @ReflectiveAccessOnly(arrayElements = false)
        private static String staticMarked;
        private String instanceUnmarked;
        private static String staticUnmarked;
        @ReflectiveAccessOnly(arrayElements = true)
        private String[] instanceElementsOnly;
        @ReflectiveAccessOnly(arrayElements = true)
        private static String[] staticElementsOnly;
    }

    private static class Parent {
        @ReflectiveAccessOnly
        private String inheritedMarked;
        @ReflectiveAccessOnly
        private static String inheritedStaticMarked;
        @ReflectiveAccessOnly
        private String hiddenUnmarked;
        private String hiddenMarked;
    }

    private static class Child extends Parent {
        private String hiddenUnmarked;
        @ReflectiveAccessOnly
        private String hiddenMarked;
    }

    @Test
    void annotatedFieldsReachPrototypeStub() {
        acceptsInstance(Fields.class, String.class, "instanceMarked");
        acceptsStatic(Fields.class, String.class, "staticMarked");
    }

    @Test
    void unannotatedFieldsAreRejected() {
        rejectsInstance(Fields.class, String.class, "instanceUnmarked");
        rejectsStatic(Fields.class, String.class, "staticUnmarked");
    }

    @Test
    void arrayElementModeDoesNotMarkTheField() {
        rejectsInstance(Fields.class, String[].class, "instanceElementsOnly");
        rejectsStatic(Fields.class, String[].class, "staticElementsOnly");
    }

    @Test
    void annotationIsCheckedOnTheResolvedField() {
        acceptsInstance(Child.class, String.class, "inheritedMarked");
        acceptsStatic(Child.class, String.class, "inheritedStaticMarked");
        rejectsInstance(Child.class, String.class, "hiddenUnmarked");
        acceptsInstance(Child.class, String.class, "hiddenMarked");
    }

    private static <T> void acceptsInstance(Class<T> owner, Class<?> type, String name) {
        assertThrows(UnsupportedOperationException.class, () ->
                StableAccessors.ofField(owner, type, name, Object.class,
                        context -> unexpectedComputation()));
        assertThrows(UnsupportedOperationException.class, () ->
                StableAccessors.ofField(owner, type, name,
                        () -> unexpectedComputation()));
    }

    private static void acceptsStatic(Class<?> owner, Class<?> type, String name) {
        assertThrows(UnsupportedOperationException.class, () ->
                StableAccessors.ofStaticField(owner, type, name, Object.class,
                        context -> unexpectedComputation()));
        assertThrows(UnsupportedOperationException.class, () ->
                StableAccessors.ofStaticField(owner, type, name,
                        () -> unexpectedComputation()));
    }

    private static <T> void rejectsInstance(Class<T> owner, Class<?> type, String name) {
        assertThrows(IllegalArgumentException.class, () ->
                StableAccessors.ofField(owner, type, name, Object.class,
                        context -> unexpectedComputation()));
        assertThrows(IllegalArgumentException.class, () ->
                StableAccessors.ofField(owner, type, name,
                        () -> unexpectedComputation()));
    }

    private static <V> void rejectsStatic(Class<?> owner, Class<V> type, String name) {
        assertThrows(IllegalArgumentException.class, () ->
                StableAccessors.ofStaticField(owner, type, name, Object.class,
                        context -> unexpectedComputation()));
        assertThrows(IllegalArgumentException.class, () ->
                StableAccessors.ofStaticField(owner, type, name,
                        () -> unexpectedComputation()));
    }

    private static <V> V unexpectedComputation() {
        throw new AssertionError("A factory must not invoke its computing function");
    }
}
