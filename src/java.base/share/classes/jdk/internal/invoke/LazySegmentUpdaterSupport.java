/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.internal.invoke;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.util.Lazy.UpdaterMode;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import jdk.internal.access.SharedSecrets;
import sun.invoke.util.Wrapper;

/**
 * Creates lazy updaters for locations selected by a structured memory layout.
 * The layout's var handle performs all segment lifetime, thread, bounds,
 * alignment, and read-only checks on each access.
 */
public final class LazySegmentUpdaterSupport {
    private LazySegmentUpdaterSupport() { }

    /**
     * Creates an updater that retains its computing function. The functional
     * method has exactly the layout var handle's coordinates and carrier.
     */
    public static <C> C ofSegment(UpdaterMode mode,
                                   Class<C> computationShape,
                                   MemoryLayout layout,
                                   PathElement[] path,
                                   C computingFunction) {
        Objects.requireNonNull(computingFunction);
        SegmentAccess access = segmentAccess(mode, computationShape, layout, path);
        if (!computationShape.isInstance(computingFunction)) {
            throw new IllegalArgumentException("Function must implement "
                    + computationShape.getTypeName());
        }
        try {
            SegmentUpdater updater = new SegmentUpdater(access, computingFunction);
            return create(computationShape, access.computerMethod(), updater, false);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(
                    "Cannot access the computing functional method", e);
        }
    }

    /**
     * Creates an updater that accepts its computing function on each access.
     * The use-site method adds a structurally compatible computing-function
     * interface before the layout var handle's coordinates and has the same
     * carrier result.
     */
    public static <C, U> U ofSegmentUseSite(UpdaterMode mode,
                                            Class<C> computationShape,
                                            Class<U> useSiteShape,
                                            MemoryLayout layout,
                                            PathElement[] path) {
        Objects.requireNonNull(useSiteShape);
        SegmentAccess access = segmentAccess(mode, computationShape, layout, path);
        Method method = functionalMethod(useSiteShape);
        Class<?>[] parameters = method.getParameterTypes();
        Class<?>[] coordinates = access.coordinateTypes();
        if (parameters.length != coordinates.length + 1
                || method.getReturnType() != access.valueType()
                || !Arrays.equals(Arrays.copyOfRange(parameters, 1,
                        parameters.length), coordinates)) {
            throw new IllegalArgumentException("Use-site functional method must "
                    + "accept a computing function followed by the segment "
                    + "coordinates, and return "
                    + access.valueType().getTypeName());
        }
        Method callbackMethod = functionalMethod(parameters[0]);
        if (callbackMethod.getReturnType() != access.valueType()
                || !Arrays.equals(callbackMethod.getParameterTypes(), coordinates)) {
            throw new IllegalArgumentException("Use-site computing function must "
                    + "have the same coordinates and result as "
                    + computationShape.getTypeName());
        }
        SegmentAccess callbackAccess = new SegmentAccess(access.mode(),
                access.handle(), callbackMethod, coordinates, access.valueType());
        try {
            SegmentUpdater updater = new SegmentUpdater(callbackAccess, null);
            return create(useSiteShape, method, updater, true);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(
                    "Cannot access the computing functional method", e);
        }
    }

