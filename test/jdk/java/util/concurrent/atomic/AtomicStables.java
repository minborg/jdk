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
   @summary Test of getStable() semantics for atomic classes
   @run junit AtomicStables
 */

import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

final class AtomicStables {

    @Test
    void atomicReference() {
        var ab = new AtomicReference<Integer>();
        assertEquals(ab.get(), ab.getStable());
        ab.set(1);
        assertEquals(ab.get(), ab.getStable());
    }

    @Test
    void atomicBoolean() {
        var ab = new AtomicBoolean();
        assertEquals(ab.get(), ab.getStable());
        ab.set(true);
        assertEquals(ab.get(), ab.getStable());
    }

    @Test
    void atomicInteger() {
        var ab = new AtomicInteger();
        assertEquals(ab.get(), ab.getStable());
        ab.set(1);
        assertEquals(ab.get(), ab.getStable());
    }

    @Test
    void atomicLong() {
        var ab = new AtomicLong();
        assertEquals(ab.get(), ab.getStable());
        ab.set(1L);
        assertEquals(ab.get(), ab.getStable());
    }

    @Test
    void atomicReferenceVolatile() {
        var ab = new AtomicReference<Integer>();
        assertEquals(ab.get(), ab.getStableVolatile());
        ab.set(1);
        assertEquals(ab.get(), ab.getStableVolatile());
    }

    @Test
    void atomicBooleanVolatile() {
        var ab = new AtomicBoolean();
        assertEquals(ab.get(), ab.getStableVolatile());
        ab.set(true);
        assertEquals(ab.get(), ab.getStableVolatile());
    }

    @Test
    void atomicIntegerVolatile() {
        var ab = new AtomicInteger();
        assertEquals(ab.get(), ab.getStableVolatile());
        ab.set(1);
        assertEquals(ab.get(), ab.getStableVolatile());
    }

    @Test
    void atomicLongVolatile() {
        var ab = new AtomicLong();
        assertEquals(ab.get(), ab.getStableVolatile());
        ab.set(1L);
        assertEquals(ab.get(), ab.getStableVolatile());
    }

    // Arrays

    @Test
    void atomicReferenceArray() {
        var ab = new AtomicReferenceArray<Integer>(new Integer[1]);
        assertEquals(ab.get(0), ab.getStable(0));
        ab.set(0, 1);
        assertEquals(ab.get(0), ab.getStable(0));
    }

    @Test
    void atomicIntegerArray() {
        var ab = new AtomicIntegerArray(new int[1]);
        assertEquals(ab.get(0), ab.getStable(0));
        ab.set(0, 1);
        assertEquals(ab.get(0), ab.getStable(0));
    }

    @Test
    void atomicLongArray() {
        var ab = new AtomicLongArray(new long[1]);
        assertEquals(ab.get(0), ab.getStable(0));
        ab.set(0, 1L);
        assertEquals(ab.get(0), ab.getStable(0));
    }

    @Test
    void atomicReferenceArrayVolatile() {
        var ab = new AtomicReferenceArray<Integer>(new Integer[1]);
        assertEquals(ab.get(0), ab.getStableVolatile(0));
        ab.set(0, 1);
        assertEquals(ab.get(0), ab.getStableVolatile(0));
    }

    @Test
    void atomicIntegerArrayVolatile() {
        var ab = new AtomicIntegerArray(new int[1]);
        assertEquals(ab.get(0), ab.getStableVolatile(0));
        ab.set(0, 1);
        assertEquals(ab.get(0), ab.getStableVolatile(0));
    }

    @Test
    void atomicLongArrayVolatile() {
        var ab = new AtomicLongArray(new long[1]);
        assertEquals(ab.get(0), ab.getStableVolatile(0));
        ab.set(0, 1L);
        assertEquals(ab.get(0), ab.getStableVolatile(0));
    }

}