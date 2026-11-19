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

import static jdk.internal.invoke.LazyInitializersUtil.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.*;

/**
 * Factory methods for stable access to fields and array elements.
 * <p>
 * Stable accessors use caller-owned storage. An invocation of {@code getOrCompute}
 * returns a non-default value already in the selected location without computing.
 * If no non-default value is observed, it invokes a <em>computing function</em> and
 * attempts to install the resulting candidate value in the selected location.
 * The {@code get} methods observe a location without computing or installing a value,
 * returning an empty {@code Optional} if the read observes the reserved default value.
 * <p>
 * Merely creating a stable accessor neither reads that storage nor invokes its computing
 * function.
 * <p>
 * For ready-made suppliers that caches one reference, use
 * {@link Lazy#ofSupplier(Supplier) Lazy.ofSupplier} and overloads thereof. The
 * {@link Lazy#ofList(Lazy.Mode, int, IntFunction)},
 * {@link Lazy#ofSet(Lazy.Mode, Set, Predicate)}, and
 * {@link Lazy#ofMap(Lazy.Mode, Set, Function)} factories cache list elements,
 * membership decisions, and map values, respectively. These factories manage their
 * own storage and computation coordination, offering {@link Lazy.Mode#ONCE at-most-once},
 * {@link Lazy.Mode#SERIALIZED serialized retry}, and
 * {@link Lazy.Mode#PUBLISH racing publication} modes.
 * <p>
 * The accessors in this class operate on caller-owned storage. Computations may race
 * when using {@code getOrCompute}; the {@code getOrComputeSerialized} methods instead
 * support {@linkplain ##serialized-computation serialized computation} using a
 * caller-supplied monitor. Neither form records failures, so failed computations
 * may be retried.
 *
 * <h2 id="access-shapes">Locations and access shapes</h2>
 * Each factory selects a named field and returns a distinct implementation of
 * {@link OfField OfField&lt;T, C, R&gt;} or
 * {@link OfArrayField OfArrayField&lt;T, C, R&gt;}.
 * {@code T} is the receiver type, {@code C} is the computation context type, and
 * {@code R} is the result type, boxed for primitive storage. {@link Void} represents
 * an absent receiver or context; callers supply {@code null} for the corresponding argument.
 * The factory determines the storage shape and the restrictions on the returned
 * implementation:
 * <table class="striped">
 * <caption>Accessor types and invocations without a computation context</caption>
 * <thead>
 * <tr><th scope="col">Storage</th><th scope="col">Accessor type</th>
 *     <th scope="col">Invocation</th></tr>
 * </thead>
 * <tbody>
 * <tr><th scope="row">Static field</th>
 *     <td>{@link OfField OfField&lt;Void, Void, R&gt;}</td>
 *     <td>{@link OfField#getOrCompute(Object, Object)
 *         getOrCompute(null, null)}</td></tr>
 * <tr><th scope="row">Instance field</th>
 *     <td>{@link OfField OfField&lt;T, Void, R&gt;}</td>
 *     <td>{@link OfField#getOrCompute(Object, Object)
 *         getOrCompute(receiver, null)}</td></tr>
 * <tr><th scope="row">Element of a static array field</th>
 *     <td>{@link OfArrayField OfArrayField&lt;Void, Void, R&gt;}</td>
 *     <td>{@link OfArrayField#getOrCompute(Object, int, Object)
 *         getOrCompute(null, index, null)}</td></tr>
 * <tr><th scope="row">Element of an instance array field</th>
 *     <td>{@link OfArrayField OfArrayField&lt;T, Void, R&gt;}</td>
 *     <td>{@link OfArrayField#getOrCompute(Object, int, Object)
 *         getOrCompute(receiver, index, null)}</td></tr>
 * </tbody>
 * </table>
 * <table class="striped">
 * <caption>Accessor types and invocations with a computation context</caption>
 * <thead>
 * <tr><th scope="col">Storage</th><th scope="col">Accessor type</th>
 *     <th scope="col">Invocation</th></tr>
 * </thead>
 * <tbody>
 * <tr><th scope="row">Static field</th>
 *     <td>{@link OfField OfField&lt;Void, C, R&gt;}</td>
 *     <td>{@link OfField#getOrCompute(Object, Object)
 *         getOrCompute(null, context)}</td></tr>
 * <tr><th scope="row">Instance field</th>
 *     <td>{@link OfField OfField&lt;T, C, R&gt;}</td>
 *     <td>{@link OfField#getOrCompute(Object, Object)
 *         getOrCompute(receiver, context)}</td></tr>
 * <tr><th scope="row">Element of a static array field</th>
 *     <td>{@link OfArrayField OfArrayField&lt;Void, C, R&gt;}</td>
 *     <td>{@link OfArrayField#getOrCompute(Object, int, Object)
 *         getOrCompute(null, index, context)}</td></tr>
 * <tr><th scope="row">Element of an instance array field</th>
 *     <td>{@link OfArrayField OfArrayField&lt;T, C, R&gt;}</td>
 *     <td>{@link OfArrayField#getOrCompute(Object, int, Object)
 *         getOrCompute(receiver, index, context)}</td></tr>
 * </tbody>
 * </table>
 * Each factory has a context-free variant and a variant with a computation context.
 * The latter returns the same accessor interface parameterized with {@code C}
 * instead of {@code Void}; callers supply the context as the last argument to
 * {@code getOrCompute}. The {@code get} methods take the receiver and, for arrays,
 * the index, but no context. The context may be unrelated to the storage owner and
 * is not part of the storage coordinates:
 * invocations selecting the same location share the installed value even if they
 * supply different contexts.
 * <p>
 * Sharing an interface does not relax the restrictions imposed by a factory.
 * An instance-field or instance-array accessor requires a non-null receiver
 * compatible with the selected field; a null receiver causes
 * {@link NullPointerException}. An incompatible receiver, including one that
 * violates a protected access restriction, causes {@link ClassCastException}.
 * A static-field or static-array accessor requires a null receiver, and a
 * context-free accessor requires a null context. Supplying a non-null argument
 * in either of these {@code Void} positions, for example through a raw interface,
 * causes {@code ClassCastException}. For an accessor created with a context,
 * a non-null context must be an instance of the type represented by its
 * {@code contextType} token; otherwise, {@code ClassCastException} is thrown.
 * These restrictions apply on every invocation,
 * even if a value has already been installed. Staticness and the presence of a
 * context are fixed by the factory, not inferred from null arguments at invocation.
 * <p>
 * Field accessors support reference and primitive fields. Array accessors select a
 * static or instance field containing a one-dimensional reference or primitive array. The
 * underlying array's identity and the index identify the cached location; different
 * fields or receivers can share an array and therefore share its cached elements. Values of
 * primitive fields and array elements are represented by their wrapper types in the
 * accessor API.
 *
 * <h2 id="field-selection">Field selection and access checks</h2>
 * The owner type token and simple field name select a field independently of the
 * computation context. A declaration in the named owner takes precedence; otherwise,
 * the nearest superclass declaration is selected. An unsuitable declaration is
 * rejected rather than skipped in favor of a superclass field.
 * <p>
 * All factories are caller-sensitive: the immediate caller's privileges determine
 * access to the owner and selected field. Array factories also check access to the
 * declared component type and the element type token, boxed if primitive. These
 * access checks are performed when the accessor is created. Invoking it does not
 * repeat them. For a protected instance field declared in a different runtime package
 * from the caller, the receiver must be an instance of the caller class or a subclass.
 * <p>
 * A field used directly as storage must be non-final and annotated with
 * {@link ReflectiveAccessOnly} in field mode ({@code arrayElements = false}).
 * An array factory instead selects a final static or instance array field annotated
 * with {@code @ReflectiveAccessOnly(arrayElements = true)}.
 * The annotation declares intent but does not establish exclusive access; the
 * {@linkplain ##access-and-storage-ownership storage-ownership requirements} still apply.
 * Creating an accessor for a static field does not initialize its declaring class.
 * Invoking {@code get} or {@code getOrCompute} on that accessor initializes the
 * declaring class if necessary, including when the field holds an array.
 *
 * <h2 id="computing-functions">Computing functions</h2>
 * An accessor retains the computing function supplied at creation and invokes it on
 * the thread calling {@code getOrCompute}. A context-aware accessor can use dynamic
 * inputs without requiring a new function at each invocation. A context may be
 * {@code null} if the computing function accepts it.
 * <p>
 * Context-free field factories accept a {@link Supplier}; context-free array
 * factories accept an {@link IntFunction} receiving the element index.
 * Context-taking factories accept {@link OfField.Computer} or
 * {@link OfArrayField.Computer}, receiving the context and, for arrays, the index.
 * The null context placeholder of a context-free accessor is not passed to its
 * computing function.
 * <p>
 * A {@code contextType} token must represent a reference type, including an array
 * type or a value class. Primitive type tokens, including {@code void.class}, are
 * rejected with {@link IllegalArgumentException} when the accessor is created.
 * The token describes the context type or a supertype. The caller must ensure that
 * contexts are suitable for the computing function; checking the token does not
 * validate erased type arguments or a more specific type required by that function.
 * <p>
 * Neither the receiver nor the array itself is supplied automatically to the computing
 * function. A receiver-dependent field computation can pass the receiver explicitly
 * as its context using {@code getOrCompute(receiver, receiver)}. A computation that
 * accesses another managed location must use an accessor, obtaining any required
 * receiver through its context or a captured reference.
 *
 * <h2 id="type-compatibility">Type compatibility</h2>
 * Factory type tokens describe the values that can be read from storage:
 * <ul>
 * <li>For a field factory, a primitive {@code fieldType} must equal the declared field
 *     type. A reference {@code fieldType} must be a supertype of, or equal to, the
 *     declared field type, boxed if primitive.</li>
 * <li>For an array factory, primitive component and element type tokens are treated
 *     as their wrapper types. The element type token must then be a supertype of,
 *     or equal to, the declared component type. An array class or {@code void.class}
 *     is not a supported element type token.</li>
 * </ul>
 * These token checks do not perform numeric widening. For example, an {@code int}
 * location can use {@code Integer.class} or {@code Number.class}, but not
 * {@code Long.class}. The caller must ensure that both computed and preexisting
 * stored values, boxed if primitive, can be used as {@code R}; erased type arguments
 * remain the caller's responsibility.
 * <p>
 * A computed candidate is checked against the storage type before installation.
 * For reference storage, the candidate must be assignable to the field type or the
 * runtime array component type. An incompatible reference-field candidate throws
 * {@link ClassCastException}. An incompatible reference-array candidate throws
 * {@link ArrayStoreException}, including when the runtime component type is narrower
 * than the declared one. For primitive storage, the candidate must be a wrapper
 * value that can be unboxed and converted to the storage type by an identity
 * conversion or a widening primitive conversion (section {@jls 5.1.2}).
 * A non-null candidate that cannot be converted in this way throws
 * {@code ClassCastException}. This includes narrowing conversions and conversions
 * between {@code boolean} and numeric types. A null candidate for primitive storage
 * throws {@code NullPointerException}.
 * The reserved-default check applies after conversion. Reads return the stored
 * value, boxed using the wrapper for its storage type if primitive.
 *
 * <h2 id="uninitialized-locations">Uninitialized locations</h2>
 * The Java default value of a selected location means uninitialized:
 * {@code null} for reference fields and array elements, {@code false} for
 * {@code boolean} fields and array elements, zero for integral fields and array
 * elements, and positive zero for floating-point fields and array elements.
 * <p>
 * A computation producing this reserved value throws {@code IllegalArgumentException}
 * without publishing it. Negative floating-point zero and {@code NaN} are permitted.
 * A null-restricted reference field cannot use the {@code null} sentinel and is
 * rejected when an accessor selecting that field is created. A null-restricted
 * reference array likewise cannot use the {@code null} sentinel; accessing one of its
 * elements through an accessor throws {@code IllegalArgumentException} before
 * computation or return, even if the element already has a value.
 * <p>
 * A nullable wrapper field or array element can distinguish a computed zero or
 * {@code false} from uninitialized storage. If {@code null} is a logical result,
 * a non-null container such as {@link java.util.Optional} can represent it.
 *
 * <h2 id="publish-once">Publish once</h2>
 * An invocation of {@code getOrCompute} reads the selected location. If it is
 * uninitialized, the accessor computes a candidate and attempts to atomically replace
 * the reserved default value with that candidate. This <em>installation</em> publishes
 * the value with the {@linkplain ##memory-effects memory effects} described below.
 * <p>
 * Computations may race, but every invocation of {@code getOrCompute} that returns
 * normally returns the installed value, including an invocation whose candidate was
 * discarded. The {@code get} methods only observe the location.
 * <p>
 * A computation that throws or produces an invalid candidate fails even if another
 * invocation installs a value in the meantime; the failing invocation does not
 * attempt installation. A throwable from the computation propagates unchanged.
 * If the location remains uninitialized, a later invocation can retry. Recursive
 * access to the same uninitialized location is not detected and may recurse without bound.
 *
 * <h2 id="serialized-computation">Serialized computation</h2>
 * The {@link OfField#getOrComputeSerialized(Object, Object, Object)} and
 * {@link OfArrayField#getOrComputeSerialized(Object, Object, int, Object)} methods
 * accept a monitor to serialize computation. If an installed value is observed,
 * they return it without acquiring the monitor. Otherwise, they acquire the monitor
 * and invoke {@code getOrCompute}, which rechecks the location before computing.
 * The monitor remains held through computation and publication. A thread that
 * waited for the monitor therefore returns a value installed by a preceding
 * invocation without computing another candidate.
 * <p>
 * To prevent concurrent computations for a location, every invocation that can
 * initialize it must use the same monitor, including invocations through other
 * accessors or aliases of an array. Calls to {@code getOrCompute}, or serialized
 * calls using a different monitor, can still race with these computations.
 * Sharing a monitor across different locations also serializes their computations;
 * separate monitors allow independent locations to be computed concurrently.
 * Computing functions must not release the monitor, for example by calling
 * {@link Object#wait() wait} on it.
 * <p>
 * A failed computation exits the synchronized block without recording the failure.
 * A waiting or later invocation may then retry if the location remains uninitialized.
 * This is also the retry policy of {@link Lazy.Mode#SERIALIZED}, whereas
 * {@link Lazy.Mode#ONCE} records failures and does not retry. Unlike
 * {@link Lazy.Mode#SERIALIZED}, these methods do not detect recursive computation
 * by the same thread. Monitors are reentrant, so such computation may recurse
 * without bound. Dependencies between computations using different monitors can
 * deadlock.
 * <p>
 * The monitor must be a non-null {@linkplain Objects#hasIdentity(Object) identity
 * object}, even when the value is already installed. It is independent of the
 * receiver and computation context and does not change the identity of the cached
 * location. Passing a monitor does not itself cause the accessor to retain it
 * after the invocation returns.
 * Acquiring and releasing the monitor have their usual memory effects; calls that
 * observe an installed value before locking do not acquire the monitor.
 *
 * <h2 id="memory-effects">Memory effects</h2>
 * All access methods, including the serialized variants, read the selected location
 * with acquire semantics. A successful installation publishes the candidate with
 * release effects. An invocation that loses an installation race observes the installed value
 * with acquire effects before returning it. These effects are independent of whether
 * a field used directly as storage is declared {@code volatile}.
 * <p>
 * For each lazily initialized location, actions in the publishing thread preceding
 * publication of the installed value
 * {@linkplain java.util.concurrent##MemoryVisibility happen-before} actions
 * following an invocation in another thread that observes that value.
 * These guarantees do not impose a total order on accesses to different locations.
 * Subsequent reads of the installed value provide no additional memory ordering or
 * visibility guarantees for actions performed after publication. Publishing a
 * reference does not make later mutations of the referenced object thread-safe.
 * Failure or loss of an installation race does not by itself publish the computing
 * function's actions through the selected location. A computing function can publish
 * through other actions, including another initialization.
 *
 * <h2 id="access-and-storage-ownership">Access and storage ownership</h2>
 * The caller must safely publish any receiver, array, and preexisting values to
 * threads that use an accessor. While a field or array element is
 * managed by stable accessors, all reads and writes of that location must be
 * performed through those accessors. Clients, including computing functions,
 * must not read, write, or reset the location directly or through another access
 * mechanism. This requirement also applies to fields declared {@code volatile}.
 * <p>
 * Access outside stable accessors is not covered by their guarantees. In particular,
 * plain reads can observe default or stale values and expose incompletely initialized
 * state in referenced objects. Plain reads of non-volatile {@code long} or {@code double}
 * locations may also observe torn values. Direct writes can invalidate the
 * publish-once and stability guarantees. However, multiple stable accessors may share a
 * storage location.
 * <p>
 * An accessor does not retain a receiver passed to {@code get} or
 * {@code getOrCompute}, though it may retain objects captured by its computing function.
 * <p>
 * For an array accessor, the selected {@code final} array field must contain a non-null
 * array. The array reference itself may be read normally (including reading its
 * {@code length} value); all aliases of the array remain subject to
 * the element-access restrictions above.
 *
 * <h2 id="encapsulating-stable-accessors">Encapsulating stable accessors</h2>
 * A class can encapsulate stable accessors to cache values in its own fields and arrays.
 *
 * <h3 id="scalar-caches">Scalar caches</h3>
 * The following small class caches a String in a reserved field without a
 * computation context. The accessor is shared across all instances, while each
 * instance holds its own cached value:
 * {@snippet lang = java:
 * import java.lang.invoke.StableAccessors;
 * import java.lang.invoke.StableAccessors.OfField;
 * import java.lang.invoke.ReflectiveAccessOnly;
 *
 * class StringCache {
 *
 *     private static final OfField<StringCache, Void, String> ACCESSOR =
 *             StableAccessors.ofField(
 *                     StringCache.class,   // Field owner
 *                     String.class,        // Field type
 *                     "value",             // Field name
 *                     () -> "Some value"); // Computing function
 *
 *     @ReflectiveAccessOnly
 *     private String value;
 *
 *     public String get() {
 *         return ACCESSOR.getOrCompute(this, null);
 *     }
 * }
 *}
 * To supply configuration as a computation context, it is possible to add a record and
 * replace the accessor and {@code get} method with these members:
 * {@snippet lang = java:
 * record Configuration(String prefix, int number) {}
 *
 * private static final StableAccessors.OfField<StringCache, Configuration, String>
 *         ACCESSOR = StableAccessors.ofField(
 *                 StringCache.class,   // Field owner
 *                 String.class,        // Field type
 *                 "value",             // Field name
 *                 Configuration.class, // Context type
 *                 config -> config.prefix() + config.number()); // Computing function
 *
 * public String get(Configuration config) {
 *     return ACCESSOR.getOrCompute(this, config);
 * }
 *}
 * The first successful installation determines the value for this {@code StringCache}
 * instance; supplying a different configuration does not create a separate cached value.
 *
 * <h3 id="static-scalar-caches">Static scalar caches</h3>
 * Replacing the field, accessor, and {@code get} method in the context-free
 * {@code StringCache} with
 * the following members gives a context-free <em>static</em> cache:
 * {@snippet lang = java:
 * @ReflectiveAccessOnly
 * private static String value;
 *
 * private static final StableAccessors.OfField<Void, Void, String> ACCESSOR =
 *         StableAccessors.ofStaticField(
 *                 StringCache.class,   // Field owner
 *                 String.class,        // Field type
 *                 "value",             // Field name
 *                 () -> "Some value"); // Computing function
 *
 * public static String get() {
 *     return ACCESSOR.getOrCompute(null, null);
 * }
 *}
 *
 * <h3 id="indexed-caches">Indexed caches</h3>
 * An array accessor caches a separate value at each index:
 * {@snippet lang = java:
 * import java.lang.invoke.StableAccessors;
 * import java.lang.invoke.StableAccessors.OfArrayField;
 * import java.lang.invoke.ReflectiveAccessOnly;
 *
 * class ArrayCache {
 *
 *     private static final OfArrayField<ArrayCache, Void, String> ACCESSOR =
 *             StableAccessors.ofArrayField(
 *                     ArrayCache.class,   // Field owner
 *                     String.class,       // Element type
 *                     "array",            // Array field name
 *                     i -> "Value " + i); // Computing function
 *
 *     @ReflectiveAccessOnly(arrayElements = true)
 *     private final String[] array = new String[16];
 *
 *     public String get(int index) {
 *         return ACCESSOR.getOrCompute(this, index, null);
 *     }
 * }
 *}
 * To store non-zero {@code int} values instead, replace the array field, accessor,
 * and {@code get} method in the context-free {@code ArrayCache} with these members.
 * The accessor represents primitive results as {@link Integer}:
 * {@snippet lang = java:
 * @ReflectiveAccessOnly(arrayElements = true)
 * private final int[] array = new int[16];
 *
 * private static final OfArrayField<ArrayCache, Void, Integer> ACCESSOR =
 *         StableAccessors.ofArrayField(
 *                 ArrayCache.class, // Field owner
 *                 Integer.class,    // Element type
 *                 "array",          // Field name
 *                 i -> i + 1);      // Computing function
 *
 * public int get(int index) {
 *     return ACCESSOR.getOrCompute(this, index, null);
 * }
 *}
 * To supply a prefix as a computation context, replace the accessor and {@code get}
 * method in the original {@code String[]} {@code ArrayCache} example with these members:
 * {@snippet lang = java:
 * private static final OfArrayField<ArrayCache, String, String>
 *         ACCESSOR = StableAccessors.ofArrayField(
 *                 ArrayCache.class, // Field owner
 *                 String.class,     // Element type
 *                 "array",          // Field name
 *                 String.class,     // Context type
 *                 (i, prefix) -> prefix + i); // Computing function
 *
 * public String get(int index, String prefix) {
 *     return ACCESSOR.getOrCompute(this, index, prefix);
 * }
 *}
 * The prefix is supplied at each call without a capturing computing function.
 * The first successfully installed value at an index is retained regardless of later
 * prefixes.
 *
 * <h3 id="static-indexed-caches">Static indexed caches</h3>
 * A static array accessor selects an array field shared by the class. Access supplies
 * the index, with null placeholders for the receiver and computation context:
 * {@snippet lang = java:
 * import java.lang.invoke.StableAccessors;
 * import java.lang.invoke.StableAccessors.OfArrayField;
 * import java.lang.invoke.ReflectiveAccessOnly;
 *
 * class StaticArrayCache {
 *
 *     @ReflectiveAccessOnly(arrayElements = true)
 *     private static final String[] VALUES = new String[16];
 *
 *     private static final OfArrayField<Void, Void, String> ACCESSOR =
 *             StableAccessors.ofStaticArrayField(
 *                     StaticArrayCache.class, // Field owner
 *                     String.class,           // Element type
 *                     "VALUES",               // Array field name
 *                     i -> "Value " + i);     // Computing function
 *
 *     public static String get(int index) {
 *         return ACCESSOR.getOrCompute(null, index, null);
 *     }
 * }
 *}
 * The context-taking overload of {@code ofStaticArrayField} also accepts a context type
 * token and a computing function taking the index and context. Its accessor takes
 * those arguments in {@code getOrCompute(null, index, context)}; the first successfully
 * installed value at an index is retained regardless of later contexts.
 *
 * <h3 id="serialized-caches">Serialized caches</h3>
 * A configuration file can be read on first access and its contents cached. A private
 * monitor prevents concurrent reads during initialization; an I/O failure propagates
 * as {@code UncheckedIOException} and can be retried by a waiting or later caller:
 * {@snippet lang = java:
 * import java.io.IOException;
 * import java.io.UncheckedIOException;
 * import java.lang.invoke.ReflectiveAccessOnly;
 * import java.lang.invoke.StableAccessors;
 * import java.lang.invoke.StableAccessors.OfField;
 * import java.nio.file.Files;
 * import java.nio.file.Path;
 * import java.util.Objects;
 *
 * class ConfigurationText {
 *
 *     private static final OfField<ConfigurationText, Path, String> CONTENTS =
 *             StableAccessors.ofField(
 *                     ConfigurationText.class,  // Field owner
 *                     String.class,             // Field type
 *                     "contents",               // Field name
 *                     Path.class,               // Context type
 *                     ConfigurationText::read); // Computing function
 *
 *     private final Path source;
 *     private final Object monitor = new Object();
 *
 *     @ReflectiveAccessOnly
 *     private String contents;
 *
 *     ConfigurationText(Path source) {
 *         this.source = Objects.requireNonNull(source);
 *     }
 *
 *     public String contents() {
 *         return CONTENTS.getOrComputeSerialized(monitor, this, source);
 *     }
 *
 *     private static String read(Path source) {
 *         try {
 *             return Files.readString(source);
 *         } catch (IOException e) {
 *             throw new UncheckedIOException(e);
 *         }
 *     }
 * }
 * }
 * Each instance owns its storage and monitor; the path is the computation context.
 * After a successful read, the contents remain cached even if the file changes,
 * and subsequent accesses can return without acquiring the monitor.
 * <p>
 * Finally, here is how a lazy supplier similar to the one provided via
 * {@linkplain Lazy#ofSupplier(Supplier)} can be implemented using stable accessors:
 *
 * {@snippet lang = java:
 * private static final class LazySupplier<V> implements Supplier<V> {
 *
 *     // Singleton sentinel for errors
 *     private static final Object FAILED = new Object();
 *
 *     private static final StableAccessors.OfField<
 *             LazySupplier<?>, LazySupplier<?>, Object> VALUE =
 *             StableAccessors.ofField(
 *                     LazySupplier.class,     // Owner type
 *                     Object.class,           // Field type
 *                     "value",                // Field name
 *                     LazySupplier.class,     // Context type
 *                     LazySupplier::compute); // Computing function
 *
 *     @ReflectiveAccessOnly
 *     private Object value;
 *
 *     // Volatile makes it thread safe
 *     private volatile Supplier<? extends V> computingFunction;
 *
 *     LazySupplier(Supplier<? extends V> computingFunction) {
 *         this.computingFunction = Objects.requireNonNull(computingFunction);
 *     }
 *
 *     @Override
 *     @SuppressWarnings("unchecked")
 *     public V get() {
 *         // This call supplies the monitor, receiver, and context which are all the same
 *         Object result = VALUE.getOrComputeSerialized(this, this, this);
 *
 *         if (result == FAILED) {
 *             throw new NoSuchElementException("Lazy initialization failed");
 *         }
 *         return (V) result;
 *     }
 *
 *     private Object compute() {
 *         Supplier<? extends V> function = computingFunction;
 *
 *         // Outside the catch: recursive access must not install a candidate.
 *         if (function == null) {
 *             throw new IllegalStateException("Recursive lazy initialization");
 *         }
 *
 *         // The local variable retains the function during its invocation.
 *         // Clearing this field also detects recursive computation.
 *         computingFunction = null;
 *
 *         try {
 *             return Objects.requireNonNull(function.get());
 *         } catch (Throwable ignored) {
 *             // Published like a successful result, before releasing the monitor.
 *             return FAILED;
 *         }
 *     }
 * }
 * }
 *
 * <h2 id="performance">Performance</h2>
 * A field managed by one or more stable accessors can never change after the field has
 * been initialized. Therefore, a JVM implementation may, for an initialized field,
 * elide all future reads of that field and instead use the content that has been
 * previously observed. We call this optimization <em>constant folding</em>. This is only
 * possible if there is a direct reference from a {@code static final} field to a managed
 * field or if there is a chain from a {@code static final} field -- via one or more
 * <em>trusted fields</em> (e.g., {@code static final} fields, {@linkplain Record record}
 * fields, {@link Lazy} elements, stable-accessor managed fields, or final instance fields
 * in hidden classes) -- to a managed field.
 * Whether and when this optimization occurs is implementation-dependent. Eliding
 * a storage read preserves the guarantees described under
 * {@linkplain ##memory-effects Memory effects}.
 * <p>
 * For array accessors, these optimizations apply independently to each initialized
 * element.
 *
 * @implNote The accessor factories' implementations install a field or element
 *           candidate as if using
 *           {@link VarHandle#compareAndExchangeRelease VarHandle.compareAndExchangeRelease( ... )}.
 *           The exchange's witness has plain-read effects, so a losing invocation
 *           performs a separate acquire read before returning the winner.
 *           Accessors should generally be declared {@code static final} in order to
 *           present the best performance optimization opportunities for the VM.
 *           The implementation of the accessors allows them to be used early in the
 *           VM boot sequence.
 *
 * @since 29
 *
 * @see Optional
 * @see Lazy#ofSupplier(Supplier)
 * @see Lazy#ofList(Lazy.Mode, int, IntFunction)
 * @see Lazy#ofSet(Lazy.Mode, Set, Predicate)
 * @see Lazy#ofMap(Lazy.Mode, Set, Function)
 * @jls 12.4 Initialization of Classes and Interfaces
 * @jls 17.4.5 Happens-before Order
 */