    private static SegmentAccess segmentAccess(UpdaterMode mode,
                                                Class<?> computationShape,
                                                MemoryLayout layout,
                                                PathElement[] path) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(computationShape);
        Objects.requireNonNull(layout);
        Objects.requireNonNull(path);
        if (mode == UpdaterMode.SERIALIZED) {
            throw new IllegalArgumentException("SERIALIZED is not supported for "
                    + "memory-segment updaters: different segment views may alias "
                    + "the same storage");
        }
        PathElement[] elements = path.clone();
        for (PathElement element : elements) {
            Objects.requireNonNull(element);
            if (element.equals(PathElement.dereferenceElement())) {
                throw new IllegalArgumentException("Dereference path elements are not "
                        + "supported by memory-segment updaters");
            }
        }
        VarHandle handle = layout.varHandle(elements);
        VarHandle.AccessMode readMode = mode == UpdaterMode.PLAIN
                ? VarHandle.AccessMode.GET : VarHandle.AccessMode.GET_ACQUIRE;
        VarHandle.AccessMode writeMode = mode == UpdaterMode.PLAIN
                ? VarHandle.AccessMode.SET
                : VarHandle.AccessMode.COMPARE_AND_EXCHANGE_RELEASE;
        if (!handle.isAccessModeSupported(readMode)
                || !handle.isAccessModeSupported(writeMode)) {
            throw new IllegalArgumentException("Selected memory layout does not "
                    + "support " + mode + " updater access modes");
        }
        List<Class<?>> coordinates = handle.coordinateTypes();
        if (coordinates.size() < 2
                || coordinates.getFirst() != MemorySegment.class
                || coordinates.stream().skip(1).anyMatch(t -> t != long.class)) {
            throw new IllegalArgumentException("Selected layout has unsupported "
                    + "memory-segment coordinates");
        }
        Method method = functionalMethod(computationShape);
        if (method.getReturnType() != handle.varType()
                || !coordinates.equals(Arrays.asList(method.getParameterTypes()))) {
            throw new IllegalArgumentException("Computing functional method must "
                    + "match the selected layout's MemorySegment, long base "
                    + "offset, open-path indices, and carrier type");
        }
        return new SegmentAccess(mode, handle, method,
                coordinates.toArray(Class<?>[]::new), handle.varType());
    }

    private static Method functionalMethod(Class<?> type) {
        if (type.getTypeParameters().length != 0) {
            throw new IllegalArgumentException("The segment functional interface "
                    + type.getTypeName() + " has erased type variables; declare "
                    + "a non-generic interface with concrete coordinate and "
                    + "result types");
        }
        if (hasRawGenericSuperinterface(type, new HashSet<>())) {
            throw new IllegalArgumentException("The segment functional interface "
                    + type.getTypeName() + " inherits a raw generic interface "
                    + "with erased type variables; parameterize the inherited "
                    + "interface or declare a non-generic interface with a "
                    + "concrete functional method");
        }
        Method method = LazyUpdaterFactory.functionalMethod(type);
        if (method.getTypeParameters().length != 0) {
            throw new IllegalArgumentException("The segment functional method "
                    + method + " has erased type variables; declare a method "
                    + "with concrete coordinate and result types");
        }
        return method;
    }

    private static boolean hasRawGenericSuperinterface(Class<?> type,
                                                        Set<Class<?>> visited) {
        if (!visited.add(type)) {
            return false;
        }
        for (Type parent : type.getGenericInterfaces()) {
            Class<?> parentClass;
            if (parent instanceof ParameterizedType parameterized) {
                parentClass = (Class<?>) parameterized.getRawType();
            } else {
                parentClass = (Class<?>) parent;
                if (parentClass.getTypeParameters().length != 0) {
                    return true;
                }
            }
            if (hasRawGenericSuperinterface(parentClass, visited)) {
                return true;
            }
        }
        return false;
    }

    private static <T> T create(Class<T> type, Method method,
                                SegmentUpdater updater, boolean useSite) {
        try {
            MethodHandle target = MethodHandles.lookup().findVirtual(
                    SegmentUpdater.class, useSite ? "getWithComputer" : "get",
                    useSite
                            ? MethodType.methodType(Object.class,
                                    Object.class, Object[].class)
                            : MethodType.methodType(Object.class, Object[].class))
                    .bindTo(updater)
                    .asCollector(Object[].class,
                            method.getParameterCount() - (useSite ? 1 : 0))
                    .asType(MethodType.methodType(method.getReturnType(),
                            method.getParameterTypes()));
            return LazyUpdaterFactory.create(type, method, target);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    private record SegmentAccess(UpdaterMode mode, VarHandle handle,
                                 Method computerMethod, Class<?>[] coordinateTypes,
                                 Class<?> valueType) { }

    private static final class SegmentUpdater {
        private static final MethodType READ_TYPE =
                MethodType.methodType(Object.class, Object[].class);
        private static final MethodType WRITE_TYPE =
                MethodType.methodType(void.class, Object[].class);
        private static final MethodType COMPUTE_TYPE =
                MethodType.methodType(Object.class, Object.class, Object[].class);

        private final UpdaterMode mode;
        private final MethodHandle read;
        private final MethodHandle write;
        private final MethodHandle exchange;
        private final MethodHandle compute;
        private final Object boundComputer;
        private final Object defaultValue;
        private final boolean addressValue;
        private final int coordinateCount;

        SegmentUpdater(SegmentAccess access, Object boundComputer)
                throws IllegalAccessException {
            VarHandle handle = access.handle();
            mode = access.mode();
            this.boundComputer = boundComputer;
            coordinateCount = access.coordinateTypes().length;
            addressValue = access.valueType() == MemorySegment.class;
            defaultValue = addressValue ? MemorySegment.NULL
                    : Wrapper.forPrimitiveType(access.valueType()).zero();
            VarHandle.AccessMode readMode = mode == UpdaterMode.PLAIN
                    ? VarHandle.AccessMode.GET : VarHandle.AccessMode.GET_ACQUIRE;
            read = handle.toMethodHandle(readMode)
                    .asSpreader(Object[].class, coordinateCount)
                    .asType(READ_TYPE);
            write = mode == UpdaterMode.PLAIN
                    ? handle.toMethodHandle(VarHandle.AccessMode.SET)
                            .asSpreader(Object[].class, coordinateCount + 1)
                            .asType(WRITE_TYPE)
                    : null;
            exchange = mode == UpdaterMode.PUBLISH_ONCE
                    ? handle.toMethodHandle(
                            VarHandle.AccessMode.COMPARE_AND_EXCHANGE_RELEASE)
                            .asSpreader(Object[].class, coordinateCount + 2)
                            .asType(READ_TYPE)
                    : null;
            MethodHandle computation = SharedSecrets.getJavaLangInvokeAccess()
                    .unreflectMethod(access.computerMethod())
                    .asSpreader(Object[].class, coordinateCount)
                    .asType(MethodType.methodType(access.valueType(),
                            Object.class, Object[].class));
            compute = computation.asType(COMPUTE_TYPE);
        }

        Object get(Object[] coordinates) throws Throwable {
            return getValue(boundComputer, coordinates);
        }

        Object getWithComputer(Object computer, Object[] coordinates)
                throws Throwable {
            return getValue(Objects.requireNonNull(computer), coordinates);
        }

        private Object getValue(Object computer, Object[] coordinates)
                throws Throwable {
            Object value = (Object) read.invokeExact(coordinates);
            if (!isDefault(value)) {
                return value;
            }
            Object candidate = (Object) compute.invokeExact(computer,
                    coordinates);
            if (isDefault(candidate)) {
                throw new IllegalArgumentException("The computed value is "
                        + "reserved for uninitialized storage");
            }
            if (mode == UpdaterMode.PLAIN) {
                Object[] arguments = Arrays.copyOf(coordinates,
                        coordinateCount + 1);
                arguments[coordinateCount] = candidate;
                write.invokeExact(arguments);
                return candidate;
            }
            Object[] arguments = Arrays.copyOf(coordinates,
                    coordinateCount + 2);
            arguments[coordinateCount] = defaultValue;
            arguments[coordinateCount + 1] = candidate;
            Object witness = (Object) exchange.invokeExact(arguments);
            if (isDefault(witness)) {
                // An address layout stores only a native address. A read
                // materializes a segment with the layout's target size and
                // global scope, which can differ from the candidate segment.
                return addressValue ? (Object) read.invokeExact(coordinates)
                        : candidate;
            }
            // A failed release exchange only observes its witness with plain
            // semantics. Acquire the winner's publication before returning it.
            return (Object) read.invokeExact(coordinates);
        }

        private boolean isDefault(Object value) {
            if (addressValue) {
                return value == null || value instanceof MemorySegment segment
                        && segment.isNative() && segment.address() == 0L;
            }
            // Float.equals and Double.equals distinguish +0.0 from -0.0,
            // matching the bitwise comparison used by atomic exchanges.
            return defaultValue.equals(value);
        }
    }
}
