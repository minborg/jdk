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

import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.lazy.LazyArray;
import java.util.function.IntFunction;

public final class ReferenceLazyArray<V>
        extends AbstractLazyArray<V>
        implements LazyArray<V> {

    private static final VarHandle VALUES_HANDLE = MethodHandles.arrayElementVarHandle(Object[].class);

    @Stable
    private final V[] values;

    @SuppressWarnings("unchecked")
    public ReferenceLazyArray(int length,
                              IntFunction<? extends V> presetMapper) {
        super(length, presetMapper);
        this.values = (V[]) new Object[length];
    }

    @Override
    public int length() {
        return values.length;
    }

    @Override
    boolean isDefaultValue(V value) {
        return value == null;
    }

    @Override
    boolean isDefaultValueAtIndex(int index) {
        return values[index] == null;
    }

    @Override
    boolean isDefaultValueVolatileAtIndex(int index) {
        return VALUES_HANDLE.getVolatile(values, index) == null;
    }

    @Override
    V value(int index) {
        return values[index];
    }

    @Override
    V valueVolatile(int index) {
        return (V) VALUES_HANDLE.getVolatile(values, index);
    }

    @Override
    void casValue(int index, V value) {
        VALUES_HANDLE.compareAndSet(values, index, null, value);
    }
}
