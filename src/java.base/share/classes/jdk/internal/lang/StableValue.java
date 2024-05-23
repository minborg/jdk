package jdk.internal.lang;

import jdk.internal.lang.stable.MemoizedSupplier;
import jdk.internal.lang.stable.StableValueImpl;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A thin, lock free, set-at-most-once, stable value wrapper eligible for certain
 * JVM optimizations if set to a non-null value.
 *
 * @param <T> type of the wrapped value
 *
 * @since 23
 */
public sealed interface StableValue<T> permits StableValueImpl {

    // Principal methods

    /**
     * {@return {@code true} if the stable value was set to the provided {@code value},
     * otherwise returns {@code false}}
     *
     * @param value to set (nullable)
     */
    boolean trySet(T value);

    /**
     * {@return the set value (nullable) if set, otherwise {@code null}}
     */
    T orElseNull();

    // Convenience methods

    /**
     * Sets the stable value to the provided {@code value} if not set to a non-null value
     * otherwise throws {@linkplain IllegalStateException}}
     *
     * @param value to set (nullable)
     * @throws IllegalArgumentException if a non-null value is already set
     */
    default void setOrThrow(T value) {
        if (!trySet(value)) {
            throw new IllegalStateException("Value already set: " + orElseNull());
        }
    }

    /**
     * {@return the set value if set to a non-null value, otherwise throws
     * {@code NoSuchElementException}}
     *
     * @throws NoSuchElementException if no non-null value is set
     */
    default T orElseThrow() {
        T t = orElseNull();
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
    static <T> StableValue<T> of() {
        return StableValueImpl.of();
    }

    /**
     * {@return a new thread-safe, stable, lazily computed {@linkplain Supplier supplier}
     * that records the value of the provided {@code original} supplier upon being first
     * accessed via {@linkplain Supplier#get()}}
     * <p>
     * The provided {@code original} supplier is guaranteed to be invoked at most once
     * even in a multi-threaded environment.
     * <p>
     * If the provided {@code original} supplier throws an exception, it is relayed
     * to the initial caller. Subsequent read operations will throw
     * {@linkplain java.util.NoSuchElementException}. The class of the original exception
     * is also recorded and is available via the {@linkplain Object#toString()} method.
     * For security reasons, the entire original exception is not retained.
     *
     * @param original supplier
     * @param <T> the type of results supplied by the returned supplier
     */
    static <T> Supplier<T> memoizedSupplier(Supplier<T> original) {
        Objects.requireNonNull(original);
        return new MemoizedSupplier<>(original);
    }

    /**
     * {@return a stable unmodifiable list containing {@code size} distinct
     * fresh unset StableValue elements}
     *
     * @param <E> the returned {@code List}'s element type
     * @throws IllegalArgumentException if the provide {@code size} is negative.
     *
     */
    static <E> List<StableValue<E>> ofList(int size) {
        return Stream.generate(StableValue::<E>of)
                .limit(size)
                .toList();
    }

    /**
     * {@return a stable unmodifiable map containing {@code keys} each associated with
     * a distinct fresh unset StableValue value}
     *
     * @param <K> the returned {@code Map}'s key type
     * @param <V> the returned {@code Map}'s value type
     *
     */
    static <K, V> Map<K, StableValue<V>> ofMap(Set<K> keys) {
        return keys.stream()
                .collect(Collectors.toUnmodifiableMap(
                        Function.identity(),
                        _ -> StableValue.of()
                ));
    }

}
