/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.lang.stable;

import jdk.internal.lang.StableValue;
import jdk.internal.vm.annotation.ForceInline;

import java.util.function.Supplier;

public record OnceSupplier<T>(Supplier<? extends T> original,
                              StableValue<T> value) implements Supplier<T> {

    @ForceInline
    @Override
    public T get() {
        T t = valueOrElseSentinel();
        if (t != StableUtil.NULL_SENTINEL) {
            return t;
        }
        synchronized (value) {
            t = valueOrElseSentinel();
            if (t != StableUtil.NULL_SENTINEL) {
                return t;
            }
            t = original.get();
            return t;
        }
    }

    private T valueOrElseSentinel() {
        @SuppressWarnings("unchecked")
        T t = value.orElse((T) StableUtil.NULL_SENTINEL);
        return t;
    }

    public static <T> Supplier<T> of(Supplier<? extends T> original) {
        return new OnceSupplier<>(original, StableValue.of());
    }

}
