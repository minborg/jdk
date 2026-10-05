/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @summary Lazy updater builder configuration, shapes, and segment locations
 * @enablePreview
 * @run junit UpdaterBuilderTest
 * @run junit/othervm -Xint UpdaterBuilderTest
 */

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Modifier;
import java.lang.util.Lazy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import org.junit.jupiter.api.Test;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.MemoryLayout.PathElement.dereferenceElement;
import static java.lang.foreign.MemoryLayout.PathElement.sequenceElement;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UpdaterBuilderTest {
    private static class PrivateOwner {
        private int value;
    }

    public static class PublicOwner {
        public static int first;
        public static int second;
    }

    public static class Base { }

    public static class Sub extends Base {
        public int onlySub;
    }

    public interface PublicIntSupplier {
        int get();
    }

    private interface PrivateIntField {
        int get(PrivateOwner owner);
    }

    private interface BaseIntField {
        int get(Base owner);
    }

    private interface BaseIntFieldUseSite
            extends Lazy.IntFieldAccessor<Base> { }

    @SuppressWarnings("rawtypes")
    private interface RawIntFieldUseSite
            extends Lazy.IntFieldAccessor { }

    private static class GenericOwner {
        private List<Integer> values;
    }

    private interface Marker<T> {
        default void marker() { }
    }

    @SuppressWarnings("rawtypes")
    private interface TypedNameWithRawMarker
            extends Lazy.ReferenceFieldAccessor<GenericOwner, List<String>>,
                    Marker { }

    private interface IntElement {
        int get(int[] array, int index);
    }

    private interface OtherIntComputer {
        int apply(int[] array, int index);
    }

    private interface IntUseSite {
        int get(OtherIntComputer computer, int[] array, int index);
    }

    private interface Names extends Lazy.ReferenceArrayAccessor<String> { }

    private interface WrongIntComputer {
        int apply(int[] array, long index);
    }

    private interface WrongUseSite {
        int get(WrongIntComputer computer, int[] array, long index);
    }

    private interface SegmentInt {
        int get(MemorySegment segment, long baseOffset, long index);
    }

    private interface OtherSegmentInt {
        int compute(MemorySegment segment, long baseOffset, long index);
    }

    private interface SegmentIntUseSite {
        int get(OtherSegmentInt computer, MemorySegment segment,
                long baseOffset, long index);
    }

    private interface SegmentInt2D {
        int get(MemorySegment segment, long baseOffset,
                long row, long column);
    }

    private interface SegmentScalarInt {
        int get(MemorySegment segment, long baseOffset);
    }

    private interface SegmentScalarFloat {
        float get(MemorySegment segment, long baseOffset);
    }

    private interface SegmentAddress {
        MemorySegment get(MemorySegment segment, long baseOffset);
    }

    private static final MemoryLayout STRUCT = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("header"),
            MemoryLayout.sequenceLayout(3, ValueLayout.JAVA_INT)
                    .withName("values"));

    private static final MemoryLayout GRID = MemoryLayout.structLayout(
            MemoryLayout.sequenceLayout(2,
                    MemoryLayout.sequenceLayout(3, ValueLayout.JAVA_INT))
                    .withName("cells"));

    @Test
    public void publicBuilderInterfaceHasInternalImplementation() {
        Object builder = Lazy.ofUpdater(PublicIntSupplier.class);
        assertTrue(Lazy.UpdaterBuilder.class.isInterface());
        assertTrue(builder instanceof Lazy.UpdaterBuilder<?>);
        assertEquals("jdk.internal.invoke", builder.getClass().getPackageName());
        assertFalse(Modifier.isPublic(builder.getClass().getModifiers()));
    }

    @Test
    public void defaultModeAndPublicLookupWorkForPublicField() {
        PublicOwner.first = 0;
        AtomicInteger computations = new AtomicInteger();
        PublicIntSupplier updater = Lazy.ofUpdater(PublicIntSupplier.class)
                .withLocation(PublicOwner.class, "first")
                .build(() -> computations.incrementAndGet() + 20);

        assertEquals(21, updater.get());
        assertEquals(21, updater.get());
        assertEquals(21, PublicOwner.first);
        assertEquals(1, computations.get());
    }

    @Test
    public void lookupCanGrantPrivateFieldAccess() {
        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofUpdater(PrivateIntField.class)
                        .withLocation(PrivateOwner.class, "value")
                        .build(owner -> 42));

        PrivateIntField updater = Lazy.ofUpdater(PrivateIntField.class)
                .withLookup(MethodHandles.lookup())
                .withLocation(PrivateOwner.class, "value")
                .build(owner -> 42);
        PrivateOwner owner = new PrivateOwner();
        assertEquals(42, updater.get(owner));
        assertEquals(42, owner.value);
    }

    @Test
    public void concreteBaseReceiverCannotTargetSubclassField() {
        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofUpdater(BaseIntField.class)
                        .withLookup(MethodHandles.lookup())
                        .withLocation(Sub.class, "onlySub")
                        .build(owner -> 42));

        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofUpdater(ToIntFunction.class)
                        .withLookup(MethodHandles.lookup())
                        .withLocation(Sub.class, "onlySub")
                        .buildUseSite(BaseIntFieldUseSite.class));
    }

    @Test
    public void explicitOwnerSupportsRawInheritedFieldAccessor() {
        RawIntFieldUseSite updater =
                Lazy.ofUpdater(ToIntFunction.class)
                        .withLookup(MethodHandles.lookup())
                        .withLocation(PrivateOwner.class, "value")
                        .buildUseSite(RawIntFieldUseSite.class);
        PrivateOwner owner = new PrivateOwner();
        assertEquals(37, updater.getOrCompute(ignored -> 37, owner));
        assertEquals(37, owner.value);
    }

    @Test
    public void unrelatedRawMarkerDoesNotSuppressFieldGenericValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofUpdater(Function.class)
                        .withLookup(MethodHandles.lookup())
                        .withLocation(GenericOwner.class, "values")
                        .buildUseSite(TypedNameWithRawMarker.class));
    }

    @Test
    public void withersAreImmutableAndOnlyOneLocationMayBeSelected() {
        PublicOwner.first = 0;
        PublicOwner.second = 0;
        var base = Lazy.ofUpdater(PublicIntSupplier.class);
        var first = base.withLocation(PublicOwner.class, "first");
        var second = base.withMode(Lazy.UpdaterMode.PLAIN)
                .withLocation(PublicOwner.class, "second");

        assertThrows(IllegalArgumentException.class, () -> base.build(() -> 3));
        assertThrows(IllegalStateException.class,
                () -> first.withLocation(PublicOwner.class, "second"));
        assertEquals(11, first.build(() -> 11).get());
        assertEquals(22, second.build(() -> 22).get());
        assertEquals(11, PublicOwner.first);
        assertEquals(22, PublicOwner.second);
    }

    @Test
    public void directArraySupportsDeclarationAndUseSiteComputations() {
        int[] array = new int[3];
        IntElement declared = Lazy.ofUpdater(IntElement.class)
                .build((values, index) -> index + 10);
        IntUseSite useSite = Lazy.ofUpdater(IntElement.class)
                .buildUseSite(IntUseSite.class);

        assertEquals(11, declared.get(array, 1));
        assertEquals(11, useSite.get((values, index) -> 99, array, 1));
        assertEquals(24, useSite.get((values, index) -> index + 22, array, 2));
        assertEquals(24, array[2]);
        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofUpdater(IntElement.class)
                        .buildUseSite(WrongUseSite.class));
        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofUpdater(IntElement.class)
                        .withLookup(MethodHandles.lookup())
                        .build((values, index) -> 1));
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void concreteUseSiteResolvesRawReferenceArrayComputer() {
        Names names = Lazy.ofUpdater(Lazy.ReferenceArrayAccessor.Computer.class)
                .buildUseSite(Names.class);
        String[] values = new String[1];
        assertEquals("name", names.getOrCompute(
                (array, index) -> "name", values, 0));
        assertEquals("name", values[0]);

        Lazy.ReferenceArrayAccessor.Computer rawComputer =
                (array, index) -> "not selected";
        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofUpdater(Lazy.ReferenceArrayAccessor.Computer.class)
                        .build(rawComputer));
    }

    @Test
    public void structuredSegmentSupportsDeclarationAndUseSiteComputations() {
        SegmentInt declared = Lazy.ofUpdater(SegmentInt.class)
                .withLocation(STRUCT, groupElement("values"), sequenceElement())
                .build((segment, baseOffset, index) ->
                        (int) (baseOffset + index + 40));
        SegmentIntUseSite useSite = Lazy.ofUpdater(SegmentInt.class)
                .withLocation(STRUCT, groupElement("values"), sequenceElement())
                .buildUseSite(SegmentIntUseSite.class);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(2 * STRUCT.byteSize(),
                    STRUCT.byteAlignment());
            assertEquals(41, declared.get(segment, 0L, 1L));
            assertEquals(41, declared.get(segment.asSlice(0), 0L, 1L));
            assertEquals(STRUCT.byteSize() + 41,
                    declared.get(segment, STRUCT.byteSize(), 1L));
            assertEquals(41, useSite.get((s, base, index) -> 99,
                    segment, 0L, 1L));
            assertEquals(73, useSite.get((s, base, index) -> 73,
                    segment, 0L, 2L));
            assertEquals(41, (int) STRUCT.varHandle(groupElement("values"),
                    sequenceElement()).get(segment, 0L, 1L));
            assertEquals(73, (int) STRUCT.varHandle(groupElement("values"),
                    sequenceElement()).get(segment, 0L, 2L));
            assertEquals(STRUCT.byteSize() + 41,
                    (int) STRUCT.varHandle(groupElement("values"),
                            sequenceElement()).get(segment,
                            STRUCT.byteSize(), 1L));
        }
    }

    @Test
    public void segmentPathPreservesTwoOpenSequenceIndices() {
        SegmentInt2D updater = Lazy.ofUpdater(SegmentInt2D.class)
                .withLocation(GRID, groupElement("cells"),
                        sequenceElement(), sequenceElement())
                .build((segment, baseOffset, row, column) ->
                        (int) (100 + 10 * row + column));

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(GRID);
            assertEquals(101, updater.get(segment, 0L, 0L, 1L));
            assertEquals(110, updater.get(segment, 0L, 1L, 0L));
            assertEquals(112, updater.get(segment, 0L, 1L, 2L));
            assertEquals(101, updater.get(segment, 0L, 0L, 1L));
            var handle = GRID.varHandle(groupElement("cells"),
                    sequenceElement(), sequenceElement());
            assertEquals(101, (int) handle.get(segment, 0L, 0L, 1L));
            assertEquals(110, (int) handle.get(segment, 0L, 1L, 0L));
            assertEquals(112, (int) handle.get(segment, 0L, 1L, 2L));
            assertEquals(0, (int) handle.get(segment, 0L, 0L, 0L));
        }
    }

    @Test
    public void segmentRejectsUnsupportedModeAndDefaultResult() {
        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofUpdater(SegmentInt.class)
                        .withMode(Lazy.UpdaterMode.SERIALIZED)
                        .withLocation(STRUCT, groupElement("values"),
                                sequenceElement())
                        .build((segment, baseOffset, index) -> 1));
        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofUpdater(SegmentInt.class)
                        .withLookup(MethodHandles.lookup())
                        .withLocation(STRUCT, groupElement("values"),
                                sequenceElement())
                        .build((segment, baseOffset, index) -> 1));

        SegmentInt zero = Lazy.ofUpdater(SegmentInt.class)
                .withLocation(STRUCT, groupElement("values"), sequenceElement())
                .build((segment, baseOffset, index) -> 0);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(STRUCT);
            assertThrows(IllegalArgumentException.class,
                    () -> zero.get(segment, 0L, 0L));
            assertEquals(0, (int) STRUCT.varHandle(groupElement("values"),
                    sequenceElement()).get(segment, 0L, 0L));
        }
    }

    @Test
    public void segmentPlainModeAndNonDefaultZeroBits() {
        SegmentScalarInt unaligned = Lazy.ofUpdater(SegmentScalarInt.class)
                .withMode(Lazy.UpdaterMode.PLAIN)
                .withLocation(ValueLayout.JAVA_INT_UNALIGNED)
                .build((segment, baseOffset) -> 19);
        SegmentScalarFloat negativeZero = Lazy.ofUpdater(SegmentScalarFloat.class)
                .withMode(Lazy.UpdaterMode.PLAIN)
                .withLocation(ValueLayout.JAVA_FLOAT)
                .build((segment, baseOffset) -> -0.0f);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ints = arena.allocate(ValueLayout.JAVA_INT_UNALIGNED);
            MemorySegment floats = arena.allocate(ValueLayout.JAVA_FLOAT);
            assertEquals(19, unaligned.get(ints, 0L));
            assertEquals(19, ints.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            assertEquals(Float.floatToRawIntBits(-0.0f),
                    Float.floatToRawIntBits(negativeZero.get(floats, 0L)));
            assertEquals(Float.floatToRawIntBits(-0.0f),
                    Float.floatToRawIntBits(floats.get(ValueLayout.JAVA_FLOAT, 0L)));
        }
    }

    @Test
    public void segmentRejectsUnalignedAtomicAndDereferenceLocations() {
        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofUpdater(SegmentScalarInt.class)
                        .withLocation(ValueLayout.JAVA_INT_UNALIGNED)
                        .build((segment, baseOffset) -> 9));

        MemoryLayout pointer = MemoryLayout.structLayout(
                ValueLayout.ADDRESS.withTargetLayout(ValueLayout.JAVA_INT)
                        .withName("pointer"));
        assertThrows(IllegalArgumentException.class,
                () -> Lazy.ofUpdater(SegmentScalarInt.class)
                        .withLocation(pointer, groupElement("pointer"),
                                dereferenceElement())
                        .build((segment, baseOffset) -> 9));
    }

    @Test
    public void segmentAddressZeroIsReserved() {
        SegmentAddress zeroAddress = Lazy.ofUpdater(SegmentAddress.class)
                .withMode(Lazy.UpdaterMode.PLAIN)
                .withLocation(ValueLayout.ADDRESS)
                .build((segment, baseOffset) -> MemorySegment.NULL);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment address = arena.allocate(ValueLayout.ADDRESS);
            assertThrows(IllegalArgumentException.class,
                    () -> zeroAddress.get(address, 0L));
        }
    }

    @Test
    public void publishedAddressReturnsMaterializedTargetSegment() {
        MemoryLayout target = MemoryLayout.sequenceLayout(4,
                ValueLayout.JAVA_BYTE);
        var addressLayout = ValueLayout.ADDRESS.withTargetLayout(target);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment slot = arena.allocate(addressLayout);
            MemorySegment candidate = arena.allocate(16);
            AtomicInteger computations = new AtomicInteger();
            SegmentAddress updater = Lazy.ofUpdater(SegmentAddress.class)
                    .withLocation(addressLayout)
                    .build((segment, baseOffset) -> {
                        computations.incrementAndGet();
                        return candidate;
                    });

            MemorySegment first = updater.get(slot, 0L);
            MemorySegment later = updater.get(slot, 0L);
            assertEquals(candidate.address(), first.address());
            assertEquals(candidate, first);
            assertEquals(first, later);
            assertEquals(target.byteSize(), first.byteSize());
            assertEquals(target.byteSize(), later.byteSize());
            assertNotSame(candidate.scope(), first.scope());
            assertEquals(1, computations.get());
        }
    }
}
