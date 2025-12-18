package jdk.internal.access;

/**
 * Marker interface for all the Access classes.
 */
public sealed interface Access permits JavaSecurityPropertiesAccess, JavaSecuritySignatureAccess, JavaSecuritySpecAccess, JavaUtilCollectionAccess, JavaUtilConcurrentFJPAccess, JavaUtilConcurrentTLRAccess, JavaUtilJarAccess, JavaUtilResourceBundleAccess, JavaUtilZipFileAccess, JavaxCryptoSealedObjectAccess, JavaxCryptoSpecAccess, JavaxSecurityAccess { }
