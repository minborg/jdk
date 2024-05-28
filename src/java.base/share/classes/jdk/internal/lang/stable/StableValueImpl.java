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
import jdk.internal.vm.annotation.Stable;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Supplier;

import static jdk.internal.lang.stable.StableUtil.UNSAFE;

public final class StableValueImpl<T> implements StableValue<T> {

    private static final long COMPUTATION_OFFSET =
            UNSAFE.objectFieldOffset(StableValueImpl.class, "computation");

    @Stable
    private final Object mutex = new Object();
    @Stable
    private volatile Computation<T> computation;

    private StableValueImpl() {}

    @Override
    public boolean trySet(T value) {
        if (computation != null) {
            return false;
        }
        synchronized (mutex) {
            return trySet0(Computation.Value.of(value));
        }
    }

    private boolean trySet0(Computation<T> comp) {
        return UNSAFE.compareAndSetReference(this, COMPUTATION_OFFSET, null, comp);
    }

    @Override
    @ForceInline
    public T orElseThrow() {
        return switch (computation) {
            case Computation.Value<T> n -> n.value();
            case Computation.Error<T> e -> throw new NoSuchElementException(e.throwableClassName());
            case null                   -> throw new NoSuchElementException("No value set");
        };
    }

    @Override
    @ForceInline
    public T orElse(T other) {
        return computation instanceof Computation.Value<T> v
                ? v.value()
                : other;
    }

    @ForceInline
    @Override
    public T computeIfUnset(Supplier<? extends T> supplier) {
        return computation instanceof Computation.Value<T> v
                ? v.value()
                : computeIfUnset0(supplier);
    }

    @DontInline
    private T computeIfUnset0(Supplier<? extends T> supplier) {
        synchronized (mutex) {
            return switch (computation) {
                case Computation.Value<T> n -> n.value();
                case Computation.Error<T> e -> throw new NoSuchElementException(e.throwableClassName());
                case null -> {
                    try {
                        T t = supplier.get();
                        trySet0(Computation.Value.of(t));
                        yield t;
                    } catch (Throwable th) {
                        trySet0(Computation.Error.of(th));
                        throw th;
                    }
                }
            };
        }
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(computation);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof StableValueImpl<?> other &&
                Objects.equals(computation, other.computation);
    }

    @Override
    public String toString() {
        return "StableValue" + StableUtil.render(computation);
    }

    // Factory
    public static <T> StableValueImpl<T> of() {
        return new StableValueImpl<>();
    }

}
