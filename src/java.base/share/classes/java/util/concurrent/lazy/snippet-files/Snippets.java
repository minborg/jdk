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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.lazy.LazyArray;
import java.util.concurrent.lazy.LazyValue;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Sippets for the package-info file
 */
public class Snippets {

    private Snippets() {
    }

    // @start region="DemoPreset"
    class JepDemoPreset {

        // 1. Declare a lazy field
        private static final LazyValue<Foo> FOO = LazyValue.of(Foo::new);

        public Foo theFoo() {
            // 2. Foo is lazily constructed and recorded here upon first invocation
            return FOO.get();
        }
    }
    // @end

    // @start region="DemoPreset"
    class DemoPreset {

        private static final LazyValue<Foo> FOO = LazyValue.of(Foo::new);

        public Foo theFoo() {
            // Foo is lazily constructed and recorded here upon first invocation
            return FOO.get();
        }
    }
    // @end

    // @start region="DemoHolder"
    class DemoHolder {

        public Foo theBar() {
            class Holder {
                private static final Foo FOO = new Foo();
            }

            // Foo is lazily constructed and recorded here upon first invocation
            return Holder.FOO;
        }
    }
    // @end


    // @start region="DemoPrecomputed"
    class DemoPrecomputed {

/*        private static final EmptyLazyReference<Foo> lazy = Lazy.<Foo>emptyBuilder()
                .withValue(new Foo())
                .build();

        public static void main(String[] args) throws InterruptedException {
            // lazy is already pre-computed here
            System.out.println("lazy.apply(Foo::new) = " + lazy.apply(Foo::new));
        }*/
    }
    // @end

    // @start region="SupplierDemo"
    class SupplierDemo {

        // Eager Supplier of Foo
        private static final Supplier<Foo> EAGER_FOO = Foo::new;

        // Turns an eager Supplier into a caching lazy Supplier
        private static final Supplier<Foo> LAZILY_CACHED_FOO = LazyValue.of(EAGER_FOO);

        public static void main(String[] args) {
            // Lazily construct and record the one-and-only Foo
            Foo theFoo = LAZILY_CACHED_FOO.get();
        }
    }
    // @end

    // @start region="JepDemoArray"
    class JepDemoArray {

        // 1. Declare a lazy array of length 32
        private static final LazyArray<Long> VALUE_PO2_CACHE = LazyArray.of(32, index -> 1L << index);

        public long powerOfTwo(int n) {
            // 2. The n:th slot is lazily computed and recorded here upon first slot invocation
            // 3. Using an n outside the array will throw an ArrayOutOfBoundsException
            return VALUE_PO2_CACHE.get(n);
        }
    }
    // @end


    // @start region="DemoArray"
    class DemoArray {

        private static final LazyArray<Value> VALUE_PO2_CACHE =
                LazyArray.of(32, index -> new Value(1L << index));

        public Value powerOfTwoValue(int n) {
            if (n < 0 || n >= VALUE_PO2_CACHE.length()) {
                throw new IllegalArgumentException(Integer.toString(n));
            }

            return VALUE_PO2_CACHE.get(n);
        }
    }
    // @end


    // @start region="DemoIntFunction"
    class DemoIntFunction {

        // Eager IntFunction<Value>
        private static final IntFunction<Value> EAGER_VALUE =
                index -> new Value(index);

        // Turns an eager IntFunction into a caching lazy IntFunction
        private static final IntFunction<Value> LAZILY_CACHED_VALUES =
                LazyArray.of(64, EAGER_VALUE)::get;

        public static void main(String[] args) {
            Value value42 = LAZILY_CACHED_VALUES.apply(42);
        }
    }
    // @end


    // @start region="DemoIntFunction"
    class NullDemo {

        private Supplier<Optional<Color>> backgroundColor =
                LazyValue.of(() -> Optional.ofNullable(calculateBgColor()));

        Color backgroundColor(Color defaultColor) {
            return backgroundColor.get()
                    .orElse(defaultColor);
        }

        private Color calculateBgColor() {
            // Read background color from file returning "null" if it fails.
            // ...
            return null;
        }
    }
    // @end

    static class MapFib {
        Map<Integer, Integer> fibonacci = new ConcurrentHashMap<>();

        int fib(int n) {
            return (n < 2) ? n
                    : fibonacci.computeIfAbsent(n, nk -> fib(nk - 1) + fib(nk - 2) );
        }
    }

    static class LazyArrayFib {
        LazyArray<Integer> fibonacci = LazyArray.of(20, this::fib);

        int fib(int n) {
            return (n < 2) ? n
                    : fibonacci.get(n - 1) +
                      fibonacci.get(n - 2);
        }

        public void a() {
            fibonacci.stream()
                    .limit(10)
                    .toArray();
        }

    }

    private static final class Color{}


    static class Foo {
    }

    static class Value {

        long value;

        public Value(long value) {
            this.value = value;
        }

        long asLong() {
            return value;
        }
    }

    static class User {

        private final int id;
        private String name;


        public User(int id) {
            this.id = id;
        }

    }

    static class Connection {
    }

    static class Request {
    }

    static Connection getDatabaseConnection() {
        return new Connection();
    }

    User findUserById(Connection c,
                      int id) {
        return new User(id);
    }

    static class DbTools {
        static String lookupPage(String pageName) {
            // Gets the HTML code for the named page from a content database
            return ""; // Whatever ...
        }

        static String loadBadRequestPage(int code) {
            return "";
        }

        static String loadUnaothorizedPage(int code) {
            return "";
        }

        static String loadForbiddenPage(int code) {
            return "";
        }

        static String loadNotFoundPage(int code) {
            return "";
        }


    }

    int check(Request request) {
        return 0;
    }

    String render(Request request) {
        return "";
    }

}
