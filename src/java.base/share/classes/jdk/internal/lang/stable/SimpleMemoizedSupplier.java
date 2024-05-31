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
    private volatile T value;

    public SimpleMemoizedSupplier(Supplier<? extends T> original) {
        this.original = original;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T get() {
        T t = value;
        if (t != null) {
            return t == nullSentinel() ? null : t;
        }
        t = original.get();
        if (t == null) {
            t = nullSentinel();
        }
        T witness = (T) UNSAFE.compareAndExchangeReference(this, VALUE_OFFSET, null, t);
        t = (witness == null ? t : witness);
        return t == nullSentinel() ? null : t;
    }
}
