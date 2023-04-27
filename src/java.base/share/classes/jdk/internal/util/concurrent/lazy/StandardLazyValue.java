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
import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.lazy.LazyValue;
import java.util.function.Supplier;

import static jdk.internal.util.concurrent.lazy.LazyUtil.PRESENT_FLAG;

public final class StandardLazyValue<V> implements LazyValue<V> {

    // Allows access to the "value" field with arbitary memory semantics
    private static final VarHandle VALUE_HANDLE;

    // Allows access to the "value" field with arbitary memory semantics
    private static final VarHandle AUX_HANDLE;

    static {
        try {
            var lookup = MethodHandles.lookup();
            VALUE_HANDLE = lookup
                    .findVarHandle(StandardLazyValue.class, "value", Object.class);
            // .withInvokeExactBehavior(); // Make sure no boxing is made?
            AUX_HANDLE = lookup
                    .findVarHandle(StandardLazyValue.class, "aux", Object.class);
            // .withInvokeExactBehavior(); // Make sure no boxing is made?
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // This field holds the lazy value. If != null, a valid value exist
    @Stable
    private V value;

    // This auxillary field has four purposes:
    // 0) Holds the initial supplier
    // 1) Flags if the Lazy is being constucted using the current thread as a maker Object
    // 2) Holds a Throwable, if the computation of the value failed.
    // 3) Holds PRESENT_FLAG, if the computation of the value succeeded.
    private Object aux;

    public StandardLazyValue(Supplier<? extends V> supplier) {
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
            // Here, visibility and atomicy is guaranteed
            v = value;
            if (v != null) {
                return v;
            }
            var a = aux;
            switch (a) {
                case Throwable t -> throw new NoSuchElementException(t);
                case Thread  t   -> throw new IllegalStateException("Circular supplier detected");
                default          -> { /* do nothing */ }
            }

            // Mark as CONSTRUCTING and make the pre-configured supplier eligeable for collection
            AUX_HANDLE.setVolatile(this, Thread.currentThread());
            try {
                v = ((Supplier<V>) a).get();
            } catch (Throwable e) {
                // Record the throwable
                AUX_HANDLE.setVolatile(this, e);
                // Rethrow
                throw e;
            }

            // The suppler must not return null
            if (v == null) {
                var npe = new NullPointerException("Supplier returned null: " + a);
                AUX_HANDLE.setVolatile(this, npe);
                throw npe;
            }

            // Record the value
            VALUE_HANDLE.setVolatile(this, v);
            // Clear CONSTRUCTING mode
            AUX_HANDLE.setVolatile(this, PRESENT_FLAG);
            return v;
        }
    }

    /**
     * {@return the {@link LazyState State} of this Lazy}.
     * <p>
     * The value is a snapshot of the current State.
     * No attempt is made to compute a value if it is not already present.
     * <p>
     * If the returned State is either {@link LazyState#PRESENT} or
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
            return LazyState.PRESENT;
        }

        Object a = aux;
        if (a instanceof Thread) {
            return LazyState.CONSTRUCTING;
        }
        if (a instanceof Throwable) {
            return LazyState.ERROR;
        }

        // Retry with volatile memory semantics
        o = VALUE_HANDLE.getVolatile(this);
        if (o != null) {
            return LazyState.PRESENT;
        }

        a = AUX_HANDLE.getVolatile(this);
        if (a instanceof Thread) {
            return LazyState.CONSTRUCTING;
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
        // Avoid race conditions by initially just observing aux
        Object a = AUX_HANDLE.getVolatile(this);
        return "StandardLazyValue[" + switch (a) {
            case Supplier<?> s -> LazyState.EMPTY.toString();
            case Thread      t -> LazyState.CONSTRUCTING + " [" + t + "]";
            case Throwable   t -> LazyState.ERROR + " [" + t.getClass().getName() + "]";
            // If a is PresentFlag, a valid value must exit
            case PresentFlag p -> VALUE_HANDLE.getVolatile(this).toString();
            default            -> throw new InternalError("should not reach here");
        } + "]";

    }

}
