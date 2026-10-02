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
 * @summary Named lazy locations retain inherited-field access through a public subclass
 * @enablePreview
 * @build lazyupdaters.inherited.PublicSubclass
 * @run junit/othervm InheritedPublicFieldTest
 */

import java.lang.invoke.LazyUpdaters;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;

import lazyupdaters.inherited.PublicSubclass;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class InheritedPublicFieldTest {
    public interface InstanceValue {
        int get(PublicSubclass receiver);
    }

    @Test
    public void publicInstanceFieldInheritedFromInaccessibleParent() {
        AtomicInteger computations = new AtomicInteger();
        InstanceValue updater = LazyUpdaters.ofLocation(
                LazyUpdaters.Mode.PUBLISH_ONCE,
                MethodHandles.publicLookup(), InstanceValue.class, "value",
                receiver -> computations.incrementAndGet() + 40);
        PublicSubclass first = new PublicSubclass();
        PublicSubclass second = new PublicSubclass();

        assertEquals(41, updater.get(first));
        assertEquals(41, updater.get(first));
        assertEquals(41, first.value);
        assertEquals(42, updater.get(second));
        assertEquals(42, second.value);
        assertEquals(2, computations.get());
    }

    @Test
    public void publicStaticFieldInheritedFromInaccessibleParent() {
        AtomicInteger computations = new AtomicInteger();
        IntSupplier updater = LazyUpdaters.ofLocation(
                LazyUpdaters.Mode.PUBLISH_ONCE,
                MethodHandles.lookup(), IntSupplier.class,
                PublicSubclass.class.getName() + ".staticValue",
                () -> computations.incrementAndGet() + 70);

        assertEquals(71, updater.getAsInt());
        assertEquals(71, updater.getAsInt());
        assertEquals(71, PublicSubclass.staticValue);
        assertEquals(1, computations.get());
    }

    @Test
    public void staticArrayFieldInheritedFromInaccessibleInterface() {
        AtomicInteger computations = new AtomicInteger();
        IntUnaryOperator updater = LazyUpdaters.ofLocation(
                LazyUpdaters.Mode.PUBLISH_ONCE,
                MethodHandles.lookup(), IntUnaryOperator.class,
                PublicSubclass.class.getName() + ".ARRAY",
                index -> {
                    computations.incrementAndGet();
                    return index + 51;
                });

        assertEquals(51, updater.applyAsInt(0));
        assertEquals(52, updater.applyAsInt(1));
        assertEquals(51, updater.applyAsInt(0));
        assertEquals(51, PublicSubclass.ARRAY[0]);
        assertEquals(52, PublicSubclass.ARRAY[1]);
        assertEquals(2, computations.get());
    }

    @Test
    public void ambiguousInheritedArrayFieldIsRejected() {
        AtomicInteger computations = new AtomicInteger();
        assertThrows(IllegalArgumentException.class,
                () -> LazyUpdaters.ofLocation(
                        LazyUpdaters.Mode.PUBLISH_ONCE,
                        MethodHandles.lookup(), IntUnaryOperator.class,
                        PublicSubclass.Ambiguous.class.getName() + ".DUPLICATE",
                        index -> computations.incrementAndGet()));

        assertEquals(0, computations.get());
    }

    @Test
    public void protectedFieldIsStillInaccessible() {
        AtomicInteger computations = new AtomicInteger();
        IllegalArgumentException denied = assertThrows(IllegalArgumentException.class,
                () -> LazyUpdaters.ofLocation(
                        LazyUpdaters.Mode.PUBLISH_ONCE,
                        MethodHandles.publicLookup(), InstanceValue.class,
                        "protectedValue", receiver -> computations.incrementAndGet()));

        assertInstanceOf(IllegalAccessException.class, denied.getCause());
        assertEquals(0, computations.get());
    }
}
