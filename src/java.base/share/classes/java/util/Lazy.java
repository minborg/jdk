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

import java.lang.invoke.StableAccessors;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

import jdk.internal.foreign.Utils;
import jdk.internal.javac.PreviewFeature;
import jdk.internal.lang.LazyConstantImpl;

/**
 * Factory methods for lazy suppliers and unmodifiable collections.
 * <p>
 * These factories defer computation until a result is needed and cache the result
 * for subsequent accesses. A supplier caches one result; a list caches an element
 * per index; a map caches a value per key; and a set caches a membership decision
 * per element candidate. Creating a supplier or collection does not invoke its
 * computing function.
 * <p>
 * Collection factories and the context-accepting supplier factory require an explicit
 * {@link Mode}. The context-free supplier factory also has a
 * {@linkplain #ofSupplier(Supplier) convenience overload} using {@link Mode#ONCE}.
 * The mode applies independently to each cached result.
 * In either mode, once a result is installed, all normally returning accesses
 * requiring that result use its installed value. The factories manage their own storage.
 * For access to caller-owned fields and array elements, use {@link StableAccessors}.
 * <p>
 * A {@link BoundSupplier} accepts a computation context at each access, allowing its
 * computing function to use inputs supplied after the supplier is created. Each
 * supplier still caches one result: the context is computation input, not a cache key.
 *
 * <h2 id="modes">Computation modes</h2>
 * <table class="striped">
 * <caption>Computation and failure policies</caption>
 * <thead>
 * <tr><th scope="col">Mode</th><th scope="col">Computation per result</th>
 *     <th scope="col">Failure</th><th scope="col">Recursive access</th></tr>
 * </thead>
 * <tbody>
 * <tr><th scope="row">{@link Mode#ONCE}</th><td>At most one invocation</td>
 *     <td>Recorded; no retry</td><td>Detected</td></tr>
 * <tr><th scope="row">{@link Mode#PUBLISH}</th><td>Invocations may race</td>
 *     <td>Not recorded; retry permitted</td><td>Not detected</td></tr>
 * </tbody>
 * </table>
 * In {@code ONCE} mode, competing accesses to the same result wait for its computation
 * to complete successfully or with failure. In {@code PUBLISH} mode, multiple
 * invocations may compute candidates for the same result. One successful candidate
 * is atomically installed; invocations that lose the installation race discard their
 * candidates and return the installed result. Computing functions used with
 * {@code PUBLISH} must tolerate repeated and concurrent execution and discarded results.
 *
 * <h2 id="failures">Failures</h2>
 * Suppliers, list elements, and map values cannot have {@code null} as their computed
 * result. A {@code null} result is treated as a computation failure caused by
 * {@link NullPointerException}. A non-null holder such as {@link Optional} can
 * represent a logical {@code null}. Both {@code true} and {@code false} are valid
 * cached set-membership decisions.
 * <p>
 * In {@code ONCE} mode, if the computing function throws an unchecked exception
 * (a runtime exception or an error), the access that performed the computation throws
 * {@link NoSuchElementException} with that exception as its cause. Subsequent accesses
 * requiring that result throw {@code NoSuchElementException} with no cause and with
 * a message containing the name of the original exception's class. Computation is
 * not retried for that result. A recursive access to the same result during its
 * computation throws {@link IllegalStateException}. If that exception escapes the
 * computing function, the initiating access records the failure and throws
 * {@code NoSuchElementException} with that exception as its cause.
 * <p>
 * In {@code PUBLISH} mode, a throwable from the computing function propagates
 * unchanged, and a {@code null} result throws {@code NullPointerException} directly.
 * The failure is not cached. The failing access still fails if another invocation
 * installs a result in the meantime. If no result has been installed, a subsequent
 * access can retry. Recursive access to the same uninitialized result is not detected
 * and may recurse without bound.
 *
 * <h2 id="thread-safety">Thread safety and memory effects</h2>
 * A computing function runs on the thread accessing the result. Computations for
 * different collection inputs may proceed concurrently in either mode. In
 * {@code PUBLISH} mode, computations for the same result may also proceed concurrently.
 * Computing functions must support the concurrent invocations permitted by the mode
 * when their supplier or collection is accessed concurrently.
 * <p>
 * Installed results have the {@linkplain StableAccessors##memory-effects memory
 * effects described by StableAccessors}. These guarantees apply separately to each
 * result and do not impose a total order on accesses to different results. In
 * {@code PUBLISH} mode, discarding a candidate does not itself publish the actions
 * of its computation. Publishing a reference does not make later mutations of the
 * referenced object thread-safe.
 * <p>
 * The lazy computation machinery does not cancel computation, clear interruption
 * status, or throw {@link InterruptedException}. The computing function may itself
 * respond to interruption. In {@code ONCE} mode, an indefinitely blocked computation
 * can indefinitely block competing accesses requiring the same result. No timeout
 * or cancellation operation is provided.
 *
 * <h2 id="dependencies">Dependencies</h2>
 * A computing function can depend on other lazy suppliers or collections, or return
 * a lazy collection to defer both its construction and computation of its contents.
 * Cyclic dependencies can cause recursive access or deadlock between threads waiting
 * for one another. Use during static initialization also follows the class
 * initialization rules in section {@jls 12.4} of <cite>The Java Language
 * Specification</cite>; cycles can cause initialization errors or deadlock.
 * <p>
 * Cached results may be eligible for optimizations such as constant folding, as
 * described in {@linkplain StableAccessors##performance StableAccessors}.
 *
 * @see StableAccessors
 * @see Supplier
 * @see List
 * @see Set
 * @see Map
 * @since 29
 */
@PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
public final class Lazy {

    // Suppresses default constructor, ensuring non-instantiability.
    private Lazy() { }

    /**
     * The computation and failure policy for each independently cached result.
     * See {@linkplain Lazy##modes computation modes} and
     * {@linkplain Lazy##failures failures} for the shared contracts.
     *
     * @since 29
     */
    public enum Mode {
        /**
         * Invokes the computing function at most once per result and records failures.
         * Competing accesses wait for the computation, and recursive access to the
         * result being computed is detected.
         */
        ONCE,

        /**
         * Allows competing computations, installs one successful result, and does not
         * record failures. Failed computations may be retried, and recursive access
         * is not detected.
         */
        PUBLISH
    }

    /**
     * A supplier whose accessor accepts a computation context.
     * <p>
     * Instances returned by {@link Lazy#ofSupplier(Mode, Class, Function)} cache one
     * result per supplier, regardless of the contexts passed to {@link #get(Object)}.
     * They follow the shared {@linkplain Lazy##modes computation modes},
     * {@linkplain Lazy##failures failure policy}, and
     * {@linkplain Lazy##thread-safety memory effects}.
     *
     * @param <C> the computation context type
     * @param <T> the result type
     * @since 29
     */
    @FunctionalInterface
    public interface BoundSupplier<C, T> {
        /**
         * {@return the cached result, computing it from the context if necessary}
         * For suppliers created by {@code Lazy}, the context is passed to the computing
         * function only when computation is needed. Different contexts share the same
         * installed result or, in {@link Mode#ONCE}, the same recorded failure.
         * Computation failures are reported according to the
         * {@linkplain Lazy##failures selected mode}.
         *
         * @param context the computation context, possibly {@code null} if the
         *                computing function accepts it
         */
        T get(C context);
    }

    /**
     * {@return a new lazy supplier using {@link Mode#ONCE}}
     * Equivalent to {@link #ofSupplier(Mode, Supplier)
     * ofSupplier(Mode.ONCE, computingFunction)}.
     *
     * @param computingFunction the function computing a non-null result
     * @param <T> the result type
     * @throws NullPointerException if {@code computingFunction} is null
     * @since 29
     */
    public static <T> Supplier<T> ofSupplier(Supplier<? extends T> computingFunction) {
        return ofSupplier(Mode.ONCE, computingFunction);
    }

