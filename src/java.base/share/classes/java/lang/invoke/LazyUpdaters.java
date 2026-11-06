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

import jdk.internal.javac.PreviewFeature;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;
import sun.invoke.util.VerifyAccess;

import static jdk.internal.invoke.LazyUpdatersUtil.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.*;

/**
 * Factory methods for lazy updaters.
 *
 * <h2 id="updaters">Updaters</h2>
 * Lazy updaters use caller-owned storage. An invocation returns a non-default value
 * already in the selected location without computing. If no non-default value exists,
 * an updater tries to compute a value and install it in the selected location.
 * <p>
 * The factories do not read that storage or invoke the computing function when an updater
 * is created.
 *
 * <h3 id="access-shapes">Locations and access shapes</h3>
 * The computing function receives the storage coordinates (if any) and, for arrays,
 * an additional index coordinate, and returns a candidate value. An updater's
 * {@code getOrCompute} method takes the same coordinates:
 * <ul>
 * <li>For a named static field, there are no coordinates.</li>
 * <li>For a named instance field, the single coordinate is the instance receiver.</li>
 * <li>For a named static array field, the single coordinate is the array index.</li>
 * <li>For a named array field, the first coordinate is the instance receiver (in which
 *     the array resides) and the second coordinate is the array index.</li>
 * </ul>
 * Only one-dimensional array fields are supported. Array factories also take an element
 * type token. That type must be able to hold a value of the array's component type or
 * boxed if primitive. A primitive class token is treated like its wrapper class token.
 * <p>
 * A field can be selected by a type token (for example, in the form of a class literal)
 * describing the owner of the field and its simple field name. The direct-field factories
 * also take an expected field type token; the array-field factories take an expected
 * element result type token. The owner type token identifies the class in its defining
 * loader, so the same binary name in a different loader does not select that class.
 * A field declared by the named owner takes precedence. Otherwise, the nearest
 * superclass declaration is selected.
 * <p>
 * The computing function must return a non-default result compatible with the selected
 * storage type. The updater's result type may be that type or a reference supertype of
 * the selected value, boxed if necessary. The explicit owner type token permits a raw
 * generic computing function to select a named field even if its receiver type erases to
 * {@code Object}; erased value type arguments remain the caller's responsibility.
 *
 * <h3 id="computing-functions">Computing functions</h3>
 * An updater retains its computing function and invokes it on the thread calling
 * {@code getOrCompute}. A reference parameter of a field or array computing function may
 * be a supertype of its corresponding coordinate type. Its result may be a subtype of
 * reference storage or use boxing, unboxing, or widening primitive conversion; a broader
 * reference result is checked against the storage type when computed. An incompatible
 * result fails before publication. As with ordinary array stores, a runtime array whose
 * component type is narrower than the declared array type may reject a computed value
 * with {@link ArrayStoreException}.
 *
 * <h3 id="uninitialized-locations">Uninitialized locations</h3>
 * The Java default value of a selected field or array element means uninitialized:
 * {@code null} for references, {@code false} for {@code boolean}, zero for
 * integral types, and positive zero for floating-point types.
 * <p>
 * A computation producing this reserved value throws {@code IllegalArgumentException}
 * without publishing it. Negative floating-point zero and {@code NaN} are permitted.
 * A null-restricted reference field cannot use the {@code null} sentinel and is
 * rejected when an updater selecting that field is created. A null-restricted
 * reference array likewise cannot use the {@code null} sentinel; accessing one of its
 * elements through an updater throws {@code IllegalArgumentException} before computation
 * or return, even if the element already has a value.
 * <p>
 * A nullable wrapper field or array element can distinguish a computed zero or
 * {@code false} from uninitialized storage. If {@code null} is a logical result,
 * a non-null container such as {@link java.util.Optional} can represent it.
 *
 * <h3 id="publish-once">Publish once</h3>
 * The updaters always use a <em>publish once</em> initialization scheme where
 * an invocation reads the selected location with acquire semantics. If it is
 * uninitialized, the updater computes a candidate and attempts to install it atomically
 * with release effects. Computations may race, but every invocation that returns normally
 * returns the value installed in the location, including an invocation whose result
 * was discarded.
 * A losing invocation observes the installed value with acquire effects before
 * returning it. Actions preceding the winner's publication
 * {@linkplain java.util.concurrent##MemoryVisibility happen-before} actions
 * following an invocation that observes it. A computation that throws or produces
 * the reserved value fails even if another invocation has installed a value in
 * the meantime; that updater does not attempt its own installation. A throwable from
 * the computation propagates unchanged. If the location remains uninitialized, a later
 * invocation can retry. A losing compare-and-exchange does not install its candidate.
 * Neither failure nor loss by itself publishes the computing function's actions through
 * the selected location; a computing function can publish through other actions,
 * including another updater. Recursive access to the same uninitialized location is not
 * detected and may recurse without bound.
 * <p>
 * Updaters do not provide a <em>plain</em> access mode. Clients that need plain
 * access would have to resort to conditional assignment such as
 * {@code if (x == 0) x = computeX();}. Without external synchronization, this
 * pattern can return different values to different callers, expose
 * incompletely initialized objects, and/or permit tearing of non-volatile
 * {@code long} and {@code double} values.
 *
 * <h3 id="access-and-storage-ownership">Access and storage ownership</h3>
 * The caller must safely publish any receiver or array and any preexisting
 * values to threads that use an updater. The selected storage must be reserved
 * exclusively for lazy updater use: it must not be reset or otherwise written directly
 * while an updater is in use. However, multiple updaters may share a storage location.
 * Other non-updater writes can defeat the updaters' guarantees. An updater does not
 * retain a receiver passed to {@code getOrCompute}, though it may retain objects captured
 * by its computing function. Publishing a computed reference does not make later
 * mutations of that object thread-safe, and accesses to different locations have no total
 * order.
 * <p>
 * For all factories, the caller must have access to the selected owner and field and any
 * selected array type. Accessing a static field through the updater initializes its
 * declaring class if necessary, following normal var-handle semantics. A field used
 * directly as storage must be non-final; an array field must be final although its
 * elements remain mutable.
 *
 * <h2 id="caching-suppliers">Caching suppliers</h2>
 * Lazy updaters can be used to create different flavors of <em>caching suppliers</em>
 * that internalize the result from its {@linkplain Supplier#get() get()} operation and
 * only returns the internalized value. The following flavors are exemplified below:
 *
 * <table class="striped">
 * <caption>Caching supplier examples</caption>
 * <thead>
 * <tr>
 *     <th scope="col">Supplier</th>
 *     <th scope="col">Race or serialized</th>
 *     <th scope="col">Retry or at most once</th>
 *     <th scope="col">Internalize errors</th>
 *     <th scope="col">Protect against recursion</th>
 * </tr>
 * </thead>
 * <tbody>
 * <tr>
 *     <th scope="row">{@code MemoizedSupplier}</th>
 *     <td>Race</td><td>Retry</td><td>No</td><td>No</td>
 * </tr>
 * <tr>
 *     <th scope="row">{@code SerializedSupplier}</th>
 *     <td>Serialized</td><td>Retry</td><td>No</td><td>No</td>
 * </tr>
 * <tr>
 *     <th scope="row">{@code LazySupplier}</th>
 *     <td>Serialized</td><td>At most once</td><td>Yes</td><td>Yes</td>
 * </tr>
 * </tbody>
 * </table>
 *
 * <h3 id="memoized-suppliers">Memoized suppliers</h3>
 * A <em>memoized supplier</em> can reserve a field for lazy values and
 * share one static updater across all instances. Each getter supplies the storage
 * coordinates. The updater invokes its computing function when the selected location is
 * uninitialized, then returns the value stored there.
 * <p>
 * There are no pre-defined memoized suppliers provided by this class, but a
 * reference-valued memoized cache can use {@link #ofField(Class, Class, String, FieldUpdater.Computer)}:
 * {@snippet lang = java:
 * import java.lang.invoke.LazyUpdaters;
 * import java.util.function.Supplier;
 *
 * class MemoizedSupplier<T> implements Supplier<T> {
 *
 *     // T erases to Object, so this updater can serve every cache instance.
 *     private static final LazyUpdaters.FieldUpdater<MemoizedSupplier<?>, Object> UPDATER =
 *             LazyUpdaters.ofField(MemoizedSupplier.class, Object.class, "value",
 *                     cache -> cache.valueComputer.get());
 *
 *     private final Supplier<? extends T> valueComputer;
 *     private T value;
 *
 *     MemoizedSupplier(Supplier<? extends T> valueComputer) {
 *         this.valueComputer = valueComputer;
 *     }
 *
 *     @Override
 *     @SuppressWarnings("unchecked")
 *     public T get() {
 *         // Only this instance's supplier can populate value, so the result is T.
 *         return (T) UPDATER.getOrCompute(this);
 *     }
 * }
 * }
 * <p>
 * For a primitive {@code int} value, a similar approach can use automatic boxing and
 * unboxing:
 * {@snippet lang = java:
 * import java.lang.invoke.LazyUpdaters;
 * import java.util.function.IntSupplier;
 *
 * class MemoizedIntCache {
 *
 *     private static final LazyUpdaters.FieldUpdater<MemoizedIntCache, Integer> UPDATER =
 *             LazyUpdaters.ofField(MemoizedIntCache.class, int.class, "value",
 *                     cache -> cache.valueComputer.getAsInt());
 *
 *     private final IntSupplier valueComputer;
 *     private int value;
 *
 *     MemoizedIntCache(IntSupplier valueComputer) {
 *         this.valueComputer = valueComputer;
 *     }
 *
 *     int getAsInt() {
 *         return UPDATER.getOrCompute(this);
 *     }
 * }
 * }
 * <p>
 * The primitive {@code int} field above cannot cache a computed zero, because zero
 * marks an uninitialized location. To cache zero, declare {@code value} as
 * {@code Integer} and pass {@code Integer.class} to {@code ofField}; {@code null}
 * then marks uninitialized storage. To cache a logical {@code null} result,
 * store a non-null {@code Optional<T>} and use {@code Optional.empty()} for absence.
 *
 * <h3 id="serialized-suppliers">Serialized suppliers</h3>
 * Clients that need to prevent overlapping calls across threads to a caching supplier
 * can have an updater's computing function call a second updater while holding
 * a monitor. The second updater publishes before the monitor is released.
 * <p>
 * There are no pre-defined serialized suppliers provided by this class, but
 * a reference-valued serialized cache can use
 * {@link #ofField(Class, Class, String, FieldUpdater.Computer)}:
 * {@snippet lang = java:
 * import java.lang.invoke.LazyUpdaters;
 * import java.util.function.Supplier;
 *
 * class SerializedSupplier<T> implements Supplier<T> {
 *
 *     // T erases to Object, so both updaters can serve every cache instance.
 *     // To be used indirectly within a synchronization block only
 *     private static final LazyUpdaters.FieldUpdater<SerializedSupplier<?>, Object> INSTALLER =
 *             LazyUpdaters.ofField(SerializedSupplier.class, Object.class, "value",
 *                     // Uses the valueComputer directly
 *                     cache -> cache.valueComputer.get());
 *
 *     // To be used directly by the get() operations
 *     private static final LazyUpdaters.FieldUpdater<SerializedSupplier<?>, Object> UPDATER =
 *             LazyUpdaters.ofField(SerializedSupplier.class, Object.class, "value",
 *                      // Uses synchronization when computing
 *                     cache -> cache.computeCandidate());
 *
 *     private final Supplier<? extends T> valueComputer;
 *     private final Object computeLock = new Object();
 *     private T value;
 *     private boolean published; // accessed only under computeLock
 *
 *     SerializedSupplier(Supplier<? extends T> valueComputer) {
 *         this.valueComputer = valueComputer;
 *     }
 *
 *     @Override
 *     @SuppressWarnings("unchecked")
 *     public T get() {
 *         // A published value returns without invoking `computeCandidate()`.
 *         return (T) UPDATER.getOrCompute(this);
 *     }
 *
 *     private Object computeCandidate() {
 *         synchronized (computeLock) {
 *             if (published) {
 *                 return UPDATER.getOrCompute(this);
 *             }
 *             // Publishes the value while holding computeLock.
 *             Object result = INSTALLER.getOrCompute(this);
 *             published = true;
 *             return result;
 *         }
 *     }
 * }
 * }
 * {@code INSTALLER} publishes while the monitor is held. A later callback can therefore
 * use {@code UPDATER} to read the published value. Published hits take neither the
 * monitor nor an extra field read. The tradeoff is a second updater.
 *
 * Simply synchronizing only the computing function prevents overlapping computations
 * across threads but can still compute a value more than once. Computations that wait on
 * one another while holding these monitors can deadlock.
 *
 * <h3 id="lazy-supplier">Lazy suppliers</h3>
 * Unlike the {@linkplain ##memoized-suppliers memoized supplier} and the
 * {@linkplain ##serialized-suppliers serialized supplier} above,
 * a <em>lazy supplier</em> invokes its wrapped supplier <em>at most once</em>. It
 * publishes either a result or a failure marker for all subsequent calls. It also
 * protects against recursive invocation of the computing function via its public access
 * operation.
 * <p>
 * There are no pre-defined lazy caches provided by this class but instead, this is
 * provided via {@linkplain LazyConstant#of(Supplier) LazyConstant.of}. Here is how a
 * similar reference-valued lazy cache can use two
 * {@link #ofField(Class, Class, String, FieldUpdater.Computer)} updaters as shown in
 * this example:
 * {@snippet lang = java:
 * import java.lang.invoke.LazyUpdaters;
 * import java.util.NoSuchElementException;
 * import java.util.Objects;
 * import java.util.function.Supplier;
 *
 * class LazySupplier<T> implements Supplier<T> {
 *
 *     // value holds either a result or a class-name marker for a failure.
 *     private record Either<V>(V result, String exceptionType) {
 *
 *         Either {
 *             if ((result == null) == (exceptionType == null)) {
 *                 throw new IllegalArgumentException("Exactly one alternative is required");
 *             }
 *         }
 *
 *         static <V> Either<V> success(V result) {
 *             return new Either<>(Objects.requireNonNull(result), null);
 *         }
 *
 *         static <V> Either<V> failure(String exceptionType) {
 *             return new Either<>(null, Objects.requireNonNull(exceptionType));
 *         }
 *     }
 *
 *     // A generic marker used if the specific outcome cannot be recorded.
 *     private static final Either<?> FALLBACK_FAILURE = Either.failure("java.lang.Error");
 *
 *     private final Supplier<? extends T> computingFunction;
 *     private Either<T> value;
 *
 *     // Guarded by this monitor. A fallback is saved before the supplier runs,
 *     // so a later call can publish without invoking the supplier again.
 *     private boolean published; // INSTALLER has returned a value
 *     private boolean computing; // detects recursive get() calls
 *     private Either<?> pending;
 *
 *     // getOrCompute is called under the monitor of `this` to install pending or read an
 *     // existing value with acquire effects.
 *     private static final LazyUpdaters.FieldUpdater<LazySupplier<?>, Either<?>> INSTALLER =
 *             LazyUpdaters.ofField(LazySupplier.class, Either.class, "value",
 *                     LazySupplier::pendingCandidate);
 *
 *     // getOrCompute reads a published value without entering the monitor of `this`;
 *     // only a `null` read invokes `computeCandidate`.
 *     private static final LazyUpdaters.FieldUpdater<LazySupplier<?>, Either<?>> UPDATER =
 *             LazyUpdaters.ofField(LazySupplier.class, Either.class, "value",
 *                     LazySupplier::computeCandidate);
 *
 *     LazySupplier(Supplier<? extends T> computingFunction) {
 *         this.computingFunction = Objects.requireNonNull(computingFunction);
 *     }
 *
 *     @Override
 *     @SuppressWarnings("unchecked")
 *     public T get() {
 *         Either<T> outcome = (Either<T>) UPDATER.getOrCompute(this);
 *         if (outcome.exceptionType() != null) {
 *             // Recreate the exception from the marker, without its original cause.
 *             throw unableToAccessConstant(outcome.exceptionType(), null);
 *         }
 *         return outcome.result();
 *     }
 *
 *     // INSTALLER invokes this on the current thread while the monitor is held.
 *     private Either<?> pendingCandidate() {
 *         return Objects.requireNonNull(pending);
 *     }
 *
 *     // UPDATER invokes this only after observing null in value.
 *     private Either<?> computeCandidate() {
 *         synchronized (this) {
 *             // The monitor is reentrant, so it cannot detect recursion by itself.
 *             if (computing) {
 *                 throw new IllegalStateException("Recursive computation");
 *             }
 *             // Another call may have published after UPDATER read null.
 *             // A saved candidate needs only an installation retry.
 *             if (published || pending != null) {
 *                 Either<?> outcome = INSTALLER.getOrCompute(this);
 *                 published = true;
 *                 pending = null;
 *                 return outcome;
 *             }
 *
 *             computing = true;
 *             try {
 *                 pending = FALLBACK_FAILURE;
 *                 Throwable failure = null;
 *                 Either<T> candidate;
 *                 try {
 *                     candidate = Either.success(computingFunction.get());
 *                 } catch (Throwable ex) {
 *                     failure = ex;
 *                     // Replace the fallback with the specific exception class.
 *                     candidate = Either.failure(ex.getClass().getName().intern());
 *                 }
 *
 *                 pending = candidate;
 *                 Either<?> outcome = INSTALLER.getOrCompute(this);
 *                 published = true;
 *                 pending = null;
 *                 if (failure != null) {
 *                     // The computing caller can also report the original cause.
 *                     throw unableToAccessConstant(candidate.exceptionType(), failure);
 *                 }
 *                 return outcome;
 *             } finally {
 *                 computing = false;
 *             }
 *         }
 *     }
 *
 *     private static NoSuchElementException unableToAccessConstant(String type, Throwable cause) {
 *         return new NoSuchElementException("Unable to access the lazy value because "
 *                 + type + " was thrown during computation", cause);
 *     }
 * }
 * }
 *
 * <h2 id="indexed-caches">Indexed caches</h2>
 * An array field updater can compute and cache a separate value at each index.
 * The following example compiles patterns on demand and stores them in a static
 * final array field:
 * {@snippet lang = java:
 * import java.lang.invoke.LazyUpdaters;
 * import java.util.regex.Pattern;
 *
 * class CompiledPatterns {
 *
 *     private static final String[] EXPRESSIONS = {"[a-z]+", "[0-9]+"};
 *     private static final Pattern[] CACHE = new Pattern[EXPRESSIONS.length];
 *
 *     private static final LazyUpdaters.StaticArrayFieldUpdater<Pattern> UPDATER =
 *             LazyUpdaters.ofStaticArrayField(CompiledPatterns.class, Pattern.class,
 *                     "CACHE",
 *                     index -> Pattern.compile(EXPRESSIONS[index]));
 *
 *     static Pattern get(int index) {
 *         return UPDATER.getOrCompute(index);
 *     }
 * }
 * }
 * Each index is initialized independently. Compilations for the same index may
 * race, but every normally returning call receives the installed pattern.
 *
 * <h2 id="caching-closeable-resources">Caching closeable resources</h2>
 * Lazy updaters can also cache an {@link AutoCloseable} resource. Closing the cache
 * attempts to close an initialized resource; closing it before the first {@code get()} installs
 * a no-op marker without invoking the factory. Like
 * {@linkplain ##caching-suppliers caching suppliers}, resource caches can vary in
 * how they handle races, failures, and recursion.
 *
 * <p>
 * There are no pre-defined resource caches provided by this class, but a
 * reference-valued memoized resource cache can use
 * {@link #ofField(Class, Class, String, FieldUpdater.Computer)}:
 * {@snippet lang = java:
 * import java.lang.invoke.LazyUpdaters;
 * import java.util.Objects;
 * import java.util.function.Supplier;
 *
 * public final class LazyResource<T extends AutoCloseable>
 *         implements Supplier<T>, AutoCloseable {
 *
 *     private static final AutoCloseable NO_OP = () -> {};
 *
 *     // Publishes a factory result or NO_OP while the instance lock is held.
 *     private static final LazyUpdaters.FieldUpdater<LazyResource<?>, AutoCloseable> INSTALLER =
 *             LazyUpdaters.ofField(LazyResource.class, AutoCloseable.class, "resource",
 *                     LazyResource::createCandidate);
 *
 *     // Reads a published value, or handles an uninitialized field.
 *     private static final LazyUpdaters.FieldUpdater<LazyResource<?>, AutoCloseable> ACCESSOR =
 *             LazyUpdaters.ofField(LazyResource.class, AutoCloseable.class, "resource",
 *                     LazyResource::computeCandidate);
 *
 *     private final Supplier<? extends T> factory;
 *     private final Object lock = new Object();
 *     private AutoCloseable resource;
 *     private volatile boolean closeRequested;
 *     private volatile boolean closeFinished;
 *     private boolean closeHandled; // guarded by lock
 *     private AutoCloseable pendingClose; // guarded by lock
 *     private Throwable closeFailure; // guarded by lock
 *
 *     public LazyResource(Supplier<? extends T> factory) {
 *         this.factory = Objects.requireNonNull(factory);
 *     }
 *
 *     @Override
 *     @SuppressWarnings("unchecked")
 *     public T get() {
 *         AutoCloseable result = ACCESSOR.getOrCompute(this);
 *         if (result == NO_OP || closeRequested) {
 *             throw new IllegalStateException(closeFinished
 *                     ? "Resource is unavailable" : "Resource is closing");
 *         }
 *         return (T) result;
 *     }
 *
 *     private AutoCloseable computeCandidate() {
 *         synchronized (lock) {
 *             if (closeRequested) {
 *                 return NO_OP;
 *             }
 *             return INSTALLER.getOrCompute(this);
 *         }
 *     }
 *
 *     private AutoCloseable createCandidate() {
 *         if (closeRequested) {
 *             return NO_OP;
 *         }
 *
 *         T candidate = Objects.requireNonNull(factory.get());
 *
 *         // Give a candidate produced after close() started to the closing thread.
 *         if (closeRequested) {
 *             pendingClose = candidate;
 *             return NO_OP;
 *         }
 *         return candidate;
 *     }
 *
 *     @Override
 *     public void close() throws Exception {
 *         // A reentrant close cannot wait for the factory to return its candidate.
 *         if (Thread.holdsLock(lock) && !closeHandled) {
 *             throw new IllegalStateException("Cannot close during resource creation");
 *         }
 *         closeRequested = true; // signal an in-flight factory before waiting for lock
 *         synchronized (lock) {
 *             if (closeHandled) {
 *                 if (closeFailure != null) {
 *                     throw new IllegalStateException("Resource close previously failed",
 *                             closeFailure);
 *                 }
 *                 return;
 *             }
 *             try {
 *                 AutoCloseable result = ACCESSOR.getOrCompute(this);
 *                 AutoCloseable toClose = pendingClose != null ? pendingClose : result;
 *                 pendingClose = null;
 *                 closeHandled = true;
 *                 if (toClose != NO_OP) {
 *                     try {
 *                         toClose.close();
 *                     } catch (Exception | Error failure) {
 *                         closeFailure = failure;
 *                         throw failure;
 *                     }
 *                 }
 *             } finally {
 *                 closeFinished = true;
 *             }
 *         }
 *     }
 * }
 * }
 *
 * The field {@code closeRequested} signals a factory already in progress. If that factory
 * returns a candidate after the request, the installer publishes {@code NO_OP}
 * and hands the candidate to {@code close()}, which attempts to close it and reports any failure.
 * A {@code close()} call made after that attempt reports the failure as its cause
 * without retrying the close.
 * {@code closeFinished} becomes true only after that close attempt ends. Since an
 * {@code AutoCloseable} may throw from {@code close()}, finishing the attempt does not
 * necessarily mean the underlying resource closed successfully.
 * <p>
 * This variant is similar to a {@linkplain ##serialized-suppliers serialized supplier} in
 * the sense that it uses two updaters, serializes creation under a lock, returns
 * an already published value without taking that lock, and retries if the factory throws.
 * Similar resource caches can use the other
 * {@linkplain ##caching-suppliers supplier policies} described above.
 * <p>
 * In the {@code LazyResource} example above, a {@code get()} operation can return
 * an already closed resource if {@code close()} is invoked by another thread concurrently.
 * There is also a race during construction: if creation passes its
 * {@code closeRequested} check just before {@code close()} starts, the factory may still
 * run. A factory that calls this cache's {@code close()} on the computing thread
 * gets an {@code IllegalStateException}. It must also avoid recursive {@code get()}
 * calls, which can discard a resource.
 *
 * @implNote The updater factories' implementations install a field or element candidate
 *           as if using
 *           {@link VarHandle#compareAndExchangeRelease VarHandle.compareAndExchangeRelease( ... )}.
 *           The exchange's witness has plain-read effects, so a losing invocation
 *           performs a separate acquire read before returning the winner.
 *           <p>
 *           An updater's published result is not replaced through an updater. This may
 *           permit an implementation to constant-fold repeated accesses when it can
 *           determine a stable path to the storage and its result, perhaps from
 *           a {@code static final} field. Whether and when this optimization occurs is
 *           implementation-dependent. Eliding a storage read must preserve the applicable
 *           {@linkplain ##publish-once publish once} guarantees.
 *           Updaters should generally be declared {@code static final} in order to
 *           present the best performance optimization opportunities for the VM.
 *
 * @since 29
 *
 * @see Optional
 * @see Supplier
 * @see List#ofLazy(int, IntFunction)
 * @see Set#ofLazy(java.util.Set, Predicate)
 * @see Map#ofLazy(java.util.Set, Function)
 * @jls 12.4 Initialization of Classes and Interfaces
 * @jls 17.4.5 Happens-before Order
 */
@PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
public final class LazyUpdaters {

    // Suppresses default constructor, ensuring non-instantiability.
    private LazyUpdaters() {}

    /**
     * {@return a lazy updater for a static field accessible to the caller}
     * <p>
     * This method uses {@linkplain ##publish-once publish once} and the supplied field
     * as storage. Its result is a {@link StaticFieldUpdater} whose
     * {@linkplain StaticFieldUpdater#getOrCompute() getOrCompute()} method accesses that
     * field; the computing function is invoked only when the field has its reserved Java
     * default value. The field must be static and non-final. Access is checked with
     * the caller's privileges.
     * <p>
     * This method is caller-sensitive: a helper that invokes it supplies the helper's
     * access privileges. Once the updater is created, calling its
     * {@linkplain StaticFieldUpdater#getOrCompute() getOrCompute()} method does not
     * repeat the caller access check. A caller may select its own private static field
     * or one in a nestmate. Access to an unrelated private field is denied.
     * <p>
     * The {@code computingFunction} may produce a subtype of {@code V}. The type
     * parameter {@code V} is not available at run time. The caller must ensure that
     * {@code computingFunction} produces values compatible with the field and
     * that any preexisting field value can be used as {@code V}. Incompatible
     * computed values fail before publication, as described in
     * {@linkplain ##computing-functions Computing functions}.
     *
     * @param owner the type through which the static field is selected
     * @param fieldType the expected field type
     * @param name the simple name of the field
     * @param computingFunction the function that computes a non-default field value
     * @param <V> the result type expected by the caller
     * @throws IllegalCallerException if there is no caller frame
     * @throws IllegalArgumentException if {@code name} is empty or contains a
     *         dot, the field cannot be accessed, is not a suitable mutable
     *         static location, or the required access mode is unsupported
     * @since 29
     */
    @CallerSensitive
    public static <V> StaticFieldUpdater<V> ofStaticField(Class<?> owner,
                                                          Class<? super V> fieldType,
                                                          String name,
                                                          StaticFieldUpdater.Computer<? extends V> computingFunction) {
        // Keep caller identification in this entry point. A helper accepting
        // a caller Class could be invoked reflectively with a forged argument.
        final Class<?> caller = Reflection.getCallerClass();
        checkCaller(caller);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(fieldType);
        checkName(name);
        Objects.requireNonNull(computingFunction);
        final Field field = findField(caller, owner, fieldType, name, true);
        checkFieldFinalState(field, false);
        throw new UnsupportedOperationException();
    }


