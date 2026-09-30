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

package jdk.internal.invoke;

import java.lang.classfile.Opcode;
import java.lang.invoke.LazyUpdaters.Mode;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import jdk.internal.access.SharedSecrets;
import jdk.internal.reflect.ConstantPool;
import jdk.internal.value.ValueClass;
import sun.invoke.util.Wrapper;

import static java.lang.classfile.Opcode.*;

/**
 * Implementation of lazy storage selection and updater creation.
 */
public final class LazyUpdaterSupport {
    private LazyUpdaterSupport() { }

    public static <T> T ofLocation(Mode mode,
                                   MethodHandles.Lookup lookup,
                                   Class<T> type,
                                   T locationAccessor,
                                   T computingFunction) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(lookup);
        Objects.requireNonNull(type);
        Objects.requireNonNull(locationAccessor);
        Objects.requireNonNull(computingFunction);
        Method method = LazyUpdaterFactory.functionalMethod(type);
        if (!type.isInstance(locationAccessor) || !type.isInstance(computingFunction)) {
            throw new IllegalArgumentException("Functions must implement " + type.getTypeName());
        }
        try {
            lookup.accessClass(type);
            StorageAccess access = crackAccess(lookup, locationAccessor);
            VarHandle storage = access instanceof ArrayAccess array
                    ? array.elementHandle() : access.handle();
            if (!storage.isAccessModeSupported(readMode(mode))
                    || !storage.isAccessModeSupported(writeMode(mode))) {
                throw new IllegalArgumentException("Lazy storage does not support the requested mode");
            }
            if (access instanceof FieldAccess fieldAccess
                    && (Modifier.isFinal(fieldAccess.field().getModifiers())
                        || ValueClass.isNullRestrictedField(fieldAccess.field()))) {
                throw new IllegalArgumentException("Not a mutable lazy storage field: " + fieldAccess.field());
            }
            boolean hasReceiver = access.receiverType() != null;
            int expectedParameters = access instanceof ArrayAccess array
                    ? array.rank() + (hasReceiver ? 1 : 0) : hasReceiver ? 1 : 0;
            if (method.getParameterCount() != expectedParameters
                    || method.getReturnType() == void.class
                    || (hasReceiver && (method.getParameterTypes()[0].isPrimitive()
                        || !method.getParameterTypes()[0].isAssignableFrom(access.receiverType())))) {
                throw new IllegalArgumentException("Functional method does not match the storage accessor");
            }
            if (access instanceof ArrayAccess array) {
                for (int i = hasReceiver ? 1 : 0; i < expectedParameters; i++) {
                    if (method.getParameterTypes()[i] != int.class) {
                        throw new IllegalArgumentException("Array coordinates must be int parameters");
                    }
                }
            }
            Class<?> valueType = storage.varType();
            Class<?> boxedType = valueType.isPrimitive()
                    ? Wrapper.forPrimitiveType(valueType).wrapperType() : valueType;
            if (method.getReturnType() != valueType
                    && !method.getReturnType().isAssignableFrom(boxedType)) {
                throw new IllegalArgumentException("Functional result does not match the storage type");
            }
            if (access instanceof FieldAccess fieldAccess && hasReceiver) {
                Class<?> coordinate = storage.coordinateTypes().getFirst();
                if (!coordinate.isAssignableFrom(access.receiverType())
                        && !access.receiverType().isAssignableFrom(coordinate)) {
                    throw new IllegalArgumentException("Lookup receiver restriction does not match the accessor");
                }
            }

            MethodHandle target;
            if (access instanceof ArrayAccess array) {
                ArrayUpdater updater = new ArrayUpdater(mode, array, method, computingFunction);
                MethodType readType = array.rank() == 1
                        ? ArrayUpdater.READ1_TYPE : ArrayUpdater.READ2_TYPE;
                target = MethodHandles.lookup().findVirtual(ArrayUpdater.class,
                        array.rank() == 1 ? "get1" : "get2", readType).bindTo(updater);
            } else {
                FieldUpdater updater = new FieldUpdater(mode, (FieldAccess) access, method, computingFunction);
                target = MethodHandles.lookup().findVirtual(FieldUpdater.class, "get",
                        FieldUpdater.READ_TYPE).bindTo(updater);
            }
            if (!hasReceiver) {
                target = MethodHandles.insertArguments(target, 0, (Object) null);
            }
            target = target.asType(MethodType.methodType(method.getReturnType(), method.getParameterTypes()));
            return LazyUpdaterFactory.create(type, method, target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Cannot access the lazy location or functional interface", e);
        }
    }

