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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.lang.invoke.Lazy.IntFieldAccessor;
import java.lang.invoke.Lazy.LongFieldAccessor;
import java.lang.invoke.Lazy.ReferenceArrayAccessor;
import java.lang.invoke.Lazy.ReferenceFieldAccessor;
import java.lang.invoke.Lazy.StaticReferenceFieldAccessor;
import java.lang.invoke.Lazy.Builder;
import java.lang.invoke.Lazy.Mode;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

import jdk.internal.access.SharedSecrets;
import jdk.internal.value.ValueClass;
import sun.invoke.util.Wrapper;
import sun.reflect.generics.reflectiveObjects.GenericArrayTypeImpl;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

/**
 * Implementation of lazy storage selection and updater creation.
 */
public final class LazyUpdaterSupport {
    private LazyUpdaterSupport() { }

    /**
     * Starts an updater builder while keeping its state and storage-selection
     * machinery outside the public API class.
     */
    public static <C> Builder<C> newBuilder(Class<C> computationShape) {
        return LazyBuilderImpl.initial(computationShape);
    }

    public static <T> T ofArray(Mode mode,
                                Class<T> type,
                                T computingFunction) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(type);
        Objects.requireNonNull(computingFunction);
        Method method = accessorMethod(type);
        Class<?> specializedArrayType = specializedReferenceArrayType(type,
                ReferenceArrayAccessor.class);
        requireConcreteCoordinates(type, method, 0, specializedArrayType);
        if (!type.isInstance(computingFunction)) {
            throw new IllegalArgumentException("Function must implement " + type.getTypeName());
        }
        try {
            StorageAccess access = selectArray(method.getParameterTypes(),
                    specializedArrayType);
            return createUpdater(mode, type, method, method, access,
                    computingFunction, false);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Cannot create the lazy array updater", e);
        }
    }

    public static <T> T ofArray(Mode mode, Class<T> type) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(type);
        Method accessor = accessorMethod(type);
        Class<?> specializedArrayType = specializedReferenceArrayType(type,
                ReferenceArrayAccessor.Computer.class);
        requireConcreteCoordinates(type, accessor, 1, specializedArrayType);
        Method computer = computerMethod(type, accessor);
        try {
            StorageAccess access = selectArray(accessorCoordinates(accessor),
                    specializedArrayType);
            return createUpdater(mode, type, accessor, computer, access,
                    null, true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Cannot create the lazy array updater", e);
        }
    }

    /**
     * Creates a declaration-site updater for a field selected through an
     * explicit owner class. Unlike the older string selector, this preserves
     * the identity of classes in different loaders, including hidden classes.
     * The owner may also be a public subclass through which an inherited
     * field is accessible.
     */
    public static <C> C ofField(Mode mode, MethodHandles.Lookup lookup,
                                Class<C> computationShape, Class<?> ownerClass,
                                String fieldName, C computingFunction) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(lookup);
        Objects.requireNonNull(computationShape);
        Objects.requireNonNull(ownerClass);
        Objects.requireNonNull(fieldName);
        Objects.requireNonNull(computingFunction);
        Method method = fieldShapeMethod(computationShape);
        if (!computationShape.isInstance(computingFunction)) {
            throw new IllegalArgumentException("Function must implement "
                    + computationShape.getTypeName());
        }
        boolean concreteGenericShape = computationShape.getTypeParameters().length == 0
                && !hasUnresolvedFieldAccessorArguments(computationShape);
        Class<?> specializedOwner = concreteGenericShape
                ? specializedFieldOwner(computationShape) : null;
        ReferenceValueType specializedValue = concreteGenericShape
                ? specializedReferenceFieldValue(computationShape) : null;
        try {
            lookup.accessClass(computationShape);
            StorageAccess access = selectField(mode, lookup, method.getParameterTypes(),
                    ownerClass, fieldName, specializedOwner);
            validateReferenceFieldValue(access, specializedValue);
            return createUpdater(mode, computationShape, method, method, access,
                    computingFunction, false);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Cannot access the lazy location or functional interface", e);
        }
    }

    /**
     * Creates a use-site field updater. The first parameter of the returned
     * accessor is a callback with the computation shape chosen at the start
     * of the builder chain. Generic functional interfaces may be used raw;
     * the explicit owner identifies the field but cannot restore erased Java
     * type arguments to a Class token.
     */
    public static <C, U> U ofFieldUseSite(Mode mode,
                                          MethodHandles.Lookup lookup,
                                          Class<C> computationShape,
                                          Class<U> useSiteShape,
                                          Class<?> ownerClass,
                                          String fieldName) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(lookup);
        Objects.requireNonNull(computationShape);
        Objects.requireNonNull(useSiteShape);
        Objects.requireNonNull(ownerClass);
        Objects.requireNonNull(fieldName);
        Method declaredComputer = fieldShapeMethod(computationShape);
        Method accessor = fieldShapeMethod(useSiteShape);
        Method callback = fieldComputerMethod(useSiteShape, accessor);
        requireSameComputerShape(declaredComputer, callback);
        boolean concreteGenericShape = useSiteShape.getTypeParameters().length == 0
                && !hasUnresolvedFieldAccessorArguments(useSiteShape);
        Class<?> specializedOwner = concreteGenericShape
                ? specializedFieldOwner(useSiteShape) : null;
        ReferenceValueType specializedValue = concreteGenericShape
                ? specializedReferenceFieldValue(useSiteShape) : null;
        try {
            lookup.accessClass(computationShape);
            lookup.accessClass(useSiteShape);
            lookup.accessClass(accessor.getParameterTypes()[0]);
            StorageAccess access = selectField(mode, lookup,
                    accessorCoordinates(accessor), ownerClass, fieldName,
                    specializedOwner);
            validateReferenceFieldValue(access, specializedValue);
            return createUpdater(mode, useSiteShape, accessor, callback, access,
                    null, true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Cannot access the lazy location or functional interface", e);
        }
    }

    /**
     * The use-site array accessor can identify a more specific reference
     * array type than the computation Class token (for example a concrete
     * subinterface of ReferenceArrayAccessor.Computer<String>).
     */
    public static <C, U> U ofArrayUseSite(Mode mode,
                                          Class<C> computationShape,
                                          Class<U> useSiteShape) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(computationShape);
        Objects.requireNonNull(useSiteShape);
        Method declaredComputer = fieldShapeMethod(computationShape);
        Method accessor = accessorMethod(useSiteShape);
        Method callback = computerMethod(useSiteShape, accessor);
        requireSameComputerShape(declaredComputer, callback);
        return ofArray(mode, useSiteShape);
    }

    private static Method fieldShapeMethod(Class<?> type) {
        Method method = LazyUpdaterFactory.functionalMethod(type);
        if (method.getTypeParameters().length != 0) {
            throw erasedTypeVariables(type, "declares a method type variable "
                    + "erased in its functional-method descriptor");
        }
        return method;
    }

    private static Method fieldComputerMethod(Class<?> type, Method accessor) {
        if (accessor.getParameterCount() == 0) {
            throw new IllegalArgumentException("A use-site updater requires a computing-function parameter");
        }
        Method computer = fieldShapeMethod(accessor.getParameterTypes()[0]);
        Class<?>[] coordinates = accessorCoordinates(accessor);
        Class<?>[] accepted = computer.getParameterTypes();
        if (accepted.length != coordinates.length) {
            throw new IllegalArgumentException("Computing function coordinates do not match the updater");
        }
        for (int i = 0; i < coordinates.length; i++) {
            if (!accepted[i].isAssignableFrom(coordinates[i])) {
                throw new IllegalArgumentException(
                        "Computing function coordinate " + i + " does not accept the updater coordinate");
            }
        }
        return computer;
    }

    private static void requireSameComputerShape(Method expected, Method actual) {
        if (expected.getReturnType() != actual.getReturnType()
                || !Arrays.equals(expected.getParameterTypes(), actual.getParameterTypes())) {
            throw new IllegalArgumentException("Use-site computing function does not match the builder's computation shape");
        }
    }

    private static Method accessorMethod(Class<?> type) {
        Method method = LazyUpdaterFactory.functionalMethod(type);
        if (type.getTypeParameters().length != 0) {
            throw erasedTypeVariables(type, "declares a type variable whose "
                    + "argument is erased from the supplied Class token");
        }
        if (method.getTypeParameters().length != 0) {
            throw erasedTypeVariables(type, "declares a method type variable "
                    + "erased in its functional-method descriptor");
        }
        if (hasErasedFunctionalMethodTypes(type, method)) {
            throw erasedTypeVariables(type, "inherits a type variable erased "
                    + "in its functional method");
        }
        return method;
    }

    // An unrelated raw generic parent does not erase the functional method.
    // Resolve only the type variables that occur in that method's signature.
    static boolean hasErasedFunctionalMethodTypes(Class<?> type, Method method) {
        if (hasErasedMethodType(type, method.getGenericReturnType())) {
            return true;
        }
        for (Type parameter : method.getGenericParameterTypes()) {
            if (hasErasedMethodType(type, parameter)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasErasedMethodType(Class<?> type, Type methodType) {
        if (methodType instanceof TypeVariable<?> variable) {
            if (variable.getGenericDeclaration() instanceof Class<?> declaringClass) {
                TypeVariable<?>[] variables = declaringClass.getTypeParameters();
                for (int i = 0; i < variables.length; i++) {
                    if (variables[i].equals(variable)) {
                        Type resolved = typeArgument(type, declaringClass, i,
                                new HashMap<>());
                        return resolved == null || hasUnresolvedTypeVariables(resolved);
                    }
                }
            }
            return true;
        }
        if (methodType instanceof ParameterizedType parameterized) {
            for (Type argument : parameterized.getActualTypeArguments()) {
                if (hasErasedMethodType(type, argument)) {
                    return true;
                }
            }
            Type owner = parameterized.getOwnerType();
            return owner != null && hasErasedMethodType(type, owner);
        }
        if (methodType instanceof GenericArrayType array) {
            return hasErasedMethodType(type, array.getGenericComponentType());
        }
        if (methodType instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) {
                if (hasErasedMethodType(type, bound)) {
                    return true;
                }
            }
            for (Type bound : wildcard.getLowerBounds()) {
                if (hasErasedMethodType(type, bound)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static IllegalArgumentException erasedTypeVariables(Class<?> type,
                                                                String problem) {
        String remedy;
        if (IntFieldAccessor.Computer.class.isAssignableFrom(type)) {
            remedy = "Declare a concrete subinterface, for example "
                    + "interface HolderAccessor extends "
                    + "IntFieldAccessor.Computer<Holder> {}, "
                    + "and pass HolderAccessor.class.";
        } else if (IntFieldAccessor.class.isAssignableFrom(type)) {
            remedy = "Declare a concrete subinterface, for example "
                    + "interface HolderAccessor extends IntFieldAccessor<Holder> {}, "
                    + "and pass HolderAccessor.class.";
        } else if (LongFieldAccessor.Computer.class.isAssignableFrom(type)) {
            remedy = "Declare a concrete subinterface, for example "
                    + "interface HolderAccessor extends "
                    + "LongFieldAccessor.Computer<Holder> {}, "
                    + "and pass HolderAccessor.class.";
        } else if (LongFieldAccessor.class.isAssignableFrom(type)) {
            remedy = "Declare a concrete subinterface, for example "
                    + "interface HolderAccessor extends LongFieldAccessor<Holder> {}, "
                    + "and pass HolderAccessor.class.";
        } else if (ReferenceArrayAccessor.Computer.class.isAssignableFrom(type)) {
            remedy = "Declare a concrete subinterface, for example "
                    + "interface Names extends ReferenceArrayAccessor.Computer<String> {}, "
                    + "and pass Names.class.";
        } else if (ReferenceArrayAccessor.class.isAssignableFrom(type)) {
            remedy = "Declare a concrete subinterface, for example "
                    + "interface Names extends ReferenceArrayAccessor<String> {}, "
                    + "and pass Names.class.";
        } else if (StaticReferenceFieldAccessor.Computer.class.isAssignableFrom(type)) {
            remedy = "Declare a concrete subinterface, for example "
                    + "interface NameAccessor extends "
                    + "StaticReferenceFieldAccessor.Computer<String> {}, "
                    + "and pass NameAccessor.class.";
        } else if (StaticReferenceFieldAccessor.class.isAssignableFrom(type)) {
            remedy = "Declare a concrete subinterface, for example "
                    + "interface NameAccessor extends StaticReferenceFieldAccessor<String> {}, "
                    + "and pass NameAccessor.class.";
        } else if (ReferenceFieldAccessor.Computer.class.isAssignableFrom(type)) {
            remedy = "Declare a concrete subinterface, for example "
                    + "interface NameAccessor extends "
                    + "ReferenceFieldAccessor.Computer<Holder, String> {}, "
                    + "and pass NameAccessor.class.";
        } else if (ReferenceFieldAccessor.class.isAssignableFrom(type)) {
            remedy = "Declare a concrete subinterface, for example "
                    + "interface NameAccessor extends ReferenceFieldAccessor<Holder, String> {}, "
                    + "and pass NameAccessor.class.";
        } else {
            remedy = "Declare a non-generic interface with its own functional "
                    + "method using concrete parameter and return types, and "
                    + "pass that interface's .class token.";
        }
        return new IllegalArgumentException("Functional interface "
                + type.getTypeName() + " " + problem + ". " + remedy);
    }

    private static void requireConcreteCoordinates(Class<?> type, Method method,
                                                   int firstCoordinate,
                                                   Class<?> specializedCoordinate) {
        Type[] genericTypes = method.getGenericParameterTypes();
        for (int i = firstCoordinate; i < genericTypes.length; i++) {
            if (specializedCoordinate != null && i == firstCoordinate) {
                continue;
            }
            if (genericTypes[i] instanceof TypeVariable<?>
                    || genericTypes[i] instanceof GenericArrayType) {
                throw new IllegalArgumentException("Functional interface "
                        + type.getTypeName() + " has a storage coordinate "
                        + "whose type variable is erased in the functional "
                        + "method. Declare a non-generic interface with its "
                        + "own method using concrete parameter types, and "
                        + "pass that interface's .class token.");
            }
        }
    }

    private static Method computerMethod(Class<?> type, Method accessor) {
        if (accessor.getParameterCount() == 0) {
            throw new IllegalArgumentException("A use-site updater requires a computing-function parameter");
        }
        Type callbackType = accessor.getGenericParameterTypes()[0];
        if (callbackType instanceof TypeVariable<?>
                || callbackType instanceof GenericArrayType) {
            throw new IllegalArgumentException("Functional interface "
                    + type.getTypeName() + " inherits a computing-function "
                    + "parameter whose type variable is erased in the "
                    + "functional method. Declare a non-generic interface "
                    + "with its own method using concrete parameter and "
                    + "return types.");
        }
        if (callbackType instanceof Class<?> raw
                && (raw.getTypeParameters().length != 0
                    || hasErasedFunctionalMethodTypes(raw,
                            LazyUpdaterFactory.functionalMethod(raw)))) {
            throw new IllegalArgumentException("Computing-function interface "
                    + raw.getTypeName() + " contains a type variable erased "
                    + "in the accessor method. Use a parameterized callback "
                    + "type or declare a non-generic interface with concrete "
                    + "parameter and return types.");
        }
        Method computer = LazyUpdaterFactory.functionalMethod(accessor.getParameterTypes()[0]);
        if (computer.getTypeParameters().length != 0) {
            throw new IllegalArgumentException("Computing-function interface "
                    + computer.getDeclaringClass().getTypeName()
                    + " has a method type variable erased in the accessor. "
                    + "Declare a non-generic callback interface with concrete "
                    + "parameter and return types.");
        }
        Class<?>[] coordinates = accessorCoordinates(accessor);
        Class<?>[] accepted = computer.getParameterTypes();
        if (accepted.length != coordinates.length) {
            throw new IllegalArgumentException("Computing function coordinates do not match the updater");
        }
        for (int i = 0; i < coordinates.length; i++) {
            if (!accepted[i].isAssignableFrom(coordinates[i])) {
                throw new IllegalArgumentException(
                        "Computing function coordinate " + i + " does not accept the updater coordinate");
            }
        }
        return computer;
    }

    private static Class<?>[] accessorCoordinates(Method accessor) {
        return Arrays.copyOfRange(accessor.getParameterTypes(), 1,
                accessor.getParameterCount());
    }

    private static Class<?> specializedFieldOwner(Class<?> type) {
        Class<?> base = instanceFieldAccessorBase(type);
        if (base == null) {
            return null;
        }
        Type argument = typeArgument(type, base, 0, new HashMap<>());
        Class<?> owner = argument instanceof Class<?> c ? c
                : argument instanceof ParameterizedType p
                        && p.getRawType() instanceof Class<?> c ? c : null;
        if (owner == null || owner.isPrimitive() || owner.isArray()) {
            throw new IllegalArgumentException(
                    "A field accessor requires a concrete receiver class: "
                            + type.getTypeName());
        }
        return owner;
    }

    private static boolean hasUnresolvedFieldAccessorArguments(Class<?> type) {
        Class<?> instanceBase = instanceFieldAccessorBase(type);
        if (instanceBase != null) {
            if (hasUnresolvedTypeVariables(typeArgument(type,
                    instanceBase, 0, new HashMap<>()))) {
                return true;
            }
            return (instanceBase == ReferenceFieldAccessor.class
                    || instanceBase == ReferenceFieldAccessor.Computer.class)
                    && hasUnresolvedTypeVariables(typeArgument(type,
                            instanceBase, 1, new HashMap<>()));
        }
        Class<?> staticReferenceBase = staticReferenceFieldAccessorBase(type);
        if (staticReferenceBase != null) {
            return hasUnresolvedTypeVariables(typeArgument(type,
                    staticReferenceBase, 0, new HashMap<>()));
        }
        return false;
    }

    private static Class<?> instanceFieldAccessorBase(Class<?> type) {
        if (IntFieldAccessor.Computer.class.isAssignableFrom(type)) {
            return IntFieldAccessor.Computer.class;
        }
        if (IntFieldAccessor.class.isAssignableFrom(type)) {
            return IntFieldAccessor.class;
        }
        if (LongFieldAccessor.Computer.class.isAssignableFrom(type)) {
            return LongFieldAccessor.Computer.class;
        }
        if (LongFieldAccessor.class.isAssignableFrom(type)) {
            return LongFieldAccessor.class;
        }
        return referenceFieldAccessorBase(type);
    }

    private static Class<?> referenceFieldAccessorBase(Class<?> type) {
        if (ReferenceFieldAccessor.Computer.class.isAssignableFrom(type)) {
            return ReferenceFieldAccessor.Computer.class;
        }
        if (ReferenceFieldAccessor.class.isAssignableFrom(type)) {
            return ReferenceFieldAccessor.class;
        }
        return null;
    }

    private static Class<?> staticReferenceFieldAccessorBase(Class<?> type) {
        if (StaticReferenceFieldAccessor.class.isAssignableFrom(type)) {
            return StaticReferenceFieldAccessor.class;
        }
        if (StaticReferenceFieldAccessor.Computer.class.isAssignableFrom(type)) {
            return StaticReferenceFieldAccessor.Computer.class;
        }
        return null;
    }

    private record ReferenceValueType(Class<?> erasedType, Type genericType) { }

    private static ReferenceValueType specializedReferenceFieldValue(Class<?> type) {
        Class<?> base;
        int argumentIndex;
        if ((base = staticReferenceFieldAccessorBase(type)) != null) {
            argumentIndex = 0;
        } else if ((base = referenceFieldAccessorBase(type)) != null) {
            argumentIndex = 1;
        } else {
            return null;
        }
        Type genericValue = typeArgument(type, base, argumentIndex,
                new HashMap<>());
        Class<?> value = rawReferenceType(genericValue);
        if (value == null || hasUnresolvedTypeVariables(genericValue)) {
            throw new IllegalArgumentException("A reference field accessor requires a "
                    + "concrete reference value type: " + type.getTypeName());
        }
        return new ReferenceValueType(value, genericValue);
    }

    private static void validateReferenceFieldValue(StorageAccess access,
                                                    ReferenceValueType valueType) {
        if (valueType == null) {
            return;
        }
        Class<?> storageType = access.valueHandle().varType();
        if (!(access instanceof FieldAccess) || storageType.isPrimitive()
                || !valueType.erasedType().isAssignableFrom(storageType)) {
            throw new IllegalArgumentException("Reference field accessor value type "
                    + valueType.genericType().getTypeName()
                    + " cannot represent lazy storage type "
                    + storageType.getTypeName());
        }
        Type genericType = valueType.genericType();
        if (!(genericType instanceof Class<?>)
                && !genericType.equals(((FieldAccess) access).field().getGenericType())) {
            throw new IllegalArgumentException("Reference field accessor value type "
                    + genericType.getTypeName()
                    + " does not match the field's declared generic type "
                    + ((FieldAccess) access).field().getGenericType().getTypeName());
        }
    }

    private static Class<?> specializedReferenceArrayType(Class<?> type,
                                                          Class<?> accessorType) {
        if (!accessorType.isAssignableFrom(type)) {
            return null;
        }
        Class<?> elementType = specializedReferenceType(type,
                accessorType, 0);
        if (elementType == null) {
            throw new IllegalArgumentException("A reference array accessor requires a "
                    + "concrete reference element type: " + type.getTypeName());
        }
        return elementType.arrayType();
    }

    private static Class<?> specializedReferenceType(Class<?> type,
                                                      Class<?> base,
                                                      int argumentIndex) {
        Type argument = typeArgument(type, base, argumentIndex, new HashMap<>());
        return hasUnresolvedTypeVariables(argument) ? null
                : rawReferenceType(argument);
    }

    private static Class<?> rawReferenceType(Type type) {
        Class<?> result = type instanceof Class<?> c ? c
                : type instanceof ParameterizedType p
                        && p.getRawType() instanceof Class<?> c ? c : null;
        if (result == null && type instanceof GenericArrayType array) {
            Class<?> component = rawReferenceType(array.getGenericComponentType());
            result = component == null ? null : component.arrayType();
        }
        return result != null && !result.isPrimitive() ? result : null;
    }

    private static boolean hasUnresolvedTypeVariables(Type type) {
        if (type instanceof TypeVariable<?>) {
            return true;
        }
        if (type instanceof ParameterizedType parameterized) {
            for (Type argument : parameterized.getActualTypeArguments()) {
                if (hasUnresolvedTypeVariables(argument)) {
                    return true;
                }
            }
            return parameterized.getOwnerType() != null
                    && hasUnresolvedTypeVariables(parameterized.getOwnerType());
        }
        if (type instanceof GenericArrayType array) {
            return hasUnresolvedTypeVariables(array.getGenericComponentType());
        }
        if (type instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) {
                if (hasUnresolvedTypeVariables(bound)) {
                    return true;
                }
            }
            for (Type bound : wildcard.getLowerBounds()) {
                if (hasUnresolvedTypeVariables(bound)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Type typeArgument(Type type, Class<?> target, int argumentIndex,
                                     Map<TypeVariable<?>, Type> substitutions) {
        Class<?> raw;
        Map<TypeVariable<?>, Type> resolved = new HashMap<>(substitutions);
        if (type instanceof ParameterizedType parameterized) {
            raw = (Class<?>) parameterized.getRawType();
            TypeVariable<?>[] variables = raw.getTypeParameters();
            Type[] arguments = parameterized.getActualTypeArguments();
            for (int i = 0; i < variables.length; i++) {
                resolved.put(variables[i], substitute(arguments[i], substitutions));
            }
        } else if (type instanceof Class<?> c) {
            raw = c;
        } else {
            return null;
        }
        if (raw == target) {
            return substitute(raw.getTypeParameters()[argumentIndex], resolved);
        }
        for (Type parent : raw.getGenericInterfaces()) {
            Class<?> parentRaw = parent instanceof ParameterizedType p
                    ? (Class<?>) p.getRawType() : (Class<?>) parent;
            if (target.isAssignableFrom(parentRaw)) {
                Type result = typeArgument(parent, target, argumentIndex, resolved);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static Type substitute(Type type,
                                    Map<TypeVariable<?>, Type> substitutions) {
        Set<Type> seen = new HashSet<>();
        while (type instanceof TypeVariable<?> variable
                && substitutions.containsKey(variable)
                && seen.add(type)) {
            type = substitutions.get(variable);
        }
        if (type instanceof ParameterizedType parameterized) {
            Type[] arguments = parameterized.getActualTypeArguments();
            boolean changed = false;
            for (int i = 0; i < arguments.length; i++) {
                Type resolved = substitute(arguments[i], substitutions);
                changed |= resolved != arguments[i];
                arguments[i] = resolved;
            }
            Type owner = parameterized.getOwnerType();
            Type resolvedOwner = owner == null ? null : substitute(owner, substitutions);
            if (changed || resolvedOwner != owner) {
                return ParameterizedTypeImpl.make((Class<?>) parameterized.getRawType(),
                        arguments, resolvedOwner);
            }
        } else if (type instanceof GenericArrayType array) {
            Type component = array.getGenericComponentType();
            Type resolved = substitute(component, substitutions);
            if (resolved != component) {
                return resolved instanceof Class<?> c ? c.arrayType()
                        : GenericArrayTypeImpl.make(resolved);
            }
        } else if (type instanceof WildcardType wildcard) {
            Type[] upper = wildcard.getUpperBounds();
            Type[] lower = wildcard.getLowerBounds();
            boolean changed = false;
            for (int i = 0; i < upper.length; i++) {
                Type resolved = substitute(upper[i], substitutions);
                changed |= resolved != upper[i];
                upper[i] = resolved;
            }
            for (int i = 0; i < lower.length; i++) {
                Type resolved = substitute(lower[i], substitutions);
                changed |= resolved != lower[i];
                lower[i] = resolved;
            }
            if (changed) {
                return new ResolvedWildcardType(upper, lower);
            }
        }
        return type;
    }

    private static final class ResolvedWildcardType implements WildcardType {
        private final Type[] upper;
        private final Type[] lower;

        ResolvedWildcardType(Type[] upper, Type[] lower) {
            this.upper = upper.clone();
            this.lower = lower.clone();
        }

        @Override
        public Type[] getUpperBounds() {
            return upper.clone();
        }

        @Override
        public Type[] getLowerBounds() {
            return lower.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof WildcardType wildcard
                    && Arrays.equals(upper, wildcard.getUpperBounds())
                    && Arrays.equals(lower, wildcard.getLowerBounds());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(upper) ^ Arrays.hashCode(lower);
        }

        @Override
        public String toString() {
            if (lower.length > 0) {
                return "? super " + boundsToString(lower);
            }
            if (upper.length == 1 && upper[0] == Object.class) {
                return "?";
            }
            return "? extends " + boundsToString(upper);
        }

        private static String boundsToString(Type[] bounds) {
            StringJoiner names = new StringJoiner(" & ");
            for (Type bound : bounds) {
                names.add(bound.getTypeName());
            }
            return names.toString();
        }
    }

    private sealed interface StorageAccess permits FieldAccess, ArrayAccess {
        VarHandle valueHandle();
        Class<?> receiverType();
    }

    private record FieldAccess(VarHandle valueHandle, Field field,
                               Class<?> receiverType) implements StorageAccess { }

    private record ArrayAccess(VarHandle valueHandle, MethodHandle arrayGetter,
                               Class<?> receiverType, Class<?> arrayType,
                               int rank) implements StorageAccess { }

    private static StorageAccess selectField(Mode mode,
                                             MethodHandles.Lookup lookup,
                                             Class<?>[] coordinates,
                                             Class<?> ownerClass,
                                             String fieldName)
            throws ReflectiveOperationException {
        return selectField(mode, lookup, coordinates, ownerClass, fieldName, null);
    }

    private static StorageAccess selectField(Mode mode,
                                             MethodHandles.Lookup lookup,
                                             Class<?>[] coordinates,
                                             Class<?> ownerClass,
                                             String fieldName,
                                             Class<?> specializedReceiver)
            throws ReflectiveOperationException {
        if (fieldName.isEmpty() || fieldName.indexOf('.') >= 0) {
            throw new IllegalArgumentException("Expected a simple field name: " + fieldName);
        }
        lookup.accessClass(ownerClass);
        Field field = findField(ownerClass, fieldName);
        Class<?> receiverType = null;
        if (!Modifier.isStatic(field.getModifiers())) {
            if (coordinates.length == 0 || coordinates[0].isPrimitive()) {
                throw new IllegalArgumentException("An instance field requires a reference receiver");
            }
            receiverType = coordinates[0];
            if (!receiverType.isAssignableFrom(ownerClass)
                    && !ownerClass.isAssignableFrom(receiverType)) {
                throw new IllegalArgumentException("The accessor receiver does not match the field owner");
            }
        }
        return fieldAccess(mode, lookup, coordinates, ownerClass, fieldName, field,
                receiverType, specializedReceiver);
    }

    private static StorageAccess fieldAccess(Mode mode,
                                             MethodHandles.Lookup lookup,
                                             Class<?>[] coordinates,
                                             Class<?> owner,
                                             String fieldName,
                                             Field field,
                                             Class<?> receiverType,
                                             Class<?> specializedReceiver)
            throws ReflectiveOperationException {
        boolean isStatic = Modifier.isStatic(field.getModifiers());
        // Preserve access through the supplied owner. An inherited public
        // field may be accessible through a public subclass even when its
        // declaring superclass is not accessible to the lookup.
        VarHandle fieldHandle = isStatic
                ? lookup.findStaticVarHandle(owner, fieldName, field.getType())
                : lookup.findVarHandle(owner, fieldName, field.getType());
        if (receiverType != null) {
            Class<?> handleReceiver = fieldHandle.coordinateTypes().getFirst();
            Class<?> checkedReceiver = specializedReceiver == null
                    ? receiverType : specializedReceiver;
            if ((specializedReceiver != null || checkedReceiver != Object.class)
                    && !handleReceiver.isAssignableFrom(checkedReceiver)) {
                throw new IllegalArgumentException(
                        "The accessor receiver is broader than the field handle's receiver");
            }
        }
        int rank = coordinates.length - (isStatic ? 0 : 1);
        if (rank == 0) {
            return new FieldAccess(fieldHandle, field, receiverType);
        }
        // The field supplies an array reference; the selected storage is an
        // element, so the field itself may be final. Coordinated modes must
        // acquire a release-published array before traversing its elements.
        lookup.accessClass(field.getType());
        return arrayAccess(fieldHandle.toMethodHandle(readMode(mode)),
                receiverType, field.getType(), rank);
    }

    private static Field findField(Class<?> owner, String name)
            throws NoSuchFieldException {
        Field declared = declaredField(owner, name);
        if (declared != null) {
            return declared;
        }

        Field inheritedClassField = null;
        for (Class<?> current = owner.getSuperclass(); current != null;
             current = current.getSuperclass()) {
            inheritedClassField = declaredField(current, name);
            if (inheritedClassField != null) {
                break;
            }
        }

        Set<Field> interfaceFields = new HashSet<>();
        Set<Class<?>> visited = new HashSet<>();
        for (Class<?> current = owner; current != null;
             current = current.getSuperclass()) {
            for (Class<?> iface : current.getInterfaces()) {
                collectInterfaceFields(iface, name, visited, interfaceFields);
            }
        }
        if (inheritedClassField != null && interfaceFields.isEmpty()) {
            return inheritedClassField;
        }
        if (inheritedClassField == null && interfaceFields.size() == 1) {
            return interfaceFields.iterator().next();
        }
        if (inheritedClassField != null || !interfaceFields.isEmpty()) {
            throw new IllegalArgumentException(
                    "Ambiguous inherited lazy field: " + owner.getTypeName() + "." + name);
        }
        throw new NoSuchFieldException(owner.getTypeName() + "." + name);
    }

    private static Field declaredField(Class<?> owner, String name) {
        Field selected = null;
        for (Field field : owner.getDeclaredFields()) {
            if (field.getName().equals(name)) {
                if (selected != null) {
                    throw new IllegalArgumentException(
                            "Ambiguous declared lazy field: " + owner.getTypeName() + "." + name);
                }
                selected = field;
            }
        }
        return selected;
    }

    private static void collectInterfaceFields(Class<?> iface, String name,
                                               Set<Class<?>> visited,
                                               Set<Field> fields) {
        if (!visited.add(iface)) {
            return;
        }
        Field declared = declaredField(iface, name);
        if (declared != null) {
            fields.add(declared);
            return;
        }
        for (Class<?> parent : iface.getInterfaces()) {
            collectInterfaceFields(parent, name, visited, fields);
        }
    }

    private static StorageAccess selectArray(Class<?>[] coordinates,
                                             Class<?> specializedArrayType) {
        if (coordinates.length < 2 || !coordinates[0].isArray()) {
            throw new IllegalArgumentException(
                    "An array updater requires an array receiver and at least one int index");
        }
        Class<?> receiverType = coordinates[0];
        Class<?> arrayType = specializedArrayType == null
                ? receiverType : specializedArrayType;
        if (!receiverType.isAssignableFrom(arrayType)) {
            throw new IllegalArgumentException("Specialized array type does not match the "
                    + "functional method's array coordinate");
        }
        return arrayAccess(MethodHandles.identity(arrayType), receiverType,
                arrayType, coordinates.length - 1);
    }

    private static ArrayAccess arrayAccess(MethodHandle getter,
                                           Class<?> receiverType,
                                           Class<?> arrayType,
                                           int rank) {
        if (rank < 1) {
            throw new IllegalArgumentException("An array updater requires at least one index");
        }
        Class<?> leafArrayType = arrayType;
        for (int i = 1; i < rank; i++) {
            if (leafArrayType == null || !leafArrayType.isArray()) {
                throw new IllegalArgumentException("Array rank is smaller than the number of indices");
            }
            leafArrayType = leafArrayType.getComponentType();
        }
        if (leafArrayType == null || !leafArrayType.isArray()) {
            throw new IllegalArgumentException("Array rank is smaller than the number of indices");
        }
        MethodHandle arrayGetter = receiverType == null
                ? MethodHandles.dropArguments(getter, 0, Object.class)
                : getter.asType(MethodType.methodType(arrayType, receiverType));
        return new ArrayAccess(MethodHandles.arrayElementVarHandle(leafArrayType),
                arrayGetter.asType(MethodType.methodType(Object.class, Object.class)),
                receiverType, arrayType, rank);
    }

    private static <T> T createUpdater(Mode mode,
                                       Class<T> type,
                                       Method method,
                                       Method computerMethod,
                                       StorageAccess access,
                                       Object boundComputer,
                                       boolean useSite)
            throws ReflectiveOperationException {
        VarHandle storage = access.valueHandle();
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
        Class<?>[] coordinates = useSite
                ? accessorCoordinates(method) : method.getParameterTypes();
        int expectedParameters = access instanceof ArrayAccess array
                ? array.rank() + (hasReceiver ? 1 : 0) : hasReceiver ? 1 : 0;
        if (coordinates.length != expectedParameters
                || method.getReturnType() == void.class
                || computerMethod.getReturnType() == void.class
                || (hasReceiver && coordinates[0] != access.receiverType())) {
            throw new IllegalArgumentException("Functional method does not match the selected storage");
        }
        if (access instanceof ArrayAccess) {
            for (int i = hasReceiver ? 1 : 0; i < expectedParameters; i++) {
                if (coordinates[i] != int.class) {
                    throw new IllegalArgumentException("Array coordinates must be int parameters");
                }
            }
        }
        Class<?> valueType = storage.varType();
        Class<?> boxedType = valueType.isPrimitive()
                ? Wrapper.forPrimitiveType(valueType).wrapperType() : valueType;
        Class<?> resultType = method.getReturnType();
        Class<?> computerResultType = computerMethod.getReturnType();
        if ((resultType != valueType
                && !resultType.isAssignableFrom(boxedType))
                || !compatibleComputerResult(computerResultType, valueType, boxedType)) {
            throw new IllegalArgumentException("Functional result does not match the storage type");
        }
        if (access instanceof FieldAccess && hasReceiver) {
            Class<?> coordinate = storage.coordinateTypes().getFirst();
            if (!coordinate.isAssignableFrom(access.receiverType())
                    && !access.receiverType().isAssignableFrom(coordinate)) {
                throw new IllegalArgumentException("Lookup receiver restriction does not match the updater");
            }
        }

        MethodHandle target;
        if (access instanceof ArrayAccess array) {
            ArrayUpdater updater = new ArrayUpdater(mode, array, computerMethod,
                    boundComputer);
            MethodType readType = switch (array.rank()) {
                case 1 -> useSite ? ArrayUpdater.READ1_WITH_COMPUTER_TYPE
                        : ArrayUpdater.READ1_TYPE;
                case 2 -> useSite ? ArrayUpdater.READ2_WITH_COMPUTER_TYPE
                        : ArrayUpdater.READ2_TYPE;
                default -> useSite ? ArrayUpdater.READ_N_WITH_COMPUTER_TYPE
                        : ArrayUpdater.READ_N_TYPE;
            };
            String methodName = switch (array.rank()) {
                case 1 -> useSite ? "get1WithComputer" : "get1";
                case 2 -> useSite ? "get2WithComputer" : "get2";
                default -> useSite ? "getNWithComputer" : "getN";
            };
            target = MethodHandles.lookup().findVirtual(ArrayUpdater.class,
                    methodName, readType).bindTo(updater);
        } else {
            FieldUpdater updater = new FieldUpdater(mode, (FieldAccess) access,
                    computerMethod, boundComputer);
            target = MethodHandles.lookup().findVirtual(FieldUpdater.class,
                    useSite ? "getWithComputer" : "get",
                    useSite ? FieldUpdater.READ_WITH_COMPUTER_TYPE
                            : FieldUpdater.READ_TYPE).bindTo(updater);
        }
        if (!hasReceiver) {
            target = MethodHandles.insertArguments(target, useSite ? 1 : 0,
                    (Object) null);
        }
        if (access instanceof ArrayAccess array && array.rank() > 2) {
            target = target.asCollector(int[].class, array.rank());
        }
        target = target.asType(MethodType.methodType(method.getReturnType(),
                method.getParameterTypes()));
        return LazyUpdaterFactory.create(type, method, target);
    }

    private static boolean compatibleComputerResult(Class<?> resultType,
                                                    Class<?> valueType,
                                                    Class<?> boxedType) {
        if (resultType == valueType || resultType.isAssignableFrom(boxedType)) {
            return true;
        }
        if (!valueType.isPrimitive()) {
            Class<?> boxedResult = resultType.isPrimitive()
                    ? Wrapper.forPrimitiveType(resultType).wrapperType() : resultType;
            return valueType.isAssignableFrom(boxedResult);
        }
        Wrapper target = Wrapper.forPrimitiveType(valueType);
        if (resultType.isPrimitive()) {
            return target.isConvertibleFrom(Wrapper.forPrimitiveType(resultType));
        }
        return Wrapper.isWrapperType(resultType)
                && target.isConvertibleFrom(Wrapper.forWrapperType(resultType));
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
        static final MethodType READ_WITH_COMPUTER_TYPE = MethodType.methodType(Object.class,
                Object.class, Object.class);
        static final MethodType EXCHANGE_TYPE = MethodType.methodType(Object.class,
                Object.class, Object.class, Object.class);
        static final MethodType WRITE_TYPE = MethodType.methodType(void.class,
                Object.class, Object.class);

        private final Mode mode;
        private final MethodHandle read;
        private final MethodHandle exchange;
        private final MethodHandle write;
        private final MethodHandle compute;
        private final Object boundComputer;
        private final Object defaultValue;
        private final SynchronizedField synchronizedField;

        FieldUpdater(Mode mode, FieldAccess access, Method computerMethod,
                     Object boundComputer)
                throws IllegalAccessException {
            this.mode = mode;
            this.boundComputer = boundComputer;
            VarHandle field = access.valueHandle();
            Class<?> valueType = field.varType();
            defaultValue = valueType.isPrimitive() ? Wrapper.forPrimitiveType(valueType).zero() : null;
            MethodHandle reader = field.toMethodHandle(readMode(mode));
            MethodHandle exchanger = mode == Mode.PUBLISH_ONCE
                    ? field.toMethodHandle(VarHandle.AccessMode.COMPARE_AND_EXCHANGE_RELEASE) : null;
            MethodHandle writer = mode == Mode.PUBLISH_ONCE
                    ? null : field.toMethodHandle(writeMode(mode));
            MethodHandle computation = SharedSecrets.getJavaLangInvokeAccess()
                    .unreflectMethod(computerMethod);
            if (access.receiverType() == null) {
                reader = MethodHandles.dropArguments(reader, 0, Object.class);
                if (exchanger != null) {
                    exchanger = MethodHandles.dropArguments(exchanger, 0, Object.class);
                }
                if (writer != null) {
                    writer = MethodHandles.dropArguments(writer, 0, Object.class);
                }
                computation = MethodHandles.dropArguments(computation, 1, Object.class);
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
            }
            read = reader.asType(READ_TYPE);
            exchange = exchanger == null ? null : exchanger.asType(EXCHANGE_TYPE);
            write = writer == null ? null : writer.asType(WRITE_TYPE);
            // Normalize to the selected storage type before checking the
            // reserved value, including for broad reference results.
            computation = computation.asType(MethodType.methodType(valueType,
                    Object.class, Object.class));
            compute = computation.asType(READ_WITH_COMPUTER_TYPE);
            synchronizedField = mode == Mode.SERIALIZED
                    ? SynchronizedField.forField(access.field(), access.receiverType() == null) : null;
        }

        Object get(Object receiver) throws Throwable {
            return getValue(boundComputer, receiver);
        }

        Object getWithComputer(Object computer, Object receiver) throws Throwable {
            Objects.requireNonNull(computer);
            return getValue(computer, receiver);
        }

        private Object getValue(Object computer, Object receiver) throws Throwable {
            Object value = (Object) read.invokeExact(receiver);
            if (!isDefault(value)) {
                return value;
            }
            return switch (mode) {
                case PLAIN -> getPlain(computer, receiver);
                case PUBLISH_ONCE -> getCas(computer, receiver);
                case SERIALIZED -> getSynchronized(computer, receiver);
            };
        }

        private Object getCas(Object computer, Object receiver) throws Throwable {
            Object candidate = (Object) compute.invokeExact(computer, receiver);
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

        private Object getPlain(Object computer, Object receiver) throws Throwable {
            Object candidate = (Object) compute.invokeExact(computer, receiver);
            if (isDefault(candidate)) {
                throw new IllegalArgumentException("The computed value is reserved for uninitialized storage");
            }
            write.invokeExact(receiver, candidate);
            return candidate;
        }

        private Object getSynchronized(Object computer, Object receiver) throws Throwable {
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
                    candidate = (Object) compute.invokeExact(computer, receiver);
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
        static final MethodType READ1_WITH_COMPUTER_TYPE = MethodType.methodType(Object.class,
                Object.class, Object.class, int.class);
        static final MethodType READ2_TYPE = MethodType.methodType(Object.class,
                Object.class, int.class, int.class);
        static final MethodType READ2_WITH_COMPUTER_TYPE = MethodType.methodType(Object.class,
                Object.class, Object.class, int.class, int.class);
        static final MethodType READ_N_TYPE = MethodType.methodType(Object.class,
                Object.class, int[].class);
        static final MethodType READ_N_WITH_COMPUTER_TYPE = MethodType.methodType(Object.class,
                Object.class, Object.class, int[].class);
        private static final MethodType COMPUTE2_TYPE = MethodType.methodType(Object.class,
                Object.class, Object.class, int.class, int.class);
        private static final MethodType COMPUTE_N_TYPE = MethodType.methodType(Object.class,
                Object.class, Object.class, int[].class);
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
        private final MethodHandle[] rowGetters;
        private final MethodHandle read;
        private final MethodHandle exchange;
        private final MethodHandle write;
        private final MethodHandle compute;
        private final Object boundComputer;
        private final Object defaultValue;

        ArrayUpdater(Mode mode, ArrayAccess access, Method computerMethod,
                     Object boundComputer)
                throws IllegalAccessException {
            this(mode, access.arrayGetter(), access.arrayType(), access.valueHandle(),
                    access.receiverType(), access.rank(), computerMethod,
                    boundComputer);
        }

        private ArrayUpdater(Mode mode, MethodHandle arrayGetter, Class<?> arrayType,
                             VarHandle element, Class<?> receiverType, int rank,
                             Method computerMethod, Object boundComputer)
                throws IllegalAccessException {
            this.mode = mode;
            this.boundComputer = boundComputer;
            this.arrayGetter = arrayGetter.asType(ARRAY_GETTER_TYPE);
            rowGetter = rank == 2
                    ? MethodHandles.arrayElementGetter(arrayType).asType(ELEMENT_READ_TYPE)
                    : null;
            if (rank > 2) {
                rowGetters = new MethodHandle[rank - 1];
                Class<?> currentArrayType = arrayType;
                for (int i = 0; i < rowGetters.length; i++) {
                    rowGetters[i] = MethodHandles.arrayElementGetter(currentArrayType)
                            .asType(ELEMENT_READ_TYPE);
                    currentArrayType = currentArrayType.getComponentType();
                }
            } else {
                rowGetters = null;
            }
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
                    .unreflectMethod(computerMethod);
            if (receiverType == null) {
                computation = MethodHandles.dropArguments(computation, 1, Object.class);
            }
            if (rank == 1) {
                computation = computation.asType(MethodType.methodType(valueType,
                        Object.class, Object.class, int.class));
                computation = MethodHandles.dropArguments(
                        computation, 3, int.class).asType(COMPUTE2_TYPE);
            } else if (rank == 2) {
                computation = computation.asType(MethodType.methodType(valueType,
                        Object.class, Object.class, int.class, int.class))
                        .asType(COMPUTE2_TYPE);
            } else {
                computation = computation.asSpreader(int[].class, rank)
                        .asType(MethodType.methodType(valueType,
                                Object.class, Object.class, int[].class))
                        .asType(COMPUTE_N_TYPE);
            }
            compute = computation;
        }

        Object get1(Object receiver, int index) throws Throwable {
            return get1WithComputer(boundComputer, receiver, index);
        }

        Object get1WithComputer(Object computer, Object receiver, int index) throws Throwable {
            Objects.requireNonNull(computer);
            Object array = (Object) arrayGetter.invokeExact(receiver);
            return getAt(computer, array, index, receiver, index, 0, null);
        }

        Object get2(Object receiver, int index0, int index1) throws Throwable {
            return get2WithComputer(boundComputer, receiver, index0, index1);
        }

        Object get2WithComputer(Object computer, Object receiver,
                                int index0, int index1) throws Throwable {
            Objects.requireNonNull(computer);
            Object outer = (Object) arrayGetter.invokeExact(receiver);
            Object row = (Object) rowGetter.invokeExact(outer, index0);
            return getAt(computer, row, index1, receiver, index0, index1, null);
        }

        Object getN(Object receiver, int[] indices) throws Throwable {
            return getNWithComputer(boundComputer, receiver, indices);
        }

        Object getNWithComputer(Object computer, Object receiver,
                                int[] indices) throws Throwable {
            Objects.requireNonNull(computer);
            Object array = (Object) arrayGetter.invokeExact(receiver);
            // Resolve the full path before computing. The last array is the
            // physical container used for coordination, even if paths alias.
            for (int i = 0; i < rowGetters.length; i++) {
                array = (Object) rowGetters[i].invokeExact(array, indices[i]);
            }
            return getAt(computer, array, indices[rowGetters.length],
                    receiver, 0, 0, indices);
        }

        private Object getAt(Object computer, Object array, int index, Object receiver,
                             int index0, int index1, int[] indices) throws Throwable {
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
                case PLAIN -> getPlain(computer, array, index, receiver, index0, index1, indices);
                case PUBLISH_ONCE -> getCas(computer, array, index, receiver, index0, index1, indices);
                case SERIALIZED -> getSynchronized(computer, array, index, receiver, index0, index1, indices);
            };
        }

        private Object getCas(Object computer, Object array, int index, Object receiver,
                              int index0, int index1, int[] indices) throws Throwable {
            Object candidate = computeCandidate(computer, receiver, index0, index1, indices);
            Object witness = (Object) exchange.invokeExact(array, index, defaultValue, candidate);
            if (isDefault(witness)) {
                return candidate;
            }
            // A failed release exchange needs an acquire read of the winner.
            return (Object) read.invokeExact(array, index);
        }

        private Object getPlain(Object computer, Object array, int index, Object receiver,
                                int index0, int index1, int[] indices) throws Throwable {
            Object candidate = computeCandidate(computer, receiver, index0, index1, indices);
            write.invokeExact(array, index, candidate);
            return candidate;
        }

        private Object getSynchronized(Object computer, Object array, int index,
                                       Object receiver,
                                       int index0, int index1, int[] indices) throws Throwable {
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
                    candidate = computeCandidate(computer, receiver, index0, index1, indices);
                    write.invokeExact(array, index, candidate);
                } finally {
                    state.computingThread = null;
                }
                synchronizedArray.discard(state);
                return candidate;
            }
        }

        private Object computeCandidate(Object computer, Object receiver,
                                        int index0, int index1,
                                        int[] indices) throws Throwable {
            Object candidate = indices == null
                    ? (Object) compute.invokeExact(computer, receiver, index0, index1)
                    : (Object) compute.invokeExact(computer, receiver, indices);
            checkCandidate(candidate);
            return candidate;
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
