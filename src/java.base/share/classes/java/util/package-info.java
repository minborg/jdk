/*
 * Copyright (c) 1998, 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Contains the collections framework, some internationalization support classes,
 * a service loader, properties, random number generation, string parsing
 * and scanning classes, base64 encoding and decoding, a bit array, and
 * several miscellaneous utility classes. This package also contains
 * legacy collection classes and legacy date and time classes.
 *
 * <h2><a id="CollectionsFramework"></a>{@index "Java Collections Framework"}</h2>
 * <p>For an overview, API outline, and design rationale, please see:
 * <ul>
 *   <li><a href="doc-files/coll-index.html">
 *          <b>Collections Framework Documentation</b></a>
 * </ul>
 *
 * <p>For a tutorial and programming guide with examples of use
 * of the collections framework, please see:
 * <ul>
 *   <li><a href="http://docs.oracle.com/javase/tutorial/collections/index.html">
 *          <b>Collections Framework Tutorial</b></a>
 * </ul>
 *
 * <h2 id="lazy-values">Lazy Values and Collections</h2>
 * A <em>lazy value</em> is content that is computed on first access and then
 * retained. The {@linkplain java.util.function.Supplier#ofLazy(java.util.function.Supplier)
 * Supplier.ofLazy} factory returns a supplier for a single lazy value. The
 * {@linkplain java.util.List#ofLazy(int, java.util.function.IntFunction) List.ofLazy},
 * {@linkplain java.util.Map#ofLazy(Set, java.util.function.Function) Map.ofLazy},
 * and {@linkplain java.util.Set#ofLazy(Set, java.util.function.Predicate) Set.ofLazy}
 * factories return collections whose elements, mapped values, or membership
 * statuses are lazy values.
 * <p>
 * When a lazy value is created, it is <em>not initialized</em>, meaning it has no
 * content. The first access to the lazy value invokes the computing function
 * provided to the factory. If the computation completes successfully, its result
 * initializes the lazy value. Once initialized, the content can <em>never change</em>
 * and is returned by subsequent accesses.
 * <p>
 * A lazy value can depend on other lazy values, forming a dependency graph that
 * can be lazily computed while access to individual values can still be performant.
 * For example, a {@code Supplier<Bar>} created with {@code Supplier.ofLazy} can
 * create its {@code Bar} by first accessing another lazy {@code Supplier<Foo>}.
 *
 * <h3 id="lazy-values-exception-handling">Exception handling</h3>
 * If evaluation of a computing function throws an unchecked exception (i.e., a
 * runtime exception or an error), the lazy value is not initialized but instead
 * transitions to an error state whereafter a {@linkplain NoSuchElementException}
 * is thrown with the unchecked exception as a cause. Subsequent accesses throw
 * {@linkplain NoSuchElementException} (without ever invoking the computing
 * function again) with no cause and with a message that includes the name of the
 * original unchecked exception's class.
 * <p>
 * If the computing function returns {@code null}, a {@linkplain NoSuchElementException}
 * (with a {@linkplain NullPointerException} as a cause) is thrown. Hence, a lazy
 * value can never hold a {@code null} value. Clients who want to use a nullable
 * value can wrap the value into an {@linkplain Optional} holder.
 * <p>
 * If the computing function recursively invokes itself via the lazy value, a
 * {@linkplain NoSuchElementException} (with an {@linkplain IllegalStateException}
 * as a cause) is thrown.
 *
 * <h3 id="lazy-values-thread-safety">Thread Safety</h3>
 * A lazy value is guaranteed to be initialized atomically and at most once. If
 * competing threads are racing to initialize a lazy value, only one updating
 * thread runs the computing function (which runs on the caller's thread and is
 * hereafter denoted <em>the computing thread</em>), while the other threads are
 * blocked until the value is initialized (or computation fails), after which the
 * other threads observe the lazy value is initialized (or has transitioned to an
 * error state) and leave the value unchanged and will never invoke any computation.
 * <p>
 * The invocation of the computing function and the resulting initialization of
 * the lazy value {@linkplain java.util.concurrent##MemoryVisibility
 * <em>happens-before</em>} the initialized content is read. Hence, the initialized
 * content, including any {@code final} fields of any newly created objects, is
 * safely published. As subsequent retrieval of the content might be elided, there
 * are no other memory ordering or visibility guarantees provided as a consequence
 * of accessing the lazy value again.
 * <p>
 * Thread interruption does not cancel initialization of a lazy value. In other
 * words, if the computing thread is interrupted, the lazy access doesn't clear
 * the interrupted thread’s status, nor does it throw an {@linkplain InterruptedException}.
 * <p>
 * If the computing function blocks indefinitely, other threads operating on this
 * lazy value may block indefinitely; no timeouts or cancellations are provided.
 *
 * <h3 id="lazy-values-performance">Performance</h3>
 * The content of a lazy value can never change after the lazy value has been
 * initialized. Therefore, a JVM implementation may, for an initialized lazy value,
 * elide all future reads of that lazy value's content and instead use content that
 * has been previously observed. We call this optimization <em>constant folding</em>.
 * This is only possible if there is a direct reference from a {@code static final}
 * field to the lazy holder or if there is a chain from a {@code static final} field
 * -- via one or more <em>trusted fields</em> (i.e., {@code static final} fields,
 * {@linkplain Record record} fields, or final instance fields in hidden classes) --
 * to the lazy holder.
 *
 * <h3 id="lazy-values-reachability">Reachability</h3>
 * Once a lazy value is initialized, its content can't be removed. This can be a
 * source of an unintended memory leak. More specifically, a lazy value
 * {@linkplain java.lang.ref##reachability strongly references} its content. Hence,
 * the content of a lazy value will be reachable as long as the lazy holder itself
 * is reachable.
 * <p>
 * A lazy holder strongly references its computing function until computation is no
 * longer possible. For {@code Supplier.ofLazy}, this is until the computation
 * completes successfully or fails. For lazy collections, this is at least as long
 * as there are uninitialized lazy values.
 *
 * <h3 id="lazy-values-additional-notes">Additional notes</h3>
 * While it's possible to store an array inside a lazy value, doing so will not
 * result in improved access performance of the array elements. Instead, a
 * {@linkplain List#ofLazy(int, java.util.function.IntFunction) lazy list} of
 * arbitrary depth can be used, which provides constant components.
 * <p>
 * Lazy holders described here are not {@link java.io.Serializable}, unless a
 * particular factory specifies otherwise.
 * <p>
 * Use in static initializers may interact with class initialization order; cyclic
 * initialization may result in initialization errors as described in section
 * {@jls 12.4} of <cite>The Java Language Specification</cite>.
 * <p>
 * An implementation is free to synchronize on a lazy holder. Hence, care must be
 * taken when directly or indirectly synchronizing on a lazy holder. A lazy holder
 * is unmodifiable but its content may or may not be immutable (e.g., it may hold
 * an {@linkplain ArrayList}).
 *
 * @since 1.0
 */
package java.util;
