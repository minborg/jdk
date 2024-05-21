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

import jdk.internal.lang.StableValue;

import javax.crypto.SealedObject;
import javax.crypto.spec.SecretKeySpec;
import java.io.ObjectInputFilter;
import java.lang.invoke.MethodHandles;
import java.lang.module.ModuleDescriptor;
import java.security.Security;
import java.security.spec.EncodedKeySpec;
import java.util.ResourceBundle;
import java.util.concurrent.ForkJoinPool;
import java.util.jar.JarFile;
import java.io.Console;
import java.io.FileDescriptor;
import java.io.FilePermission;
import java.io.ObjectInputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.security.ProtectionDomain;
import java.security.Signature;
import javax.security.auth.x500.X500Principal;

/** A repository of "shared secrets", which are a mechanism for
    calling implementation-private methods in another package without
    using reflection. A package-private class implements a public
    interface and provides the ability to call package-private methods
    within that package; the object implementing that interface is
    provided through a third package to which access is restricted.
    This framework avoids the primary disadvantage of using reflection
    for this purpose, namely the loss of compile-time checking. */

public final class SharedSecrets {

    private SharedSecrets() {}

    private static final StableValue<JavaAWTAccess> javaAWTAccess = StableValue.of();
    private static final StableValue<JavaAWTFontAccess> javaAWTFontAccess = StableValue.of();
    private static final StableValue<JavaBeansAccess> javaBeansAccess = StableValue.of();
    private static final StableValue<JavaLangAccess> javaLangAccess = StableValue.of();
    private static final StableValue<JavaLangInvokeAccess> javaLangInvokeAccess = StableValue.of();
    private static final StableValue<JavaLangModuleAccess> javaLangModuleAccess = StableValue.of();
    private static final StableValue<JavaLangRefAccess> javaLangRefAccess = StableValue.of();
    private static final StableValue<JavaLangReflectAccess> javaLangReflectAccess = StableValue.of();
    private static final StableValue<JavaIOAccess> javaIOAccess = StableValue.of();
    private static final StableValue<JavaIOPrintStreamAccess> javaIOPrintStreamAccess = StableValue.of();
    private static final StableValue<JavaIOPrintWriterAccess> javaIOPrintWriterAccess = StableValue.of();
    private static final StableValue<JavaIOFileDescriptorAccess> javaIOFileDescriptorAccess = StableValue.of();
    private static final StableValue<JavaIOFilePermissionAccess> javaIOFilePermissionAccess = StableValue.of();
    private static final StableValue<JavaIORandomAccessFileAccess> javaIORandomAccessFileAccess = StableValue.of();
    private static final StableValue<JavaObjectInputStreamReadString> javaObjectInputStreamReadString = StableValue.of();
    private static final StableValue<JavaObjectInputStreamAccess> javaObjectInputStreamAccess = StableValue.of();
    private static final StableValue<JavaObjectInputFilterAccess> javaObjectInputFilterAccess = StableValue.of();
    private static final StableValue<JavaNetInetAddressAccess> javaNetInetAddressAccess = StableValue.of();
    private static final StableValue<JavaNetHttpCookieAccess> javaNetHttpCookieAccess = StableValue.of();
    private static final StableValue<JavaNetUriAccess> javaNetUriAccess = StableValue.of();
    private static final StableValue<JavaNetURLAccess> javaNetURLAccess = StableValue.of();
    private static final StableValue<JavaNioAccess> javaNioAccess = StableValue.of();
    private static final StableValue<JavaUtilCollectionAccess> javaUtilCollectionAccess = StableValue.of();
    private static final StableValue<JavaUtilConcurrentTLRAccess> javaUtilConcurrentTLRAccess = StableValue.of();
    private static final StableValue<JavaUtilConcurrentFJPAccess> javaUtilConcurrentFJPAccess = StableValue.of();
    private static final StableValue<JavaUtilJarAccess> javaUtilJarAccess = StableValue.of();
    private static final StableValue<JavaUtilZipFileAccess> javaUtilZipFileAccess = StableValue.of();
    private static final StableValue<JavaUtilResourceBundleAccess> javaUtilResourceBundleAccess = StableValue.of();
    private static final StableValue<JavaSecurityAccess> javaSecurityAccess = StableValue.of();
    private static final StableValue<JavaSecurityPropertiesAccess> javaSecurityPropertiesAccess = StableValue.of();
    private static final StableValue<JavaSecuritySignatureAccess> javaSecuritySignatureAccess = StableValue.of();
    private static final StableValue<JavaSecuritySpecAccess> javaSecuritySpecAccess = StableValue.of();
    private static final StableValue<JavaxCryptoSealedObjectAccess> javaxCryptoSealedObjectAccess = StableValue.of();
    private static final StableValue<JavaxCryptoSpecAccess> javaxCryptoSpecAccess = StableValue.of();
    private static final StableValue<JavaxSecurityAccess> javaxSecurityAccess = StableValue.of();

