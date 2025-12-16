package jdk.internal.access;

import java.util.Objects;
import java.util.Set;

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
public sealed interface StableComponentContainer<T> permits StableComponentContainerImpl {

    /**
     * {@return the associated component for the provided {@code type}}
     *
     * @param type to use as lookup
     * @param <C> component type
     * @throws IllegalArgumentException if the provided {@code type} was not specified
     *         {@linkplain StableComponentContainer#of(Set) at construction}.
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
     *         {@linkplain StableComponentContainer#of(Set) at construction}.
     */
    <C extends T> C orElse(Class<C> type, C other);

    /**
     * {@return {@code true} if, and only if, there is an associated component for
     *          the provided {@code type}}, or else {@code false}}
     *
     * @param type to use as lookup
     * @throws IllegalArgumentException if the provided {@code type} was not specified
     *         {@linkplain StableComponentContainer#of(Set) at construction}.
     */
    boolean isInitialized(Class<? extends T> type);

    /**
     * Associates the provided {@code type} with the provided {@code component}
     *
     * @param type to use as lookup
     * @throws IllegalArgumentException if the provided {@code type} was not specified
     *         {@linkplain StableComponentContainer#of(Set) at construction}.
     * @throws IllegalStateException if the provided {@code type} was already associated
     *         with a component
     */
    <C extends T> void set(Class<C> type, C component);

    /**
     * {@return a new stable component container that can associate any of the provided
     *          {@code types} to components}
     *
     * @param types that can be used to associate to components
     * @param <T>   the common type of the components. The type can be {@linkplain Object}
     *              if there is no common super type for the components.
     */
    static <T> StableComponentContainer<T> of(Set<Class<? extends T>> types) {
        // TOC TOU protection and
        // implicit null check of `types` and explicit null check on all its elements
        final Object[] inputs = new Object[types.size()];
        int idx = 0;
        for (Object type : types) {
            inputs[idx++] = Objects.requireNonNull(type);
        }
        return StableComponentContainerImpl.of(inputs);
    }

}
