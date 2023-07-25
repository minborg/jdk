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
package java.util.concurrent.constant.snippets;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.NoSuchElementException;
import java.util.concurrent.constant.ComputedConstant;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Sippets for composition
 */
public class Composition {

    private Composition() {
    }

    static
    // @start region="DemoPreset"
    class Primes {

        // 1. A lazy field
        private static final ComputedConstant.OfSupplied<Integer> LARGE_PRIME = ComputedConstant.of(Primes::largePrime);
        // 2. A lazy field that is the result of lazily applying a mapping operation of an existing lazy field
        private static final ComputedConstant<Integer> EVEN_LARGER_PRIME = LARGE_PRIME.map(Primes::nextPrime);
        // 3. A field that lazily combines two existing lazy fields by lazily applying a reduction on the eventually bound values
        private static final ComputedConstant<Integer> LARGE_PRIMES_SUM = ComputedConstant.of(() -> LARGE_PRIME.get() + EVEN_LARGER_PRIME.get());

        private static final ComputedConstant<Integer> LARGE_PRIMES_SUM2 = ComputedConstant.of(() ->
                Stream.of(LARGE_PRIME, EVEN_LARGER_PRIME)
                        .map(ComputedConstant::get)
                        .reduce(0, Integer::sum));

        private static final ComputedConstant<Integer> LARGE_PRIMES_SUM3 = ComputedConstant.of(() -> Stream.of(LARGE_PRIME, EVEN_LARGER_PRIME)
                .map(ComputedConstant::get)
                .reduce(Integer::sum)
                .get());

        private static final ComputedConstant<Integer> LARGE_PRIMES_SUM4 = ComputedConstant.of(() -> Stream.of(EVEN_LARGER_PRIME)
                .map(ComputedConstant::get)
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
            for (int i = p + 1; i != Integer.MAX_VALUE; i++) {
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

    static
    class StringUtil {

        static MethodHandle find(MethodHandles.Lookup lookup, String name, MethodType type) {
            try {
                return lookup.findVirtual(String.class, name, type);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        private static final ComputedConstant.OfSupplied<MethodHandles.Lookup> LOOKUP = ComputedConstant.of(MethodHandles::lookup);

        private static final ComputedConstant<MethodHandle> LENGTH = LOOKUP.map(
                l -> find(l, "length", MethodType.methodType(int.class)));

        private static final ComputedConstant<MethodHandle> EMPTY = LOOKUP.map(
                l -> find(l, "isEmpty", MethodType.methodType(boolean.class)));


    }

    // Original
    abstract class Socket implements java.io.Closeable {
        private static final VarHandle STATE, IN, OUT;

        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                STATE = l.findVarHandle(java.net.Socket.class, "state", int.class);
                IN = l.findVarHandle(java.net.Socket.class, "in", InputStream.class);
                OUT = l.findVarHandle(java.net.Socket.class, "out", OutputStream.class);
            } catch (Exception e) {
                throw new InternalError(e);
            }
        }

        private int getAndBitwiseOrState(int mask) {
            return (int) STATE.getAndBitwiseOr(this, mask);
        }

    }

    abstract class LazySocket implements java.io.Closeable {

        private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
        private static final ComputedConstant<VarHandle>
                STATE = ComputedConstant.of(() -> find(LOOKUP, "state", int.class)),
                IN = ComputedConstant.of(() -> find(LOOKUP, "in", InputStream.class)),
                OUT = ComputedConstant.of(() -> find(LOOKUP, "out", OutputStream.class));

        static VarHandle find(MethodHandles.Lookup lookup, String name, Class<?> type) {
            try {
                return lookup.findVarHandle(java.net.Socket.class, name, type);
            } catch (Exception e) {
                throw new InternalError(e);
            }
        }

        private int getAndBitwiseOrState(int mask) {
            return (int) STATE.get().getAndBitwiseOr(this, mask);
        }

    }


    abstract class LazyComposeSocket implements java.io.Closeable {

        private static final ComputedConstant.OfSupplied<MethodHandles.Lookup> LOOKUP = ComputedConstant.of(MethodHandles::lookup);
        private static final ComputedConstant<VarHandle>
                STATE = LOOKUP.map(l -> find(l, "state", int.class)),
                IN = LOOKUP.map(l -> find(l, "in", InputStream.class)),
                OUT = LOOKUP.map(l -> find(l, "out", OutputStream.class));


        static VarHandle find(MethodHandles.Lookup lookup, String name, Class<?> type) {
            try {
                return lookup.findVarHandle(java.net.Socket.class, name, type);
            } catch (Exception e) {
                throw new InternalError(e);
            }
        }
    }


    static

    class Expressions {

        public static void main(String[] args) {

            var threePlusFourExpr = new Add(
                    new Const(3),
                    new Const(4));

            double threePlusFour = eval(threePlusFourExpr); // 7

            double threePlusFourSquared = eval(new Mul(
                    new Const(threePlusFour),
                    new Const(threePlusFour))); // 49

            ComputedConstant<Double> lazyThreeTimesFour = lazilyEval(threePlusFourExpr);

            ComputedConstant<Double> lazyThreeTimesFourSquared = lazilyEval(new Mul(
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
        record Lazy(ComputedConstant<Double> lazy) implements Expr {};

        // Eager Evaluator
        static double eval(Expr n) {
            return switch (n) {
                case Add(var left, var right)     -> Double.sum(eval(left), eval(right));
                case Mul(var left, var right)     -> eval(left) * eval(right);
                case Neg(var exp)                 -> -eval(exp);
                case Const(double val)            -> val;
                case Lazy(ComputedConstant<Double> lazy) -> lazy.get();
            };
        }

        // Lazy Evaluator
        static ComputedConstant.OfSupplied<Double> lazilyEval(Expr n) {
            return switch (n) {
                case Add(var left, var right) -> ComputedConstant.of(() -> lazilyEval(left).get() + lazilyEval(right).get());
                case Mul(var left, var right) -> ComputedConstant.of(() -> lazilyEval(left).get() * lazilyEval(right).get());
                case Neg(var exp)                 -> lazilyEval(exp).map(d -> -d);
                case Const(double val)            -> ComputedConstant.ofSupplied(val);
                case Lazy(ComputedConstant.OfSupplied<Double> lazy) -> lazy;
            };
        }

    }

}
