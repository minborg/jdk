package java.stable;

/** To be removed */
public final class InstanceField extends Base {

    private static final long VALUE_OFFSET = UNSAFE.objectFieldOffset(InstanceField.class, "value");

    private int value = 1;

    /** Ctor */
    public InstanceField() { }

    int payload() {
        return UNSAFE.getInt(this, VALUE_OFFSET);
    }

}
