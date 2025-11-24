package java;

import jdk.internal.misc.Unsafe;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_INT;

/** To be removed */
public final class MainSegment {

    /** A */
    public MainSegment() {
    }

    private static final Unsafe U = Unsafe.getUnsafe();
    private static final MemorySegment S = Arena.global().allocate(JAVA_INT);
    private static final long A = S.address();

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
        return U.getIntStable(null, A);
    }

}
