/*
 *  Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */
package jdk.internal.foreign.layout;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.UnionLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A compound layout that aggregates multiple <em>member layouts</em>. There are two ways in which member layouts
 * can be combined: if member layouts are laid out one after the other, the resulting group layout is said to be a <em>struct</em>
 * (see {@link MemoryLayout#structLayout(MemoryLayout...)}); conversely, if all member layouts are laid out at the same starting offset,
 * the resulting group layout is said to be a <em>union</em> (see {@link MemoryLayout#unionLayout(MemoryLayout...)}).
 *
 * @implSpec
 * This class is immutable, thread-safe and <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 */
public abstract sealed class AbstractGroupLayout<L extends AbstractGroupLayout<L> & MemoryLayout>
        extends AbstractLayout<L>
        permits AbstractGroupLayout.AbstractOfCarrier, StructLayoutImpl, UnionLayoutImpl {

    final Kind kind;
    private final List<MemoryLayout> elements;
    final long minByteAlignment;

    AbstractGroupLayout(Kind kind, List<MemoryLayout> elements, long byteSize, long byteAlignment, long minByteAlignment, Optional<String> name) {
        super(byteSize, byteAlignment, name); // Subclassing creates toctou problems here
        this.kind = kind;
        this.elements = List.copyOf(elements);
        this.minByteAlignment = minByteAlignment;
    }

    /**
     * Returns the member layouts associated with this group.
     *
     * @apiNote the order in which member layouts are returned is the same order in which member layouts have
     * been passed to one of the group layout factory methods (see {@link MemoryLayout#structLayout(MemoryLayout...)},
     * {@link MemoryLayout#unionLayout(MemoryLayout...)}).
     *
     * @return the member layouts associated with this group.
     */
    public final List<MemoryLayout> memberLayouts() {
        return elements; // "elements" are already unmodifiable.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final String toString() {
        return decorateLayoutString(elements.stream()
                .map(Object::toString)
                .collect(Collectors.joining(kind.delimTag, "[", "]")));
    }

    @Override
    public L withByteAlignment(long byteAlignment) {
        if (byteAlignment < minByteAlignment) {
            throw new IllegalArgumentException("Invalid alignment constraint");
        }
        return super.withByteAlignment(byteAlignment);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean equals(Object other) {
        return this == other ||
                other instanceof AbstractGroupLayout<?> otherGroup &&
                        super.equals(other) &&
                        kind == otherGroup.kind &&
                        elements.equals(otherGroup.elements);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int hashCode() {
        return Objects.hash(super.hashCode(), kind, elements);
    }

    @Override
    public final boolean hasNaturalAlignment() {
        return byteAlignment() == minByteAlignment;
    }

    /**
     * The group kind.
     */
    enum Kind {
        /**
         * A 'struct' kind.
         */
        STRUCT(""),
        /**
         * A 'union' kind.
         */
        UNION("|");

        final String delimTag;

        Kind(String delimTag) {
            this.delimTag = delimTag;
        }
    }

    public abstract static sealed class AbstractOfCarrier<T, L extends AbstractOfCarrier<T, L> & MemoryLayout>
            extends AbstractGroupLayout<L>
            implements GroupLayout.OfCarrier<T>
            permits StructLayoutImpl.OfCarrierImpl, UnionLayoutImpl.OfCarrierImpl {

        private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

        private final Class<T> carrier;
        private final Function<? super MemorySegment, ? extends T> unmarshaller;
        private final BiConsumer<? super MemorySegment, ? super T> marshaller;
        private final MethodHandle getter;
        private final MethodHandle setter;

        protected AbstractOfCarrier(Kind kind, List<MemoryLayout> elements, long byteSize, long byteAlignment, long minByteAlignment, Optional<String> name,
                                    Class<T> carrier, Function<? super MemorySegment, ? extends T> unmarshaller, BiConsumer<? super MemorySegment, ? super T> marshaller) {
            super(kind, elements, byteSize, byteAlignment, minByteAlignment, name);
            this.carrier = carrier;
            this.unmarshaller = unmarshaller;
            this.marshaller = marshaller;
            try {
                MethodHandle getter = LOOKUP.findStatic(AbstractOfCarrier.class, "get",
                        MethodType.methodType(Object.class, Function.class, MemorySegment.class, long.class));
                getter = getter.bindTo(unmarshaller);
                getter = getter.asType(getter.type().changeReturnType(carrier));
                this.getter = getter;
                MethodHandle setter = LOOKUP.findStatic(AbstractOfCarrier.class, "set",
                        MethodType.methodType(void.class, BiConsumer.class, MemorySegment.class, long.class, Object.class));
                setter = setter.bindTo(marshaller);
                setter = setter.asType(setter.type().changeParameterType(2, carrier));
                this.setter = setter;
            } catch (ReflectiveOperationException re) {
                throw new InternalError(re);
            }
        }

        @Override
        public Class<T> carrier() {
            return carrier;
        }

        @SuppressWarnings("unchecked")
        public Function<MemorySegment, T> unmarshaller() {
            return (Function<MemorySegment, T>) unmarshaller;
        }

        @SuppressWarnings("unchecked")
        public BiConsumer<MemorySegment, T> marshaller() {
            return (BiConsumer<MemorySegment, T>) marshaller;
        }

        @Override
        public MethodHandle getter() {
            return getter;
        }

        @Override
        public MethodHandle setter() {
            return setter;
        }

        private static <T> T get(Function<? super MemorySegment, ? extends T> unmarshaller,
                                 MemorySegment segment,
                                 long offset) {
            return unmarshaller.apply(segment.asSlice(offset));
        }

        private static <T> void set(BiConsumer<? super MemorySegment, ? super T> marshaller,
                                    MemorySegment segment,
                                    long offset,
                                    T value) {
            marshaller.accept(segment.asSlice(offset), value);
        }

        private T get(MemorySegment segment, int offset) {
            return unmarshaller.apply(segment.asSlice(offset));
        }

        abstract <R, M extends AbstractOfCarrier<R, M>> M dup(Kind kind,
                                                              List<MemoryLayout> elements,
                                                              long byteSize,
                                                              long byteAlignment,
                                                              long minByteAlignment,
                                                              Optional<String> name,
                                                              Class<R> carrier,
                                                              Function<? super MemorySegment, ? extends R> unmarshaller,
                                                              BiConsumer<? super MemorySegment, ? super R> marshaller);

/*        @Override
        public <R extends Record> OfCarrier<R> withCarrier(Class<R> carrierType) {
            Objects.requireNonNull(carrierType);
            return withCarrier(carrierType, unmarshaller(kind, carrierType), marshaller(kind, carrierType));
        }*/

/*        @Override
        public <R> OfCarrier<R> withCarrier(Class<R> carrierType,
                                            Function<? super MemorySegment, ? extends R> unmarshaller,
                                            BiConsumer<? super MemorySegment, ? super R> marshaller) {
            Objects.requireNonNull(carrierType);
            Objects.requireNonNull(unmarshaller);
            Objects.requireNonNull(marshaller);
            return dup(kind, memberLayouts(), byteSize(), byteAlignment(), minByteAlignment, name(), carrierType, unmarshaller, marshaller);
        }*/

        @Override
        L dup(long byteAlignment, Optional<String> name) {
            return dup(kind, memberLayouts(), byteSize(), byteAlignment(), minByteAlignment, name(), carrier(), unmarshaller, marshaller);
        }

        // Todo: Implement mapper
        static <R> Function<MemorySegment, R> unmarshaller(Kind kind, Class<R> carrier) {
            return null;
        }

        // Todo: Implement mapper
        static <R> BiConsumer<MemorySegment, R> marshaller(Kind kind, Class<R> carrier) {
            return null;
        }

    }
}
