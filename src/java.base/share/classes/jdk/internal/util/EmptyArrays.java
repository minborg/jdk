package jdk.internal.util;

/**
 * A utility class for providing reusable empty arrays of various sorts.
 */
public final class EmptyArrays {

    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private static final short[] EMPTY_SHORT_ARRAY = new short[0];

    private static final char[] EMPTY_CHAR_ARRAY = new char[0];

    private static final boolean[] EMPTY_BOOLEAN_ARRAY = new boolean[0];

    private static final int[] EMPTY_INT_ARRAY = new int[0];

    private static final long[] EMPTY_LONG_ARRAY = new long[0];

    private static final float[] EMPTY_FLOAT_ARRAY = new float[0];

    private static final double[] EMPTY_DOUBLE_ARRAY = new double[0];

    private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

    private EmptyArrays() {}

    /**
     * {@return an empty (immutable) array of bytes}.
     * <p>
     * @implNote
     * Implementations of this method need not create a separate array
     * for each call.
     *
     * @since 21
     */
    public static byte[] emptyByteArray() {
        return EMPTY_BYTE_ARRAY;
    }

    /**
     * {@return an empty (immutable) array of shorts}.
     * <p>
     * @implNote
     * Implementations of this method need not create a separate array
     * for each call.
     *
     * @since 21
     */
    public static short[] emptyShortArray() {
        return EMPTY_SHORT_ARRAY;
    }

    /**
     * {@return an empty (immutable) array of chars}.
     * <p>
     * @implNote
     * Implementations of this method need not create a separate array
     * for each call.
     *
     * @since 21
     */
    public static char[] emptyCharArray() {
        return EMPTY_CHAR_ARRAY;
    }

    /**
     * {@return an empty (immutable) array of ints}.
     * <p>
     * @implNote
     * Implementations of this method need not create a separate array
     * for each call.
     *
     * @since 21
     */
    public static boolean[] emptyBooleanArray() {
        return EMPTY_BOOLEAN_ARRAY;
    }

    /**
     * {@return an empty (immutable) array of ints}.
     * <p>
     * @implNote
     * Implementations of this method need not create a separate array
     * for each call.
     *
     * @since 21
     */
    public static int[] emptyIntArray() {
        return EMPTY_INT_ARRAY;
    }

    /**
     * {@return an empty (immutable) array of longs}.
     * <p>
     * @implNote
     * Implementations of this method need not create a separate array
     * for each call.
     *
     * @since 21
     */
    public static long[] emptyLongArray() {
        return EMPTY_LONG_ARRAY;
    }

    /**
     * {@return an empty (immutable) array of longs}.
     * <p>
     * @implNote
     * Implementations of this method need not create a separate array
     * for each call.
     *
     * @since 21
     */
    public static float[] emptyFloatArray() {
        return EMPTY_FLOAT_ARRAY;
    }

    /**
     * {@return an empty (immutable) array of doubles}.
     * <p>
     * @implNote
     * Implementations of this method need not create a separate array
     * for each call.
     *
     * @since 21
     */
    public static double[] emptyDoubleArray() {
        return EMPTY_DOUBLE_ARRAY;
    }

    /**
     * {@return an empty (immutable) array of objects of type T}.
     * <p>
     * @implNote
     * Implementations of this method need not create a separate array
     * for each call. The component type of the returned array is {@link Object}.
     *
     * @param <T> Array type exposed via the type system.
     *
     * @since 21
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] emptyObjectArray() {
        return (T[]) EMPTY_OBJECT_ARRAY;
    }


    /**
     * {@return an array that cannot be modified and with the same content as the provided {@code array}}.
     * <p>
     * If the provided {@code array} cannot be modified to begin with, the method is free to return the
     * provided {@code array} directly, otherwise a copy of the provided {@code array} is returned.
     * <p>
     * Arrays of zero length and frozen arrays cannot be modified.
     *
     * @param array for which a defensive copy should be returned.
     * @throws NullPointerException if the provided {@code array} is {@code null}.
     */
    public static byte[] defensiveCopy(byte[] array) {
        return array.length == 0
                ? array
                : array.clone();
    }

    public static <T> T[] defensiveCopy(T array) {
        if (!array.getClass().isArray()) {
            throw new IllegalArgumentException("Not an array type:"+array);
        }
    }

}
