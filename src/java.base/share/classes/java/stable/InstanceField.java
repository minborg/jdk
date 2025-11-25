package java.stable;

/** To be removed */
public final class InstanceField extends Base {

    static final class Holder {

        int value;

        public Holder(int value) {
            this.value = value;
        }
    }

    private static final long VALUE_OFFSET = UNSAFE.objectFieldOffset(Holder.class, "value");

    private static final Holder HOLDER = new Holder(1);

    /** Ctor */
    public InstanceField() { }

    int payload() {
        return UNSAFE.getInt(HOLDER, VALUE_OFFSET);
    }

}
