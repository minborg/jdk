package jdk.internal.lang.stable;

import jdk.internal.lang.StableValue;
import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;

import java.util.NoSuchElementException;
import java.util.function.Supplier;

public record MemoizedSupplier<T>(Supplier<T> original,
                                  StableValue<T> value,
                                  StableValue<ProviderResult> result) implements Supplier<T> {

    public MemoizedSupplier(Supplier<T> original) {
        this(original, StableValue.of(), StableValue.of());
    }

    @ForceInline
    @Override
    public T get() {
        final T t = value.orElseNull();
        if (t != null) {
            return t;
        }
        if (result.orElseNull() instanceof ProviderResult.Null) {
            return null;
        }
        return getSlowPath();
    }

    @DontInline
    private T getSlowPath() {
        // The internal `result` field also serves as a mutex

        // Consider old switch statement (HelloClassList)

        synchronized (result) {
            return switch (result.orElseNull()) {
                case ProviderResult.NonNull _  -> value.orElseNull();
                case ProviderResult.Null _     -> null;
                case ProviderResult.Error<?> e -> throw new NoSuchElementException(e.throwableClass().getName());
                case null -> {
                    try {
                        T t = original.get();
                        if (t != null) {
                            value.setOrThrow(t);
                            result.setOrThrow(ProviderResult.NonNull.INSTANCE);
                        } else {
                            result.setOrThrow(ProviderResult.Null.INSTANCE);
                        }
                        yield t;
                    } catch (Throwable th) {
                        result.setOrThrow(new ProviderResult.Error<>(th.getClass()));
                        throw th;
                    }
                }
            };
        }
    }

}
