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
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.util.Lazy;
import java.util.Objects;

/** Adapts historical updater tests to the builder while retaining their cases. */
final class UpdaterTestAdapter {
    private UpdaterTestAdapter() { }

    static <C> Lazy.UpdaterBuilder<C> locationBuilder(
            Lazy.UpdaterMode mode, Lookup lookup, Class<C> computationShape,
            String selector, Class<?> unqualifiedOwner) {
        Lazy.UpdaterBuilder<C> builder = Lazy.ofUpdater(computationShape)
                .withMode(mode).withLookup(lookup);
        Objects.requireNonNull(selector);
        int separator = selector.lastIndexOf('.');
        if (separator < 0) {
            return builder.withLocation(receiverType(computationShape,
                    unqualifiedOwner), selector);
        }
        String binaryName = selector.substring(0, separator);
        String fieldName = selector.substring(separator + 1);
        try {
            Class<?> owner = Class.forName(binaryName, false,
                    lookup.lookupClass().getClassLoader());
            return builder.withLocation(owner, fieldName);
        } catch (ClassNotFoundException exception) {
            throw new IllegalArgumentException("Unknown field owner: " + binaryName,
                    exception);
        }
    }

    static Class<?> computerShape(Class<?> useSiteShape) {
        Objects.requireNonNull(useSiteShape);
        for (Method method : useSiteShape.getMethods()) {
            if (Modifier.isAbstract(method.getModifiers())
                    && !method.isBridge() && !method.isSynthetic()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length > 0) {
                    return parameters[0];
                }
            }
        }
        throw new IllegalArgumentException("No computation parameter in "
                + useSiteShape.getName());
    }

    private static Class<?> receiverType(Class<?> computationShape,
                                         Class<?> fallback) {
        for (Method method : computationShape.getMethods()) {
            if (Modifier.isAbstract(method.getModifiers())
                    && !method.isBridge() && !method.isSynthetic()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length > 0 && !parameters[0].isPrimitive()
                        && parameters[0] != Object.class) {
                    return parameters[0];
                }
            }
        }
        return fallback;
    }
}