    public static void setJavaUtilCollectionAccess(JavaUtilCollectionAccess juca) {
        javaUtilCollectionAccess.setOrThrow(juca);
    }

    public static JavaUtilCollectionAccess getJavaUtilCollectionAccess() {
        var access = javaUtilCollectionAccess.getOrNull();
        if (access == null) {
            try {
                Class.forName("java.util.ImmutableCollections$Access", true, null);
                access = javaUtilCollectionAccess.getOrThrow();
            } catch (ClassNotFoundException e) {}
        }
        return access;
    }

    public static void setJavaUtilConcurrentTLRAccess(JavaUtilConcurrentTLRAccess access) {
        javaUtilConcurrentTLRAccess.setOrThrow(access);
    }

    public static JavaUtilConcurrentTLRAccess getJavaUtilConcurrentTLRAccess() {
        var access = javaUtilConcurrentTLRAccess.getOrNull();
        if (access == null) {
            try {
                Class.forName("java.util.concurrent.ThreadLocalRandom$Access", true, null);
                access = javaUtilConcurrentTLRAccess.getOrThrow();
            } catch (ClassNotFoundException e) {}
        }
        return access;
    }

    public static void setJavaUtilConcurrentFJPAccess(JavaUtilConcurrentFJPAccess access) {
        javaUtilConcurrentFJPAccess.setOrThrow(access);
    }

    public static JavaUtilConcurrentFJPAccess getJavaUtilConcurrentFJPAccess() {
        var access = javaUtilConcurrentFJPAccess.getOrNull();
        if (access == null) {
            ensureClassInitialized(ForkJoinPool.class);
            access = javaUtilConcurrentFJPAccess.getOrThrow();
        }
        return access;
    }

    public static JavaUtilJarAccess javaUtilJarAccess() {
        var access = javaUtilJarAccess.getOrNull();
        if (access == null) {
            // Ensure JarFile is initialized; we know that this class
            // provides the shared secret
            ensureClassInitialized(JarFile.class);
            access = javaUtilJarAccess.getOrThrow();
        }
        return access;
    }

    public static void setJavaUtilJarAccess(JavaUtilJarAccess access) {
        javaUtilJarAccess.setOrThrow(access);
    }

    public static void setJavaLangAccess(JavaLangAccess jla) {
        javaLangAccess.setOrThrow(jla);
    }

    public static JavaLangAccess getJavaLangAccess() {
        return javaLangAccess.getOrThrow();
    }

    public static void setJavaLangInvokeAccess(JavaLangInvokeAccess jlia) {
        javaLangInvokeAccess.setOrThrow(jlia);
    }

    public static JavaLangInvokeAccess getJavaLangInvokeAccess() {
        var access = javaLangInvokeAccess.getOrNull();
        if (access == null) {
            try {
                Class.forName("java.lang.invoke.MethodHandleImpl", true, null);
                access = javaLangInvokeAccess.getOrThrow();
            } catch (ClassNotFoundException e) {}
        }
        return access;
    }

    public static void setJavaLangModuleAccess(JavaLangModuleAccess jlrma) {
        javaLangModuleAccess.setOrThrow(jlrma);
    }

    public static JavaLangModuleAccess getJavaLangModuleAccess() {
        var access = javaLangModuleAccess.getOrNull();
        if (access == null) {
            ensureClassInitialized(ModuleDescriptor.class);
            access = javaLangModuleAccess.getOrThrow();
        }
        return access;
    }

