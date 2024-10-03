package java.devoxx.j;

import java.util.Arrays;
import java.util.List;

/** Fibonacci */
public final class FibonacciStable {

    /** Ctor */ public FibonacciStable() {}

    private static final int MAX = 46;

    private static final List<Integer> ELEMENTS =
            StableValue.ofList(MAX, FibonacciStable::fib);

    /**
     * {@return Fibonacci element of i}
     * @param i element
     */
    public static int fib(int i) { // `i` positive
        return (i < 2)
                ? i
                : ELEMENTS.get(i - 1) + ELEMENTS.get(i - 2);
    }

    /**
    * Demo app.
    * @param args input values
    */
    public static void main(String[] args) {

        // O(exp(N)) -> O(N), O(1)

        Arrays.stream(args)
                .mapToInt(Integer::parseInt)
                .map(FibonacciStable::fib)
                .forEach(System.out::println);

        // fib(6); Constant folded to 8

    }

}
