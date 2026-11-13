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
 * Factory methods for lazy initialization of fields and array elements.
 * <p>
 * Lazy initializers use caller-owned storage. An invocation returns a non-default value
 * already in the selected location without computing. If no non-default value exists,
 * an initializer tries to invoke a <em>computing function</em> and install the resulting
 * candidate value in the selected location.
 * <p>
 * The factories do not read that storage or invoke the computing function when an
 * initializer is created.
 * <p>
 * For a ready-made supplier that caches one reference, use
 * {@link Supplier#ofLazy(Supplier) Supplier.ofLazy}. The
 * {@link List#ofLazy(int, IntFunction)}, {@link Set#ofLazy(Set, Predicate)}, and
 * {@link Map#ofLazy(Set, Function)} factories cache list elements, membership decisions,
 * and map values, respectively. Those factories compute each result at most once and
 * remember computation failures. The initializers in this class provide the underlying
 * storage operations: computations may race, and failed computations may be retried.
 *
 * <h2 id="access-shapes">Locations and access shapes</h2>
 * The computing function receives the storage coordinates (if any) and, for arrays,
 * an additional index coordinate, and returns a candidate value. An initializer's
 * {@code getOrCompute} method takes the same coordinates:
 * <ul>
 * <li>For a named static field, there are no coordinates.</li>
 * <li>For a named instance field, the single coordinate is the instance receiver.</li>
 * <li>For a named static array field, the single coordinate is the array index.</li>
 * <li>For a named array field, the first coordinate is the instance receiver (in which
 *     the array resides) and the second coordinate is the array index.</li>
 * </ul>
 * Array factories directly support one-dimensional array fields. Multidimensional
 * structures can be composed using holder objects, each with a final one-dimensional
 * array field whose elements are managed by an initializer.
 * <p>
 * Array factories also take an element type token. That type must be able to hold
 * values of the array's component type, boxed if primitive. A primitive class token
 * is treated like its wrapper class token.
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
 * storage type. The initializer's result type may be that type or a reference supertype of
 * the selected value, boxed if necessary. The explicit owner type token permits a raw
 * generic computing function to select a named field even if its receiver type erases to
 * {@code Object}; erased value type arguments remain the caller's responsibility.
 *
 * <h2 id="computing-functions">Computing functions</h2>
 * An initializer retains its computing function and invokes it on the thread calling
 * {@code getOrCompute}. A reference parameter of a field or array computing function may
 * be a supertype of its corresponding coordinate type. Its result may be a subtype of
 * reference storage or use boxing, unboxing, or widening primitive conversion; a broader
 * reference result is checked against the storage type when computed. An incompatible
 * result fails before publication. As with ordinary array stores, a runtime array whose
 * component type is narrower than the declared array type may reject a computed value
 * with {@link ArrayStoreException}.
 *
 * <h2 id="uninitialized-locations">Uninitialized locations</h2>
 * The Java default value of a selected field or array element means uninitialized:
 * {@code null} for references, {@code false} for {@code boolean}, zero for
 * integral types, and positive zero for floating-point types.
 * <p>
 * A computation producing this reserved value throws {@code IllegalArgumentException}
 * without publishing it. Negative floating-point zero and {@code NaN} are permitted.
 * A null-restricted reference field cannot use the {@code null} sentinel and is
 * rejected when an initializer selecting that field is created. A null-restricted
 * reference array likewise cannot use the {@code null} sentinel; accessing one of its
 * elements through an initializer throws {@code IllegalArgumentException} before
 * computation or return, even if the element already has a value.
 * <p>
 * A nullable wrapper field or array element can distinguish a computed zero or
 * {@code false} from uninitialized storage. If {@code null} is a logical result,
 * a non-null container such as {@link java.util.Optional} can represent it.
 *
 * <h2 id="publish-once">Publish once</h2>
 * The initializers always use a <em>publish once</em> installation scheme where
 * an invocation reads the selected location with acquire semantics. If it is
 * uninitialized, the initializer computes a candidate and attempts to install it.
 * A successful installation atomically replaces the reserved default value with
 * the candidate and publishes it with release effects. An invocation that reads
 * the installed value does so with acquire effects.
 * <p>
 * The memory effects of accesses performed through an initializer are independent
 * of whether a field used directly as storage is declared {@code volatile}.
 * <p>
 * Computations may race, but every invocation that returns normally returns the value
 * installed in the location, including an invocation whose result was discarded.
 * <p>
 * A losing invocation observes the installed value with acquire effects before
 * returning it. The visibility guarantees are described under
 * {@linkplain ##memory-effects Memory effects}. A computation that throws or
 * produces the reserved value fails even if another invocation has installed a value in
 * the meantime; that initializer does not attempt its own installation. A throwable from
 * the computation propagates unchanged. If the location remains uninitialized, a later
 * invocation can retry. A losing compare-and-exchange does not install its candidate.
 * Neither failure nor loss by itself publishes the computing function's actions through
 * the selected location; a computing function can publish through other actions,
 * including another initialization. Recursive access to the same uninitialized location
 * is not detected and may recurse without bound.
 * <p>
 * Initializers do not provide a <em>plain</em> access mode. Clients that need plain
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
 * values to threads that use an initializer. The selected storage must be reserved
 * exclusively for lazy initialization use: it must not be reset or otherwise written
 * directly while an initializer is in use. Direct reads of the location are permitted
 * and have the memory effects of the read operation used. A direct Java read of a
 * {@code volatile} field has volatile read semantics. Multiple initializers may
 * share a storage location. Other non-initializer writes can defeat the initializers'
 * guarantees. An initializer does not retain a receiver passed to {@code getOrCompute},
 * though it may retain objects captured by its computing function.
 * <p>
 * For all factories, the caller must have access to the selected owner and field and any
 * selected array type. Accessing a static field through the initializer initializes its
 * declaring class if necessary, following normal var-handle semantics. A field used
 * directly as storage must be non-final; an array field must be final although its
 * elements remain mutable.
 *
 * <h2 id="encapsulating-lazy-initializers">Encapsulating lazy initializers</h2>
 * A class can encapsulate one or more lazy initializers to cache values in its internal
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
 * The following small class caches a String in a reserved field. The initializer is
 * shared across all instances:
 * {@snippet lang = java:
 * import java.lang.invoke.LazyInitializers;
 * import java.lang.invoke.LazyInitializers.LazyField;
 *
 * class StringCache {
 *
 *     private static final LazyField<StringCache, String> INITIALIZER =
 *             LazyInitializers.ofField(StringCache.class, String.class, "value",
 *                     StringCache::compute);
 *
 *     private String value;
 *
 *     public String get() {
 *         return INITIALIZER.getOrCompute(this);
 *     }
 *
 *     private String compute() {
 *         return "Some string";
 *     }
 * }
 *}
 * Competing threads may compute candidates concurrently, but every normally returning
 * {@code get()} returns the installed value. A call whose computation throws or returns
 * {@code null} still fails, even if another call installs a value. If the field remains
 * uninitialized, a later call may retry. Hence, the computing function can be invoked
 * more than once. Recursive {@code get()} calls are not detected and may recurse without
 * bound.
 * <p>
 * Different policies can be built using the same initializers. For example, a wrapper
 * could cache a record representing either a computed value or a failure marker and
 * serialize the recheck, computation, and publication to invoke its computing function
 * at most once.
 *
 * <h2 id="indexed-caches">Indexed caches</h2>
 * An array field initializer can compute and cache a separate value at each index.
 * The following example shows a memoized String array with properties similar to the
 * cached string class above:
 * {@snippet lang = java:
 * import java.lang.invoke.LazyInitializers;
 * import java.lang.invoke.LazyInitializers.LazyArrayField;
 *
 * class StringArrayCache {
 *
 *     private static final LazyArrayField<StringArrayCache, String> INITIALIZER =
 *             LazyInitializers.ofArrayField(StringArrayCache.class, String.class, "array",
 *                     StringArrayCache::compute);
 *
 *     private final String[] array = new String[16];
 *
 *     public String get(int i) {
 *         return INITIALIZER.getOrCompute(this, i);
 *     }
 *
 *     private String compute(int i) {
 *         return "String " + i;
 *     }
 *
 * }
 *}
 * Each index is initialized independently. Computations for the same index may
 * race, but every normally returning call receives the element installed at that index.
 *
 * <h2 id="performance">Performance</h2>
 * A field managed by one or more lazy initializers can never change after the field has
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
 * For array-field initializers, these optimizations apply independently to each
 * initialized element. Initializing one element does not initialize the other elements.
 *
 * @implNote The initializer factories' implementations install a field or element
 *           candidate as if using
 *           {@link VarHandle#compareAndExchangeRelease VarHandle.compareAndExchangeRelease( ... )}.
 *           The exchange's witness has plain-read effects, so a losing invocation
 *           performs a separate acquire read before returning the winner.
 *           Lazily initialized fields may be eligible for constant folding as described
 *           in the {@linkplain ##performance performance section}.
 *           Initializers should generally be declared {@code static final} in order to
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
public final class LazyInitializers {

    // Suppresses default constructor, ensuring non-instantiability.
    private LazyInitializers() {}

    /**
     * {@return a lazy initializer for a static field accessible to the caller}
     * <p>
     * This method uses {@linkplain ##publish-once publish once} and the supplied field
     * as storage. Its result is a {@link LazyStaticField} whose
     * {@linkplain LazyStaticField#getOrCompute() getOrCompute()} method accesses that
     * field; the computing function is invoked only when the field has its reserved Java
     * default value. The field must be static and non-final. Access is checked with
     * the caller's privileges.
     * <p>
     * This method is caller-sensitive: a helper that invokes it supplies the helper's
     * access privileges. Once the initializer is created, calling its
     * {@linkplain LazyStaticField#getOrCompute() getOrCompute()} method does not
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
    public static <V> LazyStaticField<V> ofStaticField(Class<?> owner,
                                                       Class<? super V> fieldType,
                                                       String name,
                                                       LazyStaticField.Computer<? extends V> computingFunction) {
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
     * {@return a lazy initializer for an instance field accessible to the caller}
     * <p>
     * This method uses {@linkplain ##publish-once publish once} and the supplied field
     * as storage. Its result is a {@link LazyField} whose
     * {@linkplain LazyField#getOrCompute(Object) getOrCompute()} method accesses that
     * field; the computing function is invoked only when the field has its reserved Java
     * default value. The field must be neither static nor final. Access is checked with
     * the caller's privileges.
     * <p>
     * This method is caller-sensitive: a helper that invokes it supplies the helper's
     * access privileges. Once the initializer is created, calling its
     * {@linkplain LazyField#getOrCompute(Object) getOrCompute()} method does not
     * repeat the caller access check. A caller may select its own private non-static
     * field or one in a nestmate. Access to an unrelated private field is denied.
     * <p>
     * If the field is protected and declared in a different runtime package from
     * the caller, the initializer accepts only receivers that are instances of the
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
    public static <T, V> LazyField<T, V> ofField(Class<? super T> receiverType,
                                                 Class<?> fieldType,
                                                 String name,
                                                 LazyField.Computer<? super T, ? extends V> computingFunction) {
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
     * {@return a lazy initializer for a static array field accessible to the caller}
     * <p>
     * This method uses {@linkplain ##publish-once publish once} and the supplied array
     * field and index as storage. Its result is a {@link LazyStaticArrayField} whose
     * {@linkplain LazyStaticArrayField#getOrCompute(int) getOrCompute()} method
     * accesses that array field's element;
     * the computing function is invoked only when the field's element has its reserved Java
     * default value. The array field must be static and final. Access is checked with
     * the caller's privileges.
     * <p>
     * This method is caller-sensitive: a helper that invokes it supplies the helper's
     * access privileges. Once the initializer is created, calling its
     * {@linkplain LazyStaticArrayField#getOrCompute(int) getOrCompute()} method does
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
    public static <A, V> LazyStaticArrayField<A, V> ofStaticArrayField(Class<? super A> arrayType,
                                                                       Class<? super V> arrayComponentType,
                                                                       LazyStaticArrayField.Computer<A, ? extends V> computingFunction) {
        // Keep caller identification in this entry point. A helper accepting
        // a caller Class could be invoked reflectively with a forged argument.
        final Class<?> caller = Reflection.getCallerClass();
        checkCaller(caller);
        Objects.requireNonNull(arrayType);
        Objects.requireNonNull(arrayComponentType);
        Objects.requireNonNull(computingFunction);
        checkArrayType(caller, arrayType);
        //checkArrayComponentType(arrayType, arrayComponentType);
        throw new UnsupportedOperationException();
    }

    /**
     * {@return a lazy initializer for an instance array field accessible to the caller}
     * <p>
     * This method uses {@linkplain ##publish-once publish once} and the supplied array
     * field and index as storage. Its result is an {@link LazyArrayField} whose
     * {@linkplain LazyArrayField#getOrCompute(Object, int) getOrCompute()} method
     * accesses that array field's element;
     * the computing function is invoked only when the field's element has its reserved Java
     * default value. The array field must be non-static and final. Access is checked with
     * the caller's privileges.
     * <p>
     * This method is caller-sensitive: a helper that invokes it supplies the helper's
     * access privileges. Once the initializer is created, calling its
     * {@linkplain LazyArrayField#getOrCompute(Object, int) getOrCompute()} method does
     * not repeat the caller access check. A caller may select its own private final
     * non-static array field or one in a nestmate. Access to an unrelated private field
     * is denied.
     * <p>
     * If the field is protected and declared in a different runtime package from
     * the caller, the initializer accepts only receivers that are instances of the
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
    public static <T, V> LazyArrayField<T, V> ofArrayField(Class<? super T> receiverType,
                                                           Class<? super V> arrayComponentType,
                                                           String name,
                                                           LazyArrayField.Computer<? super T, ? extends V> computingFunction) {
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
    public interface LazyStaticField<V> {

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
    public interface LazyField<T, V> {

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
    public interface LazyStaticArrayField<A, V> {

        /**
         * {@return the non-default value installed at the selected index}
         * If the element is uninitialized, invokes the computing function and
         * attempts to install a valid candidate.
         *
         * @param index the index of the selected array element
         */
        V getOrCompute(A array, int index);

        /**
         * Represents a function that computes an array element value from its
         * index. It may be invoked more than once for the same element,
         * including concurrently.
         *
         * @param <V> the type of value computed
         * @since 29
         */
        @FunctionalInterface
        interface Computer<A, V> {
            /**
             * {@return a non-default candidate value for the selected array element}
             *
             * @param index the index of the selected array element
             */
            V compute(A array, int index);
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
    public interface LazyArrayField<T, V> {

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

        interface OfContext<A, T, V> {
            /**
             * {@return the non-default value installed at the selected array element}
             * If the element is uninitialized, invokes the computing function and
             * attempts to install a valid candidate.
             *
             * @param array the selected array field
             * @param receiver the object holding the selected array field
             * @param index the index of the selected array element
             * @throws ClassCastException if the selected field is protected and
             *         declared in a different runtime package from the factory caller,
             *         and a non-null {@code receiver} is not an instance of that caller
             *         class or one of its subclasses
             */
            V getOrCompute(A array, T receiver, int index);

        }

    }

}
