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
 * of computed constants and collections thereof.  Providers of constant values are guaranteed
 * to be invoked at most one time. In other words, a provider can only run once and
 * in the first-calling thread and so, there is no race across threads which guarantees
 * the at-most-once evaluation. The life cycle of a computed constant is said to be <em>monotonic</em>
 * where it goes from the initial state of <em>unbound</em> (when it is not associated with any value)
 * to the terminal state of <em>bound</em> when it is permanently associated with a fixed
 * value (the value can be {@code null}).
 * <p>
 * This contrasts with {@link java.util.concurrent.atomic.AtomicReferenceArray } where any number of
 * updates can be done and where there is no simple way to atomically compute a value
 * (guaranteed to only be computed once) if missing. Computed constants also contrasts to
 * {@link java.util.concurrent.Future} where a value is computed in another thread.
 * <p>
 * The implementations are optimized for providing high average performance
 * for get operations over many invocations.
 *
 *  <h2 id="computed-constant-API">Computed Constant API</h2>
 *
 * The Computed Constant API is generic with respect to a reference value of type V:
 * <ul>
 *     <li>{@link java.util.concurrent.constant.ComputedConstant} with e.g. {@link java.util.concurrent.constant.ComputedConstant#get() get()}<p>
 *     for example available via {@link java.util.concurrent.constant.ComputedConstant#of(java.util.function.Supplier) ComputedConstant.of(Supplier&lt;? super V&gt; presetSupplier)}</li>
 *
 *     <li>There is also an optimized List of ComputedConstant elements available via {@link java.util.concurrent.constant.ComputedConstant#ofList(int, java.util.function.IntFunction) ComputedConstant.of(int length, IntFunction&lt;? super V&gt; presetMapper)}</li>
 * </ul>
 *
 * <h3 id="computed-constant">ComputedConstant</h3>
 *
 * {@code ComputedConstant} provides atomic evaluation using a <em>preset-supplier</em>:
 *
 * {@snippet lang = java:
 *     class DemoPreset {
 *
 *         private static final ComputedConstant<Foo> FOO = ComputedConstant.of(Foo::new);
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
 * Here is how a constant value can be computed in the background so that it may already be bound
 * when first requested from user code:
 * {@snippet lang = java:
 *     class DemoBackground {
 *
 *         private static final ComputedConstant<Foo> CONSTANT = ComputedConstant.of(Foo::new);
 *
 *         static {
 *             Thread.ofVirtual().start(CONSTANT::get);
 *         }
 *
 *         public static void main(String[] args) throws InterruptedException {
 *             Thread.sleep(1000);
 *             // CONSTANT is likely already pre-computed here by a background thread
 *             System.out.println(CONSTANT.get());
 *         }
 *     }
 *}
 *
 * {@code ComputedConstant<T>} implements {@code Supplier<T>} allowing simple
 * interoperability with legacy code and less specific type declaration
 * as shown in the following example:
 * {@snippet lang = java:
 *     class SupplierDemo {
 *
 *         // Eager Supplier of Foo
 *         private static final Supplier<Foo> EAGER_FOO = Foo::new;
 *
 *         // Turns an eager Supplier into a caching constant Supplier
 *         private static final Supplier<Foo> LAZILY_CACHED_FOO = ComputedConstant.of(EAGER_FOO);
 *
 *         public static void main(String[] args) {
 *             // Lazily compute the one-and-only Foo
 *             Foo theFoo = LAZILY_CACHED_FOO.get();
 *         }
 *     }
 *}
 *
 * <h3 id="computed-constant-list">List of ComputedConstant Elements</h3>
 *
 * Lists of ComputedConstant elements can also be
 * obtained via {@link ComputedConstant} factory methods in the same way as
 * for {@code ComputedConstant} instances but with an extra initial arity, indicating the desired size
 * of the List:
 * {@snippet lang = java:
 *     class DemoArray {
 *
 *         // 1. Declare a list of ComputedConstant elements of size 32
 *         private static final List<ComputedConstant.OfSupplied<Long>> VALUE_PO2_CACHE = ComputedConstant.ofList(32, index -> 1L << index);
 *
 *         public long powerOfTwo(int n) {
 *             // 2. The n:th slot is computed and bound here upon the
 *             //    first call of get(n). The other elements are not affected.
 *             // 3. Using an n outside the list will throw an IndexOutOfBoundsException
 *             return VALUE_PO2_CACHE.get(n).get();
 *         }
 *     }
 *}
 * As can be seen above, a List factory takes an {@link java.util.function.IntFunction} rather
 * than a {@link java.util.function.Supplier }, allowing custom values to be
 * computed and bound into ComputedConstant elements in the list depending on the current index being used.
 *
 * <h3 id="state">States</h3>
 * {@code ComputedConstant} instances maintain an internal state described as follows:
 *
 * <ul>
 *     <li>Unbound;
 *     <p>Indicates no value is bound (transient state).
 *     <p>Can move to "Binding" or "Bound".
 *     <p>This state can be detected using {@link java.util.concurrent.constant.ComputedConstant#isUnbound()}</li>
 *     <li>Binding;
 *     <p>Indicates an attempt to bind a value is in progress (transient state).
 *     <p>Can move to "Bound" or "Error".
 *     <p>This state can be detected using {@link java.util.concurrent.constant.ComputedConstant#isBinding()}</li>
 *     <li>Bound;
 *     <p>Indicates a value is bound (final state).
 *     <p>Cannot move.
 *     <p>This state can be detected using {@link java.util.concurrent.constant.ComputedConstant#isBound()}</li>
 *     <li>Error;
 *     <p>Indicates an error when trying to bind a value(final state).
 *     <p>Cannot move.
 *     <p>This state can be detected using {@link java.util.concurrent.constant.ComputedConstant#isError()}</li>
 * </ul>
 * Transient states can change at any time, whereas if a final state is observed, it is
 * guaranteed the state will never change again.
 * <p>
 * The internal states and their transitions are depicted below where gray nodes indicate final states:
 * <p style="text-align:center">
 * <img src = "doc-files/computed-constant-states.svg" alt="the internal states">
 *
 * <h3 id="general">General Properties of ComputedConstant</h3>
 *
 * All methods of the classes in this package will throw a {@link java.lang.NullPointerException}
 * if a reference parameter is {@code null} unless otherwise specified.
 *
 * All computed constant constructs are "null-friendly" meaning a value can be bound to {@code null}.  As usual, values of type
 * Optional may also express optionality, without using {@code null}, as exemplified here:
 * {@snippet lang = java:
 *     class NullDemo {
 *
 *         private Supplier<Optional<Color>> backgroundColor =
 *                 ComputedConstant.of(() -> Optional.ofNullable(calculateBgColor()));
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
package java.util.concurrent.constant;