    /**
     * Returns a var handle for the field or array element read by a direct,
     * non-capturing accessor lambda. A static field accessor has no arguments;
     * an instance field accessor has one reference argument; and an array
     * element accessor has one or two {@code int} indices, optionally preceded
     * by a reference argument. A zero-argument accessor of a static array field
     * selects the field itself, rather than one of its elements.
     *
     * <p>The accessor is inspected without being invoked. The caller's lookup
     * must authorize access to the selected field or array type; possession of
     * the accessor does not grant access. The returned handle retains the
     * selected storage's access modes, so a final field is read-only.
     *
     * @param lookup the lookup used to authorize access
     * @param fieldAccessor the direct field or array-element accessor lambda
     * @return a var handle for the selected storage
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalArgumentException if the accessor has an unsupported shape
     * @throws IllegalAccessException if the lookup cannot access the storage
     */
    public static VarHandle findVarHandle(MethodHandles.Lookup lookup,
                                          Object fieldAccessor)
            throws IllegalAccessException {
        return crackAccess(lookup, fieldAccessor).handle();
    }

    private sealed interface StorageAccess permits FieldAccess, ArrayAccess {
        VarHandle handle();
        Class<?> receiverType();
    }

    private record FieldAccess(VarHandle handle, Field field, Class<?> receiverType)
            implements StorageAccess { }

    private record ArrayAccess(VarHandle handle, VarHandle elementHandle,
                               MethodHandle arrayGetter, Class<?> receiverType,
                               Class<?> arrayType, int[] indexOrder)
            implements StorageAccess {
        int rank() {
            return indexOrder.length;
        }
    }

