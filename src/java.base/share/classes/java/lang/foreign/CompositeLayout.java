/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.foreign;

import jdk.internal.foreign.layout.AbstractGroupLayout;
import jdk.internal.foreign.layout.SequenceLayoutImpl;
import jdk.internal.foreign.layout.StructLayoutImpl;
import jdk.internal.foreign.layout.UnionLayoutImpl;

import java.lang.invoke.MethodHandle;
import java.util.function.UnaryOperator;

/**
 * A composite layout that is an aggregation of one or several, heterogeneous
 * <em>constituent layouts</em>. There are three ways in which the constituent layouts can
 * be combined:
 * if constituent layouts are laid out one after the other, the resulting composite layout
 * is a {@linkplain StructLayout struct layout};
 * if the constituent layouts are laid out at the same starting offset, the resulting
 * composite layout is a {@linkplain UnionLayout union layout}; and,
 * if the constituent layouts are a sequence of a single layout, the resulting composite
 * layout is a {@linkplain SequenceLayout sequence layout}.
 *
 * @implSpec
 * This class is immutable, thread-safe and
 * <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 *
 * @sealedGraph
 * @since 25
 */
public sealed interface CompositeLayout
        extends MemoryLayout
        permits CompositeLayout.OfClass, GroupLayout, SequenceLayout, UnionLayout {

    /**
     * {@return a bound composite layout with the provided carrier type}
     * <p>
     * For group layouts, the carrier type must
     *
     * @param carrier to use as a carrier
     * @param getter  unmarshaller used to read carriers from a memory segment
     * @param setter  marshaller used to write carriers to a memory segment
     * @param <R>     carrier type
     * @throws IllegalArgumentException if this is a group layout and the provided
     *         {@code carrier} is not a {@linkplain Record}
     * @throws IllegalArgumentException if this is a sequence layout and the provided
     *         {@code carrier} is not an array of {@linkplain Record}
     */
    // Todo: Automatically derive the getter and setter from the carrier and this layout.
    <R> OfClass<R> bind(Class<R> carrier, MethodHandle getter, MethodHandle setter);

    /**
     * {@return a new composite layout with the same characteristics as this layout but
     *          where the constituent layouts are transformed by the provided
     *          {@code mapper}}
     * @param mapper to apply to all the constituent layouts
     */
    CompositeLayout mapConstituentLayouts(UnaryOperator<MemoryLayout> mapper);

    /**
     * A composite layout that is bound to a specific carrier type.
     *
     * @param <T> the carrier type
     *
     * @since 25
     */
    sealed interface OfClass<T>
            extends CompositeLayout
            permits AbstractGroupLayout.AbstractOfClass, SequenceLayoutImpl.OfClassImpl, StructLayoutImpl.OfClassImpl, UnionLayoutImpl.OfClassImpl {

        /**
         * {@return the carrier type}
         */
        Class<T> carrier();

        /**
         * {@return a method handle which can be used to read values described by this
         * carrier layout, from a given memory segment at a given offset}
         * <p>
         * The returned method handle's return {@linkplain MethodHandle#type() type} is
         * the {@linkplain ValueLayout#carrier() carrier type} of this carrier layout, and
         * the list of coordinate types is {@code (MemorySegment, long)}, where the
         * memory segment coordinate corresponds to the memory segment to be accessed, and
         * the {@code long} coordinate corresponds to the byte offset into the accessed
         * memory segment at which the access occurs.
         * <p>
         * The returned method handle checks that accesses are aligned according to
         * this carrier layout's {@linkplain MemoryLayout#byteAlignment() alignment constraint}.
         * <p>
         * The returned {@code getter} method handle can be adapted to a more convenient
         * one suitable for casting (i.e. {@code (T)getter.invokeExact(segment, offset)})
         * as shown in this example:
         * {@snippet lang = java:
         *     getter = getter.asType(MethodType.methodType(Object.class))
         *}
         */
        MethodHandle getter();

        /**
         * {@return a method handle which can be used to write values described by this
         * carrier layout, to a given memory segment at a given offset}
         * <p>
         * The returned method handle's return {@linkplain MethodHandle#type() type} is
         * {@code void}, and the list of coordinate types is
         * {@code (MemorySegment, long, T)}, where the memory segment coordinate
         * corresponds to the memory segment to be accessed, the {@code long} coordinate
         * corresponds to the byte offset into the accessed memory segment at which the
         * access occurs, and the {@code T} coordinate corresponds to the value to write.
         * <p>
         * The returned method handle checks that accesses are aligned according to
         * this carrier layout's {@linkplain MemoryLayout#byteAlignment() alignment constraint}.
         */
        MethodHandle setter();

        /**
         * {@inheritDoc}
         */
        @Override
        <R> OfClass<R> bind(Class<R> carrier, MethodHandle getter, MethodHandle setter);

        /**
         * {@inheritDoc}
         */
        @Override
        CompositeLayout mapConstituentLayouts(UnaryOperator<MemoryLayout> mapper);

    }

}
