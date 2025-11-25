package java.lang;

import jdk.internal.misc.Unsafe;

/** To be removed */
public final class StaticArray extends Base {

    private static final int[] VALUES = new int[]{1};

    /** Ctor */
    public StaticArray() { }

    int payload() {
        return UNSAFE.getIntStable(VALUES, Unsafe.ARRAY_INT_BASE_OFFSET);
    }

}
