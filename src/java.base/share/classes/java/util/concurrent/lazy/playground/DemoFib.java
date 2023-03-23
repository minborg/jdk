package java.util.concurrent.lazy.playground;

import java.util.concurrent.lazy.LazyReferenceArray;

/**
 * A demo of how to cache computation.
 */
public final class DemoFib {

    // 0, 1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144 ...
    // fib(11) = 89
    static int fibScoolBook(int n) {
        return (n <= 1)
                ? n
                : fibScoolBook(n - 1) + fibScoolBook(n - 2);
    }

    private static final int INTERVAL = 10;

    private static final LazyReferenceArray<Integer> FIB_10_CACHE =
            LazyReferenceArray.of(30 / INTERVAL, slot -> fib(slotToN(slot), false));

    /**
     * Main method
     *
     * @param args from command line
     * @throws InterruptedException if a thred was interrupted
     */
    public static void main(String[] args) throws InterruptedException {

        /*
        Thread.ofVirtual()
                .name("Fib Lazy Resolver")
                .start(() -> FIB_10_CACHE.force());
        Thread.sleep(5000);
        */

        System.out.println("fibScoolBook(11) = " + fibScoolBook(11));

        System.out.println(FIB_10_CACHE);
        System.out.println("fib(11) = " + fib(11)); // 288 invocations

        System.out.println(FIB_10_CACHE);
        System.out.println("fib(11) = " + fib(11)); // 111 invocations

        /*
        FIB_10_CACHE.force();
        System.out.println(FIB_10_CACHE);
        // LazyReferenceArray[0, 55, 6765]
        */

        /*
        var t = new Thread(() -> {
            System.out.println("fib(11) = " + fib(11));
        });
        t.start();
        t.join();
        */
    }

    // Caching mapper slot -> outside
    static int slotToN(int slot) {
        return slot * INTERVAL;
    }

    // Caching mapper outside -> slot
    static int nToSlot(int i) {
        return i / INTERVAL;
    }

    // Only works for values up to ~30
    static int fib(int n) {
        return fib(n, true);
    }

    static int fib(int n, boolean useCache) {
        System.out.format("%3d (%5s) : %s%n", n, useCache, Thread.currentThread().getName());
        if (n <= 1)
            return n;
        if (n % INTERVAL == 0 && useCache)
            return FIB_10_CACHE.apply(nToSlot(n));
        return fib(n - 1, true) + fib(n - 2, true);
    }

    private DemoFib() {
    }
}
