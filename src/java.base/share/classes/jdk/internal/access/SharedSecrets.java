/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.lang.stable.StableHeterogeneousContainer;
import jdk.internal.lang.stable.StableValueFactories;

import javax.crypto.SealedObject;
import javax.crypto.spec.SecretKeySpec;
import java.io.ObjectInputFilter;
import java.lang.invoke.MethodHandles;
import java.lang.module.ModuleDescriptor;
import java.security.Security;
import java.security.spec.EncodedKeySpec;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import java.io.Console;
import java.io.FileDescriptor;
import java.io.FilePermission;
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
 */

public final class SharedSecrets {

    // No instances
    private SharedSecrets() {}

    // Marker for access classes that do not have any initialization logic
    private static final Object NO_OP = new Object();

    // This map holds all Access interfaces and their associations for creating an Access
    // implementation (if any).
    // Components with no initialization code can use NO_OP as initialization
    // Todo: If we use a mutable map, the entries can be removed when they have been used
    private static final Map<Class<?>, Object> IMPLEMENTATIONS = Map.ofEntries(
            Map.entry(JavaLangAccess.class, NO_OP),
            Map.entry(JavaUtilCollectionAccess.class, "java.util.ImmutableCollections$Access"),
            Map.entry(JavaUtilConcurrentFJPAccess.class, "java.util.concurrent.ForkJoinPool"), // ForkJoinPool.class : Is this equivalent to the old solution?
            Map.entry(JavaUtilConcurrentTLRAccess.class, "java.util.concurrent.ThreadLocalRandom$Access"),
            Map.entry(JavaUtilJarAccess.class, JarFile.class),
            Map.entry(JavaNetUriAccess.class, java.net.URI.class),
            Map.entry(JavaNetURLAccess.class, java.net.URL.class),
            Map.entry(JavaBeansAccess.class, NO_OP),
            Map.entry(JavaLangInvokeAccess.class, "java.lang.invoke.MethodHandleImpl"),
            Map.entry(JavaLangModuleAccess.class, ModuleDescriptor.class),
            Map.entry(JavaLangRefAccess.class, NO_OP),

            Map.entry(JavaAWTFontAccess.class, NO_OP), // this may return null in which case calling code needs to provision for.
            Map.entry(JavaAWTAccess.class, NO_OP)      // this may return null in which case calling code needs to provision for.
    );
    // This container holds the actual Access components
    private static final StableHeterogeneousContainer COMPONENTS =
            StableValueFactories.ofHeterogeneousContainer(IMPLEMENTATIONS.keySet());

    private static JavaLangReflectAccess javaLangReflectAccess;
    private static JavaIOAccess javaIOAccess;
    private static JavaIOFileDescriptorAccess javaIOFileDescriptorAccess;
    private static JavaIOFilePermissionAccess javaIOFilePermissionAccess;
    private static JavaIORandomAccessFileAccess javaIORandomAccessFileAccess;
    private static JavaObjectInputStreamReadString javaObjectInputStreamReadString;
    private static JavaObjectInputStreamAccess javaObjectInputStreamAccess;
    private static JavaObjectInputFilterAccess javaObjectInputFilterAccess;
    private static JavaObjectStreamReflectionAccess javaObjectStreamReflectionAccess;
    private static JavaNetInetAddressAccess javaNetInetAddressAccess;
    private static JavaNetHttpCookieAccess javaNetHttpCookieAccess;
    private static JavaNioAccess javaNioAccess;
    private static JavaUtilZipFileAccess javaUtilZipFileAccess;
    private static JavaUtilResourceBundleAccess javaUtilResourceBundleAccess;
    private static JavaSecurityPropertiesAccess javaSecurityPropertiesAccess;
    private static JavaSecuritySignatureAccess javaSecuritySignatureAccess;
    private static JavaSecuritySpecAccess javaSecuritySpecAccess;
    private static JavaxCryptoSealedObjectAccess javaxCryptoSealedObjectAccess;
    private static JavaxCryptoSpecAccess javaxCryptoSpecAccess;
    private static JavaxSecurityAccess javaxSecurityAccess;

    public static <T> void putOrThrow(Class<T> type, T access) {
        if (!COMPONENTS.tryPut(type, access)) {
            throw new IllegalArgumentException(
                    "The type '" + type + "' was already associated with " + COMPONENTS.get(type));
        }
    }

    public static <T> T getOrNull(Class<T> type) {
        T access = COMPONENTS.get(type);
        if (access == null) {
            Object impl = IMPLEMENTATIONS.get(type);
            if (impl == NO_OP) {
                // there is no impl (e.g. for JavaAWTAccess)
                return null;
            }
            // Cannot use pattern matching this early in the init sequence
            if (impl instanceof String s) {
                try {
                    Class.forName(s, true, null);
                } catch (ClassNotFoundException e) {
                    throw new InternalError(e);
                }
            }
            if (impl instanceof Class<?> c) {
                ensureClassInitialized(c);
            }
            access = COMPONENTS.get(type);
        }
        return access;
    }