@PreviewFeature(feature = PreviewFeature.Feature.LAZY_CONSTANTS)
public final class StableAccessors {

    // Suppresses default constructor, ensuring non-instantiability.
    private StableAccessors() {}

    /**
     * {@return a stable accessor for a static field, without a computation context}
     * <p>
     * The field is selected by {@code owner} and {@code name}. It must be static,
     * non-final, and annotated with {@link ReflectiveAccessOnly} in field mode.
     * The {@linkplain ##field-selection field selection and access rules} and
     * {@linkplain ##type-compatibility type compatibility rules} apply.
     * The computing function takes no arguments.
     * <p>
     * The returned accessor requires a null receiver and
     * a null context argument to {@code getOrCompute}.
     * The {@linkplain ##access-shapes factory-specific argument restrictions}
     * apply on every accessor invocation.
     *
     * @param owner the type through which the field is selected
     * @param fieldType the expected field type
     * @param name the simple field name
     * @param computingFunction the function computing a non-default value
     * @param <R> the result type expected by the caller
     * @throws IllegalCallerException if there is no caller frame
     * @throws IllegalArgumentException if the field cannot be found or accessed, is
     *         incompatible, is not static or is final, lacks the required annotation,
     *         is null-restricted, or if {@code name} is invalid
     * @throws NullPointerException if any argument is null
     * @since 29
     */
    @CallerSensitive
    public static <R> OfField<Void, Void, R> ofStaticField(
            Class<?> owner,
            Class<? super R> fieldType,
            String name,
            Supplier<? extends R> computingFunction) {

        // Caller identification must occur in the caller-sensitive entry point.
        final Class<?> caller = Reflection.getCallerClass();
        checkCaller(caller);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(fieldType);
        checkName(name);
        Objects.requireNonNull(computingFunction);
        final Field field = findField(caller, owner, fieldType, name, true);
        checkFieldFinalState(field, false);
        checkFieldReflectiveAccessOnly(field);
        checkFieldNotNullRestricted(field);
        throw new UnsupportedOperationException();
    }

