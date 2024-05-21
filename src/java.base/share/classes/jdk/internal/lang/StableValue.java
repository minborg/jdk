package jdk.internal.lang;

import jdk.internal.lang.stable.StableValueImpl;

import java.util.NoSuchElementException;

/**
 * Ultra-thin stable value wrapper that will not constant-fold null values.
 *
 * @param <T> type to hold
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
     * {@return the set value (nullable) if set, otherwise {@code null}
     */
    T getOrNull();

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
            throw new IllegalStateException("Value already set: " + getOrNull());
        }
    }

    /**
     * {@return the set value if set to a non-null value, otherwise throws
     * {@code NoSuchElementException}}
     *
     * @throws NoSuchElementException if no non-null value is set
     */
    default T getOrThrow() {
        T t = getOrNull();
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

}
