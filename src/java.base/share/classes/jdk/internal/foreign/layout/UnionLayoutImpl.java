/*
 *  Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.foreign.StructLayout;
import java.lang.foreign.UnionLayout;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class UnionLayoutImpl extends AbstractGroupLayout<UnionLayoutImpl> implements UnionLayout {

    private UnionLayoutImpl(List<MemoryLayout> elements, long byteSize, long byteAlignment, long minByteAlignment, Optional<String> name) {
        super(Kind.UNION, elements, byteSize, byteAlignment, minByteAlignment, name);
    }

    @Override
    UnionLayoutImpl dup(long byteAlignment, Optional<String> name) {
        return new UnionLayoutImpl(memberLayouts(), byteSize(), byteAlignment, minByteAlignment, name);
    }

    @Override
    public <R extends Record> OfCarrier<R> withCarrier(Class<R> carrierType) {
        Objects.requireNonNull(carrierType);
        return new OfCarrierImpl<>(kind, memberLayouts(), byteSize(), byteAlignment(), minByteAlignment, name(), carrierType, null, null);
    }

    @Override
    public <R> OfCarrier<R> withCarrier(Class<R> carrierType,
                                        Function<? super MemorySegment, ? extends R> unmarshaller,
                                        BiConsumer<? super MemorySegment, ? super R> marshaller) {
        Objects.requireNonNull(carrierType);
        Objects.requireNonNull(unmarshaller);
        Objects.requireNonNull(marshaller);
        return new OfCarrierImpl<>(kind, memberLayouts(), byteSize(), byteAlignment(), minByteAlignment, name(), carrierType, unmarshaller, marshaller);
    }

    @Override
    public UnionLayout withoutCarrier() {
        return this;
    }

    public static UnionLayout of(List<MemoryLayout> elements) {
        long size = 0;
        long align = 1;
        for (MemoryLayout elem : elements) {
            size = Math.max(size, elem.byteSize());
            align = Math.max(align, elem.byteAlignment());
        }
        return new UnionLayoutImpl(elements, size, align, align, Optional.empty());
    }

    public static final class OfCarrierImpl<T>
            extends AbstractGroupLayout.AbstractOfCarrier<T, UnionLayoutImpl.OfCarrierImpl<T>>
            implements UnionLayout.OfCarrier<T> {

        OfCarrierImpl(Kind kind, List<MemoryLayout> elements, long byteSize, long byteAlignment, long minByteAlignment, Optional<String> name, Class<T> carrier, Function<? super MemorySegment, ? extends T> unmarshaller, BiConsumer<? super MemorySegment, ? super T> marshaller) {
            super(kind, elements, byteSize, byteAlignment, minByteAlignment, name, carrier, unmarshaller, marshaller);
        }

        @Override
        public UnionLayout withoutCarrier() {
            return new UnionLayoutImpl(memberLayouts(), byteSize(), byteAlignment(), minByteAlignment, name());
        }

        @Override
        public <R> UnionLayout.OfCarrier<R> withCarrier(Class<R> carrierType, Function<? super MemorySegment, ? extends R> unmarshaller, BiConsumer<? super MemorySegment, ? super R> marshaller) {
            return dup(kind, memberLayouts(), byteSize(), byteAlignment(), minByteAlignment, name(), carrierType, unmarshaller, marshaller);
        }

        @Override
        public <R extends Record> UnionLayout.OfCarrier<R> withCarrier(Class<R> carrierType) {
            return withCarrier(carrierType, AbstractGroupLayout.AbstractOfCarrier.unmarshaller(kind, carrierType), AbstractGroupLayout.AbstractOfCarrier.marshaller(kind, carrierType));
        }

        @SuppressWarnings("unchecked")
        @Override
        <R, M extends AbstractOfCarrier<R, M>> M dup(Kind kind,
                                                     List<MemoryLayout> elements,
                                                     long byteSize,
                                                     long byteAlignment,
                                                     long minByteAlignment,
                                                     Optional<String> name,
                                                     Class<R> carrier,
                                                     Function<? super MemorySegment, ? extends R> unmarshaller,
                                                     BiConsumer<? super MemorySegment, ? super R> marshaller) {
            return (M) new StructLayoutImpl.OfCarrierImpl<>(kind, elements, byteSize, byteAlignment, minByteAlignment, name, carrier, unmarshaller, marshaller);
        }

    }

}
