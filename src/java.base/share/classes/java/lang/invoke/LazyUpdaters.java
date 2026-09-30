/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.invoke;

import java.util.Objects;

import jdk.internal.invoke.LazyUpdaterSupport;
import jdk.internal.javac.PreviewFeature;

/**
 * Factories for lazy updaters.
 *
 * @since 28
 */
public final class LazyUpdaters {

    /**
     * Creates a {@code LazyUpdaters} instance.
     */
    private LazyUpdaters() { }

    /**
     * The modes supported by lazy updaters.
     *
     * @since 29
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    public enum Mode {
        /**
         * Computes and writes with plain access, without coordination.
         */
        PLAIN,

        /**
         * Allows competing computations and installs one non-default value
         * with compare-and-exchange semantics.
         */
        PUBLISH_ONCE,

        /**
         * Allows one thread at a time to compute for a storage location.
         * A failed computation may be retried.
         */
        SERIALIZED
    }

    /**
     * Returns an implementation of the given functional interface that lazily
     * computes and publishes a value in the field or array element identified
     * by the accessor, in {@link Mode#PUBLISH_ONCE} mode.
     * The selected field or array element supplies the storage. Instance fields
     * have independent values for each receiver, a static field has one value
     * for its declaring class, and array elements have independent values for
     * each physical element. The factory neither invokes the accessor nor
     * reads the selected storage or invokes the computing function.
     *
     * <p>The accessor must be a non-capturing lambda that directly reads its
     * selected storage location. For an instance field it has one reference
     * argument, as in {@code p -> p.radius}; the computing function and
     * returned updater take the same receiver. For a static field it has no
     * arguments, as in
     * {@code () -> Geometry.radius}. For a one-dimensional array element it
     * takes an {@code int} index, optionally preceded by a reference. Examples
     * are {@code i -> Holder.ints[i]}, {@code (a, i) -> a[i]}, and
     * {@code (p, i) -> p.points[i]}. For a two-dimensional array element it
     * takes two {@code int} indices, optionally preceded by a reference, as in
     * {@code (i0, i1) -> Holder.matrix[i0][i1]},
     * {@code (a, i0, i1) -> a[i0][i1]}, or
     * {@code (p, i0, i1) -> p.points[i0][i1]}. The reference may be the
     * array itself or an owner with one array-reference field. The two indices
     * may be used in the opposite order. An index-only accessor must reach its
     * element through one static array field; a zero-argument accessor such as
     * {@code () -> Holder.ints} selects the array field itself.
     *
     * <p>For an instance field, the receiver is passed to the updater:
     * {@snippet lang=java :
     * class Point {
     *     private final double x, y;
     *     private Double radius;
     *
     *     Point(double x, double y) { this.x = x; this.y = y; }
     *     interface Radius { Double get(Point point); }
     *
     *     private static final Radius LAZY_RADIUS = LazyUpdaters.ofLocation(
     *             MethodHandles.lookup(), Radius.class,
     *             point -> point.radius,
     *             point -> Math.hypot(point.x, point.y));
     *
     *     double radius() { return LAZY_RADIUS.get(this); }
     * }
     * }
     * A boxed {@code Double} permits a computed radius of positive zero,
     * since the reserved value for this field is {@code null}.
     *
     * <p>For a static field, the updater takes no arguments:
     * {@snippet lang=java :
     * class Answer {
     *     private static int value;
     *     private static final java.util.function.IntSupplier LAZY_VALUE =
     *             LazyUpdaters.ofLocation(MethodHandles.lookup(),
     *                     java.util.function.IntSupplier.class,
     *                     () -> Answer.value, () -> 42);
     *
     *     static int value() { return LAZY_VALUE.getAsInt(); }
     * }
     * }
     *
     * <p>For an element of a static array, the updater takes only its index:
     * {@snippet lang=java :
     * class Holder {
     *     private static final int[] ints = new int[8];
     *     private static final java.util.function.IntUnaryOperator LAZY_INT =
     *             LazyUpdaters.ofLocation(MethodHandles.lookup(),
     *                     java.util.function.IntUnaryOperator.class,
     *                     index -> Holder.ints[index], index -> index + 1);
     *
     *     static int get(int index) { return LAZY_INT.applyAsInt(index); }
     * }
     * }
     * The static field may be {@code final}; its array elements remain mutable.
     * A two-dimensional static array can likewise use an index-only interface
     * such as {@code java.util.function.IntBinaryOperator} with an accessor
     * {@code (row, column) -> Holder.matrix[row][column]}.
     *
     * <p>For an array held in an instance field, the accessor selects an
     * element through its owner:
     * {@snippet lang=java :
     * class Names {
     *     private final String[] entries = new String[8];
     *     interface Entry { String get(Names owner, int index); }
     *
     *     private static final Entry LAZY_ENTRY = LazyUpdaters.ofLocation(
     *             MethodHandles.lookup(), Entry.class,
     *             (owner, index) -> owner.entries[index],
     *             (owner, index) -> "entry-" + index);
     *
     *     String entry(int index) { return LAZY_ENTRY.get(this, index); }
     * }
     * }
     *
     * <p>An array can instead be passed directly as the updater's first
     * argument:
     * {@snippet lang=java :
     * class Values {
     *     private final int[] entries = new int[8];
     *     interface Entry { int get(int[] array, int index); }
     *
     *     private static final Entry LAZY_ENTRY = LazyUpdaters.ofLocation(
     *             MethodHandles.lookup(), Entry.class,
     *             (array, index) -> array[index],
     *             (array, index) -> index + 1);
     *
     *     int entry(int index) { return LAZY_ENTRY.get(entries, index); }
     * }
     * }
     *
     * <p>For a two-dimensional array, the updater takes two indices:
     * {@snippet lang=java :
     * class Grid {
     *     private final int[][] cells = { new int[4], new int[4] };
     *     interface Cell { int get(Grid owner, int row, int column); }
     *
     *     private static final Cell LAZY_CELL = LazyUpdaters.ofLocation(
     *             LazyUpdaters.Mode.SERIALIZED, MethodHandles.lookup(),
     *             Cell.class,
     *             (owner, row, column) -> owner.cells[row][column],
     *             (owner, row, column) -> 1 + row * 4 + column);
     *
     *     int cell(int row, int column) {
     *         return LAZY_CELL.get(this, row, column);
     *     }
     * }
     * }
     * The accessor is inspected using the loaded methods' bytecodes. Each
     * {@code int} argument of an array accessor must be used exactly once as an
     * index; arithmetic, constant or repeated indices, captures, more than one
     * field read, method references, and other computations or conversions in
     * the accessor are not supported. Boxing a primitive field or array-element
     * value is supported. The computing function may capture state or be a
     * method reference, and is retained by the returned updater. It receives
     * the original arguments, including their original index order when the
     * accessor reverses the two indices.
     *
     * <p>For this default mode, the selected storage location must be mutable,
     * support acquire reads and release compare-and-exchange updates, and, for
     * reference values, permit {@code null}. Its Java default value is
     * reserved to mean uninitialized: {@code null} for references,
     * {@code false} for {@code boolean}, zero for integral types, and positive
     * zero for floating-point types. A computation that returns the reserved
     * value throws {@code IllegalArgumentException} without publishing a
     * value. Negative floating-point zero and NaN are permitted. A preexisting
     * non-default value is used without invoking the computing function.
     * The caller is responsible for safely publishing a receiver or array and
     * any preinitialized value it holds to other threads.
     *
     * <p>A null-restricted reference array cannot use {@code null} as the
     * uninitialized value. The factory does not inspect the array; selecting
     * one of its elements throws {@code IllegalArgumentException} before
     * invoking the computation or returning a preexisting value.
     *
     * <p>An invocation reads the selected location with acquire semantics. If
     * the value is uninitialized, it invokes the computing function, checks
     * the result, and attempts to publish it with a release
     * compare-and-exchange. Computations may race, but successful invocations
     * return the published winner. A
     * losing invocation performs an acquire read before returning the winner.
     * Actions preceding the winner's publication happen
     * before actions following an invocation that observes that publication.
     * A computation that throws propagates its exception unchanged, publishes
     * nothing, and may be retried by another invocation. Recursive invocation
     * of an updater for the same uninitialized location is not detected.
     *
     * <p>The selected field or array element must be reserved for lazy storage
     * while an updater is in use: it must not be reset or otherwise modified
     * directly. A field supplying an array reference, whether static or an
     * instance field, and any selected inner-array reference, must not be
     * replaced while the updater is in use; the array field may be final. The
     * array-reference path is resolved once per updater invocation. Multiple
     * updaters may share a location if their computations are interchangeable;
     * whichever publishes first determines the value returned by all of them.
     * The factory allocates no additional storage per receiver or array element
     * and retains no receiver or array except through state explicitly captured
     * by the computing function. Accessing a static field through the updater
     * initializes its declaring class if necessary, following normal var-handle
     * semantics.
     *
     * <p>The interface must have a single abstract method, with no parameters
     * for a static field, one reference parameter for an instance field, one
     * or two {@code int} parameters for an element of a static array field, or
     * a reference parameter followed by one or two {@code int} parameters for
     * an element reached through the reference, and a non-void result. For
     * array elements with a reference parameter, that reference must be the
     * array itself or an owner with a single array-reference field. Inherited
     * abstract methods with the same erased parameter types, covariant return
     * types, and default methods are supported. The result must be the field or
     * element type, or a reference type to which its value, boxed if necessary,
     * can be assigned. Computed results must also be convertible to that type;
     * an incompatible result fails before publication. A null instance
     * receiver, selected array, or inner array, or an out-of-bounds index fails
     * before computation with the corresponding
     * {@code NullPointerException} or {@code ArrayIndexOutOfBoundsException}.
     * The supplied lookup must have access to the interface, any selected or
     * array-reference field, and the selected array type; any receiver
     * restriction imposed by the lookup is preserved.
     *
     * @implNote Sealed and hidden interface types, inherited abstract methods
     * requiring bridges with different parameter types, and accessor
     * lambdas whose proxy forwards through a method handle are not supported.
     * The latter includes lambdas declared in hidden classes.
     *
     * @param lookup the lookup used to authorize field and interface access
     * @param type the functional interface implemented by both functions and
     *             the returned updater
     * @param locationAccessor a non-capturing lambda identifying the field or
     *                         array element
     * @param computingFunction the computation to invoke for an uninitialized
     *                          field or array element
     * @param <T> the functional interface type
     * @return a lazy updater implementing {@code type}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if the interface, functions, or selected
     *         storage location are incompatible or unsupported, or the lookup
     *         cannot access the field, array type, or interface
     * @since 28
     * @see #ofLocation(Mode, MethodHandles.Lookup, Class, Object, Object)
     */
    public static <T> T ofLocation(MethodHandles.Lookup lookup,
                                   Class<T> type,
                                   T locationAccessor,
                                   T computingFunction) {
        return ofLocation(Mode.PUBLISH_ONCE, lookup, type,
                locationAccessor, computingFunction);
    }

    /**
     * Returns an implementation of the given functional interface that lazily
     * computes a value in the field or array element identified by the
     * accessor, using the specified mode. The storage-selection, default-value,
     * and functional-interface requirements are the same as for
     * {@link #ofLocation(MethodHandles.Lookup, Class, Object, Object)}. The
     * required var-handle access modes depend on the updater mode.
     *
     * <p>In {@link Mode#PUBLISH_ONCE} mode, competing computations race to
     * install one non-default value using release compare-and-exchange
     * after an acquire read. Every successful invocation returns the installed
     * value, including one whose result was discarded. A failed computation is
     * not retained and may be retried. This is the mode used by the overload
     * without a mode argument.
     *
     * <p>In {@link Mode#SERIALIZED} mode, only one thread at a time invokes
     * the computing function for each selected storage location. For a field,
     * this is the field and receiver (or the static field); for an array
     * element, it is the final leaf array by identity and the final index.
     * Consequently, aliases of the same leaf array element share coordination
     * even through different owner fields, outer arrays, or updater objects.
     * Competing invocations wait for it. A successful result is stored with
     * release effects and read with acquire effects. If the computation throws
     * or returns the reserved
     * uninitialized value, the invocation fails without changing the location;
     * another invocation, including one already waiting, may then retry. A
     * throwable from the computing function is propagated unchanged. Recursive
     * access by the computing thread to the same storage location throws
     * {@link IllegalStateException}; if it escapes the computing function, a
     * later invocation may retry. Waiting is uninterruptible and preserves the
     * thread's interrupt status. Dependencies between lazy computations on
     * different threads can deadlock. This mode uses auxiliary coordination
     * state for each field or array element and, for instance fields and array
     * elements, per receiver or final leaf array until a successful
     * computation or until that object becomes unreachable.
     *
     * <p>In {@link Mode#PLAIN} mode, an invocation reads the selected location
     * with plain memory effects. If it has its reserved default value, the
     * invocation computes a replacement and writes it with plain memory
     * effects, returning its own computed result. Concurrent invocations may
     * compute different results and overwrite one another; they need not
     * return the value that remains in the location. A later invocation on
     * another thread may still read the reserved default value and compute
     * again. Plain reads and writes of {@code long} and {@code double}
     * locations are not guaranteed to be atomic. No publication guarantee is
     * provided for the
     * computed result. A failed computation is propagated directly and may be
     * retried. Callers must provide their own coordination
     * if concurrent accesses require a single result or safe publication.
     * Recursive access is not detected and may cause unbounded recursion.
     *
     * <p>The caller must use the same mode for all updaters sharing a location.
     * The location must not be written directly or through another lazy updater
     * while these updaters are in use. Mixing modes or making other writes can
     * defeat the mode's guarantees. Computing functions sharing a location
     * should be interchangeable. Updaters in {@code SERIALIZED} mode share
     * coordination state for the same field and receiver or physical array
     * element, even when they were created with different computing functions.
     * Whichever updater first computes successfully determines the retained
     * value.
     *
     * @implNote In {@code SERIALIZED} mode, coordination state for an instance
     * receiver or array is keyed weakly. Cleared entries are removed on a later
     * uninitialized access, so their metadata can remain until then or until
     * class unloading.
     *
     * @param mode the updater mode
     * @param lookup the lookup used to authorize field and interface access
     * @param type the functional interface implemented by both functions and
     *             the returned updater
     * @param locationAccessor a non-capturing lambda identifying the field or
     *                         array element
     * @param computingFunction the computation to invoke for an uninitialized
     *                          field or array element
     * @param <T> the functional interface type
     * @return a lazy updater implementing {@code type}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if the interface, functions, or selected
     *         storage location are incompatible or unsupported, or the lookup
     *         cannot access the field, array type, or interface
     * @since 29
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    public static <T> T ofLocation(Mode mode,
                                   MethodHandles.Lookup lookup,
                                   Class<T> type,
                                   T locationAccessor,
                                   T computingFunction) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(lookup);
        Objects.requireNonNull(type);
        Objects.requireNonNull(locationAccessor);
        Objects.requireNonNull(computingFunction);
        return LazyUpdaterSupport.ofLocation(mode, lookup, type,
                locationAccessor, computingFunction);
    }
}
