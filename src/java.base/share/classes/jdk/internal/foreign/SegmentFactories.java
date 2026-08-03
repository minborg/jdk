/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.access.foreign.UnmapperProxy;
import jdk.internal.misc.Unsafe;
import jdk.internal.misc.VM;
import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

/**
 * This class is used to create memory segments, while making sure that classes
 * are initialized in the right order (that is, that {@code MemorySegment} is always initialized first).
 * See {@link SegmentFactories#ensureInitialized()}.
 */
public class SegmentFactories {

    // The maximum alignment supported by malloc - typically 16 bytes on
    // 64-bit platforms and 8 bytes on 32-bit platforms.
    private static final long MAX_MALLOC_ALIGN = Unsafe.ADDRESS_SIZE == 4 ? 8 : 16;

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    // Unsafe native segment factories. These are used by the implementation code, to skip the sanity checks
    // associated with MemorySegment::ofAddress.

    @ForceInline
    public static MemorySegmentImpl makeNativeSegmentUnchecked(long min, long byteSize,
                                                               MemorySessionImpl sessionImpl,
                                                               boolean readOnly, Runnable action) {
        ensureInitialized();
        if (action == null) {
            sessionImpl.checkValidState();
        } else {
            sessionImpl.addCloseAction(action);
        }
        return new MemorySegmentImpl(MemorySegmentSupport.nativeSupport(),
                min, null, null, byteSize, readOnly, sessionImpl);
    }

    @ForceInline
    public static MemorySegmentImpl makeNativeSegmentUnchecked(long min, long byteSize, MemorySessionImpl sessionImpl) {
        ensureInitialized();
        sessionImpl.checkValidState();
        return new MemorySegmentImpl(MemorySegmentSupport.nativeSupport(),
                min, null, null, byteSize, false, sessionImpl);
    }

    @ForceInline
    public static MemorySegmentImpl makeNativeSegmentUnchecked(long min, long byteSize) {
        ensureInitialized();
        return new MemorySegmentImpl(MemorySegmentSupport.nativeSupport(),
                min, null, null, byteSize, false, MemorySessionImpl.GLOBAL_SESSION);
    }

    public static MemorySegmentImpl fromArray(byte[] arr) {
        ensureInitialized();
        Objects.requireNonNull(arr);
        long byteSize = (long)arr.length * Utils.BaseAndScale.BYTE.scale();
        return new MemorySegmentImpl(
                MemorySegmentSupport.ofByteSupport(), Utils.BaseAndScale.BYTE.base(),
                arr, null, byteSize, false, MemorySessionImpl.createHeap(arr));
    }

    public static MemorySegmentImpl fromArray(short[] arr) {
        ensureInitialized();
        Objects.requireNonNull(arr);
        long byteSize = (long)arr.length * Utils.BaseAndScale.SHORT.scale();
        return new MemorySegmentImpl(
                MemorySegmentSupport.ofShortSupport(), Utils.BaseAndScale.SHORT.base(),
                arr, null, byteSize, false, MemorySessionImpl.createHeap(arr));
    }

    public static MemorySegmentImpl fromArray(int[] arr) {
        ensureInitialized();
        Objects.requireNonNull(arr);
        long byteSize = (long)arr.length * Utils.BaseAndScale.INT.scale();
        return new MemorySegmentImpl(
                MemorySegmentSupport.ofIntSupport(), Utils.BaseAndScale.INT.base(),
                arr, null, byteSize, false, MemorySessionImpl.createHeap(arr));
    }

    public static MemorySegmentImpl fromArray(char[] arr) {
        ensureInitialized();
        Objects.requireNonNull(arr);
        long byteSize = (long)arr.length * Utils.BaseAndScale.CHAR.scale();
        return new MemorySegmentImpl(
                MemorySegmentSupport.ofCharSupport(), Utils.BaseAndScale.CHAR.base(),
                arr, null, byteSize, false, MemorySessionImpl.createHeap(arr));
    }

