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

import jdk.internal.vm.annotation.AOTSafeClassInitializer;
import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import javax.crypto.SealedObject;
import javax.crypto.spec.SecretKeySpec;
import java.io.ObjectInputFilter;
import java.lang.constant.Constable;
import java.lang.invoke.MethodHandles;
import java.lang.module.ModuleDescriptor;
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
import java.util.jar.JarFile;
import java.io.Console;
import java.io.FileDescriptor;
import java.io.FilePermission;
import java.io.ObjectInputStream;
import java.io.RandomAccessFile;
import java.security.Signature;
import java.util.zip.ZipFile;
import javax.security.auth.x500.X500Principal;

/** A repository of "shared secrets", which are a mechanism for
    calling implementation-private methods in another package without
    using reflection. A package-private class implements a public
    interface and provides the ability to call package-private methods
    within that package; the object implementing that interface is
    provided through a third package to which access is restricted.
    This framework avoids the primary disadvantage of using reflection
    for this purpose, namely the loss of compile-time checking.
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

    // This field is not necessarily stable
    private static JavaAWTFontAccess javaAWTFontAccess;
    @Stable private static JavaBeansAccess javaBeansAccess;
    @Stable private static JavaLangAccess javaLangAccess;
    @Stable private static JavaLangInvokeAccess javaLangInvokeAccess;
    @Stable private static JavaLangModuleAccess javaLangModuleAccess;
/*
    @Stable private static JavaLangRefAccess javaLangRefAccess;
    @Stable private static JavaLangReflectAccess javaLangReflectAccess;
    @Stable private static JavaIOAccess javaIOAccess;
    @Stable private static JavaIOFileDescriptorAccess javaIOFileDescriptorAccess;
    @Stable private static JavaIORandomAccessFileAccess javaIORandomAccessFileAccess;

    @Stable private static JavaObjectInputStreamReadString javaObjectInputStreamReadString;
    @Stable private static JavaObjectInputStreamAccess javaObjectInputStreamAccess;
    @Stable private static JavaObjectInputFilterAccess javaObjectInputFilterAccess;
    @Stable private static JavaObjectStreamReflectionAccess javaObjectStreamReflectionAccess;
    @Stable private static JavaNetInetAddressAccess javaNetInetAddressAccess;
    @Stable private static JavaNetHttpCookieAccess javaNetHttpCookieAccess;
    @Stable private static JavaNetUriAccess javaNetUriAccess;
    @Stable private static JavaNetURLAccess javaNetURLAccess;
    @Stable private static JavaNioAccess javaNioAccess;
    @Stable private static JavaUtilCollectionAccess javaUtilCollectionAccess;
    @Stable private static JavaUtilConcurrentTLRAccess javaUtilConcurrentTLRAccess;
    @Stable private static JavaUtilConcurrentFJPAccess javaUtilConcurrentFJPAccess;
    @Stable private static JavaUtilJarAccess javaUtilJarAccess;
    @Stable private static JavaUtilZipFileAccess javaUtilZipFileAccess;
    @Stable private static JavaUtilResourceBundleAccess javaUtilResourceBundleAccess;
    @Stable private static JavaSecurityPropertiesAccess javaSecurityPropertiesAccess;
    @Stable private static JavaSecuritySignatureAccess javaSecuritySignatureAccess;
    @Stable private static JavaSecuritySpecAccess javaSecuritySpecAccess;
    @Stable private static JavaxCryptoSealedObjectAccess javaxCryptoSealedObjectAccess;
    @Stable private static JavaxCryptoSpecAccess javaxCryptoSpecAccess;
    @Stable private static JavaxSecurityAccess javaxSecurityAccess; */

    // Sentinel value signaling that no explicit class initialization should be performed
    private static final String NO_INIT = "";

    // This map is used to associate a certain Access interface to another class where
    // the implementation of said interface resides
    private static final Map<Class<? extends Access>, Constable> IMPLEMENTATIONS = implementations();

    // In order to avoid creating a circular dependency graph, we refrain from using
    // ImmutableCollections. Here, this means we have to resort to mutating a map.
    private static Map<Class<? extends Access>, Constable> implementations() {
        final Map<Class<? extends Access>, Constable> map = new HashMap<>();

        map.put(JavaLangRefAccess.class, NO_INIT);
        map.put(JavaLangReflectAccess.class, NO_INIT);
        map.put(JavaIORandomAccessFileAccess.class, RandomAccessFile.class);
        map.put(JavaIOFileDescriptorAccess.class, FileDescriptor.class);
        map.put(JavaIOAccess.class, Console.class);
        map.put(JavaObjectInputStreamReadString.class, ObjectInputStream.class);
        map.put(JavaObjectStreamReflectionAccess.class, "java.io.ObjectStreamReflection$Access");
        map.put(JavaObjectInputFilterAccess.class, ObjectInputFilter.Config.class);
        map.put(JavaObjectInputStreamAccess.class, ObjectInputStream.class);
        map.put(JavaNetHttpCookieAccess.class, HttpCookie.class);
        map.put(JavaNetInetAddressAccess.class, InetAddress.class);
        map.put(JavaNetURLAccess.class, URL.class);
        map.put(JavaNetUriAccess.class, URI.class);
        map.put(JavaNioAccess.class, Buffer.class);
        map.put(JavaUtilCollectionAccess.class, "java.util.ImmutableCollections$Access");
        map.put(JavaUtilConcurrentFJPAccess.class, ForkJoinPool.class);
        map.put(JavaUtilConcurrentTLRAccess.class, "java.util.concurrent.ThreadLocalRandom$Access");
        map.put(JavaUtilResourceBundleAccess.class, ResourceBundle.class);
        map.put(JavaUtilZipFileAccess.class, ZipFile.class);
        map.put(JavaUtilJarAccess.class, JarFile.class);
        map.put(JavaxSecurityAccess.class, X500Principal.class);
        map.put(JavaxCryptoSealedObjectAccess.class, SealedObject.class);
        map.put(JavaxCryptoSpecAccess.class, SecretKeySpec.class);
        map.put(JavaSecuritySpecAccess.class, EncodedKeySpec.class);
        map.put(JavaSecuritySignatureAccess.class, Signature.class);
        map.put(JavaSecurityPropertiesAccess.class, Security.class);

        return Collections.unmodifiableMap(map);
    }

    private static final StableComponentContainer<Access> COMPONENTS =
            StableComponentContainer.of(IMPLEMENTATIONS.keySet());

    @ForceInline
    public static <T extends Access> T get(Class<T> clazz) {
        final T component = COMPONENTS.orElse(clazz, null);
        return component == null
                ? getSlowPath(clazz)
                : component;
    }

    @DontInline
    private static <T extends Access> T getSlowPath(Class<T> clazz) {
        final Constable implementation = IMPLEMENTATIONS.get(clazz);
        // We can't use pattern matching here as that would trigger
        // classfile initialization
        if (implementation instanceof Class<?> c) {
            ensureClassInitialized(c);
        } else if (implementation instanceof String s && !s.equals(NO_INIT)) {
            ensureClassInitialized(s);
        } else {
            throw new InternalError("Should not reach here: " + implementation);
        }
        // The component should now be initialized
        return COMPONENTS.get(clazz);
    }

    public static <T extends Access> void set(Class<T> type, T access) {
        COMPONENTS.set(type, access);
    }

    public static void setJavaLangAccess(JavaLangAccess jla) {
        javaLangAccess = jla;
    }

    public static JavaLangAccess getJavaLangAccess() {
        return javaLangAccess;
    }

    public static void setJavaLangInvokeAccess(JavaLangInvokeAccess jlia) {
        javaLangInvokeAccess = jlia;
    }

    public static JavaLangInvokeAccess getJavaLangInvokeAccess() {
        var access = javaLangInvokeAccess;
        if (access == null) {
            try {
                Class.forName("java.lang.invoke.MethodHandleImpl", true, null);
                access = javaLangInvokeAccess;
            } catch (ClassNotFoundException e) {}
        }
        return access;
    }

    public static void setJavaLangModuleAccess(JavaLangModuleAccess jlrma) {
        javaLangModuleAccess = jlrma;
    }

    public static JavaLangModuleAccess getJavaLangModuleAccess() {
        var access = javaLangModuleAccess;
        if (access == null) {
            ensureClassInitialized(ModuleDescriptor.class);
            access = javaLangModuleAccess;
        }
        return access;
    }

    public static void setJavaAWTFontAccess(JavaAWTFontAccess jafa) {
        javaAWTFontAccess = jafa;
    }

    public static JavaAWTFontAccess getJavaAWTFontAccess() {
        // this may return null in which case calling code needs to
        // provision for.
        return javaAWTFontAccess;
    }

    public static JavaBeansAccess getJavaBeansAccess() {
        return javaBeansAccess;
    }

    public static void setJavaBeansAccess(JavaBeansAccess access) {
        javaBeansAccess = access;
    }

    private static void ensureClassInitialized(Class<?> c) {
        try {
            MethodHandles.lookup().ensureInitialized(c);
        } catch (IllegalAccessException _) {}
    }

    private static void ensureClassInitialized(String className) {
        try {
            Class.forName(className, true, null);
        } catch (ClassNotFoundException _) {}
    }

}
