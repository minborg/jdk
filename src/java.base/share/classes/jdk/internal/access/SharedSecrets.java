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
import java.lang.reflect.Constructor;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.AbstractMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static jdk.internal.access.SharedSecrets.Provider.*;

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
     * Marker interface for all the Access component types.
     */
    public sealed interface Access permits
            JavaAWTFontAccess, JavaBeansAccess, JavaIOAccess, JavaIOFileDescriptorAccess,
            JavaIOFilePermissionAccess, JavaIOPrintStreamAccess, JavaIOPrintWriterAccess,
            JavaIORandomAccessFileAccess, JavaLangAccess, JavaLangInvokeAccess,
            JavaLangModuleAccess, JavaLangRefAccess, JavaLangReflectAccess,
            JavaNetHttpCookieAccess, JavaNetInetAddressAccess, JavaNetURLAccess,
            JavaNetUriAccess, JavaNioAccess, JavaObjectInputFilterAccess,
            JavaObjectInputStreamAccess, JavaObjectInputStreamReadString,
            JavaSecurityAccess, JavaSecurityPropertiesAccess, JavaSecuritySignatureAccess,
            JavaSecuritySpecAccess, JavaUtilCollectionAccess, JavaUtilConcurrentFJPAccess,
            JavaUtilConcurrentTLRAccess, JavaUtilJarAccess, JavaUtilResourceBundleAccess,
            JavaUtilZipFileAccess, JavaxCryptoSealedObjectAccess, JavaxCryptoSpecAccess,
            JavaxSecurityAccess {}

    /**
     * {@return an implementation for the provided {@code accessComponent}}
     * <p>
     * The backing write-once-per-key repository can be constant folded by the VM.
     *
     * @param accessComponent for which an implementation shall be retrieved
     * @param <T> type of the provided {@code accessComponent}
     */
    public static <T extends Access> T get(Class<T> accessComponent) {
        try {
            return accessComponent.cast(
                    REPOSITORY.apply(accessComponent));
        } catch (Throwable t) {
            if (System.err != null) {
                // Todo: Remove this debug "feature"
                System.err.println("SharedSecrets::get, Unable to get " + accessComponent);
                System.err.println(REPOSITORY);
                System.err.flush();
            }
            throw new NoSuchElementException("Unable to get " + accessComponent + " from " + REPOSITORY, t);
        }
    }

    sealed interface Provider<R extends Access> {
        R get();
        Class<R> type();

        record ByName<R extends Access>(@Override Class<R> type,
                                        String name) implements Provider<R> {

            @Override
            public R get() {
                try {
                    @SuppressWarnings("unchecked")
                    Class<R> c = (Class<R>) classForName(name);
                    Constructor<R> constructor = c.getDeclaredConstructor();
                    PrivilegedAction<Void> action = new PrivilegedAction<>() {
                        @Override
                        public Void run() {
                            constructor.setAccessible(true);
                            return null;
                        }
                    };
                    @SuppressWarnings("removal")
                    var _ = AccessController.doPrivileged(action);

                    R val = constructor.newInstance();
                    // Make sure we use an instance of the correct type
                    return type.cast(val);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        record BySupplier<R extends Access>(@Override Class<R> type,
                                            String holder,
                                            Supplier<? extends R> supplier) implements Provider<R> {
            @Override
            public R get() {
                if (holder != null) {
                    var _ = classForName(holder);
                }
                return type.cast(
                        supplier.get()
                );
            }
        }

        static <R extends Access> Provider<R> byName(Class<R> type, String name) {
            return new ByName<>(type,name);
        }

        static <R extends Access> Provider<R> bySupplier(Class<R> type,
                                                         String holder,
                                                         Supplier<? extends R> supplier) {
            return new BySupplier<>(type, holder, supplier);
        }

    }

    // Mappings from an Access component to its associated implementation Provider
    private static final Map<Class<? extends Access>, Provider<?>> LOOKUPS = Map.ofEntries(
            entryFrom(byName(JavaAWTFontAccess.class, "java.awt.font.JavaAWTFontAccessImpl")),
            entryFrom(byName(JavaBeansAccess.class, "java.beans.Introspector$JavaBeansAccessImpl")),
            entryFrom(byName(JavaNetInetAddressAccess.class, "java.net.InetAddress$JavaNetInetAddressAccessImpl")),
            entryFrom(byName(JavaNetHttpCookieAccess.class,"java.net.HttpCookie$JavaNetHttpCookieAccessImpl")),
            entryFrom(byName(JavaNetUriAccess.class,"java.net.URI$JavaNetUriAccessImpl")),
            entryFrom(byName(JavaNetURLAccess.class, "java.net.URL$JavaNetURLAccessImpl")),
            entryFrom(byName(JavaIOAccess.class, "java.io.Console$JavaIOAccessImpl")),
            entryFrom(byName(JavaIOFileDescriptorAccess.class, "java.io.FileDescriptor$JavaIOFileDescriptorAccessImpl")),
            entryFrom(byName(JavaIOFilePermissionAccess.class,"java.io.FilePermission$JavaIOFilePermissionAccessImpl")),
            entryFrom(byName(JavaIOPrintStreamAccess.class,"java.io.PrintStream$JavaIOPrintStreamAccessImpl")),
            entryFrom(byName(JavaIOPrintWriterAccess.class, "java.io.PrintWriter$JavaIOPrintWriterAccessImpl")),
            entryFrom(byName(JavaIORandomAccessFileAccess.class, "java.io.RandomAccessFile$JavaIORandomAccessFileAccessImpl")),
            entryFrom(byName(JavaLangModuleAccess.class, "java.lang.module.ModuleDescriptor$JavaLangModuleAccessImpl")),
            entryFrom(bySupplier(JavaLangReflectAccess.class, "java.lang.reflect.AccessibleObject", new Supplier<>() {
                @Override  public JavaLangReflectAccess get() { return javaLangReflectAccess; }
            })),
            entryFrom(byName(JavaLangRefAccess.class, "java.lang.ref.Reference$JavaLangRefAccessImpl")),
            entryFrom(bySupplier(JavaLangInvokeAccess.class, "java.lang.invoke.MethodHandleImpl", new Supplier<>() {
                @Override  public JavaLangInvokeAccess get() { return javaLangInvokeAccess; }
            })),
            entryFrom(byName(JavaNioAccess.class, "java.nio.Buffer$JavaNioAccessImpl")),
            entryFrom(byName(JavaObjectInputFilterAccess.class, "java.io.ObjectInputFilter$Config$JavaObjectInputFilterAccessImpl")),
            entryFrom(byName(JavaObjectInputStreamReadString.class, "java.io.ObjectInputStream$JavaObjectInputStreamReadStringImpl")),
            entryFrom(byName(JavaObjectInputStreamAccess.class, "java.io.ObjectInputStream$JavaObjectInputStreamAccessImpl")),
            entryFrom(bySupplier(JavaLangAccess.class,null, new Supplier<>() {
                @Override  public JavaLangAccess get() { return javaLangAccess; }
            })),
            entryFrom(byName(JavaSecurityAccess.class ,"java.security.ProtectionDomain$JavaSecurityAccessImpl")),
            entryFrom(byName(JavaSecurityPropertiesAccess.class, "java.security.Security$JavaSecurityPropertiesAccessImpl")),
            entryFrom(byName(JavaSecuritySignatureAccess.class, "java.security.Signature$JavaSecuritySignatureAccessImpl")),
            entryFrom(byName(JavaSecuritySpecAccess.class,"java.security.spec.EncodedKeySpec$JavaSecuritySpecAccessImpl")),
            entryFrom(byName(JavaUtilCollectionAccess.class, "java.util.ImmutableCollections$JavaUtilCollectionAccessImpl")),
            entryFrom(byName(JavaUtilConcurrentFJPAccess.class,"java.util.concurrent.ForkJoinPool$JavaUtilConcurrentFJPAccessImpl")),
            entryFrom(byName(JavaUtilConcurrentTLRAccess.class, "java.util.concurrent.ThreadLocalRandom$JavaUtilConcurrentTLRAccessImpl")),
            entryFrom(byName(JavaUtilJarAccess.class, "java.util.jar.JavaUtilJarAccessImpl")), // Outer class
            entryFrom(byName(JavaUtilResourceBundleAccess.class, "java.util.ResourceBundle$JavaUtilResourceBundleAccessImpl")),
            entryFrom(byName(JavaUtilZipFileAccess.class,"java.util.zip.ZipFile$JavaUtilZipFileAccessImpl")),
            // The JavaxCryptoSealedObjectAccess will take a snapshot of the current value and intern the current value
            entryFrom(bySupplier(JavaxCryptoSealedObjectAccess.class, null, new Supplier<>() {
                @Override  public JavaxCryptoSealedObjectAccess get() { return javaxCryptoSealedObjectAccess; }
            })),
            //entry(JavaxCryptoSealedObjectAccess.class, new Provider.ByName("javax.crypto.SealedObject$JavaxCryptoSealedObjectAccessImpl")),
            entryFrom(byName(JavaxCryptoSpecAccess.class,"javax.crypto.spec.SecretKeySpec$JavaxCryptoSpecAccessImpl")),
            entryFrom(byName(JavaxSecurityAccess.class, "javax.security.auth.x500.X500Principal$JavaxSecurityAccessImpl"))
    );

    @SuppressWarnings("unchecked")
    private static final Set<Class<? extends Access>> ACCESS_CLASSES =
            (Set<Class<? extends Access>>) (Set<?>) Set.of(Access.class.getPermittedSubclasses());

    static {
        // Make sure we have mapped all the permitted classes of Access
        if (!LOOKUPS.keySet().equals(ACCESS_CLASSES)) {
            Set<Object> diff = new HashSet<>(ACCESS_CLASSES);
            diff.removeAll(LOOKUPS.keySet());
            throw new AssertionError("Missing mappings for " + diff);
        }
    }

    private static final Function<Class<? extends Access>, Access> REPOSITORY =
            StableValues.memoizedFunction(ACCESS_CLASSES,
            new Function<>() {
                @Override
                public Access apply(Class<? extends Access> type) {
                    Provider<?> provider = LOOKUPS.get(type);
                    try {
                        if (!provider.type().equals(type)) {
                            throw new IllegalArgumentException("The type " + type + " is not reflected by " + provider);
                        }
                        // Make sure we use an instance of the correct type
                        return type.cast(provider.get());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
    );

    static <T extends Access, V extends Provider<T>> Map.Entry<Class<T>, V> entryFrom(V value) {
        return new AbstractMap.SimpleImmutableEntry<>(value.type(), value);
    }

    // Callback fields that will be delegated to Providers.
    // These are needed very early in the boot sequence.
    private static JavaLangAccess javaLangAccess;
    private static JavaLangReflectAccess javaLangReflectAccess;
    private static JavaLangInvokeAccess javaLangInvokeAccess;

    // These fields are settable in various tests.
    // Because of that, we use these fields as snapshot sources
    private static JavaAWTAccess javaAWTAccess;
    private static JavaxCryptoSealedObjectAccess javaxCryptoSealedObjectAccess;

    public static void setJavaLangAccess(JavaLangAccess jla) {
        javaLangAccess = jla;
    }

    public static void setJavaLangReflectAccess(JavaLangReflectAccess jlra) {
        javaLangReflectAccess = jlra;
    }

    public static void setJavaLangInvokeAccess(JavaLangInvokeAccess jlia) {
        javaLangInvokeAccess = jlia;
    }


    public static void setJavaAWTAccess(JavaAWTAccess jaa) {
        javaAWTAccess = jaa;
    }

    public static JavaAWTAccess getJavaAWTAccess() {
        // this may return null in which case calling code needs to
        // provision for.
        return javaAWTAccess;
    }

    // Set by some tests
    public static void setJavaxCryptoSealedObjectAccess(JavaxCryptoSealedObjectAccess javaxCryptoSealedObjectAccess) {
        SharedSecrets.javaxCryptoSealedObjectAccess = javaxCryptoSealedObjectAccess;
    }

    private static Class<?> classForName(String name) {
        try {
            return Class.forName(name, true, null);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static void ensureClassInitialized(Class<?> c) {
        try {
            MethodHandles.lookup().ensureInitialized(c);
        } catch (IllegalAccessException e) {}
    }

    // Compatibility methods that can be removed once the `closed` repo is updated
    public static JavaUtilZipFileAccess getJavaUtilZipFileAccess() { return get(JavaUtilZipFileAccess.class); }

}
