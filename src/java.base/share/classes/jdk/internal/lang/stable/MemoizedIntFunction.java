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

import jdk.internal.lang.StableArray;
import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;

import java.util.NoSuchElementException;
import java.util.function.IntFunction;
import java.util.stream.Stream;

public record MemoizedIntFunction<R>(IntFunction<? extends R> original,
                                     StableArray<R> values,
                                     StableArray<ProviderResult> results,
                                     Object[] mutexes) implements IntFunction<R> {

    @ForceInline
    @Override
    public R apply(int i) {
        final R t = values.orElseNull(i);
        if (t != null) {
            return t;
        }
        if (results.orElseNull(i) instanceof ProviderResult.Null) {
            return null;
        }
        return getSlowPath(i);
    }

    @DontInline
    private R getSlowPath(int i) {
        synchronized (mutexes[i]) {
            return switch (results.orElseNull(i)) {
                case ProviderResult.NonNull _  -> values.orElseNull(i);
                case ProviderResult.Null _     -> null;
                case ProviderResult.Error<?> e -> throw new NoSuchElementException(e.throwableClass().getName());
                case null -> {
                    try {
                        R t = original.apply(i);
                        if (t != null) {
                            values.setOrThrow(i, t);
                            results.setOrThrow(i, ProviderResult.NonNull.INSTANCE);
                        } else {
                            results.setOrThrow(i, ProviderResult.Null.INSTANCE);
                        }
                        yield t;
                    } catch (Throwable th) {
                        results.setOrThrow(i, new ProviderResult.Error<>(th.getClass()));
                        throw th;
                    }
                }
            };
        }
    }

    public static <R> IntFunction<R> memoizedIntFunction(int length,
                                                         IntFunction<? extends R> original) {
        return new MemoizedIntFunction<>(
                original,
                StableArray.of(length),
                StableArray.of(length),
                Stream.generate(Object::new).limit(length).toArray()
        );
    }

}
