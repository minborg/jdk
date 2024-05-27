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
 * @summary Basic tests for StableArray implementations
 * @modules java.base/jdk.internal.lang
 * @compile --enable-preview -source ${jdk.version} StableArrayTest.java
 * @run junit/othervm --enable-preview StableArrayTest
 */

import jdk.internal.lang.StableArray;
import jdk.internal.lang.StableValue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class StableArrayTest {

    private static final int LENGTH = 3;
    private static final int INDEX = 1;

    @Test
    void empty() {
        StableArray<Integer> array = StableArray.of(0);
        assertThrows(IndexOutOfBoundsException.class, () -> array.orElse(0, null));
        assertThrows(IndexOutOfBoundsException.class, () -> array.orElseThrow(0));
        assertThrows(IndexOutOfBoundsException.class, () -> array.trySet(0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> array.setOrThrow(0, 0));
        assertEquals("StableArray[]",array.toString());
    }

    @Test
    void unset() {
        StableArray<Integer> array = StableArray.of(LENGTH);
        assertEquals(LENGTH, array.length());
        assertNull(array.orElse(INDEX, null));
        assertThrows(NoSuchElementException.class, () -> array.orElseThrow(INDEX));
        assertEquals("StableArray[.unset, .unset, .unset]",array.toString());
        assertTrue(array.trySet(INDEX, null));
        assertFalse(array.trySet(INDEX, null));
        assertFalse(array.trySet(INDEX, INDEX));
        assertNull(array.orElseThrow(INDEX));
    }

    @Test
    void setNull() {
        StableArray<Integer> array = StableArray.of(LENGTH);
        assertTrue(array.trySet(INDEX, null));
        assertEquals("StableArray[.unset, [null], .unset]", array.toString());
        assertNull(array.orElse(INDEX, null));
        assertNull(array.orElseThrow(INDEX));
        assertFalse(array.trySet(INDEX, null));
        assertFalse(array.trySet(INDEX, 1));
    }

    @Test
    void setNonNull() {
        StableArray<Integer> array = StableArray.of(LENGTH);
        assertTrue(array.trySet(INDEX,42));
        assertEquals("StableArray[.unset, [42], .unset]", array.toString());
        assertEquals(42, array.orElse(INDEX, null));
        assertFalse(array.trySet(INDEX, null));
        assertFalse(array.trySet(INDEX, 1));
        assertThrows(IllegalStateException.class, () -> array.setOrThrow(INDEX, 1));
    }

}
