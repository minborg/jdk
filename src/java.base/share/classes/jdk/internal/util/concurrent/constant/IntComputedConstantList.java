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

package jdk.internal.util.concurrent.constant;

import jdk.internal.ValueBased;
import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.function.IntFunction;

@ValueBased
public final class IntComputedConstantList
        extends AbstractComputedConstantList<Integer>
        implements List<Integer> {

    private static final VarHandle INT_ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(int[].class);

    @Stable
    private final int[] elements;

    private IntComputedConstantList(int size,
                                    IntFunction<? extends Integer> presetMapper) {
        super(size, presetMapper);
        this.elements = new int[size];
    }

    boolean isNotDefaultValue(Integer value) {
        return value != 0;
    }

    @Override
    Integer element(int index) {
        return elements[index];
    }

    Integer elementVolatile(int index) {
        return (Integer) INT_ARRAY_HANDLE.getVolatile(elements, index);
    }

    void casElement(int index, Integer value) {
        INT_ARRAY_HANDLE.compareAndSet(elements, index, 0, index);
    }

    public static List<Integer> create(int size, IntFunction<? extends Integer> presetMapper) {
        return new IntComputedConstantList(size, presetMapper);
    }

}
