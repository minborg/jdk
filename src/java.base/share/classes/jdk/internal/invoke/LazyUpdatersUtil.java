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

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import sun.invoke.util.VerifyAccess;
import sun.invoke.util.Wrapper;

/** Helpers for lazy updater factories. */
public final class LazyUpdatersUtil {

    private static final int CALLER_ACCESS_MODES = MethodHandles.Lookup.PUBLIC
            | MethodHandles.Lookup.PRIVATE | MethodHandles.Lookup.PROTECTED
            | MethodHandles.Lookup.PACKAGE | MethodHandles.Lookup.MODULE;

    private LazyUpdatersUtil() {}

    public static void checkName(String name) {
        // Implicit null checking
        if (name.isEmpty() || name.indexOf('.') >= 0) {
            throw new IllegalArgumentException("Expected a simple field name: " + name);
        }
    }

    public static void checkCaller(Class<?> caller) {
        if (caller == null) {
            throw new IllegalCallerException("No caller frame");
        }
    }

    public static void checkArrayType(Class<?> caller, Class<?> arrayFieldType) {
        if (!arrayFieldType.isArray() || arrayFieldType.getComponentType().isArray()) {
            throw new IllegalArgumentException("Expected a one-dimensional array: " + arrayFieldType);
        }
        checkClassAccess(caller, arrayFieldType.getComponentType());
    }

    public static void checkArrayComponentType(Field field, Class<?> arrayComponentType) {
        Class<?> componentType = Wrapper.asWrapperType(field.getType().getComponentType());
        Class<?> resultType = Wrapper.asWrapperType(arrayComponentType);
        if (!resultType.isAssignableFrom(componentType)) {
            throw new IllegalArgumentException("Incompatible array component type: " + arrayComponentType);
        }
    }

    public static void checkFieldFinalState(Field field, boolean isFinal) {
        if (Modifier.isFinal(field.getModifiers()) != isFinal) {
            throw new IllegalArgumentException("Field " + field.getName() + " is " + (isFinal ? "not " : "") + "final");
        }
    }

    private static void checkClassAccess(Class<?> caller, Class<?> type) {
        if (!VerifyAccess.isClassAccessible(type, caller, null, CALLER_ACCESS_MODES)) {
            throw new IllegalArgumentException("Access not allowed to type " + type.getName()
                    + " from " + caller.getName());
        }
    }

    public static Field findField(Class<?> caller,
                                  Class<?> owner,
                                  Class<?> fieldType,
                                  String name,
                                  boolean isStatic) {
        checkClassAccess(caller, owner);
        NoSuchFieldException missing = null;
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            try {
                final Field field = type.getDeclaredField(name);
                final int modifiers = field.getModifiers();
                // Private access depends on the declaring nest; the owner was checked above.
                final Class<?> refc = Modifier.isPrivate(modifiers)
                        ? field.getDeclaringClass() : owner;
                final boolean allowed = VerifyAccess.isMemberAccessible(
                        refc, field.getDeclaringClass(), modifiers,
                        caller, null, CALLER_ACCESS_MODES);
                if (!allowed) {
                    throw new IllegalArgumentException("Access not allowed to field " + name + " from " + caller.getName());
                }
                final Class<?> actualFieldType = field.getType();
                // Allow boxing from the actual type to the desired type
                if (!fieldType.isAssignableFrom(actualFieldType) && !fieldType.isAssignableFrom(Wrapper.asWrapperType(actualFieldType))) {
                    throw new IllegalArgumentException("Field type of " + name + " is " + actualFieldType.getName() + " which is not assignable to " + fieldType.getName());
                }
                if (Modifier.isStatic(modifiers) != isStatic) {
                    throw new IllegalArgumentException("Field " + name
                            + (isStatic ? " is not static" : " is static"));
                }
                return field;
            } catch (NoSuchFieldException e) {
                missing = e;
            }
        }
        throw new IllegalArgumentException("No such field: " + name, missing);
    }
}
