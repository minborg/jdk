package jdk.internal.lang.stable;

import jdk.internal.misc.Unsafe;

final class StableUtil {

    private StableUtil() {}

    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    static long objectOffset(int index) {
        return Unsafe.ARRAY_OBJECT_BASE_OFFSET + (long) index * Unsafe.ARRAY_OBJECT_INDEX_SCALE;
    }

}
