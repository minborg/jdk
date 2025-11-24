package java.stable;

/** To be removed */
public final class StaticArray extends Base {

    private static final int[] value = new int[]{1};

    /** Ctor */
    public StaticArray() { }

    int payload() {
        return value[0];
    }

}
