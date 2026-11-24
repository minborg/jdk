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
 * @summary Stable field factories reject null-restricted fields
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @requires (vm.opt.PreloadClasses == null | vm.opt.PreloadClasses == "true")
 * @run junit/othervm NullRestrictedFieldValidation
 */

import java.lang.invoke.ReflectiveAccessOnly;
import java.lang.invoke.StableFieldAccessors;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.NullRestricted;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class NullRestrictedFieldValidation {
    private static value class Value { }

    private static class Fields {
        @ReflectiveAccessOnly
        @NullRestricted
        private Value instanceRestricted;
        @ReflectiveAccessOnly
        @NullRestricted
        private static Value staticRestricted = new Value();
        @ReflectiveAccessOnly
        private Value instanceNullable;
        @ReflectiveAccessOnly
        private static Value staticNullable;

        Fields() {
            instanceRestricted = new Value();
            super();
        }
    }

    @Test
    void nullRestrictedInstanceFieldsAreRejected() throws ReflectiveOperationException {
        checkInstance("instanceRestricted", true);
    }

    @Test
    void nullRestrictedStaticFieldsAreRejected() throws ReflectiveOperationException {
        checkStatic("staticRestricted", true);
    }

    @Test
    void nullableInstanceFieldsReachPrototypeStub() throws ReflectiveOperationException {
        checkInstance("instanceNullable", false);
    }

    @Test
    void nullableStaticFieldsReachPrototypeStub() throws ReflectiveOperationException {
        checkStatic("staticNullable", false);
    }

    private static void checkInstance(String name, boolean nullRestricted)
            throws ReflectiveOperationException {
        Class<? extends RuntimeException> expected = expectedException(name, nullRestricted);
        assertThrows(expected, () ->
                StableFieldAccessors.ofField(Fields.class, Value.class, name, Void.class,
                        (_, _) -> unexpectedComputation()));
    }

    private static void checkStatic(String name, boolean nullRestricted)
            throws ReflectiveOperationException {
        Class<? extends RuntimeException> expected = expectedException(name, nullRestricted);
        assertThrows(expected, () ->
                StableFieldAccessors.ofStaticField(Fields.class, Value.class, name, Void.class,
                        (_, _) -> unexpectedComputation()));
    }

    private static Class<? extends RuntimeException> expectedException(
            String name, boolean nullRestricted) throws ReflectiveOperationException {
        assertEquals(nullRestricted,
                ValueClass.isNullRestrictedField(Fields.class.getDeclaredField(name)));
        // Nullable fields pass validation and reach the unimplemented accessor body.
        return nullRestricted ? IllegalArgumentException.class : UnsupportedOperationException.class;
    }

    private static Value unexpectedComputation() {
        throw new AssertionError("A factory must not invoke its computing function");
    }
}