    public static void setJavaLangRefAccess(JavaLangRefAccess jlra) {
        javaLangRefAccess.setOrThrow(jlra);
    }

    public static JavaLangRefAccess getJavaLangRefAccess() {
        return javaLangRefAccess.getOrThrow();
    }

    public static void setJavaLangReflectAccess(JavaLangReflectAccess jlra) {
        javaLangReflectAccess.setOrThrow(jlra);
    }

    public static JavaLangReflectAccess getJavaLangReflectAccess() {
        return javaLangReflectAccess.getOrThrow();
    }

    public static void setJavaNetUriAccess(JavaNetUriAccess jnua) {
        javaNetUriAccess.setOrThrow(jnua);
    }

    public static JavaNetUriAccess getJavaNetUriAccess() {
        var access = javaNetUriAccess.getOrNull();
        if (access == null) {
            ensureClassInitialized(java.net.URI.class);
            access = javaNetUriAccess.getOrThrow();
        }
        return access;
    }

    public static void setJavaNetURLAccess(JavaNetURLAccess jnua) {
        javaNetURLAccess.setOrThrow(jnua);
    }

    public static JavaNetURLAccess getJavaNetURLAccess() {
        var access = javaNetURLAccess.getOrNull();
        if (access == null) {
            ensureClassInitialized(java.net.URL.class);
            access = javaNetURLAccess.getOrThrow();
        }
        return access;
    }

    public static void setJavaNetInetAddressAccess(JavaNetInetAddressAccess jna) {
        javaNetInetAddressAccess.setOrThrow(jna);
    }

    public static JavaNetInetAddressAccess getJavaNetInetAddressAccess() {
        var access = javaNetInetAddressAccess.getOrNull();
        if (access == null) {
            ensureClassInitialized(java.net.InetAddress.class);
            access = javaNetInetAddressAccess.getOrThrow();
        }
        return access;
    }

    public static void setJavaNetHttpCookieAccess(JavaNetHttpCookieAccess a) {
        javaNetHttpCookieAccess.setOrThrow(a);
    }

    public static JavaNetHttpCookieAccess getJavaNetHttpCookieAccess() {
        var access = javaNetHttpCookieAccess.getOrNull();
        if (access == null) {
            ensureClassInitialized(java.net.HttpCookie.class);
            access = javaNetHttpCookieAccess.getOrThrow();
        }
        return access;
    }

    public static void setJavaNioAccess(JavaNioAccess jna) {
        javaNioAccess.setOrThrow(jna);
    }

    public static JavaNioAccess getJavaNioAccess() {
        var access = javaNioAccess.getOrNull();
        if (access == null) {
            // Ensure java.nio.Buffer is initialized, which provides the
            // shared secret.
            ensureClassInitialized(java.nio.Buffer.class);
            access = javaNioAccess.getOrThrow();
        }
        return access;
    }

    public static void setJavaIOAccess(JavaIOAccess jia) {
        javaIOAccess.setOrThrow(jia);
    }

    public static JavaIOAccess getJavaIOAccess() {
        var access = javaIOAccess.getOrNull();
        if (access == null) {
            ensureClassInitialized(Console.class);
            access = javaIOAccess.getOrThrow();
        }
        return access;
    }

    public static void setJavaIOCPrintWriterAccess(JavaIOPrintWriterAccess a) {
        javaIOPrintWriterAccess.setOrThrow(a);
    }

    public static JavaIOPrintWriterAccess getJavaIOPrintWriterAccess() {
        var access = javaIOPrintWriterAccess.getOrNull();
        if (access == null) {
            ensureClassInitialized(PrintWriter.class);
            access = javaIOPrintWriterAccess.getOrThrow();
        }
        return access;
    }

    public static void setJavaIOCPrintStreamAccess(JavaIOPrintStreamAccess a) {
        javaIOPrintStreamAccess.setOrThrow(a);
    }

    public static JavaIOPrintStreamAccess getJavaIOPrintStreamAccess() {
        var access = javaIOPrintStreamAccess.getOrNull();
        if (access == null) {
            ensureClassInitialized(PrintStream.class);
            access = javaIOPrintStreamAccess.getOrThrow();
        }
        return access;
    }

