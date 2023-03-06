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
import java.util.concurrent.lazy.LazyReference;
import java.util.function.Supplier;

// The only implementation of the LazyReference interface. This provides
// oportunities to create other specialized versions in the future.
public final class StandardLazyReference<T>
        implements LazyReference<T> {

    private Supplier<? extends T> presetSupplier;
    @Stable
    private Object value;
    @Stable
    private byte present;

    static final VarHandle PRESENT_VH;

    static {
        try {
            PRESENT_VH = MethodHandles.lookup()
                    .findVarHandle(StandardLazyReference.class, "present", byte.class)
                    .withInvokeExactBehavior(); // Improve performance?
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public StandardLazyReference(Supplier<? extends T> presetSupplier) {
        this.presetSupplier = presetSupplier;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T get() {
        return isPresent()
                ? (T) value
                : supplyIfEmpty0(presetSupplier);
    }

    @Override
    public boolean isPresent() {
        // Normal semantics
        return ((byte) PRESENT_VH.get(this)) != 0;

        // Acquire semantics
        //return ((byte) PRESENT_VH.getAcquire(this)) != 0;
    }

    @Override
    public final T supplyIfEmpty(Supplier<? extends T> supplier) {
        Objects.requireNonNull(supplier);
        return supplyIfEmpty0(supplier);
    }

    @SuppressWarnings("unchecked")
    private T supplyIfEmpty0(Supplier<? extends T> supplier) {
        if (!isPresent()) {
            synchronized (this) {
                if (!isPresent()) {
                    if (supplier == null) {
                        throw new IllegalStateException("No pre-set supplier specified.");
                    }
                    value = supplier.get();
                    // It is possible to accept null results now that there is
                    // a separate "present" variable
                    if (value == null) {
                        throw new NullPointerException("The supplier returned null: " + supplier);
                    }
                    forgetPresetSupplier();
                    // Prevent reordering (for normal semantics also?). Changes only go in one direction.
                    // https://developer.arm.com/documentation/102336/0100/Load-Acquire-and-Store-Release-instructions
                    PRESENT_VH.setRelease(this, (byte) 1);
                }
            }
        }
        return (T) value;
    }

    @Override
    public final String toString() {
        return isPresent()
                ? ("LazyReference[" + value + "]")
                : "LazyReference.empty";
    }

    void forgetPresetSupplier() {
        // Stops preventing the supplier from being collected once it has been
        // used (if initially set).
        this.presetSupplier = null;
    }
}
