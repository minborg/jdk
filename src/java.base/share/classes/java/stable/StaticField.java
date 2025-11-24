package java.stable;

/** To be removed */
public final class StaticField extends Base {

    private static final int value = 1;

    /** Ctor */
    public StaticField() { }

    int payload() {
        return value;
    }

}