    public static void setJavaIOFileDescriptorAccess(JavaIOFileDescriptorAccess jiofda) {
        javaIOFileDescriptorAccess.setOrThrow(jiofda);
    }

    public static JavaIOFilePermissionAccess getJavaIOFilePermissionAccess() {
        var access = javaIOFilePermissionAccess.getOrNull();
        if (access == null) {
            ensureClassInitialized(FilePermission.class);
            access = javaIOFilePermissionAccess.getOrThrow();
        }
        return access;
    }

    public static void setJavaIOFilePermissionAccess(JavaIOFilePermissionAccess jiofpa) {
        javaIOFilePermissionAccess.setOrThrow(jiofpa);
    }

    public static JavaIOFileDescriptorAccess getJavaIOFileDescriptorAccess() {
        var access = javaIOFileDescriptorAccess.getOrNull();
        if (access == null) {
            ensureClassInitialized(FileDescriptor.class);
            access = javaIOFileDescriptorAccess.getOrThrow();
        }
        return access;
    }

    public static void setJavaSecurityAccess(JavaSecurityAccess jsa) {
        javaSecurityAccess.setOrThrow(jsa);
    }

    public static JavaSecurityAccess getJavaSecurityAccess() {
        var access = javaSecurityAccess.getOrNull();
        if (access == null) {
            ensureClassInitialized(ProtectionDomain.class);
            access = javaSecurityAccess.getOrThrow();
        }
        return access;
    }

    public static void setJavaSecurityPropertiesAccess(JavaSecurityPropertiesAccess jspa) {
        javaSecurityPropertiesAccess.setOrThrow(jspa);
    }

    public static JavaSecurityPropertiesAccess getJavaSecurityPropertiesAccess() {
        var access = javaSecurityPropertiesAccess.getOrNull();
        if (access == null) {
            ensureClassInitialized(Security.class);
            access = javaSecurityPropertiesAccess.getOrThrow();
        }
        return access;
    }

    public static JavaUtilZipFileAccess getJavaUtilZipFileAccess() {
        var access = javaUtilZipFileAccess.getOrNull();
        if (access == null) {
            ensureClassInitialized(java.util.zip.ZipFile.class);
            access = javaUtilZipFileAccess.getOrThrow();
        }
        return access;
    }

    public static void setJavaUtilZipFileAccess(JavaUtilZipFileAccess access) {
        javaUtilZipFileAccess.setOrThrow(access);
    }

    public static void setJavaAWTAccess(JavaAWTAccess jaa) {
        javaAWTAccess.trySet(jaa);
    }

    public static JavaAWTAccess getJavaAWTAccess() {
        // this may return null in which case calling code needs to
        // provision for.
        return javaAWTAccess.getOrNull();
    }

    public static void setJavaAWTFontAccess(JavaAWTFontAccess jafa) {
        javaAWTFontAccess.setOrThrow(jafa);
    }

    public static JavaAWTFontAccess getJavaAWTFontAccess() {
        // this may return null in which case calling code needs to
        // provision for.
        return javaAWTFontAccess.getOrNull();
    }

    public static JavaBeansAccess getJavaBeansAccess() {
        return javaBeansAccess.getOrNull();
    }

    public static void setJavaBeansAccess(JavaBeansAccess access) {
        javaBeansAccess.setOrThrow(access);
    }

    public static JavaUtilResourceBundleAccess getJavaUtilResourceBundleAccess() {
        var access = javaUtilResourceBundleAccess.getOrNull();
        if (access == null) {
            ensureClassInitialized(ResourceBundle.class);
            access = javaUtilResourceBundleAccess.getOrThrow();
        }
        return access;
    }

    public static void setJavaUtilResourceBundleAccess(JavaUtilResourceBundleAccess access) {
        javaUtilResourceBundleAccess.setOrThrow(access);
    }

    public static JavaObjectInputStreamReadString getJavaObjectInputStreamReadString() {
        var access = javaObjectInputStreamReadString.getOrNull();
        if (access == null) {
            ensureClassInitialized(ObjectInputStream.class);
            access = javaObjectInputStreamReadString.getOrThrow();
        }
        return access;
    }

    public static void setJavaObjectInputStreamReadString(JavaObjectInputStreamReadString access) {
        javaObjectInputStreamReadString.setOrThrow(access);
    }

