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

package java.util.concurrent.lazy;

import jdk.internal.javac.PreviewFeature;
import jdk.internal.util.concurrent.lazy.CompactLazyValue;
import jdk.internal.util.concurrent.lazy.LazyUtil;
import jdk.internal.util.concurrent.lazy.PreComputedLazyValue;
import jdk.internal.util.concurrent.lazy.StandardLazyValue;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A lazy reference with a pre-set supplier which will be invoken at most once,
 * for example when {@link LazyValue#get() get()} is invoked.
 *
 * @param <V> The type of the value to be recorded
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.LAZY)
public sealed interface LazyValue<V>
        extends Supplier<V>
        permits StandardLazyValue, CompactLazyValue, PreComputedLazyValue {

    /**
     * {@return the excption thrown by the supplier invoked or
     * {@link Optional#empty()} if no exception was thrown}.
     */
    public Optional<Throwable> exception();

    /**
     * {@return the value if present, otherwise{@code defaultValue}}.
     *
     * @param defaultValue to use if no value is present
     * @throws NoSuchElementException if a provider has previously thrown an exception.
     */
    V getOr(V defaultValue);

    /**
     * Returns the present value or, if no present value exists, atomically attempts
     * to compute the value using the <em>pre-set {@linkplain LazyValue#of(Supplier)} supplier}</em>.
     * <p>
     * If the pre-set supplier itself throws an (unchecked) exception, the
     * exception is rethrown, and no value is recorded. The most
     * common usage is to construct a new object serving as a memoized result, as in:
     * <p>
     * {@snippet lang = java:
     *    LazyValue<V> lazy = LazyValue.of(Value::new);
     *    // ...
     *    V value = lazy.get();
     *    assertNotNull(value); // Value is non-null
     *}
     * <p>
     * If another thread attempts to compute the value, the current thread will be suspended until
     * the atempt completes (successfully or not).
     *
     * @return the value (pre-existing or newly computed)
     * @throws NullPointerException   if the pre-set supplier returns {@code null}.
     * @throws IllegalStateException  if a Lazy obtained via the {@link LazyValue#ofCompact(Supplier) Lazy.ofCompact()}
     *                                is used and the pre-set supplier produces an instance that implements
     *                                {@link Throwable}, {@link Thread} or {@link Supplier}.
     * @throws NoSuchElementException if a supplier has previously thrown an exception.
     */
    @SuppressWarnings("unchecked")
    @Override
    public V get();

    /**
     * {@return a normal Lazy with the provided {@code presetSupplier}}.
     * <p>
     * If a later attempt is made to invoke the {@link LazyValue#get()} method when no element is present,
     * the provided {@code presetSupplier} will automatically be invoked.
     * <p>
     * {@snippet lang = java:
     *     class DemoPreset {
     *
     *         private static final LazyValue<Foo> FOO = LazyValue.of(Foo::new);
     *
     *         public Foo theBar() {
     *             // Foo is lazily constructed and recorded here upon first invocation
     *             return FOO.get();
     *         }
     *     }
     *}
     *
     * @param <V>            The type of the value
     * @param presetSupplier to invoke when lazily constructing a value
     */
    public static <V> LazyValue<V> of(Supplier<? extends V> presetSupplier) {
        Objects.requireNonNull(presetSupplier);
        return new StandardLazyValue<>(presetSupplier);
    }

    /**
     * {@return a compact Lazy with the provided {@code presetSupplier}}.
     * <p>
     * A compact Lazy has a smaller footprint but exhibits lower performance.
     * <p>
     * If a later attempt is made to invoke the {@link LazyValue#get()} method when no element is present,
     * the provided {@code presetSupplier} will automatically be invoked.
     * <p>
     * {@snippet lang = java:
     *     class DemoPreset {
     *
     *         private static final LazyValue<Foo> FOO = LazyValue.ofCompact(Foo::new);
     *
     *         public Foo theBar() {
     *             // Foo is lazily constructed and recorded here upon first invocation
     *             return FOO.get();
     *         }
     *     }
     *}
     * <p>
     * The provided {@code presetSupplier} may not produce a value that implements
     * {@link Throwable} or {@link Supplier }. If that is the case, the returned Lazy
     * will throw an IllegalStateException upon being evaluated. If the {@code presetSupplier}
     * can produce objects implementing any of these types, the factory method
     * {@link LazyValue#of(Supplier)}  of()} has to be used instead.
     *
     * @param <V>            The type of the value
     * @param presetSupplier to invoke when lazily constructing a value
     */
    public static <V> LazyValue<V> ofCompact(Supplier<? extends V> presetSupplier) {
        Objects.requireNonNull(presetSupplier);
        return new CompactLazyValue<>(presetSupplier);
    }

    /**
     * {@return a pre-evaluated Lazy that is computed by invoking the provided
     * {@code supplier}'s {@link Supplier#get() get()} method (if not already pre-evaluated) in the current thread}.
     * <p>
     * An eagerly pre-evaluated Lazy may be provided seamlessly by some tools or
     * can be eexplicitly provided by user code.
     *
     * @param supplier to invoke when eagerly constructing a value
     * @param <V>   The type of the value
     */
    @SuppressWarnings("unchecked")
    public static <V> LazyValue<V> ofEvaluated(Supplier<? extends V> supplier) {
        return LazyUtil.ofEvaluated(supplier);
    }

    /**
     * {@return a pre-evaluated Lazy that is computed by invoking the provided
     * {@code supplier}'s {@link Supplier#get() get()} method (if needed) in another background thread}.
     * <p>
     * The name of the background thread (if any) is unspecified.
     *
     * @param supplier supplier to invoke
     * @param <V>   The type of the value
     */
    public static <V> LazyValue<V> ofBackgroundEvaluated(Supplier<? extends V> supplier) {
        return LazyUtil.ofBackgroundEvaluated(supplier);
    }

}
