package jdk.internal.lang.stable;

import jdk.internal.vm.annotation.Stable;

import java.util.function.Supplier;

import static jdk.internal.lang.stable.StableUtil.*;

public final class SimpleMemoizedSupplier<T>
        implements Supplier<T> {

    private static final long VALUE_OFFSET =
            UNSAFE.objectFieldOffset(StableValueImpl.class, "value");

    @Stable
    private final Supplier<? extends T> original;
    @Stable
    private T value;
    // Used to signal a `null` value and for piggybacking
    @Stable
    private volatile byte state;

    public SimpleMemoizedSupplier(Supplier<? extends T> original) {
        this.original = original;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T get() {
        // Reading `state` using volatile semantics established a happens-before relation
        // with respect to the update of `value` even if read using plain memory semantics
        // (piggybacking).
        switch (state) {
            case SET_NONNULL: return value;
            case SET_NULL: return null;
        }
        T t = original.get();
        final byte newState;
        if (t == null) {
            t = (T) NULL_SENTINEL;
            newState = SET_NULL;
        } else {
            newState = SET_NONNULL;
        }
        T witness = (T) UNSAFE.compareAndExchangeReference(this, VALUE_OFFSET, null, t);
        state = newState;
        return witness == null ? t : witness;
    }
}