    /**
     * {@return a stable accessor for a static field, using a computation context}
     * <p>
     * Field selection, access checks, and type compatibility are as specified for
     * {@link #ofStaticField(Class, Class, String, Supplier)}.
     * The computing function receives the
     * {@linkplain ##computing-functions computation context} supplied to
     * {@code getOrCompute}; the context is not a
     * {@linkplain ##access-shapes storage coordinate}.
     * <p>
     * The returned accessor requires a null receiver. Its
     * context may be null if the computing function accepts it.
     * The {@linkplain ##access-shapes factory-specific argument restrictions}
     * apply on every accessor invocation.
     *
     * @param owner the type through which the field is selected
     * @param fieldType the expected field type
     * @param name the simple field name
     * @param contextType a reference type token for the context type or a supertype
     * @param computingFunction the function computing a non-default value
     * @param <C> the computation context type
     * @param <R> the result type expected by the caller
     * @throws IllegalCallerException if there is no caller frame
     * @throws IllegalArgumentException if the field cannot be found or accessed, is
     *         incompatible, is not static or is final, lacks the required annotation,
     *         is null-restricted, if {@code name} is invalid, or if {@code contextType}
     *         is a primitive type, including {@code void.class}
     * @throws NullPointerException if any argument is null
     * @since 29
     */
    @CallerSensitive
    public static <C, R> OfField<Void, C, R> ofStaticField(
            Class<?> owner,
            Class<? super R> fieldType,
            String name,
            Class<? super C> contextType,
            OfField.Computer<? super C, ? extends R> computingFunction) {

        final Class<?> caller = Reflection.getCallerClass();
        checkCaller(caller);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(fieldType);
        checkName(name);
        checkContextType(contextType);
        Objects.requireNonNull(computingFunction);
        final Field field = findField(caller, owner, fieldType, name, true);
        checkFieldFinalState(field, false);
        checkFieldReflectiveAccessOnly(field);
        checkFieldNotNullRestricted(field);
        throw new UnsupportedOperationException();
    }