    /**
     * {@return a lazy updater for an instance field accessible to the caller}
     * <p>
     * This method uses {@linkplain ##publish-once publish once} and the supplied field
     * as storage. Its result is a {@link FieldUpdater} whose
     * {@linkplain FieldUpdater#getOrCompute(Object) getOrCompute()} method accesses that
     * field; the computing function is invoked only when the field has its reserved Java
     * default value. The field must be neither static nor final. Access is checked with
     * the caller's privileges.
     * <p>
     * This method is caller-sensitive: a helper that invokes it supplies the helper's
     * access privileges. Once the updater is created, calling its
     * {@linkplain FieldUpdater#getOrCompute(Object) getOrCompute()} method does not
     * repeat the caller access check. A caller may select its own private non-static
     * field or one in a nestmate. Access to an unrelated private field is denied.
     * <p>
     * If the field is protected and declared in a different runtime package from
     * the caller, the updater accepts only receivers that are instances of the
     * caller class or its subclasses, even if {@code T} is broader. This
     * restriction is checked when {@code getOrCompute} is invoked.
     * <p>
     * The {@code computingFunction} may produce a subtype of {@code V}. The type parameter {@code V} is
     * not available at run time. The caller must ensure that
     * {@code computingFunction} produces values compatible with the field and
     * that any preexisting field value can be used as {@code V}. Incompatible
     * computed values fail before publication, as described in
     * {@linkplain ##computing-functions Computing functions}.
     *
     * @param receiverType the type through which the instance field is selected
     * @param fieldType the expected field type
     * @param name the simple name of the field
     * @param computingFunction the function that computes a non-default field value
     * @param <T> the receiver type
     * @param <V> the result type expected by the caller
     * @throws IllegalCallerException if there is no caller frame
     * @throws IllegalArgumentException if {@code name} is empty or contains a
     *         dot, the field cannot be accessed, is not a suitable mutable
     *         instance location, or the required access mode is unsupported
     * @since 29
     */
    @CallerSensitive
    public static <T, V> FieldUpdater<T, V> ofField(Class<? super T> receiverType,
                                                    Class<?> fieldType,
                                                    String name,
                                                    FieldUpdater.Computer<? super T, ? extends V> computingFunction) {
        // Keep caller identification in this entry point. A helper accepting
        // a caller Class could be invoked reflectively with a forged argument.
        final Class<?> caller = Reflection.getCallerClass();
        checkCaller(caller);
        Objects.requireNonNull(receiverType);
        Objects.requireNonNull(fieldType);
        checkName(name);
        Objects.requireNonNull(computingFunction);
        final Field field = findField(caller, receiverType, fieldType, name, false);
        checkFieldFinalState(field, false);
        // This allows us to run-time check `protectedReceiver.cast(receiver)`
        // for an instance field that is protected and declared outside the caller’s
        // runtime package,
        final Class<?> protectedReceiver =  Modifier.isProtected(field.getModifiers())
                        && !VerifyAccess.isSamePackage(caller, field.getDeclaringClass())
                        ? caller : null;
        throw new UnsupportedOperationException();
    }

