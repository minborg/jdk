/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

package java.util;

import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/** Compatibility entry points for the original collection factories. */
final class LazyCollections {
    private LazyCollections() { }

    static <E> List<E> ofLazyList(int size,
                                 IntFunction<? extends E> computingFunction) {
        return jdk.internal.util.LazyCollections.ofLazyList(size, computingFunction);
    }

    static <K, V> Map<K, V> ofLazyMap(Set<K> keys,
                                     Function<? super K, ? extends V> computingFunction) {
        return jdk.internal.util.LazyCollections.ofLazyMap(keys, computingFunction);
    }

    static <E> Set<E> ofLazySet(Set<? extends E> elementCandidates,
                               Predicate<? super E> computingFunction) {
        return jdk.internal.util.LazyCollections.ofLazySet(elementCandidates, computingFunction);
    }

    static <K, E extends Enum<E>, V> Map<K, V> ofLazyMapWithEnumKeys(
            Set<K> keys, Function<? super K, ? extends V> computingFunction) {
        return jdk.internal.util.LazyCollections.ofLazyMapWithEnumKeys(keys, computingFunction);
    }
}
