/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.lang.stable;

import jdk.internal.vm.annotation.ForceInline;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The implementation of {@linkplain ComputedConstant}.
 * <p>
 * @implNote This implementation can be used early in the boot sequence as it does not
 *           rely on reflection, MethodHandles, Streams etc.
 *
 * @param <T> the type of the constant
 */
public record ComputedConstantImpl<T>(StandardStableValue<T> delegate,
                                      FunctionHolder<Supplier<? extends T>> mapperHolder) implements ComputedConstant<T>, ComputedConstant.OfDeferred<T> {

    @ForceInline
    @Override public T get() { return delegate.orElseSet(null, mapperHolder); }
    @Override public boolean isSet() { return delegate.isSet(); }

    @Override
    public boolean trySet(T value) {
        return delegate.trySet(value);
    }

    @ForceInline
    @Override
    public T orElseSet(Supplier<? extends T> supplier) {
        Objects.requireNonNull(supplier);
        return delegate.orElseSet(supplier);
    }

    @Override
    public <I> T orElseSet(I input, Function<? super I, ? extends T> mapper) {
        return delegate.orElseSet(input, new FunctionHolder<>(mapper, 1));
    }

    // Object methods
    @Override public int     hashCode() { return System.identityHashCode(this); }
    @Override public boolean equals(Object obj) { return obj == this; }
    @Override public String  toString() {
                   final Object t = delegate.contentsAcquire();
                   return t == this ? "(this ComputedConstant)" : StandardStableValue.render(t);
              }

    public static <T> ComputedConstantImpl<T> ofPreset(T constant) {
        return new ComputedConstantImpl<>(StandardStableValue.ofPreset(constant), null);
    }

    public static <T> ComputedConstantImpl<T> of(Supplier<? extends T> original) {
        return new ComputedConstantImpl<>(StandardStableValue.of(), new FunctionHolder<>(original, 1));
    }

    public static <T> ComputedConstantImpl<T> ofDeferred() {
        return new ComputedConstantImpl<>(StandardStableValue.of(), throwingHolder());
    }

    // Support members

    @SuppressWarnings("unchecked")
    public static <T> FunctionHolder<Supplier<? extends T>> throwingHolder() {
        return (FunctionHolder<Supplier<? extends T>>) (FunctionHolder<?>) THROWING_HOLDER;
    }

    private static final FunctionHolder<Supplier<?>> THROWING_HOLDER = new FunctionHolder<>(new Supplier<>() {
        @Override  public Supplier<?> get() { return new ThrowingSupplier<>(); }}, 1);

    private static class ThrowingSupplier<T> implements Supplier<T> {
        @Override  public T get() { throw new NoSuchElementException(); }
    }

}
