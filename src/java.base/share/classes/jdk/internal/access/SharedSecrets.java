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
import java.security.Security;
import java.security.spec.EncodedKeySpec;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ForkJoinPool;
import java.util.jar.JarFile;
import java.io.Console;
import java.io.FileDescriptor;
import java.io.ObjectInputStream;
import java.io.RandomAccessFile;
import java.security.Signature;
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

    // Sentinel value signaling that no explicit class initialization shall be performed
    private static final String NO_INIT = "";

    // This map is used to associate a certain Access interface to another class where
    // the implementation of said interface resides
    private static final Map<Class<? extends Access>, ? extends Constable> IMPLEMENTATIONS =
            Map.ofEntries(
                    Map.entry(JavaIOAccess.class                    , Console.class),
                    Map.entry(JavaLangAccess.class                  , System.class),
                    Map.entry(JavaLangInvokeAccess.class            , "java.lang.invoke.MethodHandleImpl"),
                    Map.entry(JavaBeansAccess.class                 , NO_INIT),
                    Map.entry(JavaLangModuleAccess.class            , ModuleDescriptor.class),
                    Map.entry(JavaUtilJarAccess.class               , JarFile.class),
                    Map.entry(JavaLangRefAccess.class               , NO_INIT),
                    Map.entry(JavaLangReflectAccess.class           , NO_INIT),
                    Map.entry(JavaIOFileDescriptorAccess.class      , FileDescriptor.class),
                    Map.entry(JavaIORandomAccessFileAccess.class    , RandomAccessFile.class),
                    Map.entry(JavaObjectInputStreamReadString.class , ObjectInputStream.class),
                    Map.entry(JavaObjectInputStreamAccess.class     , ObjectInputStream.class),
                    Map.entry(JavaObjectInputFilterAccess.class     , ObjectInputFilter.Config.class),
                    Map.entry(JavaObjectStreamReflectionAccess.class, "java.io.ObjectStreamReflection$Access"),
                    Map.entry(JavaNetInetAddressAccess.class        , java.net.InetAddress.class)
            );

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
        switch (implementation) {
            case Class<?> c -> ensureClassInitialized(c);
            case String s   -> { if (!s.equals(NO_INIT)) ensureClassInitialized(s); }
            default         -> throw new InternalError("Should not reach here");
        }
        // The component should now be initialized
        return COMPONENTS.get(clazz);
    }

    public static <T extends Access> void set(Class<T> type, T access) {
        COMPONENTS.set(type, access);
    }

    // This field is not necessarily stable
    private static JavaAWTFontAccess javaAWTFontAccess;
//    @Stable private static JavaBeansAccess javaBeansAccess;
//    @Stable private static JavaLangAccess javaLangAccess;
//    @Stable private static JavaLangInvokeAccess javaLangInvokeAccess;
//    @Stable private static JavaLangModuleAccess javaLangModuleAccess;
//    @Stable private static JavaLangRefAccess javaLangRefAccess;
//    @Stable private static JavaLangReflectAccess javaLangReflectAccess;
    //@Stable private static JavaIOAccess javaIOAccess;
