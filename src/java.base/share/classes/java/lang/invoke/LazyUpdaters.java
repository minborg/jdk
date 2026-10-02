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
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

import jdk.internal.invoke.LazyUpdaterSupport;
import jdk.internal.javac.PreviewFeature;

/**
 * Factories for lazy updaters.
 *
 * <p>A lazy updater uses an existing field or array element as storage for a
 * value computed when an uninitialized location is first accessed. Each
 * field or array element has independent storage. Subject to the argument and
 * storage constraints below, an access that observes a non-default value
 * returns it without computation.
 * <p>
 * The factories do not read the selected storage or invoke a computing
 * function. They provide two ways to select a location:
 * <ul>
 * <li>{@link #ofLocation(Mode, MethodHandles.Lookup, Class, String, Object)
 *     ofLocation} selects a named instance or static field, or an element of
 *     an array held in such a field.</li>
 * <li>{@link #ofArray(Mode, Class, Object) ofArray} selects an element of an array
 *     passed directly to the updater, without resolving a field or requiring
 *     a lookup.</li>
 * </ul>
 * Each factory has a <em>declaration-site</em> form that retains a computing
 * function provided when the updater is created, and a <em>use-site</em> form
 * that accepts a computing function on each access. Every factory requires
 * an explicit {@link Mode}, where {@link Mode#PUBLISH_ONCE} is the usual choice when
 * competing computations and discarded results are acceptable.
 * <p>
 * Unlike {@link java.util.LazyCaches}, which provides its own storage,
 * these updaters use caller-owned storage and reserve its Java default value
 * to mean uninitialized. Both classes offer {@code PUBLISH_ONCE} modes.
 * {@link Mode#SERIALIZED} coordinates computation of a location but allows a
 * failed computation to be retried, whereas
 * {@link java.util.LazyCaches.Mode#COMPUTE_ONCE} records the failure.
 * {@link Mode#PLAIN} is available only for updaters.
 *
 * <h2 id="access-shapes">Locations and access shapes</h2>
 * The single abstract method of the functional-interface {@code type}
 * determines the location's coordinates. In use-site forms, a computing
 * function precedes those coordinates. The following shapes describe the
 * coordinates themselves, excluding that leading function. For
 * {@code ofLocation}, an instance field takes a receiver first and a simple
 * field name as the selector. A static field takes no receiver and uses a
 * selector of the form {@code binaryClassName.fieldName}. The binary class
 * name, including {@code $} for a nested class, is resolved using the
 * supplied lookup's class loader and access privileges. The same binary
 * name can denote different classes for different lookups. A field declared
 * by the named owner or resolved instance owner takes precedence. Otherwise,
 * the nearest superclass declaration is selected unless an interface also
 * declares the name. With no superclass declaration, exactly one interface
 * declaration is accepted. Conflicting superclass and interface declarations,
 * or multiple interface declarations, are rejected. This search considers
 * declarations even when they are not inherited in Java source; lookup access
 * is checked separately. A static field can instead be named through its
 * declaring class or interface.
 * The selected field must be static precisely when no receiver is present.
 * <p>
 * With no index parameters, {@code ofLocation} selects the field itself.
 * One or more trailing {@code int} parameters select an element of an array
 * field. For {@code ofArray}, the first coordinate's erased parameter type
 * must be a concrete array type, followed by one or more {@code int} indices.
 * In both cases, the number of indices must not exceed the array's rank and
 * is subject to the usual Java and JVM method-parameter limits. Indices
 * address dimensions in order, from the outermost array to successively
 * inner arrays. No coordinate permutation or index expression is inferred.
 * The selected value may itself be an array: one index into an
 * {@code int[][][]} selects an {@code int[][]}, while three indices select an
 * {@code int}. {@code ofArray} always selects an element, not the array
 * reference itself. An array field may be {@code final} when an element is
 * selected; a field selected as the location must be non-final. An array
 * field's reference and any intermediate array references must not be
 * replaced while an updater is in use.
 * <p>
 * The accessor's functional method has a non-void result compatible with the
 * selected field or element type. It may return that type or a reference type
 * to which the selected value, boxed if necessary, can be assigned. For a
 * custom instance-field interface, the receiver type must be present in the
 * functional method's erased parameter types; a generic
 * {@code Function<Point, Double>} is unsuitable because its receiver erases
 * to {@code Object}. A functional-interface {@code Class} token is rejected
 * when type variables remain unresolved or erase a storage coordinate or
 * computing-function parameter needed to validate the updater. Declare a
 * non-generic interface whose own functional method has concrete parameter
 * and return types.
 * <p>
 * A number of {@linkplain ##ready-made-interfaces ready-made accessor interfaces}
 * are provided for common access coordinates, some of which require a
 * non-generic subinterface that fixes their type arguments. The supplied
 * accessor interface and, in a use-site form, its computing-function
 * interface must not be sealed or hidden. Inherited abstract methods with
 * the same erased parameter types and covariant return types, as well as
 * default methods, are supported; an interface requiring bridges between
 * different parameter types is not.
 *
 * <h2 id="computing-functions">Computing functions</h2>
 * In a declaration-site form, the computing function implements {@code type},
 * receives the updater's coordinates in their original order, and is retained
 * by the updater. In a use-site form, the accessor's functional method takes
 * a computing function first, followed by the storage coordinates. That
 * computing function must itself have a functional-interface type whose
 * method accepts the coordinates and returns a value compatible with the
 * selected storage. A reference parameter of the computing function may be a
 * supertype of its corresponding coordinate type. Its result may be a subtype
 * of reference storage or use boxing, unboxing, or widening primitive
 * conversion; a broader reference result is checked against the storage type
 * when computed. A computed result incompatible with the storage type fails
 * before publication. As with ordinary array stores, a runtime array whose
 * component type is narrower than the accessor's array type may reject a
 * computed value with {@link ArrayStoreException}.
 * <p>
 * A use-site updater does not retain a computing function. Every invocation
 * rejects a {@code null} computing function, even if the location is already
 * initialized. A non-null function is invoked only when a computation is
 * needed. If an invocation fails, a later invocation may supply a different
 * function. Functions used for the same location should be interchangeable,
 * since the updater mode determines which successful result is stored.
 *
 * <h3 id="ready-made-interfaces">Ready-made interfaces</h3>
 * These interfaces supply the computing function at the use site. Field
 * accessors use {@link #ofLocation(Mode, MethodHandles.Lookup, Class, String)
 * ofLocation}; direct array accessors use {@link #ofArray(Mode, Class) ofArray}.
 * The coordinates below follow the computing-function argument to
 * {@code getOrCompute}. Each computing-function cell gives its type followed
 * by an example lambda. For the instance examples, {@code Holder} has a
 * {@code final int seed} input and the lazy fields {@code int count},
 * {@code long total}, and {@code String name}.
 * <table class="striped">
 * <caption>Ready-made use-site accessor interfaces</caption>
 * <thead>
 * <tr>
 * <th scope="col">Interface</th>
 * <th scope="col">Target location</th>
 * <th scope="col">Coordinates</th>
 * <th scope="col">Computing function</th>
 * <th scope="col">Type fixing</th>
 * </tr>
 * </thead>
 * <tbody>
 * <tr>
 * <th scope="row">{@link StaticIntFieldAccessor StaticIntFieldAccessor}</th>
 * <td>Named static {@code int} field</td>
 * <td>None</td>
 * <td>{@code IntSupplier}<br>{@code () -> 42}</td>
 * <td>No; use {@code StaticIntFieldAccessor.class}</td>
 * </tr>
 * <tr>
 * <th scope="row">{@link StaticLongFieldAccessor StaticLongFieldAccessor}</th>
 * <td>Named static {@code long} field</td>
 * <td>None</td>
 * <td>{@code LongSupplier}<br>{@code () -> 42L}</td>
 * <td>No; use {@code StaticLongFieldAccessor.class}</td>
 * </tr>
 * <tr>
 * <th scope="row">{@link StaticReferenceFieldAccessor StaticReferenceFieldAccessor}</th>
 * <td>Named static reference field</td>
 * <td>None</td>
 * <td>{@code Supplier<V>}<br>{@code () -> "Ada"}</td>
 * <td>Yes; {@code interface StaticName extends
 *     StaticReferenceFieldAccessor<String> { }}</td>
 * </tr>
 * <tr>
 * <th scope="row">{@link IntFieldAccessor IntFieldAccessor}</th>
 * <td>Named instance {@code int} field</td>
 * <td>{@code R} receiver</td>
 * <td>{@code ToIntFunction<R>}<br>
 *     {@code holder -> Math.max(1, holder.seed)}</td>
 * <td>Yes; {@code interface Count extends IntFieldAccessor<Holder> { }}</td>
 * </tr>
 * <tr>
 * <th scope="row">{@link LongFieldAccessor LongFieldAccessor}</th>
 * <td>Named instance {@code long} field</td>
 * <td>{@code R} receiver</td>
 * <td>{@code ToLongFunction<R>}<br>
 *     {@code holder -> Math.max(1L, 100L * holder.seed)}</td>
 * <td>Yes; {@code interface Total extends LongFieldAccessor<Holder> { }}</td>
 * </tr>
 * <tr>
 * <th scope="row">{@link ReferenceFieldAccessor ReferenceFieldAccessor}</th>
 * <td>Named instance reference field</td>
 * <td>{@code R} receiver</td>
 * <td>{@code Function<R, V>}<br>
 *     {@code holder -> "name-" + holder.seed}</td>
 * <td>Yes; {@code interface Name extends
 *     ReferenceFieldAccessor<Holder, String> { }}</td>
 * </tr>
 * <tr>
 * <th scope="row">{@link IntArrayAccessor IntArrayAccessor}</th>
 * <td>Element of a directly supplied {@code int[]}</td>
 * <td>{@code int[] array, int index}</td>
 * <td>{@code IntArrayAccessor.Computer}<br>
 *     {@code (array, index) -> array.length - index}</td>
 * <td>No; use {@code IntArrayAccessor.class}</td>
 * </tr>
 * <tr>
 * <th scope="row">{@link LongArrayAccessor LongArrayAccessor}</th>
 * <td>Element of a directly supplied {@code long[]}</td>
 * <td>{@code long[] array, int index}</td>
 * <td>{@code LongArrayAccessor.Computer}<br>
 *     {@code (array, index) -> (long) array.length - index}</td>
 * <td>No; use {@code LongArrayAccessor.class}</td>
 * </tr>
 * <tr>
 * <th scope="row">{@link ReferenceArrayAccessor ReferenceArrayAccessor}</th>
 * <td>Element of a directly supplied {@code E[]}</td>
 * <td>{@code E[] array, int index}</td>
 * <td>{@code ReferenceArrayAccessor.Computer<E>}<br>
 *     {@code (array, index) -> "name-" + (array.length - index)}</td>
 * <td>Yes; {@code interface Names extends ReferenceArrayAccessor<String> { }}</td>
 * </tr>
 * </tbody>
 * </table>
 * For a "Yes" entry, pass the concrete subinterface's {@code Class} token
 * to the factory, for example {@code Count.class}. The generic ready-made
 * interface's {@code Class} token cannot be passed directly. An {@code E}
 * that is itself an array type selects an array-valued element with one index.
 * A custom accessor interface is required for access with two or more indices,
 * for array-field elements, and for primitive value types other than
 * {@code int} and {@code long}.
 * <p>
 * For a reference field accessor, the field's declared type must be a
 * reference type assignable to the accessor's value type. If that value type
 * is parameterized, or is a generic array type, it must match the field's
 * declared generic type. This check does not resolve type variables in a
 * generic field owner. A raw or broader value type is checked only at its
 * erased type, so its computing function must respect the field's type
 * arguments.
 *
 * <h2 id="uninitialized-locations">Uninitialized locations</h2>
 * The Java default value of a selected field or element means uninitialized:
 * {@code null} for references, {@code false} for {@code boolean}, zero for
 * integral types, and positive zero for floating-point types. A computation
 * producing this reserved value throws {@code IllegalArgumentException}
 * without publishing it. Negative floating-point zero and NaN are permitted.
 * A null-restricted reference field cannot use the null sentinel and is
 * rejected when an updater selecting that field is created. A null-restricted
 * reference array likewise cannot use the null sentinel; accessing one of
 * its elements through an updater throws {@code IllegalArgumentException}
 * before computation or return, even if the element already has a value.
 * <p>
 * A nullable wrapper field or array element can distinguish a computed zero
 * or {@code false} from uninitialized storage. If {@code null} is a logical
 * result, a non-null container such as {@link java.util.Optional} can
 * represent it.
 *
 * <h2 id="updater-modes">Updater modes</h2>
 * In {@link Mode#PUBLISH_ONCE} mode, an invocation reads the selected location
 * with acquire semantics. If it is uninitialized, the updater computes a
 * candidate and attempts to install it atomically with release effects.
 * Computations may race, but every invocation that returns normally returns
 * the value installed in the location, including an invocation whose result
 * was discarded.
 * A losing invocation observes the installed value with acquire effects before
 * returning it. Actions preceding the winner's publication
 * {@linkplain java.util.concurrent##MemoryVisibility happen-before} actions
 * following an invocation that observes it. A computation that throws, or
 * produces the reserved value, fails even if another invocation has
 * installed a value in the meantime; it publishes nothing and may be retried.
 * A throwable from the computation propagates unchanged. Actions of a
 * losing or failed computation are not published to other threads through
 * the location.
 * Recursive access to the same uninitialized location is not detected in
 * this mode and may recurse without bound.
 * <p>
 * In {@link Mode#SERIALIZED} mode, only one thread at a time invokes the
 * computing function for each selected physical location. For a field, that
 * is the field and receiver, or the static field. For an array element, it is
 * the final container array by identity and the final index, regardless of
 * how many outer dimensions were traversed. Aliases through different
 * owners, outer arrays, and updater objects therefore share coordination.
 * An invocation by another thread that finds a computation in progress waits
 * for it to finish, then returns its value or attempts its own computation
 * after a failure.
 * A successful result is stored with release effects and read with acquire
 * effects, providing the same publication guarantee as
 * {@code PUBLISH_ONCE}. If computation throws or returns the reserved value,
 * the invocation fails without changing the location; another invocation
 * may retry. A throwable from the computation propagates unchanged.
 * Recursive access by the computing thread to the same location throws
 * {@code IllegalStateException}. If it escapes the computing function, the
 * invocation fails and a later invocation may retry. Waiting is
 * uninterruptible and does not itself change interrupt status; the computing
 * function may change it.
 * Dependencies between computations on different threads can deadlock. This
 * mode uses auxiliary coordination state outside receivers and arrays. For
 * instance fields and array elements, per-location state is discarded after
 * successful computation; after failure, weakly keyed metadata may remain
 * until later cleanup. Generally speaking, it should be expected that this mode
 * is slower than {@link Mode#PUBLISH_ONCE}.
 * <p>
 * In {@link Mode#PLAIN} mode, an invocation reads the selected location with
 * plain memory semantics. If it has the reserved default value, the updater
 * computes and writes a replacement with plain effects, returning its own
 * result. Concurrent invocations may compute and overwrite one another's
 * values; a later invocation on another thread may still see the default and
 * compute again. A field's {@code volatile} modifier does not upgrade these
 * plain accesses. There is no portable atomicity guarantee for plain
 * {@code long} and {@code double} accesses; they may tear, particularly on
 * 32-bit platforms. <em>No safe publication
 * guarantee is provided.</em> A failed computation propagates directly and
 * may be retried. Recursive access is not detected and may recurse without
 * bound.
 *
 * <h2 id="access-and-ownership">Access and storage ownership</h2>
 * The caller must safely publish the receiver or array and any preexisting
 * value to other threads. The selected storage must be reserved for lazy use:
 * it must not be reset or otherwise modified directly while an updater is in
 * use. All updaters sharing a location must use the same mode, and their
 * computing functions should be interchangeable. Other writes can defeat the
 * mode's guarantees. {@code SERIALIZED} updaters share coordination for a
 * physical location even if they use different computing functions. An
 * updater does not add a holder to each receiver or array element. Apart
 * from objects captured by a declaration-site computing function, it does
 * not strongly retain receivers or arrays; {@code SERIALIZED} coordination
 * keys are weak. Publication of a computed reference does not make its later
 * mutations thread-safe, and accesses to different locations have no total
 * order.
 * <p>
 * For {@code ofLocation}, the lookup must authorize access to the accessor
 * interface, any use-site computing-function interface, the named field, and
 * any selected array type. A receiver restriction imposed by the lookup is
 * preserved. Accessing a static field through the updater
 * initializes its declaring class if necessary, following normal var-handle
 * semantics. {@code ofArray} needs no caller-provided lookup; the array
 * parameter and indices identify the element without resolving a field.
 * Non-public functional interfaces and array component types are supported
 * by {@code ofArray}. A null receiver, array, or intermediate array, or an
 * out-of-bounds index, fails before computation with the corresponding
 * {@code NullPointerException} or
 * {@code ArrayIndexOutOfBoundsException}. The array-reference path is resolved
 * once per invocation.
 *
 * @implNote In {@code PUBLISH_ONCE} mode, this implementation installs a
 *           candidate with
 *           {@link VarHandle#compareAndExchangeRelease compareAndExchangeRelease}.
 *           The exchange's witness has plain-read effects, so a losing
 *           invocation performs a separate acquire read before returning
 *           the winner.
 *           <p>
 *           In {@code SERIALIZED} mode, a receiver or array element's
 *           coordination state is created when its uninitialized storage is
 *           accessed. Cleared weak-key entries are removed on a later
 *           uninitialized access; their metadata can remain until then or
 *           until class unloading.
 *           <p>
 *           A retained result is never replaced, which may permit an
 *           implementation to constant-fold repeated accesses when it can
 *           determine a stable path to the storage and its result. Such a path
 *           may start from a {@code static final} field. Whether and when this
 *           optimization occurs is implementation-dependent. Eliding a storage
 *           read must preserve the {@linkplain ##updater-modes updater mode
 *           guarantee} for the access.
 *
 * @since 28
 */
@PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
public final class LazyUpdaters {

    // Suppresses default constructor, ensuring non-instantiability.
    private LazyUpdaters() { }

    /**
     * The modes supported by lazy updaters.
     *
     * @since 29
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    public enum Mode {

        /**
         * Allows competing computations and atomically installs one
         * non-default value with release effects.
         */
        PUBLISH_ONCE,

        /**
         * Allows one thread at a time to compute for a storage location.
         * A failed computation may be retried.
         */
        SERIALIZED,

        /**
         * Computes and writes with plain access, without coordination.
         *
         * <p><strong>Warning:</strong> This mode provides no safe publication
         * guarantee. Concurrent accesses may overwrite one another's values,
         * and an access on another thread may still observe the default value
         * and compute again. A field's {@code volatile} modifier does not
         * strengthen these plain accesses; {@code long} and {@code double}
         * accesses may tear.
         * Failed computations may be retried, and recursive access is not
         * detected.
         *
         * @see LazyUpdaters##updater-modes Updater modes
         */
        PLAIN
    }

    /**
     * Returns a declaration-site updater for a named field or array-field
     * element using the specified mode. The returned updater retains
     * {@code computingFunction}. The class documentation specifies the
     * {@linkplain ##access-shapes access shapes},
     * {@linkplain ##computing-functions computing-function rules},
     * {@linkplain ##uninitialized-locations reserved values}, and
     * {@linkplain ##updater-modes mode semantics}.
     *
     * <p>For example, an instance field can use a concrete receiver type:
     * {@snippet lang=java :
     * import java.lang.invoke.LazyUpdaters;
     * import java.lang.invoke.MethodHandles;
     *
     * import static java.lang.invoke.LazyUpdaters.Mode.PUBLISH_ONCE;
     *
     * class Point {
     *     private final double x, y;
     *     private Double radius;
     *
     *     Point(double x, double y) { this.x = x; this.y = y; }
     *
     *     interface Radius { Double getOrCompute(Point point); }
     *
     *     private static final Radius LAZY_RADIUS = LazyUpdaters.ofLocation(
     *             PUBLISH_ONCE, MethodHandles.lookup(),
     *             Radius.class, "radius",
     *             point -> Math.hypot(point.x, point.y));
     *
     *     double radius() { return LAZY_RADIUS.getOrCompute(this); }
     * }
     * }
     * The boxed result permits a computed radius of positive zero, because
     * {@code null}, rather than zero, is the reserved uninitialized value. In the example
     * above, a fixing version of a {@linkplain ##ready-made-interfaces ready-made interface}
     * could also be used:
     * {@snippet lang=java :
     *  interface Raius extends ReferenceFieldAccessor<Point, Double> { }
     * }
     *
     * <p>For a static field, the updater has no receiver:
     * {@snippet lang=java :
     * import java.lang.invoke.LazyUpdaters;
     * import java.lang.invoke.MethodHandles;
     * import java.util.function.IntSupplier;
     *
     * import static java.lang.invoke.LazyUpdaters.Mode.PUBLISH_ONCE;
     *
     * class Answer {
     *     private static int value;
     *
     *     private static final IntSupplier LAZY_VALUE =
     *             LazyUpdaters.ofLocation(PUBLISH_ONCE, MethodHandles.lookup(),
     *                     IntSupplier.class, Answer.class.getName() + ".value",
     *                     () -> 42);
     *
     *     static int value() { return LAZY_VALUE.getAsInt(); }
     * }
     * }
     *
     * <p>For an element of a static {@code int[]} field, the updater takes
     * only the index. The standard {@link java.util.function.IntUnaryOperator}
     * has the required shape:
     * {@snippet lang=java :
     * import java.lang.invoke.LazyUpdaters;
     * import java.lang.invoke.MethodHandles;
     * import java.util.function.IntUnaryOperator;
     *
     * import static java.lang.invoke.LazyUpdaters.Mode.PUBLISH_ONCE;
     *
     * class Values {
     *     private static final int[] entries = new int[8];
     *
     *     private static final IntUnaryOperator LAZY_ENTRY =
     *             LazyUpdaters.ofLocation(PUBLISH_ONCE, MethodHandles.lookup(),
     *                     IntUnaryOperator.class, Values.class.getName() + ".entries",
     *                     index -> index + 1);
     *
     *     static int entry(int index) { return LAZY_ENTRY.applyAsInt(index); }
     * }
     * }
     * An element of an instance array field instead requires an interface
     * with an owner and an index parameter.
     *
     * @param mode the updater mode
     * @param lookup the lookup used to authorize field and interface access
     * @param type the functional interface implemented by the computing
     *             function and returned updater
     * @param fieldSelector the instance field's simple name or the static
     *                      field's binary owner name followed by a dot and
     *                      the field name
     * @param computingFunction the computation for an uninitialized location
     * @param <T> the functional interface type
     * @return a lazy updater implementing {@code type}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if the selector or interface is invalid,
     *         the selected storage is inaccessible or unsuitable, the
     *         required access mode is unsupported, or the computing function
     *         is incompatible
     * @since 29
     * @see #ofLocation(Mode, MethodHandles.Lookup, Class, String)
     * @see #ofArray(Mode, Class, Object)
     */
    public static <T> T ofLocation(Mode mode,
                                   MethodHandles.Lookup lookup,
                                   Class<T> type,
                                   String fieldSelector,
                                   T computingFunction) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(lookup);
        Objects.requireNonNull(type);
        Objects.requireNonNull(fieldSelector);
        Objects.requireNonNull(computingFunction);
        return LazyUpdaterSupport.ofLocation(mode, lookup, type,
                fieldSelector, computingFunction);
    }

    /**
     * Returns a use-site updater for a named field or array-field element
     * using the specified mode. Its functional method accepts the
     * computing function first, followed by the location's coordinates. The
     * class documentation specifies the {@linkplain ##access-shapes access
     * shapes}, {@linkplain ##computing-functions computing-function rules},
     * {@linkplain ##uninitialized-locations reserved values}, and
     * {@linkplain ##updater-modes mode semantics}.
     *
     * <p>For example, an instance field can specialize the ready-made
     * {@link ReferenceFieldAccessor} and supply its computing function on each
     * access:
     * {@snippet lang=java :
     * import java.lang.invoke.LazyUpdaters;
     * import java.lang.invoke.MethodHandles;
     *
     * import static java.lang.invoke.LazyUpdaters.Mode.PUBLISH_ONCE;
     *
     * class Point {
     *     private final double x, y;
     *     private Double radius;
     *
     *     Point(double x, double y) { this.x = x; this.y = y; }
     *
     *     interface Radius extends LazyUpdaters.ReferenceFieldAccessor<Point, Double> { }
     *
     *     private static final Radius LAZY_RADIUS =
     *             LazyUpdaters.ofLocation(PUBLISH_ONCE, MethodHandles.lookup(),
     *                     Radius.class, "radius");
     *
     *     double radius() {
     *         return LAZY_RADIUS.getOrCompute(point -> Math.hypot(point.x, point.y), this);
     *     }
     * }
     * }
     * The concrete {@code Radius} subinterface fixes the receiver and value
     * types required by the factory.
     * As in the declaration-site form, boxing the result lets a computed
     * radius be positive zero, since {@code null} is the reserved value.
     *
     * <p>For a static field, the updater has no receiver. A primitive field
     * can use a ready-made accessor:
     * {@snippet lang=java :
     * import java.lang.invoke.LazyUpdaters;
     * import java.lang.invoke.MethodHandles;
     *
     * import static java.lang.invoke.LazyUpdaters.Mode.PUBLISH_ONCE;
     *
     * class Answer {
     *     private static int value;
     *
     *     private static final LazyUpdaters.StaticIntFieldAccessor LAZY_VALUE =
     *             LazyUpdaters.ofLocation(PUBLISH_ONCE, MethodHandles.lookup(),
     *                     LazyUpdaters.StaticIntFieldAccessor.class, Answer.class.getName() + ".value");
     *
     *     static int value() { return LAZY_VALUE.getOrCompute(() -> 42); }
     * }
     * }
     *
     * <p>For an element of an instance array field, the updater takes the
     * owner followed by the index:
     * {@snippet lang=java :
     * import java.lang.invoke.LazyUpdaters;
     * import java.lang.invoke.MethodHandles;
     *
     * import static java.lang.invoke.LazyUpdaters.Mode.PUBLISH_ONCE;
     *
     * class Names {
     *     private final String[] entries = new String[8];
     *
     *     interface Entry {
     *         interface Computer { String apply(Names owner, int index); }
     *         String getOrCompute(Computer computingFunction, Names owner, int index);
     *     }
     *
     *     private static final Entry LAZY_ENTRY =
     *             LazyUpdaters.ofLocation(PUBLISH_ONCE, MethodHandles.lookup(),
     *                     Entry.class, "entries");
     *
     *     String entry(int index) {
     *         return LAZY_ENTRY.getOrCompute((owner, i) -> "entry-" + i, this, index);
     *     }
     * }
     * }
     * This owner-plus-index shape has no ready-made accessor.
     * An element of a static array field can similarly use an index-only
     * accessor with the array owner's binary name in the selector.
     *
     * @param mode the updater mode
     * @param lookup the lookup used to authorize field and interface access
     * @param type the functional interface implemented by the returned updater
     * @param fieldSelector the instance field's simple name or the static
     *                      field's binary owner name followed by a dot and
     *                      the field name
     * @param <T> the functional interface type
     * @return a lazy updater implementing {@code type}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if the selector or interface is invalid,
     *         the selected storage is inaccessible or unsuitable, or the
     *         required access mode is unsupported
     * @since 29
     * @see #ofLocation(Mode, MethodHandles.Lookup, Class, String, Object)
     */
    public static <T> T ofLocation(Mode mode,
                                   MethodHandles.Lookup lookup,
                                   Class<T> type,
                                   String fieldSelector) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(lookup);
        Objects.requireNonNull(type);
        Objects.requireNonNull(fieldSelector);
        return LazyUpdaterSupport.ofLocation(mode, lookup, type, fieldSelector);
    }

    /**
     * Returns a declaration-site updater for an element of a directly
     * supplied array using the specified mode. The updater retains
     * {@code computingFunction}; it does not itself retain an array passed
     * to the updater, although the computing function may capture one. The
     * class documentation specifies the
     * {@linkplain ##access-shapes access shapes},
     * {@linkplain ##computing-functions computing-function rules},
     * {@linkplain ##uninitialized-locations reserved values}, and
     * {@linkplain ##updater-modes mode semantics}.
     *
     * <p>For example:
     * {@snippet lang=java :
     * import java.lang.invoke.LazyUpdaters;
     * import java.lang.invoke.LazyUpdaters.IntArrayAccessor;
     *
     * import static java.lang.invoke.LazyUpdaters.Mode.PUBLISH_ONCE;
     *
     * class Values {
     *     private final int[] entries = new int[8];
     *
     *     private static final IntArrayAccessor.Computer LAZY_ENTRY =
     *             LazyUpdaters.ofArray(PUBLISH_ONCE,
     *                     IntArrayAccessor.Computer.class,
     *                     (array, index) -> index + 1);
     *
     *     int entry(int index) { return LAZY_ENTRY.apply(entries, index); }
     * }
     * }
     * The ready-made {@link IntArrayAccessor.Computer} is specialized for
     * {@code int[]} elements and has the coordinate-only shape required by
     * this declaration-site form.
     *
     * <p>A custom interface can also select an element with two indices:
     * {@snippet lang=java :
     * import java.lang.invoke.LazyUpdaters;
     *
     * import static java.lang.invoke.LazyUpdaters.Mode.PUBLISH_ONCE;
     *
     * class Matrix {
     *     private final int[][] entries = new int[2][3];
     *
     *     interface Cell { int getOrCompute(int[][] array, int row, int column); }
     *
     *     private static final Cell LAZY_CELL =
     *             LazyUpdaters.ofArray(PUBLISH_ONCE,
     *                     Cell.class,
     *                     (array, row, column) -> row * array[row].length + column + 1);
     *
     *     int cell(int row, int column) {
     *         return LAZY_CELL.getOrCompute(entries, row, column);
     *     }
     * }
     * }
     *
     * @param mode the updater mode
     * @param type the functional interface implemented by the computing
     *             function and returned updater
     * @param computingFunction the computation for an uninitialized element
     * @param <T> the functional interface type
     * @return a lazy array-element updater implementing {@code type}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if the interface or array shape is
     *         invalid, the required access mode is unsupported, or the
     *         computing function is incompatible
     * @since 29
     * @see #ofArray(Mode, Class)
     */
    public static <T> T ofArray(Mode mode, Class<T> type, T computingFunction) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(type);
        Objects.requireNonNull(computingFunction);
        return LazyUpdaterSupport.ofArray(mode, type, computingFunction);
    }

    /**
     * Returns a use-site updater for elements of a directly supplied array
     * using the specified mode. Its functional method accepts the
     * computing function first, followed by the array and indices. The class
     * documentation specifies the {@linkplain ##access-shapes access shapes},
     * {@linkplain ##computing-functions computing-function rules},
     * {@linkplain ##uninitialized-locations reserved values}, and
     * {@linkplain ##updater-modes mode semantics}.
     *
     * <p>For example:
     * {@snippet lang=java :
     * import java.lang.invoke.LazyUpdaters;
     * import java.lang.invoke.LazyUpdaters.IntArrayAccessor;
     *
     * import static java.lang.invoke.LazyUpdaters.Mode.PUBLISH_ONCE;
     *
     * class Values {
     *     private final int[] entries = new int[8];
     *
     *     private static final IntArrayAccessor LAZY_ENTRY =
     *             LazyUpdaters.ofArray(PUBLISH_ONCE, IntArrayAccessor.class);
     *
     *     int entry(int index) {
     *         return LAZY_ENTRY.getOrCompute((array, i) -> i + 1, entries, index);
     *     }
     * }
     * }
     *
     * <p>The ready-made {@link IntArrayAccessor} covers an {@code int[]} element
     * with one index. A custom callback-first interface supports two indices:
     * {@snippet lang=java :
     * import java.lang.invoke.LazyUpdaters;
     *
     * import static java.lang.invoke.LazyUpdaters.Mode.PUBLISH_ONCE;
     *
     * class Matrix {
     *     private final int[][] entries = new int[2][3];
     *
     *     interface Cell {
     *
     *         interface Computer { int apply(int[][] array, int row, int column); }
     *
     *         int getOrCompute(Computer computingFunction, int[][] array, int row, int column);
     *     }
     *
     *     private static final Cell LAZY_CELL =
     *             LazyUpdaters.ofArray(PUBLISH_ONCE, Cell.class);
     *
     *     int cell(int row, int column) {
     *         return LAZY_CELL.getOrCompute(
     *                 (array, r, c) -> r * array[r].length + c + 1,
     *                 entries, row, column);
     *     }
     * }
     * }
     *
     * @param mode the updater mode
     * @param type the functional interface implemented by the returned updater
     * @param <T> the functional interface type
     * @return a lazy array-element updater implementing {@code type}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if either functional-interface shape
     *         or the array shape is invalid, or the required access mode is
     *         unsupported
     * @since 29
     * @see #ofArray(Mode, Class, Object)
     */
    public static <T> T ofArray(Mode mode, Class<T> type) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(type);
        return LazyUpdaterSupport.ofArray(mode, type);
    }

    // Ready-made interfaces

    /**
     * An updater for a named static {@code int} field. The computing
     * function is supplied at each access.
     *
     * @see LazyUpdaters##computing-functions Computing functions
     * @see LazyUpdaters##uninitialized-locations Uninitialized locations
     * @see LazyUpdaters##updater-modes Updater modes
     * @since 29
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    @FunctionalInterface
    public interface StaticIntFieldAccessor {
        /**
         * Returns the field value, computing it when uninitialized.
         *
         * @param computingFunction the computation for an uninitialized field
         * @return the field value
         */
        int getOrCompute(IntSupplier computingFunction);
    }

    /**
     * An updater for a named static {@code long} field. The computing
     * function is supplied at each access.
     *
     * @see LazyUpdaters##computing-functions Computing functions
     * @see LazyUpdaters##uninitialized-locations Uninitialized locations
     * @see LazyUpdaters##updater-modes Updater modes
     * @since 29
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    @FunctionalInterface
    public interface StaticLongFieldAccessor {

        /**
         * Returns the field value, computing it when uninitialized.
         *
         * @param computingFunction the computation for an uninitialized field
         * @return the field value
         */
        long getOrCompute(LongSupplier computingFunction);
    }

    /**
     * An updater for a named static reference field. The computing function
     * is supplied at each access. Clients declare a non-generic subinterface,
     * such as {@code NameAccessor extends StaticReferenceFieldAccessor<String>},
     * and pass its {@code Class} token to
     * {@link #ofLocation(Mode, MethodHandles.Lookup, Class, String)}. The
     * field's erased type must be a reference type assignable to {@code V}.
     * A parameterized or generic-array value type must match the field's
     * declared generic type. A computed {@code null} is rejected because it
     * denotes an uninitialized field.
     *
     * @param <V> the field value type or a reference supertype, subject to the
     *            generic-type matching rule above
     * @see LazyUpdaters##computing-functions Computing functions
     * @see LazyUpdaters##uninitialized-locations Uninitialized locations
     * @see LazyUpdaters##updater-modes Updater modes
     * @since 29
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    @FunctionalInterface
    public interface StaticReferenceFieldAccessor<V> {
        /**
         * Returns the field value, computing it when uninitialized.
         *
         * @param computingFunction the computation for an uninitialized field
         * @return the field value
         */
        V getOrCompute(Supplier<V> computingFunction);
    }

    /**
     * An updater for a named instance {@code int} field. The computing
     * function is supplied at each access. Clients declare a non-generic
     * subinterface, such as {@code CountAccessor extends
     * IntFieldAccessor<Counter>}, and pass that subinterface to
     * {@link #ofLocation(Mode, MethodHandles.Lookup, Class, String)}. The generic
     * receiver type must resolve to a class; the raw base interface cannot
     * identify a field owner.
     *
     * @param <R> the receiver type
     * @see LazyUpdaters##computing-functions Computing functions
     * @see LazyUpdaters##uninitialized-locations Uninitialized locations
     * @see LazyUpdaters##updater-modes Updater modes
     * @since 29
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    @FunctionalInterface
    public interface IntFieldAccessor<R> {

        /**
         * Returns the receiver's field value, computing it when uninitialized.
         *
         * @param computingFunction the computation for an uninitialized field
         * @param receiver the field receiver
         * @return the field value
         */
        int getOrCompute(ToIntFunction<R> computingFunction, R receiver);
    }

    /**
     * An updater for a named instance {@code long} field. The computing
     * function is supplied at each access. Clients declare a non-generic
     * subinterface, such as {@code CountAccessor extends
     * LongFieldAccessor<Counter>}, and pass that subinterface to
     * {@link #ofLocation(Mode, MethodHandles.Lookup, Class, String)}. The generic
     * receiver type must resolve to a class; the raw base interface cannot
     * identify a field owner.
     *
     * @param <R> the receiver type
     * @see LazyUpdaters##computing-functions Computing functions
     * @see LazyUpdaters##uninitialized-locations Uninitialized locations
     * @see LazyUpdaters##updater-modes Updater modes
     * @since 29
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    @FunctionalInterface
    public interface LongFieldAccessor<R> {

        /**
         * Returns the receiver's field value, computing it when uninitialized.
         *
         * @param computingFunction the computation for an uninitialized field
         * @param receiver the field receiver
         * @return the field value
         */
        long getOrCompute(ToLongFunction<R> computingFunction, R receiver);
    }

    /**
     * An updater for a named instance reference field. The computing function
     * is supplied at each access. Clients declare a non-generic subinterface,
     * such as {@code NameAccessor extends ReferenceFieldAccessor<Holder, String>},
     * and pass its {@code Class} token to
     * {@link #ofLocation(Mode, MethodHandles.Lookup, Class, String)}. The
     * receiver must resolve to a specific owner class and the value must
     * resolve to a reference type, which may be parameterized. The field's
     * erased type must be assignable to {@code V}; a parameterized or
     * generic-array value type must match the field's declared generic type.
     * A computed {@code null} is rejected because it denotes an uninitialized
     * field.
     *
     * @param <R> the receiver type
     * @param <V> the field value type or a reference supertype, subject to the
     *            generic-type matching rule above
     * @see LazyUpdaters##computing-functions Computing functions
     * @see LazyUpdaters##uninitialized-locations Uninitialized locations
     * @see LazyUpdaters##updater-modes Updater modes
     * @since 29
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    @FunctionalInterface
    public interface ReferenceFieldAccessor<R, V> {
        /**
         * Returns the receiver's field value, computing it when uninitialized.
         *
         * @param computingFunction the computation for an uninitialized field
         * @param receiver the field receiver
         * @return the field value
         */
        V getOrCompute(Function<R, V> computingFunction, R receiver);
    }


    /**
     * An updater for elements of a directly supplied {@code int[]}.
     * The computing function is supplied at each access.
     *
     * @see LazyUpdaters##computing-functions Computing functions
     * @see LazyUpdaters##uninitialized-locations Uninitialized locations
     * @see LazyUpdaters##updater-modes Updater modes
     * @since 29
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    @FunctionalInterface
    public interface IntArrayAccessor {
        /**
         * Computes an {@code int} element from its array and index.
         *
         * @since 29
         */
        @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
        @FunctionalInterface
        interface Computer {
            /**
             * Computes an element value.
             *
             * @param array the array
             * @param index the selected index
             * @return the computed value
             */
            int apply(int[] array, int index);
        }

        /**
         * Returns the selected element, computing it when uninitialized.
         *
         * @param computingFunction the computation for an uninitialized element
         * @param array the array
         * @param index the selected index
         * @return the selected value
         */
        int getOrCompute(Computer computingFunction, int[] array, int index);
    }

    /**
     * An updater for elements of a directly supplied {@code long[]}.
     * The computing function is supplied at each access.
     *
     * @see LazyUpdaters##computing-functions Computing functions
     * @see LazyUpdaters##uninitialized-locations Uninitialized locations
     * @see LazyUpdaters##updater-modes Updater modes
     * @since 29
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    @FunctionalInterface
    public interface LongArrayAccessor {
        /**
         * Computes a {@code long} element from its array and index.
         *
         * @since 29
         */
        @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
        @FunctionalInterface
        interface Computer {
            /**
             * Computes an element value.
             *
             * @param array the array
             * @param index the selected index
             * @return the computed value
             */
            long apply(long[] array, int index);
        }

        /**
         * Returns the selected element, computing it when uninitialized.
         *
         * @param computingFunction the computation for an uninitialized element
         * @param array the array
         * @param index the selected index
         * @return the selected value
         */
        long getOrCompute(Computer computingFunction, long[] array, int index);
    }

    /**
     * An updater for elements of a directly supplied {@code E[]} array. An
     * element may itself be an array. The computing function is supplied at
     * each access. Clients declare a non-generic subinterface, such as
     * {@code Names extends ReferenceArrayAccessor<String>}, and pass its
     * {@code Class} token to {@link #ofArray(Mode, Class)}. A computed
     * {@code null} is rejected because it denotes an uninitialized element.
     *
     * @param <E> the array element type
     * @see LazyUpdaters##computing-functions Computing functions
     * @see LazyUpdaters##uninitialized-locations Uninitialized locations
     * @see LazyUpdaters##updater-modes Updater modes
     * @since 29
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    @FunctionalInterface
    public interface ReferenceArrayAccessor<E> {
        /**
         * Computes a reference element from its array and index.
         *
         * @param <E> the array element type
         * @since 29
         */
        @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
        @FunctionalInterface
        interface Computer<E> {
            /**
             * Computes an element value.
             *
             * @param array the array
             * @param index the selected index
             * @return the computed value
             */
            E apply(E[] array, int index);
        }

        /**
         * Returns the selected element, computing it when uninitialized.
         *
         * @param computingFunction the computation for an uninitialized element
         * @param array the array
         * @param index the selected index
         * @return the selected value
         */
        E getOrCompute(Computer<E> computingFunction, E[] array, int index);
    }
}
