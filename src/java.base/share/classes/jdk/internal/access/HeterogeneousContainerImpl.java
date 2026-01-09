package jdk.internal.access;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.AOTSafeClassInitializer;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Function;

@AOTSafeClassInitializer
record HeterogeneousContainerImpl<T>(@Stable Object[] table) implements HeterogeneousContainer<T> {

    private static class AOTExclusion {
        private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    }

    @ForceInline
    public <C extends T> C get(Class<C> type) {
        final Object componentRaw = componentRaw(type);
        if (componentRaw == null) {
            throw new NoSuchElementException("No component of type " + type);
        }
        return type.cast(componentRaw);
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
        return AOTExclusion.UNSAFE.getReferenceAcquire(table, nextOffset(offsetFor(probe)));
    }

    public <C extends T> void set(Class<C> type, C component) {
        set0(type, component, true);
    }

    @ForceInline
    public <C extends T> void set0(Class<C> type, C component, boolean setSemantics) {
        Objects.requireNonNull(component);
        // Implicit null check of `type`
        if (!type.isInstance(component)) {
            throw new IllegalArgumentException("The component '" + component + "' is not an instance of " + type);
        }
        final int probe = probeOrThrow(type);
        if (!AOTExclusion.UNSAFE.compareAndSetReference(table, nextOffset(offsetFor(probe)), null, component) && setSemantics) {
            throw new IllegalStateException("The component is already initialized: " + type.getName());
        }
    }

    @ForceInline
    @Override
    public <C extends T> C computeIfAbsent(Class<C> type,
                                           Function<Class<C>, ? extends C> mappingFunction) {
        Objects.requireNonNull(mappingFunction);
        C c;
        if ((c = orElse(type, null)) == null) {
            // Allow racy sets as several threads can race to set the value
            set0(type, c = mappingFunction.apply(type), false);
        }
        return c;
    }

    @Override
    public String toString() {
        return "HeterogeneousContainer" + associations(true);
    }

    private String associations(boolean showValues) {
        final StringJoiner sj = new StringJoiner(", ");
        for (int i = 0; i < table.length; i += 2) {
            final Class<?> type = (Class<?>) table[i];
            if (type != null) {
                if (showValues) {
                    final Object component = AOTExclusion.UNSAFE.getReferenceAcquire(table, nextOffset(offsetFor(i)));
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

    static <T> HeterogeneousContainer<T> of(Object[] inputs) {
        // Prepopulate all the keys upfront
        final Object[] table = new Object[inputs.length << 2];
        for (Object type : inputs) {
            final int probe = probe(table, type);
            assert probe < 0;
            final int keyIndex = availableIndex(probe);
            table[keyIndex] = type;
        }
        return new HeterogeneousContainerImpl<>(table);
    }

}
