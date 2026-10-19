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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import jdk.internal.javac.PreviewFeature;
import jdk.internal.lang.LazyConstantImpl;

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
     * {@return a new lazy supplier whose result is computed at most once using
     *          the provided {@code computingFunction}}
     * <p>
     * A lazy supplier provides storage for a non-null result that can be
     * initialized at most once. Computation takes place on the accessing thread.
     * Each invocation of this method creates a new lazy supplier, even if
     * {@code computingFunction} is itself a lazy supplier. The returned supplier
     * strongly references the computing function until computation completes,
     * successfully or otherwise.
     * <p>
     * When created, the supplier is <em>not initialized</em>, meaning it has no
     * result. Its result can be computed and retrieved by calling
     * {@linkplain Supplier#get() get()}. The first invocation calls the underlying
     * <em>computing function</em> supplied at construction and uses its result to
     * initialize the supplier. Once initialized, that result can never change and
     * is returned by subsequent invocations.
     * <p>
     * Consider a field holding a lazily created {@code Logger}:
     * {@snippet lang = java:
     * public class Component {
     *     private final Supplier<Logger> logger =
     *             Supplier.ofLazy(() -> Logger.create(Component.class));
     *
     *     public void process() {
     *         logger.get().info("Process started");
     *         // ...
     *     }
     * }
     * }
     * Initially, the supplier is uninitialized. The first invocation of
     * {@code logger.get()} evaluates the computing function, initializes the
     * supplier to the result, and returns it. A successful call to {@code get()}
     * therefore guarantees that the result is initialized. If several threads
     * try to initialize it simultaneously, only
     * {@linkplain ##thread-safety one is selected} to run the computing function.
     * This matters when computation has side effects; for example,
     * {@code Logger.create()} might prepare storage resources.
     *
     * <h4 id="exception-handling">Exception handling</h4>
     * If evaluation of the computing function throws an unchecked exception
     * (a runtime exception or an error), the supplier is not initialized. Instead,
     * it enters an error state. That invocation throws a
     * {@link NoSuchElementException} with the unchecked exception as its cause.
     * Subsequent calls to {@code get()} throw {@code NoSuchElementException}
     * without invoking the computing function again, without a cause, and with a
     * message containing the original exception's class name. The same handling
     * applies to any other {@link Throwable} that escapes the function.
     * <p>
     * If the computing function returns {@code null}, the initial call throws
     * {@code NoSuchElementException} with a {@link NullPointerException} as its
     * cause. A lazy supplier can therefore never hold {@code null}; an
     * {@link Optional} can be used to represent a nullable result. If the
     * computing function recursively calls {@code get()} on the same supplier,
     * the initial call throws {@code NoSuchElementException} with an
     * {@link IllegalStateException} as its cause.
     *
     * <h4 id="composition">Composing lazy suppliers</h4>
     * Lazy suppliers can depend on one another, forming a dependency graph whose
     * values are computed only when needed. In this example, a {@code Bar} depends
     * on a {@code Foo}, and both are lazily created:
     * {@snippet lang = java:
     * public static class Foo {
     *     // ...
     * }
     *
     * public static class Bar {
     *     public Bar(Foo foo) {
     *         // ...
     *     }
     * }
     *
     * static final Supplier<Foo> FOO = Supplier.ofLazy(Foo::new);
     * static final Supplier<Bar> BAR = Supplier.ofLazy(() -> new Bar(FOO.get()));
     *
     * public static Foo foo() {
     *     return FOO.get();
     * }
     *
     * public static Bar bar() {
     *     return BAR.get();
     * }
     * }
     * Calling {@code BAR.get()} creates the {@code Bar} if necessary, creating
     * its dependent {@code Foo} first if that has not already been done.
     *
     * <h4 id="thread-safety">Thread safety of lazy suppliers</h4>
     * A lazy supplier is initialized atomically and at most once. If competing
     * threads race to initialize it, only one invokes the computing function on
     * its own thread (the <em>computing thread</em>). Other threads block until
     * computation completes or fails; they then observe the initialized result or
     * recorded error state and do not invoke the function themselves.
     * <p>
     * Invocation of the computing function and initialization of the result
     * {@linkplain java.util.concurrent##MemoryVisibility <em>happen-before</em>}
     * the initialized result is read. The result, including the {@code final}
     * fields of any newly created objects, is safely published. Subsequent reads
     * of the result may be elided, so another call to {@code get()} provides no
     * additional memory ordering or visibility guarantee.
     * <p>
     * Interruption does not cancel initialization. If the computing thread is
     * interrupted, {@code get()} does not clear its interrupted status or throw
     * {@link InterruptedException}. If the computing function blocks indefinitely,
     * other threads accessing the supplier may block indefinitely; no timeout or
     * cancellation is provided.
     *
     * <h4 id="identity">Identity and debugging</h4>
     * Suppliers returned by {@code Supplier.ofLazy} compare equal only to
     * themselves and use their {@linkplain System#identityHashCode(Object)
     * identity hash code}. Their {@code equals}, {@code hashCode}, and
     * {@code toString} methods do not themselves initialize the result.
     * {@code toString} observes initialization by another thread atomically and
     * returns an implementation-dependent debugging string. If initialized, the
     * string describes the result, using a special marker if the result is the
     * supplier itself; otherwise, it indicates that the supplier is not yet
     * initialized or has failed.
     *
     * <h4 id="performance">Performance</h4>
     * The result of a lazy supplier never changes after initialization. A JVM may
     * therefore elide later reads and reuse an already observed result, an
     * optimization called <em>constant folding</em>. For a supplier returned by
     * {@code Supplier.ofLazy}, this is possible if there is a direct reference
     * from a {@code static final} field to the supplier, or a chain from such a
     * field through one or more <em>trusted fields</em> (other {@code static final}
     * fields, {@linkplain Record record} fields, or final instance fields in
     * hidden classes) to the supplier. Lazy lists, sets, and maps can similarly
     * make their computed contents eligible for constant folding, as described by
     * their factory methods.
     *
     * @apiNote A lazy supplier {@linkplain java.lang.ref##reachability strongly
     *          references} its result for as long as the supplier is reachable.
     *          Since the result cannot be removed, this may cause an unintended
     *          memory leak. An array stored as one result does not make its
     *          elements independently lazy; a {@linkplain List#ofLazy(int,
     *          IntFunction) lazy list} can be used for that purpose.
     *          <p>
     *          The returned supplier is not {@link Serializable}. Its result
     *          may be mutable.
     *          <p>
     *          Lazy suppliers used in static initializers remain subject to class
     *          initialization order. Cyclic initialization may cause the errors
     *          described in section {@jls 12.4} of
     *          <cite>The Java Language Specification</cite>.
     *
     * @implNote After computation completes, the returned supplier releases
     *           the reference to the computing function that it kept for
     *           invocation. Other references, including through the computed
     *           result, may retain that function.
     *           <p>
     *           The returned supplier may synchronize on itself, so care is
     *           needed when directly or indirectly synchronizing on it. The
     *           supplier is unmodifiable, but its result need not be immutable
     *           (it might be an {@link ArrayList}).
     *
     * @param computingFunction the function used to initialize the result
     * @param <T> the type of the result
     * @throws NullPointerException if {@code computingFunction} is {@code null}
     * @since 28
     * @see List#ofLazy(int, IntFunction)
     * @see Set#ofLazy(Set, Predicate)
     * @see Map#ofLazy(Set, Function)
     * @see Optional
     * @see java.lang.invoke.Lazy
     * @jls 12.4 Initialization of Classes and Interfaces
     * @jls 17.4.5 Happens-before Order
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    static <T> Supplier<T> ofLazy(Supplier<? extends T> computingFunction) {
        Objects.requireNonNull(computingFunction);
        return LazyConstantImpl.ofLazy(computingFunction);
    }
}
