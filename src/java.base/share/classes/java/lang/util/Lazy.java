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

package java.lang.util;

import java.io.Serial;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.RandomAccess;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

import jdk.internal.access.SharedSecrets;
import jdk.internal.invoke.LazyUpdaterSupport;
import jdk.internal.javac.PreviewFeature;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;
import jdk.internal.util.LazySupport;

/**
 * Factory methods for lazy caches and lazy updaters.
 *
 * <p>A <em>computing function</em> produces a value only when it is needed.
 * Cache modes and coordinated updater modes retain a successful result for
 * each storage location. The {@linkplain UpdaterMode#PLAIN plain updater mode}
 * permits a weaker guarantee. Computation runs on the accessing thread; these
 * factories do not schedule work on another thread. The two families differ
 * primarily in who owns the storage and which values can represent a result:
 * <table class="striped">
 * <caption>Lazy storage families</caption>
 * <thead>
 * <tr><th scope="col">Family</th><th scope="col">Storage and results</th>
 *     <th scope="col">Modes</th></tr>
 * </thead>
 * <tbody>
 * <tr><th scope="row">Caches</th>
 *     <td>The factory provides storage. A cache retains non-null values,
 *         including boxed zero; a set cache retains either membership result.</td>
 *     <td>{@link CacheMode#COMPUTE_ONCE COMPUTE_ONCE} or
 *         {@link CacheMode#PUBLISH_ONCE PUBLISH_ONCE}</td></tr>
 * <tr><th scope="row">Updaters</th>
 *     <td>The caller provides a field or array element. Its Java default value
 *         represents an uninitialized location and cannot be a computed result.</td>
 *     <td>{@link UpdaterMode#PUBLISH_ONCE PUBLISH_ONCE},
 *         {@link UpdaterMode#SERIALIZED SERIALIZED}, or
 *         {@link UpdaterMode#PLAIN PLAIN}</td></tr>
 * </tbody>
 * </table>
 *
 * <h2 id="cache-factories">Caches</h2>
 * Cache factories provide storage and a computing function at construction.
 * {@link #ofScalarCache(Supplier)} and
 * {@link #ofScalarCache(CacheMode, Supplier)} return a {@link Supplier} holding
 * one value. {@link #ofScalarCache(CacheMode, Function)} returns a
 * {@link Function} holding one value and taking an access-time context.
 * {@link #ofListCache(CacheMode, int, IntFunction)},
 * {@link #ofMapCache(CacheMode, Set, Function)}, and
 * {@link #ofSetCache(CacheMode, Set, Predicate)} return unmodifiable
 * collections with independently computed elements, mapped values, or
 * memberships.
 * <p>
 * The {@code ofScalarCache} overloads taking a mode can be ambiguous for a
 * method reference applicable to both {@code Supplier} and {@code Function}.
 * A lambda with the intended parameter count, or a cast to the intended
 * functional interface, selects the desired overload.
 * Lazily initializing an array reference does not make its elements lazy or
 * immutable. Use {@code ofListCache} when elements should be computed
 * independently.
 * <p>
 * For example, a service can be created on its first use:
 * {@snippet lang=java :
 * import java.lang.util.Lazy;
 * import java.util.function.Supplier;
 *
 * class Services {
 *     private static final Supplier<Service> SERVICE =
 *             Lazy.ofScalarCache(Service::new);
 *
 *     static Service service() {
 *         return SERVICE.get();
 *     }
 *
 *     static final class Service { }
 * }
 * }
 *
 * <h3 id="cache-modes">Cache modes</h3>
 * Each cache factory specifies an {@link CacheMode initialization mode}, either
 * explicitly or by default. The mode applies independently to each lazy value.
 * For a collection, the computing function may run concurrently for distinct
 * indices, keys, or
 * candidates, even in {@code COMPUTE_ONCE} mode. At-most-once invocation in that
 * mode applies to each storage location separately.
 * <p>
 * {@link CacheMode#COMPUTE_ONCE} is intended for <em>initialization</em>,
 * including computations with side effects that must not be repeated. At-most-once
 * invocation of the computing function is guaranteed for each storage
 * location. One accessing thread invokes the function; accesses by other
 * threads to the same storage wait while that invocation is in progress.
 * If it succeeds, its result is retained. If it fails, the failure is
 * permanent and no further invocation is attempted. The computing function
 * is not invoked for a storage location that is never accessed.
 * The {@link #ofScalarCache(Supplier)} factory uses this mode by default.
 * <p>
 * {@link CacheMode#PUBLISH_ONCE} is intended for <em>memoization</em>,
 * where computing a value more than once is acceptable. Competing threads may
 * each invoke the computing function. A successful result is installed atomically
 * only if no result has already been installed. Every access to that lazy
 * storage location that completes normally returns the installed result,
 * including accesses whose computations produced a different result. Results
 * that are not installed are discarded; this API does not close them or
 * otherwise release resources they hold.
 * Failures are not retained, so later accesses may retry computation. Computing
 * functions used with this mode should tolerate concurrent and repeated
 * invocation and should avoid side effects that require at-most-once execution.
 *
 * <h3 id="cache-failures">Computation failures</h3>
 * In {@code COMPUTE_ONCE} mode, a computation failure is permanent:
 * <ul>
 * <li>If the computing function throws an {@link Error}, the computing thread
 *     throws that same error unchanged. Subsequent accesses throw a new
 *     {@link InitializationError} without a cause, with a message that includes
 *     the class name of the original error, when available.</li>
 * <li>If the computing function throws any other throwable, the computing thread
 *     throws a {@link NoSuchElementException} with that throwable as its cause.
 *     Subsequent accesses throw {@code NoSuchElementException} without a cause,
 *     with a message that includes the class name of the original throwable.</li>
 * </ul>
 * Subsequent accesses include those by threads waiting for the failing
 * computation to finish. The failure state does not retain the original
 * throwable, and the computing function is not invoked again for that value.
 * Propagating an initial {@code Error} unchanged and reporting subsequent
 * accesses with a different {@code Error} follows the approach used for class
 * initialization in section {@jls 12.4.2} of
 * <cite>The Java Language Specification</cite>.
 * <p>
 * A {@code null} result from a value-producing computing function is treated as
 * a computation failure with a {@link NullPointerException}. In
 * {@code COMPUTE_ONCE} mode, recursive access
 * by the computing thread to the same uninitialized value throws
 * {@link IllegalStateException}; if it escapes from the computing function,
 * it is handled as a computation failure.
 * <p>
 * In {@code PUBLISH_ONCE} mode, an exception or error from the computing
 * function is propagated to the invoking thread, and a {@code null} result causes
 * {@code NullPointerException}. Such an access fails even if another thread
 * successfully installs a value concurrently. Neither kind of failure prevents
 * another access from computing or returning a value.
 * <p>
 * Computing functions should not have circular dependencies. Recursive
 * computation in {@code PUBLISH_ONCE} mode is not required to be detected, and
 * may cause unbounded recursion. In {@code COMPUTE_ONCE} mode, dependencies
 * between values being computed by different threads can cause deadlock.
 * Use during class initialization is also subject to the initialization rules
 * in section {@jls 12.4} of <cite>The Java Language Specification</cite>.
 * <p>
 * An operation that accesses multiple lazy values or memberships may initialize
 * some before completing abruptly. Values already installed, membership results
 * already retained, and permanent failures already recorded are not rolled back.
 * The initialization mode governs the computing function, not other actions or
 * callbacks invoked by the operation, such as a {@code forEach} action, a stream
 * stage, or an element's {@code equals} or {@code hashCode} method. Such code
 * may itself perform further lazy accesses. Failures from
 * such code are handled according to the operation's contract; their propagation
 * does not itself record an additional initialization failure. Any lazy accesses
 * performed by such code, and any throwable that escapes a computing function,
 * remain subject to their applicable initialization and failure rules.
 *
 * <h3 id="collection-properties">Collection properties</h3>
 * Each invocation of {@link #ofListCache(CacheMode, int, IntFunction) ofListCache},
 * {@link #ofMapCache(CacheMode, Set, Function) ofMapCache}, or
 * {@link #ofSetCache(CacheMode, Set, Predicate) ofSetCache} provides independent, initially
 * uninitialized storage for its elements, mapped values, or membership statuses,
 * even when the same computing function and other arguments are supplied to
 * another invocation. Retaining a result or recording a permanent failure in
 * one collection's storage does not change the initialization state of storage
 * provided by another factory invocation. Views and map entries share their
 * originating collection's storage as described by the corresponding factory.
 * For a factory invocation with size zero, no keys, or no candidates, there is
 * no lazy storage to initialize, and an existing collection instance may be
 * reused.
 * <p>
 * The collections, their views, and their map entries are unmodifiable.
 * Unsupported mutation operations on these objects, or on their iterators or
 * list iterators, throw {@link UnsupportedOperationException} without invoking
 * lazy computing functions or callbacks supplied to the operation. This applies
 * even when the operation would have no effect. In particular, their
 * {@code replaceAll}, {@code sort}, {@code removeIf}, and
 * {@code computeIfAbsent} do not invoke their supplied functions, comparators,
 * or predicates.
 *
 * <h3 id="traversal-failures">Computation failures during traversal</h3>
 * The following rules apply to iterators, list iterators, and spliterators
 * obtained from collections returned by this class, including their views.
 * If a lazy access performed by the traversal itself encounters a computation
 * failure while obtaining an element or determining membership, including a
 * previously recorded failure, the failing element or candidate is not consumed
 * or skipped. Progress may be retained only past
 * elements already returned or passed to a supplied traversal action, and
 * candidates already determined not to be members. In particular, a
 * {@link ListIterator#next() next} or {@link ListIterator#previous() previous}
 * call that fails for this reason leaves the list iterator's cursor unchanged.
 * <p>
 * These rules also apply to computations performed by {@code hasNext} or
 * {@code hasPrevious}, and to bulk traversal with {@code forEachRemaining}.
 * Elements already passed to the action by a bulk traversal remain consumed;
 * the failing element or candidate remains pending. If
 * {@link Spliterator#tryAdvance(java.util.function.Consumer) tryAdvance}
 * encounters a computation failure in a lazy access performed by the traversal
 * itself, its action is not invoked. If
 * {@link Spliterator#trySplit() trySplit} fails because of a computation failure,
 * the original spliterator retains coverage of all
 * remaining elements and candidates whose membership is unresolved.
 * <p>
 * A subsequent operation that requires the pending value or membership accesses
 * the same lazy storage. In {@code PUBLISH_ONCE} mode, it may retry computation
 * or observe a result installed by another access. In {@code COMPUTE_ONCE} mode,
 * it reports the recorded failure without invoking the computing function again.
 * A {@code NoSuchElementException} that reports a computation failure does not
 * indicate that the traversal is exhausted.
 * <p>
 * These guarantees concern lazy accesses performed by the traversal itself. If a
 * supplied traversal action throws, subsequent behavior of the iterator or
 * spliterator is unspecified, as described by
 * {@link Iterator#forEachRemaining(java.util.function.Consumer)} and
 * {@link Spliterator#tryAdvance(java.util.function.Consumer)}. This includes
 * an action that itself performs a lazy access which fails.
 *
 * <h3 id="streams">Streams and spliterators</h3>
 * Creating a spliterator, sequential stream, or parallel stream from a collection
 * returned by this class, or from one of its views, does not invoke a computing
 * function or report a recorded computation failure. Querying a spliterator's
 * characteristics or size estimate, including
 * {@link Spliterator#getExactSizeIfKnown() getExactSizeIfKnown}, has the same
 * guarantee.
 * <p>
 * These spliterators, including those returned by splitting, report
 * {@link Spliterator#NONNULL NONNULL} and
 * {@link Spliterator#IMMUTABLE IMMUTABLE}. The latter describes the absence of
 * structural modification of the source; the elements themselves may be mutable.
 * Additional guaranteed characteristics are shown below.
 * <table class="striped">
 * <caption>Additional guaranteed spliterator characteristics</caption>
 * <thead>
 * <tr><th scope="col">Source</th><th scope="col">Characteristics</th></tr>
 * </thead>
 * <tbody>
 * <tr><th scope="row">A list or its views</th>
 *     <td>{@link Spliterator#ORDERED ORDERED}, {@link Spliterator#SIZED SIZED},
 *         {@link Spliterator#SUBSIZED SUBSIZED}</td></tr>
 * <tr><th scope="row">A map's key or entry view</th>
 *     <td>{@link Spliterator#DISTINCT DISTINCT}, {@code SIZED}, {@code SUBSIZED}</td></tr>
 * <tr><th scope="row">A map's value view</th>
 *     <td>{@code SIZED}, {@code SUBSIZED}</td></tr>
 * <tr><th scope="row">A set returned by {@code ofSetCache}</th>
 *     <td>{@code DISTINCT}</td></tr>
 * </tbody>
 * </table>
 * <p>
 * A lazy set's spliterator may also report {@code SIZED} if its exact size is
 * already known when the spliterator is created, and {@code SUBSIZED} if that
 * characteristic's additional requirements are met. Otherwise, its size estimate
 * may use the number of remaining candidates as an upper bound or may be
 * {@code Long.MAX_VALUE}; {@code getExactSizeIfKnown()} returns {@code -1} when
 * {@code SIZED} is not reported. Memberships are not computed to answer these
 * queries. Characteristics remain consistent as required by
 * {@link Spliterator#characteristics()}; another access computing a membership
 * does not by itself change an existing spliterator's characteristics.
 * Other characteristics may be reported when applicable.
 * <p>
 * Traversal and splitting may compute values or memberships for more source
 * elements than a stream operation ultimately consumes. Short-circuiting,
 * particularly in parallel, does not guarantee an exact number of computing
 * function invocations. These permissions are subject to each factory's and
 * view's documented non-computing operations and to the
 * {@linkplain ##traversal-failures traversal failure rules}. In particular,
 * traversing or splitting a map's key or entry view does not compute mapped
 * values; user-supplied code may access those values separately. Stream operations
 * may also avoid computation entirely when the result can be determined without
 * accessing elements, for example by counting the elements of a list with a
 * known size.
 *
 * <h3 id="cache-thread-safety">Thread safety and memory consistency</h3>
 * Cache storage supports concurrent access in both cache modes. Installation of a
 * value by a lazy access has the effects of a
 * {@linkplain VarHandle.AccessMode#SET_RELEASE release write}. Another access
 * that observes the installed value has
 * {@linkplain VarHandle.AccessMode#GET_ACQUIRE acquire effects} with respect to
 * that publication. Actions in the installing thread preceding installation
 * {@linkplain java.util.concurrent##MemoryVisibility <em>happen-before</em>}
 * actions following an access that observes and returns the installed value.
 * The installing access may return its own result without rereading it. These
 * guarantees apply in both cache modes. A successful access whose computed result
 * is discarded also observes the winner's publication with acquire effects.
 * In {@code PUBLISH_ONCE} mode, actions performed by computations that fail or
 * produce discarded results have no publication guarantee to other threads
 * through accesses to the lazy value.
 * <p>
 * In {@code COMPUTE_ONCE} mode, recording a computation failure has the memory
 * effects of a release write of the failure state; observing that recorded
 * failure has the memory effects of an acquire read of that state. Actions in
 * the computing thread preceding the recording of the failure happen-before
 * actions following an access that observes that failure and completes abruptly.
 * This includes actions in an exception handler for the
 * {@code NoSuchElementException} or {@code InitializationError} thrown by that
 * access, including an access that waited for the failing computation to finish.
 * <p>
 * No total ordering across accesses to different lazy values is guaranteed.
 * Subsequent reads of an installed value or recorded failure may be elided
 * provided the applicable publication guarantee is preserved; repeated access
 * provides no additional memory ordering or visibility guarantees.
 * <p>
 * Publication of a value or failure does not make subsequent mutations of a
 * computed value, or concurrent access to mutable state used by the computing
 * function, thread-safe. Such mutations and accesses require their own
 * coordination. Lazy access does not provide cancellation or a timeout. Waiting
 * for initialization is not interruptible. If a thread is
 * already interrupted when waiting begins, or is interrupted while waiting,
 * the waiting mechanism preserves that interruption: the thread's interrupted
 * status is set when the access completes, whether normally or abruptly.
 * This guarantee does not constrain the computing function's own handling of
 * interruption, which may change the interrupted status. A computation that
 * blocks indefinitely can prevent access to its value indefinitely.
 *
 * <h3 id="function-composition">Function composition</h3>
 * Functions returned by {@code ofScalarCache(CacheMode, Function)} support the
 * standard {@link Function#compose(Function) compose} and
 * {@link Function#andThen(Function) andThen} operations. The additional functions
 * execute on each application of the composed function: a {@code before}
 * function executes before each lazy access, even when a value is already
 * retained, and an {@code after} function executes whenever that access returns
 * normally. Composition does not add storage for their results. The
 * initialization mode governs only the underlying lazy computation. Failures
 * from the additional functions are propagated to the caller of the composed
 * function. Composition itself does not record an additional initialization
 * failure. Lazy accesses performed by the additional functions remain subject
 * to their applicable initialization and failure rules.
 *
 * <h3 id="computing-function-retention">Computing-function retention</h3>
 * Storage provided by each cache factory strongly retains its
 * computing function while any element, value, or membership still requires
 * initialization. Once every value or membership has either a retained result or a
 * recorded permanent failure, the storage releases the reference held for invoking
 * that function. This includes any mixture of successful and permanently failed
 * computations. An empty list or map, or a set created with no candidates, retains no
 * such reference. For a view, this rule concerns all values or memberships in the
 * original shared storage, including those outside the view.
 * <p>
 * Releasing the invocation reference does not guarantee that the computing
 * function becomes unreachable or is garbage collected. Computed values,
 * retained keys or candidates, other objects, and computations already in
 * progress may still reference it.
 *
 * <h3 id="cache-nullability">Nullability</h3>
 * Providing a {@code null} argument to any cache factory causes a
 * {@link NullPointerException} to be thrown.
 * <p>
 * If a value-producing computing function returns {@code null}, the result is
 * treated as a {@linkplain ##cache-failures computation failure} with a
 * {@code NullPointerException}.
 * In {@code COMPUTE_ONCE} mode, the computing thread throws a
 * {@code NoSuchElementException} with that exception as its cause, and the failure
 * is permanent. In {@code PUBLISH_ONCE} mode, the {@code NullPointerException}
 * is thrown directly and subsequent accesses may retry. An {@link Optional}
 * can be used to represent an absent value. A set's {@link Predicate}
 * instead returns a primitive {@code boolean}; both {@code true} and
 * {@code false} are retained membership results.
 *
 * <h2 id="updater-factories">Updaters</h2>
 * Lazy updaters use caller-owned field or array-element storage. A field or
 * element has independent storage, and a non-default value is returned without
 * computation. The factories do not read the selected storage or invoke the
 * supplied computing function. {@link #ofLocationUpdater(UpdaterMode,
 * MethodHandles.Lookup, Class, String, Object) ofLocationUpdater} selects a
 * named instance or static field, or an element of an array held in such a
 * field. {@link #ofArrayUpdater(UpdaterMode, Class, Object) ofArrayUpdater}
 * selects an element of an array passed directly to the updater, without
 * resolving a field or requiring a lookup.
 * <p>
 * The general {@code ofLocationUpdater} and {@code ofArrayUpdater} factories
 * require an explicit {@link UpdaterMode}. The
 * {@linkplain #ofStaticFieldUpdater(Class, String, Supplier) static-field},
 * {@linkplain #ofFieldUpdater(Class, String, Function) instance-field}, and
 * {@linkplain #ofArrayUpdater(Class, ReferenceArrayAccessor.Computer) reference-array}
 * convenience factories use {@link UpdaterMode#PUBLISH_ONCE}. The field
 * convenience factories check the caller's field access. This mode is the
 * usual choice when competing computations and discarded results are
 * acceptable. Both
 * {@code ofLocationUpdater} and {@code ofArrayUpdater} have a
 * <em>declaration-site</em> form that retains a computing function given when
 * the updater is created, and a <em>use-site</em> form that receives the
 * computing function on each access. Unlike
 * {@link CacheMode#COMPUTE_ONCE}, {@link UpdaterMode#SERIALIZED} allows a failed
 * computation to be retried. {@link UpdaterMode#PLAIN} has no cache counterpart.
 *
 * <h3 id="access-shapes">Locations and access shapes</h3>
 * The single abstract method of the functional-interface {@code type}
 * determines the location's coordinates. In use-site forms, a computing
 * function precedes those coordinates. The following shapes describe the
 * coordinates themselves, excluding that leading function. For
 * {@code ofLocationUpdater}, an instance field takes a receiver first and a simple
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
 * With no index parameters, {@code ofLocationUpdater} selects the field itself.
 * One or more trailing {@code int} parameters select an element of an array
 * field. For {@code ofArrayUpdater}, the first coordinate's erased parameter type
 * must be a concrete array type, followed by one or more {@code int} indices.
 * In both cases, the number of indices must not exceed the array's rank and
 * is subject to the usual Java and JVM method-parameter limits. Indices
 * address dimensions in order, from the outermost array to successively
 * inner arrays. No coordinate permutation or index expression is inferred.
 * The selected value may itself be an array: one index into an
 * {@code int[][][]} selects an {@code int[][]}, while three indices select an
 * {@code int}. {@code ofArrayUpdater} always selects an element, not the array
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
 * {@code Function<Point, Double>} is unsuitable by itself because its receiver
 * erases to {@code Object}. A non-generic subinterface that explicitly
 * redeclares {@code Double apply(Point)} can provide the needed concrete
 * receiver. A functional-interface {@code Class} token is rejected
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
 * default methods, including compiler-generated bridges, are supported.
 * An interface requiring multiple abstract methods with different erased
 * parameter types is not.
 *
 * <h3 id="computing-functions">Computing functions</h3>
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
 * <h4 id="ready-made-interfaces">Ready-made interfaces</h4>
 * These interfaces supply the computing function at the use site. Field
 * accessors use {@link #ofLocationUpdater(UpdaterMode, MethodHandles.Lookup, Class, String)
 * ofLocationUpdater}; direct array accessors use {@link #ofArrayUpdater(UpdaterMode, Class) ofArrayUpdater}.
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
 * <h3 id="uninitialized-locations">Uninitialized locations</h3>
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
 * <h3 id="updater-modes">Updater modes</h3>
 * In {@link UpdaterMode#PUBLISH_ONCE} mode, an invocation reads the selected location
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
 * In {@link UpdaterMode#SERIALIZED} mode, only one thread at a time invokes the
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
 * is slower than {@link UpdaterMode#PUBLISH_ONCE}.
 * <p>
 * In {@link UpdaterMode#PLAIN} mode, an invocation reads the selected location with
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
 * <h3 id="access-and-ownership">Access and storage ownership</h3>
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
 * For {@code ofLocationUpdater}, the lookup must authorize access to the accessor
 * interface, any use-site computing-function interface, the named field, and
 * any selected array type. A receiver restriction imposed by the lookup is
 * preserved. Accessing a static field through the updater
 * initializes its declaring class if necessary, following normal var-handle
 * semantics. {@code ofArrayUpdater} needs no caller-provided lookup; the array
 * parameter and indices identify the element without resolving a field.
 * Non-public functional interfaces and array component types are supported
 * by {@code ofArrayUpdater}. A null receiver, array, or intermediate array, or an
 * out-of-bounds index, fails before computation with the corresponding
 * {@code NullPointerException} or
 * {@code ArrayIndexOutOfBoundsException}. The array-reference path is resolved
 * once per invocation.
 *
 * @apiNote A cache strongly references its installed value for as long as its
 *          storage remains reachable. Suppliers, functions, and collections
 *          returned by the cache factories are by design not
 *          {@link Serializable}. Retained values may themselves be mutable.
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
 *           A cache's retained result is never replaced. An updater's
 *           published result is not replaced through a {@code PUBLISH_ONCE}
 *           or {@code SERIALIZED} updater. This may permit an implementation
 *           to constant-fold repeated accesses when it can determine a stable
 *           path to the storage and its result, perhaps from a
 *           {@code static final} field. Whether and when this optimization
 *           occurs is implementation-dependent. Eliding a storage read must
 *           preserve the applicable
 *           {@linkplain ##cache-thread-safety cache publication} or
 *           {@linkplain ##updater-modes updater mode} guarantee.
 *
 * @since 29
 */
@PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
public final class Lazy {

    // Suppresses default constructor, ensuring non-instantiability.
    private Lazy() { }

    /**
     * The policy for coordinating cache computation and handling its failures.
     *
     * @see Lazy##cache-modes Cache modes
     * @since 29
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    public enum CacheMode {
        /**
         * Invokes the computing function <em>at most once</em> per storage location
         * and permanently retains either its successful result or the fact that
         * computation failed.
         * Competing accesses by other threads wait for that invocation to finish.
         */
        COMPUTE_ONCE,

        /**
         * Permits concurrent computations and retries after failure, and
         * atomically installs and <em>publishes</em> one successful result.
         * All successful accesses to that storage location return the installed
         * result, including those that lose the race to install their result.
         */
        PUBLISH_ONCE
    }

    /**
     * The modes supported by lazy updaters.
     *
     * @see Lazy##updater-modes Updater modes
     * @since 29
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    public enum UpdaterMode {

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
     * Thrown when accessing a lazy value whose computation previously failed
     * with an {@link Error} in {@link CacheMode#COMPUTE_ONCE} mode.
     * <p>
     * The access that executes the failing computation throws the original
     * error unchanged. Each subsequent access, including one that waited for
     * the computation to finish, throws a new {@code InitializationError} with
     * no cause and a detail message that includes the class name of the
     * original error, when available. The failure state does not retain the
     * original error, and the computation is not retried. This error reports
     * the earlier failure; it does not indicate that the original error
     * condition has recurred.
     *
     * @see Lazy##cache-failures Computation failures
     * @since 29
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    public static final class InitializationError extends Error {
        @Serial
        private static final long serialVersionUID = -283725372652412245L;

        /**
         * Constructs an {@code InitializationError} with the specified detail
         * message and no cause.
         *
         * @param message the detail message, or {@code null} if there is none
         */
        public InitializationError(String message) {
            super(message, null);
        }
    }

    /**
     * {@return a new supplier that lazily computes a single value in
     *          {@link CacheMode#COMPUTE_ONCE} mode}
     *
     * @implSpec This method is equivalent to {@code ofScalarCache(CacheMode.COMPUTE_ONCE, computingFunction)}.
     *
     * @param computingFunction the function that computes the non-null value
     * @param <T> the type of the supplied value
     * @throws NullPointerException if {@code computingFunction} is {@code null}
     * @see #ofScalarCache(CacheMode, Supplier)
     */
    public static <T> Supplier<T> ofScalarCache(Supplier<? extends T> computingFunction) {
        return ofScalarCache(CacheMode.COMPUTE_ONCE, computingFunction);
    }

    /**
     * {@return a new supplier that lazily computes a single value using the given mode}
     * <p>
     * Its {@link Supplier#get() get} method returns the retained value, invoking
     * {@code computingFunction} if necessary, as described in
     * {@linkplain Lazy##cache-modes Initialization modes} and
     * {@linkplain Lazy##cache-failures Computation failures}.
     * <p>
     * The supplier has its own initially empty storage, even when the supplied
     * computing function is itself a lazy supplier. Retention of the computing
     * function is described in
     * {@linkplain Lazy##computing-function-retention Computing-function retention}.
     * Calls to {@code equals}, {@code hashCode}, and
     * {@code toString} on the returned supplier do not trigger computation.
     *
     * @param mode the initialization mode
     * @param computingFunction the function that computes the non-null value
     * @param <T> the type of the supplied value
     * @throws NullPointerException if {@code mode} or {@code computingFunction}
     *         is {@code null}
     */
    public static <T> Supplier<T> ofScalarCache(CacheMode mode,
            Supplier<? extends T> computingFunction) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(computingFunction);
        return LazySupport.ofSupplier(mode, computingFunction);
    }

    /**
     * {@return a new function that lazily computes a single value using an access-time
     *          context and the given mode}
     * <p>
     * The returned function has its own initially empty storage for a single
     * value. Its {@link Function#apply(Object) apply(context)} method uses
     * {@code computingFunction.apply(context)} if computation is necessary.
     * After a value is installed, all successful accesses return that value,
     * even if they supply different contexts. The context is an input to
     * computation, not a key for a map of cached values. A null context causes
     * {@code NullPointerException}, even when the value is already initialized.
     * In {@code COMPUTE_ONCE} mode only the computing thread's context is used.
     * In {@code PUBLISH_ONCE} mode
     * competing computations may use different contexts, but only one result
     * is installed. Failure behavior is described in
     * {@linkplain Lazy##cache-failures Computation failures}.
     * <p>
     * For example, each {@code Document} below has its own cache, while the
     * computing function need not capture the document:
     * {@snippet lang=java :
     * import java.lang.util.Lazy;
     * import java.util.function.Function;
     *
     * class Document {
     *     private final String text;
     *
     *     // A lazily derived property
     *     private final Function<Document, Integer> wordCount =
     *         Lazy.ofScalarCache(Lazy.CacheMode.PUBLISH_ONCE, Document::computeWordCount);
     *
     *     Document(String text) { this.text = text; }
     *
     *     int wordCount() { return wordCount.apply(this); }
     *
     *     private int computeWordCount() { return text.split("\\s+").length; }
     * }
     * }
     * <p>
     * Retention of the computing function is described in
     * {@linkplain Lazy##computing-function-retention Computing-function retention}.
     * Merely passing a context to {@code apply} does not retain it; the
     * computing function or a retained result may do so.
     * Its {@code equals}, {@code hashCode}, and {@code toString} methods do not
     * trigger computation.
     *
     * @apiNote A value class can hold the returned function in a final instance
     *          field while the function manages mutable lazy storage. A
     *          noncapturing computing function avoids capturing an incompletely
     *          initialized value object; the object is supplied later as the
     *          context. Such fields participate in value-object {@code ==}
     *          comparisons and inherited {@link Object#equals(Object)} behavior.
     *          Objects with otherwise equal application data may therefore
     *          compare unequal. Overriding {@code equals} and {@code hashCode}
     *          can define logical equality independently of the cache, but cannot
     *          change {@code ==}.
     *
     * @param mode the initialization mode
     * @param computingFunction the function that computes the non-null value
     * @param <C> the type of the context
     * @param <T> the type of the supplied value
     * @throws NullPointerException if {@code mode} or {@code computingFunction}
     *         is {@code null}
     * @see Lazy##function-composition Function composition
     */
    public static <C, T> Function<C, T> ofScalarCache(CacheMode mode,
            Function<? super C, ? extends T> computingFunction) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(computingFunction);
        return LazySupport.ofContext(mode, computingFunction);
    }

    /**
     * {@return an unmodifiable list of the given size whose elements are computed
     * lazily and independently using the given mode}
     * <p>
     * Each index has its own initially empty storage. Accessing an element
     * invokes {@code computingFunction.apply(index)} if necessary, with failure
     * and concurrency behavior as described for the given
     * {@linkplain Lazy##cache-modes initialization mode}. A failed
     * computation for one index does not prevent access to other indices.
     * A successful access always returns the value retained for that index.
     * <p>
     * The size is fixed at construction. {@code size()} and {@code isEmpty()},
     * obtaining an iterator, and creating {@code subList} or {@code reversed}
     * views do not compute elements. Views share the original element storage
     * and use the original indices for computation. Operations that inspect
     * elements, such as iteration, equality, hashing, searching, string
     * conversion, and copying to an array, may compute the elements they inspect.
     * Access with an out-of-range index throws {@code IndexOutOfBoundsException}
     * without invoking the computing function.
     * <p>
     * The list and its views are unmodifiable: mutator methods throw
     * {@code UnsupportedOperationException}, including when the operation would
     * have no effect. Null elements are not permitted, and searches for {@code null}
     * throw {@code NullPointerException}. The list and its {@code subList} and
     * {@code reversed} views implement {@link RandomAccess}. The list is not
     * serializable, even when empty. Its identity is unspecified and
     * should not be used for synchronization.
     *
     * @param mode the initialization mode for each element
     * @param size the size of the list
     * @param computingFunction the function that computes non-null elements
     * @param <T> the type of the elements
     * @throws IllegalArgumentException if {@code size} is negative
     * @throws NullPointerException if {@code mode} or {@code computingFunction}
     *         is {@code null}
     * @see Lazy##collection-properties Collection properties
     * @see Lazy##traversal-failures Computation failures during traversal
     * @see Lazy##streams Streams and spliterators
     * @see Lazy##computing-function-retention Computing-function retention
     */
    public static <T> List<T> ofListCache(CacheMode mode, int size,
            IntFunction<? extends T> computingFunction) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(computingFunction);
        return jdk.internal.util.LazyCollections.ofLazyList(mode, size, computingFunction);
    }

    /**
     * {@return an unmodifiable map with the given keys whose values are computed
     *          lazily and independently using the given mode}
     * <p>
     * The provided {@code keys} are copied at construction; subsequent changes to
     * {@code keys} do not affect the map. Copying does not compute values for the
     * returned map. If {@code keys} is a lazy set, copying its contents may compute
     * memberships in the input set and may fail if one of those computations fails.
     * <p>
     * If {@code keys} contains a {@code null} element, this method throws
     * {@link NullPointerException} during construction, regardless of the
     * initialization mode. Keys must not be modified in a way that changes their
     * equality or hash codes while used in the map.
     * The input set must determine membership using {@code equals}, or an
     * ordering consistent with {@code equals}; otherwise, the behavior of this
     * method is unspecified.
     * The iteration order is unspecified.
     * <p>
     * Each key has an associated, initially empty value storage. Accessing a mapped
     * value invokes {@code computingFunction.apply(key)} if necessary, with
     * failure and concurrency behavior as described for the given
     * {@linkplain Lazy##cache-modes initialization mode}. The argument
     * to the computing function is always the corresponding key retained from
     * {@code keys}, including when an access supplies a distinct but equal key.
     * A failed computation for one key does not prevent access to
     * values for other keys. Accessing an absent key does not invoke the function.
     * <p>
     * {@code size()}, {@code isEmpty()}, {@code containsKey}, and operations
     * on {@code keySet()} do not directly access the map's lazy value storage.
     * Obtaining {@code values()} or {@code entrySet()} does not access it either.
     * Traversing the entry-set view merely to yield entries does not itself
     * compute mapped values, whether by iterator, spliterator, or stream;
     * neither does converting the view to an array or reading an entry's key.
     * Reading an entry's value or iterating over the values computes the
     * accessed values if necessary.
     * Other operations that inspect values, such as equality, hashing,
     * string conversion, and {@code containsValue}, may compute the values
     * they inspect.
     * <p>
     * All views and entries share the map's value storage. Entries remain valid
     * after iteration or traversal has ended, regardless of how they were
     * obtained from the entry-set view. For as long as an entry is reachable,
     * its {@link Map.Entry#getValue() getValue} method accesses the same lazy
     * storage for its key, including any retained value or recorded permanent
     * failure, under the map's initialization mode. This applies even if the
     * map and its views are no longer otherwise reachable.
     * <p>
     * Operations that inspect an entry's value may compute it. In particular,
     * {@link Map.Entry#copyOf(Map.Entry) Map.Entry.copyOf(entry)} obtains the
     * value to create a detached entry, and may compute the value or report a
     * computation failure. Hashing or comparing entries, and user-supplied code
     * processing them, may also access their values.
     * <p>
     * The map, its views, and its entries are unmodifiable: mutator methods
     * throw {@code UnsupportedOperationException}, including when the operation
     * would have no effect. Null keys and values are not permitted; querying a
     * {@code null} key or searching for a {@code null} value throws
     * {@code NullPointerException}.
     * The map is not serializable, even when empty. Its identity is unspecified
     * and should not be used for synchronization.
     * <p>
     * For example, the following customers compare equal by ID, but have
     * different names. Looking up either customer computes the value from
     * {@code stored}, the key supplied when the map was created:
     * {@snippet lang=java :
     * import java.lang.util.Lazy;
     * import java.util.Set;
     *
     * class CustomerNames {
     *     record Customer(int id, String name) {
     *         @Override
     *         public boolean equals(Object obj) {
     *             return obj instanceof Customer other && id == other.id;
     *         }
     *
     *         @Override
     *         public int hashCode() {
     *             return Integer.hashCode(id);
     *         }
     *     }
     *
     *     public static void main(String[] args) {
     *         var stored = new Customer(7, "Alice");
     *         var lookup = new Customer(7, "Bob");
     *         var names = Lazy.ofMapCache(Lazy.CacheMode.COMPUTE_ONCE,
     *                 Set.of(stored), Customer::name);
     *
     *         System.out.println(names.get(lookup)); // Alice: computes from stored
     *         System.out.println(names.get(stored)); // Alice: returns the retained value
     *     }
     * }
     * }
     *
     * @param mode the initialization mode for each mapped value
     * @param keys the keys of the map
     * @param computingFunction the function that computes non-null mapped values
     * @param <K> the type of the keys
     * @param <V> the type of the mapped values
     * @throws NullPointerException if any argument is {@code null}, or
     *         {@code keys} contains a {@code null} element
     * @see Lazy##collection-properties Collection properties
     * @see Lazy##traversal-failures Computation failures during traversal
     * @see Lazy##streams Streams and spliterators
     * @see Lazy##computing-function-retention Computing-function retention
     */
    public static <K, V> Map<K, V> ofMapCache(
            CacheMode mode,
            Set<? extends K> keys,
            Function<? super K, ? extends V> computingFunction) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(computingFunction);
        Objects.requireNonNull(keys);
        return jdk.internal.util.LazyCollections.ofLazyMap(mode, keys, computingFunction);
    }

    /**
     * {@return an unmodifiable set whose membership is computed lazily and
     *          independently for each given candidate using the given mode}
     * <p>
     * The provided {@code elementCandidates} are copied at construction;
     * subsequent changes to the input set do not affect the returned set.
     * Copying does not compute memberships for the returned set. If
     * {@code elementCandidates} is a lazy set, copying its contents may compute
     * memberships in the input set and may fail if one of those computations fails.
     * <p>
     * If {@code elementCandidates} contains a {@code null} element, this method
     * throws {@link NullPointerException} during construction, regardless of the
     * initialization mode. Candidates must not be modified in a way that changes
     * their equality or hash codes while used by the returned set.
     * The input set must determine membership using
     * {@code equals}, or an ordering consistent with {@code equals}; otherwise,
     * the behavior of this method is unspecified. The iteration order is
     * unspecified.
     * <p>
     * Each candidate has its own initially uncomputed membership status.
     * Determining membership invokes {@code computingFunction.test(candidate)}
     * if necessary, with failure and concurrency behavior as described for the
     * given {@linkplain Lazy##cache-modes initialization mode}. A
     * retained {@code true} result includes the candidate in the returned set;
     * a retained {@code false} result excludes it. Both results are retained,
     * so an established membership status never changes. In
     * {@code PUBLISH_ONCE} mode, competing evaluations may produce different
     * results, but all successful accesses use the one retained result for
     * that candidate. A failed computation for one candidate does not prevent
     * determining membership for other candidates. Failure behavior is
     * described in {@linkplain Lazy##cache-failures Computation failures}.
     * <p>
     * A {@code contains} call directly computes only the matching retained
     * candidate's membership, if necessary. The argument to the computing
     * function is always that candidate from {@code elementCandidates}, including
     * when the queried object is distinct but equal. Querying a non-null object
     * that is not equal to any candidate returns {@code false} without invoking
     * the computing function. Querying {@code null} throws
     * {@code NullPointerException}.
     * <p>
     * Obtaining an iterator does not compute membership. Its {@code hasNext}
     * and {@code next} methods may compute membership for multiple candidates
     * while searching for the next member. Determining the set's size or hash
     * code requires the membership status of every candidate, and may therefore
     * compute all remaining statuses. Operations such as {@code isEmpty},
     * equality, string conversion, and copying to an array may also compute
     * membership for some or all candidates. These operations can fail if a
     * membership computation they require fails.
     * <p>
     * The set is unmodifiable: mutator methods throw
     * {@linkplain UnsupportedOperationException}, including when the operation would
     * have no effect. The set is not serializable, even when empty. Its identity
     * is unspecified and should not be used for synchronization.
     *
     * @apiNote The example in {@link #ofMapCache(CacheMode, Set, Function)} illustrates
     *          how equal lookup objects can have different data. The same rule
     *          applies here: the predicate examines the retained candidate.
     *
     * @param mode the initialization mode for each candidate's membership
     * @param elementCandidates the candidates for membership in the returned set
     * @param computingFunction the predicate that determines candidate membership
     * @param <E> the type of the elements
     * @throws NullPointerException if any argument is {@code null}, or
     *         {@code elementCandidates} contains a {@code null} element
     * @see Lazy##collection-properties Collection properties
     * @see Lazy##traversal-failures Computation failures during traversal
     * @see Lazy##streams Streams and spliterators
     * @see Lazy##computing-function-retention Computing-function retention
     */
    public static <E> Set<E> ofSetCache(
            CacheMode mode,
            Set<? extends E> elementCandidates,
            Predicate<? super E> computingFunction) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(elementCandidates);
        Objects.requireNonNull(computingFunction);
        return jdk.internal.util.LazyCollections.ofLazySet(mode, elementCandidates, computingFunction);
    }

    /**
     * Returns a lazy updater for a static field accessible to the caller.
     * This convenience method uses {@link UpdaterMode#PUBLISH_ONCE} and the
     * supplied field as storage. Its result is a {@link Supplier} whose
     * {@link Supplier#get() get()} method accesses that field; the computing
     * function is invoked only when the field has its reserved Java default
     * value. The field must be static and non-final. Access is checked using a
     * lookup for the invoking class. If that lookup cannot resolve {@code owner}
     * by name to the supplied {@code Class}, it is scoped to {@code owner}
     * using {@link MethodHandles.Lookup#in(Class) Lookup.in} to preserve the
     * supplied class's loader identity. A caller may select its own private
     * static field or one in a nestmate. Access to an unrelated private field
     * is denied. A hidden class cannot be selected, since its name cannot be
     * resolved by a lookup. A caller needing a different mode or explicit lookup
     * authority can use
     * {@link #ofLocationUpdater(UpdaterMode, MethodHandles.Lookup, Class, String, Object)}.
     * This method is caller-sensitive: a helper that invokes it supplies the
     * helper's access privileges. Once the updater is created, calling its
     * {@code get} method does not repeat the caller access check.
     * A method-handle invocation may use a VM-generated nestmate as its caller;
     * that class need not inherit protected access held by the class that
     * created the method handle. For such access, use the general factory with
     * an explicit lookup.
     *
     * <p>The type parameter {@code T} is not available at run time. The caller
     * must ensure that {@code computing} produces values compatible with the
     * field and that any preexisting field value can be used as {@code T}.
     * Incompatible computed values fail before publication, as described in
     * {@linkplain ##computing-functions Computing functions}.
     *
     * @implSpec This method obtains a lookup for its caller and uses
     *           {@code callerLookup.in(owner)} if the caller lookup cannot
     *           resolve {@code owner} to the supplied {@code Class}. It adapts
     *           {@code computing} to a {@link StaticObjectFieldSupplier} and
     *           delegates to {@code ofLocationUpdater(UpdaterMode.PUBLISH_ONCE,
     *           selectedLookup, StaticObjectFieldSupplier.class,
     *           owner.getName() + "." + name, adaptedComputingFunction)}.
     *           The returned updater is viewed as a {@code Supplier<T>}.
     *
     * @param owner the type through which the static field is selected
     * @param name the simple name of the field
     * @param computing the function that computes a non-default field value
     * @param <T> the result type expected by the caller
     * @return a supplier backed by the selected static field
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalCallerException if there is no caller frame
     * @throws IllegalArgumentException if {@code name} is empty or contains a
     *         dot, the field cannot be accessed, is not a suitable mutable
     *         static location, or the required access mode is unsupported
     * @see #ofLocationUpdater(UpdaterMode, MethodHandles.Lookup, Class, String, Object)
     * @since 29
     */
    @CallerSensitive
    public static <T> Supplier<T> ofStaticFieldUpdater(Class<?> owner, String name, Supplier<T> computing) {
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
        MethodHandles.Lookup lookup = SharedSecrets.getJavaLangInvokeAccess()
                .lookupForLazyCaller(caller);
        try {
            if (lookup.findClass(owner.getName()) != owner) {
                lookup = lookup.in(owner);
            }
        } catch (ClassNotFoundException | IllegalAccessException e) {
            lookup = lookup.in(owner);
        }
        StaticObjectFieldSupplier updater = ofLocationUpdater(
                UpdaterMode.PUBLISH_ONCE,
                lookup,
                StaticObjectFieldSupplier.class,
                owner.getName() + "." + name,
                (StaticObjectFieldSupplier) computing::get);
        @SuppressWarnings("unchecked")
        Supplier<T> result = (Supplier<T>) (Supplier<?>) updater;
        return result;
    }

    /**
     * Returns a lazy updater for an instance field accessible to the caller.
     * This convenience method uses {@link UpdaterMode#PUBLISH_ONCE} and the
     * supplied field as storage. Its result is a {@link Function} whose
     * {@link Function#apply(Object) apply} method accesses the field of its
     * receiver, invoking {@code computing} only when that field has its
     * reserved Java default value. The field must be non-static and
     * non-final. Each receiver has an independent stored value.
     *
     * <p>Access is checked using a lookup for the invoking class. If that
     * lookup cannot resolve {@code owner} by name to the supplied
     * {@code Class}, it is scoped to {@code owner} using
     * {@link MethodHandles.Lookup#in(Class) Lookup.in} to preserve the
     * supplied class's loader identity. A caller may select its own private
     * field or one in a nestmate. Access to an unrelated private field is
     * denied. This method is caller-sensitive: a helper that invokes it
     * supplies the helper's access privileges. Once the updater is created,
     * calling its {@code apply} method does not repeat the caller access
     * check. A caller needing another mode or explicit lookup authority can
     * use {@link #ofLocationUpdater(UpdaterMode, MethodHandles.Lookup, Class,
     * String, Object)} with a concrete functional-interface type.
     * A method-handle invocation may use a VM-generated nestmate as its
     * caller; that class need not inherit protected access held by the class
     * that created the method handle. For such access, use the general
     * factory with an explicit lookup.
     *
     * <p>For example:
     * {@snippet lang=java :
     * import java.lang.util.Lazy;
     * import java.util.function.Function;
     *
     * class Point {
     *     private final double x, y;
     *     private Double radius;
     *     private static final Function<Point, Double> RADIUS =
     *             Lazy.ofFieldUpdater(Point.class, "radius",
     *                     point -> Math.hypot(point.x, point.y));
     *
     *     Point(double x, double y) { this.x = x; this.y = y; }
     *     double radius() { return RADIUS.apply(this); }
     * }
     * }
     *
     * <p>The type parameter {@code T} is not available at run time. The caller
     * must ensure that {@code computing} produces values compatible with the
     * field and that any preexisting field value can be used as {@code T}.
     * A primitive field value is returned boxed. Incompatible computed
     * values fail before publication, as described in
     * {@linkplain ##computing-functions Computing functions}.
     *
     * @implSpec This method obtains a lookup for its caller, retaining it
     *           when it resolves {@code owner} to the supplied {@code Class}
     *           and otherwise using {@code callerLookup.in(owner)}. It
     *           delegates to the instance-field updater implementation with
     *           {@link UpdaterMode#PUBLISH_ONCE} and an explicit owner type.
     *
     * @param owner the type through which the instance field is selected
     * @param name the simple name of the field
     * @param computing the function that computes a non-default field value
     * @param <R> the receiver type
     * @param <T> the result type expected by the caller
     * @return a function backed by the selected instance field
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalCallerException if there is no caller frame
     * @throws IllegalArgumentException if {@code name} is empty or contains a
     *         dot, the field cannot be accessed, is not a suitable mutable
     *         instance location, or the required access mode is unsupported
     * @see #ofStaticFieldUpdater(Class, String, Supplier)
     * @see #ofLocationUpdater(UpdaterMode, MethodHandles.Lookup, Class, String, Object)
     * @since 29
     */
    @CallerSensitive
    public static <R, T> Function<R, T> ofFieldUpdater(
            Class<R> owner, String name, Function<R, T> computing) {
        // Keep caller identification in this entry point for the same reason
        // as in ofStaticFieldUpdater.
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
        MethodHandles.Lookup lookup = SharedSecrets.getJavaLangInvokeAccess()
                .lookupForLazyCaller(caller);
        try {
            if (lookup.findClass(owner.getName()) != owner) {
                lookup = lookup.in(owner);
            }
        } catch (ClassNotFoundException | IllegalAccessException e) {
            lookup = lookup.in(owner);
        }
        return LazyUpdaterSupport.ofInstanceField(
                UpdaterMode.PUBLISH_ONCE, lookup, owner, name, computing);
    }

    /**
     * Returns a lazy updater for elements of a directly supplied reference
     * array. This convenience method uses {@link UpdaterMode#PUBLISH_ONCE} and
     * returns a function whose {@link ReferenceArrayAccessor.Computer#apply(Object[], int)
     * apply} method selects an element by index. The computing function is
     * invoked only when that element is {@code null}. Different arrays and
     * different elements have independent storage. The supplied
     * {@code elementType} identifies the required array and result types;
     * primitive and {@code void} element types are not supported by this
     * overload. Use {@link #ofArrayUpdater(UpdaterMode, Class, Object)} with a
     * concrete primitive-array interface for primitive elements.
     *
     * <p>For example:
     * {@snippet lang=java :
     * import java.lang.util.Lazy;
     *
     * class Names {
     *     private final String[] entries = new String[8];
     *     private static final Lazy.ReferenceArrayAccessor.Computer<String> ENTRY =
     *             Lazy.ofArrayUpdater(String.class,
     *                     (array, index) -> "entry-" + index);
     *
     *     String entry(int index) { return ENTRY.apply(entries, index); }
     * }
     * }
     *
     * @implSpec This method adapts the computing function to an
     *           {@code Object[]}-based updater and delegates to the array
     *           updater implementation with {@link UpdaterMode#PUBLISH_ONCE}.
     *
     * @param elementType the reference array's element type
     * @param computing the function that computes a non-null element value
     * @param <T> the array element type
     * @return a function backed by elements of arrays supplied on access
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalArgumentException if {@code elementType} is primitive or
     *         {@code void}, or the required access mode is unsupported
     * @see #ofArrayUpdater(UpdaterMode, Class, Object)
     * @since 29
     */
    public static <T> ReferenceArrayAccessor.Computer<T> ofArrayUpdater(
            Class<T> elementType, ReferenceArrayAccessor.Computer<T> computing) {
        Objects.requireNonNull(elementType);
        Objects.requireNonNull(computing);
        return LazyUpdaterSupport.ofReferenceArray(
                UpdaterMode.PUBLISH_ONCE, elementType, computing);
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
     * import java.lang.util.Lazy;
     * import java.lang.invoke.MethodHandles;
     *
     * import static java.lang.util.Lazy.UpdaterMode.PUBLISH_ONCE;
     *
     * class Point {
     *     private final double x, y;
     *     private Double radius;
     *
     *     Point(double x, double y) { this.x = x; this.y = y; }
     *
     *     interface Radius { Double getOrCompute(Point point); }
     *
     *     private static final Radius LAZY_RADIUS = Lazy.ofLocationUpdater(
     *             PUBLISH_ONCE, MethodHandles.lookup(),
     *             Radius.class, "radius",
     *             point -> Math.hypot(point.x, point.y));
     *
     *     double radius() { return LAZY_RADIUS.getOrCompute(this); }
     * }
     * }
     * The boxed result permits a computed radius of positive zero, because
     * {@code null}, rather than zero, is the reserved uninitialized value.
     * The {@linkplain #ofLocationUpdater(UpdaterMode, MethodHandles.Lookup,
     * Class, String) use-site overload} can instead use a concrete
     * {@link ReferenceFieldAccessor} subinterface that fixes both the receiver
     * and the value type, as shown in the next factory's example.
     *
     * <p>For a static field, the updater has no receiver:
     * {@snippet lang=java :
     * import java.lang.util.Lazy;
     * import java.lang.invoke.MethodHandles;
     * import java.util.function.IntSupplier;
     *
     * import static java.lang.util.Lazy.UpdaterMode.PUBLISH_ONCE;
     *
     * class Answer {
     *     private static int value;
     *
     *     private static final IntSupplier LAZY_VALUE =
     *             Lazy.ofLocationUpdater(PUBLISH_ONCE, MethodHandles.lookup(),
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
     * import java.lang.util.Lazy;
     * import java.lang.invoke.MethodHandles;
     * import java.util.function.IntUnaryOperator;
     *
     * import static java.lang.util.Lazy.UpdaterMode.PUBLISH_ONCE;
     *
     * class Values {
     *     private static final int[] entries = new int[8];
     *
     *     private static final IntUnaryOperator LAZY_ENTRY =
     *             Lazy.ofLocationUpdater(PUBLISH_ONCE, MethodHandles.lookup(),
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
     * @see #ofLocationUpdater(UpdaterMode, MethodHandles.Lookup, Class, String)
     * @see #ofArrayUpdater(UpdaterMode, Class, Object)
     */
    public static <T> T ofLocationUpdater(UpdaterMode mode,
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
     * import java.lang.util.Lazy;
     * import java.lang.invoke.MethodHandles;
     *
     * import static java.lang.util.Lazy.UpdaterMode.PUBLISH_ONCE;
     *
     * class Point {
     *     private final double x, y;
     *     private Double radius;
     *
     *     Point(double x, double y) { this.x = x; this.y = y; }
     *
     *     interface Radius extends Lazy.ReferenceFieldAccessor<Point, Double> { }
     *
     *     private static final Radius LAZY_RADIUS =
     *             Lazy.ofLocationUpdater(PUBLISH_ONCE, MethodHandles.lookup(),
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
     * import java.lang.util.Lazy;
     * import java.lang.invoke.MethodHandles;
     *
     * import static java.lang.util.Lazy.UpdaterMode.PUBLISH_ONCE;
     *
     * class Answer {
     *     private static int value;
     *
     *     private static final Lazy.StaticIntFieldAccessor LAZY_VALUE =
     *             Lazy.ofLocationUpdater(PUBLISH_ONCE, MethodHandles.lookup(),
     *                     Lazy.StaticIntFieldAccessor.class, Answer.class.getName() + ".value");
     *
     *     static int value() { return LAZY_VALUE.getOrCompute(() -> 42); }
     * }
     * }
     *
     * <p>For an element of an instance array field, the updater takes the
     * owner followed by the index:
     * {@snippet lang=java :
     * import java.lang.util.Lazy;
     * import java.lang.invoke.MethodHandles;
     *
     * import static java.lang.util.Lazy.UpdaterMode.PUBLISH_ONCE;
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
     *             Lazy.ofLocationUpdater(PUBLISH_ONCE, MethodHandles.lookup(),
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
     * @see #ofLocationUpdater(UpdaterMode, MethodHandles.Lookup, Class, String, Object)
     */
    public static <T> T ofLocationUpdater(UpdaterMode mode,
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
     * import java.lang.util.Lazy;
     * import java.lang.util.Lazy.IntArrayAccessor;
     *
     * import static java.lang.util.Lazy.UpdaterMode.PUBLISH_ONCE;
     *
     * class Values {
     *     private final int[] entries = new int[8];
     *
     *     private static final IntArrayAccessor.Computer LAZY_ENTRY =
     *             Lazy.ofArrayUpdater(PUBLISH_ONCE,
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
     * import java.lang.util.Lazy;
     *
     * import static java.lang.util.Lazy.UpdaterMode.PUBLISH_ONCE;
     *
     * class Matrix {
     *     private final int[][] entries = new int[2][3];
     *
     *     interface Cell { int getOrCompute(int[][] array, int row, int column); }
     *
     *     private static final Cell LAZY_CELL =
     *             Lazy.ofArrayUpdater(PUBLISH_ONCE,
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
     * @see #ofArrayUpdater(UpdaterMode, Class)
     */
    public static <T> T ofArrayUpdater(UpdaterMode mode, Class<T> type, T computingFunction) {
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
     * import java.lang.util.Lazy;
     * import java.lang.util.Lazy.IntArrayAccessor;
     *
     * import static java.lang.util.Lazy.UpdaterMode.PUBLISH_ONCE;
     *
     * class Values {
     *     private final int[] entries = new int[8];
     *
     *     private static final IntArrayAccessor LAZY_ENTRY =
     *             Lazy.ofArrayUpdater(PUBLISH_ONCE, IntArrayAccessor.class);
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
     * import java.lang.util.Lazy;
     *
     * import static java.lang.util.Lazy.UpdaterMode.PUBLISH_ONCE;
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
     *             Lazy.ofArrayUpdater(PUBLISH_ONCE, Cell.class);
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
     * @see #ofArrayUpdater(UpdaterMode, Class, Object)
     */
    public static <T> T ofArrayUpdater(UpdaterMode mode, Class<T> type) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(type);
        return LazyUpdaterSupport.ofArray(mode, type);
    }

    // Ready-made interfaces

    /**
     * A non-generic supplier shape for a static field value. This interface
     * provides the concrete functional-interface token used by
     * {@link #ofStaticFieldUpdater(Class, String, Supplier)} when it delegates to
     * {@code ofLocationUpdater}. It can also be supplied directly to that
     * factory when an {@code Object}-valued result is appropriate.
     *
     * @see Lazy##uninitialized-locations Uninitialized locations
     * @see Lazy##updater-modes Updater modes
     * @since 29
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    @FunctionalInterface
    public interface StaticObjectFieldSupplier extends Supplier<Object> { }

    /**
     * An updater for a named static {@code int} field. The computing
     * function is supplied at each access.
     *
     * @see Lazy##computing-functions Computing functions
     * @see Lazy##uninitialized-locations Uninitialized locations
     * @see Lazy##updater-modes Updater modes
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
     * @see Lazy##computing-functions Computing functions
     * @see Lazy##uninitialized-locations Uninitialized locations
     * @see Lazy##updater-modes Updater modes
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
     * {@link #ofLocationUpdater(UpdaterMode, MethodHandles.Lookup, Class, String)}. The
     * field's erased type must be a reference type assignable to {@code V}.
     * A parameterized or generic-array value type must match the field's
     * declared generic type. A computed {@code null} is rejected because it
     * denotes an uninitialized field.
     *
     * @param <V> the field value type or a reference supertype, subject to the
     *            generic-type matching rule above
     * @see Lazy##computing-functions Computing functions
     * @see Lazy##uninitialized-locations Uninitialized locations
     * @see Lazy##updater-modes Updater modes
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
     * {@link #ofLocationUpdater(UpdaterMode, MethodHandles.Lookup, Class, String)}. The generic
     * receiver type must resolve to a class; the raw base interface cannot
     * identify a field owner.
     *
     * @param <R> the receiver type
     * @see Lazy##computing-functions Computing functions
     * @see Lazy##uninitialized-locations Uninitialized locations
     * @see Lazy##updater-modes Updater modes
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
     * {@link #ofLocationUpdater(UpdaterMode, MethodHandles.Lookup, Class, String)}. The generic
     * receiver type must resolve to a class; the raw base interface cannot
     * identify a field owner.
     *
     * @param <R> the receiver type
     * @see Lazy##computing-functions Computing functions
     * @see Lazy##uninitialized-locations Uninitialized locations
     * @see Lazy##updater-modes Updater modes
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
     * {@link #ofLocationUpdater(UpdaterMode, MethodHandles.Lookup, Class, String)}. The
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
     * @see Lazy##computing-functions Computing functions
     * @see Lazy##uninitialized-locations Uninitialized locations
     * @see Lazy##updater-modes Updater modes
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
     * @see Lazy##computing-functions Computing functions
     * @see Lazy##uninitialized-locations Uninitialized locations
     * @see Lazy##updater-modes Updater modes
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
     * @see Lazy##computing-functions Computing functions
     * @see Lazy##uninitialized-locations Uninitialized locations
     * @see Lazy##updater-modes Updater modes
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
     * {@code Class} token to {@link #ofArrayUpdater(UpdaterMode, Class)}. A computed
     * {@code null} is rejected because it denotes an uninitialized element.
     *
     * @param <E> the array element type
     * @see Lazy##computing-functions Computing functions
     * @see Lazy##uninitialized-locations Uninitialized locations
     * @see Lazy##updater-modes Updater modes
     * @since 29
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    @FunctionalInterface
    public interface ReferenceArrayAccessor<E> {
        /**
         * A function of a reference array and index. It can supply the
         * computation to a use-site {@code ReferenceArrayAccessor}, or serve
         * as the updater returned by {@link Lazy#ofArrayUpdater(Class,
         * ReferenceArrayAccessor.Computer) ofArrayUpdater}.
         *
         * @param <E> the array element type
         * @since 29
         */
        @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
        @FunctionalInterface
        interface Computer<E> {
            /**
             * Applies this function to the array and index. A computation
             * supplied to a use-site accessor computes an element value;
             * a function returned by {@link Lazy#ofArrayUpdater(Class,
             * ReferenceArrayAccessor.Computer) ofArrayUpdater} returns the
             * selected element, computing it when uninitialized.
             *
             * @param array the array
             * @param index the selected index
             * @return the computed or selected value
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
