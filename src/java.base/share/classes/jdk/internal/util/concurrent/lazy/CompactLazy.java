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

import jdk.internal.util.concurrent.lazy.LazyUtil.ConstructingFlag;
import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.lazy.Lazy;
import java.util.concurrent.lazy.LazyState;
import java.util.function.Supplier;

public final class CompactLazy<V> implements Lazy<V> {

    // Allows access to the "value" field with arbitary memory semantics
    private static final VarHandle VALUE_HANDLE;

    static {
        try {
            var lookup = MethodHandles.lookup();
            VALUE_HANDLE = lookup
                    .findVarHandle(CompactLazy.class, "value", Object.class);
            // .withInvokeExactBehavior(); // Make sure no boxing is made?
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private volatile Object value;

    public CompactLazy(Supplier<? extends V> supplier) {
        this.value = supplier;
    }

    @SuppressWarnings("unchecked")
    @Override
    public V get() {
        Object v = value;
        return switch (v) {
            case Supplier<?> supplier -> {
                synchronized (this) {
                    v = value;
                    yield switch (v) {
                        case Supplier<?> supplier1 -> {
                            value = LazyUtil.CONSTRUCTIING_FLAG;
                            try {
                                V newValue = (V) supplier1.get();
                                // Enforce
                                switch (newValue) {
                                    case Supplier<?> s2 ->
                                            throw new IllegalStateException("Supplier returned a Supplier: " + supplier1);
                                    case Throwable t ->
                                            throw new IllegalStateException("Supplier returned a Throwable", t);
                                    default -> {
                                        /* do nothing */
                                    }
                                }
                                value = newValue;
                                yield newValue;
                            } catch (Throwable t) {
                                value = t;
                                throw t;
                            }
                        }
                        case Throwable throwable -> {
                            throw new NoSuchElementException((Throwable) v);
                        }
                        default -> (V) v;
                    };
                }
            }
            case Throwable throwable -> {
                throw new NoSuchElementException((Throwable) value);
            }
            default -> (V) v;
        };
    }

    @Override
    public final LazyState state() {
        return switch (value) {
            case Supplier<?>      s -> LazyState.EMPTY;
            case Throwable        t -> LazyState.ERROR;
            case ConstructingFlag c -> LazyState.CONSTRUCTING;
            default                 -> LazyState.PRESENT;
        };
    }

    @Override
    public final Optional<Throwable> exception() {
        return value instanceof Throwable throwable
                ? Optional.of(throwable)
                : Optional.empty();
    }

    @SuppressWarnings("unchecked")
    @Override
    public V getOr(V defaultValue) {
        Object v = value;
        return switch (v) {
            case Supplier<?> s      -> defaultValue;
            case Throwable t        -> throw new NoSuchElementException(t);
            case ConstructingFlag s -> defaultValue;
            default                 -> (V) v;
        };
    }

    @Override
    public final String toString() {
        return "CompactLazy[" +
                switch (state()) {
                    case EMPTY        -> LazyState.EMPTY;
                    case CONSTRUCTING -> LazyState.CONSTRUCTING;
                    case PRESENT      -> value;
                    case ERROR        -> LazyState.ERROR + " [" + value.getClass().getName() + "]";
                }
                + "]";
    }

}