    /**
     * {@return a lazy updater for a static array field accessible to the caller}
     * <p>
     * This method uses {@linkplain ##publish-once publish once} and the supplied array
     * field and index as storage. Its result is a {@link StaticArrayFieldUpdater} whose
     * {@linkplain StaticArrayFieldUpdater#getOrCompute(int) getOrCompute()} method
     * accesses that array field's element;
     * the computing function is invoked only when the field's element has its reserved Java
     * default value. The array field must be static and final. Access is checked with
     * the caller's privileges.
     * <p>
     * This method is caller-sensitive: a helper that invokes it supplies the helper's
     * access privileges. Once the updater is created, calling its
     * {@linkplain StaticArrayFieldUpdater#getOrCompute(int) getOrCompute()} method does
     * not repeat the caller access check. A caller may select its own private static
     * final array field or one in a nestmate. Access to an unrelated private field is
     * denied.
     * <p>
     * The {@code computingFunction} may produce a subtype of {@code V}. The type parameter {@code V} is
     * not available at run time. The caller must ensure that
     * {@code computingFunction} produces values compatible with the field's elements and
     * that any preexisting element value can be used as {@code V}. Incompatible
     * computed values fail before publication, as described in
     * {@linkplain ##computing-functions Computing functions}.
     *
     * @param owner the type through which the static field is selected
     * @param arrayComponentType the expected element result type
     * @param name the simple name of the field
     * @param computingFunction the function that computes a non-default element value
     * @param <V> the result type expected by the caller
     * @throws IllegalCallerException if there is no caller frame
     * @throws IllegalArgumentException if {@code name} is empty or contains a
     *         dot, the field cannot be accessed, is not a suitable static array
     *         location, or the required access mode is unsupported
     * @since 29
     */
    @CallerSensitive
    public static <V> StaticArrayFieldUpdater<V> ofStaticArrayField(Class<?> owner,
                                                                    Class<? super V> arrayComponentType,
                                                                    String name,
                                                                    StaticArrayFieldUpdater.Computer<? extends V> computingFunction) {
        // Keep caller identification in this entry point. A helper accepting
        // a caller Class could be invoked reflectively with a forged argument.
        final Class<?> caller = Reflection.getCallerClass();
        checkCaller(caller);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(arrayComponentType);
        checkName(name);
        Objects.requireNonNull(computingFunction);
        final Field field = findField(caller, owner, Object.class, name, true);
        checkFieldFinalState(field, true);
        checkArrayType(caller, field.getType());
        checkArrayComponentType(field, arrayComponentType);
        throw new UnsupportedOperationException();
    }

