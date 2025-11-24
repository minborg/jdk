package java;

import jdk.internal.misc.Unsafe;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_INT;

/** To be removed */
public final class MainHeapSegment {

    /** A */
    public MainHeapSegment() {
    }

    private static final Unsafe U = Unsafe.getUnsafe();
    private static final MemorySegment S = MemorySegment.ofArray(new int[]{0});

    static void main() throws InterruptedException {
        int sum = 0;
        for (int i = 0; i < 1_000_000; i++) {
            sum += payload();
        }
        IO.println(Integer.toString(sum));
        Thread.sleep(1_000);
        IO.println("Done");
    }

    static int payload() {
        return U.getIntStable(S.heapBase().orElseThrow(), Unsafe.ARRAY_INT_BASE_OFFSET + (0L * Unsafe.ARRAY_INT_INDEX_SCALE));
    }

}
