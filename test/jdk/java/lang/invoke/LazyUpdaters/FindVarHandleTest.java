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
 * @summary Recover an instance or static field VarHandle from an ordinary getter lambda
 * @modules java.base/java.lang.invoke:open
 * @run junit FindVarHandleTest
 * @run junit/othervm -Xint FindVarHandleTest
 */

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.invoke.LazyUpdaters;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FindVarHandleTest {
    private static final Lookup LOOKUP = MethodHandles.lookup();
    private static final MethodHandle FIND_VAR_HANDLE;

    static {
        try {
            var method = LazyUpdaters.class.getDeclaredMethod("findVarHandle", Lookup.class, Object.class);
            method.setAccessible(true);
            FIND_VAR_HANDLE = LOOKUP.unreflect(method);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static int selectorInvocations;
    private static boolean staticHolderInitialized;

    private static VarHandle findVarHandle(Lookup lookup, Object selector) throws Throwable {
        return (VarHandle) FIND_VAR_HANDLE.invokeExact(lookup, selector);
    }

    interface BooleanField { boolean get(Holder p); }
    interface ByteField { byte get(Holder p); }
    interface ShortField { short get(Holder p); }
    interface CharField { char get(Holder p); }
    interface FloatField { float get(Holder p); }
    interface BooleanSupplier { boolean get(); }
    interface ByteSupplier { byte get(); }
    interface ShortSupplier { short get(); }
    interface CharSupplier { char get(); }
    interface FloatSupplier { float get(); }
    interface StringField extends Function<Holder, String> {
        @Override
        String apply(Holder p);
    }

    static class Parent {
        int inherited;
    }

    static class Holder extends Parent {
        private boolean flag;
        private byte octet;
        private short small;
        private char character;
        private int count;
        private long large;
        private float fraction;
        private double radius;
        private String text;
        private Double boxed;
        private int[] array;
        private Holder next;
        private volatile double published;
        private static double shared;

        double radius() {
            return radius;
        }
    }

    static class StaticHolder {
        private static boolean flag;
        private static byte octet;
        private static short small;
        private static char character;
        private static int count;
        private static long large;
        private static float fraction;
        private static double radius;
        private static String text;
        private static Double boxed;
        private static int[] array;
        private static volatile double published;
        private static final Object value = new Object();
    }

    static class UninitializedHolder {
        private static int value = initialize();

        private static int initialize() {
            staticHolderInitialized = true;
            return 42;
        }
    }

    @Test
    public void testPrimitiveFields() throws Throwable {
        checkField((BooleanField) p -> p.flag, boolean.class, false, true);
        checkField((ByteField) p -> p.octet, byte.class, (byte) 0, (byte) 12);
        checkField((ShortField) p -> p.small, short.class, (short) 0, (short) 123);
        checkField((CharField) p -> p.character, char.class, '\0', 'x');
        checkField((ToIntFunction<Holder>) p -> p.count, int.class, 0, 1234);
        checkField((ToLongFunction<Holder>) p -> p.large, long.class, 0L, 12345L);
        checkField((FloatField) p -> p.fraction, float.class, 0.0f, 1.25f);
        checkField((ToDoubleFunction<Holder>) p -> p.radius, double.class, 0.0, 2.5);
        checkField((ToDoubleFunction<Holder>) p -> p.published, double.class, 0.0, 4.5);
        checkField(instanceSelector(), double.class, 0.0, 6.5);
    }

    private ToDoubleFunction<Holder> instanceSelector() {
        // The enclosing method is an instance method, but this lambda captures nothing.
        return p -> p.radius;
    }

    @Test
    public void testBoxedResults() throws Throwable {
        checkField((Function<Holder, Boolean>) p -> p.flag, boolean.class, false, true);
        checkField((Function<Holder, Byte>) p -> p.octet, byte.class, (byte) 0, (byte) 12);
        checkField((Function<Holder, Short>) p -> p.small, short.class, (short) 0, (short) 123);
        checkField((Function<Holder, Character>) p -> p.character, char.class, '\0', 'x');
        checkField((Function<Holder, Integer>) p -> p.count, int.class, 0, 1234);
        checkField((Function<Holder, Long>) p -> p.large, long.class, 0L, 12345L);
        checkField((Function<Holder, Float>) p -> p.fraction, float.class, 0.0f, 1.25f);
        checkField((Function<Holder, Double>) p -> p.radius, double.class, 0.0, 2.5);
    }

    @Test
    public void testReferenceFields() throws Throwable {
        checkField((Function<Holder, String>) p -> p.text, String.class, null, "radius");
        checkField((Function<Holder, Object>) p -> p.text, String.class, null, "widened");
        checkField((StringField) p -> p.text, String.class, null, "covariant");
        checkField((Function<Holder, Double>) p -> p.boxed, Double.class, null, 3.0);
        checkField((Function<Holder, int[]>) p -> p.array, int[].class, null, new int[] { 1, 2 });
        checkField((Function<Holder, Holder>) p -> p.next, Holder.class, null, new Holder());
    }

    @Test
    public void testStaticPrimitiveFields() throws Throwable {
        checkStaticField((BooleanSupplier) () -> StaticHolder.flag, boolean.class, false, true);
        checkStaticField((ByteSupplier) () -> StaticHolder.octet, byte.class, (byte) 0, (byte) 12);
        checkStaticField((ShortSupplier) () -> StaticHolder.small, short.class, (short) 0, (short) 123);
        checkStaticField((CharSupplier) () -> StaticHolder.character, char.class, '\0', 'x');
        checkStaticField((IntSupplier) () -> StaticHolder.count, int.class, 0, 1234);
        checkStaticField((LongSupplier) () -> StaticHolder.large, long.class, 0L, 12345L);
        checkStaticField((FloatSupplier) () -> StaticHolder.fraction, float.class, 0.0f, 1.25f);
        checkStaticField((DoubleSupplier) () -> StaticHolder.radius, double.class, 0.0, 2.5);
        checkStaticField((DoubleSupplier) () -> StaticHolder.published, double.class, 0.0, 4.5);
    }

    @Test
    public void testStaticBoxedResults() throws Throwable {
        checkStaticField((Supplier<Boolean>) () -> StaticHolder.flag, boolean.class, false, true);
        checkStaticField((Supplier<Byte>) () -> StaticHolder.octet, byte.class, (byte) 0, (byte) 12);
        checkStaticField((Supplier<Short>) () -> StaticHolder.small, short.class, (short) 0, (short) 123);
        checkStaticField((Supplier<Character>) () -> StaticHolder.character, char.class, '\0', 'x');
        checkStaticField((Supplier<Integer>) () -> StaticHolder.count, int.class, 0, 1234);
        checkStaticField((Supplier<Long>) () -> StaticHolder.large, long.class, 0L, 12345L);
        checkStaticField((Supplier<Float>) () -> StaticHolder.fraction, float.class, 0.0f, 1.25f);
        checkStaticField((Supplier<Double>) () -> StaticHolder.radius, double.class, 0.0, 2.5);
    }

    @Test
    public void testStaticReferenceFields() throws Throwable {
        checkStaticField((Supplier<String>) () -> StaticHolder.text, String.class, null, "radius");
        checkStaticField((Supplier<Object>) () -> StaticHolder.text, String.class, null, "widened");
        checkStaticField((Supplier<Double>) () -> StaticHolder.boxed, Double.class, null, 3.0);
        checkStaticField((Supplier<int[]>) () -> StaticHolder.array, int[].class, null, new int[] { 1, 2 });
    }

    private static void checkField(Object selector, Class<?> fieldType,
                                   Object initial, Object replacement) throws Throwable {
        assertFalse(selector instanceof java.io.Serializable,
                "Test unexpectedly uses a serializable lambda");
        VarHandle handle = findVarHandle(LOOKUP, selector);
        assertSame(fieldType, handle.varType());
        assertEquals(List.of(Holder.class), handle.coordinateTypes());
        Holder point = new Holder();
        assertEquals(initial, (Object) handle.get(point));
        handle.set(point, replacement);
        assertEquals(replacement, (Object) handle.get(point));
        assertTrue(handle.compareAndSet(point, replacement, initial),
                "compareAndSet failed for " + fieldType);
        assertEquals(initial, (Object) handle.get(point));
    }

    private static void checkStaticField(Object selector, Class<?> fieldType,
                                         Object initial, Object replacement) throws Throwable {
        assertFalse(selector instanceof java.io.Serializable,
                "Test unexpectedly uses a serializable lambda");
        VarHandle handle = findVarHandle(LOOKUP, selector);
        assertSame(fieldType, handle.varType());
        assertEquals(List.of(), handle.coordinateTypes());
        assertEquals(initial, (Object) handle.get());
        handle.set(replacement);
        assertEquals(replacement, (Object) handle.get());
        assertTrue(handle.compareAndSet(replacement, initial),
                "compareAndSet failed for " + fieldType);
        assertEquals(initial, (Object) handle.get());
    }

    @Test
    public void testInheritedAndFinalFields() throws Throwable {
        ToIntFunction<Holder> inherited = p -> p.inherited;
        VarHandle inheritedHandle = findVarHandle(LOOKUP, inherited);
        assertSame(int.class, inheritedHandle.varType());
        Holder point = new Holder();
        inheritedHandle.set(point, 12);
        assertEquals(12, point.inherited);

        // A non-constant final field keeps a getfield in the selector's bytecode.
        Function<FinalHolder, Object> selector = p -> p.value;
        VarHandle finalHandle = findVarHandle(LOOKUP, selector);
        FinalHolder holder = new FinalHolder();
        assertSame(holder.value, finalHandle.get(holder));
        assertFalse(finalHandle.isAccessModeSupported(VarHandle.AccessMode.SET),
                "A final field must produce a read-only handle");
        assertFalse(finalHandle.isAccessModeSupported(VarHandle.AccessMode.COMPARE_AND_SET),
                "A final field must produce a read-only handle");

        VarHandle staticFinalHandle = findVarHandle(LOOKUP,
                (Supplier<Object>) () -> StaticHolder.value);
        assertSame(StaticHolder.value, staticFinalHandle.get());
        assertFalse(staticFinalHandle.isAccessModeSupported(VarHandle.AccessMode.SET),
                "A static final field must produce a read-only handle");
        assertFalse(staticFinalHandle.isAccessModeSupported(VarHandle.AccessMode.COMPARE_AND_SET),
                "A static final field must produce a read-only handle");
    }

    static class FinalHolder {
        private final Object value = new Object();
    }

    @Test
    public void testAccessChecks() {
        ToDoubleFunction<Holder> selector = p -> p.radius;
        assertThrows(IllegalAccessException.class,
                () -> findVarHandle(MethodHandles.publicLookup(), selector));
        assertThrows(IllegalAccessException.class,
                () -> findVarHandle(LOOKUP.dropLookupMode(Lookup.PRIVATE), selector));
        assertThrows(NullPointerException.class,
                () -> findVarHandle(null, selector));
        assertThrows(NullPointerException.class,
                () -> findVarHandle(LOOKUP, null));

        DoubleSupplier staticSelector = () -> StaticHolder.radius;
        assertThrows(IllegalAccessException.class,
                () -> findVarHandle(MethodHandles.publicLookup(), staticSelector));
        assertThrows(IllegalAccessException.class,
                () -> findVarHandle(LOOKUP.dropLookupMode(Lookup.PRIVATE), staticSelector));
    }

    @Test
    public void testInvalidSelectors() {
        selectorInvocations = 0;
        reject((ToDoubleFunction<Holder>) p -> p.radius + 1);
        reject((ToDoubleFunction<Holder>) p -> Holder.shared);
        reject((ToDoubleFunction<Holder>) p -> p.next.radius);
        reject((ToDoubleFunction<Holder>) p -> p.radius());
        reject((ToDoubleFunction<Holder>) Holder::radius);
        reject((ToDoubleFunction<Holder>) FindVarHandleTest::radius);
        reject((ToDoubleFunction<Holder>) p -> p.count); // A widening primitive conversion.
        reject((ToDoubleFunction<Holder>) p -> p.boxed); // Unboxing a reference field.
        reject((Function<Holder, Double>) p -> (double) p.count);
        reject((ToDoubleFunction<Holder>) p -> 42.0);
        reject((ToDoubleFunction<Holder>) p -> {
            try {
                return p.radius;
            } catch (NullPointerException e) {
                return 0;
            }
        });
        Holder captured = new Holder();
        reject((ToDoubleFunction<Holder>) p -> captured.radius);
        reject((DoubleSupplier) () -> captured.radius);
        reject((DoubleSupplier) () -> StaticHolder.radius + 1);
        reject((DoubleSupplier) () -> StaticHolder.count);
        reject((DoubleSupplier) () -> StaticHolder.boxed);
        reject((Supplier<Double>) () -> (double) StaticHolder.count);
        reject((DoubleSupplier) FindVarHandleTest::sharedRadius);
        reject((DoubleSupplier) () -> {
            selectorInvocations++;
            return StaticHolder.radius;
        });
        reject((ToDoubleFunction<Holder>) p -> {
            selectorInvocations++;
            return p.radius;
        });
        reject(new ToDoubleFunction<Holder>() {
            @Override
            public double applyAsDouble(Holder p) {
                selectorInvocations++;
                return p.radius;
            }
        });
        reject(new Object());
        assertEquals(0, selectorInvocations);
    }

    private static double radius(Holder point) {
        return point.radius;
    }

    private static double sharedRadius() {
        return StaticHolder.radius;
    }

    private static void reject(Object selector) {
        assertThrows(IllegalArgumentException.class,
                () -> findVarHandle(LOOKUP, selector));
    }

    @Test
    public void testWarmLambda() throws Throwable {
        ToDoubleFunction<Holder> primitive = p -> p.radius;
        Function<Holder, Double> boxed = p -> p.radius;
        Function<Holder, String> reference = p -> p.text;
        Holder point = new Holder();
        point.radius = 12.5;
        point.text = "warm";
        for (int i = 0; i < 20_000; i++) {
            assertEquals(12.5, primitive.applyAsDouble(point));
            assertEquals(12.5, boxed.apply(point).doubleValue());
            assertEquals("warm", reference.apply(point));
        }
        // Interpreter quickening must not affect bytecode recognition.
        assertEquals(12.5, (double) findVarHandle(LOOKUP, primitive).get(point));
        assertEquals(12.5, (double) findVarHandle(LOOKUP, boxed).get(point));
        assertEquals("warm", (String) findVarHandle(LOOKUP, reference).get(point));
    }

    @Test
    public void testWarmStaticLambda() throws Throwable {
        DoubleSupplier primitive = () -> StaticHolder.radius;
        Supplier<Double> boxed = () -> StaticHolder.radius;
        Supplier<String> reference = () -> StaticHolder.text;
        StaticHolder.radius = 12.5;
        StaticHolder.text = "warm";
        try {
            for (int i = 0; i < 20_000; i++) {
                assertEquals(12.5, primitive.getAsDouble());
                assertEquals(12.5, boxed.get().doubleValue());
                assertEquals("warm", reference.get());
            }
            assertEquals(12.5, (double) findVarHandle(LOOKUP, primitive).get());
            assertEquals(12.5, (double) findVarHandle(LOOKUP, boxed).get());
            assertEquals("warm", (String) findVarHandle(LOOKUP, reference).get());
        } finally {
            StaticHolder.radius = 0.0;
            StaticHolder.text = null;
        }
    }

    @Test
    public void testStaticClassInitialization() throws Throwable {
        IntSupplier selector = () -> UninitializedHolder.value;
        assertFalse(staticHolderInitialized);
        VarHandle handle = findVarHandle(LOOKUP, selector);
        assertSame(int.class, handle.varType());
        assertEquals(List.of(), handle.coordinateTypes());
        assertFalse(staticHolderInitialized, "Cracking a selector must not initialize the field's class");
        assertEquals(42, (int) handle.get());
        assertTrue(staticHolderInitialized);
    }

    public static class ResourceHost {
        private double radius;

        public static Object selector() {
            return (ToDoubleFunction<ResourceHost>) p -> p.radius;
        }

        public static Lookup lookup() {
            return MethodHandles.lookup();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testClassResources(boolean staleResources) throws Throwable {
        String name = ResourceHost.class.getName();
        byte[] classBytes;
        byte[] staleBytes;
        try (InputStream in = FindVarHandleTest.class.getResourceAsStream(
                "/" + name.replace('.', '/') + ".class")) {
            classBytes = Objects.requireNonNull(in).readAllBytes();
        }
        try (InputStream in = FindVarHandleTest.class.getResourceAsStream(
                "/" + FindVarHandleTest.class.getName().replace('.', '/') + ".class")) {
            staleBytes = Objects.requireNonNull(in).readAllBytes();
        }
        class ResourceLoader extends ClassLoader {
            int resourceRequests;

            ResourceLoader() {
                super(FindVarHandleTest.class.getClassLoader());
            }

            Class<?> defineHost() {
                return defineClass(name, classBytes, 0, classBytes.length);
            }

            @Override
            public InputStream getResourceAsStream(String resource) {
                resourceRequests++;
                return staleResources ? new ByteArrayInputStream(staleBytes) : null;
            }

            @Override
            public URL getResource(String resource) {
                resourceRequests++;
                return null;
            }

            @Override
            public Enumeration<URL> getResources(String resource) {
                resourceRequests++;
                return Collections.emptyEnumeration();
            }
        }
        ResourceLoader loader = new ResourceLoader();
        Class<?> host = loader.defineHost();
        Object selector = host.getMethod("selector").invoke(null);
        Lookup lookup = (Lookup) host.getMethod("lookup").invoke(null);
        Object receiver = host.getConstructor().newInstance();
        VarHandle handle = findVarHandle(lookup, selector);
        assertEquals(List.of(host), handle.coordinateTypes());
        assertSame(double.class, handle.varType());
        handle.set(receiver, 5.5);
        assertEquals(5.5, (double) handle.get(receiver));
        assertEquals(0, loader.resourceRequests);
    }
}
