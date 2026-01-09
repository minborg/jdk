/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.access;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.AOTSafeClassInitializer;
import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import javax.crypto.SealedObject;
import javax.crypto.spec.SecretKeySpec;
import java.io.ObjectInputFilter;
import java.lang.module.ModuleDescriptor;
import java.lang.ref.Reference;
import java.net.HttpCookie;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.Buffer;
import java.security.Security;
import java.security.spec.EncodedKeySpec;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.jar.JarFile;
import java.io.Console;
import java.io.FileDescriptor;
import java.io.ObjectInputStream;
import java.io.RandomAccessFile;
import java.security.Signature;
import java.util.zip.ZipFile;
import javax.security.auth.x500.X500Principal;

/**
 * A repository of "shared secrets", which are a mechanism for
 * calling implementation-private methods in another package without
 * using reflection. A package-private class implements a public
 * interface and provides the ability to call package-private methods
 * within that package; the object implementing that interface is
 * provided through a third package to which access is restricted.
 * This framework avoids the primary disadvantage of using reflection
 * for this purpose, namely the loss of compile-time checking.
 * <p><strong>
 * Usage of these APIs often means bad encapsulation designs,
 * increased complexity and lack of sustainability.
 * Use this only as a last resort!
 * </strong>
 *
 * <p> Notes on the @AOTSafeClassInitializer annotation:
 *
 * <p>All static fields in SharedSecrets that are initialized in the AOT
 * assembly phase must be stateless (as checked by the HotSpot C++ class
 * CDSHeapVerifier::SharedSecretsAccessorFinder) so they can be safely
 * stored in the AOT cache.
 *
 * <p>Static fields such as javaObjectInputFilterAccess point to a Lambda
 * which is not stateless. The AOT assembly phase must not execute any Java
 * code that would lead to the initialization of such fields, or else the AOT
 * cache creation will fail.
 */
@AOTSafeClassInitializer
public final class SharedSecrets {
    private SharedSecrets() { }

    // Todo: Remove this field.
    @Stable private static JavaLangAccess javaLangAccess;

    // Sentinel value signaling that no explicit class initialization should be performed.
    // Must not be of type Class or String.
    private static final Object NO_INIT = new Object();

    // Fix to avoid CDSHeapVerifier::SharedSecretsAccessorFinder problems
    // The above C++ class must be modified to be able to CDS components within the container
    @AOTSafeClassInitializer
    private static final class Holder {
        // This map is used to associate a certain Access interface to another class where
        // the implementation of said interface resides
        private static final Map<Class<? extends Access>, Object> IMPLEMENTATIONS = implementations();

        private static final HeterogeneousContainer<Access> COMPONENTS =
                HeterogeneousContainer.of(Holder.IMPLEMENTATIONS.keySet());
    }

    // In order to avoid creating a circular dependency graph, we refrain from using
    // ImmutableCollections. This means we have to resort to mutating a map.
    private static Map<Class<? extends Access>, Object> implementations() {
        final Map<Class<? extends Access>, Object> map = new HashMap<>();

        map.put(JavaAWTFontAccess.class, NO_INIT);
        map.put(JavaBeansAccess.class, NO_INIT);
        map.put(JavaIOAccess.class, Console.class);
        map.put(JavaIOFileDescriptorAccess.class, FileDescriptor.class);
        map.put(JavaIORandomAccessFileAccess.class, RandomAccessFile.class);
        map.put(JavaLangAccess.class, NO_INIT);
        map.put(JavaLangInvokeAccess.class, "java.lang.invoke.MethodHandleImpl");
        map.put(JavaLangModuleAccess.class, ModuleDescriptor.class);
        map.put(JavaLangRefAccess.class, Reference.class);
        map.put(JavaLangReflectAccess.class, NO_INIT);
        map.put(JavaNetHttpCookieAccess.class, HttpCookie.class);
        map.put(JavaNetInetAddressAccess.class, InetAddress.class);
        map.put(JavaNetURLAccess.class, URL.class);
        map.put(JavaNetUriAccess.class, URI.class);
        map.put(JavaNioAccess.class, Buffer.class);
        map.put(JavaObjectInputFilterAccess.class, ObjectInputFilter.Config.class);
        map.put(JavaObjectInputStreamAccess.class, ObjectInputStream.class);
        map.put(JavaObjectInputStreamReadString.class, ObjectInputStream.class);
        map.put(JavaObjectStreamReflectionAccess.class, "java.io.ObjectStreamReflection$Access");
        map.put(JavaSecurityPropertiesAccess.class, Security.class);
        map.put(JavaSecuritySignatureAccess.class, Signature.class);
        map.put(JavaSecuritySpecAccess.class, EncodedKeySpec.class);
        map.put(JavaUtilCollectionAccess.class, "java.util.ImmutableCollections$Access");
        map.put(JavaUtilConcurrentFJPAccess.class, ForkJoinPool.class);
        map.put(JavaUtilConcurrentTLRAccess.class, "java.util.concurrent.ThreadLocalRandom$Access");
        map.put(JavaUtilJarAccess.class, JarFile.class);
        map.put(JavaUtilResourceBundleAccess.class, ResourceBundle.class);
        map.put(JavaUtilZipFileAccess.class, ZipFile.class);
        map.put(JavaxCryptoSealedObjectAccess.class, SealedObject.class);
        map.put(JavaxCryptoSpecAccess.class, SecretKeySpec.class);
        map.put(JavaxSecurityAccess.class, X500Principal.class);

        return Collections.unmodifiableMap(map);
    }


    @ForceInline
    public static <T extends Access> T get(Class<T> accessType) {
        final T component = Holder.COMPONENTS.orElse(accessType, null);
        return component == null
                ? getSlowPath(accessType)
                : component;
    }

    @DontInline
    private static <T extends Access> T getSlowPath(Class<T> accessType) {
        final Object implementation = Holder.IMPLEMENTATIONS.get(accessType);
        // We can't use pattern matching here as that would trigger
        // classfile initialization
        if (implementation instanceof Class<?> c) {
            ensureClassInitialized(c);
        } else if (implementation instanceof String s) {
            ensureClassInitialized(s);
        } else {
            throw new InternalError("Should not reach here: " + accessType + " -> " + implementation);
        }

        // The component should now be initialized
        return Holder.COMPONENTS.get(accessType);
    }

    public static <T extends Access> void set(Class<T> accessType, T access) {
        Holder.COMPONENTS.set(accessType, tee(access));
    }

    public static <T extends Access> void setIfUnset(Class<T> accessType, T access) {
        Holder.COMPONENTS.computeIfAbsent(accessType, new Function<Class<T>, T>() {
            @Override public T apply(Class<T> tClass) { return tee(access); }
        });
    }

    static <T> T tee(T access) {
        // Special case for this component type as some call sites used early in the
        // boot sequence cannot use SharedSecrets::get. So, we scribble it up on the side.
        if (access instanceof JavaLangAccess jla) {
            javaLangAccess = jla;
        }
        return access;
    }

    public static JavaLangAccess getJavaLangAccess() {
        return javaLangAccess;
    }

    private static void ensureClassInitialized(Class<?> c) {
        // Unsafe is not stateless so, can't be in an AOT field.
        Unsafe.getUnsafe().ensureClassInitialized(c);
    }

    private static void ensureClassInitialized(String className) {
        try {
            Class.forName(className, true, null);
        } catch (ClassNotFoundException _) {
            throw new InternalError("Class " + className + " not found");
        }
    }

}
