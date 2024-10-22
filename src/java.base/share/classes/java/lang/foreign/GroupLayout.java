/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A compound layout that is an aggregation of multiple, heterogeneous
 * <em>member layouts</em>. There are two ways in which member layouts can be combined:
 * if member layouts are laid out one after the other, the resulting group layout is a
 * {@linkplain StructLayout struct layout}; conversely, if all member layouts are laid
 * out at the same starting offset, the resulting group layout is a
 * {@linkplain UnionLayout union layout}.
 *
 * @implSpec
 * This class is immutable, thread-safe and
 * <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 *
 * @sealedGraph
 * @since 22
 */
public sealed interface GroupLayout extends MemoryLayout permits GroupLayout.OfCarrier, StructLayout, UnionLayout {

    /**
     * {@return the member layouts of this group layout}
     *
     * @apiNote the order in which member layouts are returned is the same order in which
     *          member layouts have been passed to one of the group layout factory methods
     *          (see {@link MemoryLayout#structLayout(MemoryLayout...)} and
     *          {@link MemoryLayout#unionLayout(MemoryLayout...)}).
     */
    List<MemoryLayout> memberLayouts();

    /**
     * {@inheritDoc}
     */
    @Override
    GroupLayout withName(String name);

    /**
     * {@inheritDoc}
     */
    @Override
    GroupLayout withoutName();

    /**
     * {@inheritDoc}
     * @throws IllegalArgumentException {@inheritDoc}
     * @throws IllegalArgumentException if {@code byteAlignment} is less than {@code M},
     *         where {@code M} is the maximum alignment constraint in any of the
     *         member layouts associated with this group layout
     */
    @Override
    GroupLayout withByteAlignment(long byteAlignment);

    /**
     * {@return a new memory layout with the same characteristics as this layout but
     *          with the provided carrier type}
     *
     * @param carrierType to use
     * @param <R> the record type of the carrier
     */
    <R extends Record> GroupLayout withCarrier(Class<R> carrierType);

    /**
     * {@return a new memory layout with the same characteristics as this layout but
     *          with the provided carrier type, unmarshaller and marshaller}
     *
     * @param carrierType  to use
     * @param marshaller   used to extract carriers from a MemorySegment
     * @param unmarshaller used to write carriers to a MemorySegment
     * @param <R>          the type of the carrier
     */
    <R> GroupLayout withCarrier(Class<R> carrierType,
                                Function<? super MemorySegment, ? extends R> unmarshaller,
                                BiConsumer<? super MemorySegment, ? super R> marshaller);

    /**
     * {@return a new memory layout with the same characteristics as this layout but
     *          with no carrier type}
     */
    GroupLayout withoutCarrier();

    /**
     * A group layout whose carrier is {@code T}.
     *
     * @param <T> record carrier type
     *
     * @sealedGraph
     * @since 25
     */
    sealed interface OfCarrier<T>
            extends GroupLayout
            permits StructLayout.OfCarrier, UnionLayout.OfCarrier, AbstractGroupLayout.AbstractOfCarrier {

        /**
         * {@inheritDoc}
         */
        @Override
        GroupLayout.OfCarrier<T> withName(String name);

        /**
         * {@inheritDoc}
         */
        @Override
        GroupLayout.OfCarrier<T> withoutName();

        /**
         * {@inheritDoc}
         */
        @Override
        GroupLayout.OfCarrier<T> withByteAlignment(long byteAlignment);

        /**
         * {@inheritDoc}
         */
        @Override
        <R extends Record> GroupLayout.OfCarrier<R> withCarrier(Class<R> carrierType);

        /**
         * {@inheritDoc}
         */
        @Override
        <R> GroupLayout.OfCarrier<R> withCarrier(Class<R> carrierType,
                                                 Function<? super MemorySegment, ? extends R> unmarshaller,
                                                 BiConsumer<? super MemorySegment, ? super R> marshaller);

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

    }

}
