package jdk.internal.foreign;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;

public final class NativeCTypes {

    private NativeCTypes() {}

    public static final ValueLayout.OfBoolean C_BOOL = ValueLayout.JAVA_BOOLEAN;
    public static final ValueLayout.OfByte C_CHAR = ValueLayout.JAVA_BYTE;
    public static final ValueLayout.OfShort C_SHORT = ValueLayout.JAVA_SHORT;
    public static final ValueLayout.OfInt C_INT = ValueLayout.JAVA_INT;
    public static final ValueLayout.OfLong C_LONG_LONG = ValueLayout.JAVA_LONG;
    public static final ValueLayout.OfFloat C_FLOAT = ValueLayout.JAVA_FLOAT;
    public static final ValueLayout.OfDouble C_DOUBLE = ValueLayout.JAVA_DOUBLE;
    public static final AddressLayout C_POINTER = ValueLayout.ADDRESS
            .withTargetLayout(MemoryLayout.sequenceLayout(java.lang.Long.MAX_VALUE, ValueLayout.JAVA_BYTE));
    public static final ValueLayout.OfInt C_LONG = ValueLayout.JAVA_INT;
    public static final ValueLayout.OfDouble C_LONG_DOUBLE = ValueLayout.JAVA_DOUBLE;

}
