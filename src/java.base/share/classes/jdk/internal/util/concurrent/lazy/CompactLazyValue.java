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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.lazy.LazyValue;
import java.util.function.Supplier;

public final class CompactLazyValue<V> implements LazyValue<V> {

    // Allows access to the "value" field with arbitary memory semantics
    private static final VarHandle VALUE_HANDLE;

    static {
        try {
            var lookup = MethodHandles.lookup();
            VALUE_HANDLE = lookup
                    .findVarHandle(CompactLazyValue.class, "value", Object.class);
            // .withInvokeExactBehavior(); // Make sure no boxing is made?
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Value can assume the following meanings:
     // 0) Holds the initial supplier
     // 1) Flags if the Lazy is being constucted using the current thread as a maker Object
     // 3) Holds the value, if a value is bound
     */
    private Object value;

    public CompactLazyValue(Supplier<? extends V> supplier) {
        // Because supplier is set in the constructor, it can always be
        // observed by any thread with normal memory semantics.
        this.value = supplier;
    }

    @Override
    public boolean isBound() {
        // Try normal memory semantics first
        Object v = value;
        if (!isUnBound(v)) {
            return true;
        }
        v = VALUE_HANDLE.getVolatile(this);
        return !isUnBound(v);
    }

    @SuppressWarnings("unchecked")
    @Override
    public V get() {
        // Try normal memory semantics first
        Object v = value;
        if (isUnBound(v)) {
            return get0(null, true);
        }
        return (V) v;
    }

    @SuppressWarnings("unchecked")
    @Override
    public V orElse(V other) {
        // Try normal memory semantics first
        Object v = value;
        if (isUnBound(v)) {
            return get0(other, false);
        }
        return (V) v;
    }

    @Override
    public <X extends Throwable> V orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        return null;
    }

    @SuppressWarnings("unchecked")
    private synchronized V get0(V other,
                                boolean rethrow) {
            // "value" is only updated within this synchronization so normal
            // semantics suffice here
            Object v = value;
            return switch (v) {
                case Supplier<?> supplier1 -> {
                    VALUE_HANDLE.setVolatile(this, Thread.currentThread());
                    V valueToBind;
                    try {
                        V newValue = (V) supplier1.get();
                        // Enforce supplier invariants
                        // Todo: Use an object header bit to flag exceptional states and allow these types
                        yield switch (newValue) {
                            case Supplier<?> s2 -> {
                                throw new IllegalStateException("Supplier returned a Supplier: " + s2);
                            }
                            case Thread t -> {
                                throw new IllegalStateException("Supplier returned a Thread:" + t);
                            }
                            case null -> {
                                // Restore the pre-set supplier as no value was bound
                                VALUE_HANDLE.setVolatile(this, supplier1);
                                yield other;
                            }
                            default -> {
                                // Bind the value
                                VALUE_HANDLE.setVolatile(this, newValue);
                                yield newValue;
                            }
                        };
                    } catch (Throwable t) {
                        // Restore the pre-set supplier as no value was bound
                        VALUE_HANDLE.setVolatile(this, supplier1);
                        throw t;
                    }
                }
                case Thread t -> {
                    throw new IllegalStateException("Circular supplier detected");
                }
                default -> (V) v;
            };
    }

    public final LazyState state() {
         return switch (VALUE_HANDLE.getVolatile(this)) {
            case Supplier<?> s -> LazyState.UNBOUND;
            case Thread      t -> LazyState.CONSTRUCTING;
            default            -> LazyState.BOUND;
        };
    }

    @Override
    public final String toString() {
        Object v = VALUE_HANDLE.getVolatile(this);
        return "CompactLazyValue[" +
                switch (v) {
                    case Supplier<?> s -> LazyState.UNBOUND;
                    case Thread t      -> LazyState.CONSTRUCTING+ " [" + t + "]";
                    default            -> v.toString();
                }
                + "]";
    }

    private boolean isUnBound(Object o) {
        return (o instanceof Supplier<?> || o instanceof Thread);
    }

}