    /**
     * {@return a stable accessor for an instance field, without a computation context}
     * <p>
     * The field is selected by {@code receiverType} and {@code name}. It must be
     * non-static, non-final, and annotated with {@link ReflectiveAccessOnly} in field
     * mode. The {@linkplain ##field-selection field selection and access rules} and
     * {@linkplain ##type-compatibility type compatibility rules} apply.
     * The computing function takes no arguments.
     * <p>
     * The returned accessor requires a non-null, compatible receiver and
     * a null context argument to {@code getOrCompute}.
     * The {@linkplain ##access-shapes factory-specific argument restrictions}
     * apply on every accessor invocation.
     *
     * @param receiverType the type through which the field is selected
     * @param fieldType the expected field type
     * @param name the simple field name
     * @param computingFunction the function computing a non-default value
     * @param <T> the receiver type
     * @param <R> the result type expected by the caller
     * @throws IllegalCallerException if there is no caller frame
     * @throws IllegalArgumentException if the field cannot be found or accessed, is
     *         incompatible, is static or final, lacks the required annotation,
     *         is null-restricted, or if {@code name} is invalid
     * @throws NullPointerException if any argument is null
     * @since 29
     */
    @CallerSensitive
    public static <T, R> OfField<T, Void, R> ofField(
            Class<? super T> receiverType,
            Class<?> fieldType,
            String name,
            Supplier<? extends R> computingFunction) {

        final Class<?> caller = Reflection.getCallerClass();
        checkCaller(caller);
        Objects.requireNonNull(receiverType);
        Objects.requireNonNull(fieldType);
        checkName(name);
        Objects.requireNonNull(computingFunction);
        final Field field = findField(caller, receiverType, fieldType, name, false);
        checkFieldFinalState(field, false);
        checkFieldReflectiveAccessOnly(field);
        checkFieldNotNullRestricted(field);
        final Class<?> protectedReceiver = Modifier.isProtected(field.getModifiers())
                && !VerifyAccess.isSamePackage(caller, field.getDeclaringClass())
                ? caller : null;
        throw new UnsupportedOperationException();
    }

