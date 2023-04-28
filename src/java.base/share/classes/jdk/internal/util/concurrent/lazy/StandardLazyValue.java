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

import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.concurrent.lazy.LazyValue;
import java.util.function.Supplier;

public final class StandardLazyValue<V> implements LazyValue<V> {

    // Allows access to the "value" field with arbitary memory semantics
    private static final VarHandle VALUE_HANDLE = valueHandle();

    /**
     * This field holds a bound lazy value.
     * If != null, a value is bound
     */
    @Stable
    private V value;

    /**
     * This field is used for:
     *   0) Holding the initial supplier
     *   1) Flagging if the value is being constucted or bound using null
     */
    private Supplier<? extends V> supplier;

    public StandardLazyValue(Supplier<? extends V> supplier) {
        this.supplier = supplier;
    }

    @ForceInline
    @Override
    public boolean isBound() {
        // Try normal memory semantics first
        V v = value;
        if (v != null) {
            return true;
        }
        v = valueVolatile();
        return v != null;
    }


    @ForceInline
    @Override
    public V get() {
        // Try normal memory semantics first
        V v = value;
        if (v != null) {
            return v;
        }
        return tryBind(null, true);
    }

    @ForceInline
    @Override
    public V orElse(V other) {
        // Try normal memory semantics first
        V v = value;
        if (v != null) {
            return v;
        }
        return tryBind(other, false);
    }

    @ForceInline
    @Override
    public <X extends Throwable> V orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        V v = orElse(null);
        if (v == null) {
            throw exceptionSupplier.get();
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    private synchronized V tryBind(V other,
                                   boolean rethrow) {

        // Under synchronization, visibility and atomicy is guaranteed
        V v = value;
        if (v != null) {
            return v;
        }
        var a = supplier;
        if (a == null) {
            throw new IllegalStateException("Circular supplier detected");
        }

        // Mark as constructing using null.
        supplier = null;
        try {
            v = ((Supplier<V>) a).get();
        } catch (Throwable e) {
            // Restore the pre-set supplier as no value was bound
            supplier = a;
            if (rethrow) {
                throw e;
            }
            return other;
        }

        if (v == null) {
            // Restore the pre-set supplier as no value was bound
            supplier = a;
            return other;
        }

        // Bind the value and leave the pre-set supplier
        // as null so it may be collected
        valueVolatile(v);
        return v;
    }

    @Override
    public final String toString() {
        V v = valueVolatile();
        return "StandardLazyValue" +
                (v != null
                        ? ("[" + value + "]")
                        : ".unbound");
    }

    V valueVolatile() {
        return (V) VALUE_HANDLE.getVolatile(this);
    }

    void valueVolatile(Object o) {
        VALUE_HANDLE.setVolatile(this, o);
    }

    static VarHandle valueHandle() {
        try {
            var lookup = MethodHandles.lookup();
            return lookup
                    .findVarHandle(StandardLazyValue.class, "value", Object.class);
            // .withInvokeExactBehavior(); // Make sure no boxing is made?
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

}