    /**
     * {@return a lazy updater for an instance array field accessible to the caller}
     * <p>
     * This method uses {@linkplain ##publish-once publish once} and the supplied array
     * field and index as storage. Its result is an {@link ArrayFieldUpdater} whose
     * {@linkplain ArrayFieldUpdater#getOrCompute(Object, int) getOrCompute()} method
     * accesses that array field's element;
     * the computing function is invoked only when the field's element has its reserved Java
     * default value. The array field must be non-static and final. Access is checked with
     * the caller's privileges.
     * <p>
     * This method is caller-sensitive: a helper that invokes it supplies the helper's
     * access privileges. Once the updater is created, calling its
     * {@linkplain ArrayFieldUpdater#getOrCompute(Object, int) getOrCompute()} method does
     * not repeat the caller access check. A caller may select its own private final
     * non-static array field or one in a nestmate. Access to an unrelated private field
     * is denied.
     * <p>
     * If the field is protected and declared in a different runtime package from
     * the caller, the updater accepts only receivers that are instances of the
     * caller class or its subclasses, even if {@code T} is broader. This
     * restriction is checked when {@code getOrCompute} is invoked.
     * <p>
     * The {@code computingFunction} may produce a subtype of {@code V}. The type parameter {@code V} is
     * not available at run time. The caller must ensure that
     * {@code computingFunction} produces values compatible with the field's elements and
     * that any preexisting element value can be used as {@code V}. Incompatible
     * computed values fail before publication, as described in
     * {@linkplain ##computing-functions Computing functions}.
     *
     * @param receiverType the type through which the instance array field is selected
     * @param arrayComponentType the expected element result type
     * @param name the simple name of the field
     * @param computingFunction the function that computes a non-default element value
     * @param <T> the receiver type
     * @param <V> the result type expected by the caller
     * @throws IllegalCallerException if there is no caller frame
     * @throws IllegalArgumentException if {@code name} is empty or contains a
     *         dot, the field cannot be accessed, is not a suitable instance array
     *         location, or the required access mode is unsupported
     * @since 29
     */
    @CallerSensitive
    public static <T, V> ArrayFieldUpdater<T, V> ofArrayField(Class<? super T> receiverType,
                                                              Class<? super V> arrayComponentType,
                                                              String name,
                                                              ArrayFieldUpdater.Computer<? super T, ? extends V> computingFunction) {
        // Keep caller identification in this entry point. A helper accepting
        // a caller Class could be invoked reflectively with a forged argument.
        final Class<?> caller = Reflection.getCallerClass();
        checkCaller(caller);
        Objects.requireNonNull(receiverType);
        Objects.requireNonNull(arrayComponentType);
        checkName(name);
        Objects.requireNonNull(computingFunction);
        final Field field = findField(caller, receiverType, Object.class, name, false);
        checkFieldFinalState(field, true);
        checkArrayType(caller, field.getType());
        checkArrayComponentType(field, arrayComponentType);
        // Capture the receiver restriction for a protected instance array field.
        final Class<?> protectedReceiver = Modifier.isProtected(field.getModifiers())
                && !VerifyAccess.isSamePackage(caller, field.getDeclaringClass())
                ? caller : null;
        throw new UnsupportedOperationException();
    }