//    @Stable private static JavaIOFileDescriptorAccess javaIOFileDescriptorAccess;
//    @Stable private static JavaIORandomAccessFileAccess javaIORandomAccessFileAccess;
//    @Stable private static JavaObjectInputStreamReadString javaObjectInputStreamReadString;
//    @Stable private static JavaObjectInputStreamAccess javaObjectInputStreamAccess;
//    @Stable private static JavaObjectInputFilterAccess javaObjectInputFilterAccess;
//    @Stable private static JavaObjectStreamReflectionAccess javaObjectStreamReflectionAccess;
//    @Stable private static JavaNetInetAddressAccess javaNetInetAddressAccess;
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
    @Stable private static JavaxSecurityAccess javaxSecurityAccess;

    public static void setJavaUtilCollectionAccess(JavaUtilCollectionAccess juca) {
        javaUtilCollectionAccess = juca;
    }

    public static JavaUtilCollectionAccess getJavaUtilCollectionAccess() {
        var access = javaUtilCollectionAccess;
        if (access == null) {
            try {
                Class.forName("java.util.ImmutableCollections$Access", true, null);
                access = javaUtilCollectionAccess;
            } catch (ClassNotFoundException e) {}
        }
        return access;
    }

    public static void setJavaUtilConcurrentTLRAccess(JavaUtilConcurrentTLRAccess access) {
        javaUtilConcurrentTLRAccess = access;
    }

    public static JavaUtilConcurrentTLRAccess getJavaUtilConcurrentTLRAccess() {
        var access = javaUtilConcurrentTLRAccess;
        if (access == null) {
            try {
                Class.forName("java.util.concurrent.ThreadLocalRandom$Access", true, null);
                access = javaUtilConcurrentTLRAccess;
            } catch (ClassNotFoundException e) {}
        }
        return access;
    }

    public static void setJavaUtilConcurrentFJPAccess(JavaUtilConcurrentFJPAccess access) {
        javaUtilConcurrentFJPAccess = access;
    }

    public static JavaUtilConcurrentFJPAccess getJavaUtilConcurrentFJPAccess() {
        var access = javaUtilConcurrentFJPAccess;
        if (access == null) {
            ensureClassInitialized(ForkJoinPool.class);
            access = javaUtilConcurrentFJPAccess;
        }
        return access;
    }

    public static void setJavaNetUriAccess(JavaNetUriAccess jnua) {
        javaNetUriAccess = jnua;
    }

    public static JavaNetUriAccess getJavaNetUriAccess() {
        var access = javaNetUriAccess;
        if (access == null) {
            ensureClassInitialized(java.net.URI.class);
            access = javaNetUriAccess;
        }
        return access;
    }

    public static void setJavaNetURLAccess(JavaNetURLAccess jnua) {
        javaNetURLAccess = jnua;
    }

    public static JavaNetURLAccess getJavaNetURLAccess() {
        var access = javaNetURLAccess;
        if (access == null) {
            ensureClassInitialized(java.net.URL.class);
            access = javaNetURLAccess;
        }
        return access;
    }

    public static void setJavaNetHttpCookieAccess(JavaNetHttpCookieAccess a) {
        javaNetHttpCookieAccess = a;
    }

    public static JavaNetHttpCookieAccess getJavaNetHttpCookieAccess() {
        var access = javaNetHttpCookieAccess;
        if (access == null) {
            ensureClassInitialized(java.net.HttpCookie.class);
            access = javaNetHttpCookieAccess;
        }
        return access;
    }

    public static void setJavaNioAccess(JavaNioAccess jna) {
        javaNioAccess = jna;
    }

    public static JavaNioAccess getJavaNioAccess() {
        var access = javaNioAccess;
        if (access == null) {
            // Ensure java.nio.Buffer is initialized, which provides the
            // shared secret.
            ensureClassInitialized(java.nio.Buffer.class);
            access = javaNioAccess;
        }
        return access;
    }

    public static void setJavaSecurityPropertiesAccess(JavaSecurityPropertiesAccess jspa) {
        javaSecurityPropertiesAccess = jspa;
    }

    public static JavaSecurityPropertiesAccess getJavaSecurityPropertiesAccess() {
        var access = javaSecurityPropertiesAccess;
        if (access == null) {
            ensureClassInitialized(Security.class);
            access = javaSecurityPropertiesAccess;
        }
        return access;
    }

    public static JavaUtilZipFileAccess getJavaUtilZipFileAccess() {
        var access = javaUtilZipFileAccess;
        if (access == null) {
            ensureClassInitialized(java.util.zip.ZipFile.class);
            access = javaUtilZipFileAccess;
        }
        return access;
    }

    public static void setJavaUtilZipFileAccess(JavaUtilZipFileAccess access) {
        javaUtilZipFileAccess = access;
    }

    public static void setJavaAWTFontAccess(JavaAWTFontAccess jafa) {
        javaAWTFontAccess = jafa;
    }

    public static JavaAWTFontAccess getJavaAWTFontAccess() {
        // this may return null in which case calling code needs to
        // provision for.
        return javaAWTFontAccess;
    }

    public static JavaUtilResourceBundleAccess getJavaUtilResourceBundleAccess() {
        var access = javaUtilResourceBundleAccess;
        if (access == null) {
            ensureClassInitialized(ResourceBundle.class);
            access = javaUtilResourceBundleAccess;
        }
        return access;
    }

    public static void setJavaUtilResourceBundleAccess(JavaUtilResourceBundleAccess access) {
        javaUtilResourceBundleAccess = access;
    }

    public static void setJavaSecuritySignatureAccess(JavaSecuritySignatureAccess jssa) {
        javaSecuritySignatureAccess = jssa;
    }

    public static JavaSecuritySignatureAccess getJavaSecuritySignatureAccess() {
        var access = javaSecuritySignatureAccess;
        if (access == null) {
            ensureClassInitialized(Signature.class);
            access = javaSecuritySignatureAccess;
        }
        return access;
    }

    public static void setJavaSecuritySpecAccess(JavaSecuritySpecAccess jssa) {
        javaSecuritySpecAccess = jssa;
    }

    public static JavaSecuritySpecAccess getJavaSecuritySpecAccess() {
        var access = javaSecuritySpecAccess;
        if (access == null) {
            ensureClassInitialized(EncodedKeySpec.class);
            access = javaSecuritySpecAccess;
        }
        return access;
    }

    public static void setJavaxCryptoSpecAccess(JavaxCryptoSpecAccess jcsa) {
        javaxCryptoSpecAccess = jcsa;
    }

    public static JavaxCryptoSpecAccess getJavaxCryptoSpecAccess() {
        var access = javaxCryptoSpecAccess;
        if (access == null) {
            ensureClassInitialized(SecretKeySpec.class);
            access = javaxCryptoSpecAccess;
        }
        return access;
    }

    public static void setJavaxCryptoSealedObjectAccess(JavaxCryptoSealedObjectAccess jcsoa) {
        javaxCryptoSealedObjectAccess = jcsoa;
    }

    public static JavaxCryptoSealedObjectAccess getJavaxCryptoSealedObjectAccess() {
        var access = javaxCryptoSealedObjectAccess;
        if (access == null) {
            ensureClassInitialized(SealedObject.class);
            access = javaxCryptoSealedObjectAccess;
        }
        return access;
    }

    public static void setJavaxSecurityAccess(JavaxSecurityAccess jsa) {
        javaxSecurityAccess = jsa;
    }

    public static JavaxSecurityAccess getJavaxSecurityAccess() {
        var access = javaxSecurityAccess;
        if (access == null) {
            ensureClassInitialized(X500Principal.class);
            access = javaxSecurityAccess;
        }
        return access;
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
