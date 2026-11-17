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
 * The factories do not read that storage or invoke the computing function when an
 * accessor is created.
 * <p>
 * For a ready-made supplier that caches one reference, use
 * {@link Lazy#ofSupplier(Supplier) Lazy.ofSupplier}. The
 * {@link Lazy#ofList(Lazy.Mode, int, IntFunction)},
 * {@link Lazy#ofSet(Lazy.Mode, Set, Predicate)}, and
 * {@link Lazy#ofMap(Lazy.Mode, Set, Function)} factories cache list elements,
 * membership decisions, and map values, respectively. These factories own their storage and offer
 * {@link Lazy.Mode#ONCE at-most-once} and {@link Lazy.Mode#PUBLISH publish-once} modes.
 * The accessors in this class operate on caller-owned storage: computations may race,
 * and failed computations may be retried.
 *
 * <h2 id="access-shapes">Locations and access shapes</h2>
 * Each factory selects a named field. The accessor's {@code get} and
 * {@code getOrCompute} methods take the coordinates shown below. The computing
 * function receives different arguments:
 * <table class="striped">
 * <caption>Access coordinates and computing arguments without a context</caption>
 * <thead>
 * <tr><th scope="col">Storage</th><th scope="col">Access coordinates</th>
 *     <th scope="col">Computing arguments</th></tr>
 * </thead>
 * <tbody>
 * <tr><th scope="row">Static field</th><td>None</td><td>None</td></tr>
 * <tr><th scope="row">Instance field</th><td>Receiver</td><td>None</td></tr>
 * <tr><th scope="row">Element of an instance array field</th>
 *     <td>Receiver, index</td><td>Index</td></tr>
 * </tbody>
 * </table>
 * Each factory has a context-free variant and a variant with a computation context;
 * the two variants return different accessor interfaces. The latter adds a context
 * argument to {@code getOrCompute} and to the computing function. The context may be
 * unrelated to the storage owner and is not part of the storage coordinates:
 * invocations selecting the same location share the installed value even if they
 * supply different contexts.
 * <p>
 * Field accessors support reference and primitive fields. Array accessors select an
 * instance field containing a one-dimensional reference or primitive array. The
 * underlying array's identity and the index identify the cached location; different
 * receivers can share an array and therefore share its cached elements. Values of
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
 * An array factory instead selects a non-static array field annotated with
 * {@code @ReflectiveAccessOnly(arrayElements = true)}; that field may be final.
 * The annotation declares intent but does not establish exclusive access; the
 * {@linkplain ##access-and-storage-ownership storage-ownership requirements} still apply.
 * Accessing a static field through an accessor initializes its declaring class if necessary.
 *
 * <h2 id="computing-functions">Computing functions</h2>
 * An accessor retains the computing function supplied at creation and invokes it on
 * the thread calling {@code getOrCompute}. A context-aware accessor can use dynamic
 * inputs without requiring a new function at each invocation. A context may be
 * {@code null} if the computing function accepts it.
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
 * stored values, boxed if primitive, can be used as {@code V}; erased type arguments
 * remain the caller's responsibility.
 * <p>
 * A computed candidate is checked against the storage type before installation.
 * For reference storage, the candidate must be assignable to the field type or the
 * runtime array component type. An incompatible reference-array candidate throws
 * {@link ArrayStoreException}, including when the runtime component type is narrower
 * than the declared one. For primitive storage, the candidate must be a wrapper
 * value that can be unboxed and converted to the storage type by an identity
 * conversion or a widening primitive conversion (section {@jls 5.1.2}).
 * Narrowing conversions and conversions between {@code boolean} and numeric types
 * are not supported, and {@code null} cannot be converted to a primitive value.
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
 * <h2 id="memory-effects">Memory effects</h2>
 * The {@code get} and {@code getOrCompute} methods read the selected location with
 * acquire semantics. A successful installation publishes the candidate with release
 * effects. An invocation that loses an installation race observes the installed value
 * with acquire effects before returning it. These effects are independent of whether
 * a field used directly as storage is declared {@code volatile}.
 * <p>
 * For each lazily initialized location, actions in the publishing thread preceding
 * publication of the installed value
 * {@linkplain java.util.concurrent##MemoryVisibility happen-before} actions
 * following an invocation in another thread that observes that value.
 * These guarantees do not impose a total order on accesses to different locations.
 * Subsequent accesses provide no additional memory ordering or visibility guarantees
 * for actions performed after publication. Publishing a reference does not make
 * later mutations of the referenced object thread-safe.
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
 * publish-once and stability guarantees. Multiple stable accessors may share a
 * storage location.
 * <p>
 * An accessor does not retain a receiver passed to {@code get} or
 * {@code getOrCompute}, though it may retain objects captured by its computing function.
 * <p>
 * For an array accessor, the selected field must contain a non-null array and must
 * not be replaced while its elements are managed by an accessor. The array reference
 * itself may be read normally; all aliases of the array remain subject to the
 * element-access restrictions above.
 *
 * <h2 id="encapsulating-stable-accessors">Encapsulating stable accessors</h2>
 * A class can encapsulate stable accessors to cache values in its own fields and arrays.
 *
 * <h3 id="scalar-caches">Scalar caches</h3>
 * The following small class caches a String in a reserved field. The accessor is
 * shared across all instances, while the computation uses configuration supplied
 * at the invocation:
 * {@snippet lang = java:
 * import java.lang.invoke.StableAccessors;
 * import java.lang.invoke.StableAccessors.StableFieldWithContext;
 * import java.lang.invoke.ReflectiveAccessOnly;
 *
 * record Configuration(String prefix, int number) {}
 *
 * class StringCache {
 *
 *      private static final StableFieldWithContext<StringCache, Configuration, String> ACCESSOR =
 *             StableAccessors.ofField(
 *                     StringCache.class,       // Field owner
 *                     String.class,            // Field type
 *                     "value",                 // Field name
 *                     Configuration.class,     // Extra context
 *                     config -> config.prefix() + config.number()); // Computing function
 *
 *     @ReflectiveAccessOnly
 *     private String value;
 *
 *     public String get(Configuration config) {
 *         return ACCESSOR.getOrCompute(this, config);
 *     }
 * }
 *}
 * The first successful installation determines the value for this {@code StringCache}
 * instance; supplying a different configuration does not create a separate cached value.
 * <p>
 * Replacing the field, accessor, and {@code get} method in {@code StringCache} with
 * the following members gives a context-free <em>static</em> cache:
 * {@snippet lang = java:
 * @ReflectiveAccessOnly
 * private static String value;
 *
 * private static final StableAccessors.StableStaticField<String> ACCESSOR =
 *         StableAccessors.ofStaticField(
 *                 StringCache.class,   // Field owner
 *                 String.class,        // Field type
 *                 "value",             // Field name
 *                 () -> "Some value"); // Computing function
 *
 * public static String get() {
 *     return ACCESSOR.getOrCompute();
 * }
 *}
 *
 * <h3 id="indexed-caches">Indexed caches</h3>
 * An array accessor caches a separate value at each index:
 * {@snippet lang = java:
 * import java.lang.invoke.StableAccessors;
 * import java.lang.invoke.StableAccessors.StableArray;
 * import java.lang.invoke.ReflectiveAccessOnly;
 *
 * class ArrayCache {
 *
 *     private static final StableArray<ArrayCache, String> ACCESSOR =
 *             StableAccessors.ofArray(
 *                     ArrayCache.class,   // Field owner
 *                     String.class,       // Element type
 *                     "array",            // Array field name
 *                     i -> "Value " + i); // Computing function
 *
 *     @ReflectiveAccessOnly(arrayElements = true)
 *     private final String[] array = new String[16];
 *
 *     public String get(int index) {
 *         return ACCESSOR.getOrCompute(this, index);
 *     }
 * }
 *}
 * To supply a prefix as a computation context, replace the accessor and {@code get}
 * method above with these members:
 * {@snippet lang = java:
 * private static final StableAccessors.StableArrayWithContext<ArrayCache, String, String>
 *   ACCESSOR = StableAccessors.ofArray(
 *                  ArrayCache.class, // Field owner
 *                  String.class,     // Element type
 *                  "array",          // Field name
 *                  String.class,     // Context type
 *                  (i, prefix) -> prefix + i); // Computing function
 *
 * public String get(int index, String prefix) {
 *     return ACCESSOR.getOrCompute(this, index, prefix);
 * }
 *}
 * The prefix is supplied at each call without a capturing computing function.
 * The first successfully installed value at an index is retained regardless of later prefixes.
 * <p>
 * To store non-zero {@code int} values instead, replace the array field, accessor,
 * and {@code get} method in the context-free {@code ArrayCache} with these members.
 * The accessor represents primitive results as {@link Integer}:
 * {@snippet lang = java:
 * @ReflectiveAccessOnly(arrayElements = true)
 * private final int[] array = new int[16];
 *
 * private static final StableAccessors.StableArray<ArrayCache, Integer> ACCESSOR =
 *         StableAccessors.ofArray(
 *                 ArrayCache.class, // Field owner
 *                 Integer.class,    // Element type
 *                 "array",          // Field name
 *                 i -> i + 1);      // Computing function
 *
 * public int get(int index) {
 *     return ACCESSOR.getOrCompute(this, index);
 * }
 *}
 *
 * <h2 id="performance">Performance</h2>
 * A field managed by one or more stable accessors can never change after the field has
 * been initialized. Therefore, a JVM implementation may, for an initialized field,
 * elide all future reads of that field and instead use the content that has been
 * previously observed. We call this optimization <em>constant folding</em>. This is only
 * possible if there is a direct reference from a {@code static final} field to a managed
 * field or if there is a chain from a {@code static final} field -- via one or more
 * <em>trusted fields</em> (i.e., {@code static final} fields, {@linkplain Record record}
 * fields, or final instance fields in hidden classes) -- to a lazily initialized field.
 * Whether and when this optimization occurs is implementation-dependent. Eliding
 * a storage read preserves the guarantees described under
 * {@linkplain ##memory-effects Memory effects}.
 * <p>
 * For array accessors, these optimizations apply independently to each
 * initialized element. Initializing one element does not initialize the other elements.
 *
 * @implNote The accessor factories' implementations install a field or element
 *           candidate as if using
 *           {@link VarHandle#compareAndExchangeRelease VarHandle.compareAndExchangeRelease( ... )}.
 *           The exchange's witness has plain-read effects, so a losing invocation
 *           performs a separate acquire read before returning the winner.
 *           Accessors should generally be declared {@code static final} in order to
 *           present the best performance optimization opportunities for the VM.
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
     * The accessor uses {@linkplain ##publish-once publish once}.
     *
     * @param owner the type through which the field is selected
     * @param fieldType the expected field type
     * @param name the simple field name
     * @param computingFunction the function computing a non-default value
     * @param <V> the result type expected by the caller
     * @throws IllegalCallerException if there is no caller frame
     * @throws IllegalArgumentException if the field cannot be found or accessed, is
     *         incompatible, is not static or is final, lacks the required annotation,
     *         is null-restricted, or if {@code name} is invalid
     * @throws NullPointerException if any argument is null
     * @since 29
     */
    @CallerSensitive
    public static <V> StableStaticField<V> ofStaticField(
            Class<?> owner,
            Class<? super V> fieldType,
            String name,
            StableStaticField.Computer<? extends V> computingFunction) {

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
     * {@link #ofStaticField(Class, Class, String, StableStaticField.Computer)}.
     * The computing function receives the
     * {@linkplain ##computing-functions computation context} supplied to
     * {@code getOrCompute}; the context is not a
     * {@linkplain ##access-shapes storage coordinate}.
     *
     * @param owner the type through which the field is selected
     * @param fieldType the expected field type
     * @param name the simple field name
     * @param contextType the type through which the computation context is described
     * @param computingFunction the function computing a non-default value
     * @param <C> the computation context type
     * @param <V> the result type expected by the caller
     * @throws IllegalCallerException if there is no caller frame
     * @throws IllegalArgumentException if the field cannot be found or accessed, is
     *         incompatible, is not static or is final, lacks the required annotation,
     *         is null-restricted, or if {@code name} is invalid
     * @throws NullPointerException if any argument is null
     * @since 29
     */
    @CallerSensitive
    public static <C, V> StableStaticFieldWithContext<C, V> ofStaticField(
            Class<?> owner,
            Class<? super V> fieldType,
            String name,
            Class<? super C> contextType,
            StableStaticFieldWithContext.Computer<? super C, ? extends V> computingFunction) {

        final Class<?> caller = Reflection.getCallerClass();
        checkCaller(caller);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(fieldType);
        checkName(name);
        Objects.requireNonNull(contextType);
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
     * The accessor uses {@linkplain ##publish-once publish once}.
     *
     * @param receiverType the type through which the field is selected
     * @param fieldType the expected field type
     * @param name the simple field name
     * @param computingFunction the function computing a non-default value
     * @param <T> the receiver type
     * @param <V> the result type expected by the caller
     * @throws IllegalCallerException if there is no caller frame
     * @throws IllegalArgumentException if the field cannot be found or accessed, is
     *         incompatible, is static or final, lacks the required annotation,
     *         is null-restricted, or if {@code name} is invalid
     * @throws NullPointerException if any argument is null
     * @since 29
     */
    @CallerSensitive
    public static <T, V> StableField<T, V> ofField(
            Class<? super T> receiverType,
            Class<?> fieldType,
            String name,
            StableField.Computer<? extends V> computingFunction) {

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
     * {@link #ofField(Class, Class, String, StableField.Computer)}.
     * The computing function receives the
     * {@linkplain ##computing-functions computation context} supplied to
     * {@code getOrCompute}; the context is not a
     * {@linkplain ##access-shapes storage coordinate}.
     *
     * @param receiverType the type through which the field is selected
     * @param fieldType the expected field type
     * @param name the simple field name
     * @param contextType the type through which the computation context is described
     * @param computingFunction the function computing a non-default value
     * @param <T> the receiver type
     * @param <C> the computation context type
     * @param <V> the result type expected by the caller
     * @throws IllegalCallerException if there is no caller frame
     * @throws IllegalArgumentException if the field cannot be found or accessed, is
     *         incompatible, is static or final, lacks the required annotation,
     *         is null-restricted, or if {@code name} is invalid
     * @throws NullPointerException if any argument is null
     * @since 29
     */
    @CallerSensitive
    public static <T, C, V> StableFieldWithContext<T, C, V> ofField(
            Class<? super T> receiverType,
            Class<?> fieldType,
            String name,
            Class<? super C> contextType,
            StableFieldWithContext.Computer<? super C, ? extends V> computingFunction) {

        final Class<?> caller = Reflection.getCallerClass();
        checkCaller(caller);
        Objects.requireNonNull(receiverType);
        Objects.requireNonNull(fieldType);
        checkName(name);
        Objects.requireNonNull(contextType);
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
     * {@return a stable accessor for elements of an instance array field, without
     * a computation context}
     * <p>
     * The field is selected by {@code receiverType} and {@code name}. It must be
     * non-static, have a supported one-dimensional array
     * type, and be annotated with {@code @ReflectiveAccessOnly(arrayElements = true)}.
     * It may be final. The {@linkplain ##field-selection field selection and access
     * rules} and {@linkplain ##type-compatibility type compatibility rules} apply.
     * The computing function receives the element index.
     * <p>
     * The accessor uses {@linkplain ##publish-once publish once}. The field must
     * continue to refer to the same array while its elements are managed. See
     * {@linkplain ##access-and-storage-ownership Access and storage ownership}.
     *
     * @param receiverType the type through which the array field is selected
     * @param arrayComponentType the expected element result type
     * @param name the simple field name
     * @param computingFunction the function computing a non-default element value
     * @param <T> the receiver type
     * @param <V> the result type, boxed if the array component type is primitive
     * @throws IllegalCallerException if there is no caller frame
     * @throws IllegalArgumentException if the field cannot be found or accessed, is
     *         static, does not have a supported array type or required annotation,
     *         or if {@code arrayComponentType} is inaccessible, unsupported or
     *         incompatible, or if {@code name} is invalid
     * @throws NullPointerException if any argument is null
     * @since 29
     */
    @CallerSensitive
    public static <T, V> StableArray<T, V> ofArray(
            Class<? super T> receiverType,
            Class<? super V> arrayComponentType,
            String name,
            StableArray.Computer<? extends V> computingFunction) {

        final Class<?> caller = Reflection.getCallerClass();
        checkCaller(caller);
        Objects.requireNonNull(receiverType);
        Objects.requireNonNull(arrayComponentType);
        checkName(name);
        Objects.requireNonNull(computingFunction);
        final Field field = findField(caller, receiverType, Object.class, name, false);
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
     * {@link #ofArray(Class, Class, String, StableArray.Computer)}.
     * The computing function receives the element index and
     * {@linkplain ##computing-functions computation context} supplied to
     * {@code getOrCompute}; the context is not a
     * {@linkplain ##access-shapes storage coordinate}.
     *
     * @param receiverType the type through which the array field is selected
     * @param arrayComponentType the expected element result type
     * @param name the simple field name
     * @param contextType the type through which the computation context is described
     * @param computingFunction the function computing a non-default element value
     * @param <T> the receiver type
     * @param <C> the computation context type
     * @param <V> the result type, boxed if the array component type is primitive
     * @throws IllegalCallerException if there is no caller frame
     * @throws IllegalArgumentException if the field cannot be found or accessed, is
     *         static, does not have a supported array type or required annotation,
     *         or if {@code arrayComponentType} is inaccessible, unsupported or
     *         incompatible, or if {@code name} is invalid
     * @throws NullPointerException if any argument is null
     * @since 29
     */
    @CallerSensitive
    public static <T, C, V> StableArrayWithContext<T, C, V> ofArray(
            Class<? super T> receiverType,
            Class<? super V> arrayComponentType,
            String name,
            Class<? super C> contextType,
            StableArrayWithContext.Computer<? super C, ? extends V> computingFunction) {

        final Class<?> caller = Reflection.getCallerClass();
        checkCaller(caller);
        Objects.requireNonNull(receiverType);
        Objects.requireNonNull(arrayComponentType);
        checkName(name);
        Objects.requireNonNull(contextType);
        Objects.requireNonNull(computingFunction);
        final Field field = findField(caller, receiverType, Object.class, name, false);
        checkArrayTypes(caller, field.getType(), arrayComponentType);
        checkArrayFieldReflectiveAccessOnly(field);
        final Class<?> protectedReceiver = Modifier.isProtected(field.getModifiers())
                && !VerifyAccess.isSamePackage(caller, field.getDeclaringClass())
                ? caller : null;
        throw new UnsupportedOperationException();
    }

    /**
     * A stable accessor for a static field, without a computation context.
     * All normally returning calls to {@link #getOrCompute()} return the same
     * installed value. {@link #get()} observes the field without computing.
     * The {@linkplain StableAccessors##publish-once publish-once},
     * {@linkplain StableAccessors##uninitialized-locations uninitialized-location}, and
     * {@linkplain StableAccessors##access-and-storage-ownership storage-ownership}
     * rules apply.
     *
     * @param <V> the result type, boxed if the field is primitive
     * @since 29
     */
    public interface StableStaticField<V> {
        /**
         * {@return an optional containing the observed non-default value, or an
         * empty optional if the read observes the reserved default value}
         * The field is read with acquire semantics without computing a value.
         */
        Optional<V> get();

        /**
         * {@return the installed non-default value, computing it if necessary}
         * @throws IllegalArgumentException if the computed candidate is the reserved
         *         default value after conversion to the storage type
         */
        V getOrCompute();

        /**
         * Computes a candidate for a static field without a context. It may be
         * invoked more than once, including concurrently.
         * @param <V> the type of candidate values
         * @since 29
         */
        @FunctionalInterface
        interface Computer<V> {
            /** {@return a non-default candidate value} */
            V compute();
        }
    }

    /**
     * A stable accessor for a static field, with a computation context.
     * All normally returning calls to {@link #getOrCompute(Object)} return the
     * same installed value, regardless of context. {@link #get()} observes the
     * field without computing.
     * The {@linkplain StableAccessors##publish-once publish-once},
     * {@linkplain StableAccessors##uninitialized-locations uninitialized-location}, and
     * {@linkplain StableAccessors##access-and-storage-ownership storage-ownership}
     * rules apply.
     *
     * @param <C> the type of computation context
     * @param <V> the result type, boxed if the field is primitive
     * @since 29
     */
    public interface StableStaticFieldWithContext<C, V> {
        /**
         * {@return an optional containing the observed non-default value, or an
         * empty optional if the read observes the reserved default value}
         * The field is read with acquire semantics without computing a value.
         */
        Optional<V> get();

        /**
         * {@return the installed non-default value, computing it if necessary}
         * The context is supplied to the computing function only if computation
         * is needed; it is not part of the storage location.
         * @param context the computation context, possibly {@code null}
         * @throws IllegalArgumentException if the computed candidate is the reserved
         *         default value after conversion to the storage type
         */
        V getOrCompute(C context);

        /**
         * Computes a candidate for a static field from a context. It may be
         * invoked more than once, including concurrently.
         * @param <C> the type of computation context
         * @param <V> the type of candidate values
         * @since 29
         */
        @FunctionalInterface
        interface Computer<C, V> {
            /**
             * {@return a non-default candidate value}
             * @param context the computation context, possibly {@code null}
             */
            V compute(C context);
        }
    }

    /**
     * A stable accessor for an instance field, without a computation context.
     * All normally returning calls to {@link #getOrCompute(Object)} for a given
     * receiver return the same installed value. {@link #get(Object)} observes
     * the field without computing.
     * The {@linkplain StableAccessors##publish-once publish-once},
     * {@linkplain StableAccessors##uninitialized-locations uninitialized-location}, and
     * {@linkplain StableAccessors##access-and-storage-ownership storage-ownership}
     * rules apply.
     *
     * @param <T> the type of the receiver
     * @param <V> the result type, boxed if the field is primitive
     * @since 29
     */
    public interface StableField<T, V> {
        /**
         * {@return an optional containing the observed non-default value, or an
         * empty optional if the read observes the reserved default value}
         * The field is read with acquire semantics without computing a value.
         * @param receiver the object holding the selected field
         * @throws NullPointerException if {@code receiver} is null
         * @throws ClassCastException if the receiver is incompatible with the
         *         selected field or violates a protected access restriction
         */
        Optional<V> get(T receiver);

        /**
         * {@return the installed non-default value, computing it if necessary}
         * @param receiver the object holding the selected field
         * @throws NullPointerException if {@code receiver} is null
         * @throws ClassCastException if the receiver is incompatible with the
         *         selected field or violates a protected access restriction
         * @throws IllegalArgumentException if the computed candidate is the reserved
         *         default value after conversion to the storage type
         */
        V getOrCompute(T receiver);

        /**
         * Computes a candidate for an instance field without a context. It may be
         * invoked more than once, including concurrently.
         * @param <V> the type of candidate values
         * @since 29
         */
        @FunctionalInterface
        interface Computer<V> {
            /** {@return a non-default candidate value} */
            V compute();
        }
    }

    /**
     * A stable accessor for an instance field, with a computation context.
     * All normally returning calls to {@link #getOrCompute(Object, Object)} for
     * a given receiver return the same installed value, regardless of context.
     * {@link #get(Object)} observes the field without computing.
     * The {@linkplain StableAccessors##publish-once publish-once},
     * {@linkplain StableAccessors##uninitialized-locations uninitialized-location}, and
     * {@linkplain StableAccessors##access-and-storage-ownership storage-ownership}
     * rules apply.
     *
     * @param <T> the type of the receiver
     * @param <C> the type of computation context
     * @param <V> the result type, boxed if the field is primitive
     * @since 29
     */
    public interface StableFieldWithContext<T, C, V> {
        /**
         * {@return an optional containing the observed non-default value, or an
         * empty optional if the read observes the reserved default value}
         * The field is read with acquire semantics without computing a value.
         * @param receiver the object holding the selected field
         * @throws NullPointerException if {@code receiver} is null
         * @throws ClassCastException if the receiver is incompatible with the
         *         selected field or violates a protected access restriction
         */
        Optional<V> get(T receiver);

        /**
         * {@return the installed non-default value, computing it if necessary}
         * The context is supplied to the computing function only if computation
         * is needed; it is not part of the storage location.
         * @param receiver the object holding the selected field
         * @param context the computation context, possibly {@code null}
         * @throws NullPointerException if {@code receiver} is null
         * @throws ClassCastException if the receiver is incompatible with the
         *         selected field or violates a protected access restriction
         * @throws IllegalArgumentException if the computed candidate is the reserved
         *         default value after conversion to the storage type
         */
        V getOrCompute(T receiver, C context);

        /**
         * Computes a candidate for an instance field from a context. It may be
         * invoked more than once for the same field, including concurrently.
         * @param <C> the type of computation context
         * @param <V> the type of candidate values
         * @since 29
         */
        @FunctionalInterface
        interface Computer<C, V> {
            /**
             * {@return a non-default candidate value}
             * @param context the computation context, possibly {@code null}
             */
            V compute(C context);
        }
    }

    /**
     * A stable accessor for elements of an instance array field, without a
     * computation context. The array referenced by the selected field and the
     * index identify the storage location. Different receivers may share an array
     * and thus share its installed element values.
     * The {@linkplain StableAccessors##publish-once publish-once},
     * {@linkplain StableAccessors##uninitialized-locations uninitialized-location}, and
     * {@linkplain StableAccessors##access-and-storage-ownership storage-ownership}
     * rules apply.
     *
     * @param <T> the type of the receiver
     * @param <V> the result type, boxed if the array has a primitive component type
     * @since 29
     */
    public interface StableArray<T, V> {
        /**
         * {@return an optional containing the observed non-default element value,
         * boxed if primitive, or an empty optional if the read observes the default}
         * The element is read with acquire semantics without computing a value.
         * @param receiver the object holding the array field
         * @param index the index of the selected element
         * @throws NullPointerException if {@code receiver} or its array field is null
         * @throws IndexOutOfBoundsException if the index is outside the array
         * @throws IllegalArgumentException if the runtime array type is unsupported
         * @throws ClassCastException if the receiver is incompatible with the
         *         selected field or violates a protected access restriction
         */
        Optional<V> get(T receiver, int index);

        /**
         * {@return the installed non-default element value, boxed if primitive,
         * computing it if necessary}
         * @param receiver the object holding the array field
         * @param index the index of the selected element
         * @throws NullPointerException if {@code receiver} or its array field is null
         * @throws IndexOutOfBoundsException if the index is outside the array
         * @throws IllegalArgumentException if the runtime array type is unsupported,
         *         or the computed candidate is the reserved default value after
         *         conversion to the storage type
         * @throws ClassCastException if the receiver is incompatible with the
         *         selected field or violates a protected access restriction
         * @throws ArrayStoreException if the computed candidate is incompatible
         *         with the runtime component type of a reference array
         */
        V getOrCompute(T receiver, int index);

        /**
         * Computes an array element from its index. It may be invoked more than
         * once for the same element, including concurrently.
         * @param <V> the type of candidate values
         * @since 29
         */
        @FunctionalInterface
        interface Computer<V> {
            /**
             * {@return a non-default candidate value}
             * @param index the index of the selected element
             */
            V compute(int index);
        }
    }

    /**
     * A stable accessor for elements of an instance array field, with a
     * computation context. The array referenced by the selected field and the
     * index identify the storage location. Different receivers may share an array
     * and thus share its installed element values, regardless of context.
     * The {@linkplain StableAccessors##publish-once publish-once},
     * {@linkplain StableAccessors##uninitialized-locations uninitialized-location}, and
     * {@linkplain StableAccessors##access-and-storage-ownership storage-ownership}
     * rules apply.
     *
     * @param <T> the type of the receiver
     * @param <C> the type of computation context
     * @param <V> the result type, boxed if the array has a primitive component type
     * @since 29
     */
    public interface StableArrayWithContext<T, C, V> {
        /**
         * {@return an optional containing the observed non-default element value,
         * boxed if primitive, or an empty optional if the read observes the default}
         * The element is read with acquire semantics without computing a value.
         * @param receiver the object holding the array field
         * @param index the index of the selected element
         * @throws NullPointerException if {@code receiver} or its array field is null
         * @throws IndexOutOfBoundsException if the index is outside the array
         * @throws IllegalArgumentException if the runtime array type is unsupported
         * @throws ClassCastException if the receiver is incompatible with the
         *         selected field or violates a protected access restriction
         */
        Optional<V> get(T receiver, int index);

        /**
         * {@return the installed non-default element value, boxed if primitive,
         * computing it if necessary}
         * The context is supplied to the computing function only if computation
         * is needed; it is not part of the storage location.
         * @param receiver the object holding the array field
         * @param index the index of the selected element
         * @param context the computation context, possibly {@code null}
         * @throws NullPointerException if {@code receiver} or its array field is null
         * @throws IndexOutOfBoundsException if the index is outside the array
         * @throws IllegalArgumentException if the runtime array type is unsupported,
         *         or the computed candidate is the reserved default value after
         *         conversion to the storage type
         * @throws ClassCastException if the receiver is incompatible with the
         *         selected field or violates a protected access restriction
         * @throws ArrayStoreException if the computed candidate is incompatible
         *         with the runtime component type of a reference array
         */
        V getOrCompute(T receiver, int index, C context);

        /**
         * Computes an array element from its index and context. It may be invoked
         * more than once for the same element, including concurrently.
         * @param <C> the type of computation context
         * @param <V> the type of candidate values
         * @since 29
         */
        @FunctionalInterface
        interface Computer<C, V> {
            /**
             * {@return a non-default candidate value}
             * @param index the index of the selected element
             * @param context the computation context, possibly {@code null}
             */
            V compute(int index, C context);
        }
    }
}
