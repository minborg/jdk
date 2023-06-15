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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.lazy.LazyArray;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public abstract sealed class AbstractLazyArray<V>
        implements LazyArray<V>
        permits DoubleLazyArray,
        IntLazyArray,
        LongLazyArray,
        ReferenceLazyArray {

    private static final VarHandle LOCKS_HANDLE = MethodHandles.arrayElementVarHandle(Object[].class);
    private IntFunction<? extends V> presetMapper;

    private AtomicInteger remainsToBind;
    private volatile Object[] locks;

    public AbstractLazyArray(int length,
                             IntFunction<? extends V> presetMapper) {
        this.presetMapper = presetMapper;
        this.remainsToBind = new AtomicInteger(length);
        this.locks = IntStream.range(0, length)
                .mapToObj(i -> new LockObject())
                .toArray(Object[]::new);
    }

    @Override
    public boolean isBinding(int index) {
        return lockVolatile(locks, index) instanceof LazyUtil.Binding;
    }

    @Override
    public final boolean isBound(int index) {
        // Try normal memory semantics first
        return !isDefaultValueAtIndex(index) ||
                isBoundVolatile(index);
    }

    @Override
    public boolean isError(int index) {
        return lockVolatile(locks, index) instanceof LazyUtil.Error;
    }

    @ForceInline
    @Override
    public final V get(int index) {
        // Try normal memory semantics first
        V v = value(index);
        if (!isDefaultValue(v)) {
            return v;
        }
        if (isBound(index)) {
            return valueVolatile(index);
        }
        return tryBind(index, null, true);
    }

    @ForceInline
    @Override
    public final V orElse(int index, V other) {
        // Try normal memory semantics first
        V v = value(index);
        if (!isDefaultValue(v)) {
            return v;
        }
        if (isBound(index)) {
            return valueVolatile(index);
        }
        return tryBind(index, null, true);
    }

    @ForceInline
    @Override
    public final <X extends Throwable> V orElseThrow(int index, Supplier<? extends X> exceptionSupplier) throws X {
        V v = orElse(index, null);
        if (v == null) {
            throw exceptionSupplier.get();
        }
        return v;
    }

    @Override
    public final Stream<V> stream() {
        return IntStream.range(0, length())
                .mapToObj(this::get);
    }

    @Override
    public final Stream<V> stream(V other) {
        return IntStream.range(0, length())
                .mapToObj(i -> orElse(i, other));
    }

    @Override
    public final String toString() {
        return getClass().getSimpleName()+"[" + IntStream.range(0, length())
                .mapToObj(i ->
                        isBoundVolatile(i)
                                ? Objects.toString(valueVolatile(i))
                                : "-"
                )
                .collect(Collectors.joining(", ")) + "]";
    }

    private V tryBind(int index,
                      V other,
                      boolean rethrow) {

        Object[] l = locks;
        // Try normal memory semantics
        if (l == null || l[index] instanceof LazyUtil.Bound) {
            return valueVolatile(index);
        }

        return switch (lockVolatile(l , index)) {
            case LazyUtil.NonNull __ -> valueVolatile(index);
            case LazyUtil.Null __ -> null;
            case LazyUtil.Binding __ ->
                    throw new StackOverflowError("Circular mapper detected for index: " + index);
            case LazyUtil.Error __ ->
                    throw new NoSuchElementException("A previous mapper threw an exception for index: " + index);
            case LockObject lockObject -> {
                synchronized (lockObject) {
                    // We are alone here for this index
                    V v = value(index);
                    if (!isDefaultValue(v) || isBound(index)) {
                        yield v;
                    }
                    setLockVolatile(l, index, LazyUtil.BINDING_SENTINEL);
                    try {
                        v = presetMapper.apply(index);
                        if (v == null) {
                            setLockVolatile(l, index, LazyUtil.NULL_SENTINEL);
                        } else {
                            casValue(index, v);
                            setLockVolatile(l, index, LazyUtil.NON_NULL_SENTINEL);
                        }
                        yield v;
                    } catch (Throwable e) {
                        setLockVolatile(l, index, LazyUtil.ERROR_SENTINEL);
                        if (e instanceof Error err) {
                            // Always rethrow errors
                            throw err;
                        }
                        if (rethrow) {
                            throw e;
                        }
                        yield other;
                    } finally {
                        remainsToBind.getAndDecrement();
                        if (remainsToBind.get() == 0) {
                            // All elements are bound
                            // Make these objects eligible for collection
                            locks = null;
                            presetMapper = null;
                            remainsToBind = null;
                        }
                    }
                }
            }
            default -> throw new InternalError("Should not reach here");
        };
    }

    abstract boolean isDefaultValue(V value);

    abstract boolean isDefaultValueAtIndex(int index);

    abstract boolean isDefaultValueVolatileAtIndex(int index);

    abstract V value(int index);

    abstract V valueVolatile(int index);

    abstract void casValue(int index, V value);

    Object lockVolatile(Object[] locks, int index) {
        return LOCKS_HANDLE.getVolatile(locks, index);
    }

    void setLockVolatile(Object[] locks, int index, Object value) {
        LOCKS_HANDLE.setVolatile(locks, index, value);
    }

    boolean isBoundVolatile(int index) {
        Object[] l = locks;
        if (l == null) {
            return true;
        }
        return lockVolatile(l, index) instanceof LazyUtil.Bound;
    }

    private static final class LockObject {
        //boolean isBinding;
    }

}