    /**
     * {@return a new supplier that lazily computes and caches a non-null result}
     * The returned supplier uses the specified {@linkplain ##modes mode} and follows
     * the shared {@linkplain ##failures failure} and
     * {@linkplain ##thread-safety thread-safety} contracts. Its {@link Supplier#get()}
     * method returns the installed result, computing it if necessary.
     * This method returns a new supplier even if {@code computingFunction} is itself
     * a supplier returned by an earlier invocation of a factory in this class.
     * <p>
     * A lazy supplier can defer creation of a component until it is first needed:
     * {@snippet lang = java:
     * import java.util.Lazy;
     * import java.util.function.Supplier;
     *
     * class Component {
     *     private final Supplier<System.Logger> logger = Lazy.ofSupplier(
     *             () -> System.getLogger(Component.class.getName()));
     *
     *     void process() {
     *         logger.get().log(System.Logger.Level.INFO, "Process started");
     *     }
     * }
     * }
     *
     * @implNote In {@code ONCE} mode, after computation completes successfully or
     *           with failure, the implementation releases its reference to the
     *           computing function. The function and captured objects may then be
     *           garbage collected if they are not otherwise strongly reachable.
     *           <p>
     *           An implementation of {@code ONCE} may synchronize on the returned
     *           supplier and use monitor ownership to detect recursive access. If no
     *           result is cached, calling {@code get()} while holding that monitor
     *           may throw {@link IllegalStateException}, even if no computation is
     *           in progress. This rejection does not invoke the computing function
     *           or record a failure.
     * @param mode the computation and failure policy
     * @param computingFunction the function computing a non-null result
     * @param <T> the result type
     * @throws NullPointerException if either argument is null
     * @since 29
     */
    public static <T> Supplier<T> ofSupplier(Mode mode,
                                             Supplier<? extends T> computingFunction) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(computingFunction);
        if (mode == Mode.PUBLISH) {
            // The publishing implementation is not yet provided by this prototype.
            throw new UnsupportedOperationException();
        }
        return LazyConstantImpl.ofLazy(computingFunction);
    }

    /**
     * {@return a new supplier that lazily computes and caches a non-null result
     * using a computation context}
     * The returned supplier uses the specified {@linkplain ##modes mode} and follows
     * the shared {@linkplain ##failures failure} and
     * {@linkplain ##thread-safety thread-safety} contracts. Its
     * {@link BoundSupplier#get(Object) get(context)} method supplies the context to
     * the computing function when computation is needed.
     * <p>
     * The supplier owns one cached result, not one result per context. In
     * {@code ONCE} mode, the invocation that starts computation supplies its context;
     * subsequent accesses share the result or recorded failure. In {@code PUBLISH}
     * mode, computations using different contexts may race, and the successful
     * installation determines the cached result. A {@code null} context is permitted
     * if the computing function accepts it.
     * <p>
     * The {@code contextType} token describes the context's reference type or a
     * supertype; value classes are supported. Primitive type tokens, including
     * {@code void.class}, are not supported. The caller must ensure that contexts
     * are suitable for the computing function; erased type arguments remain the
     * caller's responsibility.
     * <p>
     * A value class can create a supplier without capturing {@code this} during
     * construction, then pass {@code this} as its context when the result is needed:
     * {@snippet lang = java:
     * import java.util.Lazy;
     * import java.util.Objects;
     *
     * value class StringHolder {
     *     private final String value;
     *     private final Lazy.BoundSupplier<StringHolder, Integer> length =
     *             Lazy.ofSupplier(Lazy.Mode.ONCE, StringHolder.class,
     *                     holder -> holder.value.length());
     *
     *     StringHolder(String value) {
     *         this.value = Objects.requireNonNull(value);
     *     }
     *
     *     int length() {
     *         return length.get(this);
     *     }
     * }
     * }
     * The supplier is an instance field so that each constructed holder has its own
     * cache; a static supplier would share one result across all holders.
     *
     * @param mode the computation and failure policy
     * @param contextType a reference type token for the context type or a supertype
     * @param computingFunction the function computing a non-null result from a context
     * @param <C> the computation context type
     * @param <T> the result type
     * @throws IllegalArgumentException if {@code contextType} is a primitive type,
     *         including {@code void.class}
     * @throws NullPointerException if any argument is null
     * @since 29
     */
    public static <C, T> BoundSupplier<C, T> ofSupplier(
            Mode mode,
            Class<? super C> contextType,
            Function<? super C, ? extends T> computingFunction) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(contextType);
        Objects.requireNonNull(computingFunction);
        if (contextType.isPrimitive()) {
            throw new IllegalArgumentException("Expected a reference context type: " + contextType);
        }
        // The context-accepting implementation is not yet provided by this prototype.
        throw new UnsupportedOperationException();
    }

    /**
     * {@return a new lazily computed list of the provided {@code size}}
     * The returned list is {@linkplain Collection##unmodifiable unmodifiable}, with
     * one independently cached, non-null element per index. Accessing an element,
     * for example through {@link List#get(int)}, computes it if necessary using the
     * specified {@linkplain ##modes mode}.
     * <p>
     * The list follows the <a href="doc-files/coll-reference.html#lazy-collections">
     * shared specification for lazy collections</a>. It can be modeled as a
     * {@code List<Supplier<E>>}, with each supplier created by
     * {@link #ofSupplier(Mode, Supplier)} using the same mode; an implementation may
     * provide better performance and storage efficiency than this model.
     * <p>
     * The elements of its {@link List#subList(int, int) subList} and
     * {@link List#reversed() reversed} views are also lazily computed and share the
     * corresponding cached results. The list and these views implement
     * {@link RandomAccess}. The list strongly references its initialized elements.
     * A logical {@code null} element can be represented by an {@link Optional} holder.
     * <p>
     * For example, this list creates at most three controllers, on demand:
     * {@snippet lang = java:
     * import java.util.Lazy;
     * import java.util.List;
     *
     * class Application {
     *     static final class OrderController { }
     *     private static final List<OrderController> ORDERS =
     *             Lazy.ofList(Lazy.Mode.ONCE, 3, _ -> new OrderController());
     *
     *     static OrderController orders() {
     *         return ORDERS.get((int) (Thread.currentThread().threadId() % ORDERS.size()));
     *     }
     * }
     * }
     *
     * @param mode the computation and failure policy for each index
     * @param size the size of the returned list
     * @param computingFunction the function computing a non-null element for an index
     * @param <E> the element type
     * @throws IllegalArgumentException if {@code size} is negative
     * @throws NullPointerException if {@code mode} or {@code computingFunction} is null
     * @since 29
     */
    public static <E> List<E> ofList(Mode mode,
                                     int size,
                                     IntFunction<? extends E> computingFunction) {
        Objects.requireNonNull(mode);
        Utils.checkNonNegativeArgument(size, "size");
        Objects.requireNonNull(computingFunction);
        if (mode == Mode.PUBLISH) {
            // The publishing implementation is not yet provided by this prototype.
            throw new UnsupportedOperationException();
        }
        // A computed list is not Serializable, even when empty.
        return LazyCollections.ofLazyList(size, computingFunction);
    }

    /**
     * {@return a new lazily computed map with the provided {@code keys}}
     * The returned map is {@linkplain Collection##unmodifiable unmodifiable}, with
     * one independently cached, non-null value per key. Accessing a value, for
     * example through {@link Map#get(Object)}, computes it if necessary using the
     * specified {@linkplain ##modes mode}.
     * <p>
     * The map follows the <a href="doc-files/coll-reference.html#lazy-collections">
     * shared specification for lazy collections</a>. It can be modeled as a
     * {@code Map<K, Supplier<V>>}, with each supplier created by
     * {@link #ofSupplier(Mode, Supplier)} using the same mode; an implementation may
     * provide better performance and storage efficiency than this model.
     * <p>
     * The values exposed by its {@link Map#values() values} and
     * {@link Map#entrySet() entrySet} views are also lazily computed and share the
     * corresponding cached results. The map strongly references its initialized
     * values. A logical {@code null} value can be represented by an {@link Optional} holder.
     * <p>
     * The keys are copied at construction; subsequent modifications to {@code keys}
     * are not reflected in the returned map. The supplied set must use
     * {@link Object#equals(Object) equals} as its equivalence relation, or a comparison
     * consistent with {@code equals}; otherwise the behavior is unspecified.
     * <p>
     * For example, this map computes each square root at most once:
     * {@snippet lang = java:
     * import java.util.Lazy;
     * import java.util.Map;
     * import java.util.Optional;
     * import java.util.Set;
     *
     * class Application {
     *     private static final Map<Integer, Double> CACHE =
     *             Lazy.ofMap(Lazy.Mode.ONCE, Set.of(0, 1, 3, 42, 97),
     *                     input -> Math.sqrt(input));
     *
     *     static Optional<Double> cachedSquareRoot(int input) {
     *         return Optional.ofNullable(CACHE.get(input));
     *     }
     * }
     * }
     *
     * @param mode the computation and failure policy for each key
     * @param keys the non-null keys of the returned map
     * @param computingFunction the function computing a non-null value for a key
     * @param <K> the key type
     * @param <V> the value type
     * @throws NullPointerException if any argument is null or {@code keys} contains null
     * @since 29
     */
    public static <K, V> Map<K, V> ofMap(Mode mode,
                                         Set<? extends K> keys,
                                         Function<? super K, ? extends V> computingFunction) {
        Objects.requireNonNull(mode);
        // Protect against TOC-TOU attacks; also checks keys and their elements for null.
        final Set<K> keyCopies = Set.copyOf(keys);
        Objects.requireNonNull(computingFunction);
        if (mode == Mode.PUBLISH) {
            // The publishing implementation is not yet provided by this prototype.
            throw new UnsupportedOperationException();
        }
        // Test the original set to retain the enum-key specialization.
        if (keys instanceof EnumSet<?> && !keyCopies.isEmpty()) {
            @SuppressWarnings("unchecked")
            var enumMap = (Map<K, V>) LazyCollections.ofLazyMapWithEnumKeys(keyCopies, computingFunction);
            return enumMap;
        } else {
            // A computed map is not Serializable, even when empty.
            return LazyCollections.ofLazyMap(keyCopies, computingFunction);
        }
    }

    /**
     * {@return a new lazily computed set drawn from {@code elementCandidates}}
     * The returned set is {@linkplain Collection##unmodifiable unmodifiable}, with
     * one independently cached membership decision per candidate. Accessing a
     * candidate's membership, for example through {@link Set#contains(Object)},
     * computes it if necessary using the specified {@linkplain ##modes mode}.
     * A {@code true} decision includes the candidate in the set; {@code false}
     * excludes it. Both decisions remain cached for the lifetime of the set.
     * <p>
     * The set follows the <a href="doc-files/coll-reference.html#lazy-collections">
     * shared specification for lazy collections</a>. It can be modeled as a
     * {@code Map<E, Supplier<Boolean>>}, with each supplier created by
     * {@link #ofSupplier(Mode, Supplier)} using the same mode; an implementation may
     * provide better performance and storage efficiency than this model.
     * <p>
     * The candidates are copied at construction; subsequent modifications to
     * {@code elementCandidates} are not reflected in the returned set. The supplied
     * set must use {@link Object#equals(Object) equals} as its equivalence relation,
     * or a comparison consistent with {@code equals}; otherwise the behavior is unspecified.
     * <p>
     * For example, this set evaluates each option at most once:
     * {@snippet lang = java:
     * import java.util.EnumSet;
     * import java.util.Lazy;
     * import java.util.Locale;
     * import java.util.Set;
     *
     * class Application {
     *
     *     enum Option { VERBOSE, DRY_RUN, STRICT }
     *
     *     static final Set<Option> OPTIONS =
     *             Lazy.ofSet(Lazy.Mode.ONCE, EnumSet.allOf(Option.class),
     *                     option -> Boolean.getBoolean(option.name().toLowerCase(Locale.ROOT)));
     *
     *     static boolean dryRun() {
     *         return OPTIONS.contains(Option.DRY_RUN);
     *     }
     * }
     * }
     *
     * @param mode the computation and failure policy for each candidate
     * @param elementCandidates the non-null candidates for membership
     * @param computingFunction the predicate computing a candidate's membership
     * @param <E> the element type
     * @throws NullPointerException if any argument is null or
     *         {@code elementCandidates} contains null
     * @since 29
     */
    public static <E> Set<E> ofSet(Mode mode,
                                   Set<? extends E> elementCandidates,
                                   Predicate<? super E> computingFunction) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(elementCandidates);
        Objects.requireNonNull(computingFunction);
        if (mode == Mode.PUBLISH) {
            // Validate the candidates without constructing a publishing implementation.
            Set.copyOf(elementCandidates);
            // The publishing implementation is not yet provided by this prototype.
            throw new UnsupportedOperationException();
        }
        return LazyCollections.ofLazySet(elementCandidates, computingFunction);
    }
}
