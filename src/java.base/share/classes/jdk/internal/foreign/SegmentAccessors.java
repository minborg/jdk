/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.foreign;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

import jdk.internal.foreign.abi.SharedUtils;
import jdk.internal.misc.ScopedMemoryAccess;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;

/**
 * Shared support for plain memory segment access operations.
 */
public final class SegmentAccessors {

    private static final ScopedMemoryAccess SCOPED_MEMORY_ACCESS = ScopedMemoryAccess.getScopedMemoryAccess();

    private SegmentAccessors() {}

    @ForceInline
    public static byte getByte(Object segment, MemoryLayout enclosing, long base, long offset, boolean bigEndian) {
        AbstractMemorySegmentImpl bb = checkSegment(segment, enclosing, base, true);
        return SCOPED_MEMORY_ACCESS.getByte(bb.sessionImpl(), bb.unsafeGetBase(), offset(bb, base, offset));
    }

    @ForceInline
    public static void putByte(Object segment, MemoryLayout enclosing, long base, long offset, byte value, boolean bigEndian) {
        AbstractMemorySegmentImpl bb = checkSegment(segment, enclosing, base, false);
        SCOPED_MEMORY_ACCESS.putByte(bb.sessionImpl(), bb.unsafeGetBase(), offset(bb, base, offset), value);
    }

    @ForceInline
    public static boolean getBoolean(Object segment, MemoryLayout enclosing, long base, long offset, boolean bigEndian) {
        return Utils.byteToBoolean(getByte(segment, enclosing, base, offset, bigEndian));
    }

    @ForceInline
    public static void putBoolean(Object segment, MemoryLayout enclosing, long base, long offset, boolean value, boolean bigEndian) {
        putByte(segment, enclosing, base, offset, value ? (byte) 1 : (byte) 0, bigEndian);
    }

    @ForceInline
    public static char getChar(Object segment, MemoryLayout enclosing, long base, long offset, boolean bigEndian) {
        AbstractMemorySegmentImpl bb = checkSegment(segment, enclosing, base, true);
        return SCOPED_MEMORY_ACCESS.getCharUnaligned(bb.sessionImpl(), bb.unsafeGetBase(), offset(bb, base, offset), bigEndian);
    }

    @ForceInline
    public static void putChar(Object segment, MemoryLayout enclosing, long base, long offset, char value, boolean bigEndian) {
        AbstractMemorySegmentImpl bb = checkSegment(segment, enclosing, base, false);
        SCOPED_MEMORY_ACCESS.putCharUnaligned(bb.sessionImpl(), bb.unsafeGetBase(), offset(bb, base, offset), value, bigEndian);
    }

    @ForceInline
    public static short getShort(Object segment, MemoryLayout enclosing, long base, long offset, boolean bigEndian) {
        AbstractMemorySegmentImpl bb = checkSegment(segment, enclosing, base, true);
        return SCOPED_MEMORY_ACCESS.getShortUnaligned(bb.sessionImpl(), bb.unsafeGetBase(), offset(bb, base, offset), bigEndian);
    }

    @ForceInline
    public static void putShort(Object segment, MemoryLayout enclosing, long base, long offset, short value, boolean bigEndian) {
        AbstractMemorySegmentImpl bb = checkSegment(segment, enclosing, base, false);
        SCOPED_MEMORY_ACCESS.putShortUnaligned(bb.sessionImpl(), bb.unsafeGetBase(), offset(bb, base, offset), value, bigEndian);
    }

    @ForceInline
    public static int getInt(Object segment, MemoryLayout enclosing, long base, long offset, boolean bigEndian) {
        AbstractMemorySegmentImpl bb = checkSegment(segment, enclosing, base, true);
        return SCOPED_MEMORY_ACCESS.getIntUnaligned(bb.sessionImpl(), bb.unsafeGetBase(), offset(bb, base, offset), bigEndian);
    }

    @ForceInline
    public static void putInt(Object segment, MemoryLayout enclosing, long base, long offset, int value, boolean bigEndian) {
        AbstractMemorySegmentImpl bb = checkSegment(segment, enclosing, base, false);
        SCOPED_MEMORY_ACCESS.putIntUnaligned(bb.sessionImpl(), bb.unsafeGetBase(), offset(bb, base, offset), value, bigEndian);
    }

    @ForceInline
    public static float getFloat(Object segment, MemoryLayout enclosing, long base, long offset, boolean bigEndian) {
        return Float.intBitsToFloat(getInt(segment, enclosing, base, offset, bigEndian));
    }

    @ForceInline
    public static void putFloat(Object segment, MemoryLayout enclosing, long base, long offset, float value, boolean bigEndian) {
        putInt(segment, enclosing, base, offset, Float.floatToRawIntBits(value), bigEndian);
    }

    @ForceInline
    public static long getLong(Object segment, MemoryLayout enclosing, long base, long offset, boolean bigEndian) {
        AbstractMemorySegmentImpl bb = checkSegment(segment, enclosing, base, true);
        return SCOPED_MEMORY_ACCESS.getLongUnaligned(bb.sessionImpl(), bb.unsafeGetBase(), offset(bb, base, offset), bigEndian);
    }

    @ForceInline
    public static void putLong(Object segment, MemoryLayout enclosing, long base, long offset, long value, boolean bigEndian) {
        AbstractMemorySegmentImpl bb = checkSegment(segment, enclosing, base, false);
        SCOPED_MEMORY_ACCESS.putLongUnaligned(bb.sessionImpl(), bb.unsafeGetBase(), offset(bb, base, offset), value, bigEndian);
    }

    @ForceInline
    public static double getDouble(Object segment, MemoryLayout enclosing, long base, long offset, boolean bigEndian) {
        return Double.longBitsToDouble(getLong(segment, enclosing, base, offset, bigEndian));
    }

    @ForceInline
    public static void putDouble(Object segment, MemoryLayout enclosing, long base, long offset, double value, boolean bigEndian) {
        putLong(segment, enclosing, base, offset, Double.doubleToRawLongBits(value), bigEndian);
    }

    @ForceInline
    public static MemorySegment getAddress(Object segment, AddressLayout enclosing, long base, long offset) {
        boolean bigEndian = enclosing.order() == java.nio.ByteOrder.BIG_ENDIAN;
        return Unsafe.ADDRESS_SIZE == Long.BYTES
                ? Utils.longToAddress(getLong(segment, enclosing, base, offset, bigEndian), enclosing)
                : Utils.longToAddress(getInt(segment, enclosing, base, offset, bigEndian), enclosing);
    }

    @ForceInline
    public static void putAddress(Object segment, AddressLayout enclosing, long base, long offset, MemorySegment value) {
        Objects.requireNonNull(value);
        boolean bigEndian = enclosing.order() == java.nio.ByteOrder.BIG_ENDIAN;
        if (Unsafe.ADDRESS_SIZE == Long.BYTES) {
            putLong(segment, enclosing, base, offset, SharedUtils.unboxSegment(value), bigEndian);
        } else {
            putInt(segment, enclosing, base, offset, SharedUtils.unboxSegment32(value), bigEndian);
        }
    }

    @ForceInline
    private static AbstractMemorySegmentImpl checkSegment(Object segment, MemoryLayout enclosing, long base, boolean readOnly) {
        AbstractMemorySegmentImpl bb = (AbstractMemorySegmentImpl) Objects.requireNonNull(segment);
        bb.checkEnclosingLayout(base, enclosing, readOnly);
        return bb;
    }

    @ForceInline
    private static long offset(AbstractMemorySegmentImpl bb, long base, long offset) {
        return bb.unsafeGetOffset() + base + offset;
    }
}
