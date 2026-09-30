/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.invoke;

import java.lang.classfile.Opcode;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Lazy;
import java.util.Objects;

import jdk.internal.access.SharedSecrets;
import jdk.internal.reflect.ConstantPool;
import jdk.internal.value.ValueClass;
import sun.invoke.util.Wrapper;

import static java.lang.classfile.Opcode.*;
import static java.lang.invoke.MethodHandles.Lookup.IMPL_LOOKUP;

/**
 * Factories and field-access utilities for lazy updaters.
 *
 * @since 28
 */
public class LazyUpdaters {

    /**
     * Creates a {@code LazyUpdaters} instance.
     */
    public LazyUpdaters() { }

    // In LazyUpdaters
    public sealed interface Mode permits Lazy.Mode, Uncoordinated {
        Lazy.Mode COMPUTE_ONCE = Lazy.Mode.COMPUTE_ONCE;
        Lazy.Mode PUBLISH_ONCE = Lazy.Mode.PUBLISH_ONCE;
        Mode UN_COORDINATED = Uncoordinated.UN_COORDINATED;
    }

    private enum Uncoordinated implements Mode {
        UN_COORDINATED
    }

    /**
     * Returns a var handle for the field read by the given lambda.
     * The lambda must be non-capturing and must read either an instance field
     * directly from its sole argument, as in {@code p -> p.radius}, or a static
     * field without arguments, as in {@code () -> Point.shared}. The lambda is
     * inspected, without being invoked. Neither serialization nor class-file
     * resources are required.
     *
     * <p>The accessor must already have a functional interface type. For
     * example, if {@code Point.radius} is a {@code double} field:
     * {@snippet lang=java :
     * VarHandle radius = LazyUpdaters.findVarHandle(
     *         MethodHandles.lookup(),
     *         (ToDoubleFunction<Point>) p -> p.radius);
     * }
     * A reference-returning functional interface, such as
     * {@code Function<Point, Double>}, may also be used. Boxing a primitive
     * field value is permitted; the var handle still has the primitive field
     * type. Other computations or conversions, including unboxing and numeric
     * widening, are not supported. A static field may be selected using a
     * supplier-shaped interface, such as {@code DoubleSupplier} or
     * {@code Supplier<Double>}. Captured receivers, nested field accesses,
     * ignored arguments, and method references are not supported.
     *
     * <p>Access is checked using {@code lookup}, with the symbolic field owner,
     * name, and type found in the lambda, as if by
     * {@link MethodHandles.Lookup#findVarHandle(Class, String, Class)
     * lookup.findVarHandle} for an instance field or
     * {@link MethodHandles.Lookup#findStaticVarHandle(Class, String, Class)
     * lookup.findStaticVarHandle} for a static field. Possession of the lambda
     * does not grant access to its field. The returned handle has the access
     * modes supported by that lookup operation; in particular, a final field
     * produces a read-only handle. Selecting a static field does not initialize
     * its declaring class; operating on the returned handle initializes that
     * class if necessary.
     *
     * @implNote This implementation recognizes hidden, synthetic lambda
     * classes whose functional method forwards its optional receiver argument,
     * with an optional reference cast, to a static synthetic implementation
     * method. That method must consist of either loading its argument and
     * reading an instance field, or reading a static field without arguments,
     * followed by optional boxing and returning the result. An optional boxing
     * adaptation in the forwarding method is also supported. Captures,
     * exception handlers, synchronization, and other instruction sequences are
     * rejected. Lambda implementations using a method handle to forward the invocation, such
     * as lambdas declared in a hidden class, are not currently supported.
     * Recognition uses the loaded methods' bytecodes and constant pools.
     *
     * @param lookup the lookup used to check access to the field
     * @param fieldAccessor a non-capturing lambda that reads a field
     * @return a var handle for the selected field
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalArgumentException if the accessor is not a supported
     *         field-access lambda
     * @throws IllegalAccessException if the lookup does not have access to
     *         the field
     */
    static VarHandle findVarHandle(MethodHandles.Lookup lookup,
                                   Object fieldAccessor)
            throws IllegalAccessException {
        return crackField(lookup, fieldAccessor).handle();
    }

