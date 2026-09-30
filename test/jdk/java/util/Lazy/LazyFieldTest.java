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
 * @summary Lazy field functions check lookup access, safely publish a winning value,
 *          and retry failed computations
 * @enablePreview
 * @library /test/lib
 * @modules java.base/jdk.internal.vm.annotation
 * @build fields.FieldOwner helpers.LazyFieldFactory
 * @run junit/othervm LazyFieldTest
 * @run junit/othervm -XX:+UnlockDiagnosticVMOptions -XX:+UseNullableAtomicValueFlattening LazyFieldTest
 */

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Lazy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import fields.FieldOwner;
import helpers.LazyFieldFactory;
import jdk.internal.vm.annotation.NullRestricted;
import jdk.test.lib.Utils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LazyFieldTest {
    private static final long TIMEOUT_SECONDS = Utils.adjustTimeout(30);

    static final class Holder {
        private String value;
        private Object objectValue;
        private Number numberValue;
        private volatile Object volatileValue;
        private int primitive;
        private static Object staticValue;
        private final Object finalValue = new Object();
        private SmallValue nullableValue;
        @NullRestricted
        private SmallValue nullRestrictedValue;

        Holder() {
            nullRestrictedValue = new SmallValue(0);
            super();
        }
    }

    static value class SmallValue {
        final int value;

        SmallValue(int value) {
            this.value = value;
        }
    }

    static final class Payload {
        int number;
        int[] array;
        String text;
    }

    static class ProtectedChild extends FieldOwner {
        final Object computedValue = new Object();
        int computationCount;

        static Lookup lookup() {
            return MethodHandles.lookup();
        }

        Object computeValue() {
            computationCount++;
            return computedValue;
        }

        Object protectedValue() {
            return protectedValue;
        }
    }

    @Test
    void fieldIsLazyAndEachReceiverHasItsOwnValue() {
        AtomicInteger calls = new AtomicInteger();
        var lazy = Lazy.ofField(MethodHandles.lookup(), Holder.class, "value", String.class,
                holder -> "value" + calls.incrementAndGet());
        Holder first = new Holder();
        Holder second = new Holder();
        assertEquals(0, calls.get());
        String firstValue = lazy.apply(first);
        assertSame(firstValue, first.value);
        assertSame(firstValue, lazy.apply(first));
        assertEquals("value2", lazy.apply(second));
        assertEquals(2, calls.get());
    }

    @Test
    void preexistingValueIsUsed() {
        var lazy = Lazy.ofField(MethodHandles.lookup(), Holder.class, "value", String.class, holder -> {
            throw new AssertionError("Computation must not run");
        });
        var objectLazy = Lazy.ofField(MethodHandles.lookup(), Holder.class, "objectValue", Object.class, holder -> {
            throw new AssertionError("Computation must not run");
        });
        Holder holder = new Holder();
        holder.value = new String("preexisting");
        holder.objectValue = Integer.valueOf(42);
        assertSame(holder.value, lazy.apply(holder));
        assertSame(holder.objectValue, objectLazy.apply(holder));
    }

    @Test
    void functionsShareStorageAcrossLookupsAndComputations() throws IllegalAccessException {
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();
        var first = Lazy.ofField(MethodHandles.lookup(), Holder.class, "value", String.class, holder -> {
            firstCalls.incrementAndGet();
            return "first";
        });
        Lookup lookup = MethodHandles.privateLookupIn(Holder.class, MethodHandles.lookup());
        var second = LazyFieldFactory.create(lookup, Holder.class, "value", String.class, holder -> {
            secondCalls.incrementAndGet();
            return "second";
        });

        Holder firstHolder = new Holder();
        Holder secondHolder = new Holder();
        assertEquals("first", first.apply(firstHolder));
        assertSame(firstHolder.value, second.apply(firstHolder));
        assertEquals("second", second.apply(secondHolder));
        assertSame(secondHolder.value, first.apply(secondHolder));
        assertEquals(1, firstCalls.get());
        assertEquals(1, secondCalls.get());
    }

    @Test
    void factoryChecksArgumentsAndFields() {
        Function<Holder, Object> compute = holder -> new Object();
        assertThrows(NullPointerException.class,
                () -> Lazy.ofField(null, Holder.class, "objectValue", Object.class, compute));
        assertThrows(NullPointerException.class,
                () -> Lazy.ofField(MethodHandles.lookup(), null, "objectValue", Object.class, compute));
        assertThrows(NullPointerException.class,
                () -> Lazy.ofField(MethodHandles.lookup(), Holder.class, null, Object.class, compute));
        assertThrows(NullPointerException.class,
                () -> Lazy.ofField(MethodHandles.lookup(), Holder.class, "objectValue", null, compute));
        assertThrows(NullPointerException.class,
                () -> Lazy.ofField(MethodHandles.lookup(), Holder.class, "objectValue", Object.class, null));
        for (String field : new String[] { "staticValue", "finalValue" }) {
            assertThrows(IllegalArgumentException.class,
                    () -> Lazy.ofField(MethodHandles.lookup(), Holder.class, field, Object.class, compute));
        }
        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofField(MethodHandles.lookup(), Holder.class, "primitive", int.class, holder -> 1));
        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofField(MethodHandles.lookup(), Holder.class, "objectValue", void.class, holder -> null));
        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofField(MethodHandles.lookup(), Holder.class, "nullRestrictedValue", SmallValue.class,
                        holder -> new SmallValue(0)));
        var missing = assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofField(MethodHandles.lookup(), Holder.class, "missing", Object.class, compute));
        assertInstanceOf(NoSuchFieldException.class, missing.getCause());
    }

    @Test
    void factoryRequiresTheExactDeclaredType() {
        AtomicInteger calls = new AtomicInteger();
        Holder holder = new Holder();
        holder.objectValue = Integer.valueOf(42);
        Function<Holder, String> compute = receiver -> {
            calls.incrementAndGet();
            return "computed";
        };
        // Neither a supertype nor a subtype token describes the declared field type.
        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofField(MethodHandles.lookup(), Holder.class, "value", Object.class, compute));
        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofField(MethodHandles.lookup(), Holder.class, "objectValue", String.class, compute));
        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofField(MethodHandles.lookup(), Holder.class, "numberValue", String.class, compute));
        assertEquals(0, calls.get());
        assertEquals(42, holder.objectValue);
    }

    @Test
    void exactDeclaredTypePermitsSubtypeResults() {
        Function<Holder, Object> objectLazy =
                Lazy.ofField(MethodHandles.lookup(), Holder.class, "objectValue", Object.class, holder -> "value");
        Function<Holder, Number> numberLazy =
                Lazy.ofField(MethodHandles.lookup(), Holder.class, "numberValue", Number.class,
                        holder -> Integer.valueOf(42));
        Holder holder = new Holder();
        assertEquals("value", objectLazy.apply(holder));
        assertSame(holder.objectValue, objectLazy.apply(holder));
        assertEquals(42, numberLazy.apply(holder));
        assertSame(holder.numberValue, numberLazy.apply(holder));
    }

    @Test
    void accessIsCheckedAgainstTheSuppliedLookup() {
        var inaccessible = assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofField(MethodHandles.lookup(), FieldOwner.class, "privateValue", Object.class,
                        owner -> new Object()));
        assertInstanceOf(IllegalAccessException.class, inaccessible.getCause());
        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofField(MethodHandles.lookup(), FieldOwner.class, "protectedValue", Object.class,
                        owner -> new Object()));
        FieldOwner owner = new FieldOwner();
        var privateLazy = FieldOwner.privateFunction();
        assertSame(privateLazy.apply(owner), privateLazy.apply(owner));
        var publicLazy = Lazy.ofField(MethodHandles.lookup(), FieldOwner.class, "publicValue", Object.class,
                context -> new Object());
        assertSame(publicLazy.apply(owner), owner.publicValue);
    }

    @Test
    void protectedAccessNarrowsTheReceiverType() {
        var lazy = LazyFieldFactory.create(ProtectedChild.lookup(), FieldOwner.class,
                "protectedValue", Object.class, owner -> new Object());
        ProtectedChild child = new ProtectedChild();
        assertSame(lazy.apply(child), lazy.apply(child));
        assertThrows(ClassCastException.class, () -> lazy.apply(new FieldOwner()));
    }

    @Test
    void sharedProtectedStorageKeepsEachLookupsReceiverRestriction() throws IllegalAccessException {
        Lookup ownerLookup = MethodHandles.privateLookupIn(FieldOwner.class, MethodHandles.lookup());
        var unrestricted = Lazy.ofField(ownerLookup, FieldOwner.class, "protectedValue", Object.class,
                owner -> new Object());
        var restricted = LazyFieldFactory.create(ProtectedChild.lookup(), FieldOwner.class,
                "protectedValue", Object.class, owner -> {
                    throw new AssertionError("Shared field already initialized");
                });
        ProtectedChild child = new ProtectedChild();
        Object value = unrestricted.apply(child);
        assertSame(value, restricted.apply(child));
        FieldOwner owner = new FieldOwner();
        unrestricted.apply(owner);
        assertThrows(ClassCastException.class, () -> restricted.apply(owner));
        assertThrows(NullPointerException.class, () -> restricted.apply(null));
    }

    @Test
    void declaredFieldSupportsSubclassSpecificComputation() {
        Function<ProtectedChild, Object> lazy = Lazy.ofField(MethodHandles.lookup(), FieldOwner.class,
                "publicValue", Object.class, ProtectedChild::computeValue);
        ProtectedChild child = new ProtectedChild();
        assertEquals(0, child.computationCount);
        assertSame(child.computedValue, lazy.apply(child));
        assertSame(child.computedValue, child.publicValue);
        assertSame(child.computedValue, lazy.apply(child));
        assertEquals(1, child.computationCount);
    }

    @Test
    void protectedFieldInfersSubclassFromComputation() {
        var lazy = LazyFieldFactory.create(ProtectedChild.lookup(), FieldOwner.class,
                "protectedValue", Object.class, ProtectedChild::computeValue);
        Function<ProtectedChild, Object> typed = lazy;
        ProtectedChild child = new ProtectedChild();
        assertEquals(0, child.computationCount);
        assertSame(child.computedValue, typed.apply(child));
        assertSame(child.computedValue, child.protectedValue());
        assertSame(child.computedValue, lazy.apply(child));
        assertEquals(1, child.computationCount);
    }

    @Test
    void lookupAuthorityCanBeDelegatedToAnUnrelatedHelper() {
        var lazy = LazyFieldFactory.create(MethodHandles.lookup(), Holder.class,
                "value", String.class, holder -> "delegated");
        Holder holder = new Holder();
        assertEquals("delegated", lazy.apply(holder));
        assertSame(holder.value, lazy.apply(holder));
    }

    @Test
    void publicAndReducedLookupsCannotGainPrivateAccess() {
        var publicLazy = Lazy.ofField(MethodHandles.publicLookup(), FieldOwner.class,
                "publicValue", Object.class, owner -> new Object());
        FieldOwner owner = new FieldOwner();
        assertSame(publicLazy.apply(owner), owner.publicValue);

        for (Lookup lookup : new Lookup[] { MethodHandles.publicLookup(),
                MethodHandles.lookup().dropLookupMode(Lookup.PRIVATE) }) {
            var inaccessible = assertThrows(IllegalArgumentException.class,
                    () -> Lazy.ofField(lookup, Holder.class, "value", String.class, holder -> "private"));
            assertInstanceOf(IllegalAccessException.class, inaccessible.getCause());
        }
    }

    @Test
    void privateLookupInGrantsAccessToAnotherClass() throws IllegalAccessException {
        Lookup lookup = MethodHandles.privateLookupIn(FieldOwner.class, MethodHandles.lookup());
        Object value = new Object();
        var lazy = LazyFieldFactory.create(lookup, FieldOwner.class,
                "privateValue", Object.class, owner -> value);
        assertSame(value, lazy.apply(new FieldOwner()));
    }

    @Test
    void inheritedFieldsMustNameTheirDeclaringClass() {
        var failure = assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofField(MethodHandles.lookup(), ProtectedChild.class,
                        "publicValue", Object.class, child -> new Object()));
        assertInstanceOf(NoSuchFieldException.class, failure.getCause());
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void accessChecksReceiverAndComputedValueTypes() {
        Function compute = (Function<Holder, Object>) holder -> new Object();
        Function<Holder, String> lazy =
                Lazy.ofField(MethodHandles.lookup(), Holder.class, "value", String.class, compute);
        assertThrows(NullPointerException.class, () -> lazy.apply(null));
        Function rawLazy = lazy;
        assertThrows(ClassCastException.class, () -> rawLazy.apply(new Object()));
        Holder holder = new Holder();
        assertThrows(ClassCastException.class, () -> lazy.apply(holder));
        assertNull(holder.value);

        Holder initialized = new Holder();
        initialized.value = "preinitialized";
        assertSame(initialized.value, lazy.apply(initialized));
        assertThrows(NullPointerException.class, () -> lazy.apply(null));
        assertThrows(ClassCastException.class, () -> rawLazy.apply(new Object()));
    }

    @Test
    void failuresAreRetried() {
        AtomicInteger calls = new AtomicInteger();
        RuntimeException failure = new RuntimeException();
        Error error = new AssertionError();
        var lazy = Lazy.ofField(MethodHandles.lookup(), Holder.class, "value", String.class, holder -> {
            return switch (calls.getAndIncrement()) {
                case 0 -> throw failure;
                case 1 -> throw error;
                case 2 -> null;
                default -> "success";
            };
        });
        Holder holder = new Holder();
        assertSame(failure, assertThrows(RuntimeException.class, () -> lazy.apply(holder)));
        assertNull(holder.value);
        assertSame(error, assertThrows(Error.class, () -> lazy.apply(holder)));
        assertNull(holder.value);
        assertThrows(NullPointerException.class, () -> lazy.apply(holder));
        assertNull(holder.value);
        assertEquals("success", lazy.apply(holder));
        assertSame(holder.value, lazy.apply(holder));
        assertEquals(4, calls.get());
    }

    @Test
    void failuresArePropagatedDespiteAConcurrentWinner() throws Exception {
        RuntimeException exception = new RuntimeException();
        Error error = new AssertionError();
        failDuringPublication(holder -> { throw exception; }, RuntimeException.class, exception);
        failDuringPublication(holder -> { throw error; }, Error.class, error);
        failDuringPublication(holder -> null, NullPointerException.class, null);
        failDuringPublication(holder -> new Object(), ClassCastException.class, null);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void failDuringPublication(Function<Holder, Object> failure,
                                       Class<? extends Throwable> failureType,
                                       Throwable expectedFailure) throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch installed = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        Function compute = (Function<Holder, Object>) holder -> {
            calls.incrementAndGet();
            entered.countDown();
            await(installed);
            return failure.apply(holder);
        };
        Function<Holder, String> failing = Lazy.ofField(MethodHandles.lookup(), Holder.class,
                "value", String.class, compute);
        var successful = Lazy.ofField(MethodHandles.lookup(), Holder.class,
                "value", String.class, holder -> "winner");
        Holder holder = new Holder();
        try (var pool = Executors.newSingleThreadExecutor()) {
            Future<String> result = pool.submit(() -> failing.apply(holder));
            try {
                assertTrue(entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
                assertEquals("winner", successful.apply(holder));
            } finally {
                installed.countDown();
            }
            var thrown = assertThrows(ExecutionException.class,
                    () -> result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertInstanceOf(failureType, thrown.getCause());
            if (expectedFailure != null) {
                assertSame(expectedFailure, thrown.getCause());
            }
            assertEquals("winner", failing.apply(holder));
            assertSame(holder.value, successful.apply(holder));
            assertEquals(1, calls.get());
        }
    }

    @Test
    void racingComputationsReturnThePublishedWinner() throws Exception {
        for (String fieldName : new String[] { "objectValue", "volatileValue" }) {
            raceAndCheck(fieldName, false);
            raceAndCheck(fieldName, true);
        }
    }

    private void raceAndCheck(String fieldName, boolean separateFunctions) throws Exception {
        int threads = 4;
        try (var pool = Executors.newFixedThreadPool(threads)) {
            for (int round = 0; round < 100; round++) {
                CountDownLatch entered = new CountDownLatch(threads);
                Function<Holder, Object> compute = holder -> {
                    entered.countDown();
                    await(entered);
                    // Initialize non-final state after the barrier. Only the
                    // lazy field publishes these writes to competing accesses.
                    Payload candidate = new Payload();
                    candidate.number = 42;
                    candidate.array = new int[] { 84 };
                    candidate.text = "published";
                    return candidate;
                };
                var lazy = Lazy.ofField(MethodHandles.lookup(), Holder.class, fieldName, Object.class, compute);
                Holder holder = new Holder();
                ArrayList<Future<Object>> results = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    var accessor = separateFunctions
                            ? Lazy.ofField(MethodHandles.lookup(), Holder.class, fieldName, Object.class, compute)
                            : lazy;
                    results.add(pool.submit(() -> {
                        Payload value = (Payload) accessor.apply(holder);
                        assertEquals(42, value.number);
                        assertArrayEquals(new int[] { 84 }, value.array);
                        assertEquals("published", value.text);
                        return value;
                    }));
                }
                Object winner = results.getFirst().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                for (var result : results) {
                    assertSame(winner, result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
                }
                assertSame(winner, lazy.apply(holder));
                assertSame(winner, fieldName.equals("objectValue") ? holder.objectValue : holder.volatileValue);
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } catch (InterruptedException ex) {
            throw new AssertionError(ex);
        }
    }

    @Test
    void nullableValueFieldIsSupported() {
        var lazy = Lazy.ofField(MethodHandles.lookup(), Holder.class, "nullableValue", SmallValue.class,
                holder -> new SmallValue(42));
        Holder holder = new Holder();
        assertEquals(42, lazy.apply(holder).value);
        assertEquals(lazy.apply(holder), holder.nullableValue);
    }

    @Test
    @SuppressWarnings("unchecked")
    void reflectionUsesTheSuppliedLookup() throws ReflectiveOperationException {
        var factory = Lazy.class.getMethod("ofField", Lookup.class, Class.class,
                String.class, Class.class, Function.class);
        Function<Holder, String> compute = holder -> "reflection";
        var lazy = (Function<Holder, String>) factory.invoke(null, MethodHandles.lookup(),
                Holder.class, "value", String.class, compute);
        assertEquals("reflection", lazy.apply(new Holder()));
        var failure = assertThrows(InvocationTargetException.class,
                () -> factory.invoke(null, MethodHandles.publicLookup(),
                        Holder.class, "value", String.class, compute));
        var accessFailure = assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertInstanceOf(IllegalAccessException.class, accessFailure.getCause());
    }

    @Test
    @SuppressWarnings("unchecked")
    void methodHandleUsesTheSuppliedLookup() throws Throwable {
        var type = MethodType.methodType(Function.class, Lookup.class, Class.class,
                String.class, Class.class, Function.class);
        var factory = MethodHandles.publicLookup().findStatic(Lazy.class, "ofField", type);
        Function<Holder, String> compute = holder -> "method handle";
        var lazy = (Function<Holder, String>) factory.invokeExact(MethodHandles.lookup(),
                Holder.class, "value", String.class, compute);
        assertEquals("method handle", lazy.apply(new Holder()));
        var failure = assertThrows(IllegalArgumentException.class, () -> {
            var ignored = (Function<Holder, String>) factory.invokeExact(MethodHandles.publicLookup(),
                    Holder.class, "value", String.class, compute);
        });
        assertInstanceOf(IllegalAccessException.class, failure.getCause());
    }
}
