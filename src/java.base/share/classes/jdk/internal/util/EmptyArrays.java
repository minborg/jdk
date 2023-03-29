package jdk.internal.util;

/**
 * A utility class for providing reusable empty arrays of various sorts.
 */
public final class EmptyArrays {

    private static final byte[] OF_BYTES = new byte[0];

    private static final short[] OF_SHORTS = new short[0];

    private static final char[] OF_CHARS = new char[0];

    private static final boolean[] OF_BOOLEANS = new boolean[0];

    private static final int[] OF_INTS = new int[0];

    private static final long[] OF_LONGS = new long[0];

    private static final float[] OF_FLOATS = new float[0];

    private static final double[] OF_DOUBLES = new double[0];

    private static final Object[] OF_OBJECTS = new Object[0];

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
    public static byte[] ofBytes() {
        return OF_BYTES;
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
    public static short[] ofShorts() {
        return OF_SHORTS;
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
    public static char[] ofChars() {
        return OF_CHARS;
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
    public static boolean[] ofBooleans() {
        return OF_BOOLEANS;
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
    public static int[] ofInts() {
        return OF_INTS;
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
    public static long[] ofLongs() {
        return OF_LONGS;
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
    public static float[] ofFloats() {
        return OF_FLOATS;
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
    public static double[] ofDouble() {
        return OF_DOUBLES;
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
    public static <T> T[] ofObjects() {
        return (T[]) OF_OBJECTS;
    }

    public static byte[] copy()

}