    private record FieldAccess(VarHandle handle, Field field, Class<?> receiverType) { }

    private static FieldAccess crackField(MethodHandles.Lookup lookup,
                                          Object fieldAccessor)
            throws IllegalAccessException {
        Objects.requireNonNull(lookup);
        Objects.requireNonNull(fieldAccessor);
        Class<?> lambdaClass = fieldAccessor.getClass();
        if (!lambdaClass.isHidden() || !lambdaClass.isSynthetic()) {
            throw unsupportedAccessor("expected a hidden lambda class");
        }
        for (Field field : lambdaClass.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                throw unsupportedAccessor("captured arguments");
            }
        }

        Method functionalMethod = null;
        for (Method method : lambdaClass.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())
                    && !Modifier.isStatic(method.getModifiers())
                    && !method.isBridge()) {
                if (functionalMethod != null || method.getParameterCount() > 1
                        || (method.getParameterCount() == 1
                            && method.getParameterTypes()[0].isPrimitive())
                        || method.getReturnType() == void.class) {
                    throw unsupportedAccessor("expected zero or one receiver and a result");
                }
                functionalMethod = method;
            }
        }
        if (functionalMethod == null) {
            throw unsupportedAccessor("no functional method");
        }

        boolean isStatic = functionalMethod.getParameterCount() == 0;
        AccessorCode forwarding = new AccessorCode(functionalMethod);
        Class<?> receiverType = null;
        if (!isStatic) {
            forwarding.expect(ALOAD_1);
            receiverType = functionalMethod.getParameterTypes()[0];
            if (forwarding.at(CHECKCAST)) {
                receiverType = forwarding.pool.getClassAt(forwarding.member(CHECKCAST));
            }
        }
        Method implementation = forwarding.method(INVOKESTATIC);
        if (!Modifier.isStatic(implementation.getModifiers())
                || !implementation.isSynthetic()
                || implementation.getParameterCount() != functionalMethod.getParameterCount()
                || (!isStatic && (implementation.getParameterTypes()[0].isPrimitive()
                    || !implementation.getParameterTypes()[0].isAssignableFrom(receiverType)))) {
            throw unsupportedAccessor("expected a static lambda implementation");
        }
        forwarding.finishReturn(implementation.getReturnType(), functionalMethod.getReturnType());

        AccessorCode getter = new AccessorCode(implementation);
        if (!isStatic) {
            getter.expect(ALOAD_0);
        }
        int fieldIndex = getter.member(isStatic ? GETSTATIC : GETFIELD);
        Field field = getter.pool.getFieldAt(fieldIndex);
        Class<?> owner = getter.pool.getClassAt(getter.pool.getClassRefIndexAt(fieldIndex));
        if (Modifier.isStatic(field.getModifiers()) != isStatic
                || (!isStatic && !owner.isAssignableFrom(implementation.getParameterTypes()[0]))) {
            throw unsupportedAccessor("field does not match the accessor shape");
        }
        getter.finishReturn(field.getType(), implementation.getReturnType());
        try {
            VarHandle handle = isStatic
                    ? lookup.findStaticVarHandle(owner, field.getName(), field.getType())
                    : lookup.findVarHandle(owner, field.getName(), field.getType());
            return new FieldAccess(handle, field,
                    isStatic ? null : implementation.getParameterTypes()[0]);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("Cannot resolve the selected field", e);
        }
    }

    private static IllegalArgumentException unsupportedAccessor(String reason) {
        return new IllegalArgumentException("Unsupported field-access lambda: " + reason);
    }

    // Parse only the recognized straight-line sequences. In particular, a
    // constant-pool reference alone is not evidence that the lambda uses it.
    private static final class AccessorCode {
        final byte[] code;
        final ConstantPool pool;
        int position;

        AccessorCode(Method method) {
            code = MethodHandleNatives.methodBytecodes(new MemberName(method));
            if (code == null) {
                throw unsupportedAccessor("method has no supported straight-line body");
            }
            pool = SharedSecrets.getJavaLangAccess().getConstantPool(method.getDeclaringClass());
        }

        boolean at(Opcode opcode) {
            return position < code.length && Byte.toUnsignedInt(code[position]) == opcode.bytecode();
        }

        void expect(Opcode opcode) {
            if (!at(opcode)) {
                throw unsupportedAccessor("expected " + opcode + " at bytecode index " + position);
            }
            position++;
        }

        int member(Opcode opcode) {
            expect(opcode);
            if (code.length - position < 2) {
                throw unsupportedAccessor("incomplete instruction");
            }
            int index = Byte.toUnsignedInt(code[position]) << 8
                    | Byte.toUnsignedInt(code[position + 1]);
            position += 2;
            return index;
        }

        Method method(Opcode opcode) {
            if (pool.getMethodAt(member(opcode)) instanceof Method method) {
                return method;
            }
            throw unsupportedAccessor("expected a method invocation");
        }

        void finishReturn(Class<?> valueType, Class<?> returnType) {
            if (valueType == void.class) {
                throw unsupportedAccessor("expected a field value");
            }
            if (at(INVOKESTATIC) && valueType.isPrimitive()) {
                Class<?> boxedType = Wrapper.forPrimitiveType(valueType).wrapperType();
                Method boxing = method(INVOKESTATIC);
                if (boxing.getDeclaringClass() != boxedType
                        || !boxing.getName().equals("valueOf")
                        || !Modifier.isStatic(boxing.getModifiers())
                        || boxing.getParameterCount() != 1
                        || boxing.getParameterTypes()[0] != valueType
                        || boxing.getReturnType() != boxedType) {
                    throw unsupportedAccessor("expected primitive boxing");
                }
                valueType = boxedType;
            }
            if (valueType.isPrimitive() ? valueType != returnType
                    : !returnType.isAssignableFrom(valueType)) {
                throw unsupportedAccessor("unsupported return conversion");
            }
            Opcode opcode = !valueType.isPrimitive() ? ARETURN
                    : valueType == long.class ? LRETURN
                    : valueType == float.class ? FRETURN
                    : valueType == double.class ? DRETURN : IRETURN;
            expect(opcode);
            if (position != code.length) {
                throw unsupportedAccessor("additional instructions");
            }
        }
    }

    // Add a mode PLAIN, RACE, SYNCHRONIZED

    /**
     * Returns an implementation of the given functional interface that lazily
     * computes and publishes a value in the field identified by the accessor.
     * The field supplies the storage; instance fields have independent values
     * for each receiver, and a static field has one value for its declaring
     * class. The factory neither invokes the accessor nor reads the field or
     * invokes the computing function.
     *
     * <p>The field accessor must be a non-capturing lambda that directly reads
     * the field. For an instance field it has one reference argument, as in
     * {@code p -> p.radius}; the computing function and returned updater take
     * the same receiver. For a static field it has no arguments, as in
     * {@code () -> Geometry.radius}. For example:
     * {@snippet lang=java :
     * private static double radius;
     * private static final DoubleSupplier LAZY_RADIUS = LazyUpdaters.ofField(
     *         MethodHandles.lookup(), DoubleSupplier.class,
     *         () -> Geometry.radius, () -> Math.hypot(3.0, 4.0));
     * }
     * The accessor is inspected using the loaded methods' bytecodes. Boxing a
     * primitive field value is supported; captures, nested field accesses,
     * method references, and other computations or conversions in the accessor
     * are not supported. The computing function may capture state or be a
     * method reference, and is retained by the returned updater.
     *
     * <p>The field must be mutable, support acquire reads and atomic updates,
     * and, for reference fields, permit {@code null}. Its Java default value
     * is reserved to mean uninitialized: {@code null} for references,
     * {@code false} for {@code boolean}, zero for integral types, and positive
     * zero for floating-point types. A computation that returns the reserved
     * value throws {@code IllegalArgumentException} without publishing a
     * value. Negative floating-point zero and NaN are permitted. A preexisting
     * non-default field value is used without invoking the computing function.
     *
     * <p>An invocation reads the field with acquire semantics. If the value is
     * uninitialized, it invokes the computing function, checks the result, and
     * attempts to publish it with a release compare-and-exchange. Computations
     * may race, but successful invocations return the published winner. A
     * losing invocation performs an acquire read before returning the winner.
     * Actions preceding the winner's publication happen
     * before actions following an invocation that observes that publication.
     * A computation that throws propagates its exception unchanged, publishes
     * nothing, and may be retried by another invocation. Recursive invocation
     * of an updater for the same uninitialized field is not detected.
     *
     * <p>The field must be reserved for lazy storage while an updater is in
     * use: it must not be reset or otherwise modified directly. Multiple
     * updaters may share a field if their computations are interchangeable;
     * whichever publishes first determines the value returned by all of them.
     * The factory allocates no additional storage per receiver and retains no
     * receiver except through state explicitly captured by the computing
     * function. Accessing a static field through the updater initializes its
     * declaring class if necessary, following normal var-handle semantics.
     *
     * <p>The interface must have a single abstract method, with either no
     * parameters or one reference parameter, and a non-void result. Inherited
     * abstract methods with the same erased parameter types, covariant return
     * types, and default methods are supported. The
     * result must be the field type or a reference type to which the field
     * value, boxed if necessary, can be assigned. Computed results must also
     * be convertible to the field type; an incompatible result fails before
     * publication. A null instance receiver throws {@code NullPointerException}
     * before computation. The supplied lookup must have access to both the
     * field and the interface, and any receiver restriction imposed by the
     * lookup is preserved.
     *
     * @implNote Sealed and hidden interface types, inherited abstract methods
     * requiring bridges with different parameter types, and field-selector
     * lambdas whose proxy forwards through a method handle are not supported.
     * The latter includes lambdas declared in hidden classes.
     *
     * @param lookup the lookup used to authorize field and interface access
     * @param type the functional interface implemented by both functions and
     *             the returned updater
     * @param fieldAccessor a non-capturing lambda identifying the field
     * @param computingFunction the computation to invoke for an uninitialized
     *                          field
     * @param <T> the functional interface type
     * @return a lazy updater implementing {@code type}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if the interface, functions, or selected
     *         field are incompatible or unsupported, or the lookup cannot
     *         access the field or interface
     * @since 28
     */
    public static <T> T ofField(MethodHandles.Lookup lookup,
                                Class<T> type,
                                T fieldAccessor,
                                T computingFunction) {
        Objects.requireNonNull(lookup);
        Objects.requireNonNull(type);
        Objects.requireNonNull(fieldAccessor);
        Objects.requireNonNull(computingFunction);
        Method method = LazyUpdaterFactory.functionalMethod(type);
        if (!type.isInstance(fieldAccessor) || !type.isInstance(computingFunction)) {
            throw new IllegalArgumentException("Functions must implement " + type.getTypeName());
        }
        try {
            lookup.accessClass(type);
            FieldAccess access = crackField(lookup, fieldAccessor);
            VarHandle field = access.handle();
            if (Modifier.isFinal(access.field().getModifiers())
                    || ValueClass.isNullRestrictedField(access.field())
                    || !field.isAccessModeSupported(VarHandle.AccessMode.GET_ACQUIRE)
                    || !field.isAccessModeSupported(VarHandle.AccessMode.COMPARE_AND_EXCHANGE_RELEASE)) {
                throw new IllegalArgumentException("Not a mutable lazy storage field: " + access.field());
            }
            boolean isStatic = access.receiverType() == null;
            if (method.getParameterCount() != (isStatic ? 0 : 1)
                    || method.getReturnType() == void.class
                    || (!isStatic && (method.getParameterTypes()[0].isPrimitive()
                        || !method.getParameterTypes()[0].isAssignableFrom(access.receiverType())))) {
                throw new IllegalArgumentException("Functional method does not match the field accessor");
            }
            Class<?> valueType = field.varType();
            Class<?> boxedType = valueType.isPrimitive()
                    ? Wrapper.forPrimitiveType(valueType).wrapperType() : valueType;
            if (method.getReturnType() != valueType
                    && !method.getReturnType().isAssignableFrom(boxedType)) {
                throw new IllegalArgumentException("Functional result does not match the field type");
            }
            if (!isStatic) {
                Class<?> coordinate = field.coordinateTypes().getFirst();
                if (!coordinate.isAssignableFrom(access.receiverType())
                        && !access.receiverType().isAssignableFrom(coordinate)) {
                    throw new IllegalArgumentException("Lookup receiver restriction does not match the accessor");
                }
            }

            FieldUpdater updater = new FieldUpdater(access, method, computingFunction);
            MethodHandle target = IMPL_LOOKUP.findVirtual(FieldUpdater.class, "get",
                    FieldUpdater.READ_TYPE).bindTo(updater);
            if (isStatic) {
                target = MethodHandles.insertArguments(target, 0, (Object) null);
            }
            target = target.asType(MethodType.methodType(method.getReturnType(), method.getParameterTypes()));
            return LazyUpdaterFactory.create(type, method, target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Cannot access the lazy field or functional interface", e);
        }
    }

    private static final class FieldUpdater {
        static final MethodType READ_TYPE = MethodType.methodType(Object.class, Object.class);
        static final MethodType EXCHANGE_TYPE = MethodType.methodType(Object.class,
                Object.class, Object.class, Object.class);

        private final MethodHandle read;
        private final MethodHandle exchange;
        private final MethodHandle compute;
        private final Object defaultValue;

        FieldUpdater(FieldAccess access, Method method, Object computingFunction)
                throws IllegalAccessException {
            VarHandle field = access.handle();
            Class<?> valueType = field.varType();
            defaultValue = valueType.isPrimitive() ? Wrapper.forPrimitiveType(valueType).zero() : null;
            MethodHandle reader = field.toMethodHandle(VarHandle.AccessMode.GET_ACQUIRE);
            MethodHandle exchanger = field.toMethodHandle(VarHandle.AccessMode.COMPARE_AND_EXCHANGE_RELEASE);
            MethodHandle computation = IMPL_LOOKUP.unreflect(method).bindTo(computingFunction);
            if (access.receiverType() == null) {
                computation = computation.asType(MethodType.methodType(valueType));
                reader = MethodHandles.dropArguments(reader, 0, Object.class);
                exchanger = MethodHandles.dropArguments(exchanger, 0, Object.class);
                computation = MethodHandles.dropArguments(computation, 0, Object.class);
            } else {
                // Preserve the selector's receiver cast even when the erased
                // functional interface or the field owner has a broader type.
                Class<?> receiver = access.receiverType();
                reader = reader.asType(MethodType.methodType(Object.class, receiver));
                exchanger = exchanger.asType(MethodType.methodType(Object.class,
                        receiver, Object.class, Object.class));
                computation = computation.asType(MethodType.methodType(valueType, receiver));
            }
            read = reader.asType(READ_TYPE);
            exchange = exchanger.asType(EXCHANGE_TYPE);
            compute = computation.asType(READ_TYPE);
        }

        Object get(Object receiver) throws Throwable {
            Object value = (Object) read.invokeExact(receiver);
            if (!isDefault(value)) {
                return value;
            }
            Object candidate = (Object) compute.invokeExact(receiver);
            if (isDefault(candidate)) {
                throw new IllegalArgumentException("The computed value is reserved for uninitialized storage");
            }
            Object witness = (Object) exchange.invokeExact(receiver, defaultValue, candidate);
            if (isDefault(witness)) {
                return candidate;
            }
            // A failed release exchange observes the witness with plain
            // semantics. Acquire the winner's publication before returning it.
            return (Object) read.invokeExact(receiver);
        }

        private boolean isDefault(Object value) {
            // Float/Double.equals distinguish positive and negative zero,
            // matching the bitwise comparison used by the atomic exchange.
            return defaultValue == null ? value == null : defaultValue.equals(value);
        }
    }
}
