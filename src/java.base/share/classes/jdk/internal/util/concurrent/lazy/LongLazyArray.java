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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.lazy.LazyArray;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class LongLazyArray
        extends AbstractLazyArray<Long>
        implements LazyArray<Long> {

    private static final VarHandle VALUES_HANDLE = MethodHandles.arrayElementVarHandle(long[].class);

    @Stable
    private final long[] values;

    @SuppressWarnings("unchecked")
    public LongLazyArray(int length,
                         IntFunction<? extends Long> presetMapper) {
        super(length, presetMapper);
        this.values = new long[length];
    }

    @Override
    public int length() {
        return values.length;
    }

    @Override
    boolean isDefaultValue(Long value) {
        return value == 0;
    }

    @Override
    boolean isDefaultValueAtIndex(int index) {
        return values[index] == 0;
    }

    @Override
    boolean isDefaultValueVolatileAtIndex(int index) {
        return (long) VALUES_HANDLE.getVolatile(values, index) == 0;
    }

    @Override
    Long value(int index) {
        return values[index];
    }

    @Override
    Long valueVolatile(int index) {
        return (long) VALUES_HANDLE.getVolatile(values, index);
    }

    @Override
    void casValue(int index, Long value) {
        VALUES_HANDLE.compareAndSet(values, index, 0, value.longValue());
    }
}