    /**
     * {@return a stable accessor for an instance field, using a computation context}
     * <p>
     * Field selection, access checks, and type compatibility are as specified for
     * {@link #ofField(Class, Class, String, Supplier)}.
     * The computing function receives the
     * {@linkplain ##computing-functions computation context} supplied to
     * {@code getOrCompute}; the context is not a
     * {@linkplain ##access-shapes storage coordinate}.
     * <p>
     * The returned accessor requires a non-null, compatible receiver. Its
     * context may be null if the computing function accepts it.
     * The {@linkplain ##access-shapes factory-specific argument restrictions}
     * apply on every accessor invocation.
     *
     * @param receiverType the type through which the field is selected
     * @param fieldType the expected field type
     * @param name the simple field name
     * @param contextType a reference type token for the context type or a supertype
     * @param computingFunction the function computing a non-default value
     * @param <T> the receiver type
     * @param <C> the computation context type
     * @param <R> the result type expected by the caller
     * @throws IllegalCallerException if there is no caller frame
     * @throws IllegalArgumentException if the field cannot be found or accessed, is
     *         incompatible, is static or final, lacks the required annotation,
     *         is null-restricted, if {@code name} is invalid, or if {@code contextType}
     *         is a primitive type, including {@code void.class}
     * @throws NullPointerException if any argument is null
     * @since 29
     */
    @CallerSensitive
    public static <T, C, R> OfField<T, C, R> ofField(
            Class<? super T> receiverType,
            Class<?> fieldType,
            String name,
            Class<? super C> contextType,
            OfField.Computer<? super C, ? extends R> computingFunction) {

        final Class<?> caller = Reflection.getCallerClass();
        checkCaller(caller);
        Objects.requireNonNull(receiverType);
        Objects.requireNonNull(fieldType);
        checkName(name);
        checkContextType(contextType);
        Objects.requireNonNull(computingFunction);
        final Field field = findField(caller, receiverType, fieldType, name, false);
        checkFieldFinalState(field, false);
        checkFieldReflectiveAccessOnly(field);
        checkFieldNotNullRestricted(field);
        final Class<?> protectedReceiver = Modifier.isProtected(field.getModifiers())
                && !VerifyAccess.isSamePackage(caller, field.getDeclaringClass())
                ? caller : null;
        throw new UnsupportedOperationException();
    }

