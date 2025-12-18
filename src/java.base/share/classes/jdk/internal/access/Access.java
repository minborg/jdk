package jdk.internal.access;

/**
 * Marker interface for all the Access components.
 */
public sealed interface Access permits
        JavaBeansAccess,
        JavaIOAccess,
        JavaIOFileDescriptorAccess,
        JavaIORandomAccessFileAccess,
        JavaLangInvokeAccess,
        JavaLangModuleAccess,
        JavaLangRefAccess,
        JavaLangReflectAccess,
        JavaNetHttpCookieAccess,
        JavaNetInetAddressAccess,
        JavaNetURLAccess,
        JavaNetUriAccess,
        JavaNioAccess,
        JavaObjectInputFilterAccess,
        JavaObjectInputStreamAccess,
        JavaObjectInputStreamReadString,
        JavaObjectStreamReflectionAccess,
        JavaSecurityPropertiesAccess,
        JavaSecuritySignatureAccess,
        JavaSecuritySpecAccess,
        JavaUtilCollectionAccess,
        JavaUtilConcurrentFJPAccess,
        JavaUtilConcurrentTLRAccess,
        JavaUtilJarAccess,
        JavaUtilResourceBundleAccess,
        JavaUtilZipFileAccess,
        JavaxCryptoSealedObjectAccess,
        JavaxCryptoSpecAccess,
        JavaxSecurityAccess { }
