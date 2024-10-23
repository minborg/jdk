/*
 *  Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.vm.annotation.ForceInline;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

/**
 * Common methods for group and sequence layout OfClass implementations.
 *
 * @param <T> carrier type
 */
public interface InternalCompositeLayoutOfClass<T> {
    Class<T> carrier();

    MethodHandle getter();

    MethodHandle setter();

    MethodHandle adaptedGetter();

    MethodHandle adaptedSetter();

    @SuppressWarnings("unchecked")
    @ForceInline
    default T get(MemorySegment segment, long offset) {
        try {
            return (T) adaptedGetter().invokeExact(segment, offset);
        } catch (NullPointerException |
                 IndexOutOfBoundsException |
                 WrongThreadException |
                 IllegalStateException |
                 IllegalArgumentException rethrow) {
            throw rethrow;
        } catch (Throwable e) {
            throw new RuntimeException("Unable to invoke getter() with " +
                    "segment=" + segment +
                    ", offset=" + offset, e);
        }
    }

    default void set(MemorySegment segment, long offset, T t) {
        try {
            adaptedSetter().invokeExact(segment, offset, (Object) t);
        } catch (IndexOutOfBoundsException |
                 WrongThreadException |
                 IllegalStateException |
                 IllegalArgumentException |
                 UnsupportedOperationException |
                 NullPointerException rethrow) {
            throw rethrow;
        } catch (Throwable e) {
            throw new RuntimeException("Unable to invoke setter() with " +
                    "segment=" + segment +
                    ", offset=" + offset +
                    ", t=" + t, e);
        }
    }

    static MethodHandle adaptGetter(MethodHandle getter) {
        return getter.asType(MethodType.methodType(Object.class, MemorySegment.class, long.class));
    }

    static MethodHandle adaptSetter(MethodHandle setter) {
        return setter.asType(MethodType.methodType(void.class, MemorySegment.class, long.class, Object.class));
    }

}
