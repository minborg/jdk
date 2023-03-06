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
package java.util.concurrent.lazy;

import jdk.internal.util.lazy.StandardLazyReference;
import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * TO BE REMOVED. JUST USED FOR BENCHMARK COMPARISON.
 * <p>
 * An object reference in which the value is lazily and atomically computed.
 * <p>
 * It is guaranteed that just at most one supplier is invoked and that
 * that supplier (if any) is invoked just once per LazyReference instance provided
 * a value is sucessfully computed. More formally, at most one sucessfull invocation is
 * made of any provided set of suppliers.
 * <p>
 * This contrasts to {@link AtomicReference } where any number of updates can be done
 * and where there is no simple way to atomically compute
 * a value (guaranteed to only be computed once) if missing.
 * <p>
 * The implementation is optimized for the case where there are N invocations
 * trying to obtain the value and where N >> 1, for example where N is > 2<sup>20</sup>.
 * <p>
 * This class is thread-safe.
 * <p>
 * The JVM may apply certain optimizations as it knows the value is updated just once
 * at most as described by {@link Stable}.
 *
 * @param <V> The type of the value to be recorded
 */
public final class LazyReference3<V> implements Supplier<V> {

    private Supplier<? extends V> presetSupplier;
    @Stable
    private Object value;
    @Stable
    private byte present;

    static final VarHandle PRESENT_VH;

    static {
        try {
            PRESENT_VH = MethodHandles.lookup()
                    .findVarHandle(LazyReference3.class, "present", byte.class);
                    //.withInvokeExactBehavior(); // Improve performance?
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * A
     * @param presetSupplier b
     */
    public LazyReference3(Supplier<? extends V> presetSupplier) {
        this.presetSupplier = presetSupplier;
    }

    @SuppressWarnings("unchecked")
    @Override
    public V get() {
        return ((byte) PRESENT_VH.get(this)) == 0
                ? supplyIfEmpty0(presetSupplier)
                : (V) value;
    }

    /**
     * P
     * @return a
     */
    public boolean isPresent() {
        // Normal semantics
        return ((byte) PRESENT_VH.get(this)) != 0;

        // Acquire semantics
        //return ((byte) PRESENT_VH.getAcquire(this)) != 0;
    }


    @SuppressWarnings("unchecked")
    private V supplyIfEmpty0(Supplier<? extends V> supplier) {
        if (((byte) PRESENT_VH.get(this)) == 0) {
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
        return (V) value;
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