    /**
     * {@return a stable accessor for elements of a static array field, without
     * a computation context}
     * <p>
     * The field is selected by {@code owner} and {@code name}. It must be static
     * and final, have a supported one-dimensional reference or primitive array type,
     * and be annotated with {@code @ReflectiveAccessOnly(arrayElements = true)}.
     * The {@linkplain ##field-selection field selection and access rules} and
     * {@linkplain ##type-compatibility type compatibility rules} apply.
     * The computing function receives the element index.
     * <p>
     * Creating it does not initialize the field's declaring class or read the array
     * field. Invoking either access method initializes the declaring class if
     * necessary. See {@linkplain ##access-and-storage-ownership Access and storage
     * ownership} for the requirements on the array and its elements.
     * <p>
     * The returned accessor requires a null receiver and
     * a null context argument to {@code getOrCompute}.
     * The {@linkplain ##access-shapes factory-specific argument restrictions}
     * apply on every accessor invocation.
     *
     * @param owner the type through which the array field is selected
     * @param arrayComponentType the expected element result type
     * @param name the simple field name
     * @param computingFunction the function computing a non-default element value
     * @param <R> the result type, boxed if the array component type is primitive
     * @throws IllegalCallerException if there is no caller frame
     * @throws IllegalArgumentException if the field cannot be found or accessed,
     *         is not static or not final, does not have a supported array type or
     *         required annotation, or if {@code arrayComponentType} is inaccessible,
     *         unsupported or incompatible, or if {@code name} is invalid
     * @throws NullPointerException if any argument is null
     * @since 29
     */
    @CallerSensitive
    public static <R> OfArrayField<Void, Void, R> ofStaticArrayField(
            Class<?> owner,
            Class<? super R> arrayComponentType,
            String name,
            IntFunction<? extends R> computingFunction) {

        final Class<?> caller = Reflection.getCallerClass();
        checkCaller(caller);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(arrayComponentType);
        checkName(name);
        Objects.requireNonNull(computingFunction);
        final Field field = findField(caller, owner, Object.class, name, true);
        checkFieldFinalState(field, true);
        checkArrayTypes(caller, field.getType(), arrayComponentType);
        checkArrayFieldReflectiveAccessOnly(field);
        throw new UnsupportedOperationException();
    }

    /**
     * {@return a stable accessor for elements of a static array field, using a
     * computation context}
     * <p>
     * Field selection, access checks, type compatibility, and class initialization
     * are as specified for
     * {@link #ofStaticArrayField(Class, Class, String, IntFunction)}.
     * The computing function receives the element index and
     * {@linkplain ##computing-functions computation context} supplied to
     * {@code getOrCompute}; the context is not a
     * {@linkplain ##access-shapes storage coordinate}.
     * <p>
     * The returned accessor requires a null receiver. Its
     * context may be null if the computing function accepts it.
     * The {@linkplain ##access-shapes factory-specific argument restrictions}
     * apply on every accessor invocation.
     *
     * @param owner the type through which the array field is selected
     * @param arrayComponentType the expected element result type
     * @param name the simple field name
     * @param contextType a reference type token for the context type or a supertype
     * @param computingFunction the function computing a non-default element value
     * @param <C> the type of computation context
     * @param <R> the result type, boxed if the array component type is primitive
     * @throws IllegalCallerException if there is no caller frame
     * @throws IllegalArgumentException if the field cannot be found or accessed,
     *         is not static or not final, does not have a supported array type or
     *         required annotation, or if {@code arrayComponentType} is inaccessible,
     *         unsupported or incompatible, if {@code name} is invalid, or if
     *         {@code contextType} is a primitive type, including {@code void.class}
     * @throws NullPointerException if any argument is null
     * @since 29
     */
    @CallerSensitive
    public static <C, R> OfArrayField<Void, C, R> ofStaticArrayField(
            Class<?> owner,
            Class<? super R> arrayComponentType,
            String name,
            Class<? super C> contextType,
            OfArrayField.Computer<? super C, ? extends R> computingFunction) {

        final Class<?> caller = Reflection.getCallerClass();
        checkCaller(caller);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(arrayComponentType);
        checkName(name);
        checkContextType(contextType);
        Objects.requireNonNull(computingFunction);
        final Field field = findField(caller, owner, Object.class, name, true);
        checkFieldFinalState(field, true);
        checkArrayTypes(caller, field.getType(), arrayComponentType);
        checkArrayFieldReflectiveAccessOnly(field);
        throw new UnsupportedOperationException();
    }

    /**
     * {@return a stable accessor for elements of an instance array field, without
     * a computation context}
     * <p>
     * The field is selected by {@code receiverType} and {@code name}. It must be
     * non-static and final, have a supported one-dimensional array
     * type, and be annotated with {@code @ReflectiveAccessOnly(arrayElements = true)}.
     * The {@linkplain ##field-selection field selection and access
     * rules} and {@linkplain ##type-compatibility type compatibility rules} apply.
     * The computing function receives the element index.
     * <p>
     * The returned accessor requires a non-null, compatible receiver and
     * a null context argument to {@code getOrCompute}.
     * The {@linkplain ##access-shapes factory-specific argument restrictions}
     * apply on every accessor invocation.
     *
     * @param receiverType the type through which the array field is selected
     * @param arrayComponentType the expected element result type
     * @param name the simple field name
     * @param computingFunction the function computing a non-default element value
     * @param <T> the receiver type
     * @param <R> the result type, boxed if the array component type is primitive
     * @throws IllegalCallerException if there is no caller frame
     * @throws IllegalArgumentException if the field cannot be found or accessed, is
     *         static or not final, does not have a supported array type or required annotation,
     *         or if {@code arrayComponentType} is inaccessible, unsupported or
     *         incompatible, or if {@code name} is invalid
     * @throws NullPointerException if any argument is null
     * @since 29
     */
    @CallerSensitive
    public static <T, R> OfArrayField<T, Void, R> ofArrayField(
            Class<? super T> receiverType,
            Class<? super R> arrayComponentType,
            String name,
            IntFunction<? extends R> computingFunction) {

        final Class<?> caller = Reflection.getCallerClass();
        checkCaller(caller);
        Objects.requireNonNull(receiverType);
        Objects.requireNonNull(arrayComponentType);
        checkName(name);
        Objects.requireNonNull(computingFunction);
        final Field field = findField(caller, receiverType, Object.class, name, false);
        checkFieldFinalState(field, true);
        checkArrayTypes(caller, field.getType(), arrayComponentType);
        checkArrayFieldReflectiveAccessOnly(field);
        final Class<?> protectedReceiver = Modifier.isProtected(field.getModifiers())
                && !VerifyAccess.isSamePackage(caller, field.getDeclaringClass())
                ? caller : null;
        throw new UnsupportedOperationException();
    }

