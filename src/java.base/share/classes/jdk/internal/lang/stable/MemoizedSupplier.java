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
import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;

import java.util.NoSuchElementException;
import java.util.function.Supplier;

public record MemoizedSupplier<T>(Supplier<T> original,
                                  StableValue<T> value,
                                  StableValue<ProviderResult> result) implements Supplier<T> {

    public MemoizedSupplier(Supplier<T> original) {
        this(original, StableValue.of(), StableValue.of());
    }

    @ForceInline
    @Override
    public T get() {
        final T t = value.orElseNull();
        if (t != null) {
            return t;
        }
        if (result.orElseNull() instanceof ProviderResult.Null) {
            return null;
        }
        return getSlowPath();
    }

    @DontInline
    private T getSlowPath() {
        // The internal `result` field also serves as a mutex

        // Consider old switch statement (HelloClassList)

        synchronized (result) {
            return switch (result.orElseNull()) {
                case ProviderResult.NonNull _  -> value.orElseNull();
                case ProviderResult.Null _     -> null;
                case ProviderResult.Error<?> e -> throw new NoSuchElementException(e.throwableClass().getName());
                case null -> {
                    try {
                        T t = original.get();
                        if (t != null) {
                            value.setOrThrow(t);
                            result.setOrThrow(ProviderResult.NonNull.INSTANCE);
                        } else {
                            result.setOrThrow(ProviderResult.Null.INSTANCE);
                        }
                        yield t;
                    } catch (Throwable th) {
                        result.setOrThrow(new ProviderResult.Error<>(th.getClass()));
                        throw th;
                    }
                }
            };
        }
    }

}
