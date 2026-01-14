/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.lang;

import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;

import java.util.NoSuchElementException;
import java.util.function.Supplier;

public sealed abstract class AbstractRigidValue<T> implements RigidValue<T> permits  ResettableRigidValue, StableRigidValue{

    @ForceInline
    @Override
    public final T get() {
        final T result = get0();
        if (result == null) {
            throw new NoSuchElementException("No value present");
        }
        return result;
    }

    @ForceInline
    @Override
    public final T orElse(T other) {
        final T result = get0();
        return result == null ? other : result;
    }

    @ForceInline
    @Override
    public final T orElseGet(Supplier<T> supplier) {
        final T result = get0();
        return result == null ? supplier.get() : result;
    }

    @ForceInline
    @Override
    public final boolean isSet() {
        return get0() != null;
    }

    @ForceInline
    @Override
    public final void set(T value) {
        preventReentry(null);
        synchronized (this) {
            if (isSet() && isStable()) {
                throw new IllegalStateException();
            }
            set0(value);
        }
    }

    @ForceInline
    @Override
    public final T orElseSet(Supplier<T> supplier) {
        final T result = get0();
        return result == null
                ? orElseSetSlowPath(supplier)
                : result;
    }

    @DontInline
    private T orElseSetSlowPath(Supplier<T> supplier) {
        preventReentry(supplier);
        synchronized (this) {
            T result = get0();
            if (result != null) {
                return result;
            }
            result = supplier.get();
            set0(result);
            return result;
        }
    }

    @Override
    public final boolean compareAndSet(T expect, T update) {
        throw new UnsupportedOperationException("Fix me");
    }

    @Override
    public final String toString() {
        final T result = get0();
        return result == null
                ? ".unset"
                : result == this ? "(this RigidValue)" : result.toString();
    }

    abstract boolean isStable();

    abstract T get0();

    abstract void set0(T newValue);

    private void preventReentry(Object computingFunction) {
        if (Thread.holdsLock(this)) {
            throw new IllegalStateException("Recursive invocation of a computing function" + (computingFunction != null ? ": " + computingFunction : ""));
        }
    }

}