    /**
     * {@return a stable accessor for elements of an instance array field, using a
     * computation context}
     * <p>
     * Field selection, access checks, and type compatibility are as specified for
     * {@link #ofArrayField(Class, Class, String, IntFunction)}.
     * The computing function receives the element index and
     * {@linkplain ##computing-functions computation context} supplied to
     * {@code getOrCompute}; the context is not a
     * {@linkplain ##access-shapes storage coordinate}.
     * <p>
     * The returned accessor requires a non-null, compatible receiver. Its
     * context may be null if the computing function accepts it.
     * The {@linkplain ##access-shapes factory-specific argument restrictions}
     * apply on every accessor invocation.
     *
     * @param receiverType the type through which the array field is selected
     * @param arrayComponentType the expected element result type
     * @param name the simple field name
     * @param contextType a reference type token for the context type or a supertype
     * @param computingFunction the function computing a non-default element value
     * @param <T> the receiver type
     * @param <C> the computation context type
     * @param <R> the result type, boxed if the array component type is primitive
     * @throws IllegalCallerException if there is no caller frame
     * @throws IllegalArgumentException if the field cannot be found or accessed, is
     *         static or not final, does not have a supported array type or required annotation,
     *         or if {@code arrayComponentType} is inaccessible, unsupported or
     *         incompatible, if {@code name} is invalid, or if {@code contextType}
     *         is a primitive type, including {@code void.class}
     * @throws NullPointerException if any argument is null
     * @since 29
     */
    @CallerSensitive
    public static <T, C, R> OfArrayField<T, C, R> ofArrayField(
            Class<? super T> receiverType,
            Class<? super R> arrayComponentType,
            String name,
            Class<? super C> contextType,
            OfArrayField.Computer<? super C, ? extends R> computingFunction) {

        final Class<?> caller = Reflection.getCallerClass();
        checkCaller(caller);
        Objects.requireNonNull(receiverType);
        Objects.requireNonNull(arrayComponentType);
        checkName(name);
        checkContextType(contextType);
        Objects.requireNonNull(computingFunction);
        final Field field = findField(caller, receiverType, Object.class, name, false);
        checkFieldFinalState(field, true);
        checkArrayTypes(caller, field.getType(), arrayComponentType);
        checkArrayFieldReflectiveAccessOnly(field);
        final Class<?> protectedReceiver = Modifier.isProtected(field.getModifiers())
                && !VerifyAccess.isSamePackage(caller, field.getDeclaringClass())
                ? caller : null;
        throw new UnsupportedOperationException();
    }

    /**
     * A stable accessor for a static or instance field, with an optional computation
     * context. All normally returning calls to {@link #getOrCompute(Object, Object)}
     * selecting the same field location return the same installed value, regardless
     * of context. {@link #get(Object)} observes the field without computing.
     * <p>
     * The factory selecting the field determines the restrictions on the returned
     * implementation. An instance-field accessor requires a non-null receiver
     * compatible with the selected field and any protected access restriction.
     * A static-field accessor has receiver type {@link Void} and requires a null
     * receiver; a null receiver does not turn an instance-field accessor into a
     * static-field accessor. Access to a static field initializes its declaring
     * class if necessary; creating the accessor does not initialize that class.
     * <p>
     * An accessor created without a computation context has context type
     * {@code Void} and requires a null context. An accessor created with a context
     * passes that context to the computing function only when computation is needed;
     * a null context is permitted if the computing function accepts it.
     * The {@linkplain StableAccessors##access-shapes factory-specific argument
     * restrictions}, {@linkplain StableAccessors##publish-once publish-once},
     * {@linkplain StableAccessors##uninitialized-locations uninitialized-location}, and
     * {@linkplain StableAccessors##access-and-storage-ownership storage-ownership}
     * rules apply.
     *
     * @param <T> the receiver type, or {@code Void} for a static field
     * @param <C> the computation context type, or {@code Void} if no context is used
     * @param <R> the result type, boxed if the field is primitive
     * @since 29
     */
    public /* sealed */ interface OfField<T, C, R> {
        /**
         * {@return an optional containing the observed non-default value, boxed if
         * primitive, or an empty optional if the read observes the reserved default}
         * The field is read with acquire semantics without computing a value.
         * @param receiver the object holding the selected instance field, or
         *        {@code null} for a static field
         * @throws NullPointerException if the accessor selects an instance field
         *         and {@code receiver} is null
         * @throws ClassCastException if the receiver is incompatible with the
         *         selected instance field or violates a protected access restriction,
         *         or if the accessor selects a static field and the receiver is non-null
         */
        Optional<R> get(T receiver);

        /**
         * {@return the installed non-default value, boxed if primitive, computing
         * it if necessary}
         * For an accessor created with a context, the context is supplied to the
         * computing function only if computation is needed. It is not part of the
         * storage location.
         * @param receiver the object holding the selected instance field, or
         *        {@code null} for a static field
         * @param context the computation context, possibly {@code null}; must be
         *        {@code null} if the accessor was created without a context
         * @throws NullPointerException if the accessor selects an instance field
         *         and {@code receiver} is null, or if the field is primitive and
         *         the computed candidate is null
         * @throws ClassCastException if the receiver is incompatible with the
         *         selected instance field or violates a protected access restriction,
         *         if the accessor selects a static field and the receiver is non-null,
         *         if the accessor was created without a context and the context
         *         is non-null, if a non-null context is incompatible with the
         *         accessor's context type token, or if a non-null computed candidate
         *         cannot be converted to the field type as specified by the
         *         {@linkplain StableAccessors##type-compatibility type compatibility rules}
         * @throws IllegalArgumentException if the computed candidate is the reserved
         *         default value after conversion to the storage type
         */
        R getOrCompute(T receiver, C context);

        /**
         * {@return the installed non-default value, computing it under the supplied
         * monitor if necessary}
         * This method follows the {@linkplain StableAccessors##serialized-computation
         * serialized-computation protocol}. All invocations that can initialize the
         * same location must use the same monitor to prevent concurrent computations.
         * An observed installed value is returned without acquiring the monitor.
         * Argument restrictions, candidate checks, and computation failures are as
         * specified for {@link #getOrCompute(Object, Object)}.
         *
         * @implSpec This implementation first checks that {@code monitor} is a
         *           non-null identity object, then invokes {@link #get(Object)}.
         *           If a value is observed, it invokes {@code getOrCompute} without
         *           locking, preserving that method's argument checks. Otherwise,
         *           it invokes {@code getOrCompute} while synchronized on
         *           {@code monitor}, holding the monitor through publication or
         *           exceptional completion.
         * @param monitor the non-null identity object used to serialize computation
         * @param receiver the object holding the selected instance field, or
         *        {@code null} for a static field
         * @param context the computation context, possibly {@code null}; must be
         *        {@code null} if the accessor was created without a context
         * @throws NullPointerException if {@code monitor} is null, or if the accessor
         *         selects an instance field and {@code receiver} is null, or if the
         *         field is primitive and the computed candidate is null
         * @throws IdentityException if {@code monitor} is a value object
         * @throws ClassCastException if the receiver or context violates the
         *         restrictions specified by {@code getOrCompute}, or if a non-null
         *         computed candidate cannot be converted to the field type
         * @throws IllegalArgumentException if the computed candidate is the reserved
         *         default value after conversion to the storage type
         * @since 29
         */
        default R getOrComputeSerialized(Object monitor, T receiver, C context) {
            Objects.requireIdentity(monitor);
            Optional<R> observed = get(receiver);
            if (observed.isPresent()) {
                // Preserve context validation even when no computation is needed:
                //    contextType.cast(context);
                return observed.get();
            }

            synchronized (monitor) {
                // Rechecks the location before computing.
                // Any successful candidate is published before unlocking.
                return getOrCompute(receiver, context);
            }
        }

        /**
         * Computes a candidate field value from a context. It may be invoked more
         * than once for the same field location, including concurrently. The receiver
         * is not supplied automatically; it can be passed explicitly as the context.
         * @param <C> the type of computation context
         * @param <R> the type of candidate values
         * @since 29
         */
        @FunctionalInterface
        interface Computer<C, R> {
            /**
             * {@return a non-default candidate value}
             * @param context the computation context, possibly {@code null}
             */
            R compute(C context);
        }
    }

