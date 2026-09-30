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

package java.util;

import java.io.Serial;
import java.io.Serializable;
import java.lang.invoke.VarHandle;
import java.util.function.*;

import jdk.internal.javac.PreviewFeature;
import jdk.internal.util.LazySupport;

// Todo: Close semantics ... AutoClosable

/**
 * Factory methods for lazily computing and retaining values.
 * <p>
 * A lazy value is computed when it is first needed and retained for subsequent
 * access. These factories capture a <em>computing function</em> for deferred
 * computation and provide storage for its result. The access operation invokes
 * the computing function if necessary and returns the retained value.
 * A retained value is never replaced.
 * <p>
 * The factories provide several ways to access lazy values:
 * <ul>
 * <li>{@link #ofScalar(Supplier)} and {@link #ofScalar(Mode, Supplier)} return a
 *     {@linkplain Supplier} that holds a single value.</li>
 * <li>{@link #ofScalar(Mode, Function)} returns a {@link Function} that holds
 *     a single value and accepts a context when accessed. This permits a computing
 *     function to use instance state without capturing the instance.</li>
 * <li>{@link #ofList(Mode, int, IntFunction)},
 *     {@link #ofMap(Mode, Set, Function)}, and {@link #ofSet(Mode, Set, Predicate)}
 *     return unmodifiable collections whose elements, mapped values, or memberships
 *     are computed independently.</li>
 * </ul>
 * <p>
 * A method reference applicable to both {@link #ofScalar(Mode, Supplier)} and
 * {@link #ofScalar(Mode, Function)} can make overload selection ambiguous.
 * A lambda with the intended parameter count or a cast to the intended
 * functional interface selects the desired overload.
 *
 * <p>
 * For example, a service can be created on its first use:
 * {@snippet lang = java:
 * private static final Supplier<Service> SERVICE = LazyCaches.ofScalar(Service::new);
 *
 * static Service service() {
 *     return SERVICE.get();
 * }
 * }
 *
 * <h2 id="initialization-modes">Initialization modes</h2>
 * Each factory specifies an {@link Mode initialization mode}, either
 * explicitly or by default. The mode applies independently to each lazy value.
 * In both modes, computing functions are invoked synchronously on the thread
 * performing the lazy access. A collection's computing function may be invoked
 * concurrently for different elements, keys, or candidates, even in
 * {@code COMPUTE_ONCE} mode. That mode prevents concurrent or repeated
 * computation of the same value, not concurrent computation of different values.
 * <p>
 * {@link Mode#COMPUTE_ONCE} is intended for <em>initialization</em>,
 * including computations with side effects that must not be repeated. At-most-once
 * invocation of the computing function is guaranteed for each value. A
 * thread that first needs the value invokes the function on that thread;
 * competing threads wait for that invocation to finish. If it succeeds, its
 * result is retained. If it fails, the failure is permanent and no further
 * invocation is attempted. A value that is never accessed need not be computed.
 * The {@link #ofScalar(Supplier)} factory uses this mode by default.
 * <p>
 * {@link Mode#PUBLISH_ONCE} is intended for <em>memoization</em>,
 * where computing a value more than once is acceptable. Competing threads may
 * each invoke the computing function. A non-null result is installed atomically
 * only if no result has already been installed. Every access that completes
 * normally returns the installed value, including accesses whose computations
 * produced a different result. Results that are not installed are discarded;
 * this API does not close them or otherwise release resources they hold.
 * Failures are not retained, so later accesses may retry computation. Computing
 * functions used with this mode should tolerate concurrent and repeated
 * invocation and should avoid side effects that require at-most-once execution.
 *
 * <h2 id="failures">Computation failures</h2>
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
 * Returning {@code null} is treated as a computation failure with a
 * {@link NullPointerException}. In {@code COMPUTE_ONCE} mode, recursive access
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
 * stage, or an element's {@code equals} or {@code hashCode} method. Failures from
 * such code are handled according to the operation's contract; their propagation
 * does not itself record an additional initialization failure. Any lazy accesses
 * performed by such code, and any throwable that escapes a computing function,
 * remain subject to their applicable initialization and failure rules.
 *
 * <h2 id="collection-properties">Collection properties</h2>
 * Each invocation of {@link #ofList(Mode, int, IntFunction) ofList},
 * {@link #ofMap(Mode, Set, Function) ofMap}, or
 * {@link #ofSet(Mode, Set, Predicate) ofSet} provides independent, initially
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
 * <h2 id="traversal-failures">Computation failures during traversal</h2>
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
 * itself, its action is not invoked. If {@link Spliterator#trySplit() trySplit} fails because of a
 * computation failure, the original spliterator retains coverage of all
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
 * <h2 id="streams">Streams and spliterators</h2>
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
 * <tr><th scope="row">A set returned by {@code ofSet}</th>
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
 * <h2 id="thread-safety">Thread safety and memory consistency</h2>
 * Lazy storage supports concurrent access in both modes. Installation of a value
 * by a lazy access has the memory effects of a
 * {@linkplain VarHandle.AccessMode#SET_RELEASE release write} of its storage;
 * observation of the retained value has the memory effects of an
 * {@linkplain VarHandle.AccessMode#GET_ACQUIRE acquire read} of its storage.
 * In particular, actions in the installing thread preceding installation
 * {@linkplain java.util.concurrent##MemoryVisibility <em>happen-before</em>}
 * actions following a successful access that returns that value. This guarantee
 * applies in both modes. A successful access whose computed result is discarded
 * still observes the publication of the installed value. In {@code PUBLISH_ONCE}
 * mode, actions performed by computations that fail or produce discarded results
 * have no publication guarantee to other threads through accesses to the lazy
 * value.
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
 * computed value, or concurrent access to
 * mutable state used by the computing function, thread-safe. Such mutations and
 * accesses require their own coordination. Lazy access does not provide cancellation or
 * a timeout. Waiting for initialization is not interruptible. If a thread is
 * already interrupted when waiting begins, or is interrupted while waiting,
 * the waiting mechanism preserves that interruption: the thread's interrupted
 * status is set when the access completes, whether normally or abruptly.
 * This guarantee does not constrain the computing function's own handling of
 * interruption, which may change the interrupted status. A computation that
 * blocks indefinitely can prevent access to its value indefinitely.
 *
 * <h2 id="performance">Performance</h2>
 * The value retained in a particular lazy storage location never changes after
 * installation. If a JVM implementation can determine that location and its
 * installed value, it may elide subsequent reads and use the previously observed
 * value, provided the {@linkplain ##thread-safety publication guarantee} is
 * preserved for every successful access. We call this optimization
 * <em>constant folding</em>.
 * <p>
 * Such optimization opportunities may arise when there is a direct reference
 * from a {@code static final} field to a lazy supplier, or a chain of
 * <em>trusted fields</em> (such as {@code static final} fields,
 * {@linkplain Record record} fields, or final instance fields in hidden classes)
 * leading to a lazy storage location.
 * <p>
 * Lazily initializing an array reference does not make its elements lazy or
 * immutable. Use {@link #ofList(Mode, int, IntFunction) ofList} when elements
 * should be computed independently.
 *
 * <h2 id="function-composition">Function composition</h2>
 * Functions returned by {@code ofScalar(Mode, Function)} support the
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
 * <h2 id="computing-function-retention">Computing-function retention</h2>
 * Storage provided by {@linkplain #ofScalar(Supplier) ofScalar},
 * {@linkplain #ofScalar(Mode, Function) ofScalar},
 * {@link #ofList(Mode, int, IntFunction) ofList},
 * {@linkplain #ofMap(Mode, java.util.Set, Function) ofMap}, and
 * {@linkplain #ofSet(Mode, java.util.Set, Predicate) ofSet} strongly retains its
 * computing function while any element, value or membership still requires
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
 * <h2 id="nullability">Nullability</h2>
 * Providing a {@code null} argument to any of the factories in this class causes
 * a {@link NullPointerException} to be thrown.
 * <p>
 * If a computing function returns {@code null}, the result is treated as a
 * {@linkplain ##failures computation failure} with a {@code NullPointerException}.
 * In {@code COMPUTE_ONCE} mode, the computing thread throws a
 * {@code NoSuchElementException} with that exception as its cause, and the failure
 * is permanent. In {@code PUBLISH_ONCE} mode, the {@code NullPointerException}
 * is thrown directly and subsequent accesses may retry. An {@link Optional}
 * can be used to represent an absent value.
 *
 * @apiNote A lazy value strongly references its installed value for as long as
 *          the storage remains reachable. Suppliers and functions returned by this class,
 *          and the collections returned by its factories, are by design not
 *          {@link Serializable}. The collection contents, and values returned by
 *          suppliers, may themselves be mutable.
 *
 * @since 29
 */
@PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
public final class LazyCaches {

    private LazyCaches() { }

    /**
     * The policy for coordinating computation and handling computation failures.
     *
     * @see LazyCaches##initialization-modes Initialization modes
     * @since 29
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    public enum Mode {
        /**
         * Invokes the computing function <em>at most once</em> per value and permanently
         * retains either its non-null result or the fact that computation failed.
         * Competing accesses wait for that invocation to finish.
         */
        COMPUTE_ONCE,

        /**
         * Permits concurrent computations and retries after failure, and
         * atomically installs and <em>publishes</em> one non-null result. All successful
         * accesses return the installed value, including those that lose the race to
         * install their result.
         */
        PUBLISH_ONCE
    }

    /**
     * Thrown when accessing a lazy value whose computation previously failed
     * with an {@link Error} in {@link Mode#COMPUTE_ONCE} mode.
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
     * @see LazyCaches##failures Computation failures
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
     *          {@link Mode#COMPUTE_ONCE} mode}
     *
     * @implSpec This method is equivalent to {@code ofScalar(Mode.COMPUTE_ONCE, computingFunction)}.
     *
     * @param computingFunction the function that computes the non-null value
     * @param <T> the type of the supplied value
     * @see #ofScalar(Mode, Supplier)
     */
    public static <T> Supplier<T> ofScalar(Supplier<? extends T> computingFunction) {
        return ofScalar(Mode.COMPUTE_ONCE, computingFunction);
    }

    /**
     * {@return a new supplier that lazily computes a single value using the given mode}
     * <p>
     * Its {@link Supplier#get() get} method returns the retained value, invoking
     * {@code computingFunction} if necessary, as described in
     * {@linkplain LazyCaches##initialization-modes Initialization modes} and
     * {@linkplain LazyCaches##failures Computation failures}.
     * <p>
     * The supplier has its own initially empty storage, even when the supplied
     * computing function is itself a lazy supplier. Retention of the computing
     * function is described in
     * {@linkplain LazyCaches##computing-function-retention Computing-function retention}.
     * Calls to {@code equals}, {@code hashCode}, and
     * {@code toString} on the returned supplier do not trigger computation.
     *
     * @param mode the initialization mode
     * @param computingFunction the function that computes the non-null value
     * @param <T> the type of the supplied value
     */
    public static <T> Supplier<T> ofScalar(Mode mode,
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
     * {@linkplain LazyCaches##failures Computation failures}.
     * <p>
     * For example, each {@code Document} below has its own cache, while the
     * computing function need not capture the document:
     * {@snippet lang = java:
     * class Document {
     *     private final String text;
     *
     *     // A lazily derived property
     *     private final Function<Document, Integer> wordCount =
     *         LazyCaches.ofScalar(LazyCaches.Mode.PUBLISH_ONCE,
     *                 Document::computeWordCount);
     *
     *     Document(String text) { this.text = text; }
     *
     *     int wordCount() { return wordCount.apply(this); }
     *
     *     private int computeWordCount() { return text.split("\\s+").length; }
     * }
     *}
     * <p>
     * Retention of the computing function is described in
     * {@linkplain LazyCaches##computing-function-retention Computing-function retention}.
     * The returned function does not
     * retain the context except through the computing function or its result.
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
     * @see LazyCaches##function-composition Function composition
     */
    public static <C, T> Function<C, T> ofScalar(Mode mode,
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
     * {@linkplain LazyCaches##initialization-modes initialization mode}. A failed
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
     * @see LazyCaches##collection-properties Collection properties
     * @see LazyCaches##traversal-failures Computation failures during traversal
     * @see LazyCaches##streams Streams and spliterators
     * @see LazyCaches##computing-function-retention Computing-function retention
     */
    public static <T> List<T> ofList(Mode mode, int size,
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
     * {@linkplain LazyCaches##initialization-modes initialization mode}. The argument
     * to the computing function is always the corresponding key retained from
     * {@code keys}, including when an access supplies a distinct but equal key.
     * A failed computation for one key does not prevent access to
     * values for other keys. Accessing an absent key does not invoke the function.
     * <p>
     * {@code size()}, {@code isEmpty()}, {@code containsKey}, and operations
     * on {@code keySet()} do not compute values. Obtaining {@code values()} or
     * {@code entrySet()} does not compute values either. Obtaining entries from
     * the entry-set view, including through iterators, spliterators, streams, or
     * array conversion, and reading an entry's key do not themselves compute
     * mapped values. Reading an entry's value or iterating over the values
     * computes the accessed values if necessary. Other operations that inspect
     * values, such as equality, hashing, string conversion, or
     * {@code containsValue}, may compute the values they inspect.
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
     * {@snippet lang = java:
     * record Customer(int id, String name) {
     *     @Override
     *     public boolean equals(Object obj) {
     *         return obj instanceof Customer other && id == other.id;
     *     }
     *
     *     @Override
     *     public int hashCode() {
     *         return Integer.hashCode(id);
     *     }
     * }
     *
     * var stored = new Customer(7, "Alice");
     * var lookup = new Customer(7, "Bob");
     * var names = LazyCaches.ofMap(LazyCaches.Mode.COMPUTE_ONCE,
     *         Set.of(stored), Customer::name);
     *
     * System.out.println(names.get(lookup)); // Alice: computes from stored
     * System.out.println(names.get(stored)); // Alice: returns the retained value
     * }
     *
     * @param mode the initialization mode for each mapped value
     * @param keys the keys of the map
     * @param computingFunction the function that computes non-null mapped values
     * @param <K> the type of the keys
     * @param <V> the type of the mapped values
     * @throws NullPointerException if any argument is {@code null}, or
     *         {@code keys} contains a {@code null} element
     * @see LazyCaches##collection-properties Collection properties
     * @see LazyCaches##traversal-failures Computation failures during traversal
     * @see LazyCaches##streams Streams and spliterators
     * @see LazyCaches##computing-function-retention Computing-function retention
     */
    public static <K, V> Map<K, V> ofMap(
            Mode mode,
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
     * given {@linkplain LazyCaches##initialization-modes initialization mode}. A
     * retained {@code true} result includes the candidate in the returned set;
     * a retained {@code false} result excludes it. Both results are retained,
     * so an established membership status never changes. In
     * {@code PUBLISH_ONCE} mode, competing evaluations may produce different
     * results, but all successful accesses use the one retained result for
     * that candidate. A failed computation for one candidate does not prevent
     * determining membership for other candidates. Failure behavior is
     * described in {@linkplain LazyCaches##failures Computation failures}.
     * <p>
     * A {@code contains} call computes only the matching retained candidate's
     * membership, if necessary. The argument to the computing function is
     * always that candidate from {@code elementCandidates}, including when the
     * queried object is distinct but equal. Querying a non-null object that is
     * not equal to any candidate returns {@code false} without invoking the
     * computing function. Querying {@code null} throws
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
     * @apiNote The example in {@link #ofMap(Mode, Set, Function)} illustrates
     *          how equal lookup objects can have different data. The same rule
     *          applies here: the predicate examines the retained candidate.
     *
     * @param mode the initialization mode for each candidate's membership
     * @param elementCandidates the candidates for membership in the returned set
     * @param computingFunction the predicate that determines candidate membership
     * @param <E> the type of the elements
     * @throws NullPointerException if any argument is {@code null}, or
     *         {@code elementCandidates} contains a {@code null} element
     * @see LazyCaches##collection-properties Collection properties
     * @see LazyCaches##traversal-failures Computation failures during traversal
     * @see LazyCaches##streams Streams and spliterators
     * @see LazyCaches##computing-function-retention Computing-function retention
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    public static <E> Set<E> ofSet(
            Mode mode,
            Set<? extends E> elementCandidates,
            Predicate<? super E> computingFunction) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(elementCandidates);
        Objects.requireNonNull(computingFunction);
        return jdk.internal.util.LazyCollections.ofLazySet(mode, elementCandidates, computingFunction);
    }
}
