/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * The sole implementation of the ComputedConstant interface.
 *
 * @param <T> type of the constant
 * @implNote This implementation can be used early in the boot sequence as it does not
 * rely on reflection, MethodHandles, Streams etc.
 */
public final class ComputedConstantImpl<T> implements ComputedConstant<T> {

    static final String UNSET_LABEL = ".unset";

    // Unsafe allows ComputedConstant to be used early in the boot sequence
    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    // Unsafe offset for access of the constant field
    private static final long CONSTANT_OFFSET =
            UNSAFE.objectFieldOffset(ComputedConstantImpl.class, "constant");

    // Generally, fields annotated with `@Stable` are accessed by the JVM using special
    // memory semantics rules (see `parse.hpp` and `parse(1|2|3).cpp`).
    //
    // This field is used directly and reflectively via Unsafe using explicit memory semantics.
    //
    // | Value           | Meaning        |
    // | --------------- | -------------- |
    // | null            | Unset          |
    // | `other`         | Set to `other` |
    //
    // This field is accessed reflectively
    @Stable
    private T constant;

    @Stable
    private volatile Supplier<? extends T> underlying;

    private ComputedConstantImpl(Supplier<? extends T> underlying) {
        this.underlying = underlying;
    }

    private ComputedConstantImpl(T contents) {
        this((Supplier<? extends T>) null);
        setRelease(contents);
    }

    @ForceInline
    @Override
    public T get() {
        final T t = contentsAcquire();
        return (t != null) ? t : getSlowPath();
    }

    private T getSlowPath() {
        preventReentry();
        synchronized (this) {
            T t = contentsAcquire();
            if (t == null) {
                t = underlying.get();
                Objects.requireNonNull(t);
                setRelease(t);
                // Allow the underlying supplier to be collected after use
                underlying = null;
            }
            return t;
        }
    }

    @ForceInline
    @Override
    public T orElse(T other) {
        final T t = contentsAcquire();
        return (t == null) ? other : t;
    }

    @ForceInline
    @Override
    public boolean isSet() {
        return contentsAcquire() != null;
    }

    // The methods equals() and hashCode() should be based on identity (defaults from Object)

    @Override
    public String toString() {
        final T t = contentsAcquire();
        return t == this ? "(this ComputedConstant)" : renderConstant(t);
    }

    @SuppressWarnings("unchecked")
    @ForceInline
    private T contentsAcquire() {
        return (T) UNSAFE.getReferenceAcquire(this, CONSTANT_OFFSET);
    }

    private void setRelease(T newValue) {
        UNSAFE.putReferenceRelease(this, CONSTANT_OFFSET, newValue);
    }

    private void preventReentry() {
        if (Thread.holdsLock(this)) {
            throw new IllegalStateException("Recursive invocation of a ComputedConstant's underlying supplier: " + underlying);
        }
    }

    public static String renderConstant(Object t) {
        return (t == null) ? UNSET_LABEL : Objects.toString(t);
    }


    // Factories

    public static <T> ComputedConstantImpl<T> ofComputed(Supplier<? extends T> underlying) {
        return new ComputedConstantImpl<>(underlying);
    }

    public static <T> ComputedConstantImpl<T> ofPreset(T contents) {
        return new ComputedConstantImpl<>(contents);
    }

}
