/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package java.util.concurrent.lazy.snippets;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.lazy.LazyValue;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Sippets for composition
 */
public class Composition {

    private Composition() {
    }

    // @start region="DemoPreset"
    class Demo {

        // 1. A lazy field
        private static final LazyValue<Integer> LARGE_PRIME = LazyValue.of(Demo::largePrime);
        // 2. A lazy field that is the result of lazily applying a transformation of an existing lazy field
        private static final LazyValue<Integer> EVEN_LARGER_PRIME = LARGE_PRIME.map(Demo::nextPrime);
        // 3. A field that lazily combines two existing lazy fields by lazily applying a combinator of the existing fields
        private static final LazyValue<Integer> LARGE_PRIMES_SUM = LazyValue.reduce(Integer::sum, LARGE_PRIME, EVEN_LARGER_PRIME);

        private static final LazyValue<Integer> LARGE_PRIMES_SUM2 = LazyValue.reduce(
                        Integer::sum, List.of(LARGE_PRIME, EVEN_LARGER_PRIME))
                .orElseThrow();

        private static final LazyValue<Integer> LARGE_PRIMES_SUM3 = LazyValue.of(() -> Stream.of(LARGE_PRIME, EVEN_LARGER_PRIME)
                .map(LazyValue::get)
                .reduce(Integer::sum)
                .get());

        private static final LazyValue<Integer> LARGE_PRIMES_SUM4 = LazyValue.of(() -> Stream.of(EVEN_LARGER_PRIME)
                .map(LazyValue::get)
                .reduce(LARGE_PRIME.get(), Integer::sum));

        // Todo: Add reduce operation on several lazies.

        public int theLargePrime() {
            return LARGE_PRIME.get();
        }

        public int theLargerPrime() {
            return EVEN_LARGER_PRIME.get();
        }

        public int thePrimeSum() {
            return LARGE_PRIMES_SUM.get();
        }

        static int largePrime() {
            // Compute a large prime
            return 7919;
        }

        static int nextPrime(int p) {
            for (int i = p+1; i <= Integer.MAX_VALUE; i++) {
                if (isPrime(i)) {
                    return i;
                }
            }
            throw new NoSuchElementException("No prime after " + p);
        }

        private static boolean isPrime(int x) {
            return IntStream.rangeClosed(2, (int) (Math.sqrt(x)))
                    .allMatch(n -> x % n != 0);
        }

    }
    // @end

    class Expressions {

        public static void main(String[] args) {

            var threePlusFourExpr = new Add(
                    new Const(3),
                    new Const(4));

            double threePlusFour = eval(threePlusFourExpr); // 12

            double threePlusFourSquared = eval(new Mul(new Const(threePlusFour), new Const(threePlusFour))); // 144

            LazyValue<Double> lazyThreeTimesFour = lazilyEval(threePlusFourExpr);

            LazyValue<Double> mulSquared = lazilyEval(new Mul(
                    new Lazy(lazyThreeTimesFour),
                    new Lazy(lazyThreeTimesFour)));

            // Evaluating any of the above lazies will trigger the lazy binding of lazyThreeTimesFour


        }

        sealed interface Expr permits BinExpr, Neg, Const, Lazy {}

        sealed interface BinExpr extends Expr permits Add, Mul {
            Expr left();
            Expr right();
        }

        record Add(Expr left, Expr right) implements BinExpr {}
        record Mul(Expr left, Expr right) implements BinExpr {}
        record Neg(Expr node) implements Expr {}
        record Const(double val) implements Expr {}
        record Lazy(LazyValue<Double> lazy) implements Expr {};

        static double eval(Expr n) {
            return switch (n) {
                case Add(var left, var right) -> Double.sum(eval(left), eval(right));
                case Mul(var left, var right) -> eval(left) * eval(right);
                case Neg(var exp) -> - eval(exp);
                case Const(double val) -> val;
                case Lazy(LazyValue<Double> lazy) -> lazy.get();
            };
        }

        static LazyValue<Double> lazilyEval(Expr n) {
            return switch (n) {
                case Add(var left, var right) -> LazyValue.reduce(Double::sum, lazilyEval(left), lazilyEval(right));
                case Mul(var left, var right) -> LazyValue.reduce((a, b) -> a * b, lazilyEval(left), lazilyEval(right));
                case Neg(var exp) -> lazilyEval(exp).map(d -> -d);
                case Const(double val) -> LazyValue.of(val);
                case Lazy(LazyValue<Double> lazy) -> lazy;
            };
        }


    }

}
