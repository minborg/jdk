/*
 * Copyright (c) 2012, 2026, Oracle and/or its affiliates. All rights reserved.
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

package java.util.function;

import jdk.internal.javac.PreviewFeature;
import jdk.internal.lang.LazySupplier;

import java.util.Objects;

/**
 * Represents a supplier of results.
 *
 * <p>There is no requirement that a new or distinct result be returned each
 * time the supplier is invoked.
 *
 * <p>This is a <a href="package-summary.html">functional interface</a>
 * whose functional method is {@link #get()}.
 *
 * @param <T> the type of results supplied by this supplier
 *
 * @since 1.8
 */
@FunctionalInterface
public interface Supplier<T> {

    /**
     * Gets a result.
     *
     * @return a result
     */
    T get();

    // Factory

    /**
     * {@return a new lazy supplier whose result is to be computed later via the
     *          provided {@code computingFunction}}
     * <p>
     * A lazy supplier is a lazy value as described in
     * {@linkplain java.util##lazy-values lazy values and collections}. The returned
     * lazy supplier invokes the provided computing function at most once and never
     * permits {@code null} content.
     * <p>
     * By design, the method always returns a new lazy supplier even if the provided
     * computing function is already a lazy supplier. Clients that want to elide creation
     * under this condition can retain and reuse the original lazy supplier rather than
     * wrapping it again.
     * <p>
     * The returned lazy supplier's {@linkplain Object#equals(Object) equals()} method
     * compares identity. Hence, two distinct lazy suppliers with the same content are
     * <em>not</em> equal. The {@linkplain Object#hashCode() hashCode()} method returns
     * the {@linkplain System#identityHashCode(Object) identity hash code}. The
     * {@linkplain Object#toString() toString()} method returns a string suitable for
     * debugging and never triggers initialization of the lazy supplier.
     * <p>
     * Here is an example involving an application that maintains a {@code OrderController}
     * components. By using a lazy supplier, we ensure that at most one
     * {@code OrderController} instance is created. Once
     * created, the component retrieval is eligible for constant folding by the JVM:
     * {@snippet lang = java:
     * class Application {
     *
     *     static final Supplier<OrderController> ORDERS
     *         = Supplier.ofLazy(OrderController::new);
     *
     *     public static OrderController orders() {
     *         return ORDERS.get();
     *     }
     *
     *      // Eligible for constant folding
     *      OrderController orders = orders();
     * }
     * }
     *
     * @param computingFunction in the form of a {@linkplain Supplier} to be used
     *                          to initialize the supplier
     * @param <T>               type of the supplied value
     * @throws NullPointerException if the provided {@code computingFunction} is
     *                              {@code null}
     *
     * @see java.util.List#ofLazy(int, IntFunction)
     * @see java.util.Map#ofLazy(java.util.Set, Function)
     * @see java.util.Set#ofLazy(java.util.Set, Predicate)
     * @since 28
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    static <T> Supplier<T> ofLazy(Supplier<? extends T> computingFunction) {
        Objects.requireNonNull(computingFunction);
        return LazySupplier.ofLazy(computingFunction);
    }
}
