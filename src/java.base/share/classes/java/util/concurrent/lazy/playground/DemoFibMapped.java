package java.util.concurrent.lazy.playground;

import java.util.concurrent.lazy.Lazy;
import java.util.concurrent.lazy.LazyReferenceArray;

/**
 * A demo of how to cache computation.
 */
public final class DemoFibMapped {

    // 0, 1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144 ...
    // fib(11) = 89
    // n non-negative
    static int fibSchoolBook(int n) {
        System.out.format("fibs: %3d%n", n);
        return (n <= 1)
                ? n
                : fibSchoolBook(n - 1) + fibSchoolBook(n - 2);
    }

    private static final int INTERVAL = 10; // Must be > 2

    private static final LazyReferenceArray<Integer> FIB_10_CACHE =
            Lazy.ofEmptyArray(3);

    private static final LazyReferenceArray.IntKeyMapper KEY_MAPPER =
            LazyReferenceArray.IntKeyMapper.ofConstant(INTERVAL);

    /**
     * Main method
     *
     * @param args from command line
     * @throws InterruptedException if a thred was interrupted
     */
    public static void main(String[] args) throws InterruptedException {
        System.out.println("fibScoolBook(11) = " + fibSchoolBook(11));

        System.out.println(FIB_10_CACHE);
        System.out.println("fib(11) = " + fib(11)); // 288 invocations

        System.out.println(FIB_10_CACHE);
        System.out.println("fib(11) = " + fib(11)); // 111 invocations
    }

    // Only works for values up to ~30

    static int fib(int n) {
        System.out.format("fib : %3d%n", n);
        if (n <= 1)
            return n;
        return FIB_10_CACHE.mapIntAndApply(KEY_MAPPER, n,
                    DemoFibMapped::fibSchoolBook,
                    DemoFibMapped::fibSchoolBook);
    }

    private DemoFibMapped() {
    }
}
