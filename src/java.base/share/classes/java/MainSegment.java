package java;

import jdk.internal.misc.Unsafe;

/** To be removed */
public class MainArray {

    /** A */
    public MainArray() {
    }

    private static final Unsafe U = Unsafe.getUnsafe();
    private static final int[] A = new int[]{1};

    static void main() throws InterruptedException {
        int sum = 0;
        for (int i = 0; i < 1_000_000; i++) {
            sum += payload();
        }
        IO.println(sum);
        Thread.sleep(1_000);
        IO.println("Done");
    }

    static int payload() {
        return U.getIntStable(A, Unsafe.ARRAY_INT_BASE_OFFSET + (0L * Unsafe.ARRAY_INT_INDEX_SCALE));
    }

}