    public static MemorySegmentImpl fromArray(float[] arr) {
        ensureInitialized();
        Objects.requireNonNull(arr);
        long byteSize = (long)arr.length * Utils.BaseAndScale.FLOAT.scale();
        return new MemorySegmentImpl(
                MemorySegmentSupport.ofFloatSupport(), Utils.BaseAndScale.FLOAT.base(),
                arr, null, byteSize, false, MemorySessionImpl.createHeap(arr));
    }

    public static MemorySegmentImpl fromArray(double[] arr) {
        ensureInitialized();
        Objects.requireNonNull(arr);
        long byteSize = (long)arr.length * Utils.BaseAndScale.DOUBLE.scale();
        return new MemorySegmentImpl(
                MemorySegmentSupport.ofDoubleSupport(), Utils.BaseAndScale.DOUBLE.base(),
                arr, null, byteSize, false, MemorySessionImpl.createHeap(arr));
    }

    public static MemorySegmentImpl fromArray(long[] arr) {
        ensureInitialized();
        Objects.requireNonNull(arr);
        long byteSize = (long)arr.length * Utils.BaseAndScale.LONG.scale();
        return new MemorySegmentImpl(
                MemorySegmentSupport.ofLongSupport(), Utils.BaseAndScale.LONG.base(),
                arr, null, byteSize, false, MemorySessionImpl.createHeap(arr));
    }

    // Buffer conversion factories

    public static MemorySegmentImpl arrayOfByteSegment(Object base, long offset, long length,
                                                       boolean readOnly, MemorySessionImpl bufferScope) {
        return new MemorySegmentImpl(MemorySegmentSupport.ofByteSupport(),
                offset, base, null, length, readOnly, bufferScope);
    }

    public static MemorySegmentImpl arrayOfShortSegment(Object base, long offset, long length,
                                                        boolean readOnly, MemorySessionImpl bufferScope) {
        return new MemorySegmentImpl(MemorySegmentSupport.ofShortSupport(),
                offset, base, null, length, readOnly, bufferScope);
    }

    public static MemorySegmentImpl arrayOfCharSegment(Object base, long offset, long length,
                                                       boolean readOnly, MemorySessionImpl bufferScope) {
        return new MemorySegmentImpl(MemorySegmentSupport.ofCharSupport(),
                offset, base, null, length, readOnly, bufferScope);
    }

    public static MemorySegmentImpl arrayOfIntSegment(Object base, long offset, long length,
                                                      boolean readOnly, MemorySessionImpl bufferScope) {
        return new MemorySegmentImpl(MemorySegmentSupport.ofIntSupport(),
                offset, base, null, length, readOnly, bufferScope);
    }

    public static MemorySegmentImpl arrayOfFloatSegment(Object base, long offset, long length,
                                                        boolean readOnly, MemorySessionImpl bufferScope) {
        return new MemorySegmentImpl(MemorySegmentSupport.ofFloatSupport(),
                offset, base, null, length, readOnly, bufferScope);
    }

    public static MemorySegmentImpl arrayOfLongSegment(Object base, long offset, long length,
                                                       boolean readOnly, MemorySessionImpl bufferScope) {
        return new MemorySegmentImpl(MemorySegmentSupport.ofLongSupport(),
                offset, base, null, length, readOnly, bufferScope);
    }

    public static MemorySegmentImpl arrayOfDoubleSegment(Object base, long offset, long length,
                                                         boolean readOnly, MemorySessionImpl bufferScope) {
        return new MemorySegmentImpl(MemorySegmentSupport.ofDoubleSupport(),
                offset, base, null, length, readOnly, bufferScope);
    }

    public static MemorySegmentImpl allocateNativeSegment(long byteSize, long byteAlignment, MemorySessionImpl sessionImpl,
                                                          boolean shouldReserve, boolean init) {
        long address = SegmentFactories.allocateNativeInternal(byteSize, byteAlignment, sessionImpl, shouldReserve, init);
        return new MemorySegmentImpl(MemorySegmentSupport.nativeSupport(),
                address, null, null, byteSize, false, sessionImpl);
    }

