/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package java.util.concurrent.lazy;

import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

// Build time, background, etc. computation (time shifting) (referentially transparent)

/**
 * An object reference in which the value can be lazily and atomically computed.
 * <p>
 * At most one invocation is made of any provided set of suppliers.
 * <p>
 * This contrasts to {@link AtomicReference } where any number of updates can be done
 * and where there is no simple way to atomically compute
 * a value (guaranteed to only be computed once) if missing.
 * <p>
 * The implementation is optimized for the case where there are N invocations
 * trying to obtain the value and where N >> 1, for example where N is > 2<sup>20</sup>.
 * <p>
 * A supplier may return {@code null} which then will be perpetually recorded as the value.
 * <p>
 * This class is thread-safe.
 * <p>
 * The JVM may apply certain optimizations as it knows the value is updated just once
 * at most as described by {@link Stable} as exemplified here:
 * {@snippet lang = java:
 *     private static final LazyReference<Value> MY_LAZY_VALUE = Lazy.of(Value::new);
 *     // ...
 *     public Value value() {
 *         // This will likely be constant-folded by the JIT C2 compiler.
 *         return MY_LAZY_VALUE.get();
 *     }
 *}
 *
 * @param <V> The type of the value to be recorded
 */
public final class LazyReference<V>
        implements Supplier<V> {

    // Maintain a private copy.
    private static final Lazy.State[] STATES = Lazy.State.values();
    private static final Lazy.Evaluation[] EVALUATIONS = Lazy.Evaluation.values();

    // Allows access to the state variable with arbitary memory semantics
    private static final VarHandle STATE_VH = stateVarHandle();

    private Supplier<? extends V> presetProvider;

    // General field to store sevaral states (saving space)
    // Bit 0-2  Indicates earliestEvaluation
    // Bit 3    Reserved
    // Bit 4    Indicates if constructing (We are using a separate bit as we want "state" to be @Stable
    // Bit 5-31 Reserved
    private int misc;

    @Stable
    private int valueState;

    @Stable
    private Object value;

    LazyReference(Lazy.Evaluation earliestEvaluation,
                  Supplier<? extends V> presetSupplier) {
        this.misc = earliestEvaluation.ordinal();
        this.presetProvider = presetSupplier;
        if (earliestEvaluation != Lazy.Evaluation.AT_USE && presetSupplier != null) {
            // Start computing the value via a background Thread.
            Thread.ofVirtual()
                    .name("Lazy evaluator: " + presetSupplier)
                    .start(() -> supplyIfEmpty0(presetSupplier));
        }
    }

    // To be called by builders/compilers/destillers to eagerly pre-compute a value (e.g. Constable)
    LazyReference(V value) {
        this(Lazy.Evaluation.CREATION, (Supplier<? extends V>) null);
        this.value = value;
        this.valueState = Lazy.PRESENT_ORDINAL;
    }

    /**
     * {@return The {@link Lazy.State } of this Lazy}.
     * <p>
     * The value is a snapshot of the current State.
     * No attempt is made to compute a value if it is not already present.
     * <p>
     * If the returned State is either {@link Lazy.State#PRESENT} or
     * {@link Lazy.State#ERROR}, it is guaranteed the state will
     * never change in the future.
     * <p>
     * This method can be used to act on a value if it is present:
     * {@snippet lang = java:
     *     if (lazy.state() == State.PRESENT) {
     *         T value = lazy.get();
     *         // perform action on the value
     *     }
     *}
     */
    public Lazy.State state() {
        Lazy.State state = STATES[stateValuePlain()];
        if (Lazy.State.isFinal(state)) {
            return state;
        }
        synchronized (this) {
            int stateValuePlain = stateValuePlain();
            if (isConstructing() && stateValuePlain == Lazy.State.EMPTY.ordinal()) {
                return Lazy.State.CONSTRUCTING;
            }
            return STATES[stateValuePlain];
        }
    }

    /**
     * {@return The erliest point at which this Lazy can be evaluated}.
     */
    Lazy.Evaluation earliestEvaluation() {
        return EVALUATIONS[misc & 0xF];
    }

    /**
     * Returns the present value or, if no present value exists, atomically attempts
     * to compute the value using the <em>pre-set {@linkplain Lazy#of(Supplier)} supplier}</em>.
     * If no pre-set {@linkplain Lazy#of(Supplier)} supplier} exists,
     * throws an IllegalStateException exception.
     * <p>
     * If the pre-set supplier itself throws an (unchecked) exception, the
     * exception is rethrown, and no value is recorded. The most
     * common usage is to construct a new object serving as a memoized result, as in:
     * <p>
     * {@snippet lang = java:
     *    LazyReference<V> lazy = Lazy.of(Value::new);
     *    // ...
     *    V value = lazy.get();
     *    assertNotNull(value); // Value is non-null
     *}
     * <p>
     * If another thread attempts to compute the value, the current thread will be suspended until
     * the atempt completes (successfully or not).
     *
     * @return the value (pre-existing or newly computed)
     * @throws NullPointerException   if the pre-set supplier returns {@code null}.
     * @throws IllegalStateException  if a value was not already present and no
     *                                pre-set supplier was specified.
     * @throws NoSuchElementException if a supplier has previously thrown an exception.
     */
    @SuppressWarnings("unchecked")
    public V get() {
        return isPresentPlain()
        //return valueState == Lazy.PRESENT_ORDINAL
                ? (V) value
                : supplyIfEmpty0(presetProvider);
    }

    /**
     * Returns the present value or, if no present value exists, atomically attempts
     * to compute the value using the <em>provided {@code supplier}</em>.
     * <p>
     * If the provided {@code supplier} itself throws an (unchecked) exception, the
     * exception is rethrown, and no value is recorded.  The most
     * common usage is to construct a new object serving as a memoized result, as in:
     * <p>
     * {@snippet lang = java:
     *    LazyReference<V> lazy = Lazy.ofEmpty();
     *    // ...
     *    V value = lazy.supplyIfAbsent(Value::new);
     *    assertNotNull(value); // Value is non-null
     *}
     * <p>
     * If another thread attempts to compute the value, the current thread will be suspended until
     * the atempt completes (successfully or not).
     *
     * @param supplier to apply if no previous value exists
     * @return the value (pre-existing or newly computed)
     * @throws NullPointerException   if the provided {@code supplier} is {@code null}.
     * @throws NoSuchElementException if a supplier has previously thrown an exception.
     */
    public V supplyIfEmpty(Supplier<? extends V> supplier) {
        Objects.requireNonNull(supplier);
        return supplyIfEmpty0(supplier);
    }

    /**
     * {@return the excption thrown by the supplier invoked or
     * {@link Optional#empty()} if no exception was thrown}.
     */
    public Optional<Throwable> exception() {
        return is(Lazy.State.ERROR)
                ? Optional.of((Throwable) value)
                : Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private V supplyIfEmpty0(Supplier<? extends V> supplier) {
        if (!isPresentPlain()) {
            // Synchronized implies acquire/release semantics when entering/leaving the monitor
            synchronized (this) {
                switch (stateValuePlain()) {
                    case Lazy.PRESENT_ORDINAL -> {
                        return (V) value;
                    }
                    case Lazy.ERROR_ORDINAL -> throw new NoSuchElementException(exception().get());
                    default -> {
                        try {
                            if (supplier == null) {
                                throw new IllegalStateException("No pre-set supplier given");
                            }
                            constructing(true);
                            V v = supplier.get();
                            if (v != null) {
                                value = v;
                            }
                            // Alt 1
                            // Prevents reordering. Changes only go in one direction.
                            // https://developer.arm.com/documentation/102336/0100/Load-Acquire-and-Store-Release-instructions
                            stateValueRelease(Lazy.State.PRESENT);

                            // Alt 2
                            // VarHandle.fullFence();
                        } catch (Throwable e) {
                            // Record the throwable instead of the value.
                            value = e;
                            // Prevents reordering.
                            stateValueRelease(Lazy.State.ERROR);
                            // Rethrow
                            throw e;
                        } finally {
                            constructing(false);  // Redundant operation
                            forgetPresetProvided();
                        }
                    }
                }
            }
        }
        return (V) value;
    }

    @Override
    public final String toString() {
        return getClass().getSimpleName() + "[" + switch (state()) {
            case EMPTY -> Lazy.State.EMPTY;
            case CONSTRUCTING -> Lazy.State.CONSTRUCTING;
            case PRESENT -> Objects.toString(value);
            case ERROR -> Lazy.State.ERROR + " [" + value + "]";
        } + "]";
    }

    // Todo: Consider adding checked exception constructior. E.g. Cache value from an SQL query (Check with Ron)
    // Todo: Consider adding a lazy that shields a POJO

    /**
     * A builder that can be used to configure a LazyReference.
     *
     * @param <T> the type of the value.
     */
    public interface Builder<T> {

        /**
         * {@return a builder that will use the provided {@code supplier} when
         * eventially {@linkplain #build() building} a LazyReference}.
         *
         * @param supplier to use
         */
        Builder<T> withSupplier(Supplier<? extends T> supplier);

        /**
         * {@return a builder that will have no {@code supplier} when
         * eventially {@linkplain #build() building} a LazyReference}.
         */
        Builder<T> withoutSuplier();

        /**
         * {@return a builder that will use the provided {@code earliestEvaluation} when
         * eventially {@linkplain #build() building} a LazyReference}.
         *
         * @param earliestEvaluation to use
         */
        Builder<T> withEarliestEvaluation(Lazy.Evaluation earliestEvaluation);

        /**
         * {@return a builder that will use the provided eagerly computed {@code value} when
         * eventially {@linkplain #build() building} a LazyReference}.
         *
         * @param value to use
         */
        Builder<T> withValue(T value);

        /**
         * {@return a new LazyReference with the builder's configured setting}.
         */
        LazyReference<T> build();
    }

    record LazyReferenceBuilder<T>(Lazy.Evaluation binding,
                                   Supplier<? extends T> supplier,
                                   boolean hasValue,
                                   T value) implements Builder<T> {

        LazyReferenceBuilder() {
            this(null);
        }

        LazyReferenceBuilder(Supplier<? extends T> supplier) {
            this(Lazy.Evaluation.AT_USE, supplier, false, null);
        }

        @Override
        public Builder<T> withEarliestEvaluation(Lazy.Evaluation earliestEvaluation) {
            return new LazyReferenceBuilder<>(Objects.requireNonNull(earliestEvaluation), supplier, hasValue, value);
        }

        @Override
        public Builder<T> withSupplier(Supplier<? extends T> supplier) {
            return new LazyReferenceBuilder<>(binding, Objects.requireNonNull(supplier), hasValue, value);
        }

        @Override
        public Builder<T> withoutSuplier() {
            return new LazyReferenceBuilder<>(binding, null, hasValue, value);
        }

        @Override
        public Builder<T> withValue(T value) {
            return new LazyReferenceBuilder<>(binding, supplier, true, value);
        }

        @Override
        public LazyReference<T> build() {
            return hasValue
                    ? new LazyReference<>(value)
                    : new LazyReference<>(binding, supplier);
        }
    }

    // Private support methods

    private boolean isConstructing() {
        return (misc & 0x10) != 0;
    }

    private void constructing(boolean constructing) {
        if (constructing) {
            misc |= 0x10;
        } else {
            misc &= (~0x10);
        }
    }

    private boolean isPlain(Lazy.State state) {
        return stateValuePlain() == state.ordinal();
    }

    private boolean is(Lazy.State state) {
        if (Lazy.State.isFinal(state) && isPlain(state)) {
            return true;
        }
        synchronized (this) {
            return isPlain(state);
        }
    }

    // Faster than isPlain(State.Present)
    private boolean isPresentPlain() {
        return stateValuePlain() == Lazy.PRESENT_ORDINAL;
    }

    private boolean isPresent() {
        if (isPresentPlain()) {
            return true;
        }
        synchronized (this) {
            return isPresentPlain();
        }

    }

    private int stateValuePlain() {
        return (int) STATE_VH.get(this);
    }

    private void stateValueRelease(Lazy.State newState) {
        STATE_VH.setRelease(this, newState.ordinal());
    }

    private void forgetPresetProvided() {
        // Stops preventing the provider from being collected once it has been
        // used (if initially set).
        this.presetProvider = null;
    }

    private static VarHandle stateVarHandle() {
        try {
            return MethodHandles.lookup()
                    .findVarHandle(LazyReference.class, "valueState", int.class);
            // .withInvokeExactBehavior(); // Make sure no boxing is made?
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
