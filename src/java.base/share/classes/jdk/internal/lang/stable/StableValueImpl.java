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

import static jdk.internal.lang.stable.StableUtil.*;

public final class StableValueImpl<T> implements StableValue<T> {

    private static final long VALUE_OFFSET =
            UNSAFE.objectFieldOffset(StableValueImpl.class, "value");

    // The set value (if non-null)
    @Stable
    private T value;

    // Used to signal UNSET, SET_NONNULL, or SET_NULL; and for piggybacking:
    // The `state` field must always be read before the `value` field is read
    // The `value` field must always be written before the `state` field is written
    @Stable
    private volatile byte state;

    private StableValueImpl() {}

    @ForceInline
    @Override
    public boolean trySet(T value) {
        if (state != UNSET) {
            return false;
        }
        return trySet0(value);
    }

    private boolean trySet0(T value) {
        boolean set = UNSAFE.compareAndSetReference(this, VALUE_OFFSET, null, value);
        if (set) {
            state = (value == null) ? SET_NULL : SET_NONNULL;
        }
        return set;
    }

    @ForceInline
    @Override
    public T orElseThrow() {
        return switch (state) {
            case SET_NONNULL -> value;
            case SET_NULL    -> null;
            default          -> throw new NoSuchElementException("No value set");
        };
    }

    @ForceInline
    @Override
    public T orElse(T other) {
        return switch (state) {
            case SET_NONNULL -> value;
            case SET_NULL    -> null;
            default          -> other;
        };
    }

    @ForceInline
    @Override
    public T computeIfUnset(Supplier<? extends T> supplier) {
        return switch (state) {
            case SET_NONNULL -> value;
            case SET_NULL    -> null;
            default          -> computeIfUnset0(supplier);
        };
    }

    @DontInline
    private T computeIfUnset0(Supplier<? extends T> supplier) {
        T t = supplier.get();
        return trySet0(t) ? t : orElseThrow();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(orElse(null));
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof StableValueImpl<?> other &&
                state == other.state &&
                Objects.equals(value, other.value);
    }

    @Override
    public String toString() {
        // Make sure state is read first.
        byte s = state;
        return "StableValue" + StableUtil.render(s, value);
    }

    // Factory
    public static <T> StableValueImpl<T> of() {
        return new StableValueImpl<>();
    }

}
