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
 * @summary Basic tests for stable lists
 * @modules java.base/jdk.internal.lang
 * @compile --enable-preview -source ${jdk.version} StableListTest.java
 * @compile StableTestUtil.java
 * @run junit/othervm --enable-preview StableListTest
 */

import jdk.internal.lang.StableCollections;
import jdk.internal.lang.StableValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class StableListTest {

    private static final IntFunction<Integer> MAPPER = i -> i;
    private static final int MAX_SIZE = 1_000;
    private static final int[] SIZES = new int[]{0, 1, 2, 7, MAX_SIZE};

    private List<Integer> list;

    @ParameterizedTest
    @MethodSource("sizes")
    void size(int size) {
        newList(size);
        assertEquals(size, list.size());
    }

    @ParameterizedTest
    @MethodSource("sizes")
    void isEmpty(int size) {
        newList(size);
        assertEquals(size == 0, list.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("sizes")
    void get(int size) {
        newList(size);
        for (int j = 0; j < 2; j++) {
            for (int i = 0; i < size; i++) {
                assertEquals(i, list.get(i));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("sizes")
    void serializable(int size) {
        newList(size);
        if (size > 0) {
            assertFalse(list instanceof Serializable);
        }
    }

    @ParameterizedTest
    @MethodSource("sizes")
    void toString(int size) {
        newList(size);
        String actual = list.toString();
        String expected = new ArrayList<>(list).toString();
        assertEquals(expected, actual);
    }

    @Test
    void computeException() {
        String text = "abc123";
        int index = 3;
        list = StableCollections.ofList(index * 2, i -> {
            if (i == index) {
                throw new UnsupportedOperationException(text);
            }
            return i;
        });
        // Initial call
        UnsupportedOperationException uoe = assertThrows(UnsupportedOperationException.class, () -> list.get(index));
        assertTrue(uoe.getMessage().contains(text));
        // Subsequent call
        NoSuchElementException nse = assertThrows(NoSuchElementException.class, () -> list.get(index));
        assertTrue(nse.getMessage().contains(UnsupportedOperationException.class.getSimpleName()));
        assertFalse(nse.getMessage().contains(text));
        String s = list.toString();
        assertTrue(s.contains(".error(" + UnsupportedOperationException.class.getName() + ")"));
    }

    @ParameterizedTest
    @MethodSource("unsupportedOperations")
    void uoe(String name, Consumer<List<Integer>> op) {
        for (int size : SIZES) {
            newList(size);
            assertThrows(UnsupportedOperationException.class, () -> op.accept(list), name);
        }
    }

    @ParameterizedTest
    @MethodSource("nullOperations")
    void npe(String name, Consumer<List<Integer>> op) {
        for (int size : SIZES) {
            newList(size);
            assertThrows(NullPointerException.class, () -> op.accept(list), name);
        }
    }

    private void newList(int size) {
        list = StableCollections.ofList(size, MAPPER);
    }

    private static Stream<Arguments> sizes() {
        return IntStream.of(SIZES)
                .mapToObj(Arguments::of);
    }

    private static Stream<Arguments> unsupportedOperations() {
        return Stream.of(
                Arguments.of("add",          asConsumer(l -> l.add(StableValue.of()))),
                Arguments.of("remove",       asConsumer(l -> l.remove(1))),
                Arguments.of("addAll(C)",    asConsumer(l -> l.addAll(List.of()))),
                Arguments.of("addAll(i, C)", asConsumer(l -> l.addAll(1, List.of()))),
                Arguments.of("removeAll",    asConsumer(l -> l.removeAll(List.<StableValue<Integer>>of()))),
                Arguments.of("retainAll",    asConsumer(l -> l.retainAll(List.<StableValue<Integer>>of()))),
                Arguments.of("replaceAll",   asConsumer(l -> l.replaceAll(_ -> StableValue.of()))),
                Arguments.of("sort",         asConsumer(l -> l.sort(null))),
                Arguments.of("clear",        asConsumer(List::clear)),
                Arguments.of("set(i, E)",    asConsumer(l -> l.set(1, StableValue.of()))),
                Arguments.of("add(i, E)",    asConsumer(l -> l.add(1, StableValue.of()))),
                Arguments.of("remove(i)",    asConsumer(l -> l.remove(1))),
                Arguments.of("removeIf",     asConsumer(l -> l.removeIf(Objects::isNull))),
                Arguments.of("addFirst",     asConsumer(l -> l.addFirst(StableValue.of()))),
                Arguments.of("addLast",      asConsumer(l -> l.addLast(StableValue.of()))),
                Arguments.of("removeFirst",  asConsumer(List::removeFirst)),
                Arguments.of("removeLast",   asConsumer(List::removeLast))
        );
    }

    private static Stream<Arguments> nullOperations() {
        return Stream.of(
                Arguments.of("toArray",     asConsumer(l -> l.toArray((Object[]) null))),
                Arguments.of("toArray",     asConsumer(l -> l.toArray((IntFunction<StableValue<Integer>[]>) null))),
                Arguments.of("containsAll", asConsumer(l -> l.containsAll(null))),
                Arguments.of("forEach",     asConsumer(l -> l.forEach(null))),
                Arguments.of("indexOf",     asConsumer(l -> l.indexOf(null))),
                Arguments.of("lastIndexOf", asConsumer(l -> l.lastIndexOf(null)))
        );
    }

    private static Consumer<List<StableValue<Integer>>> asConsumer(Consumer<List<StableValue<Integer>>> consumer) {
        return consumer;
    }
}
