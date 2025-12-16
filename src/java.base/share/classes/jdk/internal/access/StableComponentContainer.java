package jdk.internal.access;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

// This class is required to be able to be used very early in the boot sequence.
// Because of this, it does not use reflection, MethodHandles, or ImmutableCollections
/**
 * A thread-safe, concurrent, lock-free, stable heterogeneous container.
 * <p>
 * The lookup of components can be eligible for constant folding if the stable component
 * container is a VM constant (e.g., is declared as a `static final` field) and the
 * lookup key is a constant (e.g., a class literal)..
 *
 * @param <T> The common type of the components. The type can be {@linkplain Object} if
 *            there is no common super type for the components.
 */
public sealed interface StableComponentContainer<T> {

    /**
     * {@return the associated component for the provided {@code type}}
     *
     * @param type to use as lookup
     * @param <C> component type
     * @throws IllegalArgumentException if the provided {@code type} was not specified
     *         at construction.
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
     *         at construction.
     */
    <C extends T> C orElse(Class<C> type, C other);

    /**
     * {@return {@code true} if, and only if, there is an associated component for
     *          the provided {@code type}}, or else {@code false}}
     *
     * @param type to use as lookup
     * @throws IllegalArgumentException if the provided {@code type} was not specified
     *         at construction.
     */
    boolean isInitialized(Class<? extends T> type);

    /**
     * Associates the provided {@code type} with the provided {@code component}
     *
     * @param type to use as lookup
     * @throws IllegalStateException if the provided {@code type} was already associated
     *         with a component
     */
    <C extends T> void set(Class<C> type, C component);

    record StableComponentContainerImpl<T>(@Stable Object[] table) implements StableComponentContainer<T> {

        private static final Unsafe UNSAFE = Unsafe.getUnsafe();

        @ForceInline
        public <C extends T> C get(Class<C> type) {
            return type.cast(componentRaw(type));
        }

        public boolean isInitialized(Class<? extends T> type) {
            return componentRaw(type) != null;
        }

        @Override
        public <C extends T> C orElse(Class<C> type, C other) {
            final Object componentRaw = componentRaw(type);
            return componentRaw == null ? other : type.cast(componentRaw);
        }

        @ForceInline
        private Object componentRaw(Class<?> type) {
            Objects.requireNonNull(type);
            final int probe = probeOrThrow(type);
            return UNSAFE.getReferenceAcquire(table, nextOffset(offsetFor(probe)));
        }

        public <C extends T> void set(Class<C> type, C component) {
            // Implicit null check of both `type` and `component`
            if (!type.isInstance(component)) {
                throw new IllegalArgumentException();
            }
            final int probe = probeOrThrow(type);
            if (!UNSAFE.compareAndSetReference(table, nextOffset(offsetFor(probe)), null, component)) {
                throw new IllegalStateException("The component is already initialized: " + type.getName());
            }
        }

        @Override
        public String toString() {
            return "StableComponentContainer" + associations(true);
        }

        private String associations(boolean showValues) {
            final StringJoiner sj = new StringJoiner(", ");
            for (int i = 0; i < table.length; i+=2) {
                final Class<?> type = (Class<?>) table[i];
                if (type != null) {
                    if (showValues) {
                        final Object component = UNSAFE.getReferenceAcquire(table, nextOffset(offsetFor(i)));
                        sj.add(type.getName() + (component != null ? "=" + component : ""));
                    } else {
                        sj.add(type.toString());
                    }
                }
            }
            return "{" + sj + "}";
        }

        @ForceInline
        private int probeOrThrow(Class<?> type) {
            final int probe = probe(table, type);
            if (probe < 0) {
                throw new IllegalArgumentException("The type '" + type.getName() + "' is outside the allowed input types: " + associations(false));
            }
            return probe;
        }

        @ForceInline
        private long offsetFor(int index) {
            return Unsafe.ARRAY_OBJECT_BASE_OFFSET + (long) index * Unsafe.ARRAY_OBJECT_INDEX_SCALE;
        }

        @ForceInline
        private long nextOffset(long offset) {
            return offset + Unsafe.ARRAY_OBJECT_INDEX_SCALE;
        }
    }

    // returns index at which the probe key is present; or if absent,
    // (-i - 1) where i is location where element should be inserted.
    @ForceInline
    private static int probe(Object[] table, Object pk) {
        int idx = Math.floorMod(pk.hashCode(), table.length >> 1) << 1;
        while (true) {
            Object ek = table[idx];
            if (ek == null) {
                return -idx - 1;
            } else if (pk.equals(ek)) {
                return idx;
            } else if ((idx += 2) == table.length) {
                idx = 0;
            }
        }
    }

    private static int availableIndex(int probe) {
        return -probe - 1;
    }

    static <T> StableComponentContainer<T> of(Set<Class<? extends T>> types) {
        // TOC TOU protection and
        // implicit null check of `types` and explicit null check on all its elements
        final Object[] inputs = new Object[types.size()];
        int idx = 0;
        for (Object type : types) {
            inputs[idx++] = Objects.requireNonNull(type);
        }

        // Prepopulate all the keys upfront
        final Object[] table = new Object[inputs.length << 2];
        for (Object type : inputs) {
            final int probe = probe(table, type);
            assert probe < 0;
            final int keyIndex = availableIndex(probe);
            table[keyIndex] = type;
        }
        return new StableComponentContainerImpl<>(table);
    }

}
