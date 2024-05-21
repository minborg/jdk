package jdk.internal.lang.stable;

import jdk.internal.misc.Unsafe;

final class StableUtil {

    private StableUtil() {}

    // Indicates a value is not set
    static final byte UNSET = 0;
    // Indicates a value is set to a non-null value
    static final byte SET_NON_NULL = 1; // The middle value
    // Indicates a value is set to a `null` value
    static final byte SET_NULL = 2;
    // Indicates there was an error when computing a value
    static final byte ERROR = 3;

    // Computation values

    // Indicates a computation operation has NOT been invoked.
    static final byte NOT_INVOKED = 0;
    // Indicates a computation operation has been invoked.
    static final byte INVOKED = 1;

        static final Unsafe UNSAFE = Unsafe.getUnsafe();
    // Sentinel value used to mark that a mutex will not be used anymore
    static final Object TOMBSTONE = new Object();


}
