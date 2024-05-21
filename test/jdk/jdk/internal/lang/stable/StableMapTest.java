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
 * @summary Basic tests for stable Map implementations
 * @modules java.base/jdk.internal.lang
 * @compile --enable-preview -source ${jdk.version} StableMapTest.java
 * @compile StableTestUtil.java
 * @run junit/othervm --enable-preview StableMapTest
 */

import jdk.internal.lang.StableCollections;
import jdk.internal.lang.StableValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class StableMapTest {

    private static final Function<String, Integer> MAPPER = s -> (int) s.charAt(0);
    private static final String[] KEYS = "A,B,C,D,E,F,G".split(",");
    private static final String KEY = "C";


    @Test
    void basic() {
        for (var set : sets()) {
            Map<String, Integer> map = StableCollections.ofMap(set, MAPPER);
            assertEquals(set.isEmpty(), map.isEmpty());

            for (String key : set) {
                assertEquals(MAPPER.apply(key), map.get(key));
            }

            for (String key : set) {
                assertTrue(map.containsKey(key));
            }

            assertEquals(expectedToString(map), map.toString());

            if (!map.isEmpty()) {
                assertFalse(map instanceof Serializable);
            }

        }
    }

    @Test
    void entrySet() {
        for (var set : sets()) {
            Map<String, Integer> map = StableCollections.ofMap(set, MAPPER);
            var es = map.entrySet();
            assertEquals(set.size(), es.size());
        }
    }

    @Test
    void entrySetIterator() {
        for (var set : sets()) {
            Map<String, Integer> map = StableCollections.ofMap(set, MAPPER);
            var i = map.entrySet().iterator();

            Set<String> seen = new HashSet<>();
            while (i.hasNext()) {
                seen.add(i.next().getKey());
            }
            assertEquals(set, seen);
        }
    }

    @ParameterizedTest
    @MethodSource("unsupportedOperations")
    void uoe(String name, Consumer<Map<String, Integer>> op) {
        for (var map:maps()) {
            assertThrows(UnsupportedOperationException.class, () -> op.accept(map), name);
        }
    }

    @ParameterizedTest
    @MethodSource("nullOperations")
    void npe(String name, Consumer<Map<String, Integer>> op) {
        for (var map:maps()) {
            assertThrows(NullPointerException.class, () -> op.accept(map), name);
        }
    }

    private static List<Set<String>> sets() {
        return IntStream.range(0, KEY.length())
                .mapToObj(i -> Arrays.copyOfRange(KEYS, 0, i))
                .map(Set::of)
                .toList();
    }

    private static List<Map<String, Integer>> maps() {
        return sets().stream()
                .map(s -> StableCollections.ofMap(s, MAPPER))
                .toList();
    }

    private static Stream<Arguments> unsupportedOperations() {
        return Stream.of(
                Arguments.of("clear",            asConsumer(Map::clear)),
                Arguments.of("put",              asConsumer(m -> m.put(KEY, StableValue.of()))),
                Arguments.of("remove(K)",        asConsumer(m -> m.remove(KEY))),
                Arguments.of("remove(K, V)",     asConsumer(m -> m.remove(KEY, StableValue.of()))),
                Arguments.of("putAll(K, V)",     asConsumer(m -> m.putAll(new HashMap<>()))),
                Arguments.of("replaceAll",       asConsumer(m -> m.replaceAll((_, _) -> null))),
                Arguments.of("putIfAbsent",      asConsumer(m -> m.putIfAbsent(KEY, StableValue.of()))),
                Arguments.of("replace(K, V)",    asConsumer(m -> m.replace(KEY, StableValue.of()))),
                Arguments.of("replace(K, V, V)", asConsumer(m -> m.replace(KEY, StableValue.of(), StableValue.of()))),
                Arguments.of("computeIfAbsent",  asConsumer(m -> m.computeIfAbsent(KEY, _ -> StableValue.of()))),
                Arguments.of("computeIfPresent", asConsumer(m -> m.computeIfPresent(KEY, (_, _) -> StableValue.of()))),
                Arguments.of("compute",          asConsumer(m -> m.compute(KEY, (_, _) -> StableValue.of()))),
                Arguments.of("merge",            asConsumer(m -> m.merge(KEY, StableValue.of(), (_, _) -> StableValue.of()))),
                Arguments.of("es().it().remove", asConsumer(m -> m.entrySet().iterator().remove()))
        );
    }

    private static Stream<Arguments> nullOperations() {
        return Stream.of(
                Arguments.of("forEach", asConsumer(m -> m.forEach(null)))
        );
    }

    private static Consumer<Map<String, StableValue<Integer>>> asConsumer(Consumer<Map<String, StableValue<Integer>>> consumer) {
        return consumer;
    }

    static String expectedToString(Map<String, Integer> map) {
        return "{" + map.entrySet()
                .stream().map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", ")) + "}";
    }

}