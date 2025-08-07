/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Basic tests for trusting synthetic fields
 * @modules jdk.unsupported/sun.misc
 * @modules java.base/jdk.internal.misc
 * @run junit TestSyntheticTrusted
 */

import jdk.internal.misc.Unsafe;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

final class TestSyntheticTrusted {

    record Outer(String s) {
        class Inner {
            int i;

            String s() {
                return s;
            }
        }
    }


    @Test
    void reflection() throws NoSuchFieldException, IllegalAccessException {
        var outer = new Outer("A");
        var inner = outer.new Inner();
        var outer2 = new Outer("B");

        Field this$0Field = Outer.Inner.class.getDeclaredField("this$0");
        this$0Field.setAccessible(true);

        // We should be able to read the `this$0` field
        Object read = this$0Field.get(inner);
        assertSame(outer, read);
        // We should NOT be able to write to the StableValue field
        assertThrows(IllegalAccessException.class, () ->
                this$0Field.set(inner, outer2)
        );

    }

    @SuppressWarnings("removal")
    @Test
    void sunMiscUnsafe() throws NoSuchFieldException, IllegalAccessException {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        assertTrue(unsafeField.trySetAccessible());
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe)unsafeField.get(null);

        Field valueField = Outer.Inner.class.getDeclaredField("this$0");
        assertThrows(UnsupportedOperationException.class, () ->
                unsafe.objectFieldOffset(valueField)
        );
    }

    @Test
    void varHandle() throws NoSuchFieldException, IllegalAccessException {
        var outer = new Outer("A");
        var inner = outer.new Inner();
        var outer2 = new Outer("B");

        MethodHandles.Lookup lookup = MethodHandles.lookup();
        VarHandle valueVarHandle = lookup.findVarHandle(Outer.Inner.class, "this$0", Outer.class);

        assertThrows(UnsupportedOperationException.class, () ->
                valueVarHandle.set(inner, outer2)
        );

        assertThrows(UnsupportedOperationException.class, () ->
                valueVarHandle.compareAndSet(inner, outer, outer2)
        );

    }

    @Test
    void updateStableValueContentVia_j_i_m_Unsafe() {
        var outer = new Outer("A");
        var inner = outer.new Inner();
        var outer2 = new Outer("B");
        jdk.internal.misc.Unsafe unsafe = Unsafe.getUnsafe();

        long offset = unsafe.objectFieldOffset(Outer.Inner.class, "this$0");
        assertTrue(offset > 0);

        // Unfortunately, it is possible to update the enclosing instance via jdk.internal.misc.Unsafe
        Object oldData = unsafe.getAndSetReference(inner, offset, outer2);
        assertEquals("B", inner.s());
    }

}