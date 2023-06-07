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

package jdk.internal.util.concurrent.lazy;

public final class LazyUtil {

    private LazyUtil() {
    }

    public static Byte[] toObjectArray(byte[] array) {
        var result = new Byte[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = array[i];
        }
        return result;
    }

    public static Boolean[] toObjectArray(boolean[] array) {
        var result = new Boolean[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = array[i];
        }
        return result;
    }

    public static Short[] toObjectArray(short[] array) {
        var result = new Short[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = array[i];
        }
        return result;
    }

    public static Character[] toObjectArray(char[] array) {
        var result = new Character[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = array[i];
        }
        return result;
    }

    public static Float[] toObjectArray(float[] array) {
        var result = new Float[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = array[i];
        }
        return result;
    }

    public static ConstructingSentinel CONSTRUCTING_SENTINEL = new ConstructingSentinel();
    public static NullSentinel NULL_SENTINEL = new NullSentinel();
    public static ErrorSentinel ERROR_SENTINEL = new ErrorSentinel();
    public static NonNullSentinel BOUND_SENTINEL = new NonNullSentinel();

    interface Bound{}
    static final class ConstructingSentinel { private ConstructingSentinel() {} }
    static final class NullSentinel implements Bound { private NullSentinel() {} }
    static final class NonNullSentinel implements Bound { private NonNullSentinel() {} }
    static final class ErrorSentinel { private ErrorSentinel() {} }

}
