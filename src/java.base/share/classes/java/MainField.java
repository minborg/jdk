package java;

import jdk.internal.misc.Unsafe;

/**
 * A
 */
public final class MainField {

    /**
     * A
     */
    public MainField() {
    }

    private static final Unsafe U = Unsafe.getUnsafe();
    private static final long FIELD_OFFSET = U.objectFieldOffset(Holder.class, "value");

    static final class Holder {
        int value;

        public Holder(int value) {
            this.value = value;
        }
    }

    private static final Holder HOLDER = new Holder(1);

    void main() throws InterruptedException {
        IO.println("Offset is " + FIELD_OFFSET); // 12
        int sum = 0;
        for (int i = 0; i < 1_000_000; i++) {
            sum += payload();
        }
        IO.println(sum);
        Thread.sleep(1_000);
        IO.println("Done");
    }

    int payload() {
        return U.getIntStable(HOLDER, FIELD_OFFSET);
    }

}
