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
 * <li>For an array element, the coordinates are the array and the index.</li>
 * </ul>
 * The accessor's {@code get} and {@code getOrCompute} methods take these coordinates and,
 * for all locations, {@code getOrCompute} also accepts an optional computation context.
 * A field computing function receives only this context; an array computing
 * function receives the array's length, the index, and the context. The
 * {@code getOrCompute} overloads without a context argument supply {@code null}.
 * A computation context may be unrelated to the storage owner. It is an input to
 * the computing function, not part of the storage coordinates: invocations selecting
 * the same location share the installed value even if they supply different contexts.
 * <p>
 * Field accessors support reference and primitive fields. Array accessors support
 * one-dimensional reference and primitive arrays, supplied when {@code get} or
 * {@code getOrCompute} is invoked. No field containing the array is selected.
 * For a multidimensional array such as {@code String[][]}, each {@code String[]}
 * row can be passed separately to the same accessor. Values of primitive fields
 * and array elements are represented by their wrapper types in the accessor API.
 * <p>
 * The array factory takes an array type token and an element type token. The array
 * type token must describe a one-dimensional array. The element type must be able
 * to hold values of the array type's component type, boxed if primitive. A primitive
 * element type token is treated like its wrapper class token. An array class or
 * {@code void.class} is not a supported element type token.
 * <p>
 * A field can be selected by a type token (for example, in the form of a class literal)
 * describing the owner of the field and its simple field name. The direct-field factories
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
 * The computing function is fixed when the accessor is created, while a context
 * can be supplied at each invocation of
 * {@code getOrCompute}. This allows a single computing function to use inputs
 * supplied dynamically without requiring a new function at each invocation.
 * A receiver-dependent field computation can use the receiver as its context by
 * passing it explicitly as both arguments to {@code getOrCompute}.
 * <p>
 * An array computing function receives the length of its underlying array, the
 * selected index, and the computation context. The array itself is not supplied
 * to the function. A computation that needs to read another element can obtain
 * the array through its context or a captured reference and use
 * {@link StableArray#get(Object, int)} to observe that element without computing
 * it, or {@link StableArray#getOrCompute(Object, int, Object)} to initialize it
 * if needed. The computing function must still respect the storage-ownership
 * requirements when it has access to the array.
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
 * The caller must safely publish any receiver or array and any preexisting
 * values to threads that use an accessor. While a field or array element is
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
 * storage-ownership requirements above still apply. Array accessors receive an array
 * directly and do not require an annotated field.
 * <p>
 * The array factory checks access to the supplied element type with the caller's
 * privileges. The array passed to {@code get} or {@code getOrCompute} need not come
 * from a named field, and the reference used to pass it need not be final. No
 * member-access check is performed on any field that may refer to the array. Its
 * selected elements remain subject to the storage-ownership requirements above.
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
 * import java.lang.invoke.StableAccessors.StableField;
 * import java.lang.invoke.ReflectiveAccessOnly;
 *
 * record Configuration(String prefix, int number) {}
 *
 * class StringCache {
 *
 *     private static final StableField<StringCache, Configuration, String> ACCESSOR =
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
 * A static field can likewise be initialized using a context supplied at the call site:
 * {@snippet lang = java:
 * import java.lang.invoke.StableAccessors;
 * import java.lang.invoke.StableAccessors.StableStaticField;
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
 *                     Path.class,                             // Extra context
 *                     Path::toString);                        // Computing function
 *
 *     static String get(Path path) {
 *         return ACCESSOR.getOrCompute(path);
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
 *     private static final StableArray<String[], Void, String> ACCESSOR =
 *             StableAccessors.ofArray(
 *                      String[].class,  // Array type
 *                      String.class,    // Element type
 *                      Void.class,      // Context type. Not used in this example
 *                      (length, index, _) -> "From end " + (length - index - 1)); // Computing function
 *
 *     @ReflectiveAccessOnly(arrayElements = true)
 *     private final String[] array = new String[16];
 *
 *     public String get(int i) {
 *         return ACCESSOR.getOrCompute(array, i); // Delegates to .getOrCompute(array, i, null)
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
 * import java.lang.invoke.StableAccessors.StableArray;
 * import java.lang.invoke.ReflectiveAccessOnly;
 * import java.util.Objects;
 *
 * class StringArrayCache {
 *
 *     private static final StableArray<String[], StringArrayCache, String> ACCESSOR =
 *             StableAccessors.ofArray(
 *                     String[].class,         // Array type
 *                     String.class,           // Element type
 *                     StringArrayCache.class, // Context type. Allows `this` to be used
 *                     (length, index, ctx) -> ctx.prefix + (length - index - 1)); // Computing function
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
 *         return ACCESSOR.getOrCompute(array, index, this);
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
 *     private static final StableArray<int[], Void, Integer> ACCESSOR =
 *             StableAccessors.ofArray(int[].class, // Array type
 *                     Integer.class,               // Boxed element type
 *                     Void.class,                  // Context type. Not used in this example
 *                     (length, index, _) -> index + 1); // Computing function
 *
 *     @ReflectiveAccessOnly(arrayElements = true)
 *     private final int[] array = new int[16];
 *
 *     int get(int index) {
 *         return ACCESSOR.getOrCompute(array, index);
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
     * {@return a stable accessor for a static field accessible to the caller,
     * using a computation context}
     * <p>
     * This method uses {@linkplain ##publish-once publish once} and the supplied field
     * as storage. Its result is a {@link StableStaticField} whose
     * {@linkplain StableStaticField#getOrCompute(Object) getOrCompute()} method
     * accesses that field. The computing function receives the supplied context and
     * is invoked only when the field has its reserved Java default value. The field
     * must be static, non-final, and annotated with {@link ReflectiveAccessOnly}
     * in field mode ({@code arrayElements} is {@code false}). Access is checked
     * with the caller's privileges.
     * <p>
     * The context supplies computation input and does not select a separate cached
     * value. Invocations share the installed field value even if they supply different
     * contexts. If computations using different contexts race, the successful
     * installation determines the cached value. An invocation that initially observes
     * an installed value returns it without invoking the computing function.
     * <p>
     * This method is caller-sensitive: a helper that invokes it supplies the helper's
     * access privileges. Once the accessor is created, calling its
     * {@linkplain StableStaticField#get() get()} or
     * {@linkplain StableStaticField#getOrCompute(Object) getOrCompute()} method does not
     * repeat the caller access check. A caller may select its own private static field
     * or one in a nestmate. Access to an unrelated private field is denied.
     * <p>
     * The {@code computingFunction} may accept a supertype of {@code C} and produce
     * a subtype of {@code V}. The type parameters {@code C} and {@code V} are erased.
     * The caller must ensure that contexts are suitable for the computing function,
     * that computed values are compatible with the field, and that any preexisting
     * field value can be used as {@code V}. Incompatible computed values fail before
     * publication, as described in
     * {@linkplain ##computing-functions Computing functions}.
     *
     * @param owner the type through which the static field is selected
     * @param fieldType the expected field type
     * @param name the simple name of the field
     * @param contextType the type through which the computation context is described
     * @param computingFunction the function that computes a non-default field value
     * @param <C> the type of computation context
     * @param <V> the result type expected by the caller
     * @throws IllegalCallerException if there is no caller frame
     * @throws IllegalArgumentException if {@code name} is empty or contains a
     *         dot, the field cannot be accessed, is not a suitable mutable
     *         static location, lacks {@code @ReflectiveAccessOnly} in field mode,
     *         or the required access mode is unsupported
     * @throws NullPointerException if any argument is null
     * @since 29
     */
    @CallerSensitive
    public static <C, V> StableStaticField<C, V> ofStaticField(Class<?> owner,
                                                              Class<? super V> fieldType,
                                                              String name,
                                                              Class<? super C> contextType,
                                                              StableStaticField.Computer<? super C, ? extends V> computingFunction) {
        // Keep caller identification in this entry point. A helper accepting
        // a caller Class could be invoked reflectively with a forged argument.
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
     * {@return a stable accessor for an instance field accessible to the caller,
     * using a computation context}
     * <p>
     * This method uses {@linkplain ##publish-once publish once} and the supplied field
     * as storage. Its result is a {@link StableField} whose
     * {@linkplain StableField#getOrCompute(Object, Object) getOrCompute()} method
     * accesses that field. The computing function receives the supplied context and
     * is invoked only when the field has its reserved Java default value. The field
     * must be neither static nor final and must be annotated with
     * {@link ReflectiveAccessOnly} in field mode ({@code arrayElements} is
     * {@code false}). Access is checked with the caller's privileges.
     * <p>
     * The receiver and selected field identify the storage location. The context
     * supplies computation input and does not select a separate cached value.
     * Invocations using the same receiver share the installed field value even if
     * they supply different contexts. If computations using different contexts race,
     * the successful installation determines the cached value. An invocation that
     * initially observes an installed value returns it without invoking the computing
     * function. The receiver is not supplied to the computing function unless it is
     * explicitly passed as the context.
     * <p>
     * This method is caller-sensitive: a helper that invokes it supplies the helper's
     * access privileges. Once the accessor is created, calling its
     * {@linkplain StableField#get(Object) get()} or
     * {@linkplain StableField#getOrCompute(Object, Object) getOrCompute()} method does not
     * repeat the caller access check. A caller may select its own private non-static
     * field or one in a nestmate. Access to an unrelated private field is denied.
     * <p>
     * If the field is protected and declared in a different runtime package from
     * the caller, the accessor accepts only receivers that are instances of the
     * caller class or its subclasses, even if {@code T} is broader. This
     * restriction is checked when {@code get} or {@code getOrCompute} is invoked.
     * <p>
     * The {@code computingFunction} may accept a supertype of {@code C} and produce
     * a subtype of {@code V}. The type parameters {@code C} and {@code V} are erased.
     * The caller must ensure that contexts are suitable for the computing function,
     * that computed values are compatible with the field, and that any preexisting
     * field value can be used as {@code V}. Incompatible computed values fail before
     * publication, as described in
     * {@linkplain ##computing-functions Computing functions}.
     *
     * @param receiverType the type through which the instance field is selected
     * @param fieldType the expected field type
     * @param name the simple name of the field
     * @param contextType the type through which the computation context is described
     * @param computingFunction the function that computes a non-default field value
     * @param <T> the receiver type
     * @param <C> the type of computation context
     * @param <V> the result type expected by the caller
     * @throws IllegalCallerException if there is no caller frame
     * @throws IllegalArgumentException if {@code name} is empty or contains a
     *         dot, the field cannot be accessed, is not a suitable mutable
     *         instance location, lacks {@code @ReflectiveAccessOnly} in field mode,
     *         or the required access mode is unsupported
     * @throws NullPointerException if any argument is null
     * @since 29
     */
    @CallerSensitive
    public static <T, C, V> StableField<T, C, V> ofField(Class<? super T> receiverType,
                                                         Class<?> fieldType,
                                                         String name,
                                                         Class<? super C> contextType,
                                                         StableField.Computer<? super C, ? extends V> computingFunction) {
        // Keep caller identification in this entry point. A helper accepting
        // a caller Class could be invoked reflectively with a forged argument.
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
        // This allows us to run-time check `protectedReceiver.cast(receiver)`
        // for an instance field that is protected and declared outside the caller’s
        // runtime package,
        final Class<?> protectedReceiver =  Modifier.isProtected(field.getModifiers())
                        && !VerifyAccess.isSamePackage(caller, field.getDeclaringClass())
                        ? caller : null;
        throw new UnsupportedOperationException();
    }

    /**
     * {@return a stable accessor for elements of reference or primitive arrays,
     * using a computation context}
     * <p>
     * This method uses {@linkplain ##publish-once publish once}. Its result is a
     * {@link StableArray} whose
     * {@linkplain StableArray#getOrCompute(Object, int, Object) getOrCompute()} method
     * selects an element of the array supplied to that invocation. The computing
     * function receives the length of that array, the index, and the context
     * and is invoked only when the selected element is uninitialized.
     * <p>
     * The array's identity and the index identify the storage location. The context
     * supplies computation input and does not select a separate cached value.
     * Invocations using the same array and index share the installed value even if
     * they supply different contexts. If computations using different contexts race,
     * the successful installation determines the cached value. An invocation that
     * initially observes an installed value returns it without invoking the computing
     * function.
     * <p>
     * An accessor can be used with multiple arrays. The arrays need not be stored
     * in named fields. Each supplied array must be an instance of {@code arrayType}
     * and have a supported one-dimensional runtime array type. In particular, an
     * {@code Object[]} accessor does not accept a multidimensional runtime array.
     * The supported array types and the interpretation of {@code arrayComponentType}
     * are described under {@linkplain ##access-shapes Locations and access shapes}.
     * <p>
     * This method is caller-sensitive: a helper that invokes it supplies the helper's
     * access privileges for checking access to the array component and supplied
     * element types. No field is selected, and no member-access check is performed
     * on a field containing an array.
     * <p>
     * The type parameters {@code A}, {@code C}, and {@code V} are erased. The caller
     * must ensure that contexts are suitable for the computing function and that
     * computed and preexisting element values can be used as {@code V}. A candidate
     * incompatible with the runtime array's component type fails before publication,
     * as described under {@linkplain ##computing-functions Computing functions}.
     *
     * @param arrayType the type of arrays accessed by the accessor
     * @param arrayComponentType the expected element result type
     * @param contextType the type through which the computation context is described
     * @param computingFunction the function that computes a non-default element value
     * @param <A> the type of arrays accessed by the accessor
     * @param <C> the type of computation context
     * @param <V> the result type, boxed if the array has a primitive component type
     * @throws IllegalCallerException if there is no caller frame
     * @throws IllegalArgumentException if an array or element type cannot be accessed,
     *         is unsupported, or the element type is incompatible with the array type
     * @throws NullPointerException if any argument is null
     * @since 29
     */
    @CallerSensitive
    public static <A, C, V> StableArray<A, C, V> ofArray(Class<A> arrayType,
                                                         Class<? super V> arrayComponentType,
                                                         Class<? super C> contextType,
                                                         StableArray.Computer<? super C, ? extends V> computingFunction) {
        // Keep caller identification in this entry point. A helper accepting
        // a caller Class could be invoked reflectively with a forged argument.
        final Class<?> caller = Reflection.getCallerClass();
        checkCaller(caller);
        Objects.requireNonNull(arrayType);
        Objects.requireNonNull(arrayComponentType);
        Objects.requireNonNull(contextType);
        Objects.requireNonNull(computingFunction);
        checkArrayTypes(caller, arrayType, arrayComponentType);
        throw new UnsupportedOperationException();
    }

    /**
     * Represents a stable accessor for a static field, with an additional
     * computation context.
     * <p>
     * Every normally returning invocation of {@link #getOrCompute(Object)} returns
     * the same installed result, even if invocations supply different contexts.
     * The {@link #get()} method observes the field without computing or installing
     * a value. The context is an input to computation, not a storage coordinate.
     *
     * @param <C> the type of computation context
     * @param <V> the type of results returned by this accessor
     *
     * @since 29
     */
    public interface StableStaticField<C, V> {

        /**
         * {@return an optional containing the observed non-default field value,
         * or an empty optional if the read observes the reserved default value}
         * <p>
         * Reads the field with acquire semantics without invoking the computing
         * function or installing a value. Observing an installed value provides
         * the visibility guarantees described under
         * {@linkplain StableAccessors##memory-effects Memory effects}.
         */
        Optional<V> get();

        /**
         * {@return a non-default memoized result, otherwise computes and tries to install
         *          a non-default result}
         * <p>
         * Every normally returning invocation returns the same non-default value,
         * even if invocations supply different contexts. The computing function
         * receives the context only when computation is needed.
         *
         * @param context the context supplied to the computing function if invoked
         */
        V getOrCompute(C context);

        /**
         * {@return a non-default memoized result, otherwise computes and tries to install
         *          a non-default result}
         * <p>
         * This method is equivalent to:
         * {@snippet lang=java:
         *     return getOrCompute(null);
         * }
         * If computation is needed, the computing function receives a {@code null}
         * context and may reject it.
         */
        default V getOrCompute() {
            return getOrCompute(null);
        }

        /**
         * Represents a function that computes a non-default static-field value from
         * a computation context. It may be invoked more than once for the same field,
         * including concurrently.
         *
         * <p>There is no requirement that a new or distinct result be returned each
         * time the function is invoked.
         *
         * @param <C> the type of computation context
         * @param <V> the type of results returned by this function
         *
         * @since 29
         */
        @FunctionalInterface
        interface Computer<C, V> {

            /**
             * {@return a non-default candidate value for the selected field}
             *
             * @param context the computation context
             */
            V compute(C context);
        }

    }

    /**
     * Represents a stable accessor for an instance field, with an additional
     * computation context.
     * <p>
     * Every normally returning invocation of {@link #getOrCompute(Object, Object)}
     * returns the same installed result for any given receiver. The
     * {@link #get(Object)} method observes the field without computing or installing
     * a value.
     * <p>
     * The context is an input to computation, not part of the storage coordinates.
     * Invocations using the same receiver share the installed field value even if
     * they supply different contexts.
     *
     * @param <T> the type of the receiver
     * @param <C> the type of computation context
     * @param <V> the type of results returned by this accessor
     *
     * @since 29
     */
    public interface StableField<T, C, V> {

        /**
         * {@return an optional containing the observed non-default field value,
         * or an empty optional if the read observes the reserved default value}
         * <p>
         * Reads the field with acquire semantics without invoking the computing
         * function or installing a value. Observing an installed value provides
         * the visibility guarantees described under
         * {@linkplain StableAccessors##memory-effects Memory effects}.
         *
         * @param receiver the object holding the selected field
         * @throws ClassCastException if the selected field is protected and
         *         declared in a different runtime package from the factory caller,
         *         and a non-null {@code receiver} is not an instance of that caller
         *         class or one of its subclasses
         */
        Optional<V> get(T receiver);

        /**
         * {@return a non-default memoized result, otherwise computes and tries to install
         *          a non-default result}
         * <p>
         * Every normally returning invocation returns the same non-default value for any
         * given receiver, even if invocations supply different contexts. The computing
         * function receives the context only when computation is needed.
         *
         * @param receiver the object holding the selected field
         * @param context the context supplied to the computing function if invoked
         * @throws ClassCastException if the selected field is protected and
         *         declared in a different runtime package from the factory caller,
         *         and a non-null {@code receiver} is not an instance of that caller
         *         class or one of its subclasses
         */
        V getOrCompute(T receiver, C context);

        /**
         * {@return a non-default memoized result, otherwise computes and tries to install
         *          a non-default result}
         * <p>
         * This method is equivalent to:
         * {@snippet lang=java:
         *     return getOrCompute(receiver, null);
         * }
         * If computation is needed, the computing function receives a {@code null}
         * context and may reject it. The receiver is not used as the context.
         *
         * @param receiver the object holding the selected field
         * @throws ClassCastException if the selected field is protected and
         *         declared in a different runtime package from the factory caller,
         *         and a non-null {@code receiver} is not an instance of that caller
         *         class or one of its subclasses
         */
        default V getOrCompute(T receiver) {
            return getOrCompute(receiver, null);
        }

        /**
         * Represents a function that computes a non-default field value from a
         * computation context. It may be invoked more than once for the same field,
         * including concurrently.
         * <p>
         * The context may be unrelated to the object holding the selected field.
         * The computing function must access managed storage only through an accessor,
         * as described under
         * {@linkplain StableAccessors##access-and-storage-ownership Access and storage ownership}.
         *
         * @param <C> the type of computation context
         * @param <V> the type of the result of the function
         *
         * @since 29
         */
        @FunctionalInterface
        interface Computer<C, V> {

            /**
             * {@return a non-default candidate value for the selected field}
             *
             * @param context the computation context
             */
            V compute(C context);
        }

    }

    /**
     * Represents a stable accessor for reference or primitive array elements, with an additional
     * computation context.
     * <p>
     * The array's identity and the index select an array element. For the same
     * selected element, every normally returning invocation of
     * {@link #getOrCompute(Object, int, Object)} returns the same installed value.
     * The {@link #get(Object, int)} method observes the element without computing
     * or installing a value. Different arrays have independent storage, even if
     * their contents are equal.
     * <p>
     * The context is an input to computation, not part of the storage coordinates.
     * Invocations using the same array and index share the installed value even if
     * they supply different contexts.
     *
     * @param <A> the type of arrays accessed by this accessor
     * @param <C> the type of computation context
     * @param <V> the result type, boxed if the array has a primitive component type
     * @since 29
     */
    public interface StableArray<A, C, V> {

        /**
         * {@return an optional containing the observed non-default element value,
         * boxed if primitive, or an empty optional if the read observes the default value}
         * <p>
         * Reads the element with acquire semantics without invoking the computing
         * function or installing a value. Observing an installed value provides
         * the visibility guarantees described under
         * {@linkplain StableAccessors##memory-effects Memory effects}.
         *
         * @param array the array containing the selected element
         * @param index the index of the selected array element
         * @throws NullPointerException if {@code array} is null
         * @throws IndexOutOfBoundsException if {@code index} is negative or is not
         *         less than {@code array.length}
         * @throws IllegalArgumentException if the runtime array type is unsupported
         * @throws ClassCastException if the array is not an instance of the array type
         *         supplied to the factory
         */
        Optional<V> get(A array, int index);

        /**
         * {@return the non-default value installed at the selected array element,
         * boxed if primitive}
         * <p>
         * If the element is uninitialized, invokes the computing function and
         * attempts to install a valid candidate.
         * <p>
         * The computing function must not read or write managed array elements
         * directly. Reads and initialization of other managed elements must be
         * performed through an accessor.
         *
         * @param array the array containing the selected element
         * @param index the index of the selected array element
         * @param context the context supplied to the computing function if invoked
         * @throws NullPointerException if {@code array} is null
         * @throws IndexOutOfBoundsException if {@code index} is negative or is not
         *         less than {@code array.length}
         * @throws IllegalArgumentException if the runtime array type is unsupported
         * @throws ClassCastException if the array is not an instance of the array type
         *         supplied to the factory
         */
        V getOrCompute(A array, int index, C context);

        /**
         * {@return the non-default value installed at the selected array element,
         * boxed if primitive}
         * <p>
         * This method is equivalent to:
         * {@snippet lang=java:
         *     return getOrCompute(array, index, null);
         * }
         * If computation is needed, the computing function receives a {@code null}
         * context and may reject it.
         *
         * @param array the array containing the selected element
         * @param index the index of the selected array element
         */
        default V getOrCompute(A array, int index) {
            return getOrCompute(array, index, null);
        }

        /**
         * Represents a function that computes an array element value from the
         * array's length, an index, and a context. It may be invoked more
         * than once for the same element, including concurrently.
         * <p>
         * The computing function must access managed array elements only through
         * an accessor, as described under
         * {@linkplain StableAccessors##access-and-storage-ownership Access and storage ownership}.
         *
         * @param <C> the type of computation context
         * @param <V> the type of computed values
         * @since 29
         */
        @FunctionalInterface
        interface Computer<C, V> {

            /**
             * {@return a non-default candidate value for the selected array element}
             *
             * @param length the length of the array containing the selected element
             * @param index the index of the selected array element
             * @param context the computation context
             */
            V compute(int length, int index, C context);

        }

    }

}