    private static long allocateNativeInternal(long byteSize, long byteAlignment, MemorySessionImpl sessionImpl,
                                               boolean shouldReserve, boolean init) {
        ensureInitialized();
        Utils.checkAllocationSizeAndAlign(byteSize, byteAlignment);
        sessionImpl.checkValidState();
        if (VM.isDirectMemoryPageAligned()) {
            byteAlignment = Math.max(byteAlignment, MemorySegmentImpl.NIO_ACCESS.pageSize());
        }
        // Always allocate at least some memory so that zero-length segments have distinct
        // non-zero addresses.
        byteSize = Math.max(1, byteSize);

        // Align the allocation size up to a multiple of 8 so we can init the memory with longs
        long alignedSize = init ? Utils.alignUp(byteSize, Long.BYTES) : byteSize;
        // Check for wrap around
        if (alignedSize < 0) {
            throw new OutOfMemoryError();
        }

        long allocationSize;
        long allocationBase;
        long result;
        if (byteAlignment > MAX_MALLOC_ALIGN) {
            allocationSize = alignedSize + byteAlignment - MAX_MALLOC_ALIGN;
            if (shouldReserve) {
                MemorySegmentImpl.NIO_ACCESS.reserveMemory(allocationSize, byteSize);
            }

            allocationBase = allocateMemoryWrapper(allocationSize);
            result = Utils.alignUp(allocationBase, byteAlignment);
        } else {
            // always allocate at least 'byteAlignment' bytes, so that malloc is guaranteed to
            // return a pointer aligned to that alignment, for cases where byteAlignment > alignedSize
            allocationSize = Math.max(alignedSize, byteAlignment);
            if (shouldReserve) {
                MemorySegmentImpl.NIO_ACCESS.reserveMemory(allocationSize, byteSize);
            }

            allocationBase = allocateMemoryWrapper(allocationSize);
            result = allocationBase;
        }

        if (init) {
            initNativeMemory(result, alignedSize);
        }
        final long cleanupByteSize = byteSize;
        sessionImpl.addOrCleanupIfFail(new MemorySessionImpl.ResourceList.ResourceCleanup() {
            @Override
            public void cleanup() {
                UNSAFE.freeMemory(allocationBase);
                if (shouldReserve) {
                    MemorySegmentImpl.NIO_ACCESS.unreserveMemory(allocationSize, cleanupByteSize);
                }
            }
        });
        return result;
    }

    private static void initNativeMemory(long address, long byteSize) {
        for (long i = 0; i < byteSize; i += Long.BYTES) {
            UNSAFE.putLongUnaligned(null, address + i, 0);
        }
    }

    private static long allocateMemoryWrapper(long size) {
        try {
            return UNSAFE.allocateMemory(size);
        } catch (IllegalArgumentException ex) {
            throw new OutOfMemoryError();
        }
    }

    public static MemorySegmentImpl mapSegment(long size, UnmapperProxy unmapper, boolean readOnly, MemorySessionImpl sessionImpl) {
        ensureInitialized();
        if (unmapper != null) {
            MemorySegmentImpl segment = new MemorySegmentImpl(
                    MemorySegmentSupport.mappedSupport(), unmapper.address(), null,
                    unmapper, size, readOnly, sessionImpl);
            MemorySessionImpl.ResourceList.ResourceCleanup resource =
                    new MemorySessionImpl.ResourceList.ResourceCleanup() {
                        @Override
                        public void cleanup() {
                            unmapper.unmap();
                        }
                    };
            sessionImpl.addOrCleanupIfFail(resource);
            return segment;
        } else {
            return new MemorySegmentImpl(MemorySegmentSupport.mappedSupport(),
                    0, null, null, 0, readOnly, sessionImpl);
        }
    }

    // The method below needs to be called before the implementation of MemorySegment
    // is instantiated. This is to make sure that we cannot have an initialization deadlock
    // where one thread attempts to initialize e.g. MemorySegment (and then MemorySegmentImpl, via
    // the MemorySegment.NULL field) while another thread is attempting to initialize
    // MemorySegmentImpl (and then MemorySegment, the super-interface).
    @ForceInline
    private static void ensureInitialized() {
        MemorySegment segment = MemorySegment.NULL;
    }
}
