package jdk.internal.access;

/**
 * Marker interface for all the Access components.
 */
public sealed interface Access permits
        JavaAWTFontAccess,
        JavaBeansAccess,
        JavaIOAccess,
        JavaIOFileDescriptorAccess,
        JavaIORandomAccessFileAccess,
        JavaLangAccess,
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
