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
package jdk.internal.util.lazy;

import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.concurrent.lazy.LazyReferenceArray;
import java.util.function.IntFunction;
import java.util.function.Supplier;

// The only implementation of the LazyReferenceArray interface. This provides
// oportunities to create other specialized versions in the future.
public final class StandardLazyReferenceArray<T>
        implements LazyReferenceArray<T> {

    private IntFunction<? extends T> presetMapper;
    private static final VarHandle ARRAY_VH = MethodHandles.arrayElementVarHandle(Object[].class);
    private static final VarHandle GATES_VH = MethodHandles.arrayElementVarHandle(byte[].class);

    @Stable
    private final Object[] array;

    private final byte[] gates;

    public StandardLazyReferenceArray(int size,
                                      IntFunction<? extends T> presetMapper) {
        this.array = new Object[size];
        this.gates = new byte[(size + 7) / 8];
        this.presetMapper = presetMapper;
    }

    @Override
    public int length() {
        return array.length;
    }

    @Override
    public T apply(int index) {
        T value;
        return (value = getAcquire(index)) == null
                ? computeIfEmpty0(index, presetMapper)
                : value;
    }

    @Override
    public T getOrNull(int index) {
        return getAcquire(index);
    }

    @Override
    public T computeIfEmpty(int index,
                            IntFunction<? extends T> mappper) {
        Objects.requireNonNull(mappper);
        return computeIfEmpty0(index, mappper);
    }

    private T computeIfEmpty0(int index,
                              IntFunction<? extends T> mapper) {
        T value = getAcquire(index);
        if (value == null) {
            boolean otherInGate;
            int gate = gate(index);
            byte gateBitValue = gateBitValue(index);
            do {
                byte previous = (byte) GATES_VH.getAndBitwiseOr(gates, gate, gateBitValue);
                otherInGate = (previous & gateBitValue) != 0;

                // Improve this later
                Thread.yield();
                Thread.onSpinWait();

            } while (otherInGate);

            // We are now alone
            try {
                value = getVolatile(index);
                if (value == null) {
                    if (mapper == null) {
                        throw new IllegalStateException("No pre-set mapper specified.");
                    }
                    value = mapper.apply(index);
                    if (value == null) {
                        throw new NullPointerException("The mapper returned null for index " + index + ": " + mapper);
                    }
                    setVolatile(index, value);
                }
            } finally {
                GATES_VH.getAndBitwiseAnd(gates, gate, (byte) ~gateBitValue);
            }

        }
        return value;
    }

    int gate(int index) {
        return index / 8;
    }

    byte gateBitValue(int index) {
        return (byte) (1 << (index % 8));
    }

    // Use acquire/release to ensure happens-before so that newly
    // constructed elements are always observed correctly in combination
    // with double-checked locking.
    private T getAcquire(int index) {
        return (T) ARRAY_VH.getAcquire(array, index);
    }

    void setRelease(int slot,
                    Object value) {
        ARRAY_VH.setRelease(array, slot, value);
    }

    private T getVolatile(int index) {
        return (T) ARRAY_VH.getVolatile(array, index);
    }

    void setVolatile(int slot,
                     Object value) {
        ARRAY_VH.setVolatile(array, slot, value);
    }

}
