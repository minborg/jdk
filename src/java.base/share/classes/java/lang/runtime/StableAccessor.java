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

package java.lang.runtime;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

import jdk.internal.access.JavaLangInvokeAccess;
import jdk.internal.access.JavaLangInvokeAccess.FieldVarHandleInfo;
import jdk.internal.access.SharedSecrets;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.TrustFinalFields;

import static java.util.Objects.requireNonNull;

/** Stable field accessor. */
@TrustFinalFields
public abstract class StableAccessor {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final Object NULL_SENTINEL = new Object();
    private static final JavaLangInvokeAccess JLI = SharedSecrets.getJavaLangInvokeAccess();

    final long offset;
    final MethodHandle initHandle;

    private StableAccessor(long offset, MethodHandle initHandle) {
        this.offset = offset;
        this.initHandle = initHandle;
    }

    /**
     * Creates a stable accessor with embedded initialization logic.
     * @param lookup the caller lookup
     * @param unusedName ignored bootstrap name
     * @param unusedAccessorType ignored bootstrap type
     * @param cacheHandle the field var handle to be used as backing storage
     * @param initHandle method handle used to initialize the cache on miss
     * @return the accessor
     * @throws NoSuchFieldException if the field cannot be found
     * @throws IllegalAccessException if the field cannot be accessed
     */
    public static StableAccessor of(MethodHandles.Lookup lookup,
                                    String unusedName,
                                    Class<?> unusedAccessorType,
                                    VarHandle cacheHandle,
                                    MethodHandle initHandle)
            throws NoSuchFieldException, IllegalAccessException {
        requireNonNull(cacheHandle);
        requireNonNull(initHandle);
        requireNonNull(lookup);

        Class<?> fieldType = cacheHandle.varType();
        if (fieldType.isPrimitive()) {
            throw new IllegalArgumentException("reference cache field required");
        }

        FieldVarHandleInfo fieldVarHandleInfo = JLI.fieldVarHandleInfo(cacheHandle);
        return fieldVarHandleInfo.isStatic()
                ? new StaticAccessor(fieldVarHandleInfo, initHandle)
                : new InstanceAccessor(fieldVarHandleInfo, initHandle);
    }

    /**
     * Returns the cached value, initializing it on demand.
     * @param receiver the receiver, ignored for static fields
     * @throws Throwable any exception thrown during initialization
     * @return the cached value
     */
    @ForceInline
    public final Object getOrInit(Object receiver) throws Throwable {
        Object actualBase = resolveBase(receiver);
        Object cached = UNSAFE.getReferenceStableVolatile(actualBase, offset);
        if (cached != null) {
            return decode(cached);
        }
        return slowGetOrInit(actualBase, receiver);
    }

    @DontInline
    private Object slowGetOrInit(Object actualBase, Object receiver) throws Throwable {
        if (initHandle == null) {
            throw new IllegalStateException("no init handle");
        }
        Object value = initHandle.invokeExact(receiver);
        Object encoded = encode(value);
        if (UNSAFE.compareAndSetReference(actualBase, offset, null, encoded)) {
            return value;
        }
        return decode(UNSAFE.getReferenceStableVolatile(actualBase, offset));
    }

    @ForceInline
    abstract Object resolveBase(Object receiver);

    @ForceInline
    private static Object encode(Object value) {
        return value == null ? NULL_SENTINEL : value;
    }

    @ForceInline
    private static Object decode(Object value) {
        return value == NULL_SENTINEL ? null : value;
    }

    @TrustFinalFields
    private static class StaticAccessor extends StableAccessor {
        final Object base;

        private StaticAccessor(FieldVarHandleInfo info,
                               MethodHandle initHandle) {
            super(info.offset(),
                    MethodHandles.dropArguments(initHandle, 0, Object.class)
                            .asType(MethodType.methodType(Object.class, Object.class)));
            this.base = info.base();
        }

        @Override
        @ForceInline
        Object resolveBase(Object receiver) {
            return base;
        }
    }

    @TrustFinalFields
    private static final class InstanceAccessor extends StableAccessor {
        private InstanceAccessor(FieldVarHandleInfo info,
                                 MethodHandle initHandle) {

            super(info.offset(),
                    initHandle.asType(MethodType.methodType(Object.class, Object.class)));
        }

        @Override
        @ForceInline
        Object resolveBase(Object receiver) {
            return requireNonNull(receiver);
        }
    }
}
