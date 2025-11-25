package java.stable;

import jdk.internal.misc.Unsafe;

/** To be removed */
public final class StaticField extends Base {

    private static final int value = 1;

    /** Ctor */
    public StaticField() { }

    int payload() {
        throw new UnsupportedOperationException();
        // return UNSAFE.getIntStable(value);
    }

}