    public static <T> T computeIfAbsent(Class<T> type, Supplier<? extends T> supplier) {
        T access = COMPONENTS.get(type);
        if (access == null) {
            return COMPONENTS.computeIfAbsent(type, new Function<Class<T>, T>() {
                @Override
                public T apply(Class<T> type) {
                    return supplier.get();
                }
            });
        }
        return access;
    }

    public static <T> T getOrThrow(Class<T> type) {
        return Objects.requireNonNull(
                getOrNull(type)
        );
    }

    public static void setJavaLangReflectAccess(JavaLangReflectAccess jlra) {
        javaLangReflectAccess = jlra;
    }

    public static JavaLangReflectAccess getJavaLangReflectAccess() {
        return javaLangReflectAccess;
    }

    public static void setJavaNetInetAddressAccess(JavaNetInetAddressAccess jna) {
        javaNetInetAddressAccess = jna;
    }

    public static JavaNetInetAddressAccess getJavaNetInetAddressAccess() {
        var access = javaNetInetAddressAccess;
        if (access == null) {
            ensureClassInitialized(java.net.InetAddress.class);
            access = javaNetInetAddressAccess;
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

    public static void setJavaIOAccess(JavaIOAccess jia) {
        javaIOAccess = jia;
    }

    public static JavaIOAccess getJavaIOAccess() {
        var access = javaIOAccess;
        if (access == null) {
            ensureClassInitialized(Console.class);
            access = javaIOAccess;
        }
        return access;
    }

    public static void setJavaIOFileDescriptorAccess(JavaIOFileDescriptorAccess jiofda) {
        javaIOFileDescriptorAccess = jiofda;
    }

    public static JavaIOFilePermissionAccess getJavaIOFilePermissionAccess() {
        var access = javaIOFilePermissionAccess;
        if (access == null) {
            ensureClassInitialized(FilePermission.class);
            access = javaIOFilePermissionAccess;
        }
        return access;
    }

    public static void setJavaIOFilePermissionAccess(JavaIOFilePermissionAccess jiofpa) {
        javaIOFilePermissionAccess = jiofpa;
    }

    public static JavaIOFileDescriptorAccess getJavaIOFileDescriptorAccess() {
        var access = javaIOFileDescriptorAccess;
        if (access == null) {
            ensureClassInitialized(FileDescriptor.class);
            access = javaIOFileDescriptorAccess;
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

    public static JavaObjectInputStreamReadString getJavaObjectInputStreamReadString() {
        var access = javaObjectInputStreamReadString;
        if (access == null) {
            ensureClassInitialized(ObjectInputStream.class);
            access = javaObjectInputStreamReadString;
        }
        return access;
    }

    public static void setJavaObjectInputStreamReadString(JavaObjectInputStreamReadString access) {
        javaObjectInputStreamReadString = access;
    }

    public static JavaObjectInputStreamAccess getJavaObjectInputStreamAccess() {
        var access = javaObjectInputStreamAccess;
        if (access == null) {
            ensureClassInitialized(ObjectInputStream.class);
            access = javaObjectInputStreamAccess;
        }
        return access;
    }

    public static void setJavaObjectInputStreamAccess(JavaObjectInputStreamAccess access) {
        javaObjectInputStreamAccess = access;
    }

    public static JavaObjectInputFilterAccess getJavaObjectInputFilterAccess() {
        var access = javaObjectInputFilterAccess;
        if (access == null) {
            ensureClassInitialized(ObjectInputFilter.Config.class);
            access = javaObjectInputFilterAccess;
        }
        return access;
    }

    public static void setJavaObjectInputFilterAccess(JavaObjectInputFilterAccess access) {
        javaObjectInputFilterAccess = access;
    }

    public static JavaObjectStreamReflectionAccess getJavaObjectStreamReflectionAccess() {
        var access = javaObjectStreamReflectionAccess;
        if (access == null) {
            try {
                Class.forName("java.io.ObjectStreamReflection$Access", true, null);
                access = javaObjectStreamReflectionAccess;
            } catch (ClassNotFoundException e) {}
        }
        return access;
    }

    public static void setJavaObjectStreamReflectionAccess(JavaObjectStreamReflectionAccess access) {
        javaObjectStreamReflectionAccess = access;
    }

    public static void setJavaIORandomAccessFileAccess(JavaIORandomAccessFileAccess jirafa) {
        javaIORandomAccessFileAccess = jirafa;
    }

    public static JavaIORandomAccessFileAccess getJavaIORandomAccessFileAccess() {
        var access = javaIORandomAccessFileAccess;
        if (access == null) {
            ensureClassInitialized(RandomAccessFile.class);
            access = javaIORandomAccessFileAccess;
        }
        return access;
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
        } catch (IllegalAccessException e) {}
    }
}