    public static JavaObjectInputStreamAccess getJavaObjectInputStreamAccess() {
        var access = javaObjectInputStreamAccess.getOrNull();
        if (access == null) {
            ensureClassInitialized(ObjectInputStream.class);
            access = javaObjectInputStreamAccess.getOrThrow();
        }
        return access;
    }

    public static void setJavaObjectInputStreamAccess(JavaObjectInputStreamAccess access) {
        javaObjectInputStreamAccess.setOrThrow(access);
    }

    public static JavaObjectInputFilterAccess getJavaObjectInputFilterAccess() {
        var access = javaObjectInputFilterAccess.getOrNull();
        if (access == null) {
            ensureClassInitialized(ObjectInputFilter.Config.class);
            access = javaObjectInputFilterAccess.getOrThrow();
        }
        return access;
    }

    public static void setJavaObjectInputFilterAccess(JavaObjectInputFilterAccess access) {
        javaObjectInputFilterAccess.setOrThrow(access);
    }

    public static void setJavaIORandomAccessFileAccess(JavaIORandomAccessFileAccess jirafa) {
        javaIORandomAccessFileAccess.setOrThrow(jirafa);
    }

    public static JavaIORandomAccessFileAccess getJavaIORandomAccessFileAccess() {
        var access = javaIORandomAccessFileAccess.getOrNull();
        if (access == null) {
            ensureClassInitialized(RandomAccessFile.class);
            access = javaIORandomAccessFileAccess.getOrThrow();
        }
        return access;
    }

    public static void setJavaSecuritySignatureAccess(JavaSecuritySignatureAccess jssa) {
        javaSecuritySignatureAccess.setOrThrow(jssa);
    }

    public static JavaSecuritySignatureAccess getJavaSecuritySignatureAccess() {
        var access = javaSecuritySignatureAccess.getOrNull();
        if (access == null) {
            ensureClassInitialized(Signature.class);
            access = javaSecuritySignatureAccess.getOrThrow();
        }
        return access;
    }

    public static void setJavaSecuritySpecAccess(JavaSecuritySpecAccess jssa) {
        javaSecuritySpecAccess.setOrThrow(jssa);
    }

    public static JavaSecuritySpecAccess getJavaSecuritySpecAccess() {
        var access = javaSecuritySpecAccess.getOrNull();
        if (access == null) {
            ensureClassInitialized(EncodedKeySpec.class);
            access = javaSecuritySpecAccess.getOrThrow();
        }
        return access;
    }

    public static void setJavaxCryptoSpecAccess(JavaxCryptoSpecAccess jcsa) {
        javaxCryptoSpecAccess.setOrThrow(jcsa);
    }

    public static JavaxCryptoSpecAccess getJavaxCryptoSpecAccess() {
        var access = javaxCryptoSpecAccess.getOrNull();
        if (access == null) {
            ensureClassInitialized(SecretKeySpec.class);
            access = javaxCryptoSpecAccess.getOrThrow();
        }
        return access;
    }

    public static void setJavaxCryptoSealedObjectAccess(JavaxCryptoSealedObjectAccess jcsoa) {
        javaxCryptoSealedObjectAccess.setOrThrow(jcsoa);
    }

    public static JavaxCryptoSealedObjectAccess getJavaxCryptoSealedObjectAccess() {
        var access = javaxCryptoSealedObjectAccess.getOrNull();
        if (access == null) {
            ensureClassInitialized(SealedObject.class);
            access = javaxCryptoSealedObjectAccess.getOrThrow();
        }
        return access;
    }

    public static void setJavaxSecurityAccess(JavaxSecurityAccess jsa) {
        javaxSecurityAccess.setOrThrow(jsa);
    }

    public static JavaxSecurityAccess getJavaxSecurityAccess() {
        var access = javaxSecurityAccess.getOrNull();
        if (access == null) {
            ensureClassInitialized(X500Principal.class);
            access = javaxSecurityAccess.getOrThrow();
        }
        return access;
    }

    private static void ensureClassInitialized(Class<?> c) {
        try {
            MethodHandles.lookup().ensureInitialized(c);
        } catch (IllegalAccessException e) {}
    }
}
