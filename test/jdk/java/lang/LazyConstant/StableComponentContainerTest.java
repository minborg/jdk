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
 * @summary Basic tests for the StableComponentContainer implementation
 * @enablePreview
 * @modules java.base/jdk.internal.access:+open
 * @run junit StableComponentContainerTest
 */

import jdk.internal.access.StableComponentContainer;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class StableComponentContainerTest {

    private static final Set<Class<? extends Number>> SET = Set.of(
            Byte.class,
            Short.class,
            Integer.class,
            Long.class,
            Float.class,
            Double.class);

    @Test
    void factoryInvariants() {
        assertThrows(NullPointerException.class, () -> StableComponentContainer.of(null));
        Set<Class<? extends Number>> setWithNull = new HashSet<>();
        setWithNull.add(Byte.class);
        setWithNull.add(Short.class);
        setWithNull.add(null);
        setWithNull.add(Integer.class);
        assertThrows(NullPointerException.class, () -> StableComponentContainer.of(setWithNull));
    }

    @Test
    void basic() {
        StableComponentContainer<Number> container = populated();
        for (Class<? extends Number> type : SET) {
            Number value = container.get(type);
            assertEquals(1, value.intValue(), type.toString());
        }
        var x0 = assertThrows(IllegalStateException.class, () -> container.set(Byte.class, (byte) 1));
        assertEquals("The component is already initialized: " + Byte.class.getName(), x0.getMessage());
        var x1 = assertThrows(IllegalArgumentException.class, () -> container.set(BigInteger.class, BigInteger.ONE));
        assertTrue(x1.getMessage().startsWith("The type '" + BigInteger.class.getName() + "' is outside the allowed input types:"), x1.getMessage());
    }

    @Test
    void testToString() {
        StableComponentContainer<Number> container = populated();
        var toString = container.toString();
        assertTrue(toString.startsWith("StableComponentContainer{"), toString);
        for (Class<? extends Number> type : SET) {
            assertTrue(toString.contains(type.getName()+"=1"), toString);
        }
        assertTrue(toString.endsWith("}"), toString);
    }

    @Test
    void testToStringEmpty() {
        StableComponentContainer<Number> container = StableComponentContainer.of(Set.of());
        assertEquals("StableComponentContainer{}", container.toString());
    }

    @Test
    void computeIfAbsent() {
        StableComponentContainer<Number> container = StableComponentContainer.of(SET);
        Integer value = container.computeIfAbsent(Integer.class, StableComponentContainerTest::mapper);
        assertEquals(1, value);
        assertThrows(NullPointerException.class, () -> container.computeIfAbsent(Byte.class, StableComponentContainerTest::mapper));
    }

    static <C extends Number> C mapper(Class<C> type) {
        return type.cast(switch (type) {
            case Class<?> c when c.equals(Byte.class) -> null;
            case Class<?> c when c.equals(Integer.class) -> 1;
            default -> throw new NoSuchElementException(type.toString());
        });
    }

    private static StableComponentContainer<Number> populated() {
        StableComponentContainer<Number> container = StableComponentContainer.of(SET);
        container.set(Byte.class, (byte) 1);
        container.set(Short.class, (short) 1);
        container.set(Integer.class, 1);
        container.set(Long.class, 1L);
        container.set(Float.class, 1.0f);
        container.set(Double.class, 1.0d);
        return container;
    }

}