    /**
     * Represents a memoized supplier of results.
     * <p>
     * Every normally returning invocation returns the same installed result.
     * <p>
     * This is a functional interface whose functional method is
     * {@link #getOrCompute()}.
     *
     * @param <V> the type of results returned by this memoized supplier
     *
     * @since 29
     */
    @FunctionalInterface
    public interface StaticFieldUpdater<V> {

        /**
         * {@return a non-default memoized result, otherwise computes and tries to install
         *          a non-default result}
         * <p>
         * Every normally returning invocation returns the same non-default value.
         */
        V getOrCompute();

        /**
         * Represents a supplier of non-default results.
         *
         * <p>There is no requirement that a new or distinct result be returned each
         * time the supplier is invoked.
         *
         * @param <V> the type of results returned by this supplier
         *
         * @since 29
         */
        @FunctionalInterface
        interface Computer<V> {
            /**
             * {@return a non-default result}
             */
            V compute();
        }

    }

    /**
     * Represents a memoized function of a receiver.
     * <p>
     * Every normally returning invocation returns the same installed result for any
     * given receiver.
     * <p>
     * This is a functional interface whose functional method is
     * {@link #getOrCompute(Object)}.
     *
     * @param <T> the type of the receiver
     * @param <V> the type of results returned by this memoized function
     *
     * @since 29
     */
    @FunctionalInterface
    public interface FieldUpdater<T, V> {

