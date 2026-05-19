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
import jdk.internal.lang.LazySupplier;

import java.util.ArrayList;
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
 * <h2 id="lazy-suppliers">Lazy suppliers</h2>
 * A lazy supplier is a holder of content that can be initialized at most once.
 * <p>
 * A lazy supplier is created using the factory method
 * {@linkplain Supplier#ofLazy(Supplier) Supplier.ofLazy({@code <computing function>})}.
 * <p>
 * When created, the lazy supplier is <em>not initialized</em>, meaning it has no content.
 * <p>
 * The lazy supplier (of type {@code T}) can then be <em>initialized</em>
 * (and its content retrieved) by calling {@linkplain #get() get()}. The first time
 * {@linkplain #get() get()} is called, the underlying <em>computing function</em>
 * (provided at construction) will be invoked and the result will be used to initialize
 * the supplier.
 * <p>
 * Once a lazy supplier is initialized, its content can <em>never change</em>
 * and will always be returned by subsequent {@linkplain #get() get()} invocations.
 * <p>
 * Consider the following example where a lazy supplier field "{@code logger}" holds
 * an object of type {@code Logger}:
 *
 * {@snippet lang = java:
 * public class Component {
 *
 *    // Creates a new uninitialized lazy supplier
 *    private final Supplier<Logger> logger =
 *            // @link substring="ofLazy" target="#ofLazy" :
 *            Supplier.ofLazy( () -> Logger.create(Component.class) );
 *
 *    public void process() {
 *        logger.get().info("Process started");
 *        // ...
 *    }
 * }
 * }
 * <p>
 * Initially, the lazy supplier is <em>not initialized</em>. When {@code logger.get()}
 * is first invoked, it evaluates the computing function and initializes the supplier to
 * the result; the result is then returned to the client. Hence, {@linkplain #get() get()}
 * guarantees that the supplier is <em>initialized</em> before it returns, barring
 * any exceptions.
 * <p>
 * Furthermore, {@linkplain #get() get()} guarantees that, out of several threads trying to
 * invoke the computing function simultaneously, {@linkplain ##thread-safety only one is
 * ever selected} for computation. This property is crucial as evaluation of the computing
 * function may have side effects, for example, the call above to {@code Logger.create()}
 * may result in storage resources being prepared.
 *
 * <h2 id="exception-handling">Exception handling</h2>
 * If evaluation of the computing function throws an unchecked exception (i.e., a runtime
 * exception or an error), the lazy supplier is not initialized but instead transitions to
 * an error state whereafter a {@linkplain NoSuchElementException} is thrown with the
 * unchecked exception as a cause. Subsequent {@linkplain #get() get()} calls throw
 * {@linkplain NoSuchElementException} (without ever invoking the computing function
 * again) with no cause and with a message that includes the name of the original
 * unchecked exception's class.
 * <p>
 * All failures are handled in this way. There are two special cases that cause unchecked
 * exceptions to be thrown:
 * <p>
 * If the computing function returns {@code null}, a {@linkplain NoSuchElementException}
 * (with a {@linkplain NullPointerException} as a cause) will be thrown. Hence, a
 * lazy supplier can never hold a {@code null} value. Clients who want to use a nullable
 * supplier can wrap the value into an {@linkplain Optional} holder.
 * <p>
 * If the computing function recursively invokes itself via the lazy supplier, a
 * {@linkplain NoSuchElementException} (with an {@linkplain IllegalStateException} as a
 * cause) will be thrown.
 *
 * <h2 id="composition">Composing lazy suppliers</h2>
 * A lazy supplier can depend on other lazy suppliers, forming a dependency graph
 * that can be lazily computed but where access to individual elements can still be
 * performant. In the following example, a single {@code Foo} and a {@code Bar}
 * instance (that is dependent on the {@code Foo} instance) are lazily created, both of
 * which are held by lazy suppliers:
 *
 * {@snippet lang = java:
 * public static class Foo {
 *      // ...
 *  }
 *
 * public static class Bar {
 *     public Bar(Foo foo) {
 *          // ...
 *     }
 * }
 *
 * static final Supplier<Foo> FOO = Supplier.ofLazy( Foo::new );
 * static final Supplier<Bar> BAR = Supplier.ofLazy( () -> new Bar(FOO.get()) );
 *
 * public static Foo foo() {
 *     return FOO.get();
 * }
 *
 * public static Bar bar() {
 *     return BAR.get();
 * }
 * }
 * Calling {@code foo()} will create the {@code Foo} singleton if it is not already
 * created. Calling {@code bar()} will create the {@code Bar} singleton if it is not already
 * created. Upon such a creation, a dependent {@code Foo} will first be created if
 * the {@code Foo} does not already exist.
 *
 * <h2 id="thread-safety">Thread Safety</h2>
 * A lazy supplier is guaranteed to be initialized atomically and at most once. If
 * competing threads are racing to initialize a lazy supplier, only one updating thread
 * runs the computing function (which runs on the caller's thread and is hereafter denoted
 * <em>the computing thread</em>), while the other threads are blocked until the supplier
 * is initialized (or computation fails), after which the other threads observe the lazy
 * supplier is initialized (or has transisioned to an error state) and leave the supplier
 * unchanged and will never invoke any computation.
 * <p>
 * The invocation of the computing function and the resulting initialization of
 * the supplier {@linkplain java.util.concurrent##MemoryVisibility <em>happens-before</em>}
 * the initialized supplier's content is read. Hence, the initialized supplier's content,
 * including any {@code final} fields of any newly created objects, is safely published.
 * As subsequent retrieval of the content might be elided, there are no other memory
 * ordering or visibility guarantees provided as a consequence of calling
 * {@linkplain #get()} again.
 * <p>
 * Thread interruption does not cancel the initialization of a lazy supplier. In other
 * words, if the computing thread is interrupted, {@code Supplier::get} doesn't clear
 * the interrupted thread’s status, nor does it throw an {@linkplain InterruptedException}.
 * <p>
 * If the computing function blocks indefinitely, other threads operating on this
 * lazy supplier may block indefinitely; no timeouts or cancellations are provided.
 *
 * <h2 id="performance">Performance</h2>
 * The content of a lazy supplier can never change after the lazy supplier has been
 * initialized. Therefore, a JVM implementation may, for an initialized lazy supplier,
 * elide all future reads of that lazy supplier's content and instead use the content
 * that has been previously observed. We call this optimization <em>constant folding</em>.
 * This is only possible if there is a direct reference from a {@code static final} field
 * to a lazy supplier or if there is a chain from a {@code static final} field -- via one
 * or more <em>trusted fields</em> (i.e., {@code static final} fields,
 * {@linkplain Record record} fields, or final instance fields in hidden classes) --
 * to a lazy supplier.
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

    // Factory

    /**
     * {@return a new lazy supplier whose result is to be computed later via the
     *          provided {@code computingFunction}}
     * <p>
     * The returned lazy supplier has the characteristics described in the
     * {@linkplain Supplier##lazy-suppliers lazy suppliers} section.
     * <p>
     * The returned lazy supplier strongly references the provided
     * {@code computingFunction} until computation completes (successfully or with
     * failure).
     * <p>
     * By design, the method always returns a new lazy supplier even if the provided
     * computing function is already a lazy supplier. Clients that want to elide creation
     * under this condition can retain and reuse the original lazy supplier rather than
     * wrapping it again.
     * <p>
     * The returned lazy supplier's {@linkplain Object#equals(Object) equals()} method
     * compares identity. Hence, two distinct lazy suppliers with the same content are
     * <em>not</em> equal. The {@linkplain Object#hashCode() hashCode()} method returns
     * the {@linkplain System#identityHashCode(Object) identity hash code}. The
     * {@linkplain Object#toString() toString()} method returns a string suitable for
     * debugging and never triggers initialization of the lazy supplier.
     *
     * @apiNote Once a lazy supplier is initialized, its content can't be removed.
     *          This can be a source of an unintended memory leak. More specifically,
     *          a lazy supplier {@linkplain java.lang.ref##reachability strongly references}
     *          its content. Hence, the content of a lazy supplier will be reachable as long
     *          as the lazy supplier itself is reachable.
     *          <p>
     *          While it's possible to store an array inside a lazy supplier, doing so will
     *          not result in improved access performance of the array elements. Instead, a
     *          {@linkplain List#ofLazy(int, IntFunction) lazy list} of arbitrary depth can
     *          be used, which provides constant components.
     *          <p>
     *          The returned lazy supplier is not {@link java.io.Serializable}.
     *          <p>
     *          Use in static initializers may interact with class initialization order;
     *          cyclic initialization may result in initialization errors as described
     *          in section {@jls 12.4} of <cite>The Java Language Specification</cite>.
     *
     * @implNote
     *           A lazy supplier is free to synchronize on itself. Hence, care must be
     *           taken when directly or indirectly synchronizing on a lazy supplier.
     *           A lazy supplier is unmodifiable but its content may or may not be
     *           immutable (e.g., it may hold an {@linkplain ArrayList}).
     *           <p>
     *           After the computing function completes (regardless of whether it
     *           succeeds or throws an unchecked exception), the computing function is no
     *           longer strongly referenced and becomes eligible for garbage collection.
     *
     * @param computingFunction in the form of a {@linkplain Supplier} to be used
     *                          to initialize the supplier
     * @param <T>               type of the supplied value
     * @throws NullPointerException if the provided {@code computingFunction} is
     *                              {@code null}
     *
     * @see Optional
     * @see List#ofLazy(int, IntFunction)
     * @see Map#ofLazy(Set, Function)
     * @jls 12.4 Initialization of Classes and Interfaces
     * @jls 17.4.5 Happens-before Order
     * @since 28
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
    static <T> Supplier<T> ofLazy(Supplier<? extends T> computingFunction) {
        Objects.requireNonNull(computingFunction);
        return LazySupplier.ofLazy(computingFunction);
    }
}
