package jdk.internal.util;

public final class EmptyArrays {

    private static final byte[] OF_BYTE = new byte[0];

    private EmptyArrays() {}

    public static byte[] ofByte() {
        return OF_BYTE;
    }

}
