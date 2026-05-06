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
 * @bug 8316493
 * @summary Make sure map views methods always returns a _new_ view and that they are ValueBased.
 * @modules java.base/jdk.internal
 * @run junit MapViews
 */

import jdk.internal.ValueBased;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.annotation.Annotation;
import java.util.AbstractMap;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.SequencedMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class MapViews {

    // These are classes that cannot be annotated with @ValueClass.
    private static final Set<String> NON_VALUE_CLASS_NAMES = Set.of(
            "java.util.TreeMap$AscendingSubMap$AscendingEntrySetView",   // Mutable field(s) in superclass
            "java.util.TreeMap$DescendingSubMap$DescendingEntrySetView"  // Mutable field(s) in superclass
    );

    private static final Map<String, Integer> DATA = Map.of("a", 1, "b", 2, "c", 3);

    enum MyEnum { A, B, C }

    @ParameterizedTest
    @MethodSource("maps")
    void mapViews(NamedSupplier<Map<?, ?>> mapCase) {
        assertFreshMapViews(mapCase.name(), mapCase.get());
    }

    @ParameterizedTest
    @MethodSource("navigableMaps")
    void navigableMapViews(NamedSupplier<NavigableMap<String, Integer>> mapCase) {
        assertFreshNavigableMapViews(mapCase.name(), mapCase.get());
    }

    @ParameterizedTest
    @MethodSource("concurrentNavigableMaps")
    void concurrentNavigableMapViews(NamedSupplier<ConcurrentNavigableMap<String, Integer>> mapCase) {
        assertFreshNavigableMapViews(mapCase.name(), mapCase.get());
    }

    @Test
    void sequencedMapViews() {
        var linkedHashMap = new LinkedHashMap<>(DATA);
        assertFreshSequencedMapViews("LinkedHashMap", linkedHashMap);
        assertFresh(linkedHashMap::reversed, "LinkedHashMap.reversed()");

        var reversed = linkedHashMap.reversed();
        assertFreshSequencedMapViews("LinkedHashMap.reversed()", reversed);
    }


    static void assertFreshMapViews(String name, Map<?, ?> map) {
        assertFresh(map::keySet, name + ".keySet()");
        assertFresh(map::values, name + ".values()");
        assertFresh(map::entrySet, name + ".entrySet()");
    }

    static void assertFreshNavigableMapViews(String name, NavigableMap<?, ?> map) {
        assertFreshMapViews(name, map);
        assertFresh(map::navigableKeySet, name + ".navigableKeySet()");
        assertFresh(map::descendingKeySet, name + ".descendingKeySet()");
        assertFresh(map::descendingMap, name + ".descendingMap()");
    }

    static void assertFreshSequencedMapViews(String name, SequencedMap<?, ?> map) {
        assertFreshMapViews(name, map);
        assertFresh(map::sequencedKeySet, name + ".sequencedKeySet()");
        assertFresh(map::sequencedValues, name + ".sequencedValues()");
        assertFresh(map::sequencedEntrySet, name + ".sequencedEntrySet()");
    }

    static void assertFresh(Supplier<?> viewFactory, String description) {
        var view = viewFactory.get();
        assertNotSame(view, viewFactory.get(), description);
        Class<?> implementingClass = view.getClass();

        // As we are creating fresh instances, we also want them to be @ValueBased
        // in order to improve scalar replacement probability/performance
        if (!NON_VALUE_CLASS_NAMES.contains(implementingClass.getName())) {
            assertHasAnnotation(ValueBased.class, viewFactory.get().getClass(), description);
        }
    }

    static EnumMap<MyEnum, Integer> enumMap() {
        var map = new EnumMap<MyEnum, Integer>(MyEnum.class);
        map.put(MyEnum.A, 1);
        map.put(MyEnum.B, 2);
        map.put(MyEnum.C, 3);
        return map;
    }

    static IdentityHashMap<String, Integer> identityHashMap() {
        var map = new IdentityHashMap<String, Integer>();
        DATA.forEach(map::put);
        return map;
    }

    record NamedSupplier<T>(String name, Supplier<? extends T> supplier) implements Supplier<T> {
        @Override public T get() { return supplier.get(); }
        @Override public String toString() { return name; }
    }

    // Convenience method to reduce cermony
    static <T> NamedSupplier<T> ns(String name, Supplier<? extends T> supplier) {
        return new NamedSupplier<>(name, supplier);
    }

    static final class ConcreteAbstractMap extends AbstractMap<String, Integer> {
        private final Map<String, Integer> map = new LinkedHashMap<>(DATA);

        @Override
        public Set<Entry<String, Integer>> entrySet() {
            return map.entrySet();
        }
    }

    static <A extends Annotation> void assertHasAnnotation(Class<A> annotationType,
                                                           Class<?> clazz,
                                                           String description) {
        assertTrue(clazz.isAnnotationPresent(annotationType),
                description + " : " + clazz.toString() + " is not annotated with @" + annotationType.getSimpleName());
    }

    // Sources

    static Stream<NamedSupplier<Map<?, ?>>> maps() {
        return Stream.of(
                ns("AbstractMap", ConcreteAbstractMap::new),
                ns("ConcurrentHashMap", () -> new ConcurrentHashMap<>(DATA)),
                ns("ConcurrentSkipListMap", () -> new ConcurrentSkipListMap<>(DATA)),
                ns("EnumMap", MapViews::enumMap),
                ns("HashMap", () -> new HashMap<>(DATA)),
                ns("IdentityHashMap", MapViews::identityHashMap),
                ns("LinkedHashMap", () -> new LinkedHashMap<>(DATA)),
                ns("Map.of()", Map::of),
                ns("Map.of(k, v)", () -> Map.of("a", 1)),
                ns("Map.of(k1, v1, k2, v2)", () -> Map.of("a", 1, "b", 2)),
                ns("TreeMap", () -> new TreeMap<>(DATA)),
                ns("WeakHashMap", () -> new WeakHashMap<>(DATA))
        );
    }

    static Stream<NamedSupplier<NavigableMap<String, Integer>>> navigableMaps() {
        return Stream.of(
                ns("TreeMap", () -> new TreeMap<>(DATA)),
                ns("TreeMap.descendingMap()", () -> new TreeMap<>(DATA).descendingMap()),
                ns("TreeMap.headMap()", () -> new TreeMap<>(DATA).headMap("c", true)),
                ns("TreeMap.subMap()", () -> new TreeMap<>(DATA).subMap("a", true, "c", true)),
                ns("TreeMap.subMap().descendingMap()",
                        () -> new TreeMap<>(DATA).subMap("a", true, "c", true).descendingMap()),
                ns("TreeMap.tailMap()", () -> new TreeMap<>(DATA).tailMap("a", true))
        );
    }

    static List<NamedSupplier<ConcurrentNavigableMap<String, Integer>>> concurrentNavigableMaps() {
        return List.of(
                ns("ConcurrentSkipListMap", () -> new ConcurrentSkipListMap<>(DATA)),
                ns("ConcurrentSkipListMap.descendingMap()",
                        () -> new ConcurrentSkipListMap<>(DATA).descendingMap()),
                ns("ConcurrentSkipListMap.headMap()",
                        () -> new ConcurrentSkipListMap<>(DATA).headMap("c", true)),
                ns("ConcurrentSkipListMap.subMap()",
                        () -> new ConcurrentSkipListMap<>(DATA).subMap("a", true, "c", true)),
                ns("ConcurrentSkipListMap.subMap().descendingMap()",
                        () -> new ConcurrentSkipListMap<>(DATA).subMap("a", true, "c", true).descendingMap()),
                ns("ConcurrentSkipListMap.tailMap()",
                        () -> new ConcurrentSkipListMap<>(DATA).tailMap("a", true))
        );
    }

}
