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

package java.lang.invoke;

import java.lang.foreign.MemoryLayout;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

import jdk.internal.invoke.LazyBuilderImpl;
import jdk.internal.invoke.LazyUpdaterSupport;
import jdk.internal.javac.PreviewFeature;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;

/**
 * Factory methods for lazy updaters.
 *
 * <h2 id="updater-factories">Updaters</h2>
 * Lazy updaters use caller-owned storage. An invocation returns a non-default
 * value already in the selected location without computing. The builder and
 * convenience factories do not read that storage or invoke the computing
 * function when an updater is created.
 * <p>
 * {@link #of(Class)} starts an immutable builder. Its computation
 * shape is a functional-interface {@code Class} token. A builder defaults to
 * {@link Mode#PUBLISH_ONCE} and a public lookup. A
 * {@link Builder#mode(Mode) mode} call changes the mode;
 * {@link Builder#lookup(MethodHandles.Lookup) lookup} supplies
 * field-access authority. The builder selects one of three storage forms:
 * <ul>
 * <li>No explicit location selects an element of an array supplied directly
 *     to the updater.</li>
 * <li>{@link Builder#location(Class, String) location(ownerClass,
 *     fieldName)} selects a named static or instance field, or an element of
 *     an array held in that field.</li>
 * <li>{@code location(layout, pathElements)} selects a value in a supplied
 *     memory segment through a structured {@link MemoryLayout}.</li>
 * </ul>
 * The {@link Builder#build(Object) build} terminal operation retains a
 * computing function supplied when the updater is created. The
 * {@link Builder#buildUseSite(Class) buildUseSite} terminal operation
 * creates an updater whose functional method receives a computing function
 * on each access. The
 * {@linkplain #ofStaticField(Class, String, Supplier) static-field},
 * {@linkplain #ofField(Class, String, Function) instance-field}, and
 * {@linkplain #ofArray(Class, ReferenceArrayAccessor)
 * reference-array} conveniences use {@code PUBLISH_ONCE} with fixed,
 * common shapes. {@link Mode#SERIALIZED} retries after failure.
 *
 * <h3 id="access-shapes">Locations and access shapes</h3>
 * The computation shape's single abstract method receives the storage
 * coordinates and returns a candidate value. The use-site accessor's
 * functional method has one leading computing-function parameter, followed
 * by those coordinates, and returns the selected value. The storage
 * coordinates depend on the selected location:
 * <ul>
 * <li>For a named instance field, the first coordinate is a receiver.
 *     A named static field has no receiver. With no following indices,
 *     the field itself is selected. For an array-valued field, one or more
 *     trailing {@code int} coordinates select an element.</li>
 * <li>For a directly supplied array, the first coordinate has a concrete
 *     array type and is followed by one or more {@code int} indices. This
 *     form always selects an element, not the supplied array reference.</li>
 * <li>For a memory segment, the first coordinate is a
 *     {@link java.lang.foreign.MemorySegment}, followed by a {@code long}
 *     base offset and one {@code long} index for each open sequence element
 *     in the selected layout path, in path order. The path must select a
 *     value layout. A dereference path element is not supported.</li>
 * </ul>
 * For Java arrays, the number of indices must not exceed the array's rank
 * and is subject to ordinary Java and JVM method-parameter limits. Indices
 * address dimensions in order, from the outermost array to successively
 * inner arrays; neither permutation nor index expressions are inferred. A
 * selected value may itself be an array: one index into {@code int[][][]}
 * selects an {@code int[][]}, while three indices select an {@code int}.
 * An array field may be {@code final} when an element is selected; a field
 * selected as the location must be non-final. An array field's reference and
 * any intermediate array references must not be replaced while an updater
 * is in use.
 * <p>
 * A field is selected by its {@code Class} token and a simple field name.
 * The token identifies the class in its defining loader, so the same
 * binary name in a different loader does not select that class. A field
 * declared by the named owner takes precedence. Otherwise, the nearest
 * superclass declaration is selected unless an interface also declares the
 * name. With no superclass declaration, exactly one interface declaration
 * is accepted. Conflicting superclass and interface declarations, or
 * multiple interface declarations, are rejected. This search considers
 * declarations even when they are not inherited in Java source; lookup
 * access is checked separately. The selected field must be static exactly
 * when the shape has no receiver. A static field can be selected through
 * its declaring class or interface.
 * <p>
 * For fields and arrays, a functional method must have a non-void result
 * compatible with the selected type. It may return that type or a reference
 * type to which the selected value, boxed if necessary, can be assigned.
 * The explicit owner token makes a raw generic functional interface usable
 * for a named field even if its receiver erases to {@code Object}; erased
 * value type arguments remain the caller's responsibility. A concrete
 * subinterface can retain source-level parameter and result types; a
 * specialized ready-made reference-field use-site accessor also validates
 * the field's generic value type. Direct-array declaration-site shapes must
 * identify a concrete array coordinate. A specialized use-site accessor can identify
 * that array even if its callback shape has erased type arguments.
 * Segment coordinates and result carrier must match the selected layout's
 * var-handle descriptor exactly.
 * <p>
 * A number of {@linkplain ##ready-made-interfaces ready-made accessor interfaces}
 * are provided for common coordinates. The supplied accessor interface and,
 * in a use-site form, its computing-function interface must not be sealed or
 * hidden. Inherited abstract methods with the same erased parameter types
 * and covariant return types, as well as default methods and
 * compiler-generated bridges, are supported. An interface requiring
 * multiple abstract methods with different erased parameter types is not.
 *
 * <h3 id="computing-functions">Computing functions</h3>
 * In a declaration-site form, the computing function implements the
 * builder's computation shape, receives the storage coordinates in their
 * original order, and is retained by the updater. In a use-site form, the
 * updater receives a computing function on each invocation. Its functional
 * method must have the same erased parameter and return types as the
 * builder's computation shape; the two function interfaces need not have
 * the same name or nominal type. A reference parameter of a field or array
 * computing function may be a supertype of its corresponding coordinate
 * type. Its result may be a subtype of reference storage or use boxing,
 * unboxing, or widening primitive conversion; a broader reference result
 * is checked against the storage type when computed. An incompatible result
 * fails before publication. As with ordinary array stores, a runtime array
 * whose component type is narrower than the accessor's array type may
 * reject a computed value with {@link ArrayStoreException}. Segment
 * computing functions use the exact carrier and coordinates of the
 * selected layout.
 * <p>
 * A use-site updater does not retain a computing function. Every invocation
 * rejects a {@code null} computing function, even if the location is already
 * initialized. A non-null function is invoked only when a computation is
 * needed. If an invocation fails, a later invocation may supply a different
 * function. Functions used for the same location should be interchangeable,
 * since the updater mode determines which successful result is stored.
 *
 * <h4 id="ready-made-interfaces">Ready-made interfaces</h4>
 * These interfaces supply the computing function at the use site. Field
 * accessors select a named field through
 * {@link Builder#location(Class, String) location}; direct
 * array accessors use the builder with no explicit location.
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
 * <th scope="col">Type fixing for the use-site token</th>
 * </tr>
 * </thead>
 * <tbody>
 * <tr>
 * <th scope="row">{@link StaticIntFieldAccessor.Computer
 *     StaticIntFieldAccessor.Computer}</th>
 * <td>Named static {@code int} field</td>
 * <td>None</td>
 * <td>{@code IntSupplier}<br>{@code () -> 42}</td>
 * <td>No; use {@code StaticIntFieldAccessor.Computer.class}</td>
 * </tr>
 * <tr>
 * <th scope="row">{@link StaticLongFieldAccessor.Computer
 *     StaticLongFieldAccessor.Computer}</th>
 * <td>Named static {@code long} field</td>
 * <td>None</td>
 * <td>{@code LongSupplier}<br>{@code () -> 42L}</td>
 * <td>No; use {@code StaticLongFieldAccessor.Computer.class}</td>
 * </tr>
 * <tr>
 * <th scope="row">{@link StaticReferenceFieldAccessor.Computer
 *     StaticReferenceFieldAccessor.Computer}</th>
 * <td>Named static reference field</td>
 * <td>None</td>
 * <td>{@code Supplier<V>}<br>{@code () -> "Ada"}</td>
 * <td>Recommended; {@code interface StaticName extends
 *     StaticReferenceFieldAccessor.Computer<String> { }}</td>
 * </tr>
 * <tr>
 * <th scope="row">{@link IntFieldAccessor.Computer
 *     IntFieldAccessor.Computer}</th>
 * <td>Named instance {@code int} field</td>
 * <td>{@code R} receiver</td>
 * <td>{@code ToIntFunction<R>}<br>
 *     {@code holder -> Math.max(1, holder.seed)}</td>
 * <td>Recommended; {@code interface Count extends
 *     IntFieldAccessor.Computer<Holder> { }}</td>
 * </tr>
 * <tr>
 * <th scope="row">{@link LongFieldAccessor.Computer
 *     LongFieldAccessor.Computer}</th>
 * <td>Named instance {@code long} field</td>
 * <td>{@code R} receiver</td>
 * <td>{@code ToLongFunction<R>}<br>
 *     {@code holder -> Math.max(1L, 100L * holder.seed)}</td>
 * <td>Recommended; {@code interface Total extends
 *     LongFieldAccessor.Computer<Holder> { }}</td>
 * </tr>
 * <tr>
 * <th scope="row">{@link ReferenceFieldAccessor.Computer
 *     ReferenceFieldAccessor.Computer}</th>
 * <td>Named instance reference field</td>
 * <td>{@code R} receiver</td>
 * <td>{@code Function<R, V>}<br>
 *     {@code holder -> "name-" + holder.seed}</td>
 * <td>Recommended; {@code interface Name extends
 *     ReferenceFieldAccessor.Computer<Holder, String> { }}</td>
 * </tr>
 * <tr>
 * <th scope="row">{@link IntArrayAccessor.Computer
 *     IntArrayAccessor.Computer}</th>
 * <td>Element of a directly supplied {@code int[]}</td>
 * <td>{@code int[] array, int index}</td>
 * <td>{@code IntArrayAccessor}<br>
 *     {@code (array, index) -> array.length - index}</td>
 * <td>No; use {@code IntArrayAccessor.Computer.class}</td>
 * </tr>
 * <tr>
 * <th scope="row">{@link LongArrayAccessor.Computer
 *     LongArrayAccessor.Computer}</th>
 * <td>Element of a directly supplied {@code long[]}</td>
 * <td>{@code long[] array, int index}</td>
 * <td>{@code LongArrayAccessor}<br>
 *     {@code (array, index) -> (long) array.length - index}</td>
 * <td>No; use {@code LongArrayAccessor.Computer.class}</td>
 * </tr>
 * <tr>
 * <th scope="row">{@link ReferenceArrayAccessor.Computer
 *     ReferenceArrayAccessor.Computer}</th>
 * <td>Element of a directly supplied {@code E[]}</td>
 * <td>{@code E[] array, int index}</td>
 * <td>{@code ReferenceArrayAccessor<E>}<br>
 *     {@code (array, index) -> "name-" + (array.length - index)}</td>
 * <td>Yes; {@code interface Names extends
 *     ReferenceArrayAccessor.Computer<String> { }}</td>
 * </tr>
 * </tbody>
 * </table>
 * A named-field builder has an explicit owner, so a raw generic field
 * accessor token is permitted, though type fixing is recommended for
 * source type safety. For example, pass {@code Count.class} to
 * {@code buildUseSite} when using the {@code Count} interface above.
 * A generic direct-array accessor token needs a concrete subinterface to
 * identify its array type. An {@code E}
 * that is itself an array type selects an array-valued element with one index.
 * A custom accessor interface is required for access with two or more indices,
 * for array-field elements, and for primitive value types other than
 * {@code int} and {@code long}.
 * <p>
 * A specialized reference field accessor token selects only a reference
 * field whose declared type is assignable to the accessor's value type. If
 * that value type is parameterized, or is a generic array type, it must
 * match the field's declared generic type. This check does not resolve type
 * variables in a generic field owner. A raw accessor token, including one
 * used by a convenience factory, can also select a primitive field when its
 * boxed value is compatible with the result type. A raw or broader reference
 * value type is checked only at its erased type, so its computing function
 * must respect the field's type arguments.
 *
 * <h3 id="uninitialized-locations">Uninitialized locations</h3>
 * The Java default value of a selected field or array element, or the
 * zero-filled value of a selected segment location, means uninitialized:
 * {@code null} for references, {@code false} for {@code boolean}, zero for
 * integral types, and positive zero for floating-point types. A computation
 * producing this reserved value throws {@code IllegalArgumentException}
 * without publishing it. Negative floating-point zero and NaN are permitted.
 * For an address layout, the zero native address represented by
 * {@link java.lang.foreign.MemorySegment#NULL MemorySegment.NULL} is reserved;
 * a {@code null} candidate is also invalid.
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
 * <h3 id="updater-modes">Updater modes</h3>
 * In {@link Mode#PUBLISH_ONCE} mode, an invocation reads the selected location
 * with acquire semantics. If it is uninitialized, the updater computes a
 * candidate and attempts to install it atomically with release effects.
 * Computations may race, but every invocation that returns normally returns
 * the value installed in the location, including an invocation whose result
 * was discarded.
 * For an address-layout segment location, only the native address is stored.
 * A successful {@code PUBLISH_ONCE} invocation rereads that address and
 * returns a layout-materialized segment. The returned segment need not be
 * the candidate object and need not have its scope or size, but successful
 * accesses observe the same installed native address and return segments
 * equal by {@code MemorySegment.equals}. The caller must keep the pointed-to
 * memory alive independently of the updater.
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
 * {@link Mode#SERIALIZED} is available for fields and Java arrays,
 * but not for memory-segment locations, whose distinct views may alias the
 * same bytes. In this mode, only one thread at a time invokes the
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
 * bound. For an address-layout segment location, a computing invocation
 * returns its own candidate segment after storing its native address; a
 * later read materializes a segment from the stored address.
 *
 * <h3 id="access-and-ownership">Access and storage ownership</h3>
 * The caller must safely publish the receiver, array, or segment and any preexisting
 * value to other threads. The selected storage must be reserved for lazy use:
 * it must not be reset or otherwise modified directly while an updater is in
 * use. All updaters sharing a location must use the same mode, and their
 * computing functions should be interchangeable. Other writes can defeat the
 * mode's guarantees. {@code SERIALIZED} updaters share coordination for a
 * physical location even if they use different computing functions. An
 * updater does not add a holder to each receiver, array element, or segment
 * location. Apart
 * from objects captured by a declaration-site computing function, it does
 * not strongly retain receivers or arrays; {@code SERIALIZED} coordination
 * keys are weak. Publication of a computed reference does not make its later
 * mutations thread-safe, and accesses to different locations have no total
 * order.
 * <p>
 * For a named field, the lookup must authorize access to the functional
 * interfaces, the selected owner and field, and any selected array type.
 * A receiver restriction imposed by the lookup is preserved. Accessing a
 * static field through the updater initializes its declaring class if
 * necessary, following normal var-handle semantics. A direct array needs
 * no caller-provided lookup; its array coordinate and indices identify the
 * element. Non-public direct-array functional interfaces and component types
 * are supported. An explicit {@code lookup} for a direct array or
 * segment is rejected by a terminal builder operation instead of being
 * silently ignored. A null receiver, array, or intermediate array, or an
 * out-of-bounds index, fails before computation with the corresponding
 * {@code NullPointerException} or {@code ArrayIndexOutOfBoundsException}.
 * The Java array-reference path is resolved once per invocation.
 * When an array is reached through a field, {@code PUBLISH_ONCE} and
 * {@code SERIALIZED} read that field with acquire effects before accessing
 * an element; {@code PLAIN} reads it with plain effects.
 * <p>
 * For a segment location, the layout path is resolved when the updater is
 * built. Each invocation performs the memory segment's lifetime, thread,
 * bounds, alignment, and read-only checks through its var handle. The
 * selected layout must support the access modes required by the updater
 * mode; {@code PUBLISH_ONCE} requires atomic exchange support. An
 * unaligned value layout may therefore work with {@code PLAIN} but not with
 * {@code PUBLISH_ONCE}. The updater does not retain the segment supplied on
 * invocation.
 *
 * <h3 id="lazy-caches">Lazy caches</h3>
 * A cache can reserve a field or array elements for lazy values and share one
 * static updater across all instances. Each getter supplies the storage
 * coordinates and, for a use-site updater, its computing function. The
 * updater invokes the computation when the selected location is
 * uninitialized, then returns the value stored there.
 * <p>
 * A reference-valued cache can use {@link #ofField(Class, String,
 * Function)}:
 * {@snippet lang = java:
 * import java.lang.invoke.Lazy;
 * import java.util.function.Supplier;
 *
 * class StringCache {
 *
 *     private static final Lazy.ReferenceFieldAccessor<StringCache, String> UPDATER =
 *             Lazy.ofField(StringCache.class, "value",
 *                     cache -> cache.valueComputer.get());
 *
 *     private final Supplier<String> valueComputer;
 *     private String value;
 *
 *     StringCache(Supplier<String> valueComputer) {
 *         this.valueComputer = valueComputer;
 *     }
 *
 *     String get() {
 *         return UPDATER.getOrCompute(this);
 *     }
 * }
 *}
 * <p>
 * For an {@code int} value, a specialized {@link IntFieldAccessor} gives the
 * getter an {@code int} result. The builder uses the class's lookup to access
 * its private field:
 * {@snippet lang = java:
 * import java.lang.invoke.Lazy;
 * import java.lang.invoke.MethodHandles;
 * import java.util.function.IntSupplier;
 *
 * class IntCache {
 *
 *     // Declare a concrete subinterface
 *     private interface ValueAccessor extends Lazy.IntFieldAccessor<IntCache> {}
 *
 *     private static final ValueAccessor UPDATER =
 *             Lazy.of(ValueAccessor.class)
 *                     .lookup(MethodHandles.lookup())
 *                     .location(IntCache.class, "value")
 *                     .build(cache -> cache.valueComputer.getAsInt());
 *
 *     private final IntSupplier valueComputer;
 *     private int value;
 *
 *     IntCache(IntSupplier valueComputer) {
 *         this.valueComputer = valueComputer;
 *     }
 *
 *     int getAsInt() {
 *         return UPDATER.getOrCompute(this);
 *     }
 * }
 *}
 * <p>
 * A rank-two {@code int[][]} can cache values independently in its elements.
 * The constructor supplies its dimensions and a function of column and row;
 * the static updater selects an element on each access:
 * {@snippet lang = java:
 * import java.lang.invoke.Lazy;
 * import java.lang.invoke.MethodHandles;
 * import java.util.function.IntBinaryOperator;
 *
 * class IntMatrixCache {
 *
 *     private interface ElementAccessor {
 *         int getOrCompute(IntMatrixCache cache, int column, int row);
 *     }
 *
 *     private static final ElementAccessor UPDATER =
 *             Lazy.of(ElementAccessor.class)
 *                     .lookup(MethodHandles.lookup())
 *                     .location(IntMatrixCache.class, "values")
 *                     .build((cache, column, row) ->
 *                             cache.elementComputer.applyAsInt(column, row));
 *
 *     private final int[][] values;
 *     private final IntBinaryOperator elementComputer;
 *
 *     IntMatrixCache(int columnCount, int rowCount,
 *                    IntBinaryOperator elementComputer) {
 *         this.values = new int[columnCount][rowCount];
 *         this.elementComputer = elementComputer;
 *     }
 *
 *     int get(int column, int row) {
 *         return UPDATER.getOrCompute(this, column, row);
 *     }
 * }
 *}
 * <p>
 * A variant supplies the {@code int[][]} directly to a shared use-site
 * updater. Each cache stores a computing function for its own elements; no
 * field location or lookup is needed:
 * {@snippet lang = java:
 * import java.lang.invoke.Lazy;
 * import java.util.function.IntBinaryOperator;
 *
 * class DirectIntMatrixCache {
 *
 *     private interface ElementComputer {
 *         int compute(int[][] values, int column, int row);
 *     }
 *
 *     private interface ElementAccessor {
 *         int getOrCompute(ElementComputer computer, int[][] values,
 *                          int column, int row);
 *     }
 *
 *     private static final ElementAccessor UPDATER =
 *             Lazy.of(ElementComputer.class)
 *                     .buildUseSite(ElementAccessor.class);
 *
 *     private final int[][] values;
 *     private final ElementComputer computer;
 *
 *     DirectIntMatrixCache(int columnCount, int rowCount,
 *                          IntBinaryOperator elementComputer) {
 *         this.values = new int[columnCount][rowCount];
 *         this.computer = (array, column, row) ->
 *                 elementComputer.applyAsInt(column, row);
 *     }
 *
 *     int get(int column, int row) {
 *         // The `computer` is provided at use-site rather then at declaration.
 *         return UPDATER.getOrCompute(computer, values, column, row);
 *     }
 * }
 *}
 * All four examples use {@link Mode#PUBLISH_ONCE}. Concurrent calls may
 * run the computation more than once, while each getter returns the value
 * installed in its selected location. Those locations must remain reserved
 * for lazy use. The {@code String} supplier must return a non-null value;
 * the {@code int} supplier and both matrix element computers must return
 * nonzero values. {@code null} and zero are the
 * {@linkplain ##uninitialized-locations uninitialized values}.
 *
 * @implNote In updater {@code PUBLISH_ONCE} mode, this implementation
 *           installs a candidate with
 *           {@link VarHandle#compareAndExchangeRelease compareAndExchangeRelease}.
 *           The exchange's witness has plain-read effects, so a losing
 *           invocation performs a separate acquire read before returning
 *           the winner.
 *           <p>
 *           In updater {@code SERIALIZED} mode, a receiver or array element's
 *           coordination state is created when its uninitialized storage is
 *           accessed. Cleared weak-key entries are removed on a later
 *           uninitialized access; their metadata can remain until then or
 *           until class unloading.
 *           <p>
 *           An updater's published result is not replaced through a
 *           {@code PUBLISH_ONCE} or {@code SERIALIZED} updater. This may permit
 *           an implementation to constant-fold repeated accesses when it can
 *           determine a stable path to the storage and its result, perhaps
 *           from a {@code static final} field. Whether and when this
 *           optimization occurs is implementation-dependent. Eliding a
 *           storage read must preserve the applicable
 *           {@linkplain ##updater-modes updater mode} guarantee.
 *
 * @since 28
 * @see Supplier
 * @jls 17.4.5 Happens-before Order
 */
@PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
public final class Lazy {

    private Lazy() { }

    /**
     * The modes supported by lazy updaters.
     *
     * @see Lazy##updater-modes Updater modes
     * @since 28
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
         * @see Lazy##updater-modes Updater modes
         */
        PLAIN
    }


    /**
     * Returns a lazy updater for a static field accessible to the caller.
     * This convenience method uses {@link Mode#PUBLISH_ONCE} and the
     * supplied field as storage. Its result is a
     * {@link StaticReferenceFieldAccessor} whose {@code getOrCompute()} method
     * accesses that field; the computing function is invoked only when the
     * field has its reserved Java default value. The field must be static and
     * non-final. Access is checked using a
     * lookup for the invoking class. If that lookup cannot resolve {@code owner}
     * by name to the supplied {@code Class}, it is scoped to {@code owner}
     * using {@link MethodHandles.Lookup#in(Class) Lookup.in} to preserve the
     * supplied class's loader identity. A caller may select its own private
     * static field or one in a nestmate. Access to an unrelated private field
     * is denied. A caller needing a different mode or explicit lookup
     * authority can use {@link #of(Class)} with the desired
     * {@link Builder#mode(Mode) mode},
     * {@link Builder#lookup(MethodHandles.Lookup) lookup}, and
     * {@link Builder#location(Class, String) field location}.
     * This method is caller-sensitive: a helper that invokes it supplies the
     * helper's access privileges. Once the updater is created, calling its
     * {@code getOrCompute} method does not repeat the caller access check.
     * A method-handle invocation may use a VM-generated nestmate as its caller;
     * that class need not inherit protected access held by the class that
     * created the method handle. For such access, use an updater builder with
     * {@code lookup} and {@code location}.
     *
     * <p>The supplier may produce a subtype of {@code T}. The type parameter
     * {@code T} is not available at run time. The caller must ensure that
     * {@code computing} produces values compatible with the field and that
     * any preexisting field value can be used as {@code T}.
     * Incompatible computed values fail before publication, as described in
     * {@linkplain ##computing-functions Computing functions}.
     *
     * @implSpec This method obtains a lookup for its caller and uses
     *           {@code callerLookup.in(owner)} if the caller lookup cannot
     *           resolve {@code owner} to the supplied {@code Class}. It adapts
     *           {@code computing} to a {@link StaticReferenceFieldAccessor} and
     *           delegates to a builder equivalent to
     *           {@code of(StaticReferenceFieldAccessor.class)
     *           .lookup(selectedLookup).location(owner, name)
     *           .build(adaptedComputingFunction)}.
     *           The returned updater is viewed as a
     *           {@code StaticReferenceFieldAccessor<T>}.
     *
     * @param owner the type through which the static field is selected
     * @param name the simple name of the field
     * @param computing the function that computes a non-default field value
     * @param <T> the result type expected by the caller
     * @return an accessor backed by the selected static field
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalCallerException if there is no caller frame
     * @throws IllegalArgumentException if {@code name} is empty or contains a
     *         dot, the field cannot be accessed, is not a suitable mutable
     *         static location, or the required access mode is unsupported
     * @see #of(Class)
     * @since 28
     */
    @CallerSensitive
    public static <T> StaticReferenceFieldAccessor<T> ofStaticField(Class<?> owner,
                                                                    String name,
                                                                    Supplier<? extends T> computing) {
        // Keep caller identification in this entry point. A helper accepting
        // a caller Class could be invoked reflectively with a forged argument.
        Class<?> caller = Reflection.getCallerClass();
        Objects.requireNonNull(owner);
        Objects.requireNonNull(name);
        Objects.requireNonNull(computing);
        if (name.isEmpty() || name.indexOf('.') >= 0) {
            throw new IllegalArgumentException("Expected a simple field name: " + name);
        }
        if (caller == null) {
            throw new IllegalCallerException("no caller frame");
        }
        MethodHandles.Lookup lookup = new MethodHandles.Lookup(caller);
        try {
            if (lookup.findClass(owner.getName()) != owner) {
                lookup = lookup.in(owner);
            }
        } catch (ClassNotFoundException | IllegalAccessException e) {
            lookup = lookup.in(owner);
        }
        @SuppressWarnings({"rawtypes", "unchecked"})
        StaticReferenceFieldAccessor<T> updater = (StaticReferenceFieldAccessor<T>)
                of(StaticReferenceFieldAccessor.class)
                .lookup(lookup)
                .location(owner, name)
                .build((StaticReferenceFieldAccessor) computing::get);
        return updater;
    }

    /**
     * Returns a lazy updater for an instance field accessible to the caller.
     * This convenience method uses {@link Mode#PUBLISH_ONCE} and the
     * supplied field as storage. Its result is a
     * {@link ReferenceFieldAccessor} whose {@code getOrCompute(receiver)}
     * method accesses the field of its receiver, invoking {@code computing}
     * only when that field has its reserved Java default value. The field
     * must be non-static and non-final. Each receiver has an independent
     * stored value.
     *
     * <p>Access is checked using a lookup for the invoking class. If that
     * lookup cannot resolve {@code owner} by name to the supplied
     * {@code Class}, it is scoped to {@code owner} using
     * {@link MethodHandles.Lookup#in(Class) Lookup.in} to preserve the
     * supplied class's loader identity. A caller may select its own private
     * field or one in a nestmate. Access to an unrelated private field is
     * denied. This method is caller-sensitive: a helper that invokes it
     * supplies the helper's access privileges. Once the updater is created,
     * calling its {@code getOrCompute} method does not repeat the caller access
     * check. A caller needing another mode or explicit lookup authority can
     * use {@link #of(Class)} with the desired mode, lookup, and field
     * location. A specialized functional interface can preserve the receiver
     * and result types at the source level.
     * A method-handle invocation may use a VM-generated nestmate as its
     * caller; that class need not inherit protected access held by the class
     * that created the method handle. For such access, use an updater builder
     * with {@code lookup} and {@code location}.
     *
     * <p>For example:
     * {@snippet lang=java :
     * import java.lang.invoke.Lazy;
     * import java.lang.invoke.Lazy.ReferenceFieldAccessor;
     *
     * class Point {
     *
     *     private static final ReferenceFieldAccessor<Point, Double> RADIUS =
     *             Lazy.ofField(Point.class, "radius",
     *                     point -> Math.hypot(point.x, point.y));
     *
     *     private final double x, y;
     *     private Double radius;
     *
     *     Point(double x, double y) { this.x = x; this.y = y; }
     *
     *     double radius() { return RADIUS.getOrCompute(this); }
     * }
     * }
     *
     * <p>The computing function may accept a supertype of {@code R} and
     * produce a subtype of {@code T}. The type parameter {@code T} is not
     * available at run time. The caller must ensure that {@code computing}
     * produces values compatible with the field and that any preexisting
     * field value can be used as {@code T}.
     * A primitive field value is returned boxed. Incompatible computed
     * values fail before publication, as described in
     * {@linkplain ##computing-functions Computing functions}.
     *
     * @implSpec This method obtains a lookup for its caller, retaining it
     *           when it resolves {@code owner} to the supplied {@code Class}
     *           and otherwise using {@code callerLookup.in(owner)}. It
     *           adapts {@code computing} to a {@link ReferenceFieldAccessor}
     *           and delegates to an
     *           {@code of(ReferenceFieldAccessor.class)} builder
     *           configured with that lookup and the supplied field, using
     *           {@link Mode#PUBLISH_ONCE}. The returned updater is
     *           viewed as a {@code ReferenceFieldAccessor<R, T>}.
     *
     * @param owner the type through which the instance field is selected
     * @param name the simple name of the field
     * @param computing the function that computes a non-default field value
     * @param <R> the receiver type
     * @param <T> the result type expected by the caller
     * @return an accessor backed by the selected instance field
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalCallerException if there is no caller frame
     * @throws IllegalArgumentException if {@code name} is empty or contains a
     *         dot, the field cannot be accessed, is not a suitable mutable
     *         instance location, or the required access mode is unsupported
     * @see #ofStaticField(Class, String, Supplier)
     * @see #of(Class)
     * @since 28
     */
    @CallerSensitive
    public static <R, T> ReferenceFieldAccessor<R, T> ofField(Class<R> owner,
                                                              String name,
                                                              Function<? super R, ? extends T> computing) {
        // Keep caller identification in this entry point for the same reason
        // as in ofStaticField.
        Class<?> caller = Reflection.getCallerClass();
        Objects.requireNonNull(owner);
        Objects.requireNonNull(name);
        Objects.requireNonNull(computing);
        if (name.isEmpty() || name.indexOf('.') >= 0) {
            throw new IllegalArgumentException("Expected a simple field name: " + name);
        }
        if (caller == null) {
            throw new IllegalCallerException("no caller frame");
        }
        MethodHandles.Lookup lookup = new MethodHandles.Lookup(caller);
        try {
            if (lookup.findClass(owner.getName()) != owner) {
                lookup = lookup.in(owner);
            }
        } catch (ClassNotFoundException | IllegalAccessException e) {
            lookup = lookup.in(owner);
        }
        ReferenceFieldAccessor<R, T> adaptedComputingFunction = computing::apply;
        @SuppressWarnings({"rawtypes", "unchecked"})
        ReferenceFieldAccessor<R, T> updater = (ReferenceFieldAccessor<R, T>)
                of(ReferenceFieldAccessor.class)
                .lookup(lookup)
                .location(owner, name)
                .build((ReferenceFieldAccessor) adaptedComputingFunction);
        return updater;
    }

    /**
     * Returns a lazy updater for elements of a directly supplied reference
     * array. This convenience method uses {@link Mode#PUBLISH_ONCE} and
     * returns an accessor whose {@link ReferenceArrayAccessor#getOrCompute(Object[], int)
     * getOrCompute} method selects an element by index. The computing function is
     * invoked only when that element is {@code null}. Different arrays and
     * different elements have independent storage. The supplied
     * {@code elementType} identifies the required array and result types;
     * primitive and {@code void} element types are not supported by this
     * overload. Use {@link #of(Class)} with a concrete primitive-array
     * computation interface for primitive elements.
     *
     * <p>For example:
     * {@snippet lang=java :
     * import java.lang.invoke.Lazy;
     *
     * class Names {
     *
     *     private static final Lazy.ReferenceArrayAccessor<String> ENTRY =
     *             Lazy.ofArray(String.class,
     *                     (array, index) -> "entry-" + index);
     *
     *     private final String[] entries = new String[8];
     *
     *     String entry(int index) { return ENTRY.getOrCompute(entries, index); }
     * }
     * }
     *
     * @implSpec This method adapts the computing function to a non-generic
     *           {@code Object[]}-based computation shape and uses
     *           {@link #of(Class)} to build a direct-array updater with the
     *           default {@link Mode#PUBLISH_ONCE} mode.
     *
     * @param elementType the reference array's element type
     * @param computing the function that computes a non-null element value
     * @param <T> the array element type
     * @return an accessor backed by elements of arrays supplied on access
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalArgumentException if {@code elementType} is primitive or
     *         {@code void}, or the required access mode is unsupported
     * @see #of(Class)
     * @since 28
     */
    @SuppressWarnings("unchecked")
    public static <T> ReferenceArrayAccessor<T> ofArray(Class<T> elementType,
                                                       ReferenceArrayAccessor<T> computing) {
        Objects.requireNonNull(elementType);
        Objects.requireNonNull(computing);
        if (elementType.isPrimitive() || elementType == void.class) {
            throw new IllegalArgumentException("A reference array updater requires a reference element type");
        }
        Class<?> arrayType = elementType.arrayType();
        ObjectArrayElementFunction computer = (array, index) ->
                elementType.cast(computing.getOrCompute(
                        (T[]) arrayType.cast(array), index));
        ObjectArrayElementFunction updater = of(ObjectArrayElementFunction.class)
                .build(computer);
        return (array, index) -> elementType.cast(updater.apply(
                (Object[]) arrayType.cast(array), index));
    }

    @FunctionalInterface
    private interface ObjectArrayElementFunction {
        Object apply(Object[] array, int index);
    }

    /**
     * Starts configuring a lazy updater with the specified computation shape.
     * The shape is a functional interface whose abstract method receives the
     * storage coordinates and returns the value to compute when the selected
     * location is uninitialized. A terminal
     * {@link Builder#build(Object) build} operation retains a function
     * implementing this interface. A terminal
     * {@link Builder#buildUseSite(Class) buildUseSite} operation instead
     * creates an accessor that accepts such a function on each invocation.
     *
     * <p>The returned builder is immutable. Its default mode is
     * {@link Mode#PUBLISH_ONCE}; its default field-access authority is
     * {@link MethodHandles#publicLookup() publicLookup()}. With no
     * {@code location} call, it selects an element of an array supplied as
     * the first storage coordinate. A field or array-valued field is selected
     * by {@link Builder#location(Class, String)}; a memory-segment
     * value is selected by {@link Builder#location(MemoryLayout,
     * MemoryLayout.PathElement...)}. The class documentation specifies the
     * {@linkplain ##access-shapes access shapes},
     * {@linkplain ##computing-functions computing-function rules},
     * {@linkplain ##uninitialized-locations reserved values}, and
     * {@linkplain ##updater-modes mode semantics}.
     *
     * <p>For a direct {@code int[]} element:
     * {@snippet lang=java :
     * import java.lang.invoke.Lazy;
     * import java.lang.invoke.Lazy.IntArrayAccessor;
     *
     * class Entries {
     *
     *     private static final IntArrayAccessor ENTRY =
     *             Lazy.of(IntArrayAccessor.class)
     *                     .build((array, index) -> index + 1);
     *
     *     int get(int[] array, int index) {
     *         return ENTRY.getOrCompute(array, index);
     *     }
     * }
     * }
     *<p>
     * For more builder examples, see {@linkplain ##lazy-caches Lazy caches}.
     *
     * @param computationShape the functional interface for the computation
     * @param <C> the computation's functional-interface type
     * @return a new immutable updater builder
     * @throws NullPointerException if {@code computationShape} is {@code null}
     * @since 28
     */
    public static <C> Builder<C> of(Class<C> computationShape) {
        Objects.requireNonNull(computationShape);
        return LazyUpdaterSupport.newBuilder(computationShape);
    }

    /**
     * A builder for a lazy updater. Builders returned by
     * {@link #of(Class)} are immutable: each configuration method returns
     * a new builder and leaves the original unchanged. These methods
     * record the selection; the terminal methods validate the
     * functional-interface shapes, storage, access privileges, and mode before
     * creating an updater. Multiple updaters built from the same selection
     * may access the same caller-owned location when invoked with coordinates
     * selecting it. The builder does not retain a computing function; each
     * declaration-site updater retains the function supplied to its build.
     *
     * <p>The builder has three location forms:
     * <ul>
     * <li>No explicit location selects an element of a directly supplied
     *     array. The computation shape starts with the array type and has one
     *     or more following {@code int} indices.</li>
     * <li>A named field selects the field itself, or an element reached
     *     through an array-valued field when index coordinates follow the
     *     receiver. A static field has no receiver; an instance field has a
     *     receiver first. The field's owner is a {@code Class} token, preserving
     *     its identity across class loaders.</li>
     * <li>A memory-layout path selects a value in a supplied
     *     {@link java.lang.foreign.MemorySegment}. Coordinates are the segment,
     *     a {@code long} base offset, and one {@code long} index for each open
     *     sequence element in the path, in path order.</li>
     * </ul>
     *
     * <p>A computation shape can be a raw generic functional interface when
     * the named field's explicit owner supplies the missing receiver identity.
     * This does not recover erased value type arguments: values are then
     * checked against the field's erased type at run time. A specialized
     * ready-made reference-field use-site accessor additionally validates
     * the field's generic value type. A direct-array declaration-site
     * computation shape must identify a concrete array
     * coordinate. A specialized use-site accessor may provide that
     * information even when its computing-function interface is generic.
     *
     * @param <C> the computation's functional-interface type
     * @see Lazy##access-shapes Locations and access shapes
     * @see Lazy##updater-modes Updater modes
     * @since 28
    */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    public sealed interface Builder<C> permits LazyBuilderImpl {

        /**
         * Returns a builder using the specified coordination mode. The
         * initial mode is {@link Mode#PUBLISH_ONCE}. A mode unsupported
         * by the selected location is rejected by a terminal operation.
         *
         * @param mode the updater mode
         * @return a new builder using {@code mode}
         * @throws NullPointerException if {@code mode} is {@code null}
         */
        Builder<C> mode(Mode mode);

        /**
         * Returns a builder using the specified lookup to access a named
         * field and the functional interfaces. The initial lookup is
         * {@link MethodHandles#publicLookup() publicLookup()}. A lookup is
         * applicable only to a named-field location; explicitly supplying one
         * for a direct array or memory segment is rejected by a terminal
         * operation. Access is checked when the updater is built, not each
         * time it is used. A receiver restriction imposed by the lookup is
         * preserved.
         *
         * @param lookup the access authority for a named field
         * @return a new builder using {@code lookup}
         * @throws NullPointerException if {@code lookup} is {@code null}
         */
        Builder<C> lookup(MethodHandles.Lookup lookup);

        /**
         * Returns a builder selecting a named field, or an element of an
         * array held in that field. The {@code ownerClass} is the type through
         * which the field is selected; an inherited field may be selected
         * through a subclass. The field name is simple, without a class
         * prefix. The field may be static or an instance field, as determined
         * by its declaration and the computation shape's coordinates. A
         * receiver is required for an instance field. One or more trailing
         * {@code int} coordinates select array elements, including elements of
         * multidimensional arrays.
         *
         * <p>For a private instance field:
         * {@snippet lang=java :
         * import java.lang.invoke.MethodHandles;
         * import java.lang.invoke.Lazy;
         *
         * class Counter {

         *     interface Count { int getOrCompute(Counter receiver); }
         *
         *     private static final Count COUNT = Lazy.of(Count.class)
         *             .lookup(MethodHandles.lookup())
         *             .location(Counter.class, "count")
         *             .build(c -> Math.max(1, c.seed));
         *
         *     private final int seed;
         *     private int count;
         *
         *     Counter(int seed) { this.seed = seed; }
         *
         *     int count() { return COUNT.getOrCompute(this); }
         * }
         * }
         *
         * @param ownerClass the type through which the field is selected
         * @param fieldName the simple field name
         * @return a new builder selecting the field
         * @throws NullPointerException if an argument is {@code null}
         * @throws IllegalArgumentException if {@code fieldName} is empty or
         *         contains a dot
         * @throws IllegalStateException if a location has already been
         *         selected
         */
        Builder<C> location(Class<?> ownerClass, String fieldName);

        /**
         * Returns a builder selecting a value in a memory segment through a
         * structured memory layout. The path must select a
         * {@link java.lang.foreign.ValueLayout}; it may contain group elements
         * and open sequence elements, but not a dereference element.
         * The computation shape receives a
         * {@link java.lang.foreign.MemorySegment}, a {@code long} base offset,
         * and one {@code long} index for each open sequence element, in path
         * order. An empty path selects the layout itself when it is a value
         * layout. The path array is copied.
         *
         * <p>{@link Mode#SERIALIZED} is not supported for segment
         * locations. {@link Mode#PUBLISH_ONCE} requires the selected
         * layout's var handle to support an aligned atomic
         * compare-and-exchange operation; {@link Mode#PLAIN} can be used
         * when that operation is unavailable. The terminal operation checks
         * these requirements.
         *
         * <p>For a structured segment:
         * {@snippet lang=java :
         * import java.lang.foreign.MemoryLayout;
         * import java.lang.foreign.MemorySegment;
         * import java.lang.foreign.ValueLayout;
         * import java.lang.invoke.Lazy;
         *
         * class SegmentCounter {
         *
         *     static final MemoryLayout LAYOUT =
         *             MemoryLayout.structLayout(ValueLayout.JAVA_INT.withName("count"));
         *
         *     interface Count { int getOrCompute(MemorySegment segment, long baseOffset); }
         *
         *     static final Count COUNT = Lazy.of(Count.class)
         *             .location(LAYOUT, MemoryLayout.PathElement.groupElement("count"))
         *             .build((segment, offset) -> 42);
         *
         *     static int count(MemorySegment segment) {
         *         return COUNT.getOrCompute(segment, 0L);
         *     }
         * }
         * }
         * <p>
         * For a shape that takes more than just an offset parameter, path elements can be used to project a layout onto an interface shape:
         * {@snippet lang=java :
         * import java.lang.foreign.MemoryLayout;
         * import java.lang.foreign.MemorySegment;
         * import java.lang.foreign.ValueLayout;
         * import java.lang.invoke.Lazy;
         *
         * class SegmentIntArrayAccess {
         *
         *     static final MemoryLayout LAYOUT =
         *             MemoryLayout.sequenceLayout(10, ValueLayout.JAVA_INT);
         *
         *     interface SequenceAccessor { int getOrCompute(MemorySegment segment, long baseOffset, long index); }
         *
         *     static final SequenceAccessor SEQ = Lazy.of(SequenceAccessor.class)
         *             .location(LAYOUT, MemoryLayout.PathElement.sequenceElement())
         *             .build((segment, offset, index) -> (int) (31 + index * index));
         *
         *     static int val(MemorySegment segment, long index) {
         *         return SEQ.getOrCompute(segment, 0L, index);
         *     }
         * }
         * }
         *
         *
         * @param layout the layout containing the selected value
         * @param pathElements the path from the layout to the value
         * @return a new builder selecting the segment value
         * @throws NullPointerException if any argument or path element is
         *         {@code null}
         * @throws IllegalStateException if a location has already been
         *         selected
         */
        Builder<C> location(
                MemoryLayout layout, MemoryLayout.PathElement... pathElements);

        /**
         * Builds a declaration-site updater. The returned updater implements
         * the computation shape and retains {@code computingFunction}. Its
         * functional method reads the selected location and computes a value
         * only if the location contains its reserved default value.
         * For a direct array, the first coordinate is the array and subsequent
         * coordinates are its indices. For a named field, the field may be
         * static or instance. For a segment, coordinates follow the layout
         * path specified by {@link #location(MemoryLayout,
         * MemoryLayout.PathElement...)}.
         *
         * @param computingFunction the computation retained by the updater
         * @return an updater implementing the computation shape
         * @throws NullPointerException if {@code computingFunction} is
         *         {@code null}
         * @throws IllegalArgumentException if the shape, location, mode,
         *         access, or computing function is incompatible
         */
        C build(C computingFunction);

        /**
         * Builds a use-site updater implementing {@code useSiteShape}.
         * Its functional method accepts a computing function first, followed
         * by the storage coordinates. The computing function's functional
         * method must have the same erased parameter and return types as the
         * builder's computation shape; it need not be the same nominal
         * interface. A non-null computing function is required on every
         * invocation, even when the location is already initialized.
         *
         * <p>For a direct {@code int[]} element:
         * {@snippet lang=java :
         * import java.lang.invoke.Lazy;
         * import java.lang.invoke.Lazy.IntArrayAccessor;
         *
         * class Entries {
         *
         *     private static final IntArrayAccessor.Computer ENTRY =
         *             Lazy.of(IntArrayAccessor.class)
         *                     .buildUseSite(IntArrayAccessor.Computer.class);
         *
         *     int get(int[] array, int index) {
         *         return ENTRY.getOrCompute((a, i) -> i + 1, array, index);
         *     }
         * }
         * }
         *
         * @param useSiteShape the functional interface of the returned updater
         * @param <U> the use-site updater's functional-interface type
         * @return a use-site updater implementing {@code useSiteShape}
         * @throws NullPointerException if {@code useSiteShape} is {@code null}
         * @throws IllegalArgumentException if the shapes, location, mode, or
         *         access are incompatible
         */
        <U> U buildUseSite(Class<U> useSiteShape);
    }

    // Ready-made interfaces

    /**
     * An updater for a named static {@code int} field. The computing
     * function is retained when the updater is created. The nested
     * {@link Computer} interface accepts a computing function on each access.
     *
     * @see Lazy##computing-functions Computing functions
     * @see Lazy##uninitialized-locations Uninitialized locations
     * @see Lazy##updater-modes Updater modes
     * @since 28
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    @FunctionalInterface
    public interface StaticIntFieldAccessor {

        /**
         * Returns the field value, computing it when uninitialized using the
         * function retained by this updater.
         *
         * @return the field value
         */
        int getOrCompute();

        /**
         * An updater for a named static {@code int} field. The computing
         * function is supplied at each access.
         *
         * @see Lazy##computing-functions Computing functions
         * @see Lazy##uninitialized-locations Uninitialized locations
         * @see Lazy##updater-modes Updater modes
         * @since 28
         */
        @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
        @FunctionalInterface
        interface Computer {

            /**
             * Returns the field value, computing it when uninitialized.
             *
             * @param computingFunction the computation for an uninitialized field
             * @return the field value
             */
            int getOrCompute(IntSupplier computingFunction);
        }
    }

    /**
     * An updater for a named static {@code long} field. The computing
     * function is retained when the updater is created. The nested
     * {@link Computer} interface accepts a computing function on each access.
     *
     * @see Lazy##computing-functions Computing functions
     * @see Lazy##uninitialized-locations Uninitialized locations
     * @see Lazy##updater-modes Updater modes
     * @since 28
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    @FunctionalInterface
    public interface StaticLongFieldAccessor {

        /**
         * Returns the field value, computing it when uninitialized using the
         * function retained by this updater.
         *
         * @return the field value
         */
        long getOrCompute();

        /**
         * An updater for a named static {@code long} field. The computing
         * function is supplied at each access.
         *
         * @see Lazy##computing-functions Computing functions
         * @see Lazy##uninitialized-locations Uninitialized locations
         * @see Lazy##updater-modes Updater modes
         * @since 28
         */
        @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
        @FunctionalInterface
        interface Computer {

            /**
             * Returns the field value, computing it when uninitialized.
             *
             * @param computingFunction the computation for an uninitialized field
             * @return the field value
             */
            long getOrCompute(LongSupplier computingFunction);
        }
    }

    /**
     * An updater for a named static field with a reference-valued result.
     * The computing function is retained when the updater is created. A
     * primitive field value can be returned boxed when this interface is used
     * by {@link #ofStaticField(Class, String, Supplier)}. The nested
     * {@link Computer} interface accepts a computing function on each access.
     * A computed {@code null} is rejected because it denotes an
     * uninitialized field.
     *
     * @param <V> the field result type
     * @see Lazy##computing-functions Computing functions
     * @see Lazy##uninitialized-locations Uninitialized locations
     * @see Lazy##updater-modes Updater modes
     * @since 28
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    @FunctionalInterface
    public interface StaticReferenceFieldAccessor<V> {

        /**
         * Returns the field value, computing it when uninitialized using the
         * function retained by this updater.
         *
         * @return the field value
         */
        V getOrCompute();

        /**
         * An updater for a named static reference field. The computing function
         * is supplied at each access. A non-generic subinterface, such as
         * {@code NameAccessor extends StaticReferenceFieldAccessor.Computer<String>},
         * retains its value type when passed to
         * {@link Builder#buildUseSite(Class)}. The explicit field owner
         * also permits the raw interface token, with checks only at its erased
         * value type. For a specialized token, the field's erased type must be
         * a reference type assignable to {@code V}; a parameterized or
         * generic-array value type must match the field's declared generic
         * type.
         *
         * @param <V> the field value type or a reference supertype, subject
         *            to the generic-type matching rule above
         * @see Lazy##computing-functions Computing functions
         * @see Lazy##uninitialized-locations Uninitialized locations
         * @see Lazy##updater-modes Updater modes
         * @since 28
         */
        @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
        @FunctionalInterface
        interface Computer<V> {

            /**
             * Returns the field value, computing it when uninitialized.
             *
             * @param computingFunction the computation for an uninitialized field
             * @return the field value
             */
            V getOrCompute(Supplier<? extends V> computingFunction);
        }
    }

    /**
     * An updater for a named instance {@code int} field. The computing
     * function is retained when the updater is created. The nested
     * {@link Computer} interface accepts a computing function on each access.
     *
     * @param <R> the receiver type
     * @see Lazy##computing-functions Computing functions
     * @see Lazy##uninitialized-locations Uninitialized locations
     * @see Lazy##updater-modes Updater modes
     * @since 28
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    @FunctionalInterface
    public interface IntFieldAccessor<R> {

        /**
         * Returns the receiver's field value, computing it when uninitialized
         * using the function retained by this updater.
         *
         * @param receiver the field receiver
         * @return the field value
         */
        int getOrCompute(R receiver);

        /**
         * An updater for a named instance {@code int} field. The computing
         * function is supplied at each access. A non-generic subinterface,
         * such as {@code CountAccessor extends IntFieldAccessor.Computer<Counter>},
         * retains the receiver type when passed to
         * {@link Builder#buildUseSite(Class)}. The explicit field owner
         * also permits the raw interface token, whose receiver is then
         * checked only at its erased type.
         *
         * @param <R> the receiver type
         * @see Lazy##computing-functions Computing functions
         * @see Lazy##uninitialized-locations Uninitialized locations
         * @see Lazy##updater-modes Updater modes
         * @since 28
         */
        @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
        @FunctionalInterface
        interface Computer<R> {

            /**
             * Returns the receiver's field value, computing it when uninitialized.
             *
             * @param computingFunction the computation for an uninitialized field
             * @param receiver the field receiver
             * @return the field value
             */
            int getOrCompute(ToIntFunction<? super R> computingFunction, R receiver);
        }
    }

    /**
     * An updater for a named instance {@code long} field. The computing
     * function is retained when the updater is created. The nested
     * {@link Computer} interface accepts a computing function on each access.
     *
     * @param <R> the receiver type
     * @see Lazy##computing-functions Computing functions
     * @see Lazy##uninitialized-locations Uninitialized locations
     * @see Lazy##updater-modes Updater modes
     * @since 28
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    @FunctionalInterface
    public interface LongFieldAccessor<R> {

        /**
         * Returns the receiver's field value, computing it when uninitialized
         * using the function retained by this updater.
         *
         * @param receiver the field receiver
         * @return the field value
         */
        long getOrCompute(R receiver);

        /**
         * An updater for a named instance {@code long} field. The computing
         * function is supplied at each access. A non-generic subinterface,
         * such as {@code TotalAccessor extends LongFieldAccessor.Computer<Counter>},
         * retains the receiver type when passed to
         * {@link Builder#buildUseSite(Class)}. The explicit field owner
         * also permits the raw interface token, whose receiver is then
         * checked only at its erased type.
         *
         * @param <R> the receiver type
         * @see Lazy##computing-functions Computing functions
         * @see Lazy##uninitialized-locations Uninitialized locations
         * @see Lazy##updater-modes Updater modes
         * @since 28
         */
        @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
        @FunctionalInterface
        interface Computer<R> {

            /**
             * Returns the receiver's field value, computing it when uninitialized.
             *
             * @param computingFunction the computation for an uninitialized field
             * @param receiver the field receiver
             * @return the field value
             */
            long getOrCompute(ToLongFunction<? super R> computingFunction, R receiver);
        }
    }

    /**
     * An updater for a named instance field with a reference-valued result.
     * The computing function is retained when the updater is created. A
     * primitive field value can be returned boxed when this interface is used
     * by {@link #ofField(Class, String, Function)}. The nested
     * {@link Computer} interface accepts a computing function on each access.
     * A computed {@code null} is rejected because it denotes an uninitialized
     * field.
     *
     * @param <R> the receiver type
     * @param <V> the field result type
     * @see Lazy##computing-functions Computing functions
     * @see Lazy##uninitialized-locations Uninitialized locations
     * @see Lazy##updater-modes Updater modes
     * @since 28
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    @FunctionalInterface
    public interface ReferenceFieldAccessor<R, V> {

        /**
         * Returns the receiver's field value, computing it when uninitialized
         * using the function retained by this updater.
         *
         * @param receiver the field receiver
         * @return the field value
         */
        V getOrCompute(R receiver);

        /**
         * An updater for a named instance reference field. The computing
         * function is supplied at each access. A non-generic subinterface,
         * such as
         * {@code NameAccessor extends ReferenceFieldAccessor.Computer<Holder, String>},
         * retains its receiver and result types when passed to
         * {@link Builder#buildUseSite(Class)}. The explicit field owner
         * also permits the raw interface token, with checks only at its erased
         * types. For a specialized token, the field's erased type must be
         * assignable to {@code V}; a parameterized or generic-array value type
         * must match the field's declared generic type.
         *
         * @param <R> the receiver type
         * @param <V> the field value type or a reference supertype, subject to
         *            the generic-type matching rule above
         * @see Lazy##computing-functions Computing functions
         * @see Lazy##uninitialized-locations Uninitialized locations
         * @see Lazy##updater-modes Updater modes
         * @since 28
         */
        @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
        @FunctionalInterface
        interface Computer<R, V> {

            /**
             * Returns the receiver's field value, computing it when uninitialized.
             *
             * @param computingFunction the computation for an uninitialized field
             * @param receiver the field receiver
             * @return the field value
             */
            V getOrCompute(Function<? super R, ? extends V> computingFunction, R receiver);
        }
    }

    /**
     * An updater for elements of a directly supplied {@code int[]}.
     * The computing function is retained when the updater is created. The
     * nested {@link Computer} interface accepts a computing function on each
     * access.
     *
     * @see Lazy##computing-functions Computing functions
     * @see Lazy##uninitialized-locations Uninitialized locations
     * @see Lazy##updater-modes Updater modes
     * @since 28
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    @FunctionalInterface
    public interface IntArrayAccessor {

        /**
         * Returns the selected element, computing it when uninitialized using
         * the function retained by this updater.
         *
         * @param array the array
         * @param index the selected index
         * @return the selected value
         */
        int getOrCompute(int[] array, int index);

        /**
         * An updater for elements of a directly supplied {@code int[]}.
         * The computing function is supplied at each access.
         *
         * @since 28
         */
        @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
        @FunctionalInterface
        interface Computer {

            /**
             * Returns the selected element, computing it when uninitialized.
             *
             * @param computingFunction the computation for an uninitialized element
             * @param array the array
             * @param index the selected index
             * @return the selected value
             */
            int getOrCompute(IntArrayAccessor computingFunction, int[] array, int index);
        }
    }

    /**
     * An updater for elements of a directly supplied {@code long[]}.
     * The computing function is retained when the updater is created. The
     * nested {@link Computer} interface accepts a computing function on each
     * access.
     *
     * @see Lazy##computing-functions Computing functions
     * @see Lazy##uninitialized-locations Uninitialized locations
     * @see Lazy##updater-modes Updater modes
     * @since 28
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    @FunctionalInterface
    public interface LongArrayAccessor {

        /**
         * Returns the selected element, computing it when uninitialized using
         * the function retained by this updater.
         *
         * @param array the array
         * @param index the selected index
         * @return the selected value
         */
        long getOrCompute(long[] array, int index);

        /**
         * An updater for elements of a directly supplied {@code long[]}.
         * The computing function is supplied at each access.
         *
         * @since 28
         */
        @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
        @FunctionalInterface
        interface Computer {

            /**
             * Returns the selected element, computing it when uninitialized.
             *
             * @param computingFunction the computation for an uninitialized element
             * @param array the array
             * @param index the selected index
             * @return the selected value
             */
            long getOrCompute(LongArrayAccessor computingFunction, long[] array, int index);
        }
    }

    /**
     * An updater for elements of a directly supplied {@code E[]} array. An
     * element may itself be an array. The computing function is retained when
     * the updater is created. The nested {@link Computer} interface accepts a
     * computing function on each access. Clients building a declaration-site
     * updater declare a non-generic subinterface, such as
     * {@code Names extends ReferenceArrayAccessor<String>}, and pass its
     * {@code Class} token to {@link Lazy#of(Class)}. The builder has no explicit
     * location for a directly supplied array. A computed {@code null} is
     * rejected because it denotes an uninitialized element.
     *
     * @param <E> the array element type
     * @see Lazy##computing-functions Computing functions
     * @see Lazy##uninitialized-locations Uninitialized locations
     * @see Lazy##updater-modes Updater modes
     * @since 28
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    @FunctionalInterface
    public interface ReferenceArrayAccessor<E> {

        /**
         * Returns the selected element, computing it when uninitialized using
         * the function retained by this updater.
         *
         * @param array the array
         * @param index the selected index
         * @return the selected value
         */
        E getOrCompute(E[] array, int index);

        /**
         * An updater for elements of a directly supplied {@code E[]} array.
         * The computing function is supplied at each access. Clients declare a
         * non-generic subinterface, such as
         * {@code Names extends ReferenceArrayAccessor.Computer<String>}, and
         * pass its {@code Class} token to {@link Builder#buildUseSite(Class)}.
         * A computed {@code null} is rejected because it denotes an
         * uninitialized element.
         *
         * @param <E> the array element type
         * @since 28
         */
        @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
        @FunctionalInterface
        interface Computer<E> {

            /**
             * Returns the selected element, computing it when uninitialized.
             *
             * @param computingFunction the computation for an uninitialized element
             * @param array the array
             * @param index the selected index
             * @return the selected value
             */
            E getOrCompute(ReferenceArrayAccessor<E> computingFunction, E[] array, int index);
        }
    }

}
