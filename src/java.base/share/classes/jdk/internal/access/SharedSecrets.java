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
        javaUtilCollectionAccess.trySet(juca);
    }

    public static JavaUtilCollectionAccess getJavaUtilCollectionAccess() {
        var access = javaUtilCollectionAccess.orElseNull();
        if (access == null) {
            try {
                Class.forName("java.util.ImmutableCollections$Access", true, null);
                access = javaUtilCollectionAccess.orElseThrow();
            } catch (ClassNotFoundException e) {}
        }
        return access;
    }

    public static void setJavaUtilConcurrentTLRAccess(JavaUtilConcurrentTLRAccess access) {
        javaUtilConcurrentTLRAccess.trySet(access);
    }

    public static JavaUtilConcurrentTLRAccess getJavaUtilConcurrentTLRAccess() {
        var access = javaUtilConcurrentTLRAccess.orElseNull();
        if (access == null) {
            try {
                Class.forName("java.util.concurrent.ThreadLocalRandom$Access", true, null);
                access = javaUtilConcurrentTLRAccess.orElseThrow();
            } catch (ClassNotFoundException e) {}
        }
        return access;
    }

    public static void setJavaUtilConcurrentFJPAccess(JavaUtilConcurrentFJPAccess access) {
        javaUtilConcurrentFJPAccess.trySet(access);
    }

    public static JavaUtilConcurrentFJPAccess getJavaUtilConcurrentFJPAccess() {
        var access = javaUtilConcurrentFJPAccess.orElseNull();
        if (access == null) {
            ensureClassInitialized(ForkJoinPool.class);
            access = javaUtilConcurrentFJPAccess.orElseThrow();
        }
        return access;
    }

    public static JavaUtilJarAccess javaUtilJarAccess() {
        var access = javaUtilJarAccess.orElseNull();
        if (access == null) {
            // Ensure JarFile is initialized; we know that this class
            // provides the shared secret
            ensureClassInitialized(JarFile.class);
            access = javaUtilJarAccess.orElseThrow();
        }
        return access;
    }

    public static void setJavaUtilJarAccess(JavaUtilJarAccess access) {
        javaUtilJarAccess.trySet(access);
    }

    public static void setJavaLangAccess(JavaLangAccess jla) {
        javaLangAccess.trySet(jla);
    }

    public static JavaLangAccess getJavaLangAccess() {
        return javaLangAccess.orElseThrow();
    }

    public static void setJavaLangInvokeAccess(JavaLangInvokeAccess jlia) {
        javaLangInvokeAccess.trySet(jlia);
    }

    public static JavaLangInvokeAccess getJavaLangInvokeAccess() {
        var access = javaLangInvokeAccess.orElseNull();
        if (access == null) {
            try {
                Class.forName("java.lang.invoke.MethodHandleImpl", true, null);
                access = javaLangInvokeAccess.orElseThrow();
            } catch (ClassNotFoundException e) {}
        }
        return access;
    }

    public static void setJavaLangModuleAccess(JavaLangModuleAccess jlrma) {
        javaLangModuleAccess.trySet(jlrma);
    }

    public static JavaLangModuleAccess getJavaLangModuleAccess() {
        var access = javaLangModuleAccess.orElseNull();
        if (access == null) {
            ensureClassInitialized(ModuleDescriptor.class);
            access = javaLangModuleAccess.orElseThrow();
        }
        return access;
    }

    public static void setJavaLangRefAccess(JavaLangRefAccess jlra) {
        javaLangRefAccess.trySet(jlra);
    }

    public static JavaLangRefAccess getJavaLangRefAccess() {
        return javaLangRefAccess.orElseThrow();
    }

    public static void setJavaLangReflectAccess(JavaLangReflectAccess jlra) {
        javaLangReflectAccess.trySet(jlra);
    }

    public static JavaLangReflectAccess getJavaLangReflectAccess() {
        return javaLangReflectAccess.orElseThrow();
    }

    public static void setJavaNetUriAccess(JavaNetUriAccess jnua) {
        javaNetUriAccess.trySet(jnua);
    }

    public static JavaNetUriAccess getJavaNetUriAccess() {
        var access = javaNetUriAccess.orElseNull();
        if (access == null) {
            ensureClassInitialized(java.net.URI.class);
            access = javaNetUriAccess.orElseThrow();
        }
        return access;
    }

    public static void setJavaNetURLAccess(JavaNetURLAccess jnua) {
        javaNetURLAccess.trySet(jnua);
    }

    public static JavaNetURLAccess getJavaNetURLAccess() {
        var access = javaNetURLAccess.orElseNull();
        if (access == null) {
            ensureClassInitialized(java.net.URL.class);
            access = javaNetURLAccess.orElseThrow();
        }
        return access;
    }

    public static void setJavaNetInetAddressAccess(JavaNetInetAddressAccess jna) {
        javaNetInetAddressAccess.trySet(jna);
    }

    public static JavaNetInetAddressAccess getJavaNetInetAddressAccess() {
        var access = javaNetInetAddressAccess.orElseNull();
        if (access == null) {
            ensureClassInitialized(java.net.InetAddress.class);
            access = javaNetInetAddressAccess.orElseThrow();
        }
        return access;
    }

    public static void setJavaNetHttpCookieAccess(JavaNetHttpCookieAccess a) {
        javaNetHttpCookieAccess.trySet(a);
    }

    public static JavaNetHttpCookieAccess getJavaNetHttpCookieAccess() {
        var access = javaNetHttpCookieAccess.orElseNull();
        if (access == null) {
            ensureClassInitialized(java.net.HttpCookie.class);
            access = javaNetHttpCookieAccess.orElseThrow();
        }
        return access;
    }

    public static void setJavaNioAccess(JavaNioAccess jna) {
        javaNioAccess.trySet(jna);
    }

    public static JavaNioAccess getJavaNioAccess() {
        var access = javaNioAccess.orElseNull();
        if (access == null) {
            // Ensure java.nio.Buffer is initialized, which provides the
            // shared secret.
            ensureClassInitialized(java.nio.Buffer.class);
            access = javaNioAccess.orElseThrow();
        }
        return access;
    }

    public static void setJavaIOAccess(JavaIOAccess jia) {
        javaIOAccess.trySet(jia);
    }

    public static JavaIOAccess getJavaIOAccess() {
        var access = javaIOAccess.orElseNull();
        if (access == null) {
            ensureClassInitialized(Console.class);
            access = javaIOAccess.orElseThrow();
        }
        return access;
    }

    public static void setJavaIOCPrintWriterAccess(JavaIOPrintWriterAccess a) {
        javaIOPrintWriterAccess.trySet(a);
    }

    public static JavaIOPrintWriterAccess getJavaIOPrintWriterAccess() {
        var access = javaIOPrintWriterAccess.orElseNull();
        if (access == null) {
            ensureClassInitialized(PrintWriter.class);
            access = javaIOPrintWriterAccess.orElseThrow();
        }
        return access;
    }

    public static void setJavaIOCPrintStreamAccess(JavaIOPrintStreamAccess a) {
        javaIOPrintStreamAccess.trySet(a);
    }

    public static JavaIOPrintStreamAccess getJavaIOPrintStreamAccess() {
        var access = javaIOPrintStreamAccess.orElseNull();
        if (access == null) {
            ensureClassInitialized(PrintStream.class);
            access = javaIOPrintStreamAccess.orElseThrow();
        }
        return access;
    }

    public static void setJavaIOFileDescriptorAccess(JavaIOFileDescriptorAccess jiofda) {
        javaIOFileDescriptorAccess.trySet(jiofda);
    }

    public static JavaIOFilePermissionAccess getJavaIOFilePermissionAccess() {
        var access = javaIOFilePermissionAccess.orElseNull();
        if (access == null) {
            ensureClassInitialized(FilePermission.class);
            access = javaIOFilePermissionAccess.orElseThrow();
        }
        return access;
    }

    public static void setJavaIOFilePermissionAccess(JavaIOFilePermissionAccess jiofpa) {
        javaIOFilePermissionAccess.trySet(jiofpa);
    }

    public static JavaIOFileDescriptorAccess getJavaIOFileDescriptorAccess() {
        var access = javaIOFileDescriptorAccess.orElseNull();
        if (access == null) {
            ensureClassInitialized(FileDescriptor.class);
            access = javaIOFileDescriptorAccess.orElseThrow();
        }
        return access;
    }

    public static void setJavaSecurityAccess(JavaSecurityAccess jsa) {
        javaSecurityAccess.trySet(jsa);
    }

    public static JavaSecurityAccess getJavaSecurityAccess() {
        var access = javaSecurityAccess.orElseNull();
        if (access == null) {
            ensureClassInitialized(ProtectionDomain.class);
            access = javaSecurityAccess.orElseThrow();
        }
        return access;
    }

    public static void setJavaSecurityPropertiesAccess(JavaSecurityPropertiesAccess jspa) {
        javaSecurityPropertiesAccess.trySet(jspa);
    }

    public static JavaSecurityPropertiesAccess getJavaSecurityPropertiesAccess() {
        var access = javaSecurityPropertiesAccess.orElseNull();
        if (access == null) {
            ensureClassInitialized(Security.class);
            access = javaSecurityPropertiesAccess.orElseThrow();
        }
        return access;
    }

    public static JavaUtilZipFileAccess getJavaUtilZipFileAccess() {
        var access = javaUtilZipFileAccess.orElseNull();
        if (access == null) {
            ensureClassInitialized(java.util.zip.ZipFile.class);
            access = javaUtilZipFileAccess.orElseThrow();
        }
        return access;
    }

    public static void setJavaUtilZipFileAccess(JavaUtilZipFileAccess access) {
        javaUtilZipFileAccess.trySet(access);
    }

    public static void setJavaAWTAccess(JavaAWTAccess jaa) {
        javaAWTAccess.trySet(jaa);
    }

    public static JavaAWTAccess getJavaAWTAccess() {
        // this may return null in which case calling code needs to
        // provision for.
        return javaAWTAccess.orElseNull();
    }

    public static void setJavaAWTFontAccess(JavaAWTFontAccess jafa) {
        javaAWTFontAccess.trySet(jafa);
    }

    public static JavaAWTFontAccess getJavaAWTFontAccess() {
        // this may return null in which case calling code needs to
        // provision for.
        return javaAWTFontAccess.orElseNull();
    }

    public static JavaBeansAccess getJavaBeansAccess() {
        return javaBeansAccess.orElseNull();
    }

    public static void setJavaBeansAccess(JavaBeansAccess access) {
        javaBeansAccess.trySet(access);
    }

    public static JavaUtilResourceBundleAccess getJavaUtilResourceBundleAccess() {
        var access = javaUtilResourceBundleAccess.orElseNull();
        if (access == null) {
            ensureClassInitialized(ResourceBundle.class);
            access = javaUtilResourceBundleAccess.orElseThrow();
        }
        return access;
    }

    public static void setJavaUtilResourceBundleAccess(JavaUtilResourceBundleAccess access) {
        javaUtilResourceBundleAccess.trySet(access);
    }

    public static JavaObjectInputStreamReadString getJavaObjectInputStreamReadString() {
        var access = javaObjectInputStreamReadString.orElseNull();
        if (access == null) {
            ensureClassInitialized(ObjectInputStream.class);
            access = javaObjectInputStreamReadString.orElseThrow();
        }
        return access;
    }

    public static void setJavaObjectInputStreamReadString(JavaObjectInputStreamReadString access) {
        javaObjectInputStreamReadString.trySet(access);
    }

    public static JavaObjectInputStreamAccess getJavaObjectInputStreamAccess() {
        var access = javaObjectInputStreamAccess.orElseNull();
        if (access == null) {
            ensureClassInitialized(ObjectInputStream.class);
            access = javaObjectInputStreamAccess.orElseThrow();
        }
        return access;
    }

    public static void setJavaObjectInputStreamAccess(JavaObjectInputStreamAccess access) {
        javaObjectInputStreamAccess.trySet(access);
    }

    public static JavaObjectInputFilterAccess getJavaObjectInputFilterAccess() {
        var access = javaObjectInputFilterAccess.orElseNull();
        if (access == null) {
            ensureClassInitialized(ObjectInputFilter.Config.class);
            access = javaObjectInputFilterAccess.orElseThrow();
        }
        return access;
    }

    public static void setJavaObjectInputFilterAccess(JavaObjectInputFilterAccess access) {
        javaObjectInputFilterAccess.trySet(access);
    }

    public static void setJavaIORandomAccessFileAccess(JavaIORandomAccessFileAccess jirafa) {
        javaIORandomAccessFileAccess.trySet(jirafa);
    }

    public static JavaIORandomAccessFileAccess getJavaIORandomAccessFileAccess() {
        var access = javaIORandomAccessFileAccess.orElseNull();
        if (access == null) {
            ensureClassInitialized(RandomAccessFile.class);
            access = javaIORandomAccessFileAccess.orElseThrow();
        }
        return access;
    }

    public static void setJavaSecuritySignatureAccess(JavaSecuritySignatureAccess jssa) {
        javaSecuritySignatureAccess.trySet(jssa);
    }

    public static JavaSecuritySignatureAccess getJavaSecuritySignatureAccess() {
        var access = javaSecuritySignatureAccess.orElseNull();
        if (access == null) {
            ensureClassInitialized(Signature.class);
            access = javaSecuritySignatureAccess.orElseThrow();
        }
        return access;
    }

    public static void setJavaSecuritySpecAccess(JavaSecuritySpecAccess jssa) {
        javaSecuritySpecAccess.trySet(jssa);
    }

    public static JavaSecuritySpecAccess getJavaSecuritySpecAccess() {
        var access = javaSecuritySpecAccess.orElseNull();
        if (access == null) {
            ensureClassInitialized(EncodedKeySpec.class);
            access = javaSecuritySpecAccess.orElseThrow();
        }
        return access;
    }

    public static void setJavaxCryptoSpecAccess(JavaxCryptoSpecAccess jcsa) {
        javaxCryptoSpecAccess.trySet(jcsa);
    }

    public static JavaxCryptoSpecAccess getJavaxCryptoSpecAccess() {
        var access = javaxCryptoSpecAccess.orElseNull();
        if (access == null) {
            ensureClassInitialized(SecretKeySpec.class);
            access = javaxCryptoSpecAccess.orElseThrow();
        }
        return access;
    }

    public static void setJavaxCryptoSealedObjectAccess(JavaxCryptoSealedObjectAccess jcsoa) {
        javaxCryptoSealedObjectAccess.trySet(jcsoa);
    }

    public static JavaxCryptoSealedObjectAccess getJavaxCryptoSealedObjectAccess() {
        var access = javaxCryptoSealedObjectAccess.orElseNull();
        if (access == null) {
            ensureClassInitialized(SealedObject.class);
            access = javaxCryptoSealedObjectAccess.orElseThrow();
        }
        return access;
    }

    public static void setJavaxSecurityAccess(JavaxSecurityAccess jsa) {
        javaxSecurityAccess.trySet(jsa);
    }

    public static JavaxSecurityAccess getJavaxSecurityAccess() {
        var access = javaxSecurityAccess.orElseNull();
        if (access == null) {
            ensureClassInitialized(X500Principal.class);
            access = javaxSecurityAccess.orElseThrow();
        }
        return access;
    }

    private static void ensureClassInitialized(Class<?> c) {
        try {
            MethodHandles.lookup().ensureInitialized(c);
        } catch (IllegalAccessException e) {}
    }
}
