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

import jdk.internal.vm.annotation.Stable;

import java.lang.constant.ClassDesc;
import java.lang.constant.Constable;
import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_long;
import static java.util.Objects.requireNonNull;

/**
 * An long in which the value can be lazily and atomically computed.
 */
public final class CondyLong
        /*    extends AbstractLazy<LongSupplier>*/
        implements /*Lazy,*/ LongSupplier, Constable {

    private final LongSupplier presetSupplier;
    private long value;

    /**
     * A
     * @param presetSupplier A
     */
    public CondyLong(LongSupplier presetSupplier) {
        this.presetSupplier = requireNonNull(presetSupplier);
    }

    @Override
    public long getAsLong() {
        return value;
    }

    private long eval() {
        return value = presetSupplier.getAsLong();
    }

    @Override
    public Optional<DynamicConstantDesc<Long>> describeConstable() {
        DirectMethodHandleDesc bsmDesc = ConstantDescs.ofConstantBootstrap(ClassDesc.of("java.util.concurrent.lazy.CondyLong"), "eval",
                CD_long);
        return Optional.of(
                DynamicConstantDesc.of(bsmDesc)
        );
    }

    /**
     * {@return a new empty CondyLong with a pre-set supplier}.
     *
     * @param presetSupplier to invoke when lazily constructing a value
     * @throws NullPointerException if the provided {@code presetSupplier} is {@code null}
     */
    public static CondyLong of(LongSupplier presetSupplier) {
        requireNonNull(presetSupplier);
        return new CondyLong(presetSupplier);
    }

}
