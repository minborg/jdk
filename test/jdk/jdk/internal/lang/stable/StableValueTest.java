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
 * @summary Basic tests for StableValue implementations
 * @modules java.base/jdk.internal.lang
 * @compile --enable-preview -source ${jdk.version} StableValueTest.java
 * @run junit/othervm --enable-preview StableValueTest
 */

import jdk.internal.lang.StableValue;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class StableValueTest {

    @Test
    void unset() {
        StableValue<Integer> stable = StableValue.of();
        assertNull(stable.orElseNull());
        assertThrows(NoSuchElementException.class, stable::orElseThrow);
        assertEquals("StableValue[null]",stable.toString());
        assertTrue(stable.trySet(null));
        assertTrue(stable.trySet(null));
        assertTrue(stable.trySet(1));
        assertFalse(stable.trySet(2));
    }

    @Test
    void setNull() {
        StableValue<Integer> stable = StableValue.of();
        assertTrue(stable.trySet(null));
        assertEquals("StableValue[null]",stable.toString());
        assertNull(stable.orElseNull());
        assertThrows(NoSuchElementException.class, stable::orElseThrow);
        assertTrue(stable.trySet(null));
        assertTrue(stable.trySet(1));
    }

    @Test
    void setNonNull() {
        StableValue<Integer> stable = StableValue.of();
        assertTrue(stable.trySet(42));
        assertEquals("StableValue[42]",stable.toString());
        assertEquals(42, stable.orElseNull());
        assertFalse(stable.trySet(null));
        assertFalse(stable.trySet(1));
        assertThrows(IllegalStateException.class, () -> stable.setOrThrow(1));
    }

    @Test
    void ofList() {
        List<StableValue<Integer>> list = StableValue.ofList(4);
        assertEquals("java.util.ImmutableCollections$ListN", list.getClass().getName());
        assertEquals(Stream.generate(StableValue::of).limit(list.size()).toList().toString(), list.toString());
    }

    @Test
    void ofMap() {
        Set<Integer> keys = Set.of(0, 1, 2, 3);
        Map<Integer, StableValue<Integer>> list = StableValue.ofMap(keys);
        assertEquals("java.util.ImmutableCollections$MapN", list.getClass().getName());
        Map<Integer, StableValue<Integer>> expected = Map.of(
                0, StableValue.of(),
                1, StableValue.of(),
                2, StableValue.of(),
                3, StableValue.of());
        assertEquals(expected.toString(), list.toString());
    }

}
