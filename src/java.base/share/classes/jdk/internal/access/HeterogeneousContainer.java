package jdk.internal.access;

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

// This class is required to be able to be used very early in the boot sequence.
// Because of this, it does not use reflection, MethodHandles, or ImmutableCollections
/**
 * A thread-safe, concurrent, lock-free, stable heterogeneous container.
 * <p>
 * The lookup of components can be eligible for constant folding if the stable component
 * container is a VM constant (e.g., is declared as a `static final` field) and the
 * lookup key is a constant (e.g., a class literal).
 * <p>
 * Except if otherwise specified, all methods throw a {@linkplain NullPointerException}
 * if a {@code null} parameter is provided.
 *
 * @param <T> The common type of the components. The type can be {@linkplain Object} if
 *            there is no common super type for the components.
 */
public sealed interface HeterogeneousContainer<T> permits HeterogeneousContainerImpl {

    /**
     * {@return the associated component for the provided {@code type}}
     *
     * @param type to use as lookup
     * @param <C> component type
     * @throws IllegalArgumentException if the provided {@code type} was not specified
     *         {@linkplain HeterogeneousContainer#of(Set) at construction}.
     */
    <C extends T> C get(Class<C> type);

    /**
     * {@return the associated component for the provided {@code type}}, or else the
     *          provided {@code other} value.
     *
     * @param type to use as lookup
     * @param other to return if there is no association to the provided {@code type}
     *              (nullable)
     * @param <C> component type
     * @throws IllegalArgumentException if the provided {@code type} was not specified
     *         {@linkplain HeterogeneousContainer#of(Set) at construction}.
     */
    <C extends T> C orElse(Class<C> type, C other);

    /**
     * {@return {@code true} if, and only if, there is an associated component for
     *          the provided {@code type}}, or else {@code false}}
     *
     * @param type to use as lookup
     * @throws IllegalArgumentException if the provided {@code type} was not specified
     *         {@linkplain HeterogeneousContainer#of(Set) at construction}.
     */
    boolean isInitialized(Class<? extends T> type);

    /**
     * Associates the provided {@code type} with the provided {@code component}
     *
     * @param type to use as lookup
     * @throws IllegalArgumentException if the provided {@code type} was not specified
     *         {@linkplain HeterogeneousContainer#of(Set) at construction}.
     * @throws IllegalStateException if the provided {@code type} was already associated
     *         with a component
     */
    <C extends T> void set(Class<C> type, C component);

    /**
     * If the specified type is not already associated with a component,
     * attempts to compute its value using the given mapping
     * function and enters it into this container.
     *<p>
     * If the mapping function itself throws an (unchecked) exception, the
     * exception is rethrown, and no association is recorded. The most
     * common usage is to construct a new object serving as an initial
     * mapped value or memoized result, as in:
     *
     * <pre> {@code
     * Component component = container.computeIfAbsent(Component.class, k -> new ComponentImpl(f(k)));
     * }</pre>
     *
     * <p>The mapping function should not modify this container during computation.
     *
     * @implSpec
     * The implementation is equivalent to the following steps for this
     * {@code container}, then returning the current value or {@code null} if now
     * absent:
     *
     * <pre> {@code
     * if (!map.isInitialized(key)) {
     *     map.put(key, mappingFunction.apply(key));
     * }
     * }</pre>
     * <p>
     * The implementation makes no guarantees about synchronization
     * or atomicity properties of this method.
     *
     * @param type with which the to-be-computed component is to be associated
     * @param mappingFunction the mapping function to compute a component
     * @return the current (existing or computed) component associated with
     *         the specified type
     * @throws IllegalArgumentException if the provided {@code type} was not specified
     *         {@linkplain HeterogeneousContainer#of(Set) at construction}.
     */
    <C extends T> C computeIfAbsent(Class<C> type,
                                    Function<Class<C>, ? extends C> mappingFunction);

    /**
     * {@return a new stable component container that can associate any of the provided
     *          {@code types} to components}
     *
     * @param types that can be used to associate to components
     * @param <T>   the common type of the components. The type can be {@linkplain Object}
     *              if there is no common super type for the components.
     */
    static <T> HeterogeneousContainer<T> of(Set<Class<? extends T>> types) {
        // TOC TOU protection and
        // implicit null check of `types` and explicit null check on all its elements
        final Object[] inputs = new Object[types.size()];
        int idx = 0;
        for (Object type : types) {
            inputs[idx++] = Objects.requireNonNull(type);
        }
        return HeterogeneousContainerImpl.of(inputs);
    }

    /**
     * {@return a new stable component container that can associate any of the permitted
     *          subclasses of the provided {@code type} to components}
     *
     * @param type sealed type who's permitted subclasses can be used to associate
     *             components
     * @param <T>  the common type of the components. The type can be {@linkplain Object}
     *             if there is no common super type for the components.
     */
    static <T> HeterogeneousContainer<T> of(Class<T> type) {
        // Implicit null check
        if (!type.isSealed()) {
            throw new IllegalArgumentException("The provided type must be sealed: " + type);
        }
        return HeterogeneousContainerImpl.of(Objects.requireNonNull(type.getPermittedSubclasses()));
    }

}
