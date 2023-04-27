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

package jdk.internal.util.concurrent.lazy;

import java.util.Objects;
import java.util.concurrent.lazy.LazyValue;
import java.util.function.Supplier;

public final class LazyUtil {

    // Object that flags the Lazy has been successfully constucted.
    static final PresentFlag PRESENT_FLAG = new PresentFlag();

    private LazyUtil() {
    }
    static final class PresentFlag {
        private PresentFlag() {}
    }

    public static <V> LazyValue<V> ofEvaluated(Supplier<? extends V> supplier) {
        Objects.requireNonNull(supplier);
        return (supplier instanceof PreComputedLazyValue<? extends V> preComputedLazy)
                // Already evaluated so just return it
                ? asLazyV(preComputedLazy)
                : new PreComputedLazyValue<>(supplier.get());
    }

    public static <V> LazyValue<V> ofBackgroundEvaluated(Supplier<? extends V> supplier) {
        Objects.requireNonNull(supplier);

        return switch (supplier) {
            case PreComputedLazyValue<? extends V> p -> asLazyV(p);
            case LazyValue<? extends V> l -> computeInBackground(l);
            default -> computeInBackground(new StandardLazyValue<>(supplier));
        };
    }

    private static <V> LazyValue<V> computeInBackground(LazyValue<? extends V> lazyValue) {
        Thread.ofVirtual()
                .name("Background eval " + lazyValue.toString())
                .start(lazyValue::get);
        return asLazyV(lazyValue);
    }

    @SuppressWarnings("unchecked")
    private static <V> LazyValue<V> asLazyV(LazyValue<? extends V> lazyValue) {
        return (LazyValue<V>) lazyValue;
    }

}
