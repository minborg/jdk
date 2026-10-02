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

import java.lang.classfile.ClassFile;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import jdk.internal.access.SharedSecrets;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;
import static jdk.internal.constant.ConstantUtils.methodTypeDesc;
import static jdk.internal.constant.ConstantUtils.referenceClassDesc;

/**
 * Adapts an updater method handle to its functional interface. Access to
 * field-backed storage must have been authorized before an adapter is created.
 * The trusted lookup used here only defines an implementation in the
 * interface's nest; it is never used to resolve a field or obtain an updater
 * target.
 */
final class LazyUpdaterFactory {
    private LazyUpdaterFactory() { }

    private static final String TARGET = "target";
    private static final MethodType CONSTRUCTOR_TYPE =
            MethodType.methodType(void.class, MethodHandle.class);
    private static final MethodTypeDesc CONSTRUCTOR_DESC = methodTypeDesc(CONSTRUCTOR_TYPE);

    // Each factory owns one handle, regardless of the number of receiver
    // instances on which the resulting updater will be used.
    private static final ClassValue<MethodHandle> CONSTRUCTORS = new ClassValue<>() {
        @Override
        protected MethodHandle computeValue(Class<?> type) {
            return constructor(type);
        }
    };

    /**
     * Returns the abstract method whose return type is most specific. The
     * accepted interfaces have one abstract signature after Object methods
     * and covariant-return duplicates have been removed.
     */
    static Method functionalMethod(Class<?> type) {
        List<Method> methods = abstractMethods(type);
        Method first = methods.getFirst();
        for (Method method : methods) {
            if (!method.getName().equals(first.getName())
                    || !Arrays.equals(method.getParameterTypes(), first.getParameterTypes())) {
                throw notFunctional(type);
            }
        }
        for (Method candidate : methods) {
            boolean mostSpecific = true;
            for (Method method : methods) {
                if (!method.getReturnType().isAssignableFrom(candidate.getReturnType())) {
                    mostSpecific = false;
                    break;
                }
            }
            if (mostSpecific) {
                return candidate;
            }
        }
        throw notFunctional(type);
    }

    /**
     * Creates an adapter with exactly the canonical method's invocation
     * type. Abstract methods with wider covariant return types are emitted
     * as bridges; default methods are inherited without replacement.
     */
    static <T> T create(Class<T> type, Method method, MethodHandle target) {
        Objects.requireNonNull(target);
        Method canonical = functionalMethod(type);
        MethodType targetType = MethodType.methodType(canonical.getReturnType(),
                canonical.getParameterTypes());
        if (!canonical.getName().equals(method.getName())
                || canonical.getReturnType() != method.getReturnType()
                || !Arrays.equals(canonical.getParameterTypes(), method.getParameterTypes())
                || !target.type().equals(targetType)) {
            throw new IllegalArgumentException("Updater target does not match the functional method");
        }
        MethodHandle constructor = CONSTRUCTORS.get(type);
        try {
            return type.cast((Object) constructor.invokeExact(target));
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new InternalError(e);
        }
    }

    private static List<Method> abstractMethods(Class<?> type) {
        Objects.requireNonNull(type);
        if (!type.isInterface() || type.isSealed() || type.isHidden()) {
            throw notFunctional(type);
        }
        List<Method> methods = new ArrayList<>();
        for (Method method : type.getMethods()) {
            if (Modifier.isAbstract(method.getModifiers()) && !isObjectMethod(method)) {
                methods.add(method);
            }
        }
        if (methods.isEmpty()) {
            throw notFunctional(type);
        }
        return methods;
    }

    private static boolean isObjectMethod(Method method) {
        try {
            return Object.class.getMethod(method.getName(), method.getParameterTypes()).getReturnType()
                    == method.getReturnType();
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static IllegalArgumentException notFunctional(Class<?> type) {
        return new IllegalArgumentException("Not a supported functional interface: " + type.getTypeName());
    }

    private static MethodHandle constructor(Class<?> type) {
        Method method = functionalMethod(type);
        MethodTypeDesc targetDesc = methodTypeDesc(method.getReturnType(), method.getParameterTypes());
        var descriptors = new LinkedHashSet<MethodTypeDesc>();
        for (Method abstractMethod : abstractMethods(type)) {
            descriptors.add(methodTypeDesc(abstractMethod.getReturnType(), abstractMethod.getParameterTypes()));
        }

        ClassDesc adapterDesc = ClassDesc.of(type.getName() + "$$LazyUpdater");
        byte[] bytes = ClassFile.of().build(adapterDesc, clb -> {
            clb.withFlags(ACC_FINAL | ACC_SUPER | ACC_SYNTHETIC)
               .withInterfaceSymbols(referenceClassDesc(type))
               .withField(TARGET, CD_MethodHandle, ACC_PRIVATE | ACC_FINAL);
            clb.withMethodBody(INIT_NAME, CONSTRUCTOR_DESC, ACC_PRIVATE, cob -> cob
                    .aload(0)
                    .invokespecial(CD_Object, INIT_NAME, MethodTypeDesc.of(CD_void))
                    .aload(0)
                    .aload(1)
                    .putfield(adapterDesc, TARGET, CD_MethodHandle)
                    .return_());

            for (MethodTypeDesc descriptor : descriptors) {
                int flags = ACC_PUBLIC | ACC_FINAL;
                if (!descriptor.equals(targetDesc)) {
                    flags |= ACC_BRIDGE | ACC_SYNTHETIC;
                }
                clb.withMethodBody(method.getName(), descriptor, flags, cob -> {
                    cob.aload(0).getfield(adapterDesc, TARGET, CD_MethodHandle);
                    for (int i = 0; i < descriptor.parameterCount(); i++) {
                        cob.loadLocal(TypeKind.from(descriptor.parameterType(i)), cob.parameterSlot(i));
                    }
                    cob.invokevirtual(CD_MethodHandle, "invokeExact", targetDesc)
                       .return_(TypeKind.from(descriptor.returnType()));
                });
            }
        });
        try {
            return SharedSecrets.getJavaLangInvokeAccess()
                    .defineHiddenClassConstructor(type, bytes, CONSTRUCTOR_TYPE)
                    .asType(CONSTRUCTOR_TYPE.changeReturnType(Object.class));
        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }
}
