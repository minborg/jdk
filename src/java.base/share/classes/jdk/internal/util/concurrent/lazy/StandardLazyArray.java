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
import java.util.BitSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.lazy.LazyArray;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class StandardLazyArray<V>  implements LazyArray<V> {

    private static final VarHandle VALUES_HANDLE = MethodHandles.arrayElementVarHandle(Object[].class);
    private static final VarHandle LOCKS_HANDLE = MethodHandles.arrayElementVarHandle(LockObject[].class);

    private IntFunction<? extends V> presetMapper;

    private AtomicInteger remainsToBind;
    private volatile LockObject[] locks;

    @Stable
    private final V[] values;

    @SuppressWarnings("unchecked")
    public StandardLazyArray(int length,
                             IntFunction<? extends V> presetMapper) {
        this.presetMapper = presetMapper;
        this.remainsToBind = new AtomicInteger(length);
        this.locks = IntStream.range(0, length)
                .mapToObj(i -> new LockObject())
                .toArray(LockObject[]::new);
        this.values = (V[]) new Object[length];
    }

    @Override
    public final int length() {
        return values.length;
    }

    @Override
    public boolean isBound(int index) {
        // Try normal memory semantics first
        return values[index] != null || valueVolatile(index) != null;
    }

    @ForceInline
    @Override
    public V get(int index) {
        // Try normal memory semantics first
        V v = values[index];
        if (v != null) {
            return v;
        }
        return tryBind(index, null, true);
    }

    @ForceInline
    @Override
    public V orElse(int index, V other) {
        // Try normal memory semantics first
        V v = values[index];
        if (v != null) {
            return v;
        }
        return tryBind(index, null, true);
    }

    @ForceInline
    @Override
    public <X extends Throwable> V orElseThrow(int index, Supplier<? extends X> exceptionSupplier) throws X {
        V v = orElse(index, null);
        if (v == null) {
            throw exceptionSupplier.get();
        }
        return v;
    }

    @Override
    public Stream<V> stream() {
        return IntStream.range(0, length())
                .mapToObj(i -> get(i));
    }

    @Override
    public Stream<V> stream(V other) {
        return IntStream.range(0, length())
                .mapToObj(i -> orElse(i, other));
    }

    @Override
    public String toString() {
        return "StandardLazyArray[" + IntStream.range(0, length())
                .mapToObj(this::valueVolatile)
                .map(v -> v == null ? "-" : v.toString())
                .collect(Collectors.joining(", ")) + "]";
    }

    @SuppressWarnings("unchecked")
    private V tryBind(int index,
                      V other,
                      boolean rethrow) {

        if (locks == null) {
            // All elements are bound so we know we can read any bound value
            return valueVolatile(index);
        }
        LockObject lock = lockVolatile(index);
        if (lock == null) {
            // There is no lock for this index so we know we can read the coresponding bound value
            return valueVolatile(index);
        }
        synchronized (lock) {
            // We are alone here for this index
            V v = valueVolatile(index);
            if (v != null) {
                return v;
            }

            if (lock.isBinding) {
                throw new IllegalStateException("Circular supplier detected");
            }
            try {
                lock.isBinding = true;
                v = presetMapper.apply(index);
            } catch (Throwable e) {
                if (rethrow) {
                    throw e;
                }
                return other;
            } finally {
                lock.isBinding = false;
            }

            if (v == null) {
                return other;
            }

            // Bind the value
            casValue(index, v);
            // Remove the now redundant lock
            clearLock(index);
            remainsToBind.getAndDecrement();
            if (remainsToBind.get() == 0) {
                // All elements are bound
                // Make these objects elegable for collection
                presetMapper = null;
                locks = null;
                remainsToBind = null;
            }
            return v;
        }
    }

    @SuppressWarnings("unchecked")
    V valueVolatile(int i) {
        return (V) VALUES_HANDLE.getVolatile(values, i);
    }

    void casValue(int i, Object o) {
        if (!VALUES_HANDLE.compareAndSet(values, i, null, o)) {
            throw new InternalError();
        }
    }

    @SuppressWarnings("unchecked")
    LockObject lockVolatile(int i) {
        return (LockObject) LOCKS_HANDLE.getVolatile(locks, i);
    }

    void clearLock(int i) {
        LOCKS_HANDLE.setVolatile(locks, i, null);
    }

    private static final class LockObject {
        boolean isBinding;
    }

}
