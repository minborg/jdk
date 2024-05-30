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

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiPredicate;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.IntStream;
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
        assertEquals("StableArray[]", array.toString());
    }

    @Test
    void unset() {
        StableArray<Integer> array = StableArray.of(LENGTH);
        assertEquals(LENGTH, array.length());
        assertNull(array.orElse(INDEX, null));
        assertThrows(NoSuchElementException.class, () -> array.orElseThrow(INDEX));
        assertEquals("StableArray[.unset, .unset, .unset]", array.toString());
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
        assertTrue(array.trySet(INDEX, 42));
        assertEquals("StableArray[.unset, [42], .unset]", array.toString());
        assertEquals(42, array.orElse(INDEX, null));
        assertFalse(array.trySet(INDEX, null));
        assertFalse(array.trySet(INDEX, 1));
        assertThrows(IllegalStateException.class, () -> array.setOrThrow(INDEX, 1));
    }

    @Test
    void computeIfUnset() {
        StableArray<Integer> array = StableArray.of(LENGTH);
        assertEquals(42, array.computeIfUnset(INDEX, _ -> 42));
        assertEquals(42, array.computeIfUnset(INDEX, _ -> 13));
        assertEquals("StableArray[.unset, [42], .unset]", array.toString());
        assertEquals(42, array.orElse(INDEX, null));
        assertFalse(array.trySet(INDEX, null));
        assertFalse(array.trySet(INDEX, 1));
        assertThrows(IllegalStateException.class, () -> array.setOrThrow(INDEX, 1));
    }

    @Test
    void computeIfUnsetException() {
        StableArray<Integer> array = StableArray.of(LENGTH);
        IntFunction<Integer> mapper = _ -> {
            throw new UnsupportedOperationException("aaa");
        };
        var x = assertThrows(UnsupportedOperationException.class, () -> array.computeIfUnset(INDEX, mapper));
        assertTrue(x.getMessage().contains("aaa"));
        assertEquals(42, array.computeIfUnset(INDEX, _ -> 42));
        assertEquals("StableArray[.unset, [42], .unset]", array.toString());
        assertEquals(42, array.orElse(INDEX, 13));
        assertFalse(array.trySet(INDEX, null));
        assertFalse(array.trySet(INDEX, 1));
        assertThrows(IllegalStateException.class, () -> array.setOrThrow(INDEX, 1));
    }

    @Test
    void testHashCode() {
        StableArray<Integer> a0 = StableArray.of(LENGTH);
        StableArray<Integer> a1 = StableArray.of(LENGTH);
        assertEquals(a0.hashCode(), a1.hashCode());
        a0.setOrThrow(INDEX, 42);
        a1.setOrThrow(INDEX, 42);
        assertEquals(a0.hashCode(), a1.hashCode());
    }

    @Test
    void testEquals() {
        StableArray<Integer> a0 = StableArray.of(LENGTH);
        StableArray<Integer> a1 = StableArray.of(LENGTH);
        assertEquals(a0, a1);
        a0.setOrThrow(INDEX, 42);
        a1.setOrThrow(INDEX, 42);
        assertEquals(a0, a1);
        StableArray<Integer> other = StableArray.of(LENGTH);
        other.setOrThrow(INDEX, 13);
        assertNotEquals(a0, other);
        assertNotEquals(a0, "a");
    }

    private static final BiPredicate<StableArray<Integer>, Integer> TRY_SET = (s, i) -> s.trySet(INDEX, i);
    private static final BiPredicate<StableArray<Integer>, Integer> SET_OR_THROW = (s, i) -> {
        try {
            s.setOrThrow(INDEX, i);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    };
    private static final BiPredicate<StableArray<Integer>, Integer> COMPUTE_IF_UNSET = (s, i) -> {
        int r = s.computeIfUnset(INDEX, _ -> i);
        return r == i;
    };

    @Test
    void raceTrySet() {
        race(TRY_SET);
    }

    @Test
    void raceSetOrThrow() {
        race(SET_OR_THROW);
    }

    @Test
    void raceComputeIfUnset() {
        race(COMPUTE_IF_UNSET);
    }

    @Test
    void raceMixed() {
        race((s, i) -> switch (i % 3) {
            case 0 -> TRY_SET.test(s, i);
            case 1 -> SET_OR_THROW.test(s, i);
            case 2 -> COMPUTE_IF_UNSET.test(s, i);
            default -> fail("should not reach here");
        });
    }

    void race(BiPredicate<StableArray<Integer>, Integer> winnerPredicate) {
        int noThreads = 10;
        CountDownLatch starter = new CountDownLatch(1);
        StableArray<Integer> stable = StableArray.of(LENGTH);
        BitSet winner = new BitSet(noThreads);
        List<Thread> threads = IntStream.range(0, noThreads).mapToObj(i -> new Thread(() -> {
                    try {
                        // Ready, set ...
                        starter.await();
                        // Here we go!
                        winner.set(i, winnerPredicate.test(stable, i));
                    } catch (Throwable t) {
                        fail(t);
                    }
                }))
                .toList();
        threads.forEach(Thread::start);
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        // Start the race
        starter.countDown();
        threads.forEach(StableArrayTest::join);
        // There can only be one winner
        assertEquals(1, winner.cardinality());
    }

    private static void join(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException e) {
            fail(e);
        }
    }

}
