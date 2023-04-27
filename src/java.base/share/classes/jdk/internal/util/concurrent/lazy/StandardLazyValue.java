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

import jdk.internal.util.concurrent.lazy.LazyUtil.PresentFlag;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Optional;
import java.util.concurrent.lazy.LazyValue;
import java.util.function.Supplier;

import static jdk.internal.util.concurrent.lazy.LazyUtil.PRESENT_FLAG;

public final class StandardLazyValue<V> implements LazyValue<V> {

    // Allows access to the "value" field with arbitary memory semantics
    private static final VarHandle VALUE_HANDLE;

    private static final VarHandle AUX_HANDLE;

    static {
        try {
            var lookup = MethodHandles.lookup();
            VALUE_HANDLE = lookup
                    .findVarHandle(StandardLazyValue.class, "value", Object.class);
            AUX_HANDLE = lookup
                    .findVarHandle(StandardLazyValue.class, "aux", Object.class);
            // .withInvokeExactBehavior(); // Make sure no boxing is made?
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // This field holds the bound lazy value. If != null, a valid value is bound
    @Stable
    private V value;

    /**
     * aux can assume the following meanings:
     // 0) Holds the initial supplier
     // 1) Flags if the Lazy is being constucted using the current thread as a maker Object
     // 2) Holds a special value, if a value is bound.
     */
    private Object aux;

    public StandardLazyValue(Supplier<? extends V> supplier) {
        this.aux = supplier;
    }

    @ForceInline
    @Override
    public boolean isBound() {
        // Try normal memory semantics first
        Object v = value;
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

        // Here, visibility and atomicy is guaranteed under synchronization
        V v = value;
        if (v != null) {
            return v;
        }
        var a = aux;
        if (a instanceof Thread t) {
            throw new IllegalStateException("Circular supplier detected");
        }

        // Mark as CONSTRUCTING using the current thread.
        AUX_HANDLE.setVolatile(this, Thread.currentThread());
        try {
            v = ((Supplier<V>) a).get();
        } catch (Throwable e) {
            // Restore the pre-set supplier as no value was bound
            AUX_HANDLE.setVolatile(this, a);
            if (rethrow) {
                throw e;
            }
            return other;
        }

        if (v == null) {
            // Restore the pre-set supplier as no value was bound
            AUX_HANDLE.setVolatile(this, a);
            return other;
        }

        // Bind the value
        VALUE_HANDLE.setVolatile(this, v);
        // Clear CONSTRUCTING mode
        AUX_HANDLE.setVolatile(this, PRESENT_FLAG);
        return v;

    }

    /**
     * {@return the {@link LazyState State} of this Lazy}.
     * <p>
     * The value is a snapshot of the current State.
     * No attempt is made to compute a value if it is not already present.
     * <p>
     * If the returned State is either {@link LazyState#BOUND} or
     * {@link LazyState#ERROR}, it is guaranteed the state will
     * never change in the future.
     * <p>
     * This method can be used to act on a value if it is present:
     * {@snippet lang = java:
     *     if (lazy.state() == State.PRESENT) {
     *         // perform action on the value
     *     }
     *}
     */
    public final LazyState state() {
        // Try normal memory semantics first
        Object o = value;
        if (o != null) {
            return LazyState.BOUND;
        }

        Object a = aux;
        if (a instanceof Thread) {
            return LazyState.CONSTRUCTING;
        }

        // Retry with volatile memory semantics
        o = valueVolatile();
        if (o != null) {
            return LazyState.BOUND;
        }

        a = AUX_HANDLE.getVolatile(this);
        if (a instanceof Thread) {
            return LazyState.CONSTRUCTING;
        }

        return LazyState.UNBOUND;
    }

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
    public final String toString() {
        // Avoid race conditions by initially just observing aux
        Object a = AUX_HANDLE.getVolatile(this);
        return "StandardLazyValue[" + switch (a) {
            case Supplier<?> s -> LazyState.UNBOUND.toString();
            case Thread      t -> LazyState.CONSTRUCTING + " [" + t + "]";
            case PresentFlag p -> valueVolatile().toString();
            default            -> throw new InternalError("should not reach here");
        } + "]";

    }

    V valueVolatile() {
        return (V) VALUE_HANDLE.getVolatile(this);
    }

}
