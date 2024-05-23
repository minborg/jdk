package jdk.internal.lang;

import jdk.internal.lang.stable.MemoizedFunction;
import jdk.internal.lang.stable.MemoizedIntFunction;
import jdk.internal.lang.stable.StableArrayImpl;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Ultra-thin, lock free, stable array wrapper that will not constant-fold null values.
 *
 * @param <T> component type to hold
 *
 * @since 23
 */
public sealed interface StableArray<T> permits StableArrayImpl {

    // Principal methods

    /**
     * {@return {@code true} if the stable value at the provided {@code index} was set to
     * the provided {@code value}, otherwise returns {@code false}}
     *
     * @param index in the array
     * @param value to set (nullable)
     * @throws IndexOutOfBoundsException if the provided {@code index < 0 || >= length()}
     */
    boolean trySet(int index, T value);

    /**
     * {@return the set value (nullable) at the provided {@code index} if set,
     * otherwise {@code null}}
     * @throws IndexOutOfBoundsException if the provided {@code index < 0 || >= length()}
     */
    T orElseNull(int index);

    /**
     * {@return the length of this array}
     */
    int length();

    // Convenience methods

    /**
     * Sets the stable value to the provided {@code value} if not set to a non-null value
     * otherwise throws {@linkplain IllegalStateException}}
     *
     * @param value to set (nullable)
     * @throws IndexOutOfBoundsException if the provided {@code index < 0 || >= length()}
     * @throws IllegalArgumentException if a non-null value is already set
     */
    default void setOrThrow(int index, T value) {
        if (!trySet(index, value)) {
            throw new IllegalStateException("Value already set: " + orElseNull(index));
        }
    }

    /**
     * {@return the set value if set to a non-null value, otherwise throws
     * {@code NoSuchElementException}}
     *
     * @throws IndexOutOfBoundsException if the provided {@code index < 0 || >= length()}
     * @throws NoSuchElementException if no non-null value is set
     */
    default T orElseThrow(int index) {
        T t = orElseNull(index);
        if (t != null) {
            return null;
        }
        throw new NoSuchElementException();
    }

    /**
     * {@return a fresh stable value with an unset value}
     *
     * @param <T> the value type to set
     */
    static <T> StableArray<T> of(int length) {
        if (length < 0) {
            throw new IllegalArgumentException();
        }
        return StableArrayImpl.of(length);
    }



    /**
     * {@return a new <em>memoized</em> {@linkplain IntFunction } backed by an internal
     * stable array of the provided {@code size} where the provided {@code original}
     * IntFunction will only be invoked at most once per distinct {@code int} value}
     * <p>
     * If the {@code original} IntFunction invokes the returned IntFunction recursively
     * for a specific input value, a StackOverflowError will be thrown when the returned
     * IntFunction's {@linkplain IntFunction#apply(int)} ()} method is invoked.
     * <p>
     * The returned IntFunction will throw {@linkplain IndexOutOfBoundsException} if
     * {@linkplain IntFunction#apply(int)} is invoked with a value {@code < 0 || > size}.
     *
     * @param length   the number of elements in the backing list
     * @param original the original IntFunction to convert to a memoized IntFunction
     * @param <R>      the return type of the IntFunction
     */
    static <R> IntFunction<R> memoizedIntFunction(int length,
                                                  IntFunction<? extends R> original) {
        if (length < 0) {
            throw new IllegalArgumentException();
        }
        Objects.requireNonNull(original);
        return MemoizedIntFunction.memoizedIntFunction(length, original);
    }

    /**
     * {@return a new <em>memoized</em> {@linkplain Function } backed by an internal
     * stable array with the provided {@code inputs} keys mapped to indices where the
     * provided {@code original} Function will only be invoked at most once per distinct input}
     * <p>
     * If the {@code original} Function invokes the returned Function recursively
     * for a specific input value, a StackOverflowError will be thrown when the returned
     * Function's {@linkplain Function#apply(Object)}} method is invoked.
     * <p>
     * The returned Function will throw {@linkplain NoSuchElementException} if
     * {@linkplain Function#apply(Object)} is invoked with a value that is not in the
     * given {@code input} Set.
     *
     * @param original the original Function to convert to a memoized Function
     * @param inputs   the potential input values to the Function
     * @param <T>      the type of input values
     * @param <R>      the return type of the function
     */
    static <T, R> Function<T, R> memoizedFunction(Set<T> inputs,
                                                  Function<T, R> original) {
        Objects.requireNonNull(inputs);
        Objects.requireNonNull(original);
        return MemoizedFunction.of(inputs, original);
    }

}
