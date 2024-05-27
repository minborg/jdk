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

package jdk.internal.lang.stable;

/**
 * Sealed class hierarchy for expressing the results of a provider computation.
 *
 * @param <T> value holder type
 */
sealed interface Computation<T> {

    /**
     * Models a valid value from a provider computation.
     */
    record Value<T>(T value) implements Computation<T> {
        @SuppressWarnings("unchecked")
        static <T> Computation<T> of(T value) {
            final class Holder {
                static final Value<?> INSTANCE = new Value<>(null);
            }
            return value == null
                    ? (Computation<T>) Holder.INSTANCE
                    : new Value<>(value);
        }
    }

    /**
     * Models an error from a provider computation.
     *
     * @param throwableClassName class name of the throwable thrown by the provider
     */
    record Error<T>(String throwableClassName) implements Computation<T> {
        static <T, X extends Throwable> Computation<T> of(X throwable) {
            return new Error<>(throwable.getClass().getName());
        }
    }

}
