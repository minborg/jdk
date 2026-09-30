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

package java.util;

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.function.Function;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.ForceInline;

/**
 * A race-and-CAS function whose storage is a field of each receiver.
 */
final class LazyFieldFunction<C, T> implements Function<C, T> {
    // Todo: use Unsafe here instead
    private final VarHandle field;
    private final Function<? super C, ? extends T> computingFunction;

    private LazyFieldFunction(VarHandle field,
                              Function<? super C, ? extends T> computingFunction) {
        this.field = field;
        this.computingFunction = computingFunction;
    }

    static <C, T> Function<C, T> of(Lookup lookup,
                                  Class<? super C> owner,
                                  String fieldName,
                                  Class<T> valueType,
                                  Function<? super C, ? extends T> computingFunction) {
        Objects.requireNonNull(lookup);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(fieldName);
        Objects.requireNonNull(valueType);
        Objects.requireNonNull(computingFunction);
        try {
            Field field = owner.getDeclaredField(fieldName);
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)
                    || field.getType().isPrimitive()
                    || ValueClass.isNullRestrictedField(field)) {
                throw new IllegalArgumentException(
                        "Not a mutable, nullable instance reference field: " + field);
            }
            if (field.getType() != valueType) {
                throw new IllegalArgumentException(
                        "Field type " + field.getType().getTypeName()
                                + " does not match value type " + valueType.getTypeName());
            }

            // The supplied lookup checks access and narrows the
            // receiver type for protected access from a different package.
            // VarHandle also supports nullable fields with a flattened layout.
            VarHandle handle = lookup.unreflectVarHandle(field);
            if (!handle.isAccessModeSupported(VarHandle.AccessMode.GET_ACQUIRE)
                    || !handle.isAccessModeSupported(VarHandle.AccessMode.COMPARE_AND_EXCHANGE_RELEASE)) {
                throw new IllegalArgumentException(
                        "Field does not support acquire reads and atomic updates: " + field);
            }
            return new LazyFieldFunction<>(handle, computingFunction);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new IllegalArgumentException(
                    "Cannot access lazy field " + owner.getName() + "." + fieldName, ex);
        }
    }

    @Override
    @ForceInline
    @SuppressWarnings("unchecked")
    public T apply(C context) {
        Object value = field.getAcquire(Objects.requireNonNull(context));
        if (value != null) {
            return (T) value;
        }
        T candidate = Objects.requireNonNull(computingFunction.apply(context));
        // The handle checks the computed value's field type before publishing.
        Object witness = field.compareAndExchangeRelease(context, null, candidate);
        if (witness == null) {
            return candidate;
        }
        // A failed release exchange reads its witness with plain semantics.
        // Acquire the winner's publication before returning its value. The
        // field is reserved for lazy storage, so the winner cannot be replaced.
        return (T) field.getAcquire(context);
    }
}
