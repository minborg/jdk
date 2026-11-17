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
 * returning an empty {@code Optional} if the read observes the reserved (non installed)
 * default value.
 * <p>
 * The factories do not read that storage or invoke the computing function when an
 * accessor is created.
 * <p>
 * For a ready-made supplier that caches one reference, use
 * {@link Supplier#ofLazy(Supplier) Supplier.ofLazy}. The
 * {@link List#ofLazy(int, IntFunction)}, {@link Set#ofLazy(Set, Predicate)}, and
 * {@link Map#ofLazy(Set, Function)} factories cache list elements, membership decisions,
 * and map values, respectively. Those factories compute each result at most once and
 * remember computation failures. The accessors in this class provide the underlying
 * storage operations: computations may race, and failed computations may be retried.
 *
 * <h2 id="access-shapes">Locations and access shapes</h2>
 * An accessor selects a storage location using the following coordinates:
 * <ul>
 * <li>For a named static field, there are no coordinates.</li>
 * <li>For a named instance field, the single coordinate is the instance receiver.</li>
 * <li>For an array element, the coordinates are the receiver holding the array
 *     field and the index.</li>
 * </ul>
 * The accessor's {@code get} and {@code getOrCompute} methods take these coordinates.
 * Each factory has a context-free variant and a variant with a computation context;
 * the two variants return different accessor interfaces. A context-aware
 * {@code getOrCompute} takes an additional context argument, which may be unrelated
 * to the storage owner. The context is an input to the computing function, not part
 * of the storage coordinates: invocations selecting the same location share the
 * installed value even if they supply different contexts.
 * <p>
 * Field accessors support reference and primitive fields. Array accessors select an
 * instance field containing a one-dimensional reference or primitive array. The
 * receiver selects the array field and the index selects an element. The underlying
 * array's identity and the index identify the cached location; different receivers
 * can share an array and therefore share its cached elements. Values of primitive
 * fields and array elements are represented by their wrapper types in the accessor API.
 * <p>
 * The array factory takes an element type token. That type must be able to hold
 * values of the declared array field's component type, boxed if primitive. A primitive
 * element type token is treated like its wrapper class token. An array class or
 * {@code void.class} is not a supported element type token.
 * <p>
 * A field can be selected by a type token (for example, in the form of a class literal)
 * describing the owner of the field and its simple field name. The field factories
 * also take an expected field type token. The owner type token identifies the class
 * in its defining loader, so the same binary name in a different loader does not
 * select that class.
 * A field declared by the named owner takes precedence. Otherwise, the nearest
 * superclass declaration is selected.
 * <p>
 * The computing function must return a non-default result compatible with the selected
 * storage type. The accessor's result type may be that type or a reference supertype of
 * the selected value, boxed if necessary. The explicit owner type token selects
 * the field independently of the computation context; erased type arguments remain
 * the caller's responsibility.
 *
 * <h2 id="computing-functions">Computing functions</h2>
 * An accessor retains its computing function and invokes it on the thread calling
 * {@code getOrCompute}. A reference parameter of a computing function may be a
 * supertype of the value supplied to it. Its result may be a subtype of
 * reference storage. For primitive fields and array elements, boxing, unboxing, or
 * widening primitive conversion may apply. A broader reference result is checked
 * against the storage type when computed. An incompatible result fails before
 * publication; in particular, {@code null} cannot be converted to a primitive value.
 * As with ordinary array stores, a runtime reference array whose component
 * type is narrower than the declared array type may reject a computed value with
 * {@link ArrayStoreException}.
 * <p>
 * The computing function is fixed when the accessor is created. For a context-aware
 * accessor, a context can be supplied at each invocation of {@code getOrCompute}.
 * This allows a single computing function to use inputs supplied dynamically without
 * requiring a new function at each invocation. A receiver-dependent field computation
 * can use the receiver as its context by passing it explicitly as both arguments to
 * {@code getOrCompute}.
 * <p>
 * A context-free field computing function takes no arguments. A context-free array
 * computing function receives the length of its underlying array and the selected
 * index. The context-aware variants additionally receive the context. The receiver
 * and array itself are not supplied automatically to the computing function. A
 * computation that needs to read or initialize another managed element can use an
 * accessor and obtain a receiver through its context or a captured reference.
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
 * The accessors always use a <em>publish once</em> installation scheme where
 * an invocation of {@code getOrCompute} reads the selected location with acquire
 * semantics. If it is uninitialized, the accessor computes a candidate and attempts
 * to install it.
 * A successful installation atomically replaces the reserved default value with
 * the candidate and publishes it with release effects. An invocation that reads
 * the installed value does so with acquire effects.
 * <p>
 * The memory effects of accesses performed through an accessor are independent
 * of whether a field used directly as storage is declared {@code volatile}.
 * <p>
 * Computations may race, but every invocation of {@code getOrCompute} that returns
 * normally returns the value installed in the location, including an invocation
 * whose result was discarded.
 * The {@code get} methods also read with acquire semantics, but do not invoke the
 * computing function or attempt an installation.
 * <p>
 * A losing invocation observes the installed value with acquire effects before
 * returning it. The visibility guarantees are described under
 * {@linkplain ##memory-effects Memory effects}. A computation that throws or
 * produces the reserved value fails even if another invocation has installed a value in
 * the meantime; that accessor does not attempt its own installation. A throwable from
 * the computation propagates unchanged. If the location remains uninitialized, a later
 * invocation can retry. A losing compare-and-exchange does not install its candidate.
 * Neither failure nor loss by itself publishes the computing function's actions through
 * the selected location; a computing function can publish through other actions,
 * including another initialization. Recursive access to the same uninitialized location
 * is not detected and may recurse without bound.
 * <p>
 * Accessors do not provide a <em>plain</em> access mode. Clients that need plain
 * access would have to resort to conditional assignment such as
 * {@code if (x == 0) x = computeX();}. Without external synchronization, this
 * pattern can return different values to different callers, expose
 * incompletely initialized objects, and/or permit tearing of non-volatile
 * {@code long} and {@code double} values.
 *
 * <h2 id="memory-effects">Memory effects</h2>
 * For each lazily initialized location, actions in the publishing thread preceding
 * publication of the installed value
 * {@linkplain java.util.concurrent##MemoryVisibility happen-before} actions
 * following an invocation in another thread that observes that value.
 * These guarantees do not impose a total order on accesses to different locations.
 * Subsequent accesses provide no additional memory ordering or visibility guarantees
 * for actions performed after publication. Publishing a reference does not make
 * later mutations of the referenced object thread-safe.
 *
 * <h2 id="access-and-storage-ownership">Access and storage ownership</h2>
 * The caller must safely publish any receiver, array, and preexisting values to
 * threads that use an accessor. While a field or array element is
 * managed by stable accessors, all reads and writes of that location must be
 * performed through those accessors. Clients, including computing functions,
 * must not read, write, or reset the location directly or through another access
 * mechanism. This requirement also applies to fields declared {@code volatile}.
 * <p>
 * Use {@code get} to observe a location without initializing it, and
 * {@code getOrCompute} to initialize it if needed. Access outside stable
 * accessors is not covered by their guarantees. In particular, plain reads can
 * observe default or stale values and can expose incompletely initialized state
 * in referenced objects. Plain reads of non-volatile {@code long} or {@code double}
 * locations may also observe torn values. Direct writes can invalidate the
 * publish-once and stability guarantees. Multiple stable accessors may share a
 * storage location.
 * <p>
 * An accessor does not retain a receiver passed to {@code get} or
 * {@code getOrCompute}, though it may retain objects captured by its computing function.
 * <p>
 * For field factories, the caller must have access to the selected owner and field.
 * Accessing a static field through the accessor initializes its declaring class
 * if necessary, following normal var-handle semantics. A field used
 * directly as storage must be non-final and annotated with
 * {@link ReflectiveAccessOnly} in field mode. An annotation with
 * {@link ReflectiveAccessOnly#arrayElements() arrayElements} set to {@code true}
 * applies to array elements rather than the field and does not satisfy this requirement.
 * The annotation declares intent but does not establish exclusive access; the
 * storage-ownership requirements above still apply.
 * <p>
 * An array factory selects an accessible non-static array field annotated with
 * {@code @ReflectiveAccessOnly(arrayElements = true)}. The caller must ensure that
 * the selected field contains a non-null array and is not replaced while its elements
 * are managed by an accessor. The field may be {@code final}. All aliases of the
 * array remain subject to the storage-ownership requirements above.
 *
 * <h2 id="encapsulating-stable-accessors">Encapsulating stable accessors</h2>
 * A class can encapsulate one or more stable accessors to cache values in its internal
 * fields. Such caches can use different policies for:
 * <ul>
 *     <li>whether computations may run concurrently;</li>
 *     <li>how many times the computing function can be invoked;</li>
 *     <li>which state is retained after computation;</li>
 *     <li>whether failed computations are retried; and</li>
 *     <li>whether recursive access is detected.</li>
 * </ul>
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
 *     private static final StableFieldWithContext<StringCache, Configuration, String> ACCESSOR =
 *             StableAccessors.ofField(StringCache.class,            // Field owner
 *                     String.class,                                 // Field type
 *                     "value",                                      // Field name
 *                     Configuration.class,                          // Extra context
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
 * Competing threads may compute candidates concurrently, but every normally returning
 * {@code get(config)} returns the installed value. The configuration affects the
 * computation, but does not select a separate cached value: the first successful
 * installation determines the value for that {@code StringCache} instance.
 * A call whose computation throws or returns
 * {@code null} still fails, even if another call installs a value. If the field remains
 * uninitialized, a later call may retry. Hence, the computing function can be invoked
 * more than once. Recursive {@code get(config)} calls are not detected and may recurse without
 * bound. The owner and context can be the same, in which case clients can invoke the
 * computing accessor with {@code ACCESSOR.getOrCompute(this, this)}.
 * <p>
 * Different policies can be built using the same accessors. For example, a wrapper
 * could cache a record representing either a computed value or a failure marker and
 * serialize the recheck, computation, and publication to invoke its computing function
 * at most once.
 * <p>
 * A static field can likewise be initialized using a context or without a context as
 * shown in this example:
 * {@snippet lang = java:
 * import java.lang.invoke.StableAccessors;
 * import java.lang.invoke.StableAccessors.StableStaticFieldWithContext;
 * import java.lang.invoke.ReflectiveAccessOnly;
 * import java.nio.file.Path;
 *
 * class SharedName {
 *
 *     @ReflectiveAccessOnly
 *     private static String value;
 *
 *     private static final StableStaticField<Path, String> ACCESSOR =
 *             StableAccessors.ofStaticField(SharedName.class, // Field owner
 *                     String.class,                           // Field type
 *                     "value",                                // Field name
 *                     () -> "Some value");                    // Computing function
 *
 *     static String get() {
 *         return ACCESSOR.getOrCompute();
 *     }
 * }
 *}
 * All calls use the same static field, irrespective of the supplied path. The
 * successful installation determines the value returned by subsequent calls.
 *
 * <h2 id="indexed-caches">Indexed caches</h2>
 * An array accessor can compute and cache a separate value at each index.
 * The following example shows a memoized String array with properties similar to the
 * cached string class above:
 * {@snippet lang = java:
 * import java.lang.invoke.StableAccessors;
 * import java.lang.invoke.StableAccessors.StableArray;
 * import java.lang.invoke.ReflectiveAccessOnly;
 *
 * class StringArrayCache {
 *
 *     private static final StableArray<StringArrayCache, String> ACCESSOR =
 *             StableAccessors.ofArray(
 *                     StringArrayCache.class, // Field owner
 *                     String.class,           // Element type
 *                     "array",                // Array field name
 *                     (length, index) -> "From end " + (length - index - 1));
 *
 *     @ReflectiveAccessOnly(arrayElements = true)
 *     private final String[] array = new String[16];
 *
 *     public String get(int i) {
 *         return ACCESSOR.getOrCompute(this, i);
 *     }
 *
 * }
 *}
 * Each index is initialized independently. Computations for the same index may
 * race, but every normally returning call receives the element installed at that index.
 * <p>
 * Array elements can also be calculated from a context that, for example, can be the
 * instance in which the array is declared:
 * {@snippet lang = java:
 * import java.lang.invoke.StableAccessors;
 * import java.lang.invoke.StableAccessors.StableArrayWithContext;
 * import java.lang.invoke.ReflectiveAccessOnly;
 * import java.util.Objects;
 *
 * class StringArrayCache {
 *
 *     private static final StableArrayWithContext<StringArrayCache, StringArrayCache, String> ACCESSOR =
 *             StableAccessors.ofArray(
 *                     StringArrayCache.class, // Field owner
 *                     String.class,           // Element type
 *                     "array",                // Array field name
 *                     String.class,           // Context type
 *                     (length, index, prefix) -> prefix + (length - index - 1)); // Computing function
 *
 *     @ReflectiveAccessOnly(arrayElements = true)
 *     private final String[] array;
 *     private final String prefix;
 *
 *     StringArrayCache(int size, String prefix) {
 *         this.array = new String[size];
 *         this.prefix = Objects.requireNonNull(prefix);
 *     }
 *
 *     public String get(int index) {
 *         return ACCESSOR.getOrCompute(this, index, this.prefix);
 *     }
 *
 * }
 *}
 * Passing {@code this} supplies the context without requiring a capturing computing
 * function.
 * <p>
 * Primitive arrays can also be used. The following cache stores non-zero
 * {@code int} values and represents results as {@link Integer}:
 * {@snippet lang = java:
 * import java.lang.invoke.ReflectiveAccessOnly;
 * import java.lang.invoke.StableAccessors;
 * import java.lang.invoke.StableAccessors.StableArray;
 *
 * class IntArrayCache {
 *
 *     private static final StableArray<IntArrayCache, Integer> ACCESSOR =
 *             StableAccessors.ofArray(IntArrayCache.class, // Field owner
 *                     Integer.class,                       // Boxed element type
 *                     "array",                             // Array field name
 *                     (length, index) -> index + 1);       // Computing function
 *
 *     @ReflectiveAccessOnly(arrayElements = true)
 *     private final int[] array = new int[16];
 *
 *     int get(int index) {
 *         return ACCESSOR.getOrCompute(this, index);
 *     }
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
 *           Lazily initialized fields may be eligible for constant folding as described
 *           in the {@linkplain ##performance performance section}.
 *           Accessors should generally be declared {@code static final} in order to
 *           present the best performance optimization opportunities for the VM.
 *
 * @since 29
 *
 * @see Optional
 * @see Supplier#ofLazy(Supplier)
 * @see List#ofLazy(int, IntFunction)
 * @see Set#ofLazy(java.util.Set, Predicate)
 * @see Map#ofLazy(java.util.Set, Function)
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
     * non-final, accessible to the caller, and annotated with {@link ReflectiveAccessOnly}
     * in field mode. Its type must be compatible with {@code fieldType}. The computing
     * function takes no arguments and produces a non-default candidate when the field
     * is uninitialized. The accessor uses {@linkplain ##publish-once publish once}.
     * <p>
     * This method is caller-sensitive: the immediate caller's privileges determine
     * access to the field. Calling the resulting accessor does not repeat the access
     * check. The caller must ensure that computed and preexisting field values can be
     * used as {@code V}; incompatible candidates fail before publication.
     *
     * @param owner the type through which the field is selected
     * @param fieldType the expected field type
     * @param name the simple field name
     * @param computingFunction the function computing a non-default value
     * @param <V> the result type expected by the caller
     * @throws IllegalCallerException if there is no caller frame
     * @throws IllegalArgumentException if the field cannot be found or accessed, is
     *         incompatible, is not static or is final, lacks the required annotation,
     *         or the required access mode is unsupported, or if {@code name} is invalid
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
        throw new UnsupportedOperationException();
    }

    /**
     * {@return a stable accessor for a static field, using a computation context}
     * <p>
     * The selected field has the same requirements as for
     * {@link #ofStaticField(Class, Class, String, StableStaticField.Computer)}.
     * The supplied context is passed to the computing function only if computation
     * is needed; it does not select a separate cached value. Invocations using
     * different contexts share the field, and the successful installation determines
     * the cached value.
     * <p>
     * This method is caller-sensitive: the immediate caller's privileges determine
     * access to the field. Calling the resulting accessor does not repeat the access
     * check. The caller must ensure contexts are suitable for the computing function
     * and computed and preexisting field values can be used as {@code V}.
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
     *         or the required access mode is unsupported, or if {@code name} is invalid
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
        throw new UnsupportedOperationException();
    }

    /**
     * {@return a stable accessor for an instance field, without a computation context}
     * <p>
     * The field is selected by {@code receiverType} and {@code name}. It must be
     * non-static, non-final, accessible to the caller, and annotated with
     * {@link ReflectiveAccessOnly} in field mode. Its type must be compatible with
     * {@code fieldType}. The computing function takes no arguments and produces a
     * non-default candidate when the field is uninitialized. The accessor uses
     * {@linkplain ##publish-once publish once}.
     * <p>
     * This method is caller-sensitive: the immediate caller's privileges determine
     * access to the field. Calling the resulting accessor does not repeat the access
     * check. If the field is protected and declared in a different runtime package
     * from the caller, a receiver must be an instance of the caller class or a subclass.
     * The caller must ensure computed and preexisting field values can be used as
     * {@code V}; incompatible candidates fail before publication.
     *
     * @param receiverType the type through which the field is selected
     * @param fieldType the expected field type
     * @param name the simple field name
     * @param computingFunction the function computing a non-default value
     * @param <T> the receiver type
     * @param <V> the result type expected by the caller
     * @throws IllegalCallerException if there is no caller frame
     * @throws IllegalArgumentException if the field cannot be found or accessed, is
     *         incompatible, is static or final, lacks the required annotation, or the
     *         required access mode is unsupported, or if {@code name} is invalid
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
        final Class<?> protectedReceiver = Modifier.isProtected(field.getModifiers())
                && !VerifyAccess.isSamePackage(caller, field.getDeclaringClass())
                ? caller : null;
        throw new UnsupportedOperationException();
    }

    /**
     * {@return a stable accessor for an instance field, using a computation context}
     * <p>
     * The selected field has the same requirements as for
     * {@link #ofField(Class, Class, String, StableField.Computer)}. The context is
     * passed to the computing function only if computation is needed; it does not
     * select a separate cached value. Invocations using the same receiver share the
     * field's installed value even if they supply different contexts.
     * <p>
     * This method is caller-sensitive: the immediate caller's privileges determine
     * access to the field. Calling the resulting accessor does not repeat the access
     * check. The protected-receiver restriction described by the context-free factory
     * also applies. The caller must ensure contexts are suitable for the computing
     * function and computed and preexisting field values can be used as {@code V}.
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
     *         incompatible, is static or final, lacks the required annotation, or the
     *         required access mode is unsupported, or if {@code name} is invalid
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
     * non-static, accessible to the caller, have a supported one-dimensional array
     * type, and be annotated with {@code @ReflectiveAccessOnly(arrayElements = true)}.
     * It may be final. {@code arrayComponentType} must be able to hold values of the
     * field's component type, boxed if primitive. A primitive component token is treated
     * as its wrapper type. The function receives the array length and index and produces
     * a non-default candidate when the selected element is uninitialized.
     * <p>
     * This method is caller-sensitive: the immediate caller's privileges determine
     * access to the field and the component and element types. Calling the resulting
     * accessor does not repeat these checks. If the field is protected and declared in
     * a different runtime package from the caller, a receiver must be an instance of
     * the caller class or a subclass. The field must continue to refer to the same
     * array while an accessor is in use. See
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
     * The selected field has the same requirements as for
     * {@link #ofArray(Class, Class, String, StableArray.Computer)}. The computing
     * function receives the array length, index, and supplied context. The context is
     * computation input, not a storage coordinate: invocations selecting the same
     * element share the installed value even if they supply different contexts.
     * <p>
     * This method is caller-sensitive: the immediate caller's privileges determine
     * access to the field and the component and element types. Calling the resulting
     * accessor does not repeat these checks. The protected-receiver restriction
     * described by the context-free factory also applies.
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
         * @throws IllegalArgumentException if the runtime array type is unsupported
         * @throws ClassCastException if the receiver is incompatible with the
         *         selected field or violates a protected access restriction
         */
        V getOrCompute(T receiver, int index);

        /**
         * Computes an array element from the array length and index. It may be
         * invoked more than once for the same element, including concurrently.
         * @param <V> the type of candidate values
         * @since 29
         */
        @FunctionalInterface
        interface Computer<V> {
            /**
             * {@return a non-default candidate value}
             * @param length the length of the selected array
             * @param index the index of the selected element
             */
            V compute(int length, int index);
        }
    }

    /**
     * A stable accessor for elements of an instance array field, with a
     * computation context. The array referenced by the selected field and the
     * index identify the storage location. Different receivers may share an array
     * and thus share its installed element values, regardless of context.
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
         * @throws IllegalArgumentException if the runtime array type is unsupported
         * @throws ClassCastException if the receiver is incompatible with the
         *         selected field or violates a protected access restriction
         */
        V getOrCompute(T receiver, int index, C context);

        /**
         * Computes an array element from the array length, index and context. It
         * may be invoked more than once for the same element, including concurrently.
         * @param <C> the type of computation context
         * @param <V> the type of candidate values
         * @since 29
         */
        @FunctionalInterface
        interface Computer<C, V> {
            /**
             * {@return a non-default candidate value}
             * @param length the length of the selected array
             * @param index the index of the selected element
             * @param context the computation context, possibly {@code null}
             */
            V compute(int length, int index, C context);
        }
    }
}
