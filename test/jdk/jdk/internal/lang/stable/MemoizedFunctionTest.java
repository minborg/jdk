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
 * @compile --enable-preview -source ${jdk.version} MemoizedFunctionTest.java
 * @run junit/othervm --enable-preview MemoizedFunctionTest
 */

import jdk.internal.lang.StableArray;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MemoizedFunctionTest {

    private static final int SIZE = 3;
    private static final int INDEX = 1;
    private static final Set<Integer> INPUTS = IntStream.range(0, SIZE).boxed().collect(Collectors.toSet());
    private static final Function<Integer, Integer> FUNCTION = i -> i;

    @Test
    void basic() {
        StableTestUtil.CountingFunction<Integer, Integer> original = new StableTestUtil.CountingFunction<>(FUNCTION);
        Function<Integer, Integer> function = StableArray.memoizedFunction(INPUTS, original);
        assertEquals(INDEX, function.apply(INDEX));
        assertEquals(1, original.cnt());
        assertEquals(INDEX, function.apply(INDEX));
        assertEquals(1, original.cnt());
    }

    @Test
    void toStringTest() {
        Function<Integer, Integer> function = StableArray.memoizedFunction(INPUTS, FUNCTION);
        String expectedEmpty = "MemoizedFunction[original=" + FUNCTION;
        assertTrue(function.toString().startsWith(expectedEmpty), function.toString());
        function.apply(INDEX);
        assertTrue(function.toString().contains("NonNull"), function.toString());
    }

}