    /**
     * A stable accessor for elements of a static or instance array field, with an
     * optional computation context. The array referenced by the selected field and
     * the index identify the storage location. All normally returning calls to
     * {@link #getOrCompute(Object, int, Object)} selecting the same location return
     * the same installed value, regardless of context. Different fields or receivers
     * may share an array and thus share its installed element values.
     * {@link #get(Object, int)} observes the element without computing.
     * <p>
     * The factory selecting the array field determines the restrictions on the
     * returned implementation. An instance-array accessor requires a non-null
     * receiver compatible with the selected field and any protected access
     * restriction. A static-array accessor has receiver type {@link Void} and
     * requires a null receiver; a null receiver does not turn an instance-array
     * accessor into a static-array accessor. Access to a static array field initializes
     * its declaring class if necessary; creating the accessor does not initialize
     * that class.
     * <p>
     * An accessor created without a computation context has context type
     * {@code Void} and requires a null context. An accessor created with a context
     * passes that context to the computing function only when computation is needed;
     * a null context is permitted if the computing function accepts it.
     * The {@linkplain StableAccessors##access-shapes factory-specific argument
     * restrictions}, {@linkplain StableAccessors##publish-once publish-once},
     * {@linkplain StableAccessors##uninitialized-locations uninitialized-location}, and
     * {@linkplain StableAccessors##access-and-storage-ownership storage-ownership}
     * rules apply.
     *
     * @param <T> the receiver type, or {@code Void} for a static array field
     * @param <C> the computation context type, or {@code Void} if no context is used
     * @param <R> the result type, boxed if the array has a primitive component type
     * @since 29
     */
    public /* sealed */ interface OfArrayField<T, C, R> {
        /**
         * {@return an optional containing the observed non-default element value,
         * boxed if primitive, or an empty optional if the read observes the default}
         * The element is read with acquire semantics without computing a value.
         * @param receiver the object holding the selected instance array field, or
         *        {@code null} for a static array field
         * @param index the index of the selected element
         * @throws NullPointerException if the accessor selects an instance field
         *         and {@code receiver} is null, or if the selected array field is null
         * @throws IndexOutOfBoundsException if the index is outside the array
         * @throws IllegalArgumentException if the runtime array type is unsupported,
         *         including a {@linkplain StableAccessors##uninitialized-locations
         *         null-restricted array}
         * @throws ClassCastException if the receiver is incompatible with the
         *         selected instance field or violates a protected access restriction,
         *         or if the accessor selects a static field and the receiver is non-null
         */
        Optional<R> get(T receiver, int index);

        /**
         * {@return the installed non-default element value, boxed if primitive,
         * computing it if necessary}
         * The computing function receives the element index and, if the accessor
         * was created with a context, the context. It is invoked only if computation
         * is needed. The context is not part of the storage location.
         * @param receiver the object holding the selected instance array field, or
         *        {@code null} for a static array field
         * @param index the index of the selected element
         * @param context the computation context, possibly {@code null}; must be
         *        {@code null} if the accessor was created without a context
         * @throws NullPointerException if the accessor selects an instance field
         *         and {@code receiver} is null, if the selected array field is null,
         *         or if the array has a primitive component type and the computed
         *         candidate is null
         * @throws IndexOutOfBoundsException if the index is outside the array
         * @throws IllegalArgumentException if the runtime array type is unsupported,
         *         including a {@linkplain StableAccessors##uninitialized-locations
         *         null-restricted array}, or the computed candidate is the reserved
         *         default value after conversion to the storage type
         * @throws ClassCastException if the receiver is incompatible with the
         *         selected instance field or violates a protected access restriction,
         *         if the accessor selects a static field and the receiver is non-null,
         *         if the accessor was created without a context and the context
         *         is non-null, if a non-null context is incompatible with the
         *         accessor's context type token, or if a non-null computed candidate
         *         cannot be converted to the primitive component type as specified by the
         *         {@linkplain StableAccessors##type-compatibility type compatibility rules}
         * @throws ArrayStoreException if the computed candidate is incompatible
         *         with the runtime component type of a reference array
         */
        R getOrCompute(T receiver, int index, C context);

        /**
         * {@return the installed non-default element value, computing it under the
         * supplied monitor if necessary}
         * This method follows the {@linkplain StableAccessors##serialized-computation
         * serialized-computation protocol}. All invocations that can initialize the
         * same array element must use the same monitor, including accesses through
         * aliases of the array. Sharing a monitor across indices also serializes
         * their computations. An observed installed value is returned without
         * acquiring the monitor. Argument restrictions, candidate checks, and
         * computation failures are as specified for
         * {@link #getOrCompute(Object, int, Object)}.
         *
         * @implSpec This implementation first checks that {@code monitor} is a
         *           non-null identity object, then invokes {@link #get(Object, int)}.
         *           If a value is observed, it invokes {@code getOrCompute} without
         *           locking, preserving that method's argument checks. Otherwise,
         *           it invokes {@code getOrCompute} while synchronized on
         *           {@code monitor}, holding the monitor through publication or
         *           exceptional completion.
         * @param monitor the non-null identity object used to serialize computation
         * @param receiver the object holding the selected instance array field, or
         *        {@code null} for a static array field
         * @param index the index of the selected element
         * @param context the computation context, possibly {@code null}; must be
         *        {@code null} if the accessor was created without a context
         * @throws NullPointerException if {@code monitor} is null, if the accessor
         *         selects an instance field and {@code receiver} is null, if the
         *         selected array field is null, or if the array has a primitive
         *         component type and the computed candidate is null
         * @throws IdentityException if {@code monitor} is a value object
         * @throws IndexOutOfBoundsException if the index is outside the array
         * @throws IllegalArgumentException if the runtime array type is unsupported,
         *         including a {@linkplain StableAccessors##uninitialized-locations
         *         null-restricted array}, or the computed candidate is the reserved
         *         default value after conversion to the storage type
         * @throws ClassCastException if the receiver or context violates the
         *         restrictions specified by {@code getOrCompute}, or if a non-null
         *         computed candidate cannot be converted to the primitive component type
         * @throws ArrayStoreException if the computed candidate is incompatible
         *         with the runtime component type of a reference array
         * @since 29
         */
        default R getOrComputeSerialized(Object monitor, T receiver, int index, C context) {
            Objects.requireIdentity(monitor);
            Optional<R> observed = get(receiver, index);
            if (observed.isPresent()) {
                // Preserve context validation even when no computation is needed:
                //    contextType.cast(context);
                return observed.get();
            }

            synchronized (monitor) {
                // Rechecks the location before computing.
                // Any successful candidate is published before unlocking.
                return getOrCompute(receiver, index, context);
            }
        }

        /**
         * Computes an array element from its index and context. It may be invoked
         * more than once for the same element, including concurrently. Neither the
         * receiver nor the array itself is supplied automatically.
         * @param <C> the type of computation context
         * @param <R> the type of candidate values
         * @since 29
         */
        @FunctionalInterface
        interface Computer<C, R> {
            /**
             * {@return a non-default candidate value}
             * @param index the index of the selected element
             * @param context the computation context, possibly {@code null}
             */
            R compute(int index, C context);
        }
    }
}
