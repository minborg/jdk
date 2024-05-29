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

import jdk.internal.lang.StableValues;

import java.lang.invoke.MethodHandles;
import java.lang.module.ModuleDescriptor;
import java.lang.reflect.Constructor;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.AbstractMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

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

    // Pros:
    // No coupling from implementing classes back to SharedSecrets
    // Concrete classes rather than anonymous classes
    // SharedSecrets becomes simpler and scalable
    // Lookups can be constant-folded so cached references can be removed (but consider warmup)
    // Most component types are not always cached in a private field with an Access component(s)
    // More granular control of class loading. (some classes can load later)
    // Less code in SharedSecrets
    // Some implementations used to be lambdas. Now they are concrete classes
    // No static initializers needed in classes holding an Access implementation
    // ServiceLoader-like pluggability of Access components

    // Cons:
    // Streams cannot be used as they require Access components
    // It is possible, severa instances of an Access component is created via races. Only one is elected though.
    // Hard to support imperative setting of a component via tests etc.

    // Notes:
    // Due to type-safety, sealed classes and internal checks, SharedSecret::get is guaranteed to always return an Access component
    // Make it possible to run Class:getPermittedSubclasses
    // Maybe consolidate access under ::get even though listeners might call back and report.

    /**
     * Marker interface for all Access types.
     */
    public sealed interface Access permits JavaAWTFontAccess, JavaBeansAccess, JavaIOAccess, JavaIOFileDescriptorAccess, JavaIOFilePermissionAccess, JavaIOPrintStreamAccess, JavaIOPrintWriterAccess, JavaIORandomAccessFileAccess, JavaNetHttpCookieAccess, JavaNetInetAddressAccess, JavaNetURLAccess, JavaNetUriAccess, JavaNioAccess, JavaObjectInputFilterAccess, JavaObjectInputStreamAccess, JavaObjectInputStreamReadString, JavaSecurityAccess, JavaSecurityPropertiesAccess, JavaSecuritySignatureAccess, JavaSecuritySpecAccess, JavaUtilCollectionAccess, JavaUtilConcurrentFJPAccess, JavaUtilConcurrentTLRAccess, JavaUtilJarAccess, JavaUtilResourceBundleAccess, JavaUtilZipFileAccess, JavaxCryptoSealedObjectAccess, JavaxCryptoSpecAccess, JavaxSecurityAccess {}

    public static <T extends Access> T get(Class<T> type) {
        try {
            return type.cast(
                    REPOSITORY.apply(type));
        } catch (Throwable t) {
            if (System.err != null) {
                // Todo: Remove this debug "feature"
                System.err.println(REPOSITORY);
                System.err.flush();
            }
            throw t;
        }
    }

    // Mappings from an Access component to its associated implementation
    private static final Map<Class<? extends Access>, String> LOOKUPS = Map.ofEntries(
            entry(JavaAWTFontAccess.class, "java.awt.font.JavaAWTFontAccessImpl"),
            entry(JavaBeansAccess.class, "java.beans.Introspector$JavaBeansAccessImpl"),
            entry(JavaNetInetAddressAccess.class, "java.net.InetAddress$JavaNetInetAddressAccessImpl"),
            entry(JavaNetHttpCookieAccess.class, "java.net.HttpCookie$JavaNetHttpCookieAccessImpl"),
            entry(JavaNetUriAccess.class, "java.net.URI$JavaNetUriAccessImpl"),
            entry(JavaNetURLAccess.class, "java.net.URL$JavaNetURLAccessImpl"),
            entry(JavaIOAccess.class, "java.io.Console$JavaIOAccessImpl"),
            entry(JavaIOFileDescriptorAccess.class, "java.io.FileDescriptor$JavaIOFileDescriptorAccessImpl"),
            entry(JavaIOFilePermissionAccess.class, "java.io.FilePermission$JavaIOFilePermissionAccessImpl"),
            entry(JavaIOPrintStreamAccess.class, "java.io.PrintStream$JavaIOPrintStreamAccessImpl"),
            entry(JavaIOPrintWriterAccess.class, "java.io.PrintWriter$JavaIOPrintWriterAccessImpl"),
            entry(JavaIORandomAccessFileAccess.class, "java.io.RandomAccessFile$JavaIORandomAccessFileAccessImpl"),
            entry(JavaNioAccess.class, "java.nio.Buffer$JavaNioAccessImpl"),
            entry(JavaObjectInputFilterAccess.class, "java.io.ObjectInputFilter$JavaObjectInputFilterAccessImpl"),
            entry(JavaObjectInputStreamReadString.class, "java.io.ObjectInputStream$JavaObjectInputStreamReadStringImpl"),
            entry(JavaObjectInputStreamAccess.class, "java.io.ObjectInputStream$JavaObjectInputStreamAccessImpl"),
            entry(JavaSecurityAccess.class , "java.security.ProtectionDomain$JavaSecurityAccessImpl"),
            entry(JavaSecurityPropertiesAccess.class, "java.security.Security$JavaSecurityPropertiesAccessImpl"),
            entry(JavaSecuritySignatureAccess.class, "java.security.Signature$JavaSecuritySignatureAccessImpl"),
            entry(JavaSecuritySpecAccess.class, "java.security.spec.EncodedKeySpec$JavaSecuritySpecAccessImpl"),
            entry(JavaUtilCollectionAccess.class, "java.util.ImmutableCollections$JavaUtilCollectionAccessImpl"),
            entry(JavaUtilConcurrentFJPAccess.class, "java.util.concurrent.ForkJoinPool$JavaUtilConcurrentFJPAccessImpl"),
            entry(JavaUtilConcurrentTLRAccess.class, "java.util.concurrent.ThreadLocalRandom$JavaUtilConcurrentTLRAccessImpl"),
            entry(JavaUtilJarAccess.class, "java.util.jar.JavaUtilJarAccessImpl"), // Outer class
            entry(JavaUtilResourceBundleAccess.class, "java.util.ResourceBundle$JavaUtilResourceBundleAccessImpl"),
            entry(JavaUtilZipFileAccess.class, "java.util.zip.ZipFile$JavaUtilZipFileAccessImpl"),
            entry(JavaxCryptoSealedObjectAccess.class, "javax.crypto.SealedObject$JavaxCryptoSealedObjectAccessImpl"),
            entry(JavaxCryptoSpecAccess.class, "javax.crypto.spec.SecretKeySpec$JavaxCryptoSpecAccessImpl"),
            entry(JavaxSecurityAccess.class, "javax.security.auth.x500.X500Principal$JavaxSecurityAccessImpl")
    );

    @SuppressWarnings("unchecked")
    private static final Set<Class<? extends Access>> ACCESS_CLASSES =
            (Set<Class<? extends Access>>) (Set<?>) Set.of(Access.class.getPermittedSubclasses());

    static {
        if (!LOOKUPS.keySet().equals(ACCESS_CLASSES)) {
            Set<Object> diff = new HashSet<>(ACCESS_CLASSES);
            diff.removeAll(LOOKUPS.keySet());
            throw new AssertionError("Missing mappings for " + diff);
        }
    }

    private static final Function<Class<? extends Access>, Access> REPOSITORY =
            StableValues.memoizedFunction(ACCESS_CLASSES,
            new Function<Class<? extends Access>, Access>() {
                @Override
                public Access apply(Class<? extends Access> type) {
                    String lookup = LOOKUPS.get(type);
                    try {
                        Class<?> c = Class.forName(lookup, true, null);
                        Constructor<?> constructor = c.getDeclaredConstructor();
                        PrivilegedAction<Void> action = new PrivilegedAction<>() {
                            @Override
                            public Void run() {
                                constructor.setAccessible(true);
                                return null;
                            }
                        };
                        @SuppressWarnings("removal")
                        var _ = AccessController.doPrivileged(action);

                        // Make sure we use an instance of the correct type
                        return type.cast(constructor.newInstance());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
    );

    static <K, V> Map.Entry<K, V> entry(K key, V value) {
        return new AbstractMap.SimpleImmutableEntry<>(key, value);
    }

    private static JavaAWTAccess javaAWTAccess;
    private static JavaLangAccess javaLangAccess;
    private static JavaLangInvokeAccess javaLangInvokeAccess;
    private static JavaLangModuleAccess javaLangModuleAccess;
    private static JavaLangRefAccess javaLangRefAccess;
    private static JavaLangReflectAccess javaLangReflectAccess;

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

    public static void setJavaLangRefAccess(JavaLangRefAccess jlra) {
        javaLangRefAccess = jlra;
    }

    public static JavaLangRefAccess getJavaLangRefAccess() {
        return javaLangRefAccess;
    }

    public static void setJavaLangReflectAccess(JavaLangReflectAccess jlra) {
        javaLangReflectAccess = jlra;
    }

    public static JavaLangReflectAccess getJavaLangReflectAccess() {
        return javaLangReflectAccess;
    }

    public static void setJavaAWTAccess(JavaAWTAccess jaa) {
        javaAWTAccess = jaa;
    }

    public static JavaAWTAccess getJavaAWTAccess() {
        // this may return null in which case calling code needs to
        // provision for.
        return javaAWTAccess;
    }

    private static void ensureClassInitialized(Class<?> c) {
        try {
            MethodHandles.lookup().ensureInitialized(c);
        } catch (IllegalAccessException e) {}
    }
}
