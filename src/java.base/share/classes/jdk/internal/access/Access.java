package jdk.internal.access;

/**
 * Marker interface for all the Access classes.
 */
public sealed interface Access permits JavaIOAccess, JavaIOFileDescriptorAccess, JavaIORandomAccessFileAccess, JavaLangRefAccess, JavaLangReflectAccess, JavaNetHttpCookieAccess, JavaNetInetAddressAccess, JavaNetURLAccess, JavaNetUriAccess, JavaNioAccess, JavaObjectInputFilterAccess, JavaObjectInputStreamAccess, JavaObjectInputStreamReadString, JavaObjectStreamReflectionAccess, JavaSecurityPropertiesAccess, JavaSecuritySignatureAccess, JavaSecuritySpecAccess, JavaUtilCollectionAccess, JavaUtilConcurrentFJPAccess, JavaUtilConcurrentTLRAccess, JavaUtilJarAccess, JavaUtilResourceBundleAccess, JavaUtilZipFileAccess, JavaxCryptoSealedObjectAccess, JavaxCryptoSpecAccess, JavaxSecurityAccess { }
