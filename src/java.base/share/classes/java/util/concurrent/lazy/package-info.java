/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * A small toolkit of classes supporting lock-free, thread-safe
 * use of lazily initialized values and arrays with superior performance.  Providers
 * of lazy values are guaranteed to be invoked at most one time.  This contrasts
 * to {@link java.util.concurrent.atomic.AtomicReferenceArray } where any number
 * of updates can be done and where there is no simple way to atomically compute
 * a value (guaranteed to only be computed once) if missing.
 * <p>
 * The lazy implementations are optimized for the case where there are N invocations
 * trying to obtain a value and where N >> 1, for example where N is > 2<sup>20</sup>.
 *
 *  <h2 id="lazy">Lazy</h2>
 *
 * Lazy types are all generic with respect to the reference value of type V they compute and
 * come in two fundamental flavors:
 * <ul>
 *     <li>{@link Lazy} with e.g. {@link java.util.concurrent.lazy.Lazy#get() get()}<p>
 *     available via {@link java.util.concurrent.lazy.Lazy#of(java.util.function.Supplier) LazyReference.of(Supplier&lt;V&gt; presetSupplier)}</li>
 *
 *     <li>{@link LazyArray} with e.g. {@link java.util.concurrent.lazy.LazyArray#apply(int) apply(int index)}<p>
 *     available via {@link java.util.concurrent.lazy.LazyArray#ofArray(int, java.util.function.IntFunction) LazyArray.ofArray(int length, IntFunction&lt;V&gt; presetMapper)}</li>
 * </ul>
 *
 * Hence, the Array type methods provide an extra arity where the index is specified compared to the Reference types.
 *
 * <h3 id="lazyreference">LazyReference</h3>
 *
 * Lazy provides atomic lazy evaluation using a <em>preset-supplier</em>:
 *
 * {@snippet lang = java:
 *     class DemoPreset {
 *
 *         private static final Lazy<Foo> FOO = Lazy.of(Foo::new);
 *
 *         public Foo theBar() {
 *             // Foo is lazily constructed and recorded here upon first invocation
 *             return FOO.get();
 *         }
 *     }
 *}
 * The performance of the example above is on pair with using an inner/private class
 * holding a lazily initialized variable but with no overhead imposed by the extra
 * class. A corresponding private class is illustraded hereunder:
 *
 {@snippet lang = java :
 *     class DemoHolder {
 *
 *         public Foo theBar() {
 *             class Holder {
 *                 private static final Foo FOO = new Foo();
 *             }
 *
 *             // Foo is lazily constructed and recorded here upon first invocation
 *             return Holder.FOO;
 *         }
 *     }
 *}
 *
 * Here is how a lazy value can be computed in the background and that may already be computed
 * when first requested from user code:
 * {@snippet lang = java:
 *     class DemoBackground {
 *
 *         private static final Lazy<Foo> lazy = ...
 *
 *         public static void main(String[] args) throws InterruptedException {
 *             Thread.sleep(1000);
 *             // lazy is likely already pre-computed here by a background thread
 *             System.out.println("lazy.get() = " + lazy.get());
 *         }
 *     }
 *}
 *
 * {@code LazyReference<T>} implements {@code Supplier<T>} allowing simple
 * interoperability with legacy code and less specific type declaration
 * as shown in the example hereunder:
 * {@snippet lang = java:
 *     class SupplierDemo {
 *
 *         // Eager Supplier of Foo
 *         private static final Supplier<Foo> EAGER_FOO = Foo::new;
 *
 *         // Turns an eager Supplier into a caching lazy Supplier
 *         private static final Supplier<Foo> LAZILY_CACHED_FOO = Lazy.of(EAGER_FOO);
 *
 *         public static void main(String[] args) {
 *             // Lazily compute the one-and-only Foo
 *             Foo theFoo = LAZILY_CACHED_FOO.get();
 *         }
 *     }
 *}
 * Lazy contains additional methods for checking its
 * {@linkplain java.util.concurrent.lazy.Lazy#state() state} and getting
 * any {@linkplain java.util.concurrent.lazy.Lazy#exception()} that might be thrown
 * by the provider.
 *
 * <h3 id="lazyarray">LazyArray</h3>
 *
 * Arrays of lazy values (i.e. {@link java.util.concurrent.lazy.LazyArray}) can also be
 * obtained via {@link java.util.concurrent.lazy.Lazy} factory methods in the same way as
 * for LazyReference instance but with an extra initial arity, indicating the desired length/index
 * of the array:
 * {@snippet lang = java:
 *     class DemoArray {
 *
 *         private static final LazyArray<Value> VALUE_PO2_CACHE =
 *                 Lazy.ofArray(32, index -> new Value(1L << index));
 *
 *         public Value powerOfTwoValue(int n) {
 *             if (n < 0 || n >= VALUE_PO2_CACHE.length()) {
 *                 throw new IllegalArgumentException(Integer.toString(n));
 *             }
 *
 *             return VALUE_PO2_CACHE.apply(n);
 *         }
 *     }
 *}
 * As can be seen above, an array takes an {@link java.util.function.IntFunction} rather
 * than a {@link java.util.function.Supplier }, allowing custom values to be
 * computed and entered into the array depending on the current index being used.
 *
 * As was the case for LazyReference, empty LazyReferenceArray instances can also be
 * constructed, allowing lazy mappers known at a later stage to be used:
 * {@snippet lang = java:
 *     class UserCache {
 *
 *         // Cache the first 64 users
 *         private static final EmptyLazyArray<User> USER_CACHE = Lazy.ofEmptyArray(64);
 *
 *         public User user(int id) {
 *             Connection c = getDatabaseConnection();
 *             return USER_CACHE.computeIfEmpty(id, i -> findUserById(c, i));
 *         }
 *     }
 *}
 *
 * {@code LazyReferenceArray<T>} implements {@code IntFunction<T>} allowing simple interoperability
 * with existing code and with less specific type declarations as shown hereunder:
 * {@snippet lang = java:
 *     class DemoIntFunction {
 *
 *         // Eager IntFunction<Value>
 *         private static final IntFunction<Value> EAGER_VALUE =
 *                 index -> new Value(index);
 *
 *         // Turns an eager IntFunction into a caching lazy IntFunction
 *         private static final IntFunction<Value> LAZILY_CACHED_VALUES =
 *                 Lazy.ofArray(64, EAGER_VALUE);
 *
 *         public static void main(String[] args) {
 *             Value value42 = LAZILY_CACHED_VALUES.apply(42);
 *         }
 *     }
 * }
 *
 * Sometimes, there is a mapping from an {@code int} key to an index, preventing
 * the key to be used directly. If there is a constant translation factor between index and
 * actual keys, the {@linkplain java.util.concurrent.lazy.Lazy .ofEmptyTranslatedArray()} can be used.
 * <p>
 * For example, when caching every 10th Fibonacci value, the following snippet can be used:
 * {@snippet lang = java:
 *         // Un-cached fibonacci method
 *         static int fib(int n) {
 *             return (n <= 1)
 *                     ? n
 *                     : fib(n - 1) + fib(n - 2);
 *         }
 *
 *         private static final EmptyLazyArray<Integer> FIB_10_CACHE =
 *                 Lazy.ofEmptyTranslatedArray(5, 10);
 *
 *
 *         // Only works for values up to ~50 as the backing array is of length 5.
 *         static int cachedFib(int n) {
 *             if (n <= 1)
 *                 return n;
 *             return FIB_10_CACHE.computeIfEmpty(n, DemoFibMapped::fib);
 *         }
 * }
 *
 * <h3 id="general">General Properties of the Lazy Constructs</h3>
 *
 * All methods of the classes in this package will throw a {@link NullPointerException}
 * if a reference parameter is {@code null}.
 *
 * All lazy constructs are "nullofobic" meaning a provider can never return {@code null}.  If nullablilty
 * for values stored are desired, the values have to be modeled using a construct that can express
 * {@code null} values in an explicit way such as {@link java.util.Optional#empty()} as exemplified here:
 * {@snippet lang = java:
 *     class NullDemo {
 *
 *         private Supplier<Optional<Color>> backgroundColor =
 *                 Lazy.of(() -> Optional.ofNullable(calculateBgColor()));
 *
 *         Color backgroundColor(Color defaultColor) {
 *             return backgroundColor.get()
 *                     .orElse(defaultColor);
 *         }
 *
 *         private Color calculateBgColor() {
 *             // Read background color from file returning "null" if it fails.
 *             // ...
 *             return null;
 *         }
 *     }
 *}
 *
 * @since 22
 */
package java.util.concurrent.lazy;
