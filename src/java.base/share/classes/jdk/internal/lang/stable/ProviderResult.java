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
 * Sealed class hierarchy for expressing the results of a provider invocation.
 * @param <T> value holder type
 */
sealed interface ProviderResult<T> {

    /**
     * Models a non-null result from a provider.
     */
    record NonNull<T>(T value) implements ProviderResult<T> {}

    /**
     * Models a {@code null} result from a provider.
     */
    enum Null implements ProviderResult<Object> { INSTANCE;
        @Override public String toString() { return "Null"; }

        @SuppressWarnings("unchecked")
        static <T> ProviderResult<T> instance() {
            return (ProviderResult<T>) INSTANCE;
        }

    }

    /**
     * Models an error from a provider invocation.
     *
     * @param throwableClass class of the throwable thrown by the provider
     * @param <X> throwable type
     */
    record Error<T, X extends Throwable>(Class<X> throwableClass) implements ProviderResult<T> {}

}
