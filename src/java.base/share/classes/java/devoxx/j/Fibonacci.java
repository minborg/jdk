package java.devoxx.j;

import java.util.Arrays;

/** Fibonacci series: 0, 1, 1, 2, 3, 5, 8, 13, ... */
public final class Fibonacci {

    /** Ctor */ public Fibonacci() {}

    /**
     * {@return Fibonacci element of i}
     * @param i element
     */
    public static int fib(int i) { // `i` positive
        return (i < 2)
                ? i
                : fib(i - 1) + fib(i - 2);
    }

    /**
    * Demo app.
    * @param args input values
    */
    public static void main(String[] args) {
        Arrays.stream(args)
                .mapToInt(Integer::parseInt)
                .map(Fibonacci::fib)
                .forEach(System.out::println);

    }

}
