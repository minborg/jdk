/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Basic tests for TrustedFieldType implementations
 * @modules java.base/jdk.internal.lang
 * @modules java.base/jdk.internal.lang.stable
 * @compile --enable-preview -source ${jdk.version} TrustedFieldTypeTest.java
 * @run junit/othervm --enable-preview TrustedFieldTypeTest
 */

import jdk.internal.lang.StableArray;
import jdk.internal.lang.StableValue;
import jdk.internal.lang.stable.TrustedFieldType;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;

import static org.junit.jupiter.api.Assertions.*;

public class TrustedFieldTypeTest {

    @Test
    void trustedFieldType() {
        assertTrue(TrustedFieldType.class.isAssignableFrom(StableValue.class));
        assertTrue(TrustedFieldType.class.isAssignableFrom(StableValue.class));
    }

    @Test
    void reflection() throws NoSuchFieldException {
        final class Holder {
            private final StableValue<Integer> value = StableValue.of();
            private final StableArray<Integer> array = StableArray.of(0);
        }
        final class HolderNonFinal {
            private StableValue<Integer> value = StableValue.of();
            private StableArray<Integer> array = StableArray.of(0);
        }

        Field valueField = Holder.class.getDeclaredField("value");
        assertThrows(InaccessibleObjectException.class, () ->
                valueField.setAccessible(true)
        );
        Field arrayField = Holder.class.getDeclaredField("array");
        assertThrows(InaccessibleObjectException.class, () ->
                arrayField.setAccessible(true)
        );

        Field valueNonFinal = HolderNonFinal.class.getDeclaredField("value");
        assertDoesNotThrow(() -> valueNonFinal.setAccessible(true));
        Field arrayNonFinal = HolderNonFinal.class.getDeclaredField("array");
        assertDoesNotThrow(() -> arrayNonFinal.setAccessible(true));
    }

    @Test
    void sunMiscUnsafe() throws NoSuchFieldException, IllegalAccessException {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe)unsafeField.get(null);

        final class Holder {
            private final StableValue<Integer> value = StableValue.of();
            private final StableArray<Integer> array = StableArray.of(1);
        }

        Field valueField = Holder.class.getDeclaredField("value");
        assertThrows(UnsupportedOperationException.class, () ->
                unsafe.objectFieldOffset(valueField)
        );
        Field arrayField = Holder.class.getDeclaredField("array");
        assertThrows(UnsupportedOperationException.class, () ->
                unsafe.objectFieldOffset(arrayField)
        );

    }

    @Test
    void varHandle() throws NoSuchFieldException, IllegalAccessException {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        StableValue<Integer> originalValue = StableValue.of();
        StableArray<Integer> originalArray = StableArray.of(1);

        final class Holder {
            private final StableValue<Integer> value = originalValue;
            private final StableArray<Integer> array = originalArray;
        }

        VarHandle valueVarHandle = lookup.findVarHandle(Holder.class, "value", StableValue.class);
        VarHandle arrayVarHandle = lookup.findVarHandle(Holder.class, "array", StableArray.class);
        Holder holder = new Holder();

        assertThrows(UnsupportedOperationException.class, () ->
                valueVarHandle.set(holder, StableValue.of())
        );
        assertThrows(UnsupportedOperationException.class, () ->
                arrayVarHandle.set(holder, StableValue.of())
        );

        assertThrows(UnsupportedOperationException.class, () ->
                valueVarHandle.compareAndSet(holder, originalValue, StableValue.of())
        );
        assertThrows(UnsupportedOperationException.class, () ->
                arrayVarHandle.compareAndSet(holder, originalArray, StableArray.of(1))
        );

    }

}
