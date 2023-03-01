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
import java.util.concurrent.lazy.LazyLong;
import java.util.function.LongSupplier;

// The only implementation of the LazyReference interface. This provides
// oportunities to create other specialized versions in the future.
public final class StandardLazyLong
        implements LazyLong {

    private LongSupplier presetSupplier;
    @Stable
    private long value;
    @Stable
    private boolean present;

    public StandardLazyLong(LongSupplier presetSupplier) {
        this.presetSupplier = presetSupplier;
    }

    @SuppressWarnings("unchecked")
    @Override
    public long getAsLong() {
        return present
                ? value
                : supplyIfEmpty0(presetSupplier);
    }

    @Override
    public boolean isPresent() {
        if (present) {
            return true;
        }
        synchronized (this) {
            return present;
        }
    }

    @Override
    public final long supplyIfEmpty(LongSupplier supplier) {
        Objects.requireNonNull(supplier);
        return supplyIfEmpty0(supplier);
    }

    private long supplyIfEmpty0(LongSupplier supplier) {
        if (!present) {
            synchronized (this) {
                if (!present) {
                    if (supplier == null) {
                        throw new IllegalStateException("No pre-set supplier specified.");
                    }
                    value = supplier.getAsLong();
                    forgetPresetSupplier();
                    present = true;
                }
            }
        }
        return value;
    }

    @Override
    public final String toString() {
        return isPresent()
                ? ("LazyLong[" + value + "]")
                : "LazyLong.empty";
    }

    void forgetPresetSupplier() {
        // Stops preventing the supplier from being collected once it has been
        // used (if initially set).
        this.presetSupplier = null;
    }

}
