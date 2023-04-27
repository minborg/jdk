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
     // 2) Holds a Throwable, if the computation of the value failed.
     // 3) Holds the value, if the computation of the value succeeded.
     */
    private Object value;

    public CompactLazyValue(Supplier<? extends V> supplier) {
        // Because supplier is set in the constructor, it can always be
        // observed by any thread with normal memory semantics.
        this.value = supplier;
    }

    @SuppressWarnings("unchecked")
    @Override
    public V get() {
        Object v = value;
        // Todo: replace with switch once "JEP 443: Unnamed Patterns and Variables" becomes available
        if (v instanceof Supplier<?> || v instanceof Thread) {
            synchronized (this) {
                // "value" is only updated within this synchronization so normal
                // semantics suffice here
                v = value;
                return switch (v) {
                    case Supplier<?> supplier1 -> {
                        VALUE_HANDLE.setVolatile(this, Thread.currentThread());
                        try {
                            V newValue = (V) supplier1.get();
                            // Enforce supplier invariants
                            // Todo: Use an object header bit to flag exceptional states and allow these types
                            switch (newValue) {
                                case Supplier<?> s2 -> throw new IllegalStateException("Supplier returned a Supplier: " + s2);
                                case Throwable    t -> throw new IllegalStateException("Supplier returned a Throwable", t);
                                case Thread       t -> throw new IllegalStateException("Supplier returned a Thread:" + t);
                                case null           -> throw new NullPointerException("Suppler returned null");
                                default             -> {
                                    /* We have a valid value, do nothing */
                                }
                            }
                            VALUE_HANDLE.setVolatile(this, newValue);
                            yield newValue;
                        } catch (Throwable t) {
                            VALUE_HANDLE.setVolatile(this, t);
                            throw t;
                        }
                    }
                    case Throwable throwable -> {
                        throw new NoSuchElementException(throwable);
                    }
                    default -> (V) v;
                };
            }
        }
        if (v instanceof Throwable throwable) {
            throw new NoSuchElementException(throwable);
        }
        return (V) v;
    }

    public final LazyState state() {
         return switch (VALUE_HANDLE.getVolatile(this)) {
            case Supplier<?> s -> LazyState.EMPTY;
            case Throwable   t -> LazyState.ERROR;
            case Thread      t -> LazyState.CONSTRUCTING;
            default            -> LazyState.PRESENT;
        };
    }

    @Override
    public final Optional<Throwable> exception() {
        return VALUE_HANDLE.getVolatile(this) instanceof Throwable throwable
                ? Optional.of(throwable)
                : Optional.empty();
    }

    @SuppressWarnings("unchecked")
    @Override
    public V getOr(V defaultValue) {
        Object v = VALUE_HANDLE.getVolatile(this);
        return switch (v) {
            case Supplier<?> s -> defaultValue;
            case Throwable t   -> throw new NoSuchElementException(t);
            case Thread t      -> defaultValue;
            default            -> (V) v;
        };
    }

    @Override
    public final String toString() {
        Object v = VALUE_HANDLE.getVolatile(this);
        return "CompactLazyValue[" +
                switch (v) {
                    case Supplier<?> s -> LazyState.EMPTY;
                    case Thread t      -> LazyState.CONSTRUCTING+ " [" + t + "]";
                    case Throwable t   -> LazyState.ERROR + " [" + t.getClass().getName() + "]";
                    default            -> v.toString();
                }
                + "]";
    }

}