        /**
         * {@return a non-default memoized result, otherwise computes and tries to install
         *          a non-default result}
         * <p>
         * Every normally returning invocation returns the same non-default value for any
         * given receiver.
         *
         * @param receiver the object holding the selected field
         * @throws ClassCastException if the selected field is protected and
         *         declared in a different runtime package from the factory caller,
         *         and a non-null {@code receiver} is not an instance of that caller
         *         class or one of its subclasses
         */
        V getOrCompute(T receiver);

        /**
         * Represents a function that accepts one argument and produces a non-default result.
         *
         * @param <T> the type of the receiver
         * @param <V> the type of the result of the function
         *
         * @since 29
         */
        @FunctionalInterface
        interface Computer<T, V> {
            /**
             * {@return the value of applying this function to the given argument}
             *
             * @param receiver the object holding the selected field
             */
            V compute(T receiver);
        }

    }

    /**
     * Represents a memoized function of an array index.
     * <p>
     * Each index selects an element of a static array field. For the same
     * selected element, every normally returning invocation returns the same
     * installed value.
     * <p>
     * This is a functional interface whose functional method is
     * {@link #getOrCompute(int)}.
     *
     * @param <V> the type of values returned by this memoized function
     * @since 29
     */
    @FunctionalInterface
    public interface StaticArrayFieldUpdater<V> {

