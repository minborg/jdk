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
import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;

import javax.crypto.SealedObject;
import javax.crypto.spec.SecretKeySpec;
import java.io.ObjectInputFilter;
import java.lang.invoke.MethodHandles;
import java.lang.module.ModuleDescriptor;
import java.security.Security;
import java.security.spec.EncodedKeySpec;
import java.util.Map;
import java.util.NoSuchElementException;
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

    // This map holds all Access interfaces and their associations for creating an Access
    // implementation (if any).
    // Components with no initialization code use "" (an empty string) as initialization
    // Todo: If we use a mutable map, the entries can be removed when they have been used
    private static final Map<Class<?>, String> IMPLEMENTATIONS = Map.ofEntries(
            Map.entry(JavaLangAccess.class, ""),
            Map.entry(JavaUtilCollectionAccess.class, "java.util.ImmutableCollections$Access"),
            Map.entry(JavaUtilConcurrentFJPAccess.class, "java.util.concurrent.ForkJoinPool"),
            Map.entry(JavaUtilConcurrentTLRAccess.class, "java.util.concurrent.ThreadLocalRandom$Access"),
            Map.entry(JavaUtilJarAccess.class, "java.util.jar.JarFile"),
            Map.entry(JavaNetUriAccess.class, "java.net.URI"),
            Map.entry(JavaNetURLAccess.class, "java.net.URL"),
            Map.entry(JavaBeansAccess.class, ""),
            Map.entry(JavaLangInvokeAccess.class, "java.lang.invoke.MethodHandleImpl"),
            Map.entry(JavaLangModuleAccess.class, "java.lang.module.ModuleDescriptor"),
            Map.entry(JavaLangRefAccess.class, ""),
            Map.entry(JavaLangReflectAccess.class, ""),
            Map.entry(JavaIOAccess.class, "java.io.Console"),
            Map.entry(JavaIOFileDescriptorAccess.class, "java.io.FileDescriptor"),
            Map.entry(JavaIOFilePermissionAccess.class, "java.io.FilePermission"),
            Map.entry(JavaIORandomAccessFileAccess.class, "java.io.RandomAccessFile"),
            Map.entry(JavaObjectInputStreamReadString.class, "java.io.ObjectInputStream"),
            Map.entry(JavaObjectInputStreamAccess.class, "java.io.ObjectInputStream"),
            Map.entry(JavaObjectInputFilterAccess.class, "java.io.ObjectInputFilter$Config"),
            Map.entry(JavaObjectStreamReflectionAccess.class, "java.io.ObjectStreamReflection$Access"),
            Map.entry(JavaNetInetAddressAccess.class, "java.net.InetAddress"),
            Map.entry(JavaNetHttpCookieAccess.class, "java.net.HttpCookie"),
            Map.entry(JavaNioAccess.class, "java.nio.Buffer"),
            Map.entry(JavaUtilResourceBundleAccess.class, "java.util.ResourceBundle"),
            Map.entry(JavaSecurityPropertiesAccess.class, "java.security.Security"),
            Map.entry(JavaSecuritySignatureAccess.class, "java.security.Signature"),
            Map.entry(JavaSecuritySpecAccess.class, "java.security.spec.EncodedKeySpec"),
            Map.entry(JavaxCryptoSealedObjectAccess.class, "javax.crypto.SealedObject"),
            Map.entry(JavaxCryptoSpecAccess.class, "javax.crypto.spec.SecretKeySpec"),
            Map.entry(JavaxSecurityAccess.class, "javax.security.auth.x500.X500Principal"),
            Map.entry(JavaUtilZipFileAccess.class, "java.util.zip.ZipFile"),

            Map.entry(JavaAWTFontAccess.class, ""), // this Access may be null in which case calling code needs to provision for.
            Map.entry(JavaAWTAccess.class, "")      // this Access may be null in which case calling code needs to provision for.
    );
    // This container holds the actual Access components
    private static final StableHeterogeneousContainer COMPONENTS =
            StableValueFactories.ofHeterogeneousContainer(IMPLEMENTATIONS.keySet());

    public static <T> void putOrThrow(Class<T> type, T access) {
        COMPONENTS.putOrThrow(type, access);
    }

    @ForceInline
    public static <T> T getOrNull(Class<T> type) {
        final T access = COMPONENTS.get(type);
        return access == null
                ? getOrNullSlowPath(type)
                : access;
    }

    @DontInline
    public static <T> T getOrNullSlowPath(Class<T> type) {
        final String name = IMPLEMENTATIONS.get(type);
        if (name.isEmpty()) {
            // there is no impl (e.g. for JavaAWTAccess)
            return null;
        }
        try {
            Class.forName(name, true, null);
        } catch (ClassNotFoundException e) {
            throw new InternalError(e);
        }
        return COMPONENTS.get(type);
    }

    public static <T> T computeIfAbsent(Class<T> type,
                                        Function<? super Class<T>, ? extends T> mapper) {
        return COMPONENTS.computeIfAbsent(type, mapper);
    }

    public static <T> T getOrThrow(Class<T> type) {
        T t = getOrNull(type);
        if (t == null) {
            throw new NoSuchElementException("No instance of " + type);
        }
        return t;
    }

    // Closed compatibility: To be removed!

    static JavaUtilZipFileAccess getJavaUtilZipFileAccess() {
        return getOrThrow(JavaUtilZipFileAccess.class);
    }

    static void setJavaxCryptoSealedObjectAccess(JavaxCryptoSealedObjectAccess a) {
        putOrThrow(JavaxCryptoSealedObjectAccess.class, a);
    }

}