    private static StorageAccess crackAccess(MethodHandles.Lookup lookup,
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
                int parameterCount = method.getParameterCount();
                boolean hasReceiver = parameterCount > 0
                        && !method.getParameterTypes()[0].isPrimitive();
                if (functionalMethod != null
                        || parameterCount > (hasReceiver ? 3 : 2)
                        || method.getReturnType() == void.class) {
                    throw unsupportedAccessor("expected an optional receiver, up to two int indices, and a result");
                }
                for (int i = hasReceiver ? 1 : 0; i < parameterCount; i++) {
                    if (method.getParameterTypes()[i] != int.class) {
                        throw unsupportedAccessor("array indices must be int parameters");
                    }
                }
                functionalMethod = method;
            }
        }
        if (functionalMethod == null) {
            throw unsupportedAccessor("no functional method");
        }

        int parameterCount = functionalMethod.getParameterCount();
        boolean hasReceiver = parameterCount > 0
                && !functionalMethod.getParameterTypes()[0].isPrimitive();
        boolean isStaticField = parameterCount == 0;
        AccessorCode forwarding = new AccessorCode(functionalMethod);
        Class<?> receiverType = null;
        if (hasReceiver) {
            forwarding.expect(ALOAD_1);
            receiverType = functionalMethod.getParameterTypes()[0];
            if (forwarding.at(CHECKCAST)) {
                receiverType = forwarding.pool.getClassAt(forwarding.member(CHECKCAST));
            }
        }
        for (int i = hasReceiver ? 1 : 0; i < parameterCount; i++) {
            forwarding.expectIntLoad(i + 1);
        }
        Method implementation = forwarding.method(INVOKESTATIC);
        if (!Modifier.isStatic(implementation.getModifiers())
                || !implementation.isSynthetic()
                || implementation.getParameterCount() != parameterCount
                || (hasReceiver && (implementation.getParameterTypes()[0].isPrimitive()
                || !implementation.getParameterTypes()[0].isAssignableFrom(receiverType)))) {
            throw unsupportedAccessor("expected a static lambda implementation");
        }
        for (int i = hasReceiver ? 1 : 0; i < parameterCount; i++) {
            if (implementation.getParameterTypes()[i] != int.class) {
                throw unsupportedAccessor("expected int indices in the lambda implementation");
            }
        }
        forwarding.finishReturn(implementation.getReturnType(), functionalMethod.getReturnType());

        AccessorCode getter = new AccessorCode(implementation);
        if (hasReceiver) {
            getter.expect(ALOAD_0);
        }
        if (parameterCount > (hasReceiver ? 1 : 0)) {
            return crackArrayAccess(lookup, getter, implementation,
                    parameterCount - (hasReceiver ? 1 : 0), hasReceiver);
        }
        int fieldIndex = getter.member(isStaticField ? GETSTATIC : GETFIELD);
        Field field = getter.pool.getFieldAt(fieldIndex);
        Class<?> owner = getter.pool.getClassAt(getter.pool.getClassRefIndexAt(fieldIndex));
        if (Modifier.isStatic(field.getModifiers()) != isStaticField
                || (!isStaticField && !owner.isAssignableFrom(implementation.getParameterTypes()[0]))) {
            throw unsupportedAccessor("field does not match the accessor shape");
        }
        getter.finishReturn(field.getType(), implementation.getReturnType());
        try {
            VarHandle handle = isStaticField
                    ? lookup.findStaticVarHandle(owner, field.getName(), field.getType())
                    : lookup.findVarHandle(owner, field.getName(), field.getType());
            return new FieldAccess(handle, field,
                    isStaticField ? null : implementation.getParameterTypes()[0]);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("Cannot resolve the selected field", e);
        }
    }

    private static ArrayAccess crackArrayAccess(MethodHandles.Lookup lookup,
                                                AccessorCode getter,
                                                Method implementation,
                                                int rank,
                                                boolean hasReceiver) throws IllegalAccessException {
        Class<?> receiverType = hasReceiver ? implementation.getParameterTypes()[0] : null;
        Class<?> arrayType = receiverType;
        MethodHandle arrayGetter;
        MethodHandle fieldGetter = null;
        if (!hasReceiver || getter.at(GETFIELD)) {
            int fieldIndex = getter.member(hasReceiver ? GETFIELD : GETSTATIC);
            Field field = getter.pool.getFieldAt(fieldIndex);
            Class<?> owner = getter.pool.getClassAt(getter.pool.getClassRefIndexAt(fieldIndex));
            if (Modifier.isStatic(field.getModifiers()) == hasReceiver
                    || (hasReceiver && !owner.isAssignableFrom(receiverType))) {
                throw unsupportedAccessor("array field does not match the receiver");
            }
            arrayType = field.getType();
            try {
                VarHandle fieldHandle = hasReceiver
                        ? lookup.findVarHandle(owner, field.getName(), arrayType)
                        : lookup.findStaticVarHandle(owner, field.getName(), arrayType);
                if (hasReceiver) {
                    Class<?> coordinate = fieldHandle.coordinateTypes().getFirst();
                    if (!coordinate.isAssignableFrom(receiverType)
                            && !receiverType.isAssignableFrom(coordinate)) {
                        throw unsupportedAccessor("lookup receiver restriction does not match the accessor");
                    }
                }
                fieldGetter = fieldHandle.toMethodHandle(VarHandle.AccessMode.GET);
            } catch (NoSuchFieldException e) {
                throw new IllegalArgumentException("Cannot resolve the selected array field", e);
            }
            arrayGetter = hasReceiver
                    ? fieldGetter.asType(MethodType.methodType(arrayType, receiverType))
                    : MethodHandles.dropArguments(fieldGetter, 0, Object.class);
        } else {
            arrayGetter = MethodHandles.identity(arrayType);
        }
        if (!arrayType.isArray()
                || (rank == 2 && !arrayType.getComponentType().isArray())) {
            throw unsupportedAccessor("accessor must supply an array of the selected rank");
        }
        lookup.accessClass(arrayType);
        arrayGetter = arrayGetter.asType(MethodType.methodType(Object.class, Object.class));

        int[] indexOrder = new int[rank];
        int firstIndexLocal = hasReceiver ? 1 : 0;
        Class<?> elementArrayType = arrayType;
        for (int i = 0; i < rank; i++) {
            int index = getter.intLoad(firstIndexLocal, rank) - firstIndexLocal;
            for (int j = 0; j < i; j++) {
                if (indexOrder[j] == index) {
                    throw unsupportedAccessor("each array index must be used exactly once");
                }
            }
            indexOrder[i] = index;
            Class<?> component = elementArrayType.getComponentType();
            getter.expect(arrayLoadOpcode(component));
            if (i + 1 < rank) {
                elementArrayType = component;
            }
        }
        Class<?> valueType = elementArrayType.getComponentType();
        getter.finishReturn(valueType, implementation.getReturnType());

        VarHandle elementHandle = MethodHandles.arrayElementVarHandle(elementArrayType);
        VarHandle handle = elementHandle;
        if (rank == 2) {
            handle = MethodHandles.collectCoordinates(handle, 0,
                    MethodHandles.arrayElementGetter(arrayType));
        }
        if (fieldGetter != null) {
            handle = MethodHandles.collectCoordinates(handle, 0, fieldGetter);
        }
        if (rank == 2 && indexOrder[0] == 1) {
            List<Class<?>> coordinates = handle.coordinateTypes();
            handle = hasReceiver
                    ? MethodHandles.permuteCoordinates(handle, coordinates, 0, 2, 1)
                    : MethodHandles.permuteCoordinates(handle, coordinates, 1, 0);
        }
        return new ArrayAccess(handle, elementHandle, arrayGetter, receiverType,
                arrayType, indexOrder);
    }

    private static Opcode arrayLoadOpcode(Class<?> component) {
        if (!component.isPrimitive()) {
            return AALOAD;
        }
        if (component == boolean.class || component == byte.class) {
            return BALOAD;
        }
        if (component == short.class) {
            return SALOAD;
        }
        if (component == char.class) {
            return CALOAD;
        }
        if (component == int.class) {
            return IALOAD;
        }
        if (component == long.class) {
            return LALOAD;
        }
        if (component == float.class) {
            return FALOAD;
        }
        return DALOAD;
    }

    private static IllegalArgumentException unsupportedAccessor(String reason) {
        return new IllegalArgumentException("Unsupported storage-access lambda: " + reason);
    }

    // Parse only the recognized straight-line sequences. In particular, a
    // constant-pool reference alone is not evidence that the lambda uses it.
    private static final class AccessorCode {
        final byte[] code;
        final ConstantPool pool;
        int position;

        AccessorCode(Method method) {
            code = SharedSecrets.getJavaLangInvokeAccess().methodBytecodes(method);
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

        void expectIntLoad(int local) {
            expect(intLoadOpcode(local));
        }

        int intLoad(int firstLocal, int count) {
            for (int local = firstLocal; local < firstLocal + count; local++) {
                if (at(intLoadOpcode(local))) {
                    position++;
                    return local;
                }
            }
            throw unsupportedAccessor("expected a direct int index at bytecode index " + position);
        }

        private static Opcode intLoadOpcode(int local) {
            return switch (local) {
                case 0 -> ILOAD_0;
                case 1 -> ILOAD_1;
                case 2 -> ILOAD_2;
                case 3 -> ILOAD_3;
                default -> throw new IllegalArgumentException("Unsupported local index: " + local);
            };
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

    private static VarHandle.AccessMode readMode(Mode mode) {
        return switch (mode) {
            case PLAIN -> VarHandle.AccessMode.GET;
            case PUBLISH_ONCE, SERIALIZED -> VarHandle.AccessMode.GET_ACQUIRE;
        };
    }

    private static VarHandle.AccessMode writeMode(Mode mode) {
        return switch (mode) {
            case PLAIN -> VarHandle.AccessMode.SET;
            case PUBLISH_ONCE -> VarHandle.AccessMode.COMPARE_AND_EXCHANGE_RELEASE;
            case SERIALIZED -> VarHandle.AccessMode.SET_RELEASE;
        };
    }

    // Keep coordination state outside the receiver, keyed weakly by identity.
    private static final ClassValue<ConcurrentHashMap<Field, SynchronizedField>> SYNCHRONIZED_FIELDS =
            new ClassValue<>() {
                @Override
                protected ConcurrentHashMap<Field, SynchronizedField> computeValue(Class<?> type) {
                    return new ConcurrentHashMap<>();
                }
            };

    // Array coordination follows the physical element, not the path used to
    // reach it. In particular, two outer arrays may contain the same row.
    private static final ClassValue<SynchronizedArray> SYNCHRONIZED_ARRAYS =
            new ClassValue<>() {
                @Override
                protected SynchronizedArray computeValue(Class<?> type) {
                    return new SynchronizedArray();
                }
            };

    private static final class SynchronizedField {
        private final SynchronizedState staticState;
        private final ReferenceQueue<Object> staleReceivers;
        private final Map<ReceiverKey, SynchronizedState> instanceStates;

        private SynchronizedField(boolean staticField) {
            staticState = staticField ? new SynchronizedState(null) : null;
            staleReceivers = staticField ? null : new ReferenceQueue<>();
            instanceStates = staticField ? null : new HashMap<>();
        }

        static SynchronizedField forField(Field field, boolean staticField) {
            return SYNCHRONIZED_FIELDS.get(field.getDeclaringClass())
                    .computeIfAbsent(field, ignored -> new SynchronizedField(staticField));
        }

        synchronized SynchronizedState stateFor(Object receiver) {
            if (staticState != null) {
                return staticState;
            }
            removeStaleReceivers();
            SynchronizedState state = instanceStates.get(new ReceiverKey(receiver));
            if (state == null) {
                ReceiverKey key = new ReceiverKey(receiver, staleReceivers);
                state = new SynchronizedState(key);
                instanceStates.put(key, state);
            }
            return state;
        }

        synchronized void discard(SynchronizedState state) {
            if (state.key != null) {
                instanceStates.remove(state.key, state);
            }
        }

        private void removeStaleReceivers() {
            ReceiverKey key;
            while ((key = (ReceiverKey) staleReceivers.poll()) != null) {
                instanceStates.remove(key);
            }
        }
    }

    private static final class SynchronizedArray {
        private final ReferenceQueue<Object> staleArrays = new ReferenceQueue<>();
        private final Map<ReceiverKey, ArrayStates> elementStates =
                new HashMap<>();

        private static final class ArrayStates {
            final ReceiverKey key;
            final Map<Integer, SynchronizedState> elements = new HashMap<>();

            ArrayStates(ReceiverKey key) {
                this.key = key;
            }
        }

        static SynchronizedArray forArray(Object array) {
            return SYNCHRONIZED_ARRAYS.get(array.getClass());
        }

        synchronized SynchronizedState stateFor(Object array, int index) {
            removeStaleArrays();
            ArrayStates states = elementStates.get(new ReceiverKey(array));
            if (states == null) {
                ReceiverKey key = new ReceiverKey(array, staleArrays);
                states = new ArrayStates(key);
                elementStates.put(key, states);
            }
            SynchronizedState state = states.elements.get(index);
            if (state == null) {
                state = new SynchronizedState(states.key, index);
                states.elements.put(index, state);
            }
            return state;
        }

        synchronized void discard(SynchronizedState state) {
            ArrayStates states = elementStates.get(state.key);
            if (states != null
                    && states.elements.remove(state.index, state)
                    && states.elements.isEmpty()) {
                elementStates.remove(state.key);
            }
        }

        private void removeStaleArrays() {
            ReceiverKey key;
            while ((key = (ReceiverKey) staleArrays.poll()) != null) {
                elementStates.remove(key);
            }
        }
    }

    private static final class ReceiverKey extends WeakReference<Object> {
        private final int hash;

        ReceiverKey(Object receiver) {
            super(receiver);
            hash = System.identityHashCode(receiver);
        }

        ReceiverKey(Object receiver, ReferenceQueue<Object> queue) {
            super(receiver, queue);
            hash = System.identityHashCode(receiver);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            Object receiver = get();
            return receiver != null && obj instanceof ReceiverKey key
                    && receiver == key.get();
        }
    }

    private static final class SynchronizedState {
        private final ReceiverKey key;
        private final int index;
        private Thread computingThread;

        SynchronizedState(ReceiverKey key) {
            this(key, -1);
        }

        SynchronizedState(ReceiverKey key, int index) {
            this.key = key;
            this.index = index;
        }
    }

    private static final class FieldUpdater {
        static final MethodType READ_TYPE = MethodType.methodType(Object.class, Object.class);
        static final MethodType EXCHANGE_TYPE = MethodType.methodType(Object.class,
                Object.class, Object.class, Object.class);
        static final MethodType WRITE_TYPE = MethodType.methodType(void.class,
                Object.class, Object.class);

        private final Mode mode;
        private final MethodHandle read;
        private final MethodHandle exchange;
        private final MethodHandle write;
        private final MethodHandle compute;
        private final Object defaultValue;
        private final SynchronizedField synchronizedField;

        FieldUpdater(Mode mode, FieldAccess access, Method method, Object computingFunction)
                throws IllegalAccessException {
            this.mode = mode;
            VarHandle field = access.handle();
            Class<?> valueType = field.varType();
            defaultValue = valueType.isPrimitive() ? Wrapper.forPrimitiveType(valueType).zero() : null;
            MethodHandle reader = field.toMethodHandle(readMode(mode));
            MethodHandle exchanger = mode == Mode.PUBLISH_ONCE
                    ? field.toMethodHandle(VarHandle.AccessMode.COMPARE_AND_EXCHANGE_RELEASE) : null;
            MethodHandle writer = mode == Mode.PUBLISH_ONCE
                    ? null : field.toMethodHandle(writeMode(mode));
            MethodHandle computation = SharedSecrets.getJavaLangInvokeAccess().unreflectMethod(method).bindTo(computingFunction);
            if (access.receiverType() == null) {
                computation = computation.asType(MethodType.methodType(valueType));
                reader = MethodHandles.dropArguments(reader, 0, Object.class);
                if (exchanger != null) {
                    exchanger = MethodHandles.dropArguments(exchanger, 0, Object.class);
                }
                if (writer != null) {
                    writer = MethodHandles.dropArguments(writer, 0, Object.class);
                }
                computation = MethodHandles.dropArguments(computation, 0, Object.class);
            } else {
                // Preserve the selector's receiver cast even when the erased
                // functional interface or the field owner has a broader type.
                Class<?> receiver = access.receiverType();
                reader = reader.asType(MethodType.methodType(Object.class, receiver));
                if (exchanger != null) {
                    exchanger = exchanger.asType(MethodType.methodType(Object.class,
                            receiver, Object.class, Object.class));
                }
                if (writer != null) {
                    writer = writer.asType(MethodType.methodType(void.class,
                            receiver, Object.class));
                }
                computation = computation.asType(MethodType.methodType(valueType, receiver));
            }
            read = reader.asType(READ_TYPE);
            exchange = exchanger == null ? null : exchanger.asType(EXCHANGE_TYPE);
            write = writer == null ? null : writer.asType(WRITE_TYPE);
            compute = computation.asType(READ_TYPE);
            synchronizedField = mode == Mode.SERIALIZED
                    ? SynchronizedField.forField(access.field(), access.receiverType() == null) : null;
        }

        Object get(Object receiver) throws Throwable {
            Object value = (Object) read.invokeExact(receiver);
            if (!isDefault(value)) {
                return value;
            }
            return switch (mode) {
                case PLAIN -> getPlain(receiver);
                case PUBLISH_ONCE -> getCas(receiver);
                case SERIALIZED -> getSynchronized(receiver);
            };
        }

        private Object getCas(Object receiver) throws Throwable {
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

        private Object getPlain(Object receiver) throws Throwable {
            Object candidate = (Object) compute.invokeExact(receiver);
            if (isDefault(candidate)) {
                throw new IllegalArgumentException("The computed value is reserved for uninitialized storage");
            }
            write.invokeExact(receiver, candidate);
            return candidate;
        }

        private Object getSynchronized(Object receiver) throws Throwable {
            SynchronizedState state = synchronizedField.stateFor(receiver);
            synchronized (state) {
                Object value = (Object) read.invokeExact(receiver);
                if (!isDefault(value)) {
                    return value;
                }
                if (state.computingThread == Thread.currentThread()) {
                    throw new IllegalStateException("Recursive computation of a lazy field");
                }
                state.computingThread = Thread.currentThread();
                Object candidate;
                try {
                    candidate = (Object) compute.invokeExact(receiver);
                    if (isDefault(candidate)) {
                        throw new IllegalArgumentException(
                                "The computed value is reserved for uninitialized storage");
                    }
                    write.invokeExact(receiver, candidate);
                } finally {
                    state.computingThread = null;
                }
                synchronizedField.discard(state);
                return candidate;
            }
        }

        private boolean isDefault(Object value) {
            // Float/Double.equals distinguish positive and negative zero,
            // matching the bitwise comparison used by the atomic exchange.
            return defaultValue == null ? value == null : defaultValue.equals(value);
        }
    }

    private static final class ArrayUpdater {
        static final MethodType READ1_TYPE = MethodType.methodType(Object.class,
                Object.class, int.class);
        static final MethodType READ2_TYPE = MethodType.methodType(Object.class,
                Object.class, int.class, int.class);
        private static final MethodType ARRAY_GETTER_TYPE = MethodType.methodType(Object.class,
                Object.class);
        private static final MethodType ELEMENT_READ_TYPE = MethodType.methodType(Object.class,
                Object.class, int.class);
        private static final MethodType ELEMENT_EXCHANGE_TYPE = MethodType.methodType(Object.class,
                Object.class, int.class, Object.class, Object.class);
        private static final MethodType ELEMENT_WRITE_TYPE = MethodType.methodType(void.class,
                Object.class, int.class, Object.class);

        private final Mode mode;
        private final MethodHandle arrayGetter;
        private final MethodHandle rowGetter;
        private final MethodHandle read;
        private final MethodHandle exchange;
        private final MethodHandle write;
        private final MethodHandle compute;
        private final Object defaultValue;
        private final boolean swapIndices;

        ArrayUpdater(Mode mode, ArrayAccess access, Method method, Object computingFunction)
                throws IllegalAccessException {
            this(mode, access.arrayGetter(), access.arrayType(), access.elementHandle(),
                    access.receiverType(), access.rank(),
                    access.rank() == 2 && access.indexOrder()[0] == 1,
                    method, computingFunction);
        }

        private ArrayUpdater(Mode mode, MethodHandle arrayGetter, Class<?> arrayType,
                             VarHandle element, Class<?> receiverType, int rank,
                             boolean swapIndices, Method method, Object computingFunction)
                throws IllegalAccessException {
            this.mode = mode;
            this.arrayGetter = arrayGetter.asType(ARRAY_GETTER_TYPE);
            rowGetter = rank == 2
                    ? MethodHandles.arrayElementGetter(arrayType).asType(ELEMENT_READ_TYPE)
                    : null;
            this.swapIndices = swapIndices;
            Class<?> valueType = element.varType();
            defaultValue = valueType.isPrimitive() ? Wrapper.forPrimitiveType(valueType).zero() : null;
            read = element.toMethodHandle(readMode(mode)).asType(ELEMENT_READ_TYPE);
            exchange = mode == Mode.PUBLISH_ONCE
                    ? element.toMethodHandle(VarHandle.AccessMode.COMPARE_AND_EXCHANGE_RELEASE)
                             .asType(ELEMENT_EXCHANGE_TYPE)
                    : null;
            write = mode == Mode.PUBLISH_ONCE
                    ? null : element.toMethodHandle(writeMode(mode)).asType(ELEMENT_WRITE_TYPE);

            MethodHandle computation = SharedSecrets.getJavaLangInvokeAccess()
                    .unreflectMethod(method).bindTo(computingFunction);
            MethodType computationType;
            if (receiverType == null) {
                computationType = rank == 1
                        ? MethodType.methodType(valueType, int.class)
                        : MethodType.methodType(valueType, int.class, int.class);
            } else {
                computationType = rank == 1
                        ? MethodType.methodType(valueType, receiverType, int.class)
                        : MethodType.methodType(valueType, receiverType, int.class, int.class);
            }
            computation = computation.asType(computationType);
            if (receiverType == null) {
                computation = MethodHandles.dropArguments(computation, 0, Object.class);
            }
            if (rank == 1) {
                computation = MethodHandles.dropArguments(computation.asType(READ1_TYPE),
                        2, int.class);
            } else {
                computation = computation.asType(READ2_TYPE);
            }
            compute = computation;
        }

        Object get1(Object receiver, int index) throws Throwable {
            Object array = (Object) arrayGetter.invokeExact(receiver);
            return getAt(array, index, receiver, index, 0);
        }

        Object get2(Object receiver, int index0, int index1) throws Throwable {
            Object outer = (Object) arrayGetter.invokeExact(receiver);
            int rowIndex = swapIndices ? index1 : index0;
            int elementIndex = swapIndices ? index0 : index1;
            Object row = (Object) rowGetter.invokeExact(outer, rowIndex);
            return getAt(row, elementIndex, receiver, index0, index1);
        }

        private Object getAt(Object array, int index, Object receiver,
                             int index0, int index1) throws Throwable {
            Object value = (Object) read.invokeExact(array, index);
            if (array instanceof Object[] references
                    && references.getClass().getComponentType().isValue()
                    && ValueClass.isNullRestrictedArray(references)) {
                throw new IllegalArgumentException(
                        "A null-restricted array cannot use null as the uninitialized value");
            }
            if (!isDefault(value)) {
                return value;
            }
            return switch (mode) {
                case PLAIN -> getPlain(array, index, receiver, index0, index1);
                case PUBLISH_ONCE -> getCas(array, index, receiver, index0, index1);
                case SERIALIZED -> getSynchronized(array, index, receiver, index0, index1);
            };
        }

        private Object getCas(Object array, int index, Object receiver,
                              int index0, int index1) throws Throwable {
            Object candidate = (Object) compute.invokeExact(receiver, index0, index1);
            checkCandidate(candidate);
            Object witness = (Object) exchange.invokeExact(array, index, defaultValue, candidate);
            if (isDefault(witness)) {
                return candidate;
            }
            // A failed release exchange needs an acquire read of the winner.
            return (Object) read.invokeExact(array, index);
        }

        private Object getPlain(Object array, int index, Object receiver,
                                int index0, int index1) throws Throwable {
            Object candidate = (Object) compute.invokeExact(receiver, index0, index1);
            checkCandidate(candidate);
            write.invokeExact(array, index, candidate);
            return candidate;
        }

        private Object getSynchronized(Object array, int index, Object receiver,
                                       int index0, int index1) throws Throwable {
            SynchronizedArray synchronizedArray = SynchronizedArray.forArray(array);
            SynchronizedState state = synchronizedArray.stateFor(array, index);
            synchronized (state) {
                Object value = (Object) read.invokeExact(array, index);
                if (!isDefault(value)) {
                    return value;
                }
                if (state.computingThread == Thread.currentThread()) {
                    throw new IllegalStateException("Recursive computation of a lazy array element");
                }
                state.computingThread = Thread.currentThread();
                Object candidate;
                try {
                    candidate = (Object) compute.invokeExact(receiver, index0, index1);
                    checkCandidate(candidate);
                    write.invokeExact(array, index, candidate);
                } finally {
                    state.computingThread = null;
                }
                synchronizedArray.discard(state);
                return candidate;
            }
        }

        private void checkCandidate(Object candidate) {
            if (isDefault(candidate)) {
                throw new IllegalArgumentException(
                        "The computed value is reserved for uninitialized storage");
            }
        }

        private boolean isDefault(Object value) {
            return defaultValue == null ? value == null : defaultValue.equals(value);
        }
    }
}
