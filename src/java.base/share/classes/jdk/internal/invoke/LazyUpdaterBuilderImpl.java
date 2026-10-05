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

package jdk.internal.invoke;

import java.lang.foreign.MemoryLayout;
import java.lang.invoke.MethodHandles;
import java.lang.util.Lazy.UpdaterBuilder;
import java.lang.util.Lazy.UpdaterMode;
import java.util.Objects;

/**
 * Immutable configuration for the public lazy-updater builder. No selected
 * storage is accessed until an updater returned by a terminal method is used.
 */
final class LazyUpdaterBuilderImpl<C> implements UpdaterBuilder<C> {
    private final Class<C> computationShape;
    private final UpdaterMode mode;
    private final MethodHandles.Lookup lookup;
    private final boolean explicitLookup;
    private final Location location;

    private LazyUpdaterBuilderImpl(Class<C> computationShape,
                                   UpdaterMode mode,
                                   MethodHandles.Lookup lookup,
                                   boolean explicitLookup,
                                   Location location) {
        this.computationShape = computationShape;
        this.mode = mode;
        this.lookup = lookup;
        this.explicitLookup = explicitLookup;
        this.location = location;
    }

    static <C> UpdaterBuilder<C> initial(Class<C> computationShape) {
        Objects.requireNonNull(computationShape);
        return new LazyUpdaterBuilderImpl<>(computationShape,
                UpdaterMode.PUBLISH_ONCE, MethodHandles.publicLookup(),
                false, null);
    }

    @Override
    public UpdaterBuilder<C> withMode(UpdaterMode mode) {
        Objects.requireNonNull(mode);
        return new LazyUpdaterBuilderImpl<>(computationShape, mode, lookup,
                explicitLookup, location);
    }

    @Override
    public UpdaterBuilder<C> withLookup(MethodHandles.Lookup lookup) {
        Objects.requireNonNull(lookup);
        return new LazyUpdaterBuilderImpl<>(computationShape, mode, lookup,
                true, location);
    }

    @Override
    public UpdaterBuilder<C> withLocation(Class<?> ownerClass,
                                           String fieldName) {
        if (location != null) {
            throw new IllegalStateException("An updater location is already selected");
        }
        Objects.requireNonNull(ownerClass);
        Objects.requireNonNull(fieldName);
        if (fieldName.isEmpty() || fieldName.indexOf('.') >= 0) {
            throw new IllegalArgumentException(
                    "Expected a simple field name: " + fieldName);
        }
        return new LazyUpdaterBuilderImpl<>(computationShape, mode, lookup,
                explicitLookup, new FieldLocation(ownerClass, fieldName));
    }

    @Override
    public UpdaterBuilder<C> withLocation(
            MemoryLayout layout, MemoryLayout.PathElement... pathElements) {
        if (location != null) {
            throw new IllegalStateException("An updater location is already selected");
        }
        Objects.requireNonNull(layout);
        Objects.requireNonNull(pathElements);
        MemoryLayout.PathElement[] copy = pathElements.clone();
        for (MemoryLayout.PathElement element : copy) {
            Objects.requireNonNull(element);
        }
        return new LazyUpdaterBuilderImpl<>(computationShape, mode, lookup,
                explicitLookup, new SegmentLocation(layout, copy));
    }

    @Override
    public C build(C computingFunction) {
        Objects.requireNonNull(computingFunction);
        if (location instanceof FieldLocation field) {
            return LazyUpdaterSupport.ofField(mode, lookup,
                    computationShape, field.ownerClass(), field.fieldName(),
                    computingFunction);
        }
        requireNoExplicitLookup();
        if (location instanceof SegmentLocation segment) {
            return LazySegmentUpdaterSupport.ofSegment(mode,
                    computationShape, segment.layout(), segment.path(),
                    computingFunction);
        }
        return LazyUpdaterSupport.ofArray(mode, computationShape,
                computingFunction);
    }

    @Override
    public <U> U buildUseSite(Class<U> useSiteShape) {
        Objects.requireNonNull(useSiteShape);
        if (location instanceof FieldLocation field) {
            return LazyUpdaterSupport.ofFieldUseSite(mode, lookup,
                    computationShape, useSiteShape, field.ownerClass(),
                    field.fieldName());
        }
        requireNoExplicitLookup();
        if (location instanceof SegmentLocation segment) {
            return LazySegmentUpdaterSupport.ofSegmentUseSite(mode,
                    computationShape, useSiteShape, segment.layout(),
                    segment.path());
        }
        return LazyUpdaterSupport.ofArrayUseSite(mode,
                computationShape, useSiteShape);
    }

    private void requireNoExplicitLookup() {
        if (explicitLookup) {
            throw new IllegalArgumentException(
                    "withLookup applies only to named-field locations");
        }
    }

    private sealed interface Location permits FieldLocation, SegmentLocation { }

    private record FieldLocation(Class<?> ownerClass, String fieldName)
            implements Location { }

    private record SegmentLocation(MemoryLayout layout,
                                   MemoryLayout.PathElement[] path)
            implements Location { }
}
