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
 * A small toolkit of classes supporting high-performance, lock-free, thread-safe use
 * of lazily initialized values and arrays.  Providers of lazy values are guaranteed
 * to be invoked at most one time. In other words, the provider can only run once and
 * in the first-calling thread and so, there is no race across threads which guarantees
 * the at-most-once evaluation. The life cycle of a lazy is said to be <em>monotonic</em>
 * where it goes from the initial state of <em>unbound</em> (when it is not associated with any value)
 * to the terminal state of <em>bound</em> when it is permanently associated with a fixed
 * value (the value can be {@code null}).
 * <p>
 * This contrasts with {@link java.util.concurrent.atomic.AtomicReferenceArray } where any number of
 * updates can be done and where there is no simple way to atomically compute a value
 * (guaranteed to only be computed once) if missing. Lazy also contrasts to
 * {@link java.util.concurrent.Future} where a value is computed in another thread.
 * <p>
 * The lazy implementations are optimized for providing high average performance
 * for get operations over many invocations.
 *
 *  <h2 id="lazy">Lazy</h2>
 *
 * Lazy types are all generic with respect to a reference value of type V they compute and
 * come in two fundamental flavors:
 * <ul>
 *     <li>{@link LazyValue} with e.g. {@link LazyValue#get() get()}<p>
 *     for example available via {@link java.util.concurrent.lazy.LazyValue#of(java.util.function.Supplier) LazyValue.of(Supplier&lt;? super V&gt; presetSupplier)}</li>
 *
 *     <li>{@link LazyArray} with e.g. {@link java.util.concurrent.lazy.LazyArray#get(int) get(int index)}<p>
 *     for example available via {@link java.util.concurrent.lazy.LazyArray#of(int, java.util.function.IntFunction) LazyArray.of(int length, IntFunction&lt;? super V&gt; presetMapper)}</li>
 * </ul>
 *
 * In contrast to {@code LazyValue}, which records a single bound value, {@code LazyArray} access methods provide
 * an extra index argument that selects one of many bound values
 *
 * <h3 id="lazyvalue">LazyValue</h3>
 *
 * {@code LazyValue} provides atomic lazy evaluation using a <em>preset-supplier</em>:
 *
 * {@snippet lang = java:
 *     class DemoPreset {
 *
 *         private static final LazyValue<Foo> FOO = LazyValue.of(Foo::new);
 *
 *         public Foo theFoo() {
 *             // Foo is lazily constructed and recorded here upon first invocation
 *             return FOO.get();
 *         }
 *     }
 *}
 * The performance of the {@code get()} method in the example above is on par with using an
 * inner/private class holding a lazily initialized variable but with no overhead imposed by
 * the extra holder class.  Such a holder class might implement a lazy value as follows:
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
 * Here is how a lazy value can be computed in the background so that it may already be computed
 * when first requested from user code:
 * {@snippet lang = java:
 *     class DemoBackground {
 *
 *         private static final LazyValue<Foo> LAZY_VALUE = LazyValue.of(Foo::new);
 *
 *         static {
 *             Thread.ofVirtual().start(LAZY_VALUE::get);
 *         }
 *
 *         public static void main(String[] args) throws InterruptedException {
 *             Thread.sleep(1000);
 *             // lazy is likely already pre-computed here by a background thread
 *             System.out.println("lazy.get() = " + LAZY_VALUE.get());
 *         }
 *     }
 *}
 *
 * {@code LazyValue<T>} implements {@code Supplier<T>} allowing simple
 * interoperability with legacy code and less specific type declaration
 * as shown in the following example:
 * {@snippet lang = java:
 *     class SupplierDemo {
 *
 *         // Eager Supplier of Foo
 *         private static final Supplier<Foo> EAGER_FOO = Foo::new;
 *
 *         // Turns an eager Supplier into a caching lazy Supplier
 *         private static final Supplier<Foo> LAZILY_CACHED_FOO = LazyValue.of(EAGER_FOO);
 *
 *         public static void main(String[] args) {
 *             // Lazily compute the one-and-only Foo
 *             Foo theFoo = LAZILY_CACHED_FOO.get();
 *         }
 *     }
 *}
 *
 * <h3 id="lazyarray">LazyArray</h3>
 *
 * Arrays of lazy values (i.e. {@link java.util.concurrent.lazy.LazyArray}) can also be
 * obtained via {@link java.util.concurrent.lazy.LazyArray} factory methods in the same way as
 * for {@code LazyValue} instances but with an extra initial arity, indicating the desired length/index
 * of the array:
 * {@snippet lang = java:
 *     class DemoArray {
 *
 *         // 1. Declare a lazy array of length 32
 *         private static final LazyArray<Long> VALUE_PO2_CACHE = LazyArray.of(32, index -> 1L << index);
 *
 *         public long powerOfTwo(int n) {
 *             // 2. The n:th slot is lazily computed and recorded here upon the
 *             //    first call of get(n). The other slots are not affected.
 *             // 3. Using an n outside the array will throw an ArrayOutOfBoundsException
 *             return VALUE_PO2_CACHE.get(n);
 *         }
 *     }
 *}
 * As can be seen above, an array takes an {@link java.util.function.IntFunction} rather
 * than a {@link java.util.function.Supplier }, allowing custom values to be
 * computed and entered into the array depending on the current index being used.
 *
 * {@code LazyArray<V>} can be converted to an {@code IntFunction<T>} allowing simple interoperability
 * with existing code and with less specific type declarations as follows:
 * {@snippet lang = java:
 *     class DemoIntFunction {
 *
 *         // Eager IntFunction<Value>
 *         private static final IntFunction<Value> EAGER_VALUE =
 *                 index -> new Value(index);
 *
 *         // Turns an eager IntFunction into a caching lazy IntFunction
 *         private static final IntFunction<Value> LAZILY_CACHED_VALUES =
 *                 LazyValue.ofArray(64, EAGER_VALUE)::get;
 *
 *         public static void main(String[] args) {
 *             Value value42 = LAZILY_CACHED_VALUES.apply(42);
 *         }
 *     }
 *}
 *
 * <h3 id="state">Internal States</h3>
 * {@code LazyValue} and slots in a {@code LazyArray} maintain an internal state described as follows:
 *
 * <ul>
 *     <li>Unbound;
 *     <p>Indicates no value is bound (transient state).
 *     <p>Can move to "Bound" or "Error".</li>
 *     <li>Bound;
 *     <p>Indicates a value is bound (final state).
 *     <p>Cannot move.</li>
 *     <li>Error;
 *     <p>Indicates an error when trying to bind a value(final state).
 *     <p>Cannot move.</li>
 * </ul>
 * Transient states can change at any time, whereas if a final state is observed, it is
 * guaranteed the state will never change again.  The predicates {@code LazyValue::isBound} and
 * {@code LazyArray::isBound} can be used to determine if a value is bound or not.  In the same
 * way, the predicates {@code LazyValue::isError} and {@code LazyArray::isError} can be used to
 * determine if the evaluation of a bound value failed.
 * <p>
 * The internal states and their transitions are depicted below:
 * <p style="text-align:center">
 * <img src = "doc-files/lazy-states.png" alt="the internal states">
 *
 * <h3 id="general">General Properties of the Lazy Constructs</h3>
 *
 * All methods of the classes in this package will throw a {@link NullPointerException}
 * if a reference parameter is {@code null} unless otherwise specified.
 *
 * All lazy constructs are "null-friendly" meaning a value can be bound to {@code null}.  As usual, values of type
 * Optional may also express optionality, without using {@code null}, as exemplified here:
 * {@snippet lang = java:
 *     class NullDemo {
 *
 *         private Supplier<Optional<Color>> backgroundColor =
 *                 LazyValue.of(() -> Optional.ofNullable(calculateBgColor()));
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
