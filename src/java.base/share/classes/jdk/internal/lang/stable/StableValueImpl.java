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

import static jdk.internal.lang.stable.StableUtil.UNSAFE;

public final class StableValueImpl<T> implements StableValue<T> {

    private static final long ELEMENT_OFFSET =
            UNSAFE.objectFieldOffset(StableValueImpl.class, "element");

    @Stable
    private T element;

    private StableValueImpl() {}

    public boolean trySet(T value) {
        // Prevent reordering under plain read semantics
        UNSAFE.storeStoreFence();
        return UNSAFE.compareAndSetReference(this, ELEMENT_OFFSET, null, value);
    }

    @ForceInline
    public T orElseThrow() {
        final T e = element;
        if (e != null) {
            return e;
        }
        return getOrThrowSlowPath();
    }

    @DontInline
    private T getOrThrowSlowPath() {
        @SuppressWarnings("unchecked")
        final T e = (T) UNSAFE.getReferenceVolatile(this, ELEMENT_OFFSET);
        if (e != null) {
            return e;
        }
        throw new NoSuchElementException();
    }

    @SuppressWarnings("unchecked")
    @ForceInline
    public T orElseNull() {
        final T e = element;
        if (e != null) {
            return e;
        }
        return (T) UNSAFE.getReferenceVolatile(this, ELEMENT_OFFSET);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(orElseNull());
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof StableValueImpl<?> other &&
                Objects.equals(orElseNull(), other.orElseNull());
    }

    @Override
    public String toString() {
        return "StableValue[" + orElseNull() + ']';
    }


    // Factory
    public static <T> StableValueImpl<T> of() {
        return new StableValueImpl<>();
    }

}
