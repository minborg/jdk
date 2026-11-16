/*
 * Copyright (c) 2012, 2026, Oracle and/or its affiliates. All rights reserved.
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
package java.util.function;

import jdk.internal.javac.PreviewFeature;
import jdk.internal.lang.LazyConstantImpl;

import java.lang.invoke.StableAccessors;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Represents a supplier of results.
 *
 * <p>There is no requirement that a new or distinct result be returned each
 * time the supplier is invoked.
 *
 * <p>This is a <a href="package-summary.html">functional interface</a>
 * whose functional method is {@link #get()}.
 *
 * @param <T> the type of results supplied by this supplier
 *
 * @since 1.8
 */
@FunctionalInterface
public interface Supplier<T> {

    /**
     * Gets a result.
     *
     * @return a result
     */
    T get();

    /**
     * {@return a new supplier that lazily computes and caches a non-null result}
     *
     * Creating the supplier does not invoke the provided {@code computingFunction}.
     * The first invocation of {@link #get() get()} that starts computation invokes
     * the computing function and caches its result. Once computation succeeds, every
     * subsequent invocation returns the same cached result.
     * <p>
     * The computing function is invoked at most once, even when multiple threads call
     * {@code get()} concurrently. If computation fails, the failure is recorded and
     * subsequent calls do not retry the computing function.
     * <p>
     * A lazy supplier can defer creation of a component until it is first needed:
     * {@snippet lang = java:
     * import java.util.function.Supplier;
     *
     * class Component {
     *     private final Supplier<System.Logger> logger = Supplier.ofLazy(
     *             () -> System.getLogger(Component.class.getName()));
     *
     *     void process() {
     *         logger.get().log(System.Logger.Level.INFO, "Process started");
     *     }
     * }
     * }
     * The cached result may be eligible for optimizations such as constant folding,
     * as described in {@linkplain StableAccessors##performance StableAccessors}.
     * <p>
     * This method always returns a new supplier, even if {@code computingFunction}
     * is itself a supplier returned by an earlier invocation of this method.
     *
     * <h4 id="ofLazy-failures">Failures</h4>
     * If the computing function throws an unchecked exception (a runtime exception or
     * an error), the invocation that performed the computation throws
     * {@link NoSuchElementException} with that exception as its cause. Subsequent
     * {@code get()} calls throw {@code NoSuchElementException} with no cause and with
     * a message containing the name of the original exception's class.
     * <p>
     * A {@code null} result is treated as a computation failure caused by
     * {@link NullPointerException}. Clients that need to cache a logical {@code null}
     * result can instead cache an {@link Optional} holder.
     * <p>
     * A recursive {@code get()} attempt during computation is rejected with
     * {@link IllegalStateException}. If that exception escapes the computing function,
     * the invocation that started computation records the failure and throws
     * {@code NoSuchElementException} with that exception as its cause.
     *
     * <h4 id="ofLazy-thread-safety">Thread safety</h4>
     * The computing function runs on the thread that starts computation. Competing
     * calls from other threads wait for that computation to complete and then observe
     * the cached result or recorded failure. Successful results have the
     * {@linkplain StableAccessors##memory-effects memory effects described by
     * StableAccessors}.
     * <p>
     * The supplier's coordination does not cancel computation, clear a thread's
     * interruption status, or throw {@link InterruptedException}. The computing
     * function may itself respond to interruption. If it blocks indefinitely, callers
     * waiting for that computation may also block indefinitely. No timeout or
     * cancellation operation is provided.
     *
     * <h4 id="ofLazy-dependencies">Dependencies</h4>
     * A lazy supplier can depend, via its computing function, on other lazy suppliers
     * or collections. It can also return a lazy collection, deferring both construction
     * of the collection and computation of its contents.
     * Cyclic dependencies can cause recursive access or deadlock between threads
     * waiting for one another. Use during static initialization also follows
     * the class initialization rules in section {@jls 12.4} of <cite>The Java Language
     * Specification</cite>; cycles can cause initialization errors or deadlock.
     *
     * @implNote After computation completes, successfully or with failure, the
     *           implementation releases the reference used to retain the computing
     *           function for evaluation. The function and objects it captures may then
     *           be garbage collected if they are not otherwise strongly reachable.
     *           <p>
     *           An implementation may synchronize on the returned supplier and use monitor
     *           ownership to detect recursive access. If no result is cached, calling
     *           {@code get()} while holding that monitor may throw {@link IllegalStateException},
     *           even if no computation is in progress. This rejection does not itself
     *           invoke the computing function or record a new failure.
     *
     * @param computingFunction the function used to compute the cached result
     * @param <T> the type of the cached result
     * @throws NullPointerException if {@code computingFunction} is {@code null}
     *
     * @see List#ofLazy(int, IntFunction)
     * @see Map#ofLazy(Set, Function)
     * @see Set#ofLazy(Set, Predicate)
     * @see StableAccessors##encapsulating-stable-accessors StableAccessors: encapsulating stable accessors
     * @since 29
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    public static <T> Supplier<T> ofLazy(Supplier<? extends T> computingFunction) {
        Objects.requireNonNull(computingFunction);
        return LazyConstantImpl.ofLazy(computingFunction);
    }

}
