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
 * @summary Basic tests for lazy map methods
 * @enablePreview
 * @run junit UnboundStableMapTest
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class UnboundedStableMapTest {

    private static final Function<Integer, Integer> MAPPER = Function.identity();

    private static final Integer KEY = 42;
    private static final Integer VALUE = MAPPER.apply(KEY);
    private static final List<Integer> LIST = IntStream.range(0, 42).boxed().toList();

    // Todo: test to associate a `null` value

    @ParameterizedTest
    @MethodSource("maps")
    void empty(NamedMap namedMap) {
        final var map = namedMap.map();
        assertTrue(map.isEmpty());
        assertEquals("{}", map.toString());
        assertThrows(NullPointerException.class, () -> map.get(null));
        assertNotEquals(null, map);
    }

    @ParameterizedTest
    @MethodSource("maps")
    void size(NamedMap namedMap) {
        final var map = namedMap.map();
        int size = 0;
        for (Integer v : LIST) {
            assertEquals(size++, map.size());
            map.put(v, v);
        }
    }

    @ParameterizedTest
    @MethodSource("maps")
    void entryIterator(NamedMap namedMap) {
        final var map = namedMap.map();
        var iter = map.entrySet().iterator();
        assertFalse(iter.hasNext());

        for (int i = 0; i < KEY; i++) {
            map.put(i, i);
            iter = map.entrySet().iterator();
            assertTrue(iter.hasNext());
            assertTrue(iter.hasNext()); // Check for double hasNext bugs

            var set = IntStream.rangeClosed(0, i)
                    .mapToObj(j -> Map.entry(j, j))
                    .collect(Collectors.toSet());

            for (int j = 0; j < i + 1; j++) {
                var entry = iter.next();
                assertTrue(set.contains(entry));
            }
            assertFalse(iter.hasNext());
        }
    }

    @ParameterizedTest
    @MethodSource("maps")
    void toString(NamedMap namedMap) {
        final var map = namedMap.map();
        for (Integer v : LIST) {
            namedMap.map.put(v, v);
            var toString = map.toString();
            var expectContains = v + "=" + v;
            assertTrue(toString.contains(expectContains), toString + " didn't contain " + expectContains);
        }
    }

    @ParameterizedTest
    @MethodSource("maps")
    void strangeKeyType(NamedMap namedMap) {
        final var map = namedMap.map();
        assertNull(map.get("String"));
        assertThrows(NullPointerException.class, () -> map.get(null));
    }

    @ParameterizedTest
    @MethodSource("mapFactories")
    void stressTest(NamedFactory namedFactory) {
        final int maxLen = 1 << 20;
        final var rnd = new Random(42);

        for (int run = 0; run < 100; run++) {
            final Map<Integer, Integer> actual = new HashMap<>();
            final Map<Integer, Integer> expected = namedFactory.factory.get();
            final int len = rnd.nextInt(maxLen);
            for (int i = 0; i < len; i++) {
                int key;
                do {
                    key = rnd.nextInt();
                } while (expected.containsKey(key));
                final int value = rnd.nextInt();
                actual.put(key,value);
                try {
                    expected.put(key, value);
                } catch (IllegalArgumentException e) {
                    System.out.println("Ooops");
                    throw e;
                }
            }
            assertEquals(expected, actual, diff(expected, actual));
        }
    }

    String diff(Map<Integer, Integer> expected, Map<Integer, Integer> actual) {
        if (expected.size() != actual.size()) {
            return "Size mismatch: " + expected.size() + " != " + actual.size();
        }
        for (Map.Entry<Integer, Integer> entry : expected.entrySet()) {
            Integer value = actual.get(entry.getKey());
            if (value != null) {
                return "No value: " + entry;
            }
        }
        return "Dunno ?";
    }

    @Test
    void strangeKeyType2() {
        Map<Integer, Integer> map = new HashMap<>();
        Map rawMap = map;
        Object o = rawMap.computeIfAbsent("String", Function.identity());
        assertTrue(o instanceof String);
    }

    record NamedMap(String name, Map<Integer, Integer> map) {
        @Override public String toString() { return name; }
    }

    private static Stream<NamedMap> maps() {
        return mapFactories()
                .map(nf -> new NamedMap(nf.name(), nf.factory().get()));
    }

    record NamedFactory(String name, Supplier<Map<Integer, Integer>> factory) {
        @Override public String toString() { return name; }
    }

    private static Stream<NamedFactory> mapFactories() {
        return Stream.of(
                new NamedFactory("ofStable()", () -> Map.<Integer, Integer>ofStable().toMap()),
                new NamedFactory("ofStable().c(4)",() -> Map.<Integer, Integer>ofStable().withInitialMappingCapacity(4).toMap()),
                new NamedFactory("ofStable().c(1024)", () -> Map.<Integer, Integer>ofStable().withInitialMappingCapacity(1024).toMap())
        );
    }

    static Map<Integer, Integer> newRegularMap(Set<Integer> set) {
        return set.stream()
                .collect(Collectors.toMap(Function.identity(), MAPPER));
    }

}
