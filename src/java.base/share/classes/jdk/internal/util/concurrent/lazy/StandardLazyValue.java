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
import java.util.NoSuchElementException;
import java.util.concurrent.lazy.LazyValue;
import java.util.function.Supplier;

import static jdk.internal.util.concurrent.lazy.LazyUtil.NON_NULL_SENTINEL;
import static jdk.internal.util.concurrent.lazy.LazyUtil.NULL_SENTINEL;

public final class StandardLazyValue<V> implements LazyValue<V> {

    // Allows access to the "value" field with arbitrary memory semantics
    private static final VarHandle VALUE_HANDLE;

    // Allows access to the "auxiliary" field with arbitrary memory semantics
    private static final VarHandle AUX_HANDLE;

    /**
     * This field holds a bound lazy value.
     * If != null, a value is bound, otherwise the auxiliary field needs to be consulted.
     */
    @Stable
    private V value;

    /**
     * This non-final auxiliary field is used for:
     *   0) Holding the initial supplier
     *   1) Flagging if the value is being constructed
     *   2) Flagging if the value was actually evaluated to null
     *   3) Flagging if the initial supplier threw an exception
     *   4) Flagging if a value is bound
     */
    private Object auxiliary;

    public StandardLazyValue(Supplier<? extends V> supplier) {
        this.auxiliary = supplier;
    }

    @Override
    public boolean isUnbound() {
        return auxiliaryVolatile() instanceof Supplier<?>;
    }

    @Override
    public boolean isBinding() {
        return auxiliaryVolatile() instanceof LazyUtil.Binding;
    }

    @ForceInline
    @Override
    public boolean isBound() {
        // Try normal memory semantics first
        return value != null || auxiliaryVolatile() instanceof LazyUtil.Bound;
    }

    @ForceInline
    @Override
    public boolean isError() {
        return auxiliaryVolatile() instanceof LazyUtil.Error;
    }

    @ForceInline
    @Override
    public V get() {
        // Try normal memory semantics first
        V v = value;
        if (v != null) {
            return v;
        }
        if (auxiliary instanceof LazyUtil.Null) {
            return null;
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
        if (auxiliary instanceof LazyUtil.Null) {
            return null;
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

        // Under synchronization, visibility and atomicy is guaranteed for both
        // the fields "value" and "auxiliary" as they are only changed within this block.
        V v = value;
        if (v != null) {
            return v;
        }
        return switch (auxiliary) {
            case LazyUtil.Null __ -> null;
            case LazyUtil.Binding __ ->
                    throw new StackOverflowError("Circular supplier detected");
            case LazyUtil.Error __ ->
                    throw new NoSuchElementException("A previous supplier threw an exception");
            case Supplier<?> supplier -> {
                setAuxiliaryVolatile(LazyUtil.BINDING_SENTINEL);
                try {
                    v = (V) supplier.get();
                    if (v == null) {
                        setAuxiliaryVolatile(NULL_SENTINEL);
                    } else {
                        casValue(v);
                        setAuxiliaryVolatile(NON_NULL_SENTINEL);
                    }
                    yield v;
                } catch (Throwable e) {
                    setAuxiliaryVolatile(LazyUtil.ERROR_SENTINEL);
                    if (e instanceof Error err) {
                        // Always rethrow errors
                        throw err;
                    }
                    if (rethrow) {
                        throw new NoSuchElementException(e);
                    }
                    yield other;
                }
            }
            default -> throw new InternalError("Should not reach here");
        };
    }

    @Override
    public String toString() {
        String v = switch (auxiliaryVolatile()) {
            case Supplier<?> __ -> ".unbound";
            case LazyUtil.Binding __ -> ".unbound";
            case LazyUtil.Null __ -> "null";
            case LazyUtil.NonNull __ -> "[" + valueVolatile().toString() + "]";
            case LazyUtil.Error __ -> ".error";
            default -> ".INTERNAL_ERROR";
        };
        return "StandardLazyValue" + v;
    }

    @SuppressWarnings("unchecked")
    V valueVolatile() {
        return (V) VALUE_HANDLE.getVolatile(this);
    }

    Object auxiliaryVolatile() {
        return AUX_HANDLE.getVolatile(this);
    }

    void casValue(Object o) {
        if (!VALUE_HANDLE.compareAndSet(this, null, o)) {
            throw new InternalError();
        }
    }

    void setAuxiliaryVolatile(Object o) {
        AUX_HANDLE.setVolatile(this, o);
    }

    static  {
        try {
            var lookup = MethodHandles.lookup();
            VALUE_HANDLE = lookup
                    .findVarHandle(StandardLazyValue.class, "value", Object.class);
            AUX_HANDLE = lookup
                    .findVarHandle(StandardLazyValue.class, "auxiliary", Object.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

}
