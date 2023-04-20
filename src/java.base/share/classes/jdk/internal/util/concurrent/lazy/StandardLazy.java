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

import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.lazy.Lazy;
import java.util.concurrent.lazy.LazyState;
import java.util.function.Supplier;

import static java.util.concurrent.lazy.LazyState.CONSTRUCTING;

public final class StandardLazy<V> implements Lazy<V> {

    // Allows access to the "value" field with arbitary memory semantics
    private static final VarHandle VALUE_HANDLE;

    // Allows access to the "value" field with arbitary memory semantics
    private static final VarHandle AUX_HANDLE;

    static {
        try {
            var lookup = MethodHandles.lookup();
            VALUE_HANDLE = lookup
                    .findVarHandle(StandardLazy.class, "value", Object.class);
            // .withInvokeExactBehavior(); // Make sure no boxing is made?
            AUX_HANDLE = lookup
                    .findVarHandle(StandardLazy.class, "aux", Object.class);
            // .withInvokeExactBehavior(); // Make sure no boxing is made?
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // This field holds the lazy value. If != null, a valid value exist
    @Stable
    private V value;

    // This auxillary field has three purposes:
    // 0) Holds the initial supplier
    // 1) Flag if the Lazy is being constucted using the special CONSTRUCTING_FLAG Object
    // 2) Holds a Throwable, if the computation of the value failed.
    private Object aux;

    public StandardLazy(Supplier<? extends V> supplier) {
        this.aux = supplier;
    }

    @SuppressWarnings("unchecked")
    @Override
    public V get() {
        // Try normal memory semantics first
        V v = value;
        if (v != null) {
            return v;
        }

        synchronized (this) {
            // Here, visibility is guaranteed
            v = value;
            if (v != null) {
                return v;
            }
            var a = aux;
            if (a instanceof Throwable throwable) {
                throw new NoSuchElementException(throwable);
            }
            try {
                // Mark as CONSTRUCTING and make the pre-configured supplier eligeable for collection
                aux = LazyUtil.CONSTRUCTIING_FLAG;
                v = ((Supplier<V>) a).get();
                if (v == null) {
                    throw new NullPointerException("Supplier returned null: " + a);
                }
                VALUE_HANDLE.setVolatile(this, v);
                // Clear CONSTRUCTING mode
                AUX_HANDLE.setVolatile(this, null);
                return v;
            } catch (Throwable e) {
                // Record the throwable instead of the value.
                AUX_HANDLE.setVolatile(this, e);
                // Rethrow
                throw e;
            }
        }
    }

    @Override
    public final LazyState state() {
        // Try normal memory semantics first
        Object o = value;
        if (o != null) {
            return LazyState.PRESENT;
        }

        Object a = aux;
        if (a == LazyUtil.CONSTRUCTIING_FLAG) {
            return CONSTRUCTING;
        }
        if (a instanceof Throwable throwable) {
            return LazyState.ERROR;
        }

        // Retry with volatile memory semantics
        o = VALUE_HANDLE.getVolatile(this);
        if (o != null) {
            return LazyState.PRESENT;
        }

        a = AUX_HANDLE.getVolatile(this);
        if (a == LazyUtil.CONSTRUCTIING_FLAG) {
            return CONSTRUCTING;
        }
        if (a instanceof Throwable throwable) {
            return LazyState.ERROR;
        }

        return LazyState.EMPTY;
    }

    @Override
    public final Optional<Throwable> exception() {

        // Try normal memory semantics first
        Object aux = this.aux;
        if (this.aux instanceof Throwable throwable) {
            return Optional.of(throwable);
        }

        // Retry with volatile memory semantics
        aux = AUX_HANDLE.getVolatile(this);
        return (this.aux instanceof Throwable throwable)
                ? Optional.of(throwable)
                : Optional.empty();
    }

    @Override
    public V getOr(V defaultValue) {
        V v = value;
        if (v != null) {
            return v;
        }
        v = (V) VALUE_HANDLE.getVolatile(this);
        if (v != null) {
            return v;
        }

        // Skip normal memory semantics as we have to try volatile semantics anyhow
        if (AUX_HANDLE.getVolatile(this) instanceof Throwable throwable) {
            throw new NoSuchElementException(throwable);
        }
        return defaultValue;
    }

    @Override
    public final String toString() {
        return "StandardLazy[" +
                switch (state()) {
                    case EMPTY -> LazyState.EMPTY;
                    case CONSTRUCTING -> CONSTRUCTING;
                    case PRESENT -> value;
                    case ERROR -> LazyState.ERROR + " [" + aux.getClass().getName() + "]";
                }
                + "]";
    }

}