        /**
         * {@return the non-default value installed at the selected index}
         * If the element is uninitialized, invokes the computing function and
         * attempts to install a valid candidate.
         *
         * @param index the index of the selected array element
         */
        V getOrCompute(int index);

        /**
         * Represents a function that computes an array element value from its
         * index. It may be invoked more than once for the same element,
         * including concurrently.
         *
         * @param <V> the type of value computed
         * @since 29
         */
        @FunctionalInterface
        interface Computer<V> {
            /**
             * {@return a non-default candidate value for the selected array element}
             *
             * @param index the index of the selected array element
             */
            V compute(int index);
        }

    }

    /**
     * Represents a memoized function of a receiver and an array index.
     * <p>
     * Each receiver and index pair selects an element of an instance array
     * field. For the same selected element, every normally returning invocation
     * returns the same installed value.
     * <p>
     * This is a functional interface whose functional method is
     * {@link #getOrCompute(Object, int)}.
     *
     * @param <T> the type of the receiver
     * @param <V> the type of values returned by this memoized function
     * @since 29
     */
    @FunctionalInterface
    public interface ArrayFieldUpdater<T, V> {

        /**
         * {@return the non-default value installed at the selected array element}
         * If the element is uninitialized, invokes the computing function and
         * attempts to install a valid candidate.
         *
         * @param receiver the object holding the selected array field
         * @param index the index of the selected array element
         * @throws ClassCastException if the selected field is protected and
         *         declared in a different runtime package from the factory caller,
         *         and a non-null {@code receiver} is not an instance of that caller
         *         class or one of its subclasses
         */
        V getOrCompute(T receiver, int index);

        /**
         * Represents a function that computes an array element value from a
         * receiver and index. It may be invoked more than once for the same
         * element, including concurrently.
         *
         * @param <T> the type of the receiver
         * @param <V> the type of value computed
         * @since 29
         */
        @FunctionalInterface
        interface Computer<T, V> {
            /**
             * {@return a non-default candidate value for the selected array element}
             *
             * @param receiver the object holding the selected array field
             * @param index the index of the selected array element
             */
            V compute(T receiver, int index);
        }

    }

}
